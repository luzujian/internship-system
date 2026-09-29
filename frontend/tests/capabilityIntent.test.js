import test from 'node:test'
import assert from 'node:assert/strict'
import { capabilityRequest } from '../src/agent/capabilityIntent.mjs'

test('explicit administrator requests route to a preview', () => {
  for (const text of ['帮我给学生赋予自动化任务的权限', '给学生授予自动化任务权限', '给企业开通填表权限', '关闭学生的语音输入，保留聊天功能', '允许企业使用 AI 填表', '教师自动提醒只允许站内通知，不允许邮件', '能不能帮我关闭学生的语音输入？']) {
    assert.equal(capabilityRequest(text), text)
  }
})
test('questions stay in ordinary conversation even with a pending preview', () => {
  for (const text of ['权限变更有什么用', '如何关闭学生语音输入', '学生能使用语音吗？', '系统核心业务流程是什么', '现在有哪些权限']) {
    assert.equal(capabilityRequest(text, '关闭学生的语音输入'), null)
  }
})
test('clarifications retain the proposed target; unrelated chat does not', () => {
  assert.match(capabilityRequest('账号是 teacher001', '关闭张老师的自动提醒权限'), /关闭张老师.*\n.*teacher001/)
  assert.equal(capabilityRequest('账号是 teacher001'), null)
  assert.equal(capabilityRequest('介绍一下岗位发布流程', '关闭学生的语音输入'), null)
})
