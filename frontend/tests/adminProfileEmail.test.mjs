import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'

const { descriptor } = parse(readFileSync(new URL('../src/views/admin/ProfileCenter.vue', import.meta.url), 'utf8'))
const ast = ts.createSourceFile('ProfileCenter.ts', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const declarations = ast.statements.filter(ts.isVariableStatement).flatMap(statement => [...statement.declarationList.declarations])
const load = (name, scope) => vm.runInNewContext(ts.transpileModule(`(${declarations.find(item => item.name.getText(ast) === name).initializer.getText(ast)})`, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, scope)
const ref = value => ({ value })
const noop = () => {}

function fixture() {
  const writes = []
  const scope = {
    authStore: { user: { id: 7 } }, localStorage: { getItem: () => null },
    loading: ref(false), userInfo: {}, editingUser: { id: 7, username: 'admin', phone: '', email: '  admin@example.com  ' },
    editDialogVisible: ref(true), profileSaving: ref(false), profileFormRef: ref({ validate: async () => true }),
    nextTick: async callback => callback?.(),
    logger: { error: noop }, ElMessage: { error: noop, success: noop, warning: noop },
    formContext: { beginFormOperation: () => ({ complete: noop }) },
    AdminUserService: {
      getCurrentProfile: async () => ({ code: 200, data: { id: 7, username: 'admin', name: '管理员', phone: '', email: 'saved@example.com' } }),
      updateCurrentProfile: async payload => { writes.push(payload); return { code: 200 } }
    }
  }
  scope.loadUserInfo = load('loadUserInfo', scope)
  return { scope, writes, run: name => load(name, scope)() }
}

test('个人中心读取及编辑回填邮箱，关闭编辑清空草稿', async () => {
  const { scope, run } = fixture()
  await run('loadUserInfo')
  assert.equal(scope.userInfo.email, 'saved@example.com')
  await run('showEditProfile')
  assert.equal(scope.editingUser.email, 'saved@example.com')
  await run('handleEditDialogClose')
  assert.equal(scope.editingUser.email, '')
})

test('保存邮箱先校验并去除空格，只提交当前个人资料字段', async () => {
  const { scope, writes, run } = fixture()
  await run('saveProfile')
  assert.equal(writes.length, 1)
  assert.deepEqual(Object.keys(writes[0]).sort(), ['email', 'phone', 'username'])
  assert.equal(writes[0].email, 'admin@example.com')
  assert.equal(scope.editDialogVisible.value, false)
  assert.equal(scope.userInfo.email, 'saved@example.com')
  assert.equal(scope.profileSaving.value, false)
})

test('允许清空邮箱，校验失败或读取失败时不保存、不打开错误草稿', async () => {
  const empty = fixture(); empty.scope.editingUser.email = ''
  await empty.run('saveProfile')
  assert.equal(empty.writes[0].email, '')
  const invalid = fixture(); invalid.scope.profileFormRef.value.validate = async () => { throw new Error('invalid') }
  await invalid.run('saveProfile')
  assert.equal(invalid.writes.length, 0)
  assert.equal(invalid.scope.editDialogVisible.value, true)
  const failedRead = fixture(); failedRead.scope.editDialogVisible.value = false
  failedRead.scope.AdminUserService.getCurrentProfile = async () => ({ code: 400 })
  await failedRead.run('showEditProfile')
  assert.equal(failedRead.scope.editDialogVisible.value, false)
})

test('服务端保存失败时保留邮箱输入，释放保存状态', async () => {
  const { scope, run } = fixture()
  scope.AdminUserService.updateCurrentProfile = async () => ({ code: 400, message: '失败' })
  await run('saveProfile')
  assert.equal(scope.editingUser.email, 'admin@example.com')
  assert.equal(scope.editDialogVisible.value, true)
  assert.equal(scope.profileSaving.value, false)
})
