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

    <!-- 搜索区域卡片 -->
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
            <el-button v-if="authStore.hasPermission('scoring:view')" type="primary" @click="handleAdd" class="action-btn primary">
              <el-icon><Plus /></el-icon>&nbsp;新增评分规则
            </el-button>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <!-- 数据列表卡片 -->
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
        <el-alert v-if="!form.id" type="info" :closable="false" show-icon style="margin-bottom: 20px">
          系统将自动为该分类生成五个等级（优秀、良好、中等、及格、不及格）的评分规则，描述由AI智能生成。
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
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
import logger from '@/utils/logger'
import { ref, onMounted, nextTick, computed } from 'vue'
import type { ScoringRule } from '@/types/admin'

import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Document, Search, Refresh } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()

const loading = ref<boolean>(false)
const saving = ref<boolean>(false)
const dialogVisible = ref<boolean>(false)
const weightDialogVisible = ref<boolean>(false)
const dialogTitle = ref<string>('')
const ruleList = ref<unknown[]>([])
const filterCategory = ref<string>('')
const filterStatus = ref<string>('')
const activeCategories = ref<unknown[]>([])
const formRef = ref<InstanceType<typeof ElForm> | null>(null)
const weightFormRef = ref<InstanceType<typeof ElForm> | null>(null)

const categoryWeights = ref<Record<string, unknown>>({})

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
  return Object.values(weights).reduce((sum, weight) => sum + (weight || 0), 0)
})

const form = ref({
  id: null,
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
  weights: {
    'teamwork': 20,
    'problemSolving': 20,
    'communication': 20,
    'learningAbility': 20,
    'professionalism': 20
  }
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
  { label: '职业素养', value: 'professionalism' }
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
  const grouped = {}
  ruleList.value.forEach(rule => {
    if (!grouped[rule.category]) {
      grouped[rule.category] = []
    }
    grouped[rule.category].push(rule)
  })
  return grouped
})

const sortedRules = (rules) => {
  return [...rules].sort((a, b) => b.maxScore - a.maxScore)
}

const getScoreLevelClass = (rule) => {
  if (rule.maxScore >= 90) return 'excellent'
  if (rule.maxScore >= 80) return 'good'
  if (rule.maxScore >= 70) return 'medium'
  if (rule.maxScore >= 60) return 'pass'
  return 'fail'
}

const fetchRules = async (): Promise<void> => {
  loading.value = true
  try {
    const response = await request.get('/admin/scoring-rule')
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

const fetchCategoryWeights = async (): Promise<void> => {
  try {
    const response = await request.get('/admin/category-weight/active')
    if (response.code === 200) {
      const weights = response.data || []
      const weightMap = {}
      weights.forEach(item => {
        weightMap[item.categoryCode] = item.weight
      })
      categoryWeights.value = weightMap
      logger.log('获取到的权重数据:', weightMap)
    }
  } catch (error) {
    logger.error('获取类别权重失败:', error)
  }
}

const handleFilter = async (): Promise<void> => {
  loading.value = true
  try {
    let url = '/admin/scoring-rule'
    if (filterCategory.value) {
      url += `/category/${filterCategory.value}`
    }
    if (filterStatus.value !== '') {
      url += `/status/${filterStatus.value}`
    }
    
    const response = await request.get(url)
    if (response.code === 200) {
      ruleList.value = response.data || []
    } else {
      ElMessage.error(response.message || '筛选失败')
    }
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
    weight: 1,
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

const handleEdit = (row) => {
  dialogTitle.value = '编辑评分规则'
  form.value = {
    id: row.id,
    ruleName: row.ruleName,
    category: row.category,
    minScore: row.minScore,
    maxScore: row.maxScore,
    weight: row.weight,
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

const handleDelete = async (row): Promise<void> => {
  try {
    await ElMessageBox.confirm(`确定要删除评分规则"${row.ruleName}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const response = await request.delete(`/admin/scoring-rule/${row.id}`)
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

const handleDeleteCategory = async (categoryName): Promise<void> => {
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
    
    const deletePromises = rules.map(rule => request.delete(`/admin/scoring-rule/${rule.id}`))
    await Promise.all(deletePromises)
    
    await request.delete(`/admin/category-weight/category/${categoryName}`)
    
    ElMessage.success(`已删除"${categoryDisplayName}"类别及其所有评分规则`)
    await fetchRules()
    await nextTick()
    await redistributeWeights()
    await fetchCategoryWeights()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除类别失败')
    }
  }
}

const handleSave = async (): Promise<void> => {
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
      response = await request.put(`/admin/scoring-rule/${form.value.id}`, form.value)
    } else {
      const categoryName = getCategoryName(form.value.category)
      const rules = [
        {
          ruleName: '优秀',
          category: form.value.category,
          minScore: 90,
          maxScore: 100,
          status: 1,
          description: '',
          evaluationCriteria: '',
          applicableScenarios: ''
        },
        {
          ruleName: '良好',
          category: form.value.category,
          minScore: 80,
          maxScore: 89,
          status: 1,
          description: '',
          evaluationCriteria: '',
          applicableScenarios: ''
        },
        {
          ruleName: '中等',
          category: form.value.category,
          minScore: 70,
          maxScore: 79,
          status: 1,
          description: '',
          evaluationCriteria: '',
          applicableScenarios: ''
        },
        {
          ruleName: '及格',
          category: form.value.category,
          minScore: 60,
          maxScore: 69,
          status: 1,
          description: '',
          evaluationCriteria: '',
          applicableScenarios: ''
        },
        {
          ruleName: '不及格',
          category: form.value.category,
          minScore: 0,
          maxScore: 59,
          status: 1,
          description: '',
          evaluationCriteria: '',
          applicableScenarios: ''
        }
      ]
      response = await request.post('/admin/scoring-rule/batch', { category: form.value.category, rules })
    }

    if (response.code === 200) {
      ElMessage.success('保存成功')
      dialogVisible.value = false
      await fetchRules()
      await nextTick()
      await redistributeWeights()
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

const getRuleTypeName = (ruleType) => {
  const map = {
    'quantitative': '定量评分',
    'qualitative': '定性评分',
    'mixed': '混合评分'
  }
  return map[ruleType] || ruleType
}

const getCategoryName = (category) => {
  const map = {
    'internship_performance': '实习表现',
    'academic_achievement': '学术成果',
    'team_collaboration': '团队协作',
    'innovation_ability': '创新能力',
    'teamwork': '团队协作能力',
    'problemSolving': '问题解决能力',
    'communication': '沟通表达能力',
    'learningAbility': '学习能力',
    'professionalism': '职业素养'
  }
  
  if (map[category]) {
    return map[category]
  }
  
  if (category.includes('_')) {
    const parts = category.split('_')
    const capitalizedParts = parts.map(part => {
      return part.charAt(0).toUpperCase() + part.slice(1)
    })
    return capitalizedParts.join(' ')
  }
  
  return category.charAt(0).toUpperCase() + category.slice(1)
}

const getCategoryWeight = (categoryName) => {
  const weight = categoryWeights.value[categoryName] || 0
  logger.log(`获取类别权重 - 类别: ${categoryName}, 权重: ${weight}, 所有权重:`, categoryWeights.value)
  return weight
}

const handleEditAllWeights = () => {
  const existingCategories = Object.keys(groupedRules.value)
  
  if (existingCategories.length === 0) {
    ElMessage.warning('暂无类别，请先添加类别')
    return
  }
  
  const newWeights = {}
  
  existingCategories.forEach((category) => {
    newWeights[category] = categoryWeights.value[category] || 0
  })
  
  weightForm.value.weights = newWeights
  weightDialogVisible.value = true
}

const handleSaveAllWeights = async (): Promise<void> => {
  try {
    saving.value = true
    
    const weights = weightForm.value.weights
    const categoryWeightsList = []
    
    for (const [categoryCode, weight] of Object.entries(weights)) {
      categoryWeightsList.push({
        categoryCode: categoryCode,
        categoryName: getCategoryName(categoryCode),
        weight: weight,
        status: 1
      })
    }
    
    const response = await request.post('/admin/category-weight/batch', categoryWeightsList)

    if (response.code === 200) {
      await fetchCategoryWeights()
      ElMessage.success('权重保存成功')
      weightDialogVisible.value = false
    } else {
      ElMessage.error(response.message || '保存失败')
    }
  } catch (error) {
    logger.error('保存权重失败:', error)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const redistributeWeights = async (): Promise<void> => {
  try {
    await nextTick()
    
    logger.log('开始重新分配权重，当前规则列表:', ruleList.value)
    logger.log('当前分组规则:', groupedRules.value)
    
    const existingCategories = Object.keys(groupedRules.value)
    const categoryCount = existingCategories.length
    
    logger.log('现有类别数量:', categoryCount, '类别列表:', existingCategories)
    
    if (categoryCount === 0) return
    
    const weightPerCategory = Math.floor(100 / categoryCount)
    const remainder = 100 % categoryCount
    
    logger.log('每个类别的权重:', weightPerCategory, '余数:', remainder)
    
    const newWeights = {}
    existingCategories.forEach((category, index) => {
      newWeights[category] = weightPerCategory + (index < remainder ? 1 : 0)
    })
    
    logger.log('计算出的新权重:', newWeights)
    
    const categoryWeightsList = []
    for (const [categoryCode, weight] of Object.entries(newWeights)) {
      categoryWeightsList.push({
        categoryCode: categoryCode,
        categoryName: getCategoryName(categoryCode),
        weight: weight,
        status: 1
      })
    }
    
    logger.log('准备批量更新权重:', categoryWeightsList)
    
    const response = await request.post('/admin/category-weight/batch', categoryWeightsList)
    
    if (response.code === 200) {
      logger.log('权重批量更新成功')
      await fetchCategoryWeights()
    } else {
      logger.error('权重批量更新失败:', response.message)
      ElMessage.error('自动分配权重失败: ' + (response.message || '未知错误'))
    }
  } catch (error) {
    logger.error('自动分配权重失败:', error)
    ElMessage.error('自动分配权重失败，请手动调整权重')
  }
}

onMounted(() => {
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
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  border-radius: 16px;
  padding: 24px 32px;
  color: white;
  margin-bottom: 24px;
  box-shadow: 0 8px 25px rgba(64, 158, 255, 0.25);
  position: relative;
  overflow: hidden;
}

.page-header::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -50%;
  width: 100%;
  height: 100%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
  transform: rotate(30deg);
}

.header-content {
  flex: 1;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 8px 0;
  color: white;
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

/* 卡片通用样式 */
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

/* 搜索表单样式 */
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
</style>
