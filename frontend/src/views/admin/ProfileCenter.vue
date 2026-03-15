<script lang="ts" setup>
import logger from '@/utils/logger'
import { reactive, ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  Avatar,
  CaretBottom,
  Collection,
  Delete,
  Document,
  DocumentChecked,
  Fold,
  HomeFilled,
  Lock,
  Management,
  Menu,
  OfficeBuilding,
  School,
  SwitchButton,
  User,
  UserFilled,
  ChatDotRound,
  Folder,
  Download,
  Setting,
  DataLine,
  Briefcase,
  MagicStick,
  ChatLineRound,
  WarningFilled,
  InfoFilled,
  Calendar,
  Phone,
  Postcard,
  Key,
  RefreshLeft,
  RefreshRight,
  Tools
} from '@element-plus/icons-vue'
import AdminUserService from '../../api/AdminUserService'
import UserService from '../../api/user'
import { useAuthStore } from '../../store/auth'
import { useSystemSettingsStore } from '../../store/systemSettings'

const router = useRouter()
const authStore = useAuthStore()
const systemSettingsStore = useSystemSettingsStore()

const userInfo = reactive({
  id: '',
  username: '',
  name: '',
  phone: '',
  role: 'admin',
  createTime: ''
})

const loading = ref(false)

const activeTab = ref('profile')

const editDialogVisible = ref(false)
const editingUser = reactive({
  id: 0,
  username: '',
  name: '',
  phone: ''
})

const passwordDialogVisible = ref(false)
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const passwordFormRef = ref(null)

const switchRoleDialogVisible = ref(false)
const switchRoleForm = reactive({
  targetRole: '',
  targetUser: '',
  adminPassword: ''
})

const targetUserList = ref([])
const targetUserListLoading = ref(false)

const switchRoleFormRef = ref(null)

const roleOptions = [
  { value: 'teacher', label: '教师端' },
  { value: 'student', label: '学生端' },
  { value: 'company', label: '企业端' }
]

const passwordRules = computed(() => ({
  oldPassword: [
    { required: true, message: '请输入原密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { 
      min: systemSettingsStore.minPasswordLength, 
      max: 20, 
      message: `密码长度应为${systemSettingsStore.minPasswordLength}-20位`, 
      trigger: 'blur' 
    },
    {
      validator: (rule, value, callback) => {
        if (!value) {
          callback()
          return
        }
        const complexity = systemSettingsStore.passwordComplexity
        let isValid = true
        let errorMsg = ''
        
        if (complexity.includes('lowercase') && !/[a-z]/.test(value)) {
          isValid = false
          errorMsg = '密码必须包含小写字母'
        } else if (complexity.includes('uppercase') && !/[A-Z]/.test(value)) {
          isValid = false
          errorMsg = '密码必须包含大写字母'
        } else if (complexity.includes('number') && !/\d/.test(value)) {
          isValid = false
          errorMsg = '密码必须包含数字'
        } else if (complexity.includes('special') && !/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>/?]/.test(value)) {
          isValid = false
          errorMsg = '密码必须包含特殊字符'
        }
        
        if (!isValid) {
          callback(new Error(errorMsg))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
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

const switchRoleRules = computed(() => ({
  targetUser: [
    { required: true, message: '请选择用户', trigger: 'change' }
  ],
  adminPassword: [
    { required: true, message: '请输入管理员密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ]
}))

const passwordPlaceholder = computed(() => {
  return '请输入新密码'
})

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

onMounted(async () => {
  await systemSettingsStore.loadSettings()
  await loadUserInfo()
})

const loadUserInfo = async () => {
  try {
    loading.value = true
    let currentUserId: number | undefined = authStore.user?.id ? parseInt(String(authStore.user.id)) : undefined

    if (!currentUserId) {
      const localStorageId = localStorage.getItem('userId')
      currentUserId = localStorageId ? parseInt(localStorageId) : undefined
    }

    if (!currentUserId || isNaN(currentUserId)) {
      ElMessage.warning('无法获取用户信息，请重新登录')
      return
    }

    const response = await AdminUserService.getAdminById(currentUserId)

    if (response && response.code === 200 && response.data) {
      const adminInfo = response.data
      userInfo.id = adminInfo.id
      userInfo.username = adminInfo.username
      userInfo.name = adminInfo.name || ''
      userInfo.phone = adminInfo.phone || ''
      userInfo.createTime = adminInfo.createTime || ''
    }
  } catch (error) {
    logger.error('获取用户信息失败:', error)
    ElMessage.error('获取用户信息失败')
  } finally {
    loading.value = false
  }
}

const showEditProfile = async () => {
  try {
    let currentUserId: number | undefined = authStore.user?.id ? parseInt(String(authStore.user.id)) : undefined

    if (!currentUserId) {
      const localStorageId = localStorage.getItem('userId')
      currentUserId = localStorageId ? parseInt(localStorageId) : undefined
    }

    if (!currentUserId || isNaN(currentUserId)) {
      ElMessage.warning('无法获取用户信息，请重新登录')
      return
    }

    const response = await AdminUserService.getAdminById(currentUserId)

    if (response && response.code === 200 && response.data) {
      const adminInfo = response.data
      editingUser.id = adminInfo.id
      editingUser.username = adminInfo.username
      editingUser.name = adminInfo.name || ''
      editingUser.phone = adminInfo.phone || ''
    } else {
      ElMessage.error('获取用户信息失败')
    }

    editDialogVisible.value = true
  } catch (error) {
    logger.error('获取管理员信息失败:', error)
    ElMessage.error('获取用户信息失败: ' + (error.message || '未知错误'))
  }
}

const handleEditDialogClose = () => {
  nextTick(() => {
    editingUser.id = 0
    editingUser.username = ''
    editingUser.name = ''
    editingUser.phone = ''
  })
}

const saveProfile = async () => {
  try {
    if (!editingUser.username) {
      ElMessage.warning('用户名不能为空')
      return
    }

    const updateData = {
      id: editingUser.id,
      username: editingUser.username,
      phone: editingUser.phone
    }

    const response = await AdminUserService.updateAdmin(updateData)

    if (response && response.code === 200) {
      ElMessage.success('资料更新成功')
      editDialogVisible.value = false
      await loadUserInfo()
    } else {
      ElMessage.error(response?.message || '资料更新失败')
    }
  } catch (error) {
    logger.error('更新用户资料失败:', error)
    ElMessage.error('资料更新失败')
  }
}

const showChangePassword = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  if (passwordFormRef.value) {
    passwordFormRef.value.resetFields()
  }
  passwordDialogVisible.value = true
}

const handlePasswordDialogClose = () => {
  nextTick(() => {
    if (passwordFormRef.value) {
      passwordFormRef.value.resetFields()
    }
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
  })
}

const changePassword = async () => {
  try {
    await passwordFormRef.value.validate()

    const passwordData = {
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    }

    const response = await AdminUserService.changeAdminPassword(passwordData)

    const result = response || response.data
    if (result && result.code === 200) {
      ElMessage.success('密码修改成功，请重新登录')
      passwordDialogVisible.value = false

      authStore.logout(true)
      localStorage.removeItem('isLoggedIn')
      localStorage.removeItem('token')
      localStorage.removeItem('role')
      localStorage.removeItem('userId')
      localStorage.removeItem('current_role')

      router.push('/login')
    } else {
      ElMessage.error(result ? (result.message || '密码修改失败') : '密码修改失败')
    }
  } catch (error) {
    logger.error('修改密码失败:', error)
    ElMessage.error('密码修改失败')
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

const showSwitchRoleDialog = (role) => {
  if (switchRoleFormRef.value) {
    switchRoleFormRef.value.resetFields()
  }
  switchRoleForm.targetRole = role || ''
  switchRoleForm.targetUser = ''
  switchRoleForm.adminPassword = ''
  targetUserList.value = []
  
  if (role) {
    handleTargetRoleChange(role)
  }
  
  switchRoleDialogVisible.value = true
}

const cancelSwitchRole = () => {
  switchRoleDialogVisible.value = false
  nextTick(() => {
    if (switchRoleFormRef.value) {
      switchRoleFormRef.value.resetFields()
    }
    switchRoleForm.targetRole = ''
    switchRoleForm.targetUser = ''
    switchRoleForm.adminPassword = ''
    targetUserList.value = []
  })
}

const handleSwitchRoleDialogClose = () => {
  switchRoleDialogVisible.value = false
  nextTick(() => {
    if (switchRoleFormRef.value) {
      switchRoleFormRef.value.resetFields()
    }
    switchRoleForm.targetRole = ''
    switchRoleForm.targetUser = ''
    switchRoleForm.adminPassword = ''
    targetUserList.value = []
  })
}

const handleTargetRoleChange = async (role) => {
  if (!role) {
    targetUserList.value = []
    switchRoleForm.targetUser = ''
    return
  }

  targetUserListLoading.value = true
  logger.log('[个人中心 - 角色切换] 开始加载目标用户列表，角色:', role)

  try {
    let users: any[] = []

    if (role === 'teacher') {
      // 查询所有教师子角色
      const teacherRoles = ['ROLE_TEACHER', 'ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR']
      const allUsers: any[] = []
      for (const teacherRole of teacherRoles) {
        try {
          logger.log(`正在获取教师角色 ${teacherRole} 的用户列表...`)
          const res = await UserService.getUsersByRole(teacherRole)
          logger.log(`获取 ${teacherRole} 用户列表结果:`, res, '是否数组:', Array.isArray(res))
          // getUsersByRole 返回的是 User[] 数组
          if (res && Array.isArray(res) && res.length > 0) {
            logger.log(`从 ${teacherRole} 获取到 ${res.length} 个用户，第一个用户:`, res[0])
            allUsers.push(...res)
          }
        } catch (e) {
          logger.warn(`查询角色 ${teacherRole} 失败:`, e)
        }
      }
      // 去重
      const uniqueUsers = allUsers.filter((user, index, self) =>
        index === self.findIndex((u) => u.id === user.id)
      )
      users = uniqueUsers
      logger.log('所有教师用户列表（去重后）:', users, '总人数:', users.length)
    } else if (role === 'student') {
      logger.log('正在获取学生用户列表...')
      users = await UserService.getUsersByRole('ROLE_STUDENT')
      logger.log('获取学生用户列表结果:', users, '是否数组:', Array.isArray(users))
      if (Array.isArray(users) && users.length > 0) {
        logger.log('第一个学生用户:', users[0])
      }
    } else if (role === 'company') {
      logger.log('正在获取企业用户列表...')
      users = await UserService.getUsersByRole('ROLE_COMPANY')
      logger.log('获取企业用户列表结果:', users, '是否数组:', Array.isArray(users))
      if (Array.isArray(users) && users.length > 0) {
        logger.log('第一个企业用户:', users[0])
      }
    }

    logger.log('[个人中心 - 角色切换] 最终获取到的用户列表:', users, '总人数:', users?.length)

    if (users && Array.isArray(users)) {
      // 使用展开运算符确保触发 Vue 响应式更新
      targetUserList.value = [...users]
      logger.log('[个人中心 - 角色切换] 目标用户列表已更新，新长度:', targetUserList.value.length)
    } else {
      targetUserList.value = []
      logger.warn('[个人中心 - 角色切换] 获取用户列表返回空结果或不是数组')
      ElMessage.warning('未找到相关用户')
    }
  } catch (error) {
    logger.error('[个人中心 - 角色切换] 获取用户列表失败:', error)
    targetUserList.value = []
    ElMessage.error('获取用户列表失败：' + (error.message || '未知错误'))
  } finally {
    targetUserListLoading.value = false
    logger.log('[个人中心 - 角色切换] 用户列表加载完成，loading 状态:', targetUserListLoading.value)
  }
}

const confirmSwitchRole = async () => {
  try {
    if (!switchRoleFormRef.value) {
      ElMessage.error('表单初始化失败，请刷新页面重试')
      return
    }
    
    await switchRoleFormRef.value.validate()
    
    let success = false
    try {
      const targetUser = targetUserList.value.find(u => u.username === switchRoleForm.targetUser)
      const targetUsername = targetUser ? targetUser.username : switchRoleForm.targetUser
      success = await authStore.switchRole(switchRoleForm.targetRole, targetUsername, switchRoleForm.adminPassword)
    } catch (error) {
      ElMessage.error('角色切换失败: ' + (error.message || '未知错误'))
    }
    
    if (success) {
      ElMessage.success('角色切换成功')
      switchRoleDialogVisible.value = false
      
      const targetRole = switchRoleForm.targetRole
      const targetRoute = getTargetRoute(targetRole)
      
      switchRoleForm.targetRole = ''
      switchRoleForm.targetUser = ''
      switchRoleForm.adminPassword = ''
      targetUserList.value = []
      
      if (targetRoute) {
        await router.push(targetRoute)
      }
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('角色切换失败: ' + (error.message || '未知错误'))
    }
  }
}

const getTargetRoute = (targetRole) => {
  const routes = {
    'teacher': '/teacher/dashboard',
    'student': '/student/dashboard',
    'company': '/company/dashboard'
  }
  return routes[targetRole] || null
}

const getRoleLabel = (roleValue) => {
  const role = roleOptions.find(option => option.value === roleValue)
  return role ? role.label : ''
}

const logout = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要退出登录吗？退出后需要重新登录才能访问系统。',
      '确认退出',
      {
        confirmButtonText: '确定退出',
        cancelButtonText: '取消',
        type: 'warning',
        distinguishCancelAndClose: true
      }
    )
    
    await authStore.logout()
    ElMessage.success('退出登录成功')
    router.push('/login')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      logger.error('退出登录失败:', error)
      ElMessage.error('退出登录失败，请稍后重试')
    }
  }
}

const goBack = () => {
  router.back()
}
</script>

<template>
  <div class="profile-container">
    <div class="profile-header">
      <el-page-header @back="goBack" title="返回">
        <template #content>
          <div class="page-title">
            <el-icon class="title-icon">
              <User />
            </el-icon>
            <span>个人中心</span>
          </div>
        </template>
      </el-page-header>
    </div>

    <div class="profile-content">
      <el-card class="profile-card" shadow="hover">
        <el-tabs v-model="activeTab" class="profile-tabs" @tab-change="handleTabChange">
          <el-tab-pane label="基本信息" name="profile">
            <div class="info-section" v-loading="loading">
              <div class="info-header">
                <div class="header-left">
                  <div class="avatar-wrapper">
                    <el-icon class="avatar-icon">
                      <UserFilled />
                    </el-icon>
                  </div>
                  <div class="user-title">
                    <h3>{{ userInfo.name || userInfo.username }}</h3>
                    <p class="user-role">管理员</p>
                  </div>
                </div>
                <el-button type="danger" @click="logout" class="logout-btn">
                  退出登录
                </el-button>
              </div>

              <div class="info-list">
                <div class="info-item">
                  <div class="info-label">
                    <el-icon><Postcard /></el-icon>
                    <span>用户名</span>
                  </div>
                  <div class="info-value">{{ userInfo.username }}</div>
                </div>

                <div class="info-item">
                  <div class="info-label">
                    <el-icon><User /></el-icon>
                    <span>真实姓名</span>
                  </div>
                  <div class="info-value">{{ userInfo.name || '-' }}</div>
                </div>

                <div class="info-item">
                  <div class="info-label">
                    <el-icon><Phone /></el-icon>
                    <span>手机号</span>
                  </div>
                  <div class="info-value">{{ userInfo.phone || '-' }}</div>
                </div>

                <div class="info-item">
                  <div class="info-label">
                    <el-icon><Calendar /></el-icon>
                    <span>创建时间</span>
                  </div>
                  <div class="info-value">{{ userInfo.createTime || '-' }}</div>
                </div>
              </div>

              <div class="action-buttons">
                <el-button type="primary" @click="showEditProfile" :icon="Tools">
                  编辑资料
                </el-button>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="修改密码" name="password">
            <div class="password-section">
              <div class="section-header">
                <el-icon class="section-icon">
                  <Lock />
                </el-icon>
                <h3>修改密码</h3>
              </div>
              <p class="section-desc">为了账户安全，建议定期更换密码</p>

              <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="120px" class="password-form">
                <el-form-item label="原密码" prop="oldPassword">
                  <el-input v-model="passwordForm.oldPassword" type="password" placeholder="请输入原密码" show-password />
                </el-form-item>

                <el-form-item label="新密码" prop="newPassword">
                  <el-input v-model="passwordForm.newPassword" type="password" :placeholder="passwordPlaceholder" show-password />
                  <div class="password-requirements">{{ passwordRequirements }}</div>
                </el-form-item>

                <el-form-item label="确认新密码" prop="confirmPassword">
                  <el-input v-model="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码" show-password />
                </el-form-item>

                <el-form-item>
                  <el-button type="primary" @click="changePassword" :icon="Key">
                    确认修改
                  </el-button>
                </el-form-item>
              </el-form>
            </div>
          </el-tab-pane>

          <el-tab-pane label="角色切换" name="switch">
            <div class="switch-section">
              <div class="section-header">
                <el-icon class="section-icon">
                  <SwitchButton />
                </el-icon>
                <h3>角色切换</h3>
              </div>
              <p class="section-desc">切换到其他角色以体验不同端的功能</p>

              <div class="role-cards">
                <div class="role-card" @click="showSwitchRoleDialog('teacher')">
                  <el-icon class="role-icon teacher">
                    <Avatar />
                  </el-icon>
                  <h4>教师端</h4>
                  <p>管理学生实习、审核申请等</p>
                </div>

                <div class="role-card" @click="showSwitchRoleDialog('student')">
                  <el-icon class="role-icon student">
                    <UserFilled />
                  </el-icon>
                  <h4>学生端</h4>
                  <p>查看实习信息、提交申请等</p>
                </div>

                <div class="role-card" @click="showSwitchRoleDialog('company')">
                  <el-icon class="role-icon company">
                    <OfficeBuilding />
                  </el-icon>
                  <h4>企业端</h4>
                  <p>发布职位、管理招聘等</p>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>

    <el-dialog v-model="editDialogVisible" title="编辑个人资料" width="500px" @close="handleEditDialogClose">
      <el-form :model="editingUser" label-width="100px">
        <el-form-item label="真实姓名">
          <el-input v-model="editingUser.name" disabled />
          <div class="form-tip">真实姓名由管理员设置，不可修改</div>
        </el-form-item>
        
        <el-form-item label="登录用户名" prop="username">
          <el-input v-model="editingUser.username" placeholder="请输入用户名" />
          <div class="form-tip">修改用户名后需要重新登录</div>
        </el-form-item>

        <el-form-item label="手机号" prop="phone">
          <el-input v-model="editingUser.phone" placeholder="请输入手机号" />
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="handleEditDialogClose(); editDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveProfile">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <el-dialog v-model="switchRoleDialogVisible" title="角色切换" width="500px" :close-on-click-modal="false" @close="handleSwitchRoleDialogClose">
      <el-form ref="switchRoleFormRef" :model="switchRoleForm" :rules="switchRoleRules" label-width="120px">
        <el-form-item label="目标角色" prop="targetRole">
          <el-input 
            :value="getRoleLabel(switchRoleForm.targetRole)" 
            disabled 
          />
          <div class="form-tip">已选择目标角色，无需修改</div>
        </el-form-item>
        <el-form-item label="选择用户" prop="targetUser">
          <el-select 
            v-model="switchRoleForm.targetUser" 
            placeholder="请选择或输入用户名" 
            :loading="targetUserListLoading" 
            :disabled="!switchRoleForm.targetRole"
            filterable
            allow-create
            clearable
            style="width: 100%"
          >
            <el-option 
              v-for="user in targetUserList" 
              :key="user.id" 
              :label="`${user.name} (${user.username})`" 
              :value="user.username"
            >
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <span>{{ user.name }}</span>
                <span style="color: #8492a6; font-size: 13px;">{{ user.username }}</span>
              </div>
            </el-option>
          </el-select>
          <div class="form-tip">
            提示：可以从下拉列表中选择用户，也可以直接输入用户名
          </div>
        </el-form-item>
        <el-form-item label="管理员密码" prop="adminPassword">
          <el-input
            v-model="switchRoleForm.adminPassword"
            type="password"
            placeholder="请输入管理员密码进行验证"
            show-password
            clearable
          />
          <div class="form-tip">
            ⚠️ 切换角色需要管理员密码验证
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="cancelSwitchRole">取消</el-button>
          <el-button type="primary" @click="confirmSwitchRole">确认切换</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.profile-container {
  padding: 30px;
  background: #f5f7fa;
  min-height: 100vh;
}

.profile-header {
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

.profile-content {
  max-width: 1200px;
  margin: 0 auto;
}

.profile-card {
  margin-bottom: 30px;
}

.profile-card :deep(.el-card__body) {
  padding: 40px;
}

.profile-tabs {
  min-height: 500px;
}

.profile-tabs :deep(.el-tabs__header) {
  margin-bottom: 30px;
}

.profile-tabs :deep(.el-tabs__item) {
  font-size: 16px;
  padding: 0 30px;
  height: 50px;
  line-height: 50px;
}

.profile-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 2px;
}

.info-section {
  padding: 30px 0;
}

.info-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 40px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 40px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 30px;
}

.logout-btn {
  white-space: nowrap;
  padding: 12px 30px;
  font-size: 16px;
}

.avatar-wrapper {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: linear-gradient(135deg, #06b6d4 0%, #3b82f6 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(6, 182, 212, 0.3);
}

.avatar-icon {
  font-size: 60px;
  color: white;
}

.user-title h3 {
  margin: 0 0 12px 0;
  font-size: 32px;
  font-weight: 600;
  color: #303133;
}

.user-role {
  margin: 0;
  font-size: 16px;
  color: #909399;
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.info-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 30px;
  background: #f8f9fa;
  border-radius: 12px;
  transition: all 0.3s ease;
}

.info-item:hover {
  background: #e8f4ff;
  transform: translateX(6px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
}

.info-label {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  color: #606266;
  font-weight: 500;
}

.info-label .el-icon {
  font-size: 20px;
  color: #409EFF;
}

.info-value {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.action-buttons {
  display: flex;
  justify-content: center;
  margin-top: 40px;
}

.action-buttons .el-button {
  padding: 12px 40px;
  font-size: 16px;
}

.password-section,
.switch-section {
  padding: 30px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}

.section-icon {
  font-size: 32px;
  color: #409EFF;
}

.section-header h3 {
  margin: 0;
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.section-desc {
  margin: 0 0 40px 0;
  font-size: 16px;
  color: #909399;
}

.password-form {
  max-width: 600px;
}

.password-form :deep(.el-form-item__label) {
  font-size: 16px;
  font-weight: 500;
  color: #606266;
}

.password-form :deep(.el-input__inner) {
  height: 48px;
  font-size: 16px;
}

.password-form :deep(.el-button) {
  padding: 12px 36px;
  font-size: 16px;
}

.role-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 24px;
  margin-top: 32px;
}

.role-card {
  padding: 36px 28px;
  background: #ffffff;
  border: 1.5px solid #e4e7ed;
  border-radius: 12px;
  text-align: left;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  color: #303133;
  position: relative;
  overflow: hidden;
}

.role-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, #409EFF, #66b1ff);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.role-card:hover::before {
  opacity: 1;
}

.role-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  border-color: #409EFF;
}

.role-icon {
  font-size: 36px;
  margin-bottom: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: #f5f7fa;
  transition: all 0.3s ease;
}

.role-card:hover .role-icon {
  background: #ecf5ff;
  transform: scale(1.08);
}

.role-icon.teacher {
  color: #409EFF;
}

.role-icon.student {
  color: #67C23A;
}

.role-icon.company {
  color: #E6A23C;
}

.role-card h4 {
  margin: 0 0 10px 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  text-align: right;
}

.role-card p {
  margin: 0;
  font-size: 14px;
  color: #909399;
  line-height: 1.6;
  text-align: right;
}

.form-tip {
  margin-top: 6px;
  font-size: 14px;
  color: #909399;
  line-height: 1.6;
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
