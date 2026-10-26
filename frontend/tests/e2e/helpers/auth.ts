/**
 * E2E 测试共享认证与 API Mock 夹具
 *
 * 设计目标：
 * - authenticateAs(student | company | teacher | admin)：统一伪 JWT + localStorage
 *   注入，配合 authGuard 的 restoreAuthState 正常流程恢复认证状态，
 *   不使用 VITE_SKIP_AUTH_CHECK
 * - mockApis：拦截所有 /api/ 请求返回 Mock 数据，禁用真实后端调用
 * - freezeDate / mockWebSocket：按需固定时间、模拟 WebSocket
 *
 * 注意：addInitScript 的注入函数会被序列化到浏览器上下文执行，
 * 不能引用外部闭包变量，所有数据必须通过参数传入。
 */
import type { Page } from '@playwright/test'

export type TestRole = 'student' | 'company' | 'teacher' | 'admin'

/** JWT 过期时间：2030-01-01（远未来，即使 Date 被固定到 2026 也不会过期） */
export const JWT_FUTURE_EXP = 1893456000

export const TEACHER_PERMISSIONS = [
  'menu:home', 'menu:announcements', 'menu:dashboard', 'menu:student-tracking',
  'menu:resources', 'menu:reports', 'menu:account-settings', 'menu:approval',
  'menu:evaluation', 'menu:settings', 'menu:company-list', 'menu:recruitment',
  'menu:classes', 'menu:appraisal-collection', 'menu:teacherAiScoringConfig'
]

interface RoleConfig {
  role: string
  prefix: string
}

const ROLE_CONFIG: Record<TestRole, RoleConfig> = {
  student: { role: 'ROLE_STUDENT', prefix: 'student_' },
  teacher: { role: 'ROLE_TEACHER_COLLEGE', prefix: 'teacher_' },
  admin: { role: 'ROLE_ADMIN', prefix: 'admin_' },
  company: { role: 'ROLE_COMPANY', prefix: 'company_' }
}

export const DEFAULT_USERS: Record<TestRole, { userId: string; username: string; name: string }> = {
  student: { userId: '1', username: 'teststudent', name: '测试学生' },
  company: { userId: '2', username: 'testcompany', name: '测试企业' },
  teacher: { userId: '3', username: 'testteacher', name: '测试教师' },
  admin: { userId: '4', username: 'testadmin', name: '测试管理员' }
}

/**
 * 以指定角色认证。
 *
 * 注入内容与 src/store/auth/roles.ts 的 saveRoleInfoToStorage 对齐：
 * - current_role + {prefix}{token|accessToken|refreshToken|role|userId|username|name|isReadOnly}_{role}
 * - 伪 JWT payload 含 userId/username/name，与 token.ts 的 decodeToken 兼容
 *   （UTF-8 → binary → btoa → base64url，decodeURIComponent(atob(...)) 的逆过程）
 * - authGuard 走正常 restoreAuthState 流程：读取 accessToken → 未过期 →
 *   allUsers[role] = buildUserFromStorage(...) → isAuthenticated = true
 *
 * options.mockWebSocket：替换 window.WebSocket 为可控 Mock（实例收集在
 * window.__testWSInstances，便于测试中触发 onmessage）
 */
export async function authenticateAs(
  page: Page,
  role: TestRole,
  options: { mockWebSocket?: boolean; user?: Partial<{ userId: string; username: string; name: string }>; adminPermissionCodes?: string[] } = {}
) {
  const roleConfig = ROLE_CONFIG[role]
  const user = { ...DEFAULT_USERS[role], ...options.user }

  await page.addInitScript(({ roleConfig, user, exp, mockWebSocket, teacherPermissions, adminPermissionCodes }) => {
    const makeJwt = (payload: any) => {
      const header = { alg: 'HS256', typ: 'JWT' }
      // btoa 不支持非 ASCII 字符（如中文），需先转 UTF-8 字节再 base64
      const enc = (obj: any) => {
        const json = JSON.stringify(obj)
        const bytes = new TextEncoder().encode(json)
        let binary = ''
        for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i])
        return (window as any).btoa(binary)
          .replace(/=/g, '')
          .replace(/\+/g, '-')
          .replace(/\//g, '_')
      }
      return enc(header) + '.' + enc(payload) + '.sig'
    }

    const jwt = makeJwt({
      exp,
      jti: 'test-jti',
      typ: 'access',
      userId: user.userId,
      username: user.username,
      name: user.name,
      sub: user.userId,
      role: roleConfig.role,
      ...(roleConfig.role.startsWith('ROLE_TEACHER') ? { teacherType: 'COLLEGE', permissions: teacherPermissions } : {})
    })

    localStorage.setItem('current_role', roleConfig.role)
    localStorage.setItem(roleConfig.prefix + 'token_' + roleConfig.role, jwt)
    localStorage.setItem(roleConfig.prefix + 'accessToken_' + roleConfig.role, jwt)
    localStorage.setItem(roleConfig.prefix + 'refreshToken_' + roleConfig.role, 'refresh-token')
    localStorage.setItem(roleConfig.prefix + 'role_' + roleConfig.role, roleConfig.role)
    localStorage.setItem(roleConfig.prefix + 'userId_' + roleConfig.role, user.userId)
    localStorage.setItem(roleConfig.prefix + 'username_' + roleConfig.role, user.username)
    localStorage.setItem(roleConfig.prefix + 'name_' + roleConfig.role, user.name)
    localStorage.setItem(roleConfig.prefix + 'isReadOnly_' + roleConfig.role, 'false')

    // 学生扩展字段（StudentLayout / Profile 等页面依赖）
    if (roleConfig.role === 'ROLE_STUDENT') {
      localStorage.setItem('student_studentId_ROLE_STUDENT', user.userId)
    }

    // Authenticated teacher routes read current-user/JWT permissions. Legacy keys
    // are retained only for page compatibility; they do not bypass route guards.
    if (roleConfig.role.startsWith('ROLE_TEACHER')) {
      localStorage.setItem('teacherType', 'COLLEGE')
      localStorage.setItem('teacherPermissions', JSON.stringify(teacherPermissions))
    }

    // 管理员权限注入：permissionGuard 检查 meta.permission →
    // authStore.hasPermission 读取 localStorage['adminPermissions']
    // （{ permissionCode } 数组），未注入时受控路由会被弹回仪表盘。
    // 权限码全集与 src/utils/adminMenuPermissions.ts 的
    // ADMIN_MENU_PERMISSIONS 值对齐。
    if (roleConfig.role === 'ROLE_ADMIN') {
      localStorage.setItem('adminPermissions', JSON.stringify((adminPermissionCodes || [
        'user:teacher:view',
        'user:student:view',
        'user:company:view',
        'position:view',
        'position-category:view',
        'class:view',
        'major:view',
        'department:view',
        'announcement:view',
        'resource:view',
        'problem:view',
        'statistics:view',
        'log:view',
        'permission:view',
        'backup:view',
        'system:settings:view',
        'keyword:view',
        'ai:model:view',
        'ai:test:view',
        'scoring:view',
        'user:company:audit',
        'application:withdrawal:view',
        'internship:confirm:view',
        'recruitment:view'
      ]).map((permissionCode) => ({ permissionCode }))))
    }

    if (mockWebSocket) {
      ;(window as any).__testWSInstances = []
      class MockWebSocket {
        static CONNECTING = 0
        static OPEN = 1
        static CLOSING = 2
        static CLOSED = 3
        CONNECTING = 0
        OPEN = 1
        CLOSING = 2
        CLOSED = 3
        readyState = 1
        onopen: any = null
        onmessage: any = null
        onclose: any = null
        onerror: any = null
        url = ''
        binaryType = 'blob'
        bufferedAmount = 0
        extensions = ''
        protocol = ''
        constructor(url: string) {
          this.url = url
          ;(window as any).__testWSInstances.push(this)
          setTimeout(() => {
            if (typeof this.onopen === 'function') this.onopen({})
          }, 0)
        }
        send() {}
        close() {
          if (typeof this.onclose === 'function') this.onclose({ code: 1000, reason: 'mock' })
        }
        addEventListener() {}
        removeEventListener() {}
        dispatchEvent() { return true }
      }
      ;(window as any).WebSocket = MockWebSocket
    }
  }, { roleConfig, user, exp: JWT_FUTURE_EXP, mockWebSocket: options.mockWebSocket ?? true, teacherPermissions: TEACHER_PERMISSIONS, adminPermissionCodes: options.adminPermissionCodes })
}

/**
 * 固定 window.Date，让「现在」始终为指定 ISO 时间。
 * 用于让 fixture 中的日期产生稳定的分组标签（今天/昨天等）。
 */
export async function freezeDate(page: Page, iso: string) {
  await page.addInitScript((fixedNow) => {
    const RealDate = (window as any).Date
    class MockDate extends RealDate {
      constructor(...args: any[]) {
        if (args.length === 0) {
          super(fixedNow)
        } else {
          super(...args as any[])
        }
      }
      static now() { return fixedNow }
    }
    ;(window as any).Date = MockDate
  }, new Date(iso).getTime())
}

export type ApiMockHandler = any | ((url: string, callIndex: number) => any)

/**
 * 拦截所有真实 API 请求（pathname 以 /api/ 开头），禁用真实后端调用。
 *
 * handlers 的键为 URL 子串匹配模式，按插入顺序匹配（更具体的模式放前面，
 * 如 '/student/job-applications' 必须在 '/student/applications' 之前）。
 * - 值为普通对象/数组 → 响应体为 { code: 200, data: value }
 * - 值为函数 (url, callIndex) => body → 响应体为函数返回值（含 code/data 的
 *   完整响应体，callIndex 从 1 开始按同 pattern 命中次数递增）
 * - 未命中的请求统一返回 { code: 200, data: null }
 *
 * 注意：不能用 glob 通配符匹配 api 路径，否则会匹配到 Vite 的源文件请求
 * （如 /src/api/StudentUserService.ts），导致模块加载失败。
 */
export async function mockApis(page: Page, handlers: Record<string, ApiMockHandler> = {}) {
  // 学生菜单权限接口兜底 mock：路由 beforeEnter 守卫 canAccessStudentMenu 依赖
  // /student/home/menu-config（fail-closed），未 mock 时受控路由会被弹回首页。
  // 测试用户默认拥有全部默认菜单权限；显式传入同键 handler 可覆盖。
  // 注意 loadStudentMenus 期望 data 为 JSON 字符串而非对象。
  const STUDENT_MENU_CONFIG_MOCK: [string, ApiMockHandler] = [
    '/student/home/menu-config',
    () => ({
      code: 200,
      data: JSON.stringify(
        [
          '/student/home',
          '/student/jobs',
          '/student/applications',
          '/student/interviews',
          '/student/internship-confirmation-form',
          '/student/internship-registration',
          '/student/internships',
          '/student/internship-appraisal',
          '/student/profile'
        ].map((path) => ({ path, visible: true }))
      )
    })
  ]
  const entries = [...Object.entries(handlers), STUDENT_MENU_CONFIG_MOCK]
  const callCounts: Record<string, number> = {}

  await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
    const url = route.request().url()

    for (const [pattern, handler] of entries) {
      if (url.includes(pattern)) {
        callCounts[pattern] = (callCounts[pattern] || 0) + 1
        const body = typeof handler === 'function'
          ? handler(url, callCounts[pattern])
          : { code: 200, data: handler }
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(body)
        })
        return
      }
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, data: null })
    })
  })
}
