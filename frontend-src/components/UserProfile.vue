<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <h2>个人中心</h2>
      </template>
      
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="基本信息" name="info">
          <el-form :model="userInfo" label-width="120px" class="info-form">
            <el-form-item label="用户类型">
              <el-tag>{{ getUserTypeText() }}</el-tag>
            </el-form-item>
            
            <el-form-item label="登录用户名">
              <el-input v-model="userInfo.username" disabled />
              <div class="form-tip">登录用户名，用于登录系统</div>
            </el-form-item>
            
            <el-form-item label="真实姓名">
              <el-input v-model="userInfo.name" disabled />
              <div class="form-tip">真实姓名，由管理员设置，不可修改</div>
            </el-form-item>
            
            <el-form-item label="创建时间">
              <el-input v-model="userInfo.createTime" disabled />
            </el-form-item>
          </el-form>
          
          <el-button type="primary" @click="showEditUsernameDialog">修改登录用户名</el-button>
        </el-tab-pane>
        
        <el-tab-pane label="修改密码" name="password">
          <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="120px" class="password-form">
            <el-form-item label="旧密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
            </el-form-item>
            
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password :placeholder="passwordPlaceholder" />
              <div class="password-requirements">{{ passwordRequirements }}</div>
            </el-form-item>
            
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
            </el-form-item>
            
            <el-form-item>
              <el-button type="primary" @click="changePassword" :loading="passwordLoading">修改密码</el-button>
              <el-button @click="resetPasswordForm">重置</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
    
    <el-dialog v-model="editUsernameDialogVisible" title="修改登录用户名" width="500px" @close="handleUsernameDialogClose">
      <el-form :model="usernameForm" :rules="usernameRules" ref="usernameFormRef" label-width="120px">
        <el-form-item label="当前用户名">
          <el-input v-model="usernameForm.oldUsername" disabled />
        </el-form-item>
        
        <el-form-item label="新用户名" prop="newUsername">
          <el-input v-model="usernameForm.newUsername" placeholder="请输入新用户名（3-20位）" />
          <div class="form-tip">修改用户名后需要重新登录</div>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="handleUsernameDialogClose(); editUsernameDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="updateUsername" :loading="usernameLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import { useSystemSettingsStore } from '@/store/systemSettings'
import UserService from '@/api/user'
import { useRouter } from 'vue-router'

const router = useRouter()
const authStore = useAuthStore()
const systemSettingsStore = useSystemSettingsStore()

const activeTab = ref('info')
const passwordLoading = ref(false)
const usernameLoading = ref(false)
const editUsernameDialogVisible = ref(false)

const userInfo = reactive({
  username: '',
  name: '',
  createTime: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const usernameForm = reactive({
  oldUsername: '',
  newUsername: ''
})

interface PasswordData {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

interface UsernameData {
  oldUsername: string
  newUsername: string
}

const passwordFormRef = ref<HTMLElement & { validate: () => Promise<boolean>; resetFields: () => void } | null>(null)
const usernameFormRef = ref<HTMLElement & { validate: () => Promise<boolean>; resetFields: () => void } | null>(null)

const passwordRules = computed(() => ({
  oldPassword: [
    { required: true, message: '请输入旧密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { 
      min: systemSettingsStore.minPasswordLength, 
      max: 20, 
      message: `密码长度必须在${systemSettingsStore.minPasswordLength}-20个字符之间`, 
      trigger: 'blur' 
    }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}))

const passwordPlaceholder = computed(() => '请输入新密码')

const passwordRequirements = computed(() => {
  const complexityText = systemSettingsStore.passwordComplexity.map(item => {
    const map = {
      'lowercase': '小写字母',
      'uppercase': '大写字母',
      'number': '数字',
      'special': '特殊字符'
    }
    return map[item] || item
  }).join('、')
  return `密码长度：${systemSettingsStore.minPasswordLength}-20位，必须包含：${complexityText}`
})

const usernameRules = {
  newUsername: [
    { required: true, message: '请输入新用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度必须在3-20个字符之间', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value === usernameForm.oldUsername) {
          callback(new Error('新用户名不能与旧用户名相同'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const getUserTypeText = () => {
  const role = authStore.role
  const roleMap = {
    'ROLE_ADMIN': '管理员',
    'ROLE_TEACHER': '教师',
    'ROLE_TEACHER_COLLEGE': '学院教师',
    'ROLE_TEACHER_DEPARTMENT': '系室教师',
    'ROLE_TEACHER_COUNSELOR': '辅导员',
    'ROLE_STUDENT': '学生',
    'ROLE_COMPANY': '企业'
  }
  return roleMap[role] || '用户'
}

const loadUserInfo = () => {
  if (authStore.user) {
    userInfo.username = authStore.user.username || ''
    userInfo.name = authStore.user.name || ''
    const createTime = authStore.user.createTime
    userInfo.createTime = createTime ? new Date(String(createTime)).toLocaleString() : ''
  }
}

const handleTabChange = (tabName) => {
  if (tabName === 'password' && passwordFormRef.value) {
    passwordFormRef.value.resetFields()
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
  }
}

const showEditUsernameDialog = () => {
  if (usernameFormRef.value) {
    usernameFormRef.value.resetFields()
  }
  usernameForm.oldUsername = userInfo.username
  usernameForm.newUsername = ''
  editUsernameDialogVisible.value = true
}

const handleUsernameDialogClose = () => {
  nextTick(() => {
    if (usernameFormRef.value) {
      usernameFormRef.value.resetFields()
    }
    usernameForm.oldUsername = ''
    usernameForm.newUsername = ''
  })
}

const updateUsername = async () => {
  if (!usernameFormRef.value) return
  
  try {
    await usernameFormRef.value.validate()
    usernameLoading.value = true
    
    const response = await UserService.updateUsername({
      username: usernameForm.oldUsername,
      newUsername: usernameForm.newUsername
    })
    
    if (response.data && response.data.code === 200) {
      ElMessage.success('用户名修改成功，请重新登录')
      editUsernameDialogVisible.value = false
      
      setTimeout(() => {
        authStore.logout()
        router.push('/login')
      }, 1500)
    } else {
      ElMessage.error(response.data?.message || '用户名修改失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '用户名修改失败')
    }
  } finally {
    usernameLoading.value = false
  }
}

const changePassword = async () => {
  if (!passwordFormRef.value) return

  try {
    await passwordFormRef.value.validate()
    passwordLoading.value = true

    const passwordData: PasswordData = {
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
      confirmPassword: passwordForm.confirmPassword
    }

    const response = await UserService.changePassword(passwordData)

    if (response.data && response.data.code === 200) {
      ElMessage.success('密码修改成功，请重新登录')
      resetPasswordForm()

      authStore.logout()
      router.push('/login')
    } else {
      ElMessage.error(response.data?.message || '密码修改失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '密码修改失败')
    }
  } finally {
    passwordLoading.value = false
  }
}

const resetPasswordForm = () => {
  if (passwordFormRef.value) {
    passwordFormRef.value.resetFields()
  }
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

onMounted(() => {
  loadUserInfo()
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.info-form,
.password-form {
  max-width: 600px;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 5px;
}

.password-requirements {
  margin-top: 8px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  border-left: 3px solid #409EFF;
}
</style>
