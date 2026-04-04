import cacheService from './cacheService'
import ClassService from './class'
import StudentUserService from './StudentUserService'
import TeacherUserService from './TeacherUserService'
import UserService from './user'

// 需要预加载的数据类型
export const PRELOAD_TYPES = {
  CLASS_LIST: 'classList',
  USER_STATS: 'userStats',
  MAJOR_LIST: 'majorList',
  COMMON_CONFIGS: 'commonConfigs'
} as const

export type PreloadType = (typeof PRELOAD_TYPES)[keyof typeof PRELOAD_TYPES]

class PreloadService {
  isPreloading: boolean
  preloadedTypes: Set<PreloadType>
  preloadCallbacks: Map<PreloadType, Array<() => void>>

  constructor() {
    this.isPreloading = false
    this.preloadedTypes = new Set()
    this.preloadCallbacks = new Map()
  }

  /**
   * 预加载所有基础数据
   */
  async preloadAll(): Promise<void> {
    if (this.isPreloading) return Promise.resolve()

    this.isPreloading = true

    try {
      await Promise.all([
        this.preloadClassList(),
        this.preloadCommonUsers()
      ])
    } finally {
      this.isPreloading = false
    }
  }

  /**
   * 预加载班级列表
   */
  async preloadClassList(): Promise<void> {
    if (this.preloadedTypes.has(PRELOAD_TYPES.CLASS_LIST)) {
      return Promise.resolve()
    }

    try {
      await ClassService.getClasses()
      this.preloadedTypes.add('classList' as PreloadType)
      this.notifyCallbacks('classList' as PreloadType)
    } catch {
      // 预加载失败不影响主流程
      console.warn('班级列表预加载失败')
    }
  }

  /**
   * 预加载常用用户信息
   */
  preloadCommonUsers(): Promise<void> {
    // 可以根据实际需求预加载部分常用用户信息
    // 例如最近活跃的管理员或教师
    return Promise.resolve()
  }

  /**
   * 注册预加载完成回调
   */
  onPreloadComplete(type: PreloadType, callback: () => void): void {
    if (!callback || typeof callback !== 'function') return

    if (!this.preloadCallbacks.has(type)) {
      this.preloadCallbacks.set(type, [])
    }

    this.preloadCallbacks.get(type)!.push(callback)

    // 如果已经预加载完成，立即执行回调
    if (this.preloadedTypes.has(type)) {
      setTimeout(() => callback(), 0)
    }
  }

  /**
   * 通知回调函数
   */
  notifyCallbacks(type: PreloadType): void {
    const callbacks = this.preloadCallbacks.get(type)
    if (callbacks) {
      callbacks.forEach(callback => {
        try {
          callback()
        } catch (error) {
          console.error('预加载回调执行失败:', error)
        }
      })
    }
  }

  /**
   * 清除预加载状态
   */
  clearPreloadState(): void {
    this.preloadedTypes.clear()
  }
}

// 创建单例实例
const preloadService = new PreloadService()

export default preloadService
