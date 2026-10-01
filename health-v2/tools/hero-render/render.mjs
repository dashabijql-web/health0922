#!/usr/bin/env node
// 渲染首页中央动画（docs/07 第一部分"七、首页中央动画"）：起一个本地静态服务器放 scene.html，
// 用无头浏览器逐帧调用 heroFrame(t) 截图，再用 ffmpeg 合成 H.264 mp4（8 秒无缝循环）和静态海报图。
//
// 用法：
//   node tools/hero-render/render.mjs --scene=dashboard            # 动态数据页主区域 → frontend/src/assets/hero/dashboard.mp4
//   node tools/hero-render/render.mjs --scene=portal               # 入口页圆环 → frontend/src/assets/hero/portal.mp4
//   node tools/hero-render/render.mjs --scene=preview --still=out.png --model=/models/xxx.glb   # 只截一张图看效果
// 参数：--w --h 画面尺寸（默认按场景）；--fps 默认 30；--crf 画质（越小越清楚、文件越大，默认 24）；
//       --model 人体模型地址（默认 /models/human_body_wireframe.glb，即 frontend/public/models/ 下，从老项目复制）；
//       --models-dir 模型目录（默认 frontend/public/models）。
// 需要：本机 ffmpeg（libx264）、frontend/node_modules 里的 three 和 playwright-core、本机 Chrome。

import { createServer } from 'node:http'
import { createRequire } from 'node:module'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const HV2 = path.resolve(HERE, '../..')
const FRONTEND = path.join(HV2, 'frontend')
const require = createRequire(path.join(FRONTEND, 'package.json'))
const { chromium } = require('playwright-core')

const args = new Map(process.argv.slice(2).map((a) => {
  const [k, v] = a.replace(/^--/, '').split('=')
  return [k, v ?? 'true']
}))
const SCENE = args.get('scene') ?? 'dashboard'
// 动态数据页主区域 1352×566（放大 1.25 倍更清楚）；入口页圆环里 720×720
const DEFAULT_SIZE = { dashboard: [1690, 708], portal: [720, 720], preview: [800, 800] }
const [W, H] = [Number(args.get('w') ?? DEFAULT_SIZE[SCENE][0]), Number(args.get('h') ?? DEFAULT_SIZE[SCENE][1])]
const FPS = Number(args.get('fps') ?? 30)
const CRF = String(args.get('crf') ?? 24)
const MODEL = args.get('model') ?? '/models/human_body_wireframe.glb'
const MODELS_DIR = path.resolve(args.get('models-dir') ?? path.join(FRONTEND, 'public/models'))
const OUT_DIR = path.join(FRONTEND, 'src/assets/hero')
const STILL = args.get('still')

const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.glb': 'model/gltf-binary', '.json': 'application/json' }

/** 只服务三个目录：本工具、three、模型；路径里的 .. 一律拒绝 */
function resolveUrl(url) {
  const p = decodeURIComponent(new URL(url, 'http://x').pathname)
  if (p.includes('..')) return null
  if (p === '/') return path.join(HERE, 'scene.html')
  if (p === '/scene.js') return path.join(HERE, 'scene.js')
  if (p.startsWith('/three/')) return path.join(FRONTEND, 'node_modules/three', p.slice('/three/'.length))
  if (p.startsWith('/models/')) return path.join(MODELS_DIR, p.slice('/models/'.length))
  return null
}

function serve() {
  const server = createServer((req, res) => {
    const file = resolveUrl(req.url ?? '/')
    try {
      if (!file || !statSync(file).isFile()) throw new Error('not found')
      res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] ?? 'application/octet-stream' })
      res.end(readFileSync(file))
    } catch {
      res.writeHead(404)
      res.end()
    }
  })
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server)))
}

async function launch() {
  const opts = { headless: true, args: ['--use-angle=metal', '--enable-gpu-rasterization', '--ignore-gpu-blocklist'] }
  try {
    return await chromium.launch(opts)
  } catch (error) {
    if (!/Executable doesn't exist/.test(String(error))) throw error
    return chromium.launch({ ...opts, channel: 'chrome' })
  }
}

async function main() {
  const server = await serve()
  const port = server.address().port
  const browser = await launch()
  const tmp = mkdtempSync(path.join(tmpdir(), 'hero-render-'))
  try {
    const page = await browser.newPage({ viewport: { width: W, height: H } })
    page.on('console', (m) => m.type() === 'error' && console.error('[页面]', m.text()))
    page.on('pageerror', (e) => console.error('[页面异常]', e.message))
    await page.goto(`http://127.0.0.1:${port}/?scene=${SCENE}&w=${W}&h=${H}&model=${encodeURIComponent(MODEL)}`)
    await page.waitForFunction(() => window.heroReady !== undefined)
    await page.evaluate(() => window.heroReady)
    const loop = await page.evaluate(() => window.heroLoop)

    const grab = (t) => page.evaluate((tt) => {
      window.heroFrame(tt)
      return document.querySelector('canvas').toDataURL('image/png')
    }, t)
    const save = (file, dataUrl) => writeFileSync(file, Buffer.from(dataUrl.split(',')[1], 'base64'))

    if (STILL) {
      save(path.resolve(STILL), await grab(Number(args.get('t') ?? 2)))
      console.log(`已保存 ${STILL}`)
      return
    }

    const frames = Math.round(loop * FPS)
    console.log(`渲染 ${SCENE}：${W}×${H}，${frames} 帧（${loop} 秒 × ${FPS} 帧/秒）`)
    for (let i = 0; i < frames; i++) {
      save(path.join(tmp, `f${String(i).padStart(4, '0')}.png`), await grab(i / FPS))
      if (i % 30 === 0) process.stdout.write(`  第 ${i} 帧\n`)
    }

    mkdirSync(OUT_DIR, { recursive: true })
    const video = path.join(OUT_DIR, `${SCENE}.mp4`)
    const poster = path.join(OUT_DIR, `${SCENE}-poster.jpg`)
    execFileSync('ffmpeg', ['-v', 'error', '-y', '-framerate', String(FPS), '-i', path.join(tmp, 'f%04d.png'),
      // 把接近黑的底压成纯黑，页面上 screen 混合后不会留下一层灰雾
      '-vf', "curves=all='0/0 0.05/0 1/1'",
      '-c:v', 'libx264', '-preset', 'slow', '-crf', CRF, '-pix_fmt', 'yuv420p', '-movflags', '+faststart', '-an',
      video], { stdio: 'inherit' })
    execFileSync('ffmpeg', ['-v', 'error', '-y', '-i', path.join(tmp, 'f0000.png'),
      '-vf', "curves=all='0/0 0.05/0 1/1'", '-q:v', '3', poster],
      { stdio: 'inherit' })
    const mb = (f) => (statSync(f).size / 1024 / 1024).toFixed(2)
    console.log(`视频 ${path.relative(HV2, video)}（${mb(video)} MB），海报 ${path.relative(HV2, poster)}（${mb(poster)} MB）`)
  } finally {
    await browser.close()
    server.close()
    rmSync(tmp, { recursive: true, force: true })
  }
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
