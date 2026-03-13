/**
 * Token 管理工具
 */

import { CONFIG, ERROR_MESSAGES } from './config'
import { getRoleStoragePrefix, getRefreshTokenKey, getAccessTokenKey } from './storage'
import logger from '@/utils/logger'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

interface TokenPayload {
  exp?: number
  jti?: string
  typ?: string
  userId?: string
  username?: string
  name?: string
  isReadOnly?: boolean
  originalAdminUsername?: string
  [key: string]: unknown
}

/**
 * 解码 JWT Token
 */
export function decodeToken(token: string): TokenPayload | null {
  try {
    const base64Url = token.split('.')[1]
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const jsonPayload = decodeURIComponent(
      window
        .atob(base64)
        .split('')
        .map(function (c) {
          return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)
        })
        .join('')
    )
    return JSON.parse(jsonPayload)
  } catch (error) {
    logger.error('解码 JWT 失败:', error)
    return null
  }
}

/**
 * 检查 Token 是否过期
 */
export function isTokenExpired(token: string): boolean {
  try {
    const decoded = decodeToken(token)
    if (!decoded || !decoded.exp) {
      return true
    }

    const now = Date.now() / 1000
    return decoded.exp < now + CONFIG.TOKEN_EXPIRY_BUFFER
  } catch (error) {
    logger.error('检查令牌过期状态时出错:', error)
    return true
  }
}

/**
 * 从 Token 获取过期时间
 */
export function getTokenExpiry(token: string): number | null {
  try {
    const decoded = decodeToken(token)
    if (!decoded || !decoded.exp) {
      return null
    }
    return decoded.exp * 1000 // 转换为毫秒
  } catch (error) {
    return null
  }
}

/**
 * 验证 Token 有效性
 */
export function validateToken(token: string): boolean {
  if (!token) return false

  try {
    const decoded = decodeToken(token)
    if (!decoded) return false

    if (!decoded.jti || !decoded.typ) {
      logger.warn('令牌缺少必要的安全字段')
      return false
    }

    if (isTokenExpired(token)) {
      logger.warn('令牌已过期或即将过期')
      return false
    }

    return true
  } catch (error) {
    logger.error('令牌验证失败:', error)
    return false
  }
}

/**
 * 刷新访问令牌
 */
export async function refreshAccessToken(currentRole: string): Promise<string | null> {
  if (!currentRole) {
    logger.error('无法刷新令牌：未找到当前角色')
    return null
  }

  const rolePrefix = getRoleStoragePrefix(currentRole)
  let refreshToken = localStorage.getItem(getRefreshTokenKey(currentRole))

  if (!refreshToken) {
    refreshToken = localStorage.getItem(rolePrefix + 'refreshToken')
  }

  if (!refreshToken) {
    refreshToken = localStorage.getItem('refreshToken')
  }

  if (!refreshToken) {
    logger.error('无法刷新令牌：未找到刷新令牌')
    ElMessage.error(ERROR_MESSAGES.TOKEN_REFRESH_FAILED)
    return null
  }

  const now = Date.now()
  const minRefreshInterval = CONFIG.MIN_REFRESH_INTERVAL
  const lastRefreshTime = localStorage.getItem(rolePrefix + 'lastRefreshTime_' + currentRole) || '0'

  const forceRefresh = localStorage.getItem(rolePrefix + 'forceRefresh_' + currentRole) === 'true'
  const hasError = localStorage.getItem(rolePrefix + 'hasError_' + currentRole) === 'true'

  if (!forceRefresh && !hasError && now - parseInt(lastRefreshTime) < minRefreshInterval) {
    logger.warn('跳过刷新，距离上次刷新时间过短:', currentRole)
    return localStorage.getItem(getAccessTokenKey(currentRole))
  }

  if (forceRefresh) {
    localStorage.removeItem(rolePrefix + 'forceRefresh_' + currentRole)
  }
  localStorage.removeItem(rolePrefix + 'hasError_' + currentRole)

  localStorage.setItem(rolePrefix + 'lastRefreshTime_' + currentRole, now.toString())

  try {
    logger.log('尝试刷新访问令牌...')
    const response = await request.post('/auth/refresh-token', {
      refreshToken: refreshToken
    })

    if (response && response.status === 200 && response.data?.code === 200 && response.data?.data) {
      const newAccessToken = response.data.data.accessToken
      const newRefreshToken = response.data.data.refreshToken

      localStorage.setItem(getAccessTokenKey(currentRole), newAccessToken)

      if (newRefreshToken) {
        localStorage.setItem(getRefreshTokenKey(currentRole), newRefreshToken)
      }

      localStorage.setItem(rolePrefix + 'lastRefreshTime_' + currentRole, now.toString())
      localStorage.setItem(rolePrefix + 'refreshAttempts_' + currentRole, '0')

      logger.log('访问令牌刷新成功')
      return newAccessToken
    } else {
      logger.error('刷新令牌业务失败')
      ElMessage.error(ERROR_MESSAGES.TOKEN_REFRESH_FAILED)
      return null
    }
  } catch (error) {
    logger.error('刷新令牌过程中发生错误:', error)
    ElMessage.error(ERROR_MESSAGES.TOKEN_REFRESH_FAILED)
    return null
  }
}
