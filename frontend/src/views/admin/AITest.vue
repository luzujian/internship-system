<template>
  <div class="ai-test-container">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">AI测试</h1>
        <p class="page-description">测试AI模型的稳定性和响应能力，验证模型切换是否正确</p>
      </div>
      <div class="header-illustration">
        <div class="illustration-circle circle-1"></div>
        <div class="illustration-circle circle-2"></div>
      </div>
    </div>

    <div class="test-main-container">
      <div class="test-card">
        <div class="test-header">
          <div class="model-info">
            <span class="info-label">当前模型</span>
            <el-tag type="primary" size="small">{{ currentModelName }}</el-tag>
            <el-tag v-if="currentModelStatus" :type="currentModelStatus === '启用' ? 'success' : 'danger'" size="small" class="status-tag">
              {{ currentModelStatus }}
            </el-tag>
          </div>
          <div class="model-actions">
            <el-button @click="refreshModelInfo" :loading="refreshing" size="default" class="action-btn">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
            <el-button @click="showModelSelector = true" size="default" type="primary" class="action-btn primary-btn">
              <el-icon><Setting /></el-icon>
              切换模型
            </el-button>
          </div>
        </div>

        <el-tabs v-model="activeTab" class="ai-tabs" @tab-change="handleTabChange">
          <el-tab-pane label="AI测试对话" name="chat">
            <el-scrollbar height="520px" ref="messagesScrollbar" @scroll="handleScroll">
              <div class="messages-container" @click="handleMessageContainerClick">
                <div v-if="!autoScrollEnabled && streamingMessage" class="auto-scroll-hint" @click="enableAutoScroll">
                  <el-icon><Bottom /></el-icon>
                  <span>点击恢复自动滚动</span>
                </div>
                <div v-if="messages.length === 0" class="welcome-section">
                  <div class="welcome-icon">
                    <el-icon><ChatDotRound /></el-icon>
                  </div>
                  <h3>欢迎使用AI测试工具</h3>
                  <p>与AI进行对话交流，测试模型的稳定性和响应能力</p>
                  <div class="test-features">
                    <div class="feature-item">
                      <div class="feature-icon">
                        <el-icon><Timer /></el-icon>
                      </div>
                      <span>测试模型响应速度</span>
                    </div>
                    <div class="feature-item">
                      <div class="feature-icon">
                        <el-icon><Switch /></el-icon>
                      </div>
                      <span>验证模型切换功能</span>
                    </div>
                    <div class="feature-item">
                      <div class="feature-icon">
                        <el-icon><CircleCheck /></el-icon>
                      </div>
                      <span>检查模型稳定性</span>
                    </div>
                  </div>
                  <div class="quick-suggestions">
                    <div class="suggestion-title">快速测试</div>
                    <div class="suggestion-buttons">
                      <el-button 
                        v-for="(question, index) in testQuestions" 
                        :key="index"
                        @click="selectTestQuestion(question)" 
                        :disabled="loading" 
                        class="suggestion-btn"
                        size="default"
                      >
                        {{ question }}
                      </el-button>
                    </div>
                  </div>
                </div>

                <div v-for="(message, index) in messages" :key="index" :class="['message', message.role]" :data-role="message.role">
                  <div class="message-content">
                    <div class="message-bubble">
                      <div class="message-text" v-html="formatMessage(message.content)"></div>
                      <div class="message-meta">
                        <div class="message-time">{{ formatTime(message.timestamp) }}</div>
                        <div v-if="message.responseTime" class="response-time">
                          <el-icon><Timer /></el-icon>
                          <span>{{ message.responseTime }}ms</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <div v-if="streamingMessage" class="message ai streaming">
                  <div class="message-content">
                    <div class="message-bubble">
                      <div class="message-text" v-html="formatMessage(streamingMessage)"></div>
                      <div class="streaming-indicator active">
                        <span></span>
                        <span></span>
                        <span></span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </el-scrollbar>

            <div class="input-container">
              <div class="input-wrapper">
                <div class="input-actions">
                  <el-tooltip content="清空对话" placement="top">
                    <el-button 
                      @click="clearMessages" 
                      :disabled="messages.length === 0 || loading" 
                      size="default" 
                      class="action-btn"
                    >
                      <el-icon><Delete /></el-icon>
                      清空
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="导出测试记录" placement="top">
                    <el-button 
                      @click="exportTestLog" 
                      :disabled="messages.length === 0" 
                      size="default" 
                      class="action-btn"
                    >
                      <el-icon><Download /></el-icon>
                      导出
                    </el-button>
                  </el-tooltip>
                </div>
                  
                <div class="textarea-with-button">
                  <el-input 
                    v-model="inputMessage" 
                    type="textarea" 
                    :rows="3" 
                    placeholder="请输入测试问题..." 
                    :disabled="loading" 
                    @keydown.enter.exact.prevent="sendMessage" 
                    class="message-input" 
                    maxlength="2000"
                    show-word-limit
                  />
                  <el-button 
                    type="primary" 
                    @click="sendMessage" 
                    :loading="loading" 
                    :disabled="!inputMessage.trim()" 
                    class="send-btn"
                    size="large"
                  >
                    <el-icon v-if="!loading"><Promotion /></el-icon>
                    <span v-else>测试中</span>
                  </el-button>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="实习心得分析" name="reflection">
            <div class="reflection-tab-content">
              <div v-if="!analysisResult" class="reflection-upload-section">
                <div class="upload-intro">
                  <div class="intro-left">
                    <div class="intro-icon">
                      <el-icon><Document /></el-icon>
                    </div>
                    <h3>实习心得AI分析</h3>
                    <p>上传学生实习心得文档，AI将自动分析并生成评价报告</p>
                    <div class="feature-list">
                      <div class="feature-item">
                        <div class="feature-icon">
                          <el-icon><CircleCheck /></el-icon>
                        </div>
                        <span>智能提取关键词</span>
                      </div>
                      <div class="feature-item">
                        <div class="feature-icon">
                          <el-icon><CircleCheck /></el-icon>
                        </div>
                        <span>多维度能力评估</span>
                      </div>
                      <div class="feature-item">
                        <div class="feature-icon">
                          <el-icon><CircleCheck /></el-icon>
                        </div>
                        <span>专业评价建议</span>
                      </div>
                      <div class="feature-item">
                        <div class="feature-icon">
                          <el-icon><CircleCheck /></el-icon>
                        </div>
                        <span>综合评分评级</span>
                      </div>
                    </div>
                  </div>

                  <div class="intro-right">
                    <el-form label-width="100px" class="upload-form">
                      <el-form-item label="上传文档" required>
                        <el-upload
                          ref="uploadRef"
                          class="reflection-upload"
                          drag
                          :auto-upload="false"
                          :limit="1"
                          :on-change="handleFileChange"
                          :on-exceed="handleExceed"
                          :on-remove="handleFileRemove"
                          accept=".doc,.docx,.pdf,.txt"
                        >
                          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                          <div class="el-upload__text">
                            将文件拖到此处，或<em>点击上传</em>
                          </div>
                          <template #tip>
                            <div class="el-upload__tip">
                              支持 .doc, .docx, .pdf, .txt 格式，文件大小不超过10MB
                            </div>
                          </template>
                        </el-upload>
                      </el-form-item>

                      <el-form-item>
                        <el-button 
                          type="primary" 
                          @click="analyzeReflection" 
                          :loading="analyzing" 
                          :disabled="!uploadedFile"
                          size="large"
                          class="analyze-btn"
                        >
                          <el-icon v-if="!analyzing"><MagicStick /></el-icon>
                          <span v-else>分析中...</span>
                          开始分析
                        </el-button>
                      </el-form-item>
                    </el-form>
                  </div>
                </div>
              </div>

              <div v-else class="reflection-result-section">
                <div class="result-container">
                  <div class="result-header">
                    <h3>分析结果</h3>
                    <div class="header-actions">
                      <el-button @click="downloadPDF" type="success" size="default">
                        <el-icon><Download /></el-icon>
                        下载PDF
                      </el-button>
                      <el-button @click="resetAnalysis" size="default">
                        <el-icon><Refresh /></el-icon>
                        重置
                      </el-button>
                    </div>
                  </div>
                  
                  <div class="divider-line"></div>
                  
                  <div class="analysis-result">
                  <div class="result-section">
                    <h4>匹配的关键词</h4>
                    <div class="keywords-container">
                      <el-tag 
                        v-for="(keyword, index) in analysisResult.keywordStats?.matchedKeywords" 
                        :key="index"
                        type="success"
                        size="small"
                        class="keyword-tag"
                      >
                        {{ keyword }}
                      </el-tag>
                      <div v-if="!analysisResult.keywordStats?.matchedKeywords || analysisResult.keywordStats.matchedKeywords.length === 0" class="no-keywords">
                        暂无匹配的关键词
                      </div>
                    </div>
                  </div>
                  
                  <div class="result-section">
                    <h4>评分</h4>
                    <div class="score-display">
                      <span class="score-value">{{ analysisResult.weightedScore || analysisResult.score }}</span>
                      <el-tag :type="getScoreTagType(analysisResult.weightedScore || analysisResult.score)" size="large">
                        {{ analysisResult.rating }}
                      </el-tag>
                    </div>
                    <div v-if="analysisResult.weightedScore && analysisResult.weightedScore !== analysisResult.score" class="weighted-score-info">
                      <span class="weighted-score-label">加权分数</span>
                      <span class="weighted-score-value">{{ analysisResult.weightedScore }}</span>
                      <span class="original-score-label">（原始分数：{{ analysisResult.score }}）</span>
                    </div>
                  </div>
                  
                  <div class="result-section">
                    <h4>能力维度评分</h4>
                    <div class="aspects-container">
                      <div v-for="(value, key) in analysisResult.aspects" :key="key" class="aspect-item">
                        <span class="aspect-label">{{ getAspectLabel(key) }}</span>
                        <el-progress :percentage="value" :color="getProgressColor(value)" />
                      </div>
                    </div>
                  </div>
                  
                  <div class="result-section">
                    <h4>心得总结</h4>
                    <p class="summary-text">{{ analysisResult.summary }}</p>
                  </div>
                  
                  <div class="result-section">
                    <h4>亮点</h4>
                    <ul class="highlight-list">
                      <li v-for="(highlight, index) in analysisResult.highlights" :key="index">
                        {{ highlight }}
                      </li>
                    </ul>
                  </div>
                  
                  <div class="result-section">
                    <h4>改进建议</h4>
                    <ul class="improvement-list">
                      <li v-for="(improvement, index) in analysisResult.improvements" :key="index">
                        {{ improvement }}
                      </li>
                    </ul>
                  </div>
                  
                  <div class="result-section">
                    <h4>综合评语</h4>
                    <p class="comment-text">{{ analysisResult.comment }}</p>
                  </div>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <div class="test-stats-card">
        <div class="stats-header">
          <h3>测试统计</h3>
          <div class="stats-icon">
            <el-icon><DataLine /></el-icon>
          </div>
        </div>
        <div class="stats-content">
          <div class="stat-item">
            <div class="stat-label">测试次数</div>
            <div class="stat-value">{{ testStats.totalTests }}</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">成功次数</div>
            <div class="stat-value success">{{ testStats.successTests }}</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">失败次数</div>
            <div class="stat-value error">{{ testStats.failedTests }}</div>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <div class="stat-label">平均响应</div>
            <div class="stat-value">{{ testStats.avgResponseTime }}ms</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">最快响应</div>
            <div class="stat-value">{{ testStats.minResponseTime }}ms</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">最慢响应</div>
            <div class="stat-value">{{ testStats.maxResponseTime }}ms</div>
          </div>
        </div>
      </div>
    </div>
  </div>

    <el-dialog v-model="showModelSelector" title="选择AI模型" width="600px" class="model-selector-dialog">
      <el-scrollbar height="400px">
        <div class="model-list">
          <div 
            v-for="model in enabledModels" 
            :key="model.id"
            :class="['model-item', { 'selected': selectedModelRow && selectedModelRow.id === model.id }]"
            @click="selectModel(model)"
          >
            <div class="model-item-content">
              <div class="model-name">{{ formatModelCode(model.modelCode) }}</div>
              <div class="model-code">{{ model.modelName }}</div>
              <el-tag :type="getProviderTagType(model.provider)" size="small" effect="plain">
                {{ getProviderName(model.provider) }}
              </el-tag>
              <el-tag v-if="model.isDefault === 1" type="warning" size="small" effect="plain">
                默认
              </el-tag>
            </div>
          </div>
        </div>
      </el-scrollbar>
      <template #footer>
        <el-button @click="showModelSelector = false">取消</el-button>
        <el-button type="primary" @click="confirmModelSelection" :disabled="!selectedModelRow">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog 
      v-model="analysisProgressVisible" 
      title="正在生成总结报告" 
      width="450px" 
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
      class="analysis-progress-dialog"
    >
      <div class="progress-content">
        <div class="progress-icon">
          <el-icon class="is-loading"><MagicStick /></el-icon>
        </div>
        <div class="progress-text">AI正在分析文档并生成报告，请稍候...</div>
        <div class="progress-hint">
          <el-icon><InfoFilled /></el-icon>
          <span>AI评分需要对实习心得进行深度语义分析、关键词提取、情感判断等多维度处理，请耐心等待...</span>
        </div>
      </div>
    </el-dialog>
</template>

<script lang="ts" setup>
import logger from '@/utils/logger'
import { ref, reactive, onMounted, nextTick, computed } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { Delete, Promotion, Refresh, Setting, Download, Timer, ChatDotRound, Switch, CircleCheck, DataLine, Bottom, Document, MagicStick, UploadFilled, Loading, InfoFilled } from '@element-plus/icons-vue'
import aiModelService from '@/api/aiModel'
import request from '@/utils/request'
import html2pdf from 'html2pdf.js/dist/html2pdf.bundle.min.js'
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()

const inputMessage = ref('')
const loading = ref(false)
const messagesScrollbar = ref(null)
const streamingMessage = ref('')
const refreshing = ref(false)
const showModelSelector = ref(false)
const selectedModelRow = ref(null)
const enabledModels = ref([])
const currentModel = ref(null)
const isUserScrolling = ref(false)
const autoScrollEnabled = ref(true)

const messages = ref([])

const testStats = reactive({
  totalTests: 0,
  successTests: 0,
  failedTests: 0,
  avgResponseTime: 0,
  minResponseTime: Infinity,
  maxResponseTime: 0,
  responseTimes: []
})

const testQuestions = reactive([
  '你好，请介绍一下自己',
  '怎么测试模型响应速度'
])

const activeTab = ref('chat')
const analyzing = ref(false)
const uploadRef = ref(null)
const uploadedFile = ref(null)
const analysisResult = ref(null)
const analysisProgressVisible = ref(false)
// categoryWeights: 存储类别中文名称（用于UI显示）
const categoryWeights = ref({})
// categoryWeightValues: 存储类别权重值（用于传递给后端进行加权计算）
const categoryWeightValues = ref<Record<string, number>>({})

const currentModelName = computed(() => {
  if (!currentModel.value) return '未选择模型'
  return formatModelCode(currentModel.value.modelCode)
})

const currentModelStatus = computed(() => {
  return currentModel.value ? (currentModel.value.status === 1 ? '启用' : '禁用') : ''
})

const refreshModelInfo = async () => {
  refreshing.value = true
  try {
    const response = await aiModelService.getEnabledAIModels()
    if (response.code === 200) {
      enabledModels.value = response.data || []
      const defaultModel = enabledModels.value.find(m => m.isDefault === 1)
      if (defaultModel) {
        currentModel.value = defaultModel
      } else if (enabledModels.value.length > 0) {
        currentModel.value = enabledModels.value[0]
      }
    } else {
      ElMessage.error(response.message || '刷新模型信息失败')
    }
  } catch (error) {
    ElMessage.error('刷新模型信息失败')
  } finally {
    refreshing.value = false
  }
}

const selectModel = (row) => {
  selectedModelRow.value = row
}

const confirmModelSelection = () => {
  if (selectedModelRow.value) {
    currentModel.value = selectedModelRow.value
    showModelSelector.value = false
    ElMessage.success(`已切换到模型：${selectedModelRow.value.modelName}`)
  }
}

const sendMessage = async () => {
  if (!inputMessage.value.trim() || loading.value) return

  if (!currentModel.value) {
    ElMessage.warning('请先选择AI模型')
    showModelSelector.value = true
    return
  }

  const userMessage = {
    role: 'user',
    content: inputMessage.value.trim(),
    timestamp: new Date()
  }

  messages.value.push(userMessage)
  saveChatMessages()
  const currentInput = inputMessage.value
  inputMessage.value = ''
  loading.value = true
  streamingMessage.value = ''

  const startTime = Date.now()
  testStats.totalTests++

  scrollToBottom()

  try {
    await sendStreamingMessage(currentInput)
    const endTime = Date.now()
    const responseTime = endTime - startTime
    
    updateTestStats(responseTime, true)
    
    ElNotification({
      title: '测试成功',
      message: `响应时间：${responseTime}ms`,
      type: 'success',
      duration: 2000
    })
  } catch (error) {
    logger.error('发送消息失败:', error)
    const errorMessage = {
      role: 'assistant',
      content: '抱歉，测试失败。请检查模型配置或网络连接。',
      timestamp: new Date()
    }
    messages.value.push(errorMessage)
    saveChatMessages()
    
    updateTestStats(0, false)
    
    ElMessage.error('测试失败，请检查模型配置或网络连接')
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

const sendStreamingMessage = (message) => {
  return new Promise<void>((resolve, reject) => {
    const token = authStore.token
    let controller = new AbortController()
    let timeoutId = null

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

    const context = messages.value.slice(-10).map(msg => ({
      role: msg.role,
      content: msg.content
    }))

    fetch('/api/ai/chat/stream', {
      method: 'POST',
      headers: headers,
      body: JSON.stringify({ 
        message: message,
        context: context,
        model: currentModel.value.modelCode
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
            const aiMessage = {
              role: 'assistant',
              content: streamingMessage.value,
              timestamp: new Date()
            }
            messages.value.push(aiMessage)
            streamingMessage.value = ''
            saveChatMessages()
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
                  streamingMessage.value += data.content || ''
                  if (autoScrollEnabled.value) {
                    nextTick(() => scrollToBottom())
                  }
                } else if (data.type === 'end') {
                  const aiMessage = {
                    role: 'assistant',
                    content: streamingMessage.value,
                    timestamp: new Date()
                  }
                  messages.value.push(aiMessage)
                  streamingMessage.value = ''
                  loading.value = false
                  saveChatMessages()
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
              logger.error('解析流式数据失败:', error)
            }
          }

          reader.read().then(processStream)
        }

        reader.read().then(processStream)
      })
      .catch(error => {
        clearTimeout(timeoutId)
        loading.value = false
        if (error.name === 'AbortError') {
          logger.log('请求已超时中止')
        } else {
          logger.error('流式连接错误:', error)
          ElMessage.error('连接中断或服务器拒绝访问，请重试')
        }
        reject(error)
      })
  })
}

const updateTestStats = (responseTime, success) => {
  if (success) {
    testStats.successTests++
    testStats.responseTimes.push(responseTime)
    
    if (responseTime > 0) {
      testStats.minResponseTime = Math.min(testStats.minResponseTime, responseTime)
      testStats.maxResponseTime = Math.max(testStats.maxResponseTime, responseTime)
      
      const totalTime = testStats.responseTimes.reduce((sum, time) => sum + time, 0)
      testStats.avgResponseTime = Math.round(totalTime / testStats.responseTimes.length)
    }
  } else {
    testStats.failedTests++
  }
}

const selectTestQuestion = (question) => {
  inputMessage.value = question
}

const clearMessages = () => {
  messages.value = []
  streamingMessage.value = ''
  localStorage.removeItem('aiTestChatMessages')
  
  nextTick(() => {
    if (messagesScrollbar.value) {
      messagesScrollbar.value.setScrollTop(0)
    }
  })
  
  ElMessage.success('对话已清空')
}

const exportTestLog = () => {
  const logData = {
    modelName: currentModelName.value,
    modelCode: currentModel.value ? currentModel.value.modelCode : '',
    testStats: { ...testStats },
    messages: messages.value.map(msg => ({
      role: msg.role,
      content: msg.content,
      timestamp: msg.timestamp,
      responseTime: msg.responseTime
    }))
  }
  
  const blob = new Blob([JSON.stringify(logData, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `ai-test-log-${new Date().getTime()}.json`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  
  ElMessage.success('测试记录已导出')
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesScrollbar.value && autoScrollEnabled.value) {
      messagesScrollbar.value.setScrollTop(messagesScrollbar.value.wrapRef.scrollHeight)
    }
  })
}

const handleScroll = ({ scrollTop, scrollHeight, clientHeight }) => {
  const isAtBottom = scrollHeight - scrollTop - clientHeight < 50
  
  if (isAtBottom) {
    autoScrollEnabled.value = true
  } else {
    autoScrollEnabled.value = false
  }
}

const enableAutoScroll = () => {
  autoScrollEnabled.value = true
  scrollToBottom()
}

const handleMessageContainerClick = (event) => {
  
}

const formatMessage = (content) => {
  if (typeof content === 'string') {
    return content.replace(/\n/g, '<br>')
  }
  return content
}

const formatTime = (timestamp) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${hours}:${minutes}:${seconds}`
}

const getProviderName = (provider) => {
  const map = {
    'deepseek': 'DeepSeek',
    'wenxin': '百度文心一言',
    'qwen': '阿里通义千问',
    'zhipu': '智谱AI',
    'moonshot': '月之暗面',
    'other': '其他'
  }
  return map[provider] || provider
}

const getProviderTagType = (provider) => {
  const map = {
    'deepseek': 'success',
    'wenxin': 'primary',
    'qwen': 'warning',
    'zhipu': 'info',
    'moonshot': 'danger',
    'other': 'info'
  }
  return map[provider] || 'info'
}

const formatModelCode = (modelCode) => {
  if (!modelCode) return ''
  return modelCode.split('-').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join('-')
}

const handleTabChange = (tabName) => {
  if (tabName === 'reflection' && !analysisResult.value) {
    uploadedFile.value = null
    if (uploadRef.value) {
      uploadRef.value.clearFiles()
    }
  }
}

const downloadPDF = () => {
  if (!analysisResult.value) {
    ElMessage.warning('暂无分析结果可下载')
    return
  }

  const element = document.querySelector('.analysis-result') as HTMLDivElement
  if (!element) {
    ElMessage.error('找不到分析结果内容')
    return
  }

  const opt = {
    margin: [10, 10, 10, 10],
    filename: `实习心得分析报告_${new Date().toLocaleDateString()}.pdf`,
    image: { type: 'jpeg', quality: 0.98 },
    html2canvas: { 
      scale: 2, 
      useCORS: true,
      backgroundColor: '#ffffff',
      logging: false
    },
    jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' }
  }

  ElMessage.info('正在生成PDF，请稍候...')
  
  const originalOpacity = element.style.opacity
  const originalTransform = element.style.transform
  element.style.opacity = '1'
  element.style.transform = 'none'
  
  const resultSections = element.querySelectorAll('.result-section')
  const originalAnimations: string[] = []
  resultSections.forEach((section) => {
    const htmlSection = section as HTMLElement
    originalAnimations.push(htmlSection.style.animation)
    htmlSection.style.animation = 'none'
  })
  
  html2pdf().set(opt).from(element).save().then(() => {
    element.style.opacity = originalOpacity
    element.style.transform = originalTransform
    resultSections.forEach((section, index) => {
      const htmlSection = section as HTMLElement; htmlSection.style.animation = originalAnimations[index]
    })
    ElMessage.success('PDF下载成功')
  }).catch((error) => {
    logger.error('PDF生成失败:', error)
    element.style.opacity = originalOpacity
    element.style.transform = originalTransform
    resultSections.forEach((section, index) => {
      const htmlSection = section as HTMLElement; htmlSection.style.animation = originalAnimations[index]
    })
    ElMessage.error('PDF生成失败，请稍后重试')
  })
}

const resetAnalysis = () => {
  analysisResult.value = null
  uploadedFile.value = null
  localStorage.removeItem('aiTestAnalysisResult')
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
}

const handleFileChange = (file) => {
  const maxSize = 10 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过10MB')
    return false
  }
  
  const allowedTypes = ['application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'application/pdf', 'text/plain']
  const fileName = file.name.toLowerCase()
  const allowedExtensions = ['.doc', '.docx', '.pdf', '.txt']
  
  const isValidExtension = allowedExtensions.some(ext => fileName.endsWith(ext))
  
  if (!isValidExtension) {
    ElMessage.error('不支持的文件格式，仅支持 .doc, .docx, .pdf, .txt 格式')
    return false
  }
  
  uploadedFile.value = file
  return true
}

const handleExceed = (files) => {
  ElMessage.warning('只能上传一个文件，请先删除已选文件')
}

const handleFileRemove = () => {
  uploadedFile.value = null
}

const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

const analyzeReflection = async () => {
  if (!uploadedFile.value) {
    ElMessage.warning('请先上传文件')
    return
  }

  if (!currentModel.value) {
    ElMessage.warning('请先选择AI模型')
    showModelSelector.value = true
    return
  }

  analysisProgressVisible.value = true

  try {
    const formData = new FormData()
    formData.append('file', uploadedFile.value.raw)
    formData.append('modelCode', currentModel.value.modelCode)
    formData.append('modelName', currentModel.value.modelName)

    // 传递评分规则权重给后端，用于加权计算
    formData.append('categoryWeights', JSON.stringify(categoryWeightValues.value))

    const response = await request.post('/admin/internship-reflection/upload-and-analyze', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      },
      timeout: 300000
    } as any)

    if (response.code === 200) {
      await streamDisplayAnalysisResult(response.data.analysis)
      analysisProgressVisible.value = false
      ElMessage.success(`使用 ${formatModelCode(currentModel.value.modelCode)} 分析完成`)
    } else {
      analysisProgressVisible.value = false
      ElMessage.error(response.message || '分析失败')
    }
  } catch (error) {
    logger.error('上传并分析实习心得文件失败:', error)
    analysisProgressVisible.value = false
    
    if (error.response?.data?.message) {
      ElMessage.error(error.response.message)
    } else {
      ElMessage.error('上传并分析失败，请稍后重试')
    }
  }
}

const streamDisplayAnalysisResult = async (fullAnalysis) => {
  const displayOrder = [
    { key: 'keywords', delay: 300 },
    { key: 'score', delay: 200 },
    { key: 'rating', delay: 100 },
    { key: 'weightedScore', delay: 200 },
    { key: 'aspects', delay: 300 },
    { key: 'summary', delay: 400 },
    { key: 'highlights', delay: 300 },
    { key: 'improvements', delay: 300 },
    { key: 'comment', delay: 400 },
    { key: 'keywordStats', delay: 200 }
  ]

  const result = {}
  
  for (const item of displayOrder) {
    await new Promise<void>(resolve => setTimeout(resolve, item.delay))
    result[item.key] = fullAnalysis[item.key]
    analysisResult.value = { ...result }
  }

  localStorage.setItem('aiTestAnalysisResult', JSON.stringify(fullAnalysis))
}

const getScoreTagType = (score) => {
  if (score >= 90) return 'success'
  if (score >= 80) return 'primary'
  if (score >= 70) return 'warning'
  if (score >= 60) return 'info'
  return 'danger'
}

const getAspectLabel = (key) => {
  if (categoryWeights.value[key]) {
    return categoryWeights.value[key]
  }
  
  if (isChinese(key)) {
    return key
  }
  
  const labels = {
    'teamwork': '团队协作能力',
    'problemSolving': '问题解决能力',
    'problemsolving': '问题解决能力',
    'communication': '沟通表达能力',
    'learningAbility': '学习能力',
    'learningability': '学习能力',
    'professionalism': '职业素养'
  }
  return labels[key] || key
}

const isChinese = (str) => {
  const reg = /[\u4e00-\u9fa5]/
  return reg.test(str)
}

const getProgressColor = (value) => {
  if (value >= 90) return '#67c23a'
  if (value >= 80) return '#409eff'
  if (value >= 70) return '#e6a23c'
  if (value >= 60) return '#909399'
  return '#f56c6c'
}

onMounted(() => {
  refreshModelInfo()
  loadSavedAnalysisResult()
  loadChatMessages()
  fetchCategoryWeights()
})

const loadSavedAnalysisResult = () => {
  try {
    const savedResult = localStorage.getItem('aiTestAnalysisResult')
    if (savedResult) {
      analysisResult.value = JSON.parse(savedResult)
    }
  } catch (error) {
    logger.error('加载保存的分析结果失败:', error)
  }
}

const fetchCategoryWeights = async () => {
  try {
    const response = await request.get('/admin/category-weight/active')
    if (response.code === 200) {
      const weights = response.data || []
      // categoryWeights: 存储中文名称（用于UI显示）
      const nameMap: Record<string, string> = {}
      // categoryWeightValues: 存储权重值（用于传递给后端）
      const weightMap: Record<string, number> = {}
      weights.forEach((item: any) => {
        nameMap[item.categoryCode] = item.categoryName
        weightMap[item.categoryCode] = item.weight
      })
      categoryWeights.value = nameMap
      categoryWeightValues.value = weightMap
    }
  } catch (error) {
    logger.error('获取类别权重失败:', error)
  }
}

const saveChatMessages = () => {
  try {
    localStorage.setItem('aiTestChatMessages', JSON.stringify(messages.value))
  } catch (error) {
    logger.error('保存对话消息失败:', error)
  }
}

const loadChatMessages = () => {
  try {
    const savedMessages = localStorage.getItem('aiTestChatMessages')
    if (savedMessages) {
      messages.value = JSON.parse(savedMessages)
    }
  } catch (error) {
    logger.error('加载对话消息失败:', error)
  }
}
</script>

<style scoped>
.ai-test-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  border-radius: 16px;
  padding: 24px 32px;
  color: white;
  margin-bottom: 24px;
  box-shadow: 0 8px 25px rgba(64, 158, 255, 0.25);
  position: relative;
  overflow: hidden;
}

.page-header::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -50%;
  width: 100%;
  height: 100%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
  transform: rotate(30deg);
}

.header-content h1 {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
  color: white;
}

.header-content p {
  font-size: 14px;
  opacity: 0.95;
  font-weight: 500;
  margin: 0;
}

.header-illustration {
  position: relative;
  width: 100px;
  height: 100px;
}

.illustration-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  animation: float 6s ease-in-out infinite;
}

.circle-1 {
  width: 60px;
  height: 60px;
  top: 0;
  right: 0;
  animation-delay: 0s;
}

.circle-2 {
  width: 40px;
  height: 40px;
  bottom: 10px;
  right: 20px;
  animation-delay: 3s;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

.test-main-container {
  display: flex;
  gap: 24px;
  align-items: stretch;
}

.test-card {
  flex: 1;
  background: #fafbfc;
  border-radius: 16px;
  box-shadow: 0 2px 16px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 650px;
}

.test-header {
  padding: 18px 24px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fafbfc;
}

.model-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.model-info .el-tag {
  border-radius: 12px;
  font-weight: 500;
}

.info-label {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

.status-tag {
  margin-left: 8px;
  border-radius: 12px;
  font-weight: 500;
}

.model-actions {
  display: flex;
  gap: 10px;
}

.action-btn {
  border-radius: 8px;
  font-weight: 500;
  padding: 10px 20px;
}

.primary-btn {
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  border: none;
}

.primary-btn:hover {
  background: linear-gradient(135deg, #66b1ff 0%, #73d13d 100%);
}

.test-card {
  width: 100%;
}

.ai-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 20px;
}

.ai-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.ai-tabs :deep(.el-tabs__item) {
  font-size: 16px;
  padding: 0 30px;
  height: 50px;
  line-height: 50px;
  border: none;
}

.ai-tabs :deep(.el-tabs__item.is-active) {
  color: #409EFF;
  font-weight: 600;
}

.ai-tabs :deep(.el-tabs__content) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ai-tabs :deep(.el-tab-pane) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.reflection-tab-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 30px 0;
}

.reflection-upload-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 30px;
  overflow-y: auto;
  padding: 0 30px;
}

.upload-intro {
  display: flex;
  gap: 40px;
  padding: 60px 40px;
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.03) 0%, rgba(82, 196, 26, 0.03) 100%);
  border-radius: 16px;
  border: 1.5px solid #e4e7ed;
}

.intro-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.intro-right {
  width: 500px;
  flex-shrink: 0;
}

.intro-icon {
  width: 68px;
  height: 68px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  margin-bottom: 22px;
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.25);
}

.intro-icon .el-icon {
  font-size: 34px;
}

.upload-intro h3 {
  font-size: 26px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 14px 0;
}

.upload-intro p {
  font-size: 14px;
  color: #606266;
  margin: 0 0 28px 0;
  line-height: 1.6;
}

.feature-list {
  display: flex;
  gap: 28px;
  margin-bottom: 28px;
  justify-content: center;
}

.feature-list .feature-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #67c23a;
  font-weight: 500;
  padding: 0;
  background: none;
  border: none;
  box-shadow: none;
  transition: none;
}

.feature-list .feature-item:hover {
  border-color: transparent;
  transform: none;
  box-shadow: none;
}

.feature-list .feature-item .el-icon {
  color: #409EFF;
  font-size: 20px;
}

.feature-list .feature-item .feature-icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #409EFF;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
}

.feature-list .feature-item .feature-icon .el-icon {
  font-size: 22px;
}

.upload-form {
  max-width: 600px;
  margin: 0 auto;
  padding: 0;
  background: none;
  border-radius: 0;
  box-shadow: none;
  border: none;
}

.upload-form :deep(.el-form-item) {
  margin-bottom: 24px;
}

.upload-form :deep(.el-form-item__label) {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}

.upload-form :deep(.el-input__inner) {
  height: 48px;
  font-size: 16px;
}

.analyze-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 12px;
}

.analyze-btn:hover {
  background: linear-gradient(135deg, #764ba2 0%, #667eea 100%);
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.reflection-result-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 30px 0;
}

.result-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: white;
  border-radius: 16px;
  border: 1.5px solid #e4e7ed;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 30px;
}

.result-header h3 {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.result-header .header-actions {
  display: flex;
  gap: 12px;
}

.result-header .el-button {
  padding: 12px 30px;
  font-size: 16px;
}

.reflection-result-section .analysis-result {
  flex: 1;
  overflow-y: auto;
  padding: 30px;
}

.messages-container {
  padding: 24px;
  min-height: 420px;
  background: #fafbfc;
  position: relative;
}

.auto-scroll-hint {
  position: fixed;
  bottom: 200px;
  right: 40px;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  color: white;
  padding: 12px 20px;
  border-radius: 30px;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.35);
  z-index: 1000;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.3s ease;
  animation: fadeInUp 0.3s ease;
}

.auto-scroll-hint:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.45);
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.welcome-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  text-align: center;
  min-height: 420px;
}

.welcome-icon {
  width: 68px;
  height: 68px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  margin-bottom: 22px;
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.25);
}

.welcome-icon .el-icon {
  font-size: 34px;
}

.welcome-section h3 {
  font-size: 26px;
  color: #303133;
  margin: 0 0 14px 0;
  font-weight: 600;
}

.welcome-section p {
  font-size: 14px;
  color: #606266;
  margin: 0 0 28px 0;
  line-height: 1.6;
}

.test-features {
  display: flex;
  gap: 28px;
  margin-bottom: 28px;
}

.feature-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #67c23a;
  font-weight: 500;
}

.feature-icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #409EFF;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
}

.feature-icon .el-icon {
  font-size: 22px;
}

.quick-suggestions {
  width: 100%;
  max-width: 600px;
}

.suggestion-title {
  font-size: 13px;
  color: #909399;
  margin-bottom: 14px;
  text-align: center;
  font-weight: 500;
}

.suggestion-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.suggestion-btn {
  border-radius: 20px;
  padding: 9px 18px;
  font-size: 13px;
  font-weight: 500;
  border: 1px solid #e4e7ed;
  background: white;
  color: #606266;
  transition: all 0.3s ease;
}

.suggestion-btn:hover {
  border-color: #409EFF;
  color: #409EFF;
  background: #f0f9ff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
}

.message {
  display: flex;
  margin-bottom: 18px;
  animation: slideIn 0.3s ease;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message.user {
  justify-content: flex-end;
}

.message.ai {
  justify-content: flex-start;
}

.message.assistant {
  justify-content: flex-start;
}

.message-content {
  max-width: 72%;
}

.message-bubble {
  padding: 13px 17px;
  border-radius: 16px;
  position: relative;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.message.user .message-bubble {
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  color: white;
  border-bottom-right-radius: 6px;
}

.message.ai .message-bubble {
  background: white !important;
  color: #303133;
  border-bottom-left-radius: 6px;
  border: 1px solid #f0f0f0;
}

.message.assistant .message-bubble {
  background: white !important;
  color: #303133;
  border-bottom-left-radius: 6px;
  border: 1px solid #f0f0f0;
}

.message-text {
  font-size: 14px;
  line-height: 1.65;
  word-break: break-word;
}

.message-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 9px;
  gap: 12px;
}

.message-time {
  font-size: 12px;
  opacity: 0.7;
}

.response-time {
  font-size: 12px;
  opacity: 0.85;
  display: flex;
  align-items: center;
  gap: 4px;
  color: #67c23a;
  font-weight: 500;
}

.message.user .response-time {
  color: rgba(255, 255, 255, 0.85);
}

.streaming-indicator {
  display: flex;
  gap: 6px;
  margin-top: 10px;
  justify-content: center;
}

.streaming-indicator span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #409EFF;
  animation: pulse 1.4s infinite ease-in-out both;
}

.streaming-indicator span:nth-child(1) {
  animation-delay: -0.32s;
}

.streaming-indicator span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes pulse {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

.input-container {
  padding: 18px 24px;
  background: #fafbfc;
}

.input-wrapper {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.input-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.textarea-with-button {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.message-input {
  flex: 1;
}

.message-input :deep(.el-textarea__inner) {
  border-radius: 12px;
  border: 1px solid #e4e7ed;
  padding: 13px 15px;
  font-size: 14px;
  line-height: 1.55;
  resize: none;
  transition: all 0.3s ease;
}

.message-input :deep(.el-textarea__inner):focus {
  border-color: #409EFF;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.1);
}

.send-btn {
  border-radius: 12px;
  padding: 14px 32px;
  font-size: 15px;
  font-weight: 600;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.25);
  transition: all 0.3s ease;
}

.send-btn:hover {
  background: linear-gradient(135deg, #66b1ff 0%, #73d13d 100%);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.35);
}

.send-btn:disabled {
  background: #c0c4cc;
  box-shadow: none;
  transform: none;
}

.test-stats-card {
  width: 320px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 2px 16px rgba(0, 0, 0, 0.06);
  padding: 24px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}

.stats-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 2px solid #f0f0f0;
}

.stats-header h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.stats-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #409EFF;
}

.stats-icon .el-icon {
  font-size: 20px;
}

.stats-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  font-weight: 500;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #303133;
  line-height: 1;
}

.stat-value.success {
  color: #67c23a;
}

.stat-value.error {
  color: #f56c6c;
}

.stat-divider {
  height: 1px;
  background: linear-gradient(90deg, transparent 0%, #e4e7ed 50%, transparent 100%);
  margin: 8px 0;
}

.model-selector-dialog :deep(.el-dialog__body) {
  padding: 24px;
}

.model-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.model-item {
  padding: 16px;
  border: 2px solid #f0f0f0;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.model-item:hover {
  border-color: #409EFF;
  background: #f0f9ff;
  transform: translateX(4px);
}

.model-item.selected {
  border-color: #409EFF;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
}

.model-item-content {
  display: flex;
  align-items: center;
  gap: 12px;
}

.model-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  flex: 1;
}

.model-code {
  font-size: 13px;
  color: #909399;
  font-family: var(--font-family-mono);
}

@media (max-width: 1400px) {
  .test-main-container {
    flex-direction: column;
  }
  
  .test-stats-card {
    width: 100%;
  }
  
  .upload-intro {
    flex-direction: column;
    gap: 30px;
  }
  
  .intro-right {
    width: 100%;
  }
}

@media screen and (max-width: 768px) {
  .ai-test-container {
    padding: 15px;
  }
  
  .page-header {
    flex-direction: column;
    text-align: center;
    padding: 20px;
  }
  
  .header-illustration {
    margin-top: 15px;
  }
  
  .test-features {
    flex-direction: column;
    gap: 16px;
  }
  
  .suggestion-buttons {
    flex-direction: column;
  }
  
  .message-content {
    max-width: 85%;
  }
  
  .test-main-container {
    flex-direction: column;
  }
  
  .test-stats-card {
    width: 100%;
  }
  
  .upload-intro {
    padding: 30px 20px;
  }
  
  .intro-left h3 {
    font-size: 20px;
  }
  
  .feature-list {
    flex-direction: column;
    gap: 16px;
  }
  
  .upload-form {
    max-width: 100%;
  }
  
  .upload-form :deep(.el-form-item__label) {
    width: 80px !important;
  }
}

.analysis-result {
  padding: 30px;
}

.result-section {
  margin-bottom: 30px;
  padding-bottom: 30px;
  animation: fadeInUp 0.5s ease-out;
}

.result-section:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.result-section h4 {
  margin: 0 0 16px 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 12px;
}

.result-section h4::before {
  content: '';
  width: 4px;
  height: 20px;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  border-radius: 2px;
}

.keywords-container {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.no-keywords {
  color: #909399;
  font-size: 14px;
  font-style: italic;
}

.divider-line {
  height: 1px;
  background: #e4e7ed;
  margin: 20px 30px;
}

.keyword-tag {
  border-radius: 20px;
  padding: 8px 18px;
  font-size: 15px;
  font-weight: 500;
}

.score-display {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px;
  background: #f8f9fa;
  border-radius: 16px;
  border: 1.5px solid #e4e7ed;
}

.score-value {
  font-size: 56px;
  font-weight: 700;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.weighted-score-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  font-size: 14px;
  color: #606266;
  flex-wrap: wrap;
}

.weighted-score-label {
  font-weight: 600;
  color: #409EFF;
}

.weighted-score-value {
  font-weight: 700;
  font-size: 18px;
  color: #e6a23c;
}

.original-score-label {
  color: #909399;
  font-size: 13px;
}

.aspects-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.aspect-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.aspect-label {
  font-size: 15px;
  color: #606266;
  font-weight: 500;
}

.summary-text,
.comment-text {
  margin: 0;
  font-size: 16px;
  line-height: 1.8;
  color: #606266;
  padding: 20px;
  background: #f8f9fa;
  border-radius: 12px;
  border-left: 4px solid #409EFF;
}

.highlight-list,
.improvement-list {
  margin: 0;
  padding-left: 0;
  list-style: none;
}

.highlight-list li,
.improvement-list li {
  position: relative;
  padding: 16px 0 16px 32px;
  font-size: 16px;
  line-height: 1.8;
  color: #606266;
  border-bottom: 1px solid #ebeef5;
}

.highlight-list li:last-child,
.improvement-list li:last-child {
  border-bottom: none;
}

.highlight-list li::before {
  content: '✓';
  position: absolute;
  left: 0;
  top: 16px;
  width: 24px;
  height: 24px;
  background: #67c23a;
  color: white;
  border-radius: 50%;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
}

.improvement-list li::before {
  content: '!';
  position: absolute;
  left: 0;
  top: 16px;
  width: 24px;
  height: 24px;
  background: #e6a23c;
  color: white;
  border-radius: 50%;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
}

.reflection-upload {
  width: 100%;
}

.reflection-upload :deep(.el-upload-dragger) {
  padding: 60px 40px;
  border: 2px dashed #d9d9d9;
  border-radius: 12px;
  background: #fafafa;
  transition: all 0.3s ease;
}

.reflection-upload :deep(.el-upload-dragger:hover) {
  border-color: #409EFF;
  background: #f0f9ff;
}

.reflection-upload :deep(.el-icon--upload) {
  font-size: 48px;
  color: #409EFF;
  margin-bottom: 16px;
}

.reflection-upload :deep(.el-upload__text) {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

.reflection-upload :deep(.el-upload__text em) {
  color: #409EFF;
  font-style: normal;
  font-weight: 600;
}

.reflection-upload :deep(.el-upload__tip) {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
  line-height: 1.6;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: #f0f9ff;
  border: 1.5px solid #b3d8ff;
  border-radius: 12px;
  transition: all 0.3s ease;
}

.file-info:hover {
  background: #e0f2ff;
  border-color: #409EFF;
}

.file-info .el-icon {
  font-size: 24px;
  color: #409EFF;
}

.file-name {
  font-size: 16px;
  color: #303133;
  font-weight: 600;
}

.file-size {
  font-size: 14px;
  color: #909399;
  font-weight: 500;
}

.analysis-progress-dialog :deep(.el-dialog__body) {
  padding: 30px;
}

.progress-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 30px;
  padding: 20px 0;
}

.progress-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.3);
}

.progress-icon .el-icon {
  font-size: 40px;
  color: white;
}

.progress-text {
  font-size: 16px;
  color: #606266;
  text-align: center;
  line-height: 1.6;
}

.progress-hint {
  margin-top: 16px;
  padding: 12px;
  background: #f0f9ff;
  border-radius: 8px;
  border-left: 3px solid #1890ff;
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.progress-hint .el-icon {
  color: #1890ff;
  font-size: 16px;
  margin-top: 2px;
  flex-shrink: 0;
}

.progress-hint span {
  font-size: 13px;
  color: #666;
  line-height: 1.6;
  text-align: left;
}
</style>
