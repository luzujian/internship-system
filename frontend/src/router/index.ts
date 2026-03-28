import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

// 认证页面
import Login from '../views/Login.vue'
import CompanyCheck from '../views/CompanyCheck.vue'
import CompanyRegister from '../views/CompanyRegister.vue'

// 404 页面
import NotFound from '../views/NotFound.vue'

// 导入管理员路由
import adminRoutes from './adminRouter'

// 导入企业路由
import companyRoutes from './companyRouter'

// 导入学生路由
import studentRoutes from './studentRouter'

// 导入教师路由
import teacherRoutes from './teacherRouter'

// 导入模块化守卫
import { registerGuards } from './guards'

// 导入路由预加载工具
import { initRoutePreload } from './preloadRoutes'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 默认路由
    {
      path: '/',
      redirect: '/login'
    },
    // 认证页面
    {
      path: '/login',
      name: 'login',
      component: Login,
      meta: {
        requiresAuth: false
      }
    },
    {
      path: '/company-register',
      name: 'companyRegister',
      component: CompanyRegister,
      meta: {
        requiresAuth: false
      }
    },
    {
      path: '/company-check',
      name: 'companyCheck',
      component: CompanyCheck,
      meta: {
        requiresAuth: false
      }
    },
    // 管理员路由
    {
      path: '/admin',
      name: 'admin',
      meta: {
        requiresAuth: true,
        roles: ['ROLE_ADMIN']
      },
      component: () => import('../views/admin/AdminLayout.vue'),
      children: adminRoutes
    },
    // 企业路由
    {
      path: '/company',
      name: 'company',
      meta: {
        requiresAuth: true,
        roles: ['ROLE_COMPANY']
      },
      component: () => import('../views/company/CompanyLayout.vue'),
      children: companyRoutes
    },
    // 学生路由
    {
      path: '/student',
      name: 'student',
      meta: {
        requiresAuth: true,
        roles: ['ROLE_STUDENT']
      },
      component: () => import('../views/student/StudentLayout.vue'),
      children: studentRoutes
    },
    // 教师路由
    {
      path: '/teacher',
      name: 'teacher',
      meta: {
        requiresAuth: true,
        roles: ['ROLE_TEACHER', 'ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR']
      },
      component: () => import('../views/teacher/TeacherLayout.vue'),
      children: teacherRoutes
    },
    // 404路由
    {
      path: '/:catchAll(.*)',
      name: 'notFound',
      component: NotFound
    }
  ]
})

// 注册路由守卫
registerGuards(router)

// 初始化路由预加载（在应用启动时预加载常用组件）
initRoutePreload()

// 路由后置守卫（用于页面标题）
router.afterEach((to) => {
  let pageTitle = 'deepintern'

  if (to.path === '/login') {
    pageTitle = '账号登录'
  } else if (to.path.startsWith('/admin')) {
    pageTitle = to.meta.title ? `${to.meta.title} - 实习-管理员` : '实习-管理员'
  } else if (to.path.startsWith('/student')) {
    pageTitle = to.meta.title ? `${to.meta.title} - 实习-学生` : '实习-学生'
  } else if (to.path.startsWith('/company')) {
    pageTitle = to.meta.title ? `${to.meta.title} - 实习-企业` : '实习-企业'
  } else if (to.path.startsWith('/teacher')) {
    pageTitle = to.meta.title ? `${to.meta.title} - 实习-教师` : '实习-教师'
  } else if (to.path === '/company-register') {
    pageTitle = '企业注册'
  } else if (to.path === '/company-check') {
    pageTitle = '企业审核'
  }

  document.title = pageTitle
})

export default router