<script setup lang="ts">
import logger from '@/utils/logger'
import { ref, onMounted, nextTick, h } from 'vue'
import { ElMessage, ElMessageBox, ElDialog, ElTable, ElTableColumn, ElCheckbox, ElForm } from 'element-plus'
import { Search, Refresh, Plus, Delete, Upload, Download, View, Edit, Switch, ArrowDown } from '@element-plus/icons-vue'
import StudentUserService, { queryPageApi, addApi, queryInfoApi, updateApi, deleteApi, resetPasswordApi, batchDeleteApi, importExcelApi, updateStatusApi } from '../../api/StudentUserService'
import MajorService from '../../api/major'
import ClassService from '../../api/class'
import { useSystemSettingsStore } from '../../store/systemSettings'
import { useAuthStore } from '../../store/auth'
import { createGradeValidator, createRequiredValidator } from '@/utils/validation'
import type { StudentUser, PageParams, PaginationState, DialogState, Class, Major } from '@/types/admin'

import request from '../../utils/request'
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

// 专业名称格式化方法 - 用于表格中专业列的显示
const formatMajorName = (_row: unknown, _column: unknown, cellValue: string): string => {
  if (!cellValue) return ''

  // 从majors数组中查找对应的专业名称，确保类型一致
  const major = majors.value.find(item => item.value === String(cellValue))
  return major ? major.name : String(cellValue)
}

// 班级名称格式化方法 - 用于表格中班级列的显示
const formatClassName = (row: unknown, column: unknown, cellValue: string): string => {
  if (!cellValue) return ''

  // 从classes数组中查找对应的班级名称，确保类型一致
  const classItem = classes.value.find(item => item.id === String(cellValue))
  return classItem ? classItem.name : String(cellValue)
}

// 搜索表单对象
let searchForm = ref<Partial<StudentUser>>({ studentId: '', name: '', gender: '', grade: '', majorId: '', classId: '', status: '' })

// 列表展示数据
let tableData = ref<StudentUser[]>([])
// 加载状态
let loading = ref(false)

// 专业列表数据
let majors = ref<Major[]>([])

// 班级列表数据
let classes = ref<Class[]>([])

// 分页数据
let pagination = ref<PaginationState>({
  currentPage: 1,
  pageSize: 10,
  total: 0
})

// 表格引用
let studentTable = ref<InstanceType<typeof ElTable> | null>(null)

// 为新增和编辑操作创建独立的表单数据和引用，避免数据相互干扰
// 新增学生表单引用
let addStudentFormRef = ref<InstanceType<typeof ElForm> | null>(null)
// 编辑学生表单引用
let editStudentFormRef = ref<InstanceType<typeof ElForm> | null>(null)

// 新增学生表单数据
let addStudent = ref<Partial<StudentUser>>({
  id: '',
  studentId: '',
  name: '',
  gender: 1,
  grade: '',
  majorId: null,
  classId: null
})

// 编辑学生表单数据
let editStudent = ref<Partial<StudentUser>>({
  id: '',
  studentId: '',
  name: '',
  gender: 1,
  grade: '',
  majorId: null,
  classId: null
})

// 对话框控制
let dialogFormVisible = ref(false)
let formTitle = ref('')
let labelWidth = ref('100px')

// 学生课程相关状态
let selectedStudent = ref<StudentUser | null>(null)
let studentCourses = ref<unknown[]>([])
let viewCoursesDialogVisible = ref(false)

// 重置密码对话框控制
let resetPasswordDialogVisible = ref(false)
let resetPasswordUserId = ref<string | null>(null)

// 导入Excel相关状态
let importDialogVisible = ref(false)
let importLoading = ref(false)
let selectedFile = ref<File | null>(null)
// 添加ref引用文件输入元素
let excelFileInput = ref<HTMLElement | null>(null)

// 表单规则
const rules = {
  studentId: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  grade: createGradeValidator('请输入 4 位年份，如 2024'),
  majorId: [{ required: true, message: '请选择专业', trigger: 'change' }],
  classId: [{ required: true, message: '请选择班级', trigger: 'change' }]
}

// 处理复选框选中变化
const handleSelectionChange = (selection: unknown[]) => {
  selectIds.value = selection.map(item => item.id)
}

// 复选框选中数据
let selectIds = ref<string[]>([])

// 分页处理函数
const handleSizeChange = (newSize: number) => {
  pagination.value.pageSize = newSize
  queryPage()
}

const handleCurrentChange = (newCurrent: number) => {
  pagination.value.currentPage = newCurrent
  queryPage()
}

// 分页查询函数
const queryPage = async (): Promise<void> => {
  loading.value = true
  try {
    logger.log('开始查询学生列表...')
    //构建查询参数
    const params = {
      ...searchForm.value,
      pageNum: pagination.value.currentPage,
      pageSize: pagination.value.pageSize
    }

    logger.log('查询参数:', params)
    const response = await queryPageApi(params)
    logger.log('查询结果响应:', response)

    //使用标准的响应格式
    if (response && response.code === 200 && response.data) {
      //获取原始数据列表
      const dataList = response.data.rows || []
      const totalCount = response.data.total || 0

      //添加专业名称和班级名称到数据中
      const enhancedData = dataList.map(student => {
        logger.log(`处理学生: ${student.name}, majorId: ${student.majorId} (类型: ${typeof student.majorId})`)
        logger.log(`可用专业列表:`, majors.value)
        //找到对应的专业，确保类型一致
        const major = majors.value.find(m => m.value === String(student.majorId))
        logger.log(`找到的专业:`, major)
        //找到对应的班级，确保类型一致
        const classItem = classes.value.find(c => c.id === String(student.classId))
        return {
          ...student,
          majorName: major ? major.name : '未知专业',
          className: classItem ? classItem.name : '未知班级',
          // 确保日期字段名称与表格中使用的一致
          createdTime: student.createdTime || student.createTime,
          updatedTime: student.updatedTime || student.updateTime
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
              studentTable.value.toggleRowSelection(row, true)
            }
          })
        }
      })
    } else {
      tableData.value = []
      pagination.value.total = 0
      logger.log('无数据或响应格式不正确')
    }
    
    // 不再需要应用本地存储的状态，使用后端返回的真实状态
  } catch (error) {
    logger.error('查询学生列表失败:', error)
    tableData.value = []
    pagination.value.total = 0
    ElMessage.error('查询学生列表失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 清空搜索表单
const clear = (): void => {
  searchForm.value = {
    studentId: '',
    name: '',
    grade: '',
    majorId: null,
    classId: null,
    status: null
  }
  pagination.value.currentPage = 1
}

// 重置表单
const resetForm = (formRef: InstanceType<typeof ElForm> | null): void => {
  if (formRef && formRef.value) {
    setTimeout(() => {
      formRef.value.resetFields()
    }, 0)
  }
}

//清空新增学生表单数据
const clearAddStudentForm = () => {
  addStudent.value = {
    id: '',
    studentId: '',
    name: '',
    grade: '',
    majorId: null,
    classId: null
  }
}

//清空编辑学生表单数据
const clearEditStudentForm = () => {
  editStudent.value = {
    id: '',
    studentId: '',
    name: '',
    grade: '',
    majorId: null,
    classId: null
  }
}

//处理取消按钮点击事件
const handleCancel = () => {
  // 重要：先关闭对话框
  dialogFormVisible.value = false
  
  // 使用nextTick确保DOM已经更新，然后再执行清空操作
  nextTick(() => {
    // 1. 完全清空所有表单数据对象
    clearAddStudentForm()
    clearEditStudentForm()
    
    // 2. 额外的保险措施：手动重置每个字段
    // 针对编辑表单数据的额外清空
    if (editStudent.value) {
      editStudent.value.id = ''
      editStudent.value.studentId = ''
      editStudent.value.name = ''
      editStudent.value.grade = ''
      editStudent.value.majorId = null
      editStudent.value.classId = null
    }
    
    // 3. 重置表单状态和校验
    if (addStudentFormRef && addStudentFormRef.value) {
      try {
        addStudentFormRef.value.resetFields()
      } catch (error) {
        logger.warn('重置新增表单失败:', error)
      }
    }
    
    if (editStudentFormRef && editStudentFormRef.value) {
      try {
        editStudentFormRef.value.resetFields()
      } catch (error) {
        logger.warn('重置编辑表单失败:', error)
      }
    }
    
    logger.log('编辑框已关闭并完全清空数据')
  })
}

// 新增学生
const handleAddStudent = () => {
  formTitle.value = '新增学生'
  clearAddStudentForm()
  // 在下一个tick中重置表单校验，确保在对话框打开前重置完成
  nextTick(() => {
    resetForm(addStudentFormRef)
    dialogFormVisible.value = true
  })
}

// 更新学生 - 完全重写版本，确保数据正确更新
const handleUpdateStudent = async (id) => {
  try {
    logger.log('开始获取学生详情，ID:', id)
    
    // 1. 第一步：完全重置所有相关状态
    // 立即关闭对话框
    dialogFormVisible.value = false
    
    // 强制清空表单数据对象，创建一个全新的对象
    editStudent.value = {
      id: '',
      studentId: '',
      name: '',
      grade: '',
      majorId: null,
      classId: null
    }
    
    // 重置表单引用状态
    if (editStudentFormRef.value) {
      try {
        editStudentFormRef.value.resetFields()
      } catch (error) {
        logger.warn('重置表单失败，但继续操作:', error)
      }
    }
    
    // 2. 等待DOM更新完成
    await nextTick()
    
    // 3. 获取新的学生数据
    const response = await queryInfoApi(id)
    logger.log('学生详情响应:', response)

    if (response && response.code === 200 && response.data) {
      const studentData = response.data
      formTitle.value = '编辑学生'
      
      logger.log('准备设置新的学生数据:', studentData)
      
      // 4. 使用强制更新的方式设置数据
      // 强制替换整个对象，确保Vue能检测到变化
      // 注意：将majorId和classId转换为字符串，以匹配下拉选项的value类型
      Object.assign(editStudent.value, {
        id: studentData.id || '',
        studentId: studentData.studentId || '',
        name: studentData.name || '',
        gender: studentData.gender !== undefined ? studentData.gender : 1,
        grade: studentData.grade || '',
        majorId: studentData.majorId !== undefined ? String(studentData.majorId) : null,
        classId: studentData.classId !== undefined ? String(studentData.classId) : null
      })
      
      // 5. 再次等待DOM更新
      await nextTick()
      
      logger.log('设置后的editStudent数据:', editStudent.value)
      
      // 6. 最后显示对话框
      dialogFormVisible.value = true
      
      // 7. 对话框显示后再次确保数据正确绑定
      nextTick(() => {
        logger.log('对话框已显示，最终绑定的数据:', editStudent.value)
      })
    } else {
      ElMessage.error('获取学生信息失败: 无效的响应格式')
    }
  } catch (error) {
    logger.error('获取学生信息失败:', error)
    ElMessage.error('获取学生信息失败: ' + (error.message || '未知错误'))
    // 出错时确保对话框是关闭的
    dialogFormVisible.value = false
  }
}

// 保存学生
const saveStudent = async () => {
  try {
    let formData, formRef, api
    
    // 根据表单标题判断是新增还是编辑操作
    if (formTitle.value === '新增学生') {
      formData = addStudent.value
      formRef = addStudentFormRef
      //新增操作 - 添加默认密码
      api = addApi({ ...formData, password: '123456' })
    } else {
      formData = editStudent.value
      formRef = editStudentFormRef
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

    logger.log('提交的学生数据:', formData)
    const response = await api

    if (response && response.code === 200) {
      ElMessage.success(formTitle.value === '编辑学生' ? '更新成功' : '新增成功，默认密码为123456')
      dialogFormVisible.value = false
      sessionStorage.removeItem('majors_cache')
      sessionStorage.removeItem('majors_cache_time')
      sessionStorage.removeItem('classes_cache')
      sessionStorage.removeItem('classes_cache_time')
      queryPage() //重新加载列表
    } else {
      ElMessage.error(response?.msg || (formTitle.value === '编辑学生' ? '更新失败' : '新增失败'))
    }
  } catch (error) {
    logger.error((formTitle.value === '编辑学生' ? '更新' : '新增') + '学生失败:', error)
    if (error.name !== 'ValidationError') {
      ElMessage.error(error.response?.data?.message || error.message || (formTitle.value === '编辑学生' ? '更新失败' : '新增失败'))
    }
  }
}

// 级联删除学生相关数据 - 已简化，移除了提交记录、成绩记录和小组关联的删除
const cascadeDeleteStudentData = async (studentId) => {
  try {
    logger.log(`开始级联删除学生ID ${studentId} 的相关数据...`)
    // 移除了提交记录、成绩记录和小组关联的删除逻辑
    logger.log(`学生ID ${studentId} 的级联数据删除完成`)
  } catch (error) {
    logger.error(`级联删除学生数据失败:`, error)
    throw error
  }
}

// 根据ID删除单个学生
const delById = async (id) => {
  try {
    await ElMessageBox.confirm(
      '确定要删除该学生吗？此操作将删除该学生及其所有相关数据，此操作不可撤销！',
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
        center: true
      }
    )

    // 显示全局加载提示
    ElMessage({ 
      message: enableCascadeDelete ? 
        '正在进行强制级联删除，请稍候...' : 
        '正在处理删除请求，请稍候...', 
      type: 'info',
      duration: 0, // 不自动关闭
      showClose: true
    })

    // 如果启用级联删除，先进行前置处理
    if (enableCascadeDelete) {
      try {
        await cascadeDeleteStudentData(id)
        logger.log('前置级联删除完成')
      } catch (cascadeError) {
        logger.error('前置级联删除处理失败:', cascadeError)
        ElMessage.error('前置数据处理失败，但会继续尝试删除')
      }
    }

    // 调用增强的删除API
    const response = await deleteApi(id)

    // 根据不同的响应状态显示相应的消息
    if (response && response.code === 200) {
      ElMessage.success('学生删除成功！')
      queryPage() // 重新加载列表
    } else if (response && response.code === 400) {
      // 显示详细的错误信息对话框
      ElMessageBox.alert(
        response.msg || '参数错误，请检查输入后重试',
        '删除失败',
        { type: 'error' }
      )
    } else {
      // 错误信息可能包含多行，使用alert对话框显示
      ElMessageBox.alert(
        response?.msg || '删除失败，请稍后重试',
        '删除失败',
        { type: 'error' }
      )
    }
  } catch (error) {
    logger.log('删除操作已取消或发生错误:', error)
    // 智能判断是否为取消操作
    const isCancelOperation = error === 'cancel' || 
                             error?.name === 'Error' || 
                             (error?.toString && error.toString().includes('cancel')) ||
                             (error?.code && error.code === 'ERR_CANCELED')
    
    if (!isCancelOperation) {
      logger.error('删除失败异常:', error)
      ElMessage.error('删除操作发生异常，请稍后重试')
    }
  }
}

// 批量删除学生
const delByIds = async () => {
  try {
    if (selectIds.value.length === 0) {
      ElMessage.warning('请至少选择一个学生')
      return
    }

    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectIds.value.length} 名学生吗？此操作将删除这些学生及其所有相关数据，此操作不可撤销！`,
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
      ElMessage.success(`成功删除 ${selectIds.value.length} 名学生！`)
      selectIds.value = []
      queryPage()
    } else {
      ElMessage.error('删除失败: ' + (response?.msg || '未知错误'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      logger.error('批量删除失败:', error)
      ElMessage.error('批量删除操作发生异常，请稍后重试')
    }
  }
}

// 显示重置密码对话框
const showResetPasswordDialog = (row) => {
  resetPasswordUserId.value = row.id
  resetPasswordDialogVisible.value = true
}

// 重置密码
const resetUserPassword = async () => {
  try {
    const response = await resetPasswordApi(resetPasswordUserId.value)

    if (response && response.code === 200) {
      ElMessage.success('密码重置成功，新密码为123456')
      resetPasswordDialogVisible.value = false
    } else {
      ElMessage.error('密码重置失败: ' + (response?.msg || '未知错误'))
    }
  } catch (error) {
    logger.error('重置密码失败:', error)
    ElMessage.error('重置密码失败: ' + (error.message || '未知错误'))
  }
}

// 切换学生状态（启用/禁用）
const handleToggleStatus = async (id, currentStatus) => {
  const newStatus = currentStatus === 1 ? 0 : 1
  const statusText = newStatus === 1 ? '启用' : '禁用'
  
  try {
    await ElMessageBox.confirm(`确定要${statusText}该学生账户吗？`, '确认操作', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    // 调用真实的后端API更新状态
    const response = await updateStatusApi(id, newStatus)
    
    if (response && response.code === 200) {
      ElMessage.success(`${statusText}成功`)
      
        // 后端已实现状态更新，重新加载列表确保数据与后端同步
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

// 状态管理已完全由后端负责，不再需要前端模拟状态

// 处理搜索表单中专业选择变化事件
const handleMajorChange = (value) => {
  // 专业选择变化时自动更新班级列表
  if (value !== undefined) {
    searchForm.value.majorId = value
    // 清除之前选择的班级，避免跨专业选择
    searchForm.value.classId = null
  }
}

//处理对话框中专业选择变化事件
const handleDialogMajorChange = (value) => {
  if (value !== undefined) {
    // 根据当前是新增还是编辑操作，更新对应的表单数据
    if (formTitle.value === '新增学生') {
      addStudent.value.majorId = value
      // 清除之前选择的班级，避免跨专业选择
      addStudent.value.classId = null
    } else {
      editStudent.value.majorId = value
      // 清除之前选择的班级，避免跨专业选择
      editStudent.value.classId = null
    }
  }
}

// 获取专业列表
const getMajorList = async () => {
  try {
    logger.log('开始获取专业列表...')
    
    // 先从缓存读取
    const cached = sessionStorage.getItem('majors_cache')
    const cachedTime = sessionStorage.getItem('majors_cache_time')
    
    if (cached && cachedTime) {
      const age = Date.now() - parseInt(cachedTime)
      if (age < 5 * 60 * 1000) {
        logger.log('使用缓存的专业列表，缓存时间:', new Date(parseInt(cachedTime)))
        const cachedMajors = JSON.parse(cached)
        majors.value = cachedMajors
        return
      }
    }
    
    // 缓存过期或不存在，重新请求
    const response = await MajorService.getMajors()
    logger.log('专业列表响应:', response)

    //增强数据结构兼容性处理
    let rawMajors = []
    if (response && response.data) {
      if (Array.isArray(response.data)) {
        rawMajors = response.data
      } else if (response.data.code === 200 && response.data.data) {
        //处理带code和data的响应格式
        rawMajors = Array.isArray(response.data.data) ? response.data.data : []
      }
    }

    logger.log('原始专业数据:', rawMajors)

    //确保数据格式正确，为每个专业对象添加id和name属性
    majors.value = rawMajors.map(major => {
      logger.log('处理单个专业:', major)
      // 提取专业ID，支持多种属性名
      const majorId = major.id || major.majorId || major.major_id || major.MajorId || major.MAJOR_ID
      // 提取专业名称，支持多种属性名
      const majorName = major.name || major.majorName || major.major_name || major.MajorName || major.MAJOR_NAME || '未知专业'
      return {
        value: String(majorId),
        name: majorName
      }
    }).filter(major => major.value !== 'NaN' && major.value !== '') // 过滤无效数据

    logger.log('转换后的专业列表:', majors.value)
    
    // 存入缓存
    sessionStorage.setItem('majors_cache', JSON.stringify(majors.value))
    sessionStorage.setItem('majors_cache_time', Date.now().toString())
  } catch (error) {
    logger.error('获取专业列表失败:', error)
    majors.value = []
    ElMessage.error('获取专业列表失败: ' + (error.message || '未知错误'))
  }
}

//获取班级列表
const getClassList = async () => {
  try {
    logger.log('开始获取班级列表...')
    
    // 先从缓存读取
    const cached = sessionStorage.getItem('classes_cache')
    const cachedTime = sessionStorage.getItem('classes_cache_time')
    
    if (cached && cachedTime) {
      const age = Date.now() - parseInt(cachedTime)
      if (age < 5 * 60 * 1000) {
        logger.log('使用缓存的班级列表，缓存时间:', new Date(parseInt(cachedTime)))
        const cachedClasses = JSON.parse(cached)
        classes.value = cachedClasses
        return
      }
    }
    
    // 缓存过期或不存在，重新请求
    const response = await ClassService.getClasses()
    logger.log('班级列表响应:', response)

    //增强数据结构兼容性处理
    if (response && response.data) {
      if (Array.isArray(response.data)) {
        classes.value = response.data
      } else if (response.data.code === 200 && response.data.data) {
        //处理带code和data的响应格式
        classes.value = Array.isArray(response.data.data) ? response.data.data : []
      } else {
        classes.value = []
      }
    } else {
      classes.value = []
    }

    logger.log('最终的班级列表数据:', classes.value)

    //确保数据格式正确，为每个班级对象添加id和name属性
    if (classes.value.length > 0) {
      //转换数据结构，确保每个对象都有id和name属性
      classes.value = classes.value.map(classItem => ({
        id: String(classItem.id || classItem.classId || ''),
        name: classItem.name || classItem.className || '未知班级',
        majorId: String(classItem.majorId || '')
      }))
    }
    
    // 存入缓存
    sessionStorage.setItem('classes_cache', JSON.stringify(classes.value))
    sessionStorage.setItem('classes_cache_time', Date.now().toString())
  } catch (error) {
    logger.error('获取班级列表失败:', error)
    classes.value = []
    ElMessage.error('获取班级列表失败: ' + (error.message || '未知错误'))
  }
}

// 格式化日期时间
const formatDateTime = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  if (isNaN(date.getTime())) return ''
  return date.toLocaleString('zh-CN')
}

// 移除了加入方式相关函数

// 课程状态相关函数已移除，因为管理员端学生管理界面不再需要显示课程状态

// 显示学生课程对话框
const showStudentCourses = async (student) => {
  selectedStudent.value = student
  viewCoursesDialogVisible.value = false // 先关闭对话框
  // 加载中状态
  loading.value = true
  try {
    // 获取学生课程数据
    const courses = await getStudentCourses(student.id)
    studentCourses.value = courses
  } finally {
    loading.value = false
  }
  // 数据准备好后再打开对话框
  viewCoursesDialogVisible.value = true
}

// 获取学生课程数据
// 格式化专业名称显示（支持多个专业）
const formatCourseMajorNames = (course) => {
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

const getStudentCourses = async (studentId) => {
  try {
    logger.log(`获取学生 ${studentId} 的课程列表...`)
    const response = await StudentUserService.getStudentCourses(studentId)
    logger.log('学生课程列表响应:', response)
    
    // 处理不同的响应格式
    if (response && response.code === 200 && response.data) {
      const courses = Array.isArray(response.data) ? response.data : []
      return courses.map(course => ({
        ...course,
        // 确保每个课程数据中包含必要字段
        grade: course.grade || selectedStudent.value?.grade || '',
        score: course.score || 0,
        // 处理专业信息，支持多种格式
        formattedMajorNames: formatCourseMajorNames(course)
      }))
    } else {
      // 没有课程数据时返回空数组，使用真实数据
      return []
    }
  } catch (error) {
    logger.error('获取学生课程失败:', error)
    ElMessage.error('获取学生课程失败，请稍后重试')
    
    // API调用失败时返回空数组，不使用模拟数据
    return []
  }
}

// 显示导入Excel对话框
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
    
    logger.log('Native selected file:', file)
    selectedFile.value = file
    logger.log('selectedFile after native selection:', selectedFile.value)
  } else {
    logger.warn('No file selected via native input')
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
    const response = await request.get('/admin/students/import-template', {
      responseType: 'blob'
    })

    // 创建下载链接
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', '学生导入模板.xlsx')
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

// 导入Excel数据
const importExcel = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择Excel文件')
    return
  }

  importLoading.value = true
  try {
    // 简化导入流程，直接构建FormData并发送请求
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    
    // 发送请求到后端
    const response = await importExcelApi(formData)
    
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
      // 显示后端返回的具体错误信息
      ElMessage.error('导入失败: ' + (response?.msg || '未知错误'))
    }
  } catch (error) {
    logger.error('导入Excel数据失败:', error)
    // 显示详细的错误信息，包括后端返回的错误消息
    const errorMsg = error.response?.data?.message || error.response?.data?.msg || error.message || '未知错误'
    ElMessage.error('导入Excel数据失败: ' + errorMsg)
  } finally {
    importLoading.value = false
  }
}

// 导出Excel功能
const exportToExcel = async () => {
  try {
    ElMessage({ message: '正在准备导出数据...', type: 'info' })
    
    const response = await StudentUserService.getAllStudents()
    logger.log('导出数据响应:', response)
    
    const allStudents = response.data?.list || response.data?.rows || response?.list || response?.rows || []
    
    if (allStudents.length === 0) {
      ElMessage.warning('没有数据可导出')
      return
    }
    
    const exportData = allStudents.map(item => {
      return {
        '学号': item.studentId,
        '姓名': item.name,
        '性别': item.gender === 1 ? '男' : (item.gender === 2 ? '女' : '-'),
        '年级': item.grade,
        '专业': item.majorName || formatMajorName(item, null, item.majorId),
        '班级': item.className || formatClassName(item, null, item.classId),
        '创建时间': formatDate(null, null, item.createTime || item.createdTime),
        '更新时间': formatDate(null, null, item.updateTime || item.updatedTime)
      }
    })

    // 创建工作簿
    const wb = XLSX.utils.book_new()
    // 创建工作表
    const ws = XLSX.utils.json_to_sheet(exportData)
    // 添加工作表到工作簿
    XLSX.utils.book_append_sheet(wb, ws, '学生信息')
    
    // 生成文件名 - 包含当前日期时间
    const currentDate = new Date()
    const year = currentDate.getFullYear()
    const month = String(currentDate.getMonth() + 1).padStart(2, '0')
    const day = String(currentDate.getDate()).padStart(2, '0')
    const hours = String(currentDate.getHours()).padStart(2, '0')
    const minutes = String(currentDate.getMinutes()).padStart(2, '0')
    const seconds = String(currentDate.getSeconds()).padStart(2, '0')
    const fileName = `学生信息导出_${year}${month}${day}_${hours}${minutes}${seconds}.xlsx`
    
    // 导出文件
    XLSX.writeFile(wb, fileName)
    
    ElMessage({ message: '导出成功', type: 'success' })
  } catch (error) {
    logger.error('导出Excel失败:', error)
    ElMessage.error('导出Excel失败: ' + (error.message || '未知错误'))
  }
}

//页面加载时执行
onMounted(async () => {
  logger.log('页面挂载，开始加载数据...')
  await Promise.all([
    getMajorList(),
    getClassList()
  ])
  await queryPage()
  logger.log('数据加载完成')
})
</script>

<template>
  <div class="student-management-container">
    <!-- 顶部标题区域 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">学生管理</h1>
        <p class="page-description">管理系统中的所有学生账户信息</p>
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
          <el-form-item label="学号">
            <el-input
              v-model="searchForm.studentId"
              placeholder="请输入学号"
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
          <el-form-item label="年级">
            <el-input
              v-model="searchForm.grade"
              placeholder="例如：2024"
              maxlength="4"
              show-word-limit
              clearable
              @keyup.enter="queryPage"
            ></el-input>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
              <el-option label="已启用" :value="1"></el-option>
              <el-option label="已禁用" :value="0"></el-option>
            </el-select>
          </el-form-item>
        </div>
        <div class="search-row">
          <el-form-item label="专业">
            <el-select v-model="searchForm.majorId" placeholder="请选择专业" clearable filterable @change="handleMajorChange">
              <el-option v-for="major in majors" :key="major.value" :label="major.name" :value="major.value"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="班级">
            <el-select v-model="searchForm.classId" placeholder="请选择班级" clearable filterable>
              <el-option v-for="classItem in (searchForm.majorId ? classes.filter(c => c.majorId === searchForm.majorId) : classes)" 
                         :key="classItem.id" :label="classItem.name" :value="classItem.id"></el-option>
            </el-select>
          </el-form-item>
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
          <el-button v-if="authStore.hasPermission('user:student:add')" type="primary" @click="handleAddStudent()" class="action-btn primary">
            <el-icon><Plus /></el-icon>&nbsp;新增学生
          </el-button>
          <el-button v-if="authStore.hasPermission('user:student:delete')" type="danger" @click="delByIds" class="action-btn danger">
            <el-icon><Delete /></el-icon>&nbsp;批量删除
          </el-button>
        </div>
        <div class="secondary-actions">
          <el-dropdown v-if="authStore.hasPermission('user:student:view')" trigger="click" class="import-dropdown">
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
          <el-button v-if="authStore.hasPermission('user:student:view')" type="success" @click="exportToExcel()" class="action-btn success">
            <el-icon><Download /></el-icon>&nbsp;导出Excel
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 数据列表卡片 -->
    <el-card class="table-card" shadow="never">
      <el-table 
        ref="studentTable" 
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
        <el-table-column prop="studentId" label="学号" width="120" align="center" />
        <el-table-column prop="name" label="姓名" width="100" align="center" />
        <el-table-column prop="gender" label="性别" width="80" align="center">
          <template #default="scope">
            <span>{{ scope.row.gender === 1 ? '男' : scope.row.gender === 2 ? '女' : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="grade" label="年级" width="80" align="center" />
        <el-table-column prop="majorId" label="专业" width="160" align="center" :formatter="formatMajorName" />
        <el-table-column prop="classId" label="班级" width="100" align="center" :formatter="formatClassName" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'" size="small" class="status-tag">
              {{ scope.row.status === 1 ? '已启用' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedTime" label="更新时间" width="180" align="center" :formatter="formatDate" />
        <el-table-column prop="createdTime" label="创建时间" width="180" align="center" :formatter="formatDate" />    
        <el-table-column label="操作" align="center" width="270" fixed="right">
          <template #default="scope">
            <div class="action-buttons">
              <el-tooltip v-if="authStore.hasPermission('user:student:edit')" content="编辑" placement="top">
                <el-button type="primary" size="small" @click="handleUpdateStudent(scope.row.id)" class="table-btn edit">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('user:student:delete')" content="删除" placement="top">
                <el-button type="danger" size="small" @click="delById(scope.row.id)" class="table-btn delete">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('user:student:reset')" content="重置密码" placement="top">
                <el-button type="warning" size="small" @click="showResetPasswordDialog(scope.row)" class="table-btn reset">
                  <el-icon><Refresh /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('user:student:edit')" :content="scope.row.status === 1 ? '禁用账户' : '启用账户'" placement="top">
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

    <!-- 新增/编辑学生对话框 -->
    <el-dialog v-model="dialogFormVisible" :title="formTitle" width="500px" @close="handleCancel()" class="form-dialog">
      <!-- 条件渲染：根据表单标题显示对应的表单 -->
      <template v-if="formTitle === '新增学生'">
        <el-form 
          ref="addStudentFormRef"
          :model="addStudent"
          :rules="rules" 
          :label-width="labelWidth"
          class="student-form"
        >
          <el-form-item label="学号" prop="studentId">
            <el-input v-model="addStudent.studentId" placeholder="请输入学号"></el-input>
          </el-form-item>
          <el-form-item label="姓名" prop="name">
            <el-input v-model="addStudent.name" placeholder="请输入姓名"></el-input>
          </el-form-item>
          <el-form-item label="性别" prop="gender">
            <el-radio-group v-model="addStudent.gender">
              <el-radio :value="1">男</el-radio>
              <el-radio :value="2">女</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="年级" prop="grade">
            <el-input v-model="addStudent.grade" placeholder="请输入年级" maxlength="4" show-word-limit></el-input>
          </el-form-item>
          <el-form-item label="专业" prop="majorId">
            <el-select v-model="addStudent.majorId" placeholder="请选择专业" @change="handleDialogMajorChange">
              <el-option v-for="major in majors" :key="major.value" :label="major.name" :value="major.value"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="班级" prop="classId">
            <el-select v-model="addStudent.classId" placeholder="请选择班级">
              <el-option v-for="classItem in (addStudent.majorId ? classes.filter(c => c.majorId === addStudent.majorId) : classes)" 
                         :key="classItem.id" :label="classItem.name" :value="classItem.id"></el-option>
            </el-select>
          </el-form-item>
          <div class="form-tip">
            提示：密码将自动设置为123456，无需手动输入
          </div>
        </el-form>
      </template>
      
      <!-- 编辑学生表单 -->
      <template v-else-if="formTitle === '编辑学生'">
        <el-form 
          ref="editStudentFormRef"
          :model="editStudent"
          :rules="rules" 
          :label-width="labelWidth"
          class="student-form"
        >
          <el-form-item label="学号" prop="studentId">
            <el-input v-model="editStudent.studentId" placeholder="请输入学号"></el-input>
          </el-form-item>
          <el-form-item label="姓名" prop="name">
            <el-input v-model="editStudent.name" placeholder="请输入姓名"></el-input>
          </el-form-item>
          <el-form-item label="性别" prop="gender">
            <el-radio-group v-model="editStudent.gender">
              <el-radio :value="1">男</el-radio>
              <el-radio :value="2">女</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="年级" prop="grade">
            <el-input v-model="editStudent.grade" placeholder="请输入年级" maxlength="4" show-word-limit></el-input>
          </el-form-item>
          <el-form-item label="专业" prop="majorId">
            <el-select v-model="editStudent.majorId" placeholder="请选择专业" @change="handleDialogMajorChange">
              <el-option v-for="major in majors" :key="major.value" :label="major.name" :value="major.value"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="班级" prop="classId">
            <el-select v-model="editStudent.classId" placeholder="请选择班级">
              <el-option v-for="classItem in (editStudent.majorId ? classes.filter(c => c.majorId === editStudent.majorId) : classes)" 
                         :key="classItem.id" :label="classItem.name" :value="classItem.id"></el-option>
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
          <el-button type="primary" @click="saveStudent()" class="confirm-btn">确定</el-button>
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

    <!-- 导入Excel对话框 -->
    <el-dialog v-model="importDialogVisible" title="导入Excel学生数据" width="400px" class="import-dialog">
      <div class="import-content">
        <div class="file-selector">
          <el-button type="primary" @click="handleChooseFile" class="file-btn">
            选择Excel文件
          </el-button>
          <input 
            ref="excelFileInput"
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
          提示：请确保Excel文件包含学号、姓名、性别、年级、专业名称、班级名称等必要字段
        </div>
      </div>
      <template #footer>
        <el-button @click="closeImportDialog" class="cancel-btn">取消</el-button>
        <el-button type="primary" @click="importExcel" :loading="importLoading" class="confirm-btn">导入</el-button>
      </template>
    </el-dialog>
    
    <!-- 查看学生课程对话框 -->
    <el-dialog 
      v-model="viewCoursesDialogVisible" 
      :title="`查看学生 '${selectedStudent?.name || ''}' 的课程列表`" 
      width="900px"
      class="courses-dialog"
    >
      <div class="courses-content">
        <div v-if="studentCourses.length === 0" class="empty-courses">
          该学生尚未加入任何课程
        </div>
        <el-table 
          v-else 
          :data="studentCourses" 
          border 
          style="width: 100%"
          fit
          class="courses-table"
        >
          <el-table-column prop="courseId" label="课程ID" width="100" align="center" />
          <el-table-column prop="courseName" label="课程名称" min-width="200" align="center" />
          <el-table-column prop="formattedMajorNames" label="课程专业" min-width="180" align="center">
            <template #default="scope">
              {{ scope.row.formattedMajorNames || '未分配专业' }}
            </template>
          </el-table-column>
          <el-table-column prop="teacherName" label="授课教师" width="120" align="center" />
          <el-table-column prop="grade" label="年级" width="80" align="center" />
          <el-table-column prop="joinTime" label="加入时间" width="160" align="center">
            <template #default="scope">
              {{ formatDateTime(scope.row.joinTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="score" label="平均分" width="100" align="center">
            <template #default="scope">
              {{ scope.row.score > 0 ? scope.row.score.toFixed(2) : '暂未评分' }}
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="viewCoursesDialogVisible = false" class="cancel-btn">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.student-management-container {
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
  margin-bottom: 16px;
  align-items: flex-start;
}

.search-row:last-child {
  margin-bottom: 0;
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
.import-dialog,
.courses-dialog {
  border-radius: 16px;
  overflow: hidden;
}

.form-dialog :deep(.el-dialog__header),
.reset-dialog :deep(.el-dialog__header),
.import-dialog :deep(.el-dialog__header),
.courses-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #f8fbff 0%, #e6f7ff 100%);
  margin: 0;
  padding: 20px;
  border-bottom: 1px solid #e6f7ff;
}

.student-form {
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

/* 课程对话框内容 */
.courses-content {
  padding: 20px 0;
}

.empty-courses {
  text-align: center;
  color: #909399;
  padding: 40px 0;
}

.courses-table {
  border-radius: 8px;
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
  .student-management-container {
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
}
</style>