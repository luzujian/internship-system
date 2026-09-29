import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import ts from 'typescript'

const source = readFileSync(new URL('../src/utils/pieChart.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText
const { getPiePath, getStatusPercentage } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)

test('100 percent draws a full circle using two semicircles', () => {
  assert.equal(getPiePath(0, 360), 'M150 30 A120 120 0 0 1 150 270 A120 120 0 0 1 150 30 Z')
  assert.equal((getPiePath(90, 450).match(/A120/g) || []).length, 2)
})

test('partial slices retain correct large-arc flags and empty slices draw nothing', () => {
  assert.match(getPiePath(0, 90), /A120 120 0 0 1/)
  assert.match(getPiePath(0, 270), /A120 120 0 1 1/)
  assert.equal(getPiePath(0, 0), '')
  assert.equal(getPiePath(0, NaN), '')
})

test('loading and empty datasets display zero, with percentages rounded to one decimal', () => {
  assert.equal(getStatusPercentage(0, 0), 0)
  assert.equal(getStatusPercentage(1, 3), 33.3)
  assert.equal(getStatusPercentage(6, 6), 100)
})
