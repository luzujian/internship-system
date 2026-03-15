import type { WorkBook, WorkSheet } from 'xlsx'

let xlsxModule: typeof import('xlsx') | null = null

const getXLSX = async () => {
  if (!xlsxModule) {
    xlsxModule = await import('xlsx')
  }
  return xlsxModule
}

interface ExportOptions {
  sheetName?: string
  columnWidths?: { wch: number }[]
}

export const exportToExcel = async (
  data: Record<string, unknown>[],
  fileName: string,
  options?: string | ExportOptions
) => {
  const XLSX = await getXLSX()
  
  const opts: ExportOptions = typeof options === 'string' 
    ? { sheetName: options } 
    : (options || {})
  
  const wb: WorkBook = XLSX.utils.book_new()
  const ws: WorkSheet = XLSX.utils.json_to_sheet(data)
  
  if (opts.columnWidths) {
    ws['!cols'] = opts.columnWidths
  }
  
  XLSX.utils.book_append_sheet(wb, ws, opts.sheetName || 'Sheet1')
  XLSX.writeFile(wb, `${fileName}.xlsx`)
}

export const readExcelFile = async (
  file: File
): Promise<Record<string, unknown>[]> => {
  const XLSX = await getXLSX()
  
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => {
      try {
        const data = new Uint8Array(e.target?.result as ArrayBuffer)
        const workbook = XLSX.read(data, { type: 'array' })
        const sheetName = workbook.SheetNames[0]
        const worksheet = workbook.Sheets[sheetName]
        const jsonData = XLSX.utils.sheet_to_json(worksheet)
        resolve(jsonData as Record<string, unknown>[])
      } catch (error) {
        reject(error)
      }
    }
    reader.onerror = reject
    reader.readAsArrayBuffer(file)
  })
}

export const createWorkbook = async () => {
  const XLSX = await getXLSX()
  return XLSX.utils.book_new()
}

export const jsonToSheet = async (data: Record<string, unknown>[]) => {
  const XLSX = await getXLSX()
  return XLSX.utils.json_to_sheet(data)
}

export const appendSheet = async (wb: WorkBook, ws: WorkSheet, name: string) => {
  const XLSX = await getXLSX()
  return XLSX.utils.book_append_sheet(wb, ws, name)
}

export const writeWorkbook = async (wb: WorkBook, fileName: string) => {
  const XLSX = await getXLSX()
  return XLSX.writeFile(wb, `${fileName}.xlsx`)
}

export const writeWorkbookToBuffer = async (wb: WorkBook): Promise<Uint8Array> => {
  const XLSX = await getXLSX()
  return XLSX.write(wb, { bookType: 'xlsx', type: 'array' })
}

export default {
  exportToExcel,
  readExcelFile,
  createWorkbook,
  jsonToSheet,
  appendSheet,
  writeWorkbook,
  writeWorkbookToBuffer
}
