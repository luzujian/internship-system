// ========================================
// 学生业务链路
// 覆盖：登录 → 首页 → 浏览岗位 → 申请岗位 → 查看申请 → 实习确认 → 实习心得 → 个人资料
// ========================================

import { check, sleep } from 'k6';
import {
  login, authGet, authPost, authPut, isSuccess, uuid, randomInt,
  generateJobApplication, generateStudentApplication,
  generateInternshipConfirmation, generateInternshipReflection,
  maybeMalformedPayload, recordMetrics,
} from '../helpers.js';
import { REAL_DATA } from '../config.js';

export function studentFlow() {
  // 1. 登录
  const token = login('student');
  if (!token) return;
  sleep(0.5);

  // 2. 获取当前用户信息
  let res = authGet('/api/auth/current-user', token);
  check(res, { '学生-获取当前用户': (r) => r.status === 200 });
  sleep(0.3);

  // 3. 首页统计
  res = authGet('/api/student/home/stats', token);
  check(res, { '学生-首页统计': (r) => r.status === 200 });
  sleep(0.2);

  // 4. 待办列表
  res = authGet('/api/student/home/todo-list', token);
  check(res, { '学生-待办列表': (r) => r.status === 200 });
  sleep(0.2);

  // 5. 进度列表
  res = authGet('/api/student/home/progress-list', token);
  check(res, { '学生-进度列表': (r) => r.status === 200 });
  sleep(0.2);

  // 6. 通知列表
  res = authGet('/api/student/home/notifications?studentId=1', token);
  check(res, { '学生-通知列表': (r) => r.status === 200 });
  sleep(0.2);

  // 7. 未读通知数
  res = authGet('/api/student/home/unread-count?studentId=1', token);
  check(res, { '学生-未读通知数': (r) => r.status === 200 });
  sleep(0.3);

  // 8. 浏览全部岗位
  res = authGet('/api/positions/all', token);
  check(res, { '学生-全部岗位': (r) => r.status === 200 });
  sleep(0.3);

  // 9. 最新岗位
  res = authGet('/api/positions/latest', token);
  check(res, { '学生-最新岗位': (r) => r.status === 200 });
  sleep(0.2);

  // 10. 热门岗位
  res = authGet('/api/positions/hot', token);
  check(res, { '学生-热门岗位': (r) => r.status === 200 });
  sleep(0.2);

  // 11. 岗位详情（随机 ID）
  const posId = randomInt(1, 50);
  res = authGet(`/api/positions/detail/${posId}`, token);
  check(res, { '学生-岗位详情': (r) => r.status === 200 || r.status === 404 });
  sleep(0.3);

  // 12. 收藏/取消收藏
  res = authPost(`/api/positions/favorite/${posId}`, {}, token);
  check(res, { '学生-收藏操作': (r) => r.status === 200 || r.status === 404 });
  sleep(0.2);

  // 13. 收藏列表
  res = authGet('/api/positions/favorites', token);
  check(res, { '学生-收藏列表': (r) => r.status === 200 });
  sleep(0.2);

  // 14. 行业选项
  res = authGet('/api/positions/options/industries', token);
  check(res, { '学生-行业选项': (r) => r.status === 200 });
  sleep(0.2);

  // 15. 企业选项
  res = authGet('/api/positions/options/companies', token);
  check(res, { '学生-企业选项': (r) => r.status === 200 });
  sleep(0.2);

  // 16. 地区选项
  res = authGet('/api/positions/options/regions', token);
  check(res, { '学生-地区选项': (r) => r.status === 200 });
  sleep(0.3);

  // 17. 提交岗位申请（写操作，动态数据）
  const jobApp = maybeMalformedPayload(generateJobApplication(posId));
  res = authPost('/api/student/job-applications', jobApp, token);
  check(res, { '学生-提交岗位申请': (r) => r.status === 200 || r.status === 400 || r.status === 404 });
  recordMetrics(res, '学生-提交岗位申请');
  sleep(0.3);

  // 18. 查看我的岗位申请
  res = authGet('/api/student/job-applications', token);
  check(res, { '学生-查看岗位申请': (r) => r.status === 200 });
  sleep(0.2);

  // 19. 提交学生申请（另一种申请类型）
  const studentApp = maybeMalformedPayload(generateStudentApplication());
  res = authPost('/api/student/applications', studentApp, token);
  check(res, { '学生-提交学生申请': (r) => r.status === 200 || r.status === 400 });
  recordMetrics(res, '学生-提交学生申请');
  sleep(0.3);

  // 20. 查看学生申请列表
  res = authGet('/api/student/applications', token);
  check(res, { '学生-查看学生申请': (r) => r.status === 200 });
  sleep(0.2);

  // 21. 实习状态
  res = authGet('/api/student/internship-status', token);
  check(res, { '学生-实习状态': (r) => r.status === 200 });
  sleep(0.2);

  // 22. 实习确认
  res = authGet('/api/student/internship-confirmation', token);
  check(res, { '学生-实习确认': (r) => r.status === 200 });
  sleep(0.2);

  // 23. 保存实习确认（写操作）
  const confirmData = maybeMalformedPayload(generateInternshipConfirmation());
  res = authPost('/api/student/internship-confirmation/save', confirmData, token);
  check(res, { '学生-保存实习确认': (r) => r.status === 200 || r.status === 400 });
  recordMetrics(res, '学生-保存实习确认');
  sleep(0.3);

  // 24. 实习确认历史
  res = authGet('/api/student/internship-confirmation/history', token);
  check(res, { '学生-确认历史': (r) => r.status === 200 });
  sleep(0.2);

  // 25. 实习心得列表
  res = authGet('/api/student/internship-reflection/list', token);
  check(res, { '学生-心得列表': (r) => r.status === 200 });
  sleep(0.2);

  // 26. 当前周期状态
  res = authGet('/api/student/internship-reflection/current-period-status', token);
  check(res, { '学生-当前周期状态': (r) => r.status === 200 });
  sleep(0.2);

  // 27. 提交实习心得（写操作）
  const reflection = maybeMalformedPayload(generateInternshipReflection());
  res = authPost('/api/student/internship-reflection/submit', reflection, token);
  check(res, { '学生-提交心得': (r) => r.status === 200 || r.status === 400 });
  recordMetrics(res, '学生-提交心得');
  sleep(0.3);

  // 28. 个人资料
  res = authGet('/api/student/profile', token);
  check(res, { '学生-个人资料': (r) => r.status === 200 });
  sleep(0.2);

  // 29. 更新个人资料
  res = authPost('/api/student/profile/update', {
    phone: `138${randomInt(10000000, 99999999)}`,
    email: `student_${uuid().substring(0, 6)}@test.com`,
  }, token);
  check(res, { '学生-更新资料': (r) => r.status === 200 || r.status === 400 });
  sleep(0.2);

  // 30. 实习记录
  res = authGet('/api/student/internship-record', token);
  check(res, { '学生-实习记录': (r) => r.status === 200 });
  sleep(0.2);

  // 31. 面试列表
  res = authGet('/api/student/interviews', token);
  check(res, { '学生-面试列表': (r) => r.status === 200 });
  sleep(0.2);

  // 32. 查看公告
  res = authGet('/api/announcements', token);
  check(res, { '学生-公告列表': (r) => r.status === 200 });
  sleep(0.2);

  // 33. 查看资源文档
  res = authGet('/api/resource-documents/published', token);
  check(res, { '学生-资源文档': (r) => r.status === 200 });
  sleep(0.2);

  // 34. 问题反馈
  const feedback = maybeMalformedPayload({
    title: `学生反馈_${uuid().substring(0, 6)}`,
    content: '测试反馈内容',
    feedbackType: 'BUG',
    priority: 'LOW',
  });
  res = authPost('/api/problem-feedback', feedback, token);
  check(res, { '学生-提交反馈': (r) => r.status === 200 || r.status === 400 });
  sleep(0.2);

  // 35. 我的反馈
  res = authGet('/api/problem-feedback/my-feedback?userType=STUDENT&userId=1', token);
  check(res, { '学生-我的反馈': (r) => r.status === 200 });
  sleep(0.2);

  // 36. 获取密码规则（公开接口）
  res = authGet('/api/auth/password-rules', token);
  check(res, { '学生-密码规则': (r) => r.status === 200 });
  sleep(0.2);

  // 37. 检查状态
  res = authGet('/api/auth/check-status', token);
  check(res, { '学生-检查状态': (r) => r.status === 200 });
  sleep(0.2);

  // 38. 权限列表
  res = authGet('/api/permissions', token);
  check(res, { '学生-权限列表': (r) => r.status === 200 });
  sleep(0.2);

  // AI 接口已剥离至 isolated-flow.js

  // 聊天未读数（需要已有会话数据，跳过）
  sleep(0.2);

  // 42. 提醒待确认
  res = authGet('/api/student/reminder/pending?studentId=1', token);
  check(res, { '学生-待确认提醒': (r) => r.status === 200 });
  sleep(0.2);

  // 43. 实习时间设置
  res = authGet('/api/settings/internship-time', token);
  check(res, { '学生-实习时间': (r) => r.status === 200 });
  sleep(0.2);

  // 44. 实习节点
  res = authGet('/api/settings/internship-nodes', token);
  check(res, { '学生-实习节点': (r) => r.status === 200 });
  sleep(0.2);

  // 45. 账户信息
  res = authGet('/api/account-settings/info', token);
  check(res, { '学生-账户信息': (r) => r.status === 200 });
  sleep(0.2);

  // 班级/专业/院系列表（公开接口，无需认证，移除角色受限版本）
  sleep(0.2);

  // 49. 公告类别（公开接口）
  res = authGet('/api/admin/position-categories/public', token);
  check(res, { '学生-公告类别': (r) => r.status === 200 });
  sleep(0.2);

  // 50. 健康检查（公开接口）
  res = authGet('/api/health/check', token);
  check(res, { '学生-健康检查': (r) => r.status === 200 });
}
