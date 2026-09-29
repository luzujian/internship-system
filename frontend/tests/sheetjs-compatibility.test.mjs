import assert from 'node:assert/strict'
import test from 'node:test'
import * as XLSX from 'xlsx'
import { createWorkbook, jsonToSheet, appendSheet, writeWorkbookToBuffer } from '../src/utils/xlsx.ts'

test('pinned SheetJS release preserves workbook export and import operations', async () => {
  assert.equal(XLSX.version, '0.20.3')
  const workbook = await createWorkbook()
  const sheet = await jsonToSheet([{ name: '测试学生', score: 95 }])
  await appendSheet(workbook, sheet, '成绩')

  const bytes = await writeWorkbookToBuffer(workbook)
  const reopened = XLSX.read(bytes, { type: 'array' })

  assert.deepEqual(reopened.SheetNames, ['成绩'])
  assert.deepEqual(XLSX.utils.sheet_to_json(reopened.Sheets['成绩']), [{ name: '测试学生', score: 95 }])
  assert.match(XLSX.utils.sheet_to_csv(reopened.Sheets['成绩']), /测试学生,95/)
})
