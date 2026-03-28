import request from '@/utils/request'

// 获取单位变更申请状态
export function getUnitChangeStatus() {
  return request({
    url: '/student/internship-confirmation/unit-change/status',
    method: 'get'
  })
}

// 提交单位变更申请
export function submitUnitChange(data: {
  newCompany: string
  reason: string
  materials: Record<string, string>
}) {
  return request({
    url: '/student/internship-confirmation/unit-change/submit',
    method: 'post',
    data
  })
}

// 再次申请（更新被驳回的申请）
export function resubmitUnitChange(id: number, data: {
  newCompany: string
  reason: string
  materials: Record<string, string>
}) {
  return request({
    url: `/student/internship-confirmation/unit-change/resubmit/${id}`,
    method: 'put',
    data
  })
}
