import test from 'node:test'
import assert from 'node:assert/strict'
import { commitAllowCreateText, withModelOption, readSelectInputText } from '../src/utils/allowCreate.ts'

test('P2 只打字不选创建项：显示文字落入空 model', () => {
  assert.equal(commitAllowCreateText('P2定制岗', ''), 'P2定制岗')
  assert.equal(commitAllowCreateText('  P2定制岗  ', ''), 'P2定制岗')
})

test('P2 model 已有值时保持（回车选中的既有选项优先）', () => {
  assert.equal(commitAllowCreateText('销售岗x', '销售岗'), '销售岗')
  assert.equal(commitAllowCreateText('', '销售岗'), '销售岗')
})

test('P2 双空时保持空，提交前走明确校验', () => {
  assert.equal(commitAllowCreateText('', ''), '')
  assert.equal(commitAllowCreateText('   ', ''), '')
  assert.equal(commitAllowCreateText(null, ''), '')
})

test('P2 选项兜底：model 值不在选项中时追加，保证回显', () => {
  const base = [{ label: '销售岗', value: '销售岗' }]
  const out = withModelOption(base, 'AI定制岗')
  assert.equal(out.length, 2)
  assert.deepEqual(out[1], { label: 'AI定制岗', value: 'AI定制岗' })
  assert.equal(base.length, 1)
})

test('P2 选项兜底：已存在与空值时不改动', () => {
  const base = [{ label: '销售岗', value: '销售岗' }]
  assert.equal(withModelOption(base, '销售岗'), base)
  assert.equal(withModelOption(base, ''), base)
  assert.equal(withModelOption(base, '   '), base)
})

test('P2 非法 ref 输入读出空串，不抛异常', () => {
  assert.equal(readSelectInputText(null), '')
  assert.equal(readSelectInputText({}), '')
  assert.equal(readSelectInputText({ $el: null }), '')
})
