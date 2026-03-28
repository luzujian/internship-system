import request from '@/utils/request'

export interface Position {
  id: number
  name: string
  companyId?: number
  categoryId?: number
  description?: string
  requirements?: string
  status?: number
  createTime?: string
  updateTime?: string
}

export interface PositionResponse {
  code: number
  data: Position | Position[]
  msg?: string
}

const positionService = {
  getPositions: (params?: Record<string, unknown>) => {
    return request.get<Position[]>('/admin/positions', { params })
  },

  getAllPositions: () => {
    return request.get<Position[]>('/admin/positions/all')
  },

  getPositionById: (id: number) => {
    return request.get<Position>(`/admin/positions/${id}`)
  },

  addPosition: (position: Omit<Position, 'id' | 'createTime' | 'updateTime'>) => {
    return request.post('/admin/positions', position)
  },

  updatePosition: (id: number, position: Partial<Position>) => {
    return request.put(`/admin/positions/${id}`, position)
  },

  deletePosition: (id: number) => {
    return request.delete(`/admin/positions/${id}`)
  },

  getStatistics: () => {
    return request.get('/admin/positions/statistics')
  },

  getPositionStudents: (id: number) => {
    return request.get(`/admin/positions/${id}/students`)
  },

  getPositionsByCompanyId: (companyId: number) => {
    return request.get<Position[]>(`/admin/positions/company/${companyId}`)
  },

  clearAllPositions: () => {
    return request.delete('/admin/positions/clear')
  }
}

export default positionService
