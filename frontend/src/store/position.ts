import { defineStore } from 'pinia'
import { ref, computed, ComputedRef } from 'vue'
import positionApi from '@/api/PositionService'
import applicationApi from '@/api/InternshipApplicationService'
import internshipStatusApi from '@/api/InternshipStatusService'
import majorApi from '@/api/MajorService'

export interface Position {
  id: number
  positionName: string
  department: string
  workLocation: string
  salaryMin: number | null
  salaryMax: number | null
  plannedRecruit: number
  recruitedCount: number
  remainingQuota: number
  status: string
  publishDate: string
  province: string
  city: string
  district: string
  detailAddress: string
  description: string
  requirements: string
  positionType: string
  internshipStartDate: string | null
  internshipEndDate: string | null
}

export interface Application {
  id: number
  studentDbId: number
  studentName: string
  studentId: string
  major: string
  phone: string
  position: string
  applyDate: string
  status: string
  viewed: boolean
  email: string
  gender: string
  grade: string
  school: string
  education: string
  skills: string[]
  experience: string
  selfEvaluation: string
}

export interface InternshipStatus {
  id: number
  studentId: number
  studentName: string
  studentUserId: string
  gender: string
  grade: string
  majorId: string
  majorName: string
  classId: string
  companyId: number
  companyName: string
  positionId: number
  positionName: string
  status: number
  companyConfirmStatus: number
  internshipStartTime: string
  internshipEndTime: string
  internshipDuration: number
  remark: string
  feedback: string
  createTime: string
  updateTime: string
}

export interface PositionGroup {
  position: string
  applications: Application[]
  total: number
  unviewed: number
}

export const usePositionStore = defineStore('position', () => {
  const positions = ref<Position[]>([])
  const applications = ref<Application[]>([])
  const internshipStatuses = ref<InternshipStatus[]>([])
  const loading = ref(false)

  const fetchPositions = async (companyId: number) => {
    loading.value = true
    try {
      const response = await positionApi.getPositionsByCompanyId(companyId)
      if (response.data?.code === 200) {
        positions.value = response.data.data.map((item: any) => ({
          id: item.id,
          positionName: item.positionName,
          department: item.department || '',
          workLocation: formatWorkLocation(item),
          salaryMin: item.salaryMin || null,
          salaryMax: item.salaryMax || null,
          plannedRecruit: item.plannedRecruit || 0,
          recruitedCount: item.recruitedCount || 0,
          remainingQuota: item.remainingQuota || 0,
          status: item.status || 'active',
          publishDate: formatDate(item.publishDate || item.createTime),
          province: item.province || '',
          city: item.city || '',
          district: item.district || '',
          detailAddress: item.detailAddress || '',
          description: item.description || '',
          requirements: item.requirements || '',
          positionType: item.positionType || '',
          internshipStartDate: item.internshipStartDate || '',
          internshipEndDate: item.internshipEndDate || ''
        }))
        syncRecruitedCountFromInternshipStatuses()
      }
    } catch (error) {
      console.error('获取岗位列表失败:', error)
    } finally {
      loading.value = false
    }
  }

  const syncRecruitedCountFromInternshipStatuses = () => {
    positions.value.forEach(position => {
      const confirmedStatuses = internshipStatuses.value.filter(
        status => status.positionId === position.id && status.companyConfirmStatus === 1
      )
      position.recruitedCount = confirmedStatuses.length
      position.remainingQuota = position.plannedRecruit - confirmedStatuses.length
    })
  }

  const formatWorkLocation = (item: any): string => {
    const parts: string[] = []
    if (item.province) parts.push(item.province)
    if (item.city) parts.push(item.city)
    if (item.district) parts.push(item.district)
    if (item.detailAddress) parts.push(item.detailAddress)
    return parts.join('')
  }

  const formatSalaryRange = (item: any): string => {
    if (item.salaryMin !== null && item.salaryMax !== null) {
      return `${item.salaryMin}K-${item.salaryMax}K`
    }
    return '面议'
  }

  const formatDate = (date: string | null): string => {
    if (!date) return ''
    const d = new Date(date)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  }

  const formatDateToISO = (date: Date | string): string => {
    if (!date) return ''
    const d = new Date(date)
    const year = d.getFullYear()
    const month = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const hours = String(d.getHours()).padStart(2, '0')
    const minutes = String(d.getMinutes()).padStart(2, '0')
    const seconds = String(d.getSeconds()).padStart(2, '0')
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
  }

  const addPosition = async (companyId: number, positionData: Partial<Position>): Promise<boolean> => {
    loading.value = true
    try {
      if (!positionData.positionName || positionData.positionName.trim().length < 2) {
        console.error('岗位名称不能为空且长度不能少于 2 个字符')
        return false
      }

      const data = {
        companyId: companyId,
        positionName: positionData.positionName.trim(),
        department: positionData.department || '',
        positionType: positionData.positionType || '',
        province: positionData.province || '',
        city: positionData.city || '',
        district: positionData.district || '',
        detailAddress: positionData.detailAddress || '',
        salaryMin: positionData.salaryMin && positionData.salaryMin !== '' ? parseInt(String(positionData.salaryMin)) : 0,
        salaryMax: positionData.salaryMax && positionData.salaryMax !== '' ? parseInt(String(positionData.salaryMax)) : 0,
        description: positionData.description || '',
        requirements: positionData.requirements || '',
        internshipStartDate: positionData.internshipStartDate || null,
        internshipEndDate: positionData.internshipEndDate || null,
        plannedRecruit: positionData.plannedRecruit ? parseInt(String(positionData.plannedRecruit)) : 1,
        recruitedCount: 0,
        remainingQuota: positionData.plannedRecruit ? parseInt(String(positionData.plannedRecruit)) : 1,
        status: positionData.status || 'active',
        publishDate: formatDateToISO(new Date())
      }
      console.log('提交的岗位数据:', data)
      const response = await positionApi.createPosition(data)
      console.log('创建岗位响应:', response)
      if (response.data?.code === 200) {
        await fetchPositions(companyId)
        return true
      }
      return false
    } catch (error) {
      console.error('添加岗位失败:', error)
      return false
    } finally {
      loading.value = false
    }
  }

  const updatePosition = async (companyId: number, positionId: number, positionData: Partial<Position>): Promise<boolean> => {
    loading.value = true
    try {
      const data = {
        positionName: positionData.positionName,
        department: positionData.department,
        positionType: positionData.positionType,
        province: positionData.province,
        city: positionData.city,
        district: positionData.district,
        detailAddress: positionData.detailAddress,
        salaryMin: positionData.salaryMin && positionData.salaryMin !== '' ? parseInt(String(positionData.salaryMin)) : null,
        salaryMax: positionData.salaryMax && positionData.salaryMax !== '' ? parseInt(String(positionData.salaryMax)) : null,
        description: positionData.description,
        requirements: positionData.requirements,
        internshipStartDate: positionData.internshipStartDate || null,
        internshipEndDate: positionData.internshipEndDate || null,
        plannedRecruit: positionData.plannedRecruit
      }
      const response = await positionApi.updatePosition(positionId, data)
      if (response.data?.code === 200) {
        await fetchPositions(companyId)
        return true
      }
      return false
    } catch (error) {
      console.error('更新岗位失败:', error)
      return false
    } finally {
      loading.value = false
    }
  }

  const deletePosition = async (companyId: number, positionId: number): Promise<boolean> => {
    loading.value = true
    try {
      const response = await positionApi.deletePosition(positionId)
      if (response.data?.code === 200) {
        await fetchPositions(companyId)
        return true
      }
      return false
    } catch (error) {
      console.error('删除岗位失败:', error)
      return false
    } finally {
      loading.value = false
    }
  }

  const pausePosition = async (companyId: number, positionId: number): Promise<boolean> => {
    loading.value = true
    try {
      const response = await positionApi.pausePosition(positionId)
      if (response.data?.code === 200) {
        await fetchPositions(companyId)
        return true
      }
      return false
    } catch (error) {
      console.error('暂停岗位失败:', error)
      return false
    } finally {
      loading.value = false
    }
  }

  const resumePosition = async (companyId: number, positionId: number): Promise<boolean> => {
    loading.value = true
    try {
      const response = await positionApi.resumePosition(positionId)
      if (response.data?.code === 200) {
        await fetchPositions(companyId)
        return true
      }
      return false
    } catch (error) {
      console.error('恢复岗位失败:', error)
      return false
    } finally {
      loading.value = false
    }
  }

  const totalPlanCount: ComputedRef<number> = computed(() => {
    return positions.value.reduce((sum, item) => sum + (item.plannedRecruit || 0), 0)
  })

  const totalConfirmedCount: ComputedRef<number> = computed(() => {
    return positions.value.reduce((sum, item) => sum + (item.recruitedCount || 0), 0)
  })

  const totalGap: ComputedRef<number> = computed(() => {
    return positions.value.reduce((sum, item) => sum + (item.remainingQuota || 0), 0)
  })

  const activePositionCount: ComputedRef<number> = computed(() => {
    return positions.value.filter(item => item.status === 'active').length
  })

  const fetchApplications = async (companyId: number) => {
    loading.value = true
    try {
      const response = await applicationApi.getApplications(companyId)
      if (response.data?.code === 200) {
        applications.value = response.data.data.map((item: any) => ({
          id: item.id,
          studentDbId: item.studentId,
          studentName: item.studentName || '',
          studentId: item.studentUserId || '',
          major: item.major || '',
          phone: item.phone || '',
          position: item.positionName || '',
          applyDate: formatDate(item.applyTime || item.createTime),
          status: item.applyStatus || 'pending',
          viewed: item.viewed || false,
          email: item.email || '',
          gender: item.gender === '1' ? '男' : item.gender === '2' ? '女' : '',
          grade: item.grade ? item.grade + '级' : '',
          school: item.school || '',
          education: item.education || '',
          skills: item.skills
            ? Array.isArray(item.skills)
              ? item.skills
              : item.skills.split(/[,，、]/).filter((s: string) => s.trim())
            : [],
          experience: item.experience || '',
          selfEvaluation: item.selfEvaluation || ''
        }))
        syncRecruitedCountFromInternshipStatuses()
      }
    } catch (error) {
      console.error('获取申请列表失败:', error)
    } finally {
      loading.value = false
    }
  }

  const getApplicationsByPosition = (positionName?: string): PositionGroup[] => {
    const groups: Record<string, Application[]> = {}
    applications.value.forEach(item => {
      if (!groups[item.position]) {
        groups[item.position] = []
      }
      groups[item.position].push(item)
    })
    const result: PositionGroup[] = Object.entries(groups).map(([position, apps]) => ({
      position,
      applications: apps,
      total: apps.length,
      unviewed: apps.filter(a => !a.viewed).length
    }))
    if (positionName) {
      return result.filter(item => item.position === positionName)
    }
    return result
  }

  const getApplicationsByStatus = (status: string): Application[] => {
    return applications.value.filter(app => app.status === status)
  }

  const updateApplicationStatus = async (applicationId: number, newStatus: string): Promise<boolean> => {
    try {
      await applicationApi.updateApplyStatus(applicationId, newStatus)
      const application = applications.value.find(app => app.id === applicationId)
      if (application) {
        application.status = newStatus
        syncRecruitedCountFromInternshipStatuses()
      }
      return true
    } catch (error) {
      console.error('更新申请状态失败:', error)
      return false
    }
  }

  const addApplication = async (applicationData: Partial<Application>): Promise<boolean> => {
    try {
      await applicationApi.addApplication(applicationData)
      return true
    } catch (error) {
      console.error('添加申请失败:', error)
      return false
    }
  }

  const markAsViewed = async (applicationId: number) => {
    try {
      await applicationApi.markAsViewed(applicationId)
      const application = applications.value.find(app => app.id === applicationId)
      if (application) {
        application.viewed = true
      }
    } catch (error) {
      console.error('标记为已查看失败:', error)
    }
  }

  const fetchInternshipStatuses = async (companyId: number) => {
    loading.value = true
    try {
      const [internshipStatusResponse, majorsResponse] = await Promise.all([
        internshipStatusApi.getInternshipStatusByCompanyId(companyId),
        majorApi.getAllMajors()
      ])

      const majorsMap: Record<number, string> = {}
      if (majorsResponse.data?.code === 200 && majorsResponse.data.data) {
        majorsResponse.data.data.forEach((major: any) => {
          majorsMap[major.id] = major.name
        })
      }

      if (internshipStatusResponse.data?.code === 200) {
        internshipStatuses.value = internshipStatusResponse.data.data.map((item: any) => ({
          id: item.id,
          studentId: item.studentId,
          studentName: item.student ? item.student.name : '',
          studentUserId: item.student ? item.student.studentUserId : '',
          gender: item.student ? item.student.gender : '',
          grade: item.student ? item.student.grade : '',
          majorId: item.student ? item.student.majorId : '',
          majorName: item.student ? majorsMap[item.student.majorId] || '' : '',
          classId: item.student ? item.student.classId : '',
          companyId: item.companyId,
          companyName: item.company ? item.company.companyName : '',
          positionId: item.positionId,
          positionName: item.position ? item.position.positionName : '',
          status: item.status,
          companyConfirmStatus: item.companyConfirmStatus,
          internshipStartTime: item.internshipStartTime,
          internshipEndTime: item.internshipEndTime,
          internshipDuration: item.internshipDuration,
          remark: item.remark,
          feedback: item.feedback,
          createTime: item.createTime,
          updateTime: item.updateTime
        }))
        syncRecruitedCountFromInternshipStatuses()
      }
    } catch (error) {
      console.error('获取实习确认申请失败:', error)
    } finally {
      loading.value = false
    }
  }

  const updateInternshipStatus = async (id: number, statusData: Partial<InternshipStatus>): Promise<boolean> => {
    try {
      await internshipStatusApi.updateInternshipStatus(id, statusData)
      const status = internshipStatuses.value.find(s => s.id === id)
      if (status) {
        Object.assign(status, statusData)
      }
      return true
    } catch (error) {
      console.error('更新实习状态失败:', error)
      return false
    }
  }

  const approveInternshipStatus = async (id: number): Promise<boolean> => {
    try {
      await internshipStatusApi.approveInternshipStatus(id)
      const status = internshipStatuses.value.find(s => s.id === id)
      if (status) {
        status.companyConfirmStatus = 1
        status.status = 2
        if (status.companyId) {
          await fetchPositions(status.companyId)
        }
      }
      return true
    } catch (error) {
      console.error('批准实习确认失败:', error)
      return false
    }
  }

  const rejectInternshipStatus = async (id: number): Promise<boolean> => {
    try {
      await internshipStatusApi.rejectInternshipStatus(id)
      const status = internshipStatuses.value.find(s => s.id === id)
      if (status) {
        status.companyConfirmStatus = 2
        if (status.companyId) {
          await fetchPositions(status.companyId)
        }
      }
      return true
    } catch (error) {
      console.error('拒绝实习确认失败:', error)
      return false
    }
  }

  return {
    positions,
    applications,
    internshipStatuses,
    loading,
    fetchPositions,
    fetchApplications,
    fetchInternshipStatuses,
    totalPlanCount,
    totalConfirmedCount,
    totalGap,
    activePositionCount,
    getApplicationsByPosition,
    getApplicationsByStatus,
    updateApplicationStatus,
    addPosition,
    updatePosition,
    deletePosition,
    pausePosition,
    resumePosition,
    addApplication,
    markAsViewed,
    updateInternshipStatus,
    approveInternshipStatus,
    rejectInternshipStatus
  }
})
