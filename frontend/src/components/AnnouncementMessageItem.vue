<template>
  <div class="announcement-message-shell">
  <article
    class="announcement-message-item"
    :class="{ unread: isAnnouncementUnread(announcement) }"
    role="button"
    tabindex="0"
    :aria-label="`查看公告：${announcement.title || ''}`"
    @click="emit('select', announcement)"
    @keydown.enter.prevent="emit('select', announcement)"
    @keydown.space.prevent="emit('select', announcement)"
  >
    <time class="message-date" :datetime="dateTime.full || undefined">
      <span class="message-date-day">{{ dateTime.date || '-' }}</span>
      <span class="message-date-clock">{{ dateTime.time || '--:--:--' }}</span>
    </time>
    <div class="message-title">{{ announcement.title || '-' }}</div>
    <div class="publisher-info">
      <el-tag :type="getPublisherRoleTagType(announcement.publisherRole || announcement.publisher_role)" size="small">
        {{ publisherRoleText }}
      </el-tag>
      <span class="publisher-name" :title="publisherNameText">{{ publisherNameText }}</span>
    </div>
    <span v-if="isAnnouncementUnread(announcement)" class="unread-dot" aria-label="未读"></span>
  </article>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AnnouncementMessage } from '@/types/announcement'
import {
  formatAnnouncementTimeParts,
  getPublisherNameText,
  getPublisherRoleDisplay,
  getPublisherRoleTagType,
  isAnnouncementUnread
} from '@/utils/studentAnnouncement'

const props = defineProps<{
  announcement: AnnouncementMessage
}>()

const emit = defineEmits<{
  select: [announcement: AnnouncementMessage]
}>()

const dateTime = computed(() => formatAnnouncementTimeParts(
  props.announcement.publishDate || props.announcement.publishTime || props.announcement.time
))
const publisherRoleText = computed(() => getPublisherRoleDisplay(props.announcement))
const publisherNameText = computed(() => getPublisherNameText(props.announcement))
</script>

<style scoped>
.announcement-message-shell {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  container-type: inline-size;
  container-name: announcement-row;
}

.announcement-message-item {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  box-sizing: border-box;
  display: grid;
  grid-template-columns: 150px minmax(120px, 1fr) minmax(220px, 260px) 8px;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  overflow: hidden;
  background: #ffffff;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

.announcement-message-item:hover,
.announcement-message-item:focus-visible {
  border-color: #409eff;
  background: #f8fbff;
  box-shadow: 0 4px 14px rgba(64, 158, 255, 0.12);
  outline: none;
}

.announcement-message-item.unread {
  border-left: 4px solid #f56c6c;
}

.message-date {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  column-gap: 5px;
  row-gap: 2px;
  min-width: 0;
  color: #606266;
  font-size: 12px;
  line-height: 1.3;
  white-space: nowrap;
}

.message-date-day,
.message-date-clock {
  overflow: hidden;
  text-overflow: ellipsis;
}

.message-date-clock {
  color: #7b8798;
  font-variant-numeric: tabular-nums;
}

.message-title {
  min-width: 0;
  color: #303133;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.publisher-info {
  min-width: 0;
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: flex-end;
  gap: 6px 10px;
}

.publisher-name {
  min-width: 0;
  max-width: 100%;
  color: #606266;
  font-size: 13px;
  line-height: 1.35;
  text-align: right;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
}

@container announcement-row (max-width: 600px) {
  .announcement-message-item {
    grid-template-columns: minmax(0, 1fr) 8px;
    grid-template-areas:
      'time dot'
      'title title'
      'publisher publisher';
    gap: 7px 8px;
    padding: 10px;
  }

  .message-date {
    grid-area: time;
  }

  .message-title {
    grid-area: title;
  }

  .publisher-info {
    grid-area: publisher;
  }

  .unread-dot {
    grid-area: dot;
    align-self: start;
    margin-top: 4px;
  }

  .message-date,
  .publisher-name {
    font-size: 11px;
  }

  .message-title {
    font-size: 13px;
  }
}
</style>
