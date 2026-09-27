import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import {
  formatAnnouncementTime,
  getPreviewText,
  getPublisherNameText,
  getPublisherRoleDisplay,
  getPublisherRoleTagType,
  getTargetText,
  parseAnnouncementAttachments
} from '../src/utils/studentAnnouncement.ts'

test('student announcement formatting is deterministic across pages', () => {
  assert.equal(getPreviewText('<p>第一行</p><br><p>第二行</p>'), '第一行 第二行')
  assert.equal(getPublisherRoleTagType('COLLEGE'), 'primary')
  assert.equal(getPublisherRoleTagType('管理员'), 'danger')
  assert.equal(formatAnnouncementTime(new Date(2026, 8, 27, 10, 8, 23)), '2026-09-27 10:08')
})

test('announcement targets and attachments are normalized from backend JSON', () => {
  assert.equal(
    getTargetText('["MAJOR"]', '{"majorIds":["12","18"]}'),
    '特定专业学生（12、18）'
  )
  assert.deepEqual(
    parseAnnouncementAttachments('[{"name":"报到须知.pdf","url":"metadata-only"}]'),
    [{ name: '报到须知.pdf', url: 'metadata-only' }]
  )
})

test('home and profile render announcements through the shared components', () => {
  const home = readFileSync(new URL('../src/views/student/Home.vue', import.meta.url), 'utf8')
  const profile = readFileSync(new URL('../src/views/student/Profile.vue', import.meta.url), 'utf8')

  for (const source of [home, profile]) {
    assert.match(source, /StudentAnnouncementMessageItem/)
    assert.match(source, /StudentAnnouncementDetailDialog/)
  }
  assert.match(profile, /width="min\(1100px, calc\(100vw - 32px\)\)"/)
  assert.match(profile, /min-height: 280px/)
})
test('publisher aliases render and every role uses the shared horizontal row', () => {
  assert.equal(getPublisherNameText({ publisher_name: '某某某' }), '某某某')
  assert.equal(getPublisherRoleDisplay({ publisher_role: '管理员' }), '管理员')

  const row = readFileSync(new URL('../src/components/AnnouncementMessageItem.vue', import.meta.url), 'utf8')
  assert.match(row, /class="message-date"[\s\S]*class="message-title"[\s\S]*class="publisher-info"/)
  assert.match(row, /justify-content: flex-end/)

  const rolePages = [
    '../src/views/student/Home.vue',
    '../src/views/teacher/Home.vue',
    '../src/views/company/CompanyHome.vue',
    '../src/views/admin/Dashboard.vue'
  ]
  for (const file of rolePages) {
    const source = readFileSync(new URL(file, import.meta.url), 'utf8')
    assert.match(source, /AnnouncementMessageItem/)
  }
})


test('home cards keep recent read notices and management pages share persistent read state', () => {
  const studentHome = readFileSync(new URL('../src/views/student/Home.vue', import.meta.url), 'utf8')
  const teacherHome = readFileSync(new URL('../src/views/teacher/Home.vue', import.meta.url), 'utf8')
  const teacherManagement = readFileSync(new URL('../src/views/teacher/AnnouncementView.vue', import.meta.url), 'utf8')
  const adminManagement = readFileSync(new URL('../src/views/admin/AnnouncementManagement.vue', import.meta.url), 'utf8')
  const announcementApi = readFileSync(new URL('../src/api/announcement.ts', import.meta.url), 'utf8')

  assert.match(studentHome, /notificationList\.value\.slice\(0, 5\)/)
  assert.match(studentHome, /markAnnouncementRead\(Number\(item\.id\)\)/)
  assert.match(teacherHome, /TeacherService\.getNotifications\(999\)/)
  assert.match(teacherHome, /recentAnnouncements\.slice\(0, 5\)/)
  assert.doesNotMatch(teacherHome, /pendingDismissId|handleViewDialogClosed/)
  assert.match(teacherManagement, /announcementApi\.getAnnouncementById/)
  assert.match(adminManagement, /全部公告/)
  assert.match(adminManagement, /我的公告/)
  assert.match(adminManagement, /handleTabChange/)
  assert.match(announcementApi, /\/announcements\/\$\{id\}\/read/)
})
