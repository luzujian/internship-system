import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'

const read = relativePath => fs.readFileSync(new URL(relativePath, import.meta.url), 'utf8')

const adminLayout = read('./admin/AdminLayout.vue')
const teacherLayout = read('./teacher/TeacherLayout.vue')
const studentLayout = read('./student/StudentLayout.vue')
const companyLayout = read('./company/CompanyLayout.vue')
const permissionGuard = read('../router/guards/permissionGuard.ts')
const studentMenuConfig = read('../utils/studentMenuConfig.ts')
const authStore = read('../store/auth/index.ts')

test('menus stay hidden until their permission source is ready', () => {
  assert.match(adminLayout, /<el-menu\s+v-if="adminPermissionsLoaded"/)
  assert.match(teacherLayout, /const menuLoading = ref\(true\)/)
  assert.match(teacherLayout, /<el-menu\s+v-if="!menuLoading"/)
  assert.match(studentLayout, /const studentMenus = ref<StudentMenuItem\[]>\(\[]\)/)
  assert.match(studentLayout, /<el-menu\s+v-if="studentMenusLoaded"/)
  assert.match(companyLayout, /v-if="companyMenuReady"/)
})

test('permission failures fall back to safe menus instead of full defaults', () => {
  assert.doesNotMatch(teacherLayout, /getDefaultMenus/)
  assert.match(teacherLayout, /dynamicMenus\.value = getSafeMenus\(\)/)
  assert.match(studentMenuConfig, /SAFE_STUDENT_PATHS/)
  assert.doesNotMatch(studentLayout, /studentMenus = ref\(\[\.\.\.defaultStudentMenus\]\)/)
})

test('configured menu permissions also protect direct route access', () => {
  assert.match(permissionGuard, /to\.meta\.permission/)
  assert.match(permissionGuard, /!authStore\.hasPermission\(requiredPermission\)/)
  assert.match(studentMenuConfig, /export async function canAccessStudentMenu/)
})

test('an empty admin permission response clears stale permissions', () => {
  assert.match(
    authStore,
    /if \(isAdminRole\(role\)\)[\s\S]*localStorage\.removeItem\(STORAGE_KEYS\.ADMIN_PERMISSIONS\)/
  )
})
