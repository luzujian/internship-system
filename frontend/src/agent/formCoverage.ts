/**
 * 全系统表单覆盖清单（阶段四收口：明确的覆盖结果）。
 *
 * 口径（已和需求方确认）：
 * - 统计57个已注册表单；persistence区分真实服务端业务、浏览器模板与仅本页草稿；
 * - 搜索筛选条（只过滤列表）、只读详情弹窗、匿名页（无身份、无 AI 侧栏）、
 *   开关 / 勾选矩阵 / 穿梭框 / 二次确认框（非文本型可填字段）、口令类字段不接入；
 * - 本清单是唯一真相源：已接（SUBMIT_FORMS）+ 豁免（EXEMPT_FILES）构成闭环，
 *   未在清单出现的表单一律视为未完成，由 formCoverage.test.js 门禁保证。
 *
 * 路径均为相对 frontend/src 的路径。
 */

export interface SubmitFormEntry {
  /** 与 useFormContext.register(formId) 完全一致 */
  formId: string
  /** 表单标题（说明性标签） */
  title: string
  /** 注册所在文件 */
  file: string
  persistence: 'server' | 'browser' | 'page-only'
  validation: 'native' | 'business' | 'fields'
}

/** 已注册表单57个：55个服务端业务、1个浏览器模板、1个仅本页草稿；真实语音仍需端到端验收。 */
export const SUBMIT_FORMS: SubmitFormEntry[] = [
  // ===== 管理员端（28）=====
  { formId: 'admin:admin-user', title: '新增管理员', file: 'views/admin/AdminUserManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:admin-user-edit', title: '编辑管理员', file: 'views/admin/AdminUserManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:ai-model', title: 'AI 模型配置', file: 'views/admin/AIModelManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:announcement', title: '公告发布', file: 'views/admin/AnnouncementManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:backup', title: '自动备份配置', file: 'views/admin/BackupManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:center-profile', title: '个人中心资料编辑', file: 'views/admin/ProfileCenter.vue', persistence: 'server', validation: 'business' },
  { formId: 'admin:class', title: '班级新增/编辑', file: 'views/admin/ClassManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:company', title: '企业信息维护', file: 'views/admin/CompanyManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:dashboard-announcement', title: '发布公告', file: 'views/admin/Dashboard.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:dashboard-resource', title: '上传资源', file: 'views/admin/Dashboard.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:department', title: '院系维护', file: 'views/admin/DepartmentMajorManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:division', title: '新增系室', file: 'views/admin/DepartmentMajorManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:division-edit', title: '编辑系室', file: 'views/admin/DepartmentMajorManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:feedback', title: '问题反馈回复', file: 'views/admin/FeedbackManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:keyword', title: '关键词库维护', file: 'views/admin/KeywordLibraryManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:log-retention', title: '日志留存配置', file: 'views/admin/LogManagement.vue', persistence: 'server', validation: 'business' },
  { formId: 'admin:major', title: '新增专业', file: 'views/admin/MajorManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:major-edit', title: '编辑专业', file: 'views/admin/MajorManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:major-org', title: '新增专业（组织架构）', file: 'views/admin/DepartmentMajorManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:major-org-edit', title: '编辑专业（组织架构）', file: 'views/admin/DepartmentMajorManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:position-category', title: '岗位类别维护', file: 'views/admin/PositionCategoryManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:profile-edit', title: '管理员资料编辑', file: 'views/admin/AdminLayout.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:resource', title: '资源文档', file: 'views/admin/ResourceDocumentManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:score-weights', title: '评分权重', file: 'views/admin/ScoringRuleManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:scoring-rule', title: '评分规则', file: 'views/admin/ScoringRuleManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:security', title: '安全设置', file: 'views/admin/SystemSettingsManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:student', title: '学生新增/编辑', file: 'views/admin/StudentUserManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'admin:teacher', title: '教师新增/编辑', file: 'views/admin/TeacherUserManagement.vue', persistence: 'server', validation: 'native' },
  // ===== 企业端（6）=====
  { formId: 'company:account', title: '企业资料', file: 'views/company/AccountSettings.vue', persistence: 'server', validation: 'native' },
  { formId: 'company:contact', title: '企业联系人', file: 'components/company/CompanyContacts.vue', persistence: 'server', validation: 'native' },
  { formId: 'company:info', title: '企业基本信息', file: 'views/company/CompanyInfo.vue', persistence: 'server', validation: 'native' },
  { formId: 'company:interview-template', title: '面试信息模板', file: 'views/company/RecruitmentManagement.vue', persistence: 'browser', validation: 'native' },
  { formId: 'company:position', title: '岗位发布/编辑', file: 'views/company/RecruitmentManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'company:promotion', title: '企业宣传资料', file: 'views/company/CompanyInfo.vue', persistence: 'server', validation: 'fields' },
  // ===== 学生端（11）=====
  { formId: 'confirmation:recall', title: '撤回实习确认', file: 'views/student/InternshipConfirmationForm.vue', persistence: 'server', validation: 'business' },
  { formId: 'confirmation:remark', title: '实习确认补充说明', file: 'views/student/InternshipConfirmationForm.vue', persistence: 'server', validation: 'business' },
  { formId: 'feedback:submit', title: '问题反馈', file: 'components/FeedbackSubmitDialog.vue', persistence: 'server', validation: 'native' },
  { formId: 'home:delay', title: '考研延迟申请', file: 'views/student/Home.vue', persistence: 'server', validation: 'business' },
  { formId: 'home:self-practice', title: '自主实习申请', file: 'views/student/Home.vue', persistence: 'server', validation: 'business' },
  { formId: 'jobs:apply', title: '岗位投递', file: 'views/student/Jobs.vue', persistence: 'server', validation: 'native' },
  { formId: 'profile:apply', title: '岗位投递', file: 'views/student/Profile.vue', persistence: 'server', validation: 'native' },
  { formId: 'profile:bind', title: '绑定手机/邮箱', file: 'views/student/Profile.vue', persistence: 'server', validation: 'business' },
  { formId: 'reflection:log', title: '实习心得', file: 'views/student/Internships.vue', persistence: 'server', validation: 'business' },
  { formId: 'registration:internship', title: '实习去向登记', file: 'views/student/InternshipRegistration.vue', persistence: 'page-only', validation: 'native' },
  // ===== 教师端（16）=====
  { formId: 'teacher:account-email', title: '换绑邮箱', file: 'views/teacher/AccountSettings.vue', persistence: 'server', validation: 'business' },
  { formId: 'teacher:account-phone', title: '换绑手机', file: 'views/teacher/AccountSettings.vue', persistence: 'server', validation: 'business' },
  { formId: 'teacher:announcement', title: '公告发布', file: 'views/teacher/AnnouncementView.vue', persistence: 'server', validation: 'native' },
  { formId: 'teacher:approval-reject', title: '审核驳回', file: 'views/teacher/Approval.vue', persistence: 'server', validation: 'business' },
  { formId: 'teacher:company-audit', title: '企业注册审核', file: 'views/teacher/CompanyAudit.vue', persistence: 'server', validation: 'business' },
  { formId: 'teacher:company-visit', title: '登记企业走访', file: 'components/teacher/CompanyRelationsPanel.vue', persistence: 'server', validation: 'business' },
  { formId: 'teacher:school-liaison', title: '添加校内对接人', file: 'components/teacher/CompanyRelationsPanel.vue', persistence: 'server', validation: 'business' },
  { formId: 'teacher:evaluation', title: '心得批阅/评分', file: 'views/teacher/Evaluation.vue', persistence: 'server', validation: 'business' },
  { formId: 'teacher:keyword', title: '关键词库', file: 'views/teacher/CounselorKeywordLibraryManagement.vue', persistence: 'server', validation: 'native' },
  { formId: 'teacher:reminder', title: '发送邮件提醒', file: 'views/teacher/TeacherStudentManagement.vue', persistence: 'server', validation: 'business' },
  { formId: 'teacher:reminder-batch', title: '一键邮件提醒', file: 'views/teacher/TeacherStudentManagement.vue', persistence: 'server', validation: 'business' },
  { formId: 'teacher:resource', title: '资源上传/发布', file: 'views/teacher/Resources.vue', persistence: 'server', validation: 'native' },
  { formId: 'teacher:score-weights', title: '评分权重', file: 'views/teacher/CounselorScoringRuleManagement.vue', persistence: 'server', validation: 'business' },
  { formId: 'teacher:scoring-rule', title: '评分规则', file: 'views/teacher/CounselorScoringRuleManagement.vue', persistence: 'server', validation: 'native' },
]

export type ExemptReason =
  | 'anonymous' // 匿名页：无登录身份、无 AI 侧栏，天然接不了
  | 'search-only' // 只有搜索筛选 + 只读详情/预览/导出，无提交类表单
  | 'no-form' // 布局壳 / 看板 / 占位空页，无表单
  | 'unsupported-widget' // 开关 / 勾选矩阵 / 穿梭框 / 二次确认框，非文本型可填字段
  | 'password' // 口令类字段，禁止走语音/AI（安全红线）
  | 'internal-tool' // 内部测试/调试页，非业务表单

export interface ExemptEntry {
  file: string
  reason: ExemptReason
  detail: string
}

/** 明确豁免不接入的页面 / 组件：原因写死，新增提交类表单时必须移出本表并接入。 */
export const EXEMPT_FILES: ExemptEntry[] = [
  { file: 'components/agent/AutomationVerification.vue', reason: 'unsupported-widget', detail: '管理员根据邮件服务记录核实不确定投递，凭据与确认必须手工填写，不接受 AI 自动填表' },
  // ===== 匿名页（5）=====
  { file: 'views/Login.vue', reason: 'anonymous', detail: '登录页：匿名访问，无 AI 侧栏' },
  { file: 'views/CompanyRegister.vue', reason: 'anonymous', detail: '企业注册页：匿名访问，无 AI 侧栏' },
  { file: 'views/CompanyCheck.vue', reason: 'anonymous', detail: '注册状态查询页：匿名访问，无 AI 侧栏' },
  { file: 'views/ForgotPassword.vue', reason: 'anonymous', detail: '忘记密码页：匿名访问，无 AI 侧栏' },
  { file: 'views/NotFound.vue', reason: 'anonymous', detail: '404 页：无表单' },
  // ===== 布局壳 / 看板 / 空页（10）=====
  { file: 'views/company/CompanyLayout.vue', reason: 'no-form', detail: '企业端布局壳：问题反馈由 FeedbackSubmitDialog 子组件注册' },
  { file: 'views/student/StudentLayout.vue', reason: 'no-form', detail: '学生端布局壳：全局提醒弹窗为只读通知，问题反馈由 FeedbackSubmitDialog 子组件注册' },
  { file: 'views/teacher/TeacherLayout.vue', reason: 'no-form', detail: '教师端布局壳：问题反馈由 FeedbackSubmitDialog 子组件注册' },
  { file: 'views/student/Dashboard.vue', reason: 'no-form', detail: '学生仪表盘：纯展示 + 跳转' },
  { file: 'views/student/AnnouncementView.vue', reason: 'no-form', detail: '学生公告查看：纯展示' },
  { file: 'views/student/InternshipManagement.vue', reason: 'no-form', detail: '占位空页：功能开发中' },
  { file: 'views/student/Test.vue', reason: 'internal-tool', detail: '开发自检页：非业务' },
  { file: 'views/teacher/Dashboard.vue', reason: 'no-form', detail: '教师仪表盘：纯展示' },
  { file: 'views/teacher/Reports.vue', reason: 'no-form', detail: '报表页：纯展示 + 导出，无提交表单' },
  { file: 'views/admin/DataStatisticsManagement.vue', reason: 'no-form', detail: '数据统计页：时间筛选 + 图表展示' },
  // ===== 搜索 + 只读详情（13）=====
  { file: 'views/student/Applications.vue', reason: 'search-only', detail: '筛选 + 申请详情只读弹窗 + 撤回二次确认框' },
  { file: 'views/student/Interviews.vue', reason: 'search-only', detail: '筛选 + 面试详情只读弹窗' },
  { file: 'views/company/ApplicationView.vue', reason: 'search-only', detail: '搜索 + 导出 + 行级同意/拒绝/录用二次确认框' },
  { file: 'views/company/CompanyHome.vue', reason: 'search-only', detail: '看板 + 公告详情只读弹窗' },
  { file: 'views/company/InternshipConfirm.vue', reason: 'search-only', detail: '搜索 + 确认/拒绝行操作（无文本表单）' },
  { file: 'views/company/InternshipStudents.vue', reason: 'search-only', detail: '当前实习学生名单与搜索，只读页面' },
  { file: 'views/teacher/CompanyList.vue', reason: 'search-only', detail: '搜索 + 企业详情只读弹窗' },
  { file: 'views/teacher/Home.vue', reason: 'search-only', detail: '看板 + 心得详情只读弹窗 + 文件预览' },
  { file: 'views/student/InternshipAppraisal.vue', reason: 'unsupported-widget', detail: '上传实习鉴定材料与手动评分，实习公司选择不作为 AI 填表对象' },
  { file: 'views/teacher/InternshipAppraisalCollection.vue', reason: 'search-only', detail: '筛选 + 鉴定详情抽屉（只读）' },
  { file: 'views/admin/ApplicationWithdrawalRecordManagement.vue', reason: 'search-only', detail: '搜索 + 撤回记录详情只读弹窗' },
  { file: 'views/admin/InternshipConfirmApplication.vue', reason: 'search-only', detail: '搜索 + 确认表详情只读弹窗' },
  { file: 'views/admin/RecruitmentManagement.vue', reason: 'search-only', detail: '搜索 + 岗位/申请详情只读弹窗' },
  { file: 'views/admin/AITest.vue', reason: 'internal-tool', detail: 'AI 联调测试页：模型选择器为内部工具' },
  { file: 'views/teacher/Settings.vue', reason: 'unsupported-widget', detail: '普通教学日历设置页，教师手动调整并保存，不关联 AI 填表' },
  { file: 'views/teacher/CounselorAISettings.vue', reason: 'unsupported-widget', detail: '单个 AI 开关自动保存，无文本可填字段' },
  // ===== 组件类型豁免（3）=====
  { file: 'views/teacher/TeacherClassManagement.vue', reason: 'unsupported-widget', detail: '添加班级用穿梭框多选 + 学生列表只读表，无文本型可填字段' },
  { file: 'views/admin/PermissionManagement.vue', reason: 'unsupported-widget', detail: 'AI 总开关 + 能力勾选矩阵 + 权限树，均为开关/勾选型配置' },
  { file: 'components/UserProfile.vue', reason: 'password', detail: '修改密码表单涉及口令安全，不接入语音填表（教师 Profile 页复用本组件）' },
]
