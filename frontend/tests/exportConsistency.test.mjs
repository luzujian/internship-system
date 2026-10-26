import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import JSZip from 'jszip'
import * as XLSX from 'xlsx'

const sourceRoot = new URL('../src/', import.meta.url)
function loadFunction(path, name, bindings) {
  const source = readFileSync(new URL(path, sourceRoot), 'utf8').match(/<script[^>]*>([\s\S]*?)<\/script>/)[1]
  const tree = ts.createSourceFile('component.ts', source, ts.ScriptTarget.Latest, true)
  let declaration
  function visit(node) {
    if (ts.isVariableDeclaration(node) && node.name.getText(tree) === name) declaration = node
    ts.forEachChild(node, visit)
  }
  visit(tree)
  assert.ok(declaration, name)
  const code = ts.transpileModule(`const ${declaration.getText(tree)};`, {
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS }
  }).outputText
  return new vm.Script(`${code}\n${name}`).runInNewContext({
    console: { error() {}, warn() {} }, Date, URLSearchParams, Blob, ArrayBuffer,
    require: name => name === 'jszip' ? { default: JSZip } : null, ...bindings
  })
}

function browserBindings() {
  const messages = [], downloads = [], requests = []
  let blob
  const message = Object.assign(() => ({ close() {} }), ...['success', 'warning', 'error'].map(type => ({ [type]: text => messages.push({ type, text }) })))
  const document = {
    createElement: () => { const link = { click: () => downloads.push({ filename: link.download, blob }), remove() {}, setAttribute(key, value) { this[key] = value } }; return link },
    body: { appendChild() {}, removeChild() {} }
  }
  const url = { createObjectURL(value) { blob = value; return 'blob:export-test' }, revokeObjectURL() {} }
  return { messages, downloads, requests, ElMessage: message, document, URL: url, window: { URL: url },
    fetch: async (path, options) => { requests.push({ path, options }); return { ok: true, blob: async () => new Blob(['test']) } } }
}

test('teacher all export preserves every active filter, including numeric zero', async () => {
  const b = browserBindings()
  const run = loadFunction('views/teacher/TeacherStudentManagement.vue', 'exportAllData', {
    ...b, filters: { value: { search: '陈', class: '23信资17班', major: '信息资源管理', grade: '2023级', company: '自主单位', status: 0, internshipPath: 'selfPractice' } },
    getStatusValue: Number, getAuthToken: () => 'test-token'
  })
  await run()
  const query = new URL(b.requests[0].path, 'http://localhost').searchParams
  assert.deepEqual(Object.fromEntries(['name', 'className', 'major', 'grade', 'companyName', 'status', 'internshipPath'].map(k => [k, query.get(k)])), {
    name: '陈', className: '23信资17班', major: '信息资源管理', grade: '2023级', companyName: '自主单位', status: '0', internshipPath: 'selfPractice'
  })
  assert.equal(b.downloads.length, 1)
})

test('class export requests the complete filtered dataset rather than the displayed page', async () => {
  const b = browserBindings(), calls = []
  const run = loadFunction('views/admin/ClassManagement.vue', 'handleExportToExcel', {
    ...b, logger: console, tableData: { value: [{ name: '当前页' }] }, searchClass: { value: { name: '信资', grade: '2023', majorId: 2 } },
    request: { get: async (path, options) => { calls.push({ path, options }); return new Blob(['complete']) } }
  })
  await run()
  assert.equal(calls[0].path, '/classes/export')
  assert.deepEqual(JSON.parse(JSON.stringify(calls[0].options.params)), { name: '信资', grade: '2023', majorId: 2 })
  assert.equal(b.downloads[0].filename, '班级信息.xlsx')
})

test('administrator export keeps the search keyword and does not depend on the current page', async () => {
  const b = browserBindings(), calls = []
  const run = loadFunction('views/admin/AdminUserManagement.vue', 'handleExportToExcel', {
    ...b, logger: console, users: { value: [] }, searchQuery: { value: 'admin2' },
    AdminUserService: { exportAdmins: async keyword => { calls.push(keyword); return new Blob(['all']) } }
  })
  await run()
  assert.deepEqual(calls, ['admin2'])
  assert.equal(b.downloads.length, 1)
})

test('company export preserves audit filter and all selected tags', async () => {
  const b = browserBindings(), calls = []
  const run = loadFunction('views/admin/CompanyManagement.vue', 'handleExportToExcel', {
    ...b, logger: console, searchForm: { value: { companyName: '医院', status: 0, companyTag: ['接受兜底', '校友单位'] } },
    companyService: { exportCompanies: async params => { calls.push(params); return new Blob(['all']) } }
  })
  await run()
  assert.deepEqual(JSON.parse(JSON.stringify(calls[0])), { companyName: '医院', status: 0, companyTag: '接受兜底,校友单位' })
})

test('company application workbook keeps current positions, page filters and visible status wording', async () => {
  const b = browserBindings(); let saved
  const rows = [
    { positionName: '开发', studentName: '学生甲', studentNo: '001', status: 'approved', applyDate: '2026-01-01' },
    { positionName: '开发', studentName: '学生乙', studentNo: '002', status: 'interview_passed', directHire: true, applyDate: '2026-01-02' }
  ]
  const run = loadFunction('views/company/ApplicationView.vue', 'handleExport', {
    ...b, currentApplicationRows: { value: rows }, positionStore: { jobApplications: [...rows, { positionName: '已删除岗位', studentNo: '003' }] },
    searchForm: { value: { position: '开发', keyword: '学生' } }, exporting: { value: false }, exportMode: { value: 'excel' },
    jsonToSheet: async data => XLSX.utils.json_to_sheet(data), createWorkbook: async () => XLSX.utils.book_new(),
    appendSheet: async (book, sheet, name) => XLSX.utils.book_append_sheet(book, sheet, name), writeWorkbook: async book => { saved = XLSX.write(book, { type: 'buffer', bookType: 'xlsx' }) }
  })
  await run()
  const book = XLSX.read(saved), values = XLSX.utils.sheet_to_json(book.Sheets[book.SheetNames[0]])
  assert.equal(values.length, 2)
  assert.deepEqual(new Set(values.map(row => row['申请状态'])), new Set(['已同意', '已录取，待提交确认表']))
  assert.equal(values.find(row => row['学号'] === '001')['学生姓名'], '学生甲')
})

test('company confirmation workbook includes source, major and submission date', async () => {
  const b = browserBindings(); let data
  const run = loadFunction('views/company/InternshipConfirm.vue', 'handleExport', {
    ...b, filteredTableData: { value: [
      { positionName: '文字岗位', studentName: '甲', sourceApplicationId: 8, majorName: '信息资源管理', companyConfirmStatus: 0, createTime: '2026-01-01' },
      { positionName: '平台岗位', studentName: '乙', sourceApplicationId: null, majorName: '医学信息工程', companyConfirmStatus: 1, createTime: '2026-01-02' }
    ] }, exporting: { value: false }, formatDate: value => value, companyConfirmStatusMap: { 0: '待企业确认', 1: '企业已确认' },
    exportToExcel: async rows => { data = rows }
  })
  await run()
  assert.deepEqual(new Set(data.map(row => row['来源'])), new Set(['自主实习申请', '平台录取']))
  assert.equal(data.find(row => row['学生姓名'] === '甲')['专业'], '信息资源管理')
  assert.equal(data.find(row => row['学生姓名'] === '甲')['确认表提交日期'], '2026-01-01')
})

test('ZIP download reports failed attachments inside the actual archive and warns instead of success', async () => {
  const b = browserBindings()
  const run = loadFunction('views/company/ApplicationView.vue', 'exportWithFiles', {
    ...b, writeWorkbookToBuffer: async () => new Uint8Array([80, 75]),
    studentArchiveService: { getStudentArchives: async () => ({ data: [{ id: 1, fileName: '简历.pdf' }] }), downloadArchive: async () => { throw new Error('unavailable') } }
  })
  await run({}, '名单', [{ studentId: 3, studentName: '学生甲' }])
  assert.equal(b.messages.at(-1).type, 'warning')
  const zip = await JSZip.loadAsync(await b.downloads[0].blob.arrayBuffer())
  assert.match(await zip.file('_导出失败清单.txt').async('string'), /学生甲\/简历.pdf：文件下载失败/)
  assert.ok(zip.file('名单.xlsx'))
})

test('company PDF loads detail before rendering and includes alumni absent from the table row', async () => {
  const b = browserBindings(); let html = '', saved = ''
  const doc = { open() {}, close() {}, write(value) { html = value }, body: { firstElementChild: { scrollHeight: 100 } } }
  const document = { ...b.document, createElement: type => type === 'iframe' ? { setAttribute() {}, style: {}, contentDocument: doc, remove() {} }
    : { getContext: () => ({ fillRect() {}, drawImage() {} }), toDataURL: () => 'data:image/jpeg;base64,AA==' } }
  const run = loadFunction('views/admin/CompanyManagement.vue', 'downloadMaterials', {
    ...b, document, setTimeout: fn => fn(), escapePdfText: value => value,
    request: { get: async path => { assert.equal(path, '/admin/companies/7'); return { code: 200, data: { id: 7, companyName: '医院', industry: 'hospital', scale: '1000+', alumniMembers: [{ name: '校友甲', phone: '13800000000', position: '医师' }] } } } },
    formatScale: loadFunction('views/admin/CompanyManagement.vue', 'formatScale', {}),
    html2canvas: async () => ({ width: 794, height: 100 }), jsPDF: class { addPage() {} addImage() {} save(name) { saved = name } }
  })
  await run({ id: 7, companyName: '列表行', alumniMembers: [] })
  assert.match(html, /校友甲/)
  assert.match(html, /医师/)
  assert.doesNotMatch(html, />hospital</)
  assert.match(html, /1000 人以上/)
  assert.equal(saved, '医院_企业注册审核信息.pdf')
})
