<template>
  <div class="register-container">
    <div class="register-box">
      <div class="register-header">
        <h2>企业注册申请</h2>
        <p>请填写企业信息并上传相关资质文件</p>
      </div>
      
      <el-alert
        v-if="!isRecallMode"
        title="审核说明"
        type="info"
        :closable="false"
        show-icon
        class="audit-notice"
      >
        <template #default>
          <p>• 提交申请后，相关负责人会在1-3个工作日内完成审核</p>
          <p>• 审核通过：系统将通过邮件和短信通知您登录账号和密码</p>
          <p>• 审核未通过：系统将通知您未通过的原因，请补充材料后重新提交</p>
        </template>
      </el-alert>
      
      <el-alert
        v-if="isRecallMode"
        title="追回模式"
        type="warning"
        :closable="false"
        show-icon
        class="audit-notice"
      >
        <template #default>
          <p>• 您已追回之前的注册申请</p>
          <p>• 请修改需要更正的信息后重新提交</p>
          <p>• 提交后将重新进入审核流程</p>
        </template>
      </el-alert>
      
      <el-form 
        ref="registerFormRef" 
        :model="registerForm" 
        :rules="rules" 
        label-width="120px"
        class="register-form"
      >
        <el-form-item label="企业名称" prop="companyName">
          <el-input 
            v-model="registerForm.companyName" 
            placeholder="请输入企业全称"
            :disabled="isRecallMode"
            @blur="checkUsername"
          />
          <div v-if="usernameChecked && !isRecallMode" class="check-result" :class="{success: usernameAvailable, error: !usernameAvailable}">
            {{ usernameAvailable ? '企业名称可用' : '企业名称已被注册' }}
          </div>
        </el-form-item>
        
        <el-form-item label="联系人" prop="contactPerson">
          <el-input v-model="registerForm.contactPerson" placeholder="请输入联系人姓名" />
        </el-form-item>
        
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="registerForm.contactPhone" placeholder="请输入联系电话" />
        </el-form-item>
        
        <el-form-item label="联系邮箱" prop="contactEmail">
          <el-input v-model="registerForm.contactEmail" placeholder="请输入联系邮箱" />
        </el-form-item>
        
        <el-form-item label="手机号" prop="phone">
          <el-input 
            v-model="registerForm.phone" 
            placeholder="请输入手机号"
            maxlength="11"
          />
          <el-button 
            type="primary" 
            :disabled="countdown > 0" 
            @click="sendVerifyCode"
            class="verify-code-btn"
          >
            {{ countdown > 0 ? `${countdown}秒后重试` : '发送验证码' }}
          </el-button>
        </el-form-item>
        
        <el-form-item label="验证码" prop="verifyCode">
          <el-input 
            v-model="registerForm.verifyCode" 
            placeholder="请输入验证码"
            maxlength="6"
          />
        </el-form-item>
        
        <el-form-item label="企业地址" prop="address">
          <el-input 
            v-model="registerForm.address" 
            type="textarea" 
            :rows="2"
            placeholder="请输入企业详细地址" 
          />
        </el-form-item>
        
        <el-form-item label="企业简介" prop="introduction">
          <el-input 
            v-model="registerForm.introduction" 
            type="textarea" 
            :rows="4"
            placeholder="请输入企业简介（选填）" 
          />
        </el-form-item>
        
        <el-form-item label="营业执照" prop="businessLicense" required>
          <el-upload
            class="upload-demo"
            action="#"
            :auto-upload="false"
            :on-change="handleBusinessLicenseChange"
            :show-file-list="false"
            accept="image/*"
          >
            <div v-if="!registerForm.businessLicense" class="upload-trigger">
              <el-icon class="upload-icon"><Plus /></el-icon>
              <div class="upload-text">点击上传营业执照</div>
              <div class="upload-tip">支持jpg、png等图片格式</div>
            </div>
            <div v-else class="file-preview">
              <img :src="getImageUrl(registerForm.businessLicense)" alt="营业执照预览" />
              <el-button type="danger" size="small" class="delete-btn" @click="deleteBusinessLicense">删除</el-button>
            </div>
          </el-upload>
        </el-form-item>
        
        <el-form-item label="法人身份证" required>
          <div class="id-card-upload-container">
            <!-- 正面上传 -->
            <div class="id-card-upload-item">
              <div class="id-card-label-title">身份证正面</div>
              <el-upload
                class="upload-demo"
                action="#"
                :auto-upload="false"
                :on-change="(file) => handleIdCardFrontChange(file)"
                :show-file-list="false"
                accept="image/*"
              >
                <div v-if="!registerForm.legalIdCardFront" class="upload-trigger id-card-trigger">
                  <el-icon class="upload-icon"><Plus /></el-icon>
                  <div class="upload-text">点击上传正面</div>
                  <div class="upload-tip">人像面</div>
                </div>
                <div v-else class="file-preview id-card-preview-single">
                  <img :src="getImageUrl(registerForm.legalIdCardFront)" alt="身份证正面" />
                  <el-button type="danger" size="small" class="delete-btn" @click.stop="deleteIdCard('front')">删除</el-button>
                </div>
              </el-upload>
            </div>
            
            <!-- 反面上传 -->
            <div class="id-card-upload-item">
              <div class="id-card-label-title">身份证反面</div>
              <el-upload
                class="upload-demo"
                action="#"
                :auto-upload="false"
                :on-change="(file) => handleIdCardBackChange(file)"
                :show-file-list="false"
                accept="image/*"
              >
                <div v-if="!registerForm.legalIdCardBack" class="upload-trigger id-card-trigger">
                  <el-icon class="upload-icon"><Plus /></el-icon>
                  <div class="upload-text">点击上传反面</div>
                  <div class="upload-tip">国徽面</div>
                </div>
                <div v-else class="file-preview id-card-preview-single">
                  <img :src="getImageUrl(registerForm.legalIdCardBack)" alt="身份证反面" />
                  <el-button type="danger" size="small" class="delete-btn" @click.stop="deleteIdCard('back')">删除</el-button>
                </div>
              </el-upload>
            </div>
          </div>
        </el-form-item>
        
        <el-form-item label="挂牌实习基地" prop="isInternshipBase">
          <el-radio-group v-model="registerForm.isInternshipBase">
            <el-radio :label="0">否</el-radio>
            <el-radio :label="1">国家级</el-radio>
            <el-radio :label="2">省级</el-radio>
          </el-radio-group>
        </el-form-item>
        
        <el-form-item v-if="registerForm.isInternshipBase > 0" label="牌匾照片" prop="plaquePhoto">
          <el-upload
            class="upload-demo"
            action="#"
            :auto-upload="false"
            :on-change="handlePlaquePhotoChange"
            :show-file-list="false"
            accept="image/*"
          >
            <div v-if="!registerForm.plaquePhoto" class="upload-trigger">
              <el-icon class="upload-icon"><Plus /></el-icon>
              <div class="upload-text">点击上传牌匾照片</div>
              <div class="upload-tip">支持jpg、png等图片格式</div>
            </div>
            <div v-else class="file-preview">
              <img :src="getImageUrl(registerForm.plaquePhoto)" alt="牌匾照片预览" />
              <el-button type="danger" size="small" class="delete-btn" @click="deletePlaquePhoto">删除</el-button>
            </div>
          </el-upload>
        </el-form-item>
        
        <el-form-item label="接受兜底" prop="acceptBackup">
          <el-radio-group v-model="registerForm.acceptBackup">
            <el-radio :label="0">否</el-radio>
            <el-radio :label="1">是</el-radio>
          </el-radio-group>
        </el-form-item>
        
        <el-form-item v-if="registerForm.acceptBackup === 1" label="兜底学生数量" prop="maxBackupStudents">
          <el-input 
            v-model.number="registerForm.maxBackupStudents" 
            type="number"
            :min="0"
            placeholder="请输入能接受兜底的最多学生数量"
          />
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="submitRegister" :loading="loading">
            提交申请
          </el-button>
          <el-button @click="goBack">返回登录</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import request from '@/utils/request'

const router = useRouter()
const route = useRoute()
const registerFormRef = ref(null)
const loading = ref(false)
const countdown = ref(0)
const usernameChecked = ref(false)
const usernameAvailable = ref(false)
const isRecallMode = ref(false)
const existingCompanyId = ref(null)

const registerForm = reactive({
  companyName: '',
  contactPerson: '',
  contactPhone: '',
  contactEmail: '',
  phone: '',
  verifyCode: '',
  address: '',
  introduction: '',
  businessLicense: '',
  legalIdCardFront: '',
  legalIdCardBack: '',
  isInternshipBase: 0,
  plaquePhoto: '',
  acceptBackup: 0,
  maxBackupStudents: 0
})

const getImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('data:') || url.startsWith('https://') || url.startsWith('http://') || url.startsWith('/api')) {
    return url
  }
  return '/api/' + url
}

onMounted(async () => {
  if (route.query.companyName) {
    registerForm.companyName = String(route.query.companyName)
    
    if (route.query.recall === 'true') {
      isRecallMode.value = true
      await loadExistingData()
    } else {
      checkUsername()
    }
  }
})

const loadExistingData = async () => {
  try {
    const response = await request.get('/company/check-status', {
      params: { companyName: registerForm.companyName }
    })
    
    if (response.data.code === 200) {
      const data = response.data.data
      existingCompanyId.value = data.id
      registerForm.contactPerson = data.contactPerson || ''
      registerForm.contactPhone = data.contactPhone || ''
      registerForm.contactEmail = data.contactEmail || ''
      registerForm.phone = data.phone || ''
      registerForm.address = data.address || ''
      registerForm.introduction = data.introduction || ''
      registerForm.businessLicense = data.businessLicense || ''
      
      if (data.legalIdCard) {
        const idCardImages = data.legalIdCard.split(',')
        registerForm.legalIdCardFront = idCardImages[0] || ''
        registerForm.legalIdCardBack = idCardImages[1] || ''
      }
      
      registerForm.isInternshipBase = data.isInternshipBase || 0
      registerForm.plaquePhoto = data.plaquePhoto || ''
      registerForm.acceptBackup = data.acceptBackup || 0
      registerForm.maxBackupStudents = data.maxBackupStudents || 0
      ElMessage.success('已加载原有注册信息，请修改后重新提交')
    }
  } catch (error) {
    console.error('加载注册信息失败:', error)
    ElMessage.error('加载注册信息失败')
  }
}

const rules = {
  companyName: [
    { required: true, message: '请输入企业名称', trigger: 'blur' },
    { min: 2, max: 255, message: '企业名称长度必须在2-255个字符之间', trigger: 'blur' }
  ],
  contactPerson: [
    { required: true, message: '请输入联系人', trigger: 'blur' },
    { min: 1, max: 50, message: '联系人长度必须在1-50个字符之间', trigger: 'blur' }
  ],
  contactPhone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { min: 11, max: 20, message: '联系电话长度必须在11-20个字符之间', trigger: 'blur' }
  ],
  contactEmail: [
    { required: true, message: '请输入联系邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号格式', trigger: 'blur' }
  ],
  verifyCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码必须是6位数字', trigger: 'blur' }
  ],
  address: [
    { required: true, message: '请输入企业地址', trigger: 'blur' },
    { min: 5, max: 255, message: '企业地址长度必须在5-255个字符之间', trigger: 'blur' }
  ],
  businessLicense: [
    { required: true, message: '请上传营业执照', trigger: 'change' }
  ],
  legalIdCardFront: [
    { required: true, message: '请上传身份证正面', trigger: 'change' }
  ],
  legalIdCardBack: [
    { required: true, message: '请上传身份证反面', trigger: 'change' }
  ]
}

const checkUsername = async () => {
  if (!registerForm.companyName) {
    usernameChecked.value = false
    return
  }
  
  try {
    const response = await request.get('/company/check-username', {
      params: { username: registerForm.companyName }
    })
    
    usernameChecked.value = true
    usernameAvailable.value = response.data.code === 200
  } catch (error) {
    console.error('检查企业名称失败:', error)
    usernameChecked.value = false
  }
}

const sendVerifyCode = async () => {
  if (!registerForm.phone) {
    ElMessage.warning('请先输入手机号')
    return
  }
  
  if (!/^1[3-9]\d{9}$/.test(registerForm.phone)) {
    ElMessage.warning('手机号格式不正确')
    return
  }
  
  try {
    const response = await request.post('/company/send-verify-code', {
      phone: registerForm.phone
    })
    
    if (response.data.code === 200) {
      ElMessage.success('验证码发送成功')
      countdown.value = 60
      const timer = setInterval(() => {
        countdown.value--
        if (countdown.value <= 0) {
          clearInterval(timer)
        }
      }, 1000)
    } else {
      ElMessage.error(response.data.message || '发送验证码失败')
    }
  } catch (error) {
    console.error('发送验证码失败:', error)
    ElMessage.error('发送验证码失败')
  }
}

const handleBusinessLicenseChange = async (file) => {
  try {
    const formData = new FormData()
    formData.append('file', file.raw)
    
    const response = await request.post('/upload/image', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    
    if (response.data.code === 200) {
      registerForm.businessLicense = response.data.data.url
      ElMessage.success('营业执照上传成功')
    } else {
      ElMessage.error(response.data.message || '营业执照上传失败')
    }
  } catch (error) {
    console.error('营业执照上传失败:', error)
    ElMessage.error('营业执照上传失败')
  }
}

const deleteBusinessLicense = () => {
  registerForm.businessLicense = ''
  ElMessage.success('已删除营业执照')
}

const handleIdCardFrontChange = async (file) => {
  try {
    const formData = new FormData()
    formData.append('file', file.raw)

    const response = await request.post('/upload/image', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    if (response.data.code === 200) {
      registerForm.legalIdCardFront = response.data.data.url
      ElMessage.success('身份证正面上传成功')
    } else {
      ElMessage.error(response.data.message || '身份证正面上传失败')
    }
  } catch (error) {
    console.error('身份证正面上传失败:', error)
    ElMessage.error('身份证正面上传失败')
  }
}

const handleIdCardBackChange = async (file) => {
  try {
    const formData = new FormData()
    formData.append('file', file.raw)

    const response = await request.post('/upload/image', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    if (response.data.code === 200) {
      registerForm.legalIdCardBack = response.data.data.url
      ElMessage.success('身份证反面上传成功')
    } else {
      ElMessage.error(response.data.message || '身份证反面上传失败')
    }
  } catch (error) {
    console.error('身份证反面上传失败:', error)
    ElMessage.error('身份证反面上传失败')
  }
}

const deleteIdCard = (side) => {
  if (side === 'front') {
    registerForm.legalIdCardFront = ''
    ElMessage.success('已删除身份证正面')
  } else if (side === 'back') {
    registerForm.legalIdCardBack = ''
    ElMessage.success('已删除身份证反面')
  }
}

const handlePlaquePhotoChange = async (file) => {
  try {
    const formData = new FormData()
    formData.append('file', file.raw)
    
    const response = await request.post('/upload/image', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    
    if (response.data.code === 200) {
      registerForm.plaquePhoto = response.data.data.url
      ElMessage.success('牌匾照片上传成功')
    } else {
      ElMessage.error(response.data.message || '牌匾照片上传失败')
    }
  } catch (error) {
    console.error('牌匾照片上传失败:', error)
    ElMessage.error('牌匾照片上传失败')
  }
}

const deletePlaquePhoto = () => {
  registerForm.plaquePhoto = ''
  ElMessage.success('已删除牌匾照片')
}

const submitRegister = async () => {
  if (!usernameAvailable.value && !isRecallMode.value) {
      ElMessage.warning('请先确认企业名称可用')
      return
    }
  
  try {
    await registerFormRef.value.validate()
  } catch (error) {
    ElMessage.warning('请填写完整信息')
    return
  }
  
  loading.value = true
  
  try {
    let response
    if (isRecallMode.value) {
      response = await request.put(`/company/update/${existingCompanyId.value}`, registerForm)
      if (response.data.code === 200) {
        ElMessage.success('注册信息更新成功')
        setTimeout(() => {
          router.push({
            path: '/company-check',
            query: { companyName: registerForm.companyName }
          })
        }, 2000)
      } else {
        ElMessage.error(response.data.message || '更新失败')
      }
    } else {
      response = await request.post('/company/register', registerForm)
      if (response.data.code === 200) {
        ElMessage.success('注册申请提交成功，请等待管理员审核')
        setTimeout(() => {
          router.push({
            path: '/company-check',
            query: { companyName: registerForm.companyName }
          })
        }, 2000)
      } else {
        ElMessage.error(response.data.message || '注册申请提交失败')
      }
    }
  } catch (error) {
    console.error('注册申请提交失败:', error)
    ElMessage.error('注册申请提交失败')
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  router.push('/login')
}
</script>

<style scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: #f5f5f5;
  padding: 20px;
}

.register-box {
  width: 100%;
  max-width: 800px;
  background: white;
  border-radius: 10px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  padding: 40px;
}

.register-header {
  text-align: center;
  margin-bottom: 40px;
}

.register-header h2 {
  margin: 0 0 10px 0;
  color: #333;
  font-size: 28px;
}

.register-header p {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.audit-notice {
  margin-bottom: 30px;
}

.audit-notice p {
  margin: 5px 0;
  font-size: 14px;
  line-height: 1.6;
}

.register-form {
  margin-top: 30px;
}

.verify-code-btn {
  margin-left: 10px;
}

.check-result {
  margin-top: 5px;
  font-size: 12px;
}

.check-result.success {
  color: #67c23a;
}

.check-result.error {
  color: #f56c6c;
}

.file-preview {
  margin-top: 10px;
  position: relative;
  width: 200px;
  border: 1px solid #ddd;
  border-radius: 4px;
  overflow: hidden;
}

.file-preview img {
  width: 100%;
  height: 126px;
  object-fit: cover;
  display: block;
}

.upload-trigger {
  width: 100%;
  height: 120px;
  border: 2px dashed #d9d9d9;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s;
  background-color: #fafafa;
}

.upload-trigger:hover {
  border-color: #409eff;
  background-color: #f0f9ff;
}

.upload-icon {
  font-size: 32px;
  color: #8c939d;
  margin-bottom: 8px;
}

.upload-text {
  font-size: 14px;
  color: #606266;
  margin-bottom: 4px;
}

.upload-tip {
  font-size: 12px;
  color: #909399;
}

/* 身份证上传容器 */
.id-card-upload-container {
  display: flex;
  gap: 30px;
}

.id-card-upload-item {
  flex: 1;
  max-width: 220px;
}

.id-card-label-title {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
  font-weight: 500;
}

/* 身份证上传框样式 */
.id-card-trigger {
  width: 200px;
  height: 126px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.id-card-preview-single {
  position: relative;
  width: 200px;
  height: 126px;
  border: 1px solid #ddd;
  border-radius: 4px;
  overflow: hidden;
}

.id-card-preview-single img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 旧的样式保留兼容 */
.id-card-preview {
  display: flex;
  gap: 20px;
  margin-top: 15px;
}

.id-card-item {
  position: relative;
  width: 200px;
  border: 1px solid #ddd;
  border-radius: 4px;
  overflow: hidden;
}

.id-card-item img {
  width: 100%;
  height: 126px;
  object-fit: cover;
  display: block;
}

.id-card-label {
  text-align: center;
  padding: 8px;
  background-color: #f5f7fa;
  font-size: 12px;
  color: #606266;
  border-top: 1px solid #ddd;
}

.delete-btn {
  position: absolute;
  top: 5px;
  right: 5px;
  padding: 4px 8px;
  font-size: 12px;
}
</style>
