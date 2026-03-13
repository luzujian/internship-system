<script setup lang="ts">
import logger from '@/utils/logger'
import { createRequiredValidator, createPhoneValidator, createEmailValidator } from '@/utils/validation'
import { onMounted, ref, nextTick, computed } from 'vue'
import DepartmentService from '../../api/department'
import MajorService from '../../api/major'
import TeacherUserService from '../../api/TeacherUserService'
import { useSystemSettingsStore } from '../../store/systemSettings'
import { useAuthStore } from '../../store/auth'

import cacheService from '../../api/cacheService'
import { Search, Refresh, Plus, Delete, Upload, Download, Document, School, View, ArrowDown } from '@element-plus/icons-vue'
import type { TeacherUser, PageParams, PaginationState, DialogState, Department, Major } from '@/types/admin'
import { ElMessage, ElMessageBox, ElForm, ElTable } from 'element-plus'
import { queryPageApi, addApi, queryInfoApi, updateApi, deleteApi, resetPasswordApi, batchDeleteApi, exportTeacherDataApi, importTeacherDataApi, updateStatusApi } from '../../api/TeacherUserService'
import * as XLSX from 'xlsx'

const systemSettingsStore = useSystemSettingsStore()
const authStore = useAuthStore()

// 日期格式化方法 - 用于表格中日期列的显示
const formatDate = (_row: unknown, _column: unknown, cellValue: string): string => {
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

const getTeacherTypeText = (teacherType: string): string => {
  const typeMap = {
    'COLLEGE': '学院教师',
    'DEPARTMENT': '系室教师',
    'COUNSELOR': '辅导员'
  }
  return typeMap[teacherType] || '-'
}

const getTeacherTypeTagType = (teacherType: string): string => {
  const typeMap = {
    'COLLEGE': 'danger',
    'DEPARTMENT': 'primary',
    'COUNSELOR': 'success'
  }
  return typeMap[teacherType] || 'info'
}

// 院系名称格式化方法 - 用于表格中院系列的显示
const formatDepartmentName = (row: unknown, column: unknown, cellValue: string): string => {
  if (!cellValue) return ''

  // 从departments数组中查找对应的院系名称，确保类型一致
  const department = departments.value.find(item => item.value === String(cellValue))
  return department ? department.name : '未知院系'
}

// 专业名称格式化方法 - 用于格式化课程的专业显示
const formatCourseMajorNames = (course: unknown): string => {
  // 尝试多种可能的数据结构
  if (course.majorNames && Array.isArray(course.majorNames) && course.majorNames.length > 0) {
    // 已有的专业名称数组
    return course.majorNames.join('、')
  } else if (course.majors && Array.isArray(course.majors) && course.majors.length > 0) {
    // 专业对象数组，提取名称
    return course.majors.map(m => m.name || m.majorName || '未知').join('、')
  } else if (course.majorName) {
    // 单个专业名称
    return course.majorName
  } else if (course.majorId) {
    // 只有专业ID，尝试从专业列表匹配
    const major = majors.value.find(m => m.value === course.majorId)
    return major ? major.name : '未知专业'
  }
  return '未分配专业'
}

//搜索表单对象
let searchForm = ref<Partial<TeacherUser>>({
  teacherUserId: '',
  name: '',
  gender: '',
  department: null,
  status: null,
  teacherType: ''
})

//列表展示数据
let tableData = ref<TeacherUser[]>([])
//院系列表数据
let departments = ref<Department[]>([])
//专业列表数据
let majors = ref<Major[]>([])
//加载状态
let loading = ref(false)

//分页数据
let pagination = ref<PaginationState>({
  currentPage: 1,
  pageSize: 10,
  total: 0
})

// 应用本地存储的教师状态
const applyLocalTeacherStatus = (): void => {
  try {
    const teacherStatusMap = JSON.parse(localStorage.getItem('teacherStatusMap') || '{}')
    
    if (tableData.value) {
      tableData.value = tableData.value.map(teacher => {
        const localStatus = teacherStatusMap[teacher.id]
        if (localStatus !== undefined) {
          // 如果localStorage中存在该教师的状态，则应用
          return {
            ...teacher,
            status: localStatus
          }
        } else {
          // 确保新创建的教师默认状态为启用(status=1)
          // 如果教师对象中没有status字段或status为undefined，则设置为1
          if (teacher.status === undefined) {
            return {
              ...teacher,
              status: 1
            }
          }
          return teacher
        }
      })
    }
  } catch (error) {
    logger.error('应用本地教师状态失败:', error)
  }
}

// 表格引用
let teacherTable = ref<InstanceType<typeof ElTable> | null>(null)

// 为新增和编辑操作创建独立的表单数据和引用，避免数据相互干扰
// 新增教师表单引用
let addTeacherFormRef = ref<InstanceType<typeof ElForm> | null>(null)
// 编辑教师表单引用
let editTeacherFormRef = ref<InstanceType<typeof ElForm> | null>(null)

// 新增教师表单数据
let addTeacher = ref<Partial<TeacherUser>>({
  id: '',
  teacherUserId: '',
  name: '',
  gender: 1,
  departmentId: null,
  teacherType: 'DEPARTMENT'
})

// 编辑教师表单数据
let editTeacher = ref<Partial<TeacherUser>>({
  id: '',
  teacherUserId: '',
  name: '',
  gender: 1,
  departmentId: null,
  teacherType: 'DEPARTMENT'
})

//对话框控制
let dialogFormVisible = ref(false)
let formTitle = ref('')
let labelWidth = ref('100px')

//重置密码对话框控制
let resetPasswordDialogVisible = ref(false)
let resetPasswordUserId = ref<string | null>(null)

//导入 Excel相关控制
let importDialogVisible = ref(false)
let selectedFile = ref<File | null>(null)
let excelFileInput = ref<HTMLElement | null>(null)
let importLoading = ref(false)

//复选框选中数据
let selectIds = ref<string[]>([])

//查看课程对话框相关
let viewCoursesDialogVisible = ref(false)
let teacherCourses = ref<unknown[]>([])
let currentTeacherName = ref('')
let currentTeacherId = ref<string | null>(null)

//表单规则
const rules = {
  teacherUserId: [createRequiredValidator('请输入工号')],
  name: [createRequiredValidator('请输入姓名')],
  departmentId: [createRequiredValidator('请选择院系')],
  phone: createPhoneValidator('请输入 11 位中国大陆手机号'),
  email: createEmailValidator('请输入有效的邮箱地址，如 example@email.com')
}

// 移除动态计算表单，现在使用两个独立的表单

// 获取院系列表
const getDepartmentList = async () => {
  try {
    logger.log('开始获取院系列表...')
    // 清除缓存，确保获取最新数据
    cacheService.delete('all_departments')
    const response = await DepartmentService.getDepartments()
    logger.log('院系列表完整响应:', JSON.stringify(response))

    // 增强数据结构兼容性处理
    let departmentData = []
    if (response && response.data) {
      // 处理Result对象格式: { code: 200, data: [departments] }
      if (response.data.code === 200 && response.data.data) {
        departmentData = Array.isArray(response.data.data) ? response.data.data : []
      } 
      // 处理直接返回数组的情况
      else if (Array.isArray(response.data)) {
        departmentData = response.data
      }
      // 处理其他可能的格式
      else {
        logger.warn('未知的院系列表数据格式:', response.data)
      }
    } else {
      logger.warn('院系列表响应数据为空:', response)
    }

    logger.log('处理后的原始院系列表数据:', JSON.stringify(departmentData))

    // 确保数据格式正确，为每个院系对象添加value和name属性
    // 将value转换为字符串类型，确保与updateTeacher方法中的类型处理一致
    departments.value = departmentData
      .filter(department => department && (department.id || department.departmentId)) // 过滤无效数据
      .map(department => {
        const deptId = String(department.id || department.departmentId || '')
        const deptName = department.name || department.departmentName || '未知院系'
        logger.log(`处理院系: ID=${deptId}, Name=${deptName}`)
        return {
          value: deptId,
          name: deptName
        }
      })

    logger.log('最终转换后的院系列表数据:', JSON.stringify(departments.value))
  } catch (error) {
    logger.error('获取院系列表失败:', error)
    departments.value = []
    ElMessage.error('获取院系列表失败: ' + (error.message || '未知错误'))
  }
}

// 获取专业列表
const getMajorList = async () => {
  try {
    logger.log('开始获取专业列表...')
    // 使用专门的MajorService获取专业列表
    const response = await MajorService.getMajors()
    logger.log('专业列表响应:', response)

    //增强数据结构兼容性处理
    let majorData = []
    if (response && response.data) {
      if (Array.isArray(response.data)) {
        majorData = response.data
      } else if (response.data.code === 200 && response.data.data) {
        //处理带code和data的响应格式
        majorData = Array.isArray(response.data.data) ? response.data.data : []
      }
    }

    logger.log('原始专业列表数据:', majorData)

    //确保数据格式正确，为每个专业对象添加value和name属性
    majors.value = majorData.map(major => ({
      value: String(major.id || major.majorId || ''),
      name: major.name || major.majorName || '未知专业'
    }))

    logger.log('转换后的专业列表数据:', majors.value)
  } catch (error) {
    logger.error('获取专业列表失败:', error)
    majors.value = []
    // 这里不显示错误提示，因为专业列表不是页面必需的
  }
}

//处理复选框选中变化
const handleSelectionChange = (selection) => {
  selectIds.value = selection.map(item => item.id)
}

//分页处理函数
const handleSizeChange = (newSize) => {
  pagination.value.pageSize = newSize
  queryPage()
}

const handleCurrentChange = (newCurrent) => {
  pagination.value.currentPage = newCurrent
  queryPage()
}

// 分页查询函数
const queryPage = async () => {
  loading.value = true
  try {
    logger.log('开始查询教师列表...')
    //构建查询参数，确保字段名称正确映射
    const params = {
      pageNum: pagination.value.currentPage,
      pageSize: pagination.value.pageSize
    }
    
    // 只有当字段有值时才添加到参数中，特别处理status字段（值为0也需要传递）
    if (searchForm.value.teacherUserId) {
      params.teacherUserId = searchForm.value.teacherUserId
    }
    if (searchForm.value.name) {
      params.name = searchForm.value.name
    }
    if (searchForm.value.department !== null && searchForm.value.department !== undefined) {
      params.departmentId = searchForm.value.department
    }
    // 对于status字段，即使值为0也需要传递
    if (searchForm.value.status !== null && searchForm.value.status !== undefined) {
      params.status = searchForm.value.status
    }
    // 添加性别参数
    if (searchForm.value.gender !== null && searchForm.value.gender !== undefined && searchForm.value.gender !== '') {
      params.gender = searchForm.value.gender
    }
    // 添加教师类型参数
    if (searchForm.value.teacherType !== null && searchForm.value.teacherType !== undefined && searchForm.value.teacherType !== '') {
      params.teacherType = searchForm.value.teacherType
    }

    logger.log('查询参数:', params)
    const response = await queryPageApi(params)
    logger.log('查询结果响应:', response)

    //使用标准的响应格式
    if (response && response.code === 200 && response.data) {
      //获取原始数据列表
      const dataList = response.data.rows || []
      const totalCount = response.data.total || 0

      //添加院系名称到数据中
      const enhancedData = dataList.map(teacher => {
        //找到对应的院系，使用value而不是id进行匹配
        const department = departments.value.find(d => String(d.value) === String(teacher.departmentId))
        return {
          ...teacher,
          departmentName: department ? department.name : '未知院系',
          // 确保日期字段名称与表格中使用的一致
          createdTime: teacher.createdTime || teacher.createTime,
          updatedTime: teacher.updatedTime || teacher.updateTime
        }
      })

      tableData.value = enhancedData
      pagination.value.total = totalCount
      logger.log('处理后的数据列表:', tableData.value)
      
      // 在下一个tick中，根据selectIds恢复选中状态
      nextTick(() => {
        if (tableData.value && tableData.value.length > 0) {
          tableData.value.forEach(row => {
            if (selectIds.value.includes(row.id)) {
              teacherTable.value.toggleRowSelection(row, true)
            }
          })
        }
      })
    } else {
      tableData.value = []
      pagination.value.total = 0
      logger.log('无数据或响应格式不正确')
    }
    
    // 直接使用后端返回的真实状态数据，不再应用本地存储的模拟状态
  } catch (error) {
    logger.error('查询教师列表失败:', error)
    tableData.value = []
    pagination.value.total = 0
    ElMessage.error('查询教师列表失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 移除重复定义的applyLocalTeacherStatus函数 - 函数已在前面定义


//清空搜索表单
const clear = () => {
  searchForm.value = {
    teacherId: '',
    name: '',
    departmentId: null,
    status: null,
    teacherType: ''
  }
  pagination.value.currentPage = 1
}

//重置表单
const resetForm = (formRef) => {
  if (formRef && formRef.value) {
    setTimeout(() => {
      formRef.value.resetFields()
    }, 0)
  }
}

//清空新增教师表单数据
const clearAddTeacherForm = () => {
  addTeacher.value = {
    id: '',
    teacherId: '',
    name: '',
    gender: 1,
    departmentId: null,
    teacherType: 'DEPARTMENT'
  }
}

//清空编辑教师表单数据
const clearEditTeacherForm = () => {
  editTeacher.value = {
    id: '',
    teacherId: '',
    name: '',
    gender: 1,
    departmentId: null,
    teacherType: 'DEPARTMENT'
  }
}

//处理取消按钮点击事件
const handleCancel = () => {
  // 重要：先关闭对话框
  dialogFormVisible.value = false
  
  // 使用nextTick确保DOM已经更新，然后再执行清空操作
  nextTick(() => {
    // 1. 完全清空所有表单数据对象
    clearAddTeacherForm()
    clearEditTeacherForm()
    
    // 2. 额外的保险措施：手动重置每个字段
    // 针对新增表单数据的额外清空
    if (addTeacher.value) {
      addTeacher.value.id = ''
      addTeacher.value.teacherId = ''
      addTeacher.value.name = ''
      addTeacher.value.departmentId = null
      addTeacher.value.teacherType = 'DEPARTMENT'
    }
    
    // 针对编辑表单数据的额外清空
    if (editTeacher.value) {
      editTeacher.value.id = ''
      editTeacher.value.teacherId = ''
      editTeacher.value.name = ''
      editTeacher.value.departmentId = null
      editTeacher.value.teacherType = 'DEPARTMENT'
    }
    
    // 3. 重置表单状态和校验
    if (addTeacherFormRef && addTeacherFormRef.value) {
      try {
        addTeacherFormRef.value.resetFields()
      } catch (error) {
        logger.warn('重置新增表单失败:', error)
      }
    }
    
    if (editTeacherFormRef && editTeacherFormRef.value) {
      try {
        editTeacherFormRef.value.resetFields()
      } catch (error) {
        logger.warn('重置编辑表单失败:', error)
      }
    }
    
    logger.log('编辑框已关闭并完全清空数据')
  })
}

//新增教师
const handleAddTeacher = () => {
  formTitle.value = '新增教师'
  clearAddTeacherForm()
  // 在下一个tick中重置表单校验，确保在对话框打开前重置完成
  nextTick(() => {
    resetForm(addTeacherFormRef)
    dialogFormVisible.value = true
  })
}

// 更新学生 - 完全重写版本，确保数据正确更新
const handleUpdateTeacher = async (id) => {
  try {
    logger.log('开始获取教师详情，ID:', id)
    
    // 1. 第一步：完全重置所有相关状态
    // 立即关闭对话框
    dialogFormVisible.value = false
    
    // 强制清空表单数据对象，创建一个全新的对象
    editTeacher.value = {
      id: '',
      teacherId: '',
      name: '',
      departmentId: null,
    }
    
    // 重置表单引用状态
    if (editTeacherFormRef.value) {
      try {
        editTeacherFormRef.value.resetFields()
      } catch (error) {
        logger.warn('重置表单失败，但继续操作:', error)
      }
    }
    
    // 2. 等待DOM更新完成
    await nextTick()
    
    // 3. 获取新的教师数据
    const response = await queryInfoApi(id)
    logger.log('教师详情响应:', response)

    if (response && response.code === 200 && response.data) {
      const teacherData = response.data
      formTitle.value = '编辑教师'
      
      logger.log('准备设置新的教师数据:', teacherData)
      
      // 4. 使用强制更新的方式设置数据
      // 强制替换整个对象，确保Vue能检测到变化
      Object.assign(editTeacher.value, {
        id: teacherData.id || '',
        teacherUserId: teacherData.teacherUserId || '',
        name: teacherData.name || '',
        gender: teacherData.gender || 1,
        // 确保departmentId类型与departments数组中的value类型一致
        // 先检查departments数组是否为空
        departmentId: departments.value.length > 0 && teacherData.departmentId !== undefined ? 
          // 检查departments数组中第一个元素的value类型，然后进行相应转换
          (typeof departments.value[0].value === 'number' ? Number(teacherData.departmentId) : teacherData.departmentId.toString()) : 
          null,
        teacherType: teacherData.teacherType || 'DEPARTMENT'
      })
      
      logger.log('设置后的departmentId类型:', typeof editTeacher.value.departmentId)
      
      // 5. 再次等待DOM更新
      await nextTick()
      
      logger.log('设置后的editTeacher数据:', editTeacher.value)
      
      // 6. 最后显示对话框
      dialogFormVisible.value = true
      
      // 7. 对话框显示后再次确保数据正确绑定
      nextTick(() => {
        logger.log('对话框已显示，最终绑定的数据:', editTeacher.value)
      })
    } else {
      ElMessage.error('获取教师信息失败: 无效的响应格式')
    }
  } catch (error) {
    logger.error('获取教师信息失败:', error)
    ElMessage.error('获取教师信息失败: ' + (error.message || '未知错误'))
    // 出错时确保对话框是关闭的
    dialogFormVisible.value = false
  }
}

//保存教师
const saveTeacher = async () => {
  try {
    let formData, formRef, api
    
    // 根据表单标题判断是新增还是编辑操作
    if (formTitle.value === '新增教师') {
      formData = addTeacher.value
      formRef = addTeacherFormRef
      //新增操作 - 添加默认密码
      api = addApi({ ...formData, password: '123456' })
    } else {
      formData = editTeacher.value
      formRef = editTeacherFormRef
      // 更新操作
      api = updateApi(formData)
    }
    
    // 增加更健壮的表单引用检查和处理
    if (!formRef) {
      logger.error('表单引用未定义', { formRef });
      ElMessage.error('表单引用不存在')
      return
    }
    
    // 使用nextTick确保DOM已经更新
    await nextTick();
    
    // 检查表单实例是否存在
    if (!formRef.value) {
      logger.error('表单组件未挂载', { formRef });
      ElMessage.error('表单组件尚未加载完成，请稍候重试')
      return
    }
    
    // 执行表单验证
    await formRef.value.validate();

    logger.log('提交的教师数据:', formData)
    const response = await api

    // 增强数据结构兼容性处理
    if (response && response.code === 200) {
      ElMessage.success(formTitle.value === '编辑教师' ? '更新成功' : '新增成功')
      dialogFormVisible.value = false
      queryPage() // 重新加载列表
    } else {
      ElMessage.error((formTitle.value === '编辑教师' ? '更新' : '新增') + '失败: ' + (response?.msg || '未知错误'))
    }
  } catch (error) {
    logger.error((formTitle.value === '编辑教师' ? '更新' : '新增') + '教师失败:', error)
    if (error.name !== 'ValidationError') {
      ElMessage.error((formTitle.value === '编辑教师' ? '更新' : '新增') + '教师失败: ' + (error.message || '未知错误'))
    }
  }
}

// 删除单个教师
const delById = async (id) => {
  try {
    await ElMessageBox.confirm(
      '确定要删除该教师吗？此操作将删除该教师及其所有相关数据，此操作不可撤销！',
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
        center: true
      }
    )

    const response = await deleteApi(id)

    if (response && response.code === 200) {
      ElMessage.success('删除成功')
      queryPage()
    } else {
      ElMessage.error('删除失败: ' + (response?.msg || '未知错误'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      logger.error('删除教师失败:', error)
      ElMessage.error('删除失败: ' + (error.message || '未知错误'))
    }
  }
}

// 批量删除教师
const delByIds = async () => {
  try {
    if (selectIds.value.length === 0) {
      ElMessage.warning('请至少选择一个教师')
      return
    }

    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectIds.value.length} 名教师吗？此操作将删除这些教师及其所有相关数据，此操作不可撤销！`,
      '确认批量删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
        center: true
      }
    )

    const response = await batchDeleteApi(selectIds.value)

    if (response && response.code === 200) {
      ElMessage.success('删除成功')
      selectIds.value = []
      queryPage()
    } else {
      ElMessage.error('删除失败: ' + (response?.msg || '未知错误'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      logger.error('批量删除教师失败:', error)
      ElMessage.error('删除失败: ' + (error.message || '未知错误'))
    }
  }
}

//删除所有教师用户
const deleteAllTeachers = async () => {
  try {
    // 二次确认，防止误操作
    await ElMessageBox.confirm('确定要删除所有教师用户数据吗？此操作不可撤销！', '危险操作确认', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'error',
      center: true
    })

    // 获取所有教师用户的ID
    const allTeachersResponse = await TeacherUserService.getAllTeachers()
    if (!allTeachersResponse || !allTeachersResponse.data || allTeachersResponse.code !== 200) {
      ElMessage.error('获取教师列表失败，无法执行删除操作')
      return
    }

    const allTeachers = allTeachersResponse.data.rows || allTeachersResponse.data
    if (!allTeachers || allTeachers.length === 0) {
      ElMessage.success('当前没有教师用户数据')
      return
    }

    // 提取所有教师的ID
    const allTeacherIds = allTeachers.map(teacher => teacher.id)

    // 执行批量删除
    const deleteResponse = await batchDeleteApi(allTeacherIds)

    // 增强数据结构兼容性处理
    if (deleteResponse && deleteResponse.code === 200) {
      ElMessage.success('所有教师用户数据已成功删除')
      selectIds.value = []
      queryPage() // 重新加载列表
    } else {
      ElMessage.error('删除失败: ' + (deleteResponse?.msg || '未知错误'))
    }
  } catch (error) {
    logger.error('删除所有教师用户失败:', error)
    if (error !== 'cancel') {
      ElMessage.error('删除失败: ' + (error.message || '未知错误'))
    }
  }
}

//显示重置密码对话框
const showResetPasswordDialog = (row) => {
  resetPasswordUserId.value = row.id
  resetPasswordDialogVisible.value = true
}

//重置密码
const resetUserPassword = async () => {
  try {
    const response = await resetPasswordApi(resetPasswordUserId.value)

    // 增强数据结构兼容性处理
    if (response && response.code === 200) {
      ElMessage.success('密码重置成功')
      resetPasswordDialogVisible.value = false
    } else {
      ElMessage.error('密码重置失败: ' + (response?.msg || '未知错误'))
    }
  } catch (error) {
    logger.error('重置密码失败:', error)
    ElMessage.error('密码重置失败: ' + (error.message || '未知错误'))
  }
}

// 显示导入 Excel对话框
const showImportDialog = () => {
  selectedFile.value = null
  importDialogVisible.value = true
  
  // 不要在对话框刚显示时自动触发文件选择，让用户手动点击按钮
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
    
    logger.log('Native selected file:', file)
    selectedFile.value = file
    logger.log('selectedFile after native selection:', selectedFile.value)
  } else {
    logger.warn('No file selected via native input')
  }
}

// 处理文件选择按钮点击
const handleChooseFile = () => {
  if (excelFileInput.value && typeof excelFileInput.value.click === 'function') {
    excelFileInput.value.click()
  } else {
    logger.warn('excelFileInput is not available or click is not a function')
    ElMessage.warning('文件选择功能暂不可用，请稍后再试')
  }
}

// 导入 Excel数据
const importExcel = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择Excel文件')
    return
  }

  importLoading.value = true
  try {
    // 直接传递文件对象给API，API内部会处理FormData
    const response = await importTeacherDataApi(selectedFile.value)
    
    if (response && response.code === 200) {
      // 获取导入结果数据
      const importResult = response.data || {}
      const successCount = importResult.successCount || 0
      const failCount = importResult.failCount || 0
      const failList = importResult.failList || []
      
      // 构建导入结果消息
      let message = `导入完成，成功: ${successCount} 条，失败: ${failCount} 条`
      
      // 如果有失败记录，显示失败详情
      if (failList.length > 0) {
        let errorDetails = '\n失败详情:\n'
        failList.forEach((fail, index) => {
          errorDetails += `${index + 1}. 第${fail.rowNum}行: ${fail.errorMsg || '未知错误'}\n`
        })
        
        // 显示包含详细信息的消息框
        ElMessageBox.alert(message + errorDetails, '导入结果', {
          type: failCount > 0 ? 'warning' : 'success',
          confirmButtonText: '确定',
          dangerouslyUseHTMLString: false,
          callback: () => {
            importDialogVisible.value = false
            queryPage() // 重新加载列表
          }
        })
      } else {
        // 如果全部成功，显示成功消息
        ElMessage.success(message)
        importDialogVisible.value = false
        queryPage() // 重新加载列表
      }
    } else {
      ElMessage.error('导入失败: ' + (response?.msg || '未知错误'))
    }
  } catch (error) {
    logger.error('导入 Excel数据失败:', error)
    ElMessage.error('导入 Excel数据失败: ' + (error.message || '未知错误'))
  } finally {
    importLoading.value = false
  }
}

// 关闭导入对话框
const closeImportDialog = () => {
  selectedFile.value = null
  importDialogVisible.value = false
  // 重置文件输入框
  const fileInput = document.querySelector('#excel-file')
  if (fileInput) {
    fileInput.value = ''
  }
}


// 下载导入模板
const downloadTemplate = async (): Promise<void> => {
  try {
    const response = await request.get('/admin/teachers/import-template', {
      responseType: 'blob'
    })

    // 创建下载链接
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', '教师导入模板.xlsx')
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

// 切换教师状态（启用/禁用）
const handleToggleStatus = async (id, currentStatus) => {
  // 确保即使currentStatus为undefined也能正常工作，默认认为是启用状态
  const actualStatus = currentStatus ?? 1
  const newStatus = actualStatus === 1 ? 0 : 1
  const statusText = newStatus === 1 ? '启用' : '禁用'
  
  try {
    await ElMessageBox.confirm(`确定要${statusText}该教师账户吗？`, '确认操作', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    // 调用后端API更新状态
    const response = await updateStatusApi(id, newStatus)
    
    if (response && response.code === 200) {
      ElMessage.success(`${statusText}成功`)
      
      // 在前端直接更新状态
      const teacherIndex = tableData.value.findIndex(item => item.id === id)
      // 状态更新成功后，重新加载列表以获取最新的真实数据
      queryPage()
    } else {
      ElMessage.error(`${statusText}失败: ` + (response?.msg || '未知错误'))
    }
  } catch (error) {
    logger.error(`${statusText}失败:`, error)
    if (error !== 'cancel') {
      ElMessage.error(`${statusText}失败: ` + (error.message || '未知错误'))
    }
  }
}

// 应用本地存储的教师状态函数已在文件前面定义，此处删除重复定义

// 导出Excel功能
const exportToExcel = async () => {
  try {
    ElMessage({ message: '正在准备导出数据...', type: 'info' })
    
    const response = await TeacherUserService.getAllTeachers()
    logger.log('教师导出数据响应:', response)
    
    const allTeachers = response.data?.list || response.data?.rows || response?.list || response?.rows || []
    
    if (allTeachers.length === 0) {
      ElMessage.warning('没有数据可导出')
      return
    }
    
    const exportData = allTeachers.map(item => {
      return {
        '工号': item.teacherUserId,
        '姓名': item.name,
        '性别': item.gender === 1 ? '男' : (item.gender === 2 ? '女' : '-'),
        '身份': getTeacherTypeText(item.teacherType),
        '院系': item.departmentName || formatDepartmentName(item, null, item.departmentId),
        '创建时间': formatDate(null, null, item.createTime || item.createdTime),
        '更新时间': formatDate(null, null, item.updateTime || item.updatedTime)
      }
    })

    // 创建工作簿
    const wb = XLSX.utils.book_new()
    // 创建工作表
    const ws = XLSX.utils.json_to_sheet(exportData)
    // 添加工作表到工作簿
    XLSX.utils.book_append_sheet(wb, ws, '教师信息')
    
    // 生成文件名 - 包含当前日期时间
    const currentDate = new Date()
    const year = currentDate.getFullYear()
    const month = String(currentDate.getMonth() + 1).padStart(2, '0')
    const day = String(currentDate.getDate()).padStart(2, '0')
    const hours = String(currentDate.getHours()).padStart(2, '0')
    const minutes = String(currentDate.getMinutes()).padStart(2, '0')
    const seconds = String(currentDate.getSeconds()).padStart(2, '0')
    const fileName = `教师信息导出_${year}${month}${day}_${hours}${minutes}${seconds}.xlsx`
    
    // 导出文件
    XLSX.writeFile(wb, fileName)
    
    ElMessage({ message: '导出成功', type: 'success' })
  } catch (error) {
    logger.error('导出Excel失败:', error)
    ElMessage.error('导出Excel失败: ' + (error.message || '未知错误'))
  }
}

//查看教师课程
const viewTeacherCourses = async (row) => {
  try {
    currentTeacherName.value = row.name
    currentTeacherId.value = row.id
    loading.value = true
    
    // 移除课程相关代码
    teacherCourses.value = []
    
    viewCoursesDialogVisible.value = true
  } catch (error) {
    logger.error('获取教师课程失败:', error)
    ElMessage.error('获取教师课程失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

//关闭查看课程对话框
const closeViewCoursesDialog = () => {
  viewCoursesDialogVisible.value = false
  teacherCourses.value = []
  currentTeacherName.value = ''
  currentTeacherId.value = null
}

//页面加载时执行
onMounted(async () => {
  logger.log('页面挂载，开始加载数据...')
  await getDepartmentList()
  await getMajorList()
  await queryPage()
  logger.log('数据加载完成')
})
</script>

<template>
  <div class="teacher-management-container">
    <!-- 顶部标题区域 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">教师管理</h1>
        <p class="page-description">管理系统中的所有教师账户信息</p>
      </div>
      <div class="header-illustration">
        <div class="illustration-circle circle-1"></div>
        <div class="illustration-circle circle-2"></div>
      </div>
    </div>

    <!-- 搜索区域卡片 -->
    <el-card class="search-card" shadow="never">
      <el-form ref="searchFormRef" :inline="true" :model="searchForm" class="search-form" @keyup.enter="queryPage">
        <div class="search-row">
          <el-form-item label="工号">
            <el-input
              v-model="searchForm.teacherUserId"
              placeholder="请输入工号"
              clearable
              @keyup.enter="queryPage"
            ></el-input>
          </el-form-item>
          <el-form-item label="姓名">
            <el-input
              v-model="searchForm.name"
              placeholder="请输入姓名"
              clearable
              @keyup.enter="queryPage"
            ></el-input>
          </el-form-item>
          <el-form-item label="性别">
            <el-select v-model="searchForm.gender" placeholder="请选择性别" clearable>
              <el-option label="男" :value="1"></el-option>
              <el-option label="女" :value="2"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="院系">
            <el-select v-model="searchForm.department" placeholder="请选择院系" clearable style="width: 150px;" filterable>
              <el-option v-for="department in departments" :key="department.value" :label="department.name" :value="department.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="searchForm.status" placeholder="请选择状态" clearable style="width: 120px;">
              <el-option :value="1" label="已启用" />
              <el-option :value="0" label="已禁用" />
            </el-select>
          </el-form-item>
          <el-form-item label="身份">
            <el-select v-model="searchForm.teacherType" placeholder="请选择身份" clearable style="width: 120px;">
              <el-option value="COLLEGE" label="学院教师" />
              <el-option value="DEPARTMENT" label="系室教师" />
              <el-option value="COUNSELOR" label="辅导员" />
            </el-select>
          </el-form-item>
        </div>
        <div class="search-row button-row">
          <el-form-item class="search-actions">
            <el-button type="primary" @click="queryPage" class="search-btn">
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
          <el-button v-if="authStore.hasPermission('user:teacher:add')" type="primary" @click="handleAddTeacher" class="action-btn primary">
            <el-icon><Plus /></el-icon>&nbsp;新增教师
          </el-button>
          <el-button v-if="authStore.hasPermission('user:teacher:delete')" type="danger" @click="delByIds" class="action-btn danger">
            <el-icon><Delete /></el-icon>&nbsp;批量删除
          </el-button>
        </div>
        <div class="secondary-actions">
                    <el-dropdown v-if="authStore.hasPermission('user:teacher:view')" trigger="click" class="import-dropdown">
            <el-button type="primary" class="action-btn success">
              <el-icon><Upload /></el-icon>&nbsp;导入 Excel<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="showImportDialog()">选择文件导入</el-dropdown-item>
                <el-dropdown-item @click="downloadTemplate">下载导入模板</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button v-if="authStore.hasPermission('user:teacher:view')" type="success" @click="exportToExcel()" class="action-btn success">
            <el-icon><Download /></el-icon>&nbsp;导出Excel
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 数据列表卡片 -->
    <el-card class="table-card" shadow="never">
      <el-table 
        ref="teacherTable" 
        v-loading="loading" 
        :data="tableData" 
        border 
        style="width: 100%" 
        fit 
        @selection-change="handleSelectionChange" 
        row-key="id"
        class="data-table"
      >
        <el-table-column type="selection" width="55" align="center" reserve-selection />
        <el-table-column type="index" label="序号" width="55" align="center" :index="(index) => (pagination.currentPage - 1) * pagination.pageSize + index + 1" />
        <el-table-column prop="teacherUserId" label="工号" align="center" width="150" />
        <el-table-column prop="name" label="姓名" align="center" width="120" />
        <el-table-column prop="gender" label="性别" align="center" width="80">
          <template #default="scope">
            <span>{{ scope.row.gender === 1 ? '男' : scope.row.gender === 2 ? '女' : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="departmentName" label="院系" align="center" width="210"/>
        <el-table-column prop="teacherType" label="身份" align="center" width="120">
          <template #default="scope">
            <el-tag :type="getTeacherTypeTagType(scope.row.teacherType)" size="small">
              {{ getTeacherTypeText(scope.row.teacherType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'" size="small" class="status-tag">
              {{ scope.row.status === 1 ? '已启用' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="210" align="center" :formatter="formatDate" />
        <el-table-column prop="createTime" label="创建时间" width="210" align="center" :formatter="formatDate" />    
        <el-table-column label="操作" align="center" width="270" fixed="right">
          <template #default="scope">
            <div class="action-buttons">
              <el-tooltip v-if="authStore.hasPermission('user:teacher:edit')" content="编辑" placement="top">
                <el-button type="primary" size="small" @click="handleUpdateTeacher(scope.row.id)" class="table-btn edit">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('user:teacher:reset')" content="重置密码" placement="top">
                <el-button type="warning" size="small" @click="showResetPasswordDialog(scope.row)" class="table-btn reset">
                  <el-icon><Refresh /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('user:teacher:delete')" content="删除" placement="top">
                <el-button type="danger" size="small" @click="delById(scope.row.id)" class="table-btn delete">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('user:teacher:edit')" :content="scope.row.status === 1 ? '禁用账户' : '启用账户'" placement="top">
                <el-button
                  :type="scope.row.status === 1 ? 'danger' : 'success'"
                  size="small"
                  @click="handleToggleStatus(scope.row.id, scope.row.status)"
                  class="table-btn toggle"
                >
                  <el-icon><Switch /></el-icon>
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
          :page-sizes="[5, 10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="pagination.total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          class="custom-pagination"
        />
      </div>
    </el-card>

    <!-- 新增/编辑教师对话框 -->
    <el-dialog v-model="dialogFormVisible" :title="formTitle" width="500px" @close="handleCancel()" class="form-dialog">
      <!-- 条件渲染：根据表单标题显示对应的表单 -->
      <template v-if="formTitle === '新增教师'">
        <el-form 
          ref="addTeacherFormRef"
          :model="addTeacher" 
          :rules="rules" 
          :label-width="labelWidth"
          class="teacher-form"
        >
          <el-form-item label="工号" prop="teacherUserId">
            <el-input v-model="addTeacher.teacherUserId" placeholder="请输入工号" />
          </el-form-item>
          <el-form-item label="姓名" prop="name">
            <el-input v-model="addTeacher.name" placeholder="请输入姓名" />
          </el-form-item>
          <el-form-item label="性别" prop="gender">
            <el-radio-group v-model="addTeacher.gender">
              <el-radio :label="1">男</el-radio>
              <el-radio :label="2">女</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="院系" prop="departmentId">
            <el-select v-model="addTeacher.departmentId" placeholder="请选择院系" filterable>
              <el-option v-for="department in departments" :key="department.value" :label="department.name"
                :value="department.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="身份" prop="teacherType">
            <el-select v-model="addTeacher.teacherType" placeholder="请选择身份">
              <el-option label="学院教师" value="COLLEGE" />
              <el-option label="系室教师" value="DEPARTMENT" />
              <el-option label="辅导员" value="COUNSELOR" />
            </el-select>
          </el-form-item>
          <div class="form-tip">
            提示：密码将自动设置为123456，无需手动输入
          </div>
        </el-form>
      </template>
      
      <!-- 编辑教师表单 -->
      <template v-else-if="formTitle === '编辑教师'">
        <el-form 
          ref="editTeacherFormRef"
          :model="editTeacher" 
          :rules="rules" 
          :label-width="labelWidth"
          class="teacher-form"
        >
          <el-form-item label="工号" prop="teacherUserId">
            <el-input v-model="editTeacher.teacherUserId" placeholder="请输入工号" />
          </el-form-item>
          <el-form-item label="姓名" prop="name">
            <el-input v-model="editTeacher.name" placeholder="请输入姓名" />
          </el-form-item>
          <el-form-item label="性别" prop="gender">
            <el-radio-group v-model="editTeacher.gender">
              <el-radio :label="1">男</el-radio>
              <el-radio :label="2">女</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="院系" prop="departmentId">
            <el-select v-model="editTeacher.departmentId" placeholder="请选择院系" filterable>
              <el-option v-for="department in departments" :key="department.value" :label="department.name"
                :value="department.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="身份" prop="teacherType">
            <el-select v-model="editTeacher.teacherType" placeholder="请选择身份">
              <el-option label="学院教师" value="COLLEGE" />
              <el-option label="系室教师" value="DEPARTMENT" />
              <el-option label="辅导员" value="COUNSELOR" />
            </el-select>
          </el-form-item>
          <div class="form-tip">
            提示：密码将自动设置为123456，无需手动输入
          </div>
        </el-form>
      </template>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="handleCancel()" class="cancel-btn">取消</el-button>
          <el-button type="primary" @click="saveTeacher()" class="confirm-btn">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 重置密码对话框 -->
    <el-dialog v-model="resetPasswordDialogVisible" title="重置密码" width="400px" class="reset-dialog">
      <div class="reset-content">
        <p>密码将被重置为: <span class="password-text">123456</span></p>
        <p class="reset-tip">请确认是否继续重置密码</p>
      </div>
      <template #footer>
        <el-button @click="resetPasswordDialogVisible = false" class="cancel-btn">取消</el-button>
        <el-button type="primary" @click="resetUserPassword" class="confirm-btn">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入 Excel对话框 -->
    <el-dialog v-model="importDialogVisible" title="导入 Excel教师数据" width="400px" class="import-dialog">
      <div class="import-content">
        <div class="file-selector">
          <el-button type="primary" @click="handleChooseFile" class="file-btn">
            选择Excel文件
          </el-button>
          <input 
            ref="excelFileInput"
            id="excel-file"
            type="file" 
            accept=".xlsx,.xls" 
            style="display: none;" 
            @change="handleNativeFileChange"
          >
          <div v-if="selectedFile" class="file-info">
            已选择文件: <span class="file-name">{{ selectedFile.name }}</span>
          </div>
        </div>
        <div class="import-tip">
          提示：请确保Excel文件包含工号、姓名、院系等必要字段
        </div>
      </div>
      <template #footer>
        <el-button @click="closeImportDialog" class="cancel-btn">取消</el-button>
        <el-button type="primary" @click="importExcel" :loading="importLoading" class="confirm-btn">导入</el-button>
      </template>
    </el-dialog>
    
    <!-- 查看教师课程对话框 -->
    <el-dialog 
      v-model="viewCoursesDialogVisible" 
      :title="`${currentTeacherName}的课程列表`" 
      width="800px" 
      @close="closeViewCoursesDialog"
      class="courses-dialog"
    >
      <div class="courses-content">
        <el-table 
          :data="teacherCourses" 
          border 
          style="width: 100%"
          class="courses-table"
        >
          <el-table-column type="index" label="序号" width="80" align="center" />
          <el-table-column prop="name" label="课程名称" align="center" min-width="200" />
          <el-table-column prop="grade" label="年级" align="center" width="100" />
          <el-table-column prop="majorName" label="专业" align="center" min-width="150" />
          <el-table-column prop="studentCount" label="学生人数" align="center" width="100">
            <template #default="scope">
              <div class="student-count">
                {{ scope.row.studentCount }}
              </div>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="teacherCourses.length === 0" class="empty-courses">
          <el-empty description="该教师暂无课程" />
        </div>
      </div>
      <template #footer>
        <el-button @click="closeViewCoursesDialog" class="cancel-btn">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.teacher-management-container {
  padding: 20px;
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

.search-row.button-row {
  justify-content: flex-end;
  margin-top: 10px;
}

.search-row .el-form-item {
  margin-bottom: 0;
  flex: 1;
}

.search-row.button-row .el-form-item {
  flex: none;
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

.status-tag {
  border-radius: 12px;
  font-weight: 500;
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

/* 对话框样式 */
.form-dialog,
.reset-dialog,
.import-dialog {
  border-radius: 16px;
  overflow: hidden;
}

.form-dialog :deep(.el-dialog__header),
.reset-dialog :deep(.el-dialog__header),
.import-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #f8fbff 0%, #e6f7ff 100%);
  margin: 0;
  padding: 20px;
  border-bottom: 1px solid #e6f7ff;
}

.teacher-form {
  padding: 20px 0;
}

.form-tip {
  color: #909399;
  font-size: 12px;
  margin-top: 10px;
  padding-left: 100px;
}

/* 重置密码对话框内容 */
.reset-content {
  padding: 20px 0;
  text-align: center;
}

.password-text {
  color: #409eff;
  font-weight: bold;
  font-size: 16px;
}

.reset-tip {
  color: #909399;
  font-size: 12px;
  margin-top: 10px;
}

/* 导入对话框内容 */
.import-content {
  padding: 20px 0;
}

.file-selector {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
}

.file-btn {
  border-radius: 8px;
}

.file-info {
  margin-top: 10px;
  color: #606266;
}

.file-name {
  color: #409EFF;
  font-weight: 500;
}

.import-tip {
  color: #909399;
  font-size: 12px;
  margin-top: 16px;
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

/* 课程对话框样式 */
.courses-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #f6ffed 0%, #f0f9ff 100%);
}

.courses-content {
  padding: 10px 0;
}

.courses-table :deep(.el-table__header) th {
  background-color: #f0f9ff;
  color: #1890ff;
}

.courses-table :deep(.el-table__row) td {
  border-bottom: 1px solid #f0f7ff;
}

.student-count {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #52c41a;
  font-weight: 500;
}

.count-icon {
  font-size: 14px;
}

.empty-courses {
  padding: 40px 0;
  text-align: center;
}

/* 查看课程按钮样式 */
.table-btn.view-courses {
  background-color: #909399;
  border-color: #909399;
}

.table-btn.view-courses:hover {
  background-color: #a6a9ad;
  border-color: #a6a9ad;
}

/* 响应式设计 */
@media screen and (max-width: 1200px) {
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
  .teacher-management-container {
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
}
</style>