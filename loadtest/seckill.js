// 秒杀压测脚本
//
// 用法（在 loadtest 目录下）：
//   k6 run -e MODE=oversell  seckill.js     1000 个不同用户各抢 1 次，库存 100
//   k6 run -e MODE=single    seckill.js     1 个用户并发 200 次
//   k6 run -e MODE=rate      seckill.js     1000 请求突发，看限流
//   k6 run -e MODE=throughput seckill.js    500 VU 持续 30 秒
//   k6 run -e MODE=consume -e TOKENS=./tokens-consume.json seckill.js
//                                           300 个不同用户各抢 1 次，量消费端吞吐
//
// 依赖同目录的 tokens.json（由 seed-tokens.ps1 生成），可用 -e TOKENS= 换文件

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const TOKEN_FILE = __ENV.TOKENS || './tokens.json';
const tokens = new SharedArray('tokens', () => JSON.parse(open(TOKEN_FILE)).tokens);

const MODE = __ENV.MODE || 'oversell';
const ACT_ID = __ENV.ACT_ID || '1';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 业务结果分类。只看 HTTP 200 是不够的 —— 售罄、限购、限流都是 200
const bizSuccess = new Counter('biz_success');
const bizSoldOut = new Counter('biz_sold_out');
const bizLimit = new Counter('biz_limit_exceeded');
const bizRateLimited = new Counter('biz_rate_limited');
const bizOther = new Counter('biz_other');

const SCENARIOS = {
  // 1000 个不同用户各抢 1 次，但用「200 并发 × 5 轮」而不是 1000 并发。
  //
  // 为什么不直接开 1000 VU：Windows 上 1000 个瞬时 TCP 建连会大面积
  // connection refused（实测 1000 个请求只有 207 个到达应用层，
  // 把 Tomcat accept-count 从 100 调到 4000 都无效 —— 卡在 OS 的 listen backlog）。
  // 这是压测机的环境限制，不是应用问题，所以绕开它，不要让连接层噪声污染业务结论。
  //
  // 200 并发抢 100 件，本身就是 2:1 的争抢压力，足以暴露超卖。
  // token 索引 = __ITER * 200 + (__VU - 1)，正好覆盖 0..999 且不重复。
  oversell: {
    executor: 'per-vu-iterations',
    vus: 200,
    iterations: 5,
    maxDuration: '3m',
  },
  // 同一个 token 被 200 个 VU 并发使用 —— 专门测一人一单的原子性
  single: {
    executor: 'shared-iterations',
    vus: 200,
    iterations: 200,
    maxDuration: '2m',
  },
  // 突发 1000 请求，总数超过桶容量 500，用来验证限流挡在前面
  rate: {
    executor: 'shared-iterations',
    vus: 200,
    iterations: 1000,
    maxDuration: '2m',
  },
  throughput: {
    executor: 'constant-vus',
    // 可用 -e VUS=20 覆盖，用来画「并发 → 延迟」曲线
    vus: parseInt(__ENV.VUS || '200'),
    duration: __ENV.DURATION || '30s',
  },
  // 只放一批货、让消费者慢慢做完：用来量「消费端 2 个线程每秒能干多少单」。
  // 每个 VU 只发 1 次，索引走下面的 else 分支 = __VU - 1，所以 tokens 至少要有 300 个。
  // 服务器端要先把 seckill_activity.stock 设成 1000，否则会被条件 UPDATE 挡掉。
  consume: {
    executor: 'per-vu-iterations',
    vus: parseInt(__ENV.VUS || '300'),
    iterations: 1,
    maxDuration: '2m',
  },
};

export const options = {
  scenarios: { main: SCENARIOS[MODE] },
  // 只要出现 HTTP 层失败就算不通过。业务结果用上面的 Counter 单独看
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  // token 索引规则：
  //   single     —— 所有 VU 共用 tokens[0]，专门测一人一单
  //   oversell   —— __ITER * 200 + (__VU-1)，覆盖 0..999，1000 个用户不重复
  //   其余       —— 按 VU 编号循环取
  let index;
  if (MODE === 'single') {
    index = 0;
  } else if (MODE === 'oversell') {
    index = (__ITER * 200 + (__VU - 1)) % tokens.length;
  } else {
    index = (__VU - 1) % tokens.length;
  }

  const res = http.post(
    `${BASE_URL}/api/order/seckill/${ACT_ID}`,
    null,
    {
      headers: { Authorization: `Bearer ${tokens[index]}` },
      tags: { mode: MODE },
    }
  );

  let message = '';
  try {
    message = res.json('data.message') || '';
  } catch (e) {
    message = '';
  }

  check(res, {
    'HTTP 200': (r) => r.status === 200,
    '有业务响应': () => message !== '',
  });

  if (message.includes('排队中')) {
    bizSuccess.add(1);
  } else if (message.includes('已售罄')) {
    bizSoldOut.add(1);
  } else if (message.includes('限购')) {
    bizLimit.add(1);
  } else if (message.includes('人数过多')) {
    bizRateLimited.add(1);
  } else {
    bizOther.add(1);
  }
}

// 只把完整摘要写成 JSON（给 verify.ps1 用）。
// 不动 stdout：k6 自带的终端摘要已经包含 QPS、分位数和 CUSTOM 计数器，够看了。
// 之前额外拼了一个中文 textSummary，里面某个字段取不到就抛异常，
// 导致整个 handleSummary 失效、文件也没写出来 —— 少即是多。
export function handleSummary(data) {
  return {
    [`result-${MODE}.json`]: JSON.stringify(data, null, 2),
  };
}
