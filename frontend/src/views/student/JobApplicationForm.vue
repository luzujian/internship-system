<template>
  <div class="job-application-form-container">
    <div class="page-header">
      <h2>实习申请</h2>
      <p>填写实习申请信息</p>
    </div>

    <el-card class="form-card" shadow="never">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <!-- 学生信息 -->
        <div class="form-section">
          <div class="section-header">
            <div class="section-icon-wrapper">
              <el-icon class="section-icon"><User /></el-icon>
            </div>
            <h3>学生基本信息</h3>
          </div>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="姓名" prop="studentName">
                <el-input v-model="form.studentName" placeholder="请输入姓名" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="学号" prop="studentId">
                <el-input v-model="form.studentId" placeholder="请输入学号" clearable />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="性别" prop="gender">
                <el-select v-model="form.gender" placeholder="请选择性别" clearable>
                  <el-option label="男" value="male" />
                  <el-option label="女" value="female" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="年级" prop="grade">
                <el-select v-model="form.grade" placeholder="请选择年级" clearable>
                  <el-option label="一年级" value="grade1" />
                  <el-option label="二年级" value="grade2" />
                  <el-option label="三年级" value="grade3" />
                  <el-option label="四年级" value="grade4" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="学校" prop="school">
                <el-input v-model="form.school" placeholder="请输入学校名称" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="学历" prop="education">
                <el-select v-model="form.education" placeholder="请选择学历" clearable>
                  <el-option label="本科" value="bachelor" />
                  <el-option label="硕士" value="master" />
                  <el-option label="博士" value="doctor" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="专业" prop="major">
                <el-input v-model="form.major" placeholder="请输入专业名称" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="专业班级" prop="majorClass">
                <el-select v-model="form.majorClass" placeholder="请选择专业班级" clearable>
                  <el-option label="计算机科学与技术1班" value="cs1" />
                  <el-option label="计算机科学与技术2班" value="cs2" />
                  <el-option label="软件工程1班" value="se1" />
                  <el-option label="软件工程2班" value="se2" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="联系电话" prop="contactPhone">
                <el-input v-model="form.contactPhone" placeholder="请输入联系电话" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="电子邮箱" prop="email">
                <el-input v-model="form.email" placeholder="请输入电子邮箱" type="email" clearable />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <!-- 申请信息 -->
        <div class="form-section">
          <div class="section-header">
            <div class="section-icon-wrapper">
              <el-icon class="section-icon"><Briefcase /></el-icon>
            </div>
            <h3>申请信息</h3>
          </div>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="应聘岗位" prop="jobPosition">
                <el-input v-model="form.jobPosition" placeholder="请输入应聘岗位名称" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="应聘单位" prop="companyName">
                <el-input v-model="form.companyName" placeholder="请输入应聘单位名称" clearable />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="申请日期" prop="applicationDate">
                <el-date-picker v-model="form.applicationDate" type="date" placeholder="请选择申请日期" style="width: 100%" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="申请状态" prop="applicationStatus">
                <el-select v-model="form.applicationStatus" placeholder="请选择申请状态" clearable>
                  <el-option label="待审核" value="pending" />
                  <el-option label="已通过" value="approved" />
                  <el-option label="已拒绝" value="rejected" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <!-- 专业技能 -->
        <div class="form-section">
          <div class="section-header">
            <div class="section-icon-wrapper">
              <el-icon class="section-icon"><Star /></el-icon>
            </div>
            <h3>专业技能</h3>
          </div>
          <el-row :gutter="20">
            <el-col :span="24">
              <el-form-item label="技能选择" prop="skills">
                <div class="skills-container">
                  <p class="skills-tip">请选择您掌握的专业技能（可多选）</p>
                  <div class="skills-tags">
                    <el-tag 
                      v-for="skill in skills" 
                      :key="skill.value"
                      :class="{ 'skill-tag': true, 'active': selectedSkills.includes(skill.value) }"
                      @click="toggleSkill(skill.value)"
                    >
                      {{ skill.label }}
                    </el-tag>
                  </div>
                </div>
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <!-- 项目经验与自我介绍 -->
        <div class="form-section">
          <div class="section-header">
            <div class="section-icon-wrapper">
              <el-icon class="section-icon"><Document /></el-icon>
            </div>
            <h3>项目经验与自我介绍</h3>
          </div>
          <el-row :gutter="20">
            <el-col :span="24">
              <el-form-item label="项目经验" prop="projectExperience">
                <el-input
                  v-model="form.projectExperience"
                  type="textarea"
                  :rows="4"
                  placeholder="请简要介绍你的项目经验，包括项目名称、担任角色、主要工作内容等"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="24">
              <el-form-item label="自我分析" prop="selfAnalysis">
                <el-input
                  v-model="form.selfAnalysis"
                  type="textarea"
                  :rows="4"
                  placeholder="请简要介绍你的个人特点、学习能力和职业规划等"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <!-- 附件上传 -->
        <div class="form-section">
          <div class="section-header">
            <div class="section-icon-wrapper">
              <el-icon class="section-icon"><Upload /></el-icon>
            </div>
            <h3>附件上传</h3>
          </div>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="个人简历" prop="resumeFile">
                <el-upload
                  class="upload-demo"
                  action="#"
                  :auto-upload="false"
                  :on-change="handleFileUpload('resumeFile')"
                  :show-file-list="false"
                  accept=".pdf,.doc,.docx"
                >
                  <el-button class="gradient-btn w-full upload-btn">选择文件</el-button>
                </el-upload>
                <div v-if="form.resumeFile" class="file-info">
                  <el-icon class="file-icon"><Document /></el-icon>
                  <span>{{ form.resumeFile }}</span>
                  <el-button link @click="form.resumeFile = ''">删除</el-button>
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="证件照片" prop="idPhoto">
                <el-upload
                  class="upload-demo"
                  action="#"
                  :auto-upload="false"
                  :on-change="handleFileUpload('idPhoto')"
                  :show-file-list="false"
                  accept=".jpg,.jpeg,.png"
                >
                  <el-button class="gradient-btn w-full upload-btn">选择文件</el-button>
                </el-upload>
                <div v-if="form.idPhoto" class="file-info">
                  <el-icon class="file-icon"><Picture /></el-icon>
                  <span>{{ form.idPhoto }}</span>
                  <el-button link @click="form.idPhoto = ''">删除</el-button>
                </div>
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <!-- 提交按钮 -->
        <div class="form-actions">
          <el-button type="primary" @click="submitForm" :loading="submitting" class="submit-btn">
            提交申请
          </el-button>
          <el-button @click="resetForm" class="reset-btn">重置</el-button>
          <el-button @click="cancelForm" class="cancel-btn">取消</el-button>
        </div>
      </el-form>
    </el-card>

    <!-- 申请历史 -->
    <div v-if="applicationHistory.length > 0" class="history-section">
      <div class="section-header">
        <div class="section-icon-wrapper">
          <el-icon class="section-icon"><Document /></el-icon>
        </div>
        <h3>申请历史</h3>
      </div>
      <div class="history-list">
        <div v-for="item in applicationHistory" :key="item.id" class="history-item">
          <div class="history-left">
            <div class="history-icon-wrapper">
              <el-icon class="history-icon"><Document /></el-icon>
            </div>
          </div>
          <div class="history-right">
            <div class="history-header">
              <div class="history-title">{{ item.jobPosition }} - {{ item.companyName }}</div>
              <el-tag :type="item.statusType" size="small" class="status-tag">{{ item.status }}</el-tag>
            </div>
            <div class="history-meta">
              <span class="history-date">
                <el-icon><Calendar /></el-icon>
                {{ item.submitDate }}
              </span>
            </div>
            <div class="history-actions">
              <el-button link type="primary" @click="viewApplicationDetail(item)" class="detail-btn">
                查看详情
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="showDetailDialog"
      :title="`申请详情 - ${currentDetail?.jobPosition}`"
      width="700px"
      class="detail-dialog"
    >
      <div v-if="currentDetail" class="detail-content">
        <div class="detail-section">
          <div class="detail-section-header">
            <div class="detail-section-icon">
              <el-icon><Briefcase /></el-icon>
            </div>
            <h4 class="detail-section-title">岗位信息</h4>
          </div>
          <div class="info-grid">
            <div class="info-item">
              <div class="info-label">申请岗位</div>
              <div class="info-value">{{ currentDetail.jobPosition }}</div>
            </div>
            <div class="info-item">
              <div class="info-label">应聘单位</div>
              <div class="info-value">{{ currentDetail.companyName }}</div>
            </div>
            <div class="info-item">
              <div class="info-label">提交日期</div>
              <div class="info-value">{{ currentDetail.submitDate }}</div>
            </div>
            <div class="info-item">
              <div class="info-label">申请状态</div>
              <div class="info-value">
                <el-tag :type="currentDetail.statusType" size="small">{{ currentDetail.status }}</el-tag>
              </div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="detail-section-header">
            <div class="detail-section-icon">
              <el-icon><User /></el-icon>
            </div>
            <h4 class="detail-section-title">个人信息</h4>
          </div>
          <div class="info-grid">
            <div class="info-item">
              <div class="info-label">姓名</div>
              <div class="info-value">{{ form.studentName }}</div>
            </div>
            <div class="info-item">
              <div class="info-label">学号</div>
              <div class="info-value">{{ form.studentId }}</div>
            </div>
            <div class="info-item">
              <div class="info-label">专业班级</div>
              <div class="info-value">{{ form.majorClass }}</div>
            </div>
            <div class="info-item">
              <div class="info-label">联系电话</div>
              <div class="info-value">{{ form.contactPhone }}</div>
            </div>
          </div>
        </div>

        <div v-if="form.projectExperience" class="detail-section">
          <div class="detail-section-header">
            <div class="detail-section-icon">
              <el-icon><Document /></el-icon>
            </div>
            <h4 class="detail-section-title">项目经验</h4>
          </div>
          <div class="reason-content">
            {{ form.projectExperience }}
          </div>
        </div>

        <div v-if="form.selfAnalysis" class="detail-section">
          <div class="detail-section-header">
            <div class="detail-section-icon">
              <el-icon><Document /></el-icon>
            </div>
            <h4 class="detail-section-title">自我分析</h4>
          </div>
          <div class="reason-content">
            {{ form.selfAnalysis }}
          </div>
        </div>

        <div v-if="form.resumeFile" class="detail-section">
          <div class="detail-section-header">
            <div class="detail-section-icon">
              <el-icon><Document /></el-icon>
            </div>
            <h4 class="detail-section-title">个人简历</h4>
          </div>
          <div class="file-info">
            <el-icon class="file-icon"><Document /></el-icon>
            <span>{{ form.resumeFile }}</span>
          </div>
        </div>

        <div v-if="form.idPhoto" class="detail-section">
          <div class="detail-section-header">
            <div class="detail-section-icon">
              <el-icon><Picture /></el-icon>
            </div>
            <h4 class="detail-section-title">证件照片</h4>
          </div>
          <div class="file-info">
            <el-icon class="file-icon"><Picture /></el-icon>
            <span>{{ form.idPhoto }}</span>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Briefcase, User, Star, Upload, Picture, Calendar } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()

// 从 auth store 获取学生 ID
const getStudentId = () => {
  return authStore.user?.id
}

const formRef = ref(null)
const submitting = ref(false)
const showDetailDialog = ref(false)
const currentDetail = ref(null)

const form = reactive({
  studentName: '',
  studentId: '',
  gender: '',
  grade: '',
  school: '',
  education: '',
  major: '',
  majorClass: '',
  contactPhone: '',
  email: '',
  jobPosition: '',
  companyName: '',
  applicationDate: '',
  applicationStatus: 'pending',
  skills: {
    python: false,
    java: false,
    cplus: false,
    javascript: false,
    typescript: false,
    react: false,
    vue: false,
    angular: false,
    nodejs: false,
    spring: false,
    mysql: false,
    mongodb: false,
    html: false,
    css: false,
    kubernetes: false,
    ml: false,
    dl: false,
    data: false,
    ai: false,
    git: false,
    linux: false,
    nginx: false,
    webpack: false,
    vite: false
  },
  projectExperience: '',
  selfAnalysis: '',
  resumeFile: '',
  idPhoto: ''
})

// 技能列表
const skills = [
  { label: 'Python', value: 'python' },
  { label: 'Java', value: 'java' },
  { label: 'C++', value: 'cplus' },
  { label: 'JavaScript', value: 'javascript' },
  { label: 'TypeScript', value: 'typescript' },
  { label: 'React', value: 'react' },
  { label: 'Vue', value: 'vue' },
  { label: 'Angular', value: 'angular' },
  { label: 'Node.js', value: 'nodejs' },
  { label: 'Spring Boot', value: 'spring' },
  { label: 'MySQL', value: 'mysql' },
  { label: 'MongoDB', value: 'mongodb' },
  { label: 'HTML', value: 'html' },
  { label: 'CSS', value: 'css' },
  { label: 'Kubernetes', value: 'kubernetes' },
  { label: '机器学习', value: 'ml' },
  { label: '深度学习', value: 'dl' },
  { label: '数据分析', value: 'data' },
  { label: '人工智能', value: 'ai' },
  { label: 'Git', value: 'git' },
  { label: 'Linux', value: 'linux' },
  { label: 'Nginx', value: 'nginx' },
  { label: 'Webpack', value: 'webpack' },
  { label: 'Vite', value: 'vite' }
]

// 选中的技能
const selectedSkills = ref([])

// 切换技能选中状态
const toggleSkill = (skillValue) => {
  const index = selectedSkills.value.indexOf(skillValue)
  if (index > -1) {
    selectedSkills.value.splice(index, 1)
    form.skills[skillValue] = false
  } else {
    selectedSkills.value.push(skillValue)
    form.skills[skillValue] = true
  }
}

const rules = {
  studentName: [
    { required: true, message: '请输入学生姓名', trigger: 'blur' }
  ],
  studentId: [
    { required: true, message: '请输入学号', trigger: 'blur' }
  ],
  gender: [
    { required: true, message: '请选择性别', trigger: 'change' }
  ],
  grade: [
    { required: true, message: '请选择年级', trigger: 'change' }
  ],
  school: [
    { required: true, message: '请输入学校名称', trigger: 'blur' }
  ],
  education: [
    { required: true, message: '请选择学历', trigger: 'change' }
  ],
  major: [
    { required: true, message: '请输入专业名称', trigger: 'blur' }
  ],
  majorClass: [
    { required: true, message: '请选择专业班级', trigger: 'change' }
  ],
  contactPhone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入电子邮箱', trigger: 'blur' }
  ],
  jobPosition: [
    { required: true, message: '请输入应聘岗位', trigger: 'blur' }
  ],
  companyName: [
    { required: true, message: '请输入应聘单位', trigger: 'blur' }
  ],
  applicationDate: [
    { required: true, message: '请选择申请日期', trigger: 'change' }
  ]
}

// 申请历史
const applicationHistory = ref([])

// 组件加载时从store获取用户基本信息
onMounted(() => {
  const user = authStore.user
  form.studentName = user?.name || ''
  form.studentId = user?.studentId || ''
  form.gender = user?.gender || ''
  form.grade = user?.grade || ''
  form.school = 'XX大学' // 学校信息可能需要从其他地方获取
  form.education = user?.education || ''
  form.major = user?.major || ''
  form.majorClass = user?.class || ''
  form.contactPhone = user?.phone || ''
  form.email = user?.email || ''
})

const handleFileUpload = (field) => {
  return (file) => {
    form[field] = file.name
    ElMessage.success('文件选择成功')
  }
}

const submitForm = () => {
  formRef.value.validate((valid) => {
    if (valid) {
      submitting.value = true
      setTimeout(() => {
        const newApplication = {
          id: Date.now(),
          jobPosition: form.jobPosition,
          companyName: form.companyName,
          submitDate: new Date().toLocaleDateString('zh-CN'),
          status: '已提交',
          statusType: 'info'
        }
        applicationHistory.value.unshift(newApplication)
        ElMessage.success('实习申请提交成功')
        resetForm()
        submitting.value = false
      }, 1000)
    }
  })
}

const resetForm = () => {
  formRef.value.resetFields()
  form.resumeFile = ''
  form.idPhoto = ''
  Object.keys(form.skills).forEach(key => {
    form.skills[key] = false
  })
  selectedSkills.value = []
  // 重置后重新从store获取用户基本信息
  const user = authStore.user
  form.studentName = user?.name || ''
  form.studentId = user?.studentId || ''
  form.gender = user?.gender || ''
  form.grade = user?.grade || ''
  form.school = 'XX大学' // 学校信息可能需要从其他地方获取
  form.education = user?.education || ''
  form.major = user?.major || ''
  form.majorClass = user?.class || ''
  form.contactPhone = user?.phone || ''
  form.email = user?.email || ''
}

const cancelForm = () => {
  if (confirm('确定要取消吗？已填写的内容将不会保存。')) {
    resetForm()
  }
}

const viewApplicationDetail = (item) => {
  currentDetail.value = item
  showDetailDialog.value = true
}
</script>

<style scoped>
.job-application-form-container {
  width: 100%;
  min-height: 100%;
  background: #f1f5f9;
  overflow-y: auto;
  overflow-x: hidden;
}

.page-header {
  margin-bottom: 24px;
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  border-radius: 12px;
  padding: 24px 32px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.page-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: white;
  margin: 0 0 8px 0;
}

.page-header p {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
  margin: 0;
}

.form-card {
  background: #ffffff;
  border-radius: 0;
  box-shadow: none;
  margin-bottom: 24px;
  padding: 20px;
}

.form-card :deep(.el-form) {
  width: 100%;
}

.form-card :deep(.el-row) {
  margin: 0 -10px;
}

.form-card :deep(.el-col) {
  padding: 0 10px;
}

.form-card :deep(.el-form-item) {
  margin-bottom: 20px;
}

.form-card :deep(.el-form-item__label) {
  font-weight: 500;
  color: #334155;
  font-size: 14px;
  text-align: left;
}

.form-card :deep(.el-input__wrapper),
.form-card :deep(.el-select__wrapper),
.form-card :deep(.el-textarea__inner) {
  border-radius: 4px !important;
  border: 1px solid #e2e8f0 !important;
  padding: 6px 12px !important;
  background-color: #ffffff !important;
  box-shadow: none !important;
  transition: all 0.2s ease !important;
}

.form-card :deep(.el-input__wrapper):hover,
.form-card :deep(.el-select__wrapper):hover,
.form-card :deep(.el-textarea__inner):hover {
  border-color: #3b82f6 !important;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1) !important;
}

.form-card :deep(.el-input__wrapper.is-focus),
.form-card :deep(.el-select__wrapper.is-focus),
.form-card :deep(.el-textarea__inner:focus) {
  border-color: #3b82f6 !important;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2) !important;
}

.form-card :deep(.el-date-editor) {
  width: 100%;
}

.form-card :deep(.el-date-editor .el-input__wrapper) {
  border-radius: 4px !important;
  border: 1px solid #e2e8f0 !important;
  padding: 6px 12px !important;
  background-color: #ffffff !important;
  box-shadow: none !important;
  transition: all 0.2s ease !important;
}

.form-card :deep(.el-date-editor .el-input__wrapper):hover {
  border-color: #3b82f6 !important;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1) !important;
}

.form-card :deep(.el-date-editor .el-input__wrapper.is-focus) {
  border-color: #3b82f6 !important;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2) !important;
}

.form-section {
  margin-bottom: 20px;
}

.section-header {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
}

.section-icon-wrapper {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background-color: #e3f2fd;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
}

.section-icon {
  color: #1976d2;
  font-size: 16px;
}

.section-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #333333;
  margin: 0;
}

/* 模块容器样式 */
.module-container {
  background: #ffffff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 8px !important;
  padding: 20px !important;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03) !important;
  margin-bottom: 24px !important;
}

/* 表单标签列样式 */
.form-label-col {
  background: #f8fafc !important;
  padding: 8px 12px !important;
  border-radius: 4px 0 0 4px !important;
  font-weight: 500 !important;
  color: #334155 !important;
  text-align: left !important;
}

.module-header {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 2px solid #f1f5f9;
}

.module-icon-container {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
}

.module-icon {
  color: white;
  font-size: 18px;
}

.module-title {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.form-grid :deep(.el-form-item) {
  margin-bottom: 20px;
}

.form-grid :deep(.el-form-item__label) {
  font-weight: 500;
  color: #334155;
  font-size: 14px;
  text-align: left;
}

.form-grid :deep(.el-input__wrapper),
.form-grid :deep(.el-select__wrapper),
.form-grid :deep(.el-textarea__inner) {
  border-radius: 4px !important;
  border: 1px solid #e2e8f0 !important;
  padding: 6px 12px !important;
  background-color: #ffffff !important;
  box-shadow: none !important;
  transition: all 0.2s ease !important;
}

.form-grid :deep(.el-input__wrapper):hover,
.form-grid :deep(.el-select__wrapper):hover,
.form-grid :deep(.el-textarea__inner):hover {
  border-color: #3b82f6 !important;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1) !important;
}

.form-grid :deep(.el-input__wrapper.is-focus),
.form-grid :deep(.el-select__wrapper.is-focus),
.form-grid :deep(.el-textarea__inner:focus) {
  border-color: #3b82f6 !important;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2) !important;
}

.form-grid :deep(.el-date-editor) {
  width: 100%;
}

.form-grid :deep(.el-date-editor .el-input__wrapper) {
  border-radius: 4px !important;
}

.form-single {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.skills-container {
  padding: 10px 0;
}

.skills-tip {
  font-size: 14px;
  color: #64748b;
  margin-bottom: 16px;
  padding: 0;
}

.skills-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.skill-tag {
  padding: 8px 16px !important;
  border-radius: 20px !important;
  background: #f1f5f9 !important;
  color: #64748b !important;
  margin: 0 8px 8px 0 !important;
  cursor: pointer !important;
  transition: all 0.2s ease !important;
  border: 1px solid #e2e8f0 !important;
}

.skill-tag.active {
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%) !important;
  color: #ffffff !important;
  border-color: transparent !important;
}

.skill-tag:hover:not(.active) {
  background: #e2e8f0 !important;
  border-color: #cbd5e1 !important;
}

.upload-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.gradient-btn {
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  border: none;
  color: white;
  font-size: 14px;
  padding: 10px 0;
  border-radius: 8px;
  width: 100%;
  transition: all 0.3s ease;
}

.gradient-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

.form-actions {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 30px;
  padding: 24px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
}

.submit-btn, .upload-btn {
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%) !important;
  color: #ffffff !important;
  border: none !important;
  border-radius: 8px !important;
  padding: 10px 24px !important;
  transition: all 0.3s ease !important;
}

.submit-btn:hover, .upload-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3) !important;
}

.reset-btn, .cancel-btn {
  background: #ffffff !important;
  color: #64748b !important;
  border: 1px solid #e2e8f0 !important;
  border-radius: 8px !important;
  padding: 10px 24px !important;
  transition: all 0.3s ease !important;
}

.reset-btn:hover, .cancel-btn:hover {
  background: #f8fafc !important;
  border-color: #cbd5e1 !important;
  color: #475569 !important;
}

.history-section {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 20px;
  margin-top: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.03);
}

.section-header {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 2px solid #f1f5f9;
}

.section-label {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 24px;
}

.history-item {
  background: white;
  border-radius: 12px;
  padding: 20px 24px;
  display: flex;
  gap: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
  border: 1px solid #f1f5f9;
}

.history-item:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  border-color: #bae6fd;
}

.history-left {
  flex-shrink: 0;
}

.history-icon-wrapper {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.history-icon {
  font-size: 28px;
  color: white;
}

.history-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.history-title {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 4px;
}

.status-tag {
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

.history-meta {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}

.history-date {
  font-size: 14px;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 6px;
}

.history-actions {
  display: flex;
  align-items: center;
}

.detail-btn {
  font-size: 13px;
  font-weight: 500;
  padding: 8px 20px;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.detail-btn:hover {
  background: #f0f9ff;
  color: #337ecc;
}

.detail-dialog :deep(.el-dialog) {
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  position: fixed !important;
  left: 50% !important;
  top: 50% !important;
  transform: translate(-50%, -50%) !important;
  margin: 0 !important;
  right: auto !important;
  z-index: 9999 !important;
}

.detail-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #67c23a 0%, #409eff 100%);
  color: white;
  padding: 28px 32px;
  margin: 0;
  border-radius: 0;
  position: relative;
}

.detail-dialog :deep(.el-dialog__header::before) {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: url('data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><circle cx="80" cy="20" r="40" fill="white" opacity="0.05"/><circle cx="20" cy="80" r="30" fill="white" opacity="0.05"/></svg>');
  background-size: cover;
  pointer-events: none;
}

.detail-dialog :deep(.el-dialog__title) {
  color: white;
  font-weight: 700;
  font-size: 20px;
  position: relative;
  z-index: 1;
  letter-spacing: 0.5px;
}

.detail-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
  font-size: 22px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 32px;
  padding: 32px;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-section-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 2px solid #f1f5f9;
}

.detail-section-icon {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.detail-section-icon .el-icon {
  color: white;
  font-size: 16px;
}

.detail-section-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.info-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.info-value {
  font-size: 14px;
  color: #1e293b;
  font-weight: 600;
  line-height: 1.5;
}

.reason-content {
  font-size: 14px;
  line-height: 1.8;
  color: #334155;
  padding: 16px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: #f8fafc;
  border-radius: 8px;
  font-size: 14px;
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
}

.file-info:hover {
  background: #f1f5f9;
  border-color: #cbd5e1;
}

.file-icon {
  color: #409EFF;
  font-size: 20px;
}

.file-info span {
  flex: 1;
  color: #1e293b;
  font-weight: 500;
}

/* 响应式设计 */
@media screen and (max-width: 1024px) {
  .page-header {
    padding: 20px 24px;
  }

  .page-header h2 {
    font-size: 24px;
  }
}

@media screen and (max-width: 768px) {
  .job-application-form-container {
    padding: 12px;
  }

  .page-header {
    padding: 16px 20px;
    margin-bottom: 20px;
  }

  .page-header h2 {
    font-size: 20px;
  }

  .form-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .module-header {
    margin-bottom: 16px;
  }

  .module-title {
    font-size: 16px;
  }

  .skills-tags {
    justify-content: flex-start;
  }

  .form-actions {
    flex-direction: column;
    align-items: center;
    gap: 12px;
    padding: 20px;
  }

  .form-actions .el-button {
    width: 100%;
    max-width: 280px;
  }

  .history-item {
    padding: 16px 20px;
  }

  .history-icon-wrapper {
    width: 48px;
    height: 48px;
  }

  .history-icon {
    font-size: 24px;
  }

  .history-title {
    font-size: 16px;
  }

  .detail-dialog :deep(.el-dialog__header) {
    padding: 20px 24px;
  }

  .detail-dialog :deep(.el-dialog__title) {
    font-size: 18px;
  }

  .detail-content {
    padding: 20px;
  }
}

@media screen and (max-width: 480px) {
  .job-application-form-container {
    padding: 12px;
  }

  .page-header {
    padding: 16px;
  }

  .page-header h2 {
    font-size: 18px;
  }

  .module-header {
    margin-bottom: 12px;
  }

  .module-title {
    font-size: 15px;
  }

  .module-icon-container {
    width: 32px;
    height: 32px;
  }

  .module-icon {
    font-size: 16px;
  }

  .form-actions {
    padding: 16px 12px;
  }

  .history-item {
    padding: 12px 16px;
  }

  .history-icon-wrapper {
    width: 44px;
    height: 44px;
  }

  .history-icon {
    font-size: 22px;
  }
}

/* 遮罩样式，确保覆盖左侧菜单栏 */
.detail-dialog :deep(.el-dialog__wrapper) {
  z-index: 9999 !important;
}

/* 遮罩层覆盖整个页面 */
:deep(.el-dialog__mask) {
  position: fixed !important;
  top: 0 !important;
  left: 0 !important;
  width: 100vw !important;
  height: 100vh !important;
  z-index: 9998 !important;
  background: rgba(0, 0, 0, 0.4) !important;
}

/* 弹窗阴影效果 */
.detail-dialog :deep(.el-dialog) {
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15) !important;
}
</style>
