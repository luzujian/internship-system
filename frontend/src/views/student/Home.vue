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
              <p class="reminder-text">请完善您的个人资料，以便系统为您提供更准确的服务</p>
              <el-button type="primary" size="small" @click="goToProfile" class="reminder-btn">
                去完善
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>



    <!-- 实习状态查看 -->
    <div class="internship-status-display" v-if="hasRegistered">
      <div class="status-display-content">
        <div class="status-display-left">
          <div class="current-status-label">当前状态</div>
          <div :class="['current-status-badge', currentStatusClass]">
            {{ currentStatusText }}
          </div>
        </div>
        <div class="status-display-right">
          <el-button @click="openStatusDialog" class="update-btn">
            <el-icon><View /></el-icon>
            查看详情
          </el-button>
        </div>
      </div>
    </div>

    <!-- 无状态时显示提示 -->
    <div class="internship-notice-card" v-else>
      <div class="notice-content">
        <div class="notice-icon">
          <el-icon class="notice-icon-inner"><Briefcase /></el-icon>
        </div>
        <div class="notice-text">
          <h3 class="notice-title">暂无实习状态</h3>
          <p class="notice-desc">系统正在根据您的申请记录自动判断实习状态，请先投递职位或确认实习</p>
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
        </div>
        <div class="card-footer">
          <span class="view-more" @click="goToMessageCenter">查看详情</span>
        </div>
      </div>


    </div>

    <!-- 实习状态登记对话框 -->
    <el-dialog
      v-model="showStatusDialog"
      width="1000px"
      class="internship-status-dialog"
      append-to-body
      lock-scroll
      modal-class="global-modal"
      :title="''"
    >
      <div class="dialog-content">
        <!-- 蓝绿色标题栏 -->
        <div class="header-section">
          <h1 class="header-title">实习状态查看</h1>
        </div>

        <el-form :model="internshipStatus" :rules="rules" ref="formRef" label-width="100px">
          <div class="form-content">
            <!-- 基本信息 -->
            <div class="form-module">
              <div class="module-header">
                <div class="module-icon-container">
                  <el-icon class="module-icon"><User /></el-icon>
                </div>
                <h2 class="module-title">基本信息</h2>
              </div>
              <!-- 优化后的基本信息布局 -->
              <div class="basic-info-grid">
                <!-- 第一行：学号 + 姓名 -->
                <div class="form-row">
                  <el-form-item label="学号" prop="studentId" class="form-item">
                    <el-input
                      v-model="internshipStatus.studentId"
                      placeholder="请输入学号"
                      clearable
                      class="form-input"
                      disabled
                    />
                  </el-form-item>
                  <el-form-item label="姓名" prop="studentName" class="form-item">
                    <el-input
                      v-model="internshipStatus.studentName"
                      placeholder="请输入姓名"
                      clearable
                      class="form-input"
                      disabled
                    />
                  </el-form-item>
                </div>

                <!-- 第二行：性别 + 年级 -->
                <div class="form-row">
                  <el-form-item label="性别" prop="gender" class="form-item">
                    <el-select
                      v-model="internshipStatus.gender"
                      placeholder="请选择性别"
                      clearable
                      class="form-input"
                      disabled
                    >
                      <el-option label="男" :value="1" />
                      <el-option label="女" :value="2" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="年级" prop="grade" class="form-item">
                    <el-input
                      v-model="internshipStatus.grade"
                      placeholder="请输入年级"
                      clearable
                      class="form-input"
                      disabled
                    />
                  </el-form-item>
                </div>

                <!-- 第三行：学院 -->
                <div class="form-row">
                  <el-form-item label="学院" prop="department" class="form-item">
                    <el-input
                      v-model="internshipStatus.department"
                      placeholder="请输入学院"
                      clearable
                      class="form-input"
                      disabled
                    />
                  </el-form-item>
                </div>

                <!-- 第四行：专业 + 班级 -->
                <div class="form-row">
                  <el-form-item label="专业" prop="major" class="form-item">
                    <el-input
                      v-model="internshipStatus.major"
                      placeholder="请输入专业"
                      clearable
                      class="form-input"
                      disabled
                    />
                  </el-form-item>
                  <el-form-item label="班级" prop="className" class="form-item">
                    <el-input
                      v-model="internshipStatus.className"
                      placeholder="请输入班级"
                      clearable
                      class="form-input"
                      disabled
                    />
                  </el-form-item>
                </div>

                <!-- 第五行：手机号 -->
                <div class="form-row">
                  <el-form-item label="手机号" prop="phone" class="form-item">
                    <el-input
                      v-model="internshipStatus.phone"
                      placeholder="请输入手机号"
                      clearable
                      class="form-input"
                      disabled
                    />
                  </el-form-item>
                </div>

                <!-- 第六行：状态（仅显示） -->
                <div class="form-row single-item">
                  <el-form-item label="状态" prop="status" class="form-item full-width">
                    <div :class="['status-display-badge', currentStatusClass]">
                      {{ currentStatusText }}
                    </div>
                  </el-form-item>
                </div>
              </div>
            </div>

            <!-- 实习状态（系统自动判断） -->
            <div class="form-module" v-if="internshipStatus.otherReason">
              <div class="module-header">
                <div class="module-icon-container">
                  <el-icon class="module-icon"><Briefcase /></el-icon>
                </div>
                <h2 class="module-title">其他说明</h2>
              </div>
              <div class="form-single">
                <div class="reason-text">{{ internshipStatus.otherReason }}</div>
              </div>
            </div>

            <!-- 实习单位信息 -->
            <template v-if="internshipStatus.company">
              <div class="form-module">
                <div class="module-header">
                  <div class="module-icon-container">
                    <el-icon class="module-icon"><OfficeBuilding /></el-icon>
                  </div>
                  <h2 class="module-title">实习单位信息</h2>
                </div>
                <!-- 只读显示实习单位信息 -->
                <div class="basic-info-grid">
                  <div class="form-row">
                    <el-form-item label="单位名称" class="form-item">
                      <div class="display-value">{{ internshipStatus.company || '-' }}</div>
                    </el-form-item>
                    <el-form-item label="实习岗位" class="form-item">
                      <div class="display-value">{{ internshipStatus.position || '-' }}</div>
                    </el-form-item>
                  </div>
                  <div class="form-row">
                    <el-form-item label="单位地址" class="form-item">
                      <div class="display-value">{{ internshipStatus.companyAddress || '-' }}</div>
                    </el-form-item>
                    <el-form-item label="联系电话" class="form-item">
                      <div class="display-value">{{ internshipStatus.companyPhone || '-' }}</div>
                    </el-form-item>
                  </div>
                  <div class="form-row">
                    <el-form-item label="开始时间" class="form-item">
                      <div class="display-value">{{ internshipStatus.startDate || '-' }}</div>
                    </el-form-item>
                    <el-form-item label="结束时间" class="form-item">
                      <div class="display-value">{{ internshipStatus.endDate || '-' }}</div>
                    </el-form-item>
                  </div>
                </div>
              </div>
            </template>

            <!-- 备注信息 -->
            <div class="form-module" v-if="internshipStatus.remark">
              <div class="module-header">
                <div class="module-icon-container">
                  <el-icon class="module-icon"><ChatDotRound /></el-icon>
                </div>
                <h2 class="module-title">备注信息</h2>
              </div>
              <div class="form-single">
                <div class="remark-display">{{ internshipStatus.remark || '无' }}</div>
              </div>
            </div>
          </div>

          <!-- 关闭按钮 -->
          <div class="form-actions">
            <el-button @click="showStatusDialog = false" class="close-dialog-btn">关闭</el-button>
          </div>
        </el-form>
      </div>
    </el-dialog>

    <!-- 通知详情对话框 -->
    <el-dialog
      v-model="showNotificationDialog"
      title="通知详情"
      width="600px"
      class="edit-profile-dialog"
      :append-to-body="true"
      :lock-scroll="true"
      modal-class="global-modal"
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import request from '@/utils/request'
import {
  Bell,
  TrendCharts,
  Message,
  ArrowRight,
  Camera,
  Briefcase,
  Edit,
  User,
  OfficeBuilding,
  Reading,
  ChatDotRound,
  Document,
  Clock,
  DocumentChecked,
  View
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
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
      const existingNotifications = new Map(notificationList.value.map(n => [n.id, n.unread]))

      notificationList.value = listData.map(item => ({
        id: item.id,
        title: item.title,
        desc: item.content,
        time: formatTime(item.notificationTime),
        unread: existingNotifications.has(item.id) ? existingNotifications.get(item.id) : !item.isRead,
        timestamp: new Date(item.notificationTime).getTime()
      }))
    }
  } catch (error) {
    console.error('获取通知失败:', error)
  }
}

const fetchUnreadCount = async () => {
  try {
    const studentId = getStudentId()
    const requestConfig = studentId ? { params: { studentId } } : {}
    const response = await request.get(`/student/home/unread-count`, requestConfig)

    if (response && response.code === 200) {
      return response.data ?? 0
    }
  } catch (error) {
    console.error('获取未读通知数量失败:', error)
  }
  return 0
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
  return [...notificationList.value].sort((a, b) => b.timestamp - a.timestamp)
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
  console.log('查看进展详情:', item)
}

const handleNotificationClick = async (item) => {
  selectedNotification.value = item
  showNotificationDialog.value = true

  if (item.unread) {
    // 从列表中移除已读通知
    notificationList.value = notificationList.value.filter(n => n.id !== item.id)
    // TODO: 实现通知已读 API
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

const handleAvatarChange = (event) => {
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

  const reader = new FileReader()
  reader.onload = (e) => {
    avatarUrl.value = e.target.result
    ElMessage.success('头像更换成功')
  }
  reader.readAsDataURL(file)
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
watch(() => router.currentRoute.value.path, (newPath, oldPath) => {
  if (newPath === '/student/home' && oldPath === '/student/profile') {
    // 从个人中心返回，重新检查个人资料状态
    fetchProfileStatus()
  }
})

const showStatusDialog = ref(false)
const showNotificationDialog = ref(false)
const selectedNotification = ref(null)
const hasRegistered = ref(false)
const hasProfile = ref(false)
const submittingStatus = ref(false)
const formRef = ref(null)

// 修复表单校验规则
const rules = {
  studentId: [
    { required: true, message: '请输入学号', trigger: 'blur' }
  ],
  studentName: [
    { required: true, message: '请输入姓名', trigger: 'blur' }
  ],
  gender: [
    { required: true, message: '请选择性别', trigger: 'change' }
  ],
  grade: [
    { required: true, message: '请输入年级', trigger: 'blur' }
  ],
  major: [
    { required: true, message: '请输入专业', trigger: 'blur' }
  ],
  className: [
    { required: true, message: '请输入班级', trigger: 'blur' }
  ],
  status: [
    { required: true, message: '请选择当前状态', trigger: 'change' }
  ],
  otherReason: [
    { required: true, message: '请说明其他情况', trigger: 'blur' }
  ],
  company: [
    { required: true, message: '请输入单位名称', trigger: 'blur' }
  ],
  position: [
    { required: true, message: '请输入实习岗位', trigger: 'blur' }
  ],
  companyAddress: [
    { required: true, message: '请输入单位地址', trigger: 'blur' }
  ],
  companyPhone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$|^\d{3,4}-\d{7,8}$/, message: '请输入正确的联系电话', trigger: 'blur' }
  ],
  startDate: [
    { required: true, message: '请选择开始时间', trigger: 'change' }
  ],
  endDate: [
    { required: true, message: '请选择结束时间', trigger: 'change' },
    { 
      validator: (rule, value, callback) => {
        if (value && internshipStatus.value.startDate) {
          if (new Date(value) < new Date(internshipStatus.value.startDate)) {
            callback(new Error('结束时间不能早于开始时间'))
          } else {
            callback()
          }
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  graduateSchool: [
    { required: true, message: '请输入考研院校', trigger: 'blur' }
  ],
  graduateMajor: [
    { required: true, message: '请输入考研专业', trigger: 'blur' }
  ],
  remark: []
}

const internshipStatus = ref({
  studentId: '',
  studentName: '',
  gender: 1,
  grade: '',
  department: '',
  major: '',
  className: '',
  phone: '',
  status: 0,
  otherReason: '',
  company: '',
  position: '',
  companyAddress: '',
  companyPhone: '',
  startDate: '',
  endDate: '',
  graduateSchool: '',
  graduateMajor: '',
  remark: ''
})

const currentStatusText = computed(() => {
  const status = internshipStatus.value.status
  const statusMap = {
    0: '无offer',   // 未找到实习
    1: '待确认',   // 已有Offer，等待确认
    2: '已确定',   // 已确定实习，待开始
    3: '实习中',   // 实习进行中
    4: '已结束',   // 实习已结束
    5: '已中断',   // 实习中断
    6: '延期'      // 延期（包括考研）
  }
  return statusMap[status] ?? '未登记'
})

const currentStatusClass = computed(() => {
  const classMap = {
    0: 'not-found',     // 待就业
    1: 'offer',       // 待确认
    2: 'confirmed',   // 已确定
    3: 'in-progress', // 实习中
    4: 'ended',       // 已结束
    5: 'interrupted', // 已中断
    6: 'delayed'      // 延期
  }
  return classMap[internshipStatus.value.status] ?? ''
})

// 优化表单提交逻辑
const submitStatus = async () => {
  // 动态校验：只校验当前状态下需要的字段
  const validateFields = ['studentId', 'studentName', 'gender', 'grade', 'major', 'className', 'status'];
  
  // 根据不同状态添加需要校验的字段
  if (internshipStatus.value.status === 1) {
    validateFields.push('company', 'position', 'companyAddress', 'companyPhone', 'startDate', 'endDate');
  } else if (internshipStatus.value.status === 5) {  // 已中断
    validateFields.push('otherReason');
  }
  
  formRef.value.validateField(validateFields, (error) => {
    if (!error) {
      submittingStatus.value = true;
      // 构建提交数据
      const submitData = {
        ...internshipStatus.value
      };

      // 真实 API 调用
      request.post('/student/home/internship-status', submitData)
        .then(response => {
          if (response && response.code === 200) {
            ElMessage.success('登记成功');
            showStatusDialog.value = false;
            hasRegistered.value = true;
            // 更新后重新获取状态
            fetchInternshipStatus();
          } else {
            ElMessage.error(response?.message || '登记失败，请稍后重试');
          }
          submittingStatus.value = false;
        })
        .catch(error => {
          console.error('登记失败:', error);
          ElMessage.error('登记失败，请稍后重试');
          submittingStatus.value = false;
        });
    }
  });
}

const fetchInternshipStatus = async () => {
  try {
    const response = await request.get('/student/home/internship-status')
    if (response && response.code === 200 && response.data) {
      const data = response.data
      // 使用API返回的currentStatus作为显示状态
      internshipStatus.value = {
        ...internshipStatus.value,
        status: data.currentStatus ?? 0,
        company: data.companyName || '',
        position: data.positionName || '',
        startDate: data.internshipStartTime || '',
        endDate: data.internshipEndTime || '',
        otherReason: data.otherReason || '',
        remark: data.remark || '',
        companyAddress: data.companyAddress || '',
        companyPhone: data.companyPhone || ''
      }
      // 根据是否有记录和状态判断是否显示状态卡片
      hasRegistered.value = data.hasRecord === true && data.currentStatus !== undefined
    }
  } catch (error) {
    console.error('获取实习状态失败:', error)
    hasRegistered.value = false
  }
}

const fetchProfileStatus = async () => {
  try {
    const studentId = getStudentId()
    const requestConfig = studentId ? { params: { studentId } } : {}
    const response = await request.get(`/student/profile`, requestConfig)
    if (response && response.code === 200 && response.data) {
      const profile = response.data
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

// 初始化实习状态表单数据
const initInternshipStatusForm = () => {
  const user = authStore.user

  internshipStatus.value = {
    studentId: user?.studentId || '',
    studentName: user?.name || '',
    gender: user?.gender || 1,
    grade: user?.grade || '',
    department: user?.department || '',
    major: user?.major || '',
    className: user?.class || '',
    phone: user?.phone || '',
    status: 0,
    otherReason: '',
    company: '',
    position: '',
    companyAddress: '',
    companyPhone: '',
    startDate: '',
    endDate: '',
    graduateSchool: '',
    graduateMajor: '',
    remark: ''
  }
}

// 打开实习状态对话框
const openStatusDialog = () => {
  // 不重置状态数据，只更新基本信息（如果需要从用户信息同步）
  // status 应该由 API 数据决定，不应被覆盖
  const user = authStore.user
  if (user) {
    internshipStatus.value.studentId = user?.studentId || ''
    internshipStatus.value.studentName = user?.name || ''
    internshipStatus.value.gender = user?.gender || 1
    internshipStatus.value.grade = user?.grade || ''
    internshipStatus.value.department = user?.department || ''
    internshipStatus.value.major = user?.major || ''
    internshipStatus.value.className = user?.class || ''
    internshipStatus.value.phone = user?.phone || ''
  }
  showStatusDialog.value = true
}

onMounted(async () => {
  try {
    await Promise.all([
      fetchDashboardStats(),
      fetchDashboardTodos(),
      fetchDashboardProgress(),
      fetchDashboardNotifications(),
      fetchInternshipStatus(),
      fetchProfileStatus()
    ])
  } catch (error) {
    console.error('加载数据失败:', error)
  }
})
</script>

<style scoped>
/* 核心布局适配：移除全局布局冲突的样式 */
.home-container {
  width: 100%;
  height: 100%;
  background: transparent !important; /* 改为透明，继承全局布局的背景色 */
  padding: 0 !important; /* 移除内边距，由全局布局统一控制 */
  margin: 0 !important;
  box-sizing: border-box !important;
}

/* 统一卡片和容器样式，与全局布局风格保持一致 */
.welcome-section {
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  border-radius: 12px;
  padding: 24px 32px;
  margin-bottom: 24px;
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
}

.welcome-left {
  display: flex;
  align-items: center;
  gap: 20px;
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
  margin-top: 16px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  border-left: 3px solid #409EFF;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.reminder-text {
  font-size: 14px;
  color: white;
  margin: 0;
  flex: 1;
}

.reminder-btn {
  padding: 6px 16px;
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



/* 实习状态提示卡片 */
.internship-notice-card {
  background: white;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  border-left: 4px solid #409EFF;
}

.notice-content {
  display: flex;
  align-items: center;
  gap: 20px;
}

.notice-icon {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.notice-icon-inner {
  font-size: 28px;
  color: white;
}

.notice-text {
  flex: 1;
}

.notice-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px 0;
}

.notice-desc {
  font-size: 14px;
  color: #606266;
  margin: 0;
  line-height: 1.5;
}

.notice-btn {
  padding: 12px 24px;
  font-size: 14px;
  font-weight: 500;
  border-radius: 8px;
}

/* 已登记状态显示 */
.internship-status-display {
  background: white;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.status-display-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.status-display-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.current-status-label {
  font-size: 14px;
  color: #909399;
  font-weight: 500;
}

.current-status-badge {
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
}

.current-status-badge.found {
  background: #f0f9ff;
  color: #409eff;
  border: 1px solid #bae6fd;
}

.current-status-badge.not-found {
  background: #fef3c7;
  color: #d97706;
  border: 1px solid #fde68a;
}

.current-status-badge.confirmed {
  background: #f0f9ff;
  color: #409eff;
  border: 1px solid #bae6fd;
}

.current-status-badge.ended {
  background: #f1f5f9;
  color: #64748b;
  border: 1px solid #e2e8f0;
}

.current-status-badge.interrupted {
  background: #fef3c7;
  color: #d97706;
  border: 1px solid #fde68a;
}

.current-status-badge.delayed {
  background: #f3e8ff;
  color: #9333ea;
  border: 1px solid #e9d5ff;
}

.current-status-badge.offer {
  background: #fff7e6;
  color: #fa8c16;
  border: 1px solid #ffd591;
}

.current-status-badge.in-progress {
  background: #ecf5ff;
  color: #409eff;
  border: 1px solid #bae6fd;
}

.update-btn {
  padding: 8px 16px;
  font-size: 14px;
  border-radius: 8px;
}

/* 统计数据网格 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

/* 统计项 */
.stat-item {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 16px;
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
  font-size: 24px;
  width: 48px;
  height: 48px;
  border-radius: 12px;
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
  font-size: 28px;
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

/* 内容网格 */
.content-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.content-card {
  background: white;
  border-radius: 12px;
  padding: 0;
  margin-bottom: 0;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  max-height: calc(100vh - 560px);
  display: flex;
  flex-direction: column;
}

.content-card .card-list {
  flex: 1;
  overflow-y: auto;
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
  padding: 16px 20px;
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
}

.list-item {
  padding: 16px 20px;
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

/* 对话框样式优化 */
.internship-status-dialog :deep(.el-dialog) {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.internship-status-dialog :deep(.el-dialog__header) {
  padding: 0;
  border-bottom: none;
}

.internship-status-dialog :deep(.el-dialog__title) {
  display: none;
}

.internship-status-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
  font-size: 20px;
  top: 15px;
  right: 20px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  width: 30px;
  height: 30px;
  transition: all 0.3s ease;
}

.internship-status-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  background: rgba(255, 255, 255, 0.3);
}

.internship-status-dialog :deep(.el-dialog__body) {
  padding: 0;
  background-color: #ffffff;
}

.internship-status-dialog :deep(.el-dialog__footer) {
  display: none;
}

/* 蓝绿色标题栏 */
.header-section {
  background: linear-gradient(90deg, #1e88e5 0%, #4caf50 100%);
  padding: 20px;
  margin-bottom: 0;
  border-radius: 0;
  box-shadow: none;
}

.header-title {
  color: white;
  font-size: 24px;
  font-weight: 600;
  text-align: center;
  margin: 0;
}

.form-content {
  padding: 20px;
  max-height: 60vh;
  overflow-y: auto;
}

.form-module {
  background-color: transparent;
  border: none;
  border-radius: 0;
  padding: 0;
  margin-bottom: 24px;
  box-shadow: none;
}

.module-header {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.module-icon-container {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
}

.module-icon {
  color: white;
  font-size: 14px;
}

.module-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

/* 优化后的表单网格布局 */
.basic-info-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-row {
  display: flex;
  gap: 16px;
  width: 100%;
}

/* 单独一行的项目 */
.form-row.single-item {
  justify-content: flex-start;
}

.form-item {
  flex: 1;
  margin-bottom: 0 !important;
}

/* 全屏宽项目 */
.form-item.full-width {
  width: 100%;
  max-width: none;
}

/* 原有的form-grid样式（保留兼容） */
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.form-single {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-actions {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 30px;
  padding: 20px;
  background-color: #ffffff;
  border: none;
  border-radius: 0;
  max-width: 100%;
  margin: 0;
  border-top: 1px solid #f0f0f0;
}

.submit-btn {
  background: linear-gradient(90deg, #3b82f6, #10b981) !important;
  color: #ffffff !important;
  border: none !important;
  border-radius: 4px !important;
  padding: 8px 20px !important;
  transition: all 0.2s ease !important;
}

.submit-btn:hover {
  background: linear-gradient(90deg, #2563eb, #059669) !important;
}

.reset-btn {
  background: #ffffff !important;
  color: #64748b !important;
  border: 1px solid #e2e8f0 !important;
  border-radius: 4px !important;
  padding: 8px 20px !important;
}

.reset-btn:hover {
  border-color: #3b82f6 !important;
  color: #3b82f6 !important;
}

.status-radio-group {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.status-radio-group .el-radio-button__inner {
  padding: 8px 16px;
  font-size: 14px;
}

/* 优化表单元素样式 */
:deep(.form-item .el-form-item__label) {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
  padding: 0 8px 0 0;
  margin-bottom: 6px;
  display: block;
  line-height: 1.4;
}

/* 必填项星号优化 */
:deep(.form-item .el-form-item__label::after) {
  content: "*";
  color: #F56C6C;
  margin-left: 4px;
  font-size: 12px;
}

/* 输入框样式优化 */
.form-input {
  width: 100%;
}

:deep(.form-input .el-input__wrapper) {
  border-radius: 8px;
  border: 1px solid #E5E7EB;
  padding: 10px 12px;
  background-color: #FFFFFF;
  box-shadow: none;
  transition: all 0.2s ease;
  height: 44px;
}

:deep(.form-input .el-input__wrapper:focus-within) {
  border-color: #409EFF;
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.1);
}

:deep(.form-input .el-input__inner) {
  font-size: 14px;
  color: #303133;
  padding: 0;
  height: auto;
  line-height: 1.5;
}

:deep(.form-input .el-input__placeholder) {
  color: #C0C4CC;
}

/* 下拉选择器样式统一 */
:deep(.form-input .el-select__wrapper) {
  border-radius: 8px;
  border: 1px solid #E5E7EB;
  padding: 10px 12px;
  background-color: #FFFFFF;
  height: 44px;
}

:deep(.form-input .el-select__wrapper:focus-within) {
  border-color: #409EFF;
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.1);
}

/* 日期选择器样式 */
:deep(.form-input .el-date-editor) {
  width: 100%;
}

:deep(.form-input .el-textarea__inner) {
  border-radius: 8px;
  border: 1px solid #E5E7EB;
  padding: 12px;
  font-size: 14px;
  line-height: 1.5;
}

:deep(.form-input .el-textarea__inner:focus) {
  border-color: #409EFF;
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.1);
}

/* 表单样式 */
.internship-status-dialog :deep(.el-form-item) {
  margin-bottom: 16px;
}

/* 只读显示的值 */
.display-value {
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  color: #606266;
  font-size: 14px;
  min-height: 36px;
  line-height: 1.5;
}

/* 状态徽章 */
.status-display-badge {
  display: inline-block;
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
}

.status-display-badge.not-found {
  background: #fef3c7;
  color: #d97706;
  border: 1px solid #fde68a;
}

.status-display-badge.applied {
  background: #ecf5ff;
  color: #409EFF;
  border: 1px solid #b3d8fd;
}

.status-display-badge.found {
  background: #f0f9ff;
  color: #409eff;
  border: 1px solid #bae6fd;
}

.status-display-badge.confirmed {
  background: #f0f9eb;
  color: #67C23A;
  border: 1px solid #c2e7b0;
}

.status-display-badge.ended {
  background: #f1f5f9;
  color: #64748b;
  border: 1px solid #e2e8f0;
}

.status-display-badge.interrupted {
  background: #fef3c7;
  color: #d97706;
  border: 1px solid #fde68a;
}

.status-display-badge.delayed {
  background: #f3e8ff;
  color: #9333ea;
  border: 1px solid #e9d5ff;
}

.status-display-badge.offer {
  background: #fff7e6;
  color: #fa8c16;
  border: 1px solid #ffd591;
}

.status-display-badge.in-progress {
  background: #ecf5ff;
  color: #409eff;
  border: 1px solid #bae6fd;
}

/* 备注显示 */
.remark-display {
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
}

/* 关闭按钮 */
.close-dialog-btn {
  padding: 10px 40px;
  font-size: 15px;
  font-weight: 500;
  border-radius: 8px;
  background: #fff;
  color: #606266;
  border: 1px solid #dcdfe6;
}

.close-dialog-btn:hover {
  border-color: #409eff;
  color: #409eff;
}

.internship-status-dialog :deep(.el-form-item__label) {
  font-size: 14px;
  color: #334155;
  font-weight: 500;
}

.internship-status-dialog :deep(.el-input__wrapper),
.internship-status-dialog :deep(.el-select__wrapper),
.internship-status-dialog :deep(.el-textarea__inner) {
  border-radius: 4px;
  border: 1px solid #e2e8f0;
  padding: 6px 12px;
  background-color: #ffffff;
  box-shadow: none;
  transition: all 0.2s ease;
}

/* 聚焦时高亮 */
.internship-status-dialog :deep(.el-input__wrapper:focus-within),
.internship-status-dialog :deep(.el-select__wrapper:focus-within),
.internship-status-dialog :deep(.el-textarea__inner:focus) {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.internship-status-dialog :deep(.el-date-picker) {
  width: 100%;
}

.dialog-content {
  max-height: 80vh;
  overflow-y: auto;
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

  .stats-bar {
    padding: 16px;
    flex-wrap: wrap;
    gap: 16px;
  }

  .stat-item {
    flex: 1;
    min-width: 80px;
  }

  .stat-divider {
    display: none;
  }

  .content-grid {
    grid-template-columns: 1fr;
  }

  .table-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .status-radio-group {
    flex-direction: column;
  }

  .notice-content {
    flex-direction: column;
    text-align: center;
  }

  .status-display-content {
    flex-direction: column;
    gap: 16px;
  }

  /* 移动端表单布局适配 */
  .form-row {
    flex-direction: column;
    gap: 12px;
  }
  
  .form-grid {
    grid-template-columns: 1fr;
  }
  
  .internship-status-dialog :deep(.el-dialog) {
    width: 95% !important;
    margin: 0 auto;
  }
  
  .form-content {
    max-height: 70vh;
  }
}

@media screen and (max-width: 480px) {
  .welcome-text h1 {
    font-size: 20px;
  }

  .stat-value {
    font-size: 24px;
  }

  .table-row {
    grid-template-columns: 1fr;
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
</style>