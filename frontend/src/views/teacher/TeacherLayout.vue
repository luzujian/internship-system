<template>
  <div class="teacher-layout agent-host">
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
          <el-button link @click="toggleSidebar" class="sidebar-toggle-btn" :aria-expanded="!sidebarCollapsed" aria-controls="main-navigation" :aria-label="sidebarCollapsed ? '展开导航菜单' : '收起导航菜单'">
            <el-icon>
              <component :is="sidebarCollapsed ? Menu : Fold" />
            </el-icon>
          </el-button>
          <div class="logo">
          <span class="logo-text">DeepIntern</span>
          <div class="logo-subtitle">教师端</div>
        </div>
        </div>
        <div class="header-metrics" aria-label="教师宏观数据">
          <span class="header-metrics-title">宏观数据</span>
          <span class="header-metrics-divider"></span>
          <div class="header-metric header-metric--students" title="本院暂无有效投递与已投递未录用学生总数，不含已延期实习学生。">
            <span class="header-metric-dot"></span>
            <span class="header-metric-label">本院尚未获录用</span>
            <strong>{{ withoutOfferStudentCount ?? '--' }}</strong>
            <small>人</small>
          </div>
          <span class="header-metrics-divider"></span>
          <div class="header-metric header-metric--quota" title="全平台所有有效招聘岗位的剩余名额之和">
            <span class="header-metric-dot"></span>
            <span class="header-metric-label">全平台剩余名额</span>
            <strong>{{ totalRemainingQuota ?? '--' }}</strong>
            <small>个</small>
          </div>
        </div>
        <div class="header-right">
          <AgentEntry />
          <div class="teacher-type-badge">{{ getTeacherTypeText() }}</div>
          <el-dropdown :popper-style="{ zIndex: 2500 }" @command="handleCommand">
            <button type="button" class="user-info user-btn" aria-label="账号菜单">
              <el-icon><User /></el-icon>
              <span>{{ getTeacherName() }}</span>
              <el-icon><CaretBottom /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="feedback">问题反馈</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <FeedbackSubmitDialog v-model="feedbackVisible" user-type="teacher" />
        </div>
      </el-header>

      <el-container style="flex: 1; display: flex; overflow: hidden;">
        <aside :class="['aside', { 'aside-collapsed': sidebarCollapsed }]" v-show="!sidebarCollapsed" id="main-navigation" :tabindex="isMobile ? -1 : undefined" aria-label="主导航" :role="isMobile && !sidebarCollapsed ? 'dialog' : undefined" :aria-modal="isMobile && !sidebarCollapsed || undefined" :inert="isMobile && sidebarCollapsed">
      <button v-if="isMobile" type="button" class="mobile-navigation-close" aria-label="关闭导航菜单" @click="closeSidebar">关闭导航 ×</button>
          <el-scrollbar>
            <el-menu
              v-if="!menuLoading"
              :default-active="activeMenu"
              router
              class="el-menu-vertical-demo"
            >
              <el-menu-item
                v-for="menu in availableMenus"
                :key="menu.path"
                :index="menu.path"
                @click="handleMenuClick(menu.path)"
               :data-agent-route="menu.path">
                <el-icon><component :is="getMenuIcon(menu.icon)" /></el-icon>
                <template #title>{{ menu.name }}</template>
              </el-menu-item>
            </el-menu>
          </el-scrollbar>
        </aside>
        <div v-if="isMobile && !sidebarCollapsed" class="sidebar-overlay" @click="closeSidebar"></div>

        <el-main class="main" data-navigation-content :inert="isMobile && !sidebarCollapsed">
          <el-scrollbar style="height: 100%;">
            <router-view />
          </el-scrollbar>
        </el-main>
      </el-container>
    </el-container>
    <AgentWorkspace />
  </div>
</template>

<script lang="ts" setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
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
  DocumentChecked,
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
import { getTeacherMacroStatistics } from '@/api/teacherMacroStatistics'
import request from '@/utils/request'
import { initAnnouncementWebSocket, disconnectAnnouncementWebSocket } from '@/utils/websocket'
import AgentWorkspace from '@/components/agent/AgentWorkspace.vue'
import AgentEntry from '@/components/agent/AgentEntry.vue'
import FeedbackSubmitDialog from '@/components/FeedbackSubmitDialog.vue'
import { useResponsiveSidebar } from '@/composables/useResponsiveSidebar'
import { isBusinessRouteEnabled } from '@/utils/businessFeatures'

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

// 问题反馈弹窗
const feedbackVisible = ref(false)
const systemSettingsStore = useSystemSettingsStore()

const isReadOnlyMode = computed(() => authStore.user?.isReadOnly || false)
const dynamicMenus = ref<MenuItem[]>([])
const availableMenus = computed(() => dynamicMenus.value.filter(menu => isBusinessRouteEnabled(menu.path)))
const menuLoading = ref(true)
const withoutOfferStudentCount = ref<number | null>(null)
const totalRemainingQuota = ref<number | null>(null)

const loadMacroStatistics = async () => {
  try {
    const statistics = await getTeacherMacroStatistics()
    withoutOfferStudentCount.value = statistics.withoutOfferStudentCount ?? 0
    totalRemainingQuota.value = statistics.totalRemainingQuota ?? 0
  } catch (error) {
    console.error('[TeacherLayout] 加载宏观统计失败:', error)
  }
}

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
      if (!authStore.restoreAdminSession()) {
        throw new Error('管理员会话已失效，请重新登录')
      }

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
  'DocumentChecked': DocumentChecked,
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
  menuLoading.value = true
  dynamicMenus.value = []

  const role = authStore.role
  if (!role || !role.startsWith('ROLE_TEACHER')) {
    console.log('[TeacherLayout] 非教师角色，跳过获取菜单')
    menuLoading.value = false
    return
  }

  const teacherType = authStore.user?.teacherType || authStore.role?.replace('ROLE_TEACHER_', '')
  if (!teacherType) {
    console.log('[TeacherLayout] 未找到教师类型，仅显示基础菜单')
    dynamicMenus.value = getSafeMenus()
    menuLoading.value = false
    return
  }

  console.log('[TeacherLayout] 教师类型:', teacherType)
  
  try {
    interface TeacherMenuPermission {
      id?: number
      permissionCode?: string
      permission_code?: string
      permissionName?: string
      permission_name?: string
      path?: string
      icon?: string
      sortOrder?: number
      sort_order?: number
    }
    const response = await request.get<TeacherMenuPermission[]>('/teacher/permissions/tree')
    if (response.code === 200) {
      const allMenus = Array.isArray(response.data) ? response.data : []
      const permissionsResponse = await request.get<TeacherMenuPermission[]>(`/teacher/permissions/role-code/${teacherType}/permissions`)
      
      if (permissionsResponse.code === 200) {
        const permissions = Array.isArray(permissionsResponse.data) ? permissionsResponse.data : []
        console.log('[TeacherLayout] 用户权限列表:', permissions)
        
        dynamicMenus.value = permissions.map((perm: any) => {
          const menuInfo = allMenus.find((m: any) => m.permissionCode === perm.permissionCode)
          return {
            id: perm.id,
            name: perm.permissionCode === 'menu:appraisal-collection' ? '实习材料收集' : perm.permissionName,
            path: perm.path || (menuInfo?.path || ''),
            icon: perm.icon || menuInfo?.icon,
            sortOrder: perm.sortOrder || 0
          }
        }).sort((a: any, b: any) => a.sortOrder - b.sortOrder)
        
        console.log('[TeacherLayout] 动态菜单:', dynamicMenus.value)
        
        if (dynamicMenus.value.length === 0) {
          console.log('[TeacherLayout] 动态菜单为空（管理员未分配权限），仅显示首页')
          dynamicMenus.value = [{
            id: 1,
            name: '首页',
            path: '/teacher/home',
            icon: 'HomeFilled',
            sortOrder: 1
          }]
        }
      } else {
        console.log('[TeacherLayout] 获取权限列表失败，仅显示基础菜单')
        dynamicMenus.value = getSafeMenus()
      }
    } else {
      console.log('[TeacherLayout] 获取权限树失败，仅显示基础菜单')
      dynamicMenus.value = getSafeMenus()
    }
  } catch (error) {
    console.error('[TeacherLayout] 获取菜单失败:', error)
    dynamicMenus.value = getSafeMenus()
  } finally {
    menuLoading.value = false
  }
}

const getSafeMenus = (): MenuItem[] => ([
  { id: 1, name: '首页', path: '/teacher/home', icon: 'HomeFilled', sortOrder: 1 }
])

onMounted(() => {
  console.log('[TeacherLayout] 组件已挂载')
  if (!systemSettingsStore.systemName) {
    systemSettingsStore.loadSettings()
  }
  fetchTeacherMenus()
  loadMacroStatistics()
  initWebSocket()
})

onUnmounted(() => {
  disconnectAnnouncementWebSocket()
})

// 初始化WebSocket连接
const initWebSocket = () => {
  let token = authStore.token

  if (!token) {
    const rolePrefix = 'teacher_'
    const role = authStore.role || 'ROLE_TEACHER'
    token = localStorage.getItem(`${rolePrefix}accessToken_${role}`) ||
            localStorage.getItem(`${rolePrefix}token_${role}`) ||
            localStorage.getItem('accessToken')
  }

  if (token) {
    console.log('[TeacherLayout] 初始化 WebSocket')
    initAnnouncementWebSocket(token, (data) => {
      console.log('[TeacherLayout] 收到 WebSocket 消息, type:', data.type)
      if (data.type === 'new_announcement') {
        ElMessage.success({
          message: `新公告：${data.data?.title || '未知标题'}`,
          duration: 5000,
          showClose: true
        })
        window.dispatchEvent(new CustomEvent('teacher:new-announcement'))
      }
    })
  }
}

watch(() => authStore.role, (newRole) => {
  if (newRole && newRole.startsWith('ROLE_TEACHER')) {
    fetchTeacherMenus()
    loadMacroStatistics()
  }
})

watch(() => route.path, (newPath, oldPath) => {
  if (oldPath && newPath !== oldPath) {
      loadMacroStatistics()
  }
})

watch(() => authStore.user?.teacherType, (newType) => {
  if (newType) {
    console.log('[TeacherLayout] 教师类型变化:', newType)
    fetchTeacherMenus()
  }
})

const { isMobile, sidebarCollapsed, toggleSidebar, closeSidebar } = useResponsiveSidebar()

const activeMenu = computed(() => route.path)

useUserStatusCheck(180000)

const getTeacherName = () => {
  return authStore.user?.name || '教师用户'
}

const getTeacherTypeText = () => {
  const teacherType = authStore.user?.teacherType || authStore.role?.replace('ROLE_TEACHER_', '')
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

const handleCommand = async (command) => {
  switch (command) {
    case 'password':
      router.push('/teacher/account-settings')
      break
    case 'feedback':
      feedbackVisible.value = true
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

.header-left,
.header-metrics,
.header-right {
  position: relative;
  z-index: 1;
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
  font-size: 28px;
  font-weight: 800;
  letter-spacing: 3px;
  color: #ffffff;
  text-shadow:
    0 0 6px rgba(255, 255, 255, 0.5),
    0 0 12px rgba(255, 255, 255, 0.3),
    0 2px 4px rgba(0, 0, 0, 0.3);
}

.logo-subtitle {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 4px;
  color: rgba(255, 255, 255, 0.9);
  text-shadow:
    0 0 5px rgba(255, 255, 255, 0.4),
    0 2px 4px rgba(0, 0, 0, 0.2);
}

.header-metrics {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 7px 14px;
  border: 1px solid rgba(255, 255, 255, 0.55);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.94);
  color: #334155;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.12);
}

.header-metric {
  display: flex;
  align-items: center;
  white-space: nowrap;
}

.header-metrics-title {
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.header-metrics-divider {
  width: 1px;
  height: 24px;
  background: #dbe4ef;
}

.header-metric {
  gap: 6px;
  font-size: 13px;
}

.header-metric-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.header-metric strong {
  margin-left: 2px;
  font-size: 18px;
  font-weight: 800;
  line-height: 1;
}

.header-metric small {
  color: #64748b;
  font-size: 11px;
}

.header-metric--students .header-metric-dot {
  background: #f97316;
}

.header-metric--students strong {
  color: #c2410c;
}

.header-metric--quota .header-metric-dot {
  background: #3b82f6;
}

.header-metric--quota strong {
  color: #1d4ed8;
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

/* All header controls use the same header-right gap. */
.header-right :deep(.agent-entry) {
  margin-inline: 0;
}

.user-info {
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  box-shadow: none;
  transform: none;
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
  box-shadow: none;
  transform: none;
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
  padding: 12px;
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

/* 表格内部滚动容器恢复横向滚动（上方规则仅约束页面级滚动容器） */
.main :deep(.el-table .el-scrollbar__wrap) {
  overflow-x: auto;
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

@media (max-width: 1180px) {
  .header-metrics-title,
  .header-metrics-divider:first-of-type {
    display: none;
  }

  .teacher-type-badge {
    display: none;
  }
}

@media (max-width: 860px) {
  .header {
    padding: 0 12px;
  }

  .header-metrics {
    gap: 8px;
    padding: 7px 10px;
  }

  .header-metric-label,
  .header-metric small {
    display: none;
  }

}

@media (max-width: 768px) {
  .header-right {
    gap: 10px;
  }
}

@media (max-width: 620px) {
  .logo-subtitle,
  .user-info span {
    display: none;
  }

  .logo-text {
    font-size: 20px;
    letter-spacing: 1px;
  }

  .header-left {
    gap: 6px;
  }

  /* 窄屏统计丸：只留两个数字，去圆点/分隔线/紧凑字号，避免被挤压裁掉 */
  .header-metrics {
    flex-shrink: 0;
    padding: 5px 8px;
    gap: 6px;
  }

  .header-metric {
    gap: 4px;
    white-space: nowrap;
  }

  .header-metric strong {
    font-size: 15px;
  }

  .header-metric-dot,
  .header-metrics-divider {
    display: none;
  }
}

/* 手机端侧边栏为遮罩抽屉 */
@media (max-width: 768px) {
  .aside {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    width: min(82vw, 280px);
    min-width: 0;
    max-width: none;
    height: auto;
    z-index: 1999;
    transform: translateX(-100%);
    transition: transform 0.3s ease;
    box-shadow: 4px 0 16px rgba(0, 0, 0, 0.18);
  }

  .aside:not(.aside-collapsed) {
    transform: translateX(0);
  }

  /* 手机端收起仅位移隐藏，保持抽屉宽度 */
  .aside.aside-collapsed {
    width: min(82vw, 280px);
    opacity: 1;
  }

  .main {
    padding: 8px;
  }
}
</style>
