import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read = (name) => readFileSync(new URL(`./${name}`, import.meta.url), 'utf8')

test('学生首页使用日期分组语义化时间线和游标分页', () => {
  const home = read('Home.vue')

  assert.match(home, /<h3>最近动态<\/h3>/)
  assert.match(home, /<ol v-else class="activity-timeline"/)
  assert.match(home, /<time :datetime="item\.occurredAt \|\| undefined"/)
  assert.match(home, /<router-link[\s\S]*v-if="item\.action"/)
  assert.match(home, /const params = \{ limit: 10 \}/)
  assert.match(home, /params\.cursor = cursor/)
  assert.match(home, /progressLoading/)
  assert.match(home, /progressInitialError/)
  assert.match(home, /progressLoadMoreError/)
  assert.match(home, /还没有实习动态/)
})

test('首页监听最近动态 WebSocket 并在 200ms 防抖后刷新', () => {
  const home = read('Home.vue')
  const websocket = read('../../utils/websocket.ts')

  assert.match(websocket, /student_recent_activity_changed/)
  assert.match(websocket, /onStudentRecentActivityChanged/)
  assert.match(home, /onStudentRecentActivityChanged\(handleRecentActivityChanged\)/)
  assert.match(home, /}, 200\)/)
  assert.match(home, /window\.addEventListener\('focus', handleWindowFocus\)/)
})

test('动态深链可定位申请、面试、确认记录和心得', () => {
  assert.match(read('Applications.vue'), /route\.query\.applicationId/)
  assert.match(read('Interviews.vue'), /route\.query\.interviewId/)

  const confirmation = read('InternshipConfirmationForm.vue')
  assert.match(confirmation, /route\.query\.offerId/)
  assert.match(confirmation, /route\.query\.confirmationId/)
  assert.doesNotMatch(confirmation, /unitChangeId/)

  const internships = read('Internships.vue')
  assert.match(internships, /route\.query\.reflectionId/)
  assert.match(internships, /route\.query\.internshipId/)
  assert.doesNotMatch(internships, /stageAssignments|stageNo/)
})

test('学生首页最近动态骨架使用 4 项占位并标注加载中文案', () => {
  const home = read('Home.vue')
  assert.match(home, /v-for="index in 4"/)
  assert.match(home, /aria-label="正在加载最近动态"/)
})

test('学生首页最近动态空状态文案覆盖引导投递与浏览岗位', () => {
  const home = read('Home.vue')
  assert.match(home, /还没有实习动态/)
  assert.match(home, /投递岗位或提交申请后会显示在这里/)
  assert.match(home, /浏览岗位/)
})

test('学生首页最近动态首次加载失败时显示告警并提供重新加载', () => {
  const home = read('Home.vue')
  assert.match(home, /动态加载失败/)
  assert.match(home, /重新加载/)
  assert.match(home, /role="alert"/)
})

test('学生首页最近动态加载更多失败时提供重试入口', () => {
  const home = read('Home.vue')
  assert.match(home, /加载更多失败/)
  assert.match(home, /重试/)
})

test('学生首页最近动态布局 CSS 满足固定高度与可访问性要求', () => {
  const home = read('Home.vue')
  assert.match(home, /height:\s*540px/)
  assert.match(home, /min-height:\s*44px/)
  assert.match(home, /-webkit-line-clamp:\s*3/)
  assert.match(home, /overflow-x:\s*hidden/)
})

test('recentActivity actionDefinitions 覆盖全部跳转动作', () => {
  const recentActivity = read('../../utils/recentActivity.js')
  assert.match(recentActivity, /VIEW_APPLICATION:/)
  assert.match(recentActivity, /VIEW_APPLICATION_LIST:/)
  assert.match(recentActivity, /VIEW_INTERVIEW:/)
  assert.match(recentActivity, /START_CONFIRMATION:/)
  assert.match(recentActivity, /VIEW_CONFIRMATION:/)
  assert.match(recentActivity, /VIEW_REFLECTION:/)
  assert.match(recentActivity, /VIEW_INTERNSHIP:/)
  assert.match(recentActivity, /BROWSE_JOBS:/)
  assert.doesNotMatch(recentActivity, /MANAGE_UNIT_CHANGE:|BROWSE_STAGE_JOBS:/)
})
