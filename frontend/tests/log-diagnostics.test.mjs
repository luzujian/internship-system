import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

function load(context) {
  const source = fs.readFileSync(new URL('../src/views/admin/LogManagement.vue', import.meta.url), 'utf8').match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1]
  const ast = ts.createSourceFile('log.ts', source, ts.ScriptTarget.Latest, true)
  const names = ['failureDetailFields', 'detailSections']
  const declarations = []
  function visit(node) {
    if (ts.isVariableDeclaration(node) && names.includes(node.name.getText(ast))) declarations.push('let ' + node.getText(ast) + ';')
    ts.forEachChild(node, visit)
  }
  visit(ast)
  vm.runInNewContext(ts.transpileModule(declarations.join('\n') + '\n globalThis.handlers = { failureDetailFields, detailSections }', { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, context)
  return context.handlers
}
function fixture(category, detail) {
  const context = { detailCategory: { value: category }, detailData: { value: { detailJson: detail } },
    computed: callback => ({ get value() { return callback() } }), getRoleName: v => v, getActionName: v => v,
    getModuleName: v => v, getRiskName: v => v, getResultName: v => v, getEventTypeName: v => v }
  return load(context)
}
test('all three detail tabs show readable failure stage, code and reason', () => {
  for (const category of ['operations', 'login-events', 'security-events']) {
    const f = fixture(category, JSON.stringify({ failure: { reason: '输入邮箱与账号绑定邮箱不一致', reasonCode: 'EMAIL_MISMATCH', validationStage: 'RESET_SEND_CODE' } }))
    const fields = f.detailSections.value.flatMap(s => s.fields)
    assert.ok(fields.some(([name, value]) => name === '失败原因' && value === '输入邮箱与账号绑定邮箱不一致'))
    assert.ok(fields.some(([name, value]) => name === '诊断代码' && value === 'EMAIL_MISMATCH'))
    assert.ok(fields.some(([name, value]) => name === '失败阶段' && value === '找回密码：发送验证码'))
  }
})
test('old records and malformed diagnostic data remain readable', () => {
  const f = fixture('operations', null)
  for (const detail of [null, '', '{broken', '{}', '{"failure":null}']) assert.equal(f.failureDetailFields(detail).length, 0)
  assert.ok(f.detailSections.value.length > 0)
})
test('upload rejection metadata is readable without raw file content', () => {
  const f = fixture('security-events', { failure: { reason: '超过限制', validationStage: 'MULTIPART_PARSE', requestSize: 21000000,
    files: [{ size: 123, declaredMime: 'application/pdf', extension: 'pdf' }] } })
  const fields = f.detailSections.value.flatMap(s => s.fields)
  assert.ok(fields.some(([name, value]) => name === '请求大小' && value === '21000000 字节'))
  assert.ok(fields.some(([name, value]) => name === '文件 1 元信息' && value.includes('123 字节') && value.includes('application/pdf')))
})
