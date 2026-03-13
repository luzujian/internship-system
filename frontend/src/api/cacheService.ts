// 全局数据缓存服务

interface CacheItem<T = unknown> {
  data: T
  expireAt: number
}

interface PreloadItem<T = unknown> {
  key: string
  loadFunction: () => Promise<T>
  expireTime?: number
}

interface ExpirationTime {
  SHORT: number
  MEDIUM: number
  LONG: number
}

interface CacheConfig {
  EXPIRATION_TIME: ExpirationTime
  MAX_CACHE_ITEMS: number
}

/**
 * 缓存配置选项
 */
const CACHE_CONFIG: CacheConfig = {
  // 缓存过期时间（毫秒）
  EXPIRATION_TIME: {
    SHORT: 30000,  // 30 秒 - 用于频繁变化的数据
    MEDIUM: 120000, // 2 分钟 - 用于一般数据
    LONG: 300000   // 5 分钟 - 用于不常变化的数据
  },
  // 最大缓存项数量
  MAX_CACHE_ITEMS: 100
}

/**
 * 缓存服务类
 */
class CacheService {
  private cache: Map<string, CacheItem>

  constructor() {
    this.cache = new Map()
  }

  /**
   * 设置缓存项
   * @param key - 缓存键
   * @param data - 缓存数据
   * @param expireTime - 过期时间（毫秒）
   */
  set<T = unknown>(key: string, data: T, expireTime: number = CACHE_CONFIG.EXPIRATION_TIME.MEDIUM): void {
    // 如果缓存已满，删除最早的项
    if (this.cache.size >= CACHE_CONFIG.MAX_CACHE_ITEMS) {
      const firstKey = this.cache.keys().next().value
      if (firstKey) {
        this.cache.delete(firstKey)
      }
    }

    const expireAt = Date.now() + expireTime
    this.cache.set(key, { data, expireAt } as CacheItem)
  }

  /**
   * 获取缓存项
   * @param key - 缓存键
   * @returns 缓存数据或 null（如果缓存不存在或已过期）
   */
  get<T = unknown>(key: string): T | null {
    const item = this.cache.get(key)
    if (!item) {
      return null
    }

    // 检查缓存是否已过期
    if (Date.now() > item.expireAt) {
      this.cache.delete(key)
      return null
    }

    return item.data as T
  }

  /**
   * 删除缓存项
   * @param key - 缓存键
   */
  delete(key: string): void {
    this.cache.delete(key)
  }

  /**
   * 清除所有缓存
   */
  clear(): void {
    this.cache.clear()
  }

  /**
   * 获取缓存大小
   * @returns 缓存项数量
   */
  size(): number {
    return this.cache.size
  }

  /**
   * 预加载常用数据到缓存
   * @param preloadItems - 预加载项数组
   */
  async preloadData<T>(preloadItems: PreloadItem<T>[]): Promise<void> {
    const promises = preloadItems.map(item => {
      return item.loadFunction()
        .then(data => {
          this.set(item.key, data, item.expireTime || CACHE_CONFIG.EXPIRATION_TIME.LONG)
          return data
        })
        .catch(() => {})
    })

    await Promise.all(promises)
  }
}

// 创建单例实例
const cacheService = new CacheService()

// 导出缓存服务实例和配置
export default cacheService
export { CACHE_CONFIG }
export type { CacheItem, PreloadItem, CacheConfig, ExpirationTime }
