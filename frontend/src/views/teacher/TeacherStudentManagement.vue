<template>
  <div class="student-management-container">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">{{ pageTitle }}</h1>
        <p class="page-description">{{ pageDescription }}</p>
      </div>
    </div>

    <el-card class="stats-card" shadow="never" v-loading="statsLoading">
      <div class="stats-container">
        <div class="stat-item" v-if="!showInternshipFields">
          <div class="stat-value">{{ statistics.classCount }}</div>
          <div class="stat-label">负责班级</div>
        </div>
        <div class="stat-item">
          <div class="stat-value">{{ statistics.studentCount }}</div>
          <div class="stat-label">学生总数</div>
        </div>
        <div class="stat-item" v-if="showInternshipFields">
          <div class="stat-value">{{ statistics.noOfferCount || 0 }}</div>
          <div class="stat-label">无offer</div>
        </div>
        <div class="stat-item" v-if="showInternshipFields">
          <div class="stat-value">{{ statistics.pendingCount || 0 }}</div>
          <div class="stat-label">待确认</div>
        </div>
        <div class="stat-item" v-if="showInternshipFields">
          <div class="stat-value">{{ statistics.confirmedCount || 0 }}</div>
          <div class="stat-label">已确定</div>
        </div>
        <div class="stat-item" v-if="showInternshipFields">
          <div class="stat-value">{{ statistics.interningCount || 0 }}</div>
          <div class="stat-label">实习中</div>
        </div>
        <div class="stat-item" v-if="showInternshipFields">
          <div class="stat-value">{{ statistics.finishedCount || 0 }}</div>
          <div class="stat-label">已结束</div>
        </div>
        <div class="stat-item" v-if="showInternshipFields">
          <div class="stat-value">{{ statistics.interruptedCount || 0 }}</div>
          <div class="stat-label">已中断</div>
        </div>
        <div class="stat-item" v-if="showInternshipFields">
          <div class="stat-value">{{ statistics.delayedCount || 0 }}</div>
          <div class="stat-label">延期</div>
        </div>
      </div>
    </el-card>

    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="filters" class="search-form" @keyup.enter="applyFilters">
        <div class="search-row">
          <el-form-item label="班级">
            <el-select v-model="filters.class" placeholder="全部班级" clearable class="select-input">
              <el-option label="全部班级" value=""></el-option>
              <!-- 辅导员专属学生管理页面：显示负责的班级 -->
              <el-option v-for="cls in myClasses" :key="cls.id" :label="cls.name" :value="cls.name || cls.id || ''" v-if="isStudentManagementPage"></el-option>
              <!-- 学生状态监控页面：显示全部班级 -->
              <el-option v-for="cls in classList" :key="cls.id" :label="cls.name" :value="cls.name || cls.id || ''" v-else></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="实习单位" v-if="showInternshipFields">
            <el-select v-model="filters.company" placeholder="全部单位" clearable class="select-input">
              <el-option label="全部单位" value=""></el-option>
              <el-option v-for="company in companyList" :key="company.id || company.companyName" :label="company.companyName || company.name" :value="company.companyName || company.name || company.id || ''"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="实习状态" v-if="showInternshipFields">
            <el-select v-model="filters.status" placeholder="全部状态" clearable class="select-input">
              <el-option label="全部状态" value=""></el-option>
              <el-option label="无offer" value="0"></el-option>
              <el-option label="待确认" value="1"></el-option>
              <el-option label="已确定" value="2"></el-option>
              <el-option label="实习中" value="3"></el-option>
              <el-option label="已结束" value="4"></el-option>
              <el-option label="已中断" value="5"></el-option>
              <el-option label="延期" value="6"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="专业" v-if="showInternshipFields">
            <el-select v-model="filters.major" placeholder="全部专业" clearable class="select-input">
              <el-option label="全部专业" value=""></el-option>
              <el-option v-for="major in majorList" :key="major.id" :label="major.name" :value="major.name || major.id || ''"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="年级" v-if="showInternshipFields">
            <el-select v-model="filters.grade" placeholder="全部年级" clearable class="select-input">
              <el-option label="全部年级" value=""></el-option>
              <el-option v-for="grade in gradeList" :key="grade" :label="grade + '级'" :value="grade + '级'"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="搜索">
            <el-input
              v-model="filters.search"
              :placeholder="isCounselor ? '输入学生姓名搜索' : '搜索学生姓名或学号'"
              clearable
              class="search-input"
              @keyup.enter="applyFilters"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </el-form-item>
        </div>
        <div class="search-actions-row">
          <el-button type="primary" @click="applyFilters" class="action-btn primary">
            <el-icon><Search /></el-icon>&nbsp;查询
          </el-button>
          <el-button type="warning" @click="resetFilters" class="action-btn warning">
            <el-icon><Refresh /></el-icon>&nbsp;重置
          </el-button>
          <el-button type="primary" @click="oneClickReminder" class="action-btn primary" v-if="showInternshipFields">
            <el-icon><Bell /></el-icon>&nbsp;一键提醒
          </el-button>
          <el-button type="success" @click="exportSelectedStudentProfiles" class="action-btn success" v-if="showInternshipFields">
            <el-icon><Download /></el-icon>&nbsp;导出已选择
          </el-button>
          <el-button type="warning" @click="exportBatchData" class="action-btn warning" v-if="showInternshipFields">
            <el-icon><FolderOpened /></el-icon>&nbsp;批量导出
          </el-button>
        </div>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="table-header">
        <h3 class="table-title">学生列表</h3>
        <div class="table-actions">
          <span class="total-count">共 {{ students.length }} 条记录</span>
        </div>
      </div>
      <el-table 
        v-loading="loading" 
        :data="students" 
        style="width: 100%" 
        @selection-change="handleSelectionChange" 
        border
        fit
        class="data-table"
      >
        <el-table-column type="selection" width="55" align="center" v-if="showInternshipFields"></el-table-column>
        <el-table-column prop="studentId" label="学号" width="120" align="center"></el-table-column>
        <el-table-column prop="name" label="姓名" width="100" align="center"></el-table-column>
        <el-table-column label="状态" width="100" align="center" v-if="showInternshipFields">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.internshipStatus)" size="small" class="status-tag">
              {{ getStatusText(scope.row.internshipStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="gender" label="性别" align="center" width="80">
          <template #default="scope">
            <span>{{ scope.row.gender === 1 ? '男' : '女' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="grade" label="年级" align="center" width="80" />
        <el-table-column prop="className" label="班级" min-width="120" show-overflow-tooltip></el-table-column>
        <el-table-column prop="major" label="专业" min-width="150" show-overflow-tooltip></el-table-column>
        <el-table-column prop="company" label="实习单位" min-width="180" show-overflow-tooltip v-if="showInternshipFields">
          <template #default="scope">
            {{ scope.row.company || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" align="center" width="80" v-if="!showInternshipFields">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'" size="small">
              {{ scope.row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" :width="showInternshipFields ? 350 : 180" fixed="right" align="center">
          <template #default="scope">
            <div class="action-buttons">
              <el-tooltip content="查看实习心得" placement="top" v-if="!showInternshipFields">
                <el-button size="small" type="primary" @click="viewReports(scope.row)" class="table-btn view">
                  <el-icon><Document /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="AI分析状态" placement="top" v-if="!showInternshipFields">
                <el-button size="small" type="success" @click="checkAIStatus(scope.row)" class="table-btn ai">
                  <el-icon><MagicStick /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="查看详情" placement="top" v-if="showInternshipFields">
                <el-button size="small" type="primary" @click="viewStudentDetail(scope.row)" class="table-btn view">
                  <el-icon><View /></el-icon>
                  查看
                </el-button>
              </el-tooltip>
              <el-tooltip content="下载申请表" placement="top" v-if="showInternshipFields">
                <el-button size="small" type="primary" @click="downloadApplicationForm(scope.row)" class="table-btn download">
                  <el-icon><Download /></el-icon>
                  下载
                </el-button>
              </el-tooltip>
              <el-tooltip content="导出档案" placement="top" v-if="showInternshipFields">
                <el-button size="small" type="success" @click="exportStudentProfile(scope.row.id)" class="table-btn export">
                  <el-icon><Download /></el-icon>
                  导出
                </el-button>
              </el-tooltip>
              <el-tooltip content="提醒" placement="top" v-if="showInternshipFields">
                <el-button size="small" type="warning" @click="remindStudent(scope.row.id)" class="table-btn remind">
                  <el-icon><Bell /></el-icon>
                  提醒
                </el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          :current-page="currentPage"
          :page-sizes="[10, 20, 50]"
          :page-size="pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          class="custom-pagination"
        />
      </div>
    </el-card>

    <el-dialog v-model="showReminderDialog" title="发送提醒" width="500px">
      <el-form label-width="100px">
        <el-form-item label="提醒模板">
          <el-select v-model="reminderForm.template" placeholder="选择提醒模板" @change="handleTemplateChange" class="reminder-select">
            <el-option v-for="t in reminderTemplates" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="提醒内容">
          <el-input v-model="reminderForm.content" type="textarea" :rows="4" placeholder="请输入提醒内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showReminderDialog = false">取消</el-button>
        <el-button type="primary" @click="sendReminder">发送</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reportsDialogVisible" :title="`${currentStudent?.name} - 实习心得`" width="800px">
      <el-table :data="studentReports" border style="width: 100%" v-loading="reportsLoading">
        <el-table-column prop="submitTime" label="提交时间" align="center" width="180">
          <template #default="scope">
            <span>{{ formatDate(scope.row.submitTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="period" label="阶段" align="center" width="80" />
        <el-table-column prop="reportContent" label="内容摘要" align="center" min-width="200">
          <template #default="scope">
            <span>{{ scope.row.reportContent?.substring(0, 50) }}...</span>
          </template>
        </el-table-column>
        <el-table-column label="AI分析" align="center" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.hasAIAnalysis ? 'success' : 'info'" size="small">
              {{ scope.row.hasAIAnalysis ? '已分析' : '未分析' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="120">
          <template #default="scope">
            <el-button size="small" type="primary" @click="viewReportDetail(scope.row)">
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="reportsDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="aiStatusDialogVisible" :title="`${currentStudent?.name} - AI分析状态`" width="600px">
      <div class="ai-status-content" v-loading="aiStatusLoading">
        <div v-if="aiStatus" class="status-info">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="AI评分启用">
              <el-tag :type="aiStatus.aiScoringEnabled ? 'success' : 'danger'">
                {{ aiStatus.aiScoringEnabled ? '已启用' : '未启用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="自动触发">
              <el-tag :type="aiStatus.autoTriggerEnabled ? 'success' : 'info'">
                {{ aiStatus.autoTriggerEnabled ? '已启用' : '未启用' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
          <div class="ai-tip" v-if="!aiStatus.aiScoringEnabled">
            <el-alert type="warning" :closable="false" show-icon>
              AI评分功能未启用，学生提交实习心得后不会自动进行AI分析
            </el-alert>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="aiStatusDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <div v-if="showExportModal" class="modal-overlay" @click.self="showExportModal = false">
      <div class="modal-content">
        <div class="modal-header">
          <h3>批量导出数据</h3>
          <button class="close-btn" @click="showExportModal = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>导出范围：</label>
            <div class="radio-group">
              <label>
                <input type="radio" v-model="exportScope" value="class" />
                按班级导出
              </label>
              <label>
                <input type="radio" v-model="exportScope" value="grade" />
                按年级导出
              </label>
              <label>
                <input type="radio" v-model="exportScope" value="college" />
                按学院导出
              </label>
            </div>
          </div>
          <div class="form-group" v-if="exportScope === 'class'">
            <label>选择班级：</label>
            <select v-model="exportClass">
              <option value="">请选择班级</option>
              <option v-for="cls in classList" :key="cls.id" :value="cls.name">{{ cls.name }}</option>
            </select>
          </div>
          <div class="form-group" v-if="exportScope === 'grade'">
            <label>选择年级：</label>
            <select v-model="exportGrade">
              <option value="">请选择年级</option>
              <option v-for="grade in gradeList" :key="grade" :value="grade + '级'">{{ grade }}级</option>
            </select>
          </div>
          <div class="form-group">
            <label>导出格式：</label>
            <select v-model="exportFormat">
              <option value="excel">Excel</option>
            </select>
          </div>
        </div>
        <div class="modal-footer">
          <button class="cancel-btn" @click="showExportModal = false">取消</button>
          <button class="submit-btn" @click="confirmExport">确认导出</button>
        </div>
      </div>
    </div>

    <div v-if="showStudentDetailModal" class="modal-overlay" @click.self="showStudentDetailModal = false">
      <div class="modal-content student-detail-modal">
        <div class="modal-header">
          <h3>{{ currentStudent?.name }} 学生详情</h3>
          <button class="close-btn" @click="showStudentDetailModal = false">×</button>
        </div>
        <div class="modal-body student-detail-body">
          <div v-if="currentStudent" class="student-detail-content">
            <div class="detail-left">
              <div class="detail-section basic-info-section">
                <h4>基本信息</h4>
                <div class="detail-grid">
                  <div class="detail-item">
                    <label>学号：</label>
                    <span>{{ currentStudent.studentId }}</span>
                  </div>
                  <div class="detail-item">
                    <label>姓名：</label>
                    <span>{{ currentStudent.name }}</span>
                  </div>
                  <div class="detail-item">
                    <label>性别：</label>
                    <span>{{ currentStudent.gender === 1 ? '男' : currentStudent.gender === 2 ? '女' : '-' }}</span>
                  </div>
                  <div class="detail-item">
                    <label>年级：</label>
                    <span>{{ currentStudent.grade }}</span>
                  </div>
                  <div class="detail-item">
                    <label>学院：</label>
                    <span>{{ currentStudent.department || '-' }}</span>
                  </div>
                  <div class="detail-item">
                    <label>专业：</label>
                    <span>{{ currentStudent.major }}</span>
                  </div>
                  <div class="detail-item">
                    <label>班级：</label>
                    <span>{{ currentStudent.className }}</span>
                  </div>
                  <div class="detail-item">
                    <label>手机号：</label>
                    <span>{{ currentStudent.phone || '-' }}</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="detail-right">
              <div class="detail-section internship-info-section">
                <h4>实习信息</h4>
                <div class="detail-grid">
                  <div class="detail-item">
                    <label>实习单位：</label>
                    <span>{{ currentStudent.company || '未确定' }}</span>
                  </div>
                  <div class="detail-item">
                    <label>实习状态：</label>
                    <span class="status-badge" :class="getStatusClass(currentStudent.status)">{{ getStatusText(currentStudent.status) }}</span>
                  </div>
                  <div class="detail-item">
                    <label>实习开始日期：</label>
                    <span>{{ currentStudent.internshipStartDate || '未开始' }}</span>
                  </div>
                  <div class="detail-item">
                    <label>实习结束日期：</label>
                    <span>{{ currentStudent.internshipEndDate || '未确定' }}</span>
                  </div>
                </div>
              </div>

              <div class="detail-section button-section">
                <button class="export-btn" @click="exportStudentProfile(currentStudent?.id)">导出档案</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

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
          <button class="submit-btn" @click="showFeedback = false">确定</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Bell, Download, FolderOpened, View, Document, MagicStick } from '@element-plus/icons-vue'
import { studentApi, type StudentUser, type StudentFilters, type ClassItem, type MajorItem } from '@/api/teacherStudent'
import {
  getTeacherClasses,
  getCounselorStudents,
  getCounselorStatistics,
  type TeacherClass
} from '@/api/teacherClass'
import { checkAiScoringEnabled } from '@/api/counselorAIAnalysis'
import request from '@/utils/request'

const router = useRouter()

const teacherType = ref<string>('')
const isCounselor = computed(() => teacherType.value === 'COUNSELOR')

// 根据路由名称显示不同的页面标题
const pageTitle = computed(() => {
  const currentRoute = router.currentRoute.value.name
  if (currentRoute === 'teacherStudentTracking') {
    return '学生状态监控'
  }
  return '学生管理'
})
const pageDescription = computed(() => {
  const currentRoute = router.currentRoute.value.name
  if (currentRoute === 'teacherStudentTracking') {
    // 辅导员身份：显示"监控和管理负责班级学生的实习状态信息"
    if (isCounselor.value) {
      return '监控和管理负责班级学生的实习状态信息'
    }
    return '监控和管理学生的实习状态信息'
  }
  return '管理您负责班级的学生信息'
})

// 是否显示实习相关字段（企业确认状态、实习单位等）
const showInternshipFields = computed(() => {
  const currentRoute = router.currentRoute.value.name
  return currentRoute === 'teacherStudentTracking'
})

// 是否是辅导员专属的学生管理页面（只显示该辅导员负责的学生）
const isStudentManagementPage = computed(() => {
  const currentRoute = router.currentRoute.value.name
  return currentRoute === 'teacherStudents'
})

const loading = ref(false)
const statsLoading = ref(false)
const reportsLoading = ref(false)
const aiStatusLoading = ref(false)

const counselorId = ref<number>(0)
const myClasses = ref<TeacherClass[]>([])
const classList = ref<ClassItem[]>([])
const majorList = ref<MajorItem[]>([])
const gradeList = ref<number[]>([])
const companyList = ref<any[]>([])

const students = ref<StudentUser[]>([])
const studentReports = ref<any[]>([])
const currentStudent = ref<any>(null)
const aiStatus = ref<any>(null)
const selectedStudents = ref<StudentUser[]>([])

const filters = ref<StudentFilters>({
  class: '',
  company: '',
  status: '',
  major: '',
  grade: '',
  search: ''
})

const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const showExportModal = ref(false)
const showStudentDetailModal = ref(false)
const showFeedback = ref(false)
const exportScope = ref('class')
const exportClass = ref('')
const exportGrade = ref('')
const exportFormat = ref('excel')
const reportsDialogVisible = ref(false)
const aiStatusDialogVisible = ref(false)

// 提醒相关
const showReminderDialog = ref(false)
const reminderForm = ref({
  content: '',
  template: ''
})
const currentRemindStudentId = ref<number | null>(null)

// 固定提醒模板
const reminderTemplates = [
  { value: '1', label: '请尽快完善实习登记信息' },
  { value: '2', label: '请尽快提交实习周志' },
  { value: '3', label: '实习确认表待审核，请及时处理' },
  { value: '4', label: '请注意实习时间安排' },
  { value: '5', label: '其他（自定义输入）' }
]

const handleTemplateChange = (value: string) => {
  if (value !== '5') {
    const template = reminderTemplates.find(t => t.value === value)
    reminderForm.value.content = template?.label || ''
  } else {
    reminderForm.value.content = ''
  }
}

const openReminderDialog = (studentId: number) => {
  currentRemindStudentId.value = studentId
  reminderForm.value = { content: '', template: '' }
  showReminderDialog.value = true
}

const sendReminder = async () => {
  if (!reminderForm.value.content.trim()) {
    ElMessage.warning('请输入提醒内容')
    return
  }
  try {
    const teacherId = counselorId.value
    await request.post('/teacher/reminder/send', null, {
      params: {
        studentId: currentRemindStudentId.value,
        teacherId: teacherId,
        content: reminderForm.value.content
      }
    })
    ElMessage.success('提醒已发送')
    showReminderDialog.value = false
  } catch (error) {
    console.error('发送提醒失败:', error)
    ElMessage.error('发送提醒失败')
  }
}

const feedbackTitle = ref('')
const feedbackMessage = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const feedbackIcon = computed(() => feedbackType.value === 'success' ? '✓' : '✗')

const statistics = ref({
  classCount: 0,
  studentCount: 0,
  confirmedCount: 0,
  offerCount: 0,
  noOfferCount: 0,
  pendingCount: 0,
  interningCount: 0,
  finishedCount: 0,
  interruptedCount: 0,
  delayedCount: 0
})

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const loadMyClasses = async () => {
  try {
    // 只有counselorId有效时才调用
    if (!counselorId.value || counselorId.value <= 0) {
      console.warn(' counselorId 无效，跳过加载班级列表')
      return
    }
    const response = await getTeacherClasses(counselorId.value)
    if (response.data) {
      myClasses.value = response.data
    }
  } catch (error) {
    console.error('加载班级列表失败:', error)
  }
}

const loadStudents = async () => {
  loading.value = true
  try {
    // 如果是辅导员访问，只显示该辅导员负责的学生
    if (isCounselor.value && counselorId.value > 0) {
      const response = await getCounselorStudents(
        counselorId.value,
        filters.value.search || undefined,
        filters.value.class ? myClasses.value.find(c => c.name === filters.value.class)?.id : undefined,
        filters.value.major || undefined,
        filters.value.grade ? filters.value.grade.replace('级', '') : undefined,
        filters.value.status ? getStatusValue(filters.value.status) : undefined,
        filters.value.company || undefined
      )
      if (response.data) {
        students.value = response.data.map((item: any) => ({
          id: item.id,
          studentId: item.studentUserId || item.studentId,
          name: item.name,
          className: item.className || '',
          majorId: item.majorId,
          major: item.majorName || (typeof item.major === 'string' ? item.major : ''),
          grade: item.grade ? `${item.grade}级` : '',
          gender: item.gender === 1 ? '男' : item.gender === 2 ? '女' : item.gender,
          phone: item.phone || '',
          email: item.email || '',
          status: item.status !== undefined ? item.status : 0,
          companyId: item.companyId,
          company: item.companyName || item.company || '',
          internshipStatus: item.internshipStatus,
          positionId: item.positionId,
          positionName: item.positionName
        }))
        total.value = response.data.length
      }
    } else {
      // 非辅导员访问（管理员等），显示全部学生
      const response = await studentApi.getStudentList({
        page: currentPage.value,
        pageSize: pageSize.value,
        name: filters.value.search,
        grade: filters.value.grade,
        major: filters.value.major,
        className: filters.value.class,
        status: filters.value.status ? getStatusValue(filters.value.status) : undefined,
        companyName: filters.value.company
      })
      const rawData = response?.rows || []
      students.value = rawData.map((item: any) => ({
        id: item.student?.id || item.id,
        studentId: item.student?.studentUserId || item.studentId,
        name: item.student?.name || item.name,
        className: item.student?.className || item.className,
        majorId: item.student?.majorId || item.majorId,
        major: item.student?.major?.name || item.major?.name || (typeof item.major === 'string' ? item.major : ''),
        grade: item.student?.grade ? `${item.student.grade}级` : item.grade,
        gender: item.student?.gender === 1 ? '男' : item.student?.gender === 2 ? '女' : item.gender,
        phone: item.student?.phone || item.phone,
        email: item.student?.email || item.email,
        status: item.status !== undefined ? item.status : 0,
        companyId: item.companyId || item.company_id,
        company: item.company?.companyName || item.company || '',
        positionId: item.positionId || item.position_id,
        positionName: item.position?.positionName || item.positionName,
        progress: item.progress || 0,
        birthday: item.student?.birthday || item.birthday,
        internshipStartDate: item.internshipStartDate || item.internship_start_time,
        internshipEndDate: item.internshipEndDate || item.internship_end_time,
        supervisorName: item.supervisorName,
        supervisorPhone: item.supervisorPhone,
        reportCount: item.reportCount || 0,
        attendanceRate: item.attendanceRate || 0,
        internshipLogCount: item.internshipLogCount || 0,
        evaluationScore: item.evaluationScore || 0
      }))
      total.value = response?.total || 0
    }
  } catch (error) {
    console.error('加载学生列表失败:', error)
    ElMessage.error('加载学生列表失败')
  } finally {
    loading.value = false
  }
}

const loadStatistics = async () => {
  statsLoading.value = true
  try {
    // 如果是辅导员访问，加载辅导员的统计数据
    if (isCounselor.value && counselorId.value > 0) {
      const response = await getCounselorStatistics(counselorId.value)
      if (response.data) {
        statistics.value = response.data
      }
    } else {
      // 非辅导员访问（系室教师、院系教师等），获取全部学生的统计数据
      const response = await studentApi.getStatistics()
      // response.data 是后端返回的 { code, message, data } 结构，actualData 才是统计对象
      const actualData = response?.data?.data || response?.data || response || {}
      statistics.value.studentCount = actualData.studentCount || 0
      statistics.value.noOfferCount = actualData.noOfferCount || 0
      statistics.value.pendingCount = actualData.pendingCount || 0
      statistics.value.confirmedCount = actualData.confirmedCount || 0
      statistics.value.interningCount = actualData.interningCount || 0
      statistics.value.finishedCount = actualData.finishedCount || 0
      statistics.value.interruptedCount = actualData.interruptedCount || 0
      statistics.value.delayedCount = actualData.delayedCount || 0
      // 兼容旧字段
      statistics.value.offerCount = (actualData.pendingCount || 0) + (actualData.confirmedCount || 0)
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  } finally {
    statsLoading.value = false
  }
}

const loadClassList = async () => {
  try {
    // 如果是辅导员身份且有负责的班级，使用辅导员班级列表
    if (isCounselor.value && myClasses.value.length > 0) {
      classList.value = myClasses.value.map((cls: TeacherClass) => ({
        id: cls.id,
        name: cls.name
      }))
    } else {
      const response = await studentApi.getClasses()
      classList.value = response || []
    }
  } catch (error) {
    console.error('加载班级列表失败:', error)
  }
}

const loadMajorList = async () => {
  try {
    // 如果是辅导员身份且有负责的班级，从班级信息提取专业列表
    if (isCounselor.value && myClasses.value.length > 0) {
      const majorMap = new Map<number, string>()
      myClasses.value.forEach((cls: TeacherClass) => {
        if (cls.majorId && cls.majorName) {
          majorMap.set(cls.majorId, cls.majorName)
        }
      })
      majorList.value = Array.from(majorMap.entries()).map(([id, name]) => ({ id, name }))
    } else {
      const response = await studentApi.getMajors()
      majorList.value = response || []
    }
  } catch (error) {
    console.error('加载专业列表失败:', error)
  }
}

const loadGradeList = async () => {
  try {
    // 如果是辅导员身份且有负责的班级，从班级信息提取年级列表
    if (isCounselor.value && myClasses.value.length > 0) {
      const gradeSet = new Set<number>()
      myClasses.value.forEach((cls: TeacherClass) => {
        if (cls.grade) {
          gradeSet.add(cls.grade)
        }
      })
      gradeList.value = Array.from(gradeSet).sort((a, b) => b - a)
    } else {
      const response = await studentApi.getGrades()
      gradeList.value = response || []
    }
  } catch (error) {
    console.error('加载年级列表失败:', error)
  }
}

const loadCompanyList = async () => {
  try {
    const response = await studentApi.getCompanies()
    companyList.value = response || []
  } catch (error) {
    console.error('加载公司列表失败:', error)
  }
}

const applyFilters = () => {
  currentPage.value = 1
  loadStudents()
}

const resetFilters = () => {
  filters.value = {
    class: '',
    company: '',
    status: '',
    major: '',
    grade: '',
    search: ''
  }
  currentPage.value = 1
  loadStudents()
}

const handleSelectionChange = (selection: StudentUser[]) => {
  selectedStudents.value = selection
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  loadStudents()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  loadStudents()
}

const viewReports = async (student: any) => {
  currentStudent.value = student
  reportsDialogVisible.value = true
  reportsLoading.value = true
  try {
    const response = await request.get(`/internship/reports/student/${student.id}`)
    if (response.data) {
      studentReports.value = response.data.map((r: any) => ({
        ...r,
        hasAIAnalysis: false
      }))
    }
  } catch (error) {
    console.error('加载实习心得失败:', error)
    ElMessage.error('加载实习心得失败')
  } finally {
    reportsLoading.value = false
  }
}

const viewReportDetail = (report: any) => {
  router.push(`/teacher/reflection-evaluation/${report.id}`)
}

const checkAIStatus = async (student: any) => {
  currentStudent.value = student
  aiStatusDialogVisible.value = true
  aiStatusLoading.value = true
  try {
    const response = await checkAiScoringEnabled(counselorId.value)
    if (response.data) {
      aiStatus.value = response.data
    }
  } catch (error) {
    console.error('检查AI状态失败:', error)
    ElMessage.error('检查AI状态失败')
  } finally {
    aiStatusLoading.value = false
  }
}

const viewStudentDetail = (student: any) => {
  currentStudent.value = student
  showStudentDetailModal.value = true
}

const getStatusValue = (status: string): number => {
  switch (status) {
    case '0': return 0  // 无offer
    case '1': return 1  // 待确认
    case '2': return 2  // 已确定
    case '3': return 3  // 实习中
    case '4': return 4  // 已结束
    case '5': return 5  // 已中断
    case '6': return 6  // 延期
    case 'confirmed': return 2
    case 'offer': return 1
    case 'noOffer': return 0
    case 'delayed': return 6
    default: return 0
  }
}

const getStatusText = (status: number | string): string => {
  const statusNum = typeof status === 'string' ? getStatusValue(status) : status
  switch (statusNum) {
    case 2: return '已确定'
    case 1: return '待确认'
    case 0: return '无offer'
    case 5: return '已中断'
    case 3: return '实习中'
    case 4: return '已结束'
    case 6: return '延期'
    default: return '未知'
  }
}

const getStatusTagType = (status: number | string): string => {
  const statusNum = typeof status === 'string' ? getStatusValue(status) : status
  switch (statusNum) {
    case 2: return 'success'  // 已确定实习 - 绿色
    case 1: return 'warning'   // 待确认 - 黄色
    case 0: return 'info'      // 无offer - 灰色
    case 5: return 'warning'    // 延期 - 黄色
    case 3: return ''           // 进行中 - 默认
    case 4: return 'success'    // 已完成 - 绿色
    default: return 'info'
  }
}

const getStatusClass = (status: number | string): string => {
  const statusNum = typeof status === 'string' ? getStatusValue(status) : status
  switch (statusNum) {
    case 2: return 'status-confirmed'
    case 1: return 'status-offer'
    case 0: return 'status-no-offer'
    case 5: return 'status-delayed'
    case 3: return 'status-in-progress'
    case 4: return 'status-completed'
    default: return 'status-unknown'
  }
}

const downloadApplicationForm = async (row: any): Promise<void> => {
  try {
    ElMessage({ message: '正在生成实习确认申请表...', type: 'info' })

    const currentRole = localStorage.getItem('current_role') || 'ROLE_TEACHER'
    const rolePrefixes: Record<string, string> = {
      'ROLE_STUDENT': 'student_',
      'ROLE_TEACHER': 'teacher_',
      'ROLE_ADMIN': 'admin_',
      'ROLE_COMPANY': 'company_'
    }
    const rolePrefix = rolePrefixes[currentRole] || 'teacher_'
    const accessToken = localStorage.getItem(`${rolePrefix}accessToken_${currentRole}`) ||
                        localStorage.getItem(`${rolePrefix}accessToken`) ||
                        localStorage.getItem('teacher_accessToken_ROLE_TEACHER')

    const url = `/api/admin/internship-status/download-application/${row.internshipStatusId || row.id}`
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${accessToken}`
      }
    })

    if (!response.ok) {
      throw new Error('下载失败')
    }

    const blob = await response.blob()
    const downloadUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = `${row.studentUserId || ''}_${row.name || ''}_实习确认申请表.docx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(downloadUrl)

    ElMessage.success('下载成功')
  } catch (error: unknown) {
    console.error('下载失败:', error)
    const errorMessage = error instanceof Error ? error.message : '未知错误'
    ElMessage.error('下载失败：' + errorMessage)
  }
}

const oneClickReminder = () => {
  showFeedbackMessage('一键提醒功能开发中', 'info')
}

const exportSelectedStudentProfiles = async () => {
  if (selectedStudents.value.length === 0) {
    ElMessage.warning('请先选择学生')
    return
  }
  try {
    ElMessage({ message: `正在导出 ${selectedStudents.value.length} 名学生的数据...`, type: 'info' })
    const studentIds = selectedStudents.value.map(s => s.id).join(',')
    const timestamp = new Date().getTime()

    // 获取 token
    const currentRole = localStorage.getItem('current_role') || 'ROLE_TEACHER'
    const rolePrefixes: Record<string, string> = {
      'ROLE_STUDENT': 'student_',
      'ROLE_TEACHER': 'teacher_',
      'ROLE_TEACHER_COUNSELOR': 'teacher_',
      'ROLE_TEACHER_COLLEGE': 'teacher_',
      'ROLE_TEACHER_DEPARTMENT': 'teacher_',
      'ROLE_ADMIN': 'admin_',
      'ROLE_COMPANY': 'company_'
    }
    const rolePrefix = rolePrefixes[currentRole] || 'teacher_'
    const token = localStorage.getItem(`${rolePrefix}accessToken_${currentRole}`) ||
                  localStorage.getItem(`${rolePrefix}accessToken`) ||
                  localStorage.getItem('teacher_accessToken_ROLE_TEACHER')

    const url = `/api/teacher/internship-status/export?studentIds=${studentIds}&t=${timestamp}`
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })

    if (!response.ok) {
      throw new Error('导出失败')
    }

    const blob = await response.blob()
    const downloadUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = `学生档案_${new Date().getTime()}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(downloadUrl)

    ElMessage.success('导出成功')
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败')
  }
}

const exportBatchData = () => {
  exportModalTitle.value = '批量导出数据'
  exportModalType.value = 'batch'
  exportScope.value = 'college'
  exportClass.value = ''
  exportGrade.value = ''
  showExportModal.value = true
}

const confirmExport = async () => {
  if (exportScope.value === 'class' && !exportClass.value) {
    ElMessage.warning('请选择班级')
    return
  }
  if (exportScope.value === 'grade' && !exportGrade.value) {
    ElMessage.warning('请选择年级')
    return
  }
  try {
    ElMessage({ message: '正在导出数据...', type: 'info' })
    const timestamp = new Date().getTime()

    // 获取 token
    const currentRole = localStorage.getItem('current_role') || 'ROLE_TEACHER'
    const rolePrefixes: Record<string, string> = {
      'ROLE_STUDENT': 'student_',
      'ROLE_TEACHER': 'teacher_',
      'ROLE_TEACHER_COUNSELOR': 'teacher_',
      'ROLE_TEACHER_COLLEGE': 'teacher_',
      'ROLE_TEACHER_DEPARTMENT': 'teacher_',
      'ROLE_ADMIN': 'admin_',
      'ROLE_COMPANY': 'company_'
    }
    const rolePrefix = rolePrefixes[currentRole] || 'teacher_'
    const token = localStorage.getItem(`${rolePrefix}accessToken_${currentRole}`) ||
                  localStorage.getItem(`${rolePrefix}accessToken`) ||
                  localStorage.getItem('teacher_accessToken_ROLE_TEACHER')

    let url = `/api/teacher/internship-status/export/batch?scope=${exportScope.value}&t=${timestamp}`
    if (exportScope.value === 'class') {
      url += `&className=${encodeURIComponent(exportClass.value)}`
    } else if (exportScope.value === 'grade') {
      url += `&grade=${encodeURIComponent(exportGrade.value)}`
    }

    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })

    if (!response.ok) {
      throw new Error('导出失败')
    }

    const blob = await response.blob()
    const downloadUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = `批量导出_${new Date().getTime()}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(downloadUrl)

    showExportModal.value = false
    ElMessage.success('导出成功')
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败')
  }
}

const exportStudentProfile = async (studentId: number) => {
  try {
    ElMessage({ message: '正在导出学生档案...', type: 'info' })
    const timestamp = new Date().getTime()

    // 获取 token
    const currentRole = localStorage.getItem('current_role') || 'ROLE_TEACHER'
    const rolePrefixes: Record<string, string> = {
      'ROLE_STUDENT': 'student_',
      'ROLE_TEACHER': 'teacher_',
      'ROLE_TEACHER_COUNSELOR': 'teacher_',
      'ROLE_TEACHER_COLLEGE': 'teacher_',
      'ROLE_TEACHER_DEPARTMENT': 'teacher_',
      'ROLE_ADMIN': 'admin_',
      'ROLE_COMPANY': 'company_'
    }
    const rolePrefix = rolePrefixes[currentRole] || 'teacher_'
    const token = localStorage.getItem(`${rolePrefix}accessToken_${currentRole}`) ||
                  localStorage.getItem(`${rolePrefix}accessToken`) ||
                  localStorage.getItem('teacher_accessToken_ROLE_TEACHER')

    const url = `/api/teacher/internship-status/export?studentId=${studentId}&t=${timestamp}`
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })

    if (!response.ok) {
      throw new Error('导出失败')
    }

    const blob = await response.blob()
    const downloadUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = `学生档案_${studentId}_${new Date().getTime()}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(downloadUrl)

    ElMessage.success('导出成功')
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败')
  }
}

const remindStudent = (studentId: number) => {
  openReminderDialog(studentId)
}

const showFeedbackMessage = (message: string, type: 'success' | 'error' | 'info' = 'success') => {
  feedbackTitle.value = type === 'success' ? '操作成功' : type === 'error' ? '操作失败' : '提示'
  feedbackMessage.value = message
  feedbackType.value = type === 'info' ? 'success' : type
  showFeedback.value = true
  setTimeout(() => {
    showFeedback.value = false
  }, 2000)
}

onMounted(() => {
  const currentRole = localStorage.getItem('current_role') || 'ROLE_TEACHER'
  const rolePrefix = currentRole === 'ROLE_TEACHER' ? 'teacher_' : ''
  const id = localStorage.getItem(rolePrefix + 'userId_' + currentRole) || localStorage.getItem('teacherId')
  const type = localStorage.getItem('teacherType')
  if (id) {
    counselorId.value = parseInt(id)
  }
  if (type) {
    teacherType.value = type
  }

  // 根据路由决定加载哪些数据
  if (isStudentManagementPage.value) {
    // 辅导员专属的学生管理页面：加载负责班级的学生
    loadMyClasses()
    loadStudents()
    loadStatistics()
  } else {
    // 学生状态监控页面
    // 如果是辅导员身份，先加载负责的班级信息
    if (isCounselor.value) {
      loadMyClasses().then(() => {
        loadClassList()
        loadMajorList()
        loadGradeList()
        loadCompanyList()
        loadStudents()
        loadStatistics()
      })
    } else {
      // 非辅导员（管理员等）显示全部学生
      loadClassList()
      loadMajorList()
      loadGradeList()
      loadCompanyList()
      loadStudents()
      loadStatistics()
    }
  }
})
</script>

<style scoped>
.student-management-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
  position: relative;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 24px 32px;
  position: relative;
}

.header-content {
  z-index: 1;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: #000;
  margin: 0 0 8px 0;
}

.page-description {
  font-size: 14px;
  color: #606266;
  opacity: 0.95;
  font-weight: 500;
  margin: 0;
}

.stats-card {
  border-radius: 12px;
  border: none;
  padding: 12px 24px;
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 10;
}

.stats-container {
  display: flex;
  gap: 30px;
  align-items: center;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #409eff;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.search-card,
.table-card {
  border-radius: 16px;
  border: none;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  margin-bottom: 24px;
  overflow: hidden;
}

.search-card {
  padding: 24px;
}

.table-card {
  padding: 0;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
  flex-wrap: wrap;
}

.search-actions-row {
  display: flex;
  gap: 12px;
  width: 100%;
  justify-content: center;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
  margin-top: 8px;
}

.search-actions {
  margin-left: auto;
}

.select-input {
  width: 200px;
}

.search-input {
  width: 200px;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 20px 10px;
  border-bottom: 1px solid #f0f0f0;
}

.table-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.total-count {
  font-size: 14px;
  color: #909399;
}

.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
  align-items: center;
}

.table-btn {
  border-radius: 6px;
  padding: 6px 8px;
  transition: all 0.3s ease;
}

.table-btn:hover {
  transform: scale(1.1);
}

.data-table {
  border-radius: 8px;
}

.data-table :deep(.el-table__header) th {
  background-color: #f8fbff;
  color: #409EFF;
  font-weight: 600;
}

.data-table :deep(.el-table__body) tr:hover {
  background-color: #f0f7ff;
}

.action-btn {
  border-radius: 8px;
  padding: 10px 20px;
}

.action-btn.primary {
  background-color: #409EFF;
  border-color: #409EFF;
}

.action-btn.warning {
  background-color: #e6a23c;
  border-color: #e6a23c;
}

.action-btn.success {
  background-color: #67C23A;
  border-color: #67C23A;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.custom-pagination :deep(.el-pagination) {
  padding: 16px;
}

.custom-pagination :deep(.el-pager li) {
  border-radius: 4px;
  transition: all 0.3s;
}

.custom-pagination :deep(.el-pager li.is-active) {
  background-color: #409EFF;
  color: white;
}

.custom-pagination :deep(.el-pager li:hover) {
  background-color: #ecf5ff;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: 12px;
  padding: 24px;
  min-width: 400px;
  max-width: 600px;
  max-height: 80vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #909399;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.3s;
}

.close-btn:hover {
  background: #f5f5f5;
  color: #606266;
}

.modal-body {
  margin-bottom: 20px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

.form-group select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 14px;
}

.radio-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.radio-group label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.radio-group input[type="radio"] {
  margin: 0;
}

.cancel-btn,
.submit-btn {
  padding: 10px 24px;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
  border: none;
}

.cancel-btn {
  background: #f5f5f5;
  color: #606266;
}

.cancel-btn:hover {
  background: #e8e8e8;
}

.submit-btn {
  background: #409EFF;
  color: white;
}

.submit-btn:hover {
  background: #66b1ff;
}

.student-detail-modal {
  max-width: 580px;
  width: 580px;
}

.student-detail-content {
  display: flex;
  gap: 12px;
  min-height: 300px;
}

.detail-left {
  flex: 0 0 260px;
  display: flex;
  flex-direction: column;
}

.detail-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-section {
  background: #fafafa;
  border-radius: 8px;
  padding: 16px;
}

.detail-left .detail-section {
  height: 100%;
}

.detail-section h4 {
  margin: 0 0 12px 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  padding-bottom: 8px;
  border-bottom: 2px solid #409eff;
}

.detail-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-item label {
  font-size: 13px;
  color: #909399;
}

.detail-item span {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}

.button-section {
  background: transparent;
  padding: 16px 0;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-top: auto;
}

.export-btn {
  padding: 12px 32px;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
  border: none;
  background-color: #409eff;
  color: white;
  font-weight: 500;
}

.export-btn:hover {
  background-color: #66b1ff;
}

.status-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.status-confirmed {
  background: #f6ffed;
  color: #52c41a;
}

.status-offer {
  background: #fff7e6;
  color: #fa8c16;
}

.status-no-offer {
  background: #f0f9ff;
  color: #1890ff;
}

.status-delay {
  background: #f9f0ff;
  color: #722ed1;
}

.status-delayed {
  background: #fff7e6;
  color: #fa8c16;
}

.status-in-progress {
  background: #ecf5ff;
  color: #409eff;
}

.status-completed {
  background: #f6ffed;
  color: #52c41a;
}

.status-unknown {
  background: #f5f5f5;
  color: #909399;
}

.feedback-modal {
  min-width: 400px;
}

.feedback-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 20px 0;
}

.feedback-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
}

.feedback-icon.success {
  background: #f6ffed;
  color: #52c41a;
}

.feedback-icon.error {
  background: #fff2f0;
  color: #ff4d4f;
}

.feedback-message {
  font-size: 14px;
  color: #606266;
  text-align: center;
}

.ai-status-content {
  padding: 20px 0;
}

.ai-tip {
  margin-top: 16px;
}
</style>
