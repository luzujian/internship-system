import { getAccessToken } from '@/utils/request'
/**
 * 云语音识别 Provider；浏览器只负责显式采集音频，凭登录身份连接系统中转服务。
 * 录音必须由用户主动点击启动；打开表单不会自动开启麦克风（浏览器还需授予麦克风权限）。
 * 提供：实时识别文本（interim）、最终结果（final）、状态回调与显式停止入口。
 * 浏览器不支持时 createAsr 返回 null——调用方按 fail-closed 处理（不渲染麦克风入口）。
 */

export type AsrState = 'idle' | 'listening' | 'error'

export interface AsrHandlers {
  onInterim?: (text: string) => void
  onFinal?: (text: string) => void
  onState?: (state: AsrState, detail?: string) => void
}

export interface AsrSession {
  stop(): Promise<void> | void
  abort?(): void
}

interface SpeechRecognitionLike {
  lang: string
  continuous: boolean
  interimResults: boolean
  start(): void
  stop(): void
  abort?(): void
  onresult: ((event: any) => void) | null
  onerror: ((event: any) => void) | null
  onend: (() => void) | null
}

function recognitionCtor(): (new () => SpeechRecognitionLike) | null {
  const w = window as any
  return w.SpeechRecognition || w.webkitSpeechRecognition || null
}

export function asrSupported(): boolean {
  return Boolean(navigator.mediaDevices?.getUserMedia && (window.AudioContext || (window as any).webkitAudioContext) && window.AudioWorkletNode)
}

/** 创建一次识别会话；同一时刻只允许一个会话在监听。 */
export function createBrowserAsr(handlers: AsrHandlers): AsrSession | null {
  const Ctor = recognitionCtor()
  if (!Ctor) return null
  const recognition = new Ctor()
  recognition.lang = 'zh-CN'
  recognition.continuous = true
  recognition.interimResults = true

  let stopped = false
  let finalText = ''
  function release() {
    if (stopped) return
    stopped = true
    recognition.onresult = null
    recognition.onerror = null
    recognition.onend = null
    try { if (recognition.abort) recognition.abort(); else recognition.stop() } catch {}
  }

  recognition.onresult = (event: any) => {
    if (stopped) return
    let interim = ''
    for (let i = event.resultIndex; i < event.results.length; i++) {
      const chunk = event.results[i][0].transcript
      if (event.results[i].isFinal) finalText += chunk
      else interim += chunk
    }
    if (finalText) handlers.onFinal?.(finalText)
    handlers.onInterim?.(interim)
  }
  recognition.onerror = (event: any) => {
    if (stopped) return
    const code = event?.error || 'unknown'
    if (code === 'no-speech') return
    release()
    handlers.onState?.('error', asrErrorMessage(code))
  }
  recognition.onend = () => {
    if (stopped) return
    try { recognition.start() } catch {
      release()
      handlers.onState?.('idle')
    }
  }

  try {
    recognition.start()
    handlers.onState?.('listening')
  } catch {
    release()
    return null
  }

  return {
    stop() {
      if (stopped) return
      release()
      handlers.onState?.('idle')
    }
  }
}

function asrErrorMessage(code: string): string {
  const messages: Record<string, string> = {
    'not-allowed': '浏览器未授予麦克风权限，请在地址栏允许后重试。',
    'service-not-allowed': '当前浏览器语音服务不可用，可改用文字输入。',
    network: '语音服务网络异常，可改用文字输入。',
    'audio-capture': '未检测到麦克风设备，可改用文字输入。'
  }
  return messages[code] || '语音识别暂时不可用，可改用文字输入。'
}

/** Cloud provider: microphone capture is explicit; finishing never sends a chat message. */
export function createAsr(handlers: AsrHandlers): AsrSession | null {
  if (!asrSupported()) return null
  const Context = window.AudioContext || (window as any).webkitAudioContext
  let context: AudioContext
  try { context = new Context() } catch { return null }
  void context.resume().catch(() => {})
  const controller = new AbortController()
  let stream: MediaStream | null = null
  let source: MediaStreamAudioSourceNode | null = null
  let processor: AudioWorkletNode | null = null
  let socket: WebSocket | null = null
  let closed = false, finishing = false, ready = false, finishSent = false, contextClosed = false
  let dictation = false
  const stoppedTracks = new WeakSet<MediaStreamTrack>()
  const flushRequestId = 1
  let finalText = ''
  const completed = new Set<string>()
  let settle: (() => void) | null = null
  let finishPromise: Promise<void> | null = null
  let finishTimer: ReturnType<typeof setTimeout> | undefined
  let flushTimer: ReturnType<typeof setTimeout> | undefined
  const limitTimer = setTimeout(() => { void stop() }, 120_000)
  const startTimer = setTimeout(() => fail('语音连接超时，可继续使用文字输入。'), 15_000)

  function stopTracks() {
    stream?.getTracks().forEach(track => {
      if (!stoppedTracks.has(track)) { stoppedTracks.add(track); track.stop() }
    })
  }
  function stopCapture() {
    stopTracks()
    if (processor) { processor.port.onmessage = null; processor.disconnect() }
    source?.disconnect()
    if (!contextClosed) { contextClosed = true; void context.close().catch(() => {}) }
  }
  function release() {
    if (closed) return
    closed = true; controller.abort(); stopCapture()
    clearTimeout(limitTimer); clearTimeout(startTimer); clearTimeout(finishTimer); clearTimeout(flushTimer)
    if (socket) { socket.onmessage = null; socket.onclose = null; socket.onerror = null; socket.close() }
    settle?.()
  }
  function fail(message: string) {
    if (closed) return
    release(); handlers.onState?.('error', message)
  }
  function finish() {
    if (closed) return
    release(); handlers.onInterim?.(''); handlers.onState?.('idle')
  }
  function sendFinish() {
    if (closed || finishSent) return
    finishSent = true
    clearTimeout(flushTimer)
    stopCapture()
    if (socket?.readyState !== WebSocket.OPEN || !ready) {
      fail('语音连接已断开，已保留识别文字。'); return
    }
    try {
      socket.send(JSON.stringify({ type: 'finish' }))
      finishTimer = setTimeout(() => fail('等待最终识别结果超时，已保留文字，请核对后继续。'), dictation ? 65_000 : 6500)
    } catch { fail('语音连接已断开，已保留识别文字。') }
  }
  function stop(): Promise<void> {
    if (finishPromise) return finishPromise
    if (closed) return Promise.resolve()
    finishing = true
    finishPromise = new Promise(resolve => { settle = resolve })
    // Release the microphone immediately, but let the worklet deliver its queued tail before finish.
    stopTracks()
    clearTimeout(limitTimer)
    if (socket?.readyState === WebSocket.OPEN && ready && processor) {
      flushTimer = setTimeout(() => fail('录音收尾超时，已保留识别文字，请核对最后一句。'), 1000)
      try { processor.port.postMessage({ type: 'flush', requestId: flushRequestId }) }
      catch { fail('录音收尾失败，已保留识别文字，请核对最后一句。') }
    } else { finish() }
    return finishPromise
  }

  // Start both permission prompt and AudioContext within the user's click gesture.
  const microphone = navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true }, video: false })
  void (async () => {
    try {
      stream = await microphone
      if (closed || finishing) { stopCapture(); return }
      const response = await fetch('/api/agent/voice/sessions', { method: 'POST', headers: { Authorization: `Bearer ${getAccessToken() || ''}` }, signal: controller.signal })
      const result = await response.json()
      if (!response.ok || result.code !== 200) throw new Error(result.message || '语音服务暂时不可用，可继续文字输入。')
      if (closed || finishing) return
      await context.audioWorklet.addModule('/agent-pcm-worklet.js')
      if (closed || finishing) return
      const url = new URL(result.data.url, window.location.href)
      if (url.origin !== window.location.origin) throw new Error('语音连接地址无效。')
      url.protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      socket = new WebSocket(url)
      socket.onmessage = event => {
        if (closed) return
        try {
          const data = JSON.parse(event.data)
          if (data.type === 'ready' && !finishing && !ready) {
            dictation = data.mode === 'dictation'
            ready = true; clearTimeout(startTimer)
            source = context.createMediaStreamSource(stream!)
            processor = new AudioWorkletNode(context, 'agent-pcm')
            processor.port.onmessage = event => {
              if (closed || finishSent) return
              if (event.data?.type === 'flushed' && finishing && event.data.requestId === flushRequestId) {
                sendFinish(); return
              }
              if (!(event.data instanceof ArrayBuffer)) return
              if (socket?.readyState !== WebSocket.OPEN) { fail('语音连接已断开，已保留识别文字。'); return }
              if (socket.bufferedAmount > 128_000) { fail('语音网络较慢，已停止录音并保留文字。'); return }
              const bytes = new Uint8Array(event.data)
              let binary = ''; for (const value of bytes) binary += String.fromCharCode(value)
              try { socket.send(JSON.stringify({ type: 'audio', audio: btoa(binary) })) }
              catch { fail('语音连接已断开，已保留识别文字。') }
            }
            source.connect(processor); processor.connect(context.destination)
            handlers.onState?.('listening')
          } else if (data.type === 'interim') {
            handlers.onInterim?.(String(data.text || ''))
          } else if (data.type === 'final') {
            const id = String(data.id || '')
            if (!id || !completed.has(id)) {
              if (id) completed.add(id)
              finalText += String(data.text || '')
              handlers.onFinal?.(finalText); handlers.onInterim?.('')
            }
          } else if (data.type === 'finished') finish()
          else if (data.type === 'error') fail(String(data.message || '语音识别暂时不可用。'))
        } catch { fail('语音识别返回格式异常。') }
      }
      socket.onerror = () => fail('语音连接失败，可继续文字输入。')
      socket.onclose = () => fail('语音连接已断开，已保留识别文字，请核对最后一句。')
    } catch (error: any) {
      if (!closed) fail(error?.name === 'NotAllowedError' ? asrErrorMessage('not-allowed')
        : error?.name === 'NotFoundError' ? asrErrorMessage('audio-capture') : error?.message || '语音识别暂时不可用。')
    }
  })()
  return { stop, abort() { release() } }
}
