<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import request from '@/utils/request'
import type { FormFieldDescriptor } from '@/agent/types'
import { useFormContext } from '@/agent/formContext'
import { useAuthStore } from '@/store/auth'

interface TeacherOption {
  id: number
  teacherUserId?: string
  name: string
  phone?: string
  teacherType?: string
}

interface CompanyOption {
  id: number
  companyName: string
  visitSummary?: {
    visitedThisYear?: boolean
    currentYearVisitCount?: number
  }
}

interface VisitParticipant {
  teacherId: number
  teacherAccount?: string
  teacherName: string
  teacherPhone?: string
}

interface VisitRecord {
  id: number
  companyId: number
  companyName?: string
  visitDate: string
  remark?: string
  creatorTeacherId: number
  creatorTeacherName?: string
  createTime?: string
  participants?: VisitParticipant[]
}

interface SchoolLiaison {
  id: number
  teacherId: number
  creatorTeacherId?: number
  creatorTeacherName?: string
  teacherAccount?: string
  teacherName: string
  teacherPhone?: string
}

const authStore = useAuthStore()
const emit = defineEmits<{
  (event: 'relations-updated'): void
}>()
const currentYear = new Date().getFullYear()
const activeTab = ref('visits')
const loading = ref(false)
const companies = ref<CompanyOption[]>([])
const teachers = ref<TeacherOption[]>([])
const visits = ref<VisitRecord[]>([])
const liaisons = ref<SchoolLiaison[]>([])

const visitFilters = ref<{ companyId?: number; year?: number; teacherId?: number }>({ year: currentYear })
const visitDialogVisible = ref(false)
const editingVisitId = ref<number | null>(null)
const visitForm = ref<{ companyId?: number; visitDate: string; participantTeacherIds: number[]; remark: string }>({
  companyId: undefined,
  visitDate: new Date().toISOString().slice(0, 10),
  participantTeacherIds: [],
  remark: ''
})

const liaisonCompanyId = ref<number | undefined>()
const liaisonDialogVisible = ref(false)
const liaisonForm = ref<{ teacherId?: number }>({ teacherId: undefined })
const currentTeacherId = computed(() => Number(authStore.user?.id || 0))

// ===== 智能填表：企业走访与校内对接人弹窗注册到 AI 侧栏 =====
const formContext = useFormContext()
const visitFillFields = (): FormFieldDescriptor[] => [
  { prop: 'companyId', label: '走访企业', type: 'select', required: true,
    options: companies.value.map(item => ({ label: item.companyName, value: item.id })) },
  { prop: 'visitDate', label: '走访日期', type: 'date', valueType: 'string', required: true },
  { prop: 'participantTeacherIds', label: '走访教师', type: 'select', valueType: 'array', required: true,
    options: teachers.value.map(item => ({ label: `${item.name}${item.phone ? ` · ${item.phone}` : ''}`, value: item.id })) },
  { prop: 'remark', label: '备注', type: 'textarea' }
]
watch(visitDialogVisible, open => {
  if (open) formContext.register({
    formId: 'teacher:company-visit', title: '登记企业走访', fields: visitFillFields(), getFields: visitFillFields,
    getModel: () => visitForm.value,
    getValidationSummary: () => ({ missingFields: [], validationErrors: visitForm.value.companyId && visitForm.value.visitDate && visitForm.value.participantTeacherIds.length ? [] : ['请选择企业、走访日期和至少一名走访教师'] })
  })
  else formContext.unregister('teacher:company-visit')
})
watch(liaisonDialogVisible, open => {
  if (open) formContext.register({
    formId: 'teacher:school-liaison', title: '添加校内对接人',
    getFields() { return this.fields.map(field => ({ ...field, options: teachers.value.map(item => ({ label: item.name, value: item.id })) })) },
    fields: [{ prop: 'teacherId', label: '对接教师', type: 'select', required: true,
      options: teachers.value.map(item => ({ label: `${item.name}${item.phone ? ` · ${item.phone}` : ''}`, value: item.id })) }],
    getModel: () => liaisonForm.value,
    getValidationSummary: () => ({ missingFields: [], validationErrors: liaisonCompanyId.value && liaisonForm.value.teacherId ? [] : ['请选择企业和对接教师'] }),
    getObject: () => liaisonCompanyId.value ? { type: 'company', id: String(liaisonCompanyId.value) } : null
  })
  else formContext.unregister('teacher:school-liaison')
})
onUnmounted(() => {
  formContext.unregister('teacher:company-visit')
  formContext.unregister('teacher:school-liaison')
})
const unwrapRows = <T,>(response: any): T[] => {
  const data = response?.data
  if (Array.isArray(data)) return data
  if (Array.isArray(data?.rows)) return data.rows
  return []
}

const loadCompanies = async () => {
  const response: any = await request.get('/teacher/companies', {
    params: { page: 1, pageSize: 10000, visitedYear: currentYear }
  })
  companies.value = unwrapRows<CompanyOption>(response)
}

const loadTeachers = async () => {
  const response: any = await request.get('/teacher/companies/teacher-options')
  teachers.value = unwrapRows<TeacherOption>(response)
}

const loadVisits = async () => {
  loading.value = true
  try {
    const response: any = await request.get('/teacher/companies/visits', {
      params: { ...visitFilters.value, page: 1, pageSize: 1000 }
    })
    visits.value = unwrapRows<VisitRecord>(response)
  } catch (error: any) {
    ElMessage.error(error?.message || '加载走访记录失败')
  } finally {
    loading.value = false
  }
}

const resetVisitFilters = () => {
  visitFilters.value = { year: currentYear }
  void loadVisits()
}

const openCreateVisit = (companyId?: number) => {
  editingVisitId.value = null
  visitForm.value = {
    companyId,
    visitDate: new Date().toISOString().slice(0, 10),
    participantTeacherIds: currentTeacherId.value ? [currentTeacherId.value] : [],
    remark: ''
  }
  visitDialogVisible.value = true
}

const openEditVisit = (record: VisitRecord) => {
  editingVisitId.value = record.id
  visitForm.value = {
    companyId: record.companyId,
    visitDate: record.visitDate,
    participantTeacherIds: (record.participants || []).map(item => item.teacherId),
    remark: record.remark || ''
  }
  visitDialogVisible.value = true
}

const canManageVisit = (record: VisitRecord) => record.creatorTeacherId === currentTeacherId.value

const saveVisit = async () => {
  const form = visitForm.value
  if (!form.companyId || !form.visitDate || form.participantTeacherIds.length === 0) {
    ElMessage.warning('请选择企业、走访日期和至少一名走访教师')
    return
  }
  loading.value = true
  try {
    const url = editingVisitId.value
      ? `/teacher/companies/visits/${editingVisitId.value}`
      : '/teacher/companies/visits'
    const operation = formContext.beginFormOperation('teacher:company-visit')
    const response: any = editingVisitId.value
      ? await request.put(url, form)
      : await request.post(url, form)
    if (response.code !== 200) throw new Error(response.message || '保存失败')
    operation.complete({ status: 'saved', label: '企业走访记录已保存', url: '/teacher/company-list' })
    ElMessage.success(editingVisitId.value ? '走访记录已更新' : '走访记录已登记')
    visitDialogVisible.value = false
    emit('relations-updated')
    await Promise.all([loadVisits(), loadCompanies()])
  } catch (error: any) {
    ElMessage.error(error?.message || '保存走访记录失败')
  } finally {
    loading.value = false
  }
}

const deleteVisit = async (record: VisitRecord) => {
  await ElMessageBox.confirm('确定删除这条走访记录吗？', '删除确认', { type: 'warning' })
  const response: any = await request.delete(`/teacher/companies/visits/${record.id}`)
  if (response.code !== 200) throw new Error(response.message || '删除失败')
  ElMessage.success('走访记录已删除')
  emit('relations-updated')
  await Promise.all([loadVisits(), loadCompanies()])
}

const loadLiaisons = async () => {
  if (!liaisonCompanyId.value) {
    liaisons.value = []
    return
  }
  loading.value = true
  try {
    const response: any = await request.get(`/teacher/companies/${liaisonCompanyId.value}/liaisons`)
    liaisons.value = unwrapRows<SchoolLiaison>(response)
  } catch (error: any) {
    ElMessage.error(error?.message || '加载校内对接人失败')
  } finally {
    loading.value = false
  }
}

const canManageLiaison = (row: SchoolLiaison) => row.creatorTeacherId === currentTeacherId.value

const openAddLiaison = () => {
  liaisonForm.value = { teacherId: undefined }
  liaisonDialogVisible.value = true
}

const addLiaison = async () => {
  if (!liaisonCompanyId.value) return ElMessage.warning('请先选择企业')
  if (!liaisonForm.value.teacherId) return ElMessage.warning('请选择对接教师')
  loading.value = true
  try {
    const operation = formContext.beginFormOperation('teacher:school-liaison')
    const response: any = await request.post(`/teacher/companies/${liaisonCompanyId.value}/liaisons`, {
      teacherId: liaisonForm.value.teacherId
    })
    if (response.code !== 200) throw new Error(response.message || '添加失败')
    operation.complete({ status: 'saved', label: '校内对接人已添加', url: '/teacher/company-list' })
    ElMessage.success('校内对接人已添加')
    liaisonDialogVisible.value = false
    emit('relations-updated')
    await loadLiaisons()
  } catch (error: any) {
    ElMessage.error(error?.message || '添加校内对接人失败')
  } finally {
    loading.value = false
  }
}

const deleteLiaison = async (row: SchoolLiaison) => {
  await ElMessageBox.confirm(`确定删除校内对接人「${row.teacherName}」吗？`, '删除确认', { type: 'warning' })
  const response: any = await request.delete(`/teacher/companies/liaisons/${row.id}`)
  if (response.code !== 200) throw new Error(response.message || '删除失败')
  ElMessage.success('校内对接关系已解除')
  emit('relations-updated')
  await loadLiaisons()
}

const companyLabel = (company: CompanyOption) => {
  const visited = company.visitSummary?.visitedThisYear
  const count = company.visitSummary?.currentYearVisitCount || 0
  return visited ? `${company.companyName}（本年度已走访 ${count} 次）` : company.companyName
}

onMounted(async () => {
  try {
    await Promise.all([loadCompanies(), loadTeachers(), loadVisits()])
  } catch (error: any) {
    ElMessage.error(error?.message || '企业走访信息加载失败')
  }
})
</script>

<template>
  <el-card class="relations-card" shadow="never">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="企业走访记录" name="visits">
        <div class="toolbar">
          <el-select v-model="visitFilters.companyId" clearable filterable placeholder="全部企业" @change="loadVisits">
            <el-option v-for="company in companies" :key="company.id" :value="company.id" :label="company.companyName" />
          </el-select>
          <el-input-number v-model="visitFilters.year" :min="2000" :max="2100" controls-position="right" @change="loadVisits" />
          <el-select v-model="visitFilters.teacherId" clearable filterable placeholder="走访教师" @change="loadVisits">
            <el-option v-for="teacher in teachers" :key="teacher.id" :value="teacher.id" :label="teacher.name" />
          </el-select>
          <el-button :icon="Search" @click="loadVisits">查询</el-button>
          <el-button :icon="Refresh" @click="resetVisitFilters">重置</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreateVisit()">登记走访</el-button>
        </div>

        <el-table v-loading="loading" :data="visits" border empty-text="暂无走访记录">
          <el-table-column prop="companyName" label="企业" min-width="180" />
          <el-table-column prop="visitDate" label="走访日期" width="120" />
          <el-table-column label="走访教师" min-width="200">
            <template #default="scope">{{ (scope.row.participants || []).map(item => item.teacherName).join('、') || '-' }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
          <el-table-column prop="creatorTeacherName" label="填写人" width="110" />
          <el-table-column prop="createTime" label="填写时间" width="170" />
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="scope">
              <template v-if="canManageVisit(scope.row)">
                <el-button text type="primary" :icon="Edit" @click="openEditVisit(scope.row)" />
                <el-button text type="danger" :icon="Delete" @click="deleteVisit(scope.row)" />
              </template>
              <span v-else class="read-only">只读</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="校内对接人" name="liaisons">
        <div class="toolbar">
          <el-select v-model="liaisonCompanyId" filterable placeholder="请选择企业" @change="loadLiaisons">
            <el-option v-for="company in companies" :key="company.id" :value="company.id" :label="company.companyName" />
          </el-select>
          <el-button type="primary" :icon="Plus" :disabled="!liaisonCompanyId" @click="openAddLiaison">添加对接人</el-button>
        </div>

        <el-table v-loading="loading" :data="liaisons" border empty-text="请选择企业或暂无校内对接人">
          <el-table-column prop="teacherName" label="姓名" min-width="140" />
          <el-table-column prop="teacherAccount" label="教师账号" min-width="160" />
          <el-table-column prop="teacherPhone" label="联系电话" min-width="160" />
          <el-table-column prop="creatorTeacherName" label="添加人" width="110" />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="scope">
              <template v-if="canManageLiaison(scope.row)">
                <el-button text type="danger" :icon="Delete" @click="deleteLiaison(scope.row)" />
              </template>
              <span v-else class="read-only">只读</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="visitDialogVisible" :title="editingVisitId ? '编辑走访记录' : '登记企业走访'" width="620px">
      <el-form label-width="100px">
        <el-form-item label="走访企业" required>
          <el-select v-model="visitForm.companyId" filterable placeholder="请选择企业" style="width: 100%">
            <el-option v-for="company in companies" :key="company.id" :value="company.id" :label="companyLabel(company)">
              <span>{{ company.companyName }}</span>
              <el-tag v-if="company.visitSummary?.visitedThisYear" size="small" type="warning" class="visited-badge">本年度已走访</el-tag>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="走访日期" required>
          <el-date-picker v-model="visitForm.visitDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="走访教师" required>
          <el-select v-model="visitForm.participantTeacherIds" multiple filterable style="width: 100%">
            <el-option v-for="teacher in teachers" :key="teacher.id" :value="teacher.id" :label="`${teacher.name}${teacher.phone ? ` · ${teacher.phone}` : ''}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="visitForm.remark" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visitDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="saveVisit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="liaisonDialogVisible" title="添加校内对接人" width="480px">
      <el-form label-width="90px">
        <el-form-item label="对接教师" required>
          <el-select v-model="liaisonForm.teacherId" filterable placeholder="请选择教师" style="width: 100%">
            <el-option
              v-for="teacher in teachers"
              :key="teacher.id"
              :value="teacher.id"
              :label="`${teacher.name}${teacher.phone ? ` · ${teacher.phone}` : ''}`"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="liaisonDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="addLiaison">添加</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.relations-card {
  margin-bottom: 20px;
  border-radius: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.toolbar > .el-select {
  width: 220px;
}

.toolbar .liaison-picker {
  width: min(520px, 100%);
}

.visited-badge {
  float: right;
  margin-left: 12px;
}

.read-only {
  color: #909399;
  font-size: 12px;
}

@media (max-width: 720px) {
  .toolbar > .el-select,
  .toolbar .liaison-picker {
    width: 100%;
  }
}
</style>
