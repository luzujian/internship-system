import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { createRequire } from 'node:module'
import ts from 'typescript'

const require = createRequire(import.meta.url)
const { createPinia, setActivePinia } = require('pinia')
const source = readFileSync(new URL('../src/store/position.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText
function storeFor(position, statuses = []) {
  setActivePinia(createPinia())
  const exported = {}
  const services = {
    '@/api/PositionService': { getPositionsByCompanyId: async () => ({ code: 200, data: [position] }) },
    '@/api/InternshipApplicationService': { getCurrentCompanyApplications: async () => ({ code: 200, data: [] }) },
    '@/api/InternshipStatusService': { getInternshipStatusByCompanyId: async () => ({ code: 200, data: statuses }) },
    '@/api/MajorService': { getAllMajors: async () => ({ code: 200, data: [] }) }
  }
  vm.runInNewContext(compiled, { exports: exported, console: { log() {}, error() {} }, require: name => {
    if (name in services) return { default: services[name] }
    if (name.includes('salaryRange')) return { normalizeSalaryUnit: value => value || 'K/月' }
    return require(name)
  } })
  return exported.usePositionStore()
}

test('a full job stays full before the confirmation roster has loaded', async () => {
  const store = storeFor({ id: 12, positionName: '已满岗位', plannedRecruit: 3, recruitedCount: 3, remainingQuota: 0 })
  await store.fetchPositions(7)
  assert.equal(store.positions[0].remainingQuota, 0)
  assert.equal(store.positions[0].recruitedCount, 3)
  await store.fetchApplications(7)
  assert.equal(store.positions[0].remainingQuota, 0)
})

test('an incomplete or historical confirmation list cannot replace server job quotas', async () => {
  const store = storeFor({ id: 12, positionName: '岗位', plannedRecruit: 3, recruitedCount: 2, remainingQuota: 1 },
    [{ id: 20, positionId: 12, companyConfirmStatus: 1, status: 4 }])
  await store.fetchPositions(7)
  await store.fetchInternshipStatuses(7)
  assert.equal(store.positions[0].remainingQuota, 1)
  assert.equal(store.positions[0].recruitedCount, 2)
})
