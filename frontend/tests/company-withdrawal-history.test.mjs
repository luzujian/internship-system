import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(
  new URL('../src/views/admin/ApplicationWithdrawalRecordManagement.vue', import.meta.url),
  'utf8'
)

test('已完成撤回后重新提交，历史记录仍按已自动允许修改展示', () => {
  assert.match(source, /0: '已自动允许修改'/)
  assert.match(source, /2: '已自动允许修改'/)
})
