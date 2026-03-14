<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import emitter from '@/utils/event-bus'
import { usePositionStore } from '@/store/position'
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
  DataAnalysis
} from '@element-plus/icons-vue'

const router = useRouter()
const positionStore = usePositionStore()

const API_BASE = '/api/company'

const statsData = ref([
  {
    title: '发布职位',
    value: 0,
    icon: DocumentChecked,
    color: '#409EFF',
    bgGradient: 'linear-gradient(135deg, #409EFF 0%, #66b1ff 100%)',
    route: '/company/jobs'
  },
  {
    title: '收到岗位申请',
    value: 0,
    icon: User,
    color: '#67C23A',
    bgGradient: 'linear-gradient(135deg, #67C23A 0%, #85ce61 100%)',
    route: '/company/application-view'
  },
  {
    title: '待确认',
    value: 0,
    icon: Bell,
    color: '#E6A23C',
    bgGradient: 'linear-gradient(135deg, #E6A23C 0%, #ebb563 100%)',
    route: '/company/internship-confirm'
  },
  {
    title: '已确认',
    value: 0,
    icon: ChatDotRound,
    color: '#F56C6C',
    bgGradient: 'linear-gradient(135deg, #F56C6C 0%, #f78989 100%)',
    route: '/company/internship-confirm'
  }
])

const quickActions = ref([
  {
    title: '发布新职位',
    icon: Plus,
    color: '#409EFF',
    route: '/company/jobs'
  },
  {
    title: '查看申请',
    icon: View,
    color: '#67C23A',
    route: '/company/internship-confirm'
  },
  {
    title: '编辑企业信息',
    icon: Edit,
    color: '#E6A23C',
    route: '/company/interviews'
  },
  {
    title: '账号设置',
    icon: DocumentChecked,
    color: '#909399',
    route: '/company/settings'
  }
])

const recentActivities = ref([
  {
    id: 1,
    type: 'application',
    title: '张三 申请了 前端开发工程师',
    time: '10分钟前',
    status: 'pending'
  },
  {
    id: 2,
    type: 'application',
    title: '李四 申请了 后端开发工程师',
    time: '25分钟前',
    status: 'pending'
  },
  {
    id: 3,
    type: 'confirmed',
    title: '已确认 王五 的实习申请',
    time: '1小时前',
    status: 'confirmed'
  },
  {
    id: 4,
    type: 'application',
    title: '赵六 申请了 算法工程师',
    time: '2小时前',
    status: 'pending'
  },
  {
    id: 5,
    type: 'rejected',
    title: '已拒绝 钱七 的实习申请',
    time: '3小时前',
    status: 'rejected'
  }
])

const notifications = ref([])

const fetchStats = async () => {
  console.log('开始获取统计数据')
  try {
    const response = await request({
      url: `${API_BASE}/stats?companyId=3`,
      method: 'get'
    })
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
      url: `${API_BASE}/applications/recent?companyId=3&limit=5`,
      method: 'get'
    })
    console.log('最新动态响应数据:', response)
    if (response.code === 200 && response.data && response.data.length > 0) {
      console.log('第一条数据:', response.data[0])
      recentActivities.value = response.data.map((app) => ({
        id: app.id,
        type: 'application',
        title: `${app.studentName || '学生'} 申请了 ${app.positionName || '岗位'}`,
        time: formatTime(app.applyTime || app.createTime),
        status: app.status
      }))
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
      url: `/api/company/notifications?limit=5`,
      method: 'get'
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
        positionName: item.positionName
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
    system: Bell,
    reminder: Message,
    policy: DocumentChecked,
    event: Calendar,
    warning: Bell,
    recruitment_full: Bell,
    position_expiring: Message
  }
  return iconMap[type] || Bell
}

onMounted(() => {
  console.log('CompanyHome组件已挂载')
  fetchStats()
  fetchRecentActivities()
  fetchNotifications()
  
  emitter.on('notification-refresh', () => {
    console.log('收到通知刷新事件')
    fetchStats()
    fetchRecentActivities()
    fetchNotifications()
  })
})

onUnmounted(() => {
  console.log('CompanyHome组件已卸载')
  emitter.off('notification-refresh', fetchNotifications)
})
</script>

<template>
  <div class="company-home">
    <div class="welcome-section">
      <div class="welcome-card">
        <div class="welcome-content">
          <h1>欢迎回来，企业用户</h1>
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
          <el-button type="primary" text @click="handleNavigate('/company/internship-confirm')">
            查看全部
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
        <div class="activity-list">
          <div 
            v-for="activity in recentActivities" 
            :key="activity.id"
            class="activity-item"
          >
            <div class="activity-icon">
              <el-icon :size="20" :color="activity.status === 'pending' ? '#E6A23C' : activity.status === 'confirmed' ? '#67C23A' : '#F56C6C'">
                <component :is="activity.status === 'pending' ? Bell : activity.status === 'confirmed' ? DocumentChecked : Message" />
              </el-icon>
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
      </div>

      <div class="content-card notification-card">
        <div class="card-header">
          <h3>
            <el-icon><Bell /></el-icon>
            通知公告
          </h3>
          <el-tag type="info">{{ notifications.length }} 条通知</el-tag>
        </div>
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
          >
            <div class="notification-icon">
              <el-icon :size="20" :color="notification.priority === 'high' ? '#F56C6C' : notification.priority === 'medium' ? '#E6A23C' : '#909399'">
                <component :is="getNotificationIcon(notification.type)" />
              </el-icon>
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
      </div>
    </div>
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
  max-height: 400px;
  overflow-y: auto;
}

.activity-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
  transition: background 0.2s ease;
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
  max-height: 400px;
  overflow-y: auto;
}

.notification-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
  transition: background 0.2s ease;
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
