<template>
  <div class="approval-container">
    <div class="page-header">
      <h2>审核管理</h2>
      <p>管理学生自主实习、考研延迟及企业资质审核</p>
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
        <el-table-column v-if="activeTab === 'companyQualification'" label="企业名称" min-width="180">
          <template #default="scope">
            <div class="company-info-detail compact">
              <div class="company-name">{{ scope.row.companyName }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-else label="学生姓名" min-width="120" align="center">
          <template #default="scope">
            <div class="student-name-display">{{ scope.row.studentName }}</div>
          </template>
        </el-table-column>
        <el-table-column v-if="activeTab !== 'companyQualification'" label="学号/年级/班级" min-width="200" align="center">
          <template #default="scope">
            <div class="student-info">
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
                <div>{{ scope.row.company || '-' }}</div>
                <el-tag v-if="scope.row.cooperativeCompany" type="warning" size="small" class="cooperative-company-tag">校企合作企业</el-tag>
              </template>
              <template v-else>-</template>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="activeTab !== 'companyQualification' && activeTab !== 'delay'" label="实习岗位" min-width="140" align="center">
          <template #default="scope">
            <div class="company-name-display">
              <template v-if="scope.row.type === 'selfPractice'">
                {{ scope.row.positionName || '-' }}
              </template>
              <template v-else>-</template>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="applyTime" label="申请时间" min-width="160" align="center"></el-table-column>
        <el-table-column label="状态" min-width="120" align="center">
          <template #default="scope">
            <div class="status-wrapper">
              <el-tag :type="getStatusTagType(getRowStatus(scope.row))" size="small" class="status-tag">
                {{ getStatusText(getRowStatus(scope.row)) }}
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
            <MobileMoreActions>
              <el-button v-if="activeTab === 'companyQualification' ? scope.row.auditStatus === 0 : scope.row.status === 'pending'" size="small" type="success" @click="approveApplication(scope.row)" class="table-btn success">
                通过
              </el-button>
              <el-button v-if="activeTab === 'companyQualification' ? scope.row.auditStatus === 0 : scope.row.status === 'pending'" size="small" type="danger" @click="rejectApplication(scope.row)" class="table-btn danger">
                驳回
              </el-button>
              <el-button size="small" type="primary" @click="activeTab === 'companyQualification' ? viewCompanyQualification(scope.row) : viewApplication(scope.row)" class="table-btn primary">
                查看
              </el-button>
            </MobileMoreActions>
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
            <textarea v-model="rejectReason" placeholder="请填写驳回理由" rows="5"></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-default btn-sm" @click="showRejectModal = false">取消</button>
          <button class="btn-danger btn-sm" @click="confirmReject">确认驳回</button>
        </div>
      </div>
    </div>

    <!-- 企业注册申请审核查看弹窗 -->
    <div v-if="showCompanyQualificationModal" class="modal-overlay" @click.self="showCompanyQualificationModal = false">
      <div class="modal-content card company-qualification-modal">
        <div class="modal-header">
          <h3>企业注册申请审核</h3>
          <button class="close-btn" @click="showCompanyQualificationModal = false">×</button>
        </div>
        <div class="modal-body">
          <div v-if="currentApplication" class="company-qualification-info">
            <div class="form-row">
              <div class="form-group half">
                <label>企业名称：</label>
                <div class="company-name-display">{{ currentApplication.companyName || '未填写' }}</div>
              </div>
              <div class="form-group half">
                <label>统一社会信用代码：</label>
                <div class="contact-info-display">{{ currentApplication.creditCode || '未填写' }}</div>
              </div>
            </div>
            <div class="form-row">
              <div class="form-group half">
                <label>所属行业：</label>
                <div class="contact-info-display">{{ getIndustryText(currentApplication.industry) }}</div>
              </div>
              <div class="form-group half">
                <label>企业规模：</label>
                <div class="contact-info-display">{{ getScaleText(currentApplication.scale) }}</div>
              </div>
            </div>
            <div class="form-row">
              <div class="form-group half">
                <label>联系人：</label>
                <div class="contact-info-display">{{ currentApplication.contactPerson || '未填写' }}</div>
              </div>
              <div class="form-group half">
                <label>联系电话：</label>
                <div class="contact-info-display">{{ currentApplication.contactPhone || '未填写' }}</div>
              </div>
            </div>
            <div class="form-group">
              <label>企业地址：</label>
              <div class="company-address-display">{{ formatCompanyAddress(currentApplication) }}</div>
            </div>
            <div class="form-row">
              <div class="form-group half">
                <label>联系邮箱：</label>
                <div class="contact-info-display">{{ currentApplication.contactEmail || '未填写' }}</div>
              </div>
              <div class="form-group half">
                <label>个人邮箱：</label>
                <div class="contact-info-display">{{ currentApplication.email || '未填写' }}</div>
              </div>
            </div>
            <div class="form-group">
              <label>企业网站：</label>
              <div class="contact-info-display">{{ currentApplication.website || '未填写' }}</div>
            </div>
            <div class="form-group">
              <label>企业简介：</label>
              <div class="company-address-display">{{ currentApplication.introduction || '未填写' }}</div>
            </div>
            <div class="form-row">
              <div class="form-group half">
                <label>挂牌实习基地：</label>
                <div class="contact-info-display">{{ formatYesNo(currentApplication.isInternshipBase) }}</div>
              </div>
              <div class="form-group half">
                <label>接受兜底：</label>
                <div class="contact-info-display">{{ formatYesNo(currentApplication.acceptBackup) }}</div>
              </div>
            </div>
            <div class="form-group">
              <label>是否校友单位：</label>
              <div class="contact-info-display">{{ formatYesNo(currentApplication.isAlumniUnit) }}</div>
            </div>
            <div v-if="currentApplication.isAlumniUnit === 1" class="form-group">
              <label>校友信息：</label>
              <div v-if="currentApplication.alumniMembers?.length" class="alumni-info-display">
                <div v-for="(member, index) in currentApplication.alumniMembers" :key="member.id || index" class="alumni-member-display">
                  <strong>校友 {{ index + 1 }}</strong>
                  <span>姓名：{{ member.name || '未填写' }}</span>
                  <span>联系电话：{{ member.phone || '未填写' }}</span>
                  <span>在企业的任职：{{ member.position || '未填写' }}</span>
                </div>
              </div>
              <div v-else class="contact-info-display">未填写</div>
            </div>
            <div class="form-group">
              <label>是否与学校签署实训基地的协议：</label>
              <div class="contact-info-display">{{ formatYesNo(currentApplication.hasTrainingBaseAgreement) }}</div>
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
              <div class="form-group half">
                <label>姓名：</label>
                <div class="student-name-display">{{ currentApplication.studentName }}</div>
              </div>
              <div class="form-group half">
                <label>学号：</label>
                <div class="student-id-display">{{ currentApplication.studentUserId || currentApplication.studentId }}</div>
              </div>
            </div>
            <div class="form-row">
              <div class="form-group half">
                <label>年级：</label>
                <div class="grade-display">{{ currentApplication.grade || '未填写' }}</div>
              </div>
              <div class="form-group half">
                <label>班级：</label>
                <div class="class-display">{{ currentApplication.className }}</div>
              </div>
            </div>
            <div class="form-group">
              <label>申请理由：</label>
              <div class="reason-display">{{ currentApplication.reason }}</div>
            </div>
            
            <!-- 自主实习申请特有信息 -->
            <div v-if="currentApplication.type === 'selfPractice'" class="form-row">
              <div class="form-group half">
                <label>实习单位：</label>
                <div class="company-display">
                  {{ currentApplication.company || '未填写' }}
                  <el-tag v-if="currentApplication.cooperativeCompany" type="warning" size="small" class="cooperative-company-tag">校企合作企业</el-tag>
                </div>
              </div>
              <div class="form-group half">
                <label>实习岗位：</label>
                <div class="company-display">{{ currentApplication.positionName || '未填写' }}</div>
              </div>
            </div>
            <div v-if="currentApplication.type === 'selfPractice'" class="form-row">
              <div class="form-group half">
                <label>单位所在城市：</label>
                <div class="company-display">{{ currentApplication.companyCity || '未填写' }}</div>
              </div>
              <div class="form-group half">
                <label>申请时间：</label>
                <div class="apply-time-display">{{ currentApplication.applyTime || '未填写' }}</div>
              </div>
            </div>
            <div v-if="currentApplication.type === 'selfPractice'" class="form-row">
              <div class="form-group half">
                <label>人事经理姓名：</label>
                <div class="company-display">{{ currentApplication.hrManagerName || '未填写' }}</div>
              </div>
              <div class="form-group half">
                <label>人事经理联系电话：</label>
                <div class="company-display">{{ currentApplication.hrManagerPhone || '未填写' }}</div>
              </div>
            </div>
            <div v-if="currentApplication.type === 'selfPractice' && currentApplication.materials" class="form-group">
              <label>上传资料：</label>
              <div class="document-grid horizontal">
                <div class="document-item fade-in" v-for="label in materialItems.selfPractice" :key="label">
                  <label>{{ label }}：</label>
                  <div class="document-preview">
                    <button v-if="hasApplicationMaterial(label)" class="btn-default btn-sm"
                      @click="downloadApplicationMaterial(label)">下载</button>
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
                    <button v-if="hasApplicationMaterial(label)" class="btn-default btn-sm"
                      @click="downloadApplicationMaterial(label)">下载</button>
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
    
  </div>
</template>

<script setup lang="ts">
import { useAgentPageContext } from '@/agent/useAgentPageContext'
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useFormContext } from '@/agent/formContext'
import { approvalApi } from '../../api/teacherApproval'
import companyService from '../../api/company'
import { getCurrentUser } from '../../utils/teacherUser'
import { getCurrentTeacherType } from '../../utils/teacherPermissions'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import MobileMoreActions from '../../components/MobileMoreActions.vue'
import { studentApplicationMaterialByteUrl } from '@/utils/authorizedFileUrl'
import { downloadAuthorizedFile } from '@/utils/authorizedFileDownload'

// 自主实习申请由辅导员负责审核,考研延迟/企业注册由系室/学院教师负责
const teacherType = getCurrentTeacherType()
const isCounselor = teacherType === 'COUNSELOR'
const activeTab = ref(isCounselor ? 'selfPractice' : 'delay')
const approvalTabs = ref(
  isCounselor
    ? [{ key: 'selfPractice', name: '自主实习申请', count: 0 }]
    : [
        { key: 'delay', name: '考研延迟申请', count: 0 },
        { key: 'companyQualification', name: '企业注册申请审核', count: 0 }
      ]
)

const documentTypes = ref([
  { key: 'businessLicense', name: '营业执照' },
  { key: 'legalPersonId', name: '法人身份证' },
  { key: 'organizationCode', name: '组织机构代码证' },
  { key: 'basePhoto', name: '牌匾照片' }
])

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
const currentApplication = ref<any>(null)
const rejectReason = ref('')

const materialItems = ref({
  selfPractice: ['个人申请书', '实习接收函'],
  delay: ['考研计划', '学习计划', '延迟申请书']
})

const applications = ref<any[]>([])

const fetchStats = async () => {
  try {
    const response = await approvalApi.getStats() as unknown as {
      selfPracticePending?: number
      delayPending?: number
      companyQualificationPending?: number
      data?: { selfPracticePending?: number; delayPending?: number; companyQualificationPending?: number }
    }
    // 保持原运行时取值路径（顶层字段），仅用精确断言收敛类型
    approvalTabs.value[0].count = response.selfPracticePending ?? 0
    approvalTabs.value[1].count = response.delayPending ?? 0
    approvalTabs.value[2].count = response.companyQualificationPending ?? 0
  } catch (error) {
    console.error('[Approval] 获取统计数据失败:', error)
    approvalTabs.value[0].count = 0
    approvalTabs.value[1].count = 0
    approvalTabs.value[2].count = 0
  }
}

const fetchApplications = async () => {
  loading.value = true
  try {
    let response
    if (activeTab.value === 'companyQualification') {
      response = await approvalApi.getCompanyQualifications({
        page: currentPage.value,
        pageSize: pageSize.value,
        companyName: searchForm.keyword || undefined,
        status: searchForm.status || undefined
      })
      applications.value = response.rows || []
      total.value = response.total || 0
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
    ElMessage.error('获取申请列表失败')
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
    case 'delay':
      return '考研延迟申请'
    case 'companyQualification':
      return '企业资质审核'
    default:
      return '未知类型'
  }
}

// 企业注册行使用 auditStatus（0待审核/1已通过/2已驳回），学生申请行使用 status
const getRowStatus = (row: any) => {
  if (activeTab.value === 'companyQualification') {
    return row.auditStatus
  }
  return row.status
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
    const response = await approvalApi.getStudentApplicationById(application.id) as unknown as {
      applicationType?: string
      type?: string
      [key: string]: unknown
    }
    currentApplication.value = {
      ...response,
      type: response.applicationType || response.type || application.type
    }
    showApplicationDetailModal.value = true
  } catch (error) {
    ElMessage.error('获取申请详情失败')
  }
}

// 批准申请
const approveApplication = async (application: any) => {
  try {
    let response
    const currentUser = getCurrentUser()
    const reviewerId = currentUser.id || 1
    
    if (application.type === 'companyQualification') {
      response = await approvalApi.approveCompanyQualification(application.id, reviewerId)
    } else {
      response = await approvalApi.approveStudentApplication(application.id, reviewerId)
    }
    
    const serverMessage = response?.message && response.message !== 'success' ? response.message : ''
    if (application.type === 'companyQualification' && response?.data?.emailNotified === false) {
      ElMessage.warning(serverMessage || '申请已通过，但邮件通知未完成')
    } else {
      ElMessage.success(serverMessage || '申请已成功通过')
    }
    fetchApplications()
    fetchStats()
    
    showApplicationDetailModal.value = false
    showCompanyQualificationModal.value = false
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '批准申请失败')
  }
}

// ===== 智能填表：驳回理由注册到 AI 侧栏（只写响应式代理，不触发提交） =====
const formContext = useFormContext()
const rejectProxy = {
  get rejectReason() { return rejectReason.value },
  set rejectReason(v: unknown) { rejectReason.value = String(v ?? '') }
}
watch(showRejectModal, (open) => {
  if (open) {
    formContext.register({
      formId: 'teacher:approval-reject',
      title: '驳回申请',
      fields: [{ prop: 'rejectReason', label: '驳回理由', type: 'textarea', required: true }],
      getModel: () => rejectProxy,
      getValidationSummary: () => ({ missingFields: [], validationErrors: currentApplication.value && rejectReason.value.trim() ? [] : ['请选择申请并填写驳回理由'] }),
      getObject: () => currentApplication.value?.id ? { type: String(currentApplication.value.type || 'application'), id: String(currentApplication.value.id) } : null
    })
  } else formContext.unregister('teacher:approval-reject')
})
onUnmounted(() => formContext.unregister('teacher:approval-reject'))

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
    const response = await approvalApi.getCompanyQualificationById(application.id)
    currentApplication.value = { ...response, type: 'companyQualification' }
    showCompanyQualificationModal.value = true
  } catch (error) {
    ElMessage.error('获取企业资质详情失败')
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
    const operation = formContext.beginFormOperation('teacher:approval-reject')
    
    if (currentApplication.value.type === 'companyQualification') {
      response = await approvalApi.rejectCompanyQualification(
        currentApplication.value.id, 
        reviewerId, 
        rejectReason.value
      )
    } else {
      response = await approvalApi.rejectStudentApplication(
        currentApplication.value.id, 
        reviewerId, 
        rejectReason.value
      )
    }
    
    operation.complete({ status: 'submitted', label: '申请已驳回', url: '/teacher/approval' })
    showRejectModal.value = false
    const serverMessage = response?.message && response.message !== 'success' ? response.message : ''
    if (currentApplication.value.type === 'companyQualification' && response?.data?.emailNotified === false) {
      ElMessage.warning(serverMessage || '申请已驳回，但邮件通知未完成')
    } else {
      ElMessage.success(serverMessage || '申请已成功驳回')
    }
    fetchApplications()
    fetchStats()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '驳回申请失败')
  }
}

const attachmentName = (label: string, stored: unknown): string => {
  const extension = typeof stored === 'string' ? /\.([a-z0-9]{1,8})(?:$|[?#])/i.exec(stored)?.[1] : ''
  return extension ? `${label}.${extension}` : label
}
const downloadApplicationMaterial = async (label: string) => {
  const application = currentApplication.value
  const key = label === '考研计划' && application?.materials?.delayPlan ? 'delayPlan' : label
  const url = studentApplicationMaterialByteUrl(application?.id, key, true)
  if (!url) return ElMessage.error('申请材料记录不可用')
  try {
    await downloadAuthorizedFile(url, attachmentName(label, application?.materials?.[key]))
  } catch {
    ElMessage.error('申请材料下载失败，请检查权限或联系管理员')
  }
}

const hasApplicationMaterial = (label: string) => {
  const materials = currentApplication.value?.materials
  if (!materials || typeof materials !== 'object') return false
  return Boolean(materials[label] || (label === '考研计划' && materials.delayPlan))
}

const formatYesNo = (value: unknown): string => {
  if (value === 1 || value === true || value === '1') return '是'
  if (value === 0 || value === false || value === '0') return '否'
  return '未填写'
}

const formatCompanyAddress = (company: any): string => {
  const address = [company?.province, company?.city, company?.district, company?.detailAddress]
    .filter(Boolean)
    .join('')
  return address || company?.address || '未填写'
}

const getIndustryText = (industry: string) => {
  const industryMap: Record<string, string> = {
    'it': 'IT企业',
    'medical_equipment': '医疗设备企业',
    'hospital': '医院',
    'other': '其它'
  }
  return industryMap[industry] || industry || '未填写'
}

const getScaleText = (scale: string) => {
  const scaleMap: Record<string, string> = {
    '1-50': '1-50 人',
    '51-100': '51-100 人',
    '101-500': '101-500 人',
    '501-1000': '501-1000 人',
    '1000+': '1000 人以上'
  }
  return scaleMap[scale] || scale || '未填写'
}

const getCooperationModeText = (mode: string) => {
  const modeMap: Record<string, string> = {
    'autonomous': '自主',
    'mutual_choice': '双向',
    'backup': '兜底'
  }
  return modeMap[mode] || mode || '未填写'
}

// 初始化
onMounted(() => {
  fetchStats()
  fetchApplications()
  enableTableScrollBubbling()
})

// 使表格区域的滚轮事件冒泡到外层，解决鼠标在表格上时页面无法滚动的问题
const enableTableScrollBubbling = () => {
  setTimeout(() => {
    const tableElement = document.querySelector('.data-table')
    if (!tableElement) return

    // 查找父级的 el-scrollbar 容器
    const findParentScrollbar = (el: Element | null): HTMLElement | null => {
      if (!el) return null
      const scrollbarWrap = el.closest('.el-scrollbar__wrap')
      if (scrollbarWrap) return scrollbarWrap as HTMLElement
      return findParentScrollbar(el.parentElement)
    }

    const parentScrollbar = findParentScrollbar(tableElement)

    tableElement.addEventListener('wheel', (e: Event) => {
      const wheelEvent = e as WheelEvent
      
      // 如果找到了父级滚动容器，手动滚动它
      if (parentScrollbar) {
        wheelEvent.preventDefault()
        parentScrollbar.scrollTop += wheelEvent.deltaY
        parentScrollbar.scrollLeft += wheelEvent.deltaX
      }
    }, { passive: false })
  }, 500)
}
useAgentPageContext(() => ({
  title: '审核管理', view: showRejectModal.value ? 'rejection-form' : showApplicationDetailModal.value || showCompanyQualificationModal.value ? 'application-detail' : 'page',
  // Application IDs have no teacher-scoped read-tool contract yet; do not mislabel student numbers as IDs.
  object: null, filters: { keyword: searchForm.keyword, status: searchForm.status, applicationType: activeTab.value }
}))
</script>

<style scoped>
.approval-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 20px;
}

.page-header {
  margin-bottom: 8px;
}

.page-header h2 {
  font-size: 24px;
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
  margin-bottom: 12px;
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
  margin-bottom: 12px;
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
  margin-top: 8px;
}

.data-table :deep(.el-table__body-wrapper) {
  overflow: visible !important;
}

.data-table :deep(.el-scrollbar) {
  overflow: visible !important;
}

.data-table :deep(.el-scrollbar__wrap) {
  overflow: visible !important;
}

.data-table :deep(.el-table__inner-wrapper) {
  overflow: visible !important;
}

/* 手机端：恢复表格内部横向滚动（桌面的 overflow:visible hack 会让触摸横滑失效） */
@media (max-width: 768px) {
  .data-table :deep(.el-scrollbar__wrap) {
    overflow-x: auto !important;
  }
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
  margin-top: 12px;
  display: flex;
  justify-content: center;
}

.custom-pagination {
  display: flex;
  justify-content: center;
}

.student-info {
  display: flex;
  gap: 6px;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
}

.student-info .info-details {
  display: flex;
  gap: 6px;
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
  background-color: #fff;
  border: none;
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

.cooperative-company-tag {
  margin-top: 4px;
  margin-left: 6px;
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
  background-color: #fff;
  border-radius: 4px;
  font-size: 14px;
  color: #333;
  font-weight: 500;
  border: none;
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

.alumni-info-display {
  display: grid;
  gap: 10px;
}

.alumni-member-display {
  display: grid;
  grid-template-columns: 72px 1fr 1fr 1.35fr;
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
  background-color: #fff;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  font-size: 14px;
  color: #333;
}

.alumni-member-display strong {
  color: #409eff;
  font-weight: 600;
}

.alumni-member-display span {
  min-width: 0;
  overflow-wrap: anywhere;
}

@media (max-width: 768px) {
  .form-row {
    flex-wrap: wrap;
  }

  .form-group.half {
    flex: 1 1 240px;
  }

  .alumni-member-display {
    grid-template-columns: 1fr;
    gap: 6px;
  }
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
