import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'

const ref = value => ({ value })
const noop = () => {}
const tick = () => new Promise(resolve => setImmediate(resolve))
const sources = new Map()
function originalHandler(file, name, scope) {
  const key = `${file}:${name}`
  if (!sources.has(key)) {
    const { descriptor } = parse(readFileSync(new URL(`../src/views/admin/${file}.vue`, import.meta.url), 'utf8'))
    const script = descriptor.scriptSetup?.content || descriptor.script?.content
    const ast = ts.createSourceFile(file, script, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
    const node = ast.statements.filter(ts.isVariableStatement).flatMap(s => [...s.declarationList.declarations]).find(d => d.name.getText(ast) === name)
    assert.ok(node, `Missing real handler ${key}`)
    sources.set(key, ts.transpileModule(`const actual = ${node.initializer.getText(ast)}; actual`, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText)
  }
  return vm.runInNewContext(sources.get(key), scope)
}
const announce = () => ({ title: '通知', content: '请核对', status: 'DRAFT', publisher: '管理员', publisherRole: 'ADMIN', targetType: ['ALL'], attachments: [] })
const resource = () => ({ id: 3, title: '指南', publisher: '管理员', publisherRole: 'ADMIN', targetType: 'ALL', status: 'DRAFT', file: {} })
const profile = () => ({ editingUser: { id: 3, username: '管理员', phone: '13812345678' }, editDialogVisible: ref(true), loadUserInfo: noop })
// Each entry executes an original production save handler, not a copy of its implementation.
const cases = [
  ['admin-user', 'AdminUserManagement', 'addUser', v => ({ addFormRef: v(), addForm: ref({ username: '管理员' }), addDialogVisible: ref(true), loadAdmins: noop }), 'addDialogVisible'],
  ['admin-user-edit', 'AdminUserManagement', 'updateUser', v => ({ editFormRef: v(), editForm: ref({ id: 3, username: '管理员' }), editDialogVisible: ref(true), loadAdmins: noop }), 'editDialogVisible'],
  ['ai-model', 'AIModelManagement', 'handleSave', v => ({ formRef: v(), form: ref({ name: 'local', hasApiKey: true }), saving: ref(false), dialogVisible: ref(true), fetchModels: noop, capabilitiesLoading: ref(false), capabilityError: ref(null) }), 'dialogVisible'],
  ['announcement', 'AnnouncementManagement', 'handleSubmit', v => ({ formRef: v(), formData: announce(), dialogVisible: ref(true), fetchData: noop }), 'dialogVisible'],
  ['backup', 'BackupManagement', 'saveSchedule', () => ({ saving: ref(false), scheduleForm: ref({ enabled: true, time: '02:00' }), formatTime: value => value })],
  ['center-profile', 'ProfileCenter', 'saveProfile', profile, 'editDialogVisible'],
  ['class', 'ClassManagement', 'saveClass', v => ({ formTitle: ref('新增班级'), addClass: ref({ name: '测试班' }), addClassFormRef: v(), dialogFormVisible: ref(true), queryPage: noop }), 'dialogFormVisible'],
  ['company', 'CompanyManagement', 'submitForm', v => ({ formRef: v(), isEdit: ref(false), companyForm: ref({ companyName: '测试企业' }), formDialogVisible: ref(true), queryPage: noop }), 'formDialogVisible'],
  ['dashboard-announcement', 'Dashboard', 'submitAnnouncement', v => ({ announcementFormRef: v(), announcementForm: ref(announce()), submitting: ref(false), announcementDialogVisible: ref(true), resetAnnouncementForm: noop }), 'announcementDialogVisible'],
  ['dashboard-resource', 'Dashboard', 'submitResourceDocument', v => ({ resourceDocumentFormRef: v(), resourceDocumentForm: ref(resource()), resourceSubmitting: ref(false), resourceDocumentDialogVisible: ref(true), resetResourceDocumentForm: noop }), 'resourceDocumentDialogVisible'],
  ['department', 'DepartmentMajorManagement', 'saveDepartment', v => ({ formTitle: ref('新增院系'), addDepartment: ref({ name: '学院' }), addDepartmentFormRef: v(), dialogFormVisible: ref(true), queryDepartments: noop }), 'dialogFormVisible'],
  ['division', 'DepartmentMajorManagement', 'saveDivision', v => ({ addDivisionForm: ref({ name: '系', departmentId: 1 }), addDivisionFormRef: v(), addDivisionDialogVisible: ref(true), getDivisionList: noop, queryDepartments: noop }), 'addDivisionDialogVisible'],
  ['division-edit', 'DepartmentMajorManagement', 'saveEditDivision', v => ({ editDivisionForm: ref({ id: 3, name: '系', departmentId: 1 }), editDivisionFormRef: v(), editDivisionDialogVisible: ref(true), getDivisionList: noop, queryDepartments: noop }), 'editDivisionDialogVisible'],
  ['feedback', 'FeedbackManagement', 'handleReplySubmit', v => ({ replyForm: { id: 3, adminReply: '已核实', status: 'RESOLVED' }, replyFormRef: v(), replyDialogVisible: ref(true), fetchData: noop }), 'replyDialogVisible'],
  ['keyword', 'KeywordLibraryManagement', 'handleSave', v => ({ form: ref({ keyword: '认真' }), formRef: v(), saving: ref(false), dialogVisible: ref(true), fetchKeywords: noop }), 'dialogVisible'],
  ['log-retention', 'LogManagement', 'saveRetentionPolicies', () => ({ retentionLoading: ref(false), retentionForm: ref({ highRiskDays: 365, normalDays: 90, loginDays: 90 }) })],
  ['major', 'MajorManagement', 'addMajor', v => ({ addForm: ref({ name: '计算机' }), addFormRef: v(), addDialogVisible: ref(true), loadMajors: noop }), 'addDialogVisible'],
  ['major-edit', 'MajorManagement', 'updateMajor', v => ({ editForm: ref({ id: 3, name: '计算机' }), editFormRef: v(), editDialogVisible: ref(true), loadMajors: noop }), 'editDialogVisible'],
  ['major-org', 'DepartmentMajorManagement', 'saveMajor', v => ({ addMajorForm: ref({ name: '计算机', departmentId: 1, divisionId: 2 }), addMajorFormRef: v(), addMajorDialogVisible: ref(true), fixedDepartmentId: ref(1), getMajorList: noop, queryDepartments: noop }), 'addMajorDialogVisible'],
  ['major-org-edit', 'DepartmentMajorManagement', 'saveEditMajor', v => ({ editMajorForm: ref({ id: 3, name: '计算机', departmentId: 1, divisionId: 2 }), editMajorFormRef: v(), editMajorDialogVisible: ref(true), fixedDepartmentId: ref(1), getMajorList: noop, queryDepartments: noop }), 'editMajorDialogVisible'],
  ['position-category', 'PositionCategoryManagement', 'handleAddCategorySubmit', v => ({ categoryFormRef: v(), categoryForm: ref({ name: '技术', description: '技术岗位' }), submitting: ref(false), dialogFormVisible: ref(true), queryCategories: noop }), 'dialogFormVisible'],
  ['profile-edit', 'AdminLayout', 'saveProfile', profile, 'editDialogVisible'],
  ['resource', 'ResourceDocumentManagement', 'handleSubmit', () => ({ formData: resource(), dialogTitle: ref('编辑文档'), fileList: ref([]), originalFileUrl: ref('/original.pdf'), submitLoading: ref(false), dialogVisible: ref(true), fetchData: noop }), 'dialogVisible'],
  ['score-weights', 'ScoringRuleManagement', 'handleSaveAllWeights', () => ({ weightForm: ref({ weights: { communication: 100 } }), saving: ref(false), weightDialogVisible: ref(true), getCategoryName: value => value, fetchCategoryWeights: noop }), 'weightDialogVisible'],
  ['scoring-rule', 'ScoringRuleManagement', 'handleSave', v => ({ form: ref({ id: 3, category: '沟通', minScore: 60, description: '已填写' }), formRef: v(), saving: ref(false), dialogVisible: ref(true), fetchRules: noop, redistributeWeights: noop, fetchCategoryWeights: noop }), 'dialogVisible'],
  ['security', 'SystemSettingsManagement', 'saveSecuritySettings', v => ({ securityFormRef: v(), securityForm: { passwordComplexity: ['UPPERCASE'] }, saving: ref(false), systemSettingsStore: { updateSecuritySettings: noop } })],
  ['student', 'StudentUserManagement', 'saveStudent', v => ({ formTitle: ref('新增学生'), addStudent: ref({ name: '学生' }), addStudentFormRef: v(), dialogFormVisible: ref(true), queryPage: noop }), 'dialogFormVisible'],
  ['teacher', 'TeacherUserManagement', 'saveTeacher', v => ({ formTitle: ref('新增教师'), addTeacher: ref({ name: '教师' }), addTeacherFormRef: v(), dialogFormVisible: ref(true), queryPage: noop }), 'dialogFormVisible']
]

function fixture(entry, mode = 'success', invalid = false) {
  const [id, file, name, create, dialog] = entry, receipts = [], order = [], validations = [], pending = []
  const validation = () => ref({ validate(callback) {
    const promise = (async () => { await tick(); validations.push('validated'); if (callback) return callback(!invalid); if (invalid) throw new Error('validation failed'); return true })()
    // Some existing UI handlers use validate(callback) without returning its promise.
    pending.push(promise.catch(noop))
    return promise
  } })
  const scope = create(validation)
  const write = async () => { order.push('write'); if (mode === 'throw') throw new Error('backend rejected'); return { code: mode === 'failure' ? 500 : 200, data: { deletedCount: 2, highRiskDays: 365, normalDays: 90, loginDays: 90 } } }
  const service = new Proxy({}, { get: () => write })
  Object.assign(scope, {
    console: { error: noop }, logger: { log: noop, error: noop }, ElMessage: { success: noop, error: noop, warning: noop, info: noop }, ElMessageBox: { confirm: async () => {} },
    authStore: { user: { id: 3, name: '管理员' } }, nextTick: tick, sessionStorage: { removeItem: noop },
    FormData: class { append() {} },
    formContext: { beginFormOperation(formId, props) { assert.equal(formId, `admin:${id}`); order.push('capture'); return { complete(receipt) { if (dialog) assert.equal(scope[dialog].value, true, 'receipt must precede close'); order.push('complete'); receipts.push({ ...receipt, props }) } } } },
    AdminUserService: service, aiModelService: service, announcementApi: service, companyService: service, request: service,
    DivisionService: service, MajorService: service, DepartmentService: service, problemFeedbackApi: service, PositionCategoryService: service,
    resourceDocumentApi: service, updateRetentionPoliciesApi: write, runRetentionApi: write, addApi: write, updateApi: write
  })
  const run = async () => { await originalHandler(file, name, scope)(); await Promise.all(pending); await tick() }
  return { scope, receipts, order, validations, run, dialog }
}

test('管理员行为夹具覆盖登记的全部28个表单ID', () => {
  const registry = readFileSync(new URL('../src/agent/formCoverage.ts', import.meta.url), 'utf8')
  const registered = [...registry.matchAll(/formId: '(admin:[^']+)'/g)].map(m => m[1]).sort()
  assert.equal(cases.length, 28)
  assert.deepEqual(cases.map(item => `admin:${item[0]}`).sort(), registered)
})

for (const item of cases) {
  test(`${item[0]}: 200完成且先于关闭；业务失败/异常不冒认回执`, async () => {
    const ok = fixture(item)
    await ok.run()
    assert.equal(ok.receipts.length, 1, 'success must publish one real receipt')
    assert.deepEqual(ok.order, ['capture', 'write', 'complete'])
    for (const mode of ['failure', 'throw']) {
      const failed = fixture(item, mode)
      await failed.run()
      assert.equal(failed.receipts.length, 0, mode)
      if (failed.dialog) assert.equal(failed.scope[failed.dialog].value, true, 'failure must preserve the form')
    }
  })
}

test('所有使用异步原生验证的表单校验失败时不提前发业务请求', async () => {
  for (const item of cases) {
    const ctx = fixture(item, 'success', true)
    if (!Object.keys(ctx.scope).some(key => /FormRef$|^formRef$/.test(key))) continue
    await ctx.run()
    assert.ok(ctx.validations.length, item[0])
    assert.deepEqual(ctx.order, [], `${item[0]}: must await validation before capture/request`)
  }
})

test('学生/教师新增及编辑均在异步原校验通过后才创建请求', async () => {
  for (const id of ['student', 'teacher']) for (const valid of [false, true]) {
    const ctx = fixture(cases.find(item => item[0] === id), 'success', !valid)
    const title = id === 'student' ? '学生' : '教师', capital = id === 'student' ? 'Student' : 'Teacher'
    ctx.scope.formTitle.value = `编辑${title}`
    ctx.scope[`edit${capital}`] = ctx.scope[`add${capital}`]
    ctx.scope[`edit${capital}FormRef`] = ctx.scope[`add${capital}FormRef`]
    await ctx.run()
    assert.equal(ctx.receipts.length, valid ? 1 : 0)
    assert.equal(ctx.order.includes('write'), valid)
  }
})

test('日志保留策略与实际清理分别只确认自身提交字段', async () => {
  const item = cases.find(item => item[0] === 'log-retention')
  const policy = fixture(item)
  await policy.run()
  assert.deepEqual([...policy.receipts[0].props], ['highRiskDays', 'normalDays', 'loginDays'])
  assert.match(policy.receipts[0].label, /未执行清理/)
  const clean = fixture([item[0], item[1], 'executeRetention', () => ({ retentionPreviewData: ref({ total: 2 }), retentionReason: ref('已到留存期限'), retentionPreviewTotal: ref(2), retentionRunning: ref(false), loadSummary: noop, queryPage: noop })])
  await clean.run()
  assert.deepEqual([...clean.receipts[0].props], ['retentionReason'])
  assert.match(clean.receipts[0].label, /实际删除 2 条/)
})

test('新增评分规则只确认category；分数和描述不假装发送给后端', async () => {
  const ctx = fixture(cases.find(item => item[0] === 'scoring-rule'))
  delete ctx.scope.form.value.id
  await ctx.run()
  assert.deepEqual([...ctx.receipts[0].props], ['category'])
})

test('Dashboard公告/资源草稿与正式发布分别标识，手工附件缺失不发送', async () => {
  for (const id of ['dashboard-announcement', 'dashboard-resource']) for (const status of ['DRAFT', 'PUBLISHED']) {
    const ctx = fixture(cases.find(item => item[0] === id))
    const model = id.endsWith('announcement') ? ctx.scope.announcementForm.value : ctx.scope.resourceDocumentForm.value
    model.status = status
    await ctx.run()
    assert.equal(ctx.receipts[0].status, status === 'PUBLISHED' ? 'published' : 'saved')
  }
  const absent = fixture(cases.find(item => item[0] === 'dashboard-resource'))
  absent.scope.resourceDocumentForm.value.file = null
  await absent.run()
  assert.deepEqual(absent.order, [])
})
