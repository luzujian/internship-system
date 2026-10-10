<template>
  <el-dialog v-model="visible" :title="`预览：${file?.fileName || ''}`" width="920px" destroy-on-close append-to-body modal-class="global-modal" @closed="cleanup">
    <div v-loading="loading" class="preview-body">
      <iframe v-if="mode === 'document' && url" :src="url" title="实习协议预览" class="preview-frame" />
      <div v-else-if="mode === 'docx'" ref="container" class="docx-container" />
      <el-result v-else-if="error" icon="warning" title="暂时无法在线预览" :sub-title="error">
        <template #extra><el-button type="primary" @click="download">下载原文件</el-button></template>
      </el-result>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { readAgreementFile, saveAgreementBlob, type AgreementFile, type AgreementRole } from '@/api/internshipAgreements'
const props = defineProps<{ role: AgreementRole }>()
const visible = ref(false)
const loading = ref(false)
const file = ref<AgreementFile | null>(null)
const mode = ref('')
const url = ref('')
const error = ref('')
const container = ref<HTMLElement | null>(null)
let token = 0
const cleanup = () => {
  token++
  if (url.value) URL.revokeObjectURL(url.value)
  url.value = ''
  mode.value = ''
  error.value = ''
  if (container.value) container.value.replaceChildren()
}
const show = async (target: AgreementFile) => {
  cleanup()
  file.value = target
  visible.value = true
  loading.value = true
  const requestToken = token
  try {
    if (!target.id) throw new Error('文件编号不存在')
    const blob = await readAgreementFile(props.role, target.id, true)
    if (requestToken !== token || !visible.value) return
    if (target.fileName.toLowerCase().endsWith('.docx')) {
      mode.value = 'docx'
      await nextTick()
      const { renderSafeDocx } = await import('@/utils/safeDocx')
      const staging = document.createElement('div')
      await renderSafeDocx(blob, staging, undefined, { inWrapper: true, ignoreLastRenderedPageBreak: true,
        renderHeaders: true, renderFooters: true, renderFootnotes: true })
      if (requestToken === token && visible.value && container.value) container.value.replaceChildren(...Array.from(staging.childNodes))
    } else {
      mode.value = 'document'
      url.value = URL.createObjectURL(blob)
    }
  } catch (cause: any) {
    if (requestToken === token) { mode.value = ''; error.value = cause?.message || '预览失败，请下载后查看' }
  } finally {
    if (requestToken === token) loading.value = false
  }
}
const download = async () => {
  if (!file.value?.id) return
  try { saveAgreementBlob(await readAgreementFile(props.role, file.value.id), file.value.fileName) }
  catch (cause: any) { ElMessage.error(cause?.message || '文件下载失败') }
}
onBeforeUnmount(cleanup)
defineExpose({ show })
</script>

<style scoped>
.preview-body { min-height: 300px; }
.preview-frame { width: 100%; height: 68vh; border: 0; }
.docx-container { min-height: 300px; max-height: 68vh; overflow: auto; background: #f5f7fa; }
</style>
