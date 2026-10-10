import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'

const ref = value => ({ value })
const noop = () => {}
const messages = { success: noop, error: noop, warning: noop }
function declaration(file, name, scope) {
  const source = readFileSync(new URL(`../src/views/${file}.vue`, import.meta.url), 'utf8')
  const { descriptor } = parse(source)
  const ast = ts.createSourceFile(file, descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  const variable = ast.statements.filter(ts.isVariableStatement)
    .flatMap(statement => [...statement.declarationList.declarations])
    .find(item => item.name.getText(ast) === name)
  assert.ok(variable, `${file}:${name} exists`)
  const compiled = ts.transpileModule(`(() => { const actual = ${variable.initializer.getText(ast)}; return actual })()`, {
    compilerOptions: { target: ts.ScriptTarget.ES2022 }
  }).outputText
  return vm.runInNewContext(compiled, scope)
}

for (const type of ['job', 'delay']) test(`withdrawing ${type} keeps another application table's matching ID`, async () => {
  const original = { id: 7, applicationType: 'selfPractice', status: 'pending' }
  const target = { id: 7, applicationType: type, status: 'pending' }
  const applications = ref([original, target])
  const calls = []
  const write = async url => { calls.push(url); return { code: 200 } }
  await declaration('student/Applications', 'withdrawApplication', {
    applications, request: { post: write, delete: write },
    ElMessageBox: { confirm: async () => {} }, ElMessage: messages
  })(target)
  assert.equal(applications.value[0], original)
  if (type === 'job') assert.equal(applications.value.length, 1)
  else assert.equal(applications.value[1].status, 'withdrawn')
  assert.equal(calls.length, 1)
})

test('application loading preserves the submitted date and same-day ordering', async () => {
  const oldTimezone = process.env.TZ
  process.env.TZ = 'Asia/Shanghai'
  try {
    const applications = ref([])
    const rows = [
      { id: 2, positionName: '岗位', status: 'pending', applyDate: '2026-10-02T00:30:00' },
      { id: 3, positionName: '岗位', status: 'pending', applyDate: '2026-10-02T03:30:00' },
      { id: 4, positionName: '岗位', status: 'pending', applyDate: 'historical-invalid-date' }
    ]
    const formatDateTimeMinute = declaration('student/Applications', 'formatDateTimeMinute', {})
    await declaration('student/Applications', 'fetchApplications', {
      applications, console: { error: noop }, formatDateTimeMinute,
      statusMap: { pending: 'pending' }, normalizeApplicationStatus: status => status,
      request: { get: async url => ({ code: 200, data: url.endsWith('job-applications') ? rows : [] }) }
    })()
    assert.equal(applications.value.length, 3, 'one malformed historical date must not hide all applications')
    assert.equal(applications.value[0].applyDate, '2026-10-02')
    const sorted = declaration('student/Applications', 'filteredApplications', {
      applications, computed: fn => fn(), selectedType: ref('all'), selectedFilter: ref('all')
    })
    assert.equal(sorted[0].id, 3, 'latest application within the day comes first')
  } finally {
    if (oldTimezone === undefined) delete process.env.TZ
    else process.env.TZ = oldTimezone
  }
})

for (const file of ['ApplicationView', 'InternshipConfirm']) test(`${file} search returns to the first results page`, () => {
  const currentPage = ref(4)
  declaration(`company/${file}`, 'handleSearch', { currentPage, ElMessage: messages })()
  assert.equal(currentPage.value, 1)
})

test('confirmation search supports missing names and numeric student numbers', () => {
  const result = declaration('company/InternshipConfirm', 'filteredTableData', {
    computed: fn => fn(), searchForm: ref({ keyword: '2026', position: '', status: '' }),
    confirmationRecords: ref([
      { id: 1, studentName: null, studentUserId: 20260001, companyConfirmStatus: 0 },
      { id: 2, studentName: undefined, studentUserId: undefined, companyConfirmStatus: 1 }
    ])
  })
  assert.equal(result.length, 1)
  assert.equal(result[0].id, 1)
})

test('switching student details never shows attachments from a previous request', async () => {
  const responses = new Map()
  const studentArchives = ref([{ id: 99, fileName: 'previous student.pdf' }])
  const currentStudent = ref(null)
  const show = declaration('company/ApplicationView', 'handleViewDetail', {
    currentStudent, detailDialogVisible: ref(false), studentArchives,
    detailRequest: 0, activityReadError: ref(''), activityReadUnavailable: ref(false), acknowledgeActivity: async () => {},
    console: { log: noop, error: noop },
    studentArchiveService: { getStudentArchives: id => new Promise(resolve => responses.set(id, resolve)) }
  })
  const first = show({ id: 1, studentId: 11 })
  assert.equal(studentArchives.value.length, 0, 'clear previous material immediately')
  const second = show({ id: 2, studentId: 22 })
  responses.get(22)({ code: 200, data: [{ id: 222, fileName: 'student B.pdf' }] })
  await second
  responses.get(11)({ code: 200, data: [{ id: 111, fileName: 'student A.pdf' }] })
  await first
  assert.equal(currentStudent.value.studentId, 22)
  assert.equal(studentArchives.value[0].id, 222)
  await show({ id: 3 })
  assert.equal(studentArchives.value.length, 0, 'a record without student ID must not retain other material')
})

for (const keys of [['selfPractice'], ['delay', 'companyQualification']]) {
  test(`teacher approval badges match visible tabs: ${keys.join(', ')}`, async () => {
    const approvalTabs = ref(keys.map(key => ({ key, count: 0 })))
    const counts = { selfPracticePending: 3, delayPending: 5, companyQualificationPending: 7 }
    const scope = { approvalTabs, console: { error: noop }, approvalApi: { getStats: async () => counts } }
    const load = declaration('teacher/Approval', 'fetchStats', scope)
    await load()
    for (const tab of approvalTabs.value) assert.equal(tab.count, counts[`${tab.key}Pending`])
    scope.approvalApi.getStats = async () => { throw new Error('offline') }
    await load()
    assert.ok(approvalTabs.value.every(tab => tab.count === 0))
  })
}

test('late approval requests cannot overwrite another tab or relabel its records', async () => {
  let finishStudent, finishCompany
  const scope = {
    activeTab: ref('delay'), currentPage: ref(1), pageSize: ref(10), searchForm: { keyword: '', status: '' },
    applications: ref([]), total: ref(0), loading: ref(false), listRequestId: ref(0),
    console: { error: noop }, ElMessage: messages,
    approvalApi: {
      getStudentApplications: () => new Promise(resolve => { finishStudent = resolve }),
      getCompanyQualifications: () => new Promise(resolve => { finishCompany = resolve })
    }
  }
  const load = declaration('teacher/Approval', 'fetchApplications', scope)
  const studentRequest = load()
  scope.activeTab.value = 'companyQualification'
  const companyRequest = load()
  finishCompany({ rows: [{ id: 8, companyName: '目标企业' }], total: 1 })
  await companyRequest
  finishStudent({ rows: [{ id: 8, studentName: '过期学生' }], total: 4 })
  await studentRequest
  assert.equal(scope.applications.value[0].companyName, '目标企业')
  assert.equal(scope.applications.value[0].type, 'companyQualification')
  assert.equal(scope.total.value, 1)
  assert.equal(scope.loading.value, false)
})

test('appraisal companies include only confirmed internships and retain self-practice', async () => {
  const internshipRecords = ref([])
  const selectedPlacementId = ref('R1')
  const placementKey = row => `${row.isFromStatus ? 'S' : 'R'}${row.id}`
  await declaration('student/InternshipAppraisal', 'fetchInternshipRecords', {
    internshipRecords, selectedPlacementId, placementKey, console: { error: noop },
    request: { get: async () => ({ code: 200, data: [
      { id: 1, status: 0, isCurrent: true },
      { id: 2, status: 2 },
      { id: 3, status: 1, recallStatus: 2 },
      { id: 4, status: 1, placementId: 44 },
      { id: 5, status: 1, isFromStatus: true, isCurrent: true, placementId: null }
    ] }) }
  })()
  assert.deepEqual(Array.from(internshipRecords.value, row => row.id), [4, 5])
  assert.equal(selectedPlacementId.value, 'S5', 'a stale pending selection resets to a valid internship')
})

test('renaming a position keeps its existing applications visible under the correct position', () => {
  const scope = {
    computed: fn => fn(), asApplicationRows: input => input,
    searchForm: ref({ keyword: '', position: '新岗位', status: '' }),
    positionStore: {
      positions: [{ id: 7, positionName: '新岗位', status: 'active' }, { id: 8, positionName: '旧岗位', status: 'active' }],
      jobApplications: [{ id: 9, positionId: 7, positionName: '旧岗位', status: 'pending', studentName: '学生' }]
    }
  }
  scope.currentApplicationRows = ref(declaration('company/ApplicationView', 'currentApplicationRows', scope))
  const groups = declaration('company/ApplicationView', 'groupedApplications', scope)
  assert.equal(groups.find(group => group.position === '新岗位').pending, 1)
  assert.equal(groups.find(group => group.position === '旧岗位').pending, 0)
  const result = declaration('company/ApplicationView', 'filteredTableData', scope)
  assert.equal(result.length, 1)
  assert.equal(result[0].id, 9)
  assert.equal(result[0].positionName, '新岗位')
})

test('appraisal collection keeps the latest search when responses arrive out of order', async () => {
  const pending = []
  const scope = {
    collectionRequestToken: 0, loading: ref(false), rows: ref([]), pagination: { total: 0 },
    summary: {}, classOptions: ref([]), queryParams: () => ({}), ElMessage: messages,
    getAppraisalCollection: () => new Promise(resolve => pending.push(resolve))
  }
  const load = declaration('teacher/InternshipAppraisalCollection', 'loadData', scope)
  const first = load(), second = load()
  pending[1]({ data: { rows: [{ studentId: 2 }], total: 1, submittedCount: 1 } })
  await second
  pending[0]({ data: { rows: [{ studentId: 1 }], total: 3, submittedCount: 3 } })
  await first
  assert.equal(scope.rows.value[0].studentId, 2)
  assert.equal(scope.pagination.total, 1)
  assert.equal(scope.summary.submittedCount, 1)
})

test('late appraisal detail failure cannot close the newly opened student drawer', async () => {
  const pending = new Map()
  const scope = {
    detailRequestToken: 0, detailVisible: ref(false), detailLoading: ref(false), detail: ref(null), ElMessage: messages,
    getAppraisalStudentDetail: id => new Promise((resolve, reject) => pending.set(id, { resolve, reject }))
  }
  const open = declaration('teacher/InternshipAppraisalCollection', 'openDetail', scope)
  const first = open(1), second = open(2)
  pending.get(2).resolve({ data: { studentId: 2, files: [] } })
  await second
  pending.get(1).reject(new Error('earlier request timed out'))
  await first
  assert.equal(scope.detailVisible.value, true)
  assert.equal(scope.detail.value.studentId, 2)
  assert.equal(scope.detailLoading.value, false)
})
