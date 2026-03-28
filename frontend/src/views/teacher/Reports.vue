<template>
  <div class="reports-container">
    <div class="page-header fade-in">
      <div class="title-wrapper">
        <h2>统计报表</h2>
        <p class="page-subtitle">本学期实习数据总览</p>
      </div>
      <div class="time-range-selector">
        <span class="current-semester">当前学期：{{ startDate }} 至 {{ endDate }}</span>
      </div>
    </div>

    <!-- 趋势图表 -->
    <div class="charts-section">
      <div class="chart-container card fade-in" style="animation-delay: 0.5s">
        <h3>企业入驻趋势 <span class="chart-subtitle">(2026年季度)</span></h3>
        <div class="chart-placeholder">
          <div class="chart-bar-container">
            <div v-for="(data, index) in companyTrend" :key="index" class="chart-bar">
              <div class="bar-wrapper">
                <div class="bar" :style="{ '--height': `${Math.min((data.value / maxCompanyValue) * 100, 90)}` }">
                  <div class="bar-value">{{ data.value }}</div>
                </div>
              </div>
              <div class="bar-label">{{ data.label }}</div>
            </div>
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
              <p class="metric-change" :class="metrics.companyChange >= 0 ? 'positive' : 'negative'">
                {{ metrics.companyChange >= 0 ? '+' : '' }}{{ metrics.companyChange }}%
              </p>
            </div>
          </div>
          <div class="metric-card">
            <div class="metric-icon success">👥</div>
            <div class="metric-content">
              <h4>学生实习率</h4>
              <p class="metric-value">{{ metrics.internshipRate }}%</p>
              <p class="metric-change" :class="metrics.internshipRateChange >= 0 ? 'positive' : 'negative'">
                {{ metrics.internshipRateChange >= 0 ? '+' : '' }}{{ metrics.internshipRateChange }}%
              </p>
            </div>
          </div>
          <div class="metric-card">
            <div class="metric-icon info">📊</div>
            <div class="metric-content">
              <h4>申请审核数量</h4>
              <p class="metric-value">{{ metrics.approvalCount }}</p>
              <p class="metric-change" :class="metrics.approvalCountChange >= 0 ? 'positive' : 'negative'">
                {{ metrics.approvalCountChange >= 0 ? '+' : '' }}{{ metrics.approvalCountChange }}%
              </p>
            </div>
          </div>
          <div class="metric-card">
            <div class="metric-icon warning">📈</div>
            <div class="metric-content">
              <h4>资源下载量</h4>
              <p class="metric-value">{{ metrics.resourceDownloads }}</p>
              <p class="metric-change" :class="metrics.resourceDownloadsChange >= 0 ? 'positive' : 'negative'">
                {{ metrics.resourceDownloadsChange >= 0 ? '+' : '' }}{{ metrics.resourceDownloadsChange }}%
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 详细数据表格 -->
    <div class="details-section card fade-in" style="animation-delay: 0.7s">
      <h3>详细数据</h3>
      
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
    const response = await reportsApi.getCompanyTrend({ year: 2026 })
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
  return Math.max(...companyTrend.value.map(item => item.value))
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
  font-size: 28px;
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
  padding: 32px;
  background: white;
  border: 1px solid #e0e0e0;
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-normal);
  min-height: 450px;
  display: flex;
  flex-direction: column;
}

.core-metrics h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-primary);
  margin: 0 0 24px 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  flex: 1;
}

.metric-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px;
  background-color: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-normal);
  overflow: hidden;
  position: relative;
  text-align: center;
  border: 1px solid #e0e0e0;
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

.metric-card:nth-child(4)::before {
  background-color: var(--color-warning);
}

.metric-icon {
  font-size: 32px;
  width: 60px;
  height: 60px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
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
  margin: 0 0 12px 0;
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
  gap: 24px;
  margin-bottom: 24px;
}

.chart-container {
  padding: 24px;
  transition: all var(--transition-normal);
  margin-bottom: 0;
  min-height: 450px;
  display: flex;
  flex-direction: column;
}

/* 图表容器简化样式 */
.chart-container {
  background: white;
  border: 1px solid #e0e0e0;
  box-shadow: var(--shadow-sm);
  width: 100%;
}

.chart-container h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-primary);
  margin: 0 0 24px 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.chart-subtitle {
  font-size: 14px;
  font-weight: 500;
  opacity: 0.8;
  color: #666;
}

.chart-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
}

/* 柱状图 */
.chart-bar-container {
  display: flex;
  align-items: flex-end;
  gap: 32px;
  height: 100%;
  width: 100%;
  padding: 60px 40px 80px 40px;
  background-color: #f8f9ff;
  border-radius: 12px;
  border: 1px solid #e0e0e0;
  box-shadow: none;
  justify-content: center;
  align-items: center;
  margin: 0 auto;
  max-width: 100%;
}

.chart-bar {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  height: 100%;
  position: relative;
  min-height: 300px;
  max-width: 120px;
}

.bar-wrapper {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  position: relative;
  background-color: transparent;
  border-radius: 12px 12px 0 0;
  padding: 20px 0;
  overflow: hidden;
}

.bar-wrapper::before {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: #e0e0e0;
}

.bar {
  width: 80%;
  margin: 0 auto;
  background: #1890ff;
  border-radius: 4px 4px 0 0;
  position: relative;
  animation: barGrow 1.5s ease forwards;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 12px;
  box-shadow: none !important;
  transition: none !important;
}

.bar-value {
  color: white;
  font-size: 16px;
  font-weight: 600;
  z-index: 1;
  white-space: nowrap;
}

@keyframes barGrow {
  from {
    height: 0;
  }
  to {
    height: calc(var(--height, 0) * 1%);
  }
}

.bar:hover {
  background: #1890ff;
  transition: none;
}

.bar-label {
  font-size: 16px;
  color: #333;
  margin-top: 8px;
  font-weight: 600;
  text-align: center;
  padding: 8px 16px;
  background-color: transparent;
  border-radius: 0;
  min-width: auto;
  box-shadow: none;
}

/* 折线图 */
.chart-line-container {
  width: 100%;
  height: 100%;
  position: relative;
  padding: 40px 60px 120px 60px;
  background-color: white;
  border-radius: 12px;
  border: 1px solid #e0e0e0;
  box-shadow: none;
  overflow: visible;
}

.chart-line-container::before {
  content: none;
}

.chart-grid {
  position: absolute;
  top: 40px;
  left: 60px;
  right: 60px;
  bottom: 80px;
  pointer-events: none;
  z-index: 1;
}

.grid-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 1px;
  background-color: #e0e0e0;
  z-index: 1;
}

.grid-line::before {
  content: none;
}

.grid-label {
  position: absolute;
  left: -45px;
  transform: translateY(-50%);
  font-size: 12px;
  color: #666;
  font-weight: 500;
  z-index: 2;
  white-space: nowrap;
}

.chart-line {
  width: 100%;
  height: 100%;
  position: relative;
  z-index: 2;
}

.chart-labels {
  display: flex;
  justify-content: space-between;
  position: absolute;
  bottom: 40px;
  left: 60px;
  right: 60px;
  flex-wrap: nowrap;
  z-index: 2;
}

.chart-label {
  font-size: 14px;
  color: #333;
  font-weight: 600;
  white-space: nowrap;
  flex: 1;
  text-align: center;
  padding: 4px 8px;
  min-width: 60px;
}

.chart-label:hover {
  transform: none;
  box-shadow: none;
}

/* 确保SVG容器有足够空间 */
.chart-line svg {
  width: 100%;
  height: 100%;
  overflow: visible;
  z-index: 3;
  position: relative;
}

/* 图表动画 */
.chart-line svg polyline {
  stroke-dasharray: 2000;
  stroke-dashoffset: 2000;
  animation: lineDraw 2s cubic-bezier(0.4, 0, 0.2, 1) forwards;
  z-index: 4;
  position: relative;
}

@keyframes lineDraw {
  to {
    stroke-dashoffset: 0;
  }
}

.chart-line svg polygon {
  opacity: 0;
  animation: areaFadeIn 1.5s cubic-bezier(0.4, 0, 0.2, 1) forwards 0.5s;
  z-index: 3;
}

@keyframes areaFadeIn {
  to {
    opacity: 0.7;
  }
}

.chart-line svg circle {
  animation: pointAppear 0.8s cubic-bezier(0.4, 0, 0.2, 1) forwards;
  animation-delay: calc(var(--index) * 0.3s + 0.5s);
  opacity: 0;
  transition: none !important;
  z-index: 5;
  cursor: pointer;
  box-shadow: none !important;
}

.chart-line svg circle:hover {
  transform: none;
  box-shadow: none !important;
}

@keyframes pointAppear {
  0% {
    opacity: 0;
    transform: scale(0);
  }
  50% {
    transform: scale(1.2);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
}

.chart-line svg text {
  animation: textAppear 0.8s cubic-bezier(0.4, 0, 0.2, 1) forwards;
  animation-delay: calc(var(--index) * 0.3s + 1s);
  opacity: 0;
  z-index: 6;
  pointer-events: none;
  transition: none !important;
}

.chart-line svg text:hover {
  transform: none;
  transition: none !important;
}

@keyframes textAppear {
  0% {
    opacity: 0;
    transform: translateY(10px);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 数据点标签样式 */
.data-label {
  font-size: 14px !important;
  font-weight: 600 !important;
  fill: #333 !important;
  background-color: transparent !important;
  padding: 0 !important;
  border-radius: 0 !important;
  box-shadow: none !important;
  pointer-events: all !important;
  transition: all 0.3s ease !important;
  text-shadow: none !important;
}

.data-label:hover {
  transform: none !important;
  box-shadow: none !important;
}

/* 确保折线图容器有足够空间 */
.chart-line {
  width: 100%;
  height: 100%;
  position: relative;
  z-index: 2;
  min-height: 350px;
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
  padding: 24px;
  transition: all var(--transition-normal);
  margin-bottom: 24px;
  margin-top: 0;
}

.details-section h3 {
  font-size: 22px;
  font-weight: 600;
  color: var(--color-primary);
  margin: 0 0 32px 0;
  display: flex;
  align-items: center;
  gap: 16px;
}

.details-tabs {
  display: flex;
  gap: 24px;
  margin-bottom: 32px;
  border-bottom: 1px solid #e8e8e8;
  padding-bottom: 16px;
  flex-wrap: wrap;
}

.tab-btn {
  padding: 12px 24px;
  background-color: transparent;
  border: none;
  border-bottom: 3px solid transparent;
  font-size: 16px;
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
  padding: 16px;
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
  padding: 16px 20px;
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
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .metric-card {
    padding: 20px;
  }

  .metric-icon {
    width: 60px;
    height: 60px;
    font-size: 32px;
    margin-right: 16px;
  }

  .metric-value {
    font-size: 28px;
  }

  .charts-section {
    grid-template-columns: 1fr;
    gap: 20px;
  }

  .chart-container {
    min-width: unset;
    padding: 24px;
  }

  .chart-placeholder {
    height: 280px;
  }

  .chart-bar-container {
    gap: 16px;
    padding: 20px 0;
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
