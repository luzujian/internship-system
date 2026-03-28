<template>
  <div class="jobs-container">
    <!-- 柔和背景装饰 -->
    <div class="jobs-bg-pattern" aria-hidden="true">
      <span class="bg-dot"></span>
      <span class="bg-dot"></span>
      <span class="bg-dot"></span>
      <span class="bg-dot"></span>
      <span class="bg-dot"></span>
    </div>

    <!-- 内容区域 -->
    <div>
      <div class="page-header">
        <h2>职位浏览</h2>
        <p>查看并申请适合您的实习职位</p>
      </div>

      <div class="search-section">
        <div class="search-filter-container">
          <div class="search-left">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索职位或公司..."
              :prefix-icon="Search"
              clearable
              class="search-input"
              @keyup.enter="handleSearch"
            />
            <el-button type="primary" @click="handleSearch" class="search-button">
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
          </div>
          <div class="filter-center">
            <div class="quick-filters">
              <span 
                v-for="item in quickFilters" 
                :key="item.value"
                :class="['quick-filter-item', { active: activeQuickFilter === item.value }]"
                @click="handleQuickFilter(item.value)"
              >
                {{ item.label }}
              </span>
            </div>
          </div>
          <div class="filter-right">
            <el-button @click="showFilter = !showFilter" class="filter-toggle-button" :class="{ active: showFilter }">
              <el-icon><Filter /></el-icon>
              筛选
              <el-badge v-if="hasActiveFilters" :value="activeFilterCount" :max="99" class="filter-badge" />
            </el-button>
          </div>
        </div>

        <el-collapse-transition>
          <div v-show="showFilter" class="filter-panel">
            <div class="filter-panel-content">
              <div class="filter-row">
                <div class="filter-group">
                  <div class="filter-label">
                    <el-icon class="label-icon"><Location /></el-icon>
                    <span>地区</span>
                  </div>
                  <div class="filter-cascader">
                    <el-cascader
                      v-model="selectedRegion"
                      :options="regionOptions"
                      :props="cascaderProps"
                      placeholder="请选择省/市/区"
                      clearable
                      class="region-cascader"
                    />
                  </div>
                </div>

                <div class="filter-group">
                  <div class="filter-label">
                    <el-icon class="label-icon"><OfficeBuilding /></el-icon>
                    <span>公司</span>
                  </div>
                  <div class="filter-options">
                    <div
                      v-for="option in companyOptions"
                      :key="option.value"
                      :class="['filter-option', { active: selectedCompany.includes(option.value) }]"
                      @click="toggleFilter('company', option.value)"
                    >
                      {{ option.label }}
                    </div>
                  </div>
                </div>
              </div>

              <div class="filter-row">
                <div class="filter-group">
                  <div class="filter-label">
                    <el-icon class="label-icon"><OfficeBuilding /></el-icon>
                    <span>行业</span>
                  </div>
                  <div class="filter-options">
                    <div
                      v-for="option in industryOptions"
                      :key="option.value"
                      :class="['filter-option', { active: selectedIndustry.includes(option.value) }]"
                      @click="toggleFilter('industry', option.value)"
                    >
                      {{ option.label }}
                    </div>
                  </div>
                </div>

                <div class="filter-group">
                  <div class="filter-label">
                    <el-icon class="label-icon"><Star /></el-icon>
                    <span>实习基地</span>
                  </div>
                  <div class="filter-options">
                    <div
                      v-for="option in internshipBaseOptions"
                      :key="option.value"
                      :class="['filter-option', { active: selectedInternshipBase === option.value }]"
                      @click="selectedInternshipBase = option.value"
                    >
                      {{ option.label }}
                    </div>
                  </div>
                </div>
              </div>

              <div class="filter-row two-cols">
                <div class="filter-group">
                  <div class="filter-label">
                    <el-icon class="label-icon"><Money /></el-icon>
                    <span>薪资范围</span>
                  </div>
                  <div class="filter-options">
                    <div
                      v-for="option in salaryOptions"
                      :key="option.value"
                      :class="['filter-option', { active: selectedSalary === option.value }]"
                      @click="selectedSalary = option.value"
                    >
                      {{ option.label }}
                    </div>
                  </div>
                </div>

                </div>

              <div class="filter-actions">
                <div class="filter-summary">
                  <span class="summary-label">已选条件：</span>
                  <span class="summary-text">{{ filterSummary }}</span>
                </div>
                <el-button @click="resetFilters" class="reset-button">
                  <el-icon><RefreshLeft /></el-icon>
                  重置筛选
                </el-button>
              </div>
            </div>
          </div>
        </el-collapse-transition>
      </div>

      <div class="jobs-list">
        <div
          v-for="job in paginatedJobs"
          :key="job.id"
          class="job-card"
          @click="viewJobDetail(job)"
        >
              <div class="job-right">
                <div class="job-header">
                  <div class="job-title-container">
                    <h3 class="job-title">{{ job.title }}</h3>
                    <div class="internship-base-badges">
                      <el-tag v-if="job.internshipBase === 'national'" size="small" type="danger" class="base-badge national">
                        国家级实习基地
                      </el-tag>
                      <el-tag v-else-if="job.internshipBase === 'provincial'" size="small" type="warning" class="base-badge provincial">
                        省级实习基地
                      </el-tag>
                    </div>
                  </div>
                  <el-icon
                    class="favorite-icon"
                    :class="{ active: job.isFavorite }"
                    @click.stop="toggleFavorite(job)"
                  >
                    <StarFilled v-if="job.isFavorite" />
                    <Star v-else />
                  </el-icon>
                </div>
                <div class="job-info">
                  <div class="info-item">
                    <el-icon class="info-icon"><OfficeBuilding /></el-icon>
                    <span>{{ job.company }}</span>
                  </div>
                  <div class="info-item">
                    <el-icon class="info-icon"><Location /></el-icon>
                    <span>{{ job.location }}</span>
                  </div>
                  <div class="info-item">
                    <el-icon class="info-icon"><Money /></el-icon>
                    <span>{{ job.salary }}</span>
                  </div>
                  <div class="info-item">
                    <el-icon class="info-icon"><User /></el-icon>
                    <span>剩余 {{ job.remainingQuota }} 个名额</span>
                  </div>
                </div>
                <div class="job-tags-footer">
                  <div class="job-tags">
                    <el-tag v-for="tag in job.tags" :key="tag" size="small" class="job-tag">
                      {{ tag }}
                    </el-tag>
                  </div>
                  <div class="job-footer">
                    <el-button 
                      type="primary" 
                      plain
                      @click.stop="viewJobDetail(job)"
                      class="view-detail-button"
                    >
                      查看详情
                    </el-button>
                    <el-button
                      v-if="!job.isApplied && job.remainingQuota > 0 && (studentStatus < 2 || studentStatus === 5)"
                      type="primary"
                      @click.stop="applyJob(job)"
                      class="apply-button"
                    >
                      立即申请
                    </el-button>
                    <el-tag v-else-if="job.remainingQuota === 0" type="info">
                      已招满
                    </el-tag>
                    <el-button
                      v-else-if="job.isApplied"
                      type="warning"
                      @click.stop="cancelApply(job)"
                      class="cancel-apply-button"
                    >
                      取消申请
                    </el-button>
                  </div>
                </div>
            </div>
          </div>
        </div>
      </div>

      <div class="pagination-section">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="totalJobs"
          :page-sizes="[6, 9, 12, 18]"
          layout="total, sizes, prev, pager, next, jumper"
          class="pagination"
        />
      </div>

      <el-empty v-if="totalJobs === 0" description="暂无符合条件的职位"></el-empty>

      <el-dialog
        v-model="showJobDetailDialog"
        title="职位详情"
        width="800px"
        class="edit-profile-dialog"
        :append-to-body="true"
        :lock-scroll="true"
        modal-class="global-modal"
        data-custom-dialog="edit-profile"
      >
        <div v-if="selectedJob" class="job-detail-content">
          <div class="detail-header">
            <div class="detail-title">
              <h3>{{ selectedJob.title }}</h3>
              <div class="internship-base-badges">
                <el-tag v-if="selectedJob.internshipBase === 'national'" size="small" type="danger" class="base-badge national">
                  国家级实习基地
                </el-tag>
                <el-tag v-else-if="selectedJob.internshipBase === 'provincial'" size="small" type="warning" class="base-badge provincial">
                  省级实习基地
                </el-tag>
              </div>
            </div>
            <div class="detail-actions">
              <el-icon
                class="favorite-icon"
                :class="{ active: selectedJob.isFavorite }"
                @click="toggleFavorite(selectedJob)"
              >
                <StarFilled v-if="selectedJob.isFavorite" />
                <Star v-else />
              </el-icon>
            </div>
          </div>

          <div class="detail-section">
            <h4 class="section-title">基本信息</h4>
            <div class="info-grid">
              <div class="info-item">
                <el-icon><OfficeBuilding /></el-icon>
                <span class="info-label">公司：</span>
                <span class="info-value">{{ selectedJob.company }}</span>
              </div>
              <div class="info-item">
                <el-icon><Location /></el-icon>
                <span class="info-label">地点：</span>
                <span class="info-value">{{ selectedJob.location }}</span>
              </div>
              <div class="info-item">
                <el-icon><Money /></el-icon>
                <span class="info-label">薪资：</span>
                <span class="info-value">{{ selectedJob.salary }}</span>
              </div>
              <div class="info-item">
                <el-icon><OfficeBuilding /></el-icon>
                <span class="info-label">类型：</span>
                <span class="info-value">{{ selectedJob.type }}</span>
              </div>
              <div class="info-item">
                <el-icon><OfficeBuilding /></el-icon>
                <span class="info-label">行业：</span>
                <span class="info-value">{{ selectedJob.industryName }}</span>
              </div>
            </div>
          </div>

          <div class="detail-section">
            <h4 class="section-title">职位描述</h4>
            <p class="detail-description">{{ selectedJob.description }}</p>
          </div>

          <div class="detail-section">
            <h4 class="section-title">任职要求</h4>
            <p class="detail-description">{{ formatArrayOrString(selectedJob.requirements) }}</p>
          </div>

          <div class="detail-section">
            <h4 class="section-title">联系方式</h4>
            <div class="contact-info">
              <div class="contact-item">
                <span class="contact-label">联系人：</span>
                <span class="contact-value">{{ selectedJob.contactPerson }}</span>
              </div>
              <div class="contact-item">
                <span class="contact-label">电话：</span>
                <span class="contact-value">{{ selectedJob.contactPhone }}</span>
              </div>
              <div class="contact-item">
                <span class="contact-label">邮箱：</span>
                <span class="contact-value">{{ selectedJob.contactEmail }}</span>
              </div>
            </div>
          </div>

          <div class="detail-section">
            <h4 class="section-title">职位统计</h4>
            <div class="stats-info">
              <div class="stat-item">
                <span class="stat-label">发布时间：</span>
                <span class="stat-value">{{ selectedJob.publishTime || '2024-01-01' }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">浏览次数：</span>
                <span class="stat-value">{{ selectedJob.viewCount || 0 }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">申请人数：</span>
                <span class="stat-value">{{ selectedJob.applyCount || 0 }}</span>
              </div>
            </div>
          </div>

          <div class="detail-footer">
            <el-button
              v-if="!selectedJob.isApplied && selectedJob.remainingQuota > 0 && (studentStatus < 2 || studentStatus === 5)"
              type="primary"
              size="large"
              @click="applyJob(selectedJob)"
              class="detail-apply-button"
            >
              立即申请
            </el-button>
            <el-tag v-else-if="selectedJob.remainingQuota === 0" type="info">
              已招满
            </el-tag>
            <el-button
              v-else-if="selectedJob.isApplied"
              type="warning"
              size="large"
              @click="cancelApply(selectedJob)"
              class="detail-cancel-apply-button"
            >
              取消申请
            </el-button>
          </div>
        </div>
      </el-dialog>

      <!-- 岗位申请对话框 -->
      <el-dialog
        v-model="showApplicationDialog"
        width="700px"
        class="application-dialog"
        append-to-body
        lock-scroll
        modal-class="global-modal"
        :title="''"
      >
        <div class="dialog-content">
          <div class="header-section">
            <h1 class="header-title">申请职位</h1>
          </div>

          <el-form :model="applicationForm" :rules="applicationRules" ref="applicationFormRef" label-width="100px">
            <div class="form-content">
              <div class="form-module">
                <div class="module-header">
                  <div class="module-icon-container">
                    <el-icon class="module-icon"><User /></el-icon>
                  </div>
                  <h2 class="module-title">基本信息</h2>
                </div>
                <div class="form-grid">
                  <el-form-item label="学生姓名" prop="studentName">
                    <el-input v-model="applicationForm.studentName" placeholder="请输入姓名" clearable />
                  </el-form-item>
                  <el-form-item label="学号" prop="studentNo">
                    <el-input v-model="applicationForm.studentNo" placeholder="请输入学号" clearable />
                  </el-form-item>
                  <el-form-item label="专业" prop="major">
                    <el-input v-model="applicationForm.major" placeholder="请输入专业" clearable />
                  </el-form-item>
                  <el-form-item label="年级" prop="grade">
                    <el-select v-model="applicationForm.grade" placeholder="请选择年级" clearable style="width: 100%">
                      <el-option label="一年级" value="一年级" />
                      <el-option label="二年级" value="二年级" />
                      <el-option label="三年级" value="三年级" />
                      <el-option label="四年级" value="四年级" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="联系电话" prop="phone">
                    <el-input v-model="applicationForm.phone" placeholder="请输入联系电话" clearable />
                  </el-form-item>
                </div>
              </div>

              <div class="form-module">
                <div class="module-header">
                  <div class="module-icon-container">
                    <el-icon class="module-icon"><Briefcase /></el-icon>
                  </div>
                  <h2 class="module-title">申请信息</h2>
                </div>
                <div class="form-grid">
                  <el-form-item label="应聘岗位">
                    <el-input :model-value="applicationForm.positionName" disabled />
                  </el-form-item>
                  <el-form-item label="应聘单位">
                    <el-input :model-value="applicationForm.companyName" disabled />
                  </el-form-item>
                </div>
              </div>

              <div class="form-module">
                <div class="module-header">
                  <div class="module-icon-container">
                    <el-icon class="module-icon"><Document /></el-icon>
                  </div>
                  <h2 class="module-title">自我介绍</h2>
                </div>
                <el-form-item label="自我简介" prop="selfIntroduction">
                  <el-input
                    v-model="applicationForm.selfIntroduction"
                    type="textarea"
                    :rows="4"
                    placeholder="请简要介绍你的个人特点、学习能力和职业规划等"
                    resize="vertical"
                  />
                </el-form-item>
              </div>

              <!-- 我的申请材料模块 -->
              <div class="form-module">
                <div class="module-header">
                  <div class="module-icon-container">
                    <el-icon class="module-icon"><Upload /></el-icon>
                  </div>
                  <h2 class="module-title">我的申请材料</h2>
                </div>
                <div class="material-list">
                  <div v-if="resumeList.length === 0 && certificateList.length === 0" class="no-material-tip">
                    暂无可用的申请材料，请在个人中心上传简历和证书
                  </div>
                  <div v-for="resume in resumeList" :key="resume.id" class="material-item">
                    <el-icon class="material-icon"><Document /></el-icon>
                    <span class="material-name">{{ resume.name || '个人简历' }}</span>
                  </div>
                  <div v-for="cert in certificateList" :key="cert.id" class="material-item">
                    <el-icon class="material-icon"><Document /></el-icon>
                    <span class="material-name">{{ cert.name || '证书文件' }}</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="form-actions">
              <el-button @click="showApplicationDialog = false" class="reset-btn">取消</el-button>
              <el-button type="primary" @click="submitApplication" :loading="submitting" class="submit-btn">
                提交申请
              </el-button>
            </div>
          </el-form>
        </div>
      </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Location,
  Money,
  Clock,
  Search,
  Star,
  StarFilled,
  RefreshLeft,
  Filter,
  OfficeBuilding,
  User,
  Briefcase,
  Document,
  Upload
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { useAuthStore } from '@/store/auth'
import studentJobApplicationService from '@/api/StudentJobApplicationService'
import positionService from '@/api/PositionService'
import { initAnnouncementWebSocket, onPositionUpdate, onPositionDelete, offPositionUpdate, offPositionDelete } from '@/utils/websocket'
import type { PositionUpdateData } from '@/utils/websocket'

const authStore = useAuthStore()

// WebSocket 监听器引用，用于组件卸载时移除
const handlePositionUpdate = (data: PositionUpdateData) => {
  console.log('收到岗位更新:', data)
  // 更新本地职位列表
  const index = jobs.value.findIndex(j => j.id === data.positionId)
  if (index !== -1) {
    if (data.remainingQuota === 0) {
      // 岗位已满，从列表移除
      jobs.value.splice(index, 1)
      ElMessage.warning(`岗位"${data.positionName}"已招满，已从列表移除`)
    } else {
      // 更新岗位数据
      jobs.value[index] = {
        ...jobs.value[index],
        remainingQuota: data.remainingQuota,
        recruitedCount: data.recruitedCount,
        plannedRecruit: data.plannedRecruit
      }
    }
  }
}

const handlePositionDelete = (data: { positionId: number }) => {
  console.log('收到岗位删除:', data)
  const index = jobs.value.findIndex(j => j.id === data.positionId)
  if (index !== -1) {
    jobs.value.splice(index, 1)
    ElMessage.warning('该岗位已下架')
  }
}

// 从 auth store 获取学生 ID
const getStudentId = () => {
  return authStore.user?.id
}

const router = useRouter()

const searchKeyword = ref('')
const showFilter = ref(false)
const activeQuickFilter = ref('all')
const selectedIndustry = ref([])
const selectedCompany = ref([])
const selectedRegion = ref([])
const selectedSalary = ref('')
const selectedDuration = ref('')
const selectedInternshipBase = ref('')
const currentPage = ref(1)
const pageSize = ref(6)
const showJobDetailDialog = ref(false)
const selectedJob = ref(null)
const showApplicationDialog = ref(false)
const submitting = ref(false)
const applicationFormRef = ref(null)
const currentApplyingJob = ref(null)
const resumeList = ref([])
const certificateList = ref([])

const applicationForm = ref({
  studentName: '',
  studentNo: '',
  major: '',
  grade: '',
  phone: '',
  positionName: '',
  companyName: '',
  selfIntroduction: ''
})

const applicationRules = {
  studentName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  studentNo: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  major: [{ required: true, message: '请输入专业', trigger: 'blur' }],
  grade: [{ required: true, message: '请选择年级', trigger: 'change' }],
  phone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }]
}

const quickFilters = [
  { value: 'all', label: '全部' },
  { value: 'latest', label: '最新' },
  { value: 'hot', label: '热门' }
]

const industryOptions = ref([])

const companyOptions = ref([])

const regionOptions = ref([])

const salaryOptions = [
  { value: '0-3000', label: '3000元以下' },
  { value: '3000-5000', label: '3000-5000元' },
  { value: '5000-8000', label: '5000-8000元' },
  { value: '8000-12000', label: '8000-12000元' },
  { value: '12000+', label: '12000元以上' }
]

const durationOptions = [
  { value: '1-3', label: '1-3个月' },
  { value: '3-6', label: '3-6个月' },
  { value: '6-12', label: '6-12个月' },
  { value: '12+', label: '12个月以上' }
]

const internshipBaseOptions = [
  { value: 'all', label: '全部' },
  { value: 'national', label: '国家级实习基地' },
  { value: 'provincial', label: '省级实习基地' }
]

const cascaderProps = {
  expandTrigger: 'hover',
  checkStrictly: false,
  emitPath: true
}

const jobs = ref([])
const originalJobs = ref([])
const studentStatus = ref(0) // 学生实习状态：0=无offer, 1=待确认, 2=已确定, 3=实习中, 4=已结束

const filteredJobs = computed(() => {
  let result = [...jobs.value]
  
  // 先应用快捷筛选
  if (activeQuickFilter.value && activeQuickFilter.value !== 'all') {
    if (activeQuickFilter.value === 'latest') {
      // 显示最新职位（最近7天内发布的）
      const sevenDaysAgo = new Date()
      sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7)
      result = result.filter(job => new Date(job.publishTime) >= sevenDaysAgo)
      result.sort((a, b) => new Date(b.publishTime) - new Date(a.publishTime))
    } else if (activeQuickFilter.value === 'hot') {
      // 显示热门职位（收藏数或申请数高的）
      result.sort((a, b) => {
        const scoreA = (a.viewCount || 0) + (a.applyCount || 0)
        const scoreB = (b.viewCount || 0) + (b.applyCount || 0)
        return scoreB - scoreA
      })
    }
  }
  
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(job => 
      job.title.toLowerCase().includes(keyword) ||
      job.company.toLowerCase().includes(keyword)
    )
  }
  
  if (selectedIndustry.value.length > 0) {
    result = result.filter(job => selectedIndustry.value.includes(job.industry))
  }
  
  if (selectedCompany.value.length > 0) {
    result = result.filter(job =>
      selectedCompany.value.includes(job.companyId)
    )
  }
  
  if (selectedRegion.value.length > 0) {
    const regionStr = selectedRegion.value.join('-')
    result = result.filter(job => 
      job.location.toLowerCase().includes(regionStr.toLowerCase())
    )
  }
  
  if (selectedSalary) {
    result = result.filter(job => {
      if (!selectedSalary.value) return true
      const salaryRange = selectedSalary.value
      const jobSalary = job.salary
      return jobSalary.includes(salaryRange.split('-')[0])
    })
  }
  
  if (selectedDuration) {
    result = result.filter(job => {
      if (!selectedDuration.value) return true
      const durationRange = selectedDuration.value
      const jobDuration = job.duration
      return jobDuration.includes(durationRange.split('-')[0])
    })
  }
  
  if (selectedInternshipBase.value && selectedInternshipBase.value !== 'all') {
    result = result.filter(job => job.internshipBase === selectedInternshipBase.value)
  }
  
  return result
})

const totalJobs = computed(() => filteredJobs.value.length)

const paginatedJobs = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredJobs.value.slice(start, end)
})

const hasActiveFilters = computed(() => {
  return selectedIndustry.value.length > 0 ||
         selectedCompany.value.length > 0 ||
         selectedRegion.value.length > 0 ||
         selectedSalary.value ||
         selectedDuration.value ||
         (selectedInternshipBase.value && selectedInternshipBase.value !== 'all')
})

const activeFilterCount = computed(() => {
  let count = 0
  if (selectedIndustry.value.length > 0) count++
  if (selectedCompany.value.length > 0) count++
  if (selectedRegion.value.length > 0) count++
  if (selectedSalary.value) count++
  if (selectedDuration.value) count++
  if (selectedInternshipBase.value && selectedInternshipBase.value !== 'all') count++
  return count
})

const filterSummary = computed(() => {
  const summaries = []
  if (selectedIndustry.value.length > 0) {
    const industryNames = selectedIndustry.value.map(value => {
      const industry = industryOptions.find(opt => opt.value === value)
      return industry ? industry.label : value
    })
    summaries.push(`行业: ${industryNames.join(', ')}`)
  }
  if (selectedCompany.value.length > 0) {
    const companyNames = selectedCompany.value.map(value => {
      const company = companyOptions.value.find(opt => opt.value === value)
      return company ? company.label : value
    })
    summaries.push(`公司: ${companyNames.join(', ')}`)
  }
  if (selectedRegion.value.length > 0) {
    summaries.push(`地区: ${selectedRegion.value.join('-')}`)
  }
  if (selectedSalary.value) {
    const salary = salaryOptions.find(opt => opt.value === selectedSalary.value)
    summaries.push(`薪资: ${salary ? salary.label : selectedSalary.value}`)
  }
  if (selectedDuration.value) {
    const duration = durationOptions.find(opt => opt.value === selectedDuration.value)
    summaries.push(`时长: ${duration ? duration.label : selectedDuration.value}`)
  }
  if (selectedInternshipBase.value && selectedInternshipBase.value !== 'all') {
    const base = internshipBaseOptions.find(opt => opt.value === selectedInternshipBase.value)
    summaries.push(`实习基地: ${base ? base.label : selectedInternshipBase.value}`)
  }
  return summaries.join('; ') || '无'
})

const toggleFilter = (type, value) => {
  if (type === 'industry') {
    const index = selectedIndustry.value.indexOf(value)
    if (index > -1) {
      selectedIndustry.value.splice(index, 1)
    } else {
      selectedIndustry.value.push(value)
    }
  } else if (type === 'company') {
    const index = selectedCompany.value.indexOf(value)
    if (index > -1) {
      selectedCompany.value.splice(index, 1)
    } else {
      selectedCompany.value.push(value)
    }
  }
}

const resetFilters = () => {
  selectedIndustry.value = []
  selectedCompany.value = []
  selectedRegion.value = []
  selectedSalary.value = ''
  selectedDuration.value = ''
  selectedInternshipBase.value = ''
}

const handleSearch = () => {
  currentPage.value = 1
}

const handleQuickFilter = async (value) => {
  console.log('点击快捷筛选:', value)
  console.log('当前activeQuickFilter:', activeQuickFilter.value)
  activeQuickFilter.value = value
  currentPage.value = 1
  
  try {
    let response
    if (value === 'all') {
      response = await request.get(`/positions/all`)
    } else if (value === 'latest') {
      response = await request.get(`/positions/latest`)
    } else if (value === 'hot') {
      response = await request.get(`/positions/hot`)
    }
    
    if (response.code === 200) {
      jobs.value = response.data
      originalJobs.value = [...response.data]
      ElMessage.success('筛选成功')
    } else {
      ElMessage.error(response.data.msg || '筛选失败')
    }
  } catch (error) {
    console.error('筛选失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  }
}

const viewJobDetail = async (job) => {
  try {
    // 调用详情接口，会触发浏览次数+1
    const response = await request.get(`/positions/detail/${job.id}`)
    if (response.code === 200) {
      selectedJob.value = response.data
    } else {
      selectedJob.value = job
    }
  } catch (error) {
    console.error('获取职位详情失败:', error)
    selectedJob.value = job
  }
  showJobDetailDialog.value = true
}

const applyJob = async (job) => {
  // 初始化表单数据，从用户信息中获取默认值
  currentApplyingJob.value = job
  applicationForm.value = {
    studentName: authStore.user?.name || '',
    studentNo: authStore.user?.studentId || authStore.user?.username || '',
    major: authStore.user?.major || '',
    grade: authStore.user?.grade || '',
    phone: authStore.user?.phone || '',
    positionName: job.title || job.positionName,
    companyName: job.company,
    selfIntroduction: ''
  }
  // 加载学生的简历和证书列表
  await loadStudentMaterials()
  showApplicationDialog.value = true
}

const submitApplication = async () => {
  if (!applicationFormRef.value) return

  try {
    await applicationFormRef.value.validate()

    submitting.value = true

    const applicationData = {
      positionId: currentApplyingJob.value.id,
      companyId: currentApplyingJob.value.companyId,
      positionName: applicationForm.value.positionName,
      companyName: applicationForm.value.companyName,
      location: currentApplyingJob.value.location || '',
      salary: currentApplyingJob.value.salary || '',
      duration: currentApplyingJob.value.duration || '',
      phone: applicationForm.value.phone,
      studentName: applicationForm.value.studentName,
      studentNo: applicationForm.value.studentNo,
      major: applicationForm.value.major,
      grade: applicationForm.value.grade,
      selfIntroduction: applicationForm.value.selfIntroduction
    }

    const response = await studentJobApplicationService.create(applicationData)
    if (response.code === 200) {
      currentApplyingJob.value.isApplied = true
      showApplicationDialog.value = false
      ElMessage.success('申请提交成功！')
      // 关闭详情对话框
      showJobDetailDialog.value = false
    } else {
      ElMessage.error(response.message || '申请失败')
    }
  } catch (error) {
    console.error('申请失败:', error)
  } finally {
    submitting.value = false
  }
}

const cancelApply = (job) => {
  ElMessageBox.confirm('确定要取消申请这个职位吗？', '取消申请', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    job.isApplied = false
    ElMessage.success('已取消申请！')
  }).catch(() => {
    ElMessage.info('已取消操作')
  })
}

// 获取学生实习状态
const fetchStudentStatus = async () => {
  try {
    const response = await request.get('/student/internship-status')
    if (response.code === 200 && response.data) {
      studentStatus.value = response.data.status || 0
    }
  } catch (error) {
    console.error('获取学生实习状态失败:', error)
  }
}

// 加载学生简历和证书列表
const loadStudentMaterials = async () => {
  try {
    // 加载简历列表
    const resumeResponse = await request.get('/student/profile/resumes')
    if (resumeResponse.code === 200) {
      resumeList.value = resumeResponse.data || []
    }
    // 加载证书列表
    const certResponse = await request.get('/student/profile/certificates')
    if (certResponse.code === 200) {
      certificateList.value = certResponse.data || []
    }
  } catch (error) {
    console.error('加载学生材料失败:', error)
  }
}

// 格式化数组或字符串
const formatArrayOrString = (value) => {
  if (Array.isArray(value)) {
    return value.join('、')
  }
  return value || ''
}

// 标记已申请的职位
const markAppliedJobs = async () => {
  try {
    const response = await request.get('/student/applications')
    if (response.code === 200) {
      const appliedPositionIds = new Set(response.data.map(app => app.positionId))
      jobs.value.forEach(job => {
        if (appliedPositionIds.has(job.id)) {
          job.isApplied = true
        }
      })
      originalJobs.value.forEach(job => {
        if (appliedPositionIds.has(job.id)) {
          job.isApplied = true
        }
      })
    }
  } catch (error) {
    console.error('获取申请列表失败:', error)
  }
}

const toggleFavorite = async (job) => {
  try {
    const response = await positionService.toggleFavorite(job.id)
    if (response.code === 200) {
      job.isFavorite = !job.isFavorite
      ElMessage.success(job.isFavorite ? '已添加到收藏' : '已取消收藏')
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    console.error('收藏操作失败:', error)
    ElMessage.error('收藏操作失败')
  }
}

// 加载行业选项
const loadIndustryOptions = async () => {
  try {
    const response = await request.get(`/positions/options/industries`)
    if (response.code === 200) {
      industryOptions.value = response.data || []
    }
  } catch (error) {
    console.error('获取行业选项失败:', error)
  }
}

// 加载公司选项
const loadCompanyOptions = async () => {
  try {
    const response = await request.get(`/positions/options/companies`)
    if (response.code === 200) {
      companyOptions.value = response.data || []
    }
  } catch (error) {
    console.error('获取公司选项失败:', error)
  }
}

// 加载地区选项
const loadRegionOptions = async () => {
  try {
    const response = await request.get(`/positions/options/regions`)
    if (response.code === 200) {
      // 将省份列表转换为级联选择器格式
      regionOptions.value = (response.data || []).map(item => ({
        value: item.value,
        label: item.label,
        children: []
      }))
    }
  } catch (error) {
    console.error('获取地区选项失败:', error)
  }
}

// 加载职位列表
const loadPositions = async () => {
  try {
    const response = await request.get(`/positions/all`)
    if (response.code === 200) {
      jobs.value = response.data || []
      originalJobs.value = [...jobs.value]
      // 标记已申请的职位
      await markAppliedJobs()
    }
  } catch (error) {
    console.error('获取职位列表失败:', error)
  }
}

onMounted(async () => {
  console.log('Jobs component mounted')
  // 并行加载所有初始化数据，提升加载速度
  // 注意：fetchStudentStatus、loadIndustryOptions 等函数通过 ref 直接更新状态，不需要等待返回值
  Promise.all([
    fetchStudentStatus(),
    loadIndustryOptions(),
    loadCompanyOptions(),
    loadRegionOptions(),
    loadPositions()
  ])

  // 连接WebSocket并监听岗位更新
  const token = localStorage.getItem('token')
  if (token) {
    initAnnouncementWebSocket(token, () => {})
    onPositionUpdate(handlePositionUpdate)
    onPositionDelete(handlePositionDelete)
  }
})

onUnmounted(() => {
  // 【新增】移除WebSocket监听器
  offPositionUpdate(handlePositionUpdate)
  offPositionDelete(handlePositionDelete)
})
</script>

<style>
.jobs-container {
  min-height: 100%;
  background: #f5f7fa;
}

.jobs-bg-pattern {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
}

.bg-dot {
  position: absolute;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(64, 158, 255, 0.03) 0%, transparent 70%);
}

.bg-dot:nth-child(1) { top: -100px; right: -100px; }
.bg-dot:nth-child(2) { top: 30%; left: -150px; }
.bg-dot:nth-child(3) { bottom: 20%; right: -200px; }
.bg-dot:nth-child(4) { bottom: -150px; left: 30%; }
.bg-dot:nth-child(5) { top: 50%; right: 10%; }

/* 页面标题区域 - 统一为蓝绿色渐变背景 */
.page-header {
  margin-bottom: 24px;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  border-radius: 12px;
  padding: 24px 32px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  position: relative;
  overflow: hidden;
  z-index: 1;
}

.page-header::before {
  content: "";
  position: absolute;
  top: -50%;
  right: -10%;
  width: 300px;
  height: 300px;
  background: radial-gradient(
    circle,
    rgba(255, 255, 255, 0.1) 0%,
    transparent 70%
  );
  border-radius: 50%;
}

.page-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: white;
  margin: 0 0 8px 0;
  letter-spacing: 0.3px;
}

.page-header p {
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
  line-height: 1.5;
  margin: 0;
}

/* 搜索区域 */
.search-section {
  margin-bottom: 20px;
  position: relative;
  z-index: 1;
}

.search-filter-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  background: #f8fafc;
  padding: 16px 20px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
  border: 1px solid #e2e8f0;
}

.search-filter-container:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.filter-right {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.filter-center {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.quick-filters {
  display: flex;
  gap: 6px;
  align-items: center;
}

.quick-filter-item {
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  background: #f5f7fa;
  color: #606266;
  border: 1px solid #e4e7ed;
  white-space: nowrap;
}

.quick-filter-item:hover {
  background: #ecf5ff;
  border-color: #409EFF;
  color: #409EFF;
  transform: translateY(-1px);
}

.quick-filter-item.active {
  background: #409EFF;
  border-color: #409EFF;
  color: white;
}

.search-left {
  display: flex;
  gap: 10px;
  align-items: center;
  flex: 1;
}

.search-input {
  width: 100%;
  max-width: 450px;
  flex: none;
  height: 40px;
  border-radius: 8px;
  border: 1px solid #d1d5db;
  transition: all 0.3s ease;
  font-size: 14px;
  background: white;
}

.search-input:hover {
  border-color: #94a3b8;
}

.search-input:focus-within {
  border-color: #409EFF;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.1);
}

.search-button {
  height: 40px;
  padding: 0 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.3s ease;
  background: #409EFF;
  border: none;
  color: white;
}

.search-button:hover {
  background: #66b1ff;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.25);
}

.filter-toggle-button {
  height: 40px;
  padding: 0 18px;
  border-radius: 8px;
  border: 1px solid #d1d5db;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.3s ease;
  white-space: nowrap;
  background: white;
  color: #475569;
}

.filter-toggle-button:hover {
  border-color: #409EFF;
  color: #409EFF;
  background: #ecf5ff;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
}

.filter-toggle-button.active {
  background: #409EFF;
  border-color: #409EFF;
  color: white;
}

.filter-badge {
  margin-left: 6px;
  background-color: #409EFF;
}

/* 筛选面板 */
.filter-panel {
  background: #ffffff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 16px rgba(0, 0, 0, 0.08);
  margin-top: 16px;
  transition: all 0.3s ease;
  border: 1px solid #f0f2f5;
  position: relative;
  overflow: hidden;
}

.filter-panel::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 3px;
  background: linear-gradient(90deg, #409EFF, #67C23A);
  border-radius: 12px 12px 0 0;
}

.filter-panel:hover {
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.12);
  transform: translateY(-2px);
}

.filter-panel-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
  position: relative;
  z-index: 1;
}

.filter-row {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}

.filter-row.two-cols {
  justify-content: space-between;
}

.filter-group {
  flex: 1;
  min-width: 280px;
}

.filter-label {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-weight: 500;
  color: #303133;
  font-size: 14px;
}

.label-icon {
  color: #409EFF;
  font-size: 16px;
}

.filter-cascader {
  width: 100%;
}

.region-cascader {
  width: 100%;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  transition: all 0.3s ease;
}

.region-cascader:hover {
  border-color: #c0c4cc;
}

.region-cascader:focus-within {
  border-color: #409EFF;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.filter-options {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.filter-option {
  padding: 6px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 16px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  background-color: #ffffff;
  color: #606266;
}

.filter-option:hover {
  border-color: #409EFF;
  color: #409EFF;
}

.filter-option.active {
  background-color: #409EFF;
  border-color: #409EFF;
  color: #ffffff;
}

.filter-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e4e7ed;
}

.filter-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #606266;
}

.summary-label {
  font-weight: 500;
  color: #303133;
}

.summary-text {
  color: #409EFF;
  font-weight: 500;
}

.reset-button {
  border-radius: 8px;
  transition: all 0.3s ease;
}

.reset-button:hover {
  border-color: #409EFF;
  color: #409EFF;
}

/* 职位列表 - 网格布局 */
.jobs-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
  max-height: calc(100vh - 560px);
  overflow-y: auto;
  padding-right: 4px;
}

/* 职位卡片 */
.job-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
  display: flex;
  gap: 12px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  border: 1px solid transparent;
  background: #ffffff;
  height: 100%;
  flex-direction: column;
}

.job-card::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 3px;
  background: linear-gradient(90deg, #409EFF, #67C23A);
  border-radius: 16px 16px 0 0;
}

.job-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 32px rgba(64, 158, 255, 0.2);
  border-color: #409EFF;
  background: linear-gradient(135deg, #ffffff 0%, #f0f9ff 100%);
}

.job-card:hover .job-title {
  color: #409EFF;
}

.job-card:hover .favorite-icon {
  transform: scale(1.1);
}

.job-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.job-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
  width: 100%;
  margin-bottom: 8px;
}

.job-title-container {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  min-width: 0;
}

.job-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  transition: all 0.3s ease;
  margin: 0;
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.internship-base-badges {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
}

.base-badge {
  font-size: 12px;
  padding: 6px 16px;
  border-radius: 16px;
  font-weight: 600;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.base-badge.national {
  background: linear-gradient(135deg, #ff4d4f, #ff7875);
  border-color: #ff4d4f;
  color: white;
  box-shadow: 0 4px 12px rgba(255, 77, 79, 0.3);
}

.base-badge.provincial {
  background: linear-gradient(135deg, #faad14, #ffc53d);
  border-color: #faad14;
  color: white;
  box-shadow: 0 4px 12px rgba(250, 173, 20, 0.3);
}

.base-badge:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
}

.favorite-icon {
  font-size: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  color: #c0c4cc;
}

.favorite-icon:hover {
  color: #f7ba2a;
}

.favorite-icon.active {
  color: #f7ba2a;
}

.job-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  font-size: 12px;
  color: #606266;
  padding: 0;
  background: none;
  border-radius: 0;
  border: none;
  margin-bottom: 8px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
  transition: all 0.2s ease;
}

.info-item:hover {
  color: #409EFF;
  transform: translateX(4px);
}

.info-icon {
  font-size: 16px;
  color: #1890ff;
  width: 24px;
  text-align: center;
  flex-shrink: 0;
  filter: drop-shadow(0 2px 4px rgba(24, 144, 255, 0.4));
  background: linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%);
  padding: 4px;
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.2);
}

.job-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 0;
  flex: 1;
  align-items: center;
}

.job-tags-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}

.job-tag {
  background: linear-gradient(135deg, #ecf5ff 0%, #d9ecff 100%);
  color: #409EFF;
  border: 1px solid #b3d8ff;
  border-radius: 16px;
  padding: 2px 8px;
  font-size: 10px;
  font-weight: 500;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
}

.job-tag:hover {
  background: linear-gradient(135deg, #409EFF 0%, #66b1ff 100%);
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.4);
  border-color: #409EFF;
}

.job-footer {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: auto;
  padding: 0;
  background: none;
  border-radius: 0;
  border: none;
  box-shadow: none;
  justify-content: flex-end;
  flex-shrink: 0;
}

.view-detail-button {
  border-radius: 6px;
  transition: all 0.3s ease;
  font-weight: 500;
  padding: 6px 12px;
  font-size: 12px;
  min-width: 70px;
  border: 1px solid #409EFF;
  color: #409EFF;
  background: white;
  position: relative;
  overflow: hidden;
}

.view-detail-button::before {
  content: "";
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(64, 158, 255, 0.2), transparent);
  transition: left 0.6s ease;
}

.view-detail-button:hover::before {
  left: 100%;
}

.view-detail-button:hover {
  border-color: #409EFF;
  color: #409EFF;
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.3);
  background: rgba(64, 158, 255, 0.05);
}

.apply-button {
  border-radius: 6px;
  transition: all 0.3s ease;
  font-weight: 600;
  padding: 6px 12px;
  background: linear-gradient(135deg, #409EFF, #67C23A);
  border: none;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.3);
  font-size: 12px;
  min-width: 80px;
  text-align: center;
  position: relative;
  overflow: hidden;
  color: white;
}

.apply-button::before {
  content: "";
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
  transition: left 0.6s ease;
}

.apply-button:hover::before {
  left: 100%;
}

.apply-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(64, 158, 255, 0.4);
  background: linear-gradient(135deg, #66b1ff, #85ce61);
}

.cancel-apply-button {
  border-radius: 6px;
  transition: all 0.3s ease;
  font-weight: 600;
  padding: 6px 12px;
  background: linear-gradient(135deg, #faad14, #ffc53d);
  border: none;
  box-shadow: 0 4px 16px rgba(250, 173, 20, 0.3);
  font-size: 12px;
  min-width: 80px;
  text-align: center;
  position: relative;
  overflow: hidden;
  color: white;
}

.cancel-apply-button::before {
  content: "";
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
  transition: left 0.6s ease;
}

.cancel-apply-button:hover::before {
  left: 100%;
}

.cancel-apply-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(250, 173, 20, 0.4);
  background: linear-gradient(135deg, #ffc53d, #ffd666);
}

/* 分页区域 */
.pagination-section {
  margin-top: 32px;
  display: flex;
  justify-content: center;
  position: relative;
  z-index: 1;
}

.pagination {
  background: #ffffff;
  padding: 16px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  border: 1px solid #e2e8f0;
}

/* 职位详情弹窗 */
.job-detail-dialog :deep(.el-dialog) {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.job-detail-dialog :deep(.el-dialog__header) {
  padding: 0;
  border-bottom: none;
  background: transparent;
}

.job-detail-dialog :deep(.el-dialog__title) {
  display: none;
}

.job-detail-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
  font-size: 20px;
  top: 15px;
  right: 20px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  width: 30px;
  height: 30px;
  transition: all 0.3s ease;
}

.job-detail-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  background: rgba(255, 255, 255, 0.3);
}

.job-detail-dialog :deep(.el-dialog__body) {
  padding: 0;
  background-color: #ffffff;
}

.job-detail-dialog :deep(.el-dialog__footer) {
  padding: 0;
  border-top: none;
  background: transparent;
}

.job-detail-content {
  padding: 0;
  background: #ffffff;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  padding: 24px 28px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  padding: 24px 28px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.detail-title {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-title h3 {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
  letter-spacing: 0.5px;
}

.internship-base-badges {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.base-badge {
  font-weight: 600;
  border-radius: 6px;
  padding: 4px 10px;
}

.base-badge.national {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  color: white;
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.25);
}

.base-badge.provincial {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  color: white;
  box-shadow: 0 2px 8px rgba(245, 158, 11, 0.25);
}

.detail-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.favorite-icon {
  font-size: 24px;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.3s ease;
  padding: 8px;
  border-radius: 8px;
  background: #f8fafc;
}

.favorite-icon:hover {
  color: #f59e0b;
  background: #fef3c7;
  transform: scale(1.1);
}

.favorite-icon.active {
  color: #f59e0b;
}

.detail-section {
  margin-bottom: 20px;
  padding: 0 28px;
}

.section-title {
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 16px 0;
  padding-bottom: 8px;
  border-bottom: 2px solid #e2e8f0;
  position: relative;
}

.section-title::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 0;
  width: 60px;
  height: 2px;
  background: linear-gradient(90deg, #409eff 0%, #67c23a 100%);
  border-radius: 1px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}

.info-grid .info-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: white;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
}

.info-grid .info-item:hover {
  border-color: #409eff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.1);
  transform: translateY(-2px);
}

.info-grid .info-item el-icon {
  font-size: 18px;
  color: #409eff;
  width: 28px;
  text-align: center;
  flex-shrink: 0;
  background: linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%);
  padding: 6px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}

.info-grid .info-item .info-label {
  font-weight: 600;
  color: #64748b;
  min-width: 70px;
  font-size: 14px;
}

.info-grid .info-item .info-value {
  font-weight: 500;
  color: #1e293b;
  flex: 1;
  font-size: 14px;
}

.detail-description {
  font-size: 14px;
  line-height: 1.8;
  color: #475569;
  margin: 0;
  padding: 16px 20px;
  background: white;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
}

.contact-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 20px;
  background: white;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
}

.contact-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
}

.contact-item:hover {
  background: #f0f9ff;
  border-color: #409eff;
  transform: translateX(4px);
}

.contact-label {
  font-weight: 600;
  color: #64748b;
  min-width: 70px;
  font-size: 14px;
}

.contact-value {
  font-weight: 500;
  color: #1e293b;
  flex: 1;
  font-size: 14px;
}

.stats-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 20px;
  background: white;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
}

.stat-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
}

.stat-item:hover {
  background: #f0f9ff;
  border-color: #409eff;
  transform: translateX(4px);
}

.stat-label {
  font-weight: 600;
  color: #64748b;
  font-size: 14px;
}

.stat-value {
  font-weight: 500;
  color: #1e293b;
  font-size: 15px;
}

.detail-footer {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding: 20px 28px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-top: 1px solid #e2e8f0;
}

.detail-apply-button {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  border: none;
  color: white;
  font-weight: 600;
  padding: 12px 32px;
  border-radius: 10px;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.25);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.detail-apply-button:hover {
  background: linear-gradient(135deg, #67c23a 0%, #409eff 100%);
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.35);
}

.detail-cancel-apply-button {
  background: white;
  border: 2px solid #e2e8f0;
  color: #64748b;
  font-weight: 600;
  padding: 12px 32px;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.detail-cancel-apply-button:hover {
  background: #f8fafc;
  border-color: #409eff;
  color: #409eff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2);
}

.contact-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.contact-label {
  font-weight: 500;
  color: #606266;
  min-width: 80px;
}

.contact-value {
  font-weight: 400;
  color: #303133;
  flex: 1;
}

.stats-info {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stat-label {
  font-weight: 500;
  color: #606266;
}

.stat-value {
  font-weight: 400;
  color: #303133;
}

.detail-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #e4e7ed;
}

.detail-apply-button {
  border-radius: 8px;
  transition: all 0.3s ease;
}

.detail-cancel-apply-button {
  border-radius: 8px;
  transition: all 0.3s ease;
}

/* 响应式布局 */
@media (min-width: 1400px) {
  .jobs-list {
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
  }
}

@media (min-width: 1200px) and (max-width: 1399px) {
  .jobs-list {
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
  }
}

@media (min-width: 992px) and (max-width: 1199px) {
  .jobs-list {
    grid-template-columns: repeat(3, 1fr);
    gap: 14px;
  }
}

@media (min-width: 768px) and (max-width: 991px) {
  .jobs-list {
    grid-template-columns: repeat(2, 1fr);
    gap: 14px;
  }
}

@media (max-width: 767px) {
  .jobs-list {
    grid-template-columns: 1fr;
    gap: 14px;
  }
  
  .job-card {
    padding: 14px;
  }
  
  .job-title {
    font-size: 16px;
  }
  
  .job-info {
    gap: 16px;
    font-size: 13px;
  }
  
  .job-tags-footer {
    flex-direction: column;
    align-items: stretch;
  }
  
  .job-footer {
    flex-wrap: wrap;
    justify-content: stretch;
  }
  
  .job-footer .el-button {
    flex: 1;
    min-width: auto;
  }
}

@media (max-width: 480px) {
  .jobs-list {
    gap: 12px;
  }
  
  .job-card {
    padding: 12px;
  }
  
  .job-left {
    padding-right: 12px;
  }
  
  .company-logo {
    width: 60px;
    height: 60px;
    font-size: 24px;
  }
  
  .job-title {
    font-size: 15px;
  }
  
  .job-info {
    gap: 12px;
    font-size: 12px;
  }
  
  .job-tags {
    gap: 8px;
  }
  
  .job-tag {
    font-size: 11px;
    padding: 3px 10px;
  }
  
  .search-filter-container {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
    padding: 14px 16px;
  }
  
  .search-left {
    flex-direction: column;
    gap: 10px;
  }
  
  .search-input {
    width: 100%;
  }
  
  .search-button {
    width: 100%;
  }
  
  .filter-right {
    width: 100%;
  }
  
  .filter-toggle-button {
    width: 100%;
  }
  
  .filter-group {
    min-width: 100%;
  }
  
  .filter-row {
    flex-direction: column;
    gap: 16px;
  }
}

/* 申请表单对话框样式 */
.application-dialog :deep(.el-dialog) {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.application-dialog :deep(.el-dialog__header) {
  padding: 0;
  border-bottom: none;
}

.application-dialog :deep(.el-dialog__title) {
  display: none;
}

.application-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
  font-size: 20px;
  top: 15px;
  right: 20px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  width: 30px;
  height: 30px;
  transition: all 0.3s ease;
}

.application-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  background: rgba(255, 255, 255, 0.3);
}

.application-dialog :deep(.el-dialog__body) {
  padding: 0;
  background-color: #ffffff;
}

.application-dialog :deep(.el-dialog__footer) {
  display: none;
}

.application-dialog .header-section {
  background: linear-gradient(90deg, #1e88e5 0%, #4caf50 100%);
  padding: 20px;
  margin-bottom: 0;
  border-radius: 0;
  box-shadow: none;
}

.application-dialog .header-title {
  color: white;
  font-size: 24px;
  font-weight: 600;
  text-align: center;
  margin: 0;
}

.application-dialog .form-content {
  padding: 20px;
  max-height: 55vh;
  overflow-y: auto;
}

.application-dialog .form-module {
  background-color: transparent;
  border: none;
  border-radius: 0;
  padding: 0;
  margin-bottom: 24px;
  box-shadow: none;
}

.application-dialog .module-header {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.application-dialog .module-icon-container {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
}

.application-dialog .module-icon {
  color: white;
  font-size: 14px;
}

.application-dialog .module-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.application-dialog .form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.application-dialog .form-actions {
  display: flex;
  justify-content: center;
  gap: 20px;
  padding: 20px;
  background-color: #ffffff;
  border-top: 1px solid #f0f0f0;
}

.application-dialog .submit-btn {
  background: linear-gradient(90deg, #1e88e5 0%, #4caf50 100%) !important;
  color: #ffffff !important;
  border: none !important;
  border-radius: 4px !important;
  padding: 8px 20px !important;
  transition: all 0.2s ease !important;
}

.application-dialog .submit-btn:hover {
  background: linear-gradient(90deg, #1565c0 0%, #388e3c 100%) !important;
}

.application-dialog .reset-btn {
  background: #ffffff !important;
  color: #64748b !important;
  border: 1px solid #e2e8f0 !important;
  border-radius: 4px !important;
  padding: 8px 20px !important;
}

.application-dialog .reset-btn:hover {
  border-color: #409eff !important;
  color: #409eff !important;
}

.application-dialog :deep(.el-form-item) {
  margin-bottom: 16px;
}

.application-dialog :deep(.el-form-item__label) {
  font-size: 14px;
  color: #334155;
  font-weight: 500;
}

.application-dialog :deep(.el-input__wrapper),
.application-dialog :deep(.el-select__wrapper),
.application-dialog :deep(.el-textarea__inner) {
  border-radius: 4px;
  border: 1px solid #e2e8f0;
  padding: 6px 12px;
  background-color: #ffffff;
  box-shadow: none;
  transition: all 0.2s ease;
}

.application-dialog :deep(.el-input__wrapper:focus-within),
.application-dialog :deep(.el-select__wrapper:focus-within),
.application-dialog :deep(.el-textarea__inner:focus) {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}

.application-dialog :deep(.el-input.is-disabled .el-input__inner) {
  background-color: #f5f7fa;
  color: #909399;
}

/* 申请材料列表样式 */
.application-dialog .material-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px 16px;
  background-color: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.application-dialog .no-material-tip {
  color: #909399;
  font-size: 13px;
  text-align: center;
  padding: 16px 0;
}

.application-dialog .material-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background-color: #ffffff;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  transition: all 0.2s ease;
}

.application-dialog .material-item:hover {
  border-color: #409EFF;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
}

.application-dialog .material-icon {
  font-size: 18px;
  color: #409EFF;
  background: linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%);
  padding: 6px;
  border-radius: 6px;
}

.application-dialog .material-name {
  flex: 1;
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>