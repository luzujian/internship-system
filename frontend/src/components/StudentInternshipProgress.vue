<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'

const props = withDefaults(defineProps<{
  status: string
  statusClass?: string
  pendingApplicationTexts?: string[]
}>(), { statusClass: '', pendingApplicationTexts: () => [] })

const expanded = ref(false)
const applications = computed(() => [...new Set(props.pendingApplicationTexts)].map(text => ({
  key: text,
  name: text.startsWith('自主实习待') ? '自主实习申请' : text.replace(/待审核$/, ''),
  processingText: text.endsWith('待企业确认') ? '待企业确认' : '待辅导员审核'
})))
</script>

<template>
  <el-popover
    v-if="applications.length"
    v-model:visible="expanded"
    trigger="click"
    placement="bottom-end"
    :width="280"
    :popper-style="{ borderRadius: '14px', padding: '18px', maxWidth: 'calc(100vw - 32px)' }"
  >
    <template #reference>
      <button
        type="button"
        :class="['internship-progress-trigger', statusClass]"
        :aria-label="`${status}，另有${applications.length}项申请待处理，查看进度`"
        aria-haspopup="dialog"
        :aria-expanded="expanded"
        @keydown.esc.stop="expanded = false"
      >
        <span class="primary-status">{{ status }}</span>
        <span class="application-summary">
          <span class="application-count">{{ applications.length }}</span>
          <span class="application-caption">项申请</span>
        </span>
        <el-icon :class="['expand-icon', { expanded }]"><ArrowDown /></el-icon>
      </button>
    </template>

    <div class="progress-details" role="dialog" aria-label="实习与申请进度">
      <div class="current-progress">
        <span class="section-label">当前实习状态</span>
        <strong>{{ status }}</strong>
      </div>
      <div class="pending-progress">
        <div class="pending-heading">
          <span class="section-label">待处理申请</span>
          <span class="pending-total">{{ applications.length }}项</span>
        </div>
        <ul class="application-list">
          <li v-for="application in applications" :key="application.key">
            <span class="application-name">{{ application.name }}</span>
            <span class="review-label">{{ application.processingText }}</span>
          </li>
        </ul>
      </div>
    </div>
  </el-popover>
  <span v-else :class="['internship-status-only', statusClass]">{{ status }}</span>
</template>

<style scoped>
.internship-progress-trigger,
.internship-status-only {
  display: inline-flex;
  align-items: center;
  padding: 5px 12px;
  border: 1px solid rgba(255, 255, 255, 0.32);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
  white-space: nowrap;
}
.internship-progress-trigger { position: relative; gap: 9px; cursor: pointer; transition: background 0.18s ease; }
.internship-progress-trigger:hover { background: rgba(255, 255, 255, 0.27); }
.internship-progress-trigger:focus-visible { outline: 2px solid #fff; outline-offset: 3px; }
.internship-progress-trigger.ended,
.internship-progress-trigger.interrupted,
.internship-status-only.ended,
.internship-status-only.interrupted { background: rgba(255, 255, 255, 0.12); color: rgba(255, 255, 255, 0.8); }
.application-summary {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding-left: 9px;
  border-left: 1px solid rgba(255, 255, 255, 0.28);
  color: rgba(255, 255, 255, 0.85);
  font-size: 11px;
  font-weight: 400;
}
.application-count { font-weight: 600; }
.expand-icon { font-size: 11px; opacity: 0.8; transition: transform 0.18s ease; }
.expand-icon.expanded { transform: rotate(180deg); }
.progress-details { color: #303133; }
.current-progress { display: flex; flex-direction: column; gap: 8px; padding-bottom: 16px; }
.section-label { color: #909399; font-size: 12px; line-height: 18px; }
.current-progress strong { font-size: 16px; font-weight: 600; line-height: 24px; }
.pending-progress { padding-top: 14px; border-top: 1px solid #ebeef5; }
.pending-heading { display: flex; align-items: center; justify-content: space-between; }
.pending-total { color: #909399; font-size: 12px; }
.application-list { display: flex; flex-direction: column; gap: 8px; margin: 10px 0 0; padding: 0; list-style: none; }
.application-list li { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 12px; border-radius: 8px; background: #f8fafc; }
.application-name { font-size: 13px; line-height: 20px; }
.review-label { flex-shrink: 0; padding: 2px 7px; border-radius: 4px; background: #fff4e4; color: #b7791f; font-size: 11px; line-height: 18px; }
@media (max-width: 480px) {
  .internship-progress-trigger, .internship-status-only { padding: 4px 9px; font-size: 11px; }
  .internship-progress-trigger { gap: 5px; }
  .application-summary { position: absolute; top: -6px; right: -4px; padding: 0; border: 0; }
  .application-count { display: grid; place-items: center; width: 16px; height: 16px; border: 1px solid rgba(255, 255, 255, 0.8); border-radius: 50%; background: #fff4e4; color: #a86b17; font-size: 9px; line-height: 1; }
  .application-caption { display: none; }
}
</style>
