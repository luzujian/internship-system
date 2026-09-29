<script setup lang="ts">
import { useAgentStore } from '@/agent/store'
defineProps<{ prompt: string }>()
const agent = useAgentStore()
</script>
<template>
  <button v-if="agent.capabilities?.enabled" class="agent-context-action" type="button" @click="agent.openWithPrompt(agent.draft ? '' : prompt)">✦ AI 解读</button>
</template>
<style scoped>
.agent-context-action{font:inherit;font-size:12px;white-space:nowrap;color:#247dbd;background:#edf8ff;border:1px solid #b7d6e9;border-radius:7px;padding:6px 10px;cursor:pointer}.agent-context-action:hover{background:#dff2ff}.agent-context-action:focus-visible{outline:2px solid #2597d6;outline-offset:2px}
</style>
