/**
 * 学生个人资料完整性校验（岗位申请、自主实习审核提交、考研延迟申请共用）。
 * 必填字段：姓名、学号、电话、邮箱；全部完善后才允许进入下一步。
 * 与后端 com.internship.utils.StudentProfileCompleteness 的字段口径保持一致。
 */

export const REQUIRED_PROFILE_FIELDS = [
  { key: 'name', label: '姓名' },
  { key: 'studentId', label: '学号' },
  { key: 'phone', label: '电话' },
  { key: 'email', label: '邮箱' }
]

const isBlank = (value) => value === null || value === undefined || String(value).trim() === ''

/** 返回缺失字段的中文名列表（按 姓名、学号、电话、邮箱 顺序）。 */
export function missingProfileFields(profile) {
  if (!profile) return REQUIRED_PROFILE_FIELDS.map((field) => field.label)
  return REQUIRED_PROFILE_FIELDS
    .filter((field) => isBlank(profile[field.key]))
    .map((field) => field.label)
}

export function profileIncompleteMessage(missing, actionLabel = '提交申请') {
  return `个人资料尚未完善（缺少：${missing.join('、')}），请补全后再${actionLabel}`
}

/**
 * 从服务端读取当前学生资料并检查必填项。
 * 读取失败时 verified=false：前端不做拦截，由后端提交接口兜底校验。
 */
export async function checkStudentProfileReadiness() {
  try {
    const { default: request } = await import('./request')
    const response = await request.get('/student/profile')
    if (response && response.code === 200 && response.data) {
      return { verified: true, missing: missingProfileFields(response.data) }
    }
    return { verified: false, missing: [] }
  } catch {
    return { verified: false, missing: [] }
  }
}
