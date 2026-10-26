import request from '@/utils/request'

export type AgreementSource = 'CONFIRMATION' | 'SELF_PRACTICE'
export type AgreementRole = 'student' | 'teacher'

export interface AgreementFile {
  id?: number
  uploadId: number
  fileName: string
  fileType: string
  fileSize: number
  uploadTime?: string
}

export interface AgreementUploadFile extends AgreementFile {
  uid: string
  status: 'uploading' | 'success' | 'error'
  progress: number
  persisted?: boolean
  raw?: File
  error?: string
}

export interface AgreementRecord {
  sourceType: AgreementSource
  sourceId: number
  studentId: number
  studentNumber: string
  studentName: string
  classId: number | null
  className: string
  majorName: string
  companyName: string
  positionName: string
  businessStatus: string
  reviewTarget?: 'COMPANY' | 'COUNSELOR'
  submissionStatus: 'SUBMITTED' | 'NOT_SUBMITTED'
  version: number
  fileCount: number
  submittedAt: string | null
}

export interface AgreementVersion {
  id: number
  version: number
  companyName: string
  positionName: string
  submittedAt: string
  files: AgreementFile[]
}

export interface AgreementDetail {
  record: AgreementRecord
  versions: AgreementVersion[]
  editable: boolean
}

export interface AgreementQuery {
  page?: number
  pageSize?: number
  keyword?: string
  classId?: number
  sourceType?: AgreementSource
  status?: 'ALL' | 'SUBMITTED' | 'NOT_SUBMITTED'
  businessStatus?: string
}

export interface AgreementCollectionPage {
  rows: AgreementRecord[]
  total: number
  current: number
  pageSize: number
  responsibleRecordCount: number
  submittedCount: number
  notSubmittedCount: number
  classes: { id: number; name: string }[]
}

export const agreementBusinessStatusLabel = (record: AgreementRecord) => record.businessStatus === 'PENDING'
  ? record.reviewTarget === 'COMPANY' ? '待企业确认' : '待辅导员审核'
  : agreementBusinessStatusText[record.businessStatus] || record.businessStatus

export const agreementBusinessStatusText: Record<string, string> = {
  PENDING: '待处理', CONFIRMED: '已定单位待实习', IN_PROGRESS: '实习进行中', ENDED: '实习已结束',
  REPLACED: '已更换单位', INTERRUPTED: '实习已中断', REJECTED: '已拒绝', WITHDRAWN: '已撤回'
}

export const uploadAgreementFile = (file: File, signal: AbortSignal, progress: (percent: number) => void) => {
  const data = new FormData()
  data.append('file', file)
  return request.post('/student/internship-agreements/files', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
    signal,
    skipErrorMessage: true,
    onUploadProgress: (event: { loaded: number; total?: number }) => progress(event.total ? Math.min(99, Math.round(event.loaded * 100 / event.total)) : 0)
  } as any)
}

export const removeAgreementUpload = (uploadId: number) =>
  request.delete(`/student/internship-agreements/uploads/${uploadId}`, { skipErrorMessage: true } as any)

export const getAgreementDetail = (role: AgreementRole, sourceType: AgreementSource, sourceId: number) =>
  request.get(`/${role}/internship-agreements/records/${sourceType}/${sourceId}`)

export const submitAgreement = (sourceType: AgreementSource, sourceId: number, fileIds: number[], expectedVersion: number) =>
  request.post(`/student/internship-agreements/records/${sourceType}/${sourceId}/submit`, { fileIds, expectedVersion })

export const getAgreementCollection = (params: AgreementQuery) =>
  request.get('/teacher/internship-agreements', { params })

export const blobRequest = async (url: string, params?: AgreementQuery): Promise<Blob> => {
  try {
    return await request.get(url, { params, responseType: 'blob', timeout: params ? 600000 : 120000,
      skipErrorMessage: true } as any) as unknown as Blob
  } catch (error: any) {
    let message = error?.message || '文件读取失败'
    if (error?.response?.data instanceof Blob) {
      try {
        const payload = JSON.parse(await error.response.data.text())
        message = payload.message || payload.msg || message
      } catch { /* 非 JSON 错误沿用原错误提示。 */ }
    }
    throw new Error(message)
  }
}

export const readAgreementFile = (role: AgreementRole, fileId: number, preview = false) =>
  blobRequest(`/${role}/internship-agreements/files/${fileId}/${preview ? 'preview' : 'download'}`)

export const exportAgreementFiles = (params: AgreementQuery) =>
  blobRequest('/teacher/internship-agreements/export', params)

export const toAgreementUploadFiles = (files: AgreementFile[]): AgreementUploadFile[] =>
  files.map(file => ({ ...file, uid: `saved-${file.uploadId}`, status: 'success', progress: 100, persisted: true }))

export const saveAgreementBlob = (blob: Blob, name: string) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = name
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
