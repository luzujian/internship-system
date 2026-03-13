<template>
  <div class="teacher-dashboard-container">
    <el-card class="welcome-card" shadow="never">
      <div class="welcome-content">
        <div class="welcome-info">
          <el-icon size="32" color="#E6A23C"><User /></el-icon>
          <div class="welcome-text">
            <h3>欢迎回来，{{ getTeacherName() }}</h3>
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
          <div class="stat-icon-wrapper classes">
            <el-icon><School /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.classCount }}</div>
            <div class="stat-label">负责班级</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="never">
        <div class="stat-content">
          <div class="stat-icon-wrapper students">
            <el-icon><User /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.studentCount }}</div>
            <div class="stat-label">学生总数</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="never">
        <div class="stat-content">
          <div class="stat-icon-wrapper internship">
            <el-icon><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.internshipCount }}</div>
            <div class="stat-label">实习中</div>
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
            <div class="stat-label">待审核</div>
          </div>
        </div>
      </el-card>
    </div>

    <el-card class="recent-activities-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">最近活动</span>
          <el-button type="primary" link @click="viewAllActivities">查看全部</el-button>
        </div>
      </template>
      <el-table :data="recentActivities" style="width: 100%" v-loading="loading">
        <el-table-column prop="activityType" label="活动类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getActivityType(row.activityType)">
              {{ getActivityText(row.activityType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="studentName" label="学生姓名" width="120" />
        <el-table-column prop="createTime" label="时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
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
import { User, Refresh, School, Document, Clock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const currentDate = ref('')

const stats = ref({
  classCount: 0,
  studentCount: 0,
  internshipCount: 0,
  pendingCount: 0
})

const recentActivities = ref([])

const getTeacherName = () => {
  return authStore.user?.name || '教师用户'
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

const getActivityType = (type) => {
  const typeMap = {
    0: 'primary',
    1: 'success',
    2: 'warning',
    3: 'danger'
  }
  return typeMap[type] || 'info'
}

const getActivityText = (type) => {
  const typeMap = {
    0: '实习申请',
    1: '实习确认',
    2: '实习变更',
    3: '实习结束'
  }
  return typeMap[type] || '未知'
}

const refreshData = async () => {
  loading.value = true
  try {
    await loadStats()
    await loadRecentActivities()
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
      classCount: 3,
      studentCount: 120,
      internshipCount: 85,
      pendingCount: 12
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

const loadRecentActivities = async () => {
  try {
    // 模拟数据
    recentActivities.value = [
      {
        activityType: 0,
        description: '提交了前端开发实习申请',
        studentName: '张三',
        createTime: '2024-01-15 10:30:00'
      },
      {
        activityType: 1,
        description: '确认了Java开发实习',
        studentName: '李四',
        createTime: '2024-01-14 14:20:00'
      },
      {
        activityType: 2,
        description: '变更了实习岗位',
        studentName: '王五',
        createTime: '2024-01-13 09:15:00'
      }
    ]
  } catch (error) {
    console.error('加载最近活动失败:', error)
    recentActivities.value = []
  }
}

const viewAllActivities = () => {
  ElMessage.info('查看全部活动功能开发中')
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
  loadRecentActivities()
})
</script>

<style scoped>
.teacher-dashboard-container {
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

.stat-icon-wrapper.classes {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.stat-icon-wrapper.students {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
}

.stat-icon-wrapper.internship {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  color: white;
}

.stat-icon-wrapper.pending {
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

.recent-activities-card {
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
