import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import ts from 'typescript'
import vm from 'node:vm'

const source = readFileSync(new URL('../src/utils/uploadErrorMessage.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText
const { getUploadErrorMessage } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)

test('413 from direct upload and Axios explains the size limit without displaying HTML', () => {
  const direct = getUploadErrorMessage({ status: 413, message: '<html>413 Request Entity Too Large</html>' })
  assert.match(direct, /超过服务器允许的上限/)
  assert.match(direct, /压缩文件或分批上传/)
  assert.doesNotMatch(direct, /<html>/)
  assert.equal(getUploadErrorMessage({ response: { status: 413 } }), direct)
})

test('authentication, permission and network failures have actionable messages', () => {
  assert.match(getUploadErrorMessage({ status: 401 }), /重新登录/)
  assert.match(getUploadErrorMessage({ status: 403 }), /没有上传权限/)
  assert.match(getUploadErrorMessage({ status: 0 }), /网络连接/)
  assert.match(getUploadErrorMessage({ status: 500 }), /服务器暂时无法处理/)
})

test('dashboard resource selection accepts 10 MB and clears files above the limit', () => {
  const dashboard = readFileSync(new URL('../src/views/admin/Dashboard.vue', import.meta.url), 'utf8')
  const handler = dashboard.slice(dashboard.indexOf('const handleResourceFileChange ='), dashboard.indexOf('const handleResourceFileRemove ='))
  const messages = []
  const context = { ElMessage: { error: message => messages.push(message) }, resourceDocumentForm: { value: { file: null } }, resourceFileList: { value: [] } }
  vm.createContext(context)
  vm.runInContext(`${handler}\nthis.selectFile = handleResourceFileChange`, context)
  const valid = { raw: { size: 10 * 1024 * 1024 } }
  context.selectFile(valid)
  assert.equal(context.resourceDocumentForm.value.file, valid.raw)
  context.selectFile({ raw: { size: 10 * 1024 * 1024 + 1 } })
  assert.equal(context.resourceDocumentForm.value.file, null)
  assert.equal(context.resourceFileList.value.length, 0)
  assert.match(messages[0], /10MB/)
})
