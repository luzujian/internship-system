/**
 * 路由守卫 - 日志记录
 */

import logger from '@/utils/logger'
import type { NavigationGuardNext, RouteLocationNormalized } from 'vue-router'

/**
 * 日志守卫
 */
export function logGuard(
  to: RouteLocationNormalized,
  from: RouteLocationNormalized,
  next: NavigationGuardNext
) {
  // 记录页面访问日志
  logPageAccess(to, from)

  next()
}

/**
 * 记录页面访问日志
 */
function logPageAccess(to: RouteLocationNormalized, from: RouteLocationNormalized, userRole?: string) {
  const logInfo = {
    path: to.path,
    from: from.path,
    userRole: userRole || 'anonymous',
    timestamp: new Date().toISOString()
  }
  logger.log('页面访问:', logInfo)

  // 实际项目中可以发送到后端记录
  // request.post('/api/logs/page-access', logInfo)
}

/**
 * 创建带用户角色的日志守卫
 */
export function createLogGuard(getUserRole: () => string | null) {
  return (to: RouteLocationNormalized, from: RouteLocationNormalized, next: NavigationGuardNext) => {
    const userRole = getUserRole()
    logPageAccess(to, from, userRole || undefined)
    next()
  }
}
