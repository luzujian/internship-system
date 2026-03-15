<template>
  <div class="register-container">
    <div class="register-form-wrapper">
      <h2>注册</h2>
      <el-form label-width="100px" @submit.native.prevent="handleRegister">
        <el-form-item label="用户类型" prop="role">
          <el-select v-model="registerForm.role" placeholder="请选择用户类型" @change="onRoleChange">
            <el-option label="学生" value="ROLE_STUDENT" />
            <el-option label="教师" value="ROLE_TEACHER" />
            <el-option label="管理员" value="ROLE_ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item :label="getUsernameLabel" prop="username">
          <el-input v-model="registerForm.username" :placeholder="getUsernamePlaceholder" clearable />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="registerForm.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="registerForm.confirmPassword" type="password" placeholder="请再次输入密码" show-password />
        </el-form-item>
        <el-form-item :label="getNameOrPhoneLabel" prop="name">
          <el-input v-model="registerForm.name" :placeholder="getNameOrPhonePlaceholder" clearable />
        </el-form-item>
        
        <!-- 仅学生角色显示专业、年级和班级选择 -->
        <template v-if="registerForm.role === 'ROLE_STUDENT'">
          <el-form-item label="专业" prop="majorId">
            <el-select v-model="registerForm.majorId" placeholder="请选择专业" @change="onMajorChange">
              <el-option v-for="major in majorList" :key="major.id" :label="major.name" :value="major.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="年级" prop="grade">
            <el-input v-model="registerForm.grade" placeholder="请输入年级，如2024" maxlength="4" show-word-limit clearable />
          </el-form-item>
          <el-form-item label="班级" prop="classId">
              <el-select v-model="registerForm.classId" placeholder="请选择班级">
                <el-option v-for="classItem in classList" :key="classItem.id" :label="classItem.name" :value="classItem.id" />
              </el-select>
            </el-form-item>
        </template>
        
        <!-- 仅教师角色显示专业和班级选择 -->
        <template v-if="registerForm.role === 'ROLE_TEACHER'">
          <el-form-item label="院系" prop="departmentId">
            <el-select v-model="registerForm.departmentId" placeholder="请选择院系">
              <el-option v-for="department in departmentList" :key="department.id" :label="department.name" :value="department.id" />
            </el-select>
          </el-form-item>
        </template>

        <el-form-item>
          <el-button type="primary" @click="handleRegister" :loading="loading" style="width: 100%">注册</el-button>
        </el-form-item>
        <el-form-item>
          <el-link @click="$router.push('/login')">已有账号？返回登录</el-link>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../store/auth'
import { useRouter } from 'vue-router'
import MajorService from '../api/major'
import ClassService from '../api/class'
import DepartmentService from '../api/department'

const registerForm = ref({
  username: '',
  password: '',
  confirmPassword: '',
  name: '',
  role: 'ROLE_STUDENT',
  majorId: '',
  grade: '',
  classId: ''
})

const majorList = ref([])
const classList = ref([])
const departmentList = ref([])
const loading = ref(false)
const authStore = useAuthStore()
const router = useRouter()

// 根据选择的角色动态生成用户名标签
const getUsernameLabel = computed(() => {
  switch (registerForm.value.role) {
    case 'ROLE_STUDENT':
      return '学号'
    case 'ROLE_TEACHER':
      return '工号'
    default:
      return '用户名'
  }
})

// 根据选择的角色动态生成用户名占位符
const getUsernamePlaceholder = computed(() => {
  switch (registerForm.value.role) {
    case 'ROLE_STUDENT':
      return '请输入学号'
    case 'ROLE_TEACHER':
      return '请输入工号'
    default:
      return '请输入用户名'
  }
})

// 根据选择的角色动态生成姓名/手机号标签
const getNameOrPhoneLabel = computed(() => {
  switch (registerForm.value.role) {
    case 'ROLE_ADMIN':
      return '手机号'
    default:
      return '姓名'
  }
})

// 根据选择的角色动态生成姓名/手机号占位符
const getNameOrPhonePlaceholder = computed(() => {
  switch (registerForm.value.role) {
    case 'ROLE_ADMIN':
      return '请输入11位手机号码'
    default:
      return '请输入姓名'
  }
})

// 获取专业列表
const getMajorList = async () => {
  try {
    const response = await MajorService.getMajors()
    if (response && response.code === 200 && response.data) {
      majorList.value = response.data
    } else if (response && Array.isArray(response)) {
      majorList.value = response
    }
  } catch (error) {
    console.error('获取专业列表失败:', error)
    ElMessage.error('获取专业列表失败')
  }
}

// 根据专业ID获取班级列表
const getClassListByMajorId = async (majorId) => {
  if (!majorId) {
    classList.value = []
    return
  }
  
  try {
    const response = await ClassService.getClassesByMajorId(majorId)
    if (response && response.code === 200 && response.data) {
      classList.value = response.data
    } else if (response && Array.isArray(response)) {
      classList.value = response
    }
  } catch (error) {
    console.error('获取班级列表失败:', error)
    ElMessage.error('获取班级列表失败')
  }
}

// 获取院系列表
const getDepartmentList = async () => {
  try {
    const response = await DepartmentService.getDepartments()
    if (response && response.code === 200 && response.data) {
      departmentList.value = response.data
    } else if (response && Array.isArray(response)) {
      departmentList.value = response
    }
  } catch (error) {
    console.error('获取院系列表失败:', error)
    ElMessage.error('获取院系列表失败')
  }
}

// 角色改变时的处理
const onRoleChange = () => {
  // 重置专业、年级、班级和院系选择
  registerForm.value.majorId = ''
  registerForm.value.grade = ''
  registerForm.value.classId = ''
  registerForm.value.departmentId = ''
  
  // 如果选择学生角色，加载专业列表
  if (registerForm.value.role === 'ROLE_STUDENT') {
    getMajorList()
  }
  // 如果选择教师角色，加载院系列表
  if (registerForm.value.role === 'ROLE_TEACHER') {
    getDepartmentList()
  }
}

// 专业改变时的处理
const onMajorChange = (majorId) => {
  // 重置班级选择
  registerForm.value.classId = ''
  // 加载对应专业的班级列表
  getClassListByMajorId(majorId)
}

const handleRegister = async () => {
  try {
    // 验证两次输入的密码是否一致
    if (registerForm.value.password !== registerForm.value.confirmPassword) {
      ElMessage.error('两次输入的密码不一致')
      return
    }
    
    // 学生角色需要验证专业、年级和班级选择
    if (registerForm.value.role === 'ROLE_STUDENT') {
      if (!registerForm.value.majorId) {
        ElMessage.error('请选择专业')
        return
      }
      if (!registerForm.value.grade) {
        ElMessage.error('请输入年级')
        return
      }
      if (!/^\d{4}$/.test(registerForm.value.grade)) {
        ElMessage.error('年级格式不正确，应为4位数字')
        return
      }
      if (!registerForm.value.classId) {
        ElMessage.error('请选择班级')
        return
      }
    }

    const success = await authStore.register(
      registerForm.value.username,
      registerForm.value.password,
      registerForm.value.name,
      registerForm.value.role,
      registerForm.value.majorId,
      registerForm.value.classId,
      registerForm.value.departmentId,
      registerForm.value.grade
    )
    
    if (success) {
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } else {
      ElMessage.error('注册失败，请稍后再试')
    }
  } catch (error) {
    ElMessage.error('注册失败，请稍后再试')
    console.error('注册失败:', error)
  } finally {
    loading.value = false
  }
}

// 页面加载时，如果默认是学生角色，加载专业列表
onMounted(() => {
  if (registerForm.value.role === 'ROLE_STUDENT') {
    getMajorList()
  }
})
</script>

<style scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-image: url('../assets/register.png');
  background-repeat: no-repeat;
  background-size: cover;
  background-position: center;
  background-attachment: fixed;
  position: relative;
}

.register-form-wrapper {
  background-color: white;
  padding: 40px;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  width: 500px;
  z-index: 1;
}

.register-form-wrapper h2 {
  text-align: center;
  margin-bottom: 30px;
  color: #303133;
}
</style>