import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse } from '@vue/compiler-sfc'
import { ref, computed, nextTick, watch } from 'vue'

// Execute the actual component handlers with in-memory APIs. No backend or database access.
const { descriptor } = parse(readFileSync(new URL('../src/views/admin/LogManagement.vue', import.meta.url), 'utf8'))
const ast = ts.createSourceFile('LogManagement.vue', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const declarations = ast.statements.filter(ts.isVariableStatement).flatMap(statement => [...statement.declarationList.declarations])
const noop = () => {}
const plain = value => JSON.parse(JSON.stringify(value))
const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function install(scope, ...names) {
  const context = vm.isContext(scope) ? scope : vm.createContext(scope)
  for (const name of names) {
    const declaration = declarations.find(node => node.name.getText(ast) === name)
    assert.ok(declaration, `Missing production declaration ${name}`)
    const source = `globalThis.${name} = ${declaration.initializer.getText(ast)}`
    vm.runInContext(ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, context)
  }
  return context
}
function fixture(extra = {}) {
  return {
    ref, computed, watch, Date, Error, URL, console,
    logger: { error: noop }, ElMessage: { warning: noop, error: noop, success: noop },
    activeTab: ref('operations'), visibleTabs: ref([{ name: 'operations' }, { name: 'login-events' }, { name: 'security-events' }]),
    tabPermissionMap: { operations: 'log:view', 'login-events': 'log:login:view', 'security-events': 'log:security:view' },
    hasPermission: () => true,
    loading: ref(false), listError: ref(''), lastUpdated: ref(''), listRequestVersion: 0,
    operateTableData: ref([]), loginTableData: ref([]), securityTableData: ref([]),
    appliedParams: ref({ startTime: '2026-10-01 00:00:00', endTime: '2026-10-02 12:00:00', operatorName: 'saved' }),
    pagination: { currentPage: 2, pageSize: 20, total: 45 }, sortOrder: ref('desc'),
    formatDate: () => '2026-10-02 12:00:00',
    defaultTimeRange: () => ['2026-09-25 12:00:00', '2026-10-02 12:00:00'],
    ...extra
  }
}

test('请求中保留总数和当前页，翻页只使用已应用条件', async () => {
  const response = deferred(), requests = []
  const scope = install(fixture({ getOperationsApi: params => { requests.push(plain(params)); return response.promise } }), 'clearRows', 'queryPage')
  const pending = scope.queryPage()
  assert.equal(scope.pagination.total, 45, 'setting total to zero makes Element Plus clamp the current page back to one')
  assert.equal(scope.pagination.currentPage, 2)
  assert.equal(scope.loading.value, true)
  assert.equal(requests[0].page, 2)
  assert.equal(requests[0].operatorName, 'saved')
  response.resolve({ code: 200, data: { rows: [{ id: 21 }], total: 45 } })
  await pending
  assert.equal(scope.loading.value, false)
  assert.equal(scope.operateTableData.value[0].id, 21)
})

test('过期标签响应和异常不得覆盖当前标签或提前取消加载态', async () => {
  const oldRequest = deferred(), currentRequest = deferred()
  const scope = install(fixture({ getOperationsApi: () => oldRequest.promise, getLoginEventsApi: () => currentRequest.promise }), 'clearRows', 'queryPage')
  const oldPending = scope.queryPage()
  scope.activeTab.value = 'login-events'
  const currentPending = scope.queryPage()
  oldRequest.reject(new Error('old request failed'))
  await oldPending
  assert.equal(scope.loading.value, true)
  assert.equal(scope.listError.value, '')
  currentRequest.resolve({ code: 200, data: { rows: [{ id: 201, userName: 'login-user' }], total: 1 } })
  await currentPending
  assert.equal(scope.loginTableData.value[0].id, 201)
  assert.equal(scope.operateTableData.value.length, 0)
  assert.equal(scope.pagination.total, 1)
  assert.equal(scope.loading.value, false)
})

test('切换标签忽略无效草稿，沿用已应用公共筛选并清除专属条件', () => {
  let queries = 0
  const scope = install(fixture({ queryPage: () => { queries++ } }), 'emptyFilters')
  scope.searchForm = scope.emptyFilters()
  Object.assign(scope.appliedParams.value, { module: 'INTERNSHIP_MANAGEMENT', action: 'AUDIT', targetId: '77', result: 'ATTEMPTED', requestId: 'request-77' })
  Object.assign(scope.searchForm, { timeRange: ['invalid', 'invalid'], operatorName: 'unsaved' })
  install(scope, 'buildParams', 'handleSearch', 'onTabChange')
  scope.onTabChange('login-events')
  assert.equal(queries, 1)
  assert.equal(scope.pagination.currentPage, 1)
  assert.equal(scope.appliedParams.value.operatorName, 'saved')
  assert.equal(scope.appliedParams.value.requestId, 'request-77')
  assert.equal(scope.appliedParams.value.startTime, '2026-10-01 00:00:00')
  for (const field of ['module', 'action', 'targetId', 'result']) assert.equal(scope.appliedParams.value[field], undefined)
})

test('导出使用已应用筛选且类别在等待下载期间保持不变', async () => {
  const response = deferred(), sent = [], links = []
  const scope = install(fixture({
    exportLoading: ref(false),
    exportLogsApi: data => { sent.push(plain(data)); return response.promise },
    document: { createElement: () => { const link = { click: noop }; links.push(link); return link } },
    URL: { createObjectURL: () => 'blob:test', revokeObjectURL: noop },
    searchForm: { operatorName: 'unsaved' }
  }), 'handleExport')
  const pending = scope.handleExport()
  scope.activeTab.value = 'security-events'
  response.resolve({ type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  await pending
  assert.equal(sent[0].operatorName, 'saved')
  assert.equal(sent[0].category, 'operations')
  assert.equal(sent[0].page, undefined)
  assert.equal(sent[0].pageSize, undefined)
  assert.match(links[0].download, /operations/)
})

function retentionFixture(extra = {}) {
  const scope = install(fixture({
    retentionForm: ref({ highRiskDays: 365, normalDays: 180, loginDays: 180 }),
    savedRetention: ref('[365,180,180]'), retentionLoaded: ref(true), retentionFetching: ref(false),
    retentionLoading: ref(false), retentionRunning: ref(false), retentionPreviewLoading: ref(false), integrityVerifying: ref(false),
    retentionPreviewData: ref(null), retentionReason: ref(''), ...extra
  }), 'policySnapshot', 'retentionDirty', 'retentionInvalid', 'retentionBusy', 'previewRetention')
  const previewWatcher = ast.statements.find(node => ts.isExpressionStatement(node) && ts.isCallExpression(node.expression)
    && node.expression.expression.getText(ast) === 'watch' && node.expression.arguments[0]?.getText(ast).includes('policySnapshot'))
  assert.ok(previewWatcher, 'production watcher must invalidate previews on policy edits')
  vm.runInContext(ts.transpileModule(previewWatcher.getText(ast), { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText, scope)
  return scope
}

test('未保存策略不能预览，过期预览响应丢弃，编辑后已有预览失效', async () => {
  const response = deferred(); let requests = 0
  const scope = retentionFixture({ previewRetentionApi: () => { requests++; return response.promise } })
  scope.retentionForm.value.highRiskDays = 400
  await scope.previewRetention()
  assert.equal(requests, 0)
  scope.savedRetention.value = scope.policySnapshot()
  const pending = scope.previewRetention()
  assert.equal(requests, 1)
  scope.retentionForm.value.highRiskDays = 500
  response.resolve({ code: 200, data: { operateLogNormal: 4 } })
  await pending
  assert.equal(scope.retentionPreviewData.value, null)
  scope.retentionPreviewData.value = { operateLogNormal: 4 }
  scope.retentionForm.value.normalDays = 190
  await nextTick()
  assert.equal(scope.retentionPreviewData.value, null)
})

test('保留天数边界及高风险期限关系由真实校验阻止保存', async () => {
  let writes = 0
  const scope = retentionFixture({ updateRetentionPoliciesApi: async () => { writes++; return { code: 200 } } })
  install(scope, 'saveRetentionPolicies')
  for (const highRiskDays of [29, 3651, 90, 365.5, NaN]) {
    scope.retentionForm.value.highRiskDays = highRiskDays
    assert.equal(scope.retentionInvalid.value, true)
    await scope.saveRetentionPolicies()
  }
  assert.equal(writes, 0)
  scope.retentionForm.value.highRiskDays = 365
  assert.equal(scope.retentionInvalid.value, false)
})

test('完整性校验进行时不允许保存、预览或确认清理', async () => {
  let calls = 0
  const scope = retentionFixture({ integrityVerifying: ref(true), retentionPreviewData: ref({ operateLogNormal: 4 }),
    retentionReason: ref('到期清理'), retentionPreviewTotal: ref(4),
    ElMessageBox: { confirm: async () => { calls++ } },
    formContext: { beginFormOperation: () => ({ complete: noop }) },
    updateRetentionPoliciesApi: async () => { calls++; return { code: 500 } },
    previewRetentionApi: async () => { calls++; return { code: 500 } },
    runRetentionApi: async () => { calls++; return { code: 500 } }
  })
  install(scope, 'saveRetentionPolicies', 'executeRetention')
  await scope.saveRetentionPolicies()
  await scope.previewRetention()
  await scope.executeRetention()
  assert.equal(calls, 0, 'verification must not race policy changes or deletion')
})

test('刷新滚动时间范围会推进终点，同时保留未应用的筛选草稿', () => {
  let queries = 0, summaries = 0
  const scope = install(fixture({ liveRangeDays: ref(7), timeRangeFor: () => ['2026-09-25 13:00:00', '2026-10-02 13:00:00'],
    searchForm: { timeRange: ['2026-10-01 00:00:00', '2026-10-02 12:00:00'], operatorName: 'unsaved' },
    queryPage: () => { queries++ }, loadSummary: () => { summaries++ }
  }), 'refreshPage')
  scope.refreshPage()
  assert.equal(scope.appliedParams.value.endTime, '2026-10-02 13:00:00')
  assert.equal(scope.searchForm.timeRange[1], '2026-10-02 13:00:00')
  assert.equal(scope.appliedParams.value.operatorName, 'saved')
  assert.equal(scope.searchForm.operatorName, 'unsaved')
  assert.equal(queries, 1)
  assert.equal(summaries, 1)
  scope.liveRangeDays.value = null
  scope.appliedParams.value.endTime = '2026-10-01 10:00:00'
  scope.refreshPage()
  assert.equal(scope.appliedParams.value.endTime, '2026-10-01 10:00:00', 'custom time windows remain fixed')
})
