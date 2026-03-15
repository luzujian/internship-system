<template>
  <div class="data-statistics-container">
    <!-- 顶部标题区域 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">数据统计管理</h1>
        <p class="page-description">查看各角色用户的日活跃情况详细统计</p>
      </div>
      <div class="header-illustration">
        <div class="illustration-circle circle-1"></div>
        <div class="illustration-circle circle-2"></div>
      </div>
    </div>

    <!-- 时间范围选择 -->
    <el-card class="filter-card" shadow="never">
      <div class="filter-section">
        <div class="filter-item">
          <span class="filter-label">统计天数:</span>
          <el-radio-group v-model="days" @change="handleDaysChange">
            <el-radio-button :label="7">7天</el-radio-button>
            <el-radio-button :label="14">14天</el-radio-button>
            <el-radio-button :label="30">30天</el-radio-button>
            <el-radio-button :label="0">自定义</el-radio-button>
          </el-radio-group>
        </div>
        <div class="filter-item">
          <span class="filter-label">自定义日期:</span>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            @change="handleDateRangeChange"
            :disabled="days !== 0"
            :clearable="true"
          />
        </div>
        <div class="filter-actions">
          <el-button v-if="authStore.hasPermission('statistics:view')" type="primary" @click="refreshData" :loading="loading">
            <el-icon><Refresh /></el-icon>
            刷新数据
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 图表统计区域 -->
    <div class="chart-section">
      <!-- 学生端统计 -->
      <el-card class="chart-card" shadow="hover">
        <div class="section-header">
          <div class="section-title">
            <el-icon size="20" color="#409EFF">
              <UserFilled/>
            </el-icon>
            <h2>学生端日活跃统计</h2>
          </div>
          <div class="header-actions">
            <span class="chart-subtitle">{{ dateRangeText }}</span>
          </div>
        </div>
        <div class="chart-container">
          <div v-if="loading" class="chart-loading">
            <el-skeleton :rows="5" animated />
          </div>
          <div v-else-if="studentChartData && studentChartData.length > 0" class="chart-wrapper">
            <div ref="studentChartRef" class="chart"></div>
          </div>
          <div v-else class="chart-loading">
            <el-empty description="暂无数据" />
          </div>
        </div>
        <div class="chart-stats">
          <div class="stat-item">
            <div class="stat-value">{{ getAverageCount(studentChartData) }}</div>
            <div class="stat-label">平均日活</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ getMaxCount(studentChartData) }}</div>
            <div class="stat-label">最高日活</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ getChangeRate(studentChartData) }}</div>
            <div class="stat-label">环比变化</div>
          </div>
        </div>
      </el-card>

      <!-- 教师端统计 -->
      <el-card class="chart-card" shadow="hover">
        <div class="section-header">
          <div class="section-title">
            <el-icon size="20" color="#67C23A">
              <Avatar/>
            </el-icon>
            <h2>教师端日活跃统计</h2>
          </div>
          <div class="header-actions">
            <span class="chart-subtitle">{{ dateRangeText }}</span>
          </div>
        </div>
        <div class="chart-container">
          <div v-if="loading" class="chart-loading">
            <el-skeleton :rows="5" animated />
          </div>
          <div v-else-if="teacherChartData && teacherChartData.length > 0" class="chart-wrapper">
            <div ref="teacherChartRef" class="chart"></div>
          </div>
          <div v-else class="chart-loading">
            <el-empty description="暂无数据" />
          </div>
        </div>
        <div class="chart-stats">
          <div class="stat-item">
            <div class="stat-value">{{ getAverageCount(teacherChartData) }}</div>
            <div class="stat-label">平均日活</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ getMaxCount(teacherChartData) }}</div>
            <div class="stat-label">最高日活</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ getChangeRate(teacherChartData) }}</div>
            <div class="stat-label">环比变化</div>
          </div>
        </div>
      </el-card>

      <!-- 管理员端统计 -->
      <el-card class="chart-card" shadow="hover">
        <div class="section-header">
          <div class="section-title">
            <el-icon size="20" color="#E6A23C">
              <Operation/>
            </el-icon>
            <h2>管理员端日活跃统计</h2>
          </div>
          <div class="header-actions">
            <span class="chart-subtitle">{{ dateRangeText }}</span>
          </div>
        </div>
        <div class="chart-container">
          <div v-if="loading" class="chart-loading">
            <el-skeleton :rows="5" animated />
          </div>
          <div v-else-if="adminChartData && adminChartData.length > 0" class="chart-wrapper">
            <div ref="adminChartRef" class="chart"></div>
          </div>
          <div v-else class="chart-loading">
            <el-empty description="暂无数据" />
          </div>
        </div>
        <div class="chart-stats">
          <div class="stat-item">
            <div class="stat-value">{{ getAverageCount(adminChartData) }}</div>
            <div class="stat-label">平均日活</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ getMaxCount(adminChartData) }}</div>
            <div class="stat-label">最高日活</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ getChangeRate(adminChartData) }}</div>
            <div class="stat-label">环比变化</div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script lang="ts" setup>
import logger from '@/utils/logger'
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { UserFilled, Avatar, Operation, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import echarts from '../../utils/echarts'
import request from '../../utils/request'
import { useAuthStore } from '../../store/auth'

const authStore = useAuthStore()

const loading = ref(false)
const days = ref<number>(7)
const dateRange = ref<[string, string] | null>(null)
const dateRangeText = ref('最近7天数据')

const studentChartData = ref([])
const teacherChartData = ref([])
const adminChartData = ref([])

const studentChartRef = ref(null)
const teacherChartRef = ref(null)
const adminChartRef = ref(null)

let studentChartInstance = null
let teacherChartInstance = null
let adminChartInstance = null

let studentResizeObserver = null
let teacherResizeObserver = null
let adminResizeObserver = null

const isInitialLoad = ref(true)

const shouldUseLineChart = computed(() => {
  return days.value > 7 || (dateRange.value && dateRange.value.length === 2)
})

const getChartOption = (data: { date: string; count: number }[], color: string, title?: string) => {
  const dates = data.map(item => item.date)
  const values = data.map(item => item.count)
  
  const chartType = shouldUseLineChart.value ? 'line' : 'bar'
  
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        return `${params[0].name}<br/>活跃用户: ${params[0].value}人`
      }
    },
    grid: {
      left: '10%',
      right: '5%',
      bottom: '15%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: {
        interval: getLabelInterval(dates.length),
        rotate: dates.length > 10 ? 45 : 0,
        fontSize: 11
      },
      axisLine: {
        lineStyle: {
          color: '#e0e0e0'
        }
      },
      axisTick: {
        show: false
      }
    },
    yAxis: {
      type: 'value',
      min: 0,
      axisLabel: {
        formatter: '{value}'
      },
      axisLine: {
        lineStyle: {
          color: '#e0e0e0'
        }
      },
      splitLine: {
        lineStyle: {
          color: '#f0f0f0',
          type: 'dashed'
        }
      }
    },
    series: [{
      type: chartType,
      data: values,
      smooth: chartType === 'line',
      symbol: chartType === 'line' ? 'circle' : 'none',
      symbolSize: chartType === 'line' ? 6 : 0,
      itemStyle: {
        color: color,
        borderRadius: chartType === 'bar' ? [4, 4, 0, 0] : 0
      },
      barWidth: chartType === 'bar' ? '40%' : 0,
      lineStyle: {
        width: 2,
        color: color
      },
      areaStyle: chartType === 'line' ? {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: color + '33' },
          { offset: 1, color: color + '05' }
        ])
      } : null
    }]
  }
}

const getLabelInterval = (dataLength) => {
  if (dataLength <= 7) {
    return 0
  } else if (dataLength <= 14) {
    return 2
  } else if (dataLength <= 30) {
    return 4
  } else {
    return Math.floor(dataLength / 6)
  }
}

const initChart = (chartRef, chartInstance, data, color, observerVar) => {
  if (!chartRef) return
  
  if (chartInstance) {
    chartInstance.dispose()
  }
  
  if (observerVar) {
    observerVar.disconnect()
  }
  
  const newChartInstance = echarts.init(chartRef)
  const option = getChartOption(data, color)
  newChartInstance.setOption(option)
  
  const resizeObserver = new ResizeObserver(() => {
    if (newChartInstance && !newChartInstance.isDisposed()) {
      newChartInstance.resize()
    }
  })
  resizeObserver.observe(chartRef)
  
  return newChartInstance
}

const renderCharts = () => {
  nextTick(() => {
    if (studentChartData.value.length > 0 && studentChartRef.value) {
      studentChartInstance = initChart(studentChartRef.value, studentChartInstance, studentChartData.value, '#409EFF', studentResizeObserver)
    }
    if (teacherChartData.value.length > 0 && teacherChartRef.value) {
      teacherChartInstance = initChart(teacherChartRef.value, teacherChartInstance, teacherChartData.value, '#67C23A', teacherResizeObserver)
    }
    if (adminChartData.value.length > 0 && adminChartRef.value) {
      adminChartInstance = initChart(adminChartRef.value, adminChartInstance, adminChartData.value, '#E6A23C', adminResizeObserver)
    }
  })
}

const handleDaysChange = () => {
  if (days.value === 0) {
    dateRange.value = null
    dateRangeText.value = '请选择自定义日期范围'
  } else {
    dateRange.value = null
    dateRangeText.value = `最近${days.value}天数据`
    refreshData()
  }
}

const handleDateRangeChange = (value: [string, string] | null) => {
  if (value && value.length === 2) {
    const startDate = new Date(value[0])
    const endDate = new Date(value[1])

    if (startDate > endDate) {
      ElMessage.warning('开始日期不能大于结束日期')
      dateRange.value = null
      return
    }

    const diffTime = Math.abs(endDate.getTime() - startDate.getTime())
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24)) + 1
    
    if (diffDays > 365) {
      ElMessage.warning('日期范围不能超过365天')
      dateRange.value = null
      return
    }
    
    days.value = 0
    dateRangeText.value = `${value[0]} 至 ${value[1]}`
    refreshData()
  } else if (!value) {
    dateRange.value = null
    if (days.value === 0) {
      days.value = 7
      dateRangeText.value = '最近7天数据'
      refreshData()
    }
  }
}

const refreshData = async () => {
  loading.value = true
  try {
    const params: { days?: number; startDate?: string; endDate?: string } = {}
    
    if (days.value > 0) {
      params.days = days.value
    } else if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    
    const response = await request.get('/login-logs/active-users', { params })

    if (response.data && response.code === 200 && response.data) {
      studentChartData.value = response.data.studentData || []
      teacherChartData.value = response.data.teacherData || []
      adminChartData.value = response.data.adminData || []

      if (response.data.dateRange) {
        dateRangeText.value = response.data.dateRange
      }

      renderCharts()
      if (!isInitialLoad.value) {
        ElMessage.success('数据刷新成功')
      }
      isInitialLoad.value = false
    } else {
      ElMessage.error(response.message || response.msg || '获取数据失败')
    }
  } catch (error) {
    logger.error('获取活跃度统计数据失败:', error)
    ElMessage.error('获取数据失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const getAverageCount = (data) => {
  if (!data || data.length === 0) return 0
  const total = data.reduce((sum, item) => sum + item.count, 0)
  return Math.round(total / data.length)
}

const getMaxCount = (data) => {
  if (!data || data.length === 0) return 0
  return Math.max(...data.map(item => item.count))
}

const getChangeRate = (data) => {
  if (!data || data.length < 2) return '0%'
  
  const firstHalf = data.slice(0, Math.floor(data.length / 2))
  const secondHalf = data.slice(Math.floor(data.length / 2))
  
  const firstAvg = firstHalf.reduce((sum, item) => sum + item.count, 0) / firstHalf.length
  const secondAvg = secondHalf.reduce((sum, item) => sum + item.count, 0) / secondHalf.length
  
  if (firstAvg === 0) return secondAvg > 0 ? '+100%' : '0%'
  
  const rate = ((secondAvg - firstAvg) / firstAvg * 100)
  return rate >= 0 ? `+${rate.toFixed(1)}%` : `${rate.toFixed(1)}%`
}

watch([studentChartData, teacherChartData, adminChartData], () => {
  renderCharts()
})

onMounted(() => {
  refreshData()
})

onBeforeUnmount(() => {
  if (studentResizeObserver) {
    studentResizeObserver.disconnect()
    studentResizeObserver = null
  }
  if (teacherResizeObserver) {
    teacherResizeObserver.disconnect()
    teacherResizeObserver = null
  }
  if (adminResizeObserver) {
    adminResizeObserver.disconnect()
    adminResizeObserver = null
  }
  if (studentChartInstance && !studentChartInstance.isDisposed()) {
    studentChartInstance.dispose()
    studentChartInstance = null
  }
  if (teacherChartInstance && !teacherChartInstance.isDisposed()) {
    teacherChartInstance.dispose()
    teacherChartInstance = null
  }
  if (adminChartInstance && !adminChartInstance.isDisposed()) {
    adminChartInstance.dispose()
    adminChartInstance = null
  }
})
</script>

<style scoped>
.data-statistics-container {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  border-radius: 16px;
  padding: 24px 32px;
  color: white;
  margin-bottom: 24px;
  box-shadow: 0 8px 25px rgba(64, 158, 255, 0.25);
  position: relative;
  overflow: hidden;
}

.page-header::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -50%;
  width: 100%;
  height: 100%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
  transform: rotate(30deg);
}

.header-content h1 {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
  color: white;
}

.header-content p {
  font-size: 14px;
  opacity: 0.95;
  font-weight: 500;
  margin: 0;
}

.header-illustration {
  position: relative;
  width: 100px;
  height: 100px;
}

.illustration-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  animation: float 6s ease-in-out infinite;
}

.circle-1 {
  width: 60px;
  height: 60px;
  top: 0;
  right: 0;
  animation-delay: 0s;
}

.circle-2 {
  width: 40px;
  height: 40px;
  bottom: 10px;
  right: 20px;
  animation-delay: 3s;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

.filter-card {
  margin-bottom: 24px;
  border-radius: 16px;
  border: none;
}

.filter-section {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-label {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}

.filter-actions {
  margin-left: auto;
}

.chart-section {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
  margin-bottom: 30px;
}

.chart-card {
  cursor: pointer;
  transition: all 0.4s ease;
  border-radius: 16px;
  border: none;
  background: white;
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.chart-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 4px;
  background: linear-gradient(90deg, #409EFF, #52c41a);
}

.chart-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.15);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #ebeef5;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.section-title .el-icon {
  font-size: 20px;
}

.section-header h2 {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.header-actions {
  display: flex;
  align-items: center;
}

.chart-subtitle {
  font-size: 14px;
  color: #909399;
}

.chart-container {
  padding: 10px 0;
}

.chart-wrapper {
  width: 100%;
  height: 280px;
  position: relative;
}

.chart {
  width: 100%;
  height: 100%;
}

.chart-stats {
  display: flex;
  justify-content: space-around;
  padding: 20px 0 10px;
  border-top: 1px solid #f0f0f0;
  margin-top: 15px;
}

.stat-item {
  text-align: center;
}

.stat-item .stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #409EFF;
  margin-bottom: 4px;
}

.stat-item .stat-label {
  font-size: 12px;
  color: #909399;
}

.chart-loading {
  width: 100%;
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}

@media screen and (max-width: 1200px) {
  .chart-section {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media screen and (max-width: 992px) {
  .chart-section {
    grid-template-columns: 1fr;
  }
  
  .chart-wrapper {
    height: 260px;
  }
}

@media screen and (max-width: 768px) {
  .data-statistics-container {
    padding: 15px;
  }

  .page-header {
    flex-direction: column;
    text-align: center;
    padding: 20px;
  }

  .header-illustration {
    margin-top: 20px;
  }

  .chart-section {
    grid-template-columns: 1fr;
    gap: 20px;
  }

  .chart-wrapper {
    height: 240px;
  }

  .filter-section {
    flex-direction: column;
    align-items: flex-start;
  }

  .filter-actions {
    margin-left: 0;
    width: 100%;
  }

  .filter-actions .el-button {
    width: 100%;
  }
}
</style>
