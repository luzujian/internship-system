import request from '@/utils/request'

const base = '/teacher/auto-tasks/v2'
export const automationApi = {
  catalog: () => request.get(`${base}/catalog`),
  tasks: () => request.get(`${base}/tasks`),
  /**
   * 草稿预览。撞到 409 版本冲突时，自动取最新版本号重试一次。
   * 预览只写草稿、没有业务副作用（真正发信/建任务在 confirm），所以重试是安全的；
   * 重试仍失败就照实抛出，由调用方在任务卡片上显示原因。
   */
  draft: async (body: any, signal?: AbortSignal) => {
    const send = (revision?: number) => request.post(
      `${base}/drafts/preview`,
      revision === undefined ? body : { ...body, revision },
      { signal, skipErrorMessage: true } as any
    )
    try {
      return await send()
    } catch (e: any) {
      if (signal?.aborted || e?.response?.status !== 409 || !body?.draftId) throw e
      const fresh: any = await request.get(`${base}/drafts`, {
        params: { sessionId: body.sessionId || 'manager' },
        skipErrorMessage: true
      } as any)
      const latest = (fresh?.data || []).find((row: any) => row.id === body.draftId)
      if (!latest || typeof latest.revision !== 'number') throw e
      return await send(latest.revision)
    }
  },
  drafts: (sessionId: string) => request.get(`${base}/drafts`, { params: { sessionId } }),
  preview: (body: any) => request.post(`${base}/preview`, body),
  confirm: (actionId: string) => request.post(`${base}/confirm`, { actionId, confirmed: true }),
  pause: (id: number) => request.post(`${base}/tasks/${id}/pause`),
  remove: (id: number) => request.delete(`${base}/tasks/${id}`),
  runs: (id: number, offset = 0) => request.get(`${base}/tasks/${id}/runs`, { params: { offset } }),
  deliveries: (id: number, offset = 0) => request.get(`${base}/tasks/${id}/deliveries`, { params: { offset } }),
  matches: (definition: any, offset = 0) => request.post(`${base}/matches`, { definition, offset }),
}
