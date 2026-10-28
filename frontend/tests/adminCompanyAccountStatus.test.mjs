import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'
import { ref } from 'vue'

const { descriptor } = parse(readFileSync(new URL('../src/views/admin/CompanyManagement.vue', import.meta.url), 'utf8'))
const ast = ts.createSourceFile('CompanyManagement.ts', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const declarations = ast.statements.filter(ts.isVariableStatement).flatMap(statement => [...statement.declarationList.declarations])
function load(name, scope = {}) {
  const declaration = declarations.find(item => item.name.getText(ast) === name)
  assert.ok(declaration, `Missing production handler: ${name}`)
  return vm.runInNewContext(ts.transpileModule(`(${declaration.initializer.getText(ast)})`, {
    compilerOptions: { target: ts.ScriptTarget.ES2022 }
  }).outputText, scope)
}
const formatAccountStatus = load('formatAccountStatus')
const getAccountStatusTagType = load('getAccountStatusTagType')
const formatAuditStatus = load('formatAuditStatus')
const noop = () => {}

test('禁用后重新查询企业列表，账号显示已禁用，审核通过状态保留', async () => {
  for (const initialStatus of [1, '1']) {
    const company = { id: 7, companyName: '测试企业', status: initialStatus, auditStatus: 1 }
    const writes = []
    const errors = []
    const scope = {
      searchForm: ref({ companyName: '', status: null, companyTag: [] }),
      tableData: ref([company]), loading: ref(false),
      pagination: ref({ currentPage: 1, pageSize: 10, total: 1 }),
      logger: { log: noop, error: noop },
      ElMessageBox: { confirm: async () => {} },
      ElMessage: { success: noop, error: message => errors.push(message) },
      companyService: {
        updateCompanyStatus: async (id, status) => {
          writes.push({ id, status })
          company.status = status
          return { code: 200 }
        },
        getAdminCompanies: async () => ({ code: 200, data: { rows: [{ ...company }], total: 1 } })
      }
    }
    scope.queryPage = load('queryPage', scope)
    const toggle = load('handleToggleStatus', scope)

    await toggle(company.id, company.status)
    await new Promise(resolve => setImmediate(resolve))
    const disabled = scope.tableData.value[0]
    assert.deepEqual(writes, [{ id: 7, status: 0 }])
    assert.equal(formatAccountStatus(disabled.status), '已禁用')
    assert.equal(getAccountStatusTagType(disabled.status), 'danger')
    assert.equal(disabled.auditStatus, 1)
    assert.equal(formatAuditStatus(disabled.auditStatus), '审核通过')

    await toggle(company.id, String(disabled.status))
    await new Promise(resolve => setImmediate(resolve))
    assert.deepEqual(writes, [{ id: 7, status: 0 }, { id: 7, status: 1 }])
    assert.equal(formatAccountStatus(scope.tableData.value[0].status), '正常')
    assert.equal(scope.tableData.value[0].auditStatus, 1)
    assert.deepEqual(errors, [])
  }
})

test('账号状态兼容数字和字符串，待审核仅由审核状态决定', () => {
  for (const status of [0, '0', 2, '2']) {
    assert.equal(formatAccountStatus(status), '已禁用')
    assert.equal(getAccountStatusTagType(status), 'danger')
  }
  for (const auditStatus of [0, '0']) assert.equal(formatAuditStatus(auditStatus), '待审核')
  for (const auditStatus of [1, '1']) assert.equal(formatAuditStatus(auditStatus), '审核通过')
  for (const auditStatus of [2, '2']) assert.equal(formatAuditStatus(auditStatus), '审核拒绝')
  for (const status of [null, undefined, '', 99]) assert.equal(formatAccountStatus(status), '未知状态')
})

test('取消禁用时不发送状态更新，也不刷新列表', async () => {
  const toggle = load('handleToggleStatus', {
    ElMessageBox: { confirm: async () => { throw 'cancel' } },
    companyService: { updateCompanyStatus: () => assert.fail('取消后不能更新账号') },
    queryPage: () => assert.fail('取消后不能刷新列表'),
    logger: { error: () => assert.fail('取消操作不应作为错误') }
  })
  await toggle(7, 1)
})
