import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useLogStore = defineStore('log', () => {
  const logs = ref([])

  return {
    logs
  }
})
