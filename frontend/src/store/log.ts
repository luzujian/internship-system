import { defineStore } from 'pinia'

export const useLogStore = defineStore('log', {
  state: () => ({
    // 用于通知日志已被清除的标志
    logsCleared: false
  }),

  actions: {
    // 设置日志已被清除的标志
    setLogsCleared(cleared: boolean) {
      this.logsCleared = cleared
    },

    // 通知日志已被清除
    notifyLogsCleared() {
      this.setLogsCleared(true)
      // 短暂延迟后重置标志，以便下次清除操作可以再次触发
      setTimeout(() => {
        this.setLogsCleared(false)
      }, 100)
    }
  }
})
