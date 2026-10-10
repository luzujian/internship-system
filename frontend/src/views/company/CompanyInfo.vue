<script setup>
import companyInfoSchema from '../../../../backend/src/main/resources/agent/company-info-schema.json'
const companyField = (prop) => companyInfoSchema.fields.find(field => field.prop === prop)
import { useAgentPageContext } from '@/agent/useAgentPageContext'
import { useFormContext } from '@/agent/formContext'
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import { Star, Plus, Upload, View, Delete, Picture, VideoCamera, Document } from '@element-plus/icons-vue'
import { EluiChinaAreaDht } from 'elui-china-area-dht'
import CompanyService from '@/api/CompanyService'
import UploadService from '@/api/UploadService'
import CompanyContacts from '@/components/company/CompanyContacts.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// 初始化省市区数据
const chinaData = new EluiChinaAreaDht.ChinaArea().chinaAreaflat

// 从 auth store 获取当前用户 ID
const companyId = computed(() => {
  const id = authStore.user?.id
  console.log('[companyId] authStore.user?.id:', id, 'type:', typeof id)
  if (id) {
    const parsedId = parseInt(id)
    console.log('[companyId] 返回解析后的 ID:', parsedId)
    return parsedId
  }
  // 当无法获取企业 ID 时，尝试从 localStorage 获取
  const storedCompanyId = localStorage.getItem('company_companyId_COMPANY')
  if (storedCompanyId) {
    console.log('[companyId] 从 localStorage 获取到企业 ID:', storedCompanyId)
    return parseInt(storedCompanyId)
  }
  // 如果仍然无法获取，返回 null 并显示错误提示
  console.log('[companyId] authStore.user?.id 不存在，localStorage 中也没有找到企业 ID')
  ElMessage.error('未获取到当前登录企业 ID，请重新登录')
  return null
})

const activeTab = ref(route.query.tab === 'contacts' ? 'contacts' : 'basic')
const loading = ref(false)
const schoolLiaisons = ref([])
const schoolLiaisonsLoading = ref(false)

const basicForm = ref({
  companyName: '',
  industry: '',
  scale: '',
  province: '',
  city: '',
  district: '',
  addressCode: null,
  detailAddress: '',
  contactPerson: '',
  contactPhone: '',
  contactEmail: '',
  website: '',
  cooperationMode: '',
  auditStatus: null,
  isInternshipBase: companyField('isInternshipBase').defaultValue,
  hasTrainingBaseAgreement: companyField('hasTrainingBaseAgreement').defaultValue,
  acceptBackup: companyField('acceptBackup').defaultValue,
  isAlumniUnit: companyField('isAlumniUnit').defaultValue,
  alumniMembers: []
})

const createEmptyAlumniMember = () => ({ name: '', phone: '', position: '' })

const handleAlumniUnitChange = (value) => {
  if (value === 1 && basicForm.value.alumniMembers.length === 0) {
    basicForm.value.alumniMembers.push(createEmptyAlumniMember())
  }
  // 保留本地校友草稿，切回“是”或撤销 AI 填写时不会抹掉手工填写；保存“否”仍按原接口发送空列表。
}

const addAlumniMember = () => basicForm.value.alumniMembers.push(createEmptyAlumniMember())

const removeAlumniMember = (index) => {
  if (basicForm.value.alumniMembers.length > 1) basicForm.value.alumniMembers.splice(index, 1)
}

const validateAlumniMembers = () => {
  if (basicForm.value.isAlumniUnit !== 1) return true
  if (!basicForm.value.alumniMembers.length) {
    ElMessage.warning('校友单位至少需要填写一名校友')
    return false
  }
  const invalid = basicForm.value.alumniMembers.some(member => !member.name?.trim() || !member.phone?.trim() || !member.position?.trim())
  if (invalid) ElMessage.warning('请完整填写每名校友的姓名、电话和任职')
  return !invalid
}

// 系统内企业统一属于校企合作，标签显示与 AI 解释共用字段定义。
const companyTags = computed(() => {
  const modes = String(basicForm.value.cooperationMode || '').split(',').map(value => value.trim()).filter(Boolean)
  return companyInfoSchema.tags.filter(tag => !tag.requiresApproval || basicForm.value.auditStatus === 1).filter(tag => tag.prop === 'cooperationMode'
    ? tag.requiresApproval || tag.values.some(value => modes.includes(String(value)))
    : tag.values.includes(basicForm.value[tag.prop]))
})

const promotionForm = ref({
  logo: '',
  photos: [],
  videos: [],
  introduction: ''
})

const photoPreviewVisible = ref(false)
const videoPreviewVisible = ref(false)
const currentPreviewUrl = ref('')

const savePromotionData = () => {
  const data = {
    logo: promotionForm.value.logo,
    photos: JSON.stringify(promotionForm.value.photos),
    videos: JSON.stringify(promotionForm.value.videos),
    introduction: promotionForm.value.introduction
  }
  
  return CompanyService.updateCompanyInfo(data)
}

const handlePhotoPreview = (file) => {
  console.log('=== 照片预览 ===', file)
  if (file.url) {
    currentPreviewUrl.value = file.url
    photoPreviewVisible.value = true
  } else if (file.response?.url) {
    currentPreviewUrl.value = file.response.url
    photoPreviewVisible.value = true
  }
}

const handleVideoPreview = (file) => {
  console.log('=== 视频预览 ===', file)
  if (file.url) {
    currentPreviewUrl.value = file.url
    videoPreviewVisible.value = true
  } else if (file.response?.url) {
    currentPreviewUrl.value = file.response.url
    videoPreviewVisible.value = true
  }
}

const removeVideo = (index) => {
  videoFileList.value.splice(index, 1)
  promotionForm.value.videos.splice(index, 1)
  savePromotionData().then(saveRes => {
    if (saveRes.code === 200) {
      ElMessage.success('视频删除成功')
    } else {
      ElMessage.error('删除保存失败: ' + (saveRes.message || '未知错误'))
    }
  })
}

const removePhoto = (index) => {
  photoFileList.value.splice(index, 1)
  promotionForm.value.photos.splice(index, 1)
  console.log('删除照片，剩余照片:', promotionForm.value.photos)
  savePromotionData().then(saveRes => {
    if (saveRes.code === 200) {
      ElMessage.success('照片删除成功')
    } else {
      ElMessage.error('删除保存失败: ' + (saveRes.message || '未知错误'))
    }
  })
}

const removeAllPhotos = () => {
  photoFileList.value = []
  promotionForm.value.photos = []
  console.log('删除所有照片')
  savePromotionData().then(saveRes => {
    if (saveRes.code === 200) {
      ElMessage.success('所有照片已删除')
    } else {
      ElMessage.error('删除保存失败: ' + (saveRes.message || '未知错误'))
    }
  })
}

const removeLogo = () => {
  promotionForm.value.logo = ''
  logoFileList.value = []
  console.log('删除logo')
  savePromotionData().then(saveRes => {
    if (saveRes.code === 200) {
      ElMessage.success('Logo删除成功')
    } else {
      ElMessage.error('删除保存失败: ' + (saveRes.message || '未知错误'))
    }
  })
}

const industryOptions = companyField('industry').options
const scaleOptions = companyField('scale').options

const getIndustryLabel = (value) => {
  const option = industryOptions.find(item => item.value === value)
  return option ? option.label : (value || '-')
}

const getScaleLabel = (value) => {
  const option = scaleOptions.find(item => item.value === value)
  return option ? option.label : (value || '-')
}

const logoFileList = ref([])
const photoFileList = ref([])
const videoFileList = ref([])

const handleLogoChange = (file) => {
  logoFileList.value = [file]
  UploadService.uploadCompanyMedia(file.raw, 'logo').then(res => {
    if (res.code === 200) {
      promotionForm.value.logo = res.data.url
      savePromotionData().then(saveRes => {
        if (saveRes.code === 200) {
          ElMessage.success('Logo上传并保存成功')
        } else {
          ElMessage.error('Logo保存失败: ' + (saveRes.message || '未知错误'))
        }
      })
    } else {
      ElMessage.error(res.message || '上传失败')
    }
  }).catch(err => {
    ElMessage.error('上传失败: ' + err.message)
  })
}

const handlePhotoChange = (file, fileList) => {
  console.log('=== 开始上传照片 ===')
  console.log('file:', file)
  console.log('fileList:', fileList)
  
  if (!file.raw) {
    console.log('没有新文件，跳过上传')
    return
  }
  
  const files = [file.raw]
  console.log('准备上传的文件:', files)
  
  UploadService.uploadCompanyMedia(files[0], 'photo').then(res => {
    console.log('照片上传响应:', res)
    console.log('res.data:', res.data)
    
    if (res.code === 200) {
      console.log('uploadedFiles:', res.data.uploadedFiles)
      
      if (!res.data.uploadedFiles || res.data.uploadedFiles.length === 0) {
        console.error('照片上传失败：uploadedFiles为空')
        ElMessage.error('照片上传失败，请重试')
        return
      }
      
      const uploadedFile = res.data.uploadedFiles[0]
      if (!uploadedFile || !uploadedFile.url) {
        console.error('照片上传失败：文件信息不完整')
        ElMessage.error('照片上传失败，文件信息不完整')
        return
      }
      
      const newPhotoUrl = uploadedFile.url
      console.log('新照片URL:', newPhotoUrl)
      
      promotionForm.value.photos.push(newPhotoUrl)
      photoFileList.value.push({
        uid: `photo_${Date.now()}_${photoFileList.value.length}`,
        name: file.name,
        url: newPhotoUrl,
        thumbnailUrl: newPhotoUrl,
        status: 'success'
      })
      
      console.log('更新后的photos:', promotionForm.value.photos)
      console.log('更新后的photoFileList:', photoFileList.value)
      
      savePromotionData().then(saveRes => {
        if (saveRes.code === 200) {
          ElMessage.success('照片上传并保存成功')
        } else {
          ElMessage.error('照片保存失败: ' + (saveRes.message || '未知错误'))
        }
      })
      
      if (res.data.errors && res.data.errors.length > 0) {
        console.warn('上传警告:', res.data.errors)
        ElMessage.warning(res.data.errors.join('；'))
      }
    } else {
      console.error('照片上传失败:', res.message)
      ElMessage.error(res.message || '上传失败')
    }
  }).catch(err => {
    console.error('照片上传异常:', err)
    ElMessage.error('上传失败: ' + (err.message || '未知错误'))
  })
}

const handleVideoChange = (file, fileList) => {
  console.log('=== 开始上传视频 ===')
  console.log('file:', file)
  console.log('fileList:', fileList)
  
  if (!file.raw) {
    console.log('没有新文件，跳过上传')
    return
  }
  
  const files = [file.raw]
  console.log('准备上传的文件:', files)
  
  UploadService.uploadCompanyMedia(files[0], 'video').then(res => {
    console.log('视频上传响应:', res)
    console.log('res.data:', res.data)
    
    if (res.code === 200) {
      console.log('uploadedFiles:', res.data.uploadedFiles)
      
      if (!res.data.uploadedFiles || res.data.uploadedFiles.length === 0) {
        console.error('视频上传失败：uploadedFiles为空')
        ElMessage.error('视频上传失败，请重试')
        return
      }
      
      const uploadedFile = res.data.uploadedFiles[0]
      if (!uploadedFile || !uploadedFile.url) {
        console.error('视频上传失败：文件信息不完整')
        ElMessage.error('视频上传失败，文件信息不完整')
        return
      }
      
      const newVideoUrl = uploadedFile.url
      console.log('新视频URL:', newVideoUrl)
      
      promotionForm.value.videos.push(newVideoUrl)
      videoFileList.value.push({
        uid: `video_${Date.now()}_${videoFileList.value.length}`,
        name: file.name,
        url: newVideoUrl,
        status: 'success'
      })
      
      console.log('更新后的videos:', promotionForm.value.videos)
      console.log('更新后的videoFileList:', videoFileList.value)
      
      savePromotionData().then(saveRes => {
        if (saveRes.code === 200) {
          ElMessage.success('视频上传并保存成功')
        } else {
          ElMessage.error('视频保存失败: ' + (saveRes.message || '未知错误'))
        }
      })
      
      if (res.data.errors && res.data.errors.length > 0) {
        console.warn('上传警告:', res.data.errors)
        ElMessage.warning(res.data.errors.join('；'))
      }
    } else {
      console.error('视频上传失败:', res.message)
      ElMessage.error(res.message || '上传失败')
    }
  }).catch(err => {
    console.error('视频上传异常:', err)
    ElMessage.error('上传失败: ' + (err.message || '未知错误'))
  })
}

const handleRemove = (file, fileList) => {
  console.log('=== 删除文件 ===')
  console.log('file:', file)
  console.log('fileList:', fileList)
  
  const index = fileList.indexOf(file)
  if (index > -1) {
    fileList.splice(index, 1)
    if (fileList === photoFileList.value) {
      promotionForm.value.photos.splice(index, 1)
      console.log('删除照片，剩余照片:', promotionForm.value.photos)
    }
  }
}

const handleSaveBasic = () => {
  if (loading.value) return
  const fillInstance = formContext.currentForm.value?.instanceId
  const savedValues = formContext.captureValues('company:info')
  if (!validateAlumniMembers()) return
  loading.value = true

  const isDirectMunicipality = ['北京市', '上海市', '天津市', '重庆市'].includes(basicForm.value.province)

  const data = {
    companyName: basicForm.value.companyName,
    industry: basicForm.value.industry,
    scale: basicForm.value.scale,
    province: basicForm.value.province,
    city: isDirectMunicipality ? '市辖区' : basicForm.value.city,
    district: basicForm.value.district,
    detailAddress: basicForm.value.detailAddress,
    contactPerson: basicForm.value.contactPerson,
    contactPhone: basicForm.value.contactPhone,
    contactEmail: basicForm.value.contactEmail,
    website: basicForm.value.website,
    isInternshipBase: basicForm.value.isInternshipBase,
    hasTrainingBaseAgreement: basicForm.value.hasTrainingBaseAgreement,
    acceptBackup: basicForm.value.acceptBackup,
    isAlumniUnit: basicForm.value.isAlumniUnit,
    alumniMembers: basicForm.value.isAlumniUnit === 1 ? basicForm.value.alumniMembers : []
  }

  console.log('=== 保存基础信息 ===')
  console.log('发送的数据:', data)
  console.log('companyId:', companyId.value)
  
  CompanyService.updateCompanyInfo(data).then(res => {
    if (res.code === 200) {
      ElMessage.success('基础信息保存成功')
      formContext.reportFormOutcome('company:info', { status: 'saved', label: '企业基本信息已保存', url: '/company/interviews', object: { type: 'company', id: String(companyId.value) } }, fillInstance, savedValues)
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  }).catch(err => {
    ElMessage.error('保存失败: ' + err.message)
  }).finally(() => {
    loading.value = false
  })
}

const handleSavePromotion = () => {
  if (loading.value) return
  const fillInstance = formContext.currentForm.value?.instanceId
  const savedValues = formContext.captureValues('company:promotion')
  loading.value = true
  
  console.log('=== 开始保存宣传资料 ===')
  console.log('promotionForm.value:', promotionForm.value)
  console.log('logo:', promotionForm.value.logo)
  console.log('photos:', promotionForm.value.photos)
  console.log('videos:', promotionForm.value.videos)
  console.log('introduction:', promotionForm.value.introduction)
  
  const data = {
    logo: promotionForm.value.logo,
    photos: JSON.stringify(promotionForm.value.photos),
    videos: JSON.stringify(promotionForm.value.videos),
    introduction: promotionForm.value.introduction
  }
  
  console.log('准备发送给后端的数据:', data)
  
  CompanyService.updateCompanyInfo(data).then(res => {
    console.log('后端返回结果:', res)
    if (res.code === 200) {
      ElMessage.success('宣传资料保存成功')
      formContext.reportFormOutcome('company:promotion', { status: 'saved', label: '企业宣传资料已保存', url: '/company/interviews' }, fillInstance, savedValues)
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  }).catch(err => {
    console.error('保存失败错误:', err)
    ElMessage.error('保存失败: ' + err.message)
  }).finally(() => {
    loading.value = false
  })
}

const handleSaveAll = () => {
  if (loading.value) return
  const fillInstance = formContext.currentForm.value?.instanceId
  const fillFormId = formContext.currentForm.value?.formId
  const savedValues = fillFormId ? formContext.captureValues(fillFormId) : null
  if (!validateAlumniMembers()) return
  loading.value = true
  const data = {
    companyName: basicForm.value.companyName,
    industry: basicForm.value.industry,
    scale: basicForm.value.scale,
    province: basicForm.value.province,
    city: basicForm.value.city,
    district: basicForm.value.district,
    detailAddress: basicForm.value.detailAddress,
    contactPerson: basicForm.value.contactPerson,
    contactPhone: basicForm.value.contactPhone,
    contactEmail: basicForm.value.contactEmail,
    website: basicForm.value.website,
    isInternshipBase: basicForm.value.isInternshipBase,
    hasTrainingBaseAgreement: basicForm.value.hasTrainingBaseAgreement,
    acceptBackup: basicForm.value.acceptBackup,
    isAlumniUnit: basicForm.value.isAlumniUnit,
    alumniMembers: basicForm.value.isAlumniUnit === 1 ? basicForm.value.alumniMembers : [],
    logo: promotionForm.value.logo,
    photos: JSON.stringify(promotionForm.value.photos),
    videos: JSON.stringify(promotionForm.value.videos),
    introduction: promotionForm.value.introduction
  }
  
  CompanyService.updateCompanyInfo(data).then(res => {
    if (res.code === 200) {
      ElMessage.success('企业信息保存成功')
      if (fillFormId === 'company:info' || fillFormId === 'company:promotion') formContext.reportFormOutcome(fillFormId, { status: 'saved', label: '企业信息已保存', url: '/company/interviews' }, fillInstance, savedValues)
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  }).catch(err => {
    ElMessage.error('保存失败: ' + err.message)
  }).finally(() => {
    loading.value = false
  })
}

const loadCompanyInfo = () => {
  if (!companyId.value) {
    console.log('等待用户信息加载...')
    loading.value = false
    return
  }

  loading.value = true
  console.log('=== 加载企业信息 ===')
  console.log('companyId:', companyId.value)
  console.log('authStore.user:', authStore.user)
  console.log('authStore.user?.id:', authStore.user?.id)

  CompanyService.getCompanyInfo(companyId.value).then(res => {
    if (res.code === 200 && res.data) {
      const data = res.data
      console.log('API 返回的数据:', data)
      console.log('data.province:', data.province)
      console.log('data.city:', data.city)
      console.log('data.district:', data.district)
      console.log('data.detailAddress:', data.detailAddress)
      
      const addressCodes = resolveAddressCodes(data.province, data.city, data.district)

      // 直接设置表单属性，确保响应式更新
      basicForm.value.companyName = data.companyName || ''
      basicForm.value.industry = data.industry || ''
      basicForm.value.scale = data.scale || ''
      basicForm.value.province = data.province || ''
      // 对于直辖市，city 应该显示为省份名称（如"北京市"而不是"市辖区"）
      const isDirectMunicipality = ['北京市', '上海市', '天津市', '重庆市'].includes(data.province)
      if (isDirectMunicipality && data.city === '市辖区') {
        basicForm.value.city = data.province
      } else {
        basicForm.value.city = data.city || ''
      }
      basicForm.value.district = data.district || ''
      // 确保 addressCode 是一个新的数组引用，触发组件更新
      basicForm.value.addressCode = addressCodes.length > 0 ? [...addressCodes] : null
      basicForm.value.detailAddress = data.detailAddress || ''
      basicForm.value.contactPerson = data.contactPerson || ''
      basicForm.value.contactPhone = data.contactPhone || ''
      basicForm.value.contactEmail = data.contactEmail || ''
      basicForm.value.website = data.website || ''
      basicForm.value.cooperationMode = data.cooperationMode ?? ''
      basicForm.value.auditStatus = data.auditStatus ?? null
      basicForm.value.isInternshipBase = data.isInternshipBase ?? companyField('isInternshipBase').defaultValue
      basicForm.value.hasTrainingBaseAgreement = data.hasTrainingBaseAgreement ?? companyField('hasTrainingBaseAgreement').defaultValue
      basicForm.value.acceptBackup = data.acceptBackup ?? companyField('acceptBackup').defaultValue
      basicForm.value.isAlumniUnit = data.isAlumniUnit ?? companyField('isAlumniUnit').defaultValue
      basicForm.value.alumniMembers = Array.isArray(data.alumniMembers) && data.alumniMembers.length
        ? data.alumniMembers.map(member => ({ name: member.name || '', phone: member.phone || '', position: member.position || '' }))
        : (basicForm.value.isAlumniUnit === 1 && data.alumniRepresentativeName
            ? [{
                name: data.alumniRepresentativeName,
                phone: data.alumniRepresentativePhone || '',
                position: data.alumniRepresentativePosition || ''
              }]
            : [])

      const logo = data.logo || ''
      const photos = data.photos ? JSON.parse(data.photos) : []
      const videos = data.videos ? JSON.parse(data.videos) : []

      // 使用 Object.assign 更新表单属性，保持响应式引用
      Object.assign(promotionForm.value, {
        logo: logo.startsWith('blob:') ? '' : logo,
        photos: photos.filter(p => !p.startsWith('blob:')),
        videos: videos.filter(v => !v.startsWith('blob:')),
        introduction: data.introduction || ''
      })
      
      logoFileList.value = logo ? [{
        uid: Date.now(),
        name: 'logo.png',
        url: logo,
        status: 'success'
      }] : []
      
      photoFileList.value = promotionForm.value.photos.map((url, index) => ({
        uid: `photo_${Date.now()}_${index}`,
        name: `photo_${index + 1}.png`,
        url: url,
        thumbnailUrl: url,
        status: 'success'
      }))
      
      videoFileList.value = promotionForm.value.videos.map((url, index) => ({
        uid: `video_${Date.now()}_${index}`,
        name: `video_${index + 1}.mp4`,
        url: url,
        status: 'success'
      }))
      
      console.log('=== 加载企业信息完成 ===')
      console.log('photos:', promotionForm.value.photos)
      console.log('videos:', promotionForm.value.videos)
      console.log('photoFileList:', photoFileList.value)
      console.log('videoFileList:', videoFileList.value)
    }
  }).catch(err => {
    ElMessage.error('加载企业信息失败: ' + err.message)
  }).finally(() => {
    loading.value = false
  })
}

const loadSchoolLiaisons = async () => {
  schoolLiaisonsLoading.value = true
  try {
    const res = await CompanyService.getSchoolLiaisons()
    schoolLiaisons.value = res.code === 200 && Array.isArray(res.data) ? res.data : []
  } catch (error) {
    schoolLiaisons.value = []
    console.warn('加载校内对接人失败，不影响企业资料展示:', error)
  } finally {
    schoolLiaisonsLoading.value = false
  }
}

const handleAddressChange = (e) => {
  // 确保 addressCode 是一个新的数组引用，触发组件更新
  basicForm.value.addressCode = e && e.length > 0 ? [...e] : null

  if (e && e.length > 0) {
    const provinceCode = e[0]
    const cityCode = e[1]
    const districtCode = e[2]

    basicForm.value.province = chinaData[provinceCode]?.label || ''
    basicForm.value.city = chinaData[cityCode]?.label || ''
    basicForm.value.district = chinaData[districtCode]?.label || ''
  } else {
    basicForm.value.province = ''
    basicForm.value.city = ''
    basicForm.value.district = ''
  }
}

// 名称归一：兼容“广东省/广东”“深圳市/深圳”等带/不带后缀的写法
const normalizeAreaLabel = (value) => String(value || '').replace(/(省|市|自治区|特别行政区|地区|自治州|盟)$/, '')
const matchesAreaLabel = (item, label) => Boolean(item?.label) && Boolean(label)
  && (item.label === label || normalizeAreaLabel(item.label) === normalizeAreaLabel(label))

// 省/市/区名称 → 级联组件所需的行政区划代码（与岗位发布页同一算法，兼容只给部分层级）
const resolveAddressCodes = (province, city, district) => {
  const findByLabel = (label, parentCode) => Object.keys(chinaData).find(
    code => matchesAreaLabel(chinaData[code], label) && (!parentCode || chinaData[code].parent === parentCode)
  )
  const findMunicipalityCityCode = (provinceCode) => Object.keys(chinaData).find(
    code => chinaData[code].label === '市辖区' && chinaData[code].parent === provinceCode
  )
  const isMunicipality = (name) => ['北京市', '上海市', '天津市', '重庆市']
    .some(city => normalizeAreaLabel(city) === normalizeAreaLabel(name))

  if (province) {
    const provinceCode = findByLabel(province)
    if (!provinceCode) return []
    const codes = [provinceCode]
    if (!city && !district) return codes

    let cityCode = city ? findByLabel(city, provinceCode) : undefined
    if (!cityCode && isMunicipality(province)) cityCode = findMunicipalityCityCode(provinceCode)
    if (!cityCode) {
      // 兼容“市辖区/县/城区”等不具名市级写法：直接在省下匹配区县（与旧加载逻辑一致）
      if (district) {
        const directDistrictCode = findByLabel(district, provinceCode)
        if (directDistrictCode) codes.push(directDistrictCode)
      }
      return codes
    }

    codes.push(cityCode)
    if (!district) return codes
    const districtCode = findByLabel(district, cityCode)
    if (districtCode) codes.push(districtCode)
    return codes
  }

  if (city) {
    const cityCode = findByLabel(city)
    if (!cityCode) return []
    let inferredProvinceCode = chinaData[cityCode]?.parent
    let cityLevelCode = cityCode
    if (!inferredProvinceCode && isMunicipality(city)) {
      // 直辖市：city 命中省级代码时，补上“市辖区”一级
      const municipalityCityCode = findMunicipalityCityCode(cityCode)
      if (!municipalityCityCode) return []
      inferredProvinceCode = cityCode
      cityLevelCode = municipalityCityCode
    }
    if (!inferredProvinceCode) return []
    const codes = [inferredProvinceCode, cityLevelCode]
    if (!district) return codes
    const districtCode = findByLabel(district, cityLevelCode)
    if (districtCode) codes.push(districtCode)
    return codes
  }

  if (district) {
    const districtCode = findByLabel(district)
    if (!districtCode) return []
    const cityCode = chinaData[districtCode]?.parent
    const inferredProvinceCode = cityCode ? chinaData[cityCode]?.parent : undefined
    if (!cityCode || !inferredProvinceCode) return []
    return [inferredProvinceCode, cityCode, districtCode]
  }

  return []
}

// 侧栏智能填表会直接写入 province/city/district，这里解析成级联组件需要的 addressCode；
// 手动选择级联时由 handleAddressChange 赋值，两者通过代码比对避免互相覆盖。
watch(
  () => [basicForm.value.province, basicForm.value.city, basicForm.value.district],
  ([province, city, district]) => {
    const codes = resolveAddressCodes(province || '', city || '', district || '')
    if (codes.length && JSON.stringify(codes) !== JSON.stringify(basicForm.value.addressCode)) {
      basicForm.value.addressCode = [...codes]
    }
  },
  { flush: 'post' }
)

// ===== 智能填表：基础信息表单注册到 AI 侧栏（只写响应式 model，不触发提交） =====
// 未覆盖项：校友成员嵌套数组（选“是”后手动添加）、
// 宣传资料 Logo/照片/视频（文件上传手动）、校内对接人（只读）
const formContext = useFormContext()
const basicFormRef = ref(null)
const companyFillFields = () => [
  { prop: 'companyName', label: '企业名称', type: 'input', required: true },
  { prop: 'industry', label: '所属行业', type: 'select', options: industryOptions },
  { prop: 'scale', label: '企业规模', type: 'select', options: scaleOptions },
  { prop: 'province', label: '企业地址省份', type: 'input', patternHint: '例如：广东省' },
  { prop: 'city', label: '企业地址城市', type: 'input', patternHint: '例如：深圳市；直辖市可填北京市/上海市等' },
  { prop: 'district', label: '企业地址区县', type: 'input', patternHint: '例如：南山区' },
  { prop: 'detailAddress', label: '详细地址（街道/门牌）', type: 'input',
    patternHint: '只填写街道、门牌等详细地址，不要把城市或区县写到这里' },
  { prop: 'contactPerson', label: '联系人', type: 'input', required: true },
  { prop: 'contactPhone', label: '联系电话', type: 'input', required: true },
  { prop: 'contactEmail', label: '联系邮箱', type: 'input' },
  { prop: 'website', label: '企业网站', type: 'input' },
  { prop: 'isAlumniUnit', label: '是否校友单位', type: 'radio', valueType: 'number',
    options: [{ label: '否', value: 0 }, { label: '是', value: 1 }],
    patternHint: '选是后需手动添加校友信息' },
  { prop: 'isInternshipBase', label: '挂牌实习基地', type: 'radio', valueType: 'number',
    options: [{ label: '否', value: 0 }, { label: '是', value: 1 }] },
  { prop: 'acceptBackup', label: '接受兜底', type: 'radio', valueType: 'number',
    options: [{ label: '不接受', value: 0 }, { label: '接受', value: 1 }] },
  { prop: 'hasTrainingBaseAgreement', label: '签署实训基地协议', type: 'radio', valueType: 'number',
    options: [{ label: '未签署', value: 0 }, { label: '已签署', value: 1 }] }
].map(field => {
  const definition = companyField(field.prop)
  return definition ? { ...field, label: definition.label, ...(definition.options ? { options: definition.options } : {}) } : field
})
const registerCompanyForm = () => {
  formContext.register({
    formId: 'company:info',
    title: '企业基本信息',
    fields: companyFillFields(),
    getFields: companyFillFields,
    getObject: () => companyId.value ? { type: 'company', id: String(companyId.value) } : null,
    getValidationSummary: () => ({
      missingFields: basicForm.value.isAlumniUnit === 1 && (!basicForm.value.alumniMembers.length || basicForm.value.alumniMembers.some(member => !member.name?.trim() || !member.phone?.trim() || !member.position?.trim())) ? ['校友姓名、电话和任职（需手动补充）'] : [],
      validationErrors: basicForm.value.contactEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(basicForm.value.contactEmail) ? ['联系邮箱格式不正确'] : [],
    }),
    getModel: () => basicForm.value,
    getFormEl: () => basicFormRef.value
  })
}
// AI 写入是否校友单位后补齐联动（与 @change 同一函数，幂等）
watch(() => basicForm.value.isAlumniUnit, (v) => {
  if (v !== undefined && v !== null) handleAlumniUnitChange(v)
})
// 仅基础信息页签展示关联表单，切到宣传资料则切换为企业简介表单，防误填
// 宣传资料的图片/视频为文件上传，需手动操作，仅企业简介文本可由语音填写
watch(activeTab, (tab) => {
  if (tab === 'basic') {
    formContext.unregister('company:promotion')
    registerCompanyForm()
  } else if (tab === 'promotion') {
    formContext.unregister('company:info')
    formContext.register({
      formId: 'company:promotion',
      title: '企业宣传资料',
      fields: [
        { prop: 'introduction', label: '企业简介', type: 'textarea' },
        { prop: 'logo', label: '企业 Logo', type: 'upload' },
        { prop: 'photos', label: '企业照片', type: 'upload' },
        { prop: 'videos', label: '宣传视频', type: 'upload' },
      ],
      getObject: () => companyId.value ? { type: 'company', id: String(companyId.value) } : null,
      getModel: () => promotionForm.value
    })
  } else {
    formContext.unregister('company:info')
    formContext.unregister('company:promotion')
  }
})

onMounted(() => {
  loadCompanyInfo()
  loadSchoolLiaisons()
  if (activeTab.value === 'basic') registerCompanyForm()
  else if (activeTab.value === 'promotion') {
    formContext.register({
      formId: 'company:promotion',
      title: '企业宣传资料',
      fields: [
        { prop: 'introduction', label: '企业简介', type: 'textarea' },
        { prop: 'logo', label: '企业 Logo', type: 'upload' },
        { prop: 'photos', label: '企业照片', type: 'upload' },
        { prop: 'videos', label: '宣传视频', type: 'upload' },
      ],
      getObject: () => companyId.value ? { type: 'company', id: String(companyId.value) } : null,
      getModel: () => promotionForm.value
    })
  }
})
onUnmounted(() => { formContext.unregister('company:info'); formContext.unregister('company:promotion') })
useAgentPageContext(() => ({ title: '企业信息', view: activeTab.value, object: null, filters: {} }))

watch(companyId, (newVal) => {
  if (newVal && !basicForm.value.companyName) {
    loadCompanyInfo()
  }
})
</script>

<template>
  <div class="company-info">
    <div class="page-header">
      <h2>企业信息</h2>
      <p>填写或修改企业资料</p>
    </div>

    <el-tabs v-model="activeTab" class="info-tabs" v-loading="loading">
      <el-tab-pane label="基础信息" name="basic" :lazy="true">
        <div class="form-card">
          <el-form ref="basicFormRef" :model="basicForm" label-width="120px" label-position="left">
            <el-form-item :label="companyField('companyName').label">
              <el-input v-model="basicForm.companyName" placeholder="请输入企业名称" />
            </el-form-item>

            <el-form-item :label="companyField('cooperationMode').label">
              <div class="tags-container">
                <el-tag v-for="tag in companyTags" :key="tag.prop + tag.label" :type="tag.type" :title="tag.description" size="large" effect="dark">{{ tag.label }}</el-tag>
              </div>
              <span class="tag-hint">标签根据当前合作方式和基础信息选项显示，可悬停查看含义</span>
            </el-form-item>

            <el-form-item :label="companyField('industry').label">
              <el-select v-model="basicForm.industry" placeholder="请选择所属行业" style="width: 100%">
                <el-option
                  v-for="item in industryOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item :label="companyField('scale').label">
              <el-select v-model="basicForm.scale" placeholder="请选择企业规模" style="width: 100%">
                <el-option
                  v-for="item in scaleOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item :label="companyField('address').label">
              <elui-china-area-dht 
                v-model="basicForm.addressCode" 
                @change="handleAddressChange" 
                placeholder="请选择省/市/区" 
                style="width: 100%" 
              />
            </el-form-item>

            <el-form-item :label="companyField('detailAddress').label">
              <el-input v-model="basicForm.detailAddress" placeholder="请输入详细地址，如街道、门牌号等" />
            </el-form-item>

            <el-form-item :label="companyField('contactPerson').label">
              <el-input v-model="basicForm.contactPerson" placeholder="请输入联系人姓名" />
            </el-form-item>

            <el-form-item :label="companyField('contactPhone').label">
              <el-input v-model="basicForm.contactPhone" placeholder="请输入联系电话" />
            </el-form-item>

            <el-form-item :label="companyField('contactEmail').label">
              <el-input v-model="basicForm.contactEmail" placeholder="请输入联系邮箱" />
            </el-form-item>

            <el-form-item :label="companyField('schoolLiaisons').label">
              <span v-if="schoolLiaisonsLoading" class="liaison-empty">加载中...</span>
              <div v-else-if="schoolLiaisons.length" class="school-liaison-list">
                <div v-for="liaison in schoolLiaisons" :key="liaison.teacherId" class="school-liaison-item">
                  <span class="school-liaison-name">{{ liaison.teacherName }}</span>
                  <span class="school-liaison-phone">{{ liaison.teacherPhone || '未填写电话' }}</span>
                </div>
              </div>
              <span v-else class="liaison-empty">暂未设置</span>
            </el-form-item>

            <el-form-item :label="companyField('website').label">
              <el-input v-model="basicForm.website" placeholder="请输入企业网站" />
            </el-form-item>

            <el-form-item :label="companyField('isAlumniUnit').label">
              <el-radio-group v-model="basicForm.isAlumniUnit" @change="handleAlumniUnitChange">
                <el-radio v-for="option in companyField('isAlumniUnit').options" :key="option.value" :label="option.value">{{ option.label }}</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item :label="companyField('isInternshipBase').label">
              <el-radio-group v-model="basicForm.isInternshipBase">
                <el-radio v-for="option in companyField('isInternshipBase').options" :key="option.value" :label="option.value">{{ option.label }}</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item :label="companyField('acceptBackup').label">
              <el-radio-group v-model="basicForm.acceptBackup">
                <el-radio v-for="option in companyField('acceptBackup').options" :key="option.value" :label="option.value">{{ option.label }}</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item :label="companyField('hasTrainingBaseAgreement').label" label-width="150px">
              <el-radio-group v-model="basicForm.hasTrainingBaseAgreement">
                <el-radio v-for="option in companyField('hasTrainingBaseAgreement').options" :key="option.value" :label="option.value">{{ option.label }}</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item v-if="basicForm.isAlumniUnit === 1" :label="companyField('alumniMembers').label">
              <div class="alumni-members">
                <div v-for="(member, index) in basicForm.alumniMembers" :key="index" class="alumni-member-card">
                  <div class="alumni-member-title">
                    <span>校友 {{ index + 1 }}</span>
                    <el-button text type="danger" :disabled="basicForm.alumniMembers.length === 1" @click="removeAlumniMember(index)">删除</el-button>
                  </div>
                  <el-input v-model="member.name" maxlength="50" placeholder="姓名" />
                  <el-input v-model="member.phone" maxlength="20" placeholder="联系电话" />
                  <el-input v-model="member.position" maxlength="100" placeholder="在企业的任职" />
                </div>
                <el-button type="primary" :icon="Plus" @click="addAlumniMember">添加校友</el-button>
              </div>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="handleSaveBasic">保存基础信息</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <el-tab-pane label="联系人管理" name="contacts" :lazy="true">
        <CompanyContacts v-if="activeTab === 'contacts'" />
      </el-tab-pane>

      <el-tab-pane label="宣传资料" name="promotion" :lazy="true">
        <div class="promotion-container">
          <div class="promotion-card">
            <div class="card-header">
              <el-icon class="card-icon" :size="24"><Star /></el-icon>
              <h3 class="card-title">企业Logo</h3>
            </div>
            <div class="card-body">
              <el-upload
                v-model:file-list="logoFileList"
                class="logo-uploader"
                :show-file-list="false"
                :limit="1"
                :on-change="handleLogoChange"
                :auto-upload="false"
                accept="image/*"
              >
                <div v-if="!promotionForm.logo" class="upload-placeholder">
                  <el-icon><Plus /></el-icon>
                  <div>上传Logo</div>
                </div>
                <img v-else :src="promotionForm.logo" class="logo-preview" />
              </el-upload>
              <el-button v-if="promotionForm.logo" type="danger" size="small" @click="removeLogo" style="margin-top: 8px;">
                <el-icon><Delete /></el-icon>
                删除Logo
              </el-button>
            </div>
          </div>

          <div class="promotion-card">
            <div class="card-header">
              <el-icon class="card-icon" :size="24"><Picture /></el-icon>
              <h3 class="card-title">企业照片</h3>
            </div>
            <div class="card-body">
              <div class="photo-header">
                <el-upload
                  class="photo-uploader"
                  :show-file-list="false"
                  :limit="9"
                  :on-change="handlePhotoChange"
                  :auto-upload="false"
                  accept="image/*"
                >
                  <el-button type="primary">
                    <el-icon><Upload /></el-icon>
                    上传照片
                  </el-button>
                </el-upload>
                <el-button v-if="photoFileList.length > 0" type="danger" size="small" @click="removeAllPhotos" :icon="Delete" />
              </div>
              <div v-if="photoFileList.length > 0" class="photo-grid">
                <div v-for="(file, index) in photoFileList" :key="file.uid" class="photo-item">
                  <div class="photo-preview-wrapper" @click="() => handlePhotoPreview(file)">
                    <img :src="file.url" class="photo-thumbnail" />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="promotion-card">
            <div class="card-header">
              <el-icon class="card-icon" :size="24"><VideoCamera /></el-icon>
              <h3 class="card-title">企业视频</h3>
            </div>
            <div class="card-body">
              <el-upload
                class="video-uploader"
                :show-file-list="false"
                :limit="3"
                :on-change="handleVideoChange"
                :auto-upload="false"
                accept="video/*"
              >
                <el-button type="primary">
                  <el-icon><Upload /></el-icon>
                  选择视频
                </el-button>
              </el-upload>
              <div v-if="videoFileList.length > 0" class="video-list">
                <div v-for="(file, index) in videoFileList" :key="index" class="video-item">
                  <div class="video-preview-wrapper" @click="() => handleVideoPreview(file)">
                    <video :src="file.url" class="video-thumbnail" muted />
                    <div class="video-play-icon">
                      <el-icon :size="40"><View /></el-icon>
                    </div>
                  </div>
                  <div class="video-info">
                    <span class="video-name">{{ file.name }}</span>
                    <el-button type="danger" size="small" @click="removeVideo(index)">删除</el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="promotion-card">
            <div class="card-header">
              <el-icon class="card-icon" :size="24"><Document /></el-icon>
              <h3 class="card-title">企业介绍</h3>
            </div>
            <div class="card-body">
              <el-input
                v-model="promotionForm.introduction"
                type="textarea"
                :rows="6"
                placeholder="请输入企业介绍，可以详细介绍企业文化、发展历程、业务范围等"
              />
            </div>
          </div>

          <div class="action-buttons">
            <el-button type="primary" @click="handleSavePromotion">保存宣传资料</el-button>
            <el-button type="success" @click="handleSaveAll">保存全部</el-button>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="预览" name="preview" :lazy="true">
        <div class="preview-card">
          <div class="company-preview">
            <div class="preview-header">
              <img v-if="promotionForm.logo" :src="promotionForm.logo" class="preview-logo" />
              <div class="preview-title">
                <h3>{{ basicForm.companyName || '企业名称' }}</h3>
                <div class="preview-tags">
                <el-tag v-for="tag in companyTags" :key="tag.prop + tag.label" :type="tag.type" :title="tag.description" size="large" effect="dark">{{ tag.label }}</el-tag>
              </div>
              </div>
            </div>

            <div class="preview-body">
              <div class="preview-section">
                <h4>基本信息</h4>
                <div class="preview-info-grid">
                  <div class="info-item">
                    <span class="info-label">所属行业：</span>
                    <span class="info-value">{{ getIndustryLabel(basicForm.industry) }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">企业规模：</span>
                    <span class="info-value">{{ getScaleLabel(basicForm.scale) }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">企业地址：</span>
                    <span class="info-value">{{ basicForm.province }}{{ basicForm.city }}{{ basicForm.district }}{{ basicForm.detailAddress }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">联系人：</span>
                    <span class="info-value">{{ basicForm.contactPerson || '-' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">联系电话：</span>
                    <span class="info-value">{{ basicForm.contactPhone || '-' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">联系邮箱：</span>
                    <span class="info-value">{{ basicForm.contactEmail || '-' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">企业网站：</span>
                    <span class="info-value">{{ basicForm.website || '-' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">校内对接人：</span>
                    <span class="info-value">{{ schoolLiaisons.length ? schoolLiaisons.map(l => l.teacherName + (l.teacherPhone ? '（' + l.teacherPhone + '）' : '')).join('、') : '暂未设置' }}</span>
                  </div>
                </div>
              </div>

              <div class="preview-section" v-if="promotionForm.introduction">
                <h4>企业介绍</h4>
                <p class="preview-introduction">{{ promotionForm.introduction }}</p>
              </div>

              <div class="preview-section" v-if="basicForm.isAlumniUnit === 1 && basicForm.alumniMembers.length">
                <h4>校友信息</h4>
                <div class="preview-info-grid">
                  <div class="info-item" v-for="(member, index) in basicForm.alumniMembers" :key="index">
                    <span class="info-label" style="min-width: auto;">校友 {{ index + 1 }}：</span>
                    <span class="info-value">{{ member.name }}（{{ member.position }}）{{ member.phone }}</span>
                  </div>
                </div>
              </div>

              <div class="preview-section" v-if="photoFileList.length > 0">
                <h4>企业照片</h4>
                <div class="preview-photo-grid">
                  <img 
                    v-for="(file, index) in photoFileList" 
                    :key="file.uid" 
                    :src="file.url" 
                    class="preview-photo"
                  />
                </div>
              </div>

              <div class="preview-section" v-if="videoFileList.length > 0">
                <h4>企业视频</h4>
                <div class="preview-video-list">
                  <div v-for="(file, index) in videoFileList" :key="index" class="preview-video-item">
                    <video :src="file.url" controls class="preview-video" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>

  <el-dialog
    v-model="photoPreviewVisible"
    title="照片预览"
    width="80%"
    :close-on-click-modal="true"
  >
    <div class="photo-preview-container">
      <img :src="currentPreviewUrl" class="photo-preview-image" />
    </div>
  </el-dialog>

  <el-dialog
    v-model="videoPreviewVisible"
    title="视频预览"
    width="80%"
    :close-on-click-modal="true"
  >
    <div class="video-preview-container">
      <video :src="currentPreviewUrl" controls class="video-preview" />
    </div>
  </el-dialog>
</template>

<style scoped>
.company-info {
  padding: 0;
  width: 100%;
  max-width: 100%;
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
  color: white;
}

.page-header p {
  margin: 0;
  font-size: 14px;
  opacity: 0.95;
}

.info-tabs {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

:deep(.el-tabs__header) {
  margin: 0;
  padding: 0 20px;
  background: #f5f7fa;
  border-bottom: 1px solid #e8e8e8;
}

:deep(.el-tabs__nav-wrap::after) {
  display: none;
}

:deep(.el-tabs__item) {
  padding: 0 24px;
  height: 50px;
  line-height: 50px;
  font-size: 15px;
  font-weight: 500;
  color: #606266;
}

:deep(.el-tabs__item.is-active) {
  color: #409EFF;
  font-weight: 600;
}

:deep(.el-tabs__active-bar) {
  background-color: #409EFF;
  height: 3px;
}

.form-card {
  padding: 32px;
  background: white;
}

.preview-card {
  padding: 32px;
  background: white;
}

.company-preview {
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  overflow: hidden;
}

.preview-header {
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  padding: 32px;
  display: flex;
  align-items: center;
  gap: 24px;
}

.preview-logo {
  width: 80px;
  height: 80px;
  border-radius: 8px;
  background: white;
  object-fit: contain;
  padding: 8px;
}

.preview-title h3 {
  margin: 0 0 12px 0;
  font-size: 24px;
  font-weight: 600;
  color: white;
}

.preview-tags {
  display: flex;
  gap: 8px;
}

.preview-body {
  padding: 32px;
}

.preview-section {
  margin-bottom: 32px;
}

.preview-section:last-child {
  margin-bottom: 0;
}

.preview-section h4 {
  margin: 0 0 16px 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  border-left: 4px solid #409EFF;
  padding-left: 12px;
}

.preview-info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.info-item {
  display: flex;
  gap: 8px;
}

.info-label {
  color: #909399;
  font-weight: 500;
  min-width: 100px;
}

.info-value {
  color: #303133;
  flex: 1;
}

.preview-introduction {
  margin: 0;
  line-height: 1.8;
  color: #606266;
  white-space: pre-wrap;
}

.preview-photo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.preview-photo {
  width: 100%;
  height: 150px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #e8e8e8;
}

.preview-video-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.preview-video-item {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e8e8e8;
}

.preview-video {
  width: 100%;
  max-height: 400px;
  object-fit: contain;
}

.content-layout {
  display: flex;
  gap: 24px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.left-panel {
  flex: 0 0 45%;
  min-width: 400px;
  border-right: 1px solid #e8e8e8;
}

.right-panel {
  flex: 1;
  min-width: 400px;
}

.panel-header {
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  color: white;
  padding: 16px 24px;
  border-bottom: 1px solid #e8e8e8;
}

.panel-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.info-collapse {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

:deep(.el-collapse-item__header) {
  font-size: 16px;
  font-weight: 600;
  padding: 16px 24px;
  background: #f5f7fa;
  border-bottom: 1px solid #e8e8e8;
}

:deep(.el-collapse-item__wrap) {
  border-bottom: none;
}

.photo-preview-container {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
}

.photo-preview-image {
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
}

.video-preview-container {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
}

.video-preview {
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
}

.video-list {
  margin-bottom: 16px;
}

.video-item {
  margin-bottom: 16px;
  padding: 12px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  background: #f9f9f9;
}

.video-preview-wrapper {
  position: relative;
  width: 100%;
  max-width: 400px;
  aspect-ratio: 16/9;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
}

.video-thumbnail {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.video-play-icon {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: white;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 50%;
  width: 80px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.video-info {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.video-name {
  flex: 1;
  font-size: 14px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.photo-header {
  display: flex;
  align-items: center;
  gap: 0;
}

.photo-grid {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 16px;
}

.photo-item {
  position: relative;
}

.photo-preview-wrapper {
  position: relative;
  width: 120px;
  height: 120px;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid #e8e8e8;
}

.photo-thumbnail {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.form-card {
  padding: 30px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: #333;
}

:deep(.el-input__wrapper) {
  border-radius: 6px;
}

:deep(.el-textarea__inner) {
  border-radius: 6px;
}

:deep(.el-select .el-input__wrapper) {
  border-radius: 6px;
}

.logo-uploader {
  display: flex;
  align-items: center;
}

:deep(.logo-uploader .el-upload) {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: border-color 0.3s;
}

:deep(.logo-uploader .el-upload:hover) {
  border-color: #409EFF;
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 120px;
  height: 120px;
  color: #8c939d;
  font-size: 14px;
}

.upload-placeholder .el-icon {
  font-size: 28px;
  margin-bottom: 8px;
}

.logo-preview {
  width: 120px;
  height: 120px;
  object-fit: cover;
  border-radius: 6px;
}

.photo-uploader,
.video-uploader {
  width: 100%;
}

:deep(.photo-uploader .el-upload-list--picture-card) {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

:deep(.photo-uploader .el-upload-list__item) {
  width: 120px;
  height: 120px;
  border-radius: 6px;
}

:deep(.photo-uploader .el-upload--picture-card) {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  width: 120px;
  height: 120px;
}

:deep(.photo-uploader .el-upload--picture-card:hover) {
  border-color: #409EFF;
}

:deep(.video-uploader .el-upload-list) {
  margin-top: 12px;
}

:deep(.video-uploader .el-upload-list__item) {
  margin-bottom: 12px;
}

:deep(.el-button) {
  border-radius: 6px;
  padding: 10px 20px;
  font-weight: 500;
}

:deep(.el-button--primary) {
  background: linear-gradient(135deg, #409EFF 0%, #52c41a 100%);
  border: none;
}

:deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, #66b1ff 0%, #73d13d 100%);
}

.tag-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}

.tags-container {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.tags-container :deep(.el-tag) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 100px;
  padding: 0 16px;
  height: 32px;
  line-height: 32px;
}

.internship-base-tag {
  position: relative;
}

.internship-base-tag .tag-icon {
  margin-right: 4px;
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
}

.promotion-container {
  padding: 32px;
  background: white;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.promotion-card {
  background: white;
  border: 1px solid #e8e8e8;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
  overflow: hidden;
}

.promotion-card:hover {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 24px;
  background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%);
  border-bottom: 1px solid #e8e8e8;
}

.card-icon {
  color: #409EFF;
  flex-shrink: 0;
}

.card-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.card-body {
  padding: 24px;
}

.action-buttons {
  display: flex;
  gap: 12px;
  justify-content: center;
  padding: 24px 32px;
  background: white;
  border-top: 1px solid #e8e8e8;
  margin-top: 8px;
  grid-column: 1 / -1;
  border-radius: 0 0 12px 12px;
}

.alumni-members {
  width: 100%;
  display: grid;
  gap: 12px;
}

.school-liaison-list {
  width: 100%;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.school-liaison-item {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  border: 1px solid #d9ecff;
  border-radius: 6px;
  background: #ecf5ff;
}

.school-liaison-name {
  color: #303133;
  font-weight: 600;
}

.school-liaison-phone,
.liaison-empty {
  color: #909399;
}

.alumni-member-card {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fafafa;
}

.alumni-member-title {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

@media (max-width: 720px) {
  .alumni-member-card {
    grid-template-columns: 1fr;
  }
}

/* 手机端：企业资料双栏改单列 */
@media screen and (max-width: 768px) {
  .promotion-container {
    grid-template-columns: 1fr;
    padding: 16px;
    gap: 16px;
  }

  .card-body {
    padding: 16px;
  }

  .preview-info-grid {
    grid-template-columns: 1fr;
  }

  .action-buttons {
    flex-wrap: wrap;
    padding: 16px;
  }

  .action-buttons .el-button {
    flex: 1;
    min-width: 120px;
    margin-left: 0;
  }
}
</style>
