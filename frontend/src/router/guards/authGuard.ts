/**
 * 路由守卫 - 认证检查
 */

import { useAuthStore } from '@/store/auth'
import { CONFIG, STORAGE_KEYS, Roles } from '@/store/auth/config'
import { extractRoleFromPath, checkPathRoleConsistency, isTeacherRole } from '@/store/auth/roles'
import { isTokenExpired as checkTokenExpired } from '@/store/auth/token'
import logger from '@/utils/logger'
import type { NavigationGuardNext, RouteLocationNormalized } from 'vue-router'

interface RoleConsistencyResult {
  consistent: boolean
  shouldUsePathRole: boolean
  keepCurrentRole?: boolean
}

/**
 * 认证守卫
 */
export async function authGuard(
  to: RouteLocationNormalized,
  from: RouteLocationNormalized,
  next: NavigationGuardNext
) {
  const authStore = useAuthStore()

  try {
    // 开发环境下的认证配置
    const isLocalDev = import.meta.env.DEV && window.location.hostname === 'localhost'
    const skipAuthCheck = isLocalDev && import.meta.env.VITE_SKIP_AUTH_CHECK === 'true'

    if (skipAuthCheck) {
      logger.warn('开发模式（跳过认证）：直接访问页面')
      next()
      return
    }

    // 检查是否需要恢复认证状态
    const currentRole = localStorage.getItem(STORAGE_KEYS.CURRENT_ROLE)
    const pathRole = extractRoleFromPath(to.path)

    // 处理路径角色与当前角色的一致性
    handleRoleConsistency(pathRole, currentRole)

    // 如果是刚登录的状态，跳过状态恢复（已经在登录时完成）
    // 但仍然需要进行权限验证
    const isJustLoggedIn = authStore.justLoggedIn
    if (isJustLoggedIn) {
      authStore.justLoggedIn = false
      logger.log('刚登录状态，跳过状态恢复，直接进行权限检查')
    }

    // 尝试恢复认证状态（仅当不是刚登录时）
    if (!isJustLoggedIn && currentRole && !authStore.isAuthenticated && to.meta.requiresAuth) {
      try {
        const restoreSuccess = await authStore.restoreAuthState(false)
        if (!restoreSuccess) {
          logger.warn('恢复认证状态失败，清除本地存储并重定向到登录页')
          await authStore.logout()
          if (to.path !== '/login') {
            next('/login')
          } else {
            next()
          }
          return
        }
      } catch (error) {
        logger.error('恢复认证状态失败:', error)
        await authStore.logout()
        if (to.path !== '/login') {
          next('/login')
        } else {
          next()
        }
        return
      }
    }

    // 检查 token 是否过期（仅当不是刚登录时）
    if (!isJustLoggedIn && authStore.token && checkTokenExpired(authStore.token)) {
      logger.warn('访问令牌已过期，尝试刷新')
      const refreshSuccess = await authStore.refreshAccessToken()

      if (!refreshSuccess) {
        logger.warn('刷新令牌失败，清除认证状态并重定向到登录页')
        await authStore.logout()
        if (to.path !== '/login') {
          next('/login')
        } else {
          next()
        }
        return
      }
    }

    // 如果目标路由是登录页，但用户已认证
    if (to.path === '/login' && authStore.isAuthenticated) {
      logger.warn('用户已认证，不需要访问登录页')
      const role = authStore.role || localStorage.getItem(STORAGE_KEYS.CURRENT_ROLE)
      if (role) {
        next(getRedirectPathForRole(role))
      } else {
        next('/admin/dashboard')
      }
      return
    }

    // 如果目标路由需要认证但用户未认证
    if (to.meta.requiresAuth && !authStore.isAuthenticated) {
      logger.warn('用户未认证，重定向到登录页')
      next('/login')
      return
    }

    // 权限检查
    if (to.meta.requiresRole && authStore.isAuthenticated) {
      const requiredRole = to.meta.requiresRole as string
      const currentRole = authStore.role

      // 教师角色的特殊处理
      const isRequiredRoleTeacher = isTeacherRole(requiredRole)
      const isCurrentRoleTeacher = isTeacherRole(currentRole || '')

      if (isRequiredRoleTeacher && isCurrentRoleTeacher) {
        next()
        return
      }

      if (currentRole !== requiredRole) {
        logger.warn(`用户角色 ${currentRole} 与目标路由要求的角色 ${requiredRole} 不匹配`)
        next('/login')
        return
      }
    }

    next()
  } catch (error) {
    logger.error('认证守卫执行出错:', error)
    next('/login')
  }
}

/**
 * 处理角色一致性
 */
function handleRoleConsistency(pathRole: string | null, currentRole: string | null) {
  if (!pathRole || !currentRole) {
    return
  }

  const consistency = checkPathRoleConsistency(pathRole, currentRole)

  if (!consistency.consistent && consistency.shouldUsePathRole) {
    logger.warn(`路径角色 ${pathRole} 与当前角色 ${currentRole} 不一致，使用路径角色`)
    localStorage.setItem(STORAGE_KEYS.CURRENT_ROLE, pathRole)
  } else if (consistency.keepCurrentRole) {
    logger.log(`教师路径但保持当前角色：${currentRole}`)
  }
}

/**
 * 获取角色的重定向路径
 */
function getRedirectPathForRole(role: string): string {
  const roleDashboards: Record<string, string> = {
    'ROLE_ADMIN': '/admin/dashboard',
    'ROLE_TEACHER': '/teacher/dashboard',
    'ROLE_TEACHER_COLLEGE': '/teacher/dashboard',
    'ROLE_TEACHER_DEPARTMENT': '/teacher/dashboard',
    'ROLE_TEACHER_COUNSELOR': '/teacher/dashboard',
    'ROLE_STUDENT': '/student/home',
    'ROLE_COMPANY': '/company/dashboard'
  }

  return roleDashboards[role] || '/admin/dashboard'
}

/**
 * 检查认证状态
 */
export function checkAuthStatus(
  authStore: ReturnType<typeof useAuthStore>,
  to: RouteLocationNormalized,
  next: NavigationGuardNext
) {
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    logger.warn('用户未认证，重定向到登录页')
    next('/login')
  } else {
    next()
  }
}
