import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

function fixture() {
  const timers = new Map()
  const sockets = []
  const notices = []
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
  return { client, sockets, timers, notices, tick }
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
