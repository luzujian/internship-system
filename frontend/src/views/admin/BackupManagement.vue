<template>
  <div class="backup-management-container">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">数据备份与恢复</h1>
        <p class="page-description">管理系统数据备份和恢复</p>
      </div>
      <div class="header-illustration">
        <div class="illustration-circle circle-1"></div>
        <div class="illustration-circle circle-2"></div>
      </div>
    </div>

    <el-card class="main-card" shadow="never">
      <el-tabs v-model="activeTab" class="backup-tabs">
        <el-tab-pane label="备份管理" name="backup">
          <div class="backup-content">
            <div class="backup-header">
              <h3 class="backup-title">数据备份记录</h3>
              <p class="backup-description">查看和管理所有数据备份</p>
            </div>
            
            <div class="backup-actions">
              <el-button v-if="authStore.hasPermission('backup:create')" type="primary" @click="handleManualBackup" :loading="backingUp" class="backup-btn primary">
                <el-icon><Download /></el-icon>&nbsp;立即备份
              </el-button>
            </div>

            <div v-if="showProgress" class="progress-container">
              <div class="progress-header">
                <span class="progress-title">{{ progressTitle }}</span>
                <span class="progress-percentage">{{ progressPercentage }}%</span>
              </div>
              <el-progress 
                :percentage="progressPercentage" 
                :status="progressStatus"
                :stroke-width="20"
                :text-inside="true"
                class="custom-progress"
              />
              <p class="progress-tip">{{ progressTip }}</p>
            </div>

            <el-table 
              :data="backupRecords" 
              border 
              style="width: 100%" 
              v-loading="loading" 
              empty-text="暂无备份记录"
              class="backup-table"
            >
              <el-table-column prop="id" label="ID" width="80" align="center" />
              <el-table-column prop="backupName" label="备份名称" width="200" align="center" />
              <el-table-column prop="backupSize" label="文件大小" width="120" align="center">
                <template #default="{ row }">
                  <span class="file-size">{{ formatFileSize(row.backupSize) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="backupType" label="备份类型" width="120" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.backupType === 'MANUAL' ? 'primary' : 'success'" class="type-tag">
                    {{ row.backupType === 'MANUAL' ? '手动备份' : '自动备份' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="backupTime" label="备份时间" width="180" align="center">
                <template #default="{ row }">
                  <span class="time-text">{{ formatDateTime(row.backupTime) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="tableCount" label="表数量" width="100" align="center">
                <template #default="{ row }">
                  <span class="table-count">{{ row.tableCount || 0 }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="tableList" label="包含表" min-width="200" align="center">
                <template #default="{ row }">
                  <el-tooltip :content="row.tableList || '无'" placement="top">
                    <span class="table-list">{{ formatTableList(row.tableList) }}</span>
                  </el-tooltip>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 1 ? 'success' : 'danger'" class="status-tag">
                    {{ row.status === 1 ? '成功' : '失败' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="270" fixed="right" align="center">
                <template #default="{ row }">
                  <div class="action-buttons">
                    <el-tooltip v-if="authStore.hasPermission('backup:view')" content="下载备份文件" placement="top">
                      <el-button type="primary" size="small" @click="handleDownload(row)" class="table-btn download">
                        <el-icon><Download /></el-icon>
                      </el-button>
                    </el-tooltip>
                    <el-tooltip v-if="authStore.hasPermission('backup:restore')" content="恢复数据" placement="top">
                      <el-button type="warning" size="small" @click="handleRestore(row)" class="table-btn restore">
                        <el-icon><Refresh /></el-icon>
                      </el-button>
                    </el-tooltip>
                    <el-tooltip v-if="authStore.hasPermission('backup:delete')" content="删除备份" placement="top">
                      <el-button type="danger" size="small" @click="handleDelete(row)" class="table-btn delete">
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </el-tooltip>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="自动备份设置" name="schedule">
          <div class="schedule-content">
            <div class="schedule-header">
              <h3 class="schedule-title">自动备份配置</h3>
              <p class="schedule-description">配置系统自动备份策略</p>
            </div>
            
            <el-form :model="scheduleForm" label-width="150px" class="schedule-form">
              <el-form-item label="启用自动备份">
                <el-switch v-model="scheduleForm.enabled" class="schedule-switch" />
              </el-form-item>
              <el-form-item label="备份频率">
                <el-select v-model="scheduleForm.frequency" placeholder="请选择备份频率" class="schedule-select">
                  <el-option label="每天" value="daily" />
                  <el-option label="每周" value="weekly" />
                  <el-option label="每月" value="monthly" />
                </el-select>
              </el-form-item>
              <el-form-item label="备份时间">
                <el-time-picker
                  v-model="scheduleForm.time"
                  format="HH:mm"
                  placeholder="选择备份时间"
                  class="schedule-time"
                />
              </el-form-item>
              <el-form-item label="保留天数">
                <el-input-number v-model="scheduleForm.retentionDays" :min="1" :max="365" class="schedule-input" />
                <span class="form-tip">天，超过此天数的备份将被自动删除</span>
              </el-form-item>
              <el-form-item class="form-actions">
                <el-button type="primary" @click="saveSchedule" :loading="saving" class="schedule-save-btn">保存设置</el-button>
                <el-button @click="loadSchedule" class="schedule-reset-btn">重置</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script lang="ts" setup>
import logger from '@/utils/logger'
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Clock, Refresh, Delete } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()

const activeTab = ref('backup')
const loading = ref(false)
const backingUp = ref(false)
const restoring = ref(false)
const saving = ref(false)
const backupRecords = ref([])

const showProgress = ref(false)
const progressTitle = ref('')
const progressPercentage = ref(0)
const progressStatus = ref('')
const progressTip = ref('')
let progressInterval = null

const scheduleForm = ref({
  enabled: false,
  frequency: 'daily',
  time: null,
  retentionDays: 30
})

const startProgress = (title, tip) => {
  showProgress.value = true
  progressTitle.value = title
  progressPercentage.value = 0
  progressStatus.value = ''
  progressTip.value = tip
  
  if (progressInterval) {
    clearInterval(progressInterval)
  }
  
  progressInterval = setInterval(() => {
    if (progressPercentage.value < 95) {
      progressPercentage.value += Math.random() * 10
      if (progressPercentage.value > 95) {
        progressPercentage.value = 95
      }
    }
  }, 1000)
}

const completeProgress = (success, message) => {
  if (progressInterval) {
    clearInterval(progressInterval)
    progressInterval = null
  }
  
  progressPercentage.value = 100
  progressStatus.value = success ? 'success' : 'exception'
  progressTip.value = message
  
  setTimeout(() => {
    showProgress.value = false
    progressPercentage.value = 0
    progressStatus.value = ''
  }, 3000)
}

const fetchBackupRecords = async () => {
  loading.value = true
  try {
    const response = await request.get('/admin/backup/records')
    if (response.code === 200) {
      backupRecords.value = response.data || []
    } else {
      ElMessage.error(response.message || '获取备份记录失败')
    }
  } catch (error) {
    ElMessage.error('获取备份记录失败')
  } finally {
    loading.value = false
  }
}

const handleManualBackup = async () => {
  try {
    await ElMessageBox.confirm('确定要立即备份数据吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
      center: true,
      customClass: 'backup-confirm-dialog'
    })
    
    backingUp.value = true
    startProgress('正在备份数据...', '正在执行数据库备份操作，请稍候')
    
    const response = await request.post('/admin/backup/manual')
    
    if (response.code === 200) {
      completeProgress(true, '备份成功')
      ElMessage.success('备份成功')
      fetchBackupRecords()
    } else {
      completeProgress(false, response.message || '备份失败')
      ElMessage.error(response.message || '备份失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      completeProgress(false, '备份失败')
      ElMessage.error('备份失败')
    }
  } finally {
    backingUp.value = false
  }
}

const handleDownload = async (row) => {
  try {
    const blob = await request.get(`/admin/backup/download/${row.id}`, {
      responseType: 'blob'
    })
    
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = row.backupName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch (error) {
    console.error('下载失败:', error)
    ElMessage.error('下载失败')
  }
}

const handleRestore = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要恢复备份"${row.backupName}"吗？此操作将覆盖当前数据！`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
      center: true,
      customClass: 'restore-warning-dialog'
    })
    
    restoring.value = true
    startProgress('正在恢复数据...', '正在执行数据库恢复操作，这可能需要1-2分钟，请耐心等待')
    
    const response = await request.post(`/admin/backup/restore/${row.id}`, {}, {
      timeout: 120000
    })
    
    if (response.code === 200) {
      completeProgress(true, '数据恢复成功，正在重新连接数据库...')
      ElMessage.success('数据恢复成功，系统将自动重启')
      setTimeout(() => {
        window.location.reload()
      }, 3000)
    } else {
      completeProgress(false, response.message || '数据恢复失败')
      ElMessage.error(response.message || '数据恢复失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      logger.error('数据恢复失败:', error)
      const errorMsg = error.response?.data?.msg || error.message || '数据恢复失败'
      completeProgress(false, errorMsg)
      ElMessage.error(errorMsg)
    }
  } finally {
    restoring.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除备份"${row.backupName}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
      center: true,
      customClass: 'delete-confirm-dialog'
    })
    
    const response = await request.delete(`/admin/backup/${row.id}`)
    if (response.code === 200) {
      ElMessage.success('删除成功')
      fetchBackupRecords()
    } else {
      ElMessage.error(response.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const loadSchedule = async () => {
  try {
    const response = await request.get('/admin/backup/schedule')
    if (response.code === 200) {
      const data = response.data
      scheduleForm.value = {
        enabled: data.enabled,
        frequency: data.frequency,
        time: data.time ? parseTime(data.time) : null,
        retentionDays: data.retentionDays
      }
    }
  } catch (error) {
    ElMessage.error('获取自动备份设置失败')
  }
}

const parseTime = (timeStr) => {
  if (!timeStr) return null
  const [hours, minutes] = timeStr.split(':')
  const date = new Date()
  date.setHours(parseInt(hours), parseInt(minutes), 0, 0)
  return date
}

const formatTime = (date) => {
  if (!date) return null
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${hours}:${minutes}`
}

const saveSchedule = async () => {
  saving.value = true
  try {
    const data = {
      ...scheduleForm.value,
      time: formatTime(scheduleForm.value.time)
    }
    const response = await request.put('/admin/backup/schedule', data)
    if (response.code === 200) {
      ElMessage.success('保存成功')
    } else {
      ElMessage.error(response.message || '保存失败')
    }
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}

const formatDateTime = (date) => {
  if (!date) return ''
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

const formatTableList = (tableList) => {
  if (!tableList) return '无'
  const tables = tableList.split(',')
  if (tables.length <= 3) {
    return tables.join(', ')
  }
  return `${tables.slice(0, 3).join(', ')}... (共${tables.length}个表)`
}

onMounted(() => {
  fetchBackupRecords()
  loadSchedule()
})
</script>

<style scoped>
.backup-management-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

/* 页面标题区域 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  border-radius: 16px;
  padding: 24px 32px;
  color: white;
  margin-bottom: 24px;
  box-shadow: 0 8px 25px rgba(64, 158, 255, 0.25);
  position: relative;
  overflow: hidden;
}

.page-header::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -50%;
  width: 100%;
  height: 100%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
  transform: rotate(30deg);
}

.header-content h1 {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
  color: white;
}

.header-content p {
  font-size: 14px;
  opacity: 0.95;
  font-weight: 500;
  margin: 0;
}

.header-illustration {
  position: relative;
  width: 100px;
  height: 100px;
}

.illustration-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  animation: float 6s ease-in-out infinite;
}

.circle-1 {
  width: 60px;
  height: 60px;
  top: 0;
  right: 0;
  animation-delay: 0s;
}

.circle-2 {
  width: 40px;
  height: 40px;
  bottom: 10px;
  right: 20px;
  animation-delay: 3s;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

/* 主卡片样式 */
.main-card {
  border-radius: 16px;
  border: none;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  overflow: hidden;
  min-height: 500px;
}

/* 标签页样式 */
.backup-tabs :deep(.el-tabs__content) {
  padding: 24px;
}

/* 备份内容区域 */
.backup-content {
  padding: 0;
}

.backup-header, .schedule-header {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 2px solid #f0f7ff;
}

.backup-title, .schedule-title {
  font-size: 20px;
  font-weight: 600;
  color: #409EFF;
  margin: 0 0 8px 0;
}

.backup-description, .schedule-description {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

/* 备份按钮 */
.backup-actions {
  margin-bottom: 24px;
}

.backup-btn {
  border-radius: 8px;
  padding: 10px 20px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.backup-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

/* 进度条容器 */
.progress-container {
  margin-bottom: 24px;
  padding: 20px;
  background: linear-gradient(135deg, #f8fbff 0%, #f0f7ff 100%);
  border-radius: 12px;
  border: 1px solid #e6f7ff;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.progress-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.progress-percentage {
  font-size: 20px;
  font-weight: bold;
  color: #409eff;
}

.progress-tip {
  margin: 16px 0 0 0;
  font-size: 14px;
  color: #909399;
  text-align: center;
}

.custom-progress :deep(.el-progress-bar__inner) {
  background: linear-gradient(90deg, #409eff, #52c41a);
  transition: all 0.3s ease;
}

.custom-progress :deep(.el-progress-bar__innerText) {
  color: white;
  font-weight: 600;
}

/* 表格样式 */
.backup-table {
  border-radius: 8px;
}

.backup-table :deep(.el-table__header) th {
  background-color: #f8fbff;
  color: #409EFF;
  font-weight: 600;
}

.backup-table :deep(.el-table__row) td {
  border-bottom: 1px solid #f0f7ff;
}

.file-size, .time-text {
  font-weight: 500;
}

.type-tag, .status-tag {
  border-radius: 12px;
  font-weight: 500;
}

/* 操作按钮 */
.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
  align-items: center;
}

.table-btn {
  border-radius: 6px;
  padding: 6px 8px;
  transition: all 0.3s ease;
}

.table-btn:hover {
  transform: scale(1.1);
}

.table-btn.download {
  background-color: #409EFF;
  border-color: #409EFF;
}

.table-btn.download:hover {
  background-color: #66b1ff;
  border-color: #66b1ff;
}

.table-btn.restore {
  background-color: #e6a23c;
  border-color: #e6a23c;
}

.table-btn.restore:hover {
  background-color: #ebb563;
  border-color: #ebb563;
}

.table-btn.delete {
  background-color: #f56c6c;
  border-color: #f56c6c;
}

.table-btn.delete:hover {
  background-color: #f78989;
  border-color: #f78989;
}

/* 自动备份设置 */
.schedule-content {
  max-width: 700px;
  margin: 0 auto;
}

.schedule-form {
  padding: 20px 0;
}

.schedule-switch :deep(.el-switch__core) {
  width: 48px !important;
  height: 24px;
}

.schedule-switch :deep(.el-switch__core::after) {
  width: 20px;
  height: 20px;
}

.schedule-select, .schedule-time, .schedule-input {
  width: 250px;
}

.schedule-input :deep(.el-input__inner) {
  text-align: center;
}

.form-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 13px;
}

.form-actions {
  margin-top: 30px;
  padding-top: 20px;
  border-top: 2px solid #f0f7ff;
}

.schedule-save-btn, .schedule-reset-btn {
  border-radius: 8px;
  padding: 10px 20px;
}

.schedule-save-btn {
  background: linear-gradient(135deg, #409EFF, #52c41a);
  border: none;
}

.schedule-save-btn:hover {
  background: linear-gradient(135deg, #66b1ff, #73d13d);
  transform: translateY(-1px);
}

/* 响应式设计 */
@media screen and (max-width: 768px) {
  .backup-management-container {
    padding: 15px;
  }
  
  .page-header {
    flex-direction: column;
    text-align: center;
    padding: 20px;
  }
  
  .header-illustration {
    margin-top: 15px;
  }
  
  .backup-tabs :deep(.el-tabs__content) {
    padding: 16px;
  }
  
  .action-buttons {
    flex-wrap: wrap;
    justify-content: center;
  }
  
  .schedule-select, .schedule-time, .schedule-input {
    width: 100%;
  }
  
  .form-tip {
    display: block;
    margin-left: 0;
    margin-top: 8px;
  }
  
  .form-actions {
    text-align: center;
  }
  
  .schedule-save-btn, .schedule-reset-btn {
    width: 100%;
    margin-bottom: 10px;
  }
}

@media screen and (max-width: 480px) {
  .page-title {
    font-size: 22px;
  }
  
  .backup-title, .schedule-title {
    font-size: 18px;
  }
  
  .progress-container {
    padding: 15px;
  }
  
  .progress-title {
    font-size: 14px;
  }
  
  .progress-percentage {
    font-size: 16px;
  }
}
</style>