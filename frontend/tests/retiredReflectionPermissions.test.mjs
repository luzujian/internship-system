import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'

const source = readFileSync(new URL('../src/views/admin/PermissionManagement.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const ast = ts.createSourceFile('PermissionManagement.vue', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const declaration = ast.statements.filter(ts.isVariableStatement)
  .flatMap(statement => [...statement.declarationList.declarations])
  .find(node => node.name.getText(ast) === 'savePermissions')
assert.ok(declaration)

for (const enabled of [false, true]) {
  test(`permission save ${enabled ? 'permits explicit restoration edits' : 'preserves hidden reflection grants'}`, async () => {
    let payload
    const noop = () => {}
    const code = ts.transpileModule(`(${declaration.initializer.getText(ast)})`, {
      compilerOptions: { target: ts.ScriptTarget.ES2022 }
    }).outputText
    const save = vm.runInNewContext(code, {
      permissionTreeRef: { value: { getCheckedKeys: () => [102], getHalfCheckedKeys: () => [] } },
      currentRole: { value: { id: 2, roleCode: 'ROLE_ADMIN' } },
      retainedAdminPermissionIds: { value: [100, 101] },
      reflectionEnabled: { value: enabled },
      saving: { value: false }, permissionDialogVisible: { value: true },
      PERMISSION_MANAGE_LOCKED_IDS: [114],
      logger: { log: noop, error: noop },
      ElMessage: { warning: noop, success: noop, error: noop, info: noop },
      isCurrentUserRole: () => false,
      request: { post: async (_url, body) => { payload = body; return { code: 200 } } }
    })
    await save()
    assert.ok(payload)
    assert.ok(payload.permissionIds.includes(102))
    assert.ok(payload.permissionIds.includes(114))
    assert.equal(payload.permissionIds.includes(100), !enabled)
    assert.equal(payload.permissionIds.includes(101), !enabled)
  })
}
