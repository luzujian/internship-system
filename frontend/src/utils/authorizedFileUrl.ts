const positiveRecordId = (value: unknown): string => {
  if (typeof value === 'number') {
    return Number.isSafeInteger(value) && value > 0 ? String(value) : ''
  }
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return ''
  const number = Number(value)
  return Number.isSafeInteger(number) ? value : ''
}

export const resourceDocumentByteUrl = (id: unknown): string => {
  const recordId = positiveRecordId(id)
  return recordId ? `/api/resource-documents/download/${recordId}` : ''
}

export const studentProfileByteUrl = (kind: 'resume' | 'certificate', id: unknown): string => {
  const recordId = positiveRecordId(id)
  return recordId && (kind === 'resume' || kind === 'certificate')
    ? `/api/student/profile/download/${kind}/${recordId}` : ''
}

export const studentApplicationMaterialByteUrl = (id: unknown, key: unknown, teacher = false): string => {
  const recordId = positiveRecordId(id)
  if (!recordId || typeof key !== 'string' || !key || key.length > 80 || /[\\/\x00-\x1f?#]/.test(key)) return ''
  return `/api/${teacher ? 'approval/student-applications' : 'student/applications'}/${recordId}/materials/${encodeURIComponent(key)}`
}

export const companyQualificationMaterialByteUrl = (
  id: unknown,
  kind: unknown,
  index: unknown = 0,
  admin = false
): string => {
  const recordId = positiveRecordId(id)
  if (!recordId || !['businessLicense', 'legalIdCard', 'plaquePhoto'].includes(String(kind))
      || !Number.isInteger(index) || (index !== 0 && !(kind === 'legalIdCard' && index === 1))) return ''
  return `/api/${admin ? 'admin/companies' : 'approval/company-qualifications'}/${recordId}/materials/${kind}/${index}`
}

export const announcementAttachmentByteUrl = (announcementId: unknown, fileName: unknown): string => {
  const recordId = positiveRecordId(announcementId)
  if (!recordId || typeof fileName !== 'string' || !fileName.trim()) return ''
  try {
    return `/api/announcements/download-attachment?announcementId=${recordId}&fileName=${encodeURIComponent(fileName)}`
  } catch {
    return ''
  }
}

// Only record-bound application endpoints may supply preview bytes. Original OSS keys
// remain metadata and must never become a request URL or a fallback URL.
export const authorizedFileByteUrl = (value: unknown): string => {
  if (typeof value !== 'string') return ''
  const resourceId = /^\/api\/resource-documents\/download\/([1-9]\d*)$/.exec(value)?.[1]
  if (resourceId) return resourceDocumentByteUrl(resourceId)
  const studentFile = /^\/api\/student\/profile\/download\/(resume|certificate)\/([1-9]\d*)$/.exec(value)
  if (studentFile) return studentProfileByteUrl(studentFile[1] as 'resume' | 'certificate', studentFile[2])

  const application = /^\/api\/(student\/applications|approval\/student-applications)\/([1-9]\d*)\/materials\/([^/?#]+)$/.exec(value)
  if (application) {
    try {
      const canonical = studentApplicationMaterialByteUrl(application[2], decodeURIComponent(application[3]),
        application[1] === 'approval/student-applications')
      return canonical === value ? canonical : ''
    } catch { return '' }
  }
  const company = /^\/api\/(admin\/companies|approval\/company-qualifications)\/([1-9]\d*)\/materials\/(businessLicense|legalIdCard|plaquePhoto)\/(0|1)$/.exec(value)
  if (company) {
    const canonical = companyQualificationMaterialByteUrl(company[2], company[3], Number(company[4]),
      company[1] === 'admin/companies')
    return canonical === value ? canonical : ''
  }

  if (!value.startsWith('/api/announcements/download-attachment?') || value.includes('#')) return ''
  const query = value.slice(value.indexOf('?') + 1)
  const params = new URLSearchParams(query)
  if (Array.from(params.keys()).length !== 2
      || !params.has('announcementId') || !params.has('fileName')) return ''
  const canonical = announcementAttachmentByteUrl(params.get('announcementId'), params.get('fileName'))
  return canonical === value ? canonical : ''
}
