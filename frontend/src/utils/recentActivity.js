const ACTIVITY_TIME_ZONE = 'Asia/Shanghai'

const categoryNames = new Set([
  'recruitment',
  'confirmation',
  'application',
  'reflection',
  'internship',
  'lifecycle'
])

const statusToneMap = {
  pending: 'pending',
  submitted: 'pending',
  warning: 'pending',
  success: 'success',
  approved: 'success',
  scored: 'success',
  failed: 'failed',
  rejected: 'failed',
  cancelled: 'withdrawn',
  withdrawn: 'withdrawn',
  recalled: 'withdrawn',
  info: 'info'
}

const actionDefinitions = {
  VIEW_APPLICATION: {
    path: '/student/applications',
    queryKey: 'applicationId',
    label: '查看申请'
  },
  VIEW_APPLICATION_LIST: {
    path: '/student/applications',
    label: '查看申请'
  },
  VIEW_INTERVIEW: {
    path: '/student/interviews',
    queryKey: 'interviewId',
    label: '查看面试'
  },
  START_CONFIRMATION: {
    path: '/student/internship-confirmation-form',
    queryKey: 'offerId',
    label: '填写确认表'
  },
  VIEW_CONFIRMATION: {
    path: '/student/internship-confirmation-form',
    queryKey: 'confirmationId',
    label: '查看确认记录'
  },
  VIEW_REFLECTION: {
    path: '/student/internships',
    queryKey: 'reflectionId',
    label: '查看心得'
  },
  VIEW_INTERNSHIP: {
    path: '/student/internships',
    queryKey: 'internshipId',
    label: '查看实习'
  },
  BROWSE_JOBS: {
    path: '/student/jobs',
    label: '浏览岗位'
  }
}

const datePartsFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: ACTIVITY_TIME_ZONE,
  year: 'numeric',
  month: 'numeric',
  day: 'numeric'
})

const timeFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: ACTIVITY_TIME_ZONE,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23'
})

const fullTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: ACTIVITY_TIME_ZONE,
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23'
})

function getDateParts(value) {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return null

  const parts = Object.fromEntries(
    datePartsFormatter
      .formatToParts(date)
      .filter((part) => part.type !== 'literal')
      .map((part) => [part.type, Number(part.value)])
  )
  return { year: parts.year, month: parts.month, day: parts.day }
}

function getDateKey(value) {
  const parts = getDateParts(value)
  if (!parts) return 'unknown'
  return `${parts.year}-${String(parts.month).padStart(2, '0')}-${String(parts.day).padStart(2, '0')}`
}

function previousDateKey(now) {
  const parts = getDateParts(now)
  if (!parts) return ''
  const shanghaiMidnight = new Date(
    Date.UTC(parts.year, parts.month - 1, parts.day) - (8 * 60 * 60 * 1000)
  )
  return getDateKey(new Date(shanghaiMidnight.getTime() - (24 * 60 * 60 * 1000)))
}

function getGroupLabel(value, now) {
  const parts = getDateParts(value)
  const nowParts = getDateParts(now)
  if (!parts || !nowParts) return '时间未知'

  const key = getDateKey(value)
  if (key === getDateKey(now)) return '今天'
  if (key === previousDateKey(now)) return '昨天'
  if (parts.year === nowParts.year) return `${parts.month}月${parts.day}日`
  return `${parts.year}年${parts.month}月${parts.day}日`
}

function toSafeQuery(params) {
  if (!params || typeof params !== 'object' || Array.isArray(params)) return {}
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => (
      typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean'
    ))
  )
}

export function getActivityAction(action) {
  if (!action || typeof action !== 'object') return null
  const definition = actionDefinitions[action.key]
  if (!definition) return null

  const query = {
    ...(definition.query || {}),
    ...toSafeQuery(action.params)
  }
  if (definition.queryKey && action.targetId !== null && action.targetId !== undefined && action.targetId !== '') {
    query[definition.queryKey] = String(action.targetId)
  }

  return {
    label: definition.label,
    to: {
      path: definition.path,
      ...(Object.keys(query).length ? { query } : {})
    }
  }
}

export function getStatusTone(status) {
  return statusToneMap[String(status || '').toLowerCase()] || 'info'
}

export function normalizeActivity(item) {
  const occurredAt = item?.occurredAt || item?.eventTime || item?.time || null
  const timestamp = occurredAt ? new Date(occurredAt).getTime() : Number.NaN
  const action = getActivityAction(item?.action)

  return {
    id: item?.id,
    eventType: item?.eventType || 'unknown',
    category: categoryNames.has(item?.category) ? item.category : 'unknown',
    title: item?.title || '实习动态更新',
    description: item?.description || '',
    status: item?.status || 'info',
    statusText: item?.statusText || '状态更新',
    statusTone: getStatusTone(item?.status),
    occurredAt,
    timestamp: Number.isNaN(timestamp) ? 0 : timestamp,
    relatedId: item?.relatedId ?? null,
    action,
    timeText: Number.isNaN(timestamp) ? '--:--' : timeFormatter.format(new Date(timestamp)),
    fullTimeText: Number.isNaN(timestamp) ? '时间未知' : fullTimeFormatter.format(new Date(timestamp))
  }
}

export function mergeActivities(existing, incoming) {
  const byId = new Map()
  ;[...(existing || []), ...(incoming || [])].forEach((item) => {
    const normalized = item?.statusTone ? item : normalizeActivity(item)
    const key = normalized.id === null || normalized.id === undefined
      ? `${normalized.eventType}:${normalized.occurredAt}:${normalized.title}`
      : String(normalized.id)
    byId.set(key, normalized)
  })

  return Array.from(byId.values()).sort((left, right) => {
    if (right.timestamp !== left.timestamp) return right.timestamp - left.timestamp
    return Number(right.id || 0) - Number(left.id || 0)
  })
}

export function groupActivitiesByDate(items, now = new Date()) {
  const groups = []
  const groupByKey = new Map()

  mergeActivities([], items).forEach((item) => {
    const key = getDateKey(item.occurredAt)
    let group = groupByKey.get(key)
    if (!group) {
      group = { key, label: getGroupLabel(item.occurredAt, now), items: [] }
      groupByKey.set(key, group)
      groups.push(group)
    }
    group.items.push(item)
  })

  return groups
}

export { ACTIVITY_TIME_ZONE }
