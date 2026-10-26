import { onMounted, onUnmounted } from 'vue'

/** Keep fixed mobile layouts inside the visible area when browser chrome or the keyboard moves. */
export function useMobileViewport() {
  let frame = 0
  const update = () => {
    cancelAnimationFrame(frame)
    frame = requestAnimationFrame(() => {
      const viewport = window.visualViewport
      if (viewport && viewport.scale !== 1) return
      document.documentElement.style.setProperty('--app-height', `${viewport?.height ?? window.innerHeight}px`)
    })
  }
  onMounted(() => {
    update()
    window.addEventListener('resize', update)
    window.visualViewport?.addEventListener('resize', update)
  })
  onUnmounted(() => {
    cancelAnimationFrame(frame)
    window.removeEventListener('resize', update)
    window.visualViewport?.removeEventListener('resize', update)
    document.documentElement.style.removeProperty('--app-height')
  })
}
