// Only explicit user activity renews the session. Status polling never does.
export function createSessionActivityMonitor({ request, expire, now = Date.now,
  setTimer = setTimeout, clearTimer = clearTimeout }) {
  const ACTIVITY_INTERVAL = 15_000
  const STATUS_INTERVAL = 60_000
  let stopped = false, inFlight = false, pendingActivity = false, timer
  let deadline = Infinity, nextCheck = now(), lastActivitySent = -Infinity

  function schedule() {
    clearTimer(timer)
    if (stopped) return
    const activityDue = pendingActivity ? lastActivitySent + ACTIVITY_INTERVAL : Infinity
    const due = Math.min(deadline, nextCheck, activityDue)
    timer = setTimer(sync, Math.max(0, due - now()))
  }

  async function sync() {
    if (stopped || inFlight) return
    inFlight = true
    clearTimer(timer)
    const activity = pendingActivity
    pendingActivity = false
    if (activity) lastActivitySent = now()
    try {
      const state = await request(activity)
      if (stopped) return
      const remaining = state.lastActivityAt + state.timeoutMinutes * 60_000 - state.serverTime
      if (!Number.isFinite(remaining) || remaining <= 0) {
        stop(); expire(); return
      }
      // Use the server's remaining duration so workstation clock skew is harmless.
      deadline = now() + remaining
      nextCheck = now() + STATUS_INTERVAL
    } catch (error) {
      if (stopped) return
      if (error?.response?.status === 401 || now() >= deadline) {
        stop(); expire(); return
      }
      nextCheck = now() + ACTIVITY_INTERVAL
    } finally {
      inFlight = false
      schedule()
    }
  }

  function activity() {
    if (stopped) return
    pendingActivity = true
    if (!inFlight) schedule()
  }

  function stop() { stopped = true; clearTimer(timer) }
  void sync()
  return { activity, check: sync, stop }
}
