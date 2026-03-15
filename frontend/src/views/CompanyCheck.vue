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
                <p>• 审核通过后，系统将通过邮件和短信通知您</p>
                <p>• 如有疑问，请联系相关负责人（张三 1XXXXXXXXXX）</p>
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
                <p>• 默认密码为：123456</p>
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
              <p class="reject-reason">审核意见：{{ companyInfo.auditRemark || '未提供审核意见' }}</p>
              <div class="notice-box error">
                <p>• 请根据审核意见补充或修改相关材料</p>
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
      v-model="recallPendingDialogVisible" 
      title="系统正在审核中" 
      width="500px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <div class="dialog-content">
        <el-result icon="warning" title="系统正在审核中">
          <template #sub-title>
            <div class="status-info">
              <p>您的撤回申请正在由系统自动审核</p>
              <p class="company-name">企业名称：{{ companyInfo.companyName }}</p>
              <p class="apply-time">撤回申请时间：{{ formatTime(companyInfo.recallApplyTime) }}</p>
              <p class="recall-reason">撤回原因：{{ companyInfo.recallReason }}</p>
              <div class="notice-box">
                <p>• 系统正在自动审核您的撤回申请</p>
                <p>• 合理的撤回原因将自动批准，通常在30秒内完成</p>
                <p>• 如系统审核不通过，将转交给管理员审核</p>
              </div>
            </div>
          </template>
        </el-result>
      </div>
      <template #footer>
        <el-button type="primary" @click="goBack">返回登录</el-button>
      </template>
    </el-dialog>
    
    <el-dialog 
      v-model="recallTransferredDialogVisible" 
      title="撤回申请已转交管理员" 
      width="500px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <div class="dialog-content">
        <el-result icon="info" title="等待管理员审核">
          <template #sub-title>
            <div class="status-info">
              <p>您的撤回申请已由系统转交给管理员审核</p>
              <p class="company-name">企业名称：{{ companyInfo.companyName }}</p>
              <p class="apply-time">撤回申请时间：{{ formatTime(companyInfo.recallApplyTime) }}</p>
              <p class="recall-reason">撤回原因：{{ companyInfo.recallReason }}</p>
              <div class="notice-box">
                <p>• 系统审核认为需要进一步人工判断</p>
                <p>• 管理员会在1-2个工作日内完成审核</p>
                <p>• 审核通过后，您可以修改并重新提交注册申请</p>
                <p>• 如有疑问，请联系管理员</p>
              </div>
            </div>
          </template>
        </el-result>
      </div>
      <template #footer>
        <el-button type="primary" @click="goBack">返回登录</el-button>
      </template>
    </el-dialog>
    
    <el-dialog 
      v-model="recallApprovedDialogVisible" 
      title="撤回申请已批准" 
      width="500px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <div class="dialog-content">
        <el-result icon="success" title="撤回申请已批准">
          <template #sub-title>
            <div class="status-info">
              <p>您的撤回申请已批准</p>
              <p class="company-name">企业名称：{{ companyInfo.companyName }}</p>
              <p class="audit-time">审核通过时间：{{ formatTime(companyInfo.recallAuditTime) }}</p>
              <p class="audit-method">审核方式：{{ recallAuditMethod === 'SYSTEM' ? '系统自动审核' : '管理员审核' }}</p>
              <div class="notice-box success">
                <p>• 您现在可以修改注册信息并重新提交</p>
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
      v-model="recallRejectedDialogVisible" 
      title="撤回申请已拒绝" 
      width="500px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <div class="dialog-content">
        <el-result icon="error" title="撤回申请已拒绝">
          <template #sub-title>
            <div class="status-info">
              <p>您的撤回申请未通过审核</p>
              <p class="company-name">企业名称：{{ companyInfo.companyName }}</p>
              <p class="audit-method">审核方式：{{ recallAuditMethod === 'SYSTEM' ? '系统自动审核' : '管理员审核' }}</p>
              <div class="notice-box error">
                <p>• 您的注册申请将继续审核</p>
                <p>• 请耐心等待审核结果</p>
              </div>
            </div>
          </template>
        </el-result>
      </div>
      <template #footer>
        <el-button type="primary" @click="goBack">返回登录</el-button>
      </template>
    </el-dialog>
    
    <el-dialog 
      v-model="recallReasonDialogVisible" 
      title="撤回申请" 
      width="500px"
    >
      <el-alert
        title="系统自动审核"
        type="success"
        :closable="false"
        show-icon
        class="ai-notice"
      >
        <template #default>
          <p>• 系统将自动审核您的撤回申请</p>
          <p>• 合理的撤回原因将自动批准，无需等待</p>
          <p>• 请选择具体的撤回原因，系统会根据选择快速审核</p>
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
            <el-option label="联系人信息错误" value="contact_person_error" />
            <el-option label="联系电话错误" value="contact_phone_error" />
            <el-option label="联系邮箱错误" value="contact_email_error" />
            <el-option label="手机号错误" value="phone_error" />
            <el-option label="企业地址错误" value="address_error" />
            <el-option label="企业介绍不完整" value="introduction_incomplete" />
            <el-option label="营业执照不清晰" value="business_license_blur" />
            <el-option label="法人身份证缺失" value="legal_id_card_missing" />
            <el-option label="实习基地牌匾照片缺失" value="plaque_photo_missing" />
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
            placeholder="请详细说明其他撤回原因"
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
import { ref, reactive, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const router = useRouter()
const checkFormRef = ref(null)
const loading = ref(false)
const auditPendingDialogVisible = ref(false)
const auditApprovedDialogVisible = ref(false)
const auditRejectedDialogVisible = ref(false)
const recallPendingDialogVisible = ref(false)
const recallApprovedDialogVisible = ref(false)
const recallRejectedDialogVisible = ref(false)
const recallTransferredDialogVisible = ref(false)
const recallReasonDialogVisible = ref(false)
const recallSubmitting = ref(false)
const companyInfo = ref<{
  companyName: string
  applyTime?: string
  auditTime?: string
  auditRemark?: string
  recallApplyTime?: string
  recallReason?: string
  recallAuditTime?: string
  contactPerson?: string
  contactPhone?: string
}>({} as any)
const recallAuditMethod = ref('')
let recallPollingTimer = null

const checkForm = reactive({
  companyName: ''
})

const recallForm = reactive({
  reason: '',
  otherReason: ''
})

const recallReasonMap = {
  'contact_person_error': '联系人姓名填写错误',
  'contact_phone_error': '联系电话填写错误',
  'contact_email_error': '联系邮箱填写错误',
  'phone_error': '手机号填写错误',
  'address_error': '企业地址填写错误',
  'introduction_incomplete': '企业介绍内容不完整',
  'business_license_blur': '营业执照图片不清晰，需要重新上传',
  'legal_id_card_missing': '法人身份证照片缺失，需要补充上传',
  'plaque_photo_missing': '实习基地牌匾照片未上传，需要补充'
}

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

const startRecallPolling = () => {
  if (recallPollingTimer) {
    clearInterval(recallPollingTimer)
  }
  
  recallPollingTimer = setInterval(async () => {
    try {
      const response = await request.get('/company/check-status', {
        params: { companyName: checkForm.companyName }
      })
      
      if (response.code === 200) {
        const recallStatus = response.data.recallStatus
        const recallReviewerId = response.data.recallReviewerId
        companyInfo.value = response.data

        console.log('轮询撤回状态 - recallStatus:', recallStatus, 'recallReviewerId:', recallReviewerId)
        
        if (recallReviewerId === -1) {
          recallAuditMethod.value = 'MANUAL'
        } else {
          recallAuditMethod.value = 'SYSTEM'
        }
        
        if (recallStatus === 2) {
          clearInterval(recallPollingTimer)
          recallPollingTimer = null
          recallPendingDialogVisible.value = false
          recallApprovedDialogVisible.value = true
          setTimeout(() => {
            goToRegisterWithRecall()
          }, 2000)
        } else if (recallStatus === 3) {
          clearInterval(recallPollingTimer)
          recallPollingTimer = null
          recallPendingDialogVisible.value = false
          recallRejectedDialogVisible.value = true
        } else if (recallReviewerId === -1) {
          clearInterval(recallPollingTimer)
          recallPollingTimer = null
          recallPendingDialogVisible.value = false
          recallTransferredDialogVisible.value = true
        }
      }
    } catch (error) {
      console.error('轮询撤回状态失败:', error)
    }
  }, 1000)
}

const stopRecallPolling = () => {
  if (recallPollingTimer) {
    clearInterval(recallPollingTimer)
    recallPollingTimer = null
  }
}

onBeforeUnmount(() => {
  stopRecallPolling()
})

const checkCompanyStatus = async () => {
  try {
    await checkFormRef.value.validate()
  } catch (error) {
    ElMessage.warning('请输入企业全称')
    return
  }
  
  loading.value = true
  
  try {
    console.log('开始查询企业状态，企业名称:', checkForm.companyName)
    const response = await request.get('/company/check-status', {
      params: { companyName: checkForm.companyName }
    })
    console.log('查询响应:', response)

    if (response.code === 200) {
        const status = response.data.status
        const recallStatus = response.data.recallStatus
        companyInfo.value = response.data

        if (recallStatus === 1) {
          const recallReviewerId = response.data.recallReviewerId
          console.log('[CompanyCheck] recallStatus === 1, recallReviewerId:', recallReviewerId, '类型:', typeof recallReviewerId)
          if (recallReviewerId === -1) {
            console.log('[CompanyCheck] 显示转交管理员对话框')
            recallAuditMethod.value = 'MANUAL'
            recallTransferredDialogVisible.value = true
          } else {
            console.log('[CompanyCheck] 显示系统审核中对话框')
            recallAuditMethod.value = 'SYSTEM'
            recallPendingDialogVisible.value = true
          }
        } else if (recallStatus === 2) {
          recallApprovedDialogVisible.value = true
          setTimeout(() => {
            goToRegisterWithRecall()
          }, 2000)
        } else if (recallStatus === 3) {
          recallRejectedDialogVisible.value = true
        } else if (status === 0) {
          auditPendingDialogVisible.value = true
        } else if (status === 1) {
          auditApprovedDialogVisible.value = true
        } else if (status === 2) {
          auditRejectedDialogVisible.value = true
        }
      } else if (response.message && response.message.includes('未注册')) {
      router.push({
        path: '/company-register',
        query: { companyName: checkForm.companyName }
      })
    } else {
      console.log('查询失败，响应数据:', response)
    ElMessage.error(response.message || '查询失败')
    }
  } catch (error) {
    console.error('查询企业注册状态失败:', error)
    console.error('错误详情:', error.response)
    console.error('错误状态码:', error.response?.status)
    console.error('错误数据:', error.response?.data)
    ElMessage.error('查询企业注册状态失败')
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
  stopRecallPolling()
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
    query: { companyName: checkForm.companyName }
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
    const response = await request.get('/company/check-status', {
      params: { companyName: checkForm.companyName }
    })
    
    if (response.code === 200) {
      const recallStatus = response.data.recallStatus
      const recallReviewerId = response.data.recallReviewerId
      
      console.log('[showRecallConfirm] recallStatus:', recallStatus, 'recallReviewerId:', recallReviewerId)
      
      if (recallStatus === 1) {
        if (recallReviewerId === -1) {
          console.log('[showRecallConfirm] 显示转交管理员对话框')
          recallAuditMethod.value = 'TRANSFERRED'
          recallTransferredDialogVisible.value = true
        } else {
          console.log('[showRecallConfirm] 显示AI审核中对话框')
          recallAuditMethod.value = 'AI'
          recallPendingDialogVisible.value = true
        }
      } else if (recallStatus === 2) {
        recallApprovedDialogVisible.value = true
        setTimeout(() => {
          goToRegisterWithRecall()
        }, 2000)
      } else if (recallStatus === 3) {
        recallRejectedDialogVisible.value = true
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
      const message = response.data.msg || ''
      
      ElMessage.success('撤回申请已提交，系统正在审核中...')
      recallReasonDialogVisible.value = false
      auditPendingDialogVisible.value = false
      
      startRecallPolling()
      
      setTimeout(() => {
        checkCompanyStatus()
      }, 1000)
    } else {
      ElMessage.error(response.data.msg || '提交撤回申请失败')
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

.ai-notice {
  margin-bottom: 20px;
}

.ai-notice p {
  margin: 6px 0;
  color: #606266;
  font-size: 13px;
}
</style>
