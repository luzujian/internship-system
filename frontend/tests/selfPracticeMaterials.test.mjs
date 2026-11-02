import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'
import { ref as vueRef, watchEffect, nextTick } from 'vue'

const ref = value => ({ value })
const noop = () => {}
const plain = value => JSON.parse(JSON.stringify(value))
const { descriptor } = parse(readFileSync(new URL('../src/views/student/Home.vue', import.meta.url), 'utf8'))
const ast = ts.createSourceFile('Home.ts', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const names = [
  'selfPracticeOtherFileList', 'selfPracticeOtherMaterialKeys', 'selfPracticeOriginalMaterials',
  'selfPracticeRemovedMaterialKeys', 'selfPracticeOriginalCompany', 'retainedSelfPracticeMaterials',
  'resetSelfPracticeForm', 'removeRetainedSelfPracticeMaterial', 'removeSelfPracticeOtherFile',
  'handleSelfPracticeOtherFileChange', 'submitOnboardingApplication'
]
const declarations = ast.statements.filter(ts.isVariableStatement)
  .flatMap(statement => [...statement.declarationList.declarations])
  .filter(declaration => names.includes(declaration.name.getText(ast)))
assert.equal(declarations.length, names.length)
const compiled = ts.transpileModule(
  declarations.map(declaration => `const ${declaration.getText(ast)};`).join('\n')
    + `\nglobalThis.handlers = { ${names.join(',')} };`,
  { compilerOptions: { target: ts.ScriptTarget.ES2022 } }
).outputText

function fixture(upload = () => ({ code: 200, data: { url: 'private/replacement.pdf' } }), stateRef = ref) {
  const calls = [], messages = []
  const application = { id: 15, status: 'withdrawn', company: '测试单位', positionName: '实习生',
    companyCity: '广州', hrManagerName: '张经理', hrManagerPhone: '13812345678', reason: '申请实习',
    materials: { '个人申请书': 'uploaded.pdf', '实习接收函': 'uploaded.docx' } }
  const scope = {
    ref: stateRef, computed: getter => ({ get value() { return getter() } }),
    normalizeCompanyName: name => String(name || '').trim().toLowerCase(),
    selfPracticeForm: ref({}), selfPracticeApplication: ref(application),
    selfPracticeAgreementUploader: ref({ reset: noop, validate: () => true, markCommitted: noop }),
    selfPracticeAgreementFiles: ref([{ uploadId: 501, status: 'success' }]),
    matchedCooperativeCompany: ref(null), similarCooperativeCompanies: ref([]), onboardingSubmitting: ref(false),
    ensureSelfPracticeCompanyName: async () => true,
    authStore: { user: { id: 1, name: '测试学生' } },
    FormData: class { append() {} }, console,
    ElMessage: { success: noop, error: message => messages.push(message), warning: message => messages.push(message) },
    request: {
      async post(url, body) { calls.push({ url, body: plain(body) }); return url === '/upload/file' ? upload() : { code: 200 } },
      async put(url, body) { calls.push({ url, body: plain(body) }); return { code: 200 } }
    },
    formContext: { beginFormOperation: () => ({ complete: noop }) },
    scheduleProgressRefresh: noop, loadSelfPracticeApplication: noop
  }
  vm.runInNewContext(compiled, scope)
  const result = { ...scope, ...scope.handlers, calls, messages, application }
  result.resetSelfPracticeForm(application)
  result.selfPracticeAgreementFiles.value = [{ uploadId: 501, status: 'success' }]
  return result
}

test('删除原有资料只修改当前草稿，重提明确发送删除项并保留其他资料', async () => {
  const f = fixture()
  f.removeRetainedSelfPracticeMaterial('个人申请书')
  assert.deepEqual(plain(f.retainedSelfPracticeMaterials.value), { '实习接收函': 'uploaded.docx' })
  assert.equal(f.application.materials['个人申请书'], 'uploaded.pdf')
  assert.equal(f.calls.length, 0)
  await f.submitOnboardingApplication('selfPractice')
  assert.equal(f.calls[0].url, '/student/applications/15/resubmit')
  assert.deepEqual(f.calls[0].body.removedMaterialKeys, ['个人申请书'])
  assert.deepEqual(f.calls[0].body.materials, {})
  assert.deepEqual(f.calls[0].body.agreementFileIds, [501])
})

test('其他资料选填，允许删除全部旧附件后重新提交', async () => {
  const f = fixture()
  for (const key of Object.keys(f.application.materials)) f.removeRetainedSelfPracticeMaterial(key)
  assert.deepEqual(plain(f.retainedSelfPracticeMaterials.value), {})
  await f.submitOnboardingApplication('selfPractice')
  assert.deepEqual(f.calls[0].body.removedMaterialKeys, ['个人申请书', '实习接收函'])
  assert.deepEqual(f.calls[0].body.materials, {})
})

test('删除旧附件后可同名上传，提交新文件且不回传脱敏占位符', async () => {
  const f = fixture()
  f.removeRetainedSelfPracticeMaterial('个人申请书')
  const file = { uid: 101, name: '个人申请书.pdf', raw: {} }
  f.selfPracticeOtherFileList.value.push(file)
  await f.handleSelfPracticeOtherFileChange(file)
  assert.equal(file.status, 'success')
  await f.submitOnboardingApplication('selfPractice')
  assert.deepEqual(f.calls[1].body.materials, { '个人申请书': 'private/replacement.pdf' })
  assert.deepEqual(f.calls[1].body.removedMaterialKeys, ['个人申请书'])
})

test('移除新上传的替换件不会恢复已删除的旧附件', async () => {
  const f = fixture()
  f.removeRetainedSelfPracticeMaterial('个人申请书')
  const file = { uid: 102, name: '个人申请书.pdf', raw: {} }
  f.selfPracticeOtherFileList.value.push(file)
  await f.handleSelfPracticeOtherFileChange(file)
  f.selfPracticeOtherFileList.value = []
  f.removeSelfPracticeOtherFile(file)
  await f.submitOnboardingApplication('selfPractice')
  assert.deepEqual(f.calls[1].body.materials, {})
  assert.deepEqual(f.calls[1].body.removedMaterialKeys, ['个人申请书'])
  assert.equal(f.retainedSelfPracticeMaterials.value['个人申请书'], undefined)
})

test('取消后重新加载恢复服务端附件，并清空删除标记', () => {
  const f = fixture()
  f.removeRetainedSelfPracticeMaterial('个人申请书')
  f.resetSelfPracticeForm(f.application)
  assert.deepEqual(plain(f.selfPracticeRemovedMaterialKeys.value), [])
  assert.deepEqual(plain(f.retainedSelfPracticeMaterials.value), f.application.materials)
  f.resetSelfPracticeForm()
  assert.deepEqual(plain(f.retainedSelfPracticeMaterials.value), {})
  assert.deepEqual(plain(f.selfPracticeRemovedMaterialKeys.value), [])
})

test('上传中的附件被移除后，迟到的响应不会写回申请', async () => {
  let finish
  const f = fixture(() => new Promise(resolve => { finish = resolve }))
  const file = { uid: 103, name: '补充资料.pdf', raw: {} }
  f.selfPracticeOtherFileList.value.push(file)
  const pending = f.handleSelfPracticeOtherFileChange(file)
  f.selfPracticeOtherFileList.value = []
  f.removeSelfPracticeOtherFile(file)
  finish({ code: 200, data: { url: 'private/removed.pdf' } })
  await pending
  assert.deepEqual(plain(f.selfPracticeForm.value.otherMaterials), {})
})

test('上传回调传入原始文件时，列表仍响应上传中和成功状态', async () => {
  let finish, visibleStatus
  const f = fixture(() => new Promise(resolve => { finish = resolve }), vueRef)
  const file = { uid: 105, name: '补充资料.pdf', raw: {}, status: 'ready' }
  f.selfPracticeOtherFileList.value.push(file)
  const stop = watchEffect(() => { visibleStatus = f.selfPracticeOtherFileList.value[0]?.status })
  try {
    const pending = f.handleSelfPracticeOtherFileChange(file)
    await nextTick()
    assert.equal(visibleStatus, 'uploading')
    finish({ code: 200, data: { url: 'private/new.pdf' } })
    await pending
    await nextTick()
    assert.equal(visibleStatus, 'success')
  } finally { stop() }
})

test('上传失败时阻止重提，移除失败项后允许保留其余附件并提交', async () => {
  const f = fixture(() => ({ code: 400, message: '文件上传失败' }))
  const file = { uid: 104, name: '补充资料.pdf', raw: {} }
  f.selfPracticeOtherFileList.value.push(file)
  await f.handleSelfPracticeOtherFileChange(file)
  await f.submitOnboardingApplication('selfPractice')
  assert.equal(f.calls.length, 1)
  f.selfPracticeOtherFileList.value = []
  f.removeSelfPracticeOtherFile(file)
  await f.submitOnboardingApplication('selfPractice')
  assert.equal(f.calls.length, 2)
  assert.deepEqual(f.calls[1].body.removedMaterialKeys, [])
})

test('提交中禁止继续删除原有附件', () => {
  const f = fixture()
  f.onboardingSubmitting.value = true
  f.removeRetainedSelfPracticeMaterial('个人申请书')
  assert.deepEqual(plain(f.selfPracticeRemovedMaterialKeys.value), [])
})
