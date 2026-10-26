export function isImportRequest(message) { const text = String(message || ''); return /导入|导进|录入|名单上传|上传.*名单/.test(text) && !/如何|怎么|支持|什么|可以.*吗/.test(text) }
