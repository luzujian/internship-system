import { test, expect, type APIRequestContext, type Locator, type Page, type Route } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { installVoiceTestBridge } from './voice-test-bridge'

const APP_ORIGIN = 'http://127.0.0.1:5173'
const API_BASE = 'http://localhost:8080'
const RUN_ID = new Date().toISOString().replace(/[:.]/g, '-')
const RUN_DIR = path.resolve(process.cwd(), 'output', 'voice-form-e2e', RUN_ID)
const AUDIO_DIR = path.join(RUN_DIR, 'audio')
const AUDIO_CACHE_DIR = path.resolve(process.cwd(), 'output', 'voice-form-e2e', 'audio-cache')
const SCREENSHOT_DIR = path.join(RUN_DIR, 'screenshots')
const REPORT_PATH = path.join(RUN_DIR, 'report.json')
const SIDE_EFFECTS_PATH = path.join(RUN_DIR, 'side-effects.json')
const ASR_SETTLE_MS = 1200

const AUDIO_DEFS = [
  { key: 'first-desc', file: 'first-desc.wav', ttsText: '描述写负责后端开发工作。', repeats: 3 },
  { key: 'multi-fields', file: 'multi-fields.wav', ttsText: '招聘专业填生物医学工程和信息管理与信息系统，人数三人。', repeats: 2 },
  { key: 'address-fields', file: 'address-fields.wav', ttsText: '工作地点填广东省深圳市南山区，详细地址填科技园路一号。', repeats: 2 },
  { key: 'headcount-five', file: 'headcount-five.wav', ttsText: '人数改成五人。', repeats: 3 }
] as const

type AudioKey = typeof AUDIO_DEFS[number]['key']
type AudioMeta = {
  key: AudioKey
  file: string
  path: string
  url: string
  ttsText: string
  voice: string
  sha256: string
  bytes: number
  durationMs: number
}

const report: any = {
  runId: RUN_ID,
  startedAt: new Date().toISOString(),
  scope: '真实音频输入—ASR—AI填表链路；仅覆盖本地 company:position 表单，不代表实体麦克风/真人口音/全部表单',
  environment: {},
  audio: [],
  steps: [],
  network: { fillIntents: [], fillResponses: [], positionWrites: [] },
  sideEffects: {
    login: null,
    persistenceNote: 'fill-intent 不创建 AgentTask/session，不持久化 usage；登录可能生成登录/安全审计记录',
    cleanupNote: '安全审计行不自动删除；如需清理需单独授权精确操作'
  },
  failure: null
}

const audioByKey = new Map<AudioKey, AudioMeta>()

function writeReport() {
  fs.mkdirSync(RUN_DIR, { recursive: true })
  fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), 'utf8')
  fs.mkdirSync(RUN_DIR, { recursive: true })
  fs.writeFileSync(SIDE_EFFECTS_PATH, JSON.stringify(report.sideEffects, null, 2), 'utf8')
}

function quotePowerShell(value: string) {
  return `'${value.replace(/'/g, "''")}'`
}

function runPowerShell(script: string) {
  const encoded = Buffer.from(script, 'utf16le').toString('base64')
  const candidates = process.platform === 'win32' ? ['pwsh.exe', 'powershell.exe'] : ['pwsh']
  let lastError: any = null
  for (const executable of candidates) {
    try {
      return execFileSync(executable, ['-NoProfile', '-NonInteractive', '-EncodedCommand', encoded], {
        cwd: process.cwd(),
        stdio: 'pipe',
        encoding: 'utf8'
      })
    } catch (error: any) {
      lastError = error
    }
  }
  const detail = lastError?.stderr?.toString?.() || lastError?.message || String(lastError)
  throw new Error(`Windows 中文 TTS 生成失败：${detail}`)
}

function synthesizeAudio(key: AudioKey, file: string, ttsText: string, repeats: number): AudioMeta {
  fs.mkdirSync(AUDIO_CACHE_DIR, { recursive: true })
  const target = path.join(AUDIO_CACHE_DIR, file)
  if (fs.existsSync(target) && fs.statSync(target).size > 44) {
    const bytes = fs.readFileSync(target)
    const byteRate = bytes.readUInt32LE(28)
    return {
      key,
      file,
      path: target,
      url: `/__voice-audio/${key}.wav`,
      ttsText,
      voice: 'cached',
      sha256: crypto.createHash('sha256').update(bytes).digest('hex'),
      bytes: bytes.length,
      durationMs: Math.round(((bytes.length - 44) / byteRate) * 1000)
    }
  }
  const script = `
Add-Type -AssemblyName System.Speech
$path = ${quotePowerShell(target)}
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$voiceCandidates = @('Microsoft Kangkang', 'Microsoft Huihui Desktop', 'Microsoft Huihui', 'Microsoft Yaoyao')
$selectedVoice = $null
foreach ($candidate in $voiceCandidates) {
  try { $synth.SelectVoice($candidate); $selectedVoice = $candidate; break } catch { }
}
if (-not $selectedVoice) { throw '没有可用的中文 TTS 音色' }
$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(48000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
$synth.Rate = 0
$synth.Volume = 100
$synth.SetOutputToWaveFile($path, $format)
$builder = New-Object System.Speech.Synthesis.PromptBuilder([System.Globalization.CultureInfo]::GetCultureInfo('zh-CN'))
for ($i = 0; $i -lt ${repeats}; $i++) {
  $builder.AppendText(${quotePowerShell(ttsText)})
  if ($i -lt ${repeats - 1}) { $builder.AppendBreak([TimeSpan]::FromSeconds(1.5)) }
}
$synth.Speak($builder)
$synth.Dispose()
Write-Output "VOICE:$selectedVoice"
`
  const stdout = runPowerShell(script)
  const voice = (String(stdout).match(/VOICE:([^\r\n]+)/)?.[1] || 'unknown').trim()
  const bytes = fs.readFileSync(target)
  const byteRate = bytes.readUInt32LE(28)
  return {
    key,
    file,
    path: target,
    url: `/__voice-audio/${key}.wav`,
    ttsText,
    voice,
    sha256: crypto.createHash('sha256').update(bytes).digest('hex'),
    bytes: bytes.length,
    durationMs: Math.round(((bytes.length - 44) / byteRate) * 1000)
  }
}

async function captureScreenshot(page: Page, name: string) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true })
  const target = path.join(SCREENSHOT_DIR, `${name}.png`)
  try {
    await page.screenshot({ path: target, fullPage: true })
    return target
  } catch {
    return ''
  }
}

async function runStep(page: Page, name: string, expected: unknown, action: () => Promise<unknown>) {
  const startedAt = Date.now()
  try {
    const actual = await action()
    report.steps.push({ name, expected, actual, pass: true, durationMs: Date.now() - startedAt, at: new Date().toISOString() })
    writeReport()
    return actual
  } catch (error: any) {
    const failureReason = error?.message || String(error)
    report.failure = report.failure || { name, failureReason }
    report.steps.push({ name, expected, actual: { error: failureReason }, pass: false, durationMs: Date.now() - startedAt, at: new Date().toISOString() })
    const shot = await captureScreenshot(page, `FAIL-${name}`)
    if (shot) report.steps[report.steps.length - 1].screenshot = shot
    writeReport()
    throw error
  }
}

async function loginCompany(request: APIRequestContext) {
  const startedAt = new Date().toISOString()
  const response = await request.post(`${API_BASE}/api/auth/login`, {
    data: { username: '企业1', password: '123456' }
  })
  const body = await response.json()
  if (body?.code !== 200) throw new Error(`企业测试账号登录失败：${JSON.stringify(body).slice(0, 300)}`)
  report.sideEffects.login = {
    username: '企业1',
    requestedAt: startedAt,
    completedAt: new Date().toISOString(),
    auditNote: '登录接口可能写入 LOGIN_SUCCESS 安全事件/登录日志，测试只记录不自动删除'
  }
  writeReport()
  return body.data as { accessToken: string; refreshToken: string; user: any }
}

async function injectCompanyAuth(page: Page, auth: any) {
  await page.addInitScript((a: any) => {
    localStorage.setItem('current_role', 'ROLE_COMPANY')
    localStorage.setItem('company_token_ROLE_COMPANY', a.accessToken)
    localStorage.setItem('company_accessToken_ROLE_COMPANY', a.accessToken)
    localStorage.setItem('company_refreshToken_ROLE_COMPANY', a.refreshToken)
    localStorage.setItem('company_role_ROLE_COMPANY', 'ROLE_COMPANY')
    localStorage.setItem('company_userId_ROLE_COMPANY', String(a.user.id))
    localStorage.setItem('company_username_ROLE_COMPANY', a.user.username)
    localStorage.setItem('company_name_ROLE_COMPANY', a.user.name)
    localStorage.setItem('company_isReadOnly_ROLE_COMPANY', 'false')
    localStorage.setItem('company_companyId_COMPANY', String(a.user.id))
    localStorage.removeItem('interviewTemplate')
  }, auth)
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function formItem(dialog: Locator, label: string) {
  return dialog.locator('.el-form-item').filter({ hasText: new RegExp(`^${escapeRegExp(label)}`) })
}

function formInput(dialog: Locator, label: string) {
  return formItem(dialog, label).locator('input').first()
}

function formTextarea(dialog: Locator, label: string) {
  return formItem(dialog, label).locator('textarea').first()
}

async function openPositionForm(page: Page) {
  await page.locator('button.agent-entry').click()
  await expect(page.locator('#agent-workspace')).toBeVisible()
  await page.getByRole('button', { name: '发布岗位' }).click()
  const dialog = page.locator('.el-dialog:visible').filter({ hasText: '计划招聘人数' })
  await expect(dialog).toBeVisible()
  await expect(page.locator('.agent-form-link', { hasText: '发布岗位' })).toBeVisible()
  return dialog
}

async function positionValues(dialog: Locator) {
  const salaryRange = formItem(dialog, '薪资范围')
  return {
    positionName: await formInput(dialog, '岗位名称').inputValue(),
    department: await formInput(dialog, '岗位类别').inputValue(),
    salaryUnit: await salaryRange.locator('input').first().inputValue(),
    salaryMin: await dialog.getByPlaceholder('最低').inputValue(),
    salaryMax: await dialog.getByPlaceholder('最高').inputValue(),
    plannedRecruit: await formInput(dialog, '计划招聘人数').inputValue(),
    requiredMajors: await formItem(dialog, '招聘专业').locator('.el-select__tags-text').allTextContents(),
    workAddress: await formInput(dialog, '工作地点').inputValue(),
    detailAddress: await formInput(dialog, '详细地址').inputValue(),
    description: await formTextarea(dialog, '岗位描述').inputValue(),
    requirements: await formTextarea(dialog, '任职要求').inputValue()
  }
}

async function setAudio(page: Page, key: AudioKey) {
  await page.evaluate((audioKey: string) => {
    ;(window as any).__voiceE2E.audioKey = audioKey
  }, key)
}

async function waitForNaturalAudioEnd(page: Page) {
  await page.waitForFunction(() => {
    const api = (window as any).__voiceE2E
    return Boolean(api?.audioEnded && !api?.audioEndedByCleanup)
  }, null, { timeout: 45_000 })
}

async function waitForTranscript(page: Page, tokens: string[], numberAny: string[] = []) {
  await page.waitForFunction(({ required, numbers }) => {
    const text = document.querySelector('.asr-status em')?.textContent || ''
    return required.every(token => text.includes(token)) && (!numbers.length || numbers.some(token => text.includes(token)))
  }, { required: tokens, numbers: numberAny }, { timeout: 30_000 })
  await page.waitForTimeout(ASR_SETTLE_MS)
  return (await page.locator('.asr-status em').textContent()) || ''
}

async function speakAndSubmit(page: Page, key: AudioKey, tokens: string[], numberAny: string[] = []) {
  await setAudio(page, key)
  await page.locator('.mic-button').click({ force: true })
  await expect(page.locator('.asr-status')).toContainText('正在聆听', { timeout: 15_000 })
  await waitForNaturalAudioEnd(page)
  const interimTranscript = await waitForTranscript(page, tokens, numberAny)
  const requestPromise = page.waitForRequest(request =>
    request.url().includes('/api/agent/forms/fill-intent') && request.method() === 'POST',
  { timeout: 20_000 })
  await page.locator('.mic-button').click({ force: true })
  const request = await requestPromise
  const requestBody = request.postDataJSON() as any
  const message = String(requestBody?.message || '')
  report.network.fillIntents.push({ audioKey: key, message, interimTranscript, at: new Date().toISOString() })
  writeReport()
  return { interimTranscript, requestBody, message }
}

function lastFillEvent(page: Page, status: 'applied' | 'blocked' | 'undone') {
  return page.locator(`.fill-event[data-fill-status="${status}"]`).last()
}

async function holdNextFillIntent(page: Page) {
  const pattern = '**/api/agent/forms/fill-intent'
  let releaseGate: () => void = () => {}
  let resolveCaptured: (body: any) => void = () => {}
  let rejectCaptured: (error: unknown) => void = () => {}
  const gate = new Promise<void>(resolve => { releaseGate = resolve })
  const captured = new Promise<any>((resolve, reject) => {
    resolveCaptured = resolve
    rejectCaptured = reject
  })
  const handler = async (route: Route) => {
    try {
      const response = await route.fetch()
      const raw = await response.body()
      const body = JSON.parse(raw.toString('utf8'))
      resolveCaptured(body)
      await gate
      await route.fulfill({ response })
    } catch (error) {
      rejectCaptured(error)
      throw error
    } finally {
      await page.unroute(pattern, handler).catch(() => {})
    }
  }
  await page.route(pattern, handler)
  return { captured, release: () => releaseGate() }
}

function assignmentValue(body: any, prop: string) {
  const assignments = body?.data?.assignments
  if (!Array.isArray(assignments)) return undefined
  return assignments.find((item: any) => item?.prop === prop)?.value
}

async function assertBackendHealthy(request: APIRequestContext) {
  const response = await request.get(`${API_BASE}/api/health/check`)
  if (!response.ok()) throw new Error(`本地后端健康检查失败：HTTP ${response.status()}`)
  const body = await response.json()
  if (body?.data?.database !== 'connected') throw new Error(`本地数据库未连接：${JSON.stringify(body).slice(0, 300)}`)
}

test.beforeAll(() => {
  fs.mkdirSync(AUDIO_DIR, { recursive: true })
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true })
  for (const definition of AUDIO_DEFS) {
    const meta = synthesizeAudio(definition.key, definition.file, definition.ttsText, definition.repeats)
    audioByKey.set(definition.key, meta)
    report.audio.push(meta)
  }
  report.environment = {
    appOrigin: APP_ORIGIN,
    apiBase: API_BASE,
    chromeFlag: 'MediaStreamTrackWebSpeech',
    ttsVoice: 'auto: Kangkang > Huihui Desktop > Huihui > Yaoyao',
    testCommand: 'npm run test:voice-e2e',
    note: 'Chrome 在线 ASR 与后端模型按各自配置访问外部服务；其余网页与业务接口仅连接本地地址'
  }
  writeReport()
})

test('真实音频输入驱动现有 ASR、AI 填表及冲突保护', async ({ page, context, request }, testInfo) => {
  let activeHold: { release: () => void } | null = null
  try {
    await runStep(page, 'preflight-local-services', { app: APP_ORIGIN, api: API_BASE }, async () => {
      await assertBackendHealthy(request)
      const response = await request.get(APP_ORIGIN)
      if (!response.ok()) throw new Error(`本地前端不可用：HTTP ${response.status()}`)
      return { frontend: 'ok', backend: 'ok' }
    })

    const auth = await loginCompany(request)
    await context.grantPermissions(['microphone'], { origin: APP_ORIGIN })
    await installVoiceTestBridge(page, Object.fromEntries([...audioByKey.values()].map(item => [item.key, item.url])))
    await injectCompanyAuth(page, auth)

    await page.route('**/__voice-audio/*.wav', async route => {
      const fileName = path.basename(new URL(route.request().url()).pathname)
      const key = fileName.replace(/\.wav$/, '') as AudioKey
      const audio = audioByKey.get(key)
      if (!audio) return route.continue()
      await route.fulfill({ path: audio.path, contentType: 'audio/wav' })
    })

    page.on('request', req => {
      if (req.url().includes('/api/admin/positions') && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(req.method())) {
        report.network.positionWrites.push({ method: req.method(), url: req.url(), at: new Date().toISOString() })
      }
    })
    page.on('response', async response => {
      if (!response.url().includes('/api/agent/forms/fill-intent')) return
      try {
        const body = await response.json()
        report.network.fillResponses.push({ at: new Date().toISOString(), usage: body?.data?.usage || null, assignments: body?.data?.assignments || [] })
        writeReport()
      } catch { /* 非 JSON 或浏览器已关闭，忽略证据读取失败 */ }
    })
    page.on('console', message => {
      if (message.type() === 'error') {
        report.consoleErrors = report.consoleErrors || []
        report.consoleErrors.push({ text: message.text(), at: new Date().toISOString() })
      }
    })

    await page.goto(`${APP_ORIGIN}/company/jobs`, { waitUntil: 'domcontentloaded', timeout: 30_000 })
    await expect(page).not.toHaveURL(/\/login/)
    const dialog = await openPositionForm(page)
    const plannedRecruit = formInput(dialog, '计划招聘人数')
    const description = formTextarea(dialog, '岗位描述')
    const requirements = formTextarea(dialog, '任职要求')

    await runStep(page, '1-first-real-audio-form-fill', { description: '负责后端开发工作' }, async () => {
      const result = await speakAndSubmit(page, 'first-desc', ['描述', '负责后端开发工作'])
      expect(result.message).toContain('描述')
      await expect(lastFillEvent(page, 'applied')).toBeVisible({ timeout: 120_000 })
      await expect(description).toHaveValue(/负责后端开发工作/)
      const actual = await description.inputValue()
      expect(actual).toContain('负责后端开发工作')
      const screenshot = await captureScreenshot(page, '01-first-fill')
      return { actualTranscript: result.message, description: actual, screenshot }
    })

    await runStep(page, '2-one-sentence-multi-field', {
      requiredMajors: ['生物医学工程', '信息管理与信息系统'],
      plannedRecruit: '3'
    }, async () => {
      const before = await positionValues(dialog)
      const result = await speakAndSubmit(page, 'multi-fields',
        ['专业', '生物医学工程', '信息管理与信息系统', '人数'], ['三人', '3人'])
      expect(result.message).toContain('生物医学工程')
      await expect(lastFillEvent(page, 'applied')).toBeVisible({ timeout: 120_000 })
      await expect(plannedRecruit).toHaveValue('3')
      const after = await positionValues(dialog)
      expect(after.requiredMajors).toEqual(['生物医学工程', '信息管理与信息系统'])
      expect(after.positionName).toBe(before.positionName)
      expect(after.department).toBe(before.department)
      expect(after.salaryUnit).toBe(before.salaryUnit)
      expect(after.salaryMin).toBe(before.salaryMin)
      expect(after.salaryMax).toBe(before.salaryMax)
      const screenshot = await captureScreenshot(page, '02-multi-fields')
      return {
        actualTranscript: result.message,
        expectedFields: { requiredMajors: ['生物医学工程', '信息管理与信息系统'], plannedRecruit: '3' },
        actualFields: after,
        before,
        screenshot
      }
    })

    await runStep(page, '2b-address-cascade', {
      workAddress: '广东省 / 深圳市 / 南山区',
      detailAddress: '科技园路1号'
    }, async () => {
      const result = await speakAndSubmit(page, 'address-fields',
        ['工作地点', '广东省', '深圳市', '南山区', '详细地址'], ['一号', '1号'])
      expect(result.message).toContain('广东省')
      await expect(lastFillEvent(page, 'applied')).toBeVisible({ timeout: 120_000 })
      await expect(formInput(dialog, '工作地点')).toHaveValue(/广东省.*深圳市.*南山区/)
      await expect(formInput(dialog, '详细地址')).toHaveValue(/科技园路/)
      const actual = await positionValues(dialog)
      const screenshot = await captureScreenshot(page, '02b-address-cascade')
      return { actualTranscript: result.message, actual, screenshot }
    })

    await runStep(page, '3-continuous-modification', {
      plannedRecruit: '5',
      requiredMajors: ['生物医学工程', '信息管理与信息系统'],
      description: '负责后端开发工作'
    }, async () => {
      const before = await positionValues(dialog)
      const result = await speakAndSubmit(page, 'headcount-five', ['人数'], ['五人', '5人'])
      expect(result.message).toContain('人数')
      await expect(lastFillEvent(page, 'applied')).toBeVisible({ timeout: 120_000 })
      await expect(plannedRecruit).toHaveValue('5')
      const after = await positionValues(dialog)
      expect(after.requiredMajors).toEqual(before.requiredMajors)
      expect(after.description).toBe(before.description)
      expect(after.requirements).toBe(before.requirements)
      const screenshot = await captureScreenshot(page, '03-continuous-modification')
      return { actualTranscript: result.message, before, after, screenshot }
    })

    await runStep(page, '4-undo-preserves-same-field-manual-edit', { plannedRecruit: '6', notice: '已保留当前值' }, async () => {
      await plannedRecruit.fill('6')
      await plannedRecruit.press('Tab')
      await expect(plannedRecruit).toHaveValue('6')
      await page.getByRole('button', { name: '撤销最近一批 AI 填写' }).click()
      await expect(lastFillEvent(page, 'undone')).toContainText('已保留当前值')
      await expect(plannedRecruit).toHaveValue('6')
      const screenshot = await captureScreenshot(page, '04-undo-preserves-manual')
      return { actualPlannedRecruit: await plannedRecruit.inputValue(), notice: await lastFillEvent(page, 'undone').textContent(), screenshot }
    })

    await runStep(page, '5-pending-response-does-not-overwrite-new-input', { heldResponse: 'plannedRecruit=5', afterManualInput: '7', notice: '未覆盖当前值' }, async () => {
      const hold = await holdNextFillIntent(page)
      activeHold = hold
      const spoken = await speakAndSubmit(page, 'headcount-five', ['人数'], ['五人', '5人'])
      const realBody = await hold.captured
      expect(String(assignmentValue(realBody, 'plannedRecruit'))).toBe('5')
      await plannedRecruit.fill('7')
      await plannedRecruit.press('Tab')
      await expect(plannedRecruit).toHaveValue('7')
      hold.release()
      activeHold = null
      await expect(lastFillEvent(page, 'blocked')).toContainText('未覆盖当前值', { timeout: 120_000 })
      await expect(plannedRecruit).toHaveValue('7')
      const screenshot = await captureScreenshot(page, '05-pending-conflict')
      return { actualTranscript: spoken.message, realResponseAssignment: assignmentValue(realBody, 'plannedRecruit'), actualPlannedRecruit: await plannedRecruit.inputValue(), notice: await lastFillEvent(page, 'blocked').textContent(), screenshot }
    })

    await runStep(page, '6-held-response-switch-form-blocks-misfill', { blocked: '表单已关闭或切换', interviewLocation: '', interviewRemark: '' }, async () => {
      const hold = await holdNextFillIntent(page)
      activeHold = hold
      const spoken = await speakAndSubmit(page, 'headcount-five', ['人数'], ['五人', '5人'])
      const realBody = await hold.captured
      expect(String(assignmentValue(realBody, 'plannedRecruit'))).toBe('5')
      await dialog.getByRole('button', { name: '取消' }).click()
      await expect(dialog).toBeHidden()
      await page.getByRole('button', { name: '预设面试信息' }).click()
      const interviewDialog = page.locator('.el-dialog:visible').filter({ hasText: '填写面试信息' })
      await expect(interviewDialog).toBeVisible()
      const interviewLocation = formInput(interviewDialog, '面试地点')
      const interviewRemark = formTextarea(interviewDialog, '备注')
      hold.release()
      activeHold = null
      await expect(lastFillEvent(page, 'blocked')).toContainText('表单已关闭或切换', { timeout: 120_000 })
      await expect(interviewLocation).toHaveValue('')
      await expect(interviewRemark).toHaveValue('')
      const screenshot = await captureScreenshot(page, '06-switch-form-blocked')
      await interviewDialog.getByRole('button', { name: '取消' }).click()
      return { actualTranscript: spoken.message, realResponseAssignment: assignmentValue(realBody, 'plannedRecruit'), interviewLocation: await interviewLocation.inputValue(), interviewRemark: await interviewRemark.inputValue(), notice: await lastFillEvent(page, 'blocked').textContent(), screenshot }
    })

    await runStep(page, '7-business-boundary', { positionWrites: 0, formalBusinessSubmit: false }, async () => {
      await page.waitForTimeout(500)
      expect(report.network.positionWrites).toHaveLength(0)
      const websocketErrors = (report.consoleErrors || []).filter((item: any) =>
        String(item?.text || '').includes('WebSocket'))
      expect(websocketErrors).toEqual([])
      const screenshot = await captureScreenshot(page, '07-business-boundary')
      return {
        positionWrites: report.network.positionWrites,
        formalBusinessSubmit: false,
        websocketErrors,
        screenshot
      }
    })

    report.summary = { result: 'PASS', statement: '真实音频输入—ASR—AI填表链路通过', scope: report.scope }
  } catch (error: any) {
    report.summary = { result: 'FAIL', statement: '真实音频输入—ASR—AI填表链路未通过', failureReason: error?.message || String(error), scope: report.scope }
    await captureScreenshot(page, 'FAIL-final')
    throw error
  } finally {
    if (activeHold) activeHold.release()
    writeReport()
    if (fs.existsSync(REPORT_PATH)) await testInfo.attach('voice-form-e2e-report', { path: REPORT_PATH, contentType: 'application/json' }).catch(() => {})
  }
})
