<template>
  <div class="student-layout">
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
          <el-dropdown @command="handleCommand">
            <div class="user-info">
              <el-icon><User /></el-icon>
              <span>{{ getStudentName() }}</span>
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
            <el-menu-item index="/student/dashboard">
              <el-icon><HomeFilled /></el-icon>
              <template #title>首页</template>
            </el-menu-item>
            <el-menu-item index="/student/announcements">
              <el-icon><Bell /></el-icon>
              <template #title>公告通知</template>
            </el-menu-item>
            <el-menu-item index="/student/internship">
              <el-icon><Document /></el-icon>
              <template #title>实习管理</template>
            </el-menu-item>
            <el-menu-item index="/student/applications">
              <el-icon><List /></el-icon>
              <template #title>申请记录</template>
            </el-menu-item>
            <el-menu-item index="/student/profile">
              <el-icon><User /></el-icon>
              <template #title>个人中心</template>
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
  Document,
  List,
  Bell,
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
      // 清除当前学生角色的token，避免残留
      const currentRole = authStore.role
      if (currentRole === 'ROLE_STUDENT') {
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

onMounted(() => {
  console.log('[StudentLayout] 组件已挂载')
})

const sidebarCollapsed = ref(false)

const activeMenu = computed(() => route.path)

useUserStatusCheck(180000)

const getStudentName = () => {
  return authStore.user?.name || '学生用户'
}

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

const handleCommand = async (command) => {
  switch (command) {
    case 'edit':
      router.push('/student/profile')
      break
    case 'password':
      router.push('/student/profile')
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
.student-layout {
  height: 100vh;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #67C23A;
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
