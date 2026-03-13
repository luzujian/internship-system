<script setup lang="ts">
import logger from '@/utils/logger'
import { onMounted, ref, computed, nextTick } from 'vue'
import type { Department, Major, Class } from '@/types/admin'

import DepartmentService from '../../api/department'
import MajorService from '../../api/major'
import ClassService from '../../api/class'
import StudentUserService from '../../api/StudentUserService'
// import AssignmentService from '../../api/assignment' // 文件不存在，已注释
import GradeService from '../../api/grade'
import request from '../../utils/request'
import { ElMessage, ElMessageBox, ElCheckbox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, View, Download, Upload, ArrowDown } from '@element-plus/icons-vue'
import * as XLSX from 'xlsx'
import { useAuthStore } from '../../store/auth'

const authStore = useAuthStore()

//搜索表单对象
let searchDepartment = ref({ name: '', majorId: null })
//加载状态
let loading = ref<boolean>(false)
//列表展示数据 - 院系列表
let departments = ref<unknown[]>([])
//专业列表数据
let majorList = ref<unknown[]>([])
//当前悬停的院系ID
let hoveredDepartmentId = ref(null)
//当前固定显示的院系ID
let fixedDepartmentId = ref(null)
//当前选中的专业ID
let selectedMajorId = ref(null)
//专业操作框显示状态
let majorActionDialogVisible = ref<boolean>(false)
//导出加载状态
let exportLoading = ref<boolean>(false)
//导入加载状态
let importLoading = ref<boolean>(false)
//当前操作的专业数据
let currentMajor = ref<Record<string, unknown>>({})
//根据ID获取院系对应的专业
let departmentMajors = ref(new Map())
//搜索专业名称
let searchMajorName = ref<string>('')
//搜索专业结果
let searchMajorResult = ref(null)

// 专业过滤方法
const handleMajorFilter = (query, option) => {
  // 确保option和label都存在
  return option && option.label && option.label.toLowerCase().includes(query.toLowerCase());
}

// 新增专业相关状态
let addMajorDialogVisible = ref<boolean>(false)
let addMajorFormRef = ref()
let addMajorForm = ref({
  name: '',
  departmentId: ''
})

// 编辑专业相关状态
let editMajorDialogVisible = ref<boolean>(false)
let editMajorFormRef = ref()
let editMajorForm = ref({
  id: '',
  name: '',
  departmentId: ''
})

const addMajorRules = ref({
  name: [
    { required: true, message: '专业名称为必填项', trigger: 'blur' },
    { min: 2, max: 30, message: '专业名称长度为2-30个字', trigger: 'blur' }
  ]
})

// 编辑专业表单验证规则
const editMajorRules = ref({
  name: [
    { required: true, message: '专业名称为必填项', trigger: 'blur' },
    { min: 2, max: 30, message: '专业名称长度为2-30个字', trigger: 'blur' }
  ]
})

//日期格式化方法 - 用于表格中日期列的显示
const formatDate = (_row, _column, cellValue) => {
  if (!cellValue) {
    return ''
  }
  const d = new Date(cellValue)
  // 检查是否是有效日期
  if (isNaN(d.getTime())) return ''

  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')

  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

// 分页组件
const pagination = ref({ currentPage: 1, pageSize: 10, total: 0 })

//钩子函数 - 页面加载时触发
onMounted(async () => {
  logger.log('页面开始加载...')
  // 先获取专业列表，再查询院系数据
  await getMajorList()
  await queryDepartments()
})

//获取专业列表
const getMajorList = async (): Promise<void> => {
  try {
    logger.log('开始获取专业列表...')
    const response = await MajorService.getMajors()
    logger.log('专业列表响应:', response)

    // 增强数据结构兼容性处理
    if (response && response.data) {
      if (Array.isArray(response.data)) {
        majorList.value = response.data
      } else if (response.data.code === 200 && response.data.data) {
        // 处理带code和data的响应格式
        majorList.value = Array.isArray(response.data.data) ? response.data.data : []
      } else {
        majorList.value = []
      }
    } else {
      majorList.value = []
    }

    logger.log('最终的专业列表数据:', majorList.value)

    // 确保数据格式正确，为每个专业对象添加id和name属性，以及正确的departmentId和updateTime
    if (majorList.value.length > 0) {
      // 转换数据结构，确保每个对象都有id、name、departmentId和updateTime属性
      majorList.value = majorList.value.map((major, index) => {
        // 检查departmentId字段的值（处理数据库字段名department_id和前端departmentId的差异）
        let departmentId = major.departmentId || major.department_id || ''

        // 确保departmentId始终是数字类型
        if (departmentId) {
          // 转换为数字类型
          departmentId = Number(departmentId)
        } else {
          // 如果departmentId是空的，则根据索引分配一个默认的院系ID
          departmentId = (index % 5) + 1 // 分配到前5个院系，保持数字类型
        }

        return {
          id: major.id || major.majorId || '',
          name: major.name || major.majorName || '未知专业',
          departmentId: departmentId,
          updateTime: major.updateTime || major.update_time || '',
          createTime: major.createTime || major.create_time || '',
          teacherCount: major.teacherCount || 0,
          studentCount: major.studentCount || 0,
          confirmedCount: major.confirmedCount || 0,
          notFoundCount: major.notFoundCount || 0,
          hasOfferCount: major.hasOfferCount || 0
        }
      })
    }

    // 构建院系-专业映射关系
    buildDepartmentMajorsMap()
  } catch (error) {
    logger.error('获取专业列表失败:', error)
    majorList.value = []
    ElMessage.error('获取专业列表失败: ' + (error.message || '未知错误'))
  }
}

//每页展示记录数发生变化时触发
const handleSizeChange = (pageSize) => {
  pagination.value.pageSize = pageSize
  pagination.value.currentPage = 1 // 重置为第一页
  queryDepartments()
}

//当前页码发生变化时触发
const handleCurrentChange = (page) => {
  pagination.value.currentPage = page
  queryDepartments()
}

//获取院系列表 - 带分页功能
const queryDepartments = async (): Promise<void> => {
  try {
    logger.log('开始查询院系列表...')
    logger.log('搜索院系名称:', searchDepartment.value.name)

    // 确保在查询院系前已经获取了专业列表
    if (majorList.value.length === 0) {
      logger.log('专业列表为空，先获取专业列表...')
      await getMajorList()
    }

    // 传递搜索参数
    const result = await DepartmentService.getDepartments(searchDepartment.value.name)
    logger.log('院系列表查询结果:', result)

    if (result && (result.code === 200 || result.data)) {
      // 处理不同的响应格式
      const departmentData = result.data.code === 200 ? result.data.data : result.data
      const allDepartments = Array.isArray(departmentData) ? departmentData : []

      // 设置分页总数
      pagination.value.total = allDepartments.length

      // 应用前端分页
      const startIndex = (pagination.value.currentPage - 1) * pagination.value.pageSize
      const endIndex = startIndex + pagination.value.pageSize
      departments.value = allDepartments.slice(startIndex, endIndex)

      logger.log('处理后的院系数据:', departments.value)
      logger.log('分页信息:', pagination.value)
    } else {
      logger.error('查询院系失败:', result?.msg)
      departments.value = []
      pagination.value.total = 0
      ElMessage.error('查询院系失败: ' + (result?.msg || '未知错误'))
    }
  } catch (error) {
    logger.error('查询院系列表时发生异常:', error)
    departments.value = []
    pagination.value.total = 0
    ElMessage.error('查询院系失败: ' + (error.message || '未知错误'))
  }
}

//构建院系-专业映射关系
const buildDepartmentMajorsMap = () => {
  const map = new Map()
  logger.log('开始构建院系-专业映射关系...')
  logger.log('当前专业列表:', majorList.value)

  majorList.value.forEach(major => {
    const deptId = major.departmentId
    if (deptId) {
      logger.log(`专业${major.name}的departmentId: ${deptId}`)
      if (!map.has(deptId)) {
        map.set(deptId, [])
      }
      map.get(deptId).push(major)
    } else {
      logger.log(`专业${major.name}没有有效的departmentId`)
    }
  })

  // 打印构建的映射关系
  logger.log('构建的院系-专业映射关系:')
  map.forEach((majors, deptId) => {
    logger.log(`院系ID ${deptId} 包含 ${majors.length} 个专业`)
  })

  departmentMajors.value = map
}

//获取指定院系的专业列表
const getDepartmentMajors = (departmentId) => {
  logger.log(`获取院系ID ${departmentId} 的专业列表`)
  const majors = departmentMajors.value.get(departmentId) || []
  logger.log(`院系ID ${departmentId} 有 ${majors.length} 个专业`)
  return majors
}

//鼠标悬停院系时触发
const handleMouseEnterDepartment = (departmentId) => {
  // 直接设置悬停的院系ID，无论是否有固定显示的院系
  hoveredDepartmentId.value = departmentId
}

//导出Excel功能
const exportToExcel = async (): Promise<void> => {
  try {
    exportLoading.value = true
    
    // 检查是否已有专业数据，如果没有则获取
    if (majorList.value.length === 0) {
      await getMajorList()
    }
    
    // 检查是否已有院系数据，如果没有则获取
    if (departments.value.length === 0) {
      await queryDepartments()
    }
    
    // 构建完整的院系列表（包含所有院系，不只是当前分页的数据）
    let allDepartments = departments.value
    if (pagination.value.total > departments.value.length) {
      // 如果有分页，获取所有院系数据
      const result = await DepartmentService.getDepartments(searchDepartment.value.name)
      if (result && (result.code === 200 || result.data)) {
        const departmentData = result.data.code === 200 ? result.data.data : result.data
        allDepartments = Array.isArray(departmentData) ? departmentData : []
      }
    }
    
    // 准备导出数据
    const exportData = []
    
    // 遍历所有院系
    allDepartments.forEach(department => {
      const departmentId = department.id
      // 获取该院系下的所有专业
      const majors = getDepartmentMajors(departmentId)
      
      if (majors.length > 0) {
        // 有专业的院系，为每个专业创建一行
        majors.forEach((major, index) => {
          exportData.push({
            '院系名称': index === 0 ? department.name : '', // 只在第一个专业显示院系名称
            '院系ID': index === 0 ? department.id : '', // 只在第一个专业显示院系ID
            '教师人数': index === 0 ? (department.teacherCount || 0) : '', // 只在第一个专业显示教师人数
            '学生总数': index === 0 ? (department.studentCount || 0) : '', // 只在第一个专业显示学生总数
            '院系更新时间': index === 0 ? formatDateForExcel(department.updateTime) : '', // 只在第一个专业显示院系更新时间
            '专业名称': major.name,
            '专业ID': major.id,
            '专业学生人数': major.studentCount || 0,
            '专业更新时间': formatDateForExcel(major.updateTime)
          })
        })
      } else {
        // 没有专业的院系，创建一行
        exportData.push({
          '院系名称': department.name,
          '院系ID': department.id,
          '教师人数': department.teacherCount || 0,
          '学生总数': department.studentCount || 0,
          '院系更新时间': formatDateForExcel(department.updateTime),
          '专业名称': '-',
          '专业ID': '-',
          '专业学生人数': 0,
          '专业更新时间': '-'
        })
      }
    })
    
    // 创建工作簿
    const wb = XLSX.utils.book_new()
    
    // 创建工作表
    const ws = XLSX.utils.json_to_sheet(exportData)
    
    // 设置列宽
    const colWidths = [
      { wch: 20 }, // 院系名称
      { wch: 15 }, // 院系ID
      { wch: 10 }, // 教师人数
      { wch: 10 }, // 学生总数
      { wch: 20 }, // 院系更新时间
      { wch: 20 }, // 专业名称
      { wch: 15 }, // 专业ID
      { wch: 12 }, // 专业学生人数
      { wch: 20 }  // 专业更新时间
    ]
    ws['!cols'] = colWidths
    
    // 添加工作表到工作簿
    XLSX.utils.book_append_sheet(wb, ws, '院系专业信息')
    
    // 生成文件名
    const now = new Date()
    const formattedDate = formatDateForFilename(now)
    const fileName = `院系专业信息_${formattedDate}.xlsx`
    
    // 导出文件
    XLSX.writeFile(wb, fileName)
    
    ElMessage.success('导出成功！')
  } catch (error) {
    logger.error('导出Excel失败:', error)
    ElMessage.error('导出失败，请重试')
  } finally {
    exportLoading.value = false
  }
}

//格式化日期用于Excel
const formatDateForExcel = (dateString) => {
  if (!dateString) return ''
  const d = new Date(dateString)
  if (isNaN(d.getTime())) return ''
  
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')
  
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

//格式化日期用于文件名
const formatDateForFilename = (date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  
  return `${year}${month}${day}${hours}${minutes}${seconds}`
}

//鼠标离开院系时触发
const handleMouseLeaveDepartment = () => {
  // 直接清除悬停的院系ID
  hoveredDepartmentId.value = null
}

//测试专业显示功能
const testMajorDisplay = (departmentId) => {
  logger.log('开始测试专业显示功能')

  // 直接设置固定院系ID
  fixedDepartmentId.value = departmentId
  searchMajorResult.value = null

  // 打印当前状态
  logger.log(`测试：fixedDepartmentId = ${fixedDepartmentId.value}`)
  logger.log(`测试：departmentMajors.size = ${departmentMajors.value.size}`)

  // 检查是否有对应院系的专业
  const majors = getDepartmentMajors(departmentId)
  logger.log(`测试：院系ID ${departmentId} 有 ${majors.length} 个专业`)

  // 检查displayedMajors计算结果
  logger.log(`测试：displayedMajors.value =`, displayedMajors.value)
  logger.log(`测试：displayedMajors.value.length = ${displayedMajors.value.length}`)

}

//点击专业按钮显示/隐藏专业
const handleShowDepartmentMajors = (departmentId) => {
  logger.log(`点击了院系ID ${departmentId} 的专业按钮`)
  // 如果已经固定显示该院系的专业，则取消固定
  if (fixedDepartmentId.value === departmentId) {
    logger.log('取消固定院系专业')
    fixedDepartmentId.value = null
    searchMajorResult.value = null
  } else {
    // 否则固定显示该院系的专业
    logger.log(`固定显示院系ID ${departmentId} 的专业`)
    fixedDepartmentId.value = departmentId
    searchMajorResult.value = null

    // 调用测试函数
    testMajorDisplay(departmentId)
  }
}

//点击专业显示操作框
const handleMajorClick = (major) => {
  currentMajor.value = major
  selectedMajorId.value = major.id
  majorActionDialogVisible.value = true
}

//关闭专业操作框
const closeMajorActionDialog = () => {
  majorActionDialogVisible.value = false
  selectedMajorId.value = null
  currentMajor.value = {}
}

//编辑专业 - 直接显示编辑对话框，不需要经过操作对话框
const handleEditMajor = (major) => {
  // 设置编辑表单数据，确保深拷贝避免引用问题
  editMajorForm.value = { ...major }
  // 显示编辑专业对话框
  editMajorDialogVisible.value = true
  // 关闭专业操作对话框（如果打开的话）
  closeMajorActionDialog()
}

// 保存编辑的专业信息
const saveEditMajor = async (): Promise<void> => {
  if (!editMajorFormRef.value) return

  editMajorFormRef.value.validate(async (valid) => {
    if (valid) {
      if (!editMajorForm.value.id || editMajorForm.value.id === 'undefined') {
        ElMessage.error('无效的专业ID')
        return
      }
      
      try {
        const result = await MajorService.updateMajor(editMajorForm.value.id, editMajorForm.value)
        
        if (result && (result.code === 200 || result.data?.code === 200)) {
          ElMessage.success('专业编辑成功')
          editMajorDialogVisible.value = false
          await getMajorList()
          await queryDepartments()
          if (fixedDepartmentId.value) {
            fixedDepartmentId.value = fixedDepartmentId.value
          }
        } else {
          ElMessage.error('专业编辑失败: ' + (result?.msg || '未知错误'))
        }
      } catch (error) {
        logger.error('编辑专业失败:', error)
        ElMessage.error('编辑专业失败: ' + (error.message || '未知错误'))
      }
    }
  })
}

// 关闭编辑专业对话框
const closeEditMajorDialog = () => {
  editMajorDialogVisible.value = false
  // 重置表单数据
  editMajorForm.value = {
    id: '',
    name: '',
    departmentId: ''
  }
  // 重置表单验证状态
  if (editMajorFormRef.value) {
    editMajorFormRef.value.resetFields()
  }
}

//删除专业
const handleDeleteMajor = async (major): Promise<void> => {
  if (!major || !major.id || major.id === 'undefined') {
    ElMessage.error('无效的专业ID')
    return
  }
  
  try {
    await ElMessageBox.confirm(
      '确定要删除该专业吗？此操作将删除该专业及其所有相关数据（包括班级、学生等），此操作不可撤销！',
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
        center: true
      }
    )

    const result = await MajorService.deleteMajor(major.id)
    if (result && (result.code === 200 || result.data?.code === 200)) {
      ElMessage.success('专业删除成功')
      await getMajorList()
      const departmentId = major.departmentId
      await queryDepartments()
      if (departmentId) {
        fixedDepartmentId.value = departmentId
      }
      closeMajorActionDialog()
    } else {
      ElMessage.error('专业删除失败: ' + (result?.msg || '未知错误'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      logger.error('删除专业失败:', error)
      ElMessage.error('删除失败: ' + (error.message || '未知错误'))
    }
  }
}

//分页相关状态
const searchMajorPagination = ref({
  currentPage: 1,
  total: 0,
  departments: [] // 存储匹配的院系列表
})

//搜索专业名称
const searchMajorByName = async (): Promise<void> => {
  if (!searchMajorName.value.trim()) {
    clearMajorSearch()
    return
  }

  try {
    // 先尝试调用后端API搜索
    const response = await MajorService.searchMajorsByName(searchMajorName.value.trim())

    let foundMajors = []
    if (response && response.data && response.data.code === 200 && response.data.data) {
      // 如果后端返回了结果，使用后端结果
      if (Array.isArray(response.data.data)) {
        foundMajors = response.data.data
      } else if (response.data.data && typeof response.data.data === 'object') {
        // 有些后端可能直接返回单个对象
        foundMajors = [response.data.data]
      }
    } else {
      // 如果后端API不支持或返回空结果，使用前端过滤
      logger.log('后端搜索无结果，使用前端过滤')
      foundMajors = majorList.value.filter(major =>
        major.name && major.name.includes(searchMajorName.value.trim())
      )
    }

    if (foundMajors.length > 0) {
      // 按院系ID分组
      const departmentMap = new Map()
      foundMajors.forEach(major => {
        const deptId = major.departmentId
        if (!departmentMap.has(deptId)) {
          departmentMap.set(deptId, [])
        }
        departmentMap.get(deptId).push(major)
      })

      // 转换为数组并获取院系信息
      const departmentList = []
      departmentMap.forEach((majors, deptId) => {
        const department = departments.value.find(dept => dept.id === deptId)
        departmentList.push({
          id: deptId,
          name: department ? department.name : '未知院系',
          majors: majors
        })
      })

      // 更新分页信息
      searchMajorPagination.value.departments = departmentList
      searchMajorPagination.value.total = departmentList.length
      searchMajorPagination.value.currentPage = 1

      // 触发显示
      fixedDepartmentId.value = departmentList[0]?.id

      logger.log(`搜索到${foundMajors.length}个专业，分布在${departmentList.length}个院系`)
    } else {
      ElMessage.info('未找到匹配的专业')
      searchMajorResult.value = null
      searchMajorPagination.value.departments = []
      searchMajorPagination.value.total = 0
    }
  } catch (error) {
    logger.error('搜索专业失败:', error)
    // 如果API调用失败，回退到前端过滤
    try {
      logger.log('API调用失败，回退到前端过滤')
      const foundMajors = majorList.value.filter(major =>
        major.name && major.name.includes(searchMajorName.value.trim())
      )

      if (foundMajors.length > 0) {
        // 按院系ID分组
        const departmentMap = new Map()
        foundMajors.forEach(major => {
          const deptId = major.departmentId
          if (!departmentMap.has(deptId)) {
            departmentMap.set(deptId, [])
          }
          departmentMap.get(deptId).push(major)
        })

        // 转换为数组并获取院系信息
        const departmentList = []
        departmentMap.forEach((majors, deptId) => {
          const department = departments.value.find(dept => dept.id === deptId)
          departmentList.push({
            id: deptId,
            name: department ? department.name : '未知院系',
            majors: majors
          })
        })

        // 更新分页信息
        searchMajorPagination.value.departments = departmentList
        searchMajorPagination.value.total = departmentList.length
        searchMajorPagination.value.currentPage = 1

        // 触发显示
        fixedDepartmentId.value = departmentList[0]?.id

        logger.log(`搜索到${foundMajors.length}个专业，分布在${departmentList.length}个院系`)
      } else {
        ElMessage.info('未找到该专业')
        searchMajorResult.value = null
        searchMajorPagination.value.departments = []
        searchMajorPagination.value.total = 0
      }
    } catch (e) {
      ElMessage.error('搜索专业失败: ' + (e.message || '未知错误'))
    }
  }
}

//搜索结果分页切换
const handleSearchMajorPageChange = (currentPage) => {
  searchMajorPagination.value.currentPage = currentPage
  const department = searchMajorPagination.value.departments[currentPage - 1]
  if (department) {
    fixedDepartmentId.value = department.id
  }
}

//清空搜索栏
const clearMajorSearch = () => {
  searchMajorName.value = ''
  searchMajorResult.value = null
  searchMajorPagination.value.departments = []
  searchMajorPagination.value.total = 0
  searchMajorPagination.value.currentPage = 1
  fixedDepartmentId.value = null
}

// 打开新增专业对话框
const handleAddMajor = () => {
// 设置当前院系ID
addMajorForm.value.departmentId = fixedDepartmentId.value
// 重置表单数据
addMajorForm.value.name = ''
// 显示对话框
addMajorDialogVisible.value = true
}

// 保存专业信息
const saveMajor = async (): Promise<void> => {
if (!addMajorFormRef.value) return

addMajorFormRef.value.validate(async (valid) => {
if (valid) {
try {
const result = await MajorService.addMajor(addMajorForm.value)

if (result && (result.code === 200 || result.data?.code === 200)) {
ElMessage.success('专业添加成功')
// 关闭对话框
addMajorDialogVisible.value = false
// 重新获取专业列表并构建映射关系
await getMajorList()
// 重新查询院系列表以更新最后修改时间
await queryDepartments()
// 保持当前固定的院系状态
if (fixedDepartmentId.value) {
fixedDepartmentId.value = fixedDepartmentId.value
}
} else {
ElMessage.error('专业添加失败: ' + (result?.msg || '未知错误'))
}
} catch (error) {
logger.error('添加专业失败:', error)
ElMessage.error('添加专业失败: ' + (error.message || '未知错误'))
}
}
})
}

// 关闭新增专业对话框
const closeAddMajorDialog = () => {
addMajorDialogVisible.value = false
// 重置表单数据
addMajorForm.value = {
name: '',
departmentId: ''
}
// 重置表单验证状态
if (addMajorFormRef.value) {
addMajorFormRef.value.resetFields()
}
}

// 导入对话框显示状态
let importDialogVisible = ref<boolean>(false)
// 选中的文件
let selectedFile = ref(null)
// 隐藏的文件上传输入引用
let excelFileInput = ref(null)

// 显示导入 Excel对话框
const showImportDialog = () => {
  selectedFile.value = null
  importDialogVisible.value = true
  
  // 不要在对话框刚显示时自动触发文件选择，让用户手动点击按钮
}

// 处理文件选择按钮点击
const handleChooseFile = () => {
  if (excelFileInput.value) {
    excelFileInput.value.click()
  } else {
    logger.warn('excelFileInput is not available yet')
  }
}

// 处理原生input文件选择
const handleNativeFileChange = (e) => {
  logger.log('handleNativeFileChange called with:', e)
  
  if (e.target && e.target.files && e.target.files.length > 0) {
    const file = e.target.files[0]
    const fileName = file.name.toLowerCase()
    
    if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls')) {
      ElMessage.error('请选择Excel文件(.xlsx或.xls格式)')
      e.target.value = ''
      return
    }
    
    selectedFile.value = file
  }
}

// 导入 Excel功能
const importExcel = async (): Promise<void> => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择Excel文件')
    return
  }
  
  importLoading.value = true
  
  try {
    const reader = new FileReader()
    
    const fileData = await new Promise((resolve, reject) => {
      reader.onload = (e) => resolve(e.target.result)
      reader.onerror = reject
      reader.readAsArrayBuffer(selectedFile.value)
    })
    
    // 读取Excel文件
    const data = new Uint8Array(fileData)
    const workbook = XLSX.read(data, { type: 'array' })
    
    // 获取第一个工作表
    const sheetName = workbook.SheetNames[0]
    const worksheet = workbook.Sheets[sheetName]
    
    // 转换为JSON格式
    const jsonData = XLSX.utils.sheet_to_json(worksheet)
    
    if (jsonData.length === 0) {
      ElMessage.warning('Excel文件中没有数据')
      return
    }
    
    logger.log('读取到的Excel数据:', jsonData)
    
    // 处理读取到的数据
    const importResult = await processImportData(jsonData)
    
    if (importResult.success) {
      // 构建导入结果消息
      let message = `导入完成，成功: ${importResult.successCount} 条`
      
      if (importResult.errorCount > 0) {
        message += `，失败: ${importResult.errorCount} 条`
      }
      
      // 如果有失败记录，显示失败详情
      if (importResult.errorMessages && importResult.errorMessages.length > 0) {
        let errorDetails = '\n失败详情:\n'
        importResult.errorMessages.forEach((errorMsg, index) => {
          errorDetails += `${index + 1}. ${errorMsg}\n`
        })
        
        // 显示包含详细信息的消息框
        ElMessageBox.alert(message + errorDetails, '导入结果', {
          type: importResult.errorCount > 0 ? 'warning' : 'success',
          confirmButtonText: '确定',
          dangerouslyUseHTMLString: false,
          callback: () => {
            closeImportDialog()
            // 重新获取数据
            getMajorList()
            queryDepartments()
          }
        })
      } else {
        // 如果全部成功，显示成功消息
        ElMessage.success(message)
        closeImportDialog()
        // 重新获取数据
        getMajorList()
        queryDepartments()
      }
    } else {
      ElMessage.error(`导入失败: ${importResult.errorMessage}`)
    }
  } catch (error) {
    logger.error('导入 Excel文件失败:', error)
    ElMessage.error('导入 Excel文件失败: ' + (error.message || '未知错误'))
  } finally {
    importLoading.value = false
  }
}

// 关闭导入对话框
const closeImportDialog = () => {
  importDialogVisible.value = false
  selectedFile.value = null
  // 清空文件输入，以便下次可以选择同一个文件
  if (excelFileInput.value) {
    excelFileInput.value.value = ''
  }
}

// 下载导入模板
const downloadTemplate = async (): Promise<void> => {
  try {
    const response = await request.get('/admin/departments/majors/import-template', {
      responseType: 'blob'
    })

    // 创建下载链接
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', '院系专业导入模板.xlsx')
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)

    ElMessage.success('模板下载成功')
  } catch (error) {
    logger.error('下载模板失败:', error)
    ElMessage.error('下载模板失败：' + (error.message || '未知错误'))
  }
}

//处理文件上传
const handleFileUpload = (event) => {
  const file = event.target.files[0]
  if (!file) return
  
  // 验证文件类型
  if (!file.name.endsWith('.xlsx') && !file.name.endsWith('.xls')) {
    ElMessage.error('请选择Excel文件(.xlsx或.xls格式)')
    return
  }
  
  importLoading.value = true
  
  const reader = new FileReader()
  reader.onload = async (e) => {
    try {
      // 读取Excel文件
      const data = new Uint8Array(e.target.result)
      const workbook = XLSX.read(data, { type: 'array' })
      
      // 获取第一个工作表
      const sheetName = workbook.SheetNames[0]
      const worksheet = workbook.Sheets[sheetName]
      
      // 转换为JSON格式
      const jsonData = XLSX.utils.sheet_to_json(worksheet)
      
      if (jsonData.length === 0) {
        ElMessage.warning('Excel文件中没有数据')
        return
      }
      
      logger.log('读取到的Excel数据:', jsonData)
      
      // 处理读取到的数据
      const importResult = await processImportData(jsonData)
      
      if (importResult.success) {
        ElMessage.success(`成功导入${importResult.successCount}条数据`)        
        // 重新获取数据
        await getMajorList()
        await queryDepartments()
      } else {
        ElMessage.error(`导入失败: ${importResult.errorMessage}`)
      }
    } catch (error) {
      logger.error('导入 Excel文件失败:', error)
      ElMessage.error('导入 Excel文件失败: ' + (error.message || '未知错误'))
    } finally {
      importLoading.value = false
      // 清空文件输入，以便下次可以选择同一个文件
      event.target.value = ''
    }
  }
  reader.readAsArrayBuffer(file)
}

//处理导入的数据
const processImportData = async (data): Promise<void> => {
  try {
    // 存储导入结果
    let successCount = 0
    let errorCount = 0
    let errorMessages = []
    
    // 存储院系名称到ID的映射
    const departmentMap = new Map()
    
    // 用于跟踪当前处理的院系（解决导出时同一院系只在第一行显示名称的问题）
    let currentDepartmentName = ''
    let currentDepartmentId = ''
    
    // 首先获取所有院系数据，建立映射关系
    const allDepartments = await DepartmentService.getDepartments()
    if (allDepartments && (allDepartments.code === 200 || allDepartments.data)) {
      const departmentData = allDepartments.data.code === 200 ? allDepartments.data.data : allDepartments.data
      const departmentsList = Array.isArray(departmentData) ? departmentData : []
      
      departmentsList.forEach(dept => {
        departmentMap.set(dept.name, dept.id)
      })
    }
    
    // 获取所有专业数据，用于检查重复
    const allMajorsResponse = await MajorService.getMajors()
    const allMajorsData = allMajorsResponse && (allMajorsResponse.code === 200 || allMajorsResponse.data) 
      ? (allMajorsResponse.data.code === 200 ? allMajorsResponse.data.data : allMajorsResponse.data) 
      : []
    const allMajorsList = Array.isArray(allMajorsData) ? allMajorsData : []
    
    // 遍历导入的数据
    for (let i = 0; i < data.length; i++) {
      const row = data[i]
      const rowNum = i + 1
      
      try {
        let departmentName = row['院系名称'] || row['院系']
        const majorName = row['专业名称'] || row['专业']
        
        // 如果院系名称为空但有专业名称，则使用上一行的院系信息（解决导出格式问题）
        if (!departmentName && majorName) {
          if (currentDepartmentName) {
            departmentName = currentDepartmentName
          } else {
            // 如果没有上一行的院系信息，则跳过
            errorCount++
            errorMessages.push(`第${rowNum}行: 院系名称为空且无前一行院系信息`)
            continue
          }
        }
        
        if (!departmentName || !majorName) {
          errorCount++
          errorMessages.push(`第${rowNum}行: 院系名称或专业名称为空`)
          continue
        }
        
        // 更新当前院系信息
        currentDepartmentName = departmentName
        
        // 查找院系
        let departmentId = departmentMap.get(departmentName)
        
        // 如果院系不存在，创建新院系
        if (!departmentId) {
          const deptResult = await DepartmentService.addDepartment({ name: departmentName })
          if (deptResult && (deptResult.code === 200 || deptResult.data?.code === 200)) {
            // 获取新创建的院系ID
            const newDeptId = deptResult.data?.data?.id || deptResult.data?.id
            if (newDeptId) {
              departmentMap.set(departmentName, newDeptId)
              departmentId = newDeptId
            } else {
              errorCount++
              errorMessages.push(`第${rowNum}行: 创建院系失败 - 无法获取新院系ID`)
              continue
            }
          } else {
            // 检查是否是院系已存在的错误
            const errorMsg = deptResult?.msg || deptResult?.data?.msg || '未知错误'
            if (errorMsg.includes('已存在') || errorMsg.includes('Duplicate') || errorMsg.includes('重复')) {
              errorCount++
              errorMessages.push(`第${rowNum}行: 院系"${departmentName}"已存在，跳过导入`)
              continue
            } else {
              errorCount++
              errorMessages.push(`第${rowNum}行: 创建院系失败 - ${errorMsg}`)
              continue
            }
          }
        }
        
        // 更新当前院系ID
        currentDepartmentId = departmentId
        
        // 检查专业是否已存在
        const existingMajor = allMajorsList.find(m => m.name === majorName && m.departmentId === departmentId)
        
        if (existingMajor) {
          // 专业已存在，跳过
          errorCount++
          errorMessages.push(`第${rowNum}行: 专业"${majorName}"已存在，跳过导入`)
          continue
        }
        
        // 创建专业
        const majorResult = await MajorService.addMajor({
          name: majorName,
          departmentId: departmentId
        })
        
        if (majorResult && (majorResult.code === 200 || majorResult.data?.code === 200)) {
          successCount++
          // 将新添加的专业加入到已存在列表中，避免后续重复
          const newMajorId = majorResult.data?.data?.id || majorResult.data?.id
          allMajorsList.push({ id: newMajorId, name: majorName, departmentId: departmentId })
        } else {
          errorCount++
          errorMessages.push(`第${rowNum}行: 添加专业失败 - ${majorResult?.msg || majorResult?.data?.msg || '未知错误'}`)
        }
      } catch (e) {
        errorCount++
        errorMessages.push(`第${rowNum}行: 处理数据时出错 - ${e.message || '未知错误'}`)
      }
    }
    
    return {
      success: true,
      successCount: successCount,
      errorCount: errorCount,
      errorMessages: errorMessages
    }
  } catch (error) {
    logger.error('处理导入数据时出错:', error)
    return {
      success: false,
      errorMessage: error.message || '处理导入数据时出错'
    }
  }
}

//----------- 新增 / 修改 ---------------------------
let dialogFormVisible = ref<boolean>(false) //控制新增/修改的对话框的显示与隐藏
let labelWidth = ref(80) //form表单label的宽度
let formTitle = ref<string>('') //表单的标题

// 为新增和编辑操作创建独立的表单数据和引用，避免数据相互干扰
// 新增院系表单引用
let addDepartmentFormRef = ref()
// 编辑院系表单引用
let editDepartmentFormRef = ref()

// 新增院系表单数据
let addDepartment = ref({
id: '',
name: ''
})

// 编辑院系表单数据
let editDepartment = ref({
id: '',
name: ''
})

//计算属性：是否显示专业列表
const showMajors = computed(() => {
// 专业列表卡片始终显示
return true
})

//计算属性：显示的专业列表
const displayedMajors = computed(() => {
logger.log('计算displayedMajors:', {
searchMajorPagination: {
currentPage: searchMajorPagination.value.currentPage,
total: searchMajorPagination.value.total
},
fixedDepartmentId: fixedDepartmentId.value,
hoveredDepartmentId: hoveredDepartmentId.value
})

// 如果正在搜索模式且有匹配的院系，显示搜索结果
if (searchMajorPagination.value.total > 0 && fixedDepartmentId.value) {
logger.log(`显示搜索结果中的院系ID ${fixedDepartmentId.value} 的专业`)
const majors = getDepartmentMajors(fixedDepartmentId.value)
logger.log(`搜索结果院系专业数量: ${majors.length}`)
return majors
}

// 悬停优先：如果有悬停的院系，显示该院系的专业
if (hoveredDepartmentId.value) {
logger.log(`显示悬停院系ID ${hoveredDepartmentId.value} 的专业`)
const majors = getDepartmentMajors(hoveredDepartmentId.value)
logger.log(`悬停院系专业数量: ${majors.length}`)
return majors
}

// 如果有固定显示的院系，显示该院系的专业
if (fixedDepartmentId.value) {
logger.log(`显示固定院系ID ${fixedDepartmentId.value} 的专业`)
const majors = getDepartmentMajors(fixedDepartmentId.value)
logger.log(`固定院系专业数量: ${majors.length}`)
return majors
}

logger.log('没有要显示的专业')
return []
})

//清空新增院系表单数据
const clearAddDepartmentForm = () => {
addDepartment.value = {
id: '',
name: ''
}
}

//清空编辑院系表单数据
const clearEditDepartmentForm = () => {
editDepartment.value = {
id: '',
name: ''
}
}

//新增院系
const handleAddDepartment = () => {
dialogFormVisible.value = true
formTitle.value = '新增院系'
clearAddDepartmentForm()
}

//修改院系
const handleUpdateDepartment = async (id): Promise<void> => {
  if (!id || id === 'undefined') {
    ElMessage.error('无效的院系ID')
    return
  }
  
  clearEditDepartmentForm()
  dialogFormVisible.value = true
  formTitle.value = '编辑院系'
  try {
    const result = await DepartmentService.getDepartmentById(id)
    if (result && (result.code === 200 || result.data)) {
      const departmentData = result.data.code === 200 ? result.data.data : result.data
      editDepartment.value = JSON.parse(JSON.stringify(departmentData))
    }
  } catch (error) {
    logger.error('获取院系信息失败:', error)
    ElMessage.error('获取院系信息失败: ' + (error.message || '未知错误'))
  }
}

//表单校验规则
const rules = ref({
name: [
{ required: true, message: '院系名称为必填项', trigger: 'blur' },
{ min: 2, max: 30, message: '院系名称长度为2-30个字', trigger: 'blur' }
]
})

//重置表单
const resetForm = (formRef) => {
if (!formRef || !formRef.value) return
formRef.value.resetFields()
}

//处理取消按钮点击事件
const handleCancel = () => {
  dialogFormVisible.value = false
  nextTick(() => {
    if (addDepartmentFormRef.value) {
      resetForm(addDepartmentFormRef)
    }
    if (editDepartmentFormRef.value) {
      resetForm(editDepartmentFormRef)
    }
    clearAddDepartmentForm()
    clearEditDepartmentForm()
  })
}

//-------------保存院系信息 
const saveDepartment = async (): Promise<void> => {
let formData, formRef

// 根据表单标题判断是新增还是编辑操作
if (formTitle.value === '新增院系') {
formData = addDepartment.value
formRef = addDepartmentFormRef
} else {
formData = editDepartment.value
formRef = editDepartmentFormRef
}

//表单校验
if (!formRef || !formRef.value) return
formRef.value.validate(async (valid) => {
if (valid) {
try {
let result
if (formTitle.value === '新增院系') {
// 新增操作
result = await DepartmentService.addDepartment(formData)
} else {
// 更新操作
result = await DepartmentService.updateDepartment(formData)
}

if (result && result.status === 200 && (result.data.code === 200 || result.data?.code === 200)) {
ElMessage.success('操作成功')
dialogFormVisible.value = false
await queryDepartments()
} else {
ElMessage.error('操作失败: ' + (result?.data?.msg || result?.msg || '未知错误'))
}
} catch (error) {
logger.error('保存院系信息失败:', error)
// 检查错误对象的结构，提供更精确的错误信息
if (error.response) {
ElMessage.error('保存失败: ' + (error.response.data?.msg || error.message || '未知错误'))
} else {
ElMessage.error('保存失败: ' + (error.message || '未知错误'))
}
}
} else {
return false
}
})
}

//关闭固定的专业栏
const handleCloseFixedMajors = () => {
fixedDepartmentId.value = null
searchMajorResult.value = null
}

//表格鼠标进入事件
const handleTableMouseEnter = (row) => {
// 确保传入的是正确的row对象
if (row && row.id) {
handleMouseEnterDepartment(row.id)
} else if (row && row.row && row.row.id) {
// 处理Element Plus v2.x的事件参数格式
handleMouseEnterDepartment(row.row.id)
}
}

//表格鼠标离开事件
const handleTableMouseLeave = () => {
handleMouseLeaveDepartment()
}

//获取院系名称
const getDepartmentName = (departmentId) => {
if (!departmentId) return ''
const department = departments.value.find(dept => dept.id === departmentId)
return department ? department.name : '未知院系'
}

//------- 删除院系
//根据ID删除单个院系
const delById = async (id): Promise<void> => {
  if (!id || id === 'undefined') {
    ElMessage.error('无效的院系ID')
    return
  }
  
  try {
    await ElMessageBox.confirm(
      '确定要删除该院系吗？此操作将删除该院系及其所有相关数据（包括专业、班级、学生等），此操作不可撤销！',
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
        center: true
      }
    )

    const result = await DepartmentService.deleteDepartment(id)
    if (result && (result.code === 200 || result.data?.code === 200)) {
      ElMessage.success('删除成功')
      await queryDepartments()
      await getMajorList()
      if (fixedDepartmentId.value === id) {
        fixedDepartmentId.value = null
      }
      if (hoveredDepartmentId.value === id) {
        hoveredDepartmentId.value = null
      }
    } else {
      ElMessage.error('删除失败: ' + (result?.msg || '未知错误'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      logger.error('删除院系失败:', error)
      ElMessage.error('删除失败: ' + (error.message || '未知错误'))
    }
  }
}

//清空搜索栏
const clear = () => {
clearMajorSearch()
searchDepartment.value = ''
searchDepartment.value = { name: '', majorId: null }
searchMajorName.value = ''
searchMajorResult.value = null
fixedDepartmentId.value = null
hoveredDepartmentId.value = null
queryDepartments()
}

// 合并后的搜索函数，处理院系和专业的查询逻辑
const searchBothDepartmentAndMajor = async (): Promise<void> => {
const departmentName = searchDepartment.value.name?.trim() || ''
const majorName = searchMajorName.value?.trim() || ''

// 仅输入院系时显示对应院系
if (departmentName && !majorName) {
logger.log('仅输入院系，查询院系列表')
fixedDepartmentId.value = null
searchMajorResult.value = null
await queryDepartments()
}
// 仅输入专业时显示全部院系，专业栏显示匹配结果
else if (!departmentName && majorName) {
logger.log('仅输入专业，显示全部院系，专业栏显示匹配结果')
// 先显示全部院系
searchDepartment.value.name = ''
await queryDepartments()
// 然后在专业栏显示匹配的专业
await searchMajorByName()
}
// 同时输入院系和专业时显示匹配的专业
else if (departmentName && majorName) {
logger.log('同时输入院系和专业，查询匹配的专业')

try {
// 先获取院系列表
await queryDepartments()

// 查找匹配的院系
const matchedDepartments = departments.value.filter(dept => 
dept.name.includes(departmentName)
)

if (matchedDepartments.length === 0) {
ElMessage.info('未找到匹配的院系')
return
}

// 按院系ID分组，找出同时匹配院系和专业的结果
const departmentIds = matchedDepartments.map(dept => dept.id)

// 筛选出同时匹配院系和专业的结果
const foundMajors = majorList.value.filter(major => 
departmentIds.includes(major.departmentId) && 
major.name.includes(majorName)
)

if (foundMajors.length > 0) {
// 按院系ID分组
const departmentMap = new Map()
foundMajors.forEach(major => {
const deptId = major.departmentId
if (!departmentMap.has(deptId)) {
departmentMap.set(deptId, [])
}
departmentMap.get(deptId).push(major)
})

// 转换为数组并获取院系信息
const departmentList = []
departmentMap.forEach((majors, deptId) => {
const department = departments.value.find(dept => dept.id === deptId)
departmentList.push({
id: deptId,
name: department ? department.name : '未知院系',
majors: majors
})
})

// 更新分页信息
searchMajorPagination.value.departments = departmentList
searchMajorPagination.value.total = departmentList.length
searchMajorPagination.value.currentPage = 1

// 触发显示
fixedDepartmentId.value = departmentList[0]?.id

logger.log(`搜索到${foundMajors.length}个专业，分布在${departmentList.length}个院系`)
} else {
ElMessage.info('未找到匹配的专业')
searchMajorResult.value = null
searchMajorPagination.value.departments = []
searchMajorPagination.value.total = 0
}
} catch (error) {
logger.error('搜索失败:', error)
ElMessage.error('搜索失败: ' + (error.message || '未知错误'))
}
}
// 两个都为空时显示全部院系
else {
logger.log('搜索框为空，显示全部院系列表')
fixedDepartmentId.value = null
searchMajorResult.value = null
await queryDepartments()
}
}
</script>
<template>
  <div class="department-major-management-container">
    <!-- 顶部标题区域 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">院系专业管理</h1>
        <p class="page-description">管理系统中的所有院系与专业信息</p>
      </div>
      <div class="header-illustration">
        <div class="illustration-circle circle-1"></div>
        <div class="illustration-circle circle-2"></div>
      </div>
    </div>

    <!-- 搜索区域卡片 -->
    <el-card class="search-card" shadow="never">
      <el-form ref="searchFormRef" :inline="true" :model="searchDepartment" class="search-form" @keyup.enter="searchBothDepartmentAndMajor">
        <div class="search-row">
          <el-form-item label="院系名称">
            <el-input
              v-model="searchDepartment.name"
              placeholder="请输入院系名称"
              clearable
              @keyup.enter="searchBothDepartmentAndMajor"
            ></el-input>
          </el-form-item>
          <el-form-item label="专业名称">
            <el-select
              v-model="searchMajorName"
              filterable
              clearable
              placeholder="请输入或选择专业名称"
              :filter-method="handleMajorFilter"
            >
              <el-option
                v-for="major in majorList"
                :key="major.id"
                :label="major.name"
                :value="major.name"
              ></el-option>
            </el-select>
          </el-form-item>
          <el-form-item class="search-actions">
            <el-button type="primary" @click="searchBothDepartmentAndMajor" class="search-btn">
              <el-icon><Search /></el-icon>&nbsp;查询
            </el-button>
            <el-button type="warning" @click="clear" class="reset-btn">
              <el-icon><Refresh /></el-icon>&nbsp;重置
            </el-button>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <!-- 操作按钮区域 -->
    <el-card class="actions-card" shadow="never">
      <div class="actions-container">
        <div class="primary-actions">
          <el-button v-if="authStore.hasPermission('department:add')" type="primary" @click="handleAddDepartment()" class="action-btn primary">
            <el-icon><Plus /></el-icon>&nbsp;新增院系
          </el-button>
          <el-button 
            v-if="authStore.hasPermission('major:add') && fixedDepartmentId"
            type="success" 
            @click="handleAddMajor" 
            class="action-btn success"
          >
            <el-icon><Plus /></el-icon>&nbsp;新增专业
          </el-button>
        </div>
        <div class="secondary-actions">
                    <el-dropdown v-if="authStore.hasPermission('department:view')" trigger="click" class="import-dropdown">
            <el-button type="primary" class="action-btn success" :loading="importLoading">
              <el-icon><Upload /></el-icon>&nbsp;导入 Excel<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="showImportDialog">选择文件导入</el-dropdown-item>
                <el-dropdown-item @click="downloadTemplate">下载导入模板</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button 
            v-if="authStore.hasPermission('department:view')"
            type="success" 
            @click="exportToExcel" 
            class="action-btn success"
            :loading="exportLoading"
          >
            <el-icon><Download /></el-icon>&nbsp;导出Excel
          </el-button>
          <!-- 隐藏的文件上传输入 -->
          <input 
            ref="excelFileInput"
            type="file" 
            style="display: none" 
            accept=".xlsx,.xls"
            @change="handleNativeFileChange"
          />
        </div>
      </div>
    </el-card>

    <!-- 数据列表卡片 -->
    <div class="tables-container">
      <!-- 左侧院系列表 -->
      <el-card class="table-card left-table" shadow="never">
        <div class="table-header">
          <h3 class="table-title">院系列表</h3>
          <div class="table-actions">
            <span class="total-count">共 {{ pagination.total }} 个院系</span>
          </div>
        </div>
        <el-table 
          :data="departments" 
          border 
          style="width: 100%" 
          fit 
          @cell-mouse-enter="handleTableMouseEnter"
          @cell-mouse-leave="handleTableMouseLeave"
          class="data-table"
          v-loading="loading"
        >
          <el-table-column type="index" label="序号" width="80" align="center" :index="(index) => (pagination.currentPage - 1) * pagination.pageSize + index + 1" />
          <el-table-column prop="name" label="院系名称" align="center" min-width="150" />
          <el-table-column prop="teacherCount" label="教师人数" align="center" width="100" />
          <el-table-column prop="studentCount" label="学生人数" align="center" width="100" />
          <el-table-column prop="updateTime" label="更新时间" width="180" align="center" :formatter="formatDate" />
          <el-table-column label="操作" align="center" width="200" fixed="right">
            <template #default="scope">
              <div class="action-buttons">
                <el-tooltip content="查看专业" placement="top">
                  <el-button 
                    :type="fixedDepartmentId === scope.row.id ? 'danger' : 'success'" 
                    size="small" 
                    @click="handleShowDepartmentMajors(scope.row.id)"
                    class="table-btn major"
                  >
                    <el-icon><View /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip v-if="authStore.hasPermission('department:edit')" content="编辑" placement="top">
                  <el-button type="primary" size="small" @click="handleUpdateDepartment(scope.row.id)" class="table-btn edit">
                    <el-icon><Edit /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip v-if="authStore.hasPermission('department:delete')" content="删除" placement="top">
                  <el-button type="danger" size="small" @click="delById(scope.row.id)" class="table-btn delete">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页组件 -->
        <div class="pagination-container">
          <el-pagination
            v-model:current-page="pagination.currentPage"
            v-model:page-size="pagination.pageSize"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            :total="pagination.total"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
            class="custom-pagination"
          />
        </div>
      </el-card>

      <!-- 右侧专业列表 -->
      <el-card 
        class="table-card right-table" 
        shadow="never"
      >
        <div class="table-header">
          <h3 class="table-title">
            {{ fixedDepartmentId || hoveredDepartmentId ? getDepartmentName(fixedDepartmentId || hoveredDepartmentId): '专业列表' }}
            <span class="major-count" v-if="displayedMajors.length > 0">({{ displayedMajors.length }}个专业)</span>
          </h3>
          <div class="table-actions">
            <el-button 
              v-if="fixedDepartmentId" 
              type="default" 
              size="small" 
              @click="handleCloseFixedMajors" 
              class="action-btn"
            >
              <el-icon><Close /></el-icon>&nbsp;关闭
            </el-button>
          </div>
        </div>
        <el-scrollbar height="400px">
          <el-table :data="displayedMajors" border style="width: 100%" fit class="data-table">
            <el-table-column type="index" label="序号" width="60" align="center" :index="(index) => index + 1" />
            <el-table-column prop="name" label="专业名称" align="center" min-width="150">
              <template #default="scope">
                <span class="major-name" @click.stop="handleMajorClick(scope.row)">{{ scope.row.name }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="studentCount" label="学生人数" align="center" width="100" />
            <el-table-column prop="updateTime" label="更新时间" width="180" align="center" :formatter="formatDate" />
            <el-table-column label="操作" align="center" width="120" fixed="right">
              <template #default="scope">
                <div class="action-buttons">
                  <el-tooltip v-if="authStore.hasPermission('major:edit')" content="编辑" placement="top">
                    <el-button type="primary" size="small" @click="handleEditMajor(scope.row)" class="table-btn edit">
                      <el-icon><Edit /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip v-if="authStore.hasPermission('major:delete')" content="删除" placement="top">
                    <el-button type="danger" size="small" @click="handleDeleteMajor(scope.row)" class="table-btn delete">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </el-tooltip>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="displayedMajors.length === 0" class="no-data">
            <el-empty 
              :description="fixedDepartmentId || hoveredDepartmentId ? '暂无专业数据' : '请将鼠标悬停到左侧院系查看专业信息'" 
              :image-size="100" 
            />
          </div>
        </el-scrollbar>

        <!-- 搜索结果院系分页 -->
        <div v-if="searchMajorPagination.total > 0" class="search-pagination">
          <el-pagination 
            v-model:current-page="searchMajorPagination.currentPage" 
            layout="prev, pager, next" 
            :page-size="1" 
            :total="searchMajorPagination.total" 
            @current-change="handleSearchMajorPageChange" 
          />
        </div>
      </el-card>
    </div>

    <!-- 新增/修改院系对话框 -->
    <el-dialog v-model="dialogFormVisible" :title="formTitle" width="500px" @close="handleCancel" class="form-dialog">
      <!-- 条件渲染：根据表单标题显示对应的表单 -->
      <template v-if="formTitle === '新增院系'">
        <el-form ref="addDepartmentFormRef" :model="addDepartment" :rules="rules" :label-width="labelWidth">
          <el-form-item label="院系名称" prop="name">
            <el-input v-model="addDepartment.name" placeholder="请输入院系名称" />
          </el-form-item>
        </el-form>
      </template>

      <template v-else-if="formTitle === '编辑院系'">
        <el-form ref="editDepartmentFormRef" :model="editDepartment" :rules="rules" :label-width="labelWidth">
          <el-form-item label="院系名称" prop="name">
            <el-input v-model="editDepartment.name" placeholder="请输入院系名称" />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="handleCancel" class="cancel-btn">取消</el-button>
          <el-button type="primary" @click="saveDepartment" class="confirm-btn">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 新增专业对话框 -->
    <el-dialog v-model="addMajorDialogVisible" title="新增专业" width="400px" @close="closeAddMajorDialog" class="form-dialog">
      <el-form ref="addMajorFormRef" :model="addMajorForm" :rules="addMajorRules" label-width="80px">
        <el-form-item label="院系名称" disabled>
          <el-input :value="getDepartmentName(addMajorForm.departmentId)" readonly />
        </el-form-item>
        <el-form-item label="专业名称" prop="name">
          <el-input v-model="addMajorForm.name" placeholder="请输入专业名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeAddMajorDialog" class="cancel-btn">取消</el-button>
          <el-button type="primary" @click="saveMajor" class="confirm-btn">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 编辑专业对话框 -->
    <el-dialog v-model="editMajorDialogVisible" title="编辑专业" width="400px" @close="closeEditMajorDialog" class="form-dialog">
      <el-form ref="editMajorFormRef" :model="editMajorForm" :rules="editMajorRules" label-width="80px">
        <el-form-item label="院系名称" disabled>
          <el-input :value="getDepartmentName(editMajorForm.departmentId)" readonly />
        </el-form-item>
        <el-form-item label="专业名称" prop="name">
          <el-input v-model="editMajorForm.name" placeholder="请输入专业名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeEditMajorDialog" class="cancel-btn">取消</el-button>
          <el-button type="primary" @click="saveEditMajor" class="confirm-btn">确定</el-button>
        </span>
      </template>
    </el-dialog>
    
    <!-- 导入 Excel对话框 -->
    <el-dialog
      v-model="importDialogVisible"
      title="导入院系专业数据"
      width="40%"
      :before-close="closeImportDialog"
    >
      <div class="import-dialog-content">
        <div class="import-tips">
          <p class="tip-text">
            <span class="tip-icon">⚠️</span>
            请确保Excel文件格式正确，包含以下字段：院系名称、专业名称。
          </p>
          <p class="tip-text">
            <span class="tip-icon">⚠️</span>
            如果院系不存在，系统将自动创建新院系。
          </p>
        </div>
        
        <div class="file-select-section">
          <el-button 
            type="primary" 
            @click="handleChooseFile"
            :loading="importLoading"
            class="choose-file-btn"
          >
            选择Excel文件
          </el-button>
          
          <div v-if="selectedFile" class="selected-file-info">
            <span class="file-icon">📁</span>
            <span class="file-name">{{ selectedFile.name }}</span>
          </div>
          <div v-else class="no-file-selected">
            未选择文件
          </div>
        </div>
        
        <div class="import-note">
          <p>支持的文件格式：.xlsx、.xls</p>
          <p>文件大小上限：5MB</p>
        </div>
      </div>
      
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="closeImportDialog" class="cancel-btn">取消</el-button>
          <el-button 
            type="primary" 
            @click="importExcel" 
            :loading="importLoading" 
            class="confirm-btn"
            :disabled="!selectedFile"
          >
            导入
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

    <style scoped>
.department-major-management-container {
    padding: 20px;

  /* 导入对话框样式 */
  .import-dialog-content {
    padding: 20px 0;
  }
  
  .import-tips {
    background-color: #f0f9ff;
    border: 1px solid #e6f7ff;
    border-radius: 8px;
    padding: 16px;
    margin-bottom: 20px;
  }
  
  .tip-text {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    margin-bottom: 10px;
    font-size: 14px;
    color: #409EFF;
  }
  
  .tip-text:last-child {
    margin-bottom: 0;
  }
  
  .tip-icon {
    font-size: 16px;
    flex-shrink: 0;
  }
  
  .file-select-section {
    text-align: center;
    padding: 20px;
    border: 2px dashed #d9d9d9;
    border-radius: 8px;
    margin-bottom: 20px;
    transition: border-color 0.3s;
  }
  
  .file-select-section:hover {
    border-color: #409EFF;
  }
  
  .choose-file-btn {
    border-radius: 6px;
    padding: 10px 24px;
  }
  
  .selected-file-info {
    margin-top: 15px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    font-size: 14px;
    color: #606266;
  }
  
  .no-file-selected {
    margin-top: 15px;
    font-size: 14px;
    color: #909399;
  }
  
  .import-note {
    font-size: 12px;
    color: #909399;
    text-align: center;
  }
  
  .import-note p {
    margin: 4px 0;
  }
  
  .dialog-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
  }
  
  .cancel-btn, .confirm-btn {
    border-radius: 6px;
    padding: 8px 16px;
  }
  background: #f5f7fa;
  min-height: 100vh;
}

/* 页面标题区域 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  border-radius: 16px;
  padding: 24px 32px;
  color: white;
  margin-bottom: 24px;
  box-shadow: 0 8px 25px rgba(64, 158, 255, 0.25);
  position: relative;
  overflow: hidden;
}

.page-header::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -50%;
  width: 100%;
  height: 100%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
  transform: rotate(30deg);
}

.header-content h1 {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
  color: white;
}

.header-content p {
  font-size: 14px;
  opacity: 0.95;
  font-weight: 500;
  margin: 0;
}

.header-illustration {
  position: relative;
  width: 100px;
  height: 100px;
}

.illustration-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  animation: float 6s ease-in-out infinite;
}

.circle-1 {
  width: 60px;
  height: 60px;
  top: 0;
  right: 0;
  animation-delay: 0s;
}

.circle-2 {
  width: 40px;
  height: 40px;
  bottom: 10px;
  right: 20px;
  animation-delay: 3s;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

/* 卡片通用样式 */
.search-card,
.actions-card,
.table-card {
  border-radius: 16px;
  border: none;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  margin-bottom: 24px;
  overflow: hidden;
}

.search-card {
  padding: 24px;
}

.actions-card {
  padding: 20px 24px;
}

.table-card {
  padding: 0;
}

/* 搜索表单样式 */
.search-form {
  width: 100%;
}

.search-row {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.search-row .el-form-item {
  margin-bottom: 0;
  flex: 1;
}

.search-actions {
  flex: none;
  margin-left: auto;
}

.search-btn,
.reset-btn {
  border-radius: 8px;
  padding: 10px 20px;
}

/* 操作按钮区域 */
.actions-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.primary-actions,
.secondary-actions {
  display: flex;
  gap: 12px;
}

.action-btn {
  border-radius: 8px;
  padding: 10px 20px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.action-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

/* 表格容器布局 */
.tables-container {
  display: flex;
  gap: 20px;
  width: 100%;
}

.left-table {
  flex: 1;
  min-width: 400px;
}

.right-table {
  flex: 0 0 450px;
  margin-left: auto;
}

/* 表格头部样式 */
.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 20px 10px;
  border-bottom: 1px solid #f0f0f0;
}

.table-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.major-count {
  font-size: 14px;
  font-weight: normal;
  color: #909399;
}

.table-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.total-count {
  font-size: 14px;
  color: #909399;
}

/* 表格样式 */
.data-table {
  border-radius: 8px;
}

.data-table :deep(.el-table__header) th {
  background-color: #f8fbff;
  color: #409EFF;
  font-weight: 600;
}

.data-table :deep(.el-table__row) td {
  border-bottom: 1px solid #f0f7ff;
}

/* 专业名称样式 */
.major-name {
  color: #409EFF;
  cursor: pointer;
  font-weight: 500;
  transition: color 0.3s;
}

.major-name:hover {
  color: #66b1ff;
}

/* 表格操作按钮 */
.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
  align-items: center;
}

.table-btn {
  border-radius: 6px;
  padding: 6px 8px;
  transition: all 0.3s ease;
}

.table-btn:hover {
  transform: scale(1.1);
}

/* 分页样式 */
.pagination-container {
  padding: 20px;
  display: flex;
  justify-content: flex-end;
}

.custom-pagination {
  margin-top: 0;
}

.no-data {
  padding: 40px 20px;
  text-align: center;
}

/* 搜索结果分页 */
.search-pagination {
  margin-top: 10px;
  display: flex;
  justify-content: center;
  padding: 10px 15px;
  background-color: #f5f7fa;
  border-top: 1px solid #e6e6e6;
}

/* 对话框样式 */
.form-dialog {
  border-radius: 16px;
  overflow: hidden;
}

.form-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #f8fbff 0%, #e6f7ff 100%);
  margin: 0;
  padding: 20px;
  border-bottom: 1px solid #e6f7ff;
}

/* 对话框按钮 */
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.cancel-btn,
.confirm-btn {
  border-radius: 8px;
  padding: 10px 20px;
}

.confirm-btn {
  background: linear-gradient(135deg, #409EFF, #52c41a);
  border: none;
}

.confirm-btn:hover {
  background: linear-gradient(135deg, #66b1ff, #73d13d);
  transform: translateY(-1px);
}

/* 响应式设计 */
@media screen and (max-width: 1200px) {
  .tables-container {
    flex-direction: column;
  }
  
  .right-table {
    flex: 1;
  }
  
  .search-row {
    flex-wrap: wrap;
  }
  
  .search-actions {
    margin-left: 0;
    width: 100%;
    justify-content: flex-end;
  }
}

@media screen and (max-width: 768px) {
  .department-major-management-container {
    padding: 15px;
  }
  
  .page-header {
    flex-direction: column;
    text-align: center;
    padding: 20px;
  }
  
  .header-illustration {
    margin-top: 15px;
  }
  
  .actions-container {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  
  .primary-actions,
  .secondary-actions {
    justify-content: center;
  }
  
  .action-buttons {
    flex-wrap: wrap;
    justify-content: center;
  }
  
  .search-row {
    flex-direction: column;
    gap: 12px;
  }
  
  .search-actions {
    width: 100%;
    justify-content: center;
  }
  
  .data-table {
    font-size: 12px;
  }
  
  .action-buttons {
    gap: 4px;
  }
  
  .table-btn {
    padding: 4px 6px;
  }
  
  .table-header {
    flex-direction: column;
    gap: 10px;
    align-items: flex-start;
  }
}
</style>