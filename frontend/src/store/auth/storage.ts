/**
 * 存储工具函数
 */

import { CONFIG, STORAGE_KEYS, Roles } from './config'
import logger from '@/utils/logger'

/**
 * 从路径获取角色
 */
export function getRoleFromPath(): string {
  const path = window.location.pathname
  if (path.startsWith('/student') || path.includes('/student/')) {
    return CONFIG.ROLES.ROLE_STUDENT
  }
  if (path.startsWith('/teacher') || path.includes('/teacher/')) {
    return CONFIG.ROLES.ROLE_TEACHER
  }
  if (path.startsWith('/admin') || path.includes('/admin/')) {
    return CONFIG.ROLES.ROLE_ADMIN
  }
  if (path.startsWith('/company') || path.includes('/company/')) {
    return CONFIG.ROLES.ROLE_COMPANY
  }
  const currentRole = localStorage.getItem(STORAGE_KEYS.CURRENT_ROLE)
  if (currentRole) {
    return currentRole
  }
  return CONFIG.ROLES.ROLE_STUDENT
}

/**
 * 获取角色存储前缀
 */
export function getRoleStoragePrefix(role: string | null = null): string {
  if (!role) {
    const currentRole = getRoleFromPath()
    return CONFIG.ROLE_PREFIXES[currentRole as keyof typeof CONFIG.ROLE_PREFIXES] || ''
  }
  return CONFIG.ROLE_PREFIXES[role as keyof typeof CONFIG.ROLE_PREFIXES] || ''
}

/**
 * 获取角色的 Token Key
 */
export function getTokenKey(role: string): string {
  const prefix = getRoleStoragePrefix(role)
  return `${prefix}token_${role}`
}

/**
 * 获取角色的 Access Token Key
 */
export function getAccessTokenKey(role: string): string {
  const prefix = getRoleStoragePrefix(role)
  return `${prefix}accessToken_${role}`
}

/**
 * 获取角色的 Refresh Token Key
 */
export function getRefreshTokenKey(role: string): string {
  const prefix = getRoleStoragePrefix(role)
  return `${prefix}refreshToken_${role}`
}

/**
 * 获取角色的用户 ID Key
 */
export function getUserIdKey(role: string): string {
  const prefix = getRoleStoragePrefix(role)
  return `${prefix}userId_${role}`
}

/**
 * 获取角色的用户名 Key
 */
export function getUsernameKey(role: string): string {
  const prefix = getRoleStoragePrefix(role)
  return `${prefix}username_${role}`
}

/**
 * 清除角色的所有存储数据
 */
export function clearRoleStorage(role: string): void {
  const prefix = getRoleStoragePrefix(role)
  const keysToRemove = [
    'accessToken',
    'refreshToken',
    'token',
    'role',
    'userId',
    'username',
    'isReadOnly',
    'originalAdminUsername',
    'lastRefreshTime',
    'refreshAttempts',
    'forceRefresh',
    'hasError',
    'refreshError'
  ]

  keysToRemove.forEach(key => {
    localStorage.removeItem(`${prefix}${key}_${role}`)
    localStorage.removeItem(`${prefix}${key}`)
  })

  logger.log(`已清除角色 ${role} 的存储数据`)
}

/**
 * 清除所有角色的存储数据
 */
export function clearAllRolesStorage(): void {
  const roleTypes = Object.values(CONFIG.ROLES)
  roleTypes.forEach(role => {
    clearRoleStorage(role)
  })
  localStorage.removeItem(STORAGE_KEYS.CURRENT_ROLE)
  logger.log('已清除所有角色的存储数据')
}

/**
 * 验证字符串类型
 */
export function validateString(value: unknown, fieldName: string): boolean {
  if (typeof value !== 'string') {
    logger.error(`${fieldName} 必须是字符串类型`)
    return false
  }
  return true
}

/**
 * 验证对象类型
 */
export function validateObject(value: unknown, fieldName: string): boolean {
  if (typeof value !== 'object' || value === null) {
    logger.error(`${fieldName} 必须是对象类型`)
    return false
  }
  return true
}
