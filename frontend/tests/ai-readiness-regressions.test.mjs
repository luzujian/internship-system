import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
import { ref, reactive, computed, watch, nextTick, effectScope } from 'vue'
import { createPinia } from 'pinia'
import * as protocol from '../src/agent/protocol.mjs'
import { decideJobPostingAutoFill } from '../src/utils/jobPostingAutoFill.js'
import { normalizeInterviewMethods } from '../src/utils/interviewMethods.js'

const require = createRequire(import.meta.url)
const source = file => readFileSync(new URL('../src/' + file, import.meta.url), 'utf8')
const compile = (code, module = ts.ModuleKind.None) => ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022, module } }).outputText
const memory = () => { const values = new Map(); return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, String(value)), removeItem: key => values.delete(key), key: i => [...values.keys()][i], get length() { return values.size } } }
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function declarations(text, names) {
  const script = text.includes('<script') ? text.match(/<script[^>]*>([\s\S]*?)<\/script>/)[1] : text
  const ast = ts.createSourceFile('fixture.ts', script, ts.ScriptTarget.Latest, true)
  return names.map(name => {
    const node = ast.statements.find(node => ts.isVariableStatement(node) && node.declarationList.declarations.some(item => item.name.getText(ast) === name)
      || ts.isFunctionDeclaration(node) && node.name?.text === name)
    assert.ok(node, `missing actual application declaration ${name}`)
    return node.getText(ast).replace(/^export /, '')
  }).join('\n')
}

function streamFixture(fetch) {
  const module = { exports: {} }
  vm.runInNewContext(compile(source('utils/aiTestStream.ts'), ts.ModuleKind.CommonJS), {
    module, exports: module.exports, fetch, AbortController, DOMException, TextDecoder, setTimeout, clearTimeout,
    require: name => name.includes('protocol') ? protocol : require(name)
  })
  return options => module.exports.readAIChatStream({ message: '测试', model: 'test', token: 'mock', context: [], ...options })
}
const encode = text => new TextEncoder().encode(text)
const event = data => `data: ${JSON.stringify(data)}\r\n\r\n`

test('admin stream assembles split UTF-8/SSE and succeeds only on explicit end', async () => {
  const bytes = encode(event({ type: 'chunk', content: '你好' }) + event({ type: 'end' }))
  let signal, cancelled = false
  const read = streamFixture(async (_url, options) => {
    signal = options.signal
    return { ok: true, body: new ReadableStream({ start(controller) { for (const byte of bytes) controller.enqueue(Uint8Array.of(byte)) }, cancel() { cancelled = true } }) }
  })
  const chunks = []
  assert.equal(await read({ onChunk: text => chunks.push(text) }), '你好')
  assert.deepEqual(chunks, ['你好']); assert.equal(signal.aborted, true); assert.equal(cancelled, true)
})

test('admin stream rejects empty or truncated EOF, provider errors, and read failures', async () => {
  for (const scenario of ['empty', 'partial', 'provider', 'network', 'empty-end']) {
    const read = streamFixture(async () => ({ ok: true, body: new ReadableStream({ start(controller) {
      if (scenario === 'network') { controller.error(new TypeError('network disconnected')); return }
      if (scenario === 'partial') controller.enqueue(encode(event({ type: 'chunk', content: '未完成' })))
      if (scenario === 'provider') controller.enqueue(encode(event({ type: 'error', message: 'provider refused' })))
      if (scenario === 'empty-end') controller.enqueue(encode(event({ type: 'end' })))
      controller.close()
    } }) }))
    await assert.rejects(read(), undefined, scenario)
  }
})

test('admin stream retains timeout after response headers and supports user cancellation', async () => {
  for (const scenario of ['timeout', 'cancel']) {
    let responseSignal
    const read = streamFixture(async (_url, options) => {
      responseSignal = options.signal
      return { ok: true, body: new ReadableStream({ start(controller) {
        options.signal.addEventListener('abort', () => controller.error(new DOMException('aborted', 'AbortError')), { once: true })
      } }) }
    })
    const controller = new AbortController()
    const pending = read({ signal: controller.signal, timeoutMs: scenario === 'timeout' ? 15 : 1000 })
    const rejected = assert.rejects(pending, scenario === 'timeout' ? /请求超时/ : { name: 'AbortError' })
    if (scenario === 'cancel') controller.abort()
    await rejected
    assert.equal(responseSignal.aborted, true)
  }
})

function postingFixture(t) {
  const text = source('views/company/RecruitmentManagement.vue'), pending = [], notices = []
  const context = { ref, watch, AbortController, onUnmounted() {},
    authStore: reactive({ token: 'mock', role: 'ROLE_COMPANY', user: { id: 1, username: 'company-A' } }),
    postingText: ref('岗位原文'), parsingPosting: ref(false), dialogVisible: ref(true), aiParseNotices: ref([]),
    positionForm: ref({ id: null, positionName: '', description: '', interviewMethod: ['线下'] }),
    jobPostingAiService: { parseJobPosting(_text, signal) { const response = deferred(); pending.push({ ...response, signal }); return response.promise } },
    decideJobPostingAutoFill, normalizeInterviewMethods, hasInterviewTemplate: ref(false), isAiFilled: () => false,
    isPreviousAiValueUnchanged: () => false, markAiFilled() {}, fieldLabel: text => text, resolveAddressCodes: () => [],
    ElMessage: Object.fromEntries(['success', 'error', 'info', 'warning'].map(kind => [kind, text => notices.push({ kind, text })])) }
  const guard = text.slice(text.indexOf('let postingParseController:'), text.indexOf('// ===== 智能填表'))
  const scope = effectScope(); t.after(() => scope.stop())
  scope.run(() => vm.runInNewContext(compile(guard + declarations(text, ['handleParsePosting']) + '\nglobalThis.run = handleParsePosting'), context))
  const answer = { code: 200, data: { positionName: 'A岗位', description: 'A职责', confidence: { positionName: 0.99, description: 0.99 } } }
  return { context, pending, notices, answer }
}

test('job parsing fills the unchanged open form and always unlocks after no new fields', async t => {
  const f = postingFixture(t), p = f.context.run(); f.pending[0].resolve(f.answer); await p
  assert.equal(f.context.positionForm.value.positionName, 'A岗位'); assert.equal(f.context.parsingPosting.value, false)
  const again = f.context.run(); f.pending[1].resolve({ code: 200, data: {} }); await again
  assert.equal(f.context.parsingPosting.value, false)
})

test('job parsing fills multiple interview methods as selections and preserves manual or template choices', async t => {
  for (const scenario of ['default', 'manual', 'template']) {
    const f = postingFixture(t)
    if (scenario === 'manual') f.context.positionForm.value.interviewMethod = ['电话面试']
    if (scenario === 'template') f.context.hasInterviewTemplate.value = true
    const before = [...f.context.positionForm.value.interviewMethod]
    const pending = f.context.run()
    f.pending[0].resolve({ code: 200, data: { interviewMethod: '线下、线上视频', confidence: { interviewMethod: 0.99 } } })
    await pending
    assert.deepEqual(Array.from(f.context.positionForm.value.interviewMethod), scenario === 'default' ? ['线下', '线上视频'] : before)
  }
})

test('job parsing rejects a closed/reopened form, manual edit-then-revert, and another account', async t => {
  for (const scenario of ['reopen', 'edit-revert', 'account', 'logout']) {
    const f = postingFixture(t), p = f.context.run()
    if (scenario === 'reopen') { f.context.dialogVisible.value = false; f.context.positionForm.value = { id: 2, positionName: '', description: '' }; f.context.dialogVisible.value = true }
    if (scenario === 'edit-revert') { f.context.positionForm.value.description = '手填'; f.context.positionForm.value.description = '' }
    if (scenario === 'account') f.context.authStore.user = { id: 2, username: 'company-B' }
    if (scenario === 'logout') f.context.authStore.token = ''
    assert.equal(f.pending[0].signal.aborted, true, scenario)
    f.pending[0].resolve(f.answer); await p
    assert.equal(f.context.positionForm.value.positionName, '', scenario)
    assert.equal(f.context.positionForm.value.description, '', scenario)
    assert.equal(f.notices.length, 0, scenario)
  }
})

test('a cancelled old parse cannot unlock or overwrite a newer parse', async t => {
  const f = postingFixture(t), first = f.context.run()
  f.context.dialogVisible.value = false; f.context.dialogVisible.value = true
  const second = f.context.run()
  f.pending[0].reject(new Error('late network failure')); await first
  assert.equal(f.context.parsingPosting.value, true)
  f.pending[1].resolve(f.answer); await second
  assert.equal(f.context.positionForm.value.positionName, 'A岗位'); assert.equal(f.context.parsingPosting.value, false)
})

function agentFixture(t) {
  let owner = 'student-A'
  const module = { exports: {} }, scope = effectScope()
  const request = { get: async url => {
    if (url === '/agent/capabilities') return { data: { enabled: true, persistenceAvailable: false, actor: { ownerKey: owner, role: 'ROLE_STUDENT', readOnly: false }, capabilities: { agent_chat: true } } }
    if (url === '/agent/sessions') { if (owner === 'student-B') throw new Error('history unavailable'); return { data: { items: [{ id: 'A-private', title: 'private-A' }], hasMore: true } } }
    return { data: [] }
  } }
  vm.runInNewContext(compile(source('agent/store.ts'), ts.ModuleKind.CommonJS), { module, exports: module.exports,
    console, localStorage: memory(), sessionStorage: memory(), window: { setTimeout, clearTimeout }, AbortController, setTimeout, clearTimeout,
    require: name => name === '@/utils/request' ? { __esModule: true, default: request, getAccessToken: () => owner }
      : name === './protocol.mjs' ? protocol : name === './formContext' ? { currentForm: ref(null), formBusinessReceipt: ref(null), setFormSession() {} } : require(name) })
  const pinia = createPinia(), store = scope.run(() => module.exports.useAgentStore(pinia))
  t.after(() => { store.$dispose(); scope.stop() })
  return { store, switchOwner() { owner = 'student-B' } }
}

test('agent logout and direct account switch discard history even when new history fails', async t => {
  for (const logout of [true, false]) {
    const f = agentFixture(t); await f.store.initialize(); await nextTick()
    assert.equal(f.store.sessions[0].title, 'private-A'); f.store.showHistory = true
    if (logout) { f.store.dispose(); assert.equal(f.store.sessions.length, 0); assert.equal(f.store.showHistory, false) }
    f.switchOwner(); await f.store.initialize(); await nextTick()
    assert.equal(f.store.capabilities.actor.ownerKey, 'student-B')
    assert.equal(f.store.sessions.length, 0); assert.equal(f.store.sessionsHasMore, false); assert.equal(f.store.showHistory, false)
  }
})

function adminFixture(t, shared = memory(), reader) {
  const text = source('views/admin/AITest.vue'), notices = [], notifications = [], unmount = []
  const names = ['testOwner', 'testCacheKey', 'testGeneration', 'pageActive', 'chatController', 'analysisController', 'stopChat',
    'inputMessage', 'loading', 'streamingMessage', 'showModelSelector', 'currentModel', 'autoScrollEnabled', 'messages', 'testStats',
    'analyzing', 'uploadedFile', 'analysisResult', 'analysisProgressVisible', 'sendMessage', 'sendStreamingMessage', 'updateTestStats',
    'saveChatMessages', 'loadChatMessages', 'loadSavedAnalysisResult', 'streamDisplayAnalysisResult', 'resetTestAccount']
  const context = { ref, reactive, computed, watch, nextTick, AbortController, DOMException,
    authStore: reactive({ token: 'mock', role: 'ROLE_ADMIN', user: { id: 1, username: 'admin-A' } }), localStorage: shared,
    logger: { error() {} }, ElMessage: Object.fromEntries(['error', 'info', 'warning'].map(kind => [kind, text => notices.push({ kind, text })])),
    ElNotification: value => notifications.push(value), scrollToBottom() {}, readAIChatStream: reader,
    setTimeout: resolve => { queueMicrotask(resolve); return 1 }, onBeforeUnmount: callback => unmount.push(callback) }
  const lifecycle = text.slice(text.indexOf('watch(testOwner,'), text.indexOf('</script>'))
  const scope = effectScope(); t.after(() => scope.stop())
  scope.run(() => vm.runInNewContext(compile(declarations(text, names) + '\n' + lifecycle + `\nglobalThis.api = { ${names.filter(name => !['testGeneration', 'pageActive', 'chatController', 'analysisController'].includes(name)).join(',')} };`), context))
  context.api.currentModel.value = { modelCode: 'mock-model' }
  return { api: context.api, auth: context.authStore, notices, notifications, unmount, shared }
}

test('admin chat and report caches are scoped by account and ignore legacy unowned data', async t => {
  const f = adminFixture(t)
  f.shared.setItem('aiTestChatMessages', JSON.stringify([{ content: 'legacy secret' }]))
  f.shared.setItem('aiTestAnalysisResult', JSON.stringify({ summary: 'legacy secret' }))
  f.api.loadChatMessages(); f.api.loadSavedAnalysisResult()
  assert.equal(f.api.messages.value.length, 0); assert.equal(f.api.analysisResult.value, null)
  f.api.messages.value = [{ content: 'A question' }]; f.api.saveChatMessages()
  await f.api.streamDisplayAnalysisResult({ summary: 'A report' }, () => true)
  f.auth.user = { id: 2, username: 'admin-B' }
  assert.equal(f.api.messages.value.length, 0); assert.equal(f.api.analysisResult.value, null)
  f.api.messages.value = [{ content: 'B question' }]; f.api.saveChatMessages()
  f.auth.user = { id: 1, username: 'admin-A' }
  assert.equal(f.api.messages.value[0].content, 'A question'); assert.equal(f.api.analysisResult.value.summary, 'A report')
})

test('admin page counts failed stream reads as failures and never as success', async t => {
  const f = adminFixture(t, memory(), async () => { throw new Error('回答未完成') })
  f.api.inputMessage.value = 'test'; await f.api.sendMessage()
  assert.equal(f.api.loading.value, false); assert.equal(f.api.testStats.failedTests, 1); assert.equal(f.api.testStats.successTests, 0)
  assert.equal(f.notifications.length, 0)
})

test('a partially displayed analysis stops on account change and cannot populate the new cache', async t => {
  const f = adminFixture(t), owner = f.api.testOwner.value
  const pending = f.api.streamDisplayAnalysisResult({ summary: 'A delayed report' }, () => f.api.testOwner.value === owner)
  f.auth.user = { id: 2, username: 'admin-B' }
  await pending
  assert.equal(f.api.analysisResult.value, null)
  assert.equal(f.shared.getItem(f.api.testCacheKey('aiTestAnalysisResult')), null)
})

test('admin stop and account change abort streams; stale completions cannot write another account', async t => {
  for (const change of ['stop', 'account', 'unmount']) {
    const response = deferred(); let options
    const f = adminFixture(t, memory(), received => { options = received; return response.promise })
    f.api.inputMessage.value = 'A question'; const pending = f.api.sendMessage()
    if (change === 'stop') f.api.stopChat()
    if (change === 'account') f.auth.user = { id: 2, username: 'admin-B' }
    if (change === 'unmount') f.unmount.forEach(callback => callback())
    assert.equal(options.signal.aborted, true)
    options.onChunk('A late chunk'); response.resolve('A late answer'); await pending
    assert.equal(f.api.loading.value, false); assert.equal(f.api.streamingMessage.value, '')
    assert.equal(f.api.testStats.successTests, 0); assert.equal(f.notifications.length, 0)
    assert.equal(f.api.messages.value.some(message => message.content === 'A late answer'), false)
    if (change === 'account') assert.equal(f.api.messages.value.length, 0)
  }
})

test('report request carries the displayed reflection and refuses another period or stale selection', async () => {
  const text = source('views/teacher/Evaluation.vue')
  for (const scenario of ['exact', 'different-period', 'selection-changed', 'missing']) {
    const calls = [], response = deferred(), notices = []
    const context = { currentPeriodReport: ref(scenario === 'missing' ? null : { id: 101 }), currentStudent: ref({ id: 7 }),
      currentAIAnalysis: ref(null), aiAnalysisLoading: ref(false), aiAnalysisDialogVisible: ref(false),
      getAnalysisByStudentId: (...args) => { calls.push(args); return response.promise },
      ElMessage: { warning: text => notices.push(text), error: text => notices.push(text) }, console,
      parseScoreDetails: value => value }
    const run = vm.runInNewContext(compile(declarations(text, ['openAIAnalysisDialog']) + '\nopenAIAnalysisDialog'), context)
    const pending = run({ id: 7, name: '学生' })
    if (scenario === 'selection-changed') context.currentPeriodReport.value = { id: 102 }
    response.resolve({ data: { reflectionId: scenario === 'different-period' ? 102 : 101, totalScore: 88 } }); await pending
    if (scenario !== 'missing') assert.deepEqual(calls, [[7, 101]])
    else assert.equal(calls.length, 0)
    assert.equal(context.aiAnalysisDialogVisible.value, scenario === 'exact')
    if (scenario === 'exact') assert.equal(context.currentAIAnalysis.value.reflectionId, 101)
    if (scenario === 'different-period') assert.equal(notices.length, 1)
  }
})

test('analysis API transmits exact reflection query parameter', () => {
  const calls = [], get = vm.runInNewContext(compile(declarations(source('api/studentReflectionAIAnalysis.ts'), ['getAnalysisByStudentId']) + '\ngetAnalysisByStudentId'), { request: options => calls.push(options) })
  get(7, 101)
  assert.equal(calls[0].url, '/counselor/ai-analysis/student/7'); assert.equal(calls[0].params.reflectionId, 101)
})

test('selecting an empty report period does not fall back to a different period', () => {
  const context = { computed, currentStudent: ref({ reports: [{ id: 101, periodNumber: 1, isDraft: false }] }), filters: ref({ period: '2' }), actualCurrentPeriodNumber: ref(2) }
  const report = vm.runInNewContext(compile(declarations(source('views/teacher/Evaluation.vue'), ['currentPeriodReport']) + '\ncurrentPeriodReport'), context)
  assert.equal(report.value, null)
  context.filters.value.period = '1'
  assert.equal(report.value.id, 101)
})

function reflectionNoticeFixture(rows = []) {
  const text = source('views/student/Internships.vue'), calls = [], notices = []
  const submitted = { id: 101, description: '原提交内容', statusClass: 'pending', status: '待批阅' }
  const context = { ref, console: { log() {}, error() {} },
    logs: ref([submitted]), archives: ref([]), allLogs: ref([submitted]),
    request: { get: async url => { calls.push(url); return { code: 200, data: rows } } },
    fetchPeriodStatus: async () => calls.push('period-status'),
    ElMessage: Object.fromEntries(['warning', 'error'].map(kind => [kind, text => notices.push({ kind, text })])) }
  const run = vm.runInNewContext(compile(declarations(text, ['fetchReflections', 'handleAIAnalysisResult']) + '\nhandleAIAnalysisResult'), context)
  return { run, context, calls, notices }
}

test('a rejected AI reflection refreshes server draft state even when success is false', async () => {
  const f = reflectionNoticeFixture([{ id: 101, content: '退回内容', remark: '0', submitTime: '2026-10-06T08:00:00' }])
  await f.run({ reflectionId: 101, success: false, isNotReflection: true })
  assert.deepEqual(f.calls, ['/student/internship-reflection/list', 'period-status'])
  assert.equal(f.context.logs.value[0].statusClass, 'draft')
  assert.equal(f.context.logs.value[0].status, '草稿')
  assert.equal(f.notices.length, 1); assert.equal(f.notices[0].kind, 'warning')
  assert.match(f.notices[0].text, /退回草稿/)
})

test('an ordinary AI failure keeps the submitted reflection and explains no resubmission is needed', async () => {
  for (const message of [undefined, '心得已提交，AI分析暂未完成，可稍后重试或由教师人工批阅，无需重复提交']) {
    const f = reflectionNoticeFixture([{ id: 101, remark: '0' }])
    await f.run({ reflectionId: 101, success: false, isNotReflection: false, message })
    assert.deepEqual(f.calls, ['period-status'])
    assert.equal(f.context.logs.value[0].statusClass, 'pending')
    assert.equal(f.context.logs.value[0].description, '原提交内容')
    assert.equal(f.notices.length, 1)
    assert.match(f.notices[0].text, /AI分析暂未完成/)
    assert.match(f.notices[0].text, /无需重复提交/)
    assert.doesNotMatch(f.notices[0].text, /退回草稿/)
  }
})

test('a successful AI reflection notice refreshes the analysis and period state', async () => {
  const f = reflectionNoticeFixture([{ id: 101, content: '已提交内容', aiAnalysis: '分析已完成', submitTime: '2026-10-06T08:00:00' }])
  await f.run({ reflectionId: 101, success: true, isNotReflection: false })
  assert.deepEqual(f.calls, ['/student/internship-reflection/list', 'period-status'])
  assert.equal(f.context.logs.value[0].statusClass, 'pending')
  assert.equal(f.context.logs.value[0].review, '分析已完成')
  assert.equal(f.notices.length, 0)
})
