/**
 * 教师端权限工具函数
 * 权限码由后端登录接口返回，优先使用当前标签页的认证身份。
 * 本模块不再维护硬编码权限矩阵，仅保留路由→权限码桥接映射
 */

import { useAuthStore } from '@/store/auth'
import { reflectionEnabled } from './businessFeatures'

// 教师类型枚举
export enum TeacherType {
  COLLEGE = 'COLLEGE',       // 学院教师
  DEPARTMENT = 'DEPARTMENT',   // 系室教师
  COUNSELOR = 'COUNSELOR'     // 辅导员教师
}

/**
 * 路由名 → 后端真实权限码(menu:xxx)映射
 * 基于数据库 teacher_permission 表，由管理员在权限管理页面分配
 * 这是结构性桥接（路由名↔权限码），不含权限策略
 */
const ROUTE_MENU_PERMISSIONS: Record<string, string> = {
  'teacherHome': 'menu:home',
  'teacherAnnouncements': 'menu:announcements',
  'teacherDashboard': 'menu:dashboard',
  'teacherStudentTracking': 'menu:student-tracking',
  'teacherResources': 'menu:resources',
  'teacherReports': 'menu:reports',
  'teacherAccountSettings': 'menu:account-settings',
  'teacherApproval': 'menu:approval',
  'teacherEvaluation': 'menu:evaluation',
  'teacherKeyTimelineConfig': 'menu:settings',
  'teacherTimelineConfig': 'menu:settings',
  'teacherAiSettings': 'menu:settings',
  'teacherAiKeywordConfig': 'menu:settings',
  'teacherCompanyList': 'menu:company-list',
  'teacherRecruitment': 'menu:recruitment',
  'teacherClasses': 'menu:classes',
  'teacherInternshipAppraisals': 'menu:appraisal-collection',
  'teacherAiScoringConfig': 'menu:teacherAiScoringConfig',
  'teacherGradeFinalize': 'menu:evaluation',
  'teacherStudentApplicationAudit': 'menu:approval',
  'teacherCompanyAudit': 'menu:approval',
  'teacherScoringRules': 'menu:teacherAiScoringConfig',
  'teacherKeywordLibrary': 'menu:teacherAiScoringConfig',
}

/** 始终放行的路由（基础功能，不依赖权限分配） */
const ALWAYS_ACCESSIBLE_ROUTES = new Set<string>([
  'teacherHome',
  'teacherAccountSettings',
  'teacherProfile',
])

/**
 * 从当前标签页获取后端返回的教师权限码列表。
 * 仅在尚未恢复认证状态时兼容旧版 localStorage 缓存。
 */
function getStoredTeacherPermissions(): string[] {
  const auth = useAuthStore()
  if (auth.isAuthenticated) {
    if (!auth.role?.startsWith('ROLE_TEACHER')) return []
    const permissions = auth.user?.permissions ?? auth.decodeToken(auth.token || '')?.permissions
    return Array.isArray(permissions) ? permissions.filter((value): value is string => typeof value === 'string') : []
  }
  try {
    const stored = localStorage.getItem('teacherPermissions')
    if (stored) {
      return JSON.parse(stored)
    }
  } catch (e) {
    console.error('[teacherPermissions] 解析教师权限失败:', e)
  }
  return []
}

/**
 * 检查用户是否有访问路由的权限
 * 基于后端返回的真实权限码(menu:xxx)判断，无硬编码权限回退
 * @param routeName 路由名称
 * @returns 是否有权限
 */
export function hasRoutePermission(teacherType: TeacherType, routeName: string): boolean {
  if (!reflectionEnabled.value && ['teacherEvaluation', 'teacherGradeFinalize', 'teacherAiSettings',
    'teacherAiKeywordConfig', 'teacherAiScoringConfig', 'teacherScoringRules', 'teacherKeywordLibrary'].includes(routeName)) return false
  if (ALWAYS_ACCESSIBLE_ROUTES.has(routeName)) {
    return true
  }
  const requiredCode = ROUTE_MENU_PERMISSIONS[routeName]
  if (!requiredCode) {
    // 未映射的路由默认放行（由后端接口二次校验兜底）
    return true
  }
  const storedPermissions = getStoredTeacherPermissions()
  return storedPermissions.includes(requiredCode)
}

/**
 * 获取当前用户的教师类型
 * @returns 教师类型
 */
export function getCurrentTeacherType(): TeacherType {
  const auth = useAuthStore()
  const currentType = auth.user?.teacherType || auth.role?.replace('ROLE_TEACHER_', '')
  if (currentType === TeacherType.COLLEGE || currentType === TeacherType.DEPARTMENT || currentType === TeacherType.COUNSELOR) {
    return currentType
  }
  const teacherTypeFromStorage = localStorage.getItem('teacherType')
  if (teacherTypeFromStorage === 'COLLEGE') return TeacherType.COLLEGE
  if (teacherTypeFromStorage === 'DEPARTMENT') return TeacherType.DEPARTMENT
  if (teacherTypeFromStorage === 'COUNSELOR') return TeacherType.COUNSELOR

  const userInfoStr = localStorage.getItem('userInfo')
  if (userInfoStr) {
    try {
      const userInfo = JSON.parse(userInfoStr)
      if (userInfo.teacherType === 'COLLEGE') return TeacherType.COLLEGE
      if (userInfo.teacherType === 'DEPARTMENT') return TeacherType.DEPARTMENT
      if (userInfo.teacherType === 'COUNSELOR') return TeacherType.COUNSELOR
    } catch (e) {
      console.error('解析用户信息失败:', e)
    }
  }
  return TeacherType.COLLEGE
}

/**
 * 检查当前用户是否有访问路由的权限
 * @param routeName 路由名称
 * @returns 是否有权限
 */
export function currentUserHasRoutePermission(routeName: string): boolean {
  const teacherType = getCurrentTeacherType()
  return hasRoutePermission(teacherType, routeName)
}

/**
 * 获取教师类型的显示名称
 * @param teacherType 教师类型
 * @returns 显示名称
 */
export function getTeacherTypeName(teacherType: TeacherType): string {
  const names: Record<TeacherType, string> = {
    [TeacherType.COLLEGE]: '学院教师',
    [TeacherType.DEPARTMENT]: '系室教师',
    [TeacherType.COUNSELOR]: '辅导员教师',
  }
  return names[teacherType] || '未知类型'
}
