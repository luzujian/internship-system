import test from 'node:test'
import assert from 'node:assert/strict'

import {
  JOB_POSTING_CONFIDENCE_THRESHOLD,
  decideJobPostingAutoFill
} from './jobPostingAutoFill.js'

test('missing or low confidence never auto-fills', () => {
  assert.equal(decideJobPostingAutoFill({ current: '', next: 'Java实习生' }), 'low-confidence')
  assert.equal(decideJobPostingAutoFill({ current: '', next: 'Java实习生', confidence: 0.74 }), 'low-confidence')
})

test('a confident value fills only an empty field', () => {
  assert.equal(JOB_POSTING_CONFIDENCE_THRESHOLD, 0.75)
  assert.equal(decideJobPostingAutoFill({ current: '', next: 'Java实习生', confidence: 0.75 }), 'fill')
  assert.equal(decideJobPostingAutoFill({ current: '前端实习生', next: 'Java实习生', confidence: 0.99 }), 'conflict')
  assert.equal(decideJobPostingAutoFill({ current: 'Java实习生', next: 'Java实习生', confidence: 0.99 }), 'same')
})

test('only an unchanged previous AI value can be replaced on re-parse', () => {
  assert.equal(decideJobPostingAutoFill({
    current: 'Java实习生',
    next: '后端实习生',
    confidence: 0.95,
    replaceable: true
  }), 'fill')
  assert.equal(decideJobPostingAutoFill({ current: 0, next: 1, confidence: 0.95 }), 'conflict')
})
