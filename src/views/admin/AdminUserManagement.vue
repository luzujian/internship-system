<template>
  <div class="user-management-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>管理员用户管理</span>
          <el-button v-if="authStore.hasPermission('user:admin:add')" type="primary" @click="showAddDialog" style="margin-right: 10px">添加管理员</el-button>
          <el-button v-if="authStore.hasPermission('user:admin:view')" type="success" @click="exportToExcel">
            <Download />
            导出Excel
          </el-button>
        </div>
      </template>
      
      <div class="search-bar">
        <el-input
          v-model="searchQuery"
          placeholder="请输入用户名或手机号"
          style="width: 300px; margin-right: 10px"
          @keyup.enter="searchUsers"
        />
        <el-button v-if="authStore.hasPermission('user:admin:view')" type="primary" @click="searchUsers" style="margin-left: 10px">搜索</el-button>
      </div>
      
      <el-table :data="users" style="width: 100%">
        <el-table-column prop="id" label="用户ID" width="80" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column prop="updateTime" label="更新时间" width="180" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="scope">
            <!-- 超级管理员不可编辑和删除 -->
            <el-button v-if="authStore.hasPermission('user:admin:edit')" type="primary" size="small" @click="showEditDialog(scope.row)" :disabled="isSuperAdmin(scope.row)">编辑</el-button>
            <el-button v-if="authStore.hasPermission('user:admin:reset')" type="warning" size="small" @click="showResetPasswordDialog(scope.row)" :disabled="isSuperAdmin(scope.row)">重置密码</el-button>
            <el-button v-if="authStore.hasPermission('user:admin:delete')" type="danger" size="small" @click="deleteUser(scope.row.id)" :disabled="isSuperAdmin(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <pagination 
        :total="total" 
        :current-page="currentPage" 
        :page-size="pageSize"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </el-card>
    
    <!-- 添加管理员对话框 -->
    <el-dialog v-model="addDialogVisible" title="添加管理员" width="500px">
      <el-form ref="addForm" :model="addForm" :rules="addRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="addForm.username" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="addForm.phone" placeholder="请输入11位手机号码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="addUser">确定</el-button>
      </template>
    </el-dialog>

    <!-- 编辑管理员对话框 -->
    <el-dialog v-model="editDialogVisible" title="编辑管理员" width="500px">
      <el-form ref="editForm" :model="editForm" :rules="addRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="editForm.phone" placeholder="请输入11位手机号码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="updateUser">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码对话框 -->
    <el-dialog v-model="resetPasswordDialogVisible" title="重置密码" width="400px">
      <el-form ref="resetPasswordForm" :model="resetPasswordForm" :rules="resetPasswordForm" label-width="80px">
        <!-- 隐藏密码输入框，默认密码为123456 -->
        <div style="padding: 20px; text-align: center;">
          <p>密码将被重置为: <span style="color: #409eff; font-weight: bold;">123456</span></p>
          <p style="color: #909399; font-size: 12px; margin-top: 10px;">请确认是否继续重置密码</p>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="resetPasswordDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="resetUserPassword">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { createRequiredValidator, createPhoneValidator } from '@/utils/validation'
import { ref, onMounted, inject } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import Pagination from '../../components/Pagination.vue'
import AdminUserService from '../../api/AdminUserService'
import AdminService from '../../api/adminService'
import * as XLSX from 'xlsx'
import { useSystemSettingsStore } from '../../store/systemSettings'
import { useAuthStore } from '../../store/auth'

const authStore = useAuthStore()
const systemSettingsStore = useSystemSettingsStore()

// 获取全局预加载服务
const preloadService = inject('preloadService', null)

const users = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const searchQuery = ref('')

const addDialogVisible = ref(false)
const editDialogVisible = ref(false)
const resetPasswordDialogVisible = ref(false)

const addForm = ref({
  username: '',
  phone: '',
  role: 'ROLE_ADMIN'
})

const editForm = ref({})
const resetPasswordForm = ref({})
const selectedUserId = ref(null)

const addRules = ref({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  phone: createPhoneValidator('请输入 11 位中国大陆手机号')
})

// 判断是否为超级管理员
const isSuperAdmin = (user) => {
  return user.username === 'admin'
}

// 加载管理员用户列表
const loadAdmins = () => {
  AdminUserService.getAdmins(currentPage.value, pageSize.value, searchQuery.value)
    .then(response => {
      // 提取业务数据对象（从完整响应对象中提取Result对象）
      const result = response.data || response
      // 后端返回格式是 {code: 200, data: {total: xxx, rows: [...]}}
      users.value = result.data?.rows || []
      total.value = result.data?.total || 0
    })
    .catch(error => {
      ElMessage.error('加载管理员用户列表失败: ' + (error.message || '未知错误'))
    })
}

// 搜索管理员用户
const searchUsers = () => {
  currentPage.value = 1
  loadAdmins()
}

// 显示添加管理员对话框
const showAddDialog = () => {
  addForm.value = {
    username: '',
    phone: '',
    role: 'ROLE_ADMIN'
  }
  addDialogVisible.value = true
}

// 添加管理员
const addUser = () => {
  AdminUserService.addAdmin(addForm.value)
    .then(() => {
      ElMessage.success('添加管理员用户成功')
      addDialogVisible.value = false
      loadAdmins()
    })
    .catch(error => {
      ElMessage.error('添加管理员用户失败: ' + (error.message || '未知错误'))
    })
}

// 显示编辑管理员对话框
const showEditDialog = (user) => {
  if (isSuperAdmin(user)) {
    ElMessage.warning('超级管理员信息不可编辑')
    return
  }
  editForm.value = { ...user }
  editDialogVisible.value = true
}

// 显示重置密码对话框
const showResetPasswordDialog = (user) => {
  if (isSuperAdmin(user)) {
    ElMessage.warning('超级管理员密码不可重置')
    return
  }
  selectedUserId.value = user.id
  resetPasswordDialogVisible.value = true
}

// 重置管理员密码
const resetUserPassword = async () => {
  try {
    const response = await AdminUserService.resetAdminPassword(selectedUserId.value, '123456')
    const result = response.data || response
    
    // 检查响应格式
    if (result.code === 200 || result.code === undefined || result.success) {
      ElMessage.success('密码重置成功，新密码为123456')
      resetPasswordDialogVisible.value = false
    } else {
      ElMessage.error('重置密码失败: ' + (result.msg || result.message || '未知错误'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      logger.error('重置管理员密码失败:', error)
      ElMessage.error('重置密码失败: ' + (error.response?.data?.msg || error.response?.data?.message || error.message || '未知错误'))
    }
  }
}

// 删除管理员
const deleteUser = (id) => {
  const user = users.value.find(u => u.id === id)
  if (user && isSuperAdmin(user)) {
    ElMessage.warning('超级管理员不可删除')
    return
  }

  ElMessageBox.confirm(
    '确定要删除该管理员吗？此操作将删除该管理员及其所有相关数据，此操作不可撤销！',
    '确认删除',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
      center: true
    }
  )
  .then(async () => {
    try {
      await AdminUserService.deleteAdmin(id)
      ElMessage.success('删除管理员用户成功')
      loadAdmins()
    } catch (error) {
      ElMessage.error('删除管理员用户失败: ' + (error.response?.data?.msg || error.response?.data?.message || error.message || '未知错误'))
      throw error
    }
  })
  .catch(error => {
    if (error !== 'cancel') {
      logger.error('删除管理员失败:', error)
    }
  })
}

// 更新管理员信息
const updateUser = () => {
  AdminUserService.updateAdmin(editForm.value)
    .then(() => {
      ElMessage.success('更新管理员信息成功')
      editDialogVisible.value = false
      loadAdmins()
    })
    .catch(error => {
      ElMessage.error('更新管理员信息失败: ' + (error.message || '未知错误'))
    })
}

// 分页处理
const handleSizeChange = (size) => {
  pageSize.value = size
  loadAdmins()
}

const handleCurrentChange = (current) => {
  currentPage.value = current
  loadAdmins()
}

// 导出管理员数据到Excel
const exportToExcel = () => {
  if (users.value.length === 0) {
    ElMessage.warning('没有数据可导出')
    return
  }

  try {
    // 准备导出数据
    const exportData = users.value.map(user => ({
      '用户ID': user.id,
      '用户名': user.username,
      '手机号': user.phone,
      '角色': user.username === 'admin' ? '超级管理员' : '普通管理员',
      '创建时间': formatDateForExcel(user.createTime),
      '更新时间': formatDateForExcel(user.updateTime)
    }))

    // 创建工作簿和工作表
    const wb = XLSX.utils.book_new()
    const ws = XLSX.utils.json_to_sheet(exportData)

    // 设置列宽
    const columnWidths = [
      { wch: 10 }, // 用户ID
      { wch: 20 }, // 用户名
      { wch: 15 }, // 手机号
      { wch: 12 }, // 角色
      { wch: 25 }, // 创建时间
      { wch: 25 }  // 更新时间
    ]
    ws['!cols'] = columnWidths

    // 将工作表添加到工作簿
    XLSX.utils.book_append_sheet(wb, ws, '管理员数据')

    // 生成文件名
    const fileName = `管理员数据_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}.xlsx`

    // 导出文件
    XLSX.writeFile(wb, fileName)
    ElMessage.success('导出成功')
  } catch (error) {
    logger.error('导出Excel失败:', error)
    ElMessage.error('导出失败: ' + (error.message || '未知错误'))
  }
}

// 为Excel导出格式化日期
const formatDateForExcel = (cellValue) => {
  if (!cellValue) {
    return ''
  }
  const d = new Date(cellValue)
  if (isNaN(d.getTime())) return ''

  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')

  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

// 初始加载
onMounted(() => {
  // 检查是否有预加载服务
  if (preloadService) {
    // 尝试获取预加载的数据
    const preloadedAdmins = preloadService.get('admin_users')
    
    // 如果有预加载的数据，直接使用
    if (preloadedAdmins && preloadedAdmins.rows && Array.isArray(preloadedAdmins.rows)) {
      users.value = preloadedAdmins.rows
      total.value = preloadedAdmins.total || 0
    } else {
      // 没有预加载数据时，从API获取
      loadAdmins()
    }
    
    // 注册预加载完成回调
    preloadService.onComplete(() => {
      const updatedAdmins = preloadService.get('admin_users')
      if (updatedAdmins && updatedAdmins.rows && Array.isArray(updatedAdmins.rows)) {
        users.value = updatedAdmins.rows
        total.value = updatedAdmins.total || 0
      }
    })
  } else {
    // 没有预加载服务时，直接从API获取数据
    loadAdmins()
  }
})
</script>

<style scoped>
.user-management-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-bar {
  margin-bottom: 20px;
  display: flex;
  align-items: center;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>