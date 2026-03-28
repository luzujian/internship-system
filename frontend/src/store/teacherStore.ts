import { reactive, computed } from 'vue'

export interface UserInfo {
  id?: number
  username?: string
  name?: string
  role?: string
  teacherType?: string
  department?: string
  major?: string
  className?: string
  grade?: string
}

export interface AppState {
  user: UserInfo | null
  token: string | null
  isAuthenticated: boolean
  notifications: any[]
  unreadCount: number
  systemSettings: Record<string, any>
}

const state = reactive<AppState>({
  user: null,
  token: localStorage.getItem('token') || null,
  isAuthenticated: false,
  notifications: [],
  unreadCount: 0,
  systemSettings: {}
})

const getters = {
  isLoggedIn: computed(() => !!state.token && !!state.user),
  userName: computed(() => state.user?.name || '系统管理员'),
  userRole: computed(() => {
    const role = state.user?.role || 'ROLE_TEACHER'
    return role.replace('ROLE_', '')
  }),
  userTeacherType: computed(() => state.user?.teacherType || 'COLLEGE'),
  userId: computed(() => state.user?.id || 0)
}

const mutations = {
  setUser(user: UserInfo | null) {
    state.user = user
    if (user) {
      localStorage.setItem('userInfo', JSON.stringify(user))
    } else {
      localStorage.removeItem('userInfo')
    }
    state.isAuthenticated = !!user
  },

  setToken(token: string | null) {
    state.token = token
    if (token) {
      localStorage.setItem('token', token)
    } else {
      localStorage.removeItem('token')
    }
    state.isAuthenticated = !!token
  },

  setNotifications(notifications: any[]) {
    state.notifications = notifications
    state.unreadCount = notifications.filter((n: any) => !n.isRead).length
  },

  addNotification(notification: any) {
    state.notifications.unshift(notification)
    if (!notification.isRead) {
      state.unreadCount++
    }
  },

  markAsRead(notificationId: number) {
    const notification = state.notifications.find((n: any) => n.id === notificationId)
    if (notification && !notification.isRead) {
      notification.isRead = true
      state.unreadCount--
    }
  },

  markAsUnread(notificationId: number) {
    const notification = state.notifications.find((n: any) => n.id === notificationId)
    if (notification && notification.isRead) {
      notification.isRead = false
      state.unreadCount++
    }
  },

  setSystemSettings(settings: Record<string, any>) {
    state.systemSettings = settings
  },

  clearAll() {
    state.user = null
    state.token = null
    state.isAuthenticated = false
    state.notifications = []
    state.unreadCount = 0
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }
}

const actions = {
  initializeFromStorage() {
    const token = localStorage.getItem('token')
    const userInfoStr = localStorage.getItem('userInfo')
    
    if (token) {
      mutations.setToken(token)
    }
    
    if (userInfoStr) {
      try {
        const user = JSON.parse(userInfoStr)
        mutations.setUser(user)
      } catch (e) {
      }
    }
  },

  login(token: string, user: UserInfo) {
    mutations.setToken(token)
    mutations.setUser(user)
  },

  logout() {
    mutations.clearAll()
  },

  async fetchNotifications() {
    try {
      const { homeApi } = await import('../api/teacherHome')
      const response = await homeApi.getHomeStats()
      if (response.announcements) {
        mutations.setNotifications(response.announcements)
      }
    } catch (error) {
      console.error('获取通知失败:', error)
    }
  }
}

export const store = {
  state,
  getters,
  mutations,
  actions
}

export default store
