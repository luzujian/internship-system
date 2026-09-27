import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  announcementAttachmentByteUrl,
  authorizedFileByteUrl,
  companyQualificationMaterialByteUrl,
  resourceDocumentByteUrl,
  studentApplicationMaterialByteUrl,
  studentProfileByteUrl
} from '../src/utils/authorizedFileUrl.ts'

test('resource and announcement bytes use record-bound authenticated routes', () => {
  const resourceUrl = resourceDocumentByteUrl(42)
  const announcementUrl = announcementAttachmentByteUrl(7, '申请材料 A?.pdf')
  assert.equal(resourceUrl, '/api/resource-documents/download/42')
  assert.equal(announcementUrl,
    '/api/announcements/download-attachment?announcementId=7&fileName=%E7%94%B3%E8%AF%B7%E6%9D%90%E6%96%99%20A%3F.pdf')
  assert.equal(authorizedFileByteUrl(resourceUrl), resourceUrl)
  assert.equal(authorizedFileByteUrl(announcementUrl), announcementUrl)
  assert.equal(authorizedFileByteUrl(studentProfileByteUrl('resume', 12)), '/api/student/profile/download/resume/12')
  assert.equal(authorizedFileByteUrl(studentProfileByteUrl('certificate', 13)), '/api/student/profile/download/certificate/13')
})

test('malicious keys and missing record identity never become fallback fetch URLs', () => {
  assert.equal(resourceDocumentByteUrl('../42'), '')
  assert.equal(resourceDocumentByteUrl(0), '')
  assert.equal(studentProfileByteUrl('resume', '../12'), '')
  assert.equal(announcementAttachmentByteUrl(7, ''), '')
  for (const unsafe of [
    'https://oss.example.com/resources/private.pdf',
    '/api/upload/preview/resources/private.pdf',
    '/api/uploads/private.pdf',
    'blob:https://app.example.com/file',
    'javascript:alert(1)',
    '//evil.example.com/api/resource-documents/download/42',
    '/api/announcements/download-attachment?announcementId=7&fileName=a.pdf&fileName=b.pdf',
    '/api/announcements/download-attachment?announcementId=7&fileName=a.pdf#fragment'
  ]) {
    assert.equal(authorizedFileByteUrl(unsafe), '', unsafe)
  }
})

test('application and company material bytes use canonical record routes only', () => {
  const own = studentApplicationMaterialByteUrl(71, '考研计划')
  const teacher = studentApplicationMaterialByteUrl(71, 'delayPlan', true)
  const audit = companyQualificationMaterialByteUrl(15, 'legalIdCard', 1)
  const admin = companyQualificationMaterialByteUrl(15, 'businessLicense', 0, true)
  for (const route of [own, teacher, audit, admin]) assert.equal(authorizedFileByteUrl(route), route)
  assert.equal(studentApplicationMaterialByteUrl(71, '../backup'), '')
  assert.equal(companyQualificationMaterialByteUrl(15, 'backups', 0), '')
  assert.equal(companyQualificationMaterialByteUrl(15, 'businessLicense', 1), '')
  assert.equal(authorizedFileByteUrl('/api/student/applications/71/materials/%2e%2e%2fbackup'), '')
  assert.equal(authorizedFileByteUrl('/api/approval/company-qualifications/15/materials/legalIdCard/2'), '')
})

test('preview component never reconstructs a generic OSS-key proxy', () => {
  const component = readFileSync(new URL('../src/components/FilePreviewDialog.vue', import.meta.url), 'utf8')
  assert.doesNotMatch(component, /\/api\/upload\/preview|\/api\/uploads\/|convertToProxyUrl/)
  assert.doesNotMatch(component, /fetch\s*\(\s*props\.fileUrl/)
  assert.match(component, /fetchAuthorizedFile\(byteUrl\.value\)/)
})

test('private application and company-audit materials are never opened from stored URLs', () => {
  const approval = readFileSync(new URL('../src/views/teacher/Approval.vue', import.meta.url), 'utf8')
  const companyAudit = readFileSync(new URL('../src/views/teacher/CompanyAudit.vue', import.meta.url), 'utf8')
  const companyManagement = readFileSync(new URL('../src/views/admin/CompanyManagement.vue', import.meta.url), 'utf8')
  const applications = readFileSync(new URL('../src/views/student/Applications.vue', import.meta.url), 'utf8')
  const profile = readFileSync(new URL('../src/views/student/Profile.vue', import.meta.url), 'utf8')

  assert.doesNotMatch(approval, /:src="(?:getCompanyDocumentUrl|currentApplication\.materials|previewUrl)/)
  assert.doesNotMatch(approval, /link\.href\s*=\s*url/)
  assert.doesNotMatch(companyAudit, /:src="(?:getImageUrl|url)"|:preview-src-list="getImageUrls/)
  assert.doesNotMatch(companyManagement, /pdfImage\(|getImageUrl\(businessLicenseUrl\)|getImageUrls\(company\.legalIdCard\)/)
  assert.doesNotMatch(applications, /:href="delayPlanUrl"|window\.open\(\s*(?:url|resume\.url|cert\.url)/)
  assert.doesNotMatch(profile, /\/api\/admin\/templates\/.*\/download/)
})
