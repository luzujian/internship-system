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

        <el-tab-pane label="权限列表" name="permissions">
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
            :data="filteredPermissionTree"
            :props="{ children: 'children', label: 'module' }"
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
import { ref, onMounted, computed, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ElScrollbar } from 'element-plus'
import { Key, User, DocumentChecked, DataLine, Setting, MagicStick, Lock, Briefcase, ChatDotRound, Folder, ChatLineRound, Document, School, Collection, Download, HomeFilled, Monitor, Edit, Bell, OfficeBuilding, Upload, Clock, Rank } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useAuthStore } from '@/store/auth'

defineOptions({
  components: {
    ElScrollbar
  }
})

const authStore = useAuthStore()

const activeTab = ref('roles')
const loading = ref(false)
const saving = ref(false)
const roles = ref([])
const permissionTree = ref([])
const permissionDialogVisible = ref(false)
const permissionTreeRef = ref(null)
const currentRole = ref(null)

const teacherLoading = ref(false)
const teacherSaving = ref(false)
const teacherRoles = ref([])
const teacherPermissionList = ref([])
const teacherPermissionDialogVisible = ref(false)
const currentTeacherRole = ref(null)
const selectedMenuIds = ref<number[]>([])
const teacherPermissionCounts = ref<Record<number, number>>({})

const draggedIndex = ref<number | null>(null)
const menuSortOrder = ref<number[]>([])

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
    return nodes.map(node => {
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

const fetchRoles = async () => {
  loading.value = true
  try {
    const response = await request.get('/admin/permissions/roles')
    if (response.code === 200) {
      roles.value = response.data.filter(role => role.roleCode === 'ROLE_ADMIN')
    } else {
      ElMessage.error(response.message || '获取角色列表失败')
    }
  } catch (error) {
    ElMessage.error('获取角色列表失败')
  } finally {
    loading.value = false
  }
}

const fetchPermissionTree = async () => {
  try {
    const response = await request.get('/admin/permissions/permission-tree')
    logger.log('[PermissionManagement] 权限树接口响应:', response)
    if (response.code === 200) {
      permissionTree.value = response.data
      logger.log('[PermissionManagement] 获取到的权限树数据:', permissionTree.value)
      logger.log('[PermissionManagement] 权限树主模块:', permissionTree.value.map(m => m.module))
    } else {
      ElMessage.error(response.message || '获取权限树失败')
    }
  } catch (error) {
    logger.error('[PermissionManagement] 获取权限树失败:', error)
    ElMessage.error('获取权限树失败')
  }
}

const fetchTeacherRoles = async () => {
  teacherLoading.value = true
  try {
    const response = await request.get('/teacher/permissions/roles')
    if (response.code === 200) {
      teacherRoles.value = response.data
      await fetchTeacherPermissionCounts()
    } else {
      ElMessage.error(response.message || '获取教师角色列表失败')
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
      const response = await request.get(`/teacher/permissions/role/${role.id}`)
      if (response.code === 200) {
        teacherPermissionCounts.value[role.id] = response.data.length
      }
    } catch (error) {
      logger.error(`获取角色 ${role.id} 权限数量失败:`, error)
    }
  }
}

const fetchTeacherPermissionList = async () => {
  try {
    const response = await request.get('/teacher/permissions/tree')
    if (response.code === 200) {
      teacherPermissionList.value = response.data
    } else {
      ElMessage.error(response.message || '获取教师权限列表失败')
    }
  } catch (error) {
    ElMessage.error('获取教师权限列表失败')
  }
}

const handleAssignPermissions = async (row) => {
  currentRole.value = row
  permissionDialogVisible.value = true
  
  await nextTick()
  
  try {
    const response = await request.get(`/admin/permissions/roles/${row.id}/permissions`)
    if (response.code === 200) {
      let checkedKeys = response.data.map(p => p.id)
      
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
    const response = await request.get(`/teacher/permissions/role/${row.id}`)
    if (response.code === 200) {
      selectedMenuIds.value = response.data.map(p => p.id)
      const allMenuIds = teacherPermissionList.value.map(m => m.id)
      const selectedIds = response.data.map(p => p.id)
      const unselectedIds = allMenuIds.filter(id => !selectedIds.includes(id))
      const sortedSelected = response.data.sort((a, b) => (a.sortOrder || 999) - (b.sortOrder || 999))
      menuSortOrder.value = [...sortedSelected.map(p => p.id), ...unselectedIds]
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
    
    const allKeys = [...checkedKeys, ...halfCheckedKeys]
    
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

onMounted(() => {
  fetchRoles()
  fetchPermissionTree()
  fetchTeacherRoles()
  fetchTeacherPermissionList()
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
</style>
