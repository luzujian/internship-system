<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { Close, Delete, VideoPlay, RefreshRight, Plus, EditPen, Clock } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useAgentStore } from '@/agent/store'
import { automationTemplate } from '@/agent/automationIntent.mjs'
import { automationApi } from '@/api/automationV2'
import AutomationPreview from './AutomationPreview.vue'

defineProps<{ fullPage?: boolean }>()
const emit = defineEmits<{ close: []; example: [message: string]; edit: [task: any] }>()
const agent = useAgentStore(), tasks = ref<any[]>([]), catalog = ref<any>(null)
const loading = ref(true), busy = ref(false), notice = ref(''), preview = ref<any>(null)
const detailTask = ref<any>(null), runs = ref<any[]>([]), deliveries = ref<any[]>([]), more = ref(false)
const owner = agent.capabilities?.actor.ownerKey
let alive = true, timer: ReturnType<typeof setTimeout> | undefined
const current = () => alive && owner === agent.capabilities?.actor.ownerKey && agent.autoTaskEnabled && !agent.capabilities?.actor.readOnly
onBeforeUnmount(() => { alive = false; clearTimeout(timer) })
const labels: Record<string,string> = { QUEUED:'等待处理', CLAIMED:'核对中', SENDING:'投递中', SENT:'邮件服务已受理', RUNNING:'执行中', SUCCEEDED:'已完成', PARTIAL:'部分完成', FAILED:'明确失败', UNKNOWN:'待核实', CANCELLED:'已停止', SKIPPED:'已跳过', MISSED:'错过本轮', ACTIVE:'已启用', PAUSED:'已暂停', ENDED:'已结束' }
const time = (value: any) => typeof value === 'number' ? new Date(value).toLocaleString('zh-CN', {hour12:false}) : String(value || '').replace('T',' ')
const errorMessage = (e: any) => e?.response?.data?.message || e?.message || '暂时无法读取任务。'
async function load() {
  loading.value = true
  try {
    const [rows, caps]: any[] = await Promise.all([automationApi.tasks(), automationApi.catalog()])
    if (!current()) return
    tasks.value = rows.data || []; catalog.value = caps.data
    if (detailTask.value && !tasks.value.some((t: any) => t.id === detailTask.value.id)) closeRecords()
    if (!caps.data?.canManage) emit('close')
  } catch (e) { if (current()) notice.value = errorMessage(e) }
  finally { loading.value = false }
}
async function operate(task: any, action: string) {
  if (busy.value || !current()) return
  busy.value = true; notice.value = ''; agent.clearAutoTaskError(task?.id)
  try {
    if (action === 'delete') {
      await ElMessageBox.confirm(`删除“${task.name}”并停止后续执行？已发送记录会保留。`, '删除任务', { confirmButtonText:'删除', cancelButtonText:'取消', type:'warning' })
      if (!current()) return
      await automationApi.remove(task.id); await load()
    } else if (action === 'toggle' && task.status === 'ACTIVE') {
      await automationApi.pause(task.id); notice.value = '已暂停，尚未投递的部分已停止。'; await load()
    } else {
      const result: any = await automationApi.preview({ taskId:task.id, operation:action === 'toggle' ? 'ENABLE' : action === 'retry' ? 'RETRY' : 'RUN' })
      if (current()) preview.value = result.data
    }
  } catch (e: any) { if (current() && e !== 'cancel' && e !== 'close') agent.noteAutoTaskError(task?.id, errorMessage(e), action) }
  finally { busy.value = false }
}
async function edit(task: any) {
  agent.clearAutoTaskError(task?.id)
  try {
    const result: any = await automationApi.draft({ sessionId:agent.sessionId || 'manager', taskId:task.id, definition:task.definition, message:`修改任务：${task.name}` })
    if (current()) preview.value = {...result.data, taskId:task.id}
  } catch (e) { if (current()) agent.noteAutoTaskError(task?.id, errorMessage(e), 'edit') }
}
function onPreviewUpdated(value: any) {
  preview.value = value
  agent.clearAutoTaskError(value?.taskId)
}
function onPreviewError(payload: any) {
  agent.noteAutoTaskError(payload?.taskId, payload?.message || '', payload?.operation)
}
/** 点错误条上的“重新核对”：按当时失败的动作重开确认卡，成功即自动清掉提示。 */
async function retryTask(task: any) {
  const action = agent.autoTaskErrors[task.id]?.action
  if (!action || busy.value) return
  if (action === 'edit') await edit(task)
  else await operate(task, action)
}
async function confirmed(task: any) {
  agent.clearAutoTaskError(task?.id)
  preview.value = null
  notice.value = task.executionState === 'QUEUED' ? '已进入执行队列，可在记录中查看进度。' : task.status === 'ACTIVE' ? '已启用，将按确认的计划执行。' : '草稿已保存，尚未启用。'
  await load()
  if (detailTask.value?.id === task.id) await records(task)
}
async function records(task: any, append = false) {
  clearTimeout(timer)
  try {
    const [log, items]: any[] = await Promise.all([automationApi.runs(task.id), automationApi.deliveries(task.id, append ? deliveries.value.length : 0)])
    if (!current()) return
    detailTask.value = task; runs.value = log.data || []
    deliveries.value = append ? [...deliveries.value, ...(items.data || [])] : (items.data || [])
    more.value = (items.data || []).length === 50
    if (runs.value.some(run => run.status === 'RUNNING') && !append) timer = setTimeout(() => records(task), 3000)
  } catch (e) { if (current()) notice.value = errorMessage(e) }
}
function closeRecords() { detailTask.value = null; clearTimeout(timer) }
import { formatTriggerLabel } from '@/agent/formatTriggerLabel'
onMounted(load)
</script>

<template>
  <section class="automation-manager" :class="{ 'full-page':fullPage }" aria-label="自动化任务管理">
    <header><strong>自动化任务</strong><div><button v-if="catalog?.canExecute" type="button" aria-label="新建自动化任务" title="新建任务" @click="emit('example', automationTemplate)"><Plus /></button><button type="button" aria-label="刷新任务" title="刷新" :disabled="loading || busy" @click="load"><RefreshRight /></button><button v-if="!fullPage" type="button" aria-label="返回对话" title="返回对话" @click="emit('close')"><Close /></button></div></header>
    <p v-if="notice" role="status" class="notice">{{ notice }}</p>
    <template v-if="preview">
      <AutomationPreview :preview="preview" :session-id="agent.sessionId" @updated="onPreviewUpdated" @confirmed="confirmed" @error="onPreviewError" @cancel="preview = null" />
      <button v-if="preview.taskId" type="button" class="text-link" @click="emit('edit', tasks.find(t => t.id === preview.taskId)); preview = null">通过对话修改条件或对象</button>
    </template>
    <template v-else-if="detailTask">
      <div class="detail-title"><strong>{{ detailTask.name }}</strong><button type="button" aria-label="关闭执行记录" @click="closeRecords"><Close /></button></div>
      <p v-if="!runs.length" class="empty">还没有执行记录</p>
      <div v-for="run in runs" :key="run.id" class="run"><span>{{ time(run.started_at) }} · {{ labels[run.status] || run.status }}</span><p v-if="run.error_message">{{ run.error_message }}</p></div>
      <button v-if="['ACTIVE','ENDED'].includes(detailTask.status) && deliveries.some(d => d.status === 'FAILED')" type="button" class="text-link" @click="operate(detailTask, 'retry')">核对并重试失败项</button>
      <div v-for="item in deliveries" :key="item.id" class="delivery"><strong>{{ item.student_name }}</strong><span>{{ item.status === 'SENT' && item.channel === 'STATION_REMINDER' ? '站内提醒已保存' : labels[item.status] || item.status }}</span><p>{{ item.detail }}</p></div>
      <button v-if="more" type="button" class="text-link" @click="records(detailTask, true)">加载更多</button>
    </template>
    <template v-else>
      <div v-if="catalog?.canExecute && catalog.templates?.length" class="examples"><span>从模板开始</span>
        <div v-for="example in catalog.templates || []" :key="example.id" class="template-item">
          <button type="button" @click="emit('example', example.prompt)">{{ example.label }}</button>
        </div>
      </div>
      <p v-if="loading">正在加载…</p><p v-else-if="!tasks.length" class="empty">还没有自动化任务</p>
      <ul v-else><li v-for="task in tasks" :key="task.id" class="task-card">
        <div class="task-copy"><strong>{{ task.name }}</strong><p>{{ formatTriggerLabel(task.triggerLabel) }}</p><p>{{ task.scheduleLabel }} · {{ task.actionLabel }}</p><small v-if="task.blockedReason">{{ task.blockedReason }}</small><small v-else-if="task.status !== 'ACTIVE'">{{ labels[task.status] || task.status }}</small><small v-if="task.attentionCount">{{ task.attentionCount }} 项需要核对</small><div v-if="agent.autoTaskErrors[task.id]" role="alert" class="task-error"><span>{{ agent.autoTaskErrors[task.id].message }}</span><button type="button" class="task-error-retry" :disabled="busy" :aria-label="`重新核对：${task.name}`" @click="retryTask(task)">重新核对</button><button type="button" class="task-error-dismiss" :aria-label="`清除提示：${task.name}`" title="清除提示" @click="agent.clearAutoTaskError(task.id)"><Close /></button></div></div>
        <div class="controls">
          <button type="button" :aria-label="`记录：${task.name}`" title="记录" @click="records(task)"><Clock /></button>
          <button type="button" :aria-label="`编辑：${task.name}`" title="编辑" :disabled="busy" @click="edit(task)"><EditPen /></button>
          <button type="button" :aria-label="`删除：${task.name}`" title="删除" :disabled="busy" @click="operate(task, 'delete')"><Delete /></button>
          <button type="button" :aria-label="`运行：${task.name}`" title="运行一次" :disabled="busy || !catalog?.canExecute || task.status !== 'ACTIVE'" @click="operate(task, 'run')"><VideoPlay /></button>
          <button type="button" role="switch" :aria-label="`启用：${task.name}`" :aria-checked="task.status === 'ACTIVE'" :title="task.status === 'ACTIVE' ? '暂停' : '启用'" class="task-switch" :class="{active:task.status === 'ACTIVE'}" :disabled="busy || !catalog?.canExecute" @click="operate(task, 'toggle')"><span /></button>
        </div>
      </li></ul>
    </template>
  </section>
</template>

<style scoped>
.automation-manager { padding:14px 14px 16px; overflow-y:auto; max-height:50%; flex-shrink:0; border-bottom:1px solid var(--el-border-color-lighter,#edf0f4); background:#f4f6f9; font-size:14px; overscroll-behavior:contain; scrollbar-width:thin; scrollbar-color:#d4dbe4 transparent; }.full-page { max-height:none; border:0; background:#fff; }

header,header>div,.controls,.detail-title { display:flex; align-items:center; gap:8px; } header,.detail-title { justify-content:space-between; margin-bottom:12px; } header strong { font-size:15px; font-weight:700; letter-spacing:.01em; } header button:hover,.detail-title button:hover { background:#e9edf2; }
button { cursor:pointer; font:inherit; color:inherit; border:0; background:transparent; border-radius:8px; } button:disabled { opacity:.4; cursor:default; } button:focus-visible { outline:2px solid #1677ff; outline-offset:2px; }
header button,.controls button,.detail-title button { display:grid; place-items:center; padding:5px; color:#7b8493; } header button:hover,.controls button:hover:not(:disabled),.detail-title button:hover { color:#1f2a3d; } .controls button:hover:not(:disabled) { background:#eef1f6; } svg { width:17px; height:17px; }
ul { padding:0; margin:0; list-style:none; display:flex; flex-direction:column; gap:8px; } li.task-card { display:flex; align-items:flex-start; flex-wrap:wrap; gap:8px; padding:12px 14px; background:#fff; border:1px solid #e6ebf1; border-radius:14px; box-shadow:0 2px 8px rgba(24,39,75,.06); }
.task-copy { flex:1; min-width:180px; }.task-copy strong { font-size:14px; overflow-wrap:anywhere; }.task-copy p,.task-copy small { display:block; margin:5px 0 0; color:#7b8493; font-size:12px; line-height:1.6; }.controls { margin-left:auto; flex-shrink:0; gap:1px; }
.controls .task-switch { width:30px; height:18px; border-radius:12px; padding:2px; background:#cbd0d8; display:flex; margin-left:4px; }.task-switch span { width:14px; height:14px; border-radius:50%; background:#fff; transition:transform .15s; }.controls .task-switch.active { background:#10a779; }.task-switch.active span { transform:translateX(12px); }
.examples { display:flex; flex-wrap:wrap; gap:8px; padding:2px 0 12px; }.examples span { width:100%; color:#8a93a0; font-size:12px; }.examples button { padding:8px 12px; background:#fff; border:1px solid #e6ebf1; border-radius:10px; font-size:12px; box-shadow:0 1px 2px rgba(24,39,75,.04); }.notice,.empty { color:#64748b; line-height:1.6; }.run,.delivery { padding:10px 0; border-bottom:1px solid #f0f2f5; font-size:12px; }.run p,.delivery p { color:#7b8493; margin:5px 0 0; }.delivery>span { float:right; }.text-link { color:#1677ff; margin:10px 0; }

.task-copy .task-error { display:flex; align-items:center; gap:6px; color:#d54d4d; background:#fef2f2; border:1px solid #f6d5d5; border-radius:8px; padding:6px 8px; }
.task-copy .task-error span { flex:1; overflow-wrap:anywhere; }
.task-error button { display:grid; place-items:center; padding:2px; color:#d54d4d; border-radius:6px; flex-shrink:0; }
.task-error button:hover:not(:disabled) { background:#f8dede; }
.task-error .task-error-retry { display:inline-block; padding:2px 8px; border:1px solid #f0c7c7; background:#fff; font-size:12px; white-space:nowrap; }
.task-error svg { width:13px; height:13px; }
</style>
