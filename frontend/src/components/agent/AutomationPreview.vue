<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { automationApi } from '@/api/automationV2'
import { editableAutomationDefinition } from '@/agent/automationDefinition.mjs'

const props = defineProps<{ preview: any; sessionId?: string | null }>()
const emit = defineEmits<{ updated: [preview: any]; confirmed: [task: any]; cancel: []; error: [payload: { taskId?: number; message: string; operation?: string }] }>()
const definition = ref<any>({}), current = ref<any>({}), busy = ref(false), error = ref('')
const people = ref<any[]>([]), total = ref(0), expanded = ref(false)
let alive = true
onBeforeUnmount(() => { alive = false })
watch(() => props.preview, value => {
  const editable = editableAutomationDefinition(JSON.parse(JSON.stringify(value?.definition || {})))
  current.value = { ...value, definition: editable }; definition.value = structuredClone(editable)
  people.value = []; expanded.value = false
}, { immediate: true })
const dirty = computed(() => JSON.stringify(definition.value) !== JSON.stringify(current.value.definition || {}))
const execution = computed(() => ['RUN', 'RETRY'].includes(current.value.operation))
const label = computed(() => current.value.operation === 'RETRY' ? '确认重试失败项' : current.value.operation === 'RUN' ? (current.value.forceArmed ? '确认强制执行' : '确认运行一次') : '确认')
const valid = computed(() => !current.value.questions?.length && !!current.value.actionId && definition.value.trigger?.mode && definition.value.repeatPolicy?.mode && definition.value.notification?.body?.trim())
const dateLabel = (value: string) => value ? value.replace('T', ' ').slice(0,16) : '待核对'
/** 统一出口：既要就地显示，也要通知父级把错误挂到对应任务卡上。 */
function fail(message: string) {
  error.value = message
  emit('error', { taskId: current.value.taskId, message, operation: current.value.operation })
}
async function refresh(saveOnly = false) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    const body = { definition: definition.value, taskId: current.value.taskId, operation: current.value.operation || 'CREATE', saveOnly }
    const response: any = current.value.draftId && !saveOnly
      ? await automationApi.draft({ ...body, draftId: current.value.draftId, revision: current.value.revision, sessionId: props.sessionId || 'manager', message: current.value.message || '' })
      : await automationApi.preview(body)
    if (!alive) return
    current.value = { ...current.value, ...response.data, definition: definition.value }; emit('updated', current.value)
    if (saveOnly && response.data?.actionId) await submit(response.data.actionId, true)
  } catch (e: any) { if (alive) fail(e?.response?.data?.message || e.message || '预览失败，修改内容已保留。') }
  finally { busy.value = false }
}
async function forceRun() {
  if (busy.value || current.value.operation !== "RUN" || !current.value.taskId) return
  busy.value = true; error.value = ""
  try {
    const response: any = await automationApi.preview({ taskId: current.value.taskId, operation: "RUN", force: true })
    if (!alive) return
    current.value = { ...current.value, ...response.data }
    emit("updated", current.value)
  } catch (e: any) { if (alive) fail(e?.response?.data?.message || e.message || "强制预览失败，仍可直接确认运行一次。") }
  finally { busy.value = false }
}
async function submit(actionId = current.value.actionId, fromSave = false) {
  if (!actionId || current.value.questions?.length || (busy.value && !fromSave)) return
  busy.value = true; error.value = ''
  try {
    const response: any = await automationApi.confirm(actionId)
    if (alive) emit('confirmed', response.data)
  } catch (e: any) { if (alive) fail(e?.response?.data?.message || e.message || '暂未取得回执，可重试本次确认，系统不会重复创建。') }
  finally { busy.value = false }
}
async function showPeople(more = false) {
  try {
    const response: any = await automationApi.matches(current.value.definition, more ? people.value.length : 0)
    if (!alive) return
    people.value = more ? [...people.value, ...response.data.items] : response.data.items
    total.value = response.data.total; expanded.value = true
  } catch (e: any) { fail(e?.response?.data?.message || e.message || '名单暂时无法读取。') }
}
import { formatTriggerLabel } from '@/agent/formatTriggerLabel'
</script>

<template>
  <section class="automation-preview" aria-label="核对自动化任务">
    <strong>{{ current.name || '自动化任务草稿' }}</strong>
    <div class="summary-title">规则摘要</div>
    <div class="summary-box" title="规则摘要由系统根据条件生成，不可直接修改">{{ formatTriggerLabel(current.triggerLabel) }} · {{ current.scheduleLabel }} · {{ current.actionLabel }}</div>
    <p class="summary">{{ current.scopeLabel || current.targetLabel }} · 当前 {{ current.targetCount ?? '—' }} 人 <button v-if="current.targetCount" type="button" @click="expanded ? expanded = false : showPeople()">{{ expanded ? '收起' : '查看名单' }}</button></p>
    <div v-if="expanded" class="people"><span v-for="person in people" :key="person.username">{{ person.name }} · {{ person.username }}</span><button v-if="people.length < total" type="button" @click="showPeople(true)">加载更多</button></div>
    <div v-if="current.operation === 'RUN' && current.skipped && current.skipped.length" class="force-note">
      <p class="warn">其中 {{ current.skipped.length }} 人次因去重/冷却保护不会发送：</p>
      <p v-for="s in current.skipped" :key="s.username + s.channel" class="warn-item">{{ s.name }} · {{ s.username }} · {{ s.channel === "EMAIL" ? "邮件" : "站内提醒" }} · {{ s.reason === "VERIFYING" ? "在途/待核实（强制也发不了）" : s.reason === "SENT_BEFORE" ? "同一事项已发送过" : "冷却期内" }}</p>
      <button v-if="!current.forceArmed && current.skipped.some((s) => s.forceable)" type="button" class="primary" :disabled="busy" @click="forceRun()">仍要强制执行一次</button>
      <p v-if="current.forceArmed" class="warn">强制模式：确认后将重发可强制的人次（预计 {{ current.forceTargetCount }}），在途/待核实的不发。</p>
    </div>

    <p v-if="current.nextRunAt && !execution" class="summary">下次定时检查 {{ dateLabel(current.nextRunAt) }} · 北京时间</p>
    <p v-if="current.deliveryMode === 'LOCAL_TEST'" class="summary">本地测试邮件箱</p>
    <template v-if="definition.notification">
      <label>标题<input v-model="definition.notification.subject" :readonly="execution" maxlength="180" aria-label="邮件标题" /></label>
      <label>提醒内容<textarea v-model="definition.notification.body" :readonly="execution" maxlength="2000" rows="3" aria-label="提醒内容" /></label>
      <div v-if="!execution" class="schedule-section">
        <div class="schedule-title">调整时间与重复规则</div>
        <label>任务名称<input v-model="definition.name" maxlength="100" /></label>
        <label>执行方式<select v-model="definition.trigger.mode"><option v-if="definition.trigger?.mode && !['ONCE','DAILY','WEEKLY','MONTHLY','INTERVAL','CONDITION'].includes(definition.trigger.mode)" :value="definition.trigger.mode" disabled>已停用，请重新选择</option><option value="ONCE">指定日期</option><option value="DAILY">每日</option><option value="WEEKLY">每周</option><option value="MONTHLY">每月</option><option value="INTERVAL">按间隔</option><option value="CONDITION">条件满足时</option></select></label>
        <label v-if="definition.trigger.mode === 'ONCE'">日期<input v-model="definition.trigger.date" type="date" /></label>
        <label v-if="['ONCE','DAILY','WEEKLY','MONTHLY'].includes(definition.trigger.mode)">时间<input v-model="definition.trigger.time" type="time" @input="delete definition.trigger.times" /></label>
        <label v-if="definition.trigger.mode === 'WEEKLY'">每周<select v-model="definition.trigger.weekdays" multiple><option v-for="(day, i) in ['一','二','三','四','五','六','日']" :key="i" :value="i + 1">周{{ day }}</option></select></label>
        <label v-if="definition.trigger.mode === 'MONTHLY'">每月<select v-model="definition.trigger.monthdays" multiple><option v-for="day in 31" :key="day" :value="day">{{ day }} 日</option></select></label>
        <label v-if="definition.trigger.mode === 'INTERVAL'">间隔分钟<input v-model.number="definition.trigger.minutes" type="number" min="1" /></label>
        <label>重复<select v-model="definition.repeatPolicy.mode"><option value="ONCE_PER_MATTER">每个事项一次</option><option value="REPEAT">按计划重复</option></select></label>
      </div>
    </template>
    <p v-for="question in current.questions || []" :key="question" class="problem">{{ question }}</p>
    <p v-if="error" role="alert" class="problem">{{ error }}</p>
    <div class="actions">
      <button v-if="dirty" type="button" class="primary" :disabled="busy || !definition.notification?.body?.trim()" @click="refresh()">核对修改</button>
      <button v-else type="button" class="primary" :disabled="busy || !valid" @click="submit()">{{ busy ? '处理中…' : label }}</button>
      <button v-if="!execution" type="button" :disabled="busy || !definition.notification?.body?.trim()" @click="refresh(true)">保存草稿</button>
      <button type="button" :disabled="busy" @click="emit('cancel')">取消</button>
    </div>
  </section>
</template>

<style scoped>
.automation-preview { margin-top:14px; padding:14px; border:1px solid var(--el-border-color-lighter,#e8edf3); border-radius:12px; font-size:14px; background:#fff; }
.summary { color:var(--el-text-color-secondary,#768190); font-size:12px; line-height:1.7; margin:7px 0; overflow-wrap:anywhere; }
label { display:flex; flex-direction:column; gap:5px; margin:12px 0; font-size:12px; color:var(--el-text-color-secondary,#768190); }
input,textarea,select { box-sizing:border-box; width:100%; font:inherit; font-size:13.5px; padding:7px 10px; color:var(--el-text-color-primary,#253348); border:1px solid var(--el-border-color,#dce2e9); border-radius:8px; background:var(--el-bg-color,#fff); }
textarea { resize:vertical; line-height:1.6; }
button { font:inherit; border:0; background:transparent; color:var(--el-color-primary,#1677ff); cursor:pointer; padding:4px; border-radius:6px; }
button:disabled { opacity:.45; cursor:default; }
button:focus-visible { outline:2px solid #1677ff; }
.actions { display:flex; align-items:center; gap:8px; flex-wrap:wrap; margin-top:14px; }
.actions button { font-size:12px; padding:4px 10px; border-radius:6px; line-height:1.5; }
.actions button:hover:not(:disabled) { color:#0958d9; }
.primary { background:var(--ai-primary,#1677ff) !important; color:#fff !important; border:1px solid var(--ai-primary,#1677ff) !important; padding:4px 12px !important; font-size:12px !important; font-weight:500; border-radius:6px; box-shadow:0 1px 3px rgba(22,119,255,.15); }
.primary:hover:not(:disabled) { background:#0958d9 !important; background-color:#0958d9 !important; border-color:#0958d9 !important; color:#fff !important; opacity:1 !important; box-shadow:0 2px 6px rgba(22,119,255,.25); }
.primary:active:not(:disabled) { background:#003eb3 !important; background-color:#003eb3 !important; border-color:#003eb3 !important; }
.actions .primary:disabled { opacity:.55 !important; background:var(--ai-primary,#1677ff) !important; cursor:default; box-shadow:none; }
.force-note { margin:10px 0; padding:10px 12px; border:1px dashed #e8a13d; border-radius:8px; background:#fffbe6; }
.force-note .warn { color:#9a6b12; font-size:12px; margin:4px 0; }
.force-note .warn-item { color:#8a6d1a; font-size:12px; margin:2px 0; }
.problem { color:var(--el-color-danger,#d54d4d); font-size:13px; line-height:1.6; }
.people { display:flex; flex-direction:column; gap:5px; max-height:180px; overflow:auto; font-size:12px; }
.schedule-section { margin:14px 0 6px; padding-top:10px; border-top:1px dashed var(--el-border-color-lighter,#e8edf3); }
.schedule-title { font-size:12px; font-weight:600; color:var(--el-text-color-secondary,#606d7e); margin-bottom:8px; }
.summary-title { font-size:12px; font-weight:600; color:var(--el-text-color-secondary,#606d7e); margin-top:10px; margin-bottom:5px; }
.summary-box { box-sizing:border-box; width:100%; font-size:13px; line-height:1.6; padding:8px 10px; color:var(--el-text-color-regular,#4e5969); border:1px solid var(--el-border-color,#dce2e9); border-radius:8px; background:var(--el-fill-color-light,#f8fafc); margin-bottom:8px; word-break:break-word; user-select:text; cursor:default; }
</style>
