<template>
  <div class="resource-document-management-container">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">资源文档管理</h1>
        <p class="page-description">管理系统中的所有资源文档信息</p>
      </div>
    </div>

    <el-card class="search-card" shadow="never">
      <el-form ref="searchFormRef" :inline="true" :model="searchForm" class="search-form" @keyup.enter="handleSearch">
        <div class="search-row">
          <el-form-item label="标题">
            <el-input
              v-model="searchForm.title"
              placeholder="请输入文档标题"
              clearable
              style="width: 200px;"
              @keyup.enter="handleSearch"
            ></el-input>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="searchForm.status" placeholder="请选择状态" clearable style="width: 120px;">
              <el-option label="全部" value=""></el-option>
              <el-option label="草稿" value="DRAFT"></el-option>
              <el-option label="已发布" value="PUBLISHED"></el-option>
              <el-option label="已归档" value="ARCHIVED"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="发布人身份">
            <el-select v-model="searchForm.publisherRole" placeholder="请选择发布人身份" clearable style="width: 120px;">
              <el-option label="全部" value=""></el-option>
              <el-option label="管理员" value="ADMIN"></el-option>
              <el-option label="教师" value="TEACHER"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item class="search-actions">
            <el-button type="primary" @click="handleSearch" class="search-btn">
              <el-icon><Search /></el-icon>&nbsp;查询
            </el-button>
            <el-button type="warning" @click="resetForm" class="reset-btn">
              <el-icon><Refresh /></el-icon>&nbsp;重置
            </el-button>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <el-card class="actions-card" shadow="never">
      <div class="actions-container">
        <div class="primary-actions">
          <el-button type="primary" @click="handleAdd" class="action-btn primary">
            <el-icon><Plus /></el-icon>&nbsp;发布文档
          </el-button>
          <el-button type="danger" @click="handleBatchDelete" class="action-btn danger">
            <el-icon><Delete /></el-icon>&nbsp;批量删除
          </el-button>
        </div>
        <div class="secondary-actions">
          <el-radio-group v-model="filterType" @change="handleFilterChange" class="filter-radio-group">
            <el-radio-button value="all">全部资源</el-radio-button>
            <el-radio-button value="mine">我的资源</el-radio-button>
          </el-radio-group>
          <el-button type="success" @click="refreshData" class="action-btn success">
            <el-icon><Refresh /></el-icon>&nbsp;刷新列表
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card class="resources-card" shadow="never" v-loading="loading">
      <div class="resources-grid">
        <div 
          v-for="resource in resourcesList" 
          :key="resource.id" 
          class="resource-card"
          :class="{ 'selected': selectedIds.includes(resource.id) }"
          @click="viewResourceDetail(resource)"
        >
          <div class="resource-checkbox-wrapper">
            <el-checkbox
              :model-value="selectedIds.includes(resource.id)"
              @change="(val) => handleCheckboxChange(resource.id, val)"
              @click.stop
              class="resource-checkbox"
            ></el-checkbox>
          </div>
          
          <div class="resource-header">
            <div class="resource-type">
              <el-tag :type="getStatusTagType(resource.status)" size="small">
                {{ getStatusText(resource.status) }}
              </el-tag>
              <el-tag :type="getFileTypeTagType(resource.fileType)" size="small" style="margin-left: 8px;">
                {{ getFileTypeText(resource.fileType) }}
              </el-tag>
            </div>
            <div class="resource-actions">
              <el-button v-if="isMyResource(resource)" type="primary" size="small" text @click.stop="editResource(resource)">
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-button v-if="isMyResource(resource)" type="danger" size="small" text @click.stop="deleteResource(resource.id)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          
          <div class="resource-content">
            <h3 class="resource-title" v-html="highlightKeyword(resource.title, searchForm.title)"></h3>
            <p class="resource-description" v-html="highlightKeyword(resource.description, searchForm.title)"></p>
          </div>
          
          <div class="resource-footer">
            <div class="resource-meta">
              <div class="meta-item">
                <el-icon><User /></el-icon>
                <span>{{ resource.publisher }}</span>
                <el-tag :type="getPublisherRoleType(resource.publisherRole)" size="small" style="margin-left: 4px;">
                  {{ getPublisherRoleText(resource.publisherRole) }}
                </el-tag>
              </div>
              <div class="meta-item">
                <el-icon><Download /></el-icon>
                <span>{{ resource.downloadCount || 0 }}次下载</span>
              </div>
              <div class="meta-item">
                <el-icon><View /></el-icon>
                <span>{{ resource.viewCount || 0 }}次浏览</span>
              </div>
              <div class="meta-item">
                <el-icon><Clock /></el-icon>
                <span>{{ formatDate(resource.publishTime) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="resourcesList.length === 0 && !loading" class="empty-resources">
        <el-empty description="暂无资源文档数据" />
      </div>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[12, 24, 36, 48]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          class="custom-pagination"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailDialogVisible" :title="`资源文档详情 - ${selectedResource?.title || ''}`" width="700px" :close-on-click-modal="false" class="resource-detail-dialog">
      <div v-if="selectedResource" class="resource-detail">
        <div class="detail-section">
          <h4>基本信息</h4>
          <div class="detail-grid">
            <div class="detail-item">
              <label>文档ID：</label>
              <span>{{ selectedResource.id }}</span>
            </div>
            <div class="detail-item">
              <label>文件名：</label>
              <span>{{ selectedResource.fileName }}</span>
            </div>
            <div class="detail-item">
              <label>文件类型：</label>
              <el-tag size="small">{{ getFileTypeText(selectedResource.fileType) }}</el-tag>
            </div>
            <div class="detail-item">
              <label>文件大小：</label>
              <span>{{ formatFileSize(selectedResource.fileSize) }}</span>
            </div>
            <div class="detail-item">
              <label>状态：</label>
              <el-tag :type="getStatusTagType(selectedResource.status)" size="small">
                {{ getStatusText(selectedResource.status) }}
              </el-tag>
            </div>
            <div class="detail-item">
              <label>发布人：</label>
              <span>{{ selectedResource.publisher }}</span>
            </div>
            <div class="detail-item">
              <label>发布人身份：</label>
              <el-tag :type="getPublisherRoleType(selectedResource.publisherRole)" size="small">
                {{ getPublisherRoleText(selectedResource.publisherRole) }}
              </el-tag>
            </div>
            <div class="detail-item">
              <label>目标群体：</label>
              <span>{{ getTargetTypeText(selectedResource.targetType) }}</span>
            </div>
            <div class="detail-item">
              <label>发布时间：</label>
              <span>{{ formatDate(selectedResource.publishTime) }}</span>
            </div>
            <div class="detail-item">
              <label>下载次数：</label>
              <span>{{ selectedResource.downloadCount || 0 }}</span>
            </div>
            <div class="detail-item">
              <label>浏览次数：</label>
              <span>{{ selectedResource.viewCount || 0 }}</span>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h4>描述</h4>
          <div class="description-content">
            {{ selectedResource.description || '暂无描述' }}
          </div>
        </div>

        <div class="detail-section">
          <h4>文件操作</h4>
          <div class="file-actions">
            <el-button type="primary" @click="previewResource(selectedResource)" class="action-btn">
              <el-icon><ZoomIn /></el-icon>
              预览文件
            </el-button>
            <el-button type="success" @click="downloadResource(selectedResource)" class="action-btn">
              <el-icon><Download /></el-icon>
              下载文件
            </el-button>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="detailDialogVisible = false" class="cancel-btn">关闭</el-button>
      </template>
    </el-dialog>

    <FilePreviewDialog
      v-model="filePreviewVisible"
      :file-url="currentFileUrl"
      :file-name="currentFileName"
    />

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px" @close="handleDialogClose" class="form-dialog">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px" class="resource-document-form">
        <el-form-item label="标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入文档标题"></el-input>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="4" placeholder="请输入文档描述"></el-input>
        </el-form-item>
        <el-form-item label="文件">
          <el-upload
            v-model:file-list="fileList"
            :action="uploadAction"
            :headers="uploadHeaders"
            :on-success="handleUploadSuccess"
            :on-remove="handleRemoveFile"
            :before-upload="beforeUpload"
            :limit="1"
            :on-exceed="handleExceed"
            :auto-upload="true"
            class="compact-upload"
          >
            <el-button type="primary" size="small">
              <el-icon><Upload /></el-icon>
              选择文件
            </el-button>
            <template #tip>
              <div class="el-upload__tip">
                支持 PDF、Word、Excel、PPT、TXT、ZIP、RAR、7Z 等格式，单个文件≤10MB
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="formData.status" placeholder="请选择文档状态">
            <el-option label="草稿" value="DRAFT"></el-option>
            <el-option label="发布" value="PUBLISHED"></el-option>
            <el-option label="归档" value="ARCHIVED"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="发布人身份" prop="publisherRole">
          <el-select v-model="formData.publisherRole" placeholder="请选择发布人身份" disabled>
            <el-option label="学院教师" value="COLLEGE"></el-option>
            <el-option label="系室教师" value="DEPARTMENT"></el-option>
            <el-option label="辅导员" value="COUNSELOR"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="发布人" prop="publisher">
          <el-input v-model="formData.publisher" placeholder="发布人" disabled></el-input>
        </el-form-item>
        <el-form-item label="目标群体">
          <el-select v-model="formData.targetType" placeholder="请选择目标群体">
            <el-option label="全体师生" value="ALL"></el-option>
            <el-option label="全体学生" value="STUDENT"></el-option>
            <el-option label="全体教师" value="TEACHER"></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false" class="cancel-btn">取消</el-button>
          <el-button type="primary" @click="handleSubmit" :loading="submitLoading" class="confirm-btn">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { ref, reactive, onMounted, computed } from 'vue'
import type { ResourceDocument } from '@/types/admin'

import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete, Edit, View, Download, User, Clock, ZoomIn } from '@element-plus/icons-vue'
import * as resourceDocumentApi from '../../api/resourceDocument'
import { useAuthStore } from '../../store/auth'
import FilePreviewDialog from '@/components/FilePreviewDialog.vue'

const authStore = useAuthStore()

const currentPage = ref<number>(1)
const pageSize = ref(12)
const total = ref<number>(0)
const loading = ref<boolean>(false)
const resourcesList = ref<unknown[]>([])
const selectedIds = ref<unknown[]>([])
const filterType = ref<string>('all')

const searchForm = reactive({ title: '', status: '', publisherRole: '' })

const dialogVisible = ref<boolean>(false)
const dialogTitle = ref('发布文档')
const formRef = ref()
const submitLoading = ref<boolean>(false)
const formData = reactive({
  id: null as number | null,
  title: '',
  description: '',
  file: null,
  status: 'PUBLISHED',
  publisher: '',
  publisherRole: '',
  targetType: 'ALL'
})

const fileList = ref<unknown[]>([])

const detailDialogVisible = ref<boolean>(false)
const selectedResource = ref<ResourceDocument | null>(null)

const filePreviewVisible = ref<boolean>(false)
const currentFileUrl = ref<string>('')
const currentFileName = ref<string>('')
const originalFileUrl = ref<string>('')

const uploadAction = '/api/upload/file'
const uploadHeaders = computed(() => ({
  'Authorization': `Bearer ${authStore.token}`
}))

const currentUserId = computed(() => {
  return authStore.user?.id ? parseInt(authStore.user.id) : null
})

const currentUserTeacherType = computed(() => {
  return localStorage.getItem('teacherType') || 'TEACHER'
})

onMounted(() => {
  initFormData()
  fetchData()
})

const initFormData = () => {
  formData.publisher = authStore.user?.name || authStore.user?.username || ''
  const teacherType = localStorage.getItem('teacherType')
  if (teacherType === 'COLLEGE') {
    formData.publisherRole = 'COLLEGE'
  } else if (teacherType === 'DEPARTMENT') {
    formData.publisherRole = 'DEPARTMENT'
  } else if (teacherType === 'COUNSELOR') {
    formData.publisherRole = 'COUNSELOR'
  } else {
    formData.publisherRole = 'TEACHER'
  }
}

const fetchData = async (): Promise<void> => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      pageSize: pageSize.value,
      title: searchForm.title || undefined,
      status: searchForm.status || undefined,
      publisherRole: searchForm.publisherRole || undefined,
      filterType: filterType.value
    }
    logger.log('教师端资源文档查询参数:', params)
    const response = await resourceDocumentApi.getTeacherResourceDocumentsByPage(params)
    logger.log('教师端资源文档响应:', response)
    
    const result = response
    if (result && result.code === 200 && result.data) {
      resourcesList.value = result.data.rows || []
      total.value = result.data.total || 0
    } else if (result && Array.isArray(result.data)) {
      resourcesList.value = result.data
      total.value = result.data.length || 0
    } else {
      resourcesList.value = []
      total.value = 0
      if (result && result.msg) {
        ElMessage.error(result.msg)
      }
    }
    logger.log('教师端资源文档列表:', resourcesList.value, '总数:', total.value)
  } catch (error) {
    ElMessage.error('获取资源文档列表失败')
    logger.error('获取资源文档列表失败:', error)
    resourcesList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchData()
}

const resetForm = () => {
  searchForm.title = ''
  searchForm.status = ''
  searchForm.publisherRole = ''
  currentPage.value = 1
  fetchData()
}

const handleSizeChange = (val: number) => {
  pageSize.value = val
  currentPage.value = 1
  fetchData()
}

const handleCurrentChange = (val: number) => {
  currentPage.value = val
  fetchData()
}

const handleFilterChange = () => {
  currentPage.value = 1
  fetchData()
}

const refreshData = () => {
  fetchData()
}

const handleCheckboxChange = (id: number, checked: boolean) => {
  const resource = resourcesList.value.find(r => (r as ResourceDocument).id === id)
  if (resource && !isMyResource(resource as ResourceDocument)) {
    ElMessage.warning('只能选择自己上传的资源文档')
    return
  }

  if (checked) {
    if (!selectedIds.value.includes(id)) {
      selectedIds.value.push(id)
    }
  } else {
    const index = selectedIds.value.indexOf(id)
    if (index > -1) {
      selectedIds.value.splice(index, 1)
    }
  }
}

const isMyResource = (resource: ResourceDocument): boolean => {
  return resource.publisherId === currentUserId.value
}

const highlightKeyword = (text: string, keyword: string): string => {
  if (!keyword || !text) return text
  const regex = new RegExp(`(${keyword})`, 'gi')
  return text.replace(regex, '<span style="background-color: #ffeb3b;">$1</span>')
}

const getStatusText = (status: string): string => {
  const statusMap: Record<string, string> = {
    'DRAFT': '草稿',
    'PUBLISHED': '已发布',
    'ARCHIVED': '已归档'
  }
  return statusMap[status] || status
}

const getStatusTagType = (status: string): string => {
  const typeMap: Record<string, string> = {
    'DRAFT': 'info',
    'PUBLISHED': 'success',
    'ARCHIVED': 'warning'
  }
  return typeMap[status] || 'info'
}

const getPublisherRoleText = (role: string): string => {
  const roleMap: Record<string, string> = {
    'ADMIN': '管理员',
    'TEACHER': '教师',
    'COLLEGE': '学院教师',
    'DEPARTMENT': '系室教师',
    'COUNSELOR': '辅导员'
  }
  return roleMap[role] || role
}

const getPublisherRoleType = (role: string): string => {
  const typeMap: Record<string, string> = {
    'ADMIN': 'danger',
    'TEACHER': 'primary',
    'COLLEGE': 'primary',
    'DEPARTMENT': 'success',
    'COUNSELOR': 'warning'
  }
  return typeMap[role] || 'info'
}

const getTargetTypeText = (type: string): string => {
  const typeMap: Record<string, string> = {
    'ALL': '全体师生',
    'STUDENT': '全体学生',
    'TEACHER': '全体教师'
  }
  return typeMap[type] || type
}

const getFileTypeText = (fileType: string): string => {
  if (!fileType) return '未知'
  
  const lowerType = fileType.toLowerCase()
  
  if (lowerType.includes('pdf')) return 'PDF'
  if (lowerType.includes('powerpoint') || lowerType.includes('presentation')) return 'PPT'
  if (lowerType.includes('excel') || lowerType.includes('spreadsheet')) return 'Excel'
  if (lowerType.includes('word')) return 'Word'
  if (lowerType.includes('text')) return 'TXT'
  if (lowerType.includes('zip') || lowerType.includes('rar') || lowerType.includes('7z')) return '压缩包'
  if (lowerType.includes('image')) return '图片'
  return '其他'
}

const getFileTypeTagType = (fileType: string): string => {
  if (!fileType) return 'info'
  const lowerType = fileType.toLowerCase()
  
  if (lowerType.includes('pdf')) return 'danger'
  if (lowerType.includes('powerpoint') || lowerType.includes('presentation')) return 'warning'
  if (lowerType.includes('excel') || lowerType.includes('spreadsheet')) return 'success'
  if (lowerType.includes('word')) return 'primary'
  if (lowerType.includes('text')) return 'info'
  if (lowerType.includes('zip') || lowerType.includes('rar') || lowerType.includes('7z')) return 'info'
  if (lowerType.includes('image')) return 'success'
  return 'info'
}

const formatDate = (date: string | Date): string => {
  if (!date) return ''
  const d = new Date(date)
  return d.toLocaleString('zh-CN')
}

const formatFileSize = (bytes: number): string => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}

const viewResourceDetail = (resource: ResourceDocument) => {
  selectedResource.value = resource
  detailDialogVisible.value = true
}

const editResource = async (resource: ResourceDocument): Promise<void> => {
  if (!isMyResource(resource)) {
    ElMessage.warning('您只能编辑自己上传的资源文档')
    return
  }
  
  dialogTitle.value = '编辑文档'
  formData.id = resource.id
  formData.title = resource.title
  formData.description = resource.description || ''
  formData.file = null
  formData.status = resource.status || 'PUBLISHED'
  formData.publisherRole = resource.publisherRole || currentUserTeacherType.value
  formData.targetType = resource.targetType || 'ALL'
  formData.publisher = resource.publisher || authStore.user?.name || ''
  fileList.value = []
  originalFileUrl.value = ''
  
  if (resource.fileUrl) {
    originalFileUrl.value = resource.fileUrl
    fileList.value = [{
      name: resource.fileName || '未知文件',
      url: resource.fileUrl,
      status: 'success'
    }]
  }
  
  dialogVisible.value = true
}

const deleteResource = async (id: number): Promise<void> => {
  try {
    await ElMessageBox.confirm('确定要删除该资源文档吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const response = await resourceDocumentApi.teacherDeleteResourceDocument(id)
    const result = response
    if (result && result.code === 200) {
      ElMessage.success('删除成功')
      fetchData()
    } else {
      ElMessage.error(result?.msg || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
      logger.error('删除失败:', error)
    }
  }
}

const downloadResource = async (resource: ResourceDocument): Promise<void> => {
  try {
    // 响应拦截器已返回 Blob
    const blob = await resourceDocumentApi.downloadResourceDocument(resource.id!)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', resource.fileName || 'download')
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('下载成功')
  } catch (error) {
    ElMessage.error('下载失败')
    logger.error('下载失败:', error)
  }
}

const previewResource = (resource: ResourceDocument) => {
  if (!resource) {
    ElMessage.error('资源信息不存在')
    return
  }

  const fileUrl = resource.fileUrl
  logger.log('[TeacherResources] previewResource called with fileUrl:', fileUrl)

  if (!fileUrl) {
    ElMessage.error('文件地址不存在')
    return
  }

  let proxyUrl: string
  if (fileUrl.startsWith('http://') || fileUrl.startsWith('https://')) {
    try {
      const urlObj = new URL(fileUrl)
      const ossPath = urlObj.pathname.substring(1)
      proxyUrl = `/api/upload/preview/${ossPath}`
      logger.log('[TeacherResources] 使用 OSS URL 转换后的代理 URL:', proxyUrl)
    } catch (e) {
      logger.error('解析 OSS URL 失败:', e)
      proxyUrl = fileUrl
    }
  } else {
    const cleanPath = fileUrl.startsWith('/') ? fileUrl.substring(1) : fileUrl
    proxyUrl = `/api/upload/preview/${cleanPath}`
    logger.log('[TeacherResources] 使用相对路径的代理 URL:', proxyUrl)
  }

  currentFileUrl.value = proxyUrl
  currentFileName.value = resource.fileName || '未知文件'
  filePreviewVisible.value = true
}

const handleBatchDelete = async (): Promise<void> => {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请选择要删除的资源文档')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除选中的${selectedIds.value.length}个资源文档吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const response = await resourceDocumentApi.teacherBatchDeleteResourceDocuments(selectedIds.value as number[])
    const result = response
    if (result && result.code === 200) {
      ElMessage.success('批量删除成功')
      selectedIds.value = []
      fetchData()
    } else {
      ElMessage.error(result?.msg || '批量删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('批量删除失败')
      logger.error('批量删除失败:', error)
    }
  }
}

const handleAdd = () => {
  dialogTitle.value = '发布文档'
  resetFormData()
  dialogVisible.value = true
}

const resetFormData = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  formData.id = null
  formData.title = ''
  formData.description = ''
  formData.file = null
  formData.status = 'PUBLISHED'
  formData.targetType = 'ALL'
  initFormData()
  fileList.value = []
  originalFileUrl.value = ''
}

const beforeUpload = (file: File) => {
  const allowedTypes = [
    'application/pdf',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'application/vnd.ms-excel',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'application/vnd.ms-powerpoint',
    'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    'text/plain',
    'application/zip',
    'application/x-rar-compressed',
    'application/x-7z-compressed',
    'image/jpeg',
    'image/jpg',
    'image/png',
    'image/gif',
    'image/bmp',
    'image/webp'
  ]
  
  const isAllowedType = allowedTypes.includes(file.type) || file.type.startsWith('image/')
  const isLt10M = file.size / 1024 / 1024 < 10
  
  if (!isAllowedType) {
    ElMessage.error('不支持的文件格式！')
    return false
  }
  if (!isLt10M) {
    ElMessage.error('文件大小不能超过 10MB！')
    return false
  }
  return true
}

const handleExceed = () => {
  ElMessage.warning('最多只能上传 1 个文件')
}

const handleUploadSuccess = (response: { code: number; data?: string | { url?: string }; msg?: string }, uploadFile: { uid: number; name: string; url?: string; raw?: { type?: string; size?: number }; response?: { url: string; filename: string; type: string; size: number } }) => {
  if (response.code === 200) {
    ElMessage.success('文件上传成功')
    const fileUrl = typeof response.data === 'string' ? response.data : response.data?.url
    uploadFile.url = fileUrl
    uploadFile.name = uploadFile.name || '未知文件'
    uploadFile.response = {
      url: fileUrl || '',
      filename: uploadFile.name,
      type: uploadFile.raw?.type || '',
      size: uploadFile.raw?.size || 0
    }
  } else {
    ElMessage.error(response.msg || '文件上传失败')
    const index = fileList.value.findIndex((f: { uid: number }) => f.uid === uploadFile.uid)
    if (index > -1) {
      fileList.value.splice(index, 1)
    }
  }
}

const handleRemoveFile = (uploadFile: { url?: string }) => {
  if (uploadFile.url && uploadFile.url === originalFileUrl.value) {
    originalFileUrl.value = ''
  }
}

const rules = {
  title: [
    { required: true, message: '请输入文档标题', trigger: 'blur' }
  ],
  publisher: [
    { required: true, message: '请输入发布人', trigger: 'blur' }
  ],
  publisherRole: [
    { required: true, message: '请选择发布人身份', trigger: 'change' }
  ],
  status: [
    { required: true, message: '请选择文档状态', trigger: 'change' }
  ]
}

const handleSubmit = async (): Promise<void> => {
  if (!formData.title) {
    ElMessage.warning('请输入文档标题')
    return
  }
  if (!formData.publisher) {
    ElMessage.warning('请输入发布人')
    return
  }
  if (!formData.publisherRole) {
    ElMessage.warning('请选择发布人身份')
    return
  }
  if (dialogTitle.value === '发布文档' && fileList.value.length === 0) {
    ElMessage.warning('请选择文件')
    return
  }

  // 编辑文档时，如果没有上传新文件，可以只更新元数据
  if (dialogTitle.value === '编辑文档' && fileList.value.length === 0 && !originalFileUrl.value) {
    ElMessage.warning('请上传新文件或确保已有文件')
    return
  }

  submitLoading.value = true
  try {
    if (dialogTitle.value === '发布文档') {
      const formDataObj = new FormData()
      const fileItem = fileList.value[0] as { raw?: File }
      formDataObj.append('file', fileItem?.raw as File)
      formDataObj.append('title', formData.title)
      formDataObj.append('description', formData.description || '')
      formDataObj.append('publisher', formData.publisher)
      formDataObj.append('publisherRole', formData.publisherRole)
      formDataObj.append('targetType', formData.targetType)
      formDataObj.append('status', formData.status)

      const response = await resourceDocumentApi.teacherUploadResourceDocument(formDataObj)
      const result = response
      if (result && result.code === 200) {
        ElMessage.success('发布成功')
        dialogVisible.value = false
        fetchData()
      } else {
        ElMessage.error(result?.msg || '发布失败')
      }
    } else {
      const newFile = fileList.value[0] as { name: string; url?: string; response?: { type?: string; size?: number }; raw?: { type?: string; size?: number } }
      const hasNewFile = newFile && newFile.url && newFile.url !== originalFileUrl.value

      if (hasNewFile) {
        const updateData = {
          id: formData.id,
          title: formData.title,
          description: formData.description,
          fileUrl: newFile.url,
          fileName: newFile.name,
          fileType: newFile.response?.type || newFile.raw?.type || '',
          fileSize: newFile.response?.size || newFile.raw?.size || 0,
          status: formData.status,
          publisher: formData.publisher,
          publisherRole: formData.publisherRole,
          targetType: formData.targetType
        }

        const result = await resourceDocumentApi.teacherUpdateResourceDocument(formData.id!, updateData)
        if (result && result.code === 200) {
          ElMessage.success('更新成功')
          dialogVisible.value = false
          fetchData()
        } else {
          ElMessage.error(result?.msg || '更新失败')
        }
      } else {
        // 没有上传新文件，只更新元数据
        const updateData = {
          id: formData.id,
          title: formData.title,
          description: formData.description,
          status: formData.status,
          publisher: formData.publisher,
          publisherRole: formData.publisherRole,
          targetType: formData.targetType
        }

        const result = await resourceDocumentApi.teacherUpdateResourceDocument(formData.id!, updateData)
        if (result && result.code === 200) {
          ElMessage.success('更新成功')
          dialogVisible.value = false
          fetchData()
        } else {
          ElMessage.error(result?.msg || '更新失败')
        }
      }
    }
  } catch (error) {
    ElMessage.error(dialogTitle.value === '发布文档' ? '发布失败' : '更新失败')
    logger.error('操作失败:', error)
  } finally {
    submitLoading.value = false
  }
}

const handleDialogClose = () => {
  resetFormData()
  originalFileUrl.value = ''
  fileList.value = []
}
</script>

<style scoped>
.resource-document-management-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-content {
  z-index: 1;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px 0;
}

.page-description {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

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
  padding: 20px 24px;
}

.search-form {
  margin: 0;
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
  margin-bottom: 0;
}

.search-btn,
.reset-btn {
  border-radius: 8px;
  padding: 10px 20px;
}

.actions-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.primary-actions,
.secondary-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.filter-radio-group {
  margin-right: 12px;
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

.action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.resources-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.resource-card {
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.resource-checkbox-wrapper {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 10;
}

.resource-checkbox {
  padding: 4px;
}

.resource-card:hover {
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
  transform: translateY(-4px);
  border-color: #409EFF;
}

.resource-card.selected {
  border-color: #409EFF;
  background-color: #f0f7ff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2);
}

.resource-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-left: 32px;
}

.resource-type {
  display: flex;
  align-items: center;
  gap: 8px;
}

.resource-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.resource-content {
  margin-bottom: 16px;
}

.resource-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px 0;
  line-height: 1.4;
}

.resource-description {
  font-size: 13px;
  color: #606266;
  margin: 0;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.resource-footer {
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
}

.resource-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 12px;
  color: #909399;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.empty-resources {
  padding: 60px 0;
  text-align: center;
}

.pagination-container {
  padding: 20px 0 0 0;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid #f0f0f0;
}

.custom-pagination {
  display: flex;
  align-items: center;
  margin-top: 0;
}

.resource-detail-dialog .resource-detail {
  padding: 0;
}

.detail-section {
  margin-bottom: 24px;
}

.detail-section h4 {
  color: #303133;
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 16px 0;
  padding-bottom: 8px;
  border-bottom: 2px solid #667eea;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.detail-item {
  display: flex;
  align-items: center;
  font-size: 14px;
}

.detail-item label {
  color: #909399;
  margin-right: 8px;
  min-width: 80px;
}

.detail-item span {
  color: #303133;
  font-weight: 500;
}

.description-content {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 6px;
  color: #606266;
  line-height: 1.6;
  font-size: 14px;
}

.file-actions {
  display: flex;
  gap: 12px;
}

.form-dialog,
.resource-detail-dialog,
.preview-dialog {
  border-radius: 16px;
  overflow: hidden;
}

.form-dialog :deep(.el-dialog__header),
.resource-detail-dialog :deep(.el-dialog__header),
.preview-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #f8fbff 0%, #e6f7ff 100%);
  margin: 0;
  padding: 20px;
  border-bottom: 1px solid #e6f7ff;
}

.resource-document-form {
  padding: 20px 0;
}

.compact-upload :deep(.el-upload-list) {
  margin-top: 8px;
}

.compact-upload :deep(.el-upload-list__item) {
  margin-bottom: 6px;
  padding: 8px 12px;
  border-radius: 4px;
  background: #f5f7fa;
  transition: all 0.3s ease;
}

.compact-upload :deep(.el-upload-list__item:hover) {
  background: #ecf5ff;
}

.compact-upload :deep(.el-upload-list__item-name) {
  font-size: 13px;
  color: #606266;
}

.compact-upload :deep(.el-icon--close) {
  font-size: 14px;
}

.compact-upload :deep(.el-upload__tip) {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
  line-height: 1.5;
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

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media screen and (max-width: 1200px) {
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
  .resource-document-management-container {
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

  .primary-actions {
    justify-content: center;
  }

  .search-row {
    flex-direction: column;
  }

  .search-row .el-form-item {
    width: 100%;
  }

  .resources-grid {
    grid-template-columns: 1fr;
  }
}
</style>
