/**
 * 全局类型定义
 */

// API 响应基础结构
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

// 分页响应结构
export interface PageResponse<T = any> {
  rows: T[]
  total: number
}

// 分页请求参数
export interface PageParams {
  pageNum?: number
  pageSize?: number
  page?: number
}

// 通用状态
export type Status = 0 | 1

// 性别类型
export type Gender = 1 | 2 // 1=男，2=女

// 用户角色
export type UserRole = 'ROLE_ADMIN' | 'ROLE_TEACHER' | 'ROLE_STUDENT' | 'ROLE_COMPANY'

// 基础用户信息
export interface BaseUser {
  id: number
  username: string
  name: string
  phone?: string
  role?: UserRole
  status?: Status
  createTime?: string
  updateTime?: string
}

// 文件上传响应
export interface UploadResponse {
  url: string
  filename: string
}

// ==================== PDF.js 类型定义 ====================
// 扩展 Window 接口以支持 PDF.js
declare global {
  interface Window {
    pdfjsLib?: PdfJsLib
  }
}

// PDF.js 库接口
export interface PdfJsLib {
  GlobalWorkerOptions: {
    workerSrc: string
  }
  getDocument: (url: string) => PdfDocumentTask
}

// PDF 文档任务
export interface PdfDocumentTask {
  promise: Promise<PdfDocument>
}

// PDF 文档
export interface PdfDocument {
  numPages: number
  getPage: (pageNum: number) => Promise<PdfPage>
}

// PDF 页面
export interface PdfPage {
  getViewport: (options: { scale: number }) => PdfViewport
  render: (context: { canvasContext: CanvasRenderingContext2D; viewport: PdfViewport }) => PdfRenderTask
}

// PDF 视口
export interface PdfViewport {
  width: number
  height: number
}

// PDF 渲染任务
export interface PdfRenderTask {
  promise: Promise<void>
}

// ==================== 通用组件 Props 类型 ====================
// 文件预览对话框 Props
export interface FilePreviewDialogProps {
  modelValue: boolean
  fileUrl: string
  fileName: string
}

// ==================== API 响应辅助类型 ====================
// 学生信息
export interface StudentUser {
  id: number
  studentId: string
  name: string
  status: number
  majorName?: string
  departmentName?: string
  className?: string
  grade?: string
}

// 资源文档
export interface ResourceDocument {
  id: number
  title: string
  name?: string
  fileType?: string
  type?: string
  uploadTime: string
  uploaderName: string
  description?: string
}
