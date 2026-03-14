import { createRouter, createWebHistory } from 'vue-router'
import CompanyLayout from '../views/company/CompanyLayout.vue'
import CompanyHome from '../views/company/CompanyHome.vue'
import InternshipConfirm from '../views/company/InternshipConfirm.vue'
import CompanyInfo from '../views/company/CompanyInfo.vue'
import AccountSettings from '../views/company/AccountSettings.vue'
import RecruitmentManagement from '../views/company/RecruitmentManagement.vue'
import ApplicationView from '../views/company/ApplicationView.vue'
import Login from '../views/Login.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login
  },
  {
    path: '/',
    redirect: '/company'
  },
  {
    path: '/company',
    component: CompanyLayout,
    children: [
      {
        path: '',
        name: 'CompanyHome',
        component: CompanyHome
      },
      {
        path: 'jobs',
        name: 'RecruitmentManagement',
        component: RecruitmentManagement
      },
      {
        path: 'internship-confirm',
        name: 'InternshipConfirm',
        component: InternshipConfirm
      },
      {
        path: 'application-view',
        name: 'ApplicationView',
        component: ApplicationView
      },
      {
        path: 'interviews',
        name: 'CompanyInfo',
        component: CompanyInfo
      },
      {
        path: 'settings',
        name: 'AccountSettings',
        component: AccountSettings
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
