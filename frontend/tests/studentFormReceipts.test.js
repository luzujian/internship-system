import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'

const ref = value => ({ value })
const noop = () => {}
const tick = () => new Promise(resolve => setImmediate(resolve))
const compiled = new Map()
function handler(file, name, scope) {
  const key = `${file}:${name}`
  if (!compiled.has(key)) {
    const { descriptor } = parse(readFileSync(new URL(`../src/views/student/${file}.vue`, import.meta.url), 'utf8'))
    const script = descriptor.scriptSetup?.content || descriptor.script?.content
    const ast = ts.createSourceFile(file, script, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
    const declaration = ast.statements.filter(ts.isVariableStatement).flatMap(s => [...s.declarationList.declarations]).find(d => d.name.getText(ast) === name)
    assert.ok(declaration, `Missing original handler ${key}`)
    compiled.set(key, ts.transpileModule(`const actual = ${declaration.initializer.getText(ast)}; actual`, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText)
  }
  return vm.runInNewContext(compiled.get(key), scope)
}

const application = validation => ({ applicationFormRef: validation(), submitting: ref(false), currentApplyingJob: ref({ id: 3, companyId: 5 }), applicationForm: ref({ positionName: '实习岗位', companyName: '公司', selfIntroduction: '认真负责' }), showApplicationDialog: ref(true), jobs: ref([]), selectedJob: ref(null), showJobDetailDialog: ref(true), eventBus: { emit: noop } })
const appraisal = () => ({ submissionWindow: ref({ open: true }), appraisalFiles: ref([{ id: 6, placementId: 3 }]), allCompanyRatingsSelected: ref(true), appraisalSubmitting: ref(false), requiredCompanyRatings: ref([{ companyId: 5 }]), companyRatings: ref({ 5: 4 }), internshipRecords: ref([{ placementId: 3 }]), selectedPlacementId: ref('3'), placementKey: row => String(row.placementId), fetchAppraisal: noop })
const onboarding = () => {
  const matchedCooperativeCompany = ref(null)
  const similarCooperativeCompanies = ref([])
  return {
    matchedCooperativeCompany,
    similarCooperativeCompanies,
    // 与 Home.vue 中 ensureSelfPracticeCompanyName 的门禁语义一致：仅相近命中且未精确匹配时阻止提交
    ensureSelfPracticeCompanyName: async () => !(similarCooperativeCompanies.value.length > 0 && !matchedCooperativeCompany.value),
    selfPracticeForm: ref({ company: '实习单位', positionName: '实习岗位', companyCity: '北京', hrManagerName: '王老师', hrManagerPhone: '13812345678', reason: '申请自主实习' }),
    delayForm: ref({ reason: '备考安排' }),
    onboardingSubmitting: ref(false),
    scheduleProgressRefresh: noop,
    showSelfPracticeDialog: ref(true),
    showDelayDialog: ref(true),
    selfPracticeApplication: ref(null),
    loadSelfPracticeApplication: noop,
    // home:delay 提交路径引用的既有标识符（此前测试作用域缺失导致 delay 用例既有失败）
    delayApplication: ref(null),
    canResubmitDelay: () => false,
    loadDelayApplication: noop
  }
}
const cases = [
  ['confirmation:remark', 'InternshipConfirmationForm', 'confirmSelectedOffer', () => ({ selectedOffer: ref({ offerId: 3 }), pendingConfirmation: ref(null), submitting: ref(false), confirmationRemark: ref('按约实习'), selectedOfferId: ref('3'), route: { query: {} }, clearQuery: noop, fetchOffers: noop, fetchHistory: noop, fetchCurrentPlacement: noop })],
  ['confirmation:recall', 'InternshipConfirmationForm', 'submitRecall', () => ({ recallReason: ref('信息有误'), recallTarget: ref({ id: 3 }), recalling: ref(false), showRecallDialog: ref(true), fetchOffers: noop, fetchHistory: noop, fetchCurrentPlacement: noop }), 'showRecallDialog'],
  ['home:self-practice', 'Home', 'submitOnboardingApplication', onboarding, 'showSelfPracticeDialog', ['selfPractice']],
  ['home:delay', 'Home', 'submitOnboardingApplication', onboarding, 'showDelayDialog', ['delay']],
  ['jobs:apply', 'Jobs', 'submitApplication', application, 'showApplicationDialog'],
  ['profile:apply', 'Profile', 'submitApplication', application, 'showApplicationDialog'],
  ['profile:bind', 'Profile', 'handleBindPhone', () => ({ bindPhoneForm: ref({ phone: '13812345678', email: 'student@example.test' }), EMAIL_REGEX: /^[^@]+@[^@]+\.[^@]+$/, showBindPhoneDialog: ref(true) }), 'showBindPhoneDialog'],
  ['reflection:log', 'Internships', 'submitLogForm', () => ({ reflectionActiveTab: ref('text'), logForm: ref({ content: '本周实习总结', taskId: 3 }), showLogDialog: ref(true), fetchReflections: noop, fetchPeriodStatus: noop }), 'showLogDialog']
]

function fixture(entry, mode = 'success', invalid = false) {
  const [formId, file, name, create, dialog, args = []] = entry
  const order = [], receipts = [], calls = [], messages = [], resets = [], pending = []
  const validation = () => ref({ validate: async () => { await tick(); if (invalid) throw new Error('validation failed'); return true } })
  const scope = create(validation)
  const write = async (...payload) => { order.push('write'); calls.push(payload); if (mode === 'throw') throw new Error('request failed'); return { code: mode === 'failure' ? 500 : 200, data: { message: 'failed' } } }
  const record = receipt => {
    if (dialog) assert.equal(scope[dialog].value, true, 'receipt must precede close')
    if (formId === 'confirmation:remark') assert.equal(scope.confirmationRemark.value, '按约实习', 'receipt must precede resetting submitted values')
    order.push('complete'); receipts.push(receipt)
  }
  Object.assign(scope, {
    console: { log: noop, warn: noop, error: noop },
    ElMessage: { success: message => messages.push(message), error: noop, warning: noop, info: message => messages.push(message) },
    ElMessageBox: { confirm: async () => {} }, authStore: { user: { id: 7, name: '学生' } },
    request: Object.assign(write, { post: write, delete: write }), studentJobApplicationService: { create: write },
    FormData: class { values = []; append(...pair) { this.values.push(pair) } },
    formContext: {
      currentForm: ref({ instanceId: 17 }),
      beginFormOperation(id, props) { assert.equal(id, formId); order.push('capture'); return { complete: receipt => record({ ...receipt, props }) } },
      captureValues(id) { assert.equal(id, formId); order.push('capture'); return { content: scope.logForm.value.content } },
      reportFormOutcome(id, receipt, instance, savedValues) { assert.equal(id, formId); assert.equal(instance, 17); record({ ...receipt, savedValues }) }
    },
    setTimeout(callback) { const task = tick().then(callback); pending.push(task); return 1 },
    resetForm() { resets.push(true) }
  })
  return { scope, order, receipts, calls, messages, resets, run: async (...override) => { await handler(file, name, scope)(...(override.length ? override : args)); await Promise.all(pending) } }
}

test('学生8个服务端保存表单与本页草稿覆盖登记清单', () => {
  const source = readFileSync(new URL('../src/agent/formCoverage.ts', import.meta.url), 'utf8')
  const ids = [...source.matchAll(/formId: '([^']+)',[^\n]+file: 'views\/student\//g)].map(match => match[1]).sort()
  assert.deepEqual([...cases.map(item => item[0]), 'registration:internship'].sort(), ids)
})

for (const entry of cases) test(`${entry[0]}: 明确成功才回执且先于关闭/重置，非200及异常保留输入`, async () => {
  const ok = fixture(entry)
  await ok.run()
  assert.equal(ok.receipts.length, 1)
  assert.deepEqual(ok.order, ['capture', 'write', 'complete'])
  for (const mode of ['failure', 'throw']) {
    const failed = fixture(entry, mode)
    await failed.run()
    assert.equal(failed.receipts.length, 0)
    if (entry[4]) assert.equal(failed.scope[entry[4]].value, true)
  }
})

test('岗位申请先等待原生异步校验，校验失败不创建请求', async () => {
  for (const id of ['jobs:apply', 'profile:apply']) {
    const ctx = fixture(cases.find(item => item[0] === id), 'success', true)
    await ctx.run()
    assert.deepEqual(ctx.order, [])
  }
})

test('绑定仅确认真实提交的phone/email子集，非法格式不发请求', async () => {
  for (const field of ['phone', 'email']) {
    const ctx = fixture(cases.find(item => item[0] === 'profile:bind'))
    ctx.scope.bindPhoneForm.value[field === 'phone' ? 'email' : 'phone'] = ''
    await ctx.run()
    assert.deepEqual([...ctx.receipts[0].props], [field])
    assert.deepEqual(Object.keys(ctx.calls[0][1]), [field])
  }
  const invalid = fixture(cases.find(item => item[0] === 'profile:bind'))
  invalid.scope.bindPhoneForm.value.phone = 'invalid'
  await invalid.run()
  assert.deepEqual(invalid.order, [])
})

test('鉴定由用户上传与评分，不登记 AI 表单；附件及评分仍必须齐全', async () => {
  const source = readFileSync(new URL('../src/views/student/InternshipAppraisal.vue', import.meta.url), 'utf8')
  assert.doesNotMatch(source, /formContext|appraisal:submit/)
  assert.match(source, /useAgentPageContext/)
  const entry = ['appraisal:submit', 'InternshipAppraisal', 'submitAppraisal', appraisal]
  for (const field of ['appraisalFiles', 'allCompanyRatingsSelected']) {
    const ctx = fixture(entry)
    ctx.scope[field].value = field === 'appraisalFiles' ? [] : false
    await ctx.run()
    assert.equal(ctx.calls.length, 0)
  }
  const ctx = fixture(entry)
  await ctx.run()
  assert.equal(ctx.calls.length, 1)
  assert.equal(ctx.receipts.length, 0)
})

test('鉴定文件上传正确附带placementId，不产生 AI 填表回执；无文件/非法类型不请求', async () => {
  const entry = ['appraisal:submit', 'InternshipAppraisal', 'handleAppraisalFileChange', () => ({ ...appraisal(), appraisalStatus: ref('DRAFT'), APPRAISAL_ALLOWED_EXTENSIONS: new Set(['pdf', 'doc', 'docx']), APPRAISAL_MAX_FILE_SIZE: 20 * 1024 * 1024 })]
  for (const linked of [true, false]) for (const mode of ['success', 'failure', 'throw']) {
    const ctx = fixture(entry, mode)
    if (!linked) { ctx.scope.internshipRecords.value = [{ placementId: null, id: 'self' }]; ctx.scope.placementKey = () => 'self'; ctx.scope.selectedPlacementId.value = 'self' }
    const input = { value: 'selected', files: [{ name: 'appraisal.pdf', size: 200 }] }
    await ctx.run({ target: input })
    assert.equal(ctx.receipts.length, 0)
    assert.equal(ctx.calls[0][0].data.values.some(([key]) => key === 'placementId'), linked)
  }
  for (const files of [[], [{ name: 'bad.exe', size: 200 }]]) {
    const ctx = fixture(entry)
    await ctx.run({ target: { value: '', files } })
    assert.deepEqual(ctx.order, [])
  }
})

test('自主实习不伪造附件，相近合作企业需选标准名称，精确命中合作企业可提交', async () => {
  const item = cases.find(entry => entry[0] === 'home:self-practice')
  const ctx = fixture(item)
  await ctx.run()
  assert.deepEqual(Object.keys(ctx.calls[0][1].materials), [])

  // 仅相近命中（疑似同一企业名称不一致）：阻止提交，不发起写请求
  const blocked = fixture(item)
  blocked.scope.similarCooperativeCompanies.value = [{ companyName: '实习单位科技有限公司' }]
  await blocked.run()
  assert.deepEqual(blocked.order, [])
  assert.deepEqual(blocked.calls, [])

  // 精确命中系统登记的校企合作企业：允许提交
  const allowed = fixture(item)
  allowed.scope.matchedCooperativeCompany.value = { companyName: '实习单位' }
  await allowed.run()
  assert.equal(allowed.calls.length, 1)
  assert.equal(allowed.receipts.length, 1)
})

test('心得文本回执携带提交前快照，文件模式未上传文件不请求', async () => {
  const entry = cases.find(item => item[0] === 'reflection:log')
  const ctx = fixture(entry)
  ctx.scope.request.post = async () => { ctx.scope.logForm.value.content = '后改内容'; return { code: 200 } }
  await ctx.run()
  assert.equal(ctx.receipts[0].savedValues.content, '本周实习总结')
  const file = fixture(entry)
  file.scope.reflectionActiveTab.value = 'file'
  file.scope.selectedFile = ref(null)
  await file.run()
  assert.deepEqual(file.order, [])
})

test('去向登记仅本页记录，不能发送业务请求或产生保存回执', async () => {
  const entry = ['registration:internship', 'InternshipRegistration', 'submitForm', () => ({ formRef: ref({ validate: cb => cb(true) }), form: { status: 'found' }, submitting: ref(false), registrationHistory: ref([]), getStatusText: () => '已找到实习', getStatusContent: () => '本页草稿' })]
  const ctx = fixture(entry)
  await ctx.run()
  assert.equal(ctx.scope.registrationHistory.value.length, 1)
  assert.deepEqual(ctx.order, [])
  assert.equal(ctx.receipts.length, 0)
  assert.match(ctx.messages[0], /本页.*尚未提交.*不会保留/)
})
