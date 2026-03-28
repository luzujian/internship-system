import request from '../utils/request'

export function getCounselorAISettings(counselorId: number) {
  return request({
    url: `/counselor/ai-settings/${counselorId}`,
    method: 'get'
  })
}

export function saveCounselorAISettings(data: any) {
  return request({
    url: '/counselor/ai-settings',
    method: 'post',
    data
  })
}

export function updateCounselorAISettings(data: any) {
  return request({
    url: '/counselor/ai-settings',
    method: 'put',
    data
  })
}

export function getScoringRules(counselorId: number) {
  return request({
    url: `/counselor/ai-settings/scoring-rules/${counselorId}`,
    method: 'get'
  })
}

export function getScoringRulesByCategory(counselorId: number, category: string) {
  return request({
    url: `/counselor/ai-settings/scoring-rules/${counselorId}/category/${category}`,
    method: 'get'
  })
}

export function getCategories(counselorId: number) {
  return request({
    url: `/counselor/ai-settings/scoring-rules/${counselorId}/categories`,
    method: 'get'
  })
}

export function saveScoringRules(data: any) {
  return request({
    url: '/counselor/ai-settings/scoring-rules',
    method: 'post',
    data
  })
}

export function updateScoringRule(id: number, data: any) {
  return request({
    url: `/counselor/ai-settings/scoring-rules/${id}`,
    method: 'put',
    data
  })
}

export function deleteScoringRule(ruleId: number) {
  return request({
    url: `/counselor/ai-settings/scoring-rules/${ruleId}`,
    method: 'delete'
  })
}

export function deleteScoringRulesByCategory(counselorId: number, category: string) {
  return request({
    url: `/counselor/ai-settings/scoring-rules/${counselorId}/category/${category}`,
    method: 'delete'
  })
}

export function batchCreateScoringRules(counselorId: number, categoryName: string) {
  return request({
    url: '/counselor/ai-settings/scoring-rules/batch-create',
    method: 'post',
    data: {
      counselorId,
      categoryName
    }
  })
}

export function getCategoryWeights(counselorId: number) {
  return request({
    url: `/counselor/ai-settings/category-weight/${counselorId}`,
    method: 'get'
  })
}

export function getActiveCategoryWeights(counselorId: number) {
  return request({
    url: `/counselor/ai-settings/category-weight/${counselorId}/active`,
    method: 'get'
  })
}

export function batchUpdateCategoryWeights(data: any[]) {
  return request({
    url: '/counselor/ai-settings/category-weight/batch',
    method: 'post',
    data
  })
}

export function deleteCategoryWeightByCategoryCode(counselorId: number, categoryCode: string) {
  return request({
    url: `/counselor/ai-settings/category-weight/${counselorId}/category/${categoryCode}`,
    method: 'delete'
  })
}
