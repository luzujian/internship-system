import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'
import { shallowRef } from 'vue'
import { shouldResolveFormFill, shouldResolveUiIntent } from '../src/agent/sendRouting.mjs'

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
test('questions about selecting and filling fields bypass the operation model', () => {
  for (const message of ['发布岗位的时候可以选择招多少学生吗', '发布岗位时可以选择不同联系人吗', '招聘人数在哪里填写？', '怎么选择薪资单位？']) {
    assert.equal(shouldResolveUiIntent(message), false, message)
    assert.equal(shouldResolveFormFill(message, { hasForm: true }), false, message)
  }
  for (const message of ['可以帮我选择联系人吗', '请你打开岗位发布', '怎么发布？然后帮我打开发布页面', '招聘人数设置为5', '选中第二位联系人'])
    assert.equal(shouldResolveUiIntent(message), true, message)
})
function fixture(message) {
  const calls = [], accepted = deferred(), routed = deferred()
  const agent = { visible: true, capabilities: { enabled: true, actor: { ownerKey: 'company-7' } }, sessionId: 'session-1',
    draft: message, error: '', busy: false, loading: false, pendingMessage: null, capsFlow: { phase: 'idle' }, autoFlow: { phase: 'idle' },
    ask: async (_context, text) => { calls.push('ask'); agent.pendingMessage = { message: text, state: 'sending' }; await accepted.promise; agent.pendingMessage = null } }
  const scope = { agent, calls, auth: { token: 'company-token' }, readOnly: { value: false }, linkedForm: { value: null },
    uiWorking: { value: false }, voiceFinishing: { value: false }, routingMessage: shallowRef(null), atBottom: { value: false },
    canManageCapabilities: { value: false }, canAutomate: { value: false }, context: { value: { route: '/company/home' } },
    nextTick: async () => {}, scrollBottom() {}, scrollToLatest() {}, shouldResolveFormFill, shouldResolveUiIntent,
    handleUiRequest: async () => { calls.push('ui'); return routed.promise } }
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

test('slow UI interpretation keeps immediate feedback and prevents double submission', async () => {
  const f = fixture('打开企业资料')
  const sending = f.send()
  assert.equal(f.agent.draft, '')
  await Promise.resolve()
  assert.equal(f.scope.routingMessage.value.message, '打开企业资料')
  await f.send('打开企业资料')
  assert.deepEqual(f.calls, ['ui'])
  f.routed.resolve(true); await sending
  assert.equal(f.scope.routingMessage.value, null)
})

test('failed interpretation restores the message without replacing a new draft', async () => {
  for (const nextDraft of ['', '下一条问题']) {
    const f = fixture('打开企业资料')
    const sending = f.send(); await Promise.resolve()
    f.agent.draft = nextDraft
    f.agent.error = '页面识别失败'
    f.routed.resolve(true); await sending
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
