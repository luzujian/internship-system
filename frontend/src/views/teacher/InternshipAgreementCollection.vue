<template>
  <div>
    <div class="summary-grid">
      <div class="summary-card summary-card--total"><span>管理范围实习记录</span><strong>{{ summary.total }}</strong><small>条</small></div>
      <div class="summary-card summary-card--submitted"><span>已交协议</span><strong>{{ summary.submitted }}</strong><small>条</small></div>
      <div class="summary-card summary-card--pending"><span>未交协议</span><strong>{{ summary.pending }}</strong><small>条</small></div>
    </div>
    <el-card shadow="never" class="content-card">
      <div class="filters">
        <el-input v-model="filters.keyword" clearable placeholder="搜索姓名或学号" class="keyword-input" @keyup.enter="search" @clear="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filters.classId" clearable placeholder="全部班级" @change="search">
          <el-option v-for="item in classes" :key="item.id" :value="item.id" :label="item.name" />
        </el-select>
        <el-select v-model="filters.sourceType" clearable placeholder="全部实习来源" @change="search">
          <el-option label="平台实习" value="CONFIRMATION" /><el-option label="自主实习" value="SELF_PRACTICE" />
        </el-select>
        <el-select v-model="filters.status" placeholder="协议提交状态" @change="search">
          <el-option label="全部提交状态" value="ALL" /><el-option label="已提交" value="SUBMITTED" /><el-option label="未提交" value="NOT_SUBMITTED" />
        </el-select>
        <el-select v-model="filters.businessStatus" placeholder="业务状态" @change="search">
          <el-option label="待审核及已生效" value="ACTIVE" /><el-option label="撤回及拒绝记录" value="HISTORY" /><el-option label="全部业务状态" value="ALL" />
          <el-option v-for="(label, value) in agreementBusinessStatusText" :key="value" :label="label" :value="value" />
        </el-select>
        <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="studentNumber" label="学号" min-width="130" />
        <el-table-column prop="studentName" label="姓名" min-width="90" />
        <el-table-column label="班级" min-width="130"><template #default="{ row }">{{ row.className || '未分班' }}</template></el-table-column>
        <el-table-column prop="companyName" label="实习单位" min-width="190" show-overflow-tooltip />
        <el-table-column label="来源" width="100"><template #default="{ row }">{{ row.sourceType === 'SELF_PRACTICE' ? '自主实习' : '平台实习' }}</template></el-table-column>
        <el-table-column label="业务状态" width="115"><template #default="{ row }">{{ agreementBusinessStatusText[row.businessStatus] || row.businessStatus }}</template></el-table-column>
        <el-table-column label="协议状态" width="105" align="center">
          <template #default="{ row }"><el-tag :type="row.submissionStatus === 'SUBMITTED' ? 'success' : 'info'">{{ row.submissionStatus === 'SUBMITTED' ? '已提交' : '未提交' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="最新提交时间" min-width="170"><template #default="{ row }">{{ row.submittedAt || '--' }}</template></el-table-column>
        <el-table-column prop="fileCount" label="文件数" width="80" align="center" />
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">查看</el-button></template>
        </el-table-column>
        <template #empty><el-empty description="暂无符合条件的实习记录" /></template>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.pageSize" :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total" layout="total, sizes, prev, pager, next, jumper" @size-change="search" @current-change="load" />
      </div>
    </el-card>
    <el-drawer v-model="detailVisible" title="实习协议详情" size="620px" destroy-on-close>
      <div v-loading="detailLoading" class="detail-content">
        <template v-if="detail">
          <div class="student-summary">
            <div><span>学生</span><strong>{{ detail.record.studentName }}（{{ detail.record.studentNumber }}）</strong></div>
            <div><span>班级</span><strong>{{ detail.record.className || '未分班' }}</strong></div>
            <div><span>实习单位</span><strong>{{ detail.record.companyName }}</strong></div>
            <div><span>实习岗位</span><strong>{{ detail.record.positionName || '--' }}</strong></div>
          </div>
          <div v-if="detail.versions.length" class="version-picker">
            <span>提交版本</span>
            <el-select v-model="selectedVersion" style="width: 220px">
              <el-option v-for="version in detail.versions" :key="version.version" :value="version.version"
                :label="`第 ${version.version} 版${version.version === detail.versions[0].version ? '（最新）' : ''}`" />
            </el-select>
          </div>
          <p v-if="currentVersion" class="version-meta">{{ currentVersion.companyName }} · {{ currentVersion.submittedAt }}</p>
          <h3 class="file-section-title">已提交文件</h3>
          <div class="file-list">
            <div v-for="file in currentVersion?.files || []" :key="file.id" class="file-row">
              <div class="file-main"><el-icon><Document /></el-icon><div><strong>{{ file.fileName }}</strong><span>{{ formatSize(file.fileSize) }}</span></div></div>
              <div class="file-actions"><el-button link type="primary" @click="preview?.show(file)">预览</el-button><el-button link type="primary" @click="download(file)">下载</el-button></div>
            </div>
          </div>
          <el-empty v-if="!detail.versions.length" description="该实习记录尚未提交协议" />
        </template>
      </div>
    </el-drawer>
    <InternshipAgreementPreview ref="preview" role="teacher" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Search } from '@element-plus/icons-vue'
import InternshipAgreementPreview from '@/components/student/InternshipAgreementPreview.vue'
import { agreementBusinessStatusText, exportAgreementFiles, getAgreementCollection, getAgreementDetail,
  readAgreementFile, saveAgreementBlob, type AgreementCollectionPage, type AgreementDetail, type AgreementFile,
  type AgreementQuery, type AgreementRecord } from '@/api/internshipAgreements'

const loading = ref(false)
const exporting = ref(false)
const rows = ref<AgreementRecord[]>([])
const classes = ref<{ id: number; name: string }[]>([])
const filters = reactive<AgreementQuery>({ keyword: '', classId: undefined, sourceType: undefined, status: 'ALL', businessStatus: 'ACTIVE' })
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })
const summary = reactive({ total: 0, submitted: 0, pending: 0 })
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AgreementDetail | null>(null)
const selectedVersion = ref(0)
const currentVersion = computed(() => detail.value?.versions.find(version => version.version === selectedVersion.value))
const preview = ref<InstanceType<typeof InternshipAgreementPreview> | null>(null)
const canExport = computed(() => filters.status !== 'NOT_SUBMITTED' && summary.submitted > 0 && !loading.value)
let listToken = 0
let detailToken = 0
const query = (withPage = true): AgreementQuery => ({ ...filters, keyword: filters.keyword?.trim() || undefined,
  sourceType: filters.sourceType || undefined, ...(withPage ? { page: pagination.page, pageSize: pagination.pageSize } : {}) })
const load = async () => {
  const token = ++listToken
  loading.value = true
  try {
    const response: any = await getAgreementCollection(query())
    if (token !== listToken) return
    if (response.code !== 200) throw new Error(response.message || '协议数据加载失败')
    const data = response.data as AgreementCollectionPage
    rows.value = data.rows || []
    pagination.total = data.total || 0
    summary.total = data.responsibleRecordCount || 0
    summary.submitted = data.submittedCount || 0
    summary.pending = data.notSubmittedCount || 0
    classes.value = data.classes || []
  } catch (error: any) { if (token === listToken) ElMessage.error(error?.message || '协议数据加载失败') }
  finally { if (token === listToken) loading.value = false }
}
const search = () => { pagination.page = 1; void load() }
const reset = () => { Object.assign(filters, { keyword: '', classId: undefined, sourceType: undefined, status: 'ALL', businessStatus: 'ACTIVE' }); search() }
const openDetail = async (record: AgreementRecord) => {
  const token = ++detailToken
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const response: any = await getAgreementDetail('teacher', record.sourceType, record.sourceId)
    if (token !== detailToken) return
    if (response.code !== 200) throw new Error(response.message || '协议详情加载失败')
    detail.value = response.data
    selectedVersion.value = detail.value?.versions[0]?.version || 0
  } catch (error: any) { if (token === detailToken) { ElMessage.error(error?.message || '协议详情加载失败'); detailVisible.value = false } }
  finally { if (token === detailToken) detailLoading.value = false }
}
const download = async (file: AgreementFile) => {
  if (!file.id) return
  try { saveAgreementBlob(await readAgreementFile('teacher', file.id), file.fileName) }
  catch (error: any) { ElMessage.error(error?.message || '协议下载失败') }
}
const handleExport = async () => {
  if (!canExport.value || exporting.value) return
  exporting.value = true
  try {
    saveAgreementBlob(await exportAgreementFiles(query(false)), `实习协议_${new Date().toISOString().replace(/\D/g, '').slice(0, 14)}.zip`)
    ElMessage.success('实习协议压缩包导出完成')
  } catch (error: any) { ElMessage.error(error?.message || '协议导出失败') }
  finally { exporting.value = false }
}
const formatSize = (size: number) => size < 1024 * 1024 ? `${(size / 1024).toFixed(1)} KB` : `${(size / 1024 / 1024).toFixed(1)} MB`
onMounted(load)
onBeforeUnmount(() => { listToken++; detailToken++ })
defineExpose({ handleExport, exporting, canExport })
</script>

<style scoped>
.summary-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-bottom: 18px; }
.summary-card { display: flex; align-items: baseline; gap: 7px; padding: 20px 22px; border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; box-shadow: 0 4px 14px rgb(15 23 42 / 4%); }
.summary-card span { margin-right: auto; color: #64748b; font-weight: 500; }
.summary-card strong { font-size: 30px; line-height: 1; color: #1f2937; }
.summary-card small { color: #94a3b8; }
.summary-card--total { border-top: 3px solid #3b82f6; }
.summary-card--submitted { border-top: 3px solid #10b981; }
.summary-card--pending { border-top: 3px solid #f59e0b; }
.content-card { border: none; border-radius: 12px; box-shadow: 0 2px 12px rgb(0 0 0 / 8%); }
.filters { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 18px; }
.keyword-input { width: 260px; }
.filters :deep(.el-select) { width: 180px; }
.pagination-wrap { display: flex; justify-content: flex-end; padding-top: 20px; }
.detail-content { min-height: 240px; }
.student-summary { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; padding: 18px; border-radius: 10px; background: #f8fafc; }
.student-summary div { display: flex; flex-direction: column; gap: 5px; }
.student-summary span { color: #94a3b8; font-size: 13px; }
.student-summary strong { color: #334155; font-size: 14px; }
.file-section-title { margin: 24px 0 12px; font-size: 16px; color: #1f2937; }
.file-list { display: flex; flex-direction: column; gap: 10px; }
.file-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px; border: 1px solid #e5e7eb; border-radius: 9px; }
.file-main { display: flex; align-items: center; gap: 10px; min-width: 0; }
.file-main > .el-icon { flex: none; color: #3b82f6; font-size: 22px; }
.file-main div { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.file-main strong { color: #334155; overflow-wrap: anywhere; }
.file-main span, .version-meta { color: #94a3b8; font-size: 12px; }
.file-actions { display: flex; flex: none; }
.version-picker { display: flex; align-items: center; gap: 12px; margin-top: 20px; color: #64748b; }
@media (max-width: 900px) { .summary-grid, .student-summary { grid-template-columns: 1fr; } }
</style>
