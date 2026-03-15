<template>
  <div class="system-settings-container">
    <div class="settings-header">
      <el-page-header @back="goBack" title="返回">
        <template #content>
          <div class="page-title">
            <el-icon class="title-icon">
              <Setting />
            </el-icon>
            <span>系统参数配置</span>
          </div>
        </template>
      </el-page-header>
    </div>

    <div class="settings-content">
      <el-card class="settings-card" shadow="hover">
        <el-tabs v-model="activeTab" class="settings-tabs" v-loading="loading">
        <el-tab-pane label="基础配置" name="basic">
          <div class="system-status-section">
            <h3 class="section-title">系统状态</h3>
            <el-form :model="basicForm" :rules="basicRules" ref="basicFormRef" label-width="0" class="settings-form system-status-form">
              <el-form-item prop="systemStatus">
                <div class="status-content">
                  <div class="status-card">
                    <el-radio-group v-model="basicForm.systemStatus">
                      <el-radio :label="1">正常运行</el-radio>
                      <el-radio :label="0">维护模式</el-radio>
                    </el-radio-group>
                  </div>
                  <div class="form-tip">
                    <el-tag v-if="basicForm.systemStatus === 1" type="success" size="small">系统正常运行中</el-tag>
                    <el-tag v-else type="warning" size="small">系统处于维护模式，普通用户无法登录</el-tag>
                  </div>
                </div>
              </el-form-item>

              <el-form-item>
                <div class="button-group">
                  <el-button v-if="authStore.hasPermission('system:settings:view')" type="primary" @click="saveBasicSettings" :loading="saving">保存基础配置</el-button>
                  <el-button v-if="authStore.hasPermission('system:settings:view')" @click="resetBasicSettings">重置</el-button>
                </div>
              </el-form-item>
            </el-form>
          </div>

          <el-divider />

          <div class="dual-selection-section">
            <h3 class="section-title">双向选择时间段</h3>
            <el-form :model="basicForm" ref="basicFormRef" label-width="180px" class="settings-form">
              <el-form-item label="双向选择开始日期" prop="dualSelectionStartDate">
                <el-date-picker
                  v-model="basicForm.dualSelectionStartDate"
                  type="date"
                  placeholder="请选择双向选择开始日期"
                  format="YYYY-MM-DD"
                  value-format="YYYY-MM-DD"
                />
                <div class="form-tip">在此日期及之后注册的企业将自动标识为"双向选择"</div>
              </el-form-item>

              <el-form-item label="双向选择结束日期" prop="dualSelectionEndDate">
                <el-date-picker
                  v-model="basicForm.dualSelectionEndDate"
                  type="date"
                  placeholder="请选择双向选择结束日期"
                  format="YYYY-MM-DD"
                  value-format="YYYY-MM-DD"
                />
                <div class="form-tip">在此日期之后注册的企业将自动标识为"学生自主联系"</div>
              </el-form-item>

              <el-form-item>
                <div class="button-group">
                  <el-button v-if="authStore.hasPermission('system:settings:view')" type="primary" @click="saveBasicSettings" :loading="saving">保存双向选择配置</el-button>
                  <el-button v-if="authStore.hasPermission('system:settings:view')" @click="resetBasicSettings">重置</el-button>
                </div>
              </el-form-item>
            </el-form>
          </div>

          <el-divider />

          <div class="data-management-section">
            <h3 class="section-title">数据管理</h3>
            <el-alert
              title="危险操作警告"
              type="warning"
              :closable="false"
              show-icon
              class="warning-alert"
            >
              以下操作将永久删除数据，且不可恢复！请谨慎操作。
            </el-alert>
            <el-button v-if="authStore.hasPermission('system:settings:view')" type="danger" @click="showClearDataDialog" class="clear-data-btn">
              <el-icon><Delete /></el-icon>
              清除数据
            </el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="安全配置" name="security">
          <el-form :model="securityForm" :rules="securityRules" ref="securityFormRef" label-width="180px" class="settings-form">
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
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { Delete, WarningFilled, Setting, ArrowRight } from '@element-plus/icons-vue'
import request from '../../utils/request'
import { useSystemSettingsStore } from '../../store/systemSettings'
import { useAuthStore } from '../../store/auth'
import AdminUserService from '../../api/AdminUserService'
import StudentUserService from '../../api/StudentUserService'
import TeacherUserService from '../../api/TeacherUserService'
import ClassService from '../../api/class'
import MajorService from '../../api/major'
import DepartmentService from '../../api/department'
import { cleanLogsApi } from '../../api/log'
import companyService from '../../api/company'
import PositionCategoryService from '../../api/positionCategory'
import positionService from '../../api/position'
import * as announcementApi from '../../api/announcement'
import * as feedbackApi from '../../api/problemFeedback'
import * as resourceDocumentApi from '../../api/resourceDocument'
import recruitmentService from '../../api/recruitment'
import aiModelService from '../../api/aiModel'
import internshipService from '../../api/internship'
import * as permissionApi from '../../api/permission'

const router = useRouter()
const authStore = useAuthStore()
const systemSettingsStore = useSystemSettingsStore()

const goToBackupPage = () => {
  router.push('/admin/backup')
}

const activeTab = ref('basic')
const saving = ref(false)
const clearing = ref(false)
const loading = ref(false)

const basicFormRef = ref(null)
const securityFormRef = ref(null)

const basicForm = reactive({
  systemStatus: 1,
  dualSelectionStartDate: null,
  dualSelectionEndDate: null
})

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

const loadSettings = async () => {
  loading.value = true
  try {
    const response = await request.get('/admin/settings')
    if (response.code === 200) {
      const settings = response.data
      
      if (settings.basic) {
        Object.assign(basicForm, settings.basic)
      }
      if (settings.security) {
        Object.assign(securityForm, settings.security)
        if (typeof (settings.security as any).passwordComplexity === 'string') {
          securityForm.passwordComplexity = (settings.security as any).passwordComplexity.split(',')
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

const saveBasicSettings = async () => {
  if (!basicFormRef.value) return
  
  await basicFormRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        const response = await request.put('/admin/settings', {
          basic: basicForm
        })
        if (response.code === 200) {
          ElMessage.success('基础配置保存成功')
        } else {
          ElMessage.error(response.message || '保存失败')
        }
      } catch (error) {
        logger.error('保存基础配置失败:', error)
        ElMessage.error('保存基础配置失败')
      } finally {
        saving.value = false
      }
    }
  })
}

const saveSecuritySettings = async () => {
  if (!securityFormRef.value) return
  
  await securityFormRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        const securityData = { ...securityForm } as any
        securityData.passwordComplexity = securityForm.passwordComplexity.join(',')
        
        const response = await request.put('/admin/settings', {
          security: securityData
        })
        if (response.code === 200) {
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

const resetBasicSettings = () => {
  basicFormRef.value?.resetFields()
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
  { value: 'logs', label: '日志管理', service: 'log' },
  { value: 'permissions', label: '权限管理', service: 'permission' }
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
    return response.data?.code === 200
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
    case 'permission':
      await clearPermissionData()
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
    const response = await companyService.getCompanies({ page: 1, pageSize: 10000 })
    if (response.code === 200) {
      const companies = response.data || []
      if (companies.length > 0) {
        const ids = companies.map(c => c.id).filter(id => id)
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
    const studentResult = studentResponse.data
    
    if (studentResult && studentResult.code === 200) {
      ElMessage.success('学生撤回申请记录清除成功')
    } else {
      throw new Error(studentResult?.msg || '学生撤回申请记录清除失败')
    }

    const companyResponse = await companyService.clearRecallData()
    const companyResult = companyResponse.data
    
    if (companyResult && companyResult.code === 200) {
      ElMessage.success('企业撤回申请数据清除成功')
    } else {
      throw new Error(companyResult?.msg || '企业撤回申请数据清除失败')
    }
  } catch (error) {
    logger.error('清除撤回申请记录失败:', error)
    throw error
  }
}

const clearPositionCategoryData = async () => {
  try {
    const response = await PositionCategoryService.getCategories()
    if (response.code === 200) {
      const categories = response.data || []
      if (categories.length > 0) {
        for (const category of categories) {
          await PositionCategoryService.deleteCategory(category.id)
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
    if (response.code === 200) {
      const classes = response.data || []
      if (classes.length > 0) {
        const ids = classes.map(c => c.id).filter(id => id)
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
      const majors = response.data || []
      if (majors.length > 0) {
        for (const major of majors) {
          await MajorService.deleteMajor(major.id)
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
    if (response.code === 200) {
      const statuses = response.data || []
      if (statuses.length > 0) {
        const ids = statuses.map(s => s.id).filter(id => id)
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
    if (response.code === 200) {
      const announcements = response.data || []
      if (announcements.length > 0) {
        const ids = announcements.map(a => a.id).filter(id => id)
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
    if (response.code === 200) {
      const documents = response.data || []
      if (documents.length > 0) {
        const ids = documents.map(d => d.id).filter(id => id)
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
    if (response.code === 200) {
      const feedbacks = response.data || []
      if (feedbacks.length > 0) {
        const ids = feedbacks.map(f => f.id).filter(id => id)
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
      const keywords = response.data || []
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
      const rules = response.data || []
      if (rules.length > 0) {
        for (const rule of rules) {
          await request.delete(`/admin/scoring-rule/${rule.id}`)
        }
      }
    }

    const categoryWeightResponse = await request.get('/admin/category-weight/active')
    if (categoryWeightResponse.data.code === 200) {
      const weights = categoryWeightResponse.data.data || []
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
    if (response.code === 200) {
      logger.log('批量删除AI模型成功')
    } else {
      throw new Error(response.data?.msg || '删除失败')
    }
  } catch (error) {
    logger.error('清除AI大模型数据失败:', error)
    throw error
  }
}

const clearLogData = async () => {
  try {
    await cleanLogsApi(null, true)
  } catch (error) {
    logger.error('清除日志数据失败:', error)
    throw error
  }
}

const clearPermissionData = async () => {
  try {
    const response = await permissionApi.clearRolePermissions()
    const result = response

    if (result && result.code === 200) {
      ElMessage.success('权限管理数据清除成功')
    } else {
      throw new Error(result?.msg || '权限管理数据清除失败')
    }
  } catch (error) {
    logger.error('清除权限管理数据失败:', error)
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

const goBack = () => {
  router.back()
}

onMounted(() => {
  loadSettings()
})
</script>

<style scoped>
.system-settings-container {
  padding: 30px;
  background: #f5f7fa;
  min-height: 100vh;
}

.settings-header {
  margin-bottom: 30px;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.title-icon {
  font-size: 28px;
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
  padding: 40px;
}

.el-tabs {
  min-height: 500px;
}

.el-tabs :deep(.el-tabs__header) {
  margin-bottom: 30px;
}

.el-tabs :deep(.el-tabs__item) {
  font-size: 16px;
  padding: 0 30px;
  height: 50px;
  line-height: 50px;
}

.el-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 2px;
}

.settings-form {
  max-width: 800px;
  margin-top: 20px;
}

.settings-form :deep(.el-form-item__label) {
  font-size: 16px;
  font-weight: 500;
  color: #606266;
}

.settings-form :deep(.el-input__inner),
.settings-form :deep(.el-input-number) {
  height: 48px;
  font-size: 16px;
}

.settings-form :deep(.el-button) {
  padding: 12px 36px;
  font-size: 16px;
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
  font-size: 14px;
  color: #909399;
  margin-top: 12px;
  line-height: 1.6;
  padding: 8px 12px;
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
  margin-bottom: 30px;
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
  padding: 20px;
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
  padding: 12px 16px;
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

.status-card {
  padding: 12px;
  background: #f5f7fa;
  border-radius: 12px;
  border: 1px solid #e4e7ed;
  width: fit-content;
  max-width: 100%;
}

.status-card .el-radio-group {
  margin: 0;
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  gap: 12px;
  justify-content: flex-start;
}

.status-card .el-radio {
  flex: none;
  min-width: 100px;
  padding: 8px 12px;
  background: white;
  border-radius: 8px;
  border: 1px solid #dcdfe6;
  text-align: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.status-card .el-radio:hover {
  border-color: #409EFF;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
}

.status-card .el-radio.is-checked {
  background: #e6f7ff;
  border-color: #409EFF;
}

.system-status-section {
  margin-bottom: 40px;
  padding-bottom: 30px;
}

.system-status-form {
  max-width: 100%;
}

.system-status-form .el-form-item {
  margin-bottom: 20px;
}

.status-content {
  width: 100%;
}

.system-status-section .section-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.system-status-section .section-title::before {
  content: '';
  width: 4px;
  height: 24px;
  background: #409EFF;
  border-radius: 2px;
}

.dual-selection-section {
  margin-bottom: 40px;
  padding-bottom: 30px;
}

.dual-selection-section .section-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.dual-selection-section .section-title::before {
  content: '';
  width: 4px;
  height: 24px;
  background: #409EFF;
  border-radius: 2px;
}

.data-management-section {
  margin-top: 40px;
  padding-top: 30px;
}

.data-management-section .warning-alert {
  margin-bottom: 20px;
}

.data-management-section .clear-data-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 30px;
  font-size: 16px;
}

.data-management-section .section-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.data-management-section .section-title::before {
  content: '';
  width: 4px;
  height: 24px;
  background: #409EFF;
  border-radius: 2px;
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
