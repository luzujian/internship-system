import assert from 'node:assert/strict'
import test from 'node:test'
import { highlightPlainText } from '../src/utils/highlightPlainText.ts'

test('resource titles and descriptions remain text without a search term', () => {
  assert.equal(highlightPlainText('<img src=x onerror="alert(1)">&', ''), '&lt;img src=x onerror=&quot;alert(1)&quot;&gt;&amp;')
  assert.equal(highlightPlainText(null, ''), '')
})

test('search highlights literal text without interpreting HTML or regular expressions', () => {
  assert.equal(
    highlightPlainText('<script>alert(1)</script>', '<script>'),
    '<span style="background-color: #ffeb3b;">&lt;script&gt;</span>alert(1)&lt;/script&gt;'
  )
  assert.equal(
    highlightPlainText('Case (A) and (B)', '(A)'),
    'Case <span style="background-color: #ffeb3b;">(A)</span> and (B)'
  )
  assert.equal(highlightPlainText('plain text', '['), 'plain text')
})
