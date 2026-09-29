export const studentApplicationStatusText = {
  pending: '等待教师审核',
  approved: '教师已通过',
  rejected: '教师已驳回',
  withdrawn: '你已撤回'
}

export function latestStudentApplication(rows, type) {
  if (!Array.isArray(rows)) return null
  return rows
    .filter((row) => row?.applicationType === type)
    .sort((a, b) => {
      const at = new Date(a?.applyTime || a?.createTime || a?.updateTime || a?.reviewTime || 0).getTime() || 0
      const bt = new Date(b?.applyTime || b?.createTime || b?.updateTime || b?.reviewTime || 0).getTime() || 0
      return bt - at || Number(b?.id || 0) - Number(a?.id || 0)
    })[0] || null
}

export function studentApplicationDialogMode(status) {
  return status === 'pending' || status === 'approved' ? 'result' : 'form'
}
