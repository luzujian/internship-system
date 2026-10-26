<script setup lang="ts">
import { useAgentStore } from '@/agent/store'
const agent = useAgentStore()
function toggleAgent() {
  if (agent.visible) agent.visible = false
  else {
    document.dispatchEvent(new Event('agent-opening'))
    agent.openWithPrompt()
  }
}
</script>
<template>
  <!-- fail-closed：AI 助手被管理员关闭（enabled=false）、对话问答未授权或能力尚未核验完成时不渲染入口，
       各角色端页面均不显示、不可使用；启用后随 capabilities 加载自动出现 -->
  <button v-if="agent.capabilities?.enabled && agent.chatEnabled" class="agent-entry" type="button" aria-label="问问 AI" aria-controls="agent-workspace" :aria-expanded="agent.visible" @click="toggleAgent">
    <span aria-hidden="true">✦</span><span>问问 AI</span><i v-if="agent.busy" aria-label="任务执行中" />
  </button>
</template>
<style scoped>
.agent-entry{margin-inline:16px 24px;flex-shrink:0;position:relative;z-index:1;display:inline-flex;align-items:center;justify-content:center;gap:7px;height:32px;box-sizing:border-box;align-self:center;padding:0 13px;border:1px solid #ffffff80;background:#ffffff1a;color:inherit;border-radius:20px;cursor:pointer;white-space:nowrap;font:inherit;font-size:13px}
.agent-entry:hover{background:#ffffff36}.agent-entry:focus-visible{outline:2px solid white;outline-offset:3px}.agent-entry i{width:6px;height:6px;background:#c7ffe6;border-radius:50%}
@media(max-width:600px){.agent-entry{margin-inline:8px 16px;padding:0 9px;font-size:12px;gap:4px}}
</style>
