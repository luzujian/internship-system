import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'

const ref = value => ({ value })
const source = file => parse(readFileSync(new URL(`../src/views/company/${file}.vue`, import.meta.url), 'utf8')).descriptor.scriptSetup.content
function declarations(file, names) {
  const ast = ts.createSourceFile(file, source(file), ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  return ast.statements.filter(ts.isVariableStatement)
    .filter(statement => statement.declarationList.declarations.some(declaration => names.includes(declaration.name.getText(ast))))
    .map(statement => statement.getText(ast)).join('\n')
}
const names = ['students', 'total', 'loading', 'loadError', 'keyword', 'searchKeyword', 'page', 'pageSize', 'revision', 'requestVersion', 'loadStudents', 'searchStudents', 'resetSearch', 'changePageSize']
function roster(reply) {
  const code = ts.transpileModule(declarations('InternshipStudents', names) + `\n({ ${names.join(', ')} })`, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
  return vm.runInNewContext(code, { ref, getCompanyInternshipStudents: reply, Error })
}
const success = (total = 2, rows = [{ studentId: 1 }, { studentId: 2, sourceType: 'SELF_PRACTICE' }]) => ({ code: 200, data: { total, rows } })

test('roster searches from page one, keeps self-practice students and sends no company id', async () => {
  const calls = []
  const page = roster(async params => { calls.push(params); return success() })
  page.page.value = 3
  page.keyword.value = ' TEST0002 '
  await page.searchStudents()
  assert.equal(calls[0].page, 1)
  assert.equal(calls[0].keyword, 'TEST0002')
  assert.deepEqual(Object.keys(calls[0]).sort(), ['keyword', 'page', 'pageSize'])
  assert.equal(page.students.value[1].sourceType, 'SELF_PRACTICE')
  await page.resetSearch()
  assert.equal(calls.at(-1).keyword, '')
  assert.equal(page.total.value, 2)
})

test('expired pages are corrected when current students leave, failures clear stale rows', async () => {
  let fail = false
  const calls = []
  const page = roster(async params => {
    calls.push(params.page)
    if (fail) throw new Error('查询失败')
    return success(2, params.page === 1 ? [{ studentId: 1 }, { studentId: 2 }] : [])
  })
  page.page.value = 5
  await page.loadStudents()
  assert.deepEqual(calls, [5, 1])
  assert.equal(page.students.value.length, 2)
  fail = true
  await page.loadStudents()
  assert.equal(page.students.value.length, 0)
  assert.equal(page.total.value, 0)
  assert.equal(page.loadError.value, '查询失败')
  assert.equal(page.loading.value, false)
})

test('late responses do not overwrite a newer roster search', async () => {
  let resolveFirst
  let requests = 0
  const page = roster(() => ++requests === 1 ? new Promise(resolve => { resolveFirst = resolve }) : Promise.resolve(success(1, [{ studentId: 2 }])))
  const first = page.loadStudents()
  await page.searchStudents()
  resolveFirst(success(1, [{ studentId: 1 }]))
  await first
  assert.equal(page.students.value[0].studentId, 2)
  assert.equal(page.loading.value, false)
})

test('company dashboard opens the current roster and uses its count rather than historical confirmations', async () => {
  const code = ts.transpileModule(declarations('CompanyHome', ['statsData', 'statsRequest', 'fetchStats']) + '\n({ statsData, fetchStats })', { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
  const home = vm.runInNewContext(code, {
    ref, markRaw: value => value, User: {}, DocumentChecked: {}, Bell: {},
    console: { log() {}, error() {} },
    request: async () => ({ code: 200, data: { currentInternshipStudents: 3, confirmedConfirmations: 99 } })
  })
  await home.fetchStats()
  assert.equal(home.statsData.value[2].route, '/company/internship-confirm')
  assert.equal(home.statsData.value[3].title, '当前实习学生')
  assert.equal(home.statsData.value[3].value, 3)
  assert.equal(home.statsData.value[3].route, '/company/internship-students')
})
