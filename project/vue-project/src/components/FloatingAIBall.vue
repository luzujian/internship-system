<script setup>
</script>

<template>
  <div class="floating-ai-ball">
    AI
  </div>
</template>

<style scoped>
.floating-ai-ball {
  position: fixed;
  bottom: 100px;
  right: 50px;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  transition: transform 0.3s ease;
}

.floating-ai-ball:hover {
  transform: scale(1.1);
}
</style>
