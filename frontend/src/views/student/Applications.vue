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

    <!-- 岗位申请表入口区域：模仿通知卡片风格 -->
    <div class="application-form-entry">
      <div class="entry-card" @click="showJobApplicationForm = true">
        <div class="entry-icon">
          <el-icon class="big-icon"><Document /></el-icon>
        </div>
        <div class="entry-content">
          <h3>申请表</h3>
          <p>填写实习相关申请信息</p>
        </div>
        <div class="entry-action">
          <el-button type="primary" class="form-entry-btn">
            填写申请表
          </el-button>
        </div>
      </div>
    </div>

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

    <!-- 提示信息区域：选择申请类型后显示 -->
    <div v-if="selectedType !== 'all'" class="tips-section">
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

    <!-- 状态筛选标签：模仿统计栏样式 -->
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

    <!-- 申请列表：小卡片网格布局 -->
    <div class="applications-list">
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
              <span>{{ application.duration }}</span>
            </div>
          </div>
          <div class="card-footer">
            <span class="apply-time">{{ application.applyDate }}</span>
            <div class="card-actions">
              <button
                v-if="application.status === 'pending'"
                class="action-button withdraw"
                @click.stop="withdrawApplication(application.id)"
              >
                撤回申请
              </button>
              <button
                v-if="application.status === 'withdrawn'"
                class="action-button delete"
                @click.stop="deleteApplication(application.id)"
              >
                删除
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

    <!-- 空状态：统一空状态样式 -->
    <div v-if="filteredApplications.length === 0" class="empty-state">
      <el-icon class="empty-icon"><Document /></el-icon>
      <div class="empty-text">暂无{{ getFilterLabel() }}的申请记录</div>
      <el-button type="primary" class="go-browse-button" @click="goToJobs">
        去浏览职位
      </el-button>
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
                <span class="info-value">{{ currentApplication.duration }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">类型：</span>
                <span class="info-value">{{ currentApplication.type }}</span>
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
                <span class="info-value">{{ currentApplication.studentNo }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">专业：</span>
                <span class="info-value">{{ currentApplication.major }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">年级：</span>
                <span class="info-value">{{ currentApplication.grade }}</span>
              </div>
              <div class="info-item">
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

          <!-- 职位描述模块 -->
          <div class="detail-module">
            <div class="module-header">
              <div class="module-icon-container">
                <el-icon class="module-icon"><Document /></el-icon>
              </div>
              <h2 class="module-title">职位描述</h2>
            </div>
            <p class="detail-description">{{ currentApplication.description }}</p>
          </div>

          <!-- 任职要求模块 -->
          <div class="detail-module">
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

          <!-- 联系方式模块 -->
          <div class="detail-module">
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

          <!-- 职位统计模块 -->
          <div class="detail-module">
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
            <div class="material-list">
              <div class="material-item">
                <el-icon class="material-icon"><Document /></el-icon>
                <span>个人简历.pdf</span>
                <el-button type="primary" size="small" link>下载</el-button>
              </div>
              <div class="material-item">
                <el-icon class="material-icon"><Document /></el-icon>
                <span>成绩单.pdf</span>
                <el-button type="primary" size="small" link>下载</el-button>
              </div>
              <div class="material-item">
                <el-icon class="material-icon"><Document /></el-icon>
                <span>自荐信.pdf</span>
                <el-button type="primary" size="small" link>下载</el-button>
              </div>
            </div>
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

    <!-- 岗位申请表对话框：完全模仿实习状态对话框风格 -->
    <el-dialog
      v-model="showJobApplicationForm"
      width="900px"
      class="job-application-dialog"
      append-to-body
      lock-scroll
      modal-class="global-modal"
      :title="''"
    >
      <div class="dialog-content">
        <!-- 蓝绿色标题栏：和示例保持一致 -->
        <div class="header-section">
          <h1 class="header-title">申请表</h1>
        </div>

        <el-form label-width="120px">
          <div class="form-content">
            <!-- 基本信息模块 -->
          <div class="form-module">
            <div class="module-header">
              <div class="module-icon-container">
                <el-icon class="module-icon"><User /></el-icon>
              </div>
              <h2 class="module-title">基本信息</h2>
            </div>
            <div class="form-grid">
              <el-form-item label="学生姓名" required>
                <el-input
                  v-model="jobApplicationForm.studentName"
                  placeholder="请输入姓名"
                  clearable
                />
              </el-form-item>
              <el-form-item label="学号" required>
                <el-input
                  v-model="jobApplicationForm.studentId"
                  placeholder="请输入学号"
                  clearable
                />
              </el-form-item>
              <el-form-item label="性别" required>
                <el-select
                  v-model="jobApplicationForm.gender"
                  placeholder="请选择性别"
                  clearable
                >
                  <el-option label="男" value="male" />
                  <el-option label="女" value="female" />
                </el-select>
              </el-form-item>
              <el-form-item label="年级" required>
                <el-select
                  v-model="jobApplicationForm.grade"
                  placeholder="请选择年级"
                  clearable
                >
                  <el-option label="一年级" value="grade1" />
                  <el-option label="二年级" value="grade2" />
                  <el-option label="三年级" value="grade3" />
                  <el-option label="四年级" value="grade4" />
                </el-select>
              </el-form-item>
              <el-form-item label="专业" required>
                <el-input
                  v-model="jobApplicationForm.major"
                  placeholder="请输入专业"
                  clearable
                />
              </el-form-item>
              <el-form-item label="专业班级" required>
                <el-select
                  v-model="jobApplicationForm.majorClass"
                  placeholder="请选择专业班级"
                  clearable
                >
                  <el-option label="计算机科学与技术1班" value="cs1" />
                  <el-option label="计算机科学与技术2班" value="cs2" />
                  <el-option label="软件工程1班" value="se1" />
                  <el-option label="软件工程2班" value="se2" />
                  <el-option label="数据科学与大数据技术1班" value="ds1" />
                  <el-option label="数据科学与大数据技术2班" value="ds2" />
                </el-select>
              </el-form-item>
              <el-form-item label="联系电话" required>
                <el-input
                  v-model="jobApplicationForm.phone"
                  placeholder="请输入联系电话"
                  clearable
                />
              </el-form-item>
            </div>
          </div>

          <!-- 申请信息模块 -->
          <div class="form-module">
            <div class="module-header">
              <div class="module-icon-container">
                <el-icon class="module-icon"><Briefcase /></el-icon>
              </div>
              <h2 class="module-title">申请信息</h2>
            </div>
            <div class="form-grid">
              <el-form-item label="申请类型" required>
                <el-select
                  v-model="jobApplicationForm.applicationType"
                  placeholder="请选择申请类型"
                  clearable
                  @change="handleApplicationTypeChange"
                >
                  <el-option label="岗位申请" value="job" />
                  <el-option label="自主实习申请" value="selfPractice" />
                  <el-option label="单位变更申请" value="unitChange" />
                  <el-option label="考研延迟申请" value="delay" />
                </el-select>
              </el-form-item>

              <!-- 岗位申请提示 -->
              <div v-if="jobApplicationForm.applicationType === 'job'" class="application-tip">
                <el-alert
                  title="请去职位查看页面进行职位选择和申请"
                  type="info"
                  :closable="false"
                  show-icon
                />
              </div>

              <!-- 自主实习申请表单 -->
              <template v-if="jobApplicationForm.applicationType === 'selfPractice'">
                <el-form-item label="实习单位" required>
                  <el-input
                    v-model="jobApplicationForm.company"
                    placeholder="请输入实习单位名称"
                    clearable
                  />
                </el-form-item>
                <el-form-item label="申请理由" required>
                  <el-input
                    v-model="jobApplicationForm.reason"
                    type="textarea"
                    placeholder="请输入申请理由"
                    :rows="3"
                    clearable
                  />
                </el-form-item>
                <el-form-item label="企业介绍">
                  <el-upload
                    class="upload-dragger"
                    action="#"
                    :auto-upload="false"
                    :on-change="(file) => handleMaterialChange(file, 'enterpriseIntro')"
                    :file-list="enterpriseIntroFileList"
                    accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
                    :show-file-list="true"
                    drag
                  >
                    <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                    <div class="el-upload__text">点击或拖拽上传企业介绍</div>
                  </el-upload>
                </el-form-item>
                <el-form-item label="实习计划">
                  <el-upload
                    class="upload-dragger"
                    action="#"
                    :auto-upload="false"
                    :on-change="(file) => handleMaterialChange(file, 'internshipPlan')"
                    :file-list="internshipPlanFileList"
                    accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
                    :show-file-list="true"
                    drag
                  >
                    <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                    <div class="el-upload__text">点击或拖拽上传实习计划</div>
                  </el-upload>
                </el-form-item>
                <el-form-item label="安全协议">
                  <el-upload
                    class="upload-dragger"
                    action="#"
                    :auto-upload="false"
                    :on-change="(file) => handleMaterialChange(file, 'safetyAgreement')"
                    :file-list="safetyAgreementFileList"
                    accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
                    :show-file-list="true"
                    drag
                  >
                    <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                    <div class="el-upload__text">点击或拖拽上传安全协议</div>
                  </el-upload>
                </el-form-item>
              </template>

              <!-- 单位变更申请提示 -->
              <div v-if="jobApplicationForm.applicationType === 'unitChange'" class="application-tip">
                <el-alert
                  title="请去实习确认申请页面的确认记录列表进行申请"
                  type="info"
                  :closable="false"
                  show-icon
                />
              </div>

              <!-- 考研延迟申请表单 -->
              <template v-if="jobApplicationForm.applicationType === 'delay'">
                <el-form-item label="申请理由" required>
                  <el-input
                    v-model="jobApplicationForm.reason"
                    type="textarea"
                    placeholder="请输入申请理由"
                    :rows="3"
                    clearable
                  />
                </el-form-item>
                <el-form-item label="考研计划">
                  <el-upload
                    class="upload-dragger"
                    action="#"
                    :auto-upload="false"
                    :on-change="(file) => handleMaterialChange(file, '考研计划')"
                    :file-list="delayPlanFileList"
                    accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
                    :show-file-list="true"
                    drag
                  >
                    <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                    <div class="el-upload__text">点击或拖拽上传考研计划</div>
                  </el-upload>
                </el-form-item>
                <el-form-item label="学习计划">
                  <el-upload
                    class="upload-dragger"
                    action="#"
                    :auto-upload="false"
                    :on-change="(file) => handleMaterialChange(file, '学习计划')"
                    :file-list="studyPlanFileList"
                    accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
                    :show-file-list="true"
                    drag
                  >
                    <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                    <div class="el-upload__text">点击或拖拽上传学习计划</div>
                  </el-upload>
                </el-form-item>
                <el-form-item label="延迟申请书">
                  <el-upload
                    class="upload-dragger"
                    action="#"
                    :auto-upload="false"
                    :on-change="(file) => handleMaterialChange(file, '延迟申请书')"
                    :file-list="delayApplicationFileList"
                    accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
                    :show-file-list="true"
                    drag
                  >
                    <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                    <div class="el-upload__text">点击或拖拽上传延迟申请书</div>
                  </el-upload>
                </el-form-item>
              </template>
            </div>
          </div>

          <!-- 提交按钮：统一按钮样式 -->
          <div class="form-actions">
            <el-button @click="showJobApplicationForm = false" class="reset-btn"
              >取消</el-button
            >
            <el-button @click="resetJobApplicationForm" class="reset-btn"
              >重置</el-button
            >
            <el-button
              type="primary"
              @click="submitJobApplication"
              class="submit-btn"
              :disabled="isSubmitDisabled"
            >
              提交申请
            </el-button>
          </div>
        </div>
      </el-form>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessageBox, ElMessage } from "element-plus";
import {
  Plus,
  Document,
  User,
  Briefcase,
  Star,
  Upload,
  Close,
  Picture,
  UploadFilled,
  ArrowRight,
  OfficeBuilding,
  Location,
  Money,
  Clock,
  Message,
  TrendCharts,
  InfoFilled,
  CircleCheckFilled,
} from "@element-plus/icons-vue";

import request from '@/utils/request';
import { useAuthStore } from '@/store/auth';

const authStore = useAuthStore()

// 从 auth store 获取学生 ID
const getStudentId = () => {
  return authStore.user?.id
};

const router = useRouter();

// 状态管理
const showDetailDialog = ref(false);
const showJobApplicationForm = ref(false);
const selectedFilter = ref("all");
const selectedType = ref("all");
const currentApplication = ref(null);

// 岗位申请表表单数据
const jobApplicationForm = ref({
  studentName: "",
  studentId: "",
  gender: "",
  grade: "",
  school: "",
  education: "",
  major: "",
  majorClass: "",
  phone: "",
  email: "",
  position: "",
  company: "",
  applyDate: "",
  status: "pending",
  skills: [],
  projectExperience: "",
  selfIntroduction: "",
  resumeFile: "",
  certificateFiles: [],
  // 申请类型相关字段
  applicationType: "",
  reason: "",
  oldCompany: "",
  newCompany: "",
  materials: {},
});

// 自主实习申请文件列表
const enterpriseIntroFileList = ref([]);
const internshipPlanFileList = ref([]);
const safetyAgreementFileList = ref([]);

// 考研延迟申请文件列表
const delayPlanFileList = ref([]);
const studyPlanFileList = ref([]);
const delayApplicationFileList = ref([]);
const customSkill = ref("");
const resumeFileList = ref([]);
const certificateFileList = ref([]);

// 初始化表单数据
const initFormData = () => {
  // 从store获取用户基础信息
  const user = authStore.user;

  // 转换性别字段：数字0/1转换为male/female
  let genderValue = user?.gender || '';
  if (genderValue === 0 || genderValue === '0') {
    genderValue = 'male';
  } else if (genderValue === 1 || genderValue === '1') {
    genderValue = 'female';
  }

  jobApplicationForm.value = {
    studentName: user?.name || '',
    studentId: user?.studentId || '',
    gender: genderValue,
    grade: user?.grade || '',
    school: user?.school || '',
    education: user?.education || '',
    major: user?.major || '',
    majorClass: user?.class || '',
    phone: user?.phone || '',
    email: user?.email || '',
    position: "",
    company: "",
    applyDate: new Date().toISOString().split("T")[0],
    status: "pending",
    skills: [],
    projectExperience: "",
    selfIntroduction: "",
    resumeFile: "",
    certificateFiles: [],
    applicationType: "",
    reason: "",
    oldCompany: "",
    newCompany: "",
    materials: {},
  };
};

// 预定义技能列表
const predefinedSkills = [
  { label: "Python", value: "python" },
  { label: "Java", value: "java" },
  { label: "C++", value: "cplusplus" },
  { label: "JavaScript", value: "javascript" },
  { label: "TypeScript", value: "typescript" },
  { label: "React", value: "react" },
  { label: "Vue", value: "vue" },
  { label: "HTML/CSS", value: "htmlcss" },
  { label: "Node.js", value: "nodejs" },
  { label: "SQL", value: "sql" },
  { label: "Git", value: "git" },
  { label: "Linux", value: "linux" },
  { label: "Docker", value: "docker" },
  { label: "Kubernetes", value: "kubernetes" },
  { label: "Spring Boot", value: "springboot" },
  { label: "MyBatis", value: "mybatis" },
  { label: "Redis", value: "redis" },
  { label: "MongoDB", value: "mongodb" },
  { label: "机器学习", value: "ml" },
  { label: "深度学习", value: "dl" },
];

// 切换技能选中状态
const toggleSkill = (skillValue) => {
  const index = jobApplicationForm.value.skills.indexOf(skillValue);
  if (index > -1) {
    jobApplicationForm.value.skills.splice(index, 1);
  } else {
    jobApplicationForm.value.skills.push(skillValue);
  }
};

// 添加自定义技能
const addCustomSkill = () => {
  if (customSkill.value.trim()) {
    const skillValue = customSkill.value.trim();
    if (!jobApplicationForm.value.skills.includes(skillValue)) {
      jobApplicationForm.value.skills.push(skillValue);
    }
    customSkill.value = "";
  }
};

// 获取技能标签
const getSkillLabel = (skillValue) => {
  const skill = predefinedSkills.find((s) => s.value === skillValue);
  return skill ? skill.label : skillValue;
};

// 移除技能
const removeSkill = (index) => {
  jobApplicationForm.value.skills.splice(index, 1);
};

// 处理文件上传
const handleFileChange = (file, type) => {
  if (type === "resumeFile") {
    jobApplicationForm.value.resumeFile = file.name;
    resumeFileList.value = [file];
  } else if (type === "certificateFiles") {
    jobApplicationForm.value.certificateFiles.push(file.name);
    certificateFileList.value.push(file);
  }
};

// 移除文件
const removeFile = (type, index) => {
  if (type === "resumeFile") {
    resumeFileList.value.splice(index, 1);
    jobApplicationForm.value.resumeFile = "";
  } else if (type === "certificateFiles") {
    certificateFileList.value.splice(index, 1);
    jobApplicationForm.value.certificateFiles.splice(index, 1);
  }
};

// 重置岗位申请表单
const resetJobApplicationForm = () => {
  initFormData();
  customSkill.value = "";
  resumeFileList.value = [];
  certificateFileList.value = [];
  enterpriseIntroFileList.value = [];
  internshipPlanFileList.value = [];
  safetyAgreementFileList.value = [];
  delayPlanFileList.value = [];
  studyPlanFileList.value = [];
  delayApplicationFileList.value = [];
};

// 筛选选项
const filters = [
  { label: "全部", value: "all" },
  { label: "待审核", value: "pending" },
  { label: "已通过", value: "approved" },
  { label: "已拒绝", value: "rejected" },
  { label: "已撤回", value: "withdrawn" },
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

// 状态值转换：后端String到前端字符串
const statusMap = {
  '0': 'pending',
  '1': 'approved',
  '2': 'rejected',
  '3': 'hired',
  '4': 'withdrawn',
  'pending': 'pending',
  'approved': 'approved',
  'rejected': 'rejected',
  'hired': 'hired',
  'withdrawn': 'withdrawn',
  'interview_passed': 'approved',
  'interview_failed': 'rejected'
};

// 状态值转换：前端字符串到后端Integer
const reverseStatusMap = {
  'pending': 0,
  'approved': 1,
  'rejected': 2,
  'hired': 3,
  'withdrawn': 4
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

// 计算属性：是否禁用提交按钮（岗位申请和单位变更申请需要跳转到其他页面）
const isSubmitDisabled = computed(() => {
  const type = jobApplicationForm.value.applicationType
  return type === 'job' || type === 'unitChange'
})

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
    'withdrawn': '已撤回',
    'interview_passed': '已通过',
    'interview_failed': '面试未通过'
  };
  return statusMap[status] || status;
};

// 方法：查看申请详情
const viewApplicationDetail = (application) => {
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
};

// 方法：撤回申请
const withdrawApplication = async (id) => {
  try {
    const response = await request.delete(`/student/applications/${id}`);
    if (response.code === 200) {
      const application = applications.value.find((app) => app.id === id);
      if (application) {
        application.status = "withdrawn";
      }
      ElMessage.success("申请已撤回");
    } else {
      ElMessage.error("撤回申请失败");
    }
  } catch (error) {
    console.error('撤回申请失败:', error);
    ElMessage.error("撤回申请失败");
  }
};

// 方法：删除撤回的申请
const deleteApplication = async (id) => {
  try {
    // 确认是否删除
    await ElMessageBox.confirm('确定要删除这条申请记录吗？', '删除申请', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    });
    // 从本地列表移除（后端数据已在撤回时删除）
    const index = applications.value.findIndex((app) => app.id === id);
    if (index !== -1) {
      applications.value.splice(index, 1);
    }
    ElMessage.success("申请记录已删除");
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除申请失败:', error);
      ElMessage.error("删除申请失败");
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

// 申请类型变更处理
const handleApplicationTypeChange = () => {
  // 重置相关字段
  jobApplicationForm.value.reason = "";
  jobApplicationForm.value.company = "";
  jobApplicationForm.value.oldCompany = "";
  jobApplicationForm.value.newCompany = "";
  jobApplicationForm.value.materials = {};
  enterpriseIntroFileList.value = [];
  internshipPlanFileList.value = [];
  safetyAgreementFileList.value = [];
  delayPlanFileList.value = [];
  studyPlanFileList.value = [];
  delayApplicationFileList.value = [];
};

// 材料文件变更处理 - 上传到OSS
const handleMaterialChange = async (file, materialKey) => {
  try {
    const formData = new FormData();
    formData.append('file', file.raw);

    const response = await request.post('/upload/file', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    });

    if (response.code === 200 && response.data && response.data.url) {
      // 保存OSS返回的文件URL
      jobApplicationForm.value.materials[materialKey] = response.data.url;
      ElMessage.success('文件上传成功');
    } else {
      ElMessage.error(response.message || '文件上传失败');
    }
  } catch (error) {
    console.error('文件上传失败:', error);
    ElMessage.error('文件上传失败');
  }
};

// 方法：提交岗位申请
const submitJobApplication = async () => {
  // 验证申请类型
  if (!jobApplicationForm.value.applicationType) {
    ElMessage.error("请选择申请类型");
    return;
  }

  // 岗位申请需要跳转到职位页面
  if (jobApplicationForm.value.applicationType === 'job') {
    ElMessage.info("请去职位查看页面进行职位选择和申请");
    showJobApplicationForm.value = false;
    router.push("/student/jobs");
    return;
  }

  // 单位变更申请需要跳转到实习确认页面
  if (jobApplicationForm.value.applicationType === 'unitChange') {
    ElMessage.info("请去实习确认申请页面的确认记录列表进行申请");
    showJobApplicationForm.value = false;
    router.push("/student/internships");
    return;
  }

  // 自主实习申请验证
  if (jobApplicationForm.value.applicationType === 'selfPractice') {
    if (!jobApplicationForm.value.company) {
      ElMessage.error("请填写实习单位");
      return;
    }
    if (!jobApplicationForm.value.reason) {
      ElMessage.error("请填写申请理由");
      return;
    }
  }

  // 考研延迟申请验证
  if (jobApplicationForm.value.applicationType === 'delay') {
    if (!jobApplicationForm.value.reason) {
      ElMessage.error("请填写申请理由");
      return;
    }
  }

  try {
    // 构建符合后端要求的申请数据
    const applicationData = {
      applicationType: jobApplicationForm.value.applicationType,
      studentName: jobApplicationForm.value.studentName,
      studentUserId: jobApplicationForm.value.studentId,
      grade: jobApplicationForm.value.grade,
      className: jobApplicationForm.value.majorClass,
      phone: jobApplicationForm.value.phone,
      reason: jobApplicationForm.value.reason,
      status: "pending"
    };

    // 根据申请类型添加特定字段
    if (jobApplicationForm.value.applicationType === 'selfPractice') {
      applicationData.company = jobApplicationForm.value.company;
      applicationData.materials = jobApplicationForm.value.materials;
    } else if (jobApplicationForm.value.applicationType === 'unitChange') {
      applicationData.oldCompany = jobApplicationForm.value.oldCompany;
      applicationData.newCompany = jobApplicationForm.value.newCompany;
      applicationData.materials = jobApplicationForm.value.materials;
    } else if (jobApplicationForm.value.applicationType === 'delay') {
      applicationData.materials = jobApplicationForm.value.materials;
    }

    const response = await request.post(`/student/applications`, applicationData);
    if (response.code === 200) {
      ElMessage.success("申请提交成功");
      showJobApplicationForm.value = false;

      // 重新获取申请列表
      await fetchApplications();
    } else {
      ElMessage.error(response.message || "提交申请失败");
    }
  } catch (error) {
    console.error('提交申请失败:', error);
    ElMessage.error("提交申请失败");
  }
};

// 生命周期钩子
onMounted(() => {
  initFormData();
  fetchApplications();
});
</script>

<style scoped>
/* 基础布局：统一容器样式 */
.applications-page {
  width: 100%;
  height: 100%;
  background: transparent;
  padding: 0;
  margin: 0;
  box-sizing: border-box;
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



/* 岗位申请表入口卡片：模仿通知卡片样式 */
.application-form-entry {
  margin-bottom: 24px;
}

.entry-card {
  background: white;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  border-left: 4px solid #409eff;
  display: flex;
  align-items: center;
  gap: 20px;
  transition: all 0.3s ease;
}

.entry-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.entry-icon {
  flex-shrink: 0;
}

.big-icon {
  font-size: 56px;
  color: #409eff;
  opacity: 0.8;
}

.entry-content {
  flex: 1;
}

.entry-content h3 {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px 0;
}

.entry-content p {
  font-size: 14px;
  color: #606266;
  margin: 0;
  line-height: 1.5;
}

.entry-action {
  flex-shrink: 0;
}

.form-entry-btn {
  padding: 12px 24px;
  font-size: 14px;
  font-weight: 500;
  border-radius: 8px;
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
  background: white;
  border-radius: 12px;
  padding: 16px 24px;
  margin-bottom: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
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
  background: white;
  border-radius: 12px;
  padding: 16px 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
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

/* 申请列表卡片：小卡片网格布局 - 美化版 */
.applications-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 20px;
  max-height: calc(100vh - 560px);
  overflow-y: auto;
  padding-right: 4px;
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
  height: 100%;
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

.status-tag.withdrawn {
  background: linear-gradient(135deg, #fafafa 0%, #e8e8e8 100%);
  color: #8c8c8c;
  border: 1px solid #d9d9d9;
  box-shadow:
    0 2px 8px rgba(0, 0, 0, 0.1),
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
  padding: 60px 24px;
  background: white;
  border-radius: 12px;
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

/* 申请详情对话框样式：统一风格 */
.detail-dialog :deep(.el-dialog) {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.detail-dialog :deep(.el-dialog__header) {
  padding: 0;
  border-bottom: none;
  margin: 0;
}

.detail-dialog :deep(.el-dialog__title) {
  display: none;
}

.detail-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
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

.detail-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  background: rgba(255, 255, 255, 0.3);
}

.detail-dialog :deep(.el-dialog__body) {
  padding: 0;
  background-color: #ffffff;
  max-height: 75vh;
  overflow-y: auto;
}

/* 岗位申请表对话框样式：完全匹配示例风格 */
.job-application-dialog :deep(.el-dialog) {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.job-application-dialog :deep(.el-dialog__header) {
  padding: 0;
  border-bottom: none;
  margin: 0;
}

.job-application-dialog :deep(.el-dialog__title) {
  display: none;
}

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

.job-application-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  background: rgba(255, 255, 255, 0.3);
}

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

/* 表单内容样式 */
.form-content {
  padding: 24px;
  max-height: 55vh;
  overflow-y: auto;
  margin: 0 auto;
  max-width: 900px;
}

/* 表单模块样式：匹配示例的模块风格 */
.form-module {
  background-color: transparent;
  border: none;
  border-radius: 0;
  padding: 0;
  margin-bottom: 24px;
  box-shadow: none;
}

/* 表单网格布局 */
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  align-items: start;
}

.form-single {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-grid :deep(.el-form-item) {
  margin-bottom: 0;
}

/* 表单元素样式：统一样式 */
:deep(.el-form-item) {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
}

:deep(.el-form-item__label) {
  font-size: 14px;
  color: #334155;
  font-weight: 500;
  height: 36px;
  line-height: 36px;
  padding: 0 12px 0 0;
}

:deep(.el-form-item__content) {
  flex: 1;
  line-height: 36px;
}

:deep(.el-input__wrapper) {
  border-radius: 4px;
  border: 1px solid #e2e8f0;
  padding: 0 12px;
  background-color: #ffffff;
  box-shadow: none;
  transition: all 0.2s ease;
  height: 36px;
  min-height: 36px;
  display: flex;
  align-items: center;
}

:deep(.el-input__inner) {
  height: 36px;
  line-height: 36px;
  font-size: 14px;
}

:deep(.el-select__wrapper) {
  border-radius: 4px;
  border: 1px solid #e2e8f0;
  padding: 0 12px;
  background-color: #ffffff;
  box-shadow: none;
  transition: all 0.2s ease;
  height: 36px;
  min-height: 36px;
  display: flex;
  align-items: center;
}

:deep(.el-select__selected-item) {
  line-height: 36px;
  font-size: 14px;
}

:deep(.el-textarea__inner) {
  border-radius: 4px;
  border: 1px solid #e2e8f0;
  padding: 8px 12px;
  background-color: #ffffff;
  box-shadow: none;
  transition: all 0.2s ease;
  font-size: 14px;
  line-height: 1.6;
}

:deep(.el-input__wrapper:focus-within),
:deep(.el-select__wrapper:focus-within),
:deep(.el-textarea__inner:focus) {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

/* 技能选择区域 */
.skills-section {
  background: transparent;
  padding: 0;
  border-radius: 0;
  border: none;
}

.skills-tip {
  font-size: 14px;
  color: #64748b;
  margin-bottom: 12px;
  line-height: 1.6;
}

.skills-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.skill-tag {
  padding: 6px 14px;
  border-radius: 4px;
  background: white;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 13px;
  border: 1px solid #e2e8f0;
  font-weight: 500;
}

.skill-tag:hover {
  background: #f1f5f9;
  color: #1e293b;
  border-color: #cbd5e1;
}

.skill-tag.active {
  background: linear-gradient(90deg, #1e88e5 0%, #4caf50 100%);
  color: white;
  border-color: transparent;
}

.add-skill-container {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.selected-skills {
  margin-top: 16px;
}

.selected-skills-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 8px;
}

.selected-skills-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.selected-skill-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: linear-gradient(135deg, #3b82f6 0%, #10b981 100%);
  color: white;
  border-radius: 16px;
  font-size: 13px;
}

/* 附件上传区域 */
.upload-section {
  background: transparent;
  padding: 0;
  border-radius: 0;
  border: none;
}

.upload-item {
  background: white;
  border-radius: 4px;
  overflow: hidden;
  box-shadow: none;
  border: 1px solid #e8eaed;
  margin-bottom: 16px;
  transition: all 0.2s ease;
}

.upload-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.upload-item-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #f8fafc;
  border-bottom: 1px solid #e8eaed;
}

.upload-item-icon {
  font-size: 18px;
  color: #1e88e5;
}

.upload-item-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.upload-item-content {
  padding: 12px;
}

.upload-dragger :deep(.el-upload-dragger) {
  width: 100%;
  padding: 20px 12px;
  border: 2px dashed #d1d5db;
  border-radius: 4px;
  background: #fafafa;
  transition: all 0.2s ease;
}

.upload-dragger :deep(.el-upload-dragger:hover) {
  border-color: #1e88e5;
  background: #f0f9ff;
}

.upload-text-main {
  font-size: 14px;
  color: #64748b;
  margin-bottom: 6px;
  font-weight: 500;
}

.upload-text-sub {
  font-size: 12px;
  color: #94a3b8;
}

.uploaded-file-list {
  margin-top: 16px;
}

.uploaded-file-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  margin-bottom: 8px;
}

.file-icon {
  font-size: 16px;
  color: #3b82f6;
}

.file-name {
  flex: 1;
  font-size: 13px;
  color: #1e293b;
}

.file-remove {
  font-size: 14px;
  color: #94a3b8;
  cursor: pointer;
}

.file-remove:hover {
  color: #ef4444;
}

/* 申请提示样式 */
.application-tip {
  grid-column: 1 / -1;
  margin: 10px 0;
}

/* 表单按钮区域 */
.form-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 24px;
  padding: 20px 32px;
  border-top: 1px solid #f0f0f0;
  background: #fafafa;
}

.submit-btn {
  background: linear-gradient(90deg, #3b82f6, #10b981) !important;
  color: #ffffff !important;
  border: none !important;
  border-radius: 4px !important;
  padding: 10px 28px !important;
  font-size: 14px !important;
  font-weight: 500 !important;
  transition: all 0.2s ease !important;
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.2) !important;
}

.submit-btn:hover {
  background: linear-gradient(90deg, #2563eb, #059669) !important;
  transform: translateY(-1px) !important;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3) !important;
}

.reset-btn {
  background: #ffffff !important;
  color: #64748b !important;
  border: 1px solid #e2e8f0 !important;
  border-radius: 4px !important;
  padding: 10px 28px !important;
  font-size: 14px !important;
  font-weight: 500 !important;
  transition: all 0.2s ease !important;
  box-shadow: none !important;
}

.reset-btn:hover {
  border-color: #3b82f6 !important;
  color: #3b82f6 !important;
  transform: translateY(-1px) !important;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.15) !important;
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

/* 职位详情对话框样式（与Jobs.vue保持完全一致） */
.job-detail-dialog :deep(.el-dialog) {
  border-radius: 20px !important;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  animation: dialog-fade-in 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.job-detail-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  color: white;
  padding: 28px 36px;
  margin: 0;
  border-radius: 0;
  position: relative;
}

.job-detail-dialog :deep(.el-dialog__title) {
  color: white;
  font-weight: 700;
  font-size: 22px;
}

.job-detail-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
  font-size: 24px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  width: 36px;
  height: 36px;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.job-detail-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  background: rgba(255, 255, 255, 0.4);
  transform: rotate(90deg) scale(1.1);
  box-shadow: 0 4px 12px rgba(255, 255, 255, 0.4);
}

.job-detail-dialog :deep(.el-dialog__body) {
  padding: 28px 36px;
  background: white;
  max-height: 70vh;
  overflow-y: auto;
}

.job-detail-content {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding-bottom: 20px;
  border-bottom: 2px solid #f0f0f0;
}

.detail-title {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.detail-title h3 {
  font-size: 28px;
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

.detail-actions {
  flex-shrink: 0;
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

.detail-section {
  background: linear-gradient(135deg, #fafbfc 0%, #f5f7fa 100%);
  border-radius: 16px;
  padding: 24px;
  border: 1px solid #e8e8e8;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.section-title {
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 20px 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 2px solid #e8e8e8;
}

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

.info-item .el-icon {
  color: #409eff;
  font-size: 18px;
}

.info-label {
  font-weight: 600;
  color: #1e293b;
}

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

/* 动画 */
@keyframes dialog-fade-in {
  from {
    opacity: 0;
    transform: scale(0.95) translateY(-20px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

/* 响应式适配：匹配网格布局的响应式规则 */
@media screen and (max-width: 1200px) {
  .applications-list {
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  }
  
  .form-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media screen and (max-width: 768px) {
  .header-content {
    flex-direction: column;
    text-align: center;
    gap: 16px;
  }

  .entry-card {
    flex-direction: column;
    text-align: center;
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

  .form-grid {
    grid-template-columns: 1fr;
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
