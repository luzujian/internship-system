<template>
  <div class="internship-confirmation-form-container">
    <div class="page-header">
      <h2>实习确认表</h2>
      <p>请确认以下实习信息，如有疑问请联系企业或指导老师</p>
    </div>

    <!-- 待确认表单（仅当有待确认的面试时显示） -->
    <div v-if="hasPendingConfirmation" class="confirmation-content">
      <el-card class="info-card" shadow="never">
        <template #header>
          <div class="card-header">
            <el-icon class="header-icon"><InfoFilled /></el-icon>
            <span>请仔细核对以下信息，确认无误后提交</span>
          </div>
        </template>

        <!-- 学生信息区域 -->
        <div class="info-section">
          <div class="section-title">
            <el-icon><User /></el-icon>
            <span>学生信息</span>
          </div>
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">姓名</span>
              <span class="info-value">{{ confirmationData.studentName }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">学号</span>
              <span class="info-value">{{ confirmationData.studentNumber }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">性别</span>
              <span class="info-value">{{ confirmationData.gender }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">年级</span>
              <span class="info-value">{{ confirmationData.grade }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">专业</span>
              <span class="info-value">{{ confirmationData.major }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">班级</span>
              <span class="info-value">{{ confirmationData.className }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">联系电话</span>
              <span class="info-value">{{ confirmationData.contactPhone }}</span>
            </div>
          </div>
        </div>

        <!-- 实习信息区域 -->
        <div class="info-section">
          <div class="section-title">
            <el-icon><OfficeBuilding /></el-icon>
            <span>实习信息</span>
          </div>
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">实习企业</span>
              <el-select
                v-model="selectedCompanyId"
                placeholder="请选择企业"
                class="info-select"
                @change="handleCompanyChange"
              >
                <el-option
                  v-for="item in companyOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </div>
            <div class="info-item">
              <span class="info-label">实习岗位</span>
              <el-select
                v-model="selectedPositionId"
                placeholder="请选择岗位"
                class="info-select"
                :disabled="!selectedCompanyId"
                @change="handlePositionChange"
              >
                <el-option
                  v-for="item in positionOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </div>
            <div class="info-item">
              <span class="info-label">企业地址</span>
              <span class="info-value">{{ confirmationData.companyAddress }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">企业电话</span>
              <span class="info-value">{{ confirmationData.companyPhone }}</span>
            </div>
          </div>
        </div>

        <!-- 实习时间区域 -->
        <div class="info-section">
          <div class="section-title">
            <el-icon><Clock /></el-icon>
            <span>实习时间（统一安排）</span>
          </div>
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">开始日期</span>
              <span class="info-value date">{{ confirmationData.internshipStartTime }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">结束日期</span>
              <span class="info-value date">{{ confirmationData.internshipEndTime }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">实习时长</span>
              <span class="info-value duration">{{ confirmationData.internshipDuration }} 天</span>
            </div>
          </div>
        </div>

        <!-- 备注区域 -->
        <div class="info-section remark-section">
          <div class="section-title">
            <el-icon><ChatDotRound /></el-icon>
            <span>备注信息</span>
            <span class="optional-tag">选填</span>
          </div>
          <el-input
            v-model="confirmationData.remark"
            type="textarea"
            :rows="3"
            placeholder="如有疑问或需要说明的情况，请在此填写（选填）"
            maxlength="500"
            show-word-limit
          />
        </div>

        <!-- 提交按钮 -->
        <div class="submit-section">
          <div class="submit-tip">
            <el-icon><CircleCheckFilled /></el-icon>
            <span>确认提交后，您的实习信息将发送给企业进行最终确认</span>
          </div>
          <el-button type="primary" size="large" @click="submitConfirmation" :loading="submitting" class="submit-btn">
            确认提交
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- 确认记录列表 -->
    <el-card class="history-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-icon class="header-icon"><Document /></el-icon>
            <span>确认记录</span>
          </div>
          <el-tag type="info" size="small">共 {{ historyList.length }} 条</el-tag>
        </div>
      </template>

      <!-- 拒绝原因提示 -->
      <div v-if="rejectionReason" class="rejection-alert">
        <el-alert type="warning" :closable="false" show-icon>
          <template #title>
            <span>企业撤回原因：{{ rejectionReason }}</span>
          </template>
        </el-alert>
      </div>

      <div v-if="historyList.length > 0" class="history-list">
        <div v-for="item in historyList" :key="item.id" class="history-item">
          <div class="history-main">
            <div class="history-company">{{ item.companyName || '未指定' }}</div>
            <div class="history-position">{{ item.positionName || '未指定' }}</div>
            <div class="history-phone">
              <el-icon><Phone /></el-icon>
              {{ item.companyPhone || '未提供' }}
            </div>
            <div class="history-time">
              <el-icon><Clock /></el-icon>
              {{ item.internshipStartTime || '待定' }} ~ {{ item.internshipEndTime || '待定' }}
            </div>
            <div class="history-confirm-time" v-if="item.createTime">
              <el-icon><Calendar /></el-icon>
              确认时间：{{ formatDate(item.createTime) }}
            </div>
            <!-- 拒绝原因显示 -->
            <div class="history-rejection" v-if="item.status === 2 && item.rejectionReason">
              <el-icon><Warning /></el-icon>
              撤回原因：{{ item.rejectionReason }}
            </div>
          </div>
          <div class="history-actions">
            <el-tag :type="getStatusType(item.status, item.internshipStatus)" size="small">
              {{ getStatusText(item.status, item.internshipStatus) }}
            </el-tag>
            <button class="action-button view" @click="viewHistory(item)">
              <el-icon><View /></el-icon>
              查看
            </button>
            <!-- 撤回按钮：仅 status=0 待确认时可显示 -->
            <button v-if="item.status === 0" class="action-button recall" @click="openRecallDialog(item)">
              <el-icon><RefreshLeft /></el-icon>
              撤回
            </button>
            <button v-if="item.status === 2" class="action-button edit" @click="editRejectedRecord(item)">
              <el-icon><Edit /></el-icon>
              修改
            </button>
            <!-- 单位变更按钮 -->
            <template v-if="canApplyUnitChange(item)">
              <button v-if="unitChangeStatus.hasPendingApplication" class="action-button pending" disabled>
                <el-icon><Clock /></el-icon>
                审核中
              </button>
              <button v-else-if="unitChangeStatus.hasRejectedApplication" class="action-button reapply" @click="openUnitChangeDialog(true)">
                <el-icon><Refresh /></el-icon>
                再次申请
              </button>
              <button v-else class="action-button change" @click="openUnitChangeDialog(false)">
                <el-icon><Refresh /></el-icon>
                申请变更
              </button>
            </template>
          </div>
        </div>
      </div>
      <div v-else class="empty-history">
        <el-icon class="empty-icon"><Document /></el-icon>
        <div class="empty-text">暂无确认记录</div>
      </div>
    </el-card>

    <!-- 成功提示对话框 -->
    <el-dialog
      v-model="showSuccessDialog"
      title=""
      width="400px"
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      class="success-dialog"
    >
      <div class="success-content">
        <div class="success-icon">
          <el-icon><CircleCheckFilled /></el-icon>
        </div>
        <div class="success-title">提交成功</div>
        <div class="success-desc">您的实习确认表已提交，等待企业确认</div>
      </div>
      <template #footer>
        <el-button type="primary" @click="showSuccessDialog = false; hasPendingConfirmation = false" class="success-btn">
          我知道了
        </el-button>
      </template>
    </el-dialog>

    <!-- 撤回确认申请对话框 -->
    <el-dialog
      v-model="recallDialogVisible"
      title="撤回确认申请"
      width="500px"
    >
      <el-alert
        title="撤回说明"
        type="info"
        :closable="false"
        show-icon
        class="recall-notice"
      >
        <template #default>
          <p>• 撤回后将取消当前的确认申请</p>
          <p>• 企业尚未确认，可以撤回</p>
          <p>• 请选择撤回原因</p>
        </template>
      </el-alert>

      <el-form :model="recallForm" label-width="100px" class="recall-form">
        <el-form-item label="撤回原因" required>
          <el-select
            v-model="recallForm.reason"
            placeholder="请选择撤回原因"
            style="width: 100%"
          >
            <el-option label="信息填写错误" value="information_error" />
            <el-option label="企业信息有误" value="company_info_error" />
            <el-option label="岗位不符合预期" value="position_not_expected" />
            <el-option label="实习时间变更" value="time_changed" />
            <el-option label="其他原因" value="other" />
          </el-select>
        </el-form-item>

        <el-form-item
          v-if="recallForm.reason === 'other'"
          label="详细说明"
          required
        >
          <el-input
            v-model="recallForm.otherReason"
            type="textarea"
            :rows="3"
            placeholder="请详细说明撤回原因"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recallDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRecall" :loading="recallSubmitting">确认撤回</el-button>
      </template>
    </el-dialog>

    <!-- 查看历史记录对话框（只读） -->
    <el-dialog
      v-model="viewingConfirmation"
      title="确认记录详情"
      width="600px"
      class="view-dialog"
    >
      <div v-if="viewingRecord" class="view-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="学生姓名">
            {{ viewingRecord.studentName }}
          </el-descriptions-item>
          <el-descriptions-item label="学号">
            {{ viewingRecord.studentNumber }}
          </el-descriptions-item>
          <el-descriptions-item label="实习企业">
            {{ viewingRecord.companyName }}
          </el-descriptions-item>
          <el-descriptions-item label="实习岗位">
            {{ viewingRecord.positionName }}
          </el-descriptions-item>
          <el-descriptions-item label="企业地址" :span="2">
            {{ viewingRecord.companyAddress }}
          </el-descriptions-item>
          <el-descriptions-item label="联系电话">
            {{ viewingRecord.companyPhone }}
          </el-descriptions-item>
          <el-descriptions-item label="实习时间">
            {{ viewingRecord.internshipStartTime }} ~ {{ viewingRecord.internshipEndTime }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(viewingRecord.status, viewingRecord.internshipStatus)" size="small">
              {{ getStatusText(viewingRecord.status, viewingRecord.internshipStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="确认时间">
            {{ formatDate(viewingRecord.createTime) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="viewingRecord.remark" label="备注" :span="2">
            {{ viewingRecord.remark }}
          </el-descriptions-item>
          <el-descriptions-item v-if="viewingRecord.status === 2 && viewingRecord.rejectionReason" label="撤回原因" :span="2">
            <span class="rejection-text">{{ viewingRecord.rejectionReason }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="closeViewing">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 变更申请表单对话框 -->
    <el-dialog
      v-model="showUnitChangeDialog"
      title="实习单位变更申请"
      width="700px"
      class="unit-change-dialog"
    >
      <div v-if="unitChangeForm" class="unit-change-form">
        <!-- 学生信息（只读） -->
        <div class="info-section">
          <div class="section-title">
            <el-icon><User /></el-icon>
            <span>学生信息</span>
          </div>
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">姓名</span>
              <span class="info-value">{{ unitChangeForm.studentName }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">学号</span>
              <span class="info-value">{{ unitChangeForm.studentNumber }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">性别</span>
              <span class="info-value">{{ unitChangeForm.gender }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">年级</span>
              <span class="info-value">{{ unitChangeForm.grade }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">专业</span>
              <span class="info-value">{{ unitChangeForm.major }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">班级</span>
              <span class="info-value">{{ unitChangeForm.className }}</span>
            </div>
          </div>
        </div>

        <!-- 原实习单位（只读） -->
        <div class="info-section">
          <div class="section-title">
            <el-icon><OfficeBuilding /></el-icon>
            <span>原实习单位</span>
          </div>
          <div class="info-grid">
            <div class="info-item full-width">
              <span class="info-label">原实习单位</span>
              <span class="info-value highlight">{{ unitChangeForm.oldCompany }}</span>
            </div>
          </div>
        </div>

        <!-- 驳回原因提示（仅再次申请时显示） -->
        <div v-if="unitChangeForm.rejectReason" class="reject-alert">
          <el-alert type="error" :closable="false" show-icon>
            <template #title>
              <span>驳回原因：{{ unitChangeForm.rejectReason }}</span>
            </template>
          </el-alert>
        </div>

        <!-- 申请信息（可编辑） -->
        <div class="info-section">
          <div class="section-title">
            <el-icon><Edit /></el-icon>
            <span>变更信息</span>
          </div>

          <div class="form-item">
            <label>申请理由<span class="required">*</span></label>
            <el-input
              v-model="unitChangeForm.reason"
              type="textarea"
              :rows="3"
              placeholder="请填写申请变更的理由"
              maxlength="500"
              show-word-limit
            />
          </div>

          <div class="form-item">
            <label>新实习单位<span class="required">*</span></label>
            <el-input
              v-model="unitChangeForm.newCompany"
              placeholder="请输入新实习单位名称"
              maxlength="100"
            />
          </div>
        </div>

        <!-- 上传资料 -->
        <div class="info-section">
          <div class="section-title">
            <el-icon><DocumentCopy /></el-icon>
            <span>上传资料</span>
          </div>

          <div class="upload-items">
            <div class="upload-item">
              <label>家庭证明<span class="required">*</span></label>
              <el-upload
                :action="uploadUrl"
                :headers="uploadHeaders"
                :on-success="(res) => handleUploadSuccess(res, '家庭证明')"
                :before-upload="beforeUpload"
                accept="image/*,.pdf"
                :show-file-list="false"
              >
                <el-button type="primary" plain>上传家庭证明</el-button>
              </el-upload>
              <div v-if="unitChangeForm.materials?.['家庭证明']" class="uploaded-file">
                <img :src="unitChangeForm.materials['家庭证明']" class="preview-image" @click="previewImage(unitChangeForm.materials['家庭证明'])" />
              </div>
            </div>

            <div class="upload-item">
              <label>新单位接收证明<span class="required">*</span></label>
              <el-upload
                :action="uploadUrl"
                :headers="uploadHeaders"
                :on-success="(res) => handleUploadSuccess(res, '新单位接收证明')"
                :before-upload="beforeUpload"
                accept="image/*,.pdf"
                :show-file-list="false"
              >
                <el-button type="primary" plain>上传新单位接收证明</el-button>
              </el-upload>
              <div v-if="unitChangeForm.materials?.['新单位接收证明']" class="uploaded-file">
                <img :src="unitChangeForm.materials['新单位接收证明']" class="preview-image" @click="previewImage(unitChangeForm.materials['新单位接收证明'])" />
              </div>
            </div>

            <div class="upload-item">
              <label>调动申请书<span class="required">*</span></label>
              <el-upload
                :action="uploadUrl"
                :headers="uploadHeaders"
                :on-success="(res) => handleUploadSuccess(res, '调动申请书')"
                :before-upload="beforeUpload"
                accept="image/*,.pdf"
                :show-file-list="false"
              >
                <el-button type="primary" plain>上传调动申请书</el-button>
              </el-upload>
              <div v-if="unitChangeForm.materials?.['调动申请书']" class="uploaded-file">
                <img :src="unitChangeForm.materials['调动申请书']" class="preview-image" @click="previewImage(unitChangeForm.materials['调动申请书'])" />
              </div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="showUnitChangeDialog = false">取消</el-button>
        <el-button type="primary" @click="submitUnitChangeApp" :loading="submittingChange">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from "vue";
import { ElMessage } from "element-plus";
import {
  User,
  OfficeBuilding,
  Clock,
  ChatDotRound,
  DocumentChecked,
  Document,
  InfoFilled,
  CircleCheckFilled,
  Phone,
  Calendar,
  View,
  Warning,
  Edit,
  Refresh,
  DocumentCopy,
  RefreshLeft
} from "@element-plus/icons-vue";

import request from '@/utils/request';
import { useAuthStore } from '@/store/auth';
import { getInternshipTimeSettings } from '@/api/teacherSettings';
import { getUnitChangeStatus, submitUnitChange, resubmitUnitChange } from '@/api/studentInternship';

const authStore = useAuthStore()

const submitting = ref(false)
const showSuccessDialog = ref(false)
const hasPendingConfirmation = ref(false)
const justSubmitted = ref(false) // 标记是否刚刚提交过，用于防止重复显示表单
const historyList = ref([])

// 查看模式：用于只读查看历史记录
const viewingConfirmation = ref(false)
const viewingRecord = ref<any>(null)

// 编辑被拒绝的记录模式
const editingRejectedRecord = ref(false)
const rejectedRecordData = ref<any>(null)

// 撤回相关状态
const recallDialogVisible = ref(false)
const recallSubmitting = ref(false)
const recallForm = reactive({
  reason: '',
  otherReason: ''
})
const currentRecallRecord = ref<any>(null)

const recallReasonMap: Record<string, string> = {
  'information_error': '信息填写错误',
  'company_info_error': '企业信息有误',
  'position_not_expected': '岗位不符合预期',
  'time_changed': '实习时间变更'
}

// 单位变更相关状态
const showUnitChangeDialog = ref(false)
const submittingChange = ref(false)
const unitChangeStatus = ref({
  hasPendingApplication: false,
  hasRejectedApplication: false,
  currentApplicationId: null,
  application: null
})

const unitChangeForm = ref({
  studentName: '',
  studentNumber: '',
  gender: '',
  grade: '',
  major: '',
  className: '',
  oldCompany: '',
  newCompany: '',
  reason: '',
  materials: {
    '家庭证明': '',
    '新单位接收证明': '',
    '调动申请书': ''
  },
  rejectReason: ''
})

// 应聘时间段状态
const applicationPeriod = ref({
  applicationStartTime: '',
  applicationEndTime: ''
})

// 上传配置
const uploadUrl = '/api/upload'
const uploadHeaders = {}

// 拒绝原因的显示
const rejectionReason = ref('')

const confirmationData = reactive({
  studentName: '',
  studentNumber: '',
  gender: '',
  grade: '',
  major: '',
  className: '',
  contactPhone: '',
  email: '',
  companyName: '',
  positionName: '',
  companyAddress: '',
  companyPhone: '',
  internshipStartTime: '',
  internshipEndTime: '',
  internshipDuration: 0,
  remark: '',
  status: 0,
  statusId: null
})

// 面试通过的企业和岗位数据
const approvedCompanies = ref<Array<{companyId: number, companyName: string}>>([])
const approvedPositions = ref<Array<{positionId: number, positionName: string, companyId: number, companyPhone?: string, companyAddress?: string}>>([])
const selectedCompanyId = ref<number | null>(null)
const selectedPositionId = ref<number | null>(null)
const approvedInterviewsData = ref<any[]>([]) // 保存完整的面试通过数据

// 获取面试通过的记录
const fetchApprovedInterviews = async () => {
  try {
    const response = await request.get('/student/interviews')
    if (response.code === 200 && response.data) {
      const approved = response.data.filter((item: any) => item.status === 'interview_passed')
      approvedInterviewsData.value = approved // 保存完整数据
      approvedCompanies.value = approved.map((item: any) => ({
        companyId: item.companyId,
        companyName: item.companyName
      }))
      approvedPositions.value = approved.map((item: any) => ({
        positionId: item.positionId,
        positionName: item.positionName,
        companyId: item.companyId,
        companyPhone: item.contactPhone || '',
        companyAddress: item.interviewLocation || ''
      }))
    }
  } catch (error) {
    console.error('获取面试通过记录失败:', error)
  }
}

// 企业选项计算属性
const companyOptions = computed(() => {
  const map = new Map()
  approvedCompanies.value.forEach(item => {
    if (!map.has(item.companyId)) {
      map.set(item.companyId, item.companyName)
    }
  })
  return Array.from(map, ([id, name]) => ({ value: id, label: name }))
})

// 岗位选项计算属性
const positionOptions = computed(() => {
  if (!selectedCompanyId.value) return []
  return approvedPositions.value
    .filter(item => item.companyId === selectedCompanyId.value)
    .map(item => ({ value: item.positionId, label: item.positionName }))
})

// 企业变更处理
const handleCompanyChange = () => {
  // 根据选中的企业ID获取企业名称
  const company = approvedCompanies.value.find(item => item.companyId === selectedCompanyId.value)
  confirmationData.companyName = company?.companyName || ''
  selectedPositionId.value = null
  confirmationData.positionName = ''
  confirmationData.companyPhone = ''
  confirmationData.companyAddress = ''
}

// 岗位变更处理
const handlePositionChange = () => {
  if (!selectedPositionId.value) return
  const position = approvedPositions.value.find(item => item.positionId === selectedPositionId.value)
  if (position) {
    confirmationData.positionName = position.positionName || ''
    confirmationData.companyPhone = position.companyPhone || ''
    confirmationData.companyAddress = position.companyAddress || ''
  }
}

// 获取状态类型
const getStatusType = (status, internshipStatus) => {
  // 如果学生实习状态为已中断（5），显示警告状态
  if (internshipStatus === 5) {
    return 'warning'
  }
  const typeMap = {
    0: 'info',     // 待企业确认
    1: 'success',  // 企业已确认
    2: 'danger'    // 企业已拒绝
  }
  return typeMap[status] || 'info'
}

// 获取状态文本
const getStatusText = (status, internshipStatus) => {
  // 如果学生实习状态为已中断（5），显示已中断
  if (internshipStatus === 5) {
    return '已中断'
  }
  const textMap = {
    0: '待企业确认',
    1: '已确认',
    2: '已拒绝'
  }
  return textMap[status] || '未知'
}

// 格式化日期
const formatDate = (date) => {
  if (!date) return ''
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

// 性别数字转中文
const genderToText = (gender) => {
  if (gender === 1 || gender === '1' || gender === '男') return '男'
  if (gender === 0 || gender === '0' || gender === '女') return '女'
  return gender || ''
}

// 年级格式化
const formatGrade = (grade) => {
  if (!grade) return ''
  return String(grade) + '级'
}

// 获取系统统一设置的实习时间
const fetchInternshipTimeSettings = async () => {
  try {
    const response = await getInternshipTimeSettings()
    if (response.code === 200 && response.data) {
      confirmationData.internshipStartTime = response.data.startDate || ''
      confirmationData.internshipEndTime = response.data.endDate || ''
      // 计算时长
      if (response.data.startDate && response.data.endDate) {
        const start = new Date(response.data.startDate)
        const end = new Date(response.data.endDate)
        const diffTime = Math.abs(end - start)
        const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24))
        confirmationData.internshipDuration = diffDays
      }
    }
  } catch (error) {
    console.error('获取实习时间设置失败:', error)
  }
}

// 获取应聘时间窗口设置
const fetchApplicationPeriod = async () => {
  try {
    // 从 getInternshipNodes 获取完整的应聘时间设置
    const response = await request.get('/settings/internship-nodes')
    if (response.code === 200 && response.data) {
      return {
        applicationStartTime: response.data.applicationStartTime,
        applicationEndTime: response.data.applicationEndTime
      }
    }
  } catch (error) {
    console.error('获取应聘时间设置失败:', error)
  }
  return { applicationStartTime: null, applicationEndTime: null }
}

// 判断当前时间是否在应聘期间内
const isWithinApplicationPeriod = (startDate: string, endDate: string) => {
  if (!startDate || !endDate) return false
  const now = new Date()
  const start = new Date(startDate)
  const end = new Date(endDate)
  // 设置时间为当天的开始和结束
  start.setHours(0, 0, 0, 0)
  end.setHours(23, 59, 59, 999)
  return now >= start && now <= end
}

// 获取待确认的实习信息（从已通过状态的申请中获取）
const fetchPendingConfirmation = async () => {
  // 如果刚刚提交过，不重复显示表单
  if (justSubmitted.value) {
    return
  }
  try {
    // 获取应聘时间窗口
    const period = await fetchApplicationPeriod()

    // 如果当前时间不在应聘期间内，不显示表单（只能等下一学期）
    if (period.applicationStartTime && period.applicationEndTime) {
      if (!isWithinApplicationPeriod(period.applicationStartTime, period.applicationEndTime)) {
        hasPendingConfirmation.value = false
        return
      }
    }

    // 获取学生的岗位申请列表（面试通过的数据）
    let approvedApp = null

    // 同时检查两个API
    const [jobAppResponse, applicationsResponse] = await Promise.all([
      request.get('/student/job-applications'),
      request.get('/student/applications')
    ])

    // 从岗位申请中找面试通过的
    if (jobAppResponse.code === 200 && jobAppResponse.data) {
      approvedApp = jobAppResponse.data.find((app: any) => app.status === 'interview_passed')
    }

    // 如果岗位申请没有面试通过的，检查单位变更等申请
    if (!approvedApp && applicationsResponse.code === 200 && applicationsResponse.data) {
      const applications = applicationsResponse.data
      // 找到状态为"approved"的申请（表示企业同意面试通过）
      approvedApp = applications.find((app: any) => app.status === 'approved' || app.status === 1)
    }

    if (approvedApp) {
      // 检查学生当前实习状态
      // internshipStatus: 0=无offer, 1=待确认, 2=已确定, 3=实习中, 5=已中断
      const currentStatus = historyList.value.length > 0 ? historyList.value[0].internshipStatus : null

      // 如果学生当前正在实习中（status=2已确定 或 status=3实习中），不允许再次填写
      if (currentStatus === 2 || currentStatus === 3) {
        hasPendingConfirmation.value = false
        return
      }

      // 只有状态为 0（无offer）、1（待确认）、5（已中断）时才允许填写
      hasPendingConfirmation.value = true
      // 填充学生信息（从当前用户信息）
      const user = authStore.user
      confirmationData.studentName = user?.name || ''
      confirmationData.studentNumber = user?.studentId || ''
      confirmationData.gender = genderToText(user?.gender)
      confirmationData.grade = formatGrade(user?.grade)
      confirmationData.major = user?.major || ''
      confirmationData.className = user?.class || ''
      confirmationData.contactPhone = user?.phone || ''
      confirmationData.email = user?.email || ''

      // 填充企业信息
      confirmationData.companyName = approvedApp.companyName || approvedApp.company || ''
      confirmationData.positionName = approvedApp.positionName || approvedApp.jobTitle || ''

      // 设置企业ID和岗位ID（用于提交到后端）
      selectedCompanyId.value = approvedApp.companyId || null
      selectedPositionId.value = approvedApp.positionId || null

      // 从面试通过数据中获取公司地址和电话
      const interviewData = approvedPositions.value.find(
        item => item.companyId === selectedCompanyId.value && item.positionId === selectedPositionId.value
      )
      if (interviewData) {
        confirmationData.companyPhone = interviewData.companyPhone || ''
        confirmationData.companyAddress = interviewData.companyAddress || ''
      }

      // 实习时间从系统设置获取
      await fetchInternshipTimeSettings()
    } else {
      hasPendingConfirmation.value = false
    }
  } catch (error) {
    console.error('获取待确认实习信息失败:', error)
    hasPendingConfirmation.value = false
  }
}

// 获取历史确认记录
const fetchConfirmationHistory = async () => {
  try {
    const response = await request.get('/student/internship-confirmation/history')
    if (response.code === 200 && response.data) {
      historyList.value = response.data || []
      // 如果当前正在编辑被拒绝的记录，则不自动触发编辑模式
      // 只有在首次加载时检查是否有被拒绝的记录需要处理
      if (!editingRejectedRecord.value && !viewingConfirmation.value) {
        // 检查是否有被拒绝的记录，如果有则自动显示编辑表单
        const rejectedRecord = historyList.value.find((item: any) => item.status === 2)
        if (rejectedRecord) {
          editRejectedRecord(rejectedRecord)
        }
      }
    }
  } catch (error) {
    console.error('获取确认历史失败:', error)
  }
}

// 获取单位变更申请状态
const fetchUnitChangeStatus = async () => {
  try {
    const response = await getUnitChangeStatus()
    if (response.code === 200 && response.data) {
      unitChangeStatus.value = response.data
    }
  } catch (error) {
    console.error('获取单位变更申请状态失败:', error)
  }
}

// 判断是否可以申请单位变更
const canApplyUnitChange = (item) => {
  // 已中断状态不允许申请单位变更
  if (item.internshipStatus === 5) return false
  // 必须是已确认状态(status=1待确认 或 status=2已确定)且在应聘时间段内
  if (item.status !== 1 && item.status !== 2) return false
  // 需要在应聘时间段内
  if (applicationPeriod.value.applicationStartTime && applicationPeriod.value.applicationEndTime) {
    if (!isWithinApplicationPeriod(applicationPeriod.value.applicationStartTime, applicationPeriod.value.applicationEndTime)) {
      return false
    }
  }
  return true
}

// 打开变更申请对话框
const openUnitChangeDialog = (isReapply = false) => {
  // 填充学生信息
  const user = authStore.user
  const historyRecord = historyList.value.find(item => item.status === 1 || item.status === 2)

  unitChangeForm.value = {
    studentName: user?.name || '',
    studentNumber: user?.studentId || '',
    gender: genderToText(user?.gender),
    grade: formatGrade(user?.grade),
    major: user?.major || '',
    className: user?.class || '',
    oldCompany: historyRecord?.companyName || '',
    newCompany: '',
    reason: '',
    materials: {
      '家庭证明': '',
      '新单位接收证明': '',
      '调动申请书': ''
    },
    rejectReason: ''
  }

  // 如果是再次申请，回显数据
  if (isReapply && unitChangeStatus.value.application) {
    const app = unitChangeStatus.value.application
    unitChangeForm.value.newCompany = app.newCompany || ''
    unitChangeForm.value.reason = app.reason || ''
    if (app.materials) {
      unitChangeForm.value.materials = app.materials
    }
    unitChangeForm.value.rejectReason = app.rejectReason || ''
  }

  showUnitChangeDialog.value = true
}

// 处理上传成功
const handleUploadSuccess = (response, field) => {
  if (response.code === 200 && response.data) {
    unitChangeForm.value.materials[field] = response.data
  }
}

// 预览图片
const previewImage = (url) => {
  window.open(url)
}

// 上传前校验
const beforeUpload = (file) => {
  const isImage = file.type.startsWith('image/')
  const isPdf = file.type === 'application/pdf'
  const isLt5M = file.size / 1024 / 1024 < 5

  if (!isImage && !isPdf) {
    ElMessage.error('只能上传图片或PDF文件')
    return false
  }
  if (!isLt5M) {
    ElMessage.error('文件大小不能超过5MB')
    return false
  }
  return true
}

// 提交单位变更申请
const submitUnitChangeApp = async () => {
  if (!unitChangeForm.value.reason) {
    ElMessage.warning('请填写申请理由')
    return
  }
  if (!unitChangeForm.value.newCompany) {
    ElMessage.warning('请填写新实习单位名称')
    return
  }

  submittingChange.value = true
  try {
    const data = {
      newCompany: unitChangeForm.value.newCompany,
      reason: unitChangeForm.value.reason,
      materials: unitChangeForm.value.materials
    }

    let response
    if (unitChangeStatus.value.hasRejectedApplication && unitChangeStatus.value.currentApplicationId) {
      // 再次申请
      response = await resubmitUnitChange(unitChangeStatus.value.currentApplicationId, data)
    } else {
      // 新申请
      response = await submitUnitChange(data)
    }

    if (response.code === 200) {
      ElMessage.success('提交成功')
      showUnitChangeDialog.value = false
      await fetchUnitChangeStatus()
      await fetchConfirmationHistory()
    } else {
      ElMessage.error(response.message || '提交失败')
    }
  } catch (error) {
    console.error('提交失败:', error)
    ElMessage.error('提交失败，请稍后重试')
  } finally {
    submittingChange.value = false
  }
}

// 打开撤回对话框
const openRecallDialog = (record: any) => {
  currentRecallRecord.value = record
  recallForm.reason = ''
  recallForm.otherReason = ''
  recallDialogVisible.value = true
}

// 提交撤回申请
const submitRecall = async () => {
  if (!recallForm.reason) {
    ElMessage.warning('请选择撤回原因')
    return
  }
  if (recallForm.reason === 'other' && !recallForm.otherReason) {
    ElMessage.warning('请输入详细说明')
    return
  }

  recallSubmitting.value = true
  try {
    const reason = recallForm.reason === 'other'
      ? recallForm.otherReason
      : recallReasonMap[recallForm.reason] || recallForm.reason

    const response = await request.post(`/student/internship-confirmation/${currentRecallRecord.value.id}/recall`, {
      recallReason: reason
    })

    if (response.code === 200) {
      ElMessage.success('撤回成功')
      recallDialogVisible.value = false
      await fetchConfirmationHistory()
    } else {
      ElMessage.error(response.message || '撤回失败')
    }
  } catch (error) {
    console.error('撤回失败:', error)
    ElMessage.error('撤回失败，请稍后重试')
  } finally {
    recallSubmitting.value = false
  }
}

// 查看历史记录详情（只读模式）
const viewHistory = (record: any) => {
  // 查看记录时隐藏编辑表单
  hasPendingConfirmation.value = false
  viewingRecord.value = record
  viewingConfirmation.value = true
}

// 关闭查看模式
const closeViewing = () => {
  viewingConfirmation.value = false
  viewingRecord.value = null
}

// 编辑被拒绝的记录（可修改后重新提交）
const editRejectedRecord = (record: any) => {
  rejectedRecordData.value = { ...record }
  rejectionReason.value = record.rejectionReason || ''

  // 将被拒绝记录的数据填充到表单中
  confirmationData.studentName = record.studentName || ''
  confirmationData.studentNumber = record.studentNumber || ''
  confirmationData.gender = record.gender || ''
  confirmationData.grade = record.grade || ''
  confirmationData.major = record.major || ''
  confirmationData.className = record.className || ''
  confirmationData.contactPhone = record.contactPhone || ''
  confirmationData.email = record.email || ''
  confirmationData.companyName = record.companyName || ''
  confirmationData.positionName = record.positionName || ''
  confirmationData.companyAddress = record.companyAddress || ''
  confirmationData.companyPhone = record.companyPhone || ''
  confirmationData.internshipStartTime = record.internshipStartTime || ''
  confirmationData.internshipEndTime = record.internshipEndTime || ''
  confirmationData.internshipDuration = record.internshipDuration || 0
  confirmationData.remark = record.remark || ''

  editingRejectedRecord.value = true
  hasPendingConfirmation.value = true
}

// 关闭编辑被拒绝记录模式
const closeEditingRejected = () => {
  editingRejectedRecord.value = false
  rejectedRecordData.value = null
  rejectionReason.value = ''
  hasPendingConfirmation.value = false
}

// 提交确认
const submitConfirmation = async () => {
  submitting.value = true
  try {
    // 先保存企业信息，获取返回的recordId
    const saveResponse = await request.post('/student/internship-confirmation/save', {
      studentName: confirmationData.studentName,
      studentNumber: confirmationData.studentNumber,
      gender: confirmationData.gender,
      grade: confirmationData.grade,
      major: confirmationData.major,
      className: confirmationData.className,
      contactPhone: confirmationData.contactPhone,
      email: confirmationData.email,
      companyName: confirmationData.companyName,
      companyAddress: confirmationData.companyAddress,
      companyPhone: confirmationData.companyPhone,
      companyId: selectedCompanyId.value,
      positionName: confirmationData.positionName,
      positionId: selectedPositionId.value,
      internshipStartTime: confirmationData.internshipStartTime,
      internshipEndTime: confirmationData.internshipEndTime,
      internshipDuration: confirmationData.internshipDuration,
      remark: confirmationData.remark
    })

    if (saveResponse.code !== 200) {
      ElMessage.error(saveResponse.message || '保存失败')
      submitting.value = false
      return
    }

    // 从保存响应中获取recordId
    const recordId = saveResponse.data?.id || saveResponse.data
    if (!recordId) {
      ElMessage.error('保存记录ID获取失败')
      submitting.value = false
      return
    }

    // 再提交确认，传递recordId
    const response = await request.post('/student/internship-confirmation/submit', {
      recordId: recordId,
      remark: confirmationData.remark
    })

    if (response.code === 200) {
      // 标记刚刚提交过，防止fetchPendingConfirmation重新显示表单
      justSubmitted.value = true
      // 立即隐藏表单
      hasPendingConfirmation.value = false
      showSuccessDialog.value = true
      await fetchConfirmationHistory()
      // 重置提交标志
      justSubmitted.value = false
    } else {
      ElMessage.error(response.message || '提交失败')
    }
  } catch (error) {
    console.error('提交确认失败:', error)
    ElMessage.error('提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  // 先获取面试通过的数据（用于下拉框）
  await fetchApprovedInterviews()
  // 获取历史记录，确保判断状态时有完整数据
  await fetchConfirmationHistory()
  await fetchPendingConfirmation()
  // 获取单位变更申请状态
  await fetchUnitChangeStatus()
  // 获取应聘时间段
  const period = await fetchApplicationPeriod()
  applicationPeriod.value = period
})
</script>

<style scoped>
.internship-confirmation-form-container {
  width: 100%;
  min-height: 100%;
  background: #f1f5f9;
  overflow-y: auto;
  padding: 24px;
}

.page-header {
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  border-radius: 12px;
  padding: 24px 32px;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: white;
  margin: 0 0 8px 0;
}

.page-header p {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
  margin: 0;
}

/* 空状态 */
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

.empty-state .empty-icon {
  font-size: 64px;
  color: #cbd5e1;
  margin-bottom: 16px;
}

.empty-state .empty-title {
  font-size: 18px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 8px;
}

.empty-state .empty-desc {
  font-size: 14px;
  color: #94a3b8;
  text-align: center;
}

/* 信息卡片 */
.info-card {
  margin-bottom: 24px;
  border-radius: 12px;
}

.info-card :deep(.el-card__header) {
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border-bottom: none;
  padding: 16px 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 500;
  color: #1e293b;
}

.header-icon {
  font-size: 20px;
  color: #409eff;
}

/* 信息区域 */
.info-section {
  padding: 20px 0;
  border-bottom: 1px solid #f1f5f9;
}

.info-section:last-of-type {
  border-bottom: none;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 16px;
}

.section-title .el-icon {
  font-size: 18px;
  color: #409eff;
}

.optional-tag {
  font-size: 12px;
  font-weight: 400;
  color: #94a3b8;
  margin-left: 8px;
}

/* 信息网格 */
.info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-label {
  font-size: 13px;
  color: #64748b;
}

.info-value {
  font-size: 15px;
  font-weight: 500;
  color: #1e293b;
}

.info-select {
  width: 100%;
}

.info-value.highlight {
  color: #409eff;
}

.info-value.date {
  color: #1e293b;
}

.info-value.duration {
  color: #67C23A;
  font-weight: 600;
}

/* 备注区域 */
.remark-section {
  padding-top: 20px;
}

.remark-section :deep(.el-textarea__inner) {
  border-radius: 8px;
  padding: 12px 16px;
}

/* 提交区域 */
.submit-section {
  padding: 24px 0 0 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.submit-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #64748b;
  background: #f8fafc;
  padding: 10px 16px;
  border-radius: 8px;
}

.submit-tip .el-icon {
  color: #67C23A;
}

.submit-btn {
  padding: 12px 48px;
  font-size: 16px;
  border-radius: 8px;
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  border: none;
}

.submit-btn:hover {
  background: linear-gradient(135deg, #337ECC 0%, #5DAF34 100%);
}

/* 历史记录卡片 */
.history-card {
  border-radius: 12px;
}

.history-card :deep(.el-card__header) {
  background: linear-gradient(135deg, #fafbfc 0%, #f5f7fa 100%);
  border-bottom: none;
  padding: 16px 20px;
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
  transition: all 0.3s ease;
}

.history-item:hover {
  background: #f0f9ff;
  transform: translateY(-2px);
}

.history-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.history-company {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}

.history-position {
  font-size: 13px;
  color: #64748b;
}

.history-time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}

.history-time .el-icon {
  font-size: 14px;
}

.history-phone {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

.history-phone .el-icon {
  font-size: 14px;
}

.history-confirm-time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #52c41a;
  margin-top: 4px;
  font-weight: 500;
}

.history-confirm-time .el-icon {
  font-size: 14px;
}

.empty-history {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px;
}

.empty-history .empty-icon {
  font-size: 48px;
  color: #cbd5e1;
  margin-bottom: 12px;
}

.empty-history .empty-text {
  font-size: 14px;
  color: #94a3b8;
}

/* 成功对话框 */
.success-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 20px 24px;
}

.success-icon {
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.success-icon .el-icon {
  font-size: 36px;
  color: #67C23A;
}

.success-title {
  font-size: 20px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 8px;
}

.success-desc {
  font-size: 14px;
  color: #64748b;
  text-align: center;
}

.success-btn {
  width: 100%;
  padding: 12px;
  border-radius: 8px;
}

/* 拒绝原因提示 */
.rejection-alert {
  margin-bottom: 16px;
}

.history-rejection {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #f56c6c;
  margin-top: 4px;
}

.history-rejection .el-icon {
  font-size: 14px;
}

.history-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.history-actions .el-tag {
  margin-bottom: 4px;
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

.action-button.edit {
  background: linear-gradient(135deg, #e6a23c 0%, #f5c77e 100%);
  color: white;
  box-shadow:
    0 4px 12px rgba(230, 162, 60, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

.action-button.edit:hover {
  transform: translateY(-3px) scale(1.05);
  box-shadow:
    0 8px 24px rgba(230, 162, 60, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.4);
}

.rejection-text {
  color: #f56c6c;
  font-weight: 500;
}

/* 查看对话框 */
.view-content {
  padding: 16px 0;
}

.view-content .el-descriptions {
  margin-top: 16px;
}

/* 响应式 */
@media screen and (max-width: 768px) {
  .info-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .history-actions {
    flex-direction: row;
    align-items: center;
  }
}

@media screen and (max-width: 480px) {
  .info-grid {
    grid-template-columns: 1fr;
  }

  .page-header {
    padding: 20px;
  }

  .page-header h2 {
    font-size: 24px;
  }

  .history-actions {
    flex-direction: column;
    align-items: flex-end;
  }
}

/* 单位变更按钮样式 */
.action-button.change {
  background: linear-gradient(135deg, #E6A23C 0%, #F5C77E 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(230, 162, 60, 0.25);
}

.action-button.change:hover {
  transform: translateY(-3px) scale(1.05);
  box-shadow: 0 8px 24px rgba(230, 162, 60, 0.35);
}

.action-button.pending {
  background: linear-gradient(135deg, #909399 0%, #C0C4CC 100%);
  color: white;
  cursor: not-allowed;
  opacity: 0.7;
}

.action-button.reapply {
  background: linear-gradient(135deg, #F56C6C 0%, #F89A9A 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(245, 108, 108, 0.25);
}

.action-button.reapply:hover {
  transform: translateY(-3px) scale(1.05);
  box-shadow: 0 8px 24px rgba(245, 108, 108, 0.35);
}

/* 驳回提示 */
.reject-alert {
  margin: 16px 0;
}

/* 撤回按钮样式 */
.action-button.recall {
  background: linear-gradient(135deg, #909399 0%, #C0C4CC 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(144, 147, 153, 0.25);
}

.action-button.recall:hover {
  transform: translateY(-3px) scale(1.05);
  box-shadow: 0 8px 24px rgba(144, 147, 153, 0.35);
}

/* 撤回说明样式 */
.recall-notice {
  margin-bottom: 20px;
}

.recall-notice p {
  margin: 4px 0;
  line-height: 1.6;
}

.recall-form {
  margin-top: 16px;
}

/* 上传区域 */
.upload-items {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.upload-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.upload-item label {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.required {
  color: #f56c6c;
  margin-left: 4px;
}

.uploaded-file {
  margin-top: 8px;
}

.preview-image {
  width: 100px;
  height: 100px;
  object-fit: cover;
  border-radius: 4px;
  cursor: pointer;
  border: 1px solid #ddd;
}

/* 表单项 */
.form-item {
  margin-bottom: 16px;
}

.form-item label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 8px;
}

.info-value.highlight {
  color: #E6A23C;
  font-weight: 600;
}
</style>
