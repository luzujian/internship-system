<script setup lang="ts">
import { ref, computed, provide, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  Bell,
  CaretBottom,
  DocumentChecked,
  Fold,
  HomeFilled,
  Lock,
  Menu,
  SwitchButton,
  User,
  Briefcase,
  Document,
  ChatDotRound,
  Warning,
  Back
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import {
  initAnnouncementWebSocket,
  disconnectAnnouncementWebSocket
} from '@/utils/websocket'
import emitter from '@/utils/event-bus'
import FloatingAIBall from '@/components/FloatingAIBall.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// 只读模式
const isReadOnlyMode = computed(() => authStore.user?.isReadOnly || false)

// 返回管理员端
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
        const rolePrefix = 'student_'
        localStorage.removeItem(rolePrefix + 'accessToken_' + currentRole)
        localStorage.removeItem(rolePrefix + 'refreshToken_' + currentRole)
        localStorage.removeItem(rolePrefix + 'token_' + currentRole)
        localStorage.removeItem(rolePrefix + 'role_' + currentRole)
        localStorage.removeItem(rolePrefix + 'userId_' + currentRole)
        localStorage.removeItem(rolePrefix + 'username_' + currentRole)
        localStorage.removeItem(rolePrefix + 'isReadOnly_' + currentRole)
        localStorage.removeItem(rolePrefix + 'originalAdminUsername_' + currentRole)
        console.log('[returnToAdmin] 已清除学生角色token:', currentRole)
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

// 退出登录
const logout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    console.log('[logout] isReadOnlyMode:', isReadOnlyMode.value)
    console.log('[logout] originalAdminUsername:', authStore.user?.originalAdminUsername)

    // 如果是只读模式（管理员切换过来的），点击退出应该返回管理员端
    if (isReadOnlyMode.value && authStore.user?.originalAdminUsername) {
      await returnToAdmin()
    } else {
      await authStore.logout()
      ElMessage.success('退出登录成功')
      router.push('/login')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('退出登录失败:', error)
      ElMessage.error('退出登录失败')
    }
  }
}

const sidebarCollapsed = ref(false)
const isDialogOpen = ref(false)
const isMobile = ref(false)
const showMobileSidebar = ref(false)

// 获取用户名
const userName = computed(() => {
  return authStore.user?.name || authStore.user?.username || '同学'
})

// 获取学生ID
const studentId = computed(() => {
  return authStore.user?.studentId || authStore.user?.id || ''
})

// 提醒相关
const hasUnconfirmedReminder = ref(false)
const currentReminder = ref<{ id: number; content: string } | null>(null)

const checkPendingReminders = async () => {
  if (!studentId.value) return
  try {
    const response = await request.get('/student/reminder/pending', {
      params: { studentId: studentId.value }
    })
    if (response.data && response.data.length > 0) {
      currentReminder.value = response.data[0]
      hasUnconfirmedReminder.value = true
    }
  } catch (error) {
    console.error('检查提醒失败:', error)
  }
}

const confirmReminder = async () => {
  if (!currentReminder.value) return
  try {
    await request.post(`/student/reminder/confirm/${currentReminder.value.id}`)
    hasUnconfirmedReminder.value = false
    currentReminder.value = null
    ElMessage.success('已确认提醒')
    // 确认后重新检查是否还有未确认的提醒
    checkPendingReminders()
  } catch (error) {
    console.error('确认失败:', error)
    ElMessage.error('确认失败')
  }
}

// 提供对话框状态给子组件
provide('isDialogOpen', isDialogOpen)

// 计算当前激活的菜单
const activeMenu = computed(() => {
  const path = route.path
  // 确保路径精确匹配
  if (path.startsWith('/student/')) {
    return path
  }
  return path
})

// 检测是否为移动设备
const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
  if (isMobile.value) {
    sidebarCollapsed.value = true
  }
}

// 切换侧边栏
const toggleSidebar = () => {
  if (isMobile.value) {
    showMobileSidebar.value = !showMobileSidebar.value
  } else {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }
}

// 处理菜单选择
const handleMenuSelect = (index) => {
  router.push(index)
}

// 处理菜单命令
const handleCommand = async (command) => {
  switch (command) {
    case 'switch':
      break
    case 'edit':
      router.push('/student/profile')
      break
    case 'password':
      break
    case 'clear':
      break
    case 'logout':
      await logout()
      break
  }
}

// 生命周期钩子
onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  checkPendingReminders()

  // 初始化 WebSocket 连接
  initStudentWebSocket()
})

// 初始化学生端 WebSocket
const initStudentWebSocket = () => {
  let token = authStore.token

  if (!token) {
    const rolePrefix = 'student_'
    const role = 'ROLE_STUDENT'
    token = localStorage.getItem(`${rolePrefix}accessToken_${role}`) ||
            localStorage.getItem(`${rolePrefix}token_${role}`) ||
            localStorage.getItem('accessToken')
  }

  if (token) {
    initAnnouncementWebSocket(token, handleStudentWebSocketMessage)
  }
}

// 处理学生端 WebSocket 消息
const handleStudentWebSocketMessage = (data) => {
  console.log('[StudentLayout] 收到 WebSocket 消息:', data)

  if (data.type === 'application_status_update' && data.data) {
    const { companyName, positionName, status, statusText } = data.data
    ElMessage({
      type: status === 'approved' ? 'success' : 'warning',
      message: `您申请的 ${positionName} 岗位面试结果：${statusText}`,
      duration: 5000,
      showClose: true
    })
    // 触发刷新事件
    emitter.emit('application-status-update', data.data)
  }
}

// 监听路由变化，确保菜单选中状态正确
watch(() => route.path, (newPath) => {
  // 路由变化时，菜单会自动更新，因为activeMenu是computed属性
  console.log('路由变化:', newPath)
})

// 监听 authStore.user 变化，确保只读模式状态更新
watch(() => authStore.user?.isReadOnly, (newVal, oldVal) => {
  console.log('[StudentLayout] isReadOnly 变化:', oldVal, '->', newVal)
  console.log('[StudentLayout] authStore.user:', authStore.user)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
  disconnectAnnouncementWebSocket()
})
</script>

<template>
  <div class="app-container">
    <!-- 只读模式横幅 -->
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
            <span>您正在以管理员身份查看学生端，只能查看数据，不能进行任何修改操作。</span>
            <el-button type="primary" size="small" @click="returnToAdmin" class="return-btn">
              <el-icon><Back /></el-icon>
              返回管理员端
            </el-button>
          </div>
        </template>
      </el-alert>
    </div>

    <!-- 全局提醒弹窗 -->
    <el-dialog
      v-model="hasUnconfirmedReminder"
      title="系统提醒"
      width="500px"
      :close-on-click-modal="false"
      :show-close="false"
      class="reminder-dialog"
    >
      <div class="reminder-content">
        <el-icon class="reminder-icon"><Bell /></el-icon>
        <div class="reminder-text">{{ currentReminder?.content }}</div>
      </div>
      <template #footer>
        <el-button type="primary" @click="confirmReminder">确认</el-button>
      </template>
    </el-dialog>

    <!-- AI 助手悬浮球 -->
    <FloatingAIBall role="student" />

    <header :class="['header', { 'has-read-only': isReadOnlyMode }]">
      <div class="header-left">
        <el-button link @click="toggleSidebar" class="sidebar-toggle-btn">
          <el-icon>
            <template v-if="sidebarCollapsed">
              <Menu />
            </template>
            <template v-else>
              <Fold />
            </template>
          </el-icon>
        </el-button>
        <div class="logo">
          <span class="logo-text">deepintern</span>
          <div class="logo-subtitle">学生端</div>
        </div>
      </div>
      <div class="user-actions">
        <el-dropdown @command="handleCommand" class="user-dropdown">
          <el-button link class="user-btn">
            <div class="user-info">
              <el-icon class="user-avatar">
                <User />
              </el-icon>
              <span class="user-name">欢迎您，{{ userName }}</span>
              <el-icon class="dropdown-arrow">
                <CaretBottom />
              </el-icon>
            </div>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu class="user-dropdown-menu">
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

    <!-- 侧边栏 -->
    <aside
      :class="['sidebar', {
        'sidebar-collapsed': sidebarCollapsed,
        'sidebar-disabled': isDialogOpen,
        'sidebar-mobile': isMobile && showMobileSidebar,
        'has-read-only': isReadOnlyMode
      }]"
    >
      <div class="sidebar-header">
        <div class="sidebar-title" v-show="!sidebarCollapsed">系统菜单</div>
      </div>
      <el-menu 
        :default-active="activeMenu" 
        @select="handleMenuSelect"
        :unique-opened="true"
        class="el-menu-vertical-demo" 
        mode="vertical"
        :collapse="sidebarCollapsed"
        :collapse-transition="true"
      >
        <el-menu-item index="/student/home">
          <el-icon>
            <HomeFilled />
          </el-icon>
          <template #title>
            <span>首页</span>
          </template>
        </el-menu-item>

        <el-menu-item index="/student/jobs">
          <el-icon>
            <Briefcase />
          </el-icon>
          <template #title>
            <span>职位浏览</span>
          </template>
        </el-menu-item>

        <el-menu-item index="/student/applications">
          <el-icon>
            <Document />
          </el-icon>
          <template #title>
            <span>我的申请</span>
          </template>
        </el-menu-item>

        <el-menu-item index="/student/interviews">
          <el-icon>
            <ChatDotRound />
          </el-icon>
          <template #title>
            <span>面试管理</span>
          </template>
        </el-menu-item>

        <el-menu-item index="/student/internship-confirmation-form">
          <el-icon>
            <DocumentChecked />
          </el-icon>
          <template #title>
            <span>实习确认表</span>
          </template>
        </el-menu-item>

        <el-menu-item index="/student/internships">
          <el-icon>
            <Document />
          </el-icon>
          <template #title>
            <span>实习心得</span>
          </template>
        </el-menu-item>

        <el-menu-item index="/student/profile">
          <el-icon>
            <User />
          </el-icon>
          <template #title>
            <span>个人中心</span>
          </template>
        </el-menu-item>
      </el-menu>
    </aside>

    <!-- 主体区域 -->
    <div :class="['main-wrapper', { 'has-read-only': isReadOnlyMode }]">
      <el-scrollbar style="flex: 1;">
        <el-main :class="{ 'main-content-disabled': isDialogOpen, 'sidebar-collapsed-main': sidebarCollapsed }">
          <router-view></router-view>
        </el-main>
      </el-scrollbar>
    </div>
  </div>
</template>

<style scoped>
/* 全局重置和基础样式 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  width: 100%;
  height: 100%;
  font-family: var(--font-family-base);
  font-size: 14px;
  line-height: 1.5;
  color: #333;
  background-color: #f5f7fa;
}

.app-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}

/* 头部样式 */
.header {
  background: linear-gradient(135deg, #67C23A 0%, #409EFF 100%);
  color: white;
  padding: 0 2vw;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  box-shadow: 0 2px 12px rgba(103, 194, 58, 0.2);
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  flex-shrink: 0;
  backdrop-filter: blur(10px);
}

.header::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: radial-gradient(circle at 80% 20%, rgba(255, 255, 255, 0.1) 0%, transparent 50%);
  pointer-events: none;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
}

.sidebar-toggle-btn {
  color: white;
  font-size: 20px;
  padding: 8px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.sidebar-toggle-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}

.logo {
  display: flex;
  flex-direction: column;
  gap: 0;
  line-height: 1.2;
}

.logo-text {
  font-size: clamp(18px, 2.5vw, 24px);
  font-weight: bold;
  letter-spacing: 1px;
}

.logo-subtitle {
  font-size: clamp(10px, 1.2vw, 12px);
  opacity: 0.9;
}

.user-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.user-dropdown {
  cursor: pointer;
}

.user-btn {
  color: white;
  padding: 8px 16px;
  border-radius: 20px;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-btn:hover {
  background: rgba(255, 255, 255, 0.15);
  transform: translateY(-1px);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar {
  font-size: 20px;
}

.user-name {
  font-size: clamp(12px, 1.5vw, 14px);
  font-weight: 500;
  white-space: nowrap;
}

.dropdown-arrow {
  font-size: 12px;
  transition: transform 0.2s ease;
}

.user-dropdown:hover .dropdown-arrow {
  transform: rotate(180deg);
}

.user-dropdown-menu {
  min-width: 180px;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  border: none;
  overflow: hidden;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  transition: all 0.2s ease;
  font-size: 14px;
}

.dropdown-item:hover {
  background: #f0f7ff;
  color: #409EFF;
}

.logout-item {
  color: #f56c6c;
}

.logout-item:hover {
  background: #fef0f0;
  color: #f56c6c;
}

/* 侧边栏样式 - 使用 transform 实现流畅动画 */
.sidebar {
  width: clamp(200px, 20vw, 280px);
  min-width: clamp(200px, 20vw, 280px);
  max-width: clamp(200px, 20vw, 280px);
  background: white;
  border-right: 1px solid #e8e8e8;
  overflow: hidden;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  flex-shrink: 0;
  position: fixed;
  top: 64px;
  left: 0;
  bottom: 0;
  z-index: 100;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  transform: translateX(0);
}

.sidebar-collapsed {
  transform: translateX(-100%);
}

.sidebar-mobile {
  z-index: 999;
  box-shadow: 4px 0 12px rgba(0, 0, 0, 0.1);
}

.sidebar-disabled {
  opacity: 0.6;
  pointer-events: none;
  filter: grayscale(0.5);
  transition: all 0.3s ease;
}

/* 主体区域包装器 */
.main-wrapper {
  display: flex;
  flex: 1;
  overflow: hidden;
  margin: 0;
  height: 100%;
  padding-top: 64px;
  box-sizing: border-box;
}

.main-content-disabled {
  /* 移除视觉效果，保持交互正常 */
  transition: all 0.3s ease;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid #e8e8e8;
  background: white;
  position: relative;
  z-index: 10;
}

.sidebar-title {
  font-size: clamp(14px, 1.5vw, 16px);
  font-weight: 600;
  color: #333;
}

.el-menu-vertical-demo {
  border-right: none;
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  background: white;
}

.el-menu-item {
  display: flex !important;
  align-items: center !important;
  justify-content: flex-start !important;
  gap: 12px !important;
  padding: 14px 24px !important;
  margin: 4px 16px !important;
  border-radius: 8px;
  transition: all 0.2s ease;
  white-space: nowrap;
  line-height: 1;
  height: auto;
  min-height: 48px;
  font-size: 14px;
}

.el-menu-item .el-icon {
  font-size: 18px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
}

.el-menu-item:hover {
  background: #f0f7ff;
  color: #409EFF;
  transform: translateX(4px);
}

.el-menu-item.is-active {
  background: #67C23A;
  color: white;
  transform: translateX(0);
  box-shadow: 0 2px 8px rgba(103, 194, 58, 0.3);
}

/* 主内容区域样式 */
.el-main {
  flex: 1;
  padding: 20px 24px !important;
  overflow-x: hidden;
  background: #f1f5f9;
  display: flex;
  flex-direction: column;
  margin-left: clamp(200px, 20vw, 280px);
  min-height: 100%;
  position: relative;
  z-index: 1;
}

/* 侧边栏折叠时的主内容区域样式 */
.el-main.sidebar-collapsed-main {
  margin-left: 0;
}

/* 响应式设计 */
@media screen and (max-width: 1024px) {
  .header {
    padding: 0 16px;
  }

  .sidebar {
    width: 220px;
    min-width: 220px;
    max-width: 220px;
  }

  .el-main {
    margin-left: 220px;
    padding: 20px;
  }

  .el-main.sidebar-collapsed-main {
    margin-left: 0;
  }
}

@media screen and (max-width: 768px) {
  .header {
    padding: 0 12px;
  }

  .logo-text {
    font-size: 16px;
  }

  .logo-subtitle {
    font-size: 10px;
  }

  .user-name {
    font-size: 12px;
  }

  .sidebar {
    position: absolute;
    z-index: 9999;
    height: calc(100vh - 64px);
  }

  .sidebar-mobile {
    transform: translateX(0);
  }

  .main-wrapper {
    position: relative;
  }

  .el-main {
    margin-left: 0 !important;
    padding: 16px !important;
  }

  .el-main.sidebar-collapsed-main {
    margin-left: 0 !important;
  }
}

@media screen and (max-width: 480px) {
  .header {
    padding: 0 8px;
  }

  .logo-text {
    font-size: 14px;
  }

  .el-menu-item {
    padding: 12px 16px !important;
    margin: 4px 8px !important;
  }
}

/* 提醒弹窗样式 */
.reminder-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #E6A23C 0%, #F56C6C 100%);
  color: white;
}

/* 只读模式横幅样式 */
.read-only-banner {
  margin: 0;
  border-radius: 0;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1001;
}

/* 只读模式时 header 往下偏移 */
.header.has-read-only {
  top: 40px;
}

/* 侧边栏也跟着偏移 */
.sidebar.has-read-only {
  top: 104px;
}

.main-wrapper.has-read-only {
  padding-top: 104px;
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

.reminder-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
  gap: 16px;
}

.reminder-icon {
  font-size: 48px;
  color: #E6A23C;
}

.reminder-text {
  font-size: 16px;
  line-height: 1.6;
  color: #303133;
  text-align: center;
}
</style>

<style>
/* 强制统一所有页面的根容器留白 */
.home-container, .applications-container, .jobs-container, .internships-container {
  width: 100% !important;
  height: 100% !important;
  background: transparent !important;
  padding: 0 !important;
  margin: 0 !important;
  box-sizing: border-box !important;
}

/* 统一所有页面内模块的间距（和其他页面保持一致） */
.module-container {
  margin-bottom: 24px !important;
  padding: 24px !important;
  border-radius: 8px !important;
  background: #fff !important;
  box-shadow: 0 2px 8px rgba(0,0,0,0.03) !important;
  max-height: calc(100vh - 560px) !important;
  overflow-y: auto !important;
}
</style>
