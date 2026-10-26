<script setup lang="ts">
import { useAgentPageContext } from '@/agent/useAgentPageContext'
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { usePositionStore } from '../../store/position'
import { useAuthStore } from '@/store/auth'
import internshipStatusApi from '../../api/InternshipStatusService'
import { exportToExcel } from '../../utils/xlsx'
import emitter from '@/utils/event-bus'
import { onCompanyTodoUpdate, offCompanyTodoUpdate } from '@/utils/websocket'
import InternshipAgreementPreview from '@/components/student/InternshipAgreementPreview.vue'
import { blobRequest, saveAgreementBlob, type AgreementFile, type AgreementVersion } from '@/api/internshipAgreements'
import type { CompanyTodoUpdateData } from '@/utils/websocket'
import { useIsMobile } from '@/composables/useIsMobile'

const positionStore = usePositionStore()
const authStore = useAuthStore()
const isMobile = useIsMobile()

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
const confirmationRecords = ref<any[]>([])

const loadConfirmationRecords = async () => {
  if (!companyId.value) return
  const response: any = await internshipStatusApi.getConfirmationRecords(companyId.value)
  confirmationRecords.value = Array.isArray(response?.data)
    ? response.data.map((item: any) => ({
        ...item,
        // 企业确认页以确认记录为唯一主数据，id 即 confirmation record id。
        companyConfirmStatus: Number(item.status),
        majorName: item.majorName || item.major || ''
      }))
    : []
}

const searchForm = ref({
  keyword: '',
  status: '',
  position: ''
})
const showMobileFilters = ref(false)
const activeFilterCount = computed(() => [searchForm.value.status, searchForm.value.position].filter(Boolean).length)

onMounted(() => {
  if (!companyId.value) {
    ElMessage.error('未获取到企业 ID，无法加载数据')
    return
  }
  positionStore.fetchPositions(companyId.value)
  loadConfirmationRecords().catch(error => {
    console.error('加载实习确认记录失败:', error)
    ElMessage.error(error?.message || '加载实习确认记录失败')
  })
  // 注册企业待办更新监听器
  onCompanyTodoUpdate(handleCompanyTodoUpdate)
})

onUnmounted(() => {
  offCompanyTodoUpdate(handleCompanyTodoUpdate)
})

// 处理WebSocket企业待办数据更新（静默刷新统计数据）
const handleCompanyTodoUpdate = (data: CompanyTodoUpdateData) => {
  console.log('实习确认页面收到待办数据更新:', data)
  if (companyId.value) {
    positionStore.fetchPositions(companyId.value)
    loadConfirmationRecords().catch(error => console.error('刷新实习确认记录失败:', error))
  }
}

const detailDialogVisible = ref(false)
const currentStudent = ref<any>(null)
const detailApplication = ref<any>(null)
const detailContact = ref<{ name?: string; phone?: string } | null>(null)
const detailAgreements = ref<AgreementVersion[]>([])
const detailLoading = ref(false)
let detailToken = 0
const filePreview = ref<InstanceType<typeof InternshipAgreementPreview> | null>(null)
const previewRecordId = ref<number>(0)
const readCompanyAgreement = (fileId: number, preview: boolean) => blobRequest(`/company/confirmation/${previewRecordId.value}/agreements/${fileId}/${preview ? 'preview' : 'download'}`)
const previewAgreement = (file: AgreementFile) => { previewRecordId.value = currentStudent.value.id; filePreview.value?.show(file) }
const downloadAgreement = async (file: AgreementFile) => {
  try { saveAgreementBlob(await blobRequest(`/company/confirmation/${currentStudent.value.id}/agreements/${file.id}/download`), file.fileName) }
  catch (error: any) { ElMessage.error(error?.message || '文件读取失败') }
}
const downloadApplicationMaterial = async (name: string) => {
  try { saveAgreementBlob(await blobRequest(`/company/confirmation/${currentStudent.value.id}/materials/${encodeURIComponent(name)}/download`), `${name}.${String(detailApplication.value.materials[name]).split('.').pop()}`) }
  catch (error: any) { ElMessage.error(error?.message || '材料读取失败') }
}
const confirmationActionVisible = ref(false)
const confirmationActionRecord = ref<any>(null)
const confirmationMode = ref<'TEXT' | 'EXISTING'>('TEXT')
const confirmationPositionId = ref<number | null>(null)
const confirmationSubmitting = ref(false)
const openPositionManagement = () => window.open('/company/jobs', '_blank', 'noopener')
const refreshConfirmationPositions = () => positionStore.fetchPositions(companyId.value)
const handleConfirm = (row: any) => {
  if (row.positionId || !row.sourceApplicationId) { void performConfirm(row); return }
  confirmationActionRecord.value = row
  confirmationMode.value = 'TEXT'
  confirmationPositionId.value = null
  confirmationActionVisible.value = true
}
const submitConfirmationAction = async () => {
  if (confirmationMode.value === 'EXISTING' && !confirmationPositionId.value) { ElMessage.warning('请选择已发布的岗位'); return }
  confirmationSubmitting.value = true
  try {
    if (await performConfirm(confirmationActionRecord.value, confirmationMode.value === 'EXISTING' ? confirmationPositionId.value : null, confirmationMode.value === 'TEXT')) confirmationActionVisible.value = false
  } finally { confirmationSubmitting.value = false }
}

const statusMap = {
  0: '待企业确认',
  1: '企业已确认',
  2: '企业已拒绝'
}

const statusTypeMap = {
  0: 'warning',
  1: 'success',
  2: 'danger'
}

const companyConfirmStatusMap = {
  0: '待企业确认',
  1: '企业已确认',
  2: '企业已拒绝'
}

const positionOptions = computed(() => {
  return [...new Set(positionStore.positions.map(pos => pos.positionName).filter(Boolean))]
    .map(name => ({ label: name, value: name }))
})

const positionStats = computed(() => {
  interface PositionStatRow {
    position: string
    total: number
    pending: number
    confirmed: number
    rejected: number
    status?: string | number
  }
  const stats: Record<string, PositionStatRow> = {}
  
  // 只从当前岗位列表初始化岗位（不包含历史岗位）
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
  
  // 遍历实习状态数据，只统计当前存在的岗位
  if (Array.isArray(confirmationRecords.value)) {
    confirmationRecords.value.forEach(item => {
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
  
  // 按岗位名称字母顺序固定排序
  return Object.values(stats).sort((a, b) => {
    return a.position.localeCompare(b.position, 'zh-CN')
  })
})

const currentPage = ref(1)
const pageSize = ref(10)
watch([searchForm, pageSize], () => { currentPage.value = 1 }, { deep: true })

const filteredTableData = computed(() => {
  if (!Array.isArray(confirmationRecords.value)) {
    return []
  }
  
  let result = [...confirmationRecords.value]
  
  if (searchForm.value.keyword) {
    const keyword = searchForm.value.keyword.toLowerCase()
    result = result.filter(item => 
      String(item.studentName ?? '').toLowerCase().includes(keyword) ||
      String(item.studentUserId ?? '').toLowerCase().includes(keyword)
    )
  }
  
  if (searchForm.value.position) {
    result = result.filter(item => item.positionName === searchForm.value.position)
  }
  
  if (searchForm.value.status !== '') {
    result = result.filter(item => item.companyConfirmStatus === parseInt(searchForm.value.status))
  }
  
  result.sort((a, b) => {
    const statusOrder: Record<number, number> = { 0: 0, 1: 1, 2: 2 }
    const statusA = typeof a.companyConfirmStatus === 'number' ? a.companyConfirmStatus : Number(a.companyConfirmStatus)
    const statusB = typeof b.companyConfirmStatus === 'number' ? b.companyConfirmStatus : Number(b.companyConfirmStatus)
    const statusDiff = (statusOrder[statusA] ?? 99) - (statusOrder[statusB] ?? 99)
    if (statusDiff !== 0) return statusDiff
    
    return new Date(String(b.createTime ?? '')).getTime() - new Date(String(a.createTime ?? '')).getTime()
  })
  
  return result
})

const paginatedTableData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredTableData.value.slice(start, end)
})

const handleSearch = () => {
  currentPage.value = 1
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

const performConfirm = async (row: any, positionId: number | null = row.positionId, textPosition = false) => {
  try {
    const position = positionStore.positions.find(p => p.id === positionId)
    const isFull = position && position.remainingQuota <= 0

    if (isFull) {
      ElMessage.warning('该岗位当前没有可确认名额，请先调整计划招聘人数或等待名额释放')
      return
    }

    await ElMessageBox.confirm(
      `确认接收 ${row.studentName}(${row.studentUserId}) 到“${position?.positionName || row.positionName}”实习吗？${positionId ? '确认后占用该岗位名额。' : '确认后加入本企业实习名单。'}`,
      '实习确认',
      {
        confirmButtonText: '确认接收',
        cancelButtonText: '取消',
        type: 'warning',
        center: true,
        distinguishCancelAndClose: true
      }
    )

    await internshipStatusApi.confirmRecord(row.id, { positionId, textPosition })
    await positionStore.fetchPositions(companyId.value)
    await loadConfirmationRecords()
    ElMessage.success(`已确认 ${row.studentName} 的实习申请`)
    
    console.log('触发通知刷新事件')
    emitter.emit('notification-refresh')
    return true
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.message || '操作失败，请稍后重试')
    }
  }
}

const handleReject = async (row) => {
  try {
    // 使用输入框让用户填写拒绝原因
    const { value } = await ElMessageBox.prompt(
      `请输入拒绝 ${row.studentName}(${row.studentUserId}) 的原因：`,
      '拒绝申请',
      {
        confirmButtonText: '确认拒绝',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '请输入拒绝原因（必填）',
        center: true,
        distinguishCancelAndClose: true
      }
    )

    if (!value || !value.trim()) {
      ElMessage.warning('请输入拒绝原因')
      return
    }

    await internshipStatusApi.rejectRecord(row.id, value.trim())
    await loadConfirmationRecords()
    ElMessage.success(`已拒绝 ${row.studentName} 的实习确认申请`)
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.message || '操作失败，请稍后重试')
    }
  }
}

const handleViewDetail = async (row: any) => {
  const token = ++detailToken
  currentStudent.value = row
  detailApplication.value = null
  detailContact.value = null
  detailAgreements.value = []
  detailDialogVisible.value = true
  detailLoading.value = true
  try {
    const response: any = await internshipStatusApi.getConfirmationDetails(row.id)
    if (token !== detailToken) return
    detailApplication.value = response.data?.application || null
    detailContact.value = response.data?.internshipContact || null
    detailAgreements.value = response.data?.agreementVersions || []
  } catch (error: any) { if (token === detailToken) ElMessage.error(error?.message || '申请材料加载失败') }
  finally { if (token === detailToken) detailLoading.value = false }
}

const formatDate = (date) => {
  if (!date) return ''
  const d = new Date(date)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const formatGender = (gender) => {
  if (!gender) return '-'
  if (gender === 1 || gender === '1' || gender === '男') return '男'
  if (gender === 0 || gender === '0' || gender === '女') return '女'
  return gender
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
  
  const diffTime = Math.abs(end.getTime() - start.getTime())
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
        return String(a.positionName ?? '').localeCompare(String(b.positionName ?? ''), 'zh-CN')
      }
      return new Date(String(b.createTime ?? '')).getTime() - new Date(String(a.createTime ?? '')).getTime()
    })

    const exportData = sortedData.map(item => ({
      '应聘岗位': item.positionName,
      '来源': item.sourceApplicationId ? '自主实习申请' : '平台录取',
      '学生姓名': item.studentName,
      '学号': item.studentUserId,
      '年级': item.grade,
      '专业': item.majorName || '',
      '确认表提交日期': formatDate(item.createTime),
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

useAgentPageContext(() => {
  const positions = positionStore.positions.filter(position => position.positionName === searchForm.value.position)
  return {
  title: '实习确认',
  view: detailDialogVisible.value ? 'confirmation-detail' : 'page',
  object: (detailDialogVisible.value && (currentStudent.value as any)?.id) ? { type: 'confirmation', id: String((currentStudent.value as any).id) } : null,
  filters: { keyword: searchForm.value.keyword, status: searchForm.value.status,
    positionId: positions.length === 1 ? String(positions[0].id) : '',
    visibleIds: paginatedTableData.value.slice(0, 20).map(row => String(row.id)) }
  }
})
</script>

<template>
  <div class="internship-confirm">
    <div class="page-header">
      <h2>实习确认</h2>
      <p>处理学生提交的实习确认申请；关联岗位确认后扣减对应名额，文字岗位不占其他岗位名额。确认成功后切换当前实习。</p>
    </div>

    <div class="search-card">
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
        <el-form-item v-show="!isMobile || showMobileFilters" label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable style="width: 150px">
            <el-option label="待企业确认" value="0" />
            <el-option label="企业已确认" value="1" />
            <el-option label="企业已拒绝" value="2" />
          </el-select>
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
            <span class="stat-label">待企业确认</span>
            <span class="stat-value pending">{{ stat.pending }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">企业已确认</span>
            <span class="stat-value confirmed">{{ stat.confirmed }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">企业已拒绝</span>
            <span class="stat-value rejected">{{ stat.rejected }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="table-card">
      <el-table
        v-if="!isMobile"
        :data="paginatedTableData"
        border
        style="width: 100%"
      >
        <el-table-column prop="positionName" label="应聘岗位" min-width="100" />
        <el-table-column label="来源" width="120"><template #default="{ row }">{{ row.sourceApplicationId ? '自主实习申请' : '平台录取' }}</template></el-table-column>
        <el-table-column prop="studentName" label="学生姓名" width="90" />
        <el-table-column prop="studentUserId" label="学号" width="120" />
        <el-table-column label="年级和专业" min-width="120">
          <template #default="{ row }">
            {{ row.grade }}级 {{ row.majorName || '' }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="确认表提交日期" width="130" align="center">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="companyConfirmStatus" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.companyConfirmStatus]">
              {{ statusMap[row.companyConfirmStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" align="center" fixed="right">
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

      <!-- 手机端卡片列表 -->
      <div v-else class="mobile-card-list">
        <el-empty v-if="paginatedTableData.length === 0" description="暂无数据" />
        <div
          v-for="row in paginatedTableData"
          :key="row.id"
          class="mobile-card"
        >
          <div class="mobile-card-header">
            <span class="mobile-card-title">{{ row.positionName || '-' }}</span>
            <el-tag :type="statusTypeMap[row.companyConfirmStatus]" size="small">
              {{ statusMap[row.companyConfirmStatus] }}
            </el-tag>
          </div>
          <div class="mobile-card-body">
            <div class="mobile-card-row">
              <span class="mobile-card-label">来源</span>
              <span class="mobile-card-value">{{ row.sourceApplicationId ? '自主实习申请' : '平台录取' }}</span>
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">学生姓名</span>
              <span class="mobile-card-value">{{ row.studentName || '-' }}</span>
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">学号</span>
              <span class="mobile-card-value">{{ row.studentUserId || '-' }}</span>
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">年级专业</span>
              <span class="mobile-card-value">{{ row.grade }}级 {{ row.majorName || '' }}</span>
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">提交日期</span>
              <span class="mobile-card-value">{{ formatDate(row.createTime) }}</span>
            </div>
          </div>
          <div class="mobile-card-actions">
            <el-button
              v-if="row.companyConfirmStatus === 0"
              type="success"
              @click="handleConfirm(row)"
            >
              确认
            </el-button>
            <el-button
              v-if="row.companyConfirmStatus === 0"
              type="danger"
              @click="handleReject(row)"
            >
              拒绝
            </el-button>
            <el-button
              type="primary"
              @click="handleViewDetail(row)"
            >
              查看详情
            </el-button>
          </div>
        </div>
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

    <InternshipAgreementPreview ref="filePreview" :read-file="readCompanyAgreement" />
    <el-dialog v-model="confirmationActionVisible" title="确认实习岗位" class="confirmation-position-dialog" :width="isMobile ? '94%' : '560px'" :close-on-click-modal="false">
      <template v-if="confirmationActionRecord">
        <p class="confirmation-position-summary">{{ confirmationActionRecord.studentName }}申请的岗位：<strong>{{ confirmationActionRecord.positionName }}</strong></p>
        <el-radio-group v-model="confirmationMode" class="confirmation-position-modes">
          <el-radio value="TEXT">岗位未发布，直接确认</el-radio>
          <el-radio value="EXISTING">岗位已发布，从列表选择</el-radio>
        </el-radio-group>
        <p v-if="confirmationMode === 'TEXT'" class="confirmation-position-tip">若岗位已发布，请选择对应岗位。</p>
        <div v-else>
          <el-select v-model="confirmationPositionId" placeholder="请选择已发布的岗位" class="confirmation-position-select">
            <el-option v-for="position in positionStore.positions" :key="position.id" :value="position.id"
              :label="`${position.positionName}（剩余 ${position.remainingQuota ?? 0} 人）`" />
          </el-select>
          <p class="confirmation-position-tip">确认后占用所选岗位的名额。</p>
        </div>
        <div class="confirmation-position-actions">
          <button v-if="confirmationMode === 'EXISTING'" type="button" class="confirmation-text-action" @click="refreshConfirmationPositions">刷新岗位</button>
          <button type="button" class="confirmation-text-action" @click="openPositionManagement">新建岗位</button>
        </div>
      </template>
      <template #footer>
        <el-button @click="confirmationActionVisible = false">取消</el-button>
        <el-button type="primary" :loading="confirmationSubmitting" @click="submitConfirmationAction">确认接收</el-button>
      </template>
    </el-dialog>
    <el-dialog
      v-model="detailDialogVisible"
      title="学生实习确认详情"
      width="900px"
      :close-on-click-modal="false"
    >
      <div v-if="currentStudent" v-loading="detailLoading" class="student-detail">
        <div class="detail-section">
          <h3 class="section-title">学生信息</h3>
          <el-descriptions :column="isMobile ? 1 : 2" border>
            <el-descriptions-item label="姓名">{{ currentStudent.studentName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="学号">{{ currentStudent.studentUserId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="年级">{{ currentStudent.grade ? currentStudent.grade + '级' : '-' }}</el-descriptions-item>
            <el-descriptions-item label="专业">{{ currentStudent.majorName || currentStudent.major || '-' }}</el-descriptions-item>
            <el-descriptions-item label="班级">{{ currentStudent.className || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ currentStudent.contactPhone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ currentStudent.email || '未填写' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section">
          <h3 class="section-title">实习信息</h3>
          <el-descriptions :column="isMobile ? 1 : 2" border>
            <el-descriptions-item label="实习企业">{{ currentStudent.companyName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="实习岗位">{{ currentStudent.positionName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="企业地址" :span="2">{{ currentStudent.companyAddress || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系人">{{ detailContact?.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ detailContact?.phone || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div v-if="detailApplication" class="detail-section">
          <h3 class="section-title">自主实习申请信息</h3>
          <el-descriptions :column="isMobile ? 1 : 2" border>
            <el-descriptions-item label="学生填写岗位">{{ detailApplication.positionName }}</el-descriptions-item>
            <el-descriptions-item label="单位所在城市">{{ detailApplication.companyCity }}</el-descriptions-item>
            <el-descriptions-item label="人事联系人">{{ detailApplication.hrManagerName }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ detailApplication.hrManagerPhone }}</el-descriptions-item>
            <el-descriptions-item label="申请理由" :span="2">{{ detailApplication.reason }}</el-descriptions-item>
          </el-descriptions>
          <template v-for="(presence, name) in detailApplication.materials || {}" :key="name">
            <el-button v-if="presence" link type="primary" @click="downloadApplicationMaterial(String(name))">下载{{ name }}</el-button>
          </template>
        </div>
        <div v-if="detailAgreements.length" class="detail-section">
          <h3 class="section-title">本次提交的实习协议</h3>
          <div v-for="version in detailAgreements" :key="version.id">
            <div v-for="file in version.files" :key="file.id" class="agreement-file-row">
              <span>{{ file.fileName }}</span>
              <el-button link type="primary" @click="previewAgreement(file)">预览</el-button>
              <el-button link type="primary" @click="downloadAgreement(file)">下载</el-button>
            </div>
          </div>
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
.confirmation-position-summary {
  margin: 0 0 14px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.confirmation-position-summary strong {
  color: #303133;
  font-weight: 500;
}

.confirmation-position-modes {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
}

.confirmation-position-modes :deep(.el-radio) {
  height: auto;
  margin-right: 0;
  padding: 6px 0;
  white-space: normal;
}

.confirmation-position-modes :deep(.el-radio__label) {
  line-height: 1.6;
  white-space: normal;
}

.confirmation-position-select {
  width: 100%;
  margin-top: 12px;
}

.confirmation-position-tip {
  margin: 8px 0 12px;
  color: #909399;
  font-size: 13px;
  line-height: 1.7;
}

.confirmation-position-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 20px;
  margin-top: 12px;
}

.confirmation-text-action,
.confirmation-text-action:hover,
.confirmation-text-action:active {
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  color: var(--el-color-primary);
  font: inherit;
  line-height: 1.6;
  cursor: pointer;
  transform: none;
}

.confirmation-text-action:hover {
  text-decoration: underline;
}

.confirmation-text-action:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 3px;
}

.agreement-file-row { display:flex; gap:12px; align-items:center; flex-wrap:wrap; margin:8px 0; }
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
  padding: 12px 16px;
  border-radius: 12px;
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
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e8e8e8;
}

.stat-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.stat-total {
  font-size: 13px;
  color: #909399;
}

.stat-details {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
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

@media (max-width: 768px) {
  .page-header {
    padding: 18px 20px;
    margin-bottom: 16px;
  }

  .page-header h2 {
    font-size: 22px;
  }

  .search-card {
    padding: 16px 18px;
  }

  .search-form :deep(.el-form-item) {
    width: 100%;
    margin-right: 0;
  }

  .search-form :deep(.el-form-item__content) {
    flex-direction: row;
    flex-wrap: wrap;
    min-width: 0;
    gap: 8px;
  }

  .search-form :deep(.el-input),
  .search-form :deep(.el-select) {
    width: 100% !important;
  }

  .search-form :deep(.el-button) {
    flex: 1;
    min-height: 44px;
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

  .stat-details {
    grid-template-columns: repeat(2, 1fr);
  }

  .table-card {
    padding: 12px;
  }

  .pagination {
    justify-content: center;
  }

  .dialog-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .dialog-footer :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }
}
</style>
