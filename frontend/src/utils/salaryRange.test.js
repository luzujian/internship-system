import test from 'node:test'
import assert from 'node:assert/strict'

import { getMonthlySalaryRangeK, normalizeSalaryUnit } from './salaryRange.js'

test('falls back from corrupted historical salary units', () => {
  assert.equal(normalizeSalaryUnit('K/鏈�'), 'K/月')
  assert.equal(normalizeSalaryUnit('元/天'), '元/天')
})

test('keeps an open upper bound when only minimum salary exists', () => {
  assert.deepEqual(
    getMonthlySalaryRangeK({ salaryMin: 5, salaryMax: null, salaryUnit: 'K/月' }),
    { min: 5, max: Infinity }
  )
})

test('keeps an open lower bound when only maximum salary exists', () => {
  assert.deepEqual(
    getMonthlySalaryRangeK({ salaryMin: null, salaryMax: 8, salaryUnit: 'K/月' }),
    { min: 0, max: 8 }
  )
})

test('converts daily and hourly salary for filtering', () => {
  const daily = getMonthlySalaryRangeK({ salaryMin: 200, salaryMax: 300, salaryUnit: '元/天' })
  assert.ok(Math.abs(daily.min - 4.35) < 1e-9)
  assert.ok(Math.abs(daily.max - 6.525) < 1e-9)

  const hourly = getMonthlySalaryRangeK({ salaryMin: 25, salaryMax: 30, salaryUnit: '元/小时' })
  assert.ok(Math.abs(hourly.min - 4.35) < 1e-9)
  assert.ok(Math.abs(hourly.max - 5.22) < 1e-9)
})

test('negotiable or empty salary is excluded from numeric filters', () => {
  assert.equal(getMonthlySalaryRangeK({ salaryMin: 5, salaryMax: 8, salaryUnit: '面议' }), null)
  assert.equal(getMonthlySalaryRangeK({ salaryMin: null, salaryMax: null, salaryUnit: 'K/月' }), null)
})
