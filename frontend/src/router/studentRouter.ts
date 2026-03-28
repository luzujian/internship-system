import type { RouteRecordRaw } from 'vue-router'

// 预加载组件
const Jobs = () => import('../views/student/Jobs.vue')
const Interviews = () => import('../views/student/Interviews.vue')

const studentRoutes: RouteRecordRaw[] = [
  {
    path: 'home',
    name: 'studentHome',
    component: () => import('../views/student/Home.vue')
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
    component: () => import('../views/student/Applications.vue')
  },
  {
    path: 'job-application-form',
    name: 'studentJobApplicationForm',
    component: () => import('../views/student/JobApplicationForm.vue')
  },
  {
    path: 'internship-application-form',
    name: 'studentInternshipApplicationForm',
    component: () => import('../views/student/InternshipApplicationForm.vue')
  },
  {
    path: 'internship-confirmation-form',
    name: 'studentInternshipConfirmationForm',
    component: () => import('../views/student/InternshipConfirmationForm.vue')
  },
  {
    path: 'internship-registration',
    name: 'studentInternshipRegistration',
    component: () => import('../views/student/InternshipRegistration.vue')
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
    path: 'confirmation',
    name: 'studentConfirmation',
    component: () => import('../views/student/Confirmation.vue')
  },
  {
    path: 'internships',
    name: 'studentInternships',
    component: () => import('../views/student/Internships.vue')
  },
  {
    path: 'profile',
    name: 'studentProfile',
    component: () => import('../views/student/Profile.vue')
  },
  {
    path: 'test',
    name: 'studentTest',
    component: () => import('../views/student/Test.vue')
  },
  {
    path: 'internship-reflection/submit',
    name: 'studentInternshipReflectionSubmit',
    component: () => import('../views/student/Internships.vue')
  }
]

export default studentRoutes
