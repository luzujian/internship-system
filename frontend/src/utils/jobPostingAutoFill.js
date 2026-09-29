export const JOB_POSTING_CONFIDENCE_THRESHOLD = 0.75

const isEmptyValue = (value) => {
  if (value === null || value === undefined) return true
  if (typeof value === 'string') return value.trim() === ''
  if (Array.isArray(value)) return value.length === 0
  return false
}

const isSameValue = (left, right) => {
  if (Array.isArray(left) || Array.isArray(right)) {
    return JSON.stringify(left) === JSON.stringify(right)
  }
  return String(left) === String(right)
}

/**
 * 决定单个 AI 识别字段是否可回填。缺失置信度一律按低可信处理，
 * 已有人工内容一律保留；只有仍等于上一次 AI 值的字段才允许再次更新。
 */
export const decideJobPostingAutoFill = ({ current, next, confidence, replaceable = false }) => {
  if (isEmptyValue(next)) return 'ignore'

  const score = Number(confidence)
  if (!Number.isFinite(score) || score < JOB_POSTING_CONFIDENCE_THRESHOLD) {
    return 'low-confidence'
  }

  if (isSameValue(current, next)) return 'same'
  if (isEmptyValue(current) || replaceable) return 'fill'
  return 'conflict'
}
