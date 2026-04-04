import type { RouteRecordRaw } from 'vue-router'
import { currentUserHasRoutePermission } from '../utils/teacherPermissions'

const teacherRoutes: RouteRecordRaw[] = [
  {
    path: 'home',
    name: 'teacherHome',
    component: () => import('../views/teacher/Home.vue'),
    meta: {
      title: '首页',
      requiresAuth: true
    }
  },

  {
    path: 'dashboard',
    name: 'teacherDashboard',
    component: () => import('../views/teacher/Dashboard.vue'),
    meta: {
      title: '实习状态看板',
      requiresAuth: true
    }
  },
  {
    path: 'student-tracking',
    name: 'teacherStudentTracking',
    component: () => import('../views/teacher/TeacherStudentManagement.vue'),
    meta: {
      title: '学生状态监控',
      requiresAuth: true
    }
  },
  {
    path: 'approval',
    name: 'teacherApproval',
    component: () => import('../views/teacher/Approval.vue'),
    meta: {
      title: '审核管理',
      requiresAuth: true
    }
  },
  {
    path: 'evaluation',
    name: 'teacherEvaluation',
    component: () => import('../views/teacher/Evaluation.vue'),
    meta: {
      title: '智慧评分',
      requiresAuth: true
    }
  },
  {
    path: 'announcements',
    name: 'teacherAnnouncements',
    component: () => import('../views/teacher/AnnouncementView.vue'),
    meta: {
      title: '公告管理',
      requiresAuth: true
    }
  },

  {
    path: 'company-list',
    name: 'teacherCompanyList',
    component: () => import('../views/teacher/CompanyList.vue'),
    meta: {
      title: '企业列表',
      requiresAuth: true
    }
  },

  {
    path: 'resources',
    name: 'teacherResources',
    component: () => import('../views/teacher/Resources.vue'),
    meta: {
      title: '资源管理',
      requiresAuth: true
    }
  },
  {
    path: 'reports',
    name: 'teacherReports',
    component: () => import('../views/teacher/Reports.vue'),
    meta: {
      title: '统计报表',
      requiresAuth: true
    }
  },
  {
    path: 'aisettings',
    name: 'teacherAiSettings',
    component: () => import('../views/teacher/Settings.vue'),
    meta: {
      title: 'AI分析配置',
      requiresAuth: true
    }
  },
  {
    path: 'account-settings',
    name: 'teacherAccountSettings',
    component: () => import('../views/teacher/AccountSettings.vue'),
    meta: {
      title: '账号设置',
      requiresAuth: true
    }
  },
  {
    path: 'timeline-config',
    name: 'teacherTimelineConfig',
    component: () => import('../views/teacher/Settings.vue'),
    meta: {
      title: '时间节点设置',
      requiresAuth: true
    }
  },
  {
    path: 'company-audit',
    name: 'teacherCompanyAudit',
    component: () => import('../views/teacher/CompanyAudit.vue'),
    meta: {
      title: '企业审核',
      requiresAuth: true
    }
  },
  {
    path: 'student-application-audit',
    name: 'teacherStudentApplicationAudit',
    component: () => import('../views/teacher/Approval.vue'),
    meta: {
      title: '学生申请审核',
      requiresAuth: true
    }
  },
  {
    path: 'key-timeline-config',
    name: 'teacherKeyTimelineConfig',
    component: () => import('../views/teacher/Settings.vue'),
    meta: {
      title: '关键时间节点设置',
      requiresAuth: true
    }
  },
  {
    path: 'ai-keyword-config',
    name: 'teacherAiKeywordConfig',
    component: () => import('../views/teacher/Settings.vue'),
    meta: {
      title: 'AI关键词配置',
      requiresAuth: true
    }
  },
  {
    path: 'grade-finalize',
    name: 'teacherGradeFinalize',
    component: () => import('../views/teacher/Evaluation.vue'),
    meta: {
      title: '成绩评定',
      requiresAuth: true
    }
  },
  {
    path: 'profile',
    name: 'teacherProfile',
    component: () => import('../views/teacher/Profile.vue'),
    meta: {
      title: '个人资料',
      requiresAuth: true
    }
  },
  {
    path: 'scoring-rules',
    name: 'teacherScoringRules',
    component: () => import('../views/teacher/CounselorScoringRuleManagement.vue'),
    meta: {
      title: '评分规则管理',
      requiresAuth: true
    }
  },
  {
    path: 'keyword-library',
    name: 'teacherKeywordLibrary',
    component: () => import('../views/teacher/CounselorKeywordLibraryManagement.vue'),
    meta: {
      title: '关键词库管理',
      requiresAuth: true
    }
  },
  {
    path: 'classes',
    name: 'teacherClasses',
    component: () => import('../views/teacher/TeacherClassManagement.vue'),
    meta: {
      title: '班级管理',
      requiresAuth: true
    }
  },
  {
    path: 'students',
    name: 'teacherStudents',
    component: () => import('../views/teacher/TeacherStudentManagement.vue'),
    meta: {
      title: '学生管理',
      requiresAuth: true
    }
  },
  {
    path: 'ai-scoring-config',
    name: 'teacherAiScoringConfig',
    component: () => import('../views/teacher/CounselorAISettings.vue'),
    meta: {
      title: 'AI分析配置',
      requiresAuth: true
    }
  },
]

// 路由守卫：检查权限
teacherRoutes.forEach(route => {
  if (route.meta?.requiresAuth) {
    route.beforeEnter = (to, from, next) => {
      const routeName = route.name as string
      if (currentUserHasRoutePermission(routeName)) {
        next()
      } else {
        // 没有权限，跳转到首页或显示无权限提示
        next({ name: 'teacherHome' })
      }
    }
  }
})

export default teacherRoutes
