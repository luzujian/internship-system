<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { usePositionStore } from '../../store/position'
import { useAuthStore } from '@/store/auth'
import { exportToExcel } from '../../utils/xlsx'
import emitter from '@/utils/event-bus'

const positionStore = usePositionStore()
const authStore = useAuthStore()

const companyId = computed(() => {
  if (authStore.user?.id) {
    return parseInt(authStore.user.id)
  }
  // 当无法获取企业 ID 时，尝试从 localStorage 获取
  const storedCompanyId = localStorage.getItem('company_companyId_COMPANY')
  if (storedCompanyId) {
    return parseInt(storedCompanyId)
  }
  // 如果仍然无法获取，返回 null 并显示错误提示
  ElMessage.error('未获取到当前登录企业 ID，请重新登录')
  return null
})

onMounted(async () => {
  loading.value = true
  if (!companyId.value) {
    ElMessage.error('未获取到企业 ID，无法加载数据')
    loading.value = false
    return
  }
  try {
    await positionStore.fetchPositions(companyId.value)
    await positionStore.fetchInternshipStatuses(companyId.value)
  } finally {
    loading.value = false
  }
})

const searchForm = ref({
  keyword: '',
  status: '',
  position: ''
})

const loading = ref(false)
const exporting = ref(false)

const detailDialogVisible = ref(false)
const currentStudent = ref(null)

const statusMap = {
  0: '待确认',
  1: '已确认',
  2: '已拒绝'
}

const statusTypeMap = {
  0: 'warning',
  1: 'success',
  2: 'danger'
}

const companyConfirmStatusMap = {
  0: '待确认',
  1: '已确认',
  2: '已拒绝'
}

const positionOptions = computed(() => {
  return positionStore.positions.map(pos => ({ label: pos.positionName, value: pos.positionName }))
})

const positionStats = computed(() => {
  const stats = {}
  
  if (positionStore.positions && Array.isArray(positionStore.positions)) {
    positionStore.positions.forEach(pos => {
      if (pos.positionName) {
        stats[pos.positionName] = {
          position: pos.positionName,
          total: 0,
          pending: 0,
          confirmed: 0,
          rejected: 0,
          status: pos.status
        }
      }
    })
  }
  
  if (positionStore.internshipStatuses && Array.isArray(positionStore.internshipStatuses)) {
    positionStore.internshipStatuses.forEach(item => {
      if (item.positionName && stats[item.positionName]) {
        stats[item.positionName].total++
        if (item.companyConfirmStatus !== undefined) {
          if (item.companyConfirmStatus === 0) {
            stats[item.positionName].pending++
          } else if (item.companyConfirmStatus === 1) {
            stats[item.positionName].confirmed++
          } else if (item.companyConfirmStatus === 2) {
            stats[item.positionName].rejected++
          }
        }
      }
    })
  }
  
  const statsArray = Object.values(stats)
  
  return statsArray.sort((a, b) => {
    if (a.status === 'paused' && b.status !== 'paused') return 1
    if (a.status !== 'paused' && b.status === 'paused') return -1
    
    if (a.pending > 0 && b.pending === 0) return -1
    if (a.pending === 0 && b.pending > 0) return 1
    
    if (a.pending > 0 && b.pending > 0) return b.pending - a.pending
    
    return b.total - a.total
  })
})

const currentPage = ref(1)
const pageSize = ref(10)

const filteredTableData = computed(() => {
  if (!positionStore.internshipStatuses || !Array.isArray(positionStore.internshipStatuses)) {
    return []
  }
  
  let result = [...positionStore.internshipStatuses]
  
  if (searchForm.value.keyword) {
    const keyword = searchForm.value.keyword.toLowerCase()
    result = result.filter(item => 
      item.studentName.toLowerCase().includes(keyword) ||
      item.studentUserId.toLowerCase().includes(keyword)
    )
  }
  
  if (searchForm.value.position) {
    result = result.filter(item => item.positionName === searchForm.value.position)
  }
  
  if (searchForm.value.status !== '') {
    result = result.filter(item => item.companyConfirmStatus === parseInt(searchForm.value.status))
  }
  
  result.sort((a, b) => {
    const statusOrder = { 0: 0, 1: 1, 2: 2 }
    const statusDiff = statusOrder[a.companyConfirmStatus] - statusOrder[b.companyConfirmStatus]
    if (statusDiff !== 0) return statusDiff
    
    return new Date(b.createTime) - new Date(a.createTime)
  })
  
  return result
})

const paginatedTableData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredTableData.value.slice(start, end)
})

const handlePageChange = (page) => {
  currentPage.value = page
}

const handleSearch = () => {
  ElMessage.success('筛选已应用')
}

const handleReset = () => {
  searchForm.value = {
    keyword: '',
    status: '',
    position: ''
  }
  currentPage.value = 1
  ElMessage.info('已重置搜索条件')
}

const handleConfirm = async (row) => {
  try {
    const position = positionStore.positions.find(p => p.positionName === row.positionName)
    const isFull = position && position.remainingQuota <= 0
    
    let confirmMessage = `确认接收 ${row.studentName}(${row.studentUserId}) 来公司实习吗？`
    let confirmButtonText = '确认'
    
    if (isFull) {
      confirmMessage = `该岗位已招满（已确认 ${position.recruitedCount} 人/计划 ${position.plannedRecruit} 人），是否继续确认 ${row.studentName}(${row.studentUserId})？`
      confirmButtonText = '继续确认'
    } else {
      confirmMessage += '确认后该岗位招聘人数将减一。'
    }
    
    await ElMessageBox.confirm(
      confirmMessage,
      '实习确认',
      {
        confirmButtonText: confirmButtonText,
        cancelButtonText: '取消',
        type: isFull ? 'warning' : 'warning',
        center: true,
        distinguishCancelAndClose: true
      }
    )
    
    loading.value = true
    await positionStore.approveInternshipStatus(row.id)
    await positionStore.fetchPositions(companyId.value)
    await positionStore.fetchInternshipStatuses(companyId.value)
    loading.value = false
    
    if (isFull) {
      ElMessage.warning(`已确认 ${row.studentName} 的实习申请（该岗位已超额招聘）`)
    } else {
      ElMessage.success(`已确认 ${row.studentName} 的实习申请`)
    }
    
    console.log('触发通知刷新事件')
    emitter.emit('notification-refresh')
  } catch (error) {
    loading.value = false
    if (error !== 'cancel') {
      ElMessage.error('操作失败，请稍后重试')
    }
  }
}

const handleReject = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要拒绝 ${row.studentName}(${row.studentUserId}) 的实习确认申请吗？`,
      '拒绝申请',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'error',
        center: true
      }
    )
    
    loading.value = true
    await positionStore.rejectInternshipStatus(row.id)
    await positionStore.fetchInternshipStatuses(companyId.value)
    loading.value = false
    ElMessage.success(`已拒绝 ${row.studentName} 的实习确认申请`)
  } catch (error) {
    loading.value = false
    if (error !== 'cancel') {
      ElMessage.error('操作失败，请稍后重试')
    }
  }
}

const handleViewDetail = (row) => {
  currentStudent.value = row
  detailDialogVisible.value = true
}

const formatDate = (date) => {
  if (!date) return ''
  const d = new Date(date)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const calculateInternshipDuration = (startTime, endTime) => {
  if (!startTime || !endTime) {
    return '待确认'
  }
  
  const start = new Date(startTime)
  const end = new Date(endTime)
  
  if (isNaN(start.getTime()) || isNaN(end.getTime())) {
    return '待确认'
  }
  
  const diffTime = Math.abs(end - start)
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24))
  
  return `${diffDays} 天`
}

const handleExport = async () => {
  if (filteredTableData.value.length === 0) {
    ElMessage.warning('没有数据可导出')
    return
  }

  exporting.value = true

  try {
    const sortedData = [...filteredTableData.value].sort((a, b) => {
      if (a.positionName !== b.positionName) {
        return a.positionName.localeCompare(b.positionName, 'zh-CN')
      }
      return new Date(b.createTime) - new Date(a.createTime)
    })

    const exportData = sortedData.map(item => ({
      '应聘岗位': item.positionName,
      '学生姓名': item.studentName,
      '学号': item.studentUserId,
      '性别': item.gender === 1 ? '男' : '女',
      '年级': item.grade,
      '申请日期': formatDate(item.createTime),
      '实习开始时间': formatDate(item.internshipStartTime),
      '实习结束时间': formatDate(item.internshipEndTime),
      '实习时长': item.internshipDuration ? `${item.internshipDuration}天` : '待确认',
      '确认状态': companyConfirmStatusMap[item.companyConfirmStatus],
      '备注': item.remark || ''
    }))

    await exportToExcel(exportData, `实习确认名单_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}`, '实习确认名单')

    ElMessage.success(`成功导出 ${filteredTableData.value.length} 条数据`)
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <div class="internship-confirm">
    <div class="page-header">
      <h2>实习确认</h2>
      <p>确认学生来本公司实习，确认后该岗位的招聘人数将相应减一</p>
    </div>

    <div class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="关键词">
          <el-input 
            v-model="searchForm.keyword" 
            placeholder="请输入学生姓名或学号" 
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable style="width: 150px">
            <el-option label="待确认" value="0" />
            <el-option label="已确认" value="1" />
            <el-option label="已拒绝" value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="应聘岗位">
          <el-select v-model="searchForm.position" placeholder="请选择岗位" clearable style="width: 180px">
            <el-option
              v-for="item in positionOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleExport" :loading="exporting">
            <el-icon style="margin-right: 4px"><Download /></el-icon>
            导出Excel
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="stats-cards">
      <div 
        v-for="stat in positionStats" 
        :key="stat.position"
        class="stat-card"
        :class="{ 
          active: searchForm.position === stat.position,
          paused: stat.status === 'paused'
        }"
        @click="searchForm.position = searchForm.position === stat.position ? '' : stat.position"
      >
        <div class="stat-header">
          <div class="stat-title">
            {{ stat.position }}
            <el-tag v-if="stat.status === 'paused'" type="info" size="small" class="pause-tag">
              暂停
            </el-tag>
          </div>
          <div class="stat-total">共 {{ stat.total }} 人</div>
        </div>
        <div class="stat-details">
          <div class="stat-item">
            <span class="stat-label">待确认</span>
            <span class="stat-value pending">{{ stat.pending }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">已确认</span>
            <span class="stat-value confirmed">{{ stat.confirmed }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">已拒绝</span>
            <span class="stat-value rejected">{{ stat.rejected }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="table-card">
      <el-table 
        :data="paginatedTableData" 
        v-loading="loading"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="positionName" label="应聘岗位" width="180" />
        <el-table-column prop="studentName" label="学生姓名" width="120" />
        <el-table-column prop="studentUserId" label="学号" width="120" />
        <el-table-column label="年级和专业" width="200">
          <template #default="{ row }">
            {{ row.grade }}级 {{ row.majorName || '' }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="申请日期" width="120">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="companyConfirmStatus" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.companyConfirmStatus]">
              {{ statusMap[row.companyConfirmStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="240">
          <template #default="{ row }">
            <el-button 
              v-if="row.companyConfirmStatus === 0"
              type="success" 
              size="small" 
              @click="handleConfirm(row)"
            >
              确认
            </el-button>
            <el-button 
              v-if="row.companyConfirmStatus === 0"
              type="danger" 
              size="small" 
              @click="handleReject(row)"
            >
              拒绝
            </el-button>
            <el-button 
              type="primary" 
              size="small" 
              @click="handleViewDetail(row)"
            >
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          background
          layout="total, prev, pager, next, jumper"
          :total="filteredTableData.length"
          :page-size="pageSize"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <el-dialog
      v-model="detailDialogVisible"
      title="学生实习确认详情"
      width="900px"
      :close-on-click-modal="false"
    >
      <div v-if="currentStudent" class="student-detail">
        <div class="detail-section">
          <h3 class="section-title">基本信息</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="学生姓名">{{ currentStudent.studentName }}</el-descriptions-item>
            <el-descriptions-item label="学号">{{ currentStudent.studentUserId }}</el-descriptions-item>
            <el-descriptions-item label="性别" :span="2">{{ currentStudent.gender === 1 ? '男' : '女' }}</el-descriptions-item>
            <el-descriptions-item label="年级" :span="2">{{ currentStudent.grade }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section">
          <h3 class="section-title">实习信息</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="应聘岗位">{{ currentStudent.positionName }}</el-descriptions-item>
            <el-descriptions-item label="申请日期">{{ formatDate(currentStudent.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="确认状态" :span="2">
              <el-tag :type="statusTypeMap[currentStudent.companyConfirmStatus]">
                {{ statusMap[currentStudent.companyConfirmStatus] }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section">
          <h3 class="section-title">实习时间</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="实习开始时间">
              {{ formatDate(currentStudent.internshipStartTime) || '待确认' }}
            </el-descriptions-item>
            <el-descriptions-item label="实习结束时间">
              {{ formatDate(currentStudent.internshipEndTime) || '待确认' }}
            </el-descriptions-item>
            <el-descriptions-item label="实习时长" :span="2">
              {{ currentStudent.internshipDuration ? `${currentStudent.internshipDuration}天` : '待确认' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section">
          <h3 class="section-title">备注</h3>
          <div class="remark-content">
            {{ currentStudent.remark || '暂无备注' }}
          </div>
        </div>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="detailDialogVisible = false">关闭</el-button>
          <el-button 
            v-if="currentStudent && currentStudent.companyConfirmStatus === 0" 
            type="success" 
            @click="handleConfirm(currentStudent); detailDialogVisible = false"
          >
            确认接收
          </el-button>
          <el-button 
            v-if="currentStudent && currentStudent.companyConfirmStatus === 0" 
            type="danger" 
            @click="handleReject(currentStudent); detailDialogVisible = false"
          >
            拒绝申请
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.internship-confirm {
  padding: 0;
}

.page-header {
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  color: white;
  padding: 24px 40px;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(64, 158, 255, 0.3);
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 8px 0;
  font-size: 28px;
  font-weight: bold;
  letter-spacing: 1px;
}

.page-header p {
  margin: 0;
  font-size: 14px;
  opacity: 0.95;
}

.search-card {
  background: white;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.search-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  padding: 20px;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06),
              0 8px 16px rgba(0, 0, 0, 0.08),
              0 16px 32px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid rgba(255, 255, 255, 0.6);
  position: relative;
  overflow: hidden;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, var(--card-color-1), var(--card-color-2));
  opacity: 0.8;
}

.stat-card::after {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle at center, var(--card-color-1) 0%, transparent 70%);
  opacity: 0.05;
  pointer-events: none;
}

.stat-card:nth-child(4n+1) {
  --card-color-1: #409EFF;
  --card-color-2: #36d1dc;
}

.stat-card:nth-child(4n+2) {
  --card-color-1: #67C23A;
  --card-color-2: #4facfe;
}

.stat-card:nth-child(4n+3) {
  --card-color-1: #E6A23C;
  --card-color-2: #f093fb;
}

.stat-card:nth-child(4n+4) {
  --card-color-1: #F56C6C;
  --card-color-2: #ff9a9e;
}

.stat-card:hover {
  transform: translateY(-6px) scale(1.03);
  box-shadow: 0 8px 16px rgba(64, 158, 255, 0.15),
              0 16px 32px rgba(64, 158, 255, 0.2),
              0 24px 48px rgba(64, 158, 255, 0.25);
  border-color: rgba(64, 158, 255, 0.3);
}

.stat-card:hover::before {
  opacity: 1;
  height: 5px;
}

.stat-card:hover::after {
  opacity: 0.1;
}

.stat-card.active {
  border-color: #409EFF;
  background: rgba(248, 255, 254, 0.9);
}

.stat-card.paused {
  opacity: 0.9;
  background: #ffebee;
  border: 2px solid #f56c6c;
  position: relative;
}

.stat-card.paused::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: repeating-linear-gradient(
    45deg,
    transparent,
    transparent 10px,
    rgba(245, 108, 108, 0.05) 10px,
    rgba(245, 108, 108, 0.05) 20px
  );
  pointer-events: none;
  z-index: 1;
}

.stat-card.paused:hover {
  opacity: 1;
  background: #ffcdd2;
  border-color: #e6a23c;
}

.stat-card.paused .stat-title {
  text-decoration: line-through;
  color: #909399;
}

.stat-card.paused .stat-total {
  color: #f56c6c;
  font-weight: 600;
}

.pause-tag {
  margin-left: 8px;
}

.stat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e8e8e8;
}

.stat-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.stat-total {
  font-size: 14px;
  color: #909399;
}

.stat-details {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-label {
  font-size: 12px;
  color: #909399;
}

.stat-value {
  font-size: 20px;
  font-weight: bold;
}

.stat-value.pending {
  color: #E6A23C;
}

.stat-value.confirmed {
  color: #67C23A;
}

.stat-value.rejected {
  color: #F56C6C;
}

.table-card {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

:deep(.el-table) {
  border-radius: 8px;
  overflow: hidden;
}

:deep(.el-table th) {
  background: #f5f7fa;
  font-weight: 600;
  color: #333;
}

:deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
  background: #fafafa;
}

:deep(.el-button--small) {
  padding: 6px 12px;
  font-size: 13px;
}

.student-detail {
  max-height: none;
  overflow-y: visible;
}

.detail-section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 12px 0;
  padding-bottom: 8px;
  border-bottom: 2px solid #409EFF;
}

.remark-content {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 4px;
  line-height: 1.8;
  color: #606266;
  font-size: 14px;
}

:deep(.el-descriptions) {
  font-size: 14px;
}

:deep(.el-descriptions__label) {
  font-weight: 600;
  color: #303133;
}

:deep(.el-dialog__body) {
  padding: 20px 24px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
