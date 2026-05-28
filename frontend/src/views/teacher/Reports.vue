<template>
  <div class="reports-container">
    <div class="page-header fade-in">
      <div class="title-wrapper">
        <h2>统计报表<el-tag type="info" size="small" style="margin-left: 10px; vertical-align: middle;">全校</el-tag></h2>
        <p class="page-subtitle">本学期实习数据总览</p>
      </div>
    </div>

    <!-- 趋势图表 -->
    <div class="charts-section">
      <div class="chart-container card fade-in" style="animation-delay: 0.5s">
        <h3>企业入驻趋势 <span class="chart-subtitle">(2026年季度)</span></h3>
        <div class="trend-chart">
          <div class="chart-y-axis">
            <span v-for="tick in yAxisTicks" :key="tick" class="y-tick" :style="{ bottom: (tick / maxYAxis * 100) + '%' }">{{ tick }}</span>
          </div>
          <div class="chart-body">
            <div v-for="tick in yAxisTicks" :key="tick" class="grid-line" :style="{ bottom: (tick / maxYAxis) * 100 + '%' }"></div>
            <div v-for="(data, index) in companyTrend" :key="index" class="bar-group">
              <div class="bar-top-label">{{ data.value }}</div>
              <div class="bar-pillar" :style="{ height: maxCompanyValue > 0 ? (data.value / maxYAxis * 100) + '%' : '0%' }">
                <div v-if="data.value > 0" class="bar-fill"></div>
              </div>
            </div>
          </div>
          <div class="chart-x-labels">
            <span v-for="data in companyTrend" :key="data.label" class="x-label">{{ data.label }}</span>
          </div>
        </div>
      </div>
      
      <!-- 核心数据卡片 -->
      <div class="core-metrics card fade-in" style="animation-delay: 0.6s">
        <h3>核心指标</h3>
        <div class="metrics-grid">
          <div class="metric-card">
            <div class="metric-icon primary">🏢</div>
            <div class="metric-content">
              <h4>企业入驻数量</h4>
              <p class="metric-value">{{ metrics.companyCount }}</p>
            </div>
          </div>
          <div class="metric-card">
            <div class="metric-icon success">👥</div>
            <div class="metric-content">
              <h4>学生实习率</h4>
              <p class="metric-value">{{ metrics.internshipRate }}%</p>
            </div>
          </div>
          <div class="metric-card">
            <div class="metric-icon info">📊</div>
            <div class="metric-content">
              <h4>申请审核数量</h4>
              <p class="metric-value">{{ metrics.approvalCount }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 详细数据表格 -->
    <div class="details-section card fade-in" style="animation-delay: 0.7s">
      <h3>详细数据</h3>

      <div class="details-tabs-header">
        <div class="details-tabs">
          <button
            v-for="tab in detailTabs"
            :key="tab.key"
            :class="['tab-btn', { active: activeDetailTab === tab.key }]"
            @click="activeDetailTab = tab.key"
          >
            {{ tab.name }}
          </button>
        </div>
        <!-- 导出按钮区域 -->
        <div class="export-section">
          <div class="export-buttons">
            <button class="btn-primary export-btn" @click="exportReport">
              <span class="btn-icon">📊</span>导出综合报表
            </button>
            <button class="btn-success export-btn" @click="exportCompanyReport" v-if="activeDetailTab === 'companies'">
              <span class="btn-icon">🏢</span>导出企业报表
            </button>
            <button class="btn-success export-btn" @click="exportStudentReport" v-if="activeDetailTab === 'students'">
              <span class="btn-icon">👥</span>导出学生报表
            </button>
            <button class="btn-success export-btn" @click="exportApprovalReport" v-if="activeDetailTab === 'approvals'">
              <span class="btn-icon">📋</span>导出审核报表
            </button>
          </div>
        </div>
      </div>

      <!-- 企业入驻详情 -->
      <div v-if="activeDetailTab === 'companies'" class="details-table-wrapper">
        <div class="details-table">
          <table>
            <thead>
              <tr>
                <th>企业名称</th>
                <th>入驻时间</th>
                <th>提供岗位数</th>
                <th>已录取学生数</th>
                <th>企业标签</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="company in paginatedCompanies" :key="company.id" class="fade-in">
                <td>{{ company.name }}</td>
                <td>{{ company.joinDate }}</td>
                <td>{{ company.positionCount }}</td>
                <td>{{ company.admittedCount }}</td>
                <td>
                  <span v-if="company.tags && company.tags.length > 0" class="tag" :class="getTagClass(tag)" v-for="tag in company.tags" :key="tag" style="margin-right: 6px;">
                    {{ tag }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="pagination">
          <button class="page-btn" :disabled="currentPages.companies === 1" @click="goToPreviousPage('companies')">上一页</button>
          <span class="page-info">
            第 {{ currentPages.companies }} 页，共 {{ totalCompanyPages }} 页
          </span>
          <div class="page-jump">
            <input 
              type="number" 
              v-model="jumpPages.companies" 
              class="page-input"
              :min="1" 
              :max="totalCompanyPages"
              placeholder="页码"
            />
            <button class="page-btn jump-btn" @click="goToPage('companies')">跳转</button>
          </div>
          <button class="page-btn" :disabled="currentPages.companies === totalCompanyPages" @click="goToNextPage('companies')">下一页</button>
        </div>
      </div>

      <!-- 学生实习详情 -->
      <div v-if="activeDetailTab === 'students'" class="details-table-wrapper">
        <div class="details-table">
          <table>
            <thead>
              <tr>
                <th>学院</th>
                <th>专业</th>
                <th>学生总数</th>
                <th>已实习人数</th>
                <th>实习率</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="dept in paginatedStudents" :key="dept.id" class="fade-in">
                <td>{{ dept.college }}</td>
                <td>{{ dept.major }}</td>
                <td>{{ dept.totalCount }}</td>
                <td>{{ dept.internshipCount }}</td>
                <td>{{ dept.internshipRate }}%</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="pagination">
          <button class="page-btn" :disabled="currentPages.students === 1" @click="goToPreviousPage('students')">上一页</button>
          <span class="page-info">
            第 {{ currentPages.students }} 页，共 {{ totalStudentPages }} 页
          </span>
          <div class="page-jump">
            <input 
              type="number" 
              v-model="jumpPages.students" 
              class="page-input"
              :min="1" 
              :max="totalStudentPages"
              placeholder="页码"
            />
            <button class="page-btn jump-btn" @click="goToPage('students')">跳转</button>
          </div>
          <button class="page-btn" :disabled="currentPages.students === totalStudentPages" @click="goToNextPage('students')">下一页</button>
        </div>
      </div>

      <!-- 申请审核详情 -->
      <div v-if="activeDetailTab === 'approvals'" class="details-table-wrapper">
        <div class="details-table">
          <table>
            <thead>
              <tr>
                <th>申请类型</th>
                <th>申请数量</th>
                <th>通过数量</th>
                <th>驳回数量</th>
                <th>通过率</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="approval in paginatedApprovals" :key="approval.id" class="fade-in">
                <td>{{ approval.type }}</td>
                <td>{{ approval.totalCount }}</td>
                <td>{{ approval.approvedCount }}</td>
                <td>{{ approval.rejectedCount }}</td>
                <td>{{ approval.approvalRate }}%</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="pagination">
          <button class="page-btn" :disabled="currentPages.approvals === 1" @click="goToPreviousPage('approvals')">上一页</button>
          <span class="page-info">
            第 {{ currentPages.approvals }} 页，共 {{ totalApprovalPages }} 页
          </span>
          <div class="page-jump">
            <input 
              type="number" 
              v-model="jumpPages.approvals" 
              class="page-input"
              :min="1" 
              :max="totalApprovalPages"
              placeholder="页码"
            />
            <button class="page-btn jump-btn" @click="goToPage('approvals')">跳转</button>
          </div>
          <button class="page-btn" :disabled="currentPages.approvals === totalApprovalPages" @click="goToNextPage('approvals')">下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { reportsApi, type CoreMetrics, type CompanyTrend, type CompanyDetail, type StudentInternshipDetail, type ApprovalDetail } from '../../api/teacherReports'
import { getInternshipNodes } from '../../api/teacherSettings'

const route = useRoute()

// 时间范围（默认使用当前学期）
const startDate = ref('')
const endDate = ref('')
const hasLoadedSettings = ref(false)

// 详细数据标签页
const activeDetailTab = ref('companies')
const detailTabs = ref([
  { key: 'companies', name: '企业入驻详情' },
  { key: 'students', name: '学生实习详情' },
  { key: 'approvals', name: '申请审核详情' }
])

// 分页相关变量
const pageSize = ref(5) // 每页显示5条数据
const currentPages = ref({
  companies: 1,
  students: 1,
  approvals: 1
})
const jumpPages = ref({
  companies: '',
  students: '',
  approvals: ''
})

// 加载状态
const loading = ref({
  metrics: false,
  companyTrend: false,
  companies: false,
  students: false,
  approvals: false
})

// 总数
const totalCounts = ref({
  companies: 0,
  students: 0,
  approvals: 0
})

// 核心指标数据
const metrics = ref<CoreMetrics>({
  companyCount: 0,
  companyChange: 0,
  internshipRate: 0,
  internshipRateChange: 0,
  approvalCount: 0,
  approvalCountChange: 0,
  resourceDownloads: 0,
  resourceDownloadsChange: 0
})

// 企业入驻趋势数据
const companyTrend = ref<CompanyTrend[]>([
  { label: '第一季度', value: 0 },
  { label: '第二季度', value: 0 },
  { label: '第三季度', value: 0 },
  { label: '第四季度', value: 0 }
])

// 企业入驻详情数据
const companiesData = ref<CompanyDetail[]>([])

// 学生实习详情数据
const studentsData = ref<StudentInternshipDetail[]>([])

// 申请审核详情数据
const approvalsData = ref<ApprovalDetail[]>([])

// 加载核心指标数据
const loadCoreMetrics = async () => {
  loading.value.metrics = true
  try {
    const response = await reportsApi.getCoreMetrics({ startDate: startDate.value, endDate: endDate.value })
    if (response.data) {
      metrics.value = response.data
    }
  } catch (error) {
  } finally {
    loading.value.metrics = false
  }
}

// 加载企业入驻趋势数据
const loadCompanyTrend = async () => {
  loading.value.companyTrend = true
  try {
    const response = await reportsApi.getCompanyTrend({ year: new Date().getFullYear() })
    if (response.data) {
      companyTrend.value = response.data
    }
  } catch (error) {
  } finally {
    loading.value.companyTrend = false
  }
}

// 加载企业入驻详情数据
const loadCompanyDetails = async () => {
  loading.value.companies = true
  try {
    const response = await reportsApi.getCompanyDetails({ 
      page: currentPages.value.companies, 
      pageSize: pageSize.value 
    })
    if (response.data) {
      companiesData.value = response.data
    }
  } catch (error) {
  } finally {
    loading.value.companies = false
  }
}

// 加载企业入驻详情总数
const loadCompanyDetailsCount = async () => {
  try {
    const response = await reportsApi.getCompanyDetailsCount()
    if (response.data !== undefined) {
      totalCounts.value.companies = response.data
    }
  } catch (error) {
  }
}

// 加载学生实习详情数据
const loadStudentInternshipDetails = async () => {
  loading.value.students = true
  try {
    const response = await reportsApi.getStudentInternshipDetails({ 
      page: currentPages.value.students, 
      pageSize: pageSize.value 
    })
    if (response.data) {
      studentsData.value = response.data
    }
  } catch (error) {
  } finally {
    loading.value.students = false
  }
}

// 加载学生实习详情总数
const loadStudentInternshipDetailsCount = async () => {
  try {
    const response = await reportsApi.getStudentInternshipDetailsCount()
    if (response.data !== undefined) {
      totalCounts.value.students = response.data
    }
  } catch (error) {
  }
}

// 加载申请审核详情数据
const loadApprovalDetails = async () => {
  loading.value.approvals = true
  try {
    const response = await reportsApi.getApprovalDetails({ 
      page: currentPages.value.approvals, 
      pageSize: pageSize.value 
    })
    if (response.data) {
      approvalsData.value = response.data
    }
  } catch (error) {
  } finally {
    loading.value.approvals = false
  }
}

// 加载申请审核详情总数
const loadApprovalDetailsCount = async () => {
  try {
    const response = await reportsApi.getApprovalDetailsCount()
    if (response.data !== undefined) {
      totalCounts.value.approvals = response.data
    }
  } catch (error) {
  }
}

// 初始化加载数据
const loadAllData = async () => {
  await Promise.all([
    loadCoreMetrics(),
    loadCompanyTrend(),
    loadCompanyDetailsCount(),
    loadStudentInternshipDetailsCount(),
    loadApprovalDetailsCount()
  ])
  await Promise.all([
    loadCompanyDetails(),
    loadStudentInternshipDetails(),
    loadApprovalDetails()
  ])
}

// 组件挂载时加载数据
onMounted(async () => {
  await loadInternshipTimeSettings()
  loadAllData()
})

// 加载实习时间设置
const loadInternshipTimeSettings = async () => {
  if (hasLoadedSettings.value) return
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
      hasLoadedSettings.value = true
    }
  } catch (error) {
    console.error('加载实习时间设置失败:', error)
    if (!startDate.value) {
      startDate.value = '2026-01-01'
    }
    if (!endDate.value) {
      endDate.value = '2026-12-31'
    }
  }
}

// 监听路由变化，当从首页跳转过来时自动加载数据
watch(() => route.state?.fromHome, (fromHome) => {
  if (fromHome) {
    loadAllData()
  }
}, { immediate: true })

// 计算企业趋势最大值
const maxCompanyValue = computed(() => {
  return Math.max(...companyTrend.value.map(item => item.value), 1)
})

const maxYAxis = computed(() => {
  const max = maxCompanyValue.value
  if (max <= 5) return 5
  return Math.ceil(max / 10) * 10
})

const yAxisTicks = computed(() => {
  const max = maxYAxis.value
  const ticks = []
  for (let i = 0; i <= 4; i++) {
    ticks.push(Math.round(max * (1 - i / 4)))
  }
  return ticks
})

// 分页计算属性
const currentCompanyPage = computed(() => currentPages.value.companies)
const currentStudentPage = computed(() => currentPages.value.students)
const currentApprovalPage = computed(() => currentPages.value.approvals)

// 企业数据分页
const paginatedCompanies = computed(() => {
  return companiesData.value
})

const totalCompanyPages = computed(() => {
  return Math.ceil(totalCounts.value.companies / pageSize.value) || 1
})

// 学生数据分页
const paginatedStudents = computed(() => {
  return studentsData.value
})

const totalStudentPages = computed(() => {
  return Math.ceil(totalCounts.value.students / pageSize.value) || 1
})

// 申请审核数据分页
const paginatedApprovals = computed(() => {
  return approvalsData.value
})

const totalApprovalPages = computed(() => {
  return Math.ceil(totalCounts.value.approvals / pageSize.value) || 1
})



// 分页逻辑函数
const goToPreviousPage = async (tab) => {
  if (currentPages.value[tab] > 1) {
    currentPages.value[tab]--
    await loadTabData(tab)
  }
}

const goToNextPage = async (tab) => {
  let totalPages
  if (tab === 'companies') {
    totalPages = totalCompanyPages.value
  } else if (tab === 'students') {
    totalPages = totalStudentPages.value
  } else {
    totalPages = totalApprovalPages.value
  }
  
  if (currentPages.value[tab] < totalPages) {
    currentPages.value[tab]++
    await loadTabData(tab)
  }
}

const goToPage = async (tab) => {
  const jumpPage = parseInt(jumpPages.value[tab])
  let totalPages
  
  if (tab === 'companies') {
    totalPages = totalCompanyPages.value
  } else if (tab === 'students') {
    totalPages = totalStudentPages.value
  } else {
    totalPages = totalApprovalPages.value
  }
  
  if (!isNaN(jumpPage) && jumpPage >= 1 && jumpPage <= totalPages) {
    currentPages.value[tab] = jumpPage
    jumpPages.value[tab] = ''
    await loadTabData(tab)
  }
}

// 根据标签页加载数据
const loadTabData = async (tab) => {
  if (tab === 'companies') {
    await loadCompanyDetails()
  } else if (tab === 'students') {
    await loadStudentInternshipDetails()
  } else if (tab === 'approvals') {
    await loadApprovalDetails()
  }
}

// 监听标签页切换
watch(activeDetailTab, async (newTab) => {
  await loadTabData(newTab)
})

// 下载Blob文件通用方法
const downloadBlob = (blob: Blob, filename: string) => {
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  window.URL.revokeObjectURL(url)
}

// 导出综合报表
const exportReport = async () => {
  try {
    const response = await reportsApi.exportReport({
      timeRange: 'custom',
      startDate: startDate.value,
      endDate: endDate.value
    })
    const blob = new Blob([response], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    downloadBlob(blob, `综合统计报表_${new Date().toLocaleDateString()}.xlsx`)
    alert('综合统计报表已成功导出')
  } catch (error) {
    console.error('导出报表失败:', error)
    alert('导出报表失败')
  }
}

// 导出企业入驻报表
const exportCompanyReport = async () => {
  try {
    const response = await reportsApi.exportCompanyReport()
    const blob = new Blob([response], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    downloadBlob(blob, `企业入驻报表_${new Date().toLocaleDateString()}.xlsx`)
    alert('企业入驻报表已成功导出')
  } catch (error) {
    console.error('导出企业报表失败:', error)
    alert('导出企业报表失败')
  }
}

// 导出学生实习报表
const exportStudentReport = async () => {
  try {
    const response = await reportsApi.exportStudentReport()
    const blob = new Blob([response], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    downloadBlob(blob, `学生实习报表_${new Date().toLocaleDateString()}.xlsx`)
    alert('学生实习报表已成功导出')
  } catch (error) {
    console.error('导出学生报表失败:', error)
    alert('导出学生报表失败')
  }
}

// 导出申请审核报表
const exportApprovalReport = async () => {
  try {
    const response = await reportsApi.exportApprovalReport()
    const blob = new Blob([response], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    downloadBlob(blob, `申请审核报表_${new Date().toLocaleDateString()}.xlsx`)
    alert('申请审核报表已成功导出')
  } catch (error) {
    console.error('导出审核报表失败:', error)
    alert('导出审核报表失败')
  }
}

// 获取标签样式类
const getTagClass = (tag: string) => {
  switch (tag) {
    case '自主':
      return 'tag-success'
    case '双向':
      return 'tag-info'
    case '兜底':
      return 'tag-warning'
    default:
      return 'tag-default'
  }
}
</script>

<style scoped>
.reports-container {
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
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.title-wrapper {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.page-subtitle {
  font-size: 14px;
  color: #666;
  margin: 0;
  font-weight: 400;
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

.time-range-selector select {
  padding: 8px 16px;
  border: 1px solid #d9d9d9;
  border-radius: var(--radius-md);
  font-size: 14px;
  transition: all var(--transition-normal);
}

.time-range-selector select:focus {
  outline: none;
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2);
}

.custom-range {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: 12px;
  flex-wrap: wrap;
}

.custom-range input {
  padding: 8px 16px;
  border: 1px solid #d9d9d9;
  border-radius: var(--radius-md);
  font-size: 14px;
  transition: all var(--transition-normal);
}

.custom-range input:focus {
  outline: none;
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2);
}

/* 核心指标卡片 */
.core-metrics {
  padding: 12px;
  background: white;
  border: 1px solid #e0e0e0;
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-normal);
  display: flex;
  flex-direction: column;
  height: 100%;
}

.core-metrics h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-primary);
  margin: 0 0 8px 0;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin: auto 0;
}

.metric-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 8px;
  background-color: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-normal);
  overflow: hidden;
  position: relative;
  text-align: center;
  border: 1px solid #e0e0e0;
  height: 180px;
  justify-content: center;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.metric-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background-color: var(--color-primary);
}

.metric-card:nth-child(1)::before {
  background-color: var(--color-primary);
}

.metric-card:nth-child(2)::before {
  background-color: var(--color-success);
}

.metric-card:nth-child(3)::before {
  background-color: var(--color-info);
}

.metric-icon {
  font-size: 20px;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;
  transition: all var(--transition-normal);
  box-shadow: var(--shadow-sm);
}

.metric-icon:hover {
  transform: scale(1.1);
}

.metric-icon.primary {
  background-color: #e6f7ff;
  color: #1890ff;
}

.metric-icon.success {
  background-color: #f6ffed;
  color: #52c41a;
}

.metric-icon.info {
  background-color: #e6fffb;
  color: #13c2c2;
}

.metric-icon.warning {
  background-color: #fff7e6;
  color: #faad14;
}

.metric-content {
  width: 100%;
}

.metric-content h4 {
  font-size: 16px;
  font-weight: 500;
  color: #666;
  margin: 0 0 6px 0;
}

.metric-value {
  font-size: 32px;
  font-weight: 700;
  color: #333;
  margin: 0 0 6px 0;
  line-height: 1;
}

.metric-change {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  justify-content: center;
}

.metric-change::before {
  content: '';
  width: 10px;
  height: 10px;
  border-radius: var(--radius-full);
  background-color: currentColor;
}

.metric-change.positive {
  color: #52c41a;
}

.metric-change.negative {
  color: #f5222d;
}

/* 图表区域 */
.charts-section {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
  align-items: stretch;
}

.chart-container {
  padding: 12px;
  transition: all var(--transition-normal);
  margin-bottom: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* 图表容器 */
.chart-container {
  background: white;
  border: 1px solid #e8ecf1;
  box-shadow: 0 2px 12px rgba(0,0,0,0.04);
  width: 100%;
}

.chart-container h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0 0 16px 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.chart-subtitle {
  font-size: 13px;
  font-weight: 500;
  color: #8c8c9a;
}

/* 趋势图新样式 */
.trend-chart {
  display: flex;
  flex-wrap: wrap;
  height: 240px;
  position: relative;
  margin-bottom: 28px;
}

.chart-y-axis {
  width: 44px;
  height: 100%;
  position: relative;
  flex-shrink: 0;
}

.y-tick {
  position: absolute;
  right: 8px;
  font-size: 12px;
  color: #999;
  transform: translateY(50%);
  line-height: 1;
}

.chart-body {
  flex: 1;
  height: 100%;
  display: flex;
  justify-content: space-around;
  align-items: flex-end;
  padding: 0 4px 0 4px;
  position: relative;
}

.grid-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 1px;
  background: #f0f0f5;
  pointer-events: none;
}

.bar-group {
  flex: 1;
  max-width: 100px;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  z-index: 1;
}

.chart-x-labels {
  width: calc(100% - 44px);
  margin-left: 44px;
  display: flex;
  justify-content: space-around;
  height: 24px;
  flex-shrink: 0;
}

.x-label {
  flex: 1;
  max-width: 100px;
  font-size: 13px;
  color: #555;
  font-weight: 500;
  text-align: center;
}

.bar-top-label {
  font-size: 16px;
  font-weight: 700;
  color: #1890ff;
  margin-bottom: 6px;
}

.bar-pillar {
  width: 48px;
  border-radius: 6px 6px 0 0;
  background: transparent;
  position: relative;
  overflow: hidden;
  transition: height 1s cubic-bezier(0.4, 0, 0.2, 1);
}

.bar-fill {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 100%;
  border-radius: 6px 6px 0 0;
  background: linear-gradient(180deg, #40a9ff 0%, #1890ff 100%);
}

.bar-group:hover .bar-pillar {
  background: #d6eefc;
}

.bar-group:hover .bar-fill {
  background: linear-gradient(180deg, #69c0ff 0%, #40a9ff 100%);
}



/* 调整详细数据表格文本，防止重叠 */
.details-table th,
.details-table td {
  padding: 14px 18px;
  text-align: left;
  border-bottom: 1px solid #f0f0f0;
  transition: all var(--transition-normal);
  white-space: normal;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 180px;
  line-height: 1.4;
}

/* 详细数据区域 */
.details-section {
  padding: 16px;
  transition: all var(--transition-normal);
  margin-bottom: 12px;
  margin-top: -16px;
}

.details-section h3 {
  font-size: 22px;
  font-weight: 600;
  color: var(--color-primary);
  margin: 0 0 16px 0;
  display: flex;
  align-items: center;
  gap: 16px;
}

.details-tabs-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid #e8e8e8;
  padding-bottom: 12px;
}

.details-tabs {
  display: flex;
  gap: 16px;
  margin-bottom: 0;
  border-bottom: none;
  padding-bottom: 0;
  flex-wrap: wrap;
}

.tab-btn {
  padding: 8px 16px;
  background-color: transparent;
  border: none;
  border-bottom: 3px solid transparent;
  font-size: 14px;
  color: #666;
  cursor: pointer;
  transition: all var(--transition-normal);
  border-radius: var(--radius-md) var(--radius-md) 0 0;
  font-weight: 600;
  position: relative;
}

.tab-btn:hover {
  color: #1890ff;
  background-color: #f0f7ff;
  transform: translateY(-1px);
}

.tab-btn.active {
  color: #1890ff;
  border-bottom-color: #1890ff;
  background-color: #f0f7ff;
  transform: translateY(-1px);
}

/* 详细表格 */
.details-table {
  overflow-x: auto;
  background-color: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: 8px;
  transition: all var(--transition-normal);
}

.details-table:hover {
  box-shadow: var(--shadow-md);
}

.details-table table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.details-table th, .details-table td {
  padding: 8px 12px;
  text-align: left;
  border-bottom: 1px solid #f0f0f0;
  transition: all var(--transition-normal);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 220px;
}

.details-table th {
  background-color: #fafafa;
  font-weight: 600;
  color: #333;
  font-size: 16px;
  position: sticky;
  top: 0;
  z-index: 10;
  border-bottom: 2px solid #e8e8e8;
  white-space: nowrap;
}

.details-table tr:hover td {
  background-color: #f9f9f9;
  transform: translateX(4px);
}

.details-table td {
  font-size: 16px;
  color: #333;
  font-weight: 500;
}

.details-table tr:last-child td {
  border-bottom: none;
}

/* 导出区域 */
.export-section {
  display: flex;
  justify-content: flex-end;
}

.export-buttons {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.export-btn {
  padding: 12px 24px;
  font-size: 14px;
  font-weight: 600;
  transition: all var(--transition-normal);
  box-shadow: var(--shadow-md);
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-success {
  background: linear-gradient(135deg, #52c41a 0%, #389e0d 100%);
  color: white;
}

.btn-success:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
  background: linear-gradient(135deg, #73d13d 0%, #52c41a 100%);
}

.export-btn:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}

.btn-icon {
  font-size: 16px;
}

/* 分页样式 */
.details-table-wrapper {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  padding-top: 16px;
  border-top: 1px solid #e8e8e8;
}

.page-btn {
  padding: 8px 16px;
  background: linear-gradient(135deg, #1890ff 0%, #096dd9 100%);
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: white;
  transition: all 0.3s ease;
  box-shadow: 0 2px 4px rgba(24, 144, 255, 0.2);
}

.page-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #40a9ff 0%, #1890ff 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(24, 144, 255, 0.3);
}

.page-btn:disabled {
  background: linear-gradient(135deg, #d9d9d9 0%, #bfbfbf 100%);
  color: #999;
  cursor: not-allowed;
  box-shadow: none;
}

.page-info {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  background-color: #f5f5f5;
  padding: 8px 16px;
  border-radius: 6px;
}

.page-jump {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-input {
  width: 50px;
  padding: 8px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  color: #333;
  text-align: center;
  transition: all 0.3s ease;
}

.page-input:focus {
  outline: none;
  border-color: #1890ff;
  box-shadow: 0 0 0 3px rgba(24, 144, 255, 0.15);
}

.jump-btn {
  padding: 8px 16px;
}

/* 标签样式 */
.tag {
  display: inline-block;
  padding: 6px 14px;
  border-radius: var(--radius-md);
  font-size: 12px;
  font-weight: 600;
  transition: all var(--transition-normal);
  box-shadow: var(--shadow-sm);
}

.tag-success {
  background: #52c41a;
  color: white;
}

.tag-info {
  background: #1890ff;
  color: white;
}

.tag-warning {
  background: #faad14;
  color: white;
}

.tag-default {
  background: var(--color-primary);
  color: white;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .core-metrics {
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
    height: auto;
  }

  .metric-card {
    padding: 6px;
    height: 120px;
  }

  .metric-icon {
    width: 32px;
    height: 32px;
    font-size: 20px;
    margin-right: 8px;
  }

  .metric-value {
    font-size: 24px;
  }

  .charts-section {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .chart-container {
    min-width: unset;
    padding: 12px;
    height: auto;
  }

  .trend-chart {
    height: 180px;
  }

  .bar-pillar {
    width: 36px;
  }

  .chart-x-labels {
    width: calc(100% - 40px);
    margin-left: 40px;
  }

  .details-tabs {
    flex-wrap: wrap;
    gap: 12px;
  }

  .tab-btn {
    padding: 10px 20px;
  }

  .details-section {
    padding: 24px;
  }

  .details-table table {
    font-size: 12px;
  }

  .details-table th,
  .details-table td {
    padding: 12px 16px;
  }

  .export-btn {
    padding: 12px 24px;
  }
  
  .pagination {
    flex-wrap: wrap;
    gap: 12px;
  }
  
  .page-jump {
    order: 3;
    width: 100%;
    justify-content: center;
  }
}
</style>
