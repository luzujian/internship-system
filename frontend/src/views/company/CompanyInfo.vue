<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import { Star, Plus, Upload, View, Delete, Picture, VideoCamera, Document } from '@element-plus/icons-vue'
import { EluiChinaAreaDht } from 'elui-china-area-dht'
import CompanyService from '@/api/CompanyService'
import UploadService from '@/api/UploadService'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// 初始化省市区数据
const chinaData = new EluiChinaAreaDht.ChinaArea().chinaAreaflat

// 辅助函数：通过行政区划代码前缀匹配层级关系
// 中国行政区划代码规则：省级 XX0000, 市级 XXXX00, 区级 XXXXXX
const findProvinceCode = (provinceName) => {
  if (!provinceName) return null
  return Object.keys(chinaData).find(code => {
    return chinaData[code].label === provinceName && code.endsWith('0000')
  }) || null
}

const findCityCode = (cityName, provinceCode) => {
  if (!cityName || !provinceCode) return null
  const provincePrefix = provinceCode.slice(0, 2)
  // 通过省份前缀 + 名称匹配城市
  return Object.keys(chinaData).find(code => {
    const info = chinaData[code]
    // 市级代码格式：XXYY00，其中 XX 是省份前缀
    return code.startsWith(provincePrefix) &&
           code.endsWith('00') &&
           !code.endsWith('0000') &&
           (info.label === cityName ||
            info.label === cityName.slice(0, -1) || // 去掉"市"后缀
            info.label === cityName + '市') // 添加"市"后缀
  }) || null
}

// 直辖市特殊处理：返回"市辖区"代码
const getDirectMunicipalityCityCode = (provinceName) => {
  const provinceCode = findProvinceCode(provinceName)
  if (!provinceCode) return null
  // 在直辖市中，"市辖区"的父级是省级代码
  return Object.keys(chinaData).find(code => {
    const info = chinaData[code]
    return info.parent === provinceCode && info.label === '市辖区'
  }) || null
}

const findDistrictCode = (districtName, cityCode) => {
  if (!districtName || !cityCode) return null
  const cityPrefix = cityCode.slice(0, 4)
  // 通过城市前缀 + 名称匹配区县
  return Object.keys(chinaData).find(code => {
    const info = chinaData[code]
    // 区级代码格式：XXXXYY，其中 XXXX 是城市前缀
    return code.startsWith(cityPrefix) &&
           !code.endsWith('00') &&
           (info.label === districtName ||
            info.label === districtName.replace(/[区县]$/, '')) // 去掉后缀
  }) || null
}

// 直接通过名称查找区县代码（当没有城市信息时）
const findDistrictByProvince = (districtName, provinceCode) => {
  if (!districtName || !provinceCode) return null
  const provincePrefix = provinceCode.slice(0, 2)
  return Object.keys(chinaData).find(code => {
    const info = chinaData[code]
    // 直辖市区县或省直辖县级行政区：XXYYYY，其中 XX 是省份前缀
    return code.startsWith(provincePrefix) &&
           !code.endsWith('0000') &&
           !code.endsWith('00') &&
           (info.label === districtName ||
            info.label === districtName.replace(/[区县]$/, ''))
  }) || null
}

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

const activeTab = ref('basic')
const loading = ref(false)

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
  cooperationMode: 'mutual_choice',
  isInternshipBase: 0,
  acceptBackup: 0,
  maxBackupStudents: 0
})

const cooperationModeOptions = [
  { label: '学生自主联系', value: 'student_contact', type: 'warning' },
  { label: '双向选择', value: 'mutual_choice', type: 'success' }
]

const getInternshipBaseText = (value) => {
  switch (value) {
    case 1: return '国家级实习基地'
    case 2: return '省级实习基地'
    default: return null
  }
}

const getInternshipBaseType = (value) => {
  switch (value) {
    case 1: return 'danger'
    case 2: return 'warning'
    default: return 'info'
  }
}

const getAcceptBackupText = (value) => {
  return value === 1 ? '接受兜底' : null
}

const getTagText = (mode) => {
  const option = cooperationModeOptions.find(item => item.value === mode)
  return option ? option.label : '双向选择'
}

const getTagType = (mode) => {
  const option = cooperationModeOptions.find(item => item.value === mode)
  return option ? option.type : 'success'
}

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

const industryOptions = [
  { label: '互联网/IT', value: 'internet' },
  { label: '金融', value: 'finance' },
  { label: '教育', value: 'education' },
  { label: '医疗', value: 'medical' },
  { label: '制造业', value: 'manufacturing' },
  { label: '服务业', value: 'service' },
  { label: '房地产', value: 'realestate' },
  { label: '能源', value: 'energy' },
  { label: '其他', value: 'other' }
]

const scaleOptions = [
  { label: '1-50人', value: '1-50' },
  { label: '51-100人', value: '51-100' },
  { label: '101-500人', value: '101-500' },
  { label: '501-1000人', value: '501-1000' },
  { label: '1000人以上', value: '1000+' }
]

const logoFileList = ref([])
const photoFileList = ref([])
const videoFileList = ref([])

const handleLogoChange = (file) => {
  logoFileList.value = [file]
  UploadService.uploadImage(file.raw).then(res => {
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
  
  UploadService.uploadImages(files).then(res => {
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
  
  UploadService.uploadFiles(files).then(res => {
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
    cooperationMode: basicForm.value.cooperationMode,
    isInternshipBase: basicForm.value.isInternshipBase,
    acceptBackup: basicForm.value.acceptBackup,
    maxBackupStudents: basicForm.value.maxBackupStudents
  }

  console.log('=== 保存基础信息 ===')
  console.log('发送的数据:', data)
  console.log('companyId:', companyId.value)
  
  CompanyService.updateCompanyInfo(data).then(res => {
    if (res.code === 200) {
      ElMessage.success('基础信息保存成功')
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
    cooperationMode: basicForm.value.cooperationMode,
    isInternshipBase: basicForm.value.isInternshipBase,
    acceptBackup: basicForm.value.acceptBackup,
    maxBackupStudents: basicForm.value.maxBackupStudents,
    logo: promotionForm.value.logo,
    photos: JSON.stringify(promotionForm.value.photos),
    videos: JSON.stringify(promotionForm.value.videos),
    introduction: promotionForm.value.introduction
  }
  
  CompanyService.updateCompanyInfo(data).then(res => {
    if (res.code === 200) {
      ElMessage.success('企业信息保存成功')
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
      
      const addressCodes = []
      console.log('开始解析省市区代码...')

      if (data.province) {
        const provinceCode = findProvinceCode(data.province)
        if (provinceCode) {
          addressCodes.push(provinceCode)

          // 检查是否是直辖市
          const isDirectMunicipality = ['北京市', '上海市', '天津市', '重庆市'].includes(data.province)

          if (isDirectMunicipality) {
            // 直辖市特殊处理：添加"市辖区"代码
            const cityCode = getDirectMunicipalityCityCode(data.province)
            if (cityCode) {
              addressCodes.push(cityCode)
              if (data.district) {
                const districtCode = findDistrictCode(data.district, cityCode)
                if (districtCode) {
                  addressCodes.push(districtCode)
                } else {
                  console.warn('无法解析区县代码:', data.district)
                }
              }
            } else {
              console.warn('无法解析直辖市辖区代码:', data.province)
            }
          } else {
            // 非直辖市正常处理
            const isDirectDistrict = data.city === '市辖区' || data.city === '县' || data.city === '城区'

            if (data.city && !isDirectDistrict) {
              const cityCode = findCityCode(data.city, provinceCode)
              if (cityCode) {
                addressCodes.push(cityCode)
                if (data.district) {
                  const districtCode = findDistrictCode(data.district, cityCode)
                  if (districtCode) {
                    addressCodes.push(districtCode)
                  } else {
                    console.warn('无法解析区县代码:', data.district)
                  }
                }
              } else {
                console.warn('无法解析城市代码:', data.city)
              }
            } else if (data.district) {
              // 直接匹配区县（适用于"市辖区"或省直辖的情况）
              const districtCode = findDistrictByProvince(data.district, provinceCode)
              if (districtCode) {
                addressCodes.push(districtCode)
              } else {
                console.warn('无法直接匹配区县代码:', data.district)
              }
            }
          }
        } else {
          console.warn('无法解析省份代码:', data.province)
        }
      }

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
      basicForm.value.cooperationMode = data.cooperationMode || 'mutual_choice'
      basicForm.value.isInternshipBase = data.isInternshipBase || 0
      basicForm.value.acceptBackup = data.acceptBackup || 0
      basicForm.value.maxBackupStudents = data.maxBackupStudents || 0

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

onMounted(() => {
  loadCompanyInfo()
})

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
      <el-tab-pane label="基础信息" name="basic">
        <div class="form-card">
          <el-form :model="basicForm" label-width="120px" label-position="left">
            <el-form-item label="企业名称">
              <el-input v-model="basicForm.companyName" placeholder="请输入企业名称" />
            </el-form-item>

            <el-form-item label="企业标签">
              <div class="tags-container">
                <el-tag :type="getTagType(basicForm.cooperationMode)" size="large" effect="dark">
                  {{ getTagText(basicForm.cooperationMode) }}
                </el-tag>
                <el-tag 
                  v-if="basicForm.isInternshipBase > 0" 
                  :type="getInternshipBaseType(basicForm.isInternshipBase)" 
                  size="large" 
                  effect="dark"
                  class="internship-base-tag"
                >
                  <el-icon class="tag-icon"><Star /></el-icon>
                  {{ getInternshipBaseText(basicForm.isInternshipBase) }}
                </el-tag>
                <el-tag 
                  v-if="basicForm.acceptBackup === 1" 
                  type="danger" 
                  size="large" 
                  effect="dark"
                  class="accept-backup-tag"
                >
                  {{ getAcceptBackupText(basicForm.acceptBackup) }}
                </el-tag>
              </div>
              <span class="tag-hint">此标签由系统根据注册时间和选择自动设置</span>
            </el-form-item>

            <el-form-item label="所属行业">
              <el-select v-model="basicForm.industry" placeholder="请选择所属行业" style="width: 100%">
                <el-option
                  v-for="item in industryOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="企业规模">
              <el-select v-model="basicForm.scale" placeholder="请选择企业规模" style="width: 100%">
                <el-option
                  v-for="item in scaleOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="企业地址">
              <elui-china-area-dht 
                v-model="basicForm.addressCode" 
                @change="handleAddressChange" 
                placeholder="请选择省/市/区" 
                style="width: 100%" 
              />
            </el-form-item>

            <el-form-item label="详细地址">
              <el-input v-model="basicForm.detailAddress" placeholder="请输入详细地址，如街道、门牌号等" />
            </el-form-item>

            <el-form-item label="联系人">
              <el-input v-model="basicForm.contactPerson" placeholder="请输入联系人姓名" />
            </el-form-item>

            <el-form-item label="联系电话">
              <el-input v-model="basicForm.contactPhone" placeholder="请输入联系电话" />
            </el-form-item>

            <el-form-item label="联系邮箱">
              <el-input v-model="basicForm.contactEmail" placeholder="请输入联系邮箱" />
            </el-form-item>

            <el-form-item label="企业网站">
              <el-input v-model="basicForm.website" placeholder="请输入企业网站" />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="handleSaveBasic">保存基础信息</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <el-tab-pane label="宣传资料" name="promotion">
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
              <div v-if="photoFileList.length > 0" class="photo-grid">
                <div v-for="(file, index) in photoFileList" :key="file.uid" class="photo-item">
                  <div class="photo-preview-wrapper" @click="() => handlePhotoPreview(file)">
                    <img :src="file.url" class="photo-thumbnail" />
                  </div>
                  <div class="photo-actions">
                    <el-button type="danger" size="small" @click="() => removePhoto(index)">
                      <el-icon><Delete /></el-icon>
                    </el-button>
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

      <el-tab-pane label="预览" name="preview">
        <div class="preview-card">
          <div class="company-preview">
            <div class="preview-header">
              <img v-if="promotionForm.logo" :src="promotionForm.logo" class="preview-logo" />
              <div class="preview-title">
                <h3>{{ basicForm.companyName || '企业名称' }}</h3>
                <div class="preview-tags">
                  <el-tag :type="getTagType(basicForm.cooperationMode)" size="small">
                    {{ getTagText(basicForm.cooperationMode) }}
                  </el-tag>
                  <el-tag 
                    v-if="basicForm.isInternshipBase > 0" 
                    :type="getInternshipBaseType(basicForm.isInternshipBase)" 
                    size="small"
                  >
                    {{ getInternshipBaseText(basicForm.isInternshipBase) }}
                  </el-tag>
                  <el-tag 
                    v-if="basicForm.acceptBackup === 1" 
                    type="danger" 
                    size="small"
                  >
                    {{ getAcceptBackupText(basicForm.acceptBackup) }}
                  </el-tag>
                </div>
              </div>
            </div>

            <div class="preview-body">
              <div class="preview-section">
                <h4>基本信息</h4>
                <div class="preview-info-grid">
                  <div class="info-item">
                    <span class="info-label">所属行业：</span>
                    <span class="info-value">{{ basicForm.industry || '-' }}</span>
                  </div>
                  <div class="info-item">
                    <span class="info-label">企业规模：</span>
                    <span class="info-value">{{ basicForm.scale || '-' }}</span>
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
                </div>
              </div>

              <div class="preview-section" v-if="promotionForm.introduction">
                <h4>企业介绍</h4>
                <p class="preview-introduction">{{ promotionForm.introduction }}</p>
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

.info-tabs {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

:deep(.el-tabs__header) {
  margin: 0;
  background: #f5f7fa;
  border-bottom: 1px solid #e8e8e8;
}

:deep(.el-tabs__nav-wrap::after) {
  display: none;
}

:deep(.el-tabs__item) {
  font-size: 16px;
  font-weight: 600;
  color: #606266;
  padding: 0 32px;
  height: 56px;
  line-height: 56px;
}

:deep(.el-tabs__item.is-active) {
  color: #409EFF;
  background: white;
}

:deep(.el-tabs__active-bar) {
  height: 3px;
  background: #409EFF;
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

.photo-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 8px;
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
  display: flex;
  flex-direction: column;
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
  padding: 24px 0;
  background: white;
  border-top: 1px solid #e8e8e8;
  margin-top: 8px;
  border-radius: 0 0 12px 12px;
}
</style>
