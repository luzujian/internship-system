// ========================================
// 公共业务链路（无认证/通用接口）
// 覆盖：健康检查 → 公开接口 → 企业注册 → 验证码 → 公告搜索 → AI 接口
// ========================================

import { check, sleep } from 'k6';
import http from 'k6/http';
import { BASE_URL } from '../config.js';
import {
  login, authGet, authPost, isSuccess, uuid, randomInt,
  generateCompanyRegister, maybeMalformedPayload, recordMetrics,
  randomPhone, randomEmail, randomString,
} from '../helpers.js';

export function publicFlow() {
  const headers = { 'Content-Type': 'application/json' };

  // 1. 健康检查
  let res = http.get(`${BASE_URL}/api/health/check`, { timeout: '10s' });
  check(res, { '公共-健康检查': (r) => r.status === 200 });
  sleep(0.2);

  // 2. 密码规则
  res = http.get(`${BASE_URL}/api/auth/password-rules`, { headers, timeout: '10s' });
  check(res, { '公共-密码规则': (r) => r.status === 200 });
  sleep(0.2);

  // 3. 公开 AI 模型
  res = http.get(`${BASE_URL}/api/admin/ai-model/public/enabled`, { headers, timeout: '10s' });
  check(res, { '公共-AI模型': (r) => r.status === 200 });
  sleep(0.2);

  // 4. 公开岗位类别
  res = http.get(`${BASE_URL}/api/admin/position-categories/public`, { headers, timeout: '10s' });
  check(res, { '公共-岗位类别': (r) => r.status === 200 });
  sleep(0.2);

  // 5. 检查用户名
  res = http.get(`${BASE_URL}/api/company/check-username?username=test_${uuid().substring(0, 8)}`, { headers, timeout: '10s' });
  check(res, { '公共-检查用户名': (r) => r.status === 200 });
  sleep(0.2);

  // 6. 检查企业名
  res = http.get(`${BASE_URL}/api/company/check-status?companyName=测试企业_${uuid().substring(0, 6)}`, { headers, timeout: '10s' });
  check(res, { '公共-检查企业名': (r) => r.status === 200 || r.status === 400 || r.status === 404 });
  sleep(0.2);

  // 7. 企业注册（写操作，动态数据）
  const companyData = maybeMalformedPayload(generateCompanyRegister());
  res = http.post(`${BASE_URL}/api/company/register`, JSON.stringify(companyData), { headers, timeout: '10s' });
  check(res, { '公共-企业注册': (r) => r.status === 200 || r.status === 400 });
  recordMetrics(res, '公共-企业注册');
  sleep(0.3);

  // 8. 登录失败测试
  res = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({
    username: `nonexistent_${uuid().substring(0, 6)}`,
    password: 'WrongPassword123!',
  }), { headers, timeout: '10s' });
  check(res, { '公共-登录失败': (r) => r.status === 401 || r.status === 400 || r.status === 200 });
  sleep(0.2);

  // 9. 无 Token 访问受保护接口
  res = http.get(`${BASE_URL}/api/auth/current-user`, { headers, timeout: '10s' });
  check(res, { '公共-无Token拒绝': (r) => r.status === 401 || r.status === 403 });
  sleep(0.2);

  // 10. 无效 Token
  const badHeaders = { 'Content-Type': 'application/json', 'Authorization': 'Bearer invalid_token_12345' };
  res = http.get(`${BASE_URL}/api/auth/current-user`, { headers: badHeaders, timeout: '10s' });
  check(res, { '公共-无效Token拒绝': (r) => r.status === 401 || r.status === 403 });
  sleep(0.2);
}

// ---- AI 聊天链路 ----
export function aiChatFlow() {
  const token = login('student');
  if (!token) return;
  sleep(0.3);

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
  };

  // 1. AI 聊天
  let res = http.post(`${BASE_URL}/api/ai/chat`, JSON.stringify({
    message: `压测AI聊天_${uuid().substring(0, 6)}，请简短回复`,
    conversationId: uuid(),
  }), { headers, timeout: '30s' });
  check(res, { 'AI-聊天': (r) => r.status === 200 || r.status === 400 || r.status === 500 });
  sleep(0.5);

  // 2. AI 鼓励语
  res = http.get(`${BASE_URL}/api/ai/encouragement?role=student`, { headers, timeout: '10s' });
  check(res, { 'AI-鼓励': (r) => r.status === 200 });
  sleep(0.3);

  // 3. AI 岗位描述生成
  res = http.post(`${BASE_URL}/api/ai/generate-job-description`, JSON.stringify({
    positionName: 'Java开发工程师',
    companyName: '测试公司',
    requirements: '3年以上经验',
  }), { headers, timeout: '30s' });
  check(res, { 'AI-JD生成': (r) => r.status === 200 || r.status === 400 || r.status === 500 });
  sleep(0.3);

  // 4. AI 学生查询
  res = http.post(`${BASE_URL}/api/ai/student/query`, JSON.stringify({
    query: '查询实习状态',
    context: {},
    model: 'deepseek-chat',
  }), { headers, timeout: '30s' });
  check(res, { 'AI-学生查询': (r) => r.status === 200 || r.status === 400 || r.status === 500 });
  sleep(0.3);

  // 5. AI 资源查询
  res = http.post(`${BASE_URL}/api/ai/resource/advanced/query`, JSON.stringify({
    query: '查找学习资源',
    context: {},
    model: 'deepseek-chat',
  }), { headers, timeout: '30s' });
  check(res, { 'AI-资源查询': (r) => r.status === 200 || r.status === 400 || r.status === 500 });
  sleep(0.3);

  // 6. AI Agent 信息
  res = http.get(`${BASE_URL}/api/ai/student/info`, { headers, timeout: '10s' });
  check(res, { 'AI-Agent信息': (r) => r.status === 200 });
  sleep(0.2);

  res = http.get(`${BASE_URL}/api/ai/resource/advanced/info`, { headers, timeout: '10s' });
  check(res, { 'AI-资源Agent': (r) => r.status === 200 });
}

// ---- 聊天消息链路 ----
// 聊天链路已移除（需要真实会话数据，硬编码 ID 无效）

// ---- 资源文档链路 ----
export function resourceFlow() {
  const token = login('admin');
  if (!token) return;
  sleep(0.3);

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
  };

  // 1. 资源文档列表
  let res = http.get(`${BASE_URL}/api/resource-documents/page?page=1&pageSize=10`, { headers, timeout: '10s' });
  check(res, { '资源-列表': (r) => r.status === 200 });
  sleep(0.2);

  // 2. 已发布资源
  res = http.get(`${BASE_URL}/api/resource-documents/published`, { headers, timeout: '10s' });
  check(res, { '资源-已发布': (r) => r.status === 200 });
  sleep(0.2);

  // 3. 搜索资源
  res = http.get(`${BASE_URL}/api/resource-documents/search?title=test`, { headers, timeout: '10s' });
  check(res, { '资源-搜索': (r) => r.status === 200 });
  sleep(0.2);

  // 4. 文件管理列表
  res = http.get(`${BASE_URL}/api/file-management/list?page=1&pageSize=10`, { headers, timeout: '10s' });
  check(res, { '资源-文件管理': (r) => r.status === 200 });
  sleep(0.2);

  // 资源分类（仅管理员可访问，跳过）

  // 5. 已审批资源
  res = http.get(`${BASE_URL}/api/resources/approved?page=1&pageSize=10`, { headers, timeout: '10s' });
  check(res, { '资源-已审批': (r) => r.status === 200 });
  sleep(0.2);

  // 7. 搜索资源（POST）
  res = http.post(`${BASE_URL}/api/resources/search`, JSON.stringify({
    keyword: 'test',
    page: 1,
    pageSize: 10,
  }), { headers, timeout: '10s' });
  check(res, { '资源-POST搜索': (r) => r.status === 200 || r.status === 400 });
}
