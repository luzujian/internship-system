import { _android } from '@playwright/test'
const BASE = 'http://10.0.2.2:5192'
const [device] = await _android.devices()
const context = await device.launchBrowser()
const page = await context.newPage()
await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded', timeout: 60000 })
await page.waitForTimeout(2000)
await page.getByPlaceholder('请输入账号').fill('t001')
await page.getByPlaceholder('请输入密码').fill('123456')
await page.waitForTimeout(2000)
await page.screenshot({ path: 'C:/Users/24096/AppData/Local/Temp/opencode/dbg-screen.png' })
console.log('shot saved')
await context.close()
