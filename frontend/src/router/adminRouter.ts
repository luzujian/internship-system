import { agentPage } from '@/agent/pageForms'
import type { RouteRecordRaw } from 'vue-router'
import { getAdminMenuPermission } from '@/utils/adminMenuPermissions'

const permissionMeta = (path: string) => ({ permission: getAdminMenuPermission(path) })

const adminRoutes: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    name: 'adminDashboard',
    component: agentPage('views/admin/Dashboard.vue', () => import('../views/admin/Dashboard.vue'))
  },
  {
    path: 'profile',
    name: 'adminProfile',
    component: agentPage('views/admin/ProfileCenter.vue', () => import('../views/admin/ProfileCenter.vue'))
  },
  {
    path: 'student-users',
    name: 'studentUserManagement',
    component: agentPage('views/admin/StudentUserManagement.vue', () => import('../views/admin/StudentUserManagement.vue')),
    meta: permissionMeta('/admin/student-users')
  },
  {
    path: 'teacher-users',
    name: 'teacherUserManagement',
    component: agentPage('views/admin/TeacherUserManagement.vue', () => import('../views/admin/TeacherUserManagement.vue')),
    meta: permissionMeta('/admin/teacher-users')
  },
  {
    path: 'admin-users',
    name: 'adminUserManagement',
    component: agentPage('views/admin/AdminUserManagement.vue', () => import('../views/admin/AdminUserManagement.vue'))
  },
  {
    path: 'companies',
    name: 'companyManagement',
    component: agentPage('views/admin/CompanyManagement.vue', () => import('../views/admin/CompanyManagement.vue')),
    meta: permissionMeta('/admin/companies')
  },
  {
    path: 'application-withdrawal-records',
    name: 'applicationWithdrawalRecordManagement',
    component: agentPage('views/admin/ApplicationWithdrawalRecordManagement.vue', () => import('../views/admin/ApplicationWithdrawalRecordManagement.vue')),
    meta: permissionMeta('/admin/application-withdrawal-records')
  },
  {
    path: 'internship-confirm-application',
    name: 'internshipConfirmApplication',
    component: agentPage('views/admin/InternshipConfirmApplication.vue', () => import('../views/admin/InternshipConfirmApplication.vue')),
    meta: permissionMeta('/admin/internship-confirm-application')
  },
  {
    path: 'recruitment',
    name: 'recruitmentManagement',
    component: agentPage('views/admin/RecruitmentManagement.vue', () => import('../views/admin/RecruitmentManagement.vue')),
    meta: permissionMeta('/admin/recruitment')
  },
  {
    path: 'majors',
    name: 'majorManagement',
    component: agentPage('views/admin/DepartmentMajorManagement.vue', () => import('../views/admin/DepartmentMajorManagement.vue')),
    meta: permissionMeta('/admin/majors')
  },
  {
    path: 'position-categories',
    name: 'positionCategoryManagement',
    component: agentPage('views/admin/PositionCategoryManagement.vue', () => import('../views/admin/PositionCategoryManagement.vue')),
    meta: permissionMeta('/admin/position-categories')
  },
  {
    path: 'classes',
    name: 'classManagement',
    component: agentPage('views/admin/ClassManagement.vue', () => import('../views/admin/ClassManagement.vue')),
    meta: permissionMeta('/admin/classes')
  },
  {
    path: 'data-statistics',
    name: 'DataStatistics',
    meta: { title: '数据统计管理', requiresAuth: true, role: 'admin', ...permissionMeta('/admin/data-statistics/active-statistics') },
    redirect: 'active-statistics',
    children: [
      {
        path: 'active-statistics',
        name: 'ActiveStatistics',
        component: agentPage('views/admin/DataStatisticsManagement.vue', () => import('../views/admin/DataStatisticsManagement.vue')),
        meta: { title: '日活跃统计管理', requiresAuth: true, role: 'admin', ...permissionMeta('/admin/data-statistics/active-statistics') }
      }
    ]
  },
  {
    path: 'logs',
    name: 'logManagement',
    component: agentPage('views/admin/LogManagement.vue', () => import('../views/admin/LogManagement.vue')),
    meta: permissionMeta('/admin/logs')
  },
  {
    path: 'announcements',
    name: 'announcementManagement',
    component: agentPage('views/admin/AnnouncementManagement.vue', () => import('../views/admin/AnnouncementManagement.vue')),
    meta: permissionMeta('/admin/announcements')
  },
  {
    path: 'permissions',
    name: 'permissionManagement',
    component: agentPage('views/admin/PermissionManagement.vue', () => import('../views/admin/PermissionManagement.vue')),
    meta: permissionMeta('/admin/permissions')
  },
  {
    path: 'backup',
    name: 'backupManagement',
    component: agentPage('views/admin/BackupManagement.vue', () => import('../views/admin/BackupManagement.vue')),
    meta: permissionMeta('/admin/backup')
  },
  {
    path: 'keyword-library',
    name: 'keywordLibraryManagement',
    component: agentPage('views/admin/KeywordLibraryManagement.vue', () => import('../views/admin/KeywordLibraryManagement.vue')),
    meta: permissionMeta('/admin/keyword-library')
  },
  {
    path: 'ai-model',
    name: 'aiModelManagement',
    component: agentPage('views/admin/AIModelManagement.vue', () => import('../views/admin/AIModelManagement.vue')),
    meta: permissionMeta('/admin/ai-model')
  },
  {
    path: 'ai-test',
    name: 'aiTest',
    component: agentPage('views/admin/AITest.vue', () => import('../views/admin/AITest.vue')),
    meta: permissionMeta('/admin/ai-test')
  },
  {
    path: 'scoring-rule',
    name: 'scoringRuleManagement',
    component: agentPage('views/admin/ScoringRuleManagement.vue', () => import('../views/admin/ScoringRuleManagement.vue')),
    meta: permissionMeta('/admin/scoring-rule')
  },
  {
    path: 'resource-documents',
    name: 'resourceDocumentManagement',
    component: agentPage('views/admin/ResourceDocumentManagement.vue', () => import('../views/admin/ResourceDocumentManagement.vue')),
    meta: permissionMeta('/admin/resource-documents')
  },
  {
    path: 'problem-feedback',
    name: 'problemFeedbackManagement',
    component: agentPage('views/admin/FeedbackManagement.vue', () => import('../views/admin/FeedbackManagement.vue')),
    meta: permissionMeta('/admin/problem-feedback')
  },
  {
    path: 'system-settings',
    name: 'systemSettingsManagement',
    component: agentPage('views/admin/SystemSettingsManagement.vue', () => import('../views/admin/SystemSettingsManagement.vue')),
    meta: permissionMeta('/admin/system-settings')
  }
]

export default adminRoutes
