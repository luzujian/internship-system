/**
 * 路由预加载工具
 * 在浏览器空闲时预加载常用路由组件，提升页面切换速度
 */

import logger from '@/utils/logger'

// 预加载高优先级的路由组件（首页和常用页面）
const highPriorityRoutes = [
  () => import('../views/admin/Dashboard.vue'),
  () => import('../views/admin/StudentUserManagement.vue'),
  () => import('../views/admin/TeacherUserManagement.vue'),
  () => import('../views/admin/CompanyManagement.vue'),
  () => import('../views/admin/AnnouncementManagement.vue'),
  () => import('../views/admin/KeywordLibraryManagement.vue'),
  () => import('../views/admin/ScoringRuleManagement.vue'),
  () => import('../views/admin/AIModelManagement.vue'),
  () => import('../views/admin/AITest.vue'),
  () => import('../views/admin/InternshipConfirmApplication.vue'),
  () => import('../views/admin/ApplicationWithdrawalRecordManagement.vue'),
  () => import('../views/admin/RecruitmentManagement.vue')
]

// 预加载中等优先级的路由组件
const mediumPriorityRoutes = [
  () => import('../views/admin/RecruitmentManagement.vue'),
  () => import('../views/admin/ClassManagement.vue'),
  () => import('../views/admin/DepartmentMajorManagement.vue'),
  () => import('../views/admin/PositionCategoryManagement.vue'),
  () => import('../views/admin/FeedbackManagement.vue')
]

// 预加载低优先级的路由组件
const lowPriorityRoutes = [
  () => import('../views/admin/LogManagement.vue'),
  () => import('../views/admin/PermissionManagement.vue'),
  () => import('../views/admin/BackupManagement.vue'),
  () => import('../views/admin/ResourceDocumentManagement.vue'),
  () => import('../views/admin/SystemSettingsManagement.vue'),
  () => import('../views/admin/DataStatisticsManagement.vue'),
  () => import('../views/admin/AdminUserManagement.vue'),
  () => import('../views/admin/ProfileCenter.vue')
]

// 登录后需要立即预加载的首页组件
const homePageRoutes: Record<string, () => Promise<any>> = {
  'ROLE_ADMIN': () => import('../views/admin/Dashboard.vue'),
  'ROLE_STUDENT': () => import('../views/student/Home.vue'),
  'ROLE_COMPANY': () => import('../views/company/CompanyHome.vue'),
  'ROLE_TEACHER': () => import('../views/teacher/Home.vue'),
  'ROLE_TEACHER_COLLEGE': () => import('../views/teacher/Home.vue'),
  'ROLE_TEACHER_DEPARTMENT': () => import('../views/teacher/Home.vue'),
  'ROLE_TEACHER_COUNSELOR': () => import('../views/teacher/Home.vue')
}

/**
 * 预加载路由组件
 * 使用 requestIdleCallback 在浏览器空闲时加载，避免阻塞主线程
 */
function preloadRoutes(routes: Array<() => Promise<any>>, priority: 'high' | 'medium' | 'low' = 'low') {
  if ('requestIdleCallback' in window) {
    (window as any).requestIdleCallback(() => {
      routes.forEach(loadRoute => {
        loadRoute().catch(err => {
          console.warn(`预加载路由失败:`, err)
        })
      })
    }, {
      timeout: priority === 'high' ? 1000 : priority === 'medium' ? 2000 : 5000
    })
  } else {
    setTimeout(() => {
      routes.forEach(loadRoute => {
        loadRoute().catch(err => {
          console.warn(`预加载路由失败:`, err)
        })
      })
    }, priority === 'high' ? 100 : priority === 'medium' ? 500 : 1000)
  }
}

/**
 * 登录后立即预加载首页组件
 * @param role 用户角色
 */
export function preloadHomePage(role: string): void {
  if (!role) return

  const homeRouteLoader = homePageRoutes[role]
  if (homeRouteLoader) {
    logger.log(`立即预加载首页组件: ${role}`)
    homeRouteLoader().catch(err => {
      console.warn(`预加载首页组件失败:`, err)
    })
  }

  const rolePrefix = role.startsWith('ROLE_TEACHER') ? 'ROLE_TEACHER' : role
  const relatedRoutes = getRelatedRoutes(rolePrefix)
  
  if (relatedRoutes.length > 0) {
    setTimeout(() => {
      relatedRoutes.forEach(loadRoute => {
        loadRoute().catch(err => {
          console.warn(`预加载相关路由失败:`, err)
        })
      })
    }, 100)
  }
}

function getRelatedRoutes(role: string): Array<() => Promise<any>> {
  const routeMap: Record<string, Array<() => Promise<any>>> = {
    'ROLE_ADMIN': [
      () => import('../views/admin/AdminLayout.vue'),
      () => import('../views/admin/StudentUserManagement.vue'),
      () => import('../views/admin/TeacherUserManagement.vue')
    ],
    'ROLE_STUDENT': [
      () => import('../views/student/StudentLayout.vue'),
      () => import('../views/student/Profile.vue')
    ],
    'ROLE_COMPANY': [
      () => import('../views/company/CompanyLayout.vue'),
      () => import('../views/company/CompanyInfo.vue')
    ],
    'ROLE_TEACHER': [
      () => import('../views/teacher/TeacherLayout.vue'),
      () => import('../views/teacher/Home.vue')
    ]
  }
  
  return routeMap[role] || []
}

/**
 * 初始化路由预加载
 * 在应用启动时调用，按优先级加载路由组件
 */
export function initRoutePreload() {
  preloadRoutes(highPriorityRoutes, 'high')
  
  setTimeout(() => {
    preloadRoutes(mediumPriorityRoutes, 'medium')
  }, 500)
  
  setTimeout(() => {
    preloadRoutes(lowPriorityRoutes, 'low')
  }, 2000)
}

/**
 * 预加载指定路由
 * @param routeLoader 路由加载函数
 */
export function preloadRoute(routeLoader: () => Promise<any>) {
  routeLoader().catch(err => {
    console.warn(`预加载路由失败:`, err)
  })
}
