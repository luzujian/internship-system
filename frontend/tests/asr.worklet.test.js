import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'

const source = readFileSync(new URL('../public/agent-pcm-worklet.js', import.meta.url), 'utf8')
function createProcessor(sampleRate) {
  const messages = []
  let Processor
  runInNewContext(source, {
    sampleRate, ArrayBuffer, DataView,
    AudioWorkletProcessor: class { port = { postMessage: message => messages.push(message) } },
    registerProcessor(name, type) { assert.equal(name, 'agent-pcm'); Processor = type }
  })
  const processor = new Processor()
  return { processor, messages, flush: () => processor.port.onmessage({ data: { type: 'flush', requestId: 1 } }) }
}
for (const sampleRate of [8000, 16000, 44100, 48000]) {
  test(`resamples ${sampleRate} Hz to one bounded 100 ms PCM packet, little endian`, () => {
    const { processor, messages, flush } = createProcessor(sampleRate)
    const input = new Float32Array(sampleRate / 10).fill(0.5)
    for (let start = 0; start < input.length; start += 128) processor.process([[input.slice(start, start + 128)]])
    flush()
    const packets = messages.filter(message => message instanceof ArrayBuffer)
    assert.equal(packets.length, 1)
    assert.equal(packets[0].byteLength, 3200)
    const view = new DataView(packets[0])
    for (let i = 0; i < 1600; i++) assert.equal(view.getInt16(i * 2, true), 16383)
    assert.equal(messages.at(-1).type, 'flushed')
    assert.equal(messages.at(-1).requestId, 1)
  })
}

test('flush sends short tail and last fractional sample before acknowledgement exactly once', () => {
  const { processor, messages, flush } = createProcessor(48000)
  processor.process([[new Float32Array(148).fill(-0.5)]])
  assert.equal(messages.length, 0)
  flush()
  assert.equal(messages[0].byteLength, 100, '49 complete samples plus one partial sample')
  assert.equal(new DataView(messages[0]).getInt16(98, true), -16384)
  assert.equal(messages[1].type, 'flushed')
  flush()
  processor.process([[new Float32Array(4800).fill(1)]])
  assert.equal(messages.filter(message => message instanceof ArrayBuffer).length, 1)
})

test('flush after an exact packet creates no empty audio packet', () => {
  const { processor, messages, flush } = createProcessor(16000)
  processor.process([[new Float32Array(1600)]])
  flush()
  assert.equal(messages.length, 2)
  assert.equal(messages[0].byteLength, 3200)
  assert.equal(messages[1].type, 'flushed')
})

test('PCM clips to signed 16-bit range and preserves silence', () => {
  const { processor, messages, flush } = createProcessor(16000)
  processor.process([[new Float32Array([2, -2, 0])]])
  flush()
  const view = new DataView(messages[0])
  assert.deepEqual([0, 2, 4].map(offset => view.getInt16(offset, true)), [32767, -32768, 0])
})
