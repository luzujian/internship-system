import test from 'node:test'
import assert from 'node:assert/strict'
import {
  canResubmitDelay,
  delayApplicationStatusText,
  delayDialogMode,
  delayFileListFromApplication,
  delayFormFromApplication,
  latestDelayApplication
} from './delayApplication.js'

test('latest delay application is selected by recency and id', () => {
  const latest = latestDelayApplication([
    { id: 1, applicationType: 'delay', applyTime: '2026-09-25 10:00:00', status: 'rejected' },
    { id: 3, applicationType: 'delay', applyTime: '2026-09-26 10:00:00', status: 'withdrawn' },
    { id: 2, applicationType: 'delay', applyTime: '2026-09-26 10:00:00', status: 'pending' },
    { id: 9, applicationType: 'selfPractice', applyTime: '2026-09-27 10:00:00', status: 'pending' }
  ])
  assert.equal(latest.id, 3)
  assert.equal(latest.status, 'withdrawn')
})

test('pending and approved delay dialogs show results instead of a blank form', () => {
  assert.equal(delayDialogMode('pending'), 'result')
  assert.equal(delayDialogMode('approved'), 'result')
})

test('rejected and withdrawn delay dialogs reopen with submitted content', () => {
  const application = {
    status: 'withdrawn',
    reason: '准备考研，申请延迟实习',
    materials: { delayPlan: 'uploaded.pdf' }
  }
  assert.equal(delayDialogMode(application.status), 'form')
  assert.equal(canResubmitDelay(application.status), true)
  assert.deepEqual(delayFormFromApplication(application), {
    reason: '准备考研，申请延迟实习',
    materialUrl: ''
  })
  assert.deepEqual(delayFileListFromApplication(application), [
    { name: '原考研计划', status: 'success' }
  ])
  assert.equal(delayApplicationStatusText.withdrawn, '你已撤回')
})

test('new delay form is blank and existing plan is not required to be uploaded again', () => {
  assert.deepEqual(delayFormFromApplication(null), { reason: '', materialUrl: '' })
  assert.deepEqual(delayFileListFromApplication({ materials: {} }), [])
  assert.equal(canResubmitDelay('pending'), false)
})
