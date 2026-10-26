<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowDown, Check, RefreshRight, InfoFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useAgentStore } from '@/agent/store'
import { canAccessStudentMenu } from '@/utils/studentMenuConfig'
import { businessTarget } from '@/agent/protocol.mjs'

const agent = useAgentStore(), router = useRouter()
const expanded = ref(false), choosing = ref(false), navigationError = ref('')
const plan = computed(() => agent.currentPlan)
const enabled = computed(() => agent.capabilities?.enabled && agent.chatEnabled && agent.planEnabled && agent.capabilities?.actor.role === 'ROLE_STUDENT')
const status: Record<string, string> = { DONE: '已完成', CURRENT: '进行中', WAITING: '等待处理', PENDING: '待开始', TODO: '待完善', REJECTED: '已驳回', WITHDRAWN: '已撤回' }
watch(() => plan.value?.needsChoice, (value, previous) => {
  if (value) choosing.value = true
  else if (previous) choosing.value = false
}, { immediate: true })
watch(() => agent.studentLoginAutoOpenJourney, value => {
  if (value) { expanded.value = true; agent.studentLoginAutoOpenJourney = false }
}, { immediate: true })
watch(() => plan.value?.canChoose, value => { if (!value) choosing.value = false })
watch(() => agent.capabilities?.actor.ownerKey, () => { expanded.value = false; choosing.value = false; navigationError.value = '' }, { flush: 'sync' })
async function choose(choice: string) {
  if (await agent.savePlanPreference(choice)) choosing.value = false
}
async function openAction(action: string) {
  navigationError.value = ''
  if (agent.planStale || !plan.value?.reliable || agent.capabilities?.actor.readOnly) return
  const target = action === 'OPEN_SELF_PRACTICE' || action === 'OPEN_DELAY'
    ? { path: '/student/home', query: { journeyAction: action === 'OPEN_DELAY' ? 'delay' : 'selfPractice' } }
    : businessTarget({ action, parameters: {} }, 'ROLE_STUDENT')
  if (!target || !(await canAccessStudentMenu(typeof target === 'string' ? target : target.path))) {
    navigationError.value = '当前页面未开放，请联系负责老师。'; return
  }
  await router.push(target)
  if (window.innerWidth <= 1023) agent.visible = false
}
const currentSteps = computed(() => (plan.value?.steps || []).filter((step: any) => (step.key === 'delay' && step.state !== 'DONE') || ['CURRENT', 'WAITING', 'REJECTED', 'WITHDRAWN'].includes(step.state)))
</script>

<template>
  <section v-if="enabled" class="student-journey" aria-label="实习进度">
    <button type="button" class="journey-toggle" :aria-expanded="expanded" aria-controls="student-journey-details" @click="expanded = !expanded">
      <span class="journey-title">{{ plan?.title || (agent.planStale ? '进度暂未更新' : '正在核对实习进度…') }}</span>
      <span v-if="plan?.currentIndex && plan?.total" class="journey-count">第 {{ plan.currentIndex }}/{{ plan.total }} 阶段</span>
      <ArrowDown class="journey-chevron" :class="{ expanded }" />
    </button>
    <div v-if="expanded" id="student-journey-details" class="journey-details">
      <div v-if="choosing && plan?.canChoose && !agent.planStale" class="journey-choices">
        <p>准备如何开始实习？</p>
        <button type="button" class="choice-card" :disabled="agent.planSaving" @click="choose('NORMAL')">通过系统寻找实习 <span>→</span></button>
        <button type="button" class="choice-card" :disabled="agent.planSaving" @click="choose('SELF')">申请自主实习 <span>→</span></button>
        <button type="button" class="choice-card" :disabled="agent.planSaving" @click="choose('DELAY')">需要申请考研延期 <span>→</span></button>
        <button v-if="!plan.needsChoice" type="button" class="secondary" @click="choosing = false">返回进度</button>
        <button v-else type="button" class="secondary" @click="expanded = false">稍后选择</button>
      </div>
      <template v-else>
        <ol class="journey-steps">
          <li v-for="step in plan?.steps || []" :key="step.key" :class="step.state.toLowerCase()">
            <span class="step-marker"><Check v-if="step.state === 'DONE'" /><span v-else class="step-dot" /></span>
            <span class="step-title">{{ step.title }}<el-tooltip placement="top" :show-after="150"><template #content><div class="step-help-tip"><p><strong>含义：</strong>{{ step.meaning }}</p><p><strong>完成条件：</strong>{{ step.completionCriteria }}</p></div></template><button type="button" class="step-help" :aria-label="`${step.title}说明`"><InfoFilled aria-hidden="true" /></button></el-tooltip></span><span class="step-state">{{ status[step.state] }}</span>
            <p v-if="currentSteps.includes(step) || step.key === 'delay'" class="step-detail">{{ step.detail }}</p>
            <button v-if="currentSteps.includes(step) && step.action && !agent.capabilities?.actor.readOnly" type="button" class="journey-action" :disabled="agent.planStale || !plan.reliable" @click="openAction(step.action)">{{ ['REJECTED', 'WITHDRAWN'].includes(step.state) ? '重新申请' : step.state === 'WAITING' ? '查看申请' : '前往办理' }} →</button>
          </li>
        </ol>
        <button v-if="plan?.canChoose && !agent.planStale" type="button" class="journey-rechoose" @click="choosing = true">{{ plan.needsChoice ? '选择实习安排' : '重新选择' }}</button>
      </template>
      <div v-if="agent.planStale || plan?.reliable === false" class="journey-note" role="status">{{ agent.planStale ? '暂未取得最新进度' : '进度待核实' }}<button type="button" aria-label="刷新实习进度" title="刷新进度" @click="agent.refreshPlan"><RefreshRight /></button></div>
      <p v-if="agent.planError || navigationError" class="journey-note" role="alert">{{ agent.planError || navigationError }}</p>
    </div>
  </section>
</template>

<style scoped>
.student-journey{grid-column:1 / -1;min-width:0;margin:-8px -8px 4px -13px;font-size:13px;color:var(--ai-text,#28364b);border-bottom:1px solid var(--ai-border,#e8edf3)}
.student-journey button{font:inherit;cursor:pointer;border:0;background:none;color:inherit}.student-journey button:disabled{cursor:default;opacity:.5}.student-journey button:focus-visible{outline:2px solid var(--ai-primary,#1677ff);outline-offset:-2px;border-radius:6px}
.journey-toggle{display:flex;align-items:center;gap:8px;width:100%;min-height:42px;padding:10px 13px;text-align:left}.journey-title{font-weight:550;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.journey-count{color:#8591a3;font-size:12px;white-space:nowrap}.journey-chevron{width:13px;height:13px;margin-left:auto;flex-shrink:0;color:#8591a3;transition:transform .18s}.journey-chevron.expanded{transform:rotate(180deg)}
.journey-details{padding:0 13px 10px;max-height:min(310px,35dvh);overflow:auto;overscroll-behavior:contain}.journey-steps{list-style:none;margin:0;padding:0}.journey-steps li{display:grid;grid-template-columns:18px 1fr auto;align-items:center;column-gap:5px;min-height:34px;padding:5px 0}.step-marker{display:flex;align-items:center;justify-content:center}.step-marker svg{width:14px;height:14px;color:#5c9d7c}.step-dot{width:5px;height:5px;border-radius:50%;background:#cad1dd}.step-title{line-height:1.6}.step-state{font-size:11px;color:#9aa4b3}.current .step-title,.waiting .step-title{color:var(--ai-primary,#1677ff);font-weight:550}.current .step-dot,.waiting .step-dot{background:var(--ai-primary,#1677ff)}.done .step-state{color:#6b9c81}.pending{color:#9aa4b3}.rejected .step-state{color:#c45656}.withdrawn .step-state{color:#8a7a55}.step-detail{grid-column:2/4;margin:4px 0;color:#7a8799;font-size:12px;line-height:1.65}.journey-action{grid-column:2/4;justify-self:start;color:var(--ai-primary,#1677ff)!important;padding:5px 0 3px}.journey-rechoose{margin-top:5px;padding:5px 0;color:#7a8799!important;font-size:12px!important}.journey-choices p{margin:6px 0 10px;font-weight:550}.journey-choices>.choice-card{display:flex;justify-content:space-between;align-items:center;width:100%;padding:11px 10px;margin:6px 0;border:1px solid #e6eaf0;border-radius:8px;text-align:left;font-size:13px}.journey-choices>.choice-card:hover{background:#f5f8fc}.journey-choices>button.secondary{padding:5px 0;color:#7a8799;font-size:12px}.journey-note{display:flex;gap:8px;align-items:center;color:#8792a1;font-size:12px;line-height:1.6;margin:5px 0}.journey-note button{display:flex;padding:4px}.journey-note svg{width:14px;height:14px}
.step-help{display:inline-flex;align-items:center;justify-content:center;width:16px;height:16px;margin-left:4px;padding:0;vertical-align:middle;color:#a9b3c2!important;border-radius:50%}
.step-help:hover{color:var(--ai-primary,#1677ff)!important}
.step-help svg{display:block;width:12px;height:12px}
.step-help-tip{max-width:220px;font-size:12px;line-height:1.7}
.step-help-tip p{margin:0}
.step-help-tip p+p{margin-top:4px}
@media(prefers-reduced-motion:reduce){.journey-chevron{transition:none}}
</style>
