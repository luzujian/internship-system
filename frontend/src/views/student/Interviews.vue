<template>
  <div class="interviews-container">
    <div class="page-header">
      <h2>面试管理</h2>
      <p>查看和管理您的面试安排</p>
    </div>

    <div class="filter-section">
      <div
        v-for="filter in filters"
        :key="filter.value"
        :class="['filter-tab', { active: currentFilter === filter.value }]"
        @click="currentFilter = filter.value"
      >
        {{ filter.label }}
      </div>
    </div>

    <div class="interviews-list">
      <div
        v-for="item in filteredInterviews"
        :key="item.id"
        class="interview-card"
      >
        <div class="card-header">
          <div class="card-title">{{ item.jobTitle }}</div>
          <div :class="['status-tag', item.statusClass]">
            {{ item.status }}
          </div>
        </div>
        <div class="card-body">
          <div class="card-info">
            <div class="info-item">
              <el-icon><OfficeBuilding /></el-icon>
              <span>{{ item.company }}</span>
            </div>
            <div class="info-item">
              <el-icon><Clock /></el-icon>
              <span>{{ item.interviewTime }}</span>
            </div>
            <div class="info-item">
              <el-icon><VideoCamera /></el-icon>
              <span>{{ item.interviewMethod }}</span>
            </div>
            <div class="info-item">
              <el-icon><Location /></el-icon>
              <span>{{ item.location }}</span>
            </div>
          </div>
          <div class="card-footer">
            <div class="card-actions">
              <button class="action-button view" @click="viewDetails(item)">
                <el-icon><ArrowRight /></el-icon>
                查看详情
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="filteredInterviews.length === 0" class="empty-state">
      <el-icon class="empty-icon"><Clock /></el-icon>
      <div class="empty-text">暂无面试记录</div>
    </div>

    <!-- 面试详情对话框 -->
    <el-dialog
      v-model="showDetailDialog"
      :title="detailDialogTitle"
      width="700px"
      class="interview-detail-dialog"
      append-to-body
      lock-scroll
      modal-class="global-modal"
    >
      <div v-if="currentInterview" class="interview-detail-content">
        <div class="detail-section">
          <h3 class="section-title">基本信息</h3>
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">职位名称：</span>
              <span class="detail-value">{{ currentInterview.jobTitle }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">公司名称：</span>
              <span class="detail-value">{{ currentInterview.company }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">面试状态：</span>
              <span :class="['detail-status', currentInterview.statusClass]">{{ currentInterview.status }}</span>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h3 class="section-title">面试安排</h3>
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">面试时间：</span>
              <span class="detail-value">{{ currentInterview.interviewTime }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">面试方式：</span>
              <span class="detail-value">{{ currentInterview.interviewMethod }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">面试地点：</span>
              <span class="detail-value">{{ currentInterview.location }}</span>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h3 class="section-title">联系信息</h3>
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">联系人：</span>
              <span class="detail-value">{{ currentInterview.contactPerson || '未提供' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">联系电话：</span>
              <span class="detail-value">{{ currentInterview.contactPhone || '未提供' }}</span>
            </div>
            <div class="detail-item full-width">
              <span class="detail-label">企业网站：</span>
              <a v-if="currentInterview.website" :href="currentInterview.website" target="_blank" class="detail-link">{{ currentInterview.website }}</a>
              <span v-else class="detail-value">未提供</span>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h3 class="section-title">面试须知</h3>
          <div class="detail-notice">
            {{ currentInterview.interviewNotice || '未提供面试须知' }}
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, inject, watch, onMounted, onUnmounted } from 'vue'
import { Clock, VideoCamera, Location, OfficeBuilding, ArrowRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { useAuthStore } from '@/store/auth'
import emitter from '@/utils/event-bus'
import { initAnnouncementWebSocket, onInterviewCreate, onInterviewStatusUpdate, offInterviewCreate, offInterviewStatusUpdate } from '@/utils/websocket'

const authStore = useAuthStore()

// 从 auth store 获取学生 ID
const getStudentId = () => {
  return authStore.user?.id
}

// 接收父组件提供的对话框状态
const isDialogOpen = inject('isDialogOpen');

const currentFilter = ref('all')
const showDetailDialog = ref(false)
const currentInterview = ref(null)
const showInternshipConfirmationForm = ref(false)

const interviews = ref([])

// 监听对话框状态变化
watch(showDetailDialog, (newVal) => {
  if (isDialogOpen) {
    isDialogOpen.value = newVal;
  }
});

// 监听实习确认对话框状态
watch(showInternshipConfirmationForm, (newVal) => {
  if (isDialogOpen) {
    isDialogOpen.value = newVal;
  }
});

// 过滤器选项
const filters = [
  { label: '全部', value: 'all' },
  { label: '待确认', value: 'pending' },
  { label: '待面试', value: 'upcoming' },
  { label: '已完成', value: 'completed' }
]

const fetchInterviews = async () => {
  try {
    let status = null
    // pending=0, upcoming=1, completed 不发送status(获取所有), cancelled 已移除
    if (currentFilter.value === 'pending') {
      status = 0
    } else if (currentFilter.value === 'upcoming') {
      status = 1
    }
    // completed 和 all 都获取所有数据，前端筛选

    const response = await request.get(`/student/interviews/list`, {
      params: status !== null ? { status } : {}
    })
    
    if (response.code === 200) {
      interviews.value = response.data.map(item => ({
        id: item.id,
        jobTitle: item.positionName,
        company: item.companyName,
        interviewTime: item.interviewTime,
        interviewMethod: item.interviewMethod,
        location: item.interviewLocation,
        status: getStatusText(item.status),
        statusClass: getStatusClass(item.status),
        contactPerson: item.contactPerson,
        contactPhone: item.contactPhone,
        website: item.website,
        interviewNotice: item.remark || ''
      }))
    }
  } catch (error) {
    console.error('获取面试列表失败:', error)
  }
}

const getStatusText = (status: string | number) => {
  const statusMap: Record<string, string> = {
    'pending_interview': '待面试',
    'interview_passed': '面试通过',
    'interview_failed': '面试没通过'
  }
  return statusMap[String(status)] || String(status)
}

const getStatusClass = (status: string | number) => {
  const statusMap: Record<string, string> = {
    'pending_interview': 'pending',
    'interview_passed': 'interview_passed',
    'interview_failed': 'interview_failed'
  }
  return statusMap[String(status)] || 'unknown'
}

const filteredInterviews = computed(() => {
  if (currentFilter.value === 'all') {
    return interviews.value
  }
  // 已完成状态包含面试通过和面试没通过
  if (currentFilter.value === 'completed') {
    return interviews.value.filter(item =>
      item.statusClass === 'interview_passed' || item.statusClass === 'interview_failed'
    )
  }
  return interviews.value.filter(item => item.statusClass === currentFilter.value)
})

const detailDialogTitle = computed(() => {
  if (currentInterview.value) {
    return `${currentInterview.value.jobTitle} - 面试详情`
  }
  return '面试详情'
})

const confirmInterview = async (item) => {
  try {
    ElMessageBox.confirm(
      `确认参加 "${item.jobTitle}" 的面试？`,
      '确认面试',
      {
        confirmButtonText: '确认参加',
        cancelButtonText: '取消',
        type: 'info'
      }
    ).then(async () => {
      try {
        const response = await request.post(`/student/interviews/${item.id}/confirm`)
        if (response.code === 200) {
          item.status = '待面试'
          item.statusClass = 'upcoming'
          ElMessage.success('已确认参加面试')
        } else {
          ElMessage.error(response.data.message || '确认失败，请稍后重试')
        }
      } catch (error) {
        console.error('确认面试失败:', error)
        ElMessage.error('网络错误，请稍后重试')
      }
    }).catch(() => {
      ElMessage.info('已取消')
    })
  } catch (error) {
    console.error('确认面试失败:', error)
  }
}

const rejectInterview = async (item) => {
  try {
    ElMessageBox.prompt('请输入拒绝理由', '拒绝面试', {
      confirmButtonText: '提交',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '请输入拒绝理由'
    }).then(async ({ value }) => {
      try {
        const response = await request.post(`/student/interviews/${item.id}/reject`, {
          reason: value
        })
        if (response.code === 200) {
          item.status = '已取消'
          item.statusClass = 'cancelled'
          ElMessage.success('已拒绝面试')
        } else {
          ElMessage.error(response.data.message || '拒绝失败，请稍后重试')
        }
      } catch (error) {
        console.error('拒绝面试失败:', error)
        ElMessage.error('网络错误，请稍后重试')
      }
    }).catch(() => {
      ElMessage.info('已取消')
    })
  } catch (error) {
    console.error('拒绝面试失败:', error)
  }
}

const viewDetails = (item) => {
  currentInterview.value = item
  showDetailDialog.value = true
}

watch(currentFilter, () => {
  fetchInterviews()
})

// 监听申请状态更新事件
const handleApplicationStatusUpdate = (data) => {
  console.log('收到申请状态更新通知:', data)
  ElMessage({
    type: data.status === 'approved' ? 'success' : 'warning',
    message: `您申请的 ${data.positionName} 岗位面试结果：${data.statusText}`,
    duration: 5000
  })
  // 刷新面试列表
  fetchInterviews()
}

onMounted(async () => {
  fetchInterviews()

  // 初始化 WebSocket 连接
  const token = localStorage.getItem('token')
  if (token) {
    initAnnouncementWebSocket(token, () => {})
  }

  // 注册面试事件监听
  onInterviewCreate((data) => {
    const newInterview = {
      id: data.id,
      jobTitle: data.positionName,
      company: data.companyName,
      interviewTime: data.interviewTime,
      interviewMethod: data.interviewMethod,
      location: data.interviewLocation,
      status: getStatusText(data.status),
      statusClass: getStatusClass(data.status),
      contactPerson: data.contactPerson || '',
      contactPhone: data.contactPhone || '',
      website: data.website || ''
    }
    interviews.value.unshift(newInterview)
    ElMessage.success(`新增面试：${data.positionName}`)
  })

  onInterviewStatusUpdate((data) => {
    const interview = interviews.value.find(i => i.id === data.interviewId)
    if (interview) {
      interview.status = data.statusText
      interview.statusClass = getStatusClass(data.status)
      ElMessage.success(`面试状态更新：${data.statusText}`)
    }
  })

  // 监听申请状态更新事件
  emitter.on('application-status-update', handleApplicationStatusUpdate)
})

onUnmounted(() => {
  offInterviewCreate(() => {})
  offInterviewStatusUpdate(() => {})
  emitter.off('application-status-update', handleApplicationStatusUpdate)
})
</script>

<style scoped>
.interviews-container {
  width: 100%;
  min-height: 100%;
  background: #f1f5f9;
  overflow-y: auto;
  overflow-x: hidden;
}

.page-header {
  margin-bottom: 24px;
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  border-radius: 12px;
  padding: 24px 32px;
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

.filter-section {
  background: white;
  border-radius: 12px;
  padding: 16px 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  display: flex;
  gap: 8px;
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
  color: #409EFF;
}

.filter-tab.active {
  background: #409EFF;
  color: white;
  border-color: #409EFF;
}

/* 面试列表卡片：小卡片网格布局 - 美化版 */
.interviews-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 20px;
  max-height: calc(100vh - 560px);
  overflow-y: auto;
  padding-right: 4px;
}

.interview-card {
  background: linear-gradient(145deg, #ffffff 0%, #f8fafc 100%);
  border-radius: 20px;
  padding: 20px;
  box-shadow: 
    0 4px 16px rgba(0, 0, 0, 0.06),
    0 1px 4px rgba(0, 0, 0, 0.04),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  gap: 14px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(226, 232, 240, 0.6);
  height: 100%;
}

.interview-card::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 4px;
  background: linear-gradient(90deg, #409EFF 0%, #67C23A 50%, #409EFF 100%);
  background-size: 200% 100%;
  border-radius: 20px 20px 0 0;
  animation: gradient-shift 3s ease infinite;
}

@keyframes gradient-shift {
  0%, 100% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
}

.interview-card::after {
  content: "";
  position: absolute;
  top: -50%;
  right: -50%;
  width: 100%;
  height: 100%;
  background: radial-gradient(
    circle,
    rgba(64, 158, 255, 0.03) 0%,
    transparent 70%
  );
  border-radius: 50%;
  pointer-events: none;
}

.interview-card:hover {
  transform: translateY(-6px) scale(1.02);
  box-shadow: 
    0 12px 40px rgba(64, 158, 255, 0.15),
    0 4px 12px rgba(0, 0, 0, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
  border-color: rgba(64, 158, 255, 0.3);
  background: linear-gradient(145deg, #ffffff 0%, #f0f9ff 100%);
}

.interview-card:hover .card-title {
  color: #409EFF;
  text-shadow: 0 0 20px rgba(64, 158, 255, 0.3);
}

.interview-card:hover .status-tag {
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

.status-tag.upcoming {
  background: linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%);
  color: #1890ff;
  border: 1px solid #91d5ff;
  box-shadow: 
    0 2px 8px rgba(24, 144, 255, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.status-tag.completed {
  background: linear-gradient(135deg, #f6ffed 0%, #d9f7be 100%);
  color: #52c41a;
  border: 1px solid #b7eb8f;
  box-shadow: 
    0 2px 8px rgba(82, 196, 26, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.status-tag.cancelled {
  background: linear-gradient(135deg, #fafafa 0%, #e8e8e8 100%);
  color: #8c8c8c;
  border: 1px solid #d9d9d9;
  box-shadow:
    0 2px 8px rgba(0, 0, 0, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

/* 面试通过状态 - 更醒目的样式 */
.status-tag.interview_passed {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  color: #ffffff;
  border: 1px solid #389e0d;
  box-shadow:
    0 4px 12px rgba(82, 196, 26, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
  animation: pulse-success 2s ease-in-out infinite;
}

/* 面试没通过状态 */
.status-tag.interview_failed {
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7875 100%);
  color: #ffffff;
  border: 1px solid #cf1322;
  box-shadow:
    0 4px 12px rgba(255, 77, 79, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

@keyframes pulse-success {
  0%, 100% {
    box-shadow:
      0 4px 12px rgba(82, 196, 26, 0.4),
      inset 0 1px 0 rgba(255, 255, 255, 0.3);
  }
  50% {
    box-shadow:
      0 4px 20px rgba(82, 196, 26, 0.6),
      inset 0 1px 0 rgba(255, 255, 255, 0.3);
  }
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
  position: relative;
  z-index: 1;
}

.card-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  font-size: 13px;
  color: #64748b;
  padding: 12px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 12px;
  border: 1px solid rgba(226, 232, 240, 0.5);
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
  font-size: 14px;
  color: #409EFF;
  width: 18px;
  text-align: center;
  flex-shrink: 0;
  filter: drop-shadow(0 1px 2px rgba(64, 158, 255, 0.2));
}

.card-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 0 0 0;
  margin-top: auto;
  position: relative;
  z-index: 1;
}

.card-footer::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(226, 232, 240, 0.6), transparent);
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

.action-button.confirm {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  color: white;
  box-shadow: 
    0 4px 12px rgba(82, 196, 26, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

.action-button.confirm:hover {
  transform: translateY(-3px) scale(1.05);
  box-shadow: 
    0 8px 24px rgba(82, 196, 26, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.4);
}

.action-button.reject {
  background: linear-gradient(135deg, #fff7e6 0%, #ffe7ba 100%);
  color: #fa8c16;
  border: 1px solid #ffd591;
  box-shadow: 
    0 2px 8px rgba(250, 140, 22, 0.15),
    inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.action-button.reject:hover {
  transform: translateY(-3px) scale(1.05);
  box-shadow: 
    0 6px 20px rgba(250, 140, 22, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
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
}

/* 响应式适配：匹配网格布局的响应式规则 */
@media screen and (max-width: 1200px) {
  .interviews-list {
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  }
}

@media screen and (max-width: 768px) {
  .interviews-container {
    padding: 16px;
  }

  .interviews-list {
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

  .filter-section {
    justify-content: center;
  }
}

@media screen and (max-width: 480px) {
  .page-header h2 {
    font-size: 24px;
  }

  .filter-section {
    padding: 12px 16px;
  }

  .filter-tab {
    padding: 6px 14px;
    font-size: 13px;
  }

  .interview-card {
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

/* 面试详情对话框样式 */
.interview-detail-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden !important;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15) !important;
  position: fixed !important;
  left: 50% !important;
  top: 50% !important;
  transform: translate(-50%, -50%) !important;
  margin: 0 !important;
  right: auto !important;
  z-index: 9999 !important;
}

.interview-detail-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #67c23a 0%, #409eff 100%) !important;
  color: white !important;
  padding: 28px 32px !important;
  margin: 0 !important;
  border-radius: 0 !important;
  position: relative !important;
}

.interview-detail-dialog :deep(.el-dialog__header::before) {
  content: '' !important;
  position: absolute !important;
  top: 0 !important;
  left: 0 !important;
  right: 0 !important;
  bottom: 0 !important;
  background: url('data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><circle cx="80" cy="20" r="40" fill="white" opacity="0.05"/><circle cx="20" cy="80" r="30" fill="white" opacity="0.05"/></svg>') !important;
  background-size: cover !important;
  pointer-events: none !important;
}

.interview-detail-dialog :deep(.el-dialog__title) {
  color: white !important;
  font-weight: 700 !important;
  font-size: 20px !important;
  position: relative !important;
  z-index: 1 !important;
  letter-spacing: 0.5px !important;
}

.interview-detail-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white !important;
  font-size: 22px !important;
  background: rgba(255, 255, 255, 0.2) !important;
  border-radius: 50% !important;
  width: 32px !important;
  height: 32px !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  transition: all 0.3s ease !important;
}

.interview-detail-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  background: rgba(255, 255, 255, 0.3) !important;
  transform: scale(1.1) !important;
}

.interview-detail-dialog :deep(.el-dialog__body) {
  padding: 32px !important;
  background: #f8fafc !important;
  max-height: 60vh;
  overflow-y: auto;
}

.interview-detail-content {
  width: 100%;
}

.detail-section {
  margin-bottom: 28px !important;
  background: white !important;
  border-radius: 12px !important;
  padding: 24px !important;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06) !important;
}

.detail-section:last-child {
  margin-bottom: 0 !important;
}

.section-title {
  font-size: 16px !important;
  font-weight: 700 !important;
  color: #1e293b !important;
  margin-bottom: 16px !important;
  padding-bottom: 12px !important;
  border-bottom: 2px solid #f1f5f9 !important;
  position: relative !important;
}

.section-title::after {
  content: '' !important;
  position: absolute !important;
  bottom: -2px !important;
  left: 0 !important;
  width: 60px !important;
  height: 2px !important;
  background: linear-gradient(135deg, #67c23a 0%, #409eff 100%) !important;
}

.detail-grid {
  display: grid !important;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)) !important;
  gap: 16px !important;
}

.detail-item {
  display: flex !important;
  flex-direction: column !important;
  gap: 6px !important;
}

.detail-item.full-width {
  grid-column: 1 / -1 !important;
}

.detail-label {
  font-size: 13px !important;
  font-weight: 600 !important;
  color: #64748b !important;
  letter-spacing: 0.3px !important;
}

.detail-value {
  font-size: 14px !important;
  color: #1e293b !important;
  font-weight: 400 !important;
  line-height: 1.4 !important;
}

.detail-status {
  display: inline-block !important;
  padding: 6px 14px !important;
  border-radius: 6px !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  white-space: nowrap !important;
}

.detail-status.pending {
  background: #fff7ed !important;
  color: #f97316 !important;
  border: 1px solid #fdba74 !important;
}

.detail-status.upcoming {
  background: #f0f9ff !important;
  color: #409EFF !important;
  border: 1px solid #bae6fd !important;
}

.detail-status.completed {
  background: #f0fdf4 !important;
  color: #22c55e !important;
  border: 1px solid #86efac !important;
}

.detail-status.cancelled {
  background: #f1f5f9 !important;
  color: #64748b !important;
  border: 1px solid #cbd5e1 !important;
}

.detail-link {
  font-size: 14px !important;
  color: #409EFF !important;
  text-decoration: none !important;
  font-weight: 500 !important;
  transition: all 0.3s ease !important;
  word-break: break-all !important;
}

.detail-link:hover {
  color: #337ecc !important;
  text-decoration: underline !important;
}

.detail-notice {
  font-size: 14px !important;
  color: #1e293b !important;
  line-height: 1.6 !important;
  background: #f8fafc !important;
  border: 1px solid #e2e8f0 !important;
  border-radius: 8px !important;
  padding: 16px !important;
  margin-top: 8px !important;
}

@media screen and (max-width: 768px) {
  .interview-detail-dialog .el-dialog {
    width: 90% !important;
  }

  .interview-detail-dialog .el-dialog__body {
    padding: 20px !important;
  }

  .detail-grid {
    grid-template-columns: 1fr !important;
  }

  .detail-item.full-width {
    grid-column: 1 !important;
  }
}

@media screen and (max-width: 480px) {
  .interview-detail-dialog .el-dialog__header {
    padding: 20px 24px !important;
  }

  .interview-detail-dialog .el-dialog__title {
    font-size: 18px !important;
  }

  .detail-section {
    padding: 16px !important;
  }
}

/* 实习确认部分样式 */
.internship-confirmation-section {
  margin-top: 32px;
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f1f5f9;
}

.section-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.confirmation-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.confirmation-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
}

.confirmation-card:hover {
  background: #f0f9ff;
  border-color: #bae6fd;
}

.confirmation-info {
  flex: 1;
}

.confirmation-info .company-name {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 4px;
}

.confirmation-info .position-name {
  font-size: 14px;
  color: #64748b;
  margin-bottom: 4px;
}

.confirmation-info .confirm-date {
  font-size: 13px;
  color: #94a3b8;
}

.status-tag.success {
  background: #f0fdf4;
  color: #22c55e;
  border: 1px solid #86efac;
}

.status-tag.warning {
  background: #fff7ed;
  color: #f97316;
  border: 1px solid #fdba74;
}

/* 实习确认表对话框样式 */
.internship-confirmation-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden !important;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15) !important;
  position: fixed !important;
  left: 50% !important;
  top: 50% !important;
  transform: translate(-50%, -50%) !important;
  margin: 0 !important;
  right: auto !important;
  z-index: 9999 !important;
}

.internship-confirmation-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  color: white;
  border-bottom: none;
}

.internship-confirmation-dialog :deep(.el-dialog__title) {
  color: white;
  font-size: 18px;
  font-weight: 600;
}

.internship-confirmation-dialog :deep(.el-dialog__headerbtn .el-icon) {
  color: white;
}

.internship-confirmation-form {
  max-height: 70vh;
  overflow-y: auto;
  padding-right: 10px;
}

.internship-confirmation-form :deep(.el-form-item__label) {
  font-weight: 500;
  color: #333;
}

.internship-confirmation-form .file-info {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.internship-confirmation-form .file-info:hover {
  background: #f0f9ff;
}

.internship-confirmation-form .file-icon {
  color: #409EFF;
  font-size: 18px;
}

.commitment-checkbox {
  margin-bottom: 8px;
  display: block;
}

@media screen and (max-width: 768px) {
  .internship-confirmation-section {
    padding: 16px;
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .confirmation-card {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .status-tag {
    align-self: flex-start;
  }
}

@media screen and (max-width: 480px) {
  .section-header h3 {
    font-size: 16px;
  }
}

/* 遮罩样式，确保覆盖左侧菜单栏 */
.interview-detail-dialog :deep(.el-dialog__wrapper) {
  z-index: 9999 !important;
}

.internship-confirmation-dialog :deep(.el-dialog__wrapper) {
  z-index: 9999 !important;
}

/* 遮罩层覆盖整个页面 */
:deep(.el-dialog__mask) {
  position: fixed !important;
  top: 0 !important;
  left: 0 !important;
  width: 100vw !important;
  height: 100vh !important;
  z-index: 9998 !important;
  background: rgba(0, 0, 0, 0.4) !important;
}

/* 弹窗阴影效果 */
.interview-detail-dialog :deep(.el-dialog) {
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15) !important;
}

.internship-confirmation-dialog :deep(.el-dialog) {
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15) !important;
}
</style>
