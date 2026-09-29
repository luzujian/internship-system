<template>
  <el-dialog
    :model-value="modelValue"
    title="公告详情"
    width="700px"
    class="announcement-detail-dialog"
    :append-to-body="true"
    :lock-scroll="true"
    modal-class="global-modal"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="announcement" class="announcement-detail">
      <h2 class="detail-title">{{ announcement.title }}</h2>
      <div class="detail-meta">
        <div class="meta-item">
          <span class="meta-label">发布日期:</span>
          <span class="meta-value">{{ formatAnnouncementTime(announcement.publishDate || announcement.publishTime || announcement.time) || '-' }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">发布人身份:</span>
          <el-tag :type="getPublisherRoleTagType(announcement.publisherRole || announcement.publisher_role)" size="small">
            {{ getPublisherRoleDisplay(announcement) }}
          </el-tag>
        </div>
        <div class="meta-item">
          <span class="meta-label">发布人姓名:</span>
          <span class="meta-value">{{ getPublisherNameText(announcement) }}</span>
        </div>
      </div>
      <el-divider />
      <div class="detail-content" v-html="sanitizeHTML(announcement.content || '')"></div>
      <div v-if="attachmentList.length > 0" class="attachments-section">
        <el-divider />
        <div class="attachments-title">
          <el-icon><Paperclip /></el-icon>
          <span>附件列表</span>
        </div>
        <div class="attachments-list">
          <el-tag
            v-for="(attachment, index) in attachmentList"
            :key="`${attachment.name}-${index}`"
            class="attachment-tag"
            @click.stop="previewAnnouncementFile(attachment)"
          >
            <el-icon class="attachment-icon"><Document /></el-icon>
            {{ attachment.name }}
            <el-icon class="preview-icon"><ZoomIn /></el-icon>
          </el-tag>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button class="close-btn" @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>

  <FilePreviewDialog
    v-model="filePreviewVisible"
    :file-url="currentFileUrl"
    :file-name="currentFileName"
    :authorized-url="currentAuthorizedUrl"
  />
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Document, Paperclip, ZoomIn } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import FilePreviewDialog from '@/components/FilePreviewDialog.vue'
import { announcementAttachmentByteUrl } from '@/utils/authorizedFileUrl'
import { sanitizeHTML } from '@/utils/sanitize'
import {
  formatAnnouncementTime,
  getPublisherNameText,
  getPublisherRoleDisplay,
  getPublisherRoleTagType,
  parseAnnouncementAttachments
} from '@/utils/studentAnnouncement'
import type { AnnouncementMessage } from '@/types/announcement'

const props = defineProps<{
  modelValue: boolean
  announcement: AnnouncementMessage | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const filePreviewVisible = ref(false)
const currentFileUrl = ref('')
const currentFileName = ref('')
const currentAuthorizedUrl = ref('')

const attachmentList = computed(() => parseAnnouncementAttachments(props.announcement?.attachments))

const previewAnnouncementFile = (attachment: { name: string; url?: string }) => {
  const authorizedUrl = announcementAttachmentByteUrl(Number(props.announcement?.id), attachment.name)
  if (!authorizedUrl) {
    ElMessage.error('公告附件记录不完整')
    return
  }
  currentFileUrl.value = typeof attachment.url === 'string' ? attachment.url : ''
  currentFileName.value = attachment.name
  currentAuthorizedUrl.value = authorizedUrl
  filePreviewVisible.value = true
}

watch(() => props.modelValue, visible => {
  if (!visible) filePreviewVisible.value = false
})
</script>

<style scoped>
.announcement-detail-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.announcement-detail-dialog :deep(.el-dialog__body) {
  padding: 24px;
}

.detail-title {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
  margin: 0 0 16px;
  text-align: center;
  overflow-wrap: anywhere;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  font-size: 14px;
  color: #909399;
  padding: 12px 16px;
  background: #f8f9fa;
  border-radius: 8px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.meta-label {
  font-weight: 500;
  color: #606266;
}

.meta-value {
  color: #303133;
  overflow-wrap: anywhere;
}

.detail-content {
  font-size: 15px;
  line-height: 1.8;
  color: #606266;
  padding: 16px;
  background: white;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  min-height: 150px;
  overflow-wrap: anywhere;
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
  color: #409eff;
  max-width: 100%;
  overflow-wrap: anywhere;
}

.attachment-tag:hover,
.attachment-tag:focus-visible {
  background: #409eff;
  color: white;
}

.preview-icon {
  margin-left: 6px;
}

.close-btn {
  padding: 10px 32px;
}
</style>
