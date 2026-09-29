import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'

const home = fs.readFileSync(new URL('./Home.vue', import.meta.url), 'utf8')
const authController = fs.readFileSync(
  new URL('../../../../backend/src/main/java/com/internship/controller/AuthController.java', import.meta.url),
  'utf8'
)

test('teacher welcome identity is available before the first home render', () => {
  assert.match(authController, /safeUser\.put\("teacherType", authenticatedTeacherType\)/)
  assert.match(home, /const teacherType = ref<string>\(getInitialTeacherType\(\)\)/)
  assert.match(home, /ROLE_TEACHER_COUNSELOR: 'COUNSELOR'/)
  assert.doesNotMatch(home, /const teacherType = ref<string>\(''\)/)
})

test('counselor-specific home decisions use the same first-render identity', () => {
  assert.match(home, /v-if="showPendingReflectionCard"/)
  assert.match(home, /v-if="teacherType !== 'COUNSELOR'"/)
  assert.match(home, /teacherType\.value === 'COUNSELOR'/)
  assert.match(home, /if \(teacherType\.value === 'COUNSELOR'\) return '负责班级实习状态占比'/)
})
