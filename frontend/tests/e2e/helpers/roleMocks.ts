import type { TestRole } from './auth'

export const PUBLIC_ROUTES = ['/login', '/forgot-password', '/company-register', '/company-check']

export const ROLE_ROUTES: Record<TestRole, string[]> = {
  student: [
    '/student/home',
    '/student/jobs',
    '/student/applications',
    '/student/interviews',
    '/student/internship-confirmation-form',
    '/student/internship-registration',
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
    '/teacher/recruitment',
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
    '/company/internship-students',
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
  { id: 2, permissionCode: 'menu:approval', permissionName: '审核管理', path: '/teacher/approval', icon: 'Document', sortOrder: 2 },
  { id: 3, permissionCode: 'menu:announcements', permissionName: '公告管理', path: '/teacher/announcements', icon: 'Bell', sortOrder: 3 }
]

const TEACHER_APPROVAL_ROWS = [
  {
    id: 101,
    studentName: '张三',
    type: 'selfPractice',
    company: '广东示例医疗信息科技有限公司',
    positionName: '前端开发实习生',
    reason: '申请赴已联系企业开展专业实习。',
    materials: {},
    studentUserId: '2021001',
    grade: '2021级',
    className: '软件2101班',
    applyTime: '2026-01-10 10:00:00',
    status: 'pending'
  },
  {
    id: 102,
    studentName: '李四',
    type: 'selfPractice',
    company: '深圳示例智慧健康技术研究中心',
    positionName: '医学信息与软件开发实习生',
    reason: '已获得企业录用通知，申请自主实习。',
    materials: {},
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
    status: 'active',
    salaryUnit: '元/月',
    province: '广东省', city: '广州市', district: '天河区',
    detailAddress: '科韵路 100 号',
    description: '参与医疗信息系统开发与测试。',
    requirements: '掌握基础前端开发与沟通协作。'
  }
]

export const STUDENT_JOBS = [{
  id: 1, title: '前端开发实习生', industry: 'IT', industryName: '信息技术',
  company: '广东示例医疗信息科技有限公司', companyId: 2,
  location: '广东省广州市天河区', salary: '3000-5000元/月', salaryMin: 3000, salaryMax: 5000,
  salaryUnit: '元/月', remainingQuota: 3, tags: ['专业实习', '提供住宿'], isFavorite: false,
  description: '参与医疗信息系统开发、联调和质量验证。', requirements: '具备前端基础，能按时完成实习任务。'
}]

export const COMPANY_APPLICATIONS = [{
  id: 201, studentId: 301, positionId: 1, companyId: 2,
  studentName: '张三', studentNo: '2023001001', studentUserId: '2023001001',
  phone: '13800138000', email: 'student.mobile.fixture@example.com', major: '医学信息工程',
  grade: '2023', positionName: '前端开发工程师', companyName: '测试企业', status: 'pending',
  applyDate: '2026-10-19 10:00:00', createTime: '2026-10-19 10:00:00',
  selfIntroduction: '希望通过专业实习提升工程实践能力。'
}]

export const COMPANY_INTERNSHIP_STUDENTS = [{
  studentId: 301, placementId: 401, studentName: '张三', studentNumber: '2023001001',
  grade: '2023', majorName: '医学信息工程', className: '医学信息工程 2023 级 1 班',
  phone: '13800138000', email: 'student.mobile.fixture@example.com',
  positionName: '医疗信息系统前端开发与数据可视化实习生', sourceType: 'PLATFORM',
  internshipStartTime: '2026-09-01', internshipEndTime: '2027-01-31'
}]

export const TEACHER_STUDENTS = [{
  id: 401, studentId: '2023001001', name: '张三', className: '医学信息工程 2023 级 1 班',
  major: '医学信息工程', grade: '2023级', phone: '13800138000',
  email: 'student.mobile.fixture@example.com', status: 3,
  companyId: 2, companyName: '广东示例医疗信息科技有限公司', positionId: 1,
  positionName: '前端开发实习生', jobSearchStatus: 'IN_PROGRESS', jobSearchStatusText: '实习进行中',
  internshipPath: 'mutualChoice', pendingApplicationTexts: []
}]

export function getRoleApiMocks(role: TestRole): Record<string, unknown> {
  const common = {
    '/home/ai-config': true,
    '/agent/capabilities': {
      enabled: true,
      actor: { ownerKey: `mock:${role}`, id: '1', name: '移动验收用户', role: `ROLE_${role.toUpperCase()}`, readOnly: false },
      capabilities: { agent_chat: true, form_fill: true, voice_input: true }, tools: []
    },
    '/agent/sessions': [],
    '/agent/templates': []
  }
  switch (role) {
    case 'teacher':
      return {
        ...common,
        '/teacher/permissions/tree': TEACHER_MENU_TREE,
        '/teacher/permissions/role-code/COLLEGE/permissions': TEACHER_MENU_TREE,
        '/approval/stats': { selfPracticePending: 0, delayPending: 2, companyQualificationPending: 0 },
        '/approval/student-applications': { total: TEACHER_APPROVAL_ROWS.length, rows: TEACHER_APPROVAL_ROWS },
        '/approval/company-qualifications': { total: 0, rows: [] },
        '/teacher/internship-status/statistics': { studentCount: 1, interningCount: 1, noOfferCount: 0, finishedCount: 0 },
        '/teacher/internship-status/company-options': ['广东示例医疗信息科技有限公司'],
        '/teacher/internship-status': { total: 1, rows: TEACHER_STUDENTS, pages: 1, current: 1, pageSize: 10 },
        '/teacher/classes/all': [{ id: 1, name: '医学信息工程 2023 级 1 班', grade: 2023, majorId: 1 }],
        '/teacher/majors': [{ id: 1, name: '医学信息工程' }],
        '/teacher/grades': [2023],
        '/teacher/companies': { rows: [{ id: 2, companyName: '广东示例医疗信息科技有限公司' }], total: 1 }
      }
    case 'admin':
      return { ...common, '/admin/permissions/roles/1/permissions': ADMIN_PERMISSION_CODES.map((permissionCode) => ({ permissionCode })) }
    case 'company':
      return {
        ...common,
        '/admin/positions/company/': COMPANY_JOBS,
        '/company/job-applications': COMPANY_APPLICATIONS,
        '/company/applications': [],
        '/company/internship-students': { total: 1, rows: COMPANY_INTERNSHIP_STUDENTS },
        '/company/profile': { id: 2, companyName: '测试企业', contactPerson: '王老师', contactPhone: '13800138001' },
        '/company/contacts': [{ id: 1, name: '王老师', phone: '13800138001', email: 'company.mobile.fixture@example.com' }]
      }
    default:
      return {
        ...common,
        '/positions/detail/': STUDENT_JOBS[0],
        '/positions/all': STUDENT_JOBS,
        '/positions/options/industries': ['IT'],
        '/positions/options/companies': ['广东示例医疗信息科技有限公司'],
        '/positions/options/regions': [],
        '/student/profile/resumes': [],
        '/student/profile/certificates': [],
        '/student/job-applications': [],
        '/student/applications': [],
        '/student/home/internship-status': { status: 0 },
        '/student/internship-record': null
      }
  }
}
