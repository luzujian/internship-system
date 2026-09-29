/**
 * 响应式侧边栏 composable
 *
 * 统一四个角色布局的侧边栏行为：
 * - 桌面端（> 768px）：默认展开
 * - 手机端（<= 768px）：默认关闭，打开后为遮罩抽屉
 * - 点击菜单、切换路由、按下 Escape 后自动关闭（仅手机端）
 *
 * 内部使用，配合各 Layout 组件的遮罩层一起工作。
 */
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'

const MOBILE_BREAKPOINT = 768
const MEDIA_QUERY = `(max-width: ${MOBILE_BREAKPOINT}px)`

export function useResponsiveSidebar() {
  const route = useRoute()

  // 同步初始化，避免首帧闪烁：桌面展开、手机收起
  const getInitialMobile = () =>
    typeof window !== 'undefined' && typeof window.matchMedia === 'function'
      ? window.matchMedia(MEDIA_QUERY).matches
      : false

  const isMobile = ref(getInitialMobile())
  const sidebarCollapsed = ref(isMobile.value)

  let mediaQueryList: MediaQueryList | null = null

  const handleMediaChange = (event: MediaQueryListEvent | MediaQueryList) => {
    isMobile.value = event.matches
    // 跨断点切换时恢复各端默认状态
    sidebarCollapsed.value = event.matches
  }

  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  const closeSidebar = () => {
    if (isMobile.value) {
      sidebarCollapsed.value = true
    }
  }

  const handleKeydown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      closeSidebar()
    }
  }

  // 切换路由后自动关闭手机端抽屉（菜单使用 router 模式时由这里兜底）
  watch(
    () => route.path,
    () => {
      closeSidebar()
    }
  )

  onMounted(() => {
    mediaQueryList = window.matchMedia(MEDIA_QUERY)
    // 挂载时再次校准（SSR/ hydration 场景下初始值可能为 false）
    handleMediaChange(mediaQueryList)
    if (typeof mediaQueryList.addEventListener === 'function') {
      mediaQueryList.addEventListener('change', handleMediaChange)
    } else {
      // 兼容旧版 Safari
      mediaQueryList.addListener(handleMediaChange)
    }
    window.addEventListener('keydown', handleKeydown)
  })

  onUnmounted(() => {
    if (mediaQueryList) {
      if (typeof mediaQueryList.removeEventListener === 'function') {
        mediaQueryList.removeEventListener('change', handleMediaChange)
      } else {
        mediaQueryList.removeListener(handleMediaChange)
      }
    }
    window.removeEventListener('keydown', handleKeydown)
  })

  return {
    isMobile,
    sidebarCollapsed,
    toggleSidebar,
    closeSidebar
  }
}
