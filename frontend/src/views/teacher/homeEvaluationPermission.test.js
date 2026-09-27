import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'

const home = fs.readFileSync(new URL('./Home.vue', import.meta.url), 'utf8')
const permissions = fs.readFileSync(new URL('../../utils/teacherPermissions.ts', import.meta.url), 'utf8')

test('counselor pending reflection card follows the smart evaluation menu permission', () => {
  assert.match(home, /v-if="showPendingReflectionCard"/)
  assert.match(
    home,
    /teacherType\.value === 'COUNSELOR'[\s\S]*currentUserHasRoutePermission\('teacherEvaluation'\)/
  )
  assert.match(permissions, /'teacherEvaluation': 'menu:evaluation'/)
  assert.doesNotMatch(
    home,
    /handleCardClick\('evaluation'\)" v-if="teacherType === 'COUNSELOR'"/
  )
})
