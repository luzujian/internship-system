<template>
  <el-dialog
    v-model="visible"
    title="问题反馈"
    width="520px"
    align-center
    :close-on-click-modal="false"
    class="feedback-submit-dialog"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" class="feedback-form">
      <el-form-item label="问题标题" prop="title">
        <el-input
          v-model="form.title"
          placeholder="请输入问题标题（2-255字）"
          maxlength="255"
          show-word-limit
          clearable
        />
      </el-form-item>
      <el-form-item label="问题内容" prop="content">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="6"
          placeholder="请详细描述遇到的问题"
          maxlength="2000"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from "vue";
import { ElMessage } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import request from "@/utils/request";
import { useAuthStore } from "@/store/auth";
import { useFormContext } from "@/agent/formContext";

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    userType: "student" | "teacher" | "company";
  }>(),
  {
    modelValue: false,
    userType: "student",
  }
);
const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "submitted"): void;
}>();

const authStore = useAuthStore();
const formRef = ref<FormInstance | null>(null);
const submitting = ref(false);
const form = ref({ title: "", content: "" });

const formContext = useFormContext();
const feedbackFormId = "feedback:submit";

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      formRef.value?.clearValidate();
      formContext.register({
        formId: 'feedback:submit',
        title: "问题反馈",
        fields: [
          { prop: 'title', label: '问题标题', type: 'input', required: true, min: 2, max: 255 },
          { prop: 'content', label: '问题内容', type: 'textarea', required: true, max: 2000 },
        ],
        getModel: () => form.value,
        getFormEl: () => formRef.value,
      });
    } else {
      formContext.unregister(feedbackFormId);
    }
  }
);

onUnmounted(() => formContext.unregister(feedbackFormId));

const rules: FormRules = {
  title: [
    { required: true, message: "请输入问题标题", trigger: "blur" },
    { min: 2, max: 255, message: "标题长度需在2-255字之间", trigger: "blur" },
  ],
  content: [{ required: true, message: "请输入问题内容", trigger: "blur" }],
};

const visible = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit("update:modelValue", val),
});

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      formRef.value?.clearValidate();
    }
  }
);

const handleCancel = () => {
  emit("update:modelValue", false);
};

const resolveUserInfo = () => {
  const user: Record<string, any> =
    (authStore.user as unknown as Record<string, any>) || {};
  const rawId =
    user.id ?? user.userId ?? user.studentId ?? user.teacherId ?? user.companyId;
  const userId = Number(rawId);
  const userName = String(user.name || user.username || "").trim();
  const userAccount = String(user.username || user.name || "").trim();
  return { userId, userName, userAccount };
};

const handleSubmit = async () => {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  const { userId, userName, userAccount } = resolveUserInfo();
  if (!Number.isFinite(userId) || userId <= 0 || !userName || !userAccount) {
    ElMessage.error("登录信息缺失，请重新登录后再提交");
    return;
  }
  submitting.value = true;
  const operation = formContext.beginFormOperation(feedbackFormId);
  try {
    const response: any = await request.post("/problem-feedback", {
      userType: props.userType,
      userId,
      userName,
      userAccount,
      title: form.value.title.trim(),
      content: form.value.content.trim(),
    });
    if (response && response.code === 200) {
      operation.complete({ status: "submitted", label: form.value.title.trim() });
      ElMessage.success("问题反馈提交成功");
      form.value.title = "";
      form.value.content = "";
      emit("update:modelValue", false);
      emit("submitted");
    } else {
      ElMessage.error((response && response.msg) || "提交失败，请稍后重试");
    }
  } catch (error) {
    ElMessage.error("提交失败，请稍后重试");
  } finally {
    submitting.value = false;
  }
};
</script>

<style scoped>
.feedback-form {
  padding-top: 8px;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>