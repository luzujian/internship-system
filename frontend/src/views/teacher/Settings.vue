<template>
  <div class="settings-container">
    <!-- 设置页面头部 -->
    <div v-if="showSettings" class="page-header fade-in" style="animation-delay: 0.1s">
      <h2>重要设置</h2>
      <p class="page-subtitle" v-if="activeTab === 'preNode'">配置下点前实习时间节点</p>
      <p class="page-subtitle" v-else-if="activeTab === 'postNode'">配置下点后实习时间节点</p>
    </div>

    <!-- 设置选项卡 -->
    <div class="settings-tabs fade-in" style="animation-delay: 0.2s">
      <button
        v-for="tab in settingsTabs"
        :key="tab.key"
        :class="['tab-btn', { active: activeTab === tab.key }]"
        @click="activeTab = tab.key"
      >
        {{ tab.name }}
      </button>
    </div>

    <!-- 下点前时间节点设置 -->
    <div v-if="activeTab === 'preNode'" class="settings-section card fade-in" style="animation-delay: 0.3s">
      <h3>下点前时间节点设置</h3>
      <div class="settings-form">
        <div class="form-group fade-in" :style="{ animationDelay: `${0.3 + index * 0.1}s`, '--item-color': item.color }" v-for="(item, index) in preInternshipNodesItems" :key="item.key">
          <label>{{ item.label }}：</label>
          <div class="form-control" :style="{ borderColor: item.color }">
            <input :type="item.type" v-model.number="internshipSettings[item.key]" :min="item.min" :max="item.max" v-if="item.type === 'number'" />
            <input :type="item.type" v-model="internshipSettings[item.key]" v-else-if="item.type === 'date'" />
            <span class="form-unit" v-if="item.unit">{{ item.unit }}</span>
          </div>
          <p class="form-hint" :style="{ color: item.color }">
            <span class="hint-icon">💡</span> {{ item.hint }}
          </p>
        </div>
      </div>

      <!-- 设置操作按钮 -->
      <div class="settings-actions-card card fade-in" style="animation-delay: 0.6s">
        <div class="settings-actions-content">
          <button class="reset-btn btn btn-default" @click="resetSettings">重置默认值</button>
          <button class="save-btn btn btn-primary" @click="saveSettings">保存设置</button>
        </div>
      </div>
    </div>

    <!-- 下点后时间节点设置 -->
    <div v-if="activeTab === 'postNode'" class="settings-section card fade-in" style="animation-delay: 0.3s">
      <h3>下点后时间节点设置</h3>
      <div class="settings-form">
        <div class="form-group fade-in" :style="{ animationDelay: `${0.3 + index * 0.1}s`, '--item-color': item.color }" v-for="(item, index) in postInternshipNodesItems" :key="item.key">
          <label>{{ item.label }}：</label>
          <div class="form-control" :style="{ borderColor: item.color }">
            <input :type="item.type" v-model.number="internshipSettings[item.key]" :min="item.min" :max="item.max" v-if="item.type === 'number'" />
            <input :type="item.type" v-model="internshipSettings[item.key]" v-else-if="item.type === 'date'" />
            <span class="form-unit" v-if="item.unit">{{ item.unit }}</span>
          </div>
          <p class="form-hint" :style="{ color: item.color }">
            <span class="hint-icon">💡</span> {{ item.hint }}
          </p>
        </div>
      </div>

      <!-- 设置操作按钮 -->
      <div class="settings-actions-card card fade-in" style="animation-delay: 0.6s">
        <div class="settings-actions-content">
          <button class="reset-btn btn btn-default" @click="resetSettings">重置默认值</button>
          <button class="save-btn btn btn-primary" @click="saveSettings">保存设置</button>
        </div>
      </div>
    </div>

    <!-- 操作反馈提示 -->
    <div v-if="showFeedback" :class="['feedback-toast', feedbackType]">
      <span>{{ feedbackMessage }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getInternshipNodes, updateInternshipNodes } from '../../api/teacherSettings'

const router = useRouter()
const route = useRoute()

const teacherType = localStorage.getItem('teacherType') || ''

// 设置选项卡
// - 所有教师类型都显示下点前/下点后时间节点设置
// - AI分析配置在独立的 /teacher/ai-scoring-config 页面
const activeTab = ref('preNode')
const settingsTabs = computed(() => {
  // 辅导员、系室教师和学院教师都显示下点前/下点后设置
  if (teacherType === 'COUNSELOR' || teacherType === 'DEPARTMENT' || teacherType === 'COLLEGE') {
    return [
      { key: 'preNode', name: '下点前设置' },
      { key: 'postNode', name: '下点后设置' }
    ]
  }
  return []
})

// 是否显示设置页面
const showSettings = computed(() => {
  return settingsTabs.value.length > 0
})

// 下点前时间节点设置项 - 系室教师
const preInternshipNodesItems = ref([
  {
    key: 'applicationStartTime',
    label: '实习应聘开始时间',
    type: 'date',
    hint: '学生开始实习应聘的时间',
    color: '#1890ff'
  },
  {
    key: 'applicationEndTime',
    label: '实习应聘截止时间',
    type: 'date',
    hint: '学生结束实习应聘的时间',
    color: '#1890ff'
  },
  {
    key: 'companyConfirmationDeadline',
    label: '实习单位确认截止日期',
    type: 'date',
    hint: '学生确认实习单位的最后期限',
    color: '#52c41a'
  },
  {
    key: 'delayApplicationDeadline',
    label: '延迟实习申请截止日期',
    type: 'date',
    hint: '学生申请延迟实习的最后期限',
    color: '#faad14'
  }
])

// 下点后时间节点设置项 - 学院教师
const postInternshipNodesItems = ref([
  {
    key: 'startDate',
    label: '实习开始日期',
    type: 'date',
    hint: '学生开始实习的日期',
    color: '#1890ff'
  },
  {
    key: 'endDate',
    label: '实习结束日期',
    type: 'date',
    hint: '学生实习结束的日期',
    color: '#52c41a'
  },
  {
    key: 'reportCycle',
    label: '实习心得提交周期',
    type: 'number',
    unit: '天',
    hint: '学生提交实习心得的周期',
    color: '#722ed1'
  },
  {
    key: 'reportDeadline',
    label: '实习报告提交截止日期',
    type: 'date',
    hint: '学生提交实习报告的最后期限',
    color: '#faad14'
  },
  {
    key: 'evaluationDeadline',
    label: '实习评价截止日期',
    type: 'date',
    hint: '教师完成实习评价的最后期限',
    color: '#eb2f96'
  }
])

// 下点后时间节点设置项 - 学院教师
// 实习设置
const internshipSettings = ref({
  applicationStartTime: '2026-03-01', // 实习应聘开始时间
  applicationEndTime: '2026-05-31', // 实习应聘截止时间
  companyConfirmationDeadline: '2026-06-15', // 实习单位确认截止日期
  delayApplicationDeadline: '2026-06-30', // 延迟实习申请截止日期
  reportCycle: 7, // 实习心得提交周期（天）
  startDate: '2026-07-01', // 实习开始日期
  endDate: '2026-12-31', // 实习结束日期
  reportDeadline: '2027-01-15', // 实习报告提交截止日期
  evaluationDeadline: '2027-01-31' // 实习评价截止日期
})



// 操作反馈
const showFeedback = ref(false)
const feedbackMessage = ref('')
const feedbackType = ref('success') // success or error

// 显示操作反馈
const showOperationFeedback = (message: string, type: 'success' | 'error' = 'success') => {
  feedbackMessage.value = message
  feedbackType.value = type
  showFeedback.value = true
  setTimeout(() => {
    showFeedback.value = false
  }, 3000)
}

// 保存设置
const saveSettings = async () => {
  try {
    if (activeTab.value === 'preNode' || activeTab.value === 'postNode') {
      await updateInternshipNodes(internshipSettings.value)
      showOperationFeedback('实习时间节点设置保存成功！')
    }
  } catch (error: any) {
    console.error('保存设置失败:', error)
    showOperationFeedback(error.message || '保存设置失败', 'error')
  }
}

// 重置默认值
const resetSettings = async () => {
  const today = new Date()
  const formatDate = (date: Date) => {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  }

  internshipSettings.value = {
    applicationStartTime: formatDate(today),
    applicationEndTime: formatDate(today),
    companyConfirmationDeadline: formatDate(today),
    delayApplicationDeadline: formatDate(today),
    reportCycle: 7,
    approvalTimeLimit: 3,
    startDate: formatDate(today),
    endDate: formatDate(today),
    reportDeadline: formatDate(today),
    evaluationDeadline: formatDate(today)
  }

  try {
    await updateInternshipNodes(internshipSettings.value)
    console.log('重置默认设置并保存成功')
    showOperationFeedback('设置已重置为默认值！')
  } catch (error: any) {
    console.error('重置默认设置失败:', error)
    showOperationFeedback(error.message || '重置默认设置失败', 'error')
  }
}

// 初始化加载设置
const loadSettings = async () => {
  try {
    // 所有教师类型都加载下点前/下点后时间节点设置
    const internshipResponse = await getInternshipNodes()
    if (internshipResponse && internshipResponse.data) {
      Object.assign(internshipSettings.value, internshipResponse.data)
    }

    console.log('从后端加载设置成功')
  } catch (error: any) {
    console.error('从后端加载设置失败:', error)
    showOperationFeedback('加载设置失败，请稍后重试', 'error')
  }
}

// 页面加载时加载设置
onMounted(() => {
  loadSettings()
})
</script>

<style scoped>
.settings-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.page-header {
  margin-bottom: 16px;
}

.page-header h2 {
  font-size: 28px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.page-subtitle {
  font-size: 14px;
  color: #666;
  margin: 8px 0 0 0;
}

/* 设置选项卡 */
.settings-tabs {
  display: flex;
  gap: 12px;
  padding: 16px;
  border-radius: var(--radius-lg);
  transition: all var(--transition-normal);
}

.settings-tabs:hover {
  box-shadow: var(--shadow-lg);
}

.tab-btn {
  flex: 1;
  padding: 14px 20px;
  background-color: #fafafa;
  border: none;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 600;
  color: #666;
  cursor: pointer;
  transition: all var(--transition-normal);
  box-shadow: var(--shadow-sm);
}

.tab-btn:hover {
  background-color: #f0f7ff;
  color: #1890ff;
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

.tab-btn.active {
  background: var(--color-primary);
  color: white;
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

/* 设置区域 */
.settings-section {
  padding: 28px;
  border-radius: var(--radius-lg);
  transition: all var(--transition-normal);
}

.settings-section.ai-scoring {
  max-width: 650px;
  margin: 0 auto;
  padding: 50px 50px;
}

.settings-section:hover {
  box-shadow: var(--shadow-lg);
}

.settings-section h3 {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-primary);
  margin: 0 0 32px 0;
}

/* 设置表单 */
.settings-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 24px;
  background-color: white;
  border-radius: 12px;
  transition: all 0.3s ease;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  border: 2px solid transparent;
  border-left: 4px solid var(--item-color, #1890ff);
  background-color: rgba(var(--item-color-rgb, 24, 144, 255), 0.03);
}

.form-group:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  border-color: var(--item-color, #1890ff);
  background-color: rgba(var(--item-color-rgb, 24, 144, 255), 0.06);
}

.form-group label {
  font-size: 16px;
  font-weight: 600;
  color: var(--item-color, #1890ff);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: flex;
  align-items: center;
  gap: 8px;
}

.form-group label::before {
  content: '';
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: var(--item-color, #1890ff);
}

.form-control {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  padding: 8px;
  background-color: white;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.form-control input[type="number"],
.form-control input[type="date"],
.form-control select {
  flex: 1;
  padding: 12px 16px;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.form-control input[type="number"]:focus,
.form-control input[type="date"]:focus,
.form-control select:focus {
  outline: none;
  border-color: var(--item-color, #1890ff);
  box-shadow: 0 0 0 3px rgba(var(--item-color-rgb, 24, 144, 255), 0.15);
}

.form-unit {
  font-size: 14px;
  color: var(--item-color, #1890ff);
  white-space: nowrap;
  font-weight: 700;
  background-color: rgba(var(--item-color-rgb, 24, 144, 255), 0.1);
  padding: 4px 12px;
  border-radius: 16px;
}

.form-hint {
  font-size: 13px;
  margin: 0;
  padding-top: 4px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: all 0.3s ease;
  line-height: 1.5;
}

.hint-icon {
  font-size: 16px;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.1);
  }
}

/* 响应式调整 */
@media (max-width: 768px) {
  .settings-form {
    grid-template-columns: 1fr;
  }

  .form-group {
    padding: 16px;
  }
}

/* 紧凑表单组样式 */
.form-group.compact {
  padding: 10px 14px;
  gap: 4px;
  margin-bottom: 8px;
  font-size: 12px;
  min-height: 60px;
}

.form-group.compact .form-control input[type="checkbox"] {
  width: 14px;
  height: 14px;
}

.form-group.compact .form-control select {
  padding: 6px 10px;
  font-size: 12px;
}

/* 表单控件样式补充 */
.form-control input[type="checkbox"] {
  width: 18px;
  height: 18px;
  transition: all 0.3s ease;
  accent-color: var(--item-color, #1890ff);
}

.form-control input[type="checkbox"]:checked {
  accent-color: var(--item-color, #1890ff);
}

/* 设置表单布局优化 */
.settings-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 24px;
}

/* AI评分配置表单 - 居中显示 */
.settings-section.ai-scoring .settings-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 100%;
  margin: 0 auto;
}

.settings-section.ai-scoring .settings-form .form-section-card {
  max-width: 100%;
  padding: 20px 50px;
}

.settings-section.ai-scoring .section-header {
  text-align: center;
  margin-bottom: 28px;
}

.settings-section.ai-scoring .settings-actions {
  max-width: 100%;
  margin: 24px auto 0;
}

/* 设置操作按钮 */
.settings-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding: 20px 0 0 0;
  margin-top: 24px;
  border-top: 1px solid #e8e8e8;
  transition: all var(--transition-normal);
}

.reset-btn,
.save-btn {
  padding: 10px 18px;
  font-size: 14px;
  font-weight: 600;
  border-radius: var(--radius-md);
  transition: all var(--transition-normal);
  box-shadow: var(--shadow-sm);
  min-width: 120px;
  text-align: center;
}

.reset-btn {
  background-color: #f5f5f5;
  color: #333;
  border: 1px solid #d9d9d9;
}

.reset-btn:hover {
  background-color: #e8e8e8;
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.save-btn {
  background: var(--color-primary);
  color: white;
  border: none;
}

.save-btn:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

/* AI分析设置样式 */
/* 优化的启用开关区域 */
.ai-enable-section.optimized {
  margin-bottom: 24px;
}

.enable-cards-container {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}

@media (min-width: 768px) {
  .enable-cards-container {
    grid-template-columns: repeat(2, 1fr);
  }
}

.enable-card.enhanced {
  background-color: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  transition: all var(--transition-normal);
  overflow: hidden;
}

.enable-card.enhanced:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
}

.enable-header {
  display: flex;
  align-items: center;
  padding: 24px;
  gap: 20px;
  flex-wrap: wrap;
}

.enable-icon-section {
  flex-shrink: 0;
}

.enable-icon {
  width: 60px;
  height: 60px;
  border-radius: var(--radius-full);
  background: #1890ff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: white;
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-normal);
}

.enable-card:nth-child(2) .enable-icon {
  background: #52c41a;
}

.enable-card.enhanced:hover .enable-icon {
  transform: scale(1.05);
}

.enable-info-section {
  flex: 1;
  min-width: 0;
}

.enable-info-section h4 {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0 0 8px 0;
}

.enable-description {
  font-size: 14px;
  color: #666;
  margin: 0;
  line-height: 1.5;
}

.enable-toggle-section {
  flex-shrink: 0;
}

/* 明显的方形复选框样式 */
.toggle-switch.improved {
  display: inline-block;
  position: relative;
  width: 24px;
  height: 24px;
  cursor: pointer;
  user-select: none;
}

.toggle-switch.improved input[type="checkbox"] {
  display: none;
}

.toggle-switch.improved .toggle-label {
  position: absolute;
  top: 0;
  left: 0;
  width: 24px;
  height: 24px;
  background-color: white;
  border: 2px solid #1890ff;
  border-radius: 4px;
  transition: all 0.2s ease;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.toggle-switch.improved .toggle-label:hover {
  border-color: #40a9ff;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.2);
}

.toggle-switch.improved .toggle-label::after {
  content: '✓';
  font-size: 16px;
  font-weight: bold;
  color: white;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.toggle-switch.improved input[type="checkbox"]:checked + .toggle-label {
  background-color: #1890ff;
  border-color: #1890ff;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.3);
}

.toggle-switch.improved input[type="checkbox"]:checked + .toggle-label::after {
  opacity: 1;
}

/* 隐藏状态文字 */
.toggle-switch.improved .toggle-status {
  display: none;
}



/* 部分标题和描述 */
.section-header {
  margin-bottom: 24px;
}

.section-header h3 {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-primary);
  margin: 0 0 8px 0;
}

.section-description {
  font-size: 14px;
  color: #666;
  margin: 0;
  line-height: 1.5;
}

/* AI启用部分 */
.ai-enable-section {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
  margin-bottom: 32px;
}

.enable-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px;
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  border: 1px solid #e8e8e8;
  transition: all var(--transition-normal);
  min-height: 120px;
  height: 120px;
  overflow: hidden;
}

.enable-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: #1890ff;
}

.enable-icon {
  font-size: 36px;
  width: 60px;
  height: 60px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f7ff;
  flex-shrink: 0;
  transition: all var(--transition-normal);
}

.enable-card:hover .enable-icon {
  transform: scale(1.1);
}

.enable-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.enable-content h4 {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.enable-description {
  font-size: 13px;
  color: #666;
  margin: 0;
  line-height: 1.4;
}

/* 开关按钮样式已移至.improved类中 */

/* 表单部分 */
.form-section {
  margin-bottom: 32px;
}

.form-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.form-section-header h4 {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 新的按钮样式 */
.add-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border: none;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-normal);
  box-shadow: var(--shadow-sm);
}

.add-btn:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

.primary-btn {
  background: #1890ff;
  color: white;
}

.danger-btn {
  background: #ff4d4f;
  color: white;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border: none;
  border-radius: var(--radius-md);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-normal);
  box-shadow: var(--shadow-sm);
}

.action-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

.action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.btn-icon {
  font-size: 14px;
  line-height: 1;
  color: white;
}




/* 设置操作卡片样式 */
.settings-actions-card {
  background: transparent;
  padding: 12px;
  border-radius: var(--radius-md);
  border: none; /* 移除边框 */
  transition: all var(--transition-normal);
  max-width: 400px;
  margin: 20px auto;
  box-shadow: none; /* 移除阴影 */
}

.settings-actions-card:hover {
  background: transparent;
  box-shadow: none; /* 移除阴影 */
}

.settings-actions-content {
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
}



/* 按钮样式 */
.add-category-btn,
.add-rule-btn {
  margin-top: 12px;
  width: 100%;
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 500;
  transition: all var(--transition-normal);
  box-shadow: var(--shadow-sm);
}

.add-category-btn:hover,
.add-rule-btn:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

.add-keyword-btn,
.remove-category-btn,
.remove-keyword-btn,
.remove-rule-btn {
  transition: all var(--transition-normal);
  box-shadow: var(--shadow-sm);
}

.add-keyword-btn:hover,
.remove-category-btn:hover,
.remove-keyword-btn:hover,
.remove-rule-btn:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

/* 响应式调整 */
@media (max-width: 768px) {
  .settings-tabs {
    flex-direction: column;
    gap: 8px;
  }

  .form-control input[type="number"],
  .form-control input[type="date"],
  .form-control select {
    max-width: none;
  }

  .settings-actions {
    flex-direction: column;
    gap: 12px;
  }

  .settings-actions button {
    width: 100%;
  }

  .category-header,
  .rule-header {
    flex-direction: column;
    align-items: stretch;
  }

  .category-name,
  .factor-name {
    min-width: unset;
  }

  .category-actions {
    justify-content: stretch;
  }

  .add-keyword-btn,
  .remove-category-btn {
    flex: 1;
  }
}

/* 表单控件样式补充 */
.checkbox-control {
  display: flex;
  align-items: center;
  gap: 12px;
}

.checkbox-control input[type="checkbox"] {
  width: 20px;
  height: 20px;
  cursor: pointer;
}

/* 动画 */
/* 操作反馈Toast样式 */
.feedback-toast {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  padding: 16px 24px;
  border-radius: 8px;
  color: white;
  font-size: 14px;
  font-weight: 500;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.2);
  z-index: 1000;
  animation: fadeIn 0.3s ease-out;
  min-width: 200px;
  text-align: center;
  background-color: var(--color-primary);
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.feedback-toast.success {
  background-color: #52c41a;
}

.feedback-toast.error {
  background-color: #ff4d4f;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translate(-50%, -50%) scale(0.8);
  }
  to {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1);
  }
}

/* AI评分配置样式 */
.scoring-rules-config {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 16px;
}

.scoring-rule-item {
  background: #fafafa;
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #e8e8e8;
  transition: all 0.3s;
}

.scoring-rule-item:hover {
  border-color: #1890ff;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.1);
}

.rule-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  align-items: flex-end;
}

.rule-row:last-child {
  margin-bottom: 0;
}

.rule-field {
  flex: 1;
  min-width: 0;
}

.rule-field.full-width {
  flex: 1;
}

.rule-field label {
  display: block;
  font-size: 12px;
  color: #666;
  margin-bottom: 4px;
  font-weight: 500;
}

.rule-field.weight-field {
  max-width: 100px;
  flex-shrink: 0;
}

.input-field {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 14px;
  transition: all 0.3s;
  box-sizing: border-box;
}

.input-field:focus {
  border-color: #1890ff;
  outline: none;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.1);
}

.input-field.small {
  width: 60px;
  text-align: center;
}

.textarea-field {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 14px;
  min-height: 60px;
  resize: vertical;
  transition: all 0.3s;
  box-sizing: border-box;
}

.textarea-field:focus {
  border-color: #1890ff;
  outline: none;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.1);
}

.score-range {
  display: flex;
  align-items: center;
  gap: 8px;
}

.score-range span {
  color: #999;
  font-weight: bold;
}

.action-btn {
  padding: 8px 16px;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
  border: none;
  white-space: nowrap;
}

.action-btn.small {
  padding: 6px 12px;
  font-size: 12px;
}

.primary-btn {
  background: #1890ff;
  color: white;
}

.primary-btn:hover {
  background: #40a9ff;
}

.danger-btn {
  background: #ff4d4f;
  color: white;
}

.danger-btn:hover {
  background: #ff7875;
}

.danger-btn:disabled {
  background: #d9d9d9;
  cursor: not-allowed;
}

.weight-summary {
  padding: 12px 16px;
  border-radius: 8px;
  background: #fff7e6;
  border: 1px solid #ffd591;
  margin-top: 8px;
}

.weight-summary.valid {
  background: #f6ffed;
  border-color: #b7eb8f;
}

.weight-summary.invalid {
  background: #fff7e6;
  border-color: #ffd591;
}

.weight-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.weight-label {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.weight-value {
  font-size: 18px;
  font-weight: 600;
  color: #1890ff;
}

.weight-status {
  font-size: 14px;
  color: #fa8c16;
  display: flex;
  align-items: center;
  gap: 4px;
}

.weight-status.valid {
  color: #52c41a;
}

.status-icon {
  font-size: 14px;
}

.btn-sm {
  padding: 8px 16px;
  font-size: 13px;
}

/* 管理链接样式 */
.management-links {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 16px;
}

.link-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #fafafa;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.link-card:hover {
  background: #f0f7ff;
  border-color: #1890ff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.15);
}

.link-icon {
  font-size: 32px;
  flex-shrink: 0;
}

.link-content {
  flex: 1;
  min-width: 0;
}

.link-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}

.link-desc {
  font-size: 13px;
  color: #666;
}

.link-arrow {
  font-size: 20px;
  color: #1890ff;
  font-weight: bold;
  transition: transform 0.3s;
}

.link-card:hover .link-arrow {
  transform: translateX(4px);
}
</style>
