import request from '../utils/request'

export interface EvaluationStudent {
  id: number
  name: string
  studentId: string
  grade: string
  className: string
  majorName: string
  department: string
  company: string
  reportCount: number
  hasReports: boolean
  hasCurrentPeriodReport: boolean
  hasAIAnalysis: boolean
  aiAnalysis: boolean
  isEvaluated: boolean
  reports?: Report[]
  aiAnalysisData?: AIAnalysisInfo
  evaluation?: EvaluationInfo
}

export interface PeriodInfo {
  period: number
  periodName: string
  startDate: string
  endDate: string
}

export interface Report {
  id: number
  date: string
  content: string
  periodNumber?: number  // 阶段编号
  isDraft?: boolean  // 是否为草稿状态
}

export interface AIAnalysisInfo {
  overall: string
  keywords: string[]
  sentiment: {
    positive: number
    neutral: number
    negative: number
  }
  suggestedScores: {
    scores?: Record<string, number>
    attitude: number
    performance: number
    report: number
  }
}

export interface EvaluationInfo {
  scores?: Record<string, number>
  attitude: number
  performance: number
  report: number
  companyEvaluation: number
  comment: string
  totalScore: number
  grade: string
}

export interface EvaluationSubmitParams {
  studentId: number
  attitude: number
  performance: number
  report: number
  companyEvaluation: number
  comment: string
}

export interface EvaluationStatistics {
  total: number
  excellent: number
  good: number
  medium: number
  pass: number
  fail: number
}

export const evaluationApi = {
  getPeriods: async () => {
    try {
      const response = await request.get<{ code: number; message: string; data: PeriodInfo[] }>('/evaluation/periods')
      return (response as any).data
    } catch (error) {
      throw new Error(`Failed to fetch periods: ${error}`)
    }
  },

  getStudentList: async (params?: {
    period?: string
    className?: string
    name?: string
    studentId?: string
    hasReport?: string
    isEvaluated?: string
    department?: string
    majorName?: string
  }) => {
    try {
      const response = await request.get<{ code: number; message: string; data: EvaluationStudent[] }>('/evaluation/students', { params })
      return (response as any).data
    } catch (error) {
      throw new Error(`Failed to fetch student list: ${error}`)
    }
  },

  getStudentDetail: async (studentId: number, period?: string) => {
    try {
      const url = period ? `/evaluation/student/${studentId}?period=${period}` : `/evaluation/student/${studentId}`
      const response = await request.get<{ code: number; message: string; data: EvaluationStudent }>(url)
      return (response as any).data
    } catch (error) {
      throw new Error(`Failed to fetch student detail ${studentId}: ${error}`)
    }
  },

  submitEvaluation: async (params: EvaluationSubmitParams) => {
    try {
      await request.post('/evaluation/submit', params)
    } catch (error) {
      throw new Error(`Failed to submit evaluation: ${error}`)
    }
  },

  getStatistics: async () => {
    try {
      const response = await request.get<{ code: number; message: string; data: EvaluationStatistics }>('/evaluation/statistics')
      return (response as any).data
    } catch (error) {
      throw new Error(`Failed to fetch evaluation statistics: ${error}`)
    }
  }
}
