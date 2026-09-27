import { test, expect } from '@playwright/test'
import path from 'node:path'

const output = path.resolve('../output/agent-optimization-20260918-build')

test('mock mobile 390px result card does not overflow', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  const requests: string[] = []
  await page.routeWebSocket(/\/@vite\//, socket => socket.close())
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.origin !== 'http://127.0.0.1:5191') return route.abort()
    if (url.pathname.startsWith('/api/') || ['xhr', 'fetch'].includes(route.request().resourceType())) {
      requests.push(`${route.request().method()} ${url.pathname}`)
      let data: unknown = []
      if (url.pathname === '/api/agent/capabilities') data = { enabled: true, actor: { ownerKey: 'mock:1', id: '1', name: '受控 mock 企业', role: 'ROLE_COMPANY', readOnly: false }, capabilities: { agent_chat: true, form_fill: true, voice_input: true }, tools: [] }
      return route.fulfill({ json: { code: 200, data } })
    }
    return route.continue()
  })
  await page.goto('/batch1')
  await page.getByRole('button', { name: 'AI 助手' }).click()
  await page.evaluate(() => {
    const { agent } = (window as any).__batch1
    agent.tasks.push({ id: 'mock-card-task', sessionId: 'mock-session', ownerKey: agent.capabilities.actor.ownerKey, message: '超长卡片渲染检查', pageContext: { title: '岗位管理', route: '/company/recruitment', object: null, selectedObjects: [], filters: {} }, status: 'SUCCEEDED', version: 1, updatedAt: Date.now(), answer: '受控 mock 回答', cards: [{ tool: 'jobs_search', title: '超长字段渲染检查岗位卡片标题可以很长很长很长很长', status: 'ok', observedAt: new Date().toISOString(), scope: '受控 mock', items: [{ id: '1', title: 'x'.repeat(120), company: '受控 mock 企业', city: '深圳', description: '长'.repeat(200) }], total: 1, hasMore: false, facts: {}, links: [], warnings: [] }], steps: [] })
  })
  const card = page.locator('.agent-result').first()
  await expect(card).toBeVisible()
  await test.info().attach('card-overflow-evidence', { body: JSON.stringify(await card.evaluate(el => {
    const strong = el.querySelector('.result-item>strong')
    const cs = strong ? getComputedStyle(strong) : null
    return { viewport: { w: window.innerWidth, h: window.innerHeight }, cardWidth: el.getBoundingClientRect().width, scrollWidth: el.scrollWidth, clientWidth: el.clientWidth, strongOverflowWrap: cs?.overflowWrap, strongWordBreak: cs?.wordBreak }
  })), contentType: 'application/json' })
  const overflow = await card.evaluate(el => ({ card: el.scrollWidth - el.clientWidth, doc: document.documentElement.scrollWidth - window.innerWidth }))
  expect(overflow.card).toBeLessThanOrEqual(1)
  expect(overflow.doc).toBeLessThanOrEqual(1)
  await page.screenshot({ path: path.join(output, 'screenshots', 'batch1-final-mobile-card-clip.png'), fullPage: true })
  await test.info().attach('controlled-mock-requests', { body: JSON.stringify({ mode: 'ALL_API_CONTROLLED_MOCK_NO_BACKEND', requests }), contentType: 'application/json' })
})
