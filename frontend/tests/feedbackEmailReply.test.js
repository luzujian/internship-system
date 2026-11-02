import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'

const source = readFileSync(new URL('../src/views/admin/FeedbackManagement.vue', import.meta.url), 'utf8')
const script = parse(source).descriptor.scriptSetup.content
const ast = ts.createSourceFile('FeedbackManagement.ts', script, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const ref = value => ({ value })
const noop = () => {}
function actual(name, scope) {
  const declaration = ast.statements.filter(ts.isVariableStatement).flatMap(row => [...row.declarationList.declarations]).find(row => row.name.getText(ast) === name)
  assert.ok(declaration, name)
  const js = ts.transpileModule(`(${declaration.initializer.getText(ast)})`, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
  return vm.runInNewContext(js, scope)
}
function fixture(status = 'SENT') {
  const receipts = [], writes = [], messages = []
  const scope = {
    replyForm: { id: 7, title: '反馈标题', content: '问题', adminReply: '  管理员回复  ', status: 'resolved' },
    replyFormRef: ref({ validate: async () => true }), replyContext: ref({ recipientEmail: 'student@example.com', mailConfigured: true, feedbackVersion: 1000, history: [] }),
    replySending: ref(false), replyLoading: ref(false), replyUncertain: ref(false), replyRequestId: ref('original-id'), replyNotice: ref(''), replyDialogVisible: ref(true),
    computed: getter => ({ get value() { return getter() } }), crypto: { randomUUID: () => 'next-id' },
    formContext: { beginFormOperation() { return { complete: receipt => receipts.push(receipt) } } },
    ElMessage: { success: text => messages.push(['success', text]), error: text => messages.push(['error', text]), warning: text => messages.push(['warning', text]) },
    logger: { error: noop }, fetchData: noop, refreshReplyContext: async () => {},
    problemFeedbackApi: { replyFeedback: async (id, payload) => { writes.push({ id, ...payload }); return { code: 200, data: { deliveryStatus: status, deliveryDetail: status === 'SENT' ? '邮件服务已受理' : '发送失败或待确认' } } } }
  }
  scope.canSendReply = actual('canSendReply', scope)
  return { scope, receipts, writes, messages, send: () => actual('handleReplySubmit', scope)() }
}

test('only confirmed server acceptance creates a success receipt; dialog retains visible result', async () => {
  const ctx = fixture()
  await ctx.send()
  assert.equal(ctx.receipts.length, 1)
  assert.equal(ctx.messages[0][0], 'success')
  assert.equal(ctx.scope.replyDialogVisible.value, true)
  assert.equal(ctx.writes[0].recipientEmail, 'student@example.com')
  assert.equal(ctx.writes[0].requestId, 'original-id')
  assert.equal(ctx.writes[0].adminReply, '管理员回复')
  assert.equal('adminId' in ctx.writes[0], false)
})

for (const status of ['FAILED', 'UNKNOWN', 'SENDING']) {
  test(`${status} never reports a successful send or resets the reply draft`, async () => {
    const ctx = fixture(status)
    await ctx.send()
    assert.equal(ctx.receipts.length, 0)
    assert.equal(ctx.scope.replyForm.adminReply, '  管理员回复  ')
    assert.equal(ctx.messages.some(([type]) => type === 'success'), false)
    if (status === 'UNKNOWN') assert.equal(ctx.scope.replyUncertain.value, true)
  })
}

test('network failure keeps request ID and blocks resending until records are checked', async () => {
  const ctx = fixture()
  ctx.scope.problemFeedbackApi.replyFeedback = async () => { throw new Error('connection lost') }
  await ctx.send()
  assert.equal(ctx.receipts.length, 0)
  assert.equal(ctx.scope.replyRequestId.value, 'original-id')
  assert.equal(ctx.scope.replyUncertain.value, true)
  assert.equal(ctx.scope.canSendReply.value, false)
})

test('double click starts one SMTP request even when form validation is asynchronous', async () => {
  const ctx = fixture()
  let release
  const gate = new Promise(resolve => release = resolve)
  const write = ctx.scope.problemFeedbackApi.replyFeedback
  ctx.scope.problemFeedbackApi.replyFeedback = async (...args) => { await gate; return write(...args) }
  const first = ctx.send()
  await Promise.resolve()
  const second = ctx.send()
  release()
  await Promise.all([first, second])
  assert.equal(ctx.writes.length, 1)
})

test('recipient, configuration, history state, and duplicate content control the send button', () => {
  const ctx = fixture(), scope = ctx.scope
  assert.equal(scope.canSendReply.value, true)
  for (const address of ['', 'not-email']) {
    scope.replyContext.value.recipientEmail = address
    assert.equal(scope.canSendReply.value, false)
  }
  scope.replyContext.value.recipientEmail = 'student@example.com'
  scope.replyContext.value.mailConfigured = false
  assert.equal(scope.canSendReply.value, false)
  scope.replyContext.value.mailConfigured = true
  for (const state of ['SENDING', 'UNKNOWN', 'SENT']) {
    scope.replyContext.value.history = [{ deliveryStatus: state, replyContent: '管理员回复', recipientEmail: 'student@example.com' }]
    assert.equal(scope.canSendReply.value, false)
  }
  scope.replyContext.value.history[0].deliveryStatus = 'FAILED'
  assert.equal(scope.canSendReply.value, true)
})

test('checking records preserves draft and recognizes a completed uncertain request', async () => {
  const ctx = fixture(), scope = ctx.scope
  scope.replyUncertain.value = true
  scope.problemFeedbackApi.getFeedbackEmailContext = async () => ({ code: 200, data: {
    recipientEmail: 'student@example.com', mailConfigured: true, feedbackVersion: 2000,
    feedback: { title: '当前标题', content: '当前反馈', status: 'processing' },
    history: [{ id: 41, requestId: 'original-id', deliveryStatus: 'FAILED', deliveryDetail: '连接失败' }]
  } })
  await actual('refreshReplyContext', scope)()
  assert.equal(scope.replyUncertain.value, false)
  assert.equal(scope.replyForm.adminReply, '  管理员回复  ')
  assert.equal(scope.replyForm.status, 'resolved')
  assert.equal(scope.replyRequestId.value, 'next-id')
  assert.equal(scope.replyContext.value.feedbackVersion, 2000)
})

test('context lookup failure disables sending and never discards draft', async () => {
  const ctx = fixture(), scope = ctx.scope
  scope.problemFeedbackApi.getFeedbackEmailContext = async () => { throw new Error('service unavailable') }
  await actual('refreshReplyContext', scope)()
  assert.equal(scope.replyUncertain.value, true)
  assert.equal(scope.canSendReply.value, false)
  assert.equal(scope.replyForm.adminReply, '  管理员回复  ')
})
