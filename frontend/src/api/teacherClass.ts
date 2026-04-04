import request from '../utils/request'
import logger from '../utils/logger'

export interface TeacherClass {
  id: number
  name: string
  majorId: number
  majorName?: string
  departmentId?: number
  departmentName?: string
  grade: number
  studentCount?: number
  teacherId?: number
  teacherName?: string
  createTime?: string
  updateTime?: string
}

export interface ClassCounselorRelation {
  id: number
  classId: number
  className: string
  counselorId: number
  counselorName: string
  createTime: string
  updateTime: string
}

export const getTeacherClasses = async (counselorId: number): Promise<{ data: TeacherClass[] }> => {
  try {
    const response = await request.get(`/teacher/classes`, { params: { counselorId } })
    return response
  } catch (error) {
    logger.error('获取教师班级列表失败:', error)
    throw error
  }
}

export const getAllClasses = async (): Promise<{ data: TeacherClass[] }> => {
  try {
    const response = await request.get(`/teacher/classes/all`)
    return response
  } catch (error) {
    logger.error('获取所有班级列表失败:', error)
    throw error
  }
}

export const getClassDetail = async (classId: number): Promise<{ data: TeacherClass }> => {
  try {
    const response = await request.get(`/teacher/classes/${classId}`)
    return response
  } catch (error) {
    logger.error('获取班级详情失败:', error)
    throw error
  }
}

export const assignClassesToCounselor = async (counselorId: number, classIds: number[]): Promise<{ data: number }> => {
  try {
    const response = await request.post(`/teacher/classes/assign`, { counselorId, classIds })
    return response
  } catch (error) {
    logger.error('分配班级失败:', error)
    throw error
  }
}

export const unassignClassFromCounselor = async (counselorId: number, classId: number): Promise<{ data: number }> => {
  try {
    const response = await request.delete(`/teacher/classes/unassign`, { data: { counselorId, classId } })
    return response
  } catch (error) {
    logger.error('取消班级关联失败:', error)
    throw error
  }
}

export const getClassStudents = async (classId: number, counselorId: number): Promise<{ data: any[] }> => {
  try {
    const response = await request.get(`/teacher/classes/students/${classId}`, { params: { counselorId } })
    return response
  } catch (error) {
    logger.error('获取班级学生列表失败:', error)
    throw error
  }
}

export const getCounselorStudents = async (
  counselorId: number,
  searchName?: string,
  classId?: number,
  major?: string,
  grade?: string,
  status?: number,
  companyName?: string
): Promise<{ data: any[] }> => {
  try {
    const params: any = {}
    if (searchName) params.searchName = searchName
    if (classId) params.classId = classId
    if (major) params.major = major
    if (grade) params.grade = grade
    if (status !== undefined && status !== null) params.status = status
    if (companyName) params.companyName = companyName
    const response = await request.get(`/teacher/classes/counselor-students/${counselorId}`, { params })
    return response
  } catch (error) {
    logger.error('获取辅导员学生列表失败:', error)
    throw error
  }
}

export const getCounselorRelations = async (counselorId: number): Promise<{ data: ClassCounselorRelation[] }> => {
  try {
    const response = await request.get(`/teacher/classes/relations/${counselorId}`)
    return response
  } catch (error) {
    logger.error('获取班级关联关系失败:', error)
    throw error
  }
}

export const getCounselorStatistics = async (counselorId: number): Promise<{ data: { classCount: number; studentCount: number } }> => {
  try {
    const response = await request.get(`/teacher/classes/statistics/${counselorId}`)
    return response
  } catch (error) {
    logger.error('获取统计数据失败:', error)
    throw error
  }
}
