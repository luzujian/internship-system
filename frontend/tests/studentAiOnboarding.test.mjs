import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
import { createPinia } from 'pinia'
import { effectScope, ref, nextTick } from 'vue'
import * as protocol from '../src/agent/protocol.mjs'
import * as sendRouting from '../src/agent/sendRouting.mjs'

const require = createRequire(import.meta.url)
const key = 'deepintern.agent.auto-open-student-login'
const source = readFileSync(new URL('../src/agent/store.ts', import.meta.url), 'utf8')
const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
const memory = () => {
  const values = new Map()
  return { getItem: k => values.get(k) ?? null, setItem: (k, v) => values.set(k, String(v)), removeItem: k => values.delete(k),
    key: i => [...values.keys()][i], get length() { return values.size } }
}
const flush = async () => { await new Promise(resolve => setImmediate(resolve)); await nextTick() }
function fixture(t, server = { generation: 1, seen: new Map() }, options = {}) {
  const scope = effectScope(), module = { exports: {} }, sessionStorage = memory()
  let caps = { enabled: true, actor: { id: 8, ownerKey: 'student:8', role: 'ROLE_STUDENT', readOnly: false },
    capabilities: { agent_chat: true, internship_plan: true }, ...options }
  const claims = [], preferences = []
  const request = {
    get: async url => ({ data: url === '/agent/capabilities' ? caps : url === '/agent/student-plan/journey'
      ? { autoOpen: true, needsChoice: true, canChoose: true, reliable: true, preferenceVersion: 0, steps: [] } : [] }),
    post: async url => {
      assert.equal(url, '/agent/student-login-onboarding'); claims.push(caps.actor.id)
      if (server.defer) return server.defer
      if (server.fail) throw new Error('offline')
      const autoOpen = server.seen.get(caps.actor.id) !== server.generation
      server.seen.set(caps.actor.id, server.generation)
      return { data: { autoOpen } }
    },
    put: async (...args) => { preferences.push(args); return { data: {} } }
  }
  vm.runInNewContext(code, { module, exports: module.exports, console, localStorage: memory(), sessionStorage,
    window: { setTimeout, clearTimeout }, AbortController, setTimeout, clearTimeout,
    require: name => name === '@/utils/request' ? { __esModule: true, default: request, getAccessToken: () => 'fixture-token' }
      : name === './protocol.mjs' ? protocol : name === './sendRouting.mjs' ? sendRouting
        : name === './formContext' ? { currentForm: ref(null), formBusinessReceipt: ref(null), setFormSession() {} } : require(name) })
  const store = scope.run(() => module.exports.useAgentStore(createPinia()))
  t.after(() => { store.$dispose(); scope.stop() })
  return { store, claims, preferences, sessionStorage, server,
    async login() { sessionStorage.setItem(key, caps.actor.id); await store.initialize(); await flush() },
    setCaps(value) { caps = value } }
}

test('only the first real student login in each AI activation opens both panels', async t => {
  const f = fixture(t)
  await f.login()
  assert.equal(f.store.visible, true); assert.equal(f.store.studentLoginAutoOpenJourney, true)
  assert.equal(f.sessionStorage.getItem(key), null)
  f.store.dispose(); await f.login()
  assert.equal(f.store.visible, false); assert.equal(f.store.studentLoginAutoOpenJourney, false)
  f.server.generation++
  f.store.dispose(); await f.login()
  assert.equal(f.store.visible, true); assert.equal(f.store.studentLoginAutoOpenJourney, true)
  assert.equal(f.preferences.length, 0, 'showing the introduction does not change internship route preferences')
})

test('page reload and plan polling cannot open either panel or consume a login prompt', async t => {
  const f = fixture(t)
  await f.store.initialize(); await f.store.refreshPlan(); await flush()
  assert.equal(f.store.visible, false); assert.equal(f.store.studentLoginAutoOpenJourney, false)
  assert.equal(f.claims.length, 0); assert.equal(f.preferences.length, 0)
  await f.login()
  f.store.visible = false; f.store.studentLoginAutoOpenJourney = false
  await f.store.refreshPlan(); await f.store.initialize(); await flush()
  assert.equal(f.store.visible, false); assert.equal(f.store.studentLoginAutoOpenJourney, false)
  assert.equal(f.claims.length, 1)
})

test('another browser shares the server receipt while each student gets a separate prompt', async t => {
  const server = { generation: 1, seen: new Map() }
  const first = fixture(t, server); await first.login()
  const otherBrowser = fixture(t, server); await otherBrowser.login()
  assert.equal(otherBrowser.store.visible, false)
  const otherStudent = fixture(t, server, { actor: { id: 9, ownerKey: 'student:9', role: 'ROLE_STUDENT', readOnly: false } })
  await otherStudent.login(); assert.equal(otherStudent.store.visible, true)
})

test('disabled AI, missing chat permission, other roles and read-only identities never claim a prompt', async t => {
  for (const options of [{ enabled: false }, { capabilities: { agent_chat: false } },
    { actor: { id: 8, ownerKey: 'teacher:8', role: 'ROLE_TEACHER', readOnly: false } },
    { actor: { id: 8, ownerKey: 'student:8', role: 'ROLE_STUDENT', readOnly: true } }]) {
    const f = fixture(t, undefined, options); await f.login()
    assert.equal(f.claims.length, 0); assert.equal(f.store.visible, false)
    assert.equal(f.sessionStorage.getItem(key), null)
  }
})

test('a login flag for a different student cannot prompt the current account', async t => {
  const f = fixture(t); f.sessionStorage.setItem(key, '9')
  await f.store.initialize(); await flush()
  assert.equal(f.claims.length, 0); assert.equal(f.store.visible, false)
})

test('late onboarding responses cannot reopen the sidebar after logout', async t => {
  let resolve
  const server = { defer: new Promise(done => { resolve = done }) }
  const f = fixture(t, server); await f.login(); f.store.dispose()
  resolve({ data: { autoOpen: true } }); await flush()
  assert.equal(f.store.visible, false); assert.equal(f.store.studentLoginAutoOpenJourney, false)
})

test('failed onboarding keeps the manual entry and allows a later login to retry', async t => {
  const f = fixture(t); f.server.fail = true; await f.login()
  assert.equal(f.store.visible, false); assert.equal(f.store.capabilities.enabled, true)
  f.server.fail = false; f.store.dispose(); await f.login()
  assert.equal(f.store.visible, true)
})
