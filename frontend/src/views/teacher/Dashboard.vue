<template>
  <div class="dashboard-container">
    <div class="page-header fade-in">
      <h2>实习状态看板</h2>
      <div class="time-range-selector">
        <label>时间范围：</label>
        <input type="date" v-model="startDate" />
        <span>至</span>
        <input type="date" v-model="endDate" />
        <button class="btn-primary" @click="applyDateRange">应用</button>
      </div>
    </div>

    <!-- 总览统计卡片 -->
    <div class="overview-cards">
      <div class="stat-card fade-in" style="animation-delay: 0.1s">
        <div class="stat-icon primary">👥</div>
        <div class="stat-content">
          <div class="stat-label">学生总数</div>
          <div class="stat-value">{{ totalStudents }}</div>
        </div>
      </div>
      <div class="stat-card fade-in" style="animation-delay: 0.2s">
        <div class="stat-icon success">✅</div>
        <div class="stat-content">
          <div class="stat-label">已确定实习</div>
          <div class="stat-value">{{ confirmedCount }}</div>
          <div class="stat-percentage">{{ calculatePercentage(confirmedCount, totalStudents) }}%</div>
        </div>
      </div>
      <div class="stat-card fade-in" style="animation-delay: 0.3s">
        <div class="stat-icon info">📋</div>
        <div class="stat-content">
          <div class="stat-label">有offer但未确定</div>
          <div class="stat-value">{{ offerCount }}</div>
          <div class="stat-percentage">{{ calculatePercentage(offerCount, totalStudents) }}%</div>
        </div>
      </div>
      <div class="stat-card fade-in" style="animation-delay: 0.4s">
        <div class="stat-icon danger">✕</div>
        <div class="stat-content">
          <div class="stat-label">没offer</div>
          <div class="stat-value">{{ noOfferCount }}</div>
          <div class="stat-percentage">{{ calculatePercentage(noOfferCount, totalStudents) }}%</div>
        </div>
      </div>
      <div class="stat-card fade-in" style="animation-delay: 0.5s">
        <div class="stat-icon warning">📚</div>
        <div class="stat-content">
          <div class="stat-label">延迟</div>
          <div class="stat-value">{{ delayCount }}</div>
          <div class="stat-percentage">{{ calculatePercentage(delayCount, totalStudents) }}%</div>
        </div>
      </div>
    </div>

    <!-- 总体状态卡片 -->
    <div class="card fade-in" style="animation-delay: 0.6s">
      <div class="card-header">
        <h3>总体状态</h3>
      </div>
      <div class="card-body">
        <div class="status-bar">
          <div class="status-item success" :style="{ width: `${totalStudents > 0 ? (confirmedCount / totalStudents) * 100 : 0}%` }">
          </div>
          <div class="status-item info" :style="{ width: `${totalStudents > 0 ? (offerCount / totalStudents) * 100 : 0}%` }">
          </div>
          <div class="status-item danger" :style="{ width: `${totalStudents > 0 ? (noOfferCount / totalStudents) * 100 : 0}%` }">
          </div>
          <div class="status-item warning" :style="{ width: `${totalStudents > 0 ? (delayCount / totalStudents) * 100 : 0}%` }">
          </div>
        </div>
        <div class="status-legend">
          <div class="legend-item">
            <span class="legend-color success"></span>
            <span>已确定实习: {{ confirmedCount }} ({{ calculatePercentage(confirmedCount, totalStudents) }}%)</span>
          </div>
          <div class="legend-item">
            <span class="legend-color info"></span>
            <span>有offer但未确定: {{ offerCount }} ({{ calculatePercentage(offerCount, totalStudents) }}%)</span>
          </div>
          <div class="legend-item">
            <span class="legend-color danger"></span>
            <span>没offer: {{ noOfferCount }} ({{ calculatePercentage(noOfferCount, totalStudents) }}%)</span>
          </div>
          <div class="legend-item">
            <span class="legend-color warning"></span>
            <span>延迟: {{ delayCount }} ({{ calculatePercentage(delayCount, totalStudents) }}%)</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 年级维度看板 -->
    <div class="dimension-board" v-if="gradeData.length > 0">
      <h3>年级状态</h3>
      <div class="board-cards">
        <div v-for="(grade, index) in gradeData" :key="grade.gradeName" class="board-card fade-in" :style="{ animationDelay: (0.8 + index * 0.1) + 's' }" @click="handleGradeClick(grade.gradeName)">
          <div class="card-header">
            <h4>{{ grade.gradeName }}</h4>
            <span class="student-count badge badge-primary">{{ grade.total }}人</span>
          </div>
          <div class="card-body">
            <div class="progress-bar">
              <div class="progress-item success" :style="{ width: `${grade.total > 0 ? (grade.confirmed / grade.total) * 100 : 0}%` }"></div>
              <div class="progress-item info" :style="{ width: `${grade.total > 0 ? (grade.offer / grade.total) * 100 : 0}%` }"></div>
              <div class="progress-item danger" :style="{ width: `${grade.total > 0 ? (grade.noOffer / grade.total) * 100 : 0}%` }"></div>
              <div class="progress-item warning" :style="{ width: `${grade.total > 0 ? (grade.delay / grade.total) * 100 : 0}%` }"></div>
            </div>
            <div class="progress-stats">
              <div class="stat-item">
                <span class="stat-label">已确定：</span>
                <span class="stat-value success">{{ grade.confirmed }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">有offer：</span>
                <span class="stat-value info">{{ grade.offer }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">没offer：</span>
                <span class="stat-value danger">{{ grade.noOffer }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">延迟：</span>
                <span class="stat-value warning">{{ grade.delay }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 专业维度看板 -->
    <div class="dimension-board" v-if="majorData.length > 0">
      <h3>专业状态</h3>
      <div class="board-cards">
        <div v-for="(major, index) in majorData" :key="major.majorName" class="board-card fade-in" :style="{ animationDelay: (1.0 + index * 0.1) + 's' }" @click="handleMajorClick(major.majorName)">
          <div class="card-header">
            <h4>{{ major.majorName }}</h4>
            <span class="student-count badge badge-info">{{ major.total }}人</span>
          </div>
          <div class="card-body">
            <div class="progress-bar">
              <div class="progress-item success" :style="{ width: `${major.total > 0 ? (major.confirmed / major.total) * 100 : 0}%` }"></div>
              <div class="progress-item info" :style="{ width: `${major.total > 0 ? (major.offer / major.total) * 100 : 0}%` }"></div>
              <div class="progress-item danger" :style="{ width: `${major.total > 0 ? (major.noOffer / major.total) * 100 : 0}%` }"></div>
              <div class="progress-item warning" :style="{ width: `${major.total > 0 ? (major.delay / major.total) * 100 : 0}%` }"></div>
            </div>
            <div class="progress-stats">
              <div class="stat-item">
                <span class="stat-label">已确定：</span>
                <span class="stat-value success">{{ major.confirmed }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">有offer：</span>
                <span class="stat-value info">{{ major.offer }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">没offer：</span>
                <span class="stat-value danger">{{ major.noOffer }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">延迟：</span>
                <span class="stat-value warning">{{ major.delay }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 班级维度看板 -->
    <div class="dimension-board" v-if="classData.length > 0">
      <div class="board-header">
        <h3>班级状态</h3>
        <span class="board-hint" v-if="isCounselor">（仅显示我负责的班级，共 {{ classData.length }} 个班级，{{ classData.reduce((sum, cls) => sum + cls.total, 0) }} 名学生）</span>
      </div>
      <div class="board-cards">
        <div v-for="(cls, index) in classData" :key="cls.className" class="board-card fade-in" :style="{ animationDelay: (1.2 + index * 0.1) + 's' }" @click="handleClassClick(cls.className)">
          <div class="card-header">
            <h4>{{ cls.className }}</h4>
            <span class="student-count badge badge-success">{{ cls.total }}人</span>
          </div>
          <div class="card-body">
            <div class="progress-bar">
              <div class="progress-item success" :style="{ width: `${cls.total > 0 ? (cls.confirmed / cls.total) * 100 : 0}%` }"></div>
              <div class="progress-item info" :style="{ width: `${cls.total > 0 ? (cls.offer / cls.total) * 100 : 0}%` }"></div>
              <div class="progress-item danger" :style="{ width: `${cls.total > 0 ? (cls.noOffer / cls.total) * 100 : 0}%` }"></div>
              <div class="progress-item warning" :style="{ width: `${cls.total > 0 ? (cls.delay / cls.total) * 100 : 0}%` }"></div>
            </div>
            <div class="progress-stats">
              <div class="stat-item">
                <span class="stat-label">已确定：</span>
                <span class="stat-value success">{{ cls.confirmed }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">有 offer：</span>
                <span class="stat-value info">{{ cls.offer }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">没 offer：</span>
                <span class="stat-value danger">{{ cls.noOffer }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">延迟：</span>
                <span class="stat-value warning">{{ cls.delay }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { dashboardApi } from '../../api/teacherDashboard'
import type { DashboardStats } from '../../api/teacherDashboard'
import { getInternshipNodes } from '../../api/teacherSettings'

// 时间范围
const startDate = ref('')
const endDate = ref('')

// 路由实例
const router = useRouter()
const route = useRoute()

// 获取教师类型
const teacherType = localStorage.getItem('teacherType') || ''
const isCounselor = computed(() => teacherType === 'COUNSELOR')

// 获取辅导员 ID
const counselorId = computed(() => {
  const teacherId = localStorage.getItem('teacherId')
  return teacherId ? parseInt(teacherId) : 0
})

// 筛选条件
const classFilter = ref('')
const gradeFilter = ref('')
const majorFilter = ref('')

// 总体数据
const totalStudents = ref(0)
const confirmedCount = ref(0)
const offerCount = ref(0)
const noOfferCount = ref(0)
const delayCount = ref(0)

// 年级数据
const gradeData = ref<Array<{
  gradeName: string
  total: number
  confirmed: number
  offer: number
  noOffer: number
  delay: number
}>>([])

// 专业数据
const majorData = ref<Array<{
  majorName: string
  total: number
  confirmed: number
  offer: number
  noOffer: number
  delay: number
}>>([])

// 班级数据
const classData = ref<Array<{
  className: string
  total: number
  confirmed: number
  offer: number
  noOffer: number
  delay: number
}>>([])

// 辅导员负责的班级数据
const myClassData = ref<Array<{
  className: string
  total: number
  confirmed: number
  offer: number
  noOffer: number
  delay: number
}>>([])

// 加载看板数据
const loadDashboardData = async () => {
  try {
    if (isCounselor.value && counselorId.value > 0) {
      const response = await dashboardApi.getCounselorDashboardStats(counselorId.value, {
        startDate: startDate.value,
        endDate: endDate.value
      })
      totalStudents.value = response.totalStudents || 0
      confirmedCount.value = response.confirmed || 0
      offerCount.value = response.offer || 0
      noOfferCount.value = response.noOffer || 0
      delayCount.value = response.delay || 0
      gradeData.value = response.gradeData || []
      majorData.value = response.majorData || []
      classData.value = response.classData || []
      myClassData.value = []
    } else {
      const response = await dashboardApi.getDashboardStats({
        startDate: startDate.value,
        endDate: endDate.value
      })
      totalStudents.value = response.totalStudents || 0
      confirmedCount.value = response.confirmed || 0
      offerCount.value = response.offer || 0
      noOfferCount.value = response.noOffer || 0
      delayCount.value = response.delay || 0
      gradeData.value = response.gradeData || []
      majorData.value = response.majorData || []
      classData.value = response.classData || []
      myClassData.value = []
    }
  } catch (error) {
    console.error('加载看板数据失败:', error)
  }
}

// 应用时间范围
const applyDateRange = () => {
  loadDashboardData()
}

// 跳转到学生状态监控页面
const navigateToStudentTracking = (filters: any) => {
  router.push({
    path: '/teacher/student-tracking',
    query: filters
  })
}

// 点击年级卡片跳转
const handleGradeClick = (gradeName: string) => {
  navigateToStudentTracking({ grade: gradeName })
}

// 点击专业卡片跳转
const handleMajorClick = (majorName: string) => {
  navigateToStudentTracking({ major: majorName })
}

// 点击班级卡片跳转
const handleClassClick = (className: string) => {
  navigateToStudentTracking({ class: className })
}

// 加载实习时间设置
const loadInternshipTimeSettings = async () => {
  try {
    const response = await getInternshipNodes()
    if (response.data) {
      const data = response.data as any
      if (data.startDate) {
        startDate.value = data.startDate
      }
      if (data.endDate) {
        endDate.value = data.endDate
      }
    }
  } catch (error) {
    console.error('加载实习时间设置失败:', error)
    // 如果加载失败，使用默认值
    if (!startDate.value) {
      startDate.value = '2026-01-01'
    }
    if (!endDate.value) {
      endDate.value = '2026-01-31'
    }
  }
}

// 组件挂载时加载数据
onMounted(async () => {
  await loadInternshipTimeSettings()
  loadDashboardData()
})

// 监听路由变化，当从首页跳转过来时自动加载数据
watch(() => route.state?.fromHome, (fromHome) => {
  if (fromHome) {
    loadDashboardData()
  }
}, { immediate: true })

// 辅助函数：计算百分比，精确到小数点后一位，四舍五入
const calculatePercentage = (value: number, total: number) => {
  if (total === 0) return '0.0'
  return Math.round((value / total) * 1000) / 10
}
</script>

<style scoped>
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.page-header h2 {
  font-size: 28px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.time-range-selector {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.time-range-selector label {
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

.time-range-selector button {
  font-size: 18px;
  padding: 8px 16px;
  font-weight: 600;
}

/* 总览统计卡片 */
.overview-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 20px;
}

.stat-card {
  background-color: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 20px;
  transition: all var(--transition-normal);
  overflow: hidden;
  position: relative;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: var(--color-primary);
}

.stat-card:nth-child(1)::before {
  background: var(--color-primary);
}

.stat-card:nth-child(2)::before {
  background: var(--color-success);
}

.stat-card:nth-child(3)::before {
  background: var(--color-info);
}

.stat-card:nth-child(4)::before {
  background: var(--color-danger);
}

.stat-card:nth-child(5)::before {
  background: var(--color-warning);
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  flex-shrink: 0;
  transition: all var(--transition-normal);
  box-shadow: var(--shadow-sm);
}

.stat-icon:hover {
  transform: scale(1.1);
}

.stat-icon.primary {
  background: var(--color-primary);
  color: white;
}

.stat-icon.success {
  background: var(--color-success);
  color: white;
}

.stat-icon.info {
  background: var(--color-info);
  color: white;
}

.stat-icon.danger {
  background: var(--color-danger);
  color: white;
}

.stat-icon.warning {
  background: var(--color-warning);
  color: white;
}

.stat-content {
  flex: 1;
  min-width: 0;
}

.stat-label {
  font-size: 16px;
  color: #666;
  margin-bottom: 8px;
  font-weight: 500;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #333;
  margin-bottom: 6px;
  line-height: 1;
}

.stat-percentage {
  font-size: 16px;
  color: #999;
  font-weight: 500;
}

/* 状态条 */
.status-bar {
  display: flex;
  height: 28px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  margin-bottom: 20px;
  background-color: #f0f2f5;
  box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.1);
}

.status-item {
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 12px;
  font-weight: 600;
  transition: width 0.8s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

/* 将已确定实习的文字颜色改为黑色 */
.status-item.success {
  color: black;
}

.status-item::after {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent 0%, rgba(255, 255, 255, 0.3) 50%, transparent 100%);
  animation: shine 2s infinite;
}

@keyframes shine {
  0% {
    left: -100%;
  }
  100% {
    left: 100%;
  }
}

.status-item.success {
  background: var(--color-success);
}

.status-item.info {
  background: var(--color-info);
}

.status-item.danger {
  background: var(--color-danger);
}

.status-item.warning {
  background: var(--color-warning);
}

/* 状态图例 */
.status-legend {
  display: flex;
  gap: 28px;
  flex-wrap: wrap;
  padding: 16px;
  background-color: #fafafa;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

.legend-color {
  width: 16px;
  height: 16px;
  border-radius: var(--radius-full);
  box-shadow: var(--shadow-sm);
}

.legend-color.success {
  background: var(--color-success);
}

.legend-color.info {
  background: var(--color-info);
}

.legend-color.danger {
  background: var(--color-danger);
}

.legend-color.warning {
  background: var(--color-warning);
}

/* 维度筛选 */
.dimension-filters {
  display: flex;
  gap: 28px;
  align-items: center;
  padding: 24px;
  flex-wrap: wrap;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.filter-group label {
  font-size: 14px;
  color: #333;
  white-space: nowrap;
  font-weight: 500;
}

/* 维度看板 */
.dimension-board {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.dimension-board h3 {
  font-size: 22px;
  font-weight: 600;
  color: #333;
  margin: 0;
  padding-bottom: 12px;
  border-bottom: 2px solid #f0f0f0;
}

.board-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 12px;
  border-bottom: 2px solid #f0f0f0;
}

.board-header h3 {
  margin: 0;
  padding: 0;
  border: none;
}

.board-hint {
  font-size: 13px;
  color: #999;
  font-style: italic;
  font-weight: 400;
}

/* 辅导员本班状态看板样式 */
.counselor-board {
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border-radius: var(--radius-lg);
  padding: 20px;
  box-shadow: var(--shadow-md);
}

.counselor-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 22px;
  font-weight: 600;
  color: #0369a1;
  margin: 0 0 16px 0;
  padding-bottom: 12px;
  border-bottom: 2px solid #bae6fd;
}

.title-icon {
  font-size: 26px;
}

.title-hint {
  font-size: 14px;
  font-weight: 400;
  color: #7dd3fc;
  margin-left: auto;
  background: rgba(3, 105, 161, 0.1);
  padding: 4px 12px;
  border-radius: var(--radius-md);
}

.counselor-card {
  border: 2px solid #bae6fd;
  background: white;
}

.counselor-card:hover {
  border-color: #38bdf8;
  box-shadow: 0 8px 24px rgba(56, 189, 248, 0.2);
}

.board-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 20px;
}

.board-card {
  background-color: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  overflow: hidden;
  transition: all var(--transition-normal);
}

/* 卡片头部 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background-color: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}

.card-header h4 {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  margin-right: 12px;
}

/* 学生人数标签 */
.student-count {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: var(--radius-md);
  box-shadow: none;
}

.student-count.badge-primary {
  background: #e6f7ff;
  color: #1890ff;
}

.student-count.badge-success {
  background: #f6ffed;
  color: #52c41a;
}

.student-count.badge-info {
  background: #e6fffb;
  color: #13c2c2;
}

/* 卡片内容 */
.card-body {
  padding: 20px;
}

.board-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

/* 进度条 */
.progress-bar {
  display: flex;
  height: 16px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  margin-bottom: 16px;
  background-color: #f0f2f5;
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.1);
}

.progress-item {
  transition: width 0.8s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.progress-item::after {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent 0%, rgba(255, 255, 255, 0.4) 50%, transparent 100%);
  animation: shine 3s infinite;
}

.progress-item.success {
  background: var(--color-success);
}

.progress-item.info {
  background: var(--color-info);
}

.progress-item.danger {
  background: var(--color-danger);
}

.progress-item.warning {
  background: var(--color-warning);
}

/* 进度统计 */
.progress-stats {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  justify-content: flex-start;
}

.stat-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  min-width: 100px;
  white-space: nowrap;
}

.stat-label {
  color: #666;
  font-weight: 500;
  font-size: 12px;
  white-space: nowrap;
}

.stat-value {
  font-weight: 600;
  font-size: 14px;
  white-space: nowrap;
}

.stat-value.success {
  color: #52c41a;
}

.stat-value.info {
  color: #13c2c2;
}

.stat-value.danger {
  color: #f5222d;
}

.stat-value.warning {
  color: #faad14;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
  }

  .time-range-selector {
    justify-content: space-between;
    flex-wrap: wrap;
  }

  .overview-cards {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .stat-card {
    flex-direction: column;
    text-align: center;
    gap: 16px;
    padding: 20px;
  }

  .stat-icon {
    width: 50px;
    height: 50px;
    font-size: 24px;
  }

  .stat-value {
    font-size: 24px;
  }

  .dimension-filters {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
    padding: 20px;
  }

  .filter-group {
    justify-content: space-between;
  }

  .status-legend {
    flex-direction: column;
    gap: 12px;
    padding: 12px;
  }

  .progress-stats {
    flex-direction: column;
    gap: 12px;
  }

  .board-cards {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .dimension-board h3 {
    font-size: 18px;
  }

  .counselor-board {
    padding: 16px;
  }

  .counselor-title {
    font-size: 18px;
    flex-wrap: wrap;
  }

  .title-hint {
    margin-left: 0;
    margin-top: 8px;
    width: 100%;
    text-align: center;
  }
}
</style>
