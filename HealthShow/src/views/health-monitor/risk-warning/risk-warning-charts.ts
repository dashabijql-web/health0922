import * as echarts from '@/utils/echarts-setup'
import type { EChartsType } from 'echarts/core'
import { emptyOption, chartTooltip } from '@/utils/echarts-config'
import { initChart, gradV, type ChartStore } from '@/utils/chart-helpers'

export interface WarningStat { label: string; value: number; color: string }
export interface DeptStat { deptName: string; heartRate?: number; bloodOxygen?: number; temperature?: number; pressure?: number; deviceAlarm?: number }
export interface TrendData {
  dates: string[]
  series: { heartRate: number[]; bloodOxygen: number[]; temperature: number[]; pressure: number[]; deviceAlarm: number[] }
}

export function renderRiskTrend(charts: ChartStore, el: HTMLElement | null | undefined, trend: TrendData) {
  const c=initChart(charts, 'trend', el); if(!c) return
  const { dates, series } = trend
  if(!dates.length){ c.setOption(emptyOption('暂无趋势数据')); return }
  c.setOption({
    backgroundColor:'transparent',
    tooltip:chartTooltip(),
    legend:{data:['心率','血氧','体温','压力','设备报警'],right:10,top:4,textStyle:{color:'#8ba6c8',fontSize:11},itemWidth:16,itemHeight:8},
    grid:{left:'4%',right:'4%',top:'12%',bottom:'10%',containLabel:true},
    xAxis:{type:'category',data:dates,boundaryGap:false,axisLine:{lineStyle:{color:'rgba(0,212,255,0.18)'}},axisTick:{show:false},axisLabel:{color:'#8ba6c8',fontSize:10,interval:4}},
    yAxis:{type:'value',axisLine:{show:false},axisTick:{show:false},splitLine:{lineStyle:{color:'rgba(0,212,255,0.07)',type:'dashed'}},axisLabel:{color:'#8ba6c8',fontSize:10}},
    series:[
      {name:'心率',type:'line',data:series.heartRate,smooth:true,symbol:'none',lineStyle:{color:'#ef4444',width:1.5},itemStyle:{color:'#ef4444'}},
      {name:'血氧',type:'line',data:series.bloodOxygen,smooth:true,symbol:'none',lineStyle:{color:'#f97316',width:1.5},itemStyle:{color:'#f97316'}},
      {name:'体温',type:'line',data:series.temperature,smooth:true,symbol:'none',lineStyle:{color:'#22c55e',width:1.5},itemStyle:{color:'#22c55e'}},
      {name:'压力',type:'line',data:series.pressure,smooth:true,symbol:'none',lineStyle:{color:'#00d4ff',width:1.5},itemStyle:{color:'#00d4ff'}},
      {name:'设备报警',type:'line',data:series.deviceAlarm,smooth:true,symbol:'none',lineStyle:{color:'#a855f7',width:1.5},itemStyle:{color:'#a855f7'}}
    ]
  })
}

export function renderRiskDept(charts: ChartStore, el: HTMLElement | null | undefined, deptData: DeptStat[]) {
  const c=initChart(charts, 'dept', el); if(!c) return
  if(!deptData.length){ c.setOption(emptyOption()); return }
  const names=deptData.map(d=>d.deptName)
  // FIX ④: 动态左侧留白防截断
  const maxLen=Math.max(...names.map(n=>n.length))
  const leftPct=Math.min(42,Math.max(24,maxLen*2.8))+'%'
  c.setOption({
    backgroundColor:'transparent',
    tooltip:{trigger:'axis',axisPointer:{type:'shadow'},backgroundColor:'rgba(8,13,35,0.92)',borderColor:'rgba(0,212,255,0.25)',textStyle:{color:'#e0f0ff',fontSize:12}},
    legend:{data:['心率','血氧','体温','压力','设备报警'],right:6,top:4,textStyle:{color:'#8ba6c8',fontSize:12},itemWidth:10,itemHeight:8,icon:'rect'},
    grid:{left:leftPct,right:'8%',top:'14%',bottom:'6%'},
    xAxis:{type:'value',axisLine:{show:false},axisTick:{show:false},splitLine:{lineStyle:{color:'rgba(0,212,255,0.07)',type:'dashed'}},axisLabel:{color:'#8ba6c8',fontSize:12}},
    yAxis:{type:'category',data:names,inverse:true,axisLine:{show:false},axisTick:{show:false},axisLabel:{color:'#a8c5e6',fontSize:12,overflow:'truncate',width:92}},
    series:[
      {name:'心率',type:'bar',stack:'total',barWidth:'50%',data:deptData.map(d=>d.heartRate||0),itemStyle:{color:'#ef4444'},label:{show:true,position:'inside',color:'#fff',fontSize:12,formatter:p=>p.value>0?p.value:''}},
      {name:'血氧',type:'bar',stack:'total',data:deptData.map(d=>d.bloodOxygen||0),itemStyle:{color:'#f97316'},label:{show:true,position:'inside',color:'#fff',fontSize:12,formatter:p=>p.value>0?p.value:''}},
      {name:'体温',type:'bar',stack:'total',data:deptData.map(d=>d.temperature||0),itemStyle:{color:'#22c55e'},label:{show:true,position:'inside',color:'#fff',fontSize:12,formatter:p=>p.value>0?p.value:''}},
      {name:'压力',type:'bar',stack:'total',data:deptData.map(d=>d.pressure||0),itemStyle:{color:'#00d4ff'},label:{show:true,position:'inside',color:'#fff',fontSize:12,formatter:p=>p.value>0?p.value:''}},
      {name:'设备报警',type:'bar',stack:'total',data:deptData.map(d=>d.deviceAlarm||0),itemStyle:{color:'#a855f7',borderRadius:[0,4,4,0]},label:{show:true,position:'inside',color:'#fff',fontSize:12,formatter:p=>p.value>0?p.value:''}}
    ]
  })
}

export function renderRiskTrendDay(charts: ChartStore, el: HTMLElement | null | undefined, stats: WarningStat[]) {
  const c=initChart(charts, 'trend', el); if(!c) return
  const total=stats.reduce((s,x)=>s+x.value,0)
  if(!total){ c.setOption(emptyOption('今日暂无预警数据')); return }
  c.setOption({
    backgroundColor:'transparent',
    tooltip:{trigger:'axis',axisPointer:{type:'shadow'},backgroundColor:'rgba(8,13,35,0.92)',borderColor:'rgba(0,212,255,0.25)',textStyle:{color:'#e0f0ff',fontSize:12},formatter:p=>`${p[0].name}：<b style="color:${stats[p[0].dataIndex]?.color||'#00d4ff'}">${p[0].value}</b> 次`},
    grid:{left:'5%',right:'5%',top:'12%',bottom:'12%',containLabel:true},
    xAxis:{type:'category',data:stats.map(x=>x.label),axisLine:{lineStyle:{color:'rgba(0,212,255,0.18)'}},axisTick:{show:false},axisLabel:{color:'#a8c5e6',fontSize:12}},
    yAxis:{type:'value',axisLine:{show:false},axisTick:{show:false},splitLine:{lineStyle:{color:'rgba(0,212,255,0.07)',type:'dashed'}},axisLabel:{color:'#8ba6c8',fontSize:10}},
    series:[{type:'bar',barWidth:'40%',data:stats.map(x=>({value:x.value,itemStyle:{color:gradV(x.color,x.color+'55'),borderRadius:[6,6,0,0]}})),label:{show:true,position:'top',color:'#e0f0ff',fontSize:13,fontWeight:'bold',fontFamily:'Consolas'}}]
  })
}

/** 预警详情抽屉里的体征曲线：销毁上一个实例，创建并返回新实例（容器不存在时返回 null） */
export function renderVitalChart(el: HTMLElement | null | undefined, previous: EChartsType | null, times: string[], hrs: Array<number | null>, spo2: Array<number | null>, temps: Array<number | null>, warnTimeStr: string): EChartsType | null {
  if (!el) return null
  previous?.dispose()
  const c = echarts.init(el)

  // 找预警时刻在 x 轴的索引
  const warnIdx = times.findIndex(t => t >= warnTimeStr)

  c.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(8,13,35,.95)',
      borderColor: 'rgba(0,212,255,.3)',
      textStyle: { color: '#e0f0ff', fontSize: 12 }
    },
    legend: {
      data: ['心率','血氧','体温'],
      top: 4, right: 8,
      textStyle: { color: '#8ba6c8', fontSize: 11 },
      itemWidth: 14, itemHeight: 8
    },
    grid: { left: 14, right: 14, top: 36, bottom: 32, containLabel: true },
    xAxis: {
      type: 'category', data: times, boundaryGap: false,
      axisLine: { lineStyle: { color: 'rgba(0,212,255,.2)' } },
      axisTick: { show: false },
      axisLabel: { color: '#8ba6c8', fontSize: 10, interval: Math.floor(times.length/6) }
    },
    yAxis: [
      // 左轴：心率 40-160，血氧映射到同轴但用右轴刻度显示
      { type:'value', name:'bpm / %',
        nameTextStyle:{color:'#8ba6c8',fontSize:9},
        min: 40, max: 160, interval: 20,
        axisLine:{show:false}, axisTick:{show:false},
        splitLine:{lineStyle:{color:'rgba(0,212,255,.07)',type:'dashed'}},
        axisLabel:{color:'#8ba6c8',fontSize:10} },
      // 右轴：体温 35.0-39.0°C
      { type:'value', name:'°C',
        nameTextStyle:{color:'#22c55e',fontSize:9},
        min: 35, max: 39, interval: 1,
        axisLine:{show:false}, axisTick:{show:false}, splitLine:{show:false},
        axisLabel:{color:'#22c55e',fontSize:10, formatter:v => v.toFixed(1)} },
    ],
    series: [
      {
        name:'心率', type:'line', color:'#ef4444', data: hrs, smooth: true, symbol:'none',
        lineStyle:{color:'#ef4444',width:2},
        areaStyle:{color:gradV('rgba(239,68,68,.25)','rgba(239,68,68,.02)')},
        markLine: warnIdx >= 0 ? {
          silent: true,
          symbol: ['none','none'],
          lineStyle: { color: '#ff3b3b', width: 2.5, type: 'solid', shadowColor: 'rgba(255,59,59,.6)', shadowBlur: 8 },
          data: [{ xAxis: warnTimeStr, label: {
            show: true, position: 'insideStartTop',
            formatter: '预警时刻',
            color: '#fff', fontSize: 11, fontWeight: 700,
            backgroundColor: '#ef4444',
            padding: [3,7,3,7], borderRadius: 4,
            shadowColor: 'rgba(239,68,68,.5)', shadowBlur: 6
          }}]
        } : {},
        markPoint: warnIdx >= 0 && hrs[warnIdx] != null ? {
          data: [{ coord:[warnTimeStr, hrs[warnIdx]], symbol:'circle', symbolSize:14,
            itemStyle:{color:'transparent',borderColor:'#ef4444',borderWidth:3}, label:{show:false} }]
        } : {}
      },
      { name:'血氧', type:'line', color:'#f97316', data: spo2, smooth:true, symbol:'none',
        yAxisIndex: 0,
        lineStyle:{color:'#f97316',width:2},
        areaStyle:{color:gradV('rgba(249,115,22,.2)','rgba(249,115,22,.01)')},
        markPoint: warnIdx >= 0 && spo2[warnIdx] != null ? {
          data: [{ coord:[warnTimeStr, spo2[warnIdx]], symbol:'circle', symbolSize:14,
            itemStyle:{color:'transparent',borderColor:'#f97316',borderWidth:3}, label:{show:false} }]
        } : {}
      },
      { name:'体温', type:'line', color:'#22c55e', data: temps, smooth:true, symbol:'none',
        yAxisIndex:1, lineStyle:{color:'#22c55e',width:2},
        markPoint: warnIdx >= 0 && temps[warnIdx] != null ? {
          data: [{ coord:[warnTimeStr, temps[warnIdx]], symbol:'circle', symbolSize:14,
            itemStyle:{color:'transparent',borderColor:'#22c55e',borderWidth:3}, label:{show:false} }]
        } : {}
      }
    ]
  })
  return c
}
