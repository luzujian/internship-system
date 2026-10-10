import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'

const ref = value => ({ value })
const source = readFileSync(new URL('../src/views/teacher/TeacherStudentManagement.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)

function declarations(file, script, names) {
  const ast = ts.createSourceFile(file, script, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  return ast.statements.filter(ts.isVariableStatement)
    .flatMap(statement => [...statement.declarationList.declarations])
    .filter(declaration => names.includes(declaration.name.getText(ast)))
    .map(declaration => `const ${declaration.getText(ast)}`)
    .join('\n')
}

const apiSource = readFileSync(new URL('../src/api/teacherStudent.ts', import.meta.url), 'utf8')
const script = declarations('teacherStudent.ts', apiSource, ['studentApi']) + '\n' +
  declarations('TeacherStudentManagement.vue', descriptor.scriptSetup.content, ['statistics', 'loadStatistics']) +
  '\n({ statistics, loadStatistics })'
const compiled = ts.transpileModule(script, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText

const internshipStatistics = {
  studentCount: 139, noOfferCount: 83, pendingCount: 0, confirmedCount: 0,
  interningCount: 56, finishedCount: 0, interruptedCount: 0, delayedCount: 0
}

function page({ counselor = false, management = false, data = internshipStatistics } = {}) {
  const calls = []
  const statsLoading = ref(false)
  const state = vm.runInNewContext(compiled, {
    ref, statsLoading, isCounselor: ref(counselor), isStudentManagementPage: ref(management), counselorId: ref(9),
    console: { error() {} },
    request: {
      get: async url => {
        calls.push(url)
        return { code: 200, message: 'success', data }
      }
    },
    getCounselorStatistics: async id => {
      calls.push(`/teacher/classes/statistics/${id}`)
      return { code: 200, data: { classCount: 3, studentCount: 90 } }
    }
  })
  return { ...state, statsLoading, calls }
}

test('tracking cards use the statistics payload unwrapped by the actual student API', async () => {
  const state = page()
  await state.loadStatistics()
  assert.deepEqual(state.calls, ['/teacher/internship-status/statistics'])
  assert.equal(state.statistics.value.studentCount, 139)
  assert.equal(state.statistics.value.noOfferCount, 83)
  assert.equal(state.statistics.value.interningCount, 56)
  assert.equal(state.statistics.value.finishedCount, 0)
  assert.equal(state.statsLoading.value, false)
})

test('counselor tracking uses internship statistics scoped by the authenticated teacher', async () => {
  const state = page({ counselor: true, data: { ...internshipStatistics, studentCount: 90, noOfferCount: 30, interningCount: 58, finishedCount: 2 } })
  await state.loadStatistics()
  assert.deepEqual(state.calls, ['/teacher/internship-status/statistics'])
  assert.equal(state.statistics.value.studentCount, 90)
  assert.equal(state.statistics.value.noOfferCount, 30)
  assert.equal(state.statistics.value.interningCount, 58)
  assert.equal(state.statistics.value.finishedCount, 2)
})

test('counselor student management retains class and student counts', async () => {
  const state = page({ counselor: true, management: true })
  await state.loadStatistics()
  assert.deepEqual(state.calls, ['/teacher/classes/statistics/9'])
  assert.equal(state.statistics.value.classCount, 3)
  assert.equal(state.statistics.value.studentCount, 90)
})

test('zero counts remain valid and old nonzero counts are cleared on reload', async () => {
  const state = page({ data: { studentCount: 0, noOfferCount: 0, interningCount: 0, finishedCount: 0 } })
  state.statistics.value.studentCount = 12
  state.statistics.value.noOfferCount = 10
  state.statistics.value.interningCount = 1
  state.statistics.value.finishedCount = 1
  await state.loadStatistics()
  for (const name of ['studentCount', 'noOfferCount', 'interningCount', 'finishedCount']) {
    assert.equal(state.statistics.value[name], 0)
  }
})
