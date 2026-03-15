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
        <el-tab-pane label="角色管理" name="roles">
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
  </div>
</template>

<script lang="ts" setup>
import logger from '@/utils/logger'
import { ref, onMounted, computed, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ElScrollbar } from 'element-plus'
import { Key, User, DocumentChecked, DataLine, Setting, MagicStick, Lock, Briefcase, ChatDotRound, Folder, ChatLineRound, Document, School, Collection, Download } from '@element-plus/icons-vue'
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

const isCurrentUserRole = (role) => {
  return authStore.role === 'ROLE_ADMIN' && role.roleCode === 'ROLE_ADMIN'
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

onMounted(() => {
  fetchRoles()
  fetchPermissionTree()
})
</script>

<style scoped>
.permission-management-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

/* 页面标题区域 */
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

/* 主卡片样式 */
.main-card {
  border-radius: 16px;
  border: none;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  overflow: hidden;
  min-height: 500px;
}

/* 标签页样式 */
.permission-tabs :deep(.el-tabs__content) {
  padding: 24px;
}

/* 表格样式 */
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

/* 操作按钮 */
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

/* 权限树样式 */
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

/* 分配权限对话框 */
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

/* 对话框按钮 */
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

/* 响应式设计 */
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

/* 禁用节点样式 */
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