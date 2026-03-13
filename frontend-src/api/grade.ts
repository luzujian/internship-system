// 年级/成绩相关的 API 服务
import request from '@/utils/request'

export interface Grade {
  id: number
  name: string
  year?: number
  description?: string
  createTime?: string
  updateTime?: string
}

export interface GradeResponse {
  code: number
  data: Grade | Grade[]
  msg?: string
}

const GradeService = {
  // 根据学生 ID 删除学生的所有成绩
  async deleteGradesByStudentId(studentId: number): Promise<GradeResponse> {
    try {
      const response = await request({
        url: `/admin/grade/student/${studentId}`,
        method: 'delete'
      })
      return response.data as GradeResponse
    } catch (error) {
      console.error('删除学生成绩失败:', error)
      throw error
    }
  },

  // 获取年级列表
  async getGradeList(): Promise<GradeResponse> {
    try {
      const response = await request({
        url: '/admin/grade/list',
        method: 'get'
      })
      return response.data as GradeResponse
    } catch (error) {
      console.error('获取年级列表失败:', error)
      throw error
    }
  },

  // 根据 ID 获取年级信息
  async getGradeById(id: number): Promise<GradeResponse> {
    try {
      const response = await request({
        url: `/admin/grade/${id}`,
        method: 'get'
      })
      return response.data as GradeResponse
    } catch (error) {
      console.error('获取年级信息失败:', error)
      throw error
    }
  },

  // 创建新年级
  async createGrade(gradeData: Omit<Grade, 'id' | 'createTime' | 'updateTime'>): Promise<GradeResponse> {
    try {
      const response = await request({
        url: '/admin/grade',
        method: 'post',
        data: gradeData
      })
      return response.data as GradeResponse
    } catch (error) {
      console.error('创建年级失败:', error)
      throw error
    }
  },

  // 更新年级信息
  async updateGrade(id: number, gradeData: Partial<Omit<Grade, 'id' | 'createTime' | 'updateTime'>>): Promise<GradeResponse> {
    try {
      const response = await request({
        url: `/admin/grade/${id}`,
        method: 'put',
        data: gradeData
      })
      return response.data as GradeResponse
    } catch (error) {
      console.error('更新年级信息失败:', error)
      throw error
    }
  },

  // 删除年级
  async deleteGrade(id: number): Promise<GradeResponse> {
    try {
      const response = await request({
        url: `/admin/grade/${id}`,
        method: 'delete'
      })
      return response.data as GradeResponse
    } catch (error) {
      console.error('删除年级失败:', error)
      throw error
    }
  }
}

export default GradeService
