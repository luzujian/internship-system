import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'

const ref = value => ({ value })
const noop = () => {}
function handler(file, name, scope) {
  const source = readFileSync(new URL(`../src/${file}`, import.meta.url), 'utf8')
  const { descriptor } = parse(source)
  const script = descriptor.scriptSetup?.content || descriptor.script?.content
  const ast = ts.createSourceFile(file, script, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  const declaration = ast.statements.filter(ts.isVariableStatement).flatMap(s => [...s.declarationList.declarations]).find(d => d.name.getText(ast) === name)
  assert.ok(declaration, `Missing original handler ${name}`)
  const compiled = ts.transpileModule(`const actual = ${declaration.initializer.getText(ast)}; actual`, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
  return vm.runInNewContext(compiled, { console: { error() {} }, ...scope })
}

const view = name => `views/teacher/${name}.vue`
const relations = 'components/teacher/CompanyRelationsPanel.vue'
const cases = [
  ['email', view('AccountSettings'), 'updateEmail', 'teacher:account-email', 'saved', () => ({ isEmailFormValid: ref(true), emailForm: ref({ newEmail: 'teacher@example.test' }), showEmailDialog: ref(true), loadUserInfo: noop }), 'showEmailDialog'],
  ['phone', view('AccountSettings'), 'updatePhone', 'teacher:account-phone', 'saved', () => ({ isPhoneFormValid: ref(true), phoneForm: ref({ newPhone: '13812345678' }), showPhoneDialog: ref(true), showOperationFeedback: noop, loadUserInfo: noop }), 'showPhoneDialog'],
  ['announcement draft', view('AnnouncementView'), 'handleSubmit', 'teacher:announcement', 'saved', () => ({ formRef: ref({ validate: async () => {} }), formData: { title: '通知', content: '内容', targetType: [], majorIds: [], status: 'DRAFT' }, authStore: { user: { name: '老师' } }, getTeacherType: () => 'COUNSELOR', dialogVisible: ref(true), activeTab: ref('my'), currentPage: ref(1), fetchData: noop }), 'dialogVisible'],
  ['reject', view('Approval'), 'confirmReject', 'teacher:approval-reject', 'submitted', () => ({ currentApplication: ref({ id: 7, type: 'application' }), rejectReason: ref('请补全材料'), getCurrentUser: () => ({ id: 9 }), showRejectModal: ref(true), fetchApplications: noop, fetchStats: noop }), 'showRejectModal'],
  ['company audit', view('CompanyAudit'), 'submitAudit', 'teacher:company-audit', 'submitted', () => ({ auditForm: { id: 7, auditStatus: 2, auditRemark: '资料缺失' }, submitLoading: ref(false), auditDialogVisible: ref(true), fetchData: noop, fetchStatistics: noop }), 'auditDialogVisible'],
  ['visit', relations, 'saveVisit', 'teacher:company-visit', 'saved', () => ({ visitForm: ref({ companyId: 7, visitDate: '2026-09-20', participantTeacherIds: [9] }), editingVisitId: ref(null), loading: ref(false), visitDialogVisible: ref(true), emit: noop, loadVisits: noop, loadCompanies: noop }), 'visitDialogVisible'],
  ['liaison', relations, 'addLiaison', 'teacher:school-liaison', 'saved', () => ({ liaisonCompanyId: ref(7), liaisonForm: ref({ teacherId: 9 }), loading: ref(false), liaisonDialogVisible: ref(true), emit: noop, loadLiaisons: noop }), 'liaisonDialogVisible'],
  ['keyword', view('CounselorKeywordLibraryManagement'), 'handleSave', 'teacher:keyword', 'saved', () => ({ formRef: ref({ validate: async () => {} }), form: ref({ keyword: '沟通' }), saving: ref(false), counselorId: ref(9), dialogVisible: ref(true), fetchKeywords: noop }), 'dialogVisible'],
  ['rule', view('CounselorScoringRuleManagement'), 'handleSave', 'teacher:scoring-rule', 'saved', () => ({ formRef: ref({ validate: async () => {} }), form: ref({ id: 7, category: '沟通', minScore: 60 }), saving: ref(false), counselorId: ref(9), dialogVisible: ref(true), fetchRules: noop, nextTick: noop, fetchCategoryWeights: noop }), 'dialogVisible'],
  ['weights', view('CounselorScoringRuleManagement'), 'handleSaveAllWeights', 'teacher:score-weights', 'saved', () => ({ totalWeight: ref(100), weightForm: ref({ weights: { communication: 100 } }), saving: ref(false), counselorId: ref(9), weightDialogVisible: ref(true), getCategoryName: value => value, fetchCategoryWeights: noop }), 'weightDialogVisible'],
  ['resource edit', view('Resources'), 'handleSubmit', 'teacher:resource', 'published', () => ({ formData: { id: 7, title: '指南', publisher: '老师', publisherRole: 'COUNSELOR', status: 'PUBLISHED' }, dialogTitle: ref('编辑文档'), fileList: ref([]), originalFileUrl: ref('/original.pdf'), submitLoading: ref(false), dialogVisible: ref(true), fetchData: noop, logger: { error: noop } }), 'dialogVisible'],
  ['evaluation', view('Evaluation'), 'submitEvaluation', 'teacher:evaluation', 'submitted', () => ({ currentStudent: ref({ id: 7 }), isFormValid: ref(true), isSubmitting: ref(false), evaluationForm: ref({ comment: '工作认真' }), scoreItems: ref([]), totalScore: ref(80), grade: ref('良好'), loadStudentList: noop })],
  ['reminder', view('TeacherStudentManagement'), 'sendReminder', 'teacher:reminder', 'submitted', () => ({ reminderForm: ref({ content: '请提交' }), counselorId: ref(9), currentRemindStudentId: ref(7), showReminderDialog: ref(true) }), 'showReminderDialog'],
  ['batch reminder', view('TeacherStudentManagement'), 'sendBatchReminder', 'teacher:reminder-batch', 'submitted', () => ({ batchReminderForm: ref({ content: '请提交' }), batchReminderSending: ref(false), counselorId: ref(9), isCounselor: ref(true), filters: ref({}), myClasses: ref([]), getCounselorStudents: async () => ({ data: [{ id: 7 }] }), showBatchReminderDialog: ref(true) }), 'showBatchReminderDialog']
]

function fixture(item, fail = false) {
  const [name, file, functionName, formId, status, createScope, dialog] = item
  const scope = createScope(), receipts = [], order = []
  const write = async () => { order.push('write'); if (fail) throw new Error('backend rejected'); return { code: 200, data: { success: 1, fail: 0 } } }
  Object.assign(scope, {
    ElMessage: { success: noop, error: noop, warning: noop },
    formContext: { beginFormOperation(id, props) { assert.equal(id, formId); order.push('capture'); return { complete(receipt) { if (dialog) assert.equal(scope[dialog].value, true, `${name}: receipt must precede close`); order.push('complete'); receipts.push({ ...receipt, props }) } } } },
    request: { post: write, put: write }, accountSettingsApi: { updateContact: write }, announcementApi: { updateAnnouncement: write, addAnnouncement: write },
    approvalApi: { rejectCompanyQualification: write, rejectStudentApplication: write }, updateInternshipCalendar: write,
    updateScoringRule: write, batchCreateScoringRules: write, batchUpdateCategoryWeights: write,
    resourceDocumentApi: { teacherUploadResourceDocument: write, teacherUpdateResourceDocument: write }, evaluationApi: { submitEvaluation: write }
  })
  return { scope, receipts, order, run: () => handler(file, functionName, scope)(), status }
}

for (const item of cases) {
  test(`${item[0]}: explicit success records before close; failure never records`, async () => {
    const ok = fixture(item)
    await ok.run()
    assert.equal(ok.receipts.length, 1)
    assert.equal(ok.receipts[0].status, ok.status)
    assert.deepEqual(ok.order, ['capture', 'write', 'complete'])
    const failed = fixture(item, true)
    await failed.run()
    assert.equal(failed.receipts.length, 0)
  })
}

test('新增评分规则只确认真实提交的分类，不将未提交的分数/描述标成已保存', async () => {
  const ctx = fixture(cases.find(item => item[0] === 'rule'))
  delete ctx.scope.form.value.id
  await ctx.run()
  assert.equal(ctx.receipts.length, 1)
  assert.deepEqual([...ctx.receipts[0].props], ['category'])
})

test('资源缺少真实附件时原验证拦截，不能产生业务回执', async () => {
  const ctx = fixture(cases.find(item => item[0] === 'resource edit'))
  ctx.scope.originalFileUrl.value = ''
  await ctx.run()
  assert.equal(ctx.order.length, 0)
})

test('批量邮件部分失败回执包含失败数，全失败不完成且保留表单', async () => {
  const partial = fixture(cases.find(item => item[0] === 'batch reminder'))
  partial.scope.request.post = async () => ({ data: { success: 1, fail: 2 } })
  await partial.run()
  assert.match(partial.receipts[0].label, /1 人，2 人失败/)
  const failed = fixture(cases.find(item => item[0] === 'batch reminder'))
  failed.scope.request.post = async () => ({ data: { success: 0, fail: 1 } })
  await failed.run()
  assert.equal(failed.receipts.length, 0)
  assert.equal(failed.scope.showBatchReminderDialog.value, true)
})

test('批量提醒使用学生数据库主键，数字学号不能冒充另一学生主键', async () => {
  const ctx = fixture(cases.find(item => item[0] === 'batch reminder'))
  ctx.scope.getCounselorStudents = async () => ({ data: [
    { id: 7, studentUserId: '42' },
    { studentUserId: '88' }
  ] })
  let submittedIds
  ctx.scope.request.post = async (_url, body) => {
    submittedIds = Array.from(body.studentIds)
    return { data: { success: 1, fail: 0 } }
  }
  await ctx.run()
  assert.deepEqual(submittedIds, [7])
  assert.match(ctx.receipts[0].label, /1 人失败/)
})

test('原生日历和权重校验失败时不发起保存', async () => {
  let writes = 0
  const save = handler(view('Settings'), 'saveCalendar', { validateCalendar: () => '结束日期早于开始日期', showOperationFeedback: noop, updateInternshipCalendar: () => { writes++ } })
  await save()
  assert.equal(writes, 0)
  const weights = fixture(cases.find(item => item[0] === 'weights'))
  weights.scope.totalWeight.value = 90
  await weights.run()
  assert.equal(weights.order.length, 0)
})
test('驳回理由占位文案只提示填写驳回理由', () => {
  const source = readFileSync(new URL('../src/views/teacher/Approval.vue', import.meta.url), 'utf8')
  assert.match(source, /placeholder="请填写驳回理由"/)
  assert.doesNotMatch(source, /请填写驳回理由，引导学生补充材料/)
})
