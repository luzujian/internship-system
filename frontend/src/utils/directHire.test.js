import test from 'node:test'
import assert from 'node:assert/strict'

import {
  getApplicationStatusText,
  getInterviewStatusText,
  getProgressStatus,
  getProgressRoute
} from './directHire.js'

test('direct hire uses explicit student-facing labels', () => {
  assert.equal(getApplicationStatusText('interview_passed', true), '直接录取（免面试）')
  assert.equal(getInterviewStatusText('interview_passed', true), '直接录取（免面试）')
  assert.deepEqual(getProgressStatus('success', 'direct_hire'), {
    text: '已录取',
    class: 'success'
  })
  assert.equal(getProgressRoute('direct_hire'), '/student/internship-confirmation-form')
})

test('normal interview pass keeps its existing meaning', () => {
  assert.equal(getApplicationStatusText('interview_passed', false), '已通过')
  assert.equal(getInterviewStatusText('interview_passed', false), '面试通过')
  assert.deepEqual(getProgressStatus('success', 'interview'), {
    text: '已通过',
    class: 'success'
  })
  assert.equal(getProgressRoute('interview'), '/student/interviews')
})
