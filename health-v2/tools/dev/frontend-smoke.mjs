#!/usr/bin/env node
// 前端冒烟脚本：用无头浏览器逐页打开，检查路由是否到达、控制台报错、HTTP 错误、是否出现滚动条，并截图。
// 需要前端（9529）和后端（8081）已启动：tools/dev/start.sh
//
// 用法：node tools/dev/frontend-smoke.mjs [--expect-hint=true|false] [--no-dev-pages]
//   --expect-hint   登录页是否应显示默认账号提示（和后端 LOGIN_SHOW_DEFAULT_ACCOUNT 一致），默认 true
//   --no-dev-pages  跳过只在开发模式存在的页面（对生产构建跑时用）
// 环境变量：SMOKE_BASE_URL（默认 http://127.0.0.1:9529）、SMOKE_USER / SMOKE_PASSWORD（默认开发期公开账号 admin/admin）
// 截图保存在 runtime/dev/smoke/（不入库）。任何一项失败退出码为 1。
//
// 之后每个阶段新增页面时，往 PAGES 里加一行。

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
const PAGES = [
  { name: 'portal', hash: '#/portal', selector: '.header__title', text: '职工健康管理系统' },
  { name: 'dev-components', hash: '#/dev/components', selector: '.preview', dev: true }
]

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
    const ctx = await browser.newContext({ viewport: VIEWPORTS[0] })
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
      report()
      await page.close()
    }

    // 4. 逐页打开（带登录状态），两种分辨率
    for (const p of PAGES.filter((x) => DEV_PAGES || !x.dev)) {
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
        await checkLayout(page, label, vp)
        await page.screenshot({ path: path.join(SHOT_DIR, `${p.name}-${vp.width}x${vp.height}.png`) })
        report()
        await page.close()
      }
    }

    // 5. 退出登录
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
