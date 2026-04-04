import request from '../utils/request'
import logger from '../utils/logger'

export interface AIAnalysisResult {
  id: number
  reflectionId: number
  studentId: number
  counselorId: number
  overallAnalysis: string
  keywords: string[]
  sentimentPositive: number
  sentimentNeutral: number
  sentimentNegative: number
  scoreDetails: Record<string, any>
  totalScore: number
  grade: string
  analysisReport: string
  aiModelCode: string
  analysisTime: string
  createTime: string
  updateTime: string
}

export const getAnalysisByReflectionId = async (reflectionId: number): Promise<{ data: AIAnalysisResult }> => {
  try {
    const response = await request.get(`/counselor/ai-analysis/reflection/${reflectionId}`)
    return response
  } catch (error) {
    logger.error('获取AI分析结果失败:', error)
    throw error
  }
}

export const getAnalysisByStudentId = async (studentId: number): Promise<{ data: AIAnalysisResult }> => {
  try {
    const response = await request.get(`/counselor/ai-analysis/student/${studentId}`)
    return response
  } catch (error) {
    logger.error('获取学生AI分析结果失败:', error)
    throw error
  }
}

export const getAnalysesByCounselorId = async (counselorId: number): Promise<{ data: AIAnalysisResult[] }> => {
  try {
    const response = await request.get(`/counselor/ai-analysis/counselor/${counselorId}`)
    return response
  } catch (error) {
    logger.error('获取辅导员AI分析结果列表失败:', error)
    throw error
  }
}

export const triggerAnalysis = async (reflectionId: number, counselorId: number): Promise<{ data: any }> => {
  try {
    const response = await request.post(`/counselor/ai-analysis/trigger/${reflectionId}`, { counselorId })
    return response
  } catch (error) {
    logger.error('触发AI分析失败:', error)
    throw error
  }
}

export const downloadAnalysisReport = async (reflectionId: number): Promise<Blob> => {
  try {
    const response = await request.get(`/counselor/ai-analysis/download/${reflectionId}`, {
      responseType: 'blob'
    })
    return response
  } catch (error) {
    logger.error('下载AI分析报告失败:', error)
    throw error
  }
}

export const checkAiScoringEnabled = async (counselorId: number): Promise<{ data: { aiScoringEnabled: boolean; autoTriggerEnabled: boolean } }> => {
  try {
    const response = await request.get(`/counselor/ai-analysis/check-enabled/${counselorId}`)
    return response
  } catch (error) {
    logger.error('检查AI评分状态失败:', error)
    throw error
  }
}
