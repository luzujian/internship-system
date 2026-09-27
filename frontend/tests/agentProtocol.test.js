import test from 'node:test'
import assert from 'node:assert/strict'
import { sseParser, businessTarget, localReceiptTarget, shouldDock, terminal, answerParts, jobSalary, panelSize, responseOperation, taskVersions } from '../src/agent/protocol.mjs'

test('SSE preserves Chinese characters, chunk boundaries, CRLF and multiple events', () => {
  const events = []; const parse = sseParser((event, data) => events.push({ event, data }))
  const bytes = new TextEncoder().encode('id: 1\r\nevent: snapshot\r\ndata: {"answer":"已核对学生资料","version":1}\r\n\r\nevent: end\ndata: {"taskId":"x"}\n\n')
  const decoder = new TextDecoder()
  for (let i = 0; i < bytes.length; i += 3) parse(decoder.decode(bytes.slice(i, i + 3), { stream: true }))
  parse(decoder.decode())
  assert.equal(events[0].data.answer, '已核对学生资料'); assert.equal(events[1].event, 'end'); assert.equal(events.length, 2)
})
test('only allowlisted role-specific links navigate', () => {
  assert.equal(businessTarget({ action: 'OPEN_JOB', objectId: '5' }, 'ROLE_COMPANY'), null)
  assert.equal(businessTarget({ action: 'OPEN_JOB', objectId: 'javascript:alert(1)' }, 'ROLE_STUDENT'), null)
  assert.equal(businessTarget({ action: 'https://evil.example' }, 'ROLE_STUDENT'), null)
  assert.deepEqual(businessTarget({ action: 'OPEN_JOB', objectId: '5' }, 'ROLE_STUDENT'), { path: '/student/jobs', query: { positionId: '5' } })
  assert.equal(businessTarget({ action: 'OPEN_CONFIRMATION' }, 'ROLE_STUDENT').path, '/student/internship-confirmation-form')
  assert.equal(businessTarget({ action: 'OPEN_APPLICATION', objectId: '6', parameters: { applicationType: 'selfPractice' } }, 'ROLE_STUDENT').query.applicationType, 'selfPractice')
})

test('form receipt links require a real route within the current role', () => {
  const resolve = fullPath => ({ fullPath, path: fullPath.split('?')[0], matched: [{ path: fullPath.startsWith('/company/jobs') ? '/company/jobs' : '/:pathMatch(.*)*' }] })
  assert.equal(localReceiptTarget('/company/jobs?positionId=7', 'ROLE_COMPANY', resolve), '/company/jobs?positionId=7')
  for (const url of ['https://evil.example', '//evil.example', 'javascript:alert(1)', '/student/home', '/company/missing', '/company/../admin/permissions', '/company/%2e%2e/admin/permissions', '/company/%2fadmin', '/company/\\evil.example']) {
    assert.equal(localReceiptTarget(url, 'ROLE_COMPANY', resolve), null, url)
  }
  assert.equal(localReceiptTarget('/company/jobs', 'ROLE_STUDENT', resolve), null)
})
test('sidebar preserves usable business width and clarification allows another turn', () => {
  assert.equal(shouldDock(1440, 420), true); assert.equal(shouldDock(1280, 420), true); assert.equal(shouldDock(390, 420), false)
  assert.equal(terminal('WAITING_INPUT'), true); assert.equal(terminal('CANCELLING'), false)
})
test('answer formatting returns text tokens instead of rendering provider HTML', () => {
  const parts = answerParts('## 结论\n**需补充专业信息**\n<img src=x onerror=alert(1)>')
  assert.equal(parts[1].bold, true)
  assert.equal(parts[1].text, '需补充专业信息')
  assert.ok(parts.at(-1).text.includes('<img'))
})
test('salary evidence preserves units and marks unusable historical values for confirmation', () => {
  assert.equal(jobSalary({ salaryMin: 8, salaryMax: 10, salaryUnit: 'K/月' }), '8 – 10 K/月')
  assert.equal(jobSalary({ salaryMin: 180, salaryMax: 220, salaryUnit: '元/天' }), '180 – 220 元/天')
  assert.equal(jobSalary({ salaryMin: 25, salaryMax: 35, salaryUnit: '元/小时' }), '25 – 35 元/小时')
  assert.equal(jobSalary({ salaryUnit: '面议' }), '面议')
  assert.equal(jobSalary({ salaryMin: 0, salaryMax: 0, salaryUnit: 'K/鏈�' }), '待核实')
  assert.equal(jobSalary({ salaryMin: 0, salaryMax: 0, salaryUnit: 'K/月' }), '待核实')
  assert.equal(jobSalary({ salaryMin: 20, salaryMax: 10, salaryUnit: 'K/月' }), '待核实')
  assert.equal(jobSalary({ salaryMin: null, salaryMax: 10, salaryUnit: 'K/月' }), '待核实')
})
test('new page-context links resolve only for their own role', () => {
  assert.deepEqual(businessTarget({ action: 'OPEN_COMPANY_JOBS', objectId: '6' }, 'ROLE_COMPANY'), { path: '/company/jobs', query: { agentPositionId: '6' } })
  assert.deepEqual(businessTarget({ action: 'OPEN_COMPANY_APPLICATIONS' }, 'ROLE_COMPANY'), { path: '/company/application-view', query: {} })
  assert.deepEqual(businessTarget({ action: 'OPEN_COMPANY_CONFIRM', objectId: '12' }, 'ROLE_COMPANY'), { path: '/company/internship-confirm', query: { agentConfirmationId: '12' } })
  assert.deepEqual(businessTarget({ action: 'OPEN_ADMIN_COMPANIES', objectId: '5' }, 'ROLE_ADMIN'), { path: '/admin/companies', query: { agentCompanyId: '5' } })
  assert.deepEqual(businessTarget({ action: 'OPEN_ADMIN_DASHBOARD' }, 'ROLE_ADMIN'), { path: '/admin/dashboard' })
  assert.deepEqual(businessTarget({ action: 'OPEN_ADMIN_STUDENTS', objectId: '8' }, 'ROLE_ADMIN'), { path: '/admin/student-users', query: { agentStudentId: '8' } })
  assert.deepEqual(businessTarget({ action: 'OPEN_JOBS' }, 'ROLE_STUDENT'), { path: '/student/jobs', query: {} })
  assert.equal(businessTarget({ action: 'OPEN_JOBS' }, 'ROLE_COMPANY'), null)
  assert.deepEqual(businessTarget({ action: 'OPEN_REFLECTIONS' }, 'ROLE_STUDENT'), { path: '/student/internships' })
  assert.deepEqual(businessTarget({ action: 'OPEN_APPRAISAL' }, 'ROLE_STUDENT'), { path: '/student/internship-appraisal' })
  assert.deepEqual(businessTarget({ action: 'OPEN_PROFILE' }, 'ROLE_STUDENT'), { path: '/student/profile' })
  assert.deepEqual(businessTarget({ action: 'OPEN_APPROVAL' }, 'ROLE_TEACHER_COUNSELOR'), { path: '/teacher/approval' })
  assert.deepEqual(businessTarget({ action: 'OPEN_APPRAISAL_COLLECTION' }, 'ROLE_TEACHER_DEPARTMENT'), { path: '/teacher/internship-appraisals' })
  assert.deepEqual(businessTarget({ action: 'OPEN_EVALUATION' }, 'ROLE_TEACHER_COLLEGE'), { path: '/teacher/evaluation' })
  assert.equal(businessTarget({ action: 'OPEN_ADMIN_COMPANIES', objectId: '5' }, 'ROLE_STUDENT'), null)
  assert.equal(businessTarget({ action: 'OPEN_COMPANY_JOBS' }, 'ROLE_TEACHER_COLLEGE'), null)
  assert.equal(businessTarget({ action: 'OPEN_COMPANY_JOBS' }, 'ROLE_ADMIN'), null)
  assert.equal(businessTarget({ action: 'OPEN_ADMIN_DASHBOARD' }, 'ROLE_COMPANY'), null)
  assert.equal(businessTarget({ action: 'OPEN_UNKNOWN_ACTION' }, 'ROLE_ADMIN'), null)
  assert.deepEqual(businessTarget({ action: 'OPEN_COMPANY_JOBS', objectId: '../etc' }, 'ROLE_COMPANY'), { path: '/company/jobs', query: {} })
  assert.deepEqual(businessTarget({ action: 'OPEN_ADMIN_STUDENTS', objectId: '8;drop' }, 'ROLE_ADMIN'), { path: '/admin/student-users', query: {} })
})

test('desktop remains non-modal at 1024 and keeps at least 640px business content', () => {
  for (const viewport of [1024, 1280, 1366, 1440, 1920]) {
    assert.equal(shouldDock(viewport, 560), true)
    assert.ok(viewport - panelSize(viewport, 560) >= 640)
  }
  assert.equal(panelSize(800, 500), 360)
  assert.equal(panelSize(390, 500), 390)
})

test('knowledge evidence allows regenerate while business facts refresh', () => {
  assert.equal(responseOperation({ cards: [{tool:'knowledge_lookup'}] }), 'REGENERATE')
  assert.equal(responseOperation({ responseKind:'QUERY', cards:[] }), 'REFRESH_QUERY')
  assert.equal(responseOperation({ allowedOperations:['EDIT','REGENERATE'], cards:[{tool:'student_progress'}] }), 'REGENERATE')
})

test('legacy sibling versions remain distinguishable and server revision ordering wins', () => {
  const tasks = [{id:'1-root',createdAt:1},{id:'3-next',editOf:'1-root',editRound:1,createdAt:3},{id:'2-next',editOf:'1-root',editRound:1,createdAt:2}]
  assert.deepEqual(taskVersions(tasks,tasks[1]).map(x=>x.id), ['1-root','2-next','3-next'])
  assert.deepEqual(taskVersions([{id:'a',rootMessageId:'root',revisionNumber:2},{id:'b',rootMessageId:'root',revisionNumber:1}],{id:'a',rootMessageId:'root'}).map(x=>x.id),['b','a'])
})
