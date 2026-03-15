<template>
  <div class="problem-feedback-container">
    <!-- 顶部标题区域 - 统一风格 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">反馈管理</h1>
        <p class="page-description">管理系统中的用户问题反馈</p>
      </div>
      <div class="header-illustration">
        <div class="illustration-circle circle-1"></div>
        <div class="illustration-circle circle-2"></div>
      </div>
    </div>

    <!-- 搜索区域卡片 - 统一风格 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" class="search-form" @keyup.enter="handleSearch">
        <div class="search-row">
          <el-form-item label="用户类型">
            <el-select v-model="searchForm.userType" placeholder="请选择用户类型" clearable style="width: 120px;">
              <el-option label="全部" value=""></el-option>
              <el-option label="学生" value="student"></el-option>
              <el-option label="教师" value="teacher"></el-option>
              <el-option label="企业" value="company"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="searchForm.status" placeholder="请选择状态" clearable style="width: 120px;">
              <el-option label="全部" value=""></el-option>
              <el-option label="处理中" value="processing"></el-option>
              <el-option label="已解决" value="resolved"></el-option>
              <el-option label="感谢您的反馈" value="closed"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="优先级">
            <el-select v-model="searchForm.priority" placeholder="请选择优先级" clearable style="width: 120px;">
              <el-option label="全部" value=""></el-option>
              <el-option label="高" value="high"></el-option>
              <el-option label="中" value="normal"></el-option>
              <el-option label="低" value="low"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="反馈类型">
            <el-select v-model="searchForm.feedbackType" placeholder="请选择反馈类型" clearable style="width: 140px;">
              <el-option label="全部" value=""></el-option>
              <el-option label="系统问题" value="system"></el-option>
              <el-option label="功能建议" value="feature"></el-option>
              <el-option label="程序错误" value="bug"></el-option>
              <el-option label="数据问题" value="data"></el-option>
              <el-option label="其他" value="other"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="用户姓名">
            <el-input
              v-model="searchForm.userName"
              placeholder="请输入用户姓名"
              clearable
              style="width: 150px;"
              @keyup.enter="handleSearch"
            ></el-input>
          </el-form-item>
          <el-form-item label="标题">
            <el-input
              v-model="searchForm.title"
              placeholder="请输入标题"
              clearable
              style="width: 200px;"
              @keyup.enter="handleSearch"
            ></el-input>
          </el-form-item>
        </div>
        <div class="search-row button-row">
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

    <!-- 操作按钮区域 - 统一风格 -->
    <el-card class="actions-card" shadow="never">
      <div class="actions-container">
        <div class="primary-actions">
          <el-button v-if="authStore.hasPermission('problem:delete')" type="danger" @click="handleBatchDelete" class="action-btn danger">
            <el-icon><Delete /></el-icon>&nbsp;批量删除
          </el-button>
        </div>
        <div class="secondary-actions">
          <el-button v-if="authStore.hasPermission('problem:view')" type="success" @click="exportToExcel" class="action-btn success">
            <el-icon><Download /></el-icon>&nbsp;导出Excel
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 数据列表卡片 - 统一风格 -->
    <el-card class="table-card" shadow="never">
      <el-table 
        v-loading="loading" 
        :data="tableData" 
        row-key="id"
        style="width: 100%" 
        @selection-change="handleSelectionChange" 
        border
        fit
        class="data-table"
      >
        <el-table-column type="selection" width="55" align="center" reserve-selection></el-table-column>
        <el-table-column type="index" label="序号" width="55" align="center" :index="(index) => (currentPage - 1) * pageSize + index + 1"></el-table-column>
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip></el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getStatusTag(scope.row.status)" size="small" class="status-tag">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="userName" label="用户姓名" width="120" align="center"></el-table-column>
        <el-table-column prop="userAccount" label="用户账号" width="150" align="center"></el-table-column>
        <el-table-column prop="userType" label="用户类型" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getUserTypeTag(scope.row.userType)" size="small">
              {{ getUserTypeText(scope.row.userType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="feedbackType" label="反馈类型" width="130" align="center">
          <template #default="scope">
            <el-tag :type="getFeedbackTypeTag(scope.row.feedbackType)" size="small">
              {{ getFeedbackTypeText(scope.row.feedbackType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" align="center">
          <template #default="scope">
            <el-tag :type="getPriorityTag(scope.row.priority)" size="small">
              {{ getPriorityText(scope.row.priority) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="提交时间" width="170" align="center">
          <template #default="scope">{{ formatDate(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right" align="center">
          <template #default="scope">
            <div class="action-buttons">
              <el-tooltip v-if="authStore.hasPermission('problem:view')" content="查看" placement="top">
                <el-button type="success" size="small" @click="handleView(scope.row)" class="table-btn view">
                  <el-icon><View /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('problem:edit')" content="回复" placement="top">
                <el-button size="small" type="primary" @click="handleReply(scope.row)" class="table-btn edit">
                  <el-icon><ChatDotRound /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('problem:delete')" content="删除" placement="top">
                <el-button size="small" type="danger" @click="handleDelete(scope.row.id)" class="table-btn delete">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页组件 - 统一风格 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          class="custom-pagination"
        />
      </div>
    </el-card>

    <!-- 查看问题反馈对话框 - 统一风格 -->
    <el-dialog v-model="viewDialogVisible" title="查看问题反馈" width="900px" class="form-dialog view-dialog">
      <div v-if="viewData" class="view-content">
        <h3 class="view-title">{{ viewData.title }}</h3>
        <div class="view-meta">
          <div class="meta-item">
            <span class="meta-label">用户姓名:</span>
            <span class="meta-value">{{ viewData.userName }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">用户账号:</span>
            <span class="meta-value">{{ viewData.userAccount }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">用户类型:</span>
            <el-tag :type="getUserTypeTag(viewData.userType)" size="small">
              {{ getUserTypeText(viewData.userType) }}
            </el-tag>
          </div>
          <div class="meta-item">
            <span class="meta-label">反馈类型:</span>
            <el-tag :type="getFeedbackTypeTag(viewData.feedbackType)" size="small">
              {{ getFeedbackTypeText(viewData.feedbackType) }}
            </el-tag>
          </div>
          <div class="meta-item">
            <span class="meta-label">优先级:</span>
            <el-tag :type="getPriorityTag(viewData.priority)" size="small">
              {{ getPriorityText(viewData.priority) }}
            </el-tag>
          </div>
          <div class="meta-item">
            <span class="meta-label">状态:</span>
            <el-tag :type="getStatusTag(viewData.status)" size="small">
              {{ getStatusText(viewData.status) }}
            </el-tag>
          </div>
          <div class="meta-item">
            <span class="meta-label">提交时间:</span>
            <span class="meta-value">{{ formatDate(viewData.createTime) }}</span>
          </div>
        </div>
        <el-divider></el-divider>
        <div class="content-body">
          <div class="content-label">反馈内容:</div>
          <div class="content-text">{{ viewData.content }}</div>
        </div>
        <div v-if="viewData.adminReply" class="reply-section">
          <el-divider></el-divider>
          <div class="reply-label">管理员回复:</div>
          <div class="reply-content">
            <div class="reply-text">{{ viewData.adminReply }}</div>
            <div class="reply-meta">
              <span>回复人: {{ viewData.adminName }}</span>
              <span>回复时间: {{ formatDate(viewData.replyTime) }}</span>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="viewDialogVisible = false" class="cancel-btn">关闭</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 回复问题反馈对话框 - 统一风格 -->
    <el-dialog v-model="replyDialogVisible" title="回复问题反馈" width="700px" class="form-dialog reply-dialog">
      <el-form ref="replyFormRef" :model="replyForm" :rules="replyRules" label-width="100px" class="reply-form">
        <el-form-item label="反馈标题">
          <el-input v-model="replyForm.title" disabled></el-input>
        </el-form-item>
        <el-form-item label="反馈内容">
          <el-input v-model="replyForm.content" type="textarea" :rows="4" disabled></el-input>
        </el-form-item>
        <el-form-item label="回复内容" prop="adminReply">
          <el-input v-model="replyForm.adminReply" type="textarea" :rows="6" placeholder="请输入回复内容"></el-input>
        </el-form-item>
        <el-form-item label="处理状态" prop="status">
          <el-select v-model="replyForm.status" placeholder="请选择处理状态">
            <el-option label="处理中" value="processing"></el-option>
            <el-option label="已解决" value="resolved"></el-option>
            <el-option label="感谢您的反馈" value="closed"></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="replyDialogVisible = false" class="cancel-btn">取消</el-button>
          <el-button type="primary" @click="handleReplySubmit" class="confirm-btn">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { ref, reactive, onMounted } from 'vue'
import type { Feedback, PaginationState } from '@/types/admin'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete, View, ChatDotRound, Download } from '@element-plus/icons-vue'
import * as problemFeedbackApi from '../../api/problemFeedback'
import { useAuthStore } from '../../store/auth'
import { exportToExcel } from '../../utils/xlsx'

const route = useRoute()
const authStore = useAuthStore()

const currentPage = ref<number>(1)
const pageSize = ref<number>(10)
const total = ref<number>(0)
const loading = ref(false)
const tableData = ref<Feedback[]>([])

const searchForm = reactive({
  userType: '',
  status: '',
  priority: '',
  feedbackType: '',
  userName: '',
  title: ''
})

const viewDialogVisible = ref(false)
const viewData = ref<Feedback | null>(null)

const replyDialogVisible = ref(false)
const replyFormRef = ref()
const replyForm = reactive({
  id: null as number | null,
  title: '',
  content: '',
  adminReply: '',
  status: 'resolved'
})

const replyRules = {
  adminReply: [{ required: true, message: '请输入回复内容', trigger: 'blur' }],
  status: [{ required: true, message: '请选择处理状态', trigger: 'change' }]
}

const selectedIds = ref<number[]>([])

onMounted(() => {
  const feedbackId = route.query.feedbackId
  if (feedbackId) {
    fetchFeedbackById(feedbackId)
  } else {
    fetchData()
  }
})

const fetchFeedbackById = async (id: string): Promise<void> => {
  loading.value = true
  try {
    const response = await problemFeedbackApi.getFeedbackById(id)
    if (response.code === 200 && response.data) {
      const feedback = response.data
      tableData.value = [feedback]
      total.value = 1
      searchForm.title = feedback.title || ''
      searchForm.userType = feedback.userType || ''
      handleView(feedback)
    } else {
      ElMessage.error('获取问题反馈详情失败')
      fetchData()
    }
  } catch (error) {
    logger.error('获取问题反馈详情失败:', error)
    ElMessage.error('获取问题反馈详情失败')
    fetchData()
  } finally {
    loading.value = false
  }
}

const fetchData = async (): Promise<void> => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      pageSize: pageSize.value,
      userType: searchForm.userType || undefined,
      status: searchForm.status || undefined,
      priority: searchForm.priority || undefined,
      feedbackType: searchForm.feedbackType || undefined,
      userName: searchForm.userName || undefined,
      title: searchForm.title || undefined
    }
    const response = await problemFeedbackApi.getFeedbackByPage(params)
    const result = response
    if (result && result.code === 200 && result.data) {
      tableData.value = result.data.rows || []
      total.value = result.data.total || 0
    } else {
      tableData.value = []
      total.value = 0
    }
  } catch (error) {
    logger.error('获取问题反馈列表失败:', error)
    ElMessage.error('获取问题反馈列表失败')
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = (): void => {
  currentPage.value = 1
  fetchData()
}

const resetForm = (): void => {
  searchForm.userType = ''
  searchForm.status = ''
  searchForm.priority = ''
  searchForm.feedbackType = ''
  searchForm.userName = ''
  searchForm.title = ''
  currentPage.value = 1
  fetchData()
}

const handleSelectionChange = (selection: Feedback[]): void => {
  selectedIds.value = selection.map(item => item.id)
}

const handleView = (row: Feedback): void => {
  viewData.value = row
  viewDialogVisible.value = true
}

const handleReply = (row: Feedback): void => {
  replyForm.id = row.id
  replyForm.title = row.title
  replyForm.content = row.content
  replyForm.adminReply = row.adminReply || ''
  replyForm.status = row.status || 'resolved'
  replyDialogVisible.value = true
}

const handleReplySubmit = async (): Promise<void> => {
  if (!replyFormRef.value) return
  
  await replyFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        const adminUser = authStore.user
        const data = {
          adminReply: replyForm.adminReply,
          adminId: adminUser.id,
          adminName: adminUser.username || adminUser.name,
          status: replyForm.status
        }
        await problemFeedbackApi.replyFeedback(replyForm.id, data)
        ElMessage.success('回复成功')
        replyDialogVisible.value = false
        fetchData()
      } catch (error) {
        logger.error('回复失败:', error)
        ElMessage.error('回复失败')
      }
    }
  })
}

const handleDelete = async (id: number): Promise<void> => {
  try {
    await ElMessageBox.confirm('确定要删除这条问题反馈吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await problemFeedbackApi.deleteFeedback(id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      logger.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

const handleBatchDelete = async (): Promise<void> => {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先选择要删除的记录')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除选中的${selectedIds.value.length}条记录吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await problemFeedbackApi.batchDeleteFeedback(selectedIds.value)
    ElMessage.success('批量删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      logger.error('批量删除失败:', error)
      ElMessage.error('批量删除失败')
    }
  }
}

const handleSizeChange = (size: number): void => {
  pageSize.value = size
  currentPage.value = 1
  fetchData()
}

const handleCurrentChange = (page: number): void => {
  currentPage.value = page
  fetchData()
}

const formatDate = (date: string | number | Date): string => {
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

const getUserTypeText = (type: string): string => {
  const map = {
    student: '学生',
    teacher: '教师',
    company: '企业'
  }
  return map[type] || type
}

const getUserTypeTag = (type: string): string => {
  const map = {
    student: 'success',
    teacher: 'warning',
    company: 'info'
  }
  return map[type] || 'info'
}

const getFeedbackTypeText = (type: string): string => {
  const map = {
    system: '系统问题',
    feature: '功能建议',
    bug: '程序错误',
    data: '数据问题',
    other: '其他'
  }
  return map[type] || type
}

const getFeedbackTypeTag = (type: string): string => {
  const map = {
    system: 'danger',
    feature: 'success',
    bug: 'warning',
    data: 'info',
    other: 'info'
  }
  return map[type] || 'info'
}

const getPriorityText = (priority: string): string => {
  const map = {
    high: '高',
    normal: '中',
    low: '低'
  }
  return map[priority] || priority
}

const getPriorityTag = (priority: string): string => {
  const map = {
    high: 'danger',
    normal: 'warning',
    low: 'info'
  }
  return map[priority] || 'info'
}

const getStatusText = (status: string): string => {
  const map = {
    processing: '处理中',
    resolved: '已解决',
    closed: '感谢您的反馈'
  }
  return map[status] || '处理中'
}

const getStatusTag = (status: string): string => {
  const map = {
    processing: 'warning',
    resolved: 'success',
    closed: 'info'
  }
  return map[status] || 'warning'
}

const handleExportToExcel = async (): Promise<void> => {
  try {
    ElMessage({ message: '正在准备导出数据...', type: 'info' })

    const response = await problemFeedbackApi.getAllFeedback()
    logger.log('反馈导出数据响应:', response)

    // 解析后端返回的数据格式：{ code: 200, data: [...] }
    const allFeedback = response.data || []

    if (allFeedback.length === 0) {
      ElMessage.warning('没有数据可导出')
      return
    }
    
    const exportData = allFeedback.map(item => {
      return {
        'ID': item.id,
        '标题': item.title,
        '用户姓名': item.userName,
        '用户账号': item.userAccount,
        '用户类型': getUserTypeText(item.userType),
        '反馈类型': getFeedbackTypeText(item.feedbackType),
        '优先级': getPriorityText(item.priority),
        '状态': getStatusText(item.status),
        '提交时间': formatDate(item.createTime),
        '反馈内容': item.content || '',
        '管理员回复': item.adminReply || '',
        '回复人': item.adminName || '',
        '回复时间': formatDate(item.replyTime),
        '更新时间': formatDate(item.updateTime)
      }
    })

    const currentDate = new Date()
    const year = currentDate.getFullYear()
    const month = String(currentDate.getMonth() + 1).padStart(2, '0')
    const day = String(currentDate.getDate()).padStart(2, '0')
    const hours = String(currentDate.getHours()).padStart(2, '0')
    const minutes = String(currentDate.getMinutes()).padStart(2, '0')
    const seconds = String(currentDate.getSeconds()).padStart(2, '0')
    const fileName = `问题反馈导出_${year}${month}${day}_${hours}${minutes}${seconds}`

    await exportToExcel(exportData, fileName, '问题反馈信息')

    ElMessage({ message: '导出成功', type: 'success' })
  } catch (error) {
    logger.error('导出Excel失败:', error)
    ElMessage.error('导出Excel失败: ' + (error.message || '未知错误'))
  }
}
</script>

<style scoped>
/* 统一容器样式 */
.problem-feedback-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

/* 页面标题区域 - 统一风格 */
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

/* 卡片通用样式 - 统一风格 */
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

/* 搜索表单样式 - 统一风格 */
.search-form {
  width: 100%;
}

.search-row {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.search-row.button-row {
  justify-content: flex-end;
  margin-top: 10px;
}

.search-row .el-form-item {
  margin-bottom: 0;
  flex: 1;
}

.search-row.button-row .el-form-item {
  flex: none;
}

.search-actions {
  flex: none;
  margin-left: auto;
}

.search-btn,
.reset-btn {
  border-radius: 8px;
  padding: 10px 20px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.search-btn:hover,
.reset-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

/* 操作按钮区域 - 统一风格 */
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

/* 表格样式 - 统一风格 */
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

/* 表格操作按钮 - 统一风格 */
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

/* 分页样式 - 统一风格 */
.pagination-container {
  padding: 20px;
  display: flex;
  justify-content: flex-end;
}

.custom-pagination {
  margin-top: 0;
}

/* 对话框样式 - 统一风格 */
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

.view-dialog,
.reply-dialog {
  border-radius: 16px;
}

.view-dialog :deep(.el-dialog__header),
.reply-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #f8fbff 0%, #e6f7ff 100%);
}

.view-content {
  padding: 20px 0;
}

.view-title {
  margin: 0 0 20px 0;
  font-size: 22px;
  color: #303133;
  font-weight: 600;
}

.view-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 15px;
  margin-bottom: 20px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.meta-label {
  color: #909399;
  font-weight: 500;
}

.meta-value {
  color: #303133;
}

.content-label,
.reply-label {
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
}

.content-text,
.reply-text {
  background: #f5f7fa;
  padding: 15px;
  border-radius: 8px;
  color: #606266;
  line-height: 1.6;
  white-space: pre-wrap;
  border: 1px solid #e4e7ed;
}

.reply-section {
  margin-top: 20px;
}

.reply-content {
  background: #f0f9ff;
  padding: 15px;
  border-radius: 8px;
  border-left: 4px solid #409eff;
}

.reply-meta {
  margin-top: 10px;
  display: flex;
  gap: 20px;
  color: #909399;
  font-size: 13px;
}

.reply-form {
  padding: 20px 0;
}

/* 对话框按钮 - 统一风格 */
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

/* 响应式设计 - 统一风格 */
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

@media screen和 (max-width: 768px) {
  .problem-feedback-container {
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
}
</style>