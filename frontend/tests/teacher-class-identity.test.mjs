import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'

const component = readFileSync(new URL('../src/views/teacher/TeacherClassManagement.vue', import.meta.url), 'utf8')
const script = component.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const output = ts.transpileModule(script + '\nglobalThis.page = {handleAssignClass,confirmAssign,selectedClassIds,assignDialogVisible,classOptionsError};', {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
}).outputText

function mount(user, claimFails = false) {
  const requests = []
  const errors = []
  const context = {
    exports: {}, console,
    localStorage: { getItem(key) {
      return { current_role: 'ROLE_ADMIN', teacherId: '999' }[key] ?? null
    } },
    require(id) {
      if (id === 'vue') return {
        ref: value => ({ value }), computed: getter => ({ get value() { return getter() } }),
        onMounted: fn => fn()
      }
      if (id === 'vue-router') return { useRouter: () => ({}) }
      if (id === '@/store/auth') return { useAuthStore: () => ({ user, role: 'ROLE_TEACHER_COUNSELOR' }) }
      if (id === 'element-plus') return { ElMessage: { error: text => errors.push(text), success() {}, warning() {} } }
      if (id === '@/api/teacherClass') return {
        getTeacherClasses: async id => { requests.push(['classes', id]); return { data: [] } },
        getAllClasses: async () => ({ data: [] }),
        getClaimableClasses: async () => { requests.push(['claimable']); return { data: [{ id: 11, name: 'Class 11' }] } },
        claimClasses: async ids => { requests.push(['claim', ...ids]); if (claimFails) throw new Error('occupied'); return { data: 1 } },
        getCounselorStatistics: async id => { requests.push(['statistics', id]); return { data: {} } }
      }
      return {}
    }
  }
  vm.runInNewContext(output, context)
  return { requests, errors, page: context.page }
}

test('class and statistics requests use this tab identity despite stale or other-role local storage', () => {
  assert.deepEqual(mount({ id: '49' }).requests, [['classes', 49], ['statistics', 49]])
})

test('claim dialog refreshes eligible classes and claims without a client-selected counselor', async () => {
  const { page, requests } = mount({ id: '49' })
  page.handleAssignClass()
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(requests.at(-1)[0], 'claimable')
  page.selectedClassIds.value = [11]
  await page.confirmAssign()
  assert.ok(requests.some(r => r[0] === 'claim' && r[1] === 11 && r.length === 2))
  assert.equal(page.assignDialogVisible.value, false)
  assert.deepEqual(requests.slice(-2), [['classes',49],['statistics',49]])
})

test('conflicting claim clears selection and reloads available choices', async () => {
  const { page, requests, errors } = mount({ id: '49' }, true)
  page.selectedClassIds.value = [11]
  await page.confirmAssign()
  assert.equal(page.selectedClassIds.value.length,0)
  assert.equal(requests.at(-1)[0],'claimable')
  assert.equal(errors.length,1)
})

test('missing or invalid identity never falls back to another teacher account', () => {
  for (const user of [null, { id: 'invalid' }, { id: '0' }]) {
    const result = mount(user)
    assert.deepEqual(result.requests, [])
    assert.equal(result.errors.length, 1)
  }
})
