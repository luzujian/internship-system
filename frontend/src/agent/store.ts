import { computed, nextTick, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import request, { getAccessToken } from '@/utils/request'
import type { AxiosRequestConfig } from 'axios'
import { responseOperation, sseParser, terminal } from './protocol.mjs'
import { currentForm, captureValues, applyAssignments, undoLast, formBusinessReceipt, getFormFields, getFormValidation, validateForm, setFormSession } from './formContext'
import type { AgentTask, Capabilities, FillEvent, PageContext } from './types'

type SilentRequestConfig = AxiosRequestConfig & { skipErrorMessage: boolean }

/** 非安全上下文（如 http 移动端）无 crypto.randomUUID 时的等价回退 */
function newRequestId(): string {
  try {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  } catch { /* 回退到本地生成 */
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.floor(Math.random() * 16)
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

export const useAgentStore = defineStore('businessAgent', () => {
  const studentLoginAutoOpenKey = 'deepintern.agent.auto-open-student-login'
  const visible = ref(false)
  const studentLoginAutoOpenJourney = ref(false)
  const capabilities = ref<Capabilities | null>(null)
  const tasks = ref<AgentTask[]>([])
  const fillEvents = ref<FillEvent[]>([])
  const draft = ref('')
  const error = ref('')
  const loading = ref(false)
  const sending = ref(false)
  const filling = ref(false)
  const sessionId = ref<string | null>(null)
  const page = ref<Partial<PageContext> & { route: string }>({ route: '' })
  const revision = ref(0)
  let pageOwner: symbol | null = null
  let epoch = 0
  let stream: AbortController | null = null
  let pending: Record<string, unknown> | null = null
  interface PendingBubble { clientRequestId: string; message: string; createdAt: number; state: string; error?: string }
  const pendingMessage = ref<PendingBubble | null>(null)
  const cancellingIds = ref<string[]>([])
  const draftConflict = ref<string | null>(null)
  const autoOpenedReminders = new Set<string>()
  let fillController: AbortController | null = null
  let fillEpoch = 0
  let cancelAfterSend = false
  const draftKey = () => `deepintern.agent.draft:${capabilities.value?.actor.ownerKey ?? 'anon'}:${sessionId.value ?? 'new'}`
  const draftRevision = new Map<string, number>()
  const draftWrites = new Map<string, Promise<void>>()
  let draftSaveTimer = 0
  let restoringDraft = false
  const persistent = computed(() => Boolean((capabilities.value as any)?.persistenceAvailable))
  function persistDraft(key = draftKey(), text = draft.value) {
    try {
      if (text) localStorage.setItem(key, text.slice(0, 4000))
      else localStorage.removeItem(key)
    } catch { /* ignore */ }
  }
  function saveRemoteDraft(key: string, text: string, owner: string, id: string) {
    if (!persistent.value || owner !== capabilities.value?.actor.ownerKey) return
    const prior = draftWrites.get(key) || Promise.resolve()
    const next = prior.then(async () => {
      if (owner !== capabilities.value?.actor.ownerKey) return
      if (key === draftKey() && draftConflict.value !== null) return
      try {
        if (!draftRevision.has(key)) {
          const current: any = await request.get('/agent/draft', { params: { draftKey: id } })
          draftRevision.set(key, current.data?.revision ?? 0)
          if (current.data?.text && current.data.text !== text) {
            if (key === draftKey()) draftConflict.value = current.data.text
            return
          }
        }
        const response: any = await request.put('/agent/draft', { draftKey: id, text, expectedRevision: draftRevision.get(key) ?? 0 })
        draftRevision.set(key, response.data?.revision ?? 0)
      } catch (e: any) {
        if (e.response?.status === 409) {
          const latest: any = await request.get('/agent/draft', { params: { draftKey: id } }).catch(() => null)
          if (latest?.data) {
            draftRevision.set(key, latest.data.revision)
            if (key === draftKey()) draftConflict.value = latest.data.text || ''
          }
        }
        // The local draft remains available even if durable storage is offline.
      }
    })
    draftWrites.set(key, next)
  }
  function schedulePersistDraft() {
    if (restoringDraft) return
    const key = draftKey(), text = draft.value, owner = capabilities.value?.actor.ownerKey || '', id = sessionId.value || 'new'
    persistDraft(key, text)
    try {
      if (draftSaveTimer) window.clearTimeout(draftSaveTimer)
      draftSaveTimer = window.setTimeout(() => { draftSaveTimer = 0; saveRemoteDraft(key, text, owner, id) }, 500)
    } catch { /* ignore */ }
  }
  function flushDraft() {
    if (draftSaveTimer) window.clearTimeout(draftSaveTimer)
    draftSaveTimer = 0
    persistDraft()
    saveRemoteDraft(draftKey(), draft.value, capabilities.value?.actor.ownerKey || '', sessionId.value || 'new')
  }
  function restoreDraft(force = false) {
    const key = draftKey(), mine = epoch
    try {
      if (force || !draft.value) {
        restoringDraft = true
        draft.value = (localStorage.getItem(key) || '').slice(0, 4000)
        restoringDraft = false
      }
    } catch { /* ignore */ }
    if (persistent.value) void request.get('/agent/draft', { params: { draftKey: sessionId.value || 'new' } }).then((response: any) => {
      if (mine !== epoch || key !== draftKey()) return
      draftRevision.set(key, response.data?.revision ?? 0)
      const remote = response.data?.text || ''
      if (!draft.value && remote) { restoringDraft = true; draft.value = remote; restoringDraft = false; persistDraft() }
      else if (remote && remote !== draft.value) draftConflict.value = remote
    }).catch(() => {})
  }
  function resolveDraftConflict(useRemote: boolean) {
    if (useRemote && draftConflict.value !== null) draft.value = draftConflict.value
    draftConflict.value = null; flushDraft()
  }
  function clearDraftCache(ownerKey?: string | null) {
    try {
      const owner = ownerKey ?? capabilities.value?.actor.ownerKey
      if (!owner) return
      const prefix = `deepintern.agent.draft:${owner}:`
      for (let i = localStorage.length - 1; i >= 0; i--) {
        const k = localStorage.key(i)
        if (k && k.startsWith(prefix)) localStorage.removeItem(k)
      }
    } catch { /* ignore */ }
  }
  watch(draft, () => { schedulePersistDraft() }, { flush: 'sync' })
  const busy = computed(() => sending.value || filling.value || tasks.value.some(task => !terminal(task.status)) || autoFlow.value.phase === 'previewing' || capsFlow.value.phase === 'previewing')
  const operationWorking = computed(() => autoFlow.value.working || capsFlow.value.working || applyDialog.value.loading)
  const storageKey = () => `deepintern.agent.session:${capabilities.value?.actor.ownerKey}`
  const activityEvents = ref<any[]>([])
  const currentPlan = ref<any>(null)
  const planStale = ref(false)
  const planSaving = ref(false)
  const planError = ref('')
  let planRequest = 0
  async function refreshPlan() {
    if (!capabilities.value?.enabled || capabilities.value.actor.role !== 'ROLE_STUDENT' || !capabilities.value.capabilities?.internship_plan || !capabilities.value.capabilities?.agent_chat) { currentPlan.value = null; return }
    const mine = epoch, owner = capabilities.value.actor.ownerKey, requestId = ++planRequest
    try {
      const response: any = await request.get('/agent/student-plan/journey')
      if (mine !== epoch || owner !== capabilities.value?.actor.ownerKey || requestId !== planRequest) return
      currentPlan.value = response.data; planStale.value = false
      if (response.data?.autoOpen && !capabilities.value?.actor.readOnly) {
        visible.value = true
        void savePlanPreference(null)
      }
    } catch (e: any) {
      if (mine !== epoch || owner !== capabilities.value?.actor.ownerKey || requestId !== planRequest) return
      planStale.value = true
      if (e.response?.status === 403) {
        currentPlan.value = null
        if (capabilities.value?.capabilities) capabilities.value.capabilities.internship_plan = false
      }
    }
  }
  async function savePlanPreference(choice: string | null): Promise<boolean> {
    if (planSaving.value || !currentPlan.value || capabilities.value?.actor.readOnly) return false
    const mine = epoch, owner = capabilities.value?.actor.ownerKey, expectedVersion = currentPlan.value.preferenceVersion
    ++planRequest; planSaving.value = true; planError.value = ''
    try {
      const response: any = await request.put('/agent/student-plan/journey/preference', { choice, expectedVersion })
      if (mine !== epoch || owner !== capabilities.value?.actor.ownerKey) return false
      ++planRequest; currentPlan.value = response.data; planStale.value = false
      return true
    } catch (e: any) {
      if (mine === epoch && owner === capabilities.value?.actor.ownerKey) {
        planError.value = e.response?.data?.message || '指引暂未保存，请稍后重试。'
        // Refresh conflicts without automatically acknowledging the first-open flag again.
        const response: any = await request.get('/agent/student-plan/journey').catch(() => null)
        if (response && mine === epoch && owner === capabilities.value?.actor.ownerKey) currentPlan.value = response.data
      }
      return false
    } finally { if (mine === epoch) planSaving.value = false }
  }

  const eventKey = () => `deepintern.agent.events:${capabilities.value?.actor.ownerKey}:${sessionId.value || 'new'}`
  function persistEvents() {
    try { localStorage.setItem(eventKey(), JSON.stringify({ fill: fillEvents.value, activity: activityEvents.value })) } catch { /* local quota */ }
  }
  async function saveEvent(event: any, kind: string) {
    persistEvents()
    if (!persistent.value || !sessionId.value) return
    try { await request.post(`/agent/sessions/${sessionId.value}/events`, { id: event.id, kind, createdAt: event.createdAt, message: event.message || '', payload: event }) }
    catch { error.value = '记录已保留在此设备，服务器暂时未能保存。原业务结果不受影响。' }
  }
  async function restoreEvents() {
    const key = eventKey(), mine = epoch
    try {
      const saved = JSON.parse(localStorage.getItem(key) || '{}')
      fillEvents.value = saved.fill || []; activityEvents.value = saved.activity || []
    } catch { fillEvents.value = []; activityEvents.value = [] }
    if (!persistent.value || !sessionId.value) return
    try {
      const response: any = await request.get(`/agent/sessions/${sessionId.value}/events`)
      if (mine !== epoch || key !== eventKey()) return
      for (const event of Array.isArray(response.data) ? response.data : []) {
        const target = event.kind === 'FILL' ? fillEvents.value : activityEvents.value
        if (!target.some((item: any) => item.id === event.id)) target.push(event.payload)
      }
      persistEvents()
    } catch { /* local events remain visible */ }
  }
  async function ensureSession() {
    if (sessionId.value || !persistent.value) return
    const mine = epoch, owner = capabilities.value?.actor.ownerKey
    flushDraft()
    const response: any = await request.post('/agent/sessions', {})
    if (mine !== epoch || owner !== capabilities.value?.actor.ownerKey) throw new Error('当前身份或会话已切换，请重新发起。')
    sessionId.value = response.data.id; sessionStorage.setItem(storageKey(), response.data.id)
    persistDraft(); persistEvents()
  }
  function recordActivity(kind: string, message: string, title: string, detail: string, link?: any, messageAt?: number) {
    const event = { id: newRequestId(), kind, message, title, detail, link, createdAt: Date.now(), messageAt: messageAt || Date.now() }
    activityEvents.value.push(event); void saveEvent(event, kind)
  }
  watch([() => capabilities.value?.actor.ownerKey, sessionId], ([owner, id]) => setFormSession(`${owner || ''}:${id || 'new'}`), { flush: 'sync', immediate: true })
  watch(formBusinessReceipt, receipt => {
    if (!receipt) return
    const savedValues = (receipt as any).savedValues as Record<string, unknown> | undefined
    for (const event of fillEvents.value) {
      const included = savedValues && event.afterValues && Object.entries(event.afterValues).every(([key, value]) => Object.prototype.hasOwnProperty.call(savedValues, key) && JSON.stringify(savedValues[key]) === JSON.stringify(value))
      if (included && event.formId === receipt.formId && event.expectedInstanceId === receipt.instanceId && event.applied && !event.undone) {
        event.businessStatus = receipt.status; event.receipt = receipt as any; void saveEvent(event, 'FILL')
      }
    }
  })
  const formFillEnabled = computed(() => Boolean(capabilities.value?.capabilities?.form_fill))
  watch([formFillEnabled, () => capabilities.value?.enabled, () => capabilities.value?.actor.readOnly, () => capabilities.value?.actor.ownerKey, () => currentForm.value?.instanceId], (next, previous) => {
    if (filling.value && (!next[0] || !next[1] || next[2] || next[3] !== previous[3] || next[4] !== previous[4])) cancelFill()
  }, { flush: 'sync' })
  const voiceEnabled = computed(() => Boolean(capabilities.value?.capabilities?.voice_input))
  const chatEnabled = computed(() => capabilities.value?.capabilities?.agent_chat !== false)
  const planEnabled = computed(() => Boolean(capabilities.value?.capabilities?.internship_plan))
  const applyCapability = computed(() => Boolean(capabilities.value?.capabilities?.job_apply))
  /** FLOW-01：侧栏自动化任务 / 能力变更闭环的可用性闸门（只收窄，不新增业务能力）。 */
  const autoTaskEnabled = computed(() => Boolean(capabilities.value?.capabilities?.auto_task))
  const isTeacherRole = computed(() => Boolean(capabilities.value?.actor.role?.startsWith('ROLE_TEACHER')))
  const isAdminRole = computed(() => capabilities.value?.actor.role === 'ROLE_ADMIN')

  /** 投递确认弹窗状态：模型不参与决策，用户点确认后才真实执行。 */
  interface ApplyReceipt { applicationId: string; status: string; company: string; position: string; plan: any }
  const applyDialog = ref({
    open: false, positionId: '', title: '', company: '',
    loading: false, error: '', receipt: null as ApplyReceipt | null,
    // 确认卡身份：actionId 在打开确认卡时生成一次，重试/多标签页复用同一身份，避免重复投递。
    actionId: '', previewedAt: 0,
    // 超时或结果不明时置为 pending：显示“结果待核实”，先查真值而不是直接重投。
    verifyState: '' as '' | 'pending' | 'confirmed'
  })
  async function openApply(job: { id: string | number; title?: unknown; company?: unknown }) {
    if (!applyCapability.value || !capabilities.value?.enabled) {
      error.value = '当前身份未开通投递执行能力，请联系管理员。'
      return
    }
    applyDialog.value = {
      open: true, positionId: String(job.id),
      title: String(job.title ?? ''), company: String(job.company ?? ''),
      loading: false, error: '', receipt: null,
      actionId: '', previewedAt: 0, verifyState: ''
    }
    const dialog = applyDialog.value
    dialog.loading = true
    try {
      await ensureSession()
      const response: any = await request.post('/agent/job-applications/preview', { actionType: 'JOB_APPLY', objectId: String(job.id) })
      dialog.actionId = response.data.id; dialog.previewedAt = response.data.previewedAt
      dialog.title = response.data.parameters?.position || dialog.title
      dialog.company = response.data.parameters?.company || dialog.company
    } catch (e: any) { dialog.error = formatError(e) }
    finally { dialog.loading = false }
  }
  function closeApply() {
    if (applyDialog.value.loading) return
    applyDialog.value.open = false
  }
  /** 确认卡超时/结果不明：不宣布失败，也不直接重投，先查最新申请状态。 */
  async function verifyApply() {
    const dialog = applyDialog.value
    if (!dialog.open || !dialog.positionId || dialog.loading) return
    dialog.loading = true; dialog.error = ''
    try {
      const response: any = await request.get(`/agent/actions/${dialog.actionId}`)
      const action = response.data
      if (action.status === 'SUCCEEDED' && action.receipt) {
        dialog.verifyState = 'confirmed'; dialog.receipt = action.receipt
      } else if (['FAILED', 'READY', 'PREVIEWED'].includes(action.status)) {
        dialog.verifyState = ''; dialog.error = action.errorMessage || '该操作尚未完成，请核对后再决定是否提交。'
        if (action.status === 'FAILED') { dialog.actionId = ''; dialog.error += ' 请重新打开岗位确认卡。' }
        if (action.expiresAt && action.expiresAt < Date.now()) { dialog.actionId = ''; dialog.error = '确认信息已过期，请重新打开岗位确认卡。' }
      } else {
        dialog.verifyState = 'pending'; dialog.error = '执行结果仍待核实，暂不重复提交。'
      }
    } catch {
      dialog.error = '暂时无法核验投递结果，请在“我的申请”页面核对后再决定是否重试。'
    } finally { dialog.loading = false }
  }
  async function confirmApply() {
    const dialog = applyDialog.value
    if (!dialog.open || dialog.loading || !dialog.positionId || !dialog.actionId) return
    if (!applyCapability.value || capabilities.value?.actor.readOnly) { dialog.error = '当前身份已不可执行此操作，请重新核对权限。'; return }
    if (dialog.verifyState === 'pending') {
      dialog.error = '上次提交结果尚未核实，请先点击“刷新状态”确认，避免重复投递。'
      return
    }
    dialog.loading = true; dialog.error = ''; error.value = ''
    try {
      const response: any = await request.post('/agent/job-applications/apply', {
        positionId: dialog.positionId,
        clientRequestId: newRequestId(),
        // 同一确认动作沿用同一 actionId：超时重试与多标签页都会回到首次回执。
        actionId: dialog.actionId,
        previewedAt: dialog.previewedAt,
        confirmed: true
      })
      dialog.receipt = response.data
      recordActivity('ACTION', '确认投递', '已投递', `${dialog.title} · ${dialog.company}`, { action: 'OPEN_APPLICATION', objectId: String(response.data.applicationId), parameters: {} })
      dialog.verifyState = 'confirmed'
    } catch (e: any) {
      const code = e.response?.data?.errorCode
      if (['PREVIEW_EXPIRED', 'PREVIEW_REQUIRED', 'PREVIEW_INVALID', 'PREVIEW_CHANGED', 'ACTION_MISMATCH', 'ACTION_NOT_FOUND', 'ACTION_FAILED'].includes(code)) {
        dialog.error = e.response?.data?.message || '确认信息已过期，请重新打开确认卡核对后再提交。'
        // 旧确认卡失效：清空身份与预览时间，必须重新预览才可再确认。
        dialog.actionId = ''; dialog.previewedAt = 0
      } else if (['ACTION_UNKNOWN', 'ACTION_PENDING'].includes(code) || !e.response || e.code === 'ECONNABORTED' || String(e.message || '').includes('timeout')) {
        dialog.verifyState = 'pending'
        dialog.error = '投递结果待核实：请求已发出但未收到确认。请点击“刷新状态”核对，避免重复投递。'
      } else {
        dialog.error = formatError(e)
      }
    } finally {
      dialog.loading = false
    }
  }  /**
   * FLOW-01：教师自动化任务侧栏闭环（描述→追问→预览→确认→执行→记录）。
   * 复用手动页同一后端接口：preview / create / enable / run / logs，不新增业务能力。
   * 确认前只调 preview，不创建不启用；失败/无权限如实提示，不推进状态机。
   */
  let flowPreviewController: AbortController | null = null
  let flowPreviewEpoch = 0
  /** 恢复草稿防重入：会话/能力同时就绪时曾被触发两次，导致同一个草稿被并发刷新而报版本冲突。 */
  let restoreAutoInFlight = false
  const autoFlow = ref({
    phase: 'idle' as 'idle' | 'previewing' | 'need_input' | 'need_confirm' | 'creating' | 'created' | 'error',
    message: '', questions: [] as string[], draft: null as any, created: null as any, runLog: null as any, error: '', working: false,
    taskId: undefined as number | undefined,
    startedAt: 0 as number
  })
  /** 任务卡操作错误：按任务 id 归集，供自动化任务卡片直接显示，弹窗关掉也不会丢。 */
  const autoTaskErrors = ref<Record<number, { message: string; action: string }>>({})
  /** 把后端 operation 归一到卡片上的重试动作：点击“重新核对”时按它重开确认卡。 */
  function autoTaskRetryAction(operation?: string) {
    switch (operation) {
      case 'RUN': case 'run': return 'run'
      case 'RETRY': case 'retry': return 'retry'
      case 'ENABLE': case 'toggle': return 'toggle'
      default: return 'edit'
    }
  }
  function noteAutoTaskError(taskId: number | undefined, message: string, operation?: string) {
    if (!message || typeof taskId !== 'number' || !Number.isFinite(taskId)) return
    autoTaskErrors.value = { ...autoTaskErrors.value, [taskId]: { message, action: autoTaskRetryAction(operation) } }
  }
  function clearAutoTaskError(taskId: number | undefined) {
    if (typeof taskId !== 'number' || !autoTaskErrors.value[taskId]) return
    const next = { ...autoTaskErrors.value }
    delete next[taskId]
    autoTaskErrors.value = next
  }

  const autoManageSignal = ref(0)
  function resetAutoFlow() {
    ++flowPreviewEpoch; flowPreviewController?.abort(); flowPreviewController = null
    autoFlow.value = { phase: 'idle', message: '', questions: [], draft: null, created: null, runLog: null, error: '', working: false, taskId: undefined, startedAt: 0 }
  }
  function prepareAutoEdit(task: any) {
    resetAutoFlow()
    if (!task) return
    autoFlow.value.taskId = task.id
    autoFlow.value.message = '修改自动化任务：' + task.name; autoFlow.value.startedAt = Date.now()
  }
  async function dismissAutoDraft() {
    const id = autoFlow.value.draft?.draftId
    if (id) { try { await request.delete('/teacher/auto-tasks/v2/drafts/' + id) } catch (e: any) { autoFlow.value.error = formatError(e); return } }
    resetAutoFlow()
  }
  function updateAutoPreview(value: any) {
    autoFlow.value.draft = value; autoFlow.value.questions = value.questions || []
    autoFlow.value.phase = autoFlow.value.questions.length ? 'need_input' : 'need_confirm'
  }
  function acceptAutoTask(task: any, sentAt?: number) {
    const flow = autoFlow.value
    clearAutoTaskError(task?.id)
    flow.created = task; flow.phase = 'created'; flow.error = ''
    recordActivity('AUTO_TASK', flow.message, task.status === 'ACTIVE' ? '自动化任务已启用' : '草稿已保存',
      task.name + (task.status === 'ACTIVE' ? '将按确认的时间与条件执行。' : '尚未启用。'), undefined, (sentAt || flow.startedAt || Date.now()))
    ++autoManageSignal.value; resetAutoFlow()
  }
  async function previewAutoFlow(message: string): Promise<boolean> {
    const text = message.trim(), previous = autoFlow.value
    if (!text || previous.working) return false
    if (!capabilities.value?.enabled || !isTeacherRole.value || !autoTaskEnabled.value) return false
    const mine = epoch, generation = ++flowPreviewEpoch
    autoFlow.value = { ...previous, phase:'previewing', message:text, error:'', working:true, created:null, startedAt:Date.now() }
    const controller = new AbortController(); flowPreviewController = controller
    try {
      await ensureSession()
      const response: any = await request.post('/teacher/auto-tasks/v2/drafts/preview', {
        message:text, sessionId:sessionId.value, taskId:previous.taskId,
        ...(!previous.created && previous.draft?.draftId ? {draftId:previous.draft.draftId,revision:previous.draft.revision} : {})
      }, {signal:controller.signal})
      if (mine !== epoch || generation !== flowPreviewEpoch || controller.signal.aborted) return false
      updateAutoPreview(response.data || {})
      return true
    } catch (e: any) {
      if (mine !== epoch || generation !== flowPreviewEpoch || controller.signal.aborted) return false
      autoFlow.value.phase = 'error'; autoFlow.value.error = formatError(e)
      if (e.response?.status === 403) void refreshCapabilities()
      return false
    } finally {
      if (mine === epoch && generation === flowPreviewEpoch) { autoFlow.value.working = false; flowPreviewController = null }
    }
  }
  async function confirmCreateAutoFlow(): Promise<boolean> {
    const flow = autoFlow.value
    if (flow.working || !flow.draft?.actionId || flow.questions.length) return false
    flow.working = true
    try {
      const result: any = await request.post('/teacher/auto-tasks/v2/confirm', {actionId:flow.draft.actionId,confirmed:true})
      if (flow === autoFlow.value) acceptAutoTask(result.data)
      return true
    } catch(e: any) { flow.error = formatError(e); return false }
    finally { flow.working = false }
  }
  async function restoreAutoDraft() {
    if (restoreAutoInFlight) return
    if (!isTeacherRole.value || !autoTaskEnabled.value || !sessionId.value || autoFlow.value.phase !== 'idle') return
    const mine = epoch, session = sessionId.value
    restoreAutoInFlight = true
    try {
      const result: any = await request.get('/teacher/auto-tasks/v2/drafts', {params:{sessionId:session}, skipErrorMessage:true} as SilentRequestConfig)
      if (mine !== epoch || session !== sessionId.value || autoFlow.value.phase !== 'idle') return
      const latest = result.data?.[0]
      if (!latest) return
      autoFlow.value.message = latest.source_text; autoFlow.value.startedAt = Date.now()
      const preview: any = await request.post('/teacher/auto-tasks/v2/drafts/preview', {sessionId:session, draftId:latest.id, revision:latest.revision, definition:latest.definition, message:latest.source_text, taskId:latest.definition?._taskId}, {skipErrorMessage:true} as SilentRequestConfig)
      if (mine === epoch && session === sessionId.value) updateAutoPreview(preview.data)
    } catch { /* A missing or closed draft must not interrupt normal chat. */ }
    finally { restoreAutoInFlight = false }
  }
  watch([sessionId, () => capabilities.value?.actor.ownerKey, autoTaskEnabled], () => { void restoreAutoDraft() })

  /**
   * FLOW-01：管理员能力变更侧栏闭环（描述→追问→预览→确认保存→结果记录）。
   * 复用手动页同一后端接口：POST parse 预览差异与影响，PUT config 落库；确认前不保存。
   */
  const capsFlow = ref({
    phase: 'idle' as 'idle' | 'previewing' | 'need_input' | 'need_confirm' | 'saving' | 'done' | 'error',
    message: '', questions: [] as string[],
    preview: null as any, saved: null as any, error: '', working: false
  })
  function resetCapsFlow() {
    capsFlow.value = { phase: 'idle', message: '', questions: [], preview: null, saved: null, error: '', working: false }
  }
  async function previewCapsFlow(message: string): Promise<boolean> {
    const text = message.trim()
    if (!text || capsFlow.value.working) return false
    if (!capabilities.value?.enabled || !isAdminRole.value) {
      capsFlow.value = { ...capsFlow.value, phase: 'error', error: '当前身份无权限执行能力变更，请联系管理员。' }
      return false
    }
    const mine = epoch, generation = ++flowPreviewEpoch
    const controller = new AbortController(); flowPreviewController = controller
    capsFlow.value = { phase: 'previewing', message: text, questions: [], preview: null, saved: null, error: '', working: true }
    try {
      await ensureSession()
      const response: any = await request.post('/admin/agent-capabilities/parse', { message: text }, { signal: controller.signal })
      if (mine !== epoch || generation !== flowPreviewEpoch || controller.signal.aborted) return false
      const data = response.data || {}
      capsFlow.value.preview = data
      capsFlow.value.questions = data.questions || []
      capsFlow.value.phase = capsFlow.value.questions.length ? 'need_input' : 'need_confirm'
      return true
    } catch (e: any) {
      if (mine !== epoch || generation !== flowPreviewEpoch || controller.signal.aborted) return false
      capsFlow.value.phase = 'error'
      capsFlow.value.error = formatError(e)
      return false
    } finally {
      if (mine === epoch && generation === flowPreviewEpoch) { capsFlow.value.working = false; flowPreviewController = null }
    }
  }
  async function confirmCapsFlow(): Promise<boolean> {
    const flow = capsFlow.value
    if (flow.working || !flow.preview || (flow.phase !== 'need_confirm' && flow.phase !== 'error')) return false
    if ((flow.questions || []).length) {
      flow.error = '请先补充追问信息后重新生成预览，再确认保存。'
      return false
    }
    flow.working = true; flow.error = ''; flow.phase = 'saving'
    try {
      const response: any = await request.put('/admin/agent-capabilities/config', {
        actionId: flow.preview.actionId, confirmed: true, configurationVersion: flow.preview.configurationVersion, userChanges: flow.preview.userChanges || [],
        roleDefaults: flow.preview.proposedRoleDefaults,
        autoTaskPolicy: flow.preview.proposedAutoTaskPolicy
      })
      flow.saved = response.data || null
      flow.phase = 'done'
      recordActivity('CAPABILITY', flow.message, '权限变更已保存', `已暂停 ${(flow.saved?.pausedTasks || []).length} 个相关任务。后续操作按新权限核验。`, { action: 'OPEN_ADMIN_PERMISSIONS' })
      void refreshCapabilities()
      return true
    } catch (e: any) {
      flow.phase = 'error'
      flow.error = formatError(e)
      if (e.response?.status === 409 && !['ACTION_UNKNOWN', 'ACTION_PENDING'].includes(e.response?.data?.errorCode)) {
        flow.preview.actionId = null
        flow.error = '配置或确认信息已变化，请修改描述并重新生成预览后确认。'
      }
      return false
    } finally {
      flow.working = false
    }
  }

  function setPage(owner: symbol, value: typeof page.value) {
    if (JSON.stringify(value) !== JSON.stringify(page.value)) revision.value++
    pageOwner = owner; page.value = value
  }
  function releasePage(owner: symbol) { if (pageOwner === owner) { page.value = { route: '' }; revision.value++ } }
  function reset() {
    if (draftSaveTimer) window.clearTimeout(draftSaveTimer)
    draftSaveTimer = 0; restoringDraft = true
    fillEpoch++; fillController?.abort(); fillController = null
    flowPreviewEpoch++; flowPreviewController?.abort(); flowPreviewController = null
    epoch++; stream?.abort(); stream = null; pending = null; pendingMessage.value = null; cancellingIds.value = []
    tasks.value = []; fillEvents.value = []; sessionId.value = null; draft.value = ''; error.value = ''; sending.value = false; filling.value = false
    sessions.value = []; sessionsLoading.value = false; showHistory.value = false; sessionsHasMore.value = false; historyHasMore.value = false; sessionsPage = 1
    applyDialog.value.open = false; reminderDialog.value.open = false; autoOpenedReminders.clear()
    activityEvents.value = []; currentPlan.value = null; planStale.value = false; planSaving.value = false; planError.value = ''; ++planRequest; draftConflict.value = null; cancelAfterSend = false; restoringDraft = false
    resetAutoFlow(); resetCapsFlow()
  }
  async function initialize() {
    const mine = ++epoch
    stream?.abort(); loading.value = true; error.value = ''
    try {
      const response: any = await request.get('/agent/capabilities')
      if (mine !== epoch) return
      const caps: Capabilities = response.data
      if (caps.actor.ownerKey !== capabilities.value?.actor.ownerKey) {
        persistDraft(); persistEvents(); restoringDraft = true
        sessions.value = []; sessionsLoading.value = false; showHistory.value = false; sessionsHasMore.value = false; historyHasMore.value = false; sessionsPage = 1
        tasks.value = []; sessionId.value = null; draft.value = ''; pending = null; pendingMessage.value = null; cancellingIds.value = []; sending.value = false
        restoringDraft = false; applyDialog.value.open = false; currentPlan.value = null; planError.value = ''; planSaving.value = false
      }
      capabilities.value = caps
      // 管理员开启总开关后，学生下一次真实登录自动展开一次侧栏。
      // 标记由认证成功流程写入 sessionStorage，消费后刷新页面不会重复展开。
      let shouldAutoOpenForStudentLogin = false
      try {
        shouldAutoOpenForStudentLogin = sessionStorage.getItem(studentLoginAutoOpenKey) === 'true'
        if (shouldAutoOpenForStudentLogin) sessionStorage.removeItem(studentLoginAutoOpenKey)
      } catch { /* 浏览器禁用 sessionStorage 时保持默认关闭 */ }
      if (shouldAutoOpenForStudentLogin && caps.enabled && caps.actor.role === 'ROLE_STUDENT'
        && caps.capabilities?.agent_chat !== false && !caps.actor.readOnly) {
        visible.value = true
        studentLoginAutoOpenJourney.value = true
      }
      void refreshPlan()
      sessionId.value = sessionStorage.getItem(storageKey())
      cancellingIds.value = []
      restoreDraft()
      void restoreEvents()
      void recoverPending()
      void loadSessions()
      if (sessionId.value) {
        try {
          const history: any = await request.get(`/agent/sessions/${sessionId.value}/tasks`, { params: { size: 30 } })
          if (mine !== epoch) return
          tasks.value = Array.isArray(history.data) ? history.data : history.data?.items || []
          historyHasMore.value = Boolean(history.data?.hasMore)
          restoreDraft()
          const running = tasks.value.find(task => !terminal(task.status))
          if (running) void listen(running.id, mine)
        } catch (e: any) {
          if (mine !== epoch) return
          if (e.response?.status === 404) { sessionStorage.removeItem(storageKey()); sessionId.value = null; tasks.value = []; restoreDraft(true) }
          else error.value = '历史暂时无法加载，草稿仍保留。可恢复连接后继续。'
        }
      }
    } catch (e: any) {
      if (mine === epoch) { capabilities.value = null; tasks.value = []; error.value = formatError(e) }
    } finally { if (mine === epoch) loading.value = false }
  }
  function accept(task: AgentTask) {
    if (task.ownerKey !== capabilities.value?.actor.ownerKey) return
    const index = tasks.value.findIndex(item => item.id === task.id)
    const isNew = index < 0
    if (isNew) tasks.value.push(task)
    else if (task.version > tasks.value[index].version) tasks.value[index] = task
    else return
    if (terminal(task.status)) cancellingIds.value = cancellingIds.value.filter(id => id !== task.id)
    if (task.status === 'SUCCEEDED' && !autoOpenedReminders.has(task.id) && !reminderDialog.value.open) {
      const draftCard = (task.cards || []).find((card: any) => card.tool === 'teacher_reminder_draft' && card.status === 'OK')
      const student = draftCard?.items?.find((item: any) => item.id)
      if (student) {
        autoOpenedReminders.add(task.id)
        openReminder({
          studentQuery: student.studentNo || student.title || student.id,
          studentLabel: student.title, studentNo: student.studentNo, className: student.className,
          emailMasked: student.emailMasked, emailAvailable: student.emailAvailable, content: student.content, channel: student.channel
        })
      }
    }
  }
  async function listen(id: string, mine = epoch) {
    stream?.abort()
    const controller = new AbortController(); stream = controller
    for (let attempt = 0; attempt < 3 && mine === epoch; attempt++) {
      try {
        const task = tasks.value.find(item => item.id === id)
        if (!task || terminal(task.status)) return
        const response = await fetch(`/api/agent/tasks/${id}/events?after=${task.version}`, {
          headers: { Authorization: `Bearer ${getAccessToken() || ''}`, Accept: 'text/event-stream' }, signal: controller.signal,
        })
        if (!response.ok || !response.body) throw new Error('实时连接中断，正在核验任务状态。')
        const parse = sseParser((event: string, value: any) => {
          if (mine !== epoch) return
          if (event === 'snapshot') accept(value)
          if (event === 'agent-error') throw new Error(value.message)
        })
        const reader = response.body.getReader(); const decoder = new TextDecoder()
        try {
          while (mine === epoch) {
            const { done, value } = await reader.read()
            if (done) { parse(decoder.decode()); break }
            parse(decoder.decode(value, { stream: true }))
          }
        } finally { await reader.cancel().catch(() => {}) }
        if (terminal(tasks.value.find(item => item.id === id)?.status)) return
      } catch (e: any) { if (controller.signal.aborted || mine !== epoch) return }
      // Fetch the same task; a broken stream never creates a second model execution.
      try {
        const response: any = await request.get(`/agent/tasks/${id}`)
        if (mine !== epoch) return
        accept(response.data)
        if (terminal(response.data.status)) { cancellingIds.value = cancellingIds.value.filter(id => id !== response.data.id); return }
      } catch (e: any) {
        if (mine !== epoch) return
        if ([401, 403, 404].includes(e.response?.status)) { error.value = formatError(e); return }
      }
      await new Promise(resolve => setTimeout(resolve, 1200 * (attempt + 1)))
    }
    if (mine === epoch) error.value = '连接已断开，任务可能仍在执行。点击“恢复连接”查询同一任务。'
  }
  async function ask(context: PageContext, message?: string) {
    if (busy.value || !(message ?? draft.value).trim() || !capabilities.value?.enabled) return
    if (!chatEnabled.value) { error.value = '当前身份未开通对话问答能力，请联系管理员。'; return }
    const text = (message ?? draft.value).trim().slice(0, 4000)
    if (pendingMessage.value?.state === 'failed' && pending?.message !== text) {
      error.value = '还有一条发送结果待核验的消息，请先重试、修改或取消该消息；下一条草稿已保留。'; return
    }
    const sameAsPending = pending != null && (pending as any)?.message === text && typeof (pending as any)?.clientRequestId === 'string'
    const clientRequestId = sameAsPending ? (pending as any).clientRequestId as string : newRequestId()
    pending = { sessionId: sessionId.value, clientRequestId, message: text, pageContext: JSON.parse(JSON.stringify(context)) }
    pendingMessage.value = { clientRequestId, message: text, createdAt: Date.now(), state: 'sending' }
    if (message === undefined || draft.value === text) draft.value = ''
    persistPending()
    await submitPending()
  }
  function persistPending() {
    try {
      const key = `deepintern.agent.pending:${capabilities.value?.actor.ownerKey}`
      if (pending) localStorage.setItem(key, JSON.stringify(pending)); else localStorage.removeItem(key)
    } catch { /* local quota */ }
  }
  async function recoverPending() {
    try {
      const raw = localStorage.getItem(`deepintern.agent.pending:${capabilities.value?.actor.ownerKey}`)
      if (!raw) return
      const saved = JSON.parse(raw)
      if (saved.sessionId !== sessionId.value && saved.sessionId) return
      pending = saved
      pendingMessage.value = { clientRequestId: saved.clientRequestId, message: saved.message, createdAt: Date.now(), state: 'failed', error: '上次发送结果待核验，请点击重试核对同一请求。' }
    } catch { /* invalid local record */ }
  }
  async function submitPending() {
    if (!pending) return
    const mine = epoch; sending.value = true; error.value = ''
    try {
      const response: any = await request.post('/agent/tasks', pending)
      if (mine !== epoch) return
      const task: AgentTask = response.data
      const oldDraftKey = draftKey()
      sessionId.value = task.sessionId; sessionStorage.setItem(storageKey(), task.sessionId)
      accept(task); persistDraft(); persistEvents(); pending = null; pendingMessage.value = null; persistPending()
      if (oldDraftKey !== draftKey()) { try { localStorage.removeItem(oldDraftKey) } catch {} }
      void listen(task.id, mine)
      if (cancelAfterSend) { cancelAfterSend = false; void cancel() }
      void loadSessions()
    } catch (e: any) {
      if (mine !== epoch) return
      error.value = formatError(e)
      if (pending?.clientRequestId && (pending as any)?.message) {
        pendingMessage.value = { clientRequestId: String((pending as any).clientRequestId), message: String((pending as any).message), createdAt: Date.now(), state: 'failed', error: formatError(e) }
      }
      if (e.response?.status === 403) void refreshCapabilities()
    }
    finally { if (mine === epoch) sending.value = false }
  }
  async function retryPending() {
    if (!pending || sending.value) return
    error.value = ''
    if (persistent.value) {
      try {
        const response: any = await request.get(`/agent/requests/${pending.clientRequestId}`)
        if (response.data?.status === 'FOUND' && response.data.task) {
          const task = response.data.task
          sessionId.value = task.sessionId; sessionStorage.setItem(storageKey(), task.sessionId)
          accept(task); pending = null; pendingMessage.value = null; persistPending()
          if (!terminal(task.status)) void listen(task.id)
          return
        }
        if (response.data?.status === 'PENDING') { error.value = '该请求仍在处理，稍后核验即可，无需重复发送。'; return }
      } catch (e: any) { error.value = formatError(e); return }
    }
    if (pendingMessage.value) pendingMessage.value.state = 'sending'
    await submitPending()
  }
  function editPending() {
    if (!(pending as any)?.message) return
    if (draft.value) { error.value = '输入框已有下一条草稿，请先处理该草稿；原消息仍保留。'; return }
    draft.value = String((pending as any).message)
    pending = null; pendingMessage.value = null; persistPending()
    error.value = ''
  }
  function dismissPending() {
    pending = null; pendingMessage.value = null
    persistPending()
    if (!draft.value) restoreDraft()
  }
  async function reconnect() {
    error.value = ''
    const running = tasks.value.find(task => !terminal(task.status))
    if (running) void listen(running.id)
    else if (pending) await submitPending()
    else await initialize()
  }
  async function cancel() {
    if (flowPreviewController) {
      flowPreviewEpoch++; flowPreviewController.abort(); flowPreviewController = null
      for (const flow of [autoFlow.value, capsFlow.value]) {
        if (flow.phase === 'previewing') { flow.phase = 'error'; flow.working = false; flow.error = '预览已停止，尚未创建任务或保存权限变更。' }
      }
      return
    }
    if (filling.value) { cancelFill(); return }
    if (sending.value && !tasks.value.some(task => !terminal(task.status))) { cancelAfterSend = true; return }
    const task = tasks.value.find(task => !terminal(task.status)); if (!task) return
    if (!cancellingIds.value.includes(task.id)) cancellingIds.value.push(task.id)
    try {
      const response: any = await request.post(`/agent/tasks/${task.id}/cancel`)
      accept(response.data)
      const current = tasks.value.find(item => item.id === task.id)
      if (current && !terminal(current.status) && response.data?.status === 'CANCELLING') current.status = 'CANCELLING'
      if (current && terminal(current.status)) cancellingIds.value = cancellingIds.value.filter(id => id !== task.id)
    }
    catch (e: any) {
      cancellingIds.value = cancellingIds.value.filter(id => id !== task.id)
      error.value = formatError(e)
    }
  }
  function isCancelling(id: string) { return cancellingIds.value.includes(id) }
  async function waitForIdle(timeoutMs = 60000): Promise<boolean> {
    const start = Date.now()
    while (sending.value || tasks.value.some(task => !terminal(task.status))) {
      if (Date.now() - start > timeoutMs) return false
      await new Promise(resolve => setTimeout(resolve, 300))
    }
    return true
  }
  async function stopAndSend(context: PageContext) {
    const text = draft.value.trim().slice(0, 4000)
    if (!text || !capabilities.value?.enabled) return
    if (filling.value) cancelFill()
    const running = tasks.value.find(task => !terminal(task.status))
    if (running || sending.value) {
      await cancel()
      const idle = await waitForIdle(60000)
      if (!idle) { error.value = '旧任务仍在停止中，请稍后再试。'; return }
    }
    pending = null; pendingMessage.value = null
    await ask(context, text)
  }
  const resendRequests = new Map<string, string>()
  async function resendTask(id: string, message?: string): Promise<boolean> {
    if (sending.value) return false
    const orig = tasks.value.find(task => task.id === id)
    if (!orig || !capabilities.value?.enabled) return false
    if (!chatEnabled.value) { error.value = '当前身份未开通对话问答能力，请联系管理员。'; return false }
    const running = tasks.value.find(task => !terminal(task.status))
    if (running) {
      await cancel()
      const idle = await waitForIdle(60000)
      if (!idle) { error.value = '旧任务仍在停止中，请稍后再试。'; return false }
    }
    const text = (message ?? orig.message ?? '').trim().slice(0, 4000)
    if (!text) { error.value = '消息内容为空，无法重新发送。'; return false }
    const mine = epoch; sending.value = true; error.value = ''
    try {
      const operation = message === undefined ? responseOperation(orig) : 'EDIT'
      const retryKey = `${id}:${operation}:${text}`
      const clientRequestId = resendRequests.get(retryKey) || newRequestId()
      resendRequests.set(retryKey, clientRequestId)
      const response: any = await request.post(`/agent/tasks/${id}/resend`, { clientRequestId, message: text, operation })
      if (mine !== epoch) return false
      const task: AgentTask = response.data
      accept(task)
      resendRequests.delete(retryKey)
      void listen(task.id, mine)
      return true
    } catch (e: any) {
      if (mine !== epoch) return false
      error.value = formatError(e)
      if (e.response?.status === 403) void refreshCapabilities()
      return false
    } finally { if (mine === epoch) sending.value = false }
  }
  /** 会话历史：只列本人会话；归档只改展示，删除不级联业务记录或审计。 */
  interface SessionSummary { id: string; title: string; updatedAt: number; archived: boolean; taskCount: number; hasRunningTask: boolean }
  const sessions = ref<SessionSummary[]>([])
  const sessionsLoading = ref(false)
  const showHistory = ref(false)
  const sessionsHasMore = ref(false)
  const historyHasMore = ref(false)
  let sessionsPage = 1
  async function loadSessions(append = false) {
    const mine = epoch
    sessionsLoading.value = true
    try {
      if (!append) sessionsPage = 1
      const response: any = await request.get('/agent/sessions', { params: { page: sessionsPage, size: 30 } })
      if (mine !== epoch) return
      const rows = Array.isArray(response.data) ? response.data : response.data?.items || []
      sessions.value = append ? [...sessions.value, ...rows] : rows
      sessionsHasMore.value = Boolean(response.data?.hasMore)
      if (sessionsHasMore.value) sessionsPage++
    } catch (e: any) {
      if (mine === epoch) error.value = formatError(e)
    } finally { if (mine === epoch) sessionsLoading.value = false }
  }
  async function renameSession(id: string, title: string) {
    const text = String(title ?? '').trim()
    if (!text || text.length > 60) { error.value = '会话名称需为1至60字。'; return false }
    try {
      await request.patch(`/agent/sessions/${id}`, { title: text })
      await loadSessions()
      return true
    } catch (e: any) { error.value = formatError(e); return false }
  }
  async function archiveSession(id: string, archived: boolean) {
    try {
      await request.post(`/agent/sessions/${id}/archive`, { archived })
      await loadSessions()
      return true
    } catch (e: any) { error.value = formatError(e); return false }
  }
  async function deleteSession(id: string) {
    const target = sessions.value.find(item => item.id === id)
    // 有任务在跑时后端会拒绝；前端先提示，避免用户以为已经删除。
    if (target?.hasRunningTask) { error.value = '该会话仍有任务在执行，请先停止或等待结束后再删除。'; return false }
    try {
      await request.delete(`/agent/sessions/${id}`)
      try { localStorage.removeItem(`deepintern.agent.draft:${capabilities.value?.actor.ownerKey}:${id}`); localStorage.removeItem(`deepintern.agent.events:${capabilities.value?.actor.ownerKey}:${id}`) } catch {}
      if (sessionId.value === id) { sessionStorage.removeItem(storageKey()); reset() }
      await loadSessions()
      return true
    } catch (e: any) { error.value = formatError(e); return false }
  }
  async function switchSession(id: string) {
    if (busy.value || operationWorking.value || id === sessionId.value) return
    flushDraft(); persistEvents()
    const mine = ++epoch
    stream?.abort(); stream = null; pending = null; pendingMessage.value = null; cancellingIds.value = []
    sessionId.value = id
    sessionStorage.setItem(storageKey(), id)
    tasks.value = []; fillEvents.value = []; activityEvents.value = []; error.value = ''; draftConflict.value = null
    resetAutoFlow(); resetCapsFlow(); applyDialog.value.open = false
    restoreDraft(true); void restoreEvents(); void recoverPending()
    try {
      const history: any = await request.get(`/agent/sessions/${id}/tasks`, { params: { size: 30 } })
      if (mine !== epoch) return
      tasks.value = Array.isArray(history.data) ? history.data : history.data?.items || []
      historyHasMore.value = Boolean(history.data?.hasMore)
      const running = tasks.value.find(task => !terminal(task.status))
      if (running) void listen(running.id, mine)
    } catch (e: any) {
      if (mine === epoch) error.value = formatError(e)
    }
  }
  async function loadOlderTasks() {
    if (!sessionId.value || loading.value) return
    const mine = epoch; loading.value = true
    try {
      const response: any = await request.get(`/agent/sessions/${sessionId.value}/tasks`, { params: { before: tasks.value[0]?.id, size: 30 } })
      if (mine !== epoch) return
      const rows = Array.isArray(response.data) ? response.data : response.data?.items || []
      tasks.value = [...rows.filter((item: AgentTask) => !tasks.value.some(task => task.id === item.id)), ...tasks.value]
      historyHasMore.value = Boolean(response.data?.hasMore)
    } catch (e: any) { error.value = formatError(e) }
    finally { if (mine === epoch) loading.value = false }
  }
  function newSession() {
    if (busy.value || operationWorking.value) return
    flushDraft(); persistEvents(); sessionStorage.removeItem(storageKey()); reset(); restoreDraft(true); void restoreEvents(); void refreshPlan(); void loadSessions()
  }
  async function refreshCapabilities() {
    const mine = epoch
    try {
      const response: any = await request.get('/agent/capabilities')
      if (mine !== epoch) return
      const caps: Capabilities = response.data
      if (caps.actor.ownerKey !== capabilities.value?.actor.ownerKey) {
        persistDraft(); persistEvents(); restoringDraft = true
        sessions.value = []; sessionsLoading.value = false; showHistory.value = false; sessionsHasMore.value = false; historyHasMore.value = false; sessionsPage = 1
        tasks.value = []; sessionId.value = null; draft.value = ''; pending = null; pendingMessage.value = null; cancellingIds.value = []; sending.value = false
        restoringDraft = false; applyDialog.value.open = false; currentPlan.value = null; planError.value = ''; planSaving.value = false
      }
      capabilities.value = caps
      void refreshPlan()
      if (!draft.value) restoreDraft()
    } catch (e: any) {
      if (mine !== epoch) return
      if (e.response?.status === 401) dispose()
    }
  }
  async function openWithPrompt(text = '') { visible.value = true; if (text) draft.value = text; await nextTick(); void refreshCapabilities() }

  /**
   * 智能填表：把语音/文字内容交给后端解析为字段赋值意图，再写回当前注册表单的响应式 model。
   * 返回 false 表示应回落为普通顾问提问（无赋值意图/未授权/表单已关闭）。
   */
  function cancelFill() {
    fillEpoch++; fillController?.abort(); fillController = null; filling.value = false
  }
  async function fillForm(message: string): Promise<boolean> {
    const form = currentForm.value
    if (!form || !formFillEnabled.value || !capabilities.value?.enabled || capabilities.value.actor.readOnly) return false
    if (filling.value) return true
    const mine = epoch, generation = ++fillEpoch, owner = capabilities.value.actor.ownerKey
    const controller = new AbortController(); fillController = controller
    filling.value = true; error.value = ''
    const event: FillEvent = {
      id: newRequestId(), kind: 'fill', formId: form.formId, formTitle: form.title,
      sessionId: sessionId.value || undefined, message, intent: 'form_fill', assignments: [], questions: [], skippedFields: [],
      applied: false, createdAt: Date.now(), expectedInstanceId: form.instanceId
    }
    const valid = () => mine === epoch && generation === fillEpoch && !controller.signal.aborted && owner === capabilities.value?.actor.ownerKey && capabilities.value?.enabled && formFillEnabled.value && !capabilities.value.actor.readOnly && form.instanceId === currentForm.value?.instanceId
    const retain = () => {
      if (mine === epoch && owner === capabilities.value?.actor.ownerKey) {
        const fields = getFormFields(form)
        event.skippedFields = event.skippedFields.map(item => ({ ...item, label: fields.find(field => field.prop === item.prop)?.label || '该字段' }))
        event.completedAt = Date.now()
        fillEvents.value.push(event); void saveEvent(event, 'FILL')
      }
    }
    try {
      await ensureSession()
      event.sessionId = sessionId.value || undefined
      if (!valid()) { event.decision = 'CANCEL'; event.error = '本次解析已停止，未修改表单。'; retain(); return true }
      const baseline = captureValues(form.formId), instanceId = form.instanceId
      const response: any = await request.post('/agent/forms/fill-intent', {
        formId: form.formId, formTitle: form.title, fields: getFormFields(form), message
      }, { signal: controller.signal })
      if (!valid()) {
        event.decision = 'CANCEL'; event.error = '本次解析已停止，未修改表单。'; retain(); return true
      }
      const data = response.data || {}
      event.intent = data.intent || 'chat'
      if (event.intent === 'chat') return false
      event.assignments = data.assignments || []; event.questions = data.questions || []; event.skippedFields = data.skippedFields || []
      if (!event.assignments.length) {
        const validation = getFormValidation()
        Object.assign(event, validation)
        retain(); return true
      }
      const result = applyAssignments(form.formId, event.assignments, baseline, instanceId, message)
      if (result === null) {
        event.assignments = []; event.activeInstanceId = currentForm.value?.instanceId ?? null
        event.decision = 'REJECT'; event.error = '表单已关闭或切换，本次内容未填写。请打开目标表单后再试。'
      } else {
        event.skippedFields = [...event.skippedFields, ...result.skipped]; event.assignments = result.applied
        event.activeInstanceId = currentForm.value?.instanceId ?? null; event.decision = 'APPLY'
        Object.assign(event, { fillBatchId: result.fillBatchId, beforeValues: result.beforeValues, afterValues: result.afterValues,
          missingFields: result.missingFields, validationErrors: result.validationErrors, businessStatus: 'filled' })
        event.applied = result.applied.length > 0
        if (!event.applied) event.error = '本次未填写字段，已保留当前值，请核对提示。'
      }
      retain()
      if (event.applied) void validateForm().then(summary => {
        if (!valid()) return
        const retained = fillEvents.value.find(item => item.id === event.id)
        if (retained) { Object.assign(retained, summary); void saveEvent(retained, 'FILL') }
      }).catch(() => {
        if (!valid()) return
        const retained = fillEvents.value.find(item => item.id === event.id)
        if (retained) { retained.validationErrors = [...(retained.validationErrors || []), '原表单校验暂未完成，请在原页面核对。']; void saveEvent(retained, 'FILL') }
      })
      return true
    } catch (e: any) {
      event.decision = controller.signal.aborted || generation !== fillEpoch ? 'CANCEL' : 'REJECT'
      event.error = event.decision === 'CANCEL' ? '本次解析已停止，未修改表单。' : formatError(e)
      retain()
      if (e.response?.status === 403) void refreshCapabilities()
      return true
    } finally {
      if (mine === epoch && generation === fillEpoch) { filling.value = false; fillController = null }
    }
  }

  function undoFill(fillBatchId?: string): boolean {
    const latest = fillBatchId ? fillEvents.value.find(item => item.fillBatchId === fillBatchId) : [...fillEvents.value].reverse().find(item => item.applied && !item.undone && item.expectedInstanceId === currentForm.value?.instanceId && !['saved', 'submitted', 'published'].includes(item.businessStatus || ''))
    if (!latest || latest.expectedInstanceId !== currentForm.value?.instanceId || latest.formId !== currentForm.value?.formId) {
      error.value = '目标表单已切换，历史填写不能撤销当前表单。'; return false
    }
    const result = undoLast(latest.fillBatchId)
    if (!result) { error.value = '该批次已无法撤销；当前手工内容保持不变。'; return false }
    latest.undone = true
    latest.preservedFields = result.preserved.map(prop => latest.assignments.find(item => item.prop === prop)?.label || prop)
    void saveEvent(latest, 'FILL')
    return true
  }

  function dispose() { persistDraft(); persistEvents(); reset(); capabilities.value = null; visible.value = false; studentLoginAutoOpenJourney.value = false; resetAutoFlow(); resetCapsFlow() }
  /** 教师单次提醒确认卡：AI 只出草稿，教师在卡片上点确认后由系统真实发送。 */
  interface ReminderReceipt { studentId: string; studentName: string; studentNo: string; channel: string; content: string; message: string; sentAt: string }
  const reminderReceipts = ref<Record<string, ReminderReceipt>>({})

  const reminderKey = (value: unknown) => String(value ?? "").trim().toLowerCase()

  function reminderSent(value: unknown) { return Boolean(reminderReceipts.value[reminderKey(value)]) }

  const reminderDialog = ref({
    open: false, studentQuery: '', studentLabel: '', studentNo: '', className: '', emailMasked: '', emailAvailable: false,
    channel: 'EMAIL' as 'EMAIL' | 'STATION_REMINDER', content: '',
    loading: false, error: '', receipt: null as ReminderReceipt | null,
    actionId: '', parameters: null as Record<string, string> | null, previewedAt: 0,
    verifyState: '' as '' | 'pending' | 'confirmed'
  })
  function openReminder(input: { studentQuery?: unknown; studentLabel?: unknown; studentNo?: unknown; className?: unknown; emailMasked?: unknown; emailAvailable?: unknown; content?: unknown; channel?: unknown }) {
    if (capabilities.value?.actor.readOnly || !capabilities.value?.enabled) { error.value = '当前身份不能代发提醒。'; return }
    const raw = String(input.emailAvailable ?? '')
    const emailAvailable = input.emailAvailable !== false && raw !== 'false' && raw !== '' && raw !== '0'
    reminderDialog.value = {
      open: true, studentQuery: String(input.studentQuery ?? '').trim(),
      studentLabel: String(input.studentLabel ?? ''), studentNo: String(input.studentNo ?? ''),
      className: String(input.className ?? ''), emailMasked: String(input.emailMasked ?? ''), emailAvailable,
      channel: input.channel === 'STATION_REMINDER' ? 'STATION_REMINDER' : input.channel === 'EMAIL' ? 'EMAIL' : emailAvailable ? 'EMAIL' : 'STATION_REMINDER', content: String(input.content ?? ''),
      loading: false, error: '', receipt: null, actionId: '', parameters: null, previewedAt: 0, verifyState: ''
    }
  }
  function closeReminder() { if (reminderDialog.value.loading) return; reminderDialog.value.open = false }
  /** 每次确认都按当前内容重新预览：教师改过正文时旧确认卡必须失效。 */
  async function previewReminder() {
    const dialog = reminderDialog.value
    const response: any = await request.post('/agent/reminders/preview', {
      studentQuery: dialog.studentQuery.trim() || dialog.studentNo.trim(),
      channel: dialog.channel, content: dialog.content.trim()
    })
    dialog.actionId = response.data.id; dialog.previewedAt = response.data.previewedAt
    dialog.parameters = response.data.parameters
    return response.data
  }
  async function confirmReminder() {
    const dialog = reminderDialog.value
    if (!dialog.open || dialog.loading) return
    if (!(dialog.studentQuery.trim() || dialog.studentNo.trim())) { dialog.error = '请先说明要提醒的学生。'; return }
    if (!dialog.content.trim()) { dialog.error = '请填写要发送的提醒内容。'; return }
    if (dialog.channel === 'EMAIL' && !dialog.emailAvailable) { dialog.error = '该学生未绑定邮箱，请改用站内提醒。'; return }
    if (capabilities.value?.actor.readOnly) { dialog.error = '当前身份已不可执行此操作，请重新核对权限。'; return }
    if (dialog.verifyState === 'pending') { dialog.error = '上次发送结果尚未核实，请先刷新状态，避免重复发送。'; return }
    dialog.loading = true; dialog.error = ''
    try {
      await previewReminder()
      const response: any = await request.post('/agent/reminders/send', {
        actionId: dialog.actionId, parameters: dialog.parameters,
        clientRequestId: newRequestId(), confirmed: true
      })
      dialog.receipt = response.data; dialog.verifyState = 'confirmed'

      const receiptKey = reminderKey(response.data?.studentNo || response.data?.studentId || dialog.studentNo || dialog.studentQuery)

      if (receiptKey) reminderReceipts.value[receiptKey] = response.data
    } catch (e: any) {
      const code = e.response?.data?.errorCode
      if (['PREVIEW_EXPIRED', 'PREVIEW_REQUIRED', 'PREVIEW_INVALID', 'PREVIEW_CHANGED', 'ACTION_MISMATCH', 'ACTION_NOT_FOUND'].includes(code)) {
        dialog.error = e.response?.data?.message || '确认信息已过期，请重新核对后再发送。'
        dialog.actionId = ''; dialog.parameters = null; dialog.previewedAt = 0
      } else if (['ACTION_UNKNOWN', 'ACTION_PENDING'].includes(code) || !e.response || e.code === 'ECONNABORTED' || String(e.message || '').includes('timeout')) {
        dialog.verifyState = 'pending'
        dialog.error = '发送结果待核实：请求已发出但未收到确认。请点“刷新状态”核对，避免重复发送。'
      } else {
        dialog.error = formatError(e)
      }
    } finally { dialog.loading = false }
  }
  /** 结果不明时先查真值，不直接重发。 */
  async function verifyReminder() {
    const dialog = reminderDialog.value
    if (!dialog.open || dialog.loading || !dialog.actionId) return
    dialog.loading = true; dialog.error = ''
    try {
      const response: any = await request.get(`/agent/actions/${dialog.actionId}`)
      const action = response.data
      if (action.status === 'SUCCEEDED' && action.receipt) { dialog.verifyState = 'confirmed'; dialog.receipt = action.receipt }
      else if (['FAILED', 'READY', 'PREVIEWED'].includes(action.status)) {
        dialog.verifyState = ''; dialog.error = action.errorMessage || '该提醒尚未发送，可核对后重新确认。'
        if (action.status === 'FAILED') { dialog.actionId = ''; dialog.parameters = null }
        if (action.expiresAt && action.expiresAt < Date.now()) { dialog.actionId = ''; dialog.parameters = null; dialog.error = '确认信息已过期，请重新确认。' }
      } else { dialog.verifyState = 'pending'; dialog.error = '发送结果仍待核实，暂不重复发送。' }
    } catch { dialog.error = '暂时无法核验发送结果，请在学生管理页核对后再决定是否重试。' }
    finally { dialog.loading = false }
  }
  return { recordActivity, ensureSession, visible, studentLoginAutoOpenJourney, capabilities, tasks, fillEvents, activityEvents, currentPlan, refreshPlan, planStale, planSaving, planError, savePlanPreference, sessions, sessionId, sessionsLoading, sessionsHasMore, historyHasMore, showHistory, draft, draftConflict, persistent, pendingMessage, cancellingIds, error, loading, sending, filling, busy, operationWorking, page, revision,
    formFillEnabled, voiceEnabled, chatEnabled, planEnabled, applyCapability, autoTaskEnabled, isTeacherRole, isAdminRole,
    autoFlow, autoTaskErrors, noteAutoTaskError, clearAutoTaskError, capsFlow, applyDialog, reminderDialog, currentForm,
    setPage, releasePage, initialize, refreshCapabilities, loadSessions, renameSession, archiveSession, deleteSession, switchSession, ask, retryPending, editPending, dismissPending, reconnect, cancel, isCancelling, stopAndSend, waitForIdle, resendTask, persistDraft, schedulePersistDraft, flushDraft, restoreDraft, resolveDraftConflict, clearDraftCache, loadOlderTasks, newSession, openWithPrompt, fillForm, cancelFill, undoFill,
    openApply, closeApply, confirmApply, verifyApply, openReminder, closeReminder, confirmReminder, verifyReminder, reminderSent,
    resetAutoFlow, dismissAutoDraft, prepareAutoEdit, previewAutoFlow, updateAutoPreview, autoManageSignal, acceptAutoTask, confirmCreateAutoFlow,
    resetCapsFlow, previewCapsFlow, confirmCapsFlow, dispose }
})
function formatError(error: any) { return error.response?.data?.message || error.message || '助手暂时不可用，请稍后重试。' }
