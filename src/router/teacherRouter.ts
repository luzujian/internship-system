import type { RouteRecordRaw } from 'vue-router'

const teacherRoutes: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    name: 'teacherDashboard',
    component: () => import('../views/teacher/Dashboard.vue')
  },
  {
    path: 'announcements',
    name: 'teacherAnnouncements',
    component: () => import('../views/teacher/AnnouncementView.vue')
  },
  {
    path: 'classes',
    name: 'teacherClasses',
    component: () => import('../views/teacher/ClassManagement.vue')
  },
  {
    path: 'students',
    name: 'teacherStudents',
    component: () => import('../views/teacher/StudentManagement.vue')
  },
  {
    path: 'internship',
    name: 'teacherInternship',
    component: () => import('../views/teacher/InternshipManagement.vue')
  },
  {
    path: 'company-audit',
    name: 'teacherCompanyAudit',
    component: () => import('../views/teacher/CompanyAudit.vue'),
    meta: {
      title: '企业审核'
    }
  },
  {
    path: 'profile',
    name: 'teacherProfile',
    component: () => import('../views/teacher/Profile.vue')
  }
]

export default teacherRoutes
