// Dashboard 数据缓存服务

// 缓存键名常量
export const CACHE_KEYS = {
  DASHBOARD_STATS: 'dashboard_stats',
  CURRENT_USER: 'current_user',
  RECENT_LOGS: 'recent_logs',
  DAILY_ACTIVE_USERS: 'daily_active_users'
} as const

export type CacheKey = typeof CACHE_KEYS[keyof typeof CACHE_KEYS]

// 缓存默认过期时间（毫秒）
export const CACHE_EXPIRATION = {
  SHORT: 5 * 60 * 1000, // 5 分钟
  MEDIUM: 15 * 60 * 1000, // 15 分钟
  LONG: 30 * 60 * 1000 // 30 分钟
} as const

export type CacheExpiration = keyof typeof CACHE_EXPIRATION

interface CacheItem<T> {
  data: T
  expiry: number
}

class DashboardCacheService {
  storage: Storage
  cacheVersion: string

  constructor() {
    this.storage = localStorage
    // 缓存版本，用于更新缓存结构
    this.cacheVersion = 'v1'
  }

  // 获取带版本号的缓存键
  getCacheKey(key: CacheKey): string {
    return `dashboard_${this.cacheVersion}_${key}`
  }

  // 检查缓存是否存在且未过期
  isCacheValid(key: CacheKey): boolean {
    try {
      const cacheKey = this.getCacheKey(key)
      const cachedData = this.storage.getItem(cacheKey)

      if (!cachedData) {
        return false
      }

      const parsedData = JSON.parse(cachedData) as CacheItem<unknown>

      // 检查是否过期
      return parsedData.expiry > Date.now()
    } catch (error) {
      console.error('检查缓存有效性失败:', error)
      return false
    }
  }

  // 获取缓存数据
  getCachedData<T>(key: CacheKey): T | null {
    try {
      if (!this.isCacheValid(key)) {
        return null
      }

      const cacheKey = this.getCacheKey(key)
      const cachedData = this.storage.getItem(cacheKey)

      if (!cachedData) {
        return null
      }

      const parsedData = JSON.parse(cachedData) as CacheItem<T>

      return parsedData.data
    } catch (error) {
      console.error('获取缓存数据失败:', error)
      this.removeCache(key)
      return null
    }
  }

  // 设置缓存数据
  setCachedData<T>(key: CacheKey, data: T, expiration: number = CACHE_EXPIRATION.SHORT): void {
    try {
      const cacheKey = this.getCacheKey(key)
      const cacheItem: CacheItem<T> = {
        data,
        expiry: Date.now() + expiration
      }

      this.storage.setItem(cacheKey, JSON.stringify(cacheItem))
    } catch (error) {
      console.error('设置缓存数据失败:', error)
    }
  }

  // 移除指定缓存
  removeCache(key: CacheKey): void {
    try {
      const cacheKey = this.getCacheKey(key)
      this.storage.removeItem(cacheKey)
    } catch (error) {
      console.error('移除缓存失败:', error)
    }
  }

  // 清除所有 Dashboard 相关缓存
  clearAllCache(): void {
    try {
      (Object.keys(CACHE_KEYS) as Array<keyof typeof CACHE_KEYS>).forEach(key => {
        this.removeCache(CACHE_KEYS[key])
      })
    } catch (error) {
      console.error('清除所有缓存失败:', error)
    }
  }

  // 清除过期缓存
  clearExpiredCache(): void {
    try {
      (Object.keys(CACHE_KEYS) as Array<keyof typeof CACHE_KEYS>).forEach(key => {
        if (!this.isCacheValid(CACHE_KEYS[key])) {
          this.removeCache(CACHE_KEYS[key])
        }
      })
    } catch (error) {
      console.error('清除过期缓存失败:', error)
    }
  }
}

// 创建单例实例
const dashboardCacheService = new DashboardCacheService()

export default dashboardCacheService
