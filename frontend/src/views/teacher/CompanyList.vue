<script setup lang="ts">
import logger from '@/utils/logger'
import { ref, onMounted } from 'vue'
import type { CompanyUser, PaginationState } from '@/types/admin'
import { ElMessage } from 'element-plus'
import { Search, Refresh, View } from '@element-plus/icons-vue'
import request from '../../utils/request'
import companyService from '../../api/company'

const formatDate = (_row: unknown, _column: unknown, cellValue: string): string => {
  if (!cellValue) return ''
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

let searchForm = ref<Partial<CompanyUser>>({ companyName: '', status: null, companyTag: [] })
let tableData = ref<CompanyUser[]>([])
let loading = ref(false)
let pagination = ref<PaginationState>({ currentPage: 1, pageSize: 10, total: 0 })
let viewDialogVisible = ref(false)
let currentCompany = ref<CompanyUser | null>(null)

const queryPage = async (): Promise<void> => {
  loading.value = true
  try {
    const params: Record<string, any> = {
      page: pagination.value.currentPage,
      pageSize: pagination.value.pageSize
    }

    logger.log('查询参数 - 原始:', searchForm.value)

    if (searchForm.value.companyName && searchForm.value.companyName.trim() !== '') {
      params.companyName = searchForm.value.companyName.trim()
    }

    if (searchForm.value.status !== null && searchForm.value.status !== undefined && searchForm.value.status !== '') {
      params.status = searchForm.value.status
    }

    if (searchForm.value.companyTag && Array.isArray(searchForm.value.companyTag) && searchForm.value.companyTag.length > 0) {
      params.companyTag = searchForm.value.companyTag.join(',')
    }

    logger.log('查询参数 - 处理后:', params)

    const response = await companyService.getCompanies(params)
    if (response && response.code === 200 && response.data) {
      const dataList = response.data.rows || []
      const totalCount = response.data.total || 0
      tableData.value = dataList
      pagination.value.total = totalCount
      logger.log('查询结果 - 数据条数:', dataList.length, '总数:', totalCount)
    } else {
      tableData.value = []
      pagination.value.total = 0
    }
  } catch (error: any) {
    logger.error('查询企业列表失败:', error)
    tableData.value = []
    pagination.value.total = 0
    ElMessage.error('查询企业列表失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const clear = (): void => {
  searchForm.value = { companyName: '', status: null, companyTag: [] }
  pagination.value.currentPage = 1
  queryPage()
}

const handleSizeChange = (newSize: number): void => {
  pagination.value.pageSize = newSize
  queryPage()
}

const handleCurrentChange = (newCurrent: number): void => {
  pagination.value.currentPage = newCurrent
  queryPage()
}

const getImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('data:')) return url
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  if (url.startsWith('/api/')) return url
  if (url.startsWith('/')) return '/api' + url
  return '/api/' + url
}

const getImageUrls = (urls) => {
  if (!urls) return []
  const urlList = urls.split(',').map(url => url.trim()).filter(url => url)
  return urlList.map(url => getImageUrl(url))
}

const viewCompanyDetail = async (id: string): Promise<void> => {
  try {
    const response = await request.get(`/teacher/companies/${id}`)
    const result = response
    if (result && result.code === 200 && result.data) {
      currentCompany.value = result.data
      viewDialogVisible.value = true
    } else {
      ElMessage.error(result?.msg || '获取企业信息失败')
    }
  } catch (error) {
    ElMessage.error('获取企业信息失败: ' + (error.message || '未知错误'))
  }
}

const formatAuditStatus = (auditStatus: number): string => {
  switch (auditStatus) {
    case 0: return '待审核'
    case 1: return '审核通过'
    case 2: return '审核拒绝'
    default: return '未知状态'
  }
}

const formatAccountStatus = (status: number): string => {
  switch (status) {
    case 0: return '待审核'
    case 1: return '正常'
    case 2: return '已禁用'
    case 3: return '已拒绝'
    default: return '未知状态'
  }
}

const getAuditStatusTagType = (auditStatus: number): string => {
  switch (auditStatus) {
    case 0: return 'warning'
    case 1: return 'success'
    case 2: return 'danger'
    default: return 'info'
  }
}

const getAccountStatusTagType = (status: number): string => {
  switch (status) {
    case 0: return 'warning'
    case 1: return 'success'
    case 2: return 'danger'
    case 3: return 'info'
    default: return 'info'
  }
}

const getTagType = (tag: string): string => {
  const tagMap = {
    '双向选择阶段': 'primary',
    '学生自主联系': 'success',
    '接受兜底': 'warning',
    '国家级': 'danger',
    '省级': 'warning'
  }
  return tagMap[tag.trim()] || 'info'
}

onMounted(async () => {
  await queryPage()
})
</script>

<template>
  <div class="company-list-container">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">企业列表</h1>
        <p class="page-description">查看所有企业账户信息</p>
      </div>
    </div>

    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" class="search-form" @keyup.enter="queryPage">
        <div class="search-row">
          <el-form-item label="企业名称">
            <el-input
              v-model="searchForm.companyName"
              placeholder="请输入企业名称"
              clearable
              @keyup.enter="queryPage"
            ></el-input>
          </el-form-item>
          <el-form-item label="标签">
            <el-select v-model="searchForm.companyTag" placeholder="请选择标签" clearable multiple style="width: 200px;">
              <el-option label="学生自主联系" value="学生自主联系"></el-option>
              <el-option label="接受兜底" value="接受兜底"></el-option>
              <el-option label="双向选择阶段" value="双向选择阶段"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="searchForm.status" placeholder="请选择状态" clearable style="width: 140px;">
              <el-option label="待审核" :value="0"></el-option>
              <el-option label="正常" :value="1"></el-option>
              <el-option label="已禁用" :value="2"></el-option>
              <el-option label="已拒绝" :value="3"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item class="search-actions">
            <el-button type="primary" @click="queryPage" class="search-btn">
              <el-icon><Search /></el-icon>&nbsp;查询
            </el-button>
            <el-button type="warning" @click="clear" class="reset-btn">
              <el-icon><Refresh /></el-icon>&nbsp;重置
            </el-button>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table 
        :data="tableData" 
        border 
        style="width: 100%" 
        fit 
        row-key="id"
        class="data-table"
        v-loading="loading"
      >
        <el-table-column type="index" label="序号" width="55" align="center" :index="(index) => (pagination.currentPage - 1) * pagination.pageSize + index + 1" />
        <el-table-column prop="companyName" label="企业名称" min-width="180" align="center" />
        <el-table-column prop="auditStatus" label="审核状态" width="120" align="center">
          <template #default="scope">
            <el-tag :type="getAuditStatusTagType(scope.row.auditStatus)" size="small" class="status-tag">
              {{ formatAuditStatus(scope.row.auditStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="账号状态" width="120" align="center">
          <template #default="scope">
            <el-tag :type="getAccountStatusTagType(scope.row.status)" size="small" class="status-tag">
              {{ formatAccountStatus(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="companyTag" label="标签" width="150" align="center">
          <template #default="scope">
            <div v-if="scope.row.companyTag" class="tag-container">
              <el-tag 
                v-for="(tag, index) in scope.row.companyTag.split(',')" 
                :key="index" 
                :type="getTagType(tag)" 
                size="small" 
                class="company-tag"
              >
                {{ tag }}
              </el-tag>
            </div>
            <span v-else class="no-tag">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="contactPerson" label="联系人" width="120" align="center" />
        <el-table-column prop="contactPhone" label="联系电话" width="150" align="center" />
        <el-table-column prop="contactEmail" label="联系邮箱" min-width="180" align="center" />
        <el-table-column prop="address" label="企业地址" min-width="200" align="center" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="170" align="center" :formatter="formatDate" />
        <el-table-column label="操作" align="center" width="100" fixed="right">
          <template #default="scope">
            <el-tooltip content="查看详情" placement="top">
              <el-button type="success" size="small" @click="viewCompanyDetail(scope.row.id)" class="table-btn">
                <el-icon><View /></el-icon>
              </el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.currentPage"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[5, 10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="pagination.total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          class="custom-pagination"
        />
      </div>
    </el-card>

    <el-dialog v-model="viewDialogVisible" title="企业详情" width="750px" class="view-dialog">
      <div v-if="currentCompany" class="company-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="企业名称" :span="2">{{ currentCompany.companyName }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ currentCompany.contactPerson }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ currentCompany.contactPhone }}</el-descriptions-item>
          <el-descriptions-item label="联系邮箱" :span="2">{{ currentCompany.contactEmail }}</el-descriptions-item>
          <el-descriptions-item label="企业地址" :span="2">{{ currentCompany.address }}</el-descriptions-item>
          <el-descriptions-item label="企业介绍" :span="2">
            <div class="company-intro">{{ currentCompany.introduction || '暂无介绍' }}</div>
          </el-descriptions-item>
          <el-descriptions-item label="标签" :span="2">
            <div v-if="currentCompany.companyTag" class="tag-container">
              <el-tag 
                v-for="(tag, index) in currentCompany.companyTag.split(',')" 
                :key="index" 
                :type="getTagType(tag)" 
                size="small" 
                class="company-tag"
              >
                {{ tag }}
              </el-tag>
            </div>
            <span v-else class="no-tag">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="营业执照" :span="2">
            <div v-if="currentCompany.businessLicense" class="image-preview">
              <el-image
                :src="getImageUrl(currentCompany.businessLicense)"
                :preview-src-list="[getImageUrl(currentCompany.businessLicense)]"
                fit="cover"
                class="material-image"
                :preview-teleported="true"
              />
            </div>
            <div v-else class="material-content">未上传</div>
          </el-descriptions-item>
          <el-descriptions-item label="法人身份证" :span="2">
            <div v-if="currentCompany.legalIdCard" class="image-preview">
              <el-image
                v-for="(url, index) in getImageUrls(currentCompany.legalIdCard)"
                :key="index"
                :src="url"
                :preview-src-list="getImageUrls(currentCompany.legalIdCard)"
                fit="cover"
                class="material-image"
                :preview-teleported="true"
                :initial-index="index"
              />
            </div>
            <div v-else class="material-content">未上传</div>
          </el-descriptions-item>
          <el-descriptions-item label="申请时间" :span="2">{{ formatDate(null, null, currentCompany.applyTime) }}</el-descriptions-item>
          <el-descriptions-item label="审核状态">
            <el-tag :type="getAccountStatusTagType(currentCompany.status)" size="small">
              {{ formatAccountStatus(currentCompany.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ formatDate(null, null, currentCompany.auditTime) }}</el-descriptions-item>
          <el-descriptions-item label="企业ID">{{ currentCompany.id }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDate(null, null, currentCompany.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatDate(null, null, currentCompany.updateTime) }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="viewDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.company-list-container {
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

.header-content h1 {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px 0;
}

.header-content p {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.search-card {
  margin-bottom: 20px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.search-form {
  margin: 0;
}

.search-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.search-actions {
  margin-left: auto;
}

.search-btn,
.reset-btn {
  padding: 8px 20px;
  border-radius: 8px;
  font-weight: 500;
}

.table-card {
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.data-table {
  margin-bottom: 20px;
}

.status-tag {
  font-weight: 500;
}

.tag-container {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: center;
}

.company-tag {
  margin: 2px;
  font-size: 12px;
}

.no-tag {
  color: #909399;
  font-size: 12px;
}

.table-btn {
  padding: 6px 12px;
  border-radius: 6px;
  transition: all 0.3s;
}

.table-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.12);
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0;
}

.custom-pagination {
  display: flex;
  align-items: center;
}

.view-dialog {
  border-radius: 12px;
}

.company-detail {
  padding: 8px 0;
}

.company-intro {
  line-height: 1.6;
  color: #606266;
  white-space: pre-wrap;
  word-break: break-word;
}

.image-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
}

.material-image {
  width: 200px;
  height: 150px;
  border-radius: 8px;
  border: 1px solid #dcdfe6;
  cursor: pointer;
  transition: all 0.3s;
}

.material-image:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.material-content {
  color: #909399;
  font-size: 14px;
  text-align: center;
  padding: 20px 0;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
