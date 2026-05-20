<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VideoPause, Message, MagicStick } from '@element-plus/icons-vue'
import { usePositionStore } from '../../store/position'
import { useAuthStore } from '../../store/auth'
import positionApi from '../../api/PositionService'
import studentApi from '../../api/StudentUserService'
import PositionCategoryService from '../../api/positionCategory'
import { EluiChinaAreaDht } from 'elui-china-area-dht'

console.log('[RecruitmentManagement] 组件模块加载')

const chinaData = new EluiChinaAreaDht.ChinaArea().chinaAreaflat

const positionStore = usePositionStore()
const authStore = useAuthStore()

// 从 auth store 获取当前企业用户 ID
const companyId = computed(() => {
  if (authStore.user?.id) {
    return parseInt(authStore.user.id)
  }
  // 当无法获取企业 ID 时，尝试从 localStorage 获取
  const storedCompanyId = localStorage.getItem('company_companyId_COMPANY')
  if (storedCompanyId) {
    return parseInt(storedCompanyId)
  }
  // 如果仍然无法获取，返回 null 并显示错误提示
  ElMessage.error('未获取到当前登录企业 ID，请重新登录')
  return null
})

const dialogVisible = ref(false)
const dialogTitle = ref('发布岗位')
const dialogType = ref('create')
// 对话框操作加载状态
const loading = ref(false)
// AI生成岗位描述加载状态
const generatingDescription = ref(false)

// 面试信息对话框
const interviewDialogVisible = ref(false)
const interviewDialogTitle = ref('填写面试信息')
const interviewLoading = ref(false)
const interviewForm = ref({
  interviewTime: '',
  interviewLocation: '',
  interviewMethod: '线下',
  remark: ''
})

const interviewMethodOptions = [
  { label: '线下', value: '线下' },
  { label: '线上视频', value: '线上视频' },
  { label: '电话面试', value: '电话面试' }
]

const positionForm = ref({
  id: null,
  positionName: '',
  department: '',
  positionType: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  salaryMin: '',
  salaryMax: '',
  description: '',
  requirements: '',
  plannedRecruit: 1,
  recruitedCount: 0,
  remainingQuota: 0,
  status: 'active',
  addressCode: [],
  internshipStartDate: '',
  internshipEndDate: '',
  interviewTime: '',
  interviewLocation: '',
  interviewMethod: '线下',
  interviewRemark: ''
})

const addressNameToCode = computed(() => {
  const map = {}
  Object.entries(chinaData).forEach(([code, item]) => {
    if (item.label) {
      map[item.label] = code
    }
  })
  return map
})

const tableData = computed(() => positionStore.positions || [])

const currentPage = ref(1)
const pageSize = ref(10)

const paginatedTableData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredTableData.value.slice(start, end)
})

const searchResults = ref([])
const hasSearched = ref(false)

const formatWorkLocation = (item) => {
  const parts = []
  if (item.province && item.province !== 'None') parts.push(item.province)
  if (item.city && item.city !== 'None') parts.push(item.city)
  if (item.district && item.district !== 'None') parts.push(item.district)
  if (item.detailAddress) parts.push(item.detailAddress)
  return parts.join('')
}

const formatDate = (date) => {
  if (!date) return ''
  const d = new Date(date)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const filteredTableData = computed(() => {
  if (hasSearched.value) {
    return [...searchResults.value]
  }
  return [...tableData.value]
})

const searchForm = ref({
  keyword: '',
  status: '',
  department: ''
})

// 岗位类别选项（从管理员端岗位管理获取）
const categoryOptions = ref<{ label: string; value: string }[]>([])
const categoryList = ref<{ id: number; name: string }[]>([]) // 存储完整类别信息（含ID）
const loadingCategories = ref(false)

// 按类别 ID 存储岗位名称列表（用于发布岗位弹窗的岗位名称下拉）
const categoryPositionsMap = ref(new Map<number, { label: string; value: string }[]>())
const loadingCategoryPositions = ref(false)

// 获取岗位类别列表（用于所属部门下拉框）
const fetchCategories = async () => {
  loadingCategories.value = true
  try {
    const result = await PositionCategoryService.getAllCategoriesPublic()
    console.log('[fetchCategories] 获取岗位类别返回:', result)
    // 公开接口直接返回 Result.success(categories)，结构是 { code: 200, data: [...] }
    if (result && result.code === 200) {
      const categories = Array.isArray(result.data) ? result.data : []
      console.log('[fetchCategories] 岗位类别数据:', categories)
      categoryList.value = categories
      categoryOptions.value = categories.map(cat => ({
        label: cat.name,
        value: cat.name  // 使用 name 作为 value，因为岗位的 department 字段存储的也是类别名称
      }))
      console.log('[fetchCategories] categoryOptions:', categoryOptions.value)
      console.log('[fetchCategories] categoryList:', categoryList.value)
    }
  } catch (error) {
    console.error('获取岗位类别失败:', error)
  } finally {
    loadingCategories.value = false
  }
}

// 根据选中的部门筛选岗位名称选项
const positionNameOptions = computed(() => {
  const selectedDepartment = positionForm.value.department
  console.log('[positionNameOptions] 计算属性被调用!')
  console.log('[positionNameOptions] selectedDepartment:', selectedDepartment)

  // 如果没有选择部门，返回空
  if (!selectedDepartment) {
    console.log('[positionNameOptions] 未选择部门，返回空数组')
    return []
  }

  // 根据选中的部门名称找到对应的 categoryId
  const selectedCategory = categoryList.value.find(cat => cat.name === selectedDepartment)
  const selectedCategoryId = selectedCategory ? selectedCategory.id : null
  console.log('[positionNameOptions] selectedCategory:', JSON.stringify(selectedCategory))

  // 优先从按类别缓存的岗位列表中获取（通过 API 获取的该类别下的岗位）
  if (selectedCategoryId && categoryPositionsMap.value.has(selectedCategoryId)) {
    const positions = categoryPositionsMap.value.get(selectedCategoryId) || []
    console.log('[positionNameOptions] 从类别缓存获取岗位:', positions)
    return positions
  }

  // 降级方案：从当前企业已有的岗位中过滤
  const uniquePositions = new Map()

  if (positionStore.positions && Array.isArray(positionStore.positions)) {
    positionStore.positions.forEach(pos => {
      if (pos.positionName) {
        // 通过 categoryId 匹配
        const categoryIdMatch = selectedCategoryId !== null && pos.categoryId === selectedCategoryId
        console.log('[positionNameOptions] 检查岗位:', pos.positionName, 'categoryId:', pos.categoryId, 'categoryIdMatch:', categoryIdMatch)

        if (categoryIdMatch) {
          uniquePositions.set(pos.positionName, { label: pos.positionName, value: pos.positionName })
        }
      }
    })
  }
  const result = Array.from(uniquePositions.values())
  console.log('[positionNameOptions] 结果:', result)
  return result
})

// 当部门（类别）变化时，获取该类别下的岗位列表
const handleDepartmentChange = async (selectedDepartment) => {
  console.log('[handleDepartmentChange] 部门变化:', selectedDepartment)
  if (!selectedDepartment) {
    return
  }

  // 找到选中部门对应的类别 ID
  const selectedCategory = categoryList.value.find(cat => cat.name === selectedDepartment)
  if (!selectedCategory) {
    console.log('[handleDepartmentChange] 未找到对应类别')
    return
  }

  const categoryId = selectedCategory.id
  console.log('[handleDepartmentChange] 类别ID:', categoryId)

  // 如果已经缓存过该类别的岗位，不再重复获取
  if (categoryPositionsMap.value.has(categoryId)) {
    console.log('[handleDepartmentChange] 类别岗位已缓存')
    return
  }

  loadingCategoryPositions.value = true
  try {
    const result = await PositionCategoryService.getPositionsByCategoryIdPublic(categoryId)
    console.log('[handleDepartmentChange] 获取类别岗位返回:', result)

    if (result && result.code === 200) {
      const positions = Array.isArray(result.data) ? result.data : []
      // 提取岗位名称并去重
      const uniquePositionNames = [...new Set(positions.map(p => p.positionName).filter(Boolean))]
      const positionOptions = uniquePositionNames.map(name => ({ label: name, value: name }))

      categoryPositionsMap.value.set(categoryId, positionOptions)
      console.log('[handleDepartmentChange] 类别岗位缓存:', positionOptions)
    }
  } catch (error) {
    console.error('获取类别岗位列表失败:', error)
  } finally {
    loadingCategoryPositions.value = false
  }
}

// 部门选项（兼容原有逻辑，支持输入过滤）
const departmentOptions = computed(() => categoryOptions.value)

const positionTypeOptions = [
  { label: '全职', value: '全职' },
  { label: '兼职', value: '兼职' },
  { label: '实习', value: '实习' }
]

const statusMap = {
  active: '招聘中',
  paused: '已暂停'
}

const statusTypeMap = {
  active: 'success',
  paused: 'warning'
}

const getRowClassName = ({ row }) => {
  if (row.status === 'paused') {
    return 'paused-row'
  }
  return ''
}

const getRowStyle = ({ row }) => {
  if (row.status === 'paused') {
    return {
      backgroundColor: '#ffebee',
      color: '#909399'
    }
  }
  return {}
}

onMounted(() => {
  console.log('页面加载 - companyId:', companyId.value)
  if (!companyId.value) {
    ElMessage.error('未获取到企业 ID，无法加载数据')
    return
  }
  console.log('开始获取岗位数据和类别')
  positionStore.fetchPositions(companyId.value).then(() => {
    console.log('岗位数据获取完成:', positionStore.positions)
  })
  positionStore.fetchInternshipStatuses(companyId.value)
  fetchCategories().then(() => {
    console.log('类别数据获取完成:', categoryOptions.value)
  })
})

const handleSearch = async () => {
  try {
    hasSearched.value = true
    const params = {}

    if (searchForm.value.keyword) {
      params.positionName = searchForm.value.keyword
    }

    if (searchForm.value.department) {
      params.department = searchForm.value.department
    }

    if (searchForm.value.status) {
      params.status = searchForm.value.status
    }

    const response = await positionApi.getPositionsByCompanyId(companyId.value, params)
    if (response.code === 200) {
      searchResults.value = response.data.map(item => ({
        id: item.id,
        positionName: item.positionName,
        department: item.department || '',
        workLocation: formatWorkLocation(item),
        salaryMin: item.salaryMin || null,
        salaryMax: item.salaryMax || null,
        plannedRecruit: item.plannedRecruit || 0,
        recruitedCount: item.recruitedCount || 0,
        remainingQuota: item.remainingQuota || 0,
        viewCount: item.viewCount || 0,
        status: item.status || 'active',
        publishDate: formatDate(item.publishDate || item.createTime),
        province: item.province && item.province !== 'None' ? item.province : '',
        city: item.city && item.city !== 'None' ? item.city : '',
        district: item.district && item.district !== 'None' ? item.district : '',
        detailAddress: item.detailAddress || '',
        description: item.description || '',
        requirements: item.requirements || '',
        positionType: item.positionType || ''
      }))
      currentPage.value = 1
      ElMessage.success('搜索成功')
    } else {
      ElMessage.error('搜索失败')
    }
  } catch (error) {
    console.error('搜索失败:', error)
    ElMessage.error('搜索失败')
  }
}

const handleReset = () => {
  searchForm.value = {
    keyword: '',
    status: '',
    department: ''
  }
  searchResults.value = []
  hasSearched.value = false
  currentPage.value = 1
  ElMessage.info('已重置搜索条件')
}

const handlePageChange = (page) => {
  currentPage.value = page
}

const handlePublish = () => {
  dialogTitle.value = '发布岗位'
  dialogType.value = 'create'

  // 清除类别岗位缓存，确保每次打开发布弹窗时获取最新数据
  categoryPositionsMap.value.clear()

  // 从 localStorage 读取面试信息模板
  let interviewTime = ''
  let interviewLocation = ''
  let interviewMethod = '线下'
  let remark = ''
  const savedTemplate = localStorage.getItem('interviewTemplate')
  if (savedTemplate) {
    try {
      const template = JSON.parse(savedTemplate)
      interviewTime = template.interviewTime || ''
      interviewLocation = template.interviewLocation || ''
      interviewMethod = template.interviewMethod || '线下'
      remark = template.remark || ''
    } catch (e) {
      console.error('读取面试信息模板失败:', e)
    }
  }

  positionForm.value = {
    id: null,
    positionName: '',
    department: '',
    positionType: '',
    province: '',
    city: '',
    district: '',
    detailAddress: '',
    salaryMin: '',
    salaryMax: '',
    description: '',
    requirements: '',
    plannedRecruit: 1,
    status: 'active',
    addressCode: [],
    internshipStartDate: '',
    internshipEndDate: '',
    interviewTime,
    interviewLocation,
    interviewMethod,
    interviewRemark: remark
  }
  dialogVisible.value = true
}

// 面试信息模板相关方法
const handleInterviewInfo = () => {
  // 从 localStorage 读取已保存的面试信息模板
  const savedTemplate = localStorage.getItem('interviewTemplate')
  if (savedTemplate) {
    try {
      const template = JSON.parse(savedTemplate)
      interviewForm.value = {
        interviewTime: template.interviewTime || '',
        interviewLocation: template.interviewLocation || '',
        interviewMethod: template.interviewMethod || '线下',
        remark: template.remark || ''
      }
    } catch {
      interviewForm.value = {
        interviewTime: '',
        interviewLocation: '',
        interviewMethod: '线下',
        remark: ''
      }
    }
  } else {
    interviewForm.value = {
      interviewTime: '',
      interviewLocation: '',
      interviewMethod: '线下',
      remark: ''
    }
  }
  interviewDialogVisible.value = true
}

const handleInterviewSubmit = async () => {
  // 保存到 localStorage
  localStorage.setItem('interviewTemplate', JSON.stringify(interviewForm.value))
  ElMessage.success('面试信息已保存，发布岗位时将自动填充')
  interviewDialogVisible.value = false
}

const handleInterviewCancel = () => {
  interviewDialogVisible.value = false
}

const handleAddressChange = (e) => {
  positionForm.value.addressCode = e
  if (e && e.length > 0) {
    const provinceCode = e[0]
    const cityCode = e[1]
    const districtCode = e[2]

    positionForm.value.province = chinaData[provinceCode]?.label || ''
    positionForm.value.city = chinaData[cityCode]?.label || ''
    positionForm.value.district = chinaData[districtCode]?.label || ''
  } else {
    positionForm.value.province = ''
    positionForm.value.city = ''
    positionForm.value.district = ''
  }
}

// 部门下拉框过滤方法（支持输入匹配）
const handleDepartmentFilter = (query) => {
  // el-select 的 filterable 会自动处理过滤，这里可以做一些额外逻辑
  // 如果需要自定义过滤逻辑，可以在这里处理
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑岗位'
  dialogType.value = 'edit'
  
  const addressCodes = []
  if (row.province && row.province !== 'None') {
    const provinceCode = Object.keys(chinaData).find(code => chinaData[code].label === row.province)
    if (provinceCode) {
      addressCodes.push(provinceCode)
      if (row.city && row.city !== 'None') {
        const cityCode = Object.keys(chinaData).find(code => chinaData[code].label === row.city && chinaData[code].parent === provinceCode)
        if (cityCode) {
          addressCodes.push(cityCode)
          if (row.district && row.district !== 'None') {
            const districtCode = Object.keys(chinaData).find(code => chinaData[code].label === row.district && chinaData[code].parent === cityCode)
            if (districtCode) {
              addressCodes.push(districtCode)
            }
          }
        }
      }
    }
  }
  
  positionForm.value = {
    ...row,
    salaryMin: row.salaryMin || '',
    salaryMax: row.salaryMax || '',
    province: row.province && row.province !== 'None' ? row.province : '',
    city: row.city && row.city !== 'None' ? row.city : '',
    district: row.district && row.district !== 'None' ? row.district : '',
    detailAddress: row.detailAddress || '',
    plannedRecruit: row.plannedRecruit || 1,
    addressCode: addressCodes,
    interviewTime: row.interviewTime || '',
    interviewLocation: row.interviewLocation || '',
    interviewMethod: row.interviewMethod || '线下',
    interviewRemark: row.interviewRemark || ''
  }
  dialogVisible.value = true
}

const handlePause = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要暂停"${row.positionName}"的招聘吗？`,
      '暂停招聘',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
        center: true
      }
    )
    const success = await positionStore.pausePosition(companyId.value, row.id)
    if (success) {
      ElMessage.success('已暂停招聘')
    } else {
      ElMessage.error('操作失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleResume = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要恢复"${row.positionName}"的招聘吗？`,
      '恢复招聘',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
        center: true
      }
    )
    const success = await positionStore.resumePosition(companyId.value, row.id)
    if (success) {
      ElMessage.success('已恢复招聘')
    } else {
      ElMessage.error('操作失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除"${row.positionName}"吗？此操作不可恢复！`,
      '删除岗位',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'error',
        center: true
      }
    )
    const success = await positionStore.deletePosition(companyId.value, row.id)
    if (success) {
      ElMessage.success('已删除岗位')
    } else {
      ElMessage.error('操作失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleSave = async () => {
  if (!positionForm.value.positionName) {
    ElMessage.warning('请输入岗位名称')
    return
  }
  
  loading.value = true
  try {
    const positionData = {
      positionName: positionForm.value.positionName,
      department: positionForm.value.department,
      positionType: positionForm.value.positionType,
      province: positionForm.value.province,
      city: positionForm.value.city,
      district: positionForm.value.district,
      detailAddress: positionForm.value.detailAddress,
      salaryMin: positionForm.value.salaryMin,
      salaryMax: positionForm.value.salaryMax,
      description: positionForm.value.description,
      requirements: positionForm.value.requirements,
      plannedRecruit: positionForm.value.plannedRecruit,
      status: positionForm.value.status,
      internshipStartDate: positionForm.value.internshipStartDate || null,
      internshipEndDate: positionForm.value.internshipEndDate || null,
      interviewTime: positionForm.value.interviewTime || null,
      interviewLocation: positionForm.value.interviewLocation || null,
      interviewMethod: positionForm.value.interviewMethod || null,
      interviewRemark: positionForm.value.interviewRemark || null
    }
    
    let success
    if (dialogType.value === 'create') {
      success = await positionStore.addPosition(companyId.value, positionData)
      if (success) {
        ElMessage.success('岗位发布成功')
      } else {
        ElMessage.error('岗位发布失败')
      }
    } else {
      success = await positionStore.updatePosition(companyId.value, positionForm.value.id, positionData)
      if (success) {
        ElMessage.success('岗位更新成功')
      } else {
        ElMessage.error('岗位更新失败')
      }
    }
    
    if (success) {
      dialogVisible.value = false
    }
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    loading.value = false
  }
}

const handleCancel = () => {
  dialogVisible.value = false
}

// AI生成岗位描述
const handleGenerateDescription = async () => {
  if (!positionForm.value.positionName) {
    ElMessage.warning('请先输入岗位名称')
    return
  }

  generatingDescription.value = true
  try {
    const response = await fetch('/api/ai/generate-job-description', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + (authStore.token || '')
      },
      body: JSON.stringify({
        positionName: positionForm.value.positionName,
        department: positionForm.value.department,
        positionType: positionForm.value.positionType
      })
    })

    const res = await response.json()
    if (res.success && res.data) {
      if (res.data.description) {
        positionForm.value.description = res.data.description
      }
      ElMessage.success('AI已生成岗位描述')
    } else {
      ElMessage.error(res.message || '生成失败')
    }
  } catch (error) {
    console.error('生成岗位描述失败:', error)
    ElMessage.error('生成失败，请稍后重试')
  } finally {
    generatingDescription.value = false
  }
}

</script>

<template>
  <div class="recruitment-management">
    <div class="page-header">
      <h2>招聘管理</h2>
      <p>岗位的发布与维护，确认实习学生后岗位招聘人数自动减一</p>
    </div>

    <div class="stats-card">
      <div class="stat-item">
        <div class="stat-label">计划招聘人数</div>
        <div class="stat-value primary">{{ positionStore.totalPlanCount }}</div>
      </div>
      <div class="stat-item">
        <div class="stat-label">已确认人数</div>
        <div class="stat-value success">{{ positionStore.totalConfirmedCount }}</div>
      </div>
      <div class="stat-item">
        <div class="stat-label">岗位缺口</div>
        <div class="stat-value warning">{{ positionStore.totalGap }}</div>
      </div>
      <div class="stat-item">
        <div class="stat-label">在招岗位数</div>
        <div class="stat-value info">{{ positionStore.activePositionCount }}</div>
      </div>
    </div>

    <div class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="关键词">
          <el-input 
            v-model="searchForm.keyword" 
            placeholder="请输入岗位名称" 
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="searchForm.department" placeholder="请选择部门" clearable style="width: 150px">
            <el-option
              v-for="item in departmentOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable style="width: 150px">
            <el-option label="招聘中" value="active" />
            <el-option label="已暂停" value="paused" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="warning" @click="handleInterviewInfo()">
            <el-icon><Message /></el-icon>
            面试信息
          </el-button>
          <el-button type="success" @click="handlePublish">
            <el-icon><Plus /></el-icon>
            发布岗位
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <el-table
        :data="paginatedTableData"
        border
        stripe
        :row-class-name="getRowClassName"
        :row-style="getRowStyle"
        :resizable="false"
        style="width: 100%"
      >
        <el-table-column prop="positionName" label="岗位名称" min-width="120">
          <template #default="{ row }">
            <span v-if="row.status === 'paused'" class="paused-text" style="background-color: #ffebee; color: #909399; text-decoration: line-through; display: inline-block; padding: 8px 12px; border-radius: 4px;">
              <el-icon class="paused-icon" style="color: #f56c6c; margin-right: 6px; font-size: 16px;"><VideoPause /></el-icon>
              {{ row.positionName }}
            </span>
            <span v-else>{{ row.positionName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="所属部门" width="100" />
        <el-table-column label="工作地点" min-width="180">
          <template #default="{ row }">
            <div style="white-space: normal; word-break: break-all; line-height: 1.5;">
              <span v-if="row.province && row.city && row.district">
                {{ row.province }} {{ row.city }} {{ row.district }}
                <span v-if="row.detailAddress">{{ row.detailAddress }}</span>
              </span>
              <span v-else-if="row.province && row.city">
                {{ row.province }} {{ row.city }}
                <span v-if="row.detailAddress">{{ row.detailAddress }}</span>
              </span>
              <span v-else-if="row.detailAddress">{{ row.detailAddress }}</span>
              <span v-else>-</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="薪资范围" width="100" align="center">
          <template #default="{ row }">
            {{ row.salaryMin ? row.salaryMin + '-' + row.salaryMax + 'K' : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="plannedRecruit" label="招聘人数" width="90" align="center" />
        <el-table-column prop="recruitedCount" label="已招人数" width="90" align="center" />
        <el-table-column prop="remainingQuota" label="缺口" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.remainingQuota === 0 ? 'success' : 'warning'" size="small">
              {{ row.remainingQuota }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status]" size="small">
              {{ statusMap[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishDate" label="发布日期" width="110" align="center" />
        <el-table-column prop="viewCount" label="浏览次数" width="90" align="center">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.viewCount || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button 
                v-if="row.status === 'active'"
                type="warning" 
                size="small" 
                @click="handlePause(row)"
              >
                暂停
              </el-button>
              <el-button 
                v-if="row.status === 'paused'"
                type="success" 
                size="small" 
                @click="handleResume(row)"
              >
                恢复
              </el-button>
              <el-button 
                type="primary" 
                size="small" 
                @click="handleEdit(row)"
              >
                编辑
              </el-button>
              <el-button 
                type="danger" 
                size="small" 
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="hasSearched && filteredTableData.length === 0" class="empty-state">
        <el-empty description="未找到匹配的岗位信息" />
      </div>

      <div class="pagination">
        <el-pagination
          background
          layout="total, prev, pager, next, jumper"
          :total="filteredTableData.length"
          :page-size="pageSize"
          :current-page="currentPage"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <el-dialog 
      v-model="dialogVisible" 
      :title="dialogTitle" 
      width="700px"
      :close-on-click-modal="false"
    >
      <el-form :model="positionForm" label-width="120px" label-position="left">
        <el-form-item label="所属部门" required>
          <el-select
            v-model="positionForm.department"
            placeholder="请选择所属部门"
            filterable
            allow-create
            default-first-option
            :filter-method="handleDepartmentFilter"
            style="width: 100%"
            @change="handleDepartmentChange"
          >
            <el-option
              v-for="item in categoryOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="岗位名称" required>
          <el-select
            v-model="positionForm.positionName"
            placeholder="请选择岗位名称"
            filterable
            allow-create
            default-first-option
            style="width: 100%"
            :loading="loadingCategoryPositions"
          >
            <el-option
              v-for="item in positionNameOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="工作地点">
          <elui-china-area-dht v-model="positionForm.addressCode" @change="handleAddressChange" placeholder="请选择省/市/区" style="width: 100%" />
        </el-form-item>

        <el-form-item label="详细地址">
          <el-input v-model="positionForm.detailAddress" placeholder="请输入详细地址，如街道、门牌号等" />
        </el-form-item>

        <el-form-item label="薪资范围">
          <el-col :span="11">
            <el-input v-model="positionForm.salaryMin" placeholder="最低薪资" type="number">
              <template #append>K</template>
            </el-input>
          </el-col>
          <el-col :span="2" style="text-align: center">
            <span>-</span>
          </el-col>
          <el-col :span="11">
            <el-input v-model="positionForm.salaryMax" placeholder="最高薪资" type="number">
              <template #append>K</template>
            </el-input>
          </el-col>
        </el-form-item>

        <el-form-item label="计划招聘人数">
          <el-input-number 
            v-model="positionForm.plannedRecruit" 
            :min="1" 
            :max="100"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="岗位描述">
          <el-input
            v-model="positionForm.description"
            type="textarea"
            :rows="4"
            placeholder="请输入岗位描述"
            :disabled="generatingDescription"
          />
          <div class="ai-generate-tip">
            <el-button
              type="primary"
              size="small"
              :loading="generatingDescription"
              @click="handleGenerateDescription"
              :disabled="!positionForm.positionName"
            >
              <el-icon v-if="!generatingDescription"><MagicStick /></el-icon>
              {{ generatingDescription ? '生成中...' : 'AI智能生成' }}
            </el-button>
            <span class="tip-text">填写岗位名称后点击生成</span>
          </div>
        </el-form-item>

        <el-form-item label="任职要求">
          <el-input
            v-model="positionForm.requirements"
            type="textarea"
            :rows="4"
            placeholder="请输入任职要求"
          />
        </el-form-item>

        <el-form-item label="实习开始日期">
          <el-date-picker
            v-model="positionForm.internshipStartDate"
            type="date"
            placeholder="请选择实习开始日期（选填）"
            style="width: 100%"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>

        <el-form-item label="实习结束日期">
          <el-date-picker
            v-model="positionForm.internshipEndDate"
            type="date"
            placeholder="请选择实习结束日期（选填）"
            style="width: 100%"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>

        <el-divider content-position="left">面试信息</el-divider>

        <el-form-item label="面试时间">
          <el-date-picker
            v-model="positionForm.interviewTime"
            type="datetime"
            placeholder="请选择面试时间（选填）"
            style="width: 100%"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>

        <el-form-item label="面试地点">
          <el-input v-model="positionForm.interviewLocation" placeholder="请输入面试地点（选填）" />
        </el-form-item>

        <el-form-item label="面试方式">
          <el-select v-model="positionForm.interviewMethod" placeholder="请选择面试方式（选填）" style="width: 100%">
            <el-option label="线下" value="线下" />
            <el-option label="线上视频" value="线上视频" />
            <el-option label="电话面试" value="电话面试" />
          </el-select>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="positionForm.interviewRemark" type="textarea" :rows="3" placeholder="请输入备注信息（选填）" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="loading">
          {{ dialogType === 'create' ? '发布' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 面试信息对话框 -->
    <el-dialog
      v-model="interviewDialogVisible"
      :title="interviewDialogTitle"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form :model="interviewForm" label-width="100px" label-position="left">
        <el-form-item label="面试时间">
          <el-date-picker
            v-model="interviewForm.interviewTime"
            type="datetime"
            placeholder="请选择面试时间"
            style="width: 100%"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>

        <el-form-item label="面试地点">
          <el-input v-model="interviewForm.interviewLocation" placeholder="请输入面试地点" />
        </el-form-item>

        <el-form-item label="面试方式">
          <el-select v-model="interviewForm.interviewMethod" placeholder="请选择面试方式" style="width: 100%">
            <el-option label="线下" value="线下" />
            <el-option label="线上视频" value="线上视频" />
            <el-option label="电话面试" value="电话面试" />
          </el-select>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="interviewForm.remark" type="textarea" :rows="3" placeholder="请输入备注信息" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="handleInterviewCancel">取消</el-button>
        <el-button type="primary" @click="handleInterviewSubmit" :loading="interviewLoading">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.recruitment-management {
  padding: 0;
}

.page-header {
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  color: white;
  padding: 24px 40px;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(64, 158, 255, 0.3);
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 8px 0;
  font-size: 28px;
  font-weight: bold;
  letter-spacing: 1px;
  color: white;
}

.page-header p {
  margin: 0;
  font-size: 14px;
  opacity: 0.95;
}

.stats-card {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.stat-item {
  flex: 1;
  background: white;
  padding: 14px 16px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.stat-label {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
  white-space: nowrap;
}

.stat-value {
  font-size: 18px;
  font-weight: 700;
}

.stat-value.primary {
  color: #409EFF;
}

.stat-value.success {
  color: #67c23a;
}

.stat-value.warning {
  color: #e6a23c;
}

.stat-value.info {
  color: #909399;
}

.search-card {
  background: white;
  padding: 12px 16px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  margin-bottom: 20px;
}

.search-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.search-card :deep(.el-form-item) {
  margin-bottom: 0;
  display: flex;
  align-items: center;
}

.search-card :deep(.el-form-item__label) {
  display: flex;
  align-items: center;
  height: 32px;
  line-height: 32px;
}

.search-card :deep(.el-form-item__content) {
  display: flex;
  align-items: center;
}

.table-card {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

/* 禁用表格列宽拖动，内容自动换行 */
.table-card :deep(.el-table) {
  --el-table-header-cell-resizable-border: none;
}

.table-card :deep(.el-table__cell) {
  word-wrap: break-word;
  word-break: break-all;
  white-space: normal !important;
}

.table-card :deep(.el-table .cell) {
  word-wrap: break-word;
  word-break: break-all;
  white-space: normal !important;
  line-height: 1.4;
}

/* 隐藏列调整线 */
.table-card :deep(.el-table__cell.is-hidden > *),
.table-card :deep(.el-table__resizable-wrapper) {
  display: none !important;
}

.empty-state {
  padding: 40px 0;
  text-align: center;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.address-selector {
  display: flex;
  gap: 10px;
  width: 100%;
}

.address-selector :deep(.el-select) {
  flex: 1;
}

:deep(.el-table .paused-row) {
  background-color: #ffebee !important;
}

:deep(.el-table .paused-row td) {
  background-color: #ffebee !important;
  color: #909399 !important;
}

:deep(.el-table .paused-row:hover > td) {
  background-color: #ffcdd2 !important;
}

:deep(.el-table .paused-row .el-tag--warning) {
  background-color: #f4e4c1 !important;
  border-color: #e6a23c !important;
  color: #e6a23c !important;
}

:deep(.el-table .paused-row .paused-icon) {
  color: #f56c6c;
  margin-right: 6px;
  font-size: 16px;
}

:deep(.el-table .paused-row .paused-text) {
  text-decoration: line-through;
}

.action-buttons {
  display: flex;
  gap: 4px;
  justify-content: center;
}

.ai-generate-tip {
  display: flex;
  align-items: center;
  margin-top: 8px;
  gap: 10px;
}

.ai-generate-tip .tip-text {
  font-size: 12px;
  color: #909399;
}
</style>