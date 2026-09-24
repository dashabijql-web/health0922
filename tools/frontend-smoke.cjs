#!/usr/bin/env node
/**
 * 前端全页面冒烟检查（Playwright，无头浏览器）
 *
 * 做什么：
 *   1. 用接口登录本地测试账号，把令牌放进浏览器 Cookie（不在网页里输入密码，令牌不打印）
 *   2. 逐个打开所有业务页面，检查：路由是否真的到达、控制台有无报错、页面异常、HTTP 4xx/5xx、图表数量
 *   3. --interact：在心率、压力、血压、血氧页切到“近30日”，点部门图的柱子，确认能打开部门异常人员抽屉
 *   4. 有任何一项失败，退出码为 1；每页截图保存在截图目录
 *
 * 不做什么：不启动、不停止前后端服务（由你自己启动）；不修改任何数据。
 *
 * 用法（在仓库根目录）：
 *   node tools/frontend-smoke.cjs                 只做页面检查
 *   node tools/frontend-smoke.cjs --interact      再加部门图点击检查
 *   HEALTH_SMOKE_ONLY=sleep,heart-rate node tools/frontend-smoke.cjs   只检查路由里包含这些词的页面
 *
 * 环境变量（都有默认值）：
 *   HEALTH_SMOKE_FRONT     前端地址，默认 http://localhost:9528
 *   HEALTH_SMOKE_API       后端地址，默认 http://localhost:8080/health
 *   HEALTH_SMOKE_USER      登录账号，默认 admin（本地默认账号，见 AGENTS.md）
 *   HEALTH_SMOKE_PASS      登录密码，默认 admin123（仅限本地；生产密码不得写入仓库）
 *   HEALTH_SMOKE_SHOTS     截图目录，默认 系统临时目录/health-smoke
 *   HEALTH_SMOKE_SETTLE_MS 每页打开后等待毫秒数，默认 3500
 *
 * 依赖：playwright-core，已在 HealthShow/package.json 的 devDependencies 中声明（先在 HealthShow 里 pnpm install）。
 * 脚本使用本机安装的 Chrome（channel: 'chrome'），找不到时回退到 Playwright 自带的 Chromium。
 *
 * 注意：前端用的是 hash 路由，真实地址是 http://localhost:9528/#/health-monitor/heart-rate。
 *       用普通路径打开会回退到首页，导致“每页都长一样”的假结果。
 */
const fs = require('fs')
const os = require('os')
const path = require('path')

const FRONT = process.env.HEALTH_SMOKE_FRONT || 'http://localhost:9528'
const API = process.env.HEALTH_SMOKE_API || 'http://localhost:8080/health'
const USER = process.env.HEALTH_SMOKE_USER || 'admin'
const PASS = process.env.HEALTH_SMOKE_PASS || 'admin123'
const SHOTS = process.env.HEALTH_SMOKE_SHOTS || path.join(os.tmpdir(), 'health-smoke')
const SETTLE_MS = Number(process.env.HEALTH_SMOKE_SETTLE_MS || 3500)
const ONLY = (process.env.HEALTH_SMOKE_ONLY || '').split(',').map((s) => s.trim()).filter(Boolean)
const INTERACT = process.argv.includes('--interact')

// 路由列表来自 HealthShow/src/router/*.ts；新增或下线页面时同步更新这里。
// minCharts：该页面至少应渲染的图表数（空数据时图表也会渲染空态，所以数量是稳定的）。
const ROUTES = [
  { route: '/health-monitor/workbench' },
  { route: '/health-monitor/dashboard' },
  { route: '/health-monitor/real-time' },
  { route: '/health-monitor/heart-rate', minCharts: 2 },
  { route: '/health-monitor/pressure', minCharts: 2 },
  { route: '/health-monitor/blood-pressure', minCharts: 3 },
  { route: '/health-monitor/blood-oxygen', minCharts: 2 },
  { route: '/health-monitor/sleep', minCharts: 6 },
  { route: '/health-monitor/risk-warning' },
  { route: '/health-monitor/employee-archive' },
  { route: '/health-monitor/mine-entry' },
  { route: '/health-monitor/report-center' },
  { route: '/health-monitor/trend-warning' },
  { route: '/health-monitor/immersive-body' },
  { route: '/health-monitor/watch-raw' },
  { route: '/health-monitor/watch-control' },
  { route: '/alert-management/records' },
  { route: '/alert-management/config' },
  { route: '/alert-management/sos' },
  { route: '/admin/device-list' },
  { route: '/admin/user-list' },
  { route: '/admin/role' },
  { route: '/admin/department' },
  { route: '/admin/job-type' },
  { route: '/ai-chat/index' }
]

// --interact 检查的页面：都应在点击部门图柱子后打开一个抽屉
const DRILLDOWN_PAGES = ['heart-rate', 'pressure', 'blood-pressure', 'blood-oxygen']

function loadPlaywright() {
  const candidates = [path.join(__dirname, '..', 'HealthShow', 'node_modules', 'playwright-core'), 'playwright-core', 'playwright']
  for (const c of candidates) {
    try { return require(c) } catch { /* 试下一个 */ }
  }
  console.error('找不到 playwright-core。请先在 HealthShow 目录运行 pnpm install')
  process.exit(2)
}

async function login() {
  let res
  try {
    res = await fetch(`${API}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: USER, password: PASS }) })
  } catch (e) {
    console.error(`连不上后端 ${API}（${e.message}）。请先自行启动后端。`)
    process.exit(2)
  }
  const body = await res.json().catch(() => ({}))
  const token = body?.data?.token
  if (!token) {
    console.error(`登录失败：HTTP ${res.status} ${body?.message || ''}`)
    process.exit(2)
  }
  return token
}

async function frontendReachable() {
  try { await fetch(FRONT) } catch {
    console.error(`连不上前端 ${FRONT}。请先自行启动前端。`)
    process.exit(2)
  }
}

const currentRoutePath = (page) => page.evaluate(() => document.querySelector('#app')?.__vue_app__?.config.globalProperties.$router?.currentRoute.value.path)

async function checkPage(ctx, { route, minCharts }) {
  const page = await ctx.newPage()
  const problems = []
  const errs = new Set()
  const bad = new Set()
  page.on('console', (m) => { if (m.type() === 'error') errs.add(m.text().slice(0, 220)) })
  page.on('pageerror', (e) => errs.add('PAGEERROR ' + String(e.message).slice(0, 220)))
  page.on('response', (res) => {
    if (res.status() >= 400 && !/favicon|perf-log/.test(res.url())) bad.add(`${res.status()} ${res.url().replace(FRONT, '').slice(0, 100)}`)
  })
  try {
    await page.goto(`${FRONT}/#${route}`, { waitUntil: 'load', timeout: 30000 })
    await page.waitForTimeout(SETTLE_MS)
  } catch (e) {
    problems.push('打开失败：' + e.message.slice(0, 100))
  }
  const arrived = await currentRoutePath(page).catch(() => null)
  const charts = await page.evaluate(() => document.querySelectorAll('[_echarts_instance_]').length).catch(() => 0)
  if (arrived !== route) problems.push(`路由未到达：期望 ${route}，实际 ${arrived}`)
  if (minCharts && charts < minCharts) problems.push(`图表数量不足：期望至少 ${minCharts}，实际 ${charts}`)
  errs.forEach((e) => problems.push('控制台错误：' + e))
  bad.forEach((e) => problems.push('HTTP 错误：' + e))
  await page.screenshot({ path: path.join(SHOTS, route.replace(/\//g, '_') + '.png') }).catch(() => {})
  await page.close()
  return { route, charts, problems }
}

// 切到“近30日”（页面默认是当日，今天没数据时图是空的），然后在部门图（左侧那列高而窄的图）里从上到下点，
// 直到弹出抽屉；点了一整列都没弹出就算失败。
async function checkDrilldown(ctx, name) {
  const page = await ctx.newPage()
  const problems = []
  const errs = new Set()
  page.on('pageerror', (e) => errs.add(String(e.message).slice(0, 200)))
  page.on('console', (m) => { if (m.type() === 'error') errs.add(m.text().slice(0, 200)) })
  await page.goto(`${FRONT}/#/health-monitor/${name}`, { waitUntil: 'load' })
  await page.waitForTimeout(SETTLE_MS)
  const thirty = page.getByText('近30日', { exact: true }).first()
  if (await thirty.count()) { await thirty.click(); await page.waitForTimeout(SETTLE_MS) }
  const box = await page.evaluate(() => {
    const list = [...document.querySelectorAll('[_echarts_instance_]')].map((e) => e.getBoundingClientRect())
    const dept = list.filter((r) => r.height > r.width && r.x < 600).sort((a, b) => b.height - a.height)[0]
    return dept ? { x: dept.x, y: dept.y, w: dept.width, h: dept.height } : null
  })
  const drawerOpen = () => page.evaluate(() => [...document.querySelectorAll('.el-drawer,.el-dialog')]
    .some((e) => e.offsetParent !== null && getComputedStyle(e).display !== 'none'))
  let opened = false
  if (!box) {
    problems.push('找不到部门图容器')
  } else {
    outer: for (let y = box.y + 12; y < box.y + box.h - 8; y += 14) {
      for (const fx of [0.25, 0.5, 0.75]) {
        await page.mouse.click(box.x + box.w * fx, y)
        await page.waitForTimeout(250)
        if (await drawerOpen()) { opened = true; break outer }
      }
    }
    if (!opened) problems.push('点击部门图柱子后没有打开抽屉')
  }
  errs.forEach((e) => problems.push('控制台错误：' + e))
  await page.screenshot({ path: path.join(SHOTS, `interact_${name}.png`) }).catch(() => {})
  await page.close()
  return { name, problems }
}

;(async () => {
  const { chromium } = loadPlaywright()
  await frontendReachable()
  const token = await login()
  fs.mkdirSync(SHOTS, { recursive: true })
  const browser = await chromium.launch({ channel: 'chrome', headless: true }).catch(() => chromium.launch({ headless: true }))
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 900 } })
  await ctx.addCookies([{ name: 'User-Token', value: token, url: FRONT }])

  let failed = 0
  const routes = ROUTES.filter((r) => !ONLY.length || ONLY.some((k) => r.route.includes(k)))
  console.log(`页面检查（${routes.length} 页）  前端 ${FRONT}  截图 ${SHOTS}`)
  for (const item of routes) {
    const r = await checkPage(ctx, item)
    console.log(`${r.problems.length ? 'XX' : 'ok'} ${r.route}  图表=${r.charts}`)
    r.problems.forEach((p) => console.log('     ' + p))
    if (r.problems.length) failed++
  }

  if (INTERACT) {
    const pages = DRILLDOWN_PAGES.filter((n) => !ONLY.length || ONLY.some((k) => n.includes(k)))
    console.log(`\n部门图点击检查（${pages.length} 页）`)
    for (const name of pages) {
      const r = await checkDrilldown(ctx, name)
      console.log(`${r.problems.length ? 'XX' : 'ok'} ${name}`)
      r.problems.forEach((p) => console.log('     ' + p))
      if (r.problems.length) failed++
    }
  }

  await browser.close()
  console.log(failed ? `\n有 ${failed} 项未通过` : '\n全部通过')
  process.exit(failed ? 1 : 0)
})()
