import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'

const read = (relativePath) => fs.readFileSync(new URL(relativePath, import.meta.url), 'utf8')
const registerController = read('../../../../backend/src/main/java/com/internship/controller/CompanyRegisterController.java')
const companyCheck = read('../CompanyCheck.vue')
const withdrawalRecords = read('../admin/ApplicationWithdrawalRecordManagement.vue')
const companyController = read('../../../../backend/src/main/java/com/internship/controller/CompanyUserController.java')

test('企业撤回原因提交后同步自动允许修改，不进入 AI 或人工审核', () => {
  assert.match(registerController, /setRecallStatus\(2\)/)
  assert.match(registerController, /setRecallAuditRemark\("自动允许撤回修改"\)/)
  assert.match(registerController, /setRecallReviewerId\(null\)/)
  assert.doesNotMatch(registerController, /CompletableFuture|isStandardRecallReason|AIRecallAuditService/)

  assert.match(companyCheck, /撤回申请已自动允许/)
  assert.match(companyCheck, /无需等待人工审核/)
  assert.doesNotMatch(companyCheck, /系统自动审核|管理员审核|转交管理员|startRecallPolling|recallPendingDialogVisible/)

  assert.match(withdrawalRecords, /撤回处理状态/)
  assert.match(withdrawalRecords, /允许修改时间/)
  assert.doesNotMatch(companyController, /@PutMapping\("\/\{id\}\/recall-audit"\)/)
})
