<template>
  <el-dialog
    v-model="visible"
    :title="previewTitle"
    width="900px"
    class="file-preview-dialog"
    :close-on-click-modal="false"
    :append-to-body="true"
    :destroy-on-close="true"
    @closed="handleClosed"
  >
    <div class="file-preview-container" v-loading="loading" element-loading-text="加载中...">
      <div v-if="!byteUrl" class="unsupported-preview">文件读取地址不可用</div>
      <!-- 图片预览 -->
      <div v-else-if="fileType === 'image'" class="image-preview">
        <img v-if="mediaUrl" :src="mediaUrl" :alt="fileName" class="preview-image" @error="handleImageError" />
      </div>

      <!-- PDF 预览 (使用 PDF.js) -->
      <div v-else-if="fileType === 'pdf'" class="pdf-preview">
        <div class="pdf-container" ref="pdfContainer">
          <div v-if="pdfLoading" class="pdf-loading">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>加载中...</span>
          </div>
          <div v-if="pdfError" class="pdf-error">
            <el-icon><Warning /></el-icon>
            <span>PDF 加载失败</span>
            <el-button type="primary" size="small" @click="handleDownload">下载文件</el-button>
          </div>
          <canvas ref="pdfCanvas" class="pdf-canvas" style="display: none;"></canvas>
        </div>
      </div>

      <!-- DOCX 文档预览 (使用 docx-preview 本地渲染) -->
      <div v-else-if="fileType === 'docx'" class="word-preview">
        <div v-if="docxLoading" class="docx-loading-overlay">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>加载中...</span>
        </div>
        <div v-if="docxError && !docxLoading" class="docx-error-overlay">
          <el-icon><Warning /></el-icon>
          <span>Word 文档加载失败</span>
          <el-button type="primary" size="small" @click="handleDownload">下载文件</el-button>
        </div>
        <iframe
          v-if="!docxLoading && !docxError && docxHtmlContent"
          :srcdoc="docxHtmlContent"
          class="docx-iframe"
          sandbox="allow-same-origin"
        ></iframe>
      </div>

      <!-- 旧版 DOC：经本系统后端解析，只展示消毒后的正文和表格 -->
      <div v-else-if="fileType === 'doc'" class="word-preview">
        <div v-if="docLoading" class="docx-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>正在预览旧版 Word 文档...</span>
        </div>
        <div v-else-if="docError" class="docx-error">
          <el-icon><Warning /></el-icon>
          <span>旧版 Word 文档预览失败</span>
          <el-button type="primary" size="small" @click="handleDownload">下载文件</el-button>
        </div>
        <template v-else-if="docHtmlContent">
          <p class="doc-preview-note">旧版 Word 在线预览的版式可能与原文件不同，请下载原文件查看完整排版。</p>
          <div class="doc-preview-content" v-html="docHtmlContent"></div>
        </template>
      </div>

      <!-- Excel 文档预览 (使用 xlsx) -->
      <div v-else-if="fileType === 'excel'" class="excel-preview">
        <div v-if="excelLoading" class="excel-loading-overlay">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>加载中...</span>
        </div>
        <div v-if="excelError && !excelLoading" class="excel-error-overlay">
          <el-icon><Warning /></el-icon>
          <span>Excel 文档加载失败</span>
          <el-button type="primary" size="small" @click="handleDownload">下载文件</el-button>
        </div>
        <div v-if="!excelLoading && !excelError && excelHtmlContent" class="excel-container" v-html="excelHtmlContent"></div>
      </div>

      <!-- PPT 文档预览 -->
      <div v-else-if="fileType === 'ppt'" class="ppt-preview">
        <div class="ppt-info">
          <div class="ppt-icon">
            <el-icon :size="80" color="#409EFF"><Document /></el-icon>
          </div>
          <h3 class="ppt-title">{{ fileName }}</h3>
          <p class="ppt-tip">PPT文件需要下载后查看</p>
          <div class="ppt-actions">
            <el-button type="primary" size="large" @click="handleDownload">
              <el-icon><Download /></el-icon>
              下载文件
            </el-button>
          </div>
        </div>
      </div>

      <!-- 文本文件预览 -->
      <div v-else-if="fileType === 'text'" class="text-preview">
        <pre class="text-content">{{ textContent }}</pre>
      </div>

      <!-- 视频预览 -->
      <div v-else-if="fileType === 'video'" class="video-preview">
        <video v-if="mediaUrl" :src="mediaUrl" controls class="preview-video"></video>
      </div>

      <!-- 音频预览 -->
      <div v-else-if="fileType === 'audio'" class="audio-preview">
        <audio v-if="mediaUrl" :src="mediaUrl" controls class="preview-audio"></audio>
      </div>

      <!-- 不支持预览的文件类型 -->
      <div v-else class="unsupported-preview">
        <el-empty :description="`暂不支持预览 ${getFileExtension()} 格式文件`">
          <template #image>
            <el-icon :size="100" color="#909399"><Document /></el-icon>
          </template>
        </el-empty>
      </div>
    </div>

    <template #footer>
      <div class="preview-footer">
        <el-button type="primary" @click="handleDownload" :loading="downloading" :disabled="!byteUrl">
          <el-icon><Download /></el-icon>
          下载文件
        </el-button>
        <el-button @click="visible = false">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Download, Loading, Warning } from '@element-plus/icons-vue'
import type { PdfJsLib, PdfDocument } from '@/types'
import { authorizedFileByteUrl } from '@/utils/authorizedFileUrl'
import { downloadAuthorizedFile, fetchAuthorizedFile, previewLegacyDoc } from '@/utils/authorizedFileDownload'
import { sanitizeLegacyDocHTML } from '@/utils/sanitize'

const props = defineProps<{
  modelValue: boolean
  fileUrl: string
  fileName: string
  authorizedUrl: string
}>()

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const loading = ref(false)
const downloading = ref(false)
const textContent = ref('')
const mediaUrl = ref('')
let mediaObjectUrl = ''
const byteUrl = computed(() => authorizedFileByteUrl(props.authorizedUrl))

const loadMedia = async (sourceUrl: string) => {
  try {
    const response = await fetchAuthorizedFile(sourceUrl)
    const objectUrl = URL.createObjectURL(await response.blob())
    if (!visible.value || byteUrl.value !== sourceUrl) {
      URL.revokeObjectURL(objectUrl)
      return
    }
    if (mediaObjectUrl) URL.revokeObjectURL(mediaObjectUrl)
    mediaObjectUrl = objectUrl
    mediaUrl.value = objectUrl
  } catch (error) {
    console.error('加载预览文件失败:', error)
    ElMessage.error('加载文件失败')
  }
}

// PDF 相关
const pdfLoading = ref(false)
const pdfError = ref(false)
const pdfContainer = ref<HTMLDivElement | null>(null)
const pdfCanvas = ref<HTMLCanvasElement | null>(null)
let pdfDoc: PdfDocument | null = null

// Word(docx) 相关
const docxLoading = ref(false)
const docxError = ref(false)
const docxContainer = ref<HTMLDivElement | null>(null)
const docxHtmlContent = ref('')
let docxRendered = false

const docLoading = ref(false)
const docError = ref(false)
const docHtmlContent = ref('')
let docRenderId = 0

// Excel 相关
const excelLoading = ref(false)
const excelError = ref(false)
const excelHtmlContent = ref('')

// PPT 相关（已简化为下载模式，不需要loading和error状态）

// 文件类型判断
const fileType = ref<string>('unknown')
const fileExtension = ref<string>('')

// 确保 fileExtension 始终是字符串
const getFileExtension = (): string => {
  if (!fileExtension.value) return ''
  return fileExtension.value
}

// 预览标题
const previewTitle = computed(() => {
  if (!props.fileName) return '文件预览'
  return `预览：${props.fileName}`
})

// 检测文件类型
const detectFileType = (url: string, name: string): string => {
  // 安全地转换为字符串
  const nameStr = name ? String(name) : ''
  const urlStr = url ? String(url) : ''
  const str = nameStr || urlStr || ''

  // 如果字符串为空，返回 unknown
  if (!str) {
    fileExtension.value = ''
    return 'unknown'
  }

  const extension = str.split('.').pop()?.toLowerCase() || ''
  fileExtension.value = extension

  const imageExts = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg']
  const officeExts = {
    word: ['doc', 'docx'],
    excel: ['xls', 'xlsx'],
    ppt: ['ppt', 'pptx']
  }
  const textExts = ['txt', 'md', 'json', 'xml', 'csv', 'log']
  const videoExts = ['mp4', 'webm', 'ogg', 'mov', 'avi']
  const audioExts = ['mp3', 'wav', 'ogg', 'aac']

  if (imageExts.includes(extension)) return 'image'
  if (extension === 'pdf') return 'pdf'
  if (extension === 'doc') return 'doc'
  if (extension === 'docx') return 'docx'
  if (officeExts.excel.includes(extension)) return 'excel'
  if (officeExts.ppt.includes(extension)) return 'ppt'
  if (textExts.includes(extension)) return 'text'
  if (videoExts.includes(extension)) return 'video'
  if (audioExts.includes(extension)) return 'audio'

  return 'unknown'
}

// 加载文本文件内容
const loadTextContent = async () => {
  try {
    loading.value = true
    const response = await fetchAuthorizedFile(byteUrl.value)
    const text = await response.text()
    textContent.value = text.substring(0, 50000) // 限制显示长度
  } catch (error) {
    console.error('加载文本文件失败:', error)
    ElMessage.error('加载文件内容失败')
  } finally {
    loading.value = false
  }
}

// 加载 PDF.js
const loadPdfJs = (): Promise<PdfJsLib> => {
  return new Promise<PdfJsLib>((resolve, reject) => {
    if ((window as Window & { pdfjsLib?: PdfJsLib }).pdfjsLib) {
      resolve((window as Window & { pdfjsLib?: PdfJsLib }).pdfjsLib!)
      return
    }

    const script = document.createElement('script')
    script.src = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js'
    script.onload = () => {
      if ((window as Window & { pdfjsLib?: PdfJsLib }).pdfjsLib) {
        const pdfjsLib = (window as Window & { pdfjsLib?: PdfJsLib }).pdfjsLib!
        pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js'
        resolve(pdfjsLib)
      } else {
        reject(new Error('PDF.js 加载失败'))
      }
    }
    script.onerror = () => reject(new Error('PDF.js 脚本加载失败'))
    document.head.appendChild(script)
  })
}

// 渲染 PDF
const renderPdf = async () => {
  try {
    pdfLoading.value = true
    pdfError.value = false

    // 加载 PDF.js
    const pdfjsLib = await loadPdfJs()
    // 加载 PDF 文档
    const response = await fetchAuthorizedFile(byteUrl.value)
    pdfDoc = await pdfjsLib.getDocument({ data: await response.arrayBuffer() }).promise

    if (!pdfCanvas.value || !pdfContainer.value) {
      pdfLoading.value = false
      return
    }

    // 显示 canvas
    pdfCanvas.value.style.display = 'block'

    // 渲染第一页
    const page = await pdfDoc.getPage(1)
    const containerWidth = pdfContainer.value.offsetWidth
    const viewport = page.getViewport({ scale: 1 })
    const scale = containerWidth / viewport.width
    const scaledViewport = page.getViewport({ scale })

    pdfCanvas.value.height = scaledViewport.height
    pdfCanvas.value.width = scaledViewport.width

    const renderContext = {
      canvasContext: pdfCanvas.value.getContext('2d')!,
      viewport: scaledViewport
    }

    await page.render(renderContext).promise
    pdfLoading.value = false

    // 如果有更多页面，创建额外的 canvas
    if (pdfDoc.numPages > 1) {
      for (let i = 2; i <= pdfDoc.numPages; i++) {
        const pageNum = i
        const page = await pdfDoc.getPage(pageNum)
        const extraCanvas = document.createElement('canvas')
        extraCanvas.className = 'pdf-canvas'
        const extraViewport = page.getViewport({ scale })
        extraCanvas.height = extraViewport.height
        extraCanvas.width = extraViewport.width
        pdfContainer.value.appendChild(extraCanvas)

        const extraRenderContext = {
          canvasContext: extraCanvas.getContext('2d')!,
          viewport: extraViewport
        }
        await page.render(extraRenderContext).promise
      }
    }
  } catch (error) {
    console.error('PDF 渲染失败:', error)
    pdfError.value = true
    pdfLoading.value = false
  }
}

// 渲染 Word (doc/docx) 文档
const renderDocx = async () => {
  try {
    docxLoading.value = true
    docxError.value = false

    // 获取文件内容
    const response = await fetchAuthorizedFile(byteUrl.value)
    const blob = await response.blob()

    // 使用 docx-preview 渲染
    const { renderAsync } = await import('docx-preview')

    // 创建一个完全脱离的临时容器（永远不添加到 DOM）
    const tempContainer = document.createElement('div')
    tempContainer.style.cssText = 'position:absolute;left:-9999px;top:-9999px;width:800px;overflow:hidden;'

    // 在临时容器中渲染（useMathMLPolyfill 为当前 docx-preview 版本已移除的旧选项，
    // 运行时透传无影响；用局部接口收敛类型以通过 vue-tsc，避免对象字面量多余属性检查）
    interface DocxPreviewRenderOptions {
      className?: string
      inWrapper?: boolean
      ignoreLastRenderedPageBreak?: boolean
      experimental?: boolean
      trimXmlDeclaration?: boolean
      useBase64URL?: boolean
      useMathMLPolyfill?: boolean
      renderHeaders?: boolean
      renderFooters?: boolean
      renderFootnotes?: boolean
      renderEndnotes?: boolean
    }
    const docxRenderOptions: DocxPreviewRenderOptions = {
      className: 'docx',
      inWrapper: true,
      ignoreLastRenderedPageBreak: true,
      experimental: false,
      trimXmlDeclaration: true,
      useBase64URL: true,
      useMathMLPolyfill: true,
      renderHeaders: true,
      renderFooters: true,
      renderFootnotes: true,
      renderEndnotes: true
    }
    await renderAsync(blob, tempContainer, null, docxRenderOptions)

    // 直接获取渲染后的 HTML 字符串
    docxHtmlContent.value = tempContainer.innerHTML

    docxLoading.value = false
  } catch (error) {
    console.error('Word 文档渲染失败:', error)
    docxError.value = true
    docxLoading.value = false
  }
}

const renderLegacyDoc = async (sourceUrl: string) => {
  const renderId = ++docRenderId
  docLoading.value = true
  docError.value = false
  docHtmlContent.value = ''
  try {
    const html = await previewLegacyDoc(sourceUrl)
    if (renderId !== docRenderId || !visible.value || byteUrl.value !== sourceUrl) return
    const sanitized = sanitizeLegacyDocHTML(html)
    if (!sanitized.trim()) throw new Error('文档无可预览内容')
    docHtmlContent.value = sanitized
  } catch (error) {
    if (renderId !== docRenderId) return
    console.error('旧版 Word 文档预览失败:', error)
    docError.value = true
  } finally {
    if (renderId === docRenderId) docLoading.value = false
  }
}

// 渲染 Excel 文档
const renderExcel = async () => {
  try {
    excelLoading.value = true
    excelError.value = false

    // 获取文件内容
    const response = await fetchAuthorizedFile(byteUrl.value)
    const blob = await response.blob()

    // 使用 FileReader 读取 blob
    const arrayBuffer = await blob.arrayBuffer()
    const XLSX_module = await import('xlsx')
    const XLSX = XLSX_module.default || XLSX_module
    const workbook = XLSX.read(arrayBuffer, { type: 'array', cellDates: true })

    // 生成 HTML 表格
    let htmlContent = '<div class="excel-wrapper">'

    workbook.SheetNames.forEach((sheetName: string, index: number) => {
      const sheet = workbook.Sheets[sheetName]
      const csvContent = XLSX.utils.sheet_to_csv(sheet)
      const rows = csvContent.split('\n')

      htmlContent += `<div class="excel-sheet">`
      if (workbook.SheetNames.length > 1) {
        htmlContent += `<div class="sheet-title">${escapeHtml(sheetName)}</div>`
      }
      htmlContent += `<table class="excel-table">`

      rows.forEach((row: string, rowIndex: number) => {
        if (rowIndex === 0) {
          // 表头
          htmlContent += '<thead><tr>'
          const headers = row.split(',')
          headers.forEach((header: string) => {
            htmlContent += `<th>${escapeHtml(header.trim())}</th>`
          })
          htmlContent += '</tr></thead><tbody>'
        } else if (row.trim()) {
          // 数据行
          htmlContent += '<tr>'
          const cells = row.split(',')
          cells.forEach((cell: string) => {
            htmlContent += `<td>${escapeHtml(cell.trim())}</td>`
          })
          htmlContent += '</tr>'
        }
      })

      htmlContent += '</tbody></table></div>'
    })

    htmlContent += '</div>'

    // 设置 HTML 内容
    excelHtmlContent.value = htmlContent
    excelLoading.value = false
  } catch (error) {
    console.error('Excel 文档渲染失败:', error)
    excelError.value = true
    excelLoading.value = false
  }
}

// HTML 转义
const escapeHtml = (str: string): string => {
  const div = document.createElement('div')
  div.textContent = str
  return div.innerHTML
}

// 图片加载错误处理
const handleImageError = () => {
  ElMessage.error('图片加载失败')
}

// 下载文件
const handleDownload = async (event: Event) => {
  // 阻止事件冒泡
  if (event) {
    event.stopPropagation()
  }

  if (!byteUrl.value) {
    ElMessage.error('文件读取地址不可用')
    return
  }

  downloading.value = true
  try {
    await downloadAuthorizedFile(byteUrl.value, props.fileName)
    ElMessage.success('开始下载')
  } catch (error) {
    console.error('下载失败:', error)
    ElMessage.error('下载失败')
  } finally {
    downloading.value = false
  }
}

// 对话框关闭后的清理
const handleClosed = () => {
  docRenderId++
  docHtmlContent.value = ''
  docLoading.value = false
  docError.value = false
  if (mediaObjectUrl) URL.revokeObjectURL(mediaObjectUrl)
  mediaObjectUrl = ''
  mediaUrl.value = ''
  textContent.value = ''
  fileType.value = 'unknown'
  fileExtension.value = ''
  // 清理 PDF 相关
  if (pdfContainer.value) {
    // 保留第一个 canvas，删除额外创建的 canvas
    const canvases = pdfContainer.value.querySelectorAll<HTMLCanvasElement>('.pdf-canvas')
    canvases.forEach((canvas: HTMLCanvasElement, index: number) => {
      if (index > 0) {
        canvas.remove()
      }
    })
    // 重置第一个 canvas
    if (pdfCanvas.value) {
      pdfCanvas.value.style.display = 'none'
      pdfCanvas.value.width = 0
      pdfCanvas.value.height = 0
    }
  }
  pdfDoc = null
  pdfError.value = false
  // 清理 docx 容器
  docxHtmlContent.value = ''
  docxError.value = false
  // 清理 excel 容器
  excelHtmlContent.value = ''
  excelError.value = false
}

// 监听对话框打开和文件变化
watch(() => [visible.value, props.fileUrl, props.fileName, byteUrl.value] as const, ([newVisible, newUrl, newName, newByteUrl]) => {
  if (newVisible && newByteUrl) {
    // 确保 newName 是字符串类型
    const nameStr = typeof newName === 'string' ? newName : ''
    fileType.value = detectFileType(newUrl, nameStr)
    if (fileType.value === 'image' || fileType.value === 'video' || fileType.value === 'audio') {
      void loadMedia(newByteUrl)
    }

    // 文本文件需要加载内容
    if (fileType.value === 'text') {
      loadTextContent()
    }
    if (fileType.value === 'pdf') {
      // 延迟渲染，确保 DOM 已经就绪
      setTimeout(() => {
        renderPdf()
      }, 100)
    }
    // DOCX 文档需要使用 docx-preview 渲染
    if (fileType.value === 'docx') {
      setTimeout(() => {
        renderDocx()
      }, 100)
    }
    if (fileType.value === 'doc') {
      void renderLegacyDoc(newByteUrl)
    }
    // Excel 文档需要渲染
    if (fileType.value === 'excel') {
      setTimeout(() => {
        renderExcel()
      }, 100)
    }
    // PPT 文档使用下载模式，无需特殊处理
  }
}, { immediate: true })
</script>

<style scoped>
.file-preview-dialog {
  border-radius: 16px;
  overflow: hidden;
}

.file-preview-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  padding: 20px 24px;
  border-bottom: 1px solid #e0f2fe;
}

.file-preview-dialog :deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 600;
  color: #1e40af;
}

.file-preview-container {
  min-height: 500px;
  display: flex;
  flex-direction: column;
}

/* 图片预览 */
.image-preview {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 500px;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
}

.preview-image {
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
}

/* PDF 预览 */
.pdf-preview {
  flex: 1;
  min-height: 600px;
  position: relative;
}

.pdf-container {
  width: 100%;
  height: 70vh;
  overflow: auto;
  background: #f5f5f5;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.pdf-loading,
.pdf-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  height: 100%;
  color: #909399;
}

.pdf-error {
  color: #f56c6c;
}

.pdf-canvas {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border-radius: 4px;
  background: #fff;
}

/* Word 文档预览 */
.word-preview {
  flex: 1;
  min-height: 600px;
  overflow: auto;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
}

.docx-container {
  width: 100%;
  min-height: 500px;
}

.docx-loading,
.docx-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #909399;
}

.docx-error {
  color: #f56c6c;
}

.docx-iframe {
  width: 100%;
  height: 70vh;
  border: none;
  background: #fff;
}

.doc-preview-note {
  color: #6b7280;
  font-size: 12px;
  margin: 0 0 16px;
}

.doc-preview-content {
  max-width: 100%;
  overflow-x: auto;
  line-height: 1.65;
  color: #303133;
}

.doc-preview-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
}

.doc-preview-content :deep(td),
.doc-preview-content :deep(th) {
  border: 1px solid #dcdfe6;
  padding: 6px 8px;
  vertical-align: top;
}

.docx-loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(255, 255, 255, 0.9);
  color: #909399;
  z-index: 10;
}

.docx-error-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(255, 255, 255, 0.9);
  color: #f56c6c;
  z-index: 10;
}

.docx-container :deep(.docx) {
  font-size: 14px;
  line-height: 1.6;
}

.docx-container :deep(.docx h1) {
  font-size: 24px;
  font-weight: bold;
  margin: 16px 0;
}

.docx-container :deep(.docx h2) {
  font-size: 20px;
  font-weight: bold;
  margin: 14px 0;
}

.docx-container :deep(.docx h3) {
  font-size: 18px;
  font-weight: bold;
  margin: 12px 0;
}

.docx-container :deep(.docx p) {
  margin: 8px 0;
}

.docx-container :deep(.docx table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
}

.docx-container :deep(.docx td),
.docx-container :deep(.docx th) {
  border: 1px solid #ddd;
  padding: 6px 10px;
}

/* Excel 文档预览 */
.excel-preview {
  flex: 1;
  min-height: 600px;
  overflow: auto;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  position: relative;
}

.excel-container {
  width: 100%;
  min-height: 500px;
}

.excel-loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(255, 255, 255, 0.9);
  color: #909399;
  z-index: 10;
}

.excel-error-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(255, 255, 255, 0.9);
  color: #f56c6c;
  z-index: 10;
}

.excel-loading,
.excel-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  height: 300px;
  color: #909399;
}

.excel-error {
  color: #f56c6c;
}

.excel-wrapper {
  width: 100%;
}

.excel-sheet {
  margin-bottom: 20px;
}

.excel-sheet .sheet-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  padding: 8px 0;
  border-bottom: 2px solid #409EFF;
  margin-bottom: 8px;
}

.excel-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.excel-table th {
  background: #f5f7fa;
  font-weight: 600;
  text-align: left;
  padding: 8px 12px;
  border: 1px solid #e4e7ed;
  color: #303133;
}

.excel-table td {
  padding: 6px 12px;
  border: 1px solid #e4e7ed;
  color: #606266;
}

.excel-table tr:hover {
  background: #f0f7ff;
}

/* PPT 文档预览 */
.ppt-preview {
  flex: 1;
  min-height: 600px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.ppt-info {
  text-align: center;
  padding: 40px;
}

.ppt-icon {
  margin-bottom: 20px;
}

.ppt-title {
  font-size: 18px;
  color: #303133;
  margin-bottom: 12px;
}

.ppt-tip {
  color: #909399;
  margin-bottom: 24px;
}

.ppt-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

/* Office 文档预览 */
.office-preview {
  flex: 1;
  min-height: 600px;
}

.office-frame {
  width: 100%;
  height: 70vh;
  border-radius: 8px;
}

/* 文本预览 */
.text-preview {
  flex: 1;
  background: #1e293b;
  border-radius: 8px;
  padding: 20px;
  overflow: auto;
  max-height: 70vh;
}

.text-content {
  font-family: var(--font-family-mono);
  font-size: 14px;
  line-height: 1.6;
  color: #e2e8f0;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}

/* 视频预览 */
.video-preview {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 500px;
  background: #000;
  border-radius: 8px;
}

.preview-video {
  max-width: 100%;
  max-height: 70vh;
}

/* 音频预览 */
.audio-preview {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 300px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 8px;
}

.preview-audio {
  width: 80%;
}

/* 不支持预览的文件 */
.unsupported-preview {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

/* 底部按钮 */
.preview-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 0;
}

.preview-footer .el-button {
  border-radius: 8px;
  padding: 10px 24px;
}

/* 手机端：预览内容统一限制在视口内，超出部分容器内滚动 */
@media (max-width: 768px) {
  .file-preview-container,
  .image-preview,
  .video-preview,
  .pdf-preview,
  .word-preview {
    min-height: 0;
  }

  .file-preview-container {
    max-height: calc(100dvh - 240px);
    overflow-y: auto;
  }

  .preview-image,
  .preview-video {
    max-width: 100%;
    max-height: 52dvh;
    object-fit: contain;
  }

  .pdf-container {
    height: auto;
    max-height: 52dvh;
    padding: 8px;
  }

  .pdf-canvas {
    max-width: 100%;
    height: auto !important;
  }

  .word-preview {
    padding: 8px;
    overflow: auto;
  }

  .docx-iframe {
    height: 52dvh;
  }

  .excel-container {
    max-width: 100%;
    max-height: 52dvh;
    overflow: auto;
  }

  .office-frame {
    height: 52dvh;
  }

  .text-preview {
    max-height: 52dvh;
    padding: 12px;
  }

  .audio-preview {
    min-height: 160px;
    padding: 12px;
  }

  .preview-audio {
    width: 100%;
  }

  .unsupported-preview {
    min-height: 200px;
  }
}
</style>
