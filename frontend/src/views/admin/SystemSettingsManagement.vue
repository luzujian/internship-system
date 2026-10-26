<template>
  <div class="system-settings-container">
    <div class="settings-header">
      <div class="page-title">
        <el-icon class="title-icon">
          <Setting />
        </el-icon>
        <span>系统参数配置</span>
      </div>
    </div>

    <div class="settings-content">
      <el-card class="settings-card" shadow="hover">
        <el-tabs v-model="activeTab" class="settings-tabs" v-loading="loading">
        <el-tab-pane label="基础配置" name="basic">
          <div class="basic-grid">
            <!-- 系统状态 -->
            <section class="cfg-panel">
              <div class="cfg-panel-head">
                <div class="cfg-panel-title">
                  <el-icon><Monitor /></el-icon>
                  <span>系统状态</span>
                </div>
                <el-tag :type="basicForm.systemStatus === 1 ? 'success' : 'warning'" size="small" effect="light">
                  {{ basicForm.systemStatus === 1 ? '正常运行中' : '维护模式' }}
                </el-tag>
              </div>
              <el-form :model="basicForm" :rules="basicRules" ref="basicFormRef" label-width="0" class="cfg-form">
                <el-form-item prop="systemStatus">
                  <el-radio-group v-model="basicForm.systemStatus">
                    <el-radio-button :value="1">正常运行</el-radio-button>
                    <el-radio-button :value="0">维护模式</el-radio-button>
                  </el-radio-group>
                </el-form-item>
              </el-form>
              <p class="cfg-tip">维护模式下普通用户无法登录，管理员仍可正常访问系统。</p>
              <div class="cfg-actions">
                <el-button v-if="authStore.hasPermission('system:settings:view')" type="primary" size="small" @click="saveSystemStatus" :loading="savingStatus">保存状态</el-button>
              </div>
            </section>

            <!-- 系统邮件提醒 -->
            <section class="cfg-panel">
              <div class="cfg-panel-head">
                <div class="cfg-panel-title">
                  <el-icon><Message /></el-icon>
                  <span>系统邮件提醒</span>
                </div>
                <el-switch v-model="emailForm.enabled" inline-prompt :active-text="'开'" :inactive-text="'关'" />
              </div>
              <p class="cfg-tip">
                开启后，勾选的业务发生审核结果或收到新问题反馈时会向相关人员发送邮件提醒；问题反馈提醒发送给已启用且设置了邮箱的管理员。关闭后不再发送业务提醒邮件，<b>不影响系统其他功能正常使用</b>。
              </p>
              <el-checkbox-group v-model="emailForm.scopes" class="scope-group" :class="{ 'scope-disabled': !emailForm.enabled }" :disabled="!emailForm.enabled">
                <el-checkbox v-for="opt in emailScopeOptions" :key="opt.code" :value="opt.code" class="scope-item">
                  {{ opt.label }}
                </el-checkbox>
              </el-checkbox-group>
              <div class="cfg-actions">
                <el-button size="small" plain @click="sendTestEmail" :loading="testingEmail">发送测试邮件</el-button>
                <el-button v-if="authStore.hasPermission('system:settings:view')" type="primary" size="small" @click="saveEmailNotify" :loading="savingEmail">保存邮件设置</el-button>
              </div>
            </section>
          </div>

          <!-- 数据管理（危险操作区） -->
          <section class="cfg-panel danger-panel">
            <div class="cfg-panel-head">
              <div class="cfg-panel-title danger">
                <el-icon><WarningFilled /></el-icon>
                <span>数据管理</span>
              </div>
              <el-button v-if="authStore.hasPermission('system:settings:view')" type="danger" size="small" plain @click="showClearDataDialog">
                <el-icon><Delete /></el-icon>&nbsp;清除数据
              </el-button>
            </div>
            <p class="cfg-tip danger-tip">清除数据为危险操作：将永久删除所选模块的数据且不可恢复，建议操作前先前往备份页面完成备份。</p>
          </section>
        </el-tab-pane>

        <el-tab-pane label="安全配置" name="security">
          <el-form :model="securityForm" :rules="securityRules" ref="securityFormRef" label-width="150px" class="settings-form security-grid">
            <el-form-item label="密码最小长度" prop="minPasswordLength">
              <el-input-number v-model="securityForm.minPasswordLength" :min="6" :max="20" />
              <div class="form-tip">用户密码的最小长度要求</div>
            </el-form-item>

            <el-form-item label="密码复杂度" prop="passwordComplexity">
              <div class="complexity-card">
                <el-checkbox-group v-model="securityForm.passwordComplexity">
                  <el-checkbox label="lowercase">小写字母</el-checkbox>
                  <el-checkbox label="uppercase">大写字母</el-checkbox>
                  <el-checkbox label="number">数字</el-checkbox>
                  <el-checkbox label="special">特殊字符</el-checkbox>
                </el-checkbox-group>
              </div>
              <div class="form-tip">密码必须包含的字符类型</div>
            </el-form-item>

            <el-form-item label="最大登录失败次数" prop="maxLoginAttempts">
              <el-input-number v-model="securityForm.maxLoginAttempts" :min="3" :max="10" />
              <div class="form-tip">连续登录失败达到此次数后将锁定账号</div>
            </el-form-item>

            <el-form-item label="账号锁定时间(分钟)" prop="lockTime">
              <el-input-number v-model="securityForm.lockTime" :min="5" :max="1440" />
              <div class="form-tip">账号锁定后的解锁时间</div>
            </el-form-item>

            <el-form-item label="会话超时时间(分钟)" prop="sessionTimeout">
              <el-input-number v-model="securityForm.sessionTimeout" :min="15" :max="480" :step="15" />
              <div class="form-tip">用户无操作自动退出的时间</div>
            </el-form-item>

            <el-form-item>
              <div class="button-group">
                <el-button v-if="authStore.hasPermission('system:settings:view')" type="primary" @click="saveSecuritySettings" :loading="saving">保存安全配置</el-button>
                <el-button v-if="authStore.hasPermission('system:settings:view')" @click="resetSecuritySettings">重置</el-button>
              </div>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      </el-card>
    </div>

    <!-- 清除数据对话框 -->
    <el-dialog v-model="clearDataDialogVisible" title="清除数据" width="700px" :close-on-click-modal="false">
      <div class="clear-data-container">
        <div class="warning-box">
          <el-icon class="warning-icon"><WarningFilled /></el-icon>
          <div class="warning-content">
            <div class="warning-title">危险操作警告</div>
            <div class="warning-text">此操作将永久删除所选页面的所有数据，且不可恢复！</div>
            <div class="warning-tip-wrapper">
              <span class="warning-tip">建议在清除数据前先进行数据备份，以免造成数据丢失。</span>
              <span @click="goToBackupPage" class="backup-link">
                前往备份
                <el-icon><ArrowRight /></el-icon>
              </span>
            </div>
          </div>
        </div>

        <div v-if="clearing" class="progress-section">
          <div class="section-title">清除进度：</div>
          <el-progress 
            :percentage="clearProgress" 
            :status="clearProgressStatus"
            :stroke-width="20"
            :text-inside="true"
          />
          <div class="progress-info">
            <span class="current-module">正在清除：{{ currentClearingModule }}</span>
            <span class="progress-text">{{ clearedCount }} / {{ totalCount }} 个模块</span>
          </div>
        </div>

        <div class="selection-section">
          <div class="section-header">
            <div class="section-title">选择需要清除数据的模块：</div>
            <el-checkbox 
              v-model="selectAllChecked" 
              :indeterminate="isIndeterminate"
              class="select-all-checkbox"
            >
              全选
            </el-checkbox>
          </div>
          <el-checkbox-group v-model="selectedPages" class="page-selection">
            <el-checkbox v-for="page in availablePages" :key="page.value" :label="page.value">
              {{ page.label }}
            </el-checkbox>
          </el-checkbox-group>
        </div>

        <div class="password-section">
          <div class="section-title">安全验证：</div>
          <el-form :model="{ clearPassword }" label-width="100px">
            <el-form-item label="登录密码" :error="clearPasswordError">
              <el-input 
                v-model="clearPassword" 
                type="password" 
                placeholder="请输入您的登录密码以确认操作" 
                show-password
                @input="clearPasswordError = ''"
              />
            </el-form-item>
          </el-form>
        </div>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="cancelClearData">取消</el-button>
          <el-button type="danger" @click="clearSelectedData" :loading="clearing">确认清除</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import logger from '@/utils/logger'
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { Delete, WarningFilled, Setting, ArrowRight, Monitor, Message } from '@element-plus/icons-vue'
import request from '../../utils/request'
import { useSystemSettingsStore } from '../../store/systemSettings'
import { useAuthStore } from '../../store/auth'
import { useFormContext } from '@/agent/formContext'
import AdminUserService from '../../api/AdminUserService'
import StudentUserService from '../../api/StudentUserService'
import TeacherUserService from '../../api/TeacherUserService'
import ClassService from '../../api/class'
import MajorService from '../../api/major'
import DepartmentService from '../../api/department'
import companyService from '../../api/company'
import PositionCategoryService from '../../api/positionCategory'
import positionService from '../../api/position'
import * as announcementApi from '../../api/announcement'
import * as feedbackApi from '../../api/problemFeedback'
import * as resourceDocumentApi from '../../api/resourceDocument'
import recruitmentService from '../../api/recruitment'
import aiModelService from '../../api/aiModel'
import internshipService from '../../api/internship'
import type { FormFieldDescriptor } from '@/agent/types'
// 系统设置载荷精确局部接口（/admin/settings 返回体 data 形态）
interface SystemSettingsPayload {
  basic?: {
    systemStatus?: number | string | null
    emailNotifyEnabled?: number | string | boolean | null
    emailNotifyScopes?: string[] | string | null
  }
  emailNotifyScopeOptions?: { code: string; label: string }[]
  security?: {
    minPasswordLength?: number
    passwordComplexity?: string | string[]
    maxLoginAttempts?: number
    lockTime?: number
    sessionTimeout?: number
    enableTwoFactor?: number
  }
}
// 清除数据各列表项最小行接口（仅取 id）
interface ClearableRow {
  id?: string | number
}

const router = useRouter()
const authStore = useAuthStore()
const systemSettingsStore = useSystemSettingsStore()

const goToBackupPage = () => {
  router.push('/admin/backup')
}

const activeTab = ref('basic')
const saving = ref(false)
const savingStatus = ref(false)
const clearing = ref(false)
const loading = ref(false)

const basicFormRef = ref(null)
const securityFormRef = ref(null)

const basicForm = reactive({
  systemStatus: 1
})

// ===== 系统邮件提醒（基础配置）=====
// 与后端 EmailNotifyScope 枚举保持一致；接口返回 emailNotifyScopeOptions 时以接口为准
const DEFAULT_EMAIL_SCOPES = ['COMPANY_AUDIT', 'STUDENT_APPLICATION', 'JOB_APPLICATION', 'INTERNSHIP_CONFIRMATION', 'PROBLEM_FEEDBACK']
const emailForm = reactive({
  enabled: true,
  scopes: [...DEFAULT_EMAIL_SCOPES] as string[]
})
const emailScopeOptions = ref<{ code: string; label: string }[]>([
  { code: 'COMPANY_AUDIT', label: '企业注册审核结果' },
  { code: 'STUDENT_APPLICATION', label: '学生申请审核结果' },
  { code: 'JOB_APPLICATION', label: '岗位申请结果' },
  { code: 'INTERNSHIP_CONFIRMATION', label: '实习确认结果' },
  { code: 'PROBLEM_FEEDBACK', label: '问题反馈提醒' }
])
const savingEmail = ref(false)
const testingEmail = ref(false)

const securityForm = reactive({
  minPasswordLength: 6,
  passwordComplexity: ['lowercase', 'number'] as string[],
  maxLoginAttempts: 5,
  lockTime: 30,
  sessionTimeout: 120,
  enableTwoFactor: 0
})

const basicRules = {
  systemStatus: [
    { required: true, message: '请选择系统状态', trigger: 'change' }
  ]
}

const securityRules = {
  minPasswordLength: [
    { required: true, message: '请输入密码最小长度', trigger: 'blur' }
  ],
  passwordComplexity: [
    { type: 'array', required: true, message: '请选择密码复杂度', trigger: 'change' }
  ],
  maxLoginAttempts: [
    { required: true, message: '请输入最大登录失败次数', trigger: 'blur' }
  ],
  lockTime: [
    { required: true, message: '请输入账号锁定时间', trigger: 'blur' }
  ],
  sessionTimeout: [
    { required: true, message: '请输入会话超时时间', trigger: 'blur' }
  ]
}

// ===== 智能填表：安全设置页面表单（页面级常驻；密码复杂度为 checkbox 需手动勾选，不声明） =====
const formContext = useFormContext()
const securityFillFields: FormFieldDescriptor[] = [
  { prop: 'minPasswordLength', label: '密码最小长度', type: 'number', valueType: 'number', required: true,
    patternHint: '密码复杂度（大小写/数字/特殊字符复选框）需手动勾选' },
  { prop: 'maxLoginAttempts', label: '最大登录失败次数', type: 'number', valueType: 'number', required: true },
  { prop: 'lockTime', label: '账号锁定时间(分钟)', type: 'number', valueType: 'number', required: true },
  { prop: 'sessionTimeout', label: '会话超时时间(分钟)', type: 'number', valueType: 'number', required: true }
]
onUnmounted(() => formContext.unregister('admin:security'))

const loadSettings = async () => {
  loading.value = true
  try {
    const response = await request.get('/admin/settings')
    if (response.code === 200) {
      // /admin/settings 的 data 为未知载荷，用精确局部接口收窄后使用
      const settings = response.data as SystemSettingsPayload

      if (Array.isArray(settings.emailNotifyScopeOptions) && settings.emailNotifyScopeOptions.length > 0) {
        emailScopeOptions.value = settings.emailNotifyScopeOptions
      }
      if (settings.basic) {
        basicForm.systemStatus = Number(settings.basic.systemStatus ?? 1)
        emailForm.enabled = Number(settings.basic.emailNotifyEnabled ?? 1) === 1
        const scopes = settings.basic.emailNotifyScopes
        if (Array.isArray(scopes)) {
          emailForm.scopes = scopes.filter((s): s is string => typeof s === 'string')
        } else if (typeof scopes === 'string' && scopes.trim()) {
          emailForm.scopes = scopes.split(',').map(s => s.trim()).filter(Boolean)
        } else {
          emailForm.scopes = [...DEFAULT_EMAIL_SCOPES]
        }
      }
      if (settings.security) {
        Object.assign(securityForm, settings.security)
        const complexity: unknown = settings.security.passwordComplexity
        if (typeof complexity === 'string') {
          securityForm.passwordComplexity = complexity.split(',')
        }
      }
    }
  } catch (error) {
    logger.error('加载系统设置失败:', error)
    ElMessage.error('加载系统设置失败')
  } finally {
    loading.value = false
  }
}

const saveSystemStatus = async () => {
  if (!basicFormRef.value) return

  await basicFormRef.value.validate(async (valid) => {
    if (valid) {
      savingStatus.value = true
      try {
        const response = await request.put('/admin/settings', {
          basic: { systemStatus: basicForm.systemStatus }
        })
        if (response.code === 200) {
          ElMessage.success('系统状态保存成功')
        } else {
          ElMessage.error(response.message || '保存失败')
        }
      } catch (error) {
        logger.error('保存系统状态失败:', error)
        ElMessage.error('保存系统状态失败')
      } finally {
        savingStatus.value = false
      }
    }
  })
}

const saveEmailNotify = async () => {
  savingEmail.value = true
  try {
    const response = await request.put('/admin/settings', {
      basic: {
        emailNotifyEnabled: emailForm.enabled ? 1 : 0,
        emailNotifyScopes: emailForm.scopes
      }
    })
    if (response.code === 200) {
      ElMessage.success(emailForm.enabled ? '邮件提醒设置已保存' : '邮件提醒已关闭，系统不再发送业务提醒邮件')
    } else {
      ElMessage.error(response.message || '保存失败')
    }
  } catch (error) {
    logger.error('保存邮件提醒设置失败:', error)
    ElMessage.error('保存邮件提醒设置失败')
  } finally {
    savingEmail.value = false
  }
}

const sendTestEmail = async () => {
  testingEmail.value = true
  try {
    const response = await request.post('/admin/settings/test-email')
    if (response.code === 200) {
      ElMessage.success(response.message || '测试邮件已发送，请到系统邮箱查收')
    } else {
      ElMessage.error(response.message || '测试邮件发送失败')
    }
  } catch (error) {
    logger.error('测试邮件发送失败:', error)
    ElMessage.error('测试邮件发送失败')
  } finally {
    testingEmail.value = false
  }
}

const saveSecuritySettings = async () => {
  if (!securityFormRef.value) return
  
  await securityFormRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        // 展开并把多选复杂度 join 为字符串，与原运行时对象形态一致
        const securityData = { ...securityForm, passwordComplexity: securityForm.passwordComplexity.join(',') }

        const operation = formContext.beginFormOperation('admin:security')
    const response = await request.put('/admin/settings', {
          security: securityData
        })
        if (response.code === 200) {
          operation.complete({ status: 'saved', label: '安全配置已保存', url: '/admin/system-settings' })
      ElMessage.success('安全配置保存成功')
          systemSettingsStore.updateSecuritySettings(securityData)
        } else {
          ElMessage.error(response.message || '保存失败')
        }
      } catch (error) {
        logger.error('保存安全配置失败:', error)
        ElMessage.error('保存安全配置失败')
      } finally {
        saving.value = false
      }
    }
  })
}


const resetSecuritySettings = () => {
  securityFormRef.value?.resetFields()
}

const clearDataDialogVisible = ref(false)
const selectedPages = ref([])
const clearPassword = ref('')
const clearPasswordError = ref('')
const clearProgress = ref(0)
const clearProgressStatus = ref('')
const currentClearingModule = ref('')
const clearedCount = ref(0)
const totalCount = ref(0)
const availablePages = ref([
  { value: 'teacher-users', label: '教师管理', service: 'teacher' },
  { value: 'student-users', label: '学生管理', service: 'student' },
  { value: 'companies', label: '企业管理', service: 'company' },
  { value: 'recall-audit', label: '撤回申请记录', service: 'recallAudit' },
  { value: 'position-categories', label: '岗位类别管理', service: 'positionCategory' },
  { value: 'classes', label: '班级管理', service: 'class' },
  { value: 'majors', label: '院系专业管理', service: 'major' },
  { value: 'internship-confirm', label: '实习确认表', service: 'internshipConfirm' },
  { value: 'recruitment', label: '招聘管理', service: 'recruitment' },
  { value: 'announcements', label: '公告管理', service: 'announcement' },
  { value: 'resource-documents', label: '资源文档管理', service: 'resourceDocuments' },
  { value: 'problem-feedback', label: '反馈管理', service: 'feedback' },
  { value: 'keyword-library', label: '关键词库管理', service: 'keywordLibrary' },
  { value: 'scoring-rule', label: '评分规则管理', service: 'scoringRule' },
  { value: 'ai-model', label: 'AI大模型选择', service: 'aiModel' },
  { value: 'logs', label: '日志管理', service: 'log' }
])

const showClearDataDialog = () => {
  selectedPages.value = []
  clearPassword.value = ''
  clearPasswordError.value = ''
  clearProgress.value = 0
  clearProgressStatus.value = ''
  currentClearingModule.value = ''
  clearedCount.value = 0
  totalCount.value = 0
  clearDataDialogVisible.value = true
}

const toggleSelectAll = (checked) => {
  if (checked) {
    selectedPages.value = availablePages.value.map(page => page.value)
  } else {
    selectedPages.value = []
  }
}

const isAllSelected = computed(() => {
  return selectedPages.value.length === availablePages.value.length && availablePages.value.length > 0
})

const isIndeterminate = computed(() => {
  const selectedCount = selectedPages.value.length
  return selectedCount > 0 && selectedCount < availablePages.value.length
})

const selectAllChecked = computed({
  get: () => isAllSelected.value,
  set: (value) => toggleSelectAll(value)
})

const cancelClearData = () => {
  clearDataDialogVisible.value = false
  clearPassword.value = ''
  clearPasswordError.value = ''
  selectedPages.value = []
  clearProgress.value = 0
  clearProgressStatus.value = ''
  currentClearingModule.value = ''
  clearedCount.value = 0
  totalCount.value = 0
}

const clearSelectedData = async () => {
  if (selectedPages.value.length === 0) {
    ElMessage.warning('请至少选择一个模块进行清除')
    return
  }

  if (!clearPassword.value || clearPassword.value.trim() === '') {
    clearPasswordError.value = '请输入登录密码'
    return
  }

  try {
    const passwordValid = await verifyPassword(clearPassword.value)
    if (!passwordValid) {
      clearPasswordError.value = '密码错误，请重新输入'
      return
    }

    const confirmResult = await ElMessageBox.confirm(
      `确定要清除选中的${selectedPages.value.length}个模块的所有数据吗？此操作不可恢复！`,
      '危险操作警告',
      {
        confirmButtonText: '确定清除',
        cancelButtonText: '取消',
        type: 'error',
        center: true,
        confirmButtonClass: 'el-button--danger'
      }
    )

    if (confirmResult === 'confirm') {
      clearing.value = true
      totalCount.value = selectedPages.value.length
      clearedCount.value = 0
      clearProgress.value = 0
      clearProgressStatus.value = ''
      currentClearingModule.value = ''
      const results = []

      for (let i = 0; i < selectedPages.value.length; i++) {
        const pageValue = selectedPages.value[i]
        const page = availablePages.value.find(p => p.value === pageValue)
        if (page) {
          currentClearingModule.value = page.label
          try {
            await clearPageData(page)
            results.push({ success: true, page: page.label })
          } catch (error) {
            logger.error(`清除${page.label}数据失败:`, error)
            results.push({
              success: false,
              page: page.label,
              error: error.message || '清除失败'
            })
          }
          clearedCount.value = i + 1
          clearProgress.value = Math.round((clearedCount.value / totalCount.value) * 100)
        }
      }

      const hasErrors = results.some(r => !r.success)
      clearProgressStatus.value = hasErrors ? 'exception' : 'success'

      displayClearResults(results)

      setTimeout(() => {
        clearDataDialogVisible.value = false
        clearPassword.value = ''
        clearPasswordError.value = ''
        selectedPages.value = []
        clearProgress.value = 0
        clearProgressStatus.value = ''
        currentClearingModule.value = ''
        clearedCount.value = 0
        totalCount.value = 0
      }, 1500)
    }
  } catch (error) {
    if (error !== 'cancel') {
      logger.error('清除数据失败:', error)
      ElMessage.error('清除数据失败，请稍后重试')
      clearProgressStatus.value = 'exception'
    }
  } finally {
    clearing.value = false
  }
}

const verifyPassword = async (password) => {
  try {
    const response = await AdminUserService.verifyAdminPassword(password)
    return response.code === 200
  } catch (error) {
    return false
  }
}

const clearPageData = async (page) => {
  logger.log(`开始清除${page.label}数据...`)

  switch (page.service) {
    case 'teacher':
      await clearTeacherData()
      break
    case 'student':
      await clearStudentData()
      break
    case 'company':
      await clearCompanyData()
      break
    case 'recallAudit':
      await clearRecallAuditData()
      break
    case 'positionCategory':
      await clearPositionCategoryData()
      break
    case 'class':
      await clearClassData()
      break
    case 'major':
      await clearMajorData()
      break
    case 'internshipConfirm':
      await clearInternshipConfirmData()
      break
    case 'recruitment':
      await clearRecruitmentData()
      break
    case 'announcement':
      await clearAnnouncementData()
      break
    case 'resourceDocuments':
      await clearResourceDocumentsData()
      break
    case 'feedback':
      await clearFeedbackData()
      break
    case 'keywordLibrary':
      await clearKeywordLibraryData()
      break
    case 'scoringRule':
      await clearScoringRuleData()
      break
    case 'aiModel':
      await clearAiModelData()
      break
    case 'log':
      await clearLogData()
      break
    default:
      throw new Error(`未知的模块类型: ${page.service}`)
  }

  logger.log(`成功清除${page.label}数据`)
}

const clearTeacherData = async () => {
  try {
    const response = await TeacherUserService.getTeachers(1, 10000)
    if (response.code === 200) {
      const teachers = response.data?.rows || []
      if (teachers.length > 0) {
        const ids = teachers.map(t => t.id).filter(id => id)
        if (ids.length > 0) {
          await TeacherUserService.batchDeleteTeachers(ids)
        }
      }
    }
  } catch (error) {
    logger.error('清除教师数据失败:', error)
    throw error
  }
}

const clearStudentData = async () => {
  try {
    const response = await StudentUserService.getStudents(1, 10000)
    if (response.code === 200) {
      const students = response.data?.rows || []
      if (students.length > 0) {
        const ids = students.map(s => s.id).filter(id => id)
        if (ids.length > 0) {
          await StudentUserService.batchDeleteStudents(ids)
        }
      }
    }
  } catch (error) {
    logger.error('清除学生数据失败:', error)
    throw error
  }
}

const clearCompanyData = async () => {
  try {
    const response = await companyService.getAdminCompanies({ page: 1, pageSize: 10000 })
    // 声明为双层 AxiosResponseWithMeta，运行时为单层 ApiResult；data 为分页 { rows } 或数组，保持原语义收窄
    const compRes = response as unknown as { code?: unknown; data?: unknown }
    if (response && compRes.code === 200) {
      const compRaw: unknown = compRes.data
      const compRows: unknown =
        compRaw !== null && typeof compRaw === 'object' && !Array.isArray(compRaw)
          ? (compRaw as { rows?: unknown }).rows
          : undefined
      const compList: unknown = compRows ? compRows : (compRaw ? compRaw : [])
      const companies: ClearableRow[] = Array.isArray(compList) ? (compList as ClearableRow[]) : []
      if (companies.length > 0) {
        // id 转 Number 后请求体数值与运行时一致（后端 Long 接受数字；原 filter 真值语义保留）
        const ids = companies.map(c => Number(c.id)).filter(id => id)
        if (ids.length > 0) {
          await companyService.batchDeleteCompanies(ids)
        }
      }
    }
  } catch (error) {
    logger.error('清除企业数据失败:', error)
    throw error
  }
}

const clearRecallAuditData = async () => {
  try {
    const studentResponse = await request.delete('/application-withdrawal-records/clear-all')
    
    if (studentResponse && studentResponse.code === 200) {
      ElMessage.success('学生撤回申请记录清除成功')
    } else {
      throw new Error(studentResponse?.msg || studentResponse?.message || '学生撤回申请记录清除失败')
    }

    const companyResponse = await companyService.clearRecallData()
    
    if (companyResponse && companyResponse.code === 200) {
      ElMessage.success('企业撤回申请数据清除成功')
    } else {
      throw new Error(companyResponse?.msg || companyResponse?.message || '企业撤回申请数据清除失败')
    }
  } catch (error) {
    logger.error('清除撤回申请记录失败:', error)
    throw error
  }
}

const clearPositionCategoryData = async () => {
  try {
    const response = await PositionCategoryService.getCategories()
    // 声明为双层 AxiosResponseWithMeta，运行时为单层 ApiResult（data 为数组），收窄后使用
    const catRes = response as unknown as { code?: unknown; data?: unknown }
    if (response && catRes.code === 200) {
      const catRaw: unknown = catRes.data
      const categories: ClearableRow[] = Array.isArray(catRaw) ? (catRaw as ClearableRow[]) : []
      if (categories.length > 0) {
        for (const category of categories) {
          // id 转 Number 后 URL 与运行时一致（后端 Long 接受数字）
          await PositionCategoryService.deleteCategory(Number(category.id))
        }
      }
    }
  } catch (error) {
    logger.error('清除岗位类别数据失败:', error)
    throw error
  }
}

const clearClassData = async () => {
  try {
    const response = await ClassService.getClasses()
    // getClasses 声明返回 ClassAPIResponse（AxiosResponse 双层），运行时为单层 ApiResult（data 为数组），收窄后使用
    const clsRes = response as unknown as { code?: unknown; data?: unknown }
    if (response && clsRes.code === 200) {
      const clsRaw: unknown = clsRes.data
      const classes: ClearableRow[] = Array.isArray(clsRaw) ? (clsRaw as ClearableRow[]) : []
      if (classes.length > 0) {
        const ids = classes.map(c => Number(c.id)).filter(id => id)
        if (ids.length > 0) {
          await ClassService.batchDeleteClass(ids)
        }
      }
    }
  } catch (error) {
    logger.error('清除班级数据失败:', error)
    throw error
  }
}

const clearMajorData = async () => {
  try {
    const response = await MajorService.getMajors()
    if (response.code === 200) {
      // getMajors 的 data 为 unknown（运行时为数组），收窄后迭代
      const majorRaw: unknown = (response as unknown as { data?: unknown }).data
      const majors: ClearableRow[] = Array.isArray(majorRaw) ? (majorRaw as ClearableRow[]) : []
      if (majors.length > 0) {
        for (const major of majors) {
          // id 转 Number 后 URL 与运行时一致（后端接受数字）
          await MajorService.deleteMajor(Number(major.id))
        }
      }
    }
    
    const departmentResponse = await DepartmentService.getDepartments()
    if (departmentResponse.data && departmentResponse.data.code === 200) {
      const departments = departmentResponse.data.data || []
      if (departments.length > 0) {
        for (const department of departments) {
          await DepartmentService.deleteDepartment(department.id)
        }
      }
    }
  } catch (error) {
    logger.error('清除院系专业数据失败:', error)
    throw error
  }
}

const clearInternshipConfirmData = async () => {
  try {
    const response = await internshipService.getInternshipStatusList({ page: 1, pageSize: 10000 })
    // 声明为双层 AxiosResponseWithMeta，运行时为单层 ApiResult；data 为分页 { rows } 或数组，保持原语义收窄
    const stRes = response as unknown as { code?: unknown; data?: unknown }
    if (response && stRes.code === 200) {
      const stRaw: unknown = stRes.data
      const stRows: unknown =
        stRaw !== null && typeof stRaw === 'object' && !Array.isArray(stRaw)
          ? (stRaw as { rows?: unknown }).rows
          : undefined
      const stList: unknown = stRows ? stRows : (stRaw ? stRaw : [])
      const statuses: ClearableRow[] = Array.isArray(stList) ? (stList as ClearableRow[]) : []
      if (statuses.length > 0) {
        const ids = statuses.map(s => Number(s.id)).filter(id => id)
        if (ids.length > 0) {
          await internshipService.batchDeleteInternshipStatus(ids)
        }
      }
    }
  } catch (error) {
    logger.error('清除实习确认表数据失败:', error)
    throw error
  }
}

const clearRecruitmentData = async () => {
  try {
    const response = await positionService.clearAllPositions()
    const result = response

    if (result && result.code === 200) {
      ElMessage.success('招聘管理数据清除成功')
    } else {
      throw new Error(result?.msg || '招聘管理数据清除失败')
    }
  } catch (error) {
    logger.error('清除招聘管理数据失败:', error)
    throw error
  }
}

const clearAnnouncementData = async () => {
  try {
    const response = await announcementApi.getAllAnnouncements()
    // getAllAnnouncements 直接返回 Announcement[]（运行时无 code 字段）；
    // 原 `response.code === 200` 恒假，保留该语义（分支不可达），仅做类型收窄
    const annRes = response as unknown as { code?: unknown; data?: unknown }
    if (annRes.code === 200) {
      const annRaw: unknown = annRes.data
      const announcements: ClearableRow[] = Array.isArray(annRaw) ? (annRaw as ClearableRow[]) : []
      if (announcements.length > 0) {
        const ids = announcements.map(a => Number(a.id)).filter(id => id)
        if (ids.length > 0) {
          await announcementApi.batchDeleteAnnouncements(ids)
        }
      }
    }
  } catch (error) {
    logger.error('清除公告数据失败:', error)
    throw error
  }
}

const clearResourceDocumentsData = async () => {
  try {
    const response = await resourceDocumentApi.getAllResourceDocuments()
    // 声明为双层 AxiosResponseWithMeta，运行时为单层 ApiResult（data 为数组），收窄后使用
    const docRes = response as unknown as { code?: unknown; data?: unknown }
    if (response && docRes.code === 200) {
      const docRaw: unknown = docRes.data
      const documents: ClearableRow[] = Array.isArray(docRaw) ? (docRaw as ClearableRow[]) : []
      if (documents.length > 0) {
        const ids = documents.map(d => Number(d.id)).filter(id => id)
        if (ids.length > 0) {
          await resourceDocumentApi.batchDeleteResourceDocuments(ids)
        }
      }
    }
  } catch (error) {
    logger.error('清除资源文档数据失败:', error)
    throw error
  }
}

const clearFeedbackData = async () => {
  try {
    const response = await feedbackApi.getAllFeedback()
    // 声明为双层 AxiosResponseWithMeta，运行时为单层 ApiResult（data 为数组），收窄后使用
    const fbRes = response as unknown as { code?: unknown; data?: unknown }
    if (response && fbRes.code === 200) {
      const fbRaw: unknown = fbRes.data
      const feedbacks: ClearableRow[] = Array.isArray(fbRaw) ? (fbRaw as ClearableRow[]) : []
      if (feedbacks.length > 0) {
        const ids = feedbacks.map(f => Number(f.id)).filter(id => id)
        if (ids.length > 0) {
          await feedbackApi.batchDeleteFeedback(ids)
        }
      }
    }
  } catch (error) {
    logger.error('清除反馈数据失败:', error)
    throw error
  }
}

const clearKeywordLibraryData = async () => {
  try {
    const response = await request.get('/admin/keyword-library')
    if (response.code === 200) {
      // data 为 unknown（运行时为数组），收窄后迭代
      const kwRaw: unknown = response.data
      const keywords: ClearableRow[] = Array.isArray(kwRaw) ? (kwRaw as ClearableRow[]) : []
      if (keywords.length > 0) {
        for (const keyword of keywords) {
          await request.delete(`/admin/keyword-library/${keyword.id}`)
        }
      }
    }
  } catch (error) {
    logger.error('清除关键词库数据失败:', error)
    throw error
  }
}

const clearScoringRuleData = async () => {
  try {
    const response = await request.get('/admin/scoring-rule')
    if (response.code === 200) {
      // data 为 unknown（运行时为数组），收窄后迭代
      const ruleRaw: unknown = response.data
      const rules: ClearableRow[] = Array.isArray(ruleRaw) ? (ruleRaw as ClearableRow[]) : []
      if (rules.length > 0) {
        for (const rule of rules) {
          await request.delete(`/admin/scoring-rule/${rule.id}`)
        }
      }
    }

    const categoryWeightResponse = await request.get('/admin/category-weight/active')
    // data 为 unknown；原 `data.code === 200` 仅双层形态成立，运行时单层 data 为数组时恒假，收窄保留该语义
    const cwRaw: unknown = categoryWeightResponse.data
    const cwNested: { code?: unknown; data?: unknown } | null =
      cwRaw !== null && typeof cwRaw === 'object' && !Array.isArray(cwRaw) && 'code' in cwRaw
        ? (cwRaw as { code?: unknown; data?: unknown })
        : null
    if (cwNested && cwNested.code === 200) {
      const weightRaw: unknown = cwNested.data
      const weights: ClearableRow[] = Array.isArray(weightRaw) ? (weightRaw as ClearableRow[]) : []
      if (weights.length > 0) {
        for (const weight of weights) {
          await request.delete(`/admin/category-weight/${weight.id}`)
        }
      }
    }
  } catch (error) {
    logger.error('清除评分规则数据失败:', error)
    throw error
  }
}

const clearAiModelData = async () => {
  try {
    logger.log('开始批量删除所有AI模型')
    const response = await aiModelService.deleteAllAIModels()
    logger.log('批量删除AI模型响应:', response)
    // deleteAllAIModels 声明为双层 AxiosResponseWithMeta，运行时为单层 ApiResult，收窄后使用
    const aiRes = response as unknown as { code?: unknown; data?: unknown }
    if (response && aiRes.code === 200) {
      logger.log('批量删除AI模型成功')
    } else {
      // data 为 unknown（void 载荷），收窄后读 msg，保持原 `response.data?.msg || '删除失败'` 语义
      const aiRaw: unknown = aiRes.data
      const aiMsg: unknown =
        aiRaw !== null && typeof aiRaw === 'object' ? (aiRaw as { msg?: unknown }).msg : undefined
      throw new Error(typeof aiMsg === 'string' && aiMsg ? aiMsg : '删除失败')
    }
  } catch (error) {
    logger.error('清除AI大模型数据失败:', error)
    throw error
  }
}

const clearLogData = async () => {
  try {
    // 直接调用后端兼容清理接口（需 log:delete 权限，全量清空）
    await request.delete('/admin/logs/operation/clean', { params: { deleteAll: true } })
  } catch (error) {
    logger.error('清除日志数据失败:', error)
    throw error
  }
}

const extractIdsFromResponse = (response) => {
  if (!response || !response.data) {
    return []
  }

  let items = []

  if (response.data && response.data.list) {
    items = response.data.list
  } else if (response.data && response.data.rows) {
    items = response.data.rows
  } else if (response.data.list) {
    items = response.data.list
  } else if (response.data.rows) {
    items = response.data.rows
  } else if (Array.isArray(response.data)) {
    items = response.data
  }

  return items.map(item => item.id || item.ID || '').filter(id => id)
}

const displayClearResults = (results) => {
  const successCount = results.filter(r => r.success).length
  const errorCount = results.filter(r => !r.success).length

  if (errorCount === 0) {
    ElMessage.success(`成功清除所有${successCount}个模块的数据`)
  } else if (successCount === 0) {
    const errorMessages = results
      .filter(r => !r.success)
      .map(r => `${r.page}: ${r.error}`)
      .join('\n')

    ElMessage.error(`清除数据失败:\n${errorMessages}`)
  } else {
    const successMessages = results
      .filter(r => r.success)
      .map(r => r.page)
      .join('、')

    const errorMessages = results
      .filter(r => !r.success)
      .map(r => `${r.page}: ${r.error}`)
      .join('\n')

    ElMessage.info(`成功清除${successMessages}模块的数据\n清除失败的模块:\n${errorMessages}`)
  }
}

onMounted(() => {
  loadSettings()
  // 智能填表：安全设置表单注册到 AI 侧栏（只写响应式 model，不触发提交）
  formContext.register({
    formId: 'admin:security',
    title: '安全配置',
    fields: securityFillFields,
    getModel: () => securityForm,
    getFormEl: () => securityFormRef.value
  })
})
</script>

<style scoped>
.system-settings-container {
  padding: 24px 28px;
  background: #f5f7fa;
  min-height: 100vh;
}

.settings-header {
  margin-bottom: 18px;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 21px;
  font-weight: 600;
  color: #303133;
}

.title-icon {
  font-size: 24px;
  color: #409EFF;
}

.settings-content {
  max-width: 1200px;
  margin: 0 auto;
}

.settings-card {
  margin-bottom: 30px;
}

.settings-card :deep(.el-card__body) {
  padding: 8px 28px 24px;
}

.el-tabs {
  min-height: 500px;
}

.el-tabs :deep(.el-tabs__header) {
  margin-bottom: 20px;
}

.el-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  padding: 0 22px;
  height: 44px;
  line-height: 44px;
}

.el-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 2px;
}

.settings-form {
  max-width: 960px;
  margin-top: 6px;
}

.settings-form :deep(.el-form-item__label) {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}

.settings-form :deep(.el-input__inner),
.settings-form :deep(.el-input-number) {
  height: 38px;
  font-size: 14px;
}

.settings-form :deep(.el-button) {
  padding: 9px 24px;
  font-size: 14px;
}

.settings-form .button-group {
  display: flex;
  gap: 16px;
  align-items: center;
}

.settings-form .button-group .el-button {
  margin: 0;
}

.form-tip {
  font-size: 12.5px;
  color: #909399;
  margin-top: 6px;
  line-height: 1.6;
  padding: 5px 10px;
  background: #f5f7fa;
  border-radius: 4px;
  border-left: 3px solid #409EFF;
}

.form-tip:has(.el-tag) {
  background: transparent;
  border: none;
  padding: 8px 0;
}

.el-form-item {
  margin-bottom: 20px;
}

.el-form-item :deep(.el-form-item__content) {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.el-form-item :deep(.el-input-number),
.el-form-item :deep(.el-input) {
  margin-bottom: 0;
}

.el-checkbox-group {
  display: flex;
  flex-wrap: wrap;
  gap: 15px;
}

.complexity-card {
  padding: 12px 14px;
  background: #f5f7fa;
  border-radius: 12px;
  border: 1px solid #e4e7ed;
  width: 100%;
}

.complexity-card .el-checkbox-group {
  margin: 0;
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  gap: 12px;
  justify-content: space-between;
}

.complexity-card .el-checkbox {
  flex: 1;
  padding: 8px 10px;
  background: white;
  border-radius: 8px;
  border: 1px solid #dcdfe6;
  text-align: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.complexity-card .el-checkbox:hover {
  border-color: #409EFF;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
}

.complexity-card .el-checkbox.is-checked {
  background: #e6f7ff;
  border-color: #409EFF;
}

/* ===== 基础配置：紧凑面板布局 ===== */
.basic-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 18px;
  margin-bottom: 18px;
}

.cfg-panel {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 18px 20px 16px;
  transition: box-shadow 0.2s ease;
}

.cfg-panel:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.cfg-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.cfg-panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.cfg-panel-title .el-icon {
  font-size: 17px;
  color: #409EFF;
}

.cfg-panel-title.danger .el-icon {
  color: #F56C6C;
}

.cfg-form :deep(.el-form-item) {
  margin-bottom: 2px;
}

.cfg-tip {
  font-size: 12.5px;
  color: #909399;
  line-height: 1.7;
  margin: 8px 0 14px;
}

.cfg-tip b {
  color: #606266;
  font-weight: 600;
}

.cfg-tip.danger-tip {
  color: #c45656;
  margin-bottom: 0;
}

.scope-group {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.scope-group.scope-disabled {
  opacity: 0.5;
}

.scope-item {
  margin: 0 !important;
  padding: 9px 12px;
  height: auto;
  width: 100%;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fafbfc;
  transition: all 0.2s ease;
}

.scope-item:hover {
  border-color: #c6e2ff;
  background: #f4f9ff;
}

.scope-item.is-checked {
  border-color: #409EFF;
  background: #ecf5ff;
}

.scope-item :deep(.el-checkbox__label) {
  font-size: 13px;
  color: #606266;
  padding-left: 8px;
  white-space: normal;
  line-height: 1.4;
}

.cfg-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.cfg-actions .el-button + .el-button {
  margin-left: 0;
}

.danger-panel {
  border-color: #fde2e2;
  background: #fffbfb;
}

/* ===== 安全配置：两列紧凑布局 ===== */
.security-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(330px, 1fr));
  gap: 0 40px;
  max-width: 100%;
}

.security-grid > .el-form-item:last-child {
  grid-column: 1 / -1;
}

.clear-data-container {
  padding: 10px 0;
}

.warning-box {
  display: flex;
  align-items: flex-start;
  padding: 20px;
  background: #fffbe6;
  border-radius: 12px;
  border-left: 4px solid #faad14;
  margin-bottom: 30px;
}

.warning-icon {
  font-size: 28px;
  color: #faad14;
  flex-shrink: 0;
  margin-right: 16px;
}

.warning-content {
  flex: 1;
}

.warning-title {
  font-size: 18px;
  font-weight: 600;
  color: #d46b08;
  margin-bottom: 10px;
}

.warning-text {
  font-size: 15px;
  color: #d46b08;
  line-height: 1.6;
}

.warning-tip-wrapper {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.warning-tip {
  font-size: 14px;
  color: #faad14;
  line-height: 1.6;
  font-weight: 500;
  flex: 1;
}

.backup-link {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #409EFF;
  font-size: 14px;
  transition: all 0.3s ease;
}

.backup-link:hover {
  color: #66b1ff;
}

.backup-link .el-icon {
  transition: transform 0.3s ease;
}

.backup-link:hover .el-icon {
  transform: translateX(4px);
}

.selection-section {
  margin-bottom: 30px;
}

.selection-section .section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.selection-section .section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.select-all-checkbox {
  font-size: 15px;
  font-weight: 500;
}

.select-all-checkbox :deep(.el-checkbox__label) {
  color: #606266;
}

.select-all-checkbox :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background-color: #409EFF;
  border-color: #409EFF;
}

.select-all-checkbox :deep(.el-checkbox__input.is-indeterminate .el-checkbox__inner) {
  background-color: #409EFF;
  border-color: #409EFF;
}

.password-section {
  padding: 20px;
  background: #f5f7fa;
  border-radius: 12px;
  border: 1px solid #e4e7ed;
}

.password-section .section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
}

.progress-section {
  margin-top: 24px;
  padding: 24px;
  background: #f5f7fa;
  border-radius: 12px;
  border: 1px solid #e4e7ed;
}

.progress-section .section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
}

.progress-section .el-progress {
  margin-bottom: 16px;
}

.progress-section .el-progress__text {
  font-size: 14px;
  font-weight: 600;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: #606266;
}

.progress-info .current-module {
  font-weight: 500;
  color: #409EFF;
}

.progress-info .progress-text {
  font-weight: 600;
  color: #909399;
}

.page-selection {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.page-selection .el-checkbox {
  padding: 16px;
  border-radius: 12px;
  border: 1px solid #e4e7ed;
  transition: all 0.3s ease;
  background: white;
  display: flex;
  align-items: center;
  gap: 10px;
}

.page-selection .el-checkbox:hover {
  background: #f0f7ff;
  border-color: #409EFF;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
}

.page-selection .el-checkbox.is-checked {
  background: #e6f7ff;
  border-color: #409EFF;
}

.dialog-footer {
  text-align: right;
}

.dialog-footer .el-button {
  padding: 12px 30px;
  font-size: 16px;
}

.el-dialog :deep(.el-dialog__header) {
  padding: 24px 30px;
  border-bottom: 1px solid #ebeef5;
}

.el-dialog :deep(.el-dialog__title) {
  font-size: 20px;
  font-weight: 600;
}

.el-dialog :deep(.el-dialog__body) {
  padding: 30px;
}

.el-dialog :deep(.el-form-item__label) {
  font-size: 15px;
  font-weight: 500;
  color: #606266;
}

.el-dialog :deep(.el-input__inner) {
  height: 44px;
  font-size: 15px;
}
</style>
