<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { listCompanyContacts, saveCompanyContact, deleteCompanyContact, type CompanyContact } from '@/api/companyContacts'
import { useFormContext } from '@/agent/formContext'

const contacts = ref<CompanyContact[]>([])
const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const contactForm = ref({ name: '', phone: '', email: '' })
const formRef = ref<FormInstance>()
const formContext = useFormContext()
let loadVersion = 0
const rules = {
  name: [{ required: true, message: '请输入联系人姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入联系人电话', trigger: 'blur' }, { pattern: /^[+0-9()（）\-\s转]{7,30}$/, message: '请输入有效的手机或固定电话号码', trigger: 'blur' }],
  email: [{ type: 'email' as const, message: '请输入有效邮箱', trigger: 'blur' }]
}
const loadContacts = async () => {
  const version = ++loadVersion
  loading.value = true
  loadError.value = ''
  try {
    const response = await listCompanyContacts()
    if (version !== loadVersion) return
    if (response.code !== 200) throw new Error(response.message || response.msg || '加载联系人失败')
    contacts.value = response.data || []
  } catch (error) {
    if (version !== loadVersion) return
    contacts.value = []
    loadError.value = error instanceof Error ? error.message : '加载联系人失败，请重试'
  } finally { if (version === loadVersion) loading.value = false }
}
const openContact = (contact?: CompanyContact) => {
  editingId.value = contact?.id ?? null
  contactForm.value = { name: contact?.name || '', phone: contact?.phone || '', email: contact?.email || '' }
  dialogVisible.value = true
  formContext.register({
    formId: 'company:contact', title: contact ? '编辑联系人' : '新增联系人',
    fields: [{ prop: 'name', label: '姓名', type: 'input', required: true }, { prop: 'phone', label: '电话', type: 'input', required: true }, { prop: 'email', label: '邮箱', type: 'input' }],
    getModel: () => contactForm.value, getFormEl: () => formRef.value
  })
}
const closeContact = () => formContext.unregister('company:contact')
const saveContact = async () => {
  if (!await formRef.value?.validate().catch(() => false)) return
  const instance = formContext.currentForm.value?.instanceId
  const values = formContext.captureValues('company:contact')
  saving.value = true
  try {
    const response = await saveCompanyContact(editingId.value, {
      name: contactForm.value.name.trim(), phone: contactForm.value.phone.trim(), email: contactForm.value.email.trim()
    })
    if (response.code !== 200) throw new Error(response.message || response.msg || '保存联系人失败')
    formContext.reportFormOutcome('company:contact', { status: 'saved', label: '企业联系人已保存', url: '/company/interviews?tab=contacts' }, instance, values)
    ElMessage.success('联系人已保存')
    dialogVisible.value = false
    closeContact()
    await loadContacts()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '保存联系人失败') }
  finally { saving.value = false }
}
const removeContact = async (contact: CompanyContact) => {
  if ((contact.positionCount || 0) > 0) {
    ElMessage.warning('该联系人正在被岗位使用，请先更换岗位联系人再删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除联系人“${contact.name}”吗？`, '删除联系人', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    const response = await deleteCompanyContact(contact.id)
    if (response.code !== 200) throw new Error(response.message || response.msg || '删除联系人失败')
    ElMessage.success('联系人已删除')
    await loadContacts()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '删除联系人失败')
  }
}
onMounted(loadContacts)
onUnmounted(() => { loadVersion++; closeContact() })
</script>

<template>
  <div class="contacts-panel">
    <div class="contacts-toolbar">
      <div><h3>企业联系人</h3><p>发布岗位时选择一位联系人，学生将看到该联系人的联系方式。</p></div>
      <div class="toolbar-actions"><el-button :icon="Refresh" :loading="loading" @click="loadContacts">刷新</el-button><el-button type="primary" :icon="Plus" @click="openContact()">新增联系人</el-button></div>
    </div>
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon />
    <el-table v-loading="loading" :data="contacts" row-key="id" :empty-text="loadError ? '联系人加载失败，请点击刷新重试' : '暂无联系人，点击新增联系人'">
      <el-table-column prop="name" label="姓名" min-width="130" />
      <el-table-column prop="phone" label="电话" min-width="160" />
      <el-table-column label="邮箱" min-width="220"><template #default="{ row }">{{ row.email || '未填写' }}</template></el-table-column>
      <el-table-column label="使用岗位" width="110"><template #default="{ row }">{{ row.positionCount || 0 }} 个</template></el-table-column>
      <el-table-column label="操作" width="200" fixed="right"><template #default="{ row }"><div class="contact-actions"><el-button type="primary" size="small" @click="openContact(row)">编辑</el-button><el-button type="danger" size="small" plain :disabled="(row.positionCount || 0) > 0" :title="(row.positionCount || 0) > 0 ? '请先更换岗位联系人再删除' : '删除联系人'" @click="removeContact(row)">删除</el-button></div></template></el-table-column>
    </el-table>
    <p class="contacts-tip">旧岗位未选择联系人时，继续使用基础信息中的企业原联系人。已被岗位使用的联系人，请先更换岗位联系人再删除。</p>
    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新增联系人' : '编辑联系人'" width="min(500px, calc(100vw - 32px))" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" destroy-on-close @closed="closeContact">
      <el-form ref="formRef" :model="contactForm" :rules="rules" label-width="70px" :disabled="saving">
        <el-form-item label="姓名" prop="name"><el-input v-model="contactForm.name" placeholder="请输入联系人姓名" maxlength="50" /></el-form-item>
        <el-form-item label="电话" prop="phone"><el-input v-model="contactForm.phone" placeholder="手机或固定电话" maxlength="30" /></el-form-item>
        <el-form-item label="邮箱" prop="email"><el-input v-model="contactForm.email" placeholder="选填" maxlength="100" /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveContact">保存联系人</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.contacts-panel { padding: 24px; }
.contacts-toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; margin-bottom: 20px; }
.contacts-toolbar h3 { margin: 0 0 8px; font-size: 18px; color: #303133; }
.contacts-toolbar p, .contacts-tip { color: #909399; font-size: 13px; line-height: 1.7; margin: 0; }
.contacts-tip { margin-top: 18px; }
.toolbar-actions { display: flex; gap: 12px; }
.toolbar-actions .el-button + .el-button { margin-left: 0; }
.contact-actions { display: flex; gap: 8px; }
.contact-actions .el-button + .el-button { margin-left: 0; }
:deep(.el-table th.el-table__cell) { background: #f5f7fa; color: #606266; font-weight: 600; }
:deep(.el-alert) { margin-bottom: 16px; }
@media (max-width: 768px) { .contacts-panel { padding: 16px; } }
</style>
