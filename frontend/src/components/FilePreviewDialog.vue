<template>
  <el-dialog
    v-model="visible"
    :title="previewTitle"
    width="900px"
    class="file-preview-dialog"
    :close-on-click-modal="false"
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

      <!-- Office 文档预览 (使用微软在线预览) -->
      <div v-else-if="['word', 'excel', 'ppt'].includes(fileType)" class="office-preview">
        <iframe :src="officePreviewUrl" class="office-frame" frameborder="0" sandbox="allow-scripts allow-same-origin allow-forms"></iframe>
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
import { ref, computed, watch, onMounted } from 'vue'
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

const visibleRef = ref<HTMLElement | null>(null)

// 文件类型判断
const fileType = ref<string>('unknown')
const fileExtension = ref<string>('')

// 确保 fileExtension 始终是字符串
const getFileExtension = (): string => {
  if (!fileExtension.value) return ''
  return fileExtension.value
}

// 微软 Office 在线预览地址（支持 PDF、Word、Excel、PowerPoint）
const OFFICE_PREVIEW_URL = 'https://view.officeapps.live.com/op/view.aspx?src='

// PDF 预览地址（使用微软 Office Online 预览服务）
const pdfPreviewUrl = computed(() => {
  // 需要使用可公开访问的 URL，并进行编码
  const encodedUrl = encodeURIComponent(props.fileUrl)
  return OFFICE_PREVIEW_URL + encodedUrl
})

// Office 文档预览地址
const officePreviewUrl = computed(() => {
  // 需要使用可公开访问的 URL
  const encodedUrl = encodeURIComponent(props.fileUrl)
  return OFFICE_PREVIEW_URL + encodedUrl
})

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
    // 使用 axios 下载文件 blob
    const response = await fetch(props.fileUrl)
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
  }
  pdfDoc = null
  pdfError.value = false
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
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
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
