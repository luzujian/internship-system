<template>
  <div class="student-dashboard-container">
    <el-card class="welcome-card" shadow="never">
      <div class="welcome-content">
        <div class="welcome-info">
          <el-icon size="32" color="#67C23A"><User /></el-icon>
          <div class="welcome-text">
            <h3>欢迎回来，{{ getStudentName() }}</h3>
            <p class="welcome-date">{{ currentDate }}</p>
          </div>
        </div>
        <div class="welcome-actions">
          <el-button type="primary" @click="refreshData" :loading="loading">
            <el-icon><Refresh /></el-icon>&nbsp;刷新数据
          </el-button>
        </div>
      </div>
    </el-card>

    <div class="stats-cards-grid">
      <el-card class="stat-card" shadow="never">
        <div class="stat-content">
          <div class="stat-icon-wrapper applied">
            <el-icon><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.appliedCount }}</div>
            <div class="stat-label">已申请岗位</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="never">
        <div class="stat-content">
          <div class="stat-icon-wrapper pending">
            <el-icon><Clock /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.pendingCount }}</div>
            <div class="stat-label">待处理申请</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="never">
        <div class="stat-content">
          <div class="stat-icon-wrapper accepted">
            <el-icon><CircleCheck /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.acceptedCount }}</div>
            <div class="stat-label">已录用</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="never">
        <div class="stat-content">
          <div class="stat-icon-wrapper rejected">
            <el-icon><CircleClose /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.rejectedCount }}</div>
            <div class="stat-label">已拒绝</div>
          </div>
        </div>
      </el-card>
    </div>

    <el-card class="recent-applications-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">最近申请</span>
          <el-button type="primary" link @click="viewAllApplications">查看全部</el-button>
        </div>
      </template>
      <el-table :data="recentApplications" style="width: 100%" v-loading="loading">
        <el-table-column prop="positionName" label="岗位名称" />
        <el-table-column prop="companyName" label="企业名称" />
        <el-table-column prop="applyTime" label="申请时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.applyTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Refresh, Document, Clock, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const currentDate = ref('')

const stats = ref({
  appliedCount: 0,
  pendingCount: 0,
  acceptedCount: 0,
  rejectedCount: 0
})

const recentApplications = ref([])

const getStudentName = () => {
  return authStore.user?.name || '学生用户'
}

const formatDate = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const getStatusType = (status) => {
  const statusMap = {
    0: 'warning',
    1: 'success',
    2: 'danger',
    3: 'info'
  }
  return statusMap[status] || 'info'
}

const getStatusText = (status) => {
  const statusMap = {
    0: '待处理',
    1: '已录用',
    2: '已拒绝',
    3: '已取消'
  }
  return statusMap[status] || '未知'
}

const refreshData = async () => {
  loading.value = true
  try {
    await loadStats()
    await loadRecentApplications()
    ElMessage.success('数据刷新成功')
  } catch (error) {
    console.error('刷新数据失败:', error)
    ElMessage.error('刷新数据失败')
  } finally {
    loading.value = false
  }
}

const loadStats = async () => {
  try {
    // 模拟数据
    stats.value = {
      appliedCount: 5,
      pendingCount: 2,
      acceptedCount: 1,
      rejectedCount: 2
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

const loadRecentApplications = async () => {
  try {
    // 模拟数据
    recentApplications.value = [
      {
        positionName: '前端开发实习生',
        companyName: '腾讯科技企业',
        applyTime: '2024-01-15 10:30:00',
        status: 0
      },
      {
        positionName: 'Java开发实习生',
        companyName: '阿里巴巴集团',
        applyTime: '2024-01-14 14:20:00',
        status: 1
      }
    ]
  } catch (error) {
    console.error('加载最近申请失败:', error)
    recentApplications.value = []
  }
}

const viewAllApplications = () => {
  router.push('/student/applications')
}

const updateCurrentDate = () => {
  const now = new Date()
  currentDate.value = now.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long'
  })
}

onMounted(() => {
  updateCurrentDate()
  loadStats()
  loadRecentApplications()
})
</script>

<style scoped>
.student-dashboard-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

.welcome-card {
  margin-bottom: 20px;
}

.welcome-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.welcome-info {
  display: flex;
  align-items: center;
  gap: 20px;
}

.welcome-text h3 {
  margin: 0 0 8px 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.welcome-date {
  margin: 0;
  font-size: 14px;
  color: #909399;
}

.stats-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.stat-card {
  border-radius: 8px;
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon-wrapper {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.stat-icon-wrapper.applied {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.stat-icon-wrapper.pending {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
}

.stat-icon-wrapper.accepted {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  color: white;
}

.stat-icon-wrapper.rejected {
  background: linear-gradient(135deg, #fa709a 0%, #fee140 100%);
  color: white;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}

.recent-applications-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
</style>
