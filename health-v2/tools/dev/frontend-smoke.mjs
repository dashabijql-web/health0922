#!/usr/bin/env node
// 前端冒烟脚本：用无头浏览器逐页打开，检查路由是否到达、控制台报错、HTTP 错误、是否出现滚动条，并截图。
// 需要前端（9529）和后端（8081）已启动：tools/dev/start.sh；地图模式还要 GeoServer（8082，tools/dev/docker-compose.yml）
//
// 用法：node tools/dev/frontend-smoke.mjs [--expect-hint=true|false] [--no-dev-pages]
//   --expect-hint   登录页是否应显示默认账号提示（和后端 LOGIN_SHOW_DEFAULT_ACCOUNT 一致），默认 true
//   --no-dev-pages  跳过只在开发模式存在的页面（对生产构建跑时用）
// 环境变量：SMOKE_BASE_URL（默认 http://127.0.0.1:9529）、SMOKE_USER / SMOKE_PASSWORD（默认开发期公开账号 admin/admin）
// 截图保存在 runtime/dev/smoke/（不入库）。任何一项失败退出码为 1。
//
// 之后每个阶段新增页面时，往 PAGES 里加一行。
// 所有交互只看不改：不保存年龄、不加入/移出名单、不保存基站摆放（这些都会写删不掉的操作日志）。
// 月度汇总只读：翻页、切换月份、开关弹层（SMOKE_REPORT_ROUNDS 次，默认 6，看有没有越开越多的内存和绘制循环）。

import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HV2_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const require = createRequire(path.join(HV2_ROOT, 'frontend/package.json'))
const { chromium } = require('playwright-core')

const args = new Map(
  process.argv.slice(2).map((a) => {
    const [k, v] = a.replace(/^--/, '').split('=')
    return [k, v ?? 'true']
  })
)
const EXPECT_HINT = args.get('expect-hint') !== 'false'
const DEV_PAGES = !args.has('no-dev-pages')
const BASE = (process.env.SMOKE_BASE_URL ?? 'http://127.0.0.1:9529').replace(/\/$/, '')
const USER = process.env.SMOKE_USER ?? 'admin'
const PASSWORD = process.env.SMOKE_PASSWORD ?? 'admin'
const SHOT_DIR = path.join(HV2_ROOT, 'runtime/dev/smoke')
const HINT_TEXT = '默认账号 admin，密码 admin'

const VIEWPORTS = [
  { width: 1920, height: 1080 },
  { width: 1366, height: 768 }
]

// 需要登录的页面：hash 路由，必须写成 /#/...，否则会回到首页得到"每页都一样"的假结果
// charts：页面上应有的 ECharts 图表数；interact：打开后要点一点的交互（失败记一项）
const PAGES = [
  { name: 'portal', hash: '#/portal', selector: '.ring-counter', text: '重点监护人员', charts: 0, interact: portalInteract },
  { name: 'dashboard', hash: '#/dashboard', selector: '.stage__total-label', text: '累计采集数据', charts: 1,
    interact: dashboardInteract },
  { name: 'dashboard-map', hash: '#/dashboard?mode=map', selector: '.map-stage', text: '地图模式', charts: 1,
    interact: mapInteract },
  { name: 'archive', hash: '#/archive', selector: '.archive__bar', text: '健康数据汇总', charts: 0,
    interact: archiveInteract },
  // 个人档案：卡编码运行时从接口取（健康档案第一个人）；图表数随数据而定，在交互里逐个面板检查
  { name: 'person-archive', hash: () => (firstCard ? `#/archive/${firstCard}` : null), selector: '.info',
    text: '姓名', interact: personInteract },
  { name: 'dev-components', hash: '#/dev/components', selector: '.preview', dev: true }
]

/** 健康档案第一个人的卡编码（登录后从接口取）；没有绑定手表的人时为 null，个人档案页跳过 */
let firstCard = null
/** 进出个人档案的轮数（3D 人体释放检查） */
const BODY_ROUNDS = Number(process.env.SMOKE_BODY_ROUNDS ?? 6)
/** 开关月度汇总的轮数（翻书释放检查） */
const REPORT_ROUNDS = Number(process.env.SMOKE_REPORT_ROUNDS ?? 6)

const failures = []
const fail = (msg) => {
  failures.push(msg)
  console.log(`  ✗ ${msg}`)
}
const pass = (msg) => console.log(`  ✓ ${msg}`)

async function launch() {
  try {
    return await chromium.launch({ headless: true })
  } catch (error) {
    if (!/Executable doesn't exist/.test(String(error))) throw error
    // 没有 Playwright 自带的浏览器时用本机 Chrome
    return chromium.launch({ headless: true, channel: 'chrome' })
  }
}

/** 给页面挂上错误收集：控制台报错、页面异常、HTTP ≥ 400、请求失败。 */
function watch(page, label) {
  const problems = []
  page.on('console', (m) => {
    if (m.type() === 'error') problems.push(`控制台报错：${m.text()}`)
  })
  page.on('pageerror', (e) => problems.push(`页面异常：${e.message}`))
  page.on('response', (r) => {
    if (r.status() >= 400) problems.push(`HTTP ${r.status()}：${r.request().method()} ${r.url()}`)
  })
  page.on('requestfailed', (r) => {
    const reason = r.failure()?.errorText ?? ''
    // 页面跳转时被浏览器主动取消的请求不算
    if (!reason.includes('ERR_ABORTED')) problems.push(`请求失败：${r.url()} ${reason}`)
  })
  return () => {
    if (problems.length === 0) pass(`${label}：无控制台报错、无 HTTP 错误`)
    for (const p of problems) fail(`${label}：${p}`)
    problems.length = 0
  }
}

/** 整页完整显示、没有滚动条；有缩放画布时画布完全落在窗口里。 */
async function checkLayout(page, label, viewport) {
  const m = await page.evaluate(() => {
    const doc = document.documentElement
    const canvas = document.querySelector('.scale-canvas')
    const r = canvas?.getBoundingClientRect()
    return {
      scrollW: Math.max(doc.scrollWidth, document.body.scrollWidth),
      scrollH: Math.max(doc.scrollHeight, document.body.scrollHeight),
      clientW: doc.clientWidth,
      clientH: doc.clientHeight,
      canvas: r ? { left: r.left, top: r.top, right: r.right, bottom: r.bottom, width: r.width } : null
    }
  })
  if (m.scrollW > m.clientW || m.scrollH > m.clientH) {
    fail(`${label}：出现滚动条（内容 ${m.scrollW}×${m.scrollH}，窗口 ${m.clientW}×${m.clientH}）`)
  } else {
    pass(`${label}：无滚动条（内容 ${m.scrollW}×${m.scrollH}，窗口 ${m.clientW}×${m.clientH}）`)
  }
  if (m.canvas) {
    const c = m.canvas
    const inside = c.left >= -0.5 && c.top >= -0.5 && c.right <= viewport.width + 0.5 && c.bottom <= viewport.height + 0.5
    const scale = (c.width / 1920).toFixed(4)
    if (inside) pass(`${label}：1920×1080 画布缩放 ${scale} 倍，完整落在窗口内`)
    else fail(`${label}：画布超出窗口 ${JSON.stringify(c)}`)
  }
}

/** 入口页：四个体征面板、四个圆环都在；"数据展示"进入动态数据页 */
async function portalInteract(page, label) {
  const panels = await page.locator('.metric').count()
  const rings = await page.locator('.ring-counter').count()
  if (panels === 4 && rings === 4) pass(`${label}：4 个体征面板、4 个圆环`)
  else fail(`${label}：体征面板 ${panels} 个、圆环 ${rings} 个`)
  await page.getByRole('button', { name: '数据展示' }).click()
  await page.waitForURL(/#\/dashboard/, { timeout: 5000 }).catch(() => null)
  if (new URL(page.url()).hash.startsWith('#/dashboard')) pass(`${label}：点"数据展示"进入动态数据`)
  else fail(`${label}：点"数据展示"没有进入动态数据，当前 ${page.url()}`)
}

/** 动态数据页：告警格子、设备事件、佩戴情况确认框、切换模式 */
async function dashboardInteract(page, label) {
  const tiles = await page.locator('.stat-tile').count()
  if (tiles === 6) pass(`${label}：6 个告警格子`)
  else fail(`${label}：告警格子 ${tiles} 个`)

  await page.locator('.stat-tile').first().click()
  const alertModal = page.locator('.screen-modal__box', { hasText: '今天的SOS告警' })
  if (await alertModal.waitFor({ timeout: 3000 }).then(() => true, () => false)) pass(`${label}：点 SOS 格子弹出名单`)
  else fail(`${label}：点 SOS 格子没有弹出名单`)
  await page.keyboard.press('Escape')

  await page.locator('.device-badge').click()
  const devModal = page.locator('.screen-modal__box', { hasText: '今天的设备事件' })
  if (await devModal.waitFor({ timeout: 3000 }).then(() => true, () => false)) pass(`${label}：点"设备事件"弹出列表`)
  else fail(`${label}：点"设备事件"没有弹出列表`)
  await page.locator('.screen-modal__close').click()

  await page.getByRole('button', { name: '佩戴情况', exact: true }).click()
  const confirm = page.locator('.screen-modal__box', { hasText: '此操作将导出下井职工前一天手表佩戴情况' })
  if (await confirm.waitFor({ timeout: 3000 }).then(() => true, () => false)) pass(`${label}：佩戴情况确认框`)
  else fail(`${label}：没有弹出佩戴情况确认框`)
  const [download] = await Promise.all([
    page.waitForEvent('download', { timeout: 15000 }).catch(() => null),
    confirm.getByRole('button', { name: '确定' }).click()
  ])
  const fileName = download?.suggestedFilename() ?? ''
  if (/^佩戴情况_\d{14}\.xlsx$/.test(fileName)) pass(`${label}：下载 ${fileName}`)
  else fail(`${label}：没有下载到佩戴情况 Excel（${fileName || '无'}）`)

  await reportInteract(page, label)

  await page.getByRole('button', { name: '展示模式', exact: true }).click()
  await page.waitForSelector('.map-stage', { timeout: 3000 }).catch(() => null)
  if (new URL(page.url()).hash.includes('mode=map')) pass(`${label}：切换到地图模式`)
  else fail(`${label}：没有切换到地图模式`)
}

/** "2026-08" → "2026年08月"（和页面的 report-format.ts 一样） */
const monthText = (m) => (m ? `${m.slice(0, 4)}年${m.slice(5, 7)}月` : '')
/** 去掉所有空白，比较文字时不受换行、<b> 两边空格影响 */
const squash = (t) => (t ?? '').replace(/\s+/g, '')

/** 第 n 页（封面是 0）的文字 */
async function pageText(page, n) {
  return squash(await page.locator('.book-page').nth(n).textContent())
}

/** 每秒 requestAnimationFrame 回调的次数（ECharts、翻书的绘制循环都用它；关掉的书还在画就会多出来） */
async function rafPerSecond(page) {
  const a = await page.evaluate(() => window.__raf)
  await page.waitForTimeout(1000)
  return (await page.evaluate(() => window.__raf)) - a
}

/** 翻到第 n 页所在的对页，等翻完 */
async function waitSpread(page, n) {
  return page.locator(`.book[data-page="${n}"][data-flip-state="read"]`).waitFor({ timeout: 5000 })
    .then(() => true, () => false)
}

/** 月度汇总（动态数据页"月度汇总"打开）：分包按需下载、13 页的页码和免责声明、每页数字和接口一致、
 *  没有数据的页"暂无数据"、键盘/点击/拖动/目录翻页、页角卷起、切换月份、Esc 关闭，最后反复开关看释放 */
async function reportInteract(page, label) {
  const loaded = () => page.evaluate(() => performance.getEntriesByType('resource')
    .some((e) => /page-flip|MonthlyReportDialog/.test(e.name)))
  if (await loaded()) fail(`${label}：还没打开月报就下载了 page-flip`)
  else pass(`${label}：打开月报前没有下载 page-flip 和月报分包`)

  const rafBefore = await rafPerSecond(page)
  const base = await (async () => {
    const cdp = await page.context().newCDPSession(page)
    await cdp.send('Performance.enable')
    await cdp.send('HeapProfiler.collectGarbage')
    const { metrics } = await cdp.send('Performance.getMetrics')
    await cdp.detach()
    return Object.fromEntries(metrics.map((x) => [x.name, x.value]))
  })()

  await page.getByRole('button', { name: '月度汇总', exact: true }).click()
  const ready = await page.locator('.book[data-state="ready"]').waitFor({ timeout: 10000 }).then(() => true, () => false)
  if (ready && await loaded()) pass(`${label}：点"月度汇总"打开翻书，这时才下载 page-flip`)
  else fail(`${label}：月度汇总没有打开（ready=${ready}）`)

  const months = await apiGet(page, '/report/months')
  const month = months.defaultMonth
  // 等报告加载完（页面不再显示"加载中"）
  await page.waitForFunction(() => !document.querySelector('.rp__state'), null, { timeout: 15000 }).catch(() => null)
  const r = month ? await apiGet(page, `/report/monthly?month=${month}`) : null
  const cover = squash(await page.locator('.cover__month').textContent())
  if (cover === (month ? monthText(month) : '暂无数据')) pass(`${label}：封面月份"${cover}"（有数据的月份 ${months.months.join('、') || '无'}，默认 ${month}）`)
  else fail(`${label}：封面月份"${cover}"，接口默认 ${month}`)

  const nos = (await page.locator('.rp__no').allTextContents()).map(squash)
  const want = Array.from({ length: 13 }, (_, i) => `${i + 1}/13`)
  if (JSON.stringify(nos) === JSON.stringify(want)) pass(`${label}：13 页页码 1/13 … 13/13`)
  else fail(`${label}：页码不对：${nos.join(' ')}`)
  const disclaimers = await page.locator('.rp__disclaimer', { hasText: '不能作为临床或治疗的依据' }).count()
  if (disclaimers === 13) pass(`${label}：13 页都有免责声明`)
  else fail(`${label}：有免责声明的页 ${disclaimers} 个`)
  if ((await page.locator('.book-page').count()) === 15) pass(`${label}：封面 + 13 页 + 封底`)
  else fail(`${label}：书页 ${await page.locator('.book-page').count()} 个`)

  // ---- 每页的数字和接口一致；没有数据的页显示"暂无数据" ----
  if (r) {
    const m = monthText(month)
    const checks = [
      [3, r.overview, () => `涉及职工${r.overview.workerCount}人，涵盖工种${r.overview.jobKindCount}个`],
      [4, r.watchUsage, () => `共有${r.watchUsage.userCount}人使用智能手表`],
      [5, r.vitals, () => ['HEART_RATE', 'TEMPERATURE', 'SPO2'].map((k) => r.vitals[k]
        ? `最大值${k === 'TEMPERATURE' ? r.vitals[k].max.toFixed(1) : r.vitals[k].max}` : '暂无数据')],
      [6, r.alerts, () => `共发生${r.alerts.events}起，涉及${r.alerts.persons}人`],
      [8, r.stability?.HEART_RATE, () => `共${r.stability.HEART_RATE.persons}人有心率数据`],
      [9, r.stability?.TEMPERATURE, () => `共${r.stability.TEMPERATURE.persons}人有体温数据`],
      [10, r.stability?.SPO2, () => `共${r.stability.SPO2.persons}人有血氧数据`],
      [11, r.steps, () => r.steps.low[0] ? `${r.steps.low[0].name}${r.steps.low[0].cardCode.slice(-5)}` : '没有人有'],
      [12, r.steps, () => r.steps.high[0] ? `${r.steps.high[0].avgSteps}` : '没有人有'],
      [13, r.risk, () => r.risk.total > 0 ? `需要重点关注的职工共${r.risk.total}人` : '本月暂无需要重点关注的职工']
    ]
    const empties = []
    for (const [n, section, text] of checks) {
      const t = await pageText(page, n)
      if (!section) {
        const emptyText = n === 6 ? `${m}，本月暂无告警数据` : `${m}，暂无数据`
        if (t.includes(squash(emptyText))) empties.push(n)
        else fail(`${label}：第 ${n} 页没有数据，应显示"${emptyText}"`)
      } else if ([].concat(text()).every((x) => t.includes(squash(x)))) {
        pass(`${label}：第 ${n} 页和接口一致（${[].concat(text()).join(' / ').slice(0, 50)}）`)
      } else fail(`${label}：第 ${n} 页和接口不一致，应含"${[].concat(text()).join('"、"')}"`)
    }
    if (empties.length) pass(`${label}：没有数据的第 ${empties.join('、')} 页显示"暂无数据"`)
  }

  // ---- 翻页：页角卷起、键盘、点击、拖动、目录 ----
  const book = page.locator('.book')
  const pageBox = async () => {
    const b = await page.locator('.stf__block').boundingBox()
    return { x: b.x, y: b.y, w: b.width, h: b.height }
  }
  let box = await pageBox()
  await page.mouse.move(box.x + box.w - 6, box.y + box.h - 6)
  const curled = await page.locator('.book[data-flip-state="fold_corner"]').waitFor({ timeout: 3000 }).then(() => true, () => false)
  if (curled) pass(`${label}：鼠标移到封面右下角，页角卷起`)
  else fail(`${label}：鼠标在右下角，页角没有卷起（${await book.getAttribute('data-flip-state')}）`)
  await page.mouse.move(box.x + box.w / 2, box.y - 30)
  await page.locator('.book[data-flip-state="read"]').waitFor({ timeout: 3000 }).catch(() => null)
  await page.screenshot({ path: path.join(SHOT_DIR, 'report-0-cover.png') })

  const order = [1, 3, 5, 7, 9, 11, 13]
  let keyOk = true
  for (const n of order) {
    await page.keyboard.press('ArrowRight')
    if (!(await waitSpread(page, n))) {
      keyOk = false
      fail(`${label}：→ 没有翻到第 ${n} 页（停在 ${await book.getAttribute('data-page')}）`)
      break
    }
    await page.waitForTimeout(400)
    await page.screenshot({ path: path.join(SHOT_DIR, `report-${String(n).padStart(2, '0')}.png`) })
  }
  if (keyOk) pass(`${label}：键盘 → 依次翻到 1、3、5…13 页（每个对页截图 report-*.png）`)
  await page.keyboard.press('ArrowRight')
  await page.waitForTimeout(1200)
  if ((await book.getAttribute('data-page')) === '13') pass(`${label}：最后一个对页（13 / 封底）再按 → 不动，第 13 页没有落单`)
  else fail(`${label}：最后一页之后还能翻：${await book.getAttribute('data-page')}`)
  await page.keyboard.press('ArrowLeft')
  if (await waitSpread(page, 11)) pass(`${label}：← 翻回第 11 页`)
  else fail(`${label}：← 没有翻回第 11 页`)

  // 点右页正中：翻到下一页；从右边缘拖到左边：再翻一页（先回到第 1 页）
  for (let i = 0; i < 5; i++) {
    await page.keyboard.press('ArrowLeft')
    await page.waitForTimeout(900)
  }
  if (!(await waitSpread(page, 1))) fail(`${label}：没有回到第 1 页`)
  box = await pageBox()
  await page.mouse.click(box.x + box.w * 0.75, box.y + box.h * 0.5)
  if (await waitSpread(page, 3)) pass(`${label}：点击右页翻到第 3 页`)
  else fail(`${label}：点击右页没有翻页（${await book.getAttribute('data-page')}）`)
  await page.mouse.move(box.x + box.w - 10, box.y + box.h - 30)
  await page.mouse.down()
  for (let i = 1; i <= 12; i++) await page.mouse.move(box.x + box.w - 10 - i * 60, box.y + box.h - 40, { steps: 2 })
  await page.mouse.up()
  if (await waitSpread(page, 5)) pass(`${label}：从右下角往左拖，翻到第 5 页`)
  else fail(`${label}：拖动没有翻页（${await book.getAttribute('data-page')}）`)

  // 目录：回到第 1–2 页，点"二、职工健康评估"
  for (let i = 0; i < 2; i++) {
    await page.keyboard.press('ArrowLeft')
    await page.waitForTimeout(900)
  }
  await waitSpread(page, 1)
  await page.locator('.catalog__item', { hasText: '职工健康评估' }).click()
  if (await waitSpread(page, 7)) pass(`${label}：目录里点"职工健康评估"翻到第 7 页`)
  else fail(`${label}：目录没有翻到第 7 页（${await book.getAttribute('data-page')}）`)

  // ---- 切换月份：换一个有数据的月份，第 4 页跟着变 ----
  const other = months.months.find((x) => x !== month)
  if (other) {
    await page.locator('.report__month select').selectOption(other)
    await page.waitForFunction((t) => document.querySelector('.cover__month')?.textContent?.trim() === t,
      monthText(other), { timeout: 5000 }).catch(() => null)
    await page.waitForFunction(() => !document.querySelector('.rp__state'), null, { timeout: 15000 }).catch(() => null)
    const r2 = await apiGet(page, `/report/monthly?month=${other}`)
    const t4 = await pageText(page, 4)
    const ok = r2.watchUsage ? t4.includes(`共有${r2.watchUsage.userCount}人`) : t4.includes(squash(`${monthText(other)}，暂无数据`))
    if (ok) pass(`${label}：切换到 ${other}，第 4 页${r2.watchUsage ? `${r2.watchUsage.userCount} 人` : '"暂无数据"'}和接口一致`)
    else fail(`${label}：切换到 ${other} 后第 4 页不对`)
  }

  await page.keyboard.press('Escape')
  if (await page.locator('.report').waitFor({ state: 'detached', timeout: 3000 }).then(() => true, () => false)) {
    pass(`${label}：Esc 关闭月度汇总`)
  } else fail(`${label}：Esc 没有关闭月度汇总`)

  // ---- 反复开关：绘制循环、图表、DOM、事件监听都要释放 ----
  const cdp = await page.context().newCDPSession(page)
  await cdp.send('Performance.enable')
  const measure = async () => {
    await cdp.send('HeapProfiler.collectGarbage')
    const { metrics } = await cdp.send('Performance.getMetrics')
    const x = Object.fromEntries(metrics.map((v) => [v.name, v.value]))
    return { heapMb: x.JSHeapUsedSize / 1048576, nodes: x.Nodes, listeners: x.JSEventListeners,
      raf: await rafPerSecond(page) }
  }
  const samples = []
  for (let round = 1; round <= REPORT_ROUNDS; round++) {
    await page.getByRole('button', { name: '月度汇总', exact: true }).click()
    await page.locator('.book[data-state="ready"]').waitFor({ timeout: 10000 })
    await page.waitForFunction(() => !document.querySelector('.rp__state'), null, { timeout: 15000 }).catch(() => null)
    await page.keyboard.press('ArrowRight')
    await waitSpread(page, 1)
    const open = await rafPerSecond(page)
    await page.locator('.report__close').click()
    await page.locator('.report').waitFor({ state: 'detached', timeout: 3000 })
    await page.waitForTimeout(300)
    samples.push({ ...(await measure()), open })
  }
  console.log(`    打开月报前：堆 ${(base.JSHeapUsedSize / 1048576).toFixed(1)} MB、DOM 节点 ${base.Nodes}、监听 ${base.JSEventListeners}、每秒绘制回调 ${rafBefore}` +
    samples.map((s, i) => `\n      第 ${i + 1} 次关闭后：堆 ${s.heapMb.toFixed(1)} MB、DOM 节点 ${s.nodes}、监听 ${s.listeners}、每秒绘制回调 ${s.raf}（打开时 ${s.open}）`).join(''))
  const last = samples.at(-1)
  const second = samples[1] ?? samples[0]
  if (samples.every((s) => s.raf <= rafBefore + 15)) pass(`${label}：每次关闭后每秒绘制回调回到 ${rafBefore} 左右（打开时 ${samples[0].open}），绘制循环都停了`)
  else fail(`${label}：关闭后绘制回调没有回落：${samples.map((s) => s.raf).join('、')}（打开前 ${rafBefore}）`)
  if (last.heapMb - second.heapMb < 3) pass(`${label}：JS 堆第 2 次 ${second.heapMb.toFixed(1)} MB → 最后 ${last.heapMb.toFixed(1)} MB（增长 < 3 MB）`)
  else fail(`${label}：JS 堆一直在涨：${second.heapMb.toFixed(1)} → ${last.heapMb.toFixed(1)} MB`)
  if (last.nodes - second.nodes < 300 && last.listeners - second.listeners < 30) {
    pass(`${label}：DOM 节点 ${second.nodes} → ${last.nodes}、事件监听 ${second.listeners} → ${last.listeners}，没有越积越多`)
  } else fail(`${label}：DOM 节点 ${second.nodes} → ${last.nodes}、事件监听 ${second.listeners} → ${last.listeners}`)
  await cdp.detach()
}

/** 地图模式：底图出来了、名单过滤、搜索、摆放模式和操作记录（只看不改，不保存任何摆放） */
async function mapInteract(page, label) {
  // 底图请求在页面打开时就发出去了，从浏览器的资源记录里找（含 HTTP 状态码）
  await page.waitForLoadState('networkidle')
  const wms = await page.evaluate(() => performance.getEntriesByType('resource')
    .filter((e) => e.name.includes('/geoserver/hv2/wms'))
    .map((e) => ({ url: e.name, status: e.responseStatus })))
  const failed = await page.locator('.map-stage__notice', { hasText: '底图加载失败' }).count()
  if (wms.length > 0 && wms.every((w) => w.status === 200) && failed === 0) pass(`${label}：底图加载成功（${wms.length} 次 WMS 请求）`)
  else fail(`${label}：底图没有加载出来 ${JSON.stringify(wms)}`)
  if (wms.length > 0 && wms.every((w) => new URL(w.url).searchParams.get('VERSION') === '1.1.1')) pass(`${label}：底图请求都用 WMS 1.1.1`)
  else fail(`${label}：底图请求不是 WMS 1.1.1：${wms.map((w) => w.url).join(' ')}`)

  await page.getByRole('button', { name: '重点监护' }).click()
  const note = page.locator('.map-stage__filter-note', { hasText: '重点监护' })
  if (await note.waitFor({ timeout: 3000 }).then(() => true, () => false)) pass(`${label}：点"重点监护"只显示名单里的人`)
  else fail(`${label}：点"重点监护"没有反应`)
  await page.getByRole('button', { name: '重点监护' }).click()
  if (await note.waitFor({ state: 'detached', timeout: 3000 }).then(() => true, () => false)) pass(`${label}：再点一次取消过滤`)
  else fail(`${label}：再点"重点监护"没有取消过滤`)

  await page.locator('.map-stage__search-input').fill('冒烟测试不存在的人')
  await page.locator('.map-stage__search-input').press('Enter')
  if (await page.locator('.el-message', { hasText: '井下没有找到' }).waitFor({ timeout: 5000 }).then(() => true, () => false)) {
    pass(`${label}：搜不到的人提示"井下没有找到"`)
  } else fail(`${label}：搜索没有提示`)

  const tower = page.locator('.map-stage__tower')
  const shown = await tower.getAttribute('aria-pressed')
  await tower.click()
  if ((await tower.getAttribute('aria-pressed')) !== shown) pass(`${label}：左下角按钮切换基站显示`)
  else fail(`${label}：左下角按钮没有切换基站显示`)
  await tower.click()

  await page.getByRole('button', { name: '摆放基站' }).click()
  const hint = page.locator('.place__hint')
  if (await hint.filter({ hasText: /个基站/ }).waitFor({ timeout: 5000 }).then(() => true, () => false)) {
    pass(`${label}：摆放模式：${(await hint.textContent()).trim()}`)
  } else fail(`${label}：没有进入摆放模式`)
  await page.locator('.place__link').click()
  if (await page.locator('.logs').waitFor({ timeout: 3000 }).then(() => true, () => false)) pass(`${label}：打开操作记录抽屉`)
  else fail(`${label}：操作记录抽屉没有打开`)
  await page.locator('.logs__close').click()
  await page.getByRole('button', { name: '退出摆放' }).click()

  await page.getByRole('button', { name: '地图模式', exact: true }).click()
  await page.waitForSelector('.stage__total-label', { timeout: 3000 }).catch(() => null)
  if (!new URL(page.url()).hash.includes('mode=map')) pass(`${label}：切回展示模式`)
  else fail(`${label}：没有切回展示模式`)
}

/** 带着页面里的登录令牌调接口，返回 data */
async function apiGet(page, url) {
  return page.evaluate(async (u) => {
    const r = await fetch(`/api${u}`, { headers: { satoken: localStorage.getItem('hv2-token') ?? '' } })
    return (await r.json()).data
  }, url)
}

/** 页面已经下载过的脚本里有没有 three.js（开发模式是 deps/three.js，打包后是 BodyModel3D 的分包） */
async function threeLoaded(page) {
  return page.evaluate(() => performance.getEntriesByType('resource')
    .some((e) => /\/deps\/three\.js|\/three[._-]|BodyModel3D/.test(e.name) || e.name.endsWith('.glb')))
}

/** 健康档案：卡片数、总数、翻页、部门/工种/姓名筛选、重置、打开个人档案 */
async function archiveInteract(page, label) {
  const first = await apiGet(page, '/archive/persons?page=1&size=12')
  await page.locator('.person-card, .archive__empty').first().waitFor({ timeout: 5000 }).catch(() => null)
  const cards = await page.locator('.person-card').count()
  if (cards === Math.min(12, first.total)) pass(`${label}：第 1 页 ${cards} 张卡片（接口总数 ${first.total}）`)
  else fail(`${label}：卡片 ${cards} 张，接口总数 ${first.total}`)
  const countText = async () => (await page.locator('.archive__count').textContent())?.replace(/\s+/g, '') ?? ''
  const expectTotal = async (total, what) => {
    const ok = await page.waitForFunction((t) => document.querySelector('.archive__count')?.textContent
      ?.replace(/\s+/g, '').startsWith(`共${t}人`), total, { timeout: 5000 }).then(() => true, () => false)
    if (ok) pass(`${label}：${what}，共 ${total} 人与接口一致`)
    else fail(`${label}：${what}，页面"${await countText()}"，接口 ${total}`)
  }
  await expectTotal(first.total, '不筛选')
  if (await threeLoaded(page)) fail(`${label}：健康档案列表就下载了 three.js 或人体模型`)
  else pass(`${label}：列表页没有下载 three.js 和人体模型`)

  const pages = Math.max(1, Math.ceil(first.total / 12))
  if (pages > 1) {
    await page.locator('.archive__arrow--next').click()
    const p2 = await apiGet(page, '/archive/persons?page=2&size=12')
    const ok = await page.waitForFunction((name) => document.querySelector('.person-card__name')?.textContent === name,
      p2.list[0]?.name ?? '未录入', { timeout: 5000 }).then(() => true, () => false)
    if (ok && (await countText()).includes(`第2/${pages}页`)) pass(`${label}：右箭头翻到第 2 页，第一张是 ${p2.list[0]?.name}`)
    else fail(`${label}：右箭头翻页不对（${await countText()}）`)
    await page.locator('.archive__arrow--prev').click()
    await page.waitForFunction(() => document.querySelector('.archive__count')?.textContent?.replace(/\s+/g, '').includes('第1/'),
      null, { timeout: 5000 }).catch(() => null)
  }
  if (await page.locator('.archive__arrow--prev').isDisabled()) pass(`${label}：第 1 页时左箭头不可点`)
  else fail(`${label}：第 1 页时左箭头还能点`)

  const filters = await apiGet(page, '/archive/filters')
  const enc = encodeURIComponent
  if (filters.depts.length > 0) {
    const dept = filters.depts[0]
    await page.locator('.archive__dept input').click()
    await page.locator('.archive__dept .screen-select__option', { hasText: dept }).first().click()
    await page.getByRole('button', { name: '查询', exact: true }).click()
    const r = await apiGet(page, `/archive/persons?dept=${enc(dept)}&size=12`)
    await expectTotal(r.total, `部门"${dept}"`)
    if (filters.jobKinds.length > 0) {
      const job = filters.jobKinds[filters.jobKinds.length - 1]
      await page.locator('.archive__job input').fill(job.slice(0, 2))
      await page.locator('.archive__job .screen-select__option', { hasText: job }).first().click()
      await page.getByRole('button', { name: '查询', exact: true }).click()
      const r2 = await apiGet(page, `/archive/persons?dept=${enc(dept)}&jobKind=${enc(job)}&size=12`)
      await expectTotal(r2.total, `部门"${dept}" + 工种"${job}"（输入文字过滤后选中）`)
    }
    await page.getByRole('button', { name: '重置', exact: true }).click()
    await expectTotal(first.total, '重置')
  }
  const name = first.list[0]?.name
  if (name) {
    const kw = name.slice(-1)
    await page.locator('.archive__keyword').fill(kw)
    await page.locator('.archive__keyword').press('Enter')
    const r = await apiGet(page, `/archive/persons?keyword=${enc(kw)}&size=12`)
    await expectTotal(r.total, `姓名包含"${kw}"`)
    const names = await page.locator('.person-card__name').allTextContents()
    const tail = first.list[0].cardCode.slice(-5)
    if (names.length > 0 && names.every((n) => n.includes(kw))) pass(`${label}：查到的 ${names.length} 人姓名都含"${kw}"`)
    else fail(`${label}：按姓名查询结果不对：${names.join('、')}`)
    await page.locator('.archive__keyword').fill(tail)
    await page.getByRole('button', { name: '查询', exact: true }).click()
    const r2 = await apiGet(page, `/archive/persons?keyword=${tail}&size=12`)
    await expectTotal(r2.total, `卡号"${tail}"`)
    await page.getByRole('button', { name: '重置', exact: true }).click()
    await expectTotal(first.total, '重置')
  }
  await page.getByRole('button', { name: '健康数据汇总' }).click()
  if (await page.locator('.book[data-state="ready"]').waitFor({ timeout: 10000 }).then(() => true, () => false)) {
    pass(`${label}："健康数据汇总"打开月度汇总`)
  } else fail(`${label}："健康数据汇总"没有打开月度汇总`)
  await page.keyboard.press('Escape')
  if (await page.locator('.report').waitFor({ state: 'detached', timeout: 3000 }).then(() => true, () => false)) {
    pass(`${label}：Esc 关闭月度汇总`)
  } else fail(`${label}：Esc 没有关闭月度汇总`)

  if (first.list[0]) {
    await page.locator('.person-card__open').first().click()
    await page.waitForURL(/#\/archive\/[0-9A-Za-z]{17}/, { timeout: 5000 }).catch(() => null)
    if (new URL(page.url()).hash === `#/archive/${first.list[0].cardCode}`) pass(`${label}：点"健康档案"进入个人档案`)
    else fail(`${label}：点"健康档案"没有进入个人档案，当前 ${page.url()}`)
  }
}

/** 进程里的 WebGL 上下文：创建了几个、丢掉（释放）了几个；另外数 requestAnimationFrame 回调的次数。在每个页面最先执行 */
function countWebGl() {
  window.__raf = 0
  const raf = window.requestAnimationFrame.bind(window)
  window.requestAnimationFrame = (cb) => raf((t) => {
    window.__raf++
    cb(t)
  })
  const seen = new WeakSet()
  window.__gl = { created: 0, lost: 0 }
  const original = HTMLCanvasElement.prototype.getContext
  HTMLCanvasElement.prototype.getContext = function (type, ...rest) {
    const ctx = original.call(this, type, ...rest)
    if (ctx && /webgl/.test(String(type)) && !seen.has(ctx)) {
      seen.add(ctx)
      window.__gl.created++
      this.addEventListener('webglcontextlost', () => window.__gl.lost++, { once: true })
    }
    return ctx
  }
}

/** 个人档案：3D 人体、当前体征、四个图表面板（有图或"暂无数据"）、查看详情、录入年龄框（只打开不保存）、预警记录；
 *  最后反复进出个人档案，看 WebGL 上下文、JS 堆、DOM 节点和事件监听有没有越来越多。
 *  注意：这里等元素一律用 locator().waitFor()，不用 waitForSelector——后者返回的元素句柄会让浏览器留着整页，测出假的泄漏 */
async function personInteract(page, label) {
  const ready = await page.locator('.body-3d[data-state="ready"]').first().waitFor({ timeout: 20000 }).then(() => true, () => false)
  if (ready) pass(`${label}：3D 人体加载完成`)
  else fail(`${label}：3D 人体没有加载出来（${await page.locator('.body-3d').getAttribute('data-state').catch(() => '无')}）`)
  if (await threeLoaded(page)) pass(`${label}：进入个人档案才下载 three.js 和人体模型`)
  else fail(`${label}：没有看到 three.js 的下载记录`)

  const card = new URL(page.url()).hash.split('/').pop()
  const d = await apiGet(page, `/archive/persons/${card}`)
  const age = (await page.locator('[data-testid="age"]').textContent())?.trim()
  if (age === (d.age === null ? '未录入' : String(d.age))) pass(`${label}：年龄显示"${age}"`)
  else fail(`${label}：年龄显示"${age}"，接口 ${d.age}`)
  const cells = await page.locator('.vitals__cell').count()
  if (cells === 6) pass(`${label}：当前体征 6 格`)
  else fail(`${label}：当前体征 ${cells} 格`)
  for (const key of ['HEART_RATE', 'SPO2', 'TEMPERATURE', 'BLOOD_PRESSURE']) {
    const text = await page.locator(`.vitals__cell[data-key="${key}"]`).textContent()
    const want = d.vitals[key] === null ? '暂无数据' : '采集'
    if (text?.includes(want)) continue
    fail(`${label}：${key} 格子应含"${want}"：${text}`)
  }

  await page.waitForTimeout(800)
  const panels = page.locator('.chart-panel')
  const n = await panels.count()
  const states = []
  for (let i = 0; i < n; i++) {
    const p = panels.nth(i)
    const title = (await p.locator('.panel__title').textContent())?.trim()
    const charts = await p.locator('[_echarts_instance_]').count()
    const empty = await p.getByText('暂无数据').count()
    states.push(`${title}=${charts ? '图表' : empty ? '暂无数据' : '？'}`)
    if (!charts && !empty) fail(`${label}：${title} 既没有图表也没有"暂无数据"`)
  }
  if (n === 4) pass(`${label}：4 个图表面板（${states.join('，')}）`)
  else fail(`${label}：图表面板 ${n} 个`)

  await page.locator('.chart-panel', { hasText: '今日心率' }).getByRole('button', { name: '查看详情' }).click()
  const zoom = page.locator('.screen-modal__box', { hasText: '查看详情 · 心率' })
  if (await zoom.waitFor({ timeout: 3000 }).then(() => true, () => false)) pass(`${label}：心率"查看详情"弹出放大图`)
  else fail(`${label}：心率"查看详情"没有弹出`)
  for (const m of ['血压', '步数']) {
    await page.locator('.zoom__tab', { hasText: m }).click()
    const ok = await page.locator('.screen-modal__box', { hasText: `查看详情 · ${m}` }).waitFor({ timeout: 3000 })
      .then(() => page.waitForFunction(() => !document.querySelector('.zoom__msg'), null, { timeout: 5000 }))
      .then(() => true, () => false)
    if (ok) pass(`${label}：放大图换成${m}`)
    else fail(`${label}：放大图没有换成${m}`)
  }
  await page.keyboard.press('Escape')

  await page.locator('.info__edit').click()
  const ageBox = page.locator('.screen-modal__box', { hasText: '录入年龄' })
  if (await ageBox.waitFor({ timeout: 3000 }).then(() => true, () => false)) pass(`${label}：打开"录入年龄"框（不保存）`)
  else fail(`${label}：没有打开"录入年龄"框`)
  await ageBox.getByRole('button', { name: '取消' }).click()

  await page.getByRole('tab', { name: '预警记录' }).click()
  const records = await page.locator('.records').waitFor({ timeout: 5000 }).then(() => true, () => false)
  const alerts = await apiGet(page, `/archive/persons/${card}/alerts?page=1&size=10`)
  const rows = await page.locator('.records .dark-table__row').count()
  if (records && rows === alerts.list.length) pass(`${label}：预警记录 ${rows} 行（共 ${alerts.total} 条）`)
  else fail(`${label}：预警记录 ${rows} 行，接口第 1 页 ${alerts.list.length} 条`)
  await page.getByRole('tab', { name: '数据汇总' }).click()

  // ---- 反复进出个人档案：3D 人体要释放干净 ----
  const cdp = await page.context().newCDPSession(page)
  await cdp.send('Performance.enable')
  const measure = async () => {
    await cdp.send('HeapProfiler.collectGarbage')
    const { metrics } = await cdp.send('Performance.getMetrics')
    const m = Object.fromEntries(metrics.map((x) => [x.name, x.value]))
    const gl = await page.evaluate(() => window.__gl)
    return { heapMb: m.JSHeapUsedSize / 1048576, nodes: m.Nodes, listeners: m.JSEventListeners, gl,
      canvases: await page.locator('.body-3d canvas').count() }
  }
  const samples = []
  for (let round = 1; round <= BODY_ROUNDS; round++) {
    await page.locator('.nav-item', { hasText: '健康档案' }).click()
    await page.locator('.person-card').first().waitFor({ timeout: 5000 })
    await page.waitForTimeout(300)
    samples.push(await measure())
    await page.locator('.person-card__open').first().click()
    await page.locator('.body-3d[data-state="ready"]').first().waitFor({ timeout: 20000 })
    await page.waitForTimeout(500)
  }
  await page.locator('.nav-item', { hasText: '健康档案' }).click()
  await page.locator('.person-card').first().waitFor({ timeout: 5000 })
  await page.waitForTimeout(300)
  samples.push(await measure())
  const last = samples.at(-1)
  const base = samples[1] ?? samples[0]
  console.log(`    进出 ${BODY_ROUNDS} 次，每次回到列表时：` + samples.map((s, i) =>
    `\n      第 ${i + 1} 次 堆 ${s.heapMb.toFixed(1)} MB、DOM 节点 ${s.nodes}、监听 ${s.listeners}、WebGL 创建 ${s.gl.created} 释放 ${s.gl.lost}、画布 ${s.canvases}`).join(''))
  const live = last.gl.created - last.gl.lost
  if (live === 0 && last.gl.created >= BODY_ROUNDS && last.canvases === 0) {
    pass(`${label}：进出 ${BODY_ROUNDS + 1} 次共创建 ${last.gl.created} 个 WebGL 上下文，离开后全部释放，没有残留画布`)
  } else fail(`${label}：离开后还剩 ${live} 个 WebGL 上下文、${last.canvases} 个画布`)
  const growth = last.heapMb - base.heapMb
  if (growth < 5) pass(`${label}：JS 堆从第 2 次的 ${base.heapMb.toFixed(1)} MB 到最后 ${last.heapMb.toFixed(1)} MB（增长 < 5 MB）`)
  else fail(`${label}：JS 堆一直在涨：${base.heapMb.toFixed(1)} → ${last.heapMb.toFixed(1)} MB`)
  if (last.nodes - base.nodes < 500 && last.listeners - base.listeners < 50) {
    pass(`${label}：DOM 节点 ${base.nodes} → ${last.nodes}、事件监听 ${base.listeners} → ${last.listeners}，没有越积越多`)
  } else fail(`${label}：DOM 节点 ${base.nodes} → ${last.nodes}、事件监听 ${base.listeners} → ${last.listeners}`)
  await cdp.detach()
}

async function main() {
  mkdirSync(SHOT_DIR, { recursive: true })
  const browser = await launch()
  try {
    // 1. 登录页：提示是否显示、各分辨率布局
    console.log('登录页')
    for (const vp of VIEWPORTS) {
      const ctx = await browser.newContext({ viewport: vp })
      const page = await ctx.newPage()
      const report = watch(page, `登录页 ${vp.width}×${vp.height}`)
      await page.goto(`${BASE}/#/login`, { waitUntil: 'networkidle' })
      await page.waitForSelector('.login-panel')
      const hint = await page.locator('[data-testid="login-hint"]').textContent({ timeout: 1000 }).catch(() => null)
      if (EXPECT_HINT && hint?.trim() === HINT_TEXT) pass(`显示提示"${HINT_TEXT}"`)
      else if (!EXPECT_HINT && hint === null) pass('不显示默认账号提示')
      else fail(`默认账号提示不符合预期（期望${EXPECT_HINT ? '显示' : '不显示'}，实际：${hint ?? '无'}）`)
      await checkLayout(page, `登录页 ${vp.width}×${vp.height}`, vp)
      await page.screenshot({ path: path.join(SHOT_DIR, `login-${vp.width}x${vp.height}.png`) })
      report()
      await ctx.close()
    }

    // 2. 未登录访问受保护页面，应被带到登录页
    console.log('未登录访问')
    {
      const ctx = await browser.newContext({ viewport: VIEWPORTS[0] })
      const page = await ctx.newPage()
      const report = watch(page, '未登录访问 #/portal')
      await page.goto(`${BASE}/#/portal`, { waitUntil: 'networkidle' })
      await page.waitForSelector('.login-panel')
      const hash = new URL(page.url()).hash
      if (hash.startsWith('#/login') && hash.includes('redirect=')) pass(`跳到登录页（${decodeURIComponent(hash)}）`)
      else fail(`未登录没有跳到登录页，当前 ${hash}`)
      report()
      await ctx.close()
    }

    // 3. 在登录页输入账号密码登录，进入首页
    console.log('界面登录')
    const ctx = await browser.newContext({ viewport: VIEWPORTS[0], acceptDownloads: true })
    await ctx.addInitScript(countWebGl)
    {
      const page = await ctx.newPage()
      const report = watch(page, '界面登录')
      await page.goto(`${BASE}/#/login`, { waitUntil: 'networkidle' })
      await page.fill('input[name="username"]', USER)
      await page.fill('input[name="password"]', PASSWORD)
      await Promise.all([
        page.waitForURL(/#\/portal/, { timeout: 10000 }).catch(() => null),
        page.click('button.login-submit')
      ])
      if (new URL(page.url()).hash.startsWith('#/portal')) pass(`用 ${USER} 登录后进入 #/portal`)
      else fail(`登录后没有进入首页，当前 ${page.url()}，提示：${await page.locator('.login-error').textContent().catch(() => '无')}`)
      firstCard = (await apiGet(page, '/archive/persons?page=1&size=1'))?.list?.[0]?.cardCode ?? null
      report()
      await page.close()
    }

    // 4. 逐页打开（带登录状态），两种分辨率
    for (const p of PAGES.filter((x) => DEV_PAGES || !x.dev)) {
      const hashOf = typeof p.hash === 'function' ? p.hash() : p.hash
      if (!hashOf) {
        console.log(`页面 ${p.name}：跳过（健康档案里没有人，没有可打开的个人档案）`)
        continue
      }
      p.hash = hashOf
      console.log(`页面 ${p.hash}`)
      for (const vp of VIEWPORTS) {
        const page = await ctx.newPage()
        await page.setViewportSize(vp)
        const label = `${p.name} ${vp.width}×${vp.height}`
        const report = watch(page, label)
        await page.goto(`${BASE}/${p.hash}`, { waitUntil: 'networkidle' })
        const hash = new URL(page.url()).hash
        if (hash.startsWith(p.hash)) pass(`${label}：到达 ${p.hash}`)
        else fail(`${label}：没有到达 ${p.hash}，当前 ${hash}`)
        const el = await page.waitForSelector(p.selector, { timeout: 5000 }).catch(() => null)
        if (!el) fail(`${label}：找不到 ${p.selector}`)
        else if (p.text && !(await el.textContent())?.includes(p.text)) fail(`${label}：${p.selector} 里没有"${p.text}"`)
        // 元素句柄会让浏览器一直留着这个元素（连同整页），后面测内存前要放掉
        await el?.dispose()
        await checkLayout(page, label, vp)
        if (p.charts !== undefined) {
          // 图表初始化在数据回来之后，稍等一下再数
          await page.waitForTimeout(1500)
          const charts = await page.locator('[_echarts_instance_]').count()
          if (charts === p.charts) pass(`${label}：图表 ${charts} 个`)
          else fail(`${label}：图表应有 ${p.charts} 个，实际 ${charts} 个`)
        }
        await page.screenshot({ path: path.join(SHOT_DIR, `${p.name}-${vp.width}x${vp.height}.png`) })
        if (p.interact && vp === VIEWPORTS[0]) await p.interact(page, label)
        report()
        await page.close()
      }
    }

    // 5. 月度汇总在缩小的画布里（1366×768 时画布缩放约 0.71 倍）：页角卷起、拖动翻页的鼠标位置要按缩放换算
    console.log('月度汇总 1366×768')
    {
      const vp = VIEWPORTS[1]
      const label = `月度汇总 ${vp.width}×${vp.height}`
      const page = await ctx.newPage()
      await page.setViewportSize(vp)
      const report = watch(page, label)
      await page.goto(`${BASE}/#/dashboard`, { waitUntil: 'networkidle' })
      await page.getByRole('button', { name: '月度汇总', exact: true }).click()
      await page.locator('.book[data-state="ready"]').waitFor({ timeout: 10000 })
      await page.waitForFunction(() => !document.querySelector('.rp__state'), null, { timeout: 15000 }).catch(() => null)
      const b = await page.locator('.stf__block').boundingBox()
      await page.mouse.move(b.x + b.width - 4, b.y + b.height - 4)
      if (await page.locator('.book[data-flip-state="fold_corner"]').waitFor({ timeout: 3000 }).then(() => true, () => false)) {
        pass(`${label}：鼠标移到封面右下角，页角卷起`)
      } else fail(`${label}：页角没有卷起（${await page.locator('.book').getAttribute('data-flip-state')}）`)
      await page.mouse.down()
      for (let i = 1; i <= 12; i++) await page.mouse.move(b.x + b.width - 4 - i * 45, b.y + b.height - 20, { steps: 2 })
      await page.mouse.up()
      if (await waitSpread(page, 1)) pass(`${label}：从右下角往左拖，翻开封面`)
      else fail(`${label}：拖动没有翻页（${await page.locator('.book').getAttribute('data-page')}）`)
      await checkLayout(page, label, vp)
      await page.screenshot({ path: path.join(SHOT_DIR, `report-${vp.width}x${vp.height}.png`) })
      await page.keyboard.press('Escape')
      report()
      await page.close()
    }

    // 6. 退出登录
    console.log('退出登录')
    {
      const page = await ctx.newPage()
      const report = watch(page, '退出登录')
      await page.goto(`${BASE}/#/portal`, { waitUntil: 'networkidle' })
      await page.click('.account')
      await page.getByText('退出登录').click()
      await page.waitForURL(/#\/login/, { timeout: 5000 }).catch(() => null)
      const token = await page.evaluate(() => localStorage.getItem('hv2-token'))
      if (new URL(page.url()).hash.startsWith('#/login') && !token) pass('退出后回到登录页，本地令牌已清除')
      else fail(`退出登录异常：${page.url()}，令牌${token ? '仍在' : '已清除'}`)
      report()
      await page.close()
    }
    await ctx.close()
  } finally {
    await browser.close()
  }

  console.log(`\n截图：${path.relative(process.cwd(), SHOT_DIR) || SHOT_DIR}`)
  if (failures.length) {
    console.log(`失败 ${failures.length} 项`)
    process.exit(1)
  }
  console.log('全部通过')
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
