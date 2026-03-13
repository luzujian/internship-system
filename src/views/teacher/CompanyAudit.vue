<template>
  <div class="company-audit">
    <el-card class="search-card">
      <el-form :model="searchForm" :inline="true" class="search-form" @keyup.enter="handleSearch">
        <el-form-item label="企业名称">
          <el-input
            v-model="searchForm.companyName"
            placeholder="请输入企业名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input
            v-model="searchForm.contactPerson"
            placeholder="请输入联系人"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input
            v-model="searchForm.contactPhone"
            placeholder="请输入联系电话"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>&nbsp;搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>&nbsp;重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="data-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">待审核企业列表</span>
          <div class="card-actions">
            <el-tag type="warning" size="large">待审核: {{ statistics.pending }}</el-tag>
          </div>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="companyName" label="企业名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="contactPerson" label="联系人" width="120" align="center" />
        <el-table-column prop="contactPhone" label="联系电话" width="130" align="center" />
        <el-table-column prop="contactEmail" label="联系邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="address" label="企业地址" min-width="200" show-overflow-tooltip />
        <el-table-column prop="applyTime" label="申请时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatDate(row.applyTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleAudit(row)">
              <el-icon><View /></el-icon>&nbsp;审核
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        class="pagination"
      />
    </el-card>

    <el-dialog v-model="auditDialogVisible" title="企业注册审核" width="800px" class="audit-dialog">
      <el-descriptions :column="2" border v-if="currentCompany">
        <el-descriptions-item label="企业名称" :span="2">{{ currentCompany.companyName }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ currentCompany.contactPerson }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ currentCompany.contactPhone }}</el-descriptions-item>
        <el-descriptions-item label="联系邮箱" :span="2">{{ currentCompany.contactEmail }}</el-descriptions-item>
        <el-descriptions-item label="企业地址" :span="2">{{ currentCompany.address }}</el-descriptions-item>
        <el-descriptions-item label="企业介绍" :span="2">{{ currentCompany.introduction || '暂无介绍' }}</el-descriptions-item>
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
          <div v-else>未上传</div>
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
          <div v-else>未上传</div>
        </el-descriptions-item>
        <el-descriptions-item label="是否实习基地">{{ currentCompany.isInternshipBase === 1 ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ formatDate(currentCompany.applyTime) }}</el-descriptions-item>
      </el-descriptions>

      <el-form :model="auditForm" label-width="100px" class="audit-form">
        <el-form-item label="审核结果" required>
          <el-radio-group v-model="auditForm.auditStatus">
            <el-radio :value="1">通过</el-radio>
            <el-radio :value="2">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核备注">
          <el-input
            v-model="auditForm.auditRemark"
            type="textarea"
            :rows="3"
            placeholder="请输入审核备注（选填）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="auditDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitAudit" :loading="submitLoading">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, View } from '@element-plus/icons-vue'
import request from '../../utils/request'

const searchForm = reactive({
  companyName: '',
  contactPerson: '',
  contactPhone: ''
})

const tableData = ref([])
const loading = ref(false)
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0
})

const statistics = reactive({
  pending: 0
})

const auditDialogVisible = ref(false)
const currentCompany = ref(null)
const auditForm = reactive({
  id: null,
  auditStatus: 1,
  auditRemark: ''
})
const submitLoading = ref(false)

const fetchData = async () => {
  loading.value = true
  
  console.log('[CompanyAudit] 开始获取待审核企业列表')
  console.log('[CompanyAudit] localStorage内容:')
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (key.includes('token') || key.includes('role') || key.includes('accessToken')) {
      console.log(`[CompanyAudit]   ${key}:`, localStorage.getItem(key) ? '存在' : '不存在')
    }
  }
  
  console.log('[CompanyAudit] 当前路径:', window.location.pathname)
  console.log('[CompanyAudit] current_role:', localStorage.getItem('current_role'))
  
  try {
    const params = {
      page: pagination.page,
      pageSize: pagination.pageSize,
      companyName: searchForm.companyName || undefined,
      contactPerson: searchForm.contactPerson || undefined,
      contactPhone: searchForm.contactPhone || undefined
    }
    
    console.log('[CompanyAudit] 请求参数:', params)
    console.log('[CompanyAudit] 请求URL:', '/teacher/companies/audit/pending')
    
    const response = await request.get('/teacher/companies/audit/pending', { params })
    if (response.data.code === 200) {
      tableData.value = response.data.data.rows || []
      pagination.total = response.data.data.total || 0
      console.log('[CompanyAudit] 获取成功，数据量:', tableData.value.length)
    }
  } catch (error) {
    console.error('获取待审核企业列表失败:', error)
    ElMessage.error('获取待审核企业列表失败')
  } finally {
    loading.value = false
  }
}

const fetchStatistics = async () => {
  try {
    const response = await request.get('/teacher/companies/statistics')
    if (response.data.code === 200) {
      statistics.pending = response.data.data.pending || 0
    }
  } catch (error) {
    console.error('获取统计数据失败:', error)
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchData()
}

const handleReset = () => {
  searchForm.companyName = ''
  searchForm.contactPerson = ''
  searchForm.contactPhone = ''
  pagination.page = 1
  fetchData()
}

const handleSizeChange = (size) => {
  pagination.pageSize = size
  fetchData()
}

const handleCurrentChange = (page) => {
  pagination.page = page
  fetchData()
}

const handleAudit = (row) => {
  currentCompany.value = row
  auditForm.id = row.id
  auditForm.auditStatus = 1
  auditForm.auditRemark = ''
  auditDialogVisible.value = true
}

const submitAudit = async () => {
  if (auditForm.auditStatus !== 1 && auditForm.auditStatus !== 2) {
    ElMessage.warning('请选择审核结果')
    return
  }
  
  submitLoading.value = true
  try {
    const response = await request.put(`/teacher/companies/${auditForm.id}/audit`, {
      auditStatus: auditForm.auditStatus,
      auditRemark: auditForm.auditRemark
    })
    
    if (response.data.code === 200) {
      ElMessage.success(auditForm.auditStatus === 1 ? '审核通过' : '审核拒绝')
      auditDialogVisible.value = false
      fetchData()
      fetchStatistics()
    } else {
      ElMessage.error(response.data.message || '审核失败')
    }
  } catch (error) {
    console.error('审核失败:', error)
    ElMessage.error('审核失败')
  } finally {
    submitLoading.value = false
  }
}

const formatDate = (date) => {
  if (!date) return '-'
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
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

onMounted(() => {
  fetchData()
  fetchStatistics()
})
</script>

<style scoped>
.company-audit {
  padding: 20px;
}

.search-card {
  margin-bottom: 20px;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.data-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
}

.card-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.audit-dialog {
  border-radius: 8px;
}

.audit-form {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #EBEEF5;
}

.image-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
  align-items: center;
}

.material-image {
  width: 200px;
  height: 140px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.material-image:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
