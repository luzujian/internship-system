import assert from 'node:assert/strict'
import test from 'node:test'
import { escapePdfText, safePdfImageSrc } from '../src/utils/pdfExportSafety.ts'

test('company text cannot create markup in the PDF staging element', () => {
  assert.equal(escapePdfText('<img src=x onerror="alert(1)">&'), '&lt;img src=x onerror=&quot;alert(1)&quot;&gt;&amp;')
  assert.equal(escapePdfText(null), '')
})

test('PDF image source rejects script and active data URLs', () => {
  assert.equal(safePdfImageSrc('javascript:alert(1)'), null)
  assert.equal(safePdfImageSrc('data:image/svg+xml,<svg onload=alert(1)>'), null)
  assert.equal(safePdfImageSrc('https://files.example.edu/a.png" onerror="alert(1)'), null)
  assert.equal(safePdfImageSrc('/api/uploads/a.png'), '/api/uploads/a.png')
  assert.equal(safePdfImageSrc('data:image/png;base64,aGVsbG8='), 'data:image/png;base64,aGVsbG8=')
})
