import { agentPage } from '@/agent/pageForms'
import type { RouteRecordRaw } from 'vue-router'
import { canAccessStudentMenu, DEFAULT_STUDENT_MENUS } from '@/utils/studentMenuConfig'

// 预加载组件
const Jobs = agentPage('views/student/Jobs.vue', () => import('../views/student/Jobs.vue'))
const Interviews = agentPage('views/student/Interviews.vue', () => import('../views/student/Interviews.vue'))

const studentRoutes: RouteRecordRaw[] = [
  {
    path: 'home',
    name: 'studentHome',
    component: agentPage('views/student/Home.vue', () => import('../views/student/Home.vue'))
  },
  {
    path: 'jobs',
    name: 'studentJobs',
    component: Jobs,
    meta: {
      preload: true
    }
  },
  {
    path: 'applications',
    name: 'studentApplications',
    component: agentPage('views/student/Applications.vue', () => import('../views/student/Applications.vue'))
  },
  {
    path: 'internship-confirmation-form',
    name: 'studentInternshipConfirmationForm',
    component: agentPage('views/student/InternshipConfirmationForm.vue', () => import('../views/student/InternshipConfirmationForm.vue'))
  },
  {
    path: 'internship-registration',
    name: 'studentInternshipRegistration',
    component: agentPage('views/student/InternshipRegistration.vue', () => import('../views/student/InternshipRegistration.vue'))
  },
  {
    path: 'interviews',
    name: 'studentInterviews',
    component: Interviews,
    meta: {
      preload: true
    }
  },
  {
    path: 'internships',
    name: 'studentInternships',
    component: agentPage('views/student/Internships.vue', () => import('../views/student/Internships.vue'))
  },
  {
    path: 'internship-appraisal',
    name: 'studentInternshipAppraisal',
    component: agentPage('views/student/InternshipAppraisal.vue', () => import('../views/student/InternshipAppraisal.vue'))
  },
  {
    path: 'profile',
    name: 'studentProfile',
    component: agentPage('views/student/Profile.vue', () => import('../views/student/Profile.vue'))
  },
  {
    path: 'test',
    name: 'studentTest',
    component: agentPage('views/student/Test.vue', () => import('../views/student/Test.vue'))
  },
  {
    path: 'internship-reflection/submit',
    name: 'studentInternshipReflectionSubmit',
    component: agentPage('views/student/Internships.vue', () => import('../views/student/Internships.vue'))
  }
]

const controlledStudentPaths = new Set(DEFAULT_STUDENT_MENUS.map(menu => menu.path))

studentRoutes.forEach(route => {
  const fullPath = `/student/${String(route.path).replace(/^\//, '')}`
  if (!controlledStudentPaths.has(fullPath)) {
    return
  }

  route.beforeEnter = async (_to, _from, next) => {
    if (await canAccessStudentMenu(fullPath)) {
      next()
      return
    }
    next('/student/home')
  }
})

export default studentRoutes
