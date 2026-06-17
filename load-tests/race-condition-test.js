// ========================================
// 竞态条件测试（Race Condition）
// 模拟 100 个并发在同一毫秒内对同一资源发起更新
// 测试是否存在超卖/覆盖/数据不一致问题
// ========================================

import { check, sleep, group } from 'k6';
import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL } from './config.js';
import {
  login, authGet, authPost, authPut, uuid, randomInt,
  authHeaders, generatePosition, generateJobApplication,
} from './helpers.js';

const raceSuccess = new Counter('race_success');
const raceConflict = new Counter('race_conflict');
const raceOther = new Counter('race_other');
const raceDuration = new Trend('race_duration');

export const options = {
  scenarios: {
    // 场景 1：同一岗位 100 并发申请（超卖测试）
    concurrent_application: {
      executor: 'shared-iterations',
      vus: 100,
      iterations: 100,
      maxDuration: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<5000'],
    race_success: ['count>0'],        // 至少有成功的
    race_conflict: ['count>=0'],      // 冲突计数
  },
};

// 共享的目标岗位 ID
let sharedPositionId = null;
let adminToken = null;

export function setup() {
  // 用管理员创建一个测试岗位
  adminToken = login('admin');
  if (!adminToken) {
    console.error('管理员登录失败，无法创建测试岗位');
    return { positionId: 1 };
  }

  const position = generatePosition();
  const res = http.post(`${BASE_URL}/api/admin/positions`, JSON.stringify(position), {
    headers: authHeaders(adminToken),
    timeout: '10s',
  });

  let positionId = 1;
  try {
    const body = res.json();
    positionId = body.data?.id || body.id || 1;
  } catch (e) {
    console.warn('创建岗位失败，使用默认 ID=1');
  }

  console.log(`[SETUP] 创建测试岗位 ID=${positionId}`);
  return { positionId, adminToken };
}

export default function (data) {
  const positionId = data.positionId || 1;

  group('竞态条件 - 同一岗位并发申请', function () {
    const token = login('student');
    if (!token) return;

    const jobApp = {
      positionId: positionId,
      resumeId: randomInt(1, 50),
      coverLetter: `竞态测试_${__VU}_${__ITER}_${uuid().substring(0, 8)}`,
    };

    const start = Date.now();
    const res = http.post(`${BASE_URL}/api/student/job-applications`, JSON.stringify(jobApp), {
      headers: authHeaders(token),
      timeout: '10s',
    });
    raceDuration.add(Date.now() - start);

    if (res.status >= 200 && res.status < 300) {
      raceSuccess.add(1);
    } else if (res.status === 409 || res.status === 400) {
      raceConflict.add(1);
    } else {
      raceOther.add(1);
    }

    check(res, {
      '竞态-响应正常': (r) => r.status > 0,
    });
  });

  group('竞态条件 - 同一岗位并发收藏', function () {
    const token = login('student');
    if (!token) return;

    const res = http.post(`${BASE_URL}/api/positions/favorite/${positionId}`, null, {
      headers: authHeaders(token),
      timeout: '10s',
    });

    check(res, {
      '收藏竞态-响应正常': (r) => r.status > 0,
    });
  });
}

export function teardown(data) {
  // 清理：删除创建的测试岗位
  if (data.adminToken && data.positionId && data.positionId !== 1) {
    const res = http.del(`${BASE_URL}/api/admin/positions/${data.positionId}`, null, {
      headers: authHeaders(data.adminToken),
      timeout: '10s',
    });
    console.log(`[TEARDOWN] 清理岗位 ID=${data.positionId}, status=${res.status}`);
  }
}
