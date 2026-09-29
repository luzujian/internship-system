/**
 * GUI/AI 效率对比（机器代理操作，时间是下限 proxy，真人手速需另测）：
 *  任务一（填表）：企业发布岗位，同一套数据，GUI 逐项填 vs AI 一句话填，终点=点发布落库。
 *  任务二（问答）：23生工1班有多少名学生？GUI 进跟踪页筛班级读总数 vs AI 侧栏问。
 * 清理：两岗位走 DELETE 接口删＋复查；问答零写。
 * 输出 output/.../tests/efficiency-compare.json
 */
import { chromium } from '@playwright/test'
import { writeFileSync } from 'node:fs'
import path from 'node:path'

const OUT = path.resolve('../output/agent-optimization-20260918-build/tests/efficiency-compare.json')
const BASE = 'http://127.0.0.1:5192'
const TAG = '0918'
const DATA = {
  category: null, // 运行时读第一个真实类别
  headcount: '5',
  salaryMin: '8',
  salaryMax: '12',
  salaryUnit: 'K/月',
  address: 'E2E效率测试路1号',
  description: 'E2E效率对比测试岗位描述',
  requirements: '仅用于效率对比测试',
  major: '不限制专业',
  type: '实习'
}
const result = { task1: {}, task2: {}, cleanup: {}, at: new Date().toISOString() }

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1600, height: 900 } })
await page.routeWebSocket(/.*/, s => s.close())
const api = page.request

async function loginAPI(username) {
  const r = await api.post(`${BASE}/api/auth/login`, { data: { username, password: '123456' } })
  const b = await r.json()
  const d = b.data || {}
  return d.token || d.accessToken
}
async function uiLogin(username, landing) {
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
  await page.fill("input[placeholder='请输入账号']", username)
  await page.fill("input[placeholder='请输入密码']", '123456')
  await Promise.all([
    page.waitForURL(u => u.toString().includes(landing), { timeout: 30000 }),
    page.click("button:has-text('登 录')")
  ])
  await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {})
}
// 交互计数器：如实记录机器代理的操作次数
function counter() {
  const c = { clicks: 0, fills: 0, selects: 0 }
  return {
    c,
    async click(loc, force = false) { c.clicks++; await loc.click({ force }) },
    async fill(loc, v) { c.fills++; await loc.fill(v) }
  }
}
async function findPosition(token, name) {
  const r = await api.get(`${BASE}/api/company/positions`, { headers: { Authorization: `Bearer ${token}` } })
  const b = await r.json()
  const list = Array.isArray(b.data) ? b.data : (b.data?.list || b.data?.rows || [])
  return list.find(p => (p.positionName || p.position_name) === name)
}

let ctoken = null
try {
  ctoken = await loginAPI('企业1')
  // 类别取第一个真实值（两边共用，保证同一数据）
  {
    const r = await api.get(`${BASE}/api/admin/position-categories/public`, { headers: { Authorization: `Bearer ${ctoken}` } })
    const b = await r.json()
    DATA.category = (b.data || [])[0]?.name
    if (!DATA.category) throw new Error('no category')
  }
  console.log('category:', DATA.category)

  // ---------- 任务一 GUI ----------
  {
    const k = counter()
    await uiLogin('企业1', '/company')
    await page.goto(`${BASE}/company/jobs`, { waitUntil: 'domcontentloaded' })
    await page.getByRole('button', { name: '发布岗位', exact: true }).click()
    const dlg = page.locator('.el-dialog').filter({ hasText: '计划招聘人数' })
    await dlg.waitFor({ state: 'visible', timeout: 15000 })
    const t0 = Date.now()
    let corrections = 0
    const fieldInput = label => dlg.locator('.el-form-item', { hasText: label }).locator('input').first()
    const optItems = () => page.locator('.el-select-dropdown__item:visible')
    async function pickOption(text) {
      // 等目标文本出现（避免点到正在关闭的旧下拉）
      await optItems().filter({ hasText: text }).first().waitFor({ state: 'visible', timeout: 10000 }).catch(() => {})
      const exact = optItems().filter({ hasText: text }).first()
      if (await exact.count()) { await k.click(exact, true); return true }
      await optItems().first().waitFor({ state: 'visible', timeout: 10000 })
      await k.click(optItems().first(), true); return false
    }
    // 类别（按名精确选，与 AI 同一数据）
    await k.click(dlg.locator('.el-form-item', { hasText: '岗位类别' }).locator('.el-select')); k.c.selects++
    if (!(await pickOption(DATA.category))) corrections++
    // 名称（allow-create：输入后必须点“创建”建议项，否则值不提交）
    await k.fill(dlg.locator('.el-form-item', { hasText: '岗位名称' }).locator('input').first(), `E2E效率岗-G${TAG}`)
    await optItems().filter({ hasText: `E2E效率岗-G${TAG}` }).first().waitFor({ state: 'visible', timeout: 10000 })
    await k.click(optItems().filter({ hasText: `E2E效率岗-G${TAG}` }).first(), true); k.c.selects++
    if ((await dlg.locator('.el-form-item', { hasText: '岗位名称' }).locator('input').first().inputValue()) !== `E2E效率岗-G${TAG}`) corrections++
    // 类型
    await k.click(dlg.locator('.el-form-item', { hasText: '岗位类型' }).locator('.el-select')); k.c.selects++
    if (!(await pickOption(DATA.type))) corrections++
    // 详细地址
    await k.fill(fieldInput('详细地址'), DATA.address)
    // 薪资
    await k.click(dlg.locator('.el-form-item', { hasText: '薪资范围' }).locator('.salary-unit-select')); k.c.selects++
    if (!(await pickOption(DATA.salaryUnit))) corrections++
    await k.fill(dlg.locator('.el-form-item', { hasText: '薪资范围' }).getByPlaceholder('最低'), DATA.salaryMin)
    await k.fill(dlg.locator('.el-form-item', { hasText: '薪资范围' }).getByPlaceholder('最高'), DATA.salaryMax)
    // 人数
    await k.fill(fieldInput('计划招聘人数'), DATA.headcount)
    // 专业
    await k.click(dlg.locator('.el-form-item', { hasText: '招聘专业' }).locator('.el-select')); k.c.selects++
    await k.click(page.locator('.el-select-dropdown__item:visible', { hasText: DATA.major }).first(), true)
    await page.keyboard.press('Escape')
    // 描述/要求
    await k.fill(dlg.locator('.el-form-item', { hasText: '岗位描述' }).locator('textarea'), DATA.description)
    await k.fill(dlg.locator('.el-form-item', { hasText: '任职要求' }).locator('textarea'), DATA.requirements)
    // 提交
    await k.click(dlg.getByRole('button', { name: '发布', exact: true }))
    await page.waitForResponse(r => r.url().includes('/api/company/positions') && r.request().method() === 'POST', { timeout: 30000 }).catch(() => {})
    await page.waitForTimeout(2000)
    const toast = await page.locator('.el-message').allInnerTexts().catch(() => [])
    const dlgStillOpen = await dlg.isVisible().catch(() => false)
    const ms = Date.now() - t0
    const row = await findPosition(ctoken, `E2E效率岗-G${TAG}`)
    result.task1.gui = { ms, interactions: k.c, corrections, success: !!row, positionId: row?.id || null, toast: toast.join('|').slice(0, 200), dlgStillOpen }
    console.log('T1-GUI:', JSON.stringify(result.task1.gui))
  }

  // ---------- 任务一 AI ----------
  {
    const k = counter()
    await page.goto(`${BASE}/company/jobs`, { waitUntil: 'domcontentloaded' })
    await page.locator('button.agent-entry').click()
    await page.locator('#agent-workspace').waitFor({ state: 'visible', timeout: 15000 })
    await page.getByRole('button', { name: '发布岗位', exact: true }).click()
    const dlg = page.locator('.el-dialog').filter({ hasText: '计划招聘人数' })
    await dlg.waitFor({ state: 'visible', timeout: 15000 })
    const t0 = Date.now()
    const sentence = `岗位类别选${DATA.category}，岗位名称E2E效率岗-A${TAG}，岗位类型${DATA.type}，详细地址${DATA.address}，薪资${DATA.salaryMin}到${DATA.salaryMax}${DATA.salaryUnit}每月，计划招${DATA.headcount}人，招聘专业${DATA.major}，岗位描述写${DATA.description}，任职要求写${DATA.requirements}`
    let corrections = 0
    await k.fill(page.locator('#agent-input'), sentence)
    await k.click(page.locator('.agent-composer .send-button:not(.stop-button)'))
    k.c.sends = 1
    // 等 AI 填写生效
    try {
      await page.locator('.fill-event[data-fill-status="applied"]').first().waitFor({ timeout: 120000 })
    } catch {
      const states = await page.locator('.fill-event').evaluateAll(els => els.map(e => e.getAttribute('data-fill-status') + ':' + (e.innerText || '').slice(0, 120)))
      result.task1.aiWaitDiag = { fillEvents: states }
      throw new Error('AI fill no applied in 120s; states=' + JSON.stringify(states).slice(0, 300))
    }
    const msFill = Date.now() - t0
    // 核对：读 AI 填写事件的赋值清单（模型层真值；allow-create 控件显示提交前可能为空）
    const appliedText = await page.locator('.fill-event[data-fill-status="applied"]').first().innerText()
    const mismatches = [
      appliedText.includes(`E2E效率岗-A${TAG}`) ? null : 'name missing in fill-event',
      appliedText.includes(DATA.headcount) ? null : 'headcount missing in fill-event',
      appliedText.includes(DATA.description.slice(0, 8)) ? null : 'desc missing in fill-event'
    ].filter(Boolean)
    result.task1.aiFillHead = appliedText.slice(0, 300)
    // 可见表单是否真正吃到值（旧实例残留会导致写到不可见实例上）
    async function visibleName() {
      return dlg.locator('.el-form-item', { hasText: '岗位名称' }).locator('input').first().inputValue()
    }
    if ((await visibleName()) !== `E2E效率岗-A${TAG}`) {
      corrections++
      result.task1.aiStaleInstanceRetry = true
      await dlg.getByRole('button', { name: '取消', exact: true }).click().catch(() => {})
      await page.waitForTimeout(1000)
      await page.getByRole('button', { name: '发布岗位', exact: true }).click()
      await dlg.waitFor({ state: 'visible', timeout: 15000 })
      await k.fill(page.locator('#agent-input'), sentence)
      await k.click(page.locator('.agent-composer .send-button:not(.stop-button)'))
      const n0 = await page.locator('.fill-event[data-fill-status="applied"]').count()
      await page.waitForFunction(
        n => document.querySelectorAll('.fill-event[data-fill-status="applied"]').length > n,
        n0, { timeout: 120000 })
    }
    if (mismatches.length) {
      corrections++
      const appliedBefore = await page.locator('.fill-event[data-fill-status="applied"]').count()
      await k.fill(page.locator('#agent-input'), `不对，${mismatches.join('，')}，改过来，其他不动`)
      await k.click(page.locator('.agent-composer .send-button:not(.stop-button)'))
      await page.waitForFunction(
        n => document.querySelectorAll('.fill-event[data-fill-status="applied"]').length > n,
        appliedBefore, { timeout: 120000 }).catch(() => {})
    }
    result.task1.aiMismatches = mismatches
    await k.click(dlg.getByRole('button', { name: '发布', exact: true }))
    await page.waitForResponse(r => r.url().includes('/api/company/positions') && r.request().method() === 'POST', { timeout: 30000 }).catch(() => {})
    await page.waitForTimeout(2000)
    const toastA = await page.locator('.el-message').allInnerTexts().catch(() => [])
    const dlgStillOpenA = await dlg.isVisible().catch(() => false)
    const ms = Date.now() - t0
    const row = await findPosition(ctoken, `E2E效率岗-A${TAG}`)
    result.task1.ai = { ms, msFill, interactions: { ...k.c }, corrections, success: !!row, positionId: row?.id || null, toast: toastA.join('|').slice(0, 200), dlgStillOpen: dlgStillOpenA }
    console.log('T1-AI:', JSON.stringify(result.task1.ai))
  }

  // ---------- 任务二：真值 ----------
  const ttoken = await loginAPI('t001')
  let truth = null
  {
    const r = await api.get(`${BASE}/api/teacher/internship-status`, {
      params: { page: 1, pageSize: 1, className: '23生工1班' },
      headers: { Authorization: `Bearer ${ttoken}` }
    })
    truth = (await r.json()).data?.total
  }
  console.log('truth total:', truth)

  // ---------- 任务二 GUI ----------
  {
    const k = counter()
    await uiLogin('t001', '/teacher')
    await page.goto(`${BASE}/teacher/student-tracking`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(3000)
    const t0 = Date.now()
    // 班级筛选
    const classItem = page.locator('.el-form-item', { hasText: '班级' })
    await k.click(classItem.locator('.el-select')); k.c.selects++
    await k.click(page.locator('.el-select-dropdown__item:visible', { hasText: '23生工1班' }).first())
    await k.click(page.getByRole('button', { name: '查询', exact: true }))
    await page.waitForTimeout(3000)
    const body = await page.innerText('body')
    const m = body.match(/共\s*(\d+)\s*条/)
    const ms = Date.now() - t0
    result.task2.gui = { ms, interactions: k.c, corrections: 0, readTotal: m ? +m[1] : null, correct: m ? +m[1] === truth : false }
    console.log('T2-GUI:', JSON.stringify(result.task2.gui))
  }

  // ---------- 任务二 AI ----------
  {
    const k = counter()
    await page.getByRole('button', { name: 'AI 助手' }).first().click()
    await page.locator('#agent-workspace').waitFor({ state: 'visible', timeout: 15000 })
    const fresh = page.locator('#agent-workspace').getByRole('button', { name: '新会话' })
    if (await fresh.count()) { await k.click(fresh); await page.waitForTimeout(1500) }
    const turnsBefore = await page.locator('.agent-turn').count()
    const t0 = Date.now()
    await k.fill(page.getByLabel('输入实习相关任务'), '23生工1班一共有多少名学生？只要数字')
    await k.click(page.getByRole('button', { name: '发送任务', exact: true }))
    k.c.sends = 1
    await page.waitForFunction(
      n => document.querySelectorAll('.agent-turn').length > n,
      turnsBefore, { timeout: 30000 })
    const turn = page.locator('.agent-turn').last()
    await turn.waitFor({ state: 'visible', timeout: 30000 })
    await page.waitForFunction(
      () => { const els = document.querySelectorAll('.agent-turn'); const last = els[els.length - 1]; return last && /已完成|失败|错误/.test(last.innerText || '') },
      null, { timeout: 120000 }).catch(() => {})
    const ms = Date.now() - t0
    const text = await turn.innerText()
    const nums = [...text.matchAll(/(\d+)\s*(名|位|人|条)/g)].map(x => +x[1])
    const hit = nums.includes(truth) || text.includes(`学生总数 ${truth}`)
    result.task2.ai = { ms, interactions: { ...k.c }, corrections: 0, numbers: nums.slice(0, 8), truth, correct: hit, answerHead: text.slice(0, 200) }
    console.log('T2-AI:', JSON.stringify(result.task2.ai))
  }

  // ---------- 清理 ----------
  await doCleanup()
  writeFileSync(OUT, JSON.stringify(result, null, 2), 'utf-8')
  console.log('SAVED', OUT)
} catch (e) {
  result.error = String(e).slice(0, 500)
  console.log('FLOW-ERROR:', result.error)
  try { await doCleanup() } catch (ce) { result.cleanupError = String(ce).slice(0, 200) }
  try { writeFileSync(OUT, JSON.stringify(result, null, 2), 'utf-8') } catch {}
  throw e
} finally {
  await browser.close().catch(() => {})
}

async function doCleanup() {
  const gone = []
  for (const name of [`E2E效率岗-G${TAG}`, `E2E效率岗-A${TAG}`]) {
    let n = 0
    for (let i = 0; i < 5; i++) {
      const row = await findPosition(ctoken, name)
      if (!row?.id) break
      const r = await api.delete(`${BASE}/api/company/positions/${row.id}`, { headers: { Authorization: `Bearer ${ctoken}` } })
      n++
      if (r.status() !== 200) { gone.push({ name, http: r.status() }); break }
    }
    if (!n) gone.push({ name, http: 'not-found' })
    else gone.push({ name, deleted: n })
  }
  const left = []
  for (const name of [`E2E效率岗-G${TAG}`, `E2E效率岗-A${TAG}`]) {
    if (await findPosition(ctoken, name)) left.push(name)
  }
  result.cleanup = { deleted: gone, leftovers: left }
  console.log('CLEANUP:', JSON.stringify(result.cleanup))
}
