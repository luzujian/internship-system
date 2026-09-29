import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const viewsDirectory = dirname(fileURLToPath(import.meta.url))

for (const viewFile of ['Jobs.vue', 'Profile.vue']) {
  test(`${viewFile} does not render job statistics in the detail dialog`, () => {
    const source = readFileSync(resolve(viewsDirectory, viewFile), 'utf8')

    assert.doesNotMatch(source, /职位统计/)
    assert.doesNotMatch(source, /\.stats-info\b/)
    assert.doesNotMatch(source, /\.stat-item\b/)
    assert.doesNotMatch(source, /\.stat-label\b/)
    assert.doesNotMatch(source, /\.stat-value\b/)
  })

  test(`${viewFile} conditionally renders the detail footer`, () => {
    const source = readFileSync(resolve(viewsDirectory, viewFile), 'utf8')
    const footerOpeningTag = source.match(/<div(?=[^>]*class="detail-footer")[^>]*>/)?.[0]

    assert.ok(footerOpeningTag)
    assert.match(footerOpeningTag, /\bv-if="/)
  })

  test(`${viewFile} keeps only job details, introduction, and application materials in the application dialog`, () => {
    const source = readFileSync(resolve(viewsDirectory, viewFile), 'utf8')
    const applicationDialog = source.match(/<el-dialog\b[^>]*class="application-dialog"[^>]*>[\s\S]*?<\/el-dialog>/)?.[0]

    assert.ok(applicationDialog)
    assert.doesNotMatch(applicationDialog, /基本信息|学生姓名|学号|专业|年级|联系电话/)
    assert.match(applicationDialog, /应聘单位/)
    assert.match(applicationDialog, /岗位类别/)
    assert.match(applicationDialog, /岗位名称/)
    assert.match(applicationDialog, /自我介绍/)
    assert.match(applicationDialog, /上传简历和证书/)
    assert.match(applicationDialog, /去个人中心上传/)
    assert.match(applicationDialog, /<el-link[^>]*material-upload-link/)
    assert.doesNotMatch(applicationDialog, /<el-button[^>]*去个人中心上传/)
    assert.match(applicationDialog, /goToMaterialManagement/)
    assert.match(source, /const goToMaterialManagement/)
    assert.match(source, /\.application-dialog \.material-upload-link\s*\{[\s\S]*vertical-align:\s*baseline/)
    assert.match(source, /\.application-dialog \.material-upload-link\s*\{[\s\S]*line-height:\s*24px/)
    assert.doesNotMatch(source, /applicationRules/)
  })
}
