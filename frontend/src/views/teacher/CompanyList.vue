<script setup lang="ts">
import logger from '@/utils/logger'
import { ref, computed, onMounted } from 'vue'
import type { PaginationState } from '@/types/admin'
import { ElMessage } from 'element-plus'
import { Search, Refresh, View, PictureFilled, VideoCameraFilled, OfficeBuilding, ZoomIn } from '@element-plus/icons-vue'
import request from '../../utils/request'
import companyService from '../../api/company'
import CompanyRelationsPanel from '@/components/teacher/CompanyRelationsPanel.vue'

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

const currentYear = new Date().getFullYear()
let searchForm = ref<any>({ keyword: '', status: null, companyTag: [], industry: '', visitedYear: currentYear, visitState: '', visitTeacherId: '', liaisonTeacherId: '' })
let tableData = ref<any[]>([])
let loading = ref(false)
let pagination = ref<PaginationState>({ currentPage: 1, pageSize: 10, total: 0 })
let viewDialogVisible = ref(false)
let currentCompany = ref<any>(null)
let companyTagOptions = ref<string[]>([])
let teacherOptions = ref<any[]>([])

// 企业宣传资料：photos/videos 为 JSON 数组字符串，容错解析
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

const queryPage = async (): Promise<void> => {
  loading.value = true
  try {
    const params: Record<string, any> = {
      page: pagination.value.currentPage,
      pageSize: pagination.value.pageSize
    }

    logger.log('查询参数 - 原始:', searchForm.value)

    if (searchForm.value.keyword && searchForm.value.keyword.trim() !== '') {
      params.keyword = searchForm.value.keyword.trim()
    }

    if (searchForm.value.status !== null && searchForm.value.status !== undefined && searchForm.value.status !== '') {
      params.status = searchForm.value.status
    }

    if (searchForm.value.companyTag && Array.isArray(searchForm.value.companyTag) && searchForm.value.companyTag.length > 0) {
      params.companyTag = searchForm.value.companyTag.join(',')
    }

    if (searchForm.value.industry && searchForm.value.industry.trim() !== '') {
      params.industry = searchForm.value.industry.trim()
    }

    if (searchForm.value.visitedYear) params.visitedYear = searchForm.value.visitedYear
    if (searchForm.value.visitState !== '' && searchForm.value.visitState !== null) params.visitState = searchForm.value.visitState
    if (searchForm.value.visitTeacherId) params.visitTeacherId = searchForm.value.visitTeacherId
    if (searchForm.value.liaisonTeacherId) params.liaisonTeacherId = searchForm.value.liaisonTeacherId

    logger.log('查询参数 - 处理后:', params)

    const response = await companyService.getCompanies(params) as unknown as {
      code?: number
      message?: string
      msg?: string
      data?: { rows?: unknown[]; total?: number } | unknown[]
    }
    if (response && response.code === 200 && response.data) {
      const pageData: unknown = response.data
      const dataList = Array.isArray(pageData) ? pageData : (Array.isArray((pageData as { rows?: unknown[] }).rows) ? (pageData as { rows: unknown[] }).rows : [])
      const totalCount = !Array.isArray(pageData) && pageData !== null && typeof pageData === 'object'
        ? (pageData as { total?: number }).total ?? dataList.length
        : dataList.length
      tableData.value = dataList
      pagination.value.total = typeof totalCount === 'number' ? totalCount : dataList.length
      logger.log('查询结果 - 数据条数:', dataList.length, '总数:', totalCount)
    } else {
      tableData.value = []
      pagination.value.total = 0
    }
  } catch (error: any) {
    logger.error('查询企业列表失败:', error)
    tableData.value = []
    pagination.value.total = 0
    ElMessage.error('查询企业列表失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const clear = (): void => {
  searchForm.value = { keyword: '', status: null, companyTag: [], industry: '', visitedYear: currentYear, visitState: '', visitTeacherId: '', liaisonTeacherId: '' }
  pagination.value.currentPage = 1
  queryPage()
}

const handleSizeChange = (newSize: number): void => {
  pagination.value.pageSize = newSize
  queryPage()
}

const handleCurrentChange = (newCurrent: number): void => {
  pagination.value.currentPage = newCurrent
  queryPage()
}

const viewCompanyDetail = async (id: string): Promise<void> => {
  try {
    const response = await request.get(`/teacher/companies/${id}`)
    const result = response
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

const formatAccountStatus = (status: number): string => {
  switch (status) {
    case 0: return '待审核'
    case 1: return '正常'
    case 2: return '已禁用'
    case 3: return '已拒绝'
    default: return '未知状态'
  }
}

const getAccountStatusTagType = (status: number): string => {
  switch (status) {
    case 0: return 'warning'
    case 1: return 'success'
    case 2: return 'danger'
    case 3: return 'info'
    default: return 'info'
  }
}

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

// 企业标签合并区：合作模式拆解 + 实习基地/已签协议/校友单位（接受兜底综合 cooperationMode 与 acceptBackup，不重复）

const getCompanyTagList = (company: any): Array<{ text: string; type: string }> => {
  const tags: Array<{ text: string; type: string }> = []
  const modeMap: Record<string, { text: string; type: string }> = {
    '双向': { text: '校企双向选择', type: 'success' },
    '自主': { text: '学生自主联系', type: 'warning' },
    '兜底': { text: '接受兜底', type: 'danger' },
    mutual_choice: { text: '校企双向选择', type: 'success' },
    student_contact: { text: '学生自主联系', type: 'warning' }
  }
  const hasBackup = company.acceptBackup === 1
  if (company.cooperationMode) {
    company.cooperationMode.split(',').forEach((m: string) => {
      const t = modeMap[m.trim()]
      if (t) tags.push(t)
    })
  }
  if (company.isInternshipBase === 1) tags.push({ text: '实习基地', type: 'success' })
  if (hasBackup && !tags.some(t => t.text === '接受兜底')) tags.push({ text: '接受兜底', type: 'danger' })
  if (company.hasTrainingBaseAgreement === 1) tags.push({ text: '已签协议', type: 'info' })
  if (company.isAlumniUnit === 1) tags.push({ text: '校友单位', type: 'warning' })
  // 合并 company_tag 老标签体系，跳过与状态标签重复的（实习基地/接受兜底/合作模式）
  const skip = new Set(tags.map(t => t.text))
  skip.add('校企双向选择').add('学生自主联系')
  if (company.companyTag) {
    company.companyTag.split(/[,，]/).forEach((tag: string) => {
      const t = tag.trim()
      if (t && !skip.has(t)) tags.push({ text: t, type: getTagType(t) })
    })
  }
  return tags
}

const getFullAddress = (company: any): string => {
  const detail = [company.province, company.city, company.district, company.detailAddress].filter(Boolean).join('')
  return detail || company.address || '-'
}

// 仅允许 http/https 链接，防止 javascript: 等协议执行脚本
const isSafeUrl = (url: string): boolean => /^https?:\/\//i.test(url)

const getTagType = (tag: string): string => {
  const tagMap = {
    '校企双向选择': 'primary',
    '学生自主联系': 'success',
    '接受兜底': 'warning',
    '国家级': 'danger',
    '省级': 'warning'
  }
  return tagMap[tag.trim()] || 'info'
}

const fetchCompanyTags = async (): Promise<void> => {
  try {
    const response = await companyService.getCompanyTags()
    if (response && (response as any).code === 200 && (response as any).data) {
      companyTagOptions.value = (response as any).data
    }
  } catch (error: any) {
    logger.error('获取企业标签列表失败:', error)
  }
}

const fetchTeacherOptions = async (): Promise<void> => {
  try {
    interface TeacherOptionRow {
      id?: number
      name?: string
      [key: string]: unknown
    }
    const response = await request.get<TeacherOptionRow[] | { rows?: TeacherOptionRow[] }>('/teacher/companies/teacher-options')
    const optionData: unknown = response?.data
    teacherOptions.value = Array.isArray(optionData) ? optionData : (Array.isArray((optionData as { rows?: TeacherOptionRow[] } | null | undefined)?.rows) ? (optionData as { rows: TeacherOptionRow[] }).rows : [])
  } catch (error: any) {
    logger.error('获取教师选项失败:', error)
  }
}

onMounted(async () => {
  await Promise.all([queryPage(), fetchCompanyTags(), fetchTeacherOptions()])
})
</script>

<template>
  <div class="company-list-container">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">企业信息</h1>
        <p class="page-description">查看所有企业账户信息</p>
      </div>
    </div>

    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" class="search-form" @keyup.enter="queryPage">
        <div class="search-row">
          <el-form-item label="关键词">
            <el-input
              v-model="searchForm.keyword"
              placeholder="企业、对接人、走访教师或校友"
              clearable
              @keyup.enter="queryPage"
            ></el-input>
          </el-form-item>
          <el-form-item label="走访年份">
            <el-input-number v-model="searchForm.visitedYear" :min="2000" :max="2100" controls-position="right" style="width: 130px;" />
          </el-form-item>
          <el-form-item label="走访状态">
            <el-select v-model="searchForm.visitState" placeholder="全部" clearable style="width: 140px;">
              <el-option label="已走访" :value="1"></el-option>
              <el-option label="未走访" :value="0"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="走访教师">
            <el-select v-model="searchForm.visitTeacherId" placeholder="全部" clearable filterable style="width: 160px;">
              <el-option v-for="teacher in teacherOptions" :key="teacher.id" :label="teacher.name" :value="teacher.id"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="校内对接人">
            <el-select v-model="searchForm.liaisonTeacherId" placeholder="全部" clearable filterable style="width: 170px;">
              <el-option v-for="teacher in teacherOptions" :key="teacher.id" :label="teacher.name" :value="teacher.id"></el-option>
            </el-select>
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
          <el-form-item label="账号状态">
            <el-select v-model="searchForm.status" placeholder="请选择状态" clearable style="width: 140px;">
              <el-option label="待审核" :value="0"></el-option>
              <el-option label="正常" :value="1"></el-option>
              <el-option label="已禁用" :value="2"></el-option>
              <el-option label="已拒绝" :value="3"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="单位行业">
            <el-select v-model="searchForm.industry" placeholder="请选择行业" clearable style="width: 160px;">
              <el-option label="IT企业" value="it"></el-option>
              <el-option label="医疗设备企业" value="medical_equipment"></el-option>
              <el-option label="医院" value="hospital"></el-option>
              <el-option label="其它" value="other"></el-option>
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

    <CompanyRelationsPanel @relations-updated="queryPage" />

    <el-card class="table-card" shadow="never">
      <div class="table-section-title">企业列表</div>
      <el-table
        :data="tableData" 
        border 
        style="width: 100%" 
        fit 
        row-key="id"
        class="data-table"
        v-loading="loading"
      >
        <el-table-column type="index" label="序号" width="55" align="center" :index="(index) => (pagination.currentPage - 1) * pagination.pageSize + index + 1" />
        <el-table-column prop="companyName" label="企业名称" min-width="200" align="center" />
        <el-table-column label="校内对接人" min-width="180" align="center">
          <template #default="scope">
            <div v-if="scope.row.schoolLiaisons?.length" class="tag-container">
              <el-tag v-for="liaison in scope.row.schoolLiaisons" :key="liaison.teacherId" size="small" type="primary">
                {{ liaison.teacherName || liaison.name }}{{ liaison.teacherPhone || liaison.phone ? ` · ${liaison.teacherPhone || liaison.phone}` : '' }}
              </el-tag>
            </div>
            <span v-else class="no-tag">未设置</span>
          </template>
        </el-table-column>
        <el-table-column label="本年度走访" width="130" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.visitSummary?.visitedThisYear" type="success" size="small">
              已走访 {{ scope.row.visitSummary.currentYearVisitCount || 1 }} 次
            </el-tag>
            <el-tag v-else type="info" size="small">未走访</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标签" width="240" align="center">
          <template #default="scope">
            <div class="tag-container">
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
            <span v-if="!getCompanyTagList(scope.row).length" class="no-tag">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="账号状态" width="110" align="center">
          <template #default="scope">
            <el-tag :type="getAccountStatusTagType(scope.row.status)" size="small" class="status-tag">
              {{ formatAccountStatus(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="contactPerson" label="联系人" width="100" align="center" />
        <el-table-column prop="contactPhone" label="联系电话" width="140" align="center" />
        <el-table-column prop="contactEmail" label="联系邮箱" min-width="170" align="center" />
        <el-table-column prop="createTime" label="创建时间" width="170" align="center" :formatter="formatDate" />
        <el-table-column label="操作" align="center" width="100" fixed="right">
          <template #default="scope">
            <el-tooltip content="查看详情" placement="top">
              <el-button type="success" size="small" @click="viewCompanyDetail(scope.row.id)" class="table-btn">
                <el-icon><View /></el-icon>
              </el-button>
            </el-tooltip>
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

        <!-- 企业宣传资料（折叠展示） -->
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
              <!-- Logo -->
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

              <!-- 企业照片 -->
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

              <!-- 企业视频 -->
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
  </div>
</template>

<style scoped>
.company-list-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-content h1 {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px 0;
}

.header-content p {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.search-card {
  margin-bottom: 20px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.search-form {
  margin: 0;
}

.search-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.search-actions {
  margin-left: auto;
}

.search-btn,
.reset-btn {
  padding: 8px 20px;
  border-radius: 8px;
  font-weight: 500;
}

.table-card {
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.table-section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}

.data-table {
  margin-bottom: 20px;
}

.status-tag {
  font-weight: 500;
}

.tag-container {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: center;
}

.company-tag {
  margin: 2px;
  font-size: 12px;
}

.no-tag {
  color: #909399;
  font-size: 12px;
}

.table-btn {
  padding: 6px 12px;
  border-radius: 6px;
  transition: all 0.3s;
}

.table-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.12);
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0;
}

.custom-pagination {
  display: flex;
  align-items: center;
}

.view-dialog {
  border-radius: 12px;
}

.company-detail {
  padding: 8px 0;
}

.company-intro {
  line-height: 1.6;
  color: #606266;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 企业宣传资料 */
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

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 768px) {
  .search-row :deep(.el-form-item) {
    width: 100%;
    margin-right: 0;
  }

  .search-row :deep(.el-select) {
    width: 100% !important;
  }
}
</style>
