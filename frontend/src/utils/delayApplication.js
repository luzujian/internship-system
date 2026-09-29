export const delayApplicationStatusText = {
  pending: '等待教师审核',
  approved: '教师已通过',
  rejected: '教师已驳回',
  withdrawn: '你已撤回'
}

export function latestDelayApplication(rows) {
  if (!Array.isArray(rows)) return null
  return rows
    .filter((row) => row?.applicationType === 'delay')
    .sort((a, b) => {
      const at = new Date(a?.applyTime || a?.createTime || a?.updateTime || 0).getTime() || 0
      const bt = new Date(b?.applyTime || b?.createTime || b?.updateTime || 0).getTime() || 0
      return bt - at || Number(b?.id || 0) - Number(a?.id || 0)
    })[0] || null
}

export function delayDialogMode(status) {
  return status === 'pending' || status === 'approved' ? 'result' : 'form'
}

export function canResubmitDelay(status) {
  return status === 'rejected' || status === 'withdrawn'
}

export function delayFormFromApplication(application) {
  return {
    reason: application?.reason || '',
    materialUrl: ''
  }
}

export function delayFileListFromApplication(application) {
  return application?.materials?.delayPlan
    ? [{ name: '原考研计划', status: 'success' }]
    : []
}
