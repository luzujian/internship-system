import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'
import { serializeInterviewMethods } from '../src/utils/interviewMethods.js'
import { normalizeSalaryUnit } from '../src/utils/salaryRange.js'

const ref = value => ({ value })
const noop = () => {}
const tick = () => new Promise(resolve => setImmediate(resolve))
const compiled = new Map()
function handler(file, name, scope) {
  const key = `${file}:${name}`
  if (!compiled.has(key)) {
    const { descriptor } = parse(readFileSync(new URL(`../src/views/company/${file}.vue`, import.meta.url), 'utf8'))
    const ast = ts.createSourceFile(file, descriptor.scriptSetup?.content || descriptor.script?.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
    const declaration = ast.statements.filter(ts.isVariableStatement).flatMap(s => [...s.declarationList.declarations]).find(d => d.name.getText(ast) === name)
    assert.ok(declaration, key)
    compiled.set(key, ts.transpileModule(`const actual = ${declaration.initializer.getText(ast)}; actual`, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText)
  }
  return vm.runInNewContext(compiled.get(key), scope)
}

const company = () => ({ loading: ref(false), companyId: ref(3), basicForm: ref({ companyName: '实习单位', province: '广东省', city: '深圳市', isAlumniUnit: 0, alumniMembers: [] }), promotionForm: ref({ logo: '', photos: [], videos: [], introduction: '原企业简介' }), validateAlumniMembers: () => true })
const cases = [
  ['company:account', 'AccountSettings', 'handleSaveProfile', () => ({ loading: ref(false), profileForm: ref({ username: '企业用户', phone: '13812345678', email: 'company@example.test' }), authStore: { user: { id: 3 } } })],
  ['company:info', 'CompanyInfo', 'handleSaveBasic', company],
  ['company:promotion', 'CompanyInfo', 'handleSavePromotion', company],
  ['company:position', 'RecruitmentManagement', 'handleSave', () => ({ loading: ref(false), positionForm: ref({ positionName: '研发实习', salaryUnit: 'K/月', salaryMin: 4, salaryMax: 6, requiredMajors: ['不限制专业'], plannedRecruit: 3, status: 'active' }), companyId: ref(3), dialogType: ref('create'), dialogVisible: ref(true), categoryList: ref([]), commitDepartmentAllowCreate: noop, commitPositionNameAllowCreate: noop }), 'dialogVisible'],
  ['company:interview-template', 'RecruitmentManagement', 'handleInterviewSubmit', () => ({ interviewForm: ref({ interviewTime: '2026-09-21 14:00', interviewMethod: ['线下', '线上视频'], remark: '请带简历' }), interviewDialogVisible: ref(true) }), 'interviewDialogVisible'],
  ['company:contact', '../../components/company/CompanyContacts', 'saveContact', () => ({ saving: ref(false), contactForm: ref({ name: '联系人甲', phone: '13800000001', email: 'one@example.test' }), formRef: ref({ validate: async () => true }), editingId: ref(null), dialogVisible: ref(true), closeContact: noop, loadContacts: noop }), 'dialogVisible']
]
function fixture(entry, mode = 'success') {
  const [formId, file, name, create, dialog] = entry, receipts = [], order = [], calls = [], localWrites = [], messages = []
  const scope = create()
  const model = () => formId === 'company:account' ? scope.profileForm.value : formId === 'company:info' ? scope.basicForm.value : formId === 'company:promotion' ? scope.promotionForm.value : formId === 'company:position' ? scope.positionForm.value : formId === 'company:contact' ? scope.contactForm.value : scope.interviewForm.value
  const write = async (...args) => { order.push('write'); calls.push(args); if (mode === 'throw') throw new Error('server failed'); return { code: mode === 'failure' ? 500 : 200 } }
  const positionWrite = async (...args) => (await write(...args)).code === 200
  Object.assign(scope, {
    console: { log: noop, error: noop }, ElMessage: { success: message => messages.push(message), warning: noop, error: noop },
    CompanyService: { updateProfile: write, updateCompanyInfo: write }, positionStore: { addPosition: positionWrite, updatePosition: positionWrite },
    saveCompanyContact: write,
    serializeInterviewMethods,
    hasSearched: ref(false), handleSearch: noop,
    localStorage: { setItem(key, value) { order.push('local-write'); if (mode !== 'success') throw new Error('local storage unavailable'); localWrites.push([key, value]) } },
    formContext: { currentForm: ref({ formId, instanceId: 17 }), captureValues(id) { assert.equal(id, formId); order.push('capture'); return structuredClone(model()) }, reportFormOutcome(id, receipt, instance, values) { assert.equal(id, formId); assert.equal(instance, 17); if (dialog) assert.equal(scope[dialog].value, true, 'receipt before close'); order.push('complete'); receipts.push({ ...receipt, values }) } }
  })
  return { scope, model, receipts, order, calls, localWrites, messages, run: async () => { await handler(file, name, scope)(); await tick() } }
}

test('企业回执行为夹具覆盖登记的全部6个formId', () => {
  const registry = readFileSync(new URL('../src/agent/formCoverage.ts', import.meta.url), 'utf8')
  const ids = [...registry.matchAll(/formId: '(company:[^']+)'/g)].map(match => match[1]).sort()
  assert.deepEqual(cases.map(item => item[0]).sort(), ids)
})

for (const entry of cases.filter(item => item[0] !== 'company:interview-template')) test(`${entry[0]}: 成功才回执，非200/异常不冒认保存或关闭表单`, async () => {
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

test('保存全部只报告当前关联表单，基础信息/宣传资料各自使用请求前快照', async () => {
  for (const formId of ['company:info', 'company:promotion']) for (const mode of ['success', 'failure', 'throw']) {
    const entry = [...cases.find(item => item[0] === formId)]
    entry[2] = 'handleSaveAll'
    const ctx = fixture(entry, mode)
    await ctx.run()
    assert.equal(ctx.receipts.length, mode === 'success' ? 1 : 0)
    if (mode === 'success') assert.equal(ctx.receipts[0].status, 'saved')
  }
})

test('异步等待期间用户新输入不会被冒认为此次已保存内容', async () => {
  for (const entry of cases.filter(item => item[0] !== 'company:interview-template')) {
    const ctx = fixture(entry)
    const before = structuredClone(ctx.model())
    const write = async () => { for (const key of Object.keys(ctx.model())) if (typeof ctx.model()[key] === 'string') ctx.model()[key] = '后来手动修改'; return { code: 200 } }
    ctx.scope.CompanyService.updateProfile = write
    ctx.scope.CompanyService.updateCompanyInfo = write
    ctx.scope.positionStore.addPosition = async () => { await write(); return true }
    ctx.scope.saveCompanyContact = write
    await ctx.run()
    assert.deepEqual(ctx.receipts[0].values, before, entry[0])
  }
})

test('岗位创建发布、已有岗位更新只标保存，失败保留原表单', async () => {
  const entry = cases.find(item => item[0] === 'company:position')
  const created = fixture(entry)
  await created.run()
  assert.equal(created.receipts[0].status, 'published')
  const edited = fixture(entry)
  edited.scope.dialogType.value = 'edit'; edited.scope.positionForm.value.id = 3
  await edited.run()
  assert.equal(edited.receipts[0].status, 'saved')
  assert.equal(edited.receipts[0].object.id, '3')
})

test('面议薪资保存回执必须与原业务归一后的实际请求值一致', async () => {
  const ctx = fixture(cases.find(item => item[0] === 'company:position'))
  ctx.scope.positionForm.value.salaryUnit = '面议'
  await ctx.run()
  assert.equal(ctx.calls[0][1].salaryMin, '')
  assert.equal(ctx.receipts[0].values.salaryMin, '')
  assert.equal(ctx.receipts[0].values.salaryMax, '')
})

test('发布和编辑岗位将多选方式保存为文本，清空时提交空文本，回执保留表单数组', async () => {
  const entry = cases.find(item => item[0] === 'company:position')
  for (const mode of ['create', 'edit']) for (const selected of [['线下', '线上视频'], []]) {
    const ctx = fixture(entry)
    ctx.scope.dialogType.value = mode
    ctx.scope.positionForm.value.id = mode === 'edit' ? 3 : null
    ctx.scope.positionForm.value.interviewMethod = selected
    await ctx.run()
    const submitted = ctx.calls[0].at(-1)
    assert.equal(submitted.interviewMethod, selected.join('、'))
    assert.deepEqual(ctx.receipts[0].values.interviewMethod, selected)
  }
})

test('搜索结果保留面试和专业字段，编辑保存后重新加载当前搜索', async () => {
  const row = { id: 3, positionName: '岗位甲', interviewMethod: '线下、线上视频', interviewTime: '2026-10-12 14:00:00', interviewLocation: '会议室', interviewRemark: '提前联系', requiredMajors: '不限制专业' }
  const search = {
    hasSearched: ref(false), searchForm: ref({}), companyId: ref(3), searchResults: ref([]), currentPage: ref(2),
    positionApi: { getPositionsByCompanyId: async () => ({ code: 200, data: [row] }) },
    formatWorkLocation: () => '', formatDate: value => value, normalizeSalaryUnit,
    ElMessage: { success: noop, error: noop }, console: { error: noop }
  }
  await handler('RecruitmentManagement', 'handleSearch', search)()
  for (const key of ['interviewMethod', 'interviewTime', 'interviewLocation', 'interviewRemark', 'requiredMajors']) {
    assert.equal(search.searchResults.value[0][key], row[key])
  }
  const ctx = fixture(cases.find(item => item[0] === 'company:position'))
  let refreshed = 0
  ctx.scope.dialogType.value = 'edit'
  ctx.scope.hasSearched.value = true
  ctx.scope.handleSearch = async () => { refreshed++ }
  await ctx.run()
  assert.equal(refreshed, 1)
})

test('面试模板只保存浏览器且尚未发布，存储失败无成功回执且保留弹窗', async () => {
  const entry = cases.find(item => item[0] === 'company:interview-template')
  const ok = fixture(entry)
  await ok.run()
  assert.deepEqual(ok.order, ['capture', 'local-write', 'complete'])
  assert.equal(ok.calls.length, 0)
  assert.match(ok.receipts[0].label, /当前浏览器，尚未发布岗位/)
  assert.equal(ok.localWrites[0][0], 'interviewTemplate')
  assert.deepEqual(JSON.parse(ok.localWrites[0][1]).interviewMethod, ['线下', '线上视频'])
  const failed = fixture(entry, 'throw')
  await assert.rejects(failed.run())
  assert.equal(failed.receipts.length, 0)
  assert.equal(failed.scope.interviewDialogVisible.value, true)
})

test('原邮箱/校友资料/薪资校验不通过时不会发送保存请求', async () => {
  const email = fixture(cases[0]); email.scope.profileForm.value.email = 'invalid'; await email.run(); assert.equal(email.calls.length, 0)
  const basic = fixture(cases[1]); basic.scope.validateAlumniMembers = () => false; await basic.run(); assert.equal(basic.calls.length, 0)
  const salary = fixture(cases[3]); salary.scope.positionForm.value.salaryMin = 8; await salary.run(); assert.equal(salary.calls.length, 0)
})
