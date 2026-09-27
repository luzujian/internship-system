/**
 * 表单上下文（智能填表核心）：页面/弹窗打开表单时注册字段结构与响应式 model 引用。
 * 赋值只写 Vue 响应式状态（组件自动更新并参与原有校验），不触发提交；
 * 每批赋值前保存字段快照，支持逐批撤销；formId 或实例代际不匹配的赋值请求直接拒绝，
 * 防止页面切换后误填，也防止同一 formId 关闭重开新记录后被旧实例的迟到响应串写。
 * AI 请求期间被用户手动改动的字段会被保留，不会被旧响应覆盖。
 * 全部能力以模块级单例提供（同一时刻只关联一个表单，最新注册者生效）。
 */
import { ref, watch } from 'vue'
import type { BusinessObject, FillAssignment, FillSkipped, FormFieldDescriptor, FormValidationSummary, FormBusinessReceipt } from './types'

interface FormRegistration {
  formId: string
  title: string
  fields: FormFieldDescriptor[]
  getFields?: () => FormFieldDescriptor[]
  getObject?: () => BusinessObject | null
  getValidationSummary?: () => FormValidationSummary | Promise<FormValidationSummary>
  getModel: () => Record<string, unknown> | null
  /** el-form 实例引用（可选）：赋值后清除字段校验态，让组件重新计算 */
  getFormEl?: () => { clearValidate?: (props?: string | string[]) => void; validate?: (...args: any[]) => any; fields?: any[] } | null
  /** 实例代际（register 时盖章，外部无需赋值）：同一 formId 重开后，旧实例发出的请求其迟到响应按代际拒绝 */
  instanceId?: number
}

interface FormUndoBatch {
  fillBatchId: string
  formId: string
  instanceId: number
  snapshot: Record<string, unknown>
  appliedValues: Record<string, unknown>
  /** 本批写入时各字段的真实修改版本；撤销前逐字段比对，避免"手改后改回原值"被误回滚 */
  revisions: Record<string, number>
  previousOwners: Record<string, string | undefined>
  sessionKey: string
  source: "ai_fill"
}

export interface ApplyAssignmentsResult {
  applied: FillAssignment[]
  skipped: FillSkipped[]
  fillBatchId?: string
  beforeValues?: Record<string, unknown>
  afterValues?: Record<string, unknown>
  missingFields?: string[]
  validationErrors?: string[]
}

export interface UndoLastResult {
  restored: string[]
  preserved: string[]
  fillBatchId?: string
}

/** 当前注册的表单（模块级单例状态） */
export const currentForm = ref<(FormRegistration & { instanceId: number }) | null>(null)
/** 可撤销的赋值批次深度 */
export const undoDepth = ref(0)
export const formBusinessReceipt = ref<FormBusinessReceipt | null>(null)
let activeSessionKey = ''
let sessionGeneration = 0
/** 切换会话/用户只使撤销能力失效，不改变表单和已完成业务。 */
export function setFormSession(sessionKey: string) {
  if (sessionKey === activeSessionKey) return
  activeSessionKey = sessionKey
  sessionGeneration++
  undoStack.length = 0
  undoDepth.value = 0
  fieldOwners.clear()
  aiWrittenFields.clear()
}
const baselineMetadata = new WeakMap<Record<string, unknown>, { revisions: Record<string, number>; instanceId: number; sessionKey: string; sessionGeneration: number }>()
const fieldOwners = new Map<string, string>()
// Kept after saving so a later explicit publish can acknowledge the same fields.
const aiWrittenFields = new Set<string>()

export function getFormFields(registration = currentForm.value): FormFieldDescriptor[] {
  return registration ? (registration.getFields?.() ?? registration.fields) : []
}

function collectFieldValidation(registration = currentForm.value): FormValidationSummary {
  const model = registration?.getModel()
  if (!registration || !model) return { missingFields: [], validationErrors: [] }
  const missingFields: string[] = []
  const validationErrors: string[] = []
  for (const field of getFormFields(registration)) {
    const value = model[field.prop]
    const empty = value == null || (typeof value === 'string' && !value.trim()) || (Array.isArray(value) && !value.length)
    if (field.required && empty) missingFields.push(field.label)
    if (!empty && field.type === 'number') {
      const number = Number(value)
      if (!Number.isFinite(number) || (field.min != null && number < field.min) || (field.max != null && number > field.max) || (field.integer && !Number.isInteger(number))) validationErrors.push(`${field.label}格式或范围不正确`)
    }
  }
  return { missingFields, validationErrors }
}

function mergeValidation(...summaries: (FormValidationSummary | undefined)[]): FormValidationSummary {
  return { missingFields: [...new Set(summaries.flatMap(summary => summary?.missingFields ?? []))],
    validationErrors: [...new Set(summaries.flatMap(summary => summary?.validationErrors ?? []))] }
}

export function getFormValidation(): FormValidationSummary {
  const summary = collectFieldValidation()
  for (const field of currentForm.value?.getFormEl?.()?.fields ?? []) {
    if (field.validateState === 'error' && field.validateMessage) summary.validationErrors.push(String(field.validateMessage))
  }
  try {
    const extra = currentForm.value?.getValidationSummary?.()
    if (extra && typeof (extra as Promise<unknown>).then === 'function') {
      void (extra as Promise<unknown>).catch(() => {})
      return summary
    }
    return mergeValidation(summary, extra as FormValidationSummary | undefined)
  } catch { return mergeValidation(summary, { missingFields: [], validationErrors: ['原表单校验暂未完成，请返回表单核对。'] }) }
}

/** Run the same asynchronous validators as the original form, without submitting it. */
export async function validateForm(): Promise<FormValidationSummary> {
  const registration = currentForm.value
  if (!registration) return { missingFields: [], validationErrors: [] }
  const baseline = captureValues(registration.formId)
  const captured = baseline ? baselineMetadata.get(baseline) : undefined
  const initial = collectFieldValidation(registration)
  let timer: ReturnType<typeof setTimeout> | undefined
  const validate = async (): Promise<FormValidationSummary> => {
    let extra: FormValidationSummary | undefined
    try { extra = await registration.getValidationSummary?.() }
    catch { extra = { missingFields: [], validationErrors: ['原表单业务校验暂未完成，请返回表单核对。'] } }
    const errors: string[] = []
    const form = registration.getFormEl?.()
    if (form?.validate) {
      const result = await new Promise<{ valid: boolean; fields?: unknown }>(resolve => {
        let settled = false
        const finish = (valid: boolean, fields?: unknown) => { if (!settled) { settled = true; resolve({ valid, fields }) } }
        try {
          const pending = form.validate!((valid: boolean, fields: unknown) => finish(valid, fields))
          if (pending && typeof pending.then === 'function') pending.then((valid: unknown) => finish(valid !== false), (fields: unknown) => finish(false, fields))
          else if (typeof pending === 'boolean') finish(pending)
        } catch (error) { finish(false, error) }
      })
      if (!result.valid) {
        const fields = result.fields && typeof result.fields === 'object' ? Object.values(result.fields) : []
        for (const item of fields.flat()) {
          if (item && typeof item === 'object' && 'message' in item && typeof item.message === 'string') errors.push(item.message)
        }
        for (const field of form.fields ?? []) if (field.validateState === 'error' && field.validateMessage) errors.push(String(field.validateMessage))
        if (!errors.length) errors.push('原表单校验未通过，请返回表单查看具体提示。')
      }
    }
    return mergeValidation(initial, extra, { missingFields: [], validationErrors: errors })
  }
  try {
    const summary = await Promise.race([validate(), new Promise<FormValidationSummary>(resolve => {
      timer = setTimeout(() => resolve(mergeValidation(initial, { missingFields: [], validationErrors: ['原表单校验超时，请返回表单重试；尚未保存或提交。'] })), 10000)
    })])
    if (currentForm.value?.instanceId !== captured?.instanceId || captured?.sessionGeneration !== sessionGeneration ||
      Object.entries(captured?.revisions ?? {}).some(([prop, revision]) => fieldRevision(prop, registration.formId, registration.instanceId) !== revision)) {
      return { missingFields: [], validationErrors: ['校验期间表单已改变，请以当前原表单提示为准。'] }
    }
    return summary
  } finally { if (timer) clearTimeout(timer) }
}

/** Capture only the active target before its business request, and acknowledge only explicit success. */
export function beginFormOperation(formId: string, submittedProps?: readonly string[]) {
  const registration = currentForm.value
  const instanceId = registration?.formId === formId ? registration.instanceId : undefined
  const snapshot = instanceId == null ? null : captureValues(formId)
  if (snapshot && submittedProps) for (const prop of Object.keys(snapshot)) if (!submittedProps.includes(prop)) delete snapshot[prop]
  let completed = false
  return {
    complete(receipt: Omit<FormBusinessReceipt, 'formId' | 'instanceId' | 'createdAt' | 'savedValues'>) {
      if (completed || !snapshot || instanceId == null) return false
      completed = true
      return reportFormOutcome(formId, receipt, instanceId, snapshot)
    }
  }
}

/** 只能在原业务接口明确成功后调用；回执不代替保存，不重放任何操作。 */
export function reportFormOutcome(formId: string, receipt: Omit<FormBusinessReceipt, 'formId' | 'instanceId' | 'createdAt'>, expectedInstanceId?: number, savedValues?: Record<string, unknown> | null) {
  const registration = currentForm.value
  if (!registration || registration.formId !== formId || (expectedInstanceId != null && expectedInstanceId !== registration.instanceId)) return false
  const captured = savedValues ? baselineMetadata.get(savedValues) : undefined
  if (captured && (captured.instanceId !== registration.instanceId || captured.sessionGeneration !== sessionGeneration)) return false
  const tracked = aiWrittenFields
  const safeSavedValues = savedValues ? Object.fromEntries(Object.entries(savedValues).filter(([prop]) => tracked.has(prop)).map(([prop, value]) => [prop, cloneValue(value)])) : undefined
  formBusinessReceipt.value = { ...receipt, savedValues: safeSavedValues, formId, instanceId: registration.instanceId, createdAt: Date.now() }
  // Keep later unsaved batches reversible; a completed save must not silently consume them.
  for (let index = undoStack.length - 1; index >= 0; index--) {
    const batch = undoStack[index]
    for (const [prop, value] of Object.entries(batch.appliedValues)) {
      if (!safeSavedValues || (Object.prototype.hasOwnProperty.call(safeSavedValues, prop) && valuesEqual(value, safeSavedValues[prop]))) {
        const key = fieldKey(prop, batch.instanceId, batch.formId)
        if (fieldOwners.get(key) === batch.fillBatchId) fieldOwners.delete(key)
        delete batch.appliedValues[prop]
        delete batch.snapshot[prop]
        delete batch.revisions[prop]
        delete batch.previousOwners[prop]
      }
    }
    if (!Object.keys(batch.appliedValues).length) undoStack.splice(index, 1)
  }
  undoDepth.value = undoStack.length
  return true
}

/** 撤销快照栈：每批记录填写前旧值和本批实际写入值；撤销时区分普通回滚与用户手改保留。 */
const undoStack: FormUndoBatch[] = []
const MAX_UNDO = 10

/** 字段真实修改版本：任何非本账本写入的变化都会递增，用于保护"手改后改回原值"。 */
const fieldRevisions = new Map<string, number>()
/** 上次观测到的字段值，用于 diff 出外部（用户）修改。 */
const lastSeen = new Map<string, unknown>()
/** 本账本自己写入的字段，回调时不计为用户修改。 */
const selfWrites = new Set<string>()
let stopModelWatch: (() => void) | null = null
let batchCounter = 0

/** 读取字段真实修改版本（测试与调试用）。 */
export function fieldRevision(prop: string, formId: string, instanceId?: number) {
  const instance = instanceId ?? currentForm.value?.instanceId ?? 0
  return fieldRevisions.get(fieldKey(prop, instance, formId)) ?? 0
}
function fieldKey(prop: string, instanceId: number, formId: string) { return `${formId}#${instanceId}#${prop}` }
function newBatchId() { batchCounter += 1; return `fill-${Date.now().toString(36)}-${batchCounter}` }

/** 观测当前表单 model：外部修改 → 字段版本 +1；本账本写入 → 只同步基线。 */
function watchRegisteredForm(registration: FormRegistration & { instanceId: number }) {
  stopModelWatch?.()
  stopModelWatch = null
  let observedModel = registration.getModel()
  let observedObject = JSON.stringify(registration.getObject?.() ?? null)
  for (const field of getFormFields(registration)) {
    lastSeen.set(fieldKey(field.prop, registration.instanceId, registration.formId), cloneValue(observedModel?.[field.prop]))
  }
  stopModelWatch = watch(
    () => [registration.getModel(), registration.getObject?.(), ...getFormFields(registration).map(field => registration.getModel()?.[field.prop])],
    () => {
      const model = registration.getModel()
      const object = JSON.stringify(registration.getObject?.() ?? null)
      if (model !== observedModel || object !== observedObject) {
        observedModel = model; observedObject = object
        registration.instanceId = ++instanceCounter
        undoStack.length = 0; undoDepth.value = 0; fieldOwners.clear(); aiWrittenFields.clear(); fieldRevisions.clear(); lastSeen.clear()
        for (const field of getFormFields(registration)) lastSeen.set(fieldKey(field.prop, registration.instanceId, registration.formId), cloneValue(model?.[field.prop]))
        return
      }
      if (!model) return
      for (const field of getFormFields(registration)) {
        const key = fieldKey(field.prop, registration.instanceId, registration.formId)
        const current = model[field.prop]
        if (valuesEqual(current, lastSeen.get(key))) continue
        lastSeen.set(key, cloneValue(current))
        if (selfWrites.has(key)) { selfWrites.delete(key); continue }
        fieldRevisions.set(key, (fieldRevisions.get(key) ?? 0) + 1)
      }
    },
    { deep: true, flush: "sync" }
  )
}

/** 表单实例代际计数：每次 register 递增盖章，同一 formId 重开必换代，旧实例迟到响应被拒。 */
// History survives reloads; a new page must not reuse an old history item's id.
let instanceCounter = Date.now() * 1024 + Math.floor(Math.random() * 1024)

/** 页面/弹窗打开表单时调用；重复注册时以最新为准（并盖新代际） */
export function register(registration: FormRegistration) {
  if (!registration?.formId || !registration.title || !registration.fields?.length) return
  currentForm.value = { ...registration, instanceId: ++instanceCounter }
  undoStack.length = 0; undoDepth.value = 0; fieldOwners.clear(); aiWrittenFields.clear()
  // 换代即作废上一实例的字段版本与基线，避免旧代际串写。
  fieldRevisions.clear()
  lastSeen.clear()
  selfWrites.clear()
  watchRegisteredForm(currentForm.value)
}

/** 关闭弹窗/离开页面时调用；仅当仍是当前表单时清除 */
export function unregister(formId: string) {
  if (currentForm.value?.formId === formId) {
    stopModelWatch?.()
    stopModelWatch = null
    currentForm.value = null
    undoStack.length = 0
    undoDepth.value = 0
    fieldRevisions.clear()
    lastSeen.clear()
    selfWrites.clear()
    fieldOwners.clear()
    aiWrittenFields.clear()
  }
}

/** 在发起 AI 请求前捕获当前字段基线，用于识别请求期间的用户手改。 */
export function captureValues(formId: string): Record<string, unknown> | null {
  const registration = currentForm.value
  if (!registration || registration.formId !== formId) return null
  const model = registration.getModel()
  if (!model) return null
  const snapshot: Record<string, unknown> = {}
  const revisions: Record<string, number> = {}
  for (const field of getFormFields(registration)) {
    if (field.prop in model) snapshot[field.prop] = cloneValue(model[field.prop])
    revisions[field.prop] = fieldRevision(field.prop, formId, registration.instanceId)
  }
  baselineMetadata.set(snapshot, { revisions, instanceId: registration.instanceId, sessionKey: activeSessionKey, sessionGeneration })
  return snapshot
}

/**
 * 应用一批字段赋值：写响应式 model + 清除校验态。
 * 返回 null 表示表单已切换或实例换代（防误填/防旧实例串写）；
 * baseline 中记录用户在请求期间改动过的字段会被跳过。
 * expectedInstanceId 为请求发起时捕获的实例代际：同一 formId 关闭重开后不匹配，迟到响应直接拒绝。
 */
export function applyAssignments(
  formId: string,
  assignments: FillAssignment[],
  baseline?: Record<string, unknown> | null,
  expectedInstanceId?: number,
  message?: string
): ApplyAssignmentsResult | null {
  const registration = currentForm.value
  if (!registration || registration.formId !== formId) return null
  if (expectedInstanceId !== undefined && registration.instanceId !== expectedInstanceId) return null
  const captured = baseline ? baselineMetadata.get(baseline) : undefined
  if (captured && (captured.instanceId !== registration.instanceId || captured.sessionKey !== activeSessionKey || captured.sessionGeneration !== sessionGeneration)) return null
  const model = registration.getModel()
  if (!model) return null

  const fields = getFormFields(registration)
  const counts = new Map<string, number>()
  for (const item of assignments) if (item?.prop) counts.set(item.prop, (counts.get(item.prop) ?? 0) + 1)
  const applicable = assignments.filter(item => item
    && Object.prototype.hasOwnProperty.call(model, item.prop)
    && fields.some(field => field.prop === item.prop && field.type !== 'upload'))
  if (!applicable.length) return { applied: [], skipped: [] }

  const writable: FillAssignment[] = []
  const skipped: FillSkipped[] = []
  for (const item of applicable) {
    const descriptor = fields.find(field => field.prop === item.prop)!
    const instruction = instructionRejection(item, descriptor, model[item.prop], message)
    if ((counts.get(item.prop) ?? 0) > 1) {
      if (!skipped.some(row => row.prop === item.prop)) skipped.push({ prop: item.prop, reason: '同一字段返回多个值，请明确后重试。' })
    } else if (!Number.isFinite(item.confidence) || item.confidence < 0.65) {
      skipped.push({ prop: item.prop, reason: '识别把握不足，请核对后明确填写值。' })
    } else if (instruction) {
      skipped.push({ prop: item.prop, reason: instruction })
    } else if (baseline && Object.prototype.hasOwnProperty.call(baseline, item.prop)
      && (!valuesEqual(model[item.prop], baseline[item.prop]) || (captured && fieldRevision(item.prop, formId, registration.instanceId) !== captured.revisions[item.prop]))) {
      skipped.push({
        prop: item.prop,
        reason: '字段在 AI 处理期间已被手动修改，已保留当前值。'
      })
    } else if (assignmentValue(item, descriptor) === undefined) {
      skipped.push({ prop: item.prop, reason: '字段值格式或选项无效，已保留当前值。' })
    } else {
      writable.push(item)
    }
  }

  if (!writable.length) return { applied: [], skipped }

  // 快照本批字段的填写前旧值，以及本批实际写入值，供撤销时判断用户后续是否再手改。
  const snapshot: Record<string, unknown> = {}
  const appliedValues: Record<string, unknown> = {}
  const revisions: Record<string, number> = {}
  const previousOwners: Record<string, string | undefined> = {}
  const fillBatchId = newBatchId()
  const applied: FillAssignment[] = []
  for (const item of writable) {
    const descriptor = getFormFields(registration).find(field => field.prop === item.prop && field.type !== 'upload')
    const value = descriptor ? assignmentValue(item, descriptor) : undefined
    // Earlier assignments can change a dependent dropdown; only its current options may be written.
    if (value === undefined) { skipped.push({ prop: item.prop, reason: '关联字段或可用选项已变化，请核对后重试。' }); continue }
    const key = fieldKey(item.prop, registration.instanceId, formId)
    // 先声明这是本账本写入，再落值：观测器不会把它记成用户修改。
    selfWrites.add(key)
    lastSeen.set(key, cloneValue(model[item.prop]))
    snapshot[item.prop] = cloneValue(model[item.prop])
    model[item.prop] = value
    selfWrites.delete(key)
    lastSeen.set(key, cloneValue(model[item.prop]))
    appliedValues[item.prop] = cloneValue(model[item.prop])
    aiWrittenFields.add(item.prop)
    revisions[item.prop] = fieldRevisions.get(key) ?? 0
    previousOwners[item.prop] = fieldOwners.get(key)
    fieldOwners.set(key, fillBatchId)
    applied.push(item)
  }
  // 通知 el-form 重新计算校验态（赋值后的字段显示为已填/待重新校验，而不是残留错误）
  try { registration.getFormEl?.()?.clearValidate?.(writable.map(item => item.prop)) } catch { /* 忽略组件未挂载 */ }

  if (!applied.length) return { applied: [], skipped, ...getFormValidation() }
  undoStack.push({ fillBatchId, formId, instanceId: registration.instanceId, snapshot: cloneValue(snapshot), appliedValues: cloneValue(appliedValues), revisions, previousOwners, sessionKey: activeSessionKey, source: "ai_fill" })
  if (undoStack.length > MAX_UNDO) undoStack.shift()
  undoDepth.value = undoStack.length
  return { applied, skipped, fillBatchId, beforeValues: snapshot, afterValues: appliedValues, ...getFormValidation() }
}

/** 撤销最近一批 AI 赋值（纯本地操作，不调用后端）；用户之后手改的字段保留当前值；换代实例不撤销。 */
export function undoLast(fillBatchId?: string): UndoLastResult | null {
  const index = fillBatchId ? undoStack.findIndex(batch => batch.fillBatchId === fillBatchId) : undoStack.length - 1
  const latest = undoStack[index]
  const registration = currentForm.value
  if (!latest || !registration || registration.formId !== latest.formId
    || registration.instanceId !== latest.instanceId || latest.sessionKey !== activeSessionKey) return null
  const model = registration.getModel()
  if (!model) return null

  const restored: string[] = []
  const preserved: string[] = []
  for (const prop of Object.keys(latest.snapshot)) {
    const key = fieldKey(prop, latest.instanceId, latest.formId)
    // 版本保护：只要该字段被外部改过（含手改后又改回原值），就不回滚，绝不用当前值相等来放行。
    if ((fieldRevisions.get(key) ?? 0) === (latest.revisions[prop] ?? 0) && fieldOwners.get(key) === latest.fillBatchId) {
      selfWrites.add(key)
      lastSeen.set(key, cloneValue(model[prop]))
      model[prop] = cloneValue(latest.snapshot[prop])
      selfWrites.delete(key)
      lastSeen.set(key, cloneValue(model[prop]))
      restored.push(prop)
      if (latest.previousOwners[prop]) fieldOwners.set(key, latest.previousOwners[prop]!)
      else fieldOwners.delete(key)
    } else {
      preserved.push(prop)
    }
  }
  try { registration.getFormEl?.()?.clearValidate?.([...restored, ...preserved]) } catch { /* 忽略 */ }
  undoStack.splice(index, 1)
  undoDepth.value = undoStack.length
  const result: UndoLastResult = { restored, preserved }
  Object.defineProperty(result, 'fillBatchId', { value: latest.fillBatchId, enumerable: false })
  return result
}

/** 便捷封装：与模块级 API 等价，供组件按组合式风格使用。 */
export function useFormContext() {
  return { currentForm, undoDepth, register, unregister, captureValues, applyAssignments, undoLast, fieldRevision, getFormFields, getFormValidation, validateForm, beginFormOperation, reportFormOutcome, formBusinessReceipt, setFormSession }
}

function assignmentValue(item: FillAssignment, field: FormFieldDescriptor): unknown {
  if (item.operation === 'CLEAR') {
    if (field.valueType === 'array') return []
    if (field.type === 'number' || field.valueType === 'number' || field.type === 'date' || field.type === 'datetime' || field.valueType === 'date') return null
    return ''
  }
  return coerceValue(item.value, field)
}

function fieldNames(field: FormFieldDescriptor): string[] {
  const names = [field.label, field.prop]
  if (field.label.includes('人数') || field.prop.toLowerCase().includes('recruit')) names.push('人数')
  if (field.label.includes('薪资') || field.prop.toLowerCase().startsWith('salary')) names.push('薪资', '工资')
  return [...new Set(names.filter(Boolean))]
}
const regexQuote = (text: string) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
function explicitlyPreserved(message: string, field: FormFieldDescriptor): boolean {
  const negative = '(?:不要|不用|别|不需要|不能|不许|不得|不)'
  const action = '(?:修改|改变|调整|更新|改|动|清空|清除|清掉|删除)'
  return fieldNames(field).some(name => {
    const quoted = regexQuote(name)
    return new RegExp(`${negative}(?:再)?\\s*${action}\\s*(?:把|将)?\\s*${quoted}|${negative}(?:再)?\\s*(?:把|将)\\s*${quoted}(?:字段|内容)?\\s*${action}|(?:保持|保留)\\s*${quoted}|${quoted}(?:字段|内容)?\\s*(?:也|都|先|暂时)?\\s*(?:不变|不动|不改|${negative}(?:再)?${action}|保持原|保持不变)`).test(message)
  })
}
function explicitlyCleared(message: string, field: FormFieldDescriptor): boolean {
  if (explicitlyPreserved(message, field)) return false
  return fieldNames(field).some(name => new RegExp(`(?:清空|清除|清掉)\\s*${regexQuote(name)}|${regexQuote(name)}(?:字段|内容)?\\s*(?:清空|清除|清掉|留空|改为空|设为空)`).test(message))
}
function explicitlyAssigned(message: string, field: FormFieldDescriptor): boolean {
  if (explicitlyPreserved(message, field)) return false
  return message.split(/[，,。；;\n]/).some(clause => fieldNames(field).some(name => clause.includes(name)))
}
function instructionRejection(item: FillAssignment, field: FormFieldDescriptor, current: unknown, message?: string): string | null {
  if (item.operation && item.operation !== 'SET' && item.operation !== 'CLEAR') return '字段操作类型无效，已保留当前值。'
  const preserved = message !== undefined && explicitlyPreserved(message, field)
  const selective = message !== undefined && /(?:其他|其余)[^，,。；;\n]{0,8}(?:不要|不用|不变|不动|不改|别|保持)|只(?:修改|改|调整|更新|填写|填入)/.test(message)
  if (preserved || (selective && !explicitlyAssigned(message!, field) && !explicitlyCleared(message!, field))) return '你要求保留此字段，本次没有改动。'
  if (item.operation === 'CLEAR') return item.overwriteAllowed === true && message !== undefined && explicitlyCleared(message, field) ? null : '清空操作尚未得到明确授权，当前值已保留；请明确说出要清空的字段。'
  const empty = current == null || (typeof current === 'string' && !current.trim()) || (Array.isArray(current) && !current.length)
  const strict = message !== undefined || item.operation !== undefined || item.overwriteAllowed !== undefined
  if (strict && !String(item.value ?? '').trim()) return '空值不能替代清空操作，请明确说明是否清空该字段。'
  if (!empty && strict && !valuesEqual(current, assignmentValue(item, field)) &&
    (item.overwriteAllowed !== true || (message !== undefined && !explicitlyAssigned(message, field)))) return '此字段已有内容，请明确说明要修改该字段及目标值。'
  return null
}

/** 按字段声明和选项值把模型输出的字符串转换为表单真实绑定形态。 */
function coerceValue(value: string, descriptor?: FormFieldDescriptor): unknown {
  const text = String(value ?? '').trim()
  if (descriptor?.valueType === "array") {
    if (!text) return []
    const values = text.split(/[、,，;；]/).map(item => item.trim()).filter(Boolean)
      .map(item => coerceOptionValue(item, descriptor))
    return values.includes(undefined) ? undefined : values
  }
  if (descriptor?.valueType === "date" || descriptor?.type === "date" || descriptor?.type === "datetime") {
    if (descriptor?.type === 'datetime' && !/^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(?::\d{2})?$/.test(text)) return undefined
    const clock = /[ T](\d{2}):(\d{2})(?::(\d{2}))?/.exec(text)
    if (clock && (Number(clock[1]) > 23 || Number(clock[2]) > 59 || Number(clock[3] ?? 0) > 59)) return undefined
    const parsed = new Date(text)
    const parts = /^(\d{4})-(\d{2})-(\d{2})(?:$|[T ])/.exec(text)
    if (!Number.isFinite(parsed.getTime())) return undefined
    if (parts) {
      const [, year, month, day] = parts.map(Number)
      const calendar = new Date(Date.UTC(year, month - 1, day))
      if (calendar.getUTCFullYear() !== year || calendar.getUTCMonth() !== month - 1 || calendar.getUTCDate() !== day) return undefined
    }
    return descriptor?.valueType === "string" ? text : parsed
  }
  if (descriptor?.type === "select" || descriptor?.type === "radio") return coerceOptionValue(text, descriptor)
  if (descriptor?.valueType === "string") return text
  if (descriptor?.valueType === "number" || descriptor?.type === "number") {
    const parsed = Number(text)
    return text && Number.isFinite(parsed) && (descriptor?.min == null || parsed >= descriptor.min) && (descriptor?.max == null || parsed <= descriptor.max) && (!descriptor?.integer || Number.isInteger(parsed)) ? parsed : undefined
  }
  return text
}

/** 选项字段保留原选项类型（ID 为 number 时不能写成字符串），匹配 value 或 label。 */
function coerceOptionValue(value: string, descriptor: FormFieldDescriptor): unknown {
  const normalized = value.trim()
  const matches = descriptor.options?.filter(option =>
    String(option.value) === normalized || String(option.label) === normalized
  ) ?? []
  if (new Set(matches.map(option => String(option.value))).size > 1) return undefined
  if (matches.length) return matches[0].value
  if ((descriptor.options?.length || descriptor.type === 'select' || descriptor.type === 'radio') && !descriptor.allowCustom) return undefined
  return normalized
}

function cloneValue<T>(value: T): T {
  if (value instanceof Date) return new Date(value.getTime()) as T
  if (Array.isArray(value)) return value.map(item => cloneValue(item)) as T
  if (value && typeof value === 'object') {
    const result: Record<string, unknown> = {}
    for (const [key, item] of Object.entries(value as Record<string, unknown>)) result[key] = cloneValue(item)
    return result as T
  }
  return value
}

function valuesEqual(left: unknown, right: unknown): boolean {
  if (Object.is(left, right)) return true
  if (left instanceof Date && right instanceof Date) return left.getTime() === right.getTime()
  if (Array.isArray(left) && Array.isArray(right)) {
    return left.length === right.length && left.every((item, index) => valuesEqual(item, right[index]))
  }
  if (left && right && typeof left === 'object' && typeof right === 'object') {
    const leftKeys = Object.keys(left as Record<string, unknown>)
    const rightKeys = Object.keys(right as Record<string, unknown>)
    return leftKeys.length === rightKeys.length
      && leftKeys.every(key => Object.prototype.hasOwnProperty.call(right, key)
        && valuesEqual((left as Record<string, unknown>)[key], (right as Record<string, unknown>)[key]))
  }
  return false
}
