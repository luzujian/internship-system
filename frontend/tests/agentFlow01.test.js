import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileTemplate, compileScript } from '@vue/compiler-sfc'

const store = readFileSync(new URL('../src/agent/store.ts', import.meta.url), 'utf8')
const workspace = readFileSync(new URL('../src/components/agent/AgentWorkspace.vue', import.meta.url), 'utf8')
const { descriptor } = parse(workspace, { filename: 'AgentWorkspace.vue' })

test('教师描述交给统一 Agent，确认使用统一事务接口', () => {
  const previewFn = store.slice(store.indexOf('async function previewAutoFlow'), store.indexOf('async function confirmCreateAutoFlow'))
  assert.match(previewFn, /await ask\(businessMessageContext\(\), message\)/)
  assert.doesNotMatch(previewFn, /\/confirm/)
  assert.match(store, /auto-tasks\/v2\/confirm/)
  assert.doesNotMatch(previewFn, /request\.post/)
})

test('管理员描述交给统一 Agent，不保存；保存仅在确认函数内且复用 config 接口', () => {
  const previewFn = store.slice(store.indexOf('async function previewCapsFlow'))
  const previewBody = previewFn.slice(0, previewFn.indexOf('\n  async function confirmCapsFlow'))
  assert.match(previewBody, /await ask\(businessMessageContext\(\), message\)/)
  assert.doesNotMatch(previewBody, /request\.post/)
  assert.doesNotMatch(previewBody, /\/admin\/agent-capabilities\/config/)
  assert.match(store, /async function confirmCapsFlow[\s\S]*request\.put\('\/admin\/agent-capabilities\/config'/)
})

test('预览统一组件保持问答与自动化同一对话流', () => {
  assert.match(workspace, /<AutomationPreview/)
  assert.doesNotMatch(workspace, /确认创建（默认暂停）/)
  assert.match(workspace, /agent\.capsFlow\.questions\.length > 0/)
  assert.match(workspace, /确认并保存/)
})

test('AgentWorkspace 模板编译通过且 flow 按钮绑定 previewing/working 禁用', () => {
  const script = compileScript(descriptor, { id: 'flow01-test' })
  const result = compileTemplate({
    source: descriptor.template.content, filename: 'AgentWorkspace.vue', id: 'flow01-test',
    compilerOptions: { bindingMetadata: script.bindings }
  })
  assert.deepEqual(result.errors, [])
  assert.match(result.code, /data-flow-phase/)
})
