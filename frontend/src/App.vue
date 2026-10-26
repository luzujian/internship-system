<template>
  <div class="app-container" @wheel.capture="forwardTableWheelToPage">
    <!-- 路由出口 -->
    <router-view />
  </div>
</template>

<script lang="ts" setup>
import { forwardTableWheelToPage } from '@/utils/tableWheel'
import { useMobileViewport } from '@/composables/useMobileViewport'
import { useSessionTimeout } from '@/composables/useSessionTimeout'

useMobileViewport()
useSessionTimeout()
</script>

<style>
/* 全局样式重置 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: var(--font-family-base);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  background-color: #f5f5f5;
  font-size: 16px;
  font-weight: 400;
  line-height: 1.6;
}

#app {
  height: 100vh;
}

.app-container {
  height: 100%;
}
</style>
