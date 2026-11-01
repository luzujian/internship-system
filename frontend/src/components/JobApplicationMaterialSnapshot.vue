<template>
  <div class="application-material-snapshot" :aria-busy="loading">
    <p v-if="loading" role="status">正在加载本次申请材料…</p>
    <div v-else-if="error" role="alert" class="snapshot-error">
      <span>{{ error }}</span>
      <el-button type="primary" link @click="load">重新加载</el-button>
    </div>
    <el-alert v-else-if="snapshot?.accessRestricted" title="该申请当前状态不支持企业查看材料。" type="info" :closable="false" />
    <el-alert v-else-if="snapshot && !snapshot.snapshotAvailable" title="历史申请未保存投递时的材料，无法还原当时版本。" type="warning" :closable="false" />
    <template v-else-if="snapshot">
      <p class="snapshot-note">以下为本次申请提交时保存的材料，不随个人中心材料更新。</p>
      <section v-for="group in groups" :key="group.type" class="snapshot-group" :aria-label="group.title">
        <h4>{{ group.title }} <span>{{ group.files.length }}份</span></h4>
        <div v-for="file in group.files" :key="file.id" class="snapshot-file">
          <span class="snapshot-name">{{ file.fileName }}</span>
          <div class="snapshot-actions">
            <el-button type="primary" link @click="preview(file)">预览</el-button>
            <el-button type="primary" link :loading="downloading === file.id" :disabled="downloading !== null" @click="download(file)">下载</el-button>
          </div>
        </div>
      </section>
      <el-empty v-if="!snapshot.files.length" description="本次申请提交时未附简历或证书" :image-size="60" />
    </template>
    <FilePreviewDialog v-model="previewVisible" file-url="" :file-name="previewName" :authorized-url="previewUrl" />
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import FilePreviewDialog from '@/components/FilePreviewDialog.vue'
import { getApplicationMaterialSnapshot, type ApplicationMaterialFile, type ApplicationMaterialScope,
  type ApplicationMaterialSnapshot } from '@/api/jobApplicationMaterials'
import { jobApplicationMaterialByteUrl } from '@/utils/authorizedFileUrl'
import { downloadAuthorizedFile } from '@/utils/authorizedFileDownload'

const props = defineProps<{ applicationId: number | string; scope: ApplicationMaterialScope }>()
const snapshot = ref<ApplicationMaterialSnapshot | null>(null)
const loading = ref(false)
const error = ref('')
const downloading = ref<number | null>(null)
const previewVisible = ref(false)
const previewName = ref('')
const previewUrl = ref('')
let requestVersion = 0
const groups = computed(() => [
  { type: 'resume', title: '简历', files: snapshot.value?.files.filter(file => file.fileType === 'resume') || [] },
  { type: 'certificate', title: '证书', files: snapshot.value?.files.filter(file => file.fileType === 'certificate') || [] }
].filter(group => group.files.length))
const load = async () => {
  const version = ++requestVersion
  snapshot.value = null
  loading.value = true
  error.value = ''
  previewVisible.value = false
  try {
    const response = await getApplicationMaterialSnapshot(props.applicationId, props.scope)
    if (version !== requestVersion) return
    if (!response.data || !Array.isArray(response.data.files)) throw new Error('材料列表返回异常')
    snapshot.value = response.data
  } catch {
    if (version === requestVersion) error.value = '申请材料加载失败，请重试。'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}
const preview = (file: ApplicationMaterialFile) => {
  previewUrl.value = jobApplicationMaterialByteUrl(props.applicationId, file.id, props.scope)
  previewName.value = file.fileName
  previewVisible.value = true
}
const download = async (file: ApplicationMaterialFile) => {
  if (downloading.value !== null) return
  downloading.value = file.id
  try {
    await downloadAuthorizedFile(jobApplicationMaterialByteUrl(props.applicationId, file.id, props.scope), file.fileName)
  } catch {
    ElMessage.error('申请材料下载失败，请重试')
  } finally {
    downloading.value = null
  }
}
watch(() => [props.applicationId, props.scope], load, { immediate: true })
onUnmounted(() => { requestVersion++ })
</script>

<style scoped>
.snapshot-note { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.7; margin: 0 0 14px; }
.snapshot-group { border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 12px; margin-bottom: 12px; }
.snapshot-group h4 { margin: 0 0 8px; font-size: 14px; }
.snapshot-group h4 span { margin-left: 8px; font-weight: normal; color: var(--el-text-color-secondary); font-size: 12px; }
.snapshot-file { display: flex; align-items: center; gap: 12px; padding: 8px 0; }
.snapshot-name { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.snapshot-actions { display: flex; flex-shrink: 0; }
.snapshot-error { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
@media (max-width: 480px) { .snapshot-file { flex-wrap: wrap; } .snapshot-name { flex-basis: 100%; } }
</style>
