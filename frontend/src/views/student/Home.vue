<template>
  <div class="home-container">
    <div class="welcome-section">
      <div class="welcome-content">
        <div class="welcome-left">
          <div class="avatar-wrapper" @click="changeAvatar">
            <img :src="avatarUrl || 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgdmlld0JveD0iMCAwIDEwMCAxMDAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgPGNpcmNsZSBjeD0iNTAiIGN5PSI1MCIgcj0iNTAiIGZpbGw9InVybCgjZ3JhZGllbnQpIi8+CiAgPGNpcmNsZSBjeD0iNTAiIGN5PSI0MiIgcj0iMjAiIGZpbGw9IiNmZmZmZmYiLz4KICA8cGF0aCBkPSJNMzAgODVINTBDMzAgNzUgMzUgNzUgMzUgODVMMzAgODVaIiBmaWxsPSIjZmZmZmZmIi8+CiAgPHBhdGggZD0iTTcwIDg1TDUwIDg1QzcwIDc1IDY1IDc1IDY1IDg1TDcwIDg1WiIgZmlsbD0iI2ZmZmZmZiIvPgogIDxkZWZzPgogICAgPGxpbmVhckdyYWRpZW50IGlkPSJncmFkaWVudCIgeDE9IjAlIiB5MT0iMCUiIHgyPSIxMDAlIiB5Mj0iMTAwJSI+CiAgICAgIDxzdG9wIG9mZnNldD0iMCUiIHN0b3AtY29sb3I9IiM0MDlFRkYiLz4KICAgICAgPHN0b3Agb2Zmc2V0PSIxMDAlIiBzdG9wLWNvbG9yPSIjNjdDMjNBIi8+CiAgICA8L2xpbmVhckdyYWRpZW50PgogIDwvZGVmcz4KPC9zdmc+'" class="avatar-img" alt="头像" />
            <div class="avatar-edit-overlay">
              <el-icon class="edit-icon"><Camera /></el-icon>
            </div>
          </div>
          <input
            ref="avatarInput"
            type="file"
            accept="image/*"
            style="display: none"
            @change="handleAvatarChange"
          />
          <div class="welcome-text">
            <h1 class="welcome-title">欢迎回来，{{ userName }}</h1>
            <p class="welcome-date">{{ currentDate }}</p>
            <div v-if="!hasProfile" class="profile-reminder">
              <span class="reminder-text">请先完善个人资料</span>
              <el-button type="primary" size="small" @click="goToProfile" class="reminder-btn">去完善</el-button>
            </div>
          </div>
        </div>
        <div class="welcome-right">
          <div class="path-selector">
            <div class="path-card" @click="goToJobs">
              <div class="path-icon path-icon-blue"><el-icon><Search /></el-icon></div>
              <div class="path-text">
                <span class="path-title">浏览岗位</span>
                <span class="path-desc">去职位页查看和投递</span>
              </div>
              <el-icon class="path-arrow"><ArrowRight /></el-icon>
            </div>
            <div class="path-card" data-agent-open @click="openSelfPracticeDialog">
              <div class="path-icon path-icon-green"><el-icon><EditPen /></el-icon></div>
              <div class="path-text">
                <span class="path-title">自主实习</span>
                <span class="path-desc">自行联系实习单位</span>
              </div>
              <el-icon class="path-arrow"><ArrowRight /></el-icon>
            </div>
            <div class="path-card" data-agent-open @click="openDelayDialog">
              <div class="path-icon path-icon-orange"><el-icon><Timer /></el-icon></div>
              <div class="path-text">
                <span class="path-title">考研延迟</span>
                <span class="path-desc">延迟实习准备考研</span>
              </div>
              <el-icon class="path-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="stats-grid">
      <div class="stat-item applied" @click="navigateToApplications">
        <div class="stat-icon">
          <el-icon><Document /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-label">已申请</div>
          <div class="stat-value">{{ stats.applied }}</div>
        </div>
      </div>
      <div class="stat-item interview" @click="navigateToInterviews">
        <div class="stat-icon">
          <el-icon><Message /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-label">面试邀请</div>
          <div class="stat-value">{{ stats.interviewInvites }}</div>
        </div>
      </div>
      <div class="stat-item current" @click="navigateToConfirmationForm">
        <div class="stat-icon">
          <el-icon><TrendCharts /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-label">确认记录</div>
          <div class="stat-value">{{ stats.current }}</div>
        </div>
      </div>
      <div v-if="isReflectionMenuVisible" class="stat-item pending" @click="navigateToInternships">
        <div class="stat-icon">
          <el-icon><Clock /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-label">心得已提交</div>
          <div class="stat-value">{{ stats.pendingReports }}</div>
        </div>
      </div>
    </div>

    <div class="content-grid">
      <div class="content-card todo-card">
        <div class="card-header">
          <div class="header-left">
            <el-icon class="header-icon warning"><Bell /></el-icon>
            <h3>待办事项</h3>
          </div>
          <span class="badge urgent">{{ visibleTodoList.length }}</span>
        </div>
        <div class="card-list">
          <div
            v-for="item in visibleTodoList"
            :key="item.id"
            class="list-item"
            @click="handleTodoClick(item)"
          >
            <div class="item-left">
              <div :class="['item-dot', item.priority]"></div>
              <div class="item-content">
                <div class="item-title">{{ item.title }}</div>
                <div class="item-desc">{{ item.desc }}</div>
              </div>
            </div>
            <el-icon class="item-arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>

      <div class="content-card progress-card">
        <div class="card-header">
          <div class="header-left">
            <el-icon class="header-icon info"><TrendCharts /></el-icon>
            <h3>最近动态</h3>
          </div>
        </div>
        <div ref="progressScrollContainer" class="card-list recent-activity-body">
          <div v-if="progressLoading" class="activity-skeleton" role="status" aria-label="正在加载最近动态">
            <div v-for="index in 4" :key="index" class="activity-skeleton-item" aria-hidden="true">
              <span class="skeleton-node"></span>
              <div class="skeleton-content">
                <span class="skeleton-line skeleton-title"></span>
                <span class="skeleton-line skeleton-description"></span>
                <span class="skeleton-line skeleton-time"></span>
              </div>
            </div>
            <span class="visually-hidden">正在加载最近动态</span>
          </div>

          <div v-else-if="progressInitialError" class="activity-state" role="alert">
            <el-icon><Warning /></el-icon>
            <strong>动态加载失败</strong>
            <span>请检查网络后重试</span>
            <el-button type="primary" plain @click="retryProgress">重新加载</el-button>
          </div>

          <div v-else-if="progressList.length === 0" class="activity-state activity-empty">
            <el-icon><Bell /></el-icon>
            <strong>还没有实习动态</strong>
            <span>投递岗位或提交申请后会显示在这里</span>
            <el-button type="primary" plain @click="goToJobs">浏览岗位</el-button>
          </div>

          <ol v-else class="activity-timeline" aria-label="最近实习动态">
            <li v-for="group in groupedProgress" :key="group.key" class="activity-date-group">
              <h4 class="activity-date-label">{{ group.label }}</h4>
              <ol class="activity-group-list">
                <li v-for="item in group.items" :key="item.id" class="activity-item">
                  <span :class="['activity-node', `category-${item.category}`]" aria-hidden="true">
                    <el-icon><component :is="getActivityIcon(item.category)" /></el-icon>
                  </span>
                  <article class="activity-content">
                    <div class="activity-heading">
                      <strong class="activity-title">{{ item.title }}</strong>
                      <span :class="['activity-status', `status-${item.statusTone}`]">
                        {{ item.statusText }}
                      </span>
                    </div>
                    <p v-if="item.description" class="activity-description">{{ item.description }}</p>
                    <p v-if="shouldShowActivityStage(item)" class="activity-stage">第 {{ item.stageNo }} 阶段</p>
                    <div class="activity-meta">
                      <time :datetime="item.occurredAt || undefined" :title="item.fullTimeText">
                        {{ item.timeText }}
                      </time>
                      <router-link
                        v-if="item.action"
                        class="activity-action"
                        :to="item.action.to"
                        :aria-label="`${item.action.label}：${item.title}`"
                      >
                        {{ item.action.label }}
                        <el-icon><ArrowRight /></el-icon>
                      </router-link>
                    </div>
                  </article>
                </li>
              </ol>
            </li>
          </ol>

          <div v-if="!progressLoading && progressList.length > 0" class="activity-load-more">
            <div v-if="progressLoadMoreError" class="load-more-error" role="alert">
              <span>加载更多失败</span>
              <button type="button" @click="loadMoreProgress">重试</button>
            </div>
            <el-button
              v-else-if="progressHasMore"
              text
              type="primary"
              :loading="progressLoadingMore"
              @click="loadMoreProgress"
            >
              {{ progressLoadingMore ? '加载中' : '加载更多' }}
            </el-button>
            <span v-else class="activity-end">没有更多了</span>
          </div>
        </div>
      </div>

      <div class="content-card notification-card">
        <div class="card-header">
          <div class="header-left">
            <el-icon class="header-icon primary"><Message /></el-icon>
            <h3>公告通知</h3>
          </div>
          <span v-if="unreadCount > 0" class="badge new">{{ unreadCount }}</span>
        </div>
        <div class="card-list">
          <el-empty v-if="recentNotificationList.length === 0" description="暂无公告通知" />
          <TransitionGroup v-else name="notif" tag="div" class="notification-list">
            <StudentAnnouncementMessageItem
              v-for="item in recentNotificationList"
              :key="item.id"
              :announcement="item"
              @select="handleNotificationClick"
            />
          </TransitionGroup>
        </div>
        <div class="card-footer">
          <span class="view-more" @click="goToMessageCenter">查看详情</span>
        </div>
      </div>
    </div>

    <StudentAnnouncementDetailDialog
      v-model="showNotificationDialog"
      :announcement="selectedNotification"
    />
    <!-- 自主实习申请弹窗 -->
    <el-dialog
      v-model="showSelfPracticeDialog"
      width="500px"
      class="onboarding-dialog"
      append-to-body
      lock-scroll
      modal-class="global-modal"
      :title="''"
    >
      <div class="dialog-content">
        <div class="header-section">
          <h1 class="header-title">自主实习申请</h1>
        </div>

        <div v-if="selfPracticeApplicationLoading" class="delay-state-panel">
          <el-icon class="delay-state-icon"><Clock /></el-icon>
          <div class="delay-state-title">正在核对申请状态</div>
          <div class="delay-state-detail">请稍候，避免重复提交。</div>
        </div>

        <div v-else-if="selfPracticeApplicationError" class="delay-state-panel">
          <el-icon class="delay-state-icon warning"><Warning /></el-icon>
          <div class="delay-state-title">申请状态加载失败</div>
          <div class="delay-state-detail">{{ selfPracticeApplicationError }}</div>
        </div>

        <div v-else-if="selfPracticeApplicationView === 'result'" class="delay-state-panel">
          <el-icon class="delay-state-icon" :class="{ success: selfPracticeApplication?.status === 'approved' }">
            <CircleCheck v-if="selfPracticeApplication?.status === 'approved'" />
            <Clock v-else />
          </el-icon>
          <div class="delay-state-title">{{ studentApplicationStatusText[selfPracticeApplication?.status] || '等待教师审核' }}</div>
          <div class="delay-state-detail">
            {{ selfPracticeApplication?.status === 'approved' ? '自主实习申请已通过。如需更换实习单位，可点击“再次申请”；新申请审核通过后才会切换当前实习。' : '教师尚未进行通过或驳回操作，可撤回后重新提交。' }}
          </div>
          <div class="delay-state-meta">
            <span>实习单位：{{ selfPracticeApplication?.company || '-' }}</span>
            <span>实习岗位：{{ selfPracticeApplication?.positionName || '-' }}</span>
            <span>提交时间：{{ selfPracticeApplication?.applyTime || selfPracticeApplication?.createTime || '-' }}</span>
            <span v-if="selfPracticeApplication?.reviewTime">审核时间：{{ selfPracticeApplication.reviewTime }}</span>
            <span v-if="selfPracticeApplication?.materials?.['个人申请书']">个人申请书：已上传</span>
            <span v-if="selfPracticeApplication?.materials?.['实习接收函']">实习接收函：已上传</span>
          </div>
        </div>

        <div v-else class="onboarding-form">
          <el-alert
            v-if="selfPracticeApplication?.status === 'rejected'"
            class="delay-state-alert"
            type="error"
            :closable="false"
            show-icon
          >
            <template #title>教师已驳回</template>
            驳回理由：{{ selfPracticeApplication?.rejectReason || '教师未填写理由' }}
          </el-alert>
          <el-alert
            v-else-if="selfPracticeApplication?.status === 'withdrawn'"
            class="delay-state-alert"
            type="info"
            :closable="false"
            show-icon
          >
            <template #title>原申请已撤回</template>
            可以修改申请内容后重新提交；未替换的已上传材料会保留。
          </el-alert>
          <el-form label-width="160px">
            <el-form-item label="实习单位" required>
              <div class="company-field">
                <el-autocomplete
                  v-model="selfPracticeForm.company"
                  :fetch-suggestions="queryCooperativeCompanies"
                  :debounce="300"
                  :trigger-on-focus="false"
                  placeholder="请输入实习单位名称"
                  maxlength="100"
                  clearable
                  @input="handleCompanyNameInput"
                  @select="handleCooperativeCompanySelect"
                >
                  <template #default="{ item }">
                    <div class="company-suggestion">
                      <span>{{ item.companyName }}</span>
                      <el-tag type="warning" size="small">校企合作</el-tag>
                    </div>
                  </template>
                </el-autocomplete>
                <el-alert
                  v-if="matchedCooperativeCompany"
                  class="company-match-info"
                  type="success"
                  :closable="false"
                  show-icon
                >
                  <template #title>校企合作单位</template>
                  <div>
                    已匹配系统登记的校企合作单位“{{ matchedCooperativeCompany.companyName }}”，可继续提交自主实习申请，教师端将显示校企合作标识。
                  </div>
                </el-alert>
                <el-alert
                  v-else-if="similarCooperativeCompanies.length > 0"
                  class="company-match-warning"
                  type="warning"
                  :closable="false"
                  show-icon
                >
                  <template #title>存在名称相近的校企合作单位</template>
                  <div>
                    系统企业库中存在名称相近的企业：{{ similarCooperativeCompanies.map((item) => item.companyName).join('、') }}。为避免同一企业因名称不一致产生错误，请从下拉候选中选择系统登记的标准企业名称；若该企业确实不在系统中，请核对名称后再提交。
                  </div>
                </el-alert>
              </div>
            </el-form-item>
            <el-form-item label="实习岗位" required>
              <el-input v-model="selfPracticeForm.positionName" placeholder="请输入实习岗位名称" maxlength="255" clearable />
            </el-form-item>
            <el-form-item label="单位所在城市" required>
              <el-input v-model="selfPracticeForm.companyCity" placeholder="例如：广东省广州市" maxlength="100" clearable />
            </el-form-item>
            <el-form-item label="人事经理姓名" required>
              <el-input v-model="selfPracticeForm.hrManagerName" placeholder="请输入单位人事联系人姓名" maxlength="50" clearable />
            </el-form-item>
            <el-form-item label="人事经理联系电话" required>
              <el-input v-model="selfPracticeForm.hrManagerPhone" placeholder="请输入手机或固定电话号码" maxlength="30" inputmode="tel" clearable />
            </el-form-item>
            <el-form-item label="申请理由" required>
              <el-input v-model="selfPracticeForm.reason" type="textarea" placeholder="请简要说明自主实习原因" :rows="3" clearable />
            </el-form-item>
            <el-form-item label="个人申请书">
              <el-upload
                class="onboarding-upload"
                action="#"
                :auto-upload="false"
                :on-change="(file) => handleOnboardingFileChange(file, 'selfPractice', 'applicationLetter')"
                :file-list="selfPracticeFileList"
                accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
                :show-file-list="true"
                drag
              >
                <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                <div class="el-upload__text">点击或拖拽上传个人申请书</div>
              </el-upload>
            </el-form-item>
            <el-form-item label="实习接收函">
              <el-upload
                class="onboarding-upload"
                action="#"
                :auto-upload="false"
                :on-change="(file) => handleOnboardingFileChange(file, 'selfPractice', 'acceptanceLetter')"
                :file-list="selfPracticeAcceptanceFileList"
                accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
                :show-file-list="true"
                drag
              >
                <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                <div class="el-upload__text">点击或拖拽上传企业实习接收函</div>
              </el-upload>
            </el-form-item>
            <el-form-item label="盖章实习协议" required>
              <InternshipAgreementUpload ref="selfPracticeAgreementUploader" v-model="selfPracticeAgreementFiles"
                :disabled="onboardingSubmitting" :company-key="selfPracticeForm.company" />
            </el-form-item>
          </el-form>
        </div>
      </div>
      <template #footer>
        <template v-if="selfPracticeApplicationLoading">
          <el-button disabled>关闭</el-button>
          <el-button type="primary" loading>核对中</el-button>
        </template>
        <template v-else-if="selfPracticeApplicationError">
          <el-button @click="showSelfPracticeDialog = false">关闭</el-button>
          <el-button type="primary" :loading="selfPracticeApplicationLoading" @click="loadSelfPracticeApplication">重新加载</el-button>
        </template>
        <template v-else-if="selfPracticeApplicationView === 'result'">
          <el-button @click="showSelfPracticeDialog = false">关闭</el-button>
          <el-button @click="selfPracticeAgreementVisible = true">实习协议</el-button>
          <el-button
            v-if="selfPracticeApplication?.status === 'pending'"
            type="danger"
            plain
            :loading="withdrawingSelfPracticeApplication"
            @click="withdrawSelfPracticeApplication"
          >撤回申请</el-button>
          <el-button
            v-else-if="selfPracticeApplication?.status === 'approved'"
            type="primary"
            @click="startNewSelfPracticeApplication"
          >再次申请</el-button>
        </template>
        <template v-else>
          <el-button @click="showSelfPracticeDialog = false">取消</el-button>
          <el-button
            type="primary"
            :disabled="!matchedCooperativeCompany && similarCooperativeCompanies.length > 0"
            :loading="onboardingSubmitting"
            @click="submitOnboardingApplication('selfPractice')"
          >{{ selfPracticeApplication ? '重新提交申请' : '提交申请' }}</el-button>
        </template>
      </template>    </el-dialog>

    <!-- 考研延迟申请弹窗 -->
    <InternshipAgreementDialog v-if="selfPracticeApplication?.id" v-model="selfPracticeAgreementVisible"
      source-type="SELF_PRACTICE" :source-id="Number(selfPracticeApplication.id)" />
    <el-dialog
      v-model="showDelayDialog"
      width="500px"
      class="onboarding-dialog"
      append-to-body
      lock-scroll
      modal-class="global-modal"
      :title="''"
    >
      <div class="dialog-content">
        <div class="header-section">
          <h1 class="header-title">考研延迟申请</h1>
        </div>

        <div v-if="delayApplicationLoading" class="delay-state-panel">
          <el-icon class="delay-state-icon"><Clock /></el-icon>
          <div class="delay-state-title">正在核对申请状态</div>
          <div class="delay-state-detail">请稍候，避免重复提交。</div>
        </div>

        <div v-else-if="delayApplicationError" class="delay-state-panel">
          <el-icon class="delay-state-icon warning"><Warning /></el-icon>
          <div class="delay-state-title">申请状态加载失败</div>
          <div class="delay-state-detail">{{ delayApplicationError }}</div>
        </div>

        <div v-else-if="delayApplicationView === 'result'" class="delay-state-panel">
          <el-icon class="delay-state-icon" :class="{ success: delayApplication?.status === 'approved' }">
            <CircleCheck v-if="delayApplication?.status === 'approved'" />
            <Clock v-else />
          </el-icon>
          <div class="delay-state-title">{{ delayApplicationStatusText[delayApplication?.status] || '等待教师审核' }}</div>
          <div class="delay-state-detail">
            {{ delayApplication?.status === 'approved' ? '申请已通过，请按延期安排继续办理。' : '教师尚未进行通过或驳回操作，可撤回后重新提交。' }}
          </div>
          <div class="delay-state-meta">
            <span>提交时间：{{ delayApplication?.applyTime || delayApplication?.createTime || '-' }}</span>
            <span v-if="delayApplication?.reviewTime">审核时间：{{ delayApplication.reviewTime }}</span>
            <span v-if="delayApplication?.materials?.delayPlan">考研计划：已上传</span>
          </div>
        </div>

        <div v-else class="onboarding-form">
          <el-alert
            v-if="delayApplication?.status === 'rejected'"
            class="delay-state-alert"
            type="error"
            :closable="false"
            show-icon
          >
            <template #title>教师已驳回</template>
            驳回理由：{{ delayApplication?.rejectReason || '教师未填写理由' }}
          </el-alert>
          <el-alert
            v-else-if="delayApplication?.status === 'withdrawn'"
            class="delay-state-alert"
            type="info"
            :closable="false"
            show-icon
          >
            <template #title>原申请已撤回</template>
            可以修改申请理由和考研计划后重新提交。
          </el-alert>

          <el-form label-width="85px">
            <el-form-item label="申请理由" required>
              <el-input v-model="delayForm.reason" type="textarea" placeholder="请简要说明考研延迟原因" :rows="3" clearable />
            </el-form-item>
            <el-form-item label="考研计划">
              <el-upload
                class="onboarding-upload"
                action="#"
                :auto-upload="false"
                :on-change="(file) => handleOnboardingFileChange(file, 'delay')"
                :file-list="delayFileList"
                accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
                :show-file-list="true"
                drag
              >
                <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                <div class="el-upload__text">点击或拖拽上传考研计划</div>
              </el-upload>
            </el-form-item>
          </el-form>
        </div>
      </div>
      <template #footer>
        <template v-if="delayApplicationLoading">
          <el-button disabled>关闭</el-button>
          <el-button type="primary" loading>核对中</el-button>
        </template>
        <template v-else-if="delayApplicationError">
          <el-button @click="showDelayDialog = false">关闭</el-button>
          <el-button type="primary" :loading="delayApplicationLoading" @click="loadDelayApplication">重新加载</el-button>
        </template>
        <template v-else-if="delayApplicationView === 'result'">
          <el-button @click="showDelayDialog = false">关闭</el-button>
          <el-button
            v-if="delayApplication?.status === 'pending'"
            type="danger"
            plain
            :loading="withdrawingDelayApplication"
            @click="withdrawDelayApplication"
          >撤回申请</el-button>
        </template>
        <template v-else>
          <el-button @click="showDelayDialog = false">取消</el-button>
          <el-button type="primary" @click="submitOnboardingApplication('delay')" :loading="onboardingSubmitting">{{ delayApplication ? '重新提交申请' : '提交申请' }}</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useFormContext } from '@/agent/formContext'
import { useAuthStore } from '@/store/auth'
import request from '@/utils/request'
import InternshipAgreementUpload from '@/components/student/InternshipAgreementUpload.vue'
import InternshipAgreementDialog from '@/components/student/InternshipAgreementDialog.vue'
import { getAgreementDetail, toAgreementUploadFiles, type AgreementUploadFile } from '@/api/internshipAgreements'
import { getProgressList } from '@/api/studentHome'
import { getAnnouncementsForUser, markAnnouncementRead, type Announcement } from '@/api/announcement'
import StudentAnnouncementMessageItem from '@/components/student/StudentAnnouncementMessageItem.vue'
import StudentAnnouncementDetailDialog from '@/components/student/StudentAnnouncementDetailDialog.vue'
import { isAnnouncementUnread } from '@/utils/studentAnnouncement'
import { groupActivitiesByDate, mergeActivities, normalizeActivity } from '@/utils/recentActivity'
import {
  onStudentRecentActivityChanged,
  offStudentRecentActivityChanged
} from '@/utils/websocket'
import {
  Bell,
  TrendCharts,
  Message,
  ArrowRight,
  Camera,
  Document,
  Clock,
  Search,
  EditPen,
  Timer,
  UploadFilled,
  Briefcase,
  CircleCheck,
  Calendar,
  InfoFilled,
  Warning
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadUserFile } from 'element-plus'
import type { FormFieldDescriptor } from '@/agent/types'
import { canResubmitDelay, delayApplicationStatusText, delayDialogMode, delayFileListFromApplication, delayFormFromApplication, latestDelayApplication } from '@/utils/delayApplication'
import {
  latestStudentApplication,
  studentApplicationDialogMode,
  studentApplicationStatusText
} from '@/utils/studentApplication'
import { checkStudentProfileReadiness, profileIncompleteMessage } from '@/utils/studentProfileReadiness.js'
// 学生菜单项（后端形态）
interface StudentMenuItem {
  path?: string
  visible?: boolean
}
// 首页统计（后端形态）
interface DashboardStats {
  applied?: number
  interviewInvites?: number
  current?: number
  pendingReports?: number
}
// 待办行（后端形态，兼容字段别名）
interface StudentTodoRow {
  id?: string | number
  key?: string
  title?: string
  desc?: string
  description?: string
  priority?: string | number
  route?: string
  type?: string
  interviewId?: string | number
  unread?: boolean
}
// 自主实习/延迟申请载荷
interface OnboardingApplication {
  agreementFileIds?: number[]
  applicationType: string
  studentName: string
  studentUserId: string
  grade: string
  className: string
  phone: string
  reason: string
  status: string
  company?: string
  positionName?: string
  companyCity?: string
  hrManagerName?: string
  hrManagerPhone?: string
  materials?: Record<string, string>
}
interface StudentApplicationRecord extends OnboardingApplication {
  id?: number
  rejectReason?: string
  reviewerId?: number
  reviewTime?: string
  applyTime?: string
  createTime?: string
  updateTime?: string
}
// 学生简档行（个人中心形态）
interface StudentProfileRow {
  avatar?: string
  name?: string
  studentId?: string | number
  phone?: string
  email?: string
}

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// 从 auth store 获取学生 ID
const getStudentId = () => {
  return authStore.user?.studentId || authStore.user?.id || ''
}

const userName = computed(() => authStore.user?.name || authStore.user?.username || '同学')
const avatarUrl = ref('')
const avatarInput = ref(null)

const stats = ref({
  applied: 0,
  interviewInvites: 0,
  current: 0,
  pendingReports: 0
})

// 学生端菜单配置（用于控制心得卡片与待办的显隐）
const menuConfig = ref<StudentMenuItem[]>([])
const isReflectionMenuVisible = computed(() => {
  const item = menuConfig.value.find(m => m.path === '/student/internships')
  // fail-closed：menuConfig 未加载完时不显示心得卡片，避免加载瞬间闪现后再隐藏
  return item ? item.visible !== false : false
})

const fetchStudentMenuConfig = async () => {
  try {
    const response = await request.get('/student/home/menu-config')
    if (response && response.code === 200 && response.data) {
      // data 为未知形态（运行时为 JSON 字符串或数组），收窄后使用
      const menuRaw: unknown = response.data
      const parsed: unknown = typeof menuRaw === 'string' ? JSON.parse(menuRaw) : menuRaw
      if (Array.isArray(parsed)) menuConfig.value = parsed as StudentMenuItem[]
    }
  } catch (e) {
    console.error('获取学生菜单配置失败:', e)
  }
}

const fetchDashboardStats = async () => {
  try {
    const studentId = getStudentId()
    console.log('开始获取统计数据，studentId:', studentId)
    console.log('authStore.user:', authStore.user)
    console.log('请求URL:', `/student/home/stats`)

    const requestConfig = studentId ? { params: { studentId } } : {}
    const response = await request.get(`/student/home/stats`, requestConfig)

    console.log('API响应原始数据:', JSON.stringify(response))

    // response 是 axios response.data，即 { code: 200, data: {...} }；data 缺失时回退 response 本体（保持原语义）
    if (response && response.code === 200) {
      const statsRaw: unknown = response.data ? response.data : response
      const apiData = statsRaw as DashboardStats
      stats.value = {
        applied: apiData.applied ?? 0,
        interviewInvites: apiData.interviewInvites ?? 0,
        current: apiData.current ?? 0,
        pendingReports: apiData.pendingReports ?? 0
      }
      console.log('更新后的统计数据:', stats.value)
    } else if (response && response.code !== 200) {
      console.error('API返回错误:', response.message)
    } else {
      console.error('API响应格式异常:', response)
    }
  } catch (error) {
    console.error('获取统计数据失败:', error)
  }
}

const fetchDashboardTodos = async () => {
  try {
    const studentId = getStudentId()
    const requestConfig = studentId ? { params: { studentId } } : {}
    const response = await request.get(`/student/home/todo-list`, requestConfig)

    if (response && response.code === 200) {
      // data 为未知列表形态（运行时为数组），收窄后映射
      const todoRaw: unknown = response.data ? response.data : []
      const todoItems: StudentTodoRow[] = Array.isArray(todoRaw) ? (todoRaw as StudentTodoRow[]) : []
      todoList.value = todoItems.map(item => ({
        id: item.id,
        key: item.key,
        title: item.title,
        desc: item.description,
        priority: item.priority,
        route: getRouteByType(item.type),
        type: item.type,
        interviewId: item.interviewId
      }))
    }
  } catch (error) {
    console.error('获取待办事项失败:', error)
  }
}

const fetchDashboardProgress = async ({ cursor = null, mode = 'replace' } = {}) => {
  const isLoadMore = mode === 'append'
  const isRefresh = mode === 'refresh'
  const hadMultiplePages = progressList.value.length > 10
  const scrollElement = progressScrollContainer.value
  const previousScrollHeight = isRefresh ? (scrollElement?.scrollHeight || 0) : 0
  const previousScrollTop = isRefresh ? (scrollElement?.scrollTop || 0) : 0

  if (isLoadMore) {
    progressLoadingMore.value = true
    progressLoadMoreError.value = false
  } else if (!isRefresh && progressList.value.length === 0) {
    progressLoading.value = true
    progressInitialError.value = false
  }

  try {
    // limit/cursor 均为后端接受的查询参数形态（断言为精确参数类型，运行时同一对象；
    // 保持 `const params = { limit: 10 }` 字面量以兼容源码形态测试）
    const params = { limit: 10 } as { limit: number; cursor?: string | null }
    if (cursor) params.cursor = cursor
    const payload = await getProgressList(params)
    // 数组分支仅用于旧后端过渡，新契约始终返回分页 envelope。
    const pageItems = Array.isArray(payload) ? payload : (Array.isArray(payload.items) ? payload.items : [])
    const normalizedItems = pageItems.map(normalizeActivity)

    if (mode === 'replace') {
      progressList.value = mergeActivities([], normalizedItems)
    } else {
      progressList.value = mergeActivities(progressList.value, normalizedItems)
    }

    if (!isRefresh || !hadMultiplePages) {
      progressNextCursor.value = Array.isArray(payload) ? null : (payload.nextCursor || null)
      progressHasMore.value = Array.isArray(payload) ? false : Boolean(payload.hasMore)
    }

    progressInitialError.value = false
    progressLoadMoreError.value = false

    if (isRefresh && scrollElement && previousScrollHeight > 0) {
      await nextTick()
      const heightDelta = scrollElement.scrollHeight - previousScrollHeight
      scrollElement.scrollTop = previousScrollTop + Math.max(0, heightDelta)
    }
  } catch (error) {
    console.error('获取最近动态失败:', error)
    if (isLoadMore) {
      progressLoadMoreError.value = true
    } else if (progressList.value.length === 0) {
      progressInitialError.value = true
    }
  } finally {
    progressLoading.value = false
    progressLoadingMore.value = false
  }
}

const fetchDashboardNotifications = async () => {
  try {
    const majorId = String(authStore.user?.majorId || '')
    const userId = authStore.user?.id ? String(authStore.user.id) : undefined
    const response = await getAnnouncementsForUser('STUDENT', majorId, userId)
    if (response && response.code === 200) {
      notificationList.value = Array.isArray(response.data) ? response.data : []
    }
  } catch (error) {
    console.error('获取通知失败:', error)
  }
}
const getRouteByType = (type) => {
  const routes = {
    'interview': '/student/interviews',
    'application': '/student/applications',
    'report': '/student/internships',
    'reflection': '/student/internship-reflection/submit',
    'other': '/student/home'
  }
  return routes[type] || '/student/home'
}

const formatTime = (dateTime: string | number | Date) => {
  const date = new Date(dateTime)
  const now = new Date()
  // getTime 相减与原 Date 相减运行时等价
  const diff = now.getTime() - date.getTime()
  
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  
  if (minutes < 60) {
    return `${minutes}分钟前` // 修复：原代码是小时前，这里改为分钟前更合理
  } else if (hours < 24) {
    return `${hours}小时前`
  } else if (days < 7) {
    return `${days}天前`
  } else {
    const month = date.getMonth() + 1
    const day = date.getDate()
    return `${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  }
}

const currentDate = computed(() => {
  const now = new Date()
  const options: Intl.DateTimeFormatOptions = { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }
  return now.toLocaleDateString('zh-CN', options)
})

const todoList = ref<StudentTodoRow[]>([])

// 心得菜单隐藏时，前端再过滤一次心得待办，防止 API 时序导致 UI 闪烁
const visibleTodoList = computed(() => {
  if (isReflectionMenuVisible.value) return todoList.value
  return todoList.value.filter(item => item.key !== 'REFLECTION_SUBMISSION_REQUIRED')
})

const progressList = ref([])
const progressLoading = ref(true)
const progressLoadingMore = ref(false)
const progressInitialError = ref(false)
const progressLoadMoreError = ref(false)
const progressNextCursor = ref(null)
const progressHasMore = ref(false)
const progressScrollContainer = ref(null)

const notificationList = ref<Announcement[]>([])

const recentNotificationList = computed(() => notificationList.value.slice(0, 5))

const unreadCount = computed(() => {
  return notificationList.value.filter(isAnnouncementUnread).length
})

const groupedProgress = computed(() => groupActivitiesByDate(progressList.value))

const activityIcons = {
  recruitment: Briefcase,
  confirmation: CircleCheck,
  application: Document,
  reflection: EditPen,
  internship: Calendar,
  lifecycle: Calendar,
  unknown: InfoFilled
}

const getActivityIcon = (category) => activityIcons[category] || InfoFilled
const shouldShowActivityStage = (item) => (
  item.stageNo && !new RegExp(`第\\s*${item.stageNo}\\s*阶段`).test(item.description || '')
)

const retryProgress = () => fetchDashboardProgress({ mode: 'replace' })

const loadMoreProgress = () => {
  if (!progressHasMore.value || progressLoadingMore.value || !progressNextCursor.value) return
  fetchDashboardProgress({ cursor: progressNextCursor.value, mode: 'append' })
}

const handleTodoClick = async (item) => {
  if (item.type === 'interview' && item.interviewId) {
    // 对于面试相关待办，导航到面试页面并可以传递面试ID参数
    router.push({
      path: item.route,
      query: { interviewId: item.interviewId }
    })
  } else {
    router.push(item.route || '/student/home')
  }

  try {
    // TODO: 实现待办完成 API
    console.log('标记待办为已完成:', item.id)
  } catch (error) {
    console.error('标记待办为已完成失败:', error)
  }
}

const handleNotificationClick = async (item: Announcement) => {
  selectedNotification.value = item
  showNotificationDialog.value = true

  if (isAnnouncementUnread(item)) {
    try {
      await markAnnouncementRead(Number(item.id))
      const idx = notificationList.value.findIndex(announcement => announcement.id === item.id)
      if (idx !== -1) {
        const updated = {
          ...notificationList.value[idx],
          isRead: true,
          readCount: (notificationList.value[idx].readCount || 0) + 1
        }
        notificationList.value[idx] = updated
        selectedNotification.value = updated
      }
    } catch (error) {
      console.error('标记已读失败:', error)
    }
  }
}
const navigateToApplications = () => {
  router.push('/student/applications')
}

const navigateToInterviews = () => {
  router.push('/student/interviews')
}

const navigateToConfirmationForm = () => {
  router.push('/student/internship-confirmation-form')
}

const navigateToInternships = () => {
  router.push('/student/internships')
}

const changeAvatar = () => {
  if (avatarInput.value) {
    avatarInput.value.click()
  }
}

const handleAvatarChange = async (event) => {
  const file = event.target.files[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    return
  }

  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 5MB')
    return
  }

  // 先本地预览（readAsDataURL 结果恒为字符串，收窄后使用）
  const reader = new FileReader()
  reader.onload = (e) => {
    const previewResult: unknown = e.target ? e.target.result : null
    if (typeof previewResult === 'string') avatarUrl.value = previewResult
  }
  reader.readAsDataURL(file)

  // 上传到 OSS
  try {
    const formData = new FormData()
    formData.append('file', file)
    const response = await request.post('/student/profile/upload/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (response.code === 200) {
      // 上传返回的 data 为 { url } 对象，收窄后使用（缺失时清空，与原 undefined 赋值渲染一致）
      const avatarRaw: unknown = response.data
      const avatarPick: unknown =
        avatarRaw !== null && typeof avatarRaw === 'object' ? (avatarRaw as { url?: unknown }).url : undefined
      avatarUrl.value = typeof avatarPick === 'string' ? avatarPick : ''
      ElMessage.success('头像上传成功')
    } else {
      ElMessage.error(response.message || '头像上传失败')
    }
  } catch (error) {
    console.error('头像上传失败:', error)
    ElMessage.error('头像上传失败，请稍后重试')
  }
}

const goToProfile = () => {
  // 导航到个人中心，并在返回时重新检查个人资料状态
  router.push({
    path: '/student/profile',
    query: { returnTo: '/student/home' }
  })
}

// 申请岗位/自主实习审核/考研延迟申请前置校验：姓名、学号、电话、邮箱需全部完善
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
    goToProfile()
  } catch {
    // 学生取消时停留在当前页，不进入申请流程
  }
  return false
}

const goToMessageCenter = () => {
  // 导航到个人中心的消息中心
  router.push({
    path: '/student/profile',
    query: { action: 'messageCenter' }
  })
}

// 监听路由变化，当从个人中心返回时重新检查个人资料状态
watch(() => route.path, (newPath, oldPath) => {
  if (newPath === '/student/home' && oldPath === '/student/profile') {
    // 从消息中心返回时重新读取服务端已读状态
    void Promise.all([fetchProfileStatus(), fetchDashboardNotifications()])
  }
})

const showNotificationDialog = ref(false)
const selectedNotification = ref<Announcement | null>(null)
const hasProfile = ref(false)

// 弹窗和表单状态
const showSelfPracticeDialog = ref(false)
const showDelayDialog = ref(false)
const onboardingSubmitting = ref(false)
const matchedCooperativeCompany = ref(null)
// 名称相近但未完全匹配的系统企业：需从下拉候选选择标准名称后才允许提交
const similarCooperativeCompanies = ref<any[]>([])
const selfPracticeApplication = ref<StudentApplicationRecord | null>(null)
const selfPracticeApplicationLoading = ref(false)
const selfPracticeApplicationError = ref('')
const withdrawingSelfPracticeApplication = ref(false)
const selfPracticeApplicationView = computed(() => studentApplicationDialogMode(selfPracticeApplication.value?.status))
const selfPracticeAgreementFiles = ref<AgreementUploadFile[]>([])
const selfPracticeAgreementUploader = ref<InstanceType<typeof InternshipAgreementUpload> | null>(null)
const selfPracticeAgreementVisible = ref(false)
const selfPracticeForm = ref({
  agreementFileIds: [] as number[],
  company: '',
  positionName: '',
  companyCity: '',
  hrManagerName: '',
  hrManagerPhone: '',
  reason: '',
  applicationLetterUrl: '',
  acceptanceLetterUrl: ''
})
const delayForm = ref({ reason: '', materialUrl: '' })
const selfPracticeFileList = ref<UploadUserFile[]>([])
watch(selfPracticeAgreementFiles, files => {
  selfPracticeForm.value.agreementFileIds = files.filter(file => file.status === 'success').map(file => file.uploadId)
}, { flush: 'sync' })
const selfPracticeAcceptanceFileList = ref<UploadUserFile[]>([])
const delayFileList = ref([])
const delayApplication = ref(null)
const delayApplicationLoading = ref(false)
const delayApplicationError = ref('')
const withdrawingDelayApplication = ref(false)
const delayApplicationView = computed(() => delayDialogMode(delayApplication.value?.status))

// ===== 智能填表：向 AI 侧栏声明表单结构（不含用户已填值；上传字段只说明需手动上传） =====
const formContext = useFormContext()
const selfPracticeFields: FormFieldDescriptor[] = [
  { prop: 'agreementFileIds', label: '盖章实习协议', type: 'upload', required: true },
  { prop: 'company', label: '实习单位', type: 'input', required: true, patternHint: '系统外企业可直接填写；系统内已登记的校企合作企业也可申请，名称相近时需从下拉候选选择标准名称' },
  { prop: 'positionName', label: '实习岗位', type: 'input', required: true },
  { prop: 'companyCity', label: '单位所在城市', type: 'input', required: true, patternHint: '示例：广东省广州市' },
  { prop: 'hrManagerName', label: '人事经理姓名', type: 'input', required: true },
  { prop: 'hrManagerPhone', label: '人事经理联系电话', type: 'input', required: true, patternHint: '手机或固定电话' },
  { prop: 'reason', label: '申请理由', type: 'textarea', required: true },
  { prop: 'applicationLetterUrl', label: '个人申请书', type: 'upload', required: true },
  { prop: 'acceptanceLetterUrl', label: '实习接收函', type: 'upload', required: true }
]
const delayFields: FormFieldDescriptor[] = [
  { prop: 'reason', label: '申请理由', type: 'textarea', required: true },
  { prop: 'materialUrl', label: '考研计划', type: 'upload', required: true }
]
// 弹窗关闭时注销，防止侧栏继续把内容填进已关闭的表单
watch(showSelfPracticeDialog, open => { if (!open) formContext.unregister('home:self-practice') })
watch(showDelayDialog, open => { if (!open) formContext.unregister('home:delay') })
onUnmounted(() => { formContext.unregister('home:self-practice'); formContext.unregister('home:delay') })

const goToJobs = () => {
  router.push('/student/jobs')
}

const normalizeCompanyName = (value) => String(value || '').trim().toLocaleLowerCase()

const handleCompanyNameInput = (value) => {
  const normalized = normalizeCompanyName(value)
  const typedMatch = similarCooperativeCompanies.value.find(
    (item) => normalizeCompanyName(item.companyName) === normalized
  )
  if (typedMatch) {
    // 学生直接输出了某个候选的完整名称，视为精确匹配
    matchedCooperativeCompany.value = typedMatch
    similarCooperativeCompanies.value = []
    return
  }
  if (normalizeCompanyName(matchedCooperativeCompany.value?.companyName) !== normalized) {
    matchedCooperativeCompany.value = null
  }
}

const handleCooperativeCompanySelect = (item) => {
  selfPracticeForm.value.company = item.companyName
  matchedCooperativeCompany.value = item
  similarCooperativeCompanies.value = []
}

const queryCooperativeCompanies = async (queryString, callback) => {
  const keyword = String(queryString || '').trim()
  if (keyword.length < 2) {
    matchedCooperativeCompany.value = null
    similarCooperativeCompanies.value = []
    callback([])
    return
  }

  try {
    const response = await request.get('/student/applications/company-matches', {
      params: { keyword }
    })
    const suggestions = (Array.isArray(response.data) ? response.data : []).map((item) => ({
      ...item,
      value: item.companyName
    }))

    if (normalizeCompanyName(selfPracticeForm.value.company) === normalizeCompanyName(keyword)) {
      const exactMatch = suggestions.find(
        (item) => normalizeCompanyName(item.companyName) === normalizeCompanyName(keyword)
      ) || null
      matchedCooperativeCompany.value = exactMatch
      similarCooperativeCompanies.value = exactMatch ? [] : suggestions
    }
    callback(suggestions)
  } catch (error) {
    console.warn('查询系统企业库失败:', error)
    if (normalizeCompanyName(selfPracticeForm.value.company) === normalizeCompanyName(keyword)) {
      matchedCooperativeCompany.value = null
      similarCooperativeCompanies.value = []
    }
    callback([])
  }
}

const resetSelfPracticeForm = (application?: StudentApplicationRecord | null) => {
  selfPracticeAgreementUploader.value?.reset()
  selfPracticeAgreementFiles.value = []
  selfPracticeForm.value = {
    agreementFileIds: [],
    company: application?.company || '',
    positionName: application?.positionName || '',
    companyCity: application?.companyCity || '',
    hrManagerName: application?.hrManagerName || '',
    hrManagerPhone: application?.hrManagerPhone || '',
    reason: application?.reason || '',
    applicationLetterUrl: '',
    acceptanceLetterUrl: ''
  }
  selfPracticeFileList.value = application?.materials?.['个人申请书']
    ? [{ name: '个人申请书（已上传，可替换）', status: 'success' }]
    : []
  selfPracticeAcceptanceFileList.value = application?.materials?.['实习接收函']
    ? [{ name: '实习接收函（已上传，可替换）', status: 'success' }]
    : []
  matchedCooperativeCompany.value = null
  similarCooperativeCompanies.value = []
}

const registerSelfPracticeForm = () => {
  if (selfPracticeApplicationView.value !== 'form') {
    formContext.unregister('home:self-practice')
    return
  }
  formContext.register({
    formId: 'home:self-practice',
    title: '自主实习申请',
    fields: selfPracticeFields,
    getModel: () => selfPracticeForm.value,
    getValidationSummary: () => ({ missingFields: [], validationErrors: similarCooperativeCompanies.value.length > 0 ? ['系统存在名称相近的校企合作单位，请从下拉候选中选择系统登记的标准企业名称'] : [] })
  })
}

const loadSelfPracticeApplication = async () => {
  selfPracticeApplicationLoading.value = true
  selfPracticeApplicationError.value = ''
  try {
    const response = await request.get('/student/applications')
    if (response.code !== 200) {
      throw new Error(response.message || '加载申请状态失败')
    }
    selfPracticeApplication.value = latestStudentApplication(response.data, 'selfPractice')
    if (selfPracticeApplicationView.value === 'form' && selfPracticeApplication.value) {
      resetSelfPracticeForm(selfPracticeApplication.value)
      const agreementResponse: any = await getAgreementDetail('student', 'SELF_PRACTICE', Number(selfPracticeApplication.value.id))
      if (agreementResponse.code !== 200) throw new Error(agreementResponse.message || '协议加载失败')
      selfPracticeAgreementFiles.value = toAgreementUploadFiles(agreementResponse.data?.versions[0]?.files || [])
    }
    registerSelfPracticeForm()
  } catch (error) {
    console.error('加载自主实习申请状态失败:', error)
    selfPracticeApplicationError.value = error?.response?.data?.message || error?.message || '加载申请状态失败'
    formContext.unregister('home:self-practice')
  } finally {
    selfPracticeApplicationLoading.value = false
  }
}

const openSelfPracticeDialog = async () => {
  if (!(await ensureProfileReady('提交自主实习审核'))) return
  selfPracticeApplication.value = null
  resetSelfPracticeForm()
  showSelfPracticeDialog.value = true
  formContext.unregister('home:self-practice')
  await loadSelfPracticeApplication()
}
const startNewSelfPracticeApplication = () => {
  if (selfPracticeApplication.value?.status !== 'approved') return
  selfPracticeApplication.value = null
  resetSelfPracticeForm()
  registerSelfPracticeForm()
}
const resetDelayForm = (application?: StudentApplicationRecord | null) => {
  delayForm.value = delayFormFromApplication(application)
  delayFileList.value = delayFileListFromApplication(application)
}

const registerDelayForm = () => {
  if (delayApplicationView.value !== 'form') {
    formContext.unregister('home:delay')
    return
  }
  formContext.register({
    formId: 'home:delay',
    title: '考研延迟申请',
    fields: delayFields,
    getModel: () => delayForm.value,
    getValidationSummary: () => ({ missingFields: [...(!delayForm.value.reason?.trim() ? ['申请理由'] : []), ...(!delayForm.value.materialUrl && !delayApplication.value?.materials?.delayPlan ? ['考研计划'] : [])], validationErrors: [] })
  })
}

const loadDelayApplication = async () => {
  delayApplicationLoading.value = true
  delayApplicationError.value = ''
  try {
    const response = await request.get('/student/applications')
    if (response.code !== 200) {
      throw new Error(response.message || '加载申请状态失败')
    }
    delayApplication.value = latestDelayApplication(response.data)
    if (delayApplicationView.value === 'form' && delayApplication.value) {
      resetDelayForm(delayApplication.value)
    }
    registerDelayForm()
  } catch (error) {
    console.error('加载考研延迟申请状态失败:', error)
    delayApplicationError.value = error?.response?.data?.message || error?.message || '加载申请状态失败'
    formContext.unregister('home:delay')
  } finally {
    delayApplicationLoading.value = false
  }
}

const openDelayDialog = async () => {
  if (!(await ensureProfileReady('提交考研延迟申请'))) return
  resetDelayForm()
  delayApplication.value = null
  showDelayDialog.value = true
  await loadDelayApplication()
}

const withdrawDelayApplication = async () => {
  if (!delayApplication.value?.id || delayApplication.value?.status !== 'pending') return
  try {
    await ElMessageBox.confirm('撤回后教师将无法继续审核原申请，你可以重新填写并提交。', '撤回考研延迟申请', {
      type: 'warning',
      confirmButtonText: '确认撤回',
      cancelButtonText: '继续保留'
    })
  } catch {
    return
  }
  withdrawingDelayApplication.value = true
  try {
    const response = await request.post(`/student/applications/${delayApplication.value.id}/withdraw`)
    if (response.code !== 200) {
      throw new Error(response.message || '撤回申请失败')
    }
    ElMessage.success(response.message || '申请已撤回，可重新提交')
    await loadDelayApplication()
  } catch (error) {
    console.error('撤回考研延迟申请失败:', error)
    ElMessage.error(error?.response?.data?.message || error?.message || '撤回申请失败')
  } finally {
    withdrawingDelayApplication.value = false
  }
}
const withdrawSelfPracticeApplication = async () => {
  if (!selfPracticeApplication.value?.id || selfPracticeApplication.value?.status !== 'pending') return
  try {
    await ElMessageBox.confirm('撤回后教师将无法继续审核原申请，你可以重新填写并提交。', '撤回自主实习申请', {
      type: 'warning',
      confirmButtonText: '确认撤回',
      cancelButtonText: '继续保留'
    })
  } catch {
    return
  }
  withdrawingSelfPracticeApplication.value = true
  try {
    const response = await request.post(`/student/applications/${selfPracticeApplication.value.id}/withdraw`)
    if (response.code !== 200) {
      throw new Error(response.message || '撤回申请失败')
    }
    ElMessage.success(response.message || '申请已撤回，可重新提交')
    await loadSelfPracticeApplication()
  } catch (error) {
    console.error('撤回自主实习申请失败:', error)
    ElMessage.error(error?.response?.data?.message || error?.message || '撤回申请失败')
  } finally {
    withdrawingSelfPracticeApplication.value = false
  }
}
const handleOnboardingFileChange = async (file, type, subType?: string) => {
  try {
    const formData = new FormData()
    formData.append('file', file.raw)
    const response = await request.post('/upload/file', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    // 上传返回的 data 为 { url } 对象，收窄后使用（保持原真值语义）
    const uploadRaw: unknown = response.data
    const uploadObj: { url?: unknown } | null =
      uploadRaw !== null && typeof uploadRaw === 'object' ? (uploadRaw as { url?: unknown }) : null
    const uploadedUrl: unknown = uploadObj?.url
    if (response.code === 200 && uploadObj && typeof uploadedUrl === 'string' && uploadedUrl) {
      if (type === 'selfPractice') {
        if (subType === 'applicationLetter') {
          selfPracticeForm.value.applicationLetterUrl = uploadedUrl
        } else if (subType === 'acceptanceLetter') {
          selfPracticeForm.value.acceptanceLetterUrl = uploadedUrl
        }
      } else {
        delayForm.value.materialUrl = uploadedUrl
      }
      ElMessage.success('文件上传成功')
    } else {
      ElMessage.error(response.message || '文件上传失败')
    }
  } catch (error) {
    console.error('文件上传失败:', error)
    ElMessage.error('文件上传失败')
  }
}

// 提交前实时校验系统企业库：
// - 精确命中有效校企合作企业：允许提交，按系统登记标准名称记录
// - 仅相近命中（疑似同一企业名称不一致）：要求从下拉候选中选择标准企业名称
// - 无任何命中：视为系统外企业，按学生填写的名称提交
const ensureSelfPracticeCompanyName = async () => {
  const companyName = selfPracticeForm.value.company.trim()
  if (companyName.length < 2) {
    matchedCooperativeCompany.value = null
    similarCooperativeCompanies.value = []
    return true
  }
  try {
    const response = await request.get('/student/applications/company-matches', {
      params: { keyword: companyName }
    })
    const suggestions = Array.isArray(response.data) ? response.data : []
    const exactMatch = suggestions.find(
      (item) => normalizeCompanyName(item.companyName) === normalizeCompanyName(companyName)
    ) || null
    matchedCooperativeCompany.value = exactMatch
    similarCooperativeCompanies.value = exactMatch ? [] : suggestions
    if (!exactMatch && suggestions.length > 0) {
      ElMessage.warning('系统企业库存在名称相近的校企合作单位，请从下拉候选中选择系统登记的标准企业名称')
      return false
    }
    return true
  } catch (error) {
    console.warn('提交前校验系统企业库失败:', error)
    // 查询失败不阻断提交，后端插入时仍会独立校验
    return true
  }
}

const submitOnboardingApplication = async (type) => {
  if (type === 'selfPractice') {
    if (!selfPracticeForm.value.company.trim()) {
      ElMessage.error('请填写实习单位')
      return
    }
    if (!(await ensureSelfPracticeCompanyName())) {
      return
    }
    if (!selfPracticeForm.value.positionName.trim()) {
      ElMessage.error('请填写实习岗位')
      return
    }
    if (!selfPracticeForm.value.companyCity.trim()) {
      ElMessage.error('请填写单位所在城市')
      return
    }
    if (!selfPracticeForm.value.hrManagerName.trim()) {
      ElMessage.error('请填写人事经理姓名')
      return
    }
    if (!selfPracticeForm.value.hrManagerPhone.trim()) {
      ElMessage.error('请填写人事经理联系电话')
      return
    }
    if (!selfPracticeForm.value.reason.trim()) {
      ElMessage.error('请填写申请理由')
      return
    }
    if (!selfPracticeAgreementUploader.value?.validate()) return
  } else if (type === 'delay') {
    if (!delayForm.value.reason) {
      ElMessage.error('请填写申请理由')
      return
    }
  }

  onboardingSubmitting.value = true
  try {
    const user = authStore.user
    // user 的 grade/class/phone 为未知形态，转字符串后与原 `|| ''` 语义一致
    const applicationData: OnboardingApplication = {
      applicationType: type === 'selfPractice' ? 'selfPractice' : 'delay',
      studentName: user?.name || '',
      studentUserId: String(user?.studentId || user?.id || ''),
      grade: user?.grade ? String(user.grade) : '',
      className: user?.class ? String(user.class) : '',
      phone: user?.phone ? String(user.phone) : '',
      reason: type === 'selfPractice' ? selfPracticeForm.value.reason : delayForm.value.reason,
      status: 'pending'
    }

    if (type === 'selfPractice') {
      applicationData.agreementFileIds = selfPracticeAgreementFiles.value.map(file => file.uploadId)
      applicationData.company = selfPracticeForm.value.company.trim()
      applicationData.positionName = selfPracticeForm.value.positionName.trim()
      applicationData.companyCity = selfPracticeForm.value.companyCity.trim()
      applicationData.hrManagerName = selfPracticeForm.value.hrManagerName.trim()
      applicationData.hrManagerPhone = selfPracticeForm.value.hrManagerPhone.trim()
      const materials: Record<string, string> = {}
      if (selfPracticeForm.value.applicationLetterUrl) materials['个人申请书'] = selfPracticeForm.value.applicationLetterUrl
      if (selfPracticeForm.value.acceptanceLetterUrl) materials['实习接收函'] = selfPracticeForm.value.acceptanceLetterUrl
      applicationData.materials = materials
    } else if (type === 'delay') {
      applicationData.materials = delayForm.value.materialUrl ? { delayPlan: delayForm.value.materialUrl } : {}
    }

    const operation = formContext.beginFormOperation(type === 'selfPractice' ? 'home:self-practice' : 'home:delay')
    const currentStudentApplication = type === 'selfPractice' ? selfPracticeApplication.value : delayApplication.value
    const studentApplicationResubmitId = currentStudentApplication
      && (type === 'delay' ? canResubmitDelay(currentStudentApplication.status) : ['rejected', 'withdrawn'].includes(currentStudentApplication.status))
      ? currentStudentApplication.id
      : null
    const response = studentApplicationResubmitId
      ? await request.put(`/student/applications/${studentApplicationResubmitId}/resubmit`, applicationData)
      : await request.post('/student/applications', applicationData)
    if (response.code === 200) {
      if (type === 'selfPractice') selfPracticeAgreementUploader.value?.markCommitted()
      operation.complete({ status: 'submitted', label: '申请已提交，等待审核', url: '/student/applications' })
      ElMessage.success(response.message || (studentApplicationResubmitId ? '申请已重新提交' : '申请提交成功'))
      scheduleProgressRefresh()
      if (type === 'selfPractice') {
        await loadSelfPracticeApplication()
      } else {
        await loadDelayApplication()
      }
    } else {
      ElMessage.error(response.message || '提交申请失败')
    }  } catch (error) {
    console.error('提交申请失败:', error)
    ElMessage.error(error?.response?.data?.message || error?.message || '提交申请失败')
  } finally {
    onboardingSubmitting.value = false
  }
}

const fetchProfileStatus = async () => {
  try {
    const studentId = getStudentId()
    const requestConfig = studentId ? { params: { studentId } } : {}
    const response = await request.get(`/student/profile`, requestConfig)
    if (response && response.code === 200 && response.data) {
      // profile 为未知形态（运行时为学生简档对象），收窄后使用
      const profile = response.data as StudentProfileRow
      // 设置头像（无头像时清空，避免显示他人缓存的头像）
      avatarUrl.value = profile.avatar || ''
      // 检查是否有基本个人信息
      if (profile.name && profile.studentId && profile.phone && profile.email) {
        hasProfile.value = true
      }
    }
  } catch (error) {
    console.error('获取个人资料状态失败:', error)
    // 模拟数据：默认未完善个人资料
    hasProfile.value = false
  }
}

let progressRefreshTimer = null

const refreshProgressFirstPage = () => {
  if (progressLoading.value || progressLoadingMore.value) return
  fetchDashboardProgress({ mode: 'refresh' })
}

const scheduleProgressRefresh = () => {
  if (progressRefreshTimer) window.clearTimeout(progressRefreshTimer)
  progressRefreshTimer = window.setTimeout(() => {
    progressRefreshTimer = null
    refreshProgressFirstPage()
  }, 200)
}

// Only open an existing form. Query navigation never submits or replaces business state.
watch(() => route.query.journeyAction, async action => {
  if (!['selfPractice', 'delay'].includes(String(action)) || authStore.user?.originalAdminUsername) return
  if (action === 'selfPractice' && !showSelfPracticeDialog.value && !showDelayDialog.value) openSelfPracticeDialog()
  if (action === 'delay' && !showDelayDialog.value && !showSelfPracticeDialog.value) openDelayDialog()
  const query = { ...route.query }; delete query.journeyAction
  await router.replace({ path: route.path, query })
}, { immediate: true })

const handleRecentActivityChanged = () => scheduleProgressRefresh()
const handleWindowFocus = () => scheduleProgressRefresh()

onMounted(async () => {
  onStudentRecentActivityChanged(handleRecentActivityChanged)
  window.addEventListener('focus', handleWindowFocus)

  try {
    await Promise.all([
      fetchDashboardStats(),
      fetchDashboardTodos(),
      fetchDashboardProgress(),
      fetchDashboardNotifications(),
      fetchProfileStatus(),
      fetchStudentMenuConfig()
    ])
  } catch (error) {
    console.error('加载数据失败:', error)
  }
})

onUnmounted(() => {
  offStudentRecentActivityChanged(handleRecentActivityChanged)
  window.removeEventListener('focus', handleWindowFocus)
  if (progressRefreshTimer) {
    window.clearTimeout(progressRefreshTimer)
    progressRefreshTimer = null
  }
})
</script>

<style scoped>
/* 核心布局：1080p 标准固定设计，小屏出现滚动条 */
.home-container {
  width: 100%;
  min-height: calc(100vh - 64px - 40px);
  display: flex;
  flex-direction: column;
  background: transparent !important;
  padding: 0 !important;
  margin: 0 !important;
  box-sizing: border-box !important;
}

/* 统一卡片和容器样式，与全局布局风格保持一致 */
.welcome-section {
  flex-shrink: 0;
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  border-radius: 12px;
  padding: 24px 32px;
  margin-bottom: 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  position: relative;
  overflow: hidden;
}

.welcome-section::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -10%;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
  border-radius: 50%;
}

.welcome-section::after {
  content: '';
  position: absolute;
  bottom: -30%;
  left: -5%;
  width: 200px;
  height: 200px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.08) 0%, transparent 70%);
  border-radius: 50%;
}

.welcome-content {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
}

.welcome-left {
  display: flex;
  align-items: center;
  gap: 20px;
  flex: 1;
  min-width: 0;
}

/* 右侧路径选择区域 */
.welcome-right {
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  padding: 12px 16px;
  backdrop-filter: blur(6px);
}

.path-selector {
  display: flex;
  gap: 10px;
}

.path-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.3s ease;
  background: rgba(255, 255, 255, 0.92);
  min-width: 140px;
}

.path-card:hover {
  background: #fff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

/* 引导箭头：桌面横排卡不显示，移动端单列列表时展示 */
.path-arrow {
  display: none;
  margin-left: auto;
  font-size: 14px;
  color: #c0c4cc;
}

.path-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.path-icon-blue {
  background: #409eff;
  color: #fff;
}

.path-icon-green {
  background: #67c23a;
  color: #fff;
}

.path-icon-orange {
  background: #e6a23c;
  color: #fff;
}

.path-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.path-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
}

.path-desc {
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
}

.avatar-wrapper {
  position: relative;
  width: 80px;
  height: 80px;
  cursor: pointer;
  transition: transform 0.3s ease;
}

.avatar-wrapper:hover {
  transform: scale(1.05);
}

.avatar-img {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  border: 3px solid rgba(255, 255, 255, 0.3);
  object-fit: cover;
}

.avatar-edit-overlay {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 28px;
  height: 28px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.avatar-wrapper:hover .avatar-edit-overlay {
  opacity: 1;
}

.edit-icon {
  font-size: 14px;
  color: #409eff;
}

.welcome-text h1 {
  font-size: 28px;
  font-weight: 700;
  color: white;
  margin: 0 0 8px 0;
}

.welcome-text p {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
  margin: 0;
}

/* 个人资料提醒样式 */
.profile-reminder {
  margin-top: 12px;
  padding: 8px 14px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  border-left: 3px solid rgba(255, 255, 255, 0.4);
  display: flex;
  align-items: center;
  gap: 12px;
}

.reminder-text {
  font-size: 13px;
  color: white;
  margin: 0;
}

.reminder-btn {
  padding: 5px 14px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 6px;
  background: white;
  color: #409EFF !important;
  border: 1px solid white !important;
  transition: all 0.3s ease;
}

.reminder-btn:hover {
  background: rgba(255, 255, 255, 0.9) !important;
  color: #1e88e5 !important;
}

/* 统计数据网格 */
.stats-grid {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

/* 统计项 */
.stat-item {
  background: white;
  border-radius: 10px;
  padding: 14px 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 12px;
  border-left: 4px solid;
  cursor: pointer;
}

.stat-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

/* 统计项颜色 */
.stat-item.applied {
  border-left-color: #409EFF;
}

.stat-item.interview {
  border-left-color: #67C23A;
}

.stat-item.current {
  border-left-color: #E6A23C;
}

.stat-item.pending {
  border-left-color: #F56C6C;
}

/* 统计图标 */
.stat-icon {
  font-size: 20px;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-item.applied .stat-icon {
  background: #ecf5ff;
  color: #409EFF;
}

.stat-item.interview .stat-icon {
  background: #f0f9eb;
  color: #67C23A;
}

.stat-item.current .stat-icon {
  background: #fdf6ec;
  color: #E6A23C;
}

.stat-item.pending .stat-icon {
  background: #fef0f0;
  color: #F56C6C;
}

/* 统计信息 */
.stat-info {
  flex: 1;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  font-weight: 500;
  margin: 0 0 4px 0;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
}

.stat-item.applied .stat-value {
  color: #409EFF;
}

.stat-item.interview .stat-value {
  color: #67C23A;
}

.stat-item.current .stat-value {
  color: #E6A23C;
}

.stat-item.pending .stat-value {
  color: #F56C6C;
}

/* 响应式布局 */
@media screen and (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(auto-fit, minmax(0, 1fr));
  }
}

@media screen and (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
  
  .stat-item {
    padding: 16px;
  }
  
  .stat-icon {
    width: 40px;
    height: 40px;
    font-size: 20px;
  }
  
  .stat-value {
    font-size: 24px;
  }
}

/* 内容网格 — 固定高度设计，1080p 为标准 */
.content-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.content-card {
  background: white;
  border-radius: 12px;
  padding: 0;
  margin-bottom: 0;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  height: 540px;
}

.content-card .card-list {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.card-footer {
  padding: 12px 20px;
  border-top: 1px solid #f0f0f0;
  text-align: center;
}

.view-more {
  color: #409eff;
  font-size: 14px;
  cursor: pointer;
}

.view-more:hover {
  color: #66b1ff;
}

.card-header {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-icon {
  font-size: 20px;
}

.header-icon.warning {
  color: #e6a23c;
}

.header-icon.info {
  color: #409eff;
}

.header-icon.primary {
  color: #67c23a;
}

.card-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.badge {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
}

.badge.urgent {
  background: #fef3c7;
  color: #d97706;
}

.badge.new {
  background: #fee2e2;
  color: #dc2626;
}

.card-list {
  padding: 0;
  position: relative;
}

/* TransitionGroup 通知项过渡动画 */
.notif-move,
.notif-enter-active,
.notif-leave-active {
  transition: all 0.4s ease;
}

.notif-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}

.notif-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

.notif-leave-active {
  position: absolute;
  width: 100%;
}

.list-item {
  padding: 12px 16px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.list-item:last-child {
  border-bottom: none;
}

.list-item:hover {
  background: #f8fafc;
}

.item-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.item-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.item-dot.high {
  background: #f56c6c;
}

.item-dot.medium {
  background: #e6a23c;
}

.item-dot.low {
  background: #67c23a;
}

.item-content {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.item-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-desc {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-arrow {
  color: #c0c4cc;
  font-size: 16px;
  transition: transform 0.3s ease;
}

.list-item:hover .item-arrow {
  transform: translateX(4px);
  color: #409eff;
}

.item-time {
  font-size: 12px;
  color: #909399;
  min-width: 50px;
  text-align: left;
  margin-right: 8px;
}

/* 最近动态时间线 */
.recent-activity-body {
  padding: 0;
  overflow-x: hidden;
}

.activity-timeline,
.activity-group-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.activity-date-group {
  margin: 0;
  padding: 0 16px;
}

.activity-date-label {
  position: sticky;
  top: 0;
  z-index: 2;
  margin: 0 -16px;
  padding: 10px 18px 8px;
  background: rgba(248, 250, 252, 0.96);
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.4;
  backdrop-filter: blur(6px);
}

.activity-item {
  position: relative;
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 10px;
  padding: 14px 0 4px;
}

.activity-item:not(:last-child)::before {
  content: '';
  position: absolute;
  top: 44px;
  bottom: -14px;
  left: 16px;
  width: 2px;
  background: #e5e7eb;
}

.activity-node {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #eff6ff;
  color: #3b82f6;
  box-shadow: 0 0 0 4px #fff;
}

.activity-node.category-confirmation {
  background: #ecfdf5;
  color: #059669;
}

.activity-node.category-application {
  background: #fff7ed;
  color: #ea580c;
}

.activity-node.category-reflection {
  background: #f5f3ff;
  color: #7c3aed;
}

.activity-node.category-internship,
.activity-node.category-lifecycle {
  background: #eef2ff;
  color: #4f46e5;
}

.activity-node.category-unknown {
  background: #f1f5f9;
  color: #64748b;
}

.activity-content {
  min-width: 0;
  padding: 0 0 10px;
  border-bottom: 1px solid #f1f5f9;
}

.activity-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.activity-title {
  min-width: 0;
  color: #1f2937;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.activity-status {
  flex-shrink: 0;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
  white-space: nowrap;
}

.activity-status.status-pending {
  background: #fff7e6;
  color: #b45309;
}

.activity-status.status-success {
  background: #ecfdf5;
  color: #047857;
}

.activity-status.status-failed {
  background: #fef2f2;
  color: #dc2626;
}

.activity-status.status-withdrawn {
  background: #f3f4f6;
  color: #6b7280;
}

.activity-status.status-info {
  background: #eff6ff;
  color: #2563eb;
}

.activity-description,
.activity-stage {
  margin: 5px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.activity-description {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.activity-stage {
  color: #7c3aed;
}

.activity-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  gap: 10px;
  margin-top: 2px;
}

.activity-meta time {
  flex-shrink: 0;
  color: #94a3b8;
  font-size: 12px;
}

.activity-action {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  min-height: 44px;
  min-width: 44px;
  padding: 0 2px 0 8px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
  text-decoration: none;
  border-radius: 6px;
}

.recent-activity-body :deep(.el-button) {
  min-height: 44px;
}

.activity-action:hover {
  color: #1d4ed8;
  text-decoration: underline;
}

.activity-action:focus-visible {
  outline: 2px solid #409eff;
  outline-offset: 2px;
}

.activity-action .el-icon {
  margin-left: 3px;
}

.activity-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 32px 24px;
  box-sizing: border-box;
  color: #64748b;
  text-align: center;
  gap: 9px;
}

.activity-state > .el-icon {
  margin-bottom: 2px;
  color: #94a3b8;
  font-size: 38px;
}

.activity-state strong {
  color: #334155;
  font-size: 15px;
}

.activity-state span {
  max-width: 280px;
  font-size: 13px;
  line-height: 1.6;
}

.activity-skeleton {
  padding: 18px 16px;
}

.activity-skeleton-item {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 10px;
  padding: 8px 0 18px;
}

.skeleton-node,
.skeleton-line {
  display: block;
  background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 37%, #f1f5f9 63%);
  background-size: 400% 100%;
  animation: activity-skeleton-loading 1.4s ease infinite;
}

.skeleton-node {
  width: 32px;
  height: 32px;
  border-radius: 50%;
}

.skeleton-content {
  min-width: 0;
}

.skeleton-line {
  height: 12px;
  margin-bottom: 10px;
  border-radius: 6px;
}

.skeleton-title {
  width: 72%;
}

.skeleton-description {
  width: 92%;
}

.skeleton-time {
  width: 25%;
}

@keyframes activity-skeleton-loading {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}

.activity-load-more {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 52px;
  padding: 2px 16px 8px;
}

.load-more-error {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #b45309;
  font-size: 12px;
}

.load-more-error button {
  min-height: 44px;
  padding: 0 6px;
  border: 0;
  background: transparent;
  color: #2563eb;
  font: inherit;
  cursor: pointer;
}

.load-more-error button:hover,
.load-more-error button:focus-visible {
  text-decoration: underline;
}

.activity-end {
  color: #94a3b8;
  font-size: 12px;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.empty-notification {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #c0c4cc;
  font-size: 14px;
  gap: 8px;
}

.empty-notification .el-icon {
  font-size: 36px;
}

.list-item.unread {
  background: #f0f9ff;
}

.unread-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #409eff;
  flex-shrink: 0;
}

/* 响应式适配 */
@media screen and (max-width: 1200px) {
  .content-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media screen and (max-width: 768px) {
  .welcome-section {
    padding: 24px 20px;
  }

  .welcome-text h1 {
    font-size: 24px;
  }

  .content-grid {
    grid-template-columns: 1fr;
  }

}

@media screen and (max-width: 480px) {
  .welcome-text h1 {
    font-size: 20px;
  }

  .stat-value {
    font-size: 24px;
  }

  .activity-date-group {
    padding: 0 12px;
  }

  .activity-date-label {
    margin: 0 -12px;
    padding-inline: 14px;
  }

  .activity-item {
    grid-template-columns: 30px minmax(0, 1fr);
    gap: 8px;
  }

  .activity-node {
    width: 28px;
    height: 28px;
  }

  .activity-item:not(:last-child)::before {
    top: 40px;
    left: 13px;
  }

  .activity-heading {
    flex-wrap: wrap;
  }

  .activity-status {
    white-space: normal;
  }

  .activity-meta {
    align-items: flex-start;
  }
}

/* 通知详情弹窗样式 */
.notification-detail {
  padding: 0;
}

.notification-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 2px solid #f0f0f0;
}

.notification-title {
  font-size: 20px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.notification-status {
  padding: 6px 16px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 500;
  background: #f1f5f9;
  color: #64748b;
}

.notification-status.unread {
  background: #fef3c7;
  color: #d97706;
}

.notification-time {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  font-size: 14px;
  color: #64748b;
}

.notification-time .el-icon {
  color: #409EFF;
  font-size: 16px;
}

.notification-content {
  font-size: 15px;
  color: #475569;
  line-height: 1.8;
  padding: 20px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.close-btn {
  padding: 10px 32px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 8px;
  transition: all 0.3s ease;
  border: 1px solid #dcdfe6;
  background: white;
  color: #606266;
}

.close-btn:hover {
  border-color: #409eff;
  color: #409eff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2);
}

/* 实习方向选择弹窗样式 */
.onboarding-dialog :deep(.el-dialog) {
  border-radius: 8px;
  overflow: hidden;
}

.onboarding-dialog :deep(.el-dialog__header) {
  padding: 0;
  border-bottom: none;
  margin: 0;
}

.onboarding-dialog :deep(.el-dialog__title) {
  display: none;
}

.onboarding-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.onboarding-dialog .header-section {
  background: linear-gradient(90deg, #1e88e5 0%, #4caf50 100%);
  padding: 18px 20px;
  margin-bottom: 0;
}

.onboarding-dialog .header-title {
  color: white;
  font-size: 20px;
  font-weight: 600;
  text-align: center;
  margin: 0;
}

.onboarding-form {
  padding: 20px 24px;
}

.company-field,
.company-field :deep(.el-autocomplete) {
  width: 100%;
}

.company-suggestion {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.company-match-info,
.company-match-warning {
  margin-top: 10px;
}

.company-match-action {
  margin-top: 4px;
  padding: 0;
}

.delay-state-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 34px 24px 28px;
  text-align: center;
}

.delay-state-icon {
  margin-bottom: 12px;
  color: #e6a23c;
  font-size: 34px;
}

.delay-state-icon.success {
  color: #5c9d7c;
}

.delay-state-icon.warning {
  color: #d46b6b;
}

.delay-state-title {
  color: #28364b;
  font-size: 18px;
  font-weight: 650;
}

.delay-state-detail {
  max-width: 360px;
  margin-top: 9px;
  color: #66758a;
  font-size: 13px;
  line-height: 1.7;
}

.delay-state-meta {
  display: grid;
  gap: 6px;
  margin-top: 18px;
  color: #7a8799;
  font-size: 12px;
}

.delay-state-alert {
  margin-bottom: 16px;
}

.onboarding-upload :deep(.el-upload-dragger) {
  width: 100%;
  padding: 16px 12px;
  border: 2px dashed #d1d5db;
  border-radius: 4px;
  background: #fafafa;
}

.onboarding-upload :deep(.el-upload-dragger:hover) {
  border-color: #1e88e5;
  background: #f0f9ff;
}

/* 响应式：小屏时路径选择器换行 */
@media screen and (max-width: 1200px) {
  .welcome-content {
    flex-direction: column;
    align-items: flex-start;
  }

  .welcome-right {
    width: 100%;
  }

  .path-selector {
    flex-wrap: wrap;
  }

  .path-card {
    flex: 1;
    min-width: 120px;
  }
}

/* 响应式：手机端欢迎区头像缩小，保持头像在左、文字在右 */
@media screen and (max-width: 768px) {
  .welcome-left {
    gap: 14px;
  }

  .avatar-wrapper,
  .avatar-img {
    width: 64px;
    height: 64px;
  }
}

/* 响应式：窄屏快捷入口改单列列表，消除 2+1 换行的宽度不齐 */
@media screen and (max-width: 480px) {
  .welcome-right {
    background: transparent;
    padding: 0;
    backdrop-filter: none;
  }

  .path-selector {
    flex-direction: column;
    flex-wrap: nowrap;
    gap: 8px;
  }

  .path-card {
    flex: none;
    min-width: 0;
    padding: 12px 14px;
  }

  .path-card:active {
    transform: scale(0.98);
  }

  .path-arrow {
    display: block;
  }
}

/* The assistant reduces the content width without changing the viewport breakpoint. */
body.agent-docked .welcome-content { flex-direction: column; align-items: stretch; gap: 20px; }
body.agent-docked .welcome-left { min-width: 0; }
body.agent-docked .welcome-right { width: 100%; }
body.agent-docked .path-selector { flex-wrap: wrap; }
body.agent-docked .path-card { flex: 1; min-width: 120px; }
</style>
