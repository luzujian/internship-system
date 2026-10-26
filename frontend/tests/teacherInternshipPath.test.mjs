import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'

const source = readFileSync(new URL('../src/views/teacher/TeacherStudentManagement.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const ast = ts.createSourceFile('TeacherStudentManagement.vue', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const names = ['students', 'filters', 'currentPage', 'pageSize', 'total', 'clientPagination', 'pagedStudents',
  'resolveInternshipCompanyName', 'internshipPathLabels', 'getInternshipPath', 'loadInternshipPathsByStudent', 'loadStudents', 'resetFilters',
  'handleCurrentChange', 'handleSizeChange', 'sendBatchReminder']
const declarations = ast.statements.filter(ts.isVariableStatement)
  .flatMap(statement => [...statement.declarationList.declarations])
  .filter(declaration => names.includes(declaration.name.getText(ast)))
  .map(declaration => `const ${declaration.getText(ast)}`)
const compiled = ts.transpileModule(`${declarations.join('\n')}\n({ ${names.join(', ')} })`, {
  compilerOptions: { target: ts.ScriptTarget.ES2022 }
}).outputText
const ref = value => ({ value })
const computed = getter => ({ get value() { return getter() } })

const rows = Array.from({ length: 52 }, (_, index) => ({
  id: index + 1000, student: { id: index + 1, studentUserId: `student-${index + 1}`, name: `学生${index + 1}` },
  status: index >= 40 ? 6 : 3,
  companyId: index < 25 ? 8 : null,
  company: index < 25 ? { companyName: '同名企业' } : null,
  companyName: '同名企业'
}))

function page({ counselor = false, management = false, data = rows, extraCounselorRows = [] } = {}) {
  const calls = [], reminders = [], errors = []
  const state = vm.runInNewContext(compiled, {
    ref, computed, console: { error: (...args) => errors.push(args) }, loading: ref(false),
    isCounselor: ref(counselor), isStudentManagementPage: ref(management), showInternshipFields: ref(!management),
    counselorId: ref(9), myClasses: ref([]), scopeName: ref(''), scopeType: ref(''),
    getStatusValue: value => Number(value),
    studentApi: {
      async getStudentList(params) {
        calls.push({ endpoint: 'scoped', params })
        const start = (params.page - 1) * params.pageSize
        return { rows: data.slice(start, start + params.pageSize), total: data.length }
      }
    },
    async getCounselorStudents(...params) {
      calls.push({ endpoint: 'management', params })
      return { data: [...data.map(item => ({ ...item.student, status: 1, internshipStatus: String(item.status), companyName: item.companyName })), ...extraCounselorRows] }
    },
    ElMessage: { error() {}, warning() {}, success() {} },
    batchReminderForm: ref({ content: '请补充实习材料' }), batchReminderSending: ref(false),
    showBatchReminderDialog: ref(true),
    formContext: { beginFormOperation: () => ({ complete() {} }) },
    request: { async post(_url, body) { reminders.push([...body.studentIds]); return { data: { success: body.studentIds.length, fail: 0 } } } }
  })
  return { ...state, calls, reminders, errors }
}

test('three paths distinguish registered and autonomous placements with the same company name', () => {
  const state = page()
  assert.equal(state.getInternshipPath({ status: 3, companyId: 8, company: { companyName: '同名企业' } }), 'mutualChoice')
  assert.equal(state.getInternshipPath({ status: 3, companyId: null, companyName: '同名企业' }), 'selfPractice')
  assert.equal(state.getInternshipPath({ status: 4, companyId: null, companyName: '自主单位' }), 'selfPractice')
  assert.equal(state.getInternshipPath({ status: 0, companyId: null, companyName: '   ' }), 'mutualChoice')
  assert.equal(state.getInternshipPath({ status: 0 }), 'mutualChoice')
})

test('current delay takes priority over retained placement; a stale delay flag does not override a resumed internship', () => {
  const state = page()
  assert.equal(state.getInternshipPath({ status: 6, companyId: 8, companyName: '平台单位' }), 'delay')
  assert.equal(state.getInternshipPath({ internshipStatus: '6', status: 1, companyName: '自主单位' }), 'delay')
  assert.equal(state.getInternshipPath({ status: 3, isDelayed: true, companyId: 8 }), 'mutualChoice')
})

for (const counselor of [false, true]) {
  test(`${counselor ? 'counselor' : 'college/department'} tracking filters the complete authorized list before pagination`, async () => {
    const state = page({ counselor })
    state.filters.value.internshipPath = 'selfPractice'
    await state.loadStudents()
    const scoped = state.calls.find(call => call.endpoint === 'scoped')
    assert.ok(scoped)
    assert.equal(scoped.params.page, 1)
    assert.equal(scoped.params.pageSize, 99999)
    assert.equal(state.total.value, 15)
    assert.equal(state.pagedStudents.value.length, 10)
    assert.ok(state.students.value.every(item => item.internshipPath === 'selfPractice'))
    state.handleCurrentChange(2)
    assert.equal(state.pagedStudents.value.length, 5)
    assert.equal(state.pagedStudents.value[0].id, 36)
    state.handleSizeChange(20)
    assert.equal(state.currentPage.value, 1)
    assert.equal(state.pagedStudents.value.length, 15)
    assert.equal(state.calls.length, counselor ? 2 : 1)
    assert.equal(state.errors.length, 0)
  })
}

test('all three filters partition the list; reset restores server pagination and all records', async () => {
  const state = page()
  for (const [path, count] of [['mutualChoice', 25], ['selfPractice', 15], ['delay', 12]]) {
    state.filters.value.internshipPath = path
    await state.loadStudents()
    assert.equal(state.total.value, count)
  }
  state.filters.value.class = '测试班级'
  state.filters.value.company = '同名企业'
  state.filters.value.status = '3'
  state.filters.value.major = '智能医学工程'
  state.filters.value.grade = '2023级'
  state.filters.value.search = '学生'
  await state.loadStudents()
  assert.deepEqual({ ...state.calls.at(-1).params }, {
    page: 1, pageSize: 99999, name: '学生', grade: '2023级', major: '智能医学工程',
    className: '测试班级', status: 3, companyName: '同名企业'
  })
  state.resetFilters()
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(state.filters.value.internshipPath, '')
  assert.equal(state.total.value, 52)
  assert.equal(state.clientPagination.value, false)
  assert.equal(state.pagedStudents.value.length, 10)
  state.handleCurrentChange(2)
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(state.pagedStudents.value[0].id, 11)
})

test('counselor student management retains its authorized account list and local pagination', async () => {
  const state = page({ counselor: true, management: true })
  await state.loadStudents()
  assert.equal(state.calls[0].endpoint, 'management')
  assert.equal(state.total.value, 52)
  state.handleCurrentChange(2)
  assert.equal(state.pagedStudents.value.length, 10)
  assert.equal(state.pagedStudents.value[0].id, 11)
  assert.equal(state.calls.length, 1)
})

test('a path-filtered batch reminder addresses only matching student primary keys', async () => {
  const state = page({ counselor: true })
  state.filters.value.internshipPath = 'selfPractice'
  await state.sendBatchReminder()
  assert.ok(state.calls.some(call => call.endpoint === 'scoped'))
  assert.deepEqual(state.reminders, [Array.from({ length: 15 }, (_, index) => index + 26)])
  assert.equal(state.errors.length, 0)
})

test('counselor tracking keeps students without a status row and classifies them as mutual choice', async () => {
  const state = page({ counselor: true, extraCounselorRows: [{ id: 99, name: '尚未登记', status: 1, internshipStatus: '0' }] })
  await state.loadStudents()
  assert.equal(state.total.value, 53)
  assert.equal(state.students.value.at(-1).internshipPath, 'mutualChoice')
  state.filters.value.internshipPath = 'mutualChoice'
  await state.loadStudents()
  assert.equal(state.total.value, 26)
  await state.sendBatchReminder()
  assert.ok(state.reminders[0].includes(99))
})
