<template>
  <div class="ai-model-container">
    <div class="page-header">
      <div class="header-content">
        <div class="title-section">
          <h1>AI大模型选择</h1>
          <p>管理和配置AI大模型，选择适合的模型为系统提供智能服务</p>
        </div>
        <div class="header-illustration">
          <div class="illustration-circle circle-1"></div>
          <div class="illustration-circle circle-2"></div>
        </div>
      </div>
      <div class="current-model-display" v-if="currentModel">
        <div class="current-model-icon">
          <el-icon><Star /></el-icon>
        </div>
        <div class="current-model-info">
          <div class="current-model-label">当前使用模型（默认）</div>
          <div class="current-model-name">{{ currentModel.modelName }}</div>
          <div class="current-model-detail">
            <span class="detail-item">{{ currentModel.modelType }}</span>
            <span class="detail-separator">|</span>
            <span class="detail-item">最大Token: {{ currentModel.maxTokens }}</span>
          </div>
        </div>
      </div>
    </div>

    <el-card class="actions-card" shadow="never">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <div class="search-row">
          <el-form-item label="模型名称">
            <el-input v-model="searchKeyword" placeholder="请输入模型名称" clearable @keyup.enter="handleSearch">
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filterStatus" placeholder="请选择状态" clearable @change="handleFilter">
              <el-option label="启用" :value="1" />
              <el-option label="禁用" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <div class="search-actions">
              <el-button type="primary" @click="handleSearch" class="search-btn">
                <el-icon><Search /></el-icon>&nbsp;查询
              </el-button>
              <el-button type="warning" @click="handleReset" class="reset-btn">
                <el-icon><Refresh /></el-icon>&nbsp;重置
              </el-button>
            </div>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="modelList" border stripe class="data-table" v-loading="loading" empty-text="暂无AI模型">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="modelName" label="模型名称" width="180" />
        <el-table-column prop="modelCode" label="模型代码" width="200" />
        <el-table-column prop="maxTokens" label="最大Token" width="120" />
        <el-table-column prop="temperature" label="温度参数" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag class="status-tag" :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isDefault" label="默认" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault === 1" type="warning" size="small">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="200" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-tooltip v-if="authStore.hasPermission('ai:model:view')" content="编辑" placement="top">
                <el-button type="primary" size="small" @click="handleEdit(row)" class="table-btn edit">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip 
                v-if="authStore.hasPermission('ai:model:view') && row.isDefault !== 1"
                content="设为默认" 
                placement="top"
              >
                <el-button type="warning" size="small" @click="handleSetDefault(row)" class="table-btn default">
                  <el-icon><Star /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('ai:model:view')" content="删除" placement="top">
                <el-button type="danger" size="small" @click="handleDelete(row)" class="table-btn delete">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px" class="form-dialog" @close="handleDialogClose">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px" class="ai-model-form">
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="form.modelName" placeholder="请输入模型名称，如：DeepSeek-V3" />
        </el-form-item>
        <el-form-item label="模型代码" prop="modelCode">
          <el-input v-model="form.modelCode" placeholder="请输入模型代码，如：deepseek-chat" :disabled="!!form.id" />
          <div class="form-tip">模型代码是唯一标识，添加后不可修改</div>
        </el-form-item>
        <el-form-item label="最大Token数" prop="maxTokens">
          <el-input-number v-model="form.maxTokens" :min="1" :max="128000" placeholder="请输入最大Token数" />
        </el-form-item>
        <el-form-item label="温度参数" prop="temperature">
          <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="2" placeholder="请输入温度参数" />
          <div class="form-tip">温度参数控制输出的随机性，值越高输出越随机</div>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="设为默认" prop="isDefault">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
          <div class="form-tip">设置后，该模型将作为默认AI模型</div>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入模型描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button class="cancel-btn" @click="dialogVisible = false">取消</el-button>
          <el-button class="confirm-btn" type="primary" @click="handleSave" :loading="saving">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import logger from '@/utils/logger'
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Search, Star, Refresh } from '@element-plus/icons-vue'
import aiModelService from '@/api/aiModel'
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const modelList = ref([])
const currentModel = ref(null)
const searchKeyword = ref('')
const filterStatus = ref('')
const formRef = ref(null)

const searchForm = ref({
  modelName: '',
  status: ''
})

const form = ref({
  id: null,
  name: '',
  provider: '',
  modelType: '',
  modelName: '',
  modelCode: '',
  maxTokens: 4096,
  temperature: 0.70,
  status: 1,
  isDefault: false,
  description: ''
})

const rules = {
  modelName: [
    { required: true, message: '请输入模型名称', trigger: 'blur' }
  ],
  modelCode: [
    { required: true, message: '请输入模型代码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9-_]+$/, message: '模型代码只能包含字母、数字、横线和下划线', trigger: 'blur' }
  ],
  maxTokens: [
    { required: true, message: '请输入最大Token数', trigger: 'blur' }
  ],
  temperature: [
    { required: true, message: '请输入温度参数', trigger: 'blur' }
  ]
}

const fetchModels = async () => {
  loading.value = true
  try {
    const response = await aiModelService.getAIModels()
    if (response.code === 200) {
      modelList.value = response.data || []
      currentModel.value = modelList.value.find(model => model.isDefault === 1) || null
    } else {
      ElMessage.error(response.message || '获取AI模型列表失败')
    }
  } catch (error) {
    ElMessage.error('获取AI模型列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = async () => {
  if (!searchKeyword.value) {
    fetchModels()
    return
  }

  loading.value = true
  try {
    const response = await aiModelService.searchAIModels(searchKeyword.value)
    if (response.code === 200) {
      modelList.value = response.data || []
    } else {
      ElMessage.error(response.message || '搜索失败')
    }
  } catch (error) {
    ElMessage.error('搜索失败')
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  searchKeyword.value = ''
  filterStatus.value = ''
  fetchModels()
}

const handleFilter = async () => {
  loading.value = true
  try {
    let response
    if (filterStatus.value !== '') {
      response = await aiModelService.getAIModelsByStatus(filterStatus.value === '1')
    } else {
      fetchModels()
      return
    }

    if (response && response.code === 200) {
      modelList.value = response.data || []
    }
  } catch (error) {
    ElMessage.error('筛选失败')
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  dialogTitle.value = '添加AI模型'
  form.value = {
    id: null,
    modelName: '',
    modelCode: '',
    maxTokens: 4096,
    temperature: 0.70,
    name: '',
    provider: '',
    modelType: '',
    status: 1,
    isDefault: false,
    description: ''
  }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑AI模型'
  form.value = {
    id: row.id,
    modelName: row.modelName,
    name: row.name || '',
    provider: row.provider || '',
    modelType: row.modelType || '',
    modelCode: row.modelCode,
    maxTokens: row.maxTokens,
    temperature: row.temperature,
    status: row.status,
    isDefault: row.isDefault,
    description: row.description || ''
  }
  dialogVisible.value = true
}

const handleSetDefault = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要将"${row.modelName}"设置为默认模型吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const response = await aiModelService.setAsDefault(row.id, 'admin')
    if (response.code === 200) {
      ElMessage.success('设置默认模型成功')
      fetchModels()
    } else {
      ElMessage.error(response.message || '设置失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('设置失败')
    }
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除AI模型"${row.modelName}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const response = await aiModelService.deleteAIModel(row.id)
    if (response.code === 200) {
      ElMessage.success('删除成功')
      fetchModels()
    } else {
      ElMessage.error(response.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
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
      response = await aiModelService.updateAIModel(form.value.id, form.value)
      logger.log('更新 AI 模型响应:', response)
      logger.log('响应数据:', response.data)
    } else {
      response = await aiModelService.addAIModel(form.value)
      logger.log('添加 AI 模型响应:', response)
      logger.log('响应数据:', response.data)
    }

    logger.log('response.code:', response?.code)
    logger.log('response.message:', response?.message)

    if (response && response.code === 200) {
      ElMessage.success('保存成功')
      dialogVisible.value = false
      fetchModels()
    } else {
      ElMessage.error(response?.message || '保存失败')
    }
  } catch (error) {
    logger.error('保存失败:', error)
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

const formatDateTime = (date) => {
  if (!date) return ''
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

onMounted(() => {
  fetchModels()
})
</script>

<style scoped>
.ai-model-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

/* 页面标题区域 */
.page-header {
  border-radius: 16px;
  border: none;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  margin-bottom: 24px;
  overflow: hidden;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  border-radius: 16px 16px 0 0;
  padding: 24px 32px;
  color: white;
  position: relative;
  overflow: hidden;
}

.header-content::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -50%;
  width: 100%;
  height: 100%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
  transform: rotate(30deg);
}

.title-section h1 {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
  color: white;
}

.title-section p {
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

/* 当前模型显示区域 */
.current-model-display {
  display: flex;
  align-items: center;
  padding: 20px 32px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 0 0 16px 16px;
  color: white;
  position: relative;
  overflow: hidden;
}

.current-model-display::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -50%;
  width: 100%;
  height: 100%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
  transform: rotate(30deg);
}

.current-model-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  margin-right: 20px;
  flex-shrink: 0;
  z-index: 1;
}

.current-model-icon .el-icon {
  font-size: 28px;
}

.current-model-info {
  flex: 1;
  z-index: 1;
}

.current-model-label {
  font-size: 13px;
  opacity: 0.95;
  margin-bottom: 6px;
  font-weight: 500;
}

.current-model-name {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 8px;
}

.current-model-detail {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  opacity: 0.9;
}

.detail-item {
  display: flex;
  align-items: center;
}

.detail-separator {
  opacity: 0.6;
  font-weight: 300;
}

/* 卡片通用样式 */
.actions-card,
.table-card {
  border-radius: 16px;
  border: none;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  margin-bottom: 24px;
  overflow: hidden;
}

.actions-card {
  padding: 20px 24px;
}

.table-card {
  padding: 0;
}

.search-form {
  width: 100%;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.search-actions {
  display: flex;
  gap: 10px;
}

.search-btn,
.reset-btn {
  border-radius: 8px;
  padding: 10px 20px;
}

/* 表格样式 */
.data-table {
  border-radius: 8px;
}

.data-table :deep(.el-table__header) th {
  background-color: #f8fbff;
  color: #409EFF;
  font-weight: 600;
}

.data-table :deep(.el-table__row) td {
  border-bottom: 1px solid #f0f7ff;
}

.status-tag {
  border-radius: 12px;
  font-weight: 500;
}

/* 表格操作按钮 */
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

.table-btn.edit {
  background-color: #409EFF;
  border-color: #409EFF;
  color: white;
}

.table-btn.edit:hover {
  background-color: #66b1ff;
  border-color: #66b1ff;
}

.table-btn.delete {
  background-color: #F56C6C;
  border-color: #F56C6C;
  color: white;
}

.table-btn.delete:hover {
  background-color: #f78989;
  border-color: #f78989;
}

.table-btn.default {
  background-color: #E6A23C;
  border-color: #E6A23C;
  color: white;
}

.table-btn.default:hover {
  background-color: #ebb563;
  border-color: #ebb563;
}

/* 对话框样式 */
.form-dialog {
  border-radius: 16px;
  overflow: hidden;
}

.form-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #f8fbff 0%, #e6f7ff 100%);
  margin: 0;
  padding: 20px;
  border-bottom: 1px solid #e6f7ff;
}

.ai-model-form {
  padding: 20px 0;
}

.form-tip {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}

/* 对话框按钮 */
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.cancel-btn,
.confirm-btn {
  border-radius: 8px;
  padding: 10px 20px;
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
  .ai-model-container {
    padding: 15px;
  }
  
  .header-content {
    flex-direction: column;
    text-align: center;
    padding: 20px;
  }
  
  .header-illustration {
    margin-top: 15px;
  }
  
  .current-model-display {
    flex-direction: column;
    text-align: center;
    padding: 20px;
  }
  
  .current-model-icon {
    margin-right: 0;
    margin-bottom: 16px;
  }
  
  .current-model-detail {
    flex-direction: column;
    gap: 4px;
  }
  
  .detail-separator {
    display: none;
  }
  
  .search-row {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  
  .search-actions {
    justify-content: center;
  }
  
  .action-buttons {
    flex-wrap: wrap;
    justify-content: center;
  }
  
  .data-table {
    font-size: 12px;
  }
  
  .action-buttons {
    gap: 4px;
  }
}
</style>
