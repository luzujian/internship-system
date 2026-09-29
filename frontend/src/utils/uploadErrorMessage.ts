// el-upload uses XMLHttpRequest directly; it does not use the Axios interceptor.
export function getUploadErrorMessage(error: { status?: number; response?: { status?: number } }): string {
  const status = error.response?.status ?? error.status
  if (status === 413) {
    return '文件或本次上传总大小超过服务器允许的上限，请压缩文件或分批上传后重试。'
  }
  if (status === 401) return '登录已过期，请重新登录后上传。'
  if (status === 403) return '没有上传权限，请联系管理员。'
  if (!status) return '文件上传失败，请检查网络连接后重试。'
  return '文件上传失败，服务器暂时无法处理，请稍后重试。'
}
