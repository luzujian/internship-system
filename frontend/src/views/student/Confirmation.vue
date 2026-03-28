<script setup>
import { ref, computed } from 'vue'
import {
  Check,
  Upload,
  Calendar,
  Phone,
  Message,
  Location,
  User,
  OfficeBuilding,
  Briefcase,
  Clock,
  Money,
  DocumentChecked
} from '@element-plus/icons-vue'
import { ElMessage, ElUpload, ElDatePicker, ElCheckbox, ElCheckboxGroup } from 'element-plus'

// 实习确认表数据
const confirmationForm = ref({
  name: '',
  studentId: '',
  majorClass: '',
  phone: '',
  company: '',
  companyAddress: '',
  companyContact: '',
  companyPhone: '',
  companyEmail: '',
  position: '',
  responsibilities: '',
  startDate: null,
  endDate: null,
  duration: '',
  subsidy: '',
  commitments: {
    safety: false,
    responsibility: false,
    report: false
  }
})

// 上传文件
const files = ref([])

// 确认历史记录
const confirmationHistory = ref([])

// 计算实习时长
const calculateDuration = () => {
  if (confirmationForm.value.startDate && confirmationForm.value.endDate) {
    const start = new Date(confirmationForm.value.startDate)
    const end = new Date(confirmationForm.value.endDate)
    const diffTime = Math.abs(end - start)
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24))
    confirmationForm.value.duration = `${Math.round(diffDays / 30)}个月（${diffDays}天）`
  }
}

// 监听日期变化计算时长
const handleDateChange = () => {
  calculateDuration()
}

// 上传文件处理
const handleFileUpload = (file) => {
  files.value.push(file)
  ElMessage.success('实习协议上传成功')
}

// 移除文件
const handleFileRemove = (file) => {
  const index = files.value.findIndex(item => item.uid === file.uid)
  if (index !== -1) {
    files.value.splice(index, 1)
  }
}

// 重置表单
const resetForm = () => {
  confirmationForm.value = {
    name: '',
    studentId: '',
    majorClass: '',
    phone: '',
    company: '',
    companyAddress: '',
    companyContact: '',
    companyPhone: '',
    companyEmail: '',
    position: '',
    responsibilities: '',
    startDate: null,
    endDate: null,
    duration: '',
    subsidy: '',
    commitments: {
      safety: false,
      responsibility: false,
      report: false
    }
  }
  files.value = []
}

// 提交表单
const submitForm = () => {
  // 验证表单
  if (!confirmationForm.value.name || !confirmationForm.value.studentId || !confirmationForm.value.company) {
    ElMessage.warning('请填写必要的实习信息')
    return
  }
  
  // 验证安全承诺
  if (!confirmationForm.value.commitments.safety || !confirmationForm.value.commitments.responsibility || !confirmationForm.value.commitments.report) {
    ElMessage.warning('请阅读并确认所有安全承诺')
    return
  }
  
  // 模拟提交
  ElMessage.success('实习确认表提交成功，已发送至企业端进行确认')
  
  // 添加到历史记录
  const newRecord = {
    id: Date.now(),
    date: new Date().toISOString().split('T')[0],
    company: confirmationForm.value.company,
    status: "待确认",
    statusClass: "pending"
  }
  confirmationHistory.value.unshift(newRecord)
  
  // 重置表单
  resetForm()
}

// 查看历史记录详情
const viewHistory = (record) => {
  ElMessage.info(`查看确认记录: ${record.company} (${record.date})`)
}
</script>

<template>
  <div class="confirmation-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>实习确认表</h2>
      <p>确认实习相关信息</p>
    </div>

    <!-- 主要内容 -->
    <div class="content-container">
      <!-- 实习确认表表单 -->
      <div class="form-card">
        <h2 class="card-title">
          <Check class="card-icon" />
          实习信息确认
        </h2>
        
        <div class="form-grid">
          <!-- 个人信息 -->
          <div class="form-section">
            <h3 class="section-title">
              <User class="section-icon" />
              个人基本信息
            </h3>
            <div class="form-row">
              <div class="form-item">
                <label class="form-label">姓名 <span class="required">*</span></label>
                <input type="text" v-model="confirmationForm.name" class="form-input" placeholder="请输入姓名">
              </div>
              <div class="form-item">
                <label class="form-label">学号 <span class="required">*</span></label>
                <input type="text" v-model="confirmationForm.studentId" class="form-input" placeholder="请输入学号">
              </div>
            </div>
            <div class="form-row">
              <div class="form-item">
                <label class="form-label">专业班级</label>
                <input type="text" v-model="confirmationForm.majorClass" class="form-input" placeholder="请输入专业班级">
              </div>
              <div class="form-item">
                <label class="form-label">联系电话</label>
                <input type="tel" v-model="confirmationForm.phone" class="form-input" placeholder="请输入联系电话">
              </div>
            </div>
          </div>

          <!-- 实习单位信息 -->
          <div class="form-section">
            <h3 class="section-title">
              <OfficeBuilding class="section-icon" />
              实习单位信息
            </h3>
            <div class="form-row">
              <div class="form-item">
                <label class="form-label">单位名称 <span class="required">*</span></label>
                <input type="text" v-model="confirmationForm.company" class="form-input" placeholder="请输入实习单位名称">
              </div>
            </div>
            <div class="form-row">
              <div class="form-item">
                <label class="form-label">单位地址</label>
                <input type="text" v-model="confirmationForm.companyAddress" class="form-input" placeholder="请输入实习单位地址">
              </div>
            </div>
            <div class="form-row">
              <div class="form-item">
                <label class="form-label">单位联系人</label>
                <input type="text" v-model="confirmationForm.companyContact" class="form-input" placeholder="请输入单位联系人">
              </div>
              <div class="form-item">
                <label class="form-label">联系电话</label>
                <input type="tel" v-model="confirmationForm.companyPhone" class="form-input" placeholder="请输入单位联系电话">
              </div>
            </div>
            <div class="form-row">
              <div class="form-item">
                <label class="form-label">单位邮箱</label>
                <input type="email" v-model="confirmationForm.companyEmail" class="form-input" placeholder="请输入单位邮箱">
              </div>
            </div>
          </div>

          <!-- 岗位信息 -->
          <div class="form-section">
            <h3 class="section-title">
              <Briefcase class="section-icon" />
              实习岗位信息
            </h3>
            <div class="form-row">
              <div class="form-item">
                <label class="form-label">实习岗位</label>
                <input type="text" v-model="confirmationForm.position" class="form-input" placeholder="请输入实习岗位">
              </div>
            </div>
            <div class="form-item">
              <label class="form-label">岗位职责</label>
              <textarea v-model="confirmationForm.responsibilities" class="form-textarea" placeholder="请简要描述实习岗位职责"></textarea>
            </div>
          </div>

          <!-- 实习时间 -->
          <div class="form-section">
            <h3 class="section-title">
              <Clock class="section-icon" />
              实习时间安排
            </h3>
            <div class="form-row">
              <div class="form-item">
                <label class="form-label">开始日期</label>
                <el-date-picker
                  v-model="confirmationForm.startDate"
                  type="date"
                  placeholder="选择开始日期"
                  class="form-date-picker"
                  @change="handleDateChange"
                />
              </div>
              <div class="form-item">
                <label class="form-label">结束日期</label>
                <el-date-picker
                  v-model="confirmationForm.endDate"
                  type="date"
                  placeholder="选择结束日期"
                  class="form-date-picker"
                  @change="handleDateChange"
                />
              </div>
            </div>
            <div class="form-row">
              <div class="form-item">
                <label class="form-label">实习时长</label>
                <input type="text" v-model="confirmationForm.duration" class="form-input" placeholder="自动计算" readonly>
              </div>
              <div class="form-item">
                <label class="form-label">实习补贴</label>
                <input type="text" v-model="confirmationForm.subsidy" class="form-input" placeholder="请输入实习补贴（元/月）">
              </div>
            </div>
          </div>

          <!-- 安全承诺 -->
          <div class="form-section">
            <h3 class="section-title">
              <Check class="section-icon" />
              安全承诺
            </h3>
            <div class="commitments">
              <div class="commitment-item">
                <el-checkbox v-model="confirmationForm.commitments.safety">
                  本人承诺严格遵守国家法律法规和企业规章制度，注意实习期间的人身安全
                </el-checkbox>
              </div>
              <div class="commitment-item">
                <el-checkbox v-model="confirmationForm.commitments.responsibility">
                  本人承诺认真履行实习岗位职责，按时完成工作任务
                </el-checkbox>
              </div>
              <div class="commitment-item">
                <el-checkbox v-model="confirmationForm.commitments.report">
                  本人承诺定期向学校汇报实习情况，按时提交实习日志
                </el-checkbox>
              </div>
            </div>
          </div>

          <!-- 附件上传 -->
          <div class="form-section">
            <h3 class="section-title">
              <Upload class="section-icon" />
              附件上传
            </h3>
            <div class="upload-section">
              <el-upload
                class="upload-demo"
                action="#"
                :auto-upload="false"
                :on-change="handleFileUpload"
                :on-remove="handleFileRemove"
                :file-list="files"
                accept=".pdf,.doc,.docx"
                :limit="2"
              >
                <el-button type="primary" plain class="upload-button">
                  <Upload class="upload-icon" />
                  点击上传实习协议
                </el-button>
                <template #tip>
                  <div class="upload-tip">
                    支持上传 PDF、Word 格式文件，最多上传 2 个文件
                  </div>
                </template>
              </el-upload>
            </div>
          </div>
        </div>

        <!-- 提交按钮 -->
        <div class="form-actions">
          <button type="button" class="reset-button" @click="resetForm">
            重置
          </button>
          <button type="button" class="submit-button" @click="submitForm">
            提交确认
          </button>
        </div>
      </div>

      <!-- 确认历史记录 -->
      <div class="history-card">
        <h2 class="card-title">
          <Calendar class="card-icon" />
          确认历史记录
        </h2>
        
        <div v-if="confirmationHistory.length > 0" class="history-list">
          <div 
            v-for="record in confirmationHistory" 
            :key="record.id" 
            class="history-item"
            @click="viewHistory(record)"
          >
            <div class="history-info">
              <div class="history-company">{{ record.company }}</div>
              <div class="history-date">{{ record.date }}</div>
            </div>
            <div :class="['history-status', record.statusClass]">
              {{ record.status }}
            </div>
          </div>
        </div>
        
        <div v-else class="empty-history">
          <p>暂无确认记录</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.confirmation-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.page-header {
  margin-bottom: 24px;
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  border-radius: 12px;
  padding: 24px 32px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  position: relative;
  overflow: hidden;
}

.page-header::before {
  content: "";
  position: absolute;
  top: -50%;
  right: -10%;
  width: 300px;
  height: 300px;
  background: radial-gradient(
    circle,
    rgba(255, 255, 255, 0.1) 0%,
    transparent 70%
  );
  border-radius: 50%;
}

.page-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: white;
  margin: 0 0 8px 0;
  position: relative;
  z-index: 1;
}

.page-header p {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
  margin: 0;
  position: relative;
  z-index: 1;
}

.content-container {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1fr;
  gap: 30px;
}

.form-card, .history-card {
  background: white;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  padding: 30px;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.form-card:hover, .history-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.15);
}

.card-title {
  font-size: 20px;
  font-weight: bold;
  margin-bottom: 24px;
  color: #333;
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-icon {
  color: #67C23A;
  font-size: 20px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 24px;
}

.form-section {
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 24px;
}

.form-section:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #409EFF;
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-icon {
  font-size: 16px;
}

.form-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 14px;
  font-weight: 500;
  color: #666;
}

.required {
  color: #f56c6c;
}

.form-input, .form-textarea, .form-date-picker {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  font-size: 14px;
  transition: border-color 0.3s ease, box-shadow 0.3s ease;
}

.form-input:focus, .form-textarea:focus, .form-date-picker:focus {
  outline: none;
  border-color: #409EFF;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.form-textarea {
  resize: vertical;
  min-height: 100px;
}

.form-date-picker {
  height: 40px;
}

.commitments {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.commitment-item {
  padding: 12px;
  background: #f9f9f9;
  border-radius: 8px;
  border-left: 4px solid #409EFF;
}

.upload-section {
  margin-top: 12px;
}

.upload-button {
  width: 100%;
  max-width: 300px;
}

.upload-tip {
  font-size: 12px;
  color: #999;
  margin-top: 8px;
}

.form-actions {
  display: flex;
  gap: 16px;
  margin-top: 32px;
  justify-content: flex-end;
}

.reset-button, .submit-button {
  padding: 12px 24px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.reset-button {
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  color: #606266;
}

.reset-button:hover {
  background: #e4e7ed;
  color: #303133;
}

.submit-button {
  background: #67C23A;
  border: 1px solid #67C23A;
  color: white;
}

.submit-button:hover {
  background: #85ce61;
  border-color: #85ce61;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: #f9f9f9;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.history-item:hover {
  background: #f0f7ff;
  transform: translateX(4px);
}

.history-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.history-company {
  font-weight: 500;
  color: #333;
}

.history-date {
  font-size: 12px;
  color: #999;
}

.history-status {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.history-status.success {
  background: #f0f9eb;
  color: #67C23A;
}

.history-status.pending {
  background: #ecf5ff;
  color: #409EFF;
}

.history-status.error {
  background: #fef0f0;
  color: #f56c6c;
}

.empty-history {
  text-align: center;
  padding: 40px 20px;
  color: #999;
  background: #f9f9f9;
  border-radius: 8px;
}

/* 响应式设计 */
@media screen and (max-width: 1024px) {
  .content-container {
    padding: 0 16px;
  }
  
  .form-card, .history-card {
    padding: 24px;
  }
  
  .form-row {
    grid-template-columns: 1fr;
  }
}

@media screen and (max-width: 768px) {
  .confirmation-page {
    padding: 16px;
  }
  
  .page-header {
    margin-bottom: 24px;
  }
  
  .page-title {
    font-size: 24px;
  }
  
  .form-card, .history-card {
    padding: 20px;
  }
  
  .card-title {
    font-size: 18px;
  }
  
  .form-actions {
    flex-direction: column;
  }
  
  .reset-button, .submit-button {
    width: 100%;
  }
}

@media screen and (max-width: 480px) {
  .page-title {
    font-size: 20px;
  }
  
  .card-title {
    font-size: 16px;
  }
  
  .form-section {
    padding-bottom: 16px;
  }
}
</style>