<template>
  <div class="applications-page">
    <!-- 页面头部：模仿欢迎区域的渐变风格 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-text">
          <h2>我的申请</h2>
          <p>查看您的申请记录</p>
        </div>
      </div>
    </div>


<!-- 折叠筛选区域 -->
    <div class="filter-collapse-section">
      <!-- 折叠头部：显示摘要和展开按钮 -->
      <div class="filter-collapse-header" @click="toggleFilterCollapse">
        <div class="filter-summary">
          <el-icon class="filter-icon"><Filter /></el-icon>
          <span class="filter-summary-text">{{ filterSummaryText }}</span>
        </div>
        <div class="filter-collapse-action">
          <span class="filter-collapse-text">{{ filterCollapsed ? '展开筛选' : '收起筛选' }}</span>
          <el-icon :class="['filter-collapse-arrow', { collapsed: filterCollapsed }]">
            <ArrowUp />
          </el-icon>
        </div>
      </div>

      <!-- 折叠内容 -->
      <div :class="['filter-collapse-content', { collapsed: filterCollapsed }]">
        <!-- 申请类型筛选标签 -->
        <div class="type-filter-section">
          <div class="type-filter-label">申请类型：</div>
          <div class="type-filter-tabs">
            <div
              v-for="type in applicationTypes"
              :key="type.value"
              :class="['type-filter-tab', { active: selectedType === type.value }]"
              @click="selectedType = type.value"
            >
              {{ type.label }}
            </div>
          </div>
        </div>

        <!-- 状态筛选标签 -->
        <div class="filter-section">
          <div
            v-for="filter in filters"
            :key="filter.value"
            :class="['filter-tab', { active: selectedFilter === filter.value }]"
            @click="selectedFilter = filter.value"
          >
            {{ filter.label }}
          </div>
        </div>
      </div>
    </div>

    <!-- 提示信息区域：选择申请类型后显示 -->
    <div v-if="selectedType === 'job'" class="tips-section">
      <div class="tips-card">
        <el-icon class="tips-icon"><InfoFilled /></el-icon>
        <div class="tips-content">
          <span class="tips-text">申请岗位后，企业同意时会显示</span>
          <span class="tips-highlight">"已通过"</span>
          <span class="tips-text">状态，代表您已通过企业审核，可以去参加面试了，面试信息可在</span>
          <span class="tips-link" @click="goToInterview">面试管理</span>
          <span class="tips-text">页面查看</span>
        </div>
      </div>
    </div>

    <!-- 申请列表 + 空状态 -->
    <div v-loading="applicationsLoading" class="list-wrapper" :aria-busy="applicationsLoading">
      <el-alert v-if="applicationsError" :title="applicationsError" type="error" :closable="false" show-icon>
        <el-button link type="primary" :loading="applicationsLoading" @click="fetchApplications">重新加载</el-button>
      </el-alert>
      <div v-if="filteredApplications.length > 0" class="applications-list">
        <div
          v-for="application in filteredApplications"
          :key="`${application.applicationType}:${application.id}`"
          :class="['application-card', { 'deep-link-target': `${application.applicationType}:${application.id}` === highlightedApplicationId }]"
          :data-activity-id="application.id"
          @click="viewApplicationDetail(application)"
        >
          <div class="card-header">
            <div class="card-title">{{ application.jobTitle }}</div>
            <div :class="['status-tag', application.status]">
              {{ getStatusText(application.status, application.directHire) }}
            </div>
          </div>
          <div class="card-body">
            <div class="card-info">
              <div class="info-item">
                <el-icon><OfficeBuilding /></el-icon>
                <span>{{ application.company }}</span>
              </div>
              <div class="info-item">
                <el-icon><Location /></el-icon>
                <span>{{ application.location }}</span>
              </div>
              <div class="info-item">
                <el-icon><Money /></el-icon>
                <span>{{ application.salary }}</span>
              </div>
            </div>
            <div class="card-footer">
              <span class="apply-time">{{ application.applyDate }}</span>
              <div class="card-actions">
                <button
                  v-if="application.status === 'pending' && (application.applicationType === 'job' || application.applicationType === 'delay')"
                  class="action-button withdraw"
                  @click.stop="withdrawApplication(application)"
                >
                  撤回申请
                </button>
                <button
                  class="action-button view"
                  @click.stop="viewApplicationDetail(application)"
                >
                  <el-icon><ArrowRight /></el-icon>
                  查看详情
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-else-if="!applicationsLoading && !applicationsError" class="empty-state">
        <el-icon class="empty-icon"><Document /></el-icon>
        <div class="empty-text">暂无{{ getFilterLabel() }}的申请记录</div>
        <el-button type="primary" class="go-browse-button" @click="goToJobs">
          去浏览职位
        </el-button>
      </div>
    </div>

    <!-- 申请详情对话框：统一风格 -->
    <el-dialog
      v-model="showDetailDialog"
      width="900px"
      class="detail-dialog"
      append-to-body
      lock-scroll
      modal-class="global-modal"
      :title="''"
    >
      <div v-if="currentApplication" class="dialog-content">
        <!-- 蓝绿色标题栏 -->
        <div class="header-section">
          <h1 class="header-title">申请详情</h1>
        </div>

        <div class="detail-content">
          <!-- 职位标题和状态 -->
          <div class="detail-header">
            <div class="detail-title">
              <h3>{{ currentApplication.jobTitle }}</h3>
            </div>
            <div :class="['detail-status', currentApplication.status]">
              {{ getStatusText(currentApplication.status, currentApplication.directHire) }}
            </div>
          </div>

          <!-- 基本信息模块 -->
          <div class="detail-module">
            <div class="module-header">
              <div class="module-icon-container">
                <el-icon class="module-icon"><OfficeBuilding /></el-icon>
              </div>
              <h2 class="module-title">基本信息</h2>
            </div>
            <div class="info-grid">
              <div class="info-item">
                <span class="info-label">公司：</span>
                <span class="info-value">{{ currentApplication.company || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">岗位：</span>
                <span class="info-value">{{ currentApplication.positionName || currentApplication.jobTitle || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">{{ currentApplication.applicationType === 'selfPractice' ? '单位所在城市：' : '地点：' }}</span>
                <span class="info-value">{{ currentApplication.location || '-' }}</span>
              </div>
              <div v-if="currentApplication.applicationType === 'selfPractice'" class="info-item">
                <span class="info-label">人事经理姓名：</span>
                <span class="info-value">{{ currentApplication.hrManagerName || '-' }}</span>
              </div>
              <div v-if="currentApplication.applicationType === 'selfPractice'" class="info-item">
                <span class="info-label">联系电话：</span>
                <span class="info-value">{{ currentApplication.hrManagerPhone || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">薪资：</span>
                <span class="info-value">{{ currentApplication.salary || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">行业：</span>
                <span class="info-value">{{ currentApplication.industryName || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">申请时间：</span>
                <span class="info-value">{{ currentApplication.applyDateTime || currentApplication.applyDate || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">申请状态：</span>
                <span class="info-value">{{ getStatusText(currentApplication.status, currentApplication.directHire) }}</span>
              </div>
              <div
                v-if="currentApplication.applicationType === 'job'
                  && currentApplication.status === 'rejected'
                  && currentApplication.rejectReason"
                class="info-item delay-reason-item"
              >
                <span class="info-label">拒绝理由：</span>
                <span class="info-value delay-reason">{{ currentApplication.rejectReason }}</span>
              </div>
            </div>
          </div>

          <!-- 考研延迟申请内容模块（仅延期申请） -->
          <div v-if="currentApplication.applicationType === 'delay'" class="detail-module">
            <div class="module-header">
              <div class="module-icon-container">
                <el-icon class="module-icon"><Document /></el-icon>
              </div>
              <h2 class="module-title">申请内容</h2>
            </div>
            <div class="info-item delay-reason-item">
              <span class="info-label">申请理由：</span>
              <span class="info-value delay-reason">{{ currentApplication.reason || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">考研计划：</span>
              <span v-if="hasDelayPlan" class="info-value">已上传，可在下方“我的申请材料”中预览或下载</span>
              <span v-else class="info-value">-</span>
            </div>
          </div>

          <!-- 职位描述模块（仅岗位申请） -->
          <div v-if="currentApplication.applicationType === 'job'" class="detail-module">
            <div class="module-header">
              <div class="module-icon-container">
                <el-icon class="module-icon"><Document /></el-icon>
              </div>
              <h2 class="module-title">职位描述</h2>
            </div>
            <p class="detail-description">{{ currentApplication.description }}</p>
          </div>

          <!-- 任职要求模块（仅岗位申请） -->
          <div v-if="currentApplication.applicationType === 'job'" class="detail-module">
            <div class="module-header">
              <div class="module-icon-container">
                <el-icon class="module-icon"><User /></el-icon>
              </div>
              <h2 class="module-title">任职要求</h2>
            </div>
            <ul class="detail-list">
              <li v-for="(item, index) in currentApplication.requirements" :key="index">{{ item }}</li>
            </ul>
          </div>

          <!-- 联系方式模块（仅岗位申请） -->
          <div v-if="currentApplication.applicationType === 'job'" class="detail-module">
            <div class="module-header">
              <div class="module-icon-container">
                <el-icon class="module-icon"><Message /></el-icon>
              </div>
              <h2 class="module-title">联系方式</h2>
            </div>
            <div class="contact-info">
              <div class="contact-item">
                <span class="contact-label">联系人：</span>
                <span class="contact-value">{{ currentApplication.contactPerson || '-' }}</span>
              </div>
              <div class="contact-item">
                <span class="contact-label">电话：</span>
                <span class="contact-value">{{ currentApplication.contactPhone || '-' }}</span>
              </div>
              <div class="contact-item">
                <span class="contact-label">邮箱：</span>
                <span class="contact-value">{{ currentApplication.contactEmail || '-' }}</span>
              </div>
            </div>
          </div>

          <!-- 申请材料模块 -->
          <div class="detail-module">
            <div class="module-header">
              <div class="module-icon-container">
                <el-icon class="module-icon"><Upload /></el-icon>
              </div>
              <h2 class="module-title">我的申请材料</h2>
              <el-button v-if="currentApplication.applicationType === 'selfPractice'" link type="primary" @click="agreementVisible = true">实习协议</el-button>
            </div>

            <div v-if="materialsLoading" class="materials-loading">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>加载材料中...</span>
            </div>

            <template v-else>
              <!-- 申请专属材料 -->
              <div v-if="appMaterials.length > 0" class="materials-section">
                <div class="materials-subtitle">申请材料</div>
                <div class="material-list">
                  <div v-for="item in appMaterials" :key="item.label" class="material-item">
                    <div class="material-info">
                      <el-icon class="material-icon"><Document /></el-icon>
                      <span class="material-name">{{ item.displayName }}</span>
                    </div>
                    <div class="material-actions">
                      <el-button type="primary" size="small" link @click="previewApplicationMaterial(item.label)">
                        <el-icon><View /></el-icon>
                        预览
                      </el-button>
                      <el-button type="primary" size="small" link @click="downloadApplicationMaterial(item.label)">
                        <el-icon><Download /></el-icon>
                        下载
                      </el-button>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 简历列表 -->
              <div v-if="applicationMaterials.resumes.length > 0" class="materials-section">
                <div class="materials-subtitle">简历</div>
                <div class="material-list">
                  <div
                    v-for="resume in applicationMaterials.resumes"
                    :key="resume.id"
                    class="material-item"
                  >
                    <div class="material-info">
                      <el-icon class="material-icon"><Document /></el-icon>
                      <span class="material-name">{{ resume.name }}</span>
                    </div>
                    <div class="material-actions">
                      <el-tag v-if="resume.uploadTime" size="small" type="info" class="upload-time-tag">
                        {{ resume.uploadTime }}
                      </el-tag>
                      <el-button type="primary" size="small" link @click="previewProfileMaterial('resume', resume)">
                        <el-icon><View /></el-icon>
                        预览
                      </el-button>
                      <el-button type="primary" size="small" link @click="downloadResume(resume)">
                        <el-icon><Download /></el-icon>
                        下载
                      </el-button>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 证书列表 -->
              <div v-if="applicationMaterials.certificates.length > 0" class="materials-section">
                <div class="materials-subtitle">证书</div>
                <div class="material-list">
                  <div
                    v-for="cert in applicationMaterials.certificates"
                    :key="cert.id"
                    class="material-item"
                  >
                    <div class="material-info">
                      <el-icon class="material-icon"><Medal /></el-icon>
                      <span class="material-name">{{ cert.name }}</span>
                    </div>
                    <div class="material-actions">
                      <el-tag v-if="cert.uploadTime" size="small" type="info" class="upload-time-tag">
                        {{ cert.uploadTime }}
                      </el-tag>
                      <el-button type="primary" size="small" link @click="previewProfileMaterial('certificate', cert)">
                        <el-icon><View /></el-icon>
                        预览
                      </el-button>
                      <el-button type="primary" size="small" link @click="downloadCertificate(cert)">
                        <el-icon><Download /></el-icon>
                        下载
                      </el-button>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 无材料提示 -->
              <div v-if="appMaterials.length === 0 && applicationMaterials.resumes.length === 0 && applicationMaterials.certificates.length === 0" class="no-materials">
                <el-empty description="暂无已上传的申请材料" :image-size="60" />
                <p class="no-materials-tip">请前往个人中心的简历管理页面上传简历和证书</p>
              </div>
            </template>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showDetailDialog = false" class="close-btn">
            关闭
          </el-button>
        </div>
      </template>
    </el-dialog>

    <InternshipAgreementDialog v-if="currentApplication?.applicationType === 'selfPractice'" v-model="agreementVisible"
      source-type="SELF_PRACTICE" :source-id="Number(currentApplication.id)" />
    <FilePreviewDialog v-if="showDetailDialog" v-model="materialPreviewVisible" file-url=""
      :file-name="materialPreviewName" :authorized-url="materialPreviewUrl" />
  </div>
</template>

<script setup lang="ts">
import { useAgentPageContext } from "@/agent/useAgentPageContext"

import { ref, computed, onMounted, onUnmounted, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { ElMessageBox, ElMessage } from "element-plus";
import {
  Document,
  User,
  ArrowRight,
  OfficeBuilding,
  Location,
  Money,
  Message,
  InfoFilled,
  Download,
  View,
  Medal,
  Loading,
  Filter,
  ArrowUp,
} from "@element-plus/icons-vue";

import request from '@/utils/request';
import InternshipAgreementDialog from '@/components/student/InternshipAgreementDialog.vue'
import FilePreviewDialog from '@/components/FilePreviewDialog.vue'
import { studentApplicationMaterialByteUrl, studentProfileByteUrl } from '@/utils/authorizedFileUrl'
import { downloadAuthorizedFile } from '@/utils/authorizedFileDownload'
import { useAuthStore } from '@/store/auth';
import type { ApplicationStatusUpdateData } from '@/utils/websocket';
import { onApplicationStatusUpdate, offApplicationStatusUpdate } from '@/utils/websocket';
import { getApplicationStatusText } from '@/utils/directHire';

const authStore = useAuthStore()

// 从 auth store 获取学生 ID
const getStudentId = () => {
  return authStore.user?.id
};

const router = useRouter();
const route = useRoute();

// 状态管理
const showDetailDialog = ref(false);
const agreementVisible = ref(false)
const materialPreviewVisible = ref(false)
const materialPreviewName = ref('')
const materialPreviewUrl = ref('')
const selectedFilter = ref("all");
const selectedType = ref("all");
const currentApplication = ref(null);
const filterCollapsed = ref(true); // 默认折叠

watch(() => [showDetailDialog.value, currentApplication.value?.id, currentApplication.value?.applicationType], () => {
  materialPreviewVisible.value = false
  materialPreviewName.value = ''
  materialPreviewUrl.value = ''
})

// 切换筛选区域折叠状态
const toggleFilterCollapse = () => {
  filterCollapsed.value = !filterCollapsed.value;
};

// 筛选条件摘要文本
const filterSummaryText = computed(() => {
  const typeFilter = applicationTypes.find((t) => t.value === selectedType.value);
  const statusFilter = filters.find((f) => f.value === selectedFilter.value);

  const typeText = typeFilter ? typeFilter.label : '全部';
  const statusText = statusFilter ? statusFilter.label : '全部';

  if (selectedType.value === 'all' && selectedFilter.value === 'all') {
    return '全部申请';
  }

  const parts = [];
  if (selectedType.value !== 'all') parts.push(typeText);
  if (selectedFilter.value !== 'all') parts.push(statusText);

  return parts.join(' · ');
});

// 申请详情中的简历和证书数据（带缓存）
interface ApplicationMaterialRow {
  id?: number | string
  name?: string
  url?: string
  [key: string]: unknown
}
const applicationMaterials = ref<{
  resumes: ApplicationMaterialRow[]
  certificates: ApplicationMaterialRow[]
}>({
  resumes: [],
  certificates: []
});
const materialsLoading = ref(false);
// 材料数据缓存标记，避免重复加载
const materialsCacheLoaded = ref(false);

// 筛选选项
const filters = [
  { label: "全部", value: "all" },
  { label: "待审核", value: "pending" },
  { label: "已通过", value: "approved" },
  { label: "已拒绝", value: "rejected" },
  { label: "已撤回", value: "withdrawn" },
];

// 申请类型选项（单位变更已停用，历史记录并入"全部"；考研延迟启用中，提供专属筛选）
const applicationTypes = [
  { label: "全部", value: "all" },
  { label: "岗位申请", value: "job" },
  { label: "自主实习", value: "selfPractice" },
  { label: "考研延迟", value: "delay" },
];

const applications = ref([])
const applicationsLoading = ref(false)
const applicationsError = ref('')
let applicationsRequestId = 0
const highlightedApplicationId = ref<string | number | null>(null)

// 状态值转换：后端数字/字符串到前端字符串
const statusMap = {
  '0': 'pending',
  '1': 'approved',
  '2': 'rejected',
  '3': 'hired',
  'pending': 'pending',
  'approved': 'approved',
  'rejected': 'rejected',
  'withdrawn': 'withdrawn',
  'cancelled': 'withdrawn',
  'canceled': 'withdrawn',
  'hired': 'hired',
  'pending_interview': 'pending_interview',
  'interview': 'interview',
  'interview_passed': 'interview_passed',
  'interview_failed': 'interview_failed'
};

// 申请页只反映申请审批链：企业同意后无论面试结果如何都显示"已通过"，面试细节看面试管理页
// 直录保留原始状态以显示"直接录取（免面试）"
const normalizeApplicationStatus = (status, directHire) => {
  if (directHire && status === 'interview_passed') return status;
  if (['pending_interview', 'interview', 'interview_passed', 'interview_failed'].includes(status)) return 'approved';
  return status;
};

// 申请时间格式化到分钟（yyyy-MM-dd HH:mm），直接解析字符串避免 UTC 转换偏移
const formatDateTimeMinute = (time?: string | null) => {
  if (!time) return ''
  const m = String(time).match(/(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})/)
  if (m) return `${m[1]} ${m[2]}`
  const d = new Date(String(time))
  if (isNaN(d.getTime())) return String(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
interface StudentApplicationRow {
  id?: number | string
  positionName?: string
  position_name?: string
  companyName?: string
  company_name?: string
  company?: string
  applicationType?: string
  application_type?: string
  oldCompany?: string
  newCompany?: string
  companyCity?: string
  location?: string
  hrManagerName?: string
  hrName?: string
  contactPerson?: string
  recruiterName?: string
  hrManagerPhone?: string
  hrPhone?: string
  contactPhone?: string
  recruiterPhone?: string
  hrEmail?: string
  contactEmail?: string
  recruiterEmail?: string
  status?: string | number
  directHire?: boolean
  direct_hire?: boolean
  applyTime?: string
  apply_time?: string
  createTime?: string
  jobTitle?: string
  salary?: string
  [key: string]: unknown
}
const studentApplicationRows = ref<StudentApplicationRow[]>([])
const jobApplicationRows = ref<StudentApplicationRow[]>([])

// 两类申请独立加载；部分接口失败时保留已成功的数据并明确提示。
const fetchApplications = async () => {
  const requestId = ++applicationsRequestId
  applicationsLoading.value = true
  applicationsError.value = ''
  try {
    // 并行获取学生申请(自主实习/单位变更/考研延迟)和岗位申请
    const responses = await Promise.allSettled([
      request.get<StudentApplicationRow[]>(`/student/applications`),
      request.get<StudentApplicationRow[]>(`/student/job-applications`)
    ]);
    if (requestId !== applicationsRequestId) return

    const sources = [studentApplicationRows, jobApplicationRows]
    const sourceNames = ['自主实习/单位变更/考研延迟申请', '岗位申请']
    const failedSources = []
    responses.forEach((result, index) => {
      if (result.status === 'fulfilled' && result.value.code === 200 && Array.isArray(result.value.data)) {
        sources[index].value = result.value.data
      } else {
        failedSources.push(sourceNames[index])
      }
    })
    applicationsError.value = failedSources.length ? `${failedSources.join('、')}加载失败，请重试` : ''

    const allApplications = [];

    // 处理学生申请(自主实习/单位变更/考研延迟)
    studentApplicationRows.value.forEach(app => {
      const applicationType = app.applicationType || app.application_type;
      let jobTitle = app.positionName || app.position_name || '';
      let company = app.companyName || app.company_name || app.company || '';

      // 对于学生申请设置合适的显示标题
      if (applicationType === 'selfPractice') {
        jobTitle = jobTitle || '自主实习申请';
        company = company || app.company || '';
      } else if (applicationType === 'unitChange') {
        jobTitle = jobTitle || '单位变更申请';
        company = app.oldCompany && app.newCompany
          ? `${app.oldCompany} → ${app.newCompany}`
          : (app.oldCompany || app.newCompany || '');
      } else if (applicationType === 'delay') {
        jobTitle = jobTitle || '考研延迟申请';
        company = company || '';
      }

      allApplications.push({
        ...app,
        applicationType,
        jobTitle: jobTitle || '申请记录',
        company,
        location: app.companyCity || app.location || '',
        contactPerson: app.hrManagerName || app.hrName || app.contactPerson || app.recruiterName || '',
        contactPhone: app.hrManagerPhone || app.hrPhone || app.contactPhone || app.recruiterPhone || '',
        contactEmail: app.hrEmail || app.contactEmail || app.recruiterEmail || '',
        status: normalizeApplicationStatus(statusMap[String(app.status ?? '').trim().toLowerCase()] || 'unknown', app.directHire),
        applyTime: app.applyTime || app.apply_time || app.createTime,
        applyDate: formatDateTimeMinute(app.applyTime || app.apply_time || app.createTime).slice(0, 10),
        applyDateTime: formatDateTimeMinute(app.applyTime || app.apply_time || app.createTime)
      });
    });

    // 处理岗位申请
    jobApplicationRows.value.forEach(app => {
      allApplications.push({
        ...app,
        applicationType: 'job', // 岗位申请标记为 job
        jobTitle: app.positionName || app.position_name || app.jobTitle || '岗位申请',
        company: app.companyName || app.company_name || app.company || '',
        location: app.location || '',
        salary: app.salary || '',
        contactPerson: app.hrName || app.contactPerson || app.recruiterName || '',
        contactPhone: app.hrPhone || app.contactPhone || app.recruiterPhone || '',
        contactEmail: app.hrEmail || app.contactEmail || app.recruiterEmail || '',
        status: normalizeApplicationStatus(statusMap[String(app.status ?? '').trim().toLowerCase()] || 'unknown', app.directHire),
        applyTime: app.createTime || (app.applyDate as string),
        applyDate: formatDateTimeMinute(app.createTime || (app.applyDate as string)).slice(0, 10),
        applyDateTime: formatDateTimeMinute(app.createTime || (app.applyDate as string))
      });
    });

    applications.value = allApplications;
  } catch (error) {
    if (requestId !== applicationsRequestId) return
    console.error('获取申请数据失败:', error);
    applicationsError.value = '申请记录加载失败，请重试';
  } finally {
    if (requestId === applicationsRequestId) applicationsLoading.value = false
  }
};

// 计算属性：筛选后的申请
const filteredApplications = computed(() => {
  // 只需要 jobTitle 存在即可（单位变更和考研延迟申请没有 company）
  const validApplications = applications.value.filter(
    (app) => app.jobTitle
  );

  // 先按申请类型筛选
  let result = validApplications;
  if (selectedType.value !== "all") {
    result = result.filter(
      (app) => app.applicationType === selectedType.value,
    );
  }

  // 再按状态筛选：面试结果已归一为申请语义，直录记录仍为 interview_passed（同属企业同意录用）
  if (selectedFilter.value !== "all") {
    result = result.filter((app) => {
      if (selectedFilter.value === "approved") {
        return ['approved', 'interview_passed', 'hired'].includes(app.status);
      }
      return app.status === selectedFilter.value;
    });
  }

  // 按申请时间从新到旧排序（最新的排第一张）
  return [...result].sort((a, b) => {
    const ta = new Date(a.applyTime || a.applyDateTime || a.applyDate || a.createTime || 0).getTime() || 0;
    const tb = new Date(b.applyTime || b.applyDateTime || b.applyDate || b.createTime || 0).getTime() || 0;
    return tb - ta || Number(b.id || 0) - Number(a.id || 0);
  });
});

// 方法：获取筛选标签文本
const getFilterLabel = () => {
  const statusFilter = filters.find((f) => f.value === selectedFilter.value);
  const typeFilter = applicationTypes.find((t) => t.value === selectedType.value);

  const statusText = statusFilter ? statusFilter.label : "";
  const typeText = typeFilter && selectedType.value !== "all" ? typeFilter.label : "";

  if (statusText === "全部" && !typeText) {
    return "";
  }
  if (typeText && statusText === "全部") {
    return typeText;
  }
  if (typeText && statusText !== "全部") {
    return `${typeText} + ${statusText}`;
  }
  return statusText;
};

// 方法：获取状态中文文本
const getStatusText = (status, directHire = false) => status === 'unknown' ? '未知状态' : getApplicationStatusText(status, directHire);

// 考研计划材料上传状态；在按申请记录授权下载前不暴露存储地址。
const hasDelayPlan = computed(() => {
  const materials = currentApplication.value?.materials
  if (!materials) return false
  try {
    const parsed = typeof materials === 'string' ? JSON.parse(materials) : materials
    return Boolean(parsed?.delayPlan)
  } catch {
    return false
  }
})

// 方法：查看申请详情
const viewApplicationDetail = async (application) => {
  // 处理 requirements 字段，确保是数组格式
  let requirements = application.requirements
  if (typeof requirements === 'string') {
    // 如果是字符串，按换行符分割成数组
    requirements = requirements.split('\n').filter(item => item.trim())
  }
  // 确保是数组
  if (!Array.isArray(requirements)) {
    requirements = []
  }
  // 处理联系人字段映射
  const contactPerson = application.hrManagerName || application.hrName || application.contactPerson || application.recruiterName || ''
  const contactPhone = application.hrManagerPhone || application.hrPhone || application.contactPhone || application.recruiterPhone || ''
  const contactEmail = application.hrEmail || application.contactEmail || application.recruiterEmail || ''

  currentApplication.value = {
    ...application,
    requirements, // 使用处理后的 requirements
    contactPerson,
    contactPhone,
    contactEmail,
    jobTitle: application.jobTitle || application.positionName,
    company: application.company || application.companyName
  };
  showDetailDialog.value = true;

  // 加载申请人的简历和证书材料
  await loadApplicationMaterials();
};

const clearApplicationDeepLink = () => {
  const query = { ...route.query }
  delete query.applicationId
  router.replace({ path: route.path, query })
}

const openApplicationFromQuery = async () => {
  const targetId = route.query.applicationId
  if (!targetId) return
  const targetType = typeof route.query.applicationType === 'string'
    ? route.query.applicationType
    : 'job'

  const target = applications.value.find((application) => (
    String(application.id) === String(targetId) && application.applicationType === targetType
  ))
  if (!target) {
    highlightedApplicationId.value = null
    ElMessage.warning('未找到该申请记录，已为您显示申请列表')
    clearApplicationDeepLink()
    return
  }

  selectedType.value = target.applicationType
  selectedFilter.value = 'all'
  highlightedApplicationId.value = `${target.applicationType}:${target.id}`
  await viewApplicationDetail(target)
}

// 加载申请人的简历和证书材料（带缓存优化）
const loadApplicationMaterials = async () => {
  // 如果已经加载过材料数据，直接使用缓存
  if (materialsCacheLoaded.value) {
    return;
  }

  materialsLoading.value = true;
  try {
    // 并行加载简历和证书
    const [resumeRes, certRes] = await Promise.all([
      request.get<ApplicationMaterialRow[]>('/student/profile/resumes'),
      request.get<ApplicationMaterialRow[]>('/student/profile/certificates')
    ]);

    if (resumeRes.code === 200) {
      applicationMaterials.value.resumes = Array.isArray(resumeRes.data) ? resumeRes.data : [];
    }

    if (certRes.code === 200) {
      applicationMaterials.value.certificates = Array.isArray(certRes.data) ? certRes.data : [];
    }

    // 标记缓存已加载，后续不再重复请求
    materialsCacheLoaded.value = true;
  } catch (error) {
    console.error('加载申请材料失败:', error);
    applicationMaterials.value = { resumes: [], certificates: [] };
  } finally {
    materialsLoading.value = false;
  }
};

const materialFileName = (name: string, stored: unknown) => {
  const extension = typeof stored === 'string' ? /\.([a-z0-9]{1,8})(?:$|[?#])/i.exec(stored)?.[1] : ''
  return extension && !name.toLowerCase().endsWith(`.${extension.toLowerCase()}`) ? `${name}.${extension}` : name
}

// 申请专属材料：原存储地址仅用于识别文件格式，预览和下载均走当前记录的授权接口。
const appMaterials = computed(() => {
  const m = currentApplication.value?.materials
  if (!m) return []
  try {
    const parsed = typeof m === 'string' ? JSON.parse(m) : m
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return []
    return Object.entries(parsed).filter(([, v]) => v).map(([label, stored]) => {
      const displayName = label === 'delayPlan' ? '考研计划' : label
      return { label, displayName, fileName: materialFileName(displayName, stored) }
    })
  } catch {
    return []
  }
})

const previewApplicationMaterial = (key: string) => {
  const material = appMaterials.value.find(item => item.label === key)
  const url = ['selfPractice', 'delay'].includes(currentApplication.value?.applicationType)
    ? studentApplicationMaterialByteUrl(currentApplication.value?.id, key) : ''
  if (!url || !material) return ElMessage.error('申请材料记录不可用')
  materialPreviewName.value = material.fileName
  materialPreviewUrl.value = url
  materialPreviewVisible.value = true
}

const previewProfileMaterial = (kind: 'resume' | 'certificate', file: ApplicationMaterialRow) => {
  const url = studentProfileByteUrl(kind, file?.id)
  if (!url) return ElMessage.error('材料记录不可用')
  materialPreviewName.value = materialFileName(file.name || (kind === 'resume' ? '简历' : '证书'), file.url)
  materialPreviewUrl.value = url
  materialPreviewVisible.value = true
}

const downloadApplicationMaterial = async (key) => {
  if (!['selfPractice', 'delay'].includes(currentApplication.value?.applicationType)) {
    ElMessage.error('该申请材料暂不可通过此入口下载')
    return
  }
  const url = studentApplicationMaterialByteUrl(currentApplication.value?.id, key)
  if (!url) return ElMessage.error('申请材料记录不可用')
  try {
    await downloadAuthorizedFile(url, appMaterials.value.find(item => item.label === key)?.fileName || key)
  } catch {
    ElMessage.error('申请材料下载失败，请检查权限或联系管理员')
  }
}

// 下载简历
const downloadResume = async (resume) => {
  const authorizedUrl = studentProfileByteUrl('resume', resume?.id)
  if (!authorizedUrl) {
    ElMessage.error('简历信息不存在')
    return
  }
  try {
    await downloadAuthorizedFile(authorizedUrl, resume.name || '简历')
  } catch (error) {
    ElMessage.error('简历下载失败，请检查权限')
  }
};

// 下载证书
const downloadCertificate = async (cert) => {
  const authorizedUrl = studentProfileByteUrl('certificate', cert?.id)
  if (!authorizedUrl) {
    ElMessage.error('证书信息不存在')
    return
  }
  try {
    await downloadAuthorizedFile(authorizedUrl, cert.name || '证书')
  } catch (error) {
    ElMessage.error('证书下载失败，请检查权限')
  }
};

// 方法：撤回申请（仅岗位申请，后端物理删除）
const withdrawApplication = async (application) => {
  const isDelay = application?.applicationType === 'delay';
  try {
    await ElMessageBox.confirm(
      isDelay ? '撤回后教师将无法继续审核原申请，你可以重新填写并提交。' : '确定要撤回这条申请吗？撤回后无法恢复。',
      isDelay ? '撤回考研延迟申请' : '撤回申请',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    );
    const response = isDelay
      ? await request.post(`/student/applications/${application.id}/withdraw`)
      : await request.delete(`/student/job-applications/${application.id}`);
    if (response.code === 200) {
      const index = applications.value.findIndex((app) => (
        String(app.id) === String(application.id) && app.applicationType === application.applicationType
      ));
      if (index !== -1) {
        if (isDelay) {
          applications.value[index] = { ...applications.value[index], status: 'withdrawn' };
          const source = studentApplicationRows.value.find(app => String(app.id) === String(application.id))
          if (source) source.status = 'withdrawn'
        } else {
          applications.value.splice(index, 1);
          jobApplicationRows.value = jobApplicationRows.value.filter(app => String(app.id) !== String(application.id))
        }
      }
      ElMessage.success(response.message || '申请已撤回');
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || error?.message || '撤回申请失败');
    }
  }
};
// 方法：跳转到职位浏览页
const goToJobs = () => {
  router.push("/student/jobs");
};

// 方法：跳转到面试管理页
const goToInterview = () => {
  router.push("/student/interviews");
};

// 生命周期钩子
onMounted(async () => {
  await fetchApplications();
  await openApplicationFromQuery()
  // 注册申请状态更新监听器
  onApplicationStatusUpdate(handleApplicationStatusUpdate)
});

watch(() => [route.query.applicationId, route.query.applicationType], () => {
  if (applications.value.length > 0) openApplicationFromQuery()
})

onUnmounted(() => {
  // 移除申请状态更新监听器
  offApplicationStatusUpdate(handleApplicationStatusUpdate)
});

// 处理WebSocket收到的申请状态更新（静默刷新，不弹通知）
const handleApplicationStatusUpdate = (data: ApplicationStatusUpdateData) => {
  console.log('收到申请状态更新:', data)
  console.log('data.studentId:', data.studentId, 'data.status:', data.status)
  // 静默刷新申请列表
  fetchApplications().then(() => {
    console.log('fetchApplications完成, 当前申请数:', applications.value.length)
  })
}

useAgentPageContext(() => ({
  title: '我的申请', view: showDetailDialog.value ? 'application-detail' : 'page',
  object: showDetailDialog.value && currentApplication.value?.id ? {
    type: currentApplication.value.applicationType === 'job' ? 'job_application' : 'student_application',
    id: String(currentApplication.value.id)
  } : null,
  filters: { applicationType: selectedType.value, status: selectedFilter.value }
}))

</script>

<style scoped>
/* 基础布局：统一容器样式 */
.applications-page {
  width: 100%;
  height: calc(100vh - 64px - 40px);
  background: transparent;
  padding: 0;
  margin: 0;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 页面头部：模仿欢迎区域的渐变风格 */
.page-header {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  border-radius: 12px;
  padding: 24px 32px;
  margin-bottom: 24px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  position: relative;
  overflow: hidden;
}

.page-header::before {
  content: "";
  position: absolute;
  top: -50%;
  right: -10%;
  width: 300px;
  height: 300px;
  background: radial-gradient(
    circle,
    rgba(255, 255, 255, 0.1) 0%,
    transparent 70%
  );
  border-radius: 50%;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  position: relative;
  z-index: 1;
}

.header-text h2 {
  font-size: 28px;
  font-weight: 700;
  color: white;
  margin: 0 0 8px 0;
}

.header-text p {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
  margin: 0;
}



/* 折叠筛选区域样式 */
.filter-collapse-section {
  background: white;
  border-radius: 12px;
  margin-bottom: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.filter-collapse-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 24px;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.filter-collapse-header:hover {
  background-color: #f8fafc;
}

.filter-summary {
  display: flex;
  align-items: center;
  gap: 10px;
}

.filter-icon {
  font-size: 18px;
  color: #409eff;
}

.filter-summary-text {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.filter-collapse-action {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-collapse-text {
  font-size: 13px;
  color: #94a3b8;
}

.filter-collapse-arrow {
  font-size: 16px;
  color: #94a3b8;
  transition: transform 0.3s ease;
}

.filter-collapse-arrow.collapsed {
  transform: rotate(180deg);
}

.filter-collapse-content {
  max-height: 200px;
  overflow: hidden;
  transition: max-height 0.3s ease, opacity 0.3s ease, padding 0.3s ease;
  opacity: 1;
  padding: 0 24px 16px;
}

.filter-collapse-content.collapsed {
  max-height: 0;
  opacity: 0;
  padding: 0 24px;
}

/* 提示信息区域样式 */
.tips-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}

.tips-card,
.offer-tips-card {
  background: white;
  border-radius: 12px;
  padding: 16px 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  display: flex;
  align-items: center;
  gap: 12px;
  transition: all 0.3s ease;
  border-left: 4px solid #409eff;
}

.offer-tips-card {
  border-left-color: #67C23A;
  background: linear-gradient(135deg, #f0f9ff 0%, #f6ffed 100%);
}

.tips-card:hover,
.offer-tips-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.tips-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.tips-card .tips-icon {
  color: #409eff;
}

.offer-tips-card .tips-icon {
  color: #67C23A;
}

.tips-content {
  flex: 1;
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
}

.tips-text {
  color: #606266;
}

.tips-link {
  color: #409eff;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  text-decoration: none;
  padding: 0 4px;
}

.tips-link:hover {
  color: #337ecc;
  text-decoration: underline;
}

.tips-highlight {
  color: #67C23A;
  font-weight: 700;
  padding: 0 4px;
  background: linear-gradient(135deg, #f6ffed 0%, #d9f7be 100%);
  border-radius: 4px;
}

/* 申请类型筛选标签 */
.type-filter-section {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.type-filter-label {
  font-size: 14px;
  font-weight: 600;
  color: #606266;
  white-space: nowrap;
}

.type-filter-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.type-filter-tab {
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 13px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.3s ease;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.type-filter-tab:hover {
  background: #f0f9ff;
  color: #409eff;
  border-color: #409eff;
}

.type-filter-tab.active {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  color: white;
  border-color: transparent;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.3);
}

/* 筛选标签：模仿统计栏样式 */
.filter-section {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-tab {
  padding: 8px 20px;
  border-radius: 8px;
  font-size: 14px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.3s ease;
  background: #f8fafc;
  border: 2px solid transparent;
}

.filter-tab:hover {
  background: #f0f9ff;
  color: #409eff;
}

.filter-tab.active {
  background: #409eff;
  color: white;
  border-color: #409eff;
}

/* 列表包装器：填充剩余空间并提供滚动 */
.list-wrapper {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

/* 申请列表卡片：小卡片网格布局 - 美化版 */
.applications-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  grid-auto-rows: max-content;
  gap: 20px;
  padding-right: 4px;
  align-content: start;
}

.application-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
  display: flex;
  gap: 12px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  border: 1px solid transparent;
  background: #ffffff;
  flex-direction: column;
}

.application-card::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 3px;
  background: linear-gradient(90deg, #409EFF, #67C23A);
  border-radius: 16px 16px 0 0;
}

.application-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 32px rgba(64, 158, 255, 0.2);
  border-color: #409EFF;
  background: linear-gradient(135deg, #ffffff 0%, #f0f9ff 100%);
}

.application-card.deep-link-target {
  border-color: #409eff;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.18), 0 12px 32px rgba(64, 158, 255, 0.16);
}

.application-card:hover .card-title {
  color: #409EFF;
}

.application-card:hover .status-tag {
  transform: scale(1.05);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 2px;
  position: relative;
  z-index: 1;
}

.card-title {
  font-size: 17px;
  font-weight: 700;
  color: #1e293b;
  transition: all 0.3s ease;
  flex: 1;
  line-height: 1.5;
  letter-spacing: -0.3px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}

.status-tag {
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
  flex-shrink: 0;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.status-tag::before {
  content: "";
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.3),
    transparent
  );
  transition: left 0.5s ease;
}

.status-tag:hover::before {
  left: 100%;
}

.status-tag.pending {
  background: linear-gradient(135deg, #fff7e6 0%, #ffe7ba 100%);
  color: #fa8c16;
  border: 1px solid #ffd591;
  box-shadow: 
    0 2px 8px rgba(250, 140, 22, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.status-tag.approved {
  background: linear-gradient(135deg, #f6ffed 0%, #d9f7be 100%);
  color: #52c41a;
  border: 1px solid #b7eb8f;
  box-shadow: 
    0 2px 8px rgba(82, 196, 26, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.status-tag.rejected {
  background: linear-gradient(135deg, #fff1f0 0%, #ffccc7 100%);
  color: #f5222d;
  border: 1px solid #ffa39e;
  box-shadow: 
    0 2px 8px rgba(245, 34, 45, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.status-tag.interview_passed,
.status-tag.hired {
  background: linear-gradient(135deg, #f6ffed 0%, #d9f7be 100%);
  color: #52c41a;
  border: 1px solid #b7eb8f;
  box-shadow:
    0 2px 8px rgba(82, 196, 26, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.status-tag.interview_failed {
  background: linear-gradient(135deg, #fff1f0 0%, #ffccc7 100%);
  color: #f5222d;
  border: 1px solid #ffa39e;
  box-shadow:
    0 2px 8px rgba(245, 34, 45, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.status-tag.withdrawn,
.status-tag.unknown {
  background: #f4f4f5;
  color: #606266;
  border: 1px solid #d3d4d6;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
  position: relative;
  z-index: 1;
  background: transparent;
  border: none;
  padding: 0;
  margin: 0;
}

.card-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  font-size: 12px;
  color: #606266;
  padding: 0;
  background: none;
  border-radius: 0;
  border: none;
  margin-bottom: 8px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
  transition: all 0.2s ease;
}

.info-item:hover {
  color: #409EFF;
  transform: translateX(4px);
}

.info-item .el-icon {
  font-size: 16px;
  color: #1890ff;
  width: 24px;
  text-align: center;
  flex-shrink: 0;
  filter: drop-shadow(0 2px 4px rgba(24, 144, 255, 0.4));
  background: linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%);
  padding: 4px;
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.2);
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 0;
  margin: 0;
  background: transparent;
  border: none;
}

.apply-time {
  font-size: 12px;
  color: #94a3b8;
  white-space: nowrap;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
}

.card-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.action-button {
  padding: 8px 16px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: none;
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
  letter-spacing: 0.3px;
}

.action-button.withdraw {
  background: linear-gradient(135deg, #fff7e6 0%, #ffe7ba 100%);
  color: #fa8c16;
  border: 1px solid #ffd591;
  box-shadow: 
    0 2px 8px rgba(250, 140, 22, 0.15),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.action-button.withdraw:hover {
  transform: translateY(-3px) scale(1.05);
  box-shadow:
    0 6px 20px rgba(250, 140, 22, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.action-button.delete {
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7875 100%);
  color: white;
  box-shadow:
    0 4px 12px rgba(255, 77, 79, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

.action-button.delete:hover {
  transform: translateY(-3px) scale(1.05);
  box-shadow:
    0 8px 24px rgba(255, 77, 79, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.4);
}

.action-button.view {
  background: linear-gradient(135deg, #409EFF 0%, #66b1ff 100%);
  color: white;
  box-shadow: 
    0 4px 12px rgba(64, 158, 255, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

.action-button.view:hover {
  transform: translateY(-3px) scale(1.05);
  box-shadow: 
    0 8px 24px rgba(64, 158, 255, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.4);
}

/* 空状态样式 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 60px 24px;
  background: white;
  border-radius: 12px;
  box-sizing: border-box;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.empty-icon {
  font-size: 64px;
  color: #cbd5e1;
  margin-bottom: 16px;
}

.empty-text {
  font-size: 16px;
  color: #64748b;
  margin-bottom: 24px;
}

.go-browse-button {
  padding: 10px 24px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.go-browse-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

/* 对话框通用样式：申请详情 和 申请表对话框 */
.detail-dialog :deep(.el-dialog),
.job-application-dialog :deep(.el-dialog) {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.detail-dialog :deep(.el-dialog__header),
.job-application-dialog :deep(.el-dialog__header) {
  padding: 0;
  border-bottom: none;
  margin: 0;
}

.detail-dialog :deep(.el-dialog__title),
.job-application-dialog :deep(.el-dialog__title) {
  display: none;
}

.detail-dialog :deep(.el-dialog__headerbtn .el-dialog__close),
.job-application-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
  font-size: 20px;
  top: 15px;
  right: 20px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  width: 30px;
  height: 30px;
  transition: all 0.3s ease;
}

.detail-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover),
.job-application-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  background: rgba(255, 255, 255, 0.3);
}

.detail-dialog :deep(.el-dialog__body),
.job-application-dialog :deep(.el-dialog__body) {
  padding: 0;
  background-color: #ffffff;
  max-height: 75vh;
  overflow-y: auto;
}

/* 对话框标题栏：统一蓝绿色渐变 */
.header-section {
  background: linear-gradient(90deg, #1e88e5 0%, #4caf50 100%);
  padding: 20px;
  margin-bottom: 0;
  border-radius: 0;
  box-shadow: none;
}

.header-title {
  color: white;
  font-size: 24px;
  font-weight: 600;
  text-align: center;
  margin: 0;
}

/* 详情内容区域 */
.detail-content {
  padding: 20px;
  max-height: 60vh;
  overflow-y: auto;
}

/* 详情头部 */
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16px;
  margin-bottom: 24px;
  border-bottom: 2px solid #f0f0f0;
}

.detail-title {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.detail-title h3 {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.detail-status {
  padding: 8px 20px;
  border-radius: 24px;
  font-size: 15px;
  font-weight: 700;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.detail-status.pending {
  background: linear-gradient(135deg, #fff7e6 0%, #ffe7ba 100%);
  color: #fa8c16;
  border: 1px solid #ffd591;
}

.detail-status.approved {
  background: linear-gradient(135deg, #f6ffed 0%, #d9f7be 100%);
  color: #52c41a;
  border: 1px solid #b7eb8f;
}

.detail-status.withdrawn {
  color: #8a7a55;
  background: #f5f0e4;
  border-color: #e5d9bd;
}

.detail-status.rejected {
  background: linear-gradient(135deg, #fff1f0 0%, #ffccc7 100%);
  color: #f5222d;
  border: 1px solid #ffa39e;
}

/* 详情模块样式 */
.detail-module {
  background-color: transparent;
  border: none;
  border-radius: 0;
  padding: 0;
  margin-bottom: 24px;
  box-shadow: none;
}

.module-header {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.module-icon-container {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
}

.module-icon {
  color: white;
  font-size: 14px;
}

.module-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

/* 信息网格 */
.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px 32px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  color: #64748b;
  background: white;
  padding: 12px 16px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
}

.info-item:hover {
  border-color: #409eff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
  transform: translateY(-2px);
}

.info-label {
  font-weight: 600;
  color: #1e293b;
}

.info-value {
  font-weight: 500;
  color: #334155;
}

/* 考研延迟详情：申请理由整行展示 + 材料链接 */
.delay-reason-item {
  display: block;
}

.delay-reason {
  display: block;
  margin-top: 4px;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.8;
  font-weight: 400;
  color: #64748b;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px 16px;
}

.material-link {
  color: #409eff;
  text-decoration: none;
  font-weight: 500;
}

.material-link:hover {
  text-decoration: underline;
}

/* 详情描述 */
.detail-description {
  font-size: 15px;
  line-height: 2;
  color: #64748b;
  margin: 0;
  padding: 16px;
  background: white;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
}

/* 详情列表 */
.detail-list {
  padding-left: 24px;
  margin: 0;
}

.detail-list li {
  font-size: 15px;
  line-height: 2;
  color: #64748b;
  margin-bottom: 12px;
  padding-left: 8px;
  position: relative;
}

.detail-list li::before {
  content: '•';
  position: absolute;
  left: -12px;
  color: #409eff;
  font-weight: 700;
}

/* 联系信息 */
.contact-info {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.contact-item {
  font-size: 15px;
  color: #64748b;
  background: white;
  padding: 14px 18px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
}

.contact-item:hover {
  border-color: #409eff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
  transform: translateY(-2px);
}

.contact-label {
  font-weight: 600;
  color: #1e293b;
  margin-right: 10px;
}

.contact-value {
  font-weight: 500;
  color: #334155;
}

/* 申请材料样式 */
.material-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.material-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: white;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
}

.material-item:hover {
  border-color: #409eff;
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.2);
  transform: translateY(-3px);
}

.material-item > div {
  display: flex;
  align-items: center;
  gap: 14px;
}

.material-icon {
  font-size: 28px;
  color: #409eff;
  transition: all 0.3s ease;
}

.material-item:hover .material-icon {
  transform: scale(1.1);
  color: #409eff;
}

/* 材料模块新样式 */
.materials-section {
  margin-bottom: 20px;
}

.materials-section:last-child {
  margin-bottom: 0;
}

.materials-subtitle {
  font-size: 14px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 12px;
  padding-left: 4px;
}

.material-info {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.material-name {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.material-actions {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.upload-time-tag {
  font-size: 12px;
}

.materials-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 30px;
  color: #909399;
  font-size: 14px;
}

.no-materials {
  text-align: center;
  padding: 20px;
}

.no-materials-tip {
  font-size: 13px;
  color: #909399;
  margin-top: 8px;
}

/* 对话框底部 */
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0 0 0;
  border-top: 2px solid #e8e8e8;
}

.close-btn {
  padding: 12px 32px;
  border-radius: 10px;
  font-weight: 600;
  font-size: 15px;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  border: 2px solid #409eff;
  color: #409eff;
  background: white;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
}

.close-btn:hover {
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  transform: translateY(-3px);
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.3);
  border-color: #409eff;
}

/* 响应式适配：匹配网格布局的响应式规则 */
@media screen and (max-width: 1200px) {
  .applications-list {
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  }

}

@media screen and (max-width: 768px) {
  .material-item {
    flex-wrap: wrap;
    gap: 12px;
    padding: 12px;
  }

  .material-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .header-content {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .applications-list {
    grid-template-columns: 1fr;
  }

  .card-info {
    grid-template-columns: 1fr;
  }

  .card-footer {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .card-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .filter-collapse-header {
    padding: 12px 16px;
  }

  .filter-collapse-content {
    padding: 0 16px 12px;
  }

  .filter-collapse-content.collapsed {
    padding: 0 16px;
  }

  .type-filter-section {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .filter-section {
    justify-content: center;
  }
}

@media screen and (max-width: 480px) {
  .page-header {
    padding: 24px 20px;
  }

  .header-text h2 {
    font-size: 24px;
  }

  .application-card {
    padding: 12px;
  }

  .card-actions {
    flex-direction: column;
    gap: 8px;
    width: 100%;
  }

  .action-button {
    width: 100%;
    justify-content: center;
  }
}


.header-section{position:relative}
.header-section .agent-context-action{position:absolute;right:20px;top:50%;transform:translateY(-50%)}
</style>
