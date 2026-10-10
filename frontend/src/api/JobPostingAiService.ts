import request from '@/utils/request'

export interface ParsedJobPosting {
  positionName?: string | null
  department?: string | null
  positionType?: string | null
  province?: string | null
  city?: string | null
  district?: string | null
  detailAddress?: string | null
  salaryUnit?: string | null
  salaryMin?: number | string | null
  salaryMax?: number | string | null
  plannedRecruit?: number | null
  requiredMajors?: string[] | null
  description?: string | null
  requirements?: string | null
  internshipEnvironment?: string | null
  providesAccommodation?: number | null
  interviewTime?: string | null
  interviewLocation?: string | null
  interviewMethod?: string | null
  interviewRemark?: string | null
  confidence?: Record<string, number> | null
  warnings?: string[] | null
}

export default {
  parseJobPosting(text: string, signal?: AbortSignal) {
    return request<ParsedJobPosting>({
      url: '/ai/company/parse-job-posting',
      method: 'post',
      data: { text },
      signal
    })
  }
}
