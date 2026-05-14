import DOMPurify from 'dompurify'

/**
 * HTML内容消毒 - 防止XSS攻击
 * 只允许安全的HTML标签和属性
 */
export function sanitizeHTML(dirty: string): string {
  return DOMPurify.sanitize(dirty, {
    ALLOWED_TAGS: ['b', 'i', 'em', 'strong', 'u', 'p', 'br', 'span', 'div', 'ul', 'ol', 'li', 'a', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6'],
    ALLOWED_ATTR: ['style', 'class', 'href', 'target'],
    ALLOW_DATA_ATTR: false
  })
}

/**
 * 转义正则表达式特殊字符
 * 用于搜索关键词高亮，防止正则表达式注入
 */
export function escapeRegex(str: string): string {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}