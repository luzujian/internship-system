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
    <div class="list-wrapper">
      <div v-if="filteredApplications.length > 0" class="applications-list">
        <div
          v-for="application in filteredApplications"
          :key="application.id"
          class="application-card"
          @click="viewApplicationDetail(application)"
        >
          <div class="card-header">
            <div class="card-title">{{ application.jobTitle }}</div>
            <div :class="['status-tag', application.status]">
              {{ getStatusText(application.status) }}
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
              <div class="info-item">
                <el-icon><Clock /></el-icon>
                <span>{{ getDurationDisplay(application.duration) }}</span>
              </div>
            </div>
            <div class="card-footer">
              <span class="apply-time">{{ application.applyDate }}</span>
              <div class="card-actions">
                <button
                  v-if="application.status === 'pending' && application.applicationType === 'job'"
                  class="action-button withdraw"
                  @click.stop="withdrawApplication(application.id)"
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
      <div v-else class="empty-state">
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
              <div v-if="currentApplication.internshipBase" class="internship-base-badges">
                <el-tag v-if="currentApplication.internshipBase === 'national'" size="small" type="danger" class="base-badge national">
                  国家级实习基地
                </el-tag>
                <el-tag v-else-if="currentApplication.internshipBase === 'provincial'" size="small" type="warning" class="base-badge provincial">
                  省级实习基地
                </el-tag>
              </div>
            </div>
            <div :class="['detail-status', currentApplication.status]">
              {{ getStatusText(currentApplication.status) }}
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
                <span class="info-value">{{ currentApplication.company }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">地点：</span>
                <span class="info-value">{{ currentApplication.location }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">薪资：</span>
                <span class="info-value">{{ currentApplication.salary }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">时长：</span>
                <span class="info-value">{{ getDurationDisplay(currentApplication.duration) }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">行业：</span>
                <span class="info-value">{{ currentApplication.industryName }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">申请时间：</span>
                <span class="info-value">{{ currentApplication.applyDate }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">申请状态：</span>
                <span class="info-value">{{ getStatusText(currentApplication.status) }}</span>
              </div>
            </div>
          </div>

          <!-- 申请信息模块 -->
          <div class="detail-module">
            <div class="module-header">
              <div class="module-icon-container">
                <el-icon class="module-icon"><User /></el-icon>
              </div>
              <h2 class="module-title">申请信息</h2>
            </div>
            <div class="info-grid">
              <div class="info-item">
                <span class="info-label">学生姓名：</span>
                <span class="info-value">{{ currentApplication.studentName }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">学号：</span>
                <span class="info-value">{{ currentApplication.studentUserId || currentApplication.studentNo }}</span>
              </div>
              <div class="info-item" v-if="currentApplication.major">
                <span class="info-label">专业：</span>
                <span class="info-value">{{ currentApplication.major }}</span>
              </div>
              <div class="info-item" v-if="currentApplication.grade">
                <span class="info-label">年级：</span>
                <span class="info-value">{{ currentApplication.grade }}</span>
              </div>
              <div class="info-item" v-if="currentApplication.className">
                <span class="info-label">班级：</span>
                <span class="info-value">{{ currentApplication.className }}</span>
              </div>
              <div class="info-item" v-if="currentApplication.phone">
                <span class="info-label">联系电话：</span>
                <span class="info-value">{{ currentApplication.phone }}</span>
              </div>
              <div class="info-item" v-if="currentApplication.email">
                <span class="info-label">邮箱：</span>
                <span class="info-value">{{ currentApplication.email }}</span>
              </div>
            </div>
            <div class="self-intro-section" v-if="currentApplication.selfIntroduction">
              <div class="self-intro-label">自我简介：</div>
              <div class="self-intro-content">{{ currentApplication.selfIntroduction }}</div>
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
                <span class="contact-value">{{ currentApplication.contactPerson }}</span>
              </div>
              <div class="contact-item">
                <span class="contact-label">电话：</span>
                <span class="contact-value">{{ currentApplication.contactPhone }}</span>
              </div>
              <div class="contact-item">
                <span class="contact-label">邮箱：</span>
                <span class="contact-value">{{ currentApplication.contactEmail }}</span>
              </div>
            </div>
          </div>

          <!-- 职位统计模块（仅岗位申请） -->
          <div v-if="currentApplication.applicationType === 'job'" class="detail-module">
            <div class="module-header">
              <div class="module-icon-container">
                <el-icon class="module-icon"><TrendCharts /></el-icon>
              </div>
              <h2 class="module-title">职位统计</h2>
            </div>
            <div class="stats-grid">
              <div class="stat-item">
                <div class="stat-number">{{ currentApplication.viewCount }}</div>
                <div class="stat-label">浏览次数</div>
              </div>
              <div class="stat-item">
                <div class="stat-number">{{ currentApplication.applyCount }}</div>
                <div class="stat-label">申请人数</div>
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
                      <span class="material-name">{{ item.label }}</span>
                    </div>
                    <div class="material-actions">
                      <el-button type="primary" size="small" link @click="openMaterial(item.url)">
                        <el-icon><Download /></el-icon>
                        查看
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
                      <el-button type="primary" size="small" link @click="downloadCertificate(cert)">
                        <el-icon><Download /></el-icon>
                        下载
                      </el-button>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 无材料提示 -->
              <div v-if="applicationMaterials.resumes.length === 0 && applicationMaterials.certificates.length === 0" class="no-materials">
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



  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessageBox, ElMessage } from "element-plus";
import {
  Document,
  User,
  ArrowRight,
  OfficeBuilding,
  Location,
  Money,
  Clock,
  Message,
  TrendCharts,
  InfoFilled,
  Download,
  Medal,
  Loading,
  Filter,
  ArrowUp,
} from "@element-plus/icons-vue";

import request from '@/utils/request';
import { useAuthStore } from '@/store/auth';
import type { ApplicationStatusUpdateData } from '@/utils/websocket';
import { onApplicationStatusUpdate, offApplicationStatusUpdate } from '@/utils/websocket';

const authStore = useAuthStore()

// 从 auth store 获取学生 ID
const getStudentId = () => {
  return authStore.user?.id
};

const router = useRouter();

// 状态管理
const showDetailDialog = ref(false);
const selectedFilter = ref("all");
const selectedType = ref("all");
const currentApplication = ref(null);
const filterCollapsed = ref(true); // 默认折叠

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
const applicationMaterials = ref({
  resumes: [],
  certificates: []
});
const materialsLoading = ref(false);
// 材料数据缓存标记，避免重复加载
const materialsCacheLoaded = ref(false);

// 系统默认实习时间设置
const systemInternshipTime = ref({
  startDate: '',
  endDate: ''
});


// 筛选选项
const filters = [
  { label: "全部", value: "all" },
  { label: "待审核", value: "pending" },
  { label: "已通过", value: "approved" },
  { label: "已拒绝", value: "rejected" },
];

// 申请类型选项
const applicationTypes = [
  { label: "全部", value: "all" },
  { label: "岗位申请", value: "job" },
  { label: "自主实习", value: "selfPractice" },
  { label: "单位变更", value: "unitChange" },
  { label: "考研延迟", value: "delay" },
];

const applications = ref([])

// 状态值转换：后端数字/字符串到前端字符串
const statusMap = {
  '0': 'pending',
  '1': 'approved',
  '2': 'rejected',
  '3': 'hired',
  'pending': 'pending',
  'approved': 'approved',
  'rejected': 'rejected',
  'hired': 'hired',
  'interview_passed': 'interview_passed',
  'interview_failed': 'interview_failed'
};

// 从后端API获取申请数据
const fetchApplications = async () => {
  try {
    // 并行获取学生申请(自主实习/单位变更/考研延迟)和岗位申请
    const [appResponse, jobResponse] = await Promise.all([
      request.get(`/student/applications`),
      request.get(`/student/job-applications`)
    ]);

    const allApplications = [];

    // 处理学生申请(自主实习/单位变更/考研延迟)
    if (appResponse.code === 200) {
      const data = appResponse.data || [];
      data.forEach(app => {
        let jobTitle = app.positionName || '';
        let company = app.companyName || app.company || '';

        // 对于学生申请设置合适的显示标题
        if (app.applicationType === 'selfPractice') {
          jobTitle = jobTitle || '自主实习申请';
          company = company || app.company || '';
        } else if (app.applicationType === 'unitChange') {
          jobTitle = jobTitle || '单位变更申请';
          company = app.oldCompany && app.newCompany
            ? `${app.oldCompany} → ${app.newCompany}`
            : (app.oldCompany || app.newCompany || '');
        } else if (app.applicationType === 'delay') {
          jobTitle = jobTitle || '考研延迟申请';
          company = company || '';
        }

        allApplications.push({
          ...app,
          applicationType: app.applicationType,
          jobTitle,
          company,
          contactPerson: app.hrName || app.contactPerson || app.recruiterName || '',
          contactPhone: app.hrPhone || app.contactPhone || app.recruiterPhone || '',
          contactEmail: app.hrEmail || app.contactEmail || app.recruiterEmail || '',
          status: statusMap[app.status] || 'pending',
          applyDate: app.applyTime ? new Date(app.applyTime).toISOString().split('T')[0] : (app.createTime ? new Date(app.createTime).toISOString().split('T')[0] : '')
        });
      });
    }

    // 处理岗位申请
    if (jobResponse.code === 200) {
      const jobData = jobResponse.data || [];
      jobData.forEach(app => {
        allApplications.push({
          ...app,
          applicationType: 'job', // 岗位申请标记为 job
          jobTitle: app.positionName || app.jobTitle || '',
          company: app.companyName || app.company || '',
          location: app.location || '',
          salary: app.salary || '',
          duration: app.duration || '',
          contactPerson: app.hrName || app.contactPerson || app.recruiterName || '',
          contactPhone: app.hrPhone || app.contactPhone || app.recruiterPhone || '',
          contactEmail: app.hrEmail || app.contactEmail || app.recruiterEmail || '',
          status: statusMap[app.status] || 'pending',
          applyDate: app.applyDate ? new Date(app.applyDate).toISOString().split('T')[0] : (app.createTime ? new Date(app.createTime).toISOString().split('T')[0] : '')
        });
      });
    }

    applications.value = allApplications;
  } catch (error) {
    console.error('获取申请数据失败:', error);
    // 保留默认数据，确保页面正常显示
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

  // 再按状态筛选
  if (selectedFilter.value !== "all") {
    result = result.filter(
      (app) => app.status === selectedFilter.value,
    );
  }

  return result;
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
const getStatusText = (status) => {
  const statusMap = {
    'pending': '待审核',
    'approved': '已通过',
    'rejected': '已拒绝',
    'hired': '已录用',
    'interview_passed': '面试通过',
    'interview_failed': '面试未通过'
  };
  return statusMap[status] || status;
};

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
  const contactPerson = application.hrName || application.contactPerson || application.recruiterName || ''
  const contactPhone = application.hrPhone || application.contactPhone || application.recruiterPhone || ''
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

// 加载申请人的简历和证书材料（带缓存优化）
const loadApplicationMaterials = async () => {
  // 如果已经加载过材料数据，直接使用缓存
  if (materialsCacheLoaded.value) {
    return;
  }

  materialsLoading.value = true;
  try {
    // 并行加载简历、证书和系统实习时间设置
    const [resumeRes, certRes] = await Promise.all([
      request.get('/student/profile/resumes'),
      request.get('/student/profile/certificates')
    ]);

    if (resumeRes.code === 200) {
      applicationMaterials.value.resumes = resumeRes.data || [];
    }

    if (certRes.code === 200) {
      applicationMaterials.value.certificates = certRes.data || [];
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

// 申请专属材料
const appMaterials = computed(() => {
  const m = currentApplication.value?.materials
  if (!m || typeof m !== 'object') return []
  return Object.entries(m).filter(([, v]) => v).map(([k, v]) => ({ label: k, url: v }))
})

// 计算实习时长显示
const getDurationDisplay = (duration) => {
  // 如果企业没有设置时长（为空或"不限"），但系统设置了起止时间，显示系统的时间范围
  if (!duration || duration.trim() === '' || duration === '不限') {
    if (systemInternshipTime.value.startDate && systemInternshipTime.value.endDate) {
      return `${systemInternshipTime.value.startDate} 至 ${systemInternshipTime.value.endDate}`;
    }
    return '未设置';
  }
  return duration;
};

// 打开申请材料文件
const openMaterial = (url) => {
  window.open(url, '_blank')
}

// 下载简历
const downloadResume = (resume) => {
  if (!resume || !resume.url) {
    ElMessage.error('简历信息不存在')
    return
  }
  window.open(resume.url, '_blank')
};

// 下载证书
const downloadCertificate = (cert) => {
  if (!cert || !cert.url) {
    ElMessage.error('证书信息不存在')
    return
  }
  window.open(cert.url, '_blank')
};

// 方法：撤回申请（仅岗位申请，后端物理删除）
const withdrawApplication = async (id) => {
  try {
    await ElMessageBox.confirm('确定要撤回这条申请吗？撤回后无法恢复。', '撤回申请', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    });
    const response = await request.delete(`/student/job-applications/${id}`);
    if (response.code === 200) {
      const index = applications.value.findIndex((app) => app.id === id);
      if (index !== -1) {
        applications.value.splice(index, 1);
      }
      ElMessage.success('申请已撤回');
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('撤回申请失败');
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
  await Promise.all([
    fetchApplications(),
    fetchSystemInternshipTime()
  ]);
  // 注册申请状态更新监听器
  onApplicationStatusUpdate(handleApplicationStatusUpdate)
});

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

// 获取系统实习时间设置
const fetchSystemInternshipTime = async () => {
  try {
    const timeRes = await request.get('/settings/internship-time');
    if (timeRes.code === 200) {
      systemInternshipTime.value = {
        startDate: timeRes.data?.startDate || '',
        endDate: timeRes.data?.endDate || ''
      };
    }
  } catch (error) {
    console.error('获取系统实习时间设置失败:', error);
  }
};
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

.status-tag.interview_passed {
  background: linear-gradient(135deg, #f6ffed 0%, #d9f7be 100%);
  color: #52c41a;
  border: 1px solid #b7eb8f;
  box-shadow:
    0 2px 8px rgba(82, 196, 26, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
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

.internship-base-badges {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.base-badge {
  font-weight: 600;
  padding: 6px 16px;
  border-radius: 12px;
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

/* 自我简介 */
.self-intro-section {
  margin-top: 16px;
  padding: 16px;
  background: white;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
}

.self-intro-label {
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 8px;
  font-size: 14px;
}

.self-intro-content {
  color: #64748b;
  line-height: 1.8;
  font-size: 14px;
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

/* 统计网格 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.stat-item {
  text-align: center;
  padding: 16px;
  background: white;
  border-radius: 8px;
  border: 1px solid #e8e8e8;
}

.stat-number {
  font-size: 24px;
  font-weight: 700;
  color: #409eff;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
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
  align-items: center;
  gap: 12px;
}

.material-name {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}

.material-actions {
  display: flex;
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
  .header-content {
    flex-direction: column;
    text-align: center;
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
</style>
