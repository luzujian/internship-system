import type { RouteRecordRaw } from 'vue-router'

const adminRoutes: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    name: 'adminDashboard',
    component: () => import('../views/admin/Dashboard.vue')
  },
  {
    path: 'profile',
    name: 'adminProfile',
    component: () => import('../views/admin/ProfileCenter.vue')
  },
  {
    path: 'student-users',
    name: 'studentUserManagement',
    component: () => import('../views/admin/StudentUserManagement.vue')
  },
  {
    path: 'teacher-users',
    name: 'teacherUserManagement',
    component: () => import('../views/admin/TeacherUserManagement.vue')
  },
  {
    path: 'admin-users',
    name: 'adminUserManagement',
    component: () => import('../views/admin/AdminUserManagement.vue')
  },
  {
    path: 'companies',
    name: 'companyManagement',
    component: () => import('../views/admin/CompanyManagement.vue')
  },
  {
    path: 'application-withdrawal-records',
    name: 'applicationWithdrawalRecordManagement',
    component: () => import('../views/admin/ApplicationWithdrawalRecordManagement.vue')
  },
  {
    path: 'internship-confirm-application',
    name: 'internshipConfirmApplication',
    component: () => import('../views/admin/InternshipConfirmApplication.vue')
  },
  {
    path: 'recruitment',
    name: 'recruitmentManagement',
    component: () => import('../views/admin/RecruitmentManagement.vue')
  },
  {
    path: 'majors',
    name: 'majorManagement',
    component: () => import('../views/admin/DepartmentMajorManagement.vue')
  },
  {
    path: 'position-categories',
    name: 'positionCategoryManagement',
    component: () => import('../views/admin/PositionCategoryManagement.vue')
  },
  {
    path: 'classes',
    name: 'classManagement',
    component: () => import('../views/admin/ClassManagement.vue')
  },
  {
    path: 'data-statistics',
    name: 'DataStatistics',
    meta: { title: '数据统计管理', requiresAuth: true, role: 'admin' },
    redirect: 'active-statistics',
    children: [
      {
        path: 'active-statistics',
        name: 'ActiveStatistics',
        component: () => import('../views/admin/DataStatisticsManagement.vue'),
        meta: { title: '日活跃统计管理', requiresAuth: true, role: 'admin' }
      }
    ]
  },
  {
    path: 'logs',
    name: 'logManagement',
    component: () => import('../views/admin/LogManagement.vue')
  },
  {
    path: 'announcements',
    name: 'announcementManagement',
    component: () => import('../views/admin/AnnouncementManagement.vue')
  },
  {
    path: 'permissions',
    name: 'permissionManagement',
    component: () => import('../views/admin/PermissionManagement.vue')
  },
  {
    path: 'backup',
    name: 'backupManagement',
    component: () => import('../views/admin/BackupManagement.vue')
  },
  {
    path: 'keyword-library',
    name: 'keywordLibraryManagement',
    component: () => import('../views/admin/KeywordLibraryManagement.vue')
  },
  {
    path: 'ai-model',
    name: 'aiModelManagement',
    component: () => import('../views/admin/AIModelManagement.vue')
  },
  {
    path: 'ai-test',
    name: 'aiTest',
    component: () => import('../views/admin/AITest.vue')
  },
  {
    path: 'scoring-rule',
    name: 'scoringRuleManagement',
    component: () => import('../views/admin/ScoringRuleManagement.vue')
  },
  {
    path: 'resource-documents',
    name: 'resourceDocumentManagement',
    component: () => import('../views/admin/ResourceDocumentManagement.vue')
  },
  {
    path: 'problem-feedback',
    name: 'problemFeedbackManagement',
    component: () => import('../views/admin/FeedbackManagement.vue')
  },
  {
    path: 'system-settings',
    name: 'systemSettingsManagement',
    component: () => import('../views/admin/SystemSettingsManagement.vue')
  }
]

export default adminRoutes
