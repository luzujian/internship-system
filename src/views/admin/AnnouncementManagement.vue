<template>
  <div class="announcement-management-container">
    <!-- 顶部标题区域 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">公告管理</h1>
        <p class="page-description">管理系统中的所有公告信息</p>
      </div>
      <div class="header-illustration">
        <div class="illustration-circle circle-1"></div>
        <div class="illustration-circle circle-2"></div>
      </div>
    </div>

    <!-- 搜索区域卡片 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" class="search-form" @keyup.enter="handleSearch">
        <div class="search-row">
          <el-form-item label="标题">
            <el-input
              v-model="searchForm.title"
              placeholder="请输入公告标题"
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
              <el-option label="已过期" value="EXPIRED"></el-option>
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

    <!-- 操作按钮区域 -->
    <el-card class="actions-card" shadow="never">
      <div class="actions-container">
        <div class="primary-actions">
          <el-button v-if="authStore.hasPermission('announcement:add')" type="primary" @click="handleAdd" class="action-btn primary">
            <el-icon><Plus /></el-icon>&nbsp;发布公告
          </el-button>
          <el-button v-if="authStore.hasPermission('announcement:delete')" type="danger" @click="handleBatchDelete" class="action-btn danger">
            <el-icon><Delete /></el-icon>&nbsp;批量删除
          </el-button>
        </div>
        <div class="secondary-actions">
                    <el-dropdown v-if="authStore.hasPermission('announcement:view')" trigger="click" class="import-dropdown">
            <el-button type="primary" class="action-btn warning">
              <el-icon><Upload /></el-icon>&nbsp;导入 Excel<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="showImportDialog">选择文件导入</el-dropdown-item>
                <el-dropdown-item @click="downloadTemplate">下载导入模板</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button v-if="authStore.hasPermission('announcement:view')" type="success" @click="handleExport" class="action-btn success">
            <el-icon><Download /></el-icon>&nbsp;导出Excel
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 数据列表卡片 -->
    <el-card class="table-card" shadow="never">
      <el-table 
        v-loading="loading" 
        :data="tableData" 
        style="width: 100%" 
        @selection-change="handleSelectionChange" 
        border
        fit
        class="data-table"
      >
        <el-table-column type="selection" width="55" align="center"></el-table-column>
        <el-table-column prop="id" label="ID" width="80" align="center"></el-table-column>
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip></el-table-column>
        <el-table-column prop="priority" label="优先级" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.priority === 'important'" type="warning" size="small">重要</el-tag>
            <el-tag v-else type="info" size="small">普通</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getTagType(scope.row.status)" size="small" class="status-tag">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publisher" label="发布人" width="120" align="center"></el-table-column>
        <el-table-column label="发布人身份" width="120" align="center">
          <template #default="scope">
            <el-tag :type="getPublisherRoleType(scope.row.publisherRole)" size="small" class="publisher-role-tag">
              {{ getPublisherRoleText(scope.row.publisherRole) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标群体" width="150" align="center">
          <template #default="scope">
            {{ getTargetText(scope.row.targetType, scope.row.targetValue) }}
          </template>
        </el-table-column>
        <el-table-column prop="publishDate" label="发布日期" width="170" align="center">
          <template #default="scope">{{ formatDate(scope.row.publishDate || scope.row.publishTime) }}</template>
        </el-table-column>
        <el-table-column prop="expireDate" label="过期日期" width="170" align="center">
          <template #default="scope">{{ formatDate(scope.row.expireDate || scope.row.validTo) }}</template>
        </el-table-column>
        <el-table-column prop="readCount" label="阅读量" width="80" align="center"></el-table-column>
        <el-table-column label="操作" width="240" fixed="right" align="center">
          <template #default="scope">
            <div class="action-buttons">
              <el-tooltip v-if="authStore.hasPermission('announcement:view')" content="查看" placement="top">
                <el-button size="small" type="success" @click="handleView(scope.row)" class="table-btn view">
                  <el-icon><View /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('announcement:edit')" content="编辑" placement="top">
                <el-button size="small" type="primary" @click="handleEdit(scope.row)" class="table-btn edit">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('announcement:delete')" content="删除" placement="top">
                <el-button size="small" type="danger" @click="handleDelete(scope.row.id)" class="table-btn delete">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页组件 -->
      <div class="pagination-container">
        <el-pagination
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          :current-page="currentPage"
          :page-sizes="[10, 20, 50]"
          :page-size="pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          class="custom-pagination"
        />
      </div>
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="800px" @close="handleDialogClose" @open="handleDialogOpen" class="form-dialog">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px" class="announcement-form">
        <el-form-item label="标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入公告标题"></el-input>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="formData.content" type="textarea" :rows="8" placeholder="请输入公告内容"></el-input>
        </el-form-item>
        <el-form-item label="附件">
          <el-upload
            v-model:file-list="formData.attachments"
            :action="uploadAction"
            :headers="uploadHeaders"
            :on-success="handleUploadSuccess"
            :on-remove="handleRemoveFile"
            :before-upload="beforeUpload"
            :limit="5"
            :on-exceed="handleExceed"
            multiple
            :auto-upload="true"
            class="compact-upload"
          >
            <el-button type="primary" size="small">
              <el-icon><Upload /></el-icon>
              选择文件
            </el-button>
            <template #tip>
              <div class="el-upload__tip">
                支持PDF、Word、Excel、PPT、TXT、ZIP等格式，单个文件≤10MB，最多5个
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="formData.status" placeholder="请选择公告状态">
            <el-option label="草稿" value="DRAFT"></el-option>
            <el-option label="发布" value="PUBLISHED"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="发布日期" prop="publishDate">
          <el-date-picker v-model="formData.publishDate" type="datetime" placeholder="选择发布日期" style="width: 100%"></el-date-picker>
        </el-form-item>
        <el-form-item label="过期日期" prop="expireDate">
          <el-date-picker v-model="formData.expireDate" type="datetime" placeholder="选择过期日期" style="width: 100%"></el-date-picker>
        </el-form-item>
        <el-form-item label="发布人身份" prop="publisherRole">
          <el-select v-model="formData.publisherRole" placeholder="请选择发布人身份" @change="handlePublisherRoleChange">
            <el-option label="管理员" value="ADMIN"></el-option>
            <el-option label="学院教师" value="COLLEGE"></el-option>
            <el-option label="系室教师" value="DEPARTMENT"></el-option>
            <el-option label="辅导员" value="COUNSELOR"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="发布人" prop="publisher">
          <el-select 
            v-model="formData.publisher" 
            placeholder="请选择或输入发布人姓名" 
            :loading="userListLoading" 
            :disabled="!formData.publisherRole"
            filterable
            allow-create
            clearable
          >
            <el-option v-for="user in userList" :key="user.id" :label="user.name" :value="user.name"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="formData.priority" placeholder="请选择优先级">
            <el-option label="普通" value="normal"></el-option>
            <el-option label="重要" value="important"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="目标群体">
          <el-select v-model="formData.targetType" placeholder="请选择目标群体">
            <el-option label="全体师生" value="ALL"></el-option>
            <el-option label="全体学生" value="STUDENT"></el-option>
            <el-option label="全体教师" value="TEACHER"></el-option>
            <el-option label="特定教师类别" value="TEACHER_TYPE"></el-option>
            <el-option label="特定专业学生" value="MAJOR"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="formData.targetType === 'TEACHER_TYPE'" label="教师类别">
          <el-select v-model="formData.targetValue" placeholder="请选择教师类别">
            <el-option label="学院教师" value="COLLEGE"></el-option>
            <el-option label="系室教师" value="DEPARTMENT"></el-option>
            <el-option label="辅导员" value="COUNSELOR"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="formData.targetType === 'MAJOR'" label="专业">
          <el-select v-model="formData.targetValue" placeholder="请选择专业">
            <el-option v-for="major in majorList" :key="major.id" :label="major.name" :value="String(major.id)"></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false" class="cancel-btn">取消</el-button>
          <el-button type="primary" @click="handleSubmit" class="confirm-btn">确定</el-button>
        </span>
      </template>
    </el-dialog>
    
    <!-- 查看对话框 -->
    <el-dialog v-model="viewDialogVisible" title="查看公告" width="800px" class="view-dialog">
      <div v-if="viewData" class="view-content">
        <h3 class="view-title">{{ viewData.title }}</h3>
        <div class="view-meta">
          <div class="meta-item">
            <span class="meta-label">发布人:</span>
            <span class="meta-value">{{ viewData.publisher }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">发布人身份:</span>
            <el-tag :type="getPublisherRoleType(viewData.publisherRole)" size="small" class="status-tag">
              {{ getPublisherRoleText(viewData.publisherRole) }}
            </el-tag>
          </div>
          <div class="meta-item">
            <span class="meta-label">目标群体:</span>
            <span class="meta-value">{{ getTargetText(viewData.targetType, viewData.targetValue) }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">发布日期:</span>
            <span class="meta-value">{{ formatDate(viewData.publishDate || viewData.publishTime) }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">过期日期:</span>
            <span class="meta-value">{{ formatDate(viewData.expireDate || viewData.validTo) }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">状态:</span>
            <el-tag :type="getTagType(viewData.status)" size="small" class="status-tag">
              {{ getStatusText(viewData.status) }}
            </el-tag>
          </div>
          <div class="meta-item">
          <span class="meta-label">阅读次数:</span>
          <span class="meta-value">{{ viewData.readCount || 0 }}</span>
        </div>
      </div>
      <el-divider></el-divider>
      <div class="content-body" v-html="viewData.content"></div>
      <div v-if="viewData.attachments && parseAttachments(viewData.attachments).length > 0" class="attachments-section">
        <el-divider></el-divider>
        <div class="attachments-title">
          <el-icon><Paperclip /></el-icon>
          <span>附件列表</span>
        </div>
        <div class="attachments-list">
          <div
            v-for="(attachment, index) in parseAttachments(viewData.attachments)"
            :key="index"
            class="attachment-item"
          >
            <div class="attachment-info" @click="downloadFileWithConfirm(attachment)">
              <el-icon class="attachment-icon"><Document /></el-icon>
              <span class="attachment-name">{{ attachment.name }}</span>
            </div>
            <div class="attachment-actions">
              <el-button
                type="primary"
                size="small"
                @click.stop="previewFile(attachment)"
                title="预览文件"
              >
                <el-icon><ZoomIn /></el-icon>
                预览
              </el-button>
              <el-button
                type="success"
                size="small"
                @click.stop="downloadFileWithConfirm(attachment)"
                title="下载文件"
              >
                <el-icon><Download /></el-icon>
                下载
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
      <template #footer>
        <el-button @click="viewDialogVisible = false" class="cancel-btn">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 文件预览对话框 -->
    <FilePreviewDialog
      v-model="filePreviewVisible"
      :file-url="currentFileUrl"
      :file-name="currentFileName"
    />
    
    <!-- 导入 Excel对话框 -->
    <el-dialog v-model="importDialogVisible" title="导入 Excel公告数据" width="400px" class="import-dialog">
      <div class="import-content">
        <div class="file-selector">
          <el-button type="primary" @click="handleChooseFile" class="file-btn">
            选择Excel文件
          </el-button>
          <input 
            ref="excelFileInput"
            type="file" 
            accept=".xlsx,.xls" 
            style="display: none;" 
            @change="handleNativeFileChange"
          >
          <div v-if="selectedFile" class="file-info">
            已选择文件: <span class="file-name">{{ selectedFile.name }}</span>
          </div>
        </div>
        <div class="import-tip">
          提示：请确保Excel文件包含标题、内容、发布人、发布人身份、状态、优先级、目标群体、目标值等必要字段
        </div>
      </div>
      <template #footer>
        <el-button @click="closeImportDialog" class="cancel-btn">取消</el-button>
        <el-button type="primary" @click="importExcel" :loading="importLoading" class="confirm-btn">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import type { Announcement, Major, PaginationState } from '@/types/admin'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete, Edit, View, Upload, Download, Document, Paperclip, ZoomIn, ArrowDown } from '@element-plus/icons-vue'
import * as announcementApi from '../../api/announcement'
import MajorService from '../../api/major'
import request from '../../utils/request'
import { useAuthStore } from '../../store/auth'
import FilePreviewDialog from '@/components/FilePreviewDialog.vue'

const authStore = useAuthStore()

// 文件上传配置
const uploadAction = '/api/upload/file'
const uploadHeaders = computed(() => ({
  'Authorization': `Bearer ${authStore.token}`
}))

// 分页相关
const currentPage = ref<number>(1)
const pageSize = ref<number>(10)
const total = ref<number>(0)
const loading = ref(false)
const tableData = ref<Announcement[]>([])

// 搜索表单
const searchForm = reactive({ title: '', status: '' })

// 专业列表
const majorList = ref<Major[]>([])

// 用户列表
const userList = ref<{ id: string; name: string }[]>([])
const userListLoading = ref(false)

// 新增/编辑对话框
const dialogVisible = ref(false)
const dialogTitle = ref('新增公告')
const formRef = ref()
const formData = reactive({
  id: null,
  title: '',
  content: '',
  status: 'DRAFT',
  publisher: '',
  publisherRole: '',
  publishDate: null,
  expireDate: null,
  priority: 'normal',
  targetType: 'ALL',
  targetValue: null,
  attachments: []
})

// 表单验证规则
const rules = {
  title: [
    { required: true, message: '请输入公告标题', trigger: 'blur' },
    { min: 2, max: 200, message: '标题长度在2-200个字符', trigger: 'blur' }
  ],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }],
  status: [{ required: true, message: '请选择公告状态', trigger: 'change' }],
  publisher: [{ required: true, message: '请选择发布人姓名', trigger: 'change' }],
  publisherRole: [{ required: true, message: '请选择发布人身份', trigger: 'change' }]
}

// 查看对话框
const viewDialogVisible = ref(false)
const viewData = ref<Announcement | null>(null)

// 文件预览相关
const filePreviewVisible = ref(false)
const currentFileUrl = ref('')
const currentFileName = ref('')

// 选中的ID列表
const selectedIds = ref<(string | number)[]>([])

// 初始化
onMounted(() => {
  fetchData()
  loadMajors()
})

// 加载专业列表
const loadMajors = async (): Promise<void> => {
  try {
    const response = await MajorService.getMajors()
    logger.log('专业列表响应:', response)
    // 处理响应数据 - axios返回的是{data: {code, data, msg}}
    const result = response.data
    if (result && result.code === 200 && result.data) {
      majorList.value = result.data
    } else if (result && Array.isArray(result)) {
      // 兼容直接返回数组的情况
      majorList.value = result
    } else {
      majorList.value = []
    }
  } catch (error) {
    logger.error('加载专业列表失败:', error)
    majorList.value = []
  }
}

// 获取数据
const fetchData = async (): Promise<void> => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      pageSize: pageSize.value,
      title: searchForm.title || undefined,
      status: searchForm.status || undefined
    }
    const response = await announcementApi.getAnnouncementsByPage(params)
    logger.log('公告列表响应:', response)
    // 处理响应数据 - axios返回的是{data: {code, data, msg}}
    const result = response.data
    if (result && result.code === 200 && result.data) {
      tableData.value = result.data.rows || []
      total.value = result.data.total || 0
    } else {
      tableData.value = []
      total.value = 0
      if (result && result.msg) {
        ElMessage.error(result.msg)
      }
    }
  } catch (error) {
    ElMessage.error('获取公告列表失败')
    logger.error('获取公告列表失败:', error)
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = (): void => {
  currentPage.value = 1
  fetchData()
}

// 重置表单
const resetForm = (): void => {
  searchForm.title = ''
  searchForm.status = ''
  currentPage.value = 1
  fetchData()
}

// 分页
const handleSizeChange = (val: number): void => {
  pageSize.value = val
  currentPage.value = 1
  fetchData()
}

const handleCurrentChange = (val: number): void => {
  currentPage.value = val
  fetchData()
}

// 选择变化
const handleSelectionChange = (val: Announcement[]): void => {
  selectedIds.value = val.map(item => item.id)
}

// 重置表单数据
const resetFormData = (): void => {
  formData.id = null
  formData.title = ''
  formData.content = ''
  formData.status = 'DRAFT'
  formData.publisher = ''
  formData.publisherRole = ''
  formData.publishDate = null
  formData.expireDate = null
  formData.priority = 'normal'
  formData.targetType = 'ALL'
  formData.targetValue = null
  formData.attachments = []
  userList.value = []
}

// 发布人身份变化处理
const handlePublisherRoleChange = async (role: string): Promise<void> => {
  if (!role) {
    userList.value = []
    formData.publisher = ''
    return
  }
  
  userListLoading.value = true
  try {
    const response = await announcementApi.getUsersByPublisherRole(role)
    logger.log('用户列表响应:', response)
    const result = response.data
    if (result && result.code === 200 && result.data) {
      userList.value = result.data
      formData.publisher = ''
    } else {
      userList.value = []
      formData.publisher = ''
      ElMessage.error(result?.msg || '获取用户列表失败')
    }
  } catch (error) {
    logger.error('获取用户列表失败:', error)
    userList.value = []
    formData.publisher = ''
    ElMessage.error('获取用户列表失败')
  } finally {
    userListLoading.value = false
  }
}

// 新增公告
const handleAdd = (): void => {
  dialogTitle.value = '新增公告'
  resetFormData()
  dialogVisible.value = true
}

// 对话框打开
const handleDialogOpen = (): void => {
  if (formRef.value) {
    formRef.value.clearValidate()
  }
}

// 编辑公告
const handleEdit = async (row: Announcement): Promise<void> => {
  dialogTitle.value = '编辑公告'
  formData.id = row.id
  formData.title = row.title
  formData.content = row.content
  formData.status = row.status
  formData.publisher = row.publisher
  formData.publisherRole = row.publisherRole || ''
  formData.publishDate = row.publishDate || row.publishTime ? new Date(row.publishDate || row.publishTime) : null
  formData.expireDate = row.expireDate || row.validTo ? new Date(row.expireDate || row.validTo) : null
  formData.priority = row.priority || 'normal'
  formData.targetType = row.targetType || 'ALL'
  formData.targetValue = row.targetValue
  
  // 解析并回显附件
  if (row.attachments) {
    try {
      const attachments = parseAttachments(row.attachments)
      formData.attachments = attachments.map(attachment => ({
        name: attachment.name,
        url: attachment.url,
        size: attachment.size || 0,
        status: 'success'
      }))
    } catch (e) {
      logger.error('解析附件失败:', e)
      formData.attachments = []
    }
  } else {
    formData.attachments = []
  }
  
  // 如果有发布人身份，加载对应的用户列表
  if (formData.publisherRole) {
    await handlePublisherRoleChange(formData.publisherRole)
    // 重新设置发布人姓名，因为handlePublisherRoleChange会清空它
    formData.publisher = row.publisher
  }
  
  dialogVisible.value = true
  nextTick(() => {
    if (formRef.value) {
      formRef.value.clearValidate()
    }
  })
}

// 查看公告
const handleView = async (row: Announcement): Promise<void> => {
  try {
    const response = await announcementApi.getAnnouncementById(row.id)
    logger.log('公告详情响应:', response)
    const result = response.data
    if (result && result.code === 200 && result.data) {
      viewData.value = result.data
      viewDialogVisible.value = true
    } else {
      ElMessage.error(result?.msg || '获取公告详情失败')
    }
  } catch (error) {
    ElMessage.error('获取公告详情失败')
    logger.error('获取公告详情失败:', error)
  }
}

// 预览文件
const previewFile = (attachment: { url: string | unknown; name?: string | unknown }): void => {
  logger.log('[AnnouncementManagement] previewFile called with attachment:', attachment)
  logger.log('[AnnouncementManagement] attachment.url type:', typeof attachment.url, 'value:', attachment.url)
  logger.log('[AnnouncementManagement] attachment.name type:', typeof attachment.name, 'value:', attachment.name)

  if (!attachment) {
    ElMessage.error('附件信息不存在')
    return
  }

  // 安全地转换为字符串
  const url = attachment.url ? String(attachment.url) : null
  const name = attachment.name ? String(attachment.name) : '附件'

  logger.log('[AnnouncementManagement] processed url:', url, 'name:', name)

  if (!url) {
    ElMessage.error('附件地址不存在')
    return
  }

  currentFileUrl.value = url
  currentFileName.value = name
  logger.log('[AnnouncementManagement] setting filePreviewVisible to true')
  filePreviewVisible.value = true
}

// 下载文件（带确认）
const downloadFileWithConfirm = (attachment: { url?: string | unknown; name?: string | unknown }): void => {
  if (!attachment) {
    ElMessage.error('附件信息不存在')
    return
  }

  const url = attachment.url ? String(attachment.url) : null
  const name = attachment.name ? String(attachment.name) : '附件'

  if (!url) {
    ElMessage.error('附件地址不存在')
    return
  }

  ElMessageBox.confirm(`确定要下载文件 "${name}" 吗？`, '下载确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info'
  }).then(() => {
    downloadFile(url, name)
  }).catch(() => {
    // 用户取消下载
  })
}

// 下载文件
const downloadFile = (url: string, name: string): void => {
  try {
    const link = document.createElement('a')
    link.href = url
    link.download = name || 'download'
    link.target = '_blank'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    ElMessage.success('开始下载')
  } catch (error) {
    logger.error('下载失败:', error)
    ElMessage.error('下载失败')
  }
}

// 删除公告
const handleDelete = (id: string | number): Promise<void> => {
  ElMessageBox.confirm('确定要删除该公告吗？', '确认删除', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    try {
      const response = await announcementApi.deleteAnnouncement(id)
      const result = response.data
      if (result && result.code === 200) {
        ElMessage.success('删除成功')
        fetchData()
      } else {
        ElMessage.error(result?.msg || '删除失败')
      }
    } catch (error) {
      ElMessage.error('删除失败')
      logger.error('删除公告失败:', error)
    }
  }).catch(() => {})
}

// 批量删除
const handleBatchDelete = (): Promise<void> => {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请选择要删除的公告')
    return
  }
  ElMessageBox.confirm(`确定要删除选中的 ${selectedIds.value.length} 个公告吗？`, '确认批量删除', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    try {
      const response = await announcementApi.batchDeleteAnnouncements(selectedIds.value)
      const result = response.data
      if (result && result.code === 200) {
        ElMessage.success('批量删除成功')
        selectedIds.value = []
        fetchData()
      } else {
        ElMessage.error(result?.msg || '批量删除失败')
      }
    } catch (error) {
      ElMessage.error('批量删除失败')
      logger.error('批量删除公告失败:', error)
    }
  }).catch(() => {})
}

// 导入 Excel相关状态
const importDialogVisible = ref(false)
const importLoading = ref(false)
const selectedFile = ref<File | null>(null)
const excelFileInput = ref<HTMLInputElement | null>(null)

// 显示导入 Excel对话框
const showImportDialog = (): void => {
  selectedFile.value = null
  importDialogVisible.value = true
}

// 处理文件选择按钮点击
const handleChooseFile = (): void => {
  if (excelFileInput.value) {
    excelFileInput.value.click()
  } else {
    logger.warn('excelFileInput is not available yet')
  }
}

// 处理原生input文件选择
const handleNativeFileChange = (e: Event): void => {
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
    logger.log('已选择文件:', file.name)
  }
}

// 关闭导入对话框
const closeImportDialog = (): void => {
  importDialogVisible.value = false
  selectedFile.value = null
  if (excelFileInput.value) {
    excelFileInput.value.value = ''
  }
}

// 导入 Excel数据
const importExcel = async (): Promise<void> => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择Excel文件')
    return
  }

  importLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    
    const response = await announcementApi.importAnnouncements(formData)
    
    if (response && response.code === 200) {
      const importResult = response.data || {}
      const successCount = importResult.successCount || 0
      const failCount = importResult.failCount || 0
      const failList = importResult.failList || []
      
      let message = `导入完成，成功: ${successCount} 条，失败: ${failCount} 条`
      
      if (failList.length > 0) {
        let errorDetails = '\n失败详情:\n'
        failList.forEach((fail, index) => {
          errorDetails += `${index + 1}. 第${fail.rowNum}行: ${fail.errorMsg || '未知错误'}\n`
        })
        
        ElMessageBox.alert(message + errorDetails, '导入结果', {
          type: failCount > 0 ? 'warning' : 'success',
          confirmButtonText: '确定',
          dangerouslyUseHTMLString: false,
          callback: () => {
            importDialogVisible.value = false
            fetchData()
          }
        })
      } else {
        ElMessage.success(message)
        importDialogVisible.value = false
        fetchData()
      }
    } else {
      ElMessage.error(response?.msg || '导入失败')
    }
  } catch (error) {
    logger.error('导入 Excel数据失败:', error)
    const errorMsg = error.response?.data?.message || error.response?.data?.msg || error.message || '未知错误'
    ElMessage.error('导入 Excel数据失败: ' + errorMsg)
  } finally {
    importLoading.value = false
  }
}

// 导出Excel
const handleExport = async (): Promise<void> => {
  try {
    ElMessage({ message: '正在准备导出数据...', type: 'info' })
    
    const params = {
      title: searchForm.title || undefined,
      status: searchForm.status || undefined
    }
    
    const response = await announcementApi.exportAnnouncements(params)
    
    const blob = new Blob([response.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    
    const currentDate = new Date()
    const year = currentDate.getFullYear()
    const month = String(currentDate.getMonth() + 1).padStart(2, '0')
    const day = String(currentDate.getDate()).padStart(2, '0')
    const hours = String(currentDate.getHours()).padStart(2, '0')
    const minutes = String(currentDate.getMinutes()).padStart(2, '0')
    const seconds = String(currentDate.getSeconds()).padStart(2, '0')
    const fileName = `公告数据导出_${year}${month}${day}_${hours}${minutes}${seconds}.xlsx`
    
    link.download = fileName
    link.click()
    
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (error) {
    logger.error('导出Excel失败:', error)
    ElMessage.error('导出Excel失败: ' + (error.message || '未知错误'))
  }
}

// 提交表单
const handleSubmit = async (): Promise<void> => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        const submitData = {
          title: formData.title,
          content: formData.content,
          status: formData.status,
          publisher: formData.publisher,
          publisherRole: formData.publisherRole,
          priority: formData.priority,
          validFrom: formData.publishDate,
          validTo: formData.expireDate,
          targetType: formData.targetType,
          targetValue: formData.targetValue
        }
        
        // 处理附件 - 始终提交当前附件列表（包括空数组表示删除所有附件）
        submitData.attachments = (formData.attachments || []).map(file => ({
          name: file.name,
          url: file.url,
          size: file.size,
          type: file.raw ? file.raw.type : (file.response ? file.response.type : '')
        }))
        
        let response
        if (formData.id) {
          // 编辑时始终使用带附件的接口，以便正确处理附件的增删
          response = await announcementApi.updateAnnouncementWithAttachments(formData.id, submitData)
        } else {
          response = await announcementApi.addAnnouncementWithAttachments(submitData)
        }
        
        const result = response.data
        if (result && result.code === 200) {
          ElMessage.success(formData.id ? '更新成功' : '新增成功')
          dialogVisible.value = false
          fetchData()
        } else {
          ElMessage.error(result?.msg || (formData.id ? '更新失败' : '新增失败'))
        }
      } catch (error) {
        ElMessage.error(formData.id ? '更新失败' : '新增失败')
        logger.error('保存公告失败:', error)
      }
    }
  })
}

// 对话框关闭
const handleDialogClose = (): void => {
  if (formRef.value) {
    formRef.value.clearValidate()
    formRef.value.resetFields()
  }
  formData.attachments = []
}

// 文件上传前的校验
const beforeUpload = (file: File): boolean => {
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
    'image/gif'
  ]
  
  const isAllowedType = allowedTypes.includes(file.type)
  const isLt10M = file.size / 1024 / 1024 < 10
  
  if (!isAllowedType) {
    ElMessage.error('不支持的文件格式！')
    return false
  }
  if (!isLt10M) {
    ElMessage.error('文件大小不能超过10MB！')
    return false
  }
  return true
}

// 文件上传成功
const handleUploadSuccess = (response: { code: number; data?: { url: string; filename: string }; msg?: string }, uploadFile: { uid?: string | number; url: string; name: string; response?: unknown }, uploadFiles: unknown[]): void => {
  if (response.code === 200) {
    ElMessage.success('文件上传成功')
    uploadFile.url = response.data.url
    uploadFile.name = response.data.filename
    uploadFile.response = response.data
  } else {
    ElMessage.error(response.msg || '文件上传失败')
    const index = formData.attachments.findIndex(f => f.uid === uploadFile.uid)
    if (index > -1) {
      formData.attachments.splice(index, 1)
    }
  }
}

// 移除文件
const handleRemoveFile = (uploadFile: { uid?: string | number }, uploadFiles: unknown[]): void => {
  const index = formData.attachments.findIndex(f => f.uid === uploadFile.uid)
  if (index > -1) {
    formData.attachments.splice(index, 1)
  }
}

// 超出限制
const handleExceed = (files: File[], uploadFiles: unknown[]): void => {
  ElMessage.warning(`最多只能上传5个文件，当前已选择${files.length}个文件`)
}

// 解析附件JSON字符串
const parseAttachments = (attachments: string | unknown[]): unknown[] => {
  if (!attachments) return []
  try {
    if (typeof attachments === 'string') {
      return JSON.parse(attachments)
    }
    return attachments
  } catch (e) {
    logger.error('解析附件数据失败:', e)
    return []
  }
}

// 下载附件
const downloadAttachment = async (attachment: { url: string }): Promise<void> => {
  if (!attachment.url) {
    ElMessage.error('附件地址不存在')
    return
  }
  
  try {
    const response = await announcementApi.downloadAnnouncementAttachment(viewData.value.id, attachment.name)
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', attachment.name)
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

// 获取状态文本
const getStatusText = (status: string): string => {
  const map = { DRAFT: '草稿', PUBLISHED: '已发布', EXPIRED: '已过期' }
  return map[status] || status
}

// 获取标签类型
const getTagType = (status: string): string => {
  const map = { DRAFT: 'info', PUBLISHED: 'success', EXPIRED: 'warning' }
  return map[status] || 'info'
}

// 格式化日期
const formatDate = (dateString: string | number | Date): string => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  if (isNaN(date.getTime())) return '-'
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const min = String(date.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${min}`
}

// 获取发布人身份文本
const getPublisherRoleText = (role: string): string => {
  const map = {
    ADMIN: '管理员',
    COLLEGE: '学院教师',
    DEPARTMENT: '系室教师',
    COUNSELOR: '辅导员'
  }
  return map[role] || '-'
}

// 获取发布人身份标签类型
const getPublisherRoleType = (role: string): string => {
  const map = {
    ADMIN: 'danger',
    COLLEGE: 'primary',
    DEPARTMENT: 'success',
    COUNSELOR: 'warning'
  }
  return map[role] || 'info'
}

// 获取目标群体文本
const getTargetText = (targetType: string, targetValue: string | number): string => {
  if (!targetType) return '-'
  
  const typeMap = {
    ALL: '全体师生',
    STUDENT: '全体学生',
    TEACHER: '全体教师',
    TEACHER_TYPE: '特定教师类别',
    MAJOR: '特定专业学生'
  }
  
  const typeText = typeMap[targetType] || targetType
  
  if (targetType === 'TEACHER_TYPE' && targetValue) {
    const teacherTypeMap = {
      COLLEGE: '学院教师',
      DEPARTMENT: '系室教师',
      COUNSELOR: '辅导员'
    }
    return `${typeText}（${teacherTypeMap[targetValue] || targetValue}）`
  }
  
  if (targetType === 'MAJOR' && targetValue) {
    const major = majorList.value.find(m => String(m.id) === String(targetValue))
    if (major) {
      return `${typeText}（${major.name}）`
    }
  }
  
  return typeText
}

// 下载导入模板
const downloadTemplate = async (): Promise<void> => {
  try {
    const response = await request.get('/admin/announcements/import-template', {
      responseType: 'blob'
    })

    // 创建下载链接
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', '公告导入模板.xlsx')
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

</script>

<style scoped>
.announcement-management-container {
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

.primary-actions {
  display: flex;
  gap: 12px;
}

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

.action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
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

/* 发布人信息样式 */
.publisher-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.publisher-name {
  font-weight: 500;
  color: #303133;
}

.publisher-role-tag {
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

/* 分页样式 */
.pagination-container {
  padding: 20px;
  display: flex;
  justify-content: flex-end;
}

.custom-pagination {
  margin-top: 0;
}

/* 对话框样式 */
.form-dialog,
.view-dialog {
  border-radius: 16px;
  overflow: hidden;
}

.form-dialog :deep(.el-dialog__header),
.view-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #f8fbff 0%, #e6f7ff 100%);
  margin: 0;
  padding: 20px;
  border-bottom: 1px solid #e6f7ff;
}

.announcement-form {
  padding: 20px 0;
}

/* 查看对话框样式 */
.view-content {
  padding: 10px 0;
}

.view-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 20px;
  text-align: center;
}

.view-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 20px;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 8px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.meta-label {
  color: #606266;
  font-size: 14px;
  font-weight: 500;
}

.meta-value {
  color: #303133;
  font-size: 14px;
}

.content-body {
  padding: 20px;
  line-height: 1.8;
  color: #606266;
  background: white;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  min-height: 200px;
  white-space: pre-wrap;
}

/* 导入对话框样式 */
.import-content {
  padding: 20px 0;
}

.file-selector {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}

.file-btn {
  border-radius: 8px;
}

.file-info {
  margin-top: 10px;
  color: #606266;
}

.file-name {
  color: #409EFF;
  font-weight: 500;
}

.import-tip {
  color: #909399;
  font-size: 12px;
  margin-top: 16px;
}

/* 紧凑型上传组件样式 */
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

/* 附件样式 */
.attachments-section {
  margin-top: 20px;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 8px;
}

.attachments-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #303133;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 12px;
}

.attachments-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.attachment-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f8f9fa;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  transition: all 0.3s ease;
}

.attachment-item:hover {
  border-color: #409EFF;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
}

.attachment-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  cursor: pointer;
  min-width: 0;
}

.attachment-info:hover {
  color: #409EFF;
}

.attachment-icon {
  font-size: 18px;
  color: #909399;
  flex-shrink: 0;
}

.attachment-name {
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.attachment-actions .el-button {
  padding: 6px 12px;
  font-size: 13px;
}

.attachment-icon {
  margin-right: 4px;
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
  .announcement-management-container {
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
  
  .view-meta {
    flex-direction: column;
    gap: 10px;
  }
}
</style>