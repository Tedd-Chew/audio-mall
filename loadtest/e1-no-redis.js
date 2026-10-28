// E1：这套系统到底能跑多快 —— 三级阶梯
//
// 目的：判断「秒杀链路 ~900 QPS 的上限」和「单请求 15~23ms 的固定成本」到底是不是 Redis 造成的。
//
// 做法：同一个 k6、同一台机器、同样并发，只换被请求的路径，一层层加东西：
//
//   PATH=/api/nope              → 没有 handler（404）。不碰 Redis、不碰 MySQL、不投 MQ
//   PATH=/api/seckill/1         → 有 handler，查一次 MySQL（读活动详情）。不碰 Redis
//   PATH=/api/order/seckill/1   → 全链路：鉴权查 Redis + Lua 一次往返（正常会因未带 token 被 401 挡掉）
//
// 注意第二、三条路径都在 WebConfig 的 excludePathPatterns 里，不带 token 也能进：
//   /api/seckill/*   排除（详情页不登录也能看）
//   /api/nope        没有 handler，Spring 根本不会建立拦截器链，所以也不碰 Redis
//
// 用法：
//   k6 run -e PATH=/api/nope          -e VUS=200 e1-no-redis.js
//   k6 run -e PATH=/api/seckill/1     -e VUS=200 e1-no-redis.js

import http from 'k6/http';

const PATH = __ENV.PATH || '/api/nope';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = parseInt(__ENV.VUS || '200');
const DURATION = __ENV.DURATION || '20s';

export const options = {
  scenarios: {
    main: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
    },
  },
  // 故意不设 http_req_failed 阈值：404 和 401 都是预期结果，不是失败
  thresholds: {},
};

export default function () {
  http.get(`${BASE_URL}${PATH}`, { tags: { path: PATH } });
}

// 只写 JSON，不打 stdout —— 文件名带上路径，避免三次跑互相覆盖
export function handleSummary(data) {
  const safe = PATH.replace(/[^a-zA-Z0-9]/g, '_');
  return {
    [`result-e1${safe}.json`]: JSON.stringify(data, null, 2),
  };
}
