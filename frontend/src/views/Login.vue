<template>
  <div class="container">
    <div class="logo-container">
      <div class="logo"></div>
      <div class="system-title-wrapper">
        <div class="system-title">DeepIntern</div>
        <div class="system-subtitle">深耕实习，探索未来</div>
      </div>
    </div>
    <div class="university-title-container">
      <div class="university-title">智慧实习平台</div>
      <div class="university-title-english">DeepIntern</div>
    </div>
    <div class="form-wrapper">
      <div class="additional-image-container"></div>
      <div class="login-form">
        <el-form>
        <p class="title">欢迎登录</p>
        
        <el-form-item prop="username" class="form-item">
          <el-input 
            v-model="loginForm.username" 
            placeholder="请输入账号" 
            clearable 
            class="custom-input" 
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password" class="form-item" >
          <el-input
            type="password"
            v-model="loginForm.password"
            placeholder="请输入密码"
            class="custom-input"
            show-password
            :prefix-icon="Lock"
            @keyup.enter="handleLogin"
          ></el-input>
        </el-form-item>
        <div class="button-wrapper">
          <el-form-item class="button-container">
            <el-button class="button" type="primary" @click="handleLogin" :loading="loading">登 录</el-button>
          </el-form-item>
          <div class="register-link">
            <span class="register-link-text" @click="goToCompanyRegister">企业账号申请 ></span>
          </div>
        </div>
        <div class="version-text">v1.0</div>
      </el-form>
    </div>
  </div>
  </div>
</template>

<script setup>
import {ref, onMounted} from 'vue'
import {ElMessage} from 'element-plus'
import {User, Lock} from '@element-plus/icons-vue'
import {useAuthStore} from '../store/auth'
import {useRouter} from 'vue-router'
import request from '../utils/request'
import { preloadHomePage } from '../router/preloadRoutes'

const loginForm = ref({
  username: '',
  password: ''
})

const loading = ref(false)
const imageLoaded = ref(false)
const authStore = useAuthStore()
const router = useRouter()

const preloadDashboardData = async (role) => {
  try {
    const preloadTasks = []
    
    if (role === 'ROLE_ADMIN') {
      preloadTasks.push(
        request.get('/admin/dashboard/stats').catch(() => {}),
        request.get('/admin/logs/recent').catch(() => {}),
        request.get('/admin/feedback/recent').catch(() => {})
      )
    } else if (role === 'ROLE_STUDENT') {
      preloadTasks.push(
        request.get('/student/home/internship-status').catch(() => {})
      )
    } else if (role === 'ROLE_COMPANY') {
      preloadTasks.push(
        request.get('/company/stats').catch(() => {}),
        request.get('/company/applications').catch(() => {})
      )
    } else if (role.startsWith('ROLE_TEACHER')) {
      preloadTasks.push(
        request.get('/teacher/students').catch(() => {})
      )
    }
    
    if (preloadTasks.length > 0) {
      await Promise.allSettled(preloadTasks)
    }
  } catch (error) {
    console.warn('预加载数据失败:', error)
  }
}

onMounted(() => {
  const leftImg = new Image()
  leftImg.src = new URL('../assets/3.webp', import.meta.url).href
  
  const logoImg = new Image()
  logoImg.src = new URL('../assets/newlogo.png', import.meta.url).href
})

const handleLogin = async () => {
  if (!loginForm.value.username || !loginForm.value.password) {
    ElMessage.warning('请输入账号和密码');
    return;
  }

  const username = loginForm.value.username.trim();

  loading.value = true;
  try {
    const success = await authStore.login(
        loginForm.value.username,
        loginForm.value.password
    );

    if (success) {
      console.log('[Login] 登录成功，检查localStorage内容:')
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i)
        if (key.includes('token') || key.includes('role') || key.includes('accessToken')) {
          console.log(`[Login]   ${key}:`, localStorage.getItem(key) ? '存在' : '不存在')
        }
      }
      console.log('[Login] authStore.role:', authStore.role)
      console.log('[Login] authStore.token:', authStore.token ? '存在' : '不存在')
      
      ElMessage.success('登录成功');

      // 清除鼓励气泡的"不再提醒"状态，让重新登录后气泡可以再次显示
      const currentRole = authStore.role || 'ROLE_ADMIN'
      localStorage.removeItem(`internshipAIEncouragementDisabled_${currentRole.toLowerCase()}`)
      // 也清除其他可能存在的角色
      ;['teacher', 'admin', 'student', 'company'].forEach(role => {
        localStorage.removeItem(`internshipAIEncouragementDisabled_${role}`)
      })

      preloadHomePage(authStore.role)
      preloadDashboardData(authStore.role)
      
      if (authStore.role === 'ROLE_ADMIN') {
        router.push('/admin/dashboard');
      } else if (authStore.role.startsWith('ROLE_TEACHER')) {
        router.push('/teacher/home');
      } else if (authStore.role === 'ROLE_STUDENT') {
        router.push('/student/home');
      } else if (authStore.role === 'ROLE_COMPANY') {
        router.push('/company/dashboard');
      }
    }
  } catch (error) {
    ElMessage.error('登录失败，请稍后再试');
    console.error('登录失败:', error);
  } finally {
    loading.value = false;
  }
};

const goToCompanyRegister = () => {
  router.push('/company-check');
}
</script>

<style scoped>
/* 基于 1920x1080 设计尺寸的 vw/vh 换算：1vw=19.2px, 1vh=10.8px */

.container {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  width: 100vw;
  height: 100vh;
  background-image: url('../assets/2.png');
  background-repeat: no-repeat;
  background-size: 100% 100%;
  background-position: center;
  padding-top: 14vh;
  position: relative;
}

.logo-container {
  position: absolute;
  top: 1.85vh;
  left: 3.125vw;
  display: flex;
  align-items: center;
}

.logo {
  width: 8.85vw;
  height: 15.74vh;
  background-image: url('../assets/newlogo.png');
  background-size: contain;
  background-position: center;
  background-repeat: no-repeat;
  will-change: transform;
  transform: translateZ(0);
  backface-visibility: hidden;
}

.login-form {
  width: 26.56vw;
  height: 50.93vh;
  border: 0.052vw solid #e0e0e0;
  border-radius: 0.52vw;
  background-color: white;
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 2.08vw;
}

.title {
  font-size: 2.6vw;
  text-align: center;
  margin-bottom: 4.63vh;
  margin-top: 1.85vh;
  font-weight: bold;
}

.button-container {
  display: flex;
  justify-content: center;
  margin-top: 3.7vh;
}

.button-container :deep(.el-form-item__content) {
  display: flex;
  justify-content: center;
}

.button-wrapper {
  position: relative;
  margin-top: 3.7vh;
}

/* 按钮样式 */
.button {
  width: 18.23vw;
  height: 4.63vh;
  font-size: 0.94vw;
  border-radius: 0.42vw;
  font-weight: 500;
}

/* 覆盖 Element Plus 按钮内部字体 */
.button :deep(span) {
  font-size: 0.94vw;
}

/* 按钮内图标样式 */
.button :deep(.el-icon) {
  font-size: 0.94vw;
}

/* 企业注册链接样式 */
.register-link {
  text-align: center;
  margin-top: 2.78vh;
}

.register-link-text {
  font-size: 0.83vw;
  font-weight: 500;
  color: #67C23A;
  cursor: pointer;
  transition: all 0.3s ease;
  text-decoration: none;
}

.register-link-text:hover {
  color: #529B2E;
  text-decoration: none;
}

/* 系统标题容器 */
.system-title-wrapper {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  position: relative;
  margin-left: 1.04vw;
}

/* 系统标题样式 */
.system-title {
  font-size: 4.375vw;
  font-weight: 900;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  letter-spacing: -0.052vw;
  line-height: 1.1;
}

/* 系统副标题样式 */
.system-subtitle {
  font-size: 2.22vh;
  font-weight: 600;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif;
  color: #333;
  margin-top: 1.11vh;
  margin-left: 0.42vw;
  letter-spacing: 0.21vw;
}

/* 大学标题容器 */
.university-title-container {
  position: absolute;
  bottom: 0.93vh;
  right: 0.52vw;
  text-align: center;
  transform: perspective(500px) rotateY(0deg);
  transition: transform 0.3s ease;
}

/* 大学标题样式 */
.university-title {
  font-size: 2.78vh;
  font-weight: bold;
  font-family: 'Microsoft YaHei', sans-serif;
  background: linear-gradient(135deg, #ff6b6b 0%, #feca57 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-shadow:
    2px 2px 4px rgba(0, 0, 0, 0.3),
    -1px -1px 0 rgba(255, 255, 255, 0.8),
    1px 1px 0 rgba(0, 0, 0, 0.5);
  opacity: 0.2;
}

/* 大学英文标题样式 */
.university-title-english {
  font-size: 1.48vh;
  font-weight: bold;
  font-family: 'Microsoft YaHei', sans-serif;
  background: linear-gradient(135deg, #ff6b6b 0%, #feca57 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-shadow:
    1px 1px 2px rgba(0, 0, 0, 0.3),
    -1px -1px 0 rgba(255, 255, 255, 0.8),
    1px 1px 0 rgba(0, 0, 0, 0.5);
  opacity: 0.2;
  margin-top: 0.46vh;
  letter-spacing: 0.052vw;
}

/* 容器悬停效果 */
.university-title-container:hover {
  transform: perspective(500px) rotateY(0deg) scale(1.1);
}

/* 输入框样式 */
.custom-input {
  width: 18.23vw;
  height: 4.44vh;
  font-size: 0.83vw;
}

/* 覆盖 Element Plus 输入框内部字体 */
.custom-input :deep(.el-input__wrapper) {
  font-size: 0.83vw;
}

.custom-input :deep(.el-input__inner) {
  font-size: 0.83vw;
}

/* 输入框图标样式 */
.custom-input :deep(.el-input__prefix) {
  font-size: 0.83vw;
}

.custom-input :deep(.el-input__prefix .el-icon) {
  font-size: 0.83vw;
}

.custom-input :deep(.el-input__suffix) {
  font-size: 0.83vw;
}

.custom-input :deep(.el-input__suffix .el-icon) {
  font-size: 0.83vw;
}

/* 表单项目间距 */
.form-item {
  margin-top: 2.31vh;
}

.form-item :deep(.el-form-item__content) {
  display: flex;
  justify-content: center;
}

/* 确保Element Plus的输入框包装器也居中 */
.el-form-item :deep(.el-input) {
  width: 18.23vw;
}

/* 版本号文本样式 */
.version-text {
  position: absolute;
  bottom: 1.2vh;
  right: 1.2vw;
  font-size: 1.57vh;
  color: #909399;
  opacity: 0.8;
}

/* 表单包装器 */
.form-wrapper {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  transform: translateY(10vh);
}

/* 左侧图片区域 */
.additional-image-container {
  width: 33.85vw;
  height: 50.93vh;
  margin-right: -0.365vw;
  z-index: 1;
  background-image: url('../assets/3.webp');
  background-size: 100% 100%;
  background-position: center;
  background-repeat: no-repeat;
  border-radius: 0.52vw 0 0 0.52vw;
  border: 0.052vw solid #e0e0e0;
  border-right: none;
  background-color: #f5f7fa;
  will-change: transform;
  transform: translateZ(0);
  backface-visibility: hidden;
}
</style>