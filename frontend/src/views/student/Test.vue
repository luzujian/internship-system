<template>
  <div class="test-container">
    <h1>测试页面</h1>
    <p>这是一个测试页面，用于验证开发服务器是否正常工作。</p>
    <p>当前时间：{{ currentTime }}</p>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';

const currentTime = ref('');

onMounted(() => {
  currentTime.value = new Date().toLocaleString();
});
</script>

<style scoped>
.test-container {
  padding: 24px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

h1 {
  color: #1e293b;
  margin-bottom: 16px;
}

p {
  color: #64748b;
  margin-bottom: 8px;
}
</style>