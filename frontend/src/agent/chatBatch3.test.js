import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"

const dir = dirname(fileURLToPath(import.meta.url))
const storeSrc = readFileSync(join(dir, "store.ts"), "utf8")
const workspaceSrc = readFileSync(join(dir, "..", "components", "agent", "AgentWorkspace.vue"), "utf8")
const formSrc = readFileSync(join(dir, "formContext.ts"), "utf8")

function applyBlock() {
  const start = storeSrc.indexOf("const applyDialog = ref({")
  const end = storeSrc.indexOf("const autoFlow = ref({")
  assert.ok(start > 0 && end > start, "apply dialog block must exist")
  return storeSrc.slice(start, end)
}

test("confirmation card carries a stable action identity and preview time", () => {
  const block = applyBlock()
  assert.match(block, /job-applications\/preview/)
  assert.match(block, /response.data.previewedAt/)
})

test("confirmation reuses the same action identity across retries", () => {
  const block = applyBlock()
  assert.match(block, /actionId: dialog\.actionId/)
  assert.match(block, /previewedAt: dialog\.previewedAt/)
  const posts = block.match(/clientRequestId: newRequestId\(\)/g) || []
  assert.equal(posts.length, 1, "only the per-attempt request id may be regenerated")
})

test("stale preview is rejected and must be re-previewed", () => {
  const block = applyBlock()
  assert.match(block, /PREVIEW_EXPIRED/)
  assert.ok(block.includes("dialog.actionId = ''"), "expired preview clears the action identity")
})

test("unknown outcome verifies before allowing another submit", () => {
  const block = applyBlock()
  assert.match(block, /verifyState = .pending./)
  assert.ok(block.includes("结果待核实"))
  assert.ok(block.includes("上次提交结果尚未核实"))
  assert.match(block, /async function verifyApply/)
  assert.ok(workspaceSrc.includes("刷新状态"))
  assert.ok(workspaceSrc.includes("verifyState === 'pending'"))
})

test("field ledger protects against edit-then-revert by revision, not by value", () => {
  assert.match(formSrc, /fieldRevisions/)
  assert.match(formSrc, /fillBatchId/)
  const undoStart = formSrc.indexOf("export function undoLast")
  const undoBody = formSrc.slice(undoStart, undoStart + 1400)
  assert.ok(undoBody.includes("fieldRevisions.get(key)"), "undo must compare revisions")
  assert.ok(!undoBody.includes("valuesEqual(model[prop], latest.appliedValues[prop])"), "undo must not decide by value equality")
})

test("stopping chat generation never touches background automations", () => {
  const start = storeSrc.indexOf("async function cancel()")
  const end = storeSrc.indexOf("function isCancelling")
  const cancelBody = storeSrc.slice(start, end)
  assert.doesNotMatch(cancelBody, /request\.(post|put|delete)\([^\n]*auto-tasks/, "cancel must not write background automation state")
  assert.ok(!cancelBody.includes("resetAutoFlow"), "cancel must not reset automation state")
})

test("voice ends in editable draft without extra review UI or automatic sending", () => {
  const start = workspaceSrc.indexOf("async function finishRecording")
  const end = workspaceSrc.indexOf("function toggleMic", start)
  const body = workspaceSrc.slice(start, end)
  assert.match(body, /await session.stop\(\)/)
  assert.doesNotMatch(body, /send\(/)
  assert.match(body, /stopAsr\(true\)/)
  assert.match(workspaceSrc, /agent\.draft = \[base, transcript\]/)
  assert.doesNotMatch(workspaceSrc, /已转写，可核对修改后再发送/)
})
