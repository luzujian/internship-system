import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeInterviewMethods, serializeInterviewMethods } from './interviewMethods.js'

test('historical single choices and saved multi choices reopen as arrays', () => {
  assert.deepEqual(normalizeInterviewMethods('线下'), ['线下'])
  assert.deepEqual(normalizeInterviewMethods('线下、线上视频'), ['线下', '线上视频'])
  assert.deepEqual(normalizeInterviewMethods(['电话面试', '线上视频']), ['电话面试', '线上视频'])
})

test('multiple choices round-trip through the existing text API without duplicates', () => {
  const selected = ['线下', '线上视频', '电话面试']
  assert.equal(serializeInterviewMethods(selected), '线下、线上视频、电话面试')
  assert.deepEqual(normalizeInterviewMethods(serializeInterviewMethods(selected)), selected)
  assert.deepEqual(normalizeInterviewMethods(' 线下，线上视频、线下 '), ['线下', '线上视频'])
})

test('cleared or missing choices stay empty instead of becoming an offline interview', () => {
  for (const value of [null, undefined, '', [], ' 、 ']) {
    assert.deepEqual(normalizeInterviewMethods(value), [])
    assert.equal(serializeInterviewMethods(value), '')
  }
})

test('normalization preserves historical labels and does not mutate saved template arrays', () => {
  const template = ['视频面试', '线下']
  const selections = normalizeInterviewMethods(template)
  assert.deepEqual(selections, template)
  selections.push('电话面试')
  assert.deepEqual(template, ['视频面试', '线下'])
})
