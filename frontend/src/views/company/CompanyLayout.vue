<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ElScrollbar } from 'element-plus'
import { useAuthStore } from '@/store/auth'
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
  View
} from '@element-plus/icons-vue'

defineOptions({
  components: {
    ElScrollbar
  }
})

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const sidebarCollapsed = ref(false)

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

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

const handleCommand = async (command) => {
  switch (command) {
    case 'edit':
      router.push('/company/settings?tab=profile')
      break
    case 'password':
      router.push('/company/settings?tab=password')
      break
    case 'logout':
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
      break
    default:
      break
  }
}

onMounted(() => {
  console.log('CompanyLayout 挂载，当前用户:', currentUser.value)
})
</script>

<template>
  <div class="app-container">
    <header class="header">
      <div class="header-left">
        <el-button type="text" @click="toggleSidebar" class="sidebar-toggle-btn">
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
          <span class="logo-text">企业端</span>
          <div class="logo-subtitle">学生实习管理系统</div>
        </div>
      </div>
      <div class="user-actions">
        <el-dropdown @command="handleCommand" class="user-dropdown">
          <el-button type="text" class="user-btn">
            <div class="user-info">
              <el-icon class="user-avatar">
                <User />
              </el-icon>
              <span class="user-name">欢迎您，{{ displayName }}</span>
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

    <div class="main-content">
      <aside :class="['sidebar', { 'sidebar-collapsed': sidebarCollapsed }]" v-show="!sidebarCollapsed">
        <div class="sidebar-header">
          <div class="sidebar-title">系统菜单</div>
        </div>
        <el-menu :router="true" :default-active="route.path" class="el-menu-vertical-demo" mode="vertical">
          <el-menu-item index="/company">
            <el-icon>
              <HomeFilled />
            </el-icon>
            <span>首页</span>
          </el-menu-item>

          <el-menu-item index="/company/jobs">
            <el-icon>
              <OfficeBuilding />
            </el-icon>
            <span>招聘管理</span>
          </el-menu-item>

          <el-menu-item index="/company/internship-confirm">
            <el-icon>
              <DocumentChecked />
            </el-icon>
            <span>实习确认</span>
          </el-menu-item>

          <el-menu-item index="/company/application-view">
            <el-icon>
              <View />
            </el-icon>
            <span>岗位申请查看</span>
          </el-menu-item>

          <el-menu-item index="/company/interviews">
            <el-icon>
              <Document />
            </el-icon>
            <span>企业信息</span>
          </el-menu-item>

          <el-menu-item index="/company/settings">
            <el-icon>
              <Setting />
            </el-icon>
            <span>账号设置</span>
          </el-menu-item>
        </el-menu>
      </aside>

      <el-scrollbar style="flex: 1;">
        <el-main>
          <router-view></router-view>
        </el-main>
      </el-scrollbar>
    </div>
  </div>
</template>

<style scoped>
.app-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

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
  color: white;
  font-size: 20px;
  padding: 8px;
}

.sidebar-toggle-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}

.logo {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.logo-text {
  font-size: 20px;
  font-weight: bold;
  letter-spacing: 1px;
}

.logo-subtitle {
  font-size: 12px;
  opacity: 0.9;
}

.user-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-dropdown {
  cursor: pointer;
}

.user-btn {
  color: white;
  padding: 8px 16px;
  border-radius: 20px;
  transition: all 0.3s ease;
}

.user-btn:hover {
  background: rgba(255, 255, 255, 0.15);
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
  min-width: 180px;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  transition: all 0.2s ease;
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

.main-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.sidebar {
  width: 240px;
  background: white;
  border-right: 1px solid #e8e8e8;
  overflow-y: auto;
  transition: all 0.3s ease;
}

.sidebar-collapsed {
  width: 64px;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid #e8e8e8;
}

.sidebar-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.el-menu-vertical-demo {
  border-right: none;
}

.el-menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  margin: 4px 12px;
  border-radius: 6px;
  transition: all 0.2s ease;
}

.el-menu-item:hover {
  background: #f0f7ff;
  color: #409EFF;
}

.el-menu-item.is-active {
  background: #409EFF;
  color: white;
}

.el-main {
  padding: 24px;
  background: #f5f7fa;
  max-width: 100%;
  width: 100%;
}

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
