<template>
  <div class="internships-container">
    <!-- 顶部标题区域 -->
    <div class="page-header">
      <h2>实习记录</h2>
      <p>记录和管理您的实习经历</p>
    </div>

    <!-- 标签页区域 -->
    <div class="tabs-section">
      <div class="tabs-container">
        <div
          v-for="tab in tabs"
          :key="tab.key"
          :class="['tab-item', { active: activeTab === tab.key }]"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </div>
      </div>
    </div>

    <!-- 内容区域 -->
    <div class="content-section">
      <!-- 实习确认表 -->
      <div v-if="activeTab === 'confirmation'" class="confirmation-form">
        <div class="form-card">
          <div class="form-header">
            <h3>实习确认表</h3>
            <p>请填写完整的实习信息，提交后将发送至企业端进行确认</p>
          </div>
          
          <div class="form-content">
            <!-- 个人基本信息 -->
            <div class="form-section">
              <div class="section-header">
                <el-icon class="section-icon"><User /></el-icon>
                <h4>个人基本信息</h4>
              </div>
              <div class="info-table">
                <div class="table-row">
                  <div class="table-cell label">姓名</div>
                  <div class="table-cell value">
                    <el-input v-model="confirmationForm.name" placeholder="请输入姓名" clearable />
                  </div>
                  <div class="table-cell label">学号</div>
                  <div class="table-cell value">
                    <el-input v-model="confirmationForm.studentId" placeholder="请输入学号" clearable />
                  </div>
                </div>
                <div class="table-row">
                  <div class="table-cell label">专业班级</div>
                  <div class="table-cell value">
                    <el-input v-model="confirmationForm.majorClass" placeholder="请输入专业班级" clearable />
                  </div>
                  <div class="table-cell label">联系电话</div>
                  <div class="table-cell value">
                    <el-input v-model="confirmationForm.phone" placeholder="请输入联系电话" clearable />
                  </div>
                </div>
              </div>
            </div>
            
            <!-- 实习单位信息 -->
            <div class="form-section">
              <div class="section-header">
                <el-icon class="section-icon"><OfficeBuilding /></el-icon>
                <h4>实习单位信息</h4>
              </div>
              <div class="info-table">
                <div class="table-row">
                  <div class="table-cell label">单位名称</div>
                  <div class="table-cell value">
                    <el-input v-model="confirmationForm.company" placeholder="请输入实习单位名称" clearable />
                  </div>
                  <div class="table-cell label">单位地址</div>
                  <div class="table-cell value">
                    <el-input v-model="confirmationForm.companyAddress" placeholder="请输入实习单位地址" clearable />
                  </div>
                </div>
                <div class="table-row">
                  <div class="table-cell label">单位联系人</div>
                  <div class="table-cell value">
                    <el-input v-model="confirmationForm.companyContact" placeholder="请输入单位联系人" clearable />
                  </div>
                  <div class="table-cell label">联系电话</div>
                  <div class="table-cell value">
                    <el-input v-model="confirmationForm.companyPhone" placeholder="请输入单位联系电话" clearable />
                  </div>
                </div>
                <div class="table-row">
                  <div class="table-cell label">单位邮箱</div>
                  <div class="table-cell value" colspan="3">
                    <el-input v-model="confirmationForm.companyEmail" placeholder="请输入单位邮箱" clearable />
                  </div>
                </div>
              </div>
            </div>
            
            <!-- 岗位信息 -->
            <div class="form-section">
              <div class="section-header">
                <el-icon class="section-icon"><Briefcase /></el-icon>
                <h4>岗位信息</h4>
              </div>
              <div class="info-table">
                <div class="table-row">
                  <div class="table-cell label">实习岗位</div>
                  <div class="table-cell value">
                    <el-input v-model="confirmationForm.position" placeholder="请输入实习岗位" clearable />
                  </div>
                  <div class="table-cell label">岗位职责</div>
                  <div class="table-cell value">
                    <el-input v-model="confirmationForm.responsibilities" type="textarea" placeholder="请输入岗位职责" />
                  </div>
                </div>
              </div>
            </div>
            
            <!-- 实习时间 -->
            <div class="form-section">
              <div class="section-header">
                <el-icon class="section-icon"><Calendar /></el-icon>
                <h4>实习时间</h4>
              </div>
              <div class="info-table">
                <div class="table-row">
                  <div class="table-cell label">开始时间</div>
                  <div class="table-cell value">
                    <el-date-picker v-model="confirmationForm.startDate" type="date" placeholder="选择开始时间" style="width: 100%" />
                  </div>
                  <div class="table-cell label">结束时间</div>
                  <div class="table-cell value">
                    <el-date-picker v-model="confirmationForm.endDate" type="date" placeholder="选择结束时间" style="width: 100%" />
                  </div>
                </div>
                <div class="table-row">
                  <div class="table-cell label">实习时长</div>
                  <div class="table-cell value">
                    <el-input v-model="confirmationForm.duration" placeholder="请输入实习时长（如：3个月）" clearable />
                  </div>
                  <div class="table-cell label">实习补贴</div>
                  <div class="table-cell value">
                    <el-input v-model="confirmationForm.subsidy" placeholder="请输入实习补贴金额" clearable />
                  </div>
                </div>
              </div>
            </div>
            
            <!-- 安全承诺 -->
            <div class="form-section">
              <div class="section-header">
                <el-icon class="section-icon"><Warning /></el-icon>
                <h4>安全承诺</h4>
              </div>
              <div class="commitment-section">
                <div class="commitment-item">
                  <el-checkbox v-model="confirmationForm.commitments.safety">我承诺遵守国家法律法规和实习单位的规章制度，注意个人安全</el-checkbox>
                </div>
                <div class="commitment-item">
                  <el-checkbox v-model="confirmationForm.commitments.responsibility">我承诺认真履行实习岗位职责，完成实习任务</el-checkbox>
                </div>
                <div class="commitment-item">
                  <el-checkbox v-model="confirmationForm.commitments.report">我承诺定期向学校报告实习情况，遇到问题及时沟通</el-checkbox>
                </div>
              </div>
            </div>
            
            <!-- 附件上传 -->
            <div class="form-section">
              <div class="section-header">
                <el-icon class="section-icon"><UploadFilled /></el-icon>
                <h4>附件上传</h4>
              </div>
              <div class="upload-section">
                <el-upload
                  class="upload-demo"
                  action="#"
                  :auto-upload="false"
                  :on-change="handleFileChange"
                  :file-list="files"
                  accept=".pdf,.doc,.docx"
                >
                  <el-button type="primary" class="upload-button">
                    <el-icon><UploadFilled /></el-icon>
                    上传实习协议
                  </el-button>
                  <template #tip>
                    <div class="upload-tip">
                      请上传PDF或Word格式的实习协议文件
                    </div>
                  </template>
                </el-upload>
              </div>
            </div>
            
            <!-- 提交按钮 -->
            <div class="form-actions">
              <el-button @click="resetForm" class="reset-button">重置</el-button>
              <el-button type="primary" @click="submitForm" class="submit-button">
                <el-icon><Check /></el-icon>
                提交确认
              </el-button>
            </div>
          </div>
        </div>
        
        <!-- 确认历史 -->
        <div class="history-card">
          <div class="history-header">
            <h3>确认历史</h3>
          </div>
          <div class="history-list">
            <div v-for="record in confirmationHistory" :key="record.id" class="history-item">
              <div class="history-info">
                <div class="history-date">{{ record.date }}</div>
                <div class="history-company">{{ record.company }}</div>
                <div class="history-status" :class="record.statusClass">{{ record.status }}</div>
              </div>
              <button class="view-button" @click="viewHistory(record)">
                <el-icon><View /></el-icon>
                查看详情
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 进行中日志 -->
      <div v-else-if="activeTab === 'current'" class="logs-list">
        <div class="log-card">
          <div class="log-left">
            <div class="time-icon-wrapper">
              <el-icon class="time-icon"><Document /></el-icon>
            </div>
          </div>
          <div class="log-right">
            <div class="log-header">
              <div class="log-info">
                <div class="log-title">第1周工作总结</div>
                <div class="log-date">2024-01-01</div>
              </div>
              <div class="status-tag submitted">
                已提交
              </div>
            </div>
            <div class="log-content">
              <div class="log-desc">完成了公司入职手续，熟悉了工作环境和团队成员</div>
            </div>
            <div class="log-actions">
              <button class="action-button view">
                <el-icon><View /></el-icon>
                查看
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 已完成档案 -->
      <div v-else-if="activeTab === 'completed'" class="archives-list">
        <div class="archive-card">
          <div class="archive-left">
            <div class="time-icon-wrapper">
              <el-icon class="time-icon"><FolderOpened /></el-icon>
            </div>
          </div>
          <div class="archive-right">
            <div class="archive-header">
              <div class="archive-info">
                <div class="archive-company">互联网公司</div>
                <div class="archive-position">Java开发实习生</div>
              </div>
              <div class="certificate-status available">
                <el-icon class="certificate-icon"><DocumentChecked /></el-icon>
                证明已生成
              </div>
            </div>
            <div class="archive-info-list">
              <div class="info-item">
                <el-icon class="info-icon"><Calendar /></el-icon>
                <span>2023-06-01 - 2023-12-31</span>
              </div>
              <div class="info-item">
                <el-icon class="info-icon"><Clock /></el-icon>
                <span>7个月</span>
              </div>
            </div>
            <div class="archive-actions">
              <button class="action-button view">
                <el-icon><View /></el-icon>
                查看档案
              </button>
              <button class="action-button download">
                <el-icon><Download /></el-icon>
                下载证明
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { ElMessage } from "element-plus";
import {
  Clock,
  Edit,
  Delete,
  View,
  ChatDotRound,
  Calendar,
  DocumentChecked,
  Document,
  FolderOpened,
  Plus,
  Download,
  UploadFilled,
  OfficeBuilding,
  Warning,
  Check,
  User,
  Briefcase
} from '@element-plus/icons-vue'

const activeTab = ref('confirmation')

const tabs = [
  { key: "confirmation", label: "实习确认" },
  { key: "current", label: "进行中" },
  { key: "completed", label: "已完成" },
];

// 实习确认表数据
const confirmationForm = ref({
  name: "",
  studentId: "",
  majorClass: "",
  phone: "",
  company: "",
  companyAddress: "",
  companyContact: "",
  companyPhone: "",
  companyEmail: "",
  position: "",
  responsibilities: "",
  startDate: null,
  endDate: null,
  duration: "",
  subsidy: "",
  commitments: {
    safety: false,
    responsibility: false,
    report: false
  }
});

// 上传文件
const files = ref([]);

// 确认历史
const confirmationHistory = ref([]);

// 文件上传处理
const handleFileChange = (file, fileList) => {
  files.value = fileList;
};

// 重置表单
const resetForm = () => {
  confirmationForm.value = {
    name: "",
    studentId: "",
    majorClass: "",
    phone: "",
    company: "",
    companyAddress: "",
    companyContact: "",
    companyPhone: "",
    companyEmail: "",
    position: "",
    responsibilities: "",
    startDate: null,
    endDate: null,
    duration: "",
    subsidy: "",
    commitments: {
      safety: false,
      responsibility: false,
      report: false
    }
  };
  files.value = [];
};

// 提交表单
const submitForm = () => {
  // 验证表单
  if (!confirmationForm.value.name || !confirmationForm.value.studentId || !confirmationForm.value.company) {
    ElMessage.warning('请填写必要的实习信息');
    return;
  }
  
  // 验证安全承诺
  if (!confirmationForm.value.commitments.safety || !confirmationForm.value.commitments.responsibility || !confirmationForm.value.commitments.report) {
    ElMessage.warning('请阅读并确认所有安全承诺');
    return;
  }
  
  // 模拟提交
  ElMessage.success('实习确认表提交成功，已发送至企业端进行确认');
  
  // 添加到历史记录
  const newRecord = {
    id: Date.now(),
    date: new Date().toISOString().split('T')[0],
    company: confirmationForm.value.company,
    status: "待确认",
    statusClass: "pending"
  };
  confirmationHistory.value.unshift(newRecord);
  
  // 重置表单
  resetForm();
};

// 查看历史记录详情
const viewHistory = (record) => {
  ElMessage.info(`查看确认记录: ${record.company} (${record.date})`);
};
</script>

<style scoped>
/* 统一容器样式 */
.internships-container {
  width: 100%;
  min-height: 100vh;
  background: #f1f5f9;
  padding: 24px;
  overflow-y: auto;
}

/* 页面标题区域 */
.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 8px 0;
}

.page-header p {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}

/* 标签页区域 */
.tabs-section {
  background: white;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.tabs-container {
  display: flex;
  gap: 8px;
  width: fit-content;
}

.tab-item {
  padding: 8px 20px;
  border-radius: 8px;
  font-size: 14px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.3s ease;
  background: #f8fafc;
  border: 2px solid transparent;
}

.tab-item:hover {
  background: #f0f9ff;
  color: #409EFF;
}

.tab-item.active {
  background: #409EFF;
  color: white;
  border-color: #409EFF;
}

/* 内容区域 */
.content-section {
  background: white;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

/* 实习确认表样式 */
.confirmation-form {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.form-card {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.form-header {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e8e8e8;
}

.form-header h3 {
  font-size: 20px;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 8px 0;
}

.form-header p {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}

.form-section {
  margin-bottom: 24px;
}

.form-section .section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border-radius: 8px;
}

.form-section .section-header h4 {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.form-section .section-icon {
  font-size: 18px;
  color: #409EFF;
}

.info-table {
  background: #f8fafc;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.table-row {
  display: grid;
  grid-template-columns: 1fr 2fr 1fr 2fr;
  gap: 0;
  border-bottom: 1px solid #e2e8f0;
}

.table-row:last-child {
  border-bottom: none;
}

.table-cell {
  padding: 16px;
  display: flex;
  align-items: center;
}

.table-cell.label {
  background: #f1f5f9;
  font-weight: 500;
  color: #334155;
  font-size: 14px;
  border-right: 1px solid #e2e8f0;
}

.table-cell.value {
  background: white;
  font-size: 14px;
}

.table-cell.value[colspan="3"] {
  grid-column: span 3;
}

.table-cell .el-input {
  width: 100%;
}

/* 安全承诺样式 */
.commitment-section {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.commitment-item {
  margin-bottom: 12px;
}

.commitment-item:last-child {
  margin-bottom: 0;
}

/* 上传区域样式 */
.upload-section {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.upload-button {
  margin-bottom: 16px;
}

.upload-tip {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 8px;
}

/* 表单按钮样式 */
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #e8e8e8;
}

.reset-button {
  background: #f8fafc;
  color: #64748b;
  border: 1px solid #e2e8f0;
}

.submit-button {
  background: #67C23A;
  border: none;
}

.submit-button:hover {
  background: #5daf34;
}

/* 历史记录样式 */
.history-card {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.history-header {
  margin-bottom: 16px;
}

.history-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
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
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
}

.history-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.history-info {
  flex: 1;
}

.history-date {
  font-size: 13px;
  color: #94a3b8;
  margin-bottom: 4px;
}

.history-company {
  font-size: 15px;
  font-weight: 500;
  color: #1e293b;
  margin-bottom: 4px;
}

.history-status {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
}

.history-status.confirmed {
  background: #f0fdf4;
  color: #22c55e;
  border: 1px solid #dcfce7;
}

.history-status.pending {
  background: #fffbeb;
  color: #f59e0b;
  border: 1px solid #fef3c7;
}

.view-button {
  background: #409EFF;
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.3s ease;
}

.view-button:hover {
  background: #337ecc;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

/* 日志卡片样式 */
.logs-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.log-card {
  background: white;
  border-radius: 12px;
  padding: 20px 24px;
  display: flex;
  gap: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
}

.log-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.log-left {
  flex-shrink: 0;
}

.time-icon-wrapper {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.time-icon {
  font-size: 28px;
  color: white;
}

.log-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.log-info {
  flex: 1;
}

.log-title {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 4px;
}

.log-date {
  font-size: 14px;
  color: #64748b;
}

.status-tag {
  flex-shrink: 0;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

.status-tag.submitted {
  background: #f0f9ff;
  color: #409EFF;
  border: 1px solid #dbeafe;
}

.log-content {
  margin-bottom: 8px;
}

.log-desc {
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}

.log-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.action-button {
  padding: 8px 20px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  border: none;
  display: flex;
  align-items: center;
  gap: 4px;
}

.action-button.view {
  background: #67C23A;
  color: white;
}

.action-button.view:hover {
  background: #5daf34;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(103, 194, 58, 0.3);
}

.action-button.download {
  background: #e6a23c;
  color: white;
}

.action-button.download:hover {
  background: #d68c2e;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(230, 162, 60, 0.3);
}

/* 档案卡片样式 */
.archives-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.archive-card {
  background: white;
  border-radius: 12px;
  padding: 20px 24px;
  display: flex;
  gap: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
}

.archive-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.archive-left {
  flex-shrink: 0;
}

.archive-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.archive-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.archive-info {
  flex: 1;
}

.archive-company {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 4px;
}

.archive-position {
  font-size: 14px;
  color: #64748b;
}

.certificate-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

.certificate-status.available {
  background: #f0fdf4;
  color: #22c55e;
  border: 1px solid #86efac;
}

.certificate-icon {
  font-size: 14px;
}

.archive-info-list {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #64748b;
}

.info-icon {
  font-size: 16px;
  color: #94a3b8;
}

.archive-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

/* 响应式调整 */
@media screen and (max-width: 1024px) {
  .table-row {
    grid-template-columns: 1fr 3fr;
  }
  
  .table-row .table-cell:nth-child(3),
  .table-row .table-cell:nth-child(4) {
    grid-column: 1 / -1;
  }
  
  .table-row .table-cell:nth-child(3) {
    border-right: none;
    border-top: 1px solid #e2e8f0;
  }
  
  .table-row .table-cell:nth-child(4) {
    border-top: 1px solid #e2e8f0;
  }
}

@media screen and (max-width: 768px) {
  .internships-container {
    padding: 16px;
  }
  
  .form-card {
    padding: 16px;
  }
  
  .table-cell {
    padding: 12px;
  }
  
  .form-actions {
    flex-direction: column;
  }
  
  .form-actions button {
    width: 100%;
  }
  
  .history-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  
  .view-button {
    align-self: flex-end;
  }
  
  .log-card,
  .archive-card {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .time-icon-wrapper {
    align-self: flex-start;
  }
}
</style>