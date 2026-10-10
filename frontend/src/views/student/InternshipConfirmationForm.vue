<template>
  <div class="confirmation-page">
    <header class="page-header">
      <div>
        <h2>实习确认</h2>
        <p>选择并确认适合自己的实习岗位，系统将保留完整的实习记录</p>
      </div>
    </header>

    <!-- 待企业确认状态提示 -->
    <section v-if="pendingConfirmation" class="pending-card">
      <div class="pending-icon">
        <el-icon :size="24"><Clock /></el-icon>
      </div>
      <div class="pending-content">
        <h3>等待企业确认中</h3>
        <p>您已提交「{{ pendingConfirmation.companyName }} · {{ pendingConfirmation.positionName }}」的实习确认，企业将在审核通过后为您生效</p>
        <div v-if="pendingConfirmation.submitTime" class="pending-meta">
          提交时间：{{ formatDateTime(pendingConfirmation.submitTime) }}
        </div>
      </div>
      <el-button type="danger" plain size="small" @click="openRecallDialog(pendingConfirmation)">撤回申请</el-button>
    </section>

    <section v-if="!pendingConfirmation" class="offer-card">
      <div class="section-heading">
        <div>
          <h3>可确认的实习</h3>
          <p v-if="!offersLoading && offers.length === 0">暂无待确认的 Offer，请前往职位浏览页面申请岗位</p>
        </div>
        <el-tag v-if="!offersLoading" type="info" effect="plain">共 {{ offers.length }} 个</el-tag>
      </div>

      <div v-if="offersLoading" class="loading-state">
        <el-icon class="is-loading" :size="24"><Loading /></el-icon>
        <span>正在加载 Offer 列表...</span>
      </div>

      <div v-else-if="offers.length === 0" class="empty-state">
        <el-icon :size="48"><Document /></el-icon>
        <p>当前没有可确认的 Offer</p>
        <el-button type="primary" @click="browseJobs">去申请岗位</el-button>
      </div>

      <div v-else class="offer-grid">
        <button
          v-for="offer in offers"
          :key="offer.offerId"
          type="button"
          :class="['offer-item', { selected: String(offer.offerId) === selectedOfferId }]"
          @click="selectedOfferId = String(offer.offerId)"
        >
          <span class="offer-radio" aria-hidden="true"></span>
          <span class="offer-copy">
            <strong>{{ offer.companyName || '未命名企业' }}</strong>
            <span>{{ offer.positionName || '未命名岗位' }}</span>
            <small v-if="offer.companyAddress">📍 {{ offer.companyAddress }}</small>
            <small v-if="offer.offerTime">📅 Offer时间：{{ formatDate(offer.offerTime) }}</small>
          </span>
          <el-tag v-if="offer.directHire" type="warning" size="small" effect="dark">直签</el-tag>
          <el-tag v-else-if="offer.interviewTime" type="success" size="small">面试通过</el-tag>
          <el-tag v-else type="info" size="small">可确认</el-tag>
        </button>
      </div>

      <div v-if="selectedOffer" class="confirmation-box">
        <div class="selected-summary">
          <span>已选择</span>
          <strong>{{ selectedOffer.companyName }} · {{ selectedOffer.positionName }}</strong>
        </div>
        <el-input
          v-model="confirmationRemark"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="可填写补充说明（选填）"
        />
        <div class="agreement-field">
          <label><span class="required-mark">*</span>盖章实习协议</label>
          <InternshipAgreementUpload ref="agreementUploader" v-model="agreementFiles" :disabled="submitting" :company-key="String(selectedOffer.offerId)" />
        </div>
        <el-button type="primary" :loading="submitting" @click="confirmSelectedOffer">
          确认该实习
        </el-button>
      </div>
    </section>

    <section class="current-card">
      <div class="section-heading">
        <div>
          <h3>当前实习</h3>
        </div>
        <el-tag v-if="currentPlacement" :type="currentStatusTagType(currentPlacement)" effect="dark">
          {{ placementStatusText(currentPlacement) }}
        </el-tag>
        <el-tag v-else type="info" effect="plain">暂无</el-tag>
      </div>
      <div v-if="currentPlacement" class="current-content">
        <img
          v-if="currentPlacement.companyLogo"
          :src="currentPlacement.companyLogo"
          class="company-mark company-logo"
          alt="企业Logo"
          @error="handleCompanyLogoError"
        />
        <div v-else class="company-mark">{{ companyInitial(currentPlacement.companyName) }}</div>
        <div class="placement-main">
          <strong>{{ currentPlacement.companyName || '未命名企业' }}</strong>
          <span>{{ currentPlacement.positionName || '未命名岗位' }}</span>
        </div>
      </div>
      <div v-else class="empty-current">
        <el-icon :size="36"><OfficeBuilding /></el-icon>
        <p>您目前没有进行中的实习</p>
      </div>
    </section>

    <section class="history-card">
      <div class="section-heading">
        <div>
          <h3>实习确认记录</h3>
        </div>
        <el-tag v-if="history.length" type="info" effect="plain">共 {{ history.length }} 条</el-tag>
      </div>

      <div v-if="historyLoading" class="loading-state">
        <el-icon class="is-loading" :size="20"><Loading /></el-icon>
        <span>加载记录中...</span>
      </div>

      <div v-else-if="history.length === 0" class="empty-state small">
        <el-icon :size="36"><Document /></el-icon>
        <p>暂无实习确认记录</p>
      </div>

      <div v-else class="timeline">
        <article
          v-for="record in history"
          :key="record.id"
          :data-confirmation-id="record.id"
          :class="['timeline-item', { highlighted: String(record.id) === highlightedConfirmationId }]"
        >
          <div class="timeline-dot" :class="timelineDotClass(record)"></div>
          <div class="timeline-content">
            <div class="timeline-header">
              <div>
                <strong>{{ record.companyName || '未命名企业' }}</strong>
                <span>{{ record.positionName || '未命名岗位' }}</span>
              </div>
              <el-tag :type="historyStatusType(record)" size="small">{{ historyStatusText(record) }}</el-tag>
            </div>
            <div class="timeline-meta">
              <span>确认时间：{{ formatDateTime(record.createTime) }}</span>
            </div>
            <p v-if="record.endReason || record.rejectionReason || record.recallReason" class="record-reason">
              {{ record.endReason || record.rejectionReason || record.recallReason }}
            </p>
            <el-button v-if="record.agreementSourceId && (record.recallStatus != null || record.status === 1 || record.status === 2)" link type="primary" @click="openAgreement(record)">实习协议</el-button>
          </div>
        </article>
      </div>
    </section>

    <InternshipAgreementDialog v-if="agreementTarget" v-model="agreementVisible"
      :source-type="agreementTarget.sourceType" :source-id="agreementTarget.sourceId" />
    <!-- 撤回确认对话框 -->
    <el-dialog
      v-model="showRecallDialog"
      title="撤回实习确认"
      width="480px"
      :close-on-click-modal="false"
    >
      <div v-if="recallTarget" class="recall-content">
        <el-alert type="warning" :closable="false" show-icon>
          <template #title>
            确认撤回「{{ recallTarget.companyName }} · {{ recallTarget.positionName }}」的实习确认申请？
          </template>
        </el-alert>
        <el-input
          v-model="recallReason"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          placeholder="请填写撤回原因（必填）"
          style="margin-top: 16px"
        />
      </div>
      <template #footer>
        <el-button @click="showRecallDialog = false">取消</el-button>
        <el-button type="danger" :loading="recalling" @click="submitRecall">确认撤回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { useAgentPageContext } from '@/agent/useAgentPageContext'
import { useFormContext } from '@/agent/formContext'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ElMessageBox } from 'element-plus'
import {
  Clock,
  Loading,
  Document,
  OfficeBuilding
} from '@element-plus/icons-vue'
import request from '@/utils/request'
import InternshipAgreementUpload from '@/components/student/InternshipAgreementUpload.vue'
import InternshipAgreementDialog from '@/components/student/InternshipAgreementDialog.vue'
import type { AgreementSource, AgreementUploadFile } from '@/api/internshipAgreements'

interface InternshipOffer {
  offerId: string | number
  invitationId?: string | number | null
  applicationId?: string | number | null
  companyId?: string | number | null
  positionId?: string | number | null
  companyName?: string
  positionName?: string
  companyAddress?: string
  companyPhone?: string
  interviewTime?: string
  offerTime?: string
  directHire?: boolean
}

interface PlacementRecord {
  agreementSourceType?: AgreementSource
  agreementSourceId?: number
  id?: string | number
  placementId?: string | number
  companyName?: string
  companyLogo?: string
  positionName?: string
  internshipStartTime?: string
  internshipEndTime?: string
  lifecycleStatus?: string
  status?: string | number
  statusText?: string
  confirmationStatus?: string
  isCurrent?: boolean
  confirmedAt?: string
  createTime?: string
  updateTime?: string
  endReason?: string
  rejectionReason?: string
  recallReason?: string
  recallStatus?: number
  submitTime?: string
}

const route = useRoute()
const router = useRouter()
const offers = ref<InternshipOffer[]>([])
const history = ref<PlacementRecord[]>([])
const currentPlacement = ref<PlacementRecord | null>(null)
const pendingConfirmation = ref<PlacementRecord | null>(null)
const selectedOfferId = ref('')
const confirmationRemark = ref('')
const agreementFiles = ref<AgreementUploadFile[]>([])
const agreementUploader = ref<InstanceType<typeof InternshipAgreementUpload> | null>(null)
const agreementVisible = ref(false)
const agreementTarget = ref<{ sourceType: AgreementSource; sourceId: number } | null>(null)
const openAgreement = (record: PlacementRecord) => {
  if (!record.agreementSourceId || !record.agreementSourceType) return
  agreementTarget.value = { sourceType: record.agreementSourceType, sourceId: record.agreementSourceId }
  agreementVisible.value = true
}
const submitting = ref(false)
const highlightedConfirmationId = ref('')
const offersLoading = ref(false)
const historyLoading = ref(false)

// 撤回相关
const showRecallDialog = ref(false)
const recallReason = ref('')
const recalling = ref(false)
const recallTarget = ref<PlacementRecord | null>(null)

const selectedOffer = computed(() => (
  offers.value.find(offer => String(offer.offerId) === selectedOfferId.value) || null
))

const formatDate = (value?: string | null, emptyText = '未设置') => {
  if (!value) return emptyText
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).slice(0, 10) || emptyText
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

const formatDateTime = (value?: string | null) => {
  if (!value) return '未记录'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const companyInitial = (name?: string) => (name || '企').trim().slice(0, 1)

// 企业Logo加载失败时回退到默认首字头像
const handleCompanyLogoError = () => {
  if (currentPlacement.value) currentPlacement.value.companyLogo = ''
}

const lifecycleText: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_CONFIRMATION: '待企业确认',
  CONFIRMED: '已确认',
  IN_PROGRESS: '实习中',
  COMPLETED: '已结束',
  REPLACED: '已更换岗位',
  ENDED: '已结束',
  INTERRUPTED: '已中断',
  REJECTED: '企业已拒绝',
  RECALLED: '已撤回',
  WITHDRAWN: '学生已撤回',
  HISTORICAL: '历史记录'
}

const placementStatusText = (record: PlacementRecord) => {
  const status = String(record.lifecycleStatus || record.status || '').toUpperCase()
  return record.statusText || lifecycleText[status] || '当前实习'
}

const currentStatusTagType = (record: PlacementRecord) => {
  const status = String(record.lifecycleStatus || record.status || '').toUpperCase()
  if (['IN_PROGRESS', 'CURRENT'].includes(status)) return 'success'
  if (['COMPLETED', 'ENDED'].includes(status)) return 'info'
  if (['INTERRUPTED', 'REJECTED'].includes(status)) return 'danger'
  return 'warning'
}

const historyStatusText = (record: PlacementRecord) => {
  if (record.statusText) return record.statusText
  if (record.recallStatus === 2) return '已撤回'
  const lifecycle = String(record.lifecycleStatus || '').toUpperCase()
  if (lifecycleText[lifecycle]) return lifecycleText[lifecycle]
  const confirmation = String(record.confirmationStatus || '').toUpperCase()
  if (lifecycleText[confirmation]) return lifecycleText[confirmation]
  if (Number(record.status) === 0) return '待确认'
  if (Number(record.status) === 1) return record.isCurrent ? '当前实习' : '已确认'
  if (Number(record.status) === 2) return '已驳回'
  return '已记录'
}

const historyStatusType = (record: PlacementRecord) => {
  const text = historyStatusText(record)
  if (text.includes('当前') || text.includes('实习中') || text.includes('已确认')) return 'success'
  if (text.includes('驳回') || text.includes('拒绝') || text.includes('中断')) return 'danger'
  if (text.includes('待') || text.includes('撤回')) return 'warning'
  return 'info'
}

const timelineDotClass = (record: PlacementRecord) => {
  const text = historyStatusText(record)
  if (text.includes('当前') || text.includes('实习中') || text.includes('已确认')) return 'dot-success'
  if (text.includes('驳回') || text.includes('拒绝')) return 'dot-danger'
  if (text.includes('撤回')) return 'dot-warning'
  return ''
}

const fetchOffers = async () => {
  offersLoading.value = true
  try {
    const response: any = await request.get('/student/internship-confirmation/offers')
    const data = response?.data?.offers ?? response?.data ?? []
    offers.value = Array.isArray(data)
      ? data.filter((offer: InternshipOffer) => offer?.offerId !== null && offer?.offerId !== undefined)
      : []
  } catch (error) {
    console.error('获取待确认 offer 失败:', error)
    offers.value = []
    ElMessage.error('获取 Offer 列表失败，请刷新重试')
  } finally {
    offersLoading.value = false
  }
}

const fetchHistory = async () => {
  historyLoading.value = true
  try {
    const response: any = await request.get('/student/internship-confirmation/history')
    const records = Array.isArray(response?.data) ? response.data : []
    history.value = [...records].sort((left, right) => {
      const leftTime = new Date(left.createTime || 0).getTime()
      const rightTime = new Date(right.createTime || 0).getTime()
      return rightTime - leftTime
    })
  } catch (error) {
    console.error('获取实习确认记录失败:', error)
    history.value = []
  } finally {
    historyLoading.value = false
  }
}

const fetchCurrentPlacement = async () => {
  try {
    const response: any = await request.get('/student/internship-confirmation')
    if (response?.code !== 200) {
      currentPlacement.value = null
      pendingConfirmation.value = null
      return
    }
    const data = response.data
    currentPlacement.value = data && (data.companyId || data.positionId || data.companyName)
      ? { ...data, lifecycleStatus: 'IN_PROGRESS' }
      : null

    // 检查是否有待企业确认的记录，从历史记录或offer列表中获取名称
    if (data?.pendingConfirmationId) {
      const pendingId = String(data.pendingConfirmationId)
      // 优先从历史记录中查找
      const historyRecord = history.value.find(h => String(h.id) === pendingId)
      if (historyRecord) {
        pendingConfirmation.value = {
          id: historyRecord.id,
          companyName: historyRecord.companyName || '未知企业',
          positionName: historyRecord.positionName || '未知岗位',
          submitTime: historyRecord.createTime
        }
      } else {
        // 从offer列表中根据companyId和positionId查找
        const matchedOffer = offers.value.find(o =>
          String(o.companyId) === String(data.pendingCompanyId) &&
          String(o.positionId) === String(data.pendingPositionId)
        )
        pendingConfirmation.value = {
          id: data.pendingConfirmationId,
          companyName: matchedOffer?.companyName || '未知企业',
          positionName: matchedOffer?.positionName || '未知岗位',
          submitTime: null
        }
      }
    } else {
      pendingConfirmation.value = null
    }
  } catch (error) {
    console.error('获取当前实习失败:', error)
    currentPlacement.value = null
    pendingConfirmation.value = null
  }
}

const browseJobs = () => router.push('/student/jobs')

const clearQuery = async (key: string) => {
  const query = { ...route.query }
  delete query[key]
  await router.replace({ path: route.path, query })
}

const handleDeepLinks = async () => {
  const offerId = Array.isArray(route.query.offerId) ? route.query.offerId[0] : route.query.offerId
  if (offerId) {
    const matched = offers.value.find(offer => String(offer.offerId) === String(offerId))
    if (matched) {
      selectedOfferId.value = String(matched.offerId)
    } else {
      ElMessage.warning('该 Offer 已不可确认，已为您显示当前可选列表')
      await clearQuery('offerId')
    }
  }

  const confirmationId = Array.isArray(route.query.confirmationId)
    ? route.query.confirmationId[0]
    : route.query.confirmationId
  if (confirmationId) {
    const record = history.value.find(item => String(item.id) === String(confirmationId))
    if (!record) {
      ElMessage.warning('未找到该确认记录，已为您显示完整历史')
      await clearQuery('confirmationId')
      return
    }
    highlightedConfirmationId.value = String(record.id)
    await nextTick()
    document.querySelector(`[data-confirmation-id="${String(record.id)}"]`)
      ?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}

const confirmSelectedOffer = async () => {
  const offer = selectedOffer.value
  if (!offer) {
    ElMessage.warning('请先选择要确认的 Offer')
    return
  }
  if (pendingConfirmation.value) {
    ElMessage.warning('您已有待企业确认的申请，请先等待企业处理或撤回后再提交新的确认')
    return
  }
  if (!agreementUploader.value?.validate()) return
  try {
    await ElMessageBox.confirm(
      `确认选择「${offer.companyName || '该企业'} · ${offer.positionName || '该岗位'}」作为新的实习吗？`,
      '确认实习',
      {
        confirmButtonText: '确认提交',
        cancelButtonText: '再看看',
        type: 'warning'
      }
    )
    submitting.value = true
    const operation = formContext.beginFormOperation('confirmation:remark')
    const response: any = await request.post('/student/internship-confirmation', {
      offerId: offer.offerId,
      remark: confirmationRemark.value.trim(),
      agreementFileIds: agreementFiles.value.map(file => file.uploadId)
    })
    if (response.code !== 200) throw new Error(response.message || '确认失败')
    agreementUploader.value?.markCommitted()
    operation.complete({ status: 'submitted', label: '实习确认已提交，等待企业审核', url: '/student/internship-confirmation-form' })
    ElMessage.success('实习确认已提交，等待企业审核')
    selectedOfferId.value = ''
    confirmationRemark.value = ''
    if (route.query.offerId) await clearQuery('offerId')
    await Promise.all([fetchOffers(), fetchHistory()])
    await fetchCurrentPlacement()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.message || '实习确认失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

const openRecallDialog = (record: PlacementRecord) => {
  recallTarget.value = record
  recallReason.value = ''
  showRecallDialog.value = true
}

const submitRecall = async () => {
  if (!recallReason.value.trim()) {
    ElMessage.warning('请填写撤回原因')
    return
  }
  if (!recallTarget.value?.id) {
    ElMessage.error('记录不存在')
    return
  }
  try {
    recalling.value = true
    const operation = formContext.beginFormOperation('confirmation:recall')
    const response: any = await request.post(
      `/student/internship-confirmation/${recallTarget.value.id}/recall`,
      { recallReason: recallReason.value.trim() }
    )
    if (response.code !== 200) throw new Error(response.message || '撤回失败')
    operation.complete({ status: 'submitted', label: '实习确认申请已撤回', url: '/student/internship-confirmation-form' })
    ElMessage.success('已成功撤回实习确认申请')
    showRecallDialog.value = false
    recallTarget.value = null
    recallReason.value = ''
    pendingConfirmation.value = null
    await Promise.all([fetchOffers(), fetchHistory()])
    // 撤回的只是待确认申请；重新读取真实当前实习，不能清空已有的自主实习岗位。
    await fetchCurrentPlacement()

  } catch (error: any) {
    ElMessage.error(error?.message || '撤回失败，请稍后重试')
  } finally {
    recalling.value = false
  }
}

onMounted(async () => {
  await Promise.all([fetchOffers(), fetchHistory()])
  await fetchCurrentPlacement()
  await handleDeepLinks()
})

watch(
  () => [route.query.offerId, route.query.confirmationId],
  () => handleDeepLinks()
)

useAgentPageContext(() => ({
  title: '实习确认',
  view: showRecallDialog.value && recallTarget.value ? 'recall-dialog' : pendingConfirmation.value ? 'pending-confirmation' : 'page',
  object: showRecallDialog.value && recallTarget.value?.id ? { type: 'confirmation', id: String(recallTarget.value.id) }
    : pendingConfirmation.value?.id ? { type: 'confirmation', id: String(pendingConfirmation.value.id) }
    : selectedOfferId.value ? { type: 'offer', id: selectedOfferId.value } : null,
  filters: {}
}))

// ===== 智能填表：补充说明（页面）与撤回原因（弹窗）注册到 AI 侧栏 =====
// 单例同一时刻只关联一个表单：撤回弹窗打开时优先注册撤回原因；关闭后若已选 Offer 则注册补充说明
const formContext = useFormContext()
const confirmProxy = {
  get agreementFileIds() { return agreementFiles.value.filter(file => file.status === 'success').map(file => file.uploadId) },
  get remark() { return confirmationRemark.value },
  set remark(v: unknown) { confirmationRemark.value = String(v ?? '') }
}
const recallProxy = {
  get recallReason() { return recallReason.value },
  set recallReason(v: unknown) { recallReason.value = String(v ?? '') }
}
watch([showRecallDialog, selectedOfferId], ([recallOpen, offerId]) => {
  if (recallOpen) {
    formContext.register({
      formId: 'confirmation:recall',
      title: '撤回实习确认',
      fields: [{ prop: 'recallReason', label: '撤回原因', type: 'textarea', required: true }],
      getModel: () => recallProxy,
      getObject: () => recallTarget.value?.id ? { type: 'confirmation', id: String(recallTarget.value.id) } : null,
      getValidationSummary: () => ({ missingFields: [], validationErrors: recallTarget.value?.id ? [] : ['待撤回的确认记录不存在'] })
    })
  } else if (offerId) {
    formContext.register({
      formId: 'confirmation:remark',
      title: '实习确认补充说明',
      fields: [{ prop: 'remark', label: '补充说明', type: 'textarea' },
        { prop: 'agreementFileIds', label: '盖章实习协议', type: 'upload', required: true, patternHint: '支持多个Word或PDF文件，单个不超过20MB，请手动上传已盖章协议' }],
      getModel: () => confirmProxy,
      getObject: () => selectedOfferId.value ? { type: 'offer', id: selectedOfferId.value } : null,
      getValidationSummary: () => ({ missingFields: selectedOffer.value ? [] : ['待确认的Offer'], validationErrors: pendingConfirmation.value ? ['已有待企业确认的申请，请先等待企业处理或撤回后再提交'] : [] })
    })
  } else {
    formContext.unregister('confirmation:recall')
    formContext.unregister('confirmation:remark')
  }
})
onUnmounted(() => { formContext.unregister('confirmation:recall'); formContext.unregister('confirmation:remark') })
</script>

<style scoped>
.agreement-field { width: 100%; margin: 16px 0; }
.agreement-field > label { display: block; margin-bottom: 10px; color: #606266; font-size: 14px; }
.required-mark { margin-right: 4px; color: #f56c6c; }
.confirmation-page {
  display: grid;
  gap: 20px;
  width: 100%;
  color: #1e293b;
}

.page-header,
.current-card,
.offer-card,
.history-card,
.pending-card {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 32px;
  overflow: hidden;
  position: relative;
  color: #ffffff;
  border: 0;
  border-radius: 12px;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
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

.page-header h2,
.section-heading h3 {
  margin: 0;
}

.page-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: #ffffff;
  margin: 0 0 8px 0;
  position: relative;
  z-index: 1;
}

.page-header p,
.section-heading p {
  margin: 7px 0 0;
}

.page-header p {
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
}

.pending-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  border-color: #f59e0b;
  background: linear-gradient(135deg, #fffbeb 0%, #fff7ed 100%);
  border-radius: 12px;
}

.pending-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: #fef3c7;
  color: #f59e0b;
  flex-shrink: 0;
}

.pending-content {
  flex: 1;
}

.pending-content h3 {
  margin: 0 0 4px;
  font-size: 16px;
  color: #92400e;
}

.pending-content p {
  margin: 0;
  color: #78350f;
  font-size: 13px;
  line-height: 1.5;
}

.pending-meta {
  margin-top: 6px;
  font-size: 12px;
  color: #92400e;
}

.current-card,
.offer-card,
.history-card {
  padding: 22px 24px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.section-heading h3 {
  font-size: 18px;
  color: #1e293b;
}

.section-heading p {
  color: #64748b;
  font-size: 13px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px 20px;
  color: #64748b;
  font-size: 14px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px 20px;
  color: #64748b;
}

.empty-state.small {
  padding: 24px 20px;
}

.empty-state p {
  margin: 0;
  font-size: 14px;
}

.empty-current {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 24px 20px;
  color: #94a3b8;
}

.empty-current p {
  margin: 0;
  font-size: 14px;
}

.current-content {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 18px;
  border: 1px solid #bbf7d0;
  border-radius: 12px;
  background: #f0fdf4;
}

.company-mark {
  display: grid;
  flex: 0 0 46px;
  width: 46px;
  height: 46px;
  place-items: center;
  color: #ffffff;
  border-radius: 12px;
  background: #16a34a;
  font-size: 20px;
  font-weight: 700;
}

.company-logo {
  object-fit: cover;
  border: 1px solid #bbf7d0;
  background: #ffffff;
}

.placement-main {
  display: grid;
  gap: 4px;
  flex: 1;
}

.placement-main span {
  color: #64748b;
  font-size: 13px;
}

.offer-grid {
  display: grid;
  gap: 10px;
}

.offer-item {
  display: flex;
  align-items: center;
  gap: 13px;
  width: 100%;
  padding: 15px 16px;
  color: inherit;
  text-align: left;
  border: 1px solid #dbe3ef;
  border-radius: 10px;
  background: #ffffff;
  cursor: pointer;
  transition: 0.2s ease;
}

.offer-item:hover,
.offer-item.selected {
  border-color: #2563eb;
  background: #eff6ff;
}

.offer-radio {
  width: 16px;
  height: 16px;
  border: 2px solid #94a3b8;
  border-radius: 50%;
  box-shadow: inset 0 0 0 3px #ffffff;
  flex-shrink: 0;
}

.offer-item.selected .offer-radio {
  border-color: #2563eb;
  background: #2563eb;
}

.offer-copy {
  display: grid;
  flex: 1;
  gap: 3px;
}

.offer-copy span,
.offer-copy small {
  color: #64748b;
}

.offer-copy small {
  font-size: 12px;
}

.confirmation-box {
  display: grid;
  gap: 13px;
  margin-top: 16px;
  padding: 18px;
  border-radius: 10px;
  background: #f8fafc;
}

.selected-summary {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  font-size: 14px;
}

.selected-summary span {
  color: #64748b;
}

.timeline {
  display: grid;
  gap: 0;
}

.timeline-item {
  position: relative;
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 10px;
  padding: 0 0 20px;
}

.timeline-item:not(:last-child)::before {
  content: '';
  position: absolute;
  top: 12px;
  bottom: -2px;
  left: 7px;
  width: 2px;
  background: #dbe3ef;
}

.timeline-dot {
  z-index: 1;
  width: 16px;
  height: 16px;
  margin-top: 14px;
  border: 4px solid #dbeafe;
  border-radius: 50%;
  background: #2563eb;
}

.timeline-dot.dot-success {
  border-color: #bbf7d0;
  background: #16a34a;
}

.timeline-dot.dot-danger {
  border-color: #fecaca;
  background: #dc2626;
}

.timeline-dot.dot-warning {
  border-color: #fef3c7;
  background: #f59e0b;
}

.timeline-content {
  padding: 15px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #ffffff;
}

.timeline-item.highlighted .timeline-content {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.timeline-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.timeline-header > div {
  display: grid;
  gap: 4px;
}

.timeline-header span,
.timeline-meta,
.record-reason {
  color: #64748b;
  font-size: 13px;
}

.timeline-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin-top: 12px;
}

.record-reason {
  margin: 10px 0 0;
  padding: 9px 11px;
  border-radius: 7px;
  background: #f8fafc;
  color: #475569;
  border-left: 3px solid #cbd5e1;
}

.recall-content {
  padding: 0 8px;
}

@media (max-width: 860px) {
  .pending-card,
  .current-content,
  .page-header,
  .timeline-header,
  .selected-summary {
    align-items: flex-start;
    flex-direction: column;
  }

}

@media (max-width: 520px) {
  .page-header h2 {
    font-size: 24px;
  }

  .current-card,
  .offer-card,
  .history-card {
    padding: 18px;
  }
}
</style>
