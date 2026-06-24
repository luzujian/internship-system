<template>
  <div class="home-container">
    <div class="welcome-section">
      <div class="welcome-content">
        <div class="welcome-left">
          <div class="avatar-wrapper" @click="changeAvatar">
            <img :src="avatarUrl || 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgdmlld0JveD0iMCAwIDEwMCAxMDAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgPGNpcmNsZSBjeD0iNTAiIGN5PSI1MCIgcj0iNTAiIGZpbGw9InVybCgjZ3JhZGllbnQpIi8+CiAgPGNpcmNsZSBjeD0iNTAiIGN5PSI0MiIgcj0iMjAiIGZpbGw9IiNmZmZmZmYiLz4KICA8cGF0aCBkPSJNMzAgODVINTBDMzAgNzUgMzUgNzUgMzUgODVMMzAgODVaIiBmaWxsPSIjZmZmZmZmIi8+CiAgPHBhdGggZD0iTTcwIDg1TDUwIDg1QzcwIDc1IDY1IDc1IDY1IDg1TDcwIDg1WiIgZmlsbD0iI2ZmZmZmZiIvPgogIDxkZWZzPgogICAgPGxpbmVhckdyYWRpZW50IGlkPSJncmFkaWVudCIgeDE9IjAlIiB5MT0iMCUiIHgyPSIxMDAlIiB5Mj0iMTAwJSI+CiAgICAgIDxzdG9wIG9mZnNldD0iMCUiIHN0b3AtY29sb3I9IiM0MDlFRkYiLz4KICAgICAgPHN0b3Agb2Zmc2V0PSIxMDAlIiBzdG9wLWNvbG9yPSIjNjdDMjNBIi8+CiAgICA8L2xpbmVhckdyYWRpZW50PgogIDwvZGVmcz4KPC9zdmc+'" class="avatar-img" alt="头像" />
            <div class="avatar-edit-overlay">
              <el-icon class="edit-icon"><Camera /></el-icon>
            </div>
          </div>
          <input
            ref="avatarInput"
            type="file"
            accept="image/*"
            style="display: none"
            @change="handleAvatarChange"
          />
          <div class="welcome-text">
            <h1 class="welcome-title">欢迎回来，{{ userName }}</h1>
            <p class="welcome-date">{{ currentDate }}</p>
            <div v-if="!hasProfile" class="profile-reminder">
              <span class="reminder-text">请先完善个人资料</span>
              <el-button type="primary" size="small" @click="goToProfile" class="reminder-btn">去完善</el-button>
            </div>
          </div>
        </div>
        <div class="welcome-right">
          <div class="path-selector">
            <div class="path-card" @click="goToJobs">
              <div class="path-icon path-icon-blue"><el-icon><Search /></el-icon></div>
              <div class="path-text">
                <span class="path-title">浏览岗位</span>
                <span class="path-desc">去职位页查看和投递</span>
              </div>
            </div>
            <div class="path-card" @click="openSelfPracticeDialog">
              <div class="path-icon path-icon-green"><el-icon><EditPen /></el-icon></div>
              <div class="path-text">
                <span class="path-title">自主实习</span>
                <span class="path-desc">自行联系实习单位</span>
              </div>
            </div>
            <div class="path-card" @click="openDelayDialog">
              <div class="path-icon path-icon-orange"><el-icon><Timer /></el-icon></div>
              <div class="path-text">
                <span class="path-title">考研延迟</span>
                <span class="path-desc">延迟实习准备考研</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="stats-grid">
      <div class="stat-item applied" @click="navigateToApplications">
        <div class="stat-icon">
          <el-icon><Document /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-label">已申请</div>
          <div class="stat-value">{{ stats.applied }}</div>
        </div>
      </div>
      <div class="stat-item interview" @click="navigateToInterviews">
        <div class="stat-icon">
          <el-icon><Message /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-label">面试邀请</div>
          <div class="stat-value">{{ stats.interviewInvites }}</div>
        </div>
      </div>
      <div class="stat-item current" @click="navigateToConfirmationForm">
        <div class="stat-icon">
          <el-icon><TrendCharts /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-label">确认记录</div>
          <div class="stat-value">{{ stats.current }}</div>
        </div>
      </div>
      <div class="stat-item pending" @click="navigateToInternships">
        <div class="stat-icon">
          <el-icon><Clock /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-label">心得已提交</div>
          <div class="stat-value">{{ stats.pendingReports }}</div>
        </div>
      </div>
    </div>

    <div class="content-grid">
      <div class="content-card todo-card">
        <div class="card-header">
          <div class="header-left">
            <el-icon class="header-icon warning"><Bell /></el-icon>
            <h3>待办事项</h3>
          </div>
          <span class="badge urgent">{{ todoList.length }}</span>
        </div>
        <div class="card-list">
          <div
            v-for="item in todoList"
            :key="item.id"
            class="list-item"
            @click="handleTodoClick(item)"
          >
            <div class="item-left">
              <div :class="['item-dot', item.priority]"></div>
              <div class="item-content">
                <div class="item-title">{{ item.title }}</div>
                <div class="item-desc">{{ item.desc }}</div>
              </div>
            </div>
            <el-icon class="item-arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>

      <div class="content-card progress-card">
        <div class="card-header">
          <div class="header-left">
            <el-icon class="header-icon info"><TrendCharts /></el-icon>
            <h3>实习进展</h3>
          </div>
        </div>
        <div class="card-list">
          <div
            v-for="item in sortedProgress"
            :key="item.id"
            class="list-item progress-item"
            @click="handleProgressClick(item)"
          >
            <div class="progress-top">
              <div class="item-title">{{ item.title }}</div>
              <div :class="['item-status', item.statusClass]">{{ item.status }}</div>
            </div>
            <div class="progress-bottom">
              <div class="item-desc">{{ item.desc }}</div>
              <div class="item-time">{{ item.time }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="content-card notification-card">
        <div class="card-header">
          <div class="header-left">
            <el-icon class="header-icon primary"><Message /></el-icon>
            <h3>重要通知</h3>
          </div>
          <span v-if="unreadCount > 0" class="badge new">{{ unreadCount }}</span>
        </div>
        <div class="card-list">
          <div v-if="sortedNotifications.length === 0" class="empty-notification">
            <el-icon><Message /></el-icon>
            <span>暂无未读消息</span>
          </div>
          <TransitionGroup name="notif">
            <div
              v-for="item in sortedNotifications"
              :key="item.id"
              :class="['list-item', { unread: item.unread }]"
              @click="handleNotificationClick(item)"
            >
              <div class="item-left">
                <div v-if="item.unread" class="unread-dot"></div>
                <div class="item-content">
                  <div class="item-title">{{ item.title }}</div>
                  <div class="item-desc">{{ item.desc }}</div>
                </div>
              </div>
              <div class="item-time">{{ item.time }}</div>
            </div>
          </TransitionGroup>
        </div>
        <div class="card-footer">
          <span class="view-more" @click="goToMessageCenter">查看详情</span>
        </div>
      </div>


    </div>

    <!-- 通知详情对话框 -->
    <el-dialog
      v-model="showNotificationDialog"
      title="通知详情"
      width="600px"
      class="edit-profile-dialog"
      :append-to-body="true"
      :lock-scroll="true"
      modal-class="global-modal"
      @closed="handleNotificationDialogClosed"
      data-custom-dialog="edit-profile"
    >
      <div v-if="selectedNotification" class="notification-detail">
        <div class="notification-header">
          <h3 class="notification-title">{{ selectedNotification.title }}</h3>
          <div :class="['notification-status', { unread: selectedNotification.unread }]">
            {{ selectedNotification.unread ? '未读' : '已读' }}
          </div>
        </div>
        <div class="notification-time">
          <el-icon><Clock /></el-icon>
          <span>{{ selectedNotification.time }}</span>
        </div>
        <div class="notification-content">
          {{ selectedNotification.desc }}
        </div>
      </div>
      <template #footer>
        <el-button @click="showNotificationDialog = false" class="close-btn">
          关闭
        </el-button>
      </template>
    </el-dialog>

    <!-- 自主实习申请弹窗 -->
    <el-dialog
      v-model="showSelfPracticeDialog"
      width="500px"
      class="onboarding-dialog"
      append-to-body
      lock-scroll
      modal-class="global-modal"
      :title="''"
    >
      <div class="dialog-content">
        <div class="header-section">
          <h1 class="header-title">自主实习申请</h1>
        </div>
        <div class="onboarding-form">
          <el-form label-width="85px">
            <el-form-item label="实习单位" required>
              <el-input v-model="selfPracticeForm.company" placeholder="请输入实习单位名称" clearable />
            </el-form-item>
            <el-form-item label="申请理由" required>
              <el-input v-model="selfPracticeForm.reason" type="textarea" placeholder="请简要说明自主实习原因" :rows="3" clearable />
            </el-form-item>
            <el-form-item label="个人申请书">
              <el-upload
                class="onboarding-upload"
                action="#"
                :auto-upload="false"
                :on-change="(file) => handleOnboardingFileChange(file, 'selfPractice', 'applicationLetter')"
                :file-list="selfPracticeFileList"
                accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
                :show-file-list="true"
                drag
              >
                <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                <div class="el-upload__text">点击或拖拽上传个人申请书</div>
              </el-upload>
            </el-form-item>
            <el-form-item label="实习接收函">
              <el-upload
                class="onboarding-upload"
                action="#"
                :auto-upload="false"
                :on-change="(file) => handleOnboardingFileChange(file, 'selfPractice', 'acceptanceLetter')"
                :file-list="selfPracticeAcceptanceFileList"
                accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
                :show-file-list="true"
                drag
              >
                <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                <div class="el-upload__text">点击或拖拽上传企业实习接收函</div>
              </el-upload>
            </el-form-item>
          </el-form>
        </div>
      </div>
      <template #footer>
        <el-button @click="showSelfPracticeDialog = false">取消</el-button>
        <el-button type="primary" @click="submitOnboardingApplication('selfPractice')" :loading="onboardingSubmitting">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 考研延迟申请弹窗 -->
    <el-dialog
      v-model="showDelayDialog"
      width="500px"
      class="onboarding-dialog"
      append-to-body
      lock-scroll
      modal-class="global-modal"
      :title="''"
    >
      <div class="dialog-content">
        <div class="header-section">
          <h1 class="header-title">考研延迟申请</h1>
        </div>
        <div class="onboarding-form">
          <el-form label-width="85px">
            <el-form-item label="申请理由" required>
              <el-input v-model="delayForm.reason" type="textarea" placeholder="请简要说明考研延迟原因" :rows="3" clearable />
            </el-form-item>
            <el-form-item label="考研计划">
              <el-upload
                class="onboarding-upload"
                action="#"
                :auto-upload="false"
                :on-change="(file) => handleOnboardingFileChange(file, 'delay')"
                :file-list="delayFileList"
                accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
                :show-file-list="true"
                drag
              >
                <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                <div class="el-upload__text">点击或拖拽上传考研计划</div>
              </el-upload>
            </el-form-item>
          </el-form>
        </div>
      </div>
      <template #footer>
        <el-button @click="showDelayDialog = false">取消</el-button>
        <el-button type="primary" @click="submitOnboardingApplication('delay')" :loading="onboardingSubmitting">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import request from '@/utils/request'
import {
  Bell,
  TrendCharts,
  Message,
  ArrowRight,
  Camera,
  Document,
  Clock,
  Search,
  EditPen,
  Timer,
  UploadFilled
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// 从 auth store 获取学生 ID
const getStudentId = () => {
  return authStore.user?.studentId || authStore.user?.id || ''
}

const userName = computed(() => authStore.user?.name || authStore.user?.username || '同学')
const avatarUrl = ref('')
const avatarInput = ref(null)

const stats = ref({
  applied: 0,
  interviewInvites: 0,
  current: 0,
  pendingReports: 0
})

const fetchDashboardStats = async () => {
  try {
    const studentId = getStudentId()
    console.log('开始获取统计数据，studentId:', studentId)
    console.log('authStore.user:', authStore.user)
    console.log('请求URL:', `/student/home/stats`)

    const requestConfig = studentId ? { params: { studentId } } : {}
    const response = await request.get(`/student/home/stats`, requestConfig)

    console.log('API响应原始数据:', JSON.stringify(response))

    // response 是 axios response.data，即 { code: 200, data: {...} }
    if (response && response.code === 200) {
      const apiData = response.data || response
      stats.value = {
        applied: apiData.applied ?? 0,
        interviewInvites: apiData.interviewInvites ?? 0,
        current: apiData.current ?? 0,
        pendingReports: apiData.pendingReports ?? 0
      }
      console.log('更新后的统计数据:', stats.value)
    } else if (response && response.code !== 200) {
      console.error('API返回错误:', response.message)
    } else {
      console.error('API响应格式异常:', response)
    }
  } catch (error) {
    console.error('获取统计数据失败:', error)
  }
}

const fetchDashboardTodos = async () => {
  try {
    const studentId = getStudentId()
    const requestConfig = studentId ? { params: { studentId } } : {}
    const response = await request.get(`/student/home/todo-list`, requestConfig)

    if (response && response.code === 200) {
      const listData = response.data || []
      todoList.value = listData.map(item => ({
        id: item.id,
        title: item.title,
        desc: item.description,
        priority: item.priority,
        route: getRouteByType(item.type),
        type: item.type,
        interviewId: item.interviewId
      }))
    }
  } catch (error) {
    console.error('获取待办事项失败:', error)
  }
}

const fetchDashboardProgress = async () => {
  try {
    const studentId = getStudentId()
    const requestConfig = studentId ? { params: { studentId } } : {}
    const response = await request.get(`/student/home/progress-list`, requestConfig)

    if (response && response.code === 200) {
      const listData = response.data || []
      progressList.value = listData.map(item => {
        // 状态中文映射
        const statusMap = {
          'pending': { text: '待处理', class: 'pending' },
          'warning': { text: '待确认', class: 'warning' },
          'success': { text: '已通过', class: 'success' },
          'failed': { text: '未通过', class: 'failed' },
          'submitted': { text: '已提交', class: 'warning' },
          'scored': { text: '已评分', class: 'success' }
        }
        const statusInfo = statusMap[item.status] || { text: item.status, class: 'pending' }
        return {
          id: item.id,
          time: formatTime(item.time),
          title: item.title,
          desc: item.description,
          status: statusInfo.text,
          statusClass: statusInfo.class,
          eventType: item.eventType,
          timestamp: item.time ? new Date(item.time).getTime() : 0
        }
      })
    }
  } catch (error) {
    console.error('获取实习进展失败:', error)
  }
}

const fetchDashboardNotifications = async () => {
  try {
    const studentId = getStudentId()
    const requestConfig = studentId ? { params: { studentId } } : {}
    const response = await request.get(`/student/home/notifications`, requestConfig)

    if (response && response.code === 200) {
      const listData = response.data || []
      notificationList.value = listData.map(item => ({
        id: item.id,
        title: item.title,
        desc: item.content,
        time: formatTime(item.notificationTime),
        unread: !item.isRead,
        timestamp: new Date(item.notificationTime).getTime()
      }))
    }
  } catch (error) {
    console.error('获取通知失败:', error)
  }
}

const getRouteByType = (type) => {
  const routes = {
    'interview': '/student/interviews',
    'application': '/student/applications',
    'report': '/student/internships',
    'reflection': '/student/internship-reflection/submit',
    'other': '/student/home'
  }
  return routes[type] || '/student/home'
}

const formatTime = (dateTime) => {
  const date = new Date(dateTime)
  const now = new Date()
  const diff = now - date
  
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  
  if (minutes < 60) {
    return `${minutes}分钟前` // 修复：原代码是小时前，这里改为分钟前更合理
  } else if (hours < 24) {
    return `${hours}小时前`
  } else if (days < 7) {
    return `${days}天前`
  } else {
    const month = date.getMonth() + 1
    const day = date.getDate()
    return `${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  }
}

const currentDate = computed(() => {
  const now = new Date()
  const options = { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }
  return now.toLocaleDateString('zh-CN', options)
})

const todoList = ref([])

const progressList = ref([])

const notificationList = ref([])

const unreadCount = computed(() => {
  return notificationList.value.filter(item => item.unread).length
})

const sortedNotifications = computed(() => {
  return [...notificationList.value]
    .filter(item => item.unread)
    .sort((a, b) => b.timestamp - a.timestamp)
})

const sortedProgress = computed(() => {
  return [...progressList.value].sort((a, b) => b.timestamp - a.timestamp)
})

const handleTodoClick = async (item) => {
  if (item.type === 'interview' && item.interviewId) {
    // 对于面试相关待办，导航到面试页面并可以传递面试ID参数
    router.push({
      path: item.route,
      query: { interviewId: item.interviewId }
    })
  } else {
    router.push(item.route)
  }

  try {
    // TODO: 实现待办完成 API
    console.log('标记待办为已完成:', item.id)
  } catch (error) {
    console.error('标记待办为已完成失败:', error)
  }
}

const handleProgressClick = (item) => {
  const routeMap = {
    interview: '/student/interviews',
    job_application: '/student/applications',
    internship_confirmation: '/student/internship-confirmation-form',
    reflection_submit: '/student/internship-reflection/submit',
    unit_change_application: '/student/internship-confirmation-form'
  }
  router.push(routeMap[item.eventType] || '/student/internships')
}

const handleNotificationClick = async (item) => {
  selectedNotification.value = item
  showNotificationDialog.value = true

  if (item.unread) {
    try {
      await request.post('/announcement-read-records', {
        announcementId: item.id,
        userId: String(authStore.user?.id || ''),
        userType: 'STUDENT'
      })
      // 先标记已读但不从列表移除，等弹窗关闭后再移除（触发过渡动画）
      pendingDismissId.value = item.id
    } catch (e) {
      console.error('标记已读失败:', e)
    }
  }
}

const handleNotificationDialogClosed = () => {
  if (pendingDismissId.value) {
    const item = notificationList.value.find(n => n.id === pendingDismissId.value)
    if (item) {
      item.unread = false
    }
    pendingDismissId.value = null
  }
}

const navigateToApplications = () => {
  router.push('/student/applications')
}

const navigateToInterviews = () => {
  router.push('/student/interviews')
}

const navigateToConfirmationForm = () => {
  router.push('/student/internship-confirmation-form')
}

const navigateToInternships = () => {
  router.push('/student/internships')
}

const changeAvatar = () => {
  if (avatarInput.value) {
    avatarInput.value.click()
  }
}

const handleAvatarChange = async (event) => {
  const file = event.target.files[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    return
  }

  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 5MB')
    return
  }

  // 先本地预览
  const reader = new FileReader()
  reader.onload = (e) => {
    avatarUrl.value = e.target.result
  }
  reader.readAsDataURL(file)

  // 上传到 OSS
  try {
    const formData = new FormData()
    formData.append('file', file)
    const response = await request.post('/student/profile/upload/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (response.code === 200) {
      avatarUrl.value = response.data.url
      ElMessage.success('头像上传成功')
    } else {
      ElMessage.error(response.message || '头像上传失败')
    }
  } catch (error) {
    console.error('头像上传失败:', error)
    ElMessage.error('头像上传失败，请稍后重试')
  }
}

const goToProfile = () => {
  // 导航到个人中心，并在返回时重新检查个人资料状态
  router.push({
    path: '/student/profile',
    query: { returnTo: '/student/home' }
  })
}

const goToMessageCenter = () => {
  // 导航到个人中心的消息中心
  router.push({
    path: '/student/profile',
    query: { tab: 'messageCenter' }
  })
}

// 监听路由变化，当从个人中心返回时重新检查个人资料状态
watch(() => route.path, (newPath, oldPath) => {
  if (newPath === '/student/home' && oldPath === '/student/profile') {
    // 从个人中心返回，重新检查个人资料状态
    fetchProfileStatus()
  }
})

const showNotificationDialog = ref(false)
const selectedNotification = ref(null)
const pendingDismissId = ref(null)
const hasProfile = ref(false)

// 弹窗和表单状态
const showSelfPracticeDialog = ref(false)
const showDelayDialog = ref(false)
const onboardingSubmitting = ref(false)
const selfPracticeForm = ref({ company: '', reason: '', applicationLetterUrl: '', acceptanceLetterUrl: '' })
const delayForm = ref({ reason: '', materialUrl: '' })
const selfPracticeFileList = ref([])
const selfPracticeAcceptanceFileList = ref([])
const delayFileList = ref([])

const goToJobs = () => {
  router.push('/student/jobs')
}

const openSelfPracticeDialog = () => {
  selfPracticeForm.value = { company: '', reason: '', applicationLetterUrl: '', acceptanceLetterUrl: '' }
  selfPracticeFileList.value = []
  selfPracticeAcceptanceFileList.value = []
  showSelfPracticeDialog.value = true
}

const openDelayDialog = () => {
  delayForm.value = { reason: '', materialUrl: '' }
  delayFileList.value = []
  showDelayDialog.value = true
}

const handleOnboardingFileChange = async (file, type, subType) => {
  try {
    const formData = new FormData()
    formData.append('file', file.raw)
    const response = await request.post('/upload/file', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (response.code === 200 && response.data && response.data.url) {
      if (type === 'selfPractice') {
        if (subType === 'applicationLetter') {
          selfPracticeForm.value.applicationLetterUrl = response.data.url
        } else if (subType === 'acceptanceLetter') {
          selfPracticeForm.value.acceptanceLetterUrl = response.data.url
        }
      } else {
        delayForm.value.materialUrl = response.data.url
      }
      ElMessage.success('文件上传成功')
    } else {
      ElMessage.error(response.message || '文件上传失败')
    }
  } catch (error) {
    console.error('文件上传失败:', error)
    ElMessage.error('文件上传失败')
  }
}

const submitOnboardingApplication = async (type) => {
  if (type === 'selfPractice') {
    if (!selfPracticeForm.value.company) {
      ElMessage.error('请填写实习单位')
      return
    }
    if (!selfPracticeForm.value.reason) {
      ElMessage.error('请填写申请理由')
      return
    }
  } else if (type === 'delay') {
    if (!delayForm.value.reason) {
      ElMessage.error('请填写申请理由')
      return
    }
  }

  onboardingSubmitting.value = true
  try {
    const user = authStore.user
    const applicationData = {
      applicationType: type === 'selfPractice' ? 'selfPractice' : 'delay',
      studentName: user?.name || '',
      studentUserId: String(user?.studentId || user?.id || ''),
      grade: user?.grade || '',
      className: user?.class || '',
      phone: user?.phone || '',
      reason: type === 'selfPractice' ? selfPracticeForm.value.reason : delayForm.value.reason,
      status: 'pending'
    }

    if (type === 'selfPractice') {
      applicationData.company = selfPracticeForm.value.company
      const materials = {}
      if (selfPracticeForm.value.applicationLetterUrl) materials['个人申请书'] = selfPracticeForm.value.applicationLetterUrl
      if (selfPracticeForm.value.acceptanceLetterUrl) materials['实习接收函'] = selfPracticeForm.value.acceptanceLetterUrl
      applicationData.materials = materials
    } else if (type === 'delay') {
      applicationData.materials = delayForm.value.materialUrl ? { delayPlan: delayForm.value.materialUrl } : {}
    }

    const response = await request.post('/student/applications', applicationData)
    if (response.code === 200) {
      ElMessage.success('申请提交成功')
      if (type === 'selfPractice') {
        showSelfPracticeDialog.value = false
      } else {
        showDelayDialog.value = false
      }
    } else {
      ElMessage.error(response.message || '提交申请失败')
    }
  } catch (error) {
    console.error('提交申请失败:', error)
    ElMessage.error('提交申请失败')
  } finally {
    onboardingSubmitting.value = false
  }
}

const fetchProfileStatus = async () => {
  try {
    const studentId = getStudentId()
    const requestConfig = studentId ? { params: { studentId } } : {}
    const response = await request.get(`/student/profile`, requestConfig)
    if (response && response.code === 200 && response.data) {
      const profile = response.data
      // 设置头像（无头像时清空，避免显示他人缓存的头像）
      avatarUrl.value = profile.avatar || ''
      // 检查是否有基本个人信息
      if (profile.name && profile.studentId && profile.phone && profile.email) {
        hasProfile.value = true
      }
    }
  } catch (error) {
    console.error('获取个人资料状态失败:', error)
    // 模拟数据：默认未完善个人资料
    hasProfile.value = false
  }
}

onMounted(async () => {
  try {
    await Promise.all([
      fetchDashboardStats(),
      fetchDashboardTodos(),
      fetchDashboardProgress(),
      fetchDashboardNotifications(),
      fetchProfileStatus()
    ])
  } catch (error) {
    console.error('加载数据失败:', error)
  }
})
</script>

<style scoped>
/* 核心布局：1080p 标准固定设计，小屏出现滚动条 */
.home-container {
  width: 100%;
  min-height: calc(100vh - 64px - 40px);
  display: flex;
  flex-direction: column;
  background: transparent !important;
  padding: 0 !important;
  margin: 0 !important;
  box-sizing: border-box !important;
}

/* 统一卡片和容器样式，与全局布局风格保持一致 */
.welcome-section {
  flex-shrink: 0;
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  border-radius: 12px;
  padding: 24px 32px;
  margin-bottom: 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  position: relative;
  overflow: hidden;
}

.welcome-section::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -10%;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
  border-radius: 50%;
}

.welcome-section::after {
  content: '';
  position: absolute;
  bottom: -30%;
  left: -5%;
  width: 200px;
  height: 200px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.08) 0%, transparent 70%);
  border-radius: 50%;
}

.welcome-content {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
}

.welcome-left {
  display: flex;
  align-items: center;
  gap: 20px;
  flex: 1;
  min-width: 0;
}

/* 右侧路径选择区域 */
.welcome-right {
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  padding: 12px 16px;
  backdrop-filter: blur(6px);
}

.path-selector {
  display: flex;
  gap: 10px;
}

.path-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.3s ease;
  background: rgba(255, 255, 255, 0.92);
  min-width: 140px;
}

.path-card:hover {
  background: #fff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.path-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.path-icon-blue {
  background: #409eff;
  color: #fff;
}

.path-icon-green {
  background: #67c23a;
  color: #fff;
}

.path-icon-orange {
  background: #e6a23c;
  color: #fff;
}

.path-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.path-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
}

.path-desc {
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
}

.avatar-wrapper {
  position: relative;
  width: 80px;
  height: 80px;
  cursor: pointer;
  transition: transform 0.3s ease;
}

.avatar-wrapper:hover {
  transform: scale(1.05);
}

.avatar-img {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  border: 3px solid rgba(255, 255, 255, 0.3);
  object-fit: cover;
}

.avatar-edit-overlay {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 28px;
  height: 28px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.avatar-wrapper:hover .avatar-edit-overlay {
  opacity: 1;
}

.edit-icon {
  font-size: 14px;
  color: #409eff;
}

.welcome-text h1 {
  font-size: 28px;
  font-weight: 700;
  color: white;
  margin: 0 0 8px 0;
}

.welcome-text p {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
  margin: 0;
}

/* 个人资料提醒样式 */
.profile-reminder {
  margin-top: 12px;
  padding: 8px 14px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  border-left: 3px solid rgba(255, 255, 255, 0.4);
  display: flex;
  align-items: center;
  gap: 12px;
}

.reminder-text {
  font-size: 13px;
  color: white;
  margin: 0;
}

.reminder-btn {
  padding: 5px 14px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 6px;
  background: white;
  color: #409EFF !important;
  border: 1px solid white !important;
  transition: all 0.3s ease;
}

.reminder-btn:hover {
  background: rgba(255, 255, 255, 0.9) !important;
  color: #1e88e5 !important;
}

/* 统计数据网格 */
.stats-grid {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

/* 统计项 */
.stat-item {
  background: white;
  border-radius: 10px;
  padding: 14px 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 12px;
  border-left: 4px solid;
  cursor: pointer;
}

.stat-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

/* 统计项颜色 */
.stat-item.applied {
  border-left-color: #409EFF;
}

.stat-item.interview {
  border-left-color: #67C23A;
}

.stat-item.current {
  border-left-color: #E6A23C;
}

.stat-item.pending {
  border-left-color: #F56C6C;
}

/* 统计图标 */
.stat-icon {
  font-size: 20px;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-item.applied .stat-icon {
  background: #ecf5ff;
  color: #409EFF;
}

.stat-item.interview .stat-icon {
  background: #f0f9eb;
  color: #67C23A;
}

.stat-item.current .stat-icon {
  background: #fdf6ec;
  color: #E6A23C;
}

.stat-item.pending .stat-icon {
  background: #fef0f0;
  color: #F56C6C;
}

/* 统计信息 */
.stat-info {
  flex: 1;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  font-weight: 500;
  margin: 0 0 4px 0;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
}

.stat-item.applied .stat-value {
  color: #409EFF;
}

.stat-item.interview .stat-value {
  color: #67C23A;
}

.stat-item.current .stat-value {
  color: #E6A23C;
}

.stat-item.pending .stat-value {
  color: #F56C6C;
}

/* 响应式布局 */
@media screen and (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media screen and (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
  
  .stat-item {
    padding: 16px;
  }
  
  .stat-icon {
    width: 40px;
    height: 40px;
    font-size: 20px;
  }
  
  .stat-value {
    font-size: 24px;
  }
}

/* 内容网格 — 固定高度设计，1080p 为标准 */
.content-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.content-card {
  background: white;
  border-radius: 12px;
  padding: 0;
  margin-bottom: 0;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  height: 540px;
}

.content-card .card-list {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.card-footer {
  padding: 12px 20px;
  border-top: 1px solid #f0f0f0;
  text-align: center;
}

.view-more {
  color: #409eff;
  font-size: 14px;
  cursor: pointer;
}

.view-more:hover {
  color: #66b1ff;
}

.card-header {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-icon {
  font-size: 20px;
}

.header-icon.warning {
  color: #e6a23c;
}

.header-icon.info {
  color: #409eff;
}

.header-icon.primary {
  color: #67c23a;
}

.card-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.badge {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
}

.badge.urgent {
  background: #fef3c7;
  color: #d97706;
}

.badge.new {
  background: #fee2e2;
  color: #dc2626;
}

.card-list {
  padding: 0;
  position: relative;
}

/* TransitionGroup 通知项过渡动画 */
.notif-move,
.notif-enter-active,
.notif-leave-active {
  transition: all 0.4s ease;
}

.notif-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}

.notif-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

.notif-leave-active {
  position: absolute;
  width: 100%;
}

.list-item {
  padding: 12px 16px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.list-item:last-child {
  border-bottom: none;
}

.list-item:hover {
  background: #f8fafc;
}

.item-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.item-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.item-dot.high {
  background: #f56c6c;
}

.item-dot.medium {
  background: #e6a23c;
}

.item-dot.low {
  background: #67c23a;
}

.item-content {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.item-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-desc {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-arrow {
  color: #c0c4cc;
  font-size: 16px;
  transition: transform 0.3s ease;
}

.list-item:hover .item-arrow {
  transform: translateX(4px);
  color: #409eff;
}

.item-time {
  font-size: 12px;
  color: #909399;
  min-width: 50px;
  text-align: left;
  margin-right: 8px;
}

.item-status {
  padding: 4px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
  margin-left: 4px;
  white-space: nowrap;
}

.item-status.success {
  background: #ecf5ff;
  color: #409eff;
}

.item-status.warning {
  background: #fff7e6;
  color: #fa8c16;
}

.item-status.pending {
  background: #f5f5f5;
  color: #909399;
}

.item-status.failed {
  background: #fff1f0;
  color: #ff4d4f;
}

/* 实习进展卡片优化 */
.progress-item {
  flex-direction: column;
  align-items: stretch;
  padding: 14px 20px;
}

.progress-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.progress-top .item-title {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 0;
  -webkit-line-clamp: 1;
}

.progress-bottom {
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  gap: 0;
}

.progress-bottom .item-desc {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  -webkit-line-clamp: 2;
  margin-bottom: 0;
  padding-right: 24px;
}

.progress-bottom .item-time {
  font-size: 12px;
  color: #c0c4cc;
  white-space: nowrap;
  flex-shrink: 0;
}

.empty-notification {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #c0c4cc;
  font-size: 14px;
  gap: 8px;
}

.empty-notification .el-icon {
  font-size: 36px;
}

.list-item.unread {
  background: #f0f9ff;
}

.unread-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #409eff;
  flex-shrink: 0;
}

/* 响应式适配 */
@media screen and (max-width: 1200px) {
  .content-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media screen and (max-width: 768px) {
  .welcome-section {
    padding: 24px 20px;
  }

  .welcome-left {
    flex-direction: column;
    text-align: center;
  }

  .welcome-text h1 {
    font-size: 24px;
  }

  .content-grid {
    grid-template-columns: 1fr;
  }

}

@media screen and (max-width: 480px) {
  .welcome-text h1 {
    font-size: 20px;
  }

  .stat-value {
    font-size: 24px;
  }
}

/* 通知详情弹窗样式 */
.notification-detail {
  padding: 0;
}

.notification-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 2px solid #f0f0f0;
}

.notification-title {
  font-size: 20px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.notification-status {
  padding: 6px 16px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 500;
  background: #f1f5f9;
  color: #64748b;
}

.notification-status.unread {
  background: #fef3c7;
  color: #d97706;
}

.notification-time {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  font-size: 14px;
  color: #64748b;
}

.notification-time .el-icon {
  color: #409EFF;
  font-size: 16px;
}

.notification-content {
  font-size: 15px;
  color: #475569;
  line-height: 1.8;
  padding: 20px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.close-btn {
  padding: 10px 32px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 8px;
  transition: all 0.3s ease;
  border: 1px solid #dcdfe6;
  background: white;
  color: #606266;
}

.close-btn:hover {
  border-color: #409eff;
  color: #409eff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2);
}

/* 实习方向选择弹窗样式 */
.onboarding-dialog :deep(.el-dialog) {
  border-radius: 8px;
  overflow: hidden;
}

.onboarding-dialog :deep(.el-dialog__header) {
  padding: 0;
  border-bottom: none;
  margin: 0;
}

.onboarding-dialog :deep(.el-dialog__title) {
  display: none;
}

.onboarding-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.onboarding-dialog .header-section {
  background: linear-gradient(90deg, #1e88e5 0%, #4caf50 100%);
  padding: 18px 20px;
  margin-bottom: 0;
}

.onboarding-dialog .header-title {
  color: white;
  font-size: 20px;
  font-weight: 600;
  text-align: center;
  margin: 0;
}

.onboarding-form {
  padding: 20px 24px;
}

.onboarding-upload :deep(.el-upload-dragger) {
  width: 100%;
  padding: 16px 12px;
  border: 2px dashed #d1d5db;
  border-radius: 4px;
  background: #fafafa;
}

.onboarding-upload :deep(.el-upload-dragger:hover) {
  border-color: #1e88e5;
  background: #f0f9ff;
}

/* 响应式：小屏时路径选择器换行 */
@media screen and (max-width: 1200px) {
  .welcome-content {
    flex-direction: column;
    align-items: flex-start;
  }

  .welcome-right {
    width: 100%;
  }

  .path-selector {
    flex-wrap: wrap;
  }

  .path-card {
    flex: 1;
    min-width: 120px;
  }
}
</style>