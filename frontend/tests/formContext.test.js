import test from 'node:test'
import assert from 'node:assert/strict'
import { reactive, ref } from 'vue'
import { register, unregister, captureValues, applyAssignments, undoLast, currentForm, undoDepth, fieldRevision, setFormSession, getFormFields, getFormValidation, validateForm, beginFormOperation, reportFormOutcome, formBusinessReceipt } from '../src/agent/formContext.ts'

test.beforeEach(() => unregister(currentForm.value?.formId || 'none'))

test('数字绑定写 number，但赋值记录保留协议 string', () => {
  const model = reactive({ salaryMin: '' })
  register({ formId: 'salary', title: '薪资', fields: [
    { prop: 'salaryMin', label: '最低薪资', type: 'number', valueType: 'number' }
  ], getModel: () => model })
  const result = applyAssignments('salary', [{ prop: 'salaryMin', value: '3', confidence: 1 }])
  assert.equal(model.salaryMin, 3)
  assert.equal(result.applied[0].value, '3')
})

for (const value of ['', '   ', 'not-a-number', 'Infinity']) {
  test(`非法数字不得覆盖薪资：${JSON.stringify(value)}`, () => {
    const model = reactive({ salaryMin: 3 })
    register({ formId: 'salary-invalid', title: '薪资', fields: [
      { prop: 'salaryMin', label: '最低薪资', type: 'number', valueType: 'number' }
    ], getModel: () => model })
    const result = applyAssignments('salary-invalid', [{ prop: 'salaryMin', value, confidence: 1 }])
    assert.equal(model.salaryMin, 3)
    assert.equal(result.applied.length, 0)
    assert.equal(result.skipped.length, 1)
    assert.equal(undoDepth.value, 0)
  })
}

for (const valueType of ['date', 'string']) {
  for (const value of ['not-a-date', '2026-02-30']) {
    test(`非法日期不得写入 ${valueType}：${value}`, () => {
      const model = reactive({ date: '' })
      register({ formId: 'invalid-date', title: '日期', fields: [
        { prop: 'date', label: '日期', type: 'date', valueType }
      ], getModel: () => model })
      const result = applyAssignments('invalid-date', [{ prop: 'date', value, confidence: 1 }])
      assert.equal(model.date, '')
      assert.equal(result.skipped.length, 1)
      assert.equal(undoDepth.value, 0)
    })
  }
}

test('model 中存在但未声明的系统字段不可写入', () => {
  const model = reactive({ detailAddress: '', id: null, status: 'active' })
  register({ formId: 'undeclared', title: '岗位', fields: [
    { prop: 'detailAddress', label: '详细地址', type: 'input' }
  ], getModel: () => model })
  const result = applyAssignments('undeclared', [
    { prop: 'id', value: '999', confidence: 1 },
    { prop: 'status', value: 'inactive', confidence: 1 }
  ])
  assert.equal(model.id, null)
  assert.equal(model.status, 'active')
  assert.equal(result.applied.length, 0)
  assert.equal(undoDepth.value, 0)
})

test('数字 radio 标签映射优先于 number 转换，非法选项不写入', () => {
  const model = reactive({ providesAccommodation: 0, requiredMajors: ['软件工程'] })
  register({ formId: 'options', title: '选项', fields: [
    { prop: 'providesAccommodation', label: '住宿', type: 'radio', valueType: 'number', options: [{ label: '否', value: 0 }, { label: '是', value: 1 }] },
    { prop: 'requiredMajors', label: '专业', type: 'select', valueType: 'array', options: [{ label: '软件工程', value: '软件工程' }] }
  ], getModel: () => model })
  applyAssignments('options', [{ prop: 'providesAccommodation', value: '是', confidence: 1 }])
  assert.equal(model.providesAccommodation, 1)
  const result = applyAssignments('options', [
    { prop: 'providesAccommodation', value: '7', confidence: 1 },
    { prop: 'requiredMajors', value: '软件工程、不存在专业', confidence: 1 }
  ])
  assert.equal(model.providesAccommodation, 1)
  assert.deepEqual(model.requiredMajors, ['软件工程'])
  assert.equal(result.skipped.length, 2)
})

test('字段赋值、连续修改与逐批撤销只影响目标字段', () => {
  const model = reactive({ title: '手动标题', count: 1, remark: '手写备注' })
  register({
    formId: 'test:form', title: '测试表单',
    fields: [
      { prop: 'title', label: '标题', type: 'input' },
      { prop: 'count', label: '人数', type: 'number' }
    ],
    getModel: () => model
  })
  assert.equal(applyAssignments('test:form', [
    { prop: 'title', value: 'AI标题', confidence: 1 },
    { prop: 'count', value: '3', confidence: 1 }
  ])?.applied.length, 2)
  assert.equal(model.title, 'AI标题')
  assert.equal(model.count, 3)
  assert.equal(model.remark, '手写备注')

  applyAssignments('test:form', [{ prop: 'count', value: '5', confidence: 1 }])
  assert.equal(model.count, 5)
  assert.deepEqual(undoLast(), { restored: ['count'], preserved: [] })
  assert.equal(model.count, 3)
  assert.equal(model.title, 'AI标题')
})

test('表单切换和未知字段拒绝写入', () => {
  const first = reactive({ title: '' })
  const second = reactive({ title: '' })
  register({ formId: 'first', title: '第一表单', fields: [{ prop: 'title', label: '标题', type: 'input' }], getModel: () => first })
  register({ formId: 'second', title: '第二表单', fields: [{ prop: 'title', label: '标题', type: 'input' }], getModel: () => second })
  assert.equal(applyAssignments('first', [{ prop: 'title', value: '错误', confidence: 1 }]), null)
  assert.deepEqual(applyAssignments('second', [{ prop: 'unknown', value: '错误', confidence: 1 }]), { applied: [], skipped: [] })
  assert.equal(second.title, '')
})

test('撤销栈最多保留十批', () => {
  const model = reactive({ count: 0 })
  register({ formId: 'stack', title: '栈测试', fields: [{ prop: 'count', label: '人数', type: 'number' }], getModel: () => model })
  for (let i = 1; i <= 12; i++) applyAssignments('stack', [{ prop: 'count', value: String(i), confidence: 1 }])
  assert.equal(undoDepth.value, 10)
})

test('请求期间手动改过的字段不被旧响应覆盖', () => {
  const model = reactive({ title: '', count: 1 })
  register({ formId: 'request', title: '请求冲突', fields: [
    { prop: 'title', label: '标题', type: 'input' },
    { prop: 'count', label: '人数', type: 'number' }
  ], getModel: () => model })
  const baseline = captureValues('request')
  model.count = 6
  const result = applyAssignments('request', [
    { prop: 'title', value: 'AI标题', confidence: 1 },
    { prop: 'count', value: '5', confidence: 1 }
  ], baseline)
  assert.deepEqual(result?.applied.map(item => item.prop), ['title'])
  assert.deepEqual(result?.skipped.map(item => item.prop), ['count'])
  assert.match(result?.skipped[0].reason || '', /手动修改/)
  assert.equal(model.title, 'AI标题')
  assert.equal(model.count, 6)
})

test('多选字段按数组写入并在撤销时整批回滚', () => {
  const model = reactive({ majors: ['软件工程'], title: '' })
  register({ formId: 'multi', title: '多选测试', fields: [
    { prop: 'majors', label: '招聘专业', type: 'select', valueType: 'array', options: [{ label: '软件工程', value: '软件工程' }, { label: '计算机科学与技术', value: '计算机科学与技术' }] },
    { prop: 'title', label: '标题', type: 'input' }
  ], getModel: () => model })
  assert.equal(applyAssignments('multi', [{ prop: 'majors', value: '计算机科学与技术', confidence: 1 }])?.applied.length, 1)
  assert.deepEqual(model.majors, ['计算机科学与技术'])
  assert.deepEqual(undoLast(), { restored: ['majors'], preserved: [] })
  assert.deepEqual(model.majors, ['软件工程'])
})

test('多选字段的多值字符串按分隔符拆分为数组', () => {
  const model = reactive({ majors: [] })
  register({ formId: 'multi-split', title: '多值拆分', fields: [
    { prop: 'majors', label: '招聘专业', type: 'select', valueType: 'array', options: [{ label: '软件工程', value: '软件工程' }, { label: '计算机科学与技术', value: '计算机科学与技术' }] }
  ], getModel: () => model })
  applyAssignments('multi-split', [{ prop: 'majors', value: '软件工程、计算机科学与技术', confidence: 1 }])
  assert.deepEqual(model.majors, ['软件工程', '计算机科学与技术'])
})

test('撤销时保留 AI 填写后再次手改的字段', () => {
  const model = reactive({ title: '', count: 1 })
  register({ formId: 'undo-manual', title: '撤销保留', fields: [
    { prop: 'title', label: '标题', type: 'input' },
    { prop: 'count', label: '人数', type: 'number' }
  ], getModel: () => model })
  const baseline = captureValues('undo-manual')
  applyAssignments('undo-manual', [
    { prop: 'title', value: 'AI标题', confidence: 1 },
    { prop: 'count', value: '3', confidence: 1 }
  ], baseline)
  model.count = 6
  assert.deepEqual(undoLast(), { restored: ['title'], preserved: ['count'] })
  assert.equal(model.title, '')
  assert.equal(model.count, 6)
})

test('选项字段按选项定义保留数字类型', () => {
  const model = reactive({ classId: null, teacherIds: [] })
  register({ formId: 'numeric-options', title: '数字选项', fields: [
    { prop: 'classId', label: '班级', type: 'select', options: [{ label: '23信管1班', value: 7 }] },
    { prop: 'teacherIds', label: '走访教师', type: 'select', valueType: 'array', options: [{ label: '辅导员1', value: 9 }, { label: '教师2', value: 10 }] }
  ], getModel: () => model })
  applyAssignments('numeric-options', [
    { prop: 'classId', value: '23信管1班', confidence: 1 },
    { prop: 'teacherIds', value: '辅导员1、教师2', confidence: 1 }
  ])
  assert.equal(model.classId, 7)
  assert.deepEqual(model.teacherIds, [9, 10])
})

test('date 字段 valueType=string 时保留日期字符串', () => {
  const model = reactive({ startDate: '' })
  register({ formId: 'date-string', title: '日期字符串', fields: [
    { prop: 'startDate', label: '开始日期', type: 'date', valueType: 'string' }
  ], getModel: () => model })
  applyAssignments('date-string', [{ prop: 'startDate', value: '2026-09-17', confidence: 1 }])
  assert.equal(model.startDate, '2026-09-17')
})

test('同一 formId 关闭重开后，旧实例的迟到响应被拒绝（相同初值场景）', () => {
  // 复现 FILL-01：记录 A 发起请求后关闭；同 formId 打开初值相同的 B；A 的旧响应不得写入 B
  const modelA = reactive({ title: '', count: null })
  register({ formId: 'company:position', title: '岗位表单', fields: [
    { prop: 'title', label: '标题', type: 'input' },
    { prop: 'count', label: '人数', type: 'number' }
  ], getModel: () => modelA })
  const instanceA = currentForm.value.instanceId
  const baselineA = captureValues('company:position')

  // A 关闭（unregister），B 以同 formId、同初值重开
  unregister('company:position')
  const modelB = reactive({ title: '', count: null })
  register({ formId: 'company:position', title: '岗位表单', fields: [
    { prop: 'title', label: '标题', type: 'input' },
    { prop: 'count', label: '人数', type: 'number' }
  ], getModel: () => modelB })

  // A 的迟到响应带 A 的实例代际：必须被拒绝，B 的 model 不受影响
  assert.equal(applyAssignments('company:position', [
    { prop: 'title', value: 'A的旧标题', confidence: 1 },
    { prop: 'count', value: '5', confidence: 1 }
  ], baselineA, instanceA), null)
  assert.equal(modelB.title, '')
  assert.equal(modelB.count, null)

  // B 自己发起的请求（B 的代际）正常写入
  const result = applyAssignments('company:position', [
    { prop: 'title', value: 'B的新标题', confidence: 1 }
  ], captureValues('company:position'), currentForm.value.instanceId)
  assert.equal(result?.applied.length, 1)
  assert.equal(modelB.title, 'B的新标题')
})

test('复用注册对象重开时，请求持有的旧实例代际不随新注册变化', () => {
  let model = reactive({ title: '' })
  const registration = {
    formId: 'reused', title: '复用注册',
    fields: [{ prop: 'title', label: '标题', type: 'input' }],
    getModel: () => model
  }
  register(registration)
  const requestForm = currentForm.value
  const baseline = captureValues(requestForm.formId)
  unregister('reused')
  model = reactive({ title: '' })
  register(registration)
  assert.equal(applyAssignments(requestForm.formId, [
    { prop: 'title', value: '旧响应', confidence: 1 }
  ], baseline, requestForm.instanceId), null)
  assert.equal(model.title, '')
})

test('同 formId 换代但未注销时，旧代际响应与撤销同样被拒绝', () => {
  // 覆盖视图遗漏 unregister 的路径：register 本身换代即可隔离
  const modelA = reactive({ title: '' })
  register({ formId: 'reopen', title: '重开测试', fields: [{ prop: 'title', label: '标题', type: 'input' }], getModel: () => modelA })
  const instanceA = currentForm.value.instanceId
  applyAssignments('reopen', [{ prop: 'title', value: 'A批填写', confidence: 1 }], undefined, instanceA)
  assert.equal(modelA.title, 'A批填写')

  // 同 formId 重开（未显式 unregister）：换代
  const modelB = reactive({ title: '' })
  register({ formId: 'reopen', title: '重开测试', fields: [{ prop: 'title', label: '标题', type: 'input' }], getModel: () => modelB })

  // 旧代际迟到响应被拒
  assert.equal(applyAssignments('reopen', [{ prop: 'title', value: '旧响应', confidence: 1 }], undefined, instanceA), null)
  assert.equal(modelB.title, '')
  // A 批次的撤销不能作用于 B 实例（undoLast 按代际校验）
  assert.equal(undoLast(), null)
  assert.equal(modelB.title, '')
})

test('P1 同一响应式对象原地重置重开，同初值旧响应仍被拒（生产 positionForm 复用形态）', () => {
  const model = reactive({ title: '', count: null })
  const fields = [{ prop: 'title', label: '标题', type: 'input' }, { prop: 'count', label: '人数', type: 'number' }]
  register({ formId: 'company:position', title: '岗位表单', fields, getModel: () => model })
  const instanceA = currentForm.value.instanceId
  const baselineA = captureValues('company:position')
  // B 原地重置同一对象并重注册（初值与 A 完全相同）
  model.title = ''; model.count = null
  register({ formId: 'company:position', title: '岗位表单', fields, getModel: () => model })
  assert.notEqual(currentForm.value.instanceId, instanceA)
  assert.equal(applyAssignments('company:position', [{ prop: 'title', value: 'A旧标题', confidence: 1 }], baselineA, instanceA), null)
  assert.equal(model.title, '')
  assert.equal(model.count, null)
})

test('P1 连续两批请求，只有新实例代际的响应能写入', () => {
  const modelA = reactive({ title: '' })
  register({ formId: 'cont', title: '连续请求', fields: [{ prop: 'title', label: '标题', type: 'input' }], getModel: () => modelA })
  const instA = currentForm.value.instanceId
  const baseA = captureValues('cont')
  const modelB = reactive({ title: '' })
  register({ formId: 'cont', title: '连续请求', fields: [{ prop: 'title', label: '标题', type: 'input' }], getModel: () => modelB })
  const instB = currentForm.value.instanceId
  const baseB = captureValues('cont')
  assert.equal(applyAssignments('cont', [{ prop: 'title', value: 'A迟到', confidence: 1 }], baseA, instA), null)
  const ok = applyAssignments('cont', [{ prop: 'title', value: 'B最新', confidence: 1 }], baseB, instB)
  assert.equal(ok?.applied.length, 1)
  assert.equal(modelB.title, 'B最新')
  assert.equal(modelA.title, '')
})

test('手改后又改回 AI 原值，撤销仍受版本保护不误回滚', () => {
  const model = reactive({ title: '' })
  register({ formId: 'revert-eq', title: '回改保护', fields: [{ prop: 'title', label: '标题', type: 'input' }], getModel: () => model })
  const applied = applyAssignments('revert-eq', [{ prop: 'title', value: 'AI值', confidence: 1 }])
  assert.equal(applied.applied.length, 1)
  assert.equal(model.title, 'AI值')
  // 用户先改成别的值，再改回与 AI 写入完全相同的值：仅比较值会误判为未修改。
  model.title = '用户值'
  model.title = 'AI值'
  const result = undoLast()
  assert.equal(model.title, 'AI值')
  assert.deepEqual(result.restored, [])
  assert.deepEqual(result.preserved, ['title'])
})

test('未手改的字段仍可正常撤销（版本保护不放宽正常回滚）', () => {
  const model = reactive({ title: '初值' })
  register({ formId: 'revert-clean', title: '正常撤销', fields: [{ prop: 'title', label: '标题', type: 'input' }], getModel: () => model })
  applyAssignments('revert-clean', [{ prop: 'title', value: 'AI值', confidence: 1 }])
  const result = undoLast()
  assert.equal(model.title, '初值')
  assert.deepEqual(result.restored, ['title'])
})

test('填写账本记录批次标识、字段前后值与修改版本', () => {
  const model = reactive({ title: '' })
  register({ formId: 'ledger', title: '账本', fields: [{ prop: 'title', label: '标题', type: 'input' }], getModel: () => model })
  applyAssignments('ledger', [{ prop: 'title', value: 'AI值', confidence: 1 }])
  assert.equal(fieldRevision('title', 'ledger'), 0)
  model.title = '用户值'
  assert.equal(fieldRevision('title', 'ledger'), 1)
})

test('请求期间手改后改回基线，迟到响应仍不能覆盖', () => {
  const model = reactive({ title: '原值' })
  register({ formId: 'pending-revert', title: '表单', fields: [{ prop: 'title', label: '标题', type: 'input' }], getModel: () => model })
  const baseline = captureValues('pending-revert')
  model.title = '手改'; model.title = '原值'
  const result = applyAssignments('pending-revert', [{ prop: 'title', value: '迟到', confidence: 1 }], baseline)
  assert.equal(model.title, '原值')
  assert.equal(result.skipped.length, 1)
})

test('model 对象替换自动换代，旧请求拒绝，新对象手改受撤销保护', () => {
  const model = ref({ title: 'A' })
  register({ formId: 'replace', title: '表单', fields: [{ prop: 'title', label: '标题', type: 'input' }], getModel: () => model.value })
  const instance = currentForm.value.instanceId
  const baseline = captureValues('replace')
  model.value = { title: 'B' }
  assert.notEqual(currentForm.value.instanceId, instance)
  assert.equal(applyAssignments('replace', [{ prop: 'title', value: '迟到', confidence: 1 }], baseline, instance), null)
  applyAssignments('replace', [{ prop: 'title', value: 'AI', confidence: 1 }])
  model.value.title = '手改'
  assert.deepEqual(undoLast().preserved, ['title'])
  assert.equal(model.value.title, '手改')
})

test('同一字段重复赋值整字段拒绝，不产生错误撤销快照', () => {
  const model = reactive({ title: '原值' })
  register({ formId: 'duplicate', title: '表单', fields: [{ prop: 'title', label: '标题', type: 'input' }], getModel: () => model })
  const result = applyAssignments('duplicate', [{ prop: 'title', value: 'A', confidence: 1 }, { prop: 'title', value: 'B', confidence: 1 }])
  assert.equal(result.applied.length, 0)
  assert.equal(result.skipped.length, 1)
  assert.equal(model.title, '原值')
  assert.equal(undoLast(), null)
})

test('按批次撤销保留后续 AI 写入和手工修改，返回前后差异', () => {
  const model = reactive({ title: '原值', count: 1 })
  register({ formId: 'batches', title: '表单', fields: [{ prop: 'title', label: '标题', type: 'input' }, { prop: 'count', label: '人数', type: 'number' }], getModel: () => model })
  const first = applyAssignments('batches', [{ prop: 'title', value: 'A', confidence: 1 }, { prop: 'count', value: '5', confidence: 1 }])
  const second = applyAssignments('batches', [{ prop: 'title', value: 'B', confidence: 1 }])
  assert.deepEqual(first.beforeValues, { title: '原值', count: 1 })
  assert.deepEqual(first.afterValues, { title: 'A', count: 5 })
  const result = undoLast(first.fillBatchId)
  assert.equal(result.fillBatchId, first.fillBatchId)
  assert.deepEqual(result, { restored: ['count'], preserved: ['title'] })
  assert.deepEqual(model, { title: 'B', count: 1 })
  assert.deepEqual(undoLast(second.fillBatchId).restored, ['title'])
})

test('切会话使旧响应和撤销失效，但不改变已填写值', () => {
  const model = reactive({ title: '' })
  setFormSession('owner:A')
  register({ formId: 'session', title: '表单', fields: [{ prop: 'title', label: '标题', type: 'input' }], getModel: () => model })
  const first = applyAssignments('session', [{ prop: 'title', value: 'A', confidence: 1 }])
  const baseline = captureValues('session')
  setFormSession('owner:B')
  assert.equal(applyAssignments('session', [{ prop: 'title', value: '迟到', confidence: 1 }], baseline), null)
  assert.equal(undoLast(first.fillBatchId), null)
  assert.equal(model.title, 'A')
})

test('动态选项使用最新值，只有明确允许创建的字段接受新选项', () => {
  const model = reactive({ choice: '', custom: '' })
  const options = ref([])
  const fields = () => [{ prop: 'choice', label: '选项', type: 'select', options: options.value }, { prop: 'custom', label: '自定义', type: 'select', options: [], allowCustom: true }]
  register({ formId: 'dynamic', title: '表单', fields: fields(), getFields: fields, getModel: () => model })
  assert.equal(applyAssignments('dynamic', [{ prop: 'choice', value: '未知', confidence: 1 }]).applied.length, 0)
  options.value = [{ label: '已加载', value: 9 }]
  assert.equal(getFormFields()[0].options[0].value, 9)
  applyAssignments('dynamic', [{ prop: 'choice', value: '已加载', confidence: 1 }, { prop: 'custom', value: '新值', confidence: 1 }])
  assert.deepEqual(model, { choice: 9, custom: '新值' })
})

test('填写回执包含必填附件和业务校验摘要，不伪造附件', () => {
  const model = reactive({ title: '', attachment: '', count: 1 })
  register({ formId: 'validation', title: '表单', fields: [{ prop: 'title', label: '标题', type: 'input', required: true }, { prop: 'attachment', label: '真实附件', type: 'upload', required: true }, { prop: 'count', label: '人数', type: 'number', min: 1, max: 100, integer: true }], getModel: () => model, getValidationSummary: () => ({ missingFields: [], validationErrors: ['原表单跨字段校验提示'] }) })
  const result = applyAssignments('validation', [{ prop: 'title', value: '已填', confidence: 1 }, { prop: 'attachment', value: '伪造URL', confidence: 1 }, { prop: 'count', value: '-2', confidence: 1 }])
  assert.deepEqual(result.missingFields, ['真实附件'])
  assert.deepEqual(result.validationErrors, ['原表单跨字段校验提示'])
  assert.equal(model.attachment, '')
  assert.equal(model.count, 1)
})

test('日期时间必须包含有效日期和时间，不能被归一成仅日期', () => {
  const model = reactive({ time: '' })
  register({ formId: 'datetime', title: '表单', fields: [{ prop: 'time', label: '面试时间', type: 'datetime', valueType: 'string' }], getModel: () => model })
  for (const value of ['2026-09-25', '2026-02-30 12:30:00', '2026-09-25 25:00:00']) assert.equal(applyAssignments('datetime', [{ prop: 'time', value, confidence: 1 }]).applied.length, 0)
  applyAssignments('datetime', [{ prop: 'time', value: '2026-09-25 14:30:00', confidence: 1 }])
  assert.equal(model.time, '2026-09-25 14:30:00')
})

test('真实保存回执绑定表单实例，使本地撤销失效且不修改表单', () => {
  const model = reactive({ title: '' })
  register({ formId: 'receipt', title: '表单', fields: [{ prop: 'title', label: '标题', type: 'input' }], getModel: () => model })
  const instance = currentForm.value.instanceId
  applyAssignments('receipt', [{ prop: 'title', value: '已填写', confidence: 1 }])
  assert.equal(reportFormOutcome('receipt', { status: 'published', label: '已发布' }, instance - 1), false)
  assert.equal(reportFormOutcome('receipt', { status: 'published', label: '已发布' }, instance), true)
  assert.equal(formBusinessReceipt.value.status, 'published')
  assert.equal(undoLast(), null)
  assert.equal(model.title, '已填写')
})

test('复核：稳定字段代理支持填写撤销并保护手改回原值', () => {
  const remark = ref('原值')
  const proxy = { get remark() { return remark.value }, set remark(value) { remark.value = value } }
  register({ formId: 'proxy-review', title: '补充说明', fields: [{ prop: 'remark', label: '说明', type: 'textarea' }], getModel: () => proxy })
  const instance = currentForm.value.instanceId
  const first = applyAssignments('proxy-review', [{ prop: 'remark', value: 'AI', confidence: 1 }])
  assert.equal(currentForm.value.instanceId, instance)
  assert.deepEqual(undoLast(first.fillBatchId).restored, ['remark'])
  assert.equal(remark.value, '原值')
  const baseline = captureValues('proxy-review')
  remark.value = '人改'; remark.value = '原值'
  assert.equal(applyAssignments('proxy-review', [{ prop: 'remark', value: '迟到', confidence: 1 }], baseline).applied.length, 0)
})

test('复核：同批父字段改变下拉选项时不写入旧子选项', () => {
  const model = reactive({ department: 'A', major: '' })
  const fields = () => [{ prop: 'department', label: '院系', type: 'select', options: [{ label: 'A', value: 'A' }, { label: 'B', value: 'B' }] },
    { prop: 'major', label: '专业', type: 'select', options: [{ label: model.department + '专业', value: model.department + '-major' }] }]
  register({ formId: 'cascade-review', title: '联动下拉', fields: fields(), getFields: fields, getModel: () => model })
  const result = applyAssignments('cascade-review', [{ prop: 'department', value: 'B', confidence: 1 }, { prop: 'major', value: 'A-major', confidence: 1 }])
  assert.deepEqual(model, { department: 'B', major: '' })
  assert.deepEqual(result.applied.map(item => item.prop), ['department'])
  assert.match(result.skipped[0].reason, /选项已变化/)
})

test('复核：会话切走又切回仍拒绝原代际迟到响应', () => {
  const model = reactive({ title: '' })
  setFormSession('review:A')
  register({ formId: 'session-review', title: '表单', fields: [{ prop: 'title', label: '标题', type: 'input' }], getModel: () => model })
  const baseline = captureValues('session-review')
  setFormSession('review:B'); setFormSession('review:A')
  assert.equal(applyAssignments('session-review', [{ prop: 'title', value: '迟到', confidence: 1 }], baseline), null)
})

test('复核：保存回执只记录原请求已填字段，保留等待期间新批次的撤销', () => {
  const model = reactive({ title: '', manual: '不要上报的手工内容' })
  register({ formId: 'save-review', title: '表单', fields: [{ prop: 'title', label: '标题', type: 'input' }, { prop: 'manual', label: '人工字段', type: 'input' }], getModel: () => model })
  const instance = currentForm.value.instanceId
  const first = applyAssignments('save-review', [{ prop: 'title', value: '保存请求的值', confidence: 1 }])
  const savedValues = captureValues('save-review')
  const second = applyAssignments('save-review', [{ prop: 'title', value: '后续未保存修改', confidence: 1 }])
  reportFormOutcome('save-review', { status: 'saved' }, instance, savedValues)
  assert.deepEqual(formBusinessReceipt.value.savedValues, { title: '保存请求的值' })
  assert.equal(undoLast(first.fillBatchId), null)
  assert.deepEqual(undoLast(second.fillBatchId).restored, ['title'])
  assert.equal(model.title, '保存请求的值')
})

test('复核：动态选项出现重名时不选择第一项，空白必填仍为缺项', () => {
  const model = reactive({ choice: '', title: '   ' })
  register({ formId: 'ambiguous-review', title: '表单', fields: [
    { prop: 'choice', label: '专业', type: 'select', options: [{ label: '软件', value: 1 }, { label: '软件', value: 2 }] },
    { prop: 'title', label: '标题', type: 'input', required: true }
  ], getModel: () => model })
  assert.equal(applyAssignments('ambiguous-review', [{ prop: 'choice', value: '软件', confidence: 1 }]).applied.length, 0)
  assert.deepEqual(getFormValidation().missingFields, ['标题'])
})

test('native contract: async validators and business checks share a summary without submitting', async () => {
  const model = reactive({ phone: '123', title: '' })
  let submitCalls = 0
  const native = { fields: [{ validateState: 'error', validateMessage: '手机号码格式错误' }],
    validate: async callback => { await Promise.resolve(); callback(false, { phone: [{ message: '手机号码格式错误' }] }); return false },
    submit: () => { submitCalls++ } }
  register({ formId: 'native-contract', title: '联系资料', fields: [{ prop: 'phone', label: '手机', type: 'input' }, { prop: 'title', label: '标题', type: 'input', required: true }], getModel: () => model,
    getFormEl: () => native, getValidationSummary: async () => ({ missingFields: ['真实附件'], validationErrors: ['当前未开放提交'] }) })
  assert.deepEqual(getFormValidation().validationErrors, ['手机号码格式错误'])
  assert.deepEqual(await validateForm(), { missingFields: ['标题', '真实附件'], validationErrors: ['当前未开放提交', '手机号码格式错误'] })
  assert.equal(submitCalls, 0)
})

test('native contract: rejected validation promises expose field messages', async () => {
  register({ formId: 'native-reject', title: '联系资料', fields: [{ prop: 'phone', label: '手机', type: 'input' }], getModel: () => ({ phone: '123' }),
    getFormEl: () => ({ validate: async () => { throw { phone: [{ message: '请输入有效手机号' }] } } }) })
  assert.deepEqual((await validateForm()).validationErrors, ['请输入有效手机号'])
})

test('native contract: an object replacement makes a late validation result stale', async () => {
  const model = reactive({ phone: '123' }), object = ref('A')
  let finish
  register({ formId: 'native-stale', title: '资料', fields: [{ prop: 'phone', label: '手机', type: 'input' }], getModel: () => model,
    getObject: () => ({ type: 'record', id: object.value }), getFormEl: () => ({ validate: callback => { finish = callback } }) })
  const pending = validateForm()
  await Promise.resolve()
  object.value = 'B'
  finish(false, { phone: [{ message: 'A记录错误，不能给B' }] })
  const result = await pending
  assert.deepEqual(result.validationErrors, ['校验期间表单已改变，请以当前原表单提示为准。'])
})

test('native contract: timeouts end waiting without inventing success', async context => {
  context.mock.timers.enable({ apis: ['setTimeout'] })
  const model = reactive({ title: '标题' })
  register({ formId: 'native-timeout', title: '资料', fields: [{ prop: 'title', label: '标题', type: 'input' }], getModel: () => model,
    getFormEl: () => ({ validate: () => new Promise(() => {}) }) })
  const pending = validateForm()
  await Promise.resolve()
  context.mock.timers.tick(10001)
  assert.match((await pending).validationErrors[0], /校验超时/)
})

test('receipt contract: request failure leaves its AI batch and original receipt untouched', async () => {
  const model = reactive({ title: '' })
  register({ formId: 'operation-failure', title: '资料', fields: [{ prop: 'title', label: '标题', type: 'input' }], getModel: () => model })
  const batch = applyAssignments('operation-failure', [{ prop: 'title', value: '待保存', confidence: 1 }])
  const previous = formBusinessReceipt.value
  const operation = beginFormOperation('operation-failure')
  try { await Promise.reject(new Error('网络故障')); operation.complete({ status: 'saved' }) } catch {}
  assert.equal(formBusinessReceipt.value, previous)
  assert.deepEqual(undoLast(batch.fillBatchId).restored, ['title'])
})

test('receipt contract: late success after object or account replacement cannot acknowledge another form', () => {
  const model = reactive({ title: 'A' }), object = ref('A')
  register({ formId: 'operation-stale', title: '资料', fields: [{ prop: 'title', label: '标题', type: 'input' }], getModel: () => model, getObject: () => ({ type: 'record', id: object.value }) })
  const oldObject = beginFormOperation('operation-stale')
  object.value = 'B'
  assert.equal(oldObject.complete({ status: 'saved' }), false)
  const oldAccount = beginFormOperation('operation-stale')
  setFormSession('different-account')
  assert.equal(oldAccount.complete({ status: 'saved' }), false)
})

test('receipt contract: partial submission cannot mark unsent fields saved, repeat completion is inert', () => {
  const model = reactive({ category: '', description: '' })
  register({ formId: 'operation-partial', title: '规则', fields: [{ prop: 'category', label: '分类', type: 'input' }, { prop: 'description', label: '描述', type: 'input' }], getModel: () => model })
  const batch = applyAssignments('operation-partial', [{ prop: 'category', value: '质量', confidence: 1 }, { prop: 'description', value: '未发送的描述', confidence: 1 }])
  const operation = beginFormOperation('operation-partial', ['category'])
  assert.equal(operation.complete({ status: 'saved' }), true)
  assert.deepEqual(formBusinessReceipt.value.savedValues, { category: '质量' })
  assert.deepEqual(batch.afterValues, { category: '质量', description: '未发送的描述' })
  assert.deepEqual(batch.beforeValues, { category: '', description: '' })
  assert.equal(operation.complete({ status: 'published' }), false)
  assert.equal(undoDepth.value, 1)
  assert.deepEqual(undoLast(batch.fillBatchId).restored, ['description'])
  assert.equal(model.category, '质量')
})

test('receipt contract: proxy-normalized values and a later explicit publish remain verifiable', () => {
  const count = ref(2)
  const proxy = { get count() { return String(count.value) }, set count(value) { count.value = Number(value) } }
  register({ formId: 'operation-proxy', title: '人数', fields: [{ prop: 'count', label: '人数', type: 'number' }], getModel: () => proxy })
  const batch = applyAssignments('operation-proxy', [{ prop: 'count', value: '5', confidence: 1 }])
  assert.deepEqual(batch.afterValues, { count: '5' })
  beginFormOperation('operation-proxy').complete({ status: 'saved' })
  assert.equal(undoDepth.value, 0)
  assert.deepEqual(formBusinessReceipt.value.savedValues, { count: '5' })
  beginFormOperation('operation-proxy').complete({ status: 'published' })
  assert.equal(formBusinessReceipt.value.status, 'published')
  assert.deepEqual(formBusinessReceipt.value.savedValues, { count: '5' })
})

test('instruction contract: explicit count change preserves salary and all other fields including blanks', () => {
  const model = reactive({ recruitmentCount: 1, salaryMin: 3000, description: '' })
  register({ formId: 'instruction-count', title: '岗位', fields: [{ prop: 'recruitmentCount', label: '招聘人数', type: 'number' }, { prop: 'salaryMin', label: '最低薪资', type: 'number' }, { prop: 'description', label: '岗位描述', type: 'textarea' }], getModel: () => model })
  const result = applyAssignments('instruction-count', [
    { prop: 'recruitmentCount', value: '5', operation: 'SET', overwriteAllowed: true, confidence: 1 },
    { prop: 'salaryMin', value: '9000', operation: 'SET', overwriteAllowed: true, confidence: 1 },
    { prop: 'description', value: '擅自补充', operation: 'SET', overwriteAllowed: true, confidence: 1 }
  ], captureValues('instruction-count'), currentForm.value.instanceId, '人数改成五个，薪资不变，其他内容不要动')
  assert.deepEqual(model, { recruitmentCount: 5, salaryMin: 3000, description: '' })
  assert.equal(result.applied.length, 1)
  assert.equal(result.skipped.length, 2)
})

test('instruction contract: production rejects old or unauthorized overwrite even when model supplies a value', () => {
  const model = reactive({ count: 1, title: '原有', description: '' })
  register({ formId: 'instruction-overwrite', title: '岗位', fields: [{ prop: 'count', label: '人数', type: 'number' }, { prop: 'title', label: '标题', type: 'input' }, { prop: 'description', label: '描述', type: 'textarea' }], getModel: () => model })
  const result = applyAssignments('instruction-overwrite', [
    { prop: 'count', value: '5', confidence: 1 },
    { prop: 'title', value: '越权修改', operation: 'SET', overwriteAllowed: true, confidence: 1 },
    { prop: 'description', value: '填写新的描述', operation: 'SET', overwriteAllowed: false, confidence: 1 }
  ], null, undefined, '人数改成五个')
  assert.deepEqual(model, { count: 1, title: '原有', description: '填写新的描述' })
  assert.equal(result.skipped.length, 2)
})

test('instruction contract: natural option expressions may replace an existing value', () => {
  const model = reactive({ status: 'PUBLISHED', targetType: 'ALL' })
  register({ formId: 'instruction-select', title: '发布文档', fields: [
    { prop: 'status', label: '状态', type: 'select', options: [{ label: '草稿', value: 'DRAFT' }, { label: '发布', value: 'PUBLISHED' }] },
    { prop: 'targetType', label: '目标群体', type: 'select', options: [{ label: '全体师生', value: 'ALL' }, { label: '全体学生', value: 'STUDENT' }] }
  ], getModel: () => model })
  const result = applyAssignments('instruction-select', [
    { prop: 'status', value: 'DRAFT', operation: 'SET', overwriteAllowed: true, confidence: 1 },
    { prop: 'targetType', value: 'STUDENT', operation: 'SET', overwriteAllowed: true, confidence: 1 }
  ], null, undefined, '状态就用草稿，目标群体弄成全体学生')
  assert.deepEqual(model, { status: 'DRAFT', targetType: 'STUDENT' })
  assert.equal(result.applied.length, 2)
})
for (const phrase of ['不要把备注清空', '别清掉备注', '备注不要动', '备注不能清空', '保留备注', '不要清除备注']) {
  test(`instruction contract: negative clear remains untouched: ${phrase}`, () => {
    const model = reactive({ remark: '手工内容' })
    register({ formId: 'instruction-negative', title: '资料', fields: [{ prop: 'remark', label: '备注', type: 'textarea' }], getModel: () => model })
    const result = applyAssignments('instruction-negative', [{ prop: 'remark', value: '', operation: 'CLEAR', overwriteAllowed: true, confidence: 1 }], null, undefined, phrase)
    assert.equal(model.remark, '手工内容')
    assert.equal(result.applied.length, 0)
    assert.equal(undoDepth.value, 0)
  })
}

for (const phrase of ['不要把人数改成五个', '人数不能改成五个']) {
  test(`instruction contract: negative overwrite remains untouched: ${phrase}`, () => {
    const model = reactive({ recruitmentCount: 1 })
    register({ formId: 'instruction-negative-count', title: '岗位', fields: [{ prop: 'recruitmentCount', label: '招聘人数', type: 'number' }], getModel: () => model })
    assert.equal(applyAssignments('instruction-negative-count', [{ prop: 'recruitmentCount', value: '5', operation: 'SET', overwriteAllowed: true, confidence: 1 }], null, undefined, phrase).applied.length, 0)
    assert.equal(model.recruitmentCount, 1)
  })
}

test('instruction contract: explicit CLEAR uses native empty values and undo preserves later manual changes', () => {
  const model = reactive({ remark: '原文', count: 3, date: '2026-09-20', tags: ['A'] })
  const fields = [{ prop: 'remark', label: '备注[选填]', type: 'textarea' }, { prop: 'count', label: '人数', type: 'number' }, { prop: 'date', label: '日期', type: 'date', valueType: 'string' }, { prop: 'tags', label: '标签', type: 'select', valueType: 'array', options: [{ label: 'A', value: 'A' }] }]
  register({ formId: 'instruction-clear', title: '资料', fields, getModel: () => model })
  const result = applyAssignments('instruction-clear', fields.map(field => ({ prop: field.prop, value: '', operation: 'CLEAR', overwriteAllowed: true, confidence: 1 })), null, undefined, '清空备注[选填]，人数清空，日期留空，标签改为空')
  assert.deepEqual(model, { remark: '', count: null, date: null, tags: [] })
  model.remark = '用户之后手填'
  const undo = undoLast(result.fillBatchId)
  assert.deepEqual(undo.restored, ['count', 'date', 'tags'])
  assert.deepEqual(undo.preserved, ['remark'])
  assert.deepEqual(model, { remark: '用户之后手填', count: 3, date: '2026-09-20', tags: ['A'] })
})

test('instruction contract: ambiguous or unprompted clear and late clear never erase manual edits', () => {
  const model = reactive({ remark: '原文' })
  register({ formId: 'instruction-clear-stale', title: '资料', fields: [{ prop: 'remark', label: '备注', type: 'textarea' }], getModel: () => model })
  const assignment = [{ prop: 'remark', value: '', operation: 'CLEAR', overwriteAllowed: true, confidence: 1 }]
  assert.equal(applyAssignments('instruction-clear-stale', assignment, null, undefined, '清空这个').applied.length, 0)
  assert.equal(applyAssignments('instruction-clear-stale', assignment).applied.length, 0)
  const baseline = captureValues('instruction-clear-stale')
  model.remark = '用户之后手填'
  assert.equal(applyAssignments('instruction-clear-stale', assignment, baseline, undefined, '清空备注').applied.length, 0)
  assert.equal(model.remark, '用户之后手填')
})

test('instruction contract: CLEAR requires server authorization and SET empty cannot bypass clear intent', () => {
  const model = reactive({ remark: '原文' })
  register({ formId: 'instruction-clear-authority', title: '资料', fields: [{ prop: 'remark', label: '备注', type: 'textarea' }], getModel: () => model })
  for (const overwriteAllowed of [undefined, false]) {
    assert.equal(applyAssignments('instruction-clear-authority', [{ prop: 'remark', value: '', operation: 'CLEAR', overwriteAllowed, confidence: 1 }], null, undefined, '清空备注').applied.length, 0)
  }
  assert.equal(applyAssignments('instruction-clear-authority', [{ prop: 'remark', value: '', operation: 'SET', overwriteAllowed: true, confidence: 1 }], null, undefined, '备注改成').applied.length, 0)
  assert.equal(model.remark, '原文')
})
