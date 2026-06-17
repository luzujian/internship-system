// ========================================
// 工具函数：认证、动态数据、异常注入
// ========================================

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, TEST_ACCOUNTS, MALFORMED_PROBABILITY } from './config.js';

// ---- UUID 生成 ----
export function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

// ---- 随机字符串 ----
export function randomString(length = 8) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let result = '';
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}

// ---- 随机数字 ----
export function randomInt(min = 1, max = 99999) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

// ---- 随机手机号 ----
export function randomPhone() {
  const prefixes = ['138', '139', '150', '151', '152', '186', '187', '188'];
  return prefixes[Math.floor(Math.random() * prefixes.length)] + String(randomInt(10000000, 99999999));
}

// ---- 随机邮箱 ----
export function randomEmail() {
  return `test_${uuid().substring(0, 8)}@test.com`;
}

// ---- 随机中文名 ----
const SURNAMES = ['张', '李', '王', '赵', '刘', '陈', '杨', '黄', '周', '吴'];
const GIVEN_NAMES = ['伟', '芳', '娜', '敏', '静', '强', '磊', '洋', '勇', '艳', '杰', '娟', '涛', '明', '超'];
export function randomChineseName() {
  return SURNAMES[Math.floor(Math.random() * SURNAMES.length)] +
    GIVEN_NAMES[Math.floor(Math.random() * GIVEN_NAMES.length)];
}

// ---- 登录获取 Token ----
export function login(accountKey) {
  const account = TEST_ACCOUNTS[accountKey];
  const url = `${BASE_URL}/api/auth/login`;
  const payload = JSON.stringify({
    username: account.username,
    password: account.password,
  });
  const params = { headers: { 'Content-Type': 'application/json' }, timeout: '10s' };
  const res = http.post(url, payload, params);

  const success = check(res, {
    [`登录成功 (${accountKey})`]: (r) => r.status === 200 && r.json('code') === 200,
  });

  if (!success) {
    console.error(`登录失败 [${accountKey}]: status=${res.status}, body=${res.body}`);
    return null;
  }

  try {
    const body = res.json();
    return body.data?.token || body.token || body.data?.accessToken;
  } catch (e) {
    console.error(`解析 token 失败: ${e}`);
    return null;
  }
}

// ---- 带 Token 的请求头 ----
export function authHeaders(token) {
  return {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
  };
}

// ---- 通用 GET 请求 ----
export function authGet(path, token, params = {}) {
  const url = `${BASE_URL}${path}`;
  const headers = authHeaders(token);
  return http.get(url, { headers, ...params, timeout: '10s' });
}

// ---- 通用 POST 请求 ----
export function authPost(path, body, token, params = {}) {
  const url = `${BASE_URL}${path}`;
  const headers = authHeaders(token);
  return http.post(url, JSON.stringify(body), { headers, ...params, timeout: '10s' });
}

// ---- 通用 PUT 请求 ----
export function authPut(path, body, token, params = {}) {
  const url = `${BASE_URL}${path}`;
  const headers = authHeaders(token);
  return http.put(url, JSON.stringify(body), { headers, ...params, timeout: '10s' });
}

// ---- 通用 DELETE 请求 ----
export function authDelete(path, token, params = {}) {
  const url = `${BASE_URL}${path}`;
  const headers = authHeaders(token);
  return http.del(url, null, { headers, ...params, timeout: '10s' });
}

// ---- 检查响应是否成功 ----
export function isSuccess(res, name = '请求') {
  return check(res, {
    [`${name} 状态码 2xx`]: (r) => r.status >= 200 && r.status < 300,
  });
}

// ---- 检查响应状态码（允许特定错误码）----
export function checkStatus(res, name, allowedCodes = [200]) {
  return check(res, {
    [`${name} 状态码符合预期`]: (r) => allowedCodes.includes(r.status),
  });
}

// ---- 生成动态 Position 数据 ----
export function generatePosition() {
  const id = uuid().substring(0, 8);
  return {
    positionName: `测试岗位_${id}`,
    companyName: `测试公司_${id}`,
    description: `这是一个自动化测试生成的岗位描述_${id}，包含各种职责和要求。`,
    requirements: `本科及以上学历，熟悉相关技术栈_${id}`,
    salaryMin: randomInt(3000, 8000),
    salaryMax: randomInt(8000, 20000),
    location: `测试城市_${id}`,
    contactPerson: randomChineseName(),
    contactPhone: randomPhone(),
    contactEmail: randomEmail(),
    headCount: randomInt(1, 50),
    categoryId: randomInt(1, 10),
  };
}

// ---- 生成动态学生申请数据 ----
export function generateStudentApplication() {
  const id = uuid().substring(0, 8);
  return {
    applicationType: 'INTERNSHIP',
    companyName: `申请公司_${id}`,
    companyAddress: `城市_${id}某某路${randomInt(1, 999)}号`,
    positionName: `申请岗位_${id}`,
    contactPerson: randomChineseName(),
    contactPhone: randomPhone(),
    contactEmail: randomEmail(),
    remark: `自动化测试申请_${id}`,
  };
}

// ---- 生成动态岗位申请数据 ----
export function generateJobApplication(positionId) {
  return {
    positionId: positionId || randomInt(1, 100),
    resumeId: randomInt(1, 50),
    coverLetter: `尊敬的HR，我是自动化测试生成的求职信_${uuid().substring(0, 8)}。`,
  };
}

// ---- 生成动态企业注册数据 ----
export function generateCompanyRegister() {
  const id = uuid().substring(0, 8);
  return {
    username: `company_${id}`,
    password: 'Test@123456',
    companyName: `测试企业_${id}`,
    contactPerson: randomChineseName(),
    contactPhone: randomPhone(),
    contactEmail: randomEmail(),
    businessLicense: `LICENSE_${id}`,
    address: `企业地址_${id}`,
    industry: 'IT互联网',
    scale: '100-500人',
    description: `企业简介_${id}`,
  };
}

// ---- 生成动态公告数据 ----
export function generateAnnouncement() {
  const id = uuid().substring(0, 8);
  return {
    title: `测试公告_${id}`,
    content: `这是自动化测试生成的公告内容_${id}，用于压测验证。`,
    publisherRole: 'ADMIN',
    status: 'PUBLISHED',
    targetType: 'ALL',
  };
}

// ---- 生成动态反馈数据 ----
export function generateFeedback() {
  const id = uuid().substring(0, 8);
  return {
    title: `测试反馈_${id}`,
    content: `自动化测试反馈内容_${id}，描述遇到的问题。`,
    feedbackType: 'BUG',
    priority: 'MEDIUM',
    contactInfo: randomPhone(),
  };
}

// ---- 生成动态实习确认数据 ----
export function generateInternshipConfirmation() {
  const id = uuid().substring(0, 8);
  return {
    companyName: `确认企业_${id}`,
    companyAddress: `确认地址_${id}`,
    positionName: `确认岗位_${id}`,
    contactPerson: randomChineseName(),
    contactPhone: randomPhone(),
    startDate: '2026-01-01',
    endDate: '2026-06-30',
    remark: `自动化测试确认_${id}`,
  };
}

// ---- 生成动态实习心得数据 ----
export function generateInternshipReflection() {
  const id = uuid().substring(0, 8);
  return {
    title: `实习心得_${id}`,
    content: `这是自动化测试生成的实习心得内容_${id}。在实习期间，我学到了很多专业知识，提升了自己的实践能力。通过参与项目开发，我对行业有了更深的理解。`,
    period: '2026-01',
    reflectionType: 'WEEKLY',
  };
}

// ---- 异常数据注入 ----
export function maybeMalformedPayload(normalPayload, probability = MALFORMED_PROBABILITY) {
  if (Math.random() > probability) return normalPayload;

  const strategy = Math.floor(Math.random() * 5);
  switch (strategy) {
    case 0: // 超大文本
      return { ...normalPayload, _malformed: 'x'.repeat(100000) };
    case 1: // 空值字段
      return Object.fromEntries(Object.entries(normalPayload).map(([k]) => [k, null]));
    case 2: // 非法类型
      return Object.fromEntries(Object.entries(normalPayload).map(([k, v]) => [k, typeof v === 'string' ? 12345 : 'invalid']));
    case 3: // 完全空对象
      return {};
    case 4: // SQL注入尝试
      return Object.fromEntries(Object.entries(normalPayload).map(([k, v]) => [k, typeof v === 'string' ? `'; DROP TABLE ${k}; --` : v]));
    default:
      return normalPayload;
  }
}

// ---- 请求指标记录 ----
export function recordMetrics(res, name) {
  check(res, {
    [`${name} 响应时间 < 5s`]: (r) => r.timings.duration < 5000,
  });
}
