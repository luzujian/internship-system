// This routes only to a read-only preview. Creating/enabling still requires confirmation.
export const automationTemplate = '我想要创建一个自动化任务。\n任务内容是：\n执行条件是：'

// Templates come from the server catalog so preview and execution share the same rules.
export function automationRequest(message, pending = '') {
  const text = message.trim()
  if (!text) return null
  // A page named “通知管理” is not a scheduled notification request.
  if (/(?:打开|进入|跳转|切换到).{0,40}(?:页面|管理|表单|弹窗)/.test(text)) return null
  const question = /^(?:请|帮我)?(?:解释|介绍|说明)|什么意思|有什么用|如何|怎么(?:创建|设置|使用)|为什么|是什么|什么是/.test(text)
  if (question) return null
  if (pending && /^(?:改成|改为|改到|调整为|每天|每周|上午|下午|晚上|通过|用站内|用邮件|只提醒|仅提醒|补充|是的|确认后|截止|任务内容|执行时间|执行条件|附加条件)/.test(text)) {
    return `${pending}\n教师补充或修正，冲突处以本次为准：${text}`
  }
  const reminder = /提醒|通知|催交|跟进/.test(text)
  const scheduled = /每天|每日|每周|每月|定时|自动|明天|后天|每隔|\d+[点时]|上午|下午|晚上|截止前|条件满足/.test(text)
  const create = /创建|新建|修改|设置|安排|帮我|请|我要|我想/.test(text)
  if ((reminder && scheduled && (create || !/[吗么？?]$/.test(text))) || (create && /自动化任务|自动跟进任务/.test(text))) return text
  return null
}

export function automationSummary(task) {
  const conditions = { REFLECTION_NOT_SUBMITTED: '心得未提交', APPRAISAL_MISSING: '鉴定表未提交', OFFER_PENDING: '实习待确认', SCHEDULED_REMINDER: '定时提醒' }
  let schedule
  try { schedule = typeof task.scheduleConfig === 'string' ? JSON.parse(task.scheduleConfig) : task.scheduleConfig } catch { schedule = null }
  const s = schedule || {}
  const when = s.mode === 'MANUAL' ? '运行一次' : s.mode === 'DAILY' ? `每天 ${s.time || ''}` : s.mode === 'WEEKLY' ? `每周${(s.weekdays || []).map(d => ['一','二','三','四','五','六','日'][d - 1]).join('、')} ${s.time || ''}` : s.mode === 'ONCE' ? `${s.date || ''} ${s.time || ''}` : '按条件检查'
  const days = task.triggerDays != null && ['REFLECTION_NOT_SUBMITTED', 'APPRAISAL_MISSING'].includes(task.triggerCondition) ? `截止前${task.triggerDays}天起 · ` : ''
  return `${days}${conditions[task.triggerCondition] || '按已确认条件'} · ${when} · ${task.action === 'EMAIL' ? '邮件' : '站内提醒'}`
}
