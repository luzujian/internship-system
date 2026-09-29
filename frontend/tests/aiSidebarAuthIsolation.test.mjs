import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import vm from 'node:vm'
import ts from 'typescript'
import { createPinia } from 'pinia'
import * as tabSession from '../src/store/auth/tabSession.mjs'

const require = createRequire(import.meta.url)
const root = fileURLToPath(new URL('../src/store/auth',import.meta.url))
const memory = () => { const values = new Map(); return { getItem: key => values.get(key) ?? null, setItem: (key,value) => values.set(key,String(value)), removeItem: key => values.delete(key) } }
function fixture(shared = memory(), session = memory()) {
  const cache = new Map(), requests = []
  const request = { defaults: {baseURL:'/api'}, async post(url, body) { requests.push({url,body}); return {code:200,data:{accessToken:jwt('renewed','ROLE_TEACHER_DEPARTMENT'),refreshToken:'next-refresh'}} } }
  const quiet = {log(){},warn(){},error(){}}
  function load(file) {
    if(cache.has(file)) return cache.get(file).exports
    const module = {exports:{}}; cache.set(file,module)
    const code = ts.transpileModule(readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText
    const localRequire = name => {
      if(name === '@/utils/request') return {__esModule:true,default:request,cleanupTokens(){tabSession.clearTabSession(session)}}
      if(name === '@/utils/logger') return {__esModule:true,default:quiet}
      if(name === 'element-plus') return {ElMessage:{error(){},warning(){}}}
      if(name.endsWith('tabSession.mjs')) return {readTabSession:()=>tabSession.readTabSession(session),writeTabSession:state=>tabSession.writeTabSession(state,session),clearTabSession:()=>tabSession.clearTabSession(session)}
      if(name.startsWith('.')) return load(resolve(dirname(file),name+'.ts'))
      return require(name)
    }
    vm.runInNewContext(code,{module,exports:module.exports,require:localRequire,console:quiet,localStorage:shared,sessionStorage:session,window:{location:{pathname:'/login'},atob},setTimeout},{filename:file})
    return module.exports
  }
  const api=load(resolve(root,'index.ts')), store=api.useAuthStore(createPinia())
  const requestSource=readFileSync(new URL('../src/utils/request.ts',import.meta.url),'utf8')
  const ast=ts.createSourceFile('request.ts',requestSource,ts.ScriptTarget.Latest,true)
  const func=ast.statements.find(node=>ts.isFunctionDeclaration(node)&&node.name?.text==='getAccessToken').getText(ast).replace(/^export /,'')
  const getToken=vm.runInNewContext(ts.transpileModule(func+'\ngetAccessToken',{compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText,{useAuthStore:()=>store})
  return {store,shared,session,getToken,requests}
}
function jwt(username, role) { return 'header.'+Buffer.from(JSON.stringify({username,role,userId:username,name:username,teacherType:role.replace('ROLE_TEACHER_',''),exp:Math.floor(Date.now()/1000)+3600,jti:username,typ:'access'})).toString('base64url')+'.signature' }
function login(f, username, role) {
  const token=jwt(username,role)
  assert.equal(f.store.handleLoginSuccess({code:200,data:{accessToken:token,refreshToken:username+'-refresh',user:{id:username,username,name:username,role,teacherType:role.replace('ROLE_TEACHER_','')}}},''),true)
  return token
}

test('parallel student and company tabs keep request identity after login and reload',async()=>{
  const student=fixture(), company=fixture(student.shared)
  const s=login(student,'s001','ROLE_STUDENT'), c=login(company,'company1','ROLE_COMPANY')
  assert.equal(student.getToken(),s); assert.equal(company.getToken(),c)
  const restored=fixture(student.shared,student.session)
  assert.equal(await restored.store.restoreAuthState(false),true)
  assert.equal(restored.store.role,'ROLE_STUDENT'); assert.equal(restored.getToken(),s)
})
test('teacher subtype switch cannot restore earlier college token or teacher type',async()=>{
  const f=fixture(); login(f,'t001','ROLE_TEACHER_COLLEGE')
  const t=login(f,'t002','ROLE_TEACHER_DEPARTMENT')
  await Promise.resolve()
  assert.equal(f.store.user.username,'t002'); assert.equal(f.store.user.teacherType,'DEPARTMENT'); assert.equal(f.getToken(),t)
  f.shared.setItem('current_role','ROLE_TEACHER_COLLEGE')
  const restored=fixture(f.shared,f.session); await restored.store.restoreAuthState(false)
  assert.equal(restored.store.role,'ROLE_TEACHER_DEPARTMENT'); assert.equal(restored.getToken(),t)
})
test('same-role tabs restore their own account and refresh with their own credential',async()=>{
  const a=fixture(), b=fixture(a.shared)
  const first=login(a,'teacherA','ROLE_TEACHER_DEPARTMENT'); login(b,'teacherB','ROLE_TEACHER_DEPARTMENT')
  const reload=fixture(a.shared,a.session); await reload.store.restoreAuthState(false)
  assert.equal(reload.getToken(),first)
  assert.equal(await reload.store.refreshAccessToken(),true)
  assert.equal(reload.requests[0].body.refreshToken,'teacherA-refresh')
  assert.equal(tabSession.readTabSession(reload.session).refreshToken,'next-refresh')
})
test('cleared tab credentials are not reused by getAccessToken',()=>{
  const f=fixture(); login(f,'s001','ROLE_STUDENT'); f.store.clearAllAuthInfo()
  assert.equal(f.getToken(),null); assert.equal(tabSession.readTabSession(f.session),null)
})

test('old account responses and retries are rejected after switching identity',()=>{
  const axios=require('axios')
  const auth={token:'student-token',role:'ROLE_STUDENT',user:{id:'1',username:'s001'}}
  const source=readFileSync(new URL('../src/utils/request.ts',import.meta.url),'utf8').replace('import.meta.env.VITE_API_BASE_URL',"'/api'")
  const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText
  const module={exports:{}}
  vm.runInNewContext(compiled,{module,exports:module.exports,console:{log(){},warn(){},error(){}},localStorage:memory(),window:{location:{pathname:'/student/home'}},require:name=>{
    if(name==='@/store/auth')return {useAuthStore:()=>auth}
    if(name==='@/router')return {__esModule:true,default:{push(){}}}
    if(name.includes('tabSession'))return {clearTabSession(){}}
    if(name.includes('uploadErrorMessage'))return {getUploadErrorMessage:()=>''}
    if(name==='element-plus')return {ElMessage:{warning(){},error(){}}}
    return require(name)
  }})
  const request=module.exports.default
  const prepare=request.interceptors.request.handlers[0].fulfilled
  const receive=request.interceptors.response.handlers[0].fulfilled
  const config=prepare({url:'/agent/capabilities',headers:{}})
  assert.equal(config.headers.Authorization,'Bearer student-token')
  auth.token='company-token';auth.role='ROLE_COMPANY';auth.user={id:'2',username:'company1'}
  assert.throws(()=>prepare(config),axios.isCancel)
  return assert.rejects(receive({config,status:200,data:{code:200,data:{}}}),axios.isCancel)
})
