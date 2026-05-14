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
      <!-- 图片预览 -->
      <div v-if="fileType === 'image'" class="image-preview">
        <img :src="fileUrl" :alt="fileName" class="preview-image" @error="handleImageError" />
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

      <!-- Word 文档预览 (使用 docx-preview + iframe 隔离) -->
      <div v-else-if="fileType === 'word'" class="word-preview">
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
        <video :src="fileUrl" controls class="preview-video"></video>
      </div>

      <!-- 音频预览 -->
      <div v-else-if="fileType === 'audio'" class="audio-preview">
        <audio :src="fileUrl" controls class="preview-audio"></audio>
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
        <el-button type="primary" @click="handleDownload" :loading="downloading">
          <el-icon><Download /></el-icon>
          下载文件
        </el-button>
        <el-button @click="visible = false">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Download, Loading, Warning } from '@element-plus/icons-vue'
import type { PdfJsLib, PdfDocument } from '@/types'

const props = defineProps<{
  modelValue: boolean
  fileUrl: string
  fileName: string
}>()

console.log('[FilePreviewDialog] props:', props)

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const loading = ref(false)
const downloading = ref(false)
const textContent = ref('')

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
  console.log('[FilePreviewDialog] detectFileType called with:', { url, name })

  // 安全地转换为字符串
  const nameStr = name ? String(name) : ''
  const urlStr = url ? String(url) : ''
  const str = nameStr || urlStr || ''

  console.log('[FilePreviewDialog] processed strings:', { nameStr, urlStr, str })

  // 如果字符串为空，返回 unknown
  if (!str) {
    fileExtension.value = ''
    return 'unknown'
  }

  const extension = str.split('.').pop()?.toLowerCase() || ''
  fileExtension.value = extension

  console.log('[FilePreviewDialog] detected extension:', extension)

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
  if (officeExts.word.includes(extension)) return 'word'
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
    const response = await fetch(props.fileUrl)
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

// 将 OSS URL 转换为后端代理 URL
const convertToProxyUrl = (ossUrl: string): string => {
  // 提取 OSS 路径 (例如：/resources/pdf/2026/03/xxx.pdf 或 /uploads/xxx.pdf)
  try {
    // 如果是完整 URL，使用 URL 解析
    if (ossUrl.startsWith('http://') || ossUrl.startsWith('https://')) {
      const urlObj = new URL(ossUrl)
      const path = urlObj.pathname
      return `/api/upload/preview${path}`
    }
    // 如果已经是路径格式（以 / 开头），直接使用
    if (ossUrl.startsWith('/')) {
      // 确保路径正确，去掉可能的前缀重复
      let cleanPath = ossUrl
      // 如果路径已经包含 /api/upload/preview，直接返回
      if (cleanPath.startsWith('/api/upload/preview')) {
        return cleanPath
      }
      // 否则添加预览前缀
      return `/api/upload/preview${cleanPath}`
    }
    // 其他情况，直接拼接
    return `/api/upload/preview/${ossUrl}`
  } catch (e) {
    console.error('解析 OSS URL 失败:', e)
    // 回退：如果路径已经是正确的格式，直接使用
    if (ossUrl.startsWith('/api/upload/preview')) {
      return ossUrl
    }
    return `/api/upload/preview${ossUrl.startsWith('/') ? ossUrl : '/' + ossUrl}`
  }
}

// 渲染 PDF
const renderPdf = async () => {
  try {
    pdfLoading.value = true
    pdfError.value = false

    // 将 OSS URL 转换为代理 URL
    const proxyUrl = convertToProxyUrl(props.fileUrl)
    console.log('[FilePreviewDialog] 使用代理 URL:', proxyUrl)

    // 加载 PDF.js
    const pdfjsLib = await loadPdfJs()
    // 加载 PDF 文档
    pdfDoc = await pdfjsLib.getDocument(proxyUrl).promise

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

// 渲染 Word (docx) 文档
const renderDocx = async () => {
  try {
    docxLoading.value = true
    docxError.value = false

    // 将 OSS URL 转换为代理 URL
    const proxyUrl = convertToProxyUrl(props.fileUrl)
    console.log('[FilePreviewDialog] 渲染 Word 文档，使用代理 URL:', proxyUrl)

    // 获取文件内容
    const response = await fetch(proxyUrl)
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    const blob = await response.blob()

    // 使用 docx-preview 渲染
    const { renderAsync } = await import('docx-preview')

    // 创建一个完全脱离的临时容器（永远不添加到 DOM）
    const tempContainer = document.createElement('div')
    tempContainer.style.cssText = 'position:absolute;left:-9999px;top:-9999px;width:800px;overflow:hidden;'

    // 在临时容器中渲染
    await renderAsync(blob, tempContainer, null, {
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
    })

    // 直接获取渲染后的 HTML 字符串
    docxHtmlContent.value = tempContainer.innerHTML

    docxLoading.value = false
  } catch (error) {
    console.error('Word 文档渲染失败:', error)
    docxError.value = true
    docxLoading.value = false
  }
}

// 渲染 Excel 文档
const renderExcel = async () => {
  try {
    excelLoading.value = true
    excelError.value = false

    // 将 OSS URL 转换为代理 URL
    const proxyUrl = convertToProxyUrl(props.fileUrl)
    console.log('[FilePreviewDialog] 渲染 Excel 文档，使用代理 URL:', proxyUrl)

    // 获取文件内容
    const response = await fetch(proxyUrl)
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
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
        htmlContent += `<div class="sheet-title">${sheetName}</div>`
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

  if (!props.fileUrl) {
    ElMessage.error('文件地址不存在')
    return
  }

  downloading.value = true
  try {
    // 获取代理 URL 进行下载
    const proxyUrl = convertToProxyUrl(props.fileUrl)
    const response = await fetch(proxyUrl)
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = props.fileName || 'download'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('开始下载')
  } catch (error) {
    console.error('下载失败:', error)
    // 如果 blob 下载失败，回退到直接下载
    try {
      const link = document.createElement('a')
      link.href = props.fileUrl
      link.download = props.fileName || 'download'
      link.target = '_blank'
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      ElMessage.success('开始下载')
    } catch (fallbackError) {
      console.error('回退下载失败:', fallbackError)
      ElMessage.error('下载失败')
    }
  } finally {
    downloading.value = false
  }
}

// 对话框关闭后的清理
const handleClosed = () => {
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
watch(() => [visible.value, props.fileUrl, props.fileName] as const, ([newVisible, newUrl, newName]) => {
  console.log('[FilePreviewDialog] watch triggered:', {
    newVisible,
    newUrl,
    newName,
    newUrlType: typeof newUrl,
    newNameType: typeof newName
  })

  if (newVisible && newUrl) {
    // 确保 newName 是字符串类型
    const nameStr = typeof newName === 'string' ? newName : ''
    fileType.value = detectFileType(newUrl, nameStr)
    console.log('[FilePreviewDialog] detected fileType:', fileType.value)

    // 文本文件需要加载内容
    if (fileType.value === 'text') {
      loadTextContent()
    }
    // PDF 文件需要渲染
    if (fileType.value === 'pdf') {
      // 延迟渲染，确保 DOM 已经就绪
      setTimeout(() => {
        renderPdf()
      }, 100)
    }
    // Word 文档需要渲染
    if (fileType.value === 'word') {
      setTimeout(() => {
        renderDocx()
      }, 100)
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
</style>
