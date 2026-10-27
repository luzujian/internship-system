import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

function fixture() {
  const calls = { refresh: 0, expired: 0, retry: 0, login: 0, messages: [] }
  const auth = { token: 'old-access', role: 'ROLE_STUDENT', sessionId: 'session-1',
    user: { id: '1', username: 'student', isReadOnly: false },
    expireSession() { calls.expired++; this.token = null },
    async refreshAccessToken() { calls.refresh++; this.token = 'new-access'; return true } }
  const owner = () => JSON.stringify([auth.role, auth.user?.id, auth.user?.username, auth.user?.isReadOnly === true, auth.sessionId, auth.isLoggingOut === true])
  const handlers = {}
  const instance = Object.assign(async () => { calls.retry++; return 'retried' }, {
    interceptors: { request: { use(fn) { handlers.before = fn } },
      response: { use(ok, fail) { handlers.ok = ok; handlers.fail = fail } } }
  })
  const noop = () => {}
  class CanceledError extends Error {}
  const axios = { create: () => instance, isCancel: error => error instanceof CanceledError, CanceledError }
  const modules = {
    axios: { __esModule: true, default: axios },
    'element-plus': { ElMessage: { warning: text => calls.messages.push(text), error: text => calls.messages.push(text) } },
    './uploadErrorMessage': { getUploadErrorMessage: noop },
    '@/router': { __esModule: true, default: { replace() { calls.login++ }, push() { calls.login++ } } },
    '@/store/auth': { useAuthStore: () => auth },
    '@/store/auth/tabSession.mjs': { clearTabSession: noop }
  }
  const source = readFileSync(new URL('../src/utils/request.ts', import.meta.url), 'utf8')
    .replaceAll('import.meta.env', '({})')
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
  const module = { exports: {} }
  vm.runInNewContext(code, { module, exports: module.exports, require: name => {
    if (!modules[name]) throw new Error(name)
    return modules[name]
  }, console: { log: noop, warn: noop, error: noop }, localStorage: { removeItem: noop }, setTimeout })
  return { auth, calls, handlers, owner, api: module.exports, isCancel: axios.isCancel }
}

test('explicit backend idle timeout logs out immediately without automatic token refresh', async () => {
  const f = fixture()
  await assert.rejects(() => f.handlers.fail({ config: { _authOwner: f.owner() },
    response: { status: 401, data: { errorCode: 'SESSION_TIMEOUT' } } }))
  assert.equal(f.calls.refresh, 0); assert.equal(f.calls.retry, 0)
  assert.equal(f.calls.expired, 1); assert.equal(f.calls.login, 1)
})

test('refresh endpoint business timeout also requires a new login', async () => {
  const f = fixture()
  await assert.rejects(() => f.handlers.ok({ config: {}, data: {
    code: 401, message: '会话已超时，请重新登录', data: { errorCode: 'SESSION_TIMEOUT' }
  } }))
  assert.equal(f.calls.expired, 1); assert.equal(f.calls.refresh, 0)
})

test('ordinary access-token expiry can still refresh an active session and retry', async () => {
  const f = fixture()
  assert.equal(await f.handlers.fail({ config: { _authOwner: f.owner(), headers: {} },
    response: { status: 401, data: {} } }), 'retried')
  assert.equal(f.calls.refresh, 1); assert.equal(f.calls.retry, 1); assert.equal(f.calls.expired, 0)
})

test('a late timeout from an earlier login of the same account cannot log out the new session', async () => {
  const f = fixture(), oldOwner = f.owner()
  f.auth.sessionId = 'session-2'
  await assert.rejects(() => f.handlers.fail({ config: { _authOwner: oldOwner },
    response: { status: 401, data: { errorCode: 'SESSION_TIMEOUT' } } }))
  assert.equal(f.calls.expired, 0); assert.equal(f.calls.refresh, 0)
})

test('public refresh responses are bound to the session that requested them', async () => {
  const f = fixture()
  const config = f.handlers.before({ url: '/auth/refresh-token', method: 'post', headers: {} })
  assert.equal(config._authOwner, f.owner())
  f.auth.sessionId = 'new-session'
  await assert.rejects(() => f.handlers.ok({ config, data: { code: 401, data: { errorCode: 'SESSION_TIMEOUT' } } }))
  assert.equal(f.calls.expired, 0)
})

test('administrator preview may report activity while business writes remain blocked', async () => {
  const f = fixture(); f.auth.user.isReadOnly = true
  const config = f.handlers.before({ url: '/auth/session/activity', method: 'post', headers: {} })
  assert.equal(config.headers.Authorization, 'Bearer old-access')
  await assert.rejects(async () => f.handlers.before({ url: '/student/profile', method: 'put', headers: {} }))
})

test('logout stops new requests and ignores an earlier request timeout while retaining its credential for revocation', async () => {
  const f = fixture()
  const pending = f.handlers.before({ url: '/auth/session', method: 'get', headers: {} })
  f.auth.isLoggingOut = true
  assert.throws(() => f.handlers.before({ url: '/student/profile', method: 'get', headers: {} }), f.isCancel)
  await assert.rejects(() => f.handlers.fail({ config: pending,
    response: { status: 401, data: { errorCode: 'SESSION_TIMEOUT' } } }))
  const logout = f.handlers.before({ url: '/auth/logout', method: 'post', headers: {} })
  assert.equal(logout.headers.Authorization, 'Bearer old-access')
  assert.equal(f.calls.expired, 0); assert.equal(f.calls.refresh, 0)
  assert.deepEqual(f.calls.messages, [])
})

test('logout timeout responses do not warn or refresh, for both HTTP and business errors', async () => {
  for (const businessError of [false, true]) {
    const f = fixture()
    const config = f.handlers.before({ url: '/auth/logout', method: 'post', headers: {} })
    const data = { code: 401, message: '会话已超时，请重新登录', errorCode: 'SESSION_TIMEOUT' }
    await assert.rejects(() => businessError
      ? f.handlers.ok({ config, status: 200, data })
      : f.handlers.fail({ config, response: { status: 401, data } }))
    assert.equal(f.calls.expired, 0); assert.equal(f.calls.refresh, 0)
    assert.deepEqual(f.calls.messages, [])
  }
})

test('requests arriving after local logout cannot trigger repeated refresh or timeout messages', async () => {
  const f = fixture()
  f.auth.token = null; f.auth.user = null; f.auth.role = null
  await Promise.all(Array.from({ length: 5 }, () => assert.rejects(() => f.handlers.fail({
    config: { _authOwner: f.owner(), headers: {} }, response: { status: 401, data: {} }
  }))))
  f.api.handleLogout()
  assert.equal(f.calls.refresh, 0); assert.equal(f.calls.login, 0)
  assert.deepEqual(f.calls.messages, [])
})

test('concurrent genuine session timeouts show one message and redirect once', async () => {
  const f = fixture(), config = { _authOwner: f.owner() }
  await Promise.all(Array.from({ length: 5 }, () => assert.rejects(() => f.handlers.fail({
    config, response: { status: 401, data: { errorCode: 'SESSION_TIMEOUT' } }
  }))))
  assert.equal(f.calls.expired, 1); assert.equal(f.calls.login, 1)
  assert.deepEqual(f.calls.messages, ['会话已超时，请重新登录'])
})

test('a failed refresh produces only the session timeout message', async () => {
  const f = fixture()
  f.auth.refreshAccessToken = async () => {
    f.calls.refresh++
    try {
      await f.handlers.fail({ config: { url: '/auth/refresh-token', _authOwner: f.owner() },
        response: { status: 401, data: {} } })
    } catch { return false }
  }
  await assert.rejects(() => f.handlers.fail({ config: { _authOwner: f.owner(), headers: {} },
    response: { status: 401, data: {} } }))
  assert.equal(f.calls.refresh, 1)
  assert.deepEqual(f.calls.messages, ['会话已超时，请重新登录'])
})

test('an unexpected refresh failure rejects every waiting request', async () => {
  const f = fixture()
  let rejectRefresh
  f.auth.refreshAccessToken = () => new Promise((resolve, reject) => { rejectRefresh = reject })
  const unauthorized = () => f.handlers.fail({ config: { _authOwner: f.owner(), headers: {} },
    response: { status: 401, data: {} } })
  const first = unauthorized(), second = unauthorized()
  const settled = Promise.allSettled([first, second])
  rejectRefresh(new Error('refresh failed'))
  const results = await Promise.race([settled, new Promise(resolve => setTimeout(() => resolve(null), 100))])
  assert.ok(results, 'a waiting request was left pending')
  assert.ok(results.every(result => result.status === 'rejected'))
  assert.deepEqual(f.calls.messages, ['会话已超时，请重新登录'])
})
