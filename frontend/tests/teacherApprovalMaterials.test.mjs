import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'
import { studentApplicationMaterialByteUrl } from '../src/utils/authorizedFileUrl.ts'

const source = readFileSync(new URL('../src/views/teacher/Approval.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const ast = ts.createSourceFile('Approval.vue', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const ref = value => ({ value })
function handler(name, scope) {
  const declaration = ast.statements.filter(ts.isVariableStatement)
    .flatMap(statement => [...statement.declarationList.declarations])
    .find(item => item.name.getText(ast) === name)
  assert.ok(declaration, `${name} exists`)
  return vm.runInNewContext(ts.transpileModule(`(${declaration.initializer.getText(ast)})`, {
    compilerOptions: { target: ts.ScriptTarget.ES2022 }
  }).outputText, scope)
}

function materialScope(application = { id: 71, type: 'selfPractice', materials: { 成绩单: 'uploaded.pdf' } }) {
  const errors = []
  const scope = {
    currentApplication: ref(application), materialPreviewName: ref(''), materialPreviewUrl: ref(''),
    materialPreviewVisible: ref(false), materialDownloading: ref(''),
    studentApplicationMaterialByteUrl, ElMessage: { error: message => errors.push(message) }
  }
  for (const name of ['attachmentName', 'applicationMaterialKey', 'hasApplicationMaterial']) scope[name] = handler(name, scope)
  return { scope, errors }
}

test('teacher previews use the authorized application record and retain the file extension', () => {
  const { scope, errors } = materialScope({ id: 71, type: 'selfPractice', materials: { 成绩单: 'https://oss.example/private/transcript.PDF' } })
  handler('previewApplicationMaterial', scope)('成绩单')
  assert.equal(scope.materialPreviewVisible.value, true)
  assert.equal(scope.materialPreviewName.value, '成绩单.PDF')
  assert.equal(scope.materialPreviewUrl.value, '/api/approval/student-applications/71/materials/%E6%88%90%E7%BB%A9%E5%8D%95')
  assert.deepEqual(errors, [])
  assert.equal(scope.attachmentName('已上传.pdf', 'uploaded.pdf'), '已上传.pdf')
})

test('legacy delay plans preview through their stored material key', () => {
  const { scope } = materialScope({ id: 72, type: 'delay', materials: { delayPlan: 'uploaded.docx' } })
  handler('previewApplicationMaterial', scope)('考研计划')
  assert.equal(scope.materialPreviewName.value, '考研计划.docx')
  assert.equal(scope.materialPreviewUrl.value, '/api/approval/student-applications/72/materials/delayPlan')
})

test('missing or invalid material records cannot open a preview', () => {
  for (const application of [{ id: 71, materials: {} }, { id: '../71', materials: { 成绩单: 'uploaded.pdf' } }]) {
    const { scope, errors } = materialScope(application)
    handler('previewApplicationMaterial', scope)('成绩单')
    assert.equal(scope.materialPreviewVisible.value, false)
    assert.equal(scope.materialPreviewUrl.value, '')
    assert.equal(errors.length, 1)
  }
})

function agreementScope() {
  return {
    agreementRequestId: 0, currentApplication: ref({ id: 71, type: 'selfPractice' }),
    showApplicationDetailModal: ref(true), applicationAgreement: ref(null),
    applicationAgreementLoading: ref(false), applicationAgreementError: ref('')
  }
}

test('late agreement responses cannot replace another student or a closed detail', async () => {
  const scope = agreementScope()
  const pending = new Map()
  scope.getAgreementDetail = (role, type, id) => {
    assert.equal(role, 'teacher')
    assert.equal(type, 'SELF_PRACTICE')
    return new Promise(resolve => pending.set(id, resolve))
  }
  const load = handler('loadApplicationAgreement', scope)
  const first = load()
  scope.currentApplication.value = { id: 72, type: 'selfPractice' }
  const second = load()
  pending.get(72)({ code: 200, data: { versions: [{ id: 720 }] } })
  await second
  pending.get(71)({ code: 200, data: { versions: [{ id: 710 }] } })
  await first
  assert.equal(scope.applicationAgreement.value.versions[0].id, 720)
  assert.equal(scope.applicationAgreementLoading.value, false)
  const closing = load()
  scope.showApplicationDetailModal.value = false
  pending.get(72)({ code: 200, data: { versions: [{ id: 721 }] } })
  await closing
  assert.equal(scope.applicationAgreement.value, null)
})

test('agreement failures remain distinct from an empty submission and allow retry', async () => {
  const scope = agreementScope()
  let fail = true
  scope.getAgreementDetail = async () => {
    if (fail) throw new Error('教师无权查看该记录')
    return { code: 200, data: { versions: [] } }
  }
  const load = handler('loadApplicationAgreement', scope)
  await load()
  assert.equal(scope.applicationAgreement.value, null)
  assert.equal(scope.applicationAgreementError.value, '教师无权查看该记录')
  assert.equal(scope.applicationAgreementLoading.value, false)
  fail = false
  await load()
  assert.equal(scope.applicationAgreementError.value, '')
  assert.equal(scope.applicationAgreement.value.versions.length, 0)
})

test('ordinary material download releases its busy state after failure', async () => {
  const { scope, errors } = materialScope()
  let calls = 0
  let reject
  scope.downloadAuthorizedFile = () => { calls++; return new Promise((_, fail) => { reject = fail }) }
  const download = handler('downloadApplicationMaterial', scope)
  const first = download('成绩单')
  await download('成绩单')
  assert.equal(calls, 1)
  reject(new Error('read failed'))
  await first
  assert.equal(scope.materialDownloading.value, '')
  assert.equal(errors.length, 1)
})

test('agreement downloads use the teacher file endpoint and retain the original name', async () => {
  const { scope } = materialScope()
  const saved = []
  scope.readAgreementFile = async (role, id) => {
    assert.equal(role, 'teacher')
    assert.equal(id, 921)
    return 'authorized bytes'
  }
  scope.saveAgreementBlob = (bytes, name) => saved.push({ bytes, name })
  await handler('downloadApplicationAgreement', scope)({ id: 921, fileName: '盖章实习协议.pdf' })
  assert.deepEqual(saved, [{ bytes: 'authorized bytes', name: '盖章实习协议.pdf' }])
  assert.equal(scope.materialDownloading.value, '')
})
