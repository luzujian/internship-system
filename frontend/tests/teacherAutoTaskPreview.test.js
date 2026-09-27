import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import * as vue from 'vue'
import { editableAutomationDefinition } from '../src/agent/automationDefinition.mjs'
import { parse,compileScript,compileTemplate } from '@vue/compiler-sfc'

const source=readFileSync(new URL('../src/components/agent/AutomationPreview.vue',import.meta.url),'utf8')
const {descriptor}=parse(source),script=compileScript(descriptor,{id:'preview-test'})
const code=ts.transpileModule(script.content,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText
function fixture(questions=[]){
  const writes=[],previews=[],emits=[]
  const preview={actionId:'first-preview',questions,definition:{name:'岗位提醒',condition:{field:'internship.status',op:'EQ',value:'NOT_FOUND'},trigger:{mode:'DAILY',time:'09:00'},notification:{subject:'原始标题',body:'原始内容',channels:['EMAIL']},repeatPolicy:{mode:'REPEAT',cooldownMinutes:1440}}}
  const api={preview:async body=>{previews.push(body);return {data:{...preview,...body,actionId:'updated-preview'}}},confirm:async id=>{writes.push(id);return {data:{id:1,status:'ACTIVE'}}},matches:async()=>({data:{total:0,items:[]}})}
  const modules={vue:{...vue,onBeforeUnmount(){}},'@/api/automationV2':{automationApi:api},'@/agent/automationDefinition.mjs':{editableAutomationDefinition}}
  const context={exports:{},structuredClone,require:name=>modules[name]}
  vm.runInNewContext(code,context)
  const state=context.exports.default.setup({preview,sessionId:'teacher-session'},{expose(){},emit:(...args)=>emits.push(args)})
  return {state,writes,previews,preview,emits}
}
test('编辑通知文案不改变筛选与时间，核对不执行，确认使用新版本',async()=>{
  const f=fixture();f.state.definition.value.notification.body='教师自由编辑\n第二行';f.state.definition.value.notification.subject='新的标题'
  assert.equal(f.state.dirty.value,true);assert.equal(f.preview.definition.notification.body,'原始内容')
  await f.state.refresh();assert.equal(f.writes.length,0)
  assert.equal(f.previews[0].definition.notification.body,'教师自由编辑\n第二行');assert.equal(f.previews[0].definition.trigger.time,'09:00');assert.equal(f.previews[0].definition.condition.value,'NOT_FOUND')
  await f.state.submit();assert.deepEqual(f.writes,['updated-preview']);assert.equal(f.emits.at(-1)[0],'confirmed')
})
test('有追问时不能确认，无效草稿不发送',async()=>{
  const f=fixture(['请选择有效条件']);assert.equal(f.state.valid.value,false);await f.state.submit();assert.equal(f.writes.length,0)
})
test('保存草稿带有暂停标记，重复点击确认在等待期间被拦截',async()=>{
  const f=fixture();await f.state.refresh(true);assert.equal(f.previews[0].saveOnly,true);assert.deepEqual(f.writes,['updated-preview'])
  f.state.busy.value=true;await f.state.submit();assert.equal(f.writes.length,1)
})
test('预览模板可编译',()=>{
  const result=compileTemplate({source:descriptor.template.content,filename:'AutomationPreview.vue',id:'preview-test',compilerOptions:{bindingMetadata:script.bindings}})
  assert.deepEqual(result.errors,[])
})
test('规则摘要渲染为只读展示框且下次检查文本清晰', () => {
  assert.match(descriptor.template.content, /class="summary-box"/)
  assert.match(descriptor.template.content, /下次定时检查/)
  assert.match(descriptor.styles[0].content, /\.summary-box\s*\{[^}]*border-radius:\s*8px/)
})

