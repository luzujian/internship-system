<template>
  <div class="not-found-container">
    <div class="not-found-content">
      <h1>404</h1>
      <p>页面不存在</p>
      <p>抱歉，您访问的页面不存在或者已被删除。</p>
      <el-button type="primary" @click="goToHome">返回首页</el-button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ElButton } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../store/auth'

const router = useRouter()
const authStore = useAuthStore()

// 返回首页函数
const goToHome = () => {
  const isAuthenticated = authStore.isAuthenticated
  const userRole = authStore.user?.role

  // 根据用户是否认证和角色决定返回哪个首页
  if (isAuthenticated) {
    switch (userRole) {
      case 'ADMIN':
        router.push('/admin/dashboard')
        break
      case 'TEACHER':
        router.push('/teacher/dashboard')
        break
      case 'STUDENT':
        router.push('/student/dashboard')
        break
      default:
        router.push('/login')
    }
  } else {
    router.push('/login')
  }
}
</script>

<style scoped>
.not-found-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-color: #f5f5f5;
}

.not-found-content {
  text-align: center;
  background-color: white;
  padding: 60px 40px;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.not-found-content h1 {
  font-size: 80px;
  color: #409eff;
  margin-bottom: 20px;
  font-weight: 500;
}

.not-found-content p {
  color: #606266;
  margin-bottom: 30px;
}

.not-found-content button {
  margin-top: 20px;
}
</style>