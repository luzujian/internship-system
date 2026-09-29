import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../src/views/CompanyCheck.vue', import.meta.url), 'utf8')

test('被驳回的企业注册状态必须打开驳回结果弹窗并展示教师驳回理由', () => {
  assert.match(source, /v-model="auditRejectedDialogVisible"/)
  assert.match(source, /title="审核未通过"/)
  assert.match(source, /驳回理由：\{\{ companyInfo\.auditRemark \|\| '未提供驳回理由' \}\}/)
  assert.match(source, /else if \(status === 2\) \{\s*auditRejectedDialogVisible\.value = true/)
})

test('无法识别的注册状态不能静默无反应', () => {
  assert.match(source, /无法识别企业注册状态，请稍后重试/)
})
