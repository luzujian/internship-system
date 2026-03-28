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
          <el-button type="text" @click="toggleSidebar" class="sidebar-toggle-btn">
            <el-icon>
              <component :is="sidebarCollapsed ? Menu : Fold" />
            </el-icon>
          </el-button>
          <div class="logo">
          <span class="logo-text">deepintern</span>
          <div class="logo-subtitle">教师端</div>
        </div>
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

      <el-container style="flex: 1; display: flex; overflow: hidden;">
        <aside :class="['aside', { 'aside-collapsed': sidebarCollapsed }]" v-show="!sidebarCollapsed">
          <el-scrollbar>
            <el-menu
              :default-active="activeMenu"
              router
              class="el-menu-vertical-demo"
            >
              <el-menu-item
                v-for="menu in dynamicMenus"
                :key="menu.path"
                :index="menu.path"
                @click="handleMenuClick(menu.path)"
              >
                <el-icon><component :is="getMenuIcon(menu.icon)" /></el-icon>
                <template #title>{{ menu.name }}</template>
              </el-menu-item>
            </el-menu>
          </el-scrollbar>
        </aside>

        <el-main class="main">
          <el-scrollbar style="height: 100%;">
            <router-view />
          </el-scrollbar>
        </el-main>
      </el-container>
    </el-container>
    <FloatingAIBall role="teacher" />
  </div>
</template>

<script lang="ts" setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ElScrollbar } from 'element-plus'
import {
  Fold,
  Menu,
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
  Back,
  Monitor,
  Setting
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'
import { useSystemSettingsStore } from '@/store/systemSettings'
import { useUserStatusCheck } from '@/composables/useUserStatusCheck'
import request from '@/utils/request'
import FloatingAIBall from '@/components/FloatingAIBall.vue'

defineOptions({
  components: {
    ElScrollbar
  }
})

interface MenuItem {
  id: number
  name: string
  path: string
  icon: string
  sortOrder: number
}

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const systemSettingsStore = useSystemSettingsStore()

const isReadOnlyMode = computed(() => authStore.user?.isReadOnly || false)
const dynamicMenus = ref<MenuItem[]>([])
const menuLoading = ref(false)

const handleMenuClick = (path: string) => {
  console.log('菜单点击:', path)
}

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
      const currentRole = authStore.role
      if (currentRole) {
        const rolePrefix = 'teacher_'
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

const iconMap: Record<string, any> = {
  'HomeFilled': HomeFilled,
  'DataLine': DataLine,
  'Monitor': Monitor,
  'Edit': Edit,
  'Document': Document,
  'Bell': Bell,
  'OfficeBuilding': OfficeBuilding,
  'Upload': Upload,
  'Setting': Setting,
  'Clock': Clock,
  'Key': Key,
  'User': User
}

const getMenuIcon = (iconName: string) => {
  return iconMap[iconName] || Document
}

const fetchTeacherMenus = async () => {
  const role = authStore.role
  if (!role || !role.startsWith('ROLE_TEACHER')) {
    console.log('[TeacherLayout] 非教师角色，跳过获取菜单')
    return
  }

  const teacherType = localStorage.getItem('teacherType') || authStore.user?.teacherType
  if (!teacherType) {
    console.log('[TeacherLayout] 未找到教师类型，使用默认菜单')
    dynamicMenus.value = getDefaultMenus()
    return
  }

  console.log('[TeacherLayout] 教师类型:', teacherType)
  
  menuLoading.value = true
  try {
    const response = await request.get('/teacher/permissions/tree')
    if (response.code === 200) {
      const allMenus = response.data || []
      const permissionsResponse = await request.get(`/teacher/permissions/role-code/${teacherType}/permissions`)
      
      if (permissionsResponse.code === 200) {
        const permissions = permissionsResponse.data || []
        console.log('[TeacherLayout] 用户权限列表:', permissions)
        
        dynamicMenus.value = permissions.map((perm: any) => {
          const menuInfo = allMenus.find((m: any) => m.permissionCode === perm.permissionCode)
          return {
            id: perm.id,
            name: perm.permissionName,
            path: perm.path || (menuInfo?.path || ''),
            icon: perm.icon || menuInfo?.icon,
            sortOrder: perm.sortOrder || 0
          }
        }).sort((a: any, b: any) => a.sortOrder - b.sortOrder)
        
        console.log('[TeacherLayout] 动态菜单:', dynamicMenus.value)
        
        if (dynamicMenus.value.length === 0) {
          console.log('[TeacherLayout] 动态菜单为空，使用默认菜单')
          dynamicMenus.value = getDefaultMenus()
        }
      } else {
        console.log('[TeacherLayout] 获取权限列表失败，使用默认菜单')
        dynamicMenus.value = getDefaultMenus()
      }
    } else {
      console.log('[TeacherLayout] 获取权限树失败，使用默认菜单')
      dynamicMenus.value = getDefaultMenus()
    }
  } catch (error) {
    console.error('[TeacherLayout] 获取菜单失败:', error)
    dynamicMenus.value = getDefaultMenus()
  } finally {
    menuLoading.value = false
  }
}

const getDefaultMenus = (): MenuItem[] => {
  const teacherType = localStorage.getItem('teacherType') || authStore.user?.teacherType
  if (teacherType === 'COLLEGE') {
    return [
      { id: 1, name: '首页', path: '/teacher/home', icon: 'HomeFilled', sortOrder: 1 },
      { id: 2, name: '公告管理', path: '/teacher/announcements', icon: 'Bell', sortOrder: 2 },
      { id: 3, name: '实习状态看板', path: '/teacher/dashboard', icon: 'DataLine', sortOrder: 3 },
      { id: 4, name: '学生状态监控', path: '/teacher/student-tracking', icon: 'Monitor', sortOrder: 4 },
      { id: 5, name: '资源管理', path: '/teacher/resources', icon: 'Upload', sortOrder: 5 },
      { id: 6, name: '统计报表', path: '/teacher/reports', icon: 'DataLine', sortOrder: 6 },
      { id: 7, name: '账号设置', path: '/teacher/account-settings', icon: 'User', sortOrder: 7 },
      { id: 11, name: '企业列表', path: '/teacher/company-list', icon: 'OfficeBuilding', sortOrder: 11 },
      { id: 10, name: '系统设置', path: '/teacher/settings', icon: 'Setting', sortOrder: 10 }
    ]
  } else if (teacherType === 'DEPARTMENT') {
    return [
      { id: 1, name: '首页', path: '/teacher/home', icon: 'HomeFilled', sortOrder: 1 },
      { id: 2, name: '公告管理', path: '/teacher/announcements', icon: 'Bell', sortOrder: 2 },
      { id: 3, name: '实习状态看板', path: '/teacher/dashboard', icon: 'DataLine', sortOrder: 3 },
      { id: 4, name: '学生状态监控', path: '/teacher/student-tracking', icon: 'Monitor', sortOrder: 4 },
      { id: 8, name: '审核管理', path: '/teacher/approval', icon: 'Edit', sortOrder: 8 },
      { id: 5, name: '资源管理', path: '/teacher/resources', icon: 'Upload', sortOrder: 5 },
      { id: 6, name: '统计报表', path: '/teacher/reports', icon: 'DataLine', sortOrder: 6 },
      { id: 7, name: '账号设置', path: '/teacher/account-settings', icon: 'User', sortOrder: 7 },
      { id: 11, name: '企业列表', path: '/teacher/company-list', icon: 'OfficeBuilding', sortOrder: 11 }
    ]
  } else if (teacherType === 'COUNSELOR') {
    return [
      { id: 1, name: '首页', path: '/teacher/home', icon: 'HomeFilled', sortOrder: 1 },
      { id: 2, name: '公告管理', path: '/teacher/announcements', icon: 'Bell', sortOrder: 2 },
      { id: 3, name: '实习状态看板', path: '/teacher/dashboard', icon: 'DataLine', sortOrder: 3 },
      { id: 4, name: '学生状态监控', path: '/teacher/student-tracking', icon: 'Monitor', sortOrder: 4 },
      { id: 12, name: '班级管理', path: '/teacher/classes', icon: 'School', sortOrder: 5 },
      { id: 13, name: '学生管理', path: '/teacher/students', icon: 'User', sortOrder: 6 },
      { id: 9, name: '智慧评分', path: '/teacher/evaluation', icon: 'Document', sortOrder: 7 },
      { id: 14, name: 'AI评分配置', path: '/teacher/ai-scoring-config', icon: 'Key', sortOrder: 8 },
      { id: 5, name: '资源管理', path: '/teacher/resources', icon: 'Upload', sortOrder: 9 },
      { id: 6, name: '统计报表', path: '/teacher/reports', icon: 'DataLine', sortOrder: 10 },
      { id: 7, name: '账号设置', path: '/teacher/account-settings', icon: 'Setting', sortOrder: 11 },
      { id: 11, name: '企业列表', path: '/teacher/company-list', icon: 'OfficeBuilding', sortOrder: 12 }
    ]
  }
  return []
}

onMounted(() => {
  console.log('[TeacherLayout] 组件已挂载')
  if (!systemSettingsStore.systemName) {
    systemSettingsStore.loadSettings()
  }
  fetchTeacherMenus()
})

watch(() => authStore.role, (newRole) => {
  if (newRole && newRole.startsWith('ROLE_TEACHER')) {
    fetchTeacherMenus()
  }
})

watch(() => authStore.user?.teacherType, (newType) => {
  if (newType) {
    console.log('[TeacherLayout] 教师类型变化:', newType)
    fetchTeacherMenus()
  }
})

const sidebarCollapsed = ref(false)

const activeMenu = computed(() => route.path)

useUserStatusCheck(180000)

const getTeacherName = () => {
  return authStore.user?.name || '教师用户'
}

const getTeacherTypeText = () => {
  const teacherType = localStorage.getItem('teacherType') || authStore.user?.teacherType
  if (teacherType === 'COLLEGE') return '学院教师'
  if (teacherType === 'DEPARTMENT') return '系室教师'
  if (teacherType === 'COUNSELOR') return '辅导员'
  
  const role = authStore.role
  if (role) {
    if (role.includes('COLLEGE')) {
      return '学院教师'
    } else if (role.includes('DEPARTMENT')) {
      return '系室教师'
    } else if (role.includes('COUNSELOR')) {
      return '辅导员'
    }
  }
  
  return '教师'
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
  width: 100vw;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.teacher-layout > .el-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.teacher-layout > .el-container > .el-container {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  color: white;
  padding: 0 24px;
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
  font-size: 24px;
  font-weight: bold;
  letter-spacing: 1px;
}

.logo-subtitle {
  font-size: 12px;
  opacity: 0.9;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.teacher-type-badge {
  padding: 6px 16px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  box-shadow: 0 2px 8px rgba(255, 255, 255, 0.2);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 8px 16px;
  border-radius: 20px;
  transition: all 0.3s ease;
}

.user-info span {
  color: white;
}

.user-info .el-icon {
  color: white;
}

.user-info:hover {
  background: rgba(255, 255, 255, 0.15);
}

.aside {
  width: 200px;
  min-width: 200px;
  max-width: 200px;
  background: white;
  border-right: 1px solid #e6f7ff;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.04);
  transition: all 0.3s ease;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding-top: 12px;
}

.aside-collapsed {
  width: 0;
  opacity: 0;
}

.aside .el-scrollbar {
  flex: 1;
  height: 0;
  overflow-y: auto;
}

.el-menu-vertical-demo {
  border: none;
  background: transparent;
  /* 优化：启用 GPU 加速 */
  transform: translateZ(0);
  will-change: auto;
}

.el-menu-vertical-demo .el-menu-item {
  /* 优化：提升点击响应速度 */
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
}

.el-menu-vertical-demo .el-menu-item:hover {
  background-color: #f0f9ff;
  color: #409EFF;
  /* 优化：hover 效果更流畅 */
  transform: translateX(2px);
}

.el-menu-vertical-demo .el-menu-item.is-active {
  background: linear-gradient(135deg, #409EFF, #66b1ff);
  color: white;
  font-weight: 500;
  /* 优化：激活状态更明显 */
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.3);
}

.el-menu-vertical-demo .el-menu-item {
  padding: 0 16px;
  height: 44px;
  line-height: 44px;
  margin: 2px 8px;
  border-radius: 6px;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 12px;
}

.el-menu-vertical-demo .el-menu-item:hover {
  background-color: #f0f9ff;
  color: #409EFF;
}

.el-menu-vertical-demo .el-menu-item.is-active {
  background: linear-gradient(135deg, #409EFF, #66b1ff);
  color: white;
  font-weight: 500;
}

.main {
  background: #f5f7fa;
  padding: 24px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.main :deep(.el-scrollbar) {
  flex: 1;
  min-height: 0;
}

.main :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
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
