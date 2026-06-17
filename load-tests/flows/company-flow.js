// ========================================
// 企业业务链路（仅企业角色有权访问的端点）
// ========================================

import { check, sleep } from 'k6';
import {
  login, authGet, authPost, authPut, isSuccess, uuid, randomInt,
  generatePosition, maybeMalformedPayload, recordMetrics, randomPhone, randomChineseName,
} from '../helpers.js';
import { REAL_DATA } from '../config.js';

export function companyFlow() {
  const token = login('company');
  if (!token) return;
  sleep(0.5);

  const companyId = REAL_DATA.companyId;

  // 1. 获取当前用户
  let res = authGet('/api/auth/current-user', token);
  check(res, { '企业-当前用户': (r) => r.status === 200 });
  sleep(0.3);

  // 2. 企业资料
  res = authGet('/api/company/profile', token);
  check(res, { '企业-资料': (r) => r.status === 200 });
  sleep(0.2);

  // 3. 更新企业资料
  res = authPut('/api/company/profile', {
    contactPhone: randomPhone(),
    contactPerson: randomChineseName(),
    address: `更新地址_${uuid().substring(0, 6)}`,
  }, token);
  check(res, { '企业-更新资料': (r) => r.status === 200 || r.status === 400 });
  sleep(0.2);

  // 4. 企业统计
  res = authGet('/api/company/stats', token);
  check(res, { '企业-统计': (r) => r.status === 200 });
  sleep(0.2);

  // 5. 最近职位
  res = authGet('/api/company/positions/recent', token);
  check(res, { '企业-最近职位': (r) => r.status === 200 });
  sleep(0.2);

  // 6. 我的职位列表
  res = authGet('/api/company/positions?page=1&pageSize=10', token);
  check(res, { '企业-职位列表': (r) => r.status === 200 });
  sleep(0.3);

  // 7. 创建职位（写操作，动态数据）
  const position = maybeMalformedPayload(generatePosition());
  res = authPost('/api/company/positions', position, token);
  check(res, { '企业-创建职位': (r) => r.status === 200 || r.status === 400 });
  recordMetrics(res, '企业-创建职位');
  let createdPositionId = null;
  try { const body = res.json(); createdPositionId = body.data?.id || body.id; } catch (e) {}
  sleep(0.3);

  // 8. 更新职位（使用刚创建的 ID）
  if (createdPositionId) {
    res = authPut(`/api/company/positions/${createdPositionId}`, {
      ...position,
      positionName: `更新岗位_${uuid().substring(0, 6)}`,
      headCount: randomInt(1, 100),
    }, token);
    check(res, { '企业-更新职位': (r) => r.status === 200 || r.status === 400 });
    sleep(0.2);
  }

  // 9. 岗位申请列表
  res = authGet('/api/company/job-applications', token);
  check(res, { '企业-岗位申请': (r) => r.status === 200 });
  sleep(0.2);

  // 10. 通用申请列表
  res = authGet('/api/company/applications', token);
  check(res, { '企业-申请列表': (r) => r.status === 200 });
  sleep(0.2);

  // 11. 最近申请
  res = authGet('/api/company/applications/recent?limit=10', token);
  check(res, { '企业-最近申请': (r) => r.status === 200 });
  sleep(0.2);

  // 12. 待确认列表
  res = authGet('/api/company/confirmation/pending', token);
  check(res, { '企业-待确认': (r) => r.status === 200 });
  sleep(0.2);

  // 13. 全部确认
  res = authGet('/api/company/confirmation/all', token);
  check(res, { '企业-全部确认': (r) => r.status === 200 });
  sleep(0.2);

  // 14. 通知列表
  res = authGet('/api/company/notifications?limit=10', token);
  check(res, { '企业-通知': (r) => r.status === 200 });
  sleep(0.2);

  // 15. 问题反馈
  const feedback = maybeMalformedPayload({
    title: `企业反馈_${uuid().substring(0, 6)}`,
    content: '企业端测试反馈',
    feedbackType: 'SUGGESTION',
    priority: 'MEDIUM',
  });
  res = authPost('/api/problem-feedback', feedback, token);
  check(res, { '企业-提交反馈': (r) => r.status === 200 || r.status === 400 });
  sleep(0.2);

  // 16. 我的反馈（使用真实 companyId）
  res = authGet(`/api/problem-feedback/my-feedback?userType=COMPANY&userId=${companyId}`, token);
  check(res, { '企业-我的反馈': (r) => r.status === 200 });
  sleep(0.2);

  // 17. 账户信息
  res = authGet('/api/account-settings/info', token);
  check(res, { '企业-账户信息': (r) => r.status === 200 });
  sleep(0.2);

  // 18. 企业信息
  res = authGet(`/api/company/info?companyId=${companyId}`, token);
  check(res, { '企业-信息': (r) => r.status === 200 || r.status === 404 });
}
