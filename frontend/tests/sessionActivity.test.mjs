import test from 'node:test'
import assert from 'node:assert/strict'
import { createSessionActivityMonitor } from '../src/utils/sessionActivity.mjs'

function fixture(timeout = 15, offset = 0) {
  let now = 1_800_000_000_000, last = now + offset, timerId = 0, expired = 0, offline = false
  const tasks = new Map(), calls = []
  const flush = async () => { for (let i = 0; i < 8; i++) await Promise.resolve() }
  const options = {
    now: () => now,
    setTimer(fn, delay) { tasks.set(++timerId, { fn, at: now + delay }); return timerId },
    clearTimer(id) { tasks.delete(id) },
    expire() { expired++ },
    async request(activity) {
      calls.push({ activity, at: now })
      if (offline) throw new Error('offline')
      if (now + offset - last >= timeout * 60_000) throw { response: { status: 401 } }
      if (activity) last = now + offset
      return { lastActivityAt: last, timeoutMinutes: timeout, serverTime: now + offset }
    }
  }
  const monitor = createSessionActivityMonitor(options)
  async function advance(ms) {
    await flush()
    const end = now + ms
    for (let i = 0; i < 10_000; i++) {
      const next = [...tasks].sort((a, b) => a[1].at - b[1].at)[0]
      if (!next || next[1].at > end) { now = end; await flush(); return }
      now = next[1].at; tasks.delete(next[0]); await next[1].fn(); await flush()
    }
    throw new Error('timer did not settle')
  }
  return { monitor, advance, flush, calls, options, get expired() { return expired },
    offline() { offline = true }, timeout(value) { timeout = value } }
}

test('status polling never renews an idle session and it expires at the configured deadline', async () => {
  const f = fixture()
  await f.advance(15 * 60_000)
  assert.equal(f.expired, 1)
  assert.ok(f.calls.length >= 15)
  assert.equal(f.calls.filter(call => call.activity).length, 0)
  await f.advance(10 * 60_000)
  assert.equal(f.expired, 1)
})

test('real activity keeps the session active well beyond token issuance age', async () => {
  const f = fixture()
  for (let i = 0; i < 12; i++) {
    await f.advance(14 * 60_000); f.monitor.activity(); await f.advance(1)
    assert.equal(f.expired, 0)
  }
  await f.advance(15 * 60_000)
  assert.equal(f.expired, 1)
})

test('frequent input is throttled and never becomes an endless background keepalive', async () => {
  const f = fixture()
  for (let i = 0; i < 120; i++) {
    f.monitor.activity(); await f.advance(1000)
  }
  assert.ok(f.calls.filter(call => call.activity).length <= 9)
  await f.advance(16 * 60_000)
  assert.equal(f.expired, 1)
})

test('another tab using the same session prevents a false timeout in an idle tab', async () => {
  const f = fixture(), second = createSessionActivityMonitor(f.options)
  await f.advance(14 * 60_000); f.monitor.activity(); await f.advance(1)
  await f.advance(2 * 60_000)
  assert.equal(f.expired, 0)
  second.stop(); f.monitor.stop()
})

test('server clock offset and live timeout changes are respected', async () => {
  const f = fixture(120, 7 * 24 * 60 * 60_000)
  await f.advance(20 * 60_000)
  assert.equal(f.expired, 0)
  f.timeout(15)
  await f.advance(60_000)
  assert.equal(f.expired, 1)
})

test('offline sessions still leave the page at the last known deadline', async () => {
  const f = fixture()
  await f.flush(); f.offline()
  await f.advance(15 * 60_000)
  assert.equal(f.expired, 1)
})

test('stopping a monitor prevents a late response from affecting the next account', async () => {
  let resolve, expired = 0, timers = 0
  const monitor = createSessionActivityMonitor({ request: () => new Promise(done => { resolve = done }),
    expire: () => expired++, setTimer: () => ++timers, clearTimer() {} })
  monitor.stop(); resolve({ lastActivityAt: 0, timeoutMinutes: 0, serverTime: Date.now() })
  await Promise.resolve(); await Promise.resolve()
  assert.equal(expired, 0); assert.equal(timers, 0)
})
