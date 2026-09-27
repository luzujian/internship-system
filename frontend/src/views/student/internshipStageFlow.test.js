import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'

const jobs = fs.readFileSync(new URL('./Jobs.vue', import.meta.url), 'utf8')
const confirmation = fs.readFileSync(new URL('./InternshipConfirmationForm.vue', import.meta.url), 'utf8')
const internships = fs.readFileSync(new URL('./Internships.vue', import.meta.url), 'utf8')
const appraisal = fs.readFileSync(new URL('./InternshipAppraisal.vue', import.meta.url), 'utf8')
const home = fs.readFileSync(new URL('./Home.vue', import.meta.url), 'utf8')
const applications = fs.readFileSync(new URL('./Applications.vue', import.meta.url), 'utf8')
const profile = fs.readFileSync(new URL('./Profile.vue', import.meta.url), 'utf8')
const studentMenuConfig = fs.readFileSync(new URL('../../utils/studentMenuConfig.ts', import.meta.url), 'utf8')
const studentRouter = fs.readFileSync(new URL('../../router/studentRouter.ts', import.meta.url), 'utf8')
const permissionManagement = fs.readFileSync(new URL('../admin/PermissionManagement.vue', import.meta.url), 'utf8')
const settings = fs.readFileSync(new URL('../teacher/Settings.vue', import.meta.url), 'utf8')
const teacherSettingsApi = fs.readFileSync(new URL('../../api/teacherSettings.ts', import.meta.url), 'utf8')
const teacherStudentManagement = fs.readFileSync(new URL('../teacher/TeacherStudentManagement.vue', import.meta.url), 'utf8')
const teacherStudentApi = fs.readFileSync(new URL('../../api/teacherStudent.ts', import.meta.url), 'utf8')
const teacherApproval = fs.readFileSync(new URL('../teacher/Approval.vue', import.meta.url), 'utf8')
const companyApplicationView = fs.readFileSync(new URL('../company/ApplicationView.vue', import.meta.url), 'utf8')
const recruitmentManagement = fs.readFileSync(new URL('../company/RecruitmentManagement.vue', import.meta.url), 'utf8')
const positionService = fs.readFileSync(new URL('../../api/PositionService.ts', import.meta.url), 'utf8')
const positionStore = fs.readFileSync(new URL('../../store/position.ts', import.meta.url), 'utf8')
const appraisalCollection = fs.readFileSync(new URL('../teacher/InternshipAppraisalCollection.vue', import.meta.url), 'utf8')
const appraisalCollectionApi = fs.readFileSync(new URL('../../api/internshipAppraisalCollection.ts', import.meta.url), 'utf8')
const teacherRouter = fs.readFileSync(new URL('../../router/teacherRouter.ts', import.meta.url), 'utf8')
const adminSystemSettings = fs.readFileSync(new URL('../admin/SystemSettingsManagement.vue', import.meta.url), 'utf8')
const companyRegister = fs.readFileSync(new URL('../CompanyRegister.vue', import.meta.url), 'utf8')
const adminCompanyManagement = fs.readFileSync(new URL('../admin/CompanyManagement.vue', import.meta.url), 'utf8')
const teacherCompanyList = fs.readFileSync(new URL('../teacher/CompanyList.vue', import.meta.url), 'utf8')
const companyInternshipConfirm = fs.readFileSync(new URL('../company/InternshipConfirm.vue', import.meta.url), 'utf8')

test('job applications are independent from internship status and stage context', () => {
  assert.match(jobs, /ACTIVE_APPLICATION_STATUSES/)
  assert.match(jobs, /job\.isApplied = false/)
  assert.match(jobs, /studentJobApplicationService\.create\(applicationData\)/)
  assert.doesNotMatch(jobs, /studentStatus\s*</)
  assert.doesNotMatch(jobs, /statusLoaded/)
  assert.doesNotMatch(jobs, /stageNo|stageTwo|internship-flow/)
  assert.match(profile, /ACTIVE_APPLICATION_STATUSES/)
  assert.doesNotMatch(profile, /studentStatus\s*[<>=]|statusLoaded\s*&&\s*internshipStatus\.status/)
  assert.doesNotMatch(profile, /fetchStudentStatus/)
  assert.doesNotMatch(jobs, /remainingQuota\s*>\s*0|已招满|remainingQuota\s*===\s*0[\s\S]{0,80}splice/)
  assert.doesNotMatch(profile, /remainingQuota\s*>\s*0|已招满/)
  assert.match(jobs, /名额为 0 的岗位仍允许学生提交申请/)
})

test('confirmation uses one stable offer id and a per-record history timeline', () => {
  assert.match(confirmation, /\/student\/internship-confirmation\/offers/)
  assert.match(confirmation, /request\.post\('\/student\/internship-confirmation',\s*\{/)
  assert.match(confirmation, /offerId: offer\.offerId/)
  assert.match(confirmation, /当前实习/)
  assert.match(confirmation, /确认记录/)
  assert.match(confirmation, /historyStatusText\(record\)/)
  assert.doesNotMatch(confirmation, /applicationId:\s*selected/)
  assert.doesNotMatch(confirmation, /stageNo|stageAssignmentId|unitChange|stage-2|internship-flow/)
})

test('student-facing calendar is read-only and never gates job applications', () => {
  // 日历展示已从确认页移除，仅在鉴定/心得页作为只读参考；岗位申请不读取日历
  assert.doesNotMatch(confirmation, /getInternshipCalendar/)
  assert.match(appraisal, /getInternshipCalendar/)
  assert.doesNotMatch(jobs, /getInternshipCalendar|internship-calendar/)
})

test('reflection and appraisal pages use periods and placements instead of stages', () => {
  assert.doesNotMatch(internships, /stageNo|internship-flow|当前阶段|下一阶段/)
  assert.match(internships, /第\$\{response\.data\.currentPeriod\}期实习心得已提交，请等待下一期再提交/)
  assert.match(appraisal, /formData\.append\('placementId'/)
  assert.match(appraisal, /请先选择鉴定表对应的实习公司/)
  assert.match(appraisal, /\/student\/internship-confirmation\/history/)
  assert.match(appraisal, /getInternshipCalendar/)
  assert.match(appraisal, /internshipEnded\.value = response\.data\.internshipEnded === true/)
  assert.match(appraisal, /实习状态尚未显示为已结束，暂不能提交实习鉴定表/)
  assert.doesNotMatch(appraisal, /stageNo|stageAssignmentId|internship-flow/)
})

test('appraisal remains an independently configurable student menu and route', () => {
  // 菜单默认清单已从 StudentLayout 抽到 studentMenuConfig 模块（支持系统配置显隐）
  assert.match(studentRouter, /path: 'internship-appraisal'/)
  assert.match(studentRouter, /views\/student\/InternshipAppraisal\.vue/)
  assert.match(studentMenuConfig, /path: '\/student\/internship-appraisal', name: '实习鉴定'/)
  assert.match(permissionManagement, /path: '\/student\/internship-appraisal', name: '实习鉴定'/)
})

test('teacher appraisal collection keeps scoped APIs for all teacher identities', () => {
  assert.match(teacherRouter, /path: 'internship-appraisals'/)
  assert.match(teacherRouter, /ROLE_TEACHER_COLLEGE/)
  assert.match(teacherRouter, /ROLE_TEACHER_DEPARTMENT/)
  assert.match(teacherRouter, /ROLE_TEACHER_COUNSELOR/)
  assert.match(appraisalCollectionApi, /teacher\/internship-appraisals/)
  assert.doesNotMatch(appraisalCollectionApi, /counselorId|teacherId/)
  assert.match(appraisalCollection, /全部导出/)
})

test('teacher settings exposes one versioned global calendar', () => {
  assert.match(settings, /实习教学日历/)
  assert.match(settings, /calendar\.startDate/)
  assert.match(settings, /calendar\.endDate/)
  assert.match(settings, /calendar\.reportCycle/)
  assert.match(settings, /calendar\.appraisalDeadline/)
  assert.match(settings, /updateInternshipCalendar/)
  assert.match(teacherSettingsApi, /\/settings\/internship-calendar/)
  assert.match(teacherSettingsApi, /version: string \| null/)
  assert.match(settings, /appraisalDeadline <= calendar\.value\.endDate/)
  assert.match(settings, /鉴定表截止日期必须晚于参考结束日期/)
  assert.doesNotMatch(settings, /stageNo|双阶段|第一阶段|第二阶段|updateInternshipStages/)
})

test('teacher monitoring and approval no longer expose stage-two or unit-change workflows', () => {
  assert.doesNotMatch(teacherStudentManagement, /currentStage|awaitingStageTwo|stageTwoPending|待选择第二阶段|case 7/)
  assert.doesNotMatch(teacherApproval, /unitChange|单位变更/)
  assert.match(teacherStudentManagement, /\/teacher\/students\/\$\{student\.id\}\/internship-detail/)
})

test('teacher monitoring displays the stored company name for self-practice students', () => {
  assert.match(teacherStudentManagement, /const resolveInternshipCompanyName = \(item: any\): string =>/)
  assert.match(teacherStudentManagement, /if \(item\.companyName\) return item\.companyName/)
  assert.equal(
    (teacherStudentManagement.match(/company: resolveInternshipCompanyName\(item\)/g) || []).length,
    2
  )
})

test('teacher monitoring can filter registered and self-practice companies', () => {
  assert.match(teacherStudentManagement, /el-option-group v-if="companyList\.length" label="校企合作"/)
  assert.match(teacherStudentManagement, /el-option-group v-if="selfPracticeCompanyList\.length" label="自主实习"/)
  assert.match(teacherStudentManagement, /registeredNames\.has\(normalizedName\)/)
  assert.match(teacherStudentManagement, /selfPracticeNames\.has\(normalizedName\)/)
  assert.match(teacherStudentManagement, /replace\(\/\[，,。；;、\]\+\$\/g, ''\)/)
  assert.match(teacherStudentApi, /\/teacher\/internship-status\/company-options/)
})

test('delay workflow stays enabled while unit-change stays retired', () => {
  // 业务确认（2026-09-16）：考研延迟申请保持启用（首页入口 + 我的申请专属筛选），
  // 单位变更仍停用（确认页无入口，历史记录仅在"全部"中展示）
  assert.match(home, /openDelayDialog/)
  assert.match(home, /考研延迟申请/)
  assert.doesNotMatch(confirmation, /unitChange|单位变更申请/)
  assert.match(applications, /label:\s*["']考研延迟["']/)
  assert.equal(fs.existsSync(new URL('../../api/studentInternship.ts', import.meta.url)), false)
})

test('company recruitment UI has no stage or position-level internship dates', () => {
  assert.doesNotMatch(companyApplicationView, /stageNo|实习阶段/)
  assert.doesNotMatch(recruitmentManagement, /internshipStartDate|internshipEndDate|实习开始日期|实习结束日期/)
  assert.doesNotMatch(positionService, /internshipStartDate|internshipEndDate/)
  assert.doesNotMatch(positionStore, /internshipStartDate|internshipEndDate/)
})

test('company registration does not offer a cooperation mode or retired date window', () => {
  assert.doesNotMatch(adminSystemSettings, /dualSelectionStartDate|dualSelectionEndDate|双向选择时间段/)
  assert.doesNotMatch(companyRegister, /cooperationMode|校企双向选择|学生自主联系/)
  assert.doesNotMatch(companyRegister, /双向选择阶段|自动根据双向选择时间段/)
  assert.match(adminCompanyManagement, /校企双向选择/)
  assert.match(teacherCompanyList, /校企双向选择/)
})

test('company confirmation page resolves confirmation records instead of legacy status rows', () => {
  assert.match(companyInternshipConfirm, /getConfirmationRecords\(companyId\.value\)/)
  assert.match(companyInternshipConfirm, /companyConfirmStatus:\s*Number\(item\.status\)/)
  assert.match(companyInternshipConfirm, /confirmRecord\(row\.id\)/)
  assert.match(companyInternshipConfirm, /rejectRecord\(row\.id,\s*value\.trim\(\)\)/)
  assert.doesNotMatch(companyInternshipConfirm, /fetchInternshipStatuses|approveInternshipStatus|rejectInternshipStatus/)
  assert.doesNotMatch(companyInternshipConfirm, /继续确认|超额招聘/)
})
