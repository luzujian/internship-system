<template>
  <AnnouncementDetailDialog
    :model-value="modelValue"
    :announcement="announcement"
    @update:model-value="emit('update:modelValue', $event)"
  />
</template>

<script setup lang="ts">
import AnnouncementDetailDialog from '@/components/AnnouncementDetailDialog.vue'
import type { AnnouncementMessage } from '@/types/announcement'

defineProps<{
  modelValue: boolean
  announcement: AnnouncementMessage | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()
</script>
