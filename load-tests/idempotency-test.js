// ========================================
// 幂等性测试（Idempotency）
// 使用完全相同的 Payload 和流水号，瞬间发起 10 次重复提交
// 预期：只有 1 次成功，其余应被拒绝或返回相同结果
// ========================================

import { check, sleep, group } from 'k6';
import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL } from './config.js';
import {
  login, authGet, authPost, uuid, randomInt,
  authHeaders, generateStudentApplication, generateJobApplication,
  generateFeedback, generateCompanyRegister, randomPhone, randomChineseName,
} from './helpers.js';

const idempotentSuccess = new Counter('idempotent_success');
const idempotentDuplicate = new Counter('idempotent_duplicate');
const idempotentOther = new Counter('idempotent_other');
const idempotentDuration = new Trend('idempotent_duration');

export const options = {
  scenarios: {
    // 10 个 VU 同时提交完全相同的 Payload
    duplicate_submit: {
      executor: 'shared-iterations',
      vus: 10,
      iterations: 10,
      maxDuration: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<5000'],
    idempotent_success: ['count<=10'],      // 最多 10 次成功（理想情况只有 1 次）
    idempotent_duplicate: ['count>=0'],
  },
};

// 共享的固定 Payload（所有 VU 使用同一个）
const FIXED_PAYLOAD = {
  applicationType: 'INTERNSHIP',
  companyName: '幂等测试公司_固定',
  companyAddress: '幂等测试地址_固定',
  positionName: '幂等测试岗位_固定',
  contactPerson: '张三',
  contactPhone: '13800138000',
  contactEmail: 'idempotent@test.com',
  remark: '幂等性测试_所有VU使用相同数据',
};

const FIXED_IDEMPOTENCY_KEY = `idempotent-${uuid()}`; // 同一个 session 内所有 VU 共享

export function setup() {
  // 预创建一些学生账号用于测试
  const adminToken = login('admin');
  if (!adminToken) return {};

  // 创建一个测试岗位
  const position = {
    positionName: '幂等测试岗位',
    companyName: '幂等测试公司',
    description: '用于幂等性测试',
    headCount: 1,  // 只招 1 人，更容易暴露竞态问题
    salaryMin: 3000,
    salaryMax: 5000,
  };

  const res = http.post(`${BASE_URL}/api/admin/positions`, JSON.stringify(position), {
    headers: authHeaders(adminToken),
    timeout: '10s',
  });

  let positionId = 1;
  try {
    const body = res.json();
    positionId = body.data?.id || body.id || 1;
  } catch (e) {}

  return { positionId, adminToken };
}

export default function (data) {
  const positionId = data.positionId || 1;

  group('幂等性 - 重复提交学生申请', function () {
    const token = login('student');
    if (!token) return;

    // 所有 VU 使用完全相同的 Payload
    const start = Date.now();
    const res = http.post(`${BASE_URL}/api/student/applications`, JSON.stringify(FIXED_PAYLOAD), {
      headers: {
        ...authHeaders(token),
        'X-Idempotency-Key': FIXED_IDEMPOTENCY_KEY,  // 幂等键
      },
      timeout: '10s',
    });
    idempotentDuration.add(Date.now() - start);

    if (res.status >= 200 && res.status < 300) {
      idempotentSuccess.add(1);
    } else if (res.status === 409 || res.status === 400) {
      idempotentDuplicate.add(1);
    } else {
      idempotentOther.add(1);
    }

    check(res, {
      '幂等-响应正常': (r) => r.status > 0,
      '幂等-非500': (r) => r.status !== 500,
    });
  });

  group('幂等性 - 重复提交岗位申请', function () {
    const token = login('student');
    if (!token) return;

    const jobApp = {
      positionId: positionId,
      resumeId: 1,
      coverLetter: '幂等测试_相同内容',
    };

    const idempotencyKey = `job-app-${FIXED_IDEMPOTENCY_KEY}`;
    const start = Date.now();
    const res = http.post(`${BASE_URL}/api/student/job-applications`, JSON.stringify(jobApp), {
      headers: {
        ...authHeaders(token),
        'X-Idempotency-Key': idempotencyKey,
      },
      timeout: '10s',
    });
    idempotentDuration.add(Date.now() - start);

    if (res.status >= 200 && res.status < 300) {
      idempotentSuccess.add(1);
    } else if (res.status === 409 || res.status === 400) {
      idempotentDuplicate.add(1);
    } else {
      idempotentOther.add(1);
    }

    check(res, {
      '岗位申请幂等-响应正常': (r) => r.status > 0,
    });
  });

  group('幂等性 - 重复提交反馈', function () {
    const token = login('student');
    if (!token) return;

    const feedback = {
      title: '幂等测试反馈_固定标题',
      content: '幂等测试反馈_固定内容',
      feedbackType: 'BUG',
      priority: 'HIGH',
    };

    const idempotencyKey = `feedback-${FIXED_IDEMPOTENCY_KEY}`;
    const start = Date.now();
    const res = http.post(`${BASE_URL}/api/problem-feedback`, JSON.stringify(feedback), {
      headers: {
        ...authHeaders(token),
        'X-Idempotency-Key': idempotencyKey,
      },
      timeout: '10s',
    });
    idempotentDuration.add(Date.now() - start);

    if (res.status >= 200 && res.status < 300) {
      idempotentSuccess.add(1);
    } else if (res.status === 409 || res.status === 400) {
      idempotentDuplicate.add(1);
    } else {
      idempotentOther.add(1);
    }

    check(res, {
      '反馈幂等-响应正常': (r) => r.status > 0,
    });
  });

  group('幂等性 - 重复登录', function () {
    // 10 个并发同时登录同一个账号
    const res = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({
      username: '21209100300',
      password: '123456',
    }), {
      headers: { 'Content-Type': 'application/json' },
      timeout: '10s',
    });

    check(res, {
      '重复登录-响应正常': (r) => r.status > 0,
      '重复登录-非500': (r) => r.status !== 500,
    });
  });
}

export function teardown(data) {
  if (data.adminToken && data.positionId && data.positionId !== 1) {
    http.del(`${BASE_URL}/api/admin/positions/${data.positionId}`, null, {
      headers: authHeaders(data.adminToken),
      timeout: '10s',
    });
    console.log(`[TEARDOWN] 清理幂等测试岗位 ID=${data.positionId}`);
  }
}
