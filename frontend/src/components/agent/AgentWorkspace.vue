<script setup lang="ts">
import { CopyDocument, EditPen, RefreshRight, Check, Plus, Clock, RefreshLeft, Delete, Close, Timer } from '@element-plus/icons-vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import request from '@/utils/request'
import eventBus from '@/utils/eventBus'
import { onStudentRecentActivityChanged, offStudentRecentActivityChanged } from '@/utils/websocket'
import { useAgentStore } from '@/agent/store'
import StudentJourney from './StudentJourney.vue'
import AutomationManager from './AutomationManager.vue'
import AutomationPreview from './AutomationPreview.vue'
import { ElMessageBox } from 'element-plus'
import { automationRequest, automationTemplate } from '@/agent/automationIntent.mjs'
import { shouldResolveFormFill, shouldResolveUiIntent } from '@/agent/sendRouting.mjs'
import { getAutoTaskCapability } from '@/api/teacherAutoTask'
import { allowsWriteAction, answerParts, relevantAnswerCards, businessTarget, localReceiptTarget, panelSize, responseOperation, setActionRouteMap, taskVersions, shouldDock, terminal } from '@/agent/protocol.mjs'
import { capabilityRequest } from '@/agent/capabilityIntent.mjs'
import { asrSupported, createAsr } from '@/agent/asr'
import { collectUiTargets, targetStillAvailable, waitForUiTargets } from '@/agent/uiTargets'
import { getFormFields, getFormValidation, captureValues } from '@/agent/formContext'
import type { AsrSession } from '@/agent/asr'
import type { AgentLink, FillEvent, PageContext } from '@/agent/types'

const agent = useAgentStore(); const auth = useAuthStore(); const route = useRoute(); const router = useRouter()
const lastOf = <T>(items: readonly T[]): T | undefined => items[items.length - 1]
const panel = ref<HTMLElement>(); const composer = ref<HTMLTextAreaElement>(); const conversation = ref<HTMLElement>()
const viewportWidth = ref(window.innerWidth)
const savedWidth = Number(localStorage.getItem('deepintern.agent.width'))
const width = ref(Number.isFinite(savedWidth) && savedWidth >= 360 ? Math.min(savedWidth, 560) : 420)
const docked = computed(() => agent.visible && shouldDock(viewportWidth.value, width.value))
const panelWidth = computed(() => panelSize(viewportWidth.value, width.value))
const mobile = computed(() => viewportWidth.value <= 700)
const dictLabels = ref<Record<string, Record<string, string>>>({ pageTitle: {}, objectType: {}, filterName: {}, featureLabel: {} })
onMounted(async () => {
  try {
    const { data } = await request.get<Record<string, any>>('/agent/dicts')
    if (data) {
      dictLabels.value = { pageTitle: {}, objectType: {}, filterName: {}, featureLabel: {}, ...data }
      setActionRouteMap(data.actionRoute, data.actionRouteQueryKey)
    }
  } catch { /* 回退到空映射，显示原始 key */ }
})
const titles = computed(() => dictLabels.value.pageTitle)
const typeNames = computed(() => dictLabels.value.objectType)
const filterNames = computed(() => dictLabels.value.filterName)
const context = computed<PageContext>(() => {
  const published = agent.page.route === route.path ? agent.page : {}
  const base = { pageKey: String(route.name || route.path), route: route.path, title: String(route.meta.title || titles.value[route.path.split('/').pop() || ''] || '当前业务页面'),
    view: 'page', object: null, selectedObjects: [], filters: {}, ...published, revision: agent.revision }
  // 当前打开的表单（弹窗/页面）作为页面上下文的一部分：顾问回答"字段什么意思"类问题时的依据
  const form = agent.currentForm
  if (form && agent.formFillEnabled) {
    const fillable = getFormFields(form).filter(field => field.type !== 'upload'), validation = getFormValidation()
    const summary = [fillable.map(field => `${field.label}${field.valueType === 'array' ? '（可多选）' : ''}${field.patternHint ? '：' + field.patternHint : ''}`).join('；'), validation.missingFields.length ? `还缺：${validation.missingFields.join('、')}` : '', validation.validationErrors.length ? `需修正：${validation.validationErrors.join('、')}` : ''].filter(Boolean).join('；')
    const pendingFill = agent.fillEvents.some(event => event.formId === form.formId && event.expectedInstanceId === form.instanceId && event.applied && !event.undone && !['saved', 'submitted', 'published'].includes(event.businessStatus || ''))
    // A saved fill receipt covers only AI-written fields, not every current form value.
    base.filters = { ...base.filters, formId: form.formId, formInstanceId: String(form.instanceId), formStatus: pendingFill ? 'filled' : 'unknown', formTitle: form.title, formFields: summary.slice(0, 1200) }
  }
  return base
})
const topOffset = ref(64)
let headerObserver: ResizeObserver | null = null
const readBannerHeight = () => {
  const host = document.querySelector('.agent-host')
  // Every role shell uses .header; a generic header can select an earlier reminder dialog.
  const header = host?.querySelector<HTMLElement>('.header')
  const banner = host?.querySelector<HTMLElement>(':scope > .read-only-banner')
  topOffset.value = Math.max(0, header?.getBoundingClientRect().bottom || 64, banner?.getBoundingClientRect().bottom || 0)
  document.body.style.setProperty('--business-overlay-top', `${topOffset.value}px`)
}
const filterText = computed(() => Object.entries(context.value.filters).filter(([key, value]) => !key.startsWith('form') && value !== '' && value != null && (!Array.isArray(value) || value.length > 0))
  .map(([key, value]) => `${filterNames.value[key] || '筛选'}：${key === 'visibleIds' && Array.isArray(value) ? `${value.length} 项` : Array.isArray(value) ? value.join('、') : value}`).join(' · '))
const contextLabel = computed(() => context.value.object ? `${typeNames.value[context.value.object.type] || '对象'} #${context.value.object.id}` : '当前页面')
const role = computed(() => agent.capabilities?.actor.role || '')
// 管理员角色切换（impersonate）进入的只读身份：入口保留，但不可发起对话
const readOnly = computed(() => Boolean(auth.user?.originalAdminUsername) || Boolean(agent.capabilities?.actor.readOnly))
const roleLabel = computed(() => role.value === 'ROLE_STUDENT' ? '学生' : role.value.startsWith('ROLE_TEACHER') ? '教师' : role.value === 'ROLE_COMPANY' ? '企业' : '管理员')
const messageTime = (value: number) => new Date(value).toLocaleString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
const sessionDate = (value: number) => new Date(value).toLocaleDateString('zh-CN')
const activeTask = computed(() => agent.tasks.find(task => !terminal(task.status)))
const stateLabels: Record<string, string> = { QUEUED: '正在排队', RUNNING: '正在查询', CANCELLING: '正在停止', SUCCEEDED: '已完成', WAITING_INPUT: '需要补充信息', FAILED: '未完成', CANCELLED: '已停止' }
const taskDisplayStatus = (task: any) => (agent.isCancelling(task.id) && !terminal(task.status) ? '正在停止' : (stateLabels[task.status] || task.status))
const canRepeatTask = (task: any) => !readOnly.value && task.status === 'FAILED' && (!Array.isArray(task.allowedOperations) || task.allowedOperations.includes(responseOperation(task)))
const atBottom = ref(true)
let lastFocus: HTMLElement | null = null
let bannerObserver: MutationObserver | null = null

// ===== 智能填表：send 路由 + 语音输入 =====
// 有关联表单且开通填表能力时，输入先走 fill-intent（后端模型判定意图）；
// 无赋值意图或表单已关闭时自动回落为普通顾问提问，语音与文字共用同一条链路。
const linkedForm = computed(() => agent.currentForm)
const canVoice = computed(() => Boolean(agent.voiceEnabled && asrSupported() && agent.capabilities?.enabled))
const asrState = ref<'idle' | 'listening' | 'error'>('idle')
const asrInterim = ref('')
const asrError = ref('')
const asrFinal = ref('')
const voiceFinishing = ref(false)
let asrSession: AsrSession | null = null

// 对话统一处理问答、填表和任务预览；任务管理从顶部入口展开。
const showAutomation = ref(false)
const automationAllowed = ref(false)
const canAutomate = computed(() => Boolean(automationAllowed.value && agent.capabilities?.enabled && agent.autoTaskEnabled && agent.isTeacherRole && !readOnly.value))
let automationCheck = 0
watch([() => agent.visible, () => agent.capabilities, readOnly], async () => {
  const sequence = ++automationCheck
  automationAllowed.value = false
  if (!agent.visible || !agent.capabilities?.enabled || !agent.autoTaskEnabled || !agent.isTeacherRole || readOnly.value) return
  try {
    const response = await getAutoTaskCapability()
    if (sequence === automationCheck) automationAllowed.value = response.data?.canManage === true
  } catch { /* Unknown permission stays closed. */ }
}, { immediate: true })
watch(canAutomate, allowed => { if (!allowed) showAutomation.value = false })
watch(() => agent.capabilities?.actor.ownerKey, () => { showAutomation.value = false })
watch(() => agent.autoManageSignal, () => { if (canAutomate.value) showAutomation.value = true })
function confirmAutoTask(task: any) { agent.acceptAutoTask(task, autoStartedAt.value) }
async function useAutomationExample(message: string) {
  if (!canAutomate.value) return
  if (agent.draft.trim()) { try { await ElMessageBox.confirm('用此模板替换当前未发送内容？', '使用模板', { confirmButtonText:'替换',cancelButtonText:'保留' }) } catch { return } }
  agent.resetAutoFlow()
  agent.draft = message; showAutomation.value = false
  await nextTick(); composer.value?.focus()
  if (message === automationTemplate) {
    const position = message.indexOf('任务内容是：') + '任务内容是：'.length
    composer.value?.setSelectionRange(position, position)
  }
}
const canManageCapabilities = computed(() => Boolean(agent.isAdminRole && !readOnly.value))
const autoPhaseLabel: Record<string, string> = { idle: '空闲', previewing: '正在生成预览', need_input: '需要补充信息', need_confirm: '待确认创建', creating: '正在创建', created: '已创建（默认暂停）', error: '未完成' }
async function editAutomation(task: any) {
  agent.prepareAutoEdit(task); agent.draft = `请修改自动化任务“${task.name}”。\n调整要求是：`
  showAutomation.value = false; await nextTick(); composer.value?.focus()
}
const capsPhaseLabel: Record<string, string> = { previewing: '正在生成预览', need_input: '需要补充信息', need_confirm: '待确认保存', saving: '正在保存', done: '已完成', error: '未完成', idle: '空闲' }
const permissionLabels = computed<Record<string, string>>(() => ({
  student: '学生', teacher: '教师', company: '企业', admin: '管理员',
  actions: '执行动作', channels: '通知渠道', scopes: '学生范围', maxTargetsPerRun: '单次人数上限', globalCooldownMinutes: '同事项冷却分钟',
  SEND_REMINDER: '发送提醒', REMIND_REFLECTION: '提醒交心得', REMIND_INTERNSHIP_CONFIRMATION: '提醒实习确认',
  STATION_REMINDER: '站内提醒', EMAIL: '邮件', OWN_CLASSES: '本人班级', OWN_DIVISION: '本系', OWN_COLLEGE: '本学院',
  ...dictLabels.value.featureLabel
}))
const permissionLabel = (value: string) => permissionLabels.value[value] || value
const permissionValue = (value: any): string => Array.isArray(value) ? value.map(permissionLabel).join('、') || '无' : typeof value === 'boolean' ? value ? '开启' : '关闭' : value == null ? '跟随角色默认' : String(value)
function userPermissionChanges(change: any) {
  const before = change.fromOverrides || change.before || {}, after = change.overrides || {}
  const lines = [...new Set([...Object.keys(before), ...Object.keys(after)])].filter(key => before[key] !== after[key]).map(key => `${permissionLabel(key)}：${permissionValue(before[key])} → ${permissionValue(after[key])}`)
  if (change.automationPolicy) {
    const previous = change.fromAutomationPolicy || {}
    for (const key of new Set([...Object.keys(previous), ...Object.keys(change.automationPolicy)])) {
      if (JSON.stringify(previous[key]) !== JSON.stringify(change.automationPolicy[key])) lines.push(`${permissionLabel(key)}：${permissionValue(previous[key])} → ${permissionValue(change.automationPolicy[key])}`)
    }
  }
  return lines.join('；') || '覆盖不变'
}

const viewportBottom = ref(window.innerHeight)
const visibleTop = ref(0)
const panelTop = computed(() => Math.max(topOffset.value, visibleTop.value))
const panelHeight = computed(() => Math.max(120, viewportBottom.value - panelTop.value))
function resizeComposer() {
  const input = composer.value
  if (!input) return
  input.style.height = 'auto'
  input.style.height = `${Math.min(180, Math.max(48, input.scrollHeight))}px`
}
watch([() => agent.draft, composer, width, panelHeight], () => { void nextTick(resizeComposer) }, { flush: 'post' })
function updateKeyboardGap() {
  const vv = window.visualViewport
  viewportBottom.value = vv ? vv.height + vv.offsetTop : window.innerHeight
  visibleTop.value = vv?.offsetTop || 0
}

const uiWorking = ref(false)
let uiController: AbortController | null = null
let uiExpectedPath = '', uiNavigationPath: string | null = null
watch(() => route.fullPath, path => {
  const menuRedirect = uiNavigationPath && lastOf(router.resolve(uiNavigationPath).matched)?.redirect
    && route.redirectedFrom?.fullPath === uiNavigationPath
    && path.split('/')[1] === uiNavigationPath.split('/')[1]
  if (uiController && path !== uiExpectedPath && path !== uiNavigationPath && !menuRedirect) uiController.abort()
}, { flush: 'sync' })
watch([() => auth.token, () => agent.capabilities?.actor.ownerKey, () => agent.visible,
  () => agent.capabilities?.enabled, readOnly], () => {
  if (uiController) { uiController.abort(); agent.cancelFill() }
}, { flush: 'sync' })
watch(() => agent.sessionId, (value, previous) => {
  if (previous || !value) uiController?.abort()
}, { flush: 'sync' })
onBeforeUnmount(() => uiController?.abort())
async function handleUiRequest(message: string): Promise<boolean | 'chat'> {
  const owner = agent.capabilities?.actor.ownerKey, token = auth.token, session = agent.sessionId
  let expectedPath = route.fullPath
  uiExpectedPath = expectedPath
  const controller = new AbortController()
  controller.signal.addEventListener('abort', () => agent.cancelFill(), { once: true })
  uiController = controller; uiWorking.value = true
  const valid = () => !controller.signal.aborted && owner === agent.capabilities?.actor.ownerKey
    && token === auth.token && (session === agent.sessionId || (!session && receiptSession === agent.sessionId))
    && route.fullPath === expectedPath && agent.visible && agent.capabilities?.enabled && !readOnly.value
  let receiptSession: string | null = null
  const receipt = async (title: string, detail: string) => {
    if (!valid()) return
    await agent.ensureSession()
    receiptSession = agent.sessionId
    if (!valid()) return
    agent.recordActivity('ACTION', message, title, detail)
    if (agent.draft.trim() === message) agent.draft = ''
  }
  let opened = ''
  let needsForm = false
  const visited = new Set<string>()
  try {
    // Resolve again after mounting the destination: its current tabs, permissions and openers are authoritative.
    for (let step = 0; step < 4; step++) {
      const targets = collectUiTargets(router)
      // 明确要求打开表单且入口唯一时直接点击，避免模型把打开表单误判为普通问答。
      if (!linkedForm.value && /(?:打开|进入|切换到|弹出).{0,30}(?:表单|表格|弹窗|发布岗位|岗位发布)/.test(message)) {
        const openers = targets.filter(item => item.kind === 'open')
        const matched = openers.filter(item => {
          const label = (item.label || '').replace(/(?:表单|表格|弹窗|按钮)$/u, '').trim()
          return label.length >= 2 && message.includes(label)
        })
        if (matched.length === 1 && matched[0].element) {
          const opener = matched[0]
          opener.element.click()
          opened = opener.label
          await nextTick()
          await waitForUiTargets(router, controller.signal)
          if (!valid()) return true
          // Keep the original request after opening: its field assignments still
          // go through the registered schema and the normal confirmation boundary.
          if (linkedForm.value && agent.formFillEnabled) {
            if (agent.draft.trim() === message) agent.draft = ''
            const handled = await agent.fillForm(message)
            if (!valid() || handled) return true
          }
          await receipt('表单已打开', `已打开${opener.label}。` + (agent.formFillEnabled ? '可以继续告诉我要填写的内容。' : '当前账号未开通智能填表，请手动填写。'))
          return true
        }
      }      const formInstance = linkedForm.value?.instanceId
      const formValues = linkedForm.value ? JSON.stringify(captureValues(linkedForm.value.formId)) : null
      const response: any = await request.post('/agent/ui/intent', {
        message, currentPage: `${context.value.title} ${linkedForm.value?.title || ''}`,
        targets: targets.map(({ id, label, kind, path }) => ({ id, label: `${label}${kind === 'page' && path === route.path ? '（当前页面）' : ''}`.slice(0, 200), kind }))
      }, { signal: controller.signal, timeout: 60000 })
      if (!valid()) return true
      if (formInstance !== linkedForm.value?.instanceId) throw new Error('表单已变化，本次操作已停止。')
      const result = response.data
      if (result.fill && linkedForm.value && formValues !== JSON.stringify(captureValues(linkedForm.value.formId)))
        throw new Error('表单内容已变化，已保留当前填写，请重新发送填表要求。')
      needsForm = result.form === true || result.fill === true
      if (result.intent === 'chat') { if (opened) break; return 'chat' }
      if (result.intent === 'clarify') {
        await receipt('需要补充信息', result.question)
        return true
      }
      const target = targets.find(item => item.id === result.targetId)
      if (!target || !targetStillAvailable(target, router)) throw new Error('页面入口已变化，请重新发送。')
      if (target.kind === 'form') {
        if (result.fill && agent.formFillEnabled) {
          if (agent.draft.trim() === message) agent.draft = ''
          await agent.fillForm(message)
        } else {
          await receipt('表单已打开', `已打开${target.label}。${agent.formFillEnabled ? '可继续告诉我要填写的内容。' : '当前账号未开通智能填表，请在页面手动填写。'}`)
        }
        return true
      }
      const destination = `${route.path}:${target.kind}:${target.path || target.label}`
      if (visited.has(destination)) throw new Error('未能定位目标表单，本次操作已停止，请明确要打开的入口。')
      visited.add(destination)
      if (target.kind === 'page') {
        if (router.resolve(target.path!).fullPath === route.fullPath) {
          if (result.form || result.fill) throw new Error('已在该页面，但尚未定位目标表单，请明确表单名称。')
          opened = target.label; break
        }
        uiNavigationPath = router.resolve(target.path!).fullPath
        try { await router.push(target.path!) } finally { uiNavigationPath = null }
        if (controller.signal.aborted) return true
        expectedPath = route.fullPath
        uiExpectedPath = expectedPath
        if (!lastOf(router.resolve(target.path!).matched)?.redirect && route.path !== router.resolve(target.path!).path)
          throw new Error('当前权限或页面状态不允许打开该入口。')
      } else target.element!.click()
      opened = target.label
      await nextTick()
      await waitForUiTargets(router, controller.signal)
      if (!valid()) return true
      if (target.kind === 'page' && !result.fill && !result.form) break
    }
    if (needsForm) throw new Error('页面已打开，但未完成表单定位或填写，请明确目标表单后重试。')
    if (opened) {
      await receipt('页面已打开', `已打开${opened}。`)
      return true
    }
    return false
  } catch (error: any) {
    if (valid()) agent.error = error?.response?.data?.message || error?.message || '页面操作失败，请重试。'
    return true
  } finally {
    if (uiController === controller) { uiWorking.value = false; uiController = null; uiNavigationPath = null }
  }
}

async function send(text?: string) {
  if (readOnly.value || !agent.visible || !agent.capabilities?.enabled || agent.loading || !auth.token) return
  const token = auth.token
  const owner = agent.capabilities.actor.ownerKey
  const formInstance = linkedForm.value?.instanceId
  if (text) agent.draft = text
  const message = agent.draft.trim()
  if (!message || agent.busy || uiWorking.value || voiceFinishing.value) return
  atBottom.value = true

  const pendingCaps = ['need_input', 'need_confirm', 'error'].includes(agent.capsFlow.phase) ? agent.capsFlow.message : ''
  const capsMessage = canManageCapabilities.value ? capabilityRequest(message, pendingCaps) : null
  if (capsMessage) {
    if (agent.capsFlow.working) return
    agent.draft = ''
    const accepted = await agent.previewCapsFlow(capsMessage)
    if (!accepted && !agent.draft) agent.draft = message
    await nextTick(); scrollBottom()
    return
  }
  const pendingAuto = !agent.autoFlow.created && ['need_input', 'need_confirm'].includes(agent.autoFlow.phase) ? agent.autoFlow.message : ''
  const autoMessage = canAutomate.value ? automationRequest(message, pendingAuto) : null
  if (autoMessage) {
    if (agent.autoFlow.working) return
    agent.draft = ''
    const ok = await agent.previewAutoFlow(autoMessage)
    if (!ok && !agent.draft) agent.draft = message
    await nextTick(); scrollBottom()
    return
  }
  const hasFillForm = Boolean(linkedForm.value && agent.formFillEnabled)
  if (shouldResolveFormFill(message, { hasForm: hasFillForm })) {
    agent.draft = ''
    const handled = await agent.fillForm(message)
    await nextTick(); scrollBottom()
    if (handled || token !== auth.token || owner !== agent.capabilities?.actor.ownerKey || !agent.visible || readOnly.value || !agent.capabilities?.enabled || !agent.formFillEnabled || formInstance !== linkedForm.value?.instanceId) return
  }
  const uiResult = shouldResolveUiIntent(message)
    ? await handleUiRequest(message)
    : 'chat'
  if (uiResult === true) return
  if (uiResult !== 'chat' && linkedForm.value && agent.formFillEnabled) {
    agent.draft = ''
    const handled = await agent.fillForm(message)
    await nextTick(); scrollBottom()
    if (handled || token !== auth.token || owner !== agent.capabilities?.actor.ownerKey || !agent.visible || readOnly.value || !agent.capabilities?.enabled || !agent.formFillEnabled || formInstance !== linkedForm.value?.instanceId) return
    await agent.ask(context.value, message)
    await nextTick(); scrollToLatest(); return
  }
  await agent.ask(context.value, message)
  await nextTick(); scrollBottom()
}
let voiceEpoch = 0
function stopAsr(preserve = true) {
  const transcript = (asrFinal.value + asrInterim.value).trim()
  voiceEpoch++
  const session = asrSession; asrSession = null
  if (session) { const abort = (session as AsrSession & { abort?: () => void }).abort; if (abort) abort(); else void session.stop() }
  voiceFinishing.value = false
  if (preserve && transcript) {
    const base = agent.draft.trim()
    agent.draft = [base, transcript].filter(Boolean).join(base ? '\n' : '').slice(0, 4000)
  }
  asrState.value = 'idle'; asrFinal.value = ''; asrInterim.value = ''
}
async function finishRecording() {
  const session = asrSession, mine = voiceEpoch
  if (!session || voiceFinishing.value) return
  voiceFinishing.value = true
  try { await session.stop() }
  catch (e: any) {
    if (mine === voiceEpoch) { stopAsr(true); asrState.value = 'error'; asrError.value = e?.message || '语音结束时未取得完整转写，已保留识别文字。' }
  }
  finally {
    if (mine === voiceEpoch) { stopAsr(true); await nextTick(); composer.value?.focus() }
  }
}
function toggleMic() {
  if (asrSession) { finishRecording(); return }
  if (!agent.visible || !canVoice.value || readOnly.value || agent.loading || agent.busy || !auth.token) return
  stopAsr(false); asrError.value = ''
  const mine = voiceEpoch
  const valid = () => mine === voiceEpoch && agent.visible && canVoice.value && !readOnly.value && !agent.loading
  const session = createAsr({
    onInterim: value => { if (valid()) asrInterim.value = value },
    onFinal: value => { if (valid()) asrFinal.value = value },
    onState: (state, detail) => {
      if (!valid()) return
      if (voiceFinishing.value && state === 'idle') return
      if (state !== 'listening') stopAsr(true)
      asrState.value = state
      if (detail) asrError.value = detail
    }
  })
  if (mine !== voiceEpoch) { session?.stop(); return }
  asrSession = session
  if (!session) { asrState.value = 'error'; asrError.value = '当前语音服务不可用，请使用文字输入。' }
}
watch([() => agent.visible, canVoice, readOnly, () => agent.loading, () => linkedForm.value?.instanceId, () => route.fullPath], () => stopAsr(true), { flush: 'sync' })
watch([() => auth.token, () => auth.user?.id, () => agent.capabilities?.actor.ownerKey, () => agent.sessionId], () => { stopAsr(false) }, { flush: 'sync' })
const canUndoFill = (item: any) => !readOnly.value && item.applied && !item.undone && item.expectedInstanceId === linkedForm.value?.instanceId && !['saved', 'submitted', 'published'].includes(item.businessStatus)
function scrollBottom() { if (conversation.value) conversation.value.scrollTop = conversation.value.scrollHeight }
function updateScroll() { const el = conversation.value; if (el) atBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 90; saveScroll() }
function scrollToLatest() { atBottom.value = true; scrollBottom() }
const editingTaskId = ref<string | null>(null)
const editText = ref<string>("")
const copiedId = ref<string | null>(null)
const highlightId = ref<string | null>(null)
let highlightTimer = 0
function chainOf(task: any): any[] { return taskVersions(agent.tasks, task) }
const selectedVersions = ref<Record<string, string>>({})
const timeline = computed(() => {
  const groups = new Map<string, any[]>()
  for (const task of agent.tasks) { const chain = chainOf(task); groups.set(chain[0].id, chain) }
  const items: any[] = [...groups].map(([root, chain]) => {
    const task = chain.find(item => item.id === selectedVersions.value[root]) || chain[chain.length - 1]
    return { id: root, kind: 'task', task, createdAt: chain[0].createdAt || chain[0].updatedAt }
  })
  items.push(...agent.fillEvents.map(event => ({ id: event.id, kind: 'fill', event, createdAt: event.createdAt })))
  items.push(...agent.activityEvents.map(event => ({ id: event.id, kind: 'activity', event, createdAt: event.createdAt })))
  if (agent.autoFlow.phase !== 'idle') items.push({ id: 'auto-preview', kind: 'auto', createdAt: autoStartedAt.value })
  if (agent.capsFlow.phase !== 'idle') items.push({ id: 'caps-preview', kind: 'caps', createdAt: capsStartedAt.value })
  return items.sort((a, b) => a.createdAt - b.createdAt)
})
function isLatestMessage(id: string) {
  if (agent.pendingMessage || agent.busy || uiWorking.value) return false
  const latest = lastOf(timeline.value.filter(item => item.kind !== 'activity' || item.event.message))
  return latest?.kind === 'task' ? latest.task.id === id && lastOf(chainOf(latest.task))?.id === id : latest?.id === id
}
const autoStartedAt = ref(Date.now()), capsStartedAt = ref(Date.now())
watch(() => agent.autoFlow.message, () => { autoStartedAt.value = Date.now() })
watch(() => agent.capsFlow.message, () => { capsStartedAt.value = Date.now() })
function repeatsQuestion(task: any): boolean {
  const chain = chainOf(task)
  const idx = chain.findIndex(item => item.id === task.id)
  if (idx <= 0) return false
  return chain[idx - 1].message === task.message
}function versionLabel(task: any): string {
  const chain = chainOf(task)
  return (chain.findIndex(item => item.id === task.id) + 1) + "/" + chain.length
}
function jumpToTask(id: string) {
  const task = agent.tasks.find(item => item.id === id)
  if (!task) return
  selectedVersions.value[chainOf(task)[0].id] = id
}
async function startEdit(task: any) {
  if (!isLatestMessage(task.id) || agent.busy || uiWorking.value) return
  editingTaskId.value = task.id
  editText.value = task.message
  // 进入编辑态即聚焦输入框，让 Escape 落在编辑区内（全局另有兜底）。
  await nextTick()
  const box = panel.value?.querySelector<HTMLElement>(`#edit-${task.id}`)
  box?.focus()
}
function cancelEdit() {
  editingTaskId.value = null
  editText.value = ""
}
async function saveEdit() {
  if (!editingTaskId.value || !isLatestMessage(editingTaskId.value) || !editText.value.trim()) return
  const id = editingTaskId.value
  const ok = await agent.resendTask(id, editText.value)
  if (ok) {
    editingTaskId.value = null
    editText.value = ""
    await nextTick()
    const newer = agent.tasks.filter(item => item.editOf === id)
    jumpToTask(newer.length ? newer[newer.length - 1].id : id)
  }
}
async function copyText(text: string, id: string) {
  try {
    if (navigator && navigator.clipboard && navigator.clipboard.writeText) await navigator.clipboard.writeText(text)
    else throw new Error("no-clipboard")
  } catch {
    try {
      const area = document.createElement("textarea")
      area.value = text
      document.body.appendChild(area)
      area.select()
      const copied = document.execCommand("copy")
      area.remove()
      if (!copied) throw new Error("copy failed")
    } catch { agent.error = '复制未成功，请选择文字手动复制。'; return }
  }
  copiedId.value = id
  window.setTimeout(() => { if (copiedId.value === id) copiedId.value = null }, 1500)
}
async function resendSame(task: any) {
  const ok = await agent.resendTask(task.id)
  if (ok) { await nextTick(); scrollToLatest() }
}function onKey(event: KeyboardEvent) { if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); void send() } }
let composerFocusTimer = 0
function onComposerFocus() {
  if (composerFocusTimer) window.clearTimeout(composerFocusTimer)
  composerFocusTimer = window.setTimeout(() => {
    composerFocusTimer = 0
    try { composer.value?.scrollIntoView({ block: 'nearest' }) } catch { /* 忽略不支持的环境 */ }
    updateKeyboardGap()
  }, 350)
}
function openApplyDialog(row: Record<string, unknown>) {
  if (readOnly.value) return
  agent.openApply({ id: String(row.id ?? ''), title: row.title, company: row.company })
}
/** 提醒草稿卡独立于相关度过滤：只要本轮核验出草稿，就给出确认入口。 */
function reminderDraftCards(task: any) {
  if (readOnly.value || task.status !== 'SUCCEEDED') return []
  return (task.cards || []).filter((card: any) => card.tool === 'teacher_reminder_draft' && card.status === 'OK')
}
function reminderRows(task: any) {
  const rows = reminderDraftCards(task).flatMap((card: any) => Array.isArray(card.items) ? card.items.filter((item: any) => item.id) : [])
  return [...new Map(rows.map((row: any) => [String(row.studentNo || row.id), row])).values()]
}
function openReminderDialog(row: Record<string, unknown>) {
  if (readOnly.value) return
  agent.openReminder({
    studentQuery: row.studentNo || row.title || row.id,
    studentLabel: row.title, studentNo: row.studentNo, className: row.className,
    emailMasked: row.emailMasked, emailAvailable: row.emailAvailable, content: row.content, channel: row.channel
  })
}
async function navigate(link: AgentLink) {
  const target = businessTarget(link, role.value)
  if (!target) { agent.error = '此记录尚未提供直接定位入口，请在原业务页面核对。'; return }
  const reopenJob = link.action === 'OPEN_JOB' && router.resolve(target).fullPath === route.fullPath
  await router.push(target)
  if (reopenJob) eventBus.emit('showJobDetail', { positionId: link.objectId })
  if (!docked.value) agent.visible = false
}
function confirmedFormReceipt(event: FillEvent) {
  return event.applied && !event.undone && event.receipt && ['saved', 'submitted', 'published'].includes(event.businessStatus || '') && event.receipt.status === event.businessStatus && event.receipt.formId === event.formId && event.receipt.instanceId === event.expectedInstanceId
}
function formReceiptTarget(event: FillEvent): string | null {
  return confirmedFormReceipt(event) ? localReceiptTarget(event.receipt?.url, role.value, (url: string) => router.resolve(url)) : null
}
async function viewFormReceipt(event: FillEvent) {
  const target = formReceiptTarget(event)
  if (!target) return
  await router.push(target)
  if (!docked.value) agent.visible = false
}
async function renameFromHistory(item: any) {
  const next = window.prompt('重命名会话', item.title)
  if (next == null) return
  await agent.renameSession(item.id, next)
}
async function deleteFromHistory(item: any) {

  if (!window.confirm(`确认删除该会话？\n\n只会删除聊天记录，不会删除业务记录、审计或已启用的自动化任务。`)) return
  await agent.deleteSession(item.id)
}
function flushOnHidden() { if (document.hidden) { agent.flushDraft(); stopAsr(true) } }
function resize() { viewportWidth.value = window.innerWidth; readBannerHeight(); updateKeyboardGap() }
function startResize(event: PointerEvent) {
  const handle = event.currentTarget as HTMLElement; handle.setPointerCapture(event.pointerId)
  function move(e: PointerEvent) { width.value = Math.min(560, Math.max(360, window.innerWidth - e.clientX)) }
  function end() { handle.removeEventListener('pointermove', move); handle.removeEventListener('pointerup', end); handle.removeEventListener('pointercancel', end); localStorage.setItem('deepintern.agent.width', String(width.value)) }
  handle.addEventListener('pointermove', move); handle.addEventListener('pointerup', end); handle.addEventListener('pointercancel', end)
}
function resizeKey(event: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight'].includes(event.key)) return
  event.preventDefault(); width.value = Math.min(560, Math.max(360, width.value + (event.key === 'ArrowLeft' ? 20 : -20)))
  localStorage.setItem('deepintern.agent.width', String(width.value))
}
function keyboard(event: KeyboardEvent) {
  if (!agent.visible) return
  // Escape 最高优先级：编辑态先退出编辑，绝不顺带关闭整个侧栏。
  if (event.key === 'Escape' && editingTaskId.value) {
    cancelEdit()
    event.preventDefault()
    event.stopImmediatePropagation()
    return
  }
  if (event.key === 'Escape' && (event.target as HTMLElement)?.closest?.('.msg-edit')) return
  if (event.key === 'Escape' && panel.value?.contains(event.target as Node)) { agent.visible = false; event.preventDefault(); event.stopImmediatePropagation() }
  if (event.key !== 'Tab' || !mobile.value) return
  const nodes = panel.value?.querySelectorAll<HTMLElement>('button:not(:disabled), textarea:not(:disabled), [tabindex="0"], summary')
  if (!nodes?.length) return
  const first = nodes[0], last = nodes[nodes.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}
watch([docked, panelWidth], () => {
  document.body.classList.toggle('agent-docked', docked.value)
  document.body.style.setProperty('--agent-width', `${panelWidth.value}px`)
}, { immediate: true })
const scrollPositions = new Map<string, { top: number; bottom: boolean }>()
function saveScroll() { if (conversation.value) scrollPositions.set(agent.sessionId || 'new', { top: conversation.value.scrollTop, bottom: atBottom.value }) }
async function restoreScroll() {
  await nextTick()
  const saved = scrollPositions.get(agent.sessionId || 'new')
  if (saved && conversation.value) { conversation.value.scrollTop = saved.top; atBottom.value = saved.bottom }
  else { atBottom.value = true; scrollBottom() }
}
let journeyTimer: number | undefined
function refreshVisibleJourney() { if (agent.visible && !document.hidden) void agent.refreshPlan() }
watch(() => agent.visible, async open => {
  window.clearInterval(journeyTimer)
  if (open) { void agent.refreshPlan(); journeyTimer = window.setInterval(refreshVisibleJourney, 60000) }
  if (open) { lastFocus = document.activeElement as HTMLElement; await restoreScroll(); if (!mobile.value) composer.value?.focus({ preventScroll: true }) }
  else { agent.flushDraft(); await nextTick(); if (lastFocus?.isConnected) lastFocus.focus({ preventScroll: true }) }
})
watch(() => agent.sessionId, async () => { selectedVersions.value = {}; await restoreScroll() })
async function loadOlder() {
  const before = conversation.value?.scrollHeight || 0, top = conversation.value?.scrollTop || 0
  await agent.loadOlderTasks(); await nextTick()
  if (conversation.value) conversation.value.scrollTop = top + conversation.value.scrollHeight - before
}
watch(() => agent.tasks.map(task => task.version).join(','), async () => { if (atBottom.value) { await nextTick(); scrollBottom() } })
watch(() => auth.token, () => { if (auth.token) void agent.initialize(); else agent.dispose() }, { immediate: true })
onMounted(() => {
  window.addEventListener('focus', refreshVisibleJourney)
  onStudentRecentActivityChanged(agent.refreshPlan)
  document.body.classList.add('business-overlay-below-header')
  readBannerHeight(); window.addEventListener('resize', resize); document.addEventListener('keydown', keyboard, true)
  try { window.visualViewport?.addEventListener('resize', updateKeyboardGap); window.visualViewport?.addEventListener('scroll', updateKeyboardGap); updateKeyboardGap() } catch { /* 忽略不支持的环境 */ }
  bannerObserver = new MutationObserver(readBannerHeight)
  const host = document.querySelector('.agent-host')
  if (host) {
    bannerObserver.observe(host, { childList: true, subtree: true, attributes: true, attributeFilter: ['style'] })
    headerObserver = new ResizeObserver(readBannerHeight)
    for (const el of host.querySelectorAll('.header, :scope > .read-only-banner')) headerObserver.observe(el)
  }
  document.addEventListener('visibilitychange', flushOnHidden)
})
onBeforeUnmount(() => {
  window.clearInterval(journeyTimer); window.removeEventListener('focus', refreshVisibleJourney)
  offStudentRecentActivityChanged(agent.refreshPlan)
  if (composerFocusTimer) window.clearTimeout(composerFocusTimer)
  try { window.visualViewport?.removeEventListener('resize', updateKeyboardGap); window.visualViewport?.removeEventListener('scroll', updateKeyboardGap) } catch { /* 忽略 */ }
  stopAsr()
  agent.dispose(); document.body.classList.remove('agent-docked')
  document.body.style.removeProperty('--agent-width')
  document.body.classList.remove('business-overlay-below-header')
  document.body.style.removeProperty('--business-overlay-top')
  bannerObserver?.disconnect(); headerObserver?.disconnect(); document.removeEventListener('visibilitychange', flushOnHidden)
  window.removeEventListener('resize', resize); document.removeEventListener('keydown', keyboard, true)
})
</script>
<template>
  <Teleport to="body">
    <aside v-show="agent.visible" id="agent-workspace" ref="panel" class="agent-workspace" :class="{ overlay: !docked }" :style="{ width: `${panelWidth}px`, top: `${panelTop}px`, height: `${panelHeight}px` }" :role="mobile ? 'dialog' : 'complementary'" :aria-modal="mobile || undefined" aria-labelledby="agent-title">
      <div v-if="docked" class="agent-resize" role="separator" tabindex="0" aria-label="调整 AI 工作区宽度" aria-orientation="vertical" :aria-valuenow="width" aria-valuemin="360" aria-valuemax="560" @pointerdown="startResize" @keydown="resizeKey" />
      <header class="agent-header" @keydown.esc.stop>
        <div class="agent-symbol" aria-hidden="true">✦</div>
        <h2 id="agent-title">Intern AI</h2>
        <div class="agent-header-actions">
          <button v-if="canAutomate" type="button" aria-label="自动化任务" title="自动化任务" :aria-expanded="showAutomation" @click="showAutomation = !showAutomation; agent.showHistory = false"><Timer /></button>
          <button type="button" :disabled="agent.busy || agent.operationWorking || uiWorking" aria-label="新会话" title="新会话" @click="showAutomation = false; agent.newSession()"><Plus /></button>
          <button type="button" aria-label="历史会话" title="历史会话" :aria-expanded="agent.showHistory" @click="showAutomation = false; agent.showHistory = !agent.showHistory; if (agent.showHistory) void agent.loadSessions()"><Clock /></button>
          <button type="button" aria-label="关闭 AI 助手" title="关闭" @click="agent.visible = false"><Close /></button>
        </div>
      </header>
      <AutomationManager v-if="showAutomation && canAutomate" :key="agent.capabilities?.actor.ownerKey" @close="showAutomation = false" @example="useAutomationExample" @edit="editAutomation" />
      <p v-if="(agent.capabilities as any)?.persistenceAvailable === false" class="storage-note" role="status">历史记录暂未开通持久保存，草稿仍保留在此设备。</p>
      <div v-if="agent.showHistory" class="session-history" role="region" aria-label="会话历史">
        <div class="history-head"><strong>历史会话</strong><button type="button" aria-label="关闭历史列表" title="关闭历史列表" @click="agent.showHistory = false"><Close /></button></div>
        <p v-if="agent.sessionsLoading" class="history-empty">正在加载…</p>
        <p v-else-if="!agent.sessions.length" class="history-empty">还没有历史会话。</p>
        <ul v-else>
          <li v-for="item in agent.sessions" :key="item.id" :class="{ active: item.id === agent.sessionId }">
            <button type="button" class="history-open" :aria-current="item.id === agent.sessionId ? 'true' : undefined" :disabled="agent.busy || agent.operationWorking || uiWorking" @click="agent.switchSession(item.id); agent.showHistory = false">
              <span class="history-title">{{ item.title }}</span>
              <small>{{ sessionDate(item.updatedAt) }} · {{ item.taskCount }} 条{{ item.hasRunningTask ? ' · 执行中' : '' }}</small>
            </button>
            <button type="button" class="history-act" aria-label="重命名" title="重命名" @click="renameFromHistory(item)"><EditPen /></button>

            <button type="button" class="history-act danger" aria-label="删除" title="删除" @click="deleteFromHistory(item)"><Delete /></button>
          </li>
        </ul>
        <button v-if="agent.sessionsHasMore" type="button" :disabled="agent.sessionsLoading" @click="agent.loadSessions(true)">加载更多会话</button>
      </div>
      <div v-if="linkedForm && agent.formFillEnabled" class="agent-form-link"><span class="form-link-icon" aria-hidden="true"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="2" y="6" width="20" height="12" rx="2"/><path d="M6 10h.01M10 10h.01M14 10h.01M18 10h.01M6 14h.01M18 14h.01M9 14h6"/></svg></span><div><strong>{{ linkedForm.title }}</strong></div></div>
      <div ref="conversation" class="agent-conversation" :class="{ 'is-empty': !timeline.length }" role="log" aria-live="polite" aria-label="对话记录" @scroll="updateScroll"><button v-if="agent.historyHasMore" type="button" class="older-messages" @click="loadOlder">加载更早消息</button>
        <div v-if="!timeline.length" class="agent-welcome"><div class="welcome-star" aria-hidden="true">✦</div><h3>从你现在的任务开始</h3><button v-if="canAutomate && !showAutomation" type="button" class="automation-shortcut" @click="useAutomationExample(automationTemplate)"><Timer aria-hidden="true" />创建自动化任务</button></div>
        <template v-for="entry in timeline" :key="entry.id">
        <article v-for="task in (entry.kind === 'task' ? [entry.task] : [])" :key="task.id" class="agent-turn" :class="{ flash: highlightId === task.id }" :data-task-id="task.id" :data-task-status="task.status">
          <div v-if="editingTaskId === task.id" class="msg-edit"><label class="sr-only" :for="`edit-${task.id}`">编辑消息</label><textarea :id="`edit-${task.id}`" v-model="editText" rows="3" maxlength="4000" @keydown.escape.stop="cancelEdit" @keydown.enter.exact.prevent="saveEdit" /><div class="msg-actions"><button type="button" :disabled="!editText.trim() || agent.sending" @click="saveEdit">发送</button><button type="button" @click="cancelEdit">取消</button></div></div><template v-else><div class="user-question">{{ task.message }}</div><div class="msg-actions user-actions"><time v-if="task.createdAt" class="message-time" :datetime="new Date(task.createdAt).toISOString()">{{ messageTime(task.createdAt) }}</time><button type="button" class="message-icon" :aria-label="copiedId === task.id ? '已复制' : '复制'" :title="copiedId === task.id ? '已复制' : '复制'" @click="copyText(task.message, task.id)"><Check v-if="copiedId === task.id" /><CopyDocument v-else /></button><button v-if="!readOnly && isLatestMessage(task.id)" type="button" class="message-icon" aria-label="编辑" title="编辑" @click="startEdit(task)"><EditPen /></button><span v-if="chainOf(task).length > 1" class="version-pager">第{{ versionLabel(task) }}<template v-for="v in chainOf(task)" :key="v.id"><button v-if="v.id !== task.id" type="button" :aria-label="`查看第${chainOf(task).findIndex(item => item.id === v.id) + 1}版`" @click="jumpToTask(v.id)">{{ chainOf(task).findIndex(item => item.id === v.id) + 1 }}</button><strong v-else>{{ chainOf(task).findIndex(item => item.id === v.id) + 1 }}</strong></template></span></div></template>
          <div v-if="!terminal(task.status)" class="thinking-indicator" role="status"><span class="thinking-star" aria-hidden="true">✦</span>正在思考</div>
          <p v-if="task.answer" class="agent-answer"><span v-if="terminal(task.status)" class="ai-message-mark" aria-hidden="true">✦</span><template v-for="(part, index) in answerParts(task.answer)" :key="index"><strong v-if="part.bold" class="answer-text">{{ part.text }}</strong><span v-else class="answer-text">{{ part.text }}</span></template></p><p v-if="task.errorMessage" class="agent-task-error" :class="{ 'agent-task-neutral': task.status === 'CANCELLED' }" :role="task.status === 'CANCELLED' ? 'status' : 'alert'">{{ task.errorMessage }}</p>
          <div v-if="terminal(task.status)" class="answer-links">
            <template v-for="(card, index) in relevantAnswerCards(task, role)" :key="index">
              <button v-for="link in card.links" :key="link.action + (link.objectId || '')" type="button" @click="navigate(link)">{{ link.label }}</button>
              <template v-if="agent.applyCapability && !readOnly && allowsWriteAction(task, 'JOB_APPLY') && ['jobs_details', 'jobs_search'].includes(card.tool)">
                <button v-for="row in card.items.filter(item => item.id)" :key="String(row.id)" type="button" @click="openApplyDialog(row)">投递 · {{ row.title || row.positionName || row.position || '岗位' }}</button>
              </template>
            </template>
            <template v-for="row in reminderRows(task)" :key="'remind-row-' + String(row.studentNo || row.id)">
              <button type="button" :class="{ sent: agent.reminderSent(row.studentNo || row.id) }" :disabled="agent.reminderSent(row.studentNo || row.id)" @click="openReminderDialog(row)">{{ agent.reminderSent(row.studentNo || row.id) ? "已发送 · " : "发送提醒 · " }}{{ row.title || "学生" }}</button>
            </template>
          </div>
          <div v-if="task.answer || task.errorMessage" class="msg-actions ai-actions">
            <time v-if="terminal(task.status) && task.updatedAt" class="message-time" :datetime="new Date(task.updatedAt).toISOString()">{{ messageTime(task.updatedAt) }}</time>
            <button v-if="task.answer" type="button" class="message-icon" :aria-label="copiedId === task.id + ':a' ? '已复制' : '复制回答'" :title="copiedId === task.id + ':a' ? '已复制' : '复制'" @click="copyText(task.answer, task.id + ':a')"><Check v-if="copiedId === task.id + ':a'" /><CopyDocument v-else /></button>
            <button v-if="canRepeatTask(task)" type="button" class="message-icon" aria-label="重试回复" title="重试" :disabled="agent.sending" @click="resendSame(task)"><RefreshRight /></button>
          </div>
        </article>
        <article v-for="event in (entry.kind === 'fill' ? [entry.event] : [])" :key="event.id" class="agent-turn fill-event" :data-fill-status="event.undone ? 'undone' : event.applied ? 'applied' : 'blocked'">
          <div class="user-question">{{ event.message }}</div>
          <div class="msg-actions user-actions">
            <time v-if="event.createdAt" class="message-time" :datetime="new Date(event.createdAt).toISOString()">{{ messageTime(event.createdAt) }}</time>
            <button type="button" class="message-icon" :aria-label="copiedId === event.id ? '已复制' : '复制'" :title="copiedId === event.id ? '已复制' : '复制'" @click="copyText(event.message, event.id)"><Check v-if="copiedId === event.id" /><CopyDocument v-else /></button>
            <button v-if="!readOnly && isLatestMessage(event.id)" type="button" class="message-icon" aria-label="编辑" title="编辑" @click="agent.draft = event.message; composer?.focus()"><EditPen /></button>
            <button v-if="canUndoFill(event)" type="button" class="message-icon" aria-label="撤销本次填写" title="撤销本次填写" @click="agent.undoFill(event.fillBatchId)"><RefreshLeft /></button>
          </div>
          <div class="task-object">{{ event.formTitle }} · 智能填表</div>
          <p v-if="event.applied && !event.undone" class="agent-answer">已填写 <strong>{{ event.assignments.length }}</strong> 项<template v-if="event.missingFields?.length">，还缺{{ event.missingFields.join('、') }}</template>。<strong>{{ ({ saved: '已保存', submitted: '已提交', published: '已发布' } as Record<string, string>)[event.businessStatus] || '尚未保存或提交' }}。</strong></p>
          <p v-else-if="event.undone" class="agent-answer">这批填写已撤销。<template v-if="event.preservedFields?.length">以下字段在 AI 填写后又被手动修改，已保留当前值：<strong>{{ event.preservedFields.join('、') }}</strong>。</template><template v-else>字段已恢复为填写前的值。</template></p>
          <p v-else-if="event.error" class="agent-task-error" role="alert">{{ event.error }}</p>
          <p v-else-if="event.questions.length" class="agent-answer">需要你确认：<strong>{{ event.questions.join(' ') }}</strong></p>
          <p v-else-if="event.skippedFields.length" class="agent-answer">{{ event.skippedFields.map(item => `${item.label || '该字段'}：${item.reason}`).join('；') }}</p>
          <p v-else class="agent-answer">本次未填写字段，请补充需要填写的内容。</p>
          <div v-if="confirmedFormReceipt(event)" class="fill-business-receipt"><p v-if="event.receipt?.label">{{ event.receipt.label }}</p><button v-if="formReceiptTarget(event)" type="button" class="receipt-link" @click="viewFormReceipt(event)">查看业务页面 ↗</button></div>
          <details v-if="event.assignments.length" class="fill-changes"><summary>查看修改</summary><div class="fill-result">
            <div v-for="item in event.assignments" :key="item.prop" class="fill-row"><span>{{ item.label || item.prop }}</span><span class="previous-value">{{ event.beforeValues?.[item.prop] ?? '空' }}</span><strong>→ {{ item.operation === 'CLEAR' ? '已清空' : item.value }}</strong></div>
          </div>
          </details>
          <div v-if="event.validationErrors?.length" class="agent-task-error">{{ event.validationErrors.join('；') }}</div>
          <div v-if="event.completedAt" class="msg-actions"><time class="message-time" :datetime="new Date(event.completedAt).toISOString()">{{ messageTime(event.completedAt) }}</time></div>
        </article>
        <article v-if="entry.kind === 'auto' && agent.autoFlow.phase !== 'idle'" class="agent-turn auto-flow" :data-flow-phase="agent.autoFlow.phase">
          <div class="user-question">{{ agent.autoFlow.message }}</div>
          <div class="msg-actions user-actions"><time class="message-time" :datetime="new Date(autoStartedAt).toISOString()">{{ messageTime(autoStartedAt) }}</time><button type="button" class="message-icon" :aria-label="copiedId === entry.id + ':q' ? '已复制' : '复制'" :title="copiedId === entry.id + ':q' ? '已复制' : '复制'" @click="copyText(agent.autoFlow.message, entry.id + ':q')"><Check v-if="copiedId === entry.id + ':q'" /><CopyDocument v-else /></button></div>
          <p v-if="agent.autoFlow.working" class="task-status" role="status">正在核对任务…</p>
          <AutomationPreview v-if="agent.autoFlow.draft && !agent.autoFlow.created && !agent.autoFlow.working" :preview="agent.autoFlow.draft" :session-id="agent.sessionId" @updated="agent.updateAutoPreview" @confirmed="confirmAutoTask" @error="agent.noteAutoTaskError($event?.taskId, $event?.message, $event?.operation)" @cancel="agent.dismissAutoDraft()" />
          <p v-if="agent.autoFlow.error" class="agent-task-error" role="alert">{{ agent.autoFlow.error }}</p>
        </article>
        <article v-if="entry.kind === 'caps' && agent.capsFlow.phase !== 'idle'" class="agent-turn caps-flow" :data-flow-phase="agent.capsFlow.phase">
          <div class="user-question">{{ agent.capsFlow.message }}</div>
          <div class="task-object">权限变更预览</div>
          <div class="task-status" role="status"><span :class="{ 'busy-dot': ['previewing', 'saving'].includes(agent.capsFlow.phase) }">{{ agent.capsFlow.phase === 'done' ? '✓' : '◌' }}</span>{{ capsPhaseLabel[agent.capsFlow.phase] || agent.capsFlow.phase }}</div>
          <p v-if="agent.capsFlow.questions.length" class="agent-answer">需要确认：<strong>{{ agent.capsFlow.questions.join(' ') }}</strong>（有待确认项时不可保存）</p>
          <div v-if="agent.capsFlow.preview" class="fill-result">
            <div v-for="(change, index) in (agent.capsFlow.preview.changes || [])" :key="'c' + index" class="fill-row"><span>{{ permissionLabel(change.role) }} · {{ permissionLabel(change.capability) }}</span><strong>{{ change.from ? '开启' : '关闭' }} → {{ change.to ? '开启' : '关闭' }}</strong></div>
            <div v-for="(change, index) in (agent.capsFlow.preview.policyChanges || [])" :key="'p' + index" class="fill-row"><span>{{ permissionLabel(change.role) }} · {{ permissionLabel(change.field) }}</span><strong>{{ permissionValue(change.from) }} → {{ permissionValue(change.to) }}</strong></div>
            <div v-if="!(agent.capsFlow.preview.changes || []).length && !(agent.capsFlow.preview.policyChanges || []).length" class="fill-row"><span>变更</span><strong>本次指令未产生角色级能力变化</strong></div>
            <div v-for="change in (agent.capsFlow.preview.userChanges || [])" :key="change.roleKey + change.userId" class="fill-row"><span>{{ change.name || change.userId }} · {{ change.account || permissionLabel(change.roleKey || change.role) }}</span><strong>{{ userPermissionChanges(change) }}</strong></div><div class="fill-row"><span>影响</span><strong>将停止 {{ agent.capsFlow.preview.impact?.tasksAtRisk ?? 0 }} 个任务 · {{ agent.capsFlow.preview.impact?.usersWithOverrides ?? 0 }} 人有用户级覆盖</strong></div>
            <div v-for="(task, index) in (agent.capsFlow.preview.impact?.tasks || [])" :key="'t' + index" class="fill-row"><span>{{ task.teacherName || '教师' }}</span><strong>{{ task.name }}：{{ task.reason }}</strong></div>
          </div>
          <p v-if="agent.capsFlow.saved" class="agent-answer">已保存并重新核验。暂停任务 <strong>{{ (agent.capsFlow.saved.pausedTasks || []).length }}</strong> 个，详情可在“权限管理页”核对。</p>
          <p v-if="agent.capsFlow.error" class="agent-task-error" role="alert">{{ agent.capsFlow.error }}</p>
          <div class="flow-actions">
            <button v-if="['need_input', 'need_confirm', 'error'].includes(agent.capsFlow.phase) && agent.capsFlow.preview && !agent.capsFlow.saved" type="button" :disabled="agent.capsFlow.working || agent.capsFlow.questions.length > 0 || !agent.capsFlow.preview.actionId" @click="agent.confirmCapsFlow()">确认并保存</button>
            <button type="button" :disabled="agent.capsFlow.working" @click="agent.resetCapsFlow()">取消/重新描述</button>
          </div>
        </article>
        <article v-if="entry.kind === 'activity'" class="agent-turn action-receipt">
          <div v-if="entry.event.message" class="user-question">{{ entry.event.message }}</div>
          <div v-if="entry.event.message" class="msg-actions user-actions"><time v-if="(entry.event.messageAt || entry.event.createdAt)" class="message-time" :datetime="new Date((entry.event.messageAt || entry.event.createdAt)).toISOString()">{{ messageTime((entry.event.messageAt || entry.event.createdAt)) }}</time><button type="button" class="message-icon" :aria-label="copiedId === entry.id + ':q' ? '已复制' : '复制'" :title="copiedId === entry.id + ':q' ? '已复制' : '复制'" @click="copyText(entry.event.message, entry.id + ':q')"><Check v-if="copiedId === entry.id + ':q'" /><CopyDocument v-else /></button></div>
          <p class="agent-answer"><span class="ai-message-mark" aria-hidden="true">✦</span><strong>{{ entry.event.title }}</strong><br>{{ entry.event.detail }}</p>
          <div class="msg-actions ai-actions"><time v-if="entry.event.createdAt" class="message-time" :datetime="new Date(entry.event.createdAt).toISOString()">{{ messageTime(entry.event.createdAt) }}</time><button type="button" class="message-icon" :aria-label="copiedId === entry.id + ':a' ? '已复制' : '复制回答'" :title="copiedId === entry.id + ':a' ? '已复制' : '复制'" @click="copyText(entry.event.title + '：' + entry.event.detail, entry.id + ':a')"><Check v-if="copiedId === entry.id + ':a'" /><CopyDocument v-else /></button></div>
          <button v-if="entry.event.link" type="button" class="receipt-link" @click="navigate(entry.event.link)">查看业务记录 ↗</button>
        </article>
        </template>
      </div>
      <div v-if="agent.applyDialog.open" class="apply-dialog" role="dialog" aria-modal="true" aria-labelledby="apply-title">
        <div class="dialog-card">
        <h3 id="apply-title">确认投递</h3>
        <template v-if="!agent.applyDialog.receipt">
          <p>岗位：<strong>{{ agent.applyDialog.title || ('#' + agent.applyDialog.positionId) }}</strong></p>
          <p>企业：<strong>{{ agent.applyDialog.company || '—' }}</strong></p>
          <p class="apply-note">提交后可在“我的申请”查看进度，也可撤回重投。请核对后再确认。</p>
          <p v-if="agent.applyDialog.error" class="agent-task-error" role="alert">{{ agent.applyDialog.error }}</p>
          <p v-if="agent.applyDialog.verifyState === 'pending'" class="apply-note">结果未确认前不会重复提交；核验到已投递会直接显示真实回执。</p>
          <div class="apply-actions">
            <button type="button" @click="agent.closeApply()" :disabled="agent.applyDialog.loading">取消</button>
            <button v-if="agent.applyDialog.verifyState === 'pending'" type="button" @click="agent.verifyApply()" :disabled="agent.applyDialog.loading">{{ agent.applyDialog.loading ? '核验中…' : '刷新状态' }}</button>
            <button type="button" class="apply-confirm" @click="agent.confirmApply()" :disabled="agent.applyDialog.loading || !agent.applyDialog.actionId || agent.applyDialog.verifyState === 'pending'">{{ agent.applyDialog.loading ? (agent.applyDialog.actionId ? '提交中…' : '核对中…') : '确认投递' }}</button>
          </div>
        </template>
        <template v-else>
          <p class="agent-answer">投递成功：<strong>{{ agent.applyDialog.receipt.position }}</strong>（{{ agent.applyDialog.receipt.company }}）</p>
          <p>申请记录 #{{ agent.applyDialog.receipt.applicationId }} · 状态{{ agent.applyDialog.receipt.status }}</p>
          <p v-if="agent.applyDialog.receipt.plan">计划已更新：当前{{ agent.applyDialog.receipt.plan.stageLabel }}，下一步{{ agent.applyDialog.receipt.plan.nextTitle }}。</p>
          <div class="apply-actions">
            <button type="button" @click="navigate({ label: '去我的申请', action: 'OPEN_APPLICATION', parameters: {} }); agent.closeApply()">去我的申请核对 ↗</button>
            <button type="button" @click="agent.closeApply()">关闭</button>
          </div>
        </template>
        </div>
      </div>
      <div v-if="agent.reminderDialog.open" class="apply-dialog reminder-dialog" role="dialog" aria-modal="true" aria-labelledby="reminder-title">
        <div class="dialog-card">
        <h3 id="reminder-title">确认发送提醒</h3>
        <template v-if="!agent.reminderDialog.receipt">
          <p>学生：<strong>{{ agent.reminderDialog.studentLabel || agent.reminderDialog.studentNo || '—' }}</strong><template v-if="agent.reminderDialog.className"> · {{ agent.reminderDialog.className }}</template></p>
          <p>渠道：<strong>{{ agent.reminderDialog.channel === 'EMAIL' ? '邮件' : '站内提醒' }}</strong><button v-if="agent.reminderDialog.emailAvailable" type="button" class="reminder-switch" @click="agent.reminderDialog.channel = agent.reminderDialog.channel === 'EMAIL' ? 'STATION_REMINDER' : 'EMAIL'">切换为{{ agent.reminderDialog.channel === 'EMAIL' ? '站内提醒' : '邮件' }}</button></p>
          <p v-if="agent.reminderDialog.channel === 'EMAIL'" class="apply-note">将发送到学生绑定邮箱：{{ agent.reminderDialog.emailMasked || '未绑定' }}</p>
          <p v-else class="apply-note">将写入学生的站内提醒，并在学生端实时推送。</p>
          <label class="reminder-field" for="reminder-content">提醒内容
            <textarea id="reminder-content" v-model="agent.reminderDialog.content" rows="4" maxlength="2000" placeholder="例如：记得早点休息，明天按时到岗。" />
          </label>
          <p v-if="agent.reminderDialog.error" class="agent-task-error" role="alert">{{ agent.reminderDialog.error }}</p>
          <p v-if="agent.reminderDialog.verifyState === 'pending'" class="apply-note">结果未确认前不会重复发送；核验到已发送会直接显示真实回执。</p>
          <div class="apply-actions">
            <button type="button" @click="agent.closeReminder()" :disabled="agent.reminderDialog.loading">取消</button>
            <button v-if="agent.reminderDialog.verifyState === 'pending'" type="button" @click="agent.verifyReminder()" :disabled="agent.reminderDialog.loading">{{ agent.reminderDialog.loading ? '核验中…' : '刷新状态' }}</button>
            <button type="button" class="apply-confirm" @click="agent.confirmReminder()" :disabled="agent.reminderDialog.loading || !agent.reminderDialog.content.trim() || agent.reminderDialog.verifyState === 'pending'">{{ agent.reminderDialog.loading ? '发送中…' : '确认发送' }}</button>
          </div>
        </template>
        <template v-else>
          <p class="agent-answer">已发送给<strong>{{ agent.reminderDialog.receipt.studentName || agent.reminderDialog.receipt.studentNo }}</strong>（{{ agent.reminderDialog.receipt.channel === 'EMAIL' ? '邮件' : '站内提醒' }}）</p>
          <p>{{ agent.reminderDialog.receipt.message }} · {{ agent.reminderDialog.receipt.sentAt }}</p>
          <div class="apply-actions">
            <button type="button" @click="agent.closeReminder()">关闭</button>
          </div>
        </template>
        </div>
      </div>
      <div v-if="agent.pendingMessage" class="agent-turn pending-bubble" :data-pending-state="agent.pendingMessage.state"><div class="user-question">{{ agent.pendingMessage.message }}</div><div v-if="agent.pendingMessage.state === 'sending'" class="pending-thinking" role="status" aria-label="正在连接助手"><span class="thinking-star" aria-hidden="true">✦</span></div><div v-if="agent.pendingMessage.state === 'failed'" class="task-status" role="status"><span class="busy-dot" aria-hidden="true">◌</span>发送失败，已保留草稿</div><p v-if="agent.pendingMessage.state === 'failed' && agent.pendingMessage.error" class="agent-task-error" role="alert">{{ agent.pendingMessage.error }}</p><div v-if="agent.pendingMessage.state === 'failed'" class="pending-actions"><button type="button" :disabled="agent.sending" @click="agent.retryPending">重试</button><button type="button" @click="agent.editPending(); composer?.focus()">编辑</button><button type="button" @click="agent.dismissPending">取消</button></div></div>
      <div v-if="agent.error" class="agent-error" role="alert"><span>{{ agent.error }}</span><button type="button" :disabled="agent.sending" @click="agent.reconnect">恢复连接</button></div>
      <div v-if="agent.capabilities && !agent.capabilities.enabled" class="agent-error">系统管理员暂未启用 AI 助手。</div>
      <div class="back-to-latest-wrap">
        <button v-if="!atBottom" type="button" class="back-to-latest" aria-label="回到最新" title="回到最新" @click="scrollToLatest"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 5v14m-6-6 6 6 6-6"/></svg></button>
      </div>
      <footer class="agent-composer"><div v-if="agent.draftConflict !== null" class="agent-error">另一处有不同的草稿，当前输入已保留。<button type="button" @click="agent.resolveDraftConflict(false)">保留当前</button><button type="button" @click="agent.resolveDraftConflict(true)">恢复另一份草稿</button></div>
        <div v-if="asrState === 'listening'" class="asr-status" role="status">
          <span class="asr-pulse" aria-hidden="true" />{{ voiceFinishing ? '正在转为文字…' : '正在录音…' }}<em v-if="asrFinal || asrInterim">{{ asrFinal }}{{ asrInterim }}</em>
        </div>
        <div v-if="asrState === 'error' && asrError" class="asr-status asr-error" role="alert">{{ asrError }}</div>
        <div v-if="readOnly" class="agent-readonly-note"><i aria-hidden="true" />管理员只读身份：助手仅供查看，不可发起对话</div><label class="sr-only" for="agent-input">输入实习相关任务</label><div class="composer-field"><StudentJourney /><textarea id="agent-input" ref="composer" v-model="agent.draft" rows="2" maxlength="4000" enterkeyhint="send" :disabled="readOnly || !agent.capabilities?.enabled || agent.loading" :placeholder="readOnly ? '只读身份不可发起对话' : '问问我能够做什么...'" @keydown="onKey" @focus="onComposerFocus" /><button v-if="canVoice && !readOnly && agent.capabilities?.enabled" type="button" class="mic-button" :class="{ listening: asrState === 'listening' }" :aria-label="asrState === 'listening' ? '结束录音并核对' : '开始语音输入'" :title="asrState === 'listening' ? '结束录音并核对' : '语音输入'" @click="toggleMic"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 10a7 7 0 0 0 14 0"/><path d="M12 19v3"/></svg></button><button type="button" class="send-button" :class="{ 'stop-button': agent.busy || uiWorking }" :disabled="uiWorking ? false : agent.busy ? Boolean(activeTask && (activeTask.status === 'CANCELLING' || agent.isCancelling(activeTask.id))) : readOnly || voiceFinishing || !agent.draft.trim() || !agent.capabilities?.enabled" :aria-label="agent.busy || uiWorking ? '停止查询' : '发送任务'" :title="agent.busy ? '停止生成' : '发送'" @click="uiWorking ? uiController?.abort() : agent.busy ? agent.cancel() : send()"><svg v-if="agent.busy || uiWorking" aria-hidden="true" viewBox="0 0 24 24" width="18" height="18"><rect x="6" y="6" width="12" height="12" rx="2" fill="currentColor" /></svg><svg v-else aria-hidden="true" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 19V5m-6 6 6-6 6 6" /></svg></button></div><p v-if="agent.draft.length >= 4000" class="composer-hint" role="status">已达到 4000 字上限，请精简后发送。</p>
      </footer>
    </aside>
  </Teleport>
</template>
<style>
/* 停靠时顶栏与只读横幅横贯全宽，仅内容区为 AI 面板让位 */
body.agent-docked #app .agent-host{box-sizing:border-box;width:100vw;max-width:100vw;padding-right:var(--agent-width)}
body.agent-docked #app .agent-host .header,body.agent-docked #app .agent-host .read-only-banner{margin-right:calc(0px - var(--agent-width))}
body.agent-docked .agent-host.teacher-layout>.el-container{overflow:visible;min-height:0}
.agent-workspace,.agent-workspace *{box-sizing:border-box}
/* 侧边栏位于业务内容之上、Element Plus 弹窗层（2000 起）之下，避免遮挡确认框及其遮罩。 */
.agent-workspace{--ai-message-size:13px;--ai-primary:#1677ff;--ai-text:#1f2a3d;--ai-sub:#66727f;--ai-faint:#98a2b3;--ai-line:#e5eaf1;--ai-soft:#f4f6f9;--ai-ok:#16a34a;--ai-warn:#92580a;--ai-warn-bg:#fff7e6;--ai-warn-line:#f0e0b8;--ai-err:#b42318;--ai-err-bg:#fef3f1;position:fixed;right:0;z-index:1999;display:flex;flex-direction:column;background:#fff;color:var(--ai-text);border-left:1px solid var(--ai-line);box-shadow:-8px 0 32px rgba(20,40,70,.08);font:13px/1.65 "Microsoft YaHei",system-ui,-apple-system,"PingFang SC",sans-serif}
.agent-workspace.overlay{box-shadow:-12px 0 48px rgba(20,40,70,.18)}
.agent-workspace button,.agent-workspace textarea{font:inherit}
.agent-workspace button{cursor:pointer}
.agent-workspace button:disabled{cursor:default;opacity:.45}
.agent-workspace button:focus-visible,.agent-workspace textarea:focus-visible,.agent-resize:focus-visible{outline:2px solid var(--ai-primary);outline-offset:2px}.agent-workspace :where(button){color:var(--ai-text);background:transparent;background-image:none;border:0;box-shadow:none;padding:0;font-weight:400;transform:none;letter-spacing:normal}.agent-workspace :where(button):hover{background-color:transparent;border-color:transparent;box-shadow:none;transform:none}.agent-workspace :where(button):active{transform:none;box-shadow:none}
.agent-workspace ::-webkit-scrollbar{width:6px;height:6px}
.agent-workspace ::-webkit-scrollbar-thumb{background:#d4dbe4;border-radius:3px}
/* ---- 顶部：标题 + 身份状态，一行到底 ---- */

.agent-resize{position:absolute;left:-4px;top:0;bottom:0;width:8px;cursor:ew-resize;touch-action:none;z-index:1}
.agent-resize:hover{background:rgba(22,119,255,.14)}
.agent-header{display:flex;gap:10px;align-items:center;padding:12px 8px 8px 16px;background:#fff;flex-shrink:0}
.agent-symbol{width:32px;height:32px;display:grid;place-items:center;border-radius:10px;background:#e9f1fe;font-size:28px;line-height:1;color:var(--ai-primary);flex-shrink:0;padding-bottom:2px}
.agent-header h2{margin:0;font-family:"Intern Nunito","Arial Rounded MT Bold",sans-serif;font-size:28px;font-weight:800;letter-spacing:-.5px;line-height:32px;color:#16233a}
.agent-header p{margin:1px 0 0;font-size:11px;color:var(--ai-sub)}
/* ---- 模式切换：分段控件 ---- */
/* ---- 上下文：单行胶囊 ---- */
.agent-context{display:flex;flex-wrap:wrap;align-items:center;gap:2px 6px;margin:10px 16px 0;padding:7px 11px;border:1px solid #edf0f4;border-radius:8px;background:#f8fafc;flex-shrink:0;font-size:11px}
.context-eyebrow{color:var(--ai-faint)}
.agent-context strong{font-size:11.5px;font-weight:600;color:var(--ai-text)}
.context-object{color:var(--ai-primary);border-left:1px solid var(--ai-line);padding-left:6px}
.agent-context details{width:100%;font-size:10.5px;color:var(--ai-faint)}
.agent-context summary{cursor:pointer}
.agent-context details p{margin:4px 0;overflow-wrap:anywhere}
.agent-form-link{display:flex;gap:9px;align-items:center;margin:8px 16px 0;padding:8px 11px;border:1px solid #d8e7fa;background:#f2f7ff;border-radius:9px;flex-shrink:0}
.agent-form-link .form-link-icon svg{display:block}.agent-form-link .form-link-icon{width:26px;height:26px;display:grid;place-items:center;border-radius:7px;background:#d9e9fd;color:var(--ai-primary);font-size:13px;flex-shrink:0}
.agent-form-link strong{display:block;font-size:12px;color:#1e3a5f;font-weight:600}
.agent-form-link span{display:block;font-size:11px;color:var(--ai-sub);margin-top:1px}
.agent-form-link button{margin-left:auto;flex-shrink:0;color:#1d4f91;background:#fff;border:1px solid #b8d2f3;border-radius:7px;padding:6px 9px;font-size:12px;font-weight:600}
.agent-form-link button:hover{color:#153e75;background:#e8f1fd;border-color:#8ab1e5}
/* ---- 对话区 ---- */
.agent-conversation{flex:1;min-height:0;overflow-y:auto;overscroll-behavior:contain;padding:4px 16px 12px;scrollbar-width:thin;scroll-behavior:smooth;scrollbar-color:#d4dbe4 transparent}
/* ---- 欢迎：收敛留白 ---- */
.agent-welcome{display:flex;flex-direction:column;gap:14px;justify-content:center;align-items:center;min-height:180px;padding:24px 12px;text-align:center}
.agent-welcome h3{font-size:24px;margin:0;color:#16233a;font-weight:650}
.agent-welcome .automation-shortcut{display:inline-flex;align-items:center;gap:6px;margin-top:4px;padding:8px 12px;border:1px solid var(--ai-line);border-radius:20px;background:transparent;color:var(--ai-sub);font:inherit;font-size:13px;cursor:pointer}
.automation-shortcut svg{width:16px;height:16px}
.agent-welcome .automation-shortcut:hover{background:var(--ai-soft)}
.agent-welcome .automation-shortcut:focus-visible{outline:2px solid var(--ai-primary);outline-offset:3px}
/* ---- 消息轮次：一问一答为一组 ---- */
.agent-turn{margin-top:18px}
.agent-turn:first-child{margin-top:12px}
.agent-turn.flash{outline:2px solid var(--ai-primary);outline-offset:2px;border-radius:8px}
.user-question{margin:0 0 0 auto;width:fit-content;max-width:88%;padding:9px 13px;background:var(--ai-primary);border:0;border-radius:12px 12px 4px 12px;white-space:pre-wrap;overflow-wrap:anywhere;color:#fff;font-size:13px;line-height:1.65}
.task-object{text-align:right;font-size:10.5px;color:var(--ai-faint);margin:5px 0 0}
.task-object.version-note{margin-top:0}
.user-question+.msg-actions,.task-object.version-note+.msg-actions{justify-content:flex-end}
.msg-actions{display:flex;flex-wrap:wrap;gap:2px 4px;margin-top:5px;align-items:center}
.msg-actions button{border:0;background:transparent;color:var(--ai-faint);border-radius:6px;padding:3px 7px;font-size:11px}
.msg-actions button:hover:enabled{color:var(--ai-primary);background:#eef4ff}
.msg-actions button:disabled{opacity:.45;cursor:default}
.msg-edit textarea{width:100%;border:1px solid var(--ai-primary);border-radius:9px;padding:9px;font-size:var(--ai-message-size);line-height:1.65;color:var(--ai-text);resize:vertical;outline:none;background:#fff}

.msg-edit .msg-actions button{border:1px solid var(--ai-line);background:#fff;padding:5px 13px;color:var(--ai-text);font-size:12px}
.msg-edit .msg-actions button:first-child{background:var(--ai-primary);border-color:var(--ai-primary);color:#fff;font-weight:600}
.version-pager{display:inline-flex;gap:2px;align-items:center;font-size:11px;color:var(--ai-faint);margin-left:2px}
.version-pager button{min-width:20px}
.version-pager strong{color:var(--ai-text);padding:0 3px}
.version-note{color:var(--ai-faint)}
/* ---- 流程状态：步骤胶囊，事情到哪一步一眼可见 ---- */
.task-status{display:inline-flex;align-items:center;gap:6px;color:var(--ai-sub);font-size:11px;background:var(--ai-soft);border-radius:999px;padding:3px 11px;margin-top:9px}
.task-status>span{color:var(--ai-ok);font-size:10px;line-height:1}
.busy-dot{animation:agent-spin 1.6s linear infinite}
/* ---- 思考中：四角星闪烁 + 状态文本，不用胶囊 ---- */
.thinking-indicator{display:flex;align-items:center;gap:6px;margin-top:9px;color:var(--ai-sub);font-size:11px}
.thinking-star{display:inline-block;color:var(--ai-primary);font-size:15px;line-height:1;animation:thinking-flash 1.2s ease-in-out infinite}
@keyframes thinking-flash{0%,100%{opacity:1;transform:scale(1)}50%{opacity:.35;transform:scale(.85)}}
.agent-answer{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.75;margin:9px 0 2px;color:#28374d;font-size:var(--ai-message-size)}
.agent-workspace .agent-answer .answer-text,.agent-workspace .agent-answer strong,.agent-workspace .agent-answer b{font-family:inherit;font-size:inherit;line-height:inherit;letter-spacing:inherit}
.agent-answer strong,.agent-answer b{color:#101c2e;font-weight:700}
.agent-task-error{background:var(--ai-err-bg);border:1px solid #f5d5d0;color:var(--ai-err);padding:9px 11px;border-radius:8px;font-size:12px;margin:8px 0 0}
.agent-task-neutral{background:#f4f6f9;border-color:#e1e6ee;color:#56657b}
.task-receipt{display:block;font-size:10px;color:#b8c1cd;padding-top:7px}
.ai-actions{margin-top:7px}
.agent-error{margin:8px 16px 0;padding:9px 11px;border-radius:8px;background:var(--ai-err-bg);color:var(--ai-err);font-size:11.5px;display:flex;gap:8px;align-items:center;flex-shrink:0}
.agent-error button{border:1px solid #efc5bf;background:#fff;color:var(--ai-err);padding:4px 10px;white-space:nowrap;border-radius:6px;font-size:11px}
.pending-bubble .user-question{opacity:.82}
.pending-actions{display:flex;gap:8px;margin-top:8px;justify-content:flex-end}
.pending-actions button{border:1px solid var(--ai-line);background:#fff;color:var(--ai-text);border-radius:7px;padding:5px 13px;font-size:11.5px}
.pending-actions button:disabled{opacity:.45;cursor:default}
.back-to-latest-wrap{position:relative;flex-shrink:0;height:0;overflow:visible}
.back-to-latest{position:absolute;left:50%;bottom:8px;transform:translateX(-50%);z-index:5;width:32px;height:32px;padding:0;display:grid;place-items:center;background:#fff;color:#4b5563;border:1px solid #e5e7eb;border-radius:50%;box-shadow:0 2px 8px rgba(15,23,42,.14);cursor:pointer}
.back-to-latest:hover,.back-to-latest:active{color:#111827;border-color:#d1d5db;background:#fff;transform:translateX(-50%)}.back-to-latest svg{display:block}
/* ---- 智能填表 / 任务流：统一信息卡片 ---- */
.fill-result{margin:8px 0 4px;border:1px solid var(--ai-line);background:#fff;border-radius:10px;overflow:hidden}
.fill-event .fill-result{border-color:#cfe6d6;background:#fbfdfb}
.fill-row{display:flex;gap:8px;align-items:baseline;padding:7px 12px;border-bottom:1px solid #f0f3f6;font-size:12px}
.fill-row:last-child{border-bottom:0}
.fill-row>span{color:var(--ai-sub);flex-shrink:0;min-width:70px;font-size:11.5px}
.fill-row>strong{color:#16233a;overflow-wrap:anywhere;flex:1;font-weight:600}
.fill-event .fill-row>strong{color:#166534}
.fill-row>small{color:var(--ai-faint);flex-shrink:0;font-size:10px;background:var(--ai-soft);border-radius:5px;padding:1px 6px}
.fill-event[data-fill-status="undone"] .fill-result{opacity:.5}
.auto-flow .automation-preview{margin-top:14px}
.flow-actions{display:flex;gap:8px;justify-content:flex-end;margin-top:9px;flex-wrap:wrap}
.flow-actions button{border:1px solid var(--ai-line);background:#fff;color:var(--ai-primary);border-radius:8px;padding:6px 13px;font-size:12.5px}
.flow-actions button:first-child{background:var(--ai-primary);border-color:var(--ai-primary);color:#fff;font-weight:600}
.flow-actions button:disabled{opacity:.45;cursor:default}
.apply-dialog{position:absolute;inset:0;z-index:30;margin:0;padding:16px;background:rgba(15,23,42,.35);display:flex;flex-direction:column;overflow:auto}
.apply-dialog .dialog-card{margin:auto;width:100%;max-width:100%;padding:13px;border:1px solid var(--ai-line);background:#fff;border-radius:12px;box-shadow:0 8px 30px rgba(15,23,42,.18)}
.apply-dialog h3{margin:0 0 6px;font-size:13.5px;color:#16233a;font-weight:650}
.apply-dialog p{margin:5px 0;font-size:12.5px;color:#43536a}
.apply-dialog .apply-note{color:var(--ai-warn);background:var(--ai-warn-bg);border:1px solid var(--ai-warn-line);border-radius:8px;padding:7px 10px;font-size:11px}
.apply-dialog .apply-actions{display:flex;gap:8px;justify-content:flex-end;margin-top:10px}
.apply-dialog .apply-actions button{border:1px solid var(--ai-line);background:#fff;color:var(--ai-sub);border-radius:8px;padding:6px 14px;font-size:12.5px}
.apply-dialog .apply-actions .apply-confirm{background:var(--ai-primary);border-color:var(--ai-primary);color:#fff;font-weight:600}
.reminder-dialog .reminder-field{display:block;margin:9px 0 0;font-size:12px;color:#66727f}
.reminder-dialog .reminder-field textarea{display:block;width:100%;margin-top:5px;box-sizing:border-box;border:1px solid var(--ai-line);border-radius:8px;padding:8px 10px;font-size:12.5px;line-height:1.6;color:var(--ai-text);resize:vertical;font-family:inherit}
.reminder-dialog .reminder-field textarea:focus{outline:none;border-color:var(--ai-primary);box-shadow:0 0 0 3px rgba(22,119,255,.12)}
.reminder-dialog .reminder-switch{margin-left:8px;font-size:11.5px}
/* ---- 会话历史 ---- */
.session-history{margin:12px 12px 8px;padding:10px 12px;border:1px solid var(--ai-line);border-radius:11px;background:#fafbfc;flex-shrink:0;max-height:260px;overflow:auto}
.history-head{display:flex;align-items:center;gap:8px;font-size:11px;color:var(--ai-sub)}
.storage-note{margin:0;padding:7px 16px;color:var(--ai-sub);font-size:11px;background:var(--ai-panel);border-bottom:1px solid var(--ai-line)}
.history-head strong{color:var(--ai-text);font-size:12.5px}
.history-head button{margin-left:auto;border:1px solid var(--ai-line);background:#fff;color:var(--ai-sub);border-radius:999px;padding:2px 10px;font-size:11px}
.history-empty{font-size:11px;color:var(--ai-sub);margin:8px 0 0}
.session-history ul{list-style:none;margin:8px 0 0;padding:0;display:flex;flex-direction:column;gap:6px}
.session-history li{display:flex;align-items:center;gap:6px;padding:5px 6px 5px 10px;border-radius:9px;background:#fff;border:1px solid #eef2f6}
.session-history li.active{border-color:var(--ai-primary);background:#f5f9ff}
.history-open{flex:1;min-width:0;display:flex;flex-direction:column;align-items:flex-start;border:0;background:transparent;text-align:left;color:var(--ai-text);padding:2px}
.history-title{font-size:11.5px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:100%}
.history-open small{color:var(--ai-faint);font-size:10px}
.history-act{display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border:0;background:transparent;color:var(--ai-sub);border-radius:6px;padding:6px;flex-shrink:0}
.history-act:hover{background:#eef2f6}
.history-act svg,.history-head button svg{width:16px;height:16px}
.history-head button{display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;padding:6px;border:0;background:transparent;border-radius:6px}
.history-act.danger{color:var(--ai-err);border-color:#f0d4d0}
/* ---- 语音 / 撤销条 ---- */
.asr-status{display:flex;gap:8px;align-items:center;margin:0 0 8px;padding:8px 11px;border:1px solid #c8e0f8;background:#f2f7ff;border-radius:9px;font-size:11.5px;color:#33608f;flex-shrink:0}
.asr-status em{font-style:normal;color:var(--ai-primary);overflow:hidden;white-space:nowrap;text-overflow:ellipsis;flex:1;min-width:0}
.asr-status.asr-error{border-color:#f2d5cd;background:var(--ai-err-bg);color:var(--ai-err)}
.asr-status button{border:1px solid #bcd8f2;background:#fff;color:var(--ai-primary);border-radius:6px;padding:3px 10px;font-size:11px;flex-shrink:0}
.asr-pulse{width:8px;height:8px;border-radius:50%;background:#e0524d;animation:asr-blink 1.1s ease-in-out infinite;flex-shrink:0}
@keyframes asr-blink{0%,100%{opacity:1;transform:scale(1)}50%{opacity:.35;transform:scale(.7)}}
/* ---- 底部输入区：一眼能用 ---- */
.agent-composer{flex-shrink:0;width:100%;z-index:6;background:#fff;padding:10px 12px 12px}
.agent-readonly-note{display:flex;align-items:center;gap:7px;margin:0 0 8px;padding:8px 11px;border:1px solid #e2e8f2;background:#f4f7fb;color:var(--ai-sub);border-radius:9px;font-size:11px;flex-shrink:0}
.agent-readonly-note i{width:6px;height:6px;background:#8aa1bd;border-radius:50%;flex-shrink:0}
.composer-field{display:grid;grid-template-columns:1fr 32px 32px;align-items:end;column-gap:10px;row-gap:4px;background:#fff;border:1px solid transparent;border-radius:13px;padding:8px 8px 8px 13px;transition:border-color .15s,background .15s,box-shadow .15s;box-shadow:0 8px 24px rgba(15,23,42,.10)}
.composer-field:focus-within{background:#fff;border-color:var(--ai-primary);box-shadow:0 0 0 3px rgba(22,119,255,.12)}
.composer-field textarea{grid-column:1 / -1;width:100%;min-width:0;border:0;resize:none;min-height:44px;max-height:180px;outline:none!important;padding:3px 4px 3px 0;color:var(--ai-text);font-size:var(--ai-message-size);line-height:1.6;background:transparent}
.composer-field textarea::placeholder{color:#a5b0bf;font-size:inherit}
.composer-field textarea:disabled{background:transparent}
.composer-field:has(.student-journey) textarea{grid-row:2}
.composer-field:has(.student-journey) .send-button,.composer-field:has(.student-journey) .mic-button{grid-row:3}
.send-button{grid-column:3;grid-row:2;border:0;border-radius:50%;background:var(--ai-primary);color:#fff;font-size:16px!important;line-height:1;width:32px;height:32px;min-width:32px;max-width:32px;padding:0;display:grid;place-items:center;flex-shrink:0}
.agent-workspace .send-button:hover{background:var(--ai-primary);color:#fff;filter:none}
.agent-workspace .send-button:hover:enabled{opacity:1}
.stop-button{background:var(--ai-primary)}
.mic-button{grid-column:2;grid-row:2;border:0;border-radius:9px;background:transparent;color:#5b6b82;width:32px;height:32px;padding:0;display:grid;place-items:center;flex-shrink:0}
.mic-button:hover{background:#e7ebf1}
.mic-button svg{display:block}.mic-button.listening{background:#e0524d;color:#fff;box-shadow:0 0 0 3px rgba(224,82,77,.15)}
.composer-hint{display:flex;justify-content:space-between;align-items:center;gap:6px;margin:6px 2px 0;font-size:10px;color:var(--ai-faint);white-space:nowrap;line-height:1.5}
.composer-hint>span:first-child{overflow:hidden;text-overflow:ellipsis;min-width:0}
.composer-hint>span:last-child{flex-shrink:0}
.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
@keyframes agent-spin{to{transform:rotate(360deg)}}
@media(prefers-reduced-motion:reduce){.busy-dot,.thinking-star,.asr-pulse,.mic-button svg{display:block}.thinking-star{animation:none}.mic-button.listening{animation:none}}
@media(max-width:700px){.agent-workspace{width:100vw!important;border-left:0}.agent-header{padding:12px 8px 8px 14px}.agent-conversation{padding:2px 14px 14px}.agent-composer{padding:10px 10px calc(10px + env(safe-area-inset-bottom))}.flow-switch,.agent-context,.agent-form-link{margin-left:12px;margin-right:12px}.user-question{max-width:92%}}
/* Business dialogs keep the shared header available, even while AI is closed.
   Message boxes and other global confirmation overlays retain their original scope. */
body.business-overlay-below-header .el-overlay:has(> .el-overlay-dialog),body.business-overlay-below-header .el-overlay-dialog,body.business-overlay-below-header .agent-host .modal-overlay{top:var(--business-overlay-top,64px)}
body.agent-docked .el-overlay:has(> .el-overlay-dialog),body.agent-docked .el-overlay.global-modal{right:var(--agent-width)}
body.agent-docked .el-overlay-dialog{right:var(--agent-width)}
body.agent-docked .el-overlay-dialog .el-dialog{max-width:calc(100% - 32px)}
body.agent-docked .agent-host .modal-overlay{right:var(--agent-width)}/* AI 标识单独占一行，回答内容从下一行开始 */.ai-message-mark{display:block;width:fit-content;color:var(--ai-primary);margin:0 0 2px;font-size:17px;line-height:1.2}.fill-changes summary{cursor:pointer;color:var(--ai-primary);font-size:12px}.previous-value{color:var(--ai-sub);text-decoration:line-through}.older-messages,.receipt-link{display:block;margin:8px auto;padding:7px 12px;color:var(--ai-primary)}
.message-time{display:block;color:#8a96a6;font-size:11px;line-height:1.5;margin-top:6px}
.user-time{text-align:right}
.answer-links{display:flex;flex-wrap:wrap;gap:6px}
.answer-links:empty{display:none}
.answer-links button{border:0;background:transparent;color:var(--ai-primary);font-size:12px;padding:4px 0;margin-right:8px}
.user-actions{justify-content:flex-end}
.msg-actions .message-time{margin:0 5px 0 0}
.msg-actions .message-icon{width:28px;height:28px;padding:5px;display:inline-flex;align-items:center;justify-content:center}
.message-icon svg{width:16px;height:16px}
.welcome-star{font-size:56px;line-height:1;color:var(--ai-primary)}
@font-face{font-family:"Intern Nunito";src:url("../../assets/fonts/Nunito-Variable.ttf") format("truetype");font-weight:200 1000;font-style:normal;font-display:swap}
.agent-conversation.is-empty{display:flex;flex-direction:column;padding-top:16px;padding-bottom:16px}
.agent-conversation.is-empty .agent-welcome{flex:1;min-height:0;padding:24px 0}
.agent-header-actions{display:flex;align-items:center;gap:4px;margin-left:auto;flex-shrink:0}
.agent-header-actions button{display:inline-flex;align-items:center;justify-content:center;width:32px;height:32px;padding:6px;border:0;border-radius:8px;background:transparent;color:var(--ai-sub)}
.agent-header-actions button:hover:enabled{background:#f0f2f5;color:var(--ai-text)}
.agent-header-actions button svg{width:20px;height:20px}
</style>
