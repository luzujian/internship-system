<template>
  <div class="permission-management-container">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">权限管理</h1>
        <p class="page-description">管理系统角色和权限分配</p>
      </div>
      <div class="header-illustration">
        <div class="illustration-circle circle-1"></div>
        <div class="illustration-circle circle-2"></div>
      </div>
    </div>

    <el-card class="main-card" shadow="never">
      <el-tabs v-model="activeTab" class="permission-tabs">
        <el-tab-pane label="管理员角色" name="roles">
          <div class="table-container">
            <el-table 
              :data="roles" 
              border 
              stripe 
              style="width: 100%" 
              v-loading="loading"
              class="permission-table"
            >
              <el-table-column prop="id" label="ID" width="150" align="center">
                <template #default="{ row }">
                  <span>{{ row.id }}</span>
                  <el-tag v-if="isCurrentUserRole(row)" type="success" size="small" style="margin-left: 8px" class="current-user-tag">当前登录用户</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="roleName" label="角色名称" width="180" align="center" />
              <el-table-column prop="roleDesc" label="角色描述" align="center" />
              <el-table-column prop="status" label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 1 ? 'success' : 'danger'" class="status-tag">
                    {{ row.status === 1 ? '启用' : '禁用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150" fixed="right" align="center">
                <template #default="{ row }">
                  <div class="action-buttons">
                    <el-tooltip v-if="authStore.hasPermission('permission:edit')" content="分配权限" placement="top">
                      <el-button type="primary" link size="small" @click="handleAssignPermissions(row)" class="table-btn assign">
                        <el-icon><Key /></el-icon>
                      </el-button>
                    </el-tooltip>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="教师角色" name="teacherRoles">
          <div class="table-container">
            <el-table 
              :data="teacherRoles" 
              border 
              stripe 
              style="width: 100%" 
              v-loading="teacherLoading"
              class="permission-table"
            >
              <el-table-column prop="id" label="ID" width="80" align="center" />
              <el-table-column prop="roleName" label="角色名称" width="150" align="center">
                <template #default="{ row }">
                  <el-tag :type="getTeacherRoleTagType(row.roleCode)">
                    {{ row.roleName }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="roleCode" label="角色代码" width="200" align="center" />
              <el-table-column prop="description" label="角色描述" align="center" />
              <el-table-column label="菜单权限" width="120" align="center">
                <template #default="{ row }">
                  <el-tag type="info" size="small">
                    {{ getTeacherPermissionCount(row.id) }} 个菜单
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150" fixed="right" align="center">
                <template #default="{ row }">
                  <div class="action-buttons">
                    <el-tooltip content="配置菜单权限" placement="top">
                      <el-button type="primary" link size="small" @click="handleTeacherPermissionConfig(row)" class="table-btn assign">
                        <el-icon><Setting /></el-icon>
                      </el-button>
                    </el-tooltip>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="学生端菜单配置" name="studentMenu">
          <div class="permission-tree-container">
            <el-card shadow="never" class="tree-card">
              <div class="teacher-permission-header">
                <h3 class="assignment-title">配置学生端菜单</h3>
                <p class="assignment-description">勾选菜单项启用，拖拽菜单项调整显示顺序</p>
                <div style="margin-top: 10px;">
                  <el-button type="primary" @click="saveStudentMenus" :loading="studentSaving">保存配置</el-button>
                  <el-button @click="resetStudentMenus">恢复默认</el-button>
                </div>
              </div>
              <div class="menu-list-draggable" style="margin-top: 20px;">
                <div 
                  v-for="(menu, index) in studentMenus" 
                  :key="menu.path" 
                  v-show="isBusinessRouteEnabled(menu.path)"
                  class="menu-item-draggable"
                  :class="{ 'selected': menu.visible, 'dragging': draggedStudentIndex === index }"
                  draggable="true"
                  @dragstart="handleStudentDragStart(index, $event)"
                  @dragover.prevent="handleStudentDragOver(index, $event)"
                  @drop="handleStudentDrop(index, $event)"
                  @dragend="handleStudentDragEnd"
                >
                  <div class="menu-drag-handle">
                    <el-icon class="drag-icon"><Rank /></el-icon>
                  </div>
                  <el-checkbox 
                    v-model="menu.visible"
                  >
                    <div class="menu-info">
                      <el-icon class="menu-icon"><component :is="getMenuIcon(menu.icon)" /></el-icon>
                      <span class="menu-name">{{ menu.name }}</span>
                      <el-tag size="small" type="info" class="menu-path">{{ menu.path }}</el-tag>
                    </div>
                  </el-checkbox>
                </div>
              </div>
            </el-card>
          </div>
        </el-tab-pane>

        <el-tab-pane label="AI权限管理" name="generalPermission">
          <div class="permission-tree-container">
            <el-card shadow="never" class="tree-card">
              <div class="teacher-permission-header">
                <h3 class="assignment-title">通用系统功能管控</h3>
                <p class="assignment-description">配置系统全局功能的开启与关闭状态</p>
              </div>
              <div style="margin-top: 20px;">
                <el-form label-width="200px">
                  <el-form-item label="AI 助手" style="margin-bottom: 20px;">
                    <el-switch
                      v-model="aiAssistantEnabled"
                      active-text="启用"
                      inactive-text="关闭"
                      @change="saveAiConfig"
                    ></el-switch>
                    <div style="margin-left: 20px; color: #909399; font-size: 13px;">
                      关闭后，各页面（学生/教师/企业/管理员）的 AI 助手入口均隐藏且不可使用
                    </div>
                  </el-form-item>
                </el-form>
              </div>
            </el-card>
            <el-card shadow="never" class="tree-card" style="margin-top: 16px;">
              <div class="teacher-permission-header">
                <h3 class="assignment-title">AI 能力授权（角色级）</h3>
                <p class="assignment-description">叠加在原业务权限之上，只收窄不放大；关闭对话问答等于该角色不可用助手；只读身份仅保留对话问答</p>
                <div style="margin-top: 10px; display: flex; gap: 8px; align-items: center;">
                  <el-button type="primary" @click="saveAgentCaps" :loading="agentCapsSaving">保存配置</el-button>
                  <el-button @click="resetAgentCaps" :disabled="agentCapsLoading">恢复默认</el-button>
                  <el-tag v-if="!agentCapsConfigured" size="small" type="info">未配置·显示默认</el-tag>
                </div>
              </div>
              <div style="margin-top: 16px;" v-loading="agentCapsLoading">
                <el-table :data="agentCapRows" border stripe style="width: 100%" class="permission-table">
                  <el-table-column prop="label" label="AI 能力" width="220" align="center">
                    <template #default="{ row }">
                      <span>{{ row.label }}</span>
                      <el-tag v-if="row.reserved" size="small" type="warning" style="margin-left: 8px;">预留未投产</el-tag>
                      <div style="color: #909399; font-size: 12px; margin-top: 4px;">{{ row.desc }}</div>
                    </template>
                  </el-table-column>
                  <el-table-column v-for="role in agentRoleColumns" :key="role.key" :prop="role.key" :label="role.label" align="center">
                    <template #default="{ row }">
                      <el-tooltip :content="capabilitySupportHint(role.key, row.key)" :disabled="supportsAgentCapability(role.key, row.key)" placement="top">
                        <span>
                          <el-checkbox
                            :model-value="supportsAgentCapability(role.key, row.key) && agentCaps[role.key][row.key]"
                            @change="value => agentCaps[role.key][row.key] = Boolean(value)"
                            :aria-label="`${role.label} · ${row.label}`"
                            :disabled="row.reserved || agentCapsLoading || !supportsAgentCapability(role.key, row.key)"
                          />
                        </span>
                      </el-tooltip>
                    </template>
                  </el-table-column>
                </el-table>
              </div>

              <AutomationVerification />
              <el-divider content-position="left">用户级覆盖</el-divider>
              <div class="user-override-box">
                <div class="user-override-toolbar">
                  <el-select v-model="overrideRole" style="width: 120px" @change="resetOverrideUser">
                    <el-option label="学生" value="student" />
                    <el-option label="教师" value="teacher" />
                    <el-option label="企业" value="company" />
                    <el-option label="管理员" value="admin" />
                  </el-select>
                  <el-input
                    v-model="overrideKeyword"
                    placeholder="搜索用户名或姓名"
                    style="width: 220px"
                    clearable
                    @keyup.enter="searchOverrideUsers"
                  />
                  <el-button :loading="overrideLoading" @click="searchOverrideUsers">搜索用户</el-button>
                </div>
                <el-table
                  :data="overrideUsers"
                  size="small"
                  max-height="240"
                  empty-text="请搜索用户"
                  highlight-current-row
                  @row-click="selectOverrideUser"
                >
                  <el-table-column prop="id" label="ID" width="90" />
                  <el-table-column prop="displayName" label="姓名 / 企业名称" min-width="140" />
                  <el-table-column prop="account" label="用户名" min-width="140" />
                  <el-table-column label="操作" width="90">
                    <template #default="{ row }">
                      <el-button link type="primary" @click.stop="selectOverrideUser(row)">配置</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <div v-if="selectedOverrideUser" class="user-override-editor">
                  <div class="user-override-title">
                    {{ roleLabel(overrideRole) }} · {{ selectedOverrideUser.displayName }}（{{ selectedOverrideUser.account || selectedOverrideUser.id }}）
                    <span class="user-override-hint">用户级设置优先于角色默认</span>
                  </div>
                  <el-checkbox
                    v-for="row in agentCapRows.filter(item => !item.reserved)"
                    :key="row.key"
                    :model-value="supportsAgentCapability(overrideRole, row.key) && overrideCaps[row.key]"
                    @change="value => overrideCaps[row.key] = Boolean(value)"
                    :disabled="overrideReading || !supportsAgentCapability(overrideRole, row.key)"
                    :title="capabilitySupportHint(overrideRole, row.key)"
                    class="user-override-check"
                  >{{ row.label }}<span v-if="!supportsAgentCapability(overrideRole, row.key)">（该角色不支持）</span></el-checkbox>
                  <div class="user-override-actions">
                    <el-button @click="resetOverrideUser">取消</el-button>
                    <el-button :loading="overrideSaving" @click="clearUserOverride">清除覆盖（恢复默认）</el-button>
                    <el-button type="primary" :loading="overrideSaving" @click="saveUserOverride">保存该用户覆盖</el-button>
                  </div>
                </div>
              </div>
            </el-card>
          </div>
        </el-tab-pane>

        <el-tab-pane label="管理员权限列表" name="permissions">
          <div class="permission-tree-container">
            <el-card shadow="never" class="tree-card">
              <div class="tree-header">
                <h3 class="tree-title">系统权限结构</h3>
                <p class="tree-description">所有可用权限的树形结构展示</p>
              </div>
              <el-tree
                :data="filteredPermissionTree"
                :props="{ children: 'children', label: 'module' }"
                node-key="id"
                default-expand-all
                class="permission-tree"
              >
                <template #default="{ node, data }">
                  <span class="custom-tree-node">
                    <span v-if="data.module && !data.permissionCode" class="module-label">
                      <el-icon class="module-icon"><component :is="getModuleIcon(data.module)" /></el-icon>
                      {{ data.module }}
                    </span>
                    <span v-else class="permission-label">
                      <el-tag size="small" type="info" class="permission-tag">{{ data.permissionCode }}</el-tag>
                      {{ data.permissionName }}
                    </span>
                  </span>
                </template>
              </el-tree>
            </el-card>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="permissionDialogVisible" title="分配权限" width="600px" class="permission-dialog">
      <el-scrollbar height="500px">
        <div class="permission-assignment">
          <div class="assignment-header">
            <h3 class="assignment-title">为角色 {{ currentRole?.roleName }} 分配权限</h3>
            <p class="assignment-description">选择该角色可以访问的权限模块</p>
          </div>
          <el-tree
            ref="permissionTreeRef"
            :data="dialogPermissionTree"
            :props="{ children: 'children', label: 'module', disabled: 'disabled' }"
            node-key="id"
            show-checkbox
            default-expand-all
            class="assignment-tree"
          >
            <template #default="{ node, data }">
              <span class="custom-tree-node">
                <span v-if="data.module && !data.permissionCode" class="module-label">
                  <el-icon class="module-icon"><component :is="getModuleIcon(data.module)" /></el-icon>
                  {{ data.module }}
                </span>
                <span v-else class="permission-label">
                  <el-tag size="small" type="info" class="permission-tag">{{ data.permissionCode }}</el-tag>
                  {{ data.permissionName }}
                </span>
              </span>
            </template>
          </el-tree>
        </div>
      </el-scrollbar>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="permissionDialogVisible = false" class="cancel-btn">取消</el-button>
          <el-button type="primary" @click="savePermissions" :loading="saving" class="confirm-btn">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="teacherPermissionDialogVisible" title="配置教师菜单权限" width="700px" class="permission-dialog">
      <div class="teacher-permission-header">
        <h3 class="assignment-title">为 {{ currentTeacherRole?.roleName }} 配置菜单权限</h3>
        <p class="assignment-description">勾选菜单项启用，拖拽菜单项调整显示顺序</p>
      </div>
      <el-scrollbar height="450px">
        <div class="menu-list-draggable">
          <div 
            v-for="(menu, index) in sortedMenuList" 
            :key="menu.id" 
            v-show="isBusinessPermissionEnabled(menu)"
            class="menu-item-draggable"
            :class="{ 'selected': selectedMenuIds.includes(menu.id), 'dragging': draggedIndex === index }"
            draggable="true"
            @dragstart="handleDragStart(index, $event)"
            @dragover.prevent="handleDragOver(index, $event)"
            @drop="handleDrop(index, $event)"
            @dragend="handleDragEnd"
          >
            <div class="menu-drag-handle">
              <el-icon class="drag-icon"><Rank /></el-icon>
            </div>
            <el-checkbox 
              :model-value="selectedMenuIds.includes(menu.id)" 
              @change="(val: boolean) => handleMenuCheck(menu.id, val)"
            >
              <div class="menu-info">
                <el-icon class="menu-icon"><component :is="getMenuIcon(menu.icon)" /></el-icon>
                <span class="menu-name">{{ menu.permissionName }}</span>
                <el-tag size="small" type="info" class="menu-path">{{ menu.path }}</el-tag>
              </div>
            </el-checkbox>
          </div>
        </div>
      </el-scrollbar>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="teacherPermissionDialogVisible = false" class="cancel-btn">取消</el-button>
          <el-button type="primary" @click="saveTeacherPermissions" :loading="teacherSaving" class="confirm-btn">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import logger from '@/utils/logger'
import AutomationVerification from '@/components/agent/AutomationVerification.vue'
import { ref, onMounted, computed, nextTick, h } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ElScrollbar } from 'element-plus'
import { Key, User, DocumentChecked, DataLine, Setting, MagicStick, Lock, Briefcase, ChatDotRound, Folder, ChatLineRound, Document, School, Collection, Download, HomeFilled, Monitor, Edit, Bell, OfficeBuilding, Upload, Clock, Rank } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useAuthStore } from '@/store/auth'
import { reflectionEnabled, isBusinessRouteEnabled, isBusinessPermissionEnabled } from '@/utils/businessFeatures'
import { isReflectionPermission } from '@/utils/reflectionBusiness.mjs'

defineOptions({
  components: {
    ElScrollbar
  }
})

const authStore = useAuthStore()

interface RoleRow {
  id?: number
  roleCode?: string
  role_code?: string
  roleName?: string
  [key: string]: unknown
}
interface PermissionNode {
  id?: number
  module?: string
  permissionCode?: string
  permission_code?: string
  sortOrder?: number
  sort_order?: number
  children?: PermissionNode[]
  [key: string]: unknown
}

const activeTab = ref('roles')
const loading = ref(false)
const saving = ref(false)
const roles = ref<RoleRow[]>([])
const permissionTree = ref<PermissionNode[]>([])
const permissionDialogVisible = ref(false)
const permissionTreeRef = ref(null)
const currentRole = ref(null)
const retainedAdminPermissionIds = ref<number[]>([])

const teacherLoading = ref(false)
const teacherSaving = ref(false)
const teacherRoles = ref<RoleRow[]>([])
const teacherPermissionList = ref<PermissionNode[]>([])
const teacherPermissionDialogVisible = ref(false)
const currentTeacherRole = ref(null)
const selectedMenuIds = ref<number[]>([])
const teacherPermissionCounts = ref<Record<number, number>>({})

const draggedIndex = ref<number | null>(null)
const menuSortOrder = ref<number[]>([])

// 学生菜单配置
const defaultStudentMenus = [
  { path: '/student/home', name: '首页', icon: 'HomeFilled', visible: true },
  { path: '/student/jobs', name: '职位浏览', icon: 'Briefcase', visible: true },
  { path: '/student/applications', name: '我的申请', icon: 'Document', visible: true },
  { path: '/student/interviews', name: '面试管理', icon: 'ChatDotRound', visible: true },
  { path: '/student/internship-confirmation-form', name: '实习确认', icon: 'DocumentChecked', visible: true },
  { path: '/student/internships', name: '实习心得', icon: 'Document', visible: true },
  { path: '/student/internship-appraisal', name: '实习鉴定', icon: 'DocumentChecked', visible: true },
  { path: '/student/profile', name: '个人中心', icon: 'User', visible: true }
]
const studentMenus = ref([...defaultStudentMenus])
const studentSaving = ref(false)
const draggedStudentIndex = ref<number | null>(null)

// 通用配置
const aiAssistantEnabled = ref(true)

// AI 能力授权（角色级 roleDefaults，与后端 AgentCapabilityService 对齐）
const agentRoleColumns = [
  { key: 'student', label: '学生' },
  { key: 'teacher', label: '教师' },
  { key: 'company', label: '企业' },
  { key: 'admin', label: '管理员' }
]
const agentCapRows = [
  { key: 'agent_chat', label: '对话问答', desc: '侧栏问答与系统顾问', reserved: false },
  { key: 'voice_input', label: '语音输入', desc: '侧栏麦克风语音转文字', reserved: false },
  { key: 'form_fill', label: '智能填表', desc: '语音/文字回填当前表单', reserved: false },
  { key: 'internship_plan', label: '实习规划', desc: '学生全流程规划陪跑（仅学生可用）', reserved: false },
  { key: 'auto_task', label: '自动任务', desc: '教师条件触发自动化', reserved: false },
  { key: 'auto_task_notify', label: '自动通知', desc: '教师自动化站内/邮件提醒', reserved: false },
  { key: 'teacher_internship_summary', label: '教师实习简报', desc: '教师查询授权范围内学生实习情况（仅教师可用）', reserved: false }
]
// Match the roles supported by the business endpoints, independently of grants.
const capabilityRoles: Record<string, string[]> = {
  agent_chat: ['student', 'teacher', 'company', 'admin'],
  voice_input: ['student', 'teacher', 'company', 'admin'],
  form_fill: ['student', 'teacher', 'company', 'admin'],
  internship_plan: ['student'],
  auto_task: ['teacher'], auto_task_notify: ['teacher'], teacher_internship_summary: ['teacher']
}
const supportsAgentCapability = (role: string, capability: string) => capabilityRoles[capability]?.includes(role) ?? false
const capabilitySupportHint = (role: string, capability: string) => supportsAgentCapability(role, capability) ? ''
  : `该角色暂不支持此能力；仅${(capabilityRoles[capability] || []).map(key => agentRoleColumns.find(item => item.key === key)?.label || key).join('、')}可用`
const AGENT_CAPS_DEFAULTS: Record<string, string[]> = {
  student: ['agent_chat', 'voice_input', 'form_fill', 'internship_plan'],
  teacher: ['agent_chat', 'voice_input', 'form_fill', 'teacher_internship_summary'],
  company: ['agent_chat', 'voice_input', 'form_fill'],
  admin: ['agent_chat', 'voice_input', 'form_fill']
}
const buildDefaultAgentCaps = () => {
  const caps: Record<string, Record<string, boolean>> = {}
  for (const role of agentRoleColumns) {
    caps[role.key] = {}
    for (const row of agentCapRows) {
      caps[role.key][row.key] = (AGENT_CAPS_DEFAULTS[role.key] || []).includes(row.key)
    }
  }
  return caps
}
const agentCaps = ref<Record<string, Record<string, boolean>>>(buildDefaultAgentCaps())
const agentCapsLoading = ref(false)
const agentCapsSaving = ref(false)
const agentCapsConfigured = ref(false)
const agentConfigBase = ref<any>(null)
const agentUserOverrides = ref<Record<string, Record<string, boolean>>>({})
const agentAutoPolicy = ref(buildDefaultAgentAutoPolicy())

function buildDefaultAgentAutoPolicy() {
  return {
    version: 1,
    globalCooldownMinutes: 1440,
    roleDefaults: {
      teacher: {
        actions: ['SEND_REMINDER'],
        channels: ['STATION_REMINDER', 'EMAIL'],
        scopes: ['OWN_CLASSES', 'OWN_DIVISION', 'OWN_COLLEGE'],
        audienceLimitMode: 'ALL_AUTHORIZED',
        maxTargetsPerRun: 200
      }
    },
    userOverrides: {}
  }
}

// 权限管理模块的权限ID和模块节点ID（系统管理-权限管理），admin角色不可取消这些权限
const PERMISSION_MANAGE_LOCKED_IDS = [114, 115, 116, 117, 'sub_系统管理_权限管理']

// 判断当前角色是否为admin
const isAdminRole = computed(() => {
  return currentRole.value?.id === 1 || currentRole.value?.roleCode === 'ROLE_ADMIN'
})

const sortedMenuList = computed(() => {
  const allMenus = [...teacherPermissionList.value]
  if (menuSortOrder.value.length === 0) {
    return allMenus.sort((a, b) => a.sortOrder - b.sortOrder)
  }
  const orderMap = new Map(menuSortOrder.value.map((id, index) => [id, index]))
  return allMenus.sort((a, b) => {
    const orderA = orderMap.has(a.id) ? orderMap.get(a.id)! : 999
    const orderB = orderMap.has(b.id) ? orderMap.get(b.id)! : 999
    return orderA - orderB
  })
})

const handleDragStart = (index: number, event: DragEvent) => {
  draggedIndex.value = index
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

const handleDragOver = (index: number, event: DragEvent) => {
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

const handleDrop = (targetIndex: number, event: DragEvent) => {
  event.preventDefault()
  if (draggedIndex.value === null || draggedIndex.value === targetIndex) return
  
  const currentOrder = [...menuSortOrder.value]
  if (currentOrder.length === 0) {
    menuSortOrder.value = sortedMenuList.value.map(m => m.id)
  }
  
  const draggedId = menuSortOrder.value[draggedIndex.value]
  menuSortOrder.value.splice(draggedIndex.value, 1)
  menuSortOrder.value.splice(targetIndex, 0, draggedId)
  
  draggedIndex.value = null
}

const handleDragEnd = () => {
  draggedIndex.value = null
}

const handleStudentDragStart = (index: number, event: DragEvent) => {
  draggedStudentIndex.value = index
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

const handleStudentDragOver = (index: number, event: DragEvent) => {
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

const handleStudentDrop = (targetIndex: number, event: DragEvent) => {
  event.preventDefault()
  if (draggedStudentIndex.value === null || draggedStudentIndex.value === targetIndex) return
  
  const draggedMenu = studentMenus.value[draggedStudentIndex.value]
  studentMenus.value.splice(draggedStudentIndex.value, 1)
  studentMenus.value.splice(targetIndex, 0, draggedMenu)
  
  draggedStudentIndex.value = null
}

const handleStudentDragEnd = () => {
  draggedStudentIndex.value = null
}

const handleMenuCheck = (menuId: number, checked: boolean) => {
  if (checked) {
    if (!selectedMenuIds.value.includes(menuId)) {
      selectedMenuIds.value.push(menuId)
    }
  } else {
    selectedMenuIds.value = selectedMenuIds.value.filter(id => id !== menuId)
  }
}

const isCurrentUserRole = (role) => {
  return authStore.role === 'ROLE_ADMIN' && role.roleCode === 'ROLE_ADMIN'
}

const getTeacherRoleTagType = (roleCode) => {
  if (roleCode === 'COLLEGE') return 'danger'
  if (roleCode === 'DEPARTMENT') return 'warning'
  if (roleCode === 'COUNSELOR') return 'success'
  return 'info'
}

const getTeacherPermissionCount = (roleId) => {
  return teacherPermissionCounts.value[roleId] || 0
}

const getModuleIcon = (moduleName) => {
  const iconMap = {
    '用户管理': User,
    '学生管理': User,
    '教师管理': User,
    '管理员管理': User,
    '企业管理': Briefcase,
    '撤回申请记录': DocumentChecked,
    '实习确认表': Document,
    '基础数据管理': DataLine,
    '岗位类别管理': Briefcase,
    '班级管理': Collection,
    '院系专业管理': School,
    '招聘管理': Briefcase,
    '撤回申请记录管理': DocumentChecked,
    '公告管理': Document,
    '数据统计': MagicStick,
    '系统管理': Lock,
    '日志管理': Document,
    '权限管理': Key,
    '数据备份与恢复': Download,
    '系统参数配置': Setting,
    'AI配置': ChatDotRound,
    '关键词库管理': Document,
    '评分规则管理': Document,
    'AI大模型选择': ChatDotRound,
    'AI测试': ChatLineRound,
    '资源文档管理': Folder,
    '问题反馈管理': ChatLineRound
  }
  return iconMap[moduleName] || Lock
}

const getMenuIcon = (iconName) => {
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
  return iconMap[iconName] || Document
}

const filteredPermissionTree = computed(() => {
  const normalizeTree = (nodes) => {
    if (!nodes) return []
    return nodes.filter(isBusinessPermissionEnabled).map(node => {
      const newNode: any = {
        id: node.id,
        module: node.module,
        permissionCode: node.permissionCode,
        permissionName: node.permissionName
      }
      if (node.subModules && node.subModules.length > 0) {
        newNode.children = normalizeTree(node.subModules)
      } else if (node.children && node.children.length > 0) {
        newNode.children = normalizeTree(node.children)
      }
      return newNode
    })
  }
  return normalizeTree(permissionTree.value)
})

// 弹窗中使用的权限树，根据角色是否为admin动态设置disabled属性
const dialogPermissionTree = computed(() => {
  const normalizeTree = (nodes) => {
    if (!nodes) return []
    return nodes.filter(isBusinessPermissionEnabled).map(node => {
      const newNode: any = {
        id: node.id,
        module: node.module,
        permissionCode: node.permissionCode,
        permissionName: node.permissionName,
        disabled: false
      }
      // 如果是admin角色，权限管理权限需要禁用
      if (isAdminRole.value && PERMISSION_MANAGE_LOCKED_IDS.includes(node.id)) {
        newNode.disabled = true
      }
      if (node.subModules && node.subModules.length > 0) {
        newNode.children = normalizeTree(node.subModules)
      } else if (node.children && node.children.length > 0) {
        newNode.children = normalizeTree(node.children)
      }
      return newNode
    })
  }
  return normalizeTree(permissionTree.value)
})

const fetchRoles = async () => {
  loading.value = true
  try {
    const response = await request.get<RoleRow[]>('/admin/permissions/roles')
    if (response.code === 200) {
      const roleRows = Array.isArray(response.data) ? response.data : []
      roles.value = roleRows.filter(role => (role.roleCode ?? role.role_code) === 'ROLE_ADMIN')
    } else {
      ElMessage.error(response.message || response.msg || '获取角色列表失败')
    }
  } catch (error) {
    ElMessage.error('获取角色列表失败')
  } finally {
    loading.value = false
  }
}

const fetchPermissionTree = async () => {
  try {
    const response = await request.get<PermissionNode[]>('/admin/permissions/permission-tree')
    logger.log('[PermissionManagement] 权限树接口响应:', response)
    if (response.code === 200) {
      permissionTree.value = Array.isArray(response.data) ? response.data : []
      logger.log('[PermissionManagement] 获取到的权限树数据:', permissionTree.value)
      logger.log('[PermissionManagement] 权限树主模块:', permissionTree.value.map(m => m.module))
    } else {
      ElMessage.error(response.message || response.msg || '获取权限树失败')
    }
  } catch (error) {
    logger.error('[PermissionManagement] 获取权限树失败:', error)
    ElMessage.error('获取权限树失败')
  }
}

const fetchTeacherRoles = async () => {
  teacherLoading.value = true
  try {
    const response = await request.get<RoleRow[]>('/teacher/permissions/roles')
    if (response.code === 200) {
      teacherRoles.value = Array.isArray(response.data) ? response.data : []
      await fetchTeacherPermissionCounts()
    } else {
      ElMessage.error(response.message || response.msg || '获取教师角色列表失败')
    }
  } catch (error) {
    ElMessage.error('获取教师角色列表失败')
  } finally {
    teacherLoading.value = false
  }
}

const fetchTeacherPermissionCounts = async () => {
  for (const role of teacherRoles.value) {
    try {
      const response = await request.get<PermissionNode[]>(`/teacher/permissions/role/${role.id}`)
      if (response.code === 200) {
        const countRows = Array.isArray(response.data) ? response.data : []
        if (role.id !== undefined) teacherPermissionCounts.value[role.id] = countRows.filter(isBusinessPermissionEnabled).length
      }
    } catch (error) {
      logger.error(`获取角色 ${role.id} 权限数量失败:`, error)
    }
  }
}

const fetchTeacherPermissionList = async () => {
  try {
    const response = await request.get<PermissionNode[]>('/teacher/permissions/tree')
    if (response.code === 200) {
      teacherPermissionList.value = Array.isArray(response.data) ? response.data : []
    } else {
      ElMessage.error(response.message || response.msg || '获取教师权限列表失败')
    }
  } catch (error) {
    ElMessage.error('获取教师权限列表失败')
  }
}

const handleAssignPermissions = async (row) => {
  currentRole.value = row
  retainedAdminPermissionIds.value = []
  permissionDialogVisible.value = true

  await nextTick()

  try {
    const response = await request.get<PermissionNode[]>(`/admin/permissions/roles/${row.id}/permissions`)
    if (response.code === 200) {
      const permissionRows = Array.isArray(response.data) ? response.data : []
      retainedAdminPermissionIds.value = permissionRows.filter(isReflectionPermission)
        .map(p => p.id).filter((id): id is number => id !== undefined)
      let checkedKeys: Array<string | number> = permissionRows.map(p => p.id).filter((id): id is number => id !== undefined)

      // 如果是admin角色，确保权限管理权限始终被选中
      if (row.id === 1 || row.roleCode === 'ROLE_ADMIN') {
        checkedKeys = [...new Set([...checkedKeys, ...PERMISSION_MANAGE_LOCKED_IDS])]
        logger.log('[PermissionManagement] admin角色已预设权限管理权限:', PERMISSION_MANAGE_LOCKED_IDS)
      }

      await nextTick()

      if (permissionTreeRef.value) {
        permissionTreeRef.value.setCheckedKeys(checkedKeys)
      }
    }
  } catch (error) {
    ElMessage.error('获取角色权限失败')
  }
}

const handleTeacherPermissionConfig = async (row) => {
  currentTeacherRole.value = row
  menuSortOrder.value = []
  draggedIndex.value = null
  selectedMenuIds.value = []
  teacherPermissionDialogVisible.value = true
  
  await nextTick()
  
  try {
    const response = await request.get<PermissionNode[]>(`/teacher/permissions/role/${row.id}`)
    if (response.code === 200) {
      const rolePermissionRows = Array.isArray(response.data) ? response.data : []
      selectedMenuIds.value = rolePermissionRows.map(p => p.id).filter((id): id is number => id !== undefined)
      const allMenuIds = teacherPermissionList.value.map(m => m.id).filter((id): id is number => id !== undefined)
      const selectedIds = rolePermissionRows.map(p => p.id).filter((id): id is number => id !== undefined)
      const unselectedIds = allMenuIds.filter(id => !selectedIds.includes(id))
      const sortedSelected = [...rolePermissionRows].sort((a, b) => ((a.sortOrder ?? a.sort_order ?? 999) - (b.sortOrder ?? b.sort_order ?? 999)))
      menuSortOrder.value = [...sortedSelected.map(p => p.id).filter((id): id is number => id !== undefined), ...unselectedIds]
    }
  } catch (error) {
    ElMessage.error('获取教师角色权限失败')
  }
}

const savePermissions = async () => {
  if (!permissionTreeRef.value || !currentRole.value) return

  saving.value = true
  try {
    const checkedKeys = permissionTreeRef.value.getCheckedKeys()
    const halfCheckedKeys = permissionTreeRef.value.getHalfCheckedKeys()

    logger.log('[PermissionManagement] 选中的所有节点ID:', checkedKeys)
    logger.log('[PermissionManagement] 半选中的节点ID:', halfCheckedKeys)

    let allKeys = [...checkedKeys, ...halfCheckedKeys,
      ...(reflectionEnabled.value ? [] : retainedAdminPermissionIds.value)]

    // 如果是admin角色，确保权限管理权限始终被包含
    if (currentRole.value.id === 1 || currentRole.value.roleCode === 'ROLE_ADMIN') {
      allKeys = [...new Set([...allKeys, ...PERMISSION_MANAGE_LOCKED_IDS])]
      logger.log('[PermissionManagement] admin角色已强制包含权限管理权限:', PERMISSION_MANAGE_LOCKED_IDS)
    }

    const filteredKeys = allKeys
      .filter(key => {
        const keyStr = key.toString()
        return !keyStr.startsWith('main_') && !keyStr.startsWith('sub_')
      })
      .map(key => Number(key))

    logger.log('[PermissionManagement] 过滤后的权限ID:', filteredKeys)

    if (filteredKeys.length === 0) {
      ElMessage.warning('请至少选择一个权限')
      saving.value = false
      return
    }

    const requestData = {
      permissionIds: filteredKeys
    }
    logger.log('[PermissionManagement] 发送到后端的数据:', requestData)

    const response = await request.post(`/admin/permissions/roles/${currentRole.value.id}/permissions`, requestData)
    
    logger.log('[PermissionManagement] 后端响应:', response)

    if (response.code === 200) {
      ElMessage.success('权限分配成功')
      permissionDialogVisible.value = false

      if (isCurrentUserRole(currentRole.value)) {
        try {
          const permissionsResponse = await request.get('/admin/permissions/roles/1/permissions')
          if (permissionsResponse.code === 200 && permissionsResponse.data) {
            localStorage.setItem('adminPermissions', JSON.stringify(permissionsResponse.data))
            logger.log('[PermissionManagement] 已更新当前用户权限:', permissionsResponse.data)
            ElMessage.info('权限已更新，页面即将刷新')
            setTimeout(() => {
              window.location.reload()
            }, 1500)
          }
        } catch (error) {
          logger.error('[PermissionManagement] 更新权限失败:', error)
          ElMessage.warning('权限已保存，但需要重新登录才能生效')
        }
      }
    } else {
      ElMessage.error(response.message || '权限分配失败')
    }
  } catch (error) {
    logger.error('[PermissionManagement] 保存权限失败:', error)
    logger.error('[PermissionManagement] 错误详情:', error.response?.data)
    ElMessage.error('权限分配失败: ' + (error.response?.data?.msg || error.message))
  } finally {
    saving.value = false
  }
}

const saveTeacherPermissions = async () => {
  if (!currentTeacherRole.value) return
  
  teacherSaving.value = true
  try {
    const finalSortOrder = menuSortOrder.value.length > 0 
      ? menuSortOrder.value 
      : sortedMenuList.value.map(m => m.id)
    
    const requestData = {
      permissionIds: selectedMenuIds.value,
      sortOrder: finalSortOrder.map((id, index) => ({ id, sortOrder: index + 1 }))
    }
    
    const response = await request.post(`/teacher/permissions/roles/${currentTeacherRole.value.id}/permissions`, requestData)
    
    if (response.code === 200) {
      const roleCode = currentTeacherRole.value.roleCode
      localStorage.removeItem(`teacherMenus_${roleCode}`)
      logger.log('[PermissionManagement] 已清除角色缓存:', `teacherMenus_${roleCode}`)
      
      ElMessage.success('教师菜单权限配置成功，该角色教师下次登录时生效')
      teacherPermissionDialogVisible.value = false
      await fetchTeacherPermissionCounts()
    } else {
      ElMessage.error(response.message || '教师菜单权限配置失败')
    }
  } catch (error) {
    logger.error('[PermissionManagement] 保存教师权限失败:', error)
    ElMessage.error('教师菜单权限配置失败')
  } finally {
    teacherSaving.value = false
  }
}

const fetchStudentMenus = async () => {
  try {
    interface SystemConfigRow {
      configValue?: string
      config_value?: string
      [key: string]: unknown
    }
    const response = await request.get<SystemConfigRow>('/admin/system-config/key/STUDENT_MENU_CONFIG')
    if (response.code === 200 && response.data && (response.data.configValue ?? response.data.config_value)) {
      const rawConfigValue = response.data.configValue ?? response.data.config_value ?? ''
      let configMenus: Array<{ path?: string; visible?: boolean }> = []
      try {
        const parsedMenus: unknown = JSON.parse(rawConfigValue)
        configMenus = Array.isArray(parsedMenus) ? parsedMenus : []
      } catch (e) {
        logger.error('解析学生菜单配置失败:', e)
      }
      
      if (Array.isArray(configMenus) && configMenus.length > 0) {
        // 合并配置与默认菜单（保留新的默认菜单项）
        const configMap = new Map(configMenus.map(m => [m.path, m]))
        const mergedMenus = []
        
        // 1. 添加配置中已有的菜单（保留配置的顺序）
        for (const configMenu of configMenus) {
          const defaultMenu = defaultStudentMenus.find(m => m.path === configMenu.path)
          if (defaultMenu) {
            mergedMenus.push({ ...defaultMenu, visible: configMenu.visible !== false })
          }
        }
        
        // 2. 添加默认菜单中有但配置中没有的菜单（新增菜单）
        for (const defaultMenu of defaultStudentMenus) {
          if (!configMap.has(defaultMenu.path)) {
            mergedMenus.push({ ...defaultMenu, visible: false })
          }
        }
        
        studentMenus.value = mergedMenus
      }
    }
  } catch (error) {
    logger.error('获取学生菜单配置失败:', error)
  }
}

const saveStudentMenus = async () => {
  studentSaving.value = true
  try {
    const configValue = JSON.stringify(studentMenus.value.map(m => ({
      path: m.path,
      visible: m.visible
    })))
    
    // 先检查是否存在该配置
    let exists = false
    try {
      const checkRes = await request.get('/admin/system-config/key/STUDENT_MENU_CONFIG')
      if (checkRes.code === 200 && checkRes.data) {
        exists = true
      }
    } catch (e) {
      exists = false
    }
    
    const requestData = {
      configKey: 'STUDENT_MENU_CONFIG',
      configValue: configValue,
      configType: 'JSON',
      configName: '学生端菜单配置',
      description: '灵活管控学生端菜单栏的显示内容和顺序',
      status: 1
    }
    
    let response
    if (exists) {
      response = await request.put('/admin/system-config/key/STUDENT_MENU_CONFIG', requestData)
    } else {
      response = await request.post('/admin/system-config', requestData)
    }
    
    if (response.code === 200) {
      ElMessage.success('学生菜单配置保存成功，学生端下次登录/刷新时生效')
    } else {
      ElMessage.error(response.message || '学生菜单配置保存失败')
    }
  } catch (error) {
    logger.error('保存学生菜单配置失败:', error)
    ElMessage.error('学生菜单配置保存失败')
  } finally {
    studentSaving.value = false
  }
}

const resetStudentMenus = () => {
  ElMessageBox.confirm('确定要恢复默认的学生菜单配置吗？这将会覆盖当前的所有设置。', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    studentMenus.value = JSON.parse(JSON.stringify(defaultStudentMenus))
  }).catch(() => {})
}

const fetchAiConfig = async () => {
  try {
    interface AiConfigRow {
      configValue?: string
      [key: string]: unknown
    }
    const response = await request.get<AiConfigRow>('/admin/system-config/key/AI_ASSISTANT_ENABLED')
    if (response.code === 200 && response.data) {
      aiAssistantEnabled.value = response.data.configValue === 'true'
    } else {
      aiAssistantEnabled.value = true // 默认为true
    }
  } catch (error) {
    logger.error('获取AI助手配置失败:', error)
    aiAssistantEnabled.value = true
  }
}

const saveAiConfig = async (val: boolean) => {
  try {
    const configValue = val ? 'true' : 'false'
    
    // 检查是否存在
    let exists = false
    try {
      const checkRes = await request.get('/admin/system-config/key/AI_ASSISTANT_ENABLED')
      if (checkRes.code === 200 && checkRes.data) {
        exists = true
      }
    } catch (e) {
      exists = false
    }
    
    const requestData = {
      configKey: 'AI_ASSISTANT_ENABLED',
      configValue: configValue,
      configType: 'BOOLEAN',
      configName: 'AI助手功能开关',
      description: '控制各角色端是否显示AI助手入口，关闭后各页面AI助手隐藏且不可使用',
      status: 1
    }

    let response
    if (exists) {
      response = await request.put('/admin/system-config/key/AI_ASSISTANT_ENABLED', requestData)
    } else {
      response = await request.post('/admin/system-config', requestData)
    }

    if (response.code === 200) {
      ElMessage.success('AI 助手功能配置已更新')
      // 开启或关闭后都刷新管理员页面，让入口及依赖总开关的状态立即重新初始化。
      window.setTimeout(() => window.location.reload(), 500)
    } else {
      ElMessage.error(response.message || '配置更新失败')
      aiAssistantEnabled.value = !val // 恢复原状
    }
  } catch (error) {
    logger.error('保存AI助手配置失败:', error)
    ElMessage.error('配置更新失败')
    aiAssistantEnabled.value = !val // 恢复原状
  }
}

const fetchAgentCaps = async () => {
  agentCapsLoading.value = true
  try {
    interface AgentCapsConfig {
      roleDefaults?: Record<string, string[]>
      autoTaskPolicy?: { roleDefaults?: Record<string, unknown> } & Record<string, unknown>
      userOverrides?: Record<string, Record<string, boolean>>
      configured?: boolean
      [key: string]: unknown
    }
    const response = await request.get<AgentCapsConfig>('/admin/agent-capabilities/config')
    if (response.code === 200 && response.data) {
      const parsed = response.data
      agentConfigBase.value = JSON.parse(JSON.stringify(parsed))
      const roleDefaults = parsed?.roleDefaults
      if (roleDefaults && typeof roleDefaults === 'object') {
        const next = buildDefaultAgentCaps()
        for (const role of agentRoleColumns) {
          const list = roleDefaults[role.key]
          if (Array.isArray(list)) {
            for (const row of agentCapRows) {
              if (!row.reserved) next[role.key][row.key] = list.includes(row.key)
            }
          }
        }
        agentCaps.value = next
        agentAutoPolicy.value = parsed.autoTaskPolicy?.roleDefaults?.teacher
          ? parsed.autoTaskPolicy as typeof agentAutoPolicy.value
          : buildDefaultAgentAutoPolicy()
        agentAutoPolicy.value.globalCooldownMinutes ??= 1440
        agentUserOverrides.value = parsed?.userOverrides && typeof parsed.userOverrides === 'object'
          ? parsed.userOverrides as Record<string, Record<string, boolean>>
          : {}
        agentCapsConfigured.value = parsed.configured === true
      } else {
        throw new Error('角色能力配置无效')
      }
      return true
    }
    throw new Error(response.message || '获取AI能力授权配置失败')
  } catch (error) {
    logger.error('获取AI能力授权配置失败:', error)
    ElMessage.error('读取权限配置失败，请刷新后重试')
    return false
  } finally {
    agentCapsLoading.value = false
  }
}

function agentConfigDraft() {
  const roleDefaults: Record<string, string[]> = {}
  for (const role of agentRoleColumns) roleDefaults[role.key] = agentCapRows.filter(row => !row.reserved && supportsAgentCapability(role.key, row.key) && agentCaps.value[role.key]?.[row.key]).map(row => row.key)
  return { roleDefaults, autoTaskPolicy: JSON.parse(JSON.stringify(agentAutoPolicy.value)) }
}
function agentConfigError(error: any) {
  if (error === 'cancel' || error === 'close') return
  ElMessage.error(error?.response?.status === 409 ? '配置已变化或预览已失效，请重新生成预览后确认。' : error?.response?.data?.message || error?.message || '配置更新失败')
}
async function confirmAgentPreview(preview: any): Promise<boolean> {
  if (preview.questions?.length) { ElMessage.warning(preview.questions.join('；')); return false }
  if (!preview.actionId) throw new Error('未取得有效变更预览，请重新核对。')
  const lines: string[] = []
  const previous = agentConfigBase.value || {}
  for (const role of agentRoleColumns) {
    const before = previous.roleDefaults?.[role.key] || [], after = preview.proposedRoleDefaults?.[role.key] || []
    for (const row of agentCapRows) if (before.includes(row.key) !== after.includes(row.key)) lines.push(`${role.label} · ${row.label}：${before.includes(row.key) ? '开启' : '关闭'} → ${after.includes(row.key) ? '开启' : '关闭'}`)
  }
  const oldPolicy = previous.autoTaskPolicy || {}, newPolicy = preview.proposedAutoTaskPolicy || {}
  for (const field of ['actions', 'channels', 'scopes', 'maxTargetsPerRun', 'audienceLimitMode', 'globalCooldownMinutes']) {
    const before = oldPolicy.roleDefaults?.teacher?.[field], after = newPolicy.roleDefaults?.teacher?.[field]
    if (JSON.stringify(before) !== JSON.stringify(after)) lines.push(`教师自动化${policyFieldLabel(field)}：${policyValue(before)} → ${policyValue(after)}`)
  }
  if (oldPolicy.globalCooldownMinutes !== newPolicy.globalCooldownMinutes) lines.push(`同事项提醒冷却：${oldPolicy.globalCooldownMinutes ?? 1440} → ${newPolicy.globalCooldownMinutes ?? 1440} 分钟`)
  for (const change of preview.userChanges || []) {
    const before = change.fromOverrides || change.before || {}, after = change.overrides || {}
    const fields = new Set([...Object.keys(before), ...Object.keys(after)])
    for (const key of fields) if (before[key] !== after[key]) lines.push(`${change.name || change.userId}（${change.account || change.roleKey || change.role}）· ${capabilityLabel(key)}：${before[key] == null ? '跟随角色默认' : before[key] ? '开启' : '关闭'} → ${after[key] == null ? '跟随角色默认' : after[key] ? '开启' : '关闭'}`)
    if (change.automationPolicy) {
      const previous = change.fromAutomationPolicy || {}
      for (const key of new Set([...Object.keys(previous), ...Object.keys(change.automationPolicy)])) if (JSON.stringify(previous[key]) !== JSON.stringify(change.automationPolicy[key])) lines.push(`${change.name || change.userId}（${change.account || change.roleKey || change.role}）· 自动跟进${policyFieldLabel(key)}：${previous[key] == null ? '跟随角色默认' : policyValue(previous[key])} → ${policyValue(change.automationPolicy[key])}`)
    }
  }
  lines.push(`影响：将暂停 ${preview.impact?.tasksAtRisk ?? 0} 个任务；已存在 ${preview.impact?.usersWithOverrides ?? 0} 个用户覆盖。`)
  for (const task of preview.impact?.tasks || []) lines.push(`${task.teacherName || '教师'} · ${task.name}：${task.reason}`)
  await ElMessageBox.confirm(h('div', { style: 'max-height:55vh;overflow:auto;white-space:pre-wrap;line-height:1.8' }, lines.map(line => h('p', line))), '核对权限变更', { confirmButtonText: '确认并保存', cancelButtonText: '返回修改', closeOnClickModal: false })
  const response: any = await request.put('/admin/agent-capabilities/config', {
    actionId: preview.actionId, confirmed: true, configurationVersion: preview.configurationVersion,
    roleDefaults: preview.proposedRoleDefaults, autoTaskPolicy: preview.proposedAutoTaskPolicy, userChanges: preview.userChanges || []
  })
  if (response.code !== 200) throw new Error(response.message || '保存未完成')
  return true
}
const saveAgentCaps = async () => {
  if (agentCapsSaving.value || agentCapsLoading.value) return
  agentCapsSaving.value = true
  try {
    const response: any = await request.post('/admin/agent-capabilities/config/preview', agentConfigDraft())
    if (!await confirmAgentPreview(response.data)) return
    if (!await fetchAgentCaps()) { ElMessage.warning('配置已保存，但重查失败，请刷新核验'); return }
    if (selectedOverrideUser.value) await loadUserOverride(selectedOverrideUser.value)
    ElMessage.success('AI 能力授权已更新并重新读取')
  } catch (error: any) { agentConfigError(error) }
  finally { agentCapsSaving.value = false }
}

// ===== 用户级覆盖 =====
const overrideRole = ref('teacher')
const overrideKeyword = ref('')
const overrideUsers = ref<Array<{ id: number; displayName: string; account: string }>>([])
const overrideLoading = ref(false)
const selectedOverrideUser = ref<{ id: number; displayName: string; account: string } | null>(null)
const overrideCaps = ref<Record<string, boolean>>({})
const overrideSaving = ref(false)
const overrideReading = ref(false)
const overrideReady = ref(false)

const roleLabel = (role: string) => ({ student: '学生', teacher: '教师', company: '企业', admin: '管理员' } as Record<string, string>)[role] || role
const capabilityLabel = (capability: string) => agentCapRows.find(row => row.key === capability)?.label || capability
const policyFieldLabel = (field: string) => ({
  actions: '动作',
  channels: '渠道',
  scopes: '数据范围',
  maxTargetsPerRun: '单次上限', audienceLimitMode: '人数限制方式', globalCooldownMinutes: '提醒间隔', ALL_AUTHORIZED: '完整负责范围', LIMITED: '限制总人数'
}[field] || field)
const policyValue = (value: unknown) => Array.isArray(value)
  ? (value.length ? value.map((item: any) => ({
    SEND_REMINDER: '发送提醒', STATION_REMINDER: '站内', EMAIL: '邮件', OWN_CLASSES: '本人班级', OWN_DIVISION: '本系', OWN_COLLEGE: '本学院'
  }[item] || item)).join('、') : '无')
  : String(value ?? '无')

let overrideSearchVersion = 0
const resetOverrideUser = () => {
  overrideSearchVersion++
  overrideUsers.value = []
  overrideLoading.value = false
  selectedOverrideUser.value = null
  overrideCaps.value = {}
}

const searchOverrideUsers = async () => {
  resetOverrideUser()
  const version = overrideSearchVersion
  const role = overrideRole.value
  overrideLoading.value = true
  try {
    const keyword = overrideKeyword.value.trim()
    const response: any = await request.get('/admin/agent-capabilities/users', { params: { role, keyword } })
    if (version !== overrideSearchVersion || role !== overrideRole.value) return
    const data = response?.data
    const rows: any[] = Array.isArray(data) ? data : (data?.rows || data?.list || [])
    overrideUsers.value = rows.map((row: any) => ({
      id: row.id,
      displayName: row.name || row.account || ('用户' + row.id),
      account: row.account || ''
    })).filter((row: any) => row.id != null)
  } catch (error) {
    if (version !== overrideSearchVersion) return
    logger.error('搜索用户失败:', error)
    ElMessage.error('搜索用户失败')
  } finally {
    if (version === overrideSearchVersion) overrideLoading.value = false
  }
}

const selectOverrideUser = async (user: { id: number; displayName: string; account: string }) => {
  if (overrideSaving.value) return
  await loadUserOverride(user)
}

const loadUserOverride = async (user: { id: number; displayName: string; account: string }) => {
  const role = overrideRole.value
  selectedOverrideUser.value = user
  overrideCaps.value = {}
  overrideReading.value = true
  overrideReady.value = false
  try {
    const response: any = await request.get('/admin/agent-capabilities/user-overrides', {
      params: { role, userId: user.id }
    })
    if (selectedOverrideUser.value?.id !== user.id || overrideRole.value !== role) return false
    if (response.code !== 200) throw new Error(response.message || '读取用户覆盖失败')
    const existing = response.data?.overrides || {}
    for (const row of agentCapRows) {
      if (!row.reserved) overrideCaps.value[row.key] = typeof existing[row.key] === 'boolean'
        ? existing[row.key] : Boolean(agentCaps.value[role]?.[row.key])
    }
    overrideReady.value = true
    return true
  } catch (error) {
    logger.error('读取用户覆盖失败:', error)
    ElMessage.error('读取用户覆盖失败，请重新选择用户后重试')
    return false
  } finally {
    if (selectedOverrideUser.value?.id === user.id && overrideRole.value === role) overrideReading.value = false
  }
}

async function submitUserOverride(overrides: Record<string, boolean>) {
  const user = selectedOverrideUser.value
  if (!user || !overrideReady.value || overrideSaving.value) return
  overrideSaving.value = true
  try {
    const base = agentConfigBase.value
    if (!base) throw new Error('请先重新读取配置后再试。')
    const response: any = await request.post('/admin/agent-capabilities/config/preview', {
      roleDefaults: base.roleDefaults, autoTaskPolicy: base.autoTaskPolicy,
      userChanges: [{ role: overrideRole.value, userId: user.id, overrides, replace: true }]
    })
    if (!await confirmAgentPreview(response.data)) return
    if (!await fetchAgentCaps() || !await loadUserOverride(user)) { ElMessage.warning('覆盖已保存，请重新选择用户核对。'); return }
    ElMessage.success('用户级能力变更已保存并重新核对')
  } catch (error: any) { agentConfigError(error) }
  finally { overrideSaving.value = false }
}
const saveUserOverride = () => submitUserOverride(Object.fromEntries(Object.entries(overrideCaps.value).filter(([key]) => supportsAgentCapability(overrideRole.value, key))))
const clearUserOverride = () => submitUserOverride({})

const resetAgentCaps = () => {
  ElMessageBox.confirm('确定恢复 AI 能力默认配置吗？将覆盖当前角色级设置。', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    agentCaps.value = buildDefaultAgentCaps()
    agentAutoPolicy.value = buildDefaultAgentAutoPolicy()
  }).catch(() => {})
}

onMounted(() => {
  fetchRoles()
  fetchPermissionTree()
  fetchTeacherRoles()
  fetchTeacherPermissionList()
  fetchStudentMenus()
  fetchAiConfig()
  fetchAgentCaps()
})
</script>

<style scoped>
.permission-management-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  border-radius: 16px;
  padding: 24px 32px;
  color: white;
  margin-bottom: 24px;
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.25);
  position: relative;
  overflow: hidden;
}

.page-header::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -50%;
  width: 100%;
  height: 100%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
  transform: rotate(30deg);
}

.header-content h1 {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
  color: white;
}

.header-content p {
  font-size: 14px;
  opacity: 0.95;
  font-weight: 500;
  margin: 0;
}

.header-illustration {
  position: relative;
  width: 100px;
  height: 100px;
}

.illustration-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  animation: float 6s ease-in-out infinite;
}

.circle-1 {
  width: 60px;
  height: 60px;
  top: 0;
  right: 0;
  animation-delay: 0s;
}

.circle-2 {
  width: 40px;
  height: 40px;
  bottom: 10px;
  right: 20px;
  animation-delay: 3s;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

.main-card {
  border-radius: 16px;
  border: none;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  overflow: hidden;
  min-height: 500px;
}

.permission-tabs :deep(.el-tabs__content) {
  padding: 24px;
}

.table-container {
  padding: 0;
}

.permission-table {
  border-radius: 8px;
}

.permission-table :deep(.el-table__header) th {
  background-color: #f8fbff;
  color: #409EFF;
  font-weight: 600;
}

.permission-table :deep(.el-table__row) td {
  border-bottom: 1px solid #f0f7ff;
}

.current-user-tag {
  border-radius: 12px;
}

.status-tag {
  border-radius: 12px;
  font-weight: 500;
}

.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
  align-items: center;
}

.table-btn {
  border-radius: 6px;
  padding: 6px 8px;
  transition: all 0.3s ease;
}

.table-btn:hover {
  transform: scale(1.1);
}

.table-btn.assign {
  color: #409EFF;
}

.permission-tree-container {
  padding: 0;
}

.tree-card {
  border-radius: 12px;
  border: 1px solid #e6f7ff;
  background: #fafcff;
}

.tree-header {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 2px solid #f0f7ff;
}

.tree-title {
  font-size: 18px;
  font-weight: 600;
  color: #409EFF;
  margin: 0 0 8px 0;
}

.tree-description {
  font-size: 13px;
  color: #909399;
  margin: 0;
}

.permission-tree {
  padding: 10px 0;
}

.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  padding-right: 8px;
  padding: 8px 0;
}

.module-label {
  font-weight: bold;
  font-size: 16px;
  color: #409EFF;
  display: flex;
  align-items: center;
  gap: 8px;
}

.module-icon {
  font-size: 18px;
}

.permission-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.permission-tag {
  border-radius: 10px;
  min-width: 80px;
  justify-content: center;
}

.permission-dialog {
  border-radius: 16px;
  overflow: hidden;
}

.permission-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #f8fbff 0%, #e6f7ff 100%);
  margin: 0;
  padding: 20px;
  border-bottom: 1px solid #e6f7ff;
}

.assignment-header {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 2px solid #f0f7ff;
}

.assignment-title {
  font-size: 18px;
  font-weight: 600;
  color: #409EFF;
  margin: 0 0 8px 0;
}

.assignment-description {
  font-size: 13px;
  color: #909399;
  margin: 0;
}

.permission-assignment {
  padding: 10px 0;
}

.assignment-tree {
  padding: 10px 0;
}

/* 禁用节点的样式 */
:deep(.el-tree-node.is-disabled > .el-tree-node__content) {
  opacity: 0.7;
}

:deep(.el-tree-node.is-disabled > .el-tree-node__content .el-checkbox__input.is-disabled) {
  opacity: 0.5;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.cancel-btn, .confirm-btn {
  border-radius: 8px;
  padding: 10px 20px;
  transition: all 0.3s ease;
}

.confirm-btn {
  background: linear-gradient(135deg, #409EFF, #52c41a);
  border: none;
}

.confirm-btn:hover {
  background: linear-gradient(135deg, #66b1ff, #73d13d);
  transform: translateY(-1px);
}

.teacher-permission-header {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 2px solid #f0f7ff;
}

.menu-list {
  padding: 10px 0;
}

.menu-item {
  padding: 12px 16px;
  margin: 8px 0;
  background: #f8fbff;
  border-radius: 8px;
  border: 1px solid #e6f7ff;
  transition: all 0.3s ease;
}

.menu-item:hover {
  background: #e6f7ff;
  border-color: #409EFF;
}

.menu-list-draggable {
  padding: 10px 0;
}

.menu-item-draggable {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  margin: 8px 0;
  background: #f8fbff;
  border-radius: 8px;
  border: 1px solid #e6f7ff;
  transition: all 0.3s ease;
  cursor: grab;
}

.menu-item-draggable:hover {
  background: #e6f7ff;
  border-color: #409EFF;
}

.menu-item-draggable.selected {
  background: #f0f9eb;
  border-color: #67c23a;
}

.menu-item-draggable.dragging {
  opacity: 0.5;
  background: #e6f7ff;
  border: 2px dashed #409EFF;
}

.menu-drag-handle {
  display: flex;
  align-items: center;
  margin-right: 12px;
  color: #c0c4cc;
  cursor: grab;
}

.menu-drag-handle:hover {
  color: #409EFF;
}

.drag-icon {
  font-size: 18px;
}

.menu-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.menu-icon {
  font-size: 18px;
  color: #409EFF;
}

.menu-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.menu-path {
  margin-left: auto;
  font-size: 12px;
}

@media screen and (max-width: 768px) {
  .permission-management-container {
    padding: 15px;
  }
  
  .page-header {
    flex-direction: column;
    text-align: center;
    padding: 20px;
  }
  
  .header-illustration {
    margin-top: 15px;
  }
  
  .action-buttons {
    flex-wrap: wrap;
    justify-content: center;
  }
  
  .permission-tabs :deep(.el-tabs__content) {
    padding: 16px;
  }
}

.disabled-node {
  opacity: 0.5;
  cursor: not-allowed;
}

.disabled-node .module-label,
.disabled-node .permission-label {
  color: #999;
}

.disabled-node .el-tag {
  opacity: 0.6;
}
.user-override-box { margin-top: 4px; }
.user-override-toolbar { display: flex; gap: 10px; margin-bottom: 10px; }
.user-override-editor { margin-top: 12px; padding: 12px; border: 1px solid #ebeef5; border-radius: 6px; background: #fafafa; }
.user-override-title { font-weight: 600; display: flex; align-items: center; gap: 10px; }
.user-override-hint { font-weight: 400; color: #909399; font-size: 12px; }
.user-override-check { margin-right: 14px; margin-top: 10px; }
.user-override-actions { margin-top: 12px; display: flex; gap: 12px; }
</style>
