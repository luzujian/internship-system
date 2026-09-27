import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, existsSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'
import { automationRequest, automationTemplate } from '../src/agent/automationIntent.mjs'

test('自然语言请求和修改进入预览，普通问题仍进入问答', () => {
  for(const text of ['每天9点提醒未投递学生','我想创建自动化任务','请修改自动化任务“每日提醒”。'])assert.equal(automationRequest(text),text)
  for(const text of ['怎么设置自动化任务？','我的学生有哪些','为什么要自动提醒'])assert.equal(automationRequest(text),null)
  assert.equal(automationTemplate,'我想要创建一个自动化任务。\n任务内容是：\n执行条件是：')
})
const source=readFileSync(new URL('../src/components/agent/AutomationManager.vue',import.meta.url),'utf8')
const {descriptor}=parse(source)
const ast=ts.createSourceFile('manager.ts',descriptor.scriptSetup.content,ts.ScriptTarget.Latest,true,ts.ScriptKind.TS)
const method=ast.statements.find(node=>ts.isFunctionDeclaration(node)&&node.name.text==='operate').getText(ast)
function fixture(allowed=true,cancel=false){
  const calls=[], scope={busy:{value:false},notice:{value:''},preview:{value:null},current:()=>allowed,load:async()=>{},errorMessage:e=>e.message,
    agent:{clearAutoTaskError:()=>{},noteAutoTaskError:(id,message,operation)=>calls.push(['taskError',id,message,operation])},
    ElMessageBox:{confirm:async()=>{if(cancel)throw 'cancel'}},automationApi:{remove:async id=>calls.push(['remove',id]),pause:async id=>calls.push(['pause',id]),preview:async body=>{calls.push(['preview',body.operation]);return {data:{actionId:'action-1'}}}}}
  const operate=vm.runInNewContext(ts.transpileModule(method+'\noperate',{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText,scope)
  return {operate,calls,scope}
}
test('运行和启用先预览，暂停不发送通知',async()=>{
  const f=fixture();await f.operate({id:1,status:'PAUSED'},'toggle');await f.operate({id:1,status:'ACTIVE'},'run');await f.operate({id:1,status:'ACTIVE'},'toggle')
  assert.deepEqual(f.calls,[['preview','ENABLE'],['preview','RUN'],['pause',1]])
})
test('撤权或取消删除不执行变更',async()=>{
  const denied=fixture(false);await denied.operate({id:1},'run');assert.equal(denied.calls.length,0)
  const cancelled=fixture(true,true);await cancelled.operate({id:1},'delete');assert.equal(cancelled.calls.length,0)
})
test('操作失败时把原因挂到对应任务卡片，成功时清除',async()=>{
  const recorded=[]
  const scope={busy:{value:false},notice:{value:''},preview:{value:null},current:()=>true,load:async()=>{},
    errorMessage:e=>e?.response?.data?.message||e?.message||'暂时无法读取任务。',
    agent:{clearAutoTaskError:id=>recorded.push(['clear',id]),noteAutoTaskError:(id,message,operation)=>recorded.push(['note',id,message,operation])},
    ElMessageBox:{confirm:async()=>{}},
    automationApi:{remove:async()=>{},pause:async()=>{},preview:async()=>{throw {response:{data:{message:'任务刚刚被修改、暂停或重新启用，请重新打开后再试。'}}}}}}
  const operate=vm.runInNewContext(ts.transpileModule(method+'\noperate',{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText,scope)
  await operate({id:7,status:'ACTIVE'},'run')
  assert.deepEqual(recorded,[['clear',7],['note',7,'任务刚刚被修改、暂停或重新启用，请重新打开后再试。','run']])
  assert.match(source,/agent\.autoTaskErrors\[task\.id\]/)
  assert.match(source,/class="task-error-retry"/,'卡片错误条提供“重新核对”入口')
  assert.match(source,/async function retryTask/,'点击错误条可按原动作重开确认卡')
})

test('自动化管理只保留侧栏入口，模板来自服务端规则',()=>{
  assert.equal(existsSync(new URL('../src/views/teacher/TeacherAutoTask.vue',import.meta.url)),false)
  const router=readFileSync(new URL('../src/router/teacherRouter.ts',import.meta.url),'utf8')
  assert.doesNotMatch(router,/path:\s*'auto-tasks'/)
  assert.doesNotMatch(source,/管理模板|编辑模板|删除模板/)
  assert.match(source,/catalog\.templates/)
  const script=compileScript(descriptor,{id:'manager-test'}), compiled=compileTemplate({source:descriptor.template.content,filename:'AutomationManager.vue',id:'manager-test',compilerOptions:{bindingMetadata:script.bindings}})
  assert.deepEqual(compiled.errors,[])
})
test('自动化任务区上下高度限制在侧边栏 50% 并开启纵向滚动', () => {
  assert.match(source, /\.automation-manager\s*\{[^}]*max-height:\s*50%/)
  assert.match(source, /\.automation-manager\s*\{[^}]*overflow-y:\s*auto/)
})
