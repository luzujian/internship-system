<script setup lang="ts">
import type { MessageAttachment } from '@/agent/types'

defineProps<{ files: MessageAttachment[]; uploading?: boolean }>()

function fileSize(size?: number) {
  if (size === undefined) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${Math.ceil(size / 1024)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}
</script>

<template>
  <ul v-if="files.length" class="message-attachments" aria-label="消息附件">
    <li v-for="(file, index) in files" :key="`${index}:${file.name}`" class="attachment-card" :title="file.name">
      <span class="attachment-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z"/><path d="M14 3v5h5M8 12h8M8 16h8M12 12v7"/></svg>
      </span>
      <span class="attachment-details"><strong>{{ file.name }}</strong><span>{{ uploading ? '正在上传…' : 'Excel 文件' }}<template v-if="file.size !== undefined"> · {{ fileSize(file.size) }}</template></span></span>
    </li>
  </ul>
</template>

<style scoped>
.message-attachments{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:7px;max-width:100%;margin:0;padding:0;list-style:none}
.attachment-card{display:flex;align-items:center;gap:9px;min-width:0;max-width:100%;width:230px;padding:10px;border:1px solid var(--ai-line,#e5e9ee);border-radius:11px;background:#fff;text-align:left}
.attachment-icon{display:grid;place-items:center;flex-shrink:0;width:34px;height:38px;border-radius:7px;color:#20845b;background:#edf7f1}
.attachment-details{display:flex;flex:1;flex-direction:column;gap:4px;min-width:0;line-height:1.35}
.attachment-details strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--ai-text,#28364b);font-size:12px;font-weight:550}
.attachment-details>span{color:var(--ai-sub,#7a8799);font-size:11px}
</style>
