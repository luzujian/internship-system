import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse } from '@vue/compiler-sfc'

test('admin AI test messages use Vue text interpolation for model output', () => {
  const source = readFileSync(new URL('../src/views/admin/AITest.vue', import.meta.url), 'utf8')
  const { descriptor } = parse(source)
  const template = descriptor.template?.content ?? ''
  const messages = [...template.matchAll(/<div class="message-text"([^>]*)>([\s\S]*?)<\/div>/g)]

  assert.equal(messages.length, 2)
  assert.ok(messages.every(([, attributes]) => !/v-html|v-text/.test(attributes)))
  assert.match(messages[0][2], /\{\{\s*message\.content\s*\}\}/)
  assert.match(messages[1][2], /\{\{\s*streamingMessage\s*\}\}/)
  assert.match(source, /\.message-text\s*\{[^}]*white-space:\s*pre-wrap/s)
})
