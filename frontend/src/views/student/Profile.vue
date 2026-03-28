<template>
  <div class="profile-container">
    <!-- 柔和背景装饰 -->
    <div class="profile-bg-pattern" aria-hidden="true">
      <span class="bg-dot"></span>
      <span class="bg-dot"></span>
      <span class="bg-dot"></span>
      <span class="bg-dot"></span>
      <span class="bg-dot"></span>
    </div>

    <!-- 内容区域 -->
    <div>
      <div class="page-header">
        <h2>个人中心</h2>
        <p>管理个人信息、实习档案与账号设置，让校园实习更清晰</p>
      </div>

    <div class="profile-card">
      <div class="card-glass-bg"></div>
      <div class="card-gradient-left"></div>
      <div class="card-gradient-right"></div>
      
      <div class="profile-content">
        <div class="avatar-section">
          <div class="avatar-container" @click="changeAvatar">
            <div class="avatar-ring"></div>
            <el-avatar :size="80" :src="avatarUrl" class="user-avatar">
              <el-icon class="avatar-placeholder"><User /></el-icon>
            </el-avatar>
            <div class="avatar-overlay">
              <el-icon class="camera-icon"><Camera /></el-icon>
            </div>
          </div>
          <input
            ref="avatarInput"
            type="file"
            accept="image/*"
            style="display: none"
            @change="handleAvatarChange"
          />
        </div>

        <div class="info-section">
          <div class="info-header">
            <div class="name-group">
              <h3 class="user-name">{{ userInfo.name }}</h3>
              <div :class="['status-badge', getStatusClass(internshipStatus.status)]">
                <span class="status-dot"></span>
                {{ getStatusText(internshipStatus.status) }}
              </div>
            </div>
          </div>

          <div class="info-tags">
            <div class="info-tag">
              <el-icon class="tag-icon"><User /></el-icon>
              <span class="tag-text">{{ userInfo.studentId }}</span>
            </div>
            <div v-if="internshipStatus.companyName" class="info-tag">
              <el-icon class="tag-icon"><OfficeBuilding /></el-icon>
              <span class="tag-text">{{ internshipStatus.companyName }}</span>
              <span v-if="internshipStatus.positionName" class="tag-text position-text"> - {{ internshipStatus.positionName }}</span>
            </div>
            <div v-else class="info-tag">
              <el-icon class="tag-icon"><OfficeBuilding /></el-icon>
              <span class="tag-text">{{ userInfo.department }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="options-section">
      <div class="options-grid">
        <div
          v-for="option in allOptions"
          :key="option.id"
          class="option-card"
          @click="handleOptionClick(option)"
        >
          <div class="card-header">
            <div :class="['card-icon-wrapper', option.iconClass]">
              <el-icon class="card-icon">
                <component :is="option.icon" />
              </el-icon>
            </div>
            <div class="card-title">{{ option.label }}</div>
          </div>
          <div class="card-body">
            <div class="card-desc">{{ getOptionDesc(option.id) }}</div>
          </div>
          <div class="card-footer">
            <el-icon class="card-arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
    </div>

    <div class="logout-wrap">
      <button class="logout-button" @click="handleLogout">
        <el-icon class="logout-icon"><SwitchButton /></el-icon>
        退出登录
      </button>
    </div>

    <!-- 资源中心对话框 -->
    <el-dialog
      v-model="showTemplateCenterDialog"
      title="资源中心"
      width="1000px"
      class="resource-dialog"
      :append-to-body="true"
      :lock-scroll="true"
      modal-class="global-modal"
      :show-close="true"
    >
      <div class="resource-container" v-loading="resourceLoading">
        <!-- 搜索栏 -->
        <div class="resource-search-bar">
          <el-input
            v-model="resourceSearchQuery"
            placeholder="搜索资源标题..."
            clearable
            @input="handleResourceSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>
        <div class="resources-grid">
          <div
            v-for="resource in resourceList"
            :key="resource.id"
            class="resource-card"
            @click="viewResource(resource)"
          >
            <div class="resource-header">
              <div class="resource-type">
                <el-tag :type="getResourceTypeTag(resource.type)" size="small">
                  {{ getResourceTypeText(resource.type) }}
                </el-tag>
              </div>
            </div>

            <div class="resource-content">
              <h3 class="resource-title" v-html="resource.title"></h3>
              <p class="resource-description" v-html="resource.description"></p>
            </div>

            <div class="resource-footer">
              <div class="resource-meta">
                <div class="meta-item">
                  <el-icon><User /></el-icon>
                  <span>{{ resource.uploader }}</span>
                </div>
                <div class="meta-item">
                  <el-icon><Download /></el-icon>
                  <span>{{ resource.downloadCount }}次下载</span>
                </div>
                <div class="meta-item">
                  <el-icon><Clock /></el-icon>
                  <span>{{ resource.uploadTime }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-if="resourceList.length === 0 && !resourceLoading" class="empty-resources">
          <el-empty description="暂无资源数据" />
        </div>
      </div>
    </el-dialog>

    <!-- 资源详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      :title="`资源详情 - ${selectedResource?.title || ''}`"
      width="700px"
      :close-on-click-modal="false"
      class="resource-detail-dialog"
      :append-to-body="true"
    >
      <div v-if="selectedResource" class="resource-detail">
        <div class="detail-section">
          <h4>基本信息</h4>
          <div class="detail-grid">
            <div class="detail-item">
              <label>资源ID：</label>
              <span>{{ selectedResource.id }}</span>
            </div>
            <div class="detail-item">
              <label>资源名称：</label>
              <span>{{ selectedResource.title }}</span>
            </div>
            <div class="detail-item">
              <label>资源类型：</label>
              <el-tag size="small">{{ getResourceTypeText(selectedResource.type) }}</el-tag>
            </div>
            <div class="detail-item" v-if="selectedResource.fileSize">
              <label>文件大小：</label>
              <span>{{ formatFileSize(selectedResource.fileSize) }}</span>
            </div>
            <div class="detail-item">
              <label>上传人：</label>
              <span>{{ selectedResource.uploader }}</span>
            </div>
            <div class="detail-item">
              <label>下载次数：</label>
              <span>{{ selectedResource.downloadCount || 0 }}</span>
            </div>
            <div class="detail-item">
              <label>上传时间：</label>
              <span>{{ selectedResource.uploadTime || selectedResource.createdTime }}</span>
            </div>
          </div>
        </div>

        <div class="detail-section" v-if="selectedResource.description">
          <h4>描述</h4>
          <div class="description-content">
            {{ selectedResource.description }}
          </div>
        </div>

        <div class="detail-section">
          <h4>文件操作</h4>
          <div class="file-actions">
            <el-button type="primary" @click="previewResource(selectedResource)" class="action-btn">
              <el-icon><ZoomIn /></el-icon>
              预览文件
            </el-button>
            <el-button type="success" @click="downloadResource(selectedResource)" class="action-btn">
              <el-icon><Download /></el-icon>
              下载文件
            </el-button>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="detailDialogVisible = false" class="cancel-btn">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 我的收藏对话框 -->
    <el-dialog
      v-model="showFavoritesDialog"
      title="我的收藏"
      width="800px"
      class="favorites-dialog"
      :append-to-body="true"
      :lock-scroll="true"
      modal-class="global-modal"
    >
      <div class="favorites-container">
        <template v-if="favoritesList.length > 0">
          <div
            v-for="item in favoritesList"
            :key="item.id"
            class="favorite-item"
          >
            <div class="favorite-header">
              <div class="favorite-icon">
                <el-icon :size="24"><Star /></el-icon>
              </div>
              <div class="favorite-title">{{ item.companyName }}</div>
            </div>
            <div class="favorite-body">
              <div class="favorite-info-grid">
                <div class="favorite-info">
                  <span class="info-label">职位</span>
                  <span class="info-value">{{ item.position }}</span>
                </div>
                <div class="favorite-info">
                  <span class="info-label">薪资</span>
                  <span class="info-value">{{ item.salary }}</span>
                </div>
                <div class="favorite-info">
                  <span class="info-label">地点</span>
                  <span class="info-value">{{ item.location }}</span>
                </div>
              </div>
              <div class="favorite-tags">
                <el-tag
                  v-for="tag in item.tags"
                  :key="tag"
                  size="small"
                  type="success"
                >
                  {{ tag }}
                </el-tag>
              </div>
              <div class="favorite-footer">
                <span class="collect-time">收藏时间：{{ item.collectTime }}</span>
                <div class="footer-buttons">
                  <el-button type="primary" size="small" @click="viewJobDetail(item)">
                    查看详情
                  </el-button>
                  <el-button type="danger" size="small" @click="removeFavorite(item.id)">
                    <el-icon><Delete /></el-icon>
                    取消收藏
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="favorites-empty">
            <div class="empty-icon">
              <el-icon :size="48"><Star /></el-icon>
            </div>
            <div class="empty-text">暂无收藏</div>
            <div class="empty-desc">您还没有收藏任何职位，去职位浏览页面看看吧</div>
          </div>
        </template>
      </div>
    </el-dialog>

    <!-- 职位详情对话框 -->
    <el-dialog
      v-model="showJobDetailDialog"
      title="职位详情"
      width="800px"
      class="job-detail-dialog"
      :append-to-body="true"
      :lock-scroll="true"
      modal-class="global-modal"
    >
      <div v-if="selectedJob" class="job-detail-content">
        <div class="detail-header">
          <div class="detail-title">
            <h3>{{ selectedJob.position }}</h3>
            <div v-if="selectedJob.internshipBase" class="internship-base-badges">
              <el-tag v-if="selectedJob.internshipBase === 'national'" size="small" type="danger" class="base-badge national">
                国家级实习基地
              </el-tag>
              <el-tag v-else-if="selectedJob.internshipBase === 'provincial'" size="small" type="warning" class="base-badge provincial">
                省级实习基地
              </el-tag>
            </div>
          </div>
          <div class="detail-actions">
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
              <span class="info-value">{{ selectedJob.companyName }}</span>
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
              <el-icon><Clock /></el-icon>
              <span class="info-label">时长：</span>
              <span class="info-value">{{ selectedJob.duration }}</span>
            </div>
            <div class="info-item">
              <el-icon><OfficeBuilding /></el-icon>
              <span class="info-label">类型：</span>
              <span class="info-value">{{ selectedJob.type }}</span>
            </div>
            <div class="info-item">
              <el-icon><OfficeBuilding /></el-icon>
              <span class="info-label">行业：</span>
              <span class="info-value">{{ selectedJob.industryName }}</span>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h4 class="section-title">职位描述</h4>
          <p class="detail-description">{{ selectedJob.description }}</p>
        </div>

        <div class="detail-section">
          <h4 class="section-title">任职要求</h4>
          <ul class="detail-list">
            <li v-for="(item, index) in selectedJob.requirements" :key="index">{{ item }}</li>
          </ul>
        </div>

        <div class="detail-section">
          <h4 class="section-title">福利待遇</h4>
          <ul class="detail-list">
            <li v-for="(item, index) in selectedJob.benefits" :key="index">{{ item }}</li>
          </ul>
        </div>

        <div class="detail-section">
          <h4 class="section-title">联系方式</h4>
          <div class="contact-info">
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

        <div class="detail-section">
          <h4 class="section-title">职位统计</h4>
          <div class="stats-info">
            <div class="stat-item">
              <span class="stat-label">发布时间：</span>
              <span class="stat-value">{{ selectedJob.publishTime }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">浏览次数：</span>
              <span class="stat-value">{{ selectedJob.viewCount }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">申请人数：</span>
              <span class="stat-value">{{ selectedJob.applyCount }}</span>
            </div>
          </div>
        </div>

        <div class="detail-footer">
          <el-button 
            v-if="!selectedJob.isApplied" 
            type="primary" 
            size="large"
            @click="applyJob(selectedJob)"
            class="detail-apply-button"
          >
            立即申请
          </el-button>
          <el-button 
            v-else 
            type="success" 
            size="large"
            disabled
            class="detail-applied-button"
          >
            已申请
          </el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 周日志查看对话框 -->
    <el-dialog
      v-model="showWeeklyLogsDialog"
      title=""
      width="950px"
      class="weekly-logs-dialog"
      :append-to-body="true"
      :lock-scroll="true"
      modal-class="global-modal"
    >
      <div class="weekly-logs-header">
        <el-button link size="default" @click="closeWeeklyLogsDialog">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <div class="weekly-logs-title">
          {{ currentArchive?.companyName }} - 周日志
        </div>
        <!-- 周次切换按钮 -->
        <div class="week-navigation">
          <el-button 
            type="primary" 
            :icon="ArrowLeft" 
            @click="prevWeek"
            :disabled="currentWeekIndex === 0"
            size="small"
          >
            上一周
          </el-button>
          <span class="current-week-indicator">第 {{ currentWeek + 1 }} 周 / 共 {{ currentWeeklyLogs.length }} 周</span>
          <el-button 
            type="primary" 
            :icon="ArrowRight" 
            @click="nextWeek"
            :disabled="currentWeekIndex === currentWeeklyLogs.length - 1"
            size="small"
          >
            下一周
          </el-button>
        </div>
      </div>
      <div class="weekly-logs-container">
        <div v-if="currentWeeklyLogs.length > 0" class="weekly-log-item">
          <div class="log-top">
            <div class="log-week">第 {{ currentWeeklyLogs[currentWeekIndex].week }} 周</div>
            <div class="log-date">{{ currentWeeklyLogs[currentWeekIndex].dateRange }}</div>
            <el-tag :type="currentWeeklyLogs[currentWeekIndex].status === 'approved' ? 'success' : 'warning'" size="small">
              {{ currentWeeklyLogs[currentWeekIndex].status === 'approved' ? '已审核' : '待审核' }}
            </el-tag>
          </div>
          <div class="log-info">
            <span class="info-item">
              <el-icon :size="14"><Calendar /></el-icon>
              {{ currentWeeklyLogs[currentWeekIndex].submitDate }}
            </span>
            <span class="info-item">
              <el-icon :size="14"><Clock /></el-icon>
              {{ currentWeeklyLogs[currentWeekIndex].submitTime }}
            </span>
            <span class="info-item">
              <el-icon :size="14"><Document /></el-icon>
              {{ currentWeeklyLogs[currentWeekIndex].attachmentCount }} 个附件
            </span>
          </div>
          <div class="log-content">
            <div class="content-item">
              <div class="content-label">工作内容</div>
              <div class="content-text">{{ currentWeeklyLogs[currentWeekIndex].workContent }}</div>
            </div>
            <div class="content-item">
              <div class="content-label">学习收获</div>
              <div class="content-text">{{ currentWeeklyLogs[currentWeekIndex].learning }}</div>
            </div>
            <div class="content-item">
              <div class="content-label">存在问题</div>
              <div class="content-text">{{ currentWeeklyLogs[currentWeekIndex].problems }}</div>
            </div>
            <div class="content-item">
              <div class="content-label">下周计划</div>
              <div class="content-text">{{ currentWeeklyLogs[currentWeekIndex].nextPlan }}</div>
            </div>
            <div v-if="currentWeeklyLogs[currentWeekIndex].status === 'approved'" class="content-item feedback-item">
              <div class="content-label">导师反馈</div>
              <div class="content-text">{{ currentWeeklyLogs[currentWeekIndex].supervisorFeedback }}</div>
              <div class="feedback-rating">
                <span class="rating-label">评分：</span>
                <span class="rating-score">{{ currentWeeklyLogs[currentWeekIndex].supervisorRating }}</span>
                <span class="rating-unit">分</span>
              </div>
            </div>
          </div>
          <!-- 附件列表 -->
          <div class="attachment-section" v-if="currentWeeklyLogs[currentWeekIndex].attachments && currentWeeklyLogs[currentWeekIndex].attachments.length > 0">
            <div class="attachment-label">附件列表</div>
            <div class="attachment-list">
              <div 
                v-for="(attachment, index) in currentWeeklyLogs[currentWeekIndex].attachments" 
                :key="index" 
                class="attachment-item"
                @click="downloadAttachment(attachment)"
              >
                <el-icon class="attachment-icon"><Document /></el-icon>
                <span class="attachment-name">{{ attachment.name }}</span>
                <span class="attachment-size">{{ attachment.size }}</span>
                <el-button type="primary" size="small" link>下载</el-button>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="empty-weekly-logs">
          <el-icon class="empty-icon"><Document /></el-icon>
          <div class="empty-text">暂无周日志记录</div>
        </div>
      </div>
    </el-dialog>

    <!-- 简历管理对话框 -->
    <el-dialog
      v-model="showResumeManagementDialog"
      title="简历管理"
      width="900px"
      class="resume-management-dialog"
      :append-to-body="true"
      :lock-scroll="true"
      modal-class="global-modal"
    >
      <div class="resume-management-container">
        <div class="resume-section">
          <div class="section-title">
            <el-icon :size="20"><Document /></el-icon>
            我的简历
            <el-button type="primary" size="small" @click="openUploadResumeInDialog" class="upload-btn">
              <el-icon><Upload /></el-icon>
              上传简历
            </el-button>
          </div>
          <div v-if="resumeList.length === 0" class="empty-tip">暂无简历，请上传</div>
          <div
            v-for="resume in resumeList"
            :key="resume.id"
            class="resume-item"
          >
            <div class="resume-icon">
              <el-icon :size="32"><DocumentCopy /></el-icon>
            </div>
            <div class="resume-info">
              <div class="resume-name">{{ resume.name }}</div>
              <div class="resume-meta">
                <span>上传时间：{{ resume.uploadTime }}</span>
                <span v-if="resume.size">大小：{{ resume.size }}</span>
              </div>
            </div>
            <div class="resume-actions">
              <el-button type="primary" size="small" @click="downloadResume(resume)">
                <el-icon><Download /></el-icon>
                下载
              </el-button>
              <el-button type="danger" size="small" @click="deleteResume(resume.id)">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </div>
          </div>
        </div>
        <div class="certificate-section">
          <div class="section-title">
            <el-icon :size="20"><FolderOpened /></el-icon>
            我的证书
            <el-button type="primary" size="small" @click="openUploadCertificateInDialog" class="upload-btn">
              <el-icon><Upload /></el-icon>
              上传证书
            </el-button>
          </div>
          <div v-if="certificateList.length === 0" class="empty-tip">暂无证书，请上传</div>
          <div
            v-for="cert in certificateList"
            :key="cert.id"
            class="certificate-item"
          >
            <div class="cert-icon">
              <el-icon :size="32"><FolderOpened /></el-icon>
            </div>
            <div class="cert-info">
              <div class="cert-name">{{ cert.name }}</div>
              <div class="cert-meta">
                <span>上传时间：{{ cert.uploadTime }}</span>
                <span v-if="cert.size">大小：{{ cert.size }}</span>
              </div>
            </div>
            <div class="cert-actions">
              <el-button type="primary" size="small" @click="downloadCertificate(cert)">
                <el-icon><Download /></el-icon>
                下载
              </el-button>
              <el-button type="danger" size="small" @click="deleteCertificate(cert.id)">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 简历上传区域 -->
      <div class="upload-area" v-if="showResumeUploadArea">
        <el-divider content-position="left">上传简历</el-divider>
        <el-upload
          class="upload-demo"
          drag
          action="#"
          :auto-upload="false"
          :on-change="handleResumeChange"
          :limit="1"
          :file-list="resumeFileList"
          accept=".pdf,.doc,.docx"
          :show-file-list="true"
        >
          <el-icon class="el-icon--upload"><Document /></el-icon>
          <div class="el-upload__text">
            拖拽文件到此处，或 <em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              请上传 PDF、Word 格式的简历文件，大小不超过 10MB
            </div>
          </template>
        </el-upload>
        <div class="upload-actions">
          <el-button @click="cancelResumeUpload">取消</el-button>
          <el-button type="primary" @click="uploadResume" :disabled="resumeFileList.length === 0">
            确认上传
          </el-button>
        </div>
      </div>

      <!-- 证书上传区域 -->
      <div class="upload-area" v-if="showCertificateUploadArea">
        <el-divider content-position="left">上传证书</el-divider>
        <el-upload
          class="upload-demo"
          drag
          action="#"
          :auto-upload="false"
          :on-change="handleCertificateChange"
          :file-list="certificateFileList"
          accept=".pdf,.jpg,.jpeg,.png,.doc,.docx"
          :show-file-list="true"
        >
          <el-icon class="el-icon--upload"><FolderOpened /></el-icon>
          <div class="el-upload__text">
            拖拽文件到此处，或 <em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              请上传 PDF、图片或 Word 格式的证书文件，大小不超过 10MB
            </div>
          </template>
        </el-upload>
        <div class="upload-actions">
          <el-button @click="cancelCertificateUpload">取消</el-button>
          <el-button type="primary" @click="uploadCertificate" :disabled="certificateFileList.length === 0">
            确认上传
          </el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 消息中心对话框 -->
    <el-dialog
      v-model="showMessageCenterDialog"
      title="消息中心"
      width="900px"
      class="message-center-dialog"
      :append-to-body="true"
      :lock-scroll="true"
      modal-class="global-modal"
    >
      <div v-loading="messageLoading" class="message-center-container">
        <el-empty v-if="!messageLoading && announcementList.length === 0" description="暂无公告通知" />
        <div
          v-for="announcement in announcementList"
          :key="announcement.id"
          :class="['message-item', { 'unread': !announcement.isRead }]"
          @click="viewAnnouncementDetail(announcement)"
        >
          <div class="message-header">
            <div class="message-sender">
              <el-tag v-if="announcement.priority === 'important'" type="warning" size="small" class="priority-tag">重要</el-tag>
              <el-tag
                :type="getPublisherRoleTagType(announcement.publisherRole)"
                size="small"
              >
                {{ getPublisherRoleText(announcement.publisherRole) }}
              </el-tag>
              <span class="sender-name">{{ announcement.publisher }}</span>
            </div>
            <div class="message-meta-right">
              <span class="message-time">{{ formatAnnouncementTime(announcement.publishTime) }}</span>
              <el-tag v-if="!announcement.isRead" type="danger" size="small" effect="plain">未读</el-tag>
            </div>
          </div>
          <div class="message-content">
            <div class="message-title">{{ announcement.title }}</div>
            <div class="message-text">{{ getPreviewText(announcement.content) }}</div>
          </div>
          <div class="message-footer">
            <span class="target-info">发送给: {{ getTargetText(announcement.targetType, announcement.targetValue) }}</span>
            <span class="read-count">阅读: {{ announcement.readCount || 0 }}</span>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 公告详情对话框 -->
    <el-dialog
      v-model="showAnnouncementDetailDialog"
      title="公告详情"
      width="700px"
      class="announcement-detail-dialog"
      :append-to-body="true"
      :lock-scroll="true"
      modal-class="global-modal"
    >
      <div v-if="currentAnnouncement" class="announcement-detail">
        <h2 class="detail-title">{{ currentAnnouncement.title }}</h2>
        <div class="detail-meta">
          <div class="meta-item">
            <span class="meta-label">发布人:</span>
            <span class="meta-value">{{ currentAnnouncement.publisher }}</span>
          </div>
          <el-tag :type="getPublisherRoleTagType(currentAnnouncement.publisherRole)" size="small">
            {{ getPublisherRoleText(currentAnnouncement.publisherRole) }}
          </el-tag>
          <div class="meta-item">
            <span class="meta-label">目标群体:</span>
            <span class="meta-value">{{ getTargetText(currentAnnouncement.targetType, currentAnnouncement.targetValue) }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">发布时间:</span>
            <span class="meta-value">{{ formatAnnouncementTime(currentAnnouncement.publishTime) }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">阅读次数:</span>
            <span class="meta-value">{{ currentAnnouncement.readCount || 0 }}</span>
          </div>
        </div>
        <el-divider />
        <div class="detail-content" v-html="currentAnnouncement.content"></div>
        <div v-if="currentAnnouncement.attachments && parseAttachments(currentAnnouncement.attachments).length > 0" class="attachments-section">
          <el-divider></el-divider>
          <div class="attachments-title">
            <el-icon><Paperclip /></el-icon>
            <span>附件列表</span>
          </div>
          <div class="attachments-list">
            <el-tag
              v-for="(attachment, index) in parseAttachments(currentAnnouncement.attachments)"
              :key="index"
              class="attachment-tag"
              @click.stop="previewAnnouncementFile(attachment)"
            >
              <el-icon class="attachment-icon"><Document /></el-icon>
              {{ attachment.name }}
              <el-icon class="preview-icon"><ZoomIn /></el-icon>
            </el-tag>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showAnnouncementDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- Blob文件预览对话框 -->
    <el-dialog
      v-model="blobPreviewVisible"
      :title="blobPreviewTitle"
      width="80%"
      class="blob-preview-dialog"
      :close-on-click-modal="true"
      :append-to-body="true"
    >
      <div class="blob-preview-container">
        <iframe
          v-if="blobPreviewUrl"
          :src="blobPreviewUrl"
          class="blob-iframe"
          frameborder="0"
        ></iframe>
        <div v-else class="blob-preview-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>加载中...</span>
        </div>
      </div>
    </el-dialog>

    <!-- 文件预览对话框 -->
    <FilePreviewDialog
      v-model="filePreviewVisible"
      :file-url="currentFileUrl"
      :file-name="currentFileName"
    />

    <!-- 修改密码对话框 -->
    <el-dialog
      v-model="showChangePasswordDialog"
      title="修改密码"
      width="500px"
      class="edit-profile-dialog"
      :append-to-body="true"
      :lock-scroll="true"
      modal-class="global-modal"
      data-custom-dialog="edit-profile"
    >
      <el-form :model="changePasswordForm" label-width="100px">
        <el-form-item label="原密码">
          <el-input v-model="changePasswordForm.oldPassword" type="password" placeholder="请输入原密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="changePasswordForm.newPassword" type="password" placeholder="请输入新密码" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="changePasswordForm.confirmPassword" type="password" placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showChangePasswordDialog = false">取消</el-button>
        <el-button type="primary" @click="handleChangePassword">确定</el-button>
      </template>
    </el-dialog>

    <!-- 绑定手机对话框 -->
    <el-dialog
      v-model="showBindPhoneDialog"
      :title="isPhoneBound ? '更换手机' : '绑定手机'"
      width="500px"
      class="edit-profile-dialog"
      :append-to-body="true"
      :lock-scroll="true"
      modal-class="global-modal"
      data-custom-dialog="edit-profile"
    >
      <el-form :model="bindPhoneForm" label-width="100px">
        <!-- 已绑定手机时显示当前手机号 -->
        <el-form-item v-if="isPhoneBound" label="当前手机">
          <el-input :value="maskPhone(authStore.user?.phone)" disabled />
        </el-form-item>
        <!-- 新手机号输入框 -->
        <el-form-item label="新手机号">
          <el-input v-model="bindPhoneForm.phone" placeholder="请输入新手机号码" />
        </el-form-item>
        <el-form-item label="验证码">
          <div class="code-input-group">
            <el-input v-model="bindPhoneForm.code" placeholder="请输入验证码" />
            <el-button type="primary" @click="sendCode">获取验证码</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBindPhoneDialog = false">取消</el-button>
        <el-button type="primary" @click="handleBindPhone">{{ isPhoneBound ? '更换' : '绑定' }}</el-button>
      </template>
    </el-dialog>
  </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  User,
  Edit,
  Camera,
  ArrowRight,
  ArrowLeft,
  Document,
  FolderOpened,
  DocumentCopy,
  Star,
  StarFilled,
  Folder,
  Message,
  Lock,
  Iphone,
  SwitchButton,
  Download,
  Delete,
  Close,
  OfficeBuilding,
  Location,
  Money,
  Clock,
  Calendar,
  ChatDotRound,
  ChatDotSquare,
  Upload,
  Paperclip,
  ZoomIn,
  Loading,
  Search
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'
import request from '@/utils/request'
import positionService from '@/api/PositionService'
import { resourceApi } from '@/api/resource'
import { getPublishedResourceDocuments } from '@/api/resourceDocument'
import { getAnnouncementsForUser, createAnnouncementReadRecord } from '@/api/announcement'
import FilePreviewDialog from '@/components/FilePreviewDialog.vue'

const authStore = useAuthStore()

// 从 auth store 获取学生 ID
const getStudentId = () => {
  return authStore.user?.id
}

const router = useRouter()

const showEditProfileDialog = ref(false)
const showTemplateCenterDialog = ref(false)
const showFavoritesDialog = ref(false)
const showJobDetailDialog = ref(false)
const selectedJob = ref(null)
const showResumeManagementDialog = ref(false)
const showMessageCenterDialog = ref(false)
const showChangePasswordDialog = ref(false)
const showBindPhoneDialog = ref(false)
const isPhoneBound = ref(false) // 是否已绑定手机
const showWeeklyLogsDialog = ref(false)
const avatarInput = ref(null)
const avatarUrl = ref('')
const showResumeUploadArea = ref(false)
const showCertificateUploadArea = ref(false)

// 实习状态
const internshipStatus = ref({
  status: null
})

// 初始化默认用户信息，确保页面加载时立即显示
const userInfo = ref({
  name: '李四',
  studentId: '2020001',
  department: '临床医学系 - 临床医学 1班',
  motto: '天道酬勤，厚德载物',
  bio: ''
})

// 初始化编辑表单数据
const editProfileForm = ref({
  name: '李四',
  studentId: '2020001',
  department: '临床医学系',
  major: '临床医学',
  class: '1班',
  gender: '男',
  phone: '13800138000',
  grade: '四年级',
  motto: '天道酬勤，厚德载物'
})

// 院系选项
const departmentOptions = ref([
  '临床医学系',
  '口腔医学系',
  '基础医学院',
  '公共卫生学院',
  '护理学院',
  '药学院',
  '医学检验系',
  '医学影像系',
  '康复医学系',
  '麻醉学系'
])

// 专业选项
const majorOptions = ref([
  '临床医学',
  '口腔医学',
  '预防医学',
  '护理学',
  '药学',
  '医学检验技术',
  '医学影像技术',
  '康复治疗学',
  '麻醉学',
  '儿科学'
])

// 年级选项
const gradeOptions = ref([
  '一年级',
  '二年级',
  '三年级',
  '四年级',
  '五年级'
])

const resumeFileList = ref([])
const certificateFileList = ref([])
const myResumes = ref([])  // 从API加载的简历列表
const myCertificates = ref([])  // 从API加载的证书列表
const currentArchive = ref(null)
const currentWeeklyLogs = ref([])
const currentWeekIndex = ref(0)
const currentWeek = computed(() => currentWeekIndex.value)

const allOptions = computed(() => {
  return [...internshipOptions, ...documentOptions, ...securityOptions]
})

// 实习进度计算
const internshipProgress = computed(() => {
  const status = internshipStatus.value.status
  const startTime = internshipStatus.value.internshipStartTime
  const endTime = internshipStatus.value.internshipEndTime

  // 状态4已结束，显示100%
  if (status === 4) {
    return {
      percent: 100,
      color: '#67c23a',
      startText: startTime ? formatDate(startTime) : '开始',
      endText: endTime ? formatDate(endTime) : '结束'
    }
  }

  // 状态2已确定但未开始，显示0%或提示即将开始
  if (status === 2) {
    if (startTime) {
      const startDate = new Date(startTime)
      const today = new Date()
      const todayStr = today.toISOString().split('T')[0]
      const startDateStr = startDate.toISOString().split('T')[0]
      if (todayStr >= startDateStr) {
        // 已经到达开始时间，显示准备开始
        return {
          percent: 0,
          color: '#409eff',
          startText: formatDate(startTime),
          endText: endTime ? formatDate(endTime) : '-'
        }
      } else {
        // 尚未到达开始时间
        const daysUntilStart = Math.ceil((startDate - today) / (1000 * 60 * 60 * 24))
        return {
          percent: 0,
          color: '#909399',
          startText: `${daysUntilStart}天后开始`,
          endText: endTime ? formatDate(endTime) : '-'
        }
      }
    }
    return {
      percent: 0,
      color: '#909399',
      startText: '尚未设置',
      endText: endTime ? formatDate(endTime) : '-'
    }
  }

  // 状态3实习中，计算进度
  if (status === 3 && startTime && endTime) {
    const startDate = new Date(startTime)
    const endDate = new Date(endTime)
    const today = new Date()

    if (today >= endDate) {
      // 已超过结束时间，显示100%
      return {
        percent: 100,
        color: '#67c23a',
        startText: formatDate(startTime),
        endText: formatDate(endTime)
      }
    }

    if (today <= startDate) {
      // 尚未开始，显示0%
      return {
        percent: 0,
        color: '#409eff',
        startText: formatDate(startTime),
        endText: formatDate(endTime)
      }
    }

    // 计算进度百分比
    const totalDuration = endDate - startDate
    const elapsed = today - startDate
    const percent = Math.min(100, Math.max(0, Math.round((elapsed / totalDuration) * 100)))

    // 根据进度选择颜色
    let color = '#409eff' // 蓝色进行中
    if (percent >= 80) {
      color = '#67c23a' // 绿色即将完成
    } else if (percent >= 50) {
      color = '#e6a23c' // 橙色进行中
    }

    return {
      percent,
      color,
      startText: formatDate(startTime),
      endText: formatDate(endTime)
    }
  }

  // 默认
  return {
    percent: 0,
    color: '#909399',
    startText: '-',
    endText: '-'
  }
})

// 日期格式化
const formatDate = (date) => {
  if (!date) return '-'
  const d = new Date(date)
  if (isNaN(d.getTime())) return '-'
  const month = d.getMonth() + 1
  const day = d.getDate()
  return `${month}月${day}日`
}

// 周次切换函数
const prevWeek = () => {
  if (currentWeekIndex.value > 0) {
    currentWeekIndex.value--
  }
}

const nextWeek = () => {
  if (currentWeekIndex.value < currentWeeklyLogs.value.length - 1) {
    currentWeekIndex.value++
  }
}

// 下载附件
const downloadAttachment = (attachment) => {
  ElMessage.success(`开始下载 ${attachment.name}`)
  // 这里后续可以集成后端下载逻辑
}

// 收藏企业数据
const favoritesList = ref([])

// 加载收藏列表
const loadFavorites = async () => {
  try {
    const response = await positionService.getFavorites()
    if (response.code === 200) {
      favoritesList.value = (response.data || []).map((item) => ({
        id: item.positionId,
        companyName: item.companyName,
        position: item.positionName,
        salary: item.salary,
        location: item.location,
        industryName: item.industryName,
        duration: item.duration,
        internshipBase: item.internshipBase,
        description: item.description,
        requirements: typeof item.requirements === 'string' ? item.requirements.split('\n') : (item.requirements || []),
        benefits: typeof item.benefits === 'string' ? item.benefits.split('\n') : (item.benefits || []),
        contactPerson: item.contactPerson,
        contactPhone: item.contactPhone,
        contactEmail: item.contactEmail,
        publishTime: item.publishTime,
        viewCount: item.viewCount,
        applyCount: item.applyCount,
        collectTime: item.collectTime,
        isFavorite: true
      }))
    }
  } catch (error) {
    console.error('加载收藏列表失败:', error)
  }
}

// 实习档案数据
const internshipArchivesList = ref([
  {
    id: 1,
    companyName: '北京协和医院',
    position: '临床实习生',
    startDate: '2025-06-01',
    endDate: '2025-12-31',
    status: '已完成',
    supervisor: '张主任',
    rating: 95,
    weeklyLogs: [
      {
        id: 1,
        week: 1,
        dateRange: '2025-06-01 至 2025-06-07',
        submitDate: '2025-06-07',
        submitTime: '17:30',
        workContent: '熟悉医院环境，学习科室规章制度，了解基本工作流程。跟随带教老师进行病房巡视，学习患者基本信息记录方法。',
        learning: '掌握了医院的基本规章制度，学会了如何与患者进行有效沟通，了解了临床工作的基本流程。',
        problems: '对部分医学术语理解不够深入，需要加强学习。',
        nextPlan: '继续熟悉科室工作，学习常见疾病的护理要点，提高专业技能。',
        status: 'approved',
        attachmentCount: 2,
        attachments: [
          { name: '第一周实习报告.docx', size: '1.2MB' },
          { name: '科室规章制度学习笔记.pdf', size: '850KB' }
        ],
        supervisorFeedback: '学习态度认真，工作积极主动，能够快速适应医院环境。',
        supervisorRating: 90
      },
      {
        id: 2,
        week: 2,
        dateRange: '2025-06-08 至 2025-06-14',
        submitDate: '2025-06-14',
        submitTime: '16:45',
        workContent: '参与患者护理工作，学习基础护理操作技能，协助医生进行查房。负责测量患者生命体征，记录护理日志。',
        learning: '掌握了生命体征测量的标准操作流程，学会了如何准确记录护理日志，提高了与患者的沟通能力。',
        problems: '在操作熟练度方面还有待提高，需要多加练习。',
        nextPlan: '加强基础护理操作练习，学习专科护理知识，提高工作效率。',
        status: 'approved',
        attachmentCount: 1,
        attachments: [
          { name: '第二周实习总结.docx', size: '980KB' }
        ],
        supervisorFeedback: '操作技能有所提升，工作认真负责，能够按时完成任务。',
        supervisorRating: 95
      },
      {
        id: 3,
        week: 3,
        dateRange: '2025-06-15 至 2025-06-21',
        submitDate: '2025-06-21',
        submitTime: '18:00',
        workContent: '独立完成部分护理工作，参与科室病例讨论，学习临床思维方法。协助处理突发情况，提高应急处理能力。',
        learning: '学会了独立完成基础护理工作，了解了临床病例讨论的重要性，提高了应急处理能力。',
        problems: '在处理复杂病例时经验不足，需要向带教老师多请教。',
        nextPlan: '继续学习临床知识，积累更多实践经验，提高综合能力。',
        status: 'pending',
        attachmentCount: 3,
        supervisorFeedback: '',
        supervisorRating: 0
      }
    ]
  },
  {
    id: 2,
    companyName: '上海瑞金医院',
    position: '护理实习生',
    startDate: '2025-03-01',
    endDate: '2025-08-31',
    status: '已完成',
    supervisor: '李护士长',
    rating: 90,
    weeklyLogs: [
      {
        id: 4,
        week: 1,
        dateRange: '2025-03-01 至 2025-03-07',
        submitDate: '2025-03-07',
        submitTime: '16:30',
        workContent: '熟悉医院环境和科室布局，学习护理工作流程。跟随带教老师进行病房巡视，学习基础护理操作。',
        learning: '了解了医院的基本情况，掌握了护理工作的基本流程，学会了基础护理操作。',
        problems: '对医院环境还不够熟悉，需要时间适应。',
        nextPlan: '尽快熟悉医院环境，提高护理操作技能。',
        status: 'approved',
        attachmentCount: 1,
        supervisorFeedback: '适应能力强，学习态度积极，能够认真完成布置的任务。',
        supervisorRating: 92
      },
      {
        id: 5,
        week: 2,
        dateRange: '2025-03-08 至 2025-03-14',
        submitDate: '2025-03-14',
        submitTime: '17:15',
        workContent: '参与患者日常护理工作，学习静脉输液、导尿等操作技能。协助医生进行查房，记录患者病情变化。',
        learning: '掌握了静脉输液的基本操作，学会了如何观察患者病情变化，提高了护理技能。',
        problems: '在操作熟练度方面还有待提高。',
        nextPlan: '加强护理操作练习，提高工作效率。',
        status: 'approved',
        attachmentCount: 2,
        supervisorFeedback: '操作技能进步明显，工作认真负责，能够主动学习。',
        supervisorRating: 93
      }
    ]
  }
])

// 简历和证书数据
const resumeList = ref([])
const certificateList = ref([])

// 消息中心数据 - 公告列表
const announcementList = ref([])
const messageLoading = ref(false)

// 公告详情对话框
const showAnnouncementDetailDialog = ref(false)
const currentAnnouncement = ref(null)

// 文件预览相关
const filePreviewVisible = ref(false)
const currentFileUrl = ref('')
const currentFileName = ref('')

// Blob文件预览相关
const blobPreviewVisible = ref(false)
const blobPreviewUrl = ref('')
const blobPreviewTitle = ref('文件预览')

// 获取学生专业ID
const getMajorId = () => {
  const user = authStore.user
  if (!user || !user.majorId) return ''
  return String(user.majorId)
}

// 加载公告列表
const loadAnnouncements = async () => {
  messageLoading.value = true
  try {
    const majorId = getMajorId()
    const userId = authStore.user?.id ? String(authStore.user.id) : undefined
    console.log('[loadAnnouncements] majorId:', majorId, 'userId:', userId)
    const response = await getAnnouncementsForUser('STUDENT', majorId, userId)
    if (response && response.code === 200) {
      console.log('[loadAnnouncements] 返回数据:', response.data)
      announcementList.value = response.data || []
    } else {
      ElMessage.error(response?.message || '获取公告失败')
    }
  } catch (error) {
    console.error('获取公告失败:', error)
    ElMessage.error('获取公告失败')
  } finally {
    messageLoading.value = false
  }
}

// 查看公告详情
const viewAnnouncementDetail = async (announcement) => {
  currentAnnouncement.value = announcement

  try {
    const userId = authStore.user?.id ? parseInt(String(authStore.user.id)) : 0
    console.log('[viewAnnouncementDetail] 公告ID:', announcement.id, '用户ID:', userId)
    await createAnnouncementReadRecord({
      announcementId: announcement.id,
      userId: userId,
      userType: 'STUDENT'
    })
    announcement.isRead = true
  } catch (error) {
    console.error('记录阅读状态失败:', error)
  }

  showAnnouncementDetailDialog.value = true
}

// 格式化公告时间
const formatAnnouncementTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

// 解析附件
const parseAttachments = (attachments) => {
  if (!attachments) return []
  try {
    if (typeof attachments === 'string') {
      return JSON.parse(attachments)
    }
    return attachments
  } catch (e) {
    console.error('解析附件数据失败:', e)
    return []
  }
}

// 预览文件
const previewAnnouncementFile = (attachment) => {
  if (!attachment.url) {
    ElMessage.error('附件地址不存在')
    return
  }
  currentFileUrl.value = attachment.url
  currentFileName.value = attachment.name
  filePreviewVisible.value = true
}

// 获取预览文本
const getPreviewText = (content) => {
  if (!content) return ''
  const text = content.replace(/<[^>]*>/g, '')
  return text.length > 100 ? text.substring(0, 100) + '...' : text
}

// 获取发布人角色文本
const getPublisherRoleText = (role) => {
  const map = {
    ADMIN: '管理员',
    COLLEGE: '学院教师',
    DEPARTMENT: '系室教师',
    COUNSELOR: '辅导员'
  }
  return map[role] || '-'
}

// 获取发布人角色标签类型
const getPublisherRoleTagType = (role) => {
  const map = {
    ADMIN: 'danger',
    COLLEGE: 'primary',
    DEPARTMENT: 'success',
    COUNSELOR: 'warning'
  }
  return map[role] || 'info'
}

// 获取目标群体文本
const getTargetText = (targetType, targetValue) => {
  if (!targetType) return '-'

  const typeMap = {
    ALL: '全体师生',
    STUDENT: '全体学生',
    TEACHER: '全体教师',
    TEACHER_TYPE: '特定教师类别',
    MAJOR: '特定专业学生'
  }

  const typeText = typeMap[targetType] || targetType

  if (targetType === 'TEACHER_TYPE' && targetValue) {
    const teacherTypeMap = {
      COLLEGE: '学院教师',
      DEPARTMENT: '系室教师',
      COUNSELOR: '辅导员'
    }
    return `${typeText}（${teacherTypeMap[targetValue] || targetValue}）`
  }

  if (targetType === 'MAJOR' && targetValue) {
    return `${typeText}（${targetValue}）`
  }

  return typeText
}

// 修改密码表单
const changePasswordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 绑定手机表单
const bindPhoneForm = ref({
  phone: '',
  code: ''
})

// 资源中心数据
const resourceList = ref([])
const allResourceList = ref([])
const resourceSearchQuery = ref('')
const resourceLoading = ref(false)
const selectedResource = ref(null)
const detailDialogVisible = ref(false)

// 资源类型映射
const getResourceTypeTag = (type) => {
  const typeMap = {
    'document': 'primary',
    'video': 'success',
    'code': 'warning',
    'dataset': 'info'
  }
  return typeMap[type] || 'info'
}

// 获取资源类型文本
const getResourceTypeText = (type) => {
  if (!type) return '未知'
  const typeStr = String(type).toLowerCase()
  const typeTextMap = {
    'document': '文档',
    'video': '视频',
    'code': '代码',
    'dataset': '数据集'
  }
  return typeTextMap[typeStr] || typeStr || '未知'
}

// 标准化资源类型
const normalizeResourceType = (type) => {
  if (!type) return '未知'
  const typeStr = String(type).toLowerCase()
  const mimeToTypeMap = {
    'application/pdf': 'document',
    'application/msword': 'document',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'document',
    'application/vnd.ms-powerpoint': 'document',
    'application/vnd.openxmlformats-officedocument.presentationml.presentation': 'document',
    'application/vnd.ms-excel': 'document',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'document',
    'text/plain': 'document',
    'video/mp4': 'video',
    'video/avi': 'video',
    'video/mov': 'video',
    'text/javascript': 'code',
    'application/json': 'code'
  }
  const extensionToTypeMap = {
    '.pdf': 'document', '.doc': 'document', '.docx': 'document',
    '.ppt': 'document', '.pptx': 'document', '.xls': 'document',
    '.xlsx': 'document', '.txt': 'document', '.mp4': 'video',
    '.avi': 'video', '.mov': 'video', '.java': 'code',
    '.py': 'code', '.js': 'code', '.json': 'code'
  }
  if (['document', 'video', 'code', 'dataset'].includes(typeStr)) {
    return typeStr
  }
  if (mimeToTypeMap[typeStr]) {
    return mimeToTypeMap[typeStr]
  }
  for (const [ext, resourceType] of Object.entries(extensionToTypeMap)) {
    if (typeStr.endsWith(ext)) {
      return resourceType
    }
  }
  if (type && type !== '') {
    return String(type)
  }
  return '未知'
}

// 获取资源列表
const fetchResources = async () => {
  resourceLoading.value = true
  try {
    const response = await getPublishedResourceDocuments()

    let allResources = []
    // 响应拦截器返回 response.data，即 { code, message, data }
    const responseData = response.data
    if (responseData?.data) {
      allResources = Array.isArray(responseData.data) ? responseData.data : []
    } else if (Array.isArray(responseData)) {
      allResources = responseData
    }

    const mappedResources = allResources.map(item => ({
      ...item,
      id: item.id,
      title: item.title,
      description: item.description,
      url: item.fileUrl,
      type: normalizeResourceType(item.fileType),
      uploadTime: item.publishTime || item.createTime ? new Date(item.publishTime || item.createTime).toLocaleString('zh-CN') : '',
      uploader: item.publisher || '系统管理员',
      downloadCount: item.downloadCount || 0
    }))

    allResourceList.value = mappedResources
    resourceList.value = mappedResources
  } catch (error) {
    console.error('获取资源列表失败:', error)
    ElMessage.error('获取资源列表失败')
  } finally {
    resourceLoading.value = false
  }
}

// 搜索资源
const handleResourceSearch = () => {
  const query = resourceSearchQuery.value.trim().toLowerCase()
  if (!query) {
    resourceList.value = allResourceList.value
    return
  }
  resourceList.value = allResourceList.value.filter(item =>
    item.title.toLowerCase().includes(query)
  )
}

// 查看资源详情
const viewResource = (resource) => {
  selectedResource.value = resource
  detailDialogVisible.value = true
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}

// 预览资源
const previewResource = (resource) => {
  if (!resource) {
    ElMessage.error('资源信息不存在')
    return
  }
  const fileUrl = resource.url || resource.fileUrl
  if (!fileUrl) {
    ElMessage.error('文件地址不存在')
    return
  }
  currentFileUrl.value = fileUrl
  currentFileName.value = resource.title || resource.fileName || '文件预览'
  filePreviewVisible.value = true
}

// 下载资源
const downloadResource = async (resource) => {
  try {
    const resourceId = resource.id
    if (!resourceId) {
      ElMessage.error('资源信息不完整')
      return
    }
    // 调用后端下载接口
    window.open(`/api/resource-documents/download/${resourceId}`, '_blank')
    ElMessage.success('下载已启动')
  } catch (error) {
    ElMessage.error('下载失败')
    console.error('下载失败:', error)
  }
}

// 获取用户信息
const fetchUserInfo = async () => {
  try {
    const response = await request.get(`/student/profile`)
    if (response.code === 200) {
      const user = response.data
      userInfo.value = {
        name: user.name || '',
        studentId: user.studentId || '',
        department: `${user.department || ''} - ${user.major || ''} ${user.class || ''}`,
        motto: user.motto || '',
        bio: user.bio || ''
      }
      avatarUrl.value = user.avatar || ''
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
    // 使用默认数据，确保页面正常显示
  }
}

// 获取实习状态
const fetchInternshipStatus = async () => {
  try {
    const response = await request.get(`/student/internship-status`)
    if (response.code === 200 && response.data) {
      internshipStatus.value.status = response.data.status
      internshipStatus.value.companyName = response.data.companyName
      internshipStatus.value.positionName = response.data.positionName
      internshipStatus.value.internshipStartTime = response.data.internshipStartTime
      internshipStatus.value.internshipEndTime = response.data.internshipEndTime
    }
  } catch (error) {
    console.error('获取实习状态失败:', error)
  }
}

// 实习状态文本映射 - 与后端 InternshipStatusEnum 一致
const getStatusText = (status) => {
  if (status === null) return '未申请'
  const statusMap = {
    0: '无offer',
    1: '待确认',
    2: '已确定',
    3: '实习中',
    4: '已结束',
    5: '已中断',
    6: '延期'
  }
  return statusMap[status] || '未知'
}

// 实习状态样式映射
const getStatusClass = (status) => {
  if (status === null) return 'default'
  if (status === 1 || status === 2) return 'offer' // 待确认、已确定
  if (status === 3) return 'interning' // 实习中
  if (status === 4) return 'finished' // 已结束
  if (status === 5) return 'interrupted' // 已中断
  if (status === 6) return 'delayed' // 延期
  return 'default'
}

// 性别数字转字符串
const genderNumberToString = (gender) => {
  if (gender === undefined || gender === null) return ''
  if (typeof gender === 'string') return gender
  return gender === 1 ? '男' : '女'
}

// 性别字符串转数字
const genderStringToNumber = (gender) => {
  return gender === '男' ? 1 : 0
}

const internshipOptions = [
  {
    id: 2,
    label: '我的收藏',
    icon: Star,
    iconClass: 'orange',
    action: 'myFavorites'
  },
  {
    id: 4,
    label: '简历管理',
    icon: DocumentCopy,
    iconClass: 'lightblue',
    action: 'resumeManagement'
  },
  {
    id: 5,
    label: '消息中心',
    icon: Message,
    iconClass: 'purple',
    action: 'messageCenter'
  }
]

const documentOptions = [
  {
    id: 10,
    label: '资源中心',
    icon: DocumentCopy,
    iconClass: 'purple',
    action: 'templateCenter'
  }
]

const securityOptions = [
  {
    id: 6,
    label: '修改密码',
    icon: Lock,
    iconClass: 'dark',
    action: 'changePassword'
  },
  {
    id: 7,
    label: '绑定手机',
    icon: Iphone,
    iconClass: 'cyan',
    action: 'bindPhone'
  }
]

const getOptionDesc = (id) => {
  const descs = {
    2: '查看收藏的职位',
    4: '管理简历和证书',
    5: '查看系统消息',
    6: '修改登录密码',
    7: '绑定手机号码',
    10: '下载简历、日志等模板'
  }
  return descs[id] || ''
}

const openEditProfileDialog = () => {
  // 优先使用从后端获取的完整用户信息，其次使用全局状态
  const user = authStore.user
  // 从后端获取的数据显示在 userInfo.value 中，格式是 "院系 - 专业 班级"
  // 需要解析出单独的院系、专业、班级
  const deptInfo = userInfo.value.department || ''
  const deptParts = deptInfo.split(' - ')
  const deptMain = deptParts[0] || ''
  // 格式可能是 "院系 - 专业 班级" 或 "院系 - 专业 班级" (1班)
  const majorClassMatch = deptParts[1] ? deptParts[1].match(/^(.+?)\s*(\d+班?)$/) : null
  const majorFromDept = majorClassMatch ? majorClassMatch[1] : (deptParts[1] || '')
  const classFromDept = majorClassMatch ? majorClassMatch[2] : ''

  editProfileForm.value = {
    name: userInfo.value.name || user?.name || '',
    studentId: userInfo.value.studentId || user?.studentId || '',
    department: deptMain || user?.department || '',
    major: majorFromDept || user?.major || '',
    class: classFromDept || user?.class || '',
    gender: genderNumberToString(user?.gender) || '',
    phone: user?.phone || '',
    grade: user?.grade || '',
    motto: userInfo.value.motto || user?.motto || '',
    bio: userInfo.value.bio || user?.bio || ''
  }
  showEditProfileDialog.value = true
}

const saveProfile = async () => {
  try {
    // 构建提交数据，将性别转换为数字
    const submitData = {
      ...editProfileForm.value,
      gender: genderStringToNumber(editProfileForm.value.gender)
    }
    const response = await request.post(`/student/profile/update`, submitData)
    if (response.code === 200) {
      // 更新全局状态
      // 更新 auth store
      if (authStore.user) {
        authStore.user.name = editProfileForm.value.name
        authStore.user.studentId = editProfileForm.value.studentId
        authStore.user.department = editProfileForm.value.department
        authStore.user.major = editProfileForm.value.major
        authStore.user.class = editProfileForm.value.class
        authStore.user.gender = editProfileForm.value.gender
        authStore.user.phone = editProfileForm.value.phone
        authStore.user.grade = editProfileForm.value.grade
      }

      // 更新本地显示
      userInfo.value = {
        name: editProfileForm.value.name,
        studentId: editProfileForm.value.studentId,
        department: `${editProfileForm.value.department} - ${editProfileForm.value.major} ${editProfileForm.value.class}`,
        motto: editProfileForm.value.motto,
        bio: editProfileForm.value.bio
      }

      showEditProfileDialog.value = false
      ElMessage.success('个人资料保存成功')
    } else {
      ElMessage.error(response.data.message || '保存失败，请稍后重试')
    }
  } catch (error) {
    console.error('保存个人资料失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  }
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

  // 先本地预览
  const reader = new FileReader()
  reader.onload = (e) => {
    avatarUrl.value = e.target.result
  }
  reader.readAsDataURL(file)

  // 上传到后端
  try {
    const formData = new FormData()
    formData.append('file', file)
    const response = await request.post('/student/profile/upload/avatar', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    if (response.code === 200) {
      avatarUrl.value = response.data.url
      ElMessage.success('头像上传成功')
    } else {
      ElMessage.error(response.message || '头像上传失败')
    }
  } catch (error) {
    console.error('头像上传失败:', error)
    ElMessage.error('头像上传失败，请稍后重试')
  }
}

// 处理简历上传
const handleResumeChange = (uploadFile) => {
  if (uploadFile.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 10MB')
    return
  }
  resumeFileList.value = [uploadFile]
}

// 处理证书上传
const handleCertificateChange = (uploadFile) => {
  if (uploadFile.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 10MB')
    return
  }
  certificateFileList.value.push(uploadFile)
}

// 在简历管理对话框中打开简历上传区域
const openUploadResumeInDialog = () => {
  showResumeUploadArea.value = true
  showCertificateUploadArea.value = false
  resumeFileList.value = []
}

// 在简历管理对话框中打开证书上传区域
const openUploadCertificateInDialog = () => {
  showCertificateUploadArea.value = true
  showResumeUploadArea.value = false
  certificateFileList.value = []
}

// 取消简历上传
const cancelResumeUpload = () => {
  showResumeUploadArea.value = false
  resumeFileList.value = []
}

// 取消证书上传
const cancelCertificateUpload = () => {
  showCertificateUploadArea.value = false
  certificateFileList.value = []
}

// 上传简历
const uploadResume = async () => {
  if (resumeFileList.value.length === 0) {
    ElMessage.warning('请先选择简历文件')
    return
  }

  const uploadFile = resumeFileList.value[0]
  // 获取原始File对象（Element Plus将文件包装在raw属性中）
  const file = uploadFile.raw || uploadFile
  const formData = new FormData()
  formData.append('file', file)

  try {
    const response = await request.post('/student/profile/upload/resume', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    if (response.code === 200) {
      ElMessage.success('简历上传成功')
      showResumeUploadArea.value = false
      resumeFileList.value = []
      // 刷新简历列表
      await loadMyResumes()
    } else {
      ElMessage.error(response.message || '上传失败')
    }
  } catch (error) {
    console.error('上传简历失败:', error)
    ElMessage.error('上传失败，请重试')
  }
}

// 加载我的简历列表
const loadMyResumes = async () => {
  try {
    const response = await request.get('/student/profile/resumes')
    if (response.code === 200) {
      myResumes.value = response.data || []
      resumeList.value = myResumes.value  // 同步到模板使用的列表
    }
  } catch (error) {
    console.error('加载简历列表失败:', error)
  }
}

// 删除简历
const deleteResume = async (id) => {
  try {
    const response = await request.delete(`/student/profile/resume/${id}`)
    if (response.code === 200) {
      ElMessage.success('简历删除成功')
      await loadMyResumes()
    } else {
      ElMessage.error(response.message || '删除失败')
    }
  } catch (error) {
    console.error('删除简历失败:', error)
    ElMessage.error('删除失败，请重试')
  }
}

// 下载简历
const downloadResume = async (resume) => {
  if (!resume || !resume.id) {
    ElMessage.error('简历信息不存在')
    return
  }
  try {
    const token = authStore.token
    const response = await fetch(`/api/student/profile/preview/resume/${resume.id}`, {
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    })
    if (!response.ok) {
      throw new Error('下载失败')
    }
    const blob = await response.blob()
    const blobUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = resume.name || 'resume'
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
    // 等待一小段时间后清理，确保下载已触发
    setTimeout(() => {
      document.body.removeChild(link)
      URL.revokeObjectURL(blobUrl)
    }, 100)
  } catch (error) {
    console.error('下载简历失败:', error)
    ElMessage.error('下载失败，请重试')
  }
}

// 上传证书
const uploadCertificate = async () => {
  if (certificateFileList.value.length === 0) {
    ElMessage.warning('请先选择证书文件')
    return
  }

  const uploadFile = certificateFileList.value[0]
  // 获取原始File对象（Element Plus将文件包装在raw属性中）
  const file = uploadFile.raw || uploadFile
  const formData = new FormData()
  formData.append('file', file)

  try {
    const response = await request.post('/student/profile/upload/certificate', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    if (response.code === 200) {
      ElMessage.success('证书上传成功')
      showCertificateUploadArea.value = false
      certificateFileList.value = []
      // 刷新证书列表
      await loadMyCertificates()
    } else {
      ElMessage.error(response.message || '上传失败')
    }
  } catch (error) {
    console.error('上传证书失败:', error)
    ElMessage.error('上传失败，请重试')
  }
}

// 加载我的证书列表
const loadMyCertificates = async () => {
  try {
    const response = await request.get('/student/profile/certificates')
    if (response.code === 200) {
      myCertificates.value = response.data || []
      certificateList.value = myCertificates.value  // 同步到模板使用的列表
    }
  } catch (error) {
    console.error('加载证书列表失败:', error)
  }
}

// 删除证书
const deleteCertificate = async (id) => {
  try {
    const response = await request.delete(`/student/profile/certificate/${id}`)
    if (response.code === 200) {
      ElMessage.success('证书删除成功')
      await loadMyCertificates()
    } else {
      ElMessage.error(response.message || '删除失败')
    }
  } catch (error) {
    console.error('删除证书失败:', error)
    ElMessage.error('删除失败，请重试')
  }
}

// 下载证书
const downloadCertificate = async (cert) => {
  try {
    const token = authStore.token
    const response = await fetch(`/api/student/profile/download/certificate/${cert.id}`, {
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    })
    if (!response.ok) {
      throw new Error('下载失败')
    }
    const blob = await response.blob()
    const blobUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = cert.name || 'certificate'
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
    setTimeout(() => {
      document.body.removeChild(link)
      URL.revokeObjectURL(blobUrl)
    }, 100)
  } catch (error) {
    console.error('下载证书失败:', error)
    ElMessage.error('下载失败，请重试')
  }
}

// 下载或预览文件
const downloadFile = (file) => {
  const fileName = file.name.toLowerCase()
  
  if (fileName.endsWith('.pdf')) {
    ElMessage.success(`正在打开：${file.name}`)
    
    if (file.url) {
      window.open(file.url, '_blank')
    } else {
      ElMessage.info('文件URL未配置，请连接后端后配置文件路径')
    }
  } else {
    ElMessage.success(`正在下载：${file.name}`)
    
    if (file.url) {
      const link = document.createElement('a')
      link.href = file.url
      link.download = file.name
      link.click()
    } else {
      ElMessage.info('文件URL未配置，请连接后端后配置文件路径')
    }
  }
}

// 下载模板
const downloadTemplate = (template) => {
  // 调用后端API下载模板文件
  window.open(`/api/admin/templates/${template.id}/download`, '_blank')
}

const handleOptionClick = (option) => {
  switch (option.action) {
    case 'myFavorites':
      showFavoritesDialog.value = true
      break
    case 'resumeManagement':
      showResumeManagementDialog.value = true
      break
    case 'messageCenter':
      showMessageCenterDialog.value = true
      loadAnnouncements()
      break
    case 'templateCenter':
      showTemplateCenterDialog.value = true
      fetchResources()
      break
    case 'changePassword':
      showChangePasswordDialog.value = true
      break
    case 'bindPhone':
      // 判断是否已绑定手机
      isPhoneBound.value = !!authStore.user?.phone
      // 清空表单
      bindPhoneForm.value = {
        phone: '',
        code: ''
      }
      showBindPhoneDialog.value = true
      break
    default:
      ElMessage.info('功能开发中')
  }
}

const removeFavorite = async (id) => {
  try {
    const response = await positionService.toggleFavorite(id)
    if (response.code === 200) {
      const index = favoritesList.value.findIndex(item => item.id === id)
      if (index !== -1) {
        favoritesList.value.splice(index, 1)
      }
      ElMessage.success('取消收藏成功')
    } else {
      ElMessage.error(response.message || '操作失败')
    }
  } catch (error) {
    console.error('取消收藏失败:', error)
    ElMessage.error('取消收藏失败')
  }
}

const viewJobDetail = (job) => {
  // 处理 requirements 字段，确保是数组格式
  let requirements = job.requirements
  if (typeof requirements === 'string') {
    requirements = requirements.split('\n').filter(item => item.trim())
  }
  if (!Array.isArray(requirements)) {
    requirements = []
  }
  selectedJob.value = {
    ...job,
    requirements
  }
  showJobDetailDialog.value = true
}

const toggleFavorite = (job) => {
  job.isFavorite = !job.isFavorite
  if (job.isFavorite) {
    ElMessage.success('收藏成功')
  } else {
    ElMessage.success('取消收藏成功')
  }
}

const applyJob = (job) => {
  job.isApplied = true
  job.applyCount++
  ElMessage.success('申请成功')
}

const deleteArchive = (id) => {
  const index = internshipArchivesList.value.findIndex(item => item.id === id)
  if (index !== -1) {
    internshipArchivesList.value.splice(index, 1)
    ElMessage.success('删除档案成功')
  }
}

const viewWeeklyLogs = (archive) => {
  currentArchive.value = archive
  currentWeeklyLogs.value = archive.weeklyLogs || []
  currentWeekIndex.value = 0
  showWeeklyLogsDialog.value = true
}

const closeWeeklyLogsDialog = () => {
  // 添加过渡效果
  showWeeklyLogsDialog.value = false
  // 可以在这里添加其他清理逻辑
}

const handleChangePassword = async () => {
  if (!changePasswordForm.value.oldPassword) {
    ElMessage.warning('请输入原密码')
    return
  }
  if (!changePasswordForm.value.newPassword) {
    ElMessage.warning('请输入新密码')
    return
  }
  if (changePasswordForm.value.newPassword !== changePasswordForm.value.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  
  try {
    const response = await request.post(`/student/profile/change-password`, {
      oldPassword: changePasswordForm.value.oldPassword,
      newPassword: changePasswordForm.value.newPassword
    })
    if (response.code === 200) {
      ElMessage.success('密码修改成功，请重新登录')
      showChangePasswordDialog.value = false
      changePasswordForm.value = {
        oldPassword: '',
        newPassword: '',
        confirmPassword: ''
      }
      router.push('/login')
    } else {
      ElMessage.error(response.data.message || '密码修改失败，请稍后重试')
    }
  } catch (error) {
    console.error('修改密码失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  }
}

const sendCode = () => {
  if (!bindPhoneForm.value.phone) {
    ElMessage.warning('请输入手机号码')
    return
  }
  ElMessage.success('验证码已发送')
}

const handleBindPhone = async () => {
  if (!bindPhoneForm.value.phone) {
    ElMessage.warning('请输入手机号码')
    return
  }
  if (!bindPhoneForm.value.code) {
    ElMessage.warning('请输入验证码')
    return
  }
  
  try {
    const response = await request.post(`/student/profile/bind-phone`, {
      phone: bindPhoneForm.value.phone,
      code: bindPhoneForm.value.code
    })
    if (response.code === 200) {
      ElMessage.success('手机绑定成功')
      showBindPhoneDialog.value = false
      bindPhoneForm.value = {
        phone: '',
        code: ''
      }
    } else {
      ElMessage.error(response.data.message || '手机绑定失败，请稍后重试')
    }
  } catch (error) {
    console.error('绑定手机失败:', error)
    ElMessage.error('网络错误，请稍后重试')
  }
}

// 隐藏手机号中间四位
const maskPhone = (phone) => {
  if (!phone) return '未绑定'
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

const handleLogout = async () => {
  // 如果是只读模式（管理员切换过来的），点击退出应该返回管理员端
  if (authStore.user?.isReadOnly && authStore.user?.originalAdminUsername) {
    const originalAdminUsername = authStore.user.originalAdminUsername
    const currentRole = authStore.role
    if (currentRole) {
      const rolePrefix = currentRole.startsWith('ROLE_') ? currentRole.toLowerCase() + '_' : ''
      localStorage.removeItem(rolePrefix + 'accessToken_' + currentRole)
      localStorage.removeItem(rolePrefix + 'refreshToken_' + currentRole)
      localStorage.removeItem(rolePrefix + 'token_' + currentRole)
      localStorage.removeItem(rolePrefix + 'role_' + currentRole)
      localStorage.removeItem(rolePrefix + 'userId_' + currentRole)
      localStorage.removeItem(rolePrefix + 'username_' + currentRole)
      localStorage.removeItem(rolePrefix + 'isReadOnly_' + currentRole)
      localStorage.removeItem(rolePrefix + 'originalAdminUsername_' + currentRole)
    }

    localStorage.setItem('current_role', 'ROLE_ADMIN')
    localStorage.setItem('admin_accessToken_ROLE_ADMIN', localStorage.getItem('admin_accessToken_ROLE_ADMIN') || '')
    localStorage.setItem('admin_refreshToken_ROLE_ADMIN', localStorage.getItem('admin_refreshToken_ROLE_ADMIN') || '')
    localStorage.setItem('admin_role_ROLE_ADMIN', 'ROLE_ADMIN')
    localStorage.setItem('admin_userId_ROLE_ADMIN', localStorage.getItem('admin_userId_ROLE_ADMIN') || '')
    localStorage.setItem('admin_username_ROLE_ADMIN', originalAdminUsername)

    authStore.role = 'ROLE_ADMIN'
    authStore.user = {
      id: localStorage.getItem('admin_userId_ROLE_ADMIN'),
      username: originalAdminUsername,
      name: originalAdminUsername
    }
    authStore.token = localStorage.getItem('admin_accessToken_ROLE_ADMIN')
    authStore.isAuthenticated = true

    await router.push('/admin/dashboard')
  } else {
    await authStore.logout()
    ElMessage.success('退出登录成功')
    router.push('/login')
  }
}

// 监听收藏对话框打开，加载收藏列表
watch(showFavoritesDialog, async (newVal) => {
  if (newVal) {
    await loadFavorites()
  }
})

// 监听简历管理对话框打开，加载简历和证书列表
watch(showResumeManagementDialog, async (newVal) => {
  if (newVal) {
    await Promise.all([
      loadMyResumes(),
      loadMyCertificates()
    ])
    // 同步到模板使用的列表
    resumeList.value = myResumes.value
    certificateList.value = myCertificates.value
  }
})

onMounted(() => {
  fetchUserInfo()
  fetchInternshipStatus()
})
</script>

<style scoped>
.profile-container {
  min-height: 100%;
  background: #f5f7fa;
}

.profile-bg-pattern {
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

/* 加载状态样式 */
.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 16px;
}

.loading-spinner {
  width: 48px;
  height: 48px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #409EFF;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.loading-text {
  font-size: 16px;
  color: #64748b;
  font-weight: 500;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.page-header {
  margin-bottom: 24px;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  border-radius: 12px;
  padding: 24px 32px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  position: relative;
  overflow: hidden;
  z-index: 1;
}

.page-header::before {
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

.page-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: white;
  margin: 0 0 8px 0;
  letter-spacing: 0.3px;
}

.page-header p {
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
  line-height: 1.5;
  margin: 0;
}

.profile-card {
  position: relative;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 24px;
  padding: 20px 24px;
  margin-bottom: 24px;
  box-shadow: 
    0 8px 32px rgba(0, 0, 0, 0.1),
    0 2px 8px rgba(0, 0, 0, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.6);
  overflow: hidden;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.profile-card:hover {
  box-shadow: 
    0 12px 48px rgba(0, 0, 0, 0.12),
    0 4px 16px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.card-glass-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, 
    rgba(64, 158, 255, 0.05) 0%, 
    rgba(103, 194, 58, 0.05) 100%);
  pointer-events: none;
  z-index: 0;
}

.card-gradient-left {
  position: absolute;
  top: -50%;
  left: -20%;
  width: 60%;
  height: 200%;
  background: radial-gradient(circle, rgba(64, 158, 255, 0.15) 0%, transparent 70%);
  pointer-events: none;
  z-index: 0;
}

.card-gradient-right {
  position: absolute;
  top: -50%;
  right: -20%;
  width: 60%;
  height: 200%;
  background: radial-gradient(circle, rgba(103, 194, 58, 0.15) 0%, transparent 70%);
  pointer-events: none;
  z-index: 0;
}

.profile-content {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 20px;
}

.avatar-section {
  flex-shrink: 0;
}

.avatar-container {
  position: relative;
  width: 100px;
  height: 100px;
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.avatar-container:hover {
  transform: scale(1.05);
}

.avatar-container:hover .avatar-overlay {
  opacity: 1;
}

.avatar-ring {
  position: absolute;
  top: -4px;
  left: -4px;
  right: -4px;
  bottom: -4px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  z-index: -1;
  animation: ringRotate 3s linear infinite;
}

@keyframes ringRotate {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.user-avatar {
  background: linear-gradient(135deg, #e6f7ff 0%, #b3d9ff 100%);
  border: 4px solid white;
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.25);
  width: 100px !important;
  height: 100px !important;
}

.avatar-placeholder {
  font-size: 48px;
  color: #409eff;
}

.avatar-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all 0.3s ease;
}

.camera-icon {
  font-size: 32px;
  color: white;
}

.info-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.name-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-name {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
  letter-spacing: 0.5px;
  margin: 0;
}

.status-badge {
  padding: 5px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.3s ease;
}

.status-badge.interning {
  background: linear-gradient(135deg, #67c23a 0%, #85ce61 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(103, 194, 58, 0.3);
}

.status-badge.finished {
  background: linear-gradient(135deg, #909399 0%, #a6a9ad 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(144, 147, 153, 0.3);
}

.status-badge.offer {
  background: linear-gradient(135deg, #409EFF 0%, #66b1ff 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

.status-badge.interrupted {
  background: linear-gradient(135deg, #f56c6c 0%, #f78989 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(245, 108, 108, 0.3);
}

.status-badge.delayed {
  background: linear-gradient(135deg, #e6a23c 0%, #ebb563 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(230, 162, 60, 0.3);
}

.status-badge.default {
  background: linear-gradient(135deg, #e6a23c 0%, #ebb563 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(230, 162, 60, 0.3);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: white;
  animation: pulse 2s infinite;
  box-shadow: 0 0 8px rgba(255, 255, 255, 0.5);
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.7;
    transform: scale(1.2);
  }
}

.edit-btn {
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  color: white;
  font-weight: 600;
  padding: 8px 16px;
  border-radius: 10px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
  border: none;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.25);
  flex-shrink: 0;
}

.edit-btn:hover {
  background: linear-gradient(135deg, #66b1ff 0%, #409eff 100%);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.35);
}

.info-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.info-tag {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.08) 0%, rgba(64, 158, 255, 0.03) 100%);
  border: 1px solid rgba(64, 158, 255, 0.15);
  border-radius: 10px;
  transition: all 0.3s ease;
}

.info-tag:hover {
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.12) 0%, rgba(64, 158, 255, 0.06) 100%);
  border-color: rgba(64, 158, 255, 0.3);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
}

.tag-icon {
  font-size: 16px;
  color: #409eff;
}

.tag-text {
  font-size: 14px;
  color: #475569;
  font-weight: 500;
}

.position-text {
  color: #909399;
  font-weight: 400;
}

/* 实习进度条样式 */
.internship-progress {
  margin-top: 16px;
  padding: 14px 16px;
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.06) 0%, rgba(64, 158, 255, 0.02) 100%);
  border: 1px solid rgba(64, 158, 255, 0.12);
  border-radius: 12px;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.progress-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.progress-percent {
  font-size: 14px;
  color: #409eff;
  font-weight: 600;
}

.internship-progress :deep(.el-progress) {
  margin-bottom: 8px;
}

.internship-progress :deep(.el-progress-bar__outer) {
  background-color: rgba(64, 158, 255, 0.1);
}

.progress-time {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
}

@media screen and (max-width: 768px) {
  .profile-content {
    flex-direction: column;
    text-align: center;
  }
  
  .info-header {
    flex-direction: column;
    gap: 12px;
  }
  
  .name-group {
    flex-direction: column;
    gap: 8px;
  }
  
  .info-tags {
    justify-content: center;
  }
}

.options-section {
  background: linear-gradient(135deg, #ffffff 0%, #f8fafc 100%);
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  margin-top: 20px;
  position: relative;
  z-index: 1;
  padding: 20px;
}

.options-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  position: relative;
  z-index: 1;
}

.option-card {
  background: white;
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  cursor: pointer;
  position: relative;
  border: 1px solid rgba(226, 232, 240, 0.4);
}

.option-card::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 3px;
  background: linear-gradient(90deg, #409EFF 0%, #67C23A 100%);
  border-radius: 14px 14px 0 0;
  transition: height 0.3s ease;
}

.option-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 20px rgba(64, 158, 255, 0.15);
  border-color: #409EFF;
  background: linear-gradient(145deg, #ffffff 0%, #f0f9ff 100%);
}

.option-card:hover::before {
  height: 4px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.card-icon-wrapper {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
}

.option-card:hover .card-icon-wrapper {
  transform: scale(1.08);
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.12);
}

.card-icon-wrapper.blue {
  background: linear-gradient(135deg, #e8f4f8 0%, #d1e9ff 100%);
}

.card-icon-wrapper.orange {
  background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
}

.card-icon-wrapper.green {
  background: linear-gradient(135deg, #dcfce7 0%, #bbf7d0 100%);
}

.card-icon-wrapper.lightblue {
  background: linear-gradient(135deg, #e0e7ff 0%, #c7d2fe 100%);
}

.card-icon-wrapper.purple {
  background: linear-gradient(135deg, #f3e8ff 0%, #d8b4fe 100%);
}

.card-icon-wrapper.yellow {
  background: linear-gradient(135deg, #fef9c3 0%, #fde047 100%);
}

.card-icon-wrapper.dark {
  background: linear-gradient(135deg, #e5e7eb 0%, #d1d5db 100%);
}

.card-icon-wrapper.cyan {
  background: linear-gradient(135deg, #cffafe 0%, #a5f3fc 100%);
}

.card-icon {
  font-size: 24px;
  color: #1e293b;
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
  flex: 1;
  letter-spacing: 0.2px;
}

.card-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-bottom: 12px;
}

.card-desc {
  font-size: 14px;
  color: #64748b;
  line-height: 1.5;
  font-weight: 500;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid rgba(226, 232, 240, 0.3);
}

.card-arrow {
  color: #94a3b8;
  font-size: 16px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.option-card:hover .card-arrow {
  transform: translateX(6px);
  color: #409eff;
}

@media screen and (max-width: 768px) {
  .options-grid {
    grid-template-columns: 1fr;
  }
}

.logout-wrap {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}

.logout-button {
  background: white;
  border: 1px solid #e2e8f0;
  color: #f56c6c;
  padding: 12px 32px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.logout-button:hover {
  background: #fef0f0;
  border-color: #fbc4c4;
  transform: translateY(-2px);
  box-shadow: 0 4px 14px rgba(245, 108, 108, 0.15);
}

.logout-icon {
  font-size: 18px;
}

.edit-profile-dialog.el-dialog {
  border-radius: 16px !important;
  overflow: hidden !important;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15) !important;
  animation: dialog-fade-in 0.3s cubic-bezier(0.4, 0, 0.2, 1) !important;
}

.edit-profile-dialog.el-dialog .el-dialog__header {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%) !important;
  color: white !important;
  padding: 24px 32px !important;
  margin: 0 !important;
  border-radius: 0 !important;
  position: relative !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
}

.edit-profile-dialog.el-dialog .el-dialog__title {
  color: white !important;
  font-weight: 700 !important;
  font-size: 20px !important;
  position: relative !important;
  z-index: 1 !important;
  text-align: center !important;
  margin: 0 !important;
}

.edit-profile-dialog.el-dialog .el-dialog__headerbtn {
  top: 20px !important;
  right: 24px !important;
}

.edit-profile-dialog.el-dialog .el-dialog__headerbtn .el-dialog__close {
  color: white !important;
  font-size: 22px !important;
  background: rgba(255, 255, 255, 0.2) !important;
  border-radius: 50% !important;
  width: 32px !important;
  height: 32px !important;
  transition: all 0.3s ease !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  border: 1px solid rgba(255, 255, 255, 0.3) !important;
}

.edit-profile-dialog.el-dialog .el-dialog__headerbtn .el-dialog__close:hover {
  background: rgba(255, 255, 255, 0.4) !important;
  transform: rotate(90deg) !important;
  box-shadow: 0 4px 12px rgba(255, 255, 255, 0.4) !important;
}

.edit-profile-dialog.el-dialog .el-dialog__body {
  padding: 32px !important;
  background: white !important;
  position: relative !important;
}

.edit-profile-dialog.el-dialog .el-dialog__body::before {
  content: '' !important;
  position: absolute !important;
  top: 0 !important;
  left: 32px !important;
  right: 32px !important;
  height: 1px !important;
  background: linear-gradient(90deg, transparent 0%, #e2e8f0 50%, transparent 100%) !important;
}

.edit-profile-dialog.el-dialog .el-dialog__footer {
  padding: 20px 32px !important;
  background: #fafafa !important;
  border-top: 1px solid #f0f0f0 !important;
}

.edit-profile-dialog.el-dialog .el-form-item__label {
  font-weight: 600 !important;
  color: #1e293b !important;
}

.edit-profile-dialog.el-dialog .el-input__wrapper {
  border-radius: 8px !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  transition: all 0.3s ease !important;
}

.edit-profile-dialog.el-dialog .el-input__wrapper:hover {
  box-shadow: 0 0 0 1px #409eff inset !important;
}

.edit-profile-dialog.el-dialog .el-input__wrapper.is-focus {
  box-shadow: 0 0 0 1px #409eff inset !important;
}

.edit-profile-dialog.el-dialog .el-textarea__inner {
  border-radius: 8px !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  transition: all 0.3s ease !important;
}

.edit-profile-dialog.el-dialog .el-textarea__inner:hover {
  box-shadow: 0 0 0 1px #409eff inset !important;
}

.edit-profile-dialog.el-dialog .el-textarea__inner:focus {
  box-shadow: 0 0 0 1px #409eff inset !important;
}

.edit-profile-dialog.el-dialog .el-select__wrapper {
  border-radius: 8px !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  transition: all 0.3s ease !important;
}

.edit-profile-dialog.el-dialog .el-select__wrapper:hover {
  box-shadow: 0 0 0 1px #409eff inset !important;
}

.edit-profile-dialog.el-dialog .el-select__wrapper.is-focus {
  box-shadow: 0 0 0 1px #409eff inset !important;
}

.edit-profile-dialog.el-dialog .el-button--primary {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%) !important;
  border: none !important;
  border-radius: 8px !important;
  padding: 10px 24px !important;
  font-weight: 600 !important;
  transition: all 0.3s ease !important;
}

.edit-profile-dialog.el-dialog .el-button--primary:hover {
  transform: translateY(-2px) !important;
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.3) !important;
}

@media screen and (max-width: 768px) {
  .profile-card {
    flex-direction: column;
    align-items: center;
    text-align: center;
    gap: 20px;
    padding: 20px 24px;
    min-height: auto;
  }

  .profile-card-deco--2,
  .profile-card-deco--3 {
    display: none;
  }

  .profile-card-deco--1 {
    width: 80px;
    height: 80px;
    top: -20px;
  }

  .profile-header {
    flex-direction: column;
    align-items: center;
    gap: 12px;
  }

  .edit-profile-btn {
    width: 100%;
    justify-content: center;
  }

  .profile-details {
    width: 100%;
  }

  .info-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .option-item {
    padding: 16px 20px;
  }

  .option-icon-wrapper {
    width: 40px;
    height: 40px;
  }

  .option-icon {
    font-size: 18px;
  }

  .logout-button {
    padding: 12px 24px;
  }
}

@media screen and (max-width: 480px) {
  .profile-container {
    padding: 16px;
  }

  .page-header-icon {
    width: 42px;
    height: 42px;
    font-size: 20px;
  }

  .page-header h2 {
    font-size: 18px;
  }

  .page-header p {
    font-size: 13px;
  }

  .profile-card {
    padding: 18px 20px;
    gap: 16px;
    border-radius: 14px;
  }

  .avatar-wrapper {
    width: 64px;
    height: 64px;
  }

  .user-avatar {
    width: 64px !important;
    height: 64px !important;
  }

  .avatar-placeholder {
    font-size: 30px;
  }

  .user-name {
    font-size: 20px;
  }

  .status-badge {
    font-size: 12px;
    padding: 5px 12px;
  }

  .option-item {
    padding: 14px 16px;
  }

  .option-text {
    font-size: 14px;
  }

  .option-desc {
    font-size: 11px;
  }
}

/* 上传对话框样式 */
.upload-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  animation: dialog-fade-in 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  margin-top: 8vh !important;
}

.upload-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  color: white;
  padding: 24px 32px;
  margin: 0;
  border-radius: 0;
  position: relative;
}

.upload-dialog :deep(.el-dialog__header::after) {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.3) 0%, rgba(255, 255, 255, 0.8) 50%, rgba(255, 255, 255, 0.3) 100%);
}

.upload-dialog :deep(.el-dialog__title) {
  color: white;
  font-weight: 700;
  font-size: 20px;
  position: relative;
  z-index: 1;
}

.upload-dialog :deep(.el-dialog__headerbtn) {
  top: 20px;
  right: 24px;
}

.upload-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
  font-size: 22px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  width: 32px;
  height: 32px;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.upload-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  background: rgba(255, 255, 255, 0.4);
  transform: rotate(90deg);
  box-shadow: 0 4px 12px rgba(255, 255, 255, 0.4);
}

.upload-dialog :deep(.el-dialog__body) {
  padding: 32px;
  background: white;
  position: relative;
}

.upload-dialog :deep(.el-dialog__body::before) {
  content: '';
  position: absolute;
  top: 0;
  left: 32px;
  right: 32px;
  height: 1px;
  background: linear-gradient(90deg, transparent 0%, #e2e8f0 50%, transparent 100%);
}

.upload-dialog :deep(.el-dialog__footer) {
  padding: 20px 32px;
  background: #fafafa;
  border-top: 1px solid #f0f0f0;
  display: flex;
  justify-content: center;
  gap: 16px;
}

.upload-dialog :deep(.el-button) {
  padding: 10px 28px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.3s ease;
  border: 1px solid #dcdfe6;
}

.upload-dialog :deep(.el-button:hover) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border-color: #409eff;
}

.upload-dialog :deep(.el-button--primary) {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  border: none;
  color: white;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.3);
}

.upload-dialog :deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, #66b1ff 0%, #85ce61 100%);
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.4);
  transform: translateY(-2px);
}

/* 统一对话框样式 */
.global-modal {
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(8px);
}

.global-modal .upload-dialog,
.global-modal .template-dialog,
.global-modal .favorites-dialog,
.global-modal .archives-dialog,
.global-modal .resume-management-dialog,
.global-modal .message-center-dialog,
.global-modal .weekly-logs-dialog {
  animation: dialog-fade-in 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes dialog-fade-in {
  from {
    opacity: 0;
    transform: scale(0.95) translateY(-20px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

/* 上传容器样式 */
.upload-container {
  max-height: 450px;
  overflow-y: auto;
  background: #fafafa;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #e8e8e8;
  position: relative;
}

.upload-container::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, #409eff 0%, #67c23a 100%);
  border-radius: 12px 12px 0 0;
}

.upload-container h4 {
  margin-top: 20px;
  margin-bottom: 16px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  text-align: center;
  padding-bottom: 12px;
  border-bottom: 1px solid #e8e8e8;
  position: relative;
}

.upload-container h4::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 50%;
  transform: translateX(-50%);
  width: 60px;
  height: 2px;
  background: linear-gradient(90deg, #409eff 0%, #67c23a 100%);
}

.file-info {
  margin-top: 20px;
  padding: 16px;
  background: white;
  border-radius: 8px;
  border: 1px solid #e8e8e8;
  display: flex;
  align-items: center;
  gap: 12px;
  transition: all 0.3s ease;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.file-info:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
  border-color: #409eff;
}

.file-list {
  margin-top: 20px;
}

/* 优化上传组件样式 */
.upload-container :deep(.el-upload) {
  border-radius: 8px;
  transition: all 0.3s ease;
}

.upload-container :deep(.el-upload:hover) {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.upload-container :deep(.el-upload-dragger) {
  border-radius: 8px;
  padding: 24px 20px;
  background: white;
  border: 2px dashed #d9d9d9;
  transition: all 0.3s ease;
  position: relative;
}

.upload-container :deep(.el-upload-dragger::before) {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.02) 0%, rgba(103, 194, 58, 0.02) 100%);
  pointer-events: none;
  z-index: 0;
}

.upload-container :deep(.el-upload-dragger:hover) {
  border-color: #409eff;
  background: #f0f9ff;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.15);
}

.upload-container :deep(.el-upload-dragger .el-icon--upload) {
  font-size: 48px;
  color: #409eff;
  margin-bottom: 12px;
  position: relative;
  z-index: 1;
}

.upload-container :deep(.el-upload__text) {
  color: #606266;
  font-size: 14px;
  font-weight: 400;
  margin-top: 12px;
  position: relative;
  z-index: 1;
}

.upload-container :deep(.el-upload__text em) {
  color: #409eff;
  font-style: normal;
  font-weight: 600;
}

.upload-container :deep(.el-upload__tip) {
  color: #909399;
  font-size: 12px;
  margin-top: 12px;
  text-align: center;
  position: relative;
  z-index: 1;
}

/* 优化表格样式 */
.upload-container :deep(.el-table) {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  border: 1px solid #e8e8e8;
}

.upload-container :deep(.el-table__header) {
  background: #fafafa;
}

.upload-container :deep(.el-table__header th) {
  background: #fafafa;
  font-weight: 600;
  color: #303133;
  padding: 12px 16px;
  border-bottom: 1px solid #e8e8e8;
  font-size: 13px;
}

.upload-container :deep(.el-table__body tr) {
  transition: background 0.25s ease;
}

.upload-container :deep(.el-table__body tr:hover) {
  background: #f0f9ff !important;
}

.upload-container :deep(.el-table__body td) {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  color: #475569;
  font-size: 13px;
}

.upload-container :deep(.el-button--primary) {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  border: none;
  color: white;
  border-radius: 6px;
  padding: 6px 14px;
  font-weight: 500;
  font-size: 12px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 6px rgba(64, 158, 255, 0.2);
}

.upload-container :deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, #66b1ff 0%, #85ce61 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

.upload-container :deep(.el-button) {
  border-radius: 6px;
  padding: 6px 14px;
  font-weight: 500;
  font-size: 12px;
  transition: all 0.3s ease;
  border: 1px solid #d9d9d9;
}

.upload-container :deep(.el-button:hover) {
  border-color: #409eff;
  color: #409eff;
  transform: translateY(-1px);
}

.upload-container :deep(.el-button:hover) {
  transform: translateY(-2px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

/* 响应式调整 */
@media screen and (max-width: 768px) {
  .upload-dialog :deep(.el-dialog) {
    width: 95% !important;
    margin: 0 auto !important;
  }

  .upload-dialog :deep(.el-dialog__body) {
    padding: 20px;
  }

  .upload-container {
    padding: 16px;
  }

  .upload-container h4 {
    font-size: 14px;
  }

  .upload-container :deep(.el-upload-dragger) {
    padding: 30px 16px;
  }

  .upload-container :deep(.el-table__header th) {
    padding: 12px;
    font-size: 13px;
  }

  .upload-container :deep(.el-table__body td) {
    padding: 12px;
    font-size: 13px;
  }

  .file-info {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}

/* 资源中心样式 */
.template-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  animation: dialog-fade-in 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.template-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%) !important;
  color: white !important;
  padding: 24px 32px;
  margin: 0;
  border-radius: 0;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.template-dialog :deep(.el-dialog__title) {
  color: white !important;
  font-weight: 700;
  font-size: 20px;
  position: relative;
  z-index: 1;
  text-align: center;
  margin: 0;
}

.template-dialog :deep(.el-dialog__headerbtn) {
  top: 20px;
  right: 24px;
}

.template-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
  font-size: 22px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  width: 32px;
  height: 32px;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.template-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  background: rgba(255, 255, 255, 0.4);
  transform: rotate(90deg);
  box-shadow: 0 4px 12px rgba(255, 255, 255, 0.4);
}

.template-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  background: rgba(255, 255, 255, 0.4);
  transform: rotate(90deg);
  box-shadow: 0 4px 12px rgba(255, 255, 255, 0.4);
}

.template-dialog :deep(.el-dialog__body) {
  padding: 32px;
  background: white;
  position: relative;
}

.template-dialog :deep(.el-dialog__body::before) {
  content: '';
  position: absolute;
  top: 0;
  left: 32px;
  right: 32px;
  height: 1px;
  background: linear-gradient(90deg, transparent 0%, #e2e8f0 50%, transparent 100%);
}

.template-container {
  max-height: 500px;
  overflow-y: auto;
  background: #f5f7fa;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #e8e8e8;
  box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.03);
}


/* 模板卡片样式 */
.template-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  padding: 0;
}

.template-card {
  background: white;
  border-radius: 10px;
  padding: 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border: 1px solid #e8e8e8;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

.template-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, #409eff 0%, #67c23a 100%);
  transform: scaleX(0);
  transition: transform 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.template-card:hover {
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.18);
  border-color: #409eff;
  transform: translateY(-3px);
}

.template-card:hover::before {
  transform: scaleX(1);
}

.template-card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 2px solid #f0f2f5;
}

.template-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 3px 8px rgba(64, 158, 255, 0.2);
}

.template-card:hover .template-icon {
  transform: scale(1.1) rotate(5deg);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

.template-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.3s ease;
}

.template-card:hover .template-name {
  color: #409eff;
}

.template-card-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-bottom: 14px;
}

.template-description {
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
  margin-bottom: 14px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  transition: color 0.3s ease;
}

.template-card:hover .template-description {
  color: #303133;
}

.template-meta {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  background: linear-gradient(135deg, #fafbfc 0%, #f5f7fa 100%);
  padding: 12px;
  border-radius: 8px;
  border: 1px solid #e8e8e8;
  margin-top: auto;
  position: relative;
  overflow: hidden;
}

.template-meta::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, #409eff 0%, #67c23a 100%);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.template-card:hover .template-meta::before {
  opacity: 1;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  transition: all 0.3s ease;
}

.meta-item:hover {
  transform: translateX(3px);
}

.meta-label {
  font-weight: 600;
  color: #606266;
  min-width: 55px;
  flex-shrink: 0;
}

.meta-value {
  color: #303133;
  font-weight: 500;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-card-footer {
  display: flex;
  justify-content: center;
  padding-top: 14px;
  border-top: 2px solid #f0f2f5;
  position: relative;
}

.template-card-footer::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 50px;
  height: 2px;
  background: linear-gradient(90deg, transparent, #409eff, transparent);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.template-card:hover .template-card-footer::before {
  opacity: 1;
}

.template-card :deep(.el-button--primary) {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  border: none;
  color: white;
  border-radius: 8px;
  padding: 8px 20px;
  font-weight: 600;
  font-size: 12px;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  min-width: 90px;
  box-shadow: 0 3px 8px rgba(64, 158, 255, 0.25);
  position: relative;
  overflow: hidden;
}

.template-card :deep(.el-button--primary::before) {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.3), transparent);
  transition: left 0.6s ease;
}

.template-card :deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, #66b1ff 0%, #85ce61 100%);
  transform: translateY(-2px) scale(1.02);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.35);
}

.template-card :deep(.el-button--primary:hover::before) {
  left: 100%;
}

.template-card :deep(.el-button--primary:active) {
  transform: translateY(-1px) scale(1);
  box-shadow: 0 3px 10px rgba(64, 158, 255, 0.3);
}

/* 响应式调整 */
@media screen and (max-width: 768px) {
  .template-cards {
    grid-template-columns: 1fr;
    gap: 14px;
  }

  .template-card {
    padding: 14px;
  }

  .template-icon {
    width: 36px;
    height: 36px;
  }

  .template-card :deep(.el-button--primary) {
    padding: 8px 20px;
    min-width: 90px;
  }
}

@media screen and (max-width: 480px) {
  .template-container {
    padding: 14px;
  }

  .template-card {
    padding: 12px;
  }

  .template-name {
    font-size: 13px;
  }

  .template-description {
    font-size: 11px;
    -webkit-line-clamp: 2;
  }

  .meta-item {
    font-size: 10px;
    gap: 5px;
  }

  .meta-label {
    min-width: 50px;
  }
}

/* 添加卡片加载动画 */
@keyframes card-fade-in {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.template-card {
  animation: card-fade-in 0.5s ease-out;
}

.template-card:nth-child(1) {
  animation-delay: 0.1s;
}

.template-card:nth-child(2) {
  animation-delay: 0.2s;
}

.template-card:nth-child(3) {
  animation-delay: 0.3s;
}

.template-card:nth-child(4) {
  animation-delay: 0.4s;
}

/* 平板设备调整 */
@media screen and (min-width: 769px) and (max-width: 1024px) {
  .template-cards {
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  }
}

/* 大屏设备优化 */
@media screen and (min-width: 1200px) {
  .template-cards {
    grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
  }

  .template-card {
    padding: 28px;
  }

  .template-name {
    font-size: 20px;
  }

  .template-description {
    font-size: 15px;
  }
}

.template-container :deep(.el-scrollbar__wrap) {
  border-radius: 8px;
}

/* 响应式调整 */
@media screen and (max-width: 768px) {
  .template-dialog :deep(.el-dialog) {
    width: 95% !important;
    margin: 0 auto !important;
  }

  .template-dialog :deep(.el-dialog__body) {
    padding: 20px;
  }

  .template-container {
    padding: 16px;
  }

  .template-container h4 {
    font-size: 16px;
  }

  .template-container :deep(.el-table__header th) {
    padding: 12px;
    font-size: 13px;
  }

  .template-container :deep(.el-table__body td) {
    padding: 12px;
    font-size: 13px;
  }
}

/* 响应式调整 */
@media screen and (max-width: 768px) {
  .upload-dialog :deep(.el-dialog) {
    width: 90% !important;
    margin: 0 auto !important;
  }

  .template-dialog :deep(.el-dialog) {
    width: 90% !important;
    margin: 0 auto !important;
  }

  .upload-dialog :deep(.el-dialog__body) {
    padding: 20px;
  }

  .template-dialog :deep(.el-dialog__body) {
    padding: 20px;
  }

  .file-info {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}

/* 我的收藏对话框样式 */
.favorites-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  animation: dialog-fade-in 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.favorites-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  color: white;
  padding: 24px 32px;
  margin: 0;
  border-radius: 0;
  position: relative;
}

.favorites-dialog :deep(.el-dialog__title) {
  color: white;
  font-weight: 700;
  font-size: 20px;
}

.favorites-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
  font-size: 22px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  width: 32px;
  height: 32px;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.favorites-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  background: rgba(255, 255, 255, 0.4);
  transform: rotate(90deg);
  box-shadow: 0 4px 12px rgba(255, 255, 255, 0.4);
}

.favorites-dialog :deep(.el-dialog__body) {
  padding: 24px;
  background: white;
}

.favorites-container {
  max-height: 500px;
  overflow-y: auto;
}

/* 资源中心对话框样式 */
.resource-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.resource-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #409eff 0%, #52c41a 100%);
  color: white;
  padding: 20px 32px;
  margin: 0;
}

.resource-dialog :deep(.el-dialog__title) {
  color: white;
  font-weight: 700;
  font-size: 20px;
}

.resource-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
  font-size: 22px;
}

.resource-dialog :deep(.el-dialog__body) {
  padding: 24px;
  background: white;
}

.resource-search-bar {
  margin-bottom: 20px;
}

.resource-search-bar .el-input {
  width: 300px;
}

.resource-container {
  min-height: 400px;
}

.resources-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.resource-card {
  background: white;
  border: 1px solid #e6f7ff;
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.resource-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 25px rgba(64, 158, 255, 0.15);
  border-color: #409EFF;
}

.resource-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.resource-type {
  flex: 1;
}

.resource-content {
  margin-bottom: 16px;
}

.resource-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 12px 0;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.resource-description {
  font-size: 14px;
  color: #606266;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin: 0;
}

.resource-footer {
  border-top: 1px solid #f0f7ff;
  padding-top: 12px;
}

.resource-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.resource-meta .meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
}

.resource-meta .meta-item .el-icon {
  font-size: 14px;
}

.empty-resources {
  padding: 60px 0;
  text-align: center;
}


.favorite-item {
  background: white;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
  border: 1px solid #e8e8e8;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
}

.favorite-item:hover {
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.15);
  border-color: #409eff;
}

.favorite-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 2px solid #f0f2f5;
}

.favorite-icon {
  color: #f56c6c;
}

.favorite-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  flex: 1;
}

.favorite-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.favorite-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.info-label {
  font-weight: 600;
  color: #606266;
  min-width: 60px;
}

.info-value {
  color: #303133;
  font-weight: 500;
}

.favorite-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin: 8px 0;
}

.favorite-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid #f0f2f5;
}

.collect-time {
  font-size: 13px;
  color: #909399;
}

/* 实习档案对话框样式 */
.archives-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.12);
  animation: dialog-fade-in 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  margin: 5vh auto 0;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
}

.archives-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  color: white;
  padding: 0;
  margin: 0;
  border-radius: 0;
  position: relative;
}

.archives-dialog :deep(.el-dialog__title) {
  display: none;
}

.archives-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
  font-size: 20px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  width: 32px;
  height: 32px;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.3);
  top: 16px;
  right: 16px;
}

.archives-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  background: rgba(255, 255, 255, 0.35);
  transform: rotate(90deg);
  box-shadow: 0 4px 12px rgba(255, 255, 255, 0.4);
}

.archives-dialog :deep(.el-dialog__body) {
  padding: 0;
  background: #ffffff;
  flex: 1;
  overflow-y: auto;
}

.archives-header {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  padding: 20px 24px;
  color: white;
}

.archives-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
}

.archives-container {
  max-height: 500px;
  overflow-y: auto;
  padding: 16px;
}


.archive-item {
  background: #ffffff;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 12px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: all 0.3s ease;
}

.archive-item:hover {
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.12);
  border-color: #409eff;
  transform: translateY(-2px);
}

.archive-main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f1f5f9;
}

.archive-left {
  flex: 1;
}

.company-name {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 4px;
}

.company-position {
  font-size: 14px;
  color: #64748b;
}

.archive-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.archive-status {
  margin-bottom: 4px;
}

.archive-rating {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.rating-score {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
}

.rating-label {
  font-size: 14px;
  color: #64748b;
}

.archive-details {
  display: flex;
  gap: 24px;
  margin-bottom: 16px;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #64748b;
}

.archive-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}

/* 简历管理对话框样式 */
.resume-management-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  animation: dialog-fade-in 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.resume-management-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  color: white;
  padding: 24px 32px;
  margin: 0;
  border-radius: 0;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.resume-management-dialog :deep(.el-dialog__title) {
  color: white;
  font-weight: 700;
  font-size: 20px;
  position: relative;
  z-index: 1;
  text-align: center;
  margin: 0;
}

.resume-management-dialog :deep(.el-dialog__headerbtn) {
  top: 20px;
  right: 24px;
}

.resume-management-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
  font-size: 22px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  width: 32px;
  height: 32px;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.resume-management-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  background: rgba(255, 255, 255, 0.4);
  transform: rotate(90deg);
  box-shadow: 0 4px 12px rgba(255, 255, 255, 0.4);
}

.resume-management-dialog :deep(.el-dialog__body) {
  padding: 32px;
  background: white;
  position: relative;
}

.resume-management-dialog :deep(.el-dialog__body::before) {
  content: '';
  position: absolute;
  top: 0;
  left: 32px;
  right: 32px;
  height: 1px;
  background: linear-gradient(90deg, transparent 0%, #e2e8f0 50%, transparent 100%);
}

.resume-management-container {
  max-height: 500px;
  overflow-y: auto;
}


.resume-section,
.certificate-section {
  margin-bottom: 32px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 20px;
  padding-bottom: 16px;
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
}

.section-title .upload-btn {
  margin-left: auto;
}

.empty-tip {
  text-align: center;
  padding: 32px;
  color: #909399;
  font-size: 14px;
}

.upload-area {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px dashed #e2e8f0;
}

.upload-area .upload-demo {
  margin-bottom: 16px;
}

.upload-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}

.resume-item,
.certificate-item {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 12px;
  margin-bottom: 16px;
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.resume-item:hover,
.certificate-item:hover {
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.15);
  border-color: #409eff;
  transform: translateY(-2px);
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
}

.resume-icon,
.cert-icon {
  color: #409eff;
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15);
}

.resume-info,
.cert-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.resume-name,
.cert-name {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.resume-meta,
.cert-meta {
  font-size: 14px;
  color: #64748b;
  display: flex;
  gap: 20px;
}

.resume-actions,
.cert-actions {
  display: flex;
  gap: 12px;
}

.resume-actions .el-button,
.cert-actions .el-button {
  border-radius: 8px;
  padding: 8px 16px;
  font-weight: 600;
  transition: all 0.3s ease;
}

.resume-actions .el-button--primary,
.cert-actions .el-button--primary {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  border: none;
}

.resume-actions .el-button--primary:hover,
.cert-actions .el-button--primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.3);
}

.resume-actions .el-button--danger,
.cert-actions .el-button--danger {
  background: #f56c6c;
  border: none;
}

.resume-actions .el-button--danger:hover,
.cert-actions .el-button--danger:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(245, 108, 108, 0.3);
}

/* 消息中心对话框样式 */
.message-center-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  animation: dialog-fade-in 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.message-center-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  color: white;
  padding: 24px 32px;
  margin: 0;
  border-radius: 0;
  position: relative;
}

.message-center-dialog :deep(.el-dialog__title) {
  color: white;
  font-weight: 700;
  font-size: 20px;
}

.message-center-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
  font-size: 22px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  width: 32px;
  height: 32px;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.message-center-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  background: rgba(255, 255, 255, 0.4);
  transform: rotate(90deg);
  box-shadow: 0 4px 12px rgba(255, 255, 255, 0.4);
}

.message-center-dialog :deep(.el-dialog__body) {
  padding: 24px;
  background: white;
}

.message-center-container {
  max-height: 500px;
  overflow-y: auto;
}


.message-item {
  background: white;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
  border: 1px solid #e8e8e8;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
  cursor: pointer;
}

.message-item:hover {
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.15);
  border-color: #409eff;
}

.message-item.unread {
  border-left: 4px solid #f56c6c;
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f2f5;
}

.message-sender {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sender-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.message-time {
  font-size: 13px;
  color: #909399;
}

.message-content {
  margin-bottom: 12px;
}

.message-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.message-text {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
}

.message-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.message-meta-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.priority-tag {
  flex-shrink: 0;
}

.target-info {
  font-size: 13px;
  color: #909399;
}

.read-count {
  font-size: 13px;
  color: #909399;
}

/* 公告详情对话框样式 */
.announcement-detail-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.announcement-detail-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  color: white;
  padding: 20px 24px;
  margin: 0;
}

.announcement-detail-dialog :deep(.el-dialog__title) {
  color: white;
  font-weight: 700;
  font-size: 18px;
}

.announcement-detail-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
}

.announcement-detail-dialog :deep(.el-dialog__body) {
  padding: 24px;
}

.announcement-detail {
  padding: 0;
}

.detail-title {
  font-size: 20px;
  font-weight: bold;
  color: #303133;
  margin: 0 0 16px 0;
  text-align: center;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 14px;
  color: #909399;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #f8f9fa;
  border-radius: 8px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.meta-label {
  font-weight: 500;
  color: #606266;
}

.meta-value {
  color: #303133;
}

.detail-content {
  font-size: 15px;
  line-height: 1.8;
  color: #606266;
  padding: 16px;
  background: white;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  min-height: 150px;
}

.detail-content :deep(img) {
  max-width: 100%;
  height: auto;
}

.attachments-section {
  margin-top: 20px;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 8px;
}

.attachments-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #303133;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 12px;
}

.attachments-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.attachment-tag {
  cursor: pointer;
  padding: 8px 16px;
  background: white;
  border: 1px solid #dcdfe6;
  color: #409EFF;
  transition: all 0.3s ease;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.attachment-tag:hover {
  background: #409EFF;
  color: white;
  border-color: #409EFF;
  transform: translateY(-2px);
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);
}

.attachment-icon {
  margin-right: 2px;
}

.preview-icon {
  font-size: 14px;
  opacity: 0.8;
}

/* 通用对话框样式 */
.favorites-dialog :deep(.el-dialog),
.archives-dialog :deep(.el-dialog),
.resume-management-dialog :deep(.el-dialog),
.message-center-dialog :deep(.el-dialog) {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.15);
  border: 1px solid #e2e8f0;
}

.weekly-logs-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.12);
  animation: dialog-fade-in 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  margin: 5vh auto 0;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
}

.weekly-logs-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  color: white;
  padding: 0;
  margin: 0;
  border-radius: 0;
  position: relative;
}

.weekly-logs-dialog :deep(.el-dialog__title) {
  display: none;
}

.weekly-logs-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
  font-size: 20px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  width: 32px;
  height: 32px;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.3);
  top: 16px;
  right: 16px;
}

.weekly-logs-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  background: rgba(255, 255, 255, 0.35);
  transform: rotate(90deg);
  box-shadow: 0 4px 12px rgba(255, 255, 255, 0.4);
}

.weekly-logs-dialog :deep(.el-dialog__body) {
  padding: 0;
  background: #ffffff;
  flex: 1;
  overflow-y: auto;
}

/* 我的收藏对话框样式 */
.favorites-container {
  max-height: 60vh;
  overflow-y: auto;
  padding: 8px;
}

.favorites-empty {
  text-align: center;
  padding: 60px 20px;
  color: #94a3b8;
  background: #f8fafc;
  border-radius: 12px;
  border: 1px solid #f1f5f9;
  margin: 20px 0;
}

.favorites-empty .empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  color: #cbd5e1;
}

.favorites-empty .empty-text {
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 8px;
  color: #64748b;
}

.favorites-empty .empty-desc {
  font-size: 14px;
  color: #94a3b8;
  line-height: 1.5;
}

.favorite-item {
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
  border: 1px solid #f0f2f5;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.favorite-item::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: linear-gradient(180deg, #409eff 0%, #67c23a 100%);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.favorite-item:hover {
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.15);
  transform: translateY(-4px);
  border-color: #409eff;
}

.favorite-item:hover::before {
  opacity: 1;
}

.favorite-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f2f5;
}

.favorite-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
  flex-shrink: 0;
}

.favorite-title {
  font-size: 20px;
  font-weight: 600;
  color: #1e293b;
  flex: 1;
  letter-spacing: 0.3px;
}

.favorite-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 20px;
}

.favorite-info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.favorite-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #f1f5f9;
  transition: all 0.3s ease;
}

.favorite-info:hover {
  background: #f0f9ff;
  border-color: #409eff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.1);
}

.favorite-info .info-label {
  font-size: 12px;
  color: #94a3b8;
  font-weight: 600;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.favorite-info .info-value {
  font-size: 14px;
  color: #1e293b;
  font-weight: 500;
  line-height: 1.4;
}

.favorite-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #f1f5f9;
}

.favorite-tags :deep(.el-tag) {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  color: white;
  border: none;
  border-radius: 16px;
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 500;
  box-shadow: 0 2px 6px rgba(64, 158, 255, 0.2);
  transition: all 0.3s ease;
}

.favorite-tags :deep(.el-tag:hover) {
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(64, 158, 255, 0.3);
}

.favorite-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;
}

.collect-time {
  font-size: 12px;
  color: #94a3b8;
  font-weight: 500;
}

.favorite-footer :deep(.el-button--danger) {
  background: linear-gradient(135deg, #f56c6c 0%, #e6a23c 100%);
  border: none;
  border-radius: 6px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  box-shadow: 0 2px 8px rgba(245, 108, 108, 0.2);
  transition: all 0.3s ease;
}

.favorite-footer :deep(.el-button--danger:hover) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(245, 108, 108, 0.3);
}

.favorite-footer .footer-buttons {
  display: flex;
  gap: 8px;
}

.favorite-footer .footer-buttons :deep(.el-button) {
  padding: 6px 12px;
  font-size: 12px;
  border-radius: 6px;
}

/* 职位详情对话框样式 */
.job-detail-dialog :deep(.el-dialog) {
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  animation: dialog-fade-in 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  margin-top: 8vh !important;
}

.job-detail-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  color: white;
  padding: 24px 32px;
  margin: 0;
  border-radius: 0;
  position: relative;
}

.job-detail-dialog :deep(.el-dialog__header::after) {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.3) 0%, rgba(255, 255, 255, 0.8) 50%, rgba(255, 255, 255, 0.3) 100%);
}

.job-detail-dialog :deep(.el-dialog__title) {
  color: white;
  font-weight: 700;
  font-size: 20px;
  position: relative;
  z-index: 1;
}

.job-detail-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: white;
  font-size: 22px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  width: 32px;
  height: 32px;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.job-detail-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  background: rgba(255, 255, 255, 0.4);
  transform: rotate(90deg);
  box-shadow: 0 4px 12px rgba(255, 255, 255, 0.4);
}

.job-detail-dialog :deep(.el-dialog__body) {
  padding: 24px;
  background: white;
  max-height: 70vh;
  overflow-y: auto;
}

.job-detail-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding-bottom: 16px;
  border-bottom: 2px solid #f0f2f5;
}

.detail-title {
  flex: 1;
}

.detail-title h3 {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  margin: 0 0 12px 0;
}

.detail-actions {
  flex-shrink: 0;
}

.detail-actions .favorite-icon {
  font-size: 28px;
  color: #cbd5e1;
  cursor: pointer;
  transition: all 0.3s ease;
}

.detail-actions .favorite-icon:hover {
  color: #fbbf24;
}

.detail-actions .favorite-icon.active {
  color: #fbbf24;
}

.detail-section {
  background: #fafbfc;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #e8e8e8;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 16px 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-title::before {
  content: '';
  width: 4px;
  height: 16px;
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  border-radius: 2px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.info-item .el-icon {
  color: #409EFF;
  font-size: 18px;
}

.info-label {
  font-weight: 600;
  color: #606266;
}

.info-value {
  color: #303133;
}

.detail-description {
  font-size: 14px;
  color: #606266;
  line-height: 1.8;
  margin: 0;
}

.detail-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.detail-list li {
  font-size: 14px;
  color: #606266;
  line-height: 1.8;
  padding-left: 20px;
  position: relative;
}

.detail-list li::before {
  content: '•';
  position: absolute;
  left: 0;
  color: #409EFF;
  font-weight: bold;
}

.contact-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.contact-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}

.contact-label {
  font-weight: 600;
  color: #606266;
  min-width: 60px;
}

.contact-value {
  color: #303133;
}

.stats-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}

.stat-label {
  font-weight: 600;
  color: #606266;
  min-width: 80px;
}

.stat-value {
  color: #303133;
}

.detail-footer {
  display: flex;
  justify-content: center;
  padding-top: 16px;
  border-top: 2px solid #f0f2f5;
}

.detail-apply-button {
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 100%);
  border: none;
  padding: 12px 48px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
  transition: all 0.3s ease;
}

.detail-apply-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.4);
}

.detail-applied-button {
  background: #67C23A;
  border: none;
  padding: 12px 48px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 8px;
}

/* 实习档案对话框样式 */
.archives-container {
  max-height: 500px;
  overflow-y: auto;
}

.archive-item {
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
  border: 1px solid #f0f2f5;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.archive-item::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: linear-gradient(180deg, #409eff 0%, #67c23a 100%);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.archive-item:hover {
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.15);
  transform: translateY(-4px);
  border-color: #409eff;
}

.archive-item:hover::before {
  opacity: 1;
}

.archive-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f2f5;
}

.archive-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, #67c23a 0%, #409eff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: 0 4px 12px rgba(103, 194, 58, 0.3);
  flex-shrink: 0;
}

.archive-title {
  font-size: 20px;
  font-weight: 600;
  color: #1e293b;
  flex: 1;
  letter-spacing: 0.3px;
}

.archive-body {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.archive-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px;
  background: #f8fafc;
  border-radius: 10px;
  border: 1px solid #f1f5f9;
  transition: all 0.3s ease;
  min-height: 80px;
}

.archive-info:hover {
  background: #f0f9ff;
  border-color: #409eff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.1);
}

.archive-info .info-label {
  font-size: 12px;
  color: #94a3b8;
  font-weight: 600;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.archive-info .info-value {
  font-size: 14px;
  color: #1e293b;
  font-weight: 500;
  line-height: 1.4;
}

.archive-info .rating-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 4px;
}

.score-display {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.score-number {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
}

.score-unit {
  font-size: 14px;
  color: #64748b;
  font-weight: 500;
}

.score-bar {
  width: 100%;
  height: 8px;
  background: #f1f5f9;
  border-radius: 4px;
  overflow: hidden;
  margin-top: 4px;
}

.score-progress {
  height: 100%;
  background: linear-gradient(90deg, #4ade80 0%, #22c55e 100%);
  border-radius: 4px;
  transition: width 0.3s ease;
}

.supervisor-rating {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}

.supervisor-rating .rating-label {
  font-size: 14px;
  color: #64748b;
  font-weight: 600;
  margin-bottom: 4px;
}



.archive-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f0f2f5;
}

.archive-footer :deep(.el-button--primary) {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  border: none;
  border-radius: 8px;
  padding: 10px 24px;
  font-size: 14px;
  font-weight: 500;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2);
  transition: all 0.3s ease;
}

.archive-footer :deep(.el-button--primary:hover) {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.3);
}

.archive-footer :deep(.el-button--danger) {
  background: linear-gradient(135deg, #f56c6c 0%, #e6a23c 100%);
  border: none;
  border-radius: 8px;
  padding: 10px 24px;
  font-size: 14px;
  font-weight: 500;
  box-shadow: 0 4px 12px rgba(245, 108, 108, 0.2);
  transition: all 0.3s ease;
}

.archive-footer :deep(.el-button--danger:hover) {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(245, 108, 108, 0.3);
}

/* 周日志对话框样式 */
.weekly-logs-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  color: white;
}

.weekly-logs-header :deep(.el-button--text) {
  color: white;
  font-size: 14px;
  font-weight: 500;
  padding: 8px 16px;
  border-radius: 6px;
  transition: all 0.3s ease;
}

.weekly-logs-header :deep(.el-button--text:hover) {
  background: rgba(255, 255, 255, 0.2);
}

.weekly-logs-title {
  font-size: 16px;
  font-weight: 600;
}

.weekly-logs-container {
  max-height: 600px;
  overflow-y: auto;
  padding: 16px;
}


/* 周次导航样式 */
.week-navigation {
  display: flex;
  align-items: center;
  gap: 16px;
}

.current-week-indicator {
  font-size: 14px;
  font-weight: 500;
  min-width: 120px;
  text-align: center;
}

.week-navigation :deep(.el-button) {
  padding: 6px 12px;
  font-size: 13px;
}

/* 附件列表样式 */
.attachment-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e8e8e8;
}

.attachment-label {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 12px;
}

.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.attachment-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
  cursor: pointer;
}

.attachment-item:hover {
  border-color: #409eff;
  background: #eff6ff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
}

.attachment-item > div {
  display: flex;
  align-items: center;
  gap: 12px;
}

.attachment-icon {
  font-size: 20px;
  color: #409eff;
}

.attachment-name {
  font-size: 14px;
  color: #1e293b;
  font-weight: 500;
}

.attachment-size {
  font-size: 12px;
  color: #94a3b8;
  margin-left: 8px;
}

/* 空状态样式 */
.empty-weekly-logs {
  text-align: center;
  padding: 60px 20px;
  color: #94a3b8;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
  opacity: 0.5;
}

.empty-text {
  font-size: 14px;
}

.weekly-log-item {
  background: #ffffff;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: all 0.3s ease;
}

.weekly-log-item:hover {
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.12);
  border-color: #409eff;
  transform: translateY(-2px);
}

.log-top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f1f5f9;
}

.log-week {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.log-date {
  font-size: 14px;
  color: #64748b;
  flex: 1;
}

.log-info {
  display: flex;
  gap: 20px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #64748b;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.log-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.content-item {
  background: #f8fafc;
  border-radius: 8px;
  padding: 12px 16px;
  border: 1px solid #e2e8f0;
}

.content-item.feedback-item {
  background: #f0f9ff;
  border-color: #bfdbfe;
}

.content-label {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 6px;
}

.content-text {
  font-size: 14px;
  color: #1e293b;
  line-height: 1.6;
  margin: 0;
}

.feedback-rating {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #bfdbfe;
}

.feedback-rating .rating-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.feedback-rating .rating-score {
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
}

.feedback-rating .rating-unit {
  font-size: 14px;
  color: #64748b;
}

/* 简历管理对话框样式 */
.resume-management-container {
  max-height: 500px;
  overflow-y: auto;
}

.resume-section,
.certificate-section {
  margin-bottom: 24px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f1f5f9;
}

.resume-item,
.certificate-item {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #ffffff;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
}

.resume-item:hover,
.certificate-item:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.resume-icon,
.cert-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}

.resume-info,
.cert-info {
  flex: 1;
}

.resume-name,
.cert-name {
  font-size: 16px;
  font-weight: 500;
  color: #1e293b;
  margin-bottom: 4px;
}

.resume-meta,
.cert-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #94a3b8;
}

.resume-actions,
.cert-actions {
  display: flex;
  gap: 8px;
}

/* 消息中心对话框样式 */
.message-center-container {
  max-height: 500px;
  overflow-y: auto;
}

.message-item {
  background: #ffffff;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
  cursor: pointer;
}

.message-item:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.message-item.unread {
  border-left: 4px solid #f56c6c;
  background: #fef2f2;
}

.message-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.message-sender {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sender-name {
  font-size: 14px;
  font-weight: 500;
  color: #1e293b;
}

.message-time {
  font-size: 12px;
  color: #94a3b8;
}

.message-content {
  margin-bottom: 12px;
}

.message-title {
  font-size: 16px;
  font-weight: 500;
  color: #1e293b;
  margin-bottom: 8px;
}

.message-text {
  font-size: 14px;
  color: #64748b;
  line-height: 1.5;
}

.message-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.message-meta-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.priority-tag {
  flex-shrink: 0;
}

.target-info {
  font-size: 13px;
  color: #94a9b8;
}

.read-count {
  font-size: 13px;
  color: #94a9b8;
}

/* 验证码输入组样式 */
.code-input-group {
  display: flex;
  gap: 12px;
}

.code-input-group .el-input {
  flex: 1;
}

/* 按钮样式统一 */
.favorites-dialog :deep(.el-button),
.archives-dialog :deep(.el-button),
.resume-management-dialog :deep(.el-button),
.message-center-dialog :deep(.el-button) {
  border-radius: 8px;
  padding: 10px 24px;
  font-size: 15px;
  font-weight: 600;
  transition: all 0.3s ease;
}

.favorites-dialog :deep(.el-button--primary),
.archives-dialog :deep(.el-button--primary),
.resume-management-dialog :deep(.el-button--primary),
.message-center-dialog :deep(.el-button--primary) {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.25);
}

.favorites-dialog :deep(.el-button--primary:hover),
.archives-dialog :deep(.el-button--primary:hover),
.resume-management-dialog :deep(.el-button--primary:hover),
.message-center-dialog :deep(.el-button--primary:hover) {
  opacity: 0.95;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.35);
}


/* 响应式设计 */
@media screen and (max-width: 768px) {
  .favorite-item,
  .archive-item,
  .resume-item,
  .certificate-item,
  .weekly-log-item {
    padding: 16px;
  }
  
  .favorite-body,
  .archive-body {
    margin-left: 0;
  }
  
  .resume-item,
  .certificate-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  
  .resume-actions,
  .cert-actions {
    align-self: flex-end;
  }
  
  .code-input-group {
    flex-direction: column;
  }

  .favorite-body {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .archive-body {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .log-content {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .archive-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .archive-footer :deep(.el-button) {
    width: 100%;
  }
}
</style>

<style>
/* Blob文件预览对话框样式 */
.blob-preview-dialog {
  border-radius: 16px;
}

.blob-preview-dialog :deep(.el-dialog__header) {
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  padding: 20px 24px;
  border-bottom: 1px solid #e0f2fe;
}

.blob-preview-dialog :deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 600;
  color: #1e40af;
}

.blob-preview-container {
  min-height: 500px;
  display: flex;
  justify-content: center;
  align-items: center;
  background: #f5f5f5;
  border-radius: 8px;
}

.blob-iframe {
  width: 100%;
  height: 70vh;
  border: none;
  border-radius: 8px;
  background: white;
}

.blob-preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #909399;
  font-size: 14px;
}
</style>

<style>
/* 编辑个人资料对话框样式 - 全局作用域 */
[data-custom-dialog="edit-profile"].el-dialog {
  border-radius: 16px !important;
  overflow: hidden !important;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15) !important;
  animation: dialog-fade-in 0.3s cubic-bezier(0.4, 0, 0.2, 1) !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-dialog__header {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%) !important;
  color: white !important;
  padding: 24px 32px !important;
  margin: 0 !important;
  border-radius: 0 !important;
  position: relative !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-dialog__title {
  color: white !important;
  font-weight: 700 !important;
  font-size: 20px !important;
  position: relative !important;
  z-index: 1 !important;
  text-align: center !important;
  margin: 0 !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-dialog__headerbtn {
  top: 20px !important;
  right: 24px !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-dialog__headerbtn {
  top: 16px !important;
  right: 16px !important;
  width: 20px !important;
  height: 20px !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-dialog__headerbtn .el-dialog__close {
  color: rgba(255, 255, 255, 0.7) !important;
  font-size: 16px !important;
  width: 20px !important;
  height: 20px !important;
  line-height: 20px !important;
  transition: all 0.2s ease !important;
  background: transparent !important;
  border: none !important;
  padding: 0 !important;
  margin: 0 !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-dialog__headerbtn .el-dialog__close:hover {
  color: white !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-dialog__body {
  padding: 32px !important;
  background: white !important;
  position: relative !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-dialog__body::before {
  content: '' !important;
  position: absolute !important;
  top: 0 !important;
  left: 32px !important;
  right: 32px !important;
  height: 1px !important;
  background: linear-gradient(90deg, transparent 0%, #e2e8f0 50%, transparent 100%) !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-dialog__footer {
  padding: 20px 32px !important;
  background: #fafafa !important;
  border-top: 1px solid #f0f0f0 !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-form-item__label {
  font-weight: 600 !important;
  color: #1e293b !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-input__wrapper {
  border-radius: 8px !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  transition: all 0.3s ease !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-input__wrapper:hover {
  box-shadow: 0 0 0 1px #409eff inset !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-input__wrapper.is-focus {
  box-shadow: 0 0 0 1px #409eff inset !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-textarea__inner {
  border-radius: 8px !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  transition: all 0.3s ease !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-textarea__inner:hover {
  box-shadow: 0 0 0 1px #409eff inset !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-textarea__inner:focus {
  box-shadow: 0 0 0 1px #409eff inset !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-select__wrapper {
  border-radius: 8px !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  transition: all 0.3s ease !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-select__wrapper:hover {
  box-shadow: 0 0 0 1px #409eff inset !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-select__wrapper.is-focus {
  box-shadow: 0 0 0 1px #409eff inset !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-button--primary {
  background: linear-gradient(135deg, #409eff 0%, #67c23a 100%) !important;
  border: none !important;
  border-radius: 8px !important;
  padding: 10px 24px !important;
  font-weight: 600 !important;
  transition: all 0.3s ease !important;
}

[data-custom-dialog="edit-profile"].el-dialog .el-button--primary:hover {
  transform: translateY(-2px) !important;
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.3) !important;
}
</style>
