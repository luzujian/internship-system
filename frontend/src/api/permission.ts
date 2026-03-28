import request from '@/utils/request'

export const clearRolePermissions = () => {
  return request.delete('/admin/permissions/clear')
}
