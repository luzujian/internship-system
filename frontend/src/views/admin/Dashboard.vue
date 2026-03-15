<template>
  <div class="teacher-management-container dashboard-management-container">
    <!-- 顶部标题区域 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">系统管理数据看板</h1>
        <p class="page-description">全面掌握系统数据，高效管理各项业务</p>
      </div>
      <div class="header-illustration">
        <div class="illustration-circle circle-1"></div>
        <div class="illustration-circle circle-2"></div>
      </div>
    </div>

    <!-- 顶部操作区域 -->
    <el-card class="search-card" shadow="never">
      <div class="dashboard-topbar">
        <div class="dashboard-user-info">
          <el-icon size="20" color="#409EFF"><User /></el-icon>
          <span class="user-info-text">欢迎回来，{{ getSafeUsername() }}</span>
          <span class="date-info">{{ currentDate }}</span>
        </div>
        <div class="dashboard-actions">
          <el-button type="primary" @click="showResourceDocumentDialog" class="action-btn primary">
            <el-icon><Document /></el-icon>&nbsp;上传资源
          </el-button>
          <el-button type="success" @click="showAnnouncementDialog" class="action-btn success">
            <el-icon><Promotion /></el-icon>&nbsp;发布公告
          </el-button>
          <el-button type="success" @click="refreshAllData" :loading="loading" class="action-btn primary">
            <el-icon><Refresh /></el-icon>&nbsp;刷新数据
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 核心统计卡片和问题反馈 -->
    <div class="dashboard-content">
      <div class="stats-section">
        <div class="stats-cards-grid">
          <el-card class="stat-card students" shadow="never" @click="navigateTo('/admin/student-users')">
            <div class="stat-content">
              <div class="stat-icon-wrapper">
                <el-icon><UserFilled /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.totalStudents }}</div>
                <div class="stat-label">学生总人数</div>
              </div>
              <div class="stat-action">
                <span>点击查看详情</span>
                <el-icon><ArrowRight /></el-icon>
              </div>
            </div>
          </el-card>
          
          <el-card class="stat-card teachers" shadow="never" @click="navigateTo('/admin/teacher-users')">
            <div class="stat-content">
              <div class="stat-icon-wrapper">
                <el-icon><Avatar /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.totalTeachers }}</div>
                <div class="stat-label">教师总人数</div>
              </div>
              <div class="stat-action">
                <span>点击查看详情</span>
                <el-icon><ArrowRight /></el-icon>
              </div>
            </div>
          </el-card>
          
          <el-card class="stat-card companies" shadow="never" @click="navigateTo('/admin/companies')">
            <div class="stat-content">
              <div class="stat-icon-wrapper">
                <el-icon><OfficeBuilding /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.totalCompanies }}</div>
                <div class="stat-label">企业用户总数</div>
              </div>
              <div class="stat-action">
                <span>点击查看详情</span>
                <el-icon><ArrowRight /></el-icon>
              </div>
            </div>
          </el-card>
          
          <el-card class="stat-card departments-majors" shadow="never" @click="navigateTo('/admin/majors')">
            <div class="stat-content">
              <div class="stat-icon-wrapper">
                <el-icon><School /></el-icon>
              </div>
              <div class="stat-info-row">
                <div class="stat-info">
                  <div class="stat-value">{{ stats.totalDepartments }}</div>
                  <div class="stat-label">学院总数</div>
                </div>
                <div class="stat-divider"></div>
                <div class="stat-info">
                  <div class="stat-value">{{ stats.totalMajors }}</div>
                  <div class="stat-label">专业总数</div>
                </div>
              </div>
              <div class="stat-action">
                <span>点击查看详情</span>
                <el-icon><ArrowRight /></el-icon>
              </div>
            </div>
          </el-card>
          
          
          
          <el-card class="stat-card positions" shadow="never" @click="navigateTo('/admin/position-categories')">
            <div class="stat-content">
              <div class="stat-icon-wrapper">
                <el-icon><Briefcase /></el-icon>
              </div>
              <div class="stat-info-row">
                <div class="stat-info">
                  <div class="stat-value">{{ stats.totalPositionCategories }}</div>
                  <div class="stat-label">岗位类别总数</div>
                </div>
                <div class="stat-divider"></div>
                <div class="stat-info">
                  <div class="stat-value">{{ stats.totalPositions }}</div>
                  <div class="stat-label">岗位总数</div>
                </div>
              </div>
              <div class="stat-action">
                <span>点击查看详情</span>
                <el-icon><ArrowRight /></el-icon>
              </div>
            </div>
          </el-card>

          <el-card class="stat-card ai-models" shadow="never" @click="navigateTo('/admin/ai-model')">
            <div class="stat-content">
              <div class="stat-icon-wrapper ai-icon">
                <span>AI</span>
              </div>
              <div class="stat-info-row">
                <div class="stat-info">
                  <div class="stat-value">{{ stats.totalAIModels }}</div>
                  <div class="stat-label">AI模型总数</div>
                </div>
                <div class="stat-divider"></div>
                <div class="stat-info">
                  <div class="stat-value" style="font-size: 14px;">{{ stats.currentAIModel }}</div>
                  <div class="stat-label">当前模型</div>
                </div>
              </div>
              <div class="stat-action">
                <span>点击查看详情</span>
                <el-icon><ArrowRight /></el-icon>
              </div>
            </div>
          </el-card>
        
        </div>
      </div>

      

      <div class="feedback-section">
        <el-card class="feedback-card" shadow="never">
          <template #header>
            <div class="feedback-header">
              <div class="feedback-title">
                <el-icon><ChatLineRound /></el-icon>
                <span>最近问题反馈</span>
                <el-badge :value="processingFeedbackCount" :hidden="processingFeedbackCount === 0" class="feedback-badge" type="danger" />
              </div>
              <span class="view-all-link" @click="navigateTo('/admin/problem-feedback')">
                查看全部
                <el-icon><ArrowRight /></el-icon>
              </span>
            </div>
          </template>
          <el-scrollbar height="400px">
            <div class="feedback-container" v-loading="loadingFeedback">
              <div v-if="recentFeedback.length === 0" class="empty-feedback">
                <el-empty description="暂无问题反馈" />
              </div>
              <div v-else class="feedback-list">
                <div v-for="feedback in recentFeedback" :key="feedback.id" class="feedback-item" @click="navigateToFeedback(feedback.id)">
                  <div class="feedback-content">
                    <div class="feedback-header">
                      <span class="feedback-user">{{ feedback.userName }}</span>
                      <el-tag :type="getUserTypeTag(feedback.userType)" size="small">
                        {{ getUserTypeText(feedback.userType) }}
                      </el-tag>
                    </div>
                    <div class="feedback-title-text">{{ feedback.title }}</div>
                    <div class="feedback-time">{{ formatFeedbackTime(feedback.createTime) }}</div>
                  </div>
                </div>
              </div>
            </div>
          </el-scrollbar>
        </el-card>
      </div>
    </div>

    <!-- 操作日志和反馈区域 -->
    <div class="logs-feedback-container">
      <!-- 操作日志卡片 -->
      <el-card class="logs-card" shadow="never">
        <template #header>
          <div class="logs-header">
            <div class="logs-title">
              <el-icon><Document /></el-icon>
              <span>最近操作日志</span>
              <el-select v-model="logFilterRole" placeholder="选择角色" size="small" @change="fetchRecentLogs" style="width: 120px; margin-left: 10px;">
                <el-option label="全部" value=""></el-option>
                <el-option label="学生" value="STUDENT"></el-option>
                <el-option label="教师" value="TEACHER"></el-option>
                <el-option label="管理员" value="ADMIN"></el-option>
                <el-option label="企业" value="COMPANY"></el-option>
              </el-select>
            </div>
            <div class="logs-filter">
              <span class="view-all-link" @click="navigateTo('/admin/logs')">
                查看全部
                <el-icon><ArrowRight /></el-icon>
              </span>
            </div>
          </div>
        </template>
        <el-scrollbar height="400px">
          <div class="logs-container" v-loading="loadingLogs">
            <div v-if="recentLogs.length === 0" class="empty-logs">
              <el-empty description="暂无操作日志" />
            </div>
            <div v-else class="logs-list">
              <div v-for="log in recentLogs" :key="log.id" class="log-item">
                <div class="log-content">
                  <div class="log-header">
                    <span class="log-operator">{{ log.operatorName }}</span>
                    <el-tag v-if="log.operatorRole" :type="getRoleTag(log.operatorRole)" size="small" class="log-role-tag">
                      {{ getRoleName(log.operatorRole) }}
                    </el-tag>
                    <el-tag :type="log.operationResult === 'SUCCESS' ? 'success' : 'danger'" size="small" class="log-result-tag">
                      {{ log.operationResult === 'SUCCESS' ? '成功' : '失败' }}
                    </el-tag>
                    <el-tag :type="getOperationTypeTag(log.operationType)" size="small" class="log-type-tag">
                      {{ getOperationTypeText(log.operationType) }}
                    </el-tag>
                    <span class="log-module">{{ getModuleName(log.module) }}</span>
                    <span class="log-time">{{ formatLogTime(log.operateTime) }}</span>
                  </div>
                  <div class="log-detail">
                    <span class="log-description">{{ log.description }}</span>
                    <div v-if="log.ipAddress" class="log-ip">
                      <el-icon><Location /></el-icon>
                      <span>{{ log.ipAddress }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </el-scrollbar>
      </el-card>

      <!-- 最近公告区域 -->
      <div class="announcement-section">
        <el-card class="announcement-card" shadow="never">
          <template #header>
            <div class="announcement-header">
              <div class="announcement-title">
                <el-icon><Promotion /></el-icon>
                <span>最近公告</span>
                <el-badge :value="publishedAnnouncementCount" :hidden="publishedAnnouncementCount === 0" class="announcement-badge" type="success" />
              </div>
              <span class="view-all-link" @click="navigateTo('/admin/announcements')">
                查看全部
                <el-icon><ArrowRight /></el-icon>
              </span>
            </div>
          </template>
          <el-scrollbar height="400px">
            <div class="announcement-container" v-loading="loadingAnnouncement">
              <div v-if="recentAnnouncements.length === 0" class="empty-announcement">
                <el-empty description="暂无公告" />
              </div>
              <div v-else class="announcement-list">
                <div v-for="announcement in recentAnnouncements" :key="announcement.id" class="announcement-item" @click="navigateToAnnouncement(announcement.id)">
                  <div class="announcement-content">
                    <div class="announcement-header">
                      <span class="announcement-title-text">{{ announcement.title }}</span>
                      <el-tag :type="getPriorityTagType(announcement.priority)" size="small">
                        {{ getPriorityText(announcement.priority) }}
                      </el-tag>
                    </div>
                    <div class="announcement-publisher">
                      <el-icon><User /></el-icon>
                      <span>{{ announcement.publisherName || '管理员' }}</span>
                    </div>
                    <div class="announcement-time">{{ formatAnnouncementTime(announcement.publishTime || announcement.createTime) }}</div>
                  </div>
                </div>
              </div>
            </div>
          </el-scrollbar>
        </el-card>
      </div>
    </div>

    <!-- 公告发布对话框 -->
    <el-dialog v-model="announcementDialogVisible" title="发布公告" width="800px" @close="handleAnnouncementDialogClose">
      <el-form :model="announcementForm" :rules="announcementRules" ref="announcementFormRef" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="announcementForm.title" placeholder="请输入公告标题" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="announcementForm.content"
            type="textarea"
            :rows="8"
            placeholder="请输入公告内容"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="附件">
          <el-upload
            v-model:file-list="announcementForm.attachments"
            :action="uploadAction"
            :headers="uploadHeaders"
            :on-success="handleAnnouncementUploadSuccess"
            :on-remove="handleAnnouncementRemoveFile"
            :before-upload="beforeAnnouncementUpload"
            :limit="5"
            :on-exceed="handleAnnouncementExceed"
            multiple
            :auto-upload="true"
            class="compact-upload"
          >
            <el-button type="primary" size="small">
              <el-icon><Paperclip /></el-icon>
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
          <el-select v-model="announcementForm.status" placeholder="请选择公告状态">
            <el-option label="草稿" value="DRAFT"></el-option>
            <el-option label="发布" value="PUBLISHED"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="发布日期" prop="publishDate">
          <el-date-picker v-model="announcementForm.publishDate" type="datetime" placeholder="选择发布日期" style="width: 100%"></el-date-picker>
        </el-form-item>
        <el-form-item label="过期日期" prop="expireDate">
          <el-date-picker v-model="announcementForm.expireDate" type="datetime" placeholder="选择过期日期" style="width: 100%"></el-date-picker>
        </el-form-item>
        <el-form-item label="发布人身份" prop="publisherRole">
          <el-select v-model="announcementForm.publisherRole" placeholder="请选择发布人身份" @change="handlePublisherRoleChange">
            <el-option label="管理员" value="ADMIN"></el-option>
            <el-option label="学院教师" value="COLLEGE"></el-option>
            <el-option label="系室教师" value="DEPARTMENT"></el-option>
            <el-option label="辅导员" value="COUNSELOR"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="发布人" prop="publisher">
          <el-select 
            v-model="announcementForm.publisher" 
            placeholder="请选择或输入发布人姓名" 
            :loading="userListLoading" 
            :disabled="!announcementForm.publisherRole"
            filterable
            allow-create
            clearable
          >
            <el-option v-for="user in userList" :key="user.id" :label="user.name" :value="user.name"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="announcementForm.priority" placeholder="请选择优先级">
            <el-option label="普通" value="normal"></el-option>
            <el-option label="重要" value="important"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="目标群体" prop="targetType">
          <el-select v-model="announcementForm.targetType" placeholder="请选择目标群体" multiple collapse-tags collapse-tags-tooltip>
            <el-option label="全体学生" value="STUDENT"></el-option>
            <el-option label="全体教师" value="TEACHER"></el-option>
            <el-option label="特定教师类别" value="TEACHER_TYPE"></el-option>
            <el-option label="特定专业学生" value="MAJOR"></el-option>
            <el-option label="企业用户" value="COMPANY"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="announcementForm.targetType.includes('TEACHER_TYPE')" label="教师类别">
          <el-select v-model="announcementForm.teacherTypes" placeholder="请选择教师类别（可多选）" multiple collapse-tags collapse-tags-tooltip>
            <el-option label="学院教师" value="COLLEGE"></el-option>
            <el-option label="系室教师" value="DEPARTMENT"></el-option>
            <el-option label="辅导员" value="COUNSELOR"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="announcementForm.targetType.includes('MAJOR')" label="专业">
          <el-select v-model="announcementForm.majorIds" placeholder="请选择专业（可多选）" multiple collapse-tags collapse-tags-tooltip filterable>
            <el-option v-for="major in majorList" :key="major.id" :label="major.name" :value="String(major.id)"></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="announcementDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAnnouncement" :loading="submitting">发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resourceDocumentDialogVisible" title="上传资源" width="700px" @close="handleResourceDocumentDialogClose">
      <el-form ref="resourceDocumentFormRef" :model="resourceDocumentForm" :rules="resourceDocumentRules" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="resourceDocumentForm.title" placeholder="请输入文档标题"></el-input>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="resourceDocumentForm.description" type="textarea" :rows="4" placeholder="请输入文档描述"></el-input>
        </el-form-item>
        <el-form-item label="文件" prop="file">
          <div class="file-upload-container">
            <el-upload
              ref="resourceUploadRef"
              :auto-upload="false"
              :limit="1"
              :on-change="handleResourceFileChange"
              :on-remove="handleResourceFileRemove"
              :file-list="resourceFileList"
            >
              <el-button type="primary">选择文件</el-button>
              <template #tip>
                <div class="el-upload__tip">支持PDF、Word、Excel、PPT、TXT、ZIP、RAR、7Z等格式，文件大小不超过10MB</div>
              </template>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="resourceDocumentForm.status" placeholder="请选择文档状态">
            <el-option label="草稿" value="DRAFT"></el-option>
            <el-option label="发布" value="PUBLISHED"></el-option>
            <el-option label="归档" value="ARCHIVED"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="发布人身份" prop="publisherRole">
          <el-select v-model="resourceDocumentForm.publisherRole" placeholder="请选择发布人身份" @change="handleResourcePublisherRoleChange">
            <el-option label="管理员" value="ADMIN"></el-option>
            <el-option label="学院教师" value="COLLEGE"></el-option>
            <el-option label="系室教师" value="DEPARTMENT"></el-option>
            <el-option label="辅导员" value="COUNSELOR"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="发布人" prop="publisher">
          <el-select 
            v-model="resourceDocumentForm.publisher" 
            placeholder="请选择或输入发布人姓名" 
            :loading="resourceUserListLoading" 
            :disabled="!resourceDocumentForm.publisherRole"
            filterable
            allow-create
            clearable
          >
            <el-option v-for="user in resourceUserList" :key="user.id" :label="user.name" :value="user.name"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="目标群体">
          <el-select v-model="resourceDocumentForm.targetType" placeholder="请选择目标群体">
            <el-option label="全体师生" value="ALL"></el-option>
            <el-option label="全体学生" value="STUDENT"></el-option>
            <el-option label="全体教师" value="TEACHER"></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resourceDocumentDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitResourceDocument" :loading="resourceSubmitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import type { TeacherUser, Announcement, OperationLog, Feedback } from '@/types/admin'

import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Refresh, UserFilled, Avatar, ArrowRight, User,
  OfficeBuilding, School, Reading, Briefcase, Document, ChatLineRound, Promotion, RefreshLeft, Location, Paperclip
} from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useAuthStore } from '@/store/auth'
import { queryPageApi } from '@/api/log'
import * as announcementApi from '@/api/announcement'
import MajorService from '@/api/major'
import * as resourceDocumentApi from '@/api/resourceDocument'
import {
  initAnnouncementWebSocket,
  disconnectAnnouncementWebSocket
} from '@/utils/websocket'

const router = useRouter()
const authStore = useAuthStore()

// 加载状态
const loading = ref<boolean>(false)
const loadingLogs = ref<boolean>(false)
const loadingFeedback = ref<boolean>(false)
const loadingAnnouncement = ref<boolean>(false)
const submitting = ref<boolean>(false)

const logFilterRole = ref<string>('')

// 当前日期
const currentDate = ref<string>('')

// 统计数据
const stats = ref({
  totalStudents: 0,
  totalTeachers: 0,
  totalCompanies: 0,
  totalDepartments: 0,
  totalMajors: 0,
  totalPositions: 0,
  totalPositionCategories: 0,
  totalAIModels: 0,
  currentAIModel: ''
})

// 最近操作日志
const recentLogs = ref<unknown[]>([])

// 最近问题反馈
const recentFeedback = ref<unknown[]>([])
const processingFeedbackCount = ref<number>(0)

// 最近公告
const recentAnnouncements = ref<unknown[]>([])
const publishedAnnouncementCount = ref<number>(0)

// 公告发布相关
const announcementDialogVisible = ref<boolean>(false)
const announcementFormRef = ref<InstanceType<typeof ElForm> | null>(null)
const majorList = ref<unknown[]>([])
const userList = ref<unknown[]>([])
const userListLoading = ref<boolean>(false)

const uploadAction = '/api/upload/file'
const uploadHeaders = computed(() => ({
  'Authorization': `Bearer ${authStore.token}`
}))

const announcementForm = ref({
  title: '',
  content: '',
  status: 'DRAFT',
  publisher: '',
  publisherRole: '',
  publishDate: null,
  expireDate: null,
  priority: 'normal',
  targetType: [] as string[],
  teacherTypes: [] as string[],
  majorIds: [] as string[],
  targetValue: null,
  attachments: []
})

const announcementRules = {
  title: [
    { required: true, message: '请输入公告标题', trigger: 'blur' },
    { min: 2, max: 200, message: '标题长度在2-200个字符', trigger: 'blur' }
  ],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }],
  status: [{ required: true, message: '请选择公告状态', trigger: 'change' }],
  publisher: [{ required: true, message: '请选择发布人姓名', trigger: 'change' }],
  publisherRole: [{ required: true, message: '请选择发布人身份', trigger: 'change' }],
  publishDate: [{ required: true, message: '请选择发布日期', trigger: 'change' }],
  expireDate: [{ required: true, message: '请选择过期日期', trigger: 'change' }],
  targetType: [
    { 
      required: true, 
      message: '请选择目标群体', 
      trigger: 'change',
      validator: (rule, value, callback) => {
        if (!value || value.length === 0) {
          callback(new Error('请选择目标群体'))
        } else {
          callback()
        }
      }
    }
  ]
}

const handleAnnouncementUploadSuccess = (response, file, fileList) => {
  if (response.code === 200) {
    file.url = response.data.url
    file.response = response.data
    ElMessage.success('文件上传成功')
  } else {
    ElMessage.error(response.msg || '文件上传失败')
  }
}

const handleAnnouncementRemoveFile = (file, fileList) => {
  ElMessage.info('已移除文件: ' + file.name)
}

const beforeAnnouncementUpload = (file) => {
  const maxSize = 10 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过10MB')
    return false
  }
  return true
}

const handleAnnouncementExceed = (files, fileList) => {
  ElMessage.warning('最多只能上传5个文件')
}

// 资源文档发布相关
const resourceDocumentDialogVisible = ref<boolean>(false)
const resourceDocumentFormRef = ref<InstanceType<typeof ElForm> | null>(null)
const uploadRef = ref<InstanceType<typeof ElForm> | null>(null)
const resourceSubmitting = ref<boolean>(false)
const resourceUserList = ref<unknown[]>([])
const resourceUserListLoading = ref<boolean>(false)
const resourceDocumentForm = ref({
  title: '',
  description: '',
  file: null,
  status: 'PUBLISHED',
  publisher: '',
  publisherRole: '',
  targetType: 'ALL'
})
const resourceDocumentRules = {
  title: [
    { required: true, message: '请输入文档标题', trigger: 'blur' }
  ],
  publisher: [
    { required: true, message: '请输入发布人', trigger: 'blur' }
  ],
  publisherRole: [
    { required: true, message: '请选择发布人身份', trigger: 'change' }
  ],
  status: [
    { required: true, message: '请选择文档状态', trigger: 'change' }
  ]
}
const resourceFileList = ref<unknown[]>([])

// 获取当前日期
const getCurrentDate = () => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const weekday = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][now.getDay()]
  currentDate.value = `${year}年${month}月${day}日 ${weekday}`
}

// 获取用户名
const getSafeUsername = () => {
  if (authStore && authStore.user && authStore.user.username) {
    return authStore.user.username
  }
  if (authStore && authStore.user && authStore.user.name) {
    return authStore.user.name
  }
  return '管理员'
}

// 获取统计数据
const fetchStats = async (): Promise<void> => {
  try {
    const response = await request.get('/admin/dashboard/stats')
    if (response.code === 200 && response.data) {
      stats.value = { ...stats.value, ...response.data }
    }
  } catch (error) {
    logger.error('获取统计数据失败:', error)
  }
  
  try {
    const categoriesResponse = await request.get('/admin/position-categories')
    if (categoriesResponse && categoriesResponse.code === 200 && categoriesResponse.data) {
      stats.value.totalPositionCategories = categoriesResponse.data.length
    }
  } catch (error) {
    logger.error('获取岗位类别总数失败:', error)
  }

  try {
    const modelsResponse = await request.get('/admin/ai-model')
    if (modelsResponse && modelsResponse.code === 200 && modelsResponse.data) {
      stats.value.totalAIModels = modelsResponse.data.length
    }
  } catch (error) {
    logger.error('获取AI模型总数失败:', error)
  }

  try {
    const defaultResponse = await request.get('/admin/ai-model/default')
    if (defaultResponse && defaultResponse.code === 200 && defaultResponse.data) {
      stats.value.currentAIModel = defaultResponse.data.modelName || '未设置'
    }
  } catch (error) {
    logger.error('获取当前AI模型失败:', error)
  }
}

// 获取最近操作日志
const fetchRecentLogs = async (): Promise<void> => {
  loadingLogs.value = true
  try {
    const response = await queryPageApi('', logFilterRole.value, '', '', 1, 5, 'operate_time', 'desc')
    if (response.code === 200 && response.data) {
      const rawData = response.data.rows || []
      recentLogs.value = rawData.map(log => ({
        ...log,
        operationTypeName: getOperationTypeText(log.operationType),
        moduleName: getModuleName(log.module),
        roleName: getRoleName(log.operatorRole)
      }))
    }
  } catch (error) {
    logger.error('获取操作日志失败:', error)
    recentLogs.value = []
  } finally {
    loadingLogs.value = false
  }
}

// 获取最近问题反馈
const fetchRecentFeedback = async (): Promise<void> => {
  loadingFeedback.value = true
  try {
    const [feedbackResponse, countResponse] = await Promise.all([
      request.get('/problem-feedback/page', {
        params: {
          page: 1,
          pageSize: 100,
          status: 'processing'
        }
      }),
      request.get('/problem-feedback/processing-count')
    ])
    if (feedbackResponse && feedbackResponse.code === 200 && feedbackResponse.data) {
      recentFeedback.value = feedbackResponse.data.rows || []
    }
    if (countResponse && countResponse.code === 200) {
      processingFeedbackCount.value = countResponse.data || 0
    }
  } catch (error) {
    logger.error('获取问题反馈失败:', error)
    recentFeedback.value = []
    processingFeedbackCount.value = 0
  } finally {
    loadingFeedback.value = false
  }
}

// 获取最近公告
const fetchRecentAnnouncements = async (): Promise<void> => {
  loadingAnnouncement.value = true
  try {
    const response = await announcementApi.getAnnouncementsByPage({
      page: 1,
      pageSize: 10,
      status: 'PUBLISHED' // 已发布
    })
    if (response && response.code === 200 && response.data) {
      recentAnnouncements.value = response.data.rows || []
      publishedAnnouncementCount.value = response.data.total || 0
    }
  } catch (error) {
    logger.error('获取公告失败:', error)
    recentAnnouncements.value = []
    publishedAnnouncementCount.value = 0
  } finally {
    loadingAnnouncement.value = false
  }
}

// 格式化公告时间
const formatAnnouncementTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) {
    return '刚刚'
  } else if (diff < 3600000) {
    return `${Math.floor(diff / 60000)}分钟前`
  } else if (diff < 86400000) {
    return `${Math.floor(diff / 3600000)}小时前`
  } else if (diff < 604800000) {
    return `${Math.floor(diff / 86400000)}天前`
  } else {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  }
}

// 获取优先级标签类型
const getPriorityTagType = (priority) => {
  const typeMap = {
    'urgent': 'danger',
    'high': 'warning',
    'normal': 'info',
    'low': 'success'
  }
  return typeMap[priority] || 'info'
}

// 获取优先级文本
const getPriorityText = (priority) => {
  const typeMap = {
    'urgent': '紧急',
    'high': '高',
    'normal': '普通',
    'low': '低'
  }
  return typeMap[priority] || '普通'
}

// 导航到公告详情
const navigateToAnnouncement = (id) => {
  router.push({
    path: '/admin/announcements',
    query: { viewId: id }
  })
}

// 格式化日志时间
const formatLogTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now - date
  
  if (diff < 60000) {
    return '刚刚'
  } else if (diff < 3600000) {
    return `${Math.floor(diff / 60000)}分钟前`
  } else if (diff < 86400000) {
    return `${Math.floor(diff / 3600000)}小时前`
  } else if (diff < 604800000) {
    return `${Math.floor(diff / 86400000)}天前`
  } else {
    return date.toLocaleDateString()
  }
}

// 获取操作类型标签类型
const getOperationTypeTag = (type) => {
  const typeMap = {
    'LOGIN': 'info',
    'ADD': 'success',
    'UPDATE': 'warning',
    'DELETE': 'danger'
  }
  return typeMap[type] || 'info'
}

// 获取操作类型文本
const getOperationTypeText = (type) => {
  const typeMap = {
    'LOGIN': '登录',
    'LOGOUT': '登出',
    'ADD': '新增',
    'INSERT': '新增',
    'UPDATE': '编辑',
    'DELETE': '删除',
    'RESET_PASSWORD': '重置密码',
    'ASSIGN': '分配',
    'BACKUP': '备份',
    'CLEAN': '清理',
    'DOWNLOAD': '下载',
    'RESTORE': '恢复',
    'SELECT': '查询',
    'IMPORT': '导入',
    'SWITCH_ROLE': '切换角色',
    'CREATE': '创建',
    'VERIFY': '验证'
  }
  return typeMap[type] || type
}

const getModuleName = (module) => {
  const nameMap = {
    'USER_MANAGEMENT': '用户管理',
    'STUDENT_MANAGEMENT': '学生管理',
    'TEACHER_MANAGEMENT': '教师管理',
    'ADMIN_MANAGEMENT': '管理员管理',
    'COMPANY_MANAGEMENT': '企业管理',
    'MAJOR_MANAGEMENT': '专业管理',
    'CLASS_MANAGEMENT': '班级管理',
    'DEPARTMENT_MANAGEMENT': '院系管理',
    'INTERNSHIP_MANAGEMENT': '实习管理',
    'APPLICATION_MANAGEMENT': '申请管理',
    'POSITION_MANAGEMENT': '岗位管理',
    'POSITION_CATEGORY_MANAGEMENT': '岗位类别管理',
    'KEYWORD_LIBRARY_MANAGEMENT': '关键词库管理',
    'KEYWORD_LIBRARY': '关键词库',
    'ANNOUNCEMENT_MANAGEMENT': '公告管理',
    'SCORING_RULE': '评分规则',
    'SCORING_RULE_MANAGEMENT': '评分规则管理',
    'PERMISSION': '权限管理',
    'PERMISSION_MANAGEMENT': '权限管理',
    'PROBLEM_FEEDBACK': '问题反馈',
    'LOG_MANAGEMENT': '日志管理',
    'ARCHIVE_MANAGEMENT': '档案管理',
    'TEMPLATE_MANAGEMENT': '模板管理',
    'SYSTEM_SETTINGS': '系统设置',
    'BACKUP': '备份管理',
    'BACKUP_MANAGEMENT': '备份管理',
    'DATA_EXPORT': '数据导出',
    'AI_MODEL': 'AI模型管理',
    'AUTH': '认证管理'
  }
  return nameMap[module] || module
}

const getRoleTag = (role) => {
  const roleMap = {
    'STUDENT': 'primary',
    'TEACHER': 'success',
    'ADMIN': 'danger',
    'COMPANY': 'warning'
  }
  return roleMap[role] || 'info'
}

const getRoleName = (role) => {
  const roleMap = {
    'STUDENT': '学生',
    'TEACHER': '教师',
    'ADMIN': '管理员',
    'COMPANY': '企业',
    'ROLE_ADMIN': '系统管理员'
  }
  return roleMap[role] || role
}

// 格式化反馈时间
const formatFeedbackTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now - date
  
  if (diff < 60000) {
    return '刚刚'
  } else if (diff < 3600000) {
    return `${Math.floor(diff / 60000)}分钟前`
  } else if (diff < 86400000) {
    return `${Math.floor(diff / 3600000)}小时前`
  } else if (diff < 604800000) {
    return `${Math.floor(diff / 86400000)}天前`
  } else {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  }
}

// 获取用户类型标签类型
const getUserTypeTag = (type) => {
  const typeMap = {
    'student': 'primary',
    'teacher': 'success',
    'company': 'warning'
  }
  return typeMap[type] || 'info'
}

// 获取用户类型文本
const getUserTypeText = (type) => {
  const typeMap = {
    'student': '学生',
    'teacher': '教师',
    'company': '企业'
  }
  return typeMap[type] || type
}

// 显示公告发布对话框
const showAnnouncementDialog = async (): Promise<void> => {
  resetAnnouncementForm()
  
  try {
    const response = await MajorService.getMajors()
    if (response.code === 200) {
      majorList.value = response.data || []
    }
  } catch (error) {
    logger.error('获取专业列表失败:', error)
    majorList.value = []
  }
  
  announcementDialogVisible.value = true
  nextTick(() => {
    if (announcementFormRef.value) {
      announcementFormRef.value.clearValidate()
    }
  })
}

const handlePublisherRoleChange = async (role): Promise<void> => {
  if (!role) {
    userList.value = []
    announcementForm.value.publisher = ''
    return
  }

  userListLoading.value = true
  try {
    const response = await announcementApi.getUsersByPublisherRole(role)
    logger.log('用户列表响应:', response)
    // 响应拦截器已经返回了 response.data，所以 response 就是 { code, message, data }
    if (response && response.code === 200 && response.data) {
      userList.value = response.data
      announcementForm.value.publisher = ''
    } else {
      userList.value = []
      announcementForm.value.publisher = ''
      ElMessage.error(response?.msg || '获取用户列表失败')
    }
  } catch (error) {
    logger.error('获取用户列表失败:', error)
    userList.value = []
    announcementForm.value.publisher = ''
    ElMessage.error('获取用户列表失败')
  } finally {
    userListLoading.value = false
  }
}

const handleResourcePublisherRoleChange = async (role): Promise<void> => {
  if (!role) {
    resourceUserList.value = []
    resourceDocumentForm.value.publisher = ''
    return
  }

  resourceUserListLoading.value = true
  try {
    const response = await announcementApi.getUsersByPublisherRole(role)
    logger.log('资源文档用户列表响应:', response)
    // 响应拦截器已经返回了 response.data，所以 response 就是 { code, message, data }
    if (response && response.code === 200 && response.data) {
      resourceUserList.value = response.data
      resourceDocumentForm.value.publisher = ''
    } else {
      resourceUserList.value = []
      resourceDocumentForm.value.publisher = ''
      ElMessage.error(response?.msg || '获取用户列表失败')
    }
  } catch (error) {
    logger.error('获取用户列表失败:', error)
    resourceUserList.value = []
    resourceDocumentForm.value.publisher = ''
    ElMessage.error('获取用户列表失败')
  } finally {
    resourceUserListLoading.value = false
  }
}

// 提交公告
const submitAnnouncement = async (): Promise<void> => {
  if (!announcementFormRef.value) return

  await announcementFormRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        // 构建 targetValue 对象
        let targetValue = null
        let targetValueObj: Record<string, unknown> = {}
        if (announcementForm.value.teacherTypes && announcementForm.value.teacherTypes.length > 0) {
          targetValueObj.teacherTypes = announcementForm.value.teacherTypes
        }
        if (announcementForm.value.majorIds && announcementForm.value.majorIds.length > 0) {
          targetValueObj.majorIds = announcementForm.value.majorIds
        }
        if (Object.keys(targetValueObj).length > 0) {
          targetValue = JSON.stringify(targetValueObj)
        }

        // 如果 publisher 为空，使用当前登录用户的名称
        let publisher = announcementForm.value.publisher
        if (!publisher || publisher.trim() === '') {
          publisher = authStore.user?.name || authStore.user?.username || '系统管理员'
          logger.log('publisher 为空，使用当前登录用户:', publisher)
        }

        // 如果 publisherRole 为空，使用默认值
        let publisherRole = announcementForm.value.publisherRole
        if (!publisherRole || publisherRole.trim() === '') {
          publisherRole = 'ADMIN'
          logger.log('publisherRole 为空，使用默认值:', publisherRole)
        }

        const submitData = {
          title: announcementForm.value.title,
          content: announcementForm.value.content,
          status: announcementForm.value.status,
          publisher: publisher,
          publisherRole: publisherRole,
          priority: announcementForm.value.priority,
          validFrom: announcementForm.value.publishDate,
          validTo: announcementForm.value.expireDate,
          targetType: announcementForm.value.targetType && announcementForm.value.targetType.length > 0 ? announcementForm.value.targetType : ['ALL'],
          targetValue: targetValue || null
        }

        if (announcementForm.value.attachments && announcementForm.value.attachments.length > 0) {
          submitData.attachments = announcementForm.value.attachments.map(file => ({
            name: file.name,
            url: file.url,
            size: file.size,
            type: file.raw ? file.raw.type : (file.response ? file.response.type : '')
          }))
        }

        let response
        if (submitData.attachments && submitData.attachments.length > 0) {
          response = await announcementApi.addAnnouncementWithAttachments(submitData)
        } else {
          response = await announcementApi.addAnnouncement(submitData)
        }

        // 响应拦截器已经返回了 response.data，所以 response 就是 { code, message, data }
        if (response && response.code === 200) {
          ElMessage.success('公告发布成功')
          announcementDialogVisible.value = false
          resetAnnouncementForm()
        } else {
          ElMessage.error(response?.msg || '公告发布失败')
        }
      } catch (error) {
        logger.error('公告发布失败:', error)
        ElMessage.error('公告发布失败')
      } finally {
        submitting.value = false
      }
    }
  })
}

const resetAnnouncementForm = () => {
  announcementForm.value = {
    title: '',
    content: '',
    status: 'DRAFT',
    publisher: '',
    publisherRole: '',
    publishDate: null,
    expireDate: null,
    priority: 'normal',
    targetType: [] as string[],
    teacherTypes: [] as string[],
    majorIds: [] as string[],
    targetValue: null,
    attachments: []
  }
  userList.value = []
}

const handleAnnouncementDialogClose = () => {
  if (announcementFormRef.value) {
    announcementFormRef.value.clearValidate()
  }
  resetAnnouncementForm()
}

// 显示资源文档发布对话框
const showResourceDocumentDialog = () => {
  resetResourceDocumentForm()
  resourceDocumentDialogVisible.value = true
  nextTick(() => {
    if (resourceDocumentFormRef.value) {
      resourceDocumentFormRef.value.clearValidate()
    }
  })
}

// 提交资源文档
const submitResourceDocument = async (): Promise<void> => {
  if (!resourceDocumentFormRef.value) return
  
  await resourceDocumentFormRef.value.validate(async (valid) => {
    if (valid) {
      if (!resourceDocumentForm.value.file) {
        ElMessage.warning('请选择文件')
        return
      }

      resourceSubmitting.value = true
      try {
        const formDataObj = new FormData()
        formDataObj.append('file', resourceDocumentForm.value.file)
        formDataObj.append('title', resourceDocumentForm.value.title)
        formDataObj.append('description', resourceDocumentForm.value.description || '')
        formDataObj.append('publisher', resourceDocumentForm.value.publisher)
        formDataObj.append('publisherRole', resourceDocumentForm.value.publisherRole)
        formDataObj.append('targetType', resourceDocumentForm.value.targetType)
        formDataObj.append('status', resourceDocumentForm.value.status)
        
        const response = await resourceDocumentApi.uploadResourceDocument(formDataObj)
        // 响应拦截器已经返回了 response.data，所以 response 就是 { code, message, data }
        if (response && response.code === 200) {
          ElMessage.success('发布成功')
          resourceDocumentDialogVisible.value = false
          resetResourceDocumentForm()
        } else {
          ElMessage.error(response?.msg || '发布失败')
        }
      } catch (error) {
        logger.error('发布失败:', error)
        ElMessage.error('发布失败')
      } finally {
        resourceSubmitting.value = false
      }
    }
  })
}

const resetResourceDocumentForm = () => {
  if (resourceDocumentFormRef.value) {
    resourceDocumentFormRef.value.resetFields()
  }
  resourceDocumentForm.value = {
    title: '',
    description: '',
    file: null,
    status: 'PUBLISHED',
    publisher: '',
    publisherRole: '',
    targetType: 'ALL'
  }
  resourceFileList.value = []
  resourceUserList.value = []
}

const handleResourceFileChange = (file) => {
  resourceDocumentForm.value.file = file.raw
  resourceFileList.value = [file]
}

const handleResourceFileRemove = () => {
  resourceDocumentForm.value.file = null
  resourceFileList.value = []
}

const handleResourceDocumentDialogClose = () => {
  resetResourceDocumentForm()
}

// 刷新所有数据
const refreshAllData = async (): Promise<void> => {
  loading.value = true
  try {
    await Promise.all([fetchStats(), fetchRecentLogs(), fetchRecentFeedback(), fetchPendingRecall()])
    ElMessage.success('数据刷新成功')
  } catch (error) {
    ElMessage.error('数据刷新失败')
  } finally {
    loading.value = false
  }
}

// 导航到指定页面
const navigateTo = (path) => {
  router.push(path)
}

// 导航到问题反馈详情
const navigateToFeedback = (feedbackId) => {
  router.push({
    path: '/admin/problem-feedback',
    query: { feedbackId: feedbackId }
  })
}

const handleWebSocketMessage = (data) => {
  console.log('管理员端收到WebSocket消息:', data)
  
  if (data.type === 'new_announcement') {
    ElMessage.success({
      message: `新公告：${data.data?.title || '未知标题'}`,
      duration: 5000,
      showClose: true
    })
    fetchRecentAnnouncements()
  } else if (data.type === 'new_feedback') {
    ElMessage.warning({
      message: `新问题反馈：${data.data?.title || '未知标题'}`,
      duration: 5000,
      showClose: true
    })
    fetchRecentFeedback()
    fetchStats()
  }
}

const initWebSocket = () => {
  let token = authStore.token
  
  if (!token) {
    const rolePrefix = 'admin_'
    const role = 'ROLE_ADMIN'
    token = localStorage.getItem(`${rolePrefix}accessToken_${role}`) ||
            localStorage.getItem(`${rolePrefix}token_${role}`) ||
            localStorage.getItem('accessToken')
  }
  
  if (token) {
    console.log('管理员端初始化 WebSocket 连接')
    initAnnouncementWebSocket(token, handleWebSocketMessage)
  } else {
    console.warn('未找到 token，无法初始化 WebSocket')
  }
}

// 页面加载
onMounted(() => {
  getCurrentDate()
  Promise.all([
    fetchStats(),
    fetchRecentLogs(),
    fetchRecentFeedback(),
    fetchRecentAnnouncements()
  ])
  initWebSocket()
})

onUnmounted(() => {
  disconnectAnnouncementWebSocket()
})
</script>

<style scoped>
.dashboard-management-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

/* 页面标题区域 */
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

/* 顶部操作区域 */
.search-card {
  border-radius: 16px;
  border: none;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  margin-bottom: 24px;
  padding: 20px 24px;
}

.dashboard-topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dashboard-user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #606266;
  font-size: 14px;
}

.user-info-text {
  font-weight: 500;
  color: #303133;
}

.date-info {
  color: #909399;
  padding-left: 12px;
  border-left: 1px solid #e0e0e0;
  margin-left: 12px;
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

.action-btn.primary {
  background: linear-gradient(135deg, #52c41a, #409EFF);
  border: none;
}

.action-btn.success {
  background: linear-gradient(135deg, #409EFF, #52c41a);
  border: none;
}

/* 统计卡片网格布局 */
.dashboard-content {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}

.stats-section {
  flex: 0 0 calc(100% - 376px);
}

.stats-cards-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.feedback-section {
  flex: 0 0 360px;
  max-width: 360px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.feedback-card {
  flex: 1;
  min-height: 0;
}

.announcement-card {
  flex: 1;
  min-height: 0;
}

/* 操作日志和反馈区域容器 */
.logs-feedback-container {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}

/* 最近公告区域 */
.announcement-section {
  flex: 0 0 360px;
  max-width: 360px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-card {
  border-radius: 16px;
  border: none;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  cursor: pointer;
  transition: all 0.3s ease;
  overflow: hidden;
  min-height: 140px;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
}

.stat-card.students {
  border-top: 4px solid #409EFF;
}

.stat-card.teachers {
  border-top: 4px solid #52c41a;
}

.stat-card.companies {
  border-top: 4px solid #ff6b00;
}

.stat-card.departments {
  border-top: 4px solid #faad14;
}

.stat-card.departments-majors {
  border-top: 4px solid #faad14;
}

.stat-card.majors {
  border-top: 4px solid #ff4d4f;
}

.stat-card.ai-models {
  border-top: 4px solid #8b5cf6;
}

.stat-card.positions {
  border-top: 4px solid #13c2c2;
}

.stat-content {
  padding: 20px;
  position: relative;
}

.stat-icon-wrapper {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-bottom: 16px;
}

.stat-card.students .stat-icon-wrapper {
  background: rgba(64, 158, 255, 0.1);
  color: #409EFF;
}

.stat-card.teachers .stat-icon-wrapper {
  background: rgba(82, 196, 26, 0.1);
  color: #52c41a;
}

.stat-card.companies .stat-icon-wrapper {
  background: rgba(255, 107, 0, 0.1);
  color: #ff6b00;
}

.stat-card.departments .stat-icon-wrapper {
  background: rgba(250, 173, 20, 0.1);
  color: #faad14;
}

.stat-card.departments-majors .stat-icon-wrapper {
  background: rgba(250, 173, 20, 0.1);
  color: #faad14;
}

.stat-card.majors .stat-icon-wrapper {
  background: rgba(255, 77, 79, 0.1);
  color: #ff4d4f;
}

.stat-card.ai-models .stat-icon-wrapper {
  background: rgba(139, 92, 246, 0.1);
  color: #8b5cf6;
}

.stat-card.ai-models .stat-icon-wrapper.ai-icon {
  background: #8b5cf6;
  color: white;
  font-weight: 700;
}

.stat-card.ai-models .stat-icon-wrapper.ai-icon span {
  font-size: 22px !important;
}

.stat-card.positions .stat-icon-wrapper {
  background: rgba(19, 194, 194, 0.1);
  color: #13c2c2;
}

.stat-info {
  margin-bottom: 16px;
}

.stat-info-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.stat-card.departments-majors .stat-info,
.stat-card.ai-models .stat-info,
.stat-card.positions .stat-info {
  margin-bottom: 0;
  flex-shrink: 0;
  min-width: 80px;
}

.stat-card.departments-majors .stat-info:first-child,
.stat-card.ai-models .stat-info:first-child,
.stat-card.positions .stat-info:first-child {
  margin-right: 0;
}

.stat-card.departments-majors .stat-info:last-child,
.stat-card.ai-models .stat-info:last-child,
.stat-card.positions .stat-info:last-child {
  margin-left: 0;
}

.stat-divider {
  width: 1px;
  height: 40px;
  background: rgba(0, 0, 0, 0.1);
  flex-shrink: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  line-height: 1;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  font-weight: 500;
  margin-bottom: 8px;
}

.stat-action {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  color: #409EFF;
  font-size: 12px;
  font-weight: 500;
}

/* 操作日志卡片 */
.logs-card {
  border-radius: 16px;
  border: none;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  flex: 1;
  min-height: 0;
}

.logs-card :deep(.el-card__header) {
  border-bottom: 1px solid #f0f0f0;
  padding: 16px 20px;
}

.logs-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logs-filter {
  display: flex;
  align-items: center;
}

.logs-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.logs-title .el-icon {
  color: #409EFF;
  font-size: 18px;
}

.view-all-link {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #409EFF;
  cursor: pointer;
  transition: all 0.3s ease;
  padding: 4px 8px;
  border-radius: 4px;
}

.view-all-link:hover {
  background: rgba(64, 158, 255, 0.1);
  color: #66b1ff;
}

.view-all-link .el-icon {
  font-size: 12px;
}

.logs-container {
  padding: 10px 0;
}

.logs-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.log-item {
  display: flex;
  align-items: flex-start;
  padding: 12px;
  border-radius: 8px;
  background: #f8f9fa;
  transition: all 0.3s ease;
}

.log-item:hover {
  background: #f0f7ff;
  transform: translateX(4px);
}

.log-content {
  flex: 1;
  min-width: 0;
}

.log-header {
  display: flex;
  align-items: center;
  margin-bottom: 6px;
  gap: 6px;
  flex-wrap: wrap;
}

.log-operator {
  font-weight: 600;
  color: #303133;
  font-size: 14px;
}

.log-role-tag {
  margin-right: 0;
}

.log-result-tag {
  margin-right: 0;
}

.log-type-tag {
  margin-right: 0;
}

.log-module {
  font-size: 13px;
  color: #606266;
  margin-right: 6px;
}

.log-time {
  font-size: 13px;
  color: #409EFF;
  font-weight: 500;
  background: rgba(64, 158, 255, 0.1);
  padding: 2px 8px;
  border-radius: 4px;
}

.log-detail {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.log-description {
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.log-ip {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #67c23a;
  flex-shrink: 0;
}

.log-ip .el-icon {
  font-size: 12px;
}

.empty-logs {
  padding: 40px 0;
  text-align: center;
}

/* 问题反馈卡片 */
.feedback-card {
  border-radius: 16px;
  border: none;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  height: 100%;
  display: flex;
  flex-direction: column;
}

.feedback-card :deep(.el-card__header) {
  border-bottom: 1px solid #f0f0f0;
  padding: 16px 20px;
}

.feedback-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.feedback-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.feedback-badge {
  margin-left: 4px;
}

.feedback-title .el-icon {
  color: #409EFF;
  font-size: 18px;
}

.feedback-container {
  padding: 8px 0;
  flex: 1;
}

.feedback-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.feedback-item {
  padding: 10px 12px;
  border-radius: 8px;
  background: #f8f9fa;
  transition: all 0.3s ease;
  cursor: pointer;
  min-width: 0;
}

.feedback-item:hover {
  background: #f0f7ff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.feedback-content {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.feedback-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.feedback-user {
  font-weight: 600;
  color: #303133;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.feedback-title-text {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
  max-width: 100%;
}

.feedback-time {
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.empty-feedback {
  padding: 40px 0;
  text-align: center;
}

/* 最近公告卡片 */
.announcement-card {
  border-radius: 16px;
  border: none;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  height: 100%;
  display: flex;
  flex-direction: column;
}

.announcement-card :deep(.el-card__header) {
  border-bottom: 1px solid #f0f0f0;
  padding: 16px 20px;
}

.announcement-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.announcement-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.announcement-badge {
  margin-left: 4px;
}

.announcement-title .el-icon {
  color: #409EFF;
  font-size: 18px;
}

.announcement-container {
  padding: 8px 0;
  flex: 1;
}

.announcement-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.announcement-item {
  padding: 10px 12px;
  border-radius: 8px;
  background: #f8f9fa;
  transition: all 0.3s ease;
  cursor: pointer;
  min-width: 0;
}

.announcement-item:hover {
  background: #f0f7ff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.announcement-content {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.announcement-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.announcement-title-text {
  font-weight: 600;
  color: #303133;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
  flex: 1;
}

.announcement-publisher {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #606266;
}

.announcement-time {
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
}

.empty-announcement {
  padding: 40px 0;
  text-align: center;
}

/* 响应式设计 */
@media screen and (max-width: 768px) {
  .dashboard-management-container {
    padding: 15px;
  }
  
  .dashboard-content {
    flex-direction: column;
  }
  
  .stats-section {
    flex: 0 0 auto;
    width: 100%;
  }
  
  .feedback-section {
    flex: 0 0 auto;
    width: 100%;
  }
  
  .logs-feedback-container {
    flex-direction: column;
  }
  
  .announcement-section {
    flex: 0 0 auto;
    width: 100%;
  }

  .stats-cards-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .stat-card {
    min-height: 120px;
  }

  .feedback-list {
    gap: 8px;
  }

  .feedback-item {
    padding: 8px 10px;
  }

  .feedback-container {
    max-height: 320px;
  }

  .announcement-list {
    gap: 8px;
  }

  .announcement-item {
    padding: 8px 10px;
  }
  
  .announcement-container {
    max-height: 320px;
  }
  
  .dashboard-topbar {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }
  
  .page-header {
    flex-direction: column;
    text-align: center;
    padding: 20px;
  }
  
  .header-illustration {
    margin-top: 15px;
  }
}

@media screen and (max-width: 480px) {
  .stats-cards-grid {
    grid-template-columns: 1fr;
  }
  
  .stat-card {
    min-height: 100px;
  }
  
  .feedback-list {
    grid-template-columns: 1fr;
  }
}

/* 资源文档发布对话框样式 */
.file-upload-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 公告附件上传样式 */
.compact-upload :deep(.el-upload-list) {
  max-height: 200px;
  overflow-y: auto;
}

.compact-upload :deep(.el-upload-list__item) {
  padding: 8px 12px;
  margin-bottom: 8px;
  border-radius: 6px;
  background: #f5f7fa;
  transition: all 0.3s;
}

.compact-upload :deep(.el-upload-list__item:hover) {
  background: #e6f7ff;
}

.compact-upload :deep(.el-upload__tip) {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}
</style>