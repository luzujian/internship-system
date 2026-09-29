import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(
  new URL('./InternshipConfirmationForm.vue', import.meta.url),
  'utf8'
)

test('internship confirmation presents current placement, offers and history', () => {
  assert.match(source, /<section class="current-card">[\s\S]*当前实习/)
  assert.match(source, /<section v-if="!pendingConfirmation" class="offer-card">[\s\S]*可确认的实习/)
  assert.match(source, /<section class="history-card">[\s\S]*确认记录/)
  assert.match(source, /\.current-card,[\s\S]*\.offer-card,[\s\S]*\.history-card,[\s\S]*\.pending-card\s*\{[\s\S]*border-radius:\s*14px/)
})

test('offer chooser and history use stable record identities', () => {
  assert.match(source, /v-for="offer in offers"[\s\S]*:key="offer\.offerId"/)
  assert.match(source, /String\(offer\.offerId\) === selectedOfferId/)
  assert.match(source, /<div v-if="selectedOffer" class="confirmation-box">/)
  assert.match(source, /v-for="record in history"[\s\S]*:data-confirmation-id="record\.id"/)
  assert.match(source, /\.timeline-item\.highlighted \.timeline-content/)
})

test('confirmation layout remains responsive on tablet and mobile', () => {
  assert.match(source, /@media \(max-width:\s*860px\)[\s\S]*\.current-content,[\s\S]*flex-direction:\s*column/)
  assert.match(source, /@media \(max-width:\s*520px\)[\s\S]*\.current-card,[\s\S]*padding:\s*18px/)
})
