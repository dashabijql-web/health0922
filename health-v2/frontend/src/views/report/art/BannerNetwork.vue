<template>
  <svg class="banner-net" :viewBox="`0 0 ${W} ${H}`" preserveAspectRatio="none" aria-hidden="true">
    <line v-for="(l, i) in net.lines" :key="`l${i}`" :x1="l[0]" :y1="l[1]" :x2="l[2]" :y2="l[3]" class="banner-net__line" />
    <circle v-for="(d, i) in net.dots" :key="`d${i}`" :cx="d[0]" :cy="d[1]" :r="d[2]" class="banner-net__dot" />
  </svg>
</template>

<script setup lang="ts">
// 页眉横幅上淡淡的网络连线纹理（截图 7–12）：固定种子的伪随机点，相近的点连线。同一个 seed 每次画得一样。
const props = defineProps<{ seed: number }>()

const W = 720
const H = 74

function build(seed: number) {
  let s = seed * 9301 + 49297
  const rnd = () => {
    s = (s * 9301 + 49297) % 233280
    return s / 233280
  }
  const pts: [number, number][] = []
  for (let i = 0; i < 26; i++) pts.push([rnd() * W, rnd() * H * 1.4 - H * 0.2])
  const lines: [number, number, number, number][] = []
  for (let i = 0; i < pts.length; i++) {
    for (let j = i + 1; j < pts.length; j++) {
      const dx = pts[i][0] - pts[j][0]
      const dy = pts[i][1] - pts[j][1]
      if (dx * dx + dy * dy < 120 * 120) lines.push([pts[i][0], pts[i][1], pts[j][0], pts[j][1]])
    }
  }
  const dots = pts.map(([x, y]) => [x, y, 1 + rnd() * 2] as [number, number, number])
  return { lines, dots }
}

const net = build(props.seed)
</script>

<style scoped>
.banner-net {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.banner-net__line {
  stroke: var(--report-banner-line);
  stroke-width: 0.8;
}

.banner-net__dot {
  fill: var(--report-banner-dot);
}
</style>
