const applicationStatusText = {
  pending: '待审核',
  approved: '已通过',
  rejected: '已拒绝',
  withdrawn: '已撤回',
  hired: '已录用',
  // 面试环节结果在申请页归一为申请语义，面试细节看面试管理页
  interview_passed: '已通过',
  interview_failed: '已拒绝'
}

const interviewStatusText = {
  pending_interview: '待面试',
  interview_passed: '面试通过',
  interview_failed: '面试没通过'
}

const progressStatus = {
  pending: { text: '待处理', class: 'pending' },
  warning: { text: '待确认', class: 'warning' },
  success: { text: '已通过', class: 'success' },
  failed: { text: '未通过', class: 'failed' },
  submitted: { text: '已提交', class: 'warning' },
  scored: { text: '已评分', class: 'success' }
}

const progressRoutes = {
  interview: '/student/interviews',
  direct_hire: '/student/internship-confirmation-form',
  job_application: '/student/applications',
  internship_confirmation: '/student/internship-confirmation-form',
  reflection_submit: '/student/internship-reflection/submit',
  unit_change_application: '/student/internship-confirmation-form'
}

export function getApplicationStatusText(status, directHire = false) {
  if (directHire && status === 'interview_passed') {
    return '直接录取（免面试）'
  }
  return applicationStatusText[status] || status
}

export function getInterviewStatusText(status, directHire = false) {
  if (directHire && status === 'interview_passed') {
    return '直接录取（免面试）'
  }
  return interviewStatusText[status] || String(status)
}

export function getProgressStatus(status, eventType) {
  if (eventType === 'direct_hire') {
    return { text: '已录取', class: 'success' }
  }
  return progressStatus[status] || { text: status, class: 'pending' }
}

export function getProgressRoute(eventType) {
  return progressRoutes[eventType] || '/student/internships'
}
