import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { stripTypeScriptTypes } from 'node:module'

const source = readFileSync(new URL('./PermissionManagement.vue', import.meta.url), 'utf8')
const body = stripTypeScriptTypes(source.slice(source.indexOf('async function confirmAgentPreview'), source.indexOf('const saveAgentCaps =')))

function setup(cancel = false) {
  const calls = []
  const confirm = new Function('agentConfigBase', 'agentRoleColumns', 'agentCapRows', 'capabilityLabel', 'policyFieldLabel', 'policyValue', 'h', 'ElMessageBox', 'ElMessage', 'request', `${body}; return confirmAgentPreview`)(
    { value: { roleDefaults: { student: ['agent_chat'] }, autoTaskPolicy: { globalCooldownMinutes: 1440 } } },
    [{ key: 'student', label: '学生' }], [{ key: 'agent_chat', label: '对话问答' }], key => key, key => key, value => String(value),
    (...args) => args,
    { confirm: async message => { calls.push({ kind: 'confirm', message }); if (cancel) throw 'cancel' } },
    { warning: message => calls.push({ kind: 'warning', message }) },
    { put: async (path, payload) => { calls.push({ kind: 'put', path, payload }); return { code: 200 } } }
  )
  return { calls, confirm }
}
const preview = () => ({ actionId: 'server-preview', configurationVersion: 'v7', proposedRoleDefaults: { student: [] }, proposedAutoTaskPolicy: { globalCooldownMinutes: 2880 }, userChanges: [{ role: 'student', userId: 12, name: '测试学生', before: { voice_input: true }, overrides: {}, replace: true }], questions: [], impact: { tasksAtRisk: 2, tasks: [] } })

test('configuration writes only after showing the exact impact and carries preview/CAS identity', async () => {
  const { confirm, calls } = setup()
  assert.equal(await confirm(preview()), true)
  assert.deepEqual(calls.map(call => call.kind), ['confirm', 'put'])
  assert.match(JSON.stringify(calls[0].message), /学生 · 对话问答：开启 → 关闭/)
  assert.match(JSON.stringify(calls[0].message), /将暂停 2 个任务/)
  assert.equal(calls[1].payload.actionId, 'server-preview')
  assert.equal(calls[1].payload.configurationVersion, 'v7')
  assert.equal(calls[1].payload.confirmed, true)
  assert.deepEqual(calls[1].payload.userChanges[0].overrides, {})
})

test('cancelling a concrete permission preview does not write', async () => {
  const { confirm, calls } = setup(true)
  await assert.rejects(confirm(preview()), error => error === 'cancel')
  assert.equal(calls.some(call => call.kind === 'put'), false)
})

test('ambiguity and missing server preview block permission changes', async () => {
  const { confirm, calls } = setup()
  assert.equal(await confirm({ ...preview(), questions: ['同名用户，请确认账号'] }), false)
  await assert.rejects(confirm({ ...preview(), actionId: '' }), /未取得有效变更预览/)
  assert.equal(calls.some(call => call.kind === 'put'), false)
})

test('a per-user channel restriction is visible in the confirmation and preserved in the write', async () => {
  const { confirm, calls } = setup()
  const proposal = preview()
  proposal.userChanges = [{ role: 'teacher', userId: 8, name: '测试教师', overrides: {}, fromAutomationPolicy: { channels: ['STATION_REMINDER', 'EMAIL'] }, automationPolicy: { channels: ['STATION_REMINDER'] } }]
  assert.equal(await confirm(proposal), true)
  assert.match(JSON.stringify(calls[0].message), /测试教师.*自动跟进channels：STATION_REMINDER,EMAIL → STATION_REMINDER/)
  assert.deepEqual(calls[1].payload.userChanges[0].automationPolicy, { channels: ['STATION_REMINDER'] })
})
