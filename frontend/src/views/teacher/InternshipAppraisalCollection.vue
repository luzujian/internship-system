<template>
  <div class="appraisal-collection-page">
    <div class="page-heading">
      <div>
        <h1>鉴定表收集</h1>
        <p>查看和导出管理范围内学生已提交的实习鉴定表</p>
      </div>
      <el-button
        type="primary"
        :loading="exporting"
        :disabled="filters.status === 'NOT_SUBMITTED' || summary.submittedCount === 0"
        @click="handleExport"
      >
        <el-icon><Download /></el-icon>
        全部导出
      </el-button>
    </div>

    <div class="summary-grid">
      <div class="summary-card summary-card--total">
        <span>管理范围学生</span>
        <strong>{{ summary.totalStudents }}</strong>
        <small>人</small>
      </div>
      <div class="summary-card summary-card--submitted">
        <span>已提交</span>
        <strong>{{ summary.submittedCount }}</strong>
        <small>人</small>
      </div>
      <div class="summary-card summary-card--pending">
        <span>未提交</span>
        <strong>{{ summary.notSubmittedCount }}</strong>
        <small>人</small>
      </div>
    </div>

    <el-card shadow="never" class="content-card">
      <div class="filters">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="搜索姓名或学号"
          class="keyword-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filters.classId" clearable placeholder="全部班级" @change="handleSearch">
          <el-option v-for="item in classOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-select v-model="filters.status" placeholder="全部状态" @change="handleSearch">
          <el-option label="全部状态" value="ALL" />
          <el-option label="已提交" value="SUBMITTED" />
          <el-option label="未提交" value="NOT_SUBMITTED" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe class="appraisal-table">
        <el-table-column prop="studentNumber" label="学号" min-width="130" />
        <el-table-column prop="studentName" label="姓名" min-width="100" />
        <el-table-column prop="className" label="班级" min-width="140">
          <template #default="{ row }">{{ row.className || '未分班' }}</template>
        </el-table-column>
        <el-table-column prop="majorName" label="专业" min-width="150">
          <template #default="{ row }">{{ row.majorName || '--' }}</template>
        </el-table-column>
        <el-table-column label="提交状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.submissionStatus === 'SUBMITTED' ? 'success' : 'info'" effect="light">
              {{ row.submissionStatus === 'SUBMITTED' ? '已提交' : '未提交' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="165">
          <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
        </el-table-column>
        <el-table-column label="文件数" width="90" align="center">
          <template #default="{ row }">{{ row.fileCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :disabled="row.submissionStatus !== 'SUBMITTED'"
              @click="openDetail(row.studentId)"
            >查看</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无符合条件的学生" />
        </template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handlePageSizeChange"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="鉴定表详情" size="620px" destroy-on-close>
      <div v-loading="detailLoading" class="detail-content">
        <template v-if="detail">
          <div class="student-summary">
            <div><span>学生</span><strong>{{ detail.studentName }}（{{ detail.studentNumber }}）</strong></div>
            <div><span>班级</span><strong>{{ detail.className || '未分班' }}</strong></div>
            <div><span>专业</span><strong>{{ detail.majorName || '--' }}</strong></div>
            <div><span>提交时间</span><strong>{{ formatDateTime(detail.submittedAt) }}</strong></div>
          </div>

          <h3 class="file-section-title">已提交文件</h3>
          <div v-if="detail.files?.length" class="file-list">
            <div v-for="file in detail.files" :key="file.id" class="file-row">
              <div class="file-main">
                <el-icon><Document /></el-icon>
                <div>
                  <strong>{{ file.fileName }}</strong>
                  <span>{{ file.companyName || '未关联企业' }} · {{ formatFileSize(file.fileSize) }}</span>
                </div>
              </div>
              <div class="file-actions">
                <el-button link type="primary" @click="handlePreview(file)">预览</el-button>
                <el-button link type="primary" @click="handleDownload(file)">下载</el-button>
              </div>
            </div>
          </div>
          <el-empty v-else description="该提交记录没有文件" />
        </template>
      </div>
    </el-drawer>

    <el-dialog
      v-model="previewVisible"
      :title="`预览：${previewFile?.fileName || ''}`"
      width="920px"
      destroy-on-close
      @closed="cleanupPreview"
    >
      <div v-loading="previewLoading" class="preview-body">
        <iframe
          v-if="previewMode === 'document' && previewUrl"
          :src="previewUrl"
          title="鉴定表预览"
          class="preview-frame"
        />
        <div v-else-if="previewMode === 'docx'" ref="docxContainer" class="docx-container" />
        <el-result v-else-if="previewError" icon="warning" title="暂时无法在线预览" :sub-title="previewError">
          <template #extra>
            <el-button v-if="previewFile" type="primary" @click="handleDownload(previewFile)">下载原文件</el-button>
          </template>
        </el-result>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Document, Download, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  downloadAppraisalFile,
  exportAppraisalFiles,
  getAppraisalCollection,
  getAppraisalStudentDetail,
  previewAppraisalFile,
  type AppraisalCollectionItem,
  type AppraisalFileItem,
  type AppraisalStudentDetail,
  type AppraisalSubmissionStatus,
  type TeacherAppraisalClassOption
} from '@/api/internshipAppraisalCollection'

const loading = ref(false)
const exporting = ref(false)
const rows = ref<AppraisalCollectionItem[]>([])
const classOptions = ref<TeacherAppraisalClassOption[]>([])
const filters = reactive<{ keyword: string; classId?: number; status: AppraisalSubmissionStatus }>({
  keyword: '',
  classId: undefined,
  status: 'ALL'
})
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })
const summary = reactive({ totalStudents: 0, submittedCount: 0, notSubmittedCount: 0 })

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AppraisalStudentDetail | null>(null)

const previewVisible = ref(false)
const previewLoading = ref(false)
const previewFile = ref<AppraisalFileItem | null>(null)
const previewUrl = ref('')
const previewMode = ref<'document' | 'docx' | ''>('')
const previewError = ref('')
const docxContainer = ref<HTMLElement | null>(null)
let previewRequestToken = 0

const queryParams = (includePage = true) => ({
  ...(includePage ? { page: pagination.page, pageSize: pagination.pageSize } : {}),
  keyword: filters.keyword.trim() || undefined,
  classId: filters.classId,
  status: filters.status
})

const loadData = async () => {
  loading.value = true
  try {
    interface AppraisalCollectionPage {
      rows?: AppraisalCollectionItem[]
      total?: number
      responsibleStudentCount?: number
      submittedCount?: number
      notSubmittedCount?: number
      classes?: TeacherAppraisalClassOption[]
      [key: string]: unknown
    }
    const response = await getAppraisalCollection(queryParams()) as unknown as {
      code?: number
      message?: string
      msg?: string
      data?: AppraisalCollectionPage
    }
    const data = (response?.data ?? {}) as AppraisalCollectionPage
    rows.value = Array.isArray(data.rows) ? data.rows : []
    pagination.total = Number(data.total || 0)
    summary.totalStudents = Number(data.responsibleStudentCount || 0)
    summary.submittedCount = Number(data.submittedCount || 0)
    summary.notSubmittedCount = Number(data.notSubmittedCount || 0)
    if (Array.isArray(data.classes)) classOptions.value = data.classes
  } catch (error: any) {
    ElMessage.error(error?.message || '加载鉴定表收集数据失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadData()
}

const handleReset = () => {
  filters.keyword = ''
  filters.classId = undefined
  filters.status = 'ALL'
  pagination.page = 1
  loadData()
}

const handlePageSizeChange = () => {
  pagination.page = 1
  loadData()
}

const openDetail = async (studentId: number) => {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const response = await getAppraisalStudentDetail(studentId) as unknown as {
      code?: number
      data?: AppraisalStudentDetail
    }
    const detailData: unknown = response.data
    detail.value = (detailData !== null && typeof detailData === 'object'
      ? detailData as AppraisalStudentDetail
      : null)
  } catch (error: any) {
    ElMessage.error(error?.message || '加载鉴定表详情失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const fileExtension = (name: string) => name.split('.').pop()?.toLowerCase() || ''

const handlePreview = async (file: AppraisalFileItem) => {
  cleanupPreview()
  const requestToken = ++previewRequestToken
  previewFile.value = file
  previewVisible.value = true
  previewLoading.value = true
  try {
    const blob = await previewAppraisalFile(file.id)
    if (requestToken !== previewRequestToken || !previewVisible.value) return
    const extension = fileExtension(file.fileName)
    if (extension === 'docx') {
      previewMode.value = 'docx'
      await nextTick()
      if (!docxContainer.value) throw new Error('预览容器初始化失败')
      const { renderSafeDocx } = await import('@/utils/safeDocx')
      const renderTarget = document.createElement('div')
      await renderSafeDocx(blob, renderTarget, undefined, {
        inWrapper: true,
        ignoreLastRenderedPageBreak: true,
        renderHeaders: true,
        renderFooters: true,
        renderFootnotes: true
      })
      if (requestToken !== previewRequestToken || !previewVisible.value || !docxContainer.value) return
      docxContainer.value.replaceChildren(...Array.from(renderTarget.childNodes))
    } else {
      previewMode.value = 'document'
      previewUrl.value = URL.createObjectURL(blob)
    }
  } catch (error: any) {
    if (requestToken !== previewRequestToken) return
    previewMode.value = ''
    previewError.value = error?.message || '文件预览失败，请下载后查看'
  } finally {
    if (requestToken === previewRequestToken) previewLoading.value = false
  }
}

const saveBlob = (blob: Blob, fileName: string) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

const handleDownload = async (file: AppraisalFileItem) => {
  try {
    const blob = await downloadAppraisalFile(file.id)
    saveBlob(blob, file.fileName || '实习鉴定表')
  } catch (error: any) {
    ElMessage.error(error?.message || '文件下载失败')
  }
}

const handleExport = async () => {
  exporting.value = true
  try {
    const blob = await exportAppraisalFiles(queryParams(false))
    saveBlob(blob, `实习鉴定表_${formatFileTimestamp(new Date())}.zip`)
    ElMessage.success('鉴定表压缩包导出完成')
  } catch (error: any) {
    ElMessage.error(error?.message || '鉴定表导出失败')
  } finally {
    exporting.value = false
  }
}

const cleanupPreview = () => {
  previewRequestToken += 1
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  previewMode.value = ''
  previewError.value = ''
  previewFile.value = null
  if (docxContainer.value) docxContainer.value.innerHTML = ''
}

const formatDateTime = (value?: string | null) => {
  if (!value) return '--'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '--' : date.toLocaleString('zh-CN', { hour12: false })
}

const formatFileSize = (value?: number | null) => {
  if (!value) return '未知大小'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

const formatFileTimestamp = (date: Date) => {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}_${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`
}

onMounted(loadData)
onBeforeUnmount(cleanupPreview)
</script>

<style scoped>
.appraisal-collection-page {
  padding: 20px;
  min-height: 100vh;
  background: #f5f7fa;
}

.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
  padding: 4px;
}

.page-heading h1 {
  margin: 0 0 8px;
  color: #303133;
  font-size: 24px;
  font-weight: 600;
}

.page-heading p {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}

.summary-card {
  display: flex;
  align-items: baseline;
  gap: 7px;
  padding: 20px 22px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 4px 14px rgb(15 23 42 / 4%);
}

.summary-card span { margin-right: auto; color: #64748b; font-weight: 500; }
.summary-card strong { font-size: 30px; line-height: 1; color: #1f2937; }
.summary-card small { color: #94a3b8; }
.summary-card--total { border-top: 3px solid #3b82f6; }
.summary-card--submitted { border-top: 3px solid #10b981; }
.summary-card--pending { border-top: 3px solid #f59e0b; }

.content-card {
  border: none;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgb(0 0 0 / 8%);
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 18px;
}

.keyword-input { width: 260px; }
.filters :deep(.el-select) { width: 180px; }
.appraisal-table { width: 100%; }

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding-top: 20px;
}

.detail-content { min-height: 240px; }

.student-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  padding: 18px;
  border-radius: 10px;
  background: #f8fafc;
}

.student-summary div { display: flex; flex-direction: column; gap: 5px; }
.student-summary span { color: #94a3b8; font-size: 13px; }
.student-summary strong { color: #334155; font-size: 14px; }
.file-section-title { margin: 24px 0 12px; font-size: 16px; color: #1f2937; }
.file-list { display: flex; flex-direction: column; gap: 10px; }

.file-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 9px;
}

.file-main { display: flex; align-items: center; gap: 10px; min-width: 0; }
.file-main > .el-icon { flex: none; color: #3b82f6; font-size: 22px; }
.file-main div { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.file-main strong { overflow: hidden; color: #334155; text-overflow: ellipsis; white-space: nowrap; }
.file-main span { color: #94a3b8; font-size: 12px; }
.file-actions { display: flex; flex: none; }

.preview-body { min-height: 560px; }
.preview-frame { width: 100%; height: 65vh; border: 0; background: #f8fafc; }
.docx-container { min-height: 560px; max-height: 70vh; overflow: auto; background: #e5e7eb; padding: 18px; }

@media (max-width: 900px) {
  .appraisal-collection-page { padding: 16px; }
  .summary-grid { grid-template-columns: 1fr; }
  .page-heading { align-items: flex-start; }
  .student-summary { grid-template-columns: 1fr; }
}
</style>
