<template>
  <div class="company-dashboard-container">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">企业实习管理平台</h1>
        <p class="page-description">发布实习岗位，管理招聘流程</p>
      </div>
      <div class="header-illustration">
        <div class="illustration-circle circle-1"></div>
        <div class="illustration-circle circle-2"></div>
      </div>
    </div>

    <el-card class="welcome-card" shadow="never">
      <div class="welcome-content">
        <div class="welcome-info">
          <el-icon size="32" color="#409EFF"><OfficeBuilding /></el-icon>
          <div class="welcome-text">
            <h3>欢迎回来，{{ getCompanyName() }}</h3>
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
          <div class="stat-icon-wrapper published">
            <el-icon><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.publishedPositions }}</div>
            <div class="stat-label">已发布岗位</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="never">
        <div class="stat-content">
          <div class="stat-icon-wrapper applicants">
            <el-icon><User /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.totalApplicants }}</div>
            <div class="stat-label">收到申请</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="never">
        <div class="stat-content">
          <div class="stat-icon-wrapper hired">
            <el-icon><CircleCheck /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.hiredCount }}</div>
            <div class="stat-label">已录用人数</div>
          </div>
        </div>
      </el-card>

      <el-card class="stat-card" shadow="never">
        <div class="stat-content">
          <div class="stat-icon-wrapper pending">
            <el-icon><Clock /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.pendingApplications }}</div>
            <div class="stat-label">待处理申请</div>
          </div>
        </div>
      </el-card>
    </div>

    <el-card class="recent-positions-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">最近发布的岗位</span>
          <el-button type="primary" link @click="viewAllPositions">查看全部</el-button>
        </div>
      </template>
      <el-table :data="recentPositions" style="width: 100%" v-loading="loading">
        <el-table-column prop="positionName" label="岗位名称" />
        <el-table-column prop="plannedRecruit" label="招聘人数" width="120" />
        <el-table-column prop="publishTime" label="发布时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.publishTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="viewPosition(row)">查看</el-button>
            <el-button type="danger" link size="small" @click="deletePosition(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { OfficeBuilding, Refresh, Document, User, CircleCheck, Clock } from '@element-plus/icons-vue'
import request from '@/utils/request'

const router = useRouter()

const loading = ref(false)
const currentDate = ref('')

const stats = ref({
  publishedPositions: 0,
  totalApplicants: 0,
  hiredCount: 0,
  pendingApplications: 0
})

const recentPositions = ref([])

const getCompanyName = () => {
  const authStore = JSON.parse(localStorage.getItem('auth') || '{}')
  return authStore.user?.name || '企业用户'
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
    2: 'info'
  }
  return statusMap[status] || 'info'
}

const getStatusText = (status) => {
  const statusMap = {
    0: '招聘中',
    1: '已招满',
    2: '已关闭'
  }
  return statusMap[status] || '未知'
}

const refreshData = async () => {
  loading.value = true
  try {
    await loadStats()
    await loadRecentPositions()
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
    const response = await request.get('/company/stats')
    if (response.data.code === 200) {
      stats.value = response.data.data
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

const loadRecentPositions = async () => {
  try {
    const response = await request.get('/company/positions/recent')
    if (response.data.code === 200) {
      recentPositions.value = response.data.data || []
    }
  } catch (error) {
    console.error('加载最近岗位失败:', error)
    recentPositions.value = []
  }
}

const viewAllPositions = () => {
  router.push('/company/positions')
}

const viewPosition = (row) => {
  ElMessage.info('查看岗位功能开发中')
}

const deletePosition = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该岗位吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const response = await request.delete(`/company/positions/${row.id}`)
    if (response.data.code === 200) {
      ElMessage.success('删除成功')
      await loadRecentPositions()
      await loadStats()
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除岗位失败:', error)
      ElMessage.error('删除失败')
    }
  }
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
  loadRecentPositions()
})
</script>

<style scoped>
.company-dashboard-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  color: white;
}

.header-content h1 {
  margin: 0 0 8px 0;
  font-size: 28px;
  font-weight: 600;
}

.header-content p {
  margin: 0;
  font-size: 14px;
  opacity: 0.9;
}

.header-illustration {
  position: relative;
  width: 80px;
  height: 80px;
}

.illustration-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
}

.circle-1 {
  width: 60px;
  height: 60px;
  top: 0;
  left: 0;
}

.circle-2 {
  width: 40px;
  height: 40px;
  bottom: 0;
  right: 0;
  background: rgba(255, 255, 255, 0.3);
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
  gap: 16px;
}

.welcome-text h3 {
  margin: 0 0 4px 0;
  font-size: 20px;
  font-weight: 600;
}

.welcome-date {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.stats-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.stat-card {
  cursor: pointer;
  transition: all 0.3s;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
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

.stat-icon-wrapper.published {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.stat-icon-wrapper.applicants {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
}

.stat-icon-wrapper.hired {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  color: white;
}

.stat-icon-wrapper.pending {
  background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
  color: white;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}

.recent-positions-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
</style>
