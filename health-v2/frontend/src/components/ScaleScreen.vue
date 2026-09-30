<template>
  <div ref="viewport" class="scale-viewport">
    <div class="scale-canvas" :style="canvasStyle">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
// 1920×1080 设计画布按窗口等比缩放（CSS transform: scale），比例不一致时居中留黑边（docs/07 第一部分"四、布局"）
import { computed, onBeforeUnmount, onMounted, provide, ref } from 'vue'
import { SCREEN_SCALE_KEY } from './screen-scale'

const DESIGN_WIDTH = 1920
const DESIGN_HEIGHT = 1080

const viewport = ref<HTMLElement | null>(null)
const width = ref(window.innerWidth)
const height = ref(window.innerHeight)

const scale = computed(() => Math.min(width.value / DESIGN_WIDTH, height.value / DESIGN_HEIGHT))
provide(SCREEN_SCALE_KEY, scale)

const canvasStyle = computed(() => {
  const s = scale.value
  return {
    width: `${DESIGN_WIDTH}px`,
    height: `${DESIGN_HEIGHT}px`,
    left: `${(width.value - DESIGN_WIDTH * s) / 2}px`,
    top: `${(height.value - DESIGN_HEIGHT * s) / 2}px`,
    transform: `scale(${s})`
  }
})

let observer: ResizeObserver | null = null

function measure() {
  const el = viewport.value
  if (!el) return
  width.value = el.clientWidth
  height.value = el.clientHeight
}

onMounted(() => {
  measure()
  observer = new ResizeObserver(measure)
  if (viewport.value) observer.observe(viewport.value)
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<style scoped>
.scale-viewport {
  position: fixed;
  inset: 0;
  overflow: hidden;
  background: var(--bg-letterbox);
}

.scale-canvas {
  position: absolute;
  transform-origin: 0 0;
  overflow: hidden;
  background: var(--bg-page);
}
</style>
