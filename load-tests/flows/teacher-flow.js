// ========================================
// 教师业务链路（COUNSELOR 类型）
// 使用辅导员 t003，绑定班级 132/133/134/153
// 覆盖：登录 → 班级学生 → 审批 → 实习状态 → 评价 → 资源 → 反馈
// ========================================

import { check, sleep } from 'k6';
import {
  login, authGet, authPost, authPut, authDelete, isSuccess, uuid, randomInt,
  maybeMalformedPayload, recordMetrics, randomChineseName,
} from '../helpers.js';
import { REAL_DATA } from '../config.js';

export function teacherFlow() {
  // 1. 登录（COUNSELOR 类型教师）
  const token = login('teacher');
  if (!token) return;
  sleep(0.5);

  const teacherId = REAL_DATA.teacherId;
  const classIds = REAL_DATA.classIds;
  const studentIds = REAL_DATA.studentIds;
  const randomClassId = classIds[Math.floor(Math.random() * classIds.length)];
  const randomStudentId = studentIds[Math.floor(Math.random() * studentIds.length)];

  // 2. 当前教师信息
  let res = authGet('/api/teacher/current', token);
  check(res, { '教师-当前信息': (r) => r.status === 200 });
  sleep(0.3);

  // 3. 通知列表
  res = authGet('/api/teacher/notifications?limit=10', token);
  check(res, { '教师-通知': (r) => r.status === 200 });
  sleep(0.2);

  // 4. Dashboard 统计
  res = authGet('/api/teacher/dashboard/stats', token);
  check(res, { '教师-统计': (r) => r.status === 200 });
  sleep(0.2);

  // 5. 辅导员专属统计
  res = authGet(`/api/teacher/dashboard/counselor-stats/${teacherId}`, token);
  check(res, { '教师-辅导员统计': (r) => r.status === 200 });
  sleep(0.2);

  // 6. 我的班级（辅导员绑定的班级）
  res = authGet(`/api/teacher/classes?counselorId=${teacherId}`, token);
  check(res, { '教师-我的班级': (r) => r.status === 200 });
  sleep(0.2);

  // 7. 全部班级
  res = authGet('/api/teacher/classes/all', token);
  check(res, { '教师-全部班级': (r) => r.status === 200 });
  sleep(0.2);

  // 8. 班级详情
  res = authGet(`/api/teacher/classes/${randomClassId}`, token);
  check(res, { '教师-班级详情': (r) => r.status === 200 || r.status === 404 });
  sleep(0.2);

  // 9. 班级学生列表（辅导员查看自己绑定的班级学生）
  res = authGet(`/api/teacher/classes/students/${randomClassId}?counselorId=${teacherId}`, token);
  check(res, { '教师-班级学生': (r) => r.status === 200 });
  sleep(0.2);

  // 10. 辅导员学生列表（带搜索）
  res = authGet(`/api/teacher/classes/counselor-students/${teacherId}?searchName=&classId=&major=&grade=&status=&companyName=`, token);
  check(res, { '教师-辅导员学生': (r) => r.status === 200 });
  sleep(0.2);

  // 11. 辅导员班级关系
  res = authGet(`/api/teacher/classes/relations/${teacherId}`, token);
  check(res, { '教师-班级关系': (r) => r.status === 200 });
  sleep(0.2);

  // 12. 辅导员班级统计
  res = authGet(`/api/teacher/classes/statistics/${teacherId}`, token);
  check(res, { '教师-班级统计': (r) => r.status === 200 });
  sleep(0.2);

  // 13. 审批统计
  res = authGet('/api/approval/stats', token);
  check(res, { '教师-审批统计': (r) => r.status === 200 });
  sleep(0.2);

  // 14. 学生申请审批列表
  res = authGet('/api/approval/student-applications?page=1&pageSize=10', token);
  check(res, { '教师-学生申请审批': (r) => r.status === 200 });
  sleep(0.2);

  // 15. 企业资质审批列表
  res = authGet('/api/approval/company-qualifications?page=1&pageSize=10', token);
  check(res, { '教师-企业资质审批': (r) => r.status === 200 });
  sleep(0.2);

  // 16. 综合审批列表
  res = authGet('/api/approval/applications?page=1&pageSize=10', token);
  check(res, { '教师-综合审批': (r) => r.status === 200 });
  sleep(0.2);

  // 17. 实习状态列表
  res = authGet('/api/teacher/internship-status?page=1&pageSize=10', token);
  check(res, { '教师-实习状态': (r) => r.status === 200 });
  sleep(0.2);

  // 18. 实习状态统计
  res = authGet('/api/teacher/internship-status/statistics', token);
  check(res, { '教师-实习统计': (r) => r.status === 200 });
  sleep(0.2);

  // 19. 学生列表
  res = authGet('/api/teacher/students?page=1&pageSize=10', token);
  check(res, { '教师-学生列表': (r) => r.status === 200 });
  sleep(0.2);

  // 20. 专业列表
  res = authGet('/api/teacher/majors', token);
  check(res, { '教师-专业列表': (r) => r.status === 200 });
  sleep(0.2);

  // 21. 年级列表
  res = authGet('/api/teacher/grades', token);
  check(res, { '教师-年级列表': (r) => r.status === 200 });
  sleep(0.2);

  // 22. 所有班级
  res = authGet('/api/teacher/all-classes', token);
  check(res, { '教师-所有班级': (r) => r.status === 200 });
  sleep(0.2);

  // 23. 企业列表
  res = authGet('/api/teacher/companies?page=1&pageSize=10', token);
  check(res, { '教师-企业列表': (r) => r.status === 200 });
  sleep(0.2);

  // 24. 企业标签
  res = authGet('/api/teacher/companies/tags', token);
  check(res, { '教师-企业标签': (r) => r.status === 200 });
  sleep(0.2);

  // 25. 待审核企业（需要 user:company:audit 权限）
  res = authGet('/api/teacher/companies/audit/pending?page=1&pageSize=10', token);
  check(res, { '教师-待审核企业': (r) => r.status === 200 || r.status === 403 });
  sleep(0.2);

  // 26. 企业统计
  res = authGet('/api/teacher/companies/statistics', token);
  check(res, { '教师-企业统计': (r) => r.status === 200 || r.status === 403 });
  sleep(0.2);

  // 27. 资源列表
  res = authGet('/api/teacher/resources?page=1&pageSize=10', token);
  check(res, { '教师-资源列表': (r) => r.status === 200 });
  sleep(0.2);

  // 28. 我的资源
  res = authGet('/api/teacher/resources/my?page=1&pageSize=10', token);
  check(res, { '教师-我的资源': (r) => r.status === 200 });
  sleep(0.2);

  // 29. 评价学生（辅导员评价自己班级的学生）
  res = authGet(`/api/evaluation/students?page=1&pageSize=10`, token);
  check(res, { '教师-评价学生': (r) => r.status === 200 || r.status === 403 });
  sleep(0.2);

  // 30. 评价周期
  res = authGet('/api/evaluation/periods', token);
  check(res, { '教师-评价周期': (r) => r.status === 200 });
  sleep(0.2);

  // 31. 评价统计
  res = authGet('/api/evaluation/statistics', token);
  check(res, { '教师-评价统计': (r) => r.status === 200 });
  sleep(0.2);

  // 32. 报表指标
  res = authGet('/api/admin/reports/metrics', token);
  check(res, { '教师-报表指标': (r) => r.status === 200 || r.status === 403 });
  sleep(0.2);

  // 33. 辅导员查看学生评价详情（智慧评分页面的核心接口）
  res = authGet(`/api/evaluation/student/${randomStudentId}`, token);
  check(res, { '教师-学生评价详情': (r) => r.status === 200 || r.status === 404 });
  sleep(0.2);

  // 34. 公告列表
  res = authGet('/api/announcements', token);
  check(res, { '教师-公告': (r) => r.status === 200 });
  sleep(0.2);

  // 35. 资源文档
  res = authGet('/api/resource-documents', token);
  check(res, { '教师-资源文档': (r) => r.status === 200 });
  sleep(0.2);

  // 36. 发送提醒（辅导员给自己班级的学生发提醒）
  res = authPost(`/api/teacher/reminder/send?studentId=${randomStudentId}&teacherId=${teacherId}&content=测试提醒_${uuid().substring(0, 6)}`, {}, token);
  check(res, { '教师-发送提醒': (r) => r.status === 200 || r.status === 400 || r.status === 404 });
  sleep(0.2);

  // 37. 问题反馈
  const feedback = maybeMalformedPayload({
    title: `教师反馈_${uuid().substring(0, 6)}`,
    content: '教师端测试反馈',
    feedbackType: 'BUG',
    priority: 'HIGH',
  });
  res = authPost('/api/problem-feedback', feedback, token);
  check(res, { '教师-提交反馈': (r) => r.status === 200 || r.status === 400 || r.status === 403 });
  sleep(0.2);

  // AI 接口已剥离至 isolated-flow.js

  // 聊天未读数（需要已有会话，跳过）

  // 41. 实习节点
  res = authGet('/api/settings/internship-nodes', token);
  check(res, { '教师-实习节点': (r) => r.status === 200 });
  sleep(0.2);

  // 42. 账户信息
  res = authGet('/api/account-settings/info', token);
  check(res, { '教师-账户信息': (r) => r.status === 200 });
  sleep(0.2);

  // 43. 权限列表
  res = authGet('/api/permissions', token);
  check(res, { '教师-权限列表': (r) => r.status === 200 });
  sleep(0.2);

  // 44. 教师权限
  res = authGet('/api/teacher/permissions', token);
  check(res, { '教师-教师权限': (r) => r.status === 200 });
  sleep(0.2);

  // 45. 教师角色
  res = authGet('/api/teacher/roles', token);
  check(res, { '教师-角色': (r) => r.status === 200 });
  sleep(0.2);

  // 46. 实习时间设置
  res = authGet('/api/settings/internship-time', token);
  check(res, { '教师-实习时间': (r) => r.status === 200 });
  sleep(0.2);

  // 公告已读记录（需要真实公告数据，跳过）

  // 48. 岗位类别（公开接口）
  res = authGet('/api/admin/position-categories/public', token);
  check(res, { '教师-岗位类别': (r) => r.status === 200 });
  sleep(0.2);

  // 49. 院系列表
  res = authGet('/api/admin/departments', token);
  check(res, { '教师-院系列表': (r) => r.status === 200 || r.status === 403 });
  sleep(0.2);

  // 档案管理（仅管理员可访问，跳过）
}
