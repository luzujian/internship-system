import test from 'node:test'
import assert from 'node:assert/strict'
import {
  missingProfileFields,
  profileIncompleteMessage
} from './studentProfileReadiness.js'

const completeProfile = () => ({
  name: '张三',
  studentId: '20230001',
  phone: '13800138000',
  email: 'zhangsan@example.com'
})

test('complete profile reports no missing fields', () => {
  assert.deepEqual(missingProfileFields(completeProfile()), [])
})

test('blank and absent fields are reported in fixed order', () => {
  const profile = completeProfile()
  profile.phone = '   '
  delete profile.email
  assert.deepEqual(missingProfileFields(profile), ['电话', '邮箱'])
})

test('missing profile object reports all required fields', () => {
  assert.deepEqual(missingProfileFields(null), ['姓名', '学号', '电话', '邮箱'])
})

test('numeric studentId counts as completed', () => {
  const profile = completeProfile()
  profile.studentId = 20230001
  assert.deepEqual(missingProfileFields(profile), [])
})

test('incomplete message lists missing fields and action', () => {
  assert.equal(
    profileIncompleteMessage(['学号', '邮箱'], '申请该岗位'),
    '个人资料尚未完善（缺少：学号、邮箱），请补全后再申请该岗位'
  )
  assert.equal(
    profileIncompleteMessage(['电话']),
    '个人资料尚未完善（缺少：电话），请补全后再提交申请'
  )
})
