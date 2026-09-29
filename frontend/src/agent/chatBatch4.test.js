import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"

const dir = dirname(fileURLToPath(import.meta.url))
const storeSrc = readFileSync(join(dir, "store.ts"), "utf8")
const workspaceSrc = readFileSync(join(dir, "..", "components", "agent", "AgentWorkspace.vue"), "utf8")

test("session history supports list, switch, rename, archive and delete", () => {
  assert.match(storeSrc, /async function loadSessions/)
  assert.match(storeSrc, /async function switchSession/)
  assert.match(storeSrc, /async function renameSession/)
  assert.match(storeSrc, /async function archiveSession/)
  assert.match(storeSrc, /async function deleteSession/)
  assert.match(storeSrc, /\/agent\/sessions/)
})

test("session history is owner scoped and listed on initialize", () => {
  assert.match(storeSrc, /request\.get\('\/agent\/sessions'/)
  assert.match(storeSrc, /void loadSessions\(\)/)
})

test("deleting a chat never cascades to business data or audit", () => {
  const start = storeSrc.indexOf("async function deleteSession")
  const end = storeSrc.indexOf("async function switchSession")
  const body = storeSrc.slice(start, end)
  assert.ok(!/job-applications|applications|autoFlow|TeacherAutoTask/.test(body), "delete must not touch business or automation endpoints")
  assert.match(workspaceSrc, /不会删除业务记录、审计或已启用的自动化任务/)
})

test("running session cannot be deleted silently", () => {
  assert.match(storeSrc, /hasRunningTask/)
  assert.match(storeSrc, /仍有任务在执行/)
})

test("switching sessions re-reads history and never replays actions", () => {
  const start = storeSrc.indexOf("async function switchSession")
  const body = storeSrc.slice(start, start + 1200)
  assert.match(body, /sessions\/\$\{id\}\/tasks/)
  assert.doesNotMatch(body, /request\.(post|put|delete)\(/, "switching must not replay business actions")
  assert.match(body, /applyDialog\.value\.open = false/, "the previous confirmation must close")
})

test("history panel keeps rename and delete icons without archive controls", () => {
  assert.match(workspaceSrc, /session-history/)
  assert.match(workspaceSrc, /renameFromHistory/)
  assert.match(workspaceSrc, /deleteFromHistory/)
  assert.doesNotMatch(workspaceSrc, /归档/)
  assert.match(workspaceSrc, /aria-label="重命名"/)
  assert.match(workspaceSrc, /只会删除聊天记录/)
})
