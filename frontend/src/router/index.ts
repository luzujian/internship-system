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
  if (to.path === '/login') {
    document.title = '账号登录 - DeepIntern'
  } else if (to.path.startsWith('/admin')) {
    document.title = to.meta.title ? `${to.meta.title} - 管理端 - DeepIntern` : '管理端 - DeepIntern'
  } else if (to.path.startsWith('/student')) {
    document.title = to.meta.title ? `${to.meta.title} - 学生端 - DeepIntern` : '学生端 - DeepIntern'
  } else if (to.path.startsWith('/company')) {
    document.title = to.meta.title ? `${to.meta.title} - 企业端 - DeepIntern` : '企业端 - DeepIntern'
  } else if (to.path.startsWith('/teacher')) {
    document.title = to.meta.title ? `${to.meta.title} - 教师端 - DeepIntern` : '教师端 - DeepIntern'
  } else if (to.path === '/company-register') {
    document.title = '企业注册 - DeepIntern'
  } else if (to.path === '/company-check') {
    document.title = '企业审核 - DeepIntern'
  } else {
    document.title = 'DeepIntern'
  }
})

export default router