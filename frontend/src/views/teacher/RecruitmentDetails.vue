<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { TableColumnCtx } from 'element-plus'
import { Search, Refresh, View } from '@element-plus/icons-vue'
import { getRecruitmentPositions, getRecruitmentDetail } from '@/api/teacherRecruitment'
import type { RecruitmentCompany, RecruitmentDetail, RecruitmentPosition } from '@/api/teacherRecruitment'
import PositionCategoryService from '@/api/positionCategory'
import type { PositionCategory } from '@/api/positionCategory'
import { normalizeSalaryUnit } from '@/utils/salaryRange.js'
import { normalizeInterviewMethods } from '@/utils/interviewMethods.js'

const filters = reactive({ companyName: '', positionName: '', status: '', categoryId: undefined as number | undefined })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const positions = ref<RecruitmentPosition[]>([])
const categories = ref<PositionCategory[]>([])
const loading = ref(false)
const detailLoading = ref(false)
const dialogVisible = ref(false)
const detail = ref<RecruitmentDetail | null>(null)
const activeTab = ref('position')

async function loadPositions() {
  loading.value = true
  try {
    const response = await getRecruitmentPositions({
      page: pagination.page, pageSize: pagination.pageSize,
      companyName: filters.companyName.trim() || undefined,
      positionName: filters.positionName.trim() || undefined,
      status: filters.status || undefined,
      categoryId: filters.categoryId || undefined
    })
    positions.value = response.data.rows
    pagination.total = response.data.total
  } catch {
    positions.value = []
    pagination.total = 0
    ElMessage.error('获取招聘岗位失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.page = 1
  loadPositions()
}

function reset() {
  Object.assign(filters, { companyName: '', positionName: '', status: '', categoryId: undefined })
  search()
}

async function loadCategories() {
  try {
    const response = await PositionCategoryService.getAllCategoriesPublic()
    categories.value = response.data
  } catch {
    ElMessage.error('获取岗位类别失败，请稍后重试')
  }
}

const companyRowSpans = computed(() => {
  const spans = new Array<number>(positions.value.length).fill(0)
  let groupStart = 0
  positions.value.forEach((position, index) => {
    if (index === 0 || position.companyId !== positions.value[index - 1].companyId) groupStart = index
    spans[groupStart] += 1
  })
  return spans
})

function mergeCompanyCells({ column, rowIndex }: { column: TableColumnCtx<RecruitmentPosition>; rowIndex: number }) {
  if (column.property === 'companyName' || column.property === 'companyPositionCount') {
    const rowspan = companyRowSpans.value[rowIndex]
    return { rowspan, colspan: rowspan > 0 ? 1 : 0 }
  }
  return { rowspan: 1, colspan: 1 }
}

async function viewDetail(id: number) {
  detail.value = null
  activeTab.value = 'position'
  dialogVisible.value = true
  detailLoading.value = true
  try {
    const response = await getRecruitmentDetail(id)
    detail.value = response.data
  } catch {
    dialogVisible.value = false
    ElMessage.error('获取招聘详情失败，请稍后重试')
  } finally {
    detailLoading.value = false
  }
}

function salary(position: RecruitmentPosition) {
  const { salaryMin: min, salaryMax: max } = position
  const unit = normalizeSalaryUnit(position.salaryUnit)
  if (unit === '面议' || (!min && !max)) return '面议'
  if (min != null && max != null) return `${min === max ? min : `${min}–${max}`} ${unit || 'K/月'}`
  return min != null ? `${min} ${unit || 'K/月'}起` : `最高 ${max} ${unit || 'K/月'}`
}

function address(item: RecruitmentPosition | RecruitmentCompany) {
  return [item.province, item.city, item.district, item.detailAddress].filter(Boolean).join('')
    || ('address' in item ? item.address : '') || '未填写'
}

function date(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '未填写'
}

function media(value?: string) {
  try {
    const parsed: unknown = JSON.parse(value || '[]')
    return Array.isArray(parsed)
      ? parsed.filter((url): url is string => typeof url === 'string' && /^(https?:\/\/|\/(?!\/))/.test(url))
      : []
  } catch { return [] }
}

const photos = computed(() => media(detail.value?.company.photos))
const videos = computed(() => media(detail.value?.company.videos))
const website = computed(() => /^https?:\/\//i.test(detail.value?.company.website || '')
  ? detail.value?.company.website : undefined)
const companyTags = computed(() => {
  const company = detail.value?.company
  if (!company) return []
  const tags = ['校企双向选择']
  if (company.isInternshipBase === 1) tags.push('实习基地')
  if (company.acceptBackup === 1 || company.cooperationMode?.split(',').includes('兜底')) tags.push('接受兜底')
  if (company.hasTrainingBaseAgreement === 1) tags.push('已签协议')
  if (company.isAlumniUnit === 1) tags.push('校友单位')
  const legacy = (company.companyTag || '').split(/[,，]/).map(tag => tag.trim()).filter(Boolean)
    .filter(tag => !['学生自主联系', '双向选择阶段', '自主联系阶段'].includes(tag))
  return [...new Set([...tags, ...legacy])]
})
const industryNames: Record<string, string> = {
  it: 'IT企业', medical_equipment: '医疗设备企业', hospital: '医院', other: '其它'
}
const scaleNames: Record<string, string> = {
  '1-50': '1-50 人', '51-100': '51-100 人', '101-500': '101-500 人',
  '501-1000': '501-1000 人', '1000+': '1000 人以上'
}

onMounted(() => {
  loadPositions()
  loadCategories()
})
</script>

<template>
  <div class="recruitment-container">
    <div class="page-header">
      <h1>招聘详情</h1>
      <p>查看全平台企业已发布的岗位，包含招聘中与已暂停岗位。</p>
    </div>

    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="filters" @submit.prevent="search">
        <el-form-item label="企业名称">
          <el-input v-model="filters.companyName" placeholder="请输入企业名称" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="岗位名称">
          <el-input v-model="filters.positionName" placeholder="请输入岗位名称" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="岗位类别">
          <el-select v-model="filters.categoryId" placeholder="全部" clearable filterable style="width: 180px">
            <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="招聘状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 150px">
            <el-option label="招聘中" value="active" />
            <el-option label="已暂停" value="paused" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="list-card" shadow="never">
      <template #header><strong>已发布岗位（共 {{ pagination.total }} 个）</strong></template>
      <el-table v-loading="loading" :data="positions" :span-method="mergeCompanyCells" border stripe row-key="id" empty-text="暂无符合条件的已发布岗位">
        <el-table-column type="index" label="序号" width="65" align="center" :index="index => (pagination.page - 1) * pagination.pageSize + index + 1" />
        <el-table-column prop="companyName" label="企业名称" min-width="210" show-overflow-tooltip />
        <el-table-column prop="companyPositionCount" label="企业已发布岗位数" width="150" align="center" />
        <el-table-column prop="positionName" label="岗位名称" min-width="170" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="岗位类别" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.categoryName || '未分类' }}</template>
        </el-table-column>
        <el-table-column label="薪资" min-width="145" align="center"><template #default="{ row }">{{ salary(row) }}</template></el-table-column>
        <el-table-column prop="plannedRecruit" label="计划人数" width="95" align="center" />
        <el-table-column prop="recruitedCount" label="已招人数" width="95" align="center" />
        <el-table-column prop="remainingQuota" label="剩余名额" width="95" align="center" />
        <el-table-column label="招聘状态" width="110" align="center">
          <template #default="{ row }"><el-tag :type="row.status === 'active' ? 'success' : 'warning'">{{ row.status === 'active' ? '招聘中' : '已暂停' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="工作地点" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ address(row) }}</template></el-table-column>
        <el-table-column label="发布时间" width="160"><template #default="{ row }">{{ date(row.publishDate || row.createTime) }}</template></el-table-column>
        <el-table-column label="查看" width="100" fixed="right" align="center">
          <template #default="{ row }"><el-button type="success" size="small" :icon="View" @click="viewDetail(row.id)">详情</el-button></template>
        </el-table-column>
      </el-table>
      <div class="pagination-container">
        <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadPositions" @size-change="search" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="招聘详情" width="min(1000px, calc(100vw - 32px))" class="recruitment-dialog">
      <div v-loading="detailLoading" class="detail-content">
        <el-tabs v-if="detail" v-model="activeTab">
          <el-tab-pane label="岗位信息" name="position">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="岗位名称">{{ detail.position.positionName }}</el-descriptions-item>
              <el-descriptions-item label="企业名称">{{ detail.position.companyName }}</el-descriptions-item>
              <el-descriptions-item label="岗位类别" :span="2">{{ detail.position.categoryName || '未分类' }}</el-descriptions-item>
              <el-descriptions-item label="薪资">{{ salary(detail.position) }}</el-descriptions-item>
              <el-descriptions-item label="招聘状态">{{ detail.position.status === 'active' ? '招聘中' : '已暂停' }}</el-descriptions-item>
              <el-descriptions-item label="计划人数">{{ detail.position.plannedRecruit }}</el-descriptions-item>
              <el-descriptions-item label="已招人数">{{ detail.position.recruitedCount }}</el-descriptions-item>
              <el-descriptions-item label="剩余名额">{{ detail.position.remainingQuota }}</el-descriptions-item>
              <el-descriptions-item label="企业已发布岗位数">{{ detail.position.companyPositionCount }}</el-descriptions-item>
              <el-descriptions-item label="工作地点" :span="2">{{ address(detail.position) }}</el-descriptions-item>
              <el-descriptions-item label="部门">{{ detail.position.department || '未填写' }}</el-descriptions-item>
              <el-descriptions-item label="岗位类型">{{ detail.position.positionType || '未填写' }}</el-descriptions-item>
              <el-descriptions-item label="联系人">{{ detail.position.contactPerson || '未填写' }}</el-descriptions-item>
              <el-descriptions-item label="联系电话">{{ detail.position.contactPhone || '未填写' }}</el-descriptions-item>
              <el-descriptions-item label="联系邮箱" :span="2">{{ detail.position.contactEmail || '未填写' }}</el-descriptions-item>
              <el-descriptions-item label="专业要求" :span="2">{{ detail.position.requiredMajors || '不限专业' }}</el-descriptions-item>
              <el-descriptions-item label="提供住宿">{{ detail.position.providesAccommodation === 1 ? '是' : '否' }}</el-descriptions-item>
              <el-descriptions-item label="发布时间">{{ date(detail.position.publishDate || detail.position.createTime) }}</el-descriptions-item>
              <el-descriptions-item label="岗位描述" :span="2"><div class="long-text">{{ detail.position.description || '未填写' }}</div></el-descriptions-item>
              <el-descriptions-item label="岗位要求" :span="2"><div class="long-text">{{ detail.position.requirements || '未填写' }}</div></el-descriptions-item>
              <el-descriptions-item label="实习环境" :span="2"><div class="long-text">{{ detail.position.internshipEnvironment || '未填写' }}</div></el-descriptions-item>
              <el-descriptions-item label="面试时间">{{ date(detail.position.interviewTime) }}</el-descriptions-item>
              <el-descriptions-item label="面试方式">{{ normalizeInterviewMethods(detail.position.interviewMethod).join('、') || '未填写' }}</el-descriptions-item>
              <el-descriptions-item label="面试地点" :span="2">{{ detail.position.interviewLocation || '未填写' }}</el-descriptions-item>
              <el-descriptions-item label="面试说明" :span="2"><div class="long-text">{{ detail.position.interviewRemark || '未填写' }}</div></el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>
          <el-tab-pane label="企业信息" name="company">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="企业名称" :span="2">{{ detail.company.companyName }}</el-descriptions-item>
              <el-descriptions-item label="企业标签" :span="2"><div class="company-tags"><el-tag v-for="tag in companyTags" :key="tag" size="small" type="success">{{ tag }}</el-tag></div></el-descriptions-item>
              <el-descriptions-item label="联系人">{{ detail.company.contactPerson || '未填写' }}</el-descriptions-item>
              <el-descriptions-item label="联系电话">{{ detail.company.contactPhone || '未填写' }}</el-descriptions-item>
              <el-descriptions-item label="联系邮箱" :span="2">{{ detail.company.contactEmail || '未填写' }}</el-descriptions-item>
              <el-descriptions-item label="统一社会信用代码" :span="2">{{ detail.company.creditCode || '未填写' }}</el-descriptions-item>
              <el-descriptions-item label="所属行业">{{ industryNames[detail.company.industry || ''] || detail.company.industry || '未填写' }}</el-descriptions-item>
              <el-descriptions-item label="企业规模">{{ scaleNames[detail.company.scale || ''] || detail.company.scale || '未填写' }}</el-descriptions-item>
              <el-descriptions-item label="企业地址" :span="2">{{ address(detail.company) }}</el-descriptions-item>
              <el-descriptions-item label="企业网站" :span="2"><el-link v-if="website" :href="website" target="_blank" rel="noopener noreferrer" type="primary">{{ detail.company.website }}</el-link><span v-else>{{ detail.company.website || '未填写' }}</span></el-descriptions-item>
              <el-descriptions-item label="企业简介" :span="2"><div class="long-text">{{ detail.company.introduction || '暂无介绍' }}</div></el-descriptions-item>
              <el-descriptions-item v-if="detail.company.alumniMembers?.length" label="校友信息" :span="2"><div v-for="member in detail.company.alumniMembers" :key="member.id">{{ [member.name, member.phone, member.position].filter(Boolean).join(' · ') }}</div></el-descriptions-item>
              <el-descriptions-item v-if="detail.company.schoolLiaisons?.length" label="校内对接人" :span="2"><div v-for="liaison in detail.company.schoolLiaisons" :key="liaison.teacherId">{{ [liaison.teacherName, liaison.teacherPhone].filter(Boolean).join(' · ') }}</div></el-descriptions-item>
            </el-descriptions>
            <section v-if="detail.company.logo || photos.length || videos.length" class="promotion">
              <h3>企业宣传资料</h3>
              <template v-if="detail.company.logo"><h4>企业 Logo</h4><el-image class="company-logo" :src="detail.company.logo" fit="contain" :preview-src-list="[detail.company.logo]" preview-teleported /></template>
              <template v-if="photos.length"><h4>企业照片（{{ photos.length }} 张）</h4><div class="photo-grid"><el-image v-for="(url, index) in photos" :key="url" :src="url" fit="cover" :preview-src-list="photos" :initial-index="index" preview-teleported><template #error><span>图片加载失败</span></template></el-image></div></template>
              <template v-if="videos.length"><h4>企业视频（{{ videos.length }} 个）</h4><div class="video-grid"><video v-for="url in videos" :key="url" :src="url" controls preload="metadata" /></div></template>
            </section>
          </el-tab-pane>
        </el-tabs>
      </div>
      <template #footer><el-button @click="dialogVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.recruitment-container { padding: 20px; background: #f5f7fa; min-height: 100%; }
.page-header { margin-bottom: 20px; }
.page-header h1 { margin: 0 0 8px; font-size: 24px; font-weight: 600; color: #303133; }
.page-header p { margin: 0; color: #909399; font-size: 14px; }
.search-card, .list-card { margin-bottom: 20px; border-radius: 12px; }
.search-card :deep(.el-form-item) { margin-bottom: 12px; }
.pagination-container { display: flex; justify-content: flex-end; margin-top: 20px; }
.detail-content { min-height: 180px; }
.long-text { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.7; }
.company-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.promotion { margin-top: 24px; }
.promotion h3 { color: #303133; font-size: 18px; }
.promotion h4 { color: #606266; font-size: 14px; font-weight: 500; }
.company-logo { width: 160px; height: 120px; }
.photo-grid, .video-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.photo-grid :deep(.el-image) { height: 160px; border-radius: 8px; }
.video-grid video { width: 100%; border-radius: 8px; }
@media (max-width: 768px) {
  .recruitment-container { padding: 12px; }
  .photo-grid, .video-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .pagination-container { overflow-x: auto; justify-content: flex-start; }
}
</style>
