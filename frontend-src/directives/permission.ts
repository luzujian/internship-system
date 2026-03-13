// 权限指令
// 用于控制按钮和页面元素的显示/隐藏
import { useAuthStore } from '@/store/auth'
import type { Directive, DirectiveBinding } from 'vue'

export default {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const { value } = binding
    const authStore = useAuthStore()

    if (!value) {
      console.warn('[v-permission] 权限指令需要传入权限代码')
      return
    }

    if (!authStore.hasPermission(value)) {
      el.style.display = 'none'
    }
  },
  updated(el: HTMLElement, binding: DirectiveBinding) {
    const { value } = binding
    const authStore = useAuthStore()

    if (!value) {
      console.warn('[v-permission] 权限指令需要传入权限代码')
      return
    }

    if (!authStore.hasPermission(value)) {
      el.style.display = 'none'
    } else {
      el.style.display = ''
    }
  }
} as Directive
