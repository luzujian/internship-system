import request from '@/utils/request'

export type AppraisalSubmissionStatus = 'ALL' | 'SUBMITTED' | 'NOT_SUBMITTED'

export interface TeacherAppraisalClassOption {
  id: number
  name: string
}

export interface AppraisalCollectionItem {
  studentId: number
  studentNumber: string
  studentName: string
  classId: number | null
  className: string
  majorName: string
  submissionStatus: Exclude<AppraisalSubmissionStatus, 'ALL'>
  submittedAt: string | null
  fileCount: number
}

export interface AppraisalCollectionPage {
  rows: AppraisalCollectionItem[]
  total: number
  pages: number
  current: number
  pageSize: number
  responsibleStudentCount: number
  submittedCount: number
  notSubmittedCount: number
  classes: TeacherAppraisalClassOption[]
}

export interface AppraisalFileItem {
  id: number
  fileName: string
  fileType: string
  fileSize: number | null
  companyName: string | null
  uploadTime: string | null
}

export interface AppraisalStudentDetail {
  studentId: number
  studentNumber: string
  studentName: string
  className: string
  majorName: string
  submittedAt: string | null
  files: AppraisalFileItem[]
}

export interface AppraisalCollectionQuery {
  page?: number
  pageSize?: number
  keyword?: string
  classId?: number
  status?: AppraisalSubmissionStatus
}

const BASE_URL = '/teacher/internship-appraisals'

const blobRequest = async (url: string, config: Record<string, unknown>, fallbackMessage: string): Promise<Blob> => {
  try {
    return await request.get(url, {
      ...config,
      responseType: 'blob',
      skipErrorMessage: true
    } as any) as unknown as Blob
  } catch (error: any) {
    let message = error?.response?.data?.message || error?.response?.data?.msg || fallbackMessage
    const errorBody = error?.response?.data

    if (errorBody instanceof Blob) {
      try {
        const text = await errorBody.text()
        if (text) {
          try {
            const payload = JSON.parse(text)
            message = payload?.message || payload?.msg || fallbackMessage
          } catch {
            message = text.length <= 200 ? text : fallbackMessage
          }
        }
      } catch {
        message = fallbackMessage
      }
    }

    throw new Error(message)
  }
}

export const getAppraisalCollection = (params: AppraisalCollectionQuery) =>
  request.get(BASE_URL, { params })

export const getAppraisalStudentDetail = (studentId: number) =>
  request.get(`${BASE_URL}/students/${studentId}`)

export const previewAppraisalFile = (fileId: number) =>
  blobRequest(`${BASE_URL}/files/${fileId}/preview`, {
    timeout: 120000
  }, '文件预览失败，请下载后查看')

export const downloadAppraisalFile = (fileId: number) =>
  blobRequest(`${BASE_URL}/files/${fileId}/download`, {
    timeout: 120000
  }, '文件下载失败')

export const exportAppraisalFiles = (params: Omit<AppraisalCollectionQuery, 'page' | 'pageSize'>) =>
  blobRequest(`${BASE_URL}/export`, {
    params,
    timeout: 600000
  }, '鉴定表导出失败')
