import request from '@/utils/request'

export interface StudentMenuItem {
  path: string
  name: string
  icon: string
  visible: boolean
}

interface StoredStudentMenu {
  path: string
  visible?: boolean
}

export const DEFAULT_STUDENT_MENUS: StudentMenuItem[] = [
  { path: '/student/home', name: '首页', icon: 'HomeFilled', visible: true },
  { path: '/student/jobs', name: '职位浏览', icon: 'Briefcase', visible: true },
  { path: '/student/applications', name: '我的申请', icon: 'Document', visible: true },
  { path: '/student/interviews', name: '面试管理', icon: 'ChatDotRound', visible: true },
  { path: '/student/internship-confirmation-form', name: '实习确认', icon: 'DocumentChecked', visible: true },
  { path: '/student/internships', name: '实习心得', icon: 'Document', visible: true },
  { path: '/student/internship-appraisal', name: '实习鉴定', icon: 'DocumentChecked', visible: true },
  { path: '/student/profile', name: '个人中心', icon: 'User', visible: true }
]

const SAFE_STUDENT_PATHS = new Set(['/student/home', '/student/profile'])

let cachedMenus: StudentMenuItem[] | null = null
let pendingMenus: Promise<StudentMenuItem[]> | null = null

function cloneMenus(menus: StudentMenuItem[]): StudentMenuItem[] {
  return menus.map(menu => ({ ...menu }))
}

function getSafeMenus(): StudentMenuItem[] {
  return cloneMenus(DEFAULT_STUDENT_MENUS.filter(menu => SAFE_STUDENT_PATHS.has(menu.path)))
}

export function resolveStudentMenus(configMenus: StoredStudentMenu[]): StudentMenuItem[] {
  if (!Array.isArray(configMenus) || configMenus.length === 0) {
    return getSafeMenus()
  }

  const resolvedMenus: StudentMenuItem[] = []
  for (const configMenu of configMenus) {
    const defaultMenu = DEFAULT_STUDENT_MENUS.find(menu => menu.path === configMenu.path)
    if (defaultMenu) {
      resolvedMenus.push({ ...defaultMenu, visible: configMenu.visible !== false })
    }
  }

  const homeMenu = DEFAULT_STUDENT_MENUS.find(menu => menu.path === '/student/home')
  const resolvedHomeMenu = resolvedMenus.find(menu => menu.path === homeMenu?.path)
  if (resolvedHomeMenu) {
    resolvedHomeMenu.visible = true
  } else if (homeMenu) {
    resolvedMenus.unshift({ ...homeMenu })
  }

  return resolvedMenus.length > 0 ? resolvedMenus : getSafeMenus()
}

export async function loadStudentMenus(): Promise<StudentMenuItem[]> {
  if (cachedMenus) {
    return cloneMenus(cachedMenus)
  }
  if (pendingMenus) {
    return cloneMenus(await pendingMenus)
  }

  pendingMenus = (async () => {
    try {
      const response = await request.get('/student/home/menu-config') as any
      if (!response || response.code !== 200 || typeof response.data !== 'string') {
        return getSafeMenus()
      }

      const parsed = JSON.parse(response.data) as unknown
      return Array.isArray(parsed)
        ? resolveStudentMenus(parsed as StoredStudentMenu[])
        : getSafeMenus()
    } catch (error) {
      console.error('获取学生菜单配置失败:', error)
      return getSafeMenus()
    }
  })()

  try {
    cachedMenus = await pendingMenus
    return cloneMenus(cachedMenus)
  } finally {
    pendingMenus = null
  }
}

export async function canAccessStudentMenu(path: string): Promise<boolean> {
  if (SAFE_STUDENT_PATHS.has(path)) {
    return true
  }
  if (!DEFAULT_STUDENT_MENUS.some(menu => menu.path === path)) {
    return true
  }

  const menus = await loadStudentMenus()
  return menus.some(menu => menu.path === path && menu.visible !== false)
}

export function resetStudentMenuConfigCache(): void {
  cachedMenus = null
  pendingMenus = null
}
