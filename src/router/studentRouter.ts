import type { RouteRecordRaw } from 'vue-router'

const studentRoutes: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    name: 'studentDashboard',
    component: () => import('../views/student/Dashboard.vue')
  },
  {
    path: 'announcements',
    name: 'studentAnnouncements',
    component: () => import('../views/student/AnnouncementView.vue')
  },
  {
    path: 'internship',
    name: 'studentInternship',
    component: () => import('../views/student/InternshipManagement.vue')
  },
  {
    path: 'applications',
    name: 'studentApplications',
    component: () => import('../views/student/ApplicationManagement.vue')
  },
  {
    path: 'profile',
    name: 'studentProfile',
    component: () => import('../views/student/Profile.vue')
  }
]

export default studentRoutes
