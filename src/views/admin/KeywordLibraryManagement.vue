<template>
  <div class="keyword-library-container">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">关键词库管理</h1>
        <p class="page-description">管理AI分析模型的关键词库</p>
      </div>
      <div class="header-illustration">
        <div class="illustration-circle circle-1"></div>
        <div class="illustration-circle circle-2"></div>
      </div>
    </div>

    <el-card class="search-card" shadow="never">
      <el-form :inline="true" class="search-form">
        <div class="search-row">
          <el-form-item label="状态">
            <el-select v-model="filterStatus" placeholder="选择状态" clearable style="width: 120px">
              <el-option label="全部状态" value="" />
              <el-option label="启用" :value="1" />
              <el-option label="禁用" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item class="search-actions">
            <el-button type="primary" @click="handleFilter" class="search-btn">
              <el-icon><Search /></el-icon>&nbsp;查询
            </el-button>
            <el-button type="warning" @click="handleReset" class="reset-btn">
              <el-icon><Refresh /></el-icon>&nbsp;重置
            </el-button>
            <el-button v-if="authStore.hasPermission('keyword:view')" type="primary" @click="handleAdd" class="action-btn primary">
              <el-icon><Plus /></el-icon>&nbsp;新增关键词
            </el-button>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="stats-summary">
        <div class="stat-item">
          <el-icon><Document /></el-icon>
          <span class="stat-text">关键词总数：<strong>{{ totalKeywords }}</strong></span>
        </div>
        <div class="stat-item">
          <el-icon><CircleCheck /></el-icon>
          <span class="stat-text">已启用：<strong>{{ enabledKeywords }}</strong></span>
        </div>
        <div class="stat-item">
          <el-icon><CircleClose /></el-icon>
          <span class="stat-text">已禁用：<strong>{{ disabledKeywords }}</strong></span>
        </div>
      </div>

      <div class="keywords-grid" v-loading="loading">
        <div 
          v-for="keyword in sortedKeywords" 
          :key="keyword.id" 
          class="keyword-card"
          :class="{ 'disabled': keyword.status === 0 }"
        >
          <div class="keyword-header">
            <div class="keyword-name">
              {{ keyword.keyword }}
              <el-tag 
                :type="keyword.status === 1 ? 'success' : 'info'" 
                size="small" 
                class="status-tag"
              >
                {{ keyword.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </div>
            <div class="keyword-actions">
              <el-button 
                :type="keyword.status === 1 ? 'success' : 'default'" 
                link 
                size="small" 
                @click="handleToggleStatus(keyword)"
                class="toggle-btn"
              >
                <el-icon v-if="keyword.status === 1"><CircleCheck /></el-icon>
                <el-icon v-else><CircleClose /></el-icon>
              </el-button>
              <el-button type="primary" link size="small" @click="handleEdit(keyword)">
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-button type="danger" link size="small" @click="handleDelete(keyword)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" @close="handleDialogClose">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="关键词" prop="keyword">
          <el-input v-model="form.keyword" placeholder="请输入关键词" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted, nextTick, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Search, Refresh, Document, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const keywordList = ref([])
const filterStatus = ref('')
const formRef = ref(null)

const form = ref({
  id: null,
  keyword: '',
  status: 1
})

const rules = {
  keyword: [
    { required: true, message: '请输入关键词', trigger: 'blur' }
  ]
}

const totalKeywords = computed(() => keywordList.value.length)

const enabledKeywords = computed(() => keywordList.value.filter(k => k.status === 1).length)

const disabledKeywords = computed(() => keywordList.value.filter(k => k.status === 0).length)

const sortedKeywords = computed(() => {
  return [...keywordList.value].sort((a, b) => {
    if (a.status === b.status) {
      return b.id - a.id
    }
    return b.status - a.status
  })
})

const fetchKeywords = async () => {
  loading.value = true
  try {
    const response = await request.get('/admin/keyword-library')
    if (response.data.code === 200) {
      keywordList.value = response.data.data || []
    } else {
      ElMessage.error(response.data.msg || '获取关键词列表失败')
    }
  } catch (error) {
    ElMessage.error('获取关键词列表失败')
  } finally {
    loading.value = false
  }
}

const handleFilter = async () => {
  loading.value = true
  try {
    let url = '/admin/keyword-library'
    if (filterStatus.value !== '') {
      url += `/status/${filterStatus.value}`
    }
    
    const response = await request.get(url)
    if (response.data.code === 200) {
      keywordList.value = response.data.data || []
    } else {
      ElMessage.error(response.data.msg || '筛选失败')
    }
  } catch (error) {
    ElMessage.error('筛选失败')
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  filterStatus.value = ''
  fetchKeywords()
}

const handleAdd = () => {
  dialogTitle.value = '新增关键词'
  form.value = {
    id: null,
    keyword: '',
    status: 1
  }
  dialogVisible.value = true
  
  nextTick(() => {
    if (formRef.value) {
      formRef.value.clearValidate()
    }
  })
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑关键词'
  form.value = {
    id: row.id,
    keyword: row.keyword,
    status: row.status
  }
  dialogVisible.value = true
  
  nextTick(() => {
    if (formRef.value) {
      formRef.value.clearValidate()
    }
  })
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除关键词"${row.keyword}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const response = await request.delete(`/admin/keyword-library/${row.id}`)
    if (response.data.code === 200) {
      ElMessage.success('删除成功')
      fetchKeywords()
    } else {
      ElMessage.error(response.data.msg || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleToggleStatus = async (row) => {
  const newStatus = row.status === 1 ? 0 : 1
  const statusText = newStatus === 1 ? '启用' : '禁用'
  
  try {
    const response = await request.put(`/admin/keyword-library/${row.id}`, {
      id: row.id,
      keyword: row.keyword,
      status: newStatus
    })
    
    if (response.data.code === 200) {
      ElMessage.success(`${statusText}成功`)
      fetchKeywords()
    } else {
      ElMessage.error(response.data.msg || `${statusText}失败`)
    }
  } catch (error) {
    ElMessage.error(`${statusText}失败`)
  }
}

const handleSave = async () => {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }
  
  saving.value = true
  try {
    let response
    if (form.value.id) {
      response = await request.put(`/admin/keyword-library/${form.value.id}`, form.value)
    } else {
      response = await request.post('/admin/keyword-library', form.value)
    }
    
    if (response.data.code === 200) {
      ElMessage.success('保存成功')
      dialogVisible.value = false
      fetchKeywords()
    } else {
      ElMessage.error(response.data.msg || '保存失败')
    }
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const handleDialogClose = () => {
  nextTick(() => {
    if (formRef.value) {
      formRef.value.resetFields()
    }
  })
}

onMounted(() => {
  fetchKeywords()
})
</script>

<style scoped>
.keyword-library-container {
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
  box-shadow: 0 8px 25px rgba(64, 158, 255, 0.25);
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
}

.header-content {
  flex: 1;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 8px 0;
  color: white;
}

.page-description {
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

.search-card,
.table-card {
  border-radius: 16px;
  border: none;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  margin-bottom: 24px;
  overflow: hidden;
}

.search-card {
  padding: 24px;
}

.table-card {
  padding: 0;
}

.search-form {
  width: 100%;
}

.search-row {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  flex-wrap: wrap;
}

.search-row .el-form-item {
  margin-bottom: 0;
  flex: 0 0 auto;
}

.search-actions {
  flex: 0 0 auto;
  margin-left: auto;
}

.search-btn,
.reset-btn {
  border-radius: 8px;
  padding: 10px 20px;
  margin-right: 8px;
}

.search-btn:last-child,
.reset-btn:last-child {
  margin-right: 0;
}

.action-btn {
  border-radius: 8px;
  padding: 10px 20px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.action-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.action-btn.primary {
  background: linear-gradient(135deg, #409EFF 0%, #409EFF 100%);
  border: none;
}

.stats-summary {
  display: flex;
  gap: 24px;
  padding: 20px 24px;
  background: #f8f9fa;
  border-bottom: 1px solid #e4e7ed;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  background: white;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  transition: all 0.3s ease;
}

.stat-item:hover {
  border-color: #409EFF;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}

.stat-item .el-icon {
  font-size: 20px;
  color: #409EFF;
}

.stat-text {
  font-size: 14px;
  color: #606266;
}

.stat-text strong {
  font-size: 18px;
  color: #303133;
  margin-left: 4px;
}

.keywords-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
  padding: 24px;
}

.keyword-card {
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  padding: 20px;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.keyword-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
  border-color: #409EFF;
}

.keyword-card.disabled {
  opacity: 0.6;
  background: #f5f7fa;
}

.keyword-card.disabled:hover {
  transform: none;
  box-shadow: none;
}

.keyword-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.keyword-name {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
}

.keyword-actions {
  display: flex;
  gap: 8px;
}

.keyword-actions .el-button {
  padding: 4px 8px;
  font-size: 14px;
}

.keyword-actions .el-button.is-link {
  padding: 4px 8px;
  font-size: 16px;
  height: auto;
  line-height: 1;
}

.keyword-actions .el-button.is-link .el-icon {
  font-size: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.toggle-btn {
  padding: 4px 8px;
  font-size: 16px;
  height: auto;
  line-height: 1;
}

.toggle-btn .el-icon {
  font-size: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
}

.status-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
}
</style>
