import { defineStore } from 'pinia'
import { ref, Ref, computed, ComputedRef } from 'vue'

interface Message {
  id: number
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
  read: boolean
}

interface ChatPosition {
  x: number
  y: number
}

interface ChatSize {
  width: number
  height: number
}

interface ConversationMessage {
  role: 'user' | 'assistant'
  content: string
}

export const useAIChatStore = defineStore('aiChat', () => {
  // 状态
  const isChatVisible = ref<boolean>(false)
  const messages = ref<Message[]>([])
  const loading = ref<boolean>(false)
  const chatPosition = ref<ChatPosition>({ x: 100, y: 100 })
  const chatSize = ref<ChatSize>({ width: 400, height: 500 })

  // 对话上下文（用于维护上下文感知）
  const conversationContext = ref<ConversationMessage[]>([])
  const maxContextLength = 10 // 最大上下文长度

  // 计算属性
  const hasUnreadMessages = computed<boolean>(() => {
    return messages.value.some(msg => msg.role === 'assistant' && !msg.read)
  })

  // Actions
  const toggleChat = (): void => {
    isChatVisible.value = !isChatVisible.value
    // 标记所有消息为已读
    if (isChatVisible.value) {
      messages.value.forEach(msg => {
        if (msg.role === 'assistant') msg.read = true
      })
    }
  }

  const addMessage = (message: Omit<Message, 'id' | 'timestamp' | 'read'>): void => {
    messages.value.push({
      ...message,
      id: Date.now() + Math.random(),
      timestamp: new Date(),
      read: message.role === 'user' // 用户消息默认已读
    } as Message)

    // 更新对话上下文
    updateConversationContext(message)
  }

  const updateConversationContext = (message: Omit<Message, 'id' | 'timestamp' | 'read'>): void => {
    conversationContext.value.push({
      role: message.role === 'user' ? 'user' : 'assistant',
      content: message.content
    })

    // 限制上下文长度
    if (conversationContext.value.length > maxContextLength) {
      conversationContext.value = conversationContext.value.slice(-maxContextLength)
    }
  }

  // 清空消息
  const clearMessages = (): void => {
    messages.value = []
    conversationContext.value = []
    // 移除自动添加的欢迎消息，让模板中的欢迎页面能够正确显示
  }

  const updatePosition = (position: ChatPosition): void => {
    chatPosition.value = position
  }

  // updateBallPosition 方法已移除，因为悬浮球功能已删除

  const updateSize = (size: ChatSize): void => {
    chatSize.value = size
  }

  const setLoading = (isLoading: boolean): void => {
    loading.value = isLoading
  }

  // 获取格式化后的对话上下文（用于 API 调用）
  const getFormattedContext = computed<ConversationMessage[]>(() => {
    return conversationContext.value.map(msg => ({
      role: msg.role,
      content: msg.content
    }))
  })

  // 初始化欢迎消息
  const initializeWelcomeMessage = (): void => {
    if (messages.value.length === 0) {
      addMessage({
        role: 'assistant',
        content: '您好！我是实习管理系统的 AI 助手。我可以帮助您解答关于实习管理、企业招聘、学生实习审核、数据分析等方面的问题。'
      })
    }
  }

  return {
    // State
    isChatVisible,
    messages,
    loading,
    chatPosition,
    chatSize,
    conversationContext,

    // Getters
    hasUnreadMessages,
    getFormattedContext,

    // Actions
    toggleChat,
    addMessage,
    clearMessages,
    updatePosition,
    updateSize,
    setLoading,
    initializeWelcomeMessage
  }
})
