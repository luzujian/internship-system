import request from '../utils/request'

export function saveEvaluation(data: any) {
  return request({
    url: '/counselor/evaluation',
    method: 'post',
    data
  })
}

export function getEvaluationById(id: number) {
  return request({
    url: `/counselor/evaluation/${id}`,
    method: 'get'
  })
}

export function getEvaluationByReflectionId(reflectionId: number) {
  return request({
    url: `/counselor/evaluation/reflection/${reflectionId}`,
    method: 'get'
  })
}

export function getEvaluationByStudentId(studentId: number) {
  return request({
    url: `/counselor/evaluation/student/${studentId}`,
    method: 'get'
  })
}

export function getEvaluationsByCounselorId(counselorId: number) {
  return request({
    url: `/counselor/evaluation/counselor/${counselorId}`,
    method: 'get'
  })
}

export function updateEvaluation(id: number, data: any) {
  return request({
    url: `/counselor/evaluation/${id}`,
    method: 'put',
    data
  })
}

export function deleteEvaluation(id: number) {
  return request({
    url: `/counselor/evaluation/${id}`,
    method: 'delete'
  })
}

export function calculateTotalScore(data: any) {
  return request({
    url: '/counselor/evaluation/calculate',
    method: 'post',
    data
  })
}
