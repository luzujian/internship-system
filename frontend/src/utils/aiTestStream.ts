import { sseParser } from '../agent/protocol.mjs'

interface ChatStreamOptions {
  message: string
  model: string
  context: { role: string; content: string }[]
  token: string
  signal?: AbortSignal
  onChunk?: (text: string) => void
  timeoutMs?: number
}

/** A test succeeds only after the server's explicit end event and a nonempty answer. */
export async function readAIChatStream(options: ChatStreamOptions): Promise<string> {
  const controller = new AbortController()
  let timedOut = false
  const abort = () => controller.abort(options.signal?.reason)
  if (options.signal?.aborted) abort()
  else options.signal?.addEventListener('abort', abort, { once: true })
  const timer = setTimeout(() => { timedOut = true; controller.abort() }, options.timeoutMs ?? 60000)
  let reader: ReadableStreamDefaultReader<Uint8Array> | undefined
  let answer = '', ended = false
  try {
    const response = await fetch('/api/ai/chat/stream', {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${options.token}` },
      body: JSON.stringify({ message: options.message, model: options.model, context: options.context }), signal: controller.signal
    })
    if (!response.ok) throw new Error(`请求失败（HTTP ${response.status}），请检查模型配置或稍后重试`)
    if (!response.body) throw new Error('服务器未返回回答内容')
    const parse = sseParser((_event: string, data: any) => {
      if (ended) return
      if (data.type === 'error') throw new Error(data.message || '生成过程中发生错误')
      if (data.type === 'end') { ended = true; return }
      if (data.type === 'chunk' || data.type === 'job_recommendation') {
        answer += String(data.content ?? '')
        options.onChunk?.(answer)
      }
    })
    reader = response.body.getReader()
    const decoder = new TextDecoder()
    while (!ended) {
      const { done, value } = await reader.read()
      if (controller.signal.aborted) throw new DOMException('已停止测试', 'AbortError')
      if (done) { parse(decoder.decode()); break }
      parse(decoder.decode(value, { stream: true }))
    }
    if (!ended) throw new Error('连接中断，回答尚未完成，请重试')
    if (!answer.trim()) throw new Error('模型未返回有效回答，请检查配置后重试')
    return answer
  } catch (error) {
    if (timedOut) throw new Error('请求超时，请重试')
    throw error
  } finally {
    clearTimeout(timer)
    options.signal?.removeEventListener('abort', abort)
    if (reader) void reader.cancel().catch(() => {})
    controller.abort()
  }
}
