<template>
  <div class="class-management-container">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">班级管理</h1>
        <p class="page-description">管理您负责的班级信息</p>
      </div>
    </div>

    <el-card class="stats-card" shadow="never" v-loading="statsLoading">
      <div class="stats-container">
        <div class="stat-item">
          <div class="stat-value">{{ statistics.classCount }}</div>
          <div class="stat-label">负责班级</div>
        </div>
        <div class="stat-item">
          <div class="stat-value">{{ statistics.studentCount }}</div>
          <div class="stat-label">学生总数</div>
        </div>
      </div>
    </el-card>

    <el-card class="search-card" shadow="never">
      <el-form :inline="true" class="search-form">
        <div class="search-row">
          <el-form-item label="班级名称">
            <el-input
              v-model="searchName"
              placeholder="请输入班级名称"
              clearable
              @keyup.enter="handleSearch"
            ></el-input>
          </el-form-item>
          <el-form-item class="search-actions">
            <el-button type="primary" @click="handleSearch" class="action-btn primary">
              <el-icon><Search /></el-icon>&nbsp;查询
            </el-button>
            <el-button type="warning" @click="handleReset" class="action-btn warning">
              <el-icon><Refresh /></el-icon>&nbsp;重置
            </el-button>
            <el-button type="primary" @click="handleAssignClass" class="action-btn primary">
              <el-icon><Plus /></el-icon>&nbsp;添加班级
            </el-button>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="table-header">
        <h3 class="table-title">班级列表</h3>
        <div class="table-actions">
          <span class="total-count">共 {{ filteredClasses.length }} 条记录</span>
        </div>
      </div>

      <el-table
        :data="filteredClasses"
        border
        style="width: 100%"
        fit
        v-loading="loading"
        class="data-table"
      >
        <el-table-column prop="id" label="ID" align="center" width="80" />
        <el-table-column prop="name" label="班级名称" align="center" min-width="180" />
        <el-table-column prop="grade" label="年级" align="center" width="100" />
        <el-table-column prop="majorName" label="专业" align="center" min-width="200" />
        <el-table-column prop="studentCount" label="学生人数" align="center" width="100" />
        <el-table-column prop="createTime" label="创建时间" align="center" width="180">
          <template #default="scope">
            <span>{{ formatDate(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="scope">
            <div class="action-buttons">
              <el-tooltip content="查看学生" placement="top">
                <el-button size="small" type="primary" @click="viewStudents(scope.row)" class="table-btn view">
                  <el-icon><User /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="取消关联" placement="top">
                <el-button size="small" type="danger" @click="handleUnassign(scope.row)" class="table-btn delete">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="assignDialogVisible" title="添加负责班级" width="600px">
      <el-transfer
        v-model="selectedClassIds"
        :data="allClassesData"
        :titles="['可选班级', '已选班级']"
        :props="{
          key: 'id',
          label: 'name'
        }"
        filterable
        filter-placeholder="搜索班级"
      />
      <template #footer>
        <el-button @click="assignDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAssign" :loading="assignLoading">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="studentsDialogVisible" :title="`${currentClass?.name} - 学生列表`" width="900px">
      <div class="students-table-container">
      <el-table :data="classStudents" border style="width: 100%" v-loading="studentsLoading" max-height="400">
        <el-table-column prop="studentUserId" label="学号" align="center" width="120" />
        <el-table-column prop="name" label="姓名" align="center" width="100" />
        <el-table-column prop="gender" label="性别" align="center" width="80">
          <template #default="scope">
            <span>{{ scope.row.gender === 1 ? '男' : '女' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="grade" label="年级" align="center" width="80" />
        <el-table-column prop="internshipStatus" label="实习状态" align="center" width="100">
          <template #default="scope">
            <el-tag v-if="scope.row.internshipStatus === 0" type="info">未找到</el-tag>
            <el-tag v-else-if="scope.row.internshipStatus === 1" type="warning">已有Offer</el-tag>
            <el-tag v-else-if="scope.row.internshipStatus === 2" type="success">已确认</el-tag>
            <el-tag v-else-if="scope.row.internshipStatus === 3" type="primary">已结束</el-tag>
            <el-tag v-else-if="scope.row.internshipStatus === 4" type="danger">已中断</el-tag>
            <el-tag v-else-if="scope.row.internshipStatus === 5" type="warning">延期</el-tag>
            <el-tag v-else type="info">未找到</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="companyName" label="实习单位" align="center" min-width="150">
          <template #default="scope">
            <span>{{ scope.row.companyName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" align="center" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
              {{ scope.row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      </div>
      <template #footer>
        <el-button @click="studentsDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, User, Delete } from '@element-plus/icons-vue'
import {
  getTeacherClasses,
  getAllClasses,
  assignClassesToCounselor,
  unassignClassFromCounselor,
  getClassStudents,
  getCounselorStatistics,
  type TeacherClass
} from '@/api/teacherClass'

const router = useRouter()

const loading = ref(false)
const statsLoading = ref(false)
const assignLoading = ref(false)
const studentsLoading = ref(false)

const counselorId = ref<number>(0)
const myClasses = ref<TeacherClass[]>([])
const allClasses = ref<TeacherClass[]>([])
const classStudents = ref<any[]>([])

const searchName = ref('')
const assignDialogVisible = ref(false)
const studentsDialogVisible = ref(false)
const selectedClassIds = ref<number[]>([])
const currentClass = ref<TeacherClass | null>(null)

const statistics = ref({
  classCount: 0,
  studentCount: 0
})

const filteredClasses = computed(() => {
  if (!searchName.value) {
    return myClasses.value
  }
  return myClasses.value.filter(c => 
    c.name.toLowerCase().includes(searchName.value.toLowerCase())
  )
})

const allClassesData = computed(() => {
  const myClassIds = myClasses.value.map(c => c.id)
  return allClasses.value
    .filter(c => !myClassIds.includes(c.id))
    .map(c => ({
      id: c.id,
      name: c.name
    }))
})

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const loadMyClasses = async () => {
  loading.value = true
  try {
    const response = await getTeacherClasses(counselorId.value)
    if (response.data) {
      myClasses.value = response.data
    }
  } catch (error) {
    console.error('加载班级列表失败:', error)
    ElMessage.error('加载班级列表失败')
  } finally {
    loading.value = false
  }
}

const loadAllClasses = async () => {
  try {
    const response = await getAllClasses()
    if (response.data) {
      allClasses.value = response.data
    }
  } catch (error) {
    console.error('加载所有班级失败:', error)
  }
}

const loadStatistics = async () => {
  statsLoading.value = true
  try {
    const response = await getCounselorStatistics(counselorId.value)
    if (response.data) {
      statistics.value = response.data
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  } finally {
    statsLoading.value = false
  }
}

const handleSearch = () => {
  // 筛选逻辑已通过计算属性实现
}

const handleReset = () => {
  searchName.value = ''
}

const handleAssignClass = () => {
  selectedClassIds.value = []
  assignDialogVisible.value = true
}

const confirmAssign = async () => {
  if (selectedClassIds.value.length === 0) {
    ElMessage.warning('请选择要添加的班级')
    return
  }

  assignLoading.value = true
  try {
    await assignClassesToCounselor(counselorId.value, selectedClassIds.value)
    ElMessage.success('添加班级成功')
    assignDialogVisible.value = false
    await loadMyClasses()
    await loadStatistics()
  } catch (error) {
    console.error('添加班级失败:', error)
    ElMessage.error('添加班级失败')
  } finally {
    assignLoading.value = false
  }
}

const handleUnassign = async (classItem: TeacherClass) => {
  try {
    await ElMessageBox.confirm(
      `确定要取消与班级"${classItem.name}"的关联吗？`,
      '确认取消关联',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await unassignClassFromCounselor(counselorId.value, classItem.id)
    ElMessage.success('取消关联成功')
    await loadMyClasses()
    await loadStatistics()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('取消关联失败:', error)
      ElMessage.error('取消关联失败')
    }
  }
}

const viewStudents = async (classItem: TeacherClass) => {
  currentClass.value = classItem
  studentsDialogVisible.value = true
  studentsLoading.value = true
  try {
    const response = await getClassStudents(classItem.id, counselorId.value)
    if (response.data) {
      classStudents.value = response.data
    }
  } catch (error) {
    console.error('加载学生列表失败:', error)
    ElMessage.error('加载学生列表失败')
  } finally {
    studentsLoading.value = false
  }
}

onMounted(() => {
  const currentRole = localStorage.getItem('current_role') || 'ROLE_TEACHER'
  const rolePrefix = currentRole === 'ROLE_TEACHER' ? 'teacher_' : ''
  const id = localStorage.getItem(rolePrefix + 'userId_' + currentRole) || localStorage.getItem('teacherId')
  if (id) {
    counselorId.value = parseInt(id)
  }
  
  loadMyClasses()
  loadAllClasses()
  loadStatistics()
})
</script>

<style scoped>
.class-management-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
  position: relative;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 24px 32px;
  position: relative;
}

.header-content {
  z-index: 1;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: #000;
  margin: 0 0 8px 0;
}

.page-description {
  font-size: 14px;
  color: #606266;
  opacity: 0.95;
  font-weight: 500;
  margin: 0;
}

.stats-card {
  border-radius: 12px;
  border: none;
  padding: 12px 24px;
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 10;
}

.stats-container {
  display: flex;
  gap: 30px;
  align-items: center;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #409eff;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.search-card,
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

.table-card {
  padding: 0;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
}

.search-actions {
  margin-left: auto;
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

.students-table-container {
  max-height: 400px;
  overflow-y: auto;
}

.action-btn {
  border-radius: 8px;
  padding: 10px 20px;
}

.action-btn.primary {
  background-color: #409EFF;
  border-color: #409EFF;
}

.action-btn.warning {
  background-color: #e6a23c;
  border-color: #e6a23c;
}
</style>
