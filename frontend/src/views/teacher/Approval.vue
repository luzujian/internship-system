<template>
  <div class="approval-container">
    <div class="page-header">
      <h2>审核管理</h2>
      <p>管理学生实习申请、单位变更、考研延迟及企业资质审核</p>
    </div>

    <!-- 审核类型标签页 -->
    <el-card class="approval-tabs-card" shadow="never">
      <div class="approval-tabs">
        <button 
          v-for="(tab, index) in approvalTabs" 
          :key="tab.key"
          :class="['tab-btn', { active: activeTab === tab.key }]"
          @click="activeTab = tab.key"
        >
          {{ tab.name }}
          <el-badge v-if="tab.count > 0" :value="tab.count" class="tab-badge" />
        </button>
      </div>
    </el-card>

    <!-- 审核列表 -->
    <el-card class="table-card" shadow="never">
      <el-form :inline="true" :model="searchForm" class="search-form" @keyup.enter="handleSearch">
        <div class="search-row">
          <el-form-item label="关键词">
            <el-input
              v-model="searchForm.keyword"
              placeholder="搜索学生姓名或学号"
              clearable
              style="width: 200px;"
              @keyup.enter="handleSearch"
            ></el-input>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="searchForm.status" placeholder="请选择状态" clearable style="width: 120px;">
              <el-option label="全部状态" value=""></el-option>
              <el-option label="待审核" value="pending"></el-option>
              <el-option label="已通过" value="approved"></el-option>
              <el-option label="已驳回" value="rejected"></el-option>
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

      <el-table 
        v-loading="loading" 
        :data="applications" 
        style="width: 100%" 
        border
        fit
        class="data-table"
      >
        <el-table-column prop="type" label="申请类型" width="180" align="center">
          <template #default="scope">
            {{ getApplicationTypeText(scope.row.type) }}
          </template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'companyQualification'" label="企业信息" width="200">
          <template #default="scope">
            <div class="company-info-detail compact">
              <div class="company-name">{{ scope.row.companyName }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-else label="学生信息" width="280">
          <template #default="scope">
            <div class="student-info">
              <div>{{ scope.row.studentName }}</div>
              <div class="info-details">
                <div class="student-id">{{ scope.row.studentUserId || scope.row.studentId }}</div>
                <div class="student-grade">{{ scope.row.grade }}</div>
                <div class="student-class">{{ scope.row.className }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="activeTab !== 'companyQualification' && activeTab !== 'delay'" label="实习单位" min-width="180" align="center">
          <template #default="scope">
            <div class="company-name-display">
              <template v-if="scope.row.type === 'selfPractice'">
                {{ scope.row.company || '-' }}
              </template>
              <template v-else-if="scope.row.type === 'unitChange'">
                <div class="company-change-info">
                  <div class="old-company">原：{{ scope.row.oldCompany || '-' }}</div>
                  <div class="new-company">新：{{ scope.row.newCompany || '-' }}</div>
                </div>
              </template>
              <template v-else>-</template>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="applyTime" label="申请时间" width="200" align="center"></el-table-column>
        <el-table-column label="状态" :width="activeTab === 'companyQualification' ? 280 : 150" align="center">
          <template #default="scope">
            <div class="status-wrapper">
              <el-tag :type="getStatusTagType(activeTab === 'companyQualification' ? scope.row.auditStatus : scope.row.status)" size="small" class="status-tag">
                {{ getStatusText(activeTab === 'companyQualification' ? scope.row.auditStatus : scope.row.status) }}
              </el-tag>
              <el-tag v-if="activeTab === 'companyQualification'" :type="scope.row.isInternshipBase ? 'success' : 'danger'" size="small" class="status-tag-base">
                {{ scope.row.isInternshipBase ? '实习基地' : '非实习基地' }}
              </el-tag>
              <template v-if="activeTab === 'companyQualification' && scope.row.companyTag">
                <el-tag type="info" size="small" class="status-tag-company">
                  {{ getCooperationModeText(scope.row.companyTag) }}
                </el-tag>
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="scope">
            <div class="action-buttons">
              <el-button v-if="(activeTab === 'companyQualification' && scope.row.auditStatus === 0) || (activeTab !== 'companyQualification' && scope.row.status === 'pending')" size="small" type="success" @click="approveApplication(scope.row)" class="table-btn success">
                通过
              </el-button>
              <el-button v-if="(activeTab === 'companyQualification' && scope.row.auditStatus === 0) || (activeTab !== 'companyQualification' && scope.row.status === 'pending')" size="small" type="danger" @click="rejectApplication(scope.row)" class="table-btn danger">
                驳回
              </el-button>
              <el-button size="small" type="primary" @click="activeTab === 'companyQualification' ? viewCompanyQualification(scope.row) : viewApplication(scope.row)" class="table-btn primary">
                查看
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          :current-page="currentPage"
          :page-sizes="[5, 10, 20, 50]"
          :page-size="pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          class="custom-pagination"
        />
      </div>
    </el-card>

    <!-- 驳回申请弹窗 -->
    <div v-if="showRejectModal" class="modal-overlay" @click.self="showRejectModal = false">
      <div class="modal-content card">
        <div class="modal-header">
          <h3>驳回申请</h3>
          <button class="close-btn" @click="showRejectModal = false">×</button>
        </div>
        <div class="modal-body">
          <div v-if="currentApplication?.type === 'companyQualification'" class="form-group">
            <label>企业名称：</label>
            <div class="student-info-display">
              {{ currentApplication?.companyName }}
            </div>
          </div>
          <div v-else class="form-group">
            <label>学生信息：</label>
            <div class="student-info-display">
              {{ currentApplication?.studentName }} ({{ currentApplication?.studentUserId || currentApplication?.studentId }})
            </div>
          </div>
          <div class="form-group">
            <label>申请类型：</label>
            <div class="application-type-display">
              {{ currentApplication ? getApplicationTypeText(currentApplication.type) : '' }}
            </div>
          </div>
          <div class="form-group">
            <label>驳回理由：</label>
            <textarea v-model="rejectReason" placeholder="请填写驳回理由，引导学生补充材料" rows="5"></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-default btn-sm" @click="showRejectModal = false">取消</button>
          <button class="btn-danger btn-sm" @click="confirmReject">确认驳回</button>
        </div>
      </div>
    </div>

    <!-- 企业资质查看弹窗 -->
    <div v-if="showCompanyQualificationModal" class="modal-overlay" @click.self="showCompanyQualificationModal = false">
      <div class="modal-content card company-qualification-modal">
        <div class="modal-header">
          <h3>企业资质审核</h3>
          <button class="close-btn" @click="showCompanyQualificationModal = false">×</button>
        </div>
        <div class="modal-body">
          <div v-if="currentApplication" class="company-qualification-info">
            <div class="form-group">
              <label>企业名称：</label>
              <div class="company-name-display">{{ currentApplication.companyName }}</div>
            </div>
            <div class="form-group">
              <label>企业地址：</label>
              <div class="company-address-display">{{ currentApplication.address || currentApplication.province + currentApplication.city + currentApplication.district + currentApplication.detailAddress || '未填写' }}</div>
            </div>
            <div class="form-row">
              <div class="form-group half">
                <label>联系人：</label>
                <div class="contact-info-display">{{ currentApplication.contactPerson }} ({{ currentApplication.contactPhone }})</div>
              </div>
              <div class="form-group half">
                <label>申请时间：</label>
                <div class="apply-time-display">{{ currentApplication.applyTime || currentApplication.registerTime }}</div>
              </div>
              <div class="form-group half">
                <label>行业：</label>
                <div class="contact-info-display">{{ getIndustryText(currentApplication.industry) }}</div>
              </div>
              <div class="form-group half">
                <label>规模：</label>
                <div class="contact-info-display">{{ currentApplication.scale }}</div>
              </div>
            </div>
            <div class="form-group">
              <label>企业介绍：</label>
              <div class="company-address-display">{{ currentApplication.introduction || '未填写' }}</div>
            </div>
            
            <!-- 资质文件预览 -->
            <div class="qualification-documents">
              <h4>资质文件</h4>
              <div class="document-grid horizontal">
                <div class="document-item fade-in" v-for="doc in companyDocumentTypes" :key="doc.key">
                  <label>{{ doc.name }}：</label>
                  <div class="document-preview">
                    <img v-if="getCompanyDocumentUrl(currentApplication, doc.key)" :src="getCompanyDocumentUrl(currentApplication, doc.key)" :alt="doc.name" class="document-image" @click="previewMaterial(getCompanyDocumentUrl(currentApplication, doc.key), doc.name)">
                    <span v-else class="no-document">未上传</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-default btn-sm" @click="showCompanyQualificationModal = false">关闭</button>
          <button v-if="currentApplication && currentApplication.auditStatus === 0" class="btn-success btn-sm" @click="approveApplication(currentApplication)">
            通过
          </button>
          <button v-if="currentApplication && currentApplication.auditStatus === 0" class="btn-danger btn-sm" @click="rejectApplication(currentApplication)">
            驳回
          </button>
        </div>
      </div>
    </div>

    <!-- 申请详情查看弹窗 -->
    <div v-if="showApplicationDetailModal" class="modal-overlay" @click.self="showApplicationDetailModal = false">
      <div class="modal-content large card">
        <div class="modal-header">
          <h3>{{ currentApplication ? getApplicationTypeText(currentApplication.type) + '详情' : '申请详情' }}</h3>
          <button class="close-btn" @click="showApplicationDetailModal = false">×</button>
        </div>
        <div class="modal-body">
          <div v-if="currentApplication" class="application-detail-info">
            <!-- 基本信息 -->
            <div class="form-row">
              <div class="form-group quarter">
                <label>姓名：</label>
                <div class="student-name-display">{{ currentApplication.studentName }}</div>
              </div>
              <div class="form-group quarter">
                <label>学号：</label>
                <div class="student-id-display">{{ currentApplication.studentUserId || currentApplication.studentId }}</div>
              </div>
              <div class="form-group quarter">
                <label>年级：</label>
                <div class="grade-display">{{ currentApplication.grade || '未填写' }}</div>
              </div>
              <div class="form-group quarter">
                <label>班级：</label>
                <div class="class-display">{{ currentApplication.className }}</div>
              </div>
            </div>
            <div class="form-group">
              <label>申请理由：</label>
              <div class="reason-display">{{ currentApplication.reason }}</div>
            </div>
            
            <!-- 自主实习申请特有信息 -->
            <div v-if="currentApplication.type === 'selfPractice'" class="form-group">
              <label>实习单位：</label>
              <div class="company-display">{{ currentApplication.company }}</div>
            </div>
            <div v-if="currentApplication.type === 'selfPractice' && currentApplication.materials" class="form-group">
              <label>上传资料：</label>
              <div class="document-grid horizontal">
                <div class="document-item fade-in" v-for="label in materialItems.selfPractice" :key="label">
                  <label>{{ label }}：</label>
                  <div class="document-preview">
                    <img v-if="currentApplication.materials[label]" :src="currentApplication.materials[label]" :alt="label" class="document-image" @click="previewMaterial(currentApplication.materials[label], label)">
                    <span v-else class="no-document">未上传</span>
                  </div>
                </div>
              </div>
            </div>
            
            <!-- 单位变更申请特有信息 -->
            <div v-if="currentApplication.type === 'unitChange'" class="form-row">
              <div class="form-group half">
                <label>原实习单位：</label>
                <div class="company-display">{{ currentApplication.oldCompany }}</div>
              </div>
              <div class="form-group half">
                <label>新实习单位：</label>
                <div class="company-display">{{ currentApplication.newCompany }}</div>
              </div>
            </div>
            <div v-if="currentApplication.type === 'unitChange' && currentApplication.materials" class="form-group">
              <label>上传资料：</label>
              <div class="document-grid horizontal">
                <div class="document-item fade-in" v-for="label in materialItems.unitChange" :key="label">
                  <label>{{ label }}：</label>
                  <div class="document-preview">
                    <img v-if="currentApplication.materials[label]" :src="currentApplication.materials[label]" :alt="label" class="document-image" @click="previewMaterial(currentApplication.materials[label], label)">
                    <span v-else class="no-document">未上传</span>
                  </div>
                </div>
              </div>
            </div>
            
            <!-- 考研延迟申请特有信息 -->
            <div v-if="currentApplication.type === 'delay' && currentApplication.materials" class="form-group">
              <label>上传资料：</label>
              <div class="document-grid horizontal">
                <div class="document-item fade-in" v-for="label in materialItems.delay" :key="label">
                  <label>{{ label }}：</label>
                  <div class="document-preview">
                    <img v-if="currentApplication.materials[label]" :src="currentApplication.materials[label]" :alt="label" class="document-image" @click="previewMaterial(currentApplication.materials[label], label)">
                    <span v-else class="no-document">未上传</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-default btn-sm" @click="showApplicationDetailModal = false">关闭</button>
          <button v-if="currentApplication && currentApplication.status === 'pending'" class="btn-success btn-sm" @click="approveApplication(currentApplication)">
            通过
          </button>
          <button v-if="currentApplication && currentApplication.status === 'pending'" class="btn-danger btn-sm" @click="rejectApplication(currentApplication)">
            驳回
          </button>
        </div>
      </div>
    </div>
    
    <!-- 资料预览弹窗 -->
    <div v-if="showMaterialPreviewModal" class="modal-overlay" @click.self="showMaterialPreviewModal = false">
      <div class="modal-content large card">
        <div class="modal-header">
          <h3>{{ previewFileName }} 预览</h3>
          <button class="close-btn" @click="showMaterialPreviewModal = false">×</button>
        </div>
        <div class="modal-body">
          <div class="material-preview-container">
            <iframe v-if="previewUrl" :src="previewUrl" class="material-preview-frame"></iframe>
            <div v-else class="no-preview">
              <span class="no-preview-icon">📄</span>
              <p>无法预览此文件，请尝试下载查看</p>
              <button class="btn btn-primary" @click="downloadMaterial(previewUrl, previewFileName)">下载文件</button>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-default btn-sm" @click="showMaterialPreviewModal = false">关闭</button>
          <button class="btn btn-sm btn-default" @click="downloadMaterial(previewUrl, previewFileName)">下载</button>
        </div>
      </div>
    </div>
    <!-- 操作反馈提示 -->
    <div v-if="showFeedback" class="modal-overlay" @click.self="showFeedback = false">
      <div class="modal-content feedback-modal">
        <div class="modal-header">
          <h3>{{ feedbackTitle }}</h3>
          <button class="close-btn" @click="showFeedback = false">×</button>
        </div>
        <div class="modal-body">
          <div class="feedback-content">
            <div class="feedback-icon" :class="feedbackType">{{ feedbackIcon }}</div>
            <div class="feedback-message">{{ feedbackMessage }}</div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-primary btn-sm" @click="showFeedback = false">确定</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { approvalApi } from '../../api/teacherApproval'
import companyService from '../../api/company'
import { getCurrentUser } from '../../utils/teacherUser'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'

const route = useRoute()

const activeTab = ref('selfPractice')
const approvalTabs = ref([
  { key: 'selfPractice', name: '自主实习申请', count: 0 },
  { key: 'unitChange', name: '单位变更申请', count: 0 },
  { key: 'delay', name: '考研延迟申请', count: 0 },
  { key: 'companyQualification', name: '企业资质审核', count: 0 }
])

const documentTypes = ref([
  { key: 'businessLicense', name: '营业执照' },
  { key: 'legalPersonId', name: '法人身份证' },
  { key: 'organizationCode', name: '组织机构代码证' },
  { key: 'basePhoto', name: '牌匾照片' }
])

const companyDocumentTypes = [
  { key: 'businessLicense', name: '营业执照' },
  { key: 'legalIdCard', name: '法人身份证' },
  { key: 'plaquePhoto', name: '牌匾照片' }
]

const searchForm = reactive({
  keyword: '',
  status: ''
})

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)

const showRejectModal = ref(false)
const showCompanyQualificationModal = ref(false)
const showApplicationDetailModal = ref(false)
const showMaterialPreviewModal = ref(false)
const showFeedback = ref(false)
const currentApplication = ref<any>(null)
const rejectReason = ref('')
const previewUrl = ref('')
const previewFileName = ref('')

const feedbackTitle = ref('操作提示')
const feedbackMessage = ref('')
const feedbackType = ref('success')
const feedbackIcon = ref('✅')

const showOperationFeedback = (message: string, type: 'success' | 'error' | 'info' = 'success') => {
  feedbackMessage.value = message
  feedbackType.value = type
  
  switch (type) {
    case 'success':
      feedbackIcon.value = '✅'
      feedbackTitle.value = '操作成功'
      break
    case 'error':
      feedbackIcon.value = '❌'
      feedbackTitle.value = '操作失败'
      break
    case 'info':
      feedbackIcon.value = 'ℹ️'
      feedbackTitle.value = '提示信息'
      break
  }
  
  showFeedback.value = true
}

const materialItems = ref({
  selfPractice: ['企业介绍', '实习计划', '安全协议'],
  unitChange: ['家庭证明', '新单位接收证明', '调动申请书'],
  delay: ['考研计划', '学习计划', '延迟申请书']
})

const applications = ref<any[]>([])

const fetchStats = async () => {
  try {
    const response = await approvalApi.getStats()
    approvalTabs.value[0].count = response.selfPracticePending
    approvalTabs.value[1].count = response.unitChangePending
    approvalTabs.value[2].count = response.delayPending
    approvalTabs.value[3].count = response.companyQualificationPending
  } catch (error) {
    console.error('[Approval] 获取统计数据失败:', error)
    approvalTabs.value[0].count = 0
    approvalTabs.value[1].count = 0
    approvalTabs.value[2].count = 0
    approvalTabs.value[3].count = 0
  }
}

const fetchApplications = async () => {
  loading.value = true
  try {
    let response
    if (activeTab.value === 'companyQualification') {
      response = await companyService.getPendingAuditCompanies({
        page: currentPage.value,
        pageSize: pageSize.value,
        companyName: searchForm.keyword || undefined
      })
      applications.value = response.data?.rows || []
      total.value = response.data?.total || 0
    } else {
      response = await approvalApi.getStudentApplications({
        page: currentPage.value,
        pageSize: pageSize.value,
        applicationType: activeTab.value,
        status: searchForm.status || undefined,
        studentName: searchForm.keyword || undefined,
        studentUserId: searchForm.keyword || undefined
      })
      applications.value = response.rows || []
      total.value = response.total || 0
    }
    
    applications.value = applications.value.map((app: any) => ({
      ...app,
      type: activeTab.value,
      studentId: app.studentUserId || app.studentId
    }))
  } catch (error) {
    console.error('[Approval] 获取申请列表失败:', error)
    showOperationFeedback('获取申请列表失败', 'error')
  } finally {
    loading.value = false
  }
}

watch(activeTab, () => {
  currentPage.value = 1
  fetchApplications()
})

watch(() => searchForm.keyword, () => {
  currentPage.value = 1
  fetchApplications()
})

watch(() => searchForm.status, () => {
  currentPage.value = 1
  fetchApplications()
})

const handleSearch = () => {
  currentPage.value = 1
  fetchApplications()
}

const resetForm = () => {
  searchForm.keyword = ''
  searchForm.status = ''
  currentPage.value = 1
  fetchApplications()
}

const handleSizeChange = (val: number) => {
  pageSize.value = val
  currentPage.value = 1
  fetchApplications()
}

const handleCurrentChange = (val: number) => {
  currentPage.value = val
  fetchApplications()
}

const filteredApplications = computed(() => {
  return applications.value
})

const totalFilteredCount = computed(() => {
  return total.value
})

const totalPages = computed(() => {
  return Math.ceil(total.value / pageSize.value)
})

const getApplicationTypeText = (type: string) => {
  switch (type) {
    case 'selfPractice':
      return '自主实习申请'
    case 'unitChange':
      return '单位变更申请'
    case 'delay':
      return '考研延迟申请'
    case 'companyQualification':
      return '企业资质审核'
    default:
      return '未知类型'
  }
}

const getStatusText = (status: string | number) => {
  if (typeof status === 'number') {
    switch (status) {
      case 0:
        return '待审核'
      case 1:
        return '已通过'
      case 2:
        return '已驳回'
      default:
        return '未知状态'
    }
  }
  
  switch (status) {
    case 'pending':
      return '待审核'
    case 'approved':
      return '已通过'
    case 'rejected':
      return '已驳回'
    default:
      return '未知状态'
  }
}

const getStatusTagType = (status: string | number) => {
  if (typeof status === 'number') {
    switch (status) {
      case 0:
        return 'warning'
      case 1:
        return 'success'
      case 2:
        return 'danger'
      default:
        return 'info'
    }
  }
  
  switch (status) {
    case 'pending':
      return 'warning'
    case 'approved':
      return 'success'
    case 'rejected':
      return 'danger'
    default:
      return 'info'
  }
}

const viewApplication = async (application: any) => {
  try {
    const response = await approvalApi.getStudentApplicationById(application.id)
    currentApplication.value = {
      ...response,
      type: response.applicationType || application.type
    }
    showApplicationDetailModal.value = true
  } catch (error) {
    showOperationFeedback('获取申请详情失败', 'error')
  }
}

// 批准申请
const approveApplication = async (application: any) => {
  try {
    let response
    const currentUser = getCurrentUser()
    const reviewerId = currentUser.id || 1
    
    if (application.type === 'companyQualification') {
      response = await companyService.auditCompanyTeacher(application.id, { auditStatus: 1 })
    } else {
      response = await approvalApi.approveStudentApplication(application.id, reviewerId)
    }
    
    showOperationFeedback('申请已成功通过', 'success')
    fetchApplications()
    fetchStats()
    
    showApplicationDetailModal.value = false
    showCompanyQualificationModal.value = false
  } catch (error: any) {
    showOperationFeedback(error.response?.data?.message || '批准申请失败', 'error')
  }
}

// 驳回申请
const rejectApplication = (application: any) => {
  currentApplication.value = application
  rejectReason.value = ''
  // 关闭所有可能的弹窗
  showApplicationDetailModal.value = false
  showCompanyQualificationModal.value = false
  // 打开驳回申请弹窗
  showRejectModal.value = true
}

// 查看企业资质
const viewCompanyQualification = async (application: any) => {
  try {
    currentApplication.value = application
    showCompanyQualificationModal.value = true
  } catch (error) {
    showOperationFeedback('获取企业资质详情失败', 'error')
  }
}

// 确认驳回
const confirmReject = async () => {
  if (!currentApplication.value) {
    ElMessage.warning('请选择要驳回的申请')
    return
  }
  
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回理由')
    return
  }
  
  try {
    let response
    const currentUser = getCurrentUser()
    const reviewerId = currentUser.id || 1
    
    if (currentApplication.value.type === 'companyQualification') {
      response = await companyService.auditCompanyTeacher(
        currentApplication.value.id, 
        { auditStatus: 2, auditRemark: rejectReason.value }
      )
    } else {
      response = await approvalApi.rejectStudentApplication(
        currentApplication.value.id, 
        reviewerId, 
        rejectReason.value
      )
    }
    
    showRejectModal.value = false
    showOperationFeedback('申请已成功驳回', 'success')
    fetchApplications()
    fetchStats()
  } catch (error: any) {
    showOperationFeedback(error.response?.data?.message || '驳回申请失败', 'error')
  }
}

const getCompanyDocumentUrl = (company: any, docKey: string) => {
  if (!company) return null
  
  if (docKey === 'legalIdCard') {
    if (company.legalIdCard) {
      const idCardImages = company.legalIdCard.split(',')
      return idCardImages[0] || null
    }
    return null
  }
  
  return company[docKey] || null
}

const getIndustryText = (industry: string) => {
  const industryMap: Record<string, string> = {
    'internet': '互联网/IT',
    'finance': '金融',
    'education': '教育',
    'medical': '医疗',
    'manufacturing': '制造业',
    'service': '服务业',
    'realestate': '房地产',
    'energy': '能源',
    'other': '其他'
  }
  return industryMap[industry] || industry || '未填写'
}

const getCooperationModeText = (mode: string) => {
  const modeMap: Record<string, string> = {
    'autonomous': '自主',
    'mutual_choice': '双向',
    'backup': '兜底'
  }
  return modeMap[mode] || mode || '未填写'
}

// 资料预览
const previewMaterial = (url: string, name: string) => {
  previewUrl.value = url
  previewFileName.value = name
  showMaterialPreviewModal.value = true
}

// 资料下载
const downloadMaterial = (url: string, name: string) => {
  const link = document.createElement('a')
  link.href = url
  link.download = `${name}_${new Date().getTime()}.pdf`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

// 初始化
onMounted(() => {
  fetchStats()
  fetchApplications()
})

// 监听路由变化，当从首页跳转过来时自动加载数据
watch(() => route.state?.fromHome, (fromHome) => {
  if (fromHome) {
    fetchStats()
    fetchApplications()
  }
}, { immediate: true })
</script>

<style scoped>
.approval-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.page-header {
  margin-bottom: 16px;
}

.page-header h2 {
  font-size: 28px;
  font-weight: 600;
  color: #333;
  margin: 0 0 8px 0;
}

.page-header p {
  font-size: 14px;
  color: #666;
  margin: 0;
}

.approval-tabs-card {
  margin-bottom: 20px;
}

.approval-tabs {
  display: flex;
  gap: 12px;
}

.tab-btn {
  flex: 1;
  padding: 12px 20px;
  background-color: #fafafa;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  color: #666;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.tab-btn:hover {
  background-color: #e6f7ff;
  border-color: #1890ff;
  color: #1890ff;
  transform: translateY(-2px);
}

.tab-btn.active {
  background-color: #1890ff;
  border-color: #1890ff;
  color: white;
  font-weight: 600;
}

.tab-badge {
  margin-left: 4px;
}

.table-card {
  margin-bottom: 20px;
}

.search-form {
  display: flex;
  align-items: center;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.search-actions {
  display: flex;
  gap: 8px;
}

.search-btn,
.reset-btn {
  display: flex;
  align-items: center;
  gap: 4px;
}

.data-table {
  margin-top: 16px;
}

.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
  align-items: center;
}

.table-btn {
  padding: 6px 12px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.custom-pagination {
  display: flex;
  justify-content: center;
}

.student-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.student-info > div:first-child {
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.student-info .info-details {
  display: flex;
  gap: 6px;
  justify-content: flex-end;
  align-items: center;
  flex-wrap: wrap;
}

.student-id {
  font-size: 12px;
  color: #666;
  background-color: #f0f2f5;
  padding: 2px 8px;
  border-radius: 4px;
}

.student-grade {
  font-size: 12px;
  color: #1890ff;
  background-color: #e6f7ff;
  padding: 2px 8px;
  border-radius: 4px;
}

.student-class {
  font-size: 12px;
  color: #52c41a;
  background-color: #f6ffed;
  padding: 2px 8px;
  border-radius: 4px;
}

.company-info-detail {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  align-items: center;
}

.company-info-detail.compact {
  gap: 0;
}

.company-info-detail.compact .company-name {
  font-size: 13px;
  color: #333;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.company-name {
  font-size: 14px;
  color: #333;
  font-weight: 600;
  width: 100%;
}

.contact-info {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.contact-person {
  font-size: 12px;
  color: #1890ff;
  background-color: #e6f7ff;
  padding: 2px 8px;
  border-radius: 4px;
}

.contact-phone {
  font-size: 12px;
  color: #52c41a;
  background-color: #f6ffed;
  padding: 2px 8px;
  border-radius: 4px;
}

/* 实习单位列样式 */
.company-name-display {
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

.company-change-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.company-change-info .old-company {
  font-size: 13px;
  color: #999;
}

.company-change-info .new-company {
  font-size: 13px;
  color: #52c41a;
  font-weight: 500;
}

.status-tag {
  font-weight: 500;
}

/* 状态栏样式 */
.status-wrapper {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: center;
  align-items: center;
}

.status-tag-base {
  margin-left: 4px;
}

.status-tag-company {
  margin-left: 4px;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background-color: white;
  border-radius: 8px;
  width: 600px;
  max-width: 90vw;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.15);
}

.modal-content.large {
  width: 900px;
}

.modal-content.company-qualification-modal {
  width: 800px;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid #f0f0f0;
  background-color: #1890ff;
  border-radius: 8px 8px 0 0;
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: white;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: white;
  padding: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.close-btn:hover {
  transform: rotate(90deg);
}

.modal-body {
  padding: 24px;
  max-height: 70vh;
  overflow-y: auto;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
  background-color: #fafafa;
  border-radius: 0 0 8px 8px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
}

.form-group textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 14px;
  resize: vertical;
  min-height: 120px;
  transition: all 0.3s ease;
}

.form-group textarea:focus {
  outline: none;
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2);
}

.student-info-display,
.application-type-display,
.company-name-display,
.company-address-display,
.contact-info-display,
.apply-time-display,
.student-name-display,
.student-id-display,
.grade-display,
.class-display,
.company-display,
.reason-display {
  padding: 12px 16px;
  background-color: #f8f9fa;
  border-radius: 4px;
  font-size: 14px;
  color: #333;
  font-weight: 500;
  border: 1px solid #e9ecef;
}

.form-row {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
}

.form-group.half {
  flex: 1;
  margin-bottom: 0;
}

.form-group.quarter {
  flex: 1;
  margin-bottom: 0;
}

.qualification-documents {
  margin-top: 20px;
}

.qualification-documents h4 {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 16px;
}

.document-grid {
  display: grid;
  gap: 16px;
}

.document-grid.horizontal {
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
}

.document-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.document-item label {
  font-size: 14px;
  font-weight: 500;
  color: #666;
}

.document-preview {
  width: 100%;
  height: 120px;
  border: 1px dashed #d9d9d9;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background-color: #fafafa;
}

.document-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  cursor: pointer;
  transition: all 0.3s ease;
}

.document-image:hover {
  transform: scale(1.05);
}

.no-document {
  color: #999;
  font-size: 14px;
}

.material-preview-container {
  width: 100%;
  height: 60vh;
  border-radius: 4px;
  overflow: hidden;
  background-color: white;
  border: 1px solid #e9ecef;
  display: flex;
  align-items: center;
  justify-content: center;
}

.material-preview-frame {
  width: 100%;
  height: 100%;
  border: none;
  background-color: white;
}

.no-preview {
  text-align: center;
  padding: 40px 20px;
}

.no-preview-icon {
  font-size: 48px;
  display: block;
  margin-bottom: 16px;
}

.no-preview p {
  font-size: 14px;
  color: #666;
  margin-bottom: 16px;
}

.feedback-modal {
  width: 400px;
}

.feedback-content {
  text-align: center;
  padding: 20px;
}

.feedback-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.feedback-icon.success {
  color: #52c41a;
}

.feedback-icon.error {
  color: #ff4d4f;
}

.feedback-icon.info {
  color: #1890ff;
}

.feedback-message {
  font-size: 16px;
  color: #333;
}

.btn-default {
  background-color: #fff;
  border: 1px solid #d9d9d9;
  color: #666;
}

.btn-default:hover {
  background-color: #f5f5f5;
  border-color: #ccc;
  color: #333;
}

.btn-sm {
  padding: 6px 16px;
  font-size: 14px;
  border-radius: 4px;
}

.btn-success {
  background-color: #52c41a;
  border-color: #52c41a;
  color: white;
}

.btn-success:hover {
  background-color: #73d13d;
  border-color: #73d13d;
}

.btn-danger {
  background-color: #ff4d4f;
  border-color: #ff4d4f;
  color: white;
}

.btn-danger:hover {
  background-color: #ff7875;
  border-color: #ff7875;
}

.btn-primary {
  background-color: #1890ff;
  border-color: #1890ff;
  color: white;
}

.btn-primary:hover {
  background-color: #40a9ff;
  border-color: #40a9ff;
}
</style>
