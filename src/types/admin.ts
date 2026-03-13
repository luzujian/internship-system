// 管理员端通用类型定义

// 通用分页参数
export interface PageParams {
  pageNum: number
  pageSize: number
  [key: string]: unknown
}

// 通用分页响应
export interface PageResult<T> {
  rows: T[]
  total: number
  pageNum: number
  pageSize: number
}

// 通用 API 响应
export interface ApiResponse<T = unknown> {
  code: number
  data: T
  message: string
}

// 性别枚举
export enum Gender {
  MALE = 1,
  FEMALE = 0
}

// 用户状态枚举
export enum UserStatus {
  DISABLED = 0,
  ENABLED = 1
}

// 学生用户
export interface StudentUser {
  id: string
  studentId: string
  name: string
  gender: number
  grade: string
  majorId: string
  majorName?: string
  classId: string
  className?: string
  phone?: string
  email?: string
  status?: number
  createTime?: string
  updateTime?: string
}

// 学生用户搜索表单
export interface StudentUserSearch extends BaseSearchForm {
  studentId?: string
  name?: string
  grade?: string
  majorId?: string
  classId?: string
}

// 教师用户
export interface TeacherUser {
  id: string
  name: string
  gender: number
  departmentId: string
  departmentName?: string
  title?: string
  phone?: string
  email?: string
  status?: number
  createTime?: string
  updateTime?: string
}

// 教师用户搜索表单
export interface TeacherUserSearch extends BaseSearchForm {
  name?: string
  departmentId?: string
  title?: string
}

// 管理员用户
export interface AdminUser {
  id: string
  username: string
  name: string
  phone?: string
  status?: number
  createTime?: string
  updateTime?: string
}

// 管理员用户搜索表单
export interface AdminUserSearch extends BaseSearchForm {
  username?: string
  name?: string
}

// 企业用户
export interface CompanyUser {
  id: string
  companyName: string
  contactPerson: string
  contactPhone: string
  contactEmail: string
  address: string
  introduction?: string
  companyTag?: string
  status: number
  createTime?: string
  updateTime?: string
}

// 企业用户搜索表单
export interface CompanyUserSearch extends BaseSearchForm {
  companyName?: string
  contactPerson?: string
}

// 部门
export interface Department {
  id: string
  name: string
  createTime?: string
  updateTime?: string
}

// 专业
export interface Major {
  id: string
  name: string
  departmentId: string
  departmentName?: string
  createTime?: string
  updateTime?: string
}

// 班级
export interface Class {
  id: string
  name: string
  majorId: string
  majorName?: string
  grade: string
  createTime?: string
  updateTime?: string
}

// 岗位
export interface Position {
  id: string
  name: string
  categoryId?: string
  categoryName?: string
  createTime?: string
  updateTime?: string
}

// 岗位类别
export interface PositionCategory {
  id: string
  name: string
  createTime?: string
  updateTime?: string
}

// 实习单位
export interface InternshipUnit {
  id: string
  name: string
  type?: string
  address?: string
  createTime?: string
  updateTime?: string
}

// 公告
export interface Announcement {
  id: string
  title: string
  content: string
  publisherId?: string
  publisherName?: string
  publishTime?: string
  createTime?: string
  updateTime?: string
}

// 基础搜索表单
export interface BaseSearchForm {
  keyword?: string
  status?: number | null
  [key: string]: unknown
}

// 表单对话框通用类型
export interface DialogState {
  visible: boolean
  title: string
  loading: boolean
  mode: 'create' | 'edit' | 'view'
}

// 分页状态通用类型
export interface PaginationState {
  currentPage: number
  pageSize: number
  total: number
}

// 日志
export interface OperationLog {
  id: string
  module: string
  operation: string
  method: string
  operator: string
  ip: string
  location?: string
  status: number
  errorMessage?: string
  duration: number
  createTime: string
}

// 反馈
export interface Feedback {
  id: string
  userId: string
  userName?: string
  userType?: number
  type: number
  title: string
  content: string
  images?: string
  status: number
  reply?: string
  replyTime?: string
  createTime: string
  updateTime: string
}

// 招聘信息
export interface Recruitment {
  id: string
  companyId: string
  companyName: string
  positionId: string
  positionName: string
  categoryId?: string
  categoryName?: string
  majorRequirements?: string
  educationRequirements?: string
  recruitmentNumber: number
  salary?: string
  benefits?: string
  jobDescription?: string
  requirements?: string
  address?: string
  contactPerson?: string
  contactPhone?: string
  status: number
  publishTime?: string
  deadline?: string
  createTime: string
  updateTime: string
}

// 实习确认申请
export interface InternshipConfirmApplication {
  id: string
  studentId: string
  studentName: string
  companyId: string
  companyName: string
  positionId: string
  positionName: string
  teacherId: string
  teacherName: string
  status: number
  applyTime: string
  confirmTime?: string
  rejectReason?: string
  createTime: string
  updateTime: string
}

// 评分规则
export interface ScoringRule {
  id: string
  ruleName: string
  ruleType: number
  score: number
  description?: string
  createTime?: string
  updateTime?: string
}

// 资源文档
export interface ResourceDocument {
  id: string
  title: string
  description?: string
  category: number
  url: string
  uploadUserId: string
  uploadUserName?: string
  downloadCount: number
  status: number
  createTime: string
  updateTime: string
}

// AI 模型
export interface AIModel {
  id: string
  name: string
  provider: string
  endpoint: string
  apiKey?: string
  maxTokens: number
  temperature: number
  status: number
  createTime?: string
  updateTime?: string
}

// 关键词库
export interface Keyword {
  id: string
  word: string
  type: number
  category?: string
  status: number
  createTime?: string
  updateTime?: string
}

// 权限
export interface Permission {
  id: string
  name: string
  code: string
  type: number
  parentId?: string
  path?: string
  method?: string
  description?: string
  createTime?: string
  updateTime?: string
}

// 用户类型
export type UserType = 'admin' | 'teacher' | 'student' | 'company'

// 表格列格式化函数类型
export type ColumnFormatter = (row: unknown, column: unknown, cellValue: unknown) => string

// 选择项类型
export interface SelectOption {
  value: string | number
  label: string
  disabled?: boolean
}
