import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'

type MessageHandler = (data: any) => void
type ConnectionHandler = () => void

// 岗位更新消息类型
interface PositionUpdateData {
  positionId: number
  positionName: string
  remainingQuota: number
  plannedRecruit: number
  recruitedCount: number
  status: string
  companyId?: number
}

// 岗位删除消息类型
interface PositionDeleteData {
  positionId: number
}

// 面试创建消息类型
interface InterviewCreateData {
  id: number
  positionId: number
  positionName: string
  companyId: number
  companyName: string
  interviewTime: string
  interviewLocation: string
  interviewMethod: string
  status: string
}

// 面试状态更新消息类型
interface InterviewStatusUpdateData {
  interviewId: number
  status: string
  statusText: string
}

// AI分析结果消息类型
interface AIAnalysisResultData {
  reflectionId: number
  success: boolean
  isNotReflection: boolean
  message: string
}

// 学生申请状态更新消息类型
export interface ApplicationStatusUpdateData {
  studentId: number
  studentName: string
  positionName: string
  companyName: string
  status: string
  statusText: string
}

// 企业待办更新消息类型
export interface CompanyTodoUpdateData {
  pendingApplications: number
  pendingConfirmations: number
}

// 实习确认结果更新消息类型
export interface ConfirmationResultUpdateData {
  confirmationStatus: number  // 1=已确认, 2=已拒绝
  companyName: string
  positionName: string
}

// 辅导员待评分数据更新消息类型
export interface TeacherTodoUpdateData {
  pendingEvaluations: number
}

// 成绩发布更新消息类型
export interface GradePublishedData {
  message: string
}

// 新申请消息类型
interface NewApplicationData {
  studentName: string
  positionName: string
}

// 岗位更新监听器列表
const positionUpdateListeners: Array<(data: PositionUpdateData) => void> = []
const positionDeleteListeners: Array<(data: PositionDeleteData) => void> = []
const interviewCreateListeners: Array<(data: InterviewCreateData) => void> = []
const interviewStatusUpdateListeners: Array<(data: InterviewStatusUpdateData) => void> = []
const aiAnalysisResultListeners: Array<(data: AIAnalysisResultData) => void> = []
const companyTodoUpdateListeners: Array<(data: CompanyTodoUpdateData) => void> = []
const newApplicationListeners: Array<(data: NewApplicationData) => void> = []
const applicationStatusUpdateListeners: Array<(data: ApplicationStatusUpdateData) => void> = []
const confirmationResultUpdateListeners: Array<(data: ConfirmationResultUpdateData) => void> = []
const teacherTodoUpdateListeners: Array<(data: TeacherTodoUpdateData) => void> = []
const gradePublishedListeners: Array<(data: GradePublishedData) => void> = []
type ErrorHandler = (error: Event) => void

interface WebSocketOptions {
  url: string
  token: string
  onMessage?: MessageHandler
  onConnected?: ConnectionHandler
  onDisconnected?: ConnectionHandler
  onError?: ErrorHandler
  reconnect?: boolean
  reconnectInterval?: number
  maxReconnectAttempts?: number
  heartbeatInterval?: number
}

class WebSocketClient {
  private ws: WebSocket | null = null
  private options: WebSocketOptions
  private reconnectAttempts = 0
  private heartbeatTimer: number | null = null
  private isManualClose = false
  private reconnectTimer: number | null = null

  constructor(options: WebSocketOptions) {
    this.options = {
      reconnect: true,
      reconnectInterval: 5000,
      maxReconnectAttempts: 10,
      heartbeatInterval: 30000,
      ...options
    }
  }

  connect(): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      logger.log('[WebSocket] 连接已存在')
      return
    }

    // 如果正在连接中，先等待连接完成
    if (this.ws && this.ws.readyState === WebSocket.CONNECTING) {
      logger.log('[WebSocket] 连接正在建立中，等待完成')
      return
    }

    this.isManualClose = false

    try {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      const host = window.location.host
      const fullUrl = `${protocol}//${host}${this.options.url}?token=${encodeURIComponent(this.options.token)}`
      
      logger.log('[WebSocket] 正在连接:', `${protocol}//${host}${this.options.url}?token=***`)

      this.ws = new WebSocket(fullUrl)

      this.ws.onopen = this.handleOpen.bind(this)
      this.ws.onmessage = this.handleMessage.bind(this)
      this.ws.onclose = this.handleClose.bind(this)
      this.ws.onerror = this.handleError.bind(this)
    } catch (error) {
      logger.error('[WebSocket] 创建连接失败:', error)
      this.scheduleReconnect()
    }
  }

  private handleOpen(): void {
    logger.log('[WebSocket] 连接成功')
    this.reconnectAttempts = 0
    this.startHeartbeat()

    if (this.options.onConnected) {
      this.options.onConnected()
    }
  }

  private handleMessage(event: MessageEvent): void {
    try {
      const data = JSON.parse(event.data)
      logger.debug('[WebSocket] 收到消息:', data)

      if (data.type === 'pong') {
        return
      }

      if (data.type === 'connected') {
        logger.log('[WebSocket] 服务器确认连接，用户ID:', data.userId)
        return
      }

      if (this.options.onMessage) {
        this.options.onMessage(data)
      }
    } catch (error) {
      logger.error('[WebSocket] 解析消息失败:', error)
    }
  }

  private handleClose(event: CloseEvent): void {
    logger.log('[WebSocket] 连接关闭，代码:', event.code, '原因:', event.reason)
    this.stopHeartbeat()

    if (this.options.onDisconnected) {
      this.options.onDisconnected()
    }

    if (!this.isManualClose && this.options.reconnect) {
      this.scheduleReconnect()
    }
  }

  private handleError(error: Event): void {
    logger.error('[WebSocket] 连接错误:', error)
    logger.error('[WebSocket] 连接URL:', this.ws?.url)
    logger.error('[WebSocket] 就绪状态:', this.ws?.readyState)

    if (this.options.onError) {
      this.options.onError(error)
    }
  }

  private scheduleReconnect(): void {
    if (this.reconnectAttempts >= (this.options.maxReconnectAttempts || 10)) {
      logger.error('[WebSocket] 达到最大重连次数，停止重连')
      ElMessage.warning('实时通知连接失败，请刷新页面重试')
      return
    }

    this.reconnectAttempts++
    const delay = this.options.reconnectInterval || 5000

    logger.log(`[WebSocket] 将在 ${delay}ms 后进行第 ${this.reconnectAttempts} 次重连`)

    this.reconnectTimer = window.setTimeout(() => {
      logger.log('[WebSocket] 开始重连...')
      this.connect()
    }, delay)
  }

  private startHeartbeat(): void {
    this.stopHeartbeat()

    this.heartbeatTimer = window.setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.send({ type: 'ping' })
      }
    }, this.options.heartbeatInterval || 30000)
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  send(data: object): boolean {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      logger.warn('[WebSocket] 连接未打开，无法发送消息')
      return false
    }

    try {
      this.ws.send(JSON.stringify(data))
      return true
    } catch (error) {
      logger.error('[WebSocket] 发送消息失败:', error)
      return false
    }
  }

  disconnect(): void {
    logger.log('[WebSocket] 主动断开连接')
    this.isManualClose = true
    this.stopHeartbeat()

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }

    if (this.ws) {
      // 保存当前 WebSocket 引用
      const oldWs = this.ws
      this.ws = null
      // 延迟关闭，让 onclose 处理器完成后再真正关闭
      setTimeout(() => {
        if (oldWs) {
          oldWs.close(1000, '用户主动断开')
        }
      }, 100)
    }
  }

  isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN
  }

  updateToken(token: string): void {
    this.options.token = token
  }
}

let announcementWebSocket: WebSocketClient | null = null

export function initAnnouncementWebSocket(
  token: string,
  onMessage: (data: any) => void
): WebSocketClient {
  if (announcementWebSocket) {
    announcementWebSocket.disconnect()
  }

  announcementWebSocket = new WebSocketClient({
    url: '/ws/announcement',
    token,
    onMessage: (data) => {
      logger.log('[WebSocket] 收到消息:', data)

      if (data.type === 'new_announcement' && data.data) {
        logger.log('[WebSocket] 收到新公告:', data.data)
        onMessage(data)
      } else if (data.type === 'new_feedback' && data.data) {
        logger.log('[WebSocket] 收到新问题反馈:', data.data)
        onMessage(data)
      } else if (data.type === 'application_status_update' && data.data) {
        console.log('[WebSocket] 收到申请状态更新, data:', JSON.stringify(data.data))
        applicationStatusUpdateListeners.forEach(callback => {
          try {
            callback(data.data as ApplicationStatusUpdateData)
          } catch (e) {
            console.error('[WebSocket] 申请状态更新监听器执行失败:', e)
          }
        })
        onMessage(data)
      } else if (data.type === 'position_update' && data.data) {
        // 【新增】触发岗位更新监听器
        logger.log('[WebSocket] 收到岗位数据更新:', data.data)
        positionUpdateListeners.forEach(callback => {
          try {
            callback(data.data as PositionUpdateData)
          } catch (e) {
            logger.error('[WebSocket] 岗位更新监听器执行失败:', e)
          }
        })
        onMessage(data)
      } else if (data.type === 'position_delete' && data.data) {
        // 【新增】触发岗位删除监听器
        logger.log('[WebSocket] 收到岗位删除:', data.data)
        positionDeleteListeners.forEach(callback => {
          try {
            callback(data.data as PositionDeleteData)
          } catch (e) {
            logger.error('[WebSocket] 岗位删除监听器执行失败:', e)
          }
        })
        onMessage(data)
      } else if (data.type === 'interview_create' && data.data) {
        logger.log('[WebSocket] 收到面试卡片新增:', data.data)
        interviewCreateListeners.forEach(callback => {
          try {
            callback(data.data as InterviewCreateData)
          } catch (e) {
            logger.error('[WebSocket] 面试创建监听器执行失败:', e)
          }
        })
        onMessage(data)
      } else if (data.type === 'interview_status_update' && data.data) {
        logger.log('[WebSocket] 收到面试状态更新:', data.data)
        interviewStatusUpdateListeners.forEach(callback => {
          try {
            callback(data.data as InterviewStatusUpdateData)
          } catch (e) {
            logger.error('[WebSocket] 面试状态更新监听器执行失败:', e)
          }
        })
        onMessage(data)
      } else if (data.type === 'ai_analysis_result' && data.data) {
        logger.log('[WebSocket] 收到AI分析结果:', data.data)
        aiAnalysisResultListeners.forEach(callback => {
          try {
            callback(data.data as AIAnalysisResultData)
          } catch (e) {
            logger.error('[WebSocket] AI分析结果监听器执行失败:', e)
          }
        })
        onMessage(data)
      } else if (data.type === 'company_todo_update' && data.data) {
        console.log('[WebSocket] 收到消息类型:', data.type, '数据:', JSON.stringify(data.data))
        companyTodoUpdateListeners.forEach(callback => {
          try {
            callback(data.data as CompanyTodoUpdateData)
          } catch (e) {
            console.error('[WebSocket] 企业待办更新监听器执行失败:', e)
          }
        })
        onMessage(data)
      } else if (data.type === 'new_application' && data.data) {
        logger.log('[WebSocket] 收到新简历申请推送:', data.data)
        newApplicationListeners.forEach(callback => {
          try {
            callback(data.data as NewApplicationData)
          } catch (e) {
            logger.error('[WebSocket] 新简历申请监听器执行失败:', e)
          }
        })
        onMessage(data)
      } else if (data.type === 'confirmation_result_update' && data.data) {
        console.log('[WebSocket] 收到实习确认结果更新:', data.data)
        confirmationResultUpdateListeners.forEach(callback => {
          try {
            callback(data.data as ConfirmationResultUpdateData)
          } catch (e) {
            console.error('[WebSocket] 实习确认结果更新监听器执行失败:', e)
          }
        })
        onMessage(data)
      } else if (data.type === 'teacher_todo_update' && data.data) {
        console.log('[WebSocket] 收到辅导员待评分数据更新:', data.data)
        teacherTodoUpdateListeners.forEach(callback => {
          try {
            callback(data.data as TeacherTodoUpdateData)
          } catch (e) {
            console.error('[WebSocket] 辅导员待评分数据更新监听器执行失败:', e)
          }
        })
        onMessage(data)
      } else if (data.type === 'grade_published' && data.data) {
        console.log('[WebSocket] 收到成绩发布通知:', data.data)
        gradePublishedListeners.forEach(callback => {
          try {
            callback(data.data as GradePublishedData)
          } catch (e) {
            console.error('[WebSocket] 成绩发布通知监听器执行失败:', e)
          }
        })
        onMessage(data)
      } else {
        onMessage(data)
      }
    },
    onConnected: () => {
      logger.log('[WebSocket] 通知连接成功')
    },
    onDisconnected: () => {
      logger.log('[WebSocket] 通知连接断开')
    },
    onError: (error) => {
      logger.error('[WebSocket] 通知连接错误:', error)
    }
  })

  announcementWebSocket.connect()
  return announcementWebSocket
}

export function disconnectAnnouncementWebSocket(): void {
  if (announcementWebSocket) {
    announcementWebSocket.disconnect()
    announcementWebSocket = null
  }
}

export function getAnnouncementWebSocket(): WebSocketClient | null {
  return announcementWebSocket
}

// 注册岗位更新监听器
export function onPositionUpdate(callback: (data: PositionUpdateData) => void): void {
  if (!positionUpdateListeners.includes(callback)) {
    positionUpdateListeners.push(callback)
  }
}

// 注册岗位删除监听器
export function onPositionDelete(callback: (data: PositionDeleteData) => void): void {
  if (!positionDeleteListeners.includes(callback)) {
    positionDeleteListeners.push(callback)
  }
}

// 移除岗位更新监听器
export function offPositionUpdate(callback: (data: PositionUpdateData) => void): void {
  const index = positionUpdateListeners.indexOf(callback)
  if (index > -1) {
    positionUpdateListeners.splice(index, 1)
  }
}

// 移除岗位删除监听器
export function offPositionDelete(callback: (data: PositionDeleteData) => void): void {
  const index = positionDeleteListeners.indexOf(callback)
  if (index > -1) {
    positionDeleteListeners.splice(index, 1)
  }
}

// 注册面试创建监听器
export function onInterviewCreate(callback: (data: InterviewCreateData) => void): void {
  if (!interviewCreateListeners.includes(callback)) {
    interviewCreateListeners.push(callback)
  }
}

// 注册面试状态更新监听器
export function onInterviewStatusUpdate(callback: (data: InterviewStatusUpdateData) => void): void {
  if (!interviewStatusUpdateListeners.includes(callback)) {
    interviewStatusUpdateListeners.push(callback)
  }
}

// 移除面试创建监听器
export function offInterviewCreate(callback: (data: InterviewCreateData) => void): void {
  const index = interviewCreateListeners.indexOf(callback)
  if (index > -1) {
    interviewCreateListeners.splice(index, 1)
  }
}

// 移除面试状态更新监听器
export function offInterviewStatusUpdate(callback: (data: InterviewStatusUpdateData) => void): void {
  const index = interviewStatusUpdateListeners.indexOf(callback)
  if (index > -1) {
    interviewStatusUpdateListeners.splice(index, 1)
  }
}

// 注册AI分析结果监听器
export function onAIAnalysisResult(callback: (data: AIAnalysisResultData) => void): void {
  if (!aiAnalysisResultListeners.includes(callback)) {
    aiAnalysisResultListeners.push(callback)
  }
}

// 移除AI分析结果监听器
export function offAIAnalysisResult(callback: (data: AIAnalysisResultData) => void): void {
  const index = aiAnalysisResultListeners.indexOf(callback)
  if (index > -1) {
    aiAnalysisResultListeners.splice(index, 1)
  }
}

// 注册企业待办更新监听器
export function onCompanyTodoUpdate(callback: (data: CompanyTodoUpdateData) => void): void {
  if (!companyTodoUpdateListeners.includes(callback)) {
    companyTodoUpdateListeners.push(callback)
  }
}

// 移除企业待办更新监听器
export function offCompanyTodoUpdate(callback: (data: CompanyTodoUpdateData) => void): void {
  const index = companyTodoUpdateListeners.indexOf(callback)
  if (index > -1) {
    companyTodoUpdateListeners.splice(index, 1)
  }
}

// 注册新申请监听器
export function onNewApplication(callback: (data: NewApplicationData) => void): void {
  if (!newApplicationListeners.includes(callback)) {
    newApplicationListeners.push(callback)
  }
}

// 移除新申请监听器
export function offNewApplication(callback: (data: NewApplicationData) => void): void {
  const index = newApplicationListeners.indexOf(callback)
  if (index > -1) {
    newApplicationListeners.splice(index, 1)
  }
}

// 注册申请状态更新监听器
export function onApplicationStatusUpdate(callback: (data: ApplicationStatusUpdateData) => void): void {
  if (!applicationStatusUpdateListeners.includes(callback)) {
    applicationStatusUpdateListeners.push(callback)
  }
}

// 移除申请状态更新监听器
export function offApplicationStatusUpdate(callback: (data: ApplicationStatusUpdateData) => void): void {
  const index = applicationStatusUpdateListeners.indexOf(callback)
  if (index > -1) {
    applicationStatusUpdateListeners.splice(index, 1)
  }
}

// 注册实习确认结果更新监听器
export function onConfirmationResultUpdate(callback: (data: ConfirmationResultUpdateData) => void): void {
  if (!confirmationResultUpdateListeners.includes(callback)) {
    confirmationResultUpdateListeners.push(callback)
  }
}

// 移除实习确认结果更新监听器
export function offConfirmationResultUpdate(callback: (data: ConfirmationResultUpdateData) => void): void {
  const index = confirmationResultUpdateListeners.indexOf(callback)
  if (index > -1) {
    confirmationResultUpdateListeners.splice(index, 1)
  }
}

// 注册辅导员待评分数据更新监听器
export function onTeacherTodoUpdate(callback: (data: TeacherTodoUpdateData) => void): void {
  if (!teacherTodoUpdateListeners.includes(callback)) {
    teacherTodoUpdateListeners.push(callback)
  }
}

// 移除辅导员待评分数据更新监听器
export function offTeacherTodoUpdate(callback: (data: TeacherTodoUpdateData) => void): void {
  const index = teacherTodoUpdateListeners.indexOf(callback)
  if (index > -1) {
    teacherTodoUpdateListeners.splice(index, 1)
  }
}

// 注册成绩发布更新监听器
export function onGradePublished(callback: (data: GradePublishedData) => void): void {
  if (!gradePublishedListeners.includes(callback)) {
    gradePublishedListeners.push(callback)
  }
}

// 移除成绩发布更新监听器
export function offGradePublished(callback: (data: GradePublishedData) => void): void {
  const index = gradePublishedListeners.indexOf(callback)
  if (index > -1) {
    gradePublishedListeners.splice(index, 1)
  }
}

export { WebSocketClient }
export default WebSocketClient
