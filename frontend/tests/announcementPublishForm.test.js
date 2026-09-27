import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const publishFiles = [
  'src/views/admin/Dashboard.vue',
  'src/views/admin/AnnouncementManagement.vue',
  'src/views/teacher/AnnouncementView.vue'
]
const viewFiles = [
  ...publishFiles,
  'src/views/student/AnnouncementView.vue',
  'src/views/teacher/Home.vue',
  'src/views/company/CompanyHome.vue',
  'src/components/AnnouncementDetailDialog.vue'
]
const sources = Object.fromEntries(
  [...new Set([...publishFiles, ...viewFiles])].map((file) => [
    file,
    readFileSync(resolve(root, file), 'utf8')
  ])
)

test('admin and teacher announcement forms ask for neither expiry nor importance', () => {
  for (const file of publishFiles) {
    const source = sources[file]
    assert.ok(!source.includes('label="过期日期"'), `${file} still has expiry input`)
    assert.ok(!source.includes('label="优先级"'), `${file} still has importance input`)
    assert.ok(!/\bexpireDate\b/.test(source), `${file} still binds or submits expiry`)
    assert.ok(!/\bvalidTo\b/.test(source), `${file} still submits validTo`)
    assert.ok(!/\bpriority\s*:/.test(source), `${file} still submits or models importance`)
    assert.ok(!source.includes('v-model="formData.publishDate"'), `${file} still binds publish-date input`)
  }
})

test('announcement views do not display expiry or importance', () => {
  for (const file of viewFiles) {
    const source = sources[file]
    assert.ok(!source.includes('过期日期'), `${file} still displays expiry`)
    assert.ok(!/\bexpireDate\b|\bvalidTo\b/.test(source), `${file} still reads expiry fields`)
    assert.ok(!/priority-tag|priority\s*===|重要程度/.test(source), `${file} still displays importance`)
  }
})

test('home reminders render one horizontal publish-date/title/role/name row and open details', () => {
  const item = readFileSync(resolve(root, 'src/components/AnnouncementMessageItem.vue'), 'utf8')
  const rowStart = item.indexOf('<time class="message-date"')
  const rowEnd = item.indexOf('<span v-if="isAnnouncementUnread"', rowStart)
  const row = item.slice(rowStart, rowEnd)
  const dateIndex = row.indexOf('class="message-date"')
  const titleIndex = row.indexOf('class="message-title"')
  const roleIndex = row.indexOf('publisherRoleText')
  const nameIndex = row.indexOf('publisherNameText')
  assert.ok(dateIndex >= 0 && dateIndex < titleIndex && titleIndex < roleIndex && roleIndex < nameIndex)
  assert.match(item, /grid-template-columns:/)
  assert.match(item, /justify-content: flex-end/)

  const homeFiles = [
    'src/views/student/Home.vue',
    'src/views/student/Profile.vue',
    'src/views/teacher/Home.vue',
    'src/views/admin/Dashboard.vue',
    'src/views/company/CompanyHome.vue'
  ]
  for (const file of homeFiles) {
    const source = readFileSync(resolve(root, file), 'utf8')
    assert.match(source, /(?:Student)?AnnouncementMessageItem/)
    assert.match(source, /(?:Student)?AnnouncementDetailDialog|openAnnouncementDetail|handleViewAnnouncement/)
  }
})
