const SCROLLABLE_OVERFLOW = /(auto|scroll|overlay)/

function isVerticallyScrollable(element: Element): boolean {
  if (!(element instanceof HTMLElement)) return false
  return SCROLLABLE_OVERFLOW.test(getComputedStyle(element).overflowY)
    && element.scrollHeight > element.clientHeight + 1
}

export function forwardTableWheelToPage(event: Event): void {
  if (!(event instanceof WheelEvent) || event.deltaY === 0) return

  const target = event.target
  if (!(target instanceof Element)) return

  const table = target.closest<HTMLElement>('.el-table')
  const root = event.currentTarget
  if (!table || !(root instanceof Element) || !root.contains(table)) return

  let pageWrap: HTMLElement | null = null
  let current: Element | null = target

  while (current && current !== root) {
    if (current instanceof HTMLElement && current.classList.contains('el-scrollbar__wrap') && !table.contains(current)) {
      pageWrap = current
      break
    }
    if (isVerticallyScrollable(current)) return
    current = current.parentElement
  }

  if (!pageWrap || pageWrap.scrollHeight <= pageWrap.clientHeight + 1) return

  event.preventDefault()
  const distance = event.deltaY
    * (event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? pageWrap.clientHeight : 1)
  pageWrap.scrollBy({ top: distance, behavior: 'auto' })
}
