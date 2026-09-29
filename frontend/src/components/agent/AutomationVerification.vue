<script setup lang="ts">
import { ref } from 'vue'
import request from '@/utils/request'
const items=ref<any[]>([]), selected=ref<any>(null), evidence=ref(''), outcome=ref(''), preview=ref<any>(null), busy=ref(false), notice=ref('')
async function load() { try { items.value=((await request.get('/admin/automation-deliveries')) as any).data || [] } catch(e:any){notice.value=e.message} }
function choose(item:any){selected.value=item;evidence.value='';outcome.value='';preview.value=null}
async function verify(){
  busy.value=true
  try{preview.value=((await request.post('/admin/automation-deliveries/'+selected.value.id+'/preview',{outcome:outcome.value,evidence:evidence.value})) as any).data}
  catch(e:any){notice.value=e.message}finally{busy.value=false}
}
async function confirm(){
  busy.value=true
  try{await request.post('/admin/automation-deliveries/confirm',{actionId:preview.value.id,confirmed:true});selected.value=null;preview.value=null;notice.value='核实结果已保存。';await load()}
  catch(e:any){notice.value=e.message}finally{busy.value=false}
}
</script>
<template>
  <details @toggle="($event.target as HTMLDetailsElement).open && load()"><summary>邮件结果核实</summary>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="!items.length">没有待核实邮件</p>
    <div v-for="item in items" :key="item.id" class="item"><span>{{ item.task_name }} · {{ item.student_name }} · {{ item.subject }}</span><el-button link type="primary" @click="choose(item)">核实</el-button></div>
    <el-form v-if="selected" label-position="top">
      <el-form-item label="根据邮件服务记录核实结果"><el-radio-group v-model="outcome" @change="preview=null"><el-radio value="ACCEPTED">邮件服务已受理</el-radio><el-radio value="NOT_ACCEPTED">确认未受理</el-radio></el-radio-group></el-form-item>
      <el-form-item label="核实凭据"><el-input v-model="evidence" type="textarea" placeholder="填写邮件服务记录编号及核实依据" :maxlength="2000" @input="preview=null" /></el-form-item>
      <p v-if="preview">{{ outcome==='ACCEPTED' ? '将记录为邮件服务已受理。' : '将记录为明确未发送，教师可另行确认重试。' }}</p>
      <el-button v-if="!preview" :disabled="!outcome || !evidence.trim()" :loading="busy" @click="verify">核对结果</el-button><el-button v-else type="primary" :loading="busy" @click="confirm">确认保存</el-button><el-button @click="selected=null">取消</el-button>
    </el-form>
  </details>
</template>
<style scoped>details{margin-top:16px;font-size:14px;}summary{cursor:pointer;}p{color:var(--el-text-color-secondary);font-size:13px}.item{display:flex;justify-content:space-between;gap:12px;margin:12px 0;}</style>
