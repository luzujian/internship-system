<template>
  <div class="home-container">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">欢迎回来，{{ teacherName }}老师</h1>
        <p class="page-description">{{ currentDate }}</p>
      </div>
    </div>

    <el-card class="overview-card" shadow="never">
      <div class="overview-cards">
        <div class="card" @click="handleCardClick('dashboard')">
          <div class="card-icon green">📊</div>
          <div class="card-content">
            <h3>实习状态</h3>
            <p class="card-value">已确定实习单位：{{ backendInternshipRate }}%</p>
            <p class="card-desc">{{ internshipCardDesc }}</p>
          </div>
        </div>
        <div class="card" @click="handleCardClick('announcements')">
          <div class="card-icon blue">📢</div>
          <div class="card-content">
            <h3>通知统计</h3>
            <p class="card-value">未读通知: {{ notificationData.unread }}</p>
            <p class="card-desc">需要及时处理的通知</p>
          </div>
        </div>
        <div class="card" @click="handleCardClick('approval')" v-if="teacherType !== 'COUNSELOR'">
          <div class="card-icon orange">✓</div>
          <div class="card-content">
            <h3>待审核</h3>
            <p class="card-value">待处理: {{ approvalData.pending }}</p>
            <p class="card-desc">学生提交的申请</p>
          </div>
        </div>
        <div class="card" @click="handleCardClick('reports')">
          <div class="card-icon purple">📈</div>
          <div class="card-content">
            <h3>企业数据</h3>
            <p class="card-value">企业入驻: {{ operationData.companies }}</p>
            <p class="card-desc">合作实习单位数量</p>
          </div>
        </div>
      </div>
    </el-card>

    <div class="content-section">
      <el-card class="announcement-card" shadow="never">
      <div class="board-header">
        <h3>
          <el-icon><Bell /></el-icon>
          公告栏
          <el-badge v-if="notificationData.unread > 0" :value="notificationData.unread" class="unread-badge"></el-badge>
        </h3>
        <router-link to="/teacher/announcements" class="view-more-link">查看更多</router-link>
      </div>
        <div class="announcement-list-container">
          <el-scrollbar height="600px">
            <div class="announcement-list">
              <div 
                v-for="announcement in announcements" 
                :key="announcement.id"
                class="announcement-item"
                :class="{ 'unread': !announcement.isRead }"
                @click="openAnnouncementDetail(announcement)"
              >
                <div class="announcement-time">{{ announcement.time }}</div>
                <div class="announcement-content">
                  <h4>{{ announcement.title }}</h4>
                  <p>
                    {{ announcement.content.length > 100 ? announcement.content.substring(0, 100) + '...' : announcement.content }}
                  </p>
                </div>
                <div v-if="!announcement.isRead" class="unread-dot"></div>
              </div>
            </div>
          </el-scrollbar>
        </div>
      </el-card>

      <el-card class="status-card" shadow="never">
        <div class="board-header">
          <h3>{{ statusCardTitle }}</h3>
          <router-link to="/teacher/dashboard" class="view-more-link">查看详情</router-link>
        </div>
        <div class="chart-container">
          <div class="chart-legend">
            <div v-for="item in statusData" :key="item.name" class="legend-item">
              <div class="legend-info">
                <div class="legend-header">
                  <span :class="['legend-color', item.color]"></span>
                  <span class="legend-name">{{ item.name }}</span>
                </div>
                <span class="legend-percentage">{{ Math.round((item.value / totalStudents) * 1000) / 10 }}%</span>
              </div>
              <div class="legend-progress">
                <div 
                  :class="['progress-bar', item.color]" 
                  :style="{ width: Math.round((item.value / totalStudents) * 1000) / 10 + '%' }"
                ></div>
              </div>
            </div>
          </div>
          <div class="pie-chart">
            <svg width="300" height="300" viewBox="0 0 300 300">
              <path 
                v-for="(path, index) in piePaths" 
                :key="index" 
                :d="path.d" 
                :fill="`url(#${path.color}Gradient)`" 
              />
              <circle cx="150" cy="150" r="90" fill="white" />
              <text x="150" y="145" text-anchor="middle" font-size="24" font-weight="bold" fill="#1890FF">{{ backendInternshipRate }}%</text>
              <text x="150" y="175" text-anchor="middle" font-size="16" fill="#666">实习率</text>
            </svg>
            <svg width="0" height="0">
              <defs>
                <linearGradient id="successGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#52C41A" />
                  <stop offset="100%" stop-color="#73D13D" />
                </linearGradient>
                <linearGradient id="infoGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#1890FF" />
                  <stop offset="100%" stop-color="#40A9FF" />
                </linearGradient>
                <linearGradient id="dangerGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#FF4D4F" />
                  <stop offset="100%" stop-color="#FF7875" />
                </linearGradient>
                <linearGradient id="warningGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#FA8C16" />
                  <stop offset="100%" stop-color="#FFA940" />
                </linearGradient>
              </defs>
            </svg>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 查看公告对话框 -->
    <el-dialog v-model="viewDialogVisible" title="查看公告" width="800px" class="view-dialog">
      <div v-if="viewData" class="view-content">
        <h3 class="view-title">{{ viewData.title }}</h3>
        <div class="view-meta">
          <div class="meta-item">
            <span class="meta-label">发布人:</span>
            <span class="meta-value">{{ viewData.publisher }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">发布人身份:</span>
            <el-tag :type="getPublisherRoleType(viewData.publisherRole)" size="small" class="status-tag">
              {{ getPublisherRoleText(viewData.publisherRole) }}
            </el-tag>
          </div>
          <div class="meta-item">
            <span class="meta-label">发布日期:</span>
            <span class="meta-value">{{ formatDate(viewData.publishDate || viewData.publishTime) }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">过期日期:</span>
            <span class="meta-value">{{ formatDate(viewData.expireDate || viewData.validTo) }}</span>
          </div>
        </div>
        <el-divider></el-divider>
        <div class="content-body" v-html="viewData.content"></div>
        <div v-if="viewData.attachments && parseAttachments(viewData.attachments).length > 0" class="attachments-section">
          <el-divider></el-divider>
          <div class="attachments-title">
            <el-icon><Paperclip /></el-icon>
            <span>附件列表</span>
          </div>
          <div class="attachments-list">
            <div
              v-for="(attachment, index) in parseAttachments(viewData.attachments)"
              :key="index"
              class="attachment-item"
            >
              <div class="attachment-info" @click="downloadFileWithConfirm(attachment)">
                <el-icon class="attachment-icon"><Document /></el-icon>
                <span class="attachment-name">{{ attachment.name }}</span>
              </div>
              <div class="attachment-actions">
                <el-button
                  type="primary"
                  size="small"
                  @click.stop="previewFile(attachment)"
                  title="预览文件"
                >
                  <el-icon><ZoomIn /></el-icon>
                  预览
                </el-button>
                <el-button
                  type="success"
                  size="small"
                  @click.stop="downloadFileWithConfirm(attachment)"
                  title="下载文件"
                >
                  <el-icon><Download /></el-icon>
                  下载
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="viewDialogVisible = false" class="cancel-btn">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 文件预览对话框 -->
    <el-dialog v-model="filePreviewVisible" title="文件预览" width="90%" class="preview-dialog">
      <div v-if="currentFileUrl" class="preview-content">
        <iframe :src="currentFileUrl" frameborder="0" class="preview-iframe"></iframe>
      </div>
      <template #footer>
        <el-button @click="filePreviewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { homeApi, type HomeStats, type AnnouncementWithReadStatus } from '../../api/teacherHome'
import * as announcementApi from '../../api/announcement'
import store from '../../store/teacherStore'
import { useAuthStore } from '@/store/auth'
import { useRouter } from 'vue-router'
import { ElMessage, ElScrollbar } from 'element-plus'
import { Bell, Paperclip, Document, Download, ZoomIn } from '@element-plus/icons-vue'
import {
  initAnnouncementWebSocket,
  disconnectAnnouncementWebSocket
} from '@/utils/websocket'
import TeacherService from '../../api/teacher'
import request from '@/utils/request'

const storeInstance = store
const authStore = useAuthStore()
const router = useRouter()

const teacherName = computed(() => {
  return authStore.user?.name || '未知'
})

const currentDate = computed(() => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  const weekday = weekdays[now.getDay()]
  return `${year}年${month}月${day}日 ${weekday}`
})

const statusData = ref([
  { name: '已确定实习', value: 0, color: 'success' },
  { name: '有offer但未确定', value: 0, color: 'info' },
  { name: '没offer', value: 0, color: 'warning' },
  { name: '延迟', value: 0, color: 'danger' }
])

const announcements = ref<AnnouncementWithReadStatus[]>([])

const notificationData = ref({
  unread: storeInstance.state.unreadCount
})

const approvalData = ref({
  pending: 0
})

const operationData = ref({
  companies: 0
})

const backendInternshipRate = ref(0)

const teacherType = ref<string>('')

const viewDialogVisible = ref(false)
const viewData = ref<any>(null)
const filePreviewVisible = ref(false)
const currentFileUrl = ref('')
const currentFileName = ref('')

const statusCardTitle = computed(() => {
  return teacherType.value === 'COUNSELOR' ? '负责班级实习状态占比' : '全院实习状态占比'
})

const internshipCardDesc = computed(() => {
  return teacherType.value === 'COUNSELOR' ? '负责班级学生实习进展情况' : '全院学生实习进展情况'
})

const loadTeacherInfo = async () => {
  try {
    if (!authStore.token) {
      console.warn('用户未登录，跳过加载教师信息')
      return
    }
    
    const response = await TeacherService.getCurrentTeacherInfo()
    if (response && response.data) {
      const teacherInfo = response.data
      
      if (teacherInfo.teacherType) {
        localStorage.setItem('teacherType', teacherInfo.teacherType)
        teacherType.value = teacherInfo.teacherType
        
        if (authStore.user) {
          authStore.user.teacherType = teacherInfo.teacherType
        }
      }
      
      if (teacherInfo.name && authStore.user) {
        authStore.user.name = teacherInfo.name
      }
    }
  } catch (error) {
    console.warn('加载教师信息失败（不影响其他功能）:', error)
  }
}

const loadHomeData = async () => {
  try {
    const response = await homeApi.getHomeStats()
    
    if (response.statusData && response.statusData.length > 0) {
      statusData.value = response.statusData
    }
    
    backendInternshipRate.value = response.internshipRate || 0
    approvalData.value.pending = response.pendingApprovalCount || 0
    operationData.value.companies = response.companyCount || 0
  } catch (error) {
    console.error('加载首页数据失败:', error)
  }
}

const loadAnnouncements = async () => {
  try {
    const response = await TeacherService.getNotifications(20)
    if (response && response.data) {
      // 后端已过滤只返回未读公告
      const unreadAnnouncements = response.data.map((a: any) => ({
        id: a.id,
        title: a.title,
        content: a.content,
        time: a.time || a.publishDate || a.publishTime || '',
        isRead: a.isRead !== undefined ? a.isRead : false,
        expanded: false
      }))

      // 首页公告栏只显示未读公告（最多5条）
      const unreadCount = unreadAnnouncements.length
      notificationData.value.unread = unreadCount
      announcements.value = unreadAnnouncements.slice(0, 5)
    }
  } catch (error) {
    console.error('加载公告数据失败:', error)
  }
}

const handleWebSocketMessage = (data: any) => {
  console.log('教师端收到WebSocket消息:', data)
  
  if (data.type === 'new_announcement') {
    ElMessage.success({
      message: `新公告：${data.data?.title || '未知标题'}`,
      duration: 5000,
      showClose: true
    })
    loadAnnouncements()
  }
}

const initWebSocket = () => {
  let token = authStore.token
  
  if (!token) {
    const rolePrefix = 'teacher_'
    const role = 'ROLE_TEACHER'
    token = localStorage.getItem(`${rolePrefix}accessToken_${role}`) ||
            localStorage.getItem(`${rolePrefix}token_${role}`) ||
            localStorage.getItem('accessToken')
  }
  
  if (token) {
    console.log('初始化 WebSocket 连接，token 存在')
    initAnnouncementWebSocket(token, handleWebSocketMessage)
  } else {
    console.warn('未找到 token，无法初始化 WebSocket')
  }
}

const handleCardClick = (type: string) => {
  const routeMap: Record<string, string> = {
    dashboard: '/teacher/dashboard',
    announcements: '/teacher/announcements',
    approval: '/teacher/approval',
    reports: '/teacher/reports'
  }
  
  const path = routeMap[type]
  if (path) {
    router.push({
      path,
      state: { fromHome: true }
    })
  }
}

const openAnnouncementDetail = async (announcement: AnnouncementWithReadStatus) => {
  try {
    const response = await request({
      url: `/announcements/${announcement.id}`,
      method: 'get'
    })
    if (response.code === 200 && response.data) {
      viewData.value = response.data
      viewDialogVisible.value = true

      // 从列表中移除已读公告（首页只显示未读公告）
      const index = announcements.value.findIndex(a => a.id === announcement.id)
      if (index > -1) {
        announcements.value.splice(index, 1)
      }
      notificationData.value.unread = announcements.value.length

      if (!announcement.isRead) {
        try {
          const userId = authStore.user?.id
          if (userId) {
            await announcementApi.createAnnouncementReadRecord({
              announcementId: announcement.id,
              userId: userId,
              userRole: 'TEACHER'
            })
          }
        } catch (error) {
          console.error('标记公告已读失败:', error)
        }
      }
    } else {
      ElMessage.error('获取公告详情失败')
    }
  } catch (error) {
    console.error('获取公告详情失败:', error)
    ElMessage.error('获取公告详情失败')
  }
}

const parseAttachments = (attachments: any) => {
  if (!attachments) return []
  try {
    if (typeof attachments === 'string') {
      return JSON.parse(attachments)
    }
    return attachments
  } catch (e) {
    console.error('解析附件数据失败:', e)
    return []
  }
}

const previewFile = (attachment: any) => {
  if (!attachment) {
    ElMessage.error('附件信息不存在')
    return
  }

  const url = attachment.url ? String(attachment.url) : null
  const name = attachment.name ? String(attachment.name) : '附件'
  
  if (!url) {
    ElMessage.error('附件URL不存在')
    return
  }

  currentFileUrl.value = url
  currentFileName.value = name
  filePreviewVisible.value = true
}

const downloadFileWithConfirm = (attachment: any) => {
  if (!attachment) {
    ElMessage.error('附件信息不存在')
    return
  }

  const url = attachment.url ? String(attachment.url) : null
  const name = attachment.name ? String(attachment.name) : '附件'
  
  if (!url) {
    ElMessage.error('附件URL不存在')
    return
  }

  const link = document.createElement('a')
  link.href = url
  link.download = name
  link.target = '_blank'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

const formatDate = (date: string | Date) => {
  if (!date) return ''
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

const getPublisherRoleText = (role: string) => {
  const map: Record<string, string> = {
    'ADMIN': '管理员',
    'COLLEGE': '学院教师',
    'DEPARTMENT': '系室教师',
    'COUNSELOR': '辅导员'
  }
  return map[role] || '-'
}

const getPublisherRoleType = (role: string) => {
  const map: Record<string, string> = {
    'ADMIN': 'danger',
    'COLLEGE': 'primary',
    'DEPARTMENT': 'success',
    'COUNSELOR': 'warning'
  }
  return map[role] || 'info'
}

const totalStudents = computed(() => {
  return statusData.value.reduce((sum, item) => sum + item.value, 0)
})

const getPiePath = (startAngle: number, endAngle: number) => {
  const radius = 120
  const centerX = 150
  const centerY = 150
  
  const startRad = (startAngle - 90) * Math.PI / 180
  const endRad = (endAngle - 90) * Math.PI / 180
  
  const startX = centerX + radius * Math.cos(startRad)
  const startY = centerY + radius * Math.sin(startRad)
  const endX = centerX + radius * Math.cos(endRad)
  const endY = centerY + radius * Math.sin(endRad)
  
  const largeArcFlag = endAngle - startAngle > 180 ? 1 : 0
  
  return `M${centerX} ${centerY} L${startX} ${startY} A${radius} ${radius} 0 ${largeArcFlag} 1 ${endX} ${endY} Z`
}

const piePaths = computed(() => {
  const paths = []
  let currentAngle = 0
  
  for (const item of statusData.value) {
    const angle = totalStudents.value > 0 ? (item.value / totalStudents.value) * 360 : 0
    const endAngle = currentAngle + angle
    paths.push({
      d: getPiePath(currentAngle, endAngle),
      color: item.color
    })
    currentAngle = endAngle
  }
  
  return paths
})

let updateInterval: number

onMounted(() => {
  loadTeacherInfo()
  loadHomeData()
  loadAnnouncements()
  initWebSocket()
  updateInterval = window.setInterval(() => {
    loadHomeData()
    loadAnnouncements()
  }, 60000)
})

onUnmounted(() => {
  if (updateInterval) {
    clearInterval(updateInterval)
  }
  disconnectAnnouncementWebSocket()
})
</script>

<style scoped>
.home-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-content h1 {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px 0;
}

.page-description {
  font-size: 14px;
  opacity: 0.95;
  font-weight: 500;
  margin: 0;
}

.overview-card {
  margin-bottom: 20px;
  border-radius: 16px;
  border: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.announcement-card,
.status-card {
  border-radius: 16px;
  border: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  margin-bottom: 20px;
}

.overview-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}

.card {
  display: flex;
  align-items: center;
  padding: 20px;
  background-color: white;
  border-radius: 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  transition: all 0.3s;
  text-decoration: none;
  color: inherit;
  cursor: pointer;
}

.card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.card-icon {
  font-size: 28px;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
}

.card-icon.green {
  background-color: #f6ffed;
  color: #52c41a;
}

.card-icon.blue {
  background-color: #e6f7ff;
  color: #1890ff;
}

.card-icon.orange {
  background-color: #fff7e6;
  color: #fa8c16;
}

.card-icon.purple {
  background-color: #f9f0ff;
  color: #722ed1;
}

.card-content {
  flex: 1;
}

.card-content h3 {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px 0;
}

.card-value {
  font-size: 20px;
  font-weight: 700;
  color: #409EFF;
  margin: 0 0 4px 0;
}

.card-desc {
  font-size: 13px;
  color: #909399;
  margin: 0;
}

.content-section {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.announcement-card,
.status-card {
  margin-bottom: 20px;
}

.board-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.board-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.view-more-link {
  font-size: 14px;
  color: #409EFF;
  text-decoration: none;
  transition: all 0.3s ease;
}

.view-more-link:hover {
  color: #66b1ff;
  text-decoration: underline;
}

/* 公告栏样式 */
.announcement-list-container {
  height: 600px;
}

.announcement-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.announcement-item {
  display: flex;
  gap: 16px;
  padding: 16px;
  background-color: #f8f9fa;
  border-radius: 8px;
  transition: all 0.3s ease;
  border-left: 4px solid #409EFF;
  cursor: pointer;
  position: relative;
}

.announcement-item:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  background-color: #e6f7ff;
}

.announcement-time {
  font-size: 14px;
  color: #909399;
  white-space: nowrap;
  min-width: 100px;
  padding-top: 4px;
}

.announcement-content {
  flex: 1;
}

.announcement-content h4 {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px 0;
}

.announcement-content p {
  font-size: 14px;
  color: #606266;
  margin: 0;
  line-height: 1.4;
}

.announcement-item.unread {
  background-color: #fff7e6;
  border-left-color: #faad14;
}

.unread-dot {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 8px;
  height: 8px;
  background-color: #f56c6c;
  border-radius: 50%;
  box-shadow: 0 0 0 2px #fff;
}

.unread-badge {
  margin-left: 8px;
}

.unread-badge :deep(.el-badge__content) {
  background-color: #f56c6c;
  border: 2px solid #fff;
  font-size: 12px;
  height: 18px;
  line-height: 14px;
  padding: 0 5px;
}

/* 饼图样式 */
.chart-container {
  display: flex;
  align-items: center;
  gap: 30px;
  justify-content: center;
  flex-wrap: wrap;
}

.pie-chart {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pie-chart svg {
  border-radius: 50%;
  transition: all 0.3s ease;
}

.pie-chart svg:hover {
  transform: scale(1.02);
}

.chart-legend {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
  max-width: 300px;
  margin-right: 30px;
}

.legend-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 14px;
  color: #303133;
  padding: 12px 16px;
  transition: all 0.3s ease;
}

.legend-item:hover {
  background-color: #f5f7fa;
  transform: translateX(4px);
}

.legend-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.legend-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.legend-name {
  font-weight: 500;
  color: #303133;
}

.legend-progress {
  width: 100%;
  height: 8px;
  background-color: #f0f0f0;
  border-radius: 4px;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  border-radius: 4px;
  transition: width 0.6s ease, background 0.3s ease;
  position: relative;
  overflow: hidden;
}

.progress-bar::after {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.4), transparent);
  animation: shimmer 2s infinite;
}

@keyframes shimmer {
  0% {
    left: -100%;
  }
  100% {
    left: 100%;
  }
}

.legend-color {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  flex-shrink: 0;
  position: relative;
  overflow: hidden;
  transition: all 0.3s ease;
}

.legend-color::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.3), transparent);
  transition: left 0.5s ease;
}

.legend-color:hover::before {
  left: 100%;
}

.legend-color.success {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  box-shadow: 0 2px 8px rgba(82, 196, 26, 0.3);
}

.legend-color.info {
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.3);
}

.legend-color.danger {
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7875 100%);
  box-shadow: 0 2px 8px rgba(255, 77, 79, 0.3);
}

.legend-color.warning {
  background: linear-gradient(135deg, #fa8c16 0%, #ffa940 100%);
  box-shadow: 0 2px 8px rgba(250, 140, 22, 0.3);
}

.legend-percentage {
  margin-left: auto;
  font-weight: 600;
  color: #303133;
  background-color: rgba(64, 158, 255, 0.1);
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}

/* 快捷操作 */
.action-buttons {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 16px;
}

.action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background-color: #fafafa;
  border-radius: 8px;
  text-decoration: none;
  color: #333;
  transition: all 0.3s;
  border: 1px solid #e8e8e8;
}

.action-btn:hover {
  background-color: #e6f7ff;
  border-color: #1890ff;
  transform: translateY(-2px);
}

.btn-icon {
  font-size: 26px;
  margin-bottom: 8px;
}

.action-btn span:last-child {
  font-size: 15px;
  font-weight: 500;
}

/* 公告详情弹窗样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.3s ease-out;
}

.modal-content {
  background-color: white;
  border-radius: 8px;
  width: 500px;
  max-width: 90vw;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.15);
  animation: slideIn 0.3s ease-out;
}

.announcement-detail-modal {
  max-width: 1200px;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid #f0f0f0;
  background-color: #409EFF;
  color: white;
}

.modal-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: white;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #999;
  padding: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.close-btn:hover {
  color: #333;
  transform: rotate(90deg);
}

.modal-body {
  padding: 24px;
}

.announcement-detail-info {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
  padding: 16px;
  background-color: #f8f9fa;
  border-radius: 8px;
  flex-wrap: wrap;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 200px;
  flex: 1;
}

.detail-item label {
  font-weight: 600;
  color: #606266;
  min-width: 80px;
}

.read-status {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.read-status.unread {
  background-color: #fff7e6;
  color: #faad14;
}

.read-status:not(.unread) {
  background-color: #f6ffed;
  color: #52c41a;
}

/* 公告详情弹窗样式 */
.view-dialog :deep(.el-dialog__header),
.preview-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #409EFF 0%, #66b1ff 100%);
  color: white;
  padding: 16px 24px;
}

.view-dialog :deep(.el-dialog__title),
.preview-dialog :deep(.el-dialog__title) {
  color: white;
  font-size: 18px;
  font-weight: 600;
}

.view-content {
  padding: 0;
}

.view-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 20px 0;
  padding-bottom: 12px;
  border-bottom: 2px solid #409EFF;
}

.view-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 16px;
  background-color: #f8f9fa;
  border-radius: 8px;
  margin-bottom: 20px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 200px;
  flex: 1;
}

.meta-label {
  font-weight: 600;
  color: #606266;
  min-width: 80px;
}

.meta-value {
  color: #303133;
}

.status-tag {
  margin-left: 8px;
}

.content-body {
  padding: 20px;
  background-color: #f8f9fa;
  border-radius: 8px;
  line-height: 1.8;
  font-size: 14px;
  color: #606266;
  min-height: 100px;
}

.attachments-section {
  margin-top: 20px;
}

.attachments-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
}

.attachments-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.attachment-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background-color: white;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.attachment-item:hover {
  border-color: #409EFF;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
}

.attachment-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  cursor: pointer;
  padding: 8px;
  border-radius: 6px;
  transition: all 0.3s ease;
}

.attachment-info:hover {
  background-color: #f0f9ff;
}

.attachment-icon {
  font-size: 24px;
  color: #409EFF;
}

.attachment-name {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}

.attachment-actions {
  display: flex;
  gap: 8px;
}

.cancel-btn {
  padding: 8px 24px;
  background-color: #909399;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.3s ease;
}

.cancel-btn:hover {
  background-color: #a6a9ad;
}

.preview-content {
  height: 600px;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.announcement-detail-content {
  margin-bottom: 20px;
}

.announcement-detail-content h4 {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 12px 0;
}

.content-text {
  padding: 16px;
  background-color: #f8f9fa;
  border-radius: 8px;
  line-height: 1.6;
  white-space: pre-wrap;
  font-size: 14px;
  color: #606266;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
  background-color: #fafafa;
}

.close-btn-primary {
  padding: 8px 16px;
  background-color: #409EFF;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.3s ease;
}

.close-btn-primary:hover {
  background-color: #66b1ff;
  transform: translateY(-1px);
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 768px) {
  .overview-cards {
    grid-template-columns: 1fr;
  }

  .content-section {
    grid-template-columns: 1fr;
  }

  .modal-content {
    width: 95vw;
  }

  .detail-item {
    min-width: 100%;
  }
}

.card-content h3 {
  font-size: 16px;
  font-weight: 500;
  color: #666;
  margin: 0 0 4px 0;
}

.card-value {
  font-size: 20px;
  font-weight: 600;
  color: #333;
  margin: 0 0 4px 0;
}

.card-desc {
  font-size: 12px;
  color: #999;
  margin: 0;
}

/* 最近活动 */
.recent-activities,
.quick-actions {
  background-color: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.recent-activities h3,
.quick-actions h3 {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0 0 16px 0;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.activity-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background-color: #fafafa;
  border-radius: 4px;
  transition: background-color 0.3s;
}

.activity-item:hover {
  background-color: #f0f0f0;
}

.activity-time {
  font-size: 12px;
  color: #999;
  white-space: nowrap;
}

.activity-content {
  flex: 1;
  margin-left: 16px;
  font-size: 14px;
  color: #333;
}

.activity-type {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 10px;
  font-weight: 500;
  margin-right: 8px;
}

.activity-type.notification {
  background-color: #e6f7ff;
  color: #1890ff;
}

.activity-type.approval {
  background-color: #f6ffed;
  color: #52c41a;
}

.activity-type.evaluation {
  background-color: #fff7e6;
  color: #fa8c16;
}

.activity-type.resource {
  background-color: #f9f0ff;
  color: #722ed1;
}

/* 快捷操作 */
.action-buttons {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 16px;
}

.action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background-color: #fafafa;
  border-radius: 8px;
  text-decoration: none;
  color: #333;
  transition: all 0.3s;
  border: 1px solid #e8e8e8;
}

.action-btn:hover {
  background-color: #e6f7ff;
  border-color: #1890ff;
  transform: translateY(-2px);
}

.btn-icon {
  font-size: 24px;
  margin-bottom: 8px;
}

.action-btn span:last-child {
  font-size: 14px;
  font-weight: 500;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .overview-cards {
    grid-template-columns: 1fr;
  }

  .action-buttons {
    grid-template-columns: repeat(2, 1fr);
  }

  .activity-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .activity-time {
    align-self: flex-end;
  }
}
</style>

<style>
.legend-color.success {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%) !important;
  box-shadow: 0 2px 8px rgba(82, 196, 26, 0.3) !important;
}

.legend-color.info {
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%) !important;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.3) !important;
}

.legend-color.danger {
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7875 100%) !important;
  box-shadow: 0 2px 8px rgba(255, 77, 79, 0.3) !important;
}

.legend-color.warning {
  background: linear-gradient(135deg, #fa8c16 0%, #ffa940 100%) !important;
  box-shadow: 0 2px 8px rgba(250, 140, 22, 0.3) !important;
}

.progress-bar.success {
  background: linear-gradient(90deg, #52c41a 0%, #73d13d 100%) !important;
  box-shadow: 0 2px 4px rgba(82, 196, 26, 0.3) !important;
}

.progress-bar.info {
  background: linear-gradient(90deg, #1890ff 0%, #40a9ff 100%) !important;
  box-shadow: 0 2px 4px rgba(24, 144, 255, 0.3) !important;
}

.progress-bar.danger {
  background: linear-gradient(90deg, #ff4d4f 0%, #ff7875 100%) !important;
  box-shadow: 0 2px 4px rgba(255, 77, 79, 0.3) !important;
}

.progress-bar.warning {
  background: linear-gradient(90deg, #fa8c16 0%, #ffa940 100%) !important;
  box-shadow: 0 2px 4px rgba(250, 140, 22, 0.3) !important;
}
</style>
