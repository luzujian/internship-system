import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'

// Execute the registration's real callback without mounting unrelated API/UI code.
function loadContext(view, bindings) {
  const source = readFileSync(new URL(`../src/views/${view}.vue`, import.meta.url), 'utf8')
  const script = parse(source).descriptor.scriptSetup.content
  const ast = ts.createSourceFile(`${view}.ts`, script, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  const registrations = []
  function visit(node) {
    if (ts.isCallExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === 'useAgentPageContext') registrations.push(node)
    ts.forEachChild(node, visit)
  }
  visit(ast)
  assert.equal(registrations.length, 1)
  let getter
  const code = ts.transpileModule(registrations[0].getText(ast), { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
  vm.runInNewContext(code, { ...bindings, useAgentPageContext: callback => { getter = callback } })
  return () => JSON.parse(JSON.stringify(getter()))
}
const ref = value => ({ value })

test('岗位上下文跟随实际编辑对象，关闭弹窗不保留旧对象且可见列表有界', () => {
  const bindings = { dialogVisible: ref(true), interviewDialogVisible: ref(false), positionForm: ref({ id: 31 }),
    searchForm: ref({ keyword: '前端', status: 'active', department: '软件' }),
    paginatedTableData: ref(Array.from({ length: 25 }, (_, i) => ({ id: i + 1, description: '不应发送的正文' }))) }
  const context = loadContext('company/RecruitmentManagement', bindings)
  assert.deepEqual(context().object, { type: 'company_job', id: '31' })
  assert.equal(context().view, 'position-form')
  assert.equal(context().filters.keyword, '前端')
  assert.equal(context().filters.visibleIds.length, 20)
  assert.equal(JSON.stringify(context()).includes('不应发送'), false)
  bindings.dialogVisible.value = false
  bindings.interviewDialogVisible.value = true
  assert.equal(context().object, null)
  assert.equal(context().view, 'interview-template')
})

test('申请上下文使用真实筛选字段，重名岗位不猜测岗位 ID', () => {
  const bindings = { positionStore: { positions: [{ id: 11, positionName: '工程师' }, { id: 12, positionName: '工程师' }] },
    searchForm: ref({ keyword: '小王', status: 'pending', position: '工程师' }), detailDialogVisible: ref(true),
    currentStudent: ref({ id: 91, phone: '不应发送' }), paginatedTableData: ref([{ id: 91 }]) }
  const context = loadContext('company/ApplicationView', bindings)
  assert.equal(context().filters.positionId, '')
  assert.equal(context().filters.keyword, '小王')
  assert.deepEqual(context().object, { type: 'company_application', id: '91' })
  bindings.positionStore.positions.pop()
  assert.equal(context().filters.positionId, '11')
  assert.equal(JSON.stringify(context()).includes('phone'), false)
})

test('撤回弹窗优先绑定被撤回记录，关闭后恢复待确认对象', () => {
  const bindings = { showRecallDialog: ref(true), recallTarget: ref({ id: 22 }), pendingConfirmation: ref({ id: 10 }), selectedOfferId: ref('7') }
  const context = loadContext('student/InternshipConfirmationForm', bindings)
  assert.deepEqual(context().object, { type: 'confirmation', id: '22' })
  assert.equal(context().view, 'recall-dialog')
  bindings.showRecallDialog.value = false
  assert.deepEqual(context().object, { type: 'confirmation', id: '10' })
  assert.equal(context().view, 'pending-confirmation')
  bindings.pendingConfirmation.value = null
  assert.deepEqual(context().object, { type: 'offer', id: '7' })
})

test('鉴定上下文使用鉴定记录 ID，不能把实习单位来源键当鉴定 ID', () => {
  const bindings = { appraisalId: ref(null), selectedPlacementId: ref('R123'), appraisalStatus: ref('DRAFT') }
  const context = loadContext('student/InternshipAppraisal', bindings)
  assert.equal(context().object, null)
  bindings.appraisalId.value = '88'
  bindings.appraisalStatus.value = 'SUBMITTED'
  assert.deepEqual(context().object, { type: 'appraisal', id: '88' })
  assert.equal(context().filters.appraisalStatus, 'SUBMITTED')
  assert.equal(JSON.stringify(context()).includes('R123'), false)
})

test('教师批阅上下文提供当前学生、有限选中对象和真实周期班级，不发送评分内容', () => {
  const bindings = { classes: ref([{ id: 5, name: '软件 1 班' }]), filters: ref({ class: '软件 1 班', search: '小王', period: 3, majorName: '软件工程', hasReport: 'submitted' }),
    showEvaluationModal: ref(true), showBatchEvaluationModal: ref(false), currentStudent: ref({ id: 80, comment: '不应发送评语' }),
    selectedStudents: ref(Array.from({ length: 25 }, (_, i) => i + 1)) }
  const context = loadContext('teacher/Evaluation', bindings)
  assert.deepEqual(context().object, { type: 'student', id: '80' })
  assert.equal(context().selectedObjects.length, 20)
  assert.equal(context().filters.classId, '5')
  assert.equal(context().filters.period, 3)
  assert.equal(JSON.stringify(context()).includes('不应发送'), false)
  bindings.showEvaluationModal.value = false
  bindings.showBatchEvaluationModal.value = true
  assert.equal(context().object, null)
  assert.equal(context().view, 'batch-evaluation')
})
