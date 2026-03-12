import request from '@/utils/request'

export const deleteAiAuditByType = (auditType: string) => {
  return request.delete(`/admin/ai-audit/by-type/${auditType}`)
}

export const deleteAllAiAudit = () => {
  return request.delete('/admin/ai-audit/all')
}
