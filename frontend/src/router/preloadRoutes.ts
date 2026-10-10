/**
 * 路由预加载工具
 * 登录成功后按当前角色预加载首页及相关组件，避免访客下载无关管理页面
 */

import logger from '@/utils/logger'

// 登录后需要立即预加载的首页组件
const homePageRoutes: Record<string, () => Promise<any>> = {
  'ROLE_ADMIN': () => import('../views/admin/Dashboard.vue'),
  'ROLE_STUDENT': () => import('../views/student/Home.vue'),
  'ROLE_COMPANY': () => import('../views/company/CompanyHome.vue'),
  'ROLE_TEACHER': () => import('../views/teacher/Home.vue'),
  'ROLE_TEACHER_COLLEGE': () => import('../views/teacher/Home.vue'),
  'ROLE_TEACHER_DEPARTMENT': () => import('../views/teacher/Home.vue'),
  'ROLE_TEACHER_COUNSELOR': () => import('../views/teacher/Home.vue')
}

/**
 * 登录后立即预加载首页组件
 * @param role 用户角色
 */
export function preloadHomePage(role: string): void {
  if (!role) return

  const homeRouteLoader = homePageRoutes[role]
  if (homeRouteLoader) {
    logger.log(`立即预加载首页组件: ${role}`)
    homeRouteLoader().catch(err => {
      console.warn(`预加载首页组件失败:`, err)
    })
  }

  const rolePrefix = role.startsWith('ROLE_TEACHER') ? 'ROLE_TEACHER' : role
  const relatedRoutes = getRelatedRoutes(rolePrefix)
  
  if (relatedRoutes.length > 0) {
    setTimeout(() => {
      relatedRoutes.forEach(loadRoute => {
        loadRoute().catch(err => {
          console.warn(`预加载相关路由失败:`, err)
        })
      })
    }, 100)
  }
}

function getRelatedRoutes(role: string): Array<() => Promise<any>> {
  const routeMap: Record<string, Array<() => Promise<any>>> = {
    'ROLE_ADMIN': [
      () => import('../views/admin/AdminLayout.vue'),
      () => import('../views/admin/StudentUserManagement.vue'),
      () => import('../views/admin/TeacherUserManagement.vue')
    ],
    'ROLE_STUDENT': [
      () => import('../views/student/StudentLayout.vue'),
      () => import('../views/student/Profile.vue')
    ],
    'ROLE_COMPANY': [
      () => import('../views/company/CompanyLayout.vue'),
      () => import('../views/company/CompanyInfo.vue')
    ],
    'ROLE_TEACHER': [
      () => import('../views/teacher/TeacherLayout.vue'),
      () => import('../views/teacher/Home.vue')
    ]
  }
  
  return routeMap[role] || []
}

/**
 * 预加载指定路由
 * @param routeLoader 路由加载函数
 */
export function preloadRoute(routeLoader: () => Promise<any>) {
  routeLoader().catch(err => {
    console.warn(`预加载路由失败:`, err)
  })
}
