/**
 * 路由守卫 - 参数验证
 */

import logger from '@/utils/logger'
import type { NavigationGuardNext, RouteLocationNormalized } from 'vue-router'

/**
 * 参数验证守卫
 */
export function paramGuard(
  to: RouteLocationNormalized,
  from: RouteLocationNormalized,
  next: NavigationGuardNext
) {
  // 验证路由参数
  if (!validateRouteParams(to)) {
    next('/404')
    return
  }

  next()
}

/**
 * 验证路由参数
 */
function validateRouteParams(to: RouteLocationNormalized): boolean {
  if (to.params.id) {
    const id = parseInt(to.params.id as string)
    if (isNaN(id) || id <= 0) {
      logger.warn('无效的路由参数:', to.params.id)
      return false
    }
  }
  return true
}
