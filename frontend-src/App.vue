<template>
  <div class="app-container">
    <!-- 路由出口 -->
    <router-view />
  </div>
</template>

<script lang="ts" setup>
// 根组件，主要用于路由出口
</script>

<style>
/* 全局样式重置 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif;
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
