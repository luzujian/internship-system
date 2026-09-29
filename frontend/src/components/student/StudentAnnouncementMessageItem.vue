<template>
  <AnnouncementMessageItem :announcement="announcement" @select="emit('select', $event)" />
</template>

<script setup lang="ts">
import AnnouncementMessageItem from '@/components/AnnouncementMessageItem.vue'
import type { AnnouncementMessage } from '@/types/announcement'

defineProps<{
  announcement: AnnouncementMessage
}>()

const emit = defineEmits<{
  select: [announcement: AnnouncementMessage]
}>()
</script>
