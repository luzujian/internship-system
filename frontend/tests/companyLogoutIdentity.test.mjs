import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'

for (const page of ['CompanyInfo', 'ApplicationView', 'InternshipConfirm', 'RecruitmentManagement']) {
  test(`${page} resolves the signed-in company and stops during logout or a role switch`, () => {
    const { descriptor } = parse(readFileSync(new URL(`../src/views/company/${page}.vue`, import.meta.url), 'utf8'))
    const ast = ts.createSourceFile('company.ts', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true)
    const declaration = ast.statements.flatMap(node => ts.isVariableStatement(node) ? [...node.declarationList.declarations] : []).find(node => node.name.getText(ast) === 'companyId')
    const invoke = ts.transpileModule(`(${declaration.initializer.getText(ast)})`, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
    const authStore = { token: '', role: null, user: null }, errors = [], reads = []
    const getter = vm.runInNewContext(invoke, { authStore, computed: fn => fn, ElMessage: { error: text => errors.push(text) }, console: { log() {} },
      localStorage: { getItem: key => { reads.push(key); return '999' } } })
    assert.equal(getter(), null); assert.deepEqual(errors, []); assert.deepEqual(reads, [])
    authStore.token = 'student'; authStore.role = 'ROLE_STUDENT'; authStore.user = { id: 2 }
    assert.equal(getter(), null); assert.deepEqual(errors, []); assert.deepEqual(reads, [])
    // Login, session restoration and role switching store the role on authStore.
    authStore.token = 'company'; authStore.role = 'ROLE_COMPANY'; authStore.user = { id: '33', username: 'company' }
    assert.equal(getter(), 33)
    authStore.token = ''; assert.equal(getter(), null); assert.deepEqual(errors, [])
    authStore.token = 'student'; authStore.role = 'ROLE_STUDENT'; authStore.user = { id: 2, role: 'ROLE_COMPANY' }
    assert.equal(getter(), null); assert.deepEqual(errors, []); assert.deepEqual(reads, [])
  })
}
