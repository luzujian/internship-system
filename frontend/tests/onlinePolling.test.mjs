import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { reactive, ref, shallowRef, computed, watch } from 'vue'

const flush = async () => { for (let i = 0; i < 8; i++) await Promise.resolve() }
function fixture(load) {
  const auth = reactive({ token: 'token', isAuthenticated: true, isLoggingOut: false,
    role: 'ROLE_ADMIN', user: { username: 'admin' }, sessionId: 'sid-1', allowed: true,
    hasPermission() { return this.allowed } })
  const timers = new Map(), listeners = new Map(), lifecycle = {}
  const document = { visibilityState: 'visible', addEventListener: (name, fn) => listeners.set(name, fn),
    removeEventListener: name => listeners.delete(name) }
  const source = readFileSync(new URL('../src/composables/useOnlinePolling.ts', import.meta.url), 'utf8')
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, document, AbortController,
    setInterval(fn, delay) { assert.equal(delay, 15000); const id = Symbol(); timers.set(id, fn); return id },
    clearInterval: id => timers.delete(id), require: name => name === 'vue'
      ? { reactive, ref, shallowRef, computed, watch, onMounted: fn => { lifecycle.mount = fn }, onUnmounted: fn => { lifecycle.unmount = fn } }
      : { useAuthStore: () => auth } })
  const state = module.exports.useOnlinePolling(load)
  return { auth, state, timers, listeners, mount: () => lifecycle.mount(), unmount: () => lifecycle.unmount(),
    visibility(value) { document.visibilityState = value; listeners.get('visibilitychange')?.() } }
}

test('online polling runs only while mounted, visible and authorized, then resumes immediately', async () => {
  let calls = 0
  const f = fixture(async () => ++calls)
  assert.equal(calls, 0)
  f.mount(); await flush(); assert.equal(calls, 1); assert.equal(f.timers.size, 1)
  f.visibility('hidden'); assert.equal(f.timers.size, 0)
  f.visibility('visible'); await flush(); assert.equal(calls, 2)
  f.auth.isLoggingOut = true; assert.equal(f.timers.size, 0); assert.equal(f.state.data.value, undefined)
  f.unmount(); assert.equal(f.listeners.size, 0)
})

test('a canceled or earlier-account response cannot overwrite the current online list', async () => {
  const pending = []
  const f = fixture(signal => new Promise(resolve => pending.push({ signal, resolve })))
  f.mount(); const first = pending[0]
  f.auth.sessionId = 'sid-2'; const second = pending[1]
  assert.equal(first.signal.aborted, true)
  second.resolve('new login data'); await flush()
  first.resolve('old login data'); await flush()
  assert.equal(f.state.data.value, 'new login data')
  f.unmount(); assert.equal(second.signal.aborted, true)
})

test('fetch failure retains the last successful counts instead of replacing them with zero', async () => {
  let fail = false
  const f = fixture(async () => { if (fail) throw new Error('offline'); return { totalOnline: 12 } })
  f.mount(); await flush(); fail = true
  await f.state.refresh()
  assert.equal(f.state.data.value.totalOnline, 12)
  assert.equal(f.state.error.value, '在线状态暂时无法获取')
  f.unmount()
})

test('permission loss hides cached data and server denial stops repeated requests', async () => {
  let calls = 0, deny = false
  const f = fixture(async () => { calls++; if (deny) throw { response: { status: 403 } }; return 'private list' })
  f.mount(); await flush(); f.auth.allowed = false
  assert.equal(f.state.data.value, undefined); assert.equal(f.timers.size, 0)
  f.auth.allowed = true; await flush(); assert.equal(calls, 2)
  deny = true; await f.state.refresh()
  assert.equal(f.state.data.value, undefined); assert.equal(f.timers.size, 0)
  assert.equal(f.state.error.value, '暂无在线用户查看权限')
  f.unmount()
})
