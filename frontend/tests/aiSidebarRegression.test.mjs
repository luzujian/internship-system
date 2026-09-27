import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { automationRequest } from '../src/agent/automationIntent.mjs'
import { editableAutomationDefinition } from '../src/agent/automationDefinition.mjs'
import { shouldResolveUiIntent } from '../src/agent/sendRouting.mjs'

test('reminder dialog headers cannot move the sidebar below the viewport',()=>{
  const {descriptor}=parse(readFileSync(new URL('../src/components/agent/AgentWorkspace.vue',import.meta.url),'utf8'))
  const ast=ts.createSourceFile('workspace.ts',descriptor.scriptSetup.content,ts.ScriptTarget.Latest,true)
  const node=ast.statements.find(n=>ts.isVariableStatement(n)&&n.declarationList.declarations.some(d=>d.name.getText(ast)==='readBannerHeight'))
  const shell={getBoundingClientRect:()=>({bottom:64})},dialog={getBoundingClientRect:()=>({bottom:1008})}
  const offset={value:0},styles=[]
  const host={querySelector(selector){return selector==='.header'?shell:selector===':scope > .read-only-banner'?null:dialog}}
  const scope={topOffset:offset,document:{querySelector:()=>host,body:{style:{setProperty:(...args)=>styles.push(args)}}}}
  const read=vm.runInNewContext(ts.transpileModule(node.getText(ast)+'\nreadBannerHeight',{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText,scope)
  read(); assert.equal(offset.value,64); assert.deepEqual(styles,[['--business-overlay-top','64px']])
})
const require=createRequire(import.meta.url)

test('navigation and immediate reminder no longer enter scheduled automation',()=>{
  for(const text of ['帮我打开通知管理页面的发布公告表单','给学生s001发站内提醒，内容是请核对实习鉴定提交截止日期。只生成确认预览，不要实际发送。']) assert.equal(automationRequest(text),null)
  for(const text of ['每天9点提醒未投递学生','请创建自动化任务，每周一提醒学生','明天下午3点提醒学生交鉴定']) assert.equal(automationRequest(text),text)
  assert.equal(shouldResolveUiIntent('帮我投递企业1的测试岗位，先给我确认预览，不要正式提交。'),false)
})
test('malformed model preview actually renders without inventing a schedule',async()=>{
  const source=readFileSync(new URL('../src/components/agent/AutomationPreview.vue',import.meta.url),'utf8')
  const {descriptor}=parse(source)
  const script=compileScript(descriptor,{id:'automation-preview-test',inlineTemplate:true})
  const code=ts.transpileModule(script.content,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText
  const module={exports:{}}
  vm.runInNewContext(code,{module,exports:module.exports,structuredClone,require:name=>{
    if(name.includes('automationDefinition')) return {editableAutomationDefinition}
    if(name.includes('automationV2')) return {automationApi:{}}
    if(name.includes('formatTriggerLabel')) return {formatTriggerLabel:()=>''}
    return require(name)
  }})
  for(const bad of [null,[],{notification:{body:'测试'},trigger:null,repeatPolicy:null},{notification:{body:'测试'},trigger:'invalid',repeatPolicy:[]}]){
    const html=await renderToString(createSSRApp(module.exports.default,{preview:{definition:bad,questions:['请补充时间']}}))
    assert.match(html,/请补充时间/)
    assert.match(html,/disabled/)
    assert.equal(editableAutomationDefinition(bad).trigger.mode,undefined)
  }
})

test('open-and-fill retains the original assignments and clears the sent draft',async()=>{
  const source=readFileSync(new URL('../src/components/agent/AgentWorkspace.vue',import.meta.url),'utf8')
  const {descriptor}=parse(source)
  const ast=ts.createSourceFile('workspace.ts',descriptor.scriptSetup.content,ts.ScriptTarget.Latest,true)
  const func=ast.statements.find(node=>ts.isFunctionDeclaration(node)&&node.name?.text==='handleUiRequest').getText(ast)
  const message='打开发布岗位表单，岗位名称填写测试，招聘人数填写5，只填写不要发布。'
  const linkedForm={value:null},calls=[]
  const agent={capabilities:{actor:{ownerKey:'company'},enabled:true},sessionId:'session',visible:true,formFillEnabled:true,draft:message,
    fillForm:async text=>{calls.push(text);return true},cancelFill(){}}
  const scope={agent,linkedForm,auth:{token:'company-token'},route:{fullPath:'/company/recruitment',path:'/company/recruitment'},router:{},readOnly:{value:false},uiWorking:{value:false},AbortController,
    collectUiTargets:()=>[{kind:'open',label:'发布岗位',element:{click(){linkedForm.value={instanceId:1}}}}],
    nextTick:async()=>{},waitForUiTargets:async()=>{},uiController:null,uiExpectedPath:'',uiNavigationPath:null}
  const invoke=vm.runInNewContext(ts.transpileModule(func+'\nhandleUiRequest',{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText,scope)
  assert.equal(await invoke(message),true)
  assert.deepEqual(calls,[message]);assert.equal(agent.draft,'');assert.equal(scope.uiWorking.value,false)
})

test('explicit reminder channel survives opening the confirmation card',()=>{
  const source=readFileSync(new URL('../src/agent/store.ts',import.meta.url),'utf8')
  const ast=ts.createSourceFile('store.ts',source,ts.ScriptTarget.Latest,true)
  let declaration
  function visit(node){if(ts.isFunctionDeclaration(node)&&node.name?.text==='openReminder')declaration=node;ts.forEachChild(node,visit)}
  visit(ast)
  const dialog={value:{}},scope={capabilities:{value:{enabled:true,actor:{readOnly:false}}},error:{value:''},reminderDialog:dialog}
  const open=vm.runInNewContext(ts.transpileModule(declaration.getText(ast)+'\nopenReminder',{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText,scope)
  open({studentQuery:'s001',emailAvailable:true,channel:'STATION_REMINDER',content:'请核对日期'})
  assert.equal(dialog.value.channel,'STATION_REMINDER');assert.equal(dialog.value.content,'请核对日期')
  open({studentQuery:'s001',emailAvailable:false,channel:'EMAIL'})
  assert.equal(dialog.value.channel,'EMAIL','missing mailbox must not silently switch an explicitly selected channel')
})
