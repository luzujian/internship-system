<template>
  <div class="agreement-upload">
    <el-upload
      action="#" drag multiple :auto-upload="false" :show-file-list="false"
      accept=".pdf,.doc,.docx" :disabled="disabled" :on-change="addFile"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">点击或拖拽上传盖章实习协议</div>
    </el-upload>
    <p class="agreement-tip">请上传已盖章的实习协议，支持 Word（DOC、DOCX）和 PDF，可上传多个文件，单个文件不超过 20MB。</p>
    <div v-for="file in modelValue" :key="file.uid" class="agreement-file">
      <div class="agreement-file-main">
        <el-icon><Document /></el-icon>
        <div>
          <strong>{{ file.fileName }}</strong>
          <small v-if="file.status === 'success'">已上传 · {{ formatSize(file.fileSize) }}</small>
          <small v-else-if="file.status === 'error'" class="file-error">{{ file.error || '上传失败，请重试' }}</small>
          <el-progress v-else :percentage="file.progress" :stroke-width="4" />
        </div>
      </div>
      <div class="agreement-file-actions">
        <el-button v-if="file.status === 'error'" link type="primary" :disabled="disabled" @click="sendFile(file)">重试</el-button>
        <el-button link type="danger" :disabled="disabled" @click="removeFile(file)">移除</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, type UploadFile } from 'element-plus'
import { Document, UploadFilled } from '@element-plus/icons-vue'
import { removeAgreementUpload, uploadAgreementFile, type AgreementUploadFile } from '@/api/internshipAgreements'

const props = withDefaults(defineProps<{
  modelValue: AgreementUploadFile[]
  disabled?: boolean
  companyKey?: string
}>(), { disabled: false, companyKey: '' })
const emit = defineEmits<{ 'update:modelValue': [files: AgreementUploadFile[]] }>()
const controllers = new Map<string, AbortController>()
const currentFiles = ref<AgreementUploadFile[]>([...props.modelValue])
watch(() => props.modelValue, value => { currentFiles.value = [...value] }, { flush: 'sync' })
const updateFiles = (files: AgreementUploadFile[]) => {
  currentFiles.value = files
  emit('update:modelValue', files)
}
let disposed = false
const busy = computed(() => currentFiles.value.some(file => file.status === 'uploading'))

const patchFile = (uid: string, patch: Partial<AgreementUploadFile>) => {
  if (!disposed) updateFiles(currentFiles.value.map(file => file.uid === uid ? { ...file, ...patch } : file))
}

const addFile = (selected: UploadFile) => {
  const raw = selected.raw
  if (!raw || props.disabled) return
  const extension = raw.name.split('.').pop()?.toLowerCase() || ''
  if (!['pdf', 'doc', 'docx'].includes(extension)) return void ElMessage.warning('仅支持 PDF、DOC、DOCX 格式')
  if (!raw.size || raw.size > 20 * 1024 * 1024) return void ElMessage.warning('文件不能为空，单个文件不能超过 20MB')
  const file: AgreementUploadFile = { uid: String(selected.uid), uploadId: 0, fileName: raw.name,
    fileType: extension, fileSize: raw.size, raw, status: 'uploading', progress: 0 }
  updateFiles([...currentFiles.value, file])
  // 等待父组件接收文件清单后再更新进度。
  queueMicrotask(() => { void sendFile(file) })
}

const sendFile = async (file: AgreementUploadFile) => {
  if (!file.raw || disposed) return
  const controller = new AbortController()
  controllers.set(file.uid, controller)
  patchFile(file.uid, { status: 'uploading', progress: 0, error: '' })
  try {
    const response: any = await uploadAgreementFile(file.raw, controller.signal, percent => patchFile(file.uid, { progress: percent }))
    if (response.code !== 200 || !response.data?.uploadId) throw new Error(response.message || '上传失败')
    if (disposed || !currentFiles.value.some(item => item.uid === file.uid)) {
      await removeAgreementUpload(response.data.uploadId).catch(() => undefined)
      return
    }
    patchFile(file.uid, { ...response.data, status: 'success', progress: 100 })
  } catch (error: any) {
    if (!controller.signal.aborted) patchFile(file.uid, { status: 'error', error: error?.response?.data?.message || error?.message || '上传失败，请重试' })
  } finally {
    controllers.delete(file.uid)
  }
}

const cleanupFile = (file: AgreementUploadFile) => {
  controllers.get(file.uid)?.abort()
  if (file.uploadId && !file.persisted) void removeAgreementUpload(file.uploadId).catch(() => undefined)
}

const removeFile = (file: AgreementUploadFile) => {
  cleanupFile(file)
  updateFiles(currentFiles.value.filter(item => item.uid !== file.uid))
}

const reset = () => {
  currentFiles.value.forEach(cleanupFile)
  updateFiles([])
}

const validate = () => {
  if (busy.value) { ElMessage.warning('协议文件正在上传，请等待上传完成'); return false }
  if (currentFiles.value.some(file => file.status === 'error')) { ElMessage.warning('请重试或移除上传失败的协议文件'); return false }
  if (!currentFiles.value.length) { ElMessage.warning('请至少上传一份已盖章的实习协议'); return false }
  return true
}

const markCommitted = () => updateFiles(currentFiles.value.map(file => ({ ...file, persisted: true })))
watch(() => props.companyKey, (value, previous) => {
  if (value.trim() !== previous.trim() && currentFiles.value.length) reset()
})
onBeforeUnmount(() => { disposed = true; currentFiles.value.forEach(cleanupFile) })
const formatSize = (value: number) => value < 1024 * 1024 ? `${(value / 1024).toFixed(1)} KB` : `${(value / 1024 / 1024).toFixed(1)} MB`
defineExpose({ validate, busy, reset, markCommitted })
</script>

<style scoped>
.agreement-upload { width: 100%; }
.agreement-tip { margin: 8px 0 12px; color: #909399; font-size: 12px; line-height: 1.7; }
.agreement-file { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px; margin-top: 8px; border: 1px solid #ebeef5; border-radius: 8px; background: #fafafa; }
.agreement-file-main { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 0; color: #606266; }
.agreement-file-main > div { flex: 1; min-width: 0; }
.agreement-file-main strong { display: block; overflow-wrap: anywhere; font-size: 13px; font-weight: 500; color: #303133; }
.agreement-file-main small { display: block; margin-top: 4px; color: #909399; }
.agreement-file-main .file-error { color: #f56c6c; }
.agreement-file-actions { flex-shrink: 0; }
.agreement-upload :deep(.el-upload) { width: 100%; }
.agreement-upload :deep(.el-upload-dragger) { padding: 18px 12px; }
.agreement-upload :deep(.el-icon--upload) { margin-bottom: 8px; font-size: 32px; line-height: 1; }
@media (max-width: 720px) { .agreement-file { align-items: flex-start; flex-wrap: wrap; } }
</style>
