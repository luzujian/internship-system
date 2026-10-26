import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { delayFormFromApplication, delayFileListFromApplication } from '../src/utils/delayApplication.js'

function load(file, names, context) {
  let source = fs.readFileSync(new URL(file, import.meta.url), 'utf8')
  if (source.includes('<script setup')) source = source.match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1]
  const ast = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  const declarations = []
  function visit(node) {
    if (ts.isVariableDeclaration(node) && names.includes(node.name.getText(ast))) declarations.push('let ' + node.getText(ast) + ';')
    ts.forEachChild(node, visit)
  }
  visit(ast)
  assert.equal(declarations.length, names.length, names.join(','))
  const code = declarations.join('\n') + '\n globalThis.handlers = {' + names.join(',') + '}'
  vm.runInNewContext(ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, context)
  return context.handlers
}
const ref = value => ({ value })
const quiet = { success() {}, error() {}, warning() {} }

function delayFixture(post) {
  const calls = []
  const context = { ref, FormData: class { append() {} }, console,
    delayForm: ref({ reason: '准备考研', materialUrl: '' }), delayFileList: ref([]),
    delayApplication: ref(null), showDelayDialog: ref(true), onboardingSubmitting: ref(false),
    authStore: { user: { id: 1001, name: '验收学生' } }, ElMessage: quiet,
    delayFormFromApplication, delayFileListFromApplication,
    canResubmitDelay: status => ['rejected', 'withdrawn'].includes(status),
    request: { async post(url, body) { calls.push({ url, body }); return post?.(url, body) ?? { code: 200, data: { url: 'https://test.invalid/plan.pdf' } } },
      async put(url, body) { calls.push({ url, body }); return { code: 200 } } },
    formContext: { beginFormOperation: () => ({ complete() {} }) },
    scheduleProgressRefresh() {}, async loadDelayApplication() {}
  }
  const handlers = load('../src/views/student/Home.vue', ['retainedDelayPlan', 'delayUploadStatus', 'delayUploadSequence', 'getDelayValidation', 'resetDelayForm', 'handleDelayFileRemove', 'handleOnboardingFileChange', 'submitOnboardingApplication'], context)
  return { ...context, ...handlers, calls }
}

test('missing plan and whitespace reason never send an application', async () => {
  const f = delayFixture()
  await f.submitOnboardingApplication('delay')
  assert.equal(f.calls.length, 0)
  f.delayForm.value = { reason: ' ', materialUrl: 'https://test.invalid/plan.pdf' }
  await f.submitOnboardingApplication('delay')
  assert.equal(f.calls.length, 0)
})

test('upload in progress and failed uploads block submission', async () => {
  let finish
  const f = delayFixture(() => new Promise(resolve => { finish = resolve }))
  const pending = f.handleOnboardingFileChange({ raw: {}, uid: 1 }, 'delay')
  await f.submitOnboardingApplication('delay')
  assert.equal(f.calls.length, 1)
  assert.match(f.getDelayValidation().validationErrors[0], /正在上传/)
  finish({ code: 400, message: '格式不支持' }); await pending
  await f.submitOnboardingApplication('delay')
  assert.equal(f.calls.length, 1)
  assert.equal(f.delayUploadStatus.value, 'fail')
})

test('removed upload cannot write its late response back into the form', async () => {
  let finish
  const f = delayFixture(() => new Promise(resolve => { finish = resolve }))
  const file = { raw: {}, uid: 1 }
  const pending = f.handleOnboardingFileChange(file, 'delay')
  f.handleDelayFileRemove(file)
  finish({ code: 200, data: { url: 'https://test.invalid/removed.pdf' } }); await pending
  assert.equal(f.delayForm.value.materialUrl, '')
  assert.equal(f.delayFileList.value.length, 0)
  await f.submitOnboardingApplication('delay'); assert.equal(f.calls.length, 1)
})

test('only the latest replacement upload is accepted', async () => {
  const pending = []
  const f = delayFixture(() => new Promise(resolve => pending.push(resolve)))
  const first = f.handleOnboardingFileChange({ raw: {}, uid: 1 }, 'delay')
  const second = f.handleOnboardingFileChange({ raw: {}, uid: 2 }, 'delay')
  pending[1]({ code: 200, data: { url: 'https://test.invalid/latest.pdf' } }); await second
  pending[0]({ code: 200, data: { url: 'https://test.invalid/old.pdf' } }); await first
  assert.equal(f.delayForm.value.materialUrl, 'https://test.invalid/latest.pdf')
})

test('resubmission retains server material without returning its redacted filename as a URL', async () => {
  const f = delayFixture()
  f.delayApplication.value = { id: 7, status: 'withdrawn', reason: '准备考研', materials: { delayPlan: 'uploaded.pdf' } }
  f.resetDelayForm(f.delayApplication.value)
  assert.equal(f.getDelayValidation().missingFields.length, 0)
  await f.submitOnboardingApplication('delay')
  assert.equal(f.calls.length, 1); assert.equal(f.calls[0].url, '/student/applications/7/resubmit')
  assert.equal(Object.keys(f.calls[0].body.materials).length, 0)
  f.handleDelayFileRemove({ name: '原考研计划' })
  await f.submitOnboardingApplication('delay'); assert.equal(f.calls.length, 1)
})

test('failed replacement can be removed to retain original material', async () => {
  const f = delayFixture(() => ({ code: 400 }))
  f.delayApplication.value = { reason: '准备考研', materials: { delayPlan: 'uploaded.pdf' } }
  f.resetDelayForm(f.delayApplication.value)
  const file = { raw: {}, uid: 2 }
  await f.handleOnboardingFileChange(file, 'delay')
  assert.equal(f.getDelayValidation().validationErrors.length, 1)
  f.handleDelayFileRemove(file)
  assert.equal(f.getDelayValidation().missingFields.length, 0)
  assert.equal(f.getDelayValidation().validationErrors.length, 0)
})

test('multiple clicks send one application and release the submission state', async () => {
  let finish
  const f = delayFixture(() => new Promise(resolve => { finish = resolve }))
  f.delayForm.value.materialUrl = 'https://test.invalid/plan.pdf'
  const pending = f.submitOnboardingApplication('delay')
  await f.submitOnboardingApplication('delay'); await f.submitOnboardingApplication('delay')
  assert.equal(f.calls.length, 1)
  finish({ code: 200 }); await pending
  assert.equal(f.onboardingSubmitting.value, false)
})

function approvalFixture() {
  const calls = [], pending = []
  const request = (...args) => { calls.push(args); return new Promise((resolve, reject) => pending.push({ resolve, reject })) }
  const context = { ref, applications: ref([]), currentApplication: ref(null), rejectReason: ref('不同意'),
    showApplicationDetailModal: ref(true), showCompanyQualificationModal: ref(false), showRejectModal: ref(false),
    getCurrentUser: () => ({ id: 9 }), ElMessage: quiet, async fetchApplications() {}, async fetchStats() {},
    approvalApi: { approveStudentApplication: request, approveCompanyQualification: request, rejectStudentApplication: request, rejectCompanyQualification: request },
    formContext: { beginFormOperation: () => ({ complete() {} }) }
  }
  const handlers = load('../src/views/teacher/Approval.vue', ['reviewingApplications', 'applicationReviewKey', 'isApplicationReviewing', 'markApplicationReviewed', 'approveApplication', 'rejectApplication', 'confirmReject'], context)
  return { ...context, ...handlers, calls, pending }
}

test('list/detail triple approval and concurrent rejection share one in-flight guard', async () => {
  const f = approvalFixture(), row = { id: 130, type: 'delay', status: 'pending' }
  f.currentApplication.value = { ...row }; f.applications.value = [row]
  const first = f.approveApplication(row)
  await f.approveApplication(f.currentApplication.value); await f.approveApplication(row); await f.confirmReject()
  assert.equal(f.calls.length, 1)
  assert.equal(f.isApplicationReviewing(row), true)
  f.pending[0].resolve({ code: 200 }); await first
  assert.equal(row.status, 'approved'); assert.equal(f.currentApplication.value.status, 'approved')
  assert.equal(f.isApplicationReviewing(row), false)
})

test('failed approval permits retry, and separate application namespaces do not collide', async () => {
  const f = approvalFixture(), student = { id: 1, type: 'delay', status: 'pending' }
  const first = f.approveApplication(student)
  f.pending[0].reject(new Error('network')); await first
  assert.equal(student.status, 'pending'); assert.equal(f.isApplicationReviewing(student), false)
  const retry = f.approveApplication(student)
  const company = f.approveApplication({ id: 1, type: 'companyQualification', auditStatus: 0 })
  assert.equal(f.calls.length, 3)
  f.pending[1].resolve({ code: 200 }); f.pending[2].resolve({ code: 200 }); await Promise.all([retry, company])
})

test('rejection prevents duplicate rejection and approval for the same row', async () => {
  const f = approvalFixture(), row = { id: 3, type: 'delay', status: 'pending' }
  f.currentApplication.value = row
  const first = f.confirmReject()
  await f.confirmReject(); await f.approveApplication(row)
  assert.equal(f.calls.length, 1)
  f.pending[0].resolve({ code: 200 }); await first
  assert.equal(row.status, 'rejected'); assert.equal(f.isApplicationReviewing(row), false)
})
