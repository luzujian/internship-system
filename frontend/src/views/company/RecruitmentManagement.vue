<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VideoPause, Message } from '@element-plus/icons-vue'
import { usePositionStore } from '../../store/position'
import { useAuthStore } from '../../store/auth'
import positionApi from '../../api/PositionService'
import studentApi from '../../api/StudentUserService'
import { EluiChinaAreaDht } from 'elui-china-area-dht'

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

const positionNameOptions = computed(() => {
  const uniquePositions = new Set()
  if (positionStore.positions && Array.isArray(positionStore.positions)) {
    positionStore.positions.forEach(pos => {
      if (pos.positionName) {
        uniquePositions.add(pos.positionName)
      }
    })
  }
  return Array.from(uniquePositions).map(name => ({ label: name, value: name }))
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

const departmentOptions = [
  { label: '技术部', value: '技术部' },
  { label: '产品部', value: '产品部' },
  { label: '设计部', value: '设计部' },
  { label: '测试部', value: '测试部' },
  { label: '运营部', value: '运营部' },
  { label: '市场部', value: '市场部' }
]

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
  if (!companyId.value) {
    ElMessage.error('未获取到企业 ID，无法加载数据')
    return
  }
  positionStore.fetchPositions(companyId.value)
  positionStore.fetchInternshipStatuses(companyId.value)
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
        style="width: 100%"
      >
        <el-table-column prop="positionName" label="岗位名称" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.status === 'paused'" class="paused-text" style="background-color: #ffebee; color: #909399; text-decoration: line-through; display: inline-block; padding: 8px 12px; border-radius: 4px;">
              <el-icon class="paused-icon" style="color: #f56c6c; margin-right: 6px; font-size: 16px;"><VideoPause /></el-icon>
              {{ row.positionName }}
            </span>
            <span v-else>{{ row.positionName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="所属部门" width="90" />
        <el-table-column label="工作地点" width="330">
          <template #default="{ row }">
            <div style="white-space: pre-wrap; word-break: break-all; line-height: 1.5;">
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
        <el-table-column label="薪资范围" width="90">
          <template #default="{ row }">
            {{ row.salaryMin && row.salaryMax ? `${row.salaryMin}-${row.salaryMax}K` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="plannedRecruit" label="计划" width="70" align="center" />
        <el-table-column prop="recruitedCount" label="已确认" width="80" align="center" />
        <el-table-column prop="remainingQuota" label="缺口" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.remainingQuota === 0 ? 'success' : 'warning'" size="small">
              {{ row.remainingQuota }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="150">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status]" size="small">
              {{ statusMap[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishDate" label="发布日期" width="120" />
        <el-table-column prop="viewCount" label="浏览次数" width="90" align="center">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.viewCount || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center">
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
        <el-form-item label="岗位名称" required>
          <el-input v-model="positionForm.positionName" placeholder="请输入岗位名称" style="width: 100%" />
        </el-form-item>

        <el-form-item label="所属部门">
          <el-select v-model="positionForm.department" placeholder="请选择所属部门" style="width: 100%">
            <el-option
              v-for="item in departmentOptions"
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
          />
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
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.stat-item {
  background: white;
  padding: 24px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  text-align: center;
  transition: all 0.3s ease;
}

.stat-item:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 12px;
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  margin-bottom: 4px;
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
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  margin-bottom: 20px;
}

.search-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.table-card {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
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
</style>