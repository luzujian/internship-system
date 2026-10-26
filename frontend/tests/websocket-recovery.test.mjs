import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

function fixture() {
  const timers = new Map()
  const sockets = []
  const notices = []
  const listeners = new Map()
  let nextId = 0
  class Socket {
    static CONNECTING = 0
    static OPEN = 1
    constructor() { this.readyState = 0; sockets.push(this) }
    fail() { this.readyState = 3; this.onclose?.({ code: 1006, reason: '' }) }
    open() { this.readyState = 1; this.onopen?.() }
    close() { this.readyState = 3; this.onclose?.({ code: 1000, reason: '' }) }
    send() {}
  }
  const output = ts.transpileModule(readFileSync(new URL('../src/utils/websocket.ts', import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
  }).outputText
  const context = {
    exports: {}, WebSocket: Socket, console,
    document: { cookie: '' },
    window: {
      location: { protocol: 'https:', host: 'app.example.test' },
      addEventListener(name, listener) { listeners.set(name, listener) },
      dispatchEvent(event) { listeners.get(event.type)?.(event) },
      setTimeout(fn, delay) { const id = ++nextId; timers.set(id, { fn, delay }); return id },
      setInterval() { return ++nextId }
    },
    clearTimeout(id) { timers.delete(id) }, clearInterval() {},
    require(id) {
      if (id === 'element-plus') return { ElMessage: { warning(message) {
        const notice = { message, closed: false, close() { this.closed = true } }
        notices.push(notice)
        return notice
      } } }
      return { default: { log() {}, debug() {}, warn() {}, error() {} } }
    }
  }
  vm.runInNewContext(output, context)
  const client = new context.exports.WebSocketClient({ url: '/ws/announcement', token: 'test-token',
    maxReconnectAttempts: 2, reconnectInterval: 5 })
  const tick = () => {
    assert.equal(timers.size, 1)
    const [id, timer] = timers.entries().next().value
    timers.delete(id)
    timer.fn()
    return timer.delay
  }
  return { client, sockets, timers, notices, tick, context }
}

test('prolonged outage retries slowly and recovers without a page refresh or repeated warnings', () => {
  const { client, sockets, timers, notices, tick } = fixture()
  client.connect()
  for (let attempt = 0; attempt < 2; attempt++) {
    sockets.at(-1).fail()
    assert.equal(tick(), 5)
  }
  for (let attempt = 0; attempt < 3; attempt++) {
    sockets.at(-1).fail()
    assert.equal(tick(), 60000)
  }
  assert.equal(notices.length, 1)
  assert.match(notices[0].message, /自动重连/)
  sockets.at(-1).open()
  assert.equal(client.isConnected(), true)
  assert.equal(notices[0].closed, true)
  assert.equal(timers.size, 0)
  sockets.at(-1).fail()
  assert.equal(tick(), 5)
})

test('duplicate close notifications do not create parallel retry loops', () => {
  const { client, sockets, timers, tick } = fixture()
  client.connect()
  sockets[0].fail()
  sockets[0].fail()
  assert.equal(timers.size, 1)
  assert.equal(tick(), 5)
  assert.equal(sockets.length, 2)
})

test('leaving the page cancels recovery and dismisses its notice', () => {
  const { client, sockets, timers, notices, tick } = fixture()
  client.connect()
  for (let attempt = 0; attempt < 2; attempt++) { sockets.at(-1).fail(); tick() }
  sockets.at(-1).fail()
  assert.equal(notices.length, 1)
  client.disconnect()
  sockets.at(-1).fail()
  assert.equal(timers.size, 0)
  assert.equal(notices[0].closed, true)
})

function authActions(websocket) {
  const source = readFileSync(new URL('../src/store/auth/index.ts', import.meta.url), 'utf8')
  const ast = ts.createSourceFile('auth.ts', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  const methods = []
  function visit(node) {
    if (ts.isMethodDeclaration(node) && ['setToken', 'refreshAccessToken', 'logout'].includes(node.name.getText(ast))) methods.push(node.getText(ast))
    ts.forEachChild(node, visit)
  }
  visit(ast); assert.equal(methods.length, 3)
  const context = {
    ...websocket.context.exports,
    window: websocket.context.window,
    CustomEvent: class { constructor(type, { detail }) { this.type = type; this.detail = detail } },
    decodeToken: token => JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString()),
    logger: { log() {}, warn() {}, error() {} }, STORAGE_KEYS: { CURRENT_ROLE: 'current_role' },
    localStorage: { getItem() {}, setItem() {}, removeItem() {} },
    sessionStorage: { getItem() { return null } },
    writeTabSession() {}, clearRoleStorage() {}, cleanupTokens() {}, setTimeout() {},
    async refreshTokenUtil() { return context.nextToken }
  }
  vm.runInNewContext(ts.transpileModule('globalThis.actions = {' + methods.join(',') + '}', {
    compilerOptions: { target: ts.ScriptTarget.ES2022 }
  }).outputText, context)
  return context
}
const jwt = claims => 'header.' + Buffer.from(JSON.stringify(claims)).toString('base64url') + '.signature'

test('actual auth refresh synchronizes the next reconnect without interrupting a healthy socket', async () => {
  const ws = fixture(), authContext = authActions(ws)
  const old = jwt({ userId: 1, username: 'test', role: 'ROLE_STUDENT', exp: 1 })
  const next = jwt({ userId: '1', username: 'test', role: 'ROLE_STUDENT', isReadOnly: false, exp: 2 })
  const auth = { token: old, role: 'ROLE_STUDENT', allUsers: { ROLE_STUDENT: { refreshToken: 'test-refresh' } }, ...authContext.actions }
  const client = ws.context.exports.initAnnouncementWebSocket(old, () => {})
  ws.sockets.at(-1).open(); authContext.nextToken = next
  assert.equal(await auth.refreshAccessToken(), true)
  assert.equal(client.isConnected(), true); assert.equal(ws.sockets.length, 1)
  ws.sockets.at(-1).fail(); ws.tick()
  assert.equal(ws.context.document.cookie, `ws_token=${encodeURIComponent(next)}; path=/; SameSite=Lax`)
  assert.equal(ws.timers.size, 0)
})

test('role changes and logout disconnect the old identity and cancel pending recovery', async () => {
  const ws = fixture(), authContext = authActions(ws)
  const old = jwt({ userId: 1, username: 'test', role: 'ROLE_STUDENT' })
  const next = jwt({ userId: 2, username: 'company', role: 'ROLE_COMPANY' })
  const auth = { token: old, role: 'ROLE_STUDENT', allUsers: { ROLE_STUDENT: {} }, ...authContext.actions }
  ws.context.exports.initAnnouncementWebSocket(old, () => {}); ws.sockets.at(-1).fail()
  auth.setToken(next)
  assert.equal(ws.context.exports.getAnnouncementWebSocket(), null); assert.equal(ws.timers.size, 0)
  ws.context.exports.initAnnouncementWebSocket(next, () => {}); ws.sockets.at(-1).open()
  await auth.logout(true)
  assert.equal(ws.context.exports.getAnnouncementWebSocket(), null); assert.equal(auth.token, null)
})
