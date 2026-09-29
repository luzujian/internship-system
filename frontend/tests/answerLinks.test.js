import test from 'node:test'
import assert from 'node:assert/strict'
import { allowsWriteAction, relevantAnswerCards } from '../src/agent/protocol.mjs'
const role = 'ROLE_STUDENT'
const links = [
  { action: 'OPEN_JOBS', label: '去职位浏览' },
  { action: 'OPEN_CONFIRMATION', label: '去实习确认' },
  { action: 'OPEN_HOME', label: '回首页核对待办' }
]
const task = answer => ({ status: 'SUCCEEDED', answer, cards: [{ status: 'OK', items: [], links }] })
test('dates and counts do not display unrelated navigation from intermediate progress queries', () => {
  assert.deepEqual(relevantAnswerCards(task('实习于12月31日结束。'), role), [])
  assert.deepEqual(relevantAnswerCards(task('你有0项待办。'), role), [])
})
test('only the page named in the answer appears and duplicates are removed', () => {
  const input = task('请进入 **实习确认** 页面办理。')
  input.cards.push(input.cards[0])
  assert.deepEqual(relevantAnswerCards(input, role).flatMap(card => card.links), [links[1]])
})
test('object links and apply items only refer to positions actually discussed', () => {
  const input = { status: 'SUCCEEDED', answer: '推荐前端开发实习生。', cards: [{ status: 'OK',
    items: [{ id: 1, title: '前端开发实习生', companyName: '同一公司' }, { id: 2, title: '财务实习生', companyName: '同一公司' }],
    links: [1, 2].map(id => ({ action: 'OPEN_JOB', objectId: id, label: '查看岗位' })) }] }
  const result = relevantAnswerCards(input, role)
  assert.equal(result[0].links.length, 1)
  assert.equal(result[0].items.length, 1)
  assert.equal(result[0].links[0].objectId, 1)
  assert.equal(result[0].links[0].label, '查看岗位：前端开发实习生（同一公司）')
  input.answer = '同一公司有两个岗位。'
  assert.deepEqual(relevantAnswerCards(input, role), [])
})
test('generic job links identify each compared position by name', () => {
  const input = {
    status: 'SUCCEEDED',
    answer: '测试适合不限专业；数据分析实习生更看重数据类专业。',
    cards: [{ status: 'OK', tool: 'jobs_details',
      items: [
        { id: 1, title: '测试', company: '企业1' },
        { id: 2, title: '数据分析实习生', company: '企业1' }
      ],
      links: [
        { action: 'OPEN_JOB', objectId: 1, label: '查看岗位' },
        { action: 'OPEN_JOB', objectId: 2, label: '查看岗位' }
      ] }]
  }
  assert.deepEqual(relevantAnswerCards(input, role).flatMap(card => card.links.map(link => link.label)), [
    '查看岗位：测试（企业1）',
    '查看岗位：数据分析实习生（企业1）'
  ])
})

test('search and detail results offer one application entry per job across the answer', () => {
  const job = { id: 42, title: '数据分析实习生', company: '企业1' }
  const input = {
    status: 'SUCCEEDED', message: '帮我查询当前可投递的岗位，并说明岗位要求。',
    answer: '当前可投递数据分析实习生。',
    cards: ['jobs_search', 'jobs_details'].map((tool, index) => ({
      status: 'OK', tool, items: [{ ...job, id: index ? '42' : 42 }],
      links: [{ action: 'OPEN_JOB', objectId: 42, label: '查看岗位' }]
    }))
  }
  const result = relevantAnswerCards(input, role)
  assert.equal(result.flatMap(card => card.items).length, 1)
  assert.equal(result.flatMap(card => card.links).length, 1)
  assert.equal(result[0].links[0].label, '查看岗位：数据分析实习生（企业1）')
  assert.equal(input.cards[1].items.length, 1, 'rendering must not mutate query evidence')
})

test('job entry deduplication preserves distinct jobs with the same title and other record types', () => {
  const row = { id: 42, title: '数据分析实习生' }
  const input = {
    status: 'SUCCEEDED', answer: '数据分析实习生。',
    cards: [
      { status: 'OK', tool: 'jobs_search', items: [row, { ...row }, { ...row, id: 43 }], links: [] },
      { status: 'OK', tool: 'jobs_details', items: [{ ...row, id: '43' }], links: [] },
      { status: 'OK', tool: 'student_applications', items: [{ ...row, objectType: 'job_application' }], links: [] }
    ]
  }
  const result = relevantAnswerCards(input, role)
  assert.deepEqual(result.filter(card => card.tool.startsWith('jobs_')).flatMap(card => card.items.map(item => item.id)), [42, 43])
  assert.equal(result.find(card => card.tool === 'student_applications').items.length, 1)
})

test('failed replies and unsupported role targets do not expose links', () => {
  assert.deepEqual(relevantAnswerCards({ ...task('职位浏览'), status: 'FAILED' }, role), [])
  assert.deepEqual(relevantAnswerCards(task('职位浏览'), 'ROLE_ADMIN'), [])
})

test('application count with named positions does not emit progress record links', () => {
  const items = ['job_application', 'offer', 'confirmation'].map((objectType, index) => ({ id: index + 1, objectType, title: '软件开发工程师' }))
  const input = { status: 'SUCCEEDED', message: '我申请了几个岗位，你知道吗', answer: '你共投递了3个岗位：软件开发工程师、软件测试岗、软件运维岗。', cards: [{ status: 'OK', items,
    links: items.map(row => ({ action: 'OPEN_APPLICATION', objectId: row.id, label: '核对记录', parameters: { objectType: row.objectType } })) }] }
  assert.deepEqual(relevantAnswerCards(input, role), [])
  input.message = '我申请了几个岗位，在哪里查看？'
  assert.ok(relevantAnswerCards(input, role).length)
})
test('same numeric id across business types cannot match a different record', () => {
  const input = { status: 'SUCCEEDED', answer: '软件开发工程师申请待处理。', cards: [{ status: 'OK',
    items: [{ id: 1, title: '软件开发工程师', objectType: 'job_application' }, { id: 1, title: '会计实习生', objectType: 'offer' }],
    links: [{ action: 'OPEN_INTERVIEWS', objectId: 1, label: '核对记录', parameters: { objectType: 'offer' } }] }] }
  assert.deepEqual(relevantAnswerCards(input, role).flatMap(card => card.links), [])
})

test('write actions require explicit intent instead of a query result', () => {
  const base = { status: 'SUCCEEDED', cards: [] }
  assert.equal(allowsWriteAction({ ...base, message: '你觉得我有什么特长？' }, 'JOB_APPLY'), false)
  assert.equal(allowsWriteAction({ ...base, message: '我申请了几个岗位？' }, 'JOB_APPLY'), false)
  assert.equal(allowsWriteAction({ ...base, message: '帮我投递数据分析实习生' }, 'JOB_APPLY'), true)
  assert.equal(allowsWriteAction({ ...base, message: '查看这个岗位的要求' }, 'JOB_APPLY'), false)
})
