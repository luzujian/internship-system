import { onMounted, onBeforeUnmount } from 'vue'
export function useSmartImportRefresh(type: string, refresh: () => unknown) {
  const handle = (event: Event) => { if ((event as CustomEvent).detail?.type === type) void refresh() }
  onMounted(() => window.addEventListener('smart-import-completed', handle))
  onBeforeUnmount(() => window.removeEventListener('smart-import-completed', handle))
}
