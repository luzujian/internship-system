<script setup lang="ts">
import { useAgentPageContext } from '@/agent/useAgentPageContext'
import { useFormContext } from '@/agent/formContext'
import logger from '@/utils/logger'
import { createRequiredValidator, createPhoneValidator, createEmailValidator } from '@/utils/validation'
import { readInitialCredentials } from '@/utils/initialCredentialPayload'
import { showInitialCredentials } from '@/utils/initialCredentialDialog'
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import type { CompanyUser, PaginationState } from '@/types/admin'
import type { FormFieldDescriptor } from '@/agent/types'
// 企业行精确局部接口：以 CompanyUser 为基，补全模板/脚本实际使用的扩展字段与后端别名，均可选
interface AlumniMemberRow {
  id?: string | number
  name?: string
  phone?: string
  position?: string
}
interface SchoolLiaisonRow {
  teacherId?: string | number
  teacherName?: string
  name?: string
  teacherPhone?: string
  phone?: string
}
interface VisitSummaryRow {
  currentYearVisitCount?: number
  totalVisitCount?: number
  lastVisitDate?: string
}
interface CompanyRow {
  id?: string | number
  username?: string
  companyName?: string
  contactPerson?: string
  contactPhone?: string
  contactEmail?: string
  email?: string
  phone?: string
  address?: string
  introduction?: string
  companyTag?: string | string[]
  industry?: string
  status?: number | string | null
  auditStatus?: number | string | null
  auditTime?: string
  applyTime?: string
  businessLicense?: string
  legalIdCard?: string
  recallStatus?: number | string | null
  createTime?: string
  updateTime?: string
  creditCode?: string
  scale?: string
  province?: string
  city?: string
  district?: string
  detailAddress?: string
  legalIdCardFront?: string
  legalIdCardBack?: string
  alumniRepresentativeName?: string
  alumniRepresentativePhone?: string
  alumniRepresentativePosition?: string
  website?: string
  description?: string
  cooperationMode?: string
  logo?: string
  photos?: string
  videos?: string
  isInternshipBase?: number | string | null
  acceptBackup?: number | string | null
  hasTrainingBaseAgreement?: number | string | null
  isAlumniUnit?: number | string | null
  hasReceivedInterns?: number | string | null
  currentEmployeesCount?: number | string | null
  plaquePhoto?: string
  registerTime?: string
  auditRemark?: string
  reviewerId?: number | string | null
  recallReason?: string
  recallApplyTime?: string
  recallAuditTime?: string
  recallReviewerId?: number | string | null
  recallAuditRemark?: string
  alumniMembers?: AlumniMemberRow[]
  schoolLiaisons?: SchoolLiaisonRow[]
  visitSummary?: VisitSummaryRow
}
// 表格实例最小接口（模板 ref 占位，未调用实例方法，避免 InstanceType 约束错误）
interface CompanyTableInstance {
  toggleRowSelection?: (row: CompanyRow, selected: boolean) => void
}
import { ElMessage, ElMessageBox, ElForm, ElTable, ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus'
import { Search, Refresh, Key, View, ArrowRight, Close, Plus, Edit, Delete, Download, Upload, Check, Switch, MoreFilled, ArrowDown, PictureFilled, VideoCameraFilled, OfficeBuilding, ZoomIn } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import request from '../../utils/request'
import companyService from '../../api/company'
import { exportToExcel } from '../../utils/xlsx'
import html2canvas from 'html2canvas'
import { jsPDF } from 'jspdf'
import { escapePdfText } from '../../utils/pdfExportSafety'
import { useSystemSettingsStore } from '../../store/systemSettings'
import { useAuthStore } from '../../store/auth'
import { companyQualificationMaterialByteUrl } from '@/utils/authorizedFileUrl'
import { downloadAuthorizedFile } from '@/utils/authorizedFileDownload'

const router = useRouter()
const systemSettingsStore = useSystemSettingsStore()
const authStore = useAuthStore()

// 日期格式化方法
const formatDate = (_row: unknown, _column: unknown, cellValue: string): string => {
  if (!cellValue) return ''
  const d = new Date(cellValue)
  if (isNaN(d.getTime())) return ''
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

// 搜索表单对象（含多选标签数组形态）
let searchForm = ref<CompanyRow>({ companyName: '', status: null, companyTag: [] })
// 列表展示数据
let tableData = ref<CompanyRow[]>([])
// 加载状态
let loading = ref(false)
// 分页数据
let pagination = ref<PaginationState>({ currentPage: 1, pageSize: 10, total: 0 })
// 表格引用
let companyTable = ref<CompanyTableInstance | null>(null)
// 多选数据
let selectedRows = ref<CompanyRow[]>([])

// 重置密码对话框控制
let resetPasswordDialogVisible = ref(false)
let resetPasswordCompanyId = ref<number | null>(null)

// 查看详情对话框
let viewDialogVisible = ref(false)
let currentCompany = ref<CompanyRow | null>(null)
let companyTagOptions = ref<string[]>([])

const legalCardIndices = computed(() => String(currentCompany.value?.legalIdCard || '')
  .split(',').slice(0, 2).map((value, index) => value.trim() ? index : -1).filter(index => index >= 0))

const downloadCompanyMaterial = async (kind: 'businessLicense' | 'legalIdCard' | 'plaquePhoto',
                                       index: number, label: string): Promise<void> => {
  const company = currentCompany.value
  const url = companyQualificationMaterialByteUrl(company?.id, kind, index, true)
  if (!url) { ElMessage.error('企业材料记录不可用'); return }
  const stored = kind === 'legalIdCard' ? String(company?.legalIdCard || '').split(',')[index] : company?.[kind]
  const extension = typeof stored === 'string' ? /\.([a-z0-9]{1,8})(?:$|[?#])/i.exec(stored)?.[1] : ''
  try {
    await downloadAuthorizedFile(url, extension ? `${label}.${extension}` : label)
  } catch {
    ElMessage.error('企业材料下载失败，请检查权限或联系管理员')
  }
}

// 企业宣传资料：photos/videos 为 JSON 数组字符串，容错解析（与教师端一致）
const companyPhotos = computed(() => {
  try {
    const arr = JSON.parse(currentCompany.value?.photos || '[]')
    return Array.isArray(arr) ? arr.filter((u: string) => !u.startsWith('blob:')) : []
  } catch { return [] }
})
const companyVideos = computed(() => {
  try {
    const arr = JSON.parse(currentCompany.value?.videos || '[]')
    return Array.isArray(arr) ? arr.filter((u: string) => !u.startsWith('blob:')) : []
  } catch { return [] }
})
const hasPromotion = computed(() =>
  !!currentCompany.value?.logo || companyPhotos.value.length > 0 || companyVideos.value.length > 0
)
// 宣传资料面板默认展开
const promotionActive = ref<string[]>(['promotion'])

// 新增/编辑对话框
let formDialogVisible = ref(false)
let formDialogTitle = ref('新增企业')
let isEdit = ref(false)
let formRef = ref<InstanceType<typeof ElForm> | null>(null)
let companyForm = ref<CompanyRow>({
  id: null,
  username: '',
  companyName: '',
  contactPerson: '',
  contactPhone: '',
  contactEmail: '',
  email: '',
  address: '',
  introduction: '',
  companyTag: '',
  isInternshipBase: 0,
  status: 0
})

// 表单验证规则
const formRules = {
  companyName: [createRequiredValidator('请输入企业名称')],
  contactPerson: [createRequiredValidator('请输入联系人')],
  contactPhone: createPhoneValidator('请输入 11 位中国大陆手机号'),
  contactEmail: createEmailValidator('请输入有效的邮箱地址，如 example@email.com'),
  email: createEmailValidator('请输入有效的邮箱地址，如 example@email.com', false),
  address: [createRequiredValidator('请输入企业地址')]
}


// ===== 智能填表：企业新增/编辑弹窗（只写响应式 model，不触发提交） =====
// 未覆盖字段说明：username/登录账号、status/auditStatus（系统维护）——均由系统维护
const formContext = useFormContext()
const companyFillFields = (): FormFieldDescriptor[] => [
  { prop: 'companyName', label: '企业名称', type: 'input', required: true },
  { prop: 'contactPerson', label: '联系人', type: 'input', required: true },
  { prop: 'contactPhone', label: '联系电话', type: 'input', required: true },
  { prop: 'contactEmail', label: '联系邮箱', type: 'input' },
  { prop: 'email', label: '个人邮箱', type: 'input' },
  { prop: 'address', label: '企业地址', type: 'input', required: true },
  { prop: 'introduction', label: '企业介绍', type: 'textarea' },
  { prop: 'isInternshipBase', label: '挂牌实习基地', type: 'radio', valueType: 'number',
    options: [{ label: '否', value: 0 }, { label: '是', value: 1 }] },
  { prop: 'companyTag', label: '标签', type: 'input', patternHint: '快捷标签（如校企双向选择/接受兜底/实习基地）需手动点选，多个标签用逗号分隔' }
]
const registerCompanyForm = (title: string): void => {
  formContext.register({
    formId: 'admin:company',
    title,
    fields: companyFillFields(),
    getFields: companyFillFields,
    getModel: () => companyForm.value,
    getFormEl: () => formRef.value
  })
}
watch(formDialogVisible, (open) => {
  if (!open) formContext.unregister('admin:company')
})
onUnmounted(() => formContext.unregister('admin:company'))


// 分页查询函数
const queryPage = async (): Promise<void> => {
  // 如果不是 onMounted 调用，则设置 loading
  if (!loading.value) {
    loading.value = true
  }
  
  try {
    let params = {
      ...searchForm.value,
      page: pagination.value.currentPage,
      pageSize: pagination.value.pageSize
    }
    
    logger.log('查询参数 - 原始:', searchForm.value)
    
    if (params.status !== null && params.status !== undefined && params.status !== '') {
      // String 包裹后 parseInt：字符串输入与原运行时一致，数字输入同样可解析
      params.status = parseInt(String(params.status))
    }
    
    if (params.companyTag && Array.isArray(params.companyTag) && params.companyTag.length > 0) {
      params.companyTag = params.companyTag.join(',')
    } else {
      delete params.companyTag
    }
    
    logger.log('查询参数 - 处理后:', params)
    
    // getAdminCompanies 声明返回双层 AxiosResponseWithMeta，运行时为单层 ApiResult；
    // params 在运行时与后端约定一致（status 数字/字符串/null 均可），用精确参数类型断言保持运行时一致
    const response = await companyService.getAdminCompanies(params as unknown as Parameters<typeof companyService.getAdminCompanies>[0])
    // 单层 ApiResult { code, data: { rows, total } } 收窄；声明层的双层 data.rows 在运行时不存在
    const listRes = response as unknown as { code?: unknown; data?: unknown }
    if (response && listRes.code === 200 && listRes.data !== undefined && listRes.data !== null) {
      const pageObj = listRes.data as { rows?: unknown; total?: unknown }
      const dataList: CompanyRow[] = Array.isArray(pageObj.rows) ? (pageObj.rows as CompanyRow[]) : []
      const totalCount: number = typeof pageObj.total === 'number' ? pageObj.total : 0
      tableData.value = dataList
      pagination.value.total = totalCount
      logger.log('查询结果 - 数据条数:', dataList.length, '总数:', totalCount)
    } else {
      tableData.value = []
      pagination.value.total = 0
    }
  } catch (error) {
    logger.error('查询企业列表失败:', error)
    tableData.value = []
    pagination.value.total = 0
    ElMessage.error('查询企业列表失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 清空搜索表单
const clear = (): void => {
  searchForm.value = { companyName: '', status: null, companyTag: [] }
  pagination.value.currentPage = 1
  queryPage()
}

// 分页处理函数
const handleSizeChange = (newSize: number): void => {
  pagination.value.pageSize = newSize
  queryPage()
}

const handleCurrentChange = (newCurrent: number): void => {
  pagination.value.currentPage = newCurrent
  queryPage()
}

// 多选处理
const handleSelectionChange = (selection: CompanyRow[]): void => {
  selectedRows.value = selection
}

// 查看企业详情
const viewCompanyDetail = async (id: string | number): Promise<void> => {
  try {
    const response = await request.get(`/admin/companies/${id}`)
    const result = response
    if (result && result.code === 200 && result.data) {
      currentCompany.value = result.data as CompanyRow
      viewDialogVisible.value = true
    } else {
      ElMessage.error(result?.msg || '获取企业信息失败')
    }
  } catch (error) {
    ElMessage.error('获取企业信息失败: ' + (error.message || '未知错误'))
  }
}

// 显示新增对话框
const showAddDialog = (): void => {
  isEdit.value = false
  formDialogTitle.value = '新增企业'
  companyForm.value = {
    id: null, username: '', companyName: '', contactPerson: '', contactPhone: '',
    contactEmail: '', email: '', address: '', introduction: '', isInternshipBase: 0, status: 1, auditStatus: 1
  }
  formDialogVisible.value = true
  registerCompanyForm('新增企业')
}

// 显示编辑对话框
const showEditDialog = (row: CompanyRow): void => {
  isEdit.value = true
  formDialogTitle.value = '编辑企业'
  companyForm.value = { ...row }
  formDialogVisible.value = true
  registerCompanyForm('编辑企业')
}

// 添加标签到表单
const addTagToForm = (tag: string): void => {
  // 自动标签联动对应状态字段，保存后标签重建（rebuildTags）才能派生出来，不会被清掉
  if (tag === '实习基地') companyForm.value.isInternshipBase = 1
  if (tag === '接受兜底') companyForm.value.acceptBackup = 1
  // companyTag 可能是搜索多选写入的数组，先归一为字符串再拆分（数组 join 后拆分与原字符串语义一致）
  const formTagRaw: string | string[] | null | undefined = companyForm.value.companyTag
  const formTagStr: string = Array.isArray(formTagRaw) ? formTagRaw.join(',') : (formTagRaw ?? '')
  if (formTagStr) {
    const tags = formTagStr.split(',').map(t => t.trim()).filter(t => t)
    if (!tags.includes(tag)) {
      tags.push(tag)
      companyForm.value.companyTag = tags.join(',')
    }
  } else {
    companyForm.value.companyTag = tag
  }
}

// 提交表单
const submitForm = async (): Promise<void> => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        const operation = formContext.beginFormOperation('admin:company')
        if (isEdit.value) {
          // 资质附件只通过专门的记录下载接口读取；普通资料编辑不能覆盖原附件字段。
          const editableFields = { ...companyForm.value }
          delete editableFields.businessLicense
          delete editableFields.legalIdCard
          delete editableFields.plaquePhoto
          const savedResponse = await companyService.updateCompany(editableFields as unknown as Parameters<typeof companyService.updateCompany>[0])
          if (savedResponse?.code !== 200) throw new Error(savedResponse?.message || '更新企业信息失败')
          operation.complete({ status: 'saved', label: '企业信息已保存', url: '/admin/companies' })
        } else {
          const savedResponse = await companyService.addCompany(companyForm.value as unknown as Parameters<typeof companyService.addCompany>[0])
          if (savedResponse?.code !== 200) throw new Error(savedResponse?.message || '添加企业信息失败')
          operation.complete({ status: 'saved', label: '企业信息已保存', url: '/admin/companies' })
        }
        formDialogVisible.value = false
        queryPage()
        if (isEdit.value) {
          ElMessage.success('更新成功')
        } else {
          ElMessage.success('新增成功')
        }
      } catch (error) {
        ElMessage.error((isEdit.value ? '更新' : '添加') + '失败: ' + (error.message || '未知错误'))
      }
    }
  })
}

// 对话框关闭处理
const handleDialogClose = (): void => {
  nextTick(() => {
    if (formRef.value) {
      formRef.value.resetFields()
    }
  })
}

// 删除企业
const deleteCompany = (id: string | number): void => {
  ElMessageBox.confirm('确定要删除该企业吗？', '确认删除', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    try {
      // id 转 Number 后 URL 与运行时一致（后端 Long 接受数字）
      await companyService.deleteCompany(Number(id))
      ElMessage.success('删除成功')
      queryPage()
    } catch (error) {
      ElMessage.error('删除失败: ' + (error.message || '未知错误'))
    }
  }).catch(() => {})
}

// 切换企业账户状态（启用/禁用）
const handleToggleStatus = async (id: string | number, currentStatus: number | string) => {
  const statusNum = Number(currentStatus)
  const newStatus = statusNum === 1 ? 0 : 1
  const statusText = newStatus === 1 ? '启用' : '禁用'

  try {
    await ElMessageBox.confirm(`确定要${statusText}该企业账户吗？`, '确认操作', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const response = await companyService.updateCompanyStatus(Number(id), newStatus)

    if (response && response.code === 200) {
      ElMessage.success(`${statusText}成功`)
      queryPage()
    }
  } catch (error) {
    if (error !== 'cancel') {
      logger.error(`${statusText}失败:`, error)
    }
  }
}

// 批量删除企业
const batchDelete = (): void => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要删除的企业')
    return
  }
  ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 家企业吗？`, '确认批量删除', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    try {
      // id 转 Number 后请求体数值与运行时一致（后端 Long 接受数字）
      const ids = selectedRows.value.map(row => Number(row.id))
      await companyService.batchDeleteCompanies(ids)
      ElMessage.success('批量删除成功')
      queryPage()
    } catch (error) {
      ElMessage.error('批量删除失败: ' + (error.message || '未知错误'))
    }
  }).catch(() => {})
}

// 导出Excel
const handleExportToExcel = async (): Promise<void> => {
  try {
    ElMessage({ message: '正在准备导出数据...', type: 'info' })

    const params = {
      companyName: searchForm.value.companyName,
      status: null
    }

    // 响应拦截器已返回 Blob（类型声明为 ApiResult，运行时为 Blob，用断言保持运行时一致）
    const blob = await companyService.exportCompanies(params) as unknown as Blob

    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `企业数据_${new Date().getTime()}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)

    ElMessage.success('导出成功')
  } catch (error) {
    logger.error('导出失败:', error)
    ElMessage.error('导出失败: ' + (error.message || '未知错误'))
  }
}

const importFileRef = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const importDialogVisible = ref(false)
const importLoading = ref(false)

const handleImportClick = (): void => {
  importDialogVisible.value = true
}

const handleChooseFile = (): void => {
  importFileRef.value.click()
}

const handleNativeFileChange = (e: Event): void => {
  // 用 instanceof 收窄替代 EventTarget 直读，与原分支语义一致
  const input = e.target instanceof HTMLInputElement ? e.target : null
  if (input && input.files && input.files.length > 0) {
    const file = input.files[0]
    const fileName = file.name.toLowerCase()

    if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls')) {
      ElMessage.error('请选择Excel文件(.xlsx或.xls格式)')
      input.value = ''
      return
    }

    selectedFile.value = file
  }
}

const closeImportDialog = (): void => {
  selectedFile.value = null
  importDialogVisible.value = false
  if (importFileRef.value) {
    importFileRef.value.value = ''
  }
}

const handleImportExcel = async (): Promise<void> => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择Excel文件')
    return
  }
  
  importLoading.value = true
  
  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    
    const response = await companyService.importCompanies(formData)
    const result = response as unknown as { code?: number; data?: unknown; msg?: string; message?: string }

    if (result?.code === 200) {
      const importResult = (result.data ?? {}) as { successCount?: unknown; createdCount?: unknown; updatedCount?: unknown; contactCount?: unknown; failCount?: unknown; failList?: unknown }
      const successCount: number = typeof importResult.successCount === 'number' ? importResult.successCount : 0
      const failCount: number = typeof importResult.failCount === 'number' ? importResult.failCount : 0
      const createdCount = typeof importResult.createdCount === 'number' ? importResult.createdCount : successCount
      const updatedCount = typeof importResult.updatedCount === 'number' ? importResult.updatedCount : 0
      const contactCount = typeof importResult.contactCount === 'number' ? importResult.contactCount : 0
      const failRaw: unknown = importResult.failList
      const failList: Array<{ rowNum?: unknown; errorMsg?: unknown }> = Array.isArray(failRaw)
        ? (failRaw as Array<{ rowNum?: unknown; errorMsg?: unknown }>)
        : []

      closeImportDialog()
      queryPage()
      const credentials = readInitialCredentials(result.data)
      if (credentials.length > 0) {
        await showInitialCredentials(credentials)
      }
      if (createdCount > credentials.length) {
        ElMessage.warning('部分成功账号未返回初始凭据，请通过重置密码流程处理')
      }
      
      let message = `导入完成：新增 ${createdCount} 家企业，更新 ${updatedCount} 家企业，处理 ${contactCount} 位联系人，失败 ${failCount} 行`
      
      if (failList.length > 0) {
        let errorDetails = '\n失败详情:\n'
        failList.forEach((fail, index) => {
          errorDetails += `${index + 1}. 第${fail.rowNum}行: ${fail.errorMsg || '未知错误'}\n`
        })
        
        await ElMessageBox.alert(message + errorDetails, '导入结果', {
          confirmButtonText: '确定',
          type: failCount > 0 ? 'warning' : 'success',
          dangerouslyUseHTMLString: false
        }).catch(() => undefined)
      } else {
        ElMessage.success(message)
      }
      
    } else {
      const importFailPick: unknown = result?.msg || result?.message
      ElMessage.error(typeof importFailPick === 'string' && importFailPick ? importFailPick : '导入失败')
    }
  } catch (error) {
    logger.error('导入失败:', error)
    ElMessage.error('导入失败: ' + (error.response?.data?.message || error.message || '未知错误'))
  } finally {
    importLoading.value = false
  }
}

// 下载导入模板
const downloadTemplate = async (): Promise<void> => {
  try {
    // 响应拦截器已返回 Blob（类型声明为 ApiResult，运行时为 Blob，用断言保持运行时一致）
    const blob = await request.get<Blob>('/admin/companies/import-template', {
      responseType: 'blob'
    }) as unknown as Blob

    // 创建下载链接
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', '企业导入模板.xlsx')
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

const updateAllTags = async (): Promise<void> => {
  try {
    await ElMessageBox.confirm('确定要批量更新所有企业的标签吗？', '确认批量更新', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const response = await request.post('/admin/companies/tags/update-all')
    if (response.code === 200) {
      ElMessage.success('批量更新标签成功')
      queryPage()
    } else {
      // data 为 unknown，收窄后读 msg，保持原 `response.data?.msg || '批量更新标签失败'` 语义
      const tagData: unknown = response.data
      const tagMsgPick: unknown =
        tagData !== null && typeof tagData === 'object' ? (tagData as { msg?: unknown }).msg : undefined
      ElMessage.error(typeof tagMsgPick === 'string' && tagMsgPick ? tagMsgPick : '批量更新标签失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      logger.error('批量更新标签失败:', error)
      ElMessage.error('批量更新标签失败: ' + (error.message || '未知错误'))
    }
  }
}


// 一键重置全部企业用户密码
const resetAllCompanyPasswords = async (): Promise<void> => {
  try {
    await ElMessageBox.confirm(
      '将把全部企业用户的登录密码统一重置为 123456，确定继续吗？',
      '一键重置全部企业密码',
      { type: 'warning', confirmButtonText: '确认重置', cancelButtonText: '取消' }
    )
    const response = await companyService.resetAllCompanyPasswords()
    const result = response as unknown as { code?: number; msg?: string; message?: string }
    if (result.code === 200) {
      ElMessage.success('全部企业用户密码已重置为 123456')
      queryPage()
    } else {
      ElMessage.error(result.msg || result.message || '一键重置全部企业用户密码失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      logger.error('一键重置全部企业用户密码失败:', error)
      ElMessage.error('一键重置全部企业用户密码失败')
    }
  }
}

// 显示单个企业重置密码对话框
const showResetPasswordDialog = (row: CompanyRow): void => {
  // id 转 Number：URL 模板展开后与运行时一致（后端 Long 接受数字）
  resetPasswordCompanyId.value = row.id === undefined || row.id === null ? null : Number(row.id)
  resetPasswordDialogVisible.value = true
}

// 重置密码
const resetPassword = async (): Promise<void> => {
  try {
    const response = await companyService.resetCompanyPassword(resetPasswordCompanyId.value!)
    const result = response as unknown as { code?: number; data?: unknown; message?: string }
    if (result.code === 200) {
      resetPasswordDialogVisible.value = false
      ElMessage.success('企业密码已重置为 123456')
    } else {
      ElMessage.error('密码重置失败：' + (result.message || '重置结果无效'))
    }
  } catch (error) {
    logger.error('重置密码失败:', error)
    ElMessage.error('重置密码失败：' + (error.message || '未知错误'))
  }
}


const formatAuditStatus = (auditStatus: number | string): string => {
  switch (auditStatus) {
    case 0: return '待审核'
    case 1: return '审核通过'
    case 2: return '审核拒绝'
    default: return '未知状态'
  }
}

const formatAccountStatus = (status: number | string): string => {
  switch (status) {
    case 0: return '待审核'
    case 1: return '正常'
    case 2: return '已禁用'
    case 3: return '已拒绝'
    default: return '未知状态'
  }
}

const getAuditStatusTagType = (auditStatus: number | string): string => {
  switch (auditStatus) {
    case 0: return 'warning'
    case 1: return 'success'
    case 2: return 'danger'
    default: return 'info'
  }
}

const getAccountStatusTagType = (status: number | string): string => {
  switch (status) {
    case 0: return 'warning'
    case 1: return 'success'
    case 2: return 'danger'
    case 3: return 'info'
    default: return 'info'
  }
}

const getTagType = (tag: string): string => {
  const tagMap = {
    '校企双向选择': 'primary',
    '接受兜底': 'warning',
    '国家级': 'danger',
    '省级': 'warning'
  }
  return tagMap[tag.trim()] || 'info'
}

// ===== 企业详情展示辅助（与教师端企业详情一致） =====

const formatScale = (scale: string): string => {
  const scaleMap: Record<string, string> = {
    '1-50': '1-50 人',
    '51-100': '51-100 人',
    '101-500': '101-500 人',
    '501-1000': '501-1000 人',
    '1000+': '1000 人以上'
  }
  return scaleMap[scale] || scale || '-'
}

const isSafeUrl = (url: string): boolean => {
  try {
    const u = new URL(url)
    return ['http:', 'https:'].includes(u.protocol)
  } catch {
    return false
  }
}

const getFullAddress = (company: CompanyRow): string => {
  const detail = [company.province, company.city, company.district, company.detailAddress].filter(Boolean).join('')
  return detail || company.address || '-'
}

// 审核通过的系统内企业统一属于校企合作，其他标签按对应字段显示。
const getCompanyTagList = (company: CompanyRow): Array<{ text: string; type: string }> => {
  const tags: Array<{ text: string; type: string }> = []
  const hasBackup = company.acceptBackup === 1 || String(company.cooperationMode || '').split(',').some(mode => mode.trim() === '兜底')
  if (company.auditStatus === 1) tags.push({ text: '校企双向选择', type: 'success' })
  if (company.isInternshipBase === 1) tags.push({ text: '实习基地', type: 'success' })
  if (hasBackup && !tags.some(t => t.text === '接受兜底')) tags.push({ text: '接受兜底', type: 'danger' })
  if (company.hasTrainingBaseAgreement === 1) tags.push({ text: '已签协议', type: 'info' })
  if (company.isAlumniUnit === 1) tags.push({ text: '校友单位', type: 'warning' })
  const skip = new Set(tags.map(t => t.text))
  skip.add('校企双向选择').add('学生自主联系')
  if (company.companyTag) {
    // companyTag 可能是数组形态，归一为字符串再拆分（与原字符串语义一致）
    const tagRaw: string | string[] = company.companyTag
    const tagStr: string = Array.isArray(tagRaw) ? tagRaw.join(',') : tagRaw
    tagStr.split(/[,，]/).forEach((tag: string) => {
      const t = tag.trim()
      if (t && !skip.has(t)) tags.push({ text: t, type: getTagType(t) })
    })
  }
  return tags
}

const goToRecallAudit = (company: CompanyRow): void => {
  router.push({
    path: '/admin/recall-audit',
    query: {
      companyId: company.id === undefined || company.id === null ? '' : String(company.id),
      companyName: company.companyName ?? '',
      contactPerson: company.contactPerson ?? '',
      contactPhone: company.contactPhone ?? ''
    }
  })
}

// 下载审核信息；附件没有按记录授权的下载接口，暂不从存储地址取文件。
const downloadMaterials = async (company: CompanyRow): Promise<void> => {
  const value = (input: unknown): string => escapePdfText(input == null || input === '' ? '未填写' : String(input))
  const alumni = Array.isArray(company.alumniMembers) && company.alumniMembers.length
    ? company.alumniMembers.map((m, i) => `校友${i + 1}：${m.name || '未填写'} / ${m.phone || '未填写'} / ${m.position || '未填写'}`).join('\n')
    : '未填写'
  const yesNo = (v: unknown) => v == null ? '未填写' : (v === 1 || v === '1' ? '是' : '否')
  const rows: Array<[string, string]> = [
    ['企业名称', value(company.companyName)], ['统一社会信用代码', value(company.creditCode)],
    ['所属行业', value(company.industry)], ['企业规模', value(company.scale)],
    ['联系人', value(company.contactPerson)], ['联系电话', value(company.contactPhone)],
    ['企业地址', value([company.province, company.city, company.district, company.detailAddress].filter(Boolean).join(' ') || company.address)],
    ['联系邮箱', value(company.contactEmail)], ['个人邮箱', value(company.email)], ['企业网站', value(company.website)],
    ['企业简介', value(company.introduction)], ['挂牌实习基地', yesNo(company.isInternshipBase)],
    ['接受兜底', yesNo(company.acceptBackup)], ['是否校友单位', yesNo(company.isAlumniUnit)],
    ['校友信息', value(alumni)], ['是否与学校签署实训基地协议', yesNo(company.hasTrainingBaseAgreement)]
  ]
  const html = `<div style="font-family:Arial,'Microsoft YaHei',sans-serif;padding:32px;width:794px;background:#fff;color:#222;box-sizing:border-box"><h1 style="text-align:center;color:#409EFF;margin:0 0 24px;font-size:26px">企业注册审核信息</h1><table style="width:100%;border-collapse:collapse;font-size:14px">${rows.map(([k,v]) => `<tr><td style="padding:10px;border:1px solid #ddd;background:#f5f7fa;font-weight:bold;width:32%">${k}</td><td style="padding:10px;border:1px solid #ddd;white-space:pre-wrap;word-break:break-word">${v}</td></tr>`).join('')}</table><div style="text-align:center;margin-top:28px;color:#909399;font-size:12px">生成时间：${new Date().toLocaleString('zh-CN')}</div></div>`
  const frame = document.createElement('iframe')
  frame.setAttribute('aria-hidden', 'true')
  frame.style.cssText = 'position:fixed;left:-10000px;top:0;width:794px;height:1400px;border:0;visibility:visible;'
  document.body.appendChild(frame)
  try {
    const doc = frame.contentDocument
    if (!doc) throw new Error('无法创建 PDF 渲染文档')
    doc.open(); doc.write(`<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#fff">${html}</body></html>`); doc.close()
    await new Promise(resolve => setTimeout(resolve, 100))
    const root = doc.body.firstElementChild as HTMLElement | null
    if (!root) throw new Error('审核信息内容为空')
    const canvas = await html2canvas(root, { scale: 2, backgroundColor: '#fff', useCORS: true, logging: false, windowWidth: 794, windowHeight: root.scrollHeight })
    if (!canvas.width || !canvas.height) throw new Error('审核信息内容为空')
    const pdf = new jsPDF({ unit: 'mm', format: 'a4', orientation: 'portrait', compress: true })
    const margin = 10, imageWidth = 190, pageImageHeight = 277, sourcePageHeight = Math.floor(pageImageHeight * canvas.width / imageWidth)
    for (let y = 0, page = 0; y < canvas.height; y += sourcePageHeight, page++) {
      if (page) pdf.addPage()
      const h = Math.min(sourcePageHeight, canvas.height - y), slice = document.createElement('canvas'); slice.width = canvas.width; slice.height = h
      const ctx = slice.getContext('2d'); if (!ctx) throw new Error('无法创建 PDF 画布')
      ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, slice.width, slice.height); ctx.drawImage(canvas, 0, y, canvas.width, h, 0, 0, canvas.width, h)
      pdf.addImage(slice.toDataURL('image/jpeg', 0.95), 'JPEG', margin, margin, imageWidth, h * imageWidth / canvas.width)
    }
    pdf.save(`${String(company.companyName || '企业').replace(/[\\/:*?"<>|\u0000-\u001f]/g, '_').slice(0, 80)}_企业注册审核信息.pdf`)
    ElMessage.success('下载成功')
  } catch (error) { ElMessage.error('下载失败: ' + (error instanceof Error ? error.message : '未知错误')) }
  finally { frame.remove() }
}
// 获取企业标签列表
const fetchCompanyTags = async (): Promise<void> => {
  try {
    const response = await companyService.getAdminCompanyTags()
    // getAdminCompanyTags 返回 ApiResult<string[]>，收窄后使用
    const tagRes = response as unknown as { code?: unknown; data?: unknown }
    if (response && tagRes.code === 200 && tagRes.data !== undefined && tagRes.data !== null) {
      const rawTags: unknown = tagRes.data
      companyTagOptions.value = Array.isArray(rawTags) ? rawTags.map(t => String(t)) : []
    }
  } catch (error) {
    logger.error('获取企业标签列表失败:', error)
  }
}

// 页面加载时执行
onMounted(async () => {
  // 立即显示 loading
  loading.value = true

  try {
    // 并行执行标签更新和数据查询，不阻塞 loading 显示
    request.post('/admin/companies/tags/update-all')
      .then(() => logger.log('企业标签已自动更新'))
      .catch((error) => logger.warn('自动更新企业标签失败:', error))

    // 立即查询数据 + 获取标签选项
    await Promise.all([queryPage(), fetchCompanyTags()])
  } catch (error) {
    logger.error('页面加载失败:', error)
  }
})

useAgentPageContext(() => ({
  title: '企业管理',
  view: viewDialogVisible.value ? 'company-detail' : formDialogVisible.value ? 'company-form' : 'page',
  object: viewDialogVisible.value && currentCompany.value?.id ? { type: 'admin_company', id: String(currentCompany.value.id) }
    : formDialogVisible.value && companyForm.value.id ? { type: 'admin_company', id: String(companyForm.value.id) } : null,
  selectedObjects: (selectedRows.value || []).slice(0, 20).map((row: CompanyRow) => ({ type: 'admin_company', id: String(row.id) })),
  filters: { keyword: searchForm.value.companyName || '', status: searchForm.value.status ?? '', visibleIds: tableData.value.slice(0, 20).map(row => String(row.id)) }
}))
</script>

<template>
  <div class="company-management-container">
    <!-- 隐藏的文件上传input -->
    <input type="file" ref="importFileRef" accept=".xlsx,.xls" style="display: none" @change="handleNativeFileChange" />
    
    <!-- 顶部标题区域 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">企业管理</h1>
        <p class="page-description">管理系统中的所有企业账户信息，可查看和下载企业注册审核材料（PDF格式）</p>
      </div>
      <div class="header-illustration">
        <div class="illustration-circle circle-1"></div>
        <div class="illustration-circle circle-2"></div>
      </div>
    </div>

    <!-- 搜索区域卡片 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" class="search-form" @keyup.enter="queryPage">
        <div class="search-row">
          <el-form-item label="企业名称">
            <el-input
              v-model="searchForm.companyName"
              placeholder="请输入企业名称"
              clearable
              @keyup.enter="queryPage"
            ></el-input>
          </el-form-item>
          <el-form-item label="标签">
            <el-select v-model="searchForm.companyTag" placeholder="请选择标签" clearable multiple style="width: 200px;">
              <el-option
                v-for="tag in companyTagOptions"
                :key="tag"
                :label="tag"
                :value="tag"
              ></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
              <el-option label="待审核" value="0"></el-option>
              <el-option label="审核通过" value="1"></el-option>
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
          <el-button v-if="authStore.hasPermission('user:company:add')" type="primary" data-agent-open @click="showAddDialog" class="action-btn primary">
            <el-icon><Plus /></el-icon>&nbsp;新增企业
          </el-button>
          <el-button v-if="authStore.hasPermission('user:company:delete')" type="danger" @click="batchDelete" class="action-btn danger">
            <el-icon><Delete /></el-icon>&nbsp;批量删除
          </el-button>
          <el-button v-if="authStore.hasPermission('user:company:reset')" type="warning" @click="resetAllCompanyPasswords" class="action-btn warning">
            <el-icon><Key /></el-icon>&nbsp;一键重置全部密码
          </el-button>
        </div>
        <div class="secondary-actions">
          <el-button v-if="authStore.hasPermission('user:company:edit')" type="warning" @click="updateAllTags" class="action-btn info">
            <el-icon><Refresh /></el-icon>&nbsp;批量更新标签
          </el-button>
          <el-dropdown v-if="authStore.hasPermission('user:company:view')" trigger="click" class="import-dropdown">
            <el-button type="primary" class="action-btn success">
              <el-icon><Upload /></el-icon>&nbsp;导入 Excel<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleImportClick">选择文件导入</el-dropdown-item>
                <el-dropdown-item @click="downloadTemplate">下载导入模板</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button v-if="authStore.hasPermission('user:company:view')" type="success" @click="handleExportToExcel" class="action-btn success">
            <el-icon><Download /></el-icon>&nbsp;导出Excel
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 数据列表卡片 -->
    <el-card class="table-card" shadow="never">
      <el-table 
        ref="companyTable" 
        :data="tableData" 
        border 
        style="width: 100%" 
        fit 
        row-key="id"
        class="data-table"
        v-loading="loading"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column type="index" label="序号" width="55" align="center" :index="(index) => (pagination.currentPage - 1) * pagination.pageSize + index + 1" />
        <el-table-column prop="companyName" label="企业名称" min-width="180" align="center" />
        <el-table-column prop="auditStatus" label="审核状态" width="120" align="center">
          <template #default="scope">
            <el-tag :type="getAuditStatusTagType(scope.row.auditStatus)" size="small" class="status-tag">
              {{ formatAuditStatus(scope.row.auditStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="账号状态" width="120" align="center">
          <template #default="scope">
            <el-tag :type="getAccountStatusTagType(scope.row.status)" size="small" class="status-tag">
              {{ formatAccountStatus(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="companyTag" label="标签" min-width="240" align="center">
          <template #default="scope">
            <div v-if="getCompanyTagList(scope.row).length" class="tag-container">
              <el-tag
                v-for="tag in getCompanyTagList(scope.row)"
                :key="tag.text"
                :type="tag.type"
                size="small"
                class="company-tag"
              >
                {{ tag.text }}
              </el-tag>
            </div>
            <span v-else class="no-tag">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="contactPerson" label="联系人" width="120" align="center" />
        <el-table-column prop="contactPhone" label="联系电话" width="150" align="center" />
        <el-table-column prop="contactEmail" label="联系邮箱" min-width="180" align="center" />
        <el-table-column prop="email" label="个人邮箱" min-width="180" align="center" show-overflow-tooltip />
        <el-table-column prop="address" label="企业地址" min-width="200" align="center" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="170" align="center" :formatter="formatDate" />
        <el-table-column label="操作" align="center" width="240" fixed="right">
          <template #default="scope">
            <div class="action-buttons">
              <!-- 常用操作按钮 -->
              <el-tooltip v-if="authStore.hasPermission('user:company:view')" content="查看详情" placement="top">
                <el-button type="success" size="small" @click="viewCompanyDetail(scope.row.id)" class="table-btn">
                  <el-icon><View /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('user:company:edit')" :content="Number(scope.row.status) === 1 ? '禁用账户' : '启用账户'" placement="top">
                <el-button
                  :type="Number(scope.row.status) === 1 ? 'danger' : 'success'"
                  size="small"
                  @click="handleToggleStatus(scope.row.id, scope.row.status)"
                  class="table-btn toggle"
                >
                  <el-icon><Switch /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip v-if="authStore.hasPermission('user:company:edit')" content="编辑" placement="top">
                <el-button type="primary" size="small" @click="showEditDialog(scope.row)" class="table-btn">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>

              <!-- 更多操作下拉菜单 -->
              <el-dropdown trigger="click" v-if="authStore.hasPermission('user:company:delete') || authStore.hasPermission('user:company:reset') || authStore.hasPermission('user:company:audit')">
                <el-button size="small" class="table-btn more">
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="authStore.hasPermission('user:company:delete')" @click="deleteCompany(scope.row.id)" divided class="danger-item">
                      <el-icon><Delete /></el-icon> 删除
                    </el-dropdown-item>
                    <el-dropdown-item v-if="authStore.hasPermission('user:company:reset')" @click="showResetPasswordDialog(scope.row)">
                      <el-icon><Refresh /></el-icon> 重置该企业密码
                    </el-dropdown-item>
                    <el-dropdown-item v-if="authStore.hasPermission('user:company:audit')" @click="downloadMaterials(scope.row)">
                      <el-icon><Download /></el-icon> 下载审核信息（不含附件）
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
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

    <!-- 新增/编辑企业对话框 -->
    <el-dialog v-model="formDialogVisible" :title="formDialogTitle" width="600px" @close="handleDialogClose" class="form-dialog">
      <el-form ref="formRef" :model="companyForm" :rules="formRules" label-width="100px">
        <el-form-item label="企业名称" prop="companyName">
          <el-input v-model="companyForm.companyName" placeholder="请输入企业名称" />
        </el-form-item>
        <div v-if="!isEdit" class="form-tip" style="margin-bottom: 18px;">
          提示：登录账号默认与企业名称相同，初始密码默认为 123456
        </div>
        <el-form-item label="联系人" prop="contactPerson">
          <el-input v-model="companyForm.contactPerson" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="companyForm.contactPhone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="联系邮箱" prop="contactEmail">
          <el-input v-model="companyForm.contactEmail" placeholder="请输入联系邮箱" />
        </el-form-item>
        <el-form-item label="个人邮箱" prop="email">
          <el-input v-model="companyForm.email" placeholder="请输入个人邮箱（接收验证码，选填）" />
        </el-form-item>
        <el-form-item label="企业地址" prop="address">
          <el-input v-model="companyForm.address" placeholder="请输入企业地址" />
        </el-form-item>
        <el-form-item label="企业介绍">
          <el-input v-model="companyForm.introduction" type="textarea" :rows="4" placeholder="请输入企业介绍" />
        </el-form-item>
        <el-form-item label="挂牌实习基地">
          <el-radio-group v-model="companyForm.isInternshipBase">
            <el-radio :label="0">否</el-radio>
            <el-radio :label="1">是</el-radio>
          </el-radio-group>
          <div class="form-tip" style="margin-left: 12px; font-size: 12px; color: #909399;">勾选后，该企业发布的岗位会显示"实习基地"标签</div>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="companyForm.companyTag" placeholder="请输入标签，多个标签用逗号分隔，例如：校企双向选择，接受兜底" />
          <div class="common-tags" style="margin-top: 8px;">
            <el-tag
              v-for="tag in ['校企双向选择', '接受兜底', '实习基地']"
              :key="tag"
              :type="getTagType(tag)"
              size="small"
              class="common-tag"
              @click="addTagToForm(tag)"
            >
              + {{ tag }}
            </el-tag>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="handleDialogClose(); formDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 查看企业详情对话框 -->
    <el-dialog v-model="viewDialogVisible" title="企业详情" width="750px" class="view-dialog">
      <div v-if="currentCompany" class="company-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="企业名称" :span="2">{{ currentCompany.companyName }}</el-descriptions-item>
          <el-descriptions-item label="企业标签" :span="2">
            <el-tag v-for="tag in getCompanyTagList(currentCompany)" :key="tag.text" :type="tag.type" size="small">
              {{ tag.text }}
            </el-tag>
            <span v-if="!getCompanyTagList(currentCompany).length">无</span>
          </el-descriptions-item>
          <el-descriptions-item label="统一社会信用代码" :span="2">{{ currentCompany.creditCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ currentCompany.contactPerson }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ currentCompany.contactPhone }}</el-descriptions-item>
          <el-descriptions-item label="联系邮箱" :span="2">{{ currentCompany.contactEmail }}</el-descriptions-item>
          <el-descriptions-item label="个人邮箱" :span="2">{{ currentCompany.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="企业地址" :span="2">{{ getFullAddress(currentCompany) }}</el-descriptions-item>
          <el-descriptions-item label="所属行业">
            <template v-if="currentCompany.industry === 'it'">IT企业</template>
            <template v-else-if="currentCompany.industry === 'medical_equipment'">医疗设备企业</template>
            <template v-else-if="currentCompany.industry === 'hospital'">医院</template>
            <template v-else-if="currentCompany.industry === 'other'">其它</template>
            <template v-else>{{ currentCompany.industry || '-' }}</template>
          </el-descriptions-item>
          <el-descriptions-item label="企业规模">{{ formatScale(currentCompany.scale) }}</el-descriptions-item>
          <el-descriptions-item label="企业网站" :span="2">
            <el-link v-if="currentCompany.website && isSafeUrl(currentCompany.website)" type="primary" :href="currentCompany.website" target="_blank" rel="noopener noreferrer">{{ currentCompany.website }}</el-link>
            <span v-else-if="currentCompany.website">{{ currentCompany.website }}</span>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="企业介绍" :span="2">
            <div class="company-intro">{{ currentCompany.introduction || '暂无介绍' }}</div>
          </el-descriptions-item>
          <el-descriptions-item label="校友信息" :span="2">
            <div v-if="currentCompany.alumniMembers?.length" class="detail-list">
              <div v-for="member in currentCompany.alumniMembers" :key="member.id || `${member.name}-${member.phone}`" class="detail-item">
                {{ member.name }} · {{ member.phone }} · {{ member.position }}
              </div>
            </div>
            <span v-else>非校友单位或暂无校友信息</span>
          </el-descriptions-item>
          <el-descriptions-item label="校内对接人" :span="2">
            <div v-if="currentCompany.schoolLiaisons?.length" class="detail-list">
              <div v-for="liaison in currentCompany.schoolLiaisons" :key="liaison.teacherId" class="detail-item">
                {{ liaison.teacherName || liaison.name }} · {{ liaison.teacherPhone || liaison.phone || '未填写电话' }}
              </div>
            </div>
            <span v-else>暂未设置</span>
          </el-descriptions-item>
          <el-descriptions-item label="走访情况" :span="2">
            本年度 {{ currentCompany.visitSummary?.currentYearVisitCount || 0 }} 次，累计 {{ currentCompany.visitSummary?.totalVisitCount || 0 }} 次
            <template v-if="currentCompany.visitSummary?.lastVisitDate">，最近走访：{{ currentCompany.visitSummary.lastVisitDate }}</template>
          </el-descriptions-item>
          <el-descriptions-item label="申请时间" :span="2">{{ formatDate(null, null, currentCompany.applyTime) }}</el-descriptions-item>
          <el-descriptions-item label="审核状态">
            <el-tag :type="getAccountStatusTagType(currentCompany.status)" size="small">
              {{ formatAccountStatus(currentCompany.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ formatDate(null, null, currentCompany.auditTime) }}</el-descriptions-item>
          <el-descriptions-item label="企业ID">{{ currentCompany.id }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDate(null, null, currentCompany.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatDate(null, null, currentCompany.updateTime) }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="authStore.hasPermission('user:company:audit')" class="detail-list">
          <strong>企业资质附件</strong>
          <el-button v-if="currentCompany.businessLicense" link type="primary"
            @click="downloadCompanyMaterial('businessLicense', 0, '营业执照')">下载营业执照</el-button>
          <el-button v-for="index in legalCardIndices" :key="index" link type="primary"
            @click="downloadCompanyMaterial('legalIdCard', index, `法人身份证${index === 0 ? '正面' : '反面'}`)">
            下载法人身份证{{ index === 0 ? '正面' : '反面' }}
          </el-button>
          <el-button v-if="currentCompany.plaquePhoto" link type="primary"
            @click="downloadCompanyMaterial('plaquePhoto', 0, '牌匾照片')">下载牌匾照片</el-button>
        </div>

        <!-- 企业宣传资料（折叠展示，与教师端一致） -->
        <el-collapse v-if="hasPromotion" v-model="promotionActive" class="promotion-panel">
          <el-collapse-item name="promotion">
            <template #title>
              <div class="promotion-header">
                <div class="promotion-title-wrap">
                  <span class="promotion-title-icon">
                    <el-icon><PictureFilled /></el-icon>
                  </span>
                  <span class="promotion-title-text">企业宣传资料</span>
                </div>
                <div class="promotion-badges">
                  <span v-if="currentCompany.logo" class="promo-badge">
                    <el-icon><OfficeBuilding /></el-icon>Logo
                  </span>
                  <span v-if="companyPhotos.length" class="promo-badge">
                    <el-icon><PictureFilled /></el-icon>{{ companyPhotos.length }} 张照片
                  </span>
                  <span v-if="companyVideos.length" class="promo-badge">
                    <el-icon><VideoCameraFilled /></el-icon>{{ companyVideos.length }} 个视频
                  </span>
                </div>
              </div>
            </template>

            <div class="promotion-body">
              <div v-if="currentCompany.logo" class="promo-section">
                <div class="promo-section-title">
                  <el-icon><OfficeBuilding /></el-icon>
                  <span>企业 Logo</span>
                </div>
                <div class="promo-logo-card">
                  <el-image
                    :src="currentCompany.logo"
                    class="promo-logo"
                    fit="contain"
                    :preview-src-list="[currentCompany.logo]"
                    preview-teleported
                  >
                    <template #placeholder>
                      <div class="promo-placeholder">加载中...</div>
                    </template>
                    <template #error>
                      <div class="promo-placeholder">图片加载失败</div>
                    </template>
                  </el-image>
                </div>
              </div>

              <div v-if="companyPhotos.length" class="promo-section">
                <div class="promo-section-title">
                  <el-icon><PictureFilled /></el-icon>
                  <span>企业照片</span>
                  <span class="promo-count">{{ companyPhotos.length }}</span>
                </div>
                <div class="promo-photo-grid">
                  <div
                    v-for="(url, i) in companyPhotos"
                    :key="i"
                    class="promo-photo-item"
                  >
                    <el-image
                      :src="url"
                      class="promo-photo"
                      fit="cover"
                      :preview-src-list="companyPhotos"
                      :initial-index="i"
                      preview-teleported
                    >
                      <template #placeholder>
                        <div class="promo-placeholder">加载中...</div>
                      </template>
                      <template #error>
                        <div class="promo-placeholder">加载失败</div>
                      </template>
                    </el-image>
                    <div class="promo-photo-overlay">
                      <el-icon class="overlay-icon"><ZoomIn /></el-icon>
                      <span class="overlay-text">点击查看大图</span>
                    </div>
                  </div>
                </div>
              </div>

              <div v-if="companyVideos.length" class="promo-section">
                <div class="promo-section-title">
                  <el-icon><VideoCameraFilled /></el-icon>
                  <span>企业视频</span>
                  <span class="promo-count">{{ companyVideos.length }}</span>
                </div>
                <div class="promo-video-grid">
                  <div
                    v-for="(url, i) in companyVideos"
                    :key="i"
                    class="promo-video-item"
                  >
                    <video :src="url" controls preload="metadata" class="promo-video" />
                  </div>
                </div>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="viewDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>


    <!-- 重置密码对话框 -->
    <el-dialog v-model="resetPasswordDialogVisible" title="重置密码" width="400px" class="reset-dialog">
      <div class="reset-content">
        <p>密码将统一重置为 123456。</p>
        <p class="reset-tip">请确认是否继续重置密码</p>
      </div>
      <template #footer>
        <el-button @click="resetPasswordDialogVisible = false" class="cancel-btn">取消</el-button>
        <el-button type="primary" @click="resetPassword" class="confirm-btn">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入 Excel对话框 -->
    <el-dialog v-model="importDialogVisible" title="导入 Excel企业数据" width="400px" class="import-dialog">
      <div class="import-content">
        <div class="file-selector">
          <el-button type="primary" @click="handleChooseFile" class="file-btn">
            选择Excel文件
          </el-button>
          <div v-if="selectedFile" class="file-info">
            已选择文件: <span class="file-name">{{ selectedFile.name }}</span>
          </div>
        </div>
        <div class="import-tip">
          支持企业导入模板和招聘会报名表。同名企业合并到原账号，非空资料按表格更新，原密码和历史实习关联保留。多位联系人可分行填写，或在同一行按相同顺序用换行、顿号分隔姓名和电话。
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeImportDialog" class="cancel-btn">取消</el-button>
          <el-button type="primary" @click="handleImportExcel" :loading="importLoading" class="confirm-btn">
            确定导入
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.form-tip {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
  line-height: 1.5;
}

.company-management-container {
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

.circle-1 { width: 60px; height: 60px; top: 0; right: 0; animation-delay: 0s; }
.circle-2 { width: 40px; height: 40px; bottom: 10px; right: 20px; animation-delay: 3s; }

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

.search-card, .actions-card, .table-card {
  border-radius: 16px;
  border: none;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  margin-bottom: 24px;
  overflow: hidden;
}

.search-card { padding: 24px; }
.actions-card { padding: 20px 24px; }
.table-card { padding: 0; }

.search-form { width: 100%; }
.search-row {
  display: flex;
  gap: 20px;
  margin-bottom: 16px;
  align-items: flex-start;
}
.search-row:last-child { margin-bottom: 0; }
.search-row .el-form-item { margin-bottom: 0; flex: 1; }
.search-actions { flex: none; margin-left: auto; }
.search-btn, .reset-btn { border-radius: 8px; padding: 10px 20px; }

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

.data-table { border-radius: 8px; }
.data-table :deep(.el-table__header) th {
  background-color: #f8fbff;
  color: #409EFF;
  font-weight: 600;
}
.data-table :deep(.el-table__row) td { border-bottom: 1px solid #f0f7ff; }
.status-tag { border-radius: 12px; font-weight: 500; }

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
.table-btn:hover { transform: scale(1.1); }
.table-btn.more {
  color: #606266;
  border-color: #dcdfe6;
}
.table-btn.more:hover {
  background-color: #f5f7fa;
}

/* 下拉菜单删除项红色样式 */
.el-dropdown-menu__item.danger-item {
  color: #f56c6c;
}
.el-dropdown-menu__item.danger-item:hover {
  background-color: #fef0f0;
}

.pagination-container {
  padding: 20px;
  display: flex;
  justify-content: flex-end;
}

.form-dialog :deep(.el-dialog__header),
.view-dialog :deep(.el-dialog__header),
.audit-dialog :deep(.el-dialog__header),
.import-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #f8fbff 0%, #e6f7ff 100%);
  margin: 0;
  padding: 20px;
  border-bottom: 1px solid #e6f7ff;
}

.form-dialog,
.view-dialog,
.audit-dialog,
.import-dialog {
  border-radius: 16px;
  overflow: hidden;
}

.company-detail { padding: 20px 0; }
.company-intro { white-space: pre-wrap; line-height: 1.6; }

/* 企业宣传资料（与教师端企业详情一致） */
.promotion-panel {
  margin-top: 16px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.promotion-panel :deep(.el-collapse-item__header) {
  height: 56px;
  padding: 0 18px;
  border-bottom: 1px solid #f0f2f5;
  background: linear-gradient(135deg, #f6f9ff 0%, #ffffff 100%);
  font-weight: 600;
  color: #303133;
  align-items: center;
}

.promotion-panel :deep(.el-collapse-item__header:hover) {
  background: linear-gradient(135deg, #eef4ff 0%, #fafcff 100%);
}

.promotion-panel :deep(.el-collapse-item__content) {
  padding: 18px;
  background: #fafbfc;
}

.promotion-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
}

.promotion-title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.promotion-title-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #409eff 0%, #6a8dff 100%);
  color: #fff;
  font-size: 16px;
  box-shadow: 0 2px 6px rgba(64, 158, 255, 0.35);
}

.promotion-title-text {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  letter-spacing: 0.3px;
}

.promotion-badges {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.promo-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 9px;
  font-size: 12px;
  color: #409eff;
  background: #ecf5ff;
  border: 1px solid #d9ecff;
  border-radius: 12px;
  line-height: 1.4;
}

.promo-badge .el-icon {
  font-size: 12px;
}

.promotion-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.promo-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.promo-section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  padding-left: 8px;
  position: relative;
}

.promo-section-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 13px;
  background: linear-gradient(180deg, #409eff 0%, #6a8dff 100%);
  border-radius: 2px;
}

.promo-section-title .el-icon {
  color: #409eff;
  font-size: 14px;
}

.promo-count {
  margin-left: 2px;
  padding: 0 6px;
  min-width: 18px;
  height: 16px;
  line-height: 16px;
  text-align: center;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: #409eff;
  border-radius: 8px;
}

/* Logo 卡片：正方形容器，图片 contain 完整显示 */
.promo-logo-card {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 140px;
  height: 140px;
  background: linear-gradient(135deg, #f6f9ff 0%, #ffffff 100%);
  border: 1px solid #ebeef5;
  border-radius: 8px;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.6);
}

.promo-logo {
  width: 100%;
  height: 100%;
  cursor: zoom-in;
}

.promo-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 12px;
  color: #909399;
  background: #f5f7fa;
}

/* 照片网格 */
.promo-photo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
}

.promo-photo-item {
  position: relative;
  aspect-ratio: 4 / 3;
  border-radius: 8px;
  overflow: hidden;
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  cursor: zoom-in;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.promo-photo-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.18);
  border-color: #c6e2ff;
}

.promo-photo {
  width: 100%;
  height: 100%;
  display: block;
}

.promo-photo-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0) 0%, rgba(0, 0, 0, 0.55) 100%);
  color: #fff;
  opacity: 0;
  transition: opacity 0.2s ease;
  pointer-events: none;
}

.promo-photo-item:hover .promo-photo-overlay {
  opacity: 1;
}

.overlay-icon {
  font-size: 20px;
}

.overlay-text {
  font-size: 12px;
  letter-spacing: 0.3px;
}

/* 视频网格 */
.promo-video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.promo-video-item {
  aspect-ratio: 16 / 9;
  border-radius: 8px;
  overflow: hidden;
  background: #000;
  border: 1px solid #ebeef5;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
}

.promo-video {
  width: 100%;
  height: 100%;
  display: block;
  background: #000;
}

.detail-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-item {
  color: #606266;
  line-height: 1.6;
}

.image-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
  align-items: center;
}

.material-image {
  width: 200px;
  height: 140px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.material-image:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.material-content {
  color: #909399;
  text-align: center;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 8px;
}

.audit-content { padding: 20px 0; text-align: center; }
.audit-message { font-size: 16px; color: #606266; }

.dialog-footer { display: flex; justify-content: flex-end; gap: 12px; }

.import-content { padding: 20px 0; }
.file-selector { display: flex; flex-direction: column; align-items: flex-start; gap: 12px; }
.file-btn { border-radius: 8px; }
.file-info { margin-top: 10px; color: #606266; }
.file-name { color: #409EFF; font-weight: 500; }
.import-tip { color: #909399; font-size: 12px; margin-top: 16px; }

.tag-container { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; justify-content: center; }
.company-tag { margin: 0; }
.no-tag { color: #909399; display: flex; justify-content: center; }

.reset-dialog .reset-content {
  text-align: center;
  padding: 20px 0;
}

.reset-dialog .reset-content p {
  margin: 12px 0;
  color: #606266;
}

.reset-dialog .password-text {
  font-weight: 600;
  color: #f56c6c;
  font-size: 16px;
}

.reset-dialog .reset-tip {
  color: #909399;
  font-size: 13px;
  margin-top: 16px;
}

@media screen and (max-width: 1200px) {
  .search-row { flex-wrap: wrap; }
  .search-actions { margin-left: 0; width: 100%; justify-content: flex-end; }
  .actions-container { flex-direction: column; gap: 12px; }
  .primary-actions, .secondary-actions { width: 100%; justify-content: flex-start; }
}
</style>
