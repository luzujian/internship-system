import { onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAgentStore } from './store'
import type { PageContext } from './types'

/** Pages publish IDs and explicit filters, never scraped DOM or unsaved form contents. */
export function useAgentPageContext(source: () => Partial<PageContext>) {
  const store = useAgentStore(); const route = useRoute(); const owner = Symbol('agent-page')
  const path = route.path
  watch(source, value => store.setPage(owner, { ...value, route: path }), { deep: true, immediate: true, flush: 'sync' })
  onBeforeUnmount(() => store.releasePage(owner))
}
