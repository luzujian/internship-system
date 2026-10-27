<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getOnlineUsers, ONLINE_ROLES, formatOnlineTime, type OnlineUserQuery } from '@/api/onlineUsers'
import { useOnlinePolling } from '@/composables/useOnlinePolling'
import { getRoleName } from '@/utils/securityEventPresentation'

const filters = reactive({ keyword: '', role: '', status: '' })
const query = ref<OnlineUserQuery>({ page: 1, pageSize: 20 })
const { data, loading, error, refresh } = useOnlinePolling(async signal => {
  const result = await getOnlineUsers(query.value, signal)
  if (result.code !== 200 || !result.data || !Array.isArray(result.data.rows)) throw new Error('在线状态暂时无法获取')
  return result.data
})
const summaryCards = computed(() => [
  { label: '在线总人数', value: data.value?.summary.totalOnline, tone: 'online' },
  { label: '活跃人数', value: data.value?.summary.active, tone: 'active' },
  { label: '空闲人数', value: data.value?.summary.idle, tone: 'idle' }
])
const search = () => { query.value = { ...filters, keyword: filters.keyword.trim(), page: 1, pageSize: 20 } }
const reset = () => { Object.assign(filters, { keyword: '', role: '', status: '' }); search() }
const pageChanged = (page: number) => { query.value = { ...query.value, page } }
watch(query, () => { void refresh() })
watch(data, value => {
  if (!value || !query.value.page || query.value.page === 1) return
  const lastPage = Math.max(1, Math.ceil(value.total / 20))
  if (query.value.page > lastPage) pageChanged(lastPage)
})
</script>

<template>
  <section class="online-users-panel" aria-label="在线用户名单">
    <div class="online-intro">
      <div><h2>在线用户</h2><p>全站在线概览 · 不随下方筛选变化</p></div>
      <div class="update-controls"><span>{{ data ? `更新于 ${formatOnlineTime(data.serverTime)}` : '每 15 秒自动刷新' }}</span><el-button :icon="Refresh" :loading="loading" @click="refresh">刷新</el-button></div>
    </div>
    <div class="online-stats" :aria-busy="loading">
      <div v-for="card in summaryCards" :key="card.label" class="online-stat" :class="card.tone"><span>{{ card.label }}</span><strong>{{ card.value?.toLocaleString() ?? '—' }}<small>人</small></strong></div>
    </div>
    <p class="presence-rules">在线：有效登录且最近 3 分钟有响应；活跃：最近 2 分钟有真实操作，其余为空闲。同一账号只计一人。关闭页面或断网后最多延迟 3 分钟移出名单。</p>
    <el-alert v-if="error" type="warning" :closable="false" show-icon class="online-alert" role="status">
      <template #title>{{ error }}{{ data ? '，当前显示上次成功数据' : '，请稍后重试' }}</template>
    </el-alert>
    <el-card shadow="never" class="online-filter-card">
      <el-form label-position="top" class="online-filters" @submit.prevent="search">
        <el-form-item label="姓名 / 登录账号"><el-input v-model="filters.keyword" placeholder="搜索姓名或账号" maxlength="100" clearable /></el-form-item>
        <el-form-item label="用户角色"><el-select v-model="filters.role" placeholder="全部角色" clearable><el-option v-for="role in ONLINE_ROLES" :key="role.value" :label="role.label" :value="role.value" /></el-select></el-form-item>
        <el-form-item label="活跃状态"><el-select v-model="filters.status" placeholder="全部状态" clearable><el-option label="活跃" value="ACTIVE" /><el-option label="空闲" value="IDLE" /></el-select></el-form-item>
        <div class="filter-actions"><el-button type="primary" :icon="Search" native-type="submit">查询</el-button><el-button @click="reset">重置</el-button></div>
      </el-form>
    </el-card>
    <el-card shadow="never" class="online-list-card" :aria-busy="loading">
      <div class="list-heading"><h3>当前在线名单</h3><span>{{ data ? `筛选结果 ${data.total.toLocaleString()} 人` : loading ? '正在获取在线状态…' : '暂时无法获取' }}</span></div>
      <template v-if="data">
        <el-table :data="data.rows" class="online-desktop-table" row-key="key" stripe empty-text="当前条件下暂无在线用户">
          <el-table-column label="用户" min-width="180"><template #default="{ row }"><div class="user-name">{{ row.displayName }}<el-tag v-if="row.simulation" type="warning" size="small" effect="plain">模拟访问中</el-tag></div><div class="username">{{ row.username }}</div></template></el-table-column>
          <el-table-column label="身份" width="120"><template #default="{ row }">{{ getRoleName(row.role) }}</template></el-table-column>
          <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="light">{{ row.status === 'ACTIVE' ? '活跃' : '空闲' }}</el-tag></template></el-table-column>
          <el-table-column label="最近操作" min-width="165"><template #default="{ row }">{{ formatOnlineTime(row.lastActivityAt) }}</template></el-table-column>
          <el-table-column label="最近响应" min-width="165"><template #default="{ row }">{{ formatOnlineTime(row.lastSeenAt) }}</template></el-table-column>
          <el-table-column prop="sessionCount" label="登录会话数" width="105" align="center" />
        </el-table>
        <div class="online-mobile-cards">
          <article v-for="user in data.rows" :key="user.key" class="online-user-card">
            <div class="mobile-user-heading"><div><strong>{{ user.displayName }}</strong><span class="username">{{ user.username }}</span></div><el-tag :type="user.status === 'ACTIVE' ? 'success' : 'info'">{{ user.status === 'ACTIVE' ? '活跃' : '空闲' }}</el-tag></div>
            <div class="mobile-user-role">{{ getRoleName(user.role) }}<el-tag v-if="user.simulation" type="warning" size="small" effect="plain">模拟访问中</el-tag></div>
            <dl><dt>最近操作</dt><dd>{{ formatOnlineTime(user.lastActivityAt) }}</dd><dt>最近响应</dt><dd>{{ formatOnlineTime(user.lastSeenAt) }}</dd><dt>登录会话数</dt><dd>{{ user.sessionCount }}</dd></dl>
          </article>
          <el-empty v-if="!data.rows.length" description="当前条件下暂无在线用户" :image-size="70" />
        </div>
        <div class="online-pagination"><el-pagination :current-page="query.page || 1" :page-size="20" :total="data.total" :pager-count="5" layout="prev, pager, next" :disabled="loading || !!error" @current-change="pageChanged" /></div>
      </template>
      <el-skeleton v-else-if="loading" :rows="5" animated />
      <el-empty v-else description="在线状态暂时无法获取" :image-size="80"><el-button type="primary" plain @click="refresh">重试加载</el-button></el-empty>
    </el-card>
  </section>
</template>

<style scoped>
.online-intro, .update-controls, .list-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.online-intro { margin: 8px 0 18px; flex-wrap: wrap; }
.online-intro h2 { font-size: 20px; margin: 0 0 6px; color: #24352f; }
.online-intro p, .update-controls span, .list-heading span { color: #737e78; font-size: 12px; margin: 0; }
.online-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.online-stat { padding: 20px 24px; background: #fff; border: 1px solid #e3e9e6; border-radius: 12px; display: flex; flex-direction: column; gap: 12px; }
.online-stat > span { font-size: 13px; color: #57675f; }
.online-stat strong { font-size: 30px; color: #25483a; line-height: 1.3; font-variant-numeric: tabular-nums; }
.online-stat small { font-size: 12px; font-weight: 400; margin-left: 6px; color: #737e78; }
.online-stat.active { background: #f3faf5; border-color: #daecde; }
.online-stat.idle { background: #f7f8fa; }
.presence-rules { font-size: 12px; color: #69766e; line-height: 1.8; margin: 14px 0 18px; }
.online-alert { margin-bottom: 16px; }
.online-filter-card, .online-list-card { border-radius: 12px; margin-bottom: 20px; }
.online-filters { display: grid; grid-template-columns: minmax(180px, 1.3fr) 1fr 1fr auto; gap: 16px; align-items: end; }
.online-filters .el-form-item { margin-bottom: 0; min-width: 0; }
.online-filters .el-select { width: 100%; }
.filter-actions { display: flex; padding-bottom: 1px; }
.list-heading { margin-bottom: 18px; }
.list-heading h3 { font-size: 16px; margin: 0; color: #303c35; }
.user-name { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; font-weight: 500; overflow-wrap: anywhere; }
.username { display: block; font-size: 12px; color: #7b837f; line-height: 1.8; overflow-wrap: anywhere; }
.online-pagination { display: flex; justify-content: flex-end; margin-top: 20px; }
.online-mobile-cards { display: none; }
@media (max-width: 1000px) { .online-filters { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 767px) {
  .online-stats { gap: 8px; }
  .online-stat { padding: 14px 10px; gap: 8px; }
  .online-stat strong { font-size: 25px; }
  .online-filters { grid-template-columns: 1fr; gap: 14px; }
  .update-controls { flex-wrap: wrap; }
  .online-desktop-table { display: none; }
  .online-mobile-cards { display: grid; gap: 12px; }
  .online-user-card { padding: 14px; border: 1px solid #e4eae6; border-radius: 10px; }
  .mobile-user-heading { display: flex; gap: 12px; justify-content: space-between; }
  .mobile-user-heading > div { min-width: 0; overflow-wrap: anywhere; }
  .mobile-user-role { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; color: #52665b; font-size: 13px; margin-top: 8px; }
  dl { display: grid; grid-template-columns: 70px minmax(0, 1fr); font-size: 12px; gap: 8px; margin-bottom: 0; }
  dt { color: #7b837f; } dd { margin: 0; text-align: right; color: #48584e; overflow-wrap: anywhere; }
  .online-pagination { justify-content: center; }
}
</style>
