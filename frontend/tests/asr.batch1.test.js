import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { stripTypeScriptTypes } from 'node:module'

// Resolve the app authentication import and execute the actual cloud provider.
const source = readFileSync(new URL('../src/agent/asr.ts', import.meta.url), 'utf8')
  .replace("import { getAccessToken } from '@/utils/request'", "const getAccessToken = () => 'test-token'")
const { createAsr, asrSupported } = await import(`data:text/javascript;base64,${Buffer.from(stripTypeScriptTypes(source)).toString('base64')}`)
const tick = () => new Promise(resolve => setImmediate(resolve))
let env, originals
const names = ['window', 'navigator', 'AudioWorkletNode', 'WebSocket', 'fetch', 'setTimeout', 'clearTimeout']

test.beforeEach(() => {
  originals = new Map(names.map(name => [name, Object.getOwnPropertyDescriptor(globalThis, name)]))
  env = { tracksStopped: 0, contextClosed: 0, fetches: 0, sockets: [], processors: [], timers: new Map(), finals: [], states: [], interim: [], flushRequests: [] }
  env.stream = { getTracks: () => [env.track] }
  env.track = { stop: () => { env.tracksStopped++ } }
  env.permission = () => Promise.resolve(env.stream)
  class Context {
    audioWorklet = { addModule: async path => { assert.equal(path, '/agent-pcm-worklet.js') } }
    destination = {}
    resume() { return Promise.resolve() }
    close() { env.contextClosed++; return Promise.resolve() }
    createMediaStreamSource() { return { connect() {}, disconnect() {} } }
  }
  class Worklet {
    constructor() { env.processors.push(this) }
    port = { onmessage: null, postMessage: message => { env.flushRequests.push(message) } }
    connect() {}
    disconnect() {}
    emit(data) { this.port.onmessage?.({ data }) }
  }
  class Socket {
    static OPEN = 1
    readyState = 1
    bufferedAmount = 0
    sent = []
    constructor(url) { this.url = url; env.sockets.push(this) }
    send(text) { this.sent.push(JSON.parse(text)) }
    close() { this.readyState = 3 }
    emit(data) { this.onmessage?.({ data: JSON.stringify(data) }) }
  }
  const values = {
    window: { AudioContext: Context, AudioWorkletNode: Worklet, location: { href: 'https://intern.test/company/jobs', origin: 'https://intern.test', protocol: 'https:' } },
    navigator: { mediaDevices: { getUserMedia: () => env.permission() } },
    AudioWorkletNode: Worklet, WebSocket: Socket,
    fetch: async (path, options) => {
      env.fetches++
      assert.equal(path, '/api/agent/voice/sessions')
      assert.equal(options.headers.Authorization, 'Bearer test-token')
      return env.response || { ok: true, json: async () => ({ code: 200, data: { url: '/api/agent/voice/ws?ticket=one-use' } }) }
    },
    setTimeout: (callback, delay) => { const id = {}; env.timers.set(id, { callback, delay }); return id },
    clearTimeout: id => env.timers.delete(id)
  }
  for (const [name, value] of Object.entries(values)) Object.defineProperty(globalThis, name, { value, writable: true, configurable: true })
  env.start = () => createAsr({ onFinal: text => env.finals.push(text), onInterim: text => env.interim.push(text), onState: (state, detail) => env.states.push({ state, detail }) })
  env.fireTimer = delay => {
    const timer = [...env.timers.values()].find(timer => timer.delay === delay)
    assert.ok(timer, `timer ${delay} exists`); timer.callback()
  }
})
test.afterEach(() => {
  for (const [name, descriptor] of originals) {
    if (descriptor) Object.defineProperty(globalThis, name, descriptor)
    else delete globalThis[name]
  }
})
async function listen() {
  const session = env.start()
  await tick()
  const socket = env.sockets[0]
  assert.equal(socket.url.protocol, 'wss:')
  socket.emit({ type: 'ready' })
  return { session, socket, processor: env.processors[0] }
}

test('stop immediately releases microphone, flushes queued tail before finish, and accepts final recognition', async () => {
  const { session, socket, processor } = await listen()
  socket.emit({ type: 'final', id: 'first', text: '招聘' })
  const pending = session.stop()
  assert.equal(session.stop(), pending)
  assert.equal(env.tracksStopped, 1)
  assert.equal(env.contextClosed, 0, 'worklet remains available to drain')
  assert.deepEqual(env.flushRequests, [{ type: 'flush', requestId: 1 }])
  assert.deepEqual(socket.sent, [])
  processor.emit(new Uint8Array([1, 2, 3, 4]).buffer)
  processor.emit({ type: 'flushed', requestId: 1 })
  assert.deepEqual(socket.sent, [{ type: 'audio', audio: 'AQIDBA==' }, { type: 'finish' }])
  assert.equal(env.contextClosed, 1)
  socket.emit({ type: 'final', id: 'last', text: '五个人' })
  socket.emit({ type: 'finished' })
  await pending
  assert.deepEqual(env.finals, ['招聘', '招聘五个人'])
  assert.equal(env.tracksStopped, 1)
  assert.equal(env.states.at(-1).state, 'idle')
  assert.equal(env.timers.size, 0)
})

test('abort on panel close releases capture immediately and ignores every queued callback', async () => {
  const { session, socket, processor } = await listen()
  const lateSocket = socket.onmessage, lateAudio = processor.port.onmessage
  session.abort(); session.abort()
  lateSocket({ data: JSON.stringify({ type: 'final', id: 'late', text: '不能填入' }) })
  lateAudio({ data: new ArrayBuffer(6) })
  assert.deepEqual(env.finals, [])
  assert.deepEqual(socket.sent, [])
  assert.equal(env.tracksStopped, 1)
  assert.equal(env.contextClosed, 1)
  assert.equal(socket.onmessage, null)
  assert.equal(processor.port.onmessage, null)
  assert.equal(env.timers.size, 0)
})

test('dictation stops the microphone before waiting for text and times out without inventing a result', async () => {
  const session = env.start(); await tick()
  const socket = env.sockets[0]
  socket.emit({ type: 'ready', mode: 'dictation' })
  const pending = session.stop()
  assert.equal(env.tracksStopped, 1)
  env.processors[0].emit({ type: 'flushed', requestId: 1 })
  assert.equal(env.contextClosed, 1)
  assert.deepEqual(env.finals, [])
  env.fireTimer(65_000)
  await pending
  assert.equal(env.states.at(-1).state, 'error')
  assert.deepEqual(env.finals, [])
  assert.equal(env.timers.size, 0)
})

test('microphone permission denial ends waiting and never requests a cloud session', async () => {
  env.permission = async () => { throw Object.assign(new Error('denied'), { name: 'NotAllowedError' }) }
  env.start(); await tick()
  assert.equal(env.fetches, 0)
  assert.equal(env.contextClosed, 1)
  assert.match(env.states.at(-1).detail, /麦克风权限/)
  assert.equal(env.timers.size, 0)
})

test('close while permission prompt is pending stops tracks granted later without starting a session', async () => {
  let grant
  env.permission = () => new Promise(resolve => { grant = resolve })
  const session = env.start()
  session.abort(); grant(env.stream); await tick()
  assert.equal(env.tracksStopped, 1)
  assert.equal(env.fetches, 0)
  assert.equal(env.contextClosed, 1)
})

test('network loss during finalization preserves text and reports incomplete recognition', async () => {
  const { session, socket, processor } = await listen()
  socket.emit({ type: 'final', id: 'one', text: '保留这句话' })
  const pending = session.stop()
  processor.emit({ type: 'flushed', requestId: 1 })
  socket.onclose(); await pending
  assert.deepEqual(env.finals, ['保留这句话'])
  assert.equal(env.states.at(-1).state, 'error')
  assert.match(env.states.at(-1).detail, /核对最后一句/)
  assert.equal(env.tracksStopped, 1)
})

for (const failure of ['flush', 'final']) {
  test(`${failure} timeout reports uncertainty and releases all resources`, async () => {
    const { session, processor } = await listen()
    const pending = session.stop()
    if (failure === 'final') processor.emit({ type: 'flushed', requestId: 1 })
    env.fireTimer(failure === 'flush' ? 1000 : 6500)
    await pending
    assert.equal(env.states.at(-1).state, 'error')
    assert.match(env.states.at(-1).detail, /超时/)
    assert.equal(env.tracksStopped, 1)
    assert.equal(env.contextClosed, 1)
    assert.equal(env.timers.size, 0)
  })
}

test('connection timeout cancels pending permission and ignores later grant', async () => {
  let grant
  env.permission = () => new Promise(resolve => { grant = resolve })
  env.start(); env.fireTimer(15000); grant(env.stream); await tick()
  assert.equal(env.tracksStopped, 1)
  assert.equal(env.fetches, 0)
  assert.match(env.states.at(-1).detail, /连接超时/)
})

test('cloud authorization failure stops capture and preserves explicit backend reason', async () => {
  env.response = { ok: false, json: async () => ({ code: 403, message: '当前账号未开放语音输入' }) }
  env.start(); await tick()
  assert.equal(env.sockets.length, 0)
  assert.equal(env.tracksStopped, 1)
  assert.equal(env.states.at(-1).detail, '当前账号未开放语音输入')
})

test('cross-origin cloud URL is rejected before opening a socket', async () => {
  env.response = { ok: true, json: async () => ({ code: 200, data: { url: 'https://other.test/voice' } }) }
  env.start(); await tick()
  assert.equal(env.sockets.length, 0)
  assert.equal(env.tracksStopped, 1)
  assert.match(env.states.at(-1).detail, /地址无效/)
})

test('duplicate ready and final events do not create duplicate capture or text', async () => {
  const { session, socket } = await listen()
  socket.emit({ type: 'ready' })
  socket.emit({ type: 'final', id: 'same', text: '五人' })
  socket.emit({ type: 'final', id: 'same', text: '五人' })
  assert.equal(env.processors.length, 1)
  assert.deepEqual(env.finals, ['五人'])
  session.abort()
})

test('backpressure ends capture without unbounded buffering', async () => {
  const { socket, processor } = await listen()
  socket.bufferedAmount = 128001
  processor.emit(new ArrayBuffer(3200))
  assert.equal(env.tracksStopped, 1)
  assert.equal(env.states.at(-1).state, 'error')
  assert.match(env.states.at(-1).detail, /网络较慢/)
})

test('unsupported browser fails closed without silently using Web Speech', () => {
  window.AudioWorkletNode = undefined
  window.SpeechRecognition = class { constructor() { assert.fail('unexpected fallback') } }
  assert.equal(asrSupported(), false)
  assert.equal(createAsr({}), null)
})
