import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

const apiSrc = readFileSync(new URL('../src/api/automationV2.ts', import.meta.url), 'utf8')
const apiJs = ts.transpileModule(
  apiSrc.replace(/^import request from '@\/utils\/request'\s*$/m, ''),
  { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }
).outputText

function loadApi(request) {
  const context = { request, exports: {} }
  vm.runInNewContext(apiJs, context)
  return context.exports.automationApi
}

test('草稿预览撞版本冲突时，自动取最新版本号重试一次', async () => {
  const posts = []
  let round = 0
  const conflict = () => { const e = new Error('任务或草稿已变化，请重新核对；当前内容仍保留。'); e.response = { status: 409 }; return e }
  const request = {
    post: async (url, body) => {
      posts.push({ url, revision: body.revision })
      round += 1
      if (round === 1) throw conflict()
      return { data: { actionId: 'action-1' } }
    },
    get: async () => ({ data: [{ id: 'draft-1', revision: 7 }] })
  }
  const result = await loadApi(request).draft({ draftId: 'draft-1', revision: 3, sessionId: 's1', message: 'x' })
  assert.equal(result.data.actionId, 'action-1')
  assert.deepEqual(posts.map(p => p.revision), [3, 7], '第二次必须带最新版本号')
})

test('不是版本冲突就不重试', async () => {
  let posts = 0
  const request = {
    post: async () => { posts += 1; const e = new Error('服务器错误'); e.response = { status: 500 }; throw e },
    get: async () => { throw new Error('不该去请求草稿列表') }
  }
  await assert.rejects(() => loadApi(request).draft({ draftId: 'draft-1', revision: 3, sessionId: 's1' }))
  assert.equal(posts, 1)
})

test('草稿已关闭或不属于当前会话时不重试（重试也修不好）', async () => {
  let posts = 0
  const request = {
    post: async () => { posts += 1; const e = new Error('该草稿已经确认或已关闭，请重新发起任务。'); e.response = { status: 409 }; throw e },
    get: async () => ({ data: [] })
  }
  await assert.rejects(() => loadApi(request).draft({ draftId: 'gone', revision: 1, sessionId: 's1' }), /已关闭/)
  assert.equal(posts, 1, '列表里找不到就保留原始错误')
})

test('新建草稿（没有 draftId）不重试', async () => {
  let posts = 0
  const request = {
    post: async () => { posts += 1; const e = new Error('冲突'); e.response = { status: 409 }; throw e },
    get: async () => { throw new Error('不该去请求草稿列表') }
  }
  await assert.rejects(() => loadApi(request).draft({ message: 'new' }))
  assert.equal(posts, 1)
})

test('预览冲突不再弹全局提示，改为在任务卡片就地显示', () => {
  assert.match(apiSrc, /skipErrorMessage: true/)
})

test('恢复草稿加了防重入，避免同一草稿被并发刷新', () => {
  const storeSrc = readFileSync(new URL('../src/agent/store.ts', import.meta.url), 'utf8')
  const start = storeSrc.indexOf('async function restoreAutoDraft()')
  assert.ok(start > 0, '找不到 restoreAutoDraft')
  const end = storeSrc.indexOf('watch([sessionId', start)
  const body = storeSrc.slice(start, end > start ? end : start + 2000)
  assert.match(body, /if \(restoreAutoInFlight\) return/)
  assert.match(body, /finally \{ restoreAutoInFlight = false \}/)
})
