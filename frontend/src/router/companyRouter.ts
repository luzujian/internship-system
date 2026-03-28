import type { RouteRecordRaw } from 'vue-router'

const companyRoutes: RouteRecordRaw[] = [
  {
    path: '',
    name: 'company-home',
    redirect: 'dashboard'
  },
  {
    path: 'dashboard',
    name: 'companyDashboard',
    component: () => import('../views/company/CompanyHome.vue')
  },
  {
    path: 'jobs',
    name: 'RecruitmentManagement',
    component: () => import('../views/company/RecruitmentManagement.vue')
  },
  {
    path: 'internship-confirm',
    name: 'InternshipConfirm',
    component: () => import('../views/company/InternshipConfirm.vue')
  },
  {
    path: 'application-view',
    name: 'ApplicationView',
    component: () => import('../views/company/ApplicationView.vue')
  },
  {
    path: 'interviews',
    name: 'CompanyInfo',
    component: () => import('../views/company/CompanyInfo.vue')
  },
  {
    path: 'settings',
    name: 'AccountSettings',
    component: () => import('../views/company/AccountSettings.vue')
  }
]

export default companyRoutes
