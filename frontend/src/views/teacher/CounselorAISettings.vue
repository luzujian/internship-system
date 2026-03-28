<template>
  <div class="settings-container">
    <!-- 设置页面头部 -->
    <div class="page-header fade-in" style="animation-delay: 0.1s">
      <h2>AI分析配置</h2>
      <p class="page-subtitle">配置评分规则和关键词</p>
    </div>

    <!-- AI评分配置 -->
    <div class="settings-section ai-scoring card fade-in" style="animation-delay: 0.3s">
      <div class="section-header">
        <h3>AI分析配置</h3>
        <p class="section-description">配置AI自动评分功能，学生提交实习心得后将自动进行AI分析评分</p>
      </div>

      <div class="settings-form">
        <!-- AI评分开关 -->
        <div class="form-section-card card fade-in" style="animation-delay: 0.3s">
          <div class="form-section-header with-toggle">
            <div class="section-info">
              <h4>AI分析开关</h4>
              <p class="section-description">启用后，学生提交实习心得时将自动进行AI分析评分</p>
            </div>
            <div class="section-toggle">
              <div class="toggle-switch improved">
                <input type="checkbox" id="enableAiScoring" v-model="aiScoringSettings.enableAiScoring" />
                <label for="enableAiScoring" class="toggle-label"></label>
                <span class="toggle-status">{{ aiScoringSettings.enableAiScoring ? '已开启' : '已关闭' }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 快捷管理入口 -->
        <div class="form-section-card card fade-in" style="animation-delay: 0.4s">
          <div class="form-section-header">
            <div class="section-info">
              <h4>评分规则与关键词管理</h4>
              <p class="section-description">配置评分维度、权重和关键词库，AI将根据这些规则进行评分</p>
            </div>
          </div>

          <div class="management-links">
            <div class="link-card" @click="goToScoringRules">
              <div class="link-icon">📊</div>
              <div class="link-content">
                <div class="link-title">评分规则管理</div>
                <div class="link-desc">配置评分维度和权重，当前已配置 {{ aiScoringSettings.scoringRules.length }} 条规则</div>
              </div>
              <div class="link-arrow">→</div>
            </div>

            <div class="link-card" @click="goToKeywordLibrary">
              <div class="link-icon">🔑</div>
              <div class="link-content">
                <div class="link-title">关键词库管理</div>
                <div class="link-desc">管理AI分析使用的关键词库</div>
              </div>
              <div class="link-arrow">→</div>
            </div>
          </div>
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
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

// AI评分配置
const aiScoringSettings = ref({
  enableAiScoring: false,
  aiModelCode: 'deepseek-chat',
  scoringRules: [
    {
      ruleName: '实习态度',
      ruleCode: 'attitude',
      weight: 30,
      minScore: 0,
      maxScore: 100,
      description: '实习态度是否端正，是否积极主动'
    },
    {
      ruleName: '专业能力',
      ruleCode: 'professional',
      weight: 40,
      minScore: 0,
      maxScore: 100,
      description: '专业知识和技能的应用能力'
    },
    {
      ruleName: '心得质量',
      ruleCode: 'report',
      weight: 30,
      minScore: 0,
      maxScore: 100,
      description: '实习心得的内容质量和反思深度'
    }
  ]
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

const loadAiScoringSettings = async () => {
  try {
    const currentRole = localStorage.getItem('current_role') || 'ROLE_TEACHER'
    const rolePrefix = currentRole === 'ROLE_TEACHER' ? 'teacher_' : ''
    const userId = localStorage.getItem(rolePrefix + 'userId_' + currentRole) || localStorage.getItem('teacherId')
    const { getCounselorAISettings } = await import('../../api/counselorAISettings')
    const response = await getCounselorAISettings(parseInt(userId || '0'))
    if (response && response.data) {
      const data = response.data
      if (data.settings) {
        aiScoringSettings.value.enableAiScoring = data.settings.enableAiScoring === 1
        aiScoringSettings.value.aiModelCode = data.settings.aiModelCode || 'deepseek-chat'
      }
      if (data.scoringRules && data.scoringRules.length > 0) {
        aiScoringSettings.value.scoringRules = data.scoringRules.map((rule: any) => ({
          ruleName: rule.ruleName,
          ruleCode: rule.ruleCode,
          weight: rule.weight,
          minScore: rule.minScore,
          maxScore: rule.maxScore,
          description: rule.description
        }))
      }
    }
  } catch (error: any) {
    console.error('加载AI评分配置失败:', error)
  }
}

const goToScoringRules = () => {
  router.push('/teacher/scoring-rules')
}

const goToKeywordLibrary = () => {
  router.push('/teacher/keyword-library')
}

// 保存AI分析配置（自动保存）
const saveAiScoringSettings = async () => {
  try {
    const currentRole = localStorage.getItem('current_role') || 'ROLE_TEACHER'
    const rolePrefix = currentRole === 'ROLE_TEACHER' ? 'teacher_' : ''
    const userId = localStorage.getItem(rolePrefix + 'userId_' + currentRole) || localStorage.getItem('teacherId')
    const data = {
      counselorId: parseInt(userId || '0'),
      enableAiScoring: aiScoringSettings.value.enableAiScoring ? 1 : 0,
      aiModelCode: aiScoringSettings.value.aiModelCode
    }

    const { saveCounselorAISettings } = await import('../../api/counselorAISettings')
    await saveCounselorAISettings(data)
  } catch (error: any) {
    console.error('保存AI分析配置失败:', error)
    showOperationFeedback('保存失败', 'error')
  }
}

// 监听AI开关变化，自动保存
watch(() => aiScoringSettings.value.enableAiScoring, (newValue, oldValue) => {
  if (oldValue !== undefined && oldValue !== newValue) {
    saveAiScoringSettings()
  }
})

// 页面加载时加载设置
onMounted(() => {
  loadAiScoringSettings()
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
  display: flex;
  flex-direction: column;
  gap: 16px;
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

/* 开关按钮样式 */
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

/* 表单部分 */
.form-section {
  margin-bottom: 32px;
}

.form-section-card {
  background: white;
  border-radius: var(--radius-lg);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.form-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.form-section-header.with-toggle {
  display: flex;
  align-items: center;
  gap: 16px;
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

.section-toggle {
  flex-shrink: 0;
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
</style>
