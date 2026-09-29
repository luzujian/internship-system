import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { computed, ref } from 'vue'

const source = fs.readFileSync(new URL('./InternshipAppraisal.vue', import.meta.url), 'utf8')
const expression = source.match(/const allCompanyRatingsSelected = computed\([\s\S]*?\n\)\)/)[0]
const compiled = ts.transpileModule(expression, {
  compilerOptions: { target: ts.ScriptTarget.ES2022 }
}).outputText

function ratingsComplete(companies, stars) {
  return vm.runInNewContext(`${compiled}\nallCompanyRatingsSelected.value`, {
    computed,
    requiredCompanyRatings: ref(companies),
    companyRatings: ref(stars)
  })
}

test('unregistered self-practice needs no stars to pass the rating gate', () => {
  assert.equal(ratingsComplete([], {}), true)
})

test('registered companies require an integer rating from one to five for every company', () => {
  const companies = [{ companyId: 101 }, { companyId: 202 }]
  for (const invalid of [undefined, 0, 6, 4.5]) {
    assert.equal(ratingsComplete(companies, { 101: 5, 202: invalid }), false)
  }
  assert.equal(ratingsComplete(companies, { 101: 5, 202: 1 }), true)
})
