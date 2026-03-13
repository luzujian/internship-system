<template>
  <div class="container">
    <div class="logo-container">
      <div class="logo"></div>
      <div class="system-title">学生实习管理系统</div>
    </div>
    <div class="university-title-container">
      <div class="university-title">广东医科大学</div>
      <div class="university-title-english">GUANGDONG MEDICAL UNIVERSITY</div>
    </div>
    <div class="form-wrapper">
      <!-- 左侧图片区域 - 使用CSS背景图实现同步渲染 -->
      <div class="additional-image-container"></div>
      <div class="login-form">
        <el-form>
        <p class="title">欢迎登录</p>
        
        <el-form-item prop="username" class="form-item">
          <el-input v-model="loginForm.username" placeholder="请输入账号" clearable class="custom-input" :prefix-icon="User"/>
        </el-form-item>
        <el-form-item prop="password" class="form-item" >
          <el-input
            type="password"
            v-model="loginForm.password"
            placeholder="请输入密码"
            class="custom-input"
            show-password
            :prefix-icon="Lock"
            @keyup.enter="autoLogin"
          ></el-input>
        </el-form-item>
        <div class="button-wrapper">
          <el-form-item class="button-container">
            <el-button class="button" type="primary" @click="autoLogin" :loading="loading">登 录</el-button>
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
import {ref} from 'vue'
import {ElMessage} from 'element-plus'
import {User, Lock} from '@element-plus/icons-vue'
import {useAuthStore} from '../store/auth'
import {useRouter} from 'vue-router'

const loginForm = ref({
  username: '',
  password: ''
})

const loading = ref(false)
const authStore = useAuthStore()
const router = useRouter()

const autoLogin = async () => {
  if (!loginForm.value.username || !loginForm.value.password) {
    ElMessage.warning('请输入用户名和密码');
    return;
  }

  const username = loginForm.value.username.trim();

  loading.value = true;
  try {
    const success = await authStore.autoLogin(
        loginForm.value.username,
        loginForm.value.password
    );

    if (success) {
      console.log('[Login] 自动登录成功，检查localStorage内容:')
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i)
        if (key.includes('token') || key.includes('role') || key.includes('accessToken')) {
          console.log(`[Login]   ${key}:`, localStorage.getItem(key) ? '存在' : '不存在')
        }
      }
      console.log('[Login] authStore.role:', authStore.role)
      console.log('[Login] authStore.token:', authStore.token ? '存在' : '不存在')
      
      ElMessage.success('登录成功');
      if (authStore.role === 'ROLE_ADMIN') {
        router.push('/admin/dashboard');
      } else if (authStore.role.startsWith('ROLE_TEACHER')) {
        router.push('/teacher/dashboard');
      } else if (authStore.role === 'ROLE_STUDENT') {
        router.push('/student/dashboard');
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
.container {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  height: 100vh;
  background-image: url('../assets/2.png');
  background-repeat: no-repeat;
  background-size: 100% 100%;
  background-position: center;
  padding-top: 15vh;
  position: relative;
}

.logo-container {
  position: absolute;
  top: 20px;
  left: 60px;
  display: flex;
  align-items: center;
}

.logo {
  width: 170px;
  height: 170px;
  background-image: url('../assets/schoollogo.png');
  background-size: contain;
  background-position: center;
  background-repeat: no-repeat;
}

.login-form {
  width: 510px;
  height: 550px;
  border: 1px solid #e0e0e0;
  border-radius: 10px;
  background-color: white;
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 40px;
}

.title {
  font-size: 50px;
  text-align: center;
  margin-bottom: 50px;
  margin-top: 20px;
  font-weight: bold;
}

.button-container {
  display: flex;
  justify-content: center;
  margin-top: 40px;
}

.button-container :deep(.el-form-item__content) {
  display: flex;
  justify-content: center;
}

.button-wrapper {
  position: relative;
  margin-top: 40px;
}

.button-container {
  display: flex;
  justify-content: center;
}

.button-container :deep(.el-form-item__content) {
  display: flex;
  justify-content: center;
}

.register-link {
  position: absolute;
  right: 40px;
  bottom: -40px;
}

/* 按钮样式 */
.button {
  width: 350px;
  height: 50px;
  font-size: 18px;
  border-radius: 8px;
  font-weight: 500;
}

/* 企业注册链接样式 */
.register-link {
  text-align: center;
  margin-top: 30px;
}

.register-link-text {
  font-size: 16px;
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

/* 系统标题样式 - 优化为圆润粗体 */
.system-title {
  font-size: 60px;
  font-weight: 900; /* 更粗的字体 */
  font-family: 'Microsoft YaHei', 'PingFang SC', 'Hiragino Sans GB', sans-serif; /* 圆润的中文字体 */
  margin-left: 20px;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  align-self: center;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1); /* 轻微阴影增强圆润感 */
}

/* 大学标题容器 */
.university-title-container {
  position: absolute;
  bottom: 40px;
  right: 40px;
  text-align: center;
  transform: perspective(500px) rotateY(0deg);
  transition: transform 0.3s ease;
}

/* 大学标题样式 - 右上角立体效果 */
.university-title {
  font-size: 25px;
  font-weight: bold;
  font-family: 'Microsoft YaHei', sans-serif;
  background: linear-gradient(135deg, #ff6b6b 0%, #feca57 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  /* 立体效果 */
  text-shadow: 
    2px 2px 4px rgba(0, 0, 0, 0.3),
    -1px -1px 0 rgba(255, 255, 255, 0.8),
    1px 1px 0 rgba(0, 0, 0, 0.5);
  /* 透明效果 */
    opacity: 0.2;
}

/* 大学英文标题样式 */
.university-title-english {
  font-size: 13px;
  font-weight: bold;
  font-family: 'Microsoft YaHei', sans-serif;
  background: linear-gradient(135deg, #ff6b6b 0%, #feca57 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  /* 立体效果 */
  text-shadow: 
    1px 1px 2px rgba(0, 0, 0, 0.3),
    -1px -1px 0 rgba(255, 255, 255, 0.8),
    1px 1px 0 rgba(0, 0, 0, 0.5);
  /* 透明效果 */
   opacity: 0.2;
  margin-top: 5px;
  letter-spacing: 1px;
}

/* 容器悬停效果 */
.university-title-container:hover {
  transform: perspective(500px) rotateY(0deg) scale(1.1);
}

/* 注册链接样式 */

/* 父容器：el-form-item（给输入框做定位参考） */
/* 输入框容器，实现水平居中 */

/* 输入框样式 */
.custom-input {
  width: 350px;  /* 输入框宽度 */
  height: 48px;  /* 输入框高度 */
  font-size: 16px;
}

/* 表单项目间距 */
.form-item {
  margin-top: 25px;
}

.form-item :deep(.el-form-item__content) {
  display: flex;
  justify-content: center;
}

/* 确保Element Plus的输入框包装器也居中 */
.el-form-item :deep(.el-input) {
  width: 350px;
}

/* 版本号文本样式 */
.version-text {
  position: absolute;
  bottom: 13px;
  right: 13px;
  font-size: 17px;
  color: #909399;
  opacity: 0.8;
}
/* 表单包装器 */
.form-wrapper {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  transform: translateX(0) translateY(80px);
}

/* 左侧图片区域 - 使用CSS背景图实现同步渲染 */
.additional-image-container {
  width: 650px;
  height: 550px;
  margin-right: -7px;
  z-index: 1;
  background-image: url('../assets/3.png');
  background-size: 100% 100%;
  background-position: center;
  background-repeat: no-repeat;
  border-radius: 10px 0 0 10px;
  border: 1px solid #e0e0e0;
  border-right: none;
  background-color: #f5f7fa;
}

/* 响应式设计 - 大屏幕保持原有样式 */

/* 中等屏幕响应式调整（1024px - 1400px） */
@media (max-width: 1400px) {
  .logo {
    width: 150px;
    height: 150px;
  }
  
  .system-title {
    font-size: 50px;
  }
  
  .form-wrapper {
    transform: translateX(calc(0px - (1400px - 100vw) * 0.2)) translateY(70px);
  }
  
  .additional-image-container {
    width: calc(600px - (1400px - 100vw) * 0.15);
  }
  
  .login-form {
    width: calc(510px - (1400px - 100vw) * 0.1);
  }
}

/* 小屏幕响应式调整（768px - 1024px） */
@media (max-width: 1024px) {
  .container {
    padding-top: 10vh;
  }
  
  .logo-container {
    left: 40px;
    top: 15px;
  }
  
  .logo {
    width: 120px;
    height: 120px;
  }
  
  .system-title {
    font-size: 40px;
    margin-left: 15px;
  }
  
  .form-wrapper {
    flex-direction: column;
    align-items: center;
    transform: translateX(0) translateY(40px);
  }
  
  .additional-image-container {
    width: 450px;
    height: 280px;
    margin-right: 0;
    margin-bottom: -7px;
    border-radius: 10px 10px 0 0;
    border-right: 1px solid #e0e0e0;
    border-bottom: none;
  }
  
  .login-form {
    width: 450px;
    height: auto;
    min-height: 480px;
    padding: 0 30px;
  }
  
  .title {
    font-size: 40px;
    margin-bottom: 35px;
    margin-top: 25px;
  }
  
  .form-item {
    margin-top: 20px;
  }

  .form-item :deep(.el-form-item__content) {
    display: flex;
    justify-content: center;
  }
  
  .button {
    width: 300px;
    height: 48px;
    font-size: 17px;
  }
  
  .custom-input {
    width: 300px;
  }
  
  .el-form-item :deep(.el-input) {
    width: 300px;
  }
  
  .university-title-container {
    right: 20px;
    bottom: 20px;
  }
  
  .university-title {
    font-size: 20px;
  }
  
  .university-title-english {
    font-size: 11px;
  }
}

/* 平板/移动设备响应式调整（<768px） */
@media (max-width: 768px) {
  .container {
    padding-top: 5vh;
  }
  
  .logo-container {
    left: 20px;
    top: 10px;
  }
  
  .logo {
    width: 100px;
    height: 100px;
  }
  
  .system-title {
    font-size: 30px;
    margin-left: 10px;
  }
  
  .form-wrapper {
    transform: translateX(0) translateY(20px);
  }
  
  .additional-image-container {
    width: 90vw;
    max-width: 400px;
    height: 250px;
  }
  
  .login-form {
    width: 90vw;
    max-width: 400px;
    height: auto;
    min-height: 450px;
    padding: 0 20px;
  }
  
  .title {
    font-size: 35px;
    margin-bottom: 30px;
    margin-top: 20px;
  }
  
  .form-item {
    margin-top: 18px;
  }

  .form-item :deep(.el-form-item__content) {
    display: flex;
    justify-content: center;
  }
  
  .button {
    width: 80%;
    height: 46px;
    font-size: 16px;
  }
  
  .custom-input {
    width: 80%;
  }
  
  .el-form-item :deep(.el-input) {
    width: 80%;
  }
  
  .university-title {
    font-size: 18px;
  }
  
  .university-title-english {
    font-size: 10px;
  }
}
</style>