import { reactive } from 'vue'

export const activityStatusText = (status) => ({
  pending: '待审核', approved: '已同意面试', interview: '面试中', pending_interview: '待面试',
  interview_passed: '已录取', interview_failed: '面试未通过', confirmed: '已确认',
  rejected: '已拒绝', withdrawn: '已撤回', cancelled: '已取消', canceled: '已取消', defaulted: '已失效'
}[status] || '状态待更新')

export const activityStatusType = (status) => ({
  pending: 'warning', approved: 'primary', interview: 'warning', pending_interview: 'warning',
  interview_passed: 'success', confirmed: 'success', rejected: 'danger', interview_failed: 'danger'
}[status] || 'info')

export const activityTitle = (activity) =>
  `${activity.studentName || '学生'} · ${activity.positionName || '岗位申请'}`

export async function readCompanyActivity(activity, saveRead) {
  if (activity.viewed) return
  await saveRead(activity)
  activity.viewed = true
}

export function activityTimeText(date, now = Date.now()) {
  if (!date) return '时间未知'
  const time = new Date(date)
  if (!Number.isFinite(time.getTime())) return '时间未知'
  const diff = Math.max(0, Math.floor((now - time.getTime()) / 1000))
  if (diff < 60) return '刚刚更新'
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前更新`
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前更新`
  if (diff < 604800) return `${Math.floor(diff / 86400)}天前更新`
  return time.toLocaleDateString('zh-CN')
}

// Ignore responses from an older filter/refresh, and keep existing rows when a request fails.
export function createCompanyActivityFeed(fetchPage) {
  const state = reactive({ items: [], unreadCount: 0, total: 0, hasMore: false,
    unreadOnly: false, loading: false, error: '' })
  let revision = 0
  let nextOffset = 0
  let retryAppend = false

  async function load(append = false) {
    if (append && (state.loading || !state.hasMore)) return
    const current = ++revision
    state.loading = true
    state.error = ''
    retryAppend = append
    const offset = append ? nextOffset : 0
    try {
      const page = await fetchPage({ offset, limit: 10, unreadOnly: state.unreadOnly })
      if (current !== revision) return
      const rows = append ? [...state.items, ...page.items] : page.items
      state.items = [...new Map(rows.map(row => [row.id, row])).values()]
      state.unreadCount = page.unreadCount
      state.total = page.total
      state.hasMore = page.hasMore
      nextOffset = offset + page.items.length
    } catch {
      if (current === revision) state.error = '动态加载失败，请重试'
    } finally {
      if (current === revision) state.loading = false
    }
  }

  return { state, refresh: () => load(), loadMore: () => load(true), retry: () => load(retryAppend),
    setUnreadOnly(value) {
      state.unreadOnly = value
      state.items = []
      state.hasMore = false
      return load()
    },
    dispose() { revision++ }
  }
}
