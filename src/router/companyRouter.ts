import type { RouteRecordRaw } from 'vue-router'

const companyRoutes: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    name: 'companyDashboard',
    component: () => import('../views/company/Dashboard.vue')
  },
  {
    path: 'positions',
    name: 'companyPositions',
    component: () => import('../views/company/PositionManagement.vue')
  }
]

export default companyRoutes
