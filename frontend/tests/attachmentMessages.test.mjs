import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { ref, createSSRApp } from 'vue'
import { parse, compileScript } from '@vue/compiler-sfc'
import { renderToString } from 'vue/server-renderer'
import { createRequire } from 'node:module'

const source = fs.readFileSync(new URL('../src/agent/store.ts', import.meta.url), 'utf8')
const ast = ts.createSourceFile('store.ts', source, ts.ScriptTarget.Latest, true)
const names = new Set(['attachmentPreview', 'recordTaskAttachments', 'attachmentsForTask', 'ask', 'persistPending', 'recoverPending', 'submitPending', 'retryPending', 'saveEvent', 'persistEvents', 'restoreEvents'])
const declarations = []
function visit(node) {
  if (ts.isFunctionDeclaration(node) && names.has(node.name?.text)) declarations.push(node.getText(ast))
  ts.forEachChild(node, visit)
}
visit(ast)
const code = ts.transpileModule(`let pending = null;\n${declarations.join('\n')}\n({ask, retryPending, recoverPending, restoreEvents, attachmentsForTask, recordTaskAttachments, getPending:()=>pending})`, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
const preview = { contextId: 'context-one', files: [{ name: '学生名单.xlsx', size: 2048 }] }
const context = { route: '/admin/student-users' }
const task = { id: 'task-one', ownerKey: 'admin-one', sessionId: 'session-one', attachmentContextId: 'context-one', createdAt: 123, status: 'SUCCEEDED' }
function fixture(saved = new Map(), remote = []) {
  let requestId = 0
  const posts = [], accepted = []
  const localStorage = { getItem: key => saved.get(key) ?? null, setItem: (key, value) => saved.set(key, value), removeItem: key => saved.delete(key) }
  const scope = { busy: ref(false), chatEnabled: ref(true), capabilities: ref({ enabled: true, actor: { ownerKey: 'admin-one' } }), sessionId: ref('session-one'),
    pendingMessage: ref(null), draft: ref(''), error: ref(''), activityEvents: ref([]), fillEvents: ref([]), persistent: ref(true), sending: ref(false), epoch: 0,
    localStorage, sessionStorage: localStorage, newRequestId: () => `request-${++requestId}`, draftKey: () => 'draft-one', storageKey: () => 'session-key',
    uiRequestContexts: new Map(), uiTurn: 0, pendingUiFeedback: ref(null), captureUiRequest() {}, bindUiRequest() {},
    eventKey: () => `events:${scope.capabilities.value.actor.ownerKey}:${scope.sessionId.value}`, persistDraft() {}, listen() {}, cancelAfterSend: false, cancel() {}, loadSessions() {}, refreshCapabilities() {}, terminal: status => status === 'SUCCEEDED', formatError: error => error.message,
    accept: value => accepted.push(value), request: { post: async (url, body) => { posts.push({ url, body }); return { data: task } }, get: async url => ({ data: url.endsWith('/events') ? remote : { status: 'FOUND', task } }) } }
  const flow = vm.runInNewContext(code, scope)
  return { flow, scope, posts, accepted, saved }
}

test('attachments travel through pending state and become a single durable record on the accepted message', async () => {
  const f = fixture(); let accept
  f.scope.request.post = async (url, body) => { f.posts.push({ url, body }); if (url === '/agent/tasks') return new Promise(resolve => { accept = resolve }); return {} }
  const sending = f.flow.ask(context, '请导入', preview)
  assert.equal(f.scope.pendingMessage.value.attachments[0].name, '学生名单.xlsx')
  assert.equal('attachmentPreview' in f.posts[0].body, false, 'display metadata must not change the task API contract')
  accept({ data: task }); await sending
  assert.equal(f.scope.pendingMessage.value, null)
  assert.equal(f.flow.attachmentsForTask(task.id)[0].size, 2048)
  f.flow.recordTaskAttachments(task, preview)
  assert.equal(f.scope.activityEvents.value.length, 1)
  assert.equal(f.posts.filter(post => post.url.endsWith('/events')).length, 1)
  const restored = fixture(f.saved); await restored.flow.restoreEvents()
  assert.equal(restored.flow.attachmentsForTask(task.id)[0].name, '学生名单.xlsx')
  const remote = fixture(new Map(), [{ id: task.id, kind: 'IMPORT', payload: f.scope.activityEvents.value[0] }])
  await remote.flow.restoreEvents()
  assert.equal(remote.flow.attachmentsForTask(task.id)[0].name, '学生名单.xlsx')
})

test('failed submission and refresh retain file cards, while retry reconciles the existing task', async () => {
  const f = fixture()
  f.scope.request.post = async () => { throw new Error('connection lost') }
  await f.flow.ask(context, '请导入', preview)
  assert.equal(f.scope.pendingMessage.value.state, 'failed')
  assert.equal(f.scope.pendingMessage.value.attachments[0].name, '学生名单.xlsx')
  const restored = fixture(f.saved); await restored.flow.recoverPending()
  assert.equal(restored.scope.pendingMessage.value.attachments[0].name, '学生名单.xlsx')
  await restored.flow.retryPending()
  assert.equal(restored.flow.attachmentsForTask(task.id)[0].name, '学生名单.xlsx')
  assert.equal(restored.posts.filter(post => post.url === '/agent/tasks').length, 0)
})

test('attachment display cannot bind another owner, session, or attachment context to a message', () => {
  const f = fixture()
  for (const changed of [{ ownerKey: 'other-admin' }, { sessionId: 'other-session' }, { attachmentContextId: 'other-files' }])
    f.flow.recordTaskAttachments({ ...task, ...changed }, preview)
  assert.equal(f.scope.activityEvents.value.length, 0)
})

test('switching identity before task acceptance does not retain outgoing file names', async () => {
  const f = fixture(); let accept
  f.scope.request.post = async () => new Promise(resolve => { accept = resolve })
  const sending = f.flow.ask(context, '请导入', preview)
  f.scope.epoch++; f.scope.capabilities.value.actor.ownerKey = 'other-admin'
  accept({ data: task }); await sending
  assert.equal(f.scope.activityEvents.value.length, 0); assert.equal(f.accepted.length, 0)
})

test('plain follow-up messages have no duplicate attachment cards', async () => {
  const f = fixture(); await f.flow.ask(context, '请导入', preview)
  f.scope.request.post = async (url, body) => { f.posts.push({ url, body }); return { data: { ...task, id: 'task-two' } } }
  await f.flow.ask(context, '专业是生物医学工程')
  assert.equal(f.flow.attachmentsForTask('task-two').length, 0)
  assert.equal(f.flow.attachmentsForTask('task-one').length, 1)
})

test('file cards render names as plain text and show the actual size', async () => {
  const component = fs.readFileSync(new URL('../src/components/agent/MessageAttachments.vue', import.meta.url), 'utf8')
  const { descriptor } = parse(component)
  const compiled = compileScript(descriptor, { id: 'attachment-render-test', inlineTemplate: true })
  const module = { exports: {} }
  vm.runInNewContext(ts.transpileModule(compiled.content, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText, { module, exports: module.exports, require: createRequire(import.meta.url) })
  const html = await renderToString(createSSRApp(module.exports.default, { files: [{ name: '<script>bad</script>.xlsx', size: 2048 }] }))
  assert.match(html, /&lt;script&gt;bad&lt;\/script&gt;\.xlsx/); assert.match(html, /2 KB/)
  assert.doesNotMatch(html, /<script>/)
})
