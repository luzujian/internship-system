<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createWorkbook, jsonToSheet, appendSheet, writeWorkbook } from '../../utils/xlsx'
import { usePositionStore } from '../../store/position'
import { useAuthStore } from '@/store/auth'
import applicationApi from '@/api/InternshipApplicationService'

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

const exporting = ref(false)
const exportMode = ref('excel')

const searchForm = ref({
  keyword: '',
  position: '',
  status: ''
})

const positionOptions = computed(() => {
  return positionStore.positions.map(pos => ({ label: pos.positionName, value: pos.positionName }))
})

const statusOptions = [
  { label: '未查看', value: false },
  { label: '已查看', value: true }
]

onMounted(() => {
  if (!companyId.value) {
    ElMessage.error('未获取到企业 ID，无法加载数据')
    return
  }
  positionStore.fetchPositions(companyId.value)
  positionStore.fetchJobApplications()
})

const detailDialogVisible = ref(false)
const currentStudent = ref(null)

const groupedApplications = computed(() => {
  const groups = {}

  // 1. 先从岗位列表初始化所有岗位
  if (positionStore.positions && Array.isArray(positionStore.positions)) {
    positionStore.positions.forEach(pos => {
      if (pos.positionName) {
        groups[pos.positionName] = {
          applications: [],
          status: pos.status,
          isSystemPosition: true
        }
      }
    })
  }

  // 2. 遍历申请列表，将所有申请分配到对应岗位组
  // 即使岗位不存在于系统岗位列表中，也为其创建组（避免数据丢失）
  if (positionStore.jobApplications && Array.isArray(positionStore.jobApplications)) {
    let data = positionStore.jobApplications

    if (searchForm.value.keyword) {
      data = data.filter(item =>
        (item.studentName && item.studentName.includes(searchForm.value.keyword)) ||
        (item.studentId && item.studentId.includes(searchForm.value.keyword))
      )
    }

    if (searchForm.value.position) {
      data = data.filter(item => (item.position || item.positionName) === searchForm.value.position)
    }

    if (searchForm.value.status === true || searchForm.value.status === false) {
      data = data.filter(item => item.viewed === searchForm.value.status)
    }

    data.forEach(item => {
      // 优先使用 position 字段，其次使用 positionName，最后使用空字符串
      const positionName = item.position || item.positionName || '未知岗位'
      // 如果该岗位不存在于 groups 中，创建一个新的组（保留历史数据）
      if (!groups[positionName]) {
        groups[positionName] = {
          applications: [],
          status: 'unknown', // 标记为未知状态
          isSystemPosition: positionStore.positions?.some(p => p.positionName === positionName)
        }
      }
      groups[positionName].applications.push(item)
    })
  }

  Object.keys(groups).forEach(position => {
    groups[position].applications.sort((a, b) => {
      // 待处理的排前面（按时间倒序）
      if (a.status === 'pending' && b.status !== 'pending') return -1
      if (a.status !== 'pending' && b.status === 'pending') return 1
      return new Date(b.applyDate) - new Date(a.applyDate)
    })
  })

  return Object.entries(groups).map(([position, group]) => ({
    position,
    applications: group.applications,
    total: group.applications.length,
    pending: group.applications.filter(a => a.status === 'pending').length,
    processed: group.applications.filter(a => a.status !== 'pending').length,
    status: group.status,
    isSystemPosition: group.isSystemPosition
  })).sort((a, b) => {
    // 系统岗位排前面
    if (a.isSystemPosition && !b.isSystemPosition) return -1
    if (!a.isSystemPosition && b.isSystemPosition) return 1

    if (a.status === 'paused' && b.status !== 'paused') return 1
    if (a.status !== 'paused' && b.status === 'paused') return -1

    // 待处理人数多的排前面
    if (a.pending > 0 && b.pending === 0) return -1
    if (a.pending === 0 && b.pending > 0) return 1

    if (a.pending > 0 && b.pending > 0) return b.pending - a.pending

    return b.total - a.total
  })
})

const filteredTableData = computed(() => {
  if (!positionStore.jobApplications || !Array.isArray(positionStore.jobApplications)) {
    return []
  }

  let data = [...positionStore.jobApplications]

  if (searchForm.value.keyword) {
    data = data.filter(item =>
      item.studentName.includes(searchForm.value.keyword) ||
      item.studentNo.includes(searchForm.value.keyword)
    )
  }

  if (searchForm.value.position) {
    data = data.filter(item => item.positionName === searchForm.value.position)
  }

  // StudentJobApplication uses 'status' instead of 'viewed'
  if (searchForm.value.status === true || searchForm.value.status === false) {
    // No viewed field in StudentJobApplication, so no filtering by viewed status
  }

  data.sort((a, b) => {
    return new Date(b.applyDate || b.createTime) - new Date(a.applyDate || a.createTime)
  })

  return data
})

const currentPage = ref(1)
const pageSize = ref(20)

const paginatedTableData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredTableData.value.slice(start, end)
})

const handlePageChange = (page) => {
  currentPage.value = page
}

const handlePositionClick = (position) => {
  searchForm.value.position = searchForm.value.position === position ? '' : position
  currentPage.value = 1
}

const handleSearch = () => {
  // 筛选是本地操作，无需显示加载状态
  ElMessage.success('筛选已应用')
}

const handleReset = () => {
  searchForm.value = {
    keyword: '',
    position: '',
    status: ''
  }
  currentPage.value = 1
  ElMessage.info('已重置搜索条件')
}

const handleApprove = async (row) => {
  try {
    await ElMessageBox.confirm('同意该学生的申请，代表该学生面试通过，是否继续？', '确认同意', {
      confirmButtonText: '确定同意',
      cancelButtonText: '取消',
      type: 'success'
    })

    const response = await applicationApi.updateJobApplicationStatus(row.id, 'approved')
    if (response.code === 200) {
      ElMessage.success('已同意该学生的申请')
      // 刷新数据
      await positionStore.fetchJobApplications()
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleReject = async (row) => {
  try {
    await ElMessageBox.confirm('拒绝该学生的申请，代表该学生面试未通过，是否继续？', '确认拒绝', {
      confirmButtonText: '确定拒绝',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const response = await applicationApi.updateJobApplicationStatus(row.id, 'rejected')
    if (response.code === 200) {
      ElMessage.success('已拒绝该学生的申请')
      // 刷新数据
      await positionStore.fetchJobApplications()
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

// 面试通过
const handleInterviewPass = async (row) => {
  try {
    await ElMessageBox.confirm('确认该学生面试通过？', '面试通过', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'success'
    })

    const response = await applicationApi.updateJobApplicationStatus(row.id, 'interview_passed')
    if (response.code === 200) {
      ElMessage.success('已确认面试通过')
      await positionStore.fetchJobApplications()
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

// 面试没通过
const handleInterviewFail = async (row) => {
  try {
    await ElMessageBox.confirm('确认该学生面试没通过？', '面试没通过', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const response = await applicationApi.updateJobApplicationStatus(row.id, 'interview_failed')
    if (response.code === 200) {
      ElMessage.success('已确认面试没通过')
      await positionStore.fetchJobApplications()
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleViewDetail = async (row) => {
  currentStudent.value = row
  detailDialogVisible.value = true

  console.log('查看学生详情:', row)
}

const handleExport = async () => {
  let dataToExport = [...positionStore.jobApplications]

  if (searchForm.value.keyword) {
    dataToExport = dataToExport.filter(item =>
      item.studentName.includes(searchForm.value.keyword) ||
      item.studentNo.includes(searchForm.value.keyword)
    )
  }

  if (searchForm.value.position) {
    dataToExport = dataToExport.filter(item => item.positionName === searchForm.value.position)
  }

  if (dataToExport.length === 0) {
    ElMessage.warning('没有数据可导出')
    return
  }

  exporting.value = true

  try {
    const groupedData: Record<string, any[]> = {}
    dataToExport.forEach(item => {
      if (!groupedData[item.positionName]) {
        groupedData[item.positionName] = []
      }
      groupedData[item.positionName].push(item)
    })

    Object.keys(groupedData).forEach(position => {
      groupedData[position].sort((a, b) => {
        return new Date(b.applyDate || b.createTime) - new Date(a.applyDate || a.createTime)
      })
    })

    const sortedData = Object.values(groupedData).flat()

    const exportData = sortedData.map(item => ({
      '应聘岗位': item.positionName,
      '学生姓名': item.studentName,
      '学号': item.studentNo,
      '年级': item.grade,
      '专业': item.major,
      '联系电话': item.phone,
      '申请日期': item.applyDate,
      '申请状态': item.status === 'pending' ? '待审核' : item.status === 'approved' ? '已通过' : item.status === 'rejected' ? '已拒绝' : item.status,
      '自我介绍': item.selfIntroduction
    }))

    const ws = await jsonToSheet(exportData)
    const wb = await createWorkbook()
    await appendSheet(wb, ws, '岗位申请名单')

    const fileName = `岗位申请名单_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}`

    await writeWorkbook(wb, fileName)
    ElMessage.success(`成功导出 ${dataToExport.length} 条数据`)
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

const exportWithFiles = async (workbook, baseFileName, data) => {
  try {
    const JSZip = (await import('jszip')).default
    const zip = new JSZip()

    const excelBuffer = await writeWorkbookToBuffer(workbook)
    zip.file(baseFileName + '.xlsx', excelBuffer)

    for (const item of data) {
      try {
        const archives = await studentArchiveService.getArchivesByStudentId(item.studentDbId)
        
        if (archives && archives.length > 0) {
          const studentFolder = zip.folder(`${item.studentName}_${item.studentId}`)
          
          for (const archive of archives) {
            try {
              const response = await studentArchiveService.downloadArchive(archive.id)
              const blob = new Blob([response], { type: response.type })
              const arrayBuffer = await blob.arrayBuffer()
              studentFolder.file(archive.fileName, arrayBuffer)
            } catch (fileError) {
              console.warn(`下载文件失败: ${archive.fileName}`, fileError)
            }
          }
        }
      } catch (studentError) {
        console.warn(`获取学生档案失败: ${item.studentName}`, studentError)
      }
    }

    const content = await zip.generateAsync({ type: 'blob' })
    const url = window.URL.createObjectURL(content)
    const link = document.createElement('a')
    link.href = url
    link.download = baseFileName + '.zip'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)

    ElMessage.success(`成功导出 ${data.length} 条数据及附件`)
  } catch (error) {
    console.error('导出压缩包失败:', error)
    throw error
  }
}

const getFileIcon = (fileName) => {
  const ext = fileName.split('.').pop().toLowerCase()
  if (['jpg', 'jpeg', 'png', 'gif', 'bmp'].includes(ext)) {
    return Picture
  } else if (['pdf', 'doc', 'docx', 'txt'].includes(ext)) {
    return Document
  } else {
    return Folder
  }
}

const handleDownloadFile = async (archiveId, fileName) => {
  try {
    const response = await studentArchiveService.downloadArchive(archiveId)
    
    const blob = new Blob([response], { type: response.type })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    
    ElMessage.success('下载成功')
  } catch (error) {
    console.error('下载文件失败:', error)
    ElMessage.error('下载失败，请稍后重试')
  }
}
</script>

<template>
  <div class="application-view">
    <div class="page-header">
      <h2>岗位申请查看</h2>
      <p>查看学生提交的岗位申请信息</p>
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
        <el-form-item label="查看状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable style="width: 140px">
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item label="导出格式">
          <el-radio-group v-model="exportMode">
            <el-radio value="excel">仅Excel表格</el-radio>
            <el-radio value="zip">压缩包（含附件）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="success" @click="handleExport" :loading="exporting">
            <el-icon style="margin-right: 4px"><Download /></el-icon>
            {{ exportMode === 'zip' ? '导出压缩包' : '导出Excel' }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="stats-cards">
      <div
        v-for="group in groupedApplications"
        :key="group.position"
        class="stat-card"
        :class="{
          active: searchForm.position === group.position,
          paused: group.status === 'paused',
          'historical': !group.isSystemPosition
        }"
        @click="handlePositionClick(group.position)"
      >
        <div class="stat-header">
          <div class="stat-title">
            {{ group.position }}
            <el-tag v-if="group.status === 'paused'" type="info" size="small" class="pause-tag">
              暂停
            </el-tag>
            <el-tag v-if="!group.isSystemPosition" type="warning" size="small" class="pause-tag">
              历史岗位
            </el-tag>
          </div>
          <div class="stat-total">共 {{ group.total }} 人</div>
        </div>
        <div class="stat-details">
          <div class="stat-item">
            <span class="stat-label">待处理</span>
            <span class="stat-value pending">{{ group.pending }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">已处理</span>
            <span class="stat-value processed">{{ group.processed }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="table-card">
      <el-table
        :data="paginatedTableData"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="positionName" label="应聘岗位" width="180" />
        <el-table-column prop="studentName" label="学生姓名" width="120" />
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="major" label="专业" width="180" />
        <el-table-column prop="phone" label="联系电话" width="140" />
        <el-table-column prop="applyDate" label="申请日期" width="120" />
        <el-table-column prop="status" label="申请状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'pending'" type="warning">待审核</el-tag>
            <el-tag v-else-if="row.status === 'approved'" type="success">已同意</el-tag>
            <el-tag v-else-if="row.status === 'rejected'" type="danger">已拒绝</el-tag>
            <el-tag v-else-if="row.status === 'interview_passed'" type="success">面试通过</el-tag>
            <el-tag v-else-if="row.status === 'interview_failed'" type="danger">面试没通过</el-tag>
            <el-tag v-else type="info">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="220">
          <template #default="{ row }">
            <!-- 待审核状态：显示"同意"按钮 -->
            <el-button
              v-if="row.status === 'pending'"
              type="success"
              size="small"
              @click="handleApprove(row)"
            >
              同意
            </el-button>
            <el-button
              v-if="row.status === 'pending'"
              type="danger"
              size="small"
              @click="handleReject(row)"
            >
              拒绝
            </el-button>

            <!-- 已同意状态：显示"面试通过"和"面试没通过"按钮 -->
            <template v-else-if="row.status === 'approved'">
              <el-button
                type="success"
                size="small"
                @click="handleInterviewPass(row)"
              >
                面试通过
              </el-button>
              <el-button
                type="danger"
                size="small"
                @click="handleInterviewFail(row)"
              >
                面试没通过
              </el-button>
            </template>

            <!-- 其他状态：只显示查看详情 -->
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
    </div>

    <div v-if="paginatedTableData.length === 0" class="empty-state">
      暂无数据
    </div>

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

    <el-dialog
      v-model="detailDialogVisible"
      title="学生岗位申请详情"
      width="700px"
      :close-on-click-modal="false"
    >
      <div v-if="currentStudent" class="student-detail">
        <div class="detail-section">
          <h3 class="section-title">基本信息</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="学号">{{ currentStudent.studentNo }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ currentStudent.studentName }}</el-descriptions-item>
            <el-descriptions-item label="年级">{{ currentStudent.grade }}</el-descriptions-item>
            <el-descriptions-item label="专业">{{ currentStudent.major }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ currentStudent.phone }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section">
          <h3 class="section-title">申请信息</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="应聘岗位">{{ currentStudent.positionName }}</el-descriptions-item>
            <el-descriptions-item label="申请日期">{{ currentStudent.applyDate }}</el-descriptions-item>
            <el-descriptions-item label="申请状态" :span="2">
              <el-tag v-if="currentStudent.status === 'pending'" type="warning">待审核</el-tag>
              <el-tag v-else-if="currentStudent.status === 'approved'" type="success">已通过</el-tag>
              <el-tag v-else-if="currentStudent.status === 'rejected'" type="danger">已拒绝</el-tag>
              <el-tag v-else type="info">{{ currentStudent.status }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section">
          <h3 class="section-title">自我介绍</h3>
          <div class="experience-content">
            {{ currentStudent.selfIntroduction || '暂无' }}
          </div>
        </div>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="detailDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.application-view {
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
  color: white;
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
  grid-template-columns: repeat(5, 1fr);
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

.stat-card:nth-child(5n+1) {
  --card-color-1: #409EFF;
  --card-color-2: #36d1dc;
}

.stat-card:nth-child(5n+2) {
  --card-color-1: #67C23A;
  --card-color-2: #4facfe;
}

.stat-card:nth-child(5n+3) {
  --card-color-1: #E6A23C;
  --card-color-2: #f093fb;
}

.stat-card:nth-child(5n+4) {
  --card-color-1: #F56C6C;
  --card-color-2: #ff9a9e;
}

.stat-card:nth-child(5n+5) {
  --card-color-1: #909399;
  --card-color-2: #b0b0b0;
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

.stat-card.historical {
  background: rgba(230, 162, 60, 0.1);
  border: 1px dashed rgba(230, 162, 60, 0.5);
}

.stat-card.historical::before {
  background: linear-gradient(90deg, #E6A23C, #f5dab1);
}

.stat-card.historical:hover {
  background: rgba(230, 162, 60, 0.2);
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
  grid-template-columns: repeat(2, 1fr);
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
  color: #F56C6C;
}

.stat-value.processed {
  color: #67C23A;
}

.table-card {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.empty-state {
  padding: 40px 20px;
  text-align: center;
  color: #909399;
  font-size: 14px;
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

.skills-container {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.skill-tag {
  margin: 0;
}

.experience-content,
.evaluation-content {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 4px;
  line-height: 1.8;
  color: #606266;
  font-size: 14px;
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
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.files-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.file-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: #f8f9fa;
  border-radius: 8px;
  border: 1px solid #e9ecef;
  transition: all 0.3s;
}

.file-item:hover {
  background: #f0f2f5;
  border-color: #d0d7de;
  transform: translateX(4px);
}

.file-icon {
  font-size: 24px;
  margin-right: 12px;
  color: #409EFF;
}

.file-name {
  flex: 1;
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-actions {
  display: flex;
  gap: 8px;
}

.loading-text {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  color: #909399;
  font-size: 14px;
}

.loading-text .el-icon {
  margin-right: 8px;
}

.empty-text {
  text-align: center;
  padding: 20px;
  color: #909399;
  font-size: 14px;
}

.preview-content {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
  max-height: 70vh;
  overflow: auto;
}

.preview-image {
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
}

.preview-pdf {
  width: 100%;
  height: 70vh;
  border: none;
}

.preview-unsupported {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  color: #909399;
}

.unsupported-icon {
  font-size: 64px;
  margin-bottom: 16px;
  color: #C0C4CC;
}

.preview-unsupported p {
  margin: 8px 0;
  font-size: 14px;
}

:deep(.el-radio__inner) {
  border-color: #409EFF !important;
  background-color: #fff !important;
}

:deep(.el-radio__input.is-checked .el-radio__inner) {
  border-color: #409EFF !important;
  background-color: #409EFF !important;
}

:deep(.el-radio__input.is-checked .el-radio__inner::after) {
  background-color: #fff !important;
}

:deep(.el-radio__inner:hover) {
  border-color: #409EFF !important;
}

:deep(.el-radio__label) {
  font-size: 14px;
  color: #606266;
}

:deep(.el-radio__input.is-checked + .el-radio__label) {
  color: #409EFF !important;
}
</style>
