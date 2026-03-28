<script setup lang="ts">
import logger from '@/utils/logger'
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ElScrollbar } from 'element-plus'
import { Search, Refresh, Download, Document, View } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'
import request from '../../utils/request'
import { useSystemSettingsStore } from '../../store/systemSettings'
import { useAuthStore } from '../../store/auth'
import { exportToExcel } from '../../utils/xlsx'
import type { PaginationState } from '@/types/admin'

const route = useRoute()
const systemSettingsStore = useSystemSettingsStore()
const authStore = useAuthStore()

defineOptions({
  components: {
    ElScrollbar
  }
})

interface InternshipStatusData {
  id: string
  student?: {
    studentUserId: string
    name: string
    gender: number
  }
  company?: {
    companyName: string
  }
  position?: {
    positionName: string
  }
  status: number
  companyConfirmStatus: number
  createTime: string
  updateTime: string
  internshipStartTime?: string
  internshipEndTime?: string
  internshipDuration?: number
  feedback?: string
  remark?: string
}

interface SearchForm {
  studentId: string
  name: string
  gender: string
  status: string
  companyName: string
}

const formatDate = (_row: unknown, _column: unknown, cellValue: string): string => {
  if (!cellValue) return ''
  const d = new Date(cellValue)
  if (isNaN(d.getTime())) return ''
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

const formatGender = (_row: unknown, _column: unknown, cellValue: number): string => {
  switch (cellValue) {
    case 1: return '男'
    case 2: return '女'
    default: return '未知'
  }
}

const formatStatus = (_row: unknown, _column: unknown, cellValue: number): string => {
  switch (cellValue) {
    case 0: return '未找到'
    case 1: return '已有Offer'
    case 2: return '已确认'
    case 3: return '已结束'
    case 4: return '已中断'
    case 5: return '延期'
    default: return '未知状态'
  }
}

const getStatusTagType = (status: number): string => {
  switch (status) {
    case 0: return 'info'
    case 1: return 'warning'
    case 2: return 'success'
    case 3: return 'primary'
    case 4: return 'danger'
    case 5: return 'warning'
    default: return 'info'
  }
}

const formatCompanyConfirmStatus = (_row: unknown, _column: unknown, cellValue: number): string => {
  switch (cellValue) {
    case 0: return '未确认'
    case 1: return '已确认'
    case 2: return '已拒绝'
    default: return '未知'
  }
}

const getCompanyConfirmStatusTagType = (status: number): string => {
  switch (status) {
    case 0: return 'info'
    case 1: return 'success'
    case 2: return 'danger'
    default: return 'info'
  }
}

const searchForm = ref<SearchForm>({
  studentId: '',
  name: '',
  gender: '',
  status: '',
  companyName: ''
})

const tableData = ref<InternshipStatusData[]>([])
const loading = ref(false)
const viewDialogVisible = ref(false)
const currentRow = ref<InternshipStatusData | null>(null)

const pagination = ref<PaginationState>({ currentPage: 1, pageSize: 10, total: 0 })

const queryData = async (): Promise<void> => {
  loading.value = true
  try {
    const params = {
      ...searchForm.value,
      page: pagination.value.currentPage,
      pageSize: pagination.value.pageSize
    }
    const response = await request.get('/admin/internship-status', { params })
    if (response.code === 200 && response.data) {
      const dataList = response.data.rows || []
      const totalCount = response.data.total || 0
      tableData.value = dataList
      pagination.value.total = totalCount
    } else {
      tableData.value = []
      pagination.value.total = 0
    }
  } catch (error: unknown) {
    logger.error('查询实习确认信息列表失败:', error)
    tableData.value = []
    pagination.value.total = 0
    const errorMessage = error instanceof Error ? error.message : '未知错误'
    ElMessage.error('查询实习确认信息列表失败：' + errorMessage)
  } finally {
    loading.value = false
  }
}

const clearSearch = (): void => {
  searchForm.value = {
    studentId: '',
    name: '',
    gender: '',
    status: '',
    companyName: ''
  }
  pagination.value.currentPage = 1
  queryData()
}

const handleSizeChange = (size: number): void => {
  pagination.value.pageSize = size
  queryData()
}

const handleCurrentChange = (page: number): void => {
  pagination.value.currentPage = page
  queryData()
}

const handleView = (row: InternshipStatusData): void => {
  logger.log('查看详情数据:', row)
  logger.log('实习开始时间:', row.internshipStartTime)
  logger.log('实习结束时间:', row.internshipEndTime)
  logger.log('实习时长:', row.internshipDuration)
  currentRow.value = row
  viewDialogVisible.value = true
}

const exportSingle = async (row: InternshipStatusData): Promise<void> => {
  try {
    ElMessage({ message: '正在生成实习确认申请表...', type: 'info' })

    // 获取访问令牌
    const currentRole = localStorage.getItem('current_role') || 'ROLE_ADMIN'
    const rolePrefixes: Record<string, string> = {
      'ROLE_STUDENT': 'student_',
      'ROLE_TEACHER': 'teacher_',
      'ROLE_ADMIN': 'admin_',
      'ROLE_COMPANY': 'company_'
    }
    const rolePrefix = rolePrefixes[currentRole] || 'admin_'
    const accessToken = localStorage.getItem(`${rolePrefix}accessToken_${currentRole}`) ||
                        localStorage.getItem(`${rolePrefix}accessToken`) ||
                        localStorage.getItem('admin_accessToken_ROLE_ADMIN')

    // 使用 fetch 下载文件，携带认证 token
    const url = `/api/admin/internship-status/download-application/${row.id}`
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${accessToken}`
      }
    })

    if (!response.ok) {
      throw new Error('下载失败')
    }

    const blob = await response.blob()
    const downloadUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = `${row.student?.studentUserId || ''}_${row.student?.name || ''}_实习确认申请表.docx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(downloadUrl)

    ElMessage.success('下载成功')
  } catch (error: unknown) {
    logger.error('下载失败:', error)
    const errorMessage = error instanceof Error ? error.message : '未知错误'
    ElMessage.error('下载失败：' + errorMessage)
  }
}

const exportAll = async (): Promise<void> => {
  try {
    ElMessage({ message: '正在生成实习确认申请表...', type: 'info' })

    // 使用 authStore 中的 token
    const accessToken = authStore.token

    // 构建查询参数
    const params = new URLSearchParams()
    if (searchForm.value.studentId) params.append('studentId', searchForm.value.studentId)
    if (searchForm.value.name) params.append('name', searchForm.value.name)
    if (searchForm.value.gender) params.append('gender', searchForm.value.gender)
    if (searchForm.value.status) params.append('status', searchForm.value.status)
    if (searchForm.value.companyName) params.append('companyName', searchForm.value.companyName)

    const url = `/api/admin/internship-status/export?${params.toString()}`
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${accessToken}`
      }
    })

    if (!response.ok) {
      throw new Error('导出失败')
    }

    const blob = await response.blob()
    const downloadUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = `实习确认申请表_${new Date().getTime()}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(downloadUrl)

    ElMessage.success('导出成功')
  } catch (error: unknown) {
    logger.error('导出失败:', error)
    const errorMessage = error instanceof Error ? error.message : '未知错误'
    ElMessage.error('导出失败：' + errorMessage)
  }
}

onMounted(async (): Promise<void> => {
  await queryData()
})
</script>

<template>
  <div class="internship-confirm-container">
    <!-- 顶部标题区域 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">实习确认表</h1>
        <p class="page-description">查看和管理学生提交的实习确认申请表</p>
      </div>
      <div class="header-illustration">
        <div class="illustration-circle circle-1"></div>
        <div class="illustration-circle circle-2"></div>
      </div>
    </div>

    <!-- 搜索区域卡片 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" class="search-form" @keyup.enter="queryData">
        <div class="search-row">
          <el-form-item label="学号">
            <el-input
              v-model="searchForm.studentId"
              placeholder="请输入学号"
              clearable
              @keyup.enter="queryData"
            ></el-input>
          </el-form-item>
          <el-form-item label="学生姓名">
            <el-input
              v-model="searchForm.name"
              placeholder="请输入学生姓名"
              clearable
              @keyup.enter="queryData"
            ></el-input>
          </el-form-item>
          <el-form-item label="性别">
            <el-select v-model="searchForm.gender" placeholder="请选择性别" clearable>
              <el-option label="男" :value="1"></el-option>
              <el-option label="女" :value="2"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="实习状态">
            <el-select v-model="searchForm.status" placeholder="请选择实习状态" clearable>
              <el-option label="未找到" :value="0"></el-option>
              <el-option label="已有Offer" :value="1"></el-option>
              <el-option label="已确认" :value="2"></el-option>
              <el-option label="已结束" :value="3"></el-option>
              <el-option label="已中断" :value="4"></el-option>
              <el-option label="延期" :value="5"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="企业名称">
            <el-input
              v-model="searchForm.companyName"
              placeholder="请输入企业名称"
              clearable
              @keyup.enter="queryData"
            ></el-input>
          </el-form-item>
        </div>
        <div class="search-row button-row">
          <el-form-item class="search-actions">
            <el-button type="primary" @click="queryData" class="search-btn">
              <el-icon><Search /></el-icon>&nbsp;查询
            </el-button>
            <el-button type="warning" @click="clearSearch" class="reset-btn">
              <el-icon><Refresh /></el-icon>&nbsp;重置
            </el-button>
            <el-button type="success" @click="exportAll" class="export-btn">
              <el-icon><Download /></el-icon>&nbsp;导出 Excel
            </el-button>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <!-- 数据列表卡片 -->
    <el-card class="table-card" shadow="never">
      <el-table
        :data="tableData"
        border
        style="width: 100%"
        row-key="id"
        class="data-table"
        v-loading="loading"
        :show-header="true"
        :header-cell-style="{
          background: '#f8fbff',
          color: '#409EFF',
          fontWeight: '600',
          padding: '16px 12px'
        }"
      >
        <el-table-column type="index" label="序号" width="60" align="center" :index="(index) => (pagination.currentPage - 1) * pagination.pageSize + index + 1" fixed="left" />
        <el-table-column prop="student.studentUserId" label="学号" width="120" align="center" />
        <el-table-column prop="student.name" label="学生姓名" width="100" align="center" />
        <el-table-column prop="student.gender" label="性别" width="70" align="center" :formatter="formatGender" />
        <el-table-column prop="company.companyName" label="实习单位" min-width="150" align="center" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.company?.companyName">{{ scope.row.company.companyName }}</span>
            <span v-else class="no-data-text">暂无实习单位</span>
          </template>
        </el-table-column>
        <el-table-column prop="position.positionName" label="岗位名称" min-width="120" align="center" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.position?.positionName">{{ scope.row.position.positionName }}</span>
            <span v-else class="no-data-text">暂无岗位</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="实习状态" width="140" align="center">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.status)" size="small" class="status-tag">
              {{ formatStatus(null, null, scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="companyConfirmStatus" label="企业确认状态" width="110" align="center">
          <template #default="scope">
            <el-tag :type="getCompanyConfirmStatusTagType(scope.row.companyConfirmStatus)" size="small" class="status-tag">
              {{ formatCompanyConfirmStatus(null, null, scope.row.companyConfirmStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" align="center" :formatter="formatDate" />
        <el-table-column prop="updateTime" label="更新时间" width="160" align="center" :formatter="formatDate" />
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="scope">
            <div class="action-buttons">
              <el-tooltip content="查看详情" placement="top">
                <el-button type="primary" size="small" @click="handleView(scope.row)" class="table-btn">
                  <el-icon><View /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="下载申请表" placement="top">
                <el-button type="success" size="small" @click="exportSingle(scope.row)" class="table-btn">
                  <el-icon><Download /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-data">
            <el-empty description="暂无数据" />
          </div>
        </template>
      </el-table>

      <!-- 分页组件 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.currentPage"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[5, 10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="pagination.total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          class="custom-pagination"
        />
      </div>
    </el-card>

    <!-- 查看详情对话框 -->
    <el-dialog v-model="viewDialogVisible" title="实习确认申请表详情" width="800px" class="detail-dialog">
      <el-scrollbar height="600px">
        <div v-if="currentRow" class="detail-content">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="学号">{{ currentRow.student?.studentUserId || '' }}</el-descriptions-item>
            <el-descriptions-item label="学生姓名">{{ currentRow.student?.name || '' }}</el-descriptions-item>
            <el-descriptions-item label="性别">{{ formatGender(null, null, currentRow.student?.gender ?? 0) }}</el-descriptions-item>
            <el-descriptions-item label="实习状态">
              <el-tag :type="getStatusTagType(currentRow.status)" size="small" class="status-tag">
                {{ formatStatus(null, null, currentRow.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="实习单位" :span="2">
              <span v-if="currentRow.company?.companyName">{{ currentRow.company.companyName }}</span>
              <span v-else class="no-data-text">暂无实习单位</span>
            </el-descriptions-item>
            <el-descriptions-item label="岗位名称" :span="2">
              <span v-if="currentRow.position?.positionName">{{ currentRow.position.positionName }}</span>
              <span v-else class="no-data-text">暂无岗位</span>
            </el-descriptions-item>
            <el-descriptions-item label="企业确认状态">
              <el-tag :type="getCompanyConfirmStatusTagType(currentRow.companyConfirmStatus)" size="small" class="status-tag">
                {{ formatCompanyConfirmStatus(null, null, currentRow.companyConfirmStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="实习开始时间">{{ formatDate(null, null, currentRow.internshipStartTime ?? '') }}</el-descriptions-item>
            <el-descriptions-item label="实习结束时间">{{ formatDate(null, null, currentRow.internshipEndTime ?? '') }}</el-descriptions-item>
            <el-descriptions-item label="实习时长（天）">{{ currentRow.internshipDuration ?? '' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDate(null, null, currentRow.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ formatDate(null, null, currentRow.updateTime) }}</el-descriptions-item>
            <el-descriptions-item label="反馈" :span="2">
              <div class="feedback-text">{{ currentRow.feedback ?? '无' }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">
              <div class="remark-text">{{ currentRow.remark ?? '无' }}</div>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-scrollbar>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="viewDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.internship-confirm-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

/* 页面标题区域 */
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
    transform: translateY(-8px);
  }
}

/* 卡片通用样式 */
.search-card,
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

.table-card {
  padding: 0;
}

/* 搜索表单样式 */
.search-form {
  width: 100%;
}

.search-row {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.search-row.button-row {
  justify-content: flex-end;
  margin-top: 10px;
}

.search-row .el-form-item {
  margin-bottom: 0;
  flex: 1;
}

.search-row.button-row .el-form-item {
  flex: none;
}

.search-actions {
  flex: none;
  margin-left: auto;
}

.search-btn,
.reset-btn,
.export-btn {
  border-radius: 8px;
  padding: 10px 20px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.search-btn:hover,
.reset-btn:hover,
.export-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.export-btn {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  border: none;
  color: white;
}

.export-btn:hover {
  background: linear-gradient(135deg, #52c41a 0%, #95de64 100%);
}

/* 表格样式 */
.data-table {
  border-radius: 8px;
}

.data-table :deep(.el-table__header) th {
  background-color: #f8fbff;
  color: #409EFF;
  font-weight: 600;
  border-right: 1px solid #f0f7ff;
}

.data-table :deep(.el-table__header th:last-child) {
  border-right: none;
}

.data-table :deep(.el-table__row) td {
  border-bottom: 1px solid #f0f7ff;
}

.data-table :deep(.el-table__row:hover) {
  background-color: #f8fbff;
}

.empty-data {
  padding: 40px 0;
}

.no-data-text {
  color: #909399;
  font-style: italic;
  font-size: 12px;
}

.status-tag {
  font-weight: 600;
  padding: 6px 12px;
  border-radius: 6px;
  white-space: normal;
  word-break: keep-all;
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
  display: flex;
  justify-content: center;
  padding: 20px;
}

.custom-pagination :deep(.el-pagination) {
  font-weight: 600;
}

.custom-pagination :deep(.el-pager li) {
  border-radius: 6px;
  margin: 0 4px;
}

.custom-pagination :deep(.el-pager li.active) {
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  color: white;
}

/* 对话框样式 */
.detail-dialog {
  border-radius: 16px;
  overflow: hidden;
}

.detail-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #f8fbff 0%, #e6f7ff 100%);
  margin: 0;
  padding: 20px;
  border-bottom: 1px solid #e6f7ff;
}

.detail-dialog :deep(.el-dialog__body) {
  padding: 20px;
}

.detail-content {
}

.feedback-text,
.remark-text {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  color: #606266;
}

/* 响应式设计 */
@media screen and (max-width: 1200px) {
  .search-row {
    flex-wrap: wrap;
  }

  .search-actions {
    margin-left: 0;
    width: 100%;
    justify-content: flex-end;
  }

  .search-row.button-row {
    justify-content: flex-end;
  }
}

@media screen and (max-width: 768px) {
  .internship-confirm-container {
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

  .search-row {
    flex-direction: column;
    gap: 12px;
  }

  .search-row.button-row {
    flex-direction: row;
    justify-content: center;
    gap: 10px;
  }

  .search-actions {
    width: 100%;
    justify-content: center;
  }

  .data-table {
    font-size: 12px;
  }

  .action-btn {
    padding: 6px 10px;
  }
}
</style>
