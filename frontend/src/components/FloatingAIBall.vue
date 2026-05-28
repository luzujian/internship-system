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

  <!-- 企业端气泡消息（待处理提醒） -->
  <Transition name="bubble-pop">
    <div
      v-if="showCompanyBubble && companyBubbleVisible && props.role === 'company'"
      class="company-bubble glass-effect"
      :style="companyBubbleStyle"
    >
      <div class="company-bubble-header">
        <div class="company-bubble-header-left">
          <span class="company-bubble-icon">📋</span>
          <span class="company-bubble-title">待办提醒</span>
        </div>
        <button class="company-bubble-close" @click.stop="closeCompanyBubble">×</button>
      </div>
      <div class="company-bubble-divider"></div>
      <div class="company-bubble-body">
        <div class="company-bubble-item">
          <span class="company-bubble-badge">
            <span class="badge-number">{{ pendingApplications }}</span>
          </span>
          <span>个岗位申请待处理</span>
        </div>
        <div class="company-bubble-item">
          <span class="company-bubble-badge">
            <span class="badge-number">{{ pendingConfirmations }}</span>
          </span>
          <span>个实习确认表待确认</span>
        </div>
      </div>
    </div>
  </Transition>

  <!-- 教师端/管理员端鼓励气泡 -->
  <Transition name="bubble-pop">
    <div
      v-if="showEncouragementBubble && encouragementBubbleVisible && (props.role === 'teacher' || props.role === 'admin')"
      class="encouragement-bubble glass-effect"
      :style="encouragementBubbleStyle"
    >
      <div class="encouragement-bubble-inner">
        <div class="encouragement-icon">{{ currentEncouragement.icon }}</div>
        <div class="encouragement-text">{{ currentEncouragement.text }}</div>
      </div>
      <div class="encouragement-bubble-footer">
        <label class="encouragement-checkbox">
          <input type="checkbox" v-model="encouragementDisabled" @change="onEncouragementDisabledChange">
          <span>不再提醒</span>
        </label>
      </div>
    </div>
  </Transition>

  <!-- 学生端气泡消息 -->
  <Transition name="bubble-pop">
    <div
      v-if="showTipBubble && tipBubbleVisible && props.role === 'student'"
      class="tip-bubble glass-effect"
      :style="tipBubbleStyle"
    >
      <div class="tip-bubble-inner">
        <div class="tip-bubble-icon">{{ currentTip.icon }}</div>
        <div class="tip-bubble-text">{{ currentTip.text }}</div>
      </div>
      <button class="tip-bubble-close" @click.stop="closeTipBubble">×</button>
    </div>
  </Transition>

  <!-- 聊天面板 - 移出为同级元素，不再嵌套在悬浮球内部 -->
  <div
    v-if="isExpanded"
    class="chat-panel glass-effect"
    :style="panelStyle"
    ref="chatPanel"
  >
    <!-- 聊天面板头部（可拖拽区域） -->
    <div
      class="chat-header"
      @mousedown="startPanelDrag"
    >
      <div class="header-left">
        <div class="ai-icon-small">AI</div>
        <span class="header-title">{{ getTitle() }}</span>
      </div>
      <div class="header-actions">
        <div class="model-select-wrapper">
          <div
            class="model-select-trigger"
            @click.stop="toggleModelDropdown"
          >
            <span class="model-name">{{ getCurrentModelName() }}</span>
            <span class="model-arrow">▼</span>
          </div>
          <Teleport to="body" :disabled="false">
            <div
              v-if="showModelDropdown"
              class="model-select-dropdown"
              :style="getModelDropdownStyle()"
            >
              <div
                v-for="model in availableModels"
                :key="model.modelCode"
                @click.stop="selectModel(model.modelCode)"
                class="model-option"
                :class="{ active: model.modelCode === selectedModel }"
              >
                {{ model.modelName }}
              </div>
            </div>
          </Teleport>
        </div>
        <button class="icon-btn" @click.stop="clearHistory" title="清空对话">🗑️</button>
        <button class="icon-btn close-btn" @click.stop="closeChat" title="关闭">×</button>
      </div>
    </div>

    <el-scrollbar class="chat-messages" ref="messagesContainer" :native="false">
      <!-- 欢迎消息作为AI气泡显示 -->
      <div v-if="messages.length === 0 && isExpanded" class="welcome-messages">
        <div class="message ai">
          <div class="message-content-container">
            <div class="message-bubble">
              <div class="message-text">您好！我是{{ getTitle() }} 👋<br><br>我可以帮助您解答关于实习的常见问题，有什么可以帮您的吗？</div>
              <div class="message-time">刚刚</div>
            </div>
          </div>
        </div>
        <div class="quick-suggestions">
          <button
            v-for="(question, index) in quickQuestions"
            :key="index"
            @click="selectQuickQuestion(question)"
            :disabled="isLoading"
            class="suggestion-capsule"
          >
            {{ question }}
          </button>
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
            <!-- 内嵌式 AI 思考呼吸圆点动画 -->
            <div v-if="message.role === 'assistant' && message.isStreaming" class="inline-typing-indicator">
              <span class="dot"></span>
              <span class="dot"></span>
              <span class="dot"></span>
            </div>

            <!-- 岗位推荐卡片 -->
            <div v-if="message.type === 'job_recommendation' && message.jobData" class="job-recommendation">
              <div class="job-recommendation-title">
                <span class="job-icon">💼</span>
                <span>为您推荐以下岗位</span>
              </div>
              <div class="job-cards-grid">
                <div
                  v-for="(job, jobIndex) in message.jobData.positions"
                  :key="job.id"
                  class="job-card-compact"
                >
                  <div class="job-row1">
                    <span class="job-name-compact">{{ job.positionName }}</span>
                    <span class="job-salary-compact">{{ job.salary }}</span>
                  </div>
                  <div class="job-row2">
                    <span class="job-company-compact">🏢 {{ job.companyName }}</span>
                  </div>
                  <div class="job-row3">
                    <span class="job-location-compact">📍 {{ job.location }}</span>
                  </div>
                  <div class="job-row3">
                    <span class="job-contact-compact">👤 {{ job.contactPerson }}</span>
                  </div>
                  <div class="job-row3">
                    <span class="job-phone-compact">📞 {{ job.contactPhone }}</span>
                  </div>
                  <div class="job-row4">
                    <button v-if="!job.isFavorited" class="action-btn favorite" @click="toggleFavorite(job, message)">收藏</button>
                    <button v-else class="action-btn favorited" @click="toggleFavorite(job, message)">已收藏</button>
                    <button class="action-btn go-apply" @click="goToJobsPage(job)">立即前往申请</button>
                  </div>
                </div>
              </div>
              <div v-if="!message.jobData.positions || message.jobData.positions.length === 0" class="job-empty">
                抱歉，暂未找到与您条件匹配的岗位
              </div>
            </div>
            <!-- 普通文本消息 -->
            <div v-else class="message-text" v-html="formatMessage(message.content)"></div>
            <div class="message-time">
              {{ formatTime(message.timestamp) }}
            </div>
          </div>
        </div>
      </div>
    </el-scrollbar>

    <div class="chat-footer-wrapper">
      <div class="input-capsule" :class="{ 'is-focused': isInputFocused }">

        <textarea
          ref="chatInputRef"
          v-model="userInput"
          class="grow-textarea"
          placeholder="向 AI 助手提问..."
          rows="1"
          :readonly="isGenerating"
          :disabled="isGenerating"
          @input="handleInput"
          @focus="isInputFocused = true"
          @blur="isInputFocused = false"
          @keydown.enter.prevent="sendMessage"
        ></textarea>

        <button
          v-if="!isGenerating"
          class="send-btn"
          :class="{ 'is-active': userInput.trim().length > 0 }"
          :disabled="!userInput.trim()"
          @click="sendMessage"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2.5"
            stroke-linecap="round"
            stroke-linejoin="round"
            style="width: 18px !important; height: 18px !important; min-width: 18px !important; min-height: 18px !important; flex-shrink: 0; margin-left: -2px;"
          >
            <line x1="22" y1="2" x2="11" y2="13"></line>
            <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
          </svg>
        </button>

        <button
          v-else
          class="send-btn stop-btn"
          @click="stopGeneration"
          title="停止生成"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24"
            fill="currentColor"
            style="width: 16px !important; height: 16px !important; flex-shrink: 0; margin: 0;"
          >
            <rect x="6" y="6" width="12" height="12" rx="2" ry="2"></rect>
          </svg>
        </button>
      </div>
    </div>

    <!-- 左下角调整大小手柄 -->
    <div
      class="resize-handle resize-handle-left"
      @click.stop
    >
      <div class="resize-hitarea" @mousedown="startResize"></div>
      <svg class="resize-svg" viewBox="0 0 50 35">
        <!-- 左下角调整大小手柄的弧线，与 24px 圆角匹配，和右下角关于中线对称 -->
        <path d="M 7 19 A 15 15 0 0 0 18 29" stroke="#606060" stroke-width="3.5" fill="none" stroke-linecap="round" opacity="0.8"/>
      </svg>
    </div>

    <!-- 右下角调整大小手柄 -->
    <div
      class="resize-handle"
      @click.stop
    >
      <div class="resize-hitarea" @mousedown="startResize"></div>
      <svg class="resize-svg" viewBox="0 0 50 35">
        <!-- 调整大小手柄的弧线，与 24px 圆角匹配 -->
        <path d="M 43 19 A 15 15 0 0 1 32 29" stroke="#606060" stroke-width="3.5" fill="none" stroke-linecap="round" opacity="0.8"/>
      </svg>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElRadioGroup, ElRadioButton, ElScrollbar, ElSelect, ElOption } from 'element-plus'
import request from '../utils/request'
import eventBus from '../utils/eventBus'
import { onCompanyTodoUpdate, offCompanyTodoUpdate } from '../utils/websocket'

const router = useRouter()
const route = useRoute()

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

// 获取当前模型名称
const getCurrentModelName = () => {
  const model = availableModels.value.find(m => m.modelCode === selectedModel.value)
  return model ? model.modelName : '选择模型'
}

// 切换模型下拉框
const toggleModelDropdown = () => {
  showModelDropdown.value = !showModelDropdown.value
}

// 选择模型
const selectModel = (modelCode) => {
  selectedModel.value = modelCode
  showModelDropdown.value = false
}

// 获取模型下拉框的位置样式
const getModelDropdownStyle = () => {
  const trigger = document.querySelector('.model-select-trigger')
  if (!trigger) return {}

  const rect = trigger.getBoundingClientRect()
  return {
    position: 'fixed',
    top: `${rect.bottom + 4}px`,
    left: `${rect.left}px`,
    background: 'white',
    borderRadius: '4px',
    boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
    zIndex: '1002',
    minWidth: `${rect.width}px`
  }
}

// 基础状态
const isExpanded = ref(false)
const isLoading = ref(false)
const isGenerating = ref(false) // AI 是否正在生成回复
const chatInputRef = ref(null)
const isInputFocused = ref(false)
const userInput = ref('')
const messages = ref([])
const showModelDropdown = ref(false)
const messagesContainer = ref(null)
const chatPanel = ref(null)
const selectedModel = ref('deepseek-v4-flash')
const availableModels = ref([
  { modelCode: 'deepseek-v4-flash', modelName: 'DeepSeek-V4-Flash' },
  { modelCode: 'deepseek-v4-pro', modelName: 'DeepSeek-V4-Pro' }
])
let abortController = null // 请求中断控制器

// 企业端气泡相关状态
const showCompanyBubble = ref(false)
const companyBubbleVisible = ref(false)
const pendingApplications = ref(0) // 待处理申请数
const pendingConfirmations = ref(0) // 待确认实习表数
const companyBubblePos = reactive({ left: 0, top: 0 })

// ============ 学生端气泡相关 ============
const showTipBubble = ref(false)
const tipBubbleVisible = ref(false)
const currentTip = ref({ icon: '💡', text: '试试对我说"帮我推荐实习岗位"吧！' })
const tipBubblePos = reactive({ left: 0, top: 0 })
const tipTimeoutId = ref(null)

// ============ 教师端/管理员端鼓励气泡相关 ============
const showEncouragementBubble = ref(false)
const encouragementBubbleVisible = ref(false)
const encouragementDisabled = ref(false)
const currentEncouragement = ref({ icon: '✨', text: '今天辛苦了~' })
const encouragementBubblePos = reactive({ left: 0, top: 0 })
const encouragementIntervalId = ref(null)
const encouragementTimeoutId1 = ref(null)
const encouragementTimeoutId2 = ref(null)

// 鼓励语列表
const encouragements = [
  { icon: '☕', text: '今天辛苦了，喝杯水休息一下吧~' },
  { icon: '💪', text: '您的工作做得真棒，继续加油！' },
  { icon: '🌸', text: '微微疲惫是正常的，记得照顾好自己~' },
  { icon: '✨', text: '每一份付出都会有回报的！' },
  { icon: '🎉', text: '今天又解决了这么多问题，太厉害了！' },
  { icon: '🍜', text: '工作再忙，也要记得按时吃饭哦~' },
  { icon: '⭐', text: '您是这个团队不可或缺的一员！' },
  { icon: '💤', text: '累了就休息一下，效率会更高的~' },
  { icon: '🌿', text: '窗外的风景很美，站起来伸个懒腰吧~' },
  { icon: '👍', text: '您今天又进步了一点，为您点赞！' },
  { icon: '🌈', text: '保持好心情，事情会越来越顺利的~' },
  { icon: '😍', text: '您认真工作的样子真好看！' },
  { icon: '🎁', text: '别忘了给自己一个小奖励哦~' },
  { icon: '🌞', text: '今天的阳光很温暖，您也是~' },
  { icon: '🦸', text: '您解决难题的能力超乎想象！' },
  { icon: '🍵', text: '给自己泡杯热茶，犒劳一下吧~' },
  { icon: '👀', text: '您的努力每个人都看在眼里呢！' },
  { icon: '📋', text: '事情一件一件做，您做得很好！' },
  { icon: '🛤️', text: '休息是为了走更远的路~' },
  { icon: '🤝', text: '相信您，一定可以做到的！' },
  { icon: '🏫', text: '您是学生心中最棒的老师！' },
  { icon: '❤️', text: '每一天都要好好爱自己哦~' },
  { icon: '👨‍👩‍👧', text: '工作虽忙，也别忘了陪陪家人~' },
  { icon: '🏆', text: '您的坚持真的非常了不起！' }
]

// 快捷问题列表（所有角色都显示相同的问题）
const quickQuestions = computed(() => {
  if (props.role === 'student') {
    return [
      '帮我推荐实习岗位',
      '学生申请实习完整流程',
      '学生实习状态指南'
    ]
  }
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

// ============ 企业端气泡消息相关 ============

// 获取企业端统计数据（仅初始化使用）
const fetchCompanyStats = async () => {
  try {
    const response = await request({
      url: '/company/stats',
      method: 'get'
    })
    if (response.code === 200) {
      pendingApplications.value = response.data.pendingApplications || 0
      pendingConfirmations.value = response.data.pendingConfirmations || 0
    }
  } catch (error) {
    console.error('获取企业统计数据失败:', error)
  }
}

// 气泡闪烁动画标记
// WebSocket企业待办更新处理
const handleCompanyTodoUpdate = (data) => {
  if (props.role === 'company') {
    pendingApplications.value = data.pendingApplications
    pendingConfirmations.value = data.pendingConfirmations
  }
}

// 更新企业端气泡位置
const updateCompanyBubblePos = () => {
  const bubbleWidth = 220 // 统一气泡宽度
  const bubbleHeight = 90
  const ballSize = 60
  const gap = 15
  const screenWidth = window.innerWidth
  const screenHeight = window.innerHeight

  let left = ballState.x - bubbleWidth - gap
  let top = ballState.y + (ballSize - bubbleHeight) / 2

  if (top < 10) top = 10
  if (top + bubbleHeight > screenHeight - 10) top = screenHeight - bubbleHeight - 10
  if (left < 10) left = ballState.x + ballSize + gap
  if (left + bubbleWidth > screenWidth - 10) left = ballState.x - bubbleWidth - gap

  companyBubblePos.left = left
  companyBubblePos.top = top
}

// 企业端气泡样式
const companyBubbleStyle = computed(() => ({
  left: `${companyBubblePos.left}px`,
  top: `${companyBubblePos.top}px`
}))

// 显示企业端气泡
const showCompanyReminderBubble = () => {
  if (props.role !== 'company') return
  updateCompanyBubblePos()
  showCompanyBubble.value = true
  companyBubbleVisible.value = true
}

// 隐藏企业端气泡
const hideCompanyBubble = () => {
  companyBubbleVisible.value = false
  setTimeout(() => {
    showCompanyBubble.value = false
  }, 300)
}

// 关闭企业端气泡（用户点击关闭）
const closeCompanyBubble = () => {
  hideCompanyBubble()
  // 保存关闭状态
  localStorage.setItem(`internshipAICompanyBubbleClosed_${props.role}`, 'true')
}

// 检查企业端气泡是否应该显示
const shouldShowCompanyBubble = () => {
  const closedKey = `internshipAICompanyBubbleClosed_${props.role}`
  return localStorage.getItem(closedKey) !== 'true'
}

// ============ 学生端气泡相关 ============

// 计算学生端气泡位置 - 与企业端一致
const updateTipBubblePos = () => {
  const bubbleWidth = 220 // 统一气泡宽度
  const bubbleHeight = 80
  const ballSize = 60
  const gap = 15
  const screenWidth = window.innerWidth
  const screenHeight = window.innerHeight

  let left = ballState.x - bubbleWidth - gap
  let top = ballState.y + (ballSize - bubbleHeight) / 2

  if (top < 10) top = 10
  if (top + bubbleHeight > screenHeight - 10) top = screenHeight - bubbleHeight - 10
  if (left < 10) left = ballState.x + ballSize + gap
  if (left + bubbleWidth > screenWidth - 10) left = ballState.x - bubbleWidth - gap

  tipBubblePos.left = left
  tipBubblePos.top = top
}

// 学生端气泡样式
const tipBubbleStyle = computed(() => ({
  left: `${tipBubblePos.left}px`,
  top: `${tipBubblePos.top}px`
}))

// 显示学生端气泡
const showTipBubbleTimer = () => {
  if (props.role !== 'student') return

  // 检查是否已经关闭过
  const closedKey = `internshipAItipBubbleClosed_${props.role}`
  if (localStorage.getItem(closedKey) === 'true') {
    return
  }

  updateTipBubblePos()
  showTipBubble.value = true
  tipBubbleVisible.value = true
}

// 关闭学生端气泡
const closeTipBubble = () => {
  tipBubbleVisible.value = false
  setTimeout(() => {
    showTipBubble.value = false
  }, 300)
  // 保存关闭状态
  const closedKey = `internshipAItipBubbleClosed_${props.role}`
  localStorage.setItem(closedKey, 'true')
}

// ============ 教师端/管理员端鼓励气泡相关 ============

// 获取鼓励语（从后端）
const fetchEncouragement = async () => {
  if (props.role === 'student' || props.role === 'company') return

  try {
    const response = await request({
      url: '/ai/encouragement',
      method: 'get',
      params: { role: props.role }
    })
    if (response.code === 200 && response.data) {
      currentEncouragement.value = response.data
    }
  } catch (error) {
    // 使用默认随机
    currentEncouragement.value = encouragements[Math.floor(Math.random() * encouragements.length)]
  }
}

// 更新鼓励气泡位置
const updateEncouragementBubblePos = () => {
  const bubbleWidth = 220 // 统一气泡宽度
  const bubbleHeight = 80
  const ballSize = 60
  const gap = 15
  const screenWidth = window.innerWidth
  const screenHeight = window.innerHeight

  let left = ballState.x - bubbleWidth - gap
  let top = ballState.y + (ballSize - bubbleHeight) / 2

  if (top < 10) top = 10
  if (top + bubbleHeight > screenHeight - 10) top = screenHeight - bubbleHeight - 10
  if (left < 10) left = ballState.x + ballSize + gap
  if (left + bubbleWidth > screenWidth - 10) left = ballState.x - bubbleWidth - gap

  encouragementBubblePos.left = left
  encouragementBubblePos.top = top
}

// 鼓励气泡样式
const encouragementBubbleStyle = computed(() => ({
  left: `${encouragementBubblePos.left}px`,
  top: `${encouragementBubblePos.top}px`
}))

// 启动鼓励语定时切换
const startEncouragementCycle = () => {
  if (props.role === 'student' || props.role === 'company') return

  // 每次组件挂载时检查是否需要重置禁用状态（用于处理重新登录的情况）
  const disabledKey = `internshipAIEncouragementDisabled_${props.role}`
  if (localStorage.getItem(disabledKey) === 'true') {
    console.log('[FloatingAIBall] 检测到"不再提醒"已勾选，跳过显示')
    return
  }

  fetchEncouragement()

  encouragementTimeoutId1.value = setTimeout(() => {
    updateEncouragementBubblePos()
    showEncouragementBubble.value = true
    encouragementBubbleVisible.value = true
  }, 3000)

  // 每5秒切换一句鼓励语
  encouragementIntervalId.value = setInterval(() => {
    const currentIdx = encouragements.findIndex(e =>
      e.text === currentEncouragement.value.text && e.icon === currentEncouragement.value.icon
    )
    const nextIdx = (currentIdx + 1) % encouragements.length
    currentEncouragement.value = encouragements[nextIdx]
    showEncouragementBubble.value = false
    setTimeout(() => {
      showEncouragementBubble.value = true
    }, 100)
  }, 5000)

  // 15秒后自动隐藏
  encouragementTimeoutId2.value = setTimeout(() => {
    showEncouragementBubble.value = false
  }, 15000)
}

// 停止鼓励语定时器
const stopEncouragementCycle = () => {
  if (encouragementIntervalId.value) {
    clearInterval(encouragementIntervalId.value)
    encouragementIntervalId.value = null
  }
  if (encouragementTimeoutId1.value) {
    clearTimeout(encouragementTimeoutId1.value)
    encouragementTimeoutId1.value = null
  }
  if (encouragementTimeoutId2.value) {
    clearTimeout(encouragementTimeoutId2.value)
    encouragementTimeoutId2.value = null
  }
}

// 勾选"不再提醒"变化时处理
const onEncouragementDisabledChange = () => {
  const disabledKey = `internshipAIEncouragementDisabled_${props.role}`
  if (encouragementDisabled.value) {
    localStorage.setItem(disabledKey, 'true')
    showEncouragementBubble.value = false
    stopEncouragementCycle()
  } else {
    localStorage.removeItem(disabledKey)
    startEncouragementCycle()
  }
}

// ============ 悬浮球状态 ============
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

  onBallDrag = (e) => {
    if (!ballState.isDragging) return

    // 在 requestAnimationFrame 外部获取最新的鼠标坐标
    const currentX = e.clientX
    const currentY = e.clientY

    requestAnimationFrame(() => {
      ballState.hasDragged = true

      const newX = currentX - ballState.dragOffset.x
      const newY = currentY - ballState.dragOffset.y

      const ballSize = 60
      ballState.x = Math.max(0, Math.min(newX, window.innerWidth - ballSize))
      ballState.y = Math.max(0, Math.min(newY, window.innerHeight - ballSize))

      // 拖拽时更新气泡位置
      if (props.role === 'company') {
        updateCompanyBubblePos()
      } else if (props.role === 'teacher' || props.role === 'admin') {
        updateEncouragementBubblePos()
      } else if (props.role === 'student') {
        updateTipBubblePos()
      }
    })
  }

  stopBallDrag = () => {
    ballState.isDragging = false

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

// 悬浮球自动吸附到边缘（平滑过渡）
const snapBallToEdge = () => {
  const ballSize = 60
  const screenWidth = window.innerWidth
  const snapThreshold = 50

  if (ballState.x < snapThreshold) {
    ballState.x = 10
  } else if (ballState.x > screenWidth - ballSize - snapThreshold) {
    ballState.x = screenWidth - ballSize - 10
  }

  // 吸附后更新气泡位置
  if (props.role === 'company') {
    updateCompanyBubblePos()
  } else if (props.role === 'teacher' || props.role === 'admin') {
    updateEncouragementBubblePos()
  } else if (props.role === 'student') {
    updateTipBubblePos()
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

  onPanelDrag = (e) => {
    if (!panelState.isDragging) return

    requestAnimationFrame(() => {
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

  // 判断是从哪个角开始调整大小
  const handleEl = e.target.closest('.resize-handle')
  const handleClass = handleEl?.getAttribute('class') || ''
  const isLeftResize = handleClass.includes('resize-handle-left')

  const startX = e.clientX
  const startY = e.clientY
  const startWidth = panelState.width
  const startHeight = panelState.height
  const startXPosition = panelState.x

  handleResizeMove = (e) => {
    if (!panelState.isResizing) return

    requestAnimationFrame(() => {
      let newWidth, newHeight

      if (isLeftResize) {
        // 左下角调整：右侧固定不动，只有左侧边缘移动
        // 向左拖动：宽度增加（左侧向左移动）
        // 向右拖动：宽度减小（左侧向右移动）
        const deltaX = startX - e.clientX
        newWidth = startWidth + deltaX
        newHeight = startHeight + (e.clientY - startY)

        // 限制最小宽度
        newWidth = Math.max(newWidth, panelState.minWidth)
        newHeight = Math.max(newHeight, panelState.minHeight)

        // 限制最大宽度，不能超出屏幕右边界
        newWidth = Math.min(newWidth, window.innerWidth - startXPosition)

        // 限制高度
        newHeight = Math.min(newHeight, window.innerHeight - panelState.y)

        // 计算新的 x 位置，确保右侧边缘固定
        panelState.x = startXPosition + startWidth - newWidth
        panelState.width = newWidth
        panelState.height = newHeight
      } else {
        // 右下角调整：向右拖动时增加宽度
        newWidth = startWidth + (e.clientX - startX)
        newHeight = startHeight + (e.clientY - startY)

        // 限制最小尺寸
        newWidth = Math.max(newWidth, panelState.minWidth)
        newHeight = Math.max(newHeight, panelState.minHeight)

        // 限制最大尺寸，不能超出屏幕
        newWidth = Math.min(newWidth, window.innerWidth - panelState.x)
        newHeight = Math.min(newHeight, window.innerHeight - panelState.y)

        panelState.width = newWidth
        panelState.height = newHeight
      }
    })
  }

  handleResizeUp = () => {
    panelState.isResizing = false

    // 移除拖拽类，恢复 CSS transition
    document.body.classList.remove('panel-resizing')
    document.body.style.cursor = ''

    document.removeEventListener('mousemove', handleResizeMove)
    document.removeEventListener('mouseup', handleResizeUp)
  }

  document.addEventListener('mousemove', handleResizeMove)
  document.addEventListener('mouseup', handleResizeUp)
  document.body.style.cursor = isLeftResize ? 'nesw-resize' : 'nwse-resize'
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
  showModelDropdown.value = false
}

// 清除聊天历史
const clearHistory = () => {
  messages.value = []
  localStorage.removeItem(getStorageKey('internshipAIChatHistory'))
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
    sendMessage()
    return
  }

  messages.value.push(aiMessage)
  saveState()
  scrollToBottom()
}

// 跳转到职位浏览页面
const goToJobsPage = (job) => {
  if (!job || !job.id) {
    ElMessage.error('职位信息不完整，无法跳转')
    return
  }
  eventBus.emit('scrollToJob', { positionId: job.id })
  router.push({
    name: 'studentJobs',
    query: { positionId: job.id }
  })
}

// 收藏/取消收藏岗位
const toggleFavorite = async (job, message) => {
  if (!job || !job.id) {
    ElMessage.error('职位信息不完整')
    return
  }
  // 确保 positionId 是数字类型
  const positionId = Number(job.id)
  if (isNaN(positionId)) {
    ElMessage.error('职位ID无效')
    return
  }
  try {
    if (job.isFavorited) {
      await request.post(`/positions/favorite/${positionId}`)
      job.isFavorited = false
      ElMessage.success('已取消收藏')
      // 通知收藏状态变化
      eventBus.emit('favoriteChanged', { positionId, isFavorited: false })
    } else {
      const response = await request.post(`/positions/favorite/${positionId}`)
      job.isFavorited = true
      ElMessage.success('收藏成功')
      // 通知收藏状态变化
      eventBus.emit('favoriteChanged', { positionId, isFavorited: true })
    }
  } catch (error) {
    console.error('收藏操作失败:', error)
    ElMessage.error('操作失败，请重试')
  }
}

// 获取格式化的对话上下文（排除正在流式输出的消息）
const getFormattedContext = () => {
  return messages.value
    .filter(msg => !msg.isStreaming)
    .slice(-10)
    .map(msg => ({
      role: msg.role,
      content: msg.content
    }))
}

// 发送消息
let isSendingMessage = false // 防止重复发送标志
const sendMessage = async () => {
  const message = userInput.value.trim()
  if (!message || isLoading.value || isSendingMessage) return

  isSendingMessage = true
  const currentInput = message
  userInput.value = ''
  isLoading.value = true
  isGenerating.value = true
  abortController = new AbortController()

  // 1. 推入用户消息
  const userMessage = {
    role: 'user',
    content: currentInput,
    timestamp: new Date()
  }
  messages.value.push(userMessage)
  scrollToBottom()

  // 2. 立刻同步推入 AI 的占位气泡，确保马上显示三个点
  const aiMessageIndex = messages.value.length
  const aiMessage = {
    role: 'assistant',
    content: '',
    isStreaming: true,
    timestamp: new Date()
  }
  messages.value.push(aiMessage)
  scrollToBottom()

  try {
    await sendStreamingMessage(currentInput, aiMessageIndex)
  } catch (error) {
    // 只有非主动中断的错误才显示错误提示
    if (error.name !== 'AbortError') {
      console.error('发送消息失败:', error)
      messages.value[aiMessageIndex].content = '抱歉，服务暂时不可用，请稍后重试。'
      messages.value[aiMessageIndex].isStreaming = false
      saveState()
      ElMessage.error('请求失败，请检查网络连接或稍后重试')
    }
  } finally {
    messages.value[aiMessageIndex].isStreaming = false
    isLoading.value = false
    isGenerating.value = false
    isSendingMessage = false
    abortController = null
    saveState()
    nextTick(() => { if (chatInputRef.value) chatInputRef.value.style.height = 'auto' })
  }
}

// 停止 AI 生成
const stopGeneration = () => {
  if (abortController) {
    try {
      abortController.abort()
    } catch (e) {
      // 忽略已中止的错误
    }
  }
  isGenerating.value = false
  isLoading.value = false
  // 找到正在流式输出的消息并标记为停止
  const streamingMsgIndex = messages.value.findIndex(msg => msg.isStreaming)
  if (streamingMsgIndex !== -1) {
    messages.value[streamingMsgIndex].isStreaming = false
    saveState()
  }
  ElMessage.info('已停止生成')
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

// 高度自适应输入处理
const handleInput = () => {
  const el = chatInputRef.value
  if (!el) return
  el.style.height = 'auto'
  const newHeight = Math.min(el.scrollHeight, 120)
  el.style.height = `${newHeight}px`
}

// 发送流式消息 - 与AIChat.vue保持一致
const sendStreamingMessage = (message, aiMessageIndex) => {
  return new Promise((resolve, reject) => {
    const token = localStorage.getItem('token')
    let timeoutId = null

    // 设置超时
    timeoutId = setTimeout(() => {
      if (abortController) abortController.abort()
      ElMessage.warning('请求超时，请重试')
      reject(new Error('请求超时'))
    }, 60000)

    const headers = {
      'Content-Type': 'application/json'
    }
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    // 获取对话上下文（不包含刚添加的占位消息）
    const context = getFormattedContext()

    // 获取用户名（学生用户名为studentId）
    // 注意：学生角色的localStorage键使用 ROLE_STUDENT 而非 STUDENT
    const roleSuffix = props.role === 'student' ? 'ROLE_STUDENT' : props.role.toUpperCase()
    const usernameKey = props.role === 'student' ? 'studentId' : 'username'
    const username = localStorage.getItem(`${props.role}_${usernameKey}_${roleSuffix}`) ||
                     localStorage.getItem(`${props.role}_username_${roleSuffix}`) || ''

    fetch('/api/ai/chat/stream', {
      method: 'POST',
      headers: headers,
      body: JSON.stringify({
        message: message,
        context: context,
        model: selectedModel.value,
        role: props.role,
        username: username
      }),
      signal: abortController.signal
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
            // 流结束
            messages.value[aiMessageIndex].isStreaming = false
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
                  messages.value[aiMessageIndex].content += data.content
                } else if (data.type === 'job_recommendation') {
                  // 岗位推荐类型，直接保存完整数据
                  messages.value[aiMessageIndex].content = data.content || ''
                  messages.value[aiMessageIndex].type = 'job_recommendation'
                  messages.value[aiMessageIndex].jobData = data.data
                  messages.value[aiMessageIndex].isStreaming = false
                  saveState()
                  reader.cancel()
                  resolve()
                  return
                } else if (data.type === 'end') {
                  // 流结束时，content 已经通过 chunk 追加完毕
                  messages.value[aiMessageIndex].isStreaming = false
                  reader.cancel()
                  resolve()
                  return
                } else if (data.type === 'error') {
                  ElMessage.error(data.message || '生成过程中发生错误')
                  messages.value[aiMessageIndex].content = '抱歉，生成过程中发生错误。'
                  messages.value[aiMessageIndex].isStreaming = false
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
        // 忽略 AbortError（用户主动中断或超时导致）
        if (error.name === 'AbortError' || error.message?.includes('aborted')) {
          console.log('请求已中止')
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
      const scrollEl = messagesContainer.value.$el?.querySelector('.el-scrollbar__wrap')
      if (scrollEl) {
        scrollEl.scrollTop = scrollEl.scrollHeight
      } else if (messagesContainer.value.$el) {
        messagesContainer.value.$el.scrollTop = messagesContainer.value.$el.scrollHeight
      }
    }
  })
}

// 滚动到指定消息位置
const scrollToMessage = (index) => {
  nextTick(() => {
    if (messagesContainer.value) {
      const messagesEl = messagesContainer.value.$el || messagesContainer.value
      const messageItems = messagesEl.querySelectorAll('.message')
      if (messageItems[index]) {
        messageItems[index].scrollIntoView({ behavior: 'smooth', block: 'center' })
      } else {
        // 如果找不到，滚动到底部
        messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
      }
    }
  })
}

// 保存状态到本地存储
const saveState = () => {
  localStorage.setItem(getStorageKey('internshipAIBallPosition'), JSON.stringify({
    x: ballState.x,
    y: ballState.y
  }))

  localStorage.setItem(getStorageKey('internshipAIPanelState'), JSON.stringify({
    x: panelState.x,
    y: panelState.y,
    width: panelState.width,
    height: panelState.height
  }))

  const recentMessages = messages.value.slice(-20)
  localStorage.setItem(getStorageKey('internshipAIChatHistory'), JSON.stringify(recentMessages))
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

// 获取用户唯一标识（用于区分不同用户的聊天历史）
const getUserIdentifier = () => {
  // 优先使用用户ID，其次使用用户名
  const userId = localStorage.getItem('userId') || localStorage.getItem('studentId')
  const username = localStorage.getItem('username')
  return userId || username || 'anonymous'
}

// 获取带有用户标识的存储key
const getStorageKey = (prefix) => {
  return `${prefix}_${props.role}_${getUserIdentifier()}`
}

// 获取可用的AI模型列表
const fetchAvailableModels = async () => {
  try {
    const response = await request.get('/admin/ai-model/public/enabled')
    // 后端返回 { code, message, data } 结构
    // axios拦截器已返回 response.data，所以response直接就是Result对象
    if (response.data && Array.isArray(response.data)) {
      availableModels.value = response.data
      // 如果当前选中的模型不在列表中，切换到第一个
      if (availableModels.value.length > 0) {
        const hasCurrentModel = availableModels.value.some(m => m.modelCode === selectedModel.value)
        if (!hasCurrentModel) {
          selectedModel.value = availableModels.value[0].modelCode
        }
      }
    } else if (response.data && response.data.data && Array.isArray(response.data.data)) {
      // 兼容旧写法
      availableModels.value = response.data.data
      if (availableModels.value.length > 0) {
        const hasCurrentModel = availableModels.value.some(m => m.modelCode === selectedModel.value)
        if (!hasCurrentModel) {
          selectedModel.value = availableModels.value[0].modelCode
        }
      }
    }
  } catch (e) {
    console.warn('获取AI模型列表失败:', e)
    // 失败时使用默认模型
    availableModels.value = [
      { modelCode: 'deepseek-v4-flash', modelName: 'DeepSeek-V4-Flash' },
      { modelCode: 'deepseek-v4-pro', modelName: 'DeepSeek-V4-Pro' }
    ]
  }
}

// 组件挂载时加载历史数据
onMounted(() => {
  try {
    const savedBallPosition = localStorage.getItem(getStorageKey('internshipAIBallPosition'))
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

  const savedPanelState = localStorage.getItem(getStorageKey('internshipAIPanelState'))
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

  const savedMessages = localStorage.getItem(getStorageKey('internshipAIChatHistory'))
  if (savedMessages) {
    try {
      messages.value = JSON.parse(savedMessages)
    } catch (e) {
      console.warn('加载聊天历史失败:', e)
    }
  }

  // 企业端气泡消息初始化
  if (props.role === 'company') {
    // 每次组件挂载时检查是否需要重置关闭状态（用于处理重新登录的情况）
    const closedKey = `internshipAICompanyBubbleClosed_${props.role}`
    if (localStorage.getItem(closedKey) === 'true') {
      console.log('[FloatingAIBall] 检测到新会话，清除企业气泡关闭状态')
      localStorage.removeItem(closedKey)
    }
    // 获取统计数据（仅初始化时调用一次）
    fetchCompanyStats()
    // 注册WebSocket监听实时更新
    onCompanyTodoUpdate(handleCompanyTodoUpdate)
    // 延迟显示气泡，等悬浮球位置稳定后再显示
    setTimeout(() => {
      updateCompanyBubblePos()
      if (shouldShowCompanyBubble()) {
        showCompanyReminderBubble()
      }
    }, 1000)
  }

  // 学生端气泡初始化
  if (props.role === 'student') {
    // 每次组件挂载时检查是否需要重置关闭状态（用于处理重新登录的情况）
    const closedKey = `internshipAItipBubbleClosed_${props.role}`
    if (localStorage.getItem(closedKey) === 'true') {
      console.log('[FloatingAIBall] 检测到新会话，清除学生气泡关闭状态')
      localStorage.removeItem(closedKey)
    }
    setTimeout(() => {
      showTipBubbleTimer()
    }, 1000)
  }

  // 教师端/管理员端鼓励气泡初始化
  if (props.role === 'teacher' || props.role === 'admin') {
    // 每次组件挂载时检查是否需要重置禁用状态（用于处理重新登录的情况）
    const disabledKey = `internshipAIEncouragementDisabled_${props.role}`
    if (localStorage.getItem(disabledKey) === 'true') {
      console.log('[FloatingAIBall] 检测到新会话，清除鼓励气泡禁用状态')
      localStorage.removeItem(disabledKey)
    }
    // 启动鼓励语循环
    startEncouragementCycle()
  }

  // 获取可用的AI模型列表
  fetchAvailableModels()

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

  // 取消WebSocket企业待办更新监听
  offCompanyTodoUpdate(handleCompanyTodoUpdate)

  // 清理鼓励气泡定时器
  stopEncouragementCycle()

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

// 监听模型下拉框，显示时添加全局点击关闭
watch(showModelDropdown, (newVal) => {
  if (newVal) {
    setTimeout(() => {
      document.addEventListener('click', closeModelDropdownOnClickOutside)
    }, 0)
  } else {
    document.removeEventListener('click', closeModelDropdownOnClickOutside)
  }
})

// 监听面板位置变化，更新下拉框位置
watch([
  () => panelState.x,
  () => panelState.y
], () => {
  if (showModelDropdown.value) {
    // 强制更新下拉框位置
    const style = getModelDropdownStyle()
    const dropdown = document.querySelector('.model-select-dropdown')
    if (dropdown) {
      Object.assign(dropdown.style, style)
    }
  }
})

const closeModelDropdownOnClickOutside = (e) => {
  const wrapper = document.querySelector('.model-select-wrapper')
  if (wrapper && !wrapper.contains(e.target)) {
    showModelDropdown.value = false
  }
}
</script>

<style scoped>
/* ========== 毛玻璃效果 ========== */
.glass-effect {
  background: #FFFFFF;
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
}

/* ========== 悬浮球 ========== */
.floating-ai-ball {
  position: fixed;
  width: 60px;
  height: 60px;
  z-index: 10000;
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1),
              left 0.4s cubic-bezier(0.34, 1.56, 0.64, 1),
              top 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  will-change: transform, left, top;
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
  /* 柔和发光阴影 */
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.35),
              0 4px 12px rgba(82, 196, 26, 0.25),
              inset 0 -2px 6px rgba(0, 0, 0, 0.1),
              inset 0 2px 6px rgba(255, 255, 255, 0.25);
  transition: all 0.3s ease;
  user-select: none;
  animation: float 3s ease-in-out infinite;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

.ai-icon:hover {
  transform: scale(1.08);
  box-shadow: 0 12px 32px rgba(64, 158, 255, 0.45),
              0 6px 16px rgba(82, 196, 26, 0.35),
              inset 0 -2px 6px rgba(0, 0, 0, 0.1),
              inset 0 2px 6px rgba(255, 255, 255, 0.3);
}

/* ========== 聊天面板 ========== */
.chat-panel {
  position: fixed;
  z-index: 1000;
  border-radius: 20px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  will-change: transform;
  transition: none;
  background: #FFFFFF;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.12);
  resize: none !important;
  -webkit-resize: none !important;
  -moz-resize: none !important;
}

.chat-panel::-webkit-resizer {
  display: none;
}

/* ========== 聊天头部 ========== */
.chat-header {
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  color: white;
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: grab;
  user-select: none;
  position: relative;
  overflow: hidden;
  border-radius: 20px 20px 0 0;
}

.chat-header::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -50%;
  width: 100%;
  height: 100%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.15) 0%, transparent 70%);
  transform: rotate(30deg);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  position: relative;
  z-index: 1;
}

.ai-icon-small {
  width: 28px;
  height: 28px;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: bold;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  color: white;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  position: relative;
  z-index: 1001;
}

.model-select-wrapper {
  position: relative;
}

.model-select-trigger {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: #FFFFFF;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 12px;
  cursor: pointer;
  min-width: 120px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: all 0.2s;
}

.model-select-trigger:hover {
  background: rgba(255, 255, 255, 1);
}

.model-name {
  flex: 1;
  font-size: 11px;
  color: #333;
}

.model-arrow {
  font-size: 9px;
  color: #666;
}

.model-select-dropdown {
  position: fixed;
  background: #FFFFFF;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  border: 1px solid rgba(0, 0, 0, 0.06);
  z-index: 1002;
  min-width: 120px;
  overflow: hidden;
}

.model-option {
  padding: 8px 12px;
  cursor: pointer;
  font-size: 12px;
  color: #333;
  transition: all 0.15s;
}

.model-option:hover {
  background: rgba(64, 158, 255, 0.1);
}

.model-option.active {
  color: #409EFF;
  background: #f0f9ff;
  font-weight: 600;
}

.icon-btn {
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: white;
  font-size: 14px;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}

.icon-btn:hover {
  background: rgba(255, 255, 255, 0.35);
  transform: scale(1.05);
}

.close-btn {
  font-size: 16px;
  font-weight: 300;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  line-height: 1;
  padding: 0;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  background: rgba(255, 100, 100, 0.4);
}

/* ========== 消息区域 ========== */
.chat-messages {
  flex: 1;
  padding: 16px;
  padding-bottom: 80px;
  background: #FFFFFF;
}

.chat-messages :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
  overflow-y: auto;
  scroll-behavior: smooth;
}

/* 欢迎消息区域 */
.welcome-messages {
  padding-top: 8px;
}

.quick-suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
  padding-left: 4px;
}

.suggestion-capsule {
  border-radius: 20px;
  font-size: 12px;
  padding: 6px 14px;
  border: 1px solid rgba(64, 158, 255, 0.3);
  background: #FFFFFF;
  color: #409EFF;
  transition: all 0.2s;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.suggestion-capsule:hover:not(:disabled) {
  background: linear-gradient(135deg, #409EFF, #52c41a);
  color: white;
  border-color: transparent;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.25);
}

.suggestion-capsule:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 消息样式 */
.message {
  display: flex;
  margin-bottom: 14px;
  animation: fadeIn 0.3s ease-in;
}

.message.user {
  justify-content: flex-end;
}

.message.ai {
  justify-content: flex-start;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-content-container {
  max-width: 82%;
}

.message.user .message-content-container {
  display: flex;
  justify-content: flex-end;
}

.message.ai .message-content-container {
  display: flex;
  justify-content: flex-start;
}

.message-bubble {
  padding: 10px 14px;
  border-radius: 16px;
  position: relative;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  max-width: 100%;
  line-height: 1.5;
  min-height: 40px;
  display: flex;
  flex-direction: column;
  white-space: normal;
  text-align: left;
}

.message.user .message-bubble {
  background: linear-gradient(135deg, #409EFF, #53a0ff);
  color: white;
  border-bottom-right-radius: 6px;
}

.message.ai .message-bubble {
  background: #FFFFFF;
  color: #333;
  border-bottom-left-radius: 6px;
  border: 1px solid rgba(0, 0, 0, 0.06);
}

.message-text {
  line-height: 1.6;
  word-wrap: break-word;
  flex: 1;
  font-size: 14px;
}

.message-time {
  font-size: 11px;
  opacity: 0.6;
  margin-top: 4px;
}

.message.ai .message-time {
  color: #999;
}

/* 流式指示器样式 */
.streaming-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
}

.streaming-indicator span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #10a37f;
  animation: streaming 1.4s infinite ease-in-out;
}

.streaming-indicator span:nth-child(1) { animation-delay: -0.32s; }
.streaming-indicator span:nth-child(2) { animation-delay: -0.16s; }

@keyframes streaming {
  0%, 80%, 100% { transform: scale(0.8); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

/* 内嵌式 AI 思考呼吸圆点动画 */
.inline-typing-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 6px;
  height: 14px;
}

.ai-bubble:has(.inline-typing-indicator) {
  min-height: 40px;
}

.inline-typing-indicator .dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  animation: bounce-dot 1.4s infinite ease-in-out both;
}

.inline-typing-indicator .dot:nth-child(1) {
  background-color: #409EFF;
  animation-delay: -0.32s;
}
.inline-typing-indicator .dot:nth-child(2) {
  background-color: #67C23A;
  animation-delay: -0.16s;
}
.inline-typing-indicator .dot:nth-child(3) {
  background-color: #52c41a;
}

@keyframes bounce-dot {
  0%, 80%, 100% {
    transform: scale(0);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

/* ========== 底部输入区域 ========== */

/* 彻底隐形的外层包裹器 */
.chat-footer-wrapper {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 0 20px 24px 20px;
  background: transparent;
  border-top: none;
  pointer-events: none;
  z-index: 10;
}

/* 悬浮胶囊本体 */
.input-capsule {
  pointer-events: auto;
  background-color: #FFFFFF;
  border-radius: 28px;
  display: flex;
  align-items: flex-end;
  padding: 8px 10px 8px 18px;
  gap: 12px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
  border: 1px solid rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.3s ease, border-color 0.3s ease;
}

.input-capsule.is-focused {
  box-shadow: 0 6px 32px rgba(0, 0, 0, 0.12);
}

/* 自动伸缩的输入框 */
.grow-textarea {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  resize: none;
  font-size: 15px;
  line-height: 1.5;
  color: #333;
  font-family: inherit;
  font-weight: normal;
  padding: 7px 0;
  max-height: 120px;
  overflow-y: auto;
  margin-bottom: 2px;
}

.grow-textarea::-webkit-scrollbar { width: 4px; }
.grow-textarea::-webkit-scrollbar-thumb { background: #d1d5db; border-radius: 4px; }

/* 完美的圆形发送按钮 */
.send-btn {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background-color: #f3f4f6;
  color: #9ca3af;
  cursor: not-allowed;
  transition: all 0.25s cubic-bezier(0.2, 0, 0, 1);
  margin-bottom: 0px;
}

.send-btn svg {
  width: 18px;
  height: 18px;
}

/* 激活状态的按钮 */
.send-btn.is-active {
  background-color: #409EFF;
  color: #ffffff;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.35);
}

.send-btn.is-active:active {
  transform: scale(0.92);
}

.send-btn.loading {
  background-color: #409EFF;
  color: #ffffff;
  cursor: default;
}

/* 停止生成按钮 */
.send-btn.stop-btn {
  background-color: #fee2e2;
  color: #ef4444;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.2);
}

.send-btn.stop-btn:hover {
  background-color: #fecaca;
  box-shadow: 0 4px 16px rgba(239, 68, 68, 0.3);
}

.send-icon {
  font-size: 14px;
}

.loading-dots {
  font-size: 14px;
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* ========== 调整大小手柄 ========== */
.resize-handle {
  position: absolute;
  right: 2px;
  bottom: 2px;
  width: 50px;
  height: 35px;
  cursor: default;
  opacity: 0.4;
  transition: opacity 0.2s;
  z-index: 20;
  background: none;
  padding: 0;
  pointer-events: none;
}

.resize-handle-left {
  left: 2px;
  right: auto;
}

.resize-hitarea {
  position: absolute;
  right: 6px;
  bottom: 6px;
  width: 12px;
  height: 10px;
  z-index: 21;
  cursor: nwse-resize;
  pointer-events: auto;
}

.resize-handle-left .resize-hitarea {
  left: 6px;
  right: auto;
  cursor: nesw-resize;
}

.resize-handle:hover {
  opacity: 0.7;
}

.resize-svg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: block;
  pointer-events: none;
}

/* ========== 文本选择 ========== */
.chat-header, .resize-handle {
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

/* ========== 气泡样式 ========== */
.company-bubble,
.encouragement-bubble,
.tip-bubble {
  position: fixed;
  z-index: 9998;
  width: 220px;
  border-radius: 20px;
  padding: 14px 16px;
  animation: bubble-pop 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  background: linear-gradient(135deg, #ffffff 0%, #f0fff4 50%, #e8f8f0 100%);
  border: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow: 0 8px 32px rgba(64, 158, 255, 0.15), 0 2px 8px rgba(82, 196, 26, 0.1);
}

.company-bubble-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.company-bubble-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.company-bubble-icon {
  font-size: 18px;
}

.company-bubble-title {
  font-size: 14px;
  font-weight: 600;
  color: #2E7D32;
}

.company-bubble-divider {
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(200, 230, 201, 0.8), transparent);
  margin: 10px 0;
}

.company-bubble-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.company-bubble-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #606266;
}

.company-bubble-badge {
  color: #ff7eb3;
  font-size: 14px;
  font-weight: 600;
}

.badge-number {
  display: inline-block;
}

@keyframes badge-pop {
  0% { transform: scale(1); }
  40% { transform: scale(1.2); color: #ff6b9d; }
  70% { transform: scale(0.95); }
  100% { transform: scale(1); }
}

.company-bubble-empty {
  font-size: 13px;
  color: #909399;
}

.company-bubble-close,
.tip-bubble-close {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.06);
  border: none;
  color: #909399;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  line-height: 1;
  flex-shrink: 0;
  transition: all 0.2s;
}

.company-bubble-close:hover,
.tip-bubble-close:hover {
  background: rgba(245, 108, 108, 0.15);
  color: #f56c6c;
}

/* 入场动画 */
@keyframes bubble-pop {
  0% {
    opacity: 0;
    transform: scale(0.85) translateX(15px);
  }
  60% {
    opacity: 1;
    transform: scale(1.05) translateX(-2px);
  }
  100% {
    opacity: 1;
    transform: scale(1) translateX(0);
  }
}

/* 气泡消失动画 */
.bubble-pop-leave-active {
  transition: all 0.25s cubic-bezier(0.4, 0, 1, 1);
}

.bubble-pop-leave-to {
  opacity: 0;
  transform: scale(0.85) translateX(10px);
}

.tip-bubble {
  width: 220px;
}

.tip-bubble-inner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-right: 24px;
}

.tip-bubble-icon {
  font-size: 22px;
  flex-shrink: 0;
}

.tip-bubble-text {
  font-size: 13px;
  color: #2E7D32;
  line-height: 1.5;
}

.tip-bubble-close {
  position: absolute;
  top: 8px;
  right: 8px;
}

.encouragement-bubble {
  width: 220px;
}

.encouragement-bubble-inner {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.encouragement-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.encouragement-text {
  font-size: 14px;
  color: #2E7D32;
  line-height: 1.5;
  flex: 1;
}

.encouragement-bubble-footer {
  display: flex;
  justify-content: flex-end;
}

.encouragement-checkbox {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #999;
  cursor: pointer;
}

.encouragement-checkbox input {
  cursor: pointer;
}

/* ========== 岗位推荐卡片样式 ========== */
.job-recommendation {
  padding: 8px 0;
}

.job-recommendation-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.job-icon {
  font-size: 16px;
}

.job-cards-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.job-card-compact {
  background: #FFFFFF;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 12px;
  padding: 12px;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.job-card-compact:hover {
  border-color: #409EFF;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.12);
}

.job-row1 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-bottom: 6px;
}

.job-name-compact {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.job-salary-compact {
  font-size: 12px;
  color: #22c55e;
  font-weight: 600;
}

.job-row2, .job-row3 {
  display: flex;
  gap: 8px;
  font-size: 11px;
  color: #999;
  margin-bottom: 4px;
}

.job-company-compact,
.job-location-compact,
.job-contact-compact,
.job-phone-compact {
  display: flex;
  align-items: center;
  gap: 2px;
}

.job-row4 {
  display: flex;
  gap: 4px;
  margin-top: 8px;
}

.action-btn {
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 10px;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn.favorite {
  background: #fff;
  border: 1px solid #E6A23C;
  color: #E6A23C;
}

.action-btn.favorite:hover {
  background: #E6A23C;
  color: #fff;
}

.action-btn.favorited {
  background: #E6A23C;
  border: 1px solid #E6A23C;
  color: #fff;
}

.action-btn.go-apply {
  background: linear-gradient(135deg, #409EFF, #53a0ff);
  color: #fff;
  flex: 1;
}

.action-btn.go-apply:hover {
  filter: brightness(1.1);
}

.job-empty {
  text-align: center;
  padding: 16px;
  color: #999;
  font-size: 12px;
}
</style>
