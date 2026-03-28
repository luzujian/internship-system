/**
 * 路由守卫 - 权限检查
 */

import { useAuthStore } from '@/store/auth'
import { ROLE_TO_DASHBOARD_MAP } from '@/store/auth/config'
import logger from '@/utils/logger'
import type { NavigationGuardNext, RouteLocationNormalized } from 'vue-router'

/**
 * 权限守卫
 */
export function permissionGuard(
  to: RouteLocationNormalized,
  from: RouteLocationNormalized,
  next: NavigationGuardNext
) {
  const authStore = useAuthStore()
  const isAuthenticated = authStore.isAuthenticated
  const userRole = authStore.role

  // 检查是否需要认证
  if (to.meta.requiresAuth) {
    // 未认证用户重定向到登录页
    if (!isAuthenticated) {
      logger.log('未认证用户访问受保护页面，重定向到登录页:', to.path)
      next('/login')
      return
    }

    // 检查用户角色权限
    if (to.meta.roles && Array.isArray(to.meta.roles) && !(to.meta.roles as string[]).includes(userRole || '')) {
      logger.warn('角色权限不足 - 需要角色:', to.meta.roles, '用户角色:', userRole)
      redirectToDashboard(userRole, next)
      return
    }
  } else {
    // 不需要认证的页面
    // 已认证用户访问登录页时，根据角色重定向到对应首页
    if (isAuthenticated && to.path === '/login') {
      logger.log('已认证用户访问登录页，重定向到对应首页')
      if (userRole && ROLE_TO_DASHBOARD_MAP[userRole as keyof typeof ROLE_TO_DASHBOARD_MAP]) {
        const dashboardPath = ROLE_TO_DASHBOARD_MAP[userRole as keyof typeof ROLE_TO_DASHBOARD_MAP]
        if (from.path !== dashboardPath) {
          redirectToDashboard(userRole, next)
          return
        }
      }
    }
  }

  next()
}

/**
 * 根据用户角色重定向到对应首页
 */
function redirectToDashboard(userRole: string | null, next: NavigationGuardNext) {
  if (!userRole) {
    logger.warn('用户角色为空，重定向到登录页')
    next('/login')
    return
  }

  const dashboardPath = ROLE_TO_DASHBOARD_MAP[userRole as keyof typeof ROLE_TO_DASHBOARD_MAP]
  if (dashboardPath) {
    next(dashboardPath)
  } else {
    logger.warn('未知用户角色:', userRole, '重定向到登录页')
    next('/login')
  }
}
