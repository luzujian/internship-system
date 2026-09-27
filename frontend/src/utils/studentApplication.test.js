import test from 'node:test'
import assert from 'node:assert/strict'
import {
  latestStudentApplication,
  studentApplicationDialogMode,
  studentApplicationStatusText
} from './studentApplication.js'

test('latest student application is selected by recency and id for the requested type', () => {
  const latest = latestStudentApplication([
    { id: 1, applicationType: 'selfPractice', applyTime: '2026-09-25 10:00:00', status: 'rejected' },
    { id: 3, applicationType: 'selfPractice', applyTime: '2026-09-26 10:00:00', status: 'withdrawn' },
    { id: 2, applicationType: 'selfPractice', applyTime: '2026-09-26 10:00:00', status: 'pending' },
    { id: 9, applicationType: 'delay', applyTime: '2026-09-27 10:00:00', status: 'pending' }
  ], 'selfPractice')
  assert.equal(latest.id, 3)
  assert.equal(latest.status, 'withdrawn')
})

test('pending and approved student dialogs show results instead of a blank form', () => {
  assert.equal(studentApplicationDialogMode('pending'), 'result')
  assert.equal(studentApplicationDialogMode('approved'), 'result')
})

test('rejected and withdrawn student dialogs show the resubmission form', () => {
  assert.equal(studentApplicationDialogMode('rejected'), 'form')
  assert.equal(studentApplicationDialogMode('withdrawn'), 'form')
  assert.equal(studentApplicationStatusText.rejected, '教师已驳回')
  assert.equal(studentApplicationStatusText.withdrawn, '你已撤回')
})
