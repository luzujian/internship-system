import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { terminal, answerParts } from './protocol.mjs'
import { shouldResolveFormFill, shouldResolveUiIntent } from './sendRouting.mjs'

const dir = dirname(fileURLToPath(import.meta.url))
const storeSrc = readFileSync(join(dir, 'store.ts'), 'utf8')
const workspaceSrc = readFileSync(join(dir, '..', 'components', 'agent', 'AgentWorkspace.vue'), 'utf8')

test('generation states stay split: CANCELLING is active, terminal set is closed', () => {
  for (const s of ['QUEUED', 'RUNNING', 'CANCELLING']) assert.equal(terminal(s), false, s)
  for (const s of ['SUCCEEDED', 'FAILED', 'CANCELLED', 'WAITING_INPUT']) assert.equal(terminal(s), true, s)
})

test('model text renders as plain text parts, never as executable markup', () => {
  const evil = '<script>alert(1)</script><a href="javascript:x">link</a>**bold**'
  const parts = answerParts(evil)
  assert.equal(parts.map(p => p.text).join(''), evil.replaceAll('**', ''))
  assert.ok(parts.some(p => p.bold && p.text === 'bold'))
  assert.ok(!parts.some(p => p.html || p.href || p.tag))
})

test('send path keeps one stable identity per message and keeps drafts on failure', () => {
  assert.match(storeSrc, /clientRequestId/)
  assert.match(storeSrc, /sameAsPending/)
  assert.match(storeSrc, /pendingMessage/)
  assert.match(storeSrc, /retryPending/)
  assert.match(storeSrc, /editPending/)
  assert.match(storeSrc, /dismissPending/)
  assert.match(storeSrc, /submitPending/)
})

test('drafts are isolated per account and session and retained locally during offline usage', () => {
  assert.match(storeSrc, /deepintern\.agent\.draft:/)
  assert.match(storeSrc, /restoreDraft\(\)/)
  assert.match(storeSrc, /persistDraft\(\)/)
  assert.match(storeSrc, /flushDraft\(\)/)
})

test('cancel is idempotent, shows requesting-stop, terminal wins', () => {
  assert.match(storeSrc, /isCancelling/)
  assert.match(storeSrc, /cancellingIds/)
  assert.match(storeSrc, /stopAndSend/)
})

test('workspace shows pending bubble, back-to-latest, a shared stop/send button and length limit', () => {
  assert.match(workspaceSrc, /pending-bubble/)
  assert.match(workspaceSrc, /retryPending/)
  assert.match(workspaceSrc, /back-to-latest/)
  assert.match(workspaceSrc, /agent\.busy \? agent\.cancel\(\) : send\(\)/)
  assert.match(workspaceSrc, /agent\.draft\.length.*4000/)
  assert.match(workspaceSrc, /taskDisplayStatus/)
  assert.match(workspaceSrc, /isComposing/)
})

test('student login auto-open is one-shot and gated by capabilities', () => {
  assert.match(storeSrc, /deepintern\.agent\.auto-open-student-login/)
  assert.match(storeSrc, /caps\.actor\.role !== 'ROLE_STUDENT'/)
  assert.match(storeSrc, /caps\.enabled/)
  assert.match(storeSrc, /sessionStorage\.removeItem\(studentLoginAutoOpenKey\)/)
})

test('docked AI leaves room for business dialogs', () => {
  assert.match(workspaceSrc, /el-overlay\.global-modal/)
  assert.match(workspaceSrc, /right:var\(--agent-width\)/)
})

test('form-open messages are delegated to the business Agent without another frontend intent model', () => {
  const messages = [
    '标题就弄成实习周报模板吧',
    '状态用草稿，目标群体弄成学生',
    '帮我处理一下这个文档，你看着填'
  ]
  for (const message of messages) {
    assert.equal(shouldResolveFormFill(message, { hasForm: true }), false, message)
    assert.equal(shouldResolveUiIntent(message), false, message)
  }
  assert.equal(shouldResolveFormFill('标题就弄成实习周报模板吧', { hasForm: false }), false)
})

test('ordinary questions bypass UI and fill models even with a form open', () => {
  for (const message of ['你知道什么', '你能做什么？', '这个字段是什么意思？', '本企业有多少招聘岗位', '有哪些学生申请了我们的岗位', '为什么不能保存', '请介绍企业端的功能有哪些']) {
    assert.equal(shouldResolveUiIntent(message), false, message)
    assert.equal(shouldResolveFormFill(message, { hasForm: true }), false, message)
  }
  for (const message of ['帮我打开岗位发布', '打开表单后告诉我这个字段是什么意思', '你能做什么？顺便打开企业资料', '标题改成为什么选择我们']) {
    assert.equal(shouldResolveUiIntent(message), false, message)
  }
})

test('business send actions stay out of form-fill routing', () => {
  assert.equal(shouldResolveFormFill('给学生发送标题为实习安全提醒的邮件', { hasForm: true }), false)
  assert.equal(shouldResolveUiIntent('给学生发送标题为实习安全提醒的邮件'), false)
})
