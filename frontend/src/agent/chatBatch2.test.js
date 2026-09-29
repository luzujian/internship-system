import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"

const dir = dirname(fileURLToPath(import.meta.url))
const storeSrc = readFileSync(join(dir, "store.ts"), "utf8")
const typesSrc = readFileSync(join(dir, "types.ts"), "utf8")
const workspaceSrc = readFileSync(join(dir, "..", "components", "agent", "AgentWorkspace.vue"), "utf8")

test("revision fields travel with tasks", () => {
  assert.match(typesSrc, /editOf/)
  assert.match(typesSrc, /editRound/)
  assert.match(workspaceSrc, /editOf/)
})

test("resend reuses identity, waits for old generation, never force-unlocks", () => {
  assert.match(storeSrc, /tasks\/\$\{id\}\/resend/)
  assert.match(storeSrc, /waitForIdle/)
  assert.match(storeSrc, /旧任务仍在停止中/)
  assert.match(storeSrc, /newRequestId\(\)/)
})

test("version switching is display-only", () => {
  const body = workspaceSrc.slice(workspaceSrc.indexOf("function jumpToTask"), workspaceSrc.indexOf("function startEdit"))
  assert.ok(body.includes("selectedVersions.value"))
  assert.ok(!body.includes("request."))
  assert.ok(!body.includes("resendTask"))
  assert.ok(!body.includes("agent.ask"))
})

test("edit mode uses the concise send action and handles escape first", () => {
  assert.match(workspaceSrc, /@click="saveEdit">发送/)
  assert.doesNotMatch(workspaceSrc, /修改消息不会撤销已完成的操作/)
  assert.match(workspaceSrc, /closest\?\.\('.msg-edit/)
  assert.match(workspaceSrc, /keydown.escape.stop=.cancelEdit/)
})

test("regenerate does not duplicate the user message bubble", () => {
  assert.match(workspaceSrc, /repeatsQuestion/)
  assert.match(workspaceSrc, /groups.set/)
  assert.match(workspaceSrc, /version-note/)
})
test("retry icon is limited to failed answers and keeps the existing resend flow", () => {
  assert.match(workspaceSrc, /task\.status === 'FAILED'/)
  assert.match(workspaceSrc, /v-if="canRepeatTask\(task\)"/)
  assert.match(workspaceSrc, /aria-label="重试回复"/)
  assert.match(workspaceSrc, /复制回答/)
  assert.match(workspaceSrc, /resendSame/)
})