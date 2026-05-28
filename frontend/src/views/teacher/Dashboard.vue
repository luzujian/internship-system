<template>
  <div class="dashboard-container">
    <div class="page-header fade-in">
      <div class="header-content">
        <h2 class="page-title">{{ scopeName ? scopeName + ' - 实习状态看板' : '实习状态看板' }}</h2>
        <p class="page-description">实时监控学生的实习状态分布情况</p>
      </div>
      <div class="time-range-selector">
        <label>时间范围：</label>
        <input type="date" v-model="startDate" />
        <span>至</span>
        <input type="date" v-model="endDate" />
        <button class="btn-primary" @click="applyDateRange">应用</button>
        <button class="btn-reset" @click="resetDateRange">重置</button>
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
        <h3>{{ statusBarTitle }}</h3>
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

    <!-- 系维度看板（学院教师专用） -->
    <div class="dimension-board" v-if="divisionData.length > 0 && isCollegeTeacher">
      <h3 class="collapsible-header" @click="divisionCollapsed = !divisionCollapsed">
        系状态
        <span class="collapse-arrow" :class="{ collapsed: divisionCollapsed }">▼</span>
      </h3>
      <div class="board-cards" v-show="!divisionCollapsed">
        <div v-for="(division, index) in divisionData" :key="division.divisionName" class="board-card fade-in" :style="{ animationDelay: (0.8 + index * 0.1) + 's' }" @click="handleDivisionClick(division.divisionName)">
          <div class="card-header">
            <h4>{{ division.divisionName }}</h4>
            <span class="student-count badge badge-primary">{{ division.total }}人</span>
          </div>
          <div class="card-body">
            <div class="progress-bar">
              <div class="progress-item success" :style="{ width: `${division.total > 0 ? (division.confirmed / division.total) * 100 : 0}%` }"></div>
              <div class="progress-item info" :style="{ width: `${division.total > 0 ? (division.offer / division.total) * 100 : 0}%` }"></div>
              <div class="progress-item danger" :style="{ width: `${division.total > 0 ? (division.noOffer / division.total) * 100 : 0}%` }"></div>
              <div class="progress-item warning" :style="{ width: `${division.total > 0 ? (division.delay / division.total) * 100 : 0}%` }"></div>
            </div>
            <div class="progress-stats">
              <div class="stat-item">
                <span class="stat-label">已确定：</span>
                <span class="stat-value success">{{ division.confirmed }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">有offer：</span>
                <span class="stat-value info">{{ division.offer }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">没offer：</span>
                <span class="stat-value danger">{{ division.noOffer }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">延迟：</span>
                <span class="stat-value warning">{{ division.delay }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 年级维度看板 -->
    <div class="dimension-board" v-if="gradeData.length > 0 && !isCounselor && !isCollegeTeacher">
      <h3 class="collapsible-header" @click="gradeCollapsed = !gradeCollapsed">
        年级状态
        <span class="collapse-arrow" :class="{ collapsed: gradeCollapsed }">▼</span>
      </h3>
      <div class="board-cards" v-show="!gradeCollapsed">
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
    <div class="dimension-board" v-if="majorData.length > 0 && !isCounselor">
      <div class="board-title-row">
        <h3 class="collapsible-header" @click="majorCollapsed = !majorCollapsed">
          专业状态
          <span class="collapse-arrow" :class="{ collapsed: majorCollapsed }">▼</span>
        </h3>
        <el-select
          v-model="selectedMajors"
          multiple
          collapse-tags
          collapse-tags-tooltip
          placeholder="全部专业"
          class="major-filter-select"
          @click.stop
        >
          <el-option label="全部" value="__all__" />
          <el-option
            v-for="m in majorData"
            :key="m.majorName"
            :label="m.majorName"
            :value="m.majorName"
          />
        </el-select>
      </div>
      <div class="board-cards" v-show="!majorCollapsed">
        <div v-for="(major, index) in filteredMajorData" :key="major.majorName" class="board-card fade-in" :style="{ animationDelay: (1.0 + index * 0.1) + 's' }" @click="handleMajorClick(major.majorName)">
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

    <!-- 班级维度看板（仅辅导员可见） -->
    <div class="dimension-board" v-if="classData.length > 0 && isCounselor">
      <div class="board-title-row">
        <h3 class="collapsible-header" @click="classCollapsed = !classCollapsed">
          班级状态
          <span class="collapse-arrow" :class="{ collapsed: classCollapsed }">▼</span>
        </h3>
        <el-select
          v-model="selectedClasses"
          multiple
          collapse-tags
          collapse-tags-tooltip
          placeholder="全部班级"
          class="major-filter-select"
          @click.stop
        >
          <el-option label="全部" value="__all__" />
          <el-option
            v-for="c in classData"
            :key="c.className"
            :label="c.className"
            :value="c.className"
          />
        </el-select>
      </div>
      <div class="board-cards" v-show="!classCollapsed">
        <div v-for="(cls, index) in filteredClassData" :key="cls.className" class="board-card fade-in" :style="{ animationDelay: (1.2 + index * 0.1) + 's' }" @click="handleClassClick(cls.className)">
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
import emitter from '../../utils/eventBus'

// 时间范围
const STORAGE_KEY_START = 'teacher_dashboard_startDate'
const STORAGE_KEY_END = 'teacher_dashboard_endDate'
const startDate = ref('')
const endDate = ref('')
const defaultStartDate = ref('')
const defaultEndDate = ref('')

// 路由实例
const router = useRouter()
const route = useRoute()

// 获取教师类型
const teacherType = localStorage.getItem('teacherType') || ''
const isCounselor = computed(() => teacherType === 'COUNSELOR')
const isCollegeTeacher = computed(() => teacherType === 'COLLEGE')

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
const scopeName = ref('')
const divisionCollapsed = ref(true)
const gradeCollapsed = ref(true)
const majorCollapsed = ref(true)
const classCollapsed = ref(true)
const selectedClasses = ref<string[]>(['__all__'])

const filteredClassData = computed(() => {
  if (selectedClasses.value.includes('__all__') || selectedClasses.value.length === 0) {
    return classData.value
  }
  return classData.value.filter(c => selectedClasses.value.includes(c.className))
})
const selectedMajors = ref<string[]>(['__all__'])

const filteredMajorData = computed(() => {
  if (selectedMajors.value.includes('__all__') || selectedMajors.value.length === 0) {
    return majorData.value
  }
  return majorData.value.filter(m => selectedMajors.value.includes(m.majorName))
})

const statusBarTitle = computed(() => {
  if (scopeName.value) return scopeName.value + ' - 应届毕业生实习状态'
  return '应届毕业生实习状态'
})

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

// 系室数据
const divisionData = ref<Array<{
  divisionName: string
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
      scopeName.value = response.scopeName || ''
      gradeData.value = response.gradeData || []
      majorData.value = response.majorData || []
      classData.value = response.classData || []
      divisionData.value = response.divisionData || []
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
      scopeName.value = response.scopeName || ''
      gradeData.value = response.gradeData || []
      majorData.value = response.majorData || []
      classData.value = response.classData || []
      divisionData.value = response.divisionData || []
      myClassData.value = []
    }
  } catch (error) {
    console.error('加载看板数据失败:', error)
  }
}

// 应用时间范围
const applyDateRange = () => {
  localStorage.setItem(STORAGE_KEY_START, startDate.value)
  localStorage.setItem(STORAGE_KEY_END, endDate.value)
  emitter.emit('dashboard-date-range-changed')
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

// 点击系室卡片跳转
const handleDivisionClick = (divisionName: string) => {
  navigateToStudentTracking({ division: divisionName })
}

// 点击专业卡片跳转
const handleMajorClick = (majorName: string) => {
  navigateToStudentTracking({ major: majorName })
}

// 点击班级卡片跳转
const handleClassClick = (className: string) => {
  navigateToStudentTracking({ class: className })
}

// 加载默认时间范围（从系统设置）
const loadDefaultDateRange = async () => {
  try {
    const response = await getInternshipNodes()
    if (response.data) {
      const data = response.data as any
      if (data.applicationStartTime) {
        defaultStartDate.value = data.applicationStartTime
      }
      if (data.endDate) {
        defaultEndDate.value = data.endDate
      } else if (data.applicationEndTime) {
        defaultEndDate.value = data.applicationEndTime
      }
    }
  } catch (error) {
    console.error('加载默认时间范围失败:', error)
    if (!defaultStartDate.value) defaultStartDate.value = '2026-03-01'
    if (!defaultEndDate.value) defaultEndDate.value = '2026-12-31'
  }
}

// 初始化时间范围：优先用户上次选择，否则用系统默认
const initDateRange = () => {
  const savedStart = localStorage.getItem(STORAGE_KEY_START)
  const savedEnd = localStorage.getItem(STORAGE_KEY_END)

  if (savedStart && savedEnd) {
    startDate.value = savedStart
    endDate.value = savedEnd
  } else {
    startDate.value = defaultStartDate.value
    endDate.value = defaultEndDate.value
  }
}

// 重置时间范围为系统默认
const resetDateRange = () => {
  localStorage.removeItem(STORAGE_KEY_START)
  localStorage.removeItem(STORAGE_KEY_END)
  startDate.value = defaultStartDate.value
  endDate.value = defaultEndDate.value
  loadDashboardData()
}

// 组件挂载时加载数据
onMounted(async () => {
  await loadDefaultDateRange()
  initDateRange()
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
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 16px;
  padding: 0 4px;
}

.header-content {
  z-index: 1;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: #000;
  margin: 0 0 8px 0;
}

.page-description {
  font-size: 14px;
  color: #606266;
  opacity: 0.95;
  font-weight: 500;
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
  font-size: 13px;
  padding: 6px 14px;
  font-weight: 500;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.time-range-selector .btn-primary {
  background: var(--color-primary, #1890ff);
  color: white;
}

.time-range-selector .btn-primary:hover {
  opacity: 0.85;
}

.time-range-selector .btn-reset {
  background: #faad14;
  color: #fff;
  border: 1px solid #faad14;
}

.time-range-selector .btn-reset:hover {
  background: #e8a200;
  border-color: #e8a200;
}

/* 总览统计卡片 */
.overview-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.stat-card {
  background-color: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
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
  width: 40px;
  height: 40px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
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
  font-size: 24px;
  color: #666;
  margin-bottom: 4px;
  font-weight: 500;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #333;
  margin-bottom: 4px;
  line-height: 1;
}

.stat-percentage {
  font-size: 12px;
  color: #999;
  font-weight: 500;
}

/* 总体状态卡片 */
.card {
  background-color: white;
  border-radius: var(--radius-lg);
  box-shadow: none;
  overflow: hidden;
  padding: 0 16px;
  margin-bottom: 16px;
}

.card-header {
  padding: 8px 0;
  background-color: transparent;
  border-bottom: 2px solid #f0f0f0;
}

.card-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.card-body {
  padding: 12px 0;
}

/* 状态条 */
.status-bar {
  display: flex;
  height: 18px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  margin-bottom: 12px;
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
  gap: 12px;
  flex-wrap: wrap;
  padding: 8px 12px;
  background-color: transparent;
  border-radius: var(--radius-md);
  box-shadow: none;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
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

.board-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 2px solid #f0f0f0;
  margin-bottom: 16px;
}

.board-title-row h3 {
  padding-bottom: 0;
  border-bottom: none;
  margin: 0;
}

.major-filter-select {
  width: 220px;
  flex-shrink: 0;
}

.major-filter-select :deep(.el-select-dropdown__list) {
  max-height: 300px;
  overflow-y: auto;
}

.collapsible-header {
  cursor: pointer;
  user-select: none;
  display: flex;
  align-items: center;
  gap: 8px;
}

.collapsible-header:hover {
  color: var(--color-primary, #1890ff);
}

.collapse-arrow {
  font-size: 14px;
  transition: transform 0.3s;
}

.collapse-arrow.collapsed {
  transform: rotate(-90deg);
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
  background-color: transparent;
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
