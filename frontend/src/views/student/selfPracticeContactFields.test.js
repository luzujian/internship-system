import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'

const home = fs.readFileSync(new URL('./Home.vue', import.meta.url), 'utf8')
const applications = fs.readFileSync(new URL('./Applications.vue', import.meta.url), 'utf8')
const approval = fs.readFileSync(new URL('../teacher/Approval.vue', import.meta.url), 'utf8')
const approvalApi = fs.readFileSync(new URL('../../api/teacherApproval.ts', import.meta.url), 'utf8')

test('self-practice form requires and submits city and HR contact fields', () => {
  for (const field of ['companyCity', 'hrManagerName', 'hrManagerPhone']) {
    assert.match(home, new RegExp(`v-model="selfPracticeForm\\.${field}"`))
    assert.match(home, new RegExp(`applicationData\\.${field} = selfPracticeForm\\.value\\.${field}\\.trim\\(\\)`))
  }

  assert.match(home, /请填写单位所在城市/)
  assert.match(home, /请填写人事经理姓名/)
  assert.match(home, /请填写人事经理联系电话/)
})

test('student and teacher details expose the persisted self-practice contact fields', () => {
  assert.match(applications, /app\.companyCity \|\| app\.location/)
  assert.match(applications, /currentApplication\.hrManagerName/)
  assert.match(applications, /currentApplication\.hrManagerPhone/)
  assert.match(approval, /currentApplication\.companyCity/)
  assert.match(approval, /currentApplication\.hrManagerName/)
  assert.match(approval, /currentApplication\.hrManagerPhone/)
  assert.match(approvalApi, /companyCity\?: string/)
  assert.match(approvalApi, /hrManagerName\?: string/)
  assert.match(approvalApi, /hrManagerPhone\?: string/)
})

test('teacher self-practice details render two fields per row except the reason', () => {
  assert.doesNotMatch(approval, /form-group quarter/)
  assert.match(approval, /<label>姓名：<\/label>[\s\S]*?<label>学号：<\/label>/)
  assert.match(approval, /<label>年级：<\/label>[\s\S]*?<label>班级：<\/label>/)
  assert.match(approval, /<label>实习单位：<\/label>[\s\S]*?<label>实习岗位：<\/label>/)
  assert.match(approval, /<label>单位所在城市：<\/label>[\s\S]*?<label>申请时间：<\/label>/)
  assert.match(approval, /<label>人事经理姓名：<\/label>[\s\S]*?<label>人事经理联系电话：<\/label>/)
  assert.match(approval, /<div class="form-group">\s*<label>申请理由：<\/label>/)
})

test('self-practice company field steers students to the registered cooperative name', () => {
  assert.match(home, /<el-autocomplete/)
  assert.match(home, /\/student\/applications\/company-matches/)
  assert.match(home, /queryCooperativeCompanies/)
  assert.match(home, /matchedCooperativeCompany/)
  assert.match(home, /similarCooperativeCompanies/)
  assert.match(home, /校企合作单位/)
  // 精确命中校企合作企业允许提交，不再直接禁用提交按钮
  assert.doesNotMatch(home, /:disabled="!!matchedCooperativeCompany"/)
  assert.doesNotMatch(home, /该企业属于校企合作单位，请前往职位浏览选择岗位/)
  // 名称相近但未精确命中时，必须从下拉候选中选择系统登记的标准名称
  assert.match(home, /请从下拉候选中选择系统登记的标准企业名称/)
  assert.match(home, /:disabled="!matchedCooperativeCompany && similarCooperativeCompanies\.length > 0"/)
  assert.match(home, /ensureSelfPracticeCompanyName/)
})

test('teacher approval surfaces cooperative company marker for self-practice applications', () => {
  assert.match(approval, /scope\.row\.cooperativeCompany/)
  assert.match(approval, /currentApplication\.cooperativeCompany/)
  assert.match(approval, /校企合作企业/)
  assert.match(approvalApi, /cooperativeCompany\?: boolean/)
})
