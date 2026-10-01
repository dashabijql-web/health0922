<template>
  <button type="button" :class="['portal-btn', `portal-btn--${side}`]">
    <svg class="portal-btn__shape" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
      <rect x="0" y="0" width="100" height="100" class="portal-btn__fill" />
      <!-- 靠圆环一侧向内的箭头凹口 -->
      <polyline :points="side === 'left' ? '99,8 96,8 96,44 99,50 96,56 96,92 99,92' : '1,8 4,8 4,44 1,50 4,56 4,92 1,92'"
                class="portal-btn__notch" vector-effect="non-scaling-stroke" />
    </svg>
    <span class="portal-btn__icon"><ScreenIcon name="screen" /></span>
    <span class="portal-btn__text"><slot /></span>
  </button>
</template>

<script setup lang="ts">
// 入口页"数据管理""数据展示"（截图 16，docs/09 第五节）：亮青色条形按钮，圆形图标在外侧，中间大字，靠圆环一侧有向内的箭头凹口。
import ScreenIcon from '@/components/ScreenIcon.vue'

defineProps<{ side: 'left' | 'right' }>()
</script>

<style scoped>
.portal-btn {
  position: absolute;
  display: flex;
  align-items: center;
  padding: 0;
  border: 0;
  background: none;
  cursor: pointer;
}

.portal-btn--right {
  flex-direction: row-reverse;
}

.portal-btn__shape {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.portal-btn__fill {
  fill: var(--portal-btn-fill);
}

.portal-btn__notch {
  fill: none;
  stroke: var(--accent);
  stroke-width: 6;
  filter: drop-shadow(0 0 6px var(--accent));
}

.portal-btn:hover .portal-btn__fill {
  fill: var(--portal-btn-fill-hover);
}

.portal-btn__icon {
  position: relative;
  width: 92px;
  height: 92px;
  margin: 0 46px;
  padding: 20px;
  border-radius: 50%;
  border: 2px solid var(--accent);
  background: radial-gradient(circle, var(--accent-bg), transparent 70%);
  color: var(--text-bright);
  box-shadow: 0 0 18px var(--accent-glow);
}

.portal-btn__text {
  position: relative;
  flex: 1;
  text-align: center;
  font-size: 38px;
  font-weight: 700;
  letter-spacing: 10px;
  color: var(--accent);
  text-shadow: 0 0 10px var(--accent-glow);
  padding-right: 40px;
}

.portal-btn--right .portal-btn__text {
  padding: 0 0 0 40px;
}
</style>
