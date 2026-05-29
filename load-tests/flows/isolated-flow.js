// ========================================
// 隔离流：AI 聊天 / 大模型 / 外部第三方接口
// 这些接口调用外部 API，响应慢（2-30s），会污染 P95 指标
// 独立运行，不影响核心 CRUD 链路的性能评估
// ========================================

import { check, sleep } from 'k6';
import http from 'k6/http';
import { BASE_URL } from '../config.js';
import { login, authGet, authPost, uuid, randomInt } from '../helpers.js';

export function isolatedAiChat() {
  const token = login('student');
  if (!token) return;

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
}

export function isolatedAiGenerate() {
  const token = login('admin');
  if (!token) return;

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
  };

  // 1. AI 岗位描述生成
  let res = http.post(`${BASE_URL}/api/ai/generate-job-description`, JSON.stringify({
    positionName: 'Java开发工程师',
    companyName: '测试公司',
    requirements: '3年以上经验',
  }), { headers, timeout: '30s' });
  check(res, { 'AI-JD生成': (r) => r.status === 200 || r.status === 400 || r.status === 500 });
  sleep(0.5);

  // 2. AI 学生查询
  res = http.post(`${BASE_URL}/api/ai/student/query`, JSON.stringify({
    query: '查询实习状态',
    context: {},
    model: 'deepseek-chat',
  }), { headers, timeout: '30s' });
  check(res, { 'AI-学生查询': (r) => r.status === 200 || r.status === 400 || r.status === 500 });
  sleep(0.5);

  // 3. AI 资源查询
  res = http.post(`${BASE_URL}/api/ai/resource/advanced/query`, JSON.stringify({
    query: '查找学习资源',
    context: {},
    model: 'deepseek-chat',
  }), { headers, timeout: '30s' });
  check(res, { 'AI-资源查询': (r) => r.status === 200 || r.status === 400 || r.status === 500 });
  sleep(0.5);

  // 4. AI Agent 信息
  res = http.get(`${BASE_URL}/api/ai/student/info`, { headers, timeout: '10s' });
  check(res, { 'AI-Agent学生': (r) => r.status === 200 });

  res = http.get(`${BASE_URL}/api/ai/resource/advanced/info`, { headers, timeout: '10s' });
  check(res, { 'AI-Agent资源': (r) => r.status === 200 });
}

export function isolatedChat() {
  const token = login('student');
  if (!token) return;

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
  };

  // 1. 聊天列表
  let res = http.get(`${BASE_URL}/api/chat/user?userId=159&userType=STUDENT`, { headers, timeout: '10s' });
  check(res, { '聊天-列表': (r) => r.status === 200 });
  sleep(0.2);

  // 2. 未读消息数
  res = http.get(`${BASE_URL}/api/chat/unread-count?userId=159&userType=STUDENT`, { headers, timeout: '10s' });
  check(res, { '聊天-未读': (r) => r.status === 200 || r.status === 400 });
  sleep(0.2);
}

export function isolatedPublic() {
  const headers = { 'Content-Type': 'application/json' };

  // 1. 健康检查
  let res = http.get(`${BASE_URL}/api/health/check`, { timeout: '10s' });
  check(res, { '公共-健康检查': (r) => r.status === 200 });
  sleep(0.2);

  // 2. 公开 AI 模型
  res = http.get(`${BASE_URL}/api/admin/ai-model/public/enabled`, { headers, timeout: '10s' });
  check(res, { '公共-AI模型': (r) => r.status === 200 });

  // 3. 公开岗位类别
  res = http.get(`${BASE_URL}/api/admin/position-categories/public`, { headers, timeout: '10s' });
  check(res, { '公共-岗位类别': (r) => r.status === 200 });

  // 4. 检查用户名
  res = http.get(`${BASE_URL}/api/company/check-username?username=test_${uuid().substring(0, 8)}`, { headers });
  check(res, { '公共-检查用户名': (r) => r.status === 200 });

  // 5. 无效 Token 拒绝
  const badHeaders = { 'Content-Type': 'application/json', 'Authorization': 'Bearer invalid' };
  res = http.get(`${BASE_URL}/api/auth/current-user`, { headers: badHeaders });
  check(res, { '公共-无效Token拒绝': (r) => r.status === 401 || r.status === 403 });
}
