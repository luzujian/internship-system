<template>
  <div class="check-container">
    <div class="check-box">
      <div class="check-header">
        <h2>企业注册申请</h2>
        <p>请输入企业全称以查询注册状态</p>
      </div>
      
      <el-form ref="checkFormRef" :model="checkForm" :rules="rules" label-width="120px" class="check-form">
        <el-form-item label="企业全称" prop="companyName">
          <el-input 
            v-model="checkForm.companyName" 
            placeholder="请输入企业全称"
            clearable
            @keyup.enter="checkCompanyStatus"
          />
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="checkCompanyStatus" :loading="loading">
            查询注册状态
          </el-button>
          <el-button @click="goBack">返回登录</el-button>
        </el-form-item>
      </el-form>
    </div>
    
    <el-dialog 
      v-model="auditPendingDialogVisible" 
      title="注册状态提示" 
      width="500px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <div class="dialog-content">
        <el-result icon="warning" title="正在审核中">
          <template #sub-title>
            <div class="status-info">
              <p>您的企业注册申请正在审核中</p>
              <p class="company-name">企业名称：{{ companyInfo.companyName }}</p>
              <p class="apply-time">申请时间：{{ formatTime(companyInfo.applyTime) }}</p>
              <p class="contact-info">联系人：{{ companyInfo.contactPerson }}（{{ companyInfo.contactPhone }}）</p>
              <div class="notice-box">
                <p>• 相关负责人会在1-3个工作日内完成审核</p>
                <p>• 审核通过后，可使用企业账号登录；也可通过个人邮箱找回密码</p>
                <p>• 如有疑问，请联系相关负责人</p>
              </div>
            </div>
          </template>
        </el-result>
      </div>
      <template #footer>
        <el-button @click="showRecallConfirm">追回申请</el-button>
        <el-button type="primary" @click="goBack">返回登录</el-button>
      </template>
    </el-dialog>
    
    <el-dialog 
      v-model="auditApprovedDialogVisible" 
      title="注册状态提示" 
      width="500px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <div class="dialog-content">
        <el-result icon="success" title="注册已完成">
          <template #sub-title>
            <div class="status-info">
              <p>该企业已完成注册审核</p>
              <p class="company-name">企业名称：{{ companyInfo.companyName }}</p>
              <p class="audit-time">审核通过时间：{{ formatTime(companyInfo.auditTime) }}</p>
              <div class="notice-box success">
                <p>• 您的企业账号已激活</p>
                <p>• 请使用企业用户名和密码登录系统</p>
              </div>
            </div>
          </template>
        </el-result>
      </div>
      <template #footer>
        <el-button type="primary" @click="goToLogin">前往登录</el-button>
      </template>
    </el-dialog>
    
    <el-dialog
      v-model="auditRejectedDialogVisible"
      title="注册状态提示"
      width="500px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <div class="dialog-content">
        <el-result icon="error" title="审核未通过">
          <template #sub-title>
            <div class="status-info">
              <p>该企业的注册申请审核未通过</p>
              <p class="company-name">企业名称：{{ companyInfo.companyName }}</p>
              <p class="reject-reason">驳回理由：{{ companyInfo.auditRemark || '未提供驳回理由' }}</p>
              <div class="notice-box error">
                <p>• 请根据驳回理由补充或修改相关材料</p>
                <p>• 修改完成后可重新提交注册申请</p>
              </div>
            </div>
          </template>
        </el-result>
      </div>
      <template #footer>
        <el-button type="primary" @click="goToRegister">重新提交申请</el-button>
        <el-button @click="goBack">返回登录</el-button>
      </template>
    </el-dialog>
    <el-dialog
      v-model="recallApprovedDialogVisible"
      title="撤回申请已允许"
      width="500px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <div class="dialog-content">
        <el-result icon="success" title="撤回申请已允许">
          <template #sub-title>
            <div class="status-info">
              <p>您的撤回申请已记录，并已自动允许修改</p>
              <p class="company-name">企业名称：{{ companyInfo.companyName }}</p>
              <p class="audit-time">允许修改时间：{{ formatTime(companyInfo.recallAuditTime || companyInfo.recallApplyTime) }}</p>
              <div class="notice-box success">
                <p>• 您现在可以修改注册信息并重新提交</p>
                <p>• 无需等待人工审核</p>
                <p>• 请点击下方按钮前往注册页面</p>
              </div>
            </div>
          </template>
        </el-result>
      </div>
      <template #footer>
        <el-button type="primary" @click="goToRegisterWithRecall">前往注册页面</el-button>
        <el-button @click="goBack">返回登录</el-button>
      </template>
    </el-dialog>
    <el-dialog 
      v-model="recallReasonDialogVisible" 
      title="撤回申请" 
      width="500px"
    >
            <el-alert
        title="自动允许撤回"
        type="success"
        :closable="false"
        show-icon
        class="recall-notice"
      >
        <template #default>
          <p>• 提交撤回原因后，系统会自动允许您修改注册信息</p>
          <p>• 无需等待人工审核</p>
          <p>• 修改完成后请重新提交注册申请</p>
        </template>
      </el-alert>
      
      <el-form :model="recallForm" label-width="100px">
        <el-form-item label="撤回原因" required>
          <el-select 
            v-model="recallForm.reason" 
            placeholder="请选择撤回原因"
            style="width: 100%"
            @change="handleReasonChange"
          >
            <el-option
              v-for="item in recallReasonOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
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
            placeholder="请说明需要修改的注册表单字段和具体原因"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recallReasonDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRecallApplication" :loading="recallSubmitting">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import recallReasonOptions from '../config/companyRecallReasons.json'

const router = useRouter()
const checkFormRef = ref(null)
const loading = ref(false)
const auditPendingDialogVisible = ref(false)
const auditApprovedDialogVisible = ref(false)
const auditRejectedDialogVisible = ref(false)

const recallApprovedDialogVisible = ref(false)


const recallReasonDialogVisible = ref(false)
const recallSubmitting = ref(false)
interface CompanyCheckRow {
  companyName?: string
  status?: number
  recallStatus?: number
  applyTime?: string
  auditTime?: string
  auditRemark?: string
  recallApplyTime?: string
  recallReason?: string
  recallAuditTime?: string
  contactPerson?: string
  contactPhone?: string
}
const companyInfo = ref<CompanyCheckRow>({})



const checkForm = reactive({
  companyName: ''
})

const recallForm = reactive({
  reason: '',
  otherReason: ''
})

const recallReasonMap = Object.fromEntries(
  recallReasonOptions.map(({ value, label }) => [value, label])
)
const handleReasonChange = (value) => {
  if (value !== 'other') {
    recallForm.otherReason = ''
  }
}

const rules = {
  companyName: [
    { required: true, message: '请输入企业全称', trigger: 'blur' },
    { min: 2, max: 255, message: '企业名称长度必须在2-255个字符之间', trigger: 'blur' }
  ]
}

const checkCompanyStatus = async () => {
  try {
    await checkFormRef.value.validate()
  } catch (error) {
    ElMessage.warning('请输入企业全称')
    return
  }

  loading.value = true
  try {
    const response = await request.get<CompanyCheckRow>('/company/check-status', {
      params: { companyName: checkForm.companyName }
    })

    if (response.code === 200) {
      const status = response.data.status
      const recallStatus = response.data.recallStatus
      companyInfo.value = response.data

      if (recallStatus === 1 || recallStatus === 2 || recallStatus === 3) {
        recallApprovedDialogVisible.value = true
      } else if (status === 0) {
        auditPendingDialogVisible.value = true
      } else if (status === 1) {
        auditApprovedDialogVisible.value = true
      } else if (status === 2) {
        auditRejectedDialogVisible.value = true
      } else {
        ElMessage.error('无法识别企业注册状态，请稍后重试')
      }
    } else if (response.message && response.message.includes('未注册')) {
      router.push({
        path: '/company-register',
        query: { companyName: checkForm.companyName }
      })
    } else {
      ElMessage.error(response.message || '查询失败')
    }
  } catch (error: any) {
    const errorMessage = error.response?.data?.message || error.message || ''
    if (errorMessage.includes('未注册')) {
      router.push({
        path: '/company-register',
        query: { companyName: checkForm.companyName }
      })
    } else {
      ElMessage.error(errorMessage || '查询企业注册状态失败')
    }
  } finally {
    loading.value = false
  }
}

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

const goBack = () => {
  router.push('/login')
}

const goToLogin = () => {
  auditApprovedDialogVisible.value = false
  router.push('/login')
}

const goToRegister = () => {
  auditRejectedDialogVisible.value = false
  router.push({
    path: '/company-register',
    query: { companyName: checkForm.companyName, recall: 'true' }
  })
}

const goToRegisterWithRecall = () => {
  recallApprovedDialogVisible.value = false
  router.push({
    path: '/company-register',
    query: { 
      companyName: checkForm.companyName,
      recall: 'true'
    }
  })
}

const showRecallConfirm = async () => {
  try {
    const response = await request.get<CompanyCheckRow>('/company/check-status', {
      params: { companyName: checkForm.companyName }
    })

    if (response.code === 200) {
      const recallStatus = response.data.recallStatus
      companyInfo.value = response.data

      if (recallStatus === 1 || recallStatus === 2 || recallStatus === 3) {
        recallApprovedDialogVisible.value = true
      } else {
        recallForm.reason = ''
        recallReasonDialogVisible.value = true
      }
    }
  } catch (error) {
    console.error('查询撤回状态失败:', error)
    ElMessage.error('查询撤回状态失败')
  }
}

const submitRecallApplication = async () => {
  if (!recallForm.reason || recallForm.reason.trim().length === 0) {
    ElMessage.warning('请选择撤回原因')
    return
  }
  
  if (recallForm.reason === 'other' && (!recallForm.otherReason || recallForm.otherReason.trim().length === 0)) {
    ElMessage.warning('请输入其他撤回原因')
    return
  }
  
  recallSubmitting.value = true
  
  try {
    let recallReasonText = ''
    if (recallForm.reason === 'other') {
      recallReasonText = recallForm.otherReason
    } else {
      recallReasonText = recallReasonMap[recallForm.reason] || recallForm.reason
    }
    
    const response = await request.post('/company/recall', {
      companyName: checkForm.companyName,
      recallReason: recallReasonText
    })
    
    if (response.code === 200) {
      const now = new Date().toISOString()
      companyInfo.value = {
        ...companyInfo.value,
        recallStatus: 2,
        recallReason: recallReasonText,
        recallApplyTime: now,
        recallAuditTime: now
      }
      ElMessage.success('撤回申请已自动允许，现在可以修改并重新提交')
      recallReasonDialogVisible.value = false
      auditPendingDialogVisible.value = false
      recallApprovedDialogVisible.value = true
    } else {
      ElMessage.error(response.message || '提交撤回申请失败')
    }
  } catch (error) {
    console.error('提交撤回申请失败:', error)
    ElMessage.error('提交撤回申请失败')
  } finally {
    recallSubmitting.value = false
  }
}
</script>

<style scoped>
.check-container {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  background-image: url('../assets/2.png');
  background-repeat: no-repeat;
  background-size: 100% 100%;
  background-position: center;
  padding-top: 15vh;
}

.check-box {
  width: 100%;
  max-width: 600px;
  background: white;
  border-radius: 10px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  padding: 40px;
  margin-top: 80px;
}

.check-header {
  text-align: center;
  margin-bottom: 40px;
}

.check-header h2 {
  margin: 0 0 10px 0;
  color: #333;
  font-size: 28px;
}

.check-header p {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.check-form {
  margin-top: 30px;
}

.dialog-content {
  padding: 20px 0;
}

.status-info {
  text-align: left;
  padding: 0 20px;
}

.status-info p {
  margin: 10px 0;
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
}

.company-name {
  font-weight: bold;
  color: #303133;
  font-size: 16px;
}

.apply-time,
.audit-time,
.audit-method,
.contact-info {
  color: #909399;
}

.reject-reason {
  color: #f56c6c;
  font-weight: 500;
}

.notice-box {
  margin-top: 20px;
  padding: 15px;
  background: #f4f4f5;
  border-radius: 6px;
  border-left: 4px solid #909399;
}

.notice-box.success {
  background: #f0f9ff;
  border-left-color: #67c23a;
}

.notice-box.error {
  background: #fef0f0;
  border-left-color: #f56c6c;
}

.notice-box p {
  margin: 8px 0;
  color: #606266;
  font-size: 13px;
}

.recall-notice {
  margin-bottom: 20px;
}

.recall-notice p {
  margin: 6px 0;
  color: #606266;
  font-size: 13px;
}

/* 手机端：单列布局，长文本可换行 */
@media screen and (max-width: 768px) {
  .check-container {
    padding: calc(16px + var(--safe-top, 0px)) 12px 24px;
    background-size: cover;
  }

  .check-box {
    margin-top: 0;
    padding: 20px 16px;
    overflow-wrap: anywhere;
    word-break: break-word;
  }

  .check-header {
    margin-bottom: 20px;
  }

  .check-header h2 {
    font-size: 22px;
  }

  .status-info {
    padding: 0;
  }
}
</style>
