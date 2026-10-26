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
          <p>• 提交申请后，相关负责人会在 1-3 个工作日内完成审核</p>
          <p>• 审核通过后，可用企业名称和初始密码登录；联系邮箱同时用于找回密码</p>
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

        <el-form-item label="统一社会信用代码" prop="creditCode" class="credit-code-item">
          <el-input
            v-model="registerForm.creditCode"
            placeholder="请输入统一社会信用代码"
            :disabled="isRecallMode"
          />
        </el-form-item>

        <el-form-item label="所属行业" prop="industry">
          <el-select v-model="registerForm.industry" placeholder="请选择所属行业" style="width: 100%">
            <el-option
              v-for="item in industryOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="企业规模" prop="scale">
          <el-select v-model="registerForm.scale" placeholder="请选择企业规模" style="width: 100%">
            <el-option
              v-for="item in scaleOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="联系人" prop="contactPerson">
          <el-input v-model="registerForm.contactPerson" placeholder="请输入联系人姓名" />
        </el-form-item>

        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="registerForm.contactPhone" placeholder="请输入联系人手机或固定电话" maxlength="20" inputmode="tel" />
        </el-form-item>

        <el-form-item label="企业地址" prop="province">
          <elui-china-area-dht
            v-model="chinaAreaValue"
            @change="handleAddressChange"
            placeholder="请选择省/市/区"
            style="width: 100%"
          />
          <el-input
            v-model="registerForm.detailAddress"
            placeholder="请输入详细地址，如街道、门牌号等"
            style="margin-top: 10px;"
          />
        </el-form-item>

        <el-form-item label="联系邮箱" prop="contactEmail">
          <el-input v-model="registerForm.contactEmail" placeholder="请输入联系人常用邮箱" maxlength="100" />
          <div class="form-tip">同时绑定企业账号，用于接收通知和找回密码</div>
        </el-form-item>

        <el-form-item label="企业网站" prop="website">
          <el-input v-model="registerForm.website" placeholder="请输入企业网站（选填）" />
        </el-form-item>

        <el-form-item label="企业简介" prop="introduction">
          <el-input
            v-model="registerForm.introduction"
            type="textarea"
            :rows="4"
            placeholder="请输入企业简介（选填）"
          />
        </el-form-item>

        <el-form-item label="挂牌实习基地" prop="isInternshipBase">
          <el-radio-group v-model="registerForm.isInternshipBase">
            <el-radio :label="0">否</el-radio>
            <el-radio :label="1">是</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="接受兜底" prop="acceptBackup">
          <el-radio-group v-model="registerForm.acceptBackup">
            <el-radio :label="0">否</el-radio>
            <el-radio :label="1">是</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="是否校友单位" prop="isAlumniUnit">
          <el-radio-group v-model="registerForm.isAlumniUnit" @change="handleAlumniUnitChange">
            <el-radio :label="0">否</el-radio>
            <el-radio :label="1">是</el-radio>
          </el-radio-group>
        </el-form-item>

        <template v-if="registerForm.isAlumniUnit === 1">
          <el-form-item label="校友信息" prop="alumniMembers">
            <div class="alumni-members">
              <div v-for="(member, index) in registerForm.alumniMembers" :key="index" class="alumni-member-card">
                <div class="alumni-member-title">
                  <span>校友 {{ index + 1 }}</span>
                  <el-button text type="danger" :disabled="registerForm.alumniMembers.length === 1" @click="removeAlumniMember(index)">删除</el-button>
                </div>
                <el-input v-model="member.name" placeholder="姓名" maxlength="50" />
                <el-input v-model="member.phone" placeholder="联系电话" maxlength="20" />
                <el-input v-model="member.position" placeholder="在企业的任职" maxlength="100" />
              </div>
              <el-button type="primary" plain :icon="Plus" @click="addAlumniMember">添加校友</el-button>
            </div>
          </el-form-item>
        </template>

        <el-form-item prop="hasTrainingBaseAgreement">
          <template #label>
            <span style="line-height: 1.4;">是否与学校签署<br/>实训基地的协议</span>
          </template>
          <el-radio-group v-model="registerForm.hasTrainingBaseAgreement">
            <el-radio :label="0">否</el-radio>
            <el-radio :label="1">是</el-radio>
          </el-radio-group>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { EluiChinaAreaDht } from 'elui-china-area-dht'
import request from '@/utils/request'

const router = useRouter()
const route = useRoute()
const registerFormRef = ref(null)
const loading = ref(false)
const usernameChecked = ref(false)
const usernameAvailable = ref(false)
const isRecallMode = ref(false)
const existingCompanyId = ref<number | null>(null)

const industryOptions = [
  { label: 'IT企业', value: 'it' },
  { label: '医疗设备企业', value: 'medical_equipment' },
  { label: '医院', value: 'hospital' },
  { label: '其它', value: 'other' }
]

const scaleOptions = [
  { label: '1-50 人', value: '1-50' },
  { label: '51-100 人', value: '51-100' },
  { label: '101-500 人', value: '101-500' },
  { label: '501-1000 人', value: '501-1000' },
  { label: '1000 人以上', value: '1000+' }
]

const chinaAreaValue = ref([])

const chinaData = new EluiChinaAreaDht.ChinaArea().chinaAreaflat

const handleAddressChange = (e) => {
  chinaAreaValue.value = e
  if (e && e.length > 0) {
    const provinceCode = e[0]
    const cityCode = e[1]
    const districtCode = e[2]

    registerForm.province = chinaData[provinceCode]?.label || ''
    registerForm.city = chinaData[cityCode]?.label || ''
    registerForm.district = chinaData[districtCode]?.label || ''
  } else {
    registerForm.province = ''
    registerForm.city = ''
    registerForm.district = ''
  }
}

const escapeHtml = (str: string) => {
  if (!str) return ''
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

const createEmptyAlumniMember = () => ({ name: '', phone: '', position: '' })

const registerForm = reactive({
  companyName: '',
  creditCode: '',
  industry: '',
  scale: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  contactPerson: '',
  contactPhone: '',
  contactEmail: '',
  website: '',
  introduction: '',
  isInternshipBase: 0,
  acceptBackup: 0,
  isAlumniUnit: 0,
  alumniMembers: [] as Array<{ name: string; phone: string; position: string }>,
  hasTrainingBaseAgreement: 0
})

const handleAlumniUnitChange = (value: number) => {
  if (value === 1 && registerForm.alumniMembers.length === 0) {
    registerForm.alumniMembers.push(createEmptyAlumniMember())
  }
  if (value === 0) registerForm.alumniMembers = []
}

const addAlumniMember = () => registerForm.alumniMembers.push(createEmptyAlumniMember())

const removeAlumniMember = (index: number) => {
  if (registerForm.alumniMembers.length > 1) registerForm.alumniMembers.splice(index, 1)
}

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

interface ExistingCompanyRow {
  id?: number | string
  creditCode?: string
  contactPerson?: string
  contactPhone?: string
  contactEmail?: string
  email?: string
  industry?: string
  scale?: string
  province?: string
  city?: string
  district?: string
  detailAddress?: string
  website?: string
  introduction?: string
  isInternshipBase?: number
  acceptBackup?: number
  isAlumniUnit?: number
  alumniMembers?: Array<{ name?: string; phone?: string; position?: string }>
  alumniRepresentativeName?: string
  alumniRepresentativePhone?: string
  alumniRepresentativePosition?: string
  hasTrainingBaseAgreement?: number
}

const loadExistingData = async () => {
  try {
    const response = await request.get<ExistingCompanyRow>('/company/check-status', {
      params: { companyName: registerForm.companyName }
    })

    if (response.code === 200) {
      const data = response.data
      const companyId = Number(data.id)
      if (!Number.isSafeInteger(companyId) || companyId <= 0) throw new Error('企业编号无效')
      existingCompanyId.value = companyId
      registerForm.creditCode = data.creditCode || ''
      registerForm.contactPerson = data.contactPerson || ''
      registerForm.contactPhone = data.contactPhone || ''
      registerForm.contactEmail = data.email || data.contactEmail || ''
      registerForm.industry = data.industry || ''
      registerForm.scale = data.scale || ''
      registerForm.province = data.province || ''
      registerForm.city = data.city || ''
      registerForm.district = data.district || ''
      registerForm.detailAddress = data.detailAddress || ''
      registerForm.website = data.website || ''
      registerForm.introduction = data.introduction || ''

      registerForm.isInternshipBase = data.isInternshipBase || 0
      registerForm.acceptBackup = data.acceptBackup || 0
      registerForm.isAlumniUnit = data.isAlumniUnit || 0
      registerForm.alumniMembers = Array.isArray(data.alumniMembers) && data.alumniMembers.length
        ? data.alumniMembers.map(member => ({ name: member.name || '', phone: member.phone || '', position: member.position || '' }))
        : (registerForm.isAlumniUnit === 1
            ? [{
                name: data.alumniRepresentativeName || '',
                phone: data.alumniRepresentativePhone || '',
                position: data.alumniRepresentativePosition || ''
              }]
            : [])
      registerForm.hasTrainingBaseAgreement = data.hasTrainingBaseAgreement || 0
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
    { min: 2, max: 255, message: '企业名称长度必须在 2-255 个字符之间', trigger: 'blur' }
  ],
  creditCode: [
    { required: true, message: '请输入统一社会信用代码', trigger: 'blur' }
  ],
  industry: [
    { required: true, message: '请选择所属行业', trigger: 'change' }
  ],
  scale: [
    { required: true, message: '请选择企业规模', trigger: 'change' }
  ],
  province: [
    { required: true, message: '请选择省份', trigger: 'change' }
  ],
  detailAddress: [
    { required: true, message: '请输入详细地址', trigger: 'blur' },
    { min: 5, max: 255, message: '详细地址长度必须在 5-255 个字符之间', trigger: 'blur' }
  ],
  website: [
    { type: 'url', message: '请输入正确的网址格式', trigger: 'blur' }
  ],
  contactPerson: [
    { required: true, message: '请输入联系人', trigger: 'blur' },
    { min: 1, max: 50, message: '联系人长度必须在 1-50 个字符之间', trigger: 'blur' }
  ],
  contactPhone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^[+0-9()（）\-\s转]{7,20}$/, message: '请输入有效的手机或固定电话号码', trigger: 'blur' }
  ],
  contactEmail: [
    { required: true, message: '请输入联系邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  alumniMembers: [
    {
      validator: (rule, value, callback) => {
        if (registerForm.isAlumniUnit === 1) {
          if (!Array.isArray(value) || value.length === 0) return callback(new Error('请至少填写一名校友'))
          const invalid = value.find(member => !member.name?.trim() || !member.phone?.trim() || !member.position?.trim())
          if (invalid) return callback(new Error('请完整填写每名校友的姓名、电话和任职'))
          const tooLong = value.find(member => member.name.length > 50 || member.phone.length > 20 || member.position.length > 100)
          if (tooLong) return callback(new Error('校友信息长度超过限制'))
          callback()
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
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
    usernameAvailable.value = response.code === 200
  } catch (error) {
    console.error('检查企业名称失败:', error)
    usernameChecked.value = false
  }
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
    registerForm.contactEmail = registerForm.contactEmail.trim()
    registerForm.contactPhone = registerForm.contactPhone.replace(/\s+/g, '')
    let response
    if (isRecallMode.value) {
      response = await request.put(`/company/update/${existingCompanyId.value}`, registerForm)
      if (response.code === 200) {
        ElMessage.success('注册信息更新成功')
        setTimeout(() => {
          router.push({
            path: '/company-check',
            query: { companyName: registerForm.companyName }
          })
        }, 2000)
      } else {
        ElMessage.error(response.message || '更新失败')
      }
    } else {
      response = await request.post('/company/register', registerForm)
      if (response.code === 200) {
        ElMessageBox.alert(
          `<div style="text-align: left; line-height: 1.8;">
            <p style="font-size: 15px; color: #333; margin: 0 0 14px 0;">申请已提交，请耐心等待审核（1-3 个工作日）</p>
            <div style="background: #fff3e0; border-left: 4px solid #ff9800; padding: 10px 14px; margin-bottom: 14px; border-radius: 4px;">
              <p style="color: #e65100; font-weight: 600; margin: 0 0 4px 0; font-size: 14px;">审核通过后方可使用以下信息登录</p>
              <p style="color: #999; margin: 0; font-size: 12px;">未通过审核前，账号无法登录系统</p>
            </div>
            <div style="background: #f5f7fa; padding: 12px 14px; border-radius: 4px; font-size: 14px;">
              <p style="margin: 0 0 6px 0;">账 号：<b>${escapeHtml(registerForm.companyName)}</b></p>
              <p style="margin: 0;">密 码：<b>123456</b></p>
            </div>
          </div>`,
          '注册申请提交成功',
          { dangerouslyUseHTMLString: true, confirmButtonText: '我知道了', type: 'success' }
        ).then(() => {
          router.push({
            path: '/company-check',
            query: { companyName: registerForm.companyName }
          })
        })
      } else {
        ElMessage.error(response.message || '注册申请提交失败')
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
  align-items: flex-start;
  background-image: url('../assets/2.png');
  background-repeat: no-repeat;
  background-size: 100% 100%;
  background-position: center;
  padding-top: 15vh;
  position: relative;
}

.register-box {
  width: 100%;
  max-width: 800px;
  background: white;
  border-radius: 10px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  padding: 40px;
  margin-top: 80px;
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

.form-tip {
  margin-top: 5px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.alumni-members {
  width: 100%;
  display: grid;
  gap: 12px;
}

.alumni-member-card {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fafafa;
}

.alumni-member-title {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  color: #303133;
}

@media (max-width: 720px) {
  .alumni-member-card {
    grid-template-columns: 1fr;
  }
}

/* 手机端：单列布局，上传区满宽，长文本可换行 */
@media screen and (max-width: 768px) {
  .register-container {
    padding: calc(16px + var(--safe-top, 0px)) 12px 24px;
    background-size: cover;
  }

  .register-box {
    margin-top: 0;
    padding: 20px 16px;
    overflow-wrap: anywhere;
    word-break: break-word;
  }

  .register-header {
    margin-bottom: 20px;
  }

  .register-header h2 {
    font-size: 22px;
  }

  .id-card-upload-container {
    flex-direction: column;
    gap: 16px;
  }

  .id-card-upload-item {
    max-width: 100%;
  }

  .id-card-trigger,
  .id-card-preview-single,
  .file-preview {
    width: 100%;
    max-width: 280px;
  }

}

.credit-code-item :deep(.el-form-item__label) {
  white-space: nowrap;
  overflow: visible;
}
</style>
