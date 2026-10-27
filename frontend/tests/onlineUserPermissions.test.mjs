import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { ref, computed } from 'vue'

const read = path => readFileSync(new URL('../src/' + path, import.meta.url), 'utf8')
const compile = source => ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
function load(source, modules = {}) {
  const module = { exports: {} }
  vm.runInNewContext(compile(source), { module, exports: module.exports, require: name => {
    if (!(name in modules)) throw new Error('Unexpected module: ' + name)
    return modules[name]
  } })
  return module.exports
}
const permissions = load(read('utils/adminMenuPermissions.ts'))
const routes = load(read('router/adminRouter.ts'), {
  '@/utils/adminMenuPermissions': permissions,
  '@/agent/pageForms': { agentPage: () => ({}) }
}).default
const logMeta = routes.find(route => route.path === 'logs').meta

test('log-center direct access accepts any existing log viewing permission and denies unrelated permissions', () => {
  for (const allowed of [['log:login:view'], ['log:security:view'], ['log:view'], ['statistics:view'], []]) {
    const destinations = []
    const auth = { isAuthenticated: true, role: 'ROLE_ADMIN', hasPermission: code => allowed.includes(code) }
    const guard = load(read('router/guards/permissionGuard.ts'), {
      '@/store/auth': { useAuthStore: () => auth },
      '@/store/auth/config': { ROLE_TO_DASHBOARD_MAP: { ROLE_ADMIN: '/admin/dashboard' } },
      '@/utils/logger': { __esModule: true, default: { log() {}, warn() {} } }
    }).permissionGuard
    guard({ path: '/admin/logs', meta: { requiresAuth: true, ...logMeta } }, { path: '/admin/dashboard' }, path => destinations.push(path))
    assert.equal(destinations[0], allowed.some(code => code.startsWith('log:')) ? undefined : '/admin/dashboard')
  }
})

test('the online tab requires administrator identity and login-log viewing permission', () => {
  const source = read('views/admin/LogManagement.vue').match(/<script[^>]*>([\s\S]*?)<\/script>/)[1]
  const ast = ts.createSourceFile('log.ts', source, ts.ScriptTarget.Latest, true)
  const declarations = ast.statements.filter(ts.isVariableStatement).flatMap(statement => [...statement.declarationList.declarations])
  const initializer = name => declarations.find(node => node.name.getText(ast) === name).initializer.getText(ast)
  for (const [role, codes, expected] of [
    ['ROLE_ADMIN', ['log:login:view'], true], ['ROLE_ADMIN', ['log:view'], false],
    ['ROLE_TEACHER_COLLEGE', ['log:login:view'], false], ['ROLE_ADMIN', [], false]
  ]) {
    const tabs = vm.runInNewContext(compile(`const tabPermissionMap=${initializer('tabPermissionMap')}; ${initializer('visibleTabs')}`),
      { computed, authStore: { role }, hasPermission: code => codes.includes(code) })
    assert.equal(tabs.value.some(tab => tab.name === 'online-users'), expected)
  }
})
