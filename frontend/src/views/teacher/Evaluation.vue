<template>
  <div class="evaluation-container">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">智慧评分</h1>
        <p class="page-description">查看学生实习心得，AI分析结果，并完成成绩评定</p>
      </div>
      <!-- 右上角统计卡片 -->
      <div class="overview-cards top-right">
        <div class="stat-card fade-in" style="animation-delay: 0.1s">
          <div class="stat-icon primary">👥</div>
          <div class="stat-content">
            <div class="stat-label">学生总数</div>
            <div class="stat-value">{{ statistics.totalStudents }}</div>
          </div>
        </div>
        <div class="stat-card fade-in" style="animation-delay: 0.2s">
          <div class="stat-icon success">✓</div>
          <div class="stat-content">
            <div class="stat-label">已评分</div>
            <div class="stat-value">{{ statistics.evaluatedCount }}</div>
          </div>
        </div>
        <div class="stat-card fade-in" style="animation-delay: 0.3s">
          <div class="stat-icon warning">⏳</div>
          <div class="stat-content">
            <div class="stat-label">未评分</div>
            <div class="stat-value">{{ statistics.unevaluatedCount }}</div>
          </div>
        </div>
        <div class="stat-card fade-in" style="animation-delay: 0.4s">
          <div class="stat-icon info">📝</div>
          <div class="stat-content">
            <div class="stat-label">已提交心得</div>
            <div class="stat-value">{{ statistics.hasReportCount }}</div>
          </div>
        </div>
      </div>
    </div>

    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="filters" class="search-form" @keyup.enter="applyFilters">
        <div class="search-row">
          <el-form-item label="实习周期">
            <el-select v-model="filters.period" placeholder="请选择实习周期" clearable style="width: 220px;" @change="applyFilters">
              <el-option
                v-for="period in availablePeriods"
                :key="period.period"
                :label="`${period.periodName} (${period.startDate} ~ ${period.endDate})`"
                :value="period.period.toString()">
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="院系">
            <el-select v-model="filters.department" placeholder="请选择院系" clearable style="width: 140px;">
              <el-option label="全部院系" value=""></el-option>
              <el-option 
                v-for="dept in departments" 
                :key="dept.id" 
                :label="dept.name" 
                :value="dept.name">
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="专业">
            <el-select v-model="filters.majorName" placeholder="请选择专业" clearable style="width: 140px;">
              <el-option label="全部专业" value=""></el-option>
              <el-option 
                v-for="major in majors" 
                :key="major.id" 
                :label="major.name" 
                :value="major.name">
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="班级">
            <el-select v-model="filters.class" placeholder="请选择班级" clearable style="width: 180px;">
              <el-option label="全部班级" value=""></el-option>
              <el-option 
                v-for="cls in classes" 
                :key="cls.id" 
                :label="cls.name" 
                :value="cls.name">
              </el-option>
            </el-select>
          </el-form-item>
        </div>
        <div class="search-row">
          <el-form-item label="搜索">
            <el-input
              v-model="filters.search"
              placeholder="搜索学生姓名或学号"
              clearable
              style="width: 180px;"
              @keyup.enter="applyFilters"
            ></el-input>
          </el-form-item>
          <el-form-item label="实习心得">
            <el-select v-model="filters.hasReport" placeholder="请选择" clearable style="width: 110px;">
              <el-option label="全部" value=""></el-option>
              <el-option label="已提交" value="true"></el-option>
              <el-option label="未提交" value="false"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="评分状态">
            <el-select v-model="filters.isEvaluated" placeholder="请选择" clearable style="width: 110px;">
              <el-option label="全部" value=""></el-option>
              <el-option label="已评分" value="true"></el-option>
              <el-option label="未评分" value="false"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item class="search-actions">
            <el-button type="primary" @click="applyFilters" class="action-btn primary">
              <el-icon><Search /></el-icon>&nbsp;查询
            </el-button>
            <el-button type="warning" @click="resetFilters" class="action-btn warning">
              <el-icon><Refresh /></el-icon>&nbsp;重置
            </el-button>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <el-card class="actions-card" shadow="never">
      <div class="actions-container">
        <!-- 未评分标签页操作 -->
        <template v-if="activeTab === 'unevaluated'">
          <div class="primary-actions">
            <el-button type="primary" @click="openBatchEvaluationModal" :disabled="selectedStudents.length === 0" class="action-btn primary">
              <el-icon><Edit /></el-icon>&nbsp;批量打分 ({{ selectedStudents.length }})
            </el-button>
            <el-button type="default" @click="selectAllStudents" v-if="!allSelected" class="action-btn">
              <el-icon><Select /></el-icon>&nbsp;全选
            </el-button>
            <el-button type="default" @click="clearSelection" v-else class="action-btn">
              <el-icon><Close /></el-icon>&nbsp;取消全选
            </el-button>
          </div>
          <div class="secondary-actions">
            <el-button type="primary" @click="goToAiScoringConfig" class="action-btn">
              <el-icon><Setting /></el-icon>&nbsp;AI评分配置
            </el-button>
            <el-button type="success" @click="startEvaluation" :disabled="students.length === 0" class="action-btn success">
              <el-icon><EditPen /></el-icon>&nbsp;开始评分
            </el-button>
            <el-button type="default" @click="refreshData" class="action-btn">
              <el-icon><Refresh /></el-icon>&nbsp;刷新列表
            </el-button>
          </div>
        </template>
        <!-- 已评分标签页操作 -->
        <template v-else>
          <div class="primary-actions">
            <el-button type="warning" @click="uploadGrades" :disabled="statistics.unpublishedCount === 0" class="action-btn warning">
              <el-icon><Upload /></el-icon>&nbsp;一键上传学生成绩 ({{ statistics.unpublishedCount }})
            </el-button>
          </div>
          <div class="secondary-actions">
            <el-button type="default" @click="refreshData" class="action-btn">
              <el-icon><Refresh /></el-icon>&nbsp;刷新列表
            </el-button>
          </div>
        </template>
      </div>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="table-header">
        <div class="period-badge" v-if="currentPeriodName">
          <el-tag type="info" effect="plain">{{ currentPeriodName }}</el-tag>
        </div>
        <div class="table-actions">
          <span class="total-count">共 {{ tabTotal }} 条记录</span>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="evaluation-tabs" @tab-change="handleTabChange">
        <el-tab-pane :label="`未评分 (${statistics.unevaluatedCount})`" name="unevaluated"></el-tab-pane>
        <el-tab-pane :label="`已评分 (${statistics.evaluatedCount})`" name="evaluated"></el-tab-pane>
      </el-tabs>

      <el-table
        v-loading="loading"
        :data="displayedStudents"
        style="width: 100%"
        @selection-change="handleSelectionChange"
        border
        fit
        class="data-table"
      >
        <el-table-column type="selection" width="55" align="center"></el-table-column>
        <el-table-column prop="name" label="姓名" width="120" align="center"></el-table-column>
        <el-table-column prop="studentId" label="学号" width="150" align="center"></el-table-column>
        <el-table-column prop="grade" label="年级" width="100" align="center"></el-table-column>
        <el-table-column prop="className" label="班级" width="220" align="center"></el-table-column>
        <el-table-column prop="department" label="院系" width="180" align="center">
          <template #default="scope">{{ scope.row.department || '-' }}</template>
        </el-table-column>
        <el-table-column prop="majorName" label="专业" width="180" align="center">
          <template #default="scope">{{ scope.row.majorName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="company" label="实习单位" width="200" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.company || '-' }}</template>
        </el-table-column>
        <el-table-column label="实习心得" width="120" align="center">
          <template #default="scope">
            <div v-if="scope.row.hasCurrentPeriodReport" class="report-submitted">
              <el-icon class="check-icon"><Check /></el-icon>
            </div>
            <div v-else class="report-not-submitted">
              <el-icon class="close-icon"><Close /></el-icon>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="评分状态" width="120" align="center">
          <template #default="scope">
            <div v-if="scope.row.isEvaluated" class="status-evaluated">
              <el-icon class="check-icon"><Check /></el-icon>
            </div>
            <div v-else class="status-not-evaluated">
              <el-icon class="close-icon"><Close /></el-icon>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="scope">
            <div class="action-buttons">
              <!-- 未评分标签页：显示评分按钮 -->
              <el-tooltip v-if="activeTab === 'unevaluated'" content="评分" placement="top">
                <el-button size="small" type="success" @click="openEvaluationModal(scope.row)" :disabled="!scope.row.hasCurrentPeriodReport" class="table-btn view">
                  <el-icon><EditPen /></el-icon>
                </el-button>
              </el-tooltip>
              <!-- 已评分标签页：显示查看和编辑按钮 -->
              <template v-else>
                <el-tooltip content="查看" placement="top">
                  <el-button size="small" type="success" @click="viewEvaluation(scope.row)" class="table-btn view">
                    <el-icon><View /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip :content="scope.row.gradePublished ? '成绩已发布，无法编辑' : '编辑'" placement="top">
                  <el-button size="small" type="primary" @click="openEvaluationModal(scope.row)" :disabled="scope.row.gradePublished" class="table-btn view">
                    <el-icon><EditPen /></el-icon>
                  </el-button>
                </el-tooltip>
              </template>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          :current-page="currentPage"
          :page-sizes="[10, 20, 50]"
          :page-size="pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :total="tabTotal"
          class="custom-pagination"
        />
      </div>
    </el-card>

    <!-- 综合评价弹窗 -->
    <div v-if="showEvaluationModal" class="modal-overlay" @click.self="showEvaluationModal = false">
      <div class="combined-modal">
        <!-- 左侧：Word文档样式的心得内容卡片 -->
        <div class="word-document-card" v-if="currentStudent">
          <div class="word-document-header">
            <div class="word-icon">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
                <path d="M14,2H6A2,2 0 0,0 4,4V20A2,2 0 0,0 6,22H18A2,2 0 0,0 20,20V8L14,2M18,20H6V4H13V9H18V20M9.5,11.5L11,17H12L13.5,13.5L15,17H16L17.5,11.5H16L15.25,14.5L13.5,10.5H12.5L11,14.5L10,11.5H9.5Z"/>
              </svg>
            </div>
            <span class="word-title">实习心得</span>
            <span class="current-period-badge" v-if="currentPeriodName">{{ currentPeriodName }}</span>
          </div>
          <div class="word-document-body">
            <div v-if="currentPeriodReport" class="word-content-wrapper">
              <div class="word-page">
                <div class="word-page-content">
                  <div class="report-meta">
                    <span class="report-period">第{{ currentPeriodReport.periodNumber }}阶段心得</span>
                    <span class="report-date">{{ currentPeriodReport.date }}</span>
                  </div>
                  <div class="report-full-content">
                    {{ currentPeriodReport.content || '暂无内容' }}
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="word-empty">
              <div class="empty-icon">📄</div>
              <p>暂无{{ currentPeriodName || '当前' }}阶段实习心得</p>
            </div>
          </div>
        </div>

        <!-- 右侧：综合评价卡片（包含header、评价面板、footer） -->
        <div class="evaluation-card">
          <div class="modal-header">
            <h3>{{ currentStudent?.name }} - 综合评价</h3>
            <button class="close-btn" @click="showEvaluationModal = false">×</button>
          </div>
          
          <div class="modal-body combined-body">
            <div v-if="currentStudent" class="evaluation-panel">
              <!-- 学生基本信息（紧凑） -->
              <div class="student-basic-info">
                <div class="info-item"><span class="label">学号：</span><span>{{ currentStudent.studentId }}</span></div>
                <div class="info-item"><span class="label">年级：</span><span>{{ currentStudent.grade || '-' }}</span></div>
                <div class="info-item"><span class="label">班级：</span><span>{{ currentStudent.className }}</span></div>
                <div class="info-item"><span class="label">专业：</span><span>{{ currentStudent.majorName || '-' }}</span></div>
                <div class="info-item"><span class="label">实习单位：</span><span>{{ currentStudent.company || '-' }}</span></div>
              </div>

              <!-- AI心得总结区域 - 直接显示AI生成的综合评价 -->
              <div class="section-card" v-if="currentPeriodReport?.aiComment || (currentStudent.reports && currentStudent.reports.length > 0)">
                <div class="section-header">
                  <h4>AI心得总结</h4>
                </div>
                <div class="ai-summary-container">
                  <div v-if="currentPeriodReport?.aiComment" class="ai-summary-content">
                    <div class="ai-summary-icon">🤖</div>
                    <div class="ai-summary-text">{{ currentPeriodReport.aiComment }}</div>
                  </div>
                  <div v-else class="ai-summary-placeholder">
                    <div class="placeholder-icon">📝</div>
                    <div class="placeholder-text">
                      <p>暂无AI分析总结</p>
                      <p class="hint">请先提交实习心得</p>
                    </div>
                  </div>
                </div>
              </div>
              
              <!-- AI总分析（紧凑） -->
              <div class="section-card" v-if="currentStudent?.aiAnalysisData">
                <div class="section-header">
                  <h4>AI分析</h4>
                </div>
                <div class="ai-analysis-compact">
                  <div class="ai-row">
                    <div class="ai-col keywords-col">
                      <div class="ai-label">关键词</div>
                      <div class="keywords-compact">
                        <span v-for="(keyword, idx) in currentStudent.aiAnalysisData.keywords" :key="idx" class="keyword-tag-compact">
                          {{ keyword }}
                        </span>
                      </div>
                    </div>
                    <div class="ai-col sentiment-col">
                      <div class="ai-label">情感分析</div>
                      <div class="sentiment-compact">
                        <span class="sentiment-item-compact positive">积极 {{ currentStudent.aiAnalysisData.sentiment.positive }}%</span>
                        <span class="sentiment-item-compact neutral">中性 {{ currentStudent.aiAnalysisData.sentiment.neutral }}%</span>
                        <span class="sentiment-item-compact negative">消极 {{ currentStudent.aiAnalysisData.sentiment.negative }}%</span>
                      </div>
                    </div>
                    <div class="ai-col score-col">
                      <div class="ai-label">建议评分</div>
                      <div class="suggested-scores-compact">
                        <span v-if="currentStudent.aiAnalysisData.suggestedScores?.scores">
                          <span v-for="(score, key) in currentStudent.aiAnalysisData.suggestedScores.scores" :key="key" class="score-tag">
                            {{ getScoreLabel(key) }}: {{ score }}分
                          </span>
                        </span>
                        <span v-else>
                          态度: {{ currentStudent.aiAnalysisData.suggestedScores?.attitude || 0 }}分 | 
                          表现: {{ currentStudent.aiAnalysisData.suggestedScores?.performance || 0 }}分 | 
                          心得: {{ currentStudent.aiAnalysisData.suggestedScores?.report || 0 }}分
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 成绩评定区域（固定在底部） -->
          <div class="evaluation-section-fixed" v-if="currentStudent">
            <div class="section-header">
              <h4>成绩评定</h4>
              <div class="total-display">
                <span class="total-label">总分：</span>
                <span class="total-value" :class="{ 'score-exceeded': totalScore > 100 }">{{ totalScore }}</span>
                <span class="grade-badge" :class="'grade-' + grade.toLowerCase()">{{ grade }}</span>
                <el-button
                  v-if="currentStudent.aiAnalysisData"
                  size="small"
                  type="primary"
                  plain
                  @click="openAIAnalysisDialog(currentStudent)"
                  class="view-ai-report-btn"
                >
                  查看AI分析报告
                </el-button>
              </div>
            </div>
            
            <div v-if="scoreItems.length === 0" class="no-rules-warning-compact">
              <span class="warning-icon-sm">⚠️</span>
              <span>未配置评分规则，请先</span>
              <button class="btn-link" @click="goToAiScoringConfig">前往配置</button>
            </div>
            
            <div v-else class="evaluation-form-compact">
              <div class="score-items-compact">
                <div class="score-item-compact" v-for="item in scoreItems" :key="item.key">
                  <div class="score-item-header">
                    <label>{{ item.label }} <span class="max-score">({{ item.max }}分)</span></label>
                    <span v-if="item.weight" class="weight-badge">{{ item.weight }}%</span>
                  </div>
                  <div v-if="item.description" class="score-item-description">{{ item.description }}</div>
                  <div v-if="item.evaluationCriteria" class="score-item-criteria">
                    <span class="criteria-label">评分标准:</span>
                    <span class="criteria-text">{{ item.evaluationCriteria }}</span>
                  </div>
                  <input 
                    type="number" 
                    v-model.number="evaluationForm[item.key]" 
                    :min="0" 
                    :max="item.max"
                    class="score-input"
                  >
                </div>
              </div>
              <div class="comment-section">
                <label>评语</label>
                <textarea 
                  v-model="evaluationForm.comment" 
                  placeholder="请输入评语" 
                  rows="2"
                  class="comment-textarea-compact"
                ></textarea>
              </div>
            </div>
          </div>
          
          <div class="modal-footer">
            <div class="footer-left">
              <button
                class="btn btn-nav"
                @click="goToPrevStudent"
                :disabled="!canGoToPrevStudent"
              >
                ← 上一篇
              </button>
              <button
                class="btn btn-nav"
                @click="goToNextStudent"
                :disabled="!canGoToNextStudent"
              >
                下一篇 →
              </button>
            </div>
            <div class="footer-right">
              <button class="btn btn-default" @click="showEvaluationModal = false">关闭</button>
              <button
                v-if="!isViewOnly"
                class="btn btn-primary"
                @click="submitEvaluation"
                :disabled="!isFormValid"
              >
                {{ currentStudent?.isEvaluated ? '更新评价' : '提交评价' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 批量打分弹窗 -->
    <div v-if="showBatchEvaluationModal" class="modal-overlay" @click.self="showBatchEvaluationModal = false">
      <div class="modal-content large card">
        <div class="modal-header">
          <h3>批量打分 (已选择 {{ selectedStudents.length }} 名学生)</h3>
          <button class="close-btn" @click="showBatchEvaluationModal = false">×</button>
        </div>
        
        <div class="modal-body">
          <div class="batch-evaluation-form">
            <div class="form-section-row">
              <div class="form-section half">
                <h4>评分项</h4>
                <div v-if="scoreItems.length === 0" class="no-rules-warning">
                  <div class="warning-content">
                    <div class="warning-icon">⚠️</div>
                    <div class="warning-text">
                      <h5>未配置评分规则</h5>
                      <p>请先在设置页面配置评分规则，然后再进行评分</p>
                      <button class="btn btn-primary btn-sm" @click="goToAiScoringConfig">
                        前往配置评分规则
                      </button>
                    </div>
                  </div>
                </div>
                <div v-else class="score-items">
                  <div class="score-item" v-for="item in scoreItems" :key="item.key">
                    <div class="score-item-header">
                      <label>{{ item.label }} ({{ item.max }}分)</label>
                      <span v-if="item.weight" class="weight-badge">{{ item.weight }}%</span>
                    </div>
                    <div v-if="item.description" class="score-item-description">{{ item.description }}</div>
                    <div v-if="item.evaluationCriteria" class="score-item-criteria">
                      <span class="criteria-label">评分标准:</span>
                      <span class="criteria-text">{{ item.evaluationCriteria }}</span>
                    </div>
                    <input 
                      type="number" 
                      v-model.number="batchEvaluationForm[item.key]" 
                      :min="0" 
                      :max="item.max"
                    >
                  </div>
                </div>
              </div>
              <div class="form-section half">
                <h4>总分与等级</h4>
                <div class="total-score">
                  <div class="score-item">
                    <label>总分: </label>
                    <span class="total-score-value" :class="{ 'score-exceeded': batchTotalScore > 100 }">{{ batchTotalScore }}</span>
                    <span v-if="batchTotalScore > 100" class="score-warning">总分不能超过100分</span>
                  </div>
                  <div class="score-item">
                    <label>等级: </label>
                    <span class="grade-value">{{ batchGrade }}</span>
                  </div>
                </div>
              </div>
            </div>
            
            <div class="form-section">
              <h4>统一评语</h4>
              <textarea 
                v-model="batchEvaluationForm.comment" 
                placeholder="请输入统一评语（可选）" 
                rows="4"
                class="comment-textarea"
              ></textarea>
            </div>
            
              <div class="form-section">
              <h4>已选择学生列表</h4>
              <div class="selected-students-list">
                <div v-for="studentId in selectedStudents" :key="studentId" class="selected-student-item">
                  <div class="student-info">
                    <div class="info-row">
                      <span class="info-label">姓名：</span>
                      <span class="info-value">{{ getStudentById(studentId)?.name }}</span>
                    </div>
                    <div class="info-row">
                      <span class="info-label">学号：</span>
                      <span class="info-value">{{ getStudentById(studentId)?.studentId }}</span>
                    </div>
                    <div class="info-row">
                      <span class="info-label">年级：</span>
                      <span class="info-value">{{ getStudentById(studentId)?.grade }}</span>
                    </div>
                    <div class="info-row">
                      <span class="info-label">班级：</span>
                      <span class="info-value">{{ getStudentById(studentId)?.className }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <div class="modal-footer">
          <button class="btn btn-default" @click="showBatchEvaluationModal = false">取消</button>
          <button class="btn btn-primary" @click="submitBatchEvaluation" :disabled="!isBatchFormValid">
            提交批量打分
          </button>
        </div>
      </div>
    </div>

    <!-- AI 分析报告对话框 -->
    <el-dialog
      v-model="aiAnalysisDialogVisible"
      title="AI 分析报告"
      width="900px"
      class="ai-analysis-dialog"
      :close-on-click-modal="false"
      :z-index="999999"
      teleport="body"
    >
      <div class="reflection-result-section" v-if="currentAIAnalysis">
        <div class="result-container">
          <div class="result-header">
            <h3>分析结果</h3>
            <div class="header-actions">
              <el-button @click="downloadAIAnalysisReport" type="success" size="default" :disabled="!currentAIAnalysis || !currentAIAnalysis.reflectionId">
                <el-icon><Download /></el-icon>
                下载PDF
              </el-button>
            </div>
          </div>

          <div class="divider-line"></div>

          <div class="analysis-result">
            <div class="result-section">
              <h4>匹配的关键词</h4>
              <div class="keywords-container">
                <el-tag
                  v-for="(keyword, index) in currentAIAnalysis.keywords"
                  :key="index"
                  type="success"
                  size="small"
                  class="keyword-tag"
                >
                  {{ keyword }}
                </el-tag>
                <div v-if="!currentAIAnalysis.keywords || currentAIAnalysis.keywords.length === 0" class="no-keywords">
                  暂无匹配的关键词
                </div>
              </div>
            </div>

            <div class="result-section" v-if="currentAIAnalysis.sentimentAnalysis">
              <h4>情感分析</h4>
              <div class="sentiment-container">
                <div class="sentiment-item">
                  <div class="sentiment-label">
                    <span class="sentiment-icon positive">😊</span>
                    <span class="sentiment-name">积极</span>
                  </div>
                  <el-progress
                    :percentage="currentAIAnalysis.sentimentAnalysis.positive || 0"
                    :color="'#67c23a'"
                    :show-text="true"
                  />
                </div>
                <div class="sentiment-item">
                  <div class="sentiment-label">
                    <span class="sentiment-icon neutral">😐</span>
                    <span class="sentiment-name">中性</span>
                  </div>
                  <el-progress
                    :percentage="currentAIAnalysis.sentimentAnalysis.neutral || 0"
                    :color="'#909399'"
                    :show-text="true"
                  />
                </div>
                <div class="sentiment-item">
                  <div class="sentiment-label">
                    <span class="sentiment-icon negative">😔</span>
                    <span class="sentiment-name">消极</span>
                  </div>
                  <el-progress
                    :percentage="currentAIAnalysis.sentimentAnalysis.negative || 0"
                    :color="'#f56c6c'"
                    :show-text="true"
                  />
                </div>
              </div>
            </div>

            <div class="result-section" v-if="currentAIAnalysis.aspects && Object.keys(currentAIAnalysis.aspects).length > 0">
              <h4>能力维度评分</h4>
              <div class="aspects-container">
                <div v-for="(value, key) in currentAIAnalysis.aspects" :key="key" class="aspect-item">
                  <span class="aspect-label">{{ getAspectLabel(key) }}</span>
                  <el-progress :percentage="value" :color="getProgressColor(value)" />
                </div>
              </div>
            </div>

            <div class="result-section" v-if="currentAIAnalysis.summary">
              <h4>心得总结</h4>
              <p class="summary-text">{{ currentAIAnalysis.summary }}</p>
            </div>

            <div class="result-section" v-if="currentAIAnalysis.highlights && currentAIAnalysis.highlights.length > 0">
              <h4>亮点</h4>
              <ul class="highlight-list">
                <li v-for="(highlight, index) in currentAIAnalysis.highlights" :key="index">
                  {{ highlight }}
                </li>
              </ul>
            </div>

            <div class="result-section" v-if="currentAIAnalysis.improvements && currentAIAnalysis.improvements.length > 0">
              <h4>改进建议</h4>
              <ul class="improvement-list">
                <li v-for="(improvement, index) in currentAIAnalysis.improvements" :key="index">
                  {{ improvement }}
                </li>
              </ul>
            </div>

            <div class="result-section" v-if="currentAIAnalysis.comment">
              <h4>综合评语</h4>
              <p class="comment-text">{{ currentAIAnalysis.comment }}</p>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="aiAnalysisDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { evaluationApi, type EvaluationStudent, type EvaluationSubmitParams, type PeriodInfo } from '../../api/teacherEvaluation'
import { getScoringRules, getActiveCategoryWeights } from '../../api/counselorAISettings'
import { getAnalysisByStudentId } from '../../api/studentReflectionAIAnalysis'
import html2pdf from 'html2pdf.js/dist/html2pdf.bundle.min.js'
import { Search, Refresh, Edit, View, Select, Close, Check, Setting, DataAnalysis, Download, EditPen, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getTeacherClasses, getCounselorRelations } from '../../api/teacherClass'
import request from '../../utils/request'

const router = useRouter()

// 跳转到AI评分配置页面
const goToAiScoringConfig = () => {
  router.push({
    name: 'teacherAiScoringConfig'
  })
}

// 时间阶段数据
const periods = ref<PeriodInfo[]>([])

// 院系、专业、班级数据
const departments = ref<any[]>([])
const majors = ref<any[]>([])
const classes = ref<any[]>([])

// 学生数据
const students = ref<EvaluationStudent[]>([])

// 分页
const currentPage = ref(1)
const pageSize = ref(10)
const total = computed(() => students.value.length)
const jumpPage = ref('')

// 跳转到指定页码
const goToPage = () => {
  const page = parseInt(jumpPage.value)
  if (!isNaN(page) && page >= 1 && page <= totalPages.value) {
    currentPage.value = page
    jumpPage.value = ''
  }
}

// 分页后的学生列表
const paginatedStudents = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return students.value.slice(start, end)
})

// 总页数
const totalPages = computed(() => {
  return Math.ceil(students.value.length / pageSize.value)
})

// 当前选中的实习周期名称（优先显示筛选阶段，没有则显示当前实际阶段）
const currentPeriodName = computed(() => {
  if (filters.value.period) {
    const period = periods.value.find(p => p.period.toString() === filters.value.period)
    return period ? period.periodName : ''
  }
  // 没有选择阶段时，显示当前实际阶段
  if (actualCurrentPeriodNumber.value && periods.value.length > 0) {
    const currentPeriod = periods.value.find(p => p.period === actualCurrentPeriodNumber.value)
    return currentPeriod ? currentPeriod.periodName : ''
  }
  return ''
})

// 根据系统日期计算当前阶段编号
const actualCurrentPeriodNumber = computed(() => {
  if (!periods.value || periods.value.length === 0) {
    return null
  }
  // 使用本地日期字符串避免时区问题，确保日期比较在同一天内正确
  const now = new Date()
  const nowDateStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  for (const period of periods.value) {
    if (nowDateStr >= period.startDate && nowDateStr <= period.endDate) {
      return period.period
    }
  }
  // 如果当前日期不在任何阶段内，返回最后一个阶段
  return periods.value[periods.value.length - 1].period
})

// 下拉框可选的阶段列表（只显示当前及之前的阶段）
const availablePeriods = computed(() => {
  if (!periods.value || periods.value.length === 0) {
    return []
  }
  const currentPeriod = actualCurrentPeriodNumber.value
  if (!currentPeriod) {
    return periods.value
  }
  // 只显示当前阶段及之前的阶段
  return periods.value.filter(p => p.period <= currentPeriod)
})

// 检查是否可以切换到上一个有当前阶段心得的学生
const canGoToPrevStudent = computed(() => {
  if (currentStudentIndex.value <= 0) return false
  for (let i = currentStudentIndex.value - 1; i >= 0; i--) {
    const student = students.value[i]
    if (student.hasCurrentPeriodReport) {
      return true
    }
  }
  return false
})

// 检查是否可以切换到下一个有当前阶段心得的学生
const canGoToNextStudent = computed(() => {
  if (currentStudentIndex.value >= students.value.length - 1) return false
  for (let i = currentStudentIndex.value + 1; i < students.value.length; i++) {
    const student = students.value[i]
    if (student.hasCurrentPeriodReport) {
      return true
    }
  }
  return false
})

// 获取要显示的心得（优先显示当前阶段的心得，如果没有则显示最新的）
const currentPeriodReport = computed(() => {
  if (!currentStudent.value?.reports || currentStudent.value.reports.length === 0) {
    return null
  }
  const selectedPeriod = filters.value.period ? parseInt(filters.value.period) : null
  const targetPeriod = selectedPeriod || actualCurrentPeriodNumber.value

  if (targetPeriod) {
    // 优先查找目标阶段的心得
    const report = currentStudent.value.reports.find((r: any) => r.periodNumber === targetPeriod)
    // 如果找到的是草稿状态的心得，返回null显示空状态
    if (report && !report.isDraft) return report
    // 如果是草稿状态，返回null
    if (report && report.isDraft) return null
  }
  // 如果没找到目标阶段的心得，返回最新的非草稿心得
  const nonDraftReports = currentStudent.value.reports.filter((r: any) => !r.isDraft)
  return nonDraftReports.length > 0 ? nonDraftReports[0] : null
})

// 处理选择变化
const handleSelectionChange = (selection: EvaluationStudent[]) => {
  selectedStudents.value = selection.map(s => s.id)
  allSelected.value = selection.length === filteredStudentsByTab.value.length && filteredStudentsByTab.value.length > 0
}

// 处理每页数量变化
const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
}

// 处理页码变化
const handleCurrentChange = (page: number) => {
  currentPage.value = page
}

// 刷新数据
const refreshData = () => {
  loadStudentList()
}

// 评分项配置（从后端动态加载）
const scoreItems = ref<Array<{ key: string; label: string; max: number; weight: number; description?: string; evaluationCriteria?: string }>>([])
const loadingScoreRules = ref(false)

// 当前选中的学生
const currentStudent = ref<EvaluationStudent | null>(null)
// 当前学生在列表中的索引
const currentStudentIndex = ref(-1)

// 弹窗状态
const showEvaluationModal = ref(false)
const isViewOnly = ref(false) // 只读模式，用于已评分查看
const loading = ref(false)
const statsLoading = ref(false)

// 统计数据
const statistics = computed(() => {
  const total = students.value.length
  const evaluated = students.value.filter(s => s.isEvaluated).length
  const unevaluated = total - evaluated
  const hasReport = students.value.filter(s => s.hasReports).length
  // 已评分但未发布（上传）的数量
  const unpublished = students.value.filter(s => s.isEvaluated && !s.gradePublished).length
  return {
    totalStudents: total,
    evaluatedCount: evaluated,
    unevaluatedCount: unevaluated,
    hasReportCount: hasReport,
    unpublishedCount: unpublished
  }
})

// 标签页状态：unevaluated-未评分，evaluated-已评分
const activeTab = ref<'unevaluated' | 'evaluated'>('unevaluated')

// 根据标签页过滤学生列表
const filteredStudentsByTab = computed(() => {
  if (activeTab.value === 'evaluated') {
    return students.value.filter(s => s.isEvaluated)
  } else {
    return students.value.filter(s => !s.isEvaluated)
  }
})

// 标签页切换
const handleTabChange = (tab: string) => {
  activeTab.value = tab as 'unevaluated' | 'evaluated'
  currentPage.value = 1
}

// 一键上传学生成绩
const uploadGrades = async () => {
  const evaluatedStudents = students.value.filter(s => s.isEvaluated)
  if (evaluatedStudents.length === 0) {
    ElMessage.warning('没有已评分的学 生可以上传')
    return
  }

  try {
    // 调用后端API上传成绩（目前是标记为已提交）
    const response = await request.post('/evaluation/batch-upload', {
      studentIds: evaluatedStudents.map(s => s.id)
    })
    if (response.code === 200) {
      ElMessage.success('成绩上传成功')
      await loadStudentList()
    } else {
      ElMessage.error(response.message || '成绩上传失败')
    }
  } catch (error) {
    console.error('上传成绩失败:', error)
    ElMessage.error('成绩上传失败，请重试')
  }
}

// 当前标签页显示的学生列表
const displayedStudents = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredStudentsByTab.value.slice(start, end)
})

// 当前标签页的学生总数
const tabTotal = computed(() => filteredStudentsByTab.value.length)

// 展开的学生ID，用于操作面板
const expandedStudentId = ref<string | null>(null)

// 批量打分相关
const selectedStudents = ref<number[]>([])
const allSelected = ref(false)
const showBatchEvaluationModal = ref(false)
// 批量评价表单（动态，根据评分规则生成）
const batchEvaluationForm = ref<Record<string, any>>({})

// 评价表单（动态，根据评分规则生成）
const evaluationForm = ref<Record<string, any>>({})

// AI 分析对话框相关
const aiAnalysisDialogVisible = ref(false)
const currentAIAnalysis = ref<any>(null)
const aiAnalysisLoading = ref(false)

// 筛选条件
const filters = ref({
  period: '',
  department: '',
  majorName: '',
  class: '',
  search: '',
  hasReport: '',
  isEvaluated: ''
})

// 应用筛选
const applyFilters = () => {
  loadStudentList()
}

// 重置筛选
const resetFilters = () => {
  filters.value = {
    period: '',
    department: '',
    majorName: '',
    class: '',
    search: '',
    hasReport: '',
    isEvaluated: ''
  }
  loadStudentList()
}

// 加载评分规则
const loadScoreRules = async () => {
  try {
    loadingScoreRules.value = true
    const currentRole = localStorage.getItem('current_role') || 'ROLE_TEACHER'
    const rolePrefix = currentRole === 'ROLE_TEACHER' ? 'teacher_' : ''
    const userId = localStorage.getItem(rolePrefix + 'userId_' + currentRole) || localStorage.getItem('teacherId')
    if (!userId) {
      console.error('用户ID不存在')
      return
    }
    
    const response = await getActiveCategoryWeights(parseInt(userId))
    console.log('类别权重响应:', response)
    
    if (response && response.data && response.data.weights && Array.isArray(response.data.weights)) {
      const weights = response.data.weights
      
      if (weights.length === 0) {
        scoreItems.value = []
        evaluationForm.value = { comment: '' }
        batchEvaluationForm.value = { comment: '' }
        console.warn('未配置评分规则类别，请先在设置页面配置评分规则')
      } else {
        scoreItems.value = weights.map((weight: any) => ({
          key: weight.categoryCode,
          label: weight.categoryName,
          max: 100,
          weight: weight.weight,
          description: '',
          evaluationCriteria: ''
        }))
        
        const formKeys: any = {}
        weights.forEach((weight: any) => {
          formKeys[weight.categoryCode] = 0
        })
        formKeys.comment = ''
        
        evaluationForm.value = { ...formKeys }
        batchEvaluationForm.value = { ...formKeys }
      }
    } else {
      scoreItems.value = []
      evaluationForm.value = { comment: '' }
      batchEvaluationForm.value = { comment: '' }
    }
  } catch (error) {
    console.error('加载评分规则失败：', error)
    scoreItems.value = []
    evaluationForm.value = { comment: '' }
    batchEvaluationForm.value = { comment: '' }
  } finally {
    loadingScoreRules.value = false
  }
}

// 加载学生列表
const loadStudentList = async () => {
  try {
    loading.value = true
    
    let name = undefined
    let studentId = undefined
    
    if (filters.value.search) {
      const searchValue = filters.value.search.trim()
      if (/^\d+$/.test(searchValue)) {
        studentId = searchValue
      } else {
        name = searchValue
      }
    }
    
    const response = await evaluationApi.getStudentList({
      period: filters.value.period || (actualCurrentPeriodNumber.value?.toString()),
      department: filters.value.department || undefined,
      majorName: filters.value.majorName || undefined,
      className: filters.value.class || undefined,
      name: name,
      studentId: studentId,
      hasReport: filters.value.hasReport || undefined,
      isEvaluated: filters.value.isEvaluated || undefined
    })
    students.value = (response || []).sort((a, b) => {
      if (a.hasCurrentPeriodReport !== b.hasCurrentPeriodReport) {
        return a.hasCurrentPeriodReport ? -1 : 1
      }
      if (a.isEvaluated !== b.isEvaluated) {
        return a.isEvaluated ? 1 : -1
      }
      return 0
    })
  } catch (error) {
    ElMessage.error('加载学生列表失败：' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 加载时间阶段列表
const loadPeriods = async () => {
  try {
    const response = await evaluationApi.getPeriods()
    periods.value = response || []
  } catch (error) {
    console.error('加载时间阶段失败：', error)
  }
}

// 加载院系列表（只显示辅导员管理班级的院系）
const loadDepartments = async () => {
  try {
    const counselorId = getCurrentCounselorId()
    if (!counselorId) {
      return
    }
    
    const response = await getTeacherClasses(counselorId)
    const teacherClasses = response?.data || []
    
    const uniqueDepartments = new Map()
    teacherClasses.forEach((cls: any) => {
      if (cls.departmentId && cls.departmentName) {
        uniqueDepartments.set(cls.departmentId, { id: cls.departmentId, name: cls.departmentName })
      }
    })
    departments.value = Array.from(uniqueDepartments.values())
  } catch (error) {
    console.error('加载院系列表失败：', error)
  }
}

// 获取当前辅导员ID
const getCurrentCounselorId = (): number | null => {
  const currentRole = localStorage.getItem('current_role')
  const rolePrefix = currentRole === 'ROLE_TEACHER' ? 'teacher_' : ''
  const userId = localStorage.getItem(rolePrefix + 'userId_' + currentRole) || localStorage.getItem('teacherId')
  return userId ? parseInt(userId) : null
}

// 加载专业列表（只显示辅导员管理班级的专业）
const loadMajors = async () => {
  try {
    const counselorId = getCurrentCounselorId()
    if (!counselorId) {
      return
    }
    
    const response = await getTeacherClasses(counselorId)
    const teacherClasses = response?.data || []
    
    const uniqueMajors = new Map()
    teacherClasses.forEach((cls: any) => {
      if (cls.majorId && cls.majorName) {
        uniqueMajors.set(cls.majorId, { id: cls.majorId, name: cls.majorName })
      }
    })
    majors.value = Array.from(uniqueMajors.values())
  } catch (error) {
    console.error('加载专业列表失败：', error)
  }
}

// 加载班级列表（只显示辅导员管理的班级）
const loadClasses = async () => {
  try {
    const counselorId = getCurrentCounselorId()
    if (!counselorId) {
      return
    }
    
    const response = await getTeacherClasses(counselorId)
    const teacherClasses = response?.data || []
    
    classes.value = teacherClasses.map((cls: any) => ({
      id: cls.id,
      name: cls.name
    }))
  } catch (error) {
    console.error('加载班级列表失败：', error)
  }
}

// 加载学生详情
const loadStudentDetail = async (studentId: number) => {
  try {
    const response = await evaluationApi.getStudentDetail(studentId, filters.value.period)
    return response
  } catch (error) {
    return null
  }
}

// 监听弹窗关闭，重置只读模式
watch(showEvaluationModal, (newVal) => {
  if (!newVal) {
    isViewOnly.value = false
  }
})

// 页面加载时获取学生列表
onMounted(() => {
  loadScoreRules()
  loadPeriods()
  loadDepartments()
  loadMajors()
  loadClasses()
  loadStudentList()
})

// 总分计算（动态，按权重加权）
const totalScore = computed(() => {
  let weightedSum = 0
  let totalWeight = 0
  scoreItems.value.forEach(item => {
    const score = evaluationForm.value[item.key] || 0
    const weight = item.weight || 1
    weightedSum += score * weight
    totalWeight += weight
  })
  if (totalWeight === 0) return 0
  return Math.round(weightedSum / totalWeight)
})

// 批量评分总分计算（动态，按权重加权）
const batchTotalScore = computed(() => {
  let weightedSum = 0
  let totalWeight = 0
  scoreItems.value.forEach(item => {
    const score = batchEvaluationForm.value[item.key] || 0
    const weight = item.weight || 1
    weightedSum += score * weight
    totalWeight += weight
  })
  if (totalWeight === 0) return 0
  return Math.round(weightedSum / totalWeight)
})

// 根据评分项key获取中文标签
const getScoreLabel = (key: string): string => {
  const item = scoreItems.value.find(item => item.key === key)
  if (item) return item.label
  const defaultLabels: Record<string, string> = {
    attitude: '实习态度',
    performance: '专业能力',
    report: '心得质量',
    companyEvaluation: '单位评价'
  }
  return defaultLabels[key] || key
}

// 等级计算
const grade = computed(() => {
  const score = totalScore.value
  if (score >= 90) return '优秀'
  if (score >= 80) return '良好'
  if (score >= 70) return '中等'
  if (score >= 60) return '及格'
  return '不及格'
})

// 批量评分等级计算
const batchGrade = computed(() => {
  const score = batchTotalScore.value
  if (score >= 90) return '优秀'
  if (score >= 80) return '良好'
  if (score >= 70) return '中等'
  if (score >= 60) return '及格'
  return '不及格'
})

// 表单验证（动态）
const isFormValid = computed(() => {
  // 检查所有评分项是否在有效范围内
  for (const item of scoreItems.value) {
    const score = evaluationForm.value[item.key] || 0
    if (score < 0 || score > item.max) {
      return false
    }
  }
  
  // 检查总分是否超过100分
  if (totalScore.value > 100) {
    return false
  }
  
  // 检查评语是否为空
  if (!evaluationForm.value.comment || evaluationForm.value.comment.trim() === '') {
    return false
  }
  
  return true
})

// 打开综合评价弹窗
const openEvaluationModal = async (student: EvaluationStudent) => {
  currentStudent.value = student
  // 设置当前学生索引
  currentStudentIndex.value = students.value.findIndex(s => s.id === student.id)
  
  // 从后端加载学生详情数据
  const studentDetail = await loadStudentDetail(student.id)
  if (studentDetail) {
    currentStudent.value = studentDetail
  }
  
  // 初始化表单
  const formKeys: any = {}
  scoreItems.value.forEach(item => {
    formKeys[item.key] = 0
  })
  formKeys.comment = ''
  
  // 如果已经评价过，填充已有数据
  if (currentStudent.value.isEvaluated && currentStudent.value.evaluation) {
    const evaluation = currentStudent.value.evaluation
    
    // 优先使用动态scores字段
    if (evaluation.scores) {
      scoreItems.value.forEach(item => {
        formKeys[item.key] = evaluation.scores[item.key] || 0
      })
    } else {
      // 向后兼容：使用传统的评分字段
      scoreItems.value.forEach(item => {
        formKeys[item.key] = evaluation[item.key as keyof typeof evaluation] || 0
      })
    }
    
    formKeys.comment = evaluation.comment || ''
  } else {
    // 否则使用AI建议分数或默认值
    const suggestedScores = currentStudent.value.aiAnalysisData?.suggestedScores || {}
    
    // 优先使用动态scores字段
    if (suggestedScores.scores) {
      scoreItems.value.forEach(item => {
        formKeys[item.key] = suggestedScores.scores[item.key] || 0
      })
    } else {
      // 向后兼容：使用传统的评分字段
      scoreItems.value.forEach(item => {
        formKeys[item.key] = suggestedScores[item.key as keyof typeof suggestedScores] || 0
      })
    }
    // 评语默认使用AI生成的改进建议
    const improvements = currentPeriodReport.value?.aiImprovements || ''
    if (improvements) {
      // 解析改进建议字符串，如 "[建议1, 建议2]" 转为 "建议1; 建议2"
      formKeys.comment = improvements.replace(/[\[\]]/g, '').replace(/,/g, '; ')
    } else {
      formKeys.comment = ''
    }
  }

  evaluationForm.value = { ...formKeys }

  showEvaluationModal.value = true
  expandedStudentId.value = null
}

// 查看已评分学生的详情（打开弹窗，只读模式）
const viewEvaluation = async (student: EvaluationStudent) => {
  isViewOnly.value = true
  await openEvaluationModal(student)
}

// 切换到上一个已提交当前阶段心得的学生
const goToPrevStudent = async () => {
  if (currentStudentIndex.value > 0) {
    // 从当前索引往前查找当前阶段有心得的学生
    for (let i = currentStudentIndex.value - 1; i >= 0; i--) {
      const student = students.value[i]
      // 只检查当前阶段是否有心得
      if (student.hasCurrentPeriodReport) {
        await openEvaluationModal(student)
        return
      }
    }
  }
}

// 切换到下一个已提交当前阶段心得的学生
const goToNextStudent = async () => {
  if (currentStudentIndex.value < students.value.length - 1) {
    // 从当前索引往后查找当前阶段有心得的学生
    for (let i = currentStudentIndex.value + 1; i < students.value.length; i++) {
      const student = students.value[i]
      // 只检查当前阶段是否有心得
      if (student.hasCurrentPeriodReport) {
        await openEvaluationModal(student)
        return
      }
    }
  }
}

// 开始评分（从第一个学生开始）
const startEvaluation = async () => {
  if (students.value.length > 0) {
    const firstStudent = students.value[0]
    await openEvaluationModal(firstStudent)
  }
}

// 切换操作面板
const toggleActionPanel = (student: Student) => {
  if (expandedStudentId.value === student.id) {
    expandedStudentId.value = null
  } else {
    expandedStudentId.value = student.id
  }
}

// 提交评价
const submitEvaluation = async () => {
  if (currentStudent.value && isFormValid.value) {
    try {
      const params: any = {
        studentId: currentStudent.value.id,
        comment: evaluationForm.value.comment
      }

      // 动态添加评分项
      scoreItems.value.forEach(item => {
        params[item.key] = evaluationForm.value[item.key]
      })

      const response = await evaluationApi.submitEvaluation(params)

      // 更新当前学生的评价状态
      if (currentStudent.value) {
        currentStudent.value.isEvaluated = true
        currentStudent.value.evaluation = {
          scores: { ...evaluationForm.value },
          comment: evaluationForm.value.comment,
          totalScore: totalScore.value,
          grade: grade.value
        }
      }

      ElMessage.success('评价提交成功')

      // 不关闭弹窗，等待用户点击"下一篇"或"关闭"
      await loadStudentList()
      await nextTick()

    } catch (error) {
      ElMessage.error('评价提交失败，请重试')
    }
  }
}

// 全选学生
const selectAllStudents = () => {
  selectedStudents.value = filteredStudentsByTab.value.map(s => s.id)
  allSelected.value = true
}

// 取消全选
const clearSelection = () => {
  selectedStudents.value = []
  allSelected.value = false
}

// 切换全选状态
const toggleSelectAll = () => {
  if (allSelected.value) {
    selectAllStudents()
  } else {
    clearSelection()
  }
}

// 打开批量打分弹窗
const openBatchEvaluationModal = () => {
  const formKeys: any = {}
  scoreItems.value.forEach(item => {
    formKeys[item.key] = 0
  })
  formKeys.comment = ''
  
  batchEvaluationForm.value = { ...formKeys }
  showBatchEvaluationModal.value = true
}

// 批量表单验证（动态）
const isBatchFormValid = computed(() => {
  // 检查所有评分项是否在有效范围内
  for (const item of scoreItems.value) {
    const score = batchEvaluationForm.value[item.key] || 0
    if (score < 0 || score > item.max) {
      return false
    }
  }
  
  // 检查总分是否超过100分
  if (batchTotalScore.value > 100) {
    return false
  }
  
  // 检查是否选择了学生
  if (selectedStudents.value.length === 0) {
    return false
  }
  
  return true
})

// 获取学生信息
const getStudentById = (studentId: number) => {
  return students.value.find(s => s.id === studentId)
}

// 提交批量打分
const submitBatchEvaluation = async () => {
  if (!isBatchFormValid.value) return

  try {
    let successCount = 0
    let failCount = 0

    for (const studentId of selectedStudents.value) {
      try {
        const params: any = {
          studentId: studentId,
          comment: batchEvaluationForm.value.comment
        }

        // 动态添加评分项
        scoreItems.value.forEach(item => {
          params[item.key] = batchEvaluationForm.value[item.key]
        })

        const response = await evaluationApi.submitEvaluation(params)

        successCount++
      } catch (error) {
        failCount++
      }
    }

    showBatchEvaluationModal.value = false
    ElMessage.success(`批量打分完成！成功 ${successCount} 人，失败 ${failCount} 人`)

    selectedStudents.value = []
    allSelected.value = false

    await loadStudentList()

  } catch (error) {
    ElMessage.error('批量打分失败，请重试')
  }
}

// 打开 AI 分析对话框
const openAIAnalysisDialog = async (student: EvaluationStudent) => {
  try {
    aiAnalysisLoading.value = true
    const response = await getAnalysisByStudentId(student.id)

    if (response && response.data) {
      const analysis = response.data
      // 将 scoreDetails 对象转换为 aspects 数组格式
      const aspects: Record<string, number> = {}
      if (analysis.scoreDetails) {
        const scoreDetails = parseScoreDetails(analysis.scoreDetails)
        if (typeof scoreDetails === 'object' && !Array.isArray(scoreDetails)) {
          Object.keys(scoreDetails).forEach(key => {
            const detail = scoreDetails[key]
            if (detail && typeof detail === 'object' && detail.score !== undefined) {
              // 分数通常是0-100的范围
              aspects[key] = typeof detail.score === 'number' ? detail.score : 0
            }
          })
        }
      }

      currentAIAnalysis.value = {
        id: analysis.id || 0,
        reflectionId: analysis.reflectionId || 0,
        studentName: student.name,
        analysisTime: analysis.createTime || analysis.createTime || '',
        totalScore: analysis.totalScore || 0,
        grade: analysis.grade || '',
        summary: analysis.analysisReport || analysis.summary || '',
        keywords: analysis.keywords || [],
        aspects: aspects,
        highlights: analysis.strengths || analysis.highlights || [],
        improvements: analysis.suggestions || analysis.improvements || [],
        comment: analysis.comment || '',
        sentimentAnalysis: {
          positive: analysis.sentimentPositive || 0,
          neutral: analysis.sentimentNeutral || 0,
          negative: analysis.sentimentNegative || 0
        }
      }
      aiAnalysisDialogVisible.value = true
    }
  } catch (error) {
    console.error('加载 AI 分析失败:', error)
    ElMessage.error('加载 AI 分析失败')
  } finally {
    aiAnalysisLoading.value = false
  }
}

// 获取分数标签类型
const getScoreTagType = (score: number) => {
  if (score >= 90) return 'success'
  if (score >= 80) return 'primary'
  if (score >= 70) return 'warning'
  if (score >= 60) return 'info'
  return 'danger'
}

// 获取进度条颜色
const getProgressColor = (value: number) => {
  if (value >= 90) return '#67c23a'
  if (value >= 80) return '#409eff'
  if (value >= 70) return '#e6a23c'
  if (value >= 60) return '#909399'
  return '#f56c6c'
}

// 获取维度标签名称
const getAspectLabel = (key: string) => {
  // 优先从 scoreItems 中查找对应的中文名称
  const item = scoreItems.value.find(item => item.key === key)
  if (item) return item.label
  return key
}

// 解析评分详情
const parseScoreDetails = (scoreDetails: any) => {
  try {
    if (typeof scoreDetails === 'string') {
      return JSON.parse(scoreDetails)
    }
    return scoreDetails
  } catch (e) {
    console.error('解析评分详情失败:', e)
    return []
  }
}

// 下载 AI 分析报告
const downloadAIAnalysisReport = () => {
  if (!currentAIAnalysis.value) {
    ElMessage.warning('暂无分析结果可下载')
    return
  }

  const element = document.querySelector('.analysis-result') as HTMLDivElement
  if (!element) {
    ElMessage.error('找不到分析结果内容')
    return
  }

  const opt = {
    margin: [10, 10, 10, 10],
    filename: `实习心得分析报告_${new Date().toLocaleDateString()}.pdf`,
    image: { type: 'jpeg', quality: 0.98 },
    html2canvas: {
      scale: 2,
      useCORS: true,
      backgroundColor: '#ffffff',
      logging: false
    },
    jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' }
  }

  ElMessage.info('正在生成PDF，请稍候...')

  const originalOpacity = element.style.opacity
  const originalTransform = element.style.transform
  element.style.opacity = '1'
  element.style.transform = 'none'

  const resultSections = element.querySelectorAll('.result-section')
  const originalAnimations: string[] = []
  resultSections.forEach((section) => {
    const htmlSection = section as HTMLElement
    originalAnimations.push(htmlSection.style.animation)
    htmlSection.style.animation = 'none'
  })

  html2pdf().set(opt).from(element).save().then(() => {
    element.style.opacity = originalOpacity
    element.style.transform = originalTransform
    resultSections.forEach((section, index) => {
      const htmlSection = section as HTMLElement
      htmlSection.style.animation = originalAnimations[index]
    })
    ElMessage.success('PDF下载成功')
  }).catch((error) => {
    console.error('PDF生成失败:', error)
    element.style.opacity = originalOpacity
    element.style.transform = originalTransform
    resultSections.forEach((section, index) => {
      const htmlSection = section as HTMLElement
      htmlSection.style.animation = originalAnimations[index]
    })
    ElMessage.error('PDF生成失败，请稍后重试')
  })
}
</script>

<style scoped>
.evaluation-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
  position: relative;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  padding: 24px 32px;
  flex-wrap: wrap;
  gap: 20px;
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

/* 右上角统计卡片 */
.overview-cards.top-right {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  grid-template-rows: auto auto;
  gap: 16px;
  align-items: start;
}

.overview-cards.top-right .ai-config-btn {
  grid-column: 1 / -1;
  justify-self: end;
  max-width: 200px;
}

.stat-card {
  background-color: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: all var(--transition-normal);
  overflow: hidden;
  position: relative;
  min-width: 160px;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: var(--color-primary);
}

.stat-card:nth-child(1)::before {
  background: var(--color-primary);
}

.stat-card:nth-child(2)::before {
  background: var(--color-success);
}

.stat-card:nth-child(3)::before {
  background: var(--color-warning);
}

.stat-card:nth-child(4)::before {
  background: var(--color-info);
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}

/* AI评分配置按钮 */
.ai-config-btn {
  padding: 16px 24px;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: all var(--transition-normal);
  background: linear-gradient(135deg, #13c2c2 0%, #08979c 100%);
  border: none;
}

.ai-config-btn:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
  background: linear-gradient(135deg, #08979c 0%, #13c2c2 100%);
}

.ai-config-btn .el-icon {
  font-size: 18px;
}

.stat-icon {
  width: 50px;
  height: 50px;
  border-radius: var(--radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
  transition: all var(--transition-normal);
}

.stat-icon.primary {
  background: var(--color-primary);
  color: white;
}

.stat-icon.success {
  background: var(--color-success);
  color: white;
}

.stat-icon.warning {
  background: var(--color-warning);
  color: white;
}

.stat-icon.info {
  background: var(--color-info);
  color: white;
}

.stat-icon.teal {
  background: #13c2c2;
  color: white;
}

.stat-content {
  flex: 1;
  min-width: 0;
}

.stat-label {
  font-size: 14px;
  color: #666;
  margin-bottom: 4px;
  font-weight: 500;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #333;
  line-height: 1.2;
}

.config-value {
  font-size: 12px;
  font-weight: 500;
  color: #999;
}

/* 响应式调整 */
@media (max-width: 1200px) {
  .overview-cards.top-right {
    grid-template-columns: repeat(2, 1fr);
  }

  .overview-cards.top-right .ai-config-btn {
    grid-column: 1 / -1;
    justify-self: start;
    max-width: none;
  }
}

@media (max-width: 600px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .overview-cards.top-right {
    grid-template-columns: 1fr;
  }

  .stat-card {
    flex-direction: column;
    text-align: center;
    gap: 12px;
  }

  .stat-card::before {
    width: 100%;
    height: 4px;
    top: 0;
    left: 0;
  }

  .ai-config-btn {
    width: 100%;
    justify-content: center;
    max-width: none;
  }
}

.search-card,
.table-card,
.actions-card {
  border-radius: 16px;
  border: none;
  background: white;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  margin-bottom: 16px;
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
  align-items: center;
  padding: 0 20px 10px;
  border-bottom: 1px solid #f0f0f0;
}

.evaluation-tabs {
  padding: 0 20px;
  display: inline-block;
  width: auto;
}

.evaluation-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.evaluation-tabs :deep(.el-tabs__nav-wrap)::after {
  display: none;
}

.table-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.period-badge {
  margin-left: 12px;
}

.table-actions {
  margin-left: auto;
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

.action-btn {
  border-radius: 8px;
  padding: 10px 20px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition: all 0.3s;
}

.action-btn.primary {
  background-color: #409EFF;
  border-color: #409EFF;
}

.action-btn.warning {
  background-color: #e6a23c;
  border-color: #e6a23c;
}

.action-btn.success {
  background-color: #67c23a;
  border-color: #67c23a;
}

.actions-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.primary-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.secondary-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

/* 分页容器样式 */
.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
  padding: 20px;
  border-top: 1px solid #f0f0f0;
}

.custom-pagination {
  display: flex;
  align-items: center;
  gap: 8px;
}

.student-id,
.student-grade,
.student-class {
  font-size: 12px;
  color: #666;
}

/* 实习心得提交状态样式 */
.report-submitted {
  display: flex;
  justify-content: center;
  align-items: center;
  color: #67c23a;
  font-size: 24px;
}

.report-submitted .check-icon {
  font-size: 24px;
}

.report-not-submitted {
  display: flex;
  justify-content: center;
  align-items: center;
  color: #f56c6c;
  font-size: 24px;
}

.report-not-submitted .close-icon {
  font-size: 24px;
}

/* 评分状态样式 */
.status-evaluated {
  display: flex;
  justify-content: center;
  align-items: center;
  color: #67c23a;
  font-size: 24px;
}

.status-evaluated .check-icon {
  font-size: 24px;
}

.status-not-evaluated {
  display: flex;
  justify-content: center;
  align-items: center;
  color: #f56c6c;
  font-size: 24px;
}

.status-not-evaluated .close-icon {
  font-size: 24px;
}
.student-name,
.student-id,
.student-class,
.status-wrapper,
.status-wrapper div {
  font-size: 14px;
}

.status-wrapper,
.ai-status,
.evaluation-status {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  justify-content: center;
  width: 100%;
}

.tag {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.tag-success {
  background-color: #f6ffed;
  color: #52c41a;
}

.tag-warning {
  background-color: #fff7e6;
  color: #faad14;
}

.btn-sm {
  padding: 6px 12px;
  font-size: 12px;
  min-width: 60px;
}

.btn {
  padding: 8px 12px;
  border: none;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
  display: inline-flex;
  align-items: center;
}

.btn-info {
  background-color: #409EFF;
  color: white;
}

.btn-primary {
  background: #409EFF;
  color: white;
}

.btn-success {
  background-color: #67c23a;
  color: white;
}

.btn-default {
  background-color: #f0f0f0;
  color: #333;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1999;
}

.modal-content {
  background-color: white;
  border-radius: 16px;
  width: 500px;
  max-width: 90vw;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.15);
}

.modal-content.large {
  width: 700px;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid #f0f0f0;
  background: linear-gradient(135deg, #409EFF 0%, #66b1ff 100%);
  color: white;
  border-radius: 16px 16px 0 0;
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: white;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.9);
  padding: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
}

.close-btn:hover {
  color: white;
}

.modal-body {
  padding: 24px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
  background-color: #fafafa;
  border-radius: 0 0 16px 16px;
}

.modal-tabs {
  display: flex;
  background-color: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}

.tab-btn {
  padding: 12px 24px;
  background-color: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  font-size: 14px;
  font-weight: 500;
  color: #666;
  cursor: pointer;
  transition: all 0.3s;
}

.tab-btn:hover:not(:disabled) {
  color: #409EFF;
  background-color: rgba(64, 158, 255, 0.1);
}

.tab-btn.active {
  color: #409EFF;
  border-bottom-color: #409EFF;
  font-weight: 600;
  background-color: rgba(64, 158, 255, 0.1);
}

.tab-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.success-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 20px 0;
}

.success-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.success-content p {
  font-size: 16px;
  color: #333;
  margin: 0;
}

.report-not-submitted {
  display: flex;
  justify-content: center;
  align-items: center;
  color: #f56c6c;
  font-size: 24px;
}

.report-not-submitted .close-icon {
  font-size: 24px;
}

/* 评分状态样式 */
.status-evaluated {
  display: flex;
  justify-content: center;
  align-items: center;
  color: #67c23a;
  font-size: 24px;
}

.status-evaluated .check-icon {
  font-size: 24px;
}

.status-not-evaluated {
  display: flex;
  justify-content: center;
  align-items: center;
  color: #f56c6c;
  font-size: 24px;
}

.status-not-evaluated .close-icon {
  font-size: 24px;
}

.no-rules-warning {
  padding: 32px;
  text-align: center;
  background-color: #fef0f0;
  border-radius: 12px;
  border: 1px solid #fde2e2;
}

.warning-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.warning-icon {
  font-size: 48px;
}

.warning-text h5 {
  margin: 0 0 8px 0;
  font-size: 18px;
  font-weight: 600;
  color: #f56c6c;
}

.warning-text p {
  margin: 0 0 16px 0;
  font-size: 14px;
  color: #666;
  line-height: 1.6;
}

.warning-text .btn {
  margin-top: 8px;
}

.report-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.report-item {
  padding: 16px;
  background-color: #ecf5ff;
  border-radius: 12px;
  border-left: 4px solid #409EFF;
}

.report-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.report-header h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.report-date {
  font-size: 12px;
  color: #666;
}

.report-content {
  font-size: 14px;
  line-height: 1.6;
  color: #333;
  white-space: pre-wrap;
}

.ai-analysis {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.total-ai-analysis {
  margin-top: 24px;
  padding: 20px;
  background-color: #f8f9fa;
  border-radius: 16px;
  border: 1px solid #e9ecef;
}

.total-ai-analysis h4 {
  margin: 0 0 20px 0;
  font-size: 18px;
  font-weight: 600;
  color: #409EFF;
}

.ai-section h4 {
  margin: 0 0 12px 0;
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.form-section {
  padding: 16px;
  border-radius: 12px;
  margin-bottom: 20px;
}

.ai-section-row {
  display: flex;
  gap: 16px;
  flex-wrap: nowrap;
}

.ai-section.half {
  flex: 1;
  min-width: 250px;
}

.modal-content.large {
  width: 800px;
}

.ai-section h4 {
  margin: 0 0 12px 0;
  font-size: 16px;
  font-weight: 600;
}

.keywords {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.keyword-tag {
  display: inline-block;
  padding: 4px 12px;
  background-color: #ecf5ff;
  color: #409EFF;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  border-color: #b3d8ff;
}

.sentiment,
.ai-score {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
}

.form-row {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
}

.form-group.half {
  flex: 1;
  margin-bottom: 0;
}

.form-group.quarter {
  flex: 1;
  margin-bottom: 0;
}

.student-name-display,
.student-id-display,
.grade-display,
.class-display,
.department-display,
.major-display,
.company-display {
  padding: 12px 16px;
  background-color: #f8f9fa;
  border-radius: 8px;
  font-size: 14px;
  color: #333;
  font-weight: 500;
  border: 1px solid #e9ecef;
}

.evaluation-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-section-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.form-section.top {
  flex: 1;
  min-width: 0;
}

.form-section.full {
  width: 100%;
}

.form-section.right {
  flex: 1;
  min-width: 0;
}

.form-section.half {
  flex: 1;
  min-width: 300px;
}

.form-section h4 {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
  color: #409EFF;
}

.student-details {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  padding: 16px;
  background: #ecf5ff;
  border-radius: 12px;
  border-left: 4px solid #409EFF;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
}

.student-details-inline {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  padding: 12px;
  background: #ecf5ff;
  border-radius: 12px;
  border-left: 4px solid #409EFF;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-item-inline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-item-inline label {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.detail-item-inline span {
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

.detail-item label {
  font-size: 12px;
  color: #666;
  font-weight: 500;
}

.detail-item span {
  font-size: 14px;
  color: #333;
  font-weight: 600;
}

.score-items {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
  background: #f0f9eb;
  border-radius: 12px;
  border-left: 4px solid #67c23a;
  box-shadow: 0 2px 8px rgba(103, 194, 58, 0.1);
}

.score-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: #f8f9fa;
  border-radius: 8px;
  margin-bottom: 10px;
}

.score-item .score-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.score-item .weight-badge {
  font-size: 11px;
  color: #409EFF;
  background: #ecf5ff;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 500;
}

.score-item .score-item-description {
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
  padding: 4px 0;
}

.score-item .score-item-criteria {
  font-size: 11px;
  color: #909399;
  background: #fff;
  padding: 6px 8px;
  border-radius: 4px;
  border-left: 3px solid #e6a23c;
}

.score-item .criteria-label {
  font-weight: 500;
  color: #e6a23c;
  margin-right: 4px;
}

.score-item .criteria-text {
  color: #606266;
}

.score-item label {
  min-width: 120px;
  font-weight: 500;
  color: #333;
}

.score-item input {
  width: 80px;
  padding: 8px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  font-size: 14px;
  transition: all 0.3s;
}

.score-item input:focus {
  outline: none;
  border-color: #409EFF;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.total-score {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  background: #fdf6ec;
  border-radius: 12px;
  border-left: 4px solid #e6a23c;
  box-shadow: 0 2px 8px rgba(230, 162, 60, 0.1);
}

.total-score-value {
  font-size: 20px;
  font-weight: 600;
  color: #409EFF;
}

.grade-value {
  font-size: 18px;
  font-weight: 600;
  color: #67c23a;
}

.score-exceeded {
  color: #f56c6c;
}

.score-warning {
  margin-left: 12px;
  font-size: 12px;
  color: #f56c6c;
  font-weight: 500;
}

.comment-textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #d9d9d9;
  border-radius: 12px;
  font-size: 14px;
  resize: vertical;
  background: white;
  transition: all 0.3s;
}

.comment-textarea:focus {
  outline: none;
  border-color: #409EFF;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
  background: white;
}

.btn {
  padding: 8px 12px;
  border: none;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
  display: inline-flex;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.btn-info {
  background: #409EFF;
  color: white;
}

.btn-primary {
  background: #409EFF;
  color: white;
}

.btn-success {
  background: #67c23a;
  color: white;
}

.btn-default {
  background: #f0f0f0;
  color: #333;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  padding: 20px 0;
}

.page-btn {
  padding: 6px 12px;
  background-color: white;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.3s;
}

.page-btn:hover:not(:disabled) {
  border-color: #409EFF;
  color: #409EFF;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 14px;
  color: #666;
}

.page-jump {
  display: flex;
  gap: 8px;
  align-items: center;
}

.page-input {
  width: 60px;
  padding: 6px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  font-size: 14px;
  text-align: center;
}

.jump-btn {
  padding: 6px 12px;
  background-color: #409EFF;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.3s;
}

.jump-btn:hover {
  background-color: #66b1ff;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.list-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.batch-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.checkbox-column {
  width: 50px !important;
  text-align: center;
}

.checkbox-column input[type="checkbox"] {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.batch-evaluation-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.note-text {
  padding: 12px;
  background-color: #fdf6ec;
  border-left: 4px solid #e6a23c;
  border-radius: 8px;
  margin-top: 12px;
}

.note-text p {
  margin: 0;
  font-size: 13px;
  color: #e6a23c;
  font-weight: 500;
}

.selected-students-list {
  max-height: 200px;
  overflow-y: auto;
  padding: 12px;
  background-color: #f9f9f9;
  border-radius: 12px;
  border: 1px solid #e8e8e8;
}

.selected-student-item {
  padding: 8px 12px;
  background-color: white;
  border-radius: 8px;
  margin-bottom: 8px;
  border: 1px solid #e8e8e8;
}

.selected-student-item:last-child {
  margin-bottom: 0;
}

.selected-student-item .student-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.selected-student-item .info-row {
  display: flex;
  align-items: center;
  font-size: 13px;
}

.selected-student-item .info-label {
  color: #666;
  min-width: 50px;
  font-weight: 500;
}

.selected-student-item .info-value {
  color: #333;
  flex: 1;
}

.success-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 20px;
}

.success-icon {
  font-size: 48px;
}

.success-content p {
  font-size: 16px;
  color: #333;
  margin: 0;
  text-align: center;
}

.ai-analysis-dialog {
  z-index: 999999 !important;
}

.ai-analysis-dialog :deep(.el-dialog) {
  z-index: 999999 !important;
}

.ai-analysis-dialog :deep(.el-overlay-mask) {
  z-index: 999999 !important;
}

.ai-analysis-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #409EFF 0%, #66b1ff 100%);
  color: white;
  padding: 16px 20px;
  border-radius: 16px 16px 0 0;
}

.ai-analysis-dialog :deep(.el-dialog__title) {
  color: white;
  font-size: 18px;
  font-weight: 600;
}

.ai-analysis-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
}

.ai-analysis-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  color: #f0f0f0;
}

.ai-analysis-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.ai-report-content {
  max-height: 70vh;
  overflow-y: auto;
  padding: 0;
}

.reflection-result-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 30px 0;
}

.reflection-result-section .result-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: white;
  border-radius: 16px;
  border: 1.5px solid #e4e7ed;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.reflection-result-section .analysis-result {
  flex: 1;
  overflow-y: auto;
  padding: 30px;
}

.result-container {
  background: white;
  border-radius: 16px;
  border: 1.5px solid #e4e7ed;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 30px;
}

.result-header h3 {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.result-header .header-actions {
  display: flex;
  gap: 12px;
}

.result-header .el-button {
  padding: 12px 30px;
  font-size: 16px;
}

.divider-line {
  height: 1px;
  background: #e4e7ed;
  margin: 0 30px;
}

.analysis-result {
  padding: 30px;
}

.result-section {
  margin-bottom: 30px;
  padding-bottom: 30px;
  animation: fadeInUp 0.5s ease-out;
}

.result-section:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.result-section h4 {
  margin: 0 0 16px 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 12px;
}

.result-section h4::before {
  content: '';
  width: 4px;
  height: 20px;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  border-radius: 2px;
}

.keywords-container {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.no-keywords {
  color: #909399;
  font-size: 14px;
  font-style: italic;
}

.keyword-tag {
  border-radius: 20px;
  padding: 8px 18px;
  font-size: 15px;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
}

.score-display {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px;
  background: #f8f9fa;
  border-radius: 16px;
  border: 1.5px solid #e4e7ed;
}

.score-value {
  font-size: 56px;
  font-weight: 700;
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.aspects-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.aspect-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.aspect-label {
  font-size: 15px;
  color: #606266;
  font-weight: 500;
}

.summary-text,
.comment-text {
  margin: 0;
  font-size: 16px;
  line-height: 1.8;
  color: #606266;
  padding: 20px;
  background: #f8f9fa;
  border-radius: 12px;
  border-left: 4px solid #409EFF;
}

.sentiment-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  background: #f8f9fa;
  border-radius: 12px;
}

.sentiment-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sentiment-label {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 80px;
}

.sentiment-icon {
  font-size: 18px;
}

.sentiment-name {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

.sentiment-item :deep(.el-progress) {
  flex: 1;
}

.sentiment-item :deep(.el-progress__text) {
  font-size: 14px !important;
  font-weight: 600;
  color: #606266;
}

.highlight-list,
.improvement-list {
  margin: 0;
  padding-left: 0;
  list-style: none;
}

.highlight-list li,
.improvement-list li {
  position: relative;
  padding: 16px 0 16px 32px;
  font-size: 16px;
  line-height: 1.8;
  color: #606266;
  border-bottom: 1px solid #ebeef5;
}

.highlight-list li:last-child,
.improvement-list li:last-child {
  border-bottom: none;
}

.highlight-list li::before {
  content: '✓';
  position: absolute;
  left: 0;
  top: 16px;
  width: 24px;
  height: 24px;
  background: #67c23a;
  color: white;
  border-radius: 50%;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
}

.improvement-list li::before {
  content: '!';
  position: absolute;
  left: 0;
  top: 16px;
  width: 24px;
  height: 24px;
  background: #e6a23c;
  color: white;
  border-radius: 50%;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
}

.combined-modal {
  display: flex;
  gap: 16px;
  width: 1300px;
  max-width: 95vw;
  height: 85vh;
}

.combined-body {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}

.evaluation-card {
  width: 480px;
  min-width: 400px;
  max-width: 520px;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.evaluation-card .modal-header {
  flex-shrink: 0;
  border-bottom: 1px solid #f0f0f0;
}

.evaluation-card .modal-footer {
  flex-shrink: 0;
  border-top: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
}

.evaluation-card .modal-footer .footer-left {
  display: flex;
  gap: 8px;
}

.evaluation-card .modal-footer .footer-right {
  display: flex;
  gap: 8px;
}

.btn-nav {
  padding: 6px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  color: #606266;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-nav:hover:not(:disabled) {
  border-color: #409EFF;
  color: #409EFF;
}

.btn-nav:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.evaluation-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  overflow-y: auto;
  min-width: 0;
}

.evaluation-section-fixed {
  flex-shrink: 0;
  flex-grow: 0;
  background: #fafbfc;
  border-top: 1px solid #e8e8e8;
  padding: 12px 16px;
  max-height: 50%;
  overflow-y: auto;
}

.evaluation-section-fixed .section-header {
  margin-bottom: 8px;
  position: sticky;
  top: 0;
  background: #fafbfc;
  padding-bottom: 8px;
  z-index: 1;
}

.evaluation-section-fixed .evaluation-form-compact {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.evaluation-section-fixed .score-items-compact {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.evaluation-section-fixed .score-item-compact {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px;
  background: #f8f9fa;
  border-radius: 8px;
  margin-bottom: 8px;
}

.score-item-compact .score-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.score-item-compact .weight-badge {
  font-size: 11px;
  color: #409EFF;
  background: #ecf5ff;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 500;
}

.score-item-compact .score-item-description {
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
  padding: 4px 0;
}

.score-item-compact .score-item-criteria {
  font-size: 11px;
  color: #909399;
  background: #fff;
  padding: 6px 8px;
  border-radius: 4px;
  border-left: 3px solid #e6a23c;
}

.score-item-compact .criteria-label {
  font-weight: 500;
  color: #e6a23c;
  margin-right: 4px;
}

.score-item-compact .criteria-text {
  color: #606266;
}

.evaluation-section-fixed .score-item-compact label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}

.evaluation-section-fixed .score-item-compact .max-score {
  font-size: 12px;
  color: #909399;
}

.evaluation-section-fixed .score-input {
  width: 60px;
  padding: 4px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  font-size: 13px;
  text-align: center;
}

.evaluation-section-fixed .comment-section {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.evaluation-section-fixed .comment-section label {
  font-size: 13px;
  color: #606266;
  line-height: 32px;
  white-space: nowrap;
}

.evaluation-section-fixed .comment-textarea-compact {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  font-size: 13px;
  resize: none;
  min-height: 50px;
}

.word-document-card {
  flex: 1;
  min-width: 500px;
  height: 100%;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.word-document-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #2b579a 0%, #1e3f6f 100%);
  color: white;
  border-radius: 16px 16px 0 0;
}

.word-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}

.word-title {
  font-size: 14px;
  font-weight: 600;
  flex: 1;
}

.current-period-badge {
  font-size: 12px;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.25);
  color: white;
  padding: 4px 12px;
  border-radius: 12px;
  margin-left: 8px;
}

.report-count {
  font-size: 12px;
  opacity: 0.9;
  background: rgba(255, 255, 255, 0.2);
  padding: 2px 8px;
  border-radius: 10px;
}

.word-document-body {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.word-content-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.word-page {
  flex: 1;
  margin: 16px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.word-page-content {
  flex: 1;
  padding: 24px 32px;
  overflow-y: auto;
}

.report-meta {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.report-period {
  font-size: 13px;
  font-weight: 600;
  color: #409EFF;
  background: #ecf5ff;
  padding: 4px 12px;
  border-radius: 12px;
}

.report-date {
  font-size: 12px;
  color: #999;
}

.report-full-content {
  font-size: 14px;
  line-height: 1.8;
  color: #333;
  white-space: pre-wrap;
  word-break: break-word;
  text-align: justify;
}

.word-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999;
}

.word-empty .empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
  opacity: 0.5;
}

.word-empty p {
  margin: 0;
  font-size: 14px;
}

.student-basic-info {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 12px;
  border-left: 3px solid #409EFF;
}

.student-basic-info .info-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}

.student-basic-info .label {
  color: #666;
  font-weight: 500;
}

.section-card {
  background: white;
  border: 1px solid #e8e8e8;
  border-radius: 12px;
  overflow: hidden;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: #fafafa;
  border-bottom: 1px solid #e8e8e8;
}

.section-header h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.report-count-badge {
  font-size: 12px;
  color: #409EFF;
  background: #ecf5ff;
  padding: 2px 8px;
  border-radius: 10px;
}

.ai-summary-container {
  padding: 16px;
}

.ai-summary-content {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  background: linear-gradient(135deg, #ecf5ff 0%, #d9ecff 100%);
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #c6e2ff;
}

.ai-summary-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.ai-summary-text {
  font-size: 14px;
  line-height: 1.8;
  color: #333;
  text-align: justify;
}

.ai-summary-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  background: #f9fafb;
  border-radius: 12px;
  border: 1px dashed #d9d9d9;
}

.placeholder-icon {
  font-size: 48px;
  margin-bottom: 12px;
  opacity: 0.6;
}

.placeholder-text {
  text-align: center;
}

.placeholder-text p {
  margin: 0;
  font-size: 14px;
  color: #666;
}

.placeholder-text .hint {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.ai-analysis-compact {
  padding: 12px 16px;
}

.ai-row {
  display: flex;
  gap: 16px;
}

.ai-col {
  flex: 1;
}

.ai-label {
  font-size: 12px;
  color: #666;
  margin-bottom: 6px;
  font-weight: 500;
}

.keywords-compact {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.keyword-tag-compact {
  display: inline-block;
  padding: 2px 8px;
  background: #ecf5ff;
  color: #409EFF;
  border-radius: 6px;
  font-size: 11px;
}

.sentiment-compact {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sentiment-item-compact {
  font-size: 12px;
  padding: 2px 0;
}

.sentiment-item-compact.positive {
  color: #67c23a;
}

.sentiment-item-compact.neutral {
  color: #409EFF;
}

.sentiment-item-compact.negative {
  color: #f56c6c;
}

.suggested-scores-compact {
  font-size: 12px;
  color: #666;
}

.score-tag {
  display: inline-block;
  margin-right: 8px;
  padding: 2px 6px;
  background: #f0f0f0;
  border-radius: 6px;
}

.evaluation-section .section-header {
  background: #ecf5ff;
}

.total-display {
  display: flex;
  align-items: center;
  gap: 8px;
}

.total-label {
  font-size: 13px;
  color: #666;
}

.total-value {
  font-size: 18px;
  font-weight: 700;
  color: #409EFF;
}

.total-value.score-exceeded {
  color: #f56c6c;
}

.grade-badge {
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
}

.grade-badge.grade-优秀 {
  background: #f0f9eb;
  color: #67c23a;
}

.grade-badge.grade-良好 {
  background: #ecf5ff;
  color: #409EFF;
}

.grade-badge.grade-中等 {
  background: #fdf6ec;
  color: #e6a23c;
}

.grade-badge.grade-及格 {
  background: #f0f0f0;
  color: #666;
}

.grade-badge.grade-不及格 {
  background: #fef0f0;
  color: #f56c6c;
}

.view-ai-report-btn {
  margin-left: 8px;
}

.no-rules-warning-compact {
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fdf6ec;
  color: #e6a23c;
  font-size: 13px;
}

.warning-icon-sm {
  font-size: 16px;
}

.btn-link {
  background: none;
  border: none;
  color: #409EFF;
  cursor: pointer;
  font-size: 13px;
  text-decoration: underline;
  padding: 0;
}

.btn-link:hover {
  color: #66b1ff;
}

.evaluation-form-compact {
  padding: 12px 16px;
}

.score-items-compact {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.score-item-compact {
  display: flex;
  align-items: center;
  gap: 8px;
}

.score-item-compact label {
  font-size: 13px;
  color: #333;
  min-width: 80px;
}

.max-score {
  font-size: 11px;
  color: #999;
}

.score-input {
  flex: 1;
  max-width: 80px;
  padding: 6px 10px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  font-size: 13px;
  text-align: center;
}

.score-input:focus {
  border-color: #409EFF;
  outline: none;
}

.comment-section {
  margin-top: 12px;
}

.comment-section label {
  display: block;
  font-size: 13px;
  color: #333;
  margin-bottom: 6px;
  font-weight: 500;
}

.comment-textarea-compact {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.5;
  resize: vertical;
  min-height: 60px;
}

.comment-textarea-compact:focus {
  border-color: #409EFF;
  outline: none;
}
</style>