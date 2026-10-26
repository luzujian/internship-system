<script setup lang="ts">
import { useAgentPageContext } from '@/agent/useAgentPageContext'
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, Download, Folder, Picture } from '@element-plus/icons-vue'
import { createWorkbook, jsonToSheet, appendSheet, writeWorkbook, writeWorkbookToBuffer } from '../../utils/xlsx'
import { usePositionStore } from '../../store/position'
import { useAuthStore } from '@/store/auth'
import applicationApi from '@/api/InternshipApplicationService'
import studentArchiveService from '@/api/StudentArchiveService'
import FilePreviewDialog from '@/components/FilePreviewDialog.vue'
import { companyArchiveByteUrl } from '@/utils/authorizedFileUrl'
import { downloadAuthorizedFile } from '@/utils/authorizedFileDownload'
import { onNewApplication, offNewApplication, onCompanyTodoUpdate, offCompanyTodoUpdate } from '@/utils/websocket'
import { fetchCompanyActivityDetail, fetchActivityForApplication, markCompanyActivityRead,
  type CompanyApplicationActivity } from '@/api/companyApplicationActivities'
import { readCompanyActivity, activityStatusText, activityStatusType } from '@/utils/companyActivityFeed.mjs'
import { useIsMobile } from '@/composables/useIsMobile'

const positionStore = usePositionStore()
const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

// 岗位申请在不同后端版本中岗位名称字段为 position（旧）或 positionName（新），本地行类型兼容两种形态
interface ApplicationViewRow {
  id?: number | string
  position?: string
  positionName?: string
  positionId?: number | string
  status?: string
  rejectReason?: string
  applyDate?: string
  createTime?: string
  studentName?: string
  studentNo?: string
  directHire?: boolean
  major?: string
  phone?: string
  [key: string]: unknown
}
const asApplicationRows = (input: unknown): ApplicationViewRow[] =>
  Array.isArray(input) ? (input as ApplicationViewRow[]) : []

const companyId = computed(() => {
  if (!authStore.token || authStore.role !== 'ROLE_COMPANY') return null
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
const archiveDownloading = ref('')
const filePreviewVisible = ref(false)
const previewFileName = ref('')
const previewAuthorizedUrl = ref('')
const archiveFileTypeText = (type: string) => ({ resume: '简历', certificate: '证书' }[type] || type || '申请材料')
const exportMode = ref('excel')
const isMobile = useIsMobile()
const showMobileFilters = ref(false)

const searchForm = ref({
  keyword: '',
  position: '',
  status: ''
})
const activeFilterCount = computed(() => Number(Boolean(searchForm.value.position)))

const currentApplicationRows = computed(() => {
  const positionsById = new Map(positionStore.positions.map(position => [String(position.id), position]))
  return asApplicationRows(positionStore.jobApplications).flatMap(application => {
    const position = positionsById.get(String(application.positionId))
    return position ? [{ ...application, positionName: position.positionName }] : []
  })
})

const positionOptions = computed(() => {
  // 从岗位申请数据中提取所有出现过的岗位名称，确保包含历史岗位
  const jobApplicationPositions = new Set<string>()
  if (positionStore.jobApplications && Array.isArray(positionStore.jobApplications)) {
    currentApplicationRows.value.forEach(item => {
      if (item.positionName) {
        jobApplicationPositions.add(item.positionName)
      }
    })
  }
  
  // 从当前岗位列表中获取岗位名称
  const currentPositions = positionStore.positions.map(pos => pos.positionName)
  
  // 合并两个列表并去重，按字母顺序排序
  const allPositions = [...new Set([...currentPositions, ...jobApplicationPositions])]
    .filter(name => name) // 过滤空值
    .sort((a, b) => a.localeCompare(b, 'zh-CN'))
  
  return allPositions.map(name => ({ label: name, value: name }))
})

const handleNewApplication = () => {
  console.log('岗位申请查看页面收到新申请通知，静默刷新数据')
  positionStore.fetchJobApplications()
}

onMounted(() => {
  if (!companyId.value) {
    ElMessage.error('未获取到企业 ID，无法加载数据')
    return
  }
  positionStore.fetchPositions(companyId.value)
  positionStore.fetchJobApplications()
  
  onNewApplication(handleNewApplication)
  onCompanyTodoUpdate(handleNewApplication)
})

import { onUnmounted } from 'vue'
onUnmounted(() => {
  offNewApplication(handleNewApplication)
  offCompanyTodoUpdate(handleNewApplication)
  activityRequest++
  detailRequest++
})

const detailDialogVisible = ref(false)
const currentStudent = ref(null)
const activityReadError = ref('')
const activityReadBusy = ref(false)
const activityReadUnavailable = ref(false)
let activityRequest = 0
let detailRequest = 0
const rejectDialogVisible = ref(false)
const rejectSubmitting = ref(false)
const rejectTarget = ref<ApplicationViewRow | null>(null)
const rejectReason = ref('')
const studentArchives = ref([])

const groupedApplications = computed(() => {
  const groups: Record<string, { applications: ApplicationViewRow[]; status?: string | number; isSystemPosition: boolean }> = {}

  // 只从当前岗位列表初始化岗位（不包含历史岗位）
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

  // 遍历申请列表，只将申请分配到当前存在的岗位组
  if (currentApplicationRows.value.length) {
    currentApplicationRows.value.forEach(item => {
      const positionName = item.positionName || ''
      // 只处理当前存在的岗位，忽略历史岗位的申请
      if (positionName && groups[positionName]) {
        groups[positionName].applications.push(item)
      }
    })
  }

  Object.keys(groups).forEach(position => {
    groups[position].applications.sort((a, b) => {
      if (a.status === 'pending' && b.status !== 'pending') return -1
      if (a.status !== 'pending' && b.status === 'pending') return 1
      return new Date(String(b.applyDate ?? '')).getTime() - new Date(String(a.applyDate ?? '')).getTime()
    })
  })

  return Object.entries(groups).map(([position, group]) => ({
    position,
    applications: group.applications,
    total: group.applications.length,
    pending: group.applications.filter(a => a.status === 'pending').length,
    processed: group.applications.filter(a => a.status !== 'pending').length,
    status: group.status,
    isSystemPosition: true
  })).sort((a, b) => {
    return a.position.localeCompare(b.position, 'zh-CN')
  })
})

const filteredTableData = computed(() => {
  let data = [...currentApplicationRows.value]

  if (searchForm.value.keyword) {
    data = data.filter(item =>
      String(item.studentName ?? '').includes(searchForm.value.keyword) ||
      String(item.studentNo ?? '').includes(searchForm.value.keyword)
    )
  }

  if (searchForm.value.position) {
    data = data.filter(item => item.positionName === searchForm.value.position)
  }

  // StudentJobApplication uses 'status' instead of 'viewed'
  // searchForm.status 为字符串形态，与布尔恒不相等（恒假空分支）；保持原语义不断言删除，仅收敛类型
  const statusFilter = searchForm.value.status as unknown
  if (statusFilter === true || statusFilter === false) {
    // No viewed field in StudentJobApplication, so no filtering by viewed status
  }

  data.sort((a, b) => {
    return new Date(String(b.applyDate ?? b.createTime ?? '')).getTime() - new Date(String(a.applyDate ?? a.createTime ?? '')).getTime()
  })

  return data
})

const currentPage = ref(1)
const pageSize = ref(10)
watch([searchForm, pageSize], () => { currentPage.value = 1 }, { deep: true })

const paginatedTableData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredTableData.value.slice(start, end)
})

const handlePositionClick = (position) => {
  searchForm.value.position = searchForm.value.position === position ? '' : position
  currentPage.value = 1
}

const handleSearch = () => {
  currentPage.value = 1
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
    await ElMessageBox.confirm('同意该学生的申请，代表该学生获得面试资格，系统将自动发送面试邀请，是否继续？', '确认同意', {
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

const handleReject = (row) => {
  rejectTarget.value = row
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

const handleRejectCancel = () => {
  if (rejectSubmitting.value) return
  rejectDialogVisible.value = false
  rejectTarget.value = null
  rejectReason.value = ''
}

const handleRejectConfirm = async () => {
  const target = rejectTarget.value
  const reason = rejectReason.value.trim()
  if (!target) return
  const applicationId = Number(target.id)
  if (!Number.isInteger(applicationId) || applicationId <= 0) {
    ElMessage.error('申请编号无效')
    return
  }
  if (!reason) {
    ElMessage.warning('请填写拒绝理由')
    return
  }
  if (reason.length > 500) {
    ElMessage.warning('拒绝理由不能超过500个字')
    return
  }

  rejectSubmitting.value = true
  try {
    const response = await applicationApi.updateJobApplicationStatus(applicationId, 'rejected', reason)
    if (response.code === 200) {
      ElMessage.success('已拒绝该学生的申请，并通知学生')
      rejectDialogVisible.value = false
      rejectTarget.value = null
      rejectReason.value = ''
      await positionStore.fetchJobApplications()
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    console.error('拒绝申请失败:', error)
    ElMessage.error('操作失败')
  } finally {
    rejectSubmitting.value = false
  }
}

const handleDirectHire = async (row) => {
  try {
    await ElMessageBox.confirm(
      '此操作将跳过面试流程，并将该学生标记为面试通过。学生随后可以填写并提交实习确认表，企业确认实习表后才正式获得该岗位。本操作暂不支持撤销。',
      '确认直接录取该学生？',
      {
        confirmButtonText: '确认直接录取',
        cancelButtonText: '返回检查',
        type: 'warning',
        distinguishCancelAndClose: true
      }
    )

    const response = await applicationApi.directHire(row.id)
    if (response.code === 200) {
      ElMessage.success('已直接录取该学生，等待学生提交实习确认表')
      await positionStore.fetchJobApplications()
    } else {
      ElMessage.error(response.message || '直接录取失败')
    }
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('直接录取失败')
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
  const requestId = ++detailRequest
  currentStudent.value = row
  activityReadError.value = ''
  activityReadUnavailable.value = false
  studentArchives.value = []
  detailDialogVisible.value = true
  const isCurrentStudent = () => (
    requestId === detailRequest &&
    String(currentStudent.value?.id) === String(row.id)
    && String(currentStudent.value?.studentId) === String(row.studentId)
  )

  void acknowledgeActivity(row, requestId)

  // 获取学生申请材料
  if (row.studentId && !row.historical) {
    try {
      interface StudentArchiveRow {
        id?: number | string
        fileName?: string
        [key: string]: unknown
      }
      const response = await studentArchiveService.getStudentArchives(row.studentId) as unknown as {
        code?: number
        data?: StudentArchiveRow[]
      }
      if (!isCurrentStudent()) return
      if (response.code === 200) {
        studentArchives.value = Array.isArray(response.data) ? response.data : []
      } else {
        studentArchives.value = []
      }
    } catch (error) {
      console.error('获取学生材料失败:', error)
      if (isCurrentStudent()) studentArchives.value = []
    }
  }

  console.log('查看学生详情:', row)
}

const acknowledgeActivity = async (row, requestId: number) => {
  activityReadBusy.value = true
  try {
    const activity: CompanyApplicationActivity | null = row.activity || await fetchActivityForApplication(Number(row.id))
    if (requestId !== detailRequest) return
    if (!activity) {
      activityReadUnavailable.value = true
      activityReadError.value = '这条申请暂时无法同步动态已读状态，请从首页打开对应动态。'
      return
    }
    row.activity = activity
    if (activity.status !== row.status) throw new Error('申请状态已更新，请重新打开详情')
    await readCompanyActivity(activity, markCompanyActivityRead)
  } catch (error) {
    if (requestId === detailRequest) activityReadError.value = '详情已打开，已读状态保存失败，请重新读取后重试。'
  } finally {
    if (requestId === detailRequest) activityReadBusy.value = false
  }
}

const openActivityDetail = async (value) => {
  if (!value) return
  const id = Number(value)
  if (!Number.isSafeInteger(id) || id <= 0) { ElMessage.error('动态记录编号无效'); return }
  const requestId = ++activityRequest
  try {
    const detail = await fetchCompanyActivityDetail(id)
    if (requestId !== activityRequest) return
    const snapshot = detail.snapshot
    const row: ApplicationViewRow = detail.application ? { ...detail.application, applyDate: detail.application.applyDate || detail.application.createTime }
      : { ...snapshot, id: `activity-${detail.activity.id}`, studentNo: snapshot.studentUserId,
          applyDate: snapshot.applyTime || snapshot.createTime, selfIntroduction: snapshot.selfEvaluation, historical: true }
    if (row.applyDate) row.applyDate = new Date(row.applyDate).toLocaleDateString('zh-CN')
    if (detail.application) {
      searchForm.value.keyword = row.studentNo || row.studentName || ''
      searchForm.value.position = row.positionName || ''
      currentPage.value = 1
    }
    await handleViewDetail({ ...row, activity: detail.activity })
  } catch {
    if (requestId === activityRequest) ElMessage.error('动态详情加载失败，请返回首页重试')
  }
}

const retryActivityRead = () => {
  if (currentStudent.value?.activity) return openActivityDetail(currentStudent.value.activity.id)
  if (currentStudent.value) return handleViewDetail(currentStudent.value)
}

const closeActivityDetail = () => {
  filePreviewVisible.value = false
  detailRequest++
  activityRequest++
  if (route.query.activityId) {
    const query = { ...route.query }
    delete query.activityId
    void router.replace({ query })
  }
}

watch(() => route.query.activityId, openActivityDetail, { immediate: true })

// 下载申请材料
const handleDownloadArchive = async (archive) => {
  if (archiveDownloading.value) return
  const authorizedUrl = companyArchiveByteUrl(archive.id, 'download')
  if (!authorizedUrl) {
    ElMessage.error('材料记录不可用，请重新打开申请详情')
    return
  }
  archiveDownloading.value = String(archive.id)
  try {
    await downloadAuthorizedFile(authorizedUrl, archive.fileName || '申请材料')
    ElMessage.success('开始下载')
  } catch (error) {
    console.error('下载失败:', error)
    ElMessage.error('下载失败，请稍后重试')
  } finally {
    archiveDownloading.value = ''
  }
}

// 预览申请材料
const handlePreviewArchive = (archive) => {
  const authorizedUrl = companyArchiveByteUrl(archive.id)
  if (!authorizedUrl) {
    ElMessage.error('材料记录不可用，请重新打开申请详情')
    return
  }
  previewFileName.value = archive.fileName || '申请材料'
  previewAuthorizedUrl.value = authorizedUrl
  filePreviewVisible.value = true
}

const handleExport = async () => {
  let dataToExport = [...currentApplicationRows.value]

  if (searchForm.value.keyword) {
    dataToExport = dataToExport.filter(item =>
      String(item.studentName ?? '').includes(searchForm.value.keyword) ||
      String(item.studentNo ?? '').includes(searchForm.value.keyword)
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
        return new Date(String(b.applyDate ?? b.createTime ?? '')).getTime() - new Date(String(a.applyDate ?? a.createTime ?? '')).getTime()
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
      '申请状态': item.status === 'pending' ? '待审核' : item.status === 'approved' ? '已同意' : item.status === 'rejected' ? '已拒绝' : item.status === 'interview_passed' && item.directHire ? '已录取，待提交确认表' : item.status === 'interview_passed' ? '面试通过' : item.status === 'interview_failed' ? '面试没通过' : item.status === 'withdrawn' ? '已撤回' : item.status === 'defaulted' ? '已失效' : '未知状态',
      '拒绝理由': item.rejectReason || '',
      '自我介绍': item.selfIntroduction
    }))

    const ws = await jsonToSheet(exportData)
    const wb = await createWorkbook()
    await appendSheet(wb, ws, '岗位申请名单')

    const fileName = `岗位申请名单_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}`

    if (exportMode.value === 'zip') {
      await exportWithFiles(wb, fileName, sortedData)
    } else {
      await writeWorkbook(wb, fileName)
      ElMessage.success(`成功导出 ${dataToExport.length} 条数据`)
    }
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
    const failures: string[] = []

    const excelBuffer = await writeWorkbookToBuffer(workbook)
    zip.file(baseFileName + '.xlsx', excelBuffer)

    for (const item of data) {
      try {
        const response = await studentArchiveService.getStudentArchives(item.studentId)
        const archives = response?.data || response

        if (archives && Array.isArray(archives) && archives.length > 0) {
          const studentFolder = zip.folder(`${item.studentName}_${item.studentId}`)
          
          for (const archive of archives) {
            try {
              const response = await studentArchiveService.downloadArchive(archive.id) as unknown as Blob
              const blob = new Blob([response], { type: response.type })
              const arrayBuffer = await blob.arrayBuffer()
              studentFolder.file(archive.fileName, arrayBuffer)
            } catch (fileError) {
              console.warn(`下载文件失败: ${archive.fileName}`, fileError)
              failures.push(`${item.studentName}/${archive.fileName}：文件下载失败`)
            }
          }
        }
      } catch (studentError) {
        console.warn(`获取学生档案失败: ${item.studentName}`, studentError)
        failures.push(`${item.studentName}：无法读取申请材料列表`)
      }
    }

    if (failures.length) zip.file('_导出失败清单.txt', failures.join('\n'))
    const content = await zip.generateAsync({ type: 'blob' })
    const url = window.URL.createObjectURL(content)
    const link = document.createElement('a')
    link.href = url
    link.download = baseFileName + '.zip'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)

    if (failures.length) ElMessage.warning(`名单已导出，${failures.length} 项附件读取失败，请查看压缩包内失败清单`)
    else ElMessage.success(`成功导出 ${data.length} 条数据及附件`)
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
    const response = await studentArchiveService.downloadArchive(archiveId) as unknown as Blob
    
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

useAgentPageContext(() => {
  const positions = positionStore.positions.filter(position => position.positionName === searchForm.value.position)
  return {
  title: '申请查看',
  view: detailDialogVisible.value ? 'application-detail' : 'page',
  object: (detailDialogVisible.value && !currentStudent.value?.historical && (currentStudent.value as any)?.id) ? { type: 'company_application', id: String((currentStudent.value as any).id) } : null,
  filters: { keyword: searchForm.value.keyword, status: searchForm.value.status,
    positionId: positions.length === 1 ? String(positions[0].id) : '',
    visibleIds: paginatedTableData.value.slice(0, 20).map(row => String(row.id)) }
  }
})
</script>

<template>
  <div class="application-view">
    <div class="page-header">
      <h2>岗位申请查看</h2>
      <p>查看学生提交的岗位申请信息</p>
    </div>

    <div class="search-card">
      <div class="search-section">
        <div class="section-title">岗位申请查询</div>
        <el-form :inline="true" :model="searchForm" class="search-form">
          <el-form-item label="关键词">
            <el-input 
              v-model="searchForm.keyword" 
              placeholder="请输入学生姓名或学号" 
              clearable
              style="width: 200px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item v-show="!isMobile || showMobileFilters" label="应聘岗位">
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
            <el-button v-if="isMobile" :aria-expanded="showMobileFilters" @click="showMobileFilters = !showMobileFilters">{{ showMobileFilters ? '收起筛选' : '筛选' }}{{ activeFilterCount ? `（${activeFilterCount}）` : '' }}</el-button>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
      <div class="search-divider"></div>
      <div class="search-section">
        <div class="section-title">数据导出</div>
        <div class="export-actions">
          <el-form :inline="true" class="export-form">
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
      </div>
    </div>

    <div class="stats-cards">
      <div
        v-for="group in groupedApplications"
        :key="group.position"
        class="stat-card"
        :class="{
          active: searchForm.position === group.position,
          paused: group.status === 'paused'
        }"
        @click="handlePositionClick(group.position)"
      >
        <div class="stat-header">
          <div class="stat-title">
            {{ group.position }}
            <el-tag v-if="group.status === 'paused'" type="info" size="small" class="pause-tag">
              暂停
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
      <el-alert
        title="提示：可先同意面试后处理面试结果，也可使用“直接录取”跳过面试流程"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 12px;"
      />
      <el-table
        v-if="!isMobile"
        :data="paginatedTableData"
        border
        style="width: 100%"
        :header-cell-style="{ padding: '8px 4px', fontSize: '13px' }"
        :cell-style="{ padding: '6px 4px', fontSize: '12px' }"
      >
        <el-table-column prop="positionName" label="应聘岗位" min-width="150" />
        <el-table-column prop="studentName" label="学生姓名" width="90" />
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="major" label="专业" min-width="150" />
        <el-table-column prop="phone" label="联系电话" width="130" />
        <el-table-column prop="applyDate" label="申请日期" width="110" />
        <el-table-column prop="status" label="申请状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'pending'" type="warning" size="small">待审核</el-tag>
            <el-tag v-else-if="row.status === 'approved'" type="success" size="small">已同意</el-tag>
            <el-tag v-else-if="row.status === 'rejected'" type="danger" size="small">已拒绝</el-tag>
            <el-tag v-else-if="row.status === 'interview_passed' && row.directHire" type="success" size="small">已录取，待提交确认表</el-tag>
            <el-tag v-else-if="row.status === 'interview_passed'" type="success" size="small">面试通过</el-tag>
            <el-tag v-else-if="row.status === 'interview_failed'" type="danger" size="small">面试没通过</el-tag>
            <el-tag v-else-if="row.status === 'withdrawn'" type="info" size="small">已撤回</el-tag>
            <el-tag v-else-if="row.status === 'defaulted'" type="info" size="small">已失效</el-tag>
            <el-tag v-else type="info" size="small">未知状态</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="430" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'pending'"
              type="success"
              size="small"
              @click="handleApprove(row)"
            >
              同意面试
            </el-button>
            <el-button
              v-if="row.status === 'pending'"
              type="danger"
              size="small"
              @click="handleReject(row)"
            >
              拒绝申请
            </el-button>
            <el-tooltip
              v-if="row.status === 'pending'"
              content="跳过面试流程，直接允许学生提交实习确认表"
              placement="top"
            >
              <el-button
                type="warning"
                size="small"
                @click="handleDirectHire(row)"
              >
                直接录取
              </el-button>
            </el-tooltip>

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

      <div v-else class="mobile-card-list" aria-label="岗位申请列表">
        <el-empty v-if="!paginatedTableData.length" description="暂无符合条件的申请" />
        <article v-for="row in paginatedTableData" :key="row.id" class="mobile-card">
          <div class="mobile-card-header">
            <span class="mobile-card-title">{{ row.studentName || '未填写姓名' }}</span>
            <el-tag v-if="row.status === 'pending'" type="warning">待审核</el-tag>
            <el-tag v-else-if="row.status === 'approved'" type="success">已同意</el-tag>
            <el-tag v-else-if="row.status === 'rejected'" type="danger">已拒绝</el-tag>
            <el-tag v-else-if="row.status === 'interview_passed' && row.directHire" type="success">已录取，待提交确认表</el-tag>
            <el-tag v-else-if="row.status === 'interview_passed'" type="success">面试通过</el-tag>
            <el-tag v-else-if="row.status === 'interview_failed'" type="danger">面试没通过</el-tag>
            <el-tag v-else-if="row.status === 'withdrawn'" type="info">已撤回</el-tag>
            <el-tag v-else-if="row.status === 'defaulted'" type="info">已失效</el-tag>
            <el-tag v-else type="info">未知状态</el-tag>
          </div>
          <div class="mobile-card-body">
            <div class="mobile-card-row"><span class="mobile-card-label">应聘岗位</span><span class="mobile-card-value">{{ row.positionName || '未填写' }}</span></div>
            <div class="mobile-card-row"><span class="mobile-card-label">学号</span><span class="mobile-card-value">{{ row.studentNo || '未填写' }}</span></div>
            <div class="mobile-card-row"><span class="mobile-card-label">专业</span><span class="mobile-card-value">{{ row.major || '未填写' }}</span></div>
            <div v-if="row.phone" class="mobile-card-row"><span class="mobile-card-label">联系电话</span><a class="mobile-card-value mobile-contact-link" :href="`tel:${row.phone}`">{{ row.phone }}</a></div>
            <div class="mobile-card-row"><span class="mobile-card-label">申请日期</span><span class="mobile-card-value">{{ row.applyDate || '未填写' }}</span></div>
          </div>
          <div class="mobile-card-actions">
            <el-button type="primary" @click="handleViewDetail(row)">查看详情</el-button>
            <template v-if="row.status === 'pending'">
              <el-button type="success" @click="handleApprove(row)">同意面试</el-button>
              <el-button type="danger" @click="handleReject(row)">拒绝申请</el-button>
              <el-button type="warning" @click="handleDirectHire(row)">直接录取</el-button>
            </template>
            <template v-else-if="row.status === 'approved'">
              <el-button type="success" @click="handleInterviewPass(row)">面试通过</el-button>
              <el-button type="danger" @click="handleInterviewFail(row)">面试没通过</el-button>
            </template>
          </div>
        </article>
      </div>

      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          background
          :layout="isMobile ? 'total, sizes, prev, next' : 'total, sizes, prev, pager, next, jumper'"
          :total="filteredTableData.length"
          :page-sizes="[10, 20, 50, 100]"
        />
      </div>
    </div>

    <div v-if="!isMobile && paginatedTableData.length === 0" class="empty-state">
      暂无数据
    </div>

    <el-dialog
      v-model="rejectDialogVisible"
      title="填写拒绝理由"
      width="520px"
      :close-on-click-modal="false"
      :close-on-press-escape="!rejectSubmitting"
      @closed="handleRejectCancel"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="拒绝理由将随结果邮件发送给学生"
        style="margin-bottom: 16px;"
      />
      <el-form label-position="top">
        <el-form-item label="学生">
          <el-input :model-value="rejectTarget?.studentName || ''" disabled />
        </el-form-item>
        <el-form-item label="拒绝理由" required>
          <el-input
            v-model="rejectReason"
            type="textarea"
            :rows="5"
            maxlength="500"
            show-word-limit
            placeholder="请说明本次申请未通过的原因，例如：专业方向与岗位要求不匹配"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleRejectCancel" :disabled="rejectSubmitting">取消</el-button>
        <el-button
          type="danger"
          @click="handleRejectConfirm"
          :loading="rejectSubmitting"
        >
          确认拒绝并通知学生
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailDialogVisible"
      title="学生岗位申请详情"
      width="700px"
      :close-on-click-modal="false"
      @close="closeActivityDetail"
    >
      <div v-if="currentStudent" class="student-detail">
        <el-alert v-if="currentStudent.historical" type="info" :closable="false"
          title="显示该次申请的历史记录，当前可处理的申请以列表为准。" style="margin-bottom: 16px" />
        <el-alert v-if="activityReadError" type="warning" :closable="false" style="margin-bottom: 16px">
          <template #title>{{ activityReadError }}</template>
          <el-button v-if="activityReadUnavailable" link type="primary" @click="router.push('/company/dashboard')">返回首页</el-button>
          <el-button v-else link type="primary" :loading="activityReadBusy" @click="retryActivityRead">重新读取</el-button>
        </el-alert>
        <div class="detail-section">
          <h3 class="section-title">基本信息</h3>
          <el-descriptions :column="isMobile ? 1 : 2" border>
            <el-descriptions-item label="学号">{{ currentStudent.studentNo }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ currentStudent.studentName }}</el-descriptions-item>
            <el-descriptions-item label="年级">{{ currentStudent.grade }}</el-descriptions-item>
            <el-descriptions-item label="专业">{{ currentStudent.major }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ currentStudent.phone }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ currentStudent.email || '未填写' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section">
          <h3 class="section-title">申请信息</h3>
          <el-descriptions :column="isMobile ? 1 : 2" border>
            <el-descriptions-item label="应聘岗位">{{ currentStudent.positionName }}</el-descriptions-item>
            <el-descriptions-item label="申请日期">{{ currentStudent.applyDate }}</el-descriptions-item>
            <el-descriptions-item
              v-if="currentStudent.status === 'rejected' && currentStudent.rejectReason"
              label="拒绝理由"
              :span="isMobile ? 1 : 2"
            >
              {{ currentStudent.rejectReason }}
            </el-descriptions-item>
            <el-descriptions-item label="申请状态" :span="isMobile ? 1 : 2">
              <el-tag :type="activityStatusType(currentStudent.status)">{{ activityStatusText(currentStudent.status) }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section">
          <h3 class="section-title">自我介绍</h3>
          <div class="experience-content">
            {{ currentStudent.selfIntroduction || '暂无' }}
          </div>
        </div>

        <!-- 申请材料区域 -->
        <div v-if="!currentStudent.historical" class="detail-section">
          <h3 class="section-title">申请材料</h3>
          <div v-if="studentArchives.length > 0" class="archives-list">
            <div v-for="archive in studentArchives" :key="archive.id" class="archive-item">
              <div class="archive-info">
                <el-icon class="archive-icon"><Document /></el-icon>
                <span class="archive-name">{{ archive.fileName }}</span>
                <el-tag size="small" type="info">{{ archiveFileTypeText(archive.fileType) }}</el-tag>
              </div>
              <div class="archive-actions">
                <el-button size="small" type="primary" link @click="handlePreviewArchive(archive)">预览</el-button>
                <el-button size="small" type="primary" link :loading="archiveDownloading === String(archive.id)" :disabled="Boolean(archiveDownloading)" @click="handleDownloadArchive(archive)">下载</el-button>
              </div>
            </div>
          </div>
          <div v-else class="no-archives">
            <el-empty description="暂无申请材料" :image-size="60" />
          </div>
        </div>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="detailDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>

    <FilePreviewDialog
      v-model="filePreviewVisible"
      file-url=""
      :file-name="previewFileName"
      :authorized-url="previewAuthorizedUrl"
    />
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
  padding: 20px 24px;
  border-radius: 8px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  display: flex;
  align-items: stretch;
  gap: 24px;
}

.search-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.search-section:first-child {
  flex: 1;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  padding-left: 12px;
  border-left: 3px solid #409EFF;
}

.search-section:last-child .section-title {
  border-left-color: #67c23a;
}

.search-divider {
  width: 1px;
  background: #e4e7ed;
}

.export-actions {
  display: flex;
  align-items: center;
  flex: 1;
}

.export-form {
  display: flex;
  align-items: center;
}

.search-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.search-card :deep(.el-form-item) {
  margin-bottom: 0;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}

.stat-card {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  padding: 10px 12px;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06),
              0 4px 8px rgba(0, 0, 0, 0.08);
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
  height: 3px;
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
  border: 2px solid #409EFF;
  background: linear-gradient(135deg, #ecf5ff 0%, #d9ecff 100%);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

.stat-card.active::before {
  background: linear-gradient(90deg, #409EFF, #66b1ff);
  opacity: 1;
  height: 5px;
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
  margin-bottom: 6px;
  padding-bottom: 6px;
  border-bottom: 1px solid #e8e8e8;
}

.stat-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.stat-total {
  font-size: 13px;
  color: #909399;
}

.stat-details {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.stat-label {
  font-size: 12px;
  color: #909399;
}

.stat-value {
  font-size: 18px;
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
  padding: 12px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.empty-state {
  padding: 24px 16px;
  text-align: center;
  color: #909399;
  font-size: 14px;
}

:deep(.el-button--small) {
  padding: 4px 10px;
  font-size: 12px;
}

.student-detail {
  max-height: none;
  overflow-y: visible;
}

.detail-section {
  margin-bottom: 24px;
}

.student-detail .section-title {
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

/* 申请材料样式 */
.archives-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.archive-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}

.archive-item:hover {
  background: #ecf5ff;
  border-color: #409EFF;
}

.archive-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.archive-icon {
  font-size: 20px;
  color: #409EFF;
}

.archive-name {
  color: #303133;
  font-size: 14px;
}

.archive-actions {
  display: flex;
  gap: 12px;
}

.no-archives {
  padding: 20px 0;
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
  margin-top: 12px;
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

@media (max-width: 768px) {
  .page-header {
    padding: 18px 20px;
    margin-bottom: 16px;
  }

  .page-header h2 {
    font-size: 22px;
  }

  .search-card {
    flex-direction: column;
    gap: 16px;
    padding: 16px 18px;
  }

  .search-section:first-child {
    flex: none;
  }

  .search-divider {
    width: 100%;
    height: 1px;
  }

  .search-form :deep(.el-form-item),
  .export-form :deep(.el-form-item) {
    width: 100%;
    margin-right: 0;
  }

  .search-form :deep(.el-input),
  .search-form :deep(.el-select) {
    width: 100% !important;
  }

  .search-form :deep(.el-form-item__content) {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    min-width: 0;
  }

  .search-form :deep(.el-button) {
    flex: 1;
    margin-left: 0;
    min-height: 44px;
  }

  .mobile-card-header :deep(.el-tag) {
    height: auto;
    min-height: 24px;
    max-width: 65%;
    padding: 4px 8px;
    white-space: normal;
    flex-shrink: 0;
  }

  .mobile-contact-link {
    display: inline-flex;
    align-items: center;
    min-height: 44px;
    color: #409eff;
  }

  .file-item {
    flex-wrap: wrap;
    gap: 8px;
    padding: 12px;
  }

  .file-name {
    min-width: 0;
    white-space: normal;
    overflow-wrap: anywhere;
  }

  .file-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .file-actions :deep(.el-button) {
    flex: 1;
    margin-left: 0;
    min-height: 44px;
  }

  .export-actions,
  .export-form {
    flex-direction: column;
    align-items: stretch;
  }

  .export-form :deep(.el-radio-group) {
    flex-wrap: wrap;
  }

  .export-form :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }

  .stats-cards {
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
  }

  .stat-card {
    padding: 12px;
  }

  .stat-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .table-card {
    padding: 12px;
  }
}
</style>
