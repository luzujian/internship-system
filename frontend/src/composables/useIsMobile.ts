/**
 * 轻量移动端断点检测（默认 768px）
 *
 * 供页面组件使用（卡片/表格切换、描述列表列数等）。
 * 布局侧边栏请使用 useResponsiveSidebar()。
 */
import { ref, onMounted, onUnmounted } from 'vue'

export function useIsMobile(breakpoint = 768) {
  const query = `(max-width: ${breakpoint}px)`

  const isMobile = ref(
    typeof window !== 'undefined' && typeof window.matchMedia === 'function'
      ? window.matchMedia(query).matches
      : false
  )

  let mediaQueryList: MediaQueryList | null = null
  const handleChange = (event: MediaQueryListEvent | MediaQueryList) => {
    isMobile.value = event.matches
  }

  onMounted(() => {
    mediaQueryList = window.matchMedia(query)
    handleChange(mediaQueryList)
    if (typeof mediaQueryList.addEventListener === 'function') {
      mediaQueryList.addEventListener('change', handleChange)
    } else {
      mediaQueryList.addListener(handleChange)
    }
  })

  onUnmounted(() => {
    if (!mediaQueryList) return
    if (typeof mediaQueryList.removeEventListener === 'function') {
      mediaQueryList.removeEventListener('change', handleChange)
    } else {
      mediaQueryList.removeListener(handleChange)
    }
  })

  return isMobile
}
