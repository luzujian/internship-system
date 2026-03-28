<script setup>
import { ref, computed, onMounted, onUnmounted, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import request from '@/utils/request'
import emitter from '@/utils/event-bus'
import { usePositionStore } from '@/store/position'
import { ElMessage } from 'element-plus'
import { ElScrollbar } from 'element-plus'
import FilePreviewDialog from '@/components/FilePreviewDialog.vue'
import {
  initAnnouncementWebSocket,
  disconnectAnnouncementWebSocket
} from '@/utils/websocket'
import {
  DocumentChecked,
  User,
  Message,
  TrendCharts,
  Calendar,
  Bell,
  ArrowRight,
  Plus,
  Edit,
  View,
  ChatDotRound,
  DataAnalysis,
  Document,
  Download,
  ZoomIn,
  Paperclip
} from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()
const positionStore = usePositionStore()

// 获取当前用户信息
const currentUser = computed(() => authStore.user)
const displayName = computed(() => {
  if (currentUser.value?.name) {
    return currentUser.value.name
  }
  if (currentUser.value?.username) {
    return currentUser.value.username
  }
  return '企业用户'
})

const API_BASE = '/company'

// 使用 markRaw 标记图标组件，避免 Vue 将其转换为响应式对象
const statsData = ref([
  {
    title: '发布职位',
    value: 0,
    icon: markRaw(DocumentChecked),
    color: '#409EFF',
    bgGradient: 'linear-gradient(135deg, #409EFF 0%, #66b1ff 100%)',
    route: '/company/jobs'
  },
  {
    title: '收到岗位申请',
    value: 0,
    icon: markRaw(User),
    color: '#67C23A',
    bgGradient: 'linear-gradient(135deg, #67C23A 0%, #85ce61 100%)',
    route: '/company/application-view'
  },
  {
    title: '待确认',
    value: 0,
    icon: markRaw(Bell),
    color: '#E6A23C',
    bgGradient: 'linear-gradient(135deg, #E6A23C 0%, #ebb563 100%)',
    route: '/company/internship-confirm'
  },
  {
    title: '已确认',
    value: 0,
    icon: markRaw(ChatDotRound),
    color: '#F56C6C',
    bgGradient: 'linear-gradient(135deg, #F56C6C 0%, #f78989 100%)',
    route: '/company/internship-confirm'
  }
])

const quickActions = ref([
  {
    title: '发布新职位',
    icon: markRaw(Plus),
    color: '#409EFF',
    route: '/company/jobs'
  },
  {
    title: '查看申请',
    icon: markRaw(View),
    color: '#67C23A',
    route: '/company/internship-confirm'
  },
  {
    title: '编辑企业信息',
    icon: markRaw(Edit),
    color: '#E6A23C',
    route: '/company/interviews'
  },
  {
    title: '账号设置',
    icon: markRaw(DocumentChecked),
    color: '#909399',
    route: '/company/settings'
  }
])

const recentActivities = ref([])

const notifications = ref([])

const unreadCount = computed(() => {
  return notifications.value.filter(n => !n.isRead).length
})

const fetchStats = async () => {
  console.log('开始获取统计数据')
  try {
    const response = await request({
      url: '/company/stats',
      method: 'get'
    })
    console.log('统计数据响应:', response)
    if (response.code === 200) {
      statsData.value[0].value = response.data.publishedPositions || 0
      statsData.value[1].value = response.data.totalApplications || 0
      statsData.value[2].value = response.data.pendingApplications || 0
      statsData.value[3].value = response.data.confirmedApplications || 0
    }
  } catch (error) {
    console.error('获取统计数据失败:', error)
  }
}

const fetchRecentActivities = async () => {
  try {
    const response = await request({
      url: '/company/applications/recent',
      method: 'get',
      params: { limit: 10 }
    })
    console.log('最新动态响应数据:', response)
    if (response.code === 200 && response.data) {
      recentActivities.value = response.data
        .map((app) => ({
          id: app.id,
          type: 'application',
          title: `${app.studentName || '学生'} 申请了 ${app.positionName || '岗位'}`,
          time: formatTime(app.applyTime || app.createTime),
          status: app.status,
          viewed: app.viewed !== undefined ? app.viewed : true
        }))
        .filter(app => !app.viewed) // 过滤掉已读的
      console.log('处理后的最新动态数据:', recentActivities.value)
    }
  } catch (error) {
    console.error('获取最新动态失败:', error)
  }
}

const formatTime = (date) => {
  if (!date) return '刚刚'
  const now = new Date()
  const time = new Date(date)
  const diff = Math.floor((now - time) / 1000)

  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`
  if (diff < 604800) return `${Math.floor(diff / 86400)}天前`
  return time.toLocaleDateString()
}

const fetchNotifications = async () => {
  try {
    const response = await request({
      url: '/company/notifications',
      method: 'get',
      params: { limit: 5 }
    })
    console.log('通知响应数据:', response)
    if (response.code === 200 && response.data) {
      console.log('通知数据长度:', response.data.length)
      notifications.value = response.data.map(item => ({
        id: item.id,
        type: item.type,
        title: item.title,
        content: item.content,
        time: item.time,
        priority: item.priority,
        positionId: item.positionId,
        positionName: item.positionName,
        isRead: item.isRead
      }))
      console.log('处理后的通知数据:', notifications.value)
    } else {
      console.log('响应码或数据异常:', response.code, response.data)
    }
  } catch (error) {
    console.error('获取通知失败:', error)
  }
}

const handleNavigate = (route) => {
  router.push(route)
}

const handleViewRecentActivity = async (activity) => {
  // 把同类型的所有未读动态标记为已读
  const activitiesToMark = recentActivities.value.filter(a => a.type === activity.type && !a.viewed)
  for (const item of activitiesToMark) {
    try {
      await request({
        url: `/company/applications/${item.id}/view`,
        method: 'put'
      })
    } catch (error) {
      console.error('标记已读失败:', error)
    }
  }
  // 从列表中移除已标记的记录
  recentActivities.value = recentActivities.value.filter(a => a.type !== activity.type)

  // 根据动态类型跳转到对应页面
  let route = ''
  if (activity.type === 'application') {
    route = '/company/application-view'
  }
  if (route) {
    router.push(route)
  }
}

const getStatusText = (status) => {
  const statusMap = {
    pending: '待确认',
    confirmed: '已确认',
    rejected: '已拒绝'
  }
  return statusMap[status] || status
}

const getStatusType = (status) => {
  const typeMap = {
    pending: 'warning',
    confirmed: 'success',
    rejected: 'danger'
  }
  return typeMap[status] || 'info'
}

const getPriorityText = (priority) => {
  const priorityMap = {
    high: '高',
    medium: '中',
    low: '低',
    info: '普通'
  }
  return priorityMap[priority] || priority
}

const getPriorityType = (priority) => {
  const typeMap = {
    high: 'danger',
    medium: 'warning',
    low: 'info',
    info: 'info'
  }
  return typeMap[priority] || 'info'
}

const getNotificationType = (type) => {
  const typeMap = {
    system: '系统通知',
    reminder: '重要提醒',
    policy: '政策更新',
    event: '活动通知',
    warning: '警告通知',
    recruitment_full: '招聘人数已满',
    position_expiring: '岗位即将到期'
  }
  return typeMap[type] || type
}

const getNotificationIcon = (type) => {
  const iconMap = {
    system: markRaw(Bell),
    reminder: markRaw(Message),
    policy: markRaw(DocumentChecked),
    event: markRaw(Calendar),
    warning: markRaw(Bell),
    recruitment_full: markRaw(Bell),
    position_expiring: markRaw(Message)
  }
  return iconMap[type] || markRaw(Bell)
}

const handleNotificationRefresh = () => {
  console.log('收到通知刷新事件')
  fetchStats()
  fetchRecentActivities()
  fetchNotifications()
}

const viewDialogVisible = ref(false)
const viewData = ref(null)
const filePreviewVisible = ref(false)
const currentFileUrl = ref('')
const currentFileName = ref('')

const handleViewAnnouncement = async (notification) => {
  try {
    const response = await request({
      url: `/announcements/${notification.id}`,
      method: 'get'
    })
    if (response.code === 200 && response.data) {
      viewData.value = response.data
      viewDialogVisible.value = true
      
      // 更新本地通知列表的阅读状态
      const targetNotification = notifications.value.find(n => n.id === notification.id)
      if (targetNotification) {
        targetNotification.isRead = true
      }
    } else {
      ElMessage.error('获取公告详情失败')
    }
  } catch (error) {
    console.error('获取公告详情失败:', error)
    ElMessage.error('获取公告详情失败')
  }
}

const parseAttachments = (attachments) => {
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

const previewFile = (attachment) => {
  if (!attachment) {
    ElMessage.error('附件信息不存在')
    return
  }

  const url = attachment.url ? String(attachment.url) : null
  const name = attachment.name ? String(attachment.name) : '附件'

  if (!url) {
    ElMessage.error('附件地址不存在')
    return
  }

  currentFileUrl.value = url
  currentFileName.value = name
  filePreviewVisible.value = true
}

const downloadFile = (url, name) => {
  try {
    const link = document.createElement('a')
    link.href = url
    link.download = name || 'download'
    link.target = '_blank'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    ElMessage.success('开始下载')
  } catch (error) {
    console.error('下载失败:', error)
    ElMessage.error('下载失败')
  }
}

const downloadFileWithConfirm = (attachment) => {
  if (!attachment) {
    ElMessage.error('附件信息不存在')
    return
  }

  const url = attachment.url ? String(attachment.url) : null
  const name = attachment.name ? String(attachment.name) : '附件'

  if (!url) {
    ElMessage.error('附件地址不存在')
    return
  }

  downloadFile(url, name)
}

const formatDate = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  if (isNaN(date.getTime())) return '-'
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const min = String(date.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${min}`
}

const getPublisherRoleText = (role) => {
  const map = {
    ADMIN: '管理员',
    COLLEGE: '学院教师',
    DEPARTMENT: '系室教师',
    COUNSELOR: '辅导员'
  }
  return map[role] || '-'
}

const getPublisherRoleType = (role) => {
  const map = {
    ADMIN: 'danger',
    COLLEGE: 'primary',
    DEPARTMENT: 'success',
    COUNSELOR: 'warning'
  }
  return map[role] || 'info'
}

const handleWebSocketMessage = (data) => {
  console.log('企业端收到WebSocket消息:', data)
  
  if (data.type === 'new_announcement') {
    ElMessage.success({
      message: `新公告：${data.data?.title || '未知标题'}`,
      duration: 5000,
      showClose: true
    })
    fetchNotifications()
  }
}

const initWebSocket = () => {
  let token = authStore.token
  
  if (!token) {
    const rolePrefix = 'company_'
    const role = 'ROLE_COMPANY'
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

onMounted(() => {
  console.log('CompanyHome 组件已挂载')
  fetchStats()
  fetchRecentActivities()
  fetchNotifications()

  initWebSocket()

  emitter.on('notification-refresh', handleNotificationRefresh)
})

onUnmounted(() => {
  console.log('CompanyHome 组件已卸载')
  disconnectAnnouncementWebSocket()
  emitter.off('notification-refresh', handleNotificationRefresh)
})
</script>

<template>
  <div class="company-home">
    <div class="welcome-section">
      <div class="welcome-card">
        <div class="welcome-content">
          <h1>欢迎回来，{{ displayName }}</h1>
          <p class="welcome-subtitle">今天是美好的一天，让我们一起管理实习招聘</p>
        </div>
        <div class="welcome-icon">
          <el-icon :size="80" color="rgba(255, 255, 255, 0.3)">
            <DataAnalysis />
          </el-icon>
        </div>
      </div>
    </div>

    <div class="stats-grid">
      <div
        v-for="(stat, index) in statsData"
        :key="index"
        class="stat-card"
        :style="{ background: stat.bgGradient }"
        @click="handleNavigate(stat.route)"
      >
        <div class="stat-icon">
          <el-icon :size="40" color="white">
            <component :is="stat.icon" />
          </el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stat.value }}</div>
          <div class="stat-title">{{ stat.title }}</div>
        </div>
        <div class="stat-arrow">
          <el-icon :size="24" color="rgba(255, 255, 255, 0.6)">
            <ArrowRight />
          </el-icon>
        </div>
      </div>
    </div>

    <div class="content-grid">
      <div class="content-card">
        <div class="card-header">
          <h3>
            <el-icon><TrendCharts /></el-icon>
            快捷操作
          </h3>
        </div>
        <div class="quick-actions">
          <div
            v-for="(action, index) in quickActions"
            :key="index"
            class="action-item"
            @click="handleNavigate(action.route)"
          >
            <div class="action-icon" :style="{ background: action.color }">
              <el-icon :size="24" color="white">
                <component :is="action.icon" />
              </el-icon>
            </div>
            <span class="action-title">{{ action.title }}</span>
          </div>
        </div>
      </div>

      <div class="content-card">
        <div class="card-header">
          <h3>
            <el-icon><Bell /></el-icon>
            最新动态
          </h3>
          <div class="header-right">
            <el-badge :value="recentActivities.length" :hidden="recentActivities.length === 0" class="unread-badge" />
          </div>
        </div>
        <el-scrollbar height="400px">
          <div class="activity-list">
            <div v-if="recentActivities.length === 0" class="empty-state">
              <el-icon :size="48" color="#C0C4CC">
                <TrendCharts />
              </el-icon>
              <p>暂无最新动态</p>
            </div>
            <div
              v-for="activity in recentActivities"
              :key="activity.id"
              class="activity-item"
              @click="handleViewRecentActivity(activity)"
            >
              <div class="activity-icon">
                <el-icon :size="20" :color="activity.status === 'pending' ? '#E6A23C' : activity.status === 'confirmed' ? '#67C23A' : '#F56C6C'">
                  <component :is="activity.status === 'pending' ? Bell : activity.status === 'confirmed' ? DocumentChecked : Message" />
                </el-icon>
                <div v-if="!activity.viewed" class="unread-dot"></div>
              </div>
              <div class="activity-content">
                <div class="activity-title">{{ activity.title }}</div>
                <div class="activity-meta">
                  <span class="activity-time">{{ activity.time }}</span>
                  <el-tag :type="getStatusType(activity.status)" size="small">
                    {{ getStatusText(activity.status) }}
                  </el-tag>
                </div>
              </div>
            </div>
          </div>
        </el-scrollbar>
      </div>

      <div class="content-card notification-card">
        <div class="card-header">
          <h3>
            <el-icon><Bell /></el-icon>
            通知公告
          </h3>
          <div class="header-right">
            <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="unread-badge" />
          </div>
        </div>
        <el-scrollbar height="400px">
          <div class="notification-list">
            <div v-if="notifications.length === 0" class="empty-state">
              <el-icon :size="48" color="#C0C4CC">
                <Bell />
              </el-icon>
              <p>暂无通知</p>
            </div>
            <div
              v-for="notification in notifications"
              :key="notification.id"
              class="notification-item"
              :class="{ 'unread': !notification.isRead }"
              @click="handleViewAnnouncement(notification)"
            >
              <div class="notification-icon">
                <el-icon :size="20" :color="notification.priority === 'high' ? '#F56C6C' : notification.priority === 'medium' ? '#E6A23C' : '#909399'">
                  <component :is="getNotificationIcon(notification.type)" />
                </el-icon>
                <div v-if="!notification.isRead" class="unread-dot"></div>
              </div>
              <div class="notification-content">
                <div class="notification-title">{{ notification.title }}</div>
                <div class="notification-desc">{{ notification.content }}</div>
                <div class="notification-meta">
                  <el-tag :type="getPriorityType(notification.priority)" size="small">
                    {{ getNotificationType(notification.type) }}
                  </el-tag>
                  <span class="notification-time">{{ notification.time }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-scrollbar>
      </div>
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
    <FilePreviewDialog
      v-model="filePreviewVisible"
      :file-url="currentFileUrl"
      :file-name="currentFileName"
    />
  </div>
</template>

<style scoped>
.company-home {
  padding: 0;
  max-width: 1600px;
  margin: 0 auto;
}

.welcome-section {
  margin-bottom: 16px;
}

.welcome-card {
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  color: white;
  padding: 24px;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(64, 158, 255, 0.3);
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: relative;
  overflow: hidden;
}

.welcome-card::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -20%;
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
  border-radius: 50%;
}

.welcome-content {
  flex: 1;
  position: relative;
  z-index: 1;
}

.welcome-content h1 {
  margin: 0 0 6px 0;
  font-size: 24px;
  font-weight: bold;
  letter-spacing: 1px;
  color: white;
}

.welcome-subtitle {
  margin: 0 0 0 0;
  font-size: 14px;
  opacity: 0.95;
}

.welcome-stats {
  display: flex;
  align-items: center;
  gap: 24px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-number {
  font-size: 32px;
  font-weight: bold;
}

.stat-label {
  font-size: 14px;
  opacity: 0.9;
}

.stat-divider {
  width: 1px;
  height: 40px;
  background: rgba(255, 255, 255, 0.3);
}

.welcome-icon {
  position: relative;
  z-index: 1;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.stat-card {
  background: white;
  padding: 24px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  gap: 16px;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.2) 0%, transparent 100%);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.stat-card:hover::before {
  opacity: 1;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.2);
  flex-shrink: 0;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: white;
  margin-bottom: 2px;
}

.stat-title {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.9);
}

.stat-arrow {
  opacity: 0.6;
  transition: opacity 0.3s ease;
}

.stat-card:hover .stat-arrow {
  opacity: 1;
}

.content-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.content-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.card-header {
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-header h3 .el-icon {
  color: #409EFF;
}

.header-right {
  display: flex;
  align-items: center;
}

.unread-badge {
  margin-right: 0;
}

.unread-badge :deep(.el-badge__content) {
  background-color: #f56c6c;
  border: 2px solid #fff;
  font-size: 12px;
  height: 18px;
  line-height: 14px;
  padding: 0 5px;
}

.quick-actions {
  padding: 20px;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 20px;
  border-radius: 8px;
  background: #f8f9fa;
  cursor: pointer;
  transition: all 0.3s ease;
}

.action-item:hover {
  background: #e8f4ff;
  transform: translateY(-2px);
}

.action-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.action-title {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}

.activity-list {
  padding: 0 24px;
}

.activity-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
  transition: background 0.2s ease;
  cursor: pointer;
}

.activity-item:hover {
  background: #f5f7fa;
}

.activity-item:last-child {
  border-bottom: none;
}

.activity-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  position: relative;
}

.activity-icon .unread-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 10px;
  height: 10px;
  background: #f56c6c;
  border-radius: 50%;
  border: 2px solid #fff;
}

.activity-content {
  flex: 1;
  min-width: 0;
}

.activity-title {
  font-size: 14px;
  color: #303133;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #909399;
}

.notification-list {
  padding: 0 24px;
}

.notification-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
  transition: background 0.2s ease;
  cursor: pointer;
}

.notification-item:hover {
  background: #f5f7fa;
}

.notification-item:last-child {
  border-bottom: none;
}

.notification-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  position: relative;
}

.unread-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 10px;
  height: 10px;
  background: #f56c6c;
  border-radius: 50%;
  border: 2px solid #fff;
}

.notification-content {
  flex: 1;
  min-width: 0;
}

.notification-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-desc {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

.notification-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #909399;
}

.notification-time {
  margin-left: auto;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #909399;
}

.empty-state p {
  margin: 16px 0 0 0;
  font-size: 14px;
}

:deep(.el-button--text) {
  padding: 4px 8px;
}

:deep(.el-button--text .el-icon) {
  margin-left: 4px;
}

.view-content {
  padding: 0 20px;
}

.view-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 20px 0;
}

.view-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 20px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.meta-label {
  font-size: 14px;
  color: #909399;
  font-weight: 500;
}

.meta-value {
  font-size: 14px;
  color: #303133;
}

.status-tag {
  margin-left: 4px;
}

.content-body {
  font-size: 14px;
  color: #606266;
  line-height: 1.8;
  min-height: 100px;
}

.attachments-section {
  margin-top: 20px;
}

.attachments-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
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
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.attachment-item:hover {
  background: #e8f4ff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.attachment-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  cursor: pointer;
}

.attachment-icon {
  font-size: 20px;
  color: #409EFF;
}

.attachment-name {
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-actions {
  display: flex;
  gap: 8px;
}

@media screen and (max-width: 1400px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .content-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media screen and (max-width: 992px) {
  .welcome-card {
    flex-direction: column;
    text-align: center;
  }

  .welcome-stats {
    justify-content: center;
  }

  .welcome-icon {
    display: none;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
