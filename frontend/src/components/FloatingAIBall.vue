<template>
  <!-- AI悬浮球 -->
  <div
    class="floating-ai-ball"
    :style="ballStyle"
    @mousedown="startBallDrag"
    @click.stop="toggleChat"
  >
    <!-- 悬浮球图标 -->
    <div class="ai-icon">
      <span>AI</span>
    </div>
  </div>

  <!-- 聊天面板 - 移出为同级元素，不再嵌套在悬浮球内部 -->
  <div
    v-if="isExpanded"
    class="chat-panel"
    :style="panelStyle"
    ref="chatPanel"
  >
    <!-- 聊天面板头部（可拖拽区域） -->
    <div
      class="chat-header"
      @mousedown="startPanelDrag"
    >
      <div class="header-content">
        <div class="ai-icon-small">AI</div>
        <div>
          <h3>{{ getTitle() }}</h3>
          <p class="header-description">基于DeepSeek AI的智能对话助手</p>
        </div>
      </div>
      <div class="header-actions">
        <button class="clear-btn" @click.stop="clearHistory">
          <span>清空</span>
        </button>
        <button class="close-btn" @click.stop="closeChat">×</button>
      </div>
    </div>

    <el-scrollbar class="chat-messages" ref="messagesContainer" :native="false">
      <!-- 欢迎消息 -->
      <div v-if="messages.length === 0" class="welcome-section">
        <div class="welcome-icon">AI</div>
        <h3>您好！我是{{ getTitle() }}</h3>
        <p>我可以帮助您解答关于实习的常见问题。</p>
        <div class="quick-suggestions">
          <div class="suggestion-title">点击下方问题获取详细解答：</div>
          <div class="suggestion-buttons">
            <button
              v-for="(question, index) in quickQuestions"
              :key="index"
              @click="selectQuickQuestion(question)"
              :disabled="isLoading"
              class="suggestion-btn"
            >
              {{ question }}
            </button>
          </div>
        </div>
      </div>

      <!-- 历史消息 -->
      <div
        v-for="(message, index) in messages"
        :key="index"
        :class="['message', message.role]"
        :data-role="message.role"
      >
        <div class="message-content-container">
          <div class="message-bubble">
            <div class="message-text" v-html="formatMessage(message.content)"></div>
            <div class="message-time">
              {{ formatTime(message.timestamp) }}
            </div>
          </div>
        </div>
      </div>

      <!-- 流式回复消息 -->
      <div v-if="streamingMessage" class="message ai streaming">
        <div class="message-content-container">
          <div class="message-bubble">
            <div class="message-text" v-html="formatMessage(streamingMessage)"></div>
            <div class="streaming-indicator">
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>
        </div>
      </div>
    </el-scrollbar>

    <div class="chat-input">
      <div class="input-actions">
        <!-- 模型选择组件 -->
        <div class="model-selection">
          <span class="model-label">选择模型：</span>
          <el-radio-group v-model="selectedModel" size="small" :disabled="isLoading">
            <el-radio-button label="deepseek-chat">标准对话</el-radio-button>
            <el-radio-button label="deepseek-reasoner">推理增强</el-radio-button>
          </el-radio-group>
        </div>
      </div>
      <div class="textarea-with-button">
        <textarea
          v-model="userInput"
          placeholder="请输入您的问题..."
          @keydown.enter.exact.prevent="sendMessage"
          :disabled="isLoading"
          rows="3"
          class="message-textarea"
        ></textarea>
        <button
          @click="sendMessage"
          :disabled="!userInput.trim() || isLoading"
          class="send-btn"
        >
          <el-icon v-if="!isLoading"><Promotion /></el-icon>
          <span v-else>生成中...</span>
        </button>
      </div>
    </div>

    <!-- 右下角调整大小手柄 -->
    <div
      class="resize-handle"
      @mousedown="startResize"
    ></div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElRadioGroup, ElRadioButton, ElScrollbar } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import request from '../utils/request'

// Props定义 - 根据角色显示不同标题
const props = defineProps({
  role: {
    type: String,
    default: 'student',
    validator: (value) => ['student', 'teacher', 'company', 'admin'].includes(value)
  }
})

// 根据role获取标题
const getTitle = () => {
  const titles = {
    student: '学生端AI助手',
    teacher: '教师端AI助手',
    company: '企业端AI助手',
    admin: '管理端AI助手'
  }
  return titles[props.role] || 'AI助手'
}

// 基础状态
const isExpanded = ref(false)
const isLoading = ref(false)
const userInput = ref('')
const messages = ref([])
const messagesContainer = ref(null)
const chatPanel = ref(null)
const selectedModel = ref('deepseek-chat')
const streamingMessage = ref('') // 当前流式回复的内容

// 快捷问题列表（所有角色都显示相同的问题）
const quickQuestions = computed(() => {
  return [
    '学生申请实习完整流程',
    '学生实习状态指南'
  ]
})

// 学生状态介绍（仅学生角色显示）
const studentStatusInfo = {
  title: '学生实习状态指南',
  statuses: [
    { status: '无offer', color: '#E6A23C', description: '还未找到实习岗位，继续加油！' },
    { status: '待确认', color: '#409EFF', description: '已获得实习Offer，等待确认实习单位' },
    { status: '已确定', color: '#67C23A', description: '已确认实习单位，实习待开始' },
    { status: '实习中', color: '#409EFF', description: '正在实习中...' },
    { status: '已结束', color: '#909399', description: '实习已全部结束' },
    { status: '已中断', color: '#F56C6C', description: '实习因故中断' },
    { status: '延期', color: '#E6A23C', description: '实习延期（如准备考研）' }
  ]
}

// 学生申请实习完整流程
const studentInternshipFlow = `📝 **学生申请实习完整流程**

**阶段一：申请岗位**
1. 学生浏览职位
2. 点击立即申请，填写申请信息
3. 提交申请
4. 企业审核（岗位申请查看页面）
   - 如果拒绝，学生端申请状态显示已拒绝
   - 如果同意，进入面试阶段

**阶段二：面试**
5. 学生端面试卡片出现（状态：待面试），显示面试时间地点
6. 学生根据面试信息卡片上的面试方式和时间地点完成面试
7. 面试完成
8. 企业给出面试结果（岗位申请查看页面）
   - 如果面试没通过，学生端面试卡片状态变为面试未通过
   - 如果面试通过，进入实习确认阶段

**阶段三：实习确认**
9. 学生填写实习确认表
10. 提交给企业确认
    - 如果拒绝，学生修改后再次提交
    - 如果确认，学生正式进入实习阶段`

// 悬浮球状态
const ballState = reactive({
  x: window.innerWidth - 100,
  y: window.innerHeight - 150,
  isDragging: false,
  dragOffset: { x: 0, y: 0 },
  hasDragged: false,
  visible: true
})

// 聊天面板状态
const panelState = reactive({
  x: 20,
  y: 20,
  width: 380,
  height: 500,
  minWidth: 300,
  minHeight: 400,
  isDragging: false,
  dragOffset: { x: 0, y: 0 },
  isResizing: false
})

// 计算样式
const ballStyle = computed(() => ({
  left: `${ballState.x}px`,
  top: `${ballState.y}px`,
  cursor: ballState.isDragging ? 'grabbing' : 'grab'
}))

const panelStyle = computed(() => ({
  left: `${panelState.x}px`,
  top: `${panelState.y}px`,
  width: `${panelState.width}px`,
  height: `${panelState.height}px`
}))

// 拖拽事件处理器引用（提升到组件级别以便在onUnmounted中清理）
let onBallDrag = null
let stopBallDrag = null
let onPanelDrag = null
let stopPanelDrag = null
let handleResizeMove = null
let handleResizeUp = null

// 优化的悬浮球拖拽逻辑 - 使用requestAnimationFrame
const startBallDrag = (e) => {
  e.preventDefault()
  e.stopPropagation()

  ballState.isDragging = true
  ballState.hasDragged = false
  ballState.dragOffset.x = e.clientX - ballState.x
  ballState.dragOffset.y = e.clientY - ballState.y

  document.body.classList.add('ball-dragging')

  let rafId = null

  onBallDrag = (e) => {
    if (!ballState.isDragging) return

    if (rafId) return

    rafId = requestAnimationFrame(() => {
      rafId = null

      ballState.hasDragged = true

      const newX = e.clientX - ballState.dragOffset.x
      const newY = e.clientY - ballState.dragOffset.y

      const ballSize = 60
      ballState.x = Math.max(0, Math.min(newX, window.innerWidth - ballSize))
      ballState.y = Math.max(0, Math.min(newY, window.innerHeight - ballSize))
    })
  }

  stopBallDrag = () => {
    ballState.isDragging = false

    if (rafId) {
      cancelAnimationFrame(rafId)
      rafId = null
    }

    if (onBallDrag) {
      document.removeEventListener('mousemove', onBallDrag)
    }
    if (stopBallDrag) {
      document.removeEventListener('mouseup', stopBallDrag)
    }
    document.body.classList.remove('ball-dragging')

    snapBallToEdge()

    setTimeout(() => {
      ballState.hasDragged = false
    }, 50)
  }

  document.addEventListener('mousemove', onBallDrag)
  document.addEventListener('mouseup', stopBallDrag)
}

// 悬浮球自动吸附到边缘
const snapBallToEdge = () => {
  const ballSize = 60
  const screenWidth = window.innerWidth
  const snapThreshold = 50

  if (ballState.x < snapThreshold) {
    ballState.x = 0
  } else if (ballState.x > screenWidth - ballSize - snapThreshold) {
    ballState.x = screenWidth - ballSize
  }
}

// 优化的面板拖拽逻辑 - 使用requestAnimationFrame
const startPanelDrag = (e) => {
  e.preventDefault()
  e.stopPropagation()

  panelState.isDragging = true
  panelState.dragOffset.x = e.clientX - panelState.x
  panelState.dragOffset.y = e.clientY - panelState.y

  document.body.classList.add('panel-dragging')

  e.target.style.cursor = 'grabbing'

  let rafId = null

  onPanelDrag = (e) => {
    if (!panelState.isDragging) return

    if (rafId) return

    rafId = requestAnimationFrame(() => {
      rafId = null

      // 直接计算新位置
      let newX = e.clientX - panelState.dragOffset.x
      let newY = e.clientY - panelState.dragOffset.y

      newX = Math.max(0, Math.min(newX, window.innerWidth - panelState.width))
      newY = Math.max(0, Math.min(newY, window.innerHeight - panelState.height))

      // 立即更新位置
      panelState.x = newX
      panelState.y = newY
    })
  }

  stopPanelDrag = () => {
    panelState.isDragging = false

    if (rafId) {
      cancelAnimationFrame(rafId)
      rafId = null
    }

    if (onPanelDrag) {
      document.removeEventListener('mousemove', onPanelDrag)
    }
    if (stopPanelDrag) {
      document.removeEventListener('mouseup', stopPanelDrag)
    }
    document.body.classList.remove('panel-dragging')

    if (chatPanel.value?.querySelector('.chat-header')) {
      chatPanel.value.querySelector('.chat-header').style.cursor = 'grab'
    }
  }

  document.addEventListener('mousemove', onPanelDrag)
  document.addEventListener('mouseup', stopPanelDrag)
}

// 面板调整大小逻辑 - 优化版
const startResize = (e) => {
  e.stopPropagation()
  e.preventDefault()
  panelState.isResizing = true

  // 添加拖拽类，禁用CSS transition
  document.body.classList.add('panel-resizing')

  const startX = e.clientX
  const startY = e.clientY
  const startWidth = panelState.width
  const startHeight = panelState.height

  let rafId = null

  handleResizeMove = (e) => {
    if (!panelState.isResizing) return

    // 使用requestAnimationFrame节流，避免频繁更新
    if (rafId) return

    rafId = requestAnimationFrame(() => {
      rafId = null

      // 直接计算新尺寸
      let newWidth = startWidth + (e.clientX - startX)
      let newHeight = startHeight + (e.clientY - startY)

      newWidth = Math.max(newWidth, panelState.minWidth)
      newHeight = Math.max(newHeight, panelState.minHeight)

      newWidth = Math.min(newWidth, window.innerWidth - panelState.x)
      newHeight = Math.min(newHeight, window.innerHeight - panelState.y)

      panelState.width = newWidth
      panelState.height = newHeight
    })
  }

  handleResizeUp = () => {
    panelState.isResizing = false

    // 取消未完成的动画帧
    if (rafId) {
      cancelAnimationFrame(rafId)
      rafId = null
    }

    // 移除拖拽类，恢复CSS transition
    document.body.classList.remove('panel-resizing')
    document.body.style.cursor = ''

    document.removeEventListener('mousemove', handleResizeMove)
    document.removeEventListener('mouseup', handleResizeUp)
  }

  document.addEventListener('mousemove', handleResizeMove)
  document.addEventListener('mouseup', handleResizeUp)
  document.body.style.cursor = 'nwse-resize'
}

// 切换聊天面板
const toggleChat = () => {
  if (ballState.isDragging || ballState.hasDragged) return

  isExpanded.value = !isExpanded.value

  if (isExpanded.value) {
    positionPanelAtCenter()
  }
}

// 将面板定位在屏幕中间
const positionPanelAtCenter = () => {
  panelState.x = (window.innerWidth - panelState.width) / 2
  panelState.y = (window.innerHeight - panelState.height) / 2

  panelState.x = Math.max(0, Math.min(panelState.x, window.innerWidth - panelState.width - 10))
  panelState.y = Math.max(0, Math.min(panelState.y, window.innerHeight - panelState.height - 10))
}

// 关闭聊天
const closeChat = () => {
  isExpanded.value = false
}

// 清除聊天历史
const clearHistory = () => {
  messages.value = []
  streamingMessage.value = ''
  localStorage.removeItem(`internshipAIChatHistory_${props.role}`)
  userInput.value = ''
  ElMessage.success('对话已清空')
}

// 选择快捷问题
const selectQuickQuestion = (question) => {
  let aiMessage = { role: 'assistant', timestamp: new Date() }

  // 学生实习状态指南直接显示
  if (question === '学生实习状态指南') {
    const statusText = studentStatusInfo.statuses.map(s => `• ${s.status}：${s.description}`).join('\n')
    aiMessage.content = `📋 **学生实习状态指南**\n\n${statusText}\n\n如需了解更多流程，请点击"学生申请实习完整流程"按钮。`
  }
  // 学生申请实习完整流程直接显示
  else if (question === '学生申请实习完整流程') {
    aiMessage.content = studentInternshipFlow
  }
  // 其他问题发给AI
  else {
    userInput.value = question
    return
  }

  messages.value.push(aiMessage)
  scrollToBottom()
}

// 获取格式化的对话上下文
const getFormattedContext = () => {
  return messages.value
    .slice(-10)
    .map(msg => ({
      role: msg.role,
      content: msg.content
    }))
}

// 发送消息
const sendMessage = async () => {
  const message = userInput.value.trim()
  if (!message || isLoading.value) return

  const currentInput = message
  userInput.value = ''
  isLoading.value = true

  const userMessage = {
    role: 'user',
    content: currentInput,
    timestamp: new Date()
  }
  messages.value.push(userMessage)

  streamingMessage.value = ''
  scrollToBottom()

  try {
    await sendStreamingMessage(currentInput)
  } catch (error) {
    console.error('发送消息失败:', error)
    const errorMessage = {
      role: 'assistant',
      content: '抱歉，服务暂时不可用，请稍后重试。',
      timestamp: new Date()
    }
    messages.value.push(errorMessage)
    ElMessage.error('请求失败，请检查网络连接或稍后重试')
  } finally {
    isLoading.value = false
    scrollToBottom()
  }
}

// HTML转义函数，防止XSS
const escapeHtml = (str) => {
  if (!str) return ''
  const div = document.createElement('div')
  div.textContent = str
  return div.innerHTML
}

// 格式化消息内容
const formatMessage = (content) => {
  if (!content) return ''
  // 先转义HTML，再处理Markdown格式
  let formatted = escapeHtml(content)
    .replace(/\*{2}(.*?)\*{2}/g, '$1') // 去除**强调**
    .replace(/\*(.*?)\*/g, '$1')      // 去除*斜体*
    .replace(/\_{2}(.*?)\_{2}/g, '$1') // 去除__强调__
    .replace(/^[\s-]*-\s/gm, '')       // 去除列表项前的-符号
    .replace(/^[\s*]*\*\s/gm, '');     // 去除列表项前的*符号
  // 然后替换换行符
  return formatted.replace(/\n/g, '<br>')
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return '未知'

  const date = new Date(time)
  if (isNaN(date.getTime())) return '未知'

  const now = new Date()
  const diff = now - date

  // 小于1分钟
  if (diff < 60 * 1000) {
    return '刚刚'
  }
  // 小于1小时
  else if (diff < 60 * 60 * 1000) {
    return `${Math.floor(diff / (60 * 1000))}分钟前`
  }
  // 小于24小时
  else if (diff < 24 * 60 * 60 * 1000) {
    return `${Math.floor(diff / (60 * 60 * 1000))}小时前`
  }
  // 小于7天
  else if (diff < 7 * 24 * 60 * 60 * 1000) {
    return `${Math.floor(diff / (24 * 60 * 60 * 1000))}天前`
  }
  // 否则显示具体日期
  else {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  }
}

// 发送流式消息 - 与AIChat.vue保持一致
const sendStreamingMessage = (message) => {
  return new Promise((resolve, reject) => {
    const token = localStorage.getItem('token')
    let controller = new AbortController()
    let timeoutId = null

    // 设置超时
    timeoutId = setTimeout(() => {
      controller.abort()
      ElMessage.warning('请求超时，请重试')
      reject(new Error('请求超时'))
    }, 60000)

    const headers = {
      'Content-Type': 'application/json'
    }
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    // 获取对话上下文
    const context = getFormattedContext()

    fetch('/api/ai/chat/stream', {
      method: 'POST',
      headers: headers,
      body: JSON.stringify({
        message: message,
        context: context,
        model: selectedModel.value
      }),
      signal: controller.signal
    })
      .then(response => {
        clearTimeout(timeoutId)

        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`)
        }

        if (!response.body) {
          throw new Error('Response body is null')
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        const processStream = ({ done, value }) => {
          if (done) {
            // 流结束，保存原始内容，渲染时再格式化
            const aiMessage = {
              role: 'assistant',
              content: streamingMessage.value,
              timestamp: new Date()
            }
            messages.value.push(aiMessage)
            streamingMessage.value = ''
            resolve()
            return
          }

          buffer += decoder.decode(value, { stream: true })

          const lines = buffer.split('\n')
          buffer = lines.pop()

          for (const line of lines) {
            if (!line.trim()) continue

            try {
              if (line.startsWith('event:')) {
                continue
              } else if (line.startsWith('data:')) {
                const jsonStr = line.substring(5).trim()
                const data = JSON.parse(jsonStr)

                if (data.type === 'chunk') {
                  streamingMessage.value += data.content
                  nextTick(() => scrollToBottom())
                } else if (data.type === 'end') {
                  // 流结束时，保存原始内容，渲染时再格式化
                  const aiMessage = {
                    role: 'assistant',
                    content: streamingMessage.value, // 保存原始内容，不格式化
                    timestamp: new Date()
                  }
                  messages.value.push(aiMessage)
                  streamingMessage.value = ''
                  isLoading.value = false
                  reader.cancel()
                  resolve()
                  return
                } else if (data.type === 'error') {
                  ElMessage.error(data.message || '生成过程中发生错误')
                  reader.cancel()
                  reject(new Error(data.message))
                  return
                }
              }
            } catch (error) {
              console.error('解析流式数据失败:', error)
            }
          }

          reader.read().then(processStream)
        }

        reader.read().then(processStream)
      })
      .catch(error => {
        clearTimeout(timeoutId)
        isLoading.value = false
        if (error.name === 'AbortError') {
          console.log('请求已超时中止')
        } else {
          console.error('流式连接错误:', error)
          ElMessage.error('连接中断或服务器拒绝访问，请重试')
        }
        reject(error)
      })
  })
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

// 保存状态到本地存储
const saveState = () => {
  localStorage.setItem(`internshipAIBallPosition_${props.role}`, JSON.stringify({
    x: ballState.x,
    y: ballState.y
  }))

  localStorage.setItem(`internshipAIPanelState_${props.role}`, JSON.stringify({
    x: panelState.x,
    y: panelState.y,
    width: panelState.width,
    height: panelState.height
  }))

  const recentMessages = messages.value.slice(-20)
  localStorage.setItem(`internshipAIChatHistory_${props.role}`, JSON.stringify(recentMessages))
}

// 节流保存函数，减少频繁写入
let saveStateTimer = null
const throttledSaveState = () => {
  if (saveStateTimer) return
  saveStateTimer = setTimeout(() => {
    saveState()
    saveStateTimer = null
  }, 300)
}

// 组件挂载时加载历史数据
onMounted(() => {
  try {
    const savedBallPosition = localStorage.getItem(`internshipAIBallPosition_${props.role}`)
    if (savedBallPosition) {
      const { x, y } = JSON.parse(savedBallPosition)
      if (typeof x === 'number' && typeof y === 'number' && !isNaN(x) && !isNaN(y)) {
        ballState.x = x
        ballState.y = y
      } else {
        setDefaultBallPosition()
      }
    }
    ensureBallVisibility()
  } catch (e) {
    console.warn('加载悬浮球位置失败:', e)
    setDefaultBallPosition()
  }

  const savedPanelState = localStorage.getItem(`internshipAIPanelState_${props.role}`)
  if (savedPanelState) {
    try {
      const state = JSON.parse(savedPanelState)
      panelState.x = state.x
      panelState.y = state.y
      panelState.width = Math.max(state.width, panelState.minWidth)
      panelState.height = Math.max(state.height, panelState.minHeight)
    } catch (e) {
      console.warn('加载面板状态失败:', e)
    }
  }

  const savedMessages = localStorage.getItem(`internshipAIChatHistory_${props.role}`)
  if (savedMessages) {
    try {
      messages.value = JSON.parse(savedMessages)
    } catch (e) {
      console.warn('加载聊天历史失败:', e)
    }
  }

  window.addEventListener('resize', handleWindowResize)
})

// 窗口大小变化时调整位置
const setDefaultBallPosition = () => {
  ballState.x = window.innerWidth - 100
  ballState.y = window.innerHeight - 150
}

const ensureBallVisibility = () => {
  const ballSize = 60
  ballState.x = Math.max(20, Math.min(ballState.x, window.innerWidth - ballSize - 20))
  ballState.y = Math.max(20, Math.min(ballState.y, window.innerHeight - ballSize - 20))
}

const handleWindowResize = () => {
  const ballSize = 60
  ballState.x = Math.max(0, Math.min(ballState.x, window.innerWidth - ballSize))
  ballState.y = Math.max(0, Math.min(ballState.y, window.innerHeight - ballSize))
  ensureBallVisibility()

  if (isExpanded.value) {
    panelState.x = (window.innerWidth - panelState.width) / 2
    panelState.y = (window.innerHeight - panelState.height) / 2

    panelState.x = Math.max(0, Math.min(panelState.x, window.innerWidth - panelState.width - 10))
    panelState.y = Math.max(0, Math.min(panelState.y, window.innerHeight - panelState.height - 10))
  } else {
    panelState.x = Math.max(0, Math.min(panelState.x, window.innerWidth - panelState.width))
    panelState.y = Math.max(0, Math.min(panelState.y, window.innerHeight - panelState.height))
  }
}

// 组件卸载时保存状态并清理事件监听器
onUnmounted(() => {
  // 清理所有可能残留的事件监听器
  if (onBallDrag) document.removeEventListener('mousemove', onBallDrag)
  if (stopBallDrag) document.removeEventListener('mouseup', stopBallDrag)
  if (onPanelDrag) document.removeEventListener('mousemove', onPanelDrag)
  if (stopPanelDrag) document.removeEventListener('mouseup', stopPanelDrag)
  if (handleResizeMove) document.removeEventListener('mousemove', handleResizeMove)
  if (handleResizeUp) document.removeEventListener('mouseup', handleResizeUp)
  document.body.classList.remove('ball-dragging', 'panel-dragging')

  // 清理窗口resize监听
  window.removeEventListener('resize', handleWindowResize)

  // 清除节流定时器并保存最终状态
  if (saveStateTimer) {
    clearTimeout(saveStateTimer)
    saveState()
  }
})

// 实时保存状态变化（使用节流）
watch([
  () => ballState.x,
  () => ballState.y,
  () => panelState.x,
  () => panelState.y,
  () => panelState.width,
  () => panelState.height,
  () => messages.value
], throttledSaveState)
</script>

<style scoped>
.floating-ai-ball {
  position: fixed;
  width: 60px;
  height: 60px;
  z-index: 10000;
  transition: transform 0.1s ease-out;
  will-change: transform;
}

/* 拖拽时禁用过渡效果 */
body.ball-dragging .floating-ai-ball {
  transition: none !important;
}

body.panel-dragging .chat-panel,
body.panel-resizing .chat-panel {
  transition: none !important;
}

.ai-icon {
  width: 60px;
  height: 60px;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: bold;
  font-size: 16px;
  cursor: pointer;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
  transition: all 0.3s ease;
  user-select: none;
  animation: float 3s ease-in-out infinite;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

.ai-icon:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 25px rgba(0, 0, 0, 0.35);
}

.chat-panel {
  position: fixed;
  z-index: 9999;
  background: white;
  border-radius: 24px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  overflow: visible;
  transition: transform 0.1s ease-out, width 0.1s ease, height 0.1s ease;
  will-change: transform, width, height;
  transform-origin: center center;
  resize: both;
}

.chat-header {
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  color: white;
  padding: 16px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: grab;
  user-select: none;
  position: relative;
  overflow: hidden;
  border-radius: 24px 24px 0 0;
}

.chat-header::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -50%;
  width: 100%;
  height: 100%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
  transform: rotate(30deg);
}

.header-content {
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
  z-index: 1;
}

.ai-icon-small {
  width: 32px;
  height: 32px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: bold;
}

.chat-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: white;
}

.header-description {
  margin: 0;
  font-size: 12px;
  opacity: 0.9;
  margin-top: 2px;
}

.header-actions {
  display: flex;
  gap: 12px;
  position: relative;
  z-index: 1;
}

.clear-btn {
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: white;
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 4px;
}

.clear-btn:hover {
  background: rgba(255, 255, 255, 0.3);
  transform: translateY(-1px);
}

.close-btn {
  background: none;
  border: none;
  color: white;
  font-size: 24px;
  cursor: pointer;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
  line-height: 1;
  font-weight: 300;
}

.close-btn:hover {
  transform: scale(1.1);
}

.chat-messages {
  flex: 1;
  padding: 20px;
  background: #fafbfc;
  border-radius: 0 0 24px 24px;
}

.chat-messages :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
  overflow-y: auto;
  scroll-behavior: smooth;
}

/* 欢迎区域 */
.welcome-section {
  text-align: center;
  padding: 40px 20px;
  max-width: 500px;
  margin: 0 auto;
}

.welcome-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  font-size: 32px;
  color: white;
  font-weight: bold;
}

.welcome-section h3 {
  font-size: 22px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #333;
}

.welcome-section p {
  font-size: 14px;
  color: #666;
  margin-bottom: 24px;
  line-height: 1.6;
}

.quick-suggestions {
  margin-top: 24px;
}

.suggestion-title {
  font-size: 13px;
  color: #999;
  margin-bottom: 12px;
}

.suggestion-buttons {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.suggestion-btn {
  border-radius: 18px;
  font-size: 12px;
  padding: 6px 14px;
  border: 1px solid #e0e0e0;
  background: white;
  color: #333;
  transition: all 0.2s;
  cursor: pointer;
}

.suggestion-btn:hover {
  background: #f5f7fa;
  border-color: #409EFF;
  color: #409EFF;
}

.suggestion-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 消息样式 */
.message {
  display: flex;
  margin-bottom: 16px;
  animation: fadeIn 0.3s ease-in;
}

.message.user {
  justify-content: flex-end;
}

.message.ai,
.message[data-role="assistant"] {
  justify-content: flex-start;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-content-container {
  max-width: 80%;
}

.message.user .message-content-container {
  display: flex;
  justify-content: flex-end;
}

.message.ai .message-content-container,
.message[data-role="assistant"] .message-content-container {
  display: flex;
  justify-content: flex-start;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 18px;
  position: relative;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  max-width: 100%;
  line-height: 1.5;
  min-height: 44px;
  display: flex;
  flex-direction: column;
  white-space: normal;
  text-align: left;
}

.message.user .message-bubble {
  background: #409EFF;
  color: white;
  border-bottom-right-radius: 4px;
}

.message.ai .message-bubble,
.message[data-role="assistant"] .message-bubble {
  background: #f1f3f5;
  color: #333;
  border-bottom-left-radius: 4px;
  position: relative;
}

.message-text {
  line-height: 1.6;
  word-wrap: break-word;
  flex: 1;
  /* 流式消息字体大小 font-size: px; */
}

.message-time {
  font-size: 12px;
  opacity: 0.7;
  margin-top: 6px;
  text-align: right;
}

.message.ai .message-time {
  text-align: left;
}

/* 流式指示器样式 */
.streaming-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  justify-content: flex-start;
}

.streaming-indicator span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10a37f;
  animation: streaming 1.4s infinite ease-in-out;
}

.streaming-indicator span:nth-child(1) {
  animation-delay: -0.32s;
}

.streaming-indicator span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes streaming {
  0%, 80%, 100% {
    transform: scale(0.8);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.chat-input {
  padding: 16px 20px;
  border-top: 1px solid #f0f0f0;
  background: white;
  position: sticky;
  bottom: 0;
  z-index: 10;
  border-radius: 0 0 24px 24px;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.model-selection {
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-label {
  font-size: 13px;
  color: #666;
  white-space: nowrap;
}

.textarea-with-button {
  position: relative;
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.message-textarea {
  flex: 1;
  border-radius: 18px;
  border: 1px solid #e0e0e0;
  resize: none;
  font-size: 14px;
  line-height: 1.5;
  padding: 12px 16px;
  transition: all 0.2s;
  outline: none;
  font-family: inherit;
  min-height: 60px;
  max-height: 120px;
}

.message-textarea:focus {
  border-color: #409EFF;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.1);
}

.message-textarea:disabled {
  background-color: #f5f5f5;
  cursor: not-allowed;
}

.send-btn {
  border-radius: 18px;
  padding: 12px 20px;
  background: #409EFF;
  border: none;
  color: white;
  height: auto;
  transition: all 0.2s;
  white-space: nowrap;
  cursor: pointer;
  font-size: 14px;
}

.send-btn:hover:not(:disabled) {
  background: #66b1ff;
  transform: translateY(-1px);
}

.send-btn:disabled {
  background: #c0c4cc;
  transform: none;
  cursor: not-allowed;
}

/* 调整大小手柄 */
.resize-handle {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 20px;
  height: 20px;
  cursor: nwse-resize;
  background: linear-gradient(135deg, transparent 50%, #dcdfe6 50%);
  border-top-left-radius: 12px;
  opacity: 0.7;
  transition: opacity 0.2s, background 0.2s;
  z-index: 20;
}

.resize-handle:hover {
  opacity: 1;
  background: linear-gradient(135deg, transparent 50%, #c0c4cc 50%);
}

.resize-handle::before {
  content: '';
  position: absolute;
  bottom: 3px;
  right: 3px;
  width: 10px;
  height: 10px;
  background-image: radial-gradient(circle, #909399 2px, transparent 2px);
  background-size: 4px 4px;
  background-position: 0 0, 4px 4px;
}

/* 文本选择样式 */
.chat-header {
  user-select: none;
}

.resize-handle {
  user-select: none;
}

.chat-messages * {
  user-select: text;
}

.message-textarea {
  user-select: text;
}

*::selection {
  background: #409EFF;
  color: white;
}
</style>
