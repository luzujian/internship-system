/** 渲染冒烟：所有本次接线动过的页面必须能正常 setup（抓 TDZ 白屏）。 */
import { test, expect } from '@playwright/test'
import { authenticateAs, mockApis, type TestRole } from './helpers/auth'

const PAGES: Array<{ role: TestRole; paths: string[] }> = [
  { role: 'student', paths: ['/student/jobs', '/student/internships', '/student/internship-appraisal'] },
  { role: 'teacher', paths: ['/teacher/announcements', '/teacher/resources', '/teacher/evaluation', '/teacher/scoring-rules', '/teacher/aisettings', '/teacher/timeline-config', '/teacher/company-audit', '/teacher/keyword-library', '/teacher/student-tracking'] },
  { role: 'company', paths: ['/company/jobs', '/company/interviews', '/company/settings'] },
  { role: 'admin', paths: ['/admin/announcements', '/admin/resource-documents', '/admin/companies', '/admin/student-users', '/admin/teacher-users', '/admin/classes', '/admin/ai-model', '/admin/scoring-rule', '/admin/problem-feedback', '/admin/system-settings', '/admin/backup', '/admin/permissions'] },
]

for (const { role, paths } of PAGES) {
  test.describe(`smoke:${role}`, () => {
    for (const path of paths) {
      test(path, async ({ page }) => {
        const errors: string[] = []
        page.on('pageerror', e => errors.push(String(e).slice(0, 200)))
        await authenticateAs(page, role)
        await mockApis(page)
        await page.goto(path, { waitUntil: 'networkidle', timeout: 60000 })
        expect(page.url()).toContain(path)
        // 非白屏：有可交互元素
        expect(await page.getByRole('button').count()).toBeGreaterThan(0)
        expect(errors.filter(m => !m.includes('ResizeObserver')), []).toEqual([])
      })
    }
  })
}
