<script setup lang="ts">
import logger from '@/utils/logger'
import { onMounted, ref, inject, nextTick, watch, h } from 'vue'
import { queryPageApi, cleanLogsApi } from '@/api/log'
import { useLogStore } from '@/store/log'
import { useSystemSettingsStore } from '@/store/systemSettings'
import { ElTag, ElMessage, ElCard, ElTable, ElTableColumn, ElPagination, ElTabs, ElTabPane, ElMessageBox, ElDropdown, ElDropdownMenu, ElDropdownItem, ElSelect, ElOption, type FormInstance } from 'element-plus'
import { Search, Refresh, Download, Delete, ArrowDown } from '@element-plus/icons-vue'
import { exportToExcel } from '../../utils/xlsx'
import request from '@/utils/request'
import { useAuthStore } from '@/store/auth'
import type { PaginationState, SelectOption } from '@/types/admin'

interface LogData {
  id: string
  operatorName: string
  operatorRole?: string
  operationType: string
  operationResult: string
  module: string
  description: string
  ipAddress: string
  createTime: string
  operateTime?: string
  operationTypeName?: string
  moduleName?: string
  roleName?: string
}

interface SearchForm {
  operatorName: string
  operationType: string
  module: string
}

const authStore = useAuthStore()

const preloadService = inject<{ get: (key: string) => unknown; onComplete: (cb: () => void) => void } | null>('preloadService', null)
const systemSettingsStore = useSystemSettingsStore()

const logStore = useLogStore()

const tableData = ref<LogData[]>([])

const searchForm = ref<SearchForm>({
  operatorName: '',
  operationType: '',
  module: ''
})

const operationTypes = ref<SelectOption[]>([
  { value: 'LOGIN', label: '登录' },
  { value: 'LOGOUT', label: '登出' },
  { value: 'ADD', label: '新增' },
  { value: 'UPDATE', label: '修改' },
  { value: 'DELETE', label: '删除' },
  { value: 'IMPORT', label: '导入' },
  { value: 'EXPORT', label: '导出' },
  { value: 'UPLOAD', label: '上传' },
  { value: 'AUDIT', label: '审核' },
  { value: 'CLEAN', label: '清理' },
  { value: 'SWITCH_ROLE', label: '切换角色' }
])

const modules = ref<SelectOption[]>([
  { value: 'AUTH', label: '认证管理' },
  { value: 'USER_MANAGEMENT', label: '用户管理' },
  { value: 'STUDENT_MANAGEMENT', label: '学生管理' },
  { value: 'TEACHER_MANAGEMENT', label: '教师管理' },
  { value: 'CLASS_MANAGEMENT', label: '班级管理' },
  { value: 'MAJOR_MANAGEMENT', label: '专业管理' },
  { value: 'DEPARTMENT_MANAGEMENT', label: '院系管理' },
  { value: 'COMPANY_MANAGEMENT', label: '企业管理' },
  { value: 'POSITION_MANAGEMENT', label: '岗位管理' },
  { value: 'POSITION_CATEGORY_MANAGEMENT', label: '岗位类别管理' },
  { value: 'ANNOUNCEMENT_MANAGEMENT', label: '公告管理' },
  { value: 'INTERNSHIP_MANAGEMENT', label: '实习管理' },
  { value: 'STUDENT_INTERNSHIP', label: '学生实习' },
  { value: 'SCORING_RULE_MANAGEMENT', label: '评分规则管理' },
  { value: 'KEYWORD_LIBRARY', label: '关键词库' },
  { value: 'KEYWORD_LIBRARY_MANAGEMENT', label: '关键词库管理' },
  { value: 'PROBLEM_FEEDBACK', label: '问题反馈' },
  { value: 'ARCHIVE_MANAGEMENT', label: '档案管理' },
  { value: 'TEMPLATE_MANAGEMENT', label: '模板管理' },
  { value: 'RESOURCE_DOCUMENT_MANAGEMENT', label: '资源文档管理' },
  { value: 'RESOURCE_MANAGEMENT', label: '资源管理' },
  { value: 'LOG_MANAGEMENT', label: '日志管理' },
  { value: 'DATA_EXPORT', label: '数据导出' },
  { value: 'SYSTEM_SETTINGS', label: '系统设置' },
  { value: 'BACKUP', label: '备份管理' },
  { value: 'BACKUP_MANAGEMENT', label: '备份管理' },
  { value: 'PERMISSION', label: '权限管理' },
  { value: 'PERMISSION_MANAGEMENT', label: '权限管理' },
  { value: 'AI_MODEL', label: 'AI模型管理' },
  { value: 'WITHDRAWAL_RECORD_MANAGEMENT', label: '撤回申请记录管理' },
  { value: 'CHAT_MANAGEMENT', label: '聊天管理' }
])

const roles = ref<SelectOption[]>([
  { value: 'STUDENT', label: '学生' },
  { value: 'TEACHER', label: '教师' },
  { value: 'ADMIN', label: '管理员' },
  { value: 'COMPANY', label: '企业' }
])

const loading = ref(false)
const activeTab = ref('all')
const pagination = ref<PaginationState>({
  currentPage: 1,
  pageSize: 10,
  total: 0
})

onMounted(() => {
  if (preloadService) {
    const preloadedLogs = preloadService.get('logs')

    if (preloadedLogs) {
      if (preloadedLogs.data && preloadedLogs.data.rows) {
        tableData.value = processLogData(preloadedLogs.data.rows)
        pagination.value.total = preloadedLogs.data.total || 0
      }
      else if (preloadedLogs.rows) {
        tableData.value = processLogData(preloadedLogs.rows)
        pagination.value.total = preloadedLogs.total || 0
      }
    }

    if (!tableData.value || tableData.value.length === 0) {
      queryPage()
    }

    preloadService.onComplete(() => {
      const updatedLogs = preloadService.get('logs')
      if (updatedLogs) {
        if (updatedLogs.data && updatedLogs.data.rows) {
          tableData.value = processLogData(updatedLogs.data.rows)
          pagination.value.total = updatedLogs.data.total || 0
        }
        else if (updatedLogs.rows) {
          tableData.value = processLogData(updatedLogs.rows)
          pagination.value.total = updatedLogs.total || 0
        }
      }
    })
  } else {
    queryPage()
  }
})


watch(() => logStore.logsCleared, (newValue) => {
  if (newValue) {
    logger.log('检测到日志已被清除，自动刷新日志列表')
    queryPage()
  }
})

watch(activeTab, () => {
  pagination.value.currentPage = 1
  queryPage()
})

const getTagType = (operationType: string): string => {
  const typeMap: Record<string, string> = {
    'LOGIN': 'success',
    'ADD': 'primary',
    'UPDATE': 'warning',
    'DELETE': 'danger',
    'RESET_PASSWORD': 'warning'
  }
  return typeMap[operationType] || 'info'
}

const getOperationTypeName = (operationType: string): string => {
  const nameMap: Record<string, string> = {
    'LOGIN': '登录',
    'LOGOUT': '登出',
    'ADD': '新增',
    'INSERT': '新增',
    'UPDATE': '修改',
    'DELETE': '删除',
    'IMPORT': '导入',
    'EXPORT': '导出',
    'UPLOAD': '上传',
    'AUDIT': '审核',
    'CLEAN': '清理',
    'SWITCH_ROLE': '切换角色',
    'CREATE': '创建',
    'ASSIGN': '分配',
    'CLEAR': '清除',
    'SELECT': '查询',
    'BACKUP': '备份',
    'RESTORE': '恢复',
    'DOWNLOAD': '下载',
    'VERIFY': '验证'
  }
  return nameMap[operationType] || operationType
}

const getModuleName = (module: string): string => {
  const nameMap: Record<string, string> = {
    'AUTH': '认证管理',
    'USER_MANAGEMENT': '用户管理',
    'STUDENT_MANAGEMENT': '学生管理',
    'TEACHER_MANAGEMENT': '教师管理',
    'CLASS_MANAGEMENT': '班级管理',
    'MAJOR_MANAGEMENT': '专业管理',
    'DEPARTMENT_MANAGEMENT': '院系管理',
    'COMPANY_MANAGEMENT': '企业管理',
    'POSITION_MANAGEMENT': '岗位管理',
    'POSITION_CATEGORY_MANAGEMENT': '岗位类别管理',
    'ANNOUNCEMENT_MANAGEMENT': '公告管理',
    'INTERNSHIP_MANAGEMENT': '实习管理',
    'STUDENT_INTERNSHIP': '学生实习',
    'SCORING_RULE_MANAGEMENT': '评分规则管理',
    'SCORING_RULE': '评分规则',
    'KEYWORD_LIBRARY_MANAGEMENT': '关键词库管理',
    'KEYWORD_LIBRARY': '关键词库',
    'PROBLEM_FEEDBACK': '问题反馈',
    'ARCHIVE_MANAGEMENT': '档案管理',
    'TEMPLATE_MANAGEMENT': '模板管理',
    'RESOURCE_DOCUMENT_MANAGEMENT': '资源文档管理',
    'RESOURCE_MANAGEMENT': '资源管理',
    'LOG_MANAGEMENT': '日志管理',
    'DATA_EXPORT': '数据导出',
    'SYSTEM_SETTINGS': '系统设置',
    'BACKUP_MANAGEMENT': '备份管理',
    'BACKUP': '备份管理',
    'PERMISSION_MANAGEMENT': '权限管理',
    'PERMISSION': '权限管理',
    'AI_MODEL': 'AI模型管理',
    'WITHDRAWAL_RECORD_MANAGEMENT': '撤回申请记录管理',
    'CHAT_MANAGEMENT': '聊天管理',
    'INTERNSHIP_REFLECTION': '实习心得管理',
    'SYSTEM_CONFIG': '系统配置'
  }

  if (nameMap[module]) {
    return nameMap[module]
  }

  if (module) {
    let words = []
    
    if (module.includes('_')) {
      words = module.split('_').map(word => word.toLowerCase())
    }
    else if (/^[a-zA-Z]+$/.test(module)) {
      words = module
        .replace(/([A-Z])/g, ' $1')
        .trim()
        .toLowerCase()
        .split(' ')
    }
    
    if (words.length > 0) {
      const wordMap = {
        'user': '用户',
        'student': '学生',
        'teacher': '教师',
        'course': '课程',
        'major': '专业',
        'system': '系统',
        'className': '班级',
        'department': '院系',
        'group': '小组',
        'assignment': '作业',
        'score': '成绩',
        'log': '日志',
        'permission': '权限',
        'role': '角色',
        'notification': '通知',
        'file': '文件',
        'dashboard': '仪表盘',
        'management': '管理',
        'settings': '设置',
        'analytics': '分析',
        'content': '内容',
        'report': '报表',
        'configuration': '配置',
        'performance': '绩效',
        'data': '数据',
        'company': '企业',
        'internship': '实习',
        'application': '申请',
        'position': '岗位',
        'category': '类别',
        'keyword': '关键词',
        'library': '库',
        'rule': '规则',
        'scoring': '评分',
        'announcement': '公告',
        'backup': '备份',
        'export': '导出',
        'feedback': '反馈',
        'problem': '问题',
        'archive': '档案',
        'template': '模板',
        'auth': '认证'
      }
      const translatedWords = words.map(word => wordMap[word] || word)
      return translatedWords.join('')
    }
  }

  return module || '未知模块'
}

const getRoleName = (role: string): string => {
  const nameMap: Record<string, string> = {
    'all': '全部',
    'STUDENT': '学生',
    'TEACHER': '教师',
    'ADMIN': '管理员',
    'COMPANY': '企业'
  }
  return nameMap[role] || role || '未知'
}

const getRoleTagType = (role: string): string => {
  const typeMap: Record<string, string> = {
    'STUDENT': 'primary',
    'TEACHER': 'success',
    'ADMIN': 'danger',
    'COMPANY': 'warning'
  }
  return typeMap[role] || 'info'
}

const formatDate = (date: string): string => {
  if (!date) return '未知'
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hour = String(d.getHours()).padStart(2, '0')
  const minute = String(d.getMinutes()).padStart(2, '0')
  const second = String(d.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`
}

const processLogData = (rows: unknown[]): LogData[] => {
  return rows.map(log => ({
    ...log,
    operatorName: (log as any).operatorName || '未知',
    createTime: (log as any).operate_time || (log as any).operateTime || (log as any).createTime || (log as any).createdTime,
    operateTime: (log as any).operate_time || (log as any).operateTime || (log as any).createTime || (log as any).createdTime
  }))
}

const handleExportToExcel = async (): Promise<void> => {
  const selectedRole = ref('all')
  
  try {
    await ElMessageBox({
      title: '导出Excel',
      message: () => h('div', { class: 'export-dialog-content' }, [
        h('div', { style: { marginBottom: '15px' } }, '请选择要导出的日志角色'),
        h('div', { style: { display: 'flex', alignItems: 'center' } }, [
          h('span', { style: { marginRight: '10px' } }, '选择角色：'),
          h(ElSelect, {
            modelValue: selectedRole.value,
            'onUpdate:modelValue': (val) => {
              selectedRole.value = val
            },
            placeholder: '请选择角色',
            style: { width: '200px' }
          }, {
            default: () => [
              h(ElOption, { label: '全部角色', value: 'all' }),
              h(ElOption, { label: '学生', value: 'STUDENT' }),
              h(ElOption, { label: '教师', value: 'TEACHER' }),
              h(ElOption, { label: '管理员', value: 'ADMIN' }),
              h(ElOption, { label: '企业', value: 'COMPANY' })
            ]
          })
        ])
      ]),
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      showCancelButton: true,
      beforeClose: (action, instance, done) => {
        if (action === 'confirm') {
          done()
        } else {
          done()
        }
      }
    })

    if (!selectedRole.value) {
      return
    }

    loading.value = true
    
    const operatorRole = selectedRole.value === 'all' ? null : selectedRole.value
    
    const response = await queryPageApi(searchForm.value.operatorName, operatorRole, searchForm.value.operationType, searchForm.value.module, 1, 10000, 'operate_time', 'desc')
    
    if (response && response.code === 200 && response.data) {
      const exportDataList = response.data.rows || []
      
      if (exportDataList.length === 0) {
        ElMessage.warning('暂无数据可导出')
        loading.value = false
        return
      }
      
      const exportData = exportDataList.map(item => ({
        'ID': item.id,
        '操作人': item.operatorName || '未知',
        '身份': getRoleName(item.operatorRole),
        '操作类型': getOperationTypeName(item.operationType),
        '操作结果': item.operationResult === 'SUCCESS' ? '成功' : '失败',
        '操作模块': getModuleName(item.module),
        '操作描述': item.description || '',
        'IP地址': item.ipAddress || '未知',
        '操作时间': formatDate(item.operateTime || item.createTime) 
      }))
      
      const timestamp = new Date().toLocaleString('zh-CN').replace(/[/\\:]/g, '-')
      const roleName = selectedRole.value === 'all' ? '全部' : getRoleName(selectedRole.value)
      const fileName = `${roleName}系统操作日志_${timestamp}`
      
      await exportToExcel(exportData, fileName, '操作日志')
      
      ElMessage.success('日志数据导出成功')
    } else {
      ElMessage.error('获取日志数据失败')
    }
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      logger.error('导出Excel失败:', error)
      ElMessage.error('导出Excel失败，请重试')
    }
  } finally {
    loading.value = false
  }
}

const cleanLogs = async (role: string | null = null): Promise<void> => {
  try {
    const roleName = role ? getRoleName(role) : '全部'
    
    await ElMessageBox.confirm(
      `确定要清理${roleName}日志吗？\n此操作将保留最新记录，不可恢复！`,
      '清理日志确认',
      {
        confirmButtonText: '确定清理',
        cancelButtonText: '取消',
        type: 'warning',
        distinguishCancelAndClose: true,
        dangerouslyUseHTMLString: true
      }
    )

    loading.value = true
    const operatorRole = role === 'all' ? null : role
    const response = await cleanLogsApi(operatorRole, false)
    
    logger.log('清理日志完整响应:', response)
    logger.log('响应数据:', response.data)
    
    const data = response.data || {}
    const code = data.code
    const message = data.message || data.msg || '日志清理成功'
    
    logger.log('实际响应码:', code)
    logger.log('实际响应消息:', message)
    
    if (code === 200) {
      ElMessage.success(message)
      queryPage()
    } else {
      ElMessage.error(message || '日志清理失败')
    }
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      logger.error('清理日志失败:', error)
      
      if (error.response && error.response.data) {
        const errorMsg = error.response.message || error.response.msg || '日志清理失败，请重试'
        ElMessage.error(errorMsg)
      } else if (error.message) {
        ElMessage.error('清理日志失败：' + error.message)
      } else {
        ElMessage.error('日志清理失败，请重试')
      }
    }
  } finally {
    loading.value = false
  }
}

const handleCleanCommand = async (command: string): Promise<void> => {
  await cleanLogs(command)
}

const clearSearch = (): void => {
  searchForm.value = {
    operatorName: '',
    operationType: '',
    module: ''
  }
  pagination.value.currentPage = 1
}

const handleSizeChange = (newSize: number): void => {
  pagination.value.pageSize = newSize
  queryPage()
}

const handleCurrentChange = (newCurrent: number): void => {
  pagination.value.currentPage = newCurrent
  queryPage()
}

const queryPage = async (): Promise<void> => {
  loading.value = true
  try {
    logger.log('开始查询日志列表...')
    const { operatorName, operationType, module } = searchForm.value
    const page = pagination.value.currentPage
    const pageSize = pagination.value.pageSize
    
    let operatorRole = null
    if (activeTab.value !== 'all') {
      operatorRole = activeTab.value
    }

    logger.log('查询参数:', { operatorName, operatorRole, operationType, module, page, pageSize })
    const response = await queryPageApi(operatorName, operatorRole, operationType, module, page, pageSize, 'operate_time', 'desc')
    logger.log('查询结果响应:', response)

    if (response && response.code === 200 && response.data) {
      const dataList = response.data.rows || []
      const totalCount = response.data.total || 0

      const enhancedData = processLogData(dataList).map(log => {
        const operationTypeName = getOperationTypeName(log.operationType)
        const moduleName = getModuleName(log.module)
        const roleName = getRoleName(log.operatorRole)
        return {
          ...log,
          operationTypeName: operationTypeName,
          moduleName: moduleName,
          roleName: roleName
        }
      })

      enhancedData.sort((a, b) => {
        const timeA = new Date(a.createTime || 0).getTime()
        const timeB = new Date(b.createTime || 0).getTime()
        return timeB - timeA
      })

      tableData.value = enhancedData
      pagination.value.total = totalCount
      logger.log('处理后的数据列表:', tableData.value)
    } else {
      tableData.value = []
      pagination.value.total = 0
      logger.log('无数据或响应格式不正确')
    }
  } catch (error) {
    logger.error('查询日志列表失败:', error)
    tableData.value = []
    pagination.value.total = 0
    ElMessage.error('查询日志列表失败: ' + (error.response?.data?.message || error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

</script>

<template>
  <div class="log-management-container">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">日志管理</h1>
        <p class="page-description">查看系统的所有操作日志记录</p>
        <p class="auto-clean-tip">系统会自动管理日志数量：学生日志超过500条保留100条，教师日志超过300条保留80条，管理员日志超过200条保留50条，企业日志超过300条保留80条</p>
      </div>
      <div class="header-illustration">
        <div class="illustration-circle circle-1"></div>
        <div class="illustration-circle circle-2"></div>
      </div>
    </div>

    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" class="search-form" @keyup.enter="queryPage">
        <div class="search-row">
          <el-form-item label="操作人">
            <el-input
              v-model="searchForm.operatorName"
              placeholder="请输入操作人姓名"
              clearable
              @keyup.enter="queryPage"
            ></el-input>
          </el-form-item>
          <el-form-item label="操作类型">
            <el-select v-model="searchForm.operationType" placeholder="请选择操作类型" clearable filterable>
              <el-option v-for="type in operationTypes" :key="type.value" :label="type.label" :value="type.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="操作模块">
            <el-select v-model="searchForm.module" placeholder="请选择操作模块" clearable filterable>
              <el-option v-for="module in modules" :key="module.value" :label="module.label" :value="module.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <div class="search-actions">
              <el-button v-if="authStore.hasPermission('log:view')" type="primary" @click="queryPage" class="search-btn">
                <el-icon>
                  <Search />
                </el-icon>&nbsp;查询
              </el-button>
              <el-button v-if="authStore.hasPermission('log:view')" type="warning" @click="clearSearch" class="reset-btn">
                <el-icon>
                  <Refresh />
                </el-icon>&nbsp;重置
              </el-button>
              <el-button v-if="authStore.hasPermission('log:view')" type="success" @click="exportToExcel" class="export-btn">
                <el-icon>
                  <Download />
                </el-icon>&nbsp;导出Excel
              </el-button>
              <div v-if="authStore.hasPermission('log:delete')" class="clean-button-group">
                <el-button type="danger" @click="cleanLogs('all')" class="clean-btn">
                  <el-icon>
                    <Delete />
                  </el-icon>&nbsp;清理日志
                </el-button>
                <el-dropdown @command="handleCleanCommand" class="clean-dropdown">
                  <el-button type="danger" class="clean-dropdown-btn">
                    <el-icon>
                      <ArrowDown />
                    </el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="all">清理全部日志</el-dropdown-item>
                      <el-dropdown-item command="STUDENT">清理学生日志</el-dropdown-item>
                      <el-dropdown-item command="TEACHER">清理教师日志</el-dropdown-item>
                      <el-dropdown-item command="ADMIN">清理管理员日志</el-dropdown-item>
                      <el-dropdown-item command="COMPANY">清理企业日志</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
          </el-form-item>
        </div>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="table-header">
        <h3 class="table-title">操作日志列表</h3>
        <div class="table-actions">
          <span class="total-count">共 {{ pagination.total }} 条记录</span>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="log-tabs">
        <el-tab-pane label="全部" name="all"></el-tab-pane>
        <el-tab-pane label="学生日志" name="STUDENT"></el-tab-pane>
        <el-tab-pane label="教师日志" name="TEACHER"></el-tab-pane>
        <el-tab-pane label="管理员日志" name="ADMIN"></el-tab-pane>
        <el-tab-pane label="企业日志" name="COMPANY"></el-tab-pane>
      </el-tabs>

      <el-table :data="tableData" border style="width: 100%" fit v-loading="loading" class="data-table">        
        <el-table-column prop="id" label="ID" align="center" width="60" />
        <el-table-column prop="operatorName" label="操作人姓名" align="center" width="120" />
        <el-table-column prop="operatorRole" label="身份" align="center" width="100">
          <template #default="scope">
            <el-tag :type="getRoleTagType(scope.row.operatorRole)" class="role-tag">
              {{ scope.row.roleName || getRoleName(scope.row.operatorRole) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operationTypeName" label="操作类型" align="center" width="120">
          <template #default="scope">
            <el-tag :type="getTagType(scope.row.operationType)" class="operation-tag">
              {{ scope.row.operationTypeName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operationResult" label="操作结果" align="center" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.operationResult === 'SUCCESS' ? 'success' : 'danger'" class="result-tag">
              {{ scope.row.operationResult === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="module" label="操作模块" align="center" width="150">
          <template #default="scope">
            <span class="module-name">{{ scope.row.moduleName || getModuleName(scope.row.module) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="操作描述" align="center" min-width="200" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP地址" align="center" width="140">
          <template #default="scope">
            <span class="ip-text">{{ scope.row.ipAddress || '未知' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="操作时间" align="center" width="180">
          <template #default="scope">
            <span v-if="scope.row.createTime" class="time-text">
              {{ new Date(scope.row.createTime).toLocaleString('zh-CN') }}
            </span>
            <span v-else-if="scope.row.operateTime" class="time-text">
              {{ new Date(scope.row.operateTime).toLocaleString('zh-CN') }}
            </span>
            <span v-else class="unknown-text">未知</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination v-model:current-page="pagination.currentPage" v-model:page-size="pagination.pageSize"
          :page-sizes="[5, 10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" :total="pagination.total"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" class="custom-pagination" />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.log-management-container {
  padding: 20px;
  background: #f5f7fa;
  min-height: 100vh;
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

.header-content h1 {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
  color: white;
}

.header-content p {
  font-size: 14px;
  opacity: 0.95;
  font-weight: 500;
  margin: 0;
}

.auto-clean-tip {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 4px;
  font-weight: 400;
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
  0%,
  100% {
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

.search-form {
  width: 100%;
}

.search-row {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.search-row .el-form-item {
  margin-bottom: 0;
  flex: 1;
}

.search-actions {
  display: flex;
  gap: 10px;
}

.search-btn,
.reset-btn {
  border-radius: 8px;
  padding: 10px 20px;
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

.table-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.total-count {
  font-size: 14px;
  color: #909399;
}

.log-tabs {
  padding: 0 20px;
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

.role-tag {
  font-weight: 500;
}

.operation-tag {
  font-weight: 500;
}

.module-name {
  color: #606266;
}

.time-text {
  color: #909399;
}

.ip-text {
  color: #606266;
  font-family: 'Courier New', monospace;
  font-size: 13px;
}

.result-tag {
  font-weight: 500;
}

.unknown-text {
  color: #C0C4CC;
}

.pagination-container {
  display: flex;
  justify-content: center;
  padding: 20px;
  border-top: 1px solid #f0f0f0;
}

.custom-pagination :deep(.el-pagination) {
  display: flex;
  align-items: center;
}

.custom-pagination :deep(.el-pagination .el-pager li) {
  border-radius: 6px;
  margin: 0 4px;
}

.custom-pagination :deep(.el-pagination .el-pager li.is-active) {
  background-color: #409EFF;
  color: white;
}

.custom-pagination :deep(.el-pagination button) {
  border-radius: 6px;
}

.clean-btn {
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
  margin-right: 0;
}

.clean-dropdown-btn {
  border-top-left-radius: 0;
  border-bottom-left-radius: 0;
  margin-left: 0;
  padding: 0 12px;
}

.clean-button-group {
  display: inline-flex;
  align-items: center;
  gap: 0;
}

.clean-dropdown {
  display: inline-flex;
  vertical-align: middle;
}
</style>