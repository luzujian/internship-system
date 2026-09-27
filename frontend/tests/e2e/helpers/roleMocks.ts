import type { TestRole } from './auth'

export const PUBLIC_ROUTES = ['/login', '/company-register', '/company-check']

export const ROLE_ROUTES: Record<TestRole, string[]> = {
  student: [
    '/student/home',
    '/student/jobs',
    '/student/applications',
    '/student/interviews',
    '/student/internship-confirmation-form',
    '/student/internships',
    '/student/internship-appraisal',
    '/student/profile'
  ],
  teacher: [
    '/teacher/home',
    '/teacher/dashboard',
    '/teacher/student-tracking',
    '/teacher/approval',
    '/teacher/evaluation',
    '/teacher/internship-appraisals',
    '/teacher/announcements',
    '/teacher/company-list',
    '/teacher/resources',
    '/teacher/reports',
    '/teacher/aisettings',
    '/teacher/account-settings',
    '/teacher/timeline-config',
    '/teacher/company-audit',
    '/teacher/student-application-audit',
    '/teacher/key-timeline-config',
    '/teacher/ai-keyword-config',
    '/teacher/grade-finalize',
    '/teacher/profile',
    '/teacher/scoring-rules',
    '/teacher/keyword-library',
    '/teacher/classes',
    '/teacher/ai-scoring-config'
  ],
  company: [
    '/company/dashboard',
    '/company/jobs',
    '/company/internship-confirm',
    '/company/application-view',
    '/company/interviews',
    '/company/settings'
  ],
  admin: [
    '/admin/dashboard',
    '/admin/profile',
    '/admin/student-users',
    '/admin/teacher-users',
    '/admin/admin-users',
    '/admin/companies',
    '/admin/application-withdrawal-records',
    '/admin/internship-confirm-application',
    '/admin/recruitment',
    '/admin/majors',
    '/admin/position-categories',
    '/admin/classes',
    '/admin/data-statistics/active-statistics',
    '/admin/logs',
    '/admin/announcements',
    '/admin/permissions',
    '/admin/backup',
    '/admin/keyword-library',
    '/admin/ai-model',
    '/admin/ai-test',
    '/admin/scoring-rule',
    '/admin/resource-documents',
    '/admin/problem-feedback',
    '/admin/system-settings'
  ]
}

export const ROLE_HOME: Record<TestRole, string> = {
  student: '/student/home',
  teacher: '/teacher/home',
  company: '/company/dashboard',
  admin: '/admin/dashboard'
}

const TEACHER_MENU_TREE = [
  { id: 1, permissionCode: 'menu:home', permissionName: '首页', path: '/teacher/home', icon: 'HomeFilled', sortOrder: 1 },
  { id: 2, permissionCode: 'menu:approval', permissionName: '审批管理', path: '/teacher/approval', icon: 'Document', sortOrder: 2 },
  { id: 3, permissionCode: 'menu:announcements', permissionName: '公告管理', path: '/teacher/announcements', icon: 'Bell', sortOrder: 3 }
]

const TEACHER_APPROVAL_ROWS = [
  {
    id: 101,
    studentName: '张三',
    studentUserId: '2021001',
    grade: '2021级',
    className: '软件2101班',
    applyTime: '2026-01-10 10:00:00',
    status: 'pending'
  },
  {
    id: 102,
    studentName: '李四',
    studentUserId: '2021002',
    grade: '2021级',
    className: '软件2102班',
    applyTime: '2026-01-11 11:30:00',
    status: 'pending'
  }
]

const ADMIN_PERMISSION_CODES = [
  'user:teacher:view',
  'user:student:view',
  'user:company:view',
  'position:view',
  'position-category:view',
  'class:view',
  'major:view',
  'department:view',
  'announcement:view',
  'resource:view',
  'problem:view',
  'statistics:view',
  'log:view',
  'permission:view',
  'backup:view',
  'system:settings:view',
  'keyword:view',
  'ai:model:view',
  'ai:test:view',
  'scoring:view',
  'user:company:audit',
  'application:withdrawal:view',
  'internship:confirm:view',
  'recruitment:view'
]

const COMPANY_JOBS = [
  {
    id: 1,
    positionName: '前端开发工程师',
    department: '研发部',
    workCity: '北京',
    workAddress: '海淀区西二旗',
    salaryMin: 8000,
    salaryMax: 15000,
    recruitedCount: 2,
    plannedRecruit: 5,
    remainingQuota: 3,
    publishDate: '2026-01-05',
    viewCount: 66,
    status: 'published'
  }
]

export function getRoleApiMocks(role: TestRole): Record<string, unknown> {
  switch (role) {
    case 'teacher':
      return {
        '/teacher/permissions/tree': TEACHER_MENU_TREE,
        '/teacher/permissions/role-code/COLLEGE/permissions': TEACHER_MENU_TREE,
        '/approval/stats': { selfPracticePending: 0, delayPending: 2, companyQualificationPending: 0 },
        '/approval/student-applications': { total: TEACHER_APPROVAL_ROWS.length, rows: TEACHER_APPROVAL_ROWS }
      }
    case 'admin':
      return {
        '/admin/permissions/roles/1/permissions': ADMIN_PERMISSION_CODES.map((permissionCode) => ({ permissionCode }))
      }
    case 'company':
      return {
        '/admin/positions/company/': COMPANY_JOBS
      }
    default:
      return {}
  }
}
