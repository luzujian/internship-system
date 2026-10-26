import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'
import { ref, computed, watch, nextTick } from 'vue'

const { descriptor } = parse(readFileSync(new URL('../src/views/admin/StudentUserManagement.vue', import.meta.url), 'utf8'))
const ast = ts.createSourceFile('StudentUserManagement.ts', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const declarations = ast.statements.filter(ts.isVariableStatement).flatMap(statement => [...statement.declarationList.declarations])
const evaluate = (source, scope) => vm.runInNewContext(ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, scope)
const load = (name, scope) => evaluate(`(${declarations.find(item => item.name.getText(ast) === name).initializer.getText(ast)})`, scope)
const noop = () => {}
const majors = ['生物医学工程', '信息管理与信息系统', '智能医学工程', '联合办学', '信息资源管理', '数据科学与大数据技术']
  .map((name, index) => ({ value: String(39 + index), name, departmentId: '24' }))
const original = { id: 7, studentId: 'test001', name: '测试学生', grade: 2023, majorId: 39, classId: 182, department: '生物医学工程学院' }

function fixture(t, detail = original) {
  const errors = []
  const scope = {
    ref, computed, watch, nextTick,
    logger: { log: noop, warn: noop, error: noop },
    ElMessage: { error: message => errors.push(message) },
    queryInfoApi: async () => ({ code: 200, data: detail }),
    registerStudentForm: noop,
    editStudent: ref({}), addStudent: ref({}), editStudentFormRef: ref(null),
    dialogFormVisible: ref(false), formTitle: ref(''),
    departments: ref([{ value: '24', name: '生物医学工程学院' }, { value: '25', name: '其他学院' }]),
    majors: ref([...majors, { value: '50', name: '其他专业', departmentId: '25' }]),
    classes: ref([{ id: '182', name: '原班级', majorId: '39' }, { id: '183', name: '其他班级', majorId: '40' }])
  }
  for (const name of ['filteredMajorsForEdit', 'handleDialogDepartmentChange', 'handleDialogMajorChange', 'studentFillFields']) scope[name] = load(name, scope)
  for (const statement of ast.statements) {
    const text = statement.getText(ast)
    if (text.startsWith('watch(() => editStudent.value.')) t.after(evaluate(text, scope))
  }
  return {
    scope,
    async open() {
      await load('handleUpdateStudent', scope)(detail.id)
      await nextTick()
      assert.deepEqual(errors, [])
      assert.equal(scope.dialogFormVisible.value, true)
    }
  }
}

test('编辑时将院系名称解析为ID，保留原专业与班级并提供全部同院专业', async t => {
  const { scope, open } = fixture(t)
  await open()
  assert.equal(scope.editStudent.value.department, '24')
  assert.equal(scope.editStudent.value.majorId, '39')
  assert.equal(scope.editStudent.value.classId, '182')
  assert.equal(scope.filteredMajorsForEdit.value.length, 6)
  const fields = scope.studentFillFields()
  assert.equal(fields.find(field => field.prop === 'majorId').options.length, 6)
  assert.equal(fields.find(field => field.prop === 'classId').options[0].value, '182')
})

test('已选专业决定所属院系，兼容旧名称、数字ID和缺失院系', async t => {
  for (const department of ['24', 24, '旧学院名称', '', null, undefined]) {
    const { scope, open } = fixture(t, { ...original, department })
    await open()
    assert.equal(scope.editStudent.value.department, '24')
    assert.equal(scope.editStudent.value.majorId, '39')
    assert.equal(scope.editStudent.value.classId, '182')
  }
})

test('缺少专业和班级时按院系名称或ID回填，空值保持未选择', async t => {
  for (const department of ['生物医学工程学院', '24', 24]) {
    const { scope, open } = fixture(t, { ...original, majorId: null, classId: null, department })
    await open()
    assert.equal(scope.editStudent.value.department, '24')
    assert.equal(scope.editStudent.value.majorId, null)
    assert.equal(scope.editStudent.value.classId, null)
    assert.equal(scope.filteredMajorsForEdit.value.length, 6)
  }
})

test('手动切换专业仅清空班级，切换院系清空专业和班级并更新选项', async t => {
  const { scope, open } = fixture(t)
  await open()
  scope.handleDialogMajorChange('40')
  await nextTick()
  assert.equal(scope.editStudent.value.department, '24')
  assert.equal(scope.editStudent.value.majorId, '40')
  assert.equal(scope.editStudent.value.classId, null)
  assert.equal(scope.studentFillFields().find(field => field.prop === 'classId').options[0].value, '183')
  scope.handleDialogDepartmentChange('25')
  await nextTick()
  assert.equal(scope.editStudent.value.department, '25')
  assert.equal(scope.editStudent.value.majorId, null)
  assert.equal(scope.editStudent.value.classId, null)
  assert.equal(scope.filteredMajorsForEdit.value.length, 1)
  assert.equal(scope.filteredMajorsForEdit.value[0].value, '50')
})
