<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowRight, Connection } from '@element-plus/icons-vue'
import { getOnlineUserSummary, ONLINE_ROLES, formatOnlineTime } from '@/api/onlineUsers'
import { useOnlinePolling } from '@/composables/useOnlinePolling'

const router = useRouter()
const { data, loading, error, refresh } = useOnlinePolling(async signal => {
  const result = await getOnlineUserSummary(signal)
  if (result.code !== 200 || !result.data) throw new Error('在线状态暂时无法获取')
  return result.data
})
</script>

<template>
  <section class="online-overview" aria-label="在线用户概览" :aria-busy="loading">
    <div class="online-total">
      <el-icon class="connection-icon"><Connection /></el-icon>
      <div><span class="online-label">当前在线</span><strong>{{ data?.totalOnline?.toLocaleString() ?? '—' }}<small>人</small></strong></div>
    </div>
    <div class="role-counts">
      <span v-for="role in ONLINE_ROLES" :key="role.value">{{ role.label }}<b>{{ data?.byRole[role.value]?.toLocaleString() ?? '—' }}</b></span>
    </div>
    <div class="overview-actions">
      <el-button link type="primary" @click="router.push('/admin/logs?tab=online-users')">查看在线用户<el-icon><ArrowRight /></el-icon></el-button>
      <span v-if="error" class="online-error" role="status">{{ error }}<el-button link type="primary" :loading="loading" @click="refresh">重试</el-button></span>
      <span v-else class="updated">{{ data ? `更新于 ${formatOnlineTime(data.serverTime)}` : '正在获取在线状态…' }}</span>
    </div>
    <p class="online-note">最近 3 分钟仍有响应的有效登录用户 · 同一账号只计一人<span v-if="error && data"> · 当前显示上次成功数据：{{ formatOnlineTime(data.serverTime) }}</span></p>
  </section>
</template>

<style scoped>
.online-overview { display: flex; align-items: center; flex-wrap: wrap; gap: 16px 28px; padding: 20px 24px 14px; margin-bottom: 24px; border: 1px solid #dcebe8; border-radius: 12px; background: linear-gradient(110deg, #f2faf7, #fff 65%); }
.online-total { display: flex; gap: 12px; align-items: center; }
.connection-icon { padding: 12px; box-sizing: content-box; color: #16816b; background: #e1f2eb; border-radius: 12px; font-size: 22px; }
.online-label { display: block; color: #526d64; font-size: 13px; }
.online-total strong { font-size: 28px; color: #174d40; line-height: 1.35; }
.online-total small { font-size: 12px; font-weight: 400; margin-left: 6px; }
.role-counts { display: flex; flex: 1; gap: 24px; flex-wrap: wrap; }
.role-counts span { display: flex; flex-direction: column; gap: 6px; color: #606266; font-size: 13px; }
.role-counts b { font-size: 19px; color: #303133; font-variant-numeric: tabular-nums; }
.overview-actions { display: flex; align-items: flex-end; flex-direction: column; gap: 6px; }
.updated, .online-note { color: #687b73; font-size: 12px; }
.online-note { width: 100%; margin: 0; line-height: 1.6; }
.online-error { color: #a06022; font-size: 12px; }
@media (max-width: 767px) {
  .online-overview { padding: 16px; gap: 16px; }
  .role-counts { flex-basis: calc(100% - 135px); justify-content: space-between; gap: 12px; }
  .overview-actions { width: 100%; flex-direction: row; align-items: center; justify-content: space-between; flex-wrap: wrap; }
}
</style>
