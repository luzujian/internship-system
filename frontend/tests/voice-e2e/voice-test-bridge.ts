import type { Page } from '@playwright/test'

export type VoiceAudioMap = Record<string, string>

/**
 * 只给真实 SpeechRecognition 提供一条真实 MediaStreamTrack。
 * 不替换 onresult/onerror/onend，不注入任何转写文本。
 * 音频只连接 MediaStreamAudioDestinationNode，不连接扬声器输出。
 */
export async function installVoiceTestBridge(page: Page, audioUrls: VoiceAudioMap) {
  await page.addInitScript((urls: VoiceAudioMap) => {
    const w = window as any
    const Native = w.SpeechRecognition || w.webkitSpeechRecognition
    const api: any = {
      audioUrls: urls,
      audioKey: null,
      audioEnded: false,
      audioEndedByCleanup: false,
      audioStartedAt: 0,
      audioEndedAt: 0,
      trackLabel: '',
      error: ''
    }
    w.__voiceE2E = api
    if (!Native) {
      api.error = 'SpeechRecognition API unavailable'
      return
    }

    class TestSpeechRecognition extends Native {
      __testVoiceStarted = false
      __testTrack: MediaStreamTrack | null = null

      start(...args: any[]) {
        if (args.length) return super.start(...args)
        const url = api.audioKey ? api.audioUrls[api.audioKey] : ''
        if (!url) throw new Error('VOICE_TEST_AUDIO_NOT_SET')
        if (this.__testVoiceStarted && this.__testTrack) return super.start(this.__testTrack)
        this.__testVoiceStarted = true

        void (async () => {
          try {
            const response = await fetch(url)
            if (!response.ok) throw new Error(`audio fetch failed: ${response.status}`)
            const data = await response.arrayBuffer()
            const context = new AudioContext({ sampleRate: 48000 })
            await context.resume()
            const buffer = await context.decodeAudioData(data)
            const destination = context.createMediaStreamDestination()
            const source = context.createBufferSource()
            source.buffer = buffer
            // 关键：不连接 context.destination，测试音频不会外放。
            source.connect(destination)
            const track = destination.stream.getAudioTracks()[0]
            if (!track) throw new Error('audio destination produced no track')
            this.__testTrack = track
            api.audioEnded = false
            api.audioEndedByCleanup = false
            api.audioStartedAt = 0
            api.audioEndedAt = 0
            api.trackLabel = track.label
            source.onended = () => {
              api.audioEnded = true
              api.audioEndedAt = Date.now()
            }
            this.addEventListener('start', () => {
              api.audioStartedAt = Date.now()
              try { source.start() } catch (error) { api.error = String(error) }
            }, { once: true })
            this.addEventListener('end', () => {
              if (!api.audioEnded) api.audioEndedByCleanup = true
              try { source.stop() } catch { /* already ended */ }
              void context.close()
            }, { once: true })
            super.start(track)
          } catch (error) {
            api.error = error instanceof Error ? error.message : String(error)
            try { this.onerror?.({ error: 'audio-capture', message: api.error }) } catch { /* app keeps its normal error UI */ }
          }
        })()
      }
    }

    w.SpeechRecognition = TestSpeechRecognition
    w.webkitSpeechRecognition = TestSpeechRecognition
  }, audioUrls)
}
