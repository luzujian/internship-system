<template>
  <div class="application-withdrawal-record-management-container">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">撤回申请记录</h1>
        <p class="page-description">查看和管理学生/企业的撤回申请记录</p>
      </div>
      <div class="header-illustration">
        <div class="illustration-circle circle-1"></div>
        <div class="illustration-circle circle-2"></div>
      </div>
    </div>

    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" class="search-form" @keyup.enter="handleSearch">
        <div class="search-row">
          <el-form-item v-if="activeTab === 'student'" label="申请人姓名">
            <el-input
              v-model="searchForm.applicantName"
              placeholder="请输入申请人姓名"
              clearable
              style="width: 150px;"
              @keyup.enter="handleSearch"
            ></el-input>
          </el-form-item>
          <el-form-item v-if="activeTab === 'company'" label="企业名称">
            <el-input
              v-model="searchForm.companyName"
              placeholder="请输入企业名称"
              clearable
              style="width: 200px;"
              @keyup.enter="handleSearch"
            ></el-input>
          </el-form-item>
          <el-form-item v-if="activeTab === 'company'" label="联系人">
            <el-input
              v-model="searchForm.contactPerson"
              placeholder="请输入联系人"
              clearable
              style="width: 150px;"
              @keyup.enter="handleSearch"
            ></el-input>
          </el-form-item>
          <el-form-item label="撤回时间">
            <el-date-picker
              v-model="searchForm.withdrawalTimeRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 240px;"
            ></el-date-picker>
          </el-form-item>
          <el-form-item class="search-actions">
            <el-button type="primary" @click="handleSearch" class="search-btn">
              <el-icon><Search /></el-icon>&nbsp;查询
            </el-button>
            <el-button type="warning" @click="resetForm" class="reset-btn">
              <el-icon><Refresh /></el-icon>&nbsp;重置
            </el-button>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <el-card class="actions-card" shadow="never">
      <div class="actions-container">
        <div class="primary-actions">
          <el-button v-if="authStore.hasPermission('application:withdrawal:delete')" type="danger" @click="handleBatchDelete" class="action-btn">
            <el-icon><Delete /></el-icon>&nbsp;批量删除
          </el-button>
        </div>
        <div class="secondary-actions">
          <el-button v-if="authStore.hasPermission('application:withdrawal:view')" type="success" @click="handleExport" class="action-btn">
            <el-icon><Download /></el-icon>&nbsp;导出Excel
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-tabs v-model="activeTab" class="withdrawal-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="学生撤回记录" name="student"></el-tab-pane>
        <el-tab-pane label="企业撤回记录" name="company"></el-tab-pane>
      </el-tabs>

      <el-table
        v-loading="loading"
        :data="tableData"
        style="width: 100%"
        @selection-change="handleSelectionChange"
        border
        fit
        class="data-table"
      >
        <el-table-column type="selection" width="55" align="center"></el-table-column>
        <el-table-column prop="id" label="ID" width="80" align="center"></el-table-column>
        
        <template v-if="activeTab === 'student'">
          <el-table-column prop="student.name" label="申请人姓名" width="120" align="center" show-overflow-tooltip></el-table-column>
          <el-table-column prop="withdrawalReason" label="撤回原因" min-width="200" show-overflow-tooltip></el-table-column>
          <el-table-column prop="withdrawalTime" label="撤回时间" width="170" align="center">
            <template #default="scope">{{ formatDateTime(scope.row.withdrawalTime) }}</template>
          </el-table-column>
          <el-table-column label="实习状态" width="100" align="center">
            <template #default="scope">
              <el-tag :type="getStatusType(scope.row.internshipStatus?.status)" size="small" class="status-tag">
                {{ getStatusText(scope.row.internshipStatus?.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="company.companyName" label="企业名称" min-width="150" show-overflow-tooltip></el-table-column>
          <el-table-column prop="position.positionName" label="岗位名称" min-width="150" show-overflow-tooltip></el-table-column>
        </template>
        
        <template v-else-if="activeTab === 'company'">
          <el-table-column prop="companyName" label="企业名称" min-width="180" show-overflow-tooltip></el-table-column>
          <el-table-column prop="contactPerson" label="联系人" width="100" align="center"></el-table-column>
          <el-table-column prop="contactPhone" label="联系电话" width="130" align="center"></el-table-column>
          <el-table-column prop="recallReason" label="撤回原因" min-width="200" show-overflow-tooltip></el-table-column>
          <el-table-column prop="recallApplyTime" label="撤回申请时间" width="170" align="center">
            <template #default="scope">{{ formatDateTime(scope.row.recallApplyTime) }}</template>
          </el-table-column>
          <el-table-column label="注册审核状态" width="110" align="center">
            <template #default="scope">
              <el-tag :type="getAuditStatusType(scope.row.auditStatus)" size="small" class="status-tag">
                {{ getAuditStatusText(scope.row.auditStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="auditTime" label="注册审核时间" width="170" align="center">
            <template #default="scope">{{ formatDateTime(scope.row.auditTime) }}</template>
          </el-table-column>
          <el-table-column prop="auditRemark" label="注册审核备注" min-width="150" show-overflow-tooltip></el-table-column>
        </template>
        
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="scope">
            <div class="action-buttons">
              <el-tooltip v-if="authStore.hasPermission('application:withdrawal:view')" content="查看详情" placement="top">
                <el-button size="small" type="success" @click="handleViewDetail(scope.row)" class="table-btn view">
                  <el-icon><View /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('application:withdrawal:delete')" content="删除" placement="top">
                <el-button size="small" type="danger" @click="handleDelete(scope.row)" class="table-btn delete">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
          :current-page="currentPage"
          :page-sizes="[10, 20, 50]"
          :page-size="pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          class="custom-pagination"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailDialogVisible" title="撤回申请记录详情" width="700px" class="view-dialog">
      <div v-if="selectedRecord" class="view-content">
        <template v-if="activeTab === 'student'">
          <h3 class="view-title">学生撤回申请记录 #{{ selectedRecord.id }}</h3>
          <div class="view-meta">
            <div class="meta-item">
              <span class="meta-label">申请人姓名:</span>
              <span class="meta-value">{{ selectedRecord.student?.name || '-' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">申请人 ID:</span>
              <span class="meta-value">{{ selectedRecord.applicantId }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">实习状态:</span>
              <el-tag :type="getStatusType(selectedRecord.internshipStatus?.status)" size="small" class="status-tag">
                {{ getStatusText(selectedRecord.internshipStatus?.status) }}
              </el-tag>
            </div>
            <div class="meta-item">
              <span class="meta-label">撤回时间:</span>
              <span class="meta-value">{{ formatDateTime(selectedRecord.withdrawalTime) }}</span>
            </div>
          </div>
          <el-divider></el-divider>
          <div class="detail-section">
            <div class="detail-row">
              <span class="detail-label">企业名称:</span>
              <span class="detail-value">{{ selectedRecord.company?.companyName || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">岗位名称:</span>
              <span class="detail-value">{{ selectedRecord.position?.positionName || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">撤回原因:</span>
              <div class="detail-content">{{ selectedRecord.withdrawalReason }}</div>
            </div>
          </div>
        </template>
        <template v-else-if="activeTab === 'company'">
          <h3 class="view-title">企业撤回申请记录 #{{ selectedRecord.id }}</h3>
          <div class="view-meta">
            <div class="meta-item">
              <span class="meta-label">企业名称:</span>
              <span class="meta-value">{{ selectedRecord.companyName || '-' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">联系人:</span>
              <span class="meta-value">{{ selectedRecord.contactPerson || '-' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">联系电话:</span>
              <span class="meta-value">{{ selectedRecord.contactPhone || '-' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">注册审核状态:</span>
              <el-tag :type="getAuditStatusType(selectedRecord.auditStatus)" size="small" class="status-tag">
                {{ getAuditStatusText(selectedRecord.auditStatus) }}
              </el-tag>
            </div>
            <div class="meta-item">
              <span class="meta-label">撤回申请时间:</span>
              <span class="meta-value">{{ formatDateTime(selectedRecord.recallApplyTime) }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">注册审核时间:</span>
              <span class="meta-value">{{ formatDateTime(selectedRecord.auditTime) }}</span>
            </div>
          </div>
          <el-divider></el-divider>
          <div class="detail-section">
            <div class="detail-row">
              <span class="detail-label">企业地址:</span>
              <span class="detail-value">{{ selectedRecord.address || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">联系邮箱:</span>
              <span class="detail-value">{{ selectedRecord.contactEmail || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">撤回原因:</span>
              <div class="detail-content">{{ selectedRecord.recallReason || '-' }}</div>
            </div>
            <div class="detail-row">
              <span class="detail-label">注册审核备注:</span>
              <div class="detail-content">{{ selectedRecord.auditRemark || '-' }}</div>
            </div>
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false" class="cancel-btn">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete, Download, View } from '@element-plus/icons-vue'
import { useAuthStore } from '../../store/auth'
import request from '../../utils/request'
import { exportToExcel } from '../../utils/xlsx'

const authStore = useAuthStore()

const activeTab = ref<string>('student')
const currentPage = ref<number>(1)
const pageSize = ref<number>(10)
const total = ref<number>(0)
const loading = ref(false)
const tableData = ref<unknown[]>([])

const searchForm = reactive({
  applicantName: '',
  companyName: '',
  contactPerson: '',
  withdrawalTimeRange: [],
  recallStatus: ''
})

const selectedRecords = ref<unknown[]>([])
const selectedRecord = ref<unknown>(null)
const detailDialogVisible = ref(false)

watch(activeTab, () => {
  currentPage.value = 1
  resetForm()
})

const handleTabChange = (): void => {
  fetchData()
}

const fetchStudentData = async (): Promise<unknown> => {
  const params = {
    page: currentPage.value,
    pageSize: pageSize.value,
    applicantName: searchForm.applicantName || null,
    applicantRole: 'STUDENT',
    startTime: searchForm.withdrawalTimeRange && searchForm.withdrawalTimeRange[0] || null,
    endTime: searchForm.withdrawalTimeRange && searchForm.withdrawalTimeRange[1] || null
  }
  const response = await request.get('/application-withdrawal-records/page', { params })
  return response
}

const fetchCompanyData = async (): Promise<unknown> => {
  const params = {
    page: currentPage.value,
    pageSize: pageSize.value,
    companyName: searchForm.companyName || null,
    contactPerson: searchForm.contactPerson || null,
    startTime: searchForm.withdrawalTimeRange && searchForm.withdrawalTimeRange[0] || null,
    endTime: searchForm.withdrawalTimeRange && searchForm.withdrawalTimeRange[1] || null
  }
  const response = await request.get('/admin/companies/recall-records', { params })
  return response
}

const fetchData = async (): Promise<void> => {
  loading.value = true
  try {
    let response
    if (activeTab.value === 'student') {
      response = await fetchStudentData()
    } else {
      response = await fetchCompanyData()
    }
    
    if (response.data && response.code === 200) {
      tableData.value = response.data.list || response.data.rows || []
      total.value = response.data.total || 0
    } else {
      ElMessage.error(response.message || '获取数据失败')
    }
  } catch (error) {
    logger.error('获取数据失败:', error)
    ElMessage.error('获取数据失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = (): void => {
  currentPage.value = 1
  fetchData()
}

const resetForm = (): void => {
  searchForm.applicantName = ''
  searchForm.companyName = ''
  searchForm.contactPerson = ''
  searchForm.withdrawalTimeRange = []
  searchForm.recallStatus = ''
  currentPage.value = 1
  fetchData()
}

const handlePageChange = (page: number): void => {
  currentPage.value = page
  fetchData()
}

const handleSizeChange = (size: number): void => {
  pageSize.value = size
  currentPage.value = 1
  fetchData()
}

const handleSelectionChange = (selection: unknown[]): void => {
  selectedRecords.value = selection
}

const handleViewDetail = (row: unknown): void => {
  selectedRecord.value = row
  detailDialogVisible.value = true
}

const handleDelete = (row: unknown): Promise<void> => {
  ElMessageBox.confirm('确定要删除这条撤回申请记录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      let response
      if (activeTab.value === 'student') {
        response = await request.delete(`/application-withdrawal-records/${row.id}`)
      } else {
        response = await request.delete(`/admin/companies/recall-records/${row.id}`)
      }
      if (response.data && response.code === 200) {
        ElMessage.success('删除成功')
        fetchData()
      } else {
        ElMessage.error(response.message || '删除失败')
      }
    } catch (error) {
      logger.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

const handleBatchDelete = (): Promise<void> => {
  if (selectedRecords.value.length === 0) {
    ElMessage.warning('请选择要删除的记录')
    return
  }

  ElMessageBox.confirm(`确定要删除选中的 ${selectedRecords.value.length} 条记录吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const ids = selectedRecords.value.map(r => r.id)
      let response
      if (activeTab.value === 'student') {
        response = await request.delete('/application-withdrawal-records/batch', { data: { ids } })
      } else {
        response = await request.delete('/admin/companies/recall-records/batch', { data: { ids } })
      }
      if (response.data && response.code === 200) {
        ElMessage.success('批量删除成功')
        fetchData()
      } else {
        ElMessage.error(response.message || '批量删除失败')
      }
    } catch (error) {
      logger.error('批量删除失败:', error)
      ElMessage.error('批量删除失败')
    }
  }).catch(() => {})
}

const handleExport = async (): Promise<void> => {
  try {
    ElMessage({ message: '正在准备导出数据...', type: 'info' })
    
    let data, fileName
    
    if (activeTab.value === 'student') {
      const response = await request.get('/application-withdrawal-records', {
        params: { applicantRole: 'STUDENT' }
      })
      // 解析后端返回的数据格式：{ code: 200, data: { total: 100, rows: [...] } }
      const allRecords = response.data?.data?.rows || response.data?.data || response.data || []
      const recordsArray = Array.isArray(allRecords) ? allRecords : []

      if (recordsArray.length === 0) {
        ElMessage.warning('没有数据可导出')
        return
      }

      data = recordsArray.map(row => ({
        '记录 ID': row.id,
        '申请人姓名': row.student?.name || row.internshipStatus?.student?.name || '-',
        '撤回原因': row.withdrawalReason,
        '撤回时间': formatDateTime(row.withdrawalTime),
        '实习状态': getStatusText(row.internshipStatus?.status || row.status),
        '企业名称': row.company?.companyName || row.internshipStatus?.company?.companyName || '-',
        '岗位名称': row.position?.positionName || row.internshipStatus?.position?.positionName || '-'
      }))
      fileName = `学生撤回申请记录_${new Date().toISOString().split('T')[0]}.xlsx`
    } else {
      const response = await request.get('/admin/companies/recall-records')
      // 解析后端返回的数据格式：{ code: 200, data: { total: 100, rows: [...] } }
      const allRecords = response.data?.data?.rows || response.data?.rows || response.data || []
      const recordsArray = Array.isArray(allRecords) ? allRecords : []

      if (recordsArray.length === 0) {
        ElMessage.warning('没有数据可导出')
        return
      }

      data = recordsArray.map(row => ({
        '记录 ID': row.id,
        '企业名称': row.companyName || '-',
        '联系人': row.contactPerson || '-',
        '联系电话': row.contactPhone || '-',
        '撤回原因': row.recallReason || '-',
        '撤回申请时间': formatDateTime(row.recallApplyTime),
        '注册审核状态': getAuditStatusText(row.auditStatus),
        '注册审核时间': formatDateTime(row.auditTime),
        '注册审核备注': row.auditRemark || '-'
      }))
      fileName = `企业撤回申请记录_${new Date().toISOString().split('T')[0]}`
    }

    await exportToExcel(data, fileName, '撤回申请记录')
    ElMessage.success('导出成功')
  } catch (error) {
    logger.error('导出Excel失败:', error)
    ElMessage.error('导出Excel失败: ' + (error.message || '未知错误'))
  }
}

const getApplicantRoleText = (role: string): string => {
  const roleMap = {
    'ROLE_STUDENT': '学生',
    'ROLE_COMPANY': '企业'
  }
  return roleMap[role] || role
}

const getApplicantRoleType = (role: string): string => {
  const typeMap = {
    'ROLE_STUDENT': 'success',
    'ROLE_COMPANY': 'warning'
  }
  return typeMap[role] || 'info'
}

const getStatusText = (status: number | string): string => {
  const statusMap = {
    0: '未找到',
    1: '已有 offer',
    2: '已确定',
    3: '已结束'
  }
  return statusMap[status] || '未知'
}

const getStatusType = (status: number | string): string => {
  const typeMap = {
    0: 'info',
    1: 'warning',
    2: 'success',
    3: ''
  }
  return typeMap[status] || 'info'
}

const getRecallStatusText = (status: number | string): string => {
  const statusMap = {
    0: '未申请',
    1: '待审核',
    2: '已批准',
    3: '已拒绝'
  }
  return statusMap[status] || '未知'
}

const getRecallStatusType = (status: number | string): string => {
  const typeMap = {
    0: 'info',
    1: 'warning',
    2: 'success',
    3: 'danger'
  }
  return typeMap[status] || 'info'
}

const getAuditStatusText = (status: number | string): string => {
  const statusMap = {
    0: '待审核',
    1: '已通过',
    2: '已拒绝'
  }
  return statusMap[status] || '未知'
}

const getAuditStatusType = (status: number | string): string => {
  const typeMap = {
    0: 'warning',
    1: 'success',
    2: 'danger'
  }
  return typeMap[status] || 'info'
}

const formatDateTime = (dateTime: string | number | Date): string => {
  if (!dateTime) return '-'
  try {
    const d = new Date(dateTime)
    if (isNaN(d.getTime())) return '-'
    const year = d.getFullYear()
    const month = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const hours = String(d.getHours()).padStart(2, '0')
    const minutes = String(d.getMinutes()).padStart(2, '0')
    const seconds = String(d.getSeconds()).padStart(2, '0')
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
  } catch (e) {
    return '-'
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.application-withdrawal-record-management-container {
  padding: 20px;
  min-height: 100%;
  background: #f5f7fa;
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

.header-content {
  z-index: 1;
}

.page-title {
  color: white;
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 8px 0;
}

.page-description {
  color: rgba(255, 255, 255, 0.95);
  font-size: 14px;
  margin: 0;
  font-weight: 500;
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
    transform: translateY(-8px);
  }
}

.search-card,
.actions-card,
.table-card {
  border-radius: 16px;
  border: none;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  margin-bottom: 24px;
  overflow: hidden;
}

.search-card {
  padding: 24px;
}

.actions-card {
  padding: 20px 24px;
}

.table-card {
  padding: 0;
}

.search-form {
  width: 100%;
}

.search-row {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  flex-wrap: wrap;
}

.search-row :deep(.el-form-item) {
  margin-bottom: 0;
}

.search-actions {
  margin-left: auto;
}

.search-btn,
.reset-btn {
  border-radius: 8px;
  padding: 10px 20px;
}

.actions-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.primary-actions,
.secondary-actions {
  display: flex;
  gap: 12px;
}

.action-btn {
  border-radius: 8px;
  padding: 10px 20px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.action-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.withdrawal-tabs {
  padding: 0 20px 16px 20px;
  margin-bottom: 0;
}

.withdrawal-tabs :deep(.el-tabs__header) {
  margin: 0;
  border-bottom: 1px solid #e4e7ed;
}

.withdrawal-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.withdrawal-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  font-weight: 500;
  padding: 0 24px;
  height: 48px;
  line-height: 48px;
}

.withdrawal-tabs :deep(.el-tabs__item.is-active) {
  color: #409EFF;
}

.withdrawal-tabs :deep(.el-tabs__active-bar) {
  background-color: #409EFF;
}

.data-table {
  border-radius: 8px;
}

.data-table :deep(.el-table__header) th {
  background-color: #f8fbff;
  color: #409EFF;
  font-weight: 600;
}

.data-table :deep(.el-table__row) td {
  border-bottom: 1px solid #f0f7ff;
}

.status-tag {
  border-radius: 12px;
  font-weight: 500;
}

.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
  align-items: center;
}

.table-btn {
  border-radius: 6px;
  padding: 6px 8px;
  transition: all 0.3s ease;
}

.table-btn:hover {
  transform: scale(1.1);
}

.pagination-container {
  padding: 20px;
  display: flex;
  justify-content: flex-end;
}

.custom-pagination {
  margin-top: 0;
}

.view-dialog {
  border-radius: 16px;
  overflow: hidden;
}

.view-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #f8fbff 0%, #e6f7ff 100%);
  margin: 0;
  padding: 20px;
  border-bottom: 1px solid #e6f7ff;
}

.view-dialog :deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 600;
  color: #1e40af;
}

.view-content {
  padding: 10px 0;
}

.view-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 20px;
  text-align: center;
}

.view-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 20px;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 8px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.meta-label {
  color: #606266;
  font-size: 14px;
  font-weight: 500;
}

.meta-value {
  color: #303133;
  font-size: 14px;
}

.detail-section {
  padding: 16px;
  background: white;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}

.detail-row {
  display: flex;
  margin-bottom: 16px;
  align-items: flex-start;
}

.detail-row:last-child {
  margin-bottom: 0;
}

.detail-label {
  min-width: 80px;
  color: #606266;
  font-size: 14px;
  font-weight: 500;
  padding-right: 12px;
}

.detail-value {
  color: #303133;
  font-size: 14px;
  flex: 1;
}

.detail-content {
  flex: 1;
  padding: 12px;
  background: #f8f9fa;
  border-radius: 6px;
  line-height: 1.6;
  color: #606266;
  white-space: pre-wrap;
}

.cancel-btn {
  border-radius: 8px;
  padding: 10px 20px;
}

@media screen and (max-width: 1200px) {
  .search-row {
    flex-wrap: wrap;
  }
  
  .search-actions {
    margin-left: 0;
    width: 100%;
    justify-content: flex-end;
  }
}

@media screen and (max-width: 768px) {
  .application-withdrawal-record-management-container {
    padding: 15px;
  }
  
  .page-header {
    flex-direction: column;
    text-align: center;
    padding: 20px;
  }
  
  .header-illustration {
    margin-top: 15px;
  }
  
  .actions-container {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  
  .primary-actions,
  .secondary-actions {
    justify-content: center;
  }
  
  .search-row {
    flex-direction: column;
    gap: 12px;
  }
  
  .search-actions {
    width: 100%;
    justify-content: center;
  }
}
</style>
