import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'
import { shallowRef } from 'vue'
import { shouldResolveFormFill, shouldResolveUiIntent, isExplicitUiAction } from '../src/agent/sendRouting.mjs'

const { descriptor } = parse(readFileSync(new URL('../src/components/agent/AgentWorkspace.vue', import.meta.url), 'utf8'))
const ast = ts.createSourceFile('workspace.ts', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true)
const sendSource = ast.statements.find(node => ts.isFunctionDeclaration(node) && node.name?.text === 'send').getText(ast)
test('pending messages occupy the conversation, independently of confirmation dialogs', () => {
  const hasClass = (node, name) => node.props?.some(prop => prop.type === 6 && prop.name === 'class' && prop.value?.content.split(' ').includes(name))
  let parents
  const visit = (node, ancestors = []) => {
    if (hasClass(node, 'pending-bubble')) parents = ancestors
    for (const child of node.children || []) visit(child, [...ancestors, node])
  }
  visit(descriptor.template.ast)
  assert.ok(parents?.some(node => hasClass(node, 'agent-conversation')))
  assert.ok(!parents.some(node => hasClass(node, 'apply-dialog')))
})
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }
test('questions and actions all bypass separate frontend intent models', () => {
  for (const message of ['发布岗位的时候可以选择招多少学生吗', '发布岗位时可以选择不同联系人吗', '招聘人数在哪里填写？', '怎么选择薪资单位？',
    '可以帮我选择联系人吗', '请你打开岗位发布', '怎么发布？然后帮我打开发布页面', '招聘人数设置为5', '选中第二位联系人']) {
    assert.equal(shouldResolveUiIntent(message), false, message)
    assert.equal(shouldResolveFormFill(message, { hasForm: true }), false, message)
  }
})
import { isImportRequest } from '../src/components/import/smartImport.mjs'
function fixture(message, role) {
  const calls = [], accepted = deferred(), routed = deferred()
  const agent = { visible: true, capabilities: { enabled: true, actor: { ownerKey: 'company-7', role } }, sessionId: 'session-1',
    draft: message, error: '', busy: false, loading: false, pendingMessage: null, capsFlow: { phase: 'idle' }, autoFlow: { phase: 'idle' },
    ask: async (_context, text) => { calls.push('ask'); agent.busy = true; agent.pendingMessage = { message: text, state: 'sending' }; await accepted.promise; agent.pendingMessage = null; agent.busy = false } }
  const scope = { agent, calls, auth: { token: 'company-token' }, readOnly: { value: false }, linkedForm: { value: null },
    uiWorking: { value: false }, voiceFinishing: { value: false }, routingMessage: shallowRef(null), atBottom: { value: false },
    canManageCapabilities: { value: false }, canAutomate: { value: false }, context: { value: { route: '/company/home' } },
    nextTick: async () => {}, scrollBottom() {}, scrollToLatest() {}, shouldResolveFormFill, shouldResolveUiIntent, isExplicitUiAction,
    handleUiRequest: async () => { calls.push('ui'); return routed.promise } }
  agent.ensureSession = async () => { calls.push('session') }
  scope.importConversation = { working: false, attached: false, draftAttachments: [], canHandle: () => scope.importConversation.attached,
    send: async () => { calls.push('import-turn'); agent.draft = '' } }
  scope.isImportRequest = isImportRequest
  const send = vm.runInNewContext(ts.transpileModule(sendSource + '\nsend', { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, scope)
  return { agent, scope, calls, accepted, routed, send }
}

test('click clears the composer and shows the question before any asynchronous routing', async () => {
  const f = fixture('你知道什么')
  const sending = f.send()
  assert.equal(f.agent.draft, '')
  assert.equal(f.scope.routingMessage.value.message, '你知道什么')
  assert.deepEqual(f.calls, [])
  await new Promise(setImmediate)
  assert.deepEqual(f.calls, ['ask'])
  assert.equal(f.scope.routingMessage.value, null, 'handoff must not leave a duplicate local bubble')
  assert.equal(f.agent.pendingMessage.message, '你知道什么')
  f.accepted.resolve(); await sending
  assert.equal(f.scope.routingMessage.value, null)
})

test('an operation message goes directly to the agent and prevents double submission', async () => {
  const f = fixture('打开企业资料')
  const sending = f.send()
  assert.equal(f.agent.draft, '')
  await new Promise(setImmediate)
  assert.equal(f.agent.pendingMessage.message, '打开企业资料')
  await f.send('打开企业资料')
  assert.deepEqual(f.calls, ['ask'])
  f.accepted.resolve(); await sending
  assert.equal(f.scope.routingMessage.value, null)
})

test('failed submission restores the message without replacing a new draft', async () => {
  for (const nextDraft of ['', '下一条问题']) {
    const f = fixture('打开企业资料')
    f.agent.ask = async () => { f.calls.push('ask'); await f.accepted.promise; f.agent.error = '请求发送失败' }
    const sending = f.send(); await new Promise(setImmediate)
    f.agent.draft = nextDraft
    f.accepted.resolve(); await sending
    assert.equal(f.agent.draft, nextDraft || '打开企业资料')
    assert.equal(f.scope.routingMessage.value, null)
  }
})

test('changing accounts before routing never sends or restores the previous account message', async () => {
  const f = fixture('你知道什么')
  const sending = f.send()
  f.scope.auth.token = 'another-account'
  f.agent.capabilities.actor.ownerKey = 'student-9'
  await sending
  assert.deepEqual(f.calls, [])
  assert.equal(f.agent.draft, '')
  assert.equal(f.scope.routingMessage.value, null)
})

test('administrator import requests without files use the shared agent to ask for an attachment', async () => {
  const f = fixture('帮我导入学生名单', 'ROLE_ADMIN')
  const sending = f.send(); await new Promise(setImmediate)
  assert.deepEqual(f.calls, ['ask']); f.accepted.resolve(); await sending; assert.equal(f.agent.draft, '')
})

test('administrator messages with attached workbooks go to import recognition', async () => {
  const f = fixture('这些都是2023级学生', 'ROLE_ADMIN'); f.scope.importConversation.attached = true
  await f.send(); assert.deepEqual(f.calls, ['session', 'import-turn'])
})

test('sending immediately places a copy of the selected attachments beside the outgoing message', async () => {
  const f = fixture('导入这两份名单', 'ROLE_ADMIN'), opening = deferred()
  f.scope.importConversation.attached = true
  f.scope.importConversation.draftAttachments = [{ name: '甲.xlsx', size: 1200 }, { name: '乙.xls', size: 900 }]
  f.agent.ensureSession = () => opening.promise
  const sending = f.send()
  assert.equal(f.agent.draft, '')
  assert.equal(f.scope.routingMessage.value.attachments[0].name, '甲.xlsx')
  f.scope.importConversation.draftAttachments[0].name = '之后选择.xlsx'
  assert.equal(f.scope.routingMessage.value.attachments[0].name, '甲.xlsx')
  opening.resolve(); await sending
})
test('an attached workbook and navigation message use the same shared agent path', async () => {
  const f = fixture('打开公告管理页面', 'ROLE_ADMIN'); f.scope.importConversation.attached = true
  await f.send(); assert.deepEqual(f.calls, ['session', 'import-turn'])
  assert.ok(!f.calls.includes('ui'))
})

test('switching accounts while opening the import session never uploads the previous account file', async () => {
  const f = fixture('帮我导入名单', 'ROLE_ADMIN'); const session = deferred()
  f.scope.importConversation.attached = true
  f.agent.ensureSession = () => session.promise
  const sending = f.send(); f.scope.auth.token = 'other-token'; session.resolve(); await sending
  assert.deepEqual(f.calls, [])
})

test('busy imports cannot create a second preview', async () => {
  const f = fixture('帮我导入名单', 'ROLE_ADMIN'); f.scope.importConversation.working = true
  await f.send(); assert.deepEqual(f.calls, [])
})
test('plain chat answers continue the existing import context without an import keyword', async () => {
  for (const message of ['测试乙的学号是0012345', '用第一个班级', '年级改为23级', '先等等，我核对一下', '好的']) {
    const f = fixture(message, 'ROLE_ADMIN'); f.scope.importConversation.attached = true
    await f.send(); assert.deepEqual(f.calls, ['session', 'import-turn'], message)
  }
})
test('other roles continue normal chat even when an import document is mentioned', async () => {
  const f = fixture('帮我导入学生名单', 'ROLE_TEACHER_COUNSELOR')
  const sending = f.send(); await new Promise(setImmediate)
  assert.ok(!f.calls.includes('import-turn')); assert.ok(f.calls.includes('ask'))
  f.accepted.resolve(); await sending
})
