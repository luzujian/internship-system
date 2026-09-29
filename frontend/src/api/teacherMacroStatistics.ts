import request from '../utils/request'

export interface TeacherMacroStatistics {
  withoutOfferStudentCount: number
  totalRemainingQuota: number
}

export const getTeacherMacroStatistics = async (): Promise<TeacherMacroStatistics> => {
  const response = await request.get<{ code: number; message: string; data: TeacherMacroStatistics }>(
    '/teacher/macro-statistics'
  )
  return (response as unknown as { data: TeacherMacroStatistics }).data
}
