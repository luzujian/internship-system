import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { ref, computed } from 'vue'
import { parse } from '@vue/compiler-sfc'

const source = readFileSync(new URL('../src/views/teacher/Approval.vue', import.meta.url), 'utf8')
const ast = ts.createSourceFile('Approval.ts', parse(source).descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const names = ['batchApproving', 'batchApprovalCompleted', 'batchApprovalTotal', 'canBatchApprove',
  'reviewingApplications', 'applicationReviewKey', 'isApplicationReviewing', 'markApplicationReviewed',
  'getApplicationTypeText', 'approveAllApplications', 'approveApplication', 'rejectApplication', 'confirmReject']
const declarations = ast.statements.filter(ts.isVariableStatement)
  .flatMap(statement => [...statement.declarationList.declarations])
  .filter(declaration => names.includes(declaration.name.getText(ast)))
const code = ts.transpileModule(declarations.map(declaration => `const ${declaration.getText(ast)}`).join('\n'), {
  compilerOptions: { target: ts.ScriptTarget.ES2022 }
}).outputText

const application = (id, overrides = {}) => ({ id, applicationType: 'selfPractice', studentName: `学生${id}`,
  status: 'pending', reviewTarget: 'COUNSELOR', ...overrides })

function fixture(options = {}) {
  const reads = [], writes = [], confirmations = [], messages = [], refreshes = []
  const scope = {
    ref, computed, Map, Number, approvalPageActive: true,
    activeTab: ref(options.type || 'selfPractice'), loading: ref(false), currentPage: ref(3),
    applications: ref([]), currentApplication: ref(null), rejectReason: ref('资料不完整'),
    showApplicationDetailModal: ref(false), showCompanyQualificationModal: ref(false), showRejectModal: ref(false),
    searchForm: { keyword: '', status: '', majorId: undefined, classId: undefined, ...options.filters },
    majorOptions: ref([{ id: 7, name: '软件工程' }]), classOptions: ref([{ id: 8, name: '软件一班' }]),
    authStore: { user: { id: options.reviewerId ?? 9 } }, getCurrentUser: () => ({ id: options.reviewerId ?? 9 }),
    ElMessage: Object.fromEntries(['success', 'warning', 'info', 'error'].map(level => [level, text => messages.push({ level, text })])),
    ElMessageBox: { confirm: async (...args) => { confirmations.push(args); return options.confirm?.() } },
    fetchApplications: async () => { refreshes.push('list') }, fetchStats: async () => { refreshes.push('stats') },
    approvalApi: {
      getStudentApplications: async params => { reads.push({ ...params }); return options.list ? options.list(params) : { total: 1, rows: [application(1)] } },
      approveStudentApplication: async (id, reviewerId) => { writes.push({ id, reviewerId }); return options.approve ? options.approve(id) : { code: 200 } }
    }
  }
  const state = vm.runInNewContext(`${code}\n({${names.join(',')}})`, scope)
  return { ...scope, ...state, scope, reads, writes, confirmations, messages, refreshes }
}

test('一键通过先收集全部分页，确认范围和数量后才审批，保留筛选与审核人', async () => {
  let confirm
  const f = fixture({ filters: { keyword: '张', majorId: 7, classId: 8 },
    list: ({ page }) => ({ total: 102, rows: page === 1
      ? Array.from({ length: 100 }, (_, i) => application(i + 1)) : [application(101), application(102)] }),
    confirm: () => new Promise(resolve => { confirm = resolve }) })
  f.applications.value = [{ ...application(1), type: 'selfPractice' }]
  const pending = f.approveAllApplications()
  while (!confirm) await Promise.resolve()
  assert.equal(f.reads.length, 2)
  assert.equal(f.writes.length, 0)
  assert.equal(f.batchApproving.value, true)
  for (const params of f.reads) {
    assert.equal(params.status, 'pending')
    assert.equal(params.applicationType, 'selfPractice')
    assert.equal(params.studentName, '张')
    assert.equal(params.studentUserId, '张')
    assert.equal(params.majorId, 7)
    assert.equal(params.classId, 8)
  }
  const [message, title, config] = f.confirmations[0]
  assert.equal(title, '一键通过确认')
  assert.match(message, /102 条自主实习申请/)
  assert.match(message, /所有分页/)
  assert.match(message, /关键词：张；专业：软件工程；班级：软件一班/)
  assert.equal(config.confirmButtonText, '确认全部通过')
  assert.equal(config.closeOnClickModal, false)
  confirm()
  await pending
  assert.equal(f.writes.length, 102)
  assert.equal(new Set(f.writes.map(item => item.id)).size, 102)
  assert.ok(f.writes.every(item => item.reviewerId === 9))
  assert.equal(f.applications.value[0].status, 'approved')
  assert.equal(f.currentPage.value, 1)
  assert.deepEqual(f.refreshes, ['list', 'stats'])
  assert.deepEqual(f.messages, [{ level: 'success', text: '已成功通过 102 条自主实习申请' }])
  assert.equal(f.batchApproving.value, false)
})

test('取消或关闭确认弹窗均不审批，也不刷新列表', async () => {
  for (const action of ['cancel', 'close']) {
    const f = fixture({ confirm: () => Promise.reject(action) })
    await f.approveAllApplications()
    assert.deepEqual(f.writes, [])
    assert.deepEqual(f.refreshes, [])
    assert.deepEqual(f.messages, [])
    assert.equal(f.batchApproving.value, false)
  }
})

test('只处理本类待审核申请，排除企业确认、已审核、撤回及重复记录', async () => {
  const f = fixture({ list: () => ({ total: 7, rows: [application(1), application(1),
    application(2, { reviewTarget: 'COMPANY' }), application(3, { status: 'approved' }),
    application(4, { status: 'withdrawn' }), application(5, { applicationType: 'delay' }), application(6)] }) })
  await f.approveAllApplications()
  assert.deepEqual(f.writes.map(item => item.id), [1, 6])
  assert.match(f.confirmations[0][0], /2 条自主实习申请/)
})

test('考研延迟支持一键通过，部分失败时继续处理并显示真实结果', async () => {
  const f = fixture({ type: 'delay', list: () => ({ total: 3, rows: [1, 2, 3].map(id => application(id, { applicationType: 'delay' })) }),
    approve: id => { if (id === 2) throw new Error('该申请已被其他审核人处理'); return id === 3 ? { code: 400 } : { code: 200 } } })
  await f.approveAllApplications()
  assert.equal(f.writes.length, 3)
  assert.match(f.confirmations[0][0], /3 条考研延迟申请/)
  assert.deepEqual(f.messages, [{ level: 'warning', text: '已通过 1 条，2 条未通过，请核对剩余申请后重试' }])
  assert.deepEqual(f.refreshes, ['list', 'stats'])
  assert.equal(f.batchApproving.value, false)
})

test('加载跨页失败或无待审核申请时不出现确认弹窗且不审批', async () => {
  const empty = fixture({ list: () => ({ total: 0, rows: [] }) })
  await empty.approveAllApplications()
  assert.equal(empty.confirmations.length, 0)
  assert.deepEqual(empty.writes, [])
  assert.equal(empty.messages[0].level, 'info')
  const failed = fixture({ list: ({ page }) => {
    if (page === 2) throw new Error('网络错误')
    return { total: 101, rows: Array.from({ length: 100 }, (_, i) => application(i + 1)) }
  } })
  await failed.approveAllApplications()
  assert.equal(failed.confirmations.length, 0)
  assert.deepEqual(failed.writes, [])
  assert.equal(failed.messages[0].level, 'error')
  assert.equal(failed.batchApproving.value, false)
})

test('已通过、已驳回和企业审核页不能触发批量，身份无效时不提交', async () => {
  for (const options of [{ filters: { status: 'approved' } }, { filters: { status: 'rejected' } }, { type: 'companyQualification' }, { reviewerId: 0 }]) {
    const f = fixture(options)
    await f.approveAllApplications()
    assert.deepEqual(f.reads, [])
    assert.deepEqual(f.writes, [])
  }
})

test('批量处理中重复点击及单条通过、驳回不发送额外请求，进度随结果更新', async () => {
  let finish
  const f = fixture({ approve: () => new Promise(resolve => { finish = resolve }) })
  const row = { ...application(1), type: 'selfPractice' }
  f.currentApplication.value = row
  const first = f.approveAllApplications()
  while (!finish) await Promise.resolve()
  await f.approveAllApplications()
  await f.approveApplication(row)
  f.rejectApplication(row)
  await f.confirmReject()
  assert.equal(f.writes.length, 1)
  assert.equal(f.reads.length, 1)
  assert.equal(f.showRejectModal.value, false)
  assert.equal(f.batchApprovalCompleted.value, 0)
  assert.equal(f.batchApprovalTotal.value, 1)
  finish({ code: 200 })
  await first
  assert.equal(f.batchApproving.value, false)
  assert.equal(f.batchApprovalTotal.value, 0)
})

test('离开页面后停止后续批量请求', async () => {
  let finish
  const f = fixture({ list: () => ({ total: 2, rows: [application(1), application(2)] }),
    approve: () => new Promise(resolve => { finish = resolve }) })
  const pending = f.approveAllApplications()
  while (!finish) await Promise.resolve()
  f.scope.approvalPageActive = false
  finish({ code: 200 })
  await pending
  assert.equal(f.writes.length, 1)
  assert.deepEqual(f.refreshes, [])
  assert.deepEqual(f.messages, [])
})
