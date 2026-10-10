<template>
  <el-dialog v-model="visible" title="实习协议" width="680px" destroy-on-close append-to-body modal-class="global-modal"
    :show-close="!submitting" :close-on-click-modal="!submitting" :close-on-press-escape="!submitting">
    <div v-loading="loading" class="agreement-detail">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="实习单位">{{ detail.record.companyName }}</el-descriptions-item>
          <el-descriptions-item label="实习岗位">{{ detail.record.positionName || '--' }}</el-descriptions-item>
          <el-descriptions-item label="协议状态">{{ detail.versions.length ? '已提交' : '未提交' }}</el-descriptions-item>
        </el-descriptions>
        <template v-if="editing">
          <el-alert type="info" :closable="false" show-icon class="agreement-alert"
            title="请核对本次提交的完整文件清单。更正后将保留原版本，实习及申请状态不受影响。" />
          <InternshipAgreementUpload ref="uploader" v-model="files" :disabled="submitting" :company-key="detail.record.companyName" />
        </template>
        <template v-else>
          <div v-if="detail.versions.length" class="version-picker">
            <span>提交版本</span>
            <el-select v-model="selectedVersion" style="width: 230px">
              <el-option v-for="version in detail.versions" :key="version.version" :value="version.version"
                :label="`第 ${version.version} 版${version.version === detail.versions[0].version ? '（最新）' : ''}`" />
            </el-select>
          </div>
          <div v-if="currentVersion" class="version-meta">{{ currentVersion.companyName }} · {{ currentVersion.submittedAt }}</div>
          <div v-for="file in currentVersion?.files || []" :key="file.id" class="file-row">
            <div class="file-main"><el-icon><Document /></el-icon><strong>{{ file.fileName }}</strong></div>
            <div class="file-actions">
              <el-button link type="primary" @click="preview?.show(file)">预览</el-button>
              <el-button link type="primary" @click="download(file)">下载</el-button>
            </div>
          </div>
          <el-empty v-if="!detail.versions.length" description="暂无实习协议，可补交已盖章的协议" />
          <el-alert v-if="!detail.editable" type="info" :closable="false" class="agreement-alert"
            title="已撤回或已拒绝的记录可查看历史协议；请在重新提交原申请时修改协议。" />
        </template>
      </template>
      <el-result v-else-if="loadError" icon="warning" title="协议加载失败" :sub-title="loadError">
        <template #extra><el-button @click="load">重新加载</el-button></template>
      </el-result>
    </div>
    <template #footer>
      <template v-if="editing">
        <el-button :disabled="submitting" @click="cancelEdit">取消更正</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交协议</el-button>
      </template>
      <template v-else>
        <el-button @click="visible = false">关闭</el-button>
        <el-button v-if="detail?.editable" type="primary" @click="beginEdit">{{ detail.versions.length ? '更正协议' : '补交协议' }}</el-button>
      </template>
    </template>
    <InternshipAgreementPreview ref="preview" role="student" />
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import InternshipAgreementUpload from './InternshipAgreementUpload.vue'
import InternshipAgreementPreview from './InternshipAgreementPreview.vue'
import { getAgreementDetail, readAgreementFile, saveAgreementBlob, submitAgreement, toAgreementUploadFiles,
  type AgreementDetail, type AgreementFile, type AgreementSource, type AgreementUploadFile } from '@/api/internshipAgreements'

const props = defineProps<{ modelValue: boolean; sourceType: AgreementSource; sourceId: number }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; updated: [] }>()
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const loading = ref(false)
const submitting = ref(false)
const editing = ref(false)
const detail = ref<AgreementDetail | null>(null)
const files = ref<AgreementUploadFile[]>([])
const selectedVersion = ref(0)
const loadError = ref('')
const uploader = ref<InstanceType<typeof InternshipAgreementUpload> | null>(null)
const preview = ref<InstanceType<typeof InternshipAgreementPreview> | null>(null)
const currentVersion = computed(() => detail.value?.versions.find(version => version.version === selectedVersion.value))
let token = 0
const load = async () => {
  const requestToken = ++token
  loading.value = true
  loadError.value = ''
  detail.value = null
  editing.value = false
  try {
    const response: any = await getAgreementDetail('student', props.sourceType, props.sourceId)
    if (requestToken !== token || !visible.value) return
    if (response.code !== 200) throw new Error(response.message || '协议加载失败')
    detail.value = response.data
    selectedVersion.value = detail.value?.versions[0]?.version || 0
  } catch (error: any) {
    if (requestToken === token) loadError.value = error?.message || '协议加载失败'
  } finally {
    if (requestToken === token) loading.value = false
  }
}
const beginEdit = () => {
  files.value = toAgreementUploadFiles(detail.value?.versions[0]?.files || [])
  editing.value = true
}
const cancelEdit = () => { uploader.value?.reset(); editing.value = false }
const submit = async () => {
  if (!uploader.value?.validate() || !detail.value) return
  submitting.value = true
  try {
    const response: any = await submitAgreement(props.sourceType, props.sourceId, files.value.map(file => file.uploadId), detail.value.versions[0]?.version || 0)
    if (response.code !== 200) throw new Error(response.message || '协议提交失败')
    uploader.value.markCommitted()
    ElMessage.success('实习协议提交成功')
    await load()
    emit('updated')
  } catch (error: any) { ElMessage.error(error?.message || '协议提交失败') }
  finally { submitting.value = false }
}
const download = async (file: AgreementFile) => {
  if (!file.id) return
  try { saveAgreementBlob(await readAgreementFile('student', file.id), file.fileName) }
  catch (error: any) { ElMessage.error(error?.message || '文件下载失败') }
}
watch(() => [props.modelValue, props.sourceType, props.sourceId], () => {
  if (props.modelValue && props.sourceId) void load()
  else { token++; detail.value = null; editing.value = false }
}, { immediate: true })
</script>

<style scoped>
.agreement-detail { min-height: 160px; }
.agreement-alert { margin: 16px 0; }
.version-picker { display: flex; align-items: center; gap: 12px; margin: 20px 0 12px; color: #606266; }
.version-meta { margin-bottom: 12px; color: #909399; font-size: 13px; }
.file-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; border: 1px solid #ebeef5; border-radius: 8px; padding: 14px; margin-top: 10px; }
.file-main { display: flex; align-items: center; gap: 10px; min-width: 0; }
.file-main strong { overflow-wrap: anywhere; font-weight: 500; font-size: 14px; }
.file-actions { flex-shrink: 0; }
</style>
