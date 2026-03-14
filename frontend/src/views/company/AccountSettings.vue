<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import CompanyService from '@/api/CompanyService'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const activeTab = ref('profile')
const loading = ref(false)

// 获取当前用户信息
const currentUser = computed(() => authStore.user)
const displayName = computed(() => {
  if (currentUser.value?.name) {
    return currentUser.value.name
  }
  if (currentUser.value?.username) {
    return currentUser.value.username
  }
  return '企业用户'
})

// 邮箱脱敏显示
const maskedEmail = computed(() => {
  const email = profileForm.value.email
  if (!email || !email.includes('@')) {
    return '未绑定'
  }
  const parts = email.split('@')
  const username = parts[0]
  const domain = parts[1]
  if (username.length <= 2) {
    return username + '***@' + domain
  }
  return username.charAt(0) + '***' + username.charAt(username.length - 1) + '@' + domain
})

// 手机脱敏显示
const maskedPhone = computed(() => {
  const phone = profileForm.value.phone
  if (!phone || phone.length < 11) {
    return '未绑定'
  }
  return phone.substring(0, 3) + '****' + phone.substring(7)
})

onMounted(async () => {
  if (route.query.tab) {
    activeTab.value = route.query.tab
  }
  await loadProfile()
})

const loadProfile = async () => {
  loading.value = true
  try {
    const response = await CompanyService.getProfile()
    if (response.code === 200 && response.data) {
      profileForm.value = {
        username: response.data.username || '',
        phone: response.data.phone || '',
        email: response.data.email || '',
        companyName: response.data.companyName || '',
        industry: response.data.industry || '',
        scale: response.data.scale || ''
      }
    }
  } catch (error) {
    console.error('加载个人信息失败:', error)
    ElMessage.error('加载个人信息失败')
  } finally {
    loading.value = false
  }
}

const profileForm = ref({
  username: '',
  phone: '',
  email: '',
  companyName: '',
  industry: '',
  scale: ''
})

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const passwordRules = {
  oldPassword: [
    { required: true, message: '请输入原密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度应为6-20位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.value.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const passwordFormRef = ref(null)

const handleSaveProfile = async () => {
  try {
    const response = await CompanyService.updateProfile({
      username: profileForm.value.username,
      phone: profileForm.value.phone,
      email: profileForm.value.email
    })
    if (response.code === 200) {
      ElMessage.success('个人信息保存成功')
    } else {
      ElMessage.error(response.message || '保存失败')
    }
  } catch (error) {
    console.error('保存个人信息失败:', error)
    ElMessage.error('保存失败，请稍后重试')
  }
}

const handleChangePassword = async () => {
  if (!passwordFormRef.value) return
  
  await passwordFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        const response = await CompanyService.changePassword({
          oldPassword: passwordForm.value.oldPassword,
          newPassword: passwordForm.value.newPassword
        })
        if (response.code === 200) {
          ElMessage.success('密码修改成功')
          passwordForm.value = {
            oldPassword: '',
            newPassword: '',
            confirmPassword: ''
          }
          passwordFormRef.value.resetFields()
        } else {
          ElMessage.error(response.message || '密码修改失败')
        }
      } catch (error) {
        console.error('修改密码失败:', error)
        ElMessage.error('密码修改失败，请稍后重试')
      }
    }
  })
}

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要退出登录吗？',
      '退出登录',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
        center: true
      }
    )
    await authStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}
</script>

<template>
  <div class="account-settings">
    <div class="page-header">
      <h2>账号设置</h2>
      <p>管理您的账号信息和安全设置</p>
    </div>

    <el-tabs v-model="activeTab" class="settings-tabs">
      <el-tab-pane label="个人信息" name="profile">
        <div class="form-card">
          <el-form :model="profileForm" label-width="120px" label-position="left">
            <el-form-item label="用户名">
              <el-input v-model="profileForm.username" placeholder="请输入用户名" />
            </el-form-item>

            <el-form-item label="手机号">
              <el-input v-model="profileForm.phone" placeholder="请输入手机号" />
            </el-form-item>

            <el-form-item label="邮箱">
              <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
            </el-form-item>

            <el-form-item label="企业名称">
              <el-input v-model="profileForm.companyName" placeholder="请输入企业名称" disabled />
            </el-form-item>

            <el-form-item label="所属行业">
              <el-input v-model="profileForm.industry" placeholder="所属行业" disabled />
            </el-form-item>

            <el-form-item label="企业规模">
              <el-input v-model="profileForm.scale" placeholder="企业规模" disabled />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="handleSaveProfile">保存个人信息</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <el-tab-pane label="修改密码" name="password">
        <div class="form-card">
          <el-form 
            ref="passwordFormRef"
            :model="passwordForm" 
            :rules="passwordRules"
            label-width="120px" 
            label-position="left"
          >
            <el-form-item label="原密码" prop="oldPassword">
              <el-input 
                v-model="passwordForm.oldPassword" 
                type="password" 
                placeholder="请输入原密码" 
                show-password 
              />
            </el-form-item>

            <el-form-item label="新密码" prop="newPassword">
              <el-input 
                v-model="passwordForm.newPassword" 
                type="password" 
                placeholder="请输入新密码（6-20位）" 
                show-password 
              />
            </el-form-item>

            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input 
                v-model="passwordForm.confirmPassword" 
                type="password" 
                placeholder="请再次输入新密码" 
                show-password 
              />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="handleChangePassword">确认修改</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <el-tab-pane label="账号安全" name="security">
        <div class="security-card">
          <div class="security-item">
            <div class="security-info">
              <h4>登录密码</h4>
              <p>定期修改密码可以保护账号安全</p>
            </div>
            <el-button type="primary" @click="activeTab = 'password'">修改密码</el-button>
          </div>

          <div class="security-item">
            <div class="security-info">
              <h4>绑定手机</h4>
              <p>已绑定：{{ maskedPhone }}</p>
            </div>
            <el-button>更换手机</el-button>
          </div>

          <div class="security-item">
            <div class="security-info">
              <h4>绑定邮箱</h4>
              <p>已绑定：{{ maskedEmail }}</p>
            </div>
            <el-button>更换邮箱</el-button>
          </div>

          <div class="security-item danger">
            <div class="security-info">
              <h4>退出登录</h4>
              <p>退出后需要重新登录才能访问</p>
            </div>
            <el-button type="danger" @click="handleLogout">退出登录</el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.account-settings {
  padding: 0;
}

.page-header {
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  color: white;
  padding: 24px 40px;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(64, 158, 255, 0.3);
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 8px 0;
  font-size: 28px;
  font-weight: bold;
  letter-spacing: 1px;
}

.page-header p {
  margin: 0;
  font-size: 14px;
  opacity: 0.95;
}

.settings-tabs {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.form-card {
  padding: 30px;
}

:deep(.el-tabs__header) {
  margin: 0;
  padding: 0 20px;
  background: #f5f7fa;
  border-bottom: 1px solid #e8e8e8;
}

:deep(.el-tabs__nav-wrap::after) {
  display: none;
}

:deep(.el-tabs__item) {
  padding: 0 24px;
  height: 50px;
  line-height: 50px;
  font-size: 15px;
  font-weight: 500;
  color: #606266;
}

:deep(.el-tabs__item.is-active) {
  color: #409EFF;
  font-weight: 600;
}

:deep(.el-tabs__active-bar) {
  background-color: #409EFF;
  height: 3px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: #333;
}

:deep(.el-input__wrapper) {
  border-radius: 6px;
}

:deep(.el-input.is-disabled .el-input__wrapper) {
  background-color: #f5f7fa;
}

.security-card {
  padding: 30px;
}

.security-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px;
  border-bottom: 1px solid #f0f0f0;
  transition: all 0.3s ease;
}

.security-item:last-child {
  border-bottom: none;
}

.security-item:hover {
  background: #f9fafc;
}

.security-item.danger:hover {
  background: #fef0f0;
}

.security-info {
  flex: 1;
}

.security-info h4 {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.security-info p {
  margin: 0;
  font-size: 14px;
  color: #909399;
}

:deep(.el-button) {
  border-radius: 6px;
  padding: 10px 20px;
  font-weight: 500;
}

:deep(.el-button--primary) {
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  border: none;
}

:deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, #66b1ff 0%, #73d13d 100%);
}

:deep(.el-button--danger) {
  background: #f56c6c;
  border: none;
}

:deep(.el-button--danger:hover) {
  background: #f78989;
}
</style>
