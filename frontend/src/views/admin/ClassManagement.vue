<script setup lang="ts">
import logger from '@/utils/logger'
import { onMounted, ref, nextTick } from 'vue'
import { queryPageApi, addApi, queryInfoApi, updateApi, deleteApi, batchDeleteApi, importExcelApi, downloadClassTemplateApi } from '../../api/class'
import ClassService from '../../api/class'
import MajorService from '../../api/major'
import StudentUserService from '../../api/StudentUserService'
import TeacherUserService from '../../api/TeacherUserService'

import request from '../../utils/request'
import { ElMessage, ElMessageBox, ElCheckbox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Plus, Upload, Download, Edit, Delete, ArrowDown } from '@element-plus/icons-vue'
import { exportToExcel } from '../../utils/xlsx'
import { readExcelFile as readExcelUtil } from '../../utils/xlsx'
import { useAuthStore } from '../../store/auth'
import type { Class, PaginationState, SelectOption } from '@/types/admin'

const authStore = useAuthStore()

// 定义类型接口
interface ClassWithCounts extends Class {
  studentCount?: number
  confirmedCount?: number
  notFoundCount?: number
  hasOfferCount?: number
  teacherName?: string
  majorName?: string
  ext?: Record<string, unknown>
}

interface SearchClassForm {
  name: string
  grade: string
  majorId: string | null
}

interface AddClassForm {
  id: string
  name: string
  grade: string
  majorId: string | null
  teacherId: string | null
}

interface TeacherOption {
  teacherUserId: string
  name: string
}

interface ExcelDataItem {
  name?: string
  grade?: string
  majorId?: string | null
  teacherId?: string | null
  studentCount?: number
}

interface ImportResult {
  successCount: number
  failCount: number
  failList: { rowNum: number; errorMsg: string }[]
}

//搜索表单对象
const searchClass = ref<SearchClassForm>({ name: '', grade: '', majorId: null })
//列表展示数据
const tableData = ref<ClassWithCounts[]>([])
//专业列表数据
const majorList = ref<SelectOption[]>([])
//教师列表数据
const teacherList = ref<TeacherOption[]>([])
//加载状态
const loading = ref(false)
// 表格引用
const classTable = ref(null)
// 选中的行 ID 数组
const selectedRowKeys = ref<string[]>([])

// 导入 Excel 相关状态
const importDialogVisible = ref(false)
const selectedFile = ref<File | null>(null)
const excelFileInput = ref<HTMLElement | null>(null)
const importLoading = ref(false)

// 新增/编辑班级相关状态
const dialogFormVisible = ref(false)
const formTitle = ref('')
const labelWidth = ref('100px')

// 新增班级表单数据
const addClass = ref<AddClassForm>({
  id: '',
  name: '',
  grade: '',
  majorId: null,
  teacherId: null
})

// 编辑班级表单数据
const editClass = ref<AddClassForm>({
  id: '',
  name: '',
  grade: '',
  majorId: null,
  teacherId: null
})

// 表单引用
const addClassFormRef = ref<FormInstance | null>(null)
const editClassFormRef = ref<FormInstance | null>(null)

// 表单校验规则
const rules = ref<FormRules>({
  name: [
    { required: true, message: '班级名称为必填项', trigger: 'blur' },
    { min: 1, max: 30, message: '班级名称长度为 1-30 个字', trigger: 'blur' }
  ],
  grade: [
    { required: true, message: '年级为必填项', trigger: 'blur' },
    { pattern: /^\d{4}$/, message: '年级必须是 4 位数字', trigger: 'blur' }
  ],
  majorId: [
    { required: true, message: '请选择专业', trigger: 'change' }
  ]
})

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

//钩子函数 - 页面加载时触发
onMounted(async () => {
  loading.value = true
  try {
    // 先获取专业列表和教师列表，再查询班级数据
    await getMajorList()
    await getTeacherList()
  } finally {
    await queryPage()
  }
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
      } else if (response.code === 200 && response.data) {
        // 处理带 code 和 data 的响应格式
        majorList.value = Array.isArray(response.data) ? response.data : []
      } else {
        majorList.value = []
      }
    } else {
      majorList.value = []
    }

    logger.log('最终的专业列表数据:', majorList.value)

    // 确保数据格式正确，为每个专业对象添加 id 和 name 属性
    if (majorList.value.length > 0) {
      // 转换数据结构，确保每个对象都有 id 和 name 属性
      majorList.value = majorList.value.map(major => ({
        id: String(major.id || major.majorId || ''),
        name: major.name || major.majorName || '未知专业'
      }))
    }
  } catch (error) {
    logger.error('获取专业列表失败:', error)
    majorList.value = []
    ElMessage.error('获取专业列表失败：' + (error instanceof Error ? error.message : '未知错误'))
  }
}

//获取教师列表
const getTeacherList = async (): Promise<void> => {
  try {
    logger.log('开始获取教师列表...')
    const response = await TeacherUserService.getAllTeachers()
    logger.log('教师列表响应:', response)

    let teachers: any[] = []

    // 处理响应数据
    if (response && response.data) {
      if (Array.isArray(response.data)) {
        teachers = response.data
      } else if (response.data.rows && Array.isArray(response.data.rows)) {
        // 处理分页格式 { rows: [...], total: ... }
        teachers = response.data.rows
      } else if (response.rows && Array.isArray(response.rows)) {
        // 处理其他分页格式
        teachers = response.rows
      }
    }

    // 过滤无效数据并确保每个教师对象都有必要的属性
    teacherList.value = teachers.filter((t: any) => {
      if (t === null || t === undefined) return false
      if (typeof t !== 'object') return false
      // 确保有必要的基本属性
      return t.teacherUserId !== undefined && t.name !== undefined
    }).map((t: any) => ({
      teacherUserId: String(t.teacherUserId || ''),
      name: String(t.name || '')
    }))

    logger.log('最终的教师列表数据:', teacherList.value)
  } catch (error) {
    logger.error('获取教师列表失败:', error)
    teacherList.value = []
    ElMessage.error('获取教师列表失败：' + (error instanceof Error ? error.message : '未知错误'))
  }
}

//分页组件
const pagination = ref<PaginationState>({ currentPage: 1, pageSize: 10, total: 0 })
//每页展示记录数发生变化时触发
const handleSizeChange = (pageSize: number): void => {
  pagination.value.pageSize = pageSize
  queryPage()
}
//当前页码发生变化时触发
const handleCurrentChange = (page: number): void => {
  pagination.value.currentPage = page
  queryPage()
}

//分页条件查询
const queryPage = async (): Promise<void> => {
  loading.value = true
  try {
    logger.log('开始查询班级列表...')

    // 确保在查询班级前已经获取了专业列表
    if (majorList.value.length === 0) {
      logger.log('专业列表为空，先获取专业列表...')
      await getMajorList()
    }

    const result = await queryPageApi(
        searchClass.value.name,
        searchClass.value.grade,
        searchClass.value.majorId,
        pagination.value.currentPage,
        pagination.value.pageSize
    );

    // 清空选中的行
    selectedRowKeys.value = []

    logger.log('班级列表查询结果:', result)

    if(result.code) {
      logger.log('班级数据原始格式:', result.data.rows)

      // 为每条班级数据添加专业名称和负责教师名称
      tableData.value = result.data.rows.map((classItem: any) => {
        logger.log('处理班级项:', classItem)
        logger.log('班级项的 studentCount:', classItem.studentCount)
        logger.log('班级项的 confirmedCount:', classItem.confirmedCount)
        logger.log('班级项的 notFoundCount:', classItem.notFoundCount)
        logger.log('班级项的 hasOfferCount:', classItem.hasOfferCount)

        // 确保 classItem 对象存在
        if (!classItem) {
          return {
            majorName: '',
            teacherName: '',
            ext: {}
          }
        }

        // 转换 majorId 类型，确保比较时类型一致
        const majorId = classItem.majorId !== undefined && classItem.majorId !== null
          ? (typeof classItem.majorId === 'string' ? parseInt(classItem.majorId) : classItem.majorId)
          : null

        // 查找匹配的专业
        const matchedMajor = majorList.value.find(major => {
          // 同样转换专业 id 类型
          const majorIdNum = typeof major.id === 'string' ? parseInt(major.id) : major.id
          return majorIdNum === majorId
        })

        // 查找匹配的教师
        logger.log('查找教师 - classItem.teacherId:', classItem.teacherId)
        logger.log('教师列表:', teacherList.value)

        const matchedTeacher = teacherList.value.find((teacher: any) => {
          logger.log('比较教师:', teacher.teacherUserId, '与', classItem.teacherId)
          // 尝试多种匹配方式
          if (teacher.teacherUserId === classItem.teacherId) {
            logger.log('直接匹配成功')
            return true
          }
          // 尝试去掉 T 前缀匹配
          if (classItem.teacherId && classItem.teacherId.startsWith('T')) {
            const withoutPrefix = classItem.teacherId.substring(1)
            logger.log('尝试去掉 T 前缀匹配:', teacher.teacherUserId, '与', withoutPrefix)
            if (teacher.teacherUserId === withoutPrefix) {
              logger.log('去掉 T 前缀匹配成功')
              return true
            }
          }
          // 尝试加上 T 前缀匹配
          if (teacher.teacherUserId && teacher.teacherUserId.startsWith('T')) {
            const withPrefix = 'T' + classItem.teacherId
            logger.log('尝试加上 T 前缀匹配:', teacher.teacherUserId, '与', withPrefix)
            if (teacher.teacherUserId === withPrefix) {
              logger.log('加上 T 前缀匹配成功')
              return true
            }
          }
          return false
        })

        logger.log('匹配结果:', matchedTeacher)

        // 优先使用后端返回的 teacherName，如果没有再尝试通过教师列表匹配
        let finalTeacherName = classItem.teacherName || ''
        if (!finalTeacherName && matchedTeacher) {
          finalTeacherName = matchedTeacher.name || ''
        }

        // 确保保留 ext 属性
        const result = {
          ...classItem,
          majorName: matchedMajor ? matchedMajor.name : '未知专业',
          teacherName: finalTeacherName
        }

        logger.log('处理后的 result:', result)

        // 确保 ext 属性存在且为对象
        if (result.ext === undefined || result.ext === null) {
          result.ext = {}
        }

        return result
      })

      logger.log('处理后的班级数据:', tableData.value)
      pagination.value.total = result.data.total
    } else {
      logger.error('查询班级失败:', result.msg)
      tableData.value = []
      pagination.value.total = 0
      ElMessage.error('查询班级失败：' + (result.msg || '未知错误'))
    }
  } catch (error) {
    logger.error('查询班级列表时发生异常:', error)
    tableData.value = []
    pagination.value.total = 0
    ElMessage.error('查询班级失败：' + (error instanceof Error ? error.message : '未知错误'))
  } finally {
    loading.value = false
  }
}

//清空搜索栏
const clear = (): void => {
  searchClass.value = {name:'', grade: '', majorId: null}
  queryPage()
}

//----------- 新增 / 修改 ---------------------------

//清空新增班级表单数据
const clearAddClassForm = (): void => {
  addClass.value = {
    id: '',
    name: '',
    grade: '',
    majorId: null,
    teacherId: null
  }
}

//清空编辑班级表单数据
const clearEditClassForm = (): void => {
  editClass.value = {
    id: '',
    name: '',
    grade: '',
    majorId: null,
    teacherId: null
  }
}

//新增班级
const handleAddClass = async (): Promise<void> => {
  // 确保教师列表和专业列表已加载
  if (majorList.value.length === 0) {
    await getMajorList()
  }
  if (teacherList.value.length === 0) {
    await getTeacherList()
  }
  
  formTitle.value = '新增班级'
  clearAddClassForm()
  dialogFormVisible.value = true
}

//修改班级
const handleUpdateClass = async (id: string): Promise<void> => {
  clearEditClassForm()
  formTitle.value = '编辑班级'
  
  // 确保教师列表和专业列表已加载
  if (majorList.value.length === 0) {
    await getMajorList()
  }
  if (teacherList.value.length === 0) {
    await getTeacherList()
  }
  
  let result = await queryInfoApi(id)
  logger.log('班级详情响应:', result)
  
  if(result.code === 200 && result.data){
    // 使用 JSON.parse(JSON.stringify) 进行深拷贝，避免数据引用问题
    const classData = JSON.parse(JSON.stringify(result.data))
    editClass.value = {
      id: classData.id || '',
      name: classData.name || '',
      grade: classData.grade || '',
      majorId: classData.majorId || null,
      teacherId: classData.teacherId || null
    }
    logger.log('编辑班级数据:', editClass.value)
    dialogFormVisible.value = true
  } else {
    ElMessage.error('获取班级信息失败：' + (result.msg || '未知错误'))
  }
}

//表单校验规则已在文件顶部声明，这里不再重复声明

// 移除动态计算表单，现在使用两个独立的表单

//重置表单
const resetForm = (formRef: FormInstance | null): void => {
  if (!formRef || !formRef.value) return
  formRef.value.resetFields()
}

//处理取消按钮点击事件
const handleCancel = (): void => {
  dialogFormVisible.value = false
  nextTick(() => {
    resetForm(addClassFormRef.value)
    resetForm(editClassFormRef.value)
    clearAddClassForm()
    clearEditClassForm()
  })
}

//-------------保存班级信息
const saveClass = async (): Promise<void> => {
  logger.log('保存班级 - 开始')
  logger.log('保存班级 - formTitle:', formTitle.value)
  
  let formData: AddClassForm
  let formRefValue: FormInstance | null

  // 根据表单标题判断是新增还是编辑操作
  if (formTitle.value === '新增班级') {
    formData = addClass.value
    formRefValue = addClassFormRef.value
    logger.log('保存班级 - 使用新增表单')
  } else {
    formData = editClass.value
    formRefValue = editClassFormRef.value
    logger.log('保存班级 - 使用编辑表单')
  }

  logger.log('保存班级 - 表单数据:', JSON.stringify(formData))
  logger.log('保存班级 - 表单引用:', formRefValue)

  // 表单校验
  if (!formRefValue) {
    logger.error('表单引用为空')
    ElMessage.error('表单初始化失败，请重试')
    return
  }

  try {
    // 使用回调方式验证表单
    await new Promise<void>((resolve, reject) => {
      formRefValue!.validate((valid) => {
        if (valid) {
          logger.log('表单验证通过')
          resolve()
        } else {
          logger.log('表单验证失败')
          reject(new Error('表单验证失败'))
        }
      })
    })

    logger.log('开始调用 API...')
    let result
    // 表单验证通过后再调用 API
    if (formTitle.value === '新增班级') {
      result = await addApi(formData)
    } else {
      result = await updateApi(formData)
    }

    logger.log('API 返回结果:', result)

    if (result && result.code === 200) {
      ElMessage.success('操作成功')
      dialogFormVisible.value = false
      queryPage()
    } else {
      ElMessage.error(result ? result.msg : '操作失败')
    }
  } catch (error) {
    logger.error('保存班级失败:', error)
    // 验证失败时，Element Plus 会自动显示错误信息
  }
}




//------- 删除班级
//根据 ID 删除单个班级
const delById = async (id: string): Promise<void> => {
  try {
    await ElMessageBox.confirm(
      '确定要删除该班级吗？此操作将删除该班级及其所有相关数据，此操作不可撤销！',
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
      ElMessage.error('删除失败：' + (response?.msg || '未知错误'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      logger.error('删除班级失败:', error)
      ElMessage.error('删除失败：' + (error instanceof Error ? error.message : '未知错误'))
    }
  }
}



// 批量删除班级
const batchDeleteClass = async (): Promise<void> => {
  if (selectedRowKeys.value.length === 0) {
    ElMessage.warning('请先选择要删除的班级')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedRowKeys.value.length} 个班级吗？此操作将删除这些班级及其所有相关数据，此操作不可撤销！`,
      '确认批量删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
        center: true
      }
    )

    const response = await batchDeleteApi(selectedRowKeys.value)

    if (response && response.code === 200) {
      ElMessage.success('删除成功')
      selectedRowKeys.value = []
      queryPage()
    } else {
      ElMessage.error('删除失败：' + (response?.msg || '未知错误'))
    }
  } catch (error) {
    if (error !== 'cancel') {
      logger.error('批量删除班级失败:', error)
      ElMessage.error('删除失败：' + (error instanceof Error ? error.message : '未知错误'))
    }
  }
}

// 处理选中行变化
const handleSelectionChange = (selection: unknown[]): void => {
  selectedRowKeys.value = selection.map((row: any) => row.id)
}

// 显示导入 Excel 对话框
const showImportDialog = (): void => {
  selectedFile.value = null
  importDialogVisible.value = true

  // 不要在对话框刚显示时自动触发文件选择，让用户手动点击按钮
}

// 处理文件选择按钮点击
const handleChooseFile = (): void => {
  if (excelFileInput.value) {
    excelFileInput.value.click()
  } else {
    logger.warn('excelFileInput is not available yet')
  }
}

// 处理原生 input 文件选择
const handleNativeFileChange = (e: Event): void => {
  const target = e.target as HTMLInputElement
  logger.log('handleNativeFileChange called with:', e)

  if (target && target.files && target.files.length > 0) {
    const file = target.files[0]
    const fileName = file.name.toLowerCase()

    if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls')) {
      ElMessage.error('请选择 Excel 文件 (.xlsx 或.xls 格式)')
      target.value = ''
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
const closeImportDialog = (): void => {
  selectedFile.value = null
  importDialogVisible.value = false
  // 重置文件输入框
  const fileInput = document.querySelector('#excel-file')
  if (fileInput) {
    (fileInput as HTMLInputElement).value = ''
  }
}

// 导入 Excel 数据
const importExcel = async (): Promise<void> => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }

  importLoading.value = true
  try {
    // 确保专业列表已加载
    if (majorList.value.length === 0) {
      await getMajorList()
      if (majorList.value.length === 0) {
        throw new Error('无法获取专业列表，请刷新页面后重试')
      }
    }

    // 确保教师列表已加载
    if (teacherList.value.length === 0) {
      await getTeacherList()
      if (teacherList.value.length === 0) {
        throw new Error('无法获取教师列表，请刷新页面后重试')
      }
    }

    // 先读取 Excel 文件内容，进行前端处理
    const fileData = await readExcelFile(selectedFile.value)

    // 处理 Excel 数据，将专业名称转换为专业 ID，将负责教师名称转换为教师 ID
    const processedData = processExcelData(fileData)

    // 过滤出有效的数据（确保有专业 ID、班级名称和年级）
    const validData = processedData.filter(item =>
      item.majorId && item.majorId !== null && item.majorId !== undefined && item.majorId !== '' &&
      item.name && item.name.trim() !== '' &&
      item.grade && item.grade.toString().trim() !== ''
    )
    const invalidCount = processedData.length - validData.length

    // 构建前端失败列表
    const frontFailList: { rowNum: number; errorMsg: string }[] = []
    if (invalidCount > 0) {
      // 找出无效的数据并记录失败原因
      processedData.forEach((item, index) => {
        if (!item.majorId || !item.name || !item.grade || item.name.trim() === '' || item.grade.toString().trim() === '') {
          let errorMsg = []
          if (!item.name || item.name.trim() === '') errorMsg.push('班级名称为空')
          if (!item.grade || item.grade.toString().trim() === '') errorMsg.push('年级为空')
          if (!item.majorId) errorMsg.push('未找到对应专业')
          frontFailList.push({
            rowNum: index + 1,
            errorMsg: errorMsg.join('，')
          })
        }
      })
    }

    // 记录处理结果
    logger.log('处理后的数据:', processedData)
    logger.log('有效数据数量:', validData.length)
    logger.log('所有有效数据的专业 ID:', validData.map(item => item.majorId))
    logger.log('所有有效数据的负责教师 ID:', validData.map(item => item.teacherId))
    logger.log('所有有效数据的班级人数:', validData.map(item => item.studentCount))
    logger.log('准备发送的有效数据:', validData)
    if (validData.length > 0 && validData[0]) {
      logger.log('第一条有效数据的详细信息:', validData[0])
      logger.log('第一条有效数据的所有字段:', Object.keys(validData[0]))
    }

    // 如果没有有效数据，直接显示错误
    if (validData.length === 0) {
      // 显示前端处理失败结果
      let errorDetails = '以下数据未能成功解析：\n\n'
      frontFailList.forEach((fail, index) => {
        errorDetails += `${index + 1}. 第${fail.rowNum}行：${fail.errorMsg}\n`
      })

      ElMessageBox.alert(errorDetails, '导入结果', {
        type: 'warning',
        confirmButtonText: '确定',
        dangerouslyUseHTMLString: false,
        callback: () => {
          importDialogVisible.value = false
        }
      })
      importLoading.value = false
      return
    }

    // 构建 FormData，确保包含所有必要信息
    const formData = new FormData()

    // 添加文件（保留原始功能）
    formData.append('file', selectedFile.value)

    // 添加前端处理好的有效数据
    formData.append('validData', JSON.stringify(validData))

    // 额外添加标志，让后端知道这是已经处理过的数据
    formData.append('hasValidData', 'true')

    // 发送请求到后端
    const response = await importExcelApi(formData)

    // 检查后端返回结果
    logger.log('导入响应:', response)

    if (response && response.code === 200) {
      const importResult = response.data as ImportResult
      const successCount = importResult.successCount || 0
      const failCount = importResult.failCount || 0
      const backendFailList = importResult.failList || []

      // 合并前端和后端的失败列表
      const totalFailList = [...frontFailList, ...backendFailList]
      const totalFailCount = frontFailList.length + failCount

      // 构建导入结果消息
      let message = `导入完成，成功：${successCount} 条，失败：${totalFailCount} 条`

      // 如果有失败记录，显示失败详情
      if (totalFailList.length > 0) {
        let errorDetails = '\n失败详情:\n'
        totalFailList.forEach((fail, index) => {
          errorDetails += `${index + 1}. 第${fail.rowNum}行：${fail.errorMsg || '未知错误'}\n`
        })

        // 显示包含详细信息的消息框
        ElMessageBox.alert(message + errorDetails, '导入结果', {
          type: totalFailCount > 0 ? 'warning' : 'success',
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
      ElMessage.error('导入失败：' + (response?.msg || '未知错误'))
    }

  } catch (error) {
    logger.error('导入 Excel 数据失败:', error)
    logger.error('错误详情:', (error as any).response?.data)

    // 显示详细的错误信息
    const errorMsg = (error as any).response?.data?.message ||
                     (error as any).response?.data?.msg ||
                     (error instanceof Error ? error.message : '未知错误')
    ElMessage.error('导入 Excel 数据失败：' + errorMsg)
  } finally {
    importLoading.value = false
  }
}

const readExcelFile = async (file: File): Promise<unknown[]> => {
  return await readExcelUtil(file) as unknown[]
}

// 处理 Excel 数据，将专业名称转换为专业 ID，将负责教师名称转换为教师 ID
const processExcelData = (excelData: unknown[]): ExcelDataItem[] => {
  logger.log('原始 Excel 数据:', excelData)
  logger.log('可用专业列表:', majorList.value)
  logger.log('可用教师列表:', teacherList.value)

  return excelData.map((item: any, index) => {
    // 处理不同可能的表头名称（兼容中英文）
    let name = item['班级名称'] || item['className'] || item['name']
    let grade = item['年级'] || item['grade']

    // 确保 grade 是字符串且长度为 4
    if (grade && grade.toString) {
      grade = grade.toString().trim()
    }

    // 查找专业 ID
    let majorId: string | null = null
    // 尝试从不同可能的列名中获取专业名称
    const majorName = item['专业'] || item['major'] || item['专业名称'] || item['majorName']

    if (majorName && majorList.value.length > 0) {
      // 去除前后空格，增强匹配精度
      const trimmedMajorName = String(majorName).trim()

      // 从专业列表中查找对应的专业 ID
      // 先尝试精确匹配
      let major = majorList.value.find(m => m.name === trimmedMajorName)

      // 如果精确匹配失败，尝试部分匹配
      if (!major) {
        major = majorList.value.find(m => m.name.includes(trimmedMajorName) || trimmedMajorName.includes(m.name))
      }

      if (major) {
        // 确保专业 ID 是数值类型（因为后端是 bigint 类型）
        majorId = String(major.id)
        logger.log(`第${index + 1}行数据匹配成功：专业名称 [${trimmedMajorName}] -> 专业 ID [${majorId}]`)
      } else {
        logger.warn(`第${index + 1}行数据匹配失败：未找到专业 [${trimmedMajorName}]`)
      }
    }

    // 查找负责教师 ID
    let teacherId: string | null = null
    // 尝试从不同可能的列名中获取负责教师名称
    const teacherName = item['负责教师'] || item['负责老师'] || item['teacherName'] || item['teacher']

    // 获取班级人数
    let studentCount = 0
    const studentCountStr = item['班级人数'] || item['studentCount'] || item['student_count'] || item['人数']
    logger.log(`第${index + 1}行数据 - 班级人数原始值：${studentCountStr}, 类型：${typeof studentCountStr}`)
    if (studentCountStr !== null && studentCountStr !== undefined && studentCountStr !== '') {
      try {
        studentCount = parseInt(String(studentCountStr).trim())
        if (isNaN(studentCount) || studentCount < 0) {
          studentCount = 0
        }
        logger.log(`第${index + 1}行数据 - 班级人数解析后：${studentCount}`)
      } catch (e) {
        logger.warn(`第${index + 1}行数据班级人数格式不正确：${studentCountStr}`)
        studentCount = 0
      }
    } else {
      logger.log(`第${index + 1}行数据 - 班级人数为空`)
    }

    if (teacherName && teacherList.value.length > 0) {
      // 去除前后空格，增强匹配精度
      const trimmedTeacherName = String(teacherName).trim()

      // 从教师列表中查找对应的教师 ID
      // 先尝试精确匹配
      let teacher = teacherList.value.find((t: any) => t.name === trimmedTeacherName)

      // 如果精确匹配失败，尝试部分匹配
      if (!teacher) {
        teacher = teacherList.value.find((t: any) => t.name.includes(trimmedTeacherName) || trimmedTeacherName.includes(t.name))
      }

      if (teacher) {
        teacherId = teacher.teacherUserId
        logger.log(`第${index + 1}行数据匹配成功：负责教师名称 [${trimmedTeacherName}] -> 教师 ID [${teacherId}]`)
      } else {
        logger.warn(`第${index + 1}行数据匹配失败：未找到负责教师 [${trimmedTeacherName}]`)
      }
    }

    return {
      name: name?.toString?.().trim(),
      grade,
      majorId,
      teacherId,
      studentCount
    }
  })
}

// 导出 Excel 功能
const handleExportToExcel = async (): Promise<void> => {
  try {
    ElMessage({ message: '正在准备导出数据...', type: 'info' })

    // 使用表格中已经处理好的数据，包含 majorName 和 teacherName
    const allClasses = tableData.value

    if (allClasses.length === 0) {
      ElMessage.warning('没有数据可导出')
      return
    }

    const exportData = allClasses.map((item: any) => {
      return {
        '班级名称': item.name,
        '年级': item.grade,
        '专业': item.majorName || item.majorId,
        '负责教师': item.teacherName || '',
        '创建时间': formatDate(null, null, item.createTime || item.createdTime),
        '更新时间': formatDate(null, null, item.updateTime || item.updatedTime)
      }
    })

    const currentDate = new Date()
    const year = currentDate.getFullYear()
    const month = String(currentDate.getMonth() + 1).padStart(2, '0')
    const day = String(currentDate.getDate()).padStart(2, '0')
    const hours = String(currentDate.getHours()).padStart(2, '0')
    const minutes = String(currentDate.getMinutes()).padStart(2, '0')
    const seconds = String(currentDate.getSeconds()).padStart(2, '0')
    const fileName = `班级信息_${year}${month}${day}_${hours}${minutes}${seconds}`

    await exportToExcel(exportData, fileName, '班级信息')

    ElMessage({ message: '导出成功，请等待文件下载完成', type: 'success' })
  } catch (error) {
    logger.error('导出 Excel 失败:', error)
    ElMessage.error('导出 Excel 失败：' + (error instanceof Error ? error.message : '未知错误'))
  }
}

// 下载导入模板
const downloadTemplate = async (): Promise<void> => {
  try {
    ElMessage({ message: '正在准备模板下载...', type: 'info' })

    const response = await downloadClassTemplateApi()

    // 创建下载链接
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', '班级导入模板.xlsx')
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
</script>

<template>
  <div class="class-management-container">
    <!-- 顶部标题区域 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">班级管理</h1>
        <p class="page-description">管理系统中的所有班级信息</p>
      </div>
      <div class="header-illustration">
        <div class="illustration-circle circle-1"></div>
        <div class="illustration-circle circle-2"></div>
      </div>
    </div>

    <!-- 搜索区域卡片 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchClass" class="search-form" @keyup.enter="queryPage">
        <div class="search-row">
          <el-form-item label="班级名称">
            <el-input
              v-model="searchClass.name"
              placeholder="请输入班级名称"
              clearable
              @keyup.enter="queryPage"
            ></el-input>
          </el-form-item>
          <el-form-item label="年级">
            <el-input
              v-model="searchClass.grade"
              placeholder="例如：2024"
              maxlength="4"
              show-word-limit
              clearable
              @keyup.enter="queryPage"
            ></el-input>
          </el-form-item>
          <el-form-item label="专业">
            <el-select v-model="searchClass.majorId" placeholder="请选择专业" clearable filterable>
              <el-option
                v-for="major in majorList"
                :key="major.id"
                :label="major.name"
                :value="major.id"
              />
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
          <el-button v-if="authStore.hasPermission('class:add')" type="primary" @click="handleAddClass()" class="action-btn primary">
            <el-icon><Plus /></el-icon>&nbsp;新增班级
          </el-button>
          <el-button
            v-if="authStore.hasPermission('class:delete')"
            type="danger"
            @click="batchDeleteClass()"
            class="action-btn danger"
          >
            <el-icon><Delete /></el-icon>&nbsp;批量删除
          </el-button>
          <span v-if="selectedRowKeys.length > 0" class="selected-count">
            已选择 {{ selectedRowKeys.length }} 项
          </span>
        </div>
        <div class="secondary-actions">
          <el-dropdown v-if="authStore.hasPermission('class:view')" trigger="click" class="import-dropdown">
            <el-button type="primary" class="action-btn primary">
              <el-icon><Upload /></el-icon>&nbsp;导入 Excel<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="showImportDialog">选择文件导入</el-dropdown-item>
                <el-dropdown-item @click="downloadTemplate">下载导入模板</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button v-if="authStore.hasPermission('class:view')" type="success" @click="exportToExcel" class="action-btn success">
            <el-icon><Download /></el-icon>&nbsp;导出 Excel
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 表格区域 -->
    <el-card class="table-card" shadow="never">
      <div class="table-header">
        <h3 class="table-title">班级列表</h3>
        <div class="table-actions">
          <span class="total-count">共 {{ pagination.total }} 条记录</span>
        </div>
      </div>

      <el-table
        ref="classTable"
        :data="tableData"
        border
        style="width: 100%"
        fit
        v-loading="loading"
        class="data-table"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" align="center" width="55" />
        <el-table-column prop="id" label="ID" align="center" width="80" />
        <el-table-column prop="name" label="班级名称" align="center" width="150" />
        <el-table-column prop="grade" label="年级" align="center" width="100" />
        <el-table-column prop="majorName" label="专业" align="center" width="150" />
        <el-table-column prop="studentCount" label="学生人数" align="center" width="100" />
        <el-table-column prop="teacherName" label="负责教师" align="center" width="120" />
        <el-table-column prop="createTime" label="创建时间" align="center" width="180">
          <template #default="scope">
            <span>{{ formatDate(null, null, scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" align="center" width="180">
          <template #default="scope">
            <span>{{ formatDate(null, null, scope.row.updateTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="100" fixed="right">
          <template #default="scope">
            <div class="action-buttons">
              <el-tooltip v-if="authStore.hasPermission('class:edit')" content="编辑" placement="top">
                <el-button type="primary" size="small" @click="handleUpdateClass(scope.row.id)" class="table-btn">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('class:delete')" content="删除" placement="top">
                <el-button type="danger" size="small" @click="delById(scope.row.id)" class="table-btn">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>

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

    <!-- 新增/编辑班级对话框 -->
    <el-dialog v-model="dialogFormVisible" :title="formTitle" width="500px">
      <el-form
        v-if="formTitle === '新增班级'"
        ref="addClassFormRef"
        :model="addClass"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="班级名称" prop="name">
          <el-input v-model="addClass.name" placeholder="请输入班级名称" />
        </el-form-item>
        <el-form-item label="年级" prop="grade">
          <el-input v-model="addClass.grade" placeholder="例如：2024" maxlength="4" />
        </el-form-item>
        <el-form-item label="专业" prop="majorId">
          <el-select v-model="addClass.majorId" placeholder="请选择专业" filterable>
            <el-option
              v-for="major in majorList"
              :key="major.id"
              :label="major.name"
              :value="major.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="负责教师" prop="teacherId">
          <el-select v-model="addClass.teacherId" placeholder="请选择负责教师" filterable clearable>
            <el-option
              v-for="teacher in teacherList"
              :key="teacher?.teacherUserId || ''"
              :label="teacher?.name || ''"
              :value="teacher?.teacherUserId || ''"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <el-form
        v-else
        ref="editClassFormRef"
        :model="editClass"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="班级名称" prop="name">
          <el-input v-model="editClass.name" placeholder="请输入班级名称" />
        </el-form-item>
        <el-form-item label="年级" prop="grade">
          <el-input v-model="editClass.grade" placeholder="例如：2024" maxlength="4" />
        </el-form-item>
        <el-form-item label="专业" prop="majorId">
          <el-select v-model="editClass.majorId" placeholder="请选择专业" filterable>
            <el-option
              v-for="major in majorList"
              :key="major.id"
              :label="major.name"
              :value="major.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="负责教师" prop="teacherId">
          <el-select v-model="editClass.teacherId" placeholder="请选择负责教师" filterable clearable>
            <el-option
              v-for="teacher in teacherList"
              :key="teacher?.teacherUserId || ''"
              :label="teacher?.name || ''"
              :value="teacher?.teacherUserId || ''"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" @click="saveClass">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入 Excel 对话框 -->
    <el-dialog v-model="importDialogVisible" title="导入 Excel 班级数据" width="400px" class="import-dialog">
      <div class="import-content">
        <input
          ref="excelFileInput"
          type="file"
          accept=".xlsx,.xls"
          style="display: none"
          @change="handleNativeFileChange"
        />
        <div class="file-selector">
          <el-button type="primary" @click="handleChooseFile" class="file-btn">
            选择 Excel 文件
          </el-button>
          <div v-if="selectedFile" class="file-info">
            已选择文件：<span class="file-name">{{ selectedFile.name }}</span>
          </div>
        </div>
        <div class="import-tip">
          提示：请先下载导入模板，按照模板格式填写数据后再导入
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeImportDialog" class="cancel-btn">取消</el-button>
          <el-button type="primary" @click="importExcel" :loading="importLoading" class="confirm-btn">
            确定导入
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.class-management-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

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
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
}

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
  padding: 20px;
}

.table-card {
  padding: 0;
}

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
  display: flex;
  gap: 10px;
}

.actions-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.primary-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.secondary-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.action-btn {
  border-radius: 8px;
  padding: 10px 20px;
}

.action-btn.primary {
  background-color: #409EFF;
  border-color: #409EFF;
}

.action-btn.danger {
  background-color: #f56c6c;
  border-color: #f56c6c;
}

.action-btn.success {
  background-color: #67c23a;
  border-color: #67c23a;
}

.action-btn.warning {
  background-color: #e6a23c;
  border-color: #e6a23c;
}

.selected-count {
  font-size: 14px;
  color: #909399;
  margin-left: 10px;
}

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

.table-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.total-count {
  font-size: 14px;
  color: #909399;
}

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

.data-table {
  border-radius: 8px;
}

.data-table :deep(.el-table__header) th {
  background-color: #f8fbff;
  color: #409EFF;
  font-weight: 600;
}

.data-table :deep(.el-table__body) tr:hover {
  background-color: #f0f7ff;
}

.pagination-container {
  display: flex;
  justify-content: center;
  padding: 20px;
  border-top: 1px solid #f0f0f0;
}

.custom-pagination :deep(.el-pagination) {
  display: flex;
  align-items: center;
}

.custom-pagination :deep(.el-pagination .el-pager li) {
  border-radius: 6px;
  margin: 0 4px;
}

.custom-pagination :deep(.el-pagination .el-pager li.is-active) {
  background-color: #409EFF;
  color: white;
}

.custom-pagination :deep(.el-pagination button) {
  border-radius: 6px;
}

.import-dialog :deep(.el-dialog__header),
.view-dialog :deep(.el-dialog__header),
.audit-dialog :deep(.el-dialog__header) {
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.import-dialog :deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.import-dialog :deep(.el-dialog__body) {
  padding: 24px;
}

.import-dialog :deep(.el-dialog__footer) {
  padding: 12px 24px;
  border-top: 1px solid #f0f0f0;
}

.import-content {
  padding: 10px;
}

.file-selector {
  margin-bottom: 20px;
}

.file-btn {
  padding: 10px 16px;
  font-size: 14px;
  border-radius: 8px;
}

.file-info {
  margin-top: 12px;
  font-size: 13px;
  color: #606266;
}

.file-name {
  color: #67c23a;
  font-weight: 500;
}

.import-tip {
  font-size: 13px;
  color: #909399;
  background-color: #f8f9fa;
  padding: 12px 16px;
  border-radius: 6px;
  border-left: 3px solid #e6a23c;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.cancel-btn,
.confirm-btn {
  border-radius: 8px;
  padding: 10px 24px;
  min-width: 100px;
}
</style>