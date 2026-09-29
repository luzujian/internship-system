import { getAccessToken } from '@/utils/request'
import { authorizedFileByteUrl } from '@/utils/authorizedFileUrl'

export const fetchAuthorizedFile = async (url: string): Promise<Response> => {
  const safeUrl = authorizedFileByteUrl(url)
  if (!safeUrl) throw new Error('文件读取地址不可用')
  const token = getAccessToken()
  const response = await fetch(safeUrl, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response
}

/** Convert bytes already obtained from a record-scoped route; no OSS key or URL is submitted. */
export const previewLegacyDoc = async (url: string): Promise<string> => {
  const source = await fetchAuthorizedFile(url)
  const bytes = await source.arrayBuffer()
  if (!bytes.byteLength || bytes.byteLength > 10 * 1024 * 1024) {
    throw new Error('旧版 Word 文件为空或超过 10MB')
  }
  const token = getAccessToken()
  if (!token) throw new Error('请重新登录后预览')
  const response = await fetch('/api/upload/preview-legacy-doc', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/octet-stream'
    },
    body: bytes
  })
  if (!response.ok) throw new Error(`旧版 Word 预览失败：HTTP ${response.status}`)
  return response.text()
}

export const downloadAuthorizedFile = async (url: string, fileName: string): Promise<void> => {
  const response = await fetchAuthorizedFile(url)
  const objectUrl = URL.createObjectURL(await response.blob())
  try {
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = fileName || 'download'
    document.body.appendChild(link)
    link.click()
    link.remove()
  } finally {
    setTimeout(() => URL.revokeObjectURL(objectUrl), 0)
  }
}
