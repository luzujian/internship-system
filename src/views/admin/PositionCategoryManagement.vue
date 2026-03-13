<template>
<div class="position-category-management-container">
  <div class="page-header">
    <div class="header-content">
      <h1 class="page-title">岗位类别管理</h1>
      <p class="page-description">管理系统中的所有岗位类别与岗位信息</p>
    </div>
    <div class="header-illustration">
      <div class="illustration-circle circle-1"></div>
      <div class="illustration-circle circle-2"></div>
    </div>
  </div>

  <el-card class="search-card" shadow="never">
    <el-form ref="searchFormRef" :inline="true" :model="searchCategory" class="search-form" @keyup.enter="searchBothCategoryAndPosition">
      <div class="search-row">
        <el-form-item label="类别名称">
          <el-input
            v-model="searchCategory.name"
            placeholder="请输入类别名称"
            clearable
            @keyup.enter="searchBothCategoryAndPosition"
          ></el-input>
        </el-form-item>
        <el-form-item label="岗位名称">
          <el-select
            v-model="searchPositionName"
            filterable
            clearable
            placeholder="请输入或选择岗位名称"
            :filter-method="handlePositionFilter"
          >
            <el-option
              v-for="position in positionList"
              :key="position.id"
              :label="position.positionName"
              :value="position.positionName"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item class="search-actions">
          <el-button type="primary" @click="searchBothCategoryAndPosition" class="search-btn">
            <el-icon><Search /></el-icon>&nbsp;查询
          </el-button>
          <el-button type="warning" @click="clear" class="reset-btn">
            <el-icon><Refresh /></el-icon>&nbsp;重置
          </el-button>
        </el-form-item>
      </div>
    </el-form>
  </el-card>

  <el-card class="actions-card" shadow="never">
    <div class="actions-container">
      <div class="primary-actions">
        <el-button v-if="authStore.hasPermission('position-category:add')" type="primary" @click="handleAddCategory()" class="action-btn primary">
          <el-icon><Plus /></el-icon>&nbsp;新增类别
        </el-button>
        <el-button 
          v-if="authStore.hasPermission('recruitment:add') && fixedCategoryId"
          type="success" 
          @click="handleAddPosition" 
          class="action-btn success"
        >
          <el-icon><Plus /></el-icon>&nbsp;新增岗位
        </el-button>
      </div>
      <div class="secondary-actions">
                <el-dropdown v-if="authStore.hasPermission('position-category:view')" trigger="click" class="import-dropdown">
          <el-button type="primary" class="action-btn success" :loading="importLoading">
            <el-icon><Upload /></el-icon>&nbsp;导入 Excel<el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="showImportDialog">选择文件导入</el-dropdown-item>
              <el-dropdown-item @click="downloadTemplate">下载导入模板</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button 
          v-if="authStore.hasPermission('position-category:view')"
          type="success" 
          @click="exportToExcel" 
          class="action-btn success"
          :loading="exportLoading"
        >
          <el-icon><Download /></el-icon>&nbsp;导出Excel
        </el-button>
        <input 
          ref="excelFileInput"
          type="file" 
          style="display: none" 
          accept=".xlsx,.xls"
          @change="handleNativeFileChange"
        />
      </div>
    </div>
  </el-card>

  <div class="tables-container">
    <el-card class="table-card left-table" shadow="never">
      <div class="table-header">
        <h3 class="table-title">岗位类别列表</h3>
        <div class="table-actions">
          <span class="total-count">共 {{ pagination.total }} 个类别</span>
        </div>
      </div>
      <el-table 
        :data="categories" 
        border 
        style="width: 100%" 
        fit 
        @cell-mouse-enter="handleTableMouseEnter"
        @cell-mouse-leave="handleTableMouseLeave"
        class="data-table"
        v-loading="loading"
      >
        <el-table-column type="index" label="序号" width="80" align="center" :index="(index) => (pagination.currentPage - 1) * pagination.pageSize + index + 1" />
        <el-table-column prop="name" label="类别名称" align="center" min-width="150" />
        <el-table-column prop="description" label="类别描述" align="center" min-width="200" show-overflow-tooltip />
        <el-table-column prop="positionCount" label="岗位数量" align="center" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="180" align="center" :formatter="formatDate" />
        <el-table-column label="操作" align="center" width="200" fixed="right">
          <template #default="scope">
            <div class="action-buttons">
              <el-tooltip content="查看岗位" placement="top">
                <el-button 
                  :type="fixedCategoryId === scope.row.id ? 'danger' : 'success'" 
                  size="small" 
                  @click="handleShowCategoryPositions(scope.row.id)"
                  class="table-btn position"
                >
                  <el-icon><View /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('position-category:edit')" content="编辑" placement="top">
                <el-button type="primary" size="small" @click="handleUpdateCategory(scope.row.id)" class="table-btn edit">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('position-category:delete')" content="删除" placement="top">
                <el-button type="danger" size="small" @click="delById(scope.row.id)" class="table-btn delete">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.currentPage"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="pagination.total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          class="custom-pagination"
        />
      </div>
    </el-card>

    <el-card 
      class="table-card right-table" 
      shadow="never"
    >
      <div class="table-header">
        <h3 class="table-title">
          {{ fixedCategoryId || hoveredCategoryId ? getCategoryName(fixedCategoryId || hoveredCategoryId): '岗位列表' }}
          <span class="position-count" v-if="displayedPositions.length > 0">({{ displayedPositions.length }}个岗位)</span>
        </h3>
        <div class="table-actions">
          <el-button 
            v-if="fixedCategoryId" 
            type="default" 
            size="small" 
            @click="handleCloseFixedPositions" 
            class="action-btn"
          >
            <el-icon><Close /></el-icon>&nbsp;关闭
          </el-button>
        </div>
      </div>
      <el-table 
        :data="displayedPositions" 
        border 
        style="width: 100%" 
        fit 
        class="data-table"
        height="400"
      >
          <el-table-column type="index" label="序号" width="60" align="center" :index="(index) => index + 1" />
          <el-table-column prop="positionName" label="岗位名称" align="center" min-width="150">
            <template #default="scope">
              <span class="position-name" @click.stop="handlePositionClick(scope.row)">{{ scope.row.positionName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="requirements" label="岗位要求" align="center" min-width="200" show-overflow-tooltip />
          <el-table-column prop="createTime" label="发布时间" width="180" align="center" :formatter="formatDate" />
          <el-table-column label="操作" align="center" width="150" fixed="right">
            <template #default="scope">
              <el-button v-if="authStore.hasPermission('recruitment:edit')" type="primary" size="small" @click="handleEditPosition(scope.row)">编辑</el-button>
              <el-button v-if="authStore.hasPermission('recruitment:delete')" type="danger" size="small" @click="handleDeletePosition(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

      <div v-if="searchPositionPagination.total > 0" class="search-pagination">
        <el-pagination 
          v-model:current-page="searchPositionPagination.currentPage" 
          layout="prev, pager, next" 
          :page-size="1" 
          :total="searchPositionPagination.total" 
          @current-change="handleSearchPositionPageChange" 
        />
      </div>
    </el-card>
  </div>

  <!-- 表单对话框（样式已更新） -->
  <el-dialog v-model="dialogFormVisible" :title="formTitle" width="500px" @close="handleCancel" class="form-dialog">
    <template v-if="formTitle === '新增岗位类别'">
      <el-form :model="categoryForm" :rules="categoryRules" ref="categoryFormRef" label-width="100px">
        <el-form-item label="类别名称" prop="name">
          <el-input v-model="categoryForm.name" placeholder="请输入类别名称" />
        </el-form-item>
        <el-form-item label="类别描述" prop="description">
          <el-input
            v-model="categoryForm.description"
            type="textarea"
            :rows="4"
            placeholder="请输入类别描述"
          />
        </el-form-item>
      </el-form>
    </template>
    <template v-else-if="formTitle === '编辑岗位类别'">
      <el-form :model="categoryForm" :rules="categoryRules" ref="categoryFormRef" label-width="100px">
        <el-form-item label="类别名称" prop="name">
          <el-input v-model="categoryForm.name" placeholder="请输入类别名称" />
        </el-form-item>
        <el-form-item label="类别描述" prop="description">
          <el-input
            v-model="categoryForm.description"
            type="textarea"
            :rows="4"
            placeholder="请输入类别描述"
          />
        </el-form-item>
      </el-form>
    </template>
    <template v-else-if="formTitle === '新增岗位'">
      <el-form :model="positionForm" :rules="positionRules" ref="positionFormRef" label-width="100px">
        <el-form-item label="岗位名称" prop="positionName">
          <el-input v-model="positionForm.positionName" placeholder="请输入岗位名称" />
        </el-form-item>
        <el-form-item label="岗位要求" prop="requirements">
          <el-input
            v-model="positionForm.requirements"
            type="textarea"
            :rows="4"
            placeholder="请输入岗位要求"
          />
        </el-form-item>
      </el-form>
    </template>
    <template v-else-if="formTitle === '编辑岗位'">
      <el-form :model="positionForm" :rules="positionRules" ref="positionFormRef" label-width="100px">
        <el-form-item label="岗位名称" prop="positionName">
          <el-input v-model="positionForm.positionName" placeholder="请输入岗位名称" />
        </el-form-item>
        <el-form-item label="岗位要求" prop="requirements">
          <el-input
            v-model="positionForm.requirements"
            type="textarea"
            :rows="4"
            placeholder="请输入岗位要求"
          />
        </el-form-item>
      </el-form>
    </template>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleCancel" class="cancel-btn">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting" class="confirm-btn">确定</el-button>
      </div>
    </template>
  </el-dialog>

  <el-dialog
    v-model="importDialogVisible"
    title="导入岗位类别数据"
    width="40%"
    :before-close="closeImportDialog"
  >
    <div class="import-dialog-content">
      <div class="import-tips">
        <p class="tip-text">
          <span class="tip-icon">⚠️</span>
          请确保Excel文件格式正确，包含以下字段：类别名称、岗位名称、岗位要求。
        </p>
        <p class="tip-text">
          <span class="tip-icon">⚠️</span>
          如果类别不存在，系统将自动创建新类别。
        </p>
      </div>
      
      <div class="file-select-section">
        <el-button 
          type="primary" 
          @click="handleChooseFile"
          :loading="importLoading"
          class="choose-file-btn"
        >
          选择Excel文件
        </el-button>
        
        <div v-if="selectedFile" class="selected-file-info">
          <span class="file-icon">📁</span>
          <span class="file-name">{{ selectedFile.name }}</span>
        </div>
        <div v-else class="no-file-selected">
          未选择文件
        </div>
      </div>
      
      <div class="import-note">
        <p>支持的文件格式：.xlsx、.xls</p>
        <p>文件大小上限：5MB</p>
      </div>
    </div>
    
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="closeImportDialog" class="cancel-btn">取消</el-button>
        <el-button 
          type="primary" 
          @click="importExcel" 
          :loading="importLoading" 
          class="confirm-btn"
          :disabled="!selectedFile"
        >
          导入
        </el-button>
      </div>
    </template>
  </el-dialog>
</div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { onMounted, ref, computed, nextTick } from 'vue'
import type { Position, PositionCategory } from '@/types/admin'

import PositionCategoryService from '../../api/positionCategory'
import PositionService from '../../api/position'
import request from '../../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, View, Download, Upload, Close, ArrowDown } from '@element-plus/icons-vue'
import * as XLSX from 'xlsx'
import { useAuthStore } from '../../store/auth'

const authStore = useAuthStore()

let searchCategory = ref({ name: '', positionId: null })
let loading = ref<boolean>(false)
let categories = ref<unknown[]>([])
let positionList = ref<unknown[]>([])
let hoveredCategoryId = ref(null)
let fixedCategoryId = ref(null)
let categoryPositions = ref(new Map())
let searchPositionName = ref<string>('')
let searchPositionResult = ref(null)
let importLoading = ref<boolean>(false)
let exportLoading = ref<boolean>(false)
let dialogFormVisible = ref<boolean>(false)
let formTitle = ref<string>('')
let submitting = ref<boolean>(false)
let importDialogVisible = ref<boolean>(false)
let selectedFile = ref(null)
let excelFileInput = ref(null)
let categoryFormRef = ref()
let positionFormRef = ref()
let categoryForm = ref({
  id: '',
  name: '',
  description: ''
})
let positionForm = ref({
  id: '',
  categoryId: '',
  positionName: '',
  requirements: ''
})

const categoryRules = ref({
  name: [
    { required: true, message: '类别名称为必填项', trigger: 'blur' },
    { min: 2, max: 50, message: '类别名称长度为2-50个字', trigger: 'blur' }
  ]
})

const positionRules = ref({
  positionName: [
    { required: true, message: '岗位名称为必填项', trigger: 'blur' },
    { min: 2, max: 100, message: '岗位名称长度为2-100个字', trigger: 'blur' }
  ]
})

const pagination = ref({ currentPage: 1, pageSize: 10, total: 0 })
const searchPositionPagination = ref({ currentPage: 1, total: 0, departments: [] })

const formatDate = (_row, _column, cellValue) => {
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

onMounted(async () => {
  logger.log('页面开始加载...')
  await getPositionList()
  await queryCategories()
})

const getPositionList = async (): Promise<void> => {
  try {
    logger.log('开始获取岗位列表...')
    const response = await PositionService.getAllPositions()
    logger.log('岗位列表响应:', response)

    if (response && response.data) {
      if (response.data.code === 200 && response.data.data) {
        positionList.value = Array.isArray(response.data.data) ? response.data.data : []
      } else if (Array.isArray(response.data)) {
        positionList.value = response.data
      } else {
        positionList.value = []
      }
    } else {
      positionList.value = []
    }

    logger.log('最终的岗位列表数据:', positionList.value)

    if (positionList.value.length > 0) {
      positionList.value = positionList.value.map((position, index) => {
        let categoryId = position.categoryId || position.category_id || ''

        if (categoryId) {
          categoryId = Number(categoryId)
        } else {
          categoryId = (index % 10) + 1
        }

        return {
          id: position.id || '',
          positionName: position.positionName || position.position_name || '未知岗位',
          categoryId: categoryId,
          requirements: position.requirements || '',
          plannedRecruit: position.plannedRecruit || position.planned_recruit || 0,
          recruitedCount: position.recruitedCount || position.recruited_count || 0,
          remainingQuota: position.remainingQuota || position.remaining_quota || 0,
          createTime: position.createTime || position.create_time || '',
          updateTime: position.updateTime || position.update_time || ''
        }
      })
    }

    buildCategoryPositionsMap()
  } catch (error) {
    logger.error('获取岗位列表失败:', error)
    positionList.value = []
    ElMessage.error('获取岗位列表失败: ' + (error.message || '未知错误'))
  }
}

const handleSizeChange = (pageSize) => {
  pagination.value.pageSize = pageSize
  pagination.value.currentPage = 1
  queryCategories()
}

const handleCurrentChange = (page) => {
  pagination.value.currentPage = page
  queryCategories()
}

const queryCategories = async (): Promise<void> => {
  try {
    logger.log('开始查询岗位类别列表...')
    logger.log('搜索类别名称:', searchCategory.value.name)

    if (positionList.value.length === 0) {
      logger.log('岗位列表为空，先获取岗位列表...')
      await getPositionList()
    }

    const result = await PositionCategoryService.getCategories(searchCategory.value.name)
    logger.log('岗位类别列表查询结果:', result)

    if (result && (result.code === 200 || result.data)) {
      const categoryData = result.data.code === 200 ? result.data.data : result.data
      const allCategories = Array.isArray(categoryData) ? categoryData : []

      pagination.value.total = allCategories.length

      const startIndex = (pagination.value.currentPage - 1) * pagination.value.pageSize
      const endIndex = startIndex + pagination.value.pageSize
      
      categories.value = allCategories.slice(startIndex, endIndex).map(category => {
        const categoryId = category.id
        const positionCount = categoryPositions.value.get(categoryId)?.length || 0
        return {
          ...category,
          positionCount: positionCount
        }
      })

      logger.log('处理后的岗位类别数据:', categories.value)
      logger.log('分页信息:', pagination.value)
    } else {
      logger.error('查询岗位类别失败:', result?.msg)
      categories.value = []
      pagination.value.total = 0
      ElMessage.error('查询岗位类别失败: ' + (result?.msg || '未知错误'))
    }
  } catch (error) {
    logger.error('查询岗位类别列表时发生异常:', error)
    categories.value = []
    pagination.value.total = 0
    ElMessage.error('查询岗位类别失败: ' + (error.message || '未知错误'))
  }
}

const buildCategoryPositionsMap = () => {
  const map = new Map()
  logger.log('开始构建类别-岗位映射关系...')
  logger.log('当前岗位列表:', positionList.value)

  positionList.value.forEach(position => {
    const categoryId = position.categoryId
    if (categoryId) {
      logger.log(`岗位${position.positionName}的categoryId: ${categoryId}`)
      if (!map.has(categoryId)) {
        map.set(categoryId, [])
      }
      map.get(categoryId).push(position)
    } else {
      logger.log(`岗位${position.positionName}没有有效的categoryId`)
    }
  })

  logger.log('构建的类别-岗位映射关系:')
  map.forEach((positions, categoryId) => {
    logger.log(`类别ID ${categoryId} 包含 ${positions.length} 个岗位`)
  })

  categoryPositions.value = map
}

const getCategoryPositions = (categoryId) => {
  logger.log(`获取类别ID ${categoryId} 的岗位列表`)
  const positions = categoryPositions.value.get(categoryId) || []
  logger.log(`类别ID ${categoryId} 有 ${positions.length} 个岗位`)
  return positions
}

const handleMouseEnterCategory = (categoryId) => {
  hoveredCategoryId.value = categoryId
}

const getCategoryName = (categoryId) => {
  const category = categories.value.find(c => c.id === categoryId)
  return category ? category.name : '未知类别'
}

const handleShowCategoryPositions = (categoryId) => {
  logger.log(`点击了类别ID ${categoryId} 的岗位按钮`)
  if (fixedCategoryId.value === categoryId) {
    logger.log('取消固定类别岗位')
    fixedCategoryId.value = null
    searchPositionResult.value = null
  } else {
    logger.log(`固定显示类别ID ${categoryId} 的岗位`)
    fixedCategoryId.value = categoryId
    searchPositionResult.value = null
  }
}

const handlePositionClick = (position) => {
  logger.log('点击了岗位:', position)
}

const displayedPositions = computed(() => {
  logger.log('计算displayedPositions:', {
    searchPositionPagination: {
      currentPage: searchPositionPagination.value.currentPage,
      total: searchPositionPagination.value.total
    },
    fixedCategoryId: fixedCategoryId.value,
    hoveredCategoryId: hoveredCategoryId.value
  })

  if (searchPositionPagination.value.total > 0 && fixedCategoryId.value) {
    logger.log(`显示搜索结果中的类别ID ${fixedCategoryId.value} 的岗位`)
    const positions = getCategoryPositions(fixedCategoryId.value)
    logger.log(`搜索结果类别岗位数量: ${positions.length}`)
    return positions
  }

  if (hoveredCategoryId.value) {
    logger.log(`显示悬停类别ID ${hoveredCategoryId.value} 的岗位`)
    const positions = getCategoryPositions(hoveredCategoryId.value)
    logger.log(`悬停类别岗位数量: ${positions.length}`)
    return positions
  }

  if (fixedCategoryId.value) {
    logger.log(`显示固定类别ID ${fixedCategoryId.value} 的岗位`)
    const positions = getCategoryPositions(fixedCategoryId.value)
    logger.log(`固定类别岗位数量: ${positions.length}`)
    return positions
  }

  logger.log('没有要显示的岗位')
  return []
})

const clearAddCategoryForm = () => {
  categoryForm.value = {
    id: '',
    name: '',
    description: ''
  }
  if (categoryFormRef.value) {
    categoryFormRef.value.resetFields()
  }
}

const clearAddPositionForm = () => {
  positionForm.value = {
    id: '',
    categoryId: '',
    positionName: '',
    requirements: ''
  }
  if (positionFormRef.value) {
    positionFormRef.value.resetFields()
  }
}

const handleAddCategory = () => {
  logger.log('点击新增类别按钮')
  clearAddCategoryForm()
  formTitle.value = '新增岗位类别'
  dialogFormVisible.value = true
}

const handleUpdateCategory = async (id): Promise<void> => {
  logger.log('点击编辑类别按钮，ID:', id)
  try {
    const response = await PositionCategoryService.getCategoryById(id)
    if (response && response.data) {
      const categoryData = response.data.code === 200 ? response.data.data : response.data
      categoryForm.value = {
        id: categoryData.id,
        name: categoryData.name,
        description: categoryData.description
      }
      formTitle.value = '编辑岗位类别'
      dialogFormVisible.value = true
    }
  } catch (error) {
    logger.error('获取岗位类别详情失败:', error)
    ElMessage.error('获取岗位类别详情失败')
  }
}

const handleAddPosition = () => {
  logger.log('点击新增岗位按钮')
  clearAddPositionForm()
  positionForm.value.categoryId = fixedCategoryId.value
  formTitle.value = '新增岗位'
  dialogFormVisible.value = true
}

const handleEditPosition = (position) => {
  logger.log('点击编辑岗位按钮:', position)
  positionForm.value = {
    id: position.id,
    categoryId: position.categoryId,
    positionName: position.positionName,
    requirements: position.requirements
  }
  formTitle.value = '编辑岗位'
  dialogFormVisible.value = true
}

const handleDeletePosition = async (position): Promise<void> => {
  try {
    await ElMessageBox.confirm(
      `确定要删除岗位"${position.positionName}"吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const response = await PositionService.deletePosition(position.id)
    if (response && (response.data?.code === 200 || response.code === 200)) {
      ElMessage.success('删除成功')
      await getPositionList()
      await queryCategories()
    } else {
      ElMessage.error('删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      logger.error('删除岗位失败:', error)
      ElMessage.error('删除失败: ' + (error.message || '未知错误'))
    }
  }
}

const handleSubmit = async (): Promise<void> => {
  if (formTitle.value === '新增岗位类别') {
    await handleAddCategorySubmit()
  } else if (formTitle.value === '编辑岗位类别') {
    await handleEditCategorySubmit()
  } else if (formTitle.value === '新增岗位') {
    await handleAddPositionSubmit()
  } else if (formTitle.value === '编辑岗位') {
    await handleEditPositionSubmit()
  }
}

const handleAddCategorySubmit = async (): Promise<void> => {
  if (!categoryFormRef.value) return

  categoryFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        submitting.value = true
        const response = await PositionCategoryService.addCategory({
          name: categoryForm.value.name,
          description: categoryForm.value.description
        })
        if (response && (response.data?.code === 200 || response.code === 200)) {
          ElMessage.success('添加成功')
          dialogFormVisible.value = false
          await queryCategories()
        } else {
          ElMessage.error('添加失败')
        }
      } catch (error) {
        logger.error('添加岗位类别失败:', error)
        ElMessage.error('添加失败: ' + (error.message || '未知错误'))
      } finally {
        submitting.value = false
      }
    }
  })
}

const handleEditCategorySubmit = async (): Promise<void> => {
  if (!categoryFormRef.value) return

  categoryFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        submitting.value = true
        const response = await PositionCategoryService.updateCategory(categoryForm.value.id, {
          name: categoryForm.value.name,
          description: categoryForm.value.description
        })
        if (response && (response.data?.code === 200 || response.code === 200)) {
          ElMessage.success('更新成功')
          dialogFormVisible.value = false
          await queryCategories()
        } else {
          ElMessage.error('更新失败')
        }
      } catch (error) {
        logger.error('更新岗位类别失败:', error)
        ElMessage.error('更新失败: ' + (error.message || '未知错误'))
      } finally {
        submitting.value = false
      }
    }
  })
}

const handleAddPositionSubmit = async (): Promise<void> => {
  if (!positionFormRef.value) return

  positionFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        submitting.value = true
        const response = await PositionService.addPosition({
          categoryId: positionForm.value.categoryId,
          positionName: positionForm.value.positionName,
          requirements: positionForm.value.requirements
        })
        if (response && (response.data?.code === 200 || response.code === 200)) {
          ElMessage.success('添加成功')
          dialogFormVisible.value = false
          await getPositionList()
          await queryCategories()
        } else {
          ElMessage.error('添加失败')
        }
      } catch (error) {
        logger.error('添加岗位失败:', error)
        ElMessage.error('添加失败: ' + (error.message || '未知错误'))
      } finally {
        submitting.value = false
      }
    }
  })
}

const handleEditPositionSubmit = async (): Promise<void> => {
  if (!positionFormRef.value) return

  positionFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        submitting.value = true
        const response = await PositionService.updatePosition(positionForm.value.id, {
          categoryId: positionForm.value.categoryId,
          positionName: positionForm.value.positionName,
          requirements: positionForm.value.requirements
        })
        if (response && (response.data?.code === 200 || response.code === 200)) {
          ElMessage.success('更新成功')
          dialogFormVisible.value = false
          await getPositionList()
          await queryCategories()
        } else {
          ElMessage.error('更新失败')
        }
      } catch (error) {
        logger.error('更新岗位失败:', error)
        ElMessage.error('更新失败: ' + (error.message || '未知错误'))
      } finally {
        submitting.value = false
      }
    }
  })
}

const handleCancel = () => {
  dialogFormVisible.value = false
  nextTick(() => {
    clearAddCategoryForm()
    clearAddPositionForm()
  })
}

const delById = async (id): Promise<void> => {
  try {
    const category = categories.value.find(c => c.id === id)
    const positionCount = category?.positionCount || 0
    
    let confirmMessage = '确定要删除该岗位类别吗？'
    if (positionCount > 0) {
      confirmMessage = `该类别下还有${positionCount}个岗位，删除类别将同时删除这些岗位，确定要删除吗？`
    }
    
    await ElMessageBox.confirm(
      confirmMessage,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const response = await PositionCategoryService.deleteCategory(id)
    if (response && (response.data?.code === 200 || response.code === 200)) {
      ElMessage.success('删除成功')
      if (fixedCategoryId.value === id) {
        fixedCategoryId.value = null
      }
      await getPositionList()
      await queryCategories()
    } else {
      ElMessage.error('删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      logger.error('删除岗位类别失败:', error)
      ElMessage.error('删除失败: ' + (error.message || '未知错误'))
    }
  }
}

const handleTableMouseEnter = (row) => {
  if (row && row.id) {
    handleMouseEnterCategory(row.id)
  } else if (row && row.row && row.row.id) {
    handleMouseEnterCategory(row.row.id)
  }
}

const handleTableMouseLeave = () => {
  hoveredCategoryId.value = null
}

const handleCloseFixedPositions = () => {
  fixedCategoryId.value = null
  searchPositionResult.value = null
}

const handlePositionFilter = (query, option) => {
  return option && option.label && option.label.toLowerCase().includes(query.toLowerCase())
}

const searchBothCategoryAndPosition = async (): Promise<void> => {
  logger.log('搜索按钮被点击')
  logger.log('搜索类别名称:', searchCategory.value.name)
  logger.log('搜索岗位名称:', searchPositionName.value)

  if (searchCategory.value.name && searchPositionName.value) {
    logger.log('同时搜索类别和岗位')
    try {
      const categoryResponse = await PositionCategoryService.getCategories(searchCategory.value.name)
      let categoryIds = []
      if (categoryResponse && categoryResponse.data) {
        const categoryData = categoryResponse.data.code === 200 ? categoryResponse.data.data : categoryResponse.data
        const allCategories = Array.isArray(categoryData) ? categoryData : []
        categoryIds = allCategories.map(c => c.id)
      }

      logger.log('搜索到的类别ID列表:', categoryIds)

      const foundPositions = positionList.value.filter(position =>
        categoryIds.includes(position.categoryId) &&
        position.positionName.includes(searchPositionName.value)
      )

      if (foundPositions.length > 0) {
        const categoryMap = new Map()
        foundPositions.forEach(position => {
          const categoryId = position.categoryId
          if (!categoryMap.has(categoryId)) {
            categoryMap.set(categoryId, [])
          }
          categoryMap.get(categoryId).push(position)
        })

        const categoryList = []
        categoryMap.forEach((positions, categoryId) => {
          const category = categories.value.find(c => c.id === categoryId)
          categoryList.push({
            id: categoryId,
            name: category ? category.name : '未知类别',
            positions: positions
          })
        })

        searchPositionPagination.value.departments = categoryList
        searchPositionPagination.value.total = categoryList.length
        searchPositionPagination.value.currentPage = 1

        fixedCategoryId.value = categoryList[0]?.id

        logger.log(`搜索到${foundPositions.length}个岗位，分布在${categoryList.length}个类别`)
      } else {
        ElMessage.info('未找到匹配的岗位')
        searchPositionResult.value = null
        searchPositionPagination.value.departments = []
        searchPositionPagination.value.total = 0
      }
    } catch (error) {
      logger.error('搜索失败:', error)
      ElMessage.error('搜索失败: ' + (error.message || '未知错误'))
    }
  } else {
    logger.log('搜索框为空，显示全部类别列表')
    fixedCategoryId.value = null
    searchPositionResult.value = null
    await queryCategories()
  }
}

const clear = () => {
  searchCategory.value.name = ''
  searchPositionName.value = ''
  fixedCategoryId.value = null
  searchPositionResult.value = null
  searchPositionPagination.value.departments = []
  searchPositionPagination.value.total = 0
  queryCategories()
}

const handleSearchPositionPageChange = (page) => {
  searchPositionPagination.value.currentPage = page
  if (searchPositionPagination.value.departments[page - 1]) {
    fixedCategoryId.value = searchPositionPagination.value.departments[page - 1].id
  }
}

const showImportDialog = () => {
  selectedFile.value = null
  importDialogVisible.value = true
}

const handleChooseFile = () => {
  if (excelFileInput.value) {
    excelFileInput.value.click()
  } else {
    logger.warn('excelFileInput is not available yet')
  }
}

const handleNativeFileChange = (e) => {
  logger.log('handleNativeFileChange called with:', e)
  
  if (e.target && e.target.files && e.target.files.length > 0) {
    const file = e.target.files[0]
    const fileName = file.name.toLowerCase()
    
    if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls')) {
      ElMessage.error('请选择Excel文件(.xlsx或.xls格式)')
      e.target.value = ''
      return
    }
    
    selectedFile.value = file
  }
}

const importExcel = async (): Promise<void> => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择Excel文件')
    return
  }
  
  importLoading.value = true
  
  try {
    const reader = new FileReader()
    
    const fileData = await new Promise((resolve, reject) => {
      reader.onload = (e) => resolve(e.target.result)
      reader.onerror = reject
      reader.readAsArrayBuffer(selectedFile.value)
    })
    
    const data = new Uint8Array(fileData)
    const workbook = XLSX.read(data, { type: 'array' })
    
    const sheetName = workbook.SheetNames[0]
    const worksheet = workbook.Sheets[sheetName]
    
    const jsonData = XLSX.utils.sheet_to_json(worksheet)
    
    if (jsonData.length === 0) {
      ElMessage.warning('Excel文件中没有数据')
      return
    }
    
    logger.log('读取到的Excel数据:', jsonData)
    
    const importResult = await processImportData(jsonData)
    
    if (importResult.success) {
      let message = `导入完成，成功: ${importResult.successCount} 条`
      
      if (importResult.errorCount > 0) {
        message += `，失败: ${importResult.errorCount} 条`
      }
      
      if (importResult.errorMessages && importResult.errorMessages.length > 0) {
        let errorDetails = '\n失败详情:\n'
        importResult.errorMessages.forEach((errorMsg, index) => {
          errorDetails += `${index + 1}. ${errorMsg}\n`
        })
        
        ElMessageBox.alert(message + errorDetails, '导入结果', {
          type: importResult.errorCount > 0 ? 'warning' : 'success',
          confirmButtonText: '确定',
          dangerouslyUseHTMLString: false,
          callback: () => {
            closeImportDialog()
            getPositionList()
            queryCategories()
          }
        })
      } else {
        ElMessage.success(message)
        closeImportDialog()
        getPositionList()
        queryCategories()
      }
    } else {
      ElMessage.error(`导入失败: ${importResult.errorMessage}`)
    }
  } catch (error) {
    logger.error('导入 Excel文件失败:', error)
    ElMessage.error('导入 Excel文件失败: ' + (error.message || '未知错误'))
  } finally {
    importLoading.value = false
  }
}

const closeImportDialog = () => {
  importDialogVisible.value = false
  selectedFile.value = null
  if (excelFileInput.value) {
    excelFileInput.value.value = ''
  }
}

// 下载导入模板
const downloadTemplate = async (): Promise<void> => {
  try {
    const response = await request.get('/admin/position-categories/import-template', {
      responseType: 'blob'
    })

    // 创建下载链接
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', '岗位类别导入模板.xlsx')
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)

    ElMessage.success('模板下载成功')
  } catch (error) {
    logger.error('下载模板失败:', error)
    ElMessage.error('下载模板失败：' + (error.message || '未知错误'))
  }
}

const processImportData = async (data): Promise<void> => {
  try {
    let successCount = 0
    let errorCount = 0
    let errorMessages = []
    
    const categoryMap = new Map()
    
    const allCategories = await PositionCategoryService.getCategories()
    if (allCategories && (allCategories.code === 200 || allCategories.data)) {
      const categoryData = allCategories.data.code === 200 ? allCategories.data.data : allCategories.data
      const categoriesList = Array.isArray(categoryData) ? categoryData : []
      
      categoriesList.forEach(cat => {
        categoryMap.set(cat.name, cat.id)
      })
    }
    
    const allPositionsResponse = await PositionService.getAllPositions()
    const allPositionsData = allPositionsResponse && (allPositionsResponse.code === 200 || allPositionsResponse.data) 
      ? (allPositionsResponse.data.code === 200 ? allPositionsResponse.data.data : allPositionsResponse.data) 
      : []
    const allPositionsList = Array.isArray(allPositionsData) ? allPositionsData : []
    
    let currentCategoryName = ''
    let currentCategoryId = ''
    
    for (let i = 0; i < data.length; i++) {
      const row = data[i]
      const rowNum = i + 1
      
      try {
        let categoryName = row['类别名称'] || row['类别']
        const positionName = row['岗位名称'] || row['岗位']
        const requirements = row['岗位要求'] || row['要求'] || ''
        
        if (!categoryName && positionName) {
          if (currentCategoryName) {
            categoryName = currentCategoryName
          } else {
            errorCount++
            errorMessages.push(`第${rowNum}行: 类别名称为空且无前一行类别信息`)
            continue
          }
        }
        
        if (!categoryName || !positionName) {
          errorCount++
          errorMessages.push(`第${rowNum}行: 类别名称或岗位名称为空`)
          continue
        }
        
        currentCategoryName = categoryName
        
        let categoryId = categoryMap.get(categoryName)
        
        if (!categoryId) {
          const catResult = await PositionCategoryService.addCategory({ name: categoryName, description: '' })
          if (catResult && (catResult.code === 200 || catResult.data?.code === 200)) {
            const newCatId = catResult.data?.id || catResult.data?.data?.id
            if (newCatId) {
              categoryMap.set(categoryName, newCatId)
              categoryId = newCatId
            } else {
              errorCount++
              errorMessages.push(`第${rowNum}行: 创建类别失败 - 无法获取新类别ID`)
              continue
            }
          } else {
            const errorMsg = catResult?.msg || catResult?.data?.msg || '未知错误'
            if (errorMsg.includes('已存在') || errorMsg.includes('Duplicate') || errorMsg.includes('重复')) {
              errorCount++
              errorMessages.push(`第${rowNum}行: 类别"${categoryName}"已存在，跳过导入`)
              continue
            } else {
              errorCount++
              errorMessages.push(`第${rowNum}行: 创建类别失败 - ${errorMsg}`)
              continue
            }
          }
        }
        
        currentCategoryId = categoryId
        
        const existingPosition = allPositionsList.find(p => p.positionName === positionName && p.categoryId === categoryId)
        
        if (existingPosition) {
          errorCount++
          errorMessages.push(`第${rowNum}行: 岗位"${positionName}"已存在，跳过导入`)
          continue
        }
        
        const positionResult = await PositionService.addPosition({
          categoryId: categoryId,
          positionName: positionName,
          requirements: requirements
        })
        
        if (positionResult && (positionResult.data?.code === 200 || positionResult.code === 200)) {
          successCount++
        } else {
          errorCount++
          errorMessages.push(`第${rowNum}行: 添加岗位"${positionName}"失败`)
        }
      } catch (error) {
        errorCount++
        errorMessages.push(`第${rowNum}行: 处理失败 - ${error.message}`)
      }
    }
    
    return {
      success: true,
      successCount,
      errorCount,
      errorMessages
    }
  } catch (error) {
    logger.error('处理导入数据时发生错误:', error)
    return {
      success: false,
      errorMessage: error.message || '未知错误'
    }
  }
}

const exportToExcel = async (): Promise<void> => {
  try {
    exportLoading.value = true

    if (positionList.value.length === 0) {
      await getPositionList()
    }

    if (categories.value.length === 0) {
      await queryCategories()
    }

    const exportData = []
    let currentCategoryName = ''
    let currentCategoryId = ''

    for (const position of positionList.value) {
      const category = categories.value.find(c => c.id === position.categoryId)
      if (category) {
        currentCategoryName = category.name
        currentCategoryId = category.id
      }

      exportData.push({
        '类别名称': currentCategoryName,
        '岗位名称': position.positionName,
        '岗位要求': position.requirements || '',
        '创建时间': formatDate(null, null, position.createTime || position.createdTime),
        '更新时间': formatDate(null, null, position.updateTime || position.updatedTime)
      })
    }

    const worksheet = XLSX.utils.json_to_sheet(exportData)
    const workbook = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(workbook, worksheet, '岗位类别数据')

    const fileName = `岗位类别数据_${new Date().getTime()}.xlsx`
    XLSX.writeFile(workbook, fileName)

    ElMessage.success('导出成功')
  } catch (error) {
    logger.error('导出Excel失败:', error)
    ElMessage.error('导出Excel失败: ' + (error.message || '未知错误'))
  } finally {
    exportLoading.value = false
  }
}
</script>

<style scoped>
.position-category-management-container {
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

/* 卡片通用样式 */
.search-card,
.actions-card,
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

.actions-card {
  padding: 20px 24px;
}

.table-card {
  padding: 0;
}

/* 搜索表单样式 */
.search-form {
  width: 100%;
}

.search-row {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.search-row .el-form-item {
  margin-bottom: 0;
  flex: 1;
}

.search-actions {
  flex: none;
  margin-left: auto;
}

.search-btn,
.reset-btn {
  border-radius: 8px;
  padding: 10px 20px;
}

/* 操作按钮区域 */
.actions-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.primary-actions,
.secondary-actions {
  display: flex;
  gap: 12px;
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

/* 表格容器布局 */
.tables-container {
  display: flex;
  gap: 20px;
  width: 100%;
}

.left-table {
  flex: 1;
  min-width: 400px;
}

.right-table {
  flex: 0 0 450px;
  margin-left: auto;
}

/* 表格头部样式 */
.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 20px 10px;
  border-bottom: 1px solid #f0f0f0;
}

.table-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.position-count {
  font-size: 14px;
  font-weight: normal;
  color: #909399;
}

.table-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.total-count {
  font-size: 14px;
  color: #909399;
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

/* 岗位名称样式 */
.position-name {
  color: #409EFF;
  cursor: pointer;
  font-weight: 500;
  transition: color 0.3s;
}

.position-name:hover {
  color: #66b1ff;
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

/* 分页样式 */
.pagination-container {
  padding: 20px;
  display: flex;
  justify-content: flex-end;
}

.custom-pagination {
  margin-top: 0;
}

.no-data {
  padding: 40px 20px;
  text-align: center;
}

/* 搜索结果分页 */
.search-pagination {
  margin-top: 10px;
  display: flex;
  justify-content: center;
  padding: 10px 15px;
  background-color: #f5f7fa;
  border-top: 1px solid #e6e6e6;
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

/* 导入对话框样式 */
.import-dialog-content {
  padding: 20px 0;
}

.import-tips {
  background-color: #f0f9ff;
  border: 1px solid #e6f7ff;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 20px;
}

.tip-text {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 14px;
  color: #409EFF;
}

.tip-text:last-child {
  margin-bottom: 0;
}

.tip-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.file-select-section {
  text-align: center;
  padding: 20px;
  border: 2px dashed #d9d9d9;
  border-radius: 8px;
  margin-bottom: 20px;
  transition: border-color 0.3s;
}

.file-select-section:hover {
  border-color: #667eea;
}

.choose-file-btn {
  border-radius: 6px;
  padding: 10px 24px;
}

.selected-file-info {
  margin-top: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
  color: #606266;
}

.no-file-selected {
  margin-top: 15px;
  font-size: 14px;
  color: #909399;
}

.import-note {
  font-size: 12px;
  color: #909399;
  text-align: center;
}

.import-note p {
  margin: 4px 0;
}

/* 响应式设计 */
@media screen and (max-width: 1200px) {
  .tables-container {
    flex-direction: column;
  }
  
  .right-table {
    flex: 1;
  }
  
  .search-row {
    flex-wrap: wrap;
  }
  
  .search-actions {
    margin-left: 0;
    width: 100%;
    justify-content: flex-end;
  }
}

@media screen and (max-width: 768px) {
  .position-category-management-container {
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
  
  .actions-container {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  
  .primary-actions,
  .secondary-actions {
    justify-content: center;
  }
  
  .action-buttons {
    flex-wrap: wrap;
    justify-content: center;
  }
  
  .search-row {
    flex-direction: column;
    gap: 12px;
  }
  
  .search-actions {
    width: 100%;
    justify-content: center;
  }
  
  .data-table {
    font-size: 12px;
  }
  
  .action-buttons {
    gap: 4px;
  }
  
  .table-btn {
    padding: 4px 6px;
  }
  
  .table-header {
    flex-direction: column;
    gap: 10px;
    align-items: flex-start;
  }
}
</style>