<template>
  <!-- 蓝色等距插画（截图 6 左页的同类风格，我们自己画的）：中间一座数据塔，四周的平台上是柱状图、
       显示折线的屏幕、饼图、一摞数据盘和一块手表，虚线把它们连到塔上 -->
  <svg class="iso" viewBox="-300 -250 600 470" aria-hidden="true">
    <path v-for="(d, i) in links" :key="`k${i}`" :d="d" class="iso__link" />
    <g v-for="(b, i) in boxes" :key="`b${i}`">
      <polygon :points="b.left" :class="`iso__left iso--${b.tone}`" />
      <polygon :points="b.right" :class="`iso__right iso--${b.tone}`" />
      <polygon :points="b.top" :class="`iso__top iso--${b.tone}`" />
    </g>
    <!-- 饼图 -->
    <g :transform="`translate(${pie.x} ${pie.y})`">
      <ellipse rx="34" ry="19.6" cy="6" class="iso__shade" />
      <ellipse rx="34" ry="19.6" class="iso__pie" />
      <path d="M0 0L34 0A34 19.6 0 0 1 -12 18.3z" class="iso__pie-slice" />
      <path d="M0 0L-12 18.3A34 19.6 0 0 1 -32 -6.6z" class="iso__pie-slice2" />
    </g>
    <!-- 屏幕上的折线 -->
    <polyline :points="screenLine" class="iso__chart-line" />
    <!-- 手表表盘 -->
    <g :transform="`translate(${watch.x} ${watch.y})`">
      <ellipse rx="13" ry="7.5" class="iso__watch-face" />
      <path d="M-7 0h4l2-4 3 7 2-3h3" class="iso__watch-beat" />
    </g>
    <!-- 塔顶的光和上升的小方块 -->
    <polygon :points="glow" class="iso__glow" />
    <rect v-for="(s, i) in sparks" :key="`s${i}`" :x="s[0]" :y="s[1]" width="7" height="7" rx="1" class="iso__spark" />
  </svg>
</template>

<script setup lang="ts">
// 等距投影：x' = (x − y)·cos30°，y' = (x + y)·sin30° − z。纯装饰，不带数据。
const C = Math.cos(Math.PI / 6)
const S = 0.5

type Tone = 'base' | 'tower' | 'accent' | 'light'

function p(x: number, y: number, z: number): string {
  return `${((x - y) * C).toFixed(1)},${((x + y) * S - z).toFixed(1)}`
}

/** 一个长方体在 (x, y, z) 处，宽 w（x 方向）、深 d（y 方向）、高 h，返回三个看得见的面 */
function box(x: number, y: number, z: number, w: number, d: number, h: number, tone: Tone) {
  const t = z + h
  return {
    tone,
    top: [p(x, y, t), p(x + w, y, t), p(x + w, y + d, t), p(x, y + d, t)].join(' '),
    left: [p(x, y + d, z), p(x + w, y + d, z), p(x + w, y + d, t), p(x, y + d, t)].join(' '),
    right: [p(x + w, y, z), p(x + w, y + d, z), p(x + w, y + d, t), p(x + w, y, t)].join(' ')
  }
}

function at(x: number, y: number, z = 0) {
  const [sx, sy] = p(x, y, z).split(',').map(Number)
  return { x: sx, y: sy }
}

const boxes = [
  // 平台：左后、右后、左前、右前、正前
  box(-210, -60, 0, 110, 90, 10, 'base'),
  box(-40, -230, 0, 110, 90, 10, 'base'),
  box(-60, 120, 0, 100, 100, 10, 'base'),
  box(120, -40, 0, 110, 100, 10, 'base'),
  box(120, 130, 0, 90, 80, 10, 'base'),
  // 左后平台：柱状图
  box(-195, -45, 10, 18, 18, 40, 'accent'),
  box(-170, -45, 10, 18, 18, 66, 'tower'),
  box(-145, -45, 10, 18, 18, 52, 'accent'),
  box(-120, -45, 10, 18, 18, 84, 'tower'),
  // 右后平台：一摞数据盘
  box(-10, -200, 10, 44, 44, 8, 'light'),
  box(-10, -200, 22, 44, 44, 8, 'light'),
  box(-10, -200, 34, 44, 44, 8, 'light'),
  box(25, -170, 10, 30, 30, 8, 'accent'),
  // 中间的数据塔：三层（先画塔后面的东西，再画塔，再画塔前面的）
  box(-35, -35, 0, 80, 80, 26, 'tower'),
  box(-28, -28, 34, 66, 66, 50, 'light'),
  box(-35, -35, 92, 80, 80, 26, 'tower'),
  // 右前平台：屏幕
  box(150, -20, 10, 8, 70, 70, 'tower'),
  // 正前平台：手表的表带
  box(150, 160, 10, 26, 40, 6, 'tower')
]

const pie = at(-10, 170, 16)
const watch = at(163, 180, 18)
const screenLine = [[150, -12, 30], [150, 4, 56], [150, 18, 44], [150, 32, 66], [150, 46, 52]]
  .map(([x, y, z]) => p(x, y, z)).join(' ')
const glow = [p(-35, -35, 118), p(45, -35, 118), p(45, 45, 118), p(-35, 45, 118)].join(' ')
const sparks = [[-6, -232], [14, -262], [-24, -276], [4, -300]].map(([x, y]) => [x, y])

// 平台到塔的虚线（在地面上走直角）
const tower = at(5, 5)
const links = [at(-155, -15), at(15, -185), at(-10, 170), at(175, 10), at(165, 170)]
  .map((q) => `M${q.x} ${q.y}L${q.x} ${tower.y}L${tower.x} ${tower.y}`)
</script>

<style scoped>
.iso {
  width: 100%;
  height: 100%;
}

.iso__link {
  fill: none;
  stroke: var(--report-art-line);
  stroke-width: 1.6;
  stroke-dasharray: 5 5;
}

.iso__top.iso--base { fill: var(--report-art-light); }
.iso__left.iso--base { fill: var(--report-art-mid); }
.iso__right.iso--base { fill: var(--report-art-blue); }

.iso__top.iso--tower { fill: var(--report-art-light); }
.iso__left.iso--tower { fill: var(--report-art-blue); }
.iso__right.iso--tower { fill: var(--report-art-deep); }

.iso__top.iso--light { fill: var(--report-white); }
.iso__left.iso--light { fill: var(--report-art-light); }
.iso__right.iso--light { fill: var(--report-art-mid); }

.iso__top.iso--accent { fill: var(--report-art-accent); }
.iso__left.iso--accent { fill: var(--report-art-accent); opacity: 0.85; }
.iso__right.iso--accent { fill: var(--report-art-accent); opacity: 0.7; }

.iso__shade { fill: var(--report-art-deep); }
.iso__pie { fill: var(--report-art-light); }
.iso__pie-slice { fill: var(--report-art-blue); }
.iso__pie-slice2 { fill: var(--report-art-accent); }

.iso__chart-line {
  fill: none;
  stroke: var(--report-tagline-arrow);
  stroke-width: 2.5;
  stroke-linejoin: round;
}

.iso__watch-face { fill: var(--report-art-deep); stroke: var(--report-art-light); stroke-width: 2; }
.iso__watch-beat { fill: none; stroke: var(--report-tagline-arrow); stroke-width: 1.6; }

.iso__glow { fill: var(--report-tagline-arrow); opacity: 0.55; }
.iso__spark { fill: var(--report-art-mid); opacity: 0.8; }
</style>
