import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { readInitialCredentials } from '../src/utils/initialCredentialPayload.ts'

test('only successful account credential entries are accepted for one-time display', () => {
  assert.deepEqual(readInitialCredentials({ initialCredentials: [
    { username: 'student-1', password: 'random-1' },
    { username: 'teacher-2', password: 'random-2' },
    { username: 'failed', password: '' },
    { username: '', password: 'random-3' }
  ] }), [
    { username: 'student-1', password: 'random-1' },
    { username: 'teacher-2', password: 'random-2' }
  ])
  assert.deepEqual(readInitialCredentials(null), [])
  assert.deepEqual(readInitialCredentials({ initialCredentials: 'not-an-array' }), [])
})

test('admin account reset flows use the approved fixed reset password', () => {
  for (const page of ['AdminUserManagement.vue', 'StudentUserManagement.vue', 'TeacherUserManagement.vue', 'CompanyManagement.vue']) {
    const source = readFileSync(new URL(`../src/views/admin/${page}`, import.meta.url), 'utf8')
    assert.match(source, /密码已重置为[:：]?\s*123456/, page)
    assert.doesNotMatch(source, /confirmButtonText: '我已记录'/, page)
  }
})

test('student, teacher, and company pages expose a bulk reset-all action', () => {
  const expected = {
    'StudentUserManagement.vue': { handler: 'resetAllStudentPasswords', apiFile: '../src/api/StudentUserService.ts', endpoint: '/admin/students/reset-password/all' },
    'TeacherUserManagement.vue': { handler: 'resetAllTeacherPasswords', apiFile: '../src/api/TeacherUserService.ts', endpoint: '/admin/teachers/reset-password/all' },
    'CompanyManagement.vue': { handler: 'resetAllCompanyPasswords', apiFile: '../src/api/company.ts', endpoint: '/admin/companies/reset-password/all' }
  }
  for (const [page, config] of Object.entries(expected)) {
    const source = readFileSync(new URL(`../src/views/admin/${page}`, import.meta.url), 'utf8')
    assert.match(source, /一键重置全部密码/, page)
    assert.match(source, new RegExp(config.handler), page)
    assert.doesNotMatch(source, /confirmButtonText: '我已记录'/, page)
    const apiSource = readFileSync(new URL(config.apiFile, import.meta.url), 'utf8')
    assert.match(apiSource, new RegExp(config.endpoint.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')), config.apiFile)
  }
})
