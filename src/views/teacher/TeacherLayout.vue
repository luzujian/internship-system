<template>
  <div class="teacher-layout">
    <div v-if="isReadOnlyMode" class="read-only-banner">
      <el-alert type="warning" :closable="false" show-icon>
        <template #title>
          <span class="banner-title">
            <el-icon><Warning /></el-icon>
            只读模式
          </span>
        </template>
        <template #default>
          <div class="banner-content">
            <span>您正在以管理员身份查看教师端，只能查看数据，不能进行任何修改操作。</span>
            <el-button type="primary" size="small" @click="returnToAdmin" class="return-btn">
              <el-icon><Back /></el-icon>
              返回管理员端
            </el-button>
          </div>
        </template>
      </el-alert>
    </div>
    
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-icon" @click="toggleSidebar">
            <Fold v-if="!sidebarCollapsed" />
            <Expand v-else />
          </el-icon>
          <div class="logo">{{ systemSettingsStore.systemName }}</div>
        </div>
        <div class="header-right">
          <div class="teacher-type-badge">{{ getTeacherTypeText() }}</div>
          <el-dropdown @command="handleCommand">
            <div class="user-info">
              <el-icon><User /></el-icon>
              <span>{{ getTeacherName() }}</span>
              <el-icon><CaretBottom /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">编辑资料</el-dropdown-item>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-container>
        <el-aside :width="sidebarCollapsed ? '64px' : '200px'" class="aside">
          <el-menu
            :default-active="activeMenu"
            :collapse="sidebarCollapsed"
            :collapse-transition="false"
            router
          >
            <el-menu-item
              v-for="menu in filteredMenus"
              :key="menu.index"
              :index="menu.index"
            >
              <el-icon><component :is="menu.icon" /></el-icon>
              <template #title>{{ menu.title }}</template>
            </el-menu-item>
          </el-menu>
        </el-aside>

        <el-scrollbar style="flex: 1;">
          <el-main class="main">
            <router-view />
          </el-main>
        </el-scrollbar>
      </el-container>
    </el-container>
  </div>
</template>

<script lang="ts" setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ElScrollbar } from 'element-plus'
import {
  Fold,
  Expand,
  User,
  CaretBottom,
  HomeFilled,
  School,
  Document,
  Bell,
  DataLine,
  Upload,
  Clock,
  OfficeBuilding,
  Edit,
  Key,
  Warning,
  Back
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'
import { useSystemSettingsStore } from '@/store/systemSettings'
import { useUserStatusCheck } from '@/composables/useUserStatusCheck'

defineOptions({
  components: {
    ElScrollbar
  }
})

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const systemSettingsStore = useSystemSettingsStore()

const isReadOnlyMode = computed(() => authStore.user?.isReadOnly || false)

const returnToAdmin = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要返回管理员端吗？',
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const originalAdminUsername = authStore.user?.originalAdminUsername
    if (originalAdminUsername) {
      // 清除当前教师角色的token，避免残留
      const currentRole = authStore.role
      if (currentRole) {
        const rolePrefix = currentRole === 'ROLE_TEACHER' ? 'teacher_' :
                          currentRole === 'ROLE_TEACHER_COLLEGE' ? 'teacher_' :
                          currentRole === 'ROLE_TEACHER_DEPARTMENT' ? 'teacher_' :
                          currentRole === 'ROLE_TEACHER_COUNSELOR' ? 'teacher_' : ''

        localStorage.removeItem(rolePrefix + 'accessToken_' + currentRole)
        localStorage.removeItem(rolePrefix + 'refreshToken_' + currentRole)
        localStorage.removeItem(rolePrefix + 'token_' + currentRole)
        localStorage.removeItem(rolePrefix + 'role_' + currentRole)
        localStorage.removeItem(rolePrefix + 'userId_' + currentRole)
        localStorage.removeItem(rolePrefix + 'username_' + currentRole)
        localStorage.removeItem(rolePrefix + 'isReadOnly_' + currentRole)
        localStorage.removeItem(rolePrefix + 'originalAdminUsername_' + currentRole)
        console.log('[returnToAdmin] 已清除教师角色token:', currentRole)
      }

      localStorage.setItem('current_role', 'ROLE_ADMIN')
      localStorage.setItem('admin_accessToken_ROLE_ADMIN', localStorage.getItem('admin_accessToken_ROLE_ADMIN') || '')
      localStorage.setItem('admin_refreshToken_ROLE_ADMIN', localStorage.getItem('admin_refreshToken_ROLE_ADMIN') || '')
      localStorage.setItem('admin_role_ROLE_ADMIN', 'ROLE_ADMIN')
      localStorage.setItem('admin_userId_ROLE_ADMIN', localStorage.getItem('admin_userId_ROLE_ADMIN') || '')
      localStorage.setItem('admin_username_ROLE_ADMIN', originalAdminUsername)

      authStore.role = 'ROLE_ADMIN'
      authStore.user = {
        id: localStorage.getItem('admin_userId_ROLE_ADMIN'),
        username: originalAdminUsername,
        name: originalAdminUsername
      }
      authStore.token = localStorage.getItem('admin_accessToken_ROLE_ADMIN')
      authStore.isAuthenticated = true
      authStore.user.isReadOnly = false
      authStore.user.originalAdminUsername = null

      await router.push('/admin/dashboard')
    } else {
      ElMessage.error('无法返回管理员端，请重新登录')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('返回管理员端失败:', error)
      ElMessage.error('返回管理员端失败')
    }
  }
}

onMounted(() => {
  console.log('[TeacherLayout] 组件已挂载')
  if (!systemSettingsStore.systemName) {
    systemSettingsStore.loadSettings()
  }
})

const sidebarCollapsed = ref(false)

const activeMenu = computed(() => route.path)

useUserStatusCheck(180000)

const menuConfig = [
  {
    index: '/teacher/dashboard',
    title: '首页',
    icon: HomeFilled,
    permission: null,
    roles: ['ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR']
  },
  {
    index: '/teacher/announcements',
    title: '通知管理',
    icon: Bell,
    permission: null,
    roles: ['ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR']
  },
  {
    index: '/teacher/internship-status',
    title: '实习状态',
    icon: Document,
    permission: 'INTERNSHIP_STATUS_VIEW',
    roles: ['ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR']
  },
  {
    index: '/teacher/students',
    title: '学生筛选导出',
    icon: School,
    permission: 'STUDENT_FILTER_EXPORT',
    roles: ['ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR']
  },
  {
    index: '/teacher/resources',
    title: '资料上传',
    icon: Upload,
    permission: 'RESOURCE_UPLOAD',
    roles: ['ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR']
  },
  {
    index: '/teacher/reports',
    title: '运营报表',
    icon: DataLine,
    permission: 'REPORT_VIEW',
    roles: ['ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR']
  },
  {
    index: '/teacher/timeline-config',
    title: '时间节点设置',
    icon: Clock,
    permission: 'TIMELINE_CONFIG',
    roles: ['ROLE_TEACHER_COLLEGE']
  },
  {
    index: '/teacher/company-audit',
    title: '企业审核',
    icon: OfficeBuilding,
    permission: 'user:company:audit',
    roles: ['ROLE_TEACHER_DEPARTMENT']
  },
  {
    index: '/teacher/student-application-audit',
    title: '学生申请审核',
    icon: Edit,
    permission: 'STUDENT_APPLICATION_AUDIT',
    roles: ['ROLE_TEACHER_DEPARTMENT']
  },
  {
    index: '/teacher/key-timeline-config',
    title: '关键时间节点设置',
    icon: Clock,
    permission: 'KEY_TIMELINE_CONFIG',
    roles: ['ROLE_TEACHER_COUNSELOR']
  },
  {
    index: '/teacher/ai-keyword-config',
    title: 'AI关键词配置',
    icon: Key,
    permission: 'AI_KEYWORD_CONFIG',
    roles: ['ROLE_TEACHER_COUNSELOR']
  },
  {
    index: '/teacher/ai-scoring-config',
    title: 'AI评分规则配置',
    icon: Key,
    permission: 'AI_SCORING_CONFIG',
    roles: ['ROLE_TEACHER_COUNSELOR']
  },
  {
    index: '/teacher/internship-reports',
    title: '实习心得',
    icon: Document,
    permission: 'INTERNSHIP_REPORT_VIEW',
    roles: ['ROLE_TEACHER_COUNSELOR']
  },
  {
    index: '/teacher/grade-finalize',
    title: '成绩评定',
    icon: Edit,
    permission: 'INTERNSHIP_GRADE_FINALIZE',
    roles: ['ROLE_TEACHER_COUNSELOR']
  },
  {
    index: '/teacher/profile',
    title: '个人中心',
    icon: User,
    permission: null,
    roles: ['ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR']
  }
]

const filteredMenus = computed(() => {
  const userRole = authStore.role
  const userPermissions = JSON.parse(localStorage.getItem('teacherPermissions') || '[]')
  
  console.log('[TeacherLayout] 菜单过滤调试信息:')
  console.log('[TeacherLayout] 用户角色:', userRole)
  console.log('[TeacherLayout] 用户权限:', userPermissions)
  console.log('[TeacherLayout] 菜单配置:', menuConfig)
  
  return menuConfig.filter(menu => {
    const roleMatch = menu.roles.includes(userRole)
    if (!roleMatch) {
      console.log('[TeacherLayout] 菜单过滤 - 角色不匹配:', menu.title, '需要角色:', menu.roles, '用户角色:', userRole)
      return false
    }
    
    if (menu.permission) {
      const permissionMatch = userPermissions.includes(menu.permission)
      if (!permissionMatch) {
        console.log('[TeacherLayout] 菜单过滤 - 权限不匹配:', menu.title, '需要权限:', menu.permission, '用户权限:', userPermissions)
        return false
      }
    }
    
    console.log('[TeacherLayout] 菜单过滤 - 通过:', menu.title)
    return true
  })
})

const getTeacherName = () => {
  return authStore.user?.name || '教师用户'
}

const getTeacherTypeText = () => {
  const teacherType = localStorage.getItem('teacherType')
  const typeMap = {
    'COLLEGE': '学院教师',
    'DEPARTMENT': '系室教师',
    'COUNSELOR': '辅导员'
  }
  return typeMap[teacherType] || '教师'
}

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

const handleCommand = async (command) => {
  switch (command) {
    case 'edit':
      router.push('/teacher/profile')
      break
    case 'password':
      router.push('/teacher/profile')
      break
    case 'logout':
      await logout()
      break
  }
}

const logout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await authStore.logout()
    ElMessage.success('退出登录成功')
    router.push('/login')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('退出登录失败:', error)
      ElMessage.error('退出登录失败')
    }
  }
}
</script>

<style scoped>
.teacher-layout {
  height: 100vh;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #E6A23C;
  color: white;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
}

.collapse-icon {
  font-size: 20px;
  cursor: pointer;
}

.logo {
  font-size: 18px;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 15px;
}

.teacher-type-badge {
  padding: 4px 12px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 4px;
  transition: background 0.3s;
}

.user-info:hover {
  background: rgba(255, 255, 255, 0.1);
}

.aside {
  background: #304156;
  color: white;
  transition: width 0.3s;
}

.aside:not(.el-menu--collapse) {
  width: 200px;
}

.el-menu {
  border-right: none;
}

.main {
  background: #f0f2f5;
  padding: 20px;
}

.read-only-banner {
  margin: 0;
  border-radius: 0;
}

.banner-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}

.banner-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}

.return-btn {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
