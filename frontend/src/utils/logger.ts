const isDevelopment = import.meta.env.DEV

const SENSITIVE_PATTERNS: RegExp[] = [
  /token/i,
  /令牌/i,
  /password/i,
  /密码/i,
  /jwt/i,
  /ey[a-zA-Z0-9_-]+\./i,
  /response.*完整内容/i,
  /用户信息.*:/i
]

function isSensitiveLog(message: unknown): boolean {
  const messageStr = String(message)
  return SENSITIVE_PATTERNS.some(pattern => pattern.test(messageStr))
}

function sanitizeLog(message: unknown): unknown {
  if (typeof message === 'string') {
    return message.replace(/ey[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+/g, '***TOKEN***')
  }
  return message
}

type LogFunction = (...args: unknown[]) => void

const logger: Record<string, LogFunction> = {
  log: (...args: unknown[]) => {
    if (!isDevelopment) return
    if (args.some(arg => isSensitiveLog(arg))) {
      return
    }
    console.log('[系统]', ...args.map(sanitizeLog))
  },

  warn: (...args: unknown[]) => {
    if (!isDevelopment) return
    console.warn('[警告]', ...args.map(sanitizeLog))
  },

  error: (...args: unknown[]) => {
    if (!isDevelopment) return
    console.error('[错误]', ...args.map(sanitizeLog))
  },

  debug: (...args: unknown[]) => {
    if (!isDevelopment) return
    if (args.some(arg => isSensitiveLog(arg))) {
      return
    }
    console.debug('[调试]', ...args.map(sanitizeLog))
  },

  auth: (...args: unknown[]) => {
    if (!isDevelopment) return
    if (args.some(arg => isSensitiveLog(arg))) {
      return
    }
    console.log('[认证]', ...args.map(sanitizeLog))
  }
}

export default logger
