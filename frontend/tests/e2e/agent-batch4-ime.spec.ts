import { test, expect, type Page } from '@playwright/test'
import path from 'node:path'
const shot = (name: string) => path.resolve('../output/agent-optimization-20260918-build/tests/batch4-shots', `${name}.png`)
const capabilities = () => ({
  enabled: true,
  actor: { ownerKey: 'mock:1', id: '1', name: 'IME mock 学生', role: 'ROLE_STUDENT', readOnly: false },
  capabilities: { agent_chat: true, form_fill: true, voice_input: true },
  tools: []
})
async function installMocks(page: Page, posts: string[]) {
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.origin !== 'http://127.0.0.1:5193') return route.abort()
    if (url.pathname === '/api/agent/tasks' && route.request().method() === 'POST') {
      posts.push(url.pathname)
      const payload = route.request().postDataJSON()
      return route.fulfill({ json: { code: 200, data: { id: 'mock-task-1', sessionId: 'mock-session', ownerKey: 'mock:1', message: payload.message, status: 'QUEUED', version: 1, answer: '', cards: [], steps: [], pageContext: payload.pageContext } } })
    }
    if (url.pathname.startsWith('/api/') || ['xhr', 'fetch'].includes(route.request().resourceType())) {
      let data: unknown = []
      if (url.pathname === '/api/agent/capabilities') data = capabilities()
      if (url.pathname === '/api/agent/sessions') data = []
      return route.fulfill({ json: { code: 200, data } })
    }
    return route.continue()
  })
}
async function openSidebar(page: Page) {
  await page.getByRole('button', { name: 'AI 助手' }).click()
  await expect(page.locator('#agent-input')).toBeVisible({ timeout: 20000 })
}
test('输入法选字 Enter 不误发，空输入不发送，正常 Enter 发送', async ({ page }) => {
  const posts: string[] = []
  await installMocks(page, posts)
  await page.goto('/batch4')
  await openSidebar(page)
  const composer = page.locator('#agent-input')
  await composer.fill('nihao ceshi')
  // 模拟中文输入法选字：compositionstart 后带 isComposing 的 Enter 必须被忽略
  await page.evaluate(() => {
    const el = document.querySelector('#agent-input') as HTMLTextAreaElement
    el.focus()
    el.dispatchEvent(new CompositionEvent('compositionstart', { data: 'ni' }))
    el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', bubbles: true, cancelable: true, isComposing: true }))
  })
  await page.waitForTimeout(1500)
  expect(posts.length).toBe(0)
  expect(await composer.inputValue()).toBe('nihao ceshi')
  await page.screenshot({ path: shot('06-ime-composing-blocked.png') })
  // 选字结束后的普通 Enter 正常发送
  await page.evaluate(() => {
    document.querySelector('#agent-input')?.dispatchEvent(new CompositionEvent('compositionend', { data: '你好' }))
  })
  await composer.press('Enter')
  await expect.poll(async () => posts.length, { timeout: 15000 }).toBe(1)
  // 空输入不发送
  await composer.fill('   ')
  await composer.press('Enter')
  await page.waitForTimeout(1500)
  expect(posts.length).toBe(1)
  console.log('IME_GUARD composing_blocked=ok blank_blocked=ok normal_sent=ok')
})