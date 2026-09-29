import { agentPage } from '@/agent/pageForms'
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
    component: agentPage('views/company/CompanyHome.vue', () => import('../views/company/CompanyHome.vue'))
  },
  {
    path: 'jobs',
    name: 'RecruitmentManagement',
    component: agentPage('views/company/RecruitmentManagement.vue', () => import('../views/company/RecruitmentManagement.vue'))
  },
  {
    path: 'internship-confirm',
    name: 'InternshipConfirm',
    component: agentPage('views/company/InternshipConfirm.vue', () => import('../views/company/InternshipConfirm.vue'))
  },
  {
    path: 'application-view',
    name: 'ApplicationView',
    component: agentPage('views/company/ApplicationView.vue', () => import('../views/company/ApplicationView.vue'))
  },
  {
    path: 'interviews',
    name: 'CompanyInfo',
    component: agentPage('views/company/CompanyInfo.vue', () => import('../views/company/CompanyInfo.vue'))
  },
  {
    path: 'settings',
    name: 'AccountSettings',
    component: agentPage('views/company/AccountSettings.vue', () => import('../views/company/AccountSettings.vue'))
  }
]

export default companyRoutes
