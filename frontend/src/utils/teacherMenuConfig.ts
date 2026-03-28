/**
 * 教师端菜单权限配置
 * 定义不同身份教师的菜单访问权限
 */

import { TeacherType } from './teacherPermissions'

/**
 * 菜单项接口
 */
export interface MenuItem {
  index: string
  title: string
  icon: string
  routeName: string
  roles: string[]
  teacherTypes?: TeacherType[]
  description?: string
}

/**
 * 学院教师菜单配置
 * 学院教师拥有最高权限，可以访问所有功能
 */
export const COLLEGE_TEACHER_MENUS: MenuItem[] = [
  {
    index: '/teacher/home',
    title: '首页',
    icon: 'HomeFilled',
    routeName: 'teacherHome',
    roles: ['ROLE_TEACHER_COLLEGE'],
    teacherTypes: [TeacherType.COLLEGE],
    description: '查看系统首页概览'
  },

  {
    index: '/teacher/dashboard',
    title: '实习状态看板',
    icon: 'DataLine',
    routeName: 'teacherDashboard',
    roles: ['ROLE_TEACHER_COLLEGE'],
    teacherTypes: [TeacherType.COLLEGE],
    description: '查看全院学生实习状态统计'
  },
  {
    index: '/teacher/approval',
    title: '审核管理',
    icon: 'Edit',
    routeName: 'teacherApproval',
    roles: ['ROLE_TEACHER_COLLEGE'],
    teacherTypes: [TeacherType.COLLEGE],
    description: '审核学生申请和企业资质'
  },
  {
    index: '/teacher/evaluation',
    title: '智慧评分',
    icon: 'Document',
    routeName: 'teacherEvaluation',
    roles: ['ROLE_TEACHER_COLLEGE'],
    teacherTypes: [TeacherType.COLLEGE],
    description: '评定学生实习成绩'
  },
  {
    index: '/teacher/announcements',
    title: '公告管理',
    icon: 'Bell',
    routeName: 'teacherAnnouncements',
    roles: ['ROLE_TEACHER_COLLEGE'],
    teacherTypes: [TeacherType.COLLEGE],
    description: '发布和管理公告'
  },
  {
    index: '/teacher/internship-status',
    title: '实习状态',
    icon: 'Document',
    routeName: 'teacherInternshipStatus',
    roles: ['ROLE_TEACHER_COLLEGE'],
    teacherTypes: [TeacherType.COLLEGE],
    description: '查看学生实习状态'
  },
  {
    index: '/teacher/company-list',
    title: '企业列表',
    icon: 'OfficeBuilding',
    routeName: 'teacherCompanyList',
    roles: ['ROLE_TEACHER_COLLEGE'],
    teacherTypes: [TeacherType.COLLEGE],
    description: '查看合作企业列表'
  },
  {
    index: '/teacher/students',
    title: '学生管理',
    icon: 'School',
    routeName: 'teacherStudents',
    roles: ['ROLE_TEACHER_COLLEGE'],
    teacherTypes: [TeacherType.COLLEGE],
    description: '管理全院学生信息和实习状态'
  },
  {
    index: '/teacher/resources',
    title: '资源管理',
    icon: 'Upload',
    routeName: 'teacherResources',
    roles: ['ROLE_TEACHER_COLLEGE'],
    teacherTypes: [TeacherType.COLLEGE],
    description: '上传和管理实习资源'
  },
  {
    index: '/teacher/reports',
    title: '统计报表',
    icon: 'DataLine',
    routeName: 'teacherReports',
    roles: ['ROLE_TEACHER_COLLEGE'],
    teacherTypes: [TeacherType.COLLEGE],
    description: '查看实习统计报表'
  },
  {
    index: '/teacher/settings',
    title: '系统设置',
    icon: 'Setting',
    routeName: 'teacherSettings',
    roles: ['ROLE_TEACHER_COLLEGE'],
    teacherTypes: [TeacherType.COLLEGE],
    description: '配置系统设置'
  },
  {
    index: '/teacher/account-settings',
    title: '账号设置',
    icon: 'Setting',
    routeName: 'teacherAccountSettings',
    roles: ['ROLE_TEACHER_COLLEGE'],
    teacherTypes: [TeacherType.COLLEGE],
    description: '管理个人账号设置'
  },
  {
    index: '/teacher/timeline-config',
    title: '重要设置',
    icon: 'Clock',
    routeName: 'teacherTimelineConfig',
    roles: ['ROLE_TEACHER_COLLEGE'],
    teacherTypes: [TeacherType.COLLEGE],
    description: '配置实习重要设置'
  },
  {
    index: '/teacher/ai-scoring-config',
    title: 'AI分析配置',
    icon: 'Key',
    routeName: 'teacherAiScoringConfig',
    roles: ['ROLE_TEACHER_COLLEGE'],
    teacherTypes: [TeacherType.COLLEGE],
    description: '配置AI评分规则'
  },
  {
    index: '/teacher/classes',
    title: '班级管理',
    icon: 'School',
    routeName: 'teacherClasses',
    roles: ['ROLE_TEACHER_COLLEGE'],
    teacherTypes: [TeacherType.COLLEGE],
    description: '管理班级信息'
  },
  {
    index: '/teacher/internship',
    title: '实习管理',
    icon: 'Document',
    routeName: 'teacherInternship',
    roles: ['ROLE_TEACHER_COLLEGE'],
    teacherTypes: [TeacherType.COLLEGE],
    description: '管理实习信息'
  },
  {
    index: '/teacher/profile',
    title: '个人中心',
    icon: 'User',
    routeName: 'teacherProfile',
    roles: ['ROLE_TEACHER_COLLEGE'],
    teacherTypes: [TeacherType.COLLEGE],
    description: '查看个人资料'
  }
]

/**
 * 系室教师菜单配置
 * 系室教师可以管理本系的学生和实习
 */
export const DEPARTMENT_TEACHER_MENUS: MenuItem[] = [
  {
    index: '/teacher/home',
    title: '首页',
    icon: 'HomeFilled',
    routeName: 'teacherHome',
    roles: ['ROLE_TEACHER_DEPARTMENT'],
    teacherTypes: [TeacherType.DEPARTMENT],
    description: '查看系统首页概览'
  },

  {
    index: '/teacher/dashboard',
    title: '实习状态看板',
    icon: 'DataLine',
    routeName: 'teacherDashboard',
    roles: ['ROLE_TEACHER_DEPARTMENT'],
    teacherTypes: [TeacherType.DEPARTMENT],
    description: '查看本系学生实习状态统计'
  },
  {
    index: '/teacher/approval',
    title: '审核管理',
    icon: 'Edit',
    routeName: 'teacherApproval',
    roles: ['ROLE_TEACHER_DEPARTMENT'],
    teacherTypes: [TeacherType.DEPARTMENT],
    description: '审核学生申请和企业资质'
  },
  {
    index: '/teacher/evaluation',
    title: '智慧评分',
    icon: 'Document',
    routeName: 'teacherEvaluation',
    roles: ['ROLE_TEACHER_DEPARTMENT'],
    teacherTypes: [TeacherType.DEPARTMENT],
    description: '评定本系学生实习成绩'
  },
  {
    index: '/teacher/announcements',
    title: '公告管理',
    icon: 'Bell',
    routeName: 'teacherAnnouncements',
    roles: ['ROLE_TEACHER_DEPARTMENT'],
    teacherTypes: [TeacherType.DEPARTMENT],
    description: '查看公告'
  },
  {
    index: '/teacher/internship-status',
    title: '实习状态',
    icon: 'Document',
    routeName: 'teacherInternshipStatus',
    roles: ['ROLE_TEACHER_DEPARTMENT'],
    teacherTypes: [TeacherType.DEPARTMENT],
    description: '查看本系学生实习状态'
  },
  {
    index: '/teacher/company-list',
    title: '企业列表',
    icon: 'OfficeBuilding',
    routeName: 'teacherCompanyList',
    roles: ['ROLE_TEACHER_DEPARTMENT'],
    teacherTypes: [TeacherType.DEPARTMENT],
    description: '查看合作企业列表'
  },
  {
    index: '/teacher/students',
    title: '学生管理',
    icon: 'School',
    routeName: 'teacherStudents',
    roles: ['ROLE_TEACHER_DEPARTMENT'],
    teacherTypes: [TeacherType.DEPARTMENT],
    description: '管理本系学生信息和实习状态'
  },
  {
    index: '/teacher/resources',
    title: '资源管理',
    icon: 'Upload',
    routeName: 'teacherResources',
    roles: ['ROLE_TEACHER_DEPARTMENT'],
    teacherTypes: [TeacherType.DEPARTMENT],
    description: '查看实习资源'
  },
  {
    index: '/teacher/account-settings',
    title: '账号设置',
    icon: 'Setting',
    routeName: 'teacherAccountSettings',
    roles: ['ROLE_TEACHER_DEPARTMENT'],
    teacherTypes: [TeacherType.DEPARTMENT],
    description: '管理个人账号设置'
  },
  {
    index: '/teacher/timeline-config',
    title: '重要设置',
    icon: 'Clock',
    routeName: 'teacherTimelineConfig',
    roles: ['ROLE_TEACHER_DEPARTMENT'],
    teacherTypes: [TeacherType.DEPARTMENT],
    description: '配置实习重要设置'
  },
  {
    index: '/teacher/ai-scoring-config',
    title: 'AI分析配置',
    icon: 'Key',
    routeName: 'teacherAiScoringConfig',
    roles: ['ROLE_TEACHER_DEPARTMENT'],
    teacherTypes: [TeacherType.DEPARTMENT],
    description: '配置AI评分规则'
  },
  {
    index: '/teacher/company-audit',
    title: '企业审核',
    icon: 'OfficeBuilding',
    routeName: 'teacherCompanyAudit',
    roles: ['ROLE_TEACHER_DEPARTMENT'],
    teacherTypes: [TeacherType.DEPARTMENT],
    description: '审核企业资质'
  },
  {
    index: '/teacher/student-application-audit',
    title: '学生申请审核',
    icon: 'Edit',
    routeName: 'teacherStudentApplicationAudit',
    roles: ['ROLE_TEACHER_DEPARTMENT'],
    teacherTypes: [TeacherType.DEPARTMENT],
    description: '审核学生申请'
  },
  {
    index: '/teacher/classes',
    title: '班级管理',
    icon: 'School',
    routeName: 'teacherClasses',
    roles: ['ROLE_TEACHER_DEPARTMENT'],
    teacherTypes: [TeacherType.DEPARTMENT],
    description: '管理本系班级信息'
  },
  {
    index: '/teacher/internship',
    title: '实习管理',
    icon: 'Document',
    routeName: 'teacherInternship',
    roles: ['ROLE_TEACHER_DEPARTMENT'],
    teacherTypes: [TeacherType.DEPARTMENT],
    description: '管理本系实习信息'
  },
  {
    index: '/teacher/profile',
    title: '个人中心',
    icon: 'User',
    routeName: 'teacherProfile',
    roles: ['ROLE_TEACHER_DEPARTMENT'],
    teacherTypes: [TeacherType.DEPARTMENT],
    description: '查看个人资料'
  }
]

/**
 * 辅导员教师菜单配置
 * 辅导员教师主要负责学生管理和辅导
 */
export const COUNSELOR_TEACHER_MENUS: MenuItem[] = [
  {
    index: '/teacher/home',
    title: '首页',
    icon: 'HomeFilled',
    routeName: 'teacherHome',
    roles: ['ROLE_TEACHER_COUNSELOR'],
    teacherTypes: [TeacherType.COUNSELOR],
    description: '查看系统首页概览'
  },

  {
    index: '/teacher/dashboard',
    title: '实习状态看板',
    icon: 'DataLine',
    routeName: 'teacherDashboard',
    roles: ['ROLE_TEACHER_COUNSELOR'],
    teacherTypes: [TeacherType.COUNSELOR],
    description: '查看辅导学生实习状态统计'
  },
  {
    index: '/teacher/approval',
    title: '审核管理',
    icon: 'Edit',
    routeName: 'teacherApproval',
    roles: ['ROLE_TEACHER_COUNSELOR'],
    teacherTypes: [TeacherType.COUNSELOR],
    description: '审核学生申请'
  },
  {
    index: '/teacher/evaluation',
    title: '智慧评分',
    icon: 'Document',
    routeName: 'teacherEvaluation',
    roles: ['ROLE_TEACHER_COUNSELOR'],
    teacherTypes: [TeacherType.COUNSELOR],
    description: '查看学生评分'
  },
  {
    index: '/teacher/announcements',
    title: '公告管理',
    icon: 'Bell',
    routeName: 'teacherAnnouncements',
    roles: ['ROLE_TEACHER_COUNSELOR'],
    teacherTypes: [TeacherType.COUNSELOR],
    description: '查看公告'
  },
  {
    index: '/teacher/internship-status',
    title: '实习状态',
    icon: 'Document',
    routeName: 'teacherInternshipStatus',
    roles: ['ROLE_TEACHER_COUNSELOR'],
    teacherTypes: [TeacherType.COUNSELOR],
    description: '查看辅导学生实习状态'
  },
  {
    index: '/teacher/company-list',
    title: '企业列表',
    icon: 'OfficeBuilding',
    routeName: 'teacherCompanyList',
    roles: ['ROLE_TEACHER_COUNSELOR'],
    teacherTypes: [TeacherType.COUNSELOR],
    description: '查看合作企业列表'
  },
  {
    index: '/teacher/students',
    title: '学生管理',
    icon: 'School',
    routeName: 'teacherStudents',
    roles: ['ROLE_TEACHER_COUNSELOR'],
    teacherTypes: [TeacherType.COUNSELOR],
    description: '管理辅导学生信息和实习心得'
  },
  {
    index: '/teacher/resources',
    title: '资源管理',
    icon: 'Upload',
    routeName: 'teacherResources',
    roles: ['ROLE_TEACHER_COUNSELOR'],
    teacherTypes: [TeacherType.COUNSELOR],
    description: '查看实习资源'
  },
  {
    index: '/teacher/account-settings',
    title: '账号设置',
    icon: 'Setting',
    routeName: 'teacherAccountSettings',
    roles: ['ROLE_TEACHER_COUNSELOR'],
    teacherTypes: [TeacherType.COUNSELOR],
    description: '管理个人账号设置'
  },
  {
    index: '/teacher/timeline-config',
    title: '重要设置',
    icon: 'Clock',
    routeName: 'teacherTimelineConfig',
    roles: ['ROLE_TEACHER_COUNSELOR'],
    teacherTypes: [TeacherType.COUNSELOR],
    description: '配置实习重要设置'
  },
  {
    index: '/teacher/ai-scoring-config',
    title: 'AI分析配置',
    icon: 'Key',
    routeName: 'teacherAiScoringConfig',
    roles: ['ROLE_TEACHER_COUNSELOR'],
    teacherTypes: [TeacherType.COUNSELOR],
    description: '配置AI评分规则'
  },
    {
    index: '/teacher/grade-finalize',
    title: '成绩评定',
    icon: 'Edit',
    routeName: 'teacherGradeFinalize',
    roles: ['ROLE_TEACHER_COUNSELOR'],
    teacherTypes: [TeacherType.COUNSELOR],
    description: '评定学生成绩'
  },
  {
    index: '/teacher/classes',
    title: '班级管理',
    icon: 'School',
    routeName: 'teacherClasses',
    roles: ['ROLE_TEACHER_COUNSELOR'],
    teacherTypes: [TeacherType.COUNSELOR],
    description: '管理辅导班级信息'
  },
  {
    index: '/teacher/internship',
    title: '实习管理',
    icon: 'Document',
    routeName: 'teacherInternship',
    roles: ['ROLE_TEACHER_COUNSELOR'],
    teacherTypes: [TeacherType.COUNSELOR],
    description: '管理辅导学生实习信息'
  },
  {
    index: '/teacher/profile',
    title: '个人中心',
    icon: 'User',
    routeName: 'teacherProfile',
    roles: ['ROLE_TEACHER_COUNSELOR'],
    teacherTypes: [TeacherType.COUNSELOR],
    description: '查看个人资料'
  }
]

/**
 * 根据教师类型获取菜单配置
 * @param teacherType 教师类型
 * @returns 菜单配置
 */
export function getMenuConfigByTeacherType(teacherType: TeacherType): MenuItem[] {
  switch (teacherType) {
    case TeacherType.COLLEGE:
      return COLLEGE_TEACHER_MENUS
    case TeacherType.DEPARTMENT:
      return DEPARTMENT_TEACHER_MENUS
    case TeacherType.COUNSELOR:
      return COUNSELOR_TEACHER_MENUS
    default:
      return COLLEGE_TEACHER_MENUS
  }
}

/**
 * 获取所有菜单配置（用于调试和管理）
 * @returns 所有菜单配置
 */
export function getAllMenuConfigs(): Record<TeacherType, MenuItem[]> {
  return {
    [TeacherType.COLLEGE]: COLLEGE_TEACHER_MENUS,
    [TeacherType.DEPARTMENT]: DEPARTMENT_TEACHER_MENUS,
    [TeacherType.COUNSELOR]: COUNSELOR_TEACHER_MENUS
  }
}