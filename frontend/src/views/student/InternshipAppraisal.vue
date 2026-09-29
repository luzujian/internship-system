<template>
  <div class="appraisal-page">
    <div class="page-header">
      <div>
        <h2>实习鉴定</h2>
        <p>上传最终实习材料，并完成实际实习企业的推荐评分</p>
      </div>
      <span :class="['status-badge', appraisalStatus.toLowerCase()]">{{ appraisalStatusText }}</span>
    </div>

    <section class="appraisal-card">
      <div class="appraisal-header">
        <div>
          <h3>鉴定表材料</h3>
          <p>支持 PDF、DOC、DOCX，单个文件不超过 20MB。</p>
        </div>
        <router-link class="resource-link" to="/student/profile?action=templateCenter">
          去个人中心资源中心下载模板
        </router-link>
      </div>

      <div class="appraisal-upload-row">
        <el-select
          v-model="selectedPlacementId"
          class="appraisal-select"
          placement="bottom-start"
          :popper-options="{ modifiers: [{ name: 'flip', enabled: false }] }"
          :disabled="appraisalStatus === 'SUBMITTED' || appraisalSubmitting"
          placeholder="请选择对应的实习公司"
        >
          <el-option
            v-for="record in internshipRecords"
            :key="placementKey(record)"
            :value="placementKey(record)"
            :label="`${record.companyName || '未命名公司'} · ${record.positionName || '未命名岗位'}`"
          />
        </el-select>
        <label :class="['appraisal-upload-link', { disabled: !selectedPlacementId || appraisalStatus === 'SUBMITTED' || appraisalSubmitting }]">
          {{ appraisalSubmitting ? '上传中…' : '上传鉴定表' }}
          <input
            type="file"
            accept=".pdf,.doc,.docx"
            :disabled="!selectedPlacementId || appraisalStatus === 'SUBMITTED' || appraisalSubmitting"
            @change="handleAppraisalFileChange"
          />
        </label>
        <button v-if="canWithdrawAppraisal" class="appraisal-withdraw-link" type="button" :disabled="appraisalSubmitting" @click="withdrawAppraisal">撤回修改</button>
        <button class="appraisal-submit-link" type="button" :disabled="appraisalSubmitDisabled" @click="submitAppraisal">
          提交最终材料
        </button>
      </div>

      <div v-if="!submissionWindow.open && appraisalStatus !== 'SUBMITTED'" class="submission-window-tip">
        {{ submissionWindow.reason }}
      </div>

      <div v-if="appraisalFiles.length" class="appraisal-files">
        <div v-for="file in appraisalFiles" :key="file.id" class="appraisal-file-row">
          <span class="appraisal-file-name">{{ file.fileName }}</span>
          <span class="appraisal-file-company">{{ file.companyName || '待关联公司' }}</span>
          <button
            type="button"
            :disabled="appraisalStatus === 'SUBMITTED' || appraisalSubmitting"
            @click="removeAppraisalFile(file.id)"
          >删除</button>
        </div>
      </div>
      <div v-else class="appraisal-empty">尚未上传鉴定表，完成实习后至少提交一份。</div>
    </section>

    <section class="rating-card">
      <div v-if="requiredCompanyRatings.length" class="company-rating-list">
        <div class="company-rating-heading">
          <strong>企业推荐指数（必选）</strong>
          <span>请为每家实际实习企业选择 1～5 星，不需要填写文字评价。</span>
        </div>
        <div v-for="company in requiredCompanyRatings" :key="company.companyId" class="company-rating-row">
          <span>{{ company.companyName || '未命名企业' }}</span>
          <el-rate
            v-model="companyRatings[company.companyId]"
            :max="5"
            :disabled="appraisalStatus === 'SUBMITTED'"
            show-score
            score-template="{value} 星"
          />
        </div>
        <div v-if="appraisalStatus === 'SUBMITTED'" class="company-rating-locked">
          评分已随最终材料提交并锁定。
        </div>
      </div>
      <div v-else class="company-rating-warning">
        当前没有需要评分的系统企业。未收录系统的自主实习企业无需评分，可在满足提交条件后提交最终材料。
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useAgentPageContext } from '@/agent/useAgentPageContext'
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { getInternshipCalendar } from '@/api/teacherSettings'

const internshipRecords = ref<any[]>([])
const internshipCalendar = ref<any>(null)
const appraisalFiles = ref<any[]>([])
const selectedPlacementId = ref<string | null>(null)

// 下拉选项的唯一键：确认记录与状态补充记录的 id 来自不同表，加前缀避免撞值
const placementKey = (record: any): string =>
  `${record.isFromStatus ? 'S' : 'R'}${record.id}`
const appraisalSubmitting = ref(false)
const requiredCompanyRatings = ref<any[]>([])
const companyRatings = ref<Record<number, number>>({})
const appraisalStatus = ref('DRAFT')
const appraisalId = ref<string | null>(null)
const internshipEnded = ref(false)

// ===== 智能填表：实习鉴定页注册到 AI 侧栏（只写响应式 model，不触发提交） =====
const APPRAISAL_MAX_FILE_SIZE = 20 * 1024 * 1024
const APPRAISAL_ALLOWED_EXTENSIONS = new Set(['pdf', 'doc', 'docx'])

const appraisalStatusText = computed(() => {
  const statusMap: Record<string, string> = {
    DRAFT: '待提交',
    RETURNED: '已退回',
    WITHDRAWN: '已撤回，可修改',
    SUBMITTED: '已提交'
  }
  return statusMap[appraisalStatus.value] || appraisalStatus.value
})

const allCompanyRatingsSelected = computed(() => (
  requiredCompanyRatings.value.every(company => {
    const stars = companyRatings.value[company.companyId]
    return Number.isInteger(stars) && stars >= 1 && stars <= 5
  })
))

const submissionWindow = computed(() => {
  const calendar = internshipCalendar.value
  if (!calendar?.endDate || !calendar?.appraisalDeadline) {
    return { open: false, reason: '全局实习教学日历尚未完整配置' }
  }
  if (!internshipEnded.value) {
    return { open: false, reason: '实习状态尚未显示为已结束，暂不能提交实习鉴定表' }
  }
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const endDate = new Date(calendar.endDate)
  endDate.setHours(0, 0, 0, 0)
  if (today < endDate) {
    return { open: false, reason: `实习尚未结束（${calendar.endDate}），暂不能提交鉴定表` }
  }
  const deadline = new Date(calendar.appraisalDeadline)
  deadline.setHours(23, 59, 59, 999)
  if (today > deadline) {
    return { open: false, reason: '实习鉴定表提交已截止，请联系辅导员' }
  }
  return { open: true, reason: '' }
})

const canWithdrawAppraisal = computed(() => appraisalStatus.value === 'SUBMITTED' && submissionWindow.value.open)
const appraisalSubmitDisabled = computed(() => (
  !appraisalFiles.value.length
  || !allCompanyRatingsSelected.value
  || appraisalSubmitting.value
  || appraisalStatus.value === 'SUBMITTED'
  || !submissionWindow.value.open
))

const fetchInternshipRecords = async () => {
  try {
    const response: any = await request.get('/student/internship-confirmation/history')
    if (response.code === 200) {
      // 自主实习学生无岗位分配（placementId 为空），仍需保留记录以便上传鉴定表
      internshipRecords.value = (response.data || []).filter((record: any) => (
        record.recallStatus !== 2
      ))
      if (!selectedPlacementId.value && internshipRecords.value.length) {
        const preferred = internshipRecords.value.find((record: any) => record.isCurrent)
          || internshipRecords.value[0]
        selectedPlacementId.value = placementKey(preferred)
      }
    }
  } catch (error) {
    console.error('获取实习确认记录失败:', error)
  }
}

const fetchCalendar = async () => {
  try {
    const response: any = await getInternshipCalendar()
    if (response.code === 200) internshipCalendar.value = response.data || null
  } catch (error) {
    console.error('获取实习教学日历失败:', error)
  }
}

const fetchAppraisal = async () => {
  try {
    const response: any = await request.get('/student/internship-appraisal')
    if (response.code === 200 && response.data) {
      appraisalId.value = response.data.id ? String(response.data.id) : null
      appraisalFiles.value = response.data.files || []
      appraisalStatus.value = response.data.status || 'DRAFT'
      internshipEnded.value = response.data.internshipEnded === true
      requiredCompanyRatings.value = response.data.requiredCompanyRatings || []
      const nextRatings: Record<number, number> = {}
      requiredCompanyRatings.value.forEach(company => {
        const stars = company.stars ?? companyRatings.value[company.companyId]
        if (stars != null) nextRatings[company.companyId] = Number(stars)
      })
      companyRatings.value = nextRatings
    }
  } catch (error) {
    console.error('获取实习鉴定表失败:', error)
  }
}

const handleAppraisalFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  if (!selectedPlacementId.value) {
    ElMessage.warning('请先选择鉴定表对应的实习公司')
    input.value = ''
    return
  }

  if (appraisalStatus.value === 'SUBMITTED') {
    ElMessage.warning('最终材料已提交，不能继续上传鉴定表')
    input.value = ''
    return
  }

  const extension = file.name.includes('.') ? file.name.split('.').pop()?.toLowerCase() || '' : ''
  if (!APPRAISAL_ALLOWED_EXTENSIONS.has(extension)) {
    ElMessage.warning('仅支持 PDF、DOC、DOCX 格式')
    input.value = ''
    return
  }
  if (file.size > APPRAISAL_MAX_FILE_SIZE) {
    ElMessage.warning('单个文件不能超过 20MB')
    input.value = ''
    return
  }

  const formData = new FormData()
  formData.append('file', file)
  // 自主实习无岗位分配（placementId 为空）时不传该参数，后端允许
  const selected = internshipRecords.value.find(r => placementKey(r) === selectedPlacementId.value)
  if (selected?.placementId) {
    formData.append('placementId', String(selected.placementId))
  }

  appraisalSubmitting.value = true
  try {
    const response: any = await request({
      url: '/student/internship-appraisal/files',
      method: 'post',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (response.code === 200) {
      ElMessage.success('鉴定表上传成功')
      await fetchAppraisal()
    } else {
      ElMessage.error(response.message || '上传失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '上传失败')
  } finally {
    appraisalSubmitting.value = false
    input.value = ''
  }
}

const removeAppraisalFile = async (id: number) => {
  try {
    const response: any = await request.delete(`/student/internship-appraisal/files/${id}`)
    if (response.code === 200) {
      ElMessage.success('鉴定表已删除')
      await fetchAppraisal()
    } else {
      ElMessage.error(response.message || '删除失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '删除失败')
  }
}

const withdrawAppraisal = async () => {
  try {
    await ElMessageBox.confirm(
      '撤回后鉴定表材料和企业推荐指数将恢复可修改，截止前可重新提交。',
      '撤回实习鉴定',
      { confirmButtonText: '撤回修改', cancelButtonText: '取消', type: 'warning' }
    )
    appraisalSubmitting.value = true
    const response: any = await request.post('/student/internship-appraisal/withdraw')
    if (response.code === 200) {
      ElMessage.success('已撤回，可修改后重新提交')
      await fetchAppraisal()
    } else {
      ElMessage.error(response.message || '撤回失败')
    }
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '撤回失败')
  } finally {
    appraisalSubmitting.value = false
  }
}
const submitAppraisal = async () => {
  if (!submissionWindow.value.open) {
    ElMessage.warning(submissionWindow.value.reason || '当前不在鉴定表提交时间范围内')
    return
  }
  if (!appraisalFiles.value.length) {
    ElMessage.warning('请至少上传一份实习鉴定表')
    return
  }
  // 自主实习企业未收录系统，无需星星评价，直接提交（ratings 为空数组）
  if (!allCompanyRatingsSelected.value) {
    ElMessage.warning('请完成所有实习企业的五星评分')
    return
  }

  appraisalSubmitting.value = true
  try {
    const ratings = requiredCompanyRatings.value.map(company => ({
      companyId: company.companyId,
      stars: companyRatings.value[company.companyId]
    }))
    const response: any = await request.post('/student/internship-appraisal/submit', { ratings })
    if (response.code === 200) {
      ElMessage.success('实习鉴定表提交成功')
      await fetchAppraisal()
    } else {
      ElMessage.error(response.message || '提交失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '提交失败')
  } finally {
    appraisalSubmitting.value = false
  }
}

onMounted(() => {
  Promise.all([fetchInternshipRecords(), fetchCalendar(), fetchAppraisal()])
})

useAgentPageContext(() => ({
  title: '实习鉴定',
  view: 'page',
  object: appraisalId.value ? { type: 'appraisal', id: appraisalId.value } : null,
  filters: { appraisalStatus: appraisalStatus.value }
}))
</script>

<style scoped>
.appraisal-page {
  width: 100%;
  min-height: auto;
  background: #f1f5f9;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
  padding: 24px 32px;
  overflow: hidden;
  position: relative;
  color: #ffffff;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.page-header::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -10%;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
  border-radius: 50%;
}

.page-header > * {
  position: relative;
  z-index: 1;
}

.page-header h2 {
  margin: 0 0 8px;
  font-size: 28px;
  font-weight: 700;
  color: #ffffff;
}

.page-header p {
  margin: 0;
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
}

.status-badge {
  flex-shrink: 0;
  padding: 6px 14px;
  color: #166534;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
}

.status-badge.returned {
  color: #c2410c;
}

.status-badge.submitted {
  color: #2563eb;
}

.appraisal-card,
.rating-card {
  padding: 24px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.rating-card {
  margin-top: 20px;
}

.appraisal-header,
.appraisal-upload-row,
.appraisal-file-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.appraisal-header {
  justify-content: space-between;
  margin-bottom: 18px;
}

.appraisal-header h3 {
  margin: 0;
  color: #1e293b;
  font-size: 18px;
}

.appraisal-header p,
.appraisal-empty {
  margin: 5px 0 0;
  color: #64748b;
  font-size: 13px;
}

.submission-window-tip {
  margin-top: 10px;
  padding: 10px 14px;
  border-radius: 8px;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  color: #b45309;
  font-size: 13px;
}

.resource-link,
.appraisal-upload-link,
.appraisal-submit-link,
.appraisal-withdraw-link,
.appraisal-file-row button {
  color: #2563eb;
  background: none;
  border: 0;
  cursor: pointer;
  font-size: 13px;
  text-decoration: none;
}

.appraisal-upload-link,
.appraisal-submit-link,
.appraisal-withdraw-link {
  padding: 8px 14px;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  background: #eff6ff;
}

.appraisal-submit-link {
  color: #ffffff;
  background: #2563eb;
  border-color: #2563eb;
}

.appraisal-withdraw-link { color: #c2410c; border-color: #fed7aa; background: #fff7ed; }
.appraisal-withdraw-link:hover { background: #ffedd5; }
.appraisal-upload-link input {
  display: none;
}

.appraisal-upload-link.disabled,
.appraisal-submit-link:disabled,
.appraisal-withdraw-link:disabled,
.appraisal-file-row button:disabled {
  color: #94a3b8;
  background: #f8fafc;
  border-color: #e2e8f0;
  cursor: not-allowed;
}

.appraisal-select {
  width: 320px;
  min-width: 320px;
}

.appraisal-select :deep(.el-select__wrapper) {
  padding: 8px 10px;
  min-height: 36px;
  color: #334155;
  background: #ffffff;
  border: 1px solid #dbe3ef;
  border-radius: 6px;
  box-shadow: none;
}

.appraisal-select :deep(.el-select__wrapper.is-disabled) {
  color: #94a3b8;
  background: #f8fafc;
  cursor: not-allowed;
}

.company-rating-list {
  display: grid;
  gap: 10px;
  padding: 16px;
  background: #fffbf2;
  border: 1px solid #fde3b2;
  border-radius: 8px;
}

.company-rating-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  color: #7c4a03;
}

.company-rating-heading span,
.company-rating-locked,
.company-rating-warning {
  color: #64748b;
  font-size: 13px;
}

.company-rating-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 12px;
  background: #ffffff;
  border-radius: 6px;
}

.company-rating-warning {
  padding: 12px;
  color: #c2410c;
  background: #fff7ed;
  border-radius: 6px;
}

.appraisal-files {
  display: grid;
  gap: 8px;
  margin-top: 18px;
}

.appraisal-file-row {
  justify-content: space-between;
  padding: 10px 12px;
  color: #334155;
  background: #f8fafc;
  border-radius: 6px;
  font-size: 13px;
}

.appraisal-file-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.appraisal-file-company {
  margin-left: auto;
  color: #64748b;
}

@media screen and (max-width: 720px) {
  .page-header,
  .appraisal-header,
  .appraisal-upload-row,
  .company-rating-heading,
  .company-rating-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .appraisal-select {
    width: 100%;
    min-width: 0;
  }

  .appraisal-upload-link,
  .appraisal-submit-link {
    box-sizing: border-box;
    width: 100%;
    text-align: center;
  }
}
</style>
