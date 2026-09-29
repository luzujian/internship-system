import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

// 门禁：全系统表单覆盖清单是闭环的，未在清单出现的表单视为未完成。
// 清单唯一真相源：../agent/formCoverage.ts（SUBMIT_FORMS 已接 + EXEMPT_FILES 豁免）。

const agentDirectory = dirname(fileURLToPath(import.meta.url))
const srcDirectory = resolve(agentDirectory, '..')
const coverageSource = readFileSync(join(agentDirectory, 'formCoverage.ts'), 'utf8')

const submitBlock = coverageSource.slice(
  coverageSource.indexOf('export const SUBMIT_FORMS'),
  coverageSource.indexOf('export const EXEMPT_FILES')
)
const exemptBlock = coverageSource.slice(coverageSource.indexOf('export const EXEMPT_FILES'))

const submitIds = [...submitBlock.matchAll(/formId:\s*'([^']+)'/g)].map(m => m[1])
const submitFiles = [...submitBlock.matchAll(/file:\s*'([^']+)'/g)].map(m => m[1])
const exemptFiles = [...exemptBlock.matchAll(/file:\s*'([^']+)'/g)].map(m => m[1])

function collectVueFiles(directory) {
  const result = []
  for (const entry of readdirSync(directory)) {
    const full = join(directory, entry)
    if (statSync(full).isDirectory()) result.push(...collectVueFiles(full))
    else if (entry.endsWith('.vue')) result.push(full)
  }
  return result
}

const viewFiles = [
  ...collectVueFiles(join(srcDirectory, 'views')),
  ...collectVueFiles(join(srcDirectory, 'components'))
]

test('覆盖清单无重复 formId', () => {
  assert.ok(submitIds.length > 0, 'SUBMIT_FORMS 不应为空')
  assert.deepEqual([...new Set(submitIds)].length, submitIds.length, 'SUBMIT_FORMS 存在重复 formId')
})

test('每个已接表单的文件存在且包含对应 formId 注册', () => {
  const fileToIds = new Map()
  const idMatches = [...submitBlock.matchAll(/formId:\s*'([^']+)'/g)]
  const fileMatches = [...submitBlock.matchAll(/file:\s*'([^']+)'/g)]
  assert.equal(idMatches.length, fileMatches.length, 'formId 与 file 必须一一对应')
  idMatches.forEach((m, index) => {
    const list = fileToIds.get(fileMatches[index][1]) || []
    list.push(m[1])
    fileToIds.set(fileMatches[index][1], list)
  })
  for (const [file, ids] of fileToIds) {
    const full = join(srcDirectory, file)
    assert.ok(existsSync(full), `清单文件不存在: ${file}`)
    const source = readFileSync(full, 'utf8')
    for (const id of ids) assert.match(source, new RegExp(`formId:\\s*'${id}'`), `${file} 缺少 ${id} 注册`)
  }
})

test('代码里所有 formId 注册都在清单中（无漏登记）', () => {
  const registered = new Map()
  for (const full of viewFiles) {
    const source = readFileSync(full, 'utf8')
    for (const m of source.matchAll(/formId:\s*'([^']+)'/g)) {
      const list = registered.get(m[1]) || []
      list.push(full)
      registered.set(m[1], list)
    }
  }
  assert.ok(registered.size > 0, '未扫描到任何 formId 注册')
  for (const [id, files] of registered) {
    assert.ok(submitIds.includes(id), `formId 未登记到覆盖清单: ${id}（位于 ${files.join(', ')}）`)
  }
  assert.equal(registered.size, new Set(submitIds).size, '清单与代码注册数量不一致')
})

test('所有渲染 el-form 的文件都在已接或豁免清单中', () => {
  const listed = new Set([...submitFiles, ...exemptFiles])
  const unlisted = []
  for (const full of viewFiles) {
    const source = readFileSync(full, 'utf8')
    if (!/<el-form\b/.test(source)) continue
    const rel = relative(srcDirectory, full).replaceAll('\\', '/')
    if (!listed.has(rel)) unlisted.push(rel)
  }
  assert.deepEqual(unlisted, [], `发现未登记也未豁免的表单文件: ${unlisted.join(', ')}`)
})
test('豁免文件存在、无 formId 注册、原因非空', () => {
  assert.ok(exemptFiles.length > 0, 'EXEMPT_FILES 不应为空')
  assert.deepEqual([...new Set(exemptFiles)].length, exemptFiles.length, 'EXEMPT_FILES 存在重复文件')
  for (const file of exemptFiles) {
    const full = join(srcDirectory, file)
    assert.ok(existsSync(full), `豁免文件不存在: ${file}`)
    const source = readFileSync(full, 'utf8')
    assert.doesNotMatch(source, /formId:\s*'/, `${file} 含有 formId 注册，不可继续豁免`)
  }
  const reasons = [...exemptBlock.matchAll(/reason:\s*'([^']+)'/g)].map(m => m[1])
  assert.equal(reasons.length, exemptFiles.length, '每个豁免项必须写 reason')
  assert.ok(reasons.every(r => r.length > 0), '豁免原因不可为空')
})

test('已接表单总数与预期一致（增删需同步改本断言）', () => {
  assert.equal(new Set(submitIds).size, 57, '已接 formId 数量变化：请同步更新清单与本断言')
})
