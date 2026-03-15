<!-- src/layouts/MainLayout.vue -->
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
  Tools
} from '@element-plus/icons-vue'
import AdminUserService from '../../api/AdminUserService'
import StudentUserService from '../../api/StudentUserService'
import TeacherUserService from '../../api/TeacherUserService'
import UserService from '../../api/user'
import { useAuthStore } from '../../store/auth'
import { useSystemSettingsStore } from '../../store/systemSettings'
import { useLogStore } from '../../store/log'
import request from '../../utils/request'
import ClassService from '../../api/class'
import DepartmentService from '../../api/department'
import MajorService from '../../api/major'
import { cleanLogsApi } from '../../api/log'
import companyService from '../../api/company'
import PositionCategoryService from '../../api/positionCategory'
import * as announcementApi from '../../api/announcement'
import * as feedbackApi from '../../api/problemFeedback'
import positionService from '../../api/position'
import { useUserStatusCheck } from '../../composables/useUserStatusCheck'

useUserStatusCheck(180000)

const router = useRouter()
const authStore = useAuthStore()
const systemSettingsStore = useSystemSettingsStore()

// 编辑资料对话框相关状态
const editDialogVisible = ref(false)
const editingUser = reactive({
  id: 0,
  username: '',
  name: '',
  phone: ''
})

const editingUserFormRef = ref(null)

const editingUserRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为3-20个字符', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ]
}

// 角色切换对话框相关状态
const switchRoleDialogVisible = ref(false)
const switchRoleForm = reactive({
  targetRole: '',
  targetUser: '',
  adminPassword: ''
})

const targetUserList = ref([])
const targetUserListLoading = ref(false)

const switchRoleRules = computed(() => ({
  targetRole: [
    { required: true, message: '请选择目标角色', trigger: 'change' }
  ],
  targetUser: [
    { required: true, message: '请选择用户', trigger: 'change' }
  ],
  adminPassword: [
    { required: true, message: '请输入管理员密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ]
}))

const switchRoleFormRef = ref(null)

const roleOptions = [
  { value: 'teacher', label: '教师端' },
  { value: 'student', label: '学生端' },
  { value: 'company', label: '企业端' }
]

const getRoleLabel = (role) => {
  const option = roleOptions.find(opt => opt.value === role)
  return option ? option.label : ''
}

// 修改密码对话框相关状态
const passwordDialogVisible = ref(false)
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

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

const passwordFormRef = ref(null)

const sidebarCollapsed = ref(false)

const adminPermissionsLoaded = ref(false)

const loadAdminPermissions = async () => {
  try {
    const response = await request.get('/admin/permissions/roles/1/permissions')
    logger.log('权限接口响应:', response)
    logger.log('response.data:', response.data)
    if (response && response.code === 200 && response.data) {
      const permissions = response.data
      localStorage.setItem('adminPermissions', JSON.stringify(permissions))
      adminPermissionsLoaded.value = true
      logger.log('管理员权限列表加载成功，数量:', permissions.length)
      logger.log('权限列表:', permissions.map(p => p.permissionCode))
      const withdrawalPerm = permissions.find(p => p.permissionCode === 'application:withdrawal:view')
      logger.log('撤回申请记录权限:', withdrawalPerm)
      const confirmPerm = permissions.find(p => p.permissionCode === 'internship:confirm:view')
      logger.log('实习确认表权限:', confirmPerm)
    } else {
      logger.error('权限接口返回格式错误:', response.data)
    }
  } catch (error) {
    logger.error('加载管理员权限列表失败:', error)
  }
}

onMounted(() => {
  systemSettingsStore.loadSettings()
  loadAdminPermissions()
})

const hasPermission = (permissionCode) => {
  return authStore.hasPermission(permissionCode)
}

const menuPermissions = {
  '/admin/teacher-users': 'user:teacher:view',
  '/admin/student-users': 'user:student:view',
  '/admin/companies': 'user:company:view',
  '/admin/positions': 'position:view',
  '/admin/position-categories': 'position-category:view',
  '/admin/classes': 'class:view',
  '/admin/majors': 'major:view',
  '/admin/departments': 'department:view',
  '/admin/announcements': 'announcement:view',
  '/admin/resource-documents': 'resource:view',
  '/admin/problem-feedback': 'problem:view',
  '/admin/data-statistics/active-statistics': 'statistics:view',
  '/admin/logs': 'log:view',
  '/admin/permissions': 'permission:view',
  '/admin/backup': 'backup:view',
  '/admin/system-settings': 'system:settings:view',
  '/admin/keyword-library': 'keyword:view',
  '/admin/ai-model': 'ai:model:view',
  '/admin/ai-test': 'ai:test:view',
  '/admin/scoring-rule': 'scoring:view',
  '/admin/companies/audit': 'user:company:audit',
  '/admin/application-withdrawal-records': 'application:withdrawal:view',
  '/admin/internship-confirm-application': 'internship:confirm:view',
  '/admin/recruitment': 'recruitment:view'
}

const canShowMenu = (menuPath) => {
  const permissionCode = menuPermissions[menuPath]
  if (!permissionCode) return true
  if (!adminPermissionsLoaded.value) {
    logger.log('canShowMenu - 权限未加载完成:', menuPath)
    return false
  }
  const result = hasPermission(permissionCode)
  logger.log('canShowMenu - 菜单:', menuPath, '权限码:', permissionCode, '结果:', result)
  return result
}

// 初始化store
const logStore = useLogStore()

// 切换侧边栏显示状态
const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

// 测试导航
const testNavigation = () => {
  router.push('/admin/data-statistics/active-statistics')
}

const handleCommand = (command) => {
  switch (command) {
    case 'switch':
      showSwitchRoleDialog()
      break
    case 'edit':
      showEditProfile()
      break
    case 'password':
      showChangePassword()
      break
    case 'logout':
      logout()
      break
  }
}

// 显示角色切换对话框
const showSwitchRoleDialog = () => {
  if (switchRoleFormRef.value) {
    switchRoleFormRef.value.resetFields()
  }
  switchRoleForm.targetRole = ''
  switchRoleForm.targetUser = ''
  switchRoleForm.adminPassword = ''
  targetUserList.value = []
  switchRoleDialogVisible.value = true
}

// 取消角色切换
const cancelSwitchRole = () => {
  switchRoleDialogVisible.value = false
  switchRoleForm.targetRole = ''
  switchRoleForm.targetUser = ''
  switchRoleForm.adminPassword = ''
  targetUserList.value = []
}

// 处理角色切换对话框关闭
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

// 处理目标角色变化
const handleTargetRoleChange = async (role) => {
  if (!role) {
    targetUserList.value = []
    switchRoleForm.targetUser = ''
    return
  }

  targetUserListLoading.value = true
  try {
    let users: any[] = []

    if (role === 'teacher') {
      // 查询所有教师子角色：ROLE_TEACHER, ROLE_TEACHER_COLLEGE, ROLE_TEACHER_DEPARTMENT, ROLE_TEACHER_COUNSELOR
      const teacherRoles = ['ROLE_TEACHER', 'ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR']
      const allUsers: any[] = []
      for (const teacherRole of teacherRoles) {
        try {
          logger.log(`正在获取教师角色 ${teacherRole} 的用户列表...`)
          const res = await UserService.getUsersByRole(teacherRole)
          logger.log(`获取 ${teacherRole} 用户列表结果:`, res)
          logger.log(`获取 ${teacherRole} 用户列表 - 是否数组:`, Array.isArray(res))
          if (Array.isArray(res) && res.length > 0) {
            logger.log(`第一个用户对象结构:`, res[0])
            logger.log(`第一个用户的 id:`, res[0].id, 'username:', res[0].username, 'name:', res[0].name)
          }
          // getUsersByRole 返回的是 User[] 数组
          if (res && Array.isArray(res)) {
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
      logger.log('所有教师用户列表（去重后）:', users)
      logger.log('去重后总人数:', users.length)
    } else if (role === 'student') {
      users = await UserService.getUsersByRole('ROLE_STUDENT')
      logger.log('获取学生用户列表结果:', users)
      logger.log('学生用户列表是否数组:', Array.isArray(users))
      if (Array.isArray(users) && users.length > 0) {
        logger.log('第一个学生用户对象:', users[0])
      }
    } else if (role === 'company') {
      users = await UserService.getUsersByRole('ROLE_COMPANY')
      logger.log('获取企业用户列表结果:', users)
      logger.log('企业用户列表是否数组:', Array.isArray(users))
      if (Array.isArray(users) && users.length > 0) {
        logger.log('第一个企业用户对象:', users[0])
      }
    }

    logger.log('准备更新 targetUserList，当前长度:', targetUserList.value.length)
    if (users && Array.isArray(users)) {
      // 使用展开运算符确保触发 Vue 响应式更新
      targetUserList.value = [...users]
      logger.log('目标用户列表已更新，新长度:', targetUserList.value.length)
      logger.log('targetUserList.value 内容:', targetUserList.value)
    } else {
      targetUserList.value = []
      logger.warn('获取用户列表返回空结果或不是数组')
      ElMessage.warning('未找到相关用户')
    }
  } catch (error) {
    logger.error('获取用户列表失败:', error)
    targetUserList.value = []
    ElMessage.error('获取用户列表失败：' + (error.message || '未知错误'))
  } finally {
    targetUserListLoading.value = false
  }
}

// 确认角色切换
const confirmSwitchRole = async () => {
  logger.log('[角色切换] 开始确认切换')
  logger.log('[角色切换] switchRoleFormRef.value:', switchRoleFormRef.value)
  logger.log('[角色切换] 表单数据:', switchRoleForm)
  
  try {
    if (!switchRoleFormRef.value) {
      logger.error('[角色切换] 表单引用不存在')
      ElMessage.error('表单初始化失败，请刷新页面重试')
      return
    }
    
    logger.log('[角色切换] 开始表单验证')
    await switchRoleFormRef.value.validate()
    logger.log('[角色切换] 表单验证通过')

    let success = false
    try {
      logger.log('[角色切换] 开始调用 authStore.switchRole')
      const targetUser = targetUserList.value.find(u => u.username === switchRoleForm.targetUser)
      const targetUsername = targetUser ? targetUser.username : switchRoleForm.targetUser
      success = await authStore.switchRole(switchRoleForm.targetRole, targetUsername, switchRoleForm.adminPassword)
      logger.log('[角色切换] authStore.switchRole 返回:', success)
    } catch (error) {
      logger.error('[角色切换] authStore.switchRole 抛出异常:', error)
      ElMessage.error('角色切换失败: ' + (error.message || '未知错误'))
    }
    
    if (success) {
      ElMessage.success('角色切换成功')
      switchRoleDialogVisible.value = false
      
      const targetRole = switchRoleForm.targetRole
      const targetRoute = getTargetRoute(targetRole)
      logger.log('[角色切换] 目标路由:', targetRoute)
      
      switchRoleForm.targetRole = ''
      switchRoleForm.targetUser = ''
      switchRoleForm.adminPassword = ''
      targetUserList.value = []
      
      if (targetRoute) {
        await router.push(targetRoute)
      }
    } else {
      logger.error('[角色切换] authStore.switchRole 返回 false')
    }
  } catch (error) {
    logger.error('[角色切换] 捕获异常:', error)
    if (error !== 'cancel') {
      ElMessage.error('角色切换失败: ' + (error.message || '未知错误'))
    }
  }
}

// 获取目标路由
const getTargetRoute = (targetRole) => {
  const routes = {
    'teacher': '/teacher/dashboard',
    'student': '/student/dashboard',
    'company': '/company/dashboard'
  }
  return routes[targetRole] || null
}

// 显示修改密码对话框
const showChangePassword = () => {
  if (passwordFormRef.value) {
    passwordFormRef.value.resetFields()
  }
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
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

// 修改密码
const changePassword = async () => {
  try {
    // 表单验证
    await passwordFormRef.value.validate()

    // 构建请求数据
    const passwordData = {
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    }

    // 调用API修改密码
    const response = await AdminUserService.changeAdminPassword(passwordData)

    // 根据后端Result对象格式检查响应状态
    const result = response.data || response;
    if (result && result.code === 200) {
          ElMessage.success('密码修改成功，请重新登录')
          passwordDialogVisible.value = false
          
          // 先立即清除本地存储的令牌，避免后续请求使用已失效的令牌
          authStore.logout(true);
          // 清除所有可能的登录状态
          localStorage.removeItem('isLoggedIn');
          localStorage.removeItem('token');
          localStorage.removeItem('role');
          localStorage.removeItem('userId');
          localStorage.removeItem('current_role');
          
          // 然后跳转到登录页面
          router.push('/login');
    } else {
      ElMessage.error(result ? (result.message || '密码修改失败') : '密码修改失败')
    }
  } catch (error) {
    logger.error('修改密码失败:', error)
    ElMessage.error('密码修改失败')
  }
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

// 显示编辑资料对话框
const showEditProfile = async () => {
  try {
    // 从auth store中获取当前登录用户的ID
    let currentUserId: number | undefined = authStore.user?.id ? parseInt(String(authStore.user.id)) : undefined

    // 如果store中没有ID，尝试从localStorage获取
    if (!currentUserId) {
      const localStorageId = localStorage.getItem('userId')
      currentUserId = localStorageId ? parseInt(localStorageId) : undefined

    }

    if (!currentUserId || isNaN(currentUserId)) {
      ElMessage.warning('无法获取用户信息，请重新登录')
      return
    }

    logger.log('获取到的用户ID:', currentUserId)
    // 调用API获取当前管理员的完整信息
    const response = await AdminUserService.getAdminById(currentUserId)

    // 根据后端Result对象格式检查响应状态
    if (response && response.data && response.code === 200 && response.data) {
      const adminInfo = response.data

      if (editingUserFormRef.value) {
        editingUserFormRef.value.resetFields()
      }

      // 将管理员信息填充到编辑表单中
      editingUser.id = adminInfo.id
      editingUser.username = adminInfo.username
      editingUser.name = adminInfo.name || ''
      editingUser.phone = adminInfo.phone || ''
    } else if (response && response.data && response.code !== 200) {
      ElMessage.error(response.message || '获取用户信息失败')
    } else {
      ElMessage.error('获取用户信息失败')
    }

    // 显示对话框
    editDialogVisible.value = true
  } catch (error) {
    logger.error('获取管理员信息失败:', error)
    ElMessage.error('获取用户信息失败: ' + (error.message || '未知错误'))
  }
}

const handleEditDialogClose = () => {
  nextTick(() => {
    if (editingUserFormRef.value) {
      editingUserFormRef.value.resetFields()
    }
    editingUser.id = 0
    editingUser.username = ''
    editingUser.name = ''
    editingUser.phone = ''
  })
}

// 保存用户资料
const saveProfile = async () => {
  try {
    // 添加表单验证
    if (!editingUser.username) {
      ElMessage.warning('用户名不能为空')
      return
    }

    // 构建更新数据
    const updateData = {
      id: editingUser.id,
      username: editingUser.username,
      phone: editingUser.phone
    }

    // 调用API更新用户资料
    const response = await AdminUserService.updateAdmin(updateData)

    // 根据后端Result对象格式检查响应状态
    if (response && response.data && response.code === 200) {
      // 始终显示自定义的中文提示信息
      ElMessage.success('资料更新成功')
      editDialogVisible.value = false
    } else {
      ElMessage.error(response?.data?.message || '资料更新失败')
    }
  } catch (error) {
    logger.error('更新用户资料失败:', error)
    ElMessage.error('资料更新失败')
  }
}
</script>

<template>
  <div class="app-container">
    <!-- 顶部栏 -->
    <header class="header">
      <div class="header-left">
        <el-button type="text" @click="toggleSidebar" class="sidebar-toggle-btn">
          <el-icon>
            <component :is="sidebarCollapsed ? Menu : Fold" />
          </el-icon>
        </el-button>
        <div class="logo">
          <span class="logo-text">管理员端</span>
          <div class="logo-subtitle">{{ systemSettingsStore.systemName }}</div>
        </div>
      </div>
      <div class="user-actions">
        <!-- 个人中心 -->
        <el-dropdown @command="handleCommand" class="user-dropdown">
          <el-button type="text" class="user-btn">
            <div class="user-info">
              <el-icon class="user-avatar">
                <User />
              </el-icon>
              <span class="user-name">欢迎您，{{
                authStore.user && authStore.user.username ? authStore.user.username : '管理员'
              }}</span>
              <el-icon class="dropdown-arrow">
                <CaretBottom />
              </el-icon>
            </div>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu class="user-dropdown-menu">
              <el-dropdown-item command="switch" class="dropdown-item">
                <el-icon>
                  <SwitchButton />
                </el-icon>
                <span>角色切换</span>
              </el-dropdown-item>

              <el-dropdown-item command="edit" class="dropdown-item">
                <el-icon>
                  <User />
                </el-icon>
                <span>编辑资料</span>
              </el-dropdown-item>

              <el-dropdown-item command="password" class="dropdown-item">
                <el-icon>
                  <Lock />
                </el-icon>
                <span>修改密码</span>
              </el-dropdown-item>

              <el-dropdown-item command="logout" divided class="dropdown-item logout-item">
                <el-icon>
                  <SwitchButton />
                </el-icon>
                <span>退出登录</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- 主要内容 -->
    <div class="main-content">
      <!-- 左侧菜单 -->
      <aside :class="['sidebar', { 'sidebar-collapsed': sidebarCollapsed }]" v-show="!sidebarCollapsed">
        <el-scrollbar>
          <el-menu :router="true" class="el-menu-vertical-demo" mode="vertical">
            <el-menu-item index="/admin/dashboard">
              <el-icon>
                <HomeFilled />
              </el-icon>
              <span>首页</span>
            </el-menu-item>

            <el-sub-menu index="user-management" v-if="canShowMenu('/admin/teacher-users') || canShowMenu('/admin/student-users') || canShowMenu('/admin/companies')">
              <template #title>
                <el-icon>
                  <User />
                </el-icon>
                <span>用户管理</span>
              </template>
              <el-menu-item index="/admin/teacher-users" v-if="canShowMenu('/admin/teacher-users')">
                <el-icon>
                  <Avatar />
                </el-icon>
                <span>教师管理</span>
              </el-menu-item>
              <el-menu-item index="/admin/student-users" v-if="canShowMenu('/admin/student-users')">
                <el-icon>
                  <UserFilled />
                </el-icon>
                <span>学生管理</span>
              </el-menu-item>
              <el-menu-item index="/admin/companies" v-if="canShowMenu('/admin/companies')">
                <el-icon>
                  <OfficeBuilding />
                </el-icon>
                <span>企业管理</span>
              </el-menu-item>
            </el-sub-menu>

            <el-sub-menu index="basic-data" v-if="canShowMenu('/admin/position-categories') || canShowMenu('/admin/classes') || canShowMenu('/admin/majors') || canShowMenu('/admin/internship-confirm-application') || canShowMenu('/admin/recruitment')">
              <template #title>
                <el-icon>
                  <DataLine />
                </el-icon>
                <span>基础数据管理</span>
              </template>
              <el-menu-item index="/admin/position-categories" v-if="canShowMenu('/admin/position-categories')">
                <el-icon>
                  <Briefcase />
                </el-icon>
                <span>岗位类别管理</span>
              </el-menu-item>
              <el-menu-item index="/admin/classes" v-if="canShowMenu('/admin/classes')">
                <el-icon>
                  <Collection />
                </el-icon>
                <span>班级管理</span>
              </el-menu-item>
              <el-menu-item index="/admin/majors" v-if="canShowMenu('/admin/majors')">
                <el-icon>
                  <School />
                </el-icon>
                <span>院系专业管理</span>
              </el-menu-item>
              <el-menu-item index="/admin/internship-confirm-application" v-if="canShowMenu('/admin/internship-confirm-application')">
                <el-icon>
                  <Document />
                </el-icon>
                <span>实习确认表</span>
              </el-menu-item>
              <el-menu-item index="/admin/recruitment" v-if="canShowMenu('/admin/recruitment')">
                <el-icon>
                  <Briefcase />
                </el-icon>
                <span>招聘管理</span>
              </el-menu-item>
            </el-sub-menu>

            <el-menu-item index="/admin/application-withdrawal-records" v-if="canShowMenu('/admin/application-withdrawal-records')">
              <el-icon>
                <DocumentChecked />
              </el-icon>
              <span>撤回申请记录</span>
            </el-menu-item>
            
            <el-menu-item index="/admin/announcements" v-if="canShowMenu('/admin/announcements')">
              <el-icon>
                <DocumentChecked />
              </el-icon>
              <span>公告管理</span>
            </el-menu-item>

            <el-menu-item index="/admin/resource-documents" v-if="canShowMenu('/admin/resource-documents')">
              <el-icon>
                <Folder />
              </el-icon>
              <span>资源管理</span>
            </el-menu-item>

            <el-menu-item index="/admin/problem-feedback" v-if="canShowMenu('/admin/problem-feedback')">
              <el-icon>
                <ChatLineRound />
              </el-icon>
                <span>反馈管理</span>
            </el-menu-item>

            <el-menu-item index="/admin/data-statistics/active-statistics" v-if="canShowMenu('/admin/data-statistics/active-statistics')">
              <el-icon>
                <DataLine />
              </el-icon>
              <span>数据统计</span>
            </el-menu-item>

            <el-sub-menu index="ai-config" v-if="canShowMenu('/admin/keyword-library') || canShowMenu('/admin/scoring-rule') || canShowMenu('/admin/ai-model') || canShowMenu('/admin/ai-test')">
              <template #title>
                <el-icon>
                  <MagicStick />
                </el-icon>
                <span>AI配置</span>
              </template>
              <el-menu-item index="/admin/keyword-library" v-if="canShowMenu('/admin/keyword-library')">
                <el-icon>
                  <Document />
                </el-icon>
                <span>关键词库管理</span>
              </el-menu-item>
              
              <el-menu-item index="/admin/scoring-rule" v-if="canShowMenu('/admin/scoring-rule')">
                <el-icon>
                  <Document />
                </el-icon>
                <span>评分规则管理</span>
              </el-menu-item>
              <el-menu-item index="/admin/ai-model" v-if="canShowMenu('/admin/ai-model')">
                <el-icon>
                  <ChatDotRound />
                </el-icon>
                <span>AI大模型选择</span>
              </el-menu-item>
              <el-menu-item index="/admin/ai-test" v-if="canShowMenu('/admin/ai-test')">
                <el-icon>
                  <ChatLineRound />
                </el-icon>
                <span>AI测试</span>
              </el-menu-item>
            </el-sub-menu>

            <el-sub-menu index="system-management" v-if="canShowMenu('/admin/logs') || canShowMenu('/admin/permissions') || canShowMenu('/admin/backup') || canShowMenu('/admin/system-settings')">
              <template #title>
                <el-icon>
                  <Setting />
                </el-icon>
                <span>系统管理</span>
              </template>
              <el-menu-item index="/admin/logs" v-if="canShowMenu('/admin/logs')">
                <el-icon>
                  <Document />
                </el-icon>
                <span>日志管理</span>
              </el-menu-item>
              <el-menu-item index="/admin/permissions" v-if="canShowMenu('/admin/permissions')">
                <el-icon>
                  <Lock />
                </el-icon>
                <span>权限管理</span>
              </el-menu-item>
              <el-menu-item index="/admin/backup" v-if="canShowMenu('/admin/backup')">
                <el-icon>
                  <Download />
                </el-icon>
                <span>数据备份与恢复</span>
              </el-menu-item>
              <el-menu-item index="/admin/system-settings" v-if="canShowMenu('/admin/system-settings')">
                <el-icon>
                  <Setting />
                </el-icon>
                <span>系统参数配置</span>
              </el-menu-item>
            </el-sub-menu>

            <el-menu-item index="/admin/profile">
              <el-icon>
                <User />
              </el-icon>
              <span>个人中心</span>
            </el-menu-item>
          </el-menu>
        </el-scrollbar>
      </aside>

      <!-- 右侧内容区域 -->
      <el-scrollbar style="flex: 1;">
        <el-main>
          <router-view></router-view>
        </el-main>
      </el-scrollbar>
    </div>


    <!-- 编辑资料对话框 -->
    <el-dialog v-model="editDialogVisible" title="编辑个人资料" width="500px" @close="handleEditDialogClose">
      <el-form ref="editingUserFormRef" :model="editingUser" :rules="editingUserRules" label-width="100px">
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

      <!-- 修改密码对话框 -->
      <el-dialog v-model="passwordDialogVisible" title="修改密码" width="500px" @close="handlePasswordDialogClose">
        <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="120px">
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
        </el-form>

        <template #footer>
          <span class="dialog-footer">
            <el-button @click="handlePasswordDialogClose(); passwordDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="changePassword">确认修改</el-button>
          </span>
        </template>
      </el-dialog>

      <!-- 角色切换对话框 -->
      <el-dialog v-model="switchRoleDialogVisible" title="角色切换" width="500px" :close-on-click-modal="false" @close="handleSwitchRoleDialogClose">
        <el-form ref="switchRoleFormRef" :model="switchRoleForm" :rules="switchRoleRules" label-width="120px">
          <el-form-item label="目标角色" prop="targetRole">
            <el-select v-model="switchRoleForm.targetRole" placeholder="请选择目标角色" style="width: 100%" @change="handleTargetRoleChange">
              <el-option
                v-for="option in roleOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
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
    <!-- AI悬浮球 (暂时隐藏，功能完善后移除 v-if) -->
    <!-- AIChatFloating组件已移除 -->
  </div>
</template>

<style scoped>
.app-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

/* 顶部栏样式优化 */
.header {
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  color: white;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.2);
  position: relative;
}

.header::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: radial-gradient(circle at 80% 20%, rgba(255, 255, 255, 0.1) 0%, transparent 50%);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.sidebar-toggle-btn {
  color: white !important;
  padding: 8px !important;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.1);
  transition: all 0.3s ease;
}

.sidebar-toggle-btn:hover {
  background: rgba(255, 255, 255, 0.2);
  transform: scale(1.05);
}

.sidebar-toggle-btn .el-icon {
  font-size: 20px;
}

.logo {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: white;
}

.logo-subtitle {
  font-size: 12px;
  opacity: 0.9;
  font-weight: 400;
}

.user-actions {
  display: flex;
  align-items: center;
}

.header-center {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
}

.role-badge {
  font-size: 14px;
  font-weight: 600;
  padding: 8px 16px;
  border-radius: 20px;
  box-shadow: 0 2px 8px rgba(255, 255, 255, 0.2);
}

.user-dropdown {
  margin-right: 8px;
}

.user-btn {
  color: white;
  border: none;
  padding: 8px 12px !important;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.1);
  transition: all 0.3s ease;
}

.user-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar {
  font-size: 18px;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
}

.dropdown-arrow {
  font-size: 12px;
  transition: transform 0.3s ease;
}

.user-dropdown:hover .dropdown-arrow {
  transform: rotate(180deg);
}

.user-dropdown-menu {
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border: none;
  padding: 8px 0;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  font-size: 14px;
  transition: all 0.3s ease;
}

.dropdown-item:hover {
  background: linear-gradient(135deg, #f0f9ff, #e6f7ff);
  color: #409EFF;
}

.logout-item:hover {
  background: #fff2f0;
  color: #ff4d4f;
}

/* 侧边栏样式优化 */
.main-content {
  display: flex;
  flex: 1;
  overflow: hidden;
  margin: 0;
}

.sidebar {
  width: 200px;
  min-width: 200px;
  max-width: 200px;
  height: 100%;
  background: white;
  border-right: 1px solid #e6f7ff;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.04);
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding-top: 12px;
}

.sidebar .el-scrollbar {
  flex: 1;
  height: 0;
  overflow-y: auto;
}

.sidebar-collapsed {
  width: 0;
  opacity: 0;
}

.el-menu-vertical-demo {
  border: none;
  background: transparent;
}

.el-menu-vertical-demo .el-menu-item,
.el-menu-vertical-demo .el-sub-menu__title {
  padding: 0 16px;
  height: 44px;
  line-height: 44px;
  margin: 2px 8px;
  border-radius: 6px;
  transition: all 0.3s ease;
}

.el-menu-vertical-demo .el-menu-item:hover,
.el-menu-vertical-demo .el-sub-menu__title:hover {
  background-color: #f0f9ff;
  color: #409EFF;
}

.el-menu-vertical-demo .el-menu-item.is-active {
  background: linear-gradient(135deg, #409EFF, #66b1ff);
  color: white;
  font-weight: 500;
}

.el-menu-vertical-demo .el-sub-menu .el-menu-item {
  padding-left: 48px;
  margin: 2px 8px;
  height: 40px;
  line-height: 40px;
}

.el-menu-vertical-demo .el-sub-menu .el-menu-item:hover {
  background-color: #f0f9ff;
  color: #409EFF;
}

.el-menu-vertical-demo .el-sub-menu .el-menu-item.is-active {
  background: linear-gradient(135deg, #409EFF, #66b1ff);
  color: white;
}

/* 其他样式保持不变 */
.content {
  flex: 1;
  padding: 20px;
  background-color: #fff;
  margin: 0;
}

.dialog-footer {
  text-align: right;
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

/* 角色切换对话框样式 */
.form-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

/* 响应式设计 */
@media screen and (max-width: 768px) {
  .header {
    padding: 0 16px;
  }

  .logo-text {
    font-size: 18px;
  }

  .logo-subtitle {
    display: none;
  }

  .sidebar {
    position: absolute;
    z-index: 99;
    height: calc(100vh - 64px);
    box-shadow: 4px 0 12px rgba(0, 0, 0, 0.1);
  }

  .sidebar-collapsed {
    transform: translateX(-100%);
  }
}
</style>