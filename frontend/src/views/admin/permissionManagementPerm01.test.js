import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'

const read = relativePath => fs.readFileSync(new URL(relativePath, import.meta.url), 'utf8')

const source = read('./PermissionManagement.vue')

test('user override editor reloads from server before allowing save', () => {
  assert.match(source, /const loadUserOverride = async/)
  assert.match(source, /overrideReady\.value = false/)
  assert.match(source, /overrideReady\.value = true/)
  assert.match(source, /if \(!user \|\| !overrideReady\.value \|\| overrideSaving\.value\) return/)
})

test('saving override re-reads config and user override before success toast', () => {
  assert.match(source, /if \(!await fetchAgentCaps\(\) \|\| !await loadUserOverride\(user\)\)/)
  assert.match(source, /覆盖已保存，请重新选择用户核对/)
  assert.doesNotMatch(source, /用户级覆盖已保存，该用户下次加载时生效/)
})

test('role save re-reads config before success toast', () => {
  assert.match(source, /if \(!await fetchAgentCaps\(\)\) \{/)
  assert.match(source, /配置已保存，但重查失败/)
})

test('read failure blocks stale editor state and surfaces error', () => {
  assert.match(source, /读取用户覆盖失败，请重新选择用户后重试/)
  assert.match(source, /return false/)
  assert.match(source, /if \(selectedOverrideUser\.value\?\.id !== user\.id \|\| overrideRole\.value !== role\) return false/)
})

test('saving override disables concurrent selection until settled', () => {
  assert.match(source, /if \(overrideSaving\.value\) return/)
  assert.match(source, /const overrideSaving = ref\(false\)/)
})

test('clear previews an empty replacement rather than writing stale defaults', () => {
  assert.match(source, /const clearUserOverride = \(\) => submitUserOverride\(\{\}\)/)
  assert.match(source, /userChanges: \[\{ role: overrideRole.value, userId: user.id, overrides, replace: true \}\]/)
  assert.match(source, /agent-capabilities\/config\/preview/)
})
