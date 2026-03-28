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
            <input type="email" v-model="userInfo.email" readonly class="readonly-input">
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
            <button class="btn-default" @click="showPasswordDialog = true">修改密码</button>
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
            <button class="btn-default" @click="changeEmail">更换邮箱</button>
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
            <input type="password" v-model="passwordForm.newPassword" placeholder="请输入新密码 (6-20位)">
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
          <div class="form-item full-width">
            <label>验证码：</label>
            <div class="input-with-action">
              <input type="text" v-model="phoneForm.verifyCode" placeholder="请输入验证码">
              <button class="verify-btn" @click="getPhoneVerifyCode" :disabled="!phoneForm.newPhone">获取验证码</button>
            </div>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-default" @click="showPhoneDialog = false">取消</button>
          <button class="btn-primary" @click="updatePhone" :disabled="!isPhoneFormValid">确认更换</button>
        </div>
      </div>
    </div>

    <!-- 更换邮箱弹窗 -->
    <div v-if="showEmailDialog" class="dialog-overlay" @click="showEmailDialog = false">
      <div class="dialog-content" @click.stop>
        <div class="dialog-header">
          <h3>更换邮箱</h3>
          <button class="dialog-close" @click="showEmailDialog = false">×</button>
        </div>
        <div class="dialog-body">
          <div class="form-item full-width">
            <label>新邮箱：</label>
            <input type="email" v-model="emailForm.newEmail" placeholder="请输入新邮箱">
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-default" @click="showEmailDialog = false">取消</button>
          <button class="btn-primary" @click="updateEmail" :disabled="!isEmailFormValid">确认更换</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { accountSettingsApi, type UserInfo } from '../../api/teacherAccountSettings'
import { useAuthStore } from '../../store/auth'
import { ElMessage, ElMessageBox } from 'element-plus'

const authStore = useAuthStore()

const showPasswordDialog = ref(false)
const showPhoneDialog = ref(false)
const showEmailDialog = ref(false)

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

const phoneForm = ref({
  newPhone: '',
  verifyCode: '',
  requestId: ''
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
    phoneForm.value.verifyCode.trim() !== '' &&
    /^1[3-9]\d{9}$/.test(phoneForm.value.newPhone)
  )
})

const isEmailFormValid = computed(() => {
  return (
    emailForm.value.newEmail.trim() !== '' &&
    /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$/.test(emailForm.value.newEmail)
  )
})

onMounted(() => {
  loadUserInfo()
})

const loadUserInfo = async () => {
  try {
    const data = await accountSettingsApi.getAccountInfo()
    userInfo.value = data
  } catch (error) {
    console.error('获取用户信息失败:', error)
  }
}

const savePersonalInfo = () => {
  console.log('保存个人信息:', userInfo.value)
  showOperationFeedback('个人信息已成功保存', 'success')
}

const changePassword = async () => {
  if (isPasswordFormValid.value) {
    try {
      await accountSettingsApi.changePassword(passwordForm.value)

      // 密码修改成功，提示用户需要重新登录
      ElMessageBox.confirm('密码修改成功，为了您的账号安全，请重新登录', '修改成功', {
        confirmButtonText: '重新登录',
        cancelButtonText: '稍后再说',
        type: 'success',
        closeOnClickModal: false,
        allowOutsideClick: false
      }).then(async () => {
        // 用户选择重新登录，调用登出
        await authStore.logout()
        localStorage.removeItem('token')
        window.location.href = '/login'
      }).catch(() => {
        // 用户选择稍后再说，关闭弹窗并清空表单
        passwordForm.value = {
          oldPassword: '',
          newPassword: '',
          confirmPassword: ''
        }
        showPasswordDialog.value = false
      })

      // 不立即关闭弹窗，等待用户选择
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

const getPhoneVerifyCode = async () => {
  if (!phoneForm.value.newPhone) {
    showOperationFeedback('请先输入新手机号', 'error')
    return
  }
  try {
    const response = await accountSettingsApi.sendVerifyCode(phoneForm.value.newPhone)
    if (response.code === 200) {
      phoneForm.value.requestId = response.data?.requestId || ''
      showOperationFeedback('验证码已发送', 'success')
    } else {
      showOperationFeedback(response.message || '发送验证码失败', 'error')
    }
  } catch (error: any) {
    showOperationFeedback(error.message || '发送验证码失败', 'error')
  }
}

const changePhone = () => {
  showPhoneDialog.value = true
}

const updatePhone = async () => {
  if (isPhoneFormValid.value) {
    try {
      await accountSettingsApi.updateContact({
        phone: phoneForm.value.newPhone,
        verifyCode: phoneForm.value.verifyCode,
        requestId: phoneForm.value.requestId
      })
      showOperationFeedback('手机号已成功更换', 'success')
      phoneForm.value = {
        newPhone: '',
        verifyCode: '',
        requestId: ''
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

const changeEmail = () => {
  showEmailDialog.value = true
}

const updateEmail = async () => {
  if (isEmailFormValid.value) {
    try {
      await accountSettingsApi.updateContact({
        email: emailForm.value.newEmail
      })
      showOperationFeedback('邮箱已成功更换', 'success')
      emailForm.value = {
        newEmail: ''
      }
      showEmailDialog.value = false
      await loadUserInfo()
    } catch (error: any) {
      showOperationFeedback(error.message || '更换邮箱失败', 'error')
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
</style>