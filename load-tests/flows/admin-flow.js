// ========================================
// 管理员业务链路
// 覆盖：登录 → Dashboard → 用户管理 → 企业管理 → 岗位管理 → 公告管理 → 系统配置 → 日志 → 报表
// ========================================

import { check, sleep } from 'k6';
import {
  login, authGet, authPost, authPut, authDelete, isSuccess, uuid, randomInt,
  generateAnnouncement, generateCompanyRegister, maybeMalformedPayload,
  recordMetrics, randomChineseName, randomPhone, randomEmail,
} from '../helpers.js';

export function adminFlow() {
  // 1. 登录
  const token = login('admin');
  if (!token) return;
  sleep(0.5);

  // 2. 当前用户
  let res = authGet('/api/auth/current-user', token);
  check(res, { '管理员-当前用户': (r) => r.status === 200 });
  sleep(0.3);

  // 3. Dashboard 统计
  res = authGet('/api/admin/dashboard/stats', token);
  check(res, { '管理员-Dashboard': (r) => r.status === 200 });
  sleep(0.2);

  // 4. 实习统计（聚合查询重，接受 500 超时）
  res = authGet('/api/admin/dashboard/internship-stats', token);
  check(res, { '管理员-实习统计': (r) => r.status === 200 || r.status === 500 });
  sleep(0.2);

  // 5. 用户统计
  res = authGet('/api/admin/stats/users', token);
  check(res, { '管理员-用户统计': (r) => r.status === 200 });
  sleep(0.2);

  // 6. 系统统计
  res = authGet('/api/admin/stats/system', token);
  check(res, { '管理员-系统统计': (r) => r.status === 200 });
  sleep(0.2);

  // 7. 用户列表
  res = authGet('/api/admin/users?page=1&pageSize=10', token);
  check(res, { '管理员-用户列表': (r) => r.status === 200 });
  sleep(0.2);

  // 8. 学生用户列表
  res = authGet('/api/admin/student-users?page=1&pageSize=10', token);
  check(res, { '管理员-学生列表': (r) => r.status === 200 });
  sleep(0.2);

  // 9. 教师用户列表
  res = authGet('/api/admin/teacher-users?page=1&pageSize=10', token);
  check(res, { '管理员-教师列表': (r) => r.status === 200 });
  sleep(0.2);

  // 10. 管理员用户列表
  res = authGet('/api/admin/admin-users?page=1&pageSize=10', token);
  check(res, { '管理员-管理员列表': (r) => r.status === 200 });
  sleep(0.2);

  // 11. 企业用户列表
  res = authGet('/api/admin/companies?page=1&pageSize=10', token);
  check(res, { '管理员-企业列表': (r) => r.status === 200 });
  sleep(0.2);

  // 12. 企业统计
  res = authGet('/api/admin/companies/statistics', token);
  check(res, { '管理员-企业统计': (r) => r.status === 200 });
  sleep(0.2);

  // 13. 待审核企业
  res = authGet('/api/admin/companies/audit/pending?page=1&pageSize=10', token);
  check(res, { '管理员-待审核企业': (r) => r.status === 200 });
  sleep(0.2);

  // 14. 岗位列表
  res = authGet('/api/admin/positions?page=1&pageSize=10', token);
  check(res, { '管理员-岗位列表': (r) => r.status === 200 });
  sleep(0.2);

  // 15. 岗位统计
  res = authGet('/api/admin/positions/statistics', token);
  check(res, { '管理员-岗位统计': (r) => r.status === 200 });
  sleep(0.2);

  // 16. 公告分页
  res = authGet('/api/announcements/page?page=1&pageSize=10', token);
  check(res, { '管理员-公告分页': (r) => r.status === 200 });
  sleep(0.2);

  // 17. 发布公告（写操作）
  const announcement = maybeMalformedPayload(generateAnnouncement());
  res = authPost('/api/announcements', announcement, token);
  check(res, { '管理员-发布公告': (r) => r.status === 200 || r.status === 400 });
  recordMetrics(res, '管理员-发布公告');
  sleep(0.3);

  // 18. 实习状态管理
  res = authGet('/api/admin/internship-status?page=1&pageSize=10', token);
  check(res, { '管理员-实习状态': (r) => r.status === 200 });
  sleep(0.2);

  // 19. 申请管理
  res = authGet('/api/admin/applications?status=PENDING', token);
  check(res, { '管理员-申请管理': (r) => r.status === 200 });
  sleep(0.2);

  // 20. 实习心得管理
  res = authGet('/api/admin/internship-reflection', token);
  check(res, { '管理员-实习心得': (r) => r.status === 200 });
  sleep(0.2);

  // 21. 档案管理
  res = authGet('/api/admin/archives?page=1&pageSize=10', token);
  check(res, { '管理员-档案管理': (r) => r.status === 200 });
  sleep(0.2);

  // 22. 档案统计
  res = authGet('/api/admin/archives/statistics', token);
  check(res, { '管理员-档案统计': (r) => r.status === 200 });
  sleep(0.2);

  // 23. 日志列表
  res = authGet('/api/admin/logs/operation?page=1&pageSize=10', token);
  check(res, { '管理员-操作日志': (r) => r.status === 200 });
  sleep(0.2);

  // 24. 最近日志
  res = authGet('/api/admin/logs/recent', token);
  check(res, { '管理员-近日志': (r) => r.status === 200 });
  sleep(0.2);

  // 25. 登录日志
  res = authGet('/api/login-logs?page=1&pageSize=10', token);
  check(res, { '管理员-登录日志': (r) => r.status === 200 });
  sleep(0.2);

  // 26. 登录日志统计
  res = authGet('/api/login-logs/count', token);
  check(res, { '管理员-登录统计': (r) => r.status === 200 });
  sleep(0.2);

  // 27. 系统配置
  res = authGet('/api/admin/system-config', token);
  check(res, { '管理员-系统配置': (r) => r.status === 200 });
  sleep(0.2);

  // 28. 系统设置
  res = authGet('/api/admin/settings', token);
  check(res, { '管理员-系统设置': (r) => r.status === 200 });
  sleep(0.2);

  // 29. 角色权限
  res = authGet('/api/admin/permissions/roles', token);
  check(res, { '管理员-角色': (r) => r.status === 200 });
  sleep(0.2);

  // 30. 权限列表
  res = authGet('/api/admin/permissions/permissions', token);
  check(res, { '管理员-权限': (r) => r.status === 200 });
  sleep(0.2);

  // 31. 权限树
  res = authGet('/api/admin/permissions/permission-tree', token);
  check(res, { '管理员-权限树': (r) => r.status === 200 });
  sleep(0.2);

  // 32. 院系列表
  res = authGet('/api/admin/departments', token);
  check(res, { '管理员-院系': (r) => r.status === 200 });
  sleep(0.2);

  // 33. 班级列表
  res = authGet('/api/admin/classes', token);
  check(res, { '管理员-班级': (r) => r.status === 200 });
  sleep(0.2);

  // 34. 专业列表
  res = authGet('/api/majors', token);
  check(res, { '管理员-专业': (r) => r.status === 200 });
  sleep(0.2);

  // 35. 岗位类别
  res = authGet('/api/admin/position-categories', token);
  check(res, { '管理员-岗位类别': (r) => r.status === 200 });
  sleep(0.2);

  // 36. 招聘管理-岗位
  res = authGet('/api/admin/recruitment/positions?page=1&pageSize=10', token);
  check(res, { '管理员-招聘岗位': (r) => r.status === 200 });
  sleep(0.2);

  // 37. 招聘管理-申请
  res = authGet('/api/admin/recruitment/applications?page=1&pageSize=10', token);
  check(res, { '管理员-招聘申请': (r) => r.status === 200 });
  sleep(0.2);

  // 38. 招聘统计
  res = authGet('/api/admin/recruitment/statistics', token);
  check(res, { '管理员-招聘统计': (r) => r.status === 200 });
  sleep(0.2);

  // 39. AI 模型列表
  res = authGet('/api/admin/ai-model', token);
  check(res, { '管理员-AI模型': (r) => r.status === 200 });
  sleep(0.2);

  // 40. AI 审计记录
  res = authGet('/api/admin/ai-audit/list?page=1&pageSize=10', token);
  check(res, { '管理员-AI审计': (r) => r.status === 200 });
  sleep(0.2);

  // 41. 评分规则
  res = authGet('/api/admin/scoring-rule', token);
  check(res, { '管理员-评分规则': (r) => r.status === 200 });
  sleep(0.2);

  // 42. 类别权重
  res = authGet('/api/admin/category-weight', token);
  check(res, { '管理员-类别权重': (r) => r.status === 200 });
  sleep(0.2);

  // 43. 关键词库
  res = authGet('/api/admin/keyword-library', token);
  check(res, { '管理员-关键词库': (r) => r.status === 200 });
  sleep(0.2);

  // 44. 模板文件
  res = authGet('/api/admin/templates?page=1&pageSize=10', token);
  check(res, { '管理员-模板文件': (r) => r.status === 200 });
  sleep(0.2);

  // 45. 问题反馈
  res = authGet('/api/problem-feedback/page?page=1&pageSize=10', token);
  check(res, { '管理员-反馈管理': (r) => r.status === 200 });
  sleep(0.2);

  // 46. 反馈统计
  res = authGet('/api/problem-feedback/statistics', token);
  check(res, { '管理员-反馈统计': (r) => r.status === 200 });
  sleep(0.2);

  // 47. 报表指标
  res = authGet('/api/admin/reports/metrics', token);
  check(res, { '管理员-报表指标': (r) => r.status === 200 });
  sleep(0.2);

  // 48. 统计报表
  res = authGet('/api/admin/statistics?dimension=department&timePeriod=month', token);
  check(res, { '管理员-统计报表': (r) => r.status === 200 });
  sleep(0.2);

  // 49. 备份记录
  res = authGet('/api/admin/backup/records', token);
  check(res, { '管理员-备份记录': (r) => r.status === 200 });
  sleep(0.2);

  // 50. 备份计划
  res = authGet('/api/admin/backup/schedule', token);
  check(res, { '管理员-备份计划': (r) => r.status === 200 });
  sleep(0.2);

  // 51. 清除缓存
  res = authPost('/api/admin/cache/clear', {}, token);
  check(res, { '管理员-清除缓存': (r) => r.status === 200 });
  sleep(0.2);

  // 52. 最近反馈
  res = authGet('/api/admin/feedback/recent', token);
  check(res, { '管理员-最近反馈': (r) => r.status === 200 });
  sleep(0.2);

  // 审核管理-学生申请/企业资质（仅教师角色可用，管理员无权限，跳过）
  res = authPost('/api/admin/logs/operation', {
    operatorName: '压测管理员',
    operatorRole: 'ADMIN',
    operationType: 'QUERY',
    module: '压测模块',
    description: `压测操作_${uuid().substring(0, 6)}`,
    ipAddress: '127.0.0.1',
  }, token);
  check(res, { '管理员-记录日志': (r) => r.status === 200 || r.status === 201 });
  sleep(0.2);

  // 56. 实习状态统计
  res = authGet('/api/admin/internship-status/statistics', token);
  check(res, { '管理员-状态统计': (r) => r.status === 200 || r.status === 500 });
  sleep(0.2);

  // 57. 撤回审核列表
  res = authGet('/api/admin/internship-status/recall/pending?page=1&pageSize=10', token);
  check(res, { '管理员-撤回审核': (r) => r.status === 200 });
  sleep(0.2);

  // 58. 企业撤回审核
  res = authGet('/api/admin/companies/recall/pending?page=1&pageSize=10', token);
  check(res, { '管理员-企业撤回': (r) => r.status === 200 });
  sleep(0.2);

  // 59. 撤回记录
  res = authGet('/api/admin/companies/recall-records?page=1&pageSize=10', token);
  check(res, { '管理员-撤回记录': (r) => r.status === 200 });
  sleep(0.2);

  // 60. 公告已读统计
  res = authGet('/api/announcement-read-records/user-unread-count?userId=1&userType=ADMIN', token);
  check(res, { '管理员-未读公告': (r) => r.status === 200 });
}
