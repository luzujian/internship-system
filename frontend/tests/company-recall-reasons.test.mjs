import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8')
const options = JSON.parse(read('../src/config/companyRecallReasons.json'))
const formSource = read('../src/views/CompanyRegister.vue')
const checkSource = read('../src/views/CompanyCheck.vue')
const controllerSource = read('../../backend/src/main/java/com/internship/controller/CompanyRegisterController.java')
const aiAuditSource = read('../../backend/src/main/java/com/internship/service/impl/AIRecallAuditServiceImpl.java')

test('企业注册撤回预设原因逐项对应注册表单字段', () => {
  assert.ok(options.length >= 12)
  assert.equal(new Set(options.map(item => item.value)).size, options.length)

  for (const option of options) {
    assert.match(option.label, /注册表单|填写|选择/, option.value)
    if (option.value === 'other') {
      assert.deepEqual(option.fields, [])
      continue
    }
    assert.ok(option.fields.length > 0, option.value)
    for (const field of option.fields) {
      assert.ok(formSource.includes(field.fragment), `${option.value} 未对应注册表单字段：${field.label}`)
    }
  }

  const presetText = options.map(item => item.label).join('\n')
  for (const unrelated of ['营业执照', '法人身份证', '牌匾照片', '手机号填写错误']) {
    assert.equal(presetText.includes(unrelated), false, `预设原因包含注册表单外内容：${unrelated}`)
  }
})

test('弹窗使用字段化原因配置，提交后直接允许修改且不进入审核', () => {
  assert.ok(checkSource.includes("import recallReasonOptions from '../config/companyRecallReasons.json'"))
  assert.ok(checkSource.includes('v-for="item in recallReasonOptions"'))
  assert.equal(checkSource.includes('business_license_blur'), false)
  assert.match(checkSource, /撤回申请已自动允许/)
  assert.doesNotMatch(checkSource, /系统自动审核|管理员审核|转交管理员|startRecallPolling/)

  assert.match(controllerSource, /setRecallStatus\(2\)/)
  assert.match(controllerSource, /自动允许撤回修改/)
  for (const option of options.filter(item => item.value !== 'other')) {
    assert.equal(controllerSource.includes(`"${option.label}"`), false, `后端不应按预设原因分流：${option.label}`)
    assert.equal(aiAuditSource.includes(option.label), false, `企业撤回仍残留 AI 审核口径：${option.label}`)
  }
  assert.doesNotMatch(aiAuditSource, /autoAuditRecall|AI_AUDIT_SYSTEM_PROMPT/)
})
