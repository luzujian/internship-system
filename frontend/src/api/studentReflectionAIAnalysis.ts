import request from '../utils/request'

export function analyzeReflection(data: any) {
  return request({
    url: '/counselor/ai-analysis/analyze',
    method: 'post',
    data
  })
}

export function getAnalysisById(id: number) {
  return request({
    url: `/counselor/ai-analysis/${id}`,
    method: 'get'
  })
}

export function getAnalysisByReflectionId(reflectionId: number) {
  return request({
    url: `/counselor/ai-analysis/reflection/${reflectionId}`,
    method: 'get'
  })
}

export function getAnalysisByStudentId(studentId: number) {
  return request({
    url: `/counselor/ai-analysis/student/${studentId}`,
    method: 'get'
  })
}

export function getAnalysesByCounselorId(counselorId: number) {
  return request({
    url: `/counselor/ai-analysis/counselor/${counselorId}`,
    method: 'get'
  })
}

export function downloadAnalysisReport(id: number) {
  return request({
    url: `/counselor/ai-analysis/download/${id}`,
    method: 'get',
    responseType: 'blob'
  })
}

export function deleteAnalysis(id: number) {
  return request({
    url: `/counselor/ai-analysis/${id}`,
    method: 'delete'
  })
}
