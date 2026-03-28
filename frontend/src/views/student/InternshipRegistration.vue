<template>
  <div class="registration-container">
    <div class="page-header">
      <h2>实习去向登记</h2>
      <p>登记您的实习去向信息，帮助我们更好地了解您的需求并提供针对性支持</p>
    </div>

    <el-card class="form-card" shadow="never" :body-style="{ padding: '24px' }">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px" class="status-form">
        <el-form-item label="当前状态" prop="status">
          <el-radio-group v-model="form.status" class="status-radio-group">
            <el-radio-button label="found">已找到实习</el-radio-button>
            <el-radio-button label="searching">正在寻找实习</el-radio-button>
            <el-radio-button label="not-found">暂未找到实习</el-radio-button>
            <el-radio-button label="postgraduate">准备考研</el-radio-button>
            <el-radio-button label="not-intern">暂不实习</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="实习单位" prop="company" v-if="form.status === 'found'">
          <el-input v-model="form.company" placeholder="请输入实习单位名称" clearable />
        </el-form-item>

        <el-form-item label="实习岗位" prop="position" v-if="form.status === 'found'">
          <el-input v-model="form.position" placeholder="请输入实习岗位名称" clearable />
        </el-form-item>

        <el-form-item label="实习地点" prop="location" v-if="form.status === 'found'">
          <el-input v-model="form.location" placeholder="请输入实习地点" clearable />
        </el-form-item>

        <el-form-item label="开始时间" prop="startDate" v-if="form.status === 'found'">
          <el-date-picker
            v-model="form.startDate"
            type="date"
            placeholder="选择开始日期"
            style="width: 100%"
            clearable
          />
        </el-form-item>

        <el-form-item label="结束时间" prop="endDate" v-if="form.status === 'found'">
          <el-date-picker
            v-model="form.endDate"
            type="date"
            placeholder="选择结束日期"
            style="width: 100%"
            clearable
          />
        </el-form-item>

        <el-form-item label="考研院校" prop="graduateSchool" v-if="form.status === 'postgraduate'">
          <el-input v-model="form.graduateSchool" placeholder="请输入考研院校" clearable />
        </el-form-item>

        <el-form-item label="考研专业" prop="graduateMajor" v-if="form.status === 'postgraduate'">
          <el-input v-model="form.graduateMajor" placeholder="请输入考研专业" clearable />
        </el-form-item>

        <el-form-item label="其他说明" prop="otherReason" v-if="form.status === 'not-intern'">
          <el-input
            v-model="form.otherReason"
            type="textarea"
            :rows="4"
            placeholder="请详细说明暂不实习的原因"
            resize="vertical"
          />
        </el-form-item>

        <el-form-item label="联系方式" prop="contactPhone">
          <el-input v-model="form.contactPhone" placeholder="请输入联系电话" clearable />
        </el-form-item>

        <el-form-item label="备注信息" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入其他需要说明的信息（选填）"
            resize="vertical"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="submitForm" :loading="submitting" class="submit-btn">
            提交登记
          </el-button>
          <el-button @click="resetForm" class="reset-btn">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="history-card" shadow="never" v-if="registrationHistory.length > 0" :body-style="{ padding: '24px' }">
      <template #header>
        <div class="card-header">
          <span>登记历史</span>
          <el-tag type="info" size="small">共 {{ registrationHistory.length }} 条记录</el-tag>
        </div>
      </template>
      <div class="history-list">
        <div v-for="item in registrationHistory" :key="item.id" class="history-item">
          <div class="history-left">
            <div :class="['status-badge', item.statusClass]">
              {{ item.statusText }}
            </div>
          </div>
          <div class="history-right">
            <div class="history-content">
              <div class="history-title">{{ item.content }}</div>
              <div class="history-time">{{ item.submitTime }}</div>
            </div>
            <div class="history-actions">
              <el-button link type="primary" @click="viewDetail(item)">
                查看详情
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useAuthStore } from '@/store/auth'
import { ElMessage } from 'element-plus'

const authStore = useAuthStore()

// 从 auth store 获取学生 ID
const getStudentId = () => {
  return authStore.user?.id
}

const formRef = ref(null)
const submitting = ref(false)

const form = reactive({
  status: 'found',
  company: '',
  position: '',
  location: '',
  startDate: '',
  endDate: '',
  graduateSchool: '',
  graduateMajor: '',
  otherReason: '',
  contactPhone: '',
  remark: ''
})

const rules = {
  status: [
    { required: true, message: '请选择当前状态', trigger: 'change' }
  ],
  company: [
    { required: true, message: '请输入实习单位', trigger: 'blur' }
  ],
  position: [
    { required: true, message: '请输入实习岗位', trigger: 'blur' }
  ],
  location: [
    { required: true, message: '请输入实习地点', trigger: 'blur' }
  ],
  startDate: [
    { required: true, message: '请选择开始日期', trigger: 'change' }
  ],
  endDate: [
    { required: true, message: '请选择结束日期', trigger: 'change' }
  ],
  graduateSchool: [
    { required: true, message: '请输入考研院校', trigger: 'blur' }
  ],
  graduateMajor: [
    { required: true, message: '请输入考研专业', trigger: 'blur' }
  ],
  otherReason: [
    { required: true, message: '请输入暂不实习的原因', trigger: 'blur' }
  ],
  contactPhone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号码', trigger: 'blur' }
  ]
}

const registrationHistory = ref([])

// 组件加载时从store获取用户基本信息
onMounted(() => {
  const user = authStore.user
  form.contactPhone = user?.phone || ''
})

const submitForm = () => {
  formRef.value.validate((valid) => {
    if (valid) {
      submitting.value = true
      setTimeout(() => {
        const newRecord = {
          id: Date.now(),
          status: form.status,
          statusText: getStatusText(form.status),
          statusClass: form.status,
          content: getStatusContent(),
          submitTime: new Date().toLocaleString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
          })
        }
        registrationHistory.value.unshift(newRecord)
        ElMessage.success('登记提交成功')
        resetForm()
        submitting.value = false
      }, 1000)
    }
  })
}

const getStatusText = (status) => {
  const statusMap = {
    found: '已找到实习',
    searching: '正在寻找实习',
    notFound: '暂未找到实习',
    postgraduate: '准备考研',
    notIntern: '暂不实习'
  }
  return statusMap[status] || status
}

const getStatusContent = () => {
  if (form.status === 'found') {
    return `${form.company} - ${form.position}`
  } else if (form.status === 'postgraduate') {
    return `考研院校：${form.graduateSchool}，专业：${form.graduateMajor}`
  } else if (form.status === 'not-intern') {
    return form.otherReason
  } else {
    return getStatusText(form.status)
  }
}

const resetForm = () => {
  formRef.value.resetFields()
  // 重置后重新从store获取联系电话
  const user = authStore.user
  form.contactPhone = user?.phone || ''
}

const viewDetail = (item) => {
  ElMessage.info(`查看详情：${item.content}`)
}
</script>

<style scoped>
.registration-container {
  padding: 24px;
  min-height: 100vh;
  background: #f5f7fa;
}

.page-header {
  margin-bottom: 32px;
  text-align: center;
}

.page-header h2 {
  font-size: 28px;
  margin-bottom: 12px;
  color: #1e293b;
  font-weight: 600;
}

.page-header p {
  color: #64748b;
  font-size: 16px;
  max-width: 600px;
  margin: 0 auto;
}

.form-card {
  margin-bottom: 32px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  border: none;
  overflow: hidden;
}

.status-form {
  max-width: 600px;
}

.form-card :deep(.el-form-item__label) {
  font-weight: 500;
  color: #334155;
}

.status-radio-group {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 4px;
}

.status-radio-group .el-radio-button {
  margin-right: 0;
  border-radius: 6px;
  overflow: hidden;
}

.status-radio-group .el-radio-button__inner {
  padding: 8px 16px;
  font-size: 14px;
}

.submit-btn,
.reset-btn {
  padding: 10px 24px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.submit-btn {
  background: #409EFF;
  border-color: #409EFF;
  margin-right: 12px;
}

.submit-btn:hover {
  background: #66b1ff;
  border-color: #66b1ff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

.reset-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.history-card {
  margin-bottom: 32px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  border: none;
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0;
}

.card-header span {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.history-item {
  display: flex;
  gap: 20px;
  padding: 20px;
  background: #f8fafc;
  border-radius: 10px;
  transition: all 0.3s ease;
  border: 1px solid #e2e8f0;
}

.history-item:hover {
  background: #f0f9ff;
  transform: translateX(4px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.history-left {
  flex-shrink: 0;
}

.status-badge {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  transition: all 0.3s ease;
}

.status-badge.found {
  background: #f0f9ff;
  color: #409EFF;
}

.status-badge.searching {
  background: #f0fdf4;
  color: #22c55e;
}

.status-badge.not-found {
  background: #fef2f2;
  color: #ef4444;
}

.status-badge.postgraduate {
  background: #fef3c7;
  color: #e6a23c;
}

.status-badge.not-intern {
  background: #f4f4f5;
  color: #909399;
}

.history-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.history-content {
  flex: 1;
}

.history-title {
  font-size: 16px;
  color: #334155;
  font-weight: 500;
  margin-bottom: 4px;
  line-height: 1.4;
}

.history-time {
  font-size: 13px;
  color: #94a3b8;
}

.history-actions {
  display: flex;
  justify-content: flex-end;
}

.history-actions .el-button {
  font-size: 13px;
  padding: 4px 12px;
}

@media screen and (max-width: 768px) {
  .registration-container {
    padding: 16px;
  }

  .page-header h2 {
    font-size: 24px;
  }

  .form-card :deep(.el-form-item__label) {
    width: 100px;
  }

  .status-radio-group {
    flex-direction: column;
  }

  .status-radio-group .el-radio-button {
    width: 100%;
  }

  .history-item {
    flex-direction: column;
    gap: 12px;
  }

  .submit-btn,
  .reset-btn {
    width: 100%;
    margin-right: 0;
    margin-bottom: 8px;
  }
}

@media screen and (max-width: 480px) {
  .page-header h2 {
    font-size: 20px;
  }

  .form-card :deep(.el-form-item__label) {
    width: 90px;
    font-size: 13px;
  }

  .status-form {
    max-width: 100%;
  }
}
</style>
