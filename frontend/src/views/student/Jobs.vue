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
            <el-button @click="showFilter = !showFilter" class="filter-toggle-button" :class="{ active: showFilter }">
              <el-icon><Filter /></el-icon>
              筛选
              <el-badge v-if="hasActiveFilters" :value="activeFilterCount" :max="99" class="filter-badge" />
            </el-button>
            <el-button v-if="!agentWorkspace.capabilities?.enabled && agentComparedJobs.length" type="success" plain @click="showLocalCompareDialog = true">
              查看对比（{{ agentComparedJobs.length }}）
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
                  <el-select
                    v-model="selectedCompany"
                    multiple
                    filterable
                    collapse-tags
                    collapse-tags-tooltip
                    placeholder="请选择公司"
                    clearable
                    style="width: 100%"
                  >
                    <el-option
                      v-for="option in companyOptions"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    />
                  </el-select>
                </div>
              </div>

              <div class="filter-row">
                <div class="filter-group">
                  <div class="filter-label">
                    <el-icon class="label-icon"><OfficeBuilding /></el-icon>
                    <span>岗位类别</span>
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
                      @click="selectedInternshipBase = selectedInternshipBase === option.value ? '' : option.value"
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
                      @click="selectedSalary = selectedSalary === option.value ? '' : option.value"
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

      <div class="table-card">
      <el-table
        :data="paginatedJobs"
        class="data-table"
        row-key="id"
        :row-class-name="tableRowClassName"
        style="width: 100%"
      >
        <el-table-column label="职位名称" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="job-title-text">{{ row.industryName ? row.industryName + ' - ' + row.title : row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="company" label="企业" min-width="150" show-overflow-tooltip />
        <el-table-column label="剩余名额" width="120" align="center">
          <template #default="{ row }">
            <span class="quota-num">{{ row.remainingQuota }}</span> 个
            <div v-if="row.remainingQuota === 0" class="quota-warn">仍可申请</div>
          </template>
        </el-table-column>
        <el-table-column label="企业评分" width="150" align="center">
          <template #default="{ row }">
            <span v-if="Number(row.companyRatingCount) > 0 && row.companyRatingAverage != null">{{ Number(row.companyRatingAverage).toFixed(1) }} ★（{{ row.companyRatingCount }}人）</span>
            <span v-else class="unrated-company">暂无评分</span>
          </template>
        </el-table-column>
        <el-table-column prop="location" label="地点" width="120" show-overflow-tooltip />
        <el-table-column prop="salary" label="薪资" width="120" align="center" show-overflow-tooltip />
        <el-table-column label="标签" min-width="180">
          <template #default="{ row }">
            <div class="tags-cell">
              <el-tag v-if="row.internshipBase && row.internshipBase !== 'none'" size="small" type="success">实习基地</el-tag>
              <el-tag v-if="row.isAlumniUnit === 1" size="small" type="warning">校友单位</el-tag>
              <el-tag v-for="tag in filterJobTags(row.tags)" :key="tag" size="small" class="job-tag">{{ tag }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="申请状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.id === confirmedPositionId" type="success">当前实习</el-tag>
            <el-tag v-else-if="!row.isApplied" type="info">未申请</el-tag>
            <el-tag v-else-if="row.applicationStatus === 'pending'" type="warning">待审核</el-tag>
            <el-tag v-else-if="row.applicationStatus === 'pending_interview'" type="warning">待面试</el-tag>
            <el-tag v-else-if="row.applicationStatus === 'interview'" type="warning">面试中</el-tag>
            <el-tag v-else-if="row.applicationStatus === 'interview_passed'" type="success">面试通过</el-tag>
            <el-tag v-else-if="row.applicationStatus === 'interview_failed'" type="danger">面试未通过</el-tag>
            <el-tag v-else-if="row.applicationStatus === 'approved'" type="success">已通过</el-tag>
            <el-tag v-else-if="row.applicationStatus === 'rejected'" type="danger">已拒绝</el-tag>
            <el-tag v-else-if="row.applicationStatus === 'withdrawn'" type="info">已撤回</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" align="center" fixed="right">
          <template #default="{ row }">
            <div class="action-cell">
              <el-icon class="favorite-icon" :class="{ active: row.isFavorite }" @click.stop="toggleFavorite(row)">
                <StarFilled v-if="row.isFavorite" />
                <Star v-else />
              </el-icon>
              <el-button v-if="agentWorkspace.capabilities?.enabled" size="small" plain :type="agentComparedJobs.includes(String(row.id)) ? 'success' : 'default'" :aria-pressed="agentComparedJobs.includes(String(row.id))" @click.stop="toggleAgentCompare(row)">
                {{ agentComparedJobs.includes(String(row.id)) ? '已加入 AI 比较' : 'AI 比较' }}
              </el-button>
              <el-button v-else size="small" plain :type="agentComparedJobs.includes(String(row.id)) ? 'success' : 'default'" :aria-pressed="agentComparedJobs.includes(String(row.id))" @click.stop="toggleLocalCompare(row)">
                {{ agentComparedJobs.includes(String(row.id)) ? '已加入对比' : '加入对比' }}
              </el-button>
              <el-button type="primary" plain size="small" @click.stop="viewJobDetail(row)">查看详情</el-button>
              <el-button v-if="row.id !== confirmedPositionId && !row.isApplied" type="primary" size="small" @click.stop="applyJob(row)">立即投递</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无符合条件的职位" />
        </template>
      </el-table>

      <div class="pagination-section">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="totalJobs"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          class="pagination"
        />
      </div>
      </div>

      <el-dialog
        v-model="showLocalCompareDialog"
        title="岗位对比"
        width="900px"
        :append-to-body="true"
        :lock-scroll="true"
      >

        <el-table :data="localCompareRows" border style="width: 100%">
          <el-table-column label="岗位">
            <template #default="{ row }">{{ row.positionName || row.title }}</template>
          </el-table-column>
          <el-table-column label="企业">
            <template #default="{ row }">{{ row.company }}</template>
          </el-table-column>
          <el-table-column label="地点">
            <template #default="{ row }">{{ row.location }}</template>
          </el-table-column>
          <el-table-column label="薪资">
            <template #default="{ row }">{{ row.salary || '面议' }}</template>
          </el-table-column>
          <el-table-column label="剩余名额" width="90">
            <template #default="{ row }">{{ row.remainingQuota == null ? '—' : row.remainingQuota }}</template>
          </el-table-column>
          <el-table-column label="企业推荐" width="90">
            <template #default="{ row }">{{ row.companyRatingAverage == null ? '—' : Number(row.companyRatingAverage).toFixed(1) }}</template>
          </el-table-column>
        </el-table>
        <template #footer>
          <el-button @click="agentComparedJobs = []; showLocalCompareDialog = false">清空选择</el-button>
          <el-button type="primary" @click="showLocalCompareDialog = false">关闭</el-button>
        </template>
      </el-dialog>
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
              <h3>{{ selectedJob.industryName ? selectedJob.industryName + ' - ' + selectedJob.title : selectedJob.title }}</h3>
              <div class="internship-base-badges">
                <el-tag v-if="selectedJob.internshipBase && selectedJob.internshipBase !== 'none'" size="small" type="success" class="base-badge">
                  实习基地
                </el-tag>
                <el-tag v-if="selectedJob.isAlumniUnit === 1" size="small" type="warning" class="base-badge">
                  校友单位
                </el-tag>
              </div>
            </div>
            <div class="detail-actions">
              <AgentContextAction prompt="这个岗位适合我吗？" />
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
                <el-icon><OfficeBuilding /></el-icon>
                <span class="info-label">合作模式：</span>
                <span class="info-value">{{ formatCooperationMode(selectedJob.cooperationMode) }}</span>
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
                <span class="info-label">岗位类别：</span>
                <span class="info-value">{{ selectedJob.industryName }}</span>
              </div>
            </div>
          </div>

          <el-collapse
            v-if="selectedJob.logo || selectedJob.companyIntroduction || companyPhotos.length || companyVideos.length"
            v-model="companyInfoActive"
            class="detail-section job-company-collapse"
          >
            <el-collapse-item name="companyInfo">
              <template #title>
                <h4 class="section-title">企业信息</h4>
              </template>
            <div v-if="selectedJob.logo || selectedJob.companyIntroduction" class="company-card-header">
              <img v-if="selectedJob.logo" :src="selectedJob.logo" class="company-logo-large" alt="企业Logo" />
              <div class="company-card-body">
                <div class="company-card-title">
                  <span class="company-name-text">{{ selectedJob.company }}</span>
                </div>
                <p v-if="selectedJob.companyIntroduction" class="company-intro-text">{{ selectedJob.companyIntroduction }}</p>
              </div>
            </div>
            <template v-if="companyPhotos.length">
              <h5 class="sub-section-title">企业照片</h5>
              <div class="company-photo-grid">
                <el-image
                  v-for="(url, i) in companyPhotos"
                  :key="i"
                  :src="url"
                  class="company-photo"
                  fit="cover"
                  :preview-src-list="companyPhotos"
                  :initial-index="i"
                  preview-teleported
                />
              </div>
            </template>
            <template v-if="companyVideos.length">
              <h5 class="sub-section-title">企业视频</h5>
              <div class="company-video-list">
                <video v-for="(url, i) in companyVideos" :key="i" :src="url" controls class="company-video" />
              </div>
            </template>
            </el-collapse-item>
          </el-collapse>

          <div class="detail-section">
            <h4 class="section-title">职位描述</h4>
            <p v-if="selectedJob.description" class="detail-description">{{ selectedJob.description }}</p>
          </div>

          <div class="detail-section">
            <h4 class="section-title">任职要求</h4>
            <p v-if="formatArrayOrString(selectedJob.requirements)" class="detail-description">{{ formatArrayOrString(selectedJob.requirements) }}</p>
          </div>

          <div class="detail-section">
            <h4 class="section-title">联系方式</h4>
            <div v-if="selectedJob.contactPerson || selectedJob.contactPhone || selectedJob.contactEmail" class="contact-info">
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

          <div
            v-if="selectedJob.id === confirmedPositionId || !selectedJob.isApplied || !!selectedJob.applicationStatus"
            class="detail-footer"
          >
              <el-tag v-if="selectedJob.id === confirmedPositionId" type="success">
                当前实习
              </el-tag>
              <el-button
                v-else-if="!selectedJob.isApplied"
                type="primary"
                size="large"
                @click="applyJob(selectedJob)"
                class="detail-apply-button"
              >
                立即投递
              </el-button>
              <!-- 待审核状态 -->
              <el-tag v-else-if="selectedJob.applicationStatus === 'pending'" type="warning">
                待审核
              </el-tag>
              <!-- 待面试 -->
              <el-tag v-else-if="selectedJob.applicationStatus === 'pending_interview'" type="warning">
                待面试
              </el-tag>
              <!-- 面试中 -->
              <el-tag v-else-if="selectedJob.applicationStatus === 'interview'" type="warning">
                面试中
              </el-tag>
              <!-- 面试通过：显示状态，不显示取消 -->
              <el-tag v-else-if="selectedJob.applicationStatus === 'interview_passed'" type="success">
                面试通过
              </el-tag>
              <!-- 面试未通过：显示状态 -->
              <el-tag v-else-if="selectedJob.applicationStatus === 'interview_failed'" type="danger">
                面试未通过
              </el-tag>
              <!-- 已通过：显示状态，不显示取消 -->
              <el-tag v-else-if="selectedJob.applicationStatus === 'approved'" type="success">
                已通过
              </el-tag>
              <!-- 已拒绝：显示状态 -->
              <el-tag v-else-if="selectedJob.applicationStatus === 'rejected'" type="danger">
                已拒绝
              </el-tag>
              <!-- 已撤回：显示状态 -->
              <el-tag v-else-if="selectedJob.applicationStatus === 'withdrawn'" type="info">
                已撤回
              </el-tag>
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

          <el-form :model="applicationForm" ref="applicationFormRef" label-width="100px">
            <div class="form-content">
              <div class="form-module">
                <div class="module-header">
                  <div class="module-icon-container">
                    <el-icon class="module-icon"><Briefcase /></el-icon>
                  </div>
                  <h2 class="module-title">申请信息</h2>
                </div>
                <div class="form-grid">
                  <el-form-item label="应聘单位">
                    <el-input :model-value="applicationForm.companyName" disabled />
                  </el-form-item>
                  <el-form-item label="岗位类别">
                    <el-input :model-value="applicationForm.industryName" disabled />
                  </el-form-item>
                  <el-form-item label="岗位名称">
                    <el-input :model-value="applicationForm.positionName" disabled />
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

              <!-- 上传简历和证书模块 -->
              <div class="form-module">
                <div class="module-header">
                  <div class="module-icon-container">
                    <el-icon class="module-icon"><Upload /></el-icon>
                  </div>
                  <h2 class="module-title">上传简历和证书</h2>
                </div>
                <div class="material-list">
                  <div v-if="resumeList.length === 0 && certificateList.length === 0" class="no-material-tip">
                    <span>暂无可用的申请材料，请在个人中心上传简历和证书</span>
                    <el-link type="primary" :underline="false" class="material-upload-link" @click="goToMaterialManagement">
                      去个人中心上传
                    </el-link>
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
  </div>
</template>

<script setup lang="ts">
import AgentContextAction from '@/components/agent/AgentContextAction.vue'
import { useAgentPageContext } from "@/agent/useAgentPageContext"
import { useAgentStore } from "@/agent/store"

import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
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
import { onPositionUpdate, onPositionDelete, offPositionUpdate, offPositionDelete } from '@/utils/websocket'
import { getTokenKey } from '@/store/auth/storage'
// websocket 内的 PositionUpdateData 未导出，此处按其真实形态本地声明（不改运行时）
interface PositionUpdateData {
  positionId: number
  positionName?: string
  remainingQuota?: number
  plannedRecruit?: number
  recruitedCount?: number
  status?: string
  companyId?: number
}
import eventBus from '@/utils/eventBus'
import { checkStudentProfileReadiness, profileIncompleteMessage } from '@/utils/studentProfileReadiness.js'
import { getMonthlySalaryRangeK } from '@/utils/salaryRange.js'
import { useFormContext } from '@/agent/formContext'
import type { FormFieldDescriptor } from '@/agent/types'

const authStore = useAuthStore()

// WebSocket 监听器引用，用于组件卸载时移除
const handlePositionUpdate = (data: PositionUpdateData) => {
  console.log('收到岗位更新:', data)
  // 更新本地职位列表
  const index = jobs.value.findIndex(j => j.id === data.positionId)
  if (data.status === 'paused') {
    if (index !== -1) {
      jobs.value.splice(index, 1)
    }
    return
  }
  if (index !== -1) {
    // 名额仅在企业确认入职时占用；名额为 0 的岗位仍允许学生提交申请。
    jobs.value[index] = {
      ...jobs.value[index],
      remainingQuota: data.remainingQuota,
      recruitedCount: data.recruitedCount,
      plannedRecruit: data.plannedRecruit
    }
  } else {
    // 新职位或恢复招聘：调用接口获取完整列表以包含所有信息
    loadPositions()
  }
}

const handlePositionDelete = (data: { positionId: number }) => {
  console.log('收到岗位删除:', data)
  const index = jobs.value.findIndex(j => j.id === data.positionId)
  if (index !== -1) {
    jobs.value.splice(index, 1)
  }
}

// 处理其他页面提交的申请（从收藏页面等）
const handleApplicationSubmitted = (data: { positionId: number }) => {
  console.log('收到申请提交事件:', data)
  const job = jobs.value.find(j => j.id === data.positionId)
  if (job) {
    job.isApplied = true
    // 不再显示提示，因为提交申请时已经显示过
  }
}

// 处理其他页面取消的申请（从收藏页面等）
const handleApplicationCancelled = (data: { positionId: number }) => {
  console.log('收到取消申请事件:', data)
  const job = jobs.value.find(j => j.id === data.positionId)
  if (job) {
    job.isApplied = false
    ElMessage.success('已取消申请！')
  }
}

// 从 auth store 获取学生 ID
const getStudentId = () => {
  return authStore.user?.id
}

const router = useRouter()
const route = useRoute()

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
const pageSize = ref(10)
const showJobDetailDialog = ref(false)
// 职位详情 - 企业信息板块折叠状态（默认展开）
const companyInfoActive = ref(['companyInfo'])
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
  industryName: '',
  positionName: '',
  companyName: '',
  selfIntroduction: ''
})

// ===== 智能填表：岗位申请弹窗注册到 AI 侧栏（只写响应式 model，不触发提交） =====
// 未覆盖字段说明：companyName/industryName/positionName 为 disabled 展示、简历与证书为个人中心已上传材料展示——均无需 AI 填写
const formContext = useFormContext()
const applicationFillFields = (): FormFieldDescriptor[] => [
  { prop: 'selfIntroduction', label: '自我简介', type: 'textarea' }
]
watch(showApplicationDialog, (open) => { if (!open) formContext.unregister('jobs:apply') })
onUnmounted(() => { formContext.unregister('jobs:apply') })

const quickFilters = [
  { value: 'all', label: '全部' },
  { value: 'latest', label: '最新' },
  { value: 'hot', label: '热门' },
  { value: 'recommended', label: '推荐' }
]

const industryOptions = ref([])

const companyOptions = ref([])

const regionOptions = ref([])

const salaryOptions = [
  { value: '0-3', label: '3K以下' },
  { value: '3-5', label: '3-5K' },
  { value: '5-8', label: '5-8K' },
  { value: '8-12', label: '8-12K' },
  { value: '12+', label: '12K以上' }
]

const durationOptions = [
  { value: '1-3', label: '1-3个月' },
  { value: '3-6', label: '3-6个月' },
  { value: '6-12', label: '6-12个月' },
  { value: '12+', label: '12个月以上' }
]

const internshipBaseOptions = [
  { value: 'all', label: '全部' },
  { value: 'yes', label: '是' },
  { value: 'no', label: '否' }
]

const cascaderProps = {
  expandTrigger: 'hover',
  checkStrictly: false,
  emitPath: true
}

const jobs = ref<Array<{
  id?: number | string
  title?: string
  positionName?: string
  company?: string
  companyId?: number | string
  location?: string
  salary?: string
  duration?: string
  internshipBase?: string
  industryName?: string
  industry?: string
  companyRatingCount?: number | string
  companyRatingAverage?: number | string
  remainingQuota?: number
  recruitedCount?: number
  plannedRecruit?: number
  isApplied?: boolean
  applicationStatus?: string | null
  isFavorite?: boolean
  [key: string]: unknown
}>>([])
const originalJobs = ref<Array<{
  id?: number | string
  isApplied?: boolean
  applicationStatus?: string | null
  [key: string]: unknown
}>>([])
const confirmedPositionId = ref(null) // 学生已确认的职位ID
const highlightedJobId = ref<number | string | null>(null)
let highlightTimer = 0

const tableRowClassName = ({ row }: { row: { id?: number | string } }) => {
  return row.id === highlightedJobId.value ? 'highlight-row' : ''
}

const highlightTableRow = (jobId: number) => {
  highlightedJobId.value = jobId
  clearTimeout(highlightTimer)
  highlightTimer = window.setTimeout(() => { highlightedJobId.value = null }, 5000)
  nextTick(() => {
    const row = document.querySelector('.el-table__row.highlight-row')
    if (row) row.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
}
const ACTIVE_APPLICATION_STATUSES = new Set([
  'pending',
  'approved',
  'interview',
  'pending_interview',
  'interview_passed'
])

const filteredJobs = computed(() => {
  let result = [...jobs.value]

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
  
  if (selectedSalary.value) {
    result = result.filter(job => {
      const jobRange = getMonthlySalaryRangeK(job)
      if (!jobRange) return false
      const filterValue = selectedSalary.value
      if (filterValue === '12+') {
        return jobRange.min >= 12
      }
      const parts = filterValue.split('-')
      const filterMin = parseInt(parts[0])
      const filterMax = parseInt(parts[1])
      return jobRange.min <= filterMax && jobRange.max >= filterMin
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
    result = result.filter(job => {
      const hasBase = job.internshipBase && job.internshipBase !== 'none'
      return selectedInternshipBase.value === 'yes' ? hasBase : !hasBase
    })
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
      const industry = industryOptions.value.find(opt => opt.value === value)
      return industry ? industry.label : value
    })
    summaries.push(`岗位类别: ${industryNames.join(', ')}`)
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
    } else if (value === 'recommended') {
      response = await request.get(`/positions/recommended`)
    }

    if (response.code === 200) {
      jobs.value = response.data || []
      originalJobs.value = [...jobs.value]
      // 重新标记已申请的职位
      await markAppliedJobs()
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
    const response: any = await request.get(`/positions/detail/${job.id}`)
    if (response.code === 200) {
      selectedJob.value = response.data
    } else {
      ElMessage.warning(response.msg || response.message || '该岗位已不可查看')
      return
    }
  } catch (error) {
    console.error('获取职位详情失败:', error)
    ElMessage.warning('岗位详情加载失败，请稍后重试')
    return
  }
  showJobDetailDialog.value = true
}

// 处理来自AI悬浮球的查看详情请求
const handleShowJobDetailFromAI = (data) => {
  console.log('Jobs页面收到showJobDetail事件, data:', data)
  if (data && data.job) {
    viewJobDetail(data.job)
  } else if (data && data.positionId) {
    console.log('准备打开职位详情, positionId:', data.positionId)
    // AI悬浮球只传了positionId，需要构建job对象
    viewJobDetail({ id: data.positionId })
  }
}

// 申请岗位前置校验：姓名、学号、电话、邮箱需全部完善（后端提交接口同样强制校验）
const ensureProfileReady = async (actionLabel: string): Promise<boolean> => {
  const readiness = await checkStudentProfileReadiness()
  // 资料读取失败时不在前端拦截，由后端提交接口兜底校验
  if (!readiness.verified || readiness.missing.length === 0) return true
  try {
    await ElMessageBox.confirm(profileIncompleteMessage(readiness.missing, actionLabel), '个人资料未完善', {
      confirmButtonText: '去完善',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await router.push({ path: '/student/profile', query: { returnTo: '/student/jobs' } })
  } catch {
    // 学生取消时停留在当前页，不进入申请流程
  }
  return false
}

const applyJob = async (job) => {
  if (!(await ensureProfileReady('申请该岗位'))) return
  // 考研延迟中的学生投递岗位前提示确认：改主意去实习属合法操作，仅提示不阻断；
  // 延期状态在企业确认新实习时由后端自动切换（isDelayed 置 false）
  try {
    interface InternshipStatusRow {
      status?: number | string
      isDelayed?: boolean
      is_delayed?: boolean
      [key: string]: unknown
    }
    const statusResponse = await request.get<InternshipStatusRow>('/student/home/internship-status')
    const statusData = statusResponse.data as InternshipStatusRow | undefined
    const delayed = statusResponse.code === 200 && statusData
      && (statusData.status === 6 || statusData.status === '6' || statusData.isDelayed === true || statusData.is_delayed === true)
    if (delayed) {
      await ElMessageBox.confirm(
        '你已获批考研延迟。确认要放弃延迟并申请实习岗位吗？企业确认实习后，延迟状态将自动结束。',
        '考研延迟确认',
        { confirmButtonText: '继续申请', cancelButtonText: '再想想', type: 'warning' }
      )
    }
  } catch (error) {
    // 学生取消申请（点"再想想"或关闭确认框）时直接返回；状态查询失败不阻断正常投递
    if (error === 'cancel' || error === 'close' || error?.message === 'cancel') return
  }

  // 初始化表单数据，从用户信息中获取默认值（保持原取值链不变，仅收敛类型）
  currentApplyingJob.value = job
  applicationForm.value = {
    studentName: (authStore.user?.name || '') as unknown as string,
    studentNo: (authStore.user?.studentId || authStore.user?.username || '') as unknown as string,
    major: (authStore.user?.major || '') as unknown as string,
    grade: (authStore.user?.grade || '') as unknown as string,
    phone: (authStore.user?.phone || '') as unknown as string,
    industryName: job.industryName || job.industry || '',
    positionName: job.title || job.positionName,
    companyName: job.company,
    selfIntroduction: ''
  }
  // 加载学生的简历和证书列表
  await loadStudentMaterials()
  showApplicationDialog.value = true
  // 智能填表：弹窗打开后把表单结构注册到 AI 侧栏（只注册结构，赋值由助手写入上方响应式对象）
  formContext.register({
    formId: 'jobs:apply',
    title: '岗位投递',
    fields: applicationFillFields(),
    getFields: applicationFillFields,
    getModel: () => applicationForm.value,
    getFormEl: () => applicationFormRef.value
  })
}

const goToMaterialManagement = async () => {
  showApplicationDialog.value = false
  await router.push({
    path: '/student/profile',
    query: { action: 'resumeManagement' }
  })
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

    const operation = formContext.beginFormOperation('jobs:apply')
    const response = await studentJobApplicationService.create(applicationData)
    if (response.code === 200) {
      operation.complete({ status: 'submitted', label: '岗位投递已提交', url: '/student/applications' })
      // 同时更新 jobs 数组中的原始对象和 selectedJob（两者可能不是同一引用）
      const jobId = currentApplyingJob.value.id
      const jobInList = jobs.value.find(j => j.id === jobId)
      if (jobInList) {
        jobInList.isApplied = true
        jobInList.applicationStatus = 'pending'
      }
      if (selectedJob.value && selectedJob.value.id === jobId) {
        selectedJob.value.isApplied = true
        selectedJob.value.applicationStatus = 'pending'
      }
      currentApplyingJob.value.isApplied = true
      showApplicationDialog.value = false
      ElMessage.success('申请提交成功！')
      // 关闭详情对话框
      showJobDetailDialog.value = false
      // 触发申请成功事件，通知其他页面更新状态
      eventBus.emit('applicationSubmitted', { positionId: jobId })
    } else {
      ElMessage.error(response.message || '申请失败')
    }
  } catch (error) {
    console.error('申请失败:', error)
    const msg = error?.response?.data?.message || error?.message || '申请失败，请稍后重试'
    ElMessage.error(msg)
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
    // 触发取消申请事件，通知其他页面更新状态
    eventBus.emit('applicationCancelled', { positionId: job.id })
  }).catch(() => {
    ElMessage.info('已取消操作')
  })
}

// 当前实习仅用于标记正在实习的岗位，不参与申请资格判断。
const fetchCurrentPlacement = async () => {
  try {
    interface PlacementRow {
      positionId?: number | string
      position_id?: number | string
      [key: string]: unknown
    }
    const response = await request.get<PlacementRow>('/student/internship-record')
    if (response.code === 200 && response.data) {
      const placement = response.data as PlacementRow
      confirmedPositionId.value = (placement.positionId ?? placement.position_id ?? null) as number | null
    }
  } catch (error) {
    console.error('获取当前实习失败:', error)
  }
}

const loadStudentMaterials = async () => {
  try {
    interface MaterialRow {
      id?: number | string
      name?: string
      url?: string
      [key: string]: unknown
    }
    // 加载简历列表
    const resumeResponse = await request.get<MaterialRow[]>('/student/profile/resumes')
    if (resumeResponse.code === 200) {
      resumeList.value = Array.isArray(resumeResponse.data) ? resumeResponse.data : []
    }
    // 加载证书列表
    const certResponse = await request.get<MaterialRow[]>('/student/profile/certificates')
    if (certResponse.code === 200) {
      certificateList.value = Array.isArray(certResponse.data) ? certResponse.data : []
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

// 卡片底部企业标签：排除与标题旁徽章重复的（实习基地/校友单位）及不宜展示的（接受兜底）
const filterJobTags = (tags) => (tags || []).filter(t => !['接受兜底', '实习基地', '校友单位'].includes(t))

// 企业宣传资料：photos/videos 为 JSON 数组字符串，容错解析
const companyPhotos = computed(() => {
  try {
    const arr = JSON.parse(selectedJob.value?.photos || '[]')
    return Array.isArray(arr) ? arr.filter(u => !u.startsWith('blob:')) : []
  } catch { return [] }
})
const companyVideos = computed(() => {
  try {
    const arr = JSON.parse(selectedJob.value?.videos || '[]')
    return Array.isArray(arr) ? arr.filter(u => !u.startsWith('blob:')) : []
  } catch { return [] }
})

// 合作模式：兼容新格式(mutual_choice/student_contact)与老格式(双向,兜底,自主)
const formatCooperationMode = (mode) => {
  if (!mode) return '-'
  const map = {
    '双向': '校企双向选择',
    '自主': '学生自主联系',
    '兜底': '接受兜底',
    mutual_choice: '校企双向选择',
    student_contact: '学生自主联系'
  }
  return mode.split(',').map(m => map[m.trim()] || m.trim()).join('、')
}

// 标记已申请的职位及其状态
const markAppliedJobs = async () => {
  try {
    // 使用与 StudentJobApplicationService.list() 相同的 API
    interface AppliedJobRow {
      positionId?: number | string
      status?: string | number
      [key: string]: unknown
    }
    const response = await studentJobApplicationService.list() as unknown as {
      code?: number
      data?: AppliedJobRow[]
    }
    if (response.code === 200) {
      // 仅进行中的招聘流程阻止同一岗位重复提交；已拒绝、未通过或已撤回后可重新申请。
      // 注意：必须存入小写化的 status，模板里用 === 严格比较，原值若是大写会匹配不上任何分支。
      const applicationStatusMap = new Map()
      console.log('[markAppliedJobs] 申请列表原始数据:', response.data)
      const appliedRows = Array.isArray(response.data) ? response.data : []
      appliedRows.forEach((app) => {
        const status = String(app.status || '').toLowerCase()
        console.log('[markAppliedJobs] 申请记录 positionId=', app.positionId, '原始status=', app.status, '小写=', status, '是否活跃=', ACTIVE_APPLICATION_STATUSES.has(status))
        if (ACTIVE_APPLICATION_STATUSES.has(status)) {
          applicationStatusMap.set(String(app.positionId), status)
        }
      })

      jobs.value.forEach(job => {
        const positionIdStr = String(job.id)
        job.isApplied = false
        job.applicationStatus = null
        if (applicationStatusMap.has(positionIdStr)) {
          job.isApplied = true
          job.applicationStatus = applicationStatusMap.get(positionIdStr)
        }
      })
      originalJobs.value.forEach(job => {
        const positionIdStr = String(job.id)
        job.isApplied = false
        job.applicationStatus = null
        if (applicationStatusMap.has(positionIdStr)) {
          job.isApplied = true
          job.applicationStatus = applicationStatusMap.get(positionIdStr)
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

// 加载岗位类别选项
const loadIndustryOptions = async () => {
  try {
    const response = await request.get<string[]>(`/positions/options/industries`)
    if (response.code === 200) {
      industryOptions.value = Array.isArray(response.data) ? response.data : []
    }
  } catch (error) {
    console.error('获取岗位类别选项失败:', error)
  }
}

// 加载公司选项
const loadCompanyOptions = async () => {
  try {
    const response = await request.get<string[]>(`/positions/options/companies`)
    if (response.code === 200) {
      companyOptions.value = Array.isArray(response.data) ? response.data : []
    }
  } catch (error) {
    console.error('获取公司选项失败:', error)
  }
}

// 加载地区选项
const loadRegionOptions = async () => {
  try {
    interface RegionOptionRow {
      value?: string
      label?: string
      children?: RegionOptionRow[]
      [key: string]: unknown
    }
    const response = await request.get<RegionOptionRow[]>(`/positions/options/regions`)
    if (response.code === 200) {
      // 将省份列表转换为级联选择器格式
      const regionRows = Array.isArray(response.data) ? response.data : []
      regionOptions.value = regionRows.map(item => ({
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
    interface JobListRow {
      id?: number | string
      [key: string]: unknown
    }
    const response = await request.get<JobListRow[]>(`/positions/all`)
    if (response.code === 200) {
      const positionRows = Array.isArray(response.data) ? response.data : []
      jobs.value = positionRows as typeof jobs.value
      originalJobs.value = [...jobs.value]
    }
  } catch (error) {
    console.error('获取职位列表失败:', error)
  }
}

onMounted(async () => {
  console.log('Jobs component mounted')

  // 先加载岗位，再标记进行中的申请，避免两个并行请求产生竞态。
  await Promise.all([
    loadPositions(),
    loadIndustryOptions(),
    loadCompanyOptions(),
    loadRegionOptions(),
    fetchCurrentPlacement()
  ])
  await markAppliedJobs()

  // Result links resolve details independently of the current list filters.
  const linkedPositionId = route.query.positionId
  if (typeof linkedPositionId === 'string' && /^[1-9][0-9]*$/.test(linkedPositionId)) {
    await viewJobDetail({ id: linkedPositionId })
  }

  // 检查是否有jobId或positionId参数（从AI推荐跳转过来）
  const targetJobId = route.query.jobId || route.query.positionId
  if (targetJobId) {
    const jobId = Number(targetJobId)
    const scrollToJobCard = () => {
      activeQuickFilter.value = 'all'
      selectedIndustry.value = []
      selectedCompany.value = []
      selectedRegion.value = []
      selectedSalary.value = ''
      selectedDuration.value = ''
      selectedInternshipBase.value = ''

      const targetJob = jobs.value.find(j => j.id === jobId)
      if (targetJob) {
        searchKeyword.value = targetJob.title
        currentPage.value = 1
        setTimeout(() => highlightTableRow(jobId), 100)
      } else {
        console.warn('未找到目标职位:', jobId)
      }
    }
    if (jobs.value.length > 0) {
      scrollToJobCard()
    } else {
      watch(jobs, () => {
        scrollToJobCard()
      }, { once: true })
    }
  }

  // 连接WebSocket并监听岗位更新
  // 不再重新初始化 WebSocket，直接注册监听器，复用 StudentLayout 中初始化的连接
  onPositionUpdate(handlePositionUpdate)
  onPositionDelete(handlePositionDelete)

  // 监听收藏页面或其他页面的申请状态变化
  eventBus.on('applicationSubmitted', handleApplicationSubmitted)
  eventBus.on('applicationCancelled', handleApplicationCancelled)
  // 监听AI悬浮球的查看详情请求
  eventBus.on('showJobDetail', handleShowJobDetailFromAI)
  // 监听AI悬浮球的跳转职位请求
  eventBus.on('scrollToJob', handleScrollToJob)
})

// 处理AI悬浮球跳转职位
const handleScrollToJob = ({ positionId }) => {
  if (!positionId) return
  const jobId = Number(positionId)

  // 重置筛选条件
  activeQuickFilter.value = 'all'
  selectedIndustry.value = []
  selectedCompany.value = []
  selectedRegion.value = []
  selectedSalary.value = ''
  selectedDuration.value = ''
  selectedInternshipBase.value = ''

  // 使用搜索功能找到目标职位
  const targetJob = jobs.value.find(j => j.id === jobId)
  if (targetJob) {
    searchKeyword.value = targetJob.title
    currentPage.value = 1
    setTimeout(() => highlightTableRow(jobId), 100)
  }
}

// 监听路由query变化（处理从悬浮框跳转过来的情况）
watch(() => route.query.positionId, (newPositionId) => {
  if (newPositionId) {
    handleScrollToJob({ positionId: newPositionId })
    void viewJobDetail({ id: newPositionId })
  }
})

onUnmounted(() => {
  // 【新增】移除WebSocket监听器
  offPositionUpdate(handlePositionUpdate)
  offPositionDelete(handlePositionDelete)
  // 移除申请状态监听
  eventBus.off('applicationSubmitted', handleApplicationSubmitted)
  eventBus.off('applicationCancelled', handleApplicationCancelled)
  // 移除AI悬浮球的查看详情监听
  eventBus.off('showJobDetail', handleShowJobDetailFromAI)
  // 移除AI悬浮球的跳转职位监听
  eventBus.off('scrollToJob', handleScrollToJob)
})

const agentComparedJobs = ref<string[]>([])
const agentWorkspace = useAgentStore()
function toggleAgentCompare(job: any) {
  const id = String(job.id)
  if (agentComparedJobs.value.includes(id)) agentComparedJobs.value = agentComparedJobs.value.filter(value => value !== id)
  else if (agentComparedJobs.value.length < 3) agentComparedJobs.value.push(id)
  else { ElMessage.info('每次最多比较 3 个岗位'); return }
  if (!agentWorkspace.draft || ['结合我的专业比较我选中的这些岗位', '结合我的专业分析我选中的这个岗位', '比较我选中的这些岗位', '分析我选中的这个岗位'].includes(agentWorkspace.draft)) {
    agentWorkspace.draft = agentComparedJobs.value.length > 1 ? '结合我的专业比较我选中的这些岗位' : agentComparedJobs.value.length ? '结合我的专业分析我选中的这个岗位' : ''
  }
  void agentWorkspace.openWithPrompt()
}
const showLocalCompareDialog = ref(false)
function toggleLocalCompare(job: any) {
  const id = String(job.id)
  if (agentComparedJobs.value.includes(id)) agentComparedJobs.value = agentComparedJobs.value.filter(value => value !== id)
  else if (agentComparedJobs.value.length < 3) agentComparedJobs.value.push(id)
  else { ElMessage.info('每次最多比较 3 个岗位'); return }
  if (agentComparedJobs.value.length >= 2) showLocalCompareDialog.value = true
}
const localCompareRows = computed((): any[] => agentComparedJobs.value
  .map(id => jobs.value.find((job: any) => String(job.id) === id))
  .filter((job: any) => Boolean(job)))
useAgentPageContext(() => ({
  title: '岗位浏览',
  view: showJobDetailDialog.value ? 'job-detail' : showApplicationDialog.value ? 'application-dialog' : 'page',
  object: (showJobDetailDialog.value && selectedJob.value?.id) ? { type: 'job', id: String(selectedJob.value.id) }
    : (showApplicationDialog.value && currentApplyingJob.value?.id) ? { type: 'job', id: String(currentApplyingJob.value.id) } : null,
  selectedObjects: agentComparedJobs.value.map(id => ({ type: 'job', id })),
  filters: { keyword: searchKeyword.value, region: selectedRegion.value, industry: selectedIndustry.value,
    company: selectedCompany.value, salary: selectedSalary.value, visibleIds: paginatedJobs.value.map((job: any) => String(job.id)) }
}))

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
.jobs-container .page-header {
  margin-bottom: 24px;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  border-radius: 12px;
  padding: 24px 32px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  position: relative;
  overflow: hidden;
  z-index: 1;
}

.jobs-container .page-header::before {
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

.jobs-container .page-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: white;
  margin: 0 0 8px 0;
  letter-spacing: 0.3px;
}

.jobs-container .page-header p {
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
  line-height: 1.5;
  margin: 0;
}

/* 搜索区域：搜索 + 筛选合并为一张卡片 */
.search-section {
  margin-bottom: 16px;
  position: relative;
  z-index: 1;
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  padding: 16px 20px;
}

.search-filter-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
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
  flex: 1 1 auto;
  min-width: 0;
  max-width: 450px;
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

/* 筛选面板：与搜索区同属一张卡片，仅用分隔线衔接 */
.filter-panel {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}

.filter-panel-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
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
  border-top: 1px solid #ebeef5;
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

/* 职位表格：表格 + 分页合并为一张卡片 */
.table-card {
  position: relative;
  z-index: 1;
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.data-table {
  --el-table-border-color: #ebeef5;
  --el-table-header-bg-color: #fafbfc;
  --el-table-row-hover-bg-color: #f5f9ff;
}

.data-table :deep(.el-table__header th.el-table__cell) {
  background: #fafbfc;
  color: #606266;
  font-weight: 600;
  font-size: 13px;
  padding: 12px 0;
  border-bottom: 1px solid #ebeef5;
}

.data-table :deep(.el-table__row td.el-table__cell) {
  padding: 12px 0;
  font-size: 13px;
  color: #303133;
  border-bottom: 1px solid #f2f3f5;
}

.data-table :deep(.el-table__row:hover td.el-table__cell) {
  background: #f5f9ff;
}

.job-title-text {
  font-weight: 500;
  color: #303133;
}

.tags-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.action-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: nowrap;
}

.action-cell .el-button + .el-button {
  margin-left: 0;
}

.action-cell .el-button.is-link,
.action-cell .el-button--small {
  padding: 5px 12px;
  font-size: 12px;
  border-radius: 6px;
}

.quota-warn {
  font-size: 11px;
  color: #e6a23c;
  margin-top: 2px;
}

.unrated-company {
  color: #b0b8c4;
  font-weight: 400;
  font-size: 12px;
}

.favorite-icon {
  font-size: 18px;
  cursor: pointer;
  transition: all 0.25s ease;
  color: #c0c4cc;
  flex-shrink: 0;
}

.favorite-icon:hover {
  color: #f7ba2a;
  transform: scale(1.15);
}

.favorite-icon.active {
  color: #f7ba2a;
}

.quota-num {
  color: #409eff;
  font-weight: 700;
  font-size: 14px;
}

.job-tag {
  background: #f0f7ff;
  color: #409eff;
  border: 1px solid #d6e8ff;
  border-radius: 4px;
  padding: 1px 6px;
  font-size: 11px;
  font-weight: 500;
  line-height: 18px;
}

.data-table :deep(.el-table__row.highlight-row td.el-table__cell) {
  animation: highlightFlash 1s ease-in-out infinite;
}

@keyframes highlightFlash {
  0%, 100% {
    background-color: rgba(64, 158, 255, 0.06);
  }
  50% {
    background-color: rgba(64, 158, 255, 0.16);
  }
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

/* 分页区域：并入表格卡片底部 */
.pagination-section {
  display: flex;
  justify-content: center;
  padding: 8px 16px;
  border-top: 1px solid #ebeef5;
  background: #fafbfc;
}

.pagination {
  background: transparent;
  padding: 0;
}

/* 职位详情弹窗 */
.edit-profile-dialog :deep(.el-dialog) {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.edit-profile-dialog :deep(.el-dialog__header) {
  padding: 0;
  border-bottom: none;
  background: transparent;
}

.edit-profile-dialog :deep(.el-dialog__title) {
  display: none;
}

.edit-profile-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
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

.edit-profile-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  background: rgba(255, 255, 255, 0.3);
}

.edit-profile-dialog :deep(.el-dialog__body) {
  padding: 0;
  background-color: #ffffff;
}

.edit-profile-dialog :deep(.el-dialog__footer) {
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

.detail-actions .favorite-icon {
  font-size: 38px;
  color: #c0c4cc;
  cursor: pointer;
  transition: all 0.3s ease;
}

.detail-actions .favorite-icon:hover {
  color: #f7ba2a;
  transform: scale(1.15);
}

.detail-actions .favorite-icon.active {
  color: #f7ba2a;
}

.detail-section {
  margin-bottom: 20px;
  padding: 0 28px;
}

/* 企业信息折叠面板：去掉默认边框，保留标题装饰线 */
.job-company-collapse {
  border: none;
  background: transparent;
}

.job-company-collapse :deep(.el-collapse-item__header) {
  height: auto;
  padding: 0;
  border-bottom: none;
  background: transparent;
  line-height: 1.5;
}

.job-company-collapse :deep(.el-collapse-item__wrap) {
  border-bottom: none;
}

.job-company-collapse .section-title {
  margin: 0 0 16px 0;
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

.company-card-header {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px 20px;
  background: linear-gradient(135deg, #f8fafc 0%, #ffffff 100%);
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  margin-bottom: 12px;
}

.company-card-body {
  flex: 1;
  min-width: 0;
}

.company-logo-large {
  width: 64px;
  height: 64px;
  object-fit: contain;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: white;
  padding: 6px;
  flex-shrink: 0;
}

.company-card-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.company-name-text {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.company-intro-text {
  font-size: 14px;
  line-height: 1.8;
  color: #475569;
  margin: 8px 0 0 0;
  white-space: pre-wrap;
}

.sub-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  margin: 14px 0 10px 0;
}

.company-photo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}

.company-photo {
  width: 100%;
  height: 110px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  cursor: zoom-in;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.company-photo:hover {
  transform: scale(1.03);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.company-video-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
}

.company-video {
  width: 100%;
  max-height: 300px;
  border-radius: 8px;
  background: #000;
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
@media (min-width: 992px) and (max-width: 1199px) {
  .search-filter-container {
    flex-wrap: wrap;
  }

  .filter-center {
    flex-wrap: wrap;
  }

  .quick-filters {
    flex-wrap: wrap;
  }
}

@media (min-width: 768px) and (max-width: 991px) {
  .search-filter-container {
    flex-wrap: wrap;
    gap: 10px;
  }

  .search-left {
    flex: 1 1 auto;
    min-width: 280px;
  }

  .filter-center {
    flex: 1 1 100%;
  }

  .quick-filters {
    flex-wrap: wrap;
    gap: 6px;
  }
}

@media (max-width: 767px) {
  .filter-group {
    min-width: 100%;
  }

  .filter-row.two-cols {
    flex-direction: column;
    gap: 16px;
  }

  .action-cell {
    flex-wrap: wrap;
  }
}

@media (max-width: 480px) {
  .search-filter-container {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
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

.application-dialog .material-upload-link {
  vertical-align: baseline;
  line-height: 24px;
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
