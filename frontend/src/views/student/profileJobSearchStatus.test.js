import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const viewsDirectory = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(viewsDirectory, 'Profile.vue'), 'utf8')

test('Profile.vue displays the derived job-search status beside the student name', () => {
  assert.match(source, /getStatusText\(internshipStatus\.status, internshipStatus\.jobSearchStatusText\)/)
  assert.match(source, /getStatusClass\(internshipStatus\.status, internshipStatus\.jobSearchStatus\)/)
  assert.match(source, /internshipStatus\.value\.jobSearchStatusText = response\.data\.jobSearchStatusText/)
  assert.match(source, /NOT_FOUND:\s*'default'/)
})
