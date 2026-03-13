<template>
  <div class="position-management-container">
    <el-card class="header-card" shadow="never">
      <div class="header-content">
        <h2 class="page-title">岗位管理</h2>
        <el-button type="primary" @click="showCreateDialog">
          <el-icon><Plus /></el-icon>&nbsp;发布岗位
        </el-button>
      </div>
    </el-card>

    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" class="search-form" @keyup.enter="searchPositions">
        <el-form-item label="岗位名称">
          <el-input
            v-model="searchForm.positionName"
            placeholder="请输入岗位名称"
            clearable
            @keyup.enter="searchPositions"
          />
        </el-form-item>
        <el-form-item label="岗位类别">
          <el-select v-model="searchForm.categoryId" placeholder="请选择岗位类别" clearable style="width: 200px">
            <el-option
              v-for="category in categories"
              :key="category.id"
              :label="category.name"
              :value="category.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="searchPositions">
            <el-icon><Search /></el-icon>&nbsp;搜索
          </el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="positions" v-loading="loading" style="width: 100%">
        <el-table-column prop="positionName" label="岗位名称" />
        <el-table-column prop="plannedRecruit" label="计划招聘人数" width="120" />
        <el-table-column prop="recruitedCount" label="已招人数" width="100" />
        <el-table-column prop="remainingQuota" label="剩余名额" width="100" />
        <el-table-column prop="publishTime" label="发布时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.publishTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="viewPosition(row)">查看</el-button>
            <el-button type="warning" link size="small" @click="editPosition(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="deletePosition(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="resetForm"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="岗位类别" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择岗位类别" style="width: 100%">
            <el-option
              v-for="category in categories"
              :key="category.id"
              :label="category.name"
              :value="category.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位名称" prop="positionName">
          <el-input v-model="form.positionName" placeholder="请输入岗位名称" />
        </el-form-item>
        <el-form-item label="计划招聘人数" prop="plannedRecruit">
          <el-input-number v-model="form.plannedRecruit" :min="1" :max="1000" />
        </el-form-item>
        <el-form-item label="岗位描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            placeholder="请输入岗位描述"
          />
        </el-form-item>
        <el-form-item label="岗位要求" prop="requirements">
          <el-input
            v-model="form.requirements"
            type="textarea"
            :rows="4"
            placeholder="请输入岗位要求"
          />
        </el-form-item>
        <el-form-item label="薪资待遇" prop="salary">
          <el-input v-model="form.salary" placeholder="请输入薪资待遇" />
        </el-form-item>
        <el-form-item label="工作地点" prop="workLocation">
          <el-input v-model="form.workLocation" placeholder="请输入工作地点" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import request from '@/utils/request'
import PositionCategoryService from '../../api/positionCategory'

const loading = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)
const dialogTitle = ref('发布岗位')
const formRef = ref(null)

const searchForm = reactive({
  positionName: '',
  categoryId: null
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

const positions = ref([])
const categories = ref([])

const form = reactive({
  id: null,
  categoryId: null,
  positionName: '',
  plannedRecruit: 1,
  description: '',
  requirements: '',
  salary: '',
  workLocation: ''
})

const rules = {
  positionName: [
    { required: true, message: '请输入岗位名称', trigger: 'blur' }
  ],
  plannedRecruit: [
    { required: true, message: '请输入计划招聘人数', trigger: 'blur' }
  ]
}

const formatDate = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const getStatusType = (status) => {
  const statusMap = {
    0: 'warning',
    1: 'success',
    2: 'info'
  }
  return statusMap[status] || 'info'
}

const getStatusText = (status) => {
  const statusMap = {
    0: '招聘中',
    1: '已招满',
    2: '已关闭'
  }
  return statusMap[status] || '未知'
}

const loadPositions = async () => {
  loading.value = true
  try {
    const response = await request.get('/company/positions', {
      params: {
        page: pagination.page,
        pageSize: pagination.pageSize
      }
    })
    if (response.code === 200) {
      positions.value = response.data.data || []
      pagination.total = response.data.total || 0
    }
  } catch (error) {
    console.error('加载岗位列表失败:', error)
    ElMessage.error('加载岗位列表失败')
  } finally {
    loading.value = false
  }
}

const searchPositions = () => {
  pagination.page = 1
  loadPositions()
}

const resetSearch = () => {
  searchForm.positionName = ''
  pagination.page = 1
  loadPositions()
}

const showCreateDialog = () => {
  dialogTitle.value = '发布岗位'
  resetForm()
  dialogVisible.value = true
}

const viewPosition = (row) => {
  ElMessage.info('查看岗位详情功能开发中')
}

const editPosition = (row) => {
  dialogTitle.value = '编辑岗位'
  Object.assign(form, row)
  dialogVisible.value = true
}

const deletePosition = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该岗位吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const response = await request.delete(`/company/positions/${row.id}`)
    if (response.code === 200) {
      ElMessage.success('删除成功')
      await loadPositions()
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除岗位失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

const submitForm = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    
    submitting.value = true
    try {
      let response
      if (form.id) {
        response = await request.put(`/company/positions/${form.id}`, form)
      } else {
        response = await request.post('/company/positions', form)
      }
      
      if (response.code === 200) {
        ElMessage.success(form.id ? '更新成功' : '发布成功')
        dialogVisible.value = false
        await loadPositions()
      }
    } catch (error) {
      console.error('提交失败:', error)
      ElMessage.error('提交失败')
    } finally {
      submitting.value = false
    }
  })
}

const resetForm = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  Object.assign(form, {
    id: null,
    categoryId: null,
    positionName: '',
    plannedRecruit: 1,
    description: '',
    requirements: '',
    salary: '',
    workLocation: ''
  })
}

const loadCategories = async () => {
  try {
    const response = await PositionCategoryService.getCategories()
    if (response && response.data) {
      categories.value = response.data.data || response.data || []
    }
  } catch (error) {
    console.error('获取岗位类别列表失败:', error)
  }
}

const handleSizeChange = (val) => {
  pagination.pageSize = val
  loadPositions()
}

const handlePageChange = (val) => {
  pagination.page = val
  loadPositions()
}

onMounted(() => {
  loadPositions()
  loadCategories()
})
</script>

<style scoped>
.position-management-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

.header-card {
  margin-bottom: 20px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.search-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
