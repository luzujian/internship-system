<template>
  <div class="announcement-view">
    <el-card class="announcement-card">
      <template #header>
        <div class="card-header">
          <span>公告通知</span>
          <el-button type="primary" size="small" @click="refreshAnnouncements">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </template>
      
      <div v-loading="loading" class="announcement-list">
        <el-empty v-if="!loading && announcements.length === 0" description="暂无公告" />
        
        <div v-for="announcement in announcements" :key="announcement.id" class="announcement-item" @click="viewAnnouncement(announcement)">
          <div class="announcement-header">
            <div class="announcement-title">
              <el-tag v-if="announcement.priority === 'important'" type="warning" size="small" class="priority-tag">重要</el-tag>
              <span class="title-text">{{ announcement.title }}</span>
            </div>
            <div class="announcement-meta">
              <span class="publish-time">{{ formatTime(announcement.publishTime) }}</span>
              <el-tag v-if="!announcement.isRead" type="danger" size="small" effect="plain">未读</el-tag>
            </div>
          </div>
          <div class="announcement-content">
            {{ getPreviewText(announcement.content) }}
          </div>
          <div class="announcement-footer">
            <div class="publisher-info">
              <span class="publisher-name">{{ announcement.publisher }}</span>
              <el-tag :type="getPublisherRoleType(announcement.publisherRole)" size="small" class="publisher-role-tag">
                {{ getPublisherRoleText(announcement.publisherRole) }}
              </el-tag>
            </div>
            <span class="read-count">阅读: {{ announcement.readCount || 0 }}</span>
          </div>
        </div>
      </div>
    </el-card>
    
    <el-dialog v-model="viewDialogVisible" title="公告详情" width="700px">
      <div v-if="currentAnnouncement" class="announcement-detail">
        <h2 class="detail-title">{{ currentAnnouncement.title }}</h2>
        <div class="detail-meta">
          <span class="meta-item">发布人: {{ currentAnnouncement.publisher }}</span>
          <el-tag :type="getPublisherRoleType(currentAnnouncement.publisherRole)" size="small" class="publisher-role-tag">
            {{ getPublisherRoleText(currentAnnouncement.publisherRole) }}
          </el-tag>
          <span class="meta-item">目标群体: {{ getTargetText(currentAnnouncement.targetType, currentAnnouncement.targetValue) }}</span>
          <span class="meta-item">发布时间: {{ formatTime(currentAnnouncement.publishTime) }}</span>
          <span class="meta-item">阅读次数: {{ currentAnnouncement.readCount || 0 }}</span>
        </div>
        <el-divider />
        <div class="detail-content" v-html="currentAnnouncement.content"></div>
        <div v-if="currentAnnouncement.attachments && parseAttachments(currentAnnouncement.attachments).length > 0" class="attachments-section">
          <el-divider></el-divider>
          <div class="attachments-title">
            <el-icon><Paperclip /></el-icon>
            <span>附件列表</span>
          </div>
          <div class="attachments-list">
            <el-tag
              v-for="(attachment, index) in parseAttachments(currentAnnouncement.attachments)"
              :key="index"
              class="attachment-tag"
              @click.stop="previewFile(attachment)"
            >
              <el-icon class="attachment-icon"><Document /></el-icon>
              {{ attachment.name }}
              <el-icon class="preview-icon"><ZoomIn /></el-icon>
            </el-tag>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="viewDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 文件预览对话框 -->
    <FilePreviewDialog
      v-model="filePreviewVisible"
      :file-url="currentFileUrl"
      :file-name="currentFileName"
    />
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Document, Paperclip, ZoomIn } from '@element-plus/icons-vue'
import { getAnnouncementsForUser, createAnnouncementReadRecord } from '@/api/announcement'
import { useAuthStore } from '@/store/auth'
import FilePreviewDialog from '@/components/FilePreviewDialog.vue'

const authStore = useAuthStore()
const loading = ref(false)
const announcements = ref([])
const viewDialogVisible = ref(false)
const currentAnnouncement = ref(null)

// 文件预览相关
const filePreviewVisible = ref(false)
const currentFileUrl = ref('')
const currentFileName = ref('')

const getTeacherType = () => {
  const user = authStore.user
  if (!user || !user.teacherType) return ''
  
  const typeMap = {
    'COLLEGE': 'COLLEGE',
    'DEPARTMENT': 'DEPARTMENT',
    'COUNSELOR': 'COUNSELOR'
  }
  return typeMap[user.teacherType] || ''
}

const loadAnnouncements = async () => {
  loading.value = true
  try {
    const teacherType = getTeacherType()
    const response = await getAnnouncementsForUser('TEACHER', teacherType)
    if (response.data && response.data.code === 200) {
      announcements.value = response.data.data || []
    } else {
      ElMessage.error(response.data.message || '获取公告失败')
    }
  } catch (error) {
    console.error('获取公告失败:', error)
    ElMessage.error('获取公告失败')
  } finally {
    loading.value = false
  }
}

const refreshAnnouncements = () => {
  loadAnnouncements()
}

const viewAnnouncement = async (announcement) => {
  currentAnnouncement.value = announcement
  
  try {
    await createAnnouncementReadRecord({
      announcementId: announcement.id,
      userId: authStore.user?.id ? parseInt(String(authStore.user.id)) : 0,
      userRole: 'TEACHER'
    })
    announcement.isRead = true
  } catch (error) {
    console.error('记录阅读状态失败:', error)
  }
  
  viewDialogVisible.value = true
}

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

const parseAttachments = (attachments) => {
  if (!attachments) return []
  try {
    if (typeof attachments === 'string') {
      return JSON.parse(attachments)
    }
    return attachments
  } catch (e) {
    console.error('解析附件数据失败:', e)
    return []
  }
}

const downloadAttachment = (attachment) => {
  if (!attachment.url) {
    ElMessage.error('附件地址不存在')
    return
  }

  const link = document.createElement('a')
  link.href = attachment.url
  link.download = attachment.name || '附件'
  link.target = '_blank'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

// 预览文件
const previewFile = (attachment) => {
  if (!attachment.url) {
    ElMessage.error('附件地址不存在')
    return
  }
  currentFileUrl.value = attachment.url
  currentFileName.value = attachment.name
  filePreviewVisible.value = true
}

const getPreviewText = (content) => {
  if (!content) return ''
  const text = content.replace(/<[^>]*>/g, '')
  return text.length > 100 ? text.substring(0, 100) + '...' : text
}

const getPublisherRoleText = (role) => {
  const map = {
    ADMIN: '管理员',
    COLLEGE: '学院教师',
    DEPARTMENT: '系室教师',
    COUNSELOR: '辅导员'
  }
  return map[role] || '-'
}

const getPublisherRoleType = (role) => {
  const map = {
    ADMIN: 'danger',
    COLLEGE: 'primary',
    DEPARTMENT: 'success',
    COUNSELOR: 'warning'
  }
  return map[role] || 'info'
}

const getTargetText = (targetType, targetValue) => {
  if (!targetType) return '-'
  
  const typeMap = {
    ALL: '全体师生',
    STUDENT: '全体学生',
    TEACHER: '全体教师',
    TEACHER_TYPE: '特定教师类别',
    MAJOR: '特定专业学生'
  }
  
  const typeText = typeMap[targetType] || targetType
  
  if (targetType === 'TEACHER_TYPE' && targetValue) {
    const teacherTypeMap = {
      COLLEGE: '学院教师',
      DEPARTMENT: '系室教师',
      COUNSELOR: '辅导员'
    }
    return `${typeText}（${teacherTypeMap[targetValue] || targetValue}）`
  }
  
  if (targetType === 'MAJOR' && targetValue) {
    return `${typeText}（${targetValue}）`
  }
  
  return typeText
}

onMounted(() => {
  loadAnnouncements()
})
</script>

<style scoped>
.announcement-view {
  padding: 20px;
}

.announcement-card {
  min-height: 600px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: bold;
  font-size: 16px;
}

.announcement-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.announcement-item {
  padding: 16px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  background: #fff;
}

.announcement-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
}

.announcement-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.announcement-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.priority-tag {
  flex-shrink: 0;
}

.title-text {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
  line-height: 1.4;
}

.announcement-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.publish-time {
  font-size: 13px;
  color: #909399;
}

.announcement-content {
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.announcement-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #909399;
}

.publisher-info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.publisher-name {
  font-weight: 500;
  color: #303133;
}

.publisher-role-tag {
  border-radius: 12px;
  font-weight: 500;
}

.announcement-detail {
  padding: 0;
}

.detail-title {
  font-size: 20px;
  font-weight: bold;
  color: #303133;
  margin: 0 0 16px 0;
}

.detail-meta {
  display: flex;
  gap: 24px;
  font-size: 14px;
  color: #909399;
  margin-bottom: 16px;
}

.meta-item {
  display: flex;
  align-items: center;
}

.detail-content {
  font-size: 15px;
  line-height: 1.8;
  color: #606266;
}

.detail-content :deep(img) {
  max-width: 100%;
  height: auto;
}

.attachments-section {
  margin-top: 20px;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 8px;
}

.attachments-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #303133;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 12px;
}

.attachments-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.attachment-tag {
  cursor: pointer;
  padding: 8px 16px;
  background: white;
  border: 1px solid #dcdfe6;
  color: #409EFF;
  transition: all 0.3s ease;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.attachment-tag:hover {
  background: #409EFF;
  color: white;
  border-color: #409EFF;
  transform: translateY(-2px);
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);
}

.attachment-icon {
  margin-right: 2px;
}

.preview-icon {
  font-size: 14px;
  opacity: 0.8;
  transition: all 0.3s ease;
}

.attachment-tag:hover .preview-icon {
  opacity: 1;
  transform: scale(1.1);
}
</style>
