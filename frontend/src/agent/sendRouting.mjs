// 保留旧调用方的句式帮助函数；所有消息均由统一业务 Agent 决定工具。
const UI_ACTION = /(?:帮我|替我|请你)?(?:打开|进入|跳转|切换|填写|填入|填上|改成|改为|设为|设置为|换成|选中|选择|勾选|取消勾选)|(?:标题|内容|状态|名称|目标群体).{0,10}(?:用|弄成)|(?:顺便|然后|再帮我)/
export function isDirectQuestion(message) {
  const text = String(message || '').trim()
  // “可以选择多少人吗”是在问字段能力；“帮我选择张三”才需要操作路由。
  const asksAboutAction = !/帮我|替我|请你|顺便|然后|再帮|改成|改为|设为|设置为|换成|填入|填上|选中|勾选/.test(text)
    && /怎么|如何|在哪|哪里|能否|是否|能不能|可不可以|(?:可以|能).+吗[？?。!！]*$/.test(text)
  if (asksAboutAction) return true
  return Boolean(text && !UI_ACTION.test(text) && /什么|为何|为什么|怎么|如何|多少|几个|几项|是谁|哪里|哪儿|哪些|是否|吗[？?。!！]*$|[？?]$/.test(text))
}

/** 页面中的表单信息随上下文发送，不再额外调用字段意图模型。 */
export function shouldResolveFormFill(message, { hasForm = false } = {}) {
  return false
}

/** 导航由业务 Agent 返回受校验的页面动作。 */
export function shouldResolveUiIntent(message) {
  return false
}

/** Explicit page/form actions may coexist with an attached import workbook. */
export function isExplicitUiAction(message) {
  return /(?:打开|进入|跳转到).{0,50}(?:页面|表单|管理|资料|中心|设置|公告|岗位|企业|学生|教师|班级)/.test(String(message || '').trim())
}

/** Public descriptors only; keep room within the server's complete filters limit. */
export function boundedUiFilters(filters = {}, targets = [], form = null) {
  const { uiTargets: _targets, uiForm: _form, ...base } = filters
  const result = { ...base, uiTargets: [] }
  const fits = candidate => JSON.stringify(candidate).length <= 22000
  const text = (value, limit) => String(value ?? '').slice(0, limit)
  if (form) result.uiForm = { formId: text(form.formId, 100), formTitle: text(form.formTitle, 200), instanceId: form.instanceId, fields: [] }
  for (const field of (form?.fields || []).filter(field => field.type !== 'upload').slice(0, 40)) {
    const descriptor = { prop: text(field.prop, 80), label: text(field.label, 120), type: field.type }
    for (const key of ['required', 'allowCustom', 'min', 'max', 'integer', 'valueType']) if (field[key] != null) descriptor[key] = field[key]
    if (field.patternHint) descriptor.patternHint = text(field.patternHint, 300)
    if (Array.isArray(field.options)) descriptor.options = field.options.slice(0, 50)
      .filter(option => ['string', 'number'].includes(typeof option.value)).map(option => ({ value: typeof option.value === 'string' ? text(option.value, 200) : option.value, label: text(option.label, 120) }))
    const candidate = { ...result, uiForm: { ...result.uiForm, fields: [...result.uiForm.fields, descriptor] } }
    if (fits(candidate)) result.uiForm = candidate.uiForm
  }
  for (const target of targets.slice(0, 100)) {
    const descriptor = { id: text(target.id, 80), label: text(target.label, 200), kind: target.kind,
      ...(target.path ? { path: text(target.path, 300) } : {}), ...(target.instanceId != null ? { instanceId: target.instanceId } : {}) }
    const candidate = { ...result, uiTargets: [...result.uiTargets, descriptor] }
    if (fits(candidate)) result.uiTargets.push(descriptor)
  }
  return result
}
