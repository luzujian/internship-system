<template>
  <div class="resource-management-container">
    <!-- 顶部标题区域 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">学习资源管理</h1>
        <p class="page-description">管理系统中的所有学习资源</p>
      </div>
      <div class="header-illustration">
        <div class="illustration-circle circle-1"></div>
        <div class="illustration-circle circle-2"></div>
      </div>
    </div>

    <!-- 搜索区域卡片 -->
    <el-card class="search-card" shadow="never">
      <el-form ref="searchFormRef" :inline="true" :model="searchForm" class="search-form" @keyup.enter="filterResources">
        <div class="search-row">
          <el-form-item label="资源标题">
            <el-input
              v-model="searchForm.title"
              placeholder="请输入资源标题"
              clearable
              @keyup.enter="filterResources"
            ></el-input>
          </el-form-item>
          <el-form-item label="资源类型">
            <el-select v-model="searchForm.fileType" placeholder="请选择资源类型" clearable style="width: 150px;" filterable>
              <el-option label="全部类型" value="" />
              <el-option label="文档" value="document" />
              <el-option label="视频" value="video" />
              <el-option label="代码" value="code" />
              <el-option label="数据集" value="dataset" />
            </el-select>
          </el-form-item>
          <el-form-item label="上传者">
            <el-input
              v-model="searchForm.uploader"
              placeholder="请输入上传者"
              clearable
              @keyup.enter="filterResources"
            ></el-input>
          </el-form-item>
          <el-form-item class="search-actions">
            <el-button type="primary" @click="filterResources" class="search-btn">
              <el-icon><Search /></el-icon>&nbsp;查询
            </el-button>
            <el-button type="warning" @click="clearSearch" class="reset-btn">
              <el-icon><Refresh /></el-icon>&nbsp;重置
            </el-button>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <!-- 操作按钮区域 -->
    <el-card class="actions-card" shadow="never">
      <div class="actions-container">
        <div class="primary-actions">
          <el-button type="primary" @click="uploadResource" class="action-btn primary">
            <el-icon><Plus /></el-icon>&nbsp;上传资源
          </el-button>
          <el-button type="danger" @click="batchDeleteResources" class="action-btn danger">
            <el-icon><Delete /></el-icon>&nbsp;批量删除
          </el-button>
        </div>
        <div class="secondary-actions">
          <el-button type="success" @click="refreshResources" class="action-btn success">
            <el-icon><Refresh /></el-icon>&nbsp;刷新列表
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 资源列表卡片区域 -->
    <el-card class="resources-card" shadow="never" v-loading="loading">
      <div class="resources-grid">
        <div 
          v-for="resource in resourcesList" 
          :key="resource.id" 
          class="resource-card"
          @click="viewResourceDetail(resource)"
        >
          <div class="resource-header">
            <div class="resource-type">
              <el-tag :type="getResourceTypeTag(resource.type)" size="small">
                {{ getResourceTypeText(resource.type) }}
              </el-tag>
            </div>
            <div class="resource-actions">
              <el-button type="primary" size="small" text @click.stop="editResource(resource)">
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-button type="danger" size="small" text @click.stop="deleteResource(resource.id)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          
          <div class="resource-content">
            <h3 class="resource-title" v-html="resource.title"></h3>
            <p class="resource-description" v-html="resource.description"></p>
          </div>
          
          <div class="resource-footer">
            <div class="resource-meta">
              <div class="meta-item">
                <el-icon><User /></el-icon>
                <span>{{ resource.uploader }}</span>
              </div>
              <div class="meta-item">
                <el-icon><Download /></el-icon>
                <span>{{ resource.downloadCount }}次下载</span>
              </div>
              <div class="meta-item">
                <el-icon><Clock /></el-icon>
                <span>{{ resource.uploadTime }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="resourcesList.length === 0 && !loading" class="empty-resources">
        <el-empty description="暂无资源数据" />
      </div>

      <!-- 分页组件 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[9, 18, 27, 36]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          class="custom-pagination"
        />
      </div>
    </el-card>

    <!-- 资源详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      :title="`资源详情 - ${selectedResource?.originalTitle || ''}`"
      width="600px"
      :close-on-click-modal="false"
      class="resource-detail-dialog"
    >
      <div v-if="selectedResource" class="resource-detail">
        <div class="detail-section">
          <h4>基本信息</h4>
          <div class="detail-grid">
            <div class="detail-item">
              <label>资源ID：</label>
              <span>{{ selectedResource.id }}</span>
            </div>
            <div class="detail-item">
              <label>资源类型：</label>
              <span>{{ getResourceTypeText(selectedResource.type) }}</span>
            </div>
            <div class="detail-item">
              <label>上传时间：</label>
              <span>{{ selectedResource.uploadTime }}</span>
            </div>
            <div class="detail-item">
              <label>上传者：</label>
              <span>{{ selectedResource.uploader }}</span>
            </div>
            <div class="detail-item">
              <label>下载次数：</label>
              <span>{{ selectedResource.downloadCount }}</span>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h4>资源描述</h4>
          <div class="description-content">
            {{ selectedResource.originalDescription || '暂无描述' }}
          </div>
        </div>

        <div v-if="selectedResource.content" class="detail-section">
          <h4>AI摘要</h4>
          <div class="ai-summary-content">
            {{ selectedResource.content }}
          </div>
        </div>
      </div>
      
      <template #footer>
        <el-button @click="detailDialogVisible = false" class="cancel-btn">关闭</el-button>
        <el-button type="primary" @click="downloadResource(selectedResource)" class="confirm-btn">下载资源</el-button>
      </template>
    </el-dialog>

    <!-- 上传资源对话框 -->
    <el-dialog
      v-model="uploadDialogVisible"
      title="上传资源"
      width="500px"
      class="upload-dialog"
    >
      <div class="upload-content">
        <el-alert
          title="支持上传文档、视频、代码、数据集等类型资源"
          type="info"
          :closable="false"
          style="margin-bottom: 20px;"
        />
        <el-form :model="uploadForm" :rules="uploadRules" ref="uploadFormRef" label-width="100px">
          <el-form-item label="资源标题" prop="title">
            <el-input v-model="uploadForm.title" placeholder="请输入资源标题" />
          </el-form-item>
          <el-form-item label="资源类型" prop="fileType">
            <el-select v-model="uploadForm.fileType" placeholder="请选择资源类型">
              <el-option label="文档" value="document" />
              <el-option label="视频" value="video" />
              <el-option label="代码" value="code" />
              <el-option label="数据集" value="dataset" />
            </el-select>
          </el-form-item>
          <el-form-item label="资源描述" prop="description">
            <el-input 
              v-model="uploadForm.description" 
              type="textarea" 
              :rows="3" 
              placeholder="请输入资源描述" 
            />
          </el-form-item>
          <el-form-item label="上传文件" prop="file">
            <el-upload
              class="upload-demo"
              drag
              action="#"
              :auto-upload="false"
              :on-change="handleFileChange"
              :file-list="fileList"
            >
              <el-icon class="el-icon--upload"><upload-filled /></el-icon>
              <div class="el-upload__text">
                将文件拖到此处，或<em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                  支持常见文件格式，单个文件不超过 100MB
                </div>
              </template>
            </el-upload>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="uploadDialogVisible = false" class="cancel-btn">取消</el-button>
        <el-button type="primary" @click="submitUpload" :loading="uploadLoading" class="confirm-btn">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { ref, onMounted, computed } from 'vue'
import type { ResourceDocument } from '@/types/admin'

import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete, Upload, Download, View, Edit, User, Clock } from '@element-plus/icons-vue'
import { UploadFilled } from '@element-plus/icons-vue'
import { resourceApi } from '@/api/resource'

// 响应式数据
const resourcesList = ref<unknown[]>([])
const total = ref<number>(0)
const currentPage = ref<number>(1)
const pageSize = ref(9)
const loading = ref<boolean>(false)

// 搜索表单
const searchForm = ref({
  title: '',
  fileType: '',
  uploader: ''
})

// 对话框控制
const detailDialogVisible = ref<boolean>(false)
const uploadDialogVisible = ref<boolean>(false)
const selectedResource = ref(null)

// 上传相关
const uploadForm = ref({
  title: '',
  fileType: '',
  description: '',
  file: null
})
const uploadFormRef = ref<InstanceType<typeof ElForm> | null>(null)
const fileList = ref<unknown[]>([])
const uploadLoading = ref<boolean>(false)

// 上传规则
const uploadRules = {
  title: [
    { required: true, message: '请输入资源标题', trigger: 'blur' }
  ],
  fileType: [
    { required: true, message: '请选择资源类型', trigger: 'change' }
  ],
  description: [
    { required: true, message: '请输入资源描述', trigger: 'blur' }
  ]
}

// API实例
const resourceApiInstance = resourceApi()

// 生命周期钩子
onMounted(() => {
  fetchResources()
})

// 获取资源类型标签
const getResourceTypeTag = (type) => {
  const typeMap = {
    'document': 'primary',
    'video': 'success',
    'code': 'warning',
    'dataset': 'info'
  }
  return typeMap[type] || 'info'
}

// 标准化资源类型
    const normalizeResourceType = (type) => {
      if (!type) return '未知';
      
      // 转换为字符串
      const typeStr = String(type).toLowerCase();
      
      // 常见的MIME类型和文件扩展名映射
      const mimeToTypeMap = {
        // 文档类型
        'application/pdf': 'document',
        'application/msword': 'document',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'document',
        'application/vnd.ms-powerpoint': 'document',
        'application/vnd.openxmlformats-officedocument.presentationml.presentation': 'document',
        'application/vnd.ms-excel': 'document',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'document',
        'text/plain': 'document',
        'text/html': 'document',
        // 视频类型
        'video/mp4': 'video',
        'video/avi': 'video',
        'video/mov': 'video',
        'video/wmv': 'video',
        // 代码类型
        'text/javascript': 'code',
        'text/css': 'code',
        'application/javascript': 'code',
        'application/json': 'code',
        'application/xml': 'code'
      };
      
      // 文件扩展名映射
      const extensionToTypeMap = {
        '.pdf': 'document',
        '.doc': 'document',
        '.docx': 'document',
        '.ppt': 'document',
        '.pptx': 'document',
        '.xls': 'document',
        '.xlsx': 'document',
        '.txt': 'document',
        '.html': 'document',
        '.mp4': 'video',
        '.avi': 'video',
        '.mov': 'video',
        '.wmv': 'video',
        '.java': 'code',
        '.py': 'code',
        '.js': 'code',
        '.css': 'code',
        '.xml': 'code',
        '.json': 'code'
      };
      
      // 如果已经是标准类型，直接返回
      if (['document', 'video', 'code', 'dataset'].includes(typeStr)) {
        return typeStr;
      }
      
      // 尝试直接MIME类型映射
      if (mimeToTypeMap[typeStr]) {
        return mimeToTypeMap[typeStr];
      }
      
      // 尝试文件扩展名映射
      for (const [ext, resourceType] of Object.entries(extensionToTypeMap)) {
        if (typeStr.endsWith(ext)) {
          return resourceType;
        }
      }
      
      // 尝试根据MIME类型前缀映射
      if (typeStr.startsWith('text/')) {
        return 'document';
      } else if (typeStr.startsWith('application/')) {
        return 'document';
      } else if (typeStr.startsWith('video/')) {
        return 'video';
      }
      
      // 尝试根据关键词映射（中文/英文混合情况）
      const keywordMap = {
        'pdf': 'document',
        'doc': 'document',
        'docx': 'document',
        'ppt': 'document',
        'pptx': 'document',
        'xls': 'document',
        'xlsx': 'document',
        'text': 'document',
        'word': 'document',
        'powerpoint': 'document',
        'excel': 'document',
        'mp4': 'video',
        'avi': 'video',
        'mov': 'video',
        'wmv': 'video',
        '视频': 'video',
        'document': 'document',
        '文档': 'document',
        'code': 'code',
        '代码': 'code',
        'dataset': 'dataset',
        '数据集': 'dataset'
      };
      
      for (const [keyword, resourceType] of Object.entries(keywordMap)) {
        if (typeStr.includes(keyword)) {
          return resourceType;
        }
      }
      
      // 不应该默认返回'document'，而应该尽量保留原始类型信息
      // 如果原始类型有值，返回原始类型的文本表示
      if (type && type !== '') {
        return String(type);
      }
      // 只有在类型完全为空时才返回未知
      return '未知';
    };
    
    // 获取资源类型文本
    const getResourceTypeText = (type) => {
      // 添加详细调试日志
      logger.log('LearningResource - getResourceTypeText - Received type:', type, typeof type)
      
      // 转换为字符串以处理数字类型
      const typeStr = String(type)
      logger.log('LearningResource - getResourceTypeText - String converted:', typeStr)
      
      const typeMap = {
        // 英文标准类型
        'document': '文档',
        'video': '视频',
        'code': '代码',
        'dataset': '数据集',
        // 添加可能的中文类型值
        '文档': '文档',
        '视频': '视频',
        '代码': '代码',
        '数据集': '数据集',
        // 添加可能的数字类型值
        '1': '文档',
        '2': '视频',
        '3': '代码',
        '4': '数据集',
        // 添加可能的未知类型映射
        '未知': '未知'
      }
      
      // 获取映射结果
      const result = typeMap[typeStr] || typeStr || '未知'
      logger.log('LearningResource - getResourceTypeText - Final result:', result)
      
      return result
    }

// 获取资源列表
    const fetchResources = async (): Promise<void> => {
      loading.value = true
      try {
        let response
        
        logger.log('发送请求参数:', {
          currentPage: currentPage.value,
          pageSize: pageSize.value,
          searchForm: searchForm.value
        })
        
        if (searchForm.value.title.trim() || searchForm.value.fileType || searchForm.value.uploader.trim()) {
          logger.log('执行AI搜索')
          response = await resourceApiInstance.aiSearchResources({
            query: searchForm.value.title,
            fileType: searchForm.value.fileType,
            uploader: searchForm.value.uploader,
            page: currentPage.value,
            pageSize: pageSize.value
          })
        } else {
          logger.log('获取已审核资源')
          response = await resourceApiInstance.getApprovedResources({
            page: currentPage.value,
            pageSize: pageSize.value
          })
        }
        
        logger.log('API响应数据结构:', response)
        
        // 处理返回的数据
        let allProcessedResources = []
        let totalCount = 0
        
        if (response.list) {
          logger.log('使用response.list，长度:', response.list.length)
          allProcessedResources = response.list.map(item => ({
            id: item.id,
            title: highlightKeyword(item.title, searchForm.value.title),
            originalTitle: item.title,
            type: normalizeResourceType(item.fileType || item.type || item.resourceType || item.file_category || item.category),
            description: highlightKeyword(item.description, searchForm.value.title),
            originalDescription: item.description,
            uploadTime: item.createdTime || item.uploadTime ? new Date(item.createdTime || item.uploadTime).toLocaleString('zh-CN') : '',
            uploader: item.uploaderRole || item.uploader || '系统管理员',
            downloadCount: item.downloadCount || 0,
            content: item.aiSummary
          }))
          totalCount = response.total || 0
          logger.log('设置totalCount为:', totalCount)
        } else if (Array.isArray(response)) {
          logger.log('直接使用response数组，长度:', response.length)
          allProcessedResources = response.map(item => ({
            id: item.id,
            title: highlightKeyword(item.title, searchForm.value.title),
            originalTitle: item.title,
            type: normalizeResourceType(item.fileType || item.type || item.resourceType || item.file_category || item.category),
            description: highlightKeyword(item.description, searchForm.value.title),
            originalDescription: item.description,
            uploadTime: item.createdTime || item.uploadTime ? new Date(item.createdTime || item.uploadTime).toLocaleString('zh-CN') : '',
            uploader: item.uploaderRole || item.uploader || '系统管理员',
            downloadCount: item.downloadCount || 0,
            content: item.aiSummary
          }))
          totalCount = allProcessedResources.length
          logger.log('设置totalCount为数组长度:', totalCount)
        }
        
        // 实现客户端分页逻辑，确保即使后端返回所有数据，前端也能正确分页显示
        const startIndex = (currentPage.value - 1) * pageSize.value
        const endIndex = startIndex + pageSize.value
        const paginatedResources = allProcessedResources.slice(startIndex, endIndex)
        
        resourcesList.value = paginatedResources
        total.value = totalCount
        logger.log('客户端分页后resourcesList长度:', resourcesList.value.length, '页码:', currentPage.value, '每页数量:', pageSize.value)
        logger.log('更新后total:', total.value)
      } catch (error) {
        logger.error('获取资源列表失败:', error)
        ElMessage.error('获取资源列表失败，请稍后重试')
      } finally {
        loading.value = false
      }
    }

// 高亮搜索关键词
const highlightKeyword = (text, keyword) => {
  if (!keyword || !text) return text
  const regex = new RegExp(`(${keyword})`, 'gi')
  return text.replace(regex, '<span class="highlight">$1</span>')
}

// 搜索和筛选资源
const filterResources = () => {
  currentPage.value = 1
  fetchResources()
}

// 清空搜索
const clearSearch = () => {
  searchForm.value = {
    title: '',
    fileType: '',
    uploader: ''
  }
  currentPage.value = 1
  fetchResources()
}

// 刷新资源列表
const refreshResources = () => {
  currentPage.value = 1
  fetchResources()
}

// 查看资源详情
const viewResourceDetail = async (resource): Promise<void> => {
  try {
    if (!resource || !resource.id) {
      ElMessage.warning('无法查看资源详情：无效的资源信息')
      return
    }
    
    const response = await resourceApiInstance.getResourceDetail(resource.id)
    
    if (response && response.data) {
      // 处理上传者信息：组合uploaderId和uploaderRole
      const uploaderInfo = response.data.uploaderRole ? 
        `${response.data.uploaderRole} ${response.data.uploaderId || ''}` : 
        (resource.uploader || '未知上传者')
      
      // 处理上传时间：优先使用createdTime，其次使用uploadTime
      const uploadTimeValue = response.data.createdTime ? 
        new Date(response.data.createdTime).toLocaleString('zh-CN') : 
        (resource.uploadTime || '')
      
      // 保留原始资源类型（列表页已处理为标准类型，与数据库一致）
      const rawTypes = {
        fileType: response.data.fileType,
        type: response.data.type,
        resourceType: response.data.resourceType,
        file_category: response.data.file_category,
        category: response.data.category,
        existingType: resource.type
      }
      logger.log('LearningResource - viewResourceDetail - Raw types collected:', rawTypes)
      
      // 重要：直接使用列表页中的原始type值，不再进行二次标准化处理
      // 这样可以确保与列表页和数据库保持一致
      const originalType = resource.type
      logger.log('LearningResource - viewResourceDetail - Using original type from list view:', originalType)
      
      const displayType = getResourceTypeText(originalType)
      logger.log('LearningResource - viewResourceDetail - Final display type:', displayType)
      
      selectedResource.value = {
        ...resource,
        ...response.data,
        type: originalType, // 保留原始类型值，不覆盖
        content: response.data.aiSummary || resource.content || '暂无详细内容',
        uploadTime: uploadTimeValue,
        uploader: uploaderInfo
      }
    } else {
      // 即使API调用失败，也要确保有上传信息
      selectedResource.value = { 
        ...resource,
        type: resource.type, // 直接使用原始类型值，不再进行二次标准化
        uploadTime: resource.uploadTime || '',
        uploader: resource.uploader || '未知上传者'
      }
    }
    
    detailDialogVisible.value = true
  } catch (error) {
    logger.error('获取资源详情失败:', error)
    ElMessage.error('获取资源详情失败，请稍后重试')
    // 错误情况下也要确保有上传信息
    selectedResource.value = { 
      ...resource,
      type: normalizeResourceType(resource.type),
      uploadTime: resource.uploadTime || '',
      uploader: resource.uploader || '未知上传者'
    }
    detailDialogVisible.value = true
  }
}

// 编辑资源
const editResource = (resource) => {
  ElMessage.info('编辑功能开发中')
}

// 删除资源
const deleteResource = async (resourceId): Promise<void> => {
  try {
    await ElMessageBox.confirm('确定要删除该资源吗？此操作不可撤销。', '警告', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await resourceApiInstance.deleteResource(resourceId)
    ElMessage.success('删除资源成功')
    fetchResources()
  } catch (error) {
    if (error !== 'cancel') {
      logger.error('删除资源失败:', error)
      ElMessage.error('删除资源失败，请稍后重试')
    }
  }
}

// 批量删除资源
const batchDeleteResources = () => {
  ElMessage.info('批量删除功能开发中')
}

// 上传资源
const uploadResource = () => {
  uploadForm.value = {
    title: '',
    fileType: '',
    description: '',
    file: null
  }
  fileList.value = []
  uploadDialogVisible.value = true
}

// 处理文件选择
const handleFileChange = (file) => {
  uploadForm.value.file = file.raw
}

// 提交上传
const submitUpload = async (): Promise<void> => {
  if (!uploadFormRef.value) return
  
  try {
    await uploadFormRef.value.validate()
    
    if (!uploadForm.value.file) {
      ElMessage.warning('请选择要上传的文件')
      return
    }
    
    uploadLoading.value = true
    
    // 构建表单数据
    const formData = new FormData()
    formData.append('title', uploadForm.value.title)
    formData.append('fileType', uploadForm.value.fileType)
    formData.append('description', uploadForm.value.description)
    formData.append('file', uploadForm.value.file)
    
    // 调用上传API
    await resourceApiInstance.uploadResource(formData)
    
    ElMessage.success('资源上传成功')
    uploadDialogVisible.value = false
    fetchResources()
  } catch (error) {
    if (error.name !== 'ValidationError') {
      logger.error('上传资源失败:', error)
      ElMessage.error('上传资源失败，请稍后重试')
    }
  } finally {
    uploadLoading.value = false
  }
}

// 下载资源
const downloadResource = (resource) => {
  ElMessage.info('下载功能开发中')
}

// 分页处理
// 测试验证点：
// 1. 切换pageSize时，列表应显示相应数量的条目
// 2. 切换页码时，应显示正确的数据范围
// 3. 搜索条件下分页也应正常工作
const handleSizeChange = (newSize) => {
  logger.log('切换每页数量为:', newSize)
  pageSize.value = newSize
  fetchResources()
}

const handleCurrentChange = (newCurrent) => {
  logger.log('切换页码为:', newCurrent)
  currentPage.value = newCurrent
  fetchResources()
}
</script>

<style scoped>
.resource-management-container {
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
.resources-card {
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

.resources-card {
  padding: 24px;
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

/* 资源网格布局 */
.resources-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
  margin-bottom: 24px;
}

/* 资源卡片样式 */
.resource-card {
  background: white;
  border: 1px solid #e6f7ff;
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.resource-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 25px rgba(64, 158, 255, 0.15);
  border-color: #409EFF;
}

.resource-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.resource-type {
  flex: 1;
}

.resource-actions {
  display: flex;
  gap: 4px;
}

.resource-content {
  margin-bottom: 16px;
}

.resource-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 12px 0;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.resource-description {
  font-size: 14px;
  color: #606266;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin: 0;
}

.resource-footer {
  border-top: 1px solid #f0f7ff;
  padding-top: 12px;
}

.resource-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
}

.meta-item .el-icon {
  font-size: 14px;
}

/* 空状态 */
.empty-resources {
  padding: 60px 0;
  text-align: center;
}

/* 分页样式 */
.pagination-container {
  display: flex;
  justify-content: flex-end;
}

.custom-pagination {
  margin-top: 0;
}

/* 对话框样式 */
.resource-detail-dialog,
.upload-dialog {
  border-radius: 16px;
  overflow: hidden;
}

.resource-detail-dialog :deep(.el-dialog__header),
.upload-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #f8fbff 0%, #e6f7ff 100%);
  margin: 0;
  padding: 20px;
  border-bottom: 1px solid #e6f7ff;
}

/* 资源详情样式 */
.resource-detail {
  padding: 10px 0;
}

.detail-section {
  margin-bottom: 24px;
}

.detail-section h4 {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 16px 0;
  padding-bottom: 8px;
  border-bottom: 2px solid #409EFF;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-item label {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

.detail-item span {
  font-size: 14px;
  color: #303133;
}

.description-content,
.ai-summary-content {
  background: #f8fafc;
  padding: 16px;
  border-radius: 8px;
  border-left: 4px solid #409EFF;
  line-height: 1.6;
  color: #606266;
}

.ai-summary-content {
  border-left-color: #52c41a;
  background: #f6ffed;
}

/* 上传表单样式 */
.upload-content {
  padding: 10px 0;
}

.upload-demo :deep(.el-upload) {
  width: 100%;
}

.upload-demo :deep(.el-upload-dragger) {
  width: 100%;
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

/* 高亮样式 */
.highlight {
  background-color: #fff3cd;
  color: #856404;
  padding: 0 2px;
  border-radius: 2px;
  font-weight: 500;
}

/* 响应式设计 */
@media screen and (max-width: 1200px) {
  .resources-grid {
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
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
  .resource-management-container {
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
  
  .resources-grid {
    grid-template-columns: 1fr;
  }
  
  .search-row {
    flex-direction: column;
    gap: 12px;
  }
  
  .search-actions {
    width: 100%;
    justify-content: center;
  }
  
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>