<template>
  <div class="application-material-editor" :aria-busy="loading">
    <p class="material-scope-note">本次调整仅用于此岗位申请，不会修改个人中心的简历和证书。提交后材料将固定保存。</p>
    <div v-if="loading" class="material-state" role="status">正在加载个人材料…</div>
    <div v-else-if="error" class="material-state" role="alert">
      <span>{{ error }}</span>
      <el-button link type="primary" @click="loadLibrary">重新加载</el-button>
    </div>
    <template v-else>
      <section class="selection-section" aria-label="本次申请的简历">
        <div class="selection-header">
          <strong>简历 <span class="selection-limit">最多1份</span></strong>
          <div class="selection-actions">
            <el-button size="small" :disabled="disabled" @click="resumeInput?.click()">{{ resumeFile || resumeArchiveId ? '更换本次简历' : '上传本次简历' }}</el-button>
            <el-button v-if="libraryResumes.length && (resumeFile || !resumeArchiveId)" link type="primary" :disabled="disabled" @click="restoreResume">使用统一简历</el-button>
          </div>
        </div>
        <div v-if="resumeFile || selectedResume" class="selected-file">
          <span class="selected-file-name">{{ resumeFile?.name || selectedResume?.name }}</span>
          <el-tag size="small" :type="resumeFile ? 'warning' : 'info'">{{ resumeFile ? '仅本次' : '个人中心' }}</el-tag>
          <el-button link type="danger" :disabled="disabled" @click="clearResume">移除</el-button>
        </div>
        <p v-else class="material-empty">暂无简历，可直接上传本次申请使用的文件。</p>
        <input ref="resumeInput" class="material-file-input" type="file" accept=".pdf,.doc,.docx" aria-label="选择本次简历" :disabled="disabled" @change="chooseResume" />
      </section>

      <section class="selection-section" aria-label="本次申请的证书">
        <div class="selection-header">
          <strong>证书 <span class="selection-limit">已选{{ selectedCertificateIds.length + certificateFiles.length }}份</span></strong>
          <el-button size="small" :disabled="disabled" @click="certificateInput?.click()">添加本次证书</el-button>
        </div>
        <p v-if="libraryCertificates.length" class="material-empty">勾选本次需要提交的个人中心证书。</p>
        <el-checkbox-group v-model="selectedCertificateIds" :disabled="disabled" class="certificate-options">
          <el-checkbox v-for="file in libraryCertificates" :key="file.id" :value="file.id">
            <span class="selected-file-name">{{ file.name }}</span>
          </el-checkbox>
        </el-checkbox-group>
        <div v-for="(file, index) in certificateFiles" :key="`${file.name}-${index}`" class="selected-file">
          <span class="selected-file-name">{{ file.name }}</span>
          <el-tag size="small" type="warning">仅本次</el-tag>
          <el-button link type="danger" :disabled="disabled" @click="removeCertificate(index)">移除</el-button>
        </div>
        <p v-if="!libraryCertificates.length && !certificateFiles.length" class="material-empty">暂无证书，可按岗位需要上传。</p>
        <input ref="certificateInput" class="material-file-input" type="file" multiple accept=".pdf,.doc,.docx,.jpg,.jpeg,.png" aria-label="选择本次证书" :disabled="disabled" @change="chooseCertificates" />
      </section>
      <p class="material-format-note">单个文件不超过10MB，总大小不超过80MB；证书最多20份。简历支持PDF、Word，证书还支持JPG、PNG。</p>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import type { ApplicationMaterialSelection } from '@/api/jobApplicationMaterials'

defineProps<{ modelValue: ApplicationMaterialSelection | null; disabled?: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: ApplicationMaterialSelection | null]
  ready: [value: boolean]
}>()
interface LibraryFile { id: number; name: string }
const libraryResumes = ref<LibraryFile[]>([])
const libraryCertificates = ref<LibraryFile[]>([])
const resumeArchiveId = ref<number | null>(null)
const resumeFile = ref<File | null>(null)
const selectedCertificateIds = ref<number[]>([])
const certificateFiles = ref<File[]>([])
const resumeInput = ref<HTMLInputElement | null>(null)
const certificateInput = ref<HTMLInputElement | null>(null)
const loading = ref(true)
const error = ref('')
let loadVersion = 0
const selectedResume = computed(() => libraryResumes.value.find(file => file.id === resumeArchiveId.value))

const publish = () => {
  if (loading.value || error.value) return
  emit('update:modelValue', {
    archiveIds: [...(resumeArchiveId.value ? [resumeArchiveId.value] : []), ...selectedCertificateIds.value],
    resume: resumeFile.value,
    certificates: [...certificateFiles.value]
  })
}
watch([resumeArchiveId, resumeFile, selectedCertificateIds, certificateFiles], publish, { deep: true })

const loadLibrary = async () => {
  const version = ++loadVersion
  loading.value = true
  error.value = ''
  emit('ready', false)
  emit('update:modelValue', null)
  try {
    const [resumes, certificates] = await Promise.all([
      request.get<LibraryFile[]>('/student/profile/resumes'),
      request.get<LibraryFile[]>('/student/profile/certificates')
    ])
    if (version !== loadVersion) return
    if (!Array.isArray(resumes.data) || !Array.isArray(certificates.data)) throw new Error('材料列表返回异常')
    libraryResumes.value = resumes.data.map(file => ({ id: Number(file.id), name: file.name }))
    libraryCertificates.value = certificates.data.map(file => ({ id: Number(file.id), name: file.name }))
    resumeArchiveId.value = libraryResumes.value[0]?.id ?? null
    resumeFile.value = null
    selectedCertificateIds.value = libraryCertificates.value.map(file => file.id)
    certificateFiles.value = []
    loading.value = false
    publish()
    emit('ready', true)
  } catch {
    if (version !== loadVersion) return
    loading.value = false
    error.value = '个人材料加载失败，请重新加载后提交。'
  }
}

const validFile = (file: File, certificate = false) => {
  const extension = file.name.split('.').pop()?.toLowerCase() || ''
  const allowed = certificate ? ['pdf', 'doc', 'docx', 'jpg', 'jpeg', 'png'] : ['pdf', 'doc', 'docx']
  if (!allowed.includes(extension)) { ElMessage.error('文件格式不支持，请按下方提示选择文件'); return false }
  if (!file.size || file.size > 10 * 1024 * 1024) { ElMessage.error('文件不能为空，且单个文件不能超过10MB'); return false }
  return true
}
const chooseResume = (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !validFile(file)) return
  resumeFile.value = file
  resumeArchiveId.value = null
}
const restoreResume = () => { resumeFile.value = null; resumeArchiveId.value = libraryResumes.value[0]?.id ?? null }
const clearResume = () => { resumeFile.value = null; resumeArchiveId.value = null }
const chooseCertificates = (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length || !files.every(file => validFile(file, true))) return
  if (selectedCertificateIds.value.length + certificateFiles.value.length + files.length > 20) {
    ElMessage.error('每次申请最多提交20份证书'); return
  }
  certificateFiles.value = [...certificateFiles.value, ...files]
}
const removeCertificate = (index: number) => { certificateFiles.value = certificateFiles.value.filter((_, i) => i !== index) }
onMounted(loadLibrary)
onUnmounted(() => { loadVersion++ })
</script>

<style scoped>
.material-scope-note { margin: 0 0 16px; padding: 12px; border-radius: 8px; background: var(--el-color-primary-light-9); color: var(--el-color-primary-dark-2); font-size: 13px; line-height: 1.7; }
.selection-section + .selection-section { margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--el-border-color-lighter); }
.selection-header, .selection-actions, .selected-file { display: flex; align-items: center; gap: 10px; }
.selection-header { justify-content: space-between; flex-wrap: wrap; margin-bottom: 10px; }
.selection-actions { flex-wrap: wrap; }
.selection-limit { font-size: 12px; font-weight: normal; color: var(--el-text-color-secondary); }
.selected-file { padding: 10px 0; }
.selected-file-name { min-width: 0; flex: 1; overflow-wrap: anywhere; }
.selected-file .el-tag, .selected-file .el-button { flex-shrink: 0; }
.certificate-options { display: flex; flex-direction: column; align-items: stretch; }
.certificate-options :deep(.el-checkbox) { height: auto; margin: 0; padding: 7px 0; align-items: flex-start; }
.certificate-options :deep(.el-checkbox__input) { margin-top: 3px; }
.certificate-options :deep(.el-checkbox__label) { white-space: normal; overflow-wrap: anywhere; min-width: 0; }
.material-empty, .material-format-note, .material-state { font-size: 13px; color: var(--el-text-color-secondary); line-height: 1.7; }
.material-format-note { margin: 16px 0 0; }
.material-state { padding: 16px 0; }
.material-file-input { display: none; }
</style>
