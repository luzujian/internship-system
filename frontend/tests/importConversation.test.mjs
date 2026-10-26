import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import fs from 'node:fs'
import ts from 'typescript'
import { ref, computed, reactive, watch } from 'vue'

const code = fs.readFileSync(new URL('../src/components/import/useImportConversation.ts', import.meta.url), 'utf8').replace(/^import .*$/gm, '').replace('export function', 'function')
const deferred = () => { let resolve, reject; const promise = new Promise((ok, fail) => { resolve = ok; reject = fail }); return { promise, resolve, reject } }
function fixture() {
  const posts = [], deletes = [], saved = new Map(), asked = []
  const auth = reactive({ token: 'token-one' })
  const agent = reactive({ capabilities: { engine: 'langgraph', attachmentImport: true, resume: true, actor: { role: 'ROLE_ADMIN', ownerKey: 'admin-one', readOnly: false } }, sessionId: 'session-one', draft: '', error: '', busy: false, sessions: [], tasks: [],
    ask: async (context, message, attachments) => {
      asked.push({ context, message, attachments })
      agent.tasks.push({ id: `task-${asked.length}`, attachmentContextId: attachments?.contextId })
    } })
  const request = { post: async (url, body) => { posts.push({ url, body }); return { data: attached } }, get: async () => ({ data: attached }), delete: async (url, config) => deletes.push({ url, config }) }
  const scope = { ref, computed, reactive, watch, onBeforeUnmount() {}, request, showInitialCredentials: async () => {}, FormData, AbortController,
    sessionStorage: { getItem: key => saved.get(key) ?? null, setItem: (key, value) => saved.set(key, value), removeItem: key => saved.delete(key) } }
  const run = vm.runInNewContext(ts.transpileModule(code + '\nuseImportConversation', { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, scope)
  const flow = run(agent, auth)
  return { flow, agent, auth, request, posts, deletes, saved, asked }
}
const attached = { contextId: 'context-one', phase: 'ATTACHED', files: ['名单.xlsx'] }
const context = { route: '/admin/dashboard' }
function choose(flow) { flow.choose({ target: { files: [new File(['data'], '名单.xlsx')] } }) }

test('changing accounts during upload cannot submit the previous account attachment', async () => {
  const f = fixture(), upload = deferred(); choose(f.flow)
  f.request.post = async url => { f.posts.push(url); return upload.promise }
  const sending = f.flow.send('导入这份名单', context)
  f.auth.token = 'token-two'; f.agent.capabilities.actor.ownerKey = 'admin-two'
  upload.resolve({ data: attached }); await sending
  assert.equal(f.posts.length, 1); assert.equal(f.flow.snapshot, null); assert.equal(f.asked.length, 0)
})
test('upload and every plain language reply use the same generic agent task stream', async () => {
  const f = fixture(); choose(f.flow)
  for (const message of ['导入名单', '丁的学号是0012345', '打开公告发布', '确认导入']) await f.flow.send(message, context)
  assert.equal(f.posts.length, 1); assert.ok(f.posts[0].url.endsWith('/attachments'))
  assert.deepEqual(f.asked.map(call => call.message), ['导入名单', '丁的学号是0012345', '打开公告发布', '确认导入'])
  assert.equal(f.flow.snapshot.contextId, 'context-one'); assert.equal(f.flow.canHandle(), true)
  assert.equal(f.asked[0].attachments.files[0].name, '名单.xlsx')
  assert.equal(f.flow.draftAttachments.length, 0)
  assert.ok(f.asked.slice(1).every(call => call.attachments === undefined), 'follow-up replies must not resend the file cards')
})
test('a failed upload preserves the file and draft without creating a model task', async () => {
  const f = fixture(); choose(f.flow)
  f.request.post = async () => { throw new Error('upload interrupted') }
  await f.flow.send('导入名单', context)
  assert.equal(f.asked.length, 0); assert.equal(f.flow.attachedNames[0], '名单.xlsx'); assert.equal(f.agent.draft, '导入名单'); assert.equal(f.flow.working, false)
  assert.equal(f.flow.draftAttachments[0].name, '名单.xlsx')
})
test('busy task blocks repeat submissions and attachment changes', async () => {
  const f = fixture(); choose(f.flow); f.agent.busy = true
  await f.flow.send('再确认一次', context); await f.flow.remove()
  assert.equal(f.asked.length, 0); assert.equal(f.deletes.length, 0); assert.equal(f.flow.attachedNames.length, 1)
})
test('removing an uploaded file detaches it from the server session', async () => {
  const f = fixture(); choose(f.flow); await f.flow.send('导入名单', context); await f.flow.remove()
  assert.equal(f.deletes.length, 1); assert.equal(f.deletes[0].config.params.sessionId, 'session-one'); assert.equal(f.flow.canHandle(), false)
})
test('session metadata restores attachment names without running a model or replaying writes', async () => {
  const f = fixture(); f.agent.sessions.push({ id: 'session-one', attachmentContextId: 'context-one' })
  await new Promise(setImmediate)
  assert.equal(f.flow.snapshot?.contextId, 'context-one'); assert.equal(f.posts.length, 0); assert.equal(f.asked.length, 0)
})
test('unavailable attachment capability preserves the selected file and draft', async () => {
  const f = fixture(); choose(f.flow); f.agent.capabilities.attachmentImport = false
  await f.flow.send('导入这份名单', context)
  assert.equal(f.posts.length, 0); assert.equal(f.asked.length, 0); assert.equal(f.flow.attachedNames[0], '名单.xlsx')
  assert.equal(f.agent.draft, '导入这份名单'); assert.match(f.agent.error, /未开通附件导入/)
})

test('attachment access follows the declared capability, independently of engine labels', async () => {
  const f = fixture(); f.agent.capabilities.engine = 'other-agent'; f.agent.capabilities.harness = 'legacy'; choose(f.flow)
  await f.flow.send('导入名单', context); assert.equal(f.posts.length, 1); assert.equal(f.asked.length, 1)
  const g = fixture(); delete g.agent.capabilities.attachmentImport; g.agent.capabilities.harness = 'opencode'; choose(g.flow)
  await g.flow.send('导入名单', context); assert.equal(g.posts.length, 0); assert.equal(g.asked.length, 0)
})

test('a task submission failure keeps uploaded attachments available without uploading them twice', async () => {
  const f = fixture(); choose(f.flow)
  const submit = f.agent.ask; let failedPreview
  f.agent.ask = async (_context, _message, preview) => { failedPreview = preview }
  await f.flow.send('导入名单', context)
  assert.equal(f.posts.length, 1); assert.equal(f.flow.draftAttachments[0].name, '名单.xlsx')
  assert.equal(failedPreview.files[0].size, 4)
  f.agent.ask = submit; await f.flow.send('导入名单', context)
  assert.equal(f.posts.length, 1); assert.equal(f.flow.draftAttachments.length, 0)
})

test('restoring a used attachment context keeps follow-up capability without returning sent files to the composer', async () => {
  const f = fixture()
  f.agent.tasks.push({ id: 'existing-task', attachmentContextId: 'context-one' })
  f.agent.sessions.push({ id: 'session-one', attachmentContextId: 'context-one' })
  await new Promise(setImmediate)
  assert.equal(f.flow.canHandle(), true); assert.equal(f.flow.draftAttachments.length, 0)
})
