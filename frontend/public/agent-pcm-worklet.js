// Captures mono audio as bounded 100 ms, 16 kHz PCM packets. The output stays silent.
class AgentPcmProcessor extends AudioWorkletProcessor {
  constructor() {
    super()
    this.phase = 0; this.sum = 0; this.samples = 0
    this.packet = new ArrayBuffer(3200); this.view = new DataView(this.packet); this.index = 0
    this.stopped = false
    this.port.onmessage = event => {
      if (event.data?.type !== 'flush') return
      if (!this.stopped) {
        this.stopped = true
        // Keep the last partial resampling interval as well as the partial 100 ms packet.
        if (this.samples) this.append(this.sum / this.samples)
        this.sum = 0; this.samples = 0; this.phase = 0
        if (this.index) {
          const tail = this.packet.slice(0, this.index * 2)
          this.port.postMessage(tail, [tail]); this.index = 0
        }
      }
      // MessagePort ordering guarantees every queued audio packet precedes this acknowledgement.
      this.port.postMessage({ type: 'flushed', requestId: event.data.requestId })
    }
  }
  append(sample) {
    const value = Math.max(-1, Math.min(1, sample))
    this.view.setInt16(this.index * 2, value < 0 ? value * 32768 : value * 32767, true)
    this.index++
    if (this.index === 1600) {
      this.port.postMessage(this.packet, [this.packet])
      this.packet = new ArrayBuffer(3200); this.view = new DataView(this.packet); this.index = 0
    }
  }
  process(inputs) {
    const channel = inputs[0]?.[0]
    if (!channel || this.stopped) return true
    for (const sample of channel) {
      this.sum += sample; this.samples++; this.phase += 16000
      if (this.phase >= sampleRate) {
        const value = this.sum / this.samples
        do { this.phase -= sampleRate; this.append(value) } while (this.phase >= sampleRate)
        this.sum = 0; this.samples = 0
      }
    }
    return true
  }
}
registerProcessor('agent-pcm', AgentPcmProcessor)
