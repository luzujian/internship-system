<template>
  <div class="recruitment-management-container">
    <!-- 顶部标题区域 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">招聘管理</h1>
        <p class="page-description">查看和管理所有招聘信息和应聘申请</p>
      </div>
      <div class="header-illustration">
        <div class="illustration-circle circle-1"></div>
        <div class="illustration-circle circle-2"></div>
      </div>
    </div>

    <!-- 统计卡片 -->
    <el-card class="stats-card" shadow="never">
      <div class="stats-grid">
        <div class="stat-item">
          <div class="stat-icon-wrapper">
            <el-icon><Briefcase /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.totalPositions }}</div>
            <div class="stat-label">发布岗位数</div>
          </div>
        </div>
        <div class="stat-item">
          <div class="stat-icon-wrapper">
            <el-icon><Document /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.totalPlannedRecruit }}</div>
            <div class="stat-label">计划招聘人数</div>
          </div>
        </div>
        <div class="stat-item">
          <div class="stat-icon-wrapper">
            <el-icon><User /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.totalRecruitedCount }}</div>
            <div class="stat-label">已招人数</div>
          </div>
        </div>
        <div class="stat-item">
          <div class="stat-icon-wrapper">
            <el-icon><Clock /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.totalRemainingQuota }}</div>
            <div class="stat-label">剩余名额</div>
          </div>
        </div>
        <div class="stat-item">
          <div class="stat-icon-wrapper">
            <el-icon><DocumentChecked /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.totalApplications }}</div>
            <div class="stat-label">应聘申请数</div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 主要内容区域 -->
    <el-card class="content-card" shadow="never">
      <el-tabs v-model="activeTab" class="recruitment-tabs">
        <el-tab-pane label="招聘信息" name="positions">
          <div class="tab-content">
            <!-- 搜索区域 -->
            <el-card class="search-card" shadow="never">
              <el-form :inline="true" :model="positionSearchForm" class="search-form">
                <div class="search-row">
                  <el-form-item label="企业名称">
                    <el-input v-model="positionSearchForm.companyName" placeholder="请输入企业名称" clearable></el-input>
                  </el-form-item>
                  <el-form-item label="岗位名称">
                    <el-input v-model="positionSearchForm.positionName" placeholder="请输入岗位名称" clearable></el-input>
                  </el-form-item>
                  <el-form-item class="search-actions">
                    <el-button v-if="authStore.hasPermission('recruitment:view')" type="primary" @click="searchPositions" class="search-btn">
                      <el-icon><Search /></el-icon>&nbsp;查询
                    </el-button>
                    <el-button v-if="authStore.hasPermission('recruitment:view')" type="warning" @click="resetPositionSearch" class="reset-btn">
                      <el-icon><Refresh /></el-icon>&nbsp;重置
                    </el-button>
                  </el-form-item>
                </div>
              </el-form>
            </el-card>

            <!-- 数据表格 -->
            <el-card class="table-card" shadow="never">
              <el-table 
                :data="positionList" 
                border 
                style="width: 100%" 
                row-key="id"
                v-loading="positionLoading"
                class="data-table"
              >
                <el-table-column type="index" label="序号" width="60" align="center" :index="(index) => (positionPagination.currentPage - 1) * positionPagination.pageSize + index + 1" />
                <el-table-column prop="companyName" label="企业名称" min-width="150" />
                <el-table-column prop="positionName" label="岗位名称" min-width="150" />
                <el-table-column prop="plannedRecruit" label="计划招聘" width="100" align="center" />
                <el-table-column prop="recruitedCount" label="已招人数" width="100" align="center" />
                <el-table-column prop="remainingQuota" label="剩余名额" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.remainingQuota > 0 ? 'success' : 'info'" size="small" class="status-tag">{{ row.remainingQuota }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createTime" label="发布时间" width="180">
                  <template #default="{ row }">
                    {{ formatDate(row.createTime) }}
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="100" align="center" fixed="right">
                  <template #default="{ row }">
                    <div class="action-buttons">
                      <el-tooltip v-if="authStore.hasPermission('recruitment:view')" content="查看详情" placement="top">
                        <el-button type="primary" size="small" @click="viewPositionDetail(row)" class="table-btn view">
                          <el-icon><View /></el-icon>
                        </el-button>
                      </el-tooltip>
                    </div>
                  </template>
                </el-table-column>
              </el-table>

              <!-- 分页 -->
              <div class="pagination-container">
                <el-pagination
                  v-model:current-page="positionPagination.currentPage"
                  v-model:page-size="positionPagination.pageSize"
                  :page-sizes="[5, 10, 20, 50]"
                  layout="total, sizes, prev, pager, next, jumper"
                  :total="positionPagination.total"
                  @size-change="handlePositionSizeChange"
                  @current-change="handlePositionPageChange"
                  class="custom-pagination"
                />
              </div>
            </el-card>
          </div>
        </el-tab-pane>

        <el-tab-pane label="应聘信息" name="applications">
          <div class="tab-content">
            <!-- 搜索区域 -->
            <el-card class="search-card" shadow="never">
              <el-form :inline="true" :model="applicationSearchForm" class="search-form">
                <div class="search-row">
                  <el-form-item label="学号">
                    <el-input v-model="applicationSearchForm.studentId" placeholder="请输入学号" clearable></el-input>
                  </el-form-item>
                  <el-form-item label="学生姓名">
                    <el-input v-model="applicationSearchForm.name" placeholder="请输入学生姓名" clearable></el-input>
                  </el-form-item>
                  <el-form-item label="性别">
                    <el-select v-model="applicationSearchForm.gender" placeholder="请选择性别" clearable>
                      <el-option label="男" :value="0"></el-option>
                      <el-option label="女" :value="1"></el-option>
                    </el-select>
                  </el-form-item>
                  <el-form-item label="企业名称">
                    <el-input v-model="applicationSearchForm.companyName" placeholder="请输入企业名称" clearable></el-input>
                  </el-form-item>
                  <el-form-item class="search-actions">
                    <el-button v-if="authStore.hasPermission('recruitment:view')" type="primary" @click="searchApplications" class="search-btn">
                      <el-icon><Search /></el-icon>&nbsp;查询
                    </el-button>
                    <el-button v-if="authStore.hasPermission('recruitment:view')" type="warning" @click="resetApplicationSearch" class="reset-btn">
                      <el-icon><Refresh /></el-icon>&nbsp;重置
                    </el-button>
                  </el-form-item>
                </div>
              </el-form>
            </el-card>

            <!-- 数据表格 -->
            <el-card class="table-card" shadow="never">
              <el-table 
                :data="applicationList" 
                border 
                style="width: 100%" 
                row-key="id"
                v-loading="applicationLoading"
                class="data-table"
              >
                <el-table-column type="index" label="序号" width="60" align="center" :index="(index) => (applicationPagination.currentPage - 1) * applicationPagination.pageSize + index + 1" />
                <el-table-column prop="student.studentUserId" label="学号" width="120" />
                <el-table-column prop="student.name" label="学生姓名" width="100" />
                <el-table-column prop="student.gender" label="性别" width="80" align="center">
                  <template #default="{ row }">
                    {{ row.student?.gender === 0 ? '男' : '女' }}
                  </template>
                </el-table-column>
                <el-table-column prop="company.companyName" label="企业名称" min-width="150">
                  <template #default="{ row }">
                    {{ row.company?.companyName || '未申请' }}
                  </template>
                </el-table-column>
                <el-table-column prop="position.positionName" label="岗位名称" min-width="150">
                  <template #default="{ row }">
                    {{ row.position?.positionName || '未申请' }}
                  </template>
                </el-table-column>
                <el-table-column prop="createTime" label="申请时间" width="180">
                  <template #default="{ row }">
                    {{ formatDate(row.createTime) }}
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="120" align="center">
                  <template #default="{ row }">
                    <el-tag :type="getApplicationStatusType(row.status)" size="small" class="status-tag">
                      {{ getApplicationStatusText(row.status) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="100" align="center" fixed="right">
                  <template #default="{ row }">
                    <div class="action-buttons">
                      <el-tooltip v-if="authStore.hasPermission('recruitment:view')" content="查看详情" placement="top">
                        <el-button type="primary" size="small" @click="viewApplicationDetail(row)" class="table-btn view">
                          <el-icon><View /></el-icon>
                        </el-button>
                      </el-tooltip>
                    </div>
                  </template>
                </el-table-column>
              </el-table>

              <!-- 分页 -->
              <div class="pagination-container">
                <el-pagination
                  v-model:current-page="applicationPagination.currentPage"
                  v-model:page-size="applicationPagination.pageSize"
                  :page-sizes="[5, 10, 20, 50]"
                  layout="total, sizes, prev, pager, next, jumper"
                  :total="applicationPagination.total"
                  @size-change="handleApplicationSizeChange"
                  @current-change="handleApplicationPageChange"
                  class="custom-pagination"
                />
              </div>
            </el-card>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 岗位详情对话框 -->
    <el-dialog
      v-model="positionDetailVisible"
      title="岗位详情"
      width="650px"
      class="view-dialog"
    >
      <div v-if="currentPosition" class="detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="企业名称">{{ currentPosition.companyName }}</el-descriptions-item>
          <el-descriptions-item label="岗位名称">{{ currentPosition.positionName }}</el-descriptions-item>
          <el-descriptions-item label="计划招聘">{{ currentPosition.plannedRecruit }}</el-descriptions-item>
          <el-descriptions-item label="已招人数">{{ currentPosition.recruitedCount }}</el-descriptions-item>
          <el-descriptions-item label="剩余名额">
            <el-tag :type="currentPosition.remainingQuota > 0 ? 'success' : 'info'" size="small" class="status-tag">
              {{ currentPosition.remainingQuota }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ formatDate(currentPosition.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="岗位要求" :span="2">
            <div class="company-intro">{{ currentPosition.requirements || '暂无要求' }}</div>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="positionDetailVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 应聘详情对话框 -->
    <el-dialog
      v-model="applicationDetailVisible"
      title="应聘详情"
      width="700px"
      class="view-dialog"
    >
      <div v-if="currentApplication" class="detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="学号">{{ currentApplication.student?.studentUserId }}</el-descriptions-item>
          <el-descriptions-item label="学生姓名">{{ currentApplication.student?.name }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ currentApplication.student?.gender === 0 ? '男' : '女' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getApplicationStatusType(currentApplication.status)" size="small" class="status-tag">
              {{ getApplicationStatusText(currentApplication.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="企业名称">{{ currentApplication.company?.companyName }}</el-descriptions-item>
          <el-descriptions-item label="岗位名称">{{ currentApplication.position?.positionName }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ formatDate(currentApplication.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatDate(currentApplication.updateTime) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">
            <div class="company-intro">{{ currentApplication.remark || '暂无备注' }}</div>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="applicationDetailVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import logger from '@/utils/logger'
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, View, DocumentChecked, Briefcase, Document, User, Clock } from '@element-plus/icons-vue'
import recruitmentService from '../../api/recruitment'
import { useAuthStore } from '../../store/auth'

const authStore = useAuthStore()

const activeTab = ref('positions')
const positionLoading = ref(false)
const applicationLoading = ref(false)

const statistics = ref({
  totalPositions: 0,
  totalPlannedRecruit: 0,
  totalRecruitedCount: 0,
  totalRemainingQuota: 0,
  totalApplications: 0
})

const positionSearchForm = reactive({
  companyName: '',
  positionName: ''
})

const applicationSearchForm = reactive({
  studentId: '',
  name: '',
  gender: null,
  companyName: ''
})

const positionPagination = reactive({
  currentPage: 1,
  pageSize: 10,
  total: 0
})

const applicationPagination = reactive({
  currentPage: 1,
  pageSize: 10,
  total: 0
})

const positionList = ref([])
const applicationList = ref([])

const positionDetailVisible = ref(false)
const applicationDetailVisible = ref(false)
const currentPosition = ref(null)
const currentApplication = ref(null)

const formatDate = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

const getStatusType = (status) => {
  const statusMap = {
    0: 'info',
    1: 'warning',
    2: 'success',
    3: 'info'
  }
  return statusMap[status] || 'info'
}

const getStatusText = (status) => {
  const statusMap = {
    0: '未找到实习',
    1: '有Offer未确定',
    2: '已确定实习',
    3: '实习结束'
  }
  return statusMap[status] || '未知'
}

const getApplicationStatusType = (status) => {
  const statusMap = {
    0: 'warning',
    1: 'primary',
    2: 'success'
  }
  return statusMap[status] || 'info'
}

const getApplicationStatusText = (status) => {
  const statusMap = {
    0: '申请中',
    1: '有Offer未确定',
    2: '已确认'
  }
  return statusMap[status] || '未知'
}

const loadStatistics = async () => {
  try {
    const response = await recruitmentService.getStatistics()
    logger.log('统计数据响应:', response)
    if (response.data && response.code === 200) {
      const data = response.data as any
      statistics.value = {
        totalPositions: data.totalPositions || 0,
        totalPlannedRecruit: data.totalPlannedRecruit || 0,
        totalRecruitedCount: data.totalRecruitedCount || 0,
        totalRemainingQuota: data.totalRemainingQuota || 0,
        totalApplications: data.totalApplications || 0
      }
      logger.log('统计数据:', statistics.value)
    } else {
      logger.error('统计数据响应格式错误:', response)
    }
  } catch (error) {
    logger.error('加载统计数据失败:', error)
  }
}

const loadPositions = async () => {
  positionLoading.value = true
  try {
    const response = await recruitmentService.getPositions({
      page: positionPagination.currentPage,
      pageSize: positionPagination.pageSize,
      companyName: positionSearchForm.companyName,
      positionName: positionSearchForm.positionName
    })
    logger.log('岗位列表响应:', response)
    if (response.data && response.code === 200) {
      positionList.value = response.data.rows || []
      positionPagination.total = response.data.total || 0
      logger.log('岗位列表数据:', positionList.value, '总数:', positionPagination.total)
    } else {
      logger.error('岗位列表响应格式错误:', response)
    }
  } catch (error) {
    logger.error('加载岗位列表失败:', error)
    ElMessage.error('加载岗位列表失败')
  } finally {
    positionLoading.value = false
  }
}

const loadApplications = async () => {
  applicationLoading.value = true
  try {
    const response = await recruitmentService.getApplications({
      page: applicationPagination.currentPage,
      pageSize: applicationPagination.pageSize,
      studentId: applicationSearchForm.studentId,
      name: applicationSearchForm.name,
      gender: applicationSearchForm.gender,
      companyName: applicationSearchForm.companyName
    })
    logger.log('应聘列表响应:', response)
    if (response.data && response.code === 200) {
      applicationList.value = response.data.rows || []
      applicationPagination.total = response.data.total || 0
      logger.log('应聘列表数据:', applicationList.value, '总数:', applicationPagination.total)
    } else {
      logger.error('应聘列表响应格式错误:', response)
    }
  } catch (error) {
    logger.error('加载应聘列表失败:', error)
    ElMessage.error('加载应聘列表失败')
  } finally {
    applicationLoading.value = false
  }
}

const searchPositions = () => {
  positionPagination.currentPage = 1
  loadPositions()
}

const resetPositionSearch = () => {
  positionSearchForm.companyName = ''
  positionSearchForm.positionName = ''
  positionPagination.currentPage = 1
  loadPositions()
}

const searchApplications = () => {
  applicationPagination.currentPage = 1
  loadApplications()
}

const resetApplicationSearch = () => {
  applicationSearchForm.studentId = ''
  applicationSearchForm.name = ''
  applicationSearchForm.gender = null
  applicationSearchForm.companyName = ''
  applicationPagination.currentPage = 1
  loadApplications()
}

const handlePositionSizeChange = (val) => {
  positionPagination.pageSize = val
  loadPositions()
}

const handlePositionPageChange = (val) => {
  positionPagination.currentPage = val
  loadPositions()
}

const handleApplicationSizeChange = (val) => {
  applicationPagination.pageSize = val
  loadApplications()
}

const handleApplicationPageChange = (val) => {
  applicationPagination.currentPage = val
  loadApplications()
}

const viewPositionDetail = (row) => {
  currentPosition.value = row
  positionDetailVisible.value = true
}

const viewApplicationDetail = (row) => {
  currentApplication.value = row
  applicationDetailVisible.value = true
}

onMounted(() => {
  loadStatistics()
  loadPositions()
})

watch(activeTab, (newTab) => {
  if (newTab === 'applications' && applicationList.value.length === 0) {
    loadApplications()
  }
})
</script>

<style scoped>
.recruitment-management-container {
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

.circle-1 { width: 60px; height: 60px; top: 0; right: 0; animation-delay: 0s; }
.circle-2 { width: 40px; height: 40px; bottom: 10px; right: 20px; animation-delay: 3s; }

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

.stats-card,
.content-card,
.search-card,
.table-card {
  border-radius: 16px;
  border: none;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  margin-bottom: 24px;
  overflow: hidden;
}

.stats-card { padding: 24px; }
.content-card { padding: 20px; }
.search-card { padding: 24px; margin-bottom: 20px; }
.table-card { padding: 0; }

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
}

.stat-item {
  display: flex;
  align-items: center;
  padding: 24px;
  border-radius: 8px;
  background: #ffffff;
  border: 1px solid #e8eaec;
  cursor: pointer;
  transition: all 0.25s ease;
}

.stat-item:hover {
  border-color: #409EFF;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.08);
}

.stat-icon-wrapper {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
  font-size: 24px;
  background: #f5f7fa;
  color: #409EFF;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #1f2d3d;
  line-height: 1.2;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 13px;
  color: #8492a6;
  font-weight: 400;
}

.recruitment-tabs {
  width: 100%;
}

.recruitment-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.recruitment-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.search-form {
  width: 100%;
}

.search-row {
  display: flex;
  gap: 20px;
  margin-bottom: 16px;
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
  font-weight: 500;
  transition: all 0.3s ease;
}

.search-btn:hover,
.reset-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

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

.pagination-container {
  padding: 20px;
  display: flex;
  justify-content: flex-end;
}

.custom-pagination {
  margin-top: 0;
}

.view-dialog {
  border-radius: 16px;
  overflow: hidden;
}

.view-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #f8fbff 0%, #e6f7ff 100%);
  margin: 0;
  padding: 20px;
  border-bottom: 1px solid #e6f7ff;
}

.detail-content {
  padding: 20px 0;
}

.company-intro {
  white-space: pre-wrap;
  line-height: 1.6;
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
  .recruitment-management-container {
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
  
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .search-row {
    flex-direction: column;
    gap: 12px;
  }
  
  .search-actions {
    width: 100%;
    justify-content: center;
  }
  
  .action-buttons {
    flex-wrap: wrap;
    justify-content: center;
  }
}

@media screen and (max-width: 480px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>