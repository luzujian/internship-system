<template>
  <div class="forgot-container">
    <div class="forgot-box">
      <div class="forgot-header">
        <h2>忘记密码</h2>
        <p>通过账号绑定的邮箱验证身份后重置密码（学生 / 教师 / 企业账号）</p>
      </div>

      <el-steps :active="activeStep" finish-status="success" align-center class="forgot-steps">
        <el-step title="验证身份" />
        <el-step title="重置密码" />
        <el-step title="完成" />
      </el-steps>

      <!-- 步骤1：账号 + 邮箱 + 验证码，确认后进入重置密码 -->
      <el-form
        v-if="activeStep === 0"
        ref="identityFormRef"
        :model="form"
        :rules="identityRules"
        label-width="90px"
        class="forgot-form"
        @submit.prevent
      >
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" placeholder="请输入登录账号（学号 / 工号 / 企业名称）" clearable />
        </el-form-item>
        <el-form-item label="绑定邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入账号绑定的邮箱" clearable />
          <div class="form-tip">请填写账号设置中的账号邮箱；新注册企业默认绑定联系人邮箱</div>
        </el-form-item>
        <el-form-item label="验证码" prop="code">
          <el-input v-model="form.code" placeholder="请输入6位邮箱验证码" maxlength="6" clearable class="code-input" />
          <el-button type="primary" :disabled="countdown > 0" :loading="sending" @click="sendCode" class="verify-code-btn">
            {{ countdown > 0 ? `${countdown}秒后重试` : '发送验证码' }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 步骤2：新密码 -->
      <el-form
        v-if="activeStep === 1"
        ref="resetFormRef"
        :model="form"
        :rules="resetRules"
        label-width="90px"
        class="forgot-form"
        @submit.prevent
      >
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" placeholder="请输入新密码" show-password />
          <div v-if="passwordHint" class="form-tip">{{ passwordHint }}</div>
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="请再次输入新密码" show-password @keyup.enter="submitReset" />
        </el-form-item>
        <el-form-item>
          <el-button @click="activeStep = 0">上一步</el-button>
          <el-button type="primary" :loading="resetting" @click="submitReset">重置密码</el-button>
        </el-form-item>
      </el-form>

      <!-- 步骤3：完成 -->
      <div v-if="activeStep === 2" class="forgot-success">
        <el-result icon="success" title="密码重置成功" sub-title="请使用新密码登录系统">
          <template #extra>
            <el-button type="primary" @click="goLogin">{{ countdown > 0 ? `${countdown}秒后返回登录` : '返回登录' }}</el-button>
          </template>
        </el-result>
      </div>

      <div class="footer-actions">
        <span class="back-link-text" @click="goLogin">返回登录</span>
        <el-button v-if="activeStep === 0" type="primary" :loading="verifying" @click="confirmIdentity">确认</el-button>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const activeStep = ref(0)
const sending = ref(false)
const verifying = ref(false)
const resetting = ref(false)
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const identityFormRef = ref(null)
const resetFormRef = ref(null)
const passwordHint = ref('')

const form = reactive({
  username: '',
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})

const startCountdown = (seconds: number) => {
  countdown.value = seconds
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

onMounted(async () => {
  try {
    interface PasswordRules {
      minLength?: number
      min_length?: number
      complexity?: string
    }
    const response = await request.get<PasswordRules>('/auth/password-rules')
    if (response.code === 200 && response.data) {
      const rulesData = response.data
      const minLength = rulesData.minLength ?? rulesData.min_length
      const complexity = rulesData.complexity
      const parts: string[] = []
      if (minLength) parts.push(`长度不少于${minLength}位`)
      if (typeof complexity === 'string') {
        if (complexity.includes('lowercase')) parts.push('含小写字母')
        if (complexity.includes('uppercase')) parts.push('含大写字母')
        if (complexity.includes('number')) parts.push('含数字')
        if (complexity.includes('special')) parts.push('含特殊字符')
      }
      if (parts.length) passwordHint.value = '新密码要求：' + parts.join('、')
    }
  } catch {
    // 规则获取失败不阻塞流程
  }
})

const identityRules = {
  username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入绑定邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码必须是6位', trigger: 'blur' }
  ]
}

const resetRules = {
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (e?: Error) => void) => {
        if (value !== form.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

const sendCode = async () => {
  try {
    // 发送验证码只需校验账号 + 邮箱，不校验验证码框
    await identityFormRef.value.validateField(['username', 'email'])
  } catch {
    return
  }
  sending.value = true
  try {
    const response = await request.post('/auth/forgot-password/send-code', {
      username: form.username.trim(),
      email: form.email.trim()
    })
    if (response.code === 200) {
      ElMessage.success('验证码发送成功，请查收邮件')
      startCountdown(60)
    } else {
      ElMessage.error(response.message || '发送验证码失败')
    }
  } catch (error) {
    console.error('发送验证码失败:', error)
    ElMessage.error(getServerMessage(error, '发送验证码失败'))
  } finally {
    sending.value = false
  }
}

const confirmIdentity = async () => {
  try {
    await identityFormRef.value.validate()
  } catch {
    return
  }
  verifying.value = true
  try {
    const response = await request.post('/auth/forgot-password/verify-code', {
      username: form.username.trim(),
      email: form.email.trim(),
      code: form.code.trim()
    })
    if (response.code === 200) {
      activeStep.value = 1
    } else {
      ElMessage.error(response.message || '身份验证失败')
    }
  } catch (error) {
    console.error('身份验证失败:', error)
    ElMessage.error(getServerMessage(error, '身份验证失败'))
  } finally {
    verifying.value = false
  }
}

const submitReset = async () => {
  try {
    await resetFormRef.value.validate()
  } catch {
    return
  }
  resetting.value = true
  try {
    const response = await request.post('/auth/forgot-password/reset', {
      username: form.username.trim(),
      email: form.email.trim(),
      code: form.code.trim(),
      newPassword: form.newPassword
    })
    if (response.code === 200) {
      ElMessage.success('密码重置成功')
      activeStep.value = 2
      startCountdown(5)
      const backTimer = setInterval(() => {
        if (countdown.value <= 0) {
          clearInterval(backTimer)
          goLogin()
        }
      }, 1000)
    } else {
      ElMessage.error(response.message || '密码重置失败')
    }
  } catch (error) {
    console.error('密码重置失败:', error)
    ElMessage.error(getServerMessage(error, '密码重置失败'))
  } finally {
    resetting.value = false
  }
}

const goLogin = () => {
  router.push('/login')
}

// 拦截器会把业务失败转为 reject，优先展示后端返回的业务消息
const getServerMessage = (error: unknown, fallback: string) => {
  const data = (error as { response?: { data?: { message?: string } } })?.response?.data
  return data?.message || (error as Error)?.message || fallback
}
</script>

<style scoped>
.forgot-container {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  background-image: url('../assets/2.png');
  background-repeat: no-repeat;
  background-size: 100% 100%;
  background-position: center;
  padding: 12vh 16px 24px;
}

.forgot-box {
  width: 100%;
  max-width: 560px;
  background: white;
  border-radius: 10px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  padding: 36px 40px 28px;
}

.forgot-header {
  text-align: center;
  margin-bottom: 24px;
}

.forgot-header h2 {
  margin: 0 0 8px 0;
  color: #333;
  font-size: 24px;
}

.forgot-header p {
  margin: 0;
  color: #666;
  font-size: 13px;
}

.forgot-steps {
  margin-bottom: 28px;
}

.forgot-form {
  margin-top: 10px;
}

.verify-code-btn {
  margin-left: 10px;
  flex-shrink: 0;
}

.code-input {
  flex: 1;
}

.form-tip {
  margin-top: 5px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.forgot-success {
  margin-top: 10px;
}

.confirm-action {
  margin-top: 20px;
}

.confirm-action .el-button {
  width: 100%;
}

.footer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20px;
}

.back-link {
  text-align: center;
  margin-top: 20px;
}

.back-link-text {
  font-size: 14px;
  color: #409eff;
  cursor: pointer;
}

.back-link-text:hover {
  color: #337ecc;
}

@media screen and (max-width: 768px) {
  .forgot-container {
    padding: calc(16px + var(--safe-top, 0px)) 12px 24px;
    background-size: cover;
  }

  .forgot-box {
    padding: 24px 18px 20px;
  }

  .verify-code-btn {
    margin-left: 0;
    margin-bottom: 8px;
    width: 100%;
  }
}
</style>
