<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getCompanyInternshipStudents, type CompanyInternshipStudent } from '@/api/companyInternshipStudents'
import { useAgentPageContext } from '@/agent/useAgentPageContext'
import { useIsMobile } from '@/composables/useIsMobile'

const isMobile = useIsMobile()
const students = ref<CompanyInternshipStudent[]>([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const keyword = ref('')
const searchKeyword = ref('')
const page = ref(1)
const pageSize = ref(20)
const revision = ref(0)
let requestVersion = 0

const loadStudents = async () => {
  const version = ++requestVersion
  loading.value = true
  loadError.value = ''
  try {
    const response = await getCompanyInternshipStudents({ page: page.value, pageSize: pageSize.value, keyword: searchKeyword.value })
    if (version !== requestVersion) return
    if (response.code !== 200) throw new Error(response.message || '加载实习学生失败')
    students.value = response.data.rows
    total.value = response.data.total
    const lastPage = Math.max(1, Math.ceil(total.value / pageSize.value))
    if (page.value > lastPage) {
      page.value = lastPage
      return await loadStudents()
    }
    revision.value++
  } catch (error) {
    if (version !== requestVersion) return
    students.value = []
    total.value = 0
    loadError.value = error instanceof Error ? error.message : '加载实习学生失败，请重试'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

const searchStudents = () => {
  searchKeyword.value = keyword.value.trim()
  page.value = 1
  return loadStudents()
}

const resetSearch = () => {
  keyword.value = ''
  return searchStudents()
}

const changePageSize = () => {
  page.value = 1
  return loadStudents()
}

useAgentPageContext(() => ({
  pageKey: 'company.internship-students', title: '实习学生', view: 'list',
  filters: { keyword: searchKeyword.value, status: 'IN_PROGRESS' }, revision: revision.value
}))
onMounted(loadStudents)
</script>

<template>
  <div class="internship-students-page">
    <div class="page-header">
      <div>
        <h2>当前实习学生</h2>
        <p>查看正在本企业实习的学生，包含平台岗位和自主实习。</p>
      </div>
      <el-button class="refresh-button" :icon="Refresh" :loading="loading" @click="loadStudents">刷新名单</el-button>
    </div>

    <div class="search-card">
      <div class="section-title">实习学生查询</div>
      <div class="search-bar">
        <el-input v-model="keyword" placeholder="搜索姓名、学号或岗位" maxlength="100" clearable :prefix-icon="Search" aria-label="搜索实习学生" @keyup.enter="searchStudents" @clear="searchStudents" />
        <el-button type="primary" :loading="loading" @click="searchStudents">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
        <span class="student-count">{{ searchKeyword ? '符合条件' : '当前在岗' }}：{{ total }} 人</span>
      </div>
    </div>

    <el-card shadow="never" class="student-list-card">
      <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" class="load-error" />
      <el-table v-if="!isMobile" v-loading="loading" :data="students" row-key="studentId" aria-label="当前实习学生名单" :empty-text="loadError ? '名单加载失败，请点击刷新重试' : searchKeyword ? '没有符合条件的实习学生' : '当前没有正在本企业实习的学生'">
        <el-table-column prop="studentName" label="姓名" min-width="100" fixed />
        <el-table-column prop="studentNumber" label="学号" min-width="125" />
        <el-table-column prop="grade" label="年级" width="75" />
        <el-table-column label="专业 / 班级" min-width="180">
          <template #default="{ row }">
            <div class="student-study"><span>{{ row.majorName || '未填写专业' }}</span><span class="secondary-text">{{ row.className || '未填写班级' }}</span></div>
          </template>
        </el-table-column>
        <el-table-column prop="positionName" label="实习岗位" min-width="150" show-overflow-tooltip />
        <el-table-column label="实习方式" min-width="160">
          <template #default="{ row }">
            <el-tag :type="row.sourceType === 'SELF_PRACTICE' ? 'warning' : 'info'" size="small">{{ row.sourceType === 'SELF_PRACTICE' ? '自主实习' : '平台岗位' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="联系方式" width="240">
          <template #default="{ row }">
            <div class="contact-info">
              <a v-if="row.phone" :href="`tel:${row.phone}`">{{ row.phone }}</a>
              <a v-if="row.email" :href="`mailto:${row.email}`">{{ row.email }}</a>
              <span v-if="!row.phone && !row.email">未填写</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div v-else v-loading="loading" class="mobile-card-list" aria-label="当前实习学生名单" :aria-busy="loading">
        <el-empty v-if="!loading && !students.length" :description="loadError ? '名单加载失败，请点击刷新重试' : searchKeyword ? '没有符合条件的实习学生' : '当前没有正在本企业实习的学生'" />
        <article v-for="student in students" :key="student.studentId" class="mobile-card">
          <div class="mobile-card-header">
            <span class="mobile-card-title">{{ student.studentName || '未填写姓名' }}</span>
            <el-tag type="success">正在实习</el-tag>
          </div>
          <div class="mobile-card-body">
            <div class="mobile-card-row"><span class="mobile-card-label">学号</span><span class="mobile-card-value">{{ student.studentNumber || '未填写' }}</span></div>
            <div class="mobile-card-row"><span class="mobile-card-label">年级</span><span class="mobile-card-value">{{ student.grade || '未填写' }}</span></div>
            <div class="mobile-card-row"><span class="mobile-card-label">专业 / 班级</span><span class="mobile-card-value">{{ student.majorName || '未填写专业' }} / {{ student.className || '未填写班级' }}</span></div>
            <div class="mobile-card-row"><span class="mobile-card-label">实习岗位</span><span class="mobile-card-value">{{ student.positionName || '未填写' }}</span></div>
            <div class="mobile-card-row"><span class="mobile-card-label">实习方式</span><el-tag :type="student.sourceType === 'SELF_PRACTICE' ? 'warning' : 'info'" size="small">{{ student.sourceType === 'SELF_PRACTICE' ? '自主实习' : '平台岗位' }}</el-tag></div>
            <div v-if="!student.phone && !student.email" class="mobile-card-row"><span class="mobile-card-label">联系方式</span><span class="mobile-card-value">未填写</span></div>
          </div>
          <div v-if="student.phone || student.email" class="mobile-card-actions student-contact-actions">
            <a v-if="student.phone" :href="`tel:${student.phone}`" :aria-label="`拨打${student.studentName}的电话 ${student.phone}`">{{ student.phone }}</a>
            <a v-if="student.email" :href="`mailto:${student.email}`" :aria-label="`发邮件给${student.studentName} ${student.email}`">{{ student.email }}</a>
          </div>
        </article>
      </div>
      <div class="pagination">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10, 20, 50]" :total="total" :layout="isMobile ? 'total, sizes, prev, next' : 'total, sizes, prev, pager, next'" @current-change="loadStudents" @size-change="changePageSize" />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.internship-students-page { padding: 0; }
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%); color: white; padding: 24px 40px; border-radius: 12px; box-shadow: 0 4px 20px rgba(64, 158, 255, 0.3); }
.page-header h2 { margin: 0 0 8px; font-size: 28px; font-weight: bold; letter-spacing: 1px; color: white; }
.page-header p { margin: 0; font-size: 14px; line-height: 1.6; opacity: 0.95; }
.refresh-button { background: rgba(255, 255, 255, 0.15); border-color: rgba(255, 255, 255, 0.6); color: white; }
.refresh-button:hover { background: rgba(255, 255, 255, 0.25); }
.search-card { background: white; padding: 20px 24px; border-radius: 8px; margin-bottom: 12px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1); }
.section-title { margin-bottom: 16px; font-size: 15px; font-weight: 600; color: #303133; padding-left: 12px; border-left: 3px solid #409EFF; }
.search-bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.search-bar .el-input { width: 300px; max-width: 100%; }
.student-count { margin-left: auto; color: #606266; }
.student-list-card { border: none; border-radius: 8px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1); }
.student-study { display: flex; flex-direction: column; gap: 4px; }
.secondary-text { color: #909399; font-size: 12px; }
:deep(.el-table th.el-table__cell) { background: #f5f7fa; color: #606266; font-weight: 600; }
.load-error { margin-bottom: 16px; }
.contact-info { display: flex; flex-direction: column; gap: 4px; overflow-wrap: anywhere; }
.contact-info a { color: #409eff; text-decoration: none; }
.contact-info a:hover { text-decoration: underline; }
.pagination { display: flex; justify-content: flex-end; margin-top: 20px; overflow-x: auto; }
@media (max-width: 768px) {
  .page-header { padding: 18px 20px; }
  .page-header h2 { font-size: 24px; }
  .search-card { padding: 16px 18px; }
  .search-bar .el-input { width: 100%; }
  .student-count { margin-left: 0; }
  .pagination { justify-content: flex-start; }
  .student-contact-actions a { display: flex; align-items: center; justify-content: center; min-height: 44px; width: 100%; padding: 8px 12px; box-sizing: border-box; border: 1px solid #d9ecff; border-radius: 6px; color: #409eff; background: #ecf5ff; text-decoration: none; overflow-wrap: anywhere; }
}
</style>
