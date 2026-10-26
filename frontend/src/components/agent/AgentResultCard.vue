<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ResultCard, AgentLink } from '@/agent/types'
import { jobSalary } from '@/agent/protocol.mjs'
const props = defineProps<{ card: ResultCard; canApply?: boolean }>()
const expanded = ref(false)
const visibleItems = computed(() => expanded.value ? props.card.items : props.card.items.slice(0, 3))
defineEmits<{ navigate: [link: AgentLink]; apply: [row: Record<string, unknown>] }>()
const appliable = (row: Record<string, unknown>) =>
  Boolean(props.canApply && row.id && (props.card.tool === 'jobs_details' || props.card.tool === 'jobs_search'))
const date = computed(() => {
  const observed = new Date(props.card.observedAt)
  return Number.isNaN(observed.getTime()) ? '查询时间待核验' : observed.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })
})
const labels: Record<string, string> = {
  company: '单位', city: '城市', location: '地点', salaryNote: '薪资说明', major: '专业', grade: '年级', className: '班级', remainingQuota: '剩余确认名额',
  dueAt: '截止时间', deadline: '截止时间', interviewTime: '面试时间', pendingApplicationTexts: '在办申请', status: '状态', updatedAt: '更新时间',
  requiredMajors: '专业要求', method: '面试方式', interviewMethod: '面试方式', interviewLocation: '面试地点', internshipStartDate: '实习开始', internshipEndDate: '实习结束', internshipEnvironment: '实习环境', positionType: '岗位类型', plannedRecruit: '计划招聘', providesAccommodation: '是否提供住宿', companyIndustry: '所属行业', companyScale: '企业规模', reason: '原因', description: '说明', requirements: '岗位要求',
  periodNumber: '期数', submittedAt: '提交时间', handling: '处理方式', studentNo: '学号', division: '系', position: '岗位', count: '人数', ratio: '比例',
}
const statusLabels: Record<string, string> = { pending: '待处理', approved: '已同意', rejected: '已拒绝',
  pending_interview: '等待面试', interview_passed: '面试通过 / 已录用', interview_failed: '面试未通过',
  accepted: '已接受', cancelled: '已取消', active: '在招', hired: '已录用', inactive: '已暂停招聘', SUBMITTED: '已提交', NOT_SUBMITTED: '尚未提交',
  CONFIRMED: '已定单位待实习', IN_PROGRESS: '实习进行中', ENDED: '实习已结束', withdrawn: '已撤回', NOT_FOUND: '暂无有效投递', NO_OFFER: '已投递未录用', PENDING_CONFIRMATION: '已录用待确认', INTERRUPTED: '实习已中断', DELAYED: '已延期实习', UNKNOWN: '未知' }
function display(key: string, value: unknown, row: Record<string, unknown>) {
  if (value === '' || value == null) return '未提供'
  if (key === 'providesAccommodation') return value === 1 || value === '1' ? '提供' : (value === 0 || value === '0' ? '不提供' : '未提供')
  if (key === 'handling') return value === 'WAITING_OTHER' ? '等待他人处理' : '本人可处理'
  if (['updatedAt', 'dueAt', 'deadline', 'interviewTime', 'submittedAt', 'internshipStartDate', 'internshipEndDate'].includes(key)) {
    const date = new Date(String(value).replace(/\[[^\]]+\]$/, ''))
    if (!Number.isNaN(date.getTime())) return date.toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
  }
  if (key === 'status') {
    return statusLabels[String(value)] || value
  }
  return value
}
function fields(row: Record<string, unknown>) {
  return Object.entries(row).filter(([key, value]) => labels[key] && !(key === 'city' && row.location)
    && ((value !== '' && value != null) || (props.card.tool === 'jobs_details' && ['description', 'requirements'].includes(key))))
}
function title(row: Record<string, unknown>) {
  return ({ selfPractice: '自主实习申请', delay: '考研延迟申请', unitChange: '历史单位变更申请' } as any)[String(row.title)] || row.title || '业务记录'
}
function links(row: Record<string, unknown>) { return props.card.links.filter(link => link.objectId === String(row.id) && (!link.parameters?.objectType || link.parameters.objectType === row.objectType)) }
function salary(row: Record<string, unknown>) {
  return ['jobs_details', 'jobs_search'].includes(props.card.tool) ? jobSalary(row) : ''
}
const summaryLabels: Record<string, string> = { actionableCount: '本人可处理', waitingCount: '等待他人', responsible: '负责学生', submitted: '已提交', notSubmitted: '尚未提交', period: '当前期数', deadline: '本期截止', today: '查询日期', total: '学生总数', withoutOffer: '尚未获录用', withoutOfferRate: '尚未获录用比例', focusCount: '重点关注', focusRate: '关注比例', placed: '已落实', placedRate: '落实比例' }
const summary = computed(() => Object.entries(props.card.facts).filter(([key]) => summaryLabels[key]))
</script>
<template>
  <section class="agent-result" :data-tool="card.tool">
    <div class="result-heading"><strong>{{ card.title }}</strong><span>{{ date }} 查询</span></div>
    <p class="result-scope">{{ card.scope }}<template v-if="card.tool !== 'knowledge_lookup'"> · 共 {{ card.total }} 项</template></p>
    <div v-if="summary.length" class="result-summary"><span v-for="[key, value] in summary" :key="key">{{ summaryLabels[key] }} <b>{{ value }}</b></span></div>
    <template v-if="card.tool === 'knowledge_lookup'">
      <details class="knowledge-source"><summary>查看流程依据（{{ card.items.length }} 项）</summary>
        <div v-for="(row, index) in card.items" :key="index"><b>{{ row.title || row.topic }}</b><p>{{ row.rules }}</p></div>
      </details>
    </template>
    <template v-else>
      <p v-if="!card.items.length" class="empty-result">本次查询范围内没有记录。</p>
      <article v-for="(row, index) in visibleItems" :key="`${row.objectType || card.tool}:${row.id || index}`" class="result-item" :data-record-id="row.id">
        <strong>{{ title(row) }}</strong>
        <p v-if="salary(row)" class="salary">薪资 {{ salary(row) }}</p>
        <dl><div v-for="[key, value] in fields(row)" :key="key"><dt>{{ labels[key] }}</dt><dd>{{ display(key, value, row) }}</dd></div></dl>
        <div class="record-footer"><span v-if="row.id">记录 #{{ row.id }}</span><span><button v-if="appliable(row)" type="button" class="apply-button" @click="$emit('apply', row)">确认投递</button><button v-for="link in links(row)" :key="link.action" type="button" @click="$emit('navigate', link)">{{ link.label }} <span aria-hidden="true">↗</span></button></span></div>
      </article>
      <div v-if="card.items.length > 3" class="result-actions"><button type="button" @click="expanded = !expanded">{{ expanded ? '收起记录' : `展开本页其余 ${card.items.length - 3} 条记录` }}</button></div>
    </template>
    <p v-if="card.hasMore" class="result-note">当前仅展示部分记录，可继续提问查看下一页；统计数为完整查询范围。</p>
    <details v-if="card.warnings.length" class="result-note"><summary>统计口径与说明</summary><p v-for="warning in card.warnings" :key="warning">{{ warning }}</p></details>
    <div v-if="card.links.some(link => !link.objectId)" class="result-actions"><button v-for="link in card.links.filter(link => !link.objectId)" :key="link.action" @click="$emit('navigate', link)">{{ link.label }} ↗</button></div>
  </section>
</template>
<style scoped>
.agent-result{border:1px solid #e5eaf1;border-radius:12px;background:#fff;margin-top:10px;overflow:hidden;font-size:12.5px}
.result-heading{display:flex;justify-content:space-between;align-items:center;gap:8px;padding:11px 12px 2px;color:#16233a;font-size:13px}
.result-heading strong{font-weight:650}
.result-heading span{color:#98a2b3;white-space:nowrap;font-size:10.5px;background:#f4f6f9;border-radius:5px;padding:1px 7px;flex-shrink:0}
.result-scope{margin:0;padding:2px 12px 9px;color:#66727f;font-size:11px}
.result-summary{display:flex;flex-wrap:wrap;gap:6px 14px;background:#f6f9fd;border-top:1px solid #eef2f6;border-bottom:1px solid #eef2f6;padding:8px 12px;color:#66727f;font-size:11px}
.result-summary b{color:#16233a}
.result-item{padding:11px 12px}
.result-item+.result-item{border-top:1px solid #f0f3f6}
.result-item>strong{color:#16233a;font-size:13px;font-weight:650;overflow-wrap:anywhere}
.salary{color:#15803d;font-weight:600;margin:4px 0 0;font-size:12.5px}
.result-item dl{margin:7px 0 0}
.result-item dl div{display:flex;gap:8px;margin:4px 0;align-items:baseline}
.result-item dt{color:#98a2b3;min-width:56px;max-width:72px;flex-shrink:0;font-size:11px}
.result-item dd{margin:0;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.65;color:#43536a;max-height:170px;overflow:auto;font-size:12px}
.record-footer{display:flex;justify-content:space-between;align-items:center;margin-top:9px;color:#a5b0bf;font-size:10.5px}
.agent-result button{color:#1677ff;background:transparent;background-image:none;border:0;box-shadow:none;padding:4px 6px;cursor:pointer;font-size:12px;border-radius:6px;transform:none}
.agent-result button:hover{background:#eef4ff;background-color:#eef4ff;border-color:transparent;box-shadow:none;transform:none;text-decoration:none}
.agent-result .apply-button{color:#fff;background:#1677ff;border-radius:7px;padding:5px 12px;margin-right:4px;font-weight:600}
.agent-result .apply-button:hover{background:#0f63d6}
.result-note{color:#66727f;font-size:11px;line-height:1.65;padding:8px 12px;margin:0;border-top:1px solid #f0f3f6}
.result-note summary,.knowledge-source summary{cursor:pointer;color:#1677ff}
.result-actions{padding:7px 10px;background:#f8fafc;border-top:1px solid #f0f3f6;display:flex;gap:6px;flex-wrap:wrap}
.knowledge-source{padding:6px 12px 12px;color:#43536a;line-height:1.7;font-size:12px}
.knowledge-source>div{margin-top:9px;padding:9px 10px;background:#f8fafc;border-radius:8px}
.knowledge-source p{margin:5px 0}
.empty-result{margin:0;padding:14px 12px;color:#98a2b3;text-align:center;font-size:12px}</style>
