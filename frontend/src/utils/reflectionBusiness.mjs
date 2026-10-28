// These retained routes belong to the retired reflection business, not current navigation.
const reflectionPaths = [
  '/student/internships', '/student/internship-reflection',
  '/teacher/evaluation', '/teacher/reflection-evaluation', '/teacher/grade-finalize',
  '/teacher/aisettings', '/teacher/ai-scoring-config', '/teacher/ai-keyword-config',
  '/teacher/scoring-rules', '/teacher/keyword-library',
  '/admin/keyword-library', '/admin/scoring-rule'
]

export function isReflectionRoute(path) {
  const pathname = String(path || '').split(/[?#]/, 1)[0]
  return reflectionPaths.some(base => pathname === base || pathname.startsWith(`${base}/`))
}

export function isReflectionPermission(permission) {
  const item = typeof permission === 'string' ? { permissionCode: permission } : permission || {}
  const code = String(item.permissionCode || item.permission_code || '')
  const label = String(item.permissionName || item.permission_name || item.module || '')
  return isReflectionRoute(item.path)
    || /^(?:(?:keyword|scoring):|view:reflection(?:$|:)|menu:(?:evaluation|teacherAiScoringConfig|ai-scoring-config|ai-keyword-config|grade-finalize|scoring-rules|keyword-library)$)/.test(code)
    || /实习心得|心得批阅|智慧评分|AI评分|评分规则|关键词库|AI关键词|AI分析配置|成绩评定/.test(label)
}

