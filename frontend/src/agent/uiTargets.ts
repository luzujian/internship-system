import type { Router } from 'vue-router'
import { currentForm } from './formContext'

export interface UiTarget { id: string; label: string; kind: 'page' | 'open' | 'form'; path?: string; element?: HTMLElement; instanceId?: number }
const available = (el: HTMLElement) => el.isConnected && el.getClientRects().length > 0 && !el.closest('[inert], [aria-hidden="true"]') && !el.matches(':disabled, .is-disabled, [aria-disabled="true"]')

/** The menu is the permission-filtered source of truth; only explicitly marked openers may be clicked. */
export function collectUiTargets(router: Router): UiTarget[] {
  const root = '/' + router.currentRoute.value.path.split('/')[1]
  const targets: UiTarget[] = []
  const paths = new Set<string>()
  document.querySelectorAll<HTMLElement>('[data-agent-route]').forEach(el => {
    const path = el.dataset.agentRoute || ''
    // Collapsed submenus and the mobile sidebar are still navigable; v-if permission gates remove their entries entirely.
    if (!el.isConnected || el.matches('.is-disabled, [aria-disabled="true"]') || !(path === root || path.startsWith(root + '/')) || paths.has(path)) return
    const resolved = router.resolve(path)
    if (!resolved.matched.length) return
    paths.add(path)
    const forms = resolved.matched.flatMap(record => (record.components?.default as any)?.agentForms || [])
    const label = [el.textContent?.trim() || path, ...forms].join(' · ').slice(0, 200)
    targets.push({ id: `page-${targets.length}`, kind: 'page', label, path, element: el })
  })
  document.querySelectorAll<HTMLElement>('[data-agent-open], .agent-host [role="tab"]').forEach(el => {
    if (!available(el) || el.closest('.agent-workspace') || el.getAttribute('aria-selected') === 'true') return
    const label = (el.dataset.agentOpen || el.textContent || '').trim().slice(0, 200)
    if (label) targets.push({ id: `open-${targets.length}`, kind: 'open', label, element: el })
  })
  if (currentForm.value) targets.push({ id: 'current-form', kind: 'form', label: currentForm.value.title, instanceId: currentForm.value.instanceId })
  return targets
}

export function targetStillAvailable(target: UiTarget, router: Router) {
  return collectUiTargets(router).some(item => item.kind === target.kind && item.label === target.label
    && item.path === target.path && item.element === target.element && item.instanceId === target.instanceId)
}

/** Wait for the mounted page and its real controls, bounded and cancellable. */
export async function waitForUiTargets(router: Router, signal: AbortSignal) {
  const deadline = Date.now() + 5000
  let previous = '', stable = 0
  while (Date.now() < deadline) {
    if (signal.aborted) return
    const signature = JSON.stringify(collectUiTargets(router).map(({ kind, label, path, instanceId }) => ({ kind, label, path, instanceId })))
    const loading = [...document.querySelectorAll<HTMLElement>('.agent-host .el-loading-mask')].some(available)
    stable = !loading && signature === previous ? stable + 1 : 0
    if (stable >= 3) return
    previous = signature
    await new Promise(resolve => window.setTimeout(resolve, 100))
  }
  throw new Error('页面仍在加载，本次操作已停止，请加载完成后重试。')
}
