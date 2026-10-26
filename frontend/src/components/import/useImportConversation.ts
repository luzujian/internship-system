import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import request from '@/utils/request'
import { showInitialCredentials } from '@/utils/initialCredentialDialog'
import type { useAgentStore } from '@/agent/store'
import type { useAuthStore } from '@/store/auth'
import type { MessageAttachment, PageContext } from '@/agent/types'

/** Attach files to the owned server session; all conversation runs through the shared Agent task stream. */
export function useImportConversation(agent: ReturnType<typeof useAgentStore>, auth: ReturnType<typeof useAuthStore>) {
  const input = ref<HTMLInputElement>(), files = ref<File[]>([]), snapshot = ref<any>(null), working = ref(false)
  const uploadedFiles = ref<MessageAttachment[]>([])
  let epoch = 0, controller: AbortController | null = null
  const key = () => `deepintern.agent.import:${agent.capabilities?.actor.ownerKey}:${agent.sessionId || 'new'}`
  const current = () => ({ token: auth.token, owner: agent.capabilities?.actor.ownerKey, session: agent.sessionId, epoch })
  const valid = (mine: ReturnType<typeof current>) => mine.epoch === epoch && mine.token === auth.token && mine.owner === agent.capabilities?.actor.ownerKey && mine.session === agent.sessionId
  const active = computed(() => files.value.length > 0 || Boolean(snapshot.value?.contextId))
  const attachedNames = computed<string[]>(() => files.value.length ? files.value.map(file => file.name) : snapshot.value?.files || [])
  const draftAttachments = computed<MessageAttachment[]>(() => {
    if (files.value.length) return files.value.map(file => ({ name: file.name, size: file.size }))
    if (!snapshot.value?.contextId || agent.tasks.some(task => task.attachmentContextId === snapshot.value.contextId)) return []
    return uploadedFiles.value.length ? uploadedFiles.value : (snapshot.value.files || []).map((name: string) => ({ name }))
  })
  async function send(message: string, context: PageContext) {
    if (working.value || agent.busy || !auth.token || agent.capabilities?.actor.role !== 'ROLE_ADMIN' || agent.capabilities.actor.readOnly) return
    if (active.value && agent.capabilities.attachmentImport !== true) { agent.error = '当前助手未开通附件导入，请保留文件并稍后重试。'; if (!agent.draft) agent.draft = message; return }
    const mine = current(), attachments = draftAttachments.value.map(file => ({ ...file }))
    working.value = true; agent.error = ''; controller = new AbortController()
    try {
      if (files.value.length) {
        const data = new FormData(); files.value.forEach(file => data.append('files', file))
        if (mine.session) data.append('sessionId', mine.session)
        const attached: any = await request.post('/admin/smart-import/conversation/attachments', data, { headers: { 'Content-Type': 'multipart/form-data' }, signal: controller.signal })
        if (!valid(mine)) return
        snapshot.value = attached.data; uploadedFiles.value = attachments; sessionStorage.setItem(key(), attached.data.contextId); files.value = []
      }
      if (!valid(mine)) return
      working.value = false; controller = null
      await agent.ask(context, message, attachments.length && snapshot.value?.contextId
        ? { contextId: snapshot.value.contextId, files: attachments } : undefined)
    } catch (error: any) {
      if (!valid(mine)) return
      agent.error = error.response?.data?.message || error.message || '附件上传未完成，文件和草稿仍保留。'
      if (!agent.draft) agent.draft = message
    } finally { if (valid(mine)) { working.value = false; controller = null } }
  }
  function choose(event: Event) {
    const selected = [...((event.target as HTMLInputElement).files || [])]
    if (!selected.length || agent.busy || working.value) return
    if (selected.length > 5 || selected.some(file => !/\.xlsx?$/i.test(file.name)) || selected.reduce((sum, file) => sum + file.size, 0) > 10 * 1024 * 1024) {
      agent.error = '请选择1至5个 .xlsx / .xls 文件，总大小不超过10MB。'
    } else files.value = selected
    if (input.value) input.value.value = ''
  }
  async function remove() {
    if (working.value || agent.busy) return
    const mine = current(); working.value = true
    try {
      if (snapshot.value?.contextId) await request.delete('/admin/smart-import/conversation/attachments', { params: { sessionId: mine.session } })
      if (valid(mine)) { files.value = []; uploadedFiles.value = []; snapshot.value = null; sessionStorage.removeItem(key()) }
    } catch (error: any) { if (valid(mine)) agent.error = error.response?.data?.message || '附件移除未完成，请稍后重试。' }
    finally { if (valid(mine)) working.value = false }
  }
  async function credentials(actionId: string) {
    const mine = current()
    try {
      const response: any = await request.get(`/agent/actions/${actionId}`)
      if (valid(mine) && response.data?.status === 'SUCCEEDED') await showInitialCredentials(response.data.receipt?.initialCredentials || [])
    } catch (error: any) { if (valid(mine)) agent.error = error.response?.data?.message || '初始凭据暂时无法读取。' }
  }
  function reset() { epoch++; controller?.abort(); controller = null; files.value = []; uploadedFiles.value = []; snapshot.value = null; working.value = false }
  async function restore() {
    if (agent.capabilities?.actor.role !== 'ROLE_ADMIN' || !auth.token || !agent.sessionId || snapshot.value || files.value.length) return
    const session = agent.sessions.find(item => item.id === agent.sessionId)
    // Server session metadata is authoritative, including a detach performed in another browser.
    const id = session ? session.attachmentContextId : sessionStorage.getItem(key()) || [...agent.tasks].reverse().find(task => task.attachmentContextId)?.attachmentContextId
    if (!id) { sessionStorage.removeItem(key()); return }
    const mine = current()
    try {
      const response: any = await request.get(`/admin/smart-import/conversation/${id}`, { params: { sessionId: mine.session } })
      if (valid(mine) && !files.value.length) { snapshot.value = response.data; sessionStorage.setItem(key(), id) }
    } catch { if (valid(mine)) { snapshot.value = null; sessionStorage.removeItem(key()) } }
  }
  watch([() => auth.token, () => agent.capabilities?.actor.ownerKey], () => { reset(); void restore() }, { flush: 'sync' })
  watch(() => agent.sessionId, (id, previous) => { if (previous && previous !== id) reset(); void restore() }, { flush: 'sync', immediate: true })
  watch([() => agent.sessions.length, () => agent.tasks.length], () => void restore())
  onBeforeUnmount(reset)
  return reactive({ attachedNames, draftAttachments, working, snapshot, active, send, choose, remove, credentials,
    stop: () => controller?.abort(), bindInput: (el: any) => { input.value = el as HTMLInputElement },
    pickFiles: () => input.value?.click(), canHandle: () => active.value })
}
