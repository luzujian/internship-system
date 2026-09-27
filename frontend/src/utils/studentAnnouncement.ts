import type { Announcement, AnnouncementAttachment } from '@/api/announcement'

export type StudentAnnouncement = Announcement
export type StudentAnnouncementAttachment = AnnouncementAttachment

const asRecord = (value: unknown): Record<string, unknown> => (
  value !== null && typeof value === 'object' ? value as Record<string, unknown> : {}
)

const firstText = (source: Record<string, unknown>, keys: string[]): string => {
  for (const key of keys) {
    const value = source[key]
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return ''
}

const normalizeRoleKey = (role: unknown): string => {
  const raw = String(role || '').trim()
  const aliases: Record<string, string> = {
    '管理员': 'ADMIN',
    '学院教师': 'COLLEGE',
    '系室教师': 'DEPARTMENT',
    '辅导员': 'COUNSELOR',
    '企业': 'COMPANY',
    '企业用户': 'COMPANY'
  }
  return aliases[raw] || raw.toUpperCase()
}

const announcementDate = (value: unknown): Date | null => {
  if (typeof value !== 'string' && typeof value !== 'number' && !(value instanceof Date)) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export const formatAnnouncementTimeParts = (time: unknown): { date: string; time: string; full: string } => {
  const date = announcementDate(time)
  if (!date) return { date: '', time: '', full: '' }

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const dateText = `${year}-${month}-${day}`
  const timeText = `${hours}:${minutes}`
  return { date: dateText, time: timeText, full: `${dateText} ${timeText}` }
}

export const formatAnnouncementTime = (time: unknown): string => formatAnnouncementTimeParts(time).full

export const getPreviewText = (content: unknown): string => {
  if (typeof content !== 'string' || !content) return ''
  const text = content
    .replace(/<br\s*\/?>/gi, ' ')
    .replace(/<[^>]*>/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  return text.length > 100 ? `${text.substring(0, 100)}...` : text
}

export const getPublisherRoleText = (role: unknown): string => {
  const raw = String(role || '').trim()
  const map: Record<string, string> = {
    ADMIN: '管理员',
    COLLEGE: '学院教师',
    DEPARTMENT: '系室教师',
    COUNSELOR: '辅导员',
    COMPANY: '企业',
    ENTERPRISE: '企业'
  }
  return map[normalizeRoleKey(raw)] || raw || '-'
}

export const getPublisherRoleTagType = (role: unknown): 'primary' | 'success' | 'warning' | 'danger' | 'info' => {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
    ADMIN: 'danger',
    COLLEGE: 'primary',
    DEPARTMENT: 'success',
    COUNSELOR: 'warning',
    COMPANY: 'info',
    ENTERPRISE: 'info'
  }
  return map[normalizeRoleKey(role)] || 'info'
}

export const getPublisherNameText = (announcement: unknown): string => {
  const source = asRecord(announcement)
  const name = firstText(source, [
    'publisher',
    'publisherName',
    'publisher_name',
    'publisherDisplayName',
    'creatorName',
    'createdByName'
  ])
  if (name) return name

  const roleText = getPublisherRoleText(
    firstText(source, ['publisherRole', 'publisher_role', 'publisherRoleText', 'role']) || undefined
  )
  return roleText === '管理员' ? '系统管理员' : '系统'
}

export const getPublisherRoleDisplay = (announcement: unknown): string => {
  const source = asRecord(announcement)
  const explicit = firstText(source, [
    'publisherRoleText',
    'publisherRoleName',
    'publisher_role_text',
    'roleName'
  ])
  return explicit || getPublisherRoleText(
    firstText(source, ['publisherRole', 'publisher_role', 'role']) || undefined
  )
}

const parseJson = (value: unknown): unknown => {
  if (typeof value !== 'string') return value
  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}

const toStringValues = (value: unknown): string[] => {
  const parsed = parseJson(value)
  if (Array.isArray(parsed)) return parsed.map(item => String(item)).filter(Boolean)
  if (parsed !== null && typeof parsed === 'object') {
    return Object.values(parsed as Record<string, unknown>)
      .flatMap(item => Array.isArray(item) ? item : [item])
      .map(item => String(item))
      .filter(Boolean)
  }
  return parsed === null || parsed === undefined || parsed === '' ? [] : [String(parsed)]
}

export const getTargetText = (targetType: unknown, targetValue: unknown): string => {
  if (!targetType) return '-'

  const typeMap: Record<string, string> = {
    ALL: '全体师生',
    STUDENT: '全体学生',
    TEACHER: '全体教师',
    COMPANY: '全体企业',
    ENTERPRISE: '全体企业',
    TEACHER_TYPE: '特定教师类别',
    MAJOR: '特定专业学生'
  }

  const parsedTypes = parseJson(targetType)
  const types = (Array.isArray(parsedTypes) ? parsedTypes : [parsedTypes])
    .map(item => String(item))
    .filter(Boolean)
  const typeTexts = types.map(type => typeMap[type] || type)
  const parsedTargetValue = parseJson(targetValue)

  if (types.includes('TEACHER_TYPE')) {
    const teacherTypeMap: Record<string, string> = {
      COLLEGE: '学院教师',
      DEPARTMENT: '系室教师',
      COUNSELOR: '辅导员'
    }
    const rawTeacherTypes = parsedTargetValue !== null && typeof parsedTargetValue === 'object' && !Array.isArray(parsedTargetValue)
      ? (parsedTargetValue as Record<string, unknown>).teacherTypes
      : parsedTargetValue
    const teacherTypes = toStringValues(rawTeacherTypes)
      .map(type => teacherTypeMap[type.toUpperCase()] || type)
    if (teacherTypes.length > 0) return `${typeTexts.join('、')}（${teacherTypes.join('、')}）`
  }

  if (types.includes('MAJOR')) {
    const rawMajorIds = parsedTargetValue !== null && typeof parsedTargetValue === 'object' && !Array.isArray(parsedTargetValue)
      ? (parsedTargetValue as Record<string, unknown>).majorIds
      : parsedTargetValue
    const majorIds = toStringValues(rawMajorIds)
    if (majorIds.length > 0) return `${typeTexts.join('、')}（${majorIds.join('、')}）`
  }

  return typeTexts.join('、')
}

export const parseAnnouncementAttachments = (attachments: unknown): StudentAnnouncementAttachment[] => {
  const parsed = parseJson(attachments)
  if (!Array.isArray(parsed)) return []
  return parsed
    .filter((item): item is StudentAnnouncementAttachment => {
      if (item === null || typeof item !== 'object') return false
      const name = (item as { name?: unknown }).name
      return typeof name === 'string' && Boolean(name.trim())
    })
    .map(item => ({ ...item, name: String(item.name).trim() }))
}

export const isAnnouncementUnread = (announcement: { isRead?: boolean }): boolean => announcement.isRead !== true
