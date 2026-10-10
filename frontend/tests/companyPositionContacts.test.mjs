import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

const source = readFileSync(new URL('../src/store/position.ts', import.meta.url), 'utf8')
const ast = ts.createSourceFile('position.ts', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const declaration = ast.statements.find(s => ts.isVariableStatement(s) && s.declarationList.declarations.some(d => d.name.getText(ast) === 'usePositionStore'))
const statements = declaration.declarationList.declarations[0].initializer.arguments[1].body.statements
function store(rows = [{ id: 1, positionName: '岗位甲', contactId: 10, contactPerson: '甲', contactPhone: '13800000001', contactEmail: 'one@example.test' }]) {
  const names = ['positions', 'fetchPositions', 'addPosition', 'updatePosition']
  const code = statements.filter(ts.isVariableStatement).filter(s => s.declarationList.declarations.some(d => names.includes(d.name.getText(ast)))).map(s => s.getText(ast)).join('\n')
  const requests = []
  const page = vm.runInNewContext(ts.transpileModule(code + `\n({ ${names.join(', ')} })`, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, {
    ref: value => ({ value }), console: { log: () => {}, error: console.error },
    normalizeSalaryUnit: value => value || 'K/月', formatDate: value => value, formatDateToISO: value => value.toISOString(), formatWorkLocation: () => '', syncRecruitedCountFromInternshipStatuses: () => {},
    positionApi: {
      getPositionsByCompanyId: async () => ({ code: 200, data: rows }),
      createPosition: async data => { requests.push(data); return { code: 200 } },
      updatePosition: async (id, data) => { requests.push(data); return { code: 200 } }
    }
  })
  return { ...page, requests }
}
test('position store retains contact selection when loading rows for editing', async () => {
  const page = store(); await page.fetchPositions(100)
  assert.equal(page.positions.value[0].contactId, 10)
  assert.equal(page.positions.value[0].contactPhone, '13800000001')
})
test('create and update send the selected contact; explicit fallback and omitted contact remain distinct', async () => {
  const page = store()
  assert.equal(await page.addPosition(100, { positionName: '岗位甲', contactId: 10 }), true)
  assert.equal(page.requests.at(-1).contactId, 10)
  assert.equal(await page.updatePosition(100, 1, { positionName: '岗位甲', contactId: 20 }), true)
  assert.equal(page.requests.at(-1).contactId, 20)
  await page.updatePosition(100, 1, { positionName: '岗位甲', contactId: null })
  assert.equal(page.requests.at(-1).contactId, null)
  await page.updatePosition(100, 1, { positionName: '岗位甲' })
  assert.equal('contactId' in page.requests.at(-1), false)
})

test('position store preserves combined, cleared and omitted interview methods through API requests', async () => {
  const page = store([
    { id: 1, interviewMethod: '线下、线上视频' },
    { id: 2, interviewMethod: '' },
    { id: 3, interviewMethod: null }
  ])
  await page.fetchPositions(100)
  assert.deepEqual(Array.from(page.positions.value, row => row.interviewMethod), ['线下、线上视频', '', ''])
  for (const method of ['线下、线上视频', '']) {
    await page.addPosition(100, { positionName: '岗位甲', interviewMethod: method })
    assert.equal(page.requests.at(-1).interviewMethod, method)
    await page.updatePosition(100, 1, { positionName: '岗位甲', interviewMethod: method })
    assert.equal(page.requests.at(-1).interviewMethod, method)
  }
  await page.updatePosition(100, 1, { positionName: '岗位甲' })
  assert.equal(page.requests.at(-1).interviewMethod, null)
})
