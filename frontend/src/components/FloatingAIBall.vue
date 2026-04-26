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
      class="company-bubble"
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
        <div class="company-bubble-item" v-if="pendingApplications > 0">
          <span class="company-bubble-badge">
            <span class="badge-number">{{ pendingApplications }}</span>
          </span>
          <span>个岗位申请待处理</span>
        </div>
        <div class="company-bubble-item" v-if="pendingConfirmations > 0">
          <span class="company-bubble-badge">
            <span class="badge-number">{{ pendingConfirmations }}</span>
          </span>
          <span>个实习确认表待确认</span>
        </div>
        <div class="company-bubble-empty" v-if="pendingApplications === 0 && pendingConfirmations === 0">
          <span>暂无待办事项</span>
        </div>
      </div>
    </div>
  </Transition>

  <!-- 教师端/管理员端鼓励气泡 -->
  <Transition name="bubble-pop">
    <div
      v-if="showEncouragementBubble && encouragementBubbleVisible && (props.role === 'teacher' || props.role === 'admin')"
      class="encouragement-bubble"
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
      class="tip-bubble"
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
        <div class="model-select-wrapper" style="position: relative; margin-right: 8px;">
          <div
            class="model-select-trigger"
            @click.stop="toggleModelDropdown"
            style="display: flex; align-items: center; gap: 4px; padding: 5px 10px; background: rgba(255,255,255,0.9); border-radius: 4px; cursor: pointer; min-width: 150px;"
          >
            <span style="flex: 1; font-size: 12px; color: #333;">{{ getCurrentModelName() }}</span>
            <span style="font-size: 10px; color: #666;">▼</span>
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
                :style="{
                  padding: '8px 12px',
                  cursor: 'pointer',
                  fontSize: '12px',
                  color: model.modelCode === selectedModel ? '#409EFF' : '#333',
                  background: model.modelCode === selectedModel ? '#f0f9ff' : 'white',
                  fontWeight: model.modelCode === selectedModel ? 'bold' : 'normal'
                }"
              >
                {{ model.modelName }}
              </div>
            </div>
          </Teleport>
        </div>
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
        <textarea
          v-model="userInput"
          placeholder="请输入您的问题..."
          @keydown.enter.exact.prevent="sendMessage"
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
import { Promotion } from '@element-plus/icons-vue'
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
const streamingMessage = ref('') // 当前流式回复的内容

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
  const bubbleWidth = 240
  const bubbleHeight = 100
  const gap = 15
  const screenHeight = window.innerHeight

  let left = ballState.x - bubbleWidth - gap
  let top = ballState.y + (60 - bubbleHeight) / 2

  if (top < 10) top = 10
  if (top + bubbleHeight > screenHeight - 10) top = screenHeight - bubbleHeight - 10
  if (left < 10) left = ballState.x + 60 + gap

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
  const bubbleWidth = 240
  const bubbleHeight = 80
  const gap = 15
  const screenHeight = window.innerHeight

  let left = ballState.x - bubbleWidth - gap
  let top = ballState.y + (60 - bubbleHeight) / 2

  if (top < 10) top = 10
  if (top + bubbleHeight > screenHeight - 10) top = screenHeight - bubbleHeight - 10
  if (left < 10) left = ballState.x + 60 + gap

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
  const bubbleWidth = 220
  const bubbleHeight = 80
  const gap = 15
  const screenHeight = window.innerHeight

  let left = ballState.x - bubbleWidth - gap
  let top = ballState.y + (60 - bubbleHeight) / 2

  if (top < 10) top = 10
  if (top + bubbleHeight > screenHeight - 10) top = screenHeight - bubbleHeight - 10
  if (left < 10) left = ballState.x + 60 + gap

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

    requestAnimationFrame(() => {
      ballState.hasDragged = true

      const newX = e.clientX - ballState.dragOffset.x
      const newY = e.clientY - ballState.dragOffset.y

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
  streamingMessage.value = ''
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
  const userMessageIndex = messages.value.length
  messages.value.push(userMessage)
  saveState()
  // 用户发消息时滚动到该消息位置
  scrollToMessage(userMessageIndex)

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
    saveState()
    // 错误回复时不自动滚动，让用户自由滚动
    ElMessage.error('请求失败，请检查网络连接或稍后重试')
  } finally {
    isLoading.value = false
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
                  // 流式过程中不自动滚动，让用户自由滚动查看
                } else if (data.type === 'job_recommendation') {
                  // 岗位推荐类型，直接保存完整数据
                  const aiMessage = {
                    role: 'assistant',
                    content: data.content || '',
                    type: 'job_recommendation',
                    jobData: data.data,
                    timestamp: new Date()
                  }
                  messages.value.push(aiMessage)
                  saveState()
                  streamingMessage.value = ''
                  isLoading.value = false
                  reader.cancel()
                  resolve()
                  return
                } else if (data.type === 'end') {
                  // 流结束时，保存原始内容，渲染时再格式化
                  const aiMessage = {
                    role: 'assistant',
                    content: streamingMessage.value, // 保存原始内容，不格式化
                    timestamp: new Date()
                  }
                  messages.value.push(aiMessage)
                  saveState()
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
  z-index: 1000;
  background: white;
  border-radius: 24px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transform-origin: center center;
  /* 改用 transform 替代 width/height 变化以获得更好的性能 */
  will-change: transform;
  /* 禁用所有过渡以确保拖拽/调整大小时的即时响应 */
  transition: none;
}

/* 隐藏浏览器原生的 resize 功能和图标 */
.chat-panel {
  resize: none !important;
  -webkit-resize: none !important;
  -moz-resize: none !important;
  resize: none !important;
}

.chat-panel::-webkit-resizer {
  display: none;
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
  z-index: 1001;
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
  max-width: 100%;
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
  gap: 16px;
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
  gap: 16px;
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

/* 调整大小手柄 - 保持视觉大小，但禁用自身事件 */
.resize-handle {
  position: absolute;
  right: 2px;
  bottom: 2px;
  width: 50px;
  height: 35px;
  cursor: default;
  opacity: 0.5;
  transition: opacity 0.2s;
  z-index: 20;
  background: none;
  padding: 0;
  pointer-events: none;
}

/* 左下角调整大小手柄 */
.resize-handle-left {
  left: 2px;
  right: auto;
  cursor: default;
}

/* 小触发区域 - 只有这一小块响应拖拽 */
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

/* 左下角触发区域 */
.resize-handle-left .resize-hitarea {
  left: 6px;
  right: auto;
  cursor: nesw-resize;
}

/* 左下角调整大小手柄 */
.resize-handle-left {
  left: 2px;
  right: auto;
  cursor: nesw-resize;
}

.resize-handle:hover {
  opacity: 0.85;
}

.resize-svg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: block;
  pointer-events: none;
  opacity: 0.5;
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

/* 企业端待办气泡样式 */
.company-bubble {
  position: fixed;
  z-index: 9998;
  width: 240px;
  background: linear-gradient(135deg, #FFFDF7 0%, #E8F5E9 100%);
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(46, 125, 50, 0.15);
  padding: 16px;
  animation: company-bubble-pop 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
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
  font-size: 20px;
}

.company-bubble-title {
  font-size: 14px;
  font-weight: 600;
  color: #2E7D32;
}

.company-bubble-divider {
  height: 1px;
  background: linear-gradient(90deg, transparent, #c8e6c9, transparent);
  margin: 10px 0;
}

.company-bubble-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
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
  font-size: 15px;
  font-weight: 600;
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
}

.badge-number {
  display: inline-block;
  transition: all 0.3s ease;
}

.badge-flash {
  animation: badge-pop 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes badge-pop {
  0% { transform: scale(1); }
  40% { transform: scale(1.25); color: #ff6b9d; }
  70% { transform: scale(0.95); }
  100% { transform: scale(1); }
}

.company-bubble-flash {
  animation: bubble-glow 0.5s ease-out;
}

@keyframes bubble-glow {
  0% { box-shadow: 0 4px 20px rgba(46, 125, 50, 0.15); transform: scale(1); }
  35% { box-shadow: 0 5px 25px rgba(46, 125, 50, 0.25); transform: scale(1.015); }
  100% { box-shadow: 0 4px 20px rgba(46, 125, 50, 0.15); transform: scale(1); }
}

.company-bubble-empty {
  font-size: 13px;
  color: #909399;
}

.company-bubble-close {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #f5f7fa;
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

.company-bubble-close:hover {
  background: #fee;
  color: #f56c6c;
}

/* 学生端气泡样式 - 改为和企业端一致 */
.tip-bubble {
  position: fixed;
  z-index: 9998;
  width: 240px;
  background: linear-gradient(135deg, #FFFDF7 0%, #E8F5E9 100%);
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(46, 125, 50, 0.15);
  padding: 16px;
  animation: company-bubble-pop 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.tip-bubble-inner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-right: 20px;
}

.tip-bubble-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.tip-bubble-text {
  font-size: 14px;
  color: #2E7D32;
  line-height: 1.5;
}

.tip-bubble-close {
  position: absolute;
  top: 6px;
  right: 8px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.06);
  border: none;
  color: #909399;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  line-height: 1;
  transition: all 0.2s;
}

.tip-bubble-close:hover {
  background: rgba(245, 108, 108, 0.15);
  color: #f56c6c;
}

/* 入场动画 */
@keyframes company-bubble-pop {
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

/* 鼓励气泡样式 - 薄荷绿+奶油白配色 */
.encouragement-bubble {
  position: fixed;
  z-index: 9998;
  width: 220px;
  background: linear-gradient(135deg, #FFFDF7 0%, #E8F5E9 100%);
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(46, 125, 50, 0.15);
  padding: 16px;
  animation: company-bubble-pop 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.encouragement-bubble-inner {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.encouragement-icon {
  font-size: 28px;
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
  font-size: 12px;
  color: #999;
  cursor: pointer;
}

.encouragement-checkbox input {
  cursor: pointer;
}

/* 岗位推荐卡片样式 */
.job-recommendation {
  padding: 10px 0;
}

.job-recommendation-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #eee;
}

.job-icon {
  font-size: 18px;
}

.job-cards-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.job-card-compact {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 10px;
  padding: 14px 16px;
  transition: all 0.2s;
}

.job-card-compact:hover {
  border-color: #409EFF;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.12);
}

.job-row1 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.job-name-compact {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.job-salary-compact {
  font-size: 13px;
  color: #22c55e;
  font-weight: 600;
}

.job-row2 {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
}

.job-company-compact {
  font-size: 12px;
  color: #666;
}

.job-row3 {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #999;
  margin-bottom: 8px;
}

.job-location-compact,
.job-contact-compact,
.job-phone-compact {
  display: flex;
  align-items: center;
  gap: 2px;
}

.job-row4 {
  display: flex;
  gap: 6px;
}

.action-btn {
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 11px;
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
  background: #409EFF;
  color: #fff;
  flex: 1;
}

.action-btn.go-apply:hover {
  background: #66b1ff;
}

.job-empty {
  text-align: center;
  padding: 20px;
  color: #999;
  font-size: 13px;
}
</style>
