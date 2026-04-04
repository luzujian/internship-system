<template>
  <div class="announcement-management-container">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">通知管理</h1>
        <p class="page-description">管理教师端发布的公告信息</p>
      </div>
    </div>

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

    <el-card class="actions-card" shadow="never">
      <div class="actions-container">
        <div class="primary-actions">
          <el-button type="primary" @click="handleAdd" class="action-btn primary">
            <el-icon><Plus /></el-icon>&nbsp;发布公告
          </el-button>
          <el-button type="danger" @click="handleBatchDelete" class="action-btn danger">
            <el-icon><Delete /></el-icon>&nbsp;批量删除
          </el-button>
        </div>
        <div class="secondary-actions">
          <el-button type="success" @click="refreshData" class="action-btn success">
            <el-icon><Refresh /></el-icon>&nbsp;刷新列表
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="table-header">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange" class="announcement-tabs">
          <el-tab-pane label="全部公告" name="all"></el-tab-pane>
          <el-tab-pane label="我的公告" name="my"></el-tab-pane>
        </el-tabs>
      </div>
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
            <el-tag :type="getTagType(scope.row)" size="small" class="status-tag">
              {{ getStatusText(scope.row) }}
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
              <el-tooltip content="查看" placement="top">
                <el-button size="small" type="success" @click="handleView(scope.row)" class="table-btn view">
                  <el-icon><View /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="canEditAnnouncement(scope.row)" content="编辑" placement="top">
                <el-button size="small" type="primary" @click="handleEdit(scope.row)" class="table-btn edit">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="canEditAnnouncement(scope.row)" content="删除" placement="top">
                <el-button size="small" type="danger" @click="handleDelete(scope.row.id)" class="table-btn delete">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>

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
        <el-form-item label="优先级">
          <el-select v-model="formData.priority" placeholder="请选择优先级">
            <el-option label="普通" value="normal"></el-option>
            <el-option label="重要" value="important"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="目标群体">
          <el-select v-model="formData.targetType" placeholder="请选择目标群体" multiple collapse-tags collapse-tags-tooltip @change="handleTargetTypeChange">
            <template v-if="isCounselor">
              <el-option label="所有负责班级" value="ALL_CLASSES"></el-option>
              <el-option label="特定班级" value="CLASS"></el-option>
            </template>
            <template v-else>
              <el-option label="全体学生" value="STUDENT"></el-option>
              <el-option label="特定专业学生" value="MAJOR"></el-option>
            </template>
          </el-select>
        </el-form-item>
        <el-form-item v-if="formData.targetType.includes('MAJOR') && !isCounselor" label="专业">
          <el-select v-model="formData.majorIds" placeholder="请选择专业（可多选）" multiple collapse-tags collapse-tags-tooltip filterable>
            <el-option v-for="major in majorList" :key="major.id" :label="major.name" :value="String(major.id)"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="formData.targetType.includes('CLASS') && isCounselor" label="选择班级">
          <el-select v-model="formData.classIds" placeholder="请选择班级（可多选）" multiple collapse-tags collapse-tags-tooltip filterable>
            <el-option v-for="cls in classList" :key="cls.id" :label="cls.name" :value="String(cls.id)"></el-option>
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
            <el-tag :type="getTagType(viewData)" size="small" class="status-tag">
              {{ getStatusText(viewData) }}
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

    <FilePreviewDialog
      v-model="filePreviewVisible"
      :file-url="currentFileUrl"
      :file-name="currentFileName"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { Announcement, Major } from '@/types/admin'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete, Edit, View, Upload, Download, Document, Paperclip, ZoomIn } from '@element-plus/icons-vue'
import * as announcementApi from '@/api/announcement'
import MajorService from '@/api/major'
import { useAuthStore } from '@/store/auth'
import FilePreviewDialog from '@/components/FilePreviewDialog.vue'
import { getTeacherClasses } from '@/api/teacherClass'
import type { TeacherClass } from '@/api/teacherClass'

const authStore = useAuthStore()
const route = useRoute()

const uploadAction = '/api/upload/file'
const uploadHeaders = computed(() => ({
  'Authorization': `Bearer ${authStore.token}`
}))

const currentPage = ref<number>(1)
const pageSize = ref<number>(10)
const total = ref<number>(0)
const loading = ref(false)
const tableData = ref<Announcement[]>([])

const searchForm = reactive({ title: '', status: '' })

const activeTab = ref('all')

const majorList = ref<Major[]>([])
const classList = ref<TeacherClass[]>([])

// 获取当前教师类型
const teacherType = computed(() => {
  return authStore.user?.teacherType || ''
})

// 是否为辅导员
const isCounselor = computed(() => {
  return teacherType.value === 'COUNSELOR'
})

// 获取辅导员负责的班级列表
const loadCounselorClasses = async (): Promise<void> => {
  if (!isCounselor.value) return
  try {
    const counselorId = authStore.user?.id
    if (!counselorId) return
    const response = await getTeacherClasses(counselorId)
    if (response && response.data) {
      classList.value = response.data
    }
  } catch (error) {
    console.error('获取辅导员班级列表失败:', error)
    classList.value = []
  }
}

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
  targetType: [] as string[],
  majorIds: [] as string[],
  classIds: [] as string[],
  targetValue: null,
  attachments: []
})

const rules = {
  title: [
    { required: true, message: '请输入公告标题', trigger: 'blur' },
    { min: 2, max: 200, message: '标题长度在2-200个字符', trigger: 'blur' }
  ],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }],
  status: [{ required: true, message: '请选择公告状态', trigger: 'change' }],
  publishDate: [{ required: true, message: '请选择发布日期', trigger: 'change' }],
  expireDate: [{ required: true, message: '请选择过期日期', trigger: 'change' }],
  targetType: [
    { 
      required: true, 
      message: '请选择目标群体', 
      trigger: 'change',
      validator: (rule: any, value: any, callback: any) => {
        if (!value || value.length === 0) {
          callback(new Error('请选择目标群体'))
        } else {
          callback()
        }
      }
    }
  ]
}

const viewDialogVisible = ref(false)
const viewData = ref<Announcement | null>(null)

const filePreviewVisible = ref(false)
const currentFileUrl = ref('')
const currentFileName = ref('')

const selectedIds = ref<(string | number)[]>([])

onMounted(() => {
  fetchData()
  loadMajors()
  loadCounselorClasses()
})

// 监听路由变化，当从首页跳转过来时自动加载数据
watch(() => route.state?.fromHome, (fromHome) => {
  if (fromHome) {
    fetchData()
  }
}, { immediate: true })

const loadMajors = async (): Promise<void> => {
  try {
    const response = await MajorService.getMajors()
    const result = response
    if (result && result.code === 200 && result.data) {
      majorList.value = result.data
    } else if (result && Array.isArray(result)) {
      majorList.value = result
    } else {
      majorList.value = []
    }
  } catch (error) {
    majorList.value = []
  }
}

const fetchData = async (): Promise<void> => {
  loading.value = true
  try {
    const params: any = {
      page: currentPage.value,
      pageSize: pageSize.value,
      title: searchForm.title || undefined,
      status: searchForm.status || undefined
    }
    
    if (activeTab.value === 'my') {
      const publisherName = authStore.user?.name
      if (publisherName) {
        params.publisher = publisherName
      }
    }
    
    console.log('请求参数:', params)
    const response = await announcementApi.getAnnouncementsByPage(params)
    const result = response
    console.log('响应结果:', result)
    if (result && result.code === 200 && result.data) {
      tableData.value = result.data.rows || []
      total.value = result.data.total || 0
    } else if (result && Array.isArray(result.data)) {
      tableData.value = result.data
      total.value = result.data.length || 0
    } else {
      tableData.value = []
      total.value = 0
      if (result && result.msg) {
        ElMessage.error(result.msg)
      }
    }
  } catch (error) {
    console.error('获取公告列表失败:', error)
    ElMessage.error('获取公告列表失败')
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleTabChange = (tabName: string): void => {
  activeTab.value = tabName
  currentPage.value = 1
  fetchData()
}

const canEditAnnouncement = (announcement: Announcement): boolean => {
  return announcement.publisher === authStore.user?.name
}

const handleSearch = (): void => {
  currentPage.value = 1
  fetchData()
}

const resetForm = (): void => {
  searchForm.title = ''
  searchForm.status = ''
  currentPage.value = 1
  fetchData()
}

const handleSizeChange = (val: number): void => {
  pageSize.value = val
  currentPage.value = 1
  fetchData()
}

const handleCurrentChange = (val: number): void => {
  currentPage.value = val
  fetchData()
}

const handleSelectionChange = (val: Announcement[]): void => {
  // Only allow selecting announcements that the current user can edit
  const selectableIds = val
    .filter(item => canEditAnnouncement(item))
    .map(item => item.id)
  selectedIds.value = selectableIds

  // If some items were deselected due to permission, show a warning
  if (val.length > 0 && selectableIds.length < val.length) {
    ElMessage.warning('只能选择自己发布的公告')
  }
}

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
  formData.targetType = []
  formData.majorIds = []
  formData.classIds = []
  formData.targetValue = null
  formData.attachments = []
}

const handleAdd = (): void => {
  resetFormData()
  formData.publisher = authStore.user?.name || ''
  formData.publisherRole = getTeacherType()
  formData.publishDate = new Date()
  dialogTitle.value = '发布公告'
  dialogVisible.value = true
}

const handleEdit = (row: Announcement): void => {
  formData.id = row.id
  formData.title = row.title
  formData.content = row.content
  formData.status = row.status
  formData.publisher = row.publisher
  formData.publisherRole = row.publisherRole
  formData.publishDate = row.publishDate || row.publishTime
  formData.expireDate = row.expireDate || row.validTo
  formData.priority = row.priority || 'normal'
  formData.targetType = row.targetType ? (Array.isArray(row.targetType) ? row.targetType : [row.targetType]) : []
  formData.majorIds = row.targetType === 'MAJOR' && row.targetValue ? [String(row.targetValue)] : []
  formData.attachments = row.attachments || []
  dialogTitle.value = '编辑公告'
  dialogVisible.value = true
}

const handleView = (row: Announcement): void => {
  viewData.value = row
  viewDialogVisible.value = true
}

const handleDelete = async (id: number): Promise<void> => {
  try {
    await ElMessageBox.confirm('确定要删除这条公告吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await announcementApi.deleteAnnouncement(id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleBatchDelete = async (): Promise<void> => {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请选择要删除的公告')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedIds.value.length} 条公告吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await announcementApi.batchDeleteAnnouncements(selectedIds.value as number[])
    ElMessage.success('批量删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('批量删除失败')
    }
  }
}

// 处理目标群体类型变化
const handleTargetTypeChange = (): void => {
  // 如果选择"所有负责班级"，清空班级选择
  if (formData.targetType.includes('ALL_CLASSES')) {
    formData.classIds = []
  }
  // 如果取消选择"特定班级"，清空班级选择
  if (!formData.targetType.includes('CLASS')) {
    formData.classIds = []
  }
}

const handleSubmit = async (): Promise<void> => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()

    // 处理目标群体值
    let targetValue = null
    if (formData.targetType.includes('MAJOR') && formData.majorIds.length > 0) {
      targetValue = JSON.stringify({ majorIds: formData.majorIds })
    } else if (formData.targetType.includes('CLASS') && formData.classIds.length > 0) {
      targetValue = JSON.stringify({ classIds: formData.classIds })
    }

    const submitData = {
      title: formData.title,
      content: formData.content,
      status: formData.status,
      publisher: formData.publisher || authStore.user?.name,
      publisherRole: formData.publisherRole || getTeacherType(),
      publishDate: formData.publishDate instanceof Date ? formData.publishDate.toISOString() : formData.publishDate,
      expireDate: formData.expireDate instanceof Date ? formData.expireDate.toISOString() : formData.expireDate,
      priority: formData.priority,
      targetType: formData.targetType,
      targetValue: targetValue,
      attachments: formData.attachments
    }

    if (formData.id) {
      await announcementApi.updateAnnouncement(formData.id, submitData)
      ElMessage.success('更新成功')
    } else {
      await announcementApi.addAnnouncement(submitData)
      ElMessage.success('发布成功')
    }

    dialogVisible.value = false

    // 切换到"我的"标签页并刷新数据
    if (activeTab.value !== 'my') {
      activeTab.value = 'my'
    }
    currentPage.value = 1
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('提交失败')
    }
  }
}

const handleDialogClose = (): void => {
  resetFormData()
}

const handleDialogOpen = (): void => {
  nextTick(() => {
    formRef.value?.clearValidate()
  })
}

const handleUploadSuccess = (response: any, file: any): void => {
  if (response.code === 200) {
    file.url = response.data.url
    file.name = response.data.name
  }
}

const handleRemoveFile = (file: any): void => {
  const index = formData.attachments.indexOf(file)
  if (index > -1) {
    formData.attachments.splice(index, 1)
  }
}

const beforeUpload = (file: File): boolean => {
  const isValidSize = file.size <= 10 * 1024 * 1024
  if (!isValidSize) {
    ElMessage.error('文件大小不能超过10MB')
  }
  return isValidSize
}

const handleExceed = (): void => {
  ElMessage.warning('最多只能上传5个文件')
}

const refreshData = (): void => {
  fetchData()
}

const parseAttachments = (attachments: any) => {
  if (!attachments) return []
  try {
    if (typeof attachments === 'string') {
      return JSON.parse(attachments)
    }
    return attachments
  } catch (e) {
    return []
  }
}

const previewFile = (attachment: any): void => {
  if (!attachment.url) {
    ElMessage.error('附件地址不存在')
    return
  }
  currentFileUrl.value = attachment.url
  currentFileName.value = attachment.name
  filePreviewVisible.value = true
}

const downloadFileWithConfirm = (attachment: any): void => {
  if (!attachment.url) {
    ElMessage.error('附件地址不存在')
    return
  }
  const link = document.createElement('a')
  link.href = attachment.url
  link.download = attachment.name || '附件'
  link.target = '_blank'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

const getTeacherType = (): string => {
  const user = authStore.user
  if (!user || !user.teacherType) return 'COLLEGE'
  
  const typeMap: Record<string, string> = {
    'COLLEGE': 'COLLEGE',
    'DEPARTMENT': 'DEPARTMENT',
    'COUNSELOR': 'COUNSELOR'
  }
  return typeMap[user.teacherType] || 'COLLEGE'
}

const formatDate = (date: string | Date | null): string => {
  if (!date) return ''
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

const getStatusText = (row: Announcement): string => {
  const now = new Date()
  const expireDate = row.expireDate || row.validTo
  const isExpired = row.status === 'PUBLISHED' && expireDate && new Date(expireDate) < now
  
  if (isExpired) return '已过期'
  
  const statusMap: Record<string, string> = {
    'DRAFT': '草稿',
    'PUBLISHED': '已发布',
    'EXPIRED': '已过期'
  }
  return statusMap[row.status] || row.status
}

const getTagType = (row: Announcement): string => {
  const now = new Date()
  const expireDate = row.expireDate || row.validTo
  const isExpired = row.status === 'PUBLISHED' && expireDate && new Date(expireDate) < now
  
  if (isExpired) return 'info'
  
  const typeMap: Record<string, string> = {
    'DRAFT': 'info',
    'PUBLISHED': 'success',
    'EXPIRED': 'warning'
  }
  return typeMap[row.status] || 'info'
}

const getPublisherRoleText = (role: string): string => {
  const map: Record<string, string> = {
    'ADMIN': '管理员',
    'COLLEGE': '学院教师',
    'DEPARTMENT': '系室教师',
    'COUNSELOR': '辅导员'
  }
  return map[role] || '-'
}

const getPublisherRoleType = (role: string): string => {
  const map: Record<string, string> = {
    'ADMIN': 'danger',
    'COLLEGE': 'primary',
    'DEPARTMENT': 'success',
    'COUNSELOR': 'warning'
  }
  return map[role] || 'info'
}

const getTargetText = (targetType: any, targetValue: any): string => {
  if (!targetType) return '-'

  // 如果是 JSON 字符串，解析为数组
  let types: string[] = []
  if (typeof targetType === 'string') {
    try {
      types = JSON.parse(targetType)
    } catch (e) {
      types = [targetType]
    }
  } else if (Array.isArray(targetType)) {
    types = targetType
  } else {
    types = [String(targetType)]
  }

  const typeMap: Record<string, string> = {
    'ALL': '全体师生',
    'STUDENT': '全体学生',
    'TEACHER': '全体教师',
    'TEACHER_TYPE': '特定教师类别',
    'MAJOR': '特定专业学生',
    'COMPANY': '企业用户'
  }

  const typeTexts = types.map(type => typeMap[type] || type).join('、')

  // 解析 targetValue 获取教师类别和专业 ID
  let teacherTypes: string[] = []
  let majorIds: string[] = []
  if (targetValue) {
    try {
      const targetValueObj = typeof targetValue === 'string'
        ? JSON.parse(targetValue)
        : targetValue
      teacherTypes = targetValueObj.teacherTypes || []
      majorIds = targetValueObj.majorIds || []
    } catch (e) {
      // 兼容旧格式
      if (typeof targetValue === 'string' && !targetValue.startsWith('{')) {
        teacherTypes = [String(targetValue)]
      }
    }
  }

  // 添加教师类别详情
  if (teacherTypes.length > 0) {
    const teacherTypeMap: Record<string, string> = {
      'COLLEGE': '学院教师',
      'DEPARTMENT': '系室教师',
      'COUNSELOR': '辅导员'
    }
    const teacherTypeTexts = teacherTypes.map(t => teacherTypeMap[t] || t).join('、')
    return `${typeTexts}（${teacherTypeTexts}）`
  }

  // 添加专业详情
  if (majorIds.length > 0) {
    const majorNames = majorIds
      .map(id => {
        const major = majorList.value.find(m => String(m.id) === String(id))
        return major ? major.name : id
      })
      .join('、')
    return `${typeTexts}（${majorNames}）`
  }

  return typeTexts
}

const nextTick = (callback: () => void) => {
  setTimeout(callback, 0)
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
  margin-bottom: 24px;
}

.header-content h1 {
  font-size: 24px;
  font-weight: 600;
  margin-bottom: 8px;
  color: #303133;
}

.page-description {
  font-size: 14px;
  color: #909399;
  margin: 0;
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

.table-header {
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}

.search-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-form {
  display: flex;
  align-items: center;
  width: 100%;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
}

.search-row .el-form-item {
  margin-bottom: 0;
  flex: 1;
}

.search-actions {
  display: flex;
  gap: 8px;
  flex: none;
  margin-left: auto;
}

.search-btn,
.reset-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  border-radius: 8px;
  padding: 10px 20px;
}

.announcement-tabs {
  margin-bottom: 0;
}

.announcement-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.announcement-tabs :deep(.el-tabs__item) {
  font-size: 14px;
  padding: 0 20px;
}

.announcement-tabs :deep(.el-tabs__item.is-active) {
  color: #409EFF;
  font-weight: 500;
}

.actions-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.primary-actions,
.secondary-actions {
  display: flex;
  gap: 12px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  border-radius: 8px;
  padding: 10px 20px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.action-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.data-table {
  margin-top: 16px;
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

.data-table :deep(.el-table__cell) {
  border-right: 1px solid #ebeef5;
}

.data-table :deep(.el-table__cell:last-child) {
  border-right: none;
}

.data-table :deep(.el-table th.el-table__cell) {
  border-right: 1px solid #ebeef5;
}

.data-table :deep(.el-table th.el-table__cell:last-child) {
  border-right: none;
}

.status-tag {
  border-radius: 12px;
  font-weight: 500;
}

.publisher-role-tag {
  border-radius: 12px;
  font-weight: 500;
}

.action-buttons {
  display: flex;
  justify-content: center;
  gap: 8px;
}

.table-btn {
  padding: 6px 12px;
  border-radius: 6px;
  transition: all 0.3s ease;
}

.table-btn:hover {
  transform: scale(1.1);
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
  padding: 20px;
}

.form-dialog,
.view-dialog {
  border-radius: 16px;
  overflow: hidden;
}

.view-dialog :deep(.el-dialog__body) {
  padding: 0;
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

.view-content {
  padding: 20px 0;
}

.view-title {
  font-size: 20px;
  font-weight: bold;
  color: #303133;
  margin: 0 0 20px 0;
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
  font-weight: 500;
  color: #606266;
  font-size: 14px;
}

.meta-value {
  color: #303133;
  font-size: 14px;
}

.content-body {
  font-size: 14px;
  line-height: 1.8;
  color: #606266;
  padding: 20px;
  background: white;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  min-height: 200px;
  white-space: pre-wrap;
}

.content-body :deep(img) {
  max-width: 100%;
  height: auto;
}

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
  cursor: pointer;
  flex: 1;
  min-width: 0;
}

.attachment-info:hover {
  color: #409EFF;
}

.attachment-icon {
  color: #409EFF;
  font-size: 18px;
  flex-shrink: 0;
}

.attachment-name {
  color: #409EFF;
  font-size: 14px;
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
