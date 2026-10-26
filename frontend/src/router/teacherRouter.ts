import { agentPage } from '@/agent/pageForms'
import type { RouteRecordRaw } from 'vue-router'
import { currentUserHasRoutePermission } from '../utils/teacherPermissions'

const teacherRoutes: RouteRecordRaw[] = [
  {
    path: 'home',
    name: 'teacherHome',
    component: agentPage('views/teacher/Home.vue', () => import('../views/teacher/Home.vue')),
    meta: {
      title: '首页',
      requiresAuth: true
    }
  },

  {
    path: 'dashboard',
    name: 'teacherDashboard',
    component: agentPage('views/teacher/Dashboard.vue', () => import('../views/teacher/Dashboard.vue')),
    meta: {
      title: '实习状态看板',
      requiresAuth: true
    }
  },
  {
    path: 'student-tracking',
    name: 'teacherStudentTracking',
    component: agentPage('views/teacher/TeacherStudentManagement.vue', () => import('../views/teacher/TeacherStudentManagement.vue')),
    meta: {
      title: '学生状态监控',
      requiresAuth: true
    }
  },
  {
    path: 'approval',
    name: 'teacherApproval',
    component: agentPage('views/teacher/Approval.vue', () => import('../views/teacher/Approval.vue')),
    meta: {
      title: '审核管理',
      requiresAuth: true
    }
  },
  {
    path: 'evaluation',
    name: 'teacherEvaluation',
    component: agentPage('views/teacher/Evaluation.vue', () => import('../views/teacher/Evaluation.vue')),
    meta: {
      title: '智慧评分',
      requiresAuth: true
    }
  },
  {
    path: 'internship-appraisals',
    name: 'teacherInternshipAppraisals',
    component: agentPage('views/teacher/InternshipAppraisalCollection.vue', () => import('../views/teacher/InternshipAppraisalCollection.vue')),
    meta: {
      title: '实习材料收集',
      requiresAuth: true,
      roles: ['ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR']
    }
  },
  {
    path: 'announcements',
    name: 'teacherAnnouncements',
    component: agentPage('views/teacher/AnnouncementView.vue', () => import('../views/teacher/AnnouncementView.vue')),
    meta: {
      title: '公告管理',
      requiresAuth: true
    }
  },

  {
    path: 'company-list',
    name: 'teacherCompanyList',
    component: agentPage('views/teacher/CompanyList.vue', () => import('../views/teacher/CompanyList.vue')),
    meta: {
      title: '企业信息',
      requiresAuth: true
    }
  },

  {
    path: 'recruitment',
    name: 'teacherRecruitment',
    component: () => import('../views/teacher/RecruitmentDetails.vue'),
    meta: {
      title: '招聘详情',
      requiresAuth: true,
      roles: ['ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR']
    }
  },

  {
    path: 'resources',
    name: 'teacherResources',
    component: agentPage('views/teacher/Resources.vue', () => import('../views/teacher/Resources.vue')),
    meta: {
      title: '资源管理',
      requiresAuth: true
    }
  },
  {
    path: 'reports',
    name: 'teacherReports',
    component: agentPage('views/teacher/Reports.vue', () => import('../views/teacher/Reports.vue')),
    meta: {
      title: '统计报表',
      requiresAuth: true
    }
  },
  {
    path: 'aisettings',
    name: 'teacherAiSettings',
    component: agentPage('views/teacher/Settings.vue', () => import('../views/teacher/Settings.vue')),
    meta: {
      title: 'AI分析配置',
      requiresAuth: true
    }
  },
  {
    path: 'account-settings',
    name: 'teacherAccountSettings',
    component: agentPage('views/teacher/AccountSettings.vue', () => import('../views/teacher/AccountSettings.vue')),
    meta: {
      title: '账号设置',
      requiresAuth: true
    }
  },
  {
    path: 'timeline-config',
    name: 'teacherTimelineConfig',
    component: agentPage('views/teacher/Settings.vue', () => import('../views/teacher/Settings.vue')),
    meta: {
      title: '时间节点设置',
      requiresAuth: true
    }
  },
  {
    path: 'company-audit',
    name: 'teacherCompanyAudit',
    component: agentPage('views/teacher/CompanyAudit.vue', () => import('../views/teacher/CompanyAudit.vue')),
    meta: {
      title: '企业审核',
      requiresAuth: true
    }
  },
  {
    path: 'student-application-audit',
    name: 'teacherStudentApplicationAudit',
    component: agentPage('views/teacher/Approval.vue', () => import('../views/teacher/Approval.vue')),
    meta: {
      title: '学生申请审核',
      requiresAuth: true
    }
  },
  {
    path: 'key-timeline-config',
    name: 'teacherKeyTimelineConfig',
    component: agentPage('views/teacher/Settings.vue', () => import('../views/teacher/Settings.vue')),
    meta: {
      title: '关键时间节点设置',
      requiresAuth: true
    }
  },
  {
    path: 'ai-keyword-config',
    name: 'teacherAiKeywordConfig',
    component: agentPage('views/teacher/Settings.vue', () => import('../views/teacher/Settings.vue')),
    meta: {
      title: 'AI关键词配置',
      requiresAuth: true
    }
  },
  {
    path: 'grade-finalize',
    name: 'teacherGradeFinalize',
    component: agentPage('views/teacher/Evaluation.vue', () => import('../views/teacher/Evaluation.vue')),
    meta: {
      title: '成绩评定',
      requiresAuth: true
    }
  },
  {
    path: 'profile',
    name: 'teacherProfile',
    component: agentPage('views/teacher/Profile.vue', () => import('../views/teacher/Profile.vue')),
    meta: {
      title: '个人资料',
      requiresAuth: true
    }
  },
  {
    path: 'scoring-rules',
    name: 'teacherScoringRules',
    component: agentPage('views/teacher/CounselorScoringRuleManagement.vue', () => import('../views/teacher/CounselorScoringRuleManagement.vue')),
    meta: {
      title: '评分规则管理',
      requiresAuth: true
    }
  },
  {
    path: 'keyword-library',
    name: 'teacherKeywordLibrary',
    component: agentPage('views/teacher/CounselorKeywordLibraryManagement.vue', () => import('../views/teacher/CounselorKeywordLibraryManagement.vue')),
    meta: {
      title: '关键词库管理',
      requiresAuth: true
    }
  },
  {
    path: 'classes',
    name: 'teacherClasses',
    component: agentPage('views/teacher/TeacherClassManagement.vue', () => import('../views/teacher/TeacherClassManagement.vue')),
    meta: {
      title: '班级管理',
      requiresAuth: true
    }
  },
  {
    path: 'ai-scoring-config',
    name: 'teacherAiScoringConfig',
    component: agentPage('views/teacher/CounselorAISettings.vue', () => import('../views/teacher/CounselorAISettings.vue')),
    meta: {
      title: 'AI分析配置',
      requiresAuth: true
    }
  },

]

// 路由守卫：检查权限
teacherRoutes.forEach(route => {
  if (route.meta?.requiresAuth) {
    route.beforeEnter = async (to, from, next) => {
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
