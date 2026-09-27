import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const profile = readFileSync(new URL('../src/views/student/Profile.vue', import.meta.url), 'utf8')
const jobs = readFileSync(new URL('../src/views/student/Jobs.vue', import.meta.url), 'utf8')

test('pickers and drop zones stage files without an upload board', () => {
  assert.match(profile, /resumeList\.length === 0 && resumeFileList\.length === 0/)
  assert.match(profile, /certificateList\.length === 0 && certificateFileList\.length === 0/)
  assert.match(profile, /v-for="\(pendingResume, index\) in resumeFileList"/)
  assert.match(profile, /v-for="\(pendingCertificate, index\) in certificateFileList"/)
  assert.match(profile, /type="warning"[^>]*>待上传</)

  assert.match(profile, /@click="openResumeFilePicker"/)
  assert.match(profile, /@click="openCertificateFilePicker"/)
  assert.match(profile, /@drop\.prevent="handleResumeDrop"/)
  assert.match(profile, /@drop\.prevent="handleCertificateDrop"/)
  assert.match(profile, /class="hidden-file-input"[^>]*type="file"/s)

  assert.doesNotMatch(profile, /class="upload-area"/)
  assert.doesNotMatch(profile, /<el-upload/)
})

test('one dialog footer button confirms every staged material', () => {
  const template = profile.slice(0, profile.indexOf('<script setup'))
  const confirmLabels = template.match(/>\s*确认上传\s*</g) || []
  assert.equal(confirmLabels.length, 1)
  assert.match(template, /class="resume-management-footer"/)
  assert.match(template, /@click="confirmMaterialUpload"/)

  const selectionHandlers = profile.slice(
    profile.indexOf('const openResumeFilePicker = () =>'),
    profile.indexOf('const uploadMaterialFile = async')
  )
  assert.doesNotMatch(selectionHandlers, /request\.post/)

  const confirmHandler = profile.slice(
    profile.indexOf('const uploadMaterialFile = async'),
    profile.indexOf('// 加载我的简历列表')
  )
  assert.match(confirmHandler, /request\.post\(endpoint/)
  assert.match(confirmHandler, /kind === 'resume'/)
  assert.match(confirmHandler, /for \(const uploadFile of pendingResumes\)/)
  assert.match(confirmHandler, /for \(const uploadFile of pendingCertificates\)/)
  assert.doesNotMatch(profile, /const uploadResume = async/)
  assert.doesNotMatch(profile, /const uploadCertificate = async/)
})

test('application forms load saved materials before opening', () => {
  const profileApply = profile.slice(profile.indexOf('const applyJob = async (job) =>'), profile.indexOf('const goToMaterialManagement'))
  const jobsApply = jobs.slice(jobs.indexOf('const applyJob = async (job) =>'), jobs.indexOf('const goToMaterialManagement'))

  assert.ok(profileApply.indexOf('await loadStudentMaterials()') < profileApply.indexOf('showApplicationDialog.value = true'))
  assert.ok(jobsApply.indexOf('await loadStudentMaterials()') < jobsApply.indexOf('showApplicationDialog.value = true'))
})
