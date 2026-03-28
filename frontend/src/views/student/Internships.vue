<template>
  <div class="internships-container">

      <!-- 顶部标题区域 - 简化为面试管理风格 -->
      <div class="page-header">
        <h2>实习心得</h2>
        <p>记录和管理您的实习经历</p>
      </div>

    <!-- 概览卡片 - 统一为面试管理卡片样式 -->
    <div class="overview-card">
      <div class="overview-content">
        <div class="overview-left">
          <div class="company-info">
            <div class="company-name">
              <el-icon class="company-icon"><OfficeBuilding /></el-icon>
              <span v-if="hasInternshipRecord">{{ currentInternship.company }}</span>
              <span v-else class="no-record-tip">暂无实习记录</span>
            </div>
            <div class="company-position" v-if="hasInternshipRecord">{{ currentInternship.position }}</div>
          </div>
        </div>
        <div class="overview-right" v-if="hasInternshipRecord">
          <div class="duration-info">
            <el-icon class="duration-icon"><Clock /></el-icon>
            <span class="duration-text">{{ currentInternship.startDate }} {{ currentInternship.endDate }}</span>
            <span class="current-time">{{ currentTime }}</span>
            <span class="current-period" v-if="currentPeriod">第{{ currentPeriod }}期</span>
          </div>
          <div class="progress-section">
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: currentInternship.progress + '%' }"></div>
            </div>
            <span class="progress-text">{{ currentInternship.progress }}%</span>
          </div>
          <el-tooltip :content="submitReason || '提交实习心得'" placement="top" :disabled="canSubmit">
            <button class="submit-log-button" :class="{ disabled: !canSubmit }" @click="submitLog" :disabled="!canSubmit">
              <el-icon class="button-icon"><Edit /></el-icon>
              提交实习心得
            </button>
          </el-tooltip>
        </div>
      </div>
    </div>

    <!-- 标签页区域 - 统一为面试管理筛选标签样式 -->
    <div class="tabs-section">
      <div class="tabs-container">
        <div
          v-for="tab in tabs"
          :key="tab.key"
          :class="['tab-item', { active: activeTab === tab.key }]"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </div>
      </div>
    </div>

    <!-- 内容区域 - 统一为面试管理卡片样式 -->
    <div class="content-section">
      <!-- 进行中日志 -->
      <div v-if="activeTab === 'current'" class="logs-list">
        <div v-for="log in logs" :key="log.id" class="log-card">
          <div class="log-header">
            <div class="log-title">{{ log.title }}</div>
            <div :class="['status-tag', log.statusClass]">
              {{ log.status }}
            </div>
          </div>
          <div class="log-content">
            <div class="log-desc">{{ log.description }}</div>
          </div>
          <div class="log-info-grid">
            <div class="info-item">
              <el-icon class="info-icon"><Star /></el-icon>
              <span>评分: {{ log.scoringDetails ? log.scoringDetails.totalScore : '未评分' }}</span>
            </div>
          </div>
          <div class="log-actions">
            <template v-if="log.statusClass === 'draft' && log.remark !== '0'">
              <button class="action-button edit" @click="editLog(log)">
                <el-icon><Edit /></el-icon>
                编辑
              </button>
              <button class="action-button delete" @click="deleteLog(log)">
                <el-icon><Delete /></el-icon>
                删除
              </button>
            </template>
            <template v-else-if="log.statusClass === 'submitted' || log.statusClass === 'pending'">
              <button class="action-button view" @click="viewLog(log)">
                <el-icon><View /></el-icon>
                查看
              </button>
              <button class="action-button withdraw" @click="withdrawLog(log)" :disabled="withdrawing">
                <el-icon><RefreshLeft /></el-icon>
                撤回
              </button>
            </template>
            <template v-else-if="log.statusClass === 'reviewed'">
              <button class="action-button view" @click="viewLog(log)">
                <el-icon><View /></el-icon>
                查看
              </button>
              <div class="review-badge">
                <el-icon class="review-icon"><ChatDotRound /></el-icon>
                已批阅
              </div>
            </template>
          </div>
        </div>
      </div>

      <!-- 全部心得 -->
      <div v-else-if="activeTab === 'all'" class="logs-list">
        <div v-for="log in allLogs" :key="log.id" class="log-card">
          <div class="log-header">
            <div class="log-title">{{ log.title }}</div>
            <div :class="['status-tag', log.statusClass]">
              {{ log.status }}
            </div>
          </div>
          <div class="log-content">
            <div class="log-desc">{{ log.description }}</div>
          </div>
          <div class="log-info-grid">
            <div class="info-item">
              <el-icon class="info-icon"><Star /></el-icon>
              <span>评分: {{ log.scoringDetails ? log.scoringDetails.totalScore : '未评分' }}</span>
            </div>
          </div>
          <div class="log-actions">
            <template v-if="log.statusClass === 'draft' && log.remark !== '0'">
              <button class="action-button edit" @click="editLog(log)">
                <el-icon><Edit /></el-icon>
                编辑
              </button>
              <button class="action-button delete" @click="deleteLog(log)">
                <el-icon><Delete /></el-icon>
                删除
              </button>
            </template>
            <template v-else-if="log.statusClass === 'submitted' || log.statusClass === 'pending'">
              <button class="action-button view" @click="viewLog(log)">
                <el-icon><View /></el-icon>
                查看
              </button>
              <button class="action-button withdraw" @click="withdrawLog(log)" :disabled="withdrawing">
                <el-icon><RefreshLeft /></el-icon>
                撤回
              </button>
            </template>
            <template v-else-if="log.statusClass === 'reviewed'">
              <button class="action-button view" @click="viewLog(log)">
                <el-icon><View /></el-icon>
                查看详情
              </button>
            </template>
          </div>
        </div>
      </div>

      <!-- 已完成档案 -->
      <div v-else-if="activeTab === 'completed'" class="archives-list">
        <div v-for="archive in archives" :key="archive.id" class="archive-card">
          <div class="archive-header">
            <!-- 实习心得标题 -->
            <div class="archive-title">{{ archive.title || archive.company || '已完成心得' }}</div>
            <div class="archive-date">{{ archive.startDate ? `${archive.startDate} - ${archive.endDate}` : '' }}</div>
            <div class="review-badge">
              <el-icon class="review-icon"><ChatDotRound /></el-icon>
              已批阅
            </div>
          </div>
          <div class="archive-content">
            <div class="archive-desc">{{ archive.description || archive.position || '' }}</div>
            <div class="archive-info-grid">
              <div class="info-item">
                <el-icon class="info-icon"><Calendar /></el-icon>
                <span>{{ archive.duration ? `实习时长: ${archive.duration}` : `提交时间: ${archive.submitTime || ''}` }}</span>
              </div>
              <div class="info-item">
                <el-icon class="info-icon"><Star /></el-icon>
                <span>评分: {{ archive.scoringDetails?.totalScore || archive.rating || '未评分' }}</span>
              </div>
            </div>
          </div>
          <div class="archive-actions">
            <button class="action-button view" @click="viewArchive(archive)">
              <el-icon><View /></el-icon>
              查看详情
            </button>
          </div>
        </div>
      </div>

      <!-- 空状态显示 -->
      <div
        v-if="activeTab === 'all' && allLogs.length === 0"
        class="empty-state"
      >
        <el-icon class="empty-icon"><Document /></el-icon>
        <div class="empty-text">暂无实习心得</div>
      </div>

      <div
        v-if="activeTab === 'current' && logs.length === 0"
        class="empty-state"
      >
        <el-icon class="empty-icon"><Document /></el-icon>
        <div class="empty-text">暂无实习心得</div>
      </div>

      <div
        v-if="activeTab === 'completed' && archives.length === 0"
        class="empty-state"
      >
        <el-icon class="empty-icon"><FolderOpened /></el-icon>
        <div class="empty-text">暂无实习档案</div>
      </div>


    </div>

    <!-- 实习心得对话框 -->
    <el-dialog
      v-model="showLogDialog"
      :title="isEditMode ? '编辑实习心得' : '提交实习心得'"
      width="650px"
      class="detail-dialog"
      :append-to-body="true"
      :lock-scroll="true"
      modal-class="global-modal"
    >
      <div v-if="currentInternship" class="detail-content">
        <div class="detail-header">
          <div class="detail-info">
            <div class="detail-title">
              <el-icon class="detail-company-icon"><OfficeBuilding /></el-icon>
              <span>{{ currentInternship.company }}</span>
            </div>
            <div class="detail-position">{{ currentInternship.position }}</div>
          </div>
          <div class="week-badge">
            <el-icon class="week-icon"><Calendar /></el-icon>
            {{ logForm.title }}
          </div>
        </div>

        <div class="detail-sections">
          <el-tabs v-model="reflectionActiveTab" class="submission-tabs">
            <el-tab-pane label="直接输入" name="text">
              <div class="detail-section">
                <div class="section-header">
                  <el-icon class="section-icon"><EditPen /></el-icon>
                  <span class="section-label">实习心得</span>
                </div>
                <el-input
                  v-model="logForm.content"
                  type="textarea"
                  :rows="8"
                  placeholder="请输入您的实习心得，包括工作感悟、学习收获、遇到的困难与解决方法、对未来的规划等..."
                  class="section-textarea"
                />
              </div>
            </el-tab-pane>
            <el-tab-pane label="上传文件" name="file">
              <div class="upload-panel">
                <el-upload
                  class="word-upload"
                  drag
                  action="#"
                  :auto-upload="false"
                  :on-change="handleFileChange"
                  :on-remove="handleFileRemove"
                  :limit="1"
                  accept=".doc,.docx"
                >
                  <el-icon class="upload-icon"><UploadFilled /></el-icon>
                  <div class="upload-text">
                    <span class="upload-title">拖拽Word文档到此处</span>
                    <span class="upload-hint">或点击选择文件</span>
                  </div>
                  <template #tip>
                    <div class="upload-tip">
                      支持 .doc, .docx 格式，单个文件不超过10MB
                    </div>
                  </template>
                </el-upload>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>

        <div class="dialog-actions">
          <el-button @click="showLogDialog = false" class="cancel-button">取消</el-button>
          <el-button type="primary" @click="submitLogForm" class="submit-button" :loading="submitting">
            <el-icon><Check /></el-icon>
            提交实习心得
          </el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 查看心得对话框 - 统一为面试管理对话框样式 -->
    <el-dialog
      v-model="showViewDialog"
      title="查看心得"
      width="650px"
      class="detail-dialog"
      :append-to-body="true"
      :lock-scroll="true"
      modal-class="global-modal"
    >
      <div v-if="currentLog" class="detail-content">
        <div class="detail-header">
          <div class="detail-info">
            <div class="detail-title">{{ currentLog.title }}</div>
            <div class="detail-position">
              <el-icon class="detail-company-icon"><OfficeBuilding /></el-icon>
              <span>{{ currentInternship.company }} · {{ currentInternship.position }}</span>
            </div>
          </div>
          <div :class="['detail-status', currentLog.statusClass]">
            {{ currentLog.status }}
          </div>
        </div>

        <div class="detail-sections">
          <div class="detail-section">
            <div class="section-header">
              <el-icon class="section-icon"><Calendar /></el-icon>
              <span class="section-label">基本信息</span>
            </div>
            <div class="info-grid">
              <div class="info-card">
                <div class="info-card-icon">
                  <el-icon><Clock /></el-icon>
                </div>
                <div class="info-card-content">
                  <div class="info-card-label">提交时间</div>
                  <div class="info-card-value">{{ currentLog.submitTime || '14:30' }}</div>
                </div>
              </div>
              <div class="info-card">
                <div class="info-card-icon">
                  <el-icon><Document /></el-icon>
                </div>
                <div class="info-card-content">
                  <div class="info-card-label">状态</div>
                  <div class="info-card-value">{{ currentLog.status }}</div>
                </div>
              </div>
            </div>
          </div>

          <div class="detail-section">
            <div class="section-header">
              <el-icon class="section-icon"><Document /></el-icon>
              <span class="section-label">工作内容</span>
            </div>
            <div class="notice-list">
              <div class="notice-item">
                {{ currentLog.description }}
              </div>
            </div>
          </div>

          <div v-if="currentLog.problems" class="detail-section">
            <div class="section-header">
              <el-icon class="section-icon"><Warning /></el-icon>
              <span class="section-label">遇到的问题</span>
            </div>
            <div class="notice-list">
              <div class="notice-item">
                {{ currentLog.problems }}
              </div>
            </div>
          </div>

          <div v-if="currentLog.plan" class="detail-section">
            <div class="section-header">
              <el-icon class="section-icon"><Promotion /></el-icon>
              <span class="section-label">下周计划</span>
            </div>
            <div class="notice-list">
              <div class="notice-item">
                {{ currentLog.plan }}
              </div>
            </div>
          </div>

          <div v-if="currentLog.scoringDetails || currentLog.review" class="detail-section">
            <div class="section-header">
              <span class="section-label">评语</span>
              <el-tag v-if="currentLog.scoringDetails" :type="getRatingType(currentLog.scoringDetails.rating)" size="small" class="rating-tag">
                {{ currentLog.scoringDetails.rating }}
              </el-tag>
            </div>
            <div v-if="currentLog.scoringDetails" class="scoring-summary">
              <div class="scoring-info">
                <div class="scoring-label">总分</div>
                <div class="scoring-value">{{ currentLog.scoringDetails.totalScore }}分</div>
              </div>
            </div>
            <div v-if="currentLog.review" class="notice-list">
              <div class="notice-item review-item">
                <span>{{ currentLog.review }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="dialog-actions">
          <el-button @click="showViewDialog = false" class="cancel-button">关闭</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 实习档案对话框 - 统一为面试管理对话框样式 -->
    <el-dialog
      v-model="showArchiveDialog"
      title="实习档案"
      width="650px"
      class="detail-dialog"
      :append-to-body="true"
      :lock-scroll="true"
      modal-class="global-modal"
    >
      <div v-if="currentArchive" class="detail-content">
        <div class="detail-header">
          <div class="detail-info">
            <div class="detail-title">
              <el-icon class="detail-company-icon"><OfficeBuilding /></el-icon>
              <span>{{ currentArchive.company }}</span>
            </div>
            <div class="detail-position">{{ currentArchive.position }}</div>
          </div>
          <div class="archive-badge">
            <el-icon class="badge-icon"><DocumentChecked /></el-icon>
            已完成
          </div>
        </div>

        <div class="detail-sections">
          <div class="detail-section">
            <div class="section-header">
              <el-icon class="section-icon"><Calendar /></el-icon>
              <span class="section-label">基本信息</span>
            </div>
            <div class="info-grid">
              <div class="info-card">
                <div class="info-card-icon">
                  <el-icon><OfficeBuilding /></el-icon>
                </div>
                <div class="info-card-content">
                  <div class="info-card-label">实习公司</div>
                  <div class="info-card-value">{{ currentArchive.company }}</div>
                </div>
              </div>
              <div class="info-card">
                <div class="info-card-icon">
                  <el-icon><User /></el-icon>
                </div>
                <div class="info-card-content">
                  <div class="info-card-label">实习岗位</div>
                  <div class="info-card-value">{{ currentArchive.position }}</div>
                </div>
              </div>
              <div class="info-card">
                <div class="info-card-icon">
                  <el-icon><Calendar /></el-icon>
                </div>
                <div class="info-card-content">
                  <div class="info-card-label">实习时间</div>
                  <div class="info-card-value">{{ currentArchive.startDate }} 至 {{ currentArchive.endDate }}</div>
                </div>
              </div>
              <div class="info-card">
                <div class="info-card-icon">
                  <el-icon><Clock /></el-icon>
                </div>
                <div class="info-card-content">
                  <div class="info-card-label">实习时长</div>
                  <div class="info-card-value">{{ currentArchive.duration }}</div>
                </div>
              </div>
            </div>
          </div>

          <div class="detail-section">
            <div class="section-header">
              <el-icon class="section-icon"><TrendCharts /></el-icon>
              <span class="section-label">实习评价</span>
            </div>
            <div class="evaluation-grid">
              <div class="evaluation-item">
                <div class="evaluation-label">工作态度</div>
                <el-rate v-model="currentArchive.attitude" disabled />
              </div>
              <div class="evaluation-item">
                <div class="evaluation-label">工作能力</div>
                <el-rate v-model="currentArchive.ability" disabled />
              </div>
              <div class="evaluation-item">
                <div class="evaluation-label">团队协作</div>
                <el-rate v-model="currentArchive.teamwork" disabled />
              </div>
              <div class="evaluation-item">
                <div class="evaluation-label">创新能力</div>
                <el-rate v-model="currentArchive.innovation" disabled />
              </div>
            </div>
          </div>

          <div class="detail-section">
            <div class="section-header">
              <el-icon class="section-icon"><Star /></el-icon>
              <span class="section-label">评分详情</span>
              <el-tag v-if="currentArchive.scoringDetails" :type="getRatingType(currentArchive.scoringDetails.rating)" size="small" class="rating-tag">
                {{ currentArchive.scoringDetails.rating }}
              </el-tag>
            </div>
            <div v-if="currentArchive.scoringDetails" class="scoring-grid">
              <div class="scoring-item">
                <div class="scoring-info">
                  <div class="scoring-label">实习态度 (20%)</div>
                  <div class="scoring-value">{{ currentArchive.scoringDetails.internshipAttitude }}分</div>
                </div>
                <div class="scoring-bar-container">
                  <div class="scoring-bar" :style="{ width: currentArchive.scoringDetails.internshipAttitude + '%' }"></div>
                </div>
              </div>
              <div class="scoring-item">
                <div class="scoring-info">
                  <div class="scoring-label">实习表现 (40%)</div>
                  <div class="scoring-value">{{ currentArchive.scoringDetails.internshipPerformance }}分</div>
                </div>
                <div class="scoring-bar-container">
                  <div class="scoring-bar" :style="{ width: currentArchive.scoringDetails.internshipPerformance + '%' }"></div>
                </div>
              </div>
              <div class="scoring-item">
                <div class="scoring-info">
                  <div class="scoring-label">实习心得 (40%)</div>
                  <div class="scoring-value">{{ currentArchive.scoringDetails.internshipReflection }}分</div>
                </div>
                <div class="scoring-bar-container">
                  <div class="scoring-bar" :style="{ width: currentArchive.scoringDetails.internshipReflection + '%' }"></div>
                </div>
              </div>
              <div class="scoring-item total">
                <div class="scoring-info">
                  <div class="scoring-label total">总分</div>
                  <div class="scoring-value total">{{ currentArchive.scoringDetails.totalScore }}分</div>
                </div>
                <div class="scoring-bar-container">
                  <div class="scoring-bar total" :style="{ width: currentArchive.scoringDetails.totalScore + '%' }"></div>
                </div>
              </div>
            </div>
          </div>

          <div class="detail-section">
            <div class="section-header">
              <el-icon class="section-icon"><ChatDotRound /></el-icon>
              <span class="section-label">导师评语</span>
            </div>
            <div class="notice-list">
              <div class="notice-item review-item">
                <el-icon class="notice-icon"><CircleCheck /></el-icon>
                <span>{{ currentArchive.review }}</span>
              </div>
            </div>
          </div>

          <div class="detail-section">
            <div class="section-header">
              <el-icon class="section-icon"><Document /></el-icon>
              <span class="section-label">实习总结</span>
            </div>
            <div class="notice-list">
              <div class="notice-item">
                {{ currentArchive.summary }}
              </div>
            </div>
          </div>

          <div class="detail-section">
            <div class="section-header">
              <el-icon class="section-icon"><FolderOpened /></el-icon>
              <span class="section-label">工作日志</span>
            </div>
            <div class="logs-summary">
              <div class="log-stat">
                <div class="stat-number">{{ currentArchive.logCount }}</div>
                <div class="stat-label">提交日志</div>
              </div>
              <div class="log-stat">
                <div class="stat-number">{{ currentArchive.weekCount }}</div>
                <div class="stat-label">实习周数</div>
              </div>
              <div class="log-stat">
                <div class="stat-number">{{ currentArchive.reviewedCount }}</div>
                <div class="stat-label">已批阅</div>
              </div>
            </div>
          </div>
        </div>

        <div class="dialog-actions">
          <el-button @click="showArchiveDialog = false" class="cancel-button">关闭</el-button>
          <el-button
            v-if="currentArchive && currentArchive.certificateClass === 'available'"
            type="primary"
            @click="downloadCertificate(currentArchive)"
            class="submit-button"
          >
            <el-icon><Download /></el-icon>
            下载实习证明
          </el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Clock,
  Edit,
  Delete,
  View,
  ChatDotRound,
  Calendar,
  DocumentChecked,
  Document,
  FolderOpened,
  Plus,
  Download,
  UploadFilled,
  OfficeBuilding,
  EditPen,
  Warning,
  Promotion,
  Paperclip,
  Close,
  Check,
  DocumentCopy,
  User,
  TrendCharts,
  CircleCheck,
  Star,
  Briefcase,
  RefreshLeft
} from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { useAuthStore } from '@/store/auth'
import { onAIAnalysisResult, offAIAnalysisResult } from '@/utils/websocket'

const authStore = useAuthStore()

// 从 auth store 获取学生 ID
const getStudentId = () => {
  return authStore.user?.id
}

const router = useRouter()
const activeTab = ref('all')
const progressColor = '#67C23A'
const showLogDialog = ref(false)
const showViewDialog = ref(false)
const showArchiveDialog = ref(false)
const isEditMode = ref(false)
const currentLog = ref(null)
const currentArchive = ref(null)
const reflectionActiveTab = ref('text')
const selectedFile = ref<File | null>(null)
const submitting = ref(false)
const withdrawing = ref(false)

// 当前阶段提交状态
const currentPeriod = ref<number | null>(null)
const canSubmit = ref(true)
const submitReason = ref('')

// 当前时间
const currentTime = ref('')
let timeTimer: ReturnType<typeof setInterval> | null = null

// 处理WebSocket推送的AI分析结果
const handleAIAnalysisResult = async (data: any) => {
  console.log('[Internships] 收到AI分析结果WebSocket消息:', data)

  if (data.success) {
    if (data.isNotReflection) {
      // 内容被判定为不是实习心得
      ElMessage.warning('您提交的内容不是实习心得，已被退回草稿，请修改后重新提交')
      // 刷新整个列表，确保状态同步
      await fetchReflections()
    } else {
      // AI分析成功完成
      await fetchReflections()
    }
  } else {
    // AI分析失败
    ElMessage.error(data.message || 'AI分析失败，请稍后重试')
  }

  await fetchPeriodStatus()
}

// 更新时间
const updateTime = () => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const hours = String(now.getHours()).padStart(2, '0')
  const minutes = String(now.getMinutes()).padStart(2, '0')
  const seconds = String(now.getSeconds()).padStart(2, '0')
  currentTime.value = `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

const logForm = ref({
  title: "",
  date: "",
  week: 1,
  content: "",
  problems: "",
  plan: "",
  files: [],
});

const currentInternship = ref({
  company: "",
  position: "",
  startDate: "",
  endDate: "",
  progress: 0,
})

const hasInternshipRecord = ref(false)
const currentRecordId = ref(null)

const tabs = [
  { key: "all", label: "全部" },
  { key: "current", label: "进行中" },
  { key: "completed", label: "已完成" },
];

const logs = ref([])
const archives = ref([])
const allLogs = ref([])

const fetchInternshipRecord = async () => {
  try {
    const response = await request.get(`/student/internship-record`)
    if (response.code === 200) {
      const record = response.data
      // status=5（已中断）时视为无实习记录，不显示公司信息和心得提交
      if (record && record.status !== 5) {
        hasInternshipRecord.value = true
        currentRecordId.value = record.id
        currentInternship.value = {
          company: record.companyName || '',
          position: record.positionName || '',
          startDate: record.internshipStartDate || '',
          endDate: record.internshipEndDate || '',
          progress: record.progress || 0
        }
      }
    }
  } catch (error) {
    console.error('获取实习记录失败:', error)
    // 使用默认数据
  }
}

const fetchLogs = async () => {
  try {
    // 如果有recordId则按recordId获取，否则获取该学生的所有周志
    const params = currentRecordId.value ? { recordId: currentRecordId.value } : {}
    const response = await request.get(`/student/weekly-log/list`, { params })

    if (response.code === 200 && response.data) {
      const logList = Array.isArray(response.data) ? response.data : []
      logs.value = logList.map(item => ({
        id: item.id,
        date: item.startDate || item.weekDate || '',
        title: item.weekNumber ? `第${item.weekNumber}周工作总结` : '工作总结',
        description: item.workContent || '',
        status: item.status === 'draft' ? '草稿' : item.status === 'submitted' ? '已提交' : '已批阅',
        statusClass: item.status === 'draft' ? 'draft' : item.status === 'submitted' ? 'submitted' : 'reviewed',
        review: item.teacherFeedback || '',
        problems: item.problems || '',
        plan: item.nextPlan || '',
        files: [],
        submitTime: item.submitTime || '',
        fileCount: 0,
        scoringDetails: item.score ? {
          totalScore: item.score || 0,
          rating: item.score >= 90 ? '优秀' : item.score >= 80 ? '良好' : item.score >= 60 ? '及格' : '不及格'
        } : null
      }))
    }
  } catch (error) {
    console.error('获取周日志列表失败:', error)
  }
}

// 获取实习心得列表（补充周志列表的数据）
const fetchReflections = async () => {
  try {
    const response = await request.get('/student/internship-reflection/list')
    if (response.code === 200 && response.data) {
      const reflectionList = Array.isArray(response.data) ? response.data : []
      // 将实习心得合并到logs列表
      // remark字段用于标记教师是否已评分： "1" = 已评分, "0" = 未评分
      const reflectionLogs = reflectionList.map((item, index) => ({
        id: item.id,
        date: item.submitTime ? new Date(item.submitTime) : new Date(),
        // 标题格式：第X期实习心得（优先使用periodNumber，其次使用后端返回的title，最后使用序号）
        title: item.title || (item.periodNumber ? `第${item.periodNumber}期实习心得` : `第${index + 1}期实习心得`),
        description: item.content || '',
        // statusClass: 基于教师是否已评分和撤回状态
        // remark === "0" 草稿（撤回状态）
        // remark === "1" 已批阅（教师评分完成）
        // 其他 待批阅（AI分析中）
        statusClass: item.remark === "0" ? 'draft' : (item.remark === "1" ? 'reviewed' : 'pending'),
        // status文本显示
        status: item.remark === "0" ? '草稿' : (item.remark === "1" ? '已批阅' : '待批阅'),
        review: item.teacherComment || item.aiAnalysis || '',
        submitTime: item.submitTime || '',
        scoringDetails: item.totalScore ? {
          totalScore: item.totalScore,
          rating: item.totalScore >= 90 ? '优秀' : item.totalScore >= 80 ? '良好' : item.totalScore >= 70 ? '中等' : item.totalScore >= 60 ? '及格' : '不及格'
        } : null
      }))

      // 只显示实习心得，不再保留周志
      const allNewLogs = reflectionLogs

      // 按日期排序，最新的在前
      allNewLogs.sort((a, b) => new Date(b.date) - new Date(a.date))

      // 分别设置logs和archives
      // logs: 进行中（待批阅、已提交等未完成状态）
      // archives: 已完成（已批阅状态）
      const pendingLogs = allNewLogs.filter(log => log.statusClass !== 'reviewed')
      const reviewedLogs = allNewLogs.filter(log => log.statusClass === 'reviewed')

      logs.value = pendingLogs
      archives.value = reviewedLogs
      allLogs.value = allNewLogs
    }
  } catch (error) {
    console.error('获取实习心得列表失败:', error)
  }
}

// 获取当前阶段提交状态
const fetchPeriodStatus = async () => {
  try {
    // 先等待实习记录加载完成，避免竞态条件
    await fetchInternshipRecord()

    const response = await request.get('/student/internship-reflection/current-period-status')
    if (response.code === 200 && response.data) {
      currentPeriod.value = response.data.currentPeriod
      canSubmit.value = response.data.canSubmit
      // 优先使用后端返回的提交原因
      if (response.data.submitReason) {
        submitReason.value = response.data.submitReason
      } else if (!hasInternshipRecord.value) {
        canSubmit.value = false
        submitReason.value = '暂无实习记录，无法提交实习心得'
      } else if (!response.data.canSubmit && response.data.hasSubmitted) {
        submitReason.value = `第${response.data.currentPeriod}期实习心得已提交，请等待下一阶段再提交`
      } else if (!response.data.currentPeriod) {
        submitReason.value = '当前不在实习期间内，无法提交实习心得'
      } else {
        submitReason.value = ''
      }
    }
  } catch (error) {
    console.error('获取阶段状态失败:', error)
  }
}

const submitLog = () => {
  if (!canSubmit.value) {
    ElMessage.warning(submitReason.value || '当前阶段不能提交实习心得')
    return
  }
  isEditMode.value = false;
  // 实习心得提交，使用实习心得表单结构
  logForm.value = {
    title: `第${currentPeriod.value}期实习心得`,
    date: new Date(),
    content: "",
    taskId: null, // 如果有自动生成的任务ID，则填充
  };
  showLogDialog.value = true;
};

const editLog = (log) => {
  isEditMode.value = true;
  currentLog.value = log;
  logForm.value = {
    title: log.title,
    date: log.date,
    content: log.description,
    problems: "",
    plan: "",
    files: [],
    taskId: log.id || null,  // 设置taskId用于更新现有心得
  };
  showLogDialog.value = true;
};

const deleteLog = (log) => {
  ElMessageBox.confirm(`确认删除日志"${log.title}"？`, "删除确认", {
    confirmButtonText: "确认删除",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      const index = logs.value.findIndex((item) => item.id === log.id);
      if (index > -1) {
        logs.value.splice(index, 1);
        ElMessage.success("删除成功");
      }
    })
    .catch(() => {
      ElMessage.info("已取消删除");
    });
};

const viewLog = (log) => {
  currentLog.value = log;
  showViewDialog.value = true;
  // 评语和评分已在fetchReflections时通过InternshipEvaluation获取并设置到log.review中
  // 无需再次请求API，避免StudentReflectionEvaluation表无数据导致覆盖
};

const saveLogAsDraft = async () => {
  if (!logForm.value.title) {
    ElMessage.warning("请输入日志标题");
    return;
  }

  try {
    const logData = {
      weekNumber: logs.value.length + 1,
      weekDate: formatDate(logForm.value.date),
      workContent: logForm.value.content,
      problems: logForm.value.problems,
      nextWeekPlan: logForm.value.plan,
      status: 0
    }

    const response = await request.post(`/student/weekly-log/save`, logData)
    if (response.code === 200) {
      ElMessage.success("保存草稿成功");
      await fetchLogs()
      showLogDialog.value = false
    } else {
      ElMessage.error(response.data.message || '保存失败，请稍后重试')
    }
  } catch (error) {
    console.error('保存日志失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  }
}

// 处理文件选择 - el-upload的on-change回调
const handleFileChange = (uploadFile: any, uploadFileList: any[]) => {
  console.log('文件选择回调:', uploadFile, '文件列表:', uploadFileList)
  // el-upload的file对象的raw属性指向原生File
  // 优先从uploadFile.raw获取，如果失败则尝试从fileList中获取
  if (uploadFile && typeof uploadFile === 'object') {
    // 尝试直接从uploadFile.raw获取
    if (uploadFile.raw) {
      selectedFile.value = uploadFile.raw
      console.log('文件已选择(从raw):', uploadFile.name)
    }
    // 否则从fileList的最新文件获取
    else if (uploadFileList && uploadFileList.length > 0) {
      const latestFile = uploadFileList[uploadFileList.length - 1]
      if (latestFile && latestFile.raw) {
        selectedFile.value = latestFile.raw
        console.log('文件已选择(从list):', latestFile.name)
      } else if (latestFile && latestFile.file) {
        selectedFile.value = latestFile.file
        console.log('文件已选择(从file):', latestFile.name)
      }
    }
  }
  if (!selectedFile.value) {
    console.warn('未能获取到文件对象，uploadFile:', uploadFile, 'uploadFileList:', uploadFileList)
  }
}

// 处理文件移除
const handleFileRemove = () => {
  selectedFile.value = null
}

const submitLogForm = async () => {
  // 文件上传模式
  if (reflectionActiveTab.value === 'file') {
    if (!selectedFile.value) {
      ElMessage.warning('请选择要上传的文件')
      return
    }

    submitting.value = true
    try {
      const formData = new FormData()
      formData.append('file', selectedFile.value)

      // 如果是编辑模式（撤回后重新提交），传递taskId
      if (isEditMode.value && logForm.value.taskId) {
        formData.append('taskId', logForm.value.taskId)
      }

      const response = await request({
        url: '/student/internship-reflection/upload',
        method: 'post',
        data: formData,
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })

      if (response && response.code === 200) {
        ElMessage.success('提交成功')
        selectedFile.value = null
        showLogDialog.value = false
        fetchReflections()  // 刷新实习心得列表
        fetchPeriodStatus()  // 刷新阶段状态
      } else {
        ElMessage.error(response?.message || '提交失败')
      }
    } catch (error) {
      console.error('提交失败:', error)
      ElMessage.error('提交失败，不符合实习心得规范')
    } finally {
      submitting.value = false
    }
    return
  }

  // 文本输入模式
  if (!logForm.value.content) {
    ElMessage.warning("请输入实习心得内容");
    return;
  }

  try {
    // 调用实习心得提交 API
    const submitData = {
      content: logForm.value.content,
      taskId: logForm.value.taskId
    };

    const response = await request.post(`/student/internship-reflection/submit`, submitData);
    if (response.code === 200) {
      ElMessage.success("提交成功");
      showLogDialog.value = false;
      // 刷新数据
      fetchReflections();  // 刷新实习心得列表
      fetchPeriodStatus();  // 刷新阶段状态
    } else {
      ElMessage.error(response.message || '提交失败，不符合实习心得规范');
    }
  } catch (error) {
    console.error('提交实习心得失败:', error);
    ElMessage.error('网络错误，不符合实习心得规范');
  }
}

const formatDate = (date) => {
  if (!date) return "";
  const d = new Date(date);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
};

const viewArchive = (archive) => {
  // 如果archive有title字段，说明是实习心得，使用viewLog对话框显示
  if (archive.title) {
    viewLog(archive)
  } else {
    currentArchive.value = archive
    showArchiveDialog.value = true
  }
}

const downloadCertificate = (archive) => {
  ElMessage.success(`下载证明：${archive.company}`);
};

const getRatingType = (rating) => {
  switch (rating) {
    case '优秀':
      return 'success';
    case '良好':
      return 'warning';
    case '中等':
      return 'info';
    case '不及格':
      return 'danger';
    default:
      return 'default';
  }
};

const withdrawLog = (log: any) => {
  ElMessageBox.confirm(`确认撤回心得"${log.title}"？撤回后可重新编辑提交。`, "撤回确认", {
    confirmButtonText: "确认撤回",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(async () => {
      if (withdrawing.value) return  // 防止重复点击
      withdrawing.value = true
      try {
        const response = await request.put(`/student/internship-reflection/withdraw/${log.id}`)
        if (response.code === 200) {
          ElMessage.success("撤回成功")
          // 如果撤回的是当前正在查看的日志，清空缓存
          if (currentLog.value && currentLog.value.id === log.id) {
            currentLog.value = null
          }
          fetchReflections()
        } else {
          ElMessage.error(response.message || '撤回失败')
        }
      } catch (error) {
        console.error('撤回失败:', error)
        ElMessage.error('撤回失败，请稍后重试')
      } finally {
        withdrawing.value = false
      }
    })
    .catch(() => {
      ElMessage.info("已取消撤回")
    })
}

onMounted(() => {
  // 并行加载心得列表和周期状态，提升加载速度
  Promise.all([
    fetchReflections(),
    fetchPeriodStatus()
  ])
  updateTime()
  // 时钟更新改为每分钟，减少不必要的渲染（秒级更新对用户无意义）
  timeTimer = setInterval(updateTime, 60000)

  // 注册AI分析结果监听器
  onAIAnalysisResult(handleAIAnalysisResult)
})

onUnmounted(() => {
  if (timeTimer) {
    clearInterval(timeTimer)
  }
  // 移除AI分析结果监听器
  offAIAnalysisResult(handleAIAnalysisResult)
})
</script>

<style scoped>
/* 统一容器样式 - 匹配面试管理风格 */
.internships-container {
  width: 100%;
  min-height: auto;
  background: #f1f5f9;
  overflow-y: auto;
}

/* 页面标题区域 - 统一为蓝绿色渐变背景 */
.page-header {
  margin-bottom: 24px;
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  border-radius: 12px;
  padding: 24px 32px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  position: relative;
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

.page-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: white;
  margin: 0 0 8px 0;
  position: relative;
  z-index: 1;
}

.page-header p {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
  margin: 0;
  position: relative;
  z-index: 1;
}

/* 卡片通用样式 - 统一为面试管理卡片样式 */
.overview-card,
.tabs-section,
.content-section {
  background: white;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

/* 概览内容样式 */
.overview-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}

.overview-left {
  flex: 1;
}

.company-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.company-name {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 8px;
}

.company-name .company-icon {
  font-size: 24px;
  color: #1890ff;
  flex-shrink: 0;
  filter: drop-shadow(0 2px 4px rgba(24, 144, 255, 0.4));
  background: linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%);
  padding: 4px;
  border-radius: 6px;
}

.company-name span {
  flex: 1;
}

.company-name .no-record-tip {
  color: #999;
  font-weight: normal;
  font-size: 16px;
}

.company-position {
  font-size: 14px;
  color: #64748b;
}

.overview-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.duration-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #64748b;
  flex-wrap: nowrap;
}

.duration-icon {
  font-size: 14px;
  color: #94a3b8;
}

.duration-text {
  color: #64748b;
}

.current-time {
  color: #409EFF;
  font-size: 13px;
  font-weight: 500;
  padding: 2px 8px;
  background: #f0f7ff;
  border-radius: 4px;
  margin-left: 8px;
}

.current-period {
  color: #67C23A;
  font-size: 13px;
  font-weight: 600;
  padding: 2px 10px;
  background: #f0fdf4;
  border-radius: 4px;
  margin-left: 6px;
  border: 1px solid #b7eb8f;
}

.progress-section {
  display: flex;
  align-items: center;
  gap: 10px;
}

.progress-bar {
  width: 120px;
  height: 6px;
  background: #f1f5f9;
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #67c23a 0%, #409eff 100%);
  border-radius: 3px;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
  min-width: 40px;
}

.submit-log-button {
  background: #409EFF;
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.3s ease;
  width: auto;
  min-width: 140px;
  box-sizing: border-box;
}

.submit-log-button:hover {
  background: #337ecc;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

.submit-log-button.disabled {
  background: #c0c4cc;
  cursor: not-allowed;
  opacity: 0.7;
}

.submit-log-button.disabled:hover {
  background: #c0c4cc;
  transform: none;
  box-shadow: none;
}

.submit-log-button.confirm-button {
  background: #67C23A;
}

.submit-log-button.confirm-button:hover {
  background: #5daf34;
  box-shadow: 0 4px 12px rgba(103, 194, 58, 0.3);
}

.button-icon {
  font-size: 14px;
}

/* 标签页样式 - 统一为面试管理筛选标签样式 */
.tabs-container {
  display: flex;
  gap: 8px;
  width: fit-content;
}

.tab-item {
  padding: 8px 20px;
  border-radius: 8px;
  font-size: 14px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.3s ease;
  background: #f8fafc;
  border: 2px solid transparent;
}

.tab-item:hover {
  background: #f0f9ff;
  color: #409EFF;
}

.tab-item.active {
  background: #409EFF;
  color: white;
  border-color: #409EFF;
}

/* 内容列表样式 */
.logs-list,
.archives-list {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  max-height: calc(100vh - 560px);
  overflow-y: auto;
  padding-right: 4px;
}

.logs-list .section-header,
.archives-list .section-header {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.08) 0%, rgba(64, 158, 255, 0.03) 100%);
  border: 1px solid rgba(64, 158, 255, 0.15);
  border-radius: 10px;
  margin-bottom: 4px;
}

.section-period {
  font-size: 15px;
  font-weight: 600;
  color: #409eff;
}

.section-tip {
  font-size: 12px;
  color: #909399;
  padding: 2px 8px;
  background: rgba(64, 158, 255, 0.1);
  border-radius: 4px;
}

.log-card,
.archive-card {
  background: linear-gradient(145deg, #ffffff 0%, #f8fafc 100%);
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
  display: flex;
  gap: 12px;
  cursor: pointer;
  position: relative;
  border: 1px solid rgba(226, 232, 240, 0.3);
  flex-direction: column;
}

.log-card::before,
.archive-card::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 4px;
  background: linear-gradient(90deg, #409EFF 0%, #67C23A 100%);
  border-radius: 12px 12px 0 0;
}

.log-card:hover,
.archive-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.15);
  border-color: #409EFF;
  background: linear-gradient(145deg, #ffffff 0%, #f0f9ff 100%);
}

.log-left,
.archive-left {
  flex-shrink: 0;
}

.time-icon-wrapper {
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
}

.time-icon {
  font-size: 24px;
  color: white;
}

.log-right,
.archive-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.log-header,
.archive-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  width: 100%;
  margin-bottom: 12px;
}

.log-title,
.archive-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  transition: all 0.3s ease;
  margin: 0;
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.log-date,
.archive-date {
  font-size: 13px;
  color: #909399;
  font-weight: 400;
  padding: 4px 10px;
  background: #f5f7fa;
  border-radius: 6px;
}

.status-tag {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.status-tag.pending {
  background: #fff7ed;
  color: #f97316;
  border: 1px solid #fdba74;
}

.status-tag.submitted {
  background: #dbeafe;
  color: #3b82f6;
  border: 1px solid #93c5fd;
}

.status-tag.reviewed {
  background: #d1fae5;
  color: #10b981;
  border: 1px solid #6ee7b7;
}

.status-tag.draft {
  background: #f5f5f5;
  color: #909399;
  border: 1px solid #d9d9d9;
}

.log-content,
.archive-content {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  margin-bottom: 12px;
  padding: 12px;
  background: #fafafa;
  border-radius: 6px;
  border: 1px solid #f0f0f0;
}

.log-info-grid,
.archive-info-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 12px;
  color: #606266;
  padding: 0;
  background: none;
  border-radius: 0;
  border: none;
  margin-bottom: 12px;
}

.log-info-grid .info-item,
.archive-info-grid .info-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: #f5f7fa;
  border-radius: 4px;
  transition: all 0.2s ease;
  flex: none;
  min-width: auto;
}

.log-info-grid .info-item:hover,
.archive-info-grid .info-item:hover {
  background: #e6f7ff;
}

.log-info-grid .info-item .info-icon,
.archive-info-grid .info-item .info-icon {
  font-size: 14px;
  color: #409EFF;
  width: 16px;
  text-align: center;
  flex-shrink: 0;
}

.log-actions,
.archive-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: center;
  margin-top: auto;
  padding: 0;
  background: transparent;
  border: none;
  flex-wrap: wrap;
}

.action-button {
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
  border: 1px solid;
  display: flex;
  align-items: center;
  gap: 6px;
  text-transform: none;
}

.action-button.edit {
  background: linear-gradient(135deg, #fff7e6 0%, #ffe7ba 100%);
  color: #fa8c16;
  border-color: #ffd591;
  box-shadow: 
    0 2px 8px rgba(250, 140, 22, 0.15),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.action-button.edit:hover {
  transform: translateY(-2px);
  box-shadow: 
    0 4px 12px rgba(250, 140, 22, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.action-button.delete {
  background: linear-gradient(135deg, #fff1f0 0%, #ffccc7 100%);
  color: #f5222d;
  border-color: #ffa39e;
  box-shadow: 
    0 2px 8px rgba(245, 34, 45, 0.15),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.action-button.delete:hover {
  transform: translateY(-2px);
  box-shadow:
    0 4px 12px rgba(245, 34, 45, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.action-button.withdraw {
  background: linear-gradient(135deg, #fff7e6 0%, #ffd591 100%);
  color: #fa8c16;
  border-color: #ffd591;
  box-shadow:
    0 2px 8px rgba(250, 140, 22, 0.15),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.action-button.withdraw:hover {
  transform: translateY(-2px);
  box-shadow:
    0 4px 12px rgba(250, 140, 22, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.action-button.view {
  background: linear-gradient(135deg, #409EFF 0%, #66b1ff 100%);
  color: white;
  border-color: #409EFF;
  box-shadow: 
    0 3px 10px rgba(64, 158, 255, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

.action-button.view:hover {
  transform: translateY(-2px);
  box-shadow: 
    0 6px 16px rgba(64, 158, 255, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.4);
}

.action-button.download {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  color: white;
  border-color: #52c41a;
  box-shadow: 
    0 3px 10px rgba(82, 196, 26, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

.action-button.download:hover {
  transform: translateY(-2px);
  box-shadow: 
    0 6px 16px rgba(82, 196, 26, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.4);
}

.review-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: linear-gradient(135deg, #f6ffed 0%, #d9f7be 100%);
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  color: #52c41a;
  box-shadow: 
    0 2px 8px rgba(82, 196, 26, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.review-icon {
  font-size: 14px;
}

/* 实习确认表样式 */
.confirmation-form {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.form-card {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.form-header {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e8e8e8;
}

.form-header h3 {
  font-size: 20px;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 8px 0;
}

.form-header p {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}

.form-section {
  margin-bottom: 24px;
}

.form-section .section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border-radius: 8px;
}

.form-section .section-header h4 {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.form-section .section-icon {
  font-size: 18px;
  color: #409EFF;
}

.info-table {
  background: #f8fafc;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.table-row {
  display: grid;
  grid-template-columns: 1fr 2fr 1fr 2fr;
  gap: 0;
  border-bottom: 1px solid #e2e8f0;
}

.table-row:last-child {
  border-bottom: none;
}

.table-cell {
  padding: 16px;
  display: flex;
  align-items: center;
}

.table-cell.label {
  background: #f1f5f9;
  font-weight: 500;
  color: #334155;
  font-size: 14px;
  border-right: 1px solid #e2e8f0;
}

.table-cell.value {
  background: white;
  font-size: 14px;
}

.table-cell.value[colspan="3"] {
  grid-column: span 3;
}

.table-cell .el-input {
  width: 100%;
}

/* 安全承诺样式 */
.commitment-section {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.commitment-item {
  margin-bottom: 12px;
}

.commitment-item:last-child {
  margin-bottom: 0;
}

/* 上传区域样式 */
.upload-section {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.upload-button {
  margin-bottom: 16px;
}

.upload-tip {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 8px;
}

/* 表单按钮样式 */
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #e8e8e8;
}

.reset-button {
  background: #f8fafc;
  color: #64748b;
  border: 1px solid #e2e8f0;
}

.submit-button {
  background: #67C23A;
  border: none;
}

.submit-button:hover {
  background: #5daf34;
}

/* 历史记录样式 */
.history-card {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.history-header {
  margin-bottom: 16px;
}

.history-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
}

.history-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.history-info {
  flex: 1;
}

.history-date {
  font-size: 13px;
  color: #94a3b8;
  margin-bottom: 4px;
}

.history-company {
  font-size: 15px;
  font-weight: 500;
  color: #1e293b;
  margin-bottom: 4px;
}

.history-status {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
}

.history-status.confirmed {
  background: #f0fdf4;
  color: #22c55e;
  border: 1px solid #dcfce7;
}

.history-status.pending {
  background: #fffbeb;
  color: #f59e0b;
  border: 1px solid #fef3c7;
}

.view-button {
  background: #409EFF;
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.3s ease;
}

.view-button:hover {
  background: #337ecc;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

/* 响应式调整 */
@media screen and (max-width: 1024px) {
  .table-row {
    grid-template-columns: 1fr 3fr;
  }
  
  .table-row .table-cell:nth-child(3),
  .table-row .table-cell:nth-child(4) {
    grid-column: 1 / -1;
  }
  
  .table-row .table-cell:nth-child(3) {
    border-right: none;
    border-top: 1px solid #e2e8f0;
  }
  
  .table-row .table-cell:nth-child(4) {
    border-top: 1px solid #e2e8f0;
  }
}

@media screen and (max-width: 768px) {
  .form-card {
    padding: 16px;
  }
  
  .table-cell {
    padding: 12px;
  }
  
  .form-actions {
    flex-direction: column;
  }
  
  .form-actions button {
    width: 100%;
  }
  
  .history-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  
  .view-button {
    align-self: flex-end;
  }
}

.archive-info-list {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #64748b;
}

.info-icon {
  font-size: 16px;
  color: #94a3b8;
}

.certificate-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

.certificate-status.available {
  background: #f0fdf4;
  color: #22c55e;
  border: 1px solid #86efac;
}

.certificate-status.pending {
  background: #fff7ed;
  color: #f97316;
  border: 1px solid #fdba74;
}

.certificate-icon {
  font-size: 14px;
}

/* 空状态样式 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 24px;
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
  margin-bottom: 16px;
}

.create-button {
  background: #409EFF;
  color: white;
  border: none;
  padding: 12px 24px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: all 0.3s ease;
}

.create-button:hover {
  background: #337ecc;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

/* 对话框样式 - 统一为面试管理对话框样式 */
.detail-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.detail-content {
  display: flex;
  flex-direction: column;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 24px 28px;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border-bottom: 1px solid #e8e8e8;
}

.detail-info {
  flex: 1;
  text-align: left;
  margin-right: auto;
  min-width: 0;
}

.detail-title {
  font-size: 20px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: flex-start;
}

.detail-title .detail-company-icon {
  font-size: 26px;
  color: #1890ff;
  flex-shrink: 0;
  filter: drop-shadow(0 2px 4px rgba(24, 144, 255, 0.4));
  background: linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%);
  padding: 5px;
  border-radius: 6px;
}

.detail-title span {
  flex-shrink: 0;
  text-align: left;
}

.detail-position {
  font-size: 14px;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 6px;
}

.detail-position .detail-company-icon {
  font-size: 18px;
  color: #1890ff;
  flex-shrink: 0;
  filter: drop-shadow(0 2px 4px rgba(24, 144, 255, 0.4));
  background: linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%);
  padding: 3px;
  border-radius: 4px;
}

.detail-position span {
  flex-shrink: 0;
}

.week-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: white;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  color: #409eff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.week-icon {
  font-size: 16px;
}

.archive-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: white;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  color: #22c55e;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.badge-icon {
  font-size: 16px;
}

.detail-status {
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
}

.detail-status.draft {
  background: #fef3c7;
  color: #f59e0b;
}

.detail-status.submitted {
  background: #dbeafe;
  color: #3b82f6;
}

.detail-status.pending {
  background: #fff7ed;
  color: #f97316;
}

.detail-status.reviewed {
  background: #d1fae5;
  color: #10b981;
}

.detail-sections {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px 28px;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-icon {
  font-size: 16px;
  color: #409EFF;
}

.section-label {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}

.section-input :deep(.el-input__wrapper) {
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
}

.section-input :deep(.el-input__wrapper):hover {
  border-color: #409eff;
}

.section-input :deep(.el-input__wrapper.is-focus) {
  border-color: #409eff;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.1);
}

.section-date-picker :deep(.el-input__wrapper) {
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
}

.section-date-picker :deep(.el-input__wrapper):hover {
  border-color: #409eff;
}

.section-date-picker :deep(.el-input__wrapper.is-focus) {
  border-color: #409eff;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.1);
}

.section-textarea :deep(.el-textarea__inner) {
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.6;
  transition: all 0.3s ease;
}

.section-textarea :deep(.el-textarea__inner):hover {
  border-color: #409eff;
}

.section-textarea :deep(.el-textarea__inner):focus {
  border-color: #409eff;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.1);
}

/* 信息网格样式 */
.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.info-card {
  background: #f8fafc;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  gap: 12px;
  transition: all 0.3s ease;
}

.info-card:hover {
  background: #f1f5f9;
  transform: translateY(-2px);
}

.info-card-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  flex-shrink: 0;
}

.info-card-icon .el-icon {
  font-size: 18px;
}

/* 加载状态样式 */
.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 16px;
}

.loading-spinner {
  width: 48px;
  height: 48px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #409EFF;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.loading-text {
  font-size: 16px;
  color: #64748b;
  font-weight: 500;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.info-card-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-card-label {
  font-size: 12px;
  color: #64748b;
}

.info-card-value {
  font-size: 14px;
  font-weight: 500;
  color: #1e293b;
}

/* 通知列表样式 */
.notice-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.notice-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 8px;
  transition: all 0.3s ease;
  line-height: 1.6;
  color: #475569;
  font-size: 14px;
}

.notice-item:hover {
  background: #f1f5f9;
}

.notice-item.review-item {
  background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%);
  color: #166534;
}

.notice-icon {
  font-size: 18px;
  color: #67C23A;
  flex-shrink: 0;
  margin-top: 2px;
}

/* 评价网格样式 */
.evaluation-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.evaluation-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  background: #f8fafc;
  border-radius: 8px;
}

.evaluation-label {
  font-size: 13px;
  color: #64748b;
}

/* 评分详情样式 */
.scoring-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.scoring-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.scoring-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.scoring-label {
  font-size: 13px;
  color: #64748b;
}

.scoring-value {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.scoring-bar {
  width: 100%;
  height: 8px;
  background: #f1f5f9;
  border-radius: 4px;
}

.scoring-bar {
  height: 100%;
  background: linear-gradient(90deg, #409EFF 0%, #67C23A 100%);
  border-radius: 4px;
  transition: width 0.3s ease;
}

.scoring-item.total {
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid #e2e8f0;
}

.scoring-item.total .scoring-label {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.scoring-item.total .scoring-value {
  font-size: 16px;
  font-weight: 700;
  color: #409EFF;
}

.scoring-item.total .scoring-bar {
  background: linear-gradient(90deg, #67C23A 0%, #409EFF 100%);
  height: 10px;
  border-radius: 5px;
}

/* 评分摘要样式 */
.scoring-summary {
  background: #f8fafc;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}

.scoring-summary .scoring-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.scoring-summary .scoring-label {
  font-size: 13px;
  color: #64748b;
}

.scoring-summary .scoring-value {
  font-size: 16px;
  font-weight: 700;
  color: #409EFF;
}

.rating-tag {
  margin-left: 12px;
  font-weight: 600;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 日志统计样式 */
.logs-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.log-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border-radius: 8px;
  transition: all 0.3s ease;
}

.log-stat:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.stat-number {
  font-size: 28px;
  font-weight: 700;
  color: #409eff;
}

.stat-label {
  font-size: 13px;
  color: #64748b;
}

/* 对话框按钮样式 */
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px 28px;
  background: #f8fafc;
  border-top: 1px solid #e8e8e8;
}

.cancel-button {
  padding: 10px 24px;
  border-radius: 8px;
  font-size: 14px;
  border: 1px solid #dcdfe6;
  color: #606266;
  transition: all 0.3s ease;
}

.cancel-button:hover {
  background: #f1f5f9;
  border-color: #c0c4cc;
}

.draft-button {
  padding: 10px 24px;
  border-radius: 8px;
  font-size: 14px;
  background: #f0f9ff;
  border: 1px solid #a0cfff;
  color: #409eff;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 6px;
}

.draft-button:hover {
  background: #d9ecff;
  border-color: #409eff;
}

.submit-button {
  padding: 10px 28px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.3s ease;
  background: #409EFF;
  color: white;
  border: none;
}

.submit-button:hover {
  background: #337ecc;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

/* 响应式设计 */
@media screen and (max-width: 1024px) {
  .overview-content {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
  }

  .overview-right {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .progress-section {
    justify-content: center;
  }

  .submit-log-button {
    justify-content: center;
  }

  .info-grid,
  .evaluation-grid {
    grid-template-columns: 1fr;
  }
}

@media screen and (max-width: 768px) {
  .internships-container {
    padding: 16px;
  }

  .page-header h2 {
    font-size: 24px;
  }

  .overview-card,
  .tabs-section,
  .content-section {
    padding: 16px;
  }

  .log-card,
  .archive-card {
    flex-direction: column;
    gap: 16px;
  }

  .log-left,
  .archive-left {
    align-self: flex-start;
  }

  .log-actions,
  .archive-actions {
    justify-content: flex-start;
  }

  .tabs-container {
    flex-direction: column;
    width: 100%;
  }

  .tab-item {
    width: 100%;
    text-align: center;
  }

  .logs-summary {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media screen and (max-width: 480px) {
  .company-name {
    font-size: 16px;
  }

  .company-position {
    font-size: 13px;
  }

  .log-title,
  .archive-company {
    font-size: 16px;
  }

  .log-date,
  .archive-position {
    font-size: 13px;
  }

  .action-button {
    padding: 8px 16px;
    font-size: 12px;
  }

  .logs-summary {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .log-stat {
    padding: 16px;
  }

  .stat-number {
    font-size: 24px;
  }
}

/* 实习心得提交对话框 - 文件上传样式 */
.submission-tabs {
  padding: 0 10px;
}

.upload-panel {
  padding: 20px 0;
}

.word-upload {
  width: 100%;
}

.word-upload :deep(.el-upload-dragger) {
  padding: 40px 20px;
  border: 2px dashed #dcdfe6;
  border-radius: 8px;
  background: #fafafa;
  transition: all 0.3s;
}

.word-upload :deep(.el-upload-dragger:hover) {
  border-color: #409eff;
  background: #f0f7ff;
}

.upload-icon {
  font-size: 48px;
  color: #909399;
  margin-bottom: 16px;
}

.upload-text {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.upload-title {
  font-size: 16px;
  color: #303133;
  font-weight: 500;
}

.upload-hint {
  font-size: 14px;
  color: #909399;
}

.upload-tip {
  margin-top: 12px;
  font-size: 13px;
  color: #909399;
  text-align: center;
}
</style>