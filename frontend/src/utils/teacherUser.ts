interface UserInfo {
  id?: number
  username?: string
  name?: string
  role?: string
  teacherType?: string
}

export const getCurrentUser = (): UserInfo => {
  const userInfoStr = localStorage.getItem('userInfo')
  if (userInfoStr) {
    try {
      return JSON.parse(userInfoStr)
    } catch (e) {
      console.error('解析用户信息失败:', e)
    }
  }
  return { id: 6, name: '张老师', role: 'ROLE_TEACHER', teacherType: 'COLLEGE' }
}

export const setCurrentUser = (userInfo: UserInfo) => {
  localStorage.setItem('userInfo', JSON.stringify(userInfo))
}

export const getUserName = (): string => {
  const user = getCurrentUser()
  return user.name || '系统管理员'
}

export const getUserRole = (): string => {
  const user = getCurrentUser()
  const role = user.role || 'ROLE_TEACHER'
  return role.replace('ROLE_', '')
}

export const getUserTeacherType = (): string => {
  const user = getCurrentUser()
  return user.teacherType || 'COLLEGE'
}

export const getUserId = (): number => {
  const user = getCurrentUser()
  return user.id || 0
}
