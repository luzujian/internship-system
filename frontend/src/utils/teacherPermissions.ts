/**
 * 教师端权限工具函数
 * 用于检查用户是否有特定权限
 */

// 教师类型枚举
export enum TeacherType {
  COLLEGE = 'COLLEGE',       // 学院教师
  DEPARTMENT = 'DEPARTMENT',   // 系室教师
  COUNSELOR = 'COUNSELOR'     // 辅导员教师
}

// 权限枚举
export enum Permission {
  // 通用权限（所有教师都有）
  VIEW_HOME = 'view:home',                    // 查看首页
  VIEW_NOTIFICATION = 'view:notification',        // 查看通知
  VIEW_DASHBOARD = 'view:dashboard',            // 查看实习状态看板
  VIEW_RESOURCES = 'view:resources',            // 查看资源管理
  VIEW_ACCOUNT_SETTINGS = 'view:account_settings', // 查看账号设置
  
  // 学院教师专属权限
  VIEW_ALL_STUDENTS = 'view:all_students',     // 查看所有学生
  VIEW_ALL_CLASSES = 'view:all_classes',       // 查看所有班级
  VIEW_ALL_MAJORS = 'view:all_majors',       // 查看所有专业
  VIEW_ALL_DEPARTMENTS = 'view:all_departments', // 查看所有系室
  MANAGE_SETTINGS = 'manage:settings',          // 管理系统设置
  VIEW_REPORTS = 'view:reports',              // 查看统计报表
  
  // 系室教师专属权限
  VIEW_DEPARTMENT_STUDENTS = 'view:department_students', // 查看本系学生
  VIEW_DEPARTMENT_CLASSES = 'view:department_classes',   // 查看本系班级
  VIEW_DEPARTMENT_MAJORS = 'view:department_majors',   // 查看本系专业
  EVALUATE_STUDENTS = 'evaluate:students',   // 评定学生成绩
  VIEW_STUDENT_TRACKING = 'view:student_tracking', // 查看学生状态监控
  
  // 辅导员教师专属权限
  VIEW_COUNSELOR_STUDENTS = 'view:counselor_students', // 查看辅导学生
  VIEW_COUNSELOR_CLASSES = 'view:counselor_classes',   // 查看辅导班级
  MANAGE_APPROVAL = 'manage:approval',       // 管理审核
  VIEW_EVALUATION = 'view:evaluation',       // 查看评分管理
}

// 教师类型与权限映射
const TEACHER_PERMISSIONS: Record<TeacherType, Permission[]> = {
  [TeacherType.COLLEGE]: [
    // 通用权限
    Permission.VIEW_HOME,
    Permission.VIEW_NOTIFICATION,
    Permission.VIEW_DASHBOARD,
    Permission.VIEW_RESOURCES,
    Permission.VIEW_ACCOUNT_SETTINGS,
    // 学院教师专属权限
    Permission.VIEW_ALL_STUDENTS,
    Permission.VIEW_ALL_CLASSES,
    Permission.VIEW_ALL_MAJORS,
    Permission.VIEW_ALL_DEPARTMENTS,
    Permission.MANAGE_SETTINGS,
    Permission.VIEW_REPORTS,
    // 也包含系室和辅导员的部分权限
    Permission.VIEW_DEPARTMENT_STUDENTS,
    Permission.VIEW_DEPARTMENT_CLASSES,
    Permission.VIEW_DEPARTMENT_MAJORS,
    Permission.EVALUATE_STUDENTS,
    Permission.VIEW_STUDENT_TRACKING,
    Permission.MANAGE_APPROVAL,
    Permission.VIEW_EVALUATION,
  ],
  [TeacherType.DEPARTMENT]: [
    // 通用权限
    Permission.VIEW_HOME,
    Permission.VIEW_NOTIFICATION,
    Permission.VIEW_DASHBOARD,
    Permission.VIEW_RESOURCES,
    Permission.VIEW_ACCOUNT_SETTINGS,
    // 系室教师专属权限
    Permission.VIEW_DEPARTMENT_STUDENTS,
    Permission.VIEW_DEPARTMENT_CLASSES,
    Permission.VIEW_DEPARTMENT_MAJORS,
    Permission.EVALUATE_STUDENTS,
    Permission.VIEW_STUDENT_TRACKING,
    Permission.MANAGE_APPROVAL,
    Permission.VIEW_EVALUATION,
  ],
  [TeacherType.COUNSELOR]: [
    // 通用权限
    Permission.VIEW_HOME,
    Permission.VIEW_NOTIFICATION,
    Permission.VIEW_DASHBOARD,
    Permission.VIEW_RESOURCES,
    Permission.VIEW_ACCOUNT_SETTINGS,
    // 辅导员教师专属权限
    Permission.VIEW_COUNSELOR_STUDENTS,
    Permission.VIEW_COUNSELOR_CLASSES,
    Permission.MANAGE_APPROVAL,
    Permission.VIEW_EVALUATION,
    Permission.VIEW_STUDENT_TRACKING,
  ],
}

// 路由与权限映射
export const ROUTE_PERMISSIONS: Record<string, Permission[]> = {
  'teacherHome': [Permission.VIEW_HOME],
  'teacherDashboard': [Permission.VIEW_DASHBOARD],
  'teacherStudentTracking': [Permission.VIEW_STUDENT_TRACKING],
  'teacherApproval': [Permission.MANAGE_APPROVAL],
  'teacherEvaluation': [Permission.VIEW_EVALUATION, Permission.EVALUATE_STUDENTS],
  'teacherAnnouncements': [Permission.VIEW_NOTIFICATION],
  'teacherInternshipStatus': [Permission.VIEW_DASHBOARD],
  'teacherCompanyList': [Permission.VIEW_DASHBOARD],
  'teacherStudents': [Permission.VIEW_ALL_STUDENTS, Permission.VIEW_DEPARTMENT_STUDENTS, Permission.VIEW_COUNSELOR_STUDENTS],
  'teacherResources': [Permission.VIEW_RESOURCES],
  'teacherReports': [Permission.VIEW_REPORTS],
  'teacherSettings': [Permission.MANAGE_SETTINGS],
  'teacherAccountSettings': [Permission.VIEW_ACCOUNT_SETTINGS],
  'teacherClasses': [Permission.VIEW_ALL_CLASSES, Permission.VIEW_DEPARTMENT_CLASSES, Permission.VIEW_COUNSELOR_CLASSES],
}

/**
 * 获取用户权限列表
 * @param teacherType 教师类型
 * @returns 权限列表
 */
export function getUserPermissions(teacherType: TeacherType): Permission[] {
  return TEACHER_PERMISSIONS[teacherType] || []
}

/**
 * 检查用户是否有指定权限
 * @param teacherType 教师类型
 * @param permission 权限
 * @returns 是否有权限
 */
export function hasPermission(teacherType: TeacherType, permission: Permission): boolean {
  const permissions = getUserPermissions(teacherType)
  return permissions.includes(permission)
}

/**
 * 检查用户是否有任意一个指定权限
 * @param teacherType 教师类型
 * @param permissions 权限列表
 * @returns 是否有任意一个权限
 */
export function hasAnyPermission(teacherType: TeacherType, permissions: Permission[]): boolean {
  return permissions.some(permission => hasPermission(teacherType, permission))
}

/**
 * 检查用户是否有所有指定权限
 * @param teacherType 教师类型
 * @param permissions 权限列表
 * @returns 是否有所有权限
 */
export function hasAllPermissions(teacherType: TeacherType, permissions: Permission[]): boolean {
  return permissions.every(permission => hasPermission(teacherType, permission))
}

/**
 * 检查用户是否有访问路由的权限
 * @param teacherType 教师类型
 * @param routeName 路由名称
 * @returns 是否有权限
 */
export function hasRoutePermission(teacherType: TeacherType, routeName: string): boolean {
  const requiredPermissions = ROUTE_PERMISSIONS[routeName]
  if (!requiredPermissions || requiredPermissions.length === 0) {
    return true // 没有权限要求的路由默认允许访问
  }
  return hasAnyPermission(teacherType, requiredPermissions)
}

/**
 * 获取当前用户的教师类型
 * @returns 教师类型
 */
export function getCurrentTeacherType(): TeacherType {
  const userInfoStr = localStorage.getItem('userInfo')
  if (userInfoStr) {
    try {
      const userInfo = JSON.parse(userInfoStr)
      return userInfo.teacherType || TeacherType.COLLEGE
    } catch (e) {
      console.error('解析用户信息失败:', e)
    }
  }
  return TeacherType.COLLEGE
}

/**
 * 检查当前用户是否有指定权限
 * @param permission 权限
 * @returns 是否有权限
 */
export function currentUserHasPermission(permission: Permission): boolean {
  const teacherType = getCurrentTeacherType()
  return hasPermission(teacherType, permission)
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

/**
 * 根据教师类型获取可访问的路由列表
 * @param teacherType 教师类型
 * @returns 可访问的路由名称列表
 */
export function getAccessibleRoutes(teacherType: TeacherType): string[] {
  const permissions = getUserPermissions(teacherType)
  const accessibleRoutes: string[] = []
  
  for (const [routeName, requiredPermissions] of Object.entries(ROUTE_PERMISSIONS)) {
    if (requiredPermissions.length === 0 || 
        requiredPermissions.some(perm => permissions.includes(perm))) {
      accessibleRoutes.push(routeName)
    }
  }
  
  return accessibleRoutes
}

/**
 * 获取当前用户可访问的路由列表
 * @returns 可访问的路由名称列表
 */
export function getCurrentUserAccessibleRoutes(): string[] {
  const teacherType = getCurrentTeacherType()
  return getAccessibleRoutes(teacherType)
}