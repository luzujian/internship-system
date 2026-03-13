<script setup lang="ts">
import logger from '@/utils/logger'
import { createRequiredValidator, createPhoneValidator, createEmailValidator } from '@/utils/validation'
import { ref, onMounted, nextTick } from 'vue'
import type { CompanyUser, PaginationState } from '@/types/admin'
import { ElMessage, ElMessageBox, ElForm, ElTable, ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus'
import { Search, Refresh, View, ArrowRight, Close, Plus, Edit, Delete, Download, Upload, Check, Switch, MoreFilled, ArrowDown } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import request from '../../utils/request'
import companyService from '../../api/company'
import * as XLSX from 'xlsx'
import html2pdf from 'html2pdf.js'
import { useSystemSettingsStore } from '../../store/systemSettings'
import { useAuthStore } from '../../store/auth'

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

// 搜索表单对象
let searchForm = ref<Partial<CompanyUser>>({ companyName: '', status: null, companyTag: [] })
// 列表展示数据
let tableData = ref<CompanyUser[]>([])
// 加载状态
let loading = ref(false)
// 分页数据
let pagination = ref<PaginationState>({ currentPage: 1, pageSize: 10, total: 0 })
// 表格引用
let companyTable = ref<InstanceType<typeof ElTable> | null>(null)
// 多选数据
let selectedRows = ref<CompanyUser[]>([])

// 重置密码对话框控制
let resetPasswordDialogVisible = ref(false)
let resetPasswordCompanyId = ref<number | null>(null)

// 查看详情对话框
let viewDialogVisible = ref(false)
let currentCompany = ref<CompanyUser | null>(null)

// 新增/编辑对话框
let formDialogVisible = ref(false)
let formDialogTitle = ref('新增企业')
let isEdit = ref(false)
let formRef = ref<InstanceType<typeof ElForm> | null>(null)
let companyForm = ref<Partial<CompanyUser>>({
  id: null,
  companyName: '',
  contactPerson: '',
  contactPhone: '',
  contactEmail: '',
  address: '',
  introduction: '',
  companyTag: '',
  status: 0
})

// 表单验证规则
const formRules = {
  companyName: [createRequiredValidator('请输入企业名称')],
  contactPerson: [createRequiredValidator('请输入联系人')],
  contactPhone: createPhoneValidator('请输入 11 位中国大陆手机号'),
  contactEmail: createEmailValidator('请输入有效的邮箱地址，如 example@email.com'),
  address: [createRequiredValidator('请输入企业地址')]
}


// 分页查询函数
const queryPage = async (): Promise<void> => {
  loading.value = true
  try {
    let params = {
      ...searchForm.value,
      page: pagination.value.currentPage,
      pageSize: pagination.value.pageSize
    }
    
    logger.log('查询参数 - 原始:', searchForm.value)
    
    if (params.status !== null && params.status !== undefined && params.status !== '') {
      params.status = parseInt(params.status)
    }
    
    if (params.companyTag && Array.isArray(params.companyTag) && params.companyTag.length > 0) {
      params.companyTag = params.companyTag.join(',')
    } else {
      delete params.companyTag
    }
    
    logger.log('查询参数 - 处理后:', params)
    
    const response = await companyService.getCompanies(params)
    if (response && response.data && response.data.code === 200 && response.data.data) {
      const dataList = response.data.data.rows || []
      const totalCount = response.data.data.total || 0
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
const handleSelectionChange = (selection: CompanyUser[]): void => {
  selectedRows.value = selection
}

// 处理图片URL
const getImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('data:')) return url
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  if (url.startsWith('/api/')) return url
  if (url.startsWith('/')) return '/api' + url
  return '/api/' + url
}

// 处理多张图片URL
const getImageUrls = (urls) => {
  if (!urls) return []
  const urlList = urls.split(',').map(url => url.trim()).filter(url => url)
  return urlList.map(url => getImageUrl(url))
}

// 查看企业详情
const viewCompanyDetail = async (id: string): Promise<void> => {
  try {
    const response = await request.get(`/admin/companies/${id}`)
    const result = response.data
    if (result && result.code === 200 && result.data) {
      currentCompany.value = result.data
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
    id: null, companyName: '', contactPerson: '', contactPhone: '',
    contactEmail: '', address: '', introduction: '', status: 1
  }
  formDialogVisible.value = true
}

// 显示编辑对话框
const showEditDialog = (row: CompanyUser): void => {
  isEdit.value = true
  formDialogTitle.value = '编辑企业'
  companyForm.value = { ...row }
  formDialogVisible.value = true
}

// 添加标签到表单
const addTagToForm = (tag: string): void => {
  if (companyForm.value.companyTag) {
    const tags = companyForm.value.companyTag.split(',').map(t => t.trim()).filter(t => t)
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
        if (isEdit.value) {
          await companyService.updateCompany(companyForm.value)
          ElMessage.success('更新成功')
        } else {
          await companyService.addCompany(companyForm.value)
          ElMessage.success('添加成功')
        }
        formDialogVisible.value = false
        queryPage()
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
const deleteCompany = (id: string): void => {
  ElMessageBox.confirm('确定要删除该企业吗？', '确认删除', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    try {
      await companyService.deleteCompany(id)
      ElMessage.success('删除成功')
      queryPage()
    } catch (error) {
      ElMessage.error('删除失败: ' + (error.message || '未知错误'))
    }
  }).catch(() => {})
}

// 切换企业账户状态（启用/禁用）
const handleToggleStatus = async (id: number, currentStatus: number | string) => {
  const statusNum = Number(currentStatus)
  const newStatus = statusNum === 1 ? 0 : 1
  const statusText = newStatus === 1 ? '启用' : '禁用'

  try {
    await ElMessageBox.confirm(`确定要${statusText}该企业账户吗？`, '确认操作', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const response = await companyService.updateCompanyStatus(id, newStatus)

    if (response && response.data.code === 200) {
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
      const ids = selectedRows.value.map(row => row.id)
      await companyService.batchDeleteCompanies(ids)
      ElMessage.success('批量删除成功')
      queryPage()
    } catch (error) {
      ElMessage.error('批量删除失败: ' + (error.message || '未知错误'))
    }
  }).catch(() => {})
}

// 导出Excel
const exportToExcel = async (): Promise<void> => {
  try {
    ElMessage({ message: '正在准备导出数据...', type: 'info' })
    
    const params = {
      companyName: searchForm.value.companyName,
      status: null
    }
    
    const response = await companyService.exportCompanies(params)
    
    const blob = new Blob([response.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
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

const importFileRef = ref<HTMLElement | null>(null)
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
    const result = response.data || response
    
    if (result && (result.code === 200 || result.code === undefined)) {
      const importResult = result.data || {}
      const successCount = importResult.successCount || 0
      const failCount = importResult.failCount || 0
      const failList = importResult.failList || []
      
      let message = `导入完成，成功: ${successCount} 条，失败: ${failCount} 条`
      
      if (failList.length > 0) {
        let errorDetails = '\n失败详情:\n'
        failList.forEach((fail, index) => {
          errorDetails += `${index + 1}. 第${fail.rowNum}行: ${fail.errorMsg || '未知错误'}\n`
        })
        
        ElMessageBox.alert(message + errorDetails, '导入结果', {
          confirmButtonText: '确定',
          type: failCount > 0 ? 'warning' : 'success',
          dangerouslyUseHTMLString: false
        })
      } else {
        ElMessage.success(message)
      }
      
      closeImportDialog()
      queryPage()
    } else {
      ElMessage.error(result?.msg || '导入失败')
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
    const response = await request.get('/admin/companies/import-template', {
      responseType: 'blob'
    })

    // 创建下载链接
    const url = window.URL.createObjectURL(new Blob([response.data]))
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
    if (response.data && response.data.code === 200) {
      ElMessage.success('批量更新标签成功')
      queryPage()
    } else {
      ElMessage.error(response.data?.msg || '批量更新标签失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      logger.error('批量更新标签失败:', error)
      ElMessage.error('批量更新标签失败: ' + (error.message || '未知错误'))
    }
  }
}


// 显示重置密码对话框
const showResetPasswordDialog = (row: CompanyUser): void => {
  resetPasswordCompanyId.value = row.id
  resetPasswordDialogVisible.value = true
}

// 重置密码
const resetPassword = async (): Promise<void> => {
  try {
    const response = await companyService.resetCompanyPassword(resetPasswordCompanyId.value!, '123456')
    const result = response.data || response

    if (result && (result.code === 200 || result.code === undefined)) {
      ElMessage.success('密码重置成功，新密码为：123456')
      resetPasswordDialogVisible.value = false
    } else {
      ElMessage.error('密码重置失败：' + (result?.msg || '未知错误'))
    }
  } catch (error) {
    logger.error('重置密码失败:', error)
    ElMessage.error('重置密码失败：' + (error.message || '未知错误'))
  }
}


const formatStatus = (row: unknown, _column: unknown, cellValue: number): string => {
  if (row && row.recallStatus === 1) {
    return '待审核'
  }
  switch (cellValue) {
    case 0: return '待审核'
    case 1: return '审核通过'
    case 2: return '审核拒绝'
    case 3: return '已撤销'
    default: return '未知状态'
  }
}

// 格式化状态标签类型
const getStatusTagType = (status: number, recallStatus: number): string => {
  if (recallStatus === 1) {
    return 'warning'
  }
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
    '双向选择阶段': 'primary',
    '学生自主联系': 'success',
    '接受兜底': 'warning',
    '国家级': 'danger',
    '省级': 'warning'
  }
  return tagMap[tag.trim()] || 'info'
}

// 跳转到撤回申请审核页面
const goToRecallAudit = (company: CompanyUser): void => {
  router.push({
    path: '/admin/recall-audit',
    query: {
      companyId: company.id,
      companyName: company.companyName,
      contactPerson: company.contactPerson,
      contactPhone: company.contactPhone
    }
  })
}

// 下载审核材料
const downloadMaterials = (company: CompanyUser): void => {
  const materials = {
    '企业名称': company.companyName,
    '联系人': company.contactPerson,
    '联系电话': company.contactPhone,
    '联系邮箱': company.contactEmail,
    '企业地址': company.address,
    '企业介绍': company.introduction || '',
    '申请时间': formatDate(null, null, company.applyTime),
    '审核状态': formatStatus(null, null, company.auditStatus),
    '审核时间': formatDate(null, null, company.auditTime)
  }
  
  const businessLicenseUrl = company.businessLicense
  const businessLicenseImg = businessLicenseUrl ? `<img src="${getImageUrl(businessLicenseUrl)}" style="max-width: 100%; max-height: 300px; display: block; margin: 10px 0;" />` : '未上传'
  
  const legalIdCardUrls = getImageUrls(company.legalIdCard)
  const legalIdCardImgs = legalIdCardUrls.length > 0 
    ? legalIdCardUrls.map(url => `<img src="${url}" style="max-width: 100%; max-height: 300px; display: block; margin: 10px 0;" />`).join('')
    : '未上传'
  
  const htmlContent = `
    <div style="font-family: Arial, sans-serif; padding: 40px; max-width: 800px; margin: 0 auto;">
      <h1 style="text-align: center; color: #409EFF; margin-bottom: 30px;">企业审核材料</h1>
      <table style="width: 100%; border-collapse: collapse; margin-bottom: 20px;">
        <tr>
          <td style="padding: 12px; border: 1px solid #ddd; background-color: #f5f7fa; font-weight: bold; width: 30%;">企业名称</td>
          <td style="padding: 12px; border: 1px solid #ddd; width: 70%;">${materials['企业名称']}</td>
        </tr>
        <tr>
          <td style="padding: 12px; border: 1px solid #ddd; background-color: #f5f7fa; font-weight: bold;">联系人</td>
          <td style="padding: 12px; border: 1px solid #ddd;">${materials['联系人']}</td>
        </tr>
        <tr>
          <td style="padding: 12px; border: 1px solid #ddd; background-color: #f5f7fa; font-weight: bold;">联系电话</td>
          <td style="padding: 12px; border: 1px solid #ddd;">${materials['联系电话']}</td>
        </tr>
        <tr>
          <td style="padding: 12px; border: 1px solid #ddd; background-color: #f5f7fa; font-weight: bold;">联系邮箱</td>
          <td style="padding: 12px; border: 1px solid #ddd;">${materials['联系邮箱']}</td>
        </tr>
        <tr>
          <td style="padding: 12px; border: 1px solid #ddd; background-color: #f5f7fa; font-weight: bold;">企业地址</td>
          <td style="padding: 12px; border: 1px solid #ddd;">${materials['企业地址']}</td>
        </tr>
        <tr>
          <td style="padding: 12px; border: 1px solid #ddd; background-color: #f5f7fa; font-weight: bold;">企业介绍</td>
          <td style="padding: 12px; border: 1px solid #ddd;">${materials['企业介绍']}</td>
        </tr>
        <tr>
          <td style="padding: 12px; border: 1px solid #ddd; background-color: #f5f7fa; font-weight: bold;">营业执照</td>
          <td style="padding: 12px; border: 1px solid #ddd;">${businessLicenseImg}</td>
        </tr>
        <tr>
          <td style="padding: 12px; border: 1px solid #ddd; background-color: #f5f7fa; font-weight: bold;">法人身份证</td>
          <td style="padding: 12px; border: 1px solid #ddd;">${legalIdCardImgs}</td>
        </tr>
        <tr>
          <td style="padding: 12px; border: 1px solid #ddd; background-color: #f5f7fa; font-weight: bold;">申请时间</td>
          <td style="padding: 12px; border: 1px solid #ddd;">${materials['申请时间']}</td>
        </tr>
        <tr>
          <td style="padding: 12px; border: 1px solid #ddd; background-color: #f5f7fa; font-weight: bold;">审核状态</td>
          <td style="padding: 12px; border: 1px solid #ddd;">${materials['审核状态']}</td>
        </tr>
        <tr>
          <td style="padding: 12px; border: 1px solid #ddd; background-color: #f5f7fa; font-weight: bold;">审核时间</td>
          <td style="padding: 12px; border: 1px solid #ddd;">${materials['审核时间']}</td>
        </tr>
      </table>
      <div style="text-align: center; margin-top: 40px; color: #909399; font-size: 12px;">
        生成时间：${new Date().toLocaleString('zh-CN')}
      </div>
    </div>
  `
  
  const element = document.createElement('div')
  element.innerHTML = htmlContent
  document.body.appendChild(element)
  
  const opt = {
    margin: 10,
    filename: `${company.companyName}_审核材料.pdf`,
    image: { type: 'jpeg', quality: 0.98 },
    html2canvas: { scale: 2, useCORS: true },
    jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' }
  }
  
  html2pdf().set(opt).from(element).save().then(() => {
    document.body.removeChild(element)
    ElMessage.success('下载成功')
  }).catch(err => {
    document.body.removeChild(element)
    ElMessage.error('下载失败: ' + err.message)
  })
}

// 页面加载时执行
onMounted(async () => {
  await queryPage()
})
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
              <el-option label="学生自主联系" value="学生自主联系"></el-option>
              <el-option label="接受兜底" value="接受兜底"></el-option>
              <el-option label="双向选择阶段" value="双向选择阶段"></el-option>
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
          <el-button v-if="authStore.hasPermission('user:company:add')" type="primary" @click="showAddDialog" class="action-btn primary">
            <el-icon><Plus /></el-icon>&nbsp;新增企业
          </el-button>
          <el-button v-if="authStore.hasPermission('user:company:delete')" type="danger" @click="batchDelete" class="action-btn danger">
            <el-icon><Delete /></el-icon>&nbsp;批量删除
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
          <el-button v-if="authStore.hasPermission('user:company:view')" type="success" @click="exportToExcel" class="action-btn success">
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
        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.status, scope.row.recallStatus)" size="small" class="status-tag">
              {{ formatStatus(scope.row, null, scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="companyTag" label="标签" width="150" align="center">
          <template #default="scope">
            <div v-if="scope.row.companyTag" class="tag-container">
              <el-tag 
                v-for="(tag, index) in scope.row.companyTag.split(',')" 
                :key="index" 
                :type="getTagType(tag)" 
                size="small" 
                class="company-tag"
              >
                {{ tag }}
              </el-tag>
            </div>
            <span v-else class="no-tag">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="contactPerson" label="联系人" width="120" align="center" />
        <el-table-column prop="contactPhone" label="联系电话" width="150" align="center" />
        <el-table-column prop="contactEmail" label="联系邮箱" min-width="180" align="center" />
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
              <el-dropdown trigger="click" v-if="authStore.hasPermission('user:company:delete') || authStore.hasPermission('user:company:reset') || authStore.hasPermission('user:company:audit') || (authStore.hasPermission('company:recall:view') && scope.row.recallStatus === 1)">
                <el-button size="small" class="table-btn more">
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="authStore.hasPermission('user:company:delete')" @click="deleteCompany(scope.row.id)" divided class="danger-item">
                      <el-icon><Delete /></el-icon> 删除
                    </el-dropdown-item>
                    <el-dropdown-item v-if="authStore.hasPermission('user:company:reset')" @click="showResetPasswordDialog(scope.row)">
                      <el-icon><Refresh /></el-icon> 重置密码
                    </el-dropdown-item>
                    <el-dropdown-item v-if="authStore.hasPermission('user:company:audit')" @click="downloadMaterials(scope.row)">
                      <el-icon><Download /></el-icon> 下载审核材料
                    </el-dropdown-item>
                    <el-dropdown-item v-if="authStore.hasPermission('company:recall:view') && scope.row.recallStatus === 1" @click="goToRecallAudit(scope.row)">
                      <el-icon><ArrowRight /></el-icon> 审核撤回申请
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
        <el-form-item label="联系人" prop="contactPerson">
          <el-input v-model="companyForm.contactPerson" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="companyForm.contactPhone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="联系邮箱" prop="contactEmail">
          <el-input v-model="companyForm.contactEmail" placeholder="请输入联系邮箱" />
        </el-form-item>
        <el-form-item label="企业地址" prop="address">
          <el-input v-model="companyForm.address" placeholder="请输入企业地址" />
        </el-form-item>
        <el-form-item label="企业介绍">
          <el-input v-model="companyForm.introduction" type="textarea" :rows="4" placeholder="请输入企业介绍" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="companyForm.companyTag" placeholder="请输入标签，多个标签用逗号分隔，例如：双向选择阶段，接受兜底" />
          <div class="common-tags" style="margin-top: 8px;">
            <el-tag
              v-for="tag in ['双向选择阶段', '学生自主联系', '接受兜底', '国家级', '省级']"
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
          <el-descriptions-item label="联系人">{{ currentCompany.contactPerson }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ currentCompany.contactPhone }}</el-descriptions-item>
          <el-descriptions-item label="联系邮箱" :span="2">{{ currentCompany.contactEmail }}</el-descriptions-item>
          <el-descriptions-item label="企业地址" :span="2">{{ currentCompany.address }}</el-descriptions-item>
          <el-descriptions-item label="企业介绍" :span="2">
            <div class="company-intro">{{ currentCompany.introduction || '暂无介绍' }}</div>
          </el-descriptions-item>
          <el-descriptions-item label="标签" :span="2">
            <div v-if="currentCompany.companyTag" class="tag-container">
              <el-tag 
                v-for="(tag, index) in currentCompany.companyTag.split(',')" 
                :key="index" 
                :type="getTagType(tag)" 
                size="small" 
                class="company-tag"
              >
                {{ tag }}
              </el-tag>
            </div>
            <span v-else class="no-tag">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="营业执照" :span="2">
            <div v-if="currentCompany.businessLicense" class="image-preview">
              <el-image
                :src="getImageUrl(currentCompany.businessLicense)"
                :preview-src-list="[getImageUrl(currentCompany.businessLicense)]"
                fit="cover"
                class="material-image"
                :preview-teleported="true"
              />
            </div>
            <div v-else class="material-content">未上传</div>
          </el-descriptions-item>
          <el-descriptions-item label="法人身份证" :span="2">
            <div v-if="currentCompany.legalIdCard" class="image-preview">
              <el-image
                v-for="(url, index) in getImageUrls(currentCompany.legalIdCard)"
                :key="index"
                :src="url"
                :preview-src-list="getImageUrls(currentCompany.legalIdCard)"
                fit="cover"
                class="material-image"
                :preview-teleported="true"
                :initial-index="index"
              />
            </div>
            <div v-else class="material-content">未上传</div>
          </el-descriptions-item>
          <el-descriptions-item label="申请时间" :span="2">{{ formatDate(null, null, currentCompany.applyTime) }}</el-descriptions-item>
          <el-descriptions-item label="审核状态">
            <el-tag :type="getStatusTagType(currentCompany.status, currentCompany.recallStatus)" size="small">
              {{ formatStatus(currentCompany, null, currentCompany.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ formatDate(null, null, currentCompany.auditTime) }}</el-descriptions-item>
          <el-descriptions-item label="企业ID">{{ currentCompany.id }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDate(null, null, currentCompany.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatDate(null, null, currentCompany.updateTime) }}</el-descriptions-item>
        </el-descriptions>
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
        <p>密码将被重置为：<span class="password-text">123456</span></p>
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
          提示：请先下载导入模板，按照模板格式填写数据后再导入
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
