import type { WorkBook, WorkSheet, CellObject } from 'xlsx'

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

// 绿色表头样式 (ARGB格式)
const GREEN_HEADER_BG = 'FF008000' // 绿色背景
const WHITE_FONT = 'FFFFFFFF'      // 白色字体
// 纯RGB格式
const GREEN_RGB = '008000'
const WHITE_RGB = 'FFFFFF'

export const exportToExcel = async (
  data: unknown,
  fileName: string,
  options?: string | ExportOptions
) => {
  // 尝试将数据转换为数组
  let validData: Record<string, unknown>[] = []

  if (Array.isArray(data)) {
    validData = data as Record<string, unknown>[]
  } else if (data && typeof data === 'object') {
    // 如果是类数组对象，尝试转换
    if (typeof (data as any).length === 'number' && typeof (data as any).forEach === 'function') {
      validData = Array.from(data as any) as Record<string, unknown>[]
    } else {
      // 如果是单个对象，包装成数组
      validData = [data as Record<string, unknown>]
    }
  }

  if (validData.length === 0) {
    console.error('exportToExcel: data 不是数组', data)
    throw new Error('导出数据必须是数组')
  }

  const XLSX = await getXLSX()

  const opts: ExportOptions = typeof options === 'string'
    ? { sheetName: options }
    : (options || {})

  const wb: WorkBook = XLSX.utils.book_new()
  const ws: WorkSheet = XLSX.utils.json_to_sheet(validData)

  if (opts.columnWidths) {
    ws['!cols'] = opts.columnWidths
  }

  // 设置绿色表头样式
  if (ws['!ref']) {
    const range = XLSX.utils.decode_range(ws['!ref'])
    for (let col = range.s.c; col <= range.e.c; col++) {
      const cellAddr = XLSX.utils.encode_cell({ r: 0, c: col })
      if (ws[cellAddr]) {
        // xlsx 库样式格式 - 使用纯RGB颜色
        ws[cellAddr].s = {
          fill: { patternType: 'solid', fgColor: GREEN_RGB },
          alignment: { horizontal: 'center', vertical: 'center' },
          font: { bold: true, color: WHITE_RGB }
        }
      }
    }
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

export const jsonToSheet = async (data: unknown) => {
  if (!Array.isArray(data)) {
    console.error('jsonToSheet: data 不是数组', data)
    throw new Error('数据必须是数组')
  }
  const XLSX = await getXLSX()
  return XLSX.utils.json_to_sheet(data as Record<string, unknown>[])
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
