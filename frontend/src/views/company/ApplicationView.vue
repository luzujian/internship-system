<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Document, Picture, Folder, Loading } from '@element-plus/icons-vue'
import { exportToExcel, createWorkbook, jsonToSheet, appendSheet, writeWorkbook, writeWorkbookToBuffer } from '../../utils/xlsx'
import { usePositionStore } from '../../store/position'
import { useAuthStore } from '@/store/auth'
import studentArchiveService from '../../api/StudentArchiveService'

const positionStore = usePositionStore()
const authStore = useAuthStore()

const companyId = computed(() => {
  if (authStore.user?.id) {
    return parseInt(authStore.user.id)
  }
  // 当无法获取企业 ID 时，尝试从 localStorage 获取
  const storedCompanyId = localStorage.getItem('company_companyId_COMPANY')
  if (storedCompanyId) {
    return parseInt(storedCompanyId)
  }
  // 如果仍然无法获取，返回 null 并显示错误提示
  ElMessage.error('未获取到当前登录企业 ID，请重新登录')
  return null
})

onMounted(async () => {
  loading.value = true
  if (!companyId.value) {
    ElMessage.error('未获取到企业 ID，无法加载数据')
    loading.value = false
    return
  }
  try {
    await positionStore.fetchPositions(companyId.value)
    await positionStore.fetchApplications(companyId.value)
  } finally {
    loading.value = false
  }
})

const searchForm = ref({
  keyword: '',
  position: '',
  status: ''
})

const positionOptions = computed(() => {
  return positionStore.positions.map(pos => ({ label: pos.positionName, value: pos.positionName }))
})

const statusOptions = [
  { label: '未查看', value: false },
  { label: '已查看', value: true }
]

const loading = ref(false)
const exporting = ref(false)
const exportMode = ref('excel')

const detailDialogVisible = ref(false)
const currentStudent = ref(null)
const previewDialogVisible = ref(false)
const previewFile = ref(null)

const groupedApplications = computed(() => {
  const groups = {}
  
  if (positionStore.positions && Array.isArray(positionStore.positions)) {
    positionStore.positions.forEach(pos => {
      if (pos.positionName) {
        groups[pos.positionName] = {
          applications: [],
          status: pos.status
        }
      }
    })
  }
  
  if (positionStore.applications && Array.isArray(positionStore.applications)) {
    let data = positionStore.applications
    
    if (searchForm.value.keyword) {
      data = data.filter(item => 
        item.studentName.includes(searchForm.value.keyword) || 
        item.studentId.includes(searchForm.value.keyword)
      )
    }
    
    data.forEach(item => {
      if (item.position && groups[item.position]) {
        groups[item.position].applications.push(item)
      }
    })
  }
  
  Object.keys(groups).forEach(position => {
    groups[position].applications.sort((a, b) => {
      if (a.viewed !== b.viewed) {
        return a.viewed ? 1 : -1
      }
      return new Date(b.applyDate) - new Date(a.applyDate)
    })
  })
  
  return Object.entries(groups).map(([position, group]) => ({
    position,
    applications: group.applications,
    total: group.applications.length,
    unviewed: group.applications.filter(a => !a.viewed).length,
    status: group.status
  })).sort((a, b) => {
    if (a.status === 'paused' && b.status !== 'paused') return 1
    if (a.status !== 'paused' && b.status === 'paused') return -1
    
    if (a.unviewed > 0 && b.unviewed === 0) return -1
    if (a.unviewed === 0 && b.unviewed > 0) return 1
    
    if (a.unviewed > 0 && b.unviewed > 0) return b.unviewed - a.unviewed
    
    return b.total - a.total
  })
})

const filteredTableData = computed(() => {
  if (!positionStore.applications || !Array.isArray(positionStore.applications)) {
    return []
  }
  
  let data = [...positionStore.applications]
  
  if (searchForm.value.keyword) {
    data = data.filter(item => 
      item.studentName.includes(searchForm.value.keyword) || 
      item.studentId.includes(searchForm.value.keyword)
    )
  }
  
  if (searchForm.value.position) {
    data = data.filter(item => item.position === searchForm.value.position)
  }
  
  if (searchForm.value.status === true || searchForm.value.status === false) {
    data = data.filter(item => item.viewed === searchForm.value.status)
  }
  
  data.sort((a, b) => {
    if (a.viewed !== b.viewed) {
      return a.viewed ? 1 : -1
    }
    return new Date(b.applyDate) - new Date(a.applyDate)
  })
  
  return data
})

const currentPage = ref(1)
const pageSize = ref(20)

const paginatedTableData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredTableData.value.slice(start, end)
})

const handlePageChange = (page) => {
  currentPage.value = page
}

const handlePositionClick = (position) => {
  searchForm.value.position = searchForm.value.position === position ? '' : position
  currentPage.value = 1
}

const handleSearch = () => {
  loading.value = true
  setTimeout(() => {
    loading.value = false
    ElMessage.success('筛选已应用')
  }, 500)
}

const handleReset = () => {
  searchForm.value = {
    keyword: '',
    position: '',
    status: ''
  }
  currentPage.value = 1
  ElMessage.info('已重置搜索条件')
}

const studentArchives = ref([])
const archiveLoading = ref(false)

const handleViewDetail = async (row) => {
  positionStore.markAsViewed(row.id)
  currentStudent.value = row
  detailDialogVisible.value = true
  
  console.log('查看学生详情:', row)
  console.log('studentDbId:', row.studentDbId)
  
  archiveLoading.value = true
  try {
    const response = await studentArchiveService.getStudentArchives(row.studentDbId)
    console.log('获取学生文件响应:', response)
    if (response.code === 200) {
      studentArchives.value = response.data || []
      console.log('学生文件数据:', studentArchives.value)
    }
  } catch (error) {
    console.error('获取学生文件失败:', error)
    studentArchives.value = []
  } finally {
    archiveLoading.value = false
  }
}

const handleExport = async () => {
  let dataToExport = positionStore.applications
  
  if (searchForm.value.keyword) {
    dataToExport = dataToExport.filter(item => 
      item.studentName.includes(searchForm.value.keyword) || 
      item.studentId.includes(searchForm.value.keyword)
    )
  }
  
  if (searchForm.value.position) {
    dataToExport = dataToExport.filter(item => item.position === searchForm.value.position)
  }
  
  if (searchForm.value.status === true || searchForm.value.status === false) {
    dataToExport = dataToExport.filter(item => item.viewed === searchForm.value.status)
  }
  
  if (dataToExport.length === 0) {
    ElMessage.warning('没有数据可导出')
    return
  }

  exporting.value = true

  try {
    const groupedData = {}
    dataToExport.forEach(item => {
      if (!groupedData[item.position]) {
        groupedData[item.position] = []
      }
      groupedData[item.position].push(item)
    })
    
    Object.keys(groupedData).forEach(position => {
      groupedData[position].sort((a, b) => {
        if (a.viewed !== b.viewed) {
          return a.viewed ? 1 : -1
        }
        return new Date(b.applyDate) - new Date(a.applyDate)
      })
    })
    
    const sortedData = Object.values(groupedData).flat()

    const exportData = sortedData.map(item => ({
      '应聘岗位': item.position,
      '学生姓名': item.studentName,
      '学号': item.studentId,
      '性别': item.gender,
      '年级': item.grade,
      '学校': item.school,
      '学历': item.education,
      '专业': item.major,
      '联系电话': item.phone,
      '电子邮箱': item.email,
      '申请日期': item.applyDate,
      '是否已查看': item.viewed ? '已查看' : '未查看',
      '专业技能': item.skills.join('、'),
      '项目经验': item.experience,
      '自我评价': item.selfEvaluation
    }))

    const ws = await jsonToSheet(exportData)
    const wb = await createWorkbook()
    await appendSheet(wb, ws, '岗位申请名单')

    const fileName = `岗位申请名单_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}`

    if (exportMode.value === 'zip') {
      await exportWithFiles(wb, fileName, sortedData)
    } else {
      await writeWorkbook(wb, fileName)
      ElMessage.success(`成功导出 ${dataToExport.length} 条数据`)
    }
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败，请稍后重试')
  } finally {
    exporting.value = false
  }
}

const exportWithFiles = async (workbook, baseFileName, data) => {
  try {
    const JSZip = (await import('jszip')).default
    const zip = new JSZip()

    const excelBuffer = await writeWorkbookToBuffer(workbook)
    zip.file(baseFileName + '.xlsx', excelBuffer)

    for (const item of data) {
      try {
        const archives = await studentArchiveService.getArchivesByStudentId(item.studentDbId)
        
        if (archives && archives.length > 0) {
          const studentFolder = zip.folder(`${item.studentName}_${item.studentId}`)
          
          for (const archive of archives) {
            try {
              const response = await studentArchiveService.downloadArchive(archive.id)
              const blob = new Blob([response], { type: response.type })
              const arrayBuffer = await blob.arrayBuffer()
              studentFolder.file(archive.fileName, arrayBuffer)
            } catch (fileError) {
              console.warn(`下载文件失败: ${archive.fileName}`, fileError)
            }
          }
        }
      } catch (studentError) {
        console.warn(`获取学生档案失败: ${item.studentName}`, studentError)
      }
    }

    const content = await zip.generateAsync({ type: 'blob' })
    const url = window.URL.createObjectURL(content)
    const link = document.createElement('a')
    link.href = url
    link.download = baseFileName + '.zip'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)

    ElMessage.success(`成功导出 ${data.length} 条数据及附件`)
  } catch (error) {
    console.error('导出压缩包失败:', error)
    throw error
  }
}

const getFileIcon = (fileName) => {
  const ext = fileName.split('.').pop().toLowerCase()
  if (['jpg', 'jpeg', 'png', 'gif', 'bmp'].includes(ext)) {
    return Picture
  } else if (['pdf', 'doc', 'docx', 'txt'].includes(ext)) {
    return Document
  } else {
    return Folder
  }
}

const handleDownloadFile = async (archiveId, fileName) => {
  try {
    const response = await studentArchiveService.downloadArchive(archiveId)
    
    const blob = new Blob([response], { type: response.type })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    
    ElMessage.success('下载成功')
  } catch (error) {
    console.error('下载文件失败:', error)
    ElMessage.error('下载失败，请稍后重试')
  }
}

const handlePreviewFile = async (archiveId) => {
  try {
    const archive = studentArchives.value.find(a => a.id === archiveId)
    if (!archive) {
      ElMessage.error('文件不存在')
      return
    }
    
    const ext = getFileExtension(archive.fileName).toLowerCase()
    
    if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'].includes(ext)) {
      const response = await studentArchiveService.previewArchive(archiveId)
      const blob = new Blob([response], { type: response.type })
      previewFile.value = {
        ...archive,
        blobUrl: window.URL.createObjectURL(blob)
      }
      previewDialogVisible.value = true
    } else if (['pdf'].includes(ext)) {
      const response = await studentArchiveService.previewArchive(archiveId)
      const blob = new Blob([response], { type: response.type })
      previewFile.value = {
        ...archive,
        blobUrl: window.URL.createObjectURL(blob)
      }
      previewDialogVisible.value = true
    } else {
      previewFile.value = archive
      previewDialogVisible.value = true
    }
  } catch (error) {
    console.error('预览文件失败:', error)
    ElMessage.error('预览失败，请稍后重试')
  }
}

const getArchivesByType = (fileType) => {
  return studentArchives.value.filter(archive => archive.fileType === fileType)
}

const getFileExtension = (fileName) => {
  const lastDotIndex = fileName.lastIndexOf('.')
  return lastDotIndex === -1 ? '' : fileName.substring(lastDotIndex + 1)
}
</script>

<template>
  <div class="application-view">
    <div class="page-header">
      <h2>岗位申请查看</h2>
      <p>查看学生提交的岗位申请信息</p>
    </div>

    <div class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="关键词">
          <el-input 
            v-model="searchForm.keyword" 
            placeholder="请输入学生姓名或学号" 
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="应聘岗位">
          <el-select v-model="searchForm.position" placeholder="请选择岗位" clearable style="width: 180px">
            <el-option
              v-for="item in positionOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="查看状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable style="width: 140px">
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item label="导出格式">
          <el-radio-group v-model="exportMode">
            <el-radio value="excel">仅Excel表格</el-radio>
            <el-radio value="zip">压缩包（含附件）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="success" @click="handleExport" :loading="exporting">
            <el-icon style="margin-right: 4px"><Download /></el-icon>
            {{ exportMode === 'zip' ? '导出压缩包' : '导出Excel' }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="stats-cards">
      <div 
        v-for="group in groupedApplications" 
        :key="group.position"
        class="stat-card"
        :class="{ 
          active: searchForm.position === group.position,
          paused: group.status === 'paused'
        }"
        @click="handlePositionClick(group.position)"
      >
        <div class="stat-header">
          <div class="stat-title">
            {{ group.position }}
            <el-tag v-if="group.status === 'paused'" type="info" size="small" class="pause-tag">
              暂停
            </el-tag>
          </div>
          <div class="stat-total">共 {{ group.total }} 人</div>
        </div>
        <div class="stat-details">
          <div class="stat-item">
            <span class="stat-label">未查看</span>
            <span class="stat-value unviewed">{{ group.unviewed }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">已查看</span>
            <span class="stat-value viewed">{{ group.total - group.unviewed }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="table-card">
      <el-table 
        :data="paginatedTableData" 
        v-loading="loading"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="position" label="应聘岗位" width="180" />
        <el-table-column prop="studentName" label="学生姓名" width="120" />
        <el-table-column prop="studentId" label="学号" width="120" />
        <el-table-column prop="major" label="专业" width="180" />
        <el-table-column prop="phone" label="联系电话" width="140" />
        <el-table-column prop="applyDate" label="申请日期" width="120" />
        <el-table-column prop="viewed" label="查看状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.viewed ? 'success' : 'info'">
              {{ row.viewed ? '已查看' : '未查看' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="120">
          <template #default="{ row }">
            <el-button 
              type="primary" 
              size="small" 
              @click="handleViewDetail(row)"
            >
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="paginatedTableData.length === 0" class="empty-state">
      暂无数据
    </div>

    <div class="pagination">
      <el-pagination
        v-model:current-page="currentPage"
        background
        layout="total, prev, pager, next, jumper"
        :total="filteredTableData.length"
        :page-size="pageSize"
        @current-change="handlePageChange"
      />
    </div>

    <el-dialog
      v-model="detailDialogVisible"
      title="学生岗位申请详情"
      width="1000px"
      :close-on-click-modal="false"
    >
      <div v-if="currentStudent" class="student-detail">
        <div class="detail-section">
          <h3 class="section-title">基本信息</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="学生姓名">{{ currentStudent.studentName }}</el-descriptions-item>
            <el-descriptions-item label="学号">{{ currentStudent.studentId }}</el-descriptions-item>
            <el-descriptions-item label="性别">{{ currentStudent.gender }}</el-descriptions-item>
            <el-descriptions-item label="年级">{{ currentStudent.grade }}</el-descriptions-item>
            <el-descriptions-item label="学校">{{ currentStudent.school }}</el-descriptions-item>
            <el-descriptions-item label="学历">{{ currentStudent.education }}</el-descriptions-item>
            <el-descriptions-item label="专业" :span="2">{{ currentStudent.major }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ currentStudent.phone }}</el-descriptions-item>
            <el-descriptions-item label="电子邮箱">{{ currentStudent.email }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section">
          <h3 class="section-title">申请信息</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="应聘岗位">{{ currentStudent.position }}</el-descriptions-item>
            <el-descriptions-item label="申请日期">{{ currentStudent.applyDate }}</el-descriptions-item>
            <el-descriptions-item label="是否已查看" :span="2">
              <el-tag :type="currentStudent.viewed ? 'success' : 'info'">
                {{ currentStudent.viewed ? '已查看' : '未查看' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section">
          <h3 class="section-title">专业技能</h3>
          <div class="skills-container">
            <el-tag 
              v-for="(skill, index) in currentStudent.skills" 
              :key="index"
              type="info"
              class="skill-tag"
            >
              {{ skill }}
            </el-tag>
          </div>
        </div>

        <div class="detail-section">
          <h3 class="section-title">项目经验</h3>
          <div class="experience-content">
            {{ currentStudent.experience }}
          </div>
        </div>

        <div class="detail-section">
          <h3 class="section-title">自我评价</h3>
          <div class="evaluation-content">
            {{ currentStudent.selfEvaluation }}
          </div>
        </div>

        <div class="detail-section">
          <h3 class="section-title">个人简历</h3>
          <div v-if="archiveLoading" class="loading-text">
            <el-icon class="is-loading"><Loading /></el-icon>
            加载中...
          </div>
          <div v-else-if="getArchivesByType('Resume').length > 0" class="files-container">
            <div 
              v-for="archive in getArchivesByType('Resume')" 
              :key="archive.id" 
              class="file-item"
            >
              <el-icon class="file-icon">
                <component :is="getFileIcon(archive.fileName)" />
              </el-icon>
              <span class="file-name">{{ archive.fileName }}</span>
              <div class="file-actions">
                <el-button type="primary" size="small" @click="handlePreviewFile(archive.id)">
                  预览
                </el-button>
                <el-button size="small" @click="handleDownloadFile(archive.id, archive.fileName)">
                  下载
                </el-button>
              </div>
            </div>
          </div>
          <div v-else class="empty-text">暂无简历</div>
        </div>

        <div class="detail-section">
          <h3 class="section-title">获奖证书</h3>
          <div v-if="archiveLoading" class="loading-text">
            <el-icon class="is-loading"><Loading /></el-icon>
            加载中...
          </div>
          <div v-else-if="getArchivesByType('Certificate').length > 0" class="files-container">
            <div 
              v-for="archive in getArchivesByType('Certificate')" 
              :key="archive.id" 
              class="file-item"
            >
              <el-icon class="file-icon">
                <component :is="getFileIcon(archive.fileName)" />
              </el-icon>
              <span class="file-name">{{ archive.fileName }}</span>
              <div class="file-actions">
                <el-button type="primary" size="small" @click="handlePreviewFile(archive.id)">
                  预览
                </el-button>
                <el-button size="small" @click="handleDownloadFile(archive.id, archive.fileName)">
                  下载
                </el-button>
              </div>
            </div>
          </div>
          <div v-else class="empty-text">暂无证书</div>
        </div>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="detailDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>

    <el-dialog
      v-model="previewDialogVisible"
      :title="previewFile?.fileName"
      width="80%"
      :close-on-click-modal="false"
    >
      <div v-if="previewFile" class="preview-content">
        <img 
          v-if="['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'].includes(getFileExtension(previewFile.fileName).toLowerCase())" 
          :src="previewFile.blobUrl || previewFile.fileUrl" 
          :alt="previewFile.fileName"
          class="preview-image"
        />
        <iframe 
          v-else-if="['pdf'].includes(getFileExtension(previewFile.fileName).toLowerCase())" 
          :src="previewFile.blobUrl || previewFile.fileUrl" 
          class="preview-pdf"
          frameborder="0"
        ></iframe>
        <div v-else class="preview-unsupported">
          <el-icon class="unsupported-icon"><Document /></el-icon>
          <p>该文件类型暂不支持在线预览</p>
          <p>请点击下载按钮查看文件</p>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button v-if="previewFile" @click="handleDownloadFile(previewFile.id, previewFile.fileName)">下载</el-button>
          <el-button @click="previewDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.application-view {
  padding: 0;
}

.page-header {
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  color: white;
  padding: 24px 40px;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(64, 158, 255, 0.3);
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 8px 0;
  font-size: 28px;
  font-weight: bold;
  letter-spacing: 1px;
}

.page-header p {
  margin: 0;
  font-size: 14px;
  opacity: 0.95;
}

.search-card {
  background: white;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.search-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  padding: 20px;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06),
              0 8px 16px rgba(0, 0, 0, 0.08),
              0 16px 32px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid rgba(255, 255, 255, 0.6);
  position: relative;
  overflow: hidden;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, var(--card-color-1), var(--card-color-2));
  opacity: 0.8;
}

.stat-card::after {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle at center, var(--card-color-1) 0%, transparent 70%);
  opacity: 0.05;
  pointer-events: none;
}

.stat-card:nth-child(5n+1) {
  --card-color-1: #409EFF;
  --card-color-2: #36d1dc;
}

.stat-card:nth-child(5n+2) {
  --card-color-1: #67C23A;
  --card-color-2: #4facfe;
}

.stat-card:nth-child(5n+3) {
  --card-color-1: #E6A23C;
  --card-color-2: #f093fb;
}

.stat-card:nth-child(5n+4) {
  --card-color-1: #F56C6C;
  --card-color-2: #ff9a9e;
}

.stat-card:nth-child(5n+5) {
  --card-color-1: #909399;
  --card-color-2: #b0b0b0;
}

.stat-card:hover {
  transform: translateY(-6px) scale(1.03);
  box-shadow: 0 8px 16px rgba(64, 158, 255, 0.15),
              0 16px 32px rgba(64, 158, 255, 0.2),
              0 24px 48px rgba(64, 158, 255, 0.25);
  border-color: rgba(64, 158, 255, 0.3);
}

.stat-card:hover::before {
  opacity: 1;
  height: 5px;
}

.stat-card:hover::after {
  opacity: 0.1;
}

.stat-card.active {
  border-color: #409EFF;
  background: rgba(248, 255, 254, 0.9);
}

.stat-card.paused {
  opacity: 0.9;
  background: #ffebee;
  border: 2px solid #f56c6c;
  position: relative;
}

.stat-card.paused::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: repeating-linear-gradient(
    45deg,
    transparent,
    transparent 10px,
    rgba(245, 108, 108, 0.05) 10px,
    rgba(245, 108, 108, 0.05) 20px
  );
  pointer-events: none;
  z-index: 1;
}

.stat-card.paused:hover {
  opacity: 1;
  background: #ffcdd2;
  border-color: #e6a23c;
}

.stat-card.paused .stat-title {
  text-decoration: line-through;
  color: #909399;
}

.stat-card.paused .stat-total {
  color: #f56c6c;
  font-weight: 600;
}

.pause-tag {
  margin-left: 8px;
}

.stat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e8e8e8;
}

.stat-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.stat-total {
  font-size: 14px;
  color: #909399;
}

.stat-details {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-label {
  font-size: 12px;
  color: #909399;
}

.stat-value {
  font-size: 20px;
  font-weight: bold;
}

.stat-value.unviewed {
  color: #F56C6C;
}

.stat-value.viewed {
  color: #67C23A;
}

.table-card {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.empty-state {
  padding: 40px 20px;
  text-align: center;
  color: #909399;
  font-size: 14px;
}

:deep(.el-button--small) {
  padding: 6px 12px;
  font-size: 13px;
}

.student-detail {
  max-height: none;
  overflow-y: visible;
}

.detail-section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 12px 0;
  padding-bottom: 8px;
  border-bottom: 2px solid #409EFF;
}

.skills-container {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.skill-tag {
  margin: 0;
}

.experience-content,
.evaluation-content {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 4px;
  line-height: 1.8;
  color: #606266;
  font-size: 14px;
}

:deep(.el-table) {
  border-radius: 8px;
  overflow: hidden;
}

:deep(.el-table th) {
  background: #f5f7fa;
  font-weight: 600;
  color: #333;
}

:deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
  background: #fafafa;
}

:deep(.el-descriptions) {
  font-size: 14px;
}

:deep(.el-descriptions__label) {
  font-weight: 600;
  color: #303133;
}

:deep(.el-dialog__body) {
  padding: 20px 24px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.files-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.file-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: #f8f9fa;
  border-radius: 8px;
  border: 1px solid #e9ecef;
  transition: all 0.3s;
}

.file-item:hover {
  background: #f0f2f5;
  border-color: #d0d7de;
  transform: translateX(4px);
}

.file-icon {
  font-size: 24px;
  margin-right: 12px;
  color: #409EFF;
}

.file-name {
  flex: 1;
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-actions {
  display: flex;
  gap: 8px;
}

.loading-text {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  color: #909399;
  font-size: 14px;
}

.loading-text .el-icon {
  margin-right: 8px;
}

.empty-text {
  text-align: center;
  padding: 20px;
  color: #909399;
  font-size: 14px;
}

.preview-content {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
  max-height: 70vh;
  overflow: auto;
}

.preview-image {
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
}

.preview-pdf {
  width: 100%;
  height: 70vh;
  border: none;
}

.preview-unsupported {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  color: #909399;
}

.unsupported-icon {
  font-size: 64px;
  margin-bottom: 16px;
  color: #C0C4CC;
}

.preview-unsupported p {
  margin: 8px 0;
  font-size: 14px;
}

:deep(.el-radio__inner) {
  border-color: #409EFF !important;
  background-color: #fff !important;
}

:deep(.el-radio__input.is-checked .el-radio__inner) {
  border-color: #409EFF !important;
  background-color: #409EFF !important;
}

:deep(.el-radio__input.is-checked .el-radio__inner::after) {
  background-color: #fff !important;
}

:deep(.el-radio__inner:hover) {
  border-color: #409EFF !important;
}

:deep(.el-radio__label) {
  font-size: 14px;
  color: #606266;
}

:deep(.el-radio__input.is-checked + .el-radio__label) {
  color: #409EFF !important;
}
</style>
