<template>
  <div class="job-application-form-container">
    <div class="page-header">
      <h2>实习申请</h2>
    </div>

    <el-card class="form-card" shadow="never">
      <el-button type="primary" @click="showApplicationDialog = true" class="apply-button">
        <el-icon class="el-icon--left"><Edit /></el-icon>
        填写实习申请
      </el-button>
    </el-card>

    <!-- 实习申请对话框 -->
    <el-dialog
      v-model="showApplicationDialog"
      width="1000px"
      class="internship-application-dialog"
      append-to-body
      lock-scroll
      modal-class="global-modal"
      :title="''"
    >
      <div class="dialog-content">
        <div class="header-section">
          <h1 class="header-title">实习申请</h1>
        </div>

        <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
          <div class="form-content">
            <div class="form-module">
              <div class="module-header">
                <div class="module-icon-container">
                  <el-icon class="module-icon"><User /></el-icon>
                </div>
                <h2 class="module-title">基本信息</h2>
              </div>
              <div class="form-grid">
                <el-form-item label="学生姓名" prop="studentName">
                  <el-input v-model="form.studentName" placeholder="请输入姓名" clearable />
                </el-form-item>
                <el-form-item label="学号" prop="studentId">
                  <el-input v-model="form.studentId" placeholder="请输入学号" clearable />
                </el-form-item>
                <el-form-item label="性别" prop="gender">
                  <el-select v-model="form.gender" placeholder="请选择性别" clearable>
                    <el-option label="男" value="male" />
                    <el-option label="女" value="female" />
                  </el-select>
                </el-form-item>
                <el-form-item label="年级" prop="grade">
                  <el-select v-model="form.grade" placeholder="请选择年级" clearable>
                    <el-option label="一年级" value="grade1" />
                    <el-option label="二年级" value="grade2" />
                    <el-option label="三年级" value="grade3" />
                    <el-option label="四年级" value="grade4" />
                  </el-select>
                </el-form-item>
                <el-form-item label="学校" prop="school">
                  <el-input v-model="form.school" placeholder="请输入学校名称" clearable />
                </el-form-item>
                <el-form-item label="学历" prop="education">
                  <el-select v-model="form.education" placeholder="请选择学历" clearable>
                    <el-option label="本科" value="bachelor" />
                    <el-option label="硕士" value="master" />
                    <el-option label="博士" value="doctor" />
                  </el-select>
                </el-form-item>
                <el-form-item label="专业" prop="major">
                  <el-input v-model="form.major" placeholder="请输入专业名称" clearable />
                </el-form-item>
                <el-form-item label="专业班级" prop="majorClass">
                  <el-select v-model="form.majorClass" placeholder="请选择专业班级" clearable>
                    <el-option label="计算机科学与技术1班" value="cs1" />
                    <el-option label="计算机科学与技术2班" value="cs2" />
                    <el-option label="软件工程1班" value="se1" />
                    <el-option label="软件工程2班" value="se2" />
                  </el-select>
                </el-form-item>
                <el-form-item label="联系电话" prop="contactPhone">
                  <el-input v-model="form.contactPhone" placeholder="请输入联系电话" clearable />
                </el-form-item>
                <el-form-item label="电子邮箱" prop="email">
                  <el-input v-model="form.email" placeholder="请输入电子邮箱" type="email" clearable />
                </el-form-item>
              </div>
            </div>

            <div class="form-module">
              <div class="module-header">
                <div class="module-icon-container">
                  <el-icon class="module-icon"><Briefcase /></el-icon>
                </div>
                <h2 class="module-title">申请信息</h2>
              </div>
              <div class="form-grid">
                <el-form-item label="应聘岗位" prop="jobPosition">
                  <el-input v-model="form.jobPosition" placeholder="请输入应聘岗位名称" clearable />
                </el-form-item>
                <el-form-item label="应聘单位" prop="companyName">
                  <el-input v-model="form.companyName" placeholder="请输入应聘单位名称" clearable />
                </el-form-item>
                <el-form-item label="申请日期" prop="applicationDate">
                  <el-date-picker v-model="form.applicationDate" type="date" placeholder="请选择申请日期" style="width: 100%" clearable />
                </el-form-item>
                <el-form-item label="申请状态" prop="applicationStatus">
                  <el-select v-model="form.applicationStatus" placeholder="请选择申请状态" clearable>
                    <el-option label="待审核" value="pending" />
                    <el-option label="已通过" value="approved" />
                    <el-option label="已拒绝" value="rejected" />
                  </el-select>
                </el-form-item>
              </div>
            </div>

            <div class="form-module">
              <div class="module-header">
                <div class="module-icon-container">
                  <el-icon class="module-icon"><Star /></el-icon>
                </div>
                <h2 class="module-title">专业技能</h2>
              </div>
              <div class="form-single">
                <div class="skills-container">
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
              </div>
            </div>

            <div class="form-module">
              <div class="module-header">
                <div class="module-icon-container">
                  <el-icon class="module-icon"><Document /></el-icon>
                </div>
                <h2 class="module-title">项目经验与自我介绍</h2>
              </div>
              <div class="form-single">
                <el-form-item label="项目经验" prop="projectExperience">
                  <el-input
                    v-model="form.projectExperience"
                    type="textarea"
                    :rows="4"
                    placeholder="请简要介绍你的项目经验，包括项目名称、担任角色、主要工作内容等"
                    resize="vertical"
                  />
                </el-form-item>
                <el-form-item label="自我分析" prop="selfAnalysis">
                  <el-input
                    v-model="form.selfAnalysis"
                    type="textarea"
                    :rows="4"
                    placeholder="请简要介绍你的个人特点、学习能力和职业规划等"
                    resize="vertical"
                  />
                </el-form-item>
              </div>
            </div>

            <div class="form-module">
              <div class="module-header">
                <div class="module-icon-container">
                  <el-icon class="module-icon"><Upload /></el-icon>
                </div>
                <h2 class="module-title">附件上传</h2>
              </div>
              <div class="form-single">
                <el-form-item label="个人简历" prop="resume">
                  <el-upload
                    class="upload-demo"
                    action="#"
                    :auto-upload="false"
                    :on-change="handleFileChange"
                    :show-file-list="false"
                  >
                    <el-button type="primary" class="upload-button">
                      <el-icon class="el-icon--left"><Upload /></el-icon>
                      选择文件
                    </el-button>
                  </el-upload>
                </el-form-item>
                <el-form-item label="证书图片" prop="certificate">
                  <el-upload
                    class="upload-demo"
                    action="#"
                    :auto-upload="false"
                    :on-change="handleFileChange"
                    :show-file-list="false"
                  >
                    <el-button type="primary" class="upload-button">
                      <el-icon class="el-icon--left"><Upload /></el-icon>
                      选择文件
                    </el-button>
                  </el-upload>
                </el-form-item>
              </div>
            </div>
          </div>

          <div class="form-actions">
            <el-button @click="showApplicationDialog = false" class="reset-btn">取消</el-button>
            <el-button type="primary" @click="submitForm" :loading="submitting" class="submit-btn">
              提交申请
            </el-button>
          </div>
        </el-form>
      </div>
    </el-dialog>

    <!-- 申请历史 -->
    <div v-if="applicationHistory.length > 0" class="history-section">
      <div class="section-header">
        <div class="section-icon-wrapper">
          <el-icon class="section-icon"><Clock /></el-icon>
        </div>
        <h3>申请历史</h3>
      </div>
      <el-card class="history-card" shadow="never">
        <el-table :data="applicationHistory" style="width: 100%">
          <el-table-column prop="jobPosition" label="应聘岗位" min-width="150" />
          <el-table-column prop="companyName" label="应聘单位" min-width="150" />
          <el-table-column prop="applicationDate" label="申请日期" min-width="120" />
          <el-table-column prop="applicationStatus" label="申请状态" min-width="100">
            <template #default="scope">
              <el-tag 
                :type="scope.row.applicationStatus === 'approved' ? 'success' : scope.row.applicationStatus === 'rejected' ? 'danger' : 'warning'"
              >
                {{ scope.row.applicationStatus === 'approved' ? '已通过' : scope.row.applicationStatus === 'rejected' ? '已拒绝' : '待审核' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="100">
            <template #default="scope">
              <el-button type="primary" size="small" @click="viewDetail(scope.row)">
                查看详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
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
          <h4>基本信息</h4>
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">姓名：</span>
              <span class="detail-value">{{ currentDetail.studentName }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">学号：</span>
              <span class="detail-value">{{ currentDetail.studentId }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">性别：</span>
              <span class="detail-value">{{ currentDetail.gender === 'male' ? '男' : '女' }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">年级：</span>
              <span class="detail-value">{{ currentDetail.grade }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">学校：</span>
              <span class="detail-value">{{ currentDetail.school }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">学历：</span>
              <span class="detail-value">{{ currentDetail.education }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">专业：</span>
              <span class="detail-value">{{ currentDetail.major }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">联系电话：</span>
              <span class="detail-value">{{ currentDetail.contactPhone }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">电子邮箱：</span>
              <span class="detail-value">{{ currentDetail.email }}</span>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h4>申请信息</h4>
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">应聘岗位：</span>
              <span class="detail-value">{{ currentDetail.jobPosition }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">应聘单位：</span>
              <span class="detail-value">{{ currentDetail.companyName }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">申请日期：</span>
              <span class="detail-value">{{ currentDetail.applicationDate }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">申请状态：</span>
              <span class="detail-value">
                <el-tag 
                  :type="currentDetail.applicationStatus === 'approved' ? 'success' : currentDetail.applicationStatus === 'rejected' ? 'danger' : 'warning'"
                >
                  {{ currentDetail.applicationStatus === 'approved' ? '已通过' : currentDetail.applicationStatus === 'rejected' ? '已拒绝' : '待审核' }}
                </el-tag>
              </span>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h4>专业技能</h4>
          <div class="detail-skills">
            <el-tag v-for="skill in currentDetail.skills" :key="skill" size="small" class="skill-tag">
              {{ skill }}
            </el-tag>
          </div>
        </div>

        <div class="detail-section">
          <h4>项目经验</h4>
          <p class="detail-text">{{ currentDetail.projectExperience }}</p>
        </div>

        <div class="detail-section">
          <h4>自我分析</h4>
          <p class="detail-text">{{ currentDetail.selfAnalysis }}</p>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import {
  User,
  Briefcase,
  Star,
  Document,
  Upload,
  Clock,
  Edit
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const showApplicationDialog = ref(false)
const submitting = ref(false)

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
  skills: [],
  projectExperience: '',
  selfAnalysis: '',
  resume: '',
  certificate: '',
  otherFiles: ''
})

const rules = {
  studentName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  studentId: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'blur' }],
  grade: [{ required: true, message: '请选择年级', trigger: 'blur' }],
  school: [{ required: true, message: '请输入学校名称', trigger: 'blur' }],
  education: [{ required: true, message: '请选择学历', trigger: 'blur' }],
  major: [{ required: true, message: '请输入专业名称', trigger: 'blur' }],
  majorClass: [{ required: true, message: '请选择专业班级', trigger: 'blur' }],
  contactPhone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }],
  email: [{ required: true, message: '请输入电子邮箱', trigger: 'blur' }, { type: 'email', message: '请输入正确的电子邮箱格式', trigger: 'blur' }],
  jobPosition: [{ required: true, message: '请输入应聘岗位名称', trigger: 'blur' }],
  companyName: [{ required: true, message: '请输入应聘单位名称', trigger: 'blur' }],
  applicationDate: [{ required: true, message: '请选择申请日期', trigger: 'blur' }],
  applicationStatus: [{ required: true, message: '请选择申请状态', trigger: 'blur' }]
}

const formRef = ref(null)

const skills = [
  { label: 'Python', value: 'python' },
  { label: 'Java', value: 'java' },
  { label: 'C++', value: 'c++' },
  { label: 'JavaScript', value: 'javascript' },
  { label: 'TypeScript', value: 'typescript' },
  { label: 'React', value: 'react' },
  { label: 'Vue', value: 'vue' },
  { label: 'Angular', value: 'angular' },
  { label: 'Node.js', value: 'nodejs' },
  { label: 'Spring Boot', value: 'springboot' },
  { label: 'MySQL', value: 'mysql' },
  { label: 'MongoDB', value: 'mongodb' },
  { label: 'Redis', value: 'redis' },
  { label: 'Docker', value: 'docker' },
  { label: '机器学习', value: 'machinelearning' },
  { label: '深度学习', value: 'deeplearning' },
  { label: '数据分析', value: 'dataanalysis' },
  { label: '人工智能', value: 'ai' },
  { label: 'Git', value: 'git' },
  { label: 'Linux', value: 'linux' },
  { label: 'Nginx', value: 'nginx' },
  { label: 'Webpack', value: 'webpack' },
  { label: 'Vite', value: 'vite' }
]

const selectedSkills = ref([])

const toggleSkill = (skillValue) => {
  const index = selectedSkills.value.indexOf(skillValue)
  if (index > -1) {
    selectedSkills.value.splice(index, 1)
  } else {
    selectedSkills.value.push(skillValue)
  }
  form.skills = selectedSkills.value
}

const handleFileChange = (file) => {
  console.log('文件选择:', file.name)
  ElMessage.success(`文件 ${file.name} 选择成功`)
}

const submitForm = async () => {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
    
    submitting.value = true
    
    await new Promise(resolve => setTimeout(resolve, 1000))
    
    ElMessage.success('申请提交成功！')
    
    showApplicationDialog.value = false
    resetForm()
  } catch (error) {
    console.log('表单验证失败:', error)
  } finally {
    submitting.value = false
  }
}

const resetForm = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  selectedSkills.value = []
  form.skills = []
}

const applicationHistory = ref([])

const showDetailDialog = ref(false)
const currentDetail = ref(null)

const viewDetail = (row) => {
  currentDetail.value = row
  showDetailDialog.value = true
}
</script>

<style scoped>
.job-application-form-container {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 24px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.form-card {
  margin-bottom: 20px;
}

.apply-button {
  background: linear-gradient(90deg, #1e88e5 0%, #4caf50 100%);
  color: #ffffff;
  border: none;
  padding: 12px 24px;
  font-size: 16px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.apply-button:hover {
  background: linear-gradient(90deg, #1565c0 0%, #388e3c 100%);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(30, 136, 229, 0.3);
}

.history-section {
  margin-top: 20px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.section-icon-wrapper {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.section-icon {
  font-size: 20px;
  color: #ffffff;
}

.section-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.history-card {
  border-radius: 8px;
}

.detail-dialog .detail-content {
  padding: 20px;
}

.detail-section {
  margin-bottom: 24px;
}

.detail-section h4 {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e2e8f0;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.detail-item {
  display: flex;
  align-items: center;
}

.detail-label {
  font-weight: 500;
  color: #64748b;
  margin-right: 8px;
}

.detail-value {
  color: #1e293b;
}

.detail-skills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-skills .skill-tag {
  background: linear-gradient(90deg, #1e88e5 0%, #4caf50 100%);
  color: #ffffff;
  border: none;
}

.detail-text {
  color: #64748b;
  line-height: 1.6;
  margin: 0;
}

.internship-application-dialog :deep(.el-dialog) {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.internship-application-dialog :deep(.el-dialog__header) {
  padding: 0;
  border-bottom: none;
}

.internship-application-dialog :deep(.el-dialog__title) {
  display: none;
}

.internship-application-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
  font-size: 20px;
  top: 15px;
  right: 20px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  width: 30px;
  height: 30px;
  transition: all 0.3s ease;
}

.internship-application-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  background: rgba(255, 255, 255, 0.3);
}

.internship-application-dialog :deep(.el-dialog__body) {
  padding: 0;
  background-color: #ffffff;
}

.internship-application-dialog :deep(.el-dialog__footer) {
  display: none;
}

.header-section {
  background: linear-gradient(90deg, #1e88e5 0%, #4caf50 100%);
  padding: 20px;
  margin-bottom: 0;
  border-radius: 0;
  box-shadow: none;
}

.header-title {
  color: white;
  font-size: 24px;
  font-weight: 600;
  text-align: center;
  margin: 0;
}

.form-content {
  padding: 20px;
  max-height: 60vh;
  overflow-y: auto;
}

.form-module {
  background-color: transparent;
  border: none;
  border-radius: 0;
  padding: 0;
  margin-bottom: 24px;
  box-shadow: none;
}

.module-header {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.module-icon-container {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
}

.module-icon {
  color: white;
  font-size: 14px;
}

.module-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.form-single {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-actions {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 30px;
  padding: 20px;
  background-color: #ffffff;
  border: none;
  border-radius: 0;
  max-width: 100%;
  margin: 0;
  border-top: 1px solid #f0f0f0;
}

.submit-btn {
  background: linear-gradient(90deg, #1e88e5 0%, #4caf50 100%) !important;
  color: #ffffff !important;
  border: none !important;
  border-radius: 4px !important;
  padding: 8px 20px !important;
  transition: all 0.2s ease !important;
}

.submit-btn:hover {
  background: linear-gradient(90deg, #1565c0 0%, #388e3c 100%) !important;
}

.reset-btn {
  background: #ffffff !important;
  color: #64748b !important;
  border: 1px solid #e2e8f0 !important;
  border-radius: 4px !important;
  padding: 8px 20px !important;
}

.reset-btn:hover {
  border-color: #409eff !important;
  color: #409eff !important;
}

.internship-application-dialog :deep(.el-form-item) {
  margin-bottom: 16px;
}

.internship-application-dialog :deep(.el-form-item__label) {
  font-size: 14px;
  color: #334155;
  font-weight: 500;
}

.internship-application-dialog :deep(.el-input__wrapper),
.internship-application-dialog :deep(.el-select__wrapper),
.internship-application-dialog :deep(.el-textarea__inner) {
  border-radius: 4px;
  border: 1px solid #e2e8f0;
  padding: 6px 12px;
  background-color: #ffffff;
  box-shadow: none;
  transition: all 0.2s ease;
}

.internship-application-dialog :deep(.el-input__wrapper:focus-within),
.internship-application-dialog :deep(.el-select__wrapper:focus-within),
.internship-application-dialog :deep(.el-textarea__inner:focus) {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.internship-application-dialog :deep(.el-date-picker) {
  width: 100%;
}

.skills-container {
  padding: 16px;
  background-color: #ffffff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.skills-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.skill-tag {
  padding: 8px 16px;
  background-color: #f1f5f9;
  color: #64748b;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 14px;
}

.skill-tag:hover {
  background-color: #e2e8f0;
  border-color: #cbd5e1;
}

.skill-tag.active {
  background: linear-gradient(90deg, #1e88e5 0%, #4caf50 100%);
  color: #ffffff;
  border: none;
}

.upload-button {
  background: linear-gradient(90deg, #1e88e5 0%, #4caf50 100%);
  color: #ffffff;
  border: none;
  border-radius: 4px;
  padding: 8px 20px;
  transition: all 0.2s ease;
}

.upload-button:hover {
  background: linear-gradient(90deg, #1565c0 0%, #388e3c 100%);
}

@media screen and (max-width: 768px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
