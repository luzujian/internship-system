<template>
  <div class="scoring-rule-container">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">评分规则管理</h1>
        <p class="page-description">管理AI分析模型的评分规则</p>
      </div>
      <div class="header-illustration">
        <div class="illustration-circle circle-1"></div>
        <div class="illustration-circle circle-2"></div>
      </div>
    </div>

    <!-- 实习已开始提示 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" class="search-form" @keyup.enter="handleFilter">
        <div class="search-row">
          <el-form-item label="分类">
            <el-select v-model="filterCategory" placeholder="选择分类" clearable style="width: 180px">
              <el-option label="全部分类" value="" />
              <el-option
                v-for="cat in existingCategories"
                :key="cat.value"
                :label="cat.label"
                :value="cat.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filterStatus" placeholder="选择状态" clearable style="width: 120px">
              <el-option label="全部状态" value="" />
              <el-option label="启用" :value="1" />
              <el-option label="禁用" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item class="search-actions">
            <el-button type="primary" @click="handleFilter" class="search-btn">
              <el-icon><Search /></el-icon>&nbsp;查询
            </el-button>
            <el-button type="warning" @click="handleReset" class="reset-btn">
              <el-icon><Refresh /></el-icon>&nbsp;重置
            </el-button>
            <el-button type="primary" @click="handleAdd" class="action-btn primary">
              <el-icon><Plus /></el-icon>&nbsp;新增评分规则
            </el-button>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="weight-summary" :class="{ 'invalid': currentTotalWeight !== 100 }">
        <div class="summary-content">
          <el-icon><Document /></el-icon>
          <span class="summary-text">当前总权重：<strong>{{ currentTotalWeight }}</strong></span>
          <el-tag v-if="currentTotalWeight !== 100" type="danger" size="small">总权重必须为100</el-tag>
          <el-button type="primary" size="small" @click="handleEditAllWeights" class="edit-weights-btn">
            <el-icon><Edit /></el-icon>
            &nbsp;调整权重
          </el-button>
        </div>
      </div>

      <el-collapse v-model="activeCategories" class="rules-collapse" v-loading="loading">
        <el-collapse-item v-for="(rules, categoryName) in groupedRules" :key="categoryName" :name="categoryName">
          <template #title>
            <div class="collapse-title">
              <span class="category-icon">
                <el-icon><Document /></el-icon>
              </span>
              <span class="category-name">{{ getCategoryName(categoryName) }}</span>
              <el-tag size="small" class="category-count">{{ rules.length }}条规则</el-tag>
              <el-tag size="small" class="category-weight" type="warning">权重: {{ getCategoryWeight(categoryName) }}</el-tag>
              <el-button
                type="danger"
                link
                size="small"
                class="category-delete-btn"
                @click.stop="handleDeleteCategory(categoryName)"
              >
                <el-icon><Delete /></el-icon>&nbsp
              </el-button>
            </div>
          </template>

          <div class="rules-grid">
            <div
              v-for="rule in sortedRules(rules)"
              :key="rule.id"
              class="rule-card"
              :class="{ 'disabled': rule.status === 0 }"
            >
              <div class="rule-header">
                <div class="score-range" :class="getScoreLevelClass(rule)">
                  {{ rule.minScore }}-{{ rule.maxScore }}分
                </div>
                <div class="rule-actions">
                  <el-button type="primary" link size="small" @click="handleEdit(rule)">
                    <el-icon><Edit /></el-icon>
                  </el-button>
                  <el-button type="danger" link size="small" @click="handleDelete(rule)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
              <div class="rule-content">
                <p class="rule-description">{{ rule.description }}</p>
                <div class="rule-meta">
                  <el-tag v-if="rule.status === 0" type="info" size="small">已禁用</el-tag>
                </div>
              </div>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="700px" @close="handleDialogClose">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item v-if="form.id" label="等级" prop="ruleName">
          <el-select v-model="form.ruleName" placeholder="请选择等级">
            <el-option label="优秀" value="优秀" />
            <el-option label="良好" value="良好" />
            <el-option label="中等" value="中等" />
            <el-option label="及格" value="及格" />
            <el-option label="不及格" value="不及格" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select
            v-model="form.category"
            placeholder="请选择参考的类别或输入所需类别"
            :disabled="!!form.id"
            filterable
            allow-create
            default-first-option
          >
            <el-option
              v-for="cat in availableCategories"
              :key="cat.value"
              :label="cat.label"
              :value="cat.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.id" label="最低分" prop="minScore">
          <el-input-number v-model="form.minScore" :min="0" :max="100" placeholder="请输入最低分" />
        </el-form-item>
        <el-form-item v-if="form.id" label="最高分" prop="maxScore">
          <el-input-number v-model="form.maxScore" :min="0" :max="100" placeholder="请输入最高分" />
        </el-form-item>
        <el-form-item v-if="form.id" label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.id" label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-alert v-if="!form.id && !saving" type="info" :closable="false" show-icon style="margin-bottom: 20px">
          系统将自动为该分类生成五个等级（优秀、良好、中等、及格、不及格）的评分规则，描述由AI智能生成。
        </el-alert>
        <el-alert v-if="!form.id && saving" type="warning" :closable="false" show-icon style="margin-bottom: 20px">
          AI正在为您生成评分规则描述，请稍等...
        </el-alert>
        <div v-if="saving" class="ai-loading-tip">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>AI生成中，预计需要10-20秒，请耐心等待...</span>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false" :disabled="saving">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">{{ saving ? 'AI生成中...' : '保存' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="weightDialogVisible" title="调整类别权重" width="600px" @close="handleWeightDialogClose">
      <el-alert
        title="权重说明"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 20px"
      >
        所有类别的权重总和必须为100，请合理分配各维度的权重。
      </el-alert>

      <el-form :model="weightForm" :rules="weightRules" ref="weightFormRef" label-width="120px">
        <div v-for="(weight, categoryCode) in weightForm.weights" :key="categoryCode" class="weight-item">
          <el-form-item :label="getCategoryName(categoryCode)">
            <el-input-number
              v-model="weightForm.weights[categoryCode]"
              :min="0"
              :max="100"
              placeholder="请输入权重"
            />
          </el-form-item>
        </div>

        <div class="weight-total-display">
            <span class="total-label">总权重：</span>
            <span class="total-value" :class="{ 'valid': totalWeight === 100, 'invalid': totalWeight !== 100 }">
              {{ totalWeight }}
            </span>
            <el-tag v-if="totalWeight === 100" type="success" size="small">✓ 正确</el-tag>
            <el-tag v-else type="danger" size="small">⚠ 必须为100</el-tag>
          </div>
      </el-form>
      <template #footer>
        <el-button @click="weightDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveAllWeights" :loading="saving" :disabled="totalWeight !== 100">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Document, Search, Refresh, Loading } from '@element-plus/icons-vue'
import {
  getScoringRules,
  batchCreateScoringRules,
  updateScoringRule,
  deleteScoringRule,
  deleteScoringRulesByCategory,
  getActiveCategoryWeights,
  batchUpdateCategoryWeights
} from '../../api/counselorAISettings'

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const weightDialogVisible = ref(false)
const dialogTitle = ref('')
const ruleList = ref<any[]>([])
const filterCategory = ref('')
const filterStatus = ref('')
const activeCategories = ref<any[]>([])
const formRef = ref<any>(null)
const weightFormRef = ref<any>(null)

const counselorId = ref<number>(0)

const categoryWeights = ref<Record<string, number>>({})

const currentTotalWeight = computed(() => {
  const existingCategories = Object.keys(groupedRules.value)
  let total = 0
  existingCategories.forEach(category => {
    total += categoryWeights.value[category] || 0
  })
  return total
})

const totalWeight = computed(() => {
  const weights = weightDialogVisible.value ? weightForm.value.weights : categoryWeights.value
  return Object.values(weights).reduce((sum: number, weight: any) => sum + (weight || 0), 0)
})

const form = ref({
  id: null as number | null,
  ruleName: '',
  category: '',
  minScore: 0,
  maxScore: 100,
  status: 1,
  description: '',
  evaluationCriteria: '',
  applicableScenarios: ''
})

const weightForm = ref({
  weights: {} as Record<string, number>
})

const rules = {
  ruleName: [
    { required: true, message: '请输入规则名称', trigger: 'blur' }
  ],
  category: [
    { required: true, message: '请选择分类', trigger: 'change' }
  ],
  minScore: [
    { required: true, message: '请输入最低分', trigger: 'blur' }
  ],
  maxScore: [
    { required: true, message: '请输入最高分', trigger: 'blur' }
  ]
}

const weightRules = {}

const allCategories = [
  { label: '实习表现', value: 'internship_performance' },
  { label: '学术成果', value: 'academic_achievement' },
  { label: '团队协作', value: 'team_collaboration' },
  { label: '创新能力', value: 'innovation_ability' },
  { label: '团队协作能力', value: 'teamwork' },
  { label: '问题解决能力', value: 'problemSolving' },
  { label: '沟通表达能力', value: 'communication' },
  { label: '学习能力', value: 'learningAbility' },
  { label: '职业素养', value: 'professionalism' },
  { label: '实习态度', value: '实习态度' },
  { label: '工作能力', value: '工作能力' },
  { label: '沟通能力', value: '沟通能力' },
  { label: '创新思维', value: '创新思维' },
  { label: '责任心', value: '责任心' },
  { label: '专业技能', value: '专业技能' }
]

const availableCategories = computed(() => {
  const existingCategories = new Set(ruleList.value.map(rule => rule.category))
  return allCategories.filter(cat => !existingCategories.has(cat.value))
})

const existingCategories = computed(() => {
  const categories = new Set(ruleList.value.map(rule => rule.category))
  return Array.from(categories).map(category => {
    const found = allCategories.find(cat => cat.value === category)
    return found || { label: category, value: category }
  })
})

const groupedRules = computed(() => {
  const grouped: Record<string, any[]> = {}
  ruleList.value.forEach(rule => {
    if (!grouped[rule.category]) {
      grouped[rule.category] = []
    }
    grouped[rule.category].push(rule)
  })
  return grouped
})

const sortedRules = (rules: any[]) => {
  return [...rules].sort((a, b) => b.maxScore - a.maxScore)
}

const getScoreLevelClass = (rule: any) => {
  if (rule.maxScore >= 90) return 'excellent'
  if (rule.maxScore >= 80) return 'good'
  if (rule.maxScore >= 70) return 'medium'
  if (rule.maxScore >= 60) return 'pass'
  return 'fail'
}

const fetchRules = async () => {
  loading.value = true
  try {
    const response = await getScoringRules(counselorId.value)
    if (response.code === 200) {
      ruleList.value = response.data || []
    } else {
      ElMessage.error(response.message || '获取评分规则列表失败')
    }
  } catch (error) {
    ElMessage.error('获取评分规则列表失败')
  } finally {
    loading.value = false
  }
}

const fetchCategoryWeights = async () => {
  try {
    const response = await getActiveCategoryWeights(counselorId.value)
    if (response.code === 200) {
      const weights = response.data?.weights || []
      const weightMap: Record<string, number> = {}
      weights.forEach((item: any) => {
        weightMap[item.categoryCode] = item.weight
      })
      categoryWeights.value = weightMap
    }
  } catch (error) {
    console.error('获取类别权重失败:', error)
  }
}

const handleFilter = async () => {
  loading.value = true
  try {
    let filteredRules = [...ruleList.value]

    if (filterCategory.value) {
      filteredRules = filteredRules.filter(rule => rule.category === filterCategory.value)
    }

    if (filterStatus.value !== '') {
      filteredRules = filteredRules.filter(rule => rule.status === filterStatus.value)
    }

    ruleList.value = filteredRules
  } catch (error) {
    ElMessage.error('筛选失败')
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  filterCategory.value = ''
  filterStatus.value = ''
  fetchRules()
}

const handleAdd = () => {
  dialogTitle.value = '新增评分规则'
  form.value = {
    id: null,
    ruleName: '',
    category: '',
    minScore: 0,
    maxScore: 100,
    status: 1,
    description: '',
    evaluationCriteria: '',
    applicableScenarios: ''
  }
  dialogVisible.value = true

  nextTick(() => {
    if (formRef.value) {
      formRef.value.clearValidate()
    }
  })
}

const handleEdit = (row: any) => {
  dialogTitle.value = '编辑评分规则'
  form.value = {
    id: row.id,
    ruleName: row.ruleName,
    category: row.category,
    minScore: row.minScore,
    maxScore: row.maxScore,
    status: row.status,
    description: row.description || '',
    evaluationCriteria: row.evaluationCriteria || '',
    applicableScenarios: row.applicableScenarios || ''
  }
  dialogVisible.value = true

  nextTick(() => {
    if (formRef.value) {
      formRef.value.clearValidate()
    }
  })
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定要删除评分规则"${row.ruleName}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const response = await deleteScoringRule(row.id)
    if (response.code === 200) {
      ElMessage.success('删除成功')
      fetchRules()
    } else {
      ElMessage.error(response.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleDeleteCategory = async (categoryName: string) => {
  try {
    const rules = groupedRules.value[categoryName]
    const categoryDisplayName = getCategoryName(categoryName)

    await ElMessageBox.confirm(
      `确定要删除"${categoryDisplayName}"类别及其所有${rules.length}条评分规则吗？此操作不可恢复！`,
      '删除类别',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )

    const response = await deleteScoringRulesByCategory(counselorId.value, categoryName)
    if (response.code === 200) {
      ElMessage.success(`已删除"${categoryDisplayName}"类别及其所有评分规则`)
      await fetchRules()
      await nextTick()
      await fetchCategoryWeights()
    } else {
      ElMessage.error(response.message || '删除类别失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除类别失败')
    }
  }
}

const handleSave = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }

  saving.value = true
  try {
    let response
    if (form.value.id) {
      response = await updateScoringRule(form.value.id, form.value)
    } else {
      response = await batchCreateScoringRules(counselorId.value, form.value.category)
    }

    if (response.code === 200) {
      ElMessage.success('保存成功')
      dialogVisible.value = false
      await fetchRules()
      await nextTick()
      await fetchCategoryWeights()
    } else {
      ElMessage.error(response.message || '保存失败')
    }
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const handleDialogClose = () => {
  nextTick(() => {
    if (formRef.value) {
      formRef.value.resetFields()
    }
  })
}

const handleWeightDialogClose = () => {
  nextTick(() => {
    if (weightFormRef.value) {
      weightFormRef.value.resetFields()
    }
  })
}

const getCategoryName = (category: string) => {
  const map: Record<string, string> = {
    'internship_performance': '实习表现',
    'academic_achievement': '学术成果',
    'team_collaboration': '团队协作',
    'innovation_ability': '创新能力',
    'teamwork': '团队协作能力',
    'problemSolving': '问题解决能力',
    'problemsolving': '问题解决能力',
    'communication': '沟通表达能力',
    'learningAbility': '学习能力',
    'learningability': '学习能力',
    'professionalism': '职业素养',
    '实习态度': '实习态度',
    '工作能力': '工作能力',
    '沟通能力': '沟通能力',
    '学习能力': '学习能力',
    '创新思维': '创新思维',
    '责任心': '责任心',
    '专业技能': '专业技能'
  }
  return map[category] || category
}

const getCategoryWeight = (categoryName: string) => {
  return categoryWeights.value[categoryName] || 0
}

const handleEditAllWeights = () => {
  const existingCategories = Object.keys(groupedRules.value)
  const weights: Record<string, number> = {}

  existingCategories.forEach(category => {
    weights[category] = categoryWeights.value[category] || 0
  })

  weightForm.value.weights = weights
  weightDialogVisible.value = true
}

const handleSaveAllWeights = async () => {
  if (totalWeight.value !== 100) {
    ElMessage.warning('总权重必须为100')
    return
  }

  saving.value = true
  try {
    const weightList = Object.entries(weightForm.value.weights).map(([categoryCode, weight]) => ({
      counselorId: counselorId.value,
      categoryCode,
      categoryName: getCategoryName(categoryCode),
      weight,
      status: 1
    }))

    const response = await batchUpdateCategoryWeights(weightList)
    if (response.code === 200) {
      ElMessage.success('权重更新成功')
      weightDialogVisible.value = false
      await fetchCategoryWeights()
    } else {
      ElMessage.error(response.message || '权重更新失败')
    }
  } catch (error) {
    ElMessage.error('权重更新失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  const currentRole = localStorage.getItem('current_role') || 'ROLE_TEACHER'
  const rolePrefix = currentRole === 'ROLE_TEACHER' ? 'teacher_' : ''
  const id = localStorage.getItem(rolePrefix + 'userId_' + currentRole) || localStorage.getItem('teacherId')
  if (id) {
    counselorId.value = parseInt(id)
  }
  fetchRules()
  fetchCategoryWeights()
})
</script>

<style scoped>
.scoring-rule-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 32px;
  margin-bottom: 24px;
  position: relative;
}

.header-content {
  flex: 1;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 8px 0;
  color: #303133;
}

.page-description {
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

.circle-1 {
  width: 60px;
  height: 60px;
  top: 0;
  right: 0;
  animation-delay: 0s;
}

.circle-2 {
  width: 40px;
  height: 40px;
  bottom: 10px;
  right: 20px;
  animation-delay: 3s;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8px);
  }
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

.summary-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.weight-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 24px;
  background: #f8f9fa;
  border-bottom: 1px solid #e4e7ed;
  border-radius: 8px 8px 0 0;
  margin-bottom: 20px;
}

.weight-summary.invalid {
  background: #fef0f0;
  border-bottom: 2px solid #f56c6c;
}

.summary-text {
  font-size: 14px;
  color: #606266;
}

.summary-text strong {
  font-size: 18px;
  color: #303133;
  margin: 0 4px;
}

.search-form {
  width: 100%;
}

.search-row {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  flex-wrap: wrap;
}

.search-row .el-form-item {
  margin-bottom: 0;
  flex: 0 0 auto;
}

.search-actions {
  flex: 0 0 auto;
  margin-left: auto;
}

.search-btn,
.reset-btn {
  border-radius: 8px;
  padding: 10px 20px;
  margin-right: 8px;
}

.search-btn:last-child,
.reset-btn:last-child {
  margin-right: 0;
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

.action-btn.primary {
  background: linear-gradient(135deg, #409EFF 0%, #409EFF 100%);
  border: none;
  color: white;
}

.rules-collapse {
  border: none;
  background: transparent;
}

.rules-collapse :deep(.el-collapse-item__header) {
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 12px;
  padding: 0 16px;
  height: 56px;
  line-height: 56px;
  transition: all 0.3s;
}

.rules-collapse :deep(.el-collapse-item__header:hover) {
  background: #e8f0fe;
}

.rules-collapse :deep(.el-collapse-item__wrap) {
  border: none;
  background: transparent;
}

.collapse-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.category-icon {
  color: #409EFF;
  font-size: 20px;
}

.category-name {
  flex: 1;
}

.category-count {
  background: #67c23a;
  color: white;
  border-radius: 12px;
  padding: 2px 10px;
  font-size: 12px;
}

.category-weight {
  background: #e6a23c;
  color: white;
  border-radius: 12px;
  padding: 2px 10px;
  font-size: 12px;
}

.category-delete-btn {
  margin-left: auto;
  color: #f56c6c;
  font-size: 13px;
}

.category-delete-btn:hover {
  color: #f78989;
  background: rgba(245, 108, 108, 0.1);
}

.category-weight:hover {
  background: #d99a3b;
  transform: scale(1.05);
}

.weight-item {
  margin-bottom: 16px;
}

.weight-total-display {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}

.total-label {
  font-size: 14px;
  color: #606266;
  font-weight: 600;
}

.total-value {
  font-size: 24px;
  font-weight: 700;
  padding: 0 8px;
}

.total-value.valid {
  color: #67c23a;
}

.total-value.invalid {
  color: #f56c6c;
}

.edit-weights-btn {
  margin-left: auto;
}

.rules-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  padding: 16px 0;
}

.rule-card {
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  padding: 16px;
  transition: all 0.3s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.rule-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  border-color: #409EFF;
}

.rule-card.disabled {
  opacity: 0.6;
  background: #f5f7fa;
}

.rule-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.score-range {
  padding: 6px 12px;
  border-radius: 6px;
  font-weight: 600;
  font-size: 14px;
  color: white;
}

.score-range.excellent {
  background: linear-gradient(135deg, #67c23a 0%, #85ce61 100%);
}

.score-range.good {
  background: linear-gradient(135deg, #409EFF 0%, #66b1ff 100%);
}

.score-range.medium {
  background: linear-gradient(135deg, #e6a23c 0%, #f0c78a 100%);
}

.score-range.pass {
  background: linear-gradient(135deg, #909399 0%, #b1b3b8 100%);
}

.score-range.fail {
  background: linear-gradient(135deg, #f56c6c 0%, #ff8c8c 100%);
}

.rule-actions {
  display: flex;
  gap: 4px;
}

.rule-content {
  flex: 1;
}

.rule-description {
  margin: 0 0 12px 0;
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
  min-height: 40px;
}

.rule-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.ai-loading-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px;
  background: #fff8e1;
  border: 1px solid #ffe082;
  border-radius: 8px;
  color: #ff9800;
  font-size: 14px;
  margin-top: 12px;
}

.ai-loading-tip .el-icon {
  font-size: 18px;
}
</style>
