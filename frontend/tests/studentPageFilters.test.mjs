import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'
import { getMonthlySalaryRangeK } from '../src/utils/salaryRange.js'

// Execute the real page functions so tests exercise the mapping and request handling used by the UI.
function pageFunctions(file, scope) {
  const { descriptor } = parse(readFileSync(new URL(`../src/views/student/${file}.vue`, import.meta.url), 'utf8'))
  const ast = ts.createSourceFile(`${file}.ts`, descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  const declarations = ast.statements.filter(ts.isVariableStatement).flatMap(s => [...s.declarationList.declarations])
  return name => {
    const declaration = declarations.find(d => d.name.getText(ast) === name)
    assert.ok(declaration, `${file}: ${name}`)
    return vm.runInNewContext(ts.transpileModule(`(${declaration.initializer.getText(ast)})`, {
      compilerOptions: { target: ts.ScriptTarget.ES2022 }
    }).outputText, scope)
  }
}
const ref = value => ({ value })
const computed = getter => ({ get value() { return getter() } })
const quiet = { log() {}, error() {}, warn() {}, success() {}, warning() {} }
const ids = rows => Array.from(rows, row => row.id)
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }

function jobsFixture() {
  const scope = {
    jobs: ref([]), searchKeyword: ref(''), selectedIndustry: ref([]), selectedCompany: ref([]),
    selectedRegion: ref([]), selectedSalary: ref(''), selectedDuration: ref(''), selectedInternshipBase: ref(''),
    currentPage: ref(1), pageSize: ref(10), activeQuickFilter: ref('all'), positionsLoading: ref(false), positionsError: ref(''),
    positionsRequestId: 0, computed, console: quiet, ElMessage: quiet, getMonthlySalaryRangeK,
    request: { get: async () => ({ code: 200, data: [] }) }, markAppliedJobs: async () => {}
  }
  const load = pageFunctions('Jobs', scope)
  scope.loadPositions = load('loadPositions')
  return { scope, load }
}

test('职位搜索容忍空字段及首尾空格，12K以上与其他薪资档一样按区间交集匹配', () => {
  const { scope, load } = jobsFixture()
  scope.jobs.value = [
    { id: 1, title: '开发', company: '测试企业', salaryMin: 8, salaryMax: 15 },
    { id: 2, title: null, company: null, salaryMin: 5, salaryMax: 10 }
  ]
  scope.searchKeyword.value = ' 开发 '
  assert.deepEqual(ids(load('filteredJobs').value), [1])
  scope.searchKeyword.value = ''
  scope.selectedSalary.value = '12+'
  assert.deepEqual(ids(load('filteredJobs').value), [1])
})

test('职位类别、公司、地区、基地组合筛选可匹配数字和字符串ID', () => {
  const { scope, load } = jobsFixture()
  scope.jobs.value = [
    { id: 1, industry: 2, companyId: 3, location: '广东省-广州市-天河区', internshipBase: 'national' },
    { id: 2, industry: 2, companyId: 3, location: '广东省-深圳市-南山区', internshipBase: 'national' }
  ]
  scope.selectedIndustry.value = ['2']; scope.selectedCompany.value = ['3']
  scope.selectedRegion.value = ['广东省', '广州市']; scope.selectedInternshipBase.value = 'yes'
  assert.deepEqual(ids(load('filteredJobs').value), [1])
})

test('快速切换职位排序时最后一次点击生效，旧响应不能覆盖新结果', async () => {
  const { scope, load } = jobsFixture()
  const latest = deferred(); const hot = deferred()
  scope.request.get = url => url.endsWith('/latest') ? latest.promise : hot.promise
  const select = load('handleQuickFilter')
  const first = select('latest'); const second = select('hot')
  hot.resolve({ code: 200, data: [{ id: 2 }] }); await second
  latest.resolve({ code: 200, data: [{ id: 1 }] }); await first
  assert.equal(scope.activeQuickFilter.value, 'hot')
  assert.deepEqual(ids(scope.jobs.value), [2])
  assert.equal(scope.positionsLoading.value, false)
})

test('职位刷新沿用当前排序，加载失败不把旧列表伪装成新筛选结果', async () => {
  const { scope } = jobsFixture()
  scope.activeQuickFilter.value = 'recommended'
  scope.jobs.value = [{ id: 1 }]
  const urls = []
  scope.request.get = async url => { urls.push(url); return { code: 500, message: '暂不可用', data: null } }
  await scope.loadPositions()
  assert.deepEqual(urls, ['/positions/recommended'])
  assert.equal(scope.positionsError.value, '暂不可用')
  assert.deepEqual(ids(scope.jobs.value), [])
})

test('已申请标记仅依据同一岗位最新记录，旧通过记录不阻止被拒后重新申请', async () => {
  const { scope, load } = jobsFixture()
  scope.jobs.value = [{ id: 1, isApplied: true }, { id: 2 }, { id: 3 }]
  scope.studentJobApplicationService = { list: async () => ({ code: 200, data: [
    { positionId: 1, status: 'interview_failed' }, { positionId: 1, status: 'approved' },
    { positionId: 2, status: 'pending' }, { positionId: 3, status: 'hired' }
  ] }) }
  scope.RETRYABLE_APPLICATION_STATUSES = new Set(['rejected', 'interview_failed', 'withdrawn', 'cancelled', 'canceled'])
  await load('markAppliedJobs')(scope.jobs.value)
  assert.equal(scope.jobs.value[0].isApplied, false)
  assert.equal(scope.jobs.value[1].isApplied, true)
  assert.equal(scope.jobs.value[2].isApplied, true)
  assert.equal(load('jobApplicationStatus')('hired').text, '已录用')
})

function applicationsFixture() {
  const scope = {
    applications: ref([]), studentApplicationRows: ref([]), jobApplicationRows: ref([]), applicationsRequestId: 0,
    applicationsLoading: ref(false), applicationsError: ref(''), selectedType: ref('all'), selectedFilter: ref('all'),
    computed, console: quiet, request: { get: async () => ({ code: 200, data: [] }) }
  }
  const load = pageFunctions('Applications', scope)
  for (const name of ['statusMap', 'normalizeApplicationStatus', 'formatDateTimeMinute']) scope[name] = load(name)
  return { scope, load, fetch: load('fetchApplications') }
}

test('我的申请按类型与状态交集显示，面试流程属于企业审核已通过，已撤回可单独筛选', async () => {
  const { scope, load, fetch } = applicationsFixture()
  const statuses = ['pending', 'approved', 'pending_interview', 'interview', 'interview_passed', 'interview_failed', 'hired', 'rejected', 'withdrawn', 'unexpected']
  scope.request.get = async url => ({ code: 200, data: url.endsWith('/job-applications')
    ? statuses.map((status, i) => ({ id: i + 1, status, directHire: i === 4, positionName: i === 0 ? '' : `岗位${i}`, createTime: '2026-10-28T14:00:00' }))
    : [{ id: 20, applicationType: 'selfPractice', status: 'approved' }, { id: 21, applicationType: 'delay', status: 'withdrawn' }] })
  await fetch()
  const filtered = load('filteredApplications')
  assert.equal(filtered.value.length, 12)
  scope.selectedType.value = 'job'; scope.selectedFilter.value = 'approved'
  assert.deepEqual(ids(filtered.value).sort((a, b) => a - b), [2, 3, 4, 5, 6, 7])
  scope.selectedFilter.value = 'pending'
  assert.deepEqual(ids(filtered.value), [1])
  scope.selectedFilter.value = 'rejected'
  assert.deepEqual(ids(filtered.value), [8])
  scope.selectedFilter.value = 'withdrawn'
  assert.deepEqual(ids(filtered.value), [9])
  scope.selectedType.value = 'delay'
  assert.deepEqual(ids(filtered.value), [21])
  scope.selectedType.value = 'selfPractice'; scope.selectedFilter.value = 'approved'
  assert.deepEqual(ids(filtered.value), [20])
  assert.equal(scope.applications.value.find(app => app.id === 10).status, 'unknown')
})

test('一个申请接口失败仍显示另一个接口成功数据，并提示重试而不是显示为空', async () => {
  const { scope, fetch } = applicationsFixture()
  scope.request.get = async url => {
    if (url.endsWith('/job-applications')) return { code: 200, data: [{ id: 1, status: 'pending', positionName: '开发' }] }
    throw new Error('student applications unavailable')
  }
  await fetch()
  assert.deepEqual(ids(scope.applications.value), [1])
  assert.match(scope.applicationsError.value, /加载失败/)
  assert.equal(scope.applicationsLoading.value, false)
})

test('申请排序优先使用提交的具体时刻，岗位只有日期时不能被其他类型错误排在最后', async () => {
  const { scope, load, fetch } = applicationsFixture()
  scope.request.get = async url => ({ code: 200, data: url.endsWith('/job-applications')
    ? [{ id: 1, positionName: '开发', status: 'pending', applyDate: '2026-10-28', createTime: '2026-10-28T15:30:00' }]
    : [{ id: 2, applicationType: 'delay', status: 'pending', applyTime: '2026-10-28T14:30:00' }] })
  await fetch()
  assert.deepEqual(ids(load('filteredApplications').value), [1, 2])
  assert.equal(scope.applications.value.find(app => app.id === 1).applyDateTime, '2026-10-28 15:30')
})

test('申请连续刷新仅采用最后一次结果，慢响应不覆盖新状态', async () => {
  const { scope, fetch } = applicationsFixture()
  const oldStudent = deferred(); const oldJob = deferred()
  scope.request.get = url => url.endsWith('/job-applications') ? oldJob.promise : oldStudent.promise
  const oldRefresh = fetch()
  scope.request.get = async url => ({ code: 200, data: url.endsWith('/job-applications')
    ? [{ id: 1, positionName: '开发', status: 'approved' }] : [] })
  await fetch()
  oldStudent.resolve({ code: 200, data: [] })
  oldJob.resolve({ code: 200, data: [{ id: 1, positionName: '开发', status: 'pending' }] })
  await oldRefresh
  assert.equal(scope.applications.value[0].status, 'approved')
  assert.equal(scope.applicationsLoading.value, false)
})

test('部分刷新失败保留该来源之前已成功的记录，同时更新另一来源', async () => {
  const { scope, fetch } = applicationsFixture()
  scope.request.get = async url => ({ code: 200, data: url.endsWith('/job-applications')
    ? [{ id: 1, positionName: '开发', status: 'pending' }]
    : [{ id: 2, applicationType: 'selfPractice', status: 'approved' }] })
  await fetch()
  scope.request.get = async url => url.endsWith('/job-applications')
    ? { code: 200, data: [{ id: 1, positionName: '开发', status: 'rejected' }] }
    : { code: 500, data: null }
  await fetch()
  assert.equal(scope.applications.value.find(app => app.id === 1).status, 'rejected')
  assert.equal(scope.applications.value.find(app => app.id === 2).status, 'approved')
  assert.match(scope.applicationsError.value, /加载失败/)
})

test('撤回同步更新来源缓存，随后接口失败时不会把已撤回申请恢复为待审核', async () => {
  const { scope, load, fetch } = applicationsFixture()
  scope.request.get = async url => ({ code: 200, data: url.endsWith('/job-applications')
    ? [{ id: 1, positionName: '开发', status: 'pending' }]
    : [{ id: 2, applicationType: 'delay', status: 'pending' }] })
  await fetch()
  scope.ElMessageBox = { confirm: async () => {} }; scope.ElMessage = quiet
  scope.request.post = scope.request.delete = async () => ({ code: 200 })
  const withdraw = load('withdrawApplication')
  await withdraw(scope.applications.value.find(app => app.applicationType === 'delay'))
  await withdraw(scope.applications.value.find(app => app.applicationType === 'job'))
  scope.request.get = async () => { throw new Error('offline') }
  await fetch()
  assert.deepEqual(ids(scope.applications.value), [2])
  assert.equal(scope.applications.value[0].status, 'withdrawn')
  scope.selectedFilter.value = 'pending'
  assert.deepEqual(ids(load('filteredApplications').value), [])
})
