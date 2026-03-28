<template>
  <div class="major-management-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>专业管理</span>
          <div style="display: flex; gap: 10px;">
            <el-button v-if="authStore.hasPermission('major:add')" type="primary" @click="showAddDialog">添加专业</el-button>
            <el-button v-if="authStore.hasPermission('major:view')" type="success" @click="handleExportToExcel">
              <el-icon><Download /></el-icon> 导出 Excel
            </el-button>
          </div>
        </div>
      </template>

      <div class="search-bar">
        <el-input
          v-model="searchQuery"
          placeholder="请输入专业名称"
          style="width: 300px"
          @keyup.enter="searchMajors"
        />
        <el-button v-if="authStore.hasPermission('major:view')" type="primary" @click="searchMajors" style="margin-left: 10px">搜索</el-button>
      </div>

      <el-table :data="majors" style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="专业 ID" width="80" />
        <el-table-column prop="name" label="专业名称" />
        <el-table-column prop="code" label="专业代码" />
        <el-table-column prop="description" label="专业描述" width="300" />
        <el-table-column prop="status" label="状态">
          <template #default="scope">
            <el-tag v-if="scope.row.status === 'ACTIVE'" type="success">激活</el-tag>
            <el-tag v-else type="danger">禁用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="teacherCount" label="教师人数" width="100" />
        <el-table-column prop="studentCount" label="学生人数" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <el-button v-if="authStore.hasPermission('major:edit')" type="primary" size="small" @click="showEditDialog(scope.row)">编辑</el-button>
            <el-button v-if="authStore.hasPermission('major:delete')" type="danger" size="small" @click="deleteMajor(scope.row.id)">删除</el-button>
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

    <!-- 添加专业对话框 -->
    <el-dialog v-model="addDialogVisible" title="添加专业" width="500px">
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="100px">
        <el-form-item label="专业名称" prop="name">
          <el-input v-model="addForm.name" />
        </el-form-item>
        <el-form-item label="专业代码" prop="code">
          <el-input v-model="addForm.code" />
        </el-form-item>
        <el-form-item label="专业描述" prop="description">
          <el-input v-model="addForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="专业状态" prop="status">
          <el-select v-model="addForm.status">
            <el-option label="激活" value="ACTIVE" />
            <el-option label="禁用" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="addMajor">确定</el-button>
      </template>
    </el-dialog>

    <!-- 编辑专业对话框 -->
    <el-dialog v-model="editDialogVisible" title="编辑专业" width="500px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px">
        <el-form-item label="专业名称" prop="name">
          <el-input v-model="editForm.name" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="updateMajor">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { ref, onMounted, inject } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { exportToExcel } from '../../utils/xlsx'
import Pagination from '../../components/Pagination.vue'
import MajorService from '../../api/major'
import { useAuthStore } from '../../store/auth'
import type { Major } from '@/types/admin'

interface PreloadService {
  get: (key: string) => unknown
  onComplete: (callback: () => void) => void
}

interface MajorWithCounts extends Major {
  teacherCount?: number
  studentCount?: number
}

interface AddMajorForm {
  name: string
  code: string
  description: string
  status: string
}

const authStore = useAuthStore()

const preloadService = inject<PreloadService | null>('preloadService', null)

const majors = ref<MajorWithCounts[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const searchQuery = ref('')
const loading = ref(false)

const addDialogVisible = ref(false)
const editDialogVisible = ref(false)

const addForm = ref<AddMajorForm>({
  name: '',
  code: '',
  description: '',
  status: 'ACTIVE'
})

const editForm = ref<MajorWithCounts>({} as MajorWithCounts)

const addRules = ref<FormRules>({
  name: [
    { required: true, message: '请输入专业名称', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入专业代码', trigger: 'blur' }
  ]
})

const editRules = ref<FormRules>({
  name: [
    { required: true, message: '请输入专业名称', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入专业代码', trigger: 'blur' }
  ]
})

const addFormRef = ref<FormInstance | null>(null)
const editFormRef = ref<FormInstance | null>(null)

// 加载专业列表
const loadMajors = (): void => {
  loading.value = true
  MajorService.getMajors()
    .then(response => {
      const result = response.data || response
      if (result && result.code === 200 && result.data) {
        majors.value = Array.isArray(result.data) ? result.data : []
        total.value = result.total || majors.value.length
      } else {
        majors.value = []
        total.value = 0
      }
    })
    .catch((error: unknown) => {
      const errorMessage = error instanceof Error ? error.message : '未知错误'
      ElMessage.error('加载专业列表失败：' + errorMessage)
      majors.value = []
      total.value = 0
    })
    .finally(() => {
      loading.value = false
    })
}

// 搜索专业
const searchMajors = (): void => {
  MajorService.getMajors()
    .then(response => {
      let allMajors: MajorWithCounts[] = []
      // 提取业务数据对象（从完整响应对象中提取 Result 对象）
      const result = response.data || response

      // api.js 响应拦截器返回的是 Result 对象{code, message, data}
      if (result && result.code === 200 && result.data) {
        allMajors = Array.isArray(result.data) ? result.data : []
      }

      if (searchQuery.value) {
        const keyword = searchQuery.value.toLowerCase()
        allMajors = allMajors.filter(major =>
          major.name.toLowerCase().includes(keyword) ||
          major.code.toLowerCase().includes(keyword)
        )
      }

      majors.value = allMajors
      total.value = majors.value.length
    })
    .catch((error: unknown) => {
      const errorMessage = error instanceof Error ? error.message : '未知错误'
      ElMessage.error('搜索专业失败：' + errorMessage)
      // 出错时也设置为空数组，避免页面显示错误
      majors.value = []
      total.value = 0
    })
}

// 显示添加专业对话框
const showAddDialog = (): void => {
  addForm.value = {
    name: '',
    code: '',
    description: '',
    status: 'ACTIVE'
  }
  addDialogVisible.value = true
}

// 添加专业
const addMajor = (): void => {
  if (!addFormRef.value) return

  addFormRef.value.validate((valid: boolean) => {
    if (!valid) return

    MajorService.addMajor(addForm.value)
      .then(() => {
        ElMessage.success('添加专业成功')
        addDialogVisible.value = false
        loadMajors()
      })
      .catch((error: unknown) => {
        const errorMessage = error instanceof Error ? error.message : '未知错误'
        ElMessage.error('添加专业失败：' + errorMessage)
      })
  })
}

// 显示编辑专业对话框
const showEditDialog = (major: MajorWithCounts): void => {
  editForm.value = { ...major }
  editDialogVisible.value = true
}

// 更新专业
const updateMajor = (): void => {
  if (!editFormRef.value) return

  editFormRef.value.validate((valid: boolean) => {
    if (!valid) return

    MajorService.updateMajor(editForm.value.id, editForm.value)
      .then(() => {
        ElMessage.success('更新专业成功')
        editDialogVisible.value = false
        loadMajors()
      })
      .catch((error: unknown) => {
        const errorMessage = error instanceof Error ? error.message : '未知错误'
        ElMessage.error('更新专业失败：' + errorMessage)
      })
  })
}

// 删除专业
const deleteMajor = async (id: string): Promise<void> => {
  try {
    await ElMessageBox.confirm(
      '确定要删除该专业吗？此操作将删除该专业及其所有相关数据（包括班级、学生等），此操作不可撤销！',
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
        center: true
      }
    )

    await MajorService.deleteMajor(id)

    ElMessage.success('删除专业成功')
    loadMajors()
  } catch (error) {
    if (error !== 'cancel') {
      logger.error('删除专业失败:', error)
      const errorMessage = error instanceof Error ? error.message : '未知错误'
      ElMessage.error('删除失败：' + errorMessage)
    }
  }
}

// 分页处理
const handleSizeChange = (size: number): void => {
  pageSize.value = size
  loadMajors()
}

const handleCurrentChange = (current: number): void => {
  currentPage.value = current
  loadMajors()
}

// 导出专业数据到 Excel
const handleExportToExcel = async (): Promise<void> => {
  if (!majors.value || majors.value.length === 0) {
    ElMessage.warning('没有数据可导出')
    return
  }

  try {
    const formatDate = (dateString: string | undefined): string => {
      if (!dateString) return ''
      const d = new Date(dateString)
      if (isNaN(d.getTime())) return ''
      const year = d.getFullYear()
      const month = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      const hours = String(d.getHours()).padStart(2, '0')
      const minutes = String(d.getMinutes()).padStart(2, '0')
      const seconds = String(d.getSeconds()).padStart(2, '0')
      return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
    }

    const exportData = majors.value.map(major => ({
      '专业 ID': major.id,
      '专业名称': major.name,
      '专业代码': major.code,
      '专业描述': major.description || '',
      '状态': major.status === 'ACTIVE' ? '激活' : '禁用',
      '创建时间': formatDate(major.createTime),
      '更新时间': formatDate(major.updateTime)
    }))

    const columnWidths = [
      { wch: 10 },
      { wch: 20 },
      { wch: 15 },
      { wch: 30 },
      { wch: 10 },
      { wch: 25 },
      { wch: 25 }
    ]

    const fileName = `专业数据_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}`

    await exportToExcel(exportData, fileName, { sheetName: '专业数据', columnWidths })
    ElMessage.success('导出成功')
  } catch (error: unknown) {
    logger.error('导出 Excel 失败:', error)
    const errorMessage = error instanceof Error ? error.message : '未知错误'
    ElMessage.error('导出失败：' + errorMessage)
  }
}

// 初始加载
onMounted(() => {
  // 检查是否有预加载服务
  if (preloadService) {
    // 尝试获取预加载的数据
    const preloadedMajors = preloadService.get('majors')

    // 处理预加载数据（考虑不同的数据结构）
    if (preloadedMajors) {
      // 如果是标准的 API 返回格式 {data: [...], total: ...}
      if (preloadedMajors.data && Array.isArray(preloadedMajors.data)) {
        majors.value = preloadedMajors.data as MajorWithCounts[]
        total.value = preloadedMajors.total || preloadedMajors.data.length
      }
      // 如果直接是数组格式
      else if (Array.isArray(preloadedMajors)) {
        majors.value = preloadedMajors as MajorWithCounts[]
        total.value = preloadedMajors.length
      }
    }

    // 如果没有有效的预加载数据，从 API 获取
    if (!majors.value || majors.value.length === 0) {
      loadMajors()
    }

    // 注册预加载完成回调
    preloadService.onComplete(() => {
      const updatedMajors = preloadService.get('majors')
      if (updatedMajors) {
        if (updatedMajors.data && Array.isArray(updatedMajors.data)) {
          majors.value = updatedMajors.data as MajorWithCounts[]
          total.value = updatedMajors.total || updatedMajors.data.length
        } else if (Array.isArray(updatedMajors)) {
          majors.value = updatedMajors as MajorWithCounts[]
          total.value = updatedMajors.length
        }
      }
    })
  } else {
    // 没有预加载服务时，直接从 API 获取数据
    loadMajors()
  }
})
</script>

<style scoped>
.major-management-container {
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
