import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'
import { ref, shallowRef, reactive, computed } from 'vue'
import { register, unregister, currentForm, captureValues, applyAssignments, getFormFields, getFormValidation, validateForm, setFormSession } from '../src/agent/formContext.ts'
import { boundedUiFilters } from '../src/agent/sendRouting.mjs'
import { terminal, responseOperation } from '../src/agent/protocol.mjs'

const storeSource = fs.readFileSync(new URL('../src/agent/store.ts', import.meta.url), 'utf8')
const workspace = parse(fs.readFileSync(new URL('../src/components/agent/AgentWorkspace.vue', import.meta.url), 'utf8')).descriptor
function functions(source, names) {
  const ast = ts.createSourceFile('source.ts', source, ts.ScriptTarget.Latest, true), found = []
  function visit(node) {
    if (ts.isFunctionDeclaration(node) && names.includes(node.name?.text)) found.push(node.getText(ast))
    ts.forEachChild(node, visit)
  }
  visit(ast)
  assert.equal(found.length, names.length)
  return found.join('\n')
}
function storeFixture(role = 'ROLE_ADMIN') {
  const scope = { epoch: 1, uiTurn: 0, revision: ref(3), tasks: ref([]), sessionId: ref('session-one'), capabilities: ref({ enabled: true, resume: true, actor: { ownerKey: 'actor-one', role, readOnly: false } }),
    uiActionQueue: shallowRef([]), uiRequestContexts: new Map(), uiTaskContexts: new Map(), deliveredUiTasks: new Set(), currentForm, captureValues, applyAssignments, getFormValidation, validateForm,
    uiFeedbackRequests: new Map(), pendingUiFeedback: shallowRef(null), uiFeedbackController: null, AbortController,
    formFillEnabled: ref(true), isAdminRole: ref(role === 'ROLE_ADMIN'), isTeacherRole: ref(role.startsWith('ROLE_TEACHER')), autoTaskEnabled: ref(true),
    fillEvents: ref([]), autoFlow: ref({ phase: 'idle', message: '', working: false }), capsFlow: ref({ phase: 'idle', message: '', working: false }),
    updateAutoPreview(data) { scope.autoFlow.value.draft = data; scope.autoFlow.value.phase = data.questions?.length ? 'need_input' : 'need_confirm' },
    savedEvents: [], saveEvent(event) { scope.savedEvents.push(event); return Promise.resolve() },
    cancellingIds: ref([]), terminal, responseOperation, autoOpenedReminders: new Set(), reminderDialog: ref({ open: false }),
    resendRequests: new Map(), chatEnabled: ref(true), newRequestId: () => 'resend-request-one', listen() {}, waitForIdle: async () => true, refreshCapabilities() {},
    flowPreviewController: null, flowPreviewEpoch: 0, filling: ref(false), sending: ref(false), cancelAfterSend: false, cancelFill() {},
    error: ref(''), formatError: e => e.message, posts: [], request: { post: async url => { scope.posts.push(url); return { data: { ...scope.tasks.value.at(-1), status: 'CANCELLED', version: 20 } } } } }
  const pendingAst = ts.createSourceFile('source.ts', storeSource, ts.ScriptTarget.Latest, true)
  let pendingDeclaration
  function find(node) {
    if (ts.isVariableDeclaration(node) && node.name.getText(pendingAst) === 'pendingWorkflow') pendingDeclaration = node.getText(pendingAst)
    ts.forEachChild(node, find)
  }
  find(pendingAst)
  const names = ['captureUiRequest', 'bindUiRequest', 'takeUiActions', 'isUiActionCurrent', 'advanceUiRevision', 'continueUiTask', 'cancelUiFeedback', 'acceptUnifiedPreview', 'applyUiFormAction', 'accept', 'cancel', 'resendTask']
  const code = functions(storeSource, names) + `\nconst ${pendingDeclaration};\n({${names.join(',')},pendingWorkflow})`
  scope.computed = computed
  const flow = vm.runInNewContext(ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, scope)
  return { scope, flow }
}
const task = { id: 'task-one', ownerKey: 'actor-one', sessionId: 'session-one', status: 'SUCCEEDED', version: 1, message: '人数设置为5，备注不变', createdAt: 100,
  runtime: { engine: 'langgraph', workflowId: 'workflow-one' }, uiActions: [{ id: 'nav-one', type: 'NAVIGATE', targetId: 'page-1' }] }
const context = { route: '/admin/student-users', revision: 3, filters: { uiTargets: [{ id: 'page-1', kind: 'page', label: '学生管理', path: '/admin/student-users' }] } }
test.beforeEach(() => { unregister(currentForm.value?.formId || 'none'); setFormSession('actor-one:session-one') })

test('only freshly submitted successful tasks deliver UI actions once; history and cancelled/awaiting snapshots cannot replay', () => {
  const f = storeFixture()
  f.flow.accept(task)
  assert.equal(f.scope.uiActionQueue.value.length, 0, 'historical task has no submit context')
  f.flow.captureUiRequest('request-one', context); f.flow.bindUiRequest(task, 'request-one')
  f.flow.accept({ ...task, status: 'RUNNING', version: 2 }); f.flow.accept({ ...task, status: 'WAITING_INPUT', version: 3 })
  assert.equal(f.scope.uiActionQueue.value.length, 0)
  f.flow.accept({ ...task, version: 4 }); f.flow.accept({ ...task, version: 5 }); f.flow.accept({ ...task, version: 5 })
  assert.equal(f.scope.uiActionQueue.value.length, 1)
  const delivery = f.flow.takeUiActions()
  assert.equal(f.flow.isUiActionCurrent(delivery), true)
  f.flow.accept({ ...task, version: 6, status: 'CANCELLED' })
  assert.equal(f.flow.isUiActionCurrent(delivery), false)
})

test('late UI actions cannot cross session, identity, or epoch', () => {
  const f = storeFixture(); f.flow.captureUiRequest('request-one', context); f.flow.bindUiRequest(task, 'request-one'); f.flow.accept(task)
  const delivery = f.flow.takeUiActions()
  f.scope.sessionId.value = 'other-session'; assert.equal(f.flow.isUiActionCurrent(delivery), false)
  f.scope.sessionId.value = task.sessionId; f.scope.capabilities.value.actor.ownerKey = 'other-user'; assert.equal(f.flow.isUiActionCurrent(delivery), false)
  const g = storeFixture(); g.flow.captureUiRequest('request-two', context); g.scope.epoch++
  g.flow.bindUiRequest(task, 'request-two'); g.flow.accept(task); assert.equal(g.scope.uiActionQueue.value.length, 0)
})

test('waiting workflow cancellation calls the real task cancellation API and accepts its terminal receipt', async () => {
  const f = storeFixture(); f.flow.accept({ ...task, status: 'WAITING_INPUT' })
  assert.equal(f.flow.pendingWorkflow.value.id, task.id)
  await f.flow.cancel(task.id)
  assert.deepEqual(f.scope.posts, ['/agent/tasks/task-one/cancel'])
  assert.equal(f.scope.tasks.value[0].status, 'CANCELLED'); assert.equal(f.flow.pendingWorkflow.value, null)
  assert.equal(f.scope.cancellingIds.value.length, 0)
})

test('a newer task or unavailable resume capability prevents stopping an obsolete waiting workflow', async () => {
  const f = storeFixture(); f.flow.accept({ ...task, status: 'WAITING_INPUT' }); f.flow.accept({ ...task, id: 'new-task', runtime: undefined })
  await f.flow.cancel(task.id); assert.equal(f.scope.posts.length, 0)
  const g = storeFixture(); g.flow.accept({ ...task, status: 'WAITING_INPUT' }); g.scope.capabilities.value.resume = false
  await g.flow.cancel(task.id); assert.equal(g.scope.posts.length, 0)
})

test('answering an intervening question preserves cancellation of only the matching original pending workflow', async () => {
  const question = { id: 'question-one', workflowId: 'workflow-one', revision: 7 }
  const waiting = { ...task, status: 'WAITING_INPUT', runtime: { ...task.runtime, pendingQuestion: question } }
  const answer = { ...task, id: 'new-task', runtime: { ...task.runtime, pendingQuestion: { ...question } } }
  const f = storeFixture(); f.flow.accept(waiting); f.flow.accept(answer)
  assert.equal(f.flow.pendingWorkflow.value.id, waiting.id)
  f.scope.request.post = async url => { f.scope.posts.push(url); return { data: { ...waiting, status: 'CANCELLED', version: 20 } } }
  await f.flow.cancel()
  assert.deepEqual(f.scope.posts, ['/agent/tasks/task-one/cancel'])
  assert.equal(f.scope.tasks.value[0].status, 'CANCELLED')
  assert.equal(f.scope.tasks.value[1].status, 'SUCCEEDED')
  assert.equal(f.scope.tasks.value[1].runtime.pendingQuestion, undefined)
  assert.equal(f.flow.pendingWorkflow.value, null)
  for (const changed of [{ id: 'other-question' }, { revision: 8 }, { workflowId: 'other-workflow' }]) {
    const g = storeFixture(); g.flow.accept(waiting); g.flow.accept({ ...answer, runtime: { ...answer.runtime, pendingQuestion: { ...question, ...changed } } })
    assert.equal(g.flow.pendingWorkflow.value, null)
    await g.flow.cancel(waiting.id); assert.equal(g.scope.posts.length, 0)
  }
})

test('editing and retrying an Agent message keeps one request identity and binds fresh UI delivery without replaying history', async () => {
  const f = storeFixture(), sent = [], fresh = { ...task, id: 'edited-task', status: 'RUNNING', version: 1 }
  f.flow.accept({ ...task, pageContext: context })
  f.scope.request.post = async (url, body) => {
    sent.push({ url, body })
    if (sent.length === 1) throw new Error('connection lost')
    return { data: fresh }
  }
  assert.equal(await f.flow.resendTask(task.id, '人数设置为6'), false)
  assert.equal(await f.flow.resendTask(task.id, '人数设置为6'), true)
  assert.equal(sent[0].body.clientRequestId, sent[1].body.clientRequestId)
  assert.equal(sent[1].url, '/agent/tasks/task-one/resend'); assert.equal(sent[1].body.operation, 'EDIT')
  assert.equal(f.scope.uiActionQueue.value.length, 0)
  f.flow.accept({ ...fresh, status: 'SUCCEEDED', version: 2 })
  assert.equal(f.scope.uiActionQueue.value.length, 1); assert.equal(f.flow.takeUiActions().task.id, fresh.id)
})

function formFixture() {
  const model = reactive({ count: 1, remark: '原备注', status: 'approved' })
  register({ formId: 'test-form', title: '岗位', fields: [{ prop: 'count', label: '人数', type: 'number' }, { prop: 'remark', label: '备注', type: 'input' }], getModel: () => model })
  const f = storeFixture('ROLE_COMPANY'); f.flow.captureUiRequest('request-one', context); f.flow.bindUiRequest(task, 'request-one')
  return { ...f, model, sent: f.scope.uiTaskContexts.get(task.id), action: { id: 'fill-one', type: 'FORM_FILL', formId: 'test-form', instanceId: currentForm.value.instanceId,
    assignments: [{ prop: 'count', value: '5', confidence: 1, overwriteAllowed: true }, { prop: 'remark', value: '模型误填', confidence: 1, overwriteAllowed: true }, { prop: 'status', value: 'other', confidence: 1 }] } }
}
test('native form validation applies the explicit field and preserves retained/undeclared values with an undo receipt', () => {
  const f = formFixture()
  assert.equal(f.flow.applyUiFormAction(task, f.action, f.sent), true)
  assert.equal(f.model.count, 5); assert.equal(f.model.remark, '原备注'); assert.equal(f.model.status, 'approved')
  const event = f.scope.fillEvents.value[0]
  assert.equal(event.sourceTaskId, task.id); assert.equal(event.assignments.length, 1); assert.ok(event.fillBatchId)
  assert.equal(event.businessStatus, 'filled'); assert.equal(f.scope.posts.length, 0, 'fill never submits a business form')
})
test('captured baseline retains native revision metadata: manual edit then revert remains protected', () => {
  const f = formFixture()
  f.model.count = 8; f.model.count = 1
  assert.equal(f.flow.applyUiFormAction(task, f.action, f.sent), true)
  assert.equal(f.model.count, 1); assert.equal(f.scope.fillEvents.value[0].applied, false)
  assert.match(f.scope.fillEvents.value[0].skippedFields[0].reason, /手动修改/)
})
test('reopening the same form and account or session changes reject a delayed fill', () => {
  const f = formFixture(), oldInstance = f.action.instanceId
  unregister('test-form'); register({ formId: 'test-form', title: '岗位', fields: [{ prop: 'count', label: '人数', type: 'number' }], getModel: () => f.model })
  assert.notEqual(currentForm.value.instanceId, oldInstance)
  assert.equal(f.flow.applyUiFormAction(task, f.action, f.sent), false); assert.equal(f.model.count, 1)
  const g = formFixture(); g.scope.capabilities.value.actor.readOnly = true
  assert.equal(g.flow.applyUiFormAction(task, g.action, g.sent), false)
  g.scope.capabilities.value.actor.readOnly = false; g.scope.sessionId.value = 'other-session'
  assert.equal(g.flow.applyUiFormAction(task, g.action, g.sent), false)
})

test('real previews open existing confirmation cards, without making configuration or create requests', () => {
  const admin = storeFixture(), preview = { actionId: 'confirmed-later', configurationVersion: 2, questions: [] }
  assert.equal(admin.flow.acceptUnifiedPreview(task, { type: 'CAPABILITIES_PREVIEW', preview }), true)
  assert.equal(admin.scope.capsFlow.value.phase, 'need_confirm'); assert.deepEqual(admin.scope.capsFlow.value.preview, preview)
  assert.equal(admin.scope.posts.length, 0)
  assert.equal(admin.flow.acceptUnifiedPreview(task, { type: 'AUTO_TASK_PREVIEW', preview }), false)
  const teacher = storeFixture('ROLE_TEACHER_COUNSELOR')
  assert.equal(teacher.flow.acceptUnifiedPreview(task, { type: 'AUTO_TASK_PREVIEW', preview: { actionId: 'auto-preview', questions: ['几点执行？'] } }), true)
  assert.equal(teacher.scope.autoFlow.value.phase, 'need_input'); assert.equal(teacher.scope.posts.length, 0)
  assert.equal(teacher.flow.acceptUnifiedPreview(task, { type: 'CAPABILITIES_PREVIEW', preview }), false)
  teacher.scope.autoTaskEnabled.value = false
  assert.equal(teacher.flow.acceptUnifiedPreview(task, { type: 'AUTO_TASK_PREVIEW', preview }), false)
})

function workspaceFixture(actions = task.uiActions) {
  const sent = JSON.parse(JSON.stringify(context)), currentTask = { ...task, uiActions: actions }
  const route = { path: sent.route, fullPath: sent.route }, calls = [], targets = sent.filters.uiTargets.map(item => ({ ...item }))
  const delivery = { task: currentTask, pageContext: sent, epoch: 1 }
  const agent = { uiActionQueue: [delivery], takeUiActions() { return this.uiActionQueue.shift() }, isUiActionCurrent: () => true, tasks: [currentTask],
    capabilities: { enabled: true, actor: { ownerKey: task.ownerKey } }, sessionId: task.sessionId, revision: sent.revision, visible: true, error: '', recordActivity() {},
    get currentForm() { return currentForm.value }, formFillEnabled: true, advanceUiRevision(value) { this.revision = Math.max(this.revision, value) + 1 },
    applyUiFormAction() { calls.push('fill'); return true }, acceptUnifiedPreview() { calls.push('preview'); return true } }
  const scope = { agent, route, auth: { token: 'token-one' }, readOnly: ref(false), uiWorking: ref(false), uiController: null, uiExpectedPath: '', uiNavigationPath: null,
    lastOf: list => list.at(-1), AbortController, collectUiTargets: () => targets, targetStillAvailable: () => true, waitForUiTargets: async () => {}, nextTick: async () => {},
    context: ref(sent), boundedUiFilters, getFormFields,
    router: { resolve: path => ({ path, fullPath: path, matched: [{}] }), push: async path => { calls.push(path); route.path = path; route.fullPath = path } } }
  const execute = vm.runInNewContext(ts.transpileModule(functions(workspace.scriptSetup.content, ['mountedUiContext', 'executeAgentUiActions']) + '\nexecuteAgentUiActions', { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, scope)
  return { agent, sent, targets, route, calls, scope, execute }
}
test('navigation uses only an original target ID, never a model supplied URL or selector', async () => {
  const f = workspaceFixture([{ id: 'nav-one', type: 'NAVIGATE', targetId: 'page-1', path: 'https://evil.invalid', selector: 'button.submit' }])
  await f.execute(); assert.deepEqual(f.calls, ['/admin/student-users']); assert.equal(f.agent.error, '')
  const g = workspaceFixture([{ id: 'nav-two', type: 'NAVIGATE', targetId: 'not-whitelisted' }])
  await g.execute(); assert.equal(g.calls.length, 0); assert.match(g.agent.error, /入口已变化/)
})
test('a changed revision, route, target role scope, or latest message blocks stale navigation', async () => {
  for (const change of [f => f.agent.revision++, f => { f.route.path = '/admin/other' }, f => { f.targets[0].path = '/company/student-users' }, f => { f.agent.tasks.push({ id: 'new-task' }) }]) {
    const f = workspaceFixture(); change(f); await f.execute(); assert.equal(f.calls.length, 0)
    if (f.agent.tasks.at(-1).id === task.id) assert.ok(f.agent.error)
    else assert.equal(f.agent.error, '', 'a superseded task must not overwrite the new message status')
  }
})
test('account change during navigation cannot continue to fill or open a confirmation card', async () => {
  const f = workspaceFixture([...task.uiActions, { id: 'fill-one', type: 'FORM_FILL' }, { id: 'preview-one', type: 'CAPABILITIES_PREVIEW' }])
  f.scope.router.push = async () => { f.calls.push('navigation'); f.scope.auth.token = 'other-account' }
  await f.execute(); assert.deepEqual(f.calls, ['navigation'])
})
test('duplicate action IDs are rejected and unsupported actions never submit business data', async () => {
  const f = workspaceFixture([task.uiActions[0], task.uiActions[0]])
  await f.execute(); assert.deepEqual(f.calls, ['/admin/student-users']); assert.match(f.agent.error, /回执无效/)
  const g = workspaceFixture([{ id: 'submit-one', type: 'FORM_SUBMIT' }])
  await g.execute(); assert.equal(g.calls.length, 0); assert.match(g.agent.error, /不支持/)
})

test('public UI context excludes DOM/model/current values and stays below backend schema and character limits', () => {
  const fields = Array.from({ length: 60 }, (_, n) => ({ prop: `p${n}`, label: `字段${n}`, type: 'select', value: '私密原值', getModel: () => {},
    options: Array.from({ length: 90 }, (_, i) => ({ value: `${i}`, label: '选项'.repeat(100), private: '私密原值' })) }))
  const targets = Array.from({ length: 130 }, (_, n) => ({ id: `page-${n}`, label: `菜单${n}`, kind: 'page', path: `/admin/page${n}`, element: { private: 'DOM' } }))
  const filters = boundedUiFilters({ keyword: '原筛选' }, targets, { formId: 'form-one', formTitle: '表单', instanceId: 1, fields, model: { secret: '原值' } })
  const encoded = JSON.stringify(filters)
  assert.ok(encoded.length <= 22000); assert.ok(filters.uiForm.fields.length <= 40); assert.ok(filters.uiTargets.length <= 100)
  assert.equal(filters.keyword, '原筛选'); assert.doesNotMatch(encoded, /私密原值|DOM|getModel|secret/)
  assert.ok(filters.uiForm.fields.length > 0); assert.ok(filters.uiForm.fields.every(field => field.options.length <= 50))
})

test('continue=true waits for a fresh mounted context, advancing revision and forwarding only descriptors', async () => {
  const f = workspaceFixture([{ ...task.uiActions[0], continue: true }]), model = reactive({ count: 1 }), feedback = []
  f.sent.filters.uiTargets[0].path = '/admin/jobs'; f.targets[0].path = '/admin/jobs'
  f.scope.waitForUiTargets = async () => {
    f.calls.push('mounted')
    register({ formId: 'new-page-form', title: '新表单', fields: [{ prop: 'count', label: '人数', type: 'number' }], getModel: () => model })
    f.targets.push({ id: 'open-new', label: '发布岗位', kind: 'open', element: { private: 'DOM' } })
  }
  f.agent.continueUiTask = async (...args) => { feedback.push(args); f.calls.push('feedback'); return true }
  await f.execute()
  assert.deepEqual(f.calls, ['/admin/jobs', 'mounted', 'feedback']); assert.equal(feedback.length, 1)
  assert.equal(feedback[0][0], task.id); assert.equal(feedback[0][1], 'nav-one')
  const next = feedback[0][2]
  assert.equal(next.route, '/admin/jobs'); assert.ok(next.revision > f.sent.revision)
  assert.equal(next.filters.uiForm.instanceId, currentForm.value.instanceId); assert.equal(next.filters.uiForm.formId, 'new-page-form')
  assert.ok(next.filters.uiTargets.some(target => target.id === 'open-new'), 'DOM candidates must refresh despite a cached computed page context')
  assert.doesNotMatch(JSON.stringify(next), /private|DOM|getModel/)
})

test('ordinary navigation does not continue; cancellation and a new message during mounting suppress continuation', async () => {
  const ordinary = workspaceFixture(); ordinary.agent.continueUiTask = async () => { throw new Error('must not continue') }; await ordinary.execute()
  for (const cancel of [f => f.scope.uiController.abort(), f => f.agent.tasks.push({ id: 'new-message' }), f => { f.scope.auth.token = 'other-user' }, f => { f.route.path = '/admin/elsewhere' }]) {
    const f = workspaceFixture([{ ...task.uiActions[0], continue: true }]), feedback = []
    f.scope.waitForUiTargets = async () => cancel(f)
    f.agent.continueUiTask = async (...args) => { feedback.push(args) }
    await f.execute(); assert.equal(feedback.length, 0)
  }
})

function continuationFixture() {
  const f = storeFixture(), parent = { ...task, pageContext: context, uiActions: [{ ...task.uiActions[0], continue: true }] }
  f.flow.captureUiRequest('parent-request', context); f.flow.bindUiRequest(parent, 'parent-request'); f.flow.accept(parent); f.flow.takeUiActions()
  const next = { ...context, route: '/admin/jobs', revision: 4, filters: { uiTargets: [{ id: 'open-1', kind: 'open', label: '发布岗位' }] } }
  const child = { ...task, id: 'child-task', parentTaskId: parent.id, responseKind: 'UI_CONTINUATION', status: 'RUNNING', pageContext: next, version: 1 }
  return { ...f, parent, next, child }
}

test('UI feedback inherits original intent without resending text; a failed request retries its identical UUID and frozen context', async () => {
  const f = continuationFixture(), requests = []
  f.scope.request.post = async (url, body) => {
    requests.push({ url, body })
    if (requests.length === 1) throw new Error('ack lost')
    return { data: f.child }
  }
  assert.equal(await f.flow.continueUiTask(task.id, 'nav-one', f.next), false)
  assert.ok(f.scope.pendingUiFeedback.value)
  assert.equal(await f.flow.continueUiTask(task.id, 'nav-one', f.next), true)
  assert.equal(requests[1].url, '/agent/tasks/task-one/ui-feedback')
  assert.equal(JSON.stringify(requests[0].body), JSON.stringify(requests[1].body)); assert.equal('message' in requests[1].body, false)
  assert.equal(requests[1].body.actionId, 'nav-one'); assert.equal(f.scope.pendingUiFeedback.value, null)
  assert.ok(f.scope.uiTaskContexts.has(f.child.id)); assert.equal(f.scope.uiActionQueue.value.length, 0)
  f.flow.accept({ ...f.child, status: 'SUCCEEDED', version: 2 })
  assert.equal(f.scope.uiActionQueue.value.length, 1)
})

test('native field baseline captured for mounted feedback preserves manual edits before the continuation reply', async () => {
  const f = continuationFixture(), model = reactive({ count: 1 })
  register({ formId: 'mounted-form', title: '岗位', fields: [{ prop: 'count', label: '人数', type: 'number' }], getModel: () => model })
  f.next.filters.uiForm = { formId: 'mounted-form', formTitle: '岗位', instanceId: currentForm.value.instanceId, fields: getFormFields() }
  f.scope.request.post = async () => ({ data: f.child })
  assert.equal(await f.flow.continueUiTask(task.id, 'nav-one', f.next), true)
  model.count = 9; model.count = 1
  const action = { id: 'fill-mounted', type: 'FORM_FILL', formId: 'mounted-form', instanceId: currentForm.value.instanceId, assignments: [{ prop: 'count', value: '5', confidence: 1, overwriteAllowed: true }] }
  f.flow.accept({ ...f.child, message: '人数设置为5', status: 'SUCCEEDED', uiActions: [action], version: 2 })
  const delivery = f.flow.takeUiActions()
  assert.equal(f.flow.applyUiFormAction(delivery.task, action, delivery), true)
  assert.equal(model.count, 1); assert.equal(f.scope.fillEvents.value[0].applied, false)
  assert.match(f.scope.fillEvents.value[0].skippedFields[0].reason, /手动修改/)
})

test('feedback rejects missing continue, newer user intent, identity changes, stale retry contexts, and the fourth hop', async () => {
  for (const change of [f => { f.parent.uiActions[0].continue = false }, f => { f.scope.uiTurn++ }, f => { f.scope.tasks.value.push({ id: 'new-message' }) }, f => { f.scope.capabilities.value.actor.ownerKey = 'other-owner' }]) {
    const f = continuationFixture(); change(f)
    assert.equal(await f.flow.continueUiTask(task.id, 'nav-one', f.next), false); assert.equal(f.scope.posts.length, 0)
  }
  const f = continuationFixture(); f.scope.request.post = async () => { throw new Error('ack lost') }
  await f.flow.continueUiTask(task.id, 'nav-one', f.next)
  assert.equal(await f.flow.continueUiTask(task.id, 'nav-one', { ...f.next, revision: 5 }), false)
  assert.match(f.scope.error.value, /页面或表单已变化/)
  const g = continuationFixture()
  const chain = [g.parent, ...[1, 2, 3].map(n => ({ ...g.parent, id: `child-${n}`, parentTaskId: n === 1 ? task.id : `child-${n - 1}`, responseKind: 'UI_CONTINUATION' }))]
  g.scope.tasks.value = chain; g.flow.captureUiRequest('latest-request', g.next); g.flow.bindUiRequest(chain[3], 'latest-request')
  assert.equal(await g.flow.continueUiTask(chain[3].id, 'nav-one', g.next), false); assert.equal(g.scope.posts.length, 0); assert.match(g.scope.error.value, /上限/)
})

test('cancelling a submitted feedback reconciles the owned child and cancels it without accepting late UI proposals', async () => {
  const f = continuationFixture(), requests = []; let finish
  f.scope.request.get = async url => { requests.push(url); return { data: { status: 'FOUND', task: f.child } } }
  f.scope.request.post = async (url, body, options) => {
    requests.push(url)
    if (url.endsWith('/ui-feedback')) return new Promise(resolve => { finish = () => resolve({ data: { ...f.child, status: 'SUCCEEDED' } }); assert.equal(options.signal.aborted, false) })
    return { data: { ...f.child, status: 'CANCELLED', version: 2 } }
  }
  const running = f.flow.continueUiTask(task.id, 'nav-one', f.next)
  await f.flow.cancel(); await new Promise(setImmediate); finish()
  assert.equal(await running, false); await new Promise(setImmediate)
  assert.ok(requests.some(url => url.startsWith('/agent/requests/')))
  assert.ok(requests.includes('/agent/tasks/child-task/cancel'))
  assert.equal(f.scope.uiActionQueue.value.length, 0); assert.equal(f.scope.pendingUiFeedback.value, null)
  assert.equal(f.scope.tasks.value.find(item => item.id === f.child.id).status, 'CANCELLED')
})

test('continuation replies do not replace the original answer or duplicate the user bubble', () => {
  const ast = ts.createSourceFile('workspace.ts', workspace.scriptSetup.content, ts.ScriptTarget.Latest, true)
  const timeline = ast.statements.find(node => ts.isVariableStatement(node) && node.declarationList.declarations.some(item => item.name.getText(ast) === 'timeline'))
  const scope = { agent: { tasks: [{ ...task, answer: '原回复' }, { ...task, id: 'child', parentTaskId: task.id, responseKind: 'UI_CONTINUATION', answer: '续接回复' }], fillEvents: [], activityEvents: [], autoFlow: { phase: 'idle' }, capsFlow: { phase: 'idle' } },
    computed, selectedVersions: ref({}), taskVersions: (tasks, task) => tasks.filter(item => item.id === task.id) }
  const code = functions(workspace.scriptSetup.content, ['isUiContinuation', 'chainOf']) + '\n' + timeline.getText(ast) + ';\n({timeline,isUiContinuation})'
  const view = vm.runInNewContext(ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, scope)
  assert.equal(view.timeline.value.length, 2)
  assert.deepEqual([...view.timeline.value].map(item => item.task.answer), ['原回复', '续接回复'])
  assert.equal(view.isUiContinuation(scope.agent.tasks[1]), true); assert.equal(view.isUiContinuation(task), false)
  assert.match(workspace.template.content, /v-else-if="!isUiContinuation\(task\)"/)
})
