<template>
  <div class="account-settings-container">
    <div class="page-header">
      <h2>账号设置</h2>
      <p>管理您的账号信息和安全设置</p>
    </div>

    <!-- 标签页内容 -->
    <div class="settings-content">
      <!-- 个人信息 -->
      <div class="personal-info section">
        <h3>基本信息</h3>
        <div class="form-grid">
          <div class="form-item">
            <label>姓名：</label>
            <input type="text" v-model="userInfo.name" readonly class="readonly-input">
          </div>
          <div class="form-item">
            <label>教师工号：</label>
            <input type="text" v-model="userInfo.teacherId" readonly class="readonly-input">
          </div>
          <div class="form-item">
            <label>手机号：</label>
            <input type="tel" v-model="userInfo.phone" readonly class="readonly-input">
          </div>
          <div class="form-item">
            <label>邮箱：</label>
            <input type="text" :value="userInfo.email || '未绑定'" readonly class="readonly-input">
          </div>
        </div>
      </div>

      <!-- 账号安全 -->
      <div class="account-security section">
        <h3>账号安全</h3>
        <div class="security-section">
          <div class="security-item">
            <div class="security-info">
              <h4>登录密码</h4>
              <p>定期修改密码可以保护账号安全</p>
            </div>
            <button class="btn-default" @click="openPasswordDialog">修改密码</button>
          </div>

          <div class="security-item">
            <div class="security-info">
              <h4>绑定手机</h4>
              <p>已绑定: {{ userInfo.phone || '未绑定' }}</p>
            </div>
            <button class="btn-default" @click="changePhone">更换手机</button>
          </div>

          <div class="security-item">
            <div class="security-info">
              <h4>绑定邮箱</h4>
              <p>已绑定: {{ userInfo.email || '未绑定' }}</p>
            </div>
            <button class="btn-default" @click="openEmailDialog">{{ userInfo.email ? '更换邮箱' : '绑定邮箱' }}</button>
          </div>

          <div class="security-item logout-item">
            <div class="security-info">
              <h4>退出登录</h4>
              <p>退出后需要重新登录才能访问系统</p>
            </div>
            <button class="btn-danger" @click="logout">退出登录</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 修改密码弹窗 -->
    <div v-if="showPasswordDialog" class="dialog-overlay" @click="showPasswordDialog = false">
      <div class="dialog-content" @click.stop>
        <div class="dialog-header">
          <h3>修改密码</h3>
          <button class="dialog-close" @click="showPasswordDialog = false">×</button>
        </div>
        <div class="dialog-body">
          <div class="form-item full-width">
            <label>原密码：</label>
            <input type="password" v-model="passwordForm.oldPassword" placeholder="请输入原密码">
          </div>
          <div class="form-item full-width">
            <label>新密码：</label>
            <input type="password" v-model="passwordForm.newPassword" :placeholder="`请输入新密码`">
            <div class="password-rules-hint">密码规则：{{ passwordRulesText }}</div>
          </div>
          <div class="form-item full-width">
            <label>确认新密码：</label>
            <input type="password" v-model="passwordForm.confirmPassword" placeholder="请再次输入新密码">
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-default" @click="showPasswordDialog = false">取消</button>
          <button class="btn-primary" @click="changePassword" :disabled="!isPasswordFormValid">确认修改</button>
        </div>
      </div>
    </div>

    <!-- 更换手机弹窗 -->
    <div v-if="showPhoneDialog" class="dialog-overlay" @click="showPhoneDialog = false">
      <div class="dialog-content" @click.stop>
        <div class="dialog-header">
          <h3>更换手机</h3>
          <button class="dialog-close" @click="showPhoneDialog = false">×</button>
        </div>
        <div class="dialog-body">
          <div class="form-item full-width">
            <label>新手机号：</label>
            <input type="tel" v-model="phoneForm.newPhone" placeholder="请输入新手机号">
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-default" @click="showPhoneDialog = false">取消</button>
          <button class="btn-primary" @click="updatePhone" :disabled="!isPhoneFormValid">确认更换</button>
        </div>
      </div>
    </div>

    <!-- 绑定/更换邮箱弹窗 -->
    <div v-if="showEmailDialog" class="dialog-overlay" @click="showEmailDialog = false">
      <div class="dialog-content" @click.stop>
        <div class="dialog-header">
          <h3>{{ userInfo.email ? '更换邮箱' : '绑定邮箱' }}</h3>
          <button class="dialog-close" @click="showEmailDialog = false">×</button>
        </div>
        <div class="dialog-body">
          <div v-if="userInfo.email" class="form-item full-width">
            <label>当前邮箱：</label>
            <input type="text" :value="userInfo.email" readonly class="readonly-input">
          </div>
          <div class="form-item full-width">
            <label>新邮箱：</label>
            <input type="email" v-model="emailForm.newEmail" placeholder="请输入新邮箱，绑定后可用于找回密码">
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-default" @click="showEmailDialog = false">取消</button>
          <button class="btn-primary" @click="updateEmail" :disabled="!isEmailFormValid">确认绑定</button>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useFormContext } from '@/agent/formContext'
import { accountSettingsApi, type UserInfo } from '../../api/teacherAccountSettings'
import { useAuthStore } from '../../store/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()

const showPasswordDialog = ref(false)
const showPhoneDialog = ref(false)
const showEmailDialog = ref(false)

// 邮箱格式校验（与后端保持一致，直接保存、无验证码）
const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/

const userInfo = ref<UserInfo>({
  id: 0,
  username: '',
  phone: '',
  email: '',
  name: '',
  teacherId: '',
  gender: 1,
  departmentId: '',
  teacherType: ''
})

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 密码规则
const passwordRules = ref({
  minLength: 6,
  complexity: 'lowercase,number'
})

// 获取密码规则
const fetchPasswordRules = async () => {
  try {
    interface PasswordRuleRow {
      minLength?: number
      min_length?: number
      complexity?: string
    }
    const response = await request.get<PasswordRuleRow>('/auth/password-rules')
    if (response.code === 200) {
      const ruleData = response.data as PasswordRuleRow
      passwordRules.value = {
        minLength: ruleData.minLength ?? ruleData.min_length ?? 6,
        complexity: ruleData.complexity ?? 'lowercase,number'
      }
    }
  } catch (error) {
    console.error('获取密码规则失败:', error)
  }
}

// 密码规则描述
const passwordRulesText = computed(() => {
  const rules = []
  rules.push(`最少 ${passwordRules.value.minLength} 位`)
  if (passwordRules.value.complexity) {
    const complexity = passwordRules.value.complexity
    if (complexity.includes('uppercase')) rules.push('大写字母')
    if (complexity.includes('lowercase')) rules.push('小写字母')
    if (complexity.includes('number')) rules.push('数字')
    if (complexity.includes('special')) rules.push('特殊字符')
  }
  return rules.join('、')
})

// ===== 智能填表：更换手机/绑定邮箱注册到 AI 侧栏 =====
// 修改密码涉及口令安全，不接入语音填表
const formContext = useFormContext()
watch(showPhoneDialog, (open) => {
  if (open) {
    formContext.register({
      formId: 'teacher:account-phone',
      title: '更换手机',
      fields: [{ prop: 'newPhone', label: '新手机号', type: 'input', required: true, patternHint: '11位手机号码' }],
      getModel: () => phoneForm.value,
      getValidationSummary: () => ({ missingFields: [], validationErrors: isPhoneFormValid.value ? [] : ['请输入正确的11位手机号'] })
    })
  } else formContext.unregister('teacher:account-phone')
})
watch(showEmailDialog, (open) => {
  if (open) {
    formContext.register({
      formId: 'teacher:account-email',
      title: '绑定邮箱',
      fields: [{ prop: 'newEmail', label: '新邮箱', type: 'input', required: true, patternHint: '邮箱地址' }],
      getModel: () => emailForm.value,
      getValidationSummary: () => ({ missingFields: [], validationErrors: isEmailFormValid.value ? [] : ['请输入正确的邮箱格式'] })
    })
  } else formContext.unregister('teacher:account-email')
})
onUnmounted(() => { formContext.unregister('teacher:account-phone'); formContext.unregister('teacher:account-email') })

// 监听密码对话框打开
watch(showPasswordDialog, (newVal) => {
  if (newVal) {
    fetchPasswordRules()
  }
})

const phoneForm = ref({
  newPhone: ''
})

const emailForm = ref({
  newEmail: ''
})

const isPasswordFormValid = computed(() => {
  return (
    passwordForm.value.oldPassword.trim() !== '' &&
    passwordForm.value.newPassword.trim() !== '' &&
    passwordForm.value.newPassword.length >= 6 &&
    passwordForm.value.newPassword.length <= 20 &&
    passwordForm.value.newPassword === passwordForm.value.confirmPassword
  )
})

const isPhoneFormValid = computed(() => {
  return (
    phoneForm.value.newPhone.trim() !== '' &&
    /^1[3-9]\d{9}$/.test(phoneForm.value.newPhone)
  )
})

const isEmailFormValid = computed(() => {
  return (
    emailForm.value.newEmail.trim() !== '' &&
    EMAIL_REGEX.test(emailForm.value.newEmail.trim())
  )
})

onMounted(() => {
  loadUserInfo()
})

const loadUserInfo = async () => {
  try {
    const data = await accountSettingsApi.getAccountInfo() as unknown as Partial<UserInfo>
    if (data !== null && typeof data === 'object') {
      userInfo.value = { ...userInfo.value, ...(data as Partial<UserInfo>) }
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
  }
}

const savePersonalInfo = () => {
  console.log('保存个人信息:', userInfo.value)
  showOperationFeedback('个人信息已成功保存', 'success')
}

const changePassword = async () => {
  if (!userInfo.value.email) {
    ElMessage.warning('请先绑定邮箱后再修改密码')
    showPasswordDialog.value = false
    openEmailDialog()
    return
  }
  if (isPasswordFormValid.value) {
    try {
      // 后端 changePassword 声明含 phoneCode（短信改密流程），账号设置页为口令改密不传该键；
      // 保持原请求体不变，用精确断言适配声明
      const payload = passwordForm.value as unknown as Parameters<typeof accountSettingsApi.changePassword>[0]
      await accountSettingsApi.changePassword(payload)

      ElMessage.success('密码修改成功，请重新登录')
      showPasswordDialog.value = false
      passwordForm.value = {
        oldPassword: '',
        newPassword: '',
        confirmPassword: ''
      }
      await authStore.logout(true)
      localStorage.removeItem('token')
      localStorage.removeItem('role')
      localStorage.removeItem('userId')
      localStorage.removeItem('current_role')
      router.push('/login')
    } catch (error: any) {
      ElMessage.error(error.message || '修改密码失败')
    }
  } else {
    ElMessage.error('请检查表单信息是否正确')
  }
}

const showOperationFeedback = (message: string, type: 'success' | 'error' | 'warning' = 'success') => {
  console.log(`${type === 'success' ? '✅' : type === 'error' ? '❌' : '⚠️'} ${message}`)
}

const changePhone = () => {
  showPhoneDialog.value = true
}

const openPasswordDialog = () => {
  if (!userInfo.value.email) {
    ElMessage.warning('请先绑定邮箱后再修改密码')
    openEmailDialog()
    return
  }
  showPasswordDialog.value = true
}

const openEmailDialog = () => {
  emailForm.value = {
    newEmail: ''
  }
  showEmailDialog.value = true
}

const updateEmail = async () => {
  if (!isEmailFormValid.value) {
    ElMessage.error('请输入正确的邮箱格式')
    return
  }
  try {
    const operation = formContext.beginFormOperation('teacher:account-email')
    await accountSettingsApi.updateContact({
      email: emailForm.value.newEmail.trim()
    })
    operation.complete({ status: 'saved', label: '邮箱已绑定', url: '/teacher/account-settings' })
    ElMessage.success('邮箱绑定成功')
    emailForm.value = {
      newEmail: ''
    }
    showEmailDialog.value = false
    await loadUserInfo()
  } catch (error: any) {
    ElMessage.error(error.message || '绑定邮箱失败')
  }
}

const updatePhone = async () => {
  if (isPhoneFormValid.value) {
    try {
      const operation = formContext.beginFormOperation('teacher:account-phone')
      await accountSettingsApi.updateContact({
        phone: phoneForm.value.newPhone
      })
      operation.complete({ status: 'saved', label: '手机号已更换', url: '/teacher/account-settings' })
      showOperationFeedback('手机号已成功更换', 'success')
      phoneForm.value = {
        newPhone: ''
      }
      showPhoneDialog.value = false
      await loadUserInfo()
    } catch (error: any) {
      showOperationFeedback(error.message || '更换手机号失败', 'error')
    }
  } else {
    showOperationFeedback('请检查表单信息是否正确', 'error')
  }
}

const logout = async () => {
  if (confirm('确定要退出登录吗？')) {
    try {
      await authStore.logout()
      localStorage.removeItem('token')
      window.location.href = '/login'
    } catch (error: any) {
      ElMessage.error(error.message || '退出登录失败')
    }
  }
}
</script>

<style scoped>
.account-settings-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.page-header h2 {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.page-header p {
  font-size: 14px;
  color: #666;
  margin: 8px 0 0 0;
}

/* 设置内容 */
.settings-content {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.section {
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  padding: 24px;
}

.section h3 {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0 0 20px 0;
  padding-bottom: 12px;
  border-bottom: 1px solid #e0e0e0;
}

/* 表单样式 */
.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 16px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item.full-width {
  grid-column: 1 / -1;
}

.form-item label {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.form-item input,
.form-item select {
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 14px;
  transition: all 0.3s ease;
}

.form-item input:focus,
.form-item select:focus {
  outline: none;
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2);
}

.readonly-input {
  background-color: #f5f5f5;
  cursor: not-allowed;
  color: #999;
}

.input-with-action {
  display: flex;
  gap: 8px;
  align-items: center;
}

.input-with-action input {
  flex: 1;
}

.verify-btn {
  padding: 8px 16px;
  background-color: #1890ff;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
  white-space: nowrap;
}

.verify-btn:hover {
  background-color: #40a9ff;
}

/* 账号安全样式 */
.security-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.security-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background-color: #fafafa;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.security-item:hover {
  background-color: #f0f7ff;
  border-color: #1890ff;
}

.security-info h4 {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0 0 4px 0;
}

.security-info p {
  font-size: 14px;
  color: #666;
  margin: 0;
}

.logout-item:hover {
  background-color: #fff2f0;
  border-color: #ff4d4f;
}

/* 按钮样式 */
.btn-primary,
.btn-default,
.btn-danger {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.btn-primary {
  background-color: var(--color-primary);
  color: white;
}

.btn-primary:hover {
  background-color: #40a9ff;
}

.btn-primary:disabled {
  background-color: #d9d9d9;
  cursor: not-allowed;
}

.btn-default {
  background-color: #f0f2f5;
  color: #333;
  border: 1px solid #d9d9d9;
}

.btn-default:hover {
  background-color: #e6e8eb;
  border-color: #c6c8ca;
}

.btn-danger {
  background-color: #ff4d4f;
  color: white;
}

.btn-danger:hover {
  background-color: #ff7875;
}

/* 弹窗样式 */
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog-content {
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  width: 90%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e0e0e0;
}

.dialog-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.dialog-close {
  background: none;
  border: none;
  font-size: 24px;
  color: #999;
  cursor: pointer;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.3s ease;
}

.dialog-close:hover {
  background-color: #f5f5f5;
  color: #333;
}

.dialog-body {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px 24px;
  border-top: 1px solid #e0e0e0;
}

/* 响应式调整 */
@media (max-width: 1200px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
  
  .security-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  
  .security-item button {
    align-self: flex-end;
  }
  
  .dialog-content {
    width: 95%;
  }
}

.password-rules-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}
</style>
