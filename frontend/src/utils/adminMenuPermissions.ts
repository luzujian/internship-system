export const ADMIN_MENU_PERMISSIONS: Record<string, string> = {
  '/admin/teacher-users': 'user:teacher:view',
  '/admin/student-users': 'user:student:view',
  '/admin/companies': 'user:company:view',
  '/admin/positions': 'position:view',
  '/admin/position-categories': 'position-category:view',
  '/admin/classes': 'class:view',
  '/admin/majors': 'major:view',
  '/admin/departments': 'department:view',
  '/admin/announcements': 'announcement:view',
  '/admin/resource-documents': 'resource:view',
  '/admin/problem-feedback': 'problem:view',
  '/admin/data-statistics/active-statistics': 'statistics:view',
  '/admin/logs': 'log:view',
  '/admin/permissions': 'permission:view',
  '/admin/backup': 'backup:view',
  '/admin/system-settings': 'system:settings:view',
  '/admin/keyword-library': 'keyword:view',
  '/admin/ai-model': 'ai:model:view',
  '/admin/ai-test': 'ai:test:view',
  '/admin/scoring-rule': 'scoring:view',
  '/admin/companies/audit': 'user:company:audit',
  '/admin/application-withdrawal-records': 'application:withdrawal:view',
  '/admin/internship-confirm-application': 'internship:confirm:view',
  '/admin/recruitment': 'recruitment:view'
}

export const ADMIN_LOG_VIEW_PERMISSIONS = ['log:view', 'log:login:view', 'log:security:view']

export function getAdminMenuPermission(path: string): string | undefined {
  return ADMIN_MENU_PERMISSIONS[path]
}
