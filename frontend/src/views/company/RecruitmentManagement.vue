<script setup lang="ts">
import { useAgentPageContext } from '@/agent/useAgentPageContext';
import { useFormContext } from '@/agent/formContext';
import type { FormFieldDescriptor } from '@/agent/types';
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  VideoPause,
  Message,
  MagicStick,
  DocumentCopy,
} from '@element-plus/icons-vue';
import { usePositionStore } from '../../store/position';
import { useAuthStore } from '../../store/auth';
import { listCompanyContacts, type CompanyContact } from '@/api/companyContacts';
import { useRouter } from 'vue-router';
import positionApi from '../../api/PositionService';
import studentApi from '../../api/StudentUserService';
import PositionCategoryService from '../../api/positionCategory';
import { useIsMobile } from '@/composables/useIsMobile';
import jobPostingAiService from '../../api/JobPostingAiService';
import {
  JOB_POSTING_CONFIDENCE_THRESHOLD,
  decideJobPostingAutoFill,
} from '../../utils/jobPostingAutoFill.js';
import { normalizeSalaryUnit } from '../../utils/salaryRange.js';
import { normalizeInterviewMethods, serializeInterviewMethods } from '../../utils/interviewMethods.js';
import {
  commitAllowCreateText,
  withModelOption,
  readSelectInputText,
} from '../../utils/allowCreate';
import { EluiChinaAreaDht } from 'elui-china-area-dht';

console.log('[RecruitmentManagement] 组件模块加载');

const chinaData = new EluiChinaAreaDht.ChinaArea().chinaAreaflat;

const positionStore = usePositionStore();
const authStore = useAuthStore();
const router = useRouter();
const contacts = ref<CompanyContact[]>([]);
const contactsLoading = ref(false);
const contactsError = ref('');
const fetchContacts = async () => {
  contactsLoading.value = true;
  contactsError.value = '';
  try {
    const response = await listCompanyContacts();
    if (response.code !== 200) throw new Error(response.message || response.msg || '加载联系人失败');
    contacts.value = response.data || [];
  } catch (error) {
    contacts.value = [];
    contactsError.value = error instanceof Error ? error.message : '加载联系人失败，请重试';
  } finally { contactsLoading.value = false; }
};
const contactOptions = computed(() => [
  { label: '使用企业原联系人', value: 0 },
  ...contacts.value.map(contact => ({ label: `${contact.name}（${contact.phone}）`, value: contact.id }))
]);
const selectedContact = computed(() => contacts.value.find(contact => contact.id === positionForm.value.contactId));
const isMobile = useIsMobile();

// 专业选项列表
const majorOptions = ref([]);

// 从 auth store 获取当前企业用户 ID
const companyId = computed(() => {
  if (!authStore.token || authStore.role !== 'ROLE_COMPANY') return null;
  if (authStore.user?.id) {
    return parseInt(authStore.user.id);
  }
  // 当无法获取企业 ID 时，尝试从 localStorage 获取
  const storedCompanyId = localStorage.getItem('company_companyId_COMPANY');
  if (storedCompanyId) {
    return parseInt(storedCompanyId);
  }
  // 如果仍然无法获取，返回 null 并显示错误提示
  ElMessage.error('未获取到当前登录企业 ID，请重新登录');
  return null;
});

const dialogVisible = ref(false);
const dialogTitle = ref('发布岗位');
const dialogType = ref('create');
const positionFormRef = ref(null);
// P2 allow-create select refs
// 对话框操作加载状态
const loading = ref(false);
// AI生成岗位描述加载状态
const generatingDescription = ref(false);
// AI岗位识别状态
const postingText = ref('');
const parsingPosting = ref(false);
const pastingPosting = ref(false);
const aiFilledFields = ref<string[]>([]);
const aiFilledValues = ref<Record<string, any>>({});
const aiParseNotices = ref<string[]>([]);
const hasInterviewTemplate = ref(false);

const JOB_POSTING_FIELD_LABELS: Record<string, string> = {
  positionName: '岗位名称',
  department: '岗位类别',
  positionType: '岗位类型',
  detailAddress: '详细地址',
  description: '岗位描述',
  requirements: '岗位要求',
  internshipEnvironment: '实习环境',
  interviewTime: '面试时间',
  interviewLocation: '面试地点',
  interviewMethod: '面试方式',
  interviewRemark: '面试备注',
  plannedRecruit: '招聘人数',
  providesAccommodation: '是否提供住宿',
  requiredMajors: '专业要求',
  salaryUnit: '薪资单位',
  salaryMin: '最低薪资',
  salaryMax: '最高薪资',
  address: '工作地点',
};

const fieldLabel = (field: string) => JOB_POSTING_FIELD_LABELS[field] || field;

// 面试信息对话框
const interviewDialogVisible = ref(false);
const interviewDialogTitle = ref('填写面试信息');
const interviewLoading = ref(false);
const interviewForm = ref({
  interviewTime: '',
  interviewLocation: '',
  interviewMethod: ['线下'],
  remark: '',
});

const interviewMethodOptions = [
  { label: '线下', value: '线下' },
  { label: '线上视频', value: '线上视频' },
  { label: '电话面试', value: '电话面试' },
];

const positionForm = ref({
  id: null,
  contactId: 0,
  positionName: '',
  department: '',
  positionType: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  salaryMin: '',
  salaryMax: '',
  salaryUnit: 'K/月',
  description: '',
  requirements: '',
  plannedRecruit: 1,
  recruitedCount: 0,
  remainingQuota: 0,
  status: 'active',
  addressCode: [],
  interviewTime: '',
  interviewLocation: '',
  interviewMethod: ['线下'],
  interviewRemark: '',
  internshipEnvironment: '',
  providesAccommodation: 0,
  requiredMajors: [],
  categoryId: null,
});

let postingParseController: AbortController | null = null;
let postingFormRevision = 0;
const postingOwner = () => `${authStore.role || ''}:${authStore.user?.id || ''}:${authStore.user?.username || ''}`;
const cancelPostingParse = () => {
  postingFormRevision++;
  postingParseController?.abort();
  postingParseController = null;
  parsingPosting.value = false;
};
// Any edit (including edit then revert), reopened dialog, or account change invalidates the old parse.
watch([positionForm, dialogVisible, postingOwner, () => Boolean(authStore.token), postingText], cancelPostingParse, { deep: true, flush: 'sync' });
onUnmounted(cancelPostingParse);

// ===== 智能填表：岗位发布/编辑弹窗注册到 AI 侧栏（只写响应式 model，不触发提交） =====
// 未覆盖字段说明：面试时间（datetime 字符串格式）、id/名额计数/状态（系统管理）——需手动操作或由系统维护；
// 省市区通过 province/city/district 字段解析为工作地点级联值，招聘专业按数组写入多选控件。
const formContext = useFormContext();
const positionFillFields = (): FormFieldDescriptor[] => [
  { prop: 'contactId', label: '岗位联系人', type: 'select', options: contactOptions.value },
  {
    prop: 'positionName',
    label: '岗位名称',
    type: 'select',
    required: true,
    allowCustom: true,
    options: positionNameOptions.value,
  },
  {
    prop: 'department',
    label: '岗位类别',
    type: 'select',
    required: true,
    allowCustom: true,
    options: categoryOptions.value,
  },
  {
    prop: 'positionType',
    label: '岗位类型',
    type: 'select',
    options: positionTypeOptions,
  },
  {
    prop: 'province',
    label: '工作地点省份',
    type: 'input',
    patternHint: '例如：广东省',
  },
  {
    prop: 'city',
    label: '工作地点城市',
    type: 'input',
    patternHint: '例如：深圳市',
  },
  {
    prop: 'district',
    label: '工作地点区县',
    type: 'input',
    patternHint: '例如：南山区',
  },
  {
    prop: 'detailAddress',
    label: '详细地址（街道/门牌）',
    type: 'input',
    patternHint: '只填写街道、门牌等详细地址，不要把城市或区县写到这里',
  },
  {
    prop: 'salaryUnit',
    label: '薪资单位',
    type: 'select',
    options: salaryUnitOptions,
  },
  { prop: 'salaryMin', label: '最低薪资', type: 'number', valueType: 'number', min: 0 },
  { prop: 'salaryMax', label: '最高薪资', type: 'number', valueType: 'number', min: 0 },
  {
    prop: 'plannedRecruit',
    label: '计划招聘人数',
    type: 'number',
    valueType: 'number',
    min: 1, max: 100, integer: true,
  },
  {
    prop: 'requiredMajors',
    label: '招聘专业',
    type: 'select',
    valueType: 'array',
    required: true,
    options: [
      { label: '不限制专业', value: '不限制专业' },
      ...majorOptions.value.map((item: any) => ({
        label: item.name,
        value: item.name,
      })),
    ],
    patternHint: '企业希望招聘的学生专业，可多选系统专业；选择“不限制专业”表示不限定学生专业。它不是岗位类别。',
  },
  { prop: 'description', label: '岗位描述', type: 'textarea' },
  { prop: 'requirements', label: '任职要求', type: 'textarea' },
  {
    prop: 'internshipEnvironment',
    label: '实习环境',
    type: 'select',
    options: ['办公室', '车间', '医院', '外派'].map((v) => ({
      label: v,
      value: v,
    })),
  },
  {
    prop: 'providesAccommodation',
    label: '是否提供住宿',
    type: 'radio',
    valueType: 'number',
    options: [
      { label: '否', value: 0 },
      { label: '是', value: 1 },
    ],
  },
  {
    prop: 'interviewMethod',
    label: '面试方式',
    type: 'select',
    valueType: 'array',
    options: interviewMethodOptions,
  },
  { prop: 'interviewLocation', label: '面试地点', type: 'input' },
  {
    prop: 'interviewTime',
    label: '面试时间',
    type: 'datetime',
    valueType: 'string',
    patternHint: '格式 YYYY-MM-DD HH:mm:ss，例如 2026-09-25 14:30:00',
  },
  { prop: 'interviewRemark', label: '备注', type: 'textarea' },
];
const registerPositionForm = (title) => {
  formContext.register({
    formId: 'company:position',
    title,
    fields: positionFillFields(),
    getFields: positionFillFields,
    getObject: () => positionForm.value.id ? { type: 'company_job', id: String(positionForm.value.id) } : null,
    getValidationSummary: () => {
      const value = positionForm.value;
      const validationErrors: string[] = [];
      if (value.salaryUnit !== '面议' && value.salaryMin !== '' && value.salaryMax !== '' && Number(value.salaryMin) > Number(value.salaryMax)) validationErrors.push('最低薪资不能高于最高薪资');
      if (value.requiredMajors?.includes('不限制专业') && value.requiredMajors.length > 1) validationErrors.push('不限制专业不能与具体专业同时选择');
      if ((value.province || value.city || value.district) && !resolveAddressCodes(value.province, value.city, value.district).length) validationErrors.push('工作地点未匹配行政区划，请手动选择');
      return { missingFields: [], validationErrors };
    },
    getModel: () => positionForm.value,
    getFormEl: () => positionFormRef.value,
  });
};
// AI 填入岗位类别后联动刷新岗位名称下拉（与 @change 同一函数，有缓存 guard，幂等）
watch(
  () => positionForm.value.department,
  (v) => {
    if (v && dialogVisible.value) void handleDepartmentChange(v);
  },
);
watch(dialogVisible, (open) => {
  if (!open) formContext.unregister('company:position');
});
onUnmounted(() => formContext.unregister('company:position'));

// ===== 智能填表：面试信息模板弹窗注册到 AI 侧栏（只写响应式 model，不触发提交） =====
// interviewTime 为 datetime 字符串（value-format），声明为 input + patternHint 避免日期归一错乱；以实测为准
const interviewFormRef = ref(null);
const interviewFillFields = (): FormFieldDescriptor[] => [
  {
    prop: 'interviewTime',
    label: '面试时间',
    type: 'datetime',
    valueType: 'string',
    patternHint: '格式 YYYY-MM-DD HH:mm:ss',
  },
  { prop: 'interviewLocation', label: '面试地点', type: 'input' },
  {
    prop: 'interviewMethod',
    label: '面试方式',
    type: 'select',
    valueType: 'array',
    options: interviewMethodOptions,
  },
  { prop: 'remark', label: '备注', type: 'textarea' },
];
const registerInterviewForm = () => {
  formContext.register({
    formId: 'company:interview-template',
    title: '面试信息模板',
    fields: interviewFillFields(),
    getFields: interviewFillFields,
    getModel: () => interviewForm.value,
    getFormEl: () => interviewFormRef.value,
  });
};
watch(interviewDialogVisible, (open) => {
  if (!open) formContext.unregister('company:interview-template');
});
onUnmounted(() => formContext.unregister('company:interview-template'));

const addressNameToCode = computed(() => {
  const map: Record<string, string> = {};
  Object.entries(chinaData as Record<string, { label: string }>).forEach(
    ([code, item]) => {
      if (item.label) {
        map[item.label] = code;
      }
    },
  );
  return map;
});

const tableData = computed(() => positionStore.positions || []);

const currentPage = ref(1);
const pageSize = ref(10);
watch(pageSize, () => { currentPage.value = 1; });

const paginatedTableData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  const end = start + pageSize.value;
  return filteredTableData.value.slice(start, end);
});

const searchResults = ref([]);
const hasSearched = ref(false);

const formatWorkLocation = (item) => {
  const parts = [];
  if (item.province && item.province !== 'None') parts.push(item.province);
  if (item.city && item.city !== 'None') parts.push(item.city);
  if (item.district && item.district !== 'None') parts.push(item.district);
  if (item.detailAddress) parts.push(item.detailAddress);
  return parts.join('');
};

const formatSalaryDisplay = (item) => {
  const unit = normalizeSalaryUnit(item.salaryUnit);
  if (unit === '面议') return '面议';
  const min = item.salaryMin;
  const max = item.salaryMax;
  if (min && max) return `${min}-${max}${unit}`;
  if (min) return `${min}${unit}起`;
  if (max) return `最高${max}${unit}`;
  return '面议';
};

const formatDate = (date) => {
  if (!date) return '';
  const d = new Date(date);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

const filteredTableData = computed(() => {
  if (hasSearched.value) {
    return [...searchResults.value];
  }
  return [...tableData.value];
});

const searchForm = ref({
  keyword: '',
  status: '',
  department: '',
});
const showMobileFilters = ref(false);
const activeFilterCount = computed(() => [searchForm.value.department, searchForm.value.status].filter(Boolean).length);

// 岗位类别选项（从管理员端岗位管理获取）
const categoryOptions = ref<{ label: string; value: string }[]>([]);
const categoryList = ref<{ id: number; name: string }[]>([]); // 存储完整类别信息（含ID）
const loadingCategories = ref(false);

// 按类别 ID 存储岗位名称列表（用于发布岗位弹窗的岗位名称下拉）
const categoryPositionsMap = ref(
  new Map<number, { label: string; value: string }[]>(),
);
const loadingCategoryPositions = ref(false);

// 获取岗位类别列表（用于所属部门下拉框）
const fetchCategories = async () => {
  loadingCategories.value = true;
  try {
    const result = await PositionCategoryService.getAllCategoriesPublic();
    console.log('[fetchCategories] 获取岗位类别返回:', result);
    // 公开接口直接返回 Result.success(categories)，结构是 { code: 200, data: [...] }
    if (result && result.code === 200) {
      const categories = Array.isArray(result.data) ? result.data : [];
      console.log('[fetchCategories] 岗位类别数据:', categories);
      categoryList.value = categories;
      categoryOptions.value = categories.map((cat) => ({
        label: cat.name,
        value: cat.name, // 使用 name 作为 value，因为岗位的 department 字段存储的也是类别名称
      }));
      console.log('[fetchCategories] categoryOptions:', categoryOptions.value);
      console.log('[fetchCategories] categoryList:', categoryList.value);
    }
  } catch (error) {
    console.error('获取岗位类别失败:', error);
  } finally {
    loadingCategories.value = false;
  }
};

// 根据选中的部门筛选岗位名称选项
const positionNameOptions = computed(() => {
  const selectedDepartment = positionForm.value.department;
  console.log('[positionNameOptions] 计算属性被调用!');
  console.log('[positionNameOptions] selectedDepartment:', selectedDepartment);

  // 如果没有选择部门，返回空
  if (!selectedDepartment) {
    console.log('[positionNameOptions] 未选择部门，返回空数组');
    return [];
  }

  // 根据选中的部门名称找到对应的 categoryId
  const selectedCategory = categoryList.value.find(
    (cat) => cat.name === selectedDepartment,
  );
  const selectedCategoryId = selectedCategory ? selectedCategory.id : null;
  console.log(
    '[positionNameOptions] selectedCategory:',
    JSON.stringify(selectedCategory),
  );

  // 优先从按类别缓存的岗位列表中获取（通过 API 获取的该类别下的岗位）
  if (
    selectedCategoryId &&
    categoryPositionsMap.value.has(selectedCategoryId)
  ) {
    const positions = categoryPositionsMap.value.get(selectedCategoryId) || [];
    console.log('[positionNameOptions] 从类别缓存获取岗位:', positions);
    return positions;
  }

  // 降级方案：从当前企业已有的岗位中过滤
  const uniquePositions = new Map();

  if (positionStore.positions && Array.isArray(positionStore.positions)) {
    positionStore.positions.forEach((pos) => {
      if (pos.positionName) {
        // 通过 categoryId 匹配
        const categoryIdMatch =
          selectedCategoryId !== null && pos.categoryId === selectedCategoryId;
        console.log(
          '[positionNameOptions] 检查岗位:',
          pos.positionName,
          'categoryId:',
          pos.categoryId,
          'categoryIdMatch:',
          categoryIdMatch,
        );

        if (categoryIdMatch) {
          uniquePositions.set(pos.positionName, {
            label: pos.positionName,
            value: pos.positionName,
          });
        }
      }
    });
  }
  const result = Array.from(uniquePositions.values());
  console.log('[positionNameOptions] 结果:', result);
  return result;
});

// 当部门（类别）变化时，获取该类别下的岗位列表
const handleDepartmentChange = async (selectedDepartment) => {
  console.log('[handleDepartmentChange] 部门变化:', selectedDepartment);
  if (!selectedDepartment) {
    return;
  }

  // 找到选中部门对应的类别 ID
  const selectedCategory = categoryList.value.find(
    (cat) => cat.name === selectedDepartment,
  );
  if (!selectedCategory) {
    console.log('[handleDepartmentChange] 未找到对应类别');
    return;
  }

  const categoryId = selectedCategory.id;
  console.log('[handleDepartmentChange] 类别ID:', categoryId);

  // 如果已经缓存过该类别的岗位，不再重复获取
  if (categoryPositionsMap.value.has(categoryId)) {
    console.log('[handleDepartmentChange] 类别岗位已缓存');
    return;
  }

  loadingCategoryPositions.value = true;
  try {
    const result =
      await PositionCategoryService.getPositionsByCategoryIdPublic(categoryId);
    console.log('[handleDepartmentChange] 获取类别岗位返回:', result);

    if (result && result.code === 200) {
      const positions = Array.isArray(result.data) ? result.data : [];
      // 提取岗位名称并去重
      const uniquePositionNames = [
        ...new Set(positions.map((p) => p.positionName).filter(Boolean)),
      ];
      const positionOptions = uniquePositionNames.map((name) => ({
        label: name,
        value: name,
      }));

      categoryPositionsMap.value.set(categoryId, positionOptions);
      console.log('[handleDepartmentChange] 类别岗位缓存:', positionOptions);
    }
  } catch (error) {
    console.error('获取类别岗位列表失败:', error);
  } finally {
    loadingCategoryPositions.value = false;
  }
};

// 部门选项（兼容原有逻辑，支持输入过滤）
const departmentOptions = computed(() => categoryOptions.value);

const positionTypeOptions = [
  { label: '全职', value: '全职' },
  { label: '兼职', value: '兼职' },
  { label: '实习', value: '实习' },
];

const salaryUnitOptions = [
  { label: 'K/月', value: 'K/月' },
  { label: '元/天', value: '元/天' },
  { label: '元/小时', value: '元/小时' },
  { label: '面议', value: '面议' },
];

const statusMap = {
  active: '招聘中',
  paused: '已暂停',
};

const statusTypeMap = {
  active: 'success',
  paused: 'warning',
};

const getRowClassName = ({ row }) => {
  if (row.status === 'paused') {
    return 'paused-row';
  }
  return '';
};

const getRowStyle = ({ row }) => {
  if (row.status === 'paused') {
    return {
      backgroundColor: '#ffebee',
      color: '#909399',
    };
  }
  return {};
};

onMounted(async () => {
  console.log('页面加载 - companyId:', companyId.value);
  if (!companyId.value) {
    ElMessage.error('未获取到企业 ID，无法加载数据');
    return;
  }
  console.log('开始获取岗位数据和类别');
  positionStore.fetchPositions(companyId.value).then(() => {
    console.log('岗位数据获取完成:', positionStore.positions);
  });
  positionStore.fetchInternshipStatuses(companyId.value);
  fetchCategories().then(() => {
    console.log('类别数据获取完成:', categoryOptions.value);
  });
  // 获取专业列表
  await fetchMajors();
});

// 获取专业列表
const fetchMajors = async () => {
  try {
    const response = await fetch('/api/majors', {
      headers: {
        Authorization: 'Bearer ' + (authStore.token || ''),
      },
    });
    const result = await response.json();
    if (result.code === 200) {
      majorOptions.value = result.data || [];
    }
  } catch (error) {
    console.error('获取专业列表失败:', error);
  }
};

const handleSearch = async () => {
  try {
    hasSearched.value = true;
    const params: Record<string, unknown> = {};

    if (searchForm.value.keyword) {
      params.positionName = searchForm.value.keyword;
    }

    if (searchForm.value.department) {
      params.department = searchForm.value.department;
    }

    if (searchForm.value.status) {
      params.status = searchForm.value.status;
    }

    const response = await positionApi.getPositionsByCompanyId(
      companyId.value,
      params,
    );
    if (response.code === 200) {
      searchResults.value = response.data.map((item) => ({
        ...item,
        id: item.id,
        contactId: item.contactId ?? null,
        contactPerson: item.contactPerson || '',
        contactPhone: item.contactPhone || '',
        contactEmail: item.contactEmail || '',
        positionName: item.positionName,
        department: item.department || '',
        workLocation: formatWorkLocation(item),
        salaryMin: item.salaryMin || null,
        salaryMax: item.salaryMax || null,
        salaryUnit: normalizeSalaryUnit(item.salaryUnit),
        plannedRecruit: item.plannedRecruit || 0,
        recruitedCount: item.recruitedCount || 0,
        remainingQuota: item.remainingQuota || 0,
        viewCount: 0, // backend Position has no viewCount; search rows default to 0
        status: item.status || 'active',
        publishDate: formatDate(item.publishDate || item.createTime),
        province:
          item.province && item.province !== 'None' ? item.province : '',
        city: item.city && item.city !== 'None' ? item.city : '',
        district:
          item.district && item.district !== 'None' ? item.district : '',
        detailAddress: item.detailAddress || '',
        description: item.description || '',
        requirements: item.requirements || '',
        positionType: item.positionType || '',
      }));
      currentPage.value = 1;
      ElMessage.success('搜索成功');
    } else {
      ElMessage.error('搜索失败');
    }
  } catch (error) {
    console.error('搜索失败:', error);
    ElMessage.error('搜索失败');
  }
};

const handleReset = () => {
  searchForm.value = {
    keyword: '',
    status: '',
    department: '',
  };
  searchResults.value = [];
  hasSearched.value = false;
  currentPage.value = 1;
  ElMessage.info('已重置搜索条件');
};

const handlePublish = () => {
  void fetchContacts();
  dialogTitle.value = '发布岗位';
  dialogType.value = 'create';

  // 清除类别岗位缓存，确保每次打开发布弹窗时获取最新数据
  categoryPositionsMap.value.clear();

  // 从 localStorage 读取面试信息模板
  let interviewTime = '';
  let interviewLocation = '';
  let interviewMethod = ['线下'];
  let remark = '';
  const savedTemplate = localStorage.getItem('interviewTemplate');
  hasInterviewTemplate.value = false;
  if (savedTemplate) {
    try {
      const template = JSON.parse(savedTemplate);
      interviewTime = template.interviewTime || '';
      interviewLocation = template.interviewLocation || '';
      interviewMethod = normalizeInterviewMethods(template.interviewMethod ?? '线下');
      remark = template.remark || '';
      hasInterviewTemplate.value = true;
    } catch (e) {
      console.error('读取面试信息模板失败:', e);
    }
  }

  positionForm.value = {
    id: null,
    contactId: 0,
    positionName: '',
    department: '',
    positionType: '',
    province: '',
    city: '',
    district: '',
    detailAddress: '',
    salaryMin: '',
    salaryMax: '',
    salaryUnit: 'K/月',
    description: '',
    requirements: '',
    plannedRecruit: 1,
    recruitedCount: 0,
    remainingQuota: 0,
    status: 'active',
    addressCode: [],
    interviewTime,
    interviewLocation,
    interviewMethod,
    interviewRemark: remark,
    internshipEnvironment: '',
    providesAccommodation: 0,
    requiredMajors: [],
    categoryId: null,
  };
  postingText.value = '';
  aiFilledFields.value = [];
  aiFilledValues.value = {};
  aiParseNotices.value = [];
  dialogVisible.value = true;
  registerPositionForm('发布岗位');
};

// 面试信息模板相关方法
const handleInterviewInfo = () => {
  // 从 localStorage 读取已保存的面试信息模板
  const savedTemplate = localStorage.getItem('interviewTemplate');
  if (savedTemplate) {
    try {
      const template = JSON.parse(savedTemplate);
      interviewForm.value = {
        interviewTime: template.interviewTime || '',
        interviewLocation: template.interviewLocation || '',
        interviewMethod: normalizeInterviewMethods(template.interviewMethod ?? '线下'),
        remark: template.remark || '',
      };
    } catch {
      interviewForm.value = {
        interviewTime: '',
        interviewLocation: '',
        interviewMethod: ['线下'],
        remark: '',
      };
    }
  } else {
    interviewForm.value = {
      interviewTime: '',
      interviewLocation: '',
      interviewMethod: ['线下'],
      remark: '',
    };
  }
  interviewDialogVisible.value = true;
  registerInterviewForm();
};

const handleInterviewSubmit = async () => {
  const savedValues = formContext.captureValues('company:interview-template');
  // 保存到 localStorage
  localStorage.setItem(
    'interviewTemplate',
    JSON.stringify(interviewForm.value),
  );
  ElMessage.success('面试信息已保存，发布岗位时将自动填充');
  formContext.reportFormOutcome('company:interview-template', { status: 'saved', label: '面试模板已保存到当前浏览器，尚未发布岗位', url: '/company/jobs' }, formContext.currentForm.value?.instanceId, savedValues);
  interviewDialogVisible.value = false;
};

const handleInterviewCancel = () => {
  interviewDialogVisible.value = false;
};

const handleAddressChange = (e) => {
  positionForm.value.addressCode = e;
  if (e && e.length > 0) {
    const provinceCode = e[0];
    const cityCode = e[1];
    const districtCode = e[2];

    positionForm.value.province = chinaData[provinceCode]?.label || '';
    positionForm.value.city = chinaData[cityCode]?.label || '';
    positionForm.value.district = chinaData[districtCode]?.label || '';
  } else {
    positionForm.value.province = '';
    positionForm.value.city = '';
    positionForm.value.district = '';
  }
};

// 部门下拉框过滤方法（支持输入匹配）
// P2：Element Plus 在指针按下关闭下拉时会先清空输入框，blur 时已读不到文字，
// 因此在 filter-method 里逐字暂存；关闭下拉与提交前用 commitAllowCreateText 落真实 model。
const departmentInputDraft = ref('');
const positionNameInputDraft = ref('');
const departmentSelectRef = ref(null);
const positionNameSelectRef = ref(null);
const handleDepartmentFilter = (query) => {
  departmentInputDraft.value = typeof query === 'string' ? query : '';
};
const handlePositionNameFilter = (query) => {
  positionNameInputDraft.value = typeof query === 'string' ? query : '';
};
const commitDepartmentAllowCreate = () => {
  const next = commitAllowCreateText(
    departmentInputDraft.value ||
      readSelectInputText(departmentSelectRef.value),
    positionForm.value.department,
  );
  departmentInputDraft.value = '';
  if (next && next !== positionForm.value.department) {
    positionForm.value.department = next;
    handleDepartmentChange(next);
  }
};
const commitPositionNameAllowCreate = () => {
  const next = commitAllowCreateText(
    positionNameInputDraft.value ||
      readSelectInputText(positionNameSelectRef.value),
    positionForm.value.positionName,
  );
  positionNameInputDraft.value = '';
  if (next && next !== positionForm.value.positionName) {
    positionForm.value.positionName = next;
  }
};
// 选项兜底：AI 直接写 model 或自定义名称未进选项时，下拉框仍需回显
const positionNameSelectOptions = computed(() =>
  withModelOption(positionNameOptions.value, positionForm.value.positionName),
);
const departmentSelectOptions = computed(() =>
  withModelOption(categoryOptions.value, positionForm.value.department),
);

const handleEdit = (row) => {
  dialogTitle.value = '编辑岗位';
  dialogType.value = 'edit';

  const addressCodes = [];
  if (row.province && row.province !== 'None') {
    const provinceCode = Object.keys(chinaData).find(
      (code) => chinaData[code].label === row.province,
    );
    if (provinceCode) {
      addressCodes.push(provinceCode);
      if (row.city && row.city !== 'None') {
        const cityCode = Object.keys(chinaData).find(
          (code) =>
            chinaData[code].label === row.city &&
            chinaData[code].parent === provinceCode,
        );
        if (cityCode) {
          addressCodes.push(cityCode);
          if (row.district && row.district !== 'None') {
            const districtCode = Object.keys(chinaData).find(
              (code) =>
                chinaData[code].label === row.district &&
                chinaData[code].parent === cityCode,
            );
            if (districtCode) {
              addressCodes.push(districtCode);
            }
          }
        }
      }
    }
  }

  positionForm.value = {
    ...row,
    contactId: row.contactId || 0,
    salaryMin: row.salaryMin || '',
    salaryMax: row.salaryMax || '',
    salaryUnit: normalizeSalaryUnit(row.salaryUnit),
    province: row.province && row.province !== 'None' ? row.province : '',
    city: row.city && row.city !== 'None' ? row.city : '',
    district: row.district && row.district !== 'None' ? row.district : '',
    detailAddress: row.detailAddress || '',
    plannedRecruit: row.plannedRecruit || 1,
    addressCode: addressCodes,
    interviewTime: row.interviewTime || '',
    interviewLocation: row.interviewLocation || '',
    interviewMethod: normalizeInterviewMethods(row.interviewMethod),
    interviewRemark: row.interviewRemark || '',
    internshipEnvironment: row.internshipEnvironment || '',
    providesAccommodation: row.providesAccommodation || 0,
    requiredMajors: row.requiredMajors
      ? row.requiredMajors.split(',').filter((m) => m)
      : [],
  };
  postingText.value = '';
  aiFilledFields.value = [];
  aiFilledValues.value = {};
  aiParseNotices.value = [];
  dialogVisible.value = true;
  registerPositionForm('编辑岗位');
  void fetchContacts();
};

const handlePause = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要暂停"${row.positionName}"的招聘吗？`,
      '暂停招聘',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
        center: true,
      },
    );
    const success = await positionStore.pausePosition(companyId.value, row.id);
    if (success) {
      ElMessage.success('已暂停招聘');
    } else {
      ElMessage.error('操作失败');
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败');
    }
  }
};

const handleResume = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要恢复"${row.positionName}"的招聘吗？`,
      '恢复招聘',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
        center: true,
      },
    );
    const success = await positionStore.resumePosition(companyId.value, row.id);
    if (success) {
      ElMessage.success('已恢复招聘');
    } else {
      ElMessage.error('操作失败');
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败');
    }
  }
};

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除"${row.positionName}"吗？此操作不可恢复！`,
      '删除岗位',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'error',
        center: true,
      },
    );
    const success = await positionStore.deletePosition(companyId.value, row.id);
    if (success) {
      ElMessage.success('已删除岗位');
    } else {
      ElMessage.error('操作失败');
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败');
    }
  }
};

const handleSave = async () => {
  if (loading.value) return;
  const fillInstance = formContext.currentForm.value?.instanceId;
  const editingPositionId = positionForm.value.id;
  const creating = dialogType.value === 'create';
  commitDepartmentAllowCreate();
  commitPositionNameAllowCreate();
  if (!positionForm.value.positionName) {
    ElMessage.warning('请输入岗位名称');
    return;
  }

  if (
    !positionForm.value.requiredMajors ||
    positionForm.value.requiredMajors.length === 0
  ) {
    ElMessage.warning('请选择招聘专业');
    return;
  }

  if (positionForm.value.salaryUnit === '面议') {
    positionForm.value.salaryMin = '';
    positionForm.value.salaryMax = '';
  } else {
    const salaryMin =
      positionForm.value.salaryMin === ''
        ? null
        : Number(positionForm.value.salaryMin);
    const salaryMax =
      positionForm.value.salaryMax === ''
        ? null
        : Number(positionForm.value.salaryMax);
    if (
      (salaryMin !== null && (!Number.isFinite(salaryMin) || salaryMin < 0)) ||
      (salaryMax !== null && (!Number.isFinite(salaryMax) || salaryMax < 0))
    ) {
      ElMessage.warning('薪资不能为负数');
      return;
    }
    if (salaryMin !== null && salaryMax !== null && salaryMin > salaryMax) {
      ElMessage.warning('最低薪资不能高于最高薪资');
      return;
    }
  }

  // Capture the values actually submitted, after the original salary normalization.
  const savedValues = formContext.captureValues('company:position');
  loading.value = true;
  try {
    const positionData = {
      positionName: positionForm.value.positionName,
      contactId: positionForm.value.contactId || null,
      department: positionForm.value.department,
      positionType: positionForm.value.positionType,
      province: positionForm.value.province,
      city: positionForm.value.city,
      district: positionForm.value.district,
      detailAddress: positionForm.value.detailAddress,
      salaryMin: positionForm.value.salaryMin,
      salaryMax: positionForm.value.salaryMax,
      salaryUnit: positionForm.value.salaryUnit,
      description: positionForm.value.description,
      requirements: positionForm.value.requirements,
      plannedRecruit: positionForm.value.plannedRecruit,
      status: positionForm.value.status,
      interviewTime: positionForm.value.interviewTime || null,
      interviewLocation: positionForm.value.interviewLocation || null,
      interviewMethod: serializeInterviewMethods(positionForm.value.interviewMethod),
      interviewRemark: positionForm.value.interviewRemark || null,
      internshipEnvironment: positionForm.value.internshipEnvironment || null,
      providesAccommodation: positionForm.value.providesAccommodation || 0,
      requiredMajors:
        positionForm.value.requiredMajors &&
        positionForm.value.requiredMajors.length > 0
          ? positionForm.value.requiredMajors.join(',')
          : null,
      categoryId: positionForm.value.department
        ? categoryList.value.find(
            (c) => c.name === positionForm.value.department,
          )?.id || null
        : null,
    };

    let success;
    if (dialogType.value === 'create') {
      success = await positionStore.addPosition(companyId.value, positionData);
      if (success) {
        ElMessage.success('岗位发布成功');
      } else {
        ElMessage.error('岗位发布失败');
      }
    } else {
      success = await positionStore.updatePosition(
        companyId.value,
        positionForm.value.id,
        positionData,
      );
      if (success) {
        ElMessage.success('岗位更新成功');
      } else {
        ElMessage.error('岗位更新失败');
      }
    }

    if (success) {
      formContext.reportFormOutcome('company:position', {
        status: creating ? 'published' : 'saved',
        label: creating ? '岗位已发布，可在岗位列表核对' : '岗位修改已保存',
        url: '/company/jobs',
        object: editingPositionId ? { type: 'company_job', id: String(editingPositionId) } : null,
      }, fillInstance, savedValues);
      dialogVisible.value = false;
      if (hasSearched.value) await handleSearch();
    }
  } catch (error) {
    ElMessage.error('操作失败');
  } finally {
    loading.value = false;
  }
};

const handleCancel = () => {
  dialogVisible.value = false;
};

const markAiFilled = (
  field: string,
  value: any = positionForm.value[field],
) => {
  const next = new Set(aiFilledFields.value);
  next.add(field);
  aiFilledFields.value = Array.from(next);
  aiFilledValues.value[field] = Array.isArray(value) ? [...value] : value;
};

const isAiFilled = (field: string) => aiFilledFields.value.includes(field);

const isPreviousAiValueUnchanged = (field: string, current: any) => {
  if (!isAiFilled(field)) return false;
  return (
    JSON.stringify(aiFilledValues.value[field]) === JSON.stringify(current)
  );
};

const handleSalaryUnitChange = (unit: string) => {
  if (unit === '面议') {
    positionForm.value.salaryMin = '';
    positionForm.value.salaryMax = '';
  }
};

const isConfident = (result: any, field: string) => {
  const score = Number(result.confidence?.[field]);
  return Number.isFinite(score) && score >= JOB_POSTING_CONFIDENCE_THRESHOLD;
};

const getCombinedConfidence = (result: any, fields: string[]) => {
  const scores = fields.map((field) => Number(result.confidence?.[field]));
  return scores.length > 0 && scores.every((score) => Number.isFinite(score))
    ? Math.min(...scores)
    : undefined;
};

const normalizeAddressLabel = (value: string) =>
  value.replace(/(省|市|自治区|特别行政区|地区|自治州|盟)$/, '');

const matchesAddressLabel = (item: any, label: string) => {
  if (!item.label || !label) return false;
  return (
    item.label === label ||
    normalizeAddressLabel(item.label) === normalizeAddressLabel(label)
  );
};

const resolveAddressCodes = (
  province: string,
  city: string,
  district: string,
) => {
  const findByLabel = (label: string, parentCode?: string) =>
    Object.keys(chinaData).find(
      (code) =>
        matchesAddressLabel(chinaData[code], label) &&
        (!parentCode || chinaData[code].parent === parentCode),
    );

  if (province) {
    const provinceCode = findByLabel(province);
    if (!provinceCode) return [];

    const codes = [provinceCode];
    if (!city) return codes;

    const cityCode = findByLabel(city, provinceCode);
    if (!cityCode) return codes;

    codes.push(cityCode);
    if (!district) return codes;

    const districtCode = findByLabel(district, cityCode);
    if (districtCode) codes.push(districtCode);
    return codes;
  }

  if (city) {
    const cityCode = findByLabel(city);
    if (!cityCode) return [];

    const inferredProvinceCode = chinaData[cityCode]?.parent;
    if (!inferredProvinceCode) return [];

    const codes = [inferredProvinceCode, cityCode];
    if (!district) return codes;

    const districtCode = findByLabel(district, cityCode);
    if (districtCode) codes.push(districtCode);
    return codes;
  }

  if (district) {
    const districtCode = findByLabel(district);
    if (!districtCode) return [];

    const cityCode = chinaData[districtCode]?.parent;
    const inferredProvinceCode = cityCode
      ? chinaData[cityCode]?.parent
      : undefined;
    if (!cityCode || !inferredProvinceCode) return [];

    return [inferredProvinceCode, cityCode, districtCode];
  }

  return [];
};

// 侧栏智能填表会直接写入 province/city/district；解析成级联组件需要的 addressCode。
watch(
  () => [
    positionForm.value.province,
    positionForm.value.city,
    positionForm.value.district,
  ],
  ([province, city, district]) => {
    const codes = resolveAddressCodes(
      province || '',
      city || '',
      district || '',
    );
    if (
      codes.length &&
      JSON.stringify(codes) !== JSON.stringify(positionForm.value.addressCode)
    ) {
      positionForm.value.addressCode = codes;
    }
  },
  { flush: 'post' },
);
const handleParsePosting = async () => {
  if (parsingPosting.value || !dialogVisible.value || !authStore.token) return;
  const text = postingText.value.trim();
  if (!text) {
    ElMessage.warning('请先粘贴岗位内容');
    return;
  }

  parsingPosting.value = true;
  aiParseNotices.value = [];
  const controller = new AbortController();
  postingParseController = controller;
  const form = positionForm.value, revision = postingFormRevision, owner = postingOwner();
  const isCurrent = () => !controller.signal.aborted && postingParseController === controller
    && dialogVisible.value && positionForm.value === form && postingFormRevision === revision && postingOwner() === owner;
  try {
    const response = await jobPostingAiService.parseJobPosting(text, controller.signal);
    if (!isCurrent()) return;
    if (response.code !== 200 || !response.data) {
      ElMessage.error(response.message || 'AI识别失败');
      return;
    }

    // Stop tracking this completed request before synchronously applying its accepted fields.
    postingParseController = null;
    parsingPosting.value = false;
    const result = response.data;
    let filledCount = 0;
    const conflictFields = new Set<string>();
    const lowConfidenceFields = new Set<string>();

    const fillField = (
      field: string,
      value: any,
      confidenceKey = field,
      replaceDefault = false,
    ) => {
      const current = positionForm.value[field];
      const decision = decideJobPostingAutoFill({
        current,
        next: value,
        confidence: result.confidence?.[confidenceKey],
        replaceable:
          replaceDefault || isPreviousAiValueUnchanged(field, current),
      });

      if (decision === 'fill') {
        positionForm.value[field] = value;
        markAiFilled(field, value);
        filledCount += 1;
      } else if (decision === 'conflict') {
        conflictFields.add(fieldLabel(field));
      } else if (decision === 'low-confidence') {
        lowConfidenceFields.add(fieldLabel(field));
      }
      return decision;
    };

    const directFields = [
      'positionName',
      'department',
      'positionType',
      'detailAddress',
      'description',
      'requirements',
      'internshipEnvironment',
      'interviewTime',
      'interviewLocation',
      'interviewMethod',
      'interviewRemark',
    ];
    directFields.forEach((field) => {
      const replaceDefault =
        field === 'interviewMethod' &&
        positionForm.value.interviewMethod.length === 1 &&
        positionForm.value.interviewMethod[0] === '线下' &&
        !hasInterviewTemplate.value &&
        !isAiFilled('interviewMethod');
      const value = field === 'interviewMethod'
        ? normalizeInterviewMethods(result[field])
        : result[field];
      const decision = fillField(field, value, field, replaceDefault);
      if (field === 'department' && decision === 'fill') {
        void handleDepartmentChange(result[field]);
      }
    });

    if (result.plannedRecruit !== null && result.plannedRecruit !== undefined) {
      fillField(
        'plannedRecruit',
        result.plannedRecruit,
        'plannedRecruit',
        positionForm.value.plannedRecruit === 1 &&
          !isAiFilled('plannedRecruit'),
      );
    }

    if (
      result.providesAccommodation === 0 ||
      result.providesAccommodation === 1
    ) {
      fillField(
        'providesAccommodation',
        result.providesAccommodation,
        'providesAccommodation',
        positionForm.value.providesAccommodation === 0 &&
          !isAiFilled('providesAccommodation'),
      );
    }

    if (result.requiredMajors?.length) {
      if (isConfident(result, 'requiredMajors')) {
        const matchesMajor = (major: string, item: any) =>
          item.name === major ||
          item.name.includes(major) ||
          major.includes(item.name);
        const validMajors = result.requiredMajors
          .map((major: string) =>
            major === '不限制专业' ||
            major === '不限专业' ||
            major === '专业不限'
              ? '不限制专业'
              : majorOptions.value.find((item: any) =>
                  matchesMajor(major, item),
                )?.name,
          )
          .filter((major: string | undefined): major is string =>
            Boolean(major),
          )
          .slice(0, 10);
        if (validMajors.length > 0) {
          fillField('requiredMajors', validMajors);
        } else {
          aiParseNotices.value.push(
            `需人工确认：专业“${result.requiredMajors.join('、')}”未匹配系统专业库`,
          );
        }
      } else {
        lowConfidenceFields.add('专业要求');
      }
    }

    if (result.salaryUnit) {
      const salaryInputsEmpty =
        positionForm.value.salaryMin === '' &&
        positionForm.value.salaryMax === '';
      const hasManualSalaryValue = [
        ['salaryMin', positionForm.value.salaryMin],
        ['salaryMax', positionForm.value.salaryMax],
      ].some(
        ([field, value]) =>
          value !== '' && !isPreviousAiValueUnchanged(field as string, value),
      );
      if (
        hasManualSalaryValue &&
        positionForm.value.salaryUnit !== result.salaryUnit
      ) {
        conflictFields.add('薪资单位');
      } else {
        fillField(
          'salaryUnit',
          result.salaryUnit,
          'salaryUnit',
          positionForm.value.salaryUnit === 'K/月' &&
            salaryInputsEmpty &&
            !isAiFilled('salaryUnit'),
        );
      }
    }

    const salaryUnitRecognized =
      Boolean(result.salaryUnit) &&
      isConfident(result, 'salaryUnit') &&
      positionForm.value.salaryUnit === result.salaryUnit;
    if (salaryUnitRecognized && positionForm.value.salaryUnit !== '面议') {
      fillField('salaryMin', result.salaryMin);
      fillField('salaryMax', result.salaryMax);
    } else if (
      salaryUnitRecognized &&
      positionForm.value.salaryUnit === '面议'
    ) {
      positionForm.value.salaryMin = '';
      positionForm.value.salaryMax = '';
    }

    const addressFields = ['province', 'city', 'district'].filter((field) =>
      Boolean(result[field]),
    );
    const addressCodes = resolveAddressCodes(
      result.province || '',
      result.city || '',
      result.district || '',
    );
    if (addressCodes.length > 0 && addressFields.length > 0) {
      const decision = decideJobPostingAutoFill({
        current: positionForm.value.addressCode,
        next: addressCodes,
        confidence: getCombinedConfidence(result, addressFields),
        replaceable: isPreviousAiValueUnchanged(
          'address',
          positionForm.value.addressCode,
        ),
      });
      if (decision === 'fill') {
        handleAddressChange(addressCodes);
        markAiFilled('address', addressCodes);
        filledCount += 1;
      } else if (decision === 'conflict') {
        conflictFields.add('工作地点');
      } else if (decision === 'low-confidence') {
        lowConfidenceFields.add('工作地点');
      }
    } else if (addressFields.length > 0) {
      lowConfidenceFields.add('工作地点');
      aiParseNotices.value.push(
        '需人工确认：识别到的地区未匹配行政区划，请手动选择工作地点',
      );
    }

    const backendWarnings = Array.isArray(result.warnings)
      ? result.warnings.slice(0, 3)
      : [];
    aiParseNotices.value.push(...backendWarnings);
    if (conflictFields.size > 0) {
      aiParseNotices.value.push(
        `未覆盖已填内容：${[...conflictFields].join('、')}`,
      );
    }
    if (lowConfidenceFields.size > 0) {
      aiParseNotices.value.push(
        `AI不确定，暂未回填：${[...lowConfidenceFields].join('、')}`,
      );
    }
    aiParseNotices.value = [...new Set(aiParseNotices.value)];

    if (filledCount > 0) {
      ElMessage.success(`AI已回填 ${filledCount} 项，请核对后发布`);
    } else {
      ElMessage.info('AI没有回填新字段，请手动填写');
    }
  } catch (error: any) {
    if (isCurrent()) ElMessage.error(error?.response?.data?.message || 'AI识别失败，请稍后重试');
  } finally {
    if (postingParseController === controller) { postingParseController = null; parsingPosting.value = false; }
  }
};

const handlePastePosting = async () => {
  if (!navigator.clipboard?.readText) {
    ElMessage.warning('当前浏览器不支持读取剪贴板，请手动粘贴');
    return;
  }

  pastingPosting.value = true;
  try {
    const text = (await navigator.clipboard.readText()).trim();
    if (!text) {
      ElMessage.warning('剪贴板中没有可粘贴的内容');
      return;
    }

    postingText.value = text.slice(0, 6000);
    aiParseNotices.value = [];
    if (text.length > 6000) {
      ElMessage.warning('剪贴板内容超过6000字，已保留前6000字');
    }
  } catch {
    ElMessage.warning('未能读取剪贴板，请手动粘贴岗位信息');
  } finally {
    pastingPosting.value = false;
  }
};

// AI生成岗位描述
const handleGenerateDescription = async () => {
  if (!positionForm.value.positionName) {
    ElMessage.warning('请先输入岗位名称');
    return;
  }

  generatingDescription.value = true;
  try {
    const response = await fetch('/api/ai/generate-job-description', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer ' + (authStore.token || ''),
      },
      body: JSON.stringify({
        positionName: positionForm.value.positionName,
        department: positionForm.value.department,
        positionType: positionForm.value.positionType,
      }),
    });

    const res = await response.json();
    if (res.success && res.data) {
      if (res.data.description) {
        positionForm.value.description = res.data.description;
      }
      ElMessage.success('AI已生成岗位描述');
    } else {
      ElMessage.error(res.message || '生成失败');
    }
  } catch (error) {
    console.error('生成岗位描述失败:', error);
    ElMessage.error('生成失败，请稍后重试');
  } finally {
    generatingDescription.value = false;
  }
};

useAgentPageContext(() => ({
  title: '岗位管理',
  view: dialogVisible.value ? 'position-form' : interviewDialogVisible.value ? 'interview-template' : 'page',
  object: dialogVisible.value && positionForm.value.id ? { type: 'company_job', id: String(positionForm.value.id) } : null,
  filters: {
    keyword: String((searchForm.value as any)?.keyword ?? ''),
    status: String((searchForm.value as any)?.status ?? ''),
    industry: searchForm.value.department || '',
    visibleIds: paginatedTableData.value.slice(0, 20).map(row => String(row.id)),
  },
}));
</script>
<template>
  <div class="recruitment-management">
    <div class="page-header">
      <div class="page-header-text">
        <h2>招聘管理</h2>
        <p>岗位的发布与维护，确认实习学生后岗位招聘人数自动减一</p>
      </div>
      <div class="header-stats">
        <div class="stat-item">
          <div class="stat-label">计划招聘人数</div>
          <div class="stat-value primary">
            {{ positionStore.totalPlanCount }}
          </div>
        </div>
        <div class="stat-item">
          <div class="stat-label">已招人数</div>
          <div class="stat-value success">
            {{ positionStore.totalConfirmedCount }}
          </div>
        </div>
        <div class="stat-item">
          <div class="stat-label">岗位缺口</div>
          <div class="stat-value warning">{{ positionStore.totalGap }}</div>
        </div>
        <div class="stat-item">
          <div class="stat-label">在招岗位数</div>
          <div class="stat-value info">
            {{ positionStore.activePositionCount }}
          </div>
        </div>
      </div>
    </div>
    <div class="search-card">
      <div class="search-section">
        <div class="section-title">招聘岗位查询</div>
        <el-form :inline="true" :model="searchForm" class="search-form">
          <el-form-item label="关键词">
            <el-input
              v-model="searchForm.keyword"
              placeholder="请输入岗位名称"
              clearable
              style="width: 200px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item v-show="!isMobile || showMobileFilters" label="部门">
            <el-select
              v-model="searchForm.department"
              placeholder="请选择部门"
              clearable
              style="width: 150px"
            >
              <el-option
                v-for="item in departmentOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-show="!isMobile || showMobileFilters" label="状态">
            <el-select
              v-model="searchForm.status"
              placeholder="请选择状态"
              clearable
              style="width: 150px"
            >
              <el-option label="招聘中" value="active" />
              <el-option label="已暂停" value="paused" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button v-if="isMobile" :aria-expanded="showMobileFilters" @click="showMobileFilters = !showMobileFilters">{{ showMobileFilters ? '收起筛选' : '筛选' }}{{ activeFilterCount ? `（${activeFilterCount}）` : '' }}</el-button>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
      <div class="search-divider"></div>
      <div class="search-section">
        <div class="section-title">岗位发布</div>
        <div class="publish-actions">
          <el-button type="warning" data-agent-open="面试信息表单" @click="handleInterviewInfo()">
            <el-icon><Message /></el-icon> 预设面试信息
          </el-button>
          <el-button type="success" data-agent-open="发布岗位表单" @click="handlePublish">
            <el-icon><Plus /></el-icon> 发布岗位
          </el-button>
        </div>
      </div>
    </div>
    <div class="table-card">
      <el-table
        v-if="!isMobile"
        :data="paginatedTableData"
        border
        stripe
        :row-class-name="getRowClassName"
        :row-style="getRowStyle"
        :resizable="false"
        style="width: 100%"
      >
        <el-table-column prop="positionName" label="岗位名称" min-width="120">
          <template #default="{ row }">
            <span
              v-if="row.status === 'paused'"
              class="paused-text"
              style="
                background-color: #ffebee;
                color: #909399;
                text-decoration: line-through;
                display: inline-block;
                padding: 8px 12px;
                border-radius: 4px;
              "
            >
              <el-icon
                class="paused-icon"
                style="color: #f56c6c; margin-right: 6px; font-size: 16px"
                ><VideoPause
              /></el-icon>
              {{ row.positionName }}
            </span>
            <span v-else>{{ row.positionName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="岗位类别" min-width="100" />
        <el-table-column label="工作地点" min-width="180">
          <template #default="{ row }">
            <div
              style="
                white-space: normal;
                word-break: break-all;
                line-height: 1.5;
              "
            >
              <span v-if="row.province && row.city && row.district">
                {{ row.province }} {{ row.city }} {{ row.district }}
                <span v-if="row.detailAddress">{{ row.detailAddress }}</span>
              </span>
              <span v-else-if="row.province && row.city">
                {{ row.province }} {{ row.city }}
                <span v-if="row.detailAddress">{{ row.detailAddress }}</span>
              </span>
              <span v-else-if="row.detailAddress">{{ row.detailAddress }}</span>
              <span v-else>-</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="薪资范围" width="100" align="center">
          <template #default="{ row }">
            {{ formatSalaryDisplay(row) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="plannedRecruit"
          label="招聘人数"
          width="90"
          align="center"
        />
        <el-table-column
          prop="recruitedCount"
          label="已招人数"
          width="90"
          align="center"
        />
        <el-table-column
          prop="remainingQuota"
          label="缺口"
          width="80"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.remainingQuota === 0 ? 'success' : 'warning'"
              size="small"
            >
              {{ row.remainingQuota }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status]" size="small">
              {{ statusMap[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="publishDate"
          label="发布日期"
          width="110"
          align="center"
        />
        <el-table-column
          prop="viewCount"
          label="浏览次数"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.viewCount || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button
                v-if="row.status === 'active'"
                type="warning"
                size="small"
                @click="handlePause(row)"
              >
                暂停
              </el-button>
              <el-button
                v-if="row.status === 'paused'"
                type="success"
                size="small"
                @click="handleResume(row)"
              >
                恢复
              </el-button>
              <el-button type="primary" size="small" @click="handleEdit(row)">
                编辑
              </el-button>
              <el-button type="danger" size="small" @click="handleDelete(row)">
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <!-- 手机端卡片列表 -->
      <div v-else class="mobile-card-list">
        <el-empty v-if="!hasSearched && !paginatedTableData.length" description="暂无招聘岗位，点击发布岗位开始招聘" />
        <div
          v-for="row in paginatedTableData"
          :key="row.id"
          class="mobile-card"
        >
          <div class="mobile-card-header">
            <span class="mobile-card-title">
              <el-icon
                v-if="row.status === 'paused'"
                style="color: #f56c6c; margin-right: 4px"
                ><VideoPause
              /></el-icon>
              {{ row.positionName || '-' }}
            </span>
            <el-tag :type="statusTypeMap[row.status]" size="small">
              {{ statusMap[row.status] }}
            </el-tag>
          </div>
          <div class="mobile-card-body">
            <div class="mobile-card-row">
              <span class="mobile-card-label">岗位类别</span>
              <span class="mobile-card-value">{{ row.department || '-' }}</span>
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">工作地点</span>
              <span class="mobile-card-value">
                <template v-if="row.province && row.city"
                  >{{ row.province }} {{ row.city
                  }}<template v-if="row.district">
                    {{ row.district }}</template
                  ></template
                >
                <template v-else>{{ row.detailAddress || '-' }}</template>
              </span>
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">薪资范围</span>
              <span class="mobile-card-value">{{
                formatSalaryDisplay(row)
              }}</span>
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">招聘进度</span>
              <span class="mobile-card-value"
                >已招 {{ row.recruitedCount || 0 }}/{{
                  row.plannedRecruit || 0
                }}，缺口 {{ row.remainingQuota }}</span
              >
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">发布日期</span>
              <span class="mobile-card-value">{{
                row.publishDate || '-'
              }}</span>
            </div>
            <div class="mobile-card-row">
              <span class="mobile-card-label">浏览次数</span>
              <span class="mobile-card-value">{{ row.viewCount || 0 }}</span>
            </div>
          </div>
          <div class="mobile-card-actions">
            <el-button
              v-if="row.status === 'active'"
              type="warning"
              @click="handlePause(row)"
            >
              暂停
            </el-button>
            <el-button
              v-if="row.status === 'paused'"
              type="success"
              @click="handleResume(row)"
            >
              恢复
            </el-button>
            <el-button type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" @click="handleDelete(row)">删除</el-button>
          </div>
        </div>
      </div>
      <div
        v-if="hasSearched && filteredTableData.length === 0"
        class="empty-state"
      >
        <el-empty description="未找到匹配的岗位信息" />
      </div>
      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          background
          :layout="isMobile ? 'total, sizes, prev, next' : 'total, sizes, prev, pager, next, jumper'"
          :total="filteredTableData.length"
          :page-sizes="[10, 20, 50, 100]"
        />
      </div>
    </div>
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="700px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="positionFormRef"
        :model="positionForm"
        label-width="120px"
        label-position="left"
      >
        <div v-if="dialogType === 'create'" class="ai-parse-panel">
          <div class="ai-parse-header">
            <el-icon><MagicStick /></el-icon> <span>粘贴岗位信息</span>
          </div>
          <el-input
            v-model="postingText"
            type="textarea"
            :rows="4"
            :maxlength="6000"
            show-word-limit
            resize="none"
            placeholder="粘贴招聘信息原文"
          />
          <div class="ai-parse-actions">
            <el-button
              size="small"
              :loading="pastingPosting"
              :disabled="parsingPosting"
              @click="handlePastePosting"
            >
              <el-icon v-if="!pastingPosting"><DocumentCopy /></el-icon> 粘贴
            </el-button>
            <el-button
              type="primary"
              size="small"
              :loading="parsingPosting"
              :disabled="!postingText.trim()"
              @click="handleParsePosting"
            >
              <el-icon v-if="!parsingPosting"><MagicStick /></el-icon>
              AI识别填充
            </el-button>
            <span>识别结果需核对后发布</span>
          </div>
          <el-alert
            v-if="aiParseNotices.length > 0"
            class="ai-parse-notice"
            type="warning"
            :closable="false"
            show-icon
          >
            <template #title>AI识别结果需要核对</template>
            <div class="ai-parse-notice-list">
              <p v-for="(notice, index) in aiParseNotices" :key="index">
                {{ notice }}
              </p>
            </div>
          </el-alert>
        </div>
        <el-form-item label="岗位联系人">
          <div style="width: 100%">
            <el-select v-model="positionForm.contactId" :loading="contactsLoading" placeholder="请选择岗位联系人" style="width: 100%">
              <el-option v-for="contact in contactOptions" :key="contact.value" :label="contact.label" :value="contact.value" />
            </el-select>
            <div class="position-contact-tip">
              <span v-if="selectedContact">电话：{{ selectedContact.phone }}<template v-if="selectedContact.email">　邮箱：{{ selectedContact.email }}</template></span>
              <span v-else>未单独选择时，使用企业基础信息中的原联系人。</span>
              <el-button type="primary" link @click="router.push({ name: 'CompanyInfo', query: { tab: 'contacts' } })">管理联系人</el-button>
            </div>
            <el-alert v-if="contactsError" :title="contactsError" type="error" :closable="false"><el-button link type="primary" @click="fetchContacts">重新加载联系人</el-button></el-alert>
          </div>
        </el-form-item>
        <el-form-item
          label="岗位类别"
          required
          :class="{ 'ai-filled': isAiFilled('department') }"
        >
          <el-select
            ref="departmentSelectRef"
            v-model="positionForm.department"
            placeholder="请选择岗位类别"
            filterable
            allow-create
            default-first-option
            :filter-method="handleDepartmentFilter"
            style="width: 100%"
            @change="handleDepartmentChange"
            @visible-change="
              (open) => {
                if (!open) commitDepartmentAllowCreate();
              }
            "
          >
            <el-option
              v-for="item in departmentSelectOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="岗位类型"
          :class="{ 'ai-filled': isAiFilled('positionType') }"
        >
          <el-select
            v-model="positionForm.positionType"
            placeholder="请选择岗位类型（选填）"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="item in positionTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="岗位名称"
          required
          :class="{ 'ai-filled': isAiFilled('positionName') }"
        >
          <el-select
            ref="positionNameSelectRef"
            v-model="positionForm.positionName"
            placeholder="请输入或选择岗位名称"
            filterable
            allow-create
            default-first-option
            :filter-method="handlePositionNameFilter"
            @visible-change="
              (open) => {
                if (!open) commitPositionNameAllowCreate();
              }
            "
            style="width: 100%"
            :loading="loadingCategoryPositions"
          >
            <el-option
              v-for="item in positionNameSelectOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="工作地点"
          :class="{ 'ai-filled': isAiFilled('address') }"
        >
          <elui-china-area-dht
            v-model="positionForm.addressCode"
            @change="handleAddressChange"
            placeholder="请选择省/市/区"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item
          label="详细地址"
          :class="{ 'ai-filled': isAiFilled('detailAddress') }"
        >
          <el-input
            v-model="positionForm.detailAddress"
            placeholder="请输入详细地址，如街道、门牌号等"
          />
        </el-form-item>
        <el-form-item
          label="薪资范围"
          :class="{
            'ai-filled':
              isAiFilled('salaryUnit') ||
              isAiFilled('salaryMin') ||
              isAiFilled('salaryMax'),
          }"
        >
          <div class="salary-input-row">
            <el-select
              v-model="positionForm.salaryUnit"
              class="salary-unit-select"
              placeholder="单位"
              @change="handleSalaryUnitChange"
            >
              <el-option
                v-for="item in salaryUnitOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-input
              v-model="positionForm.salaryMin"
              class="salary-number-input"
              placeholder="最低"
              type="number"
              min="0"
              step="0.1"
              :disabled="positionForm.salaryUnit === '面议'"
            />
            <span class="salary-separator">-</span>
            <el-input
              v-model="positionForm.salaryMax"
              class="salary-number-input"
              placeholder="最高"
              type="number"
              min="0"
              step="0.1"
              :disabled="positionForm.salaryUnit === '面议'"
            />
          </div>
        </el-form-item>
        <el-form-item
          label="计划招聘人数"
          :class="{ 'ai-filled': isAiFilled('plannedRecruit') }"
        >
          <el-input-number
            v-model="positionForm.plannedRecruit"
            :min="1"
            :max="100"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item
          label="招聘专业"
          required
          :class="{ 'ai-filled': isAiFilled('requiredMajors') }"
        >
          <el-select
            v-model="positionForm.requiredMajors"
            multiple
            placeholder='可选择多个专业，或选择"不限制专业"'
            style="width: 100%"
          >
            <el-option label="不限制专业" value="不限制专业" />
            <el-option
              v-for="major in majorOptions"
              :key="major.id"
              :label="major.name"
              :value="major.name"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="岗位描述"
          :class="{ 'ai-filled': isAiFilled('description') }"
        >
          <el-input
            v-model="positionForm.description"
            type="textarea"
            :rows="4"
            placeholder="请输入岗位描述"
            :disabled="generatingDescription"
          />
          <!-- AI智能生成功能暂时隐藏          <div class="ai-generate-tip">            <el-button              type="primary"              size="small"              :loading="generatingDescription"              @click="handleGenerateDescription"              :disabled="!positionForm.positionName"            >              <el-icon v-if="!generatingDescription"><MagicStick /></el-icon>              {{ generatingDescription ? '生成中...' : 'AI智能生成' }}            </el-button>            <span class="tip-text">填写岗位名称后点击生成</span>          </div>          -->
        </el-form-item>
        <el-form-item
          label="任职要求"
          :class="{ 'ai-filled': isAiFilled('requirements') }"
        >
          <el-input
            v-model="positionForm.requirements"
            type="textarea"
            :rows="4"
            placeholder="请输入任职要求"
          />
        </el-form-item>
        <el-form-item
          label="实习环境"
          :class="{ 'ai-filled': isAiFilled('internshipEnvironment') }"
        >
          <el-select
            v-model="positionForm.internshipEnvironment"
            placeholder="请选择实习环境（选填）"
            style="width: 100%"
          >
            <el-option label="办公室" value="办公室" />
            <el-option label="车间" value="车间" />
            <el-option label="医院" value="医院" />
            <el-option label="外派" value="外派" />
          </el-select>
        </el-form-item>
        <el-form-item
          label="是否提供住宿"
          :class="{ 'ai-filled': isAiFilled('providesAccommodation') }"
        >
          <el-radio-group v-model="positionForm.providesAccommodation">
            <el-radio :label="0">否</el-radio>
            <el-radio :label="1">是</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-divider content-position="left">面试信息</el-divider>
        <el-form-item
          label="面试时间"
          :class="{ 'ai-filled': isAiFilled('interviewTime') }"
        >
          <el-date-picker
            v-model="positionForm.interviewTime"
            type="datetime"
            placeholder="请选择面试时间（选填）"
            style="width: 100%"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item
          label="面试地点"
          :class="{ 'ai-filled': isAiFilled('interviewLocation') }"
        >
          <el-input
            v-model="positionForm.interviewLocation"
            placeholder="请输入面试地点（选填）"
          />
        </el-form-item>
        <el-form-item
          label="面试方式"
          :class="{ 'ai-filled': isAiFilled('interviewMethod') }"
        >
          <el-select
            v-model="positionForm.interviewMethod"
            multiple
            clearable
            placeholder="请选择面试方式（可多选，选填）"
            style="width: 100%"
          >
            <el-option label="线下" value="线下" />
            <el-option label="线上视频" value="线上视频" />
            <el-option label="电话面试" value="电话面试" />
          </el-select>
        </el-form-item>
        <el-form-item
          label="备注"
          :class="{ 'ai-filled': isAiFilled('interviewRemark') }"
        >
          <el-input
            v-model="positionForm.interviewRemark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注信息（选填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleCancel">取消</el-button>
        <el-button
          type="primary"
          @click="handleSave"
          :loading="loading"
          :disabled="parsingPosting"
        >
          {{ dialogType === 'create' ? '发布' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
    <!-- 面试信息对话框 -->
    <el-dialog
      v-model="interviewDialogVisible"
      :title="interviewDialogTitle"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="interviewFormRef"
        :model="interviewForm"
        label-width="100px"
        label-position="left"
      >
        <el-form-item label="面试时间">
          <el-date-picker
            v-model="interviewForm.interviewTime"
            type="datetime"
            placeholder="请选择面试时间"
            style="width: 100%"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="面试地点">
          <el-input
            v-model="interviewForm.interviewLocation"
            placeholder="请输入面试地点"
          />
        </el-form-item>
        <el-form-item label="面试方式">
          <el-select
            v-model="interviewForm.interviewMethod"
            multiple
            clearable
            placeholder="请选择面试方式（可多选）"
            style="width: 100%"
          >
            <el-option label="线下" value="线下" />
            <el-option label="线上视频" value="线上视频" />
            <el-option label="电话面试" value="电话面试" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="interviewForm.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注信息"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleInterviewCancel">取消</el-button>
        <el-button
          type="primary"
          @click="handleInterviewSubmit"
          :loading="interviewLoading"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
<style scoped>
.position-contact-tip { color: #909399; font-size: 12px; line-height: 1.8; margin-top: 6px; display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; }
.recruitment-management {
  padding: 0;
}
.page-header {
  background: linear-gradient(135deg, #409eff 0%, #52c41a 100%);
  color: white;
  padding: 24px 40px;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(64, 158, 255, 0.3);
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page-header-text {
  flex-shrink: 0;
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
.header-stats {
  display: flex;
  gap: 16px;
}
.header-stats .stat-item {
  flex: 1;
  background: white;
  padding: 14px 16px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.header-stats .stat-label {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
  white-space: nowrap;
}
.header-stats .stat-value {
  font-size: 18px;
  font-weight: 700;
}
.header-stats .stat-value.primary {
  color: #409eff;
}
.header-stats .stat-value.success {
  color: #67c23a;
}
.header-stats .stat-value.warning {
  color: #e6a23c;
}
.header-stats .stat-value.info {
  color: #909399;
}
.stats-card {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}
.stat-item {
  flex: 1;
  background: white;
  padding: 14px 16px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.stat-label {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
  white-space: nowrap;
}
.stat-value {
  font-size: 18px;
  font-weight: 700;
}
.stat-value.primary {
  color: #409eff;
}
.stat-value.success {
  color: #67c23a;
}
.stat-value.warning {
  color: #e6a23c;
}
.stat-value.info {
  color: #909399;
}
.search-card {
  background: white;
  padding: 20px 24px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  margin-bottom: 20px;
  display: flex;
  align-items: stretch;
  gap: 24px;
}
.search-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.search-section:first-child {
  flex: 1;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  padding-left: 12px;
  border-left: 3px solid #409eff;
}
.search-section:last-child .section-title {
  border-left-color: #67c23a;
}
.search-divider {
  width: 1px;
  background: #e4e7ed;
}
.publish-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  flex: 1;
}
.search-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}
.search-card :deep(.el-form-item) {
  margin-bottom: 0;
  display: flex;
  align-items: center;
}
.search-card :deep(.el-form-item__label) {
  display: flex;
  align-items: center;
  height: 32px;
  line-height: 32px;
}
.search-card :deep(.el-form-item__content) {
  display: flex;
  align-items: center;
}
.table-card {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
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
.empty-state {
  padding: 40px 0;
  text-align: center;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
.address-selector {
  display: flex;
  gap: 10px;
  width: 100%;
}
.address-selector :deep(.el-select) {
  flex: 1;
}
:deep(.el-table .paused-row) {
  background-color: #ffebee !important;
}
:deep(.el-table .paused-row td) {
  background-color: #ffebee !important;
  color: #909399 !important;
}
:deep(.el-table .paused-row:hover > td) {
  background-color: #ffcdd2 !important;
}
:deep(.el-table .paused-row .el-tag--warning) {
  background-color: #f4e4c1 !important;
  border-color: #e6a23c !important;
  color: #e6a23c !important;
}
:deep(.el-table .paused-row .paused-icon) {
  color: #f56c6c;
  margin-right: 6px;
  font-size: 16px;
}
:deep(.el-table .paused-row .paused-text) {
  text-decoration: line-through;
}
.action-buttons {
  display: flex;
  gap: 4px;
  justify-content: center;
}
.ai-generate-tip {
  display: flex;
  align-items: center;
  margin-top: 8px;
  gap: 10px;
}
.ai-generate-tip .tip-text {
  font-size: 12px;
  color: #909399;
}
.ai-parse-panel {
  padding: 14px;
  margin-bottom: 20px;
  background: #f8fbff;
  border: 1px solid #d9ecff;
  border-radius: 6px;
}
.ai-parse-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  color: #409eff;
  font-size: 14px;
  font-weight: 600;
}
.ai-parse-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}
.ai-parse-actions span {
  font-size: 12px;
  color: #909399;
}
.ai-parse-notice {
  margin-top: 10px;
}
.ai-parse-notice :deep(.el-alert__title) {
  line-height: 1.5;
}
.ai-parse-notice-list {
  margin-top: 4px;
}
.ai-parse-notice-list p {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
}
.ai-parse-notice-list p + p {
  margin-top: 4px;
}
.salary-input-row {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr) 24px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  width: 100%;
}
.salary-separator {
  color: #606266;
  text-align: center;
}
.ai-filled :deep(.el-input__wrapper),
.ai-filled :deep(.el-select__wrapper),
.ai-filled :deep(.el-textarea__inner) {
  background-color: #f8fbff;
  box-shadow: 0 0 0 1px #a0cfff inset;
}
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
    gap: 14px;
    padding: 18px 20px;
  }
  .page-header h2 {
    font-size: 22px;
  }
  .header-stats {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
  }
  .search-card {
    flex-direction: column;
    gap: 16px;
    padding: 16px 18px;
  }
  .search-section:first-child {
    flex: none;
  }
  .search-divider {
    width: 100%;
    height: 1px;
  }
  .publish-actions {
    flex-direction: column;
    align-items: stretch;
  }
  .publish-actions :deep(.el-button) {
    width: 100%;
    margin-left: 0;
    height: 44px;
    font-size: 15px;
  }
  .search-form :deep(.el-form-item) {
    width: 100%;
    margin-right: 0;
  }
  .search-form :deep(.el-input),
  .search-form :deep(.el-select) {
    width: 100% !important;
  }
  .search-form :deep(.el-form-item__content) {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    min-width: 0;
  }
  .search-form :deep(.el-button) {
    margin-left: 0;
    flex: 1;
    min-height: 44px;
  }
}
</style>
