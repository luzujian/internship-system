/**
 * 路由守卫 - 统一导出
 */

import { authGuard } from './authGuard'
import { permissionGuard } from './permissionGuard'
import { paramGuard } from './paramGuard'
import { logGuard, createLogGuard } from './logGuard'
import type { Router } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import { ROLE_TO_DASHBOARD_MAP } from '@/store/auth/config'
import { isBusinessRouteEnabled, loadBusinessFeatures } from '@/utils/businessFeatures'

export {
  authGuard,
  permissionGuard,
  paramGuard,
  logGuard,
  createLogGuard
}

/**
 * 注册所有守卫
 * @param router - Vue Router 实例
 */
export function registerGuards(router: Router) {
  // 注意：守卫的执行顺序很重要
  // 1. 参数验证
  // 2. 认证检查
  // 3. 权限检查
  // 4. 日志记录

  router.beforeEach(paramGuard)
  router.beforeEach(authGuard)
  router.beforeEach(async to => {
    if (!to.meta.requiresAuth || !useAuthStore().isAuthenticated) return
    await loadBusinessFeatures()
    if (!isBusinessRouteEnabled(to.path)) {
      ElMessage.info('实习心得业务已停用，历史数据保留')
      const role = useAuthStore().role as keyof typeof ROLE_TO_DASHBOARD_MAP
      return ROLE_TO_DASHBOARD_MAP[role] || '/login'
    }
  })
  router.beforeEach(permissionGuard)
  router.beforeEach(logGuard)
}
