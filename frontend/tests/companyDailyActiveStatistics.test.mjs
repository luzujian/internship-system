import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { parse, compileTemplate } from '@vue/compiler-sfc'

const source = readFileSync(new URL('../src/views/admin/DataStatisticsManagement.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const ast = ts.createSourceFile('DataStatisticsManagement.ts', descriptor.scriptSetup.content, ts.ScriptTarget.Latest, true)
const script = ast.statements.filter(statement => !ts.isImportDeclaration(statement)).map(statement => statement.getText(ast)).join('\n')
const compiled = ts.transpileModule(script + '\n;({refreshData,companyChartData,studentChartData,teacherChartData,adminChartData,days,dateRange,dateRangeText,loading,companyChartRef,getChartOption,getAverageCount,getMaxCount,getChangeRate,renderCharts})', { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
const plain = value => JSON.parse(JSON.stringify(value))

function page(initialData) {
  let data = initialData, cleanup
  const calls = [], charts = [], observers = []
  const state = vm.runInNewContext(compiled, {
    ref: value => ({value}), computed: getter => ({get value(){return getter()}}), watch() {},
    onMounted() {}, onBeforeUnmount: fn => {cleanup=fn}, nextTick: fn => fn(),
    useAuthStore: () => ({}), logger:{error(){}}, ElMessage:{success(){},error(){},warning(){}},
    request:{get:async(url,options)=>{calls.push({url,params:plain(options.params)});return {code:200,data}}},
    echarts:{graphic:{LinearGradient:class{}},init:()=>{
      const chart={disposed:false,setOption(option){this.option=option},dispose(){this.disposed=true},isDisposed(){return this.disposed},resize(){}}
      charts.push(chart);return chart
    }},
    ResizeObserver:class{constructor(){this.disconnected=false;observers.push(this)}observe(){}disconnect(){this.disconnected=true}},
  })
  return {...state,calls,charts,observers,setData:value=>{data=value},cleanup:()=>cleanup()}
}

test('company card consumes the active-users response alongside all existing roles', async()=>{
  const companyData=[{date:'10-08',count:2},{date:'10-09',count:4}]
  const p=page({companyData,studentData:[{date:'10-08',count:8}],teacherData:[],adminData:[],dateRange:'2026-10-08 至 2026-10-09'})
  await p.refreshData()
  assert.deepEqual(plain(p.companyChartData.value),companyData)
  assert.equal(p.studentChartData.value[0].count,8)
  assert.equal(p.getAverageCount(p.companyChartData.value),3)
  assert.equal(p.getMaxCount(p.companyChartData.value),4)
  assert.equal(p.dateRangeText.value,'2026-10-08 至 2026-10-09')
  assert.deepEqual(p.calls,[{url:'/login-logs/active-users',params:{days:7}}])
  assert.equal(p.loading.value,false)
  assert.deepEqual(compileTemplate({source:descriptor.template.content,filename:'DataStatisticsManagement.vue',id:'company-dau'}).errors,[])
})

test('snake-case responses and a later zero period replace old company counts', async()=>{
  const p=page({company_data:[{date:'10-08',count:5}]})
  await p.refreshData();assert.equal(p.companyChartData.value[0].count,5)
  p.setData({companyData:[{date:'10-08',count:0},{date:'10-09',count:0}]})
  await p.refreshData()
  assert.equal(p.getAverageCount(p.companyChartData.value),0)
  assert.equal(p.getMaxCount(p.companyChartData.value),0)
  assert.equal(p.getChangeRate(p.companyChartData.value),'0%')
})

test('preset and custom filters use the shared request and company tooltip counts accounts', async()=>{
  const p=page({companyData:[]})
  p.days.value=14;await p.refreshData();assert.deepEqual(p.calls.at(-1).params,{days:14})
  p.days.value=0;p.dateRange.value=['2026-10-01','2026-10-10'];await p.refreshData()
  assert.deepEqual(p.calls.at(-1).params,{startDate:'2026-10-01',endDate:'2026-10-10'})
  const option=p.getChartOption([{date:'10-08',count:3}],'#9B6DDF','活跃企业账号','个')
  assert.equal(option.series[0].type,'line')
  assert.equal(option.tooltip.formatter([{name:'10-08',value:3}]),'10-08<br/>活跃企业账号: 3个')
})

test('refresh and unmount dispose the company chart and its resize observer', async()=>{
  const p=page({companyData:[{date:'10-08',count:2}]})
  p.companyChartRef.value={};await p.refreshData()
  assert.equal(p.charts.length,1);assert.equal(p.observers.length,1)
  await p.refreshData()
  assert.equal(p.charts[0].disposed,true);assert.equal(p.observers[0].disconnected,true)
  p.cleanup()
  assert.equal(p.charts[1].disposed,true);assert.equal(p.observers[1].disconnected,true)
})
