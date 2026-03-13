/**
 * 角色管理工具
 */

import { CONFIG, STORAGE_KEYS, TEACHER_ROLES, ROLE_TO_DASHBOARD_MAP, Roles } from './config'
import { getRoleStoragePrefix, getRoleFromPath } from './storage'
import logger from '@/utils/logger'

interface RoleConsistencyResult {
  consistent: boolean
  shouldUsePathRole: boolean
  keepCurrentRole?: boolean
}

/**
 * 检查是否为教师角色
 */
export function isTeacherRole(role: string): boolean {
  return TEACHER_ROLES.includes(role)
}

/**
 * 检查是否为管理员角色
 */
export function isAdminRole(role: string): boolean {
  return role === CONFIG.ROLES.ROLE_ADMIN
}

/**
 * 检查是否为学生角色
 */
export function isStudentRole(role: string): boolean {
  return role === CONFIG.ROLES.ROLE_STUDENT
}

/**
 * 检查是否为企业角色
 */
export function isCompanyRole(role: string): boolean {
  return role === CONFIG.ROLES.ROLE_COMPANY
}

/**
 * 获取角色的首页路径
 */
export function getDashboardPath(role: string): string {
  return ROLE_TO_DASHBOARD_MAP[role] || '/login'
}

/**
 * 根据角色重定向到首页
 */
export function redirectToDashboard(role: string, router: { push: (path: string) => void }): string {
  const path = getDashboardPath(role)
  if (path && router) {
    router.push(path)
  }
  return path
}

/**
 * 保存角色信息到 localStorage
 */
export function saveRoleInfoToStorage(
  role: string,
  accessToken: string,
  userId: string,
  username: string,
  refreshToken: string | null = null
): void {
  const rolePrefix = getRoleStoragePrefix(role)

  localStorage.setItem(rolePrefix + 'token_' + role, accessToken)
  localStorage.setItem(rolePrefix + 'accessToken_' + role, accessToken)

  if (refreshToken) {
    localStorage.setItem(rolePrefix + 'refreshToken_' + role, refreshToken)
  }

  localStorage.setItem(rolePrefix + 'role_' + role, role)
  localStorage.setItem(rolePrefix + 'userId_' + role, userId)
  localStorage.setItem(rolePrefix + 'username_' + role, username)
  localStorage.setItem(rolePrefix + 'isReadOnly_' + role, 'false')

  if (!localStorage.getItem(STORAGE_KEYS.CURRENT_ROLE)) {
    localStorage.setItem(STORAGE_KEYS.CURRENT_ROLE, role)
  }

  logger.log(`已保存${role}角色信息到本地存储`)
}

/**
 * 设置角色特定的 ID
 */
export function setRoleId(role: string, userId: string): void {
  if (isTeacherRole(role)) {
    localStorage.setItem(STORAGE_KEYS.TEACHER_ID, userId)
    logger.log('已设置 teacherId:', userId)
  } else if (isAdminRole(role)) {
    localStorage.setItem(STORAGE_KEYS.ADMIN_ID, userId)
    logger.log('已设置 adminId:', userId)
  } else if (isStudentRole(role)) {
    localStorage.setItem(STORAGE_KEYS.STUDENT_ID, userId)
    logger.log('已设置 studentId:', userId)
  } else if (isCompanyRole(role)) {
    localStorage.setItem(STORAGE_KEYS.COMPANY_ID, userId)
    logger.log('已设置 companyId:', userId)
  }
}

/**
 * 获取角色特定的 ID
 */
export function getRoleId(role: string): string | null {
  if (isTeacherRole(role)) {
    return localStorage.getItem(STORAGE_KEYS.TEACHER_ID)
  } else if (isAdminRole(role)) {
    return localStorage.getItem(STORAGE_KEYS.ADMIN_ID)
  } else if (isStudentRole(role)) {
    return localStorage.getItem(STORAGE_KEYS.STUDENT_ID)
  } else if (isCompanyRole(role)) {
    return localStorage.getItem(STORAGE_KEYS.COMPANY_ID)
  }
  return null
}

/**
 * 根据路径提取角色
 */
export function extractRoleFromPath(path: string): string | null {
  if (path.startsWith('/admin')) return CONFIG.ROLES.ROLE_ADMIN
  if (path.startsWith('/teacher')) return CONFIG.ROLES.ROLE_TEACHER
  if (path.startsWith('/student')) return CONFIG.ROLES.ROLE_STUDENT
  if (path.startsWith('/company')) return CONFIG.ROLES.ROLE_COMPANY
  return null
}

/**
 * 检查路径角色与当前角色是否一致（教师角色特殊处理）
 */
export function checkPathRoleConsistency(pathRole: string | null, currentRole: string): RoleConsistencyResult {
  const isTeacherPath = pathRole === CONFIG.ROLES.ROLE_TEACHER
  const isCurrentRoleTeacher = isTeacherRole(currentRole)

  if (pathRole && currentRole !== pathRole && !(isTeacherPath && isCurrentRoleTeacher)) {
    return { consistent: false, shouldUsePathRole: true }
  }

  if (isTeacherPath && isCurrentRoleTeacher && currentRole !== pathRole) {
    return { consistent: true, shouldUsePathRole: false, keepCurrentRole: true }
  }

  return { consistent: true, shouldUsePathRole: false }
}
