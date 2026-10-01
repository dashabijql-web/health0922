<template>
  <section :class="['metric', `metric--${side}`]" :aria-label="title">
    <svg class="metric__shape" :viewBox="`0 0 100 100`" preserveAspectRatio="none" aria-hidden="true">
      <defs>
        <linearGradient :id="gradId" :x1="side === 'left' ? 0 : 1" y1="0" :x2="side === 'left' ? 1 : 0" y2="0">
          <stop offset="0" stop-color="var(--portal-panel-far)" />
          <stop offset="1" stop-color="var(--portal-panel-near)" />
        </linearGradient>
      </defs>
      <polygon :points="points" :fill="`url(#${gradId})`" />
      <polyline :points="edge" class="metric__edge" vector-effect="non-scaling-stroke" />
    </svg>
    <h2 class="metric__title">{{ title }}</h2>
    <div class="metric__items">
      <div v-for="item in items" :key="item.label" :class="['item', { 'item--min': item.min }]">
        <span class="item__icon"><ScreenIcon :name="icon" /></span>
        <span class="item__text">
          <span class="item__label">{{ item.label }}</span>
          <CounterRoll class="item__value" :value="item.value" />
        </span>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
// 入口页的体征面板（截图 16，docs/09 第五节）：向圆环一侧内收的梯形，标题居中在上，下面横排
// "最大值 / 最小值 / 平均值"三项，最小值的图标和数字是黄色。stat 为 null（今天还没有数据）时三项都写"暂无数据"。
import { computed, useId } from 'vue'
import type { Stat } from '@/api/dashboard'
import CounterRoll from '@/components/CounterRoll.vue'
import ScreenIcon from '@/components/ScreenIcon.vue'
import type { IconName } from '@/components/icon-names'

const props = defineProps<{
  title: string
  icon: IconName
  stat: Stat | null
  side: 'left' | 'right'
  /** 梯形四个角的横坐标（0–100），依次是左上、右上、右下、左下 */
  corners: [number, number, number, number]
}>()

const gradId = `metric-grad-${useId()}`
const points = computed(() => {
  const [tl, tr, br, bl] = props.corners
  return `${tl},0 ${tr},0 ${br},100 ${bl},100`
})
/** 只描靠圆环那一侧的斜边和上下边，外侧不描 */
const edge = computed(() => {
  const [tl, tr, br, bl] = props.corners
  return props.side === 'left' ? `${tl},0 ${tr},0 ${br},100 ${bl},100` : `${tr},0 ${tl},0 ${bl},100 ${br},100`
})
const items = computed(() => [
  { label: '最大值', value: props.stat?.max ?? null, min: false },
  { label: '最小值', value: props.stat?.min ?? null, min: true },
  { label: '平均值', value: props.stat?.avg ?? null, min: false }
])
</script>

<style scoped>
.metric {
  position: absolute;
}

.metric__shape {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: visible;
}

.metric__edge {
  fill: none;
  stroke: var(--border-glow);
  stroke-width: 1.5;
}

.metric__title {
  position: absolute;
  top: var(--title-top, 36px);
  left: var(--title-x, 0);
  width: 180px;
  margin: 0 0 0 -90px;
  text-align: center;
  font-size: 22px;
  font-weight: 700;
  color: var(--text-bright);
}

.metric__items {
  position: absolute;
  top: var(--items-top, 118px);
  left: var(--items-left, 50px);
  display: flex;
  gap: 32px;
}

.item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 120px;
}

.item__icon {
  width: 52px;
  height: 52px;
  padding: 11px;
  flex-shrink: 0;
  border-radius: 50%;
  border: 2px solid var(--portal-icon-ring);
  color: var(--text-bright);
  box-shadow: 0 0 10px var(--accent-glow), inset 0 0 8px var(--accent-bg);
}

.item--min .item__icon {
  border-color: var(--min-yellow);
  color: var(--min-yellow);
  box-shadow: 0 0 10px var(--min-yellow-glow);
}

.item__text {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.item__label {
  font-size: 15px;
  color: var(--text-bright);
}

.item__value {
  font-size: 23px;
  color: var(--text-bright);
}

.item--min .item__value {
  color: var(--min-yellow);
}
</style>
