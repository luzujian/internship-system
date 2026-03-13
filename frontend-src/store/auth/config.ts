/**
 * 认证模块配置常量
 */

export interface Config {
  MIN_REFRESH_INTERVAL: number
  TOKEN_EXPIRY_BUFFER: number
  MAX_RETRY_ATTEMPTS: number
  BASE_RETRY_DELAY: number
  ROLES: Roles
  ROLE_PREFIXES: RolePrefixes
}

export interface Roles {
  ROLE_STUDENT: string
  ROLE_TEACHER: string
  ROLE_TEACHER_COLLEGE: string
  ROLE_TEACHER_DEPARTMENT: string
  ROLE_TEACHER_COUNSELOR: string
  ROLE_ADMIN: string
  ROLE_COMPANY: string
}

export interface RolePrefixes {
  ROLE_STUDENT: string
  ROLE_TEACHER: string
  ROLE_TEACHER_COLLEGE: string
  ROLE_TEACHER_DEPARTMENT: string
  ROLE_TEACHER_COUNSELOR: string
  ROLE_ADMIN: string
  ROLE_COMPANY: string
}

export interface StorageKeys {
  CURRENT_ROLE: string
  TEACHER_TYPE: string
  TEACHER_PERMISSIONS: string
  ADMIN_PERMISSIONS: string
  TEACHER_ID: string
  ADMIN_ID: string
  STUDENT_ID: string
  COMPANY_ID: string
  STUDENT_STATUS_MAP: string
  TEACHER_STATUS_MAP: string
}

export interface ErrorMessages {
  LOGIN_FAILED: string
  REGISTER_FAILED: string
  TOKEN_EXPIRED: string
  USER_DISABLED: string
  TOKEN_REFRESH_FAILED: string
}

export interface RoleToDashboardMap {
  [key: string]: string
}

export const CONFIG: Config = {
  MIN_REFRESH_INTERVAL: 3000,
  TOKEN_EXPIRY_BUFFER: 30,
  MAX_RETRY_ATTEMPTS: 2,
  BASE_RETRY_DELAY: 1000,
  ROLES: {
    ROLE_STUDENT: 'ROLE_STUDENT',
    ROLE_TEACHER: 'ROLE_TEACHER',
    ROLE_TEACHER_COLLEGE: 'ROLE_TEACHER_COLLEGE',
    ROLE_TEACHER_DEPARTMENT: 'ROLE_TEACHER_DEPARTMENT',
    ROLE_TEACHER_COUNSELOR: 'ROLE_TEACHER_COUNSELOR',
    ROLE_ADMIN: 'ROLE_ADMIN',
    ROLE_COMPANY: 'ROLE_COMPANY'
  },
  ROLE_PREFIXES: {
    ROLE_STUDENT: 'student_',
    ROLE_TEACHER: 'teacher_',
    ROLE_TEACHER_COLLEGE: 'teacher_',
    ROLE_TEACHER_DEPARTMENT: 'teacher_',
    ROLE_TEACHER_COUNSELOR: 'teacher_',
    ROLE_ADMIN: 'admin_',
    ROLE_COMPANY: 'company_'
  }
}

export const STORAGE_KEYS: StorageKeys = {
  CURRENT_ROLE: 'current_role',
  TEACHER_TYPE: 'teacherType',
  TEACHER_PERMISSIONS: 'teacherPermissions',
  ADMIN_PERMISSIONS: 'adminPermissions',
  TEACHER_ID: 'teacherId',
  ADMIN_ID: 'adminId',
  STUDENT_ID: 'studentId',
  COMPANY_ID: 'companyId',
  STUDENT_STATUS_MAP: 'studentStatusMap',
  TEACHER_STATUS_MAP: 'teacherStatusMap'
}

export const ERROR_MESSAGES: ErrorMessages = {
  LOGIN_FAILED: '用户名或密码错误',
  REGISTER_FAILED: '注册失败',
  TOKEN_EXPIRED: '认证信息已过期，请重新登录',
  USER_DISABLED: '用户已被禁用',
  TOKEN_REFRESH_FAILED: '刷新令牌失败，请重新登录'
}

// 角色到首页的映射
export const ROLE_TO_DASHBOARD_MAP: RoleToDashboardMap = {
  'ROLE_ADMIN': '/admin/dashboard',
  'ROLE_TEACHER': '/teacher/dashboard',
  'ROLE_TEACHER_COLLEGE': '/teacher/dashboard',
  'ROLE_TEACHER_DEPARTMENT': '/teacher/dashboard',
  'ROLE_TEACHER_COUNSELOR': '/teacher/dashboard',
  'ROLE_STUDENT': '/student/dashboard',
  'ROLE_COMPANY': '/company/dashboard'
}

// 教师角色列表
export const TEACHER_ROLES: string[] = [
  CONFIG.ROLES.ROLE_TEACHER,
  CONFIG.ROLES.ROLE_TEACHER_COLLEGE,
  CONFIG.ROLES.ROLE_TEACHER_DEPARTMENT,
  CONFIG.ROLES.ROLE_TEACHER_COUNSELOR
]
