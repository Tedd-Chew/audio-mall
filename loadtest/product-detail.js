// 商品详情压测：GET /api/product/1（缓存命中路径）
//
// 用途：和秒杀接口对比 —— 两者都是「两次 Redis 往返」，
// 差别只在 JWT 解析、Lua 脚本内部计算、响应体大小。
// 详见 docs/瓶颈定位实验.md 的实验 2。
//
// 用法（在 loadtest 目录下）：
//   k6 run -e VUS=200 -e DURATION=60s product-detail.js
//
// 不需要 token：/api/product/** 在 JwtInterceptor 的放行列表里。

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = parseInt(__ENV.VUS || '200');
const DURATION = __ENV.DURATION || '60s';

const bizOk = new Counter('biz_ok');
const bizOther = new Counter('biz_other');

export const options = {
  scenarios: {
    main: { executor: 'constant-vus', vus: VUS, duration: DURATION },
  },
  thresholds: { http_req_failed: ['rate<0.01'] },
};

export default function () {
  const res = http.get(`${BASE_URL}/api/product/1`);
  let code = -1;
  try {
    code = res.json('code');
  } catch (e) {
    code = -1;
  }
  check(res, { 'HTTP 200': (r) => r.status === 200, 'code=200': () => code === 200 });
  if (code === 200) {
    bizOk.add(1);
  } else {
    bizOther.add(1);
  }
}

export function handleSummary(data) {
  return {
    'result-product-detail.json': JSON.stringify(data, null, 2),
  };
}
