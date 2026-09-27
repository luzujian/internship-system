import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'

const approvalSource = fs.readFileSync(new URL('./Approval.vue', import.meta.url), 'utf8')
const registrationSource = fs.readFileSync(new URL('../CompanyRegister.vue', import.meta.url), 'utf8')

const registrationFields = [
  { label: '企业名称', prop: 'companyName' },
  { label: '统一社会信用代码', prop: 'creditCode' },
  { label: '所属行业', prop: 'industry' },
  { label: '企业规模', prop: 'scale' },
  { label: '联系人', prop: 'contactPerson' },
  { label: '联系电话', prop: 'contactPhone' },
  { label: '企业地址', prop: 'province' },
  { label: '联系邮箱', prop: 'contactEmail' },
  { label: '个人邮箱', prop: 'email' },
  { label: '企业网站', prop: 'website' },
  { label: '企业简介', prop: 'introduction' },
  { label: '挂牌实习基地', prop: 'isInternshipBase' },
  { label: '接受兜底', prop: 'acceptBackup' },
  { label: '是否校友单位', prop: 'isAlumniUnit' },
  { label: '校友信息', prop: 'alumniMembers' },
  { label: '是否与学校签署实训基地的协议', prop: 'hasTrainingBaseAgreement' }
]

test('企业注册审核查看弹窗字段与注册申请一致', () => {
  const start = approvalSource.indexOf('<!-- 企业注册申请审核查看弹窗 -->')
  const end = approvalSource.indexOf('<!-- 申请详情查看弹窗 -->', start)
  assert.notEqual(start, -1)
  assert.notEqual(end, -1)

  const modalSource = approvalSource.slice(start, end)
  for (const { label, prop } of registrationFields) {
    assert.ok(registrationSource.includes(`prop="${prop}"`), `注册申请应包含字段：${label}`)
    assert.ok(modalSource.includes(`<label>${label}：`), `审核查看弹窗缺少字段：${label}`)
  }

  for (const extraLabel of ['申请时间', '资质文件', '营业执照', '法人身份证', '牌匾照片']) {
    assert.ok(!modalSource.includes(`<label>${extraLabel}：`), `审核查看弹窗不应显示注册申请外字段：${extraLabel}`)
  }

  assert.match(modalSource, /v-if="currentApplication\.isAlumniUnit === 1"/)
  assert.match(modalSource, /currentApplication\.alumniMembers/)
})