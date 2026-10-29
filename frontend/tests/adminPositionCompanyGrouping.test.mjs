import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { computed, ref } from 'vue'
import { parse } from '@vue/compiler-sfc'

const { descriptor } = parse(readFileSync(new URL('../src/views/admin/PositionCategoryManagement.vue', import.meta.url), 'utf8'))
const ast = ts.createSourceFile('PositionCategoryManagement.vue', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
function value(name, scope) {
  const declaration = ast.statements.filter(ts.isVariableStatement)
    .flatMap(statement => [...statement.declarationList.declarations]).find(item => item.name.getText(ast) === name)
  assert.ok(declaration, `${name} exists`)
  return vm.runInNewContext(ts.transpileModule(`(${declaration.initializer.getText(ast)})`, {
    compilerOptions: { target: ts.ScriptTarget.ES2022 }
  }).outputText, scope)
}
function fixture(rows) {
  const scope = { computed, activeCategoryId: ref(1), getCategoryPositions: id => id === 1 ? rows : [] }
  for (const name of ['positionCompanyKey', 'displayedPositions', 'positionCompanyRowSpans', 'mergePositionCompanyCells']) scope[name] = value(name, scope)
  return scope
}

test('posts from different dates are grouped by company and keep their order within the group', () => {
  const rows = [
    { id: 1, companyId: 8, createTime: '2026-10-29' },
    { id: 2, companyId: 9, createTime: '2026-10-28' },
    { id: 3, companyId: 8, createTime: '2026-10-27' },
    { id: 4, companyId: 9, createTime: '2026-10-26' }
  ]
  const scope = fixture(rows)
  assert.deepEqual(Array.from(scope.displayedPositions.value, row => row.id), [1, 3, 2, 4])
  assert.deepEqual(Array.from(scope.positionCompanyRowSpans.value), [2, 0, 2, 0])
  assert.deepEqual(rows.map(row => row.id), [1, 2, 3, 4], 'source data must remain unchanged')
})

test('matching company names with different IDs stay separate, and string IDs match numeric IDs', () => {
  const scope = fixture([
    { id: 1, companyId: 8, companyName: '同名企业' },
    { id: 2, companyId: 9, companyName: '同名企业' },
    { id: 3, companyId: '8', companyName: '同名企业' }
  ])
  assert.deepEqual(Array.from(scope.displayedPositions.value, row => row.id), [1, 3, 2])
  assert.deepEqual(Array.from(scope.positionCompanyRowSpans.value), [2, 0, 1])
})

test('unassigned positions stay separate instead of looking like one publishing company', () => {
  const scope = fixture([{ id: 1 }, { id: 2, companyId: null }, { id: 3, companyId: 0 }])
  assert.deepEqual(Array.from(scope.positionCompanyRowSpans.value), [1, 1, 1])
})

test('only company cells merge and changing category clears the previous spans', () => {
  const scope = fixture([{ id: 1, companyId: 8 }, { id: 2, companyId: 8 }])
  assert.equal(scope.mergePositionCompanyCells({ column: { property: 'companyName' }, rowIndex: 0 }).rowspan, 2)
  assert.equal(scope.mergePositionCompanyCells({ column: { property: 'companyName' }, rowIndex: 1 }).colspan, 0)
  assert.equal(scope.mergePositionCompanyCells({ column: { property: 'positionName' }, rowIndex: 1 }).rowspan, 1)
  scope.activeCategoryId.value = 2
  assert.equal(scope.displayedPositions.value.length, 0)
  assert.equal(scope.positionCompanyRowSpans.value.length, 0)
})

test('loading position data preserves publisher IDs and names for grouping', async () => {
  const scope = {
    positionList: ref([]), logger: { log() {}, error() {} }, ElMessage: { error: message => { throw new Error(message) } },
    buildCategoryPositionsMap() {}, PositionService: { getAllPositions: async () => ({ code: 200, data: [
      { id: 1, companyId: 8, companyName: '企业甲', positionName: '开发实习生', categoryId: 1 },
      { id: 2, company_id: 9, company_name: '企业乙', position_name: '测试实习生', category_id: 1 }
    ] }) }
  }
  await value('getPositionList', scope)()
  assert.deepEqual(Array.from(scope.positionList.value, row => [row.companyId, row.companyName]), [[8, '企业甲'], [9, '企业乙']])
})
