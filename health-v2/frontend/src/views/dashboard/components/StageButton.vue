<template>
  <button type="button" class="stage-button">
    <span class="stage-button__icon"><ScreenIcon :name="icon" /></span>
    <span class="stage-button__text"><slot /></span>
  </button>
</template>

<script setup lang="ts">
// 主区域左侧的按钮（佩戴情况、月度汇总；地图模式是重点监护、今日关注）：斜切的深青色块，左边发光图标，斜体粗体字（docs/09 第三节）。
import ScreenIcon from '@/components/ScreenIcon.vue'
import type { IconName } from '@/components/icon-names'

defineProps<{ icon: IconName }>()
</script>

<style scoped>
.stage-button {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
  height: 100%;
  padding: 0 0 0 26px;
  border: 0;
  background: none;
  color: var(--text-primary);
  cursor: pointer;
}

/* 斜切的底：左下角切掉，底边一道亮线 */
.stage-button::before {
  content: '';
  position: absolute;
  inset: 0;
  clip-path: polygon(0 0, 100% 0, 92% 100%, 4% 100%, 0 78%);
  background: linear-gradient(90deg, var(--stage-btn-left), var(--stage-btn-right));
  border-bottom: 3px solid var(--accent-soft);
}

.stage-button::after {
  content: '';
  position: absolute;
  left: 4%;
  right: 8%;
  bottom: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--accent), transparent);
}

.stage-button:hover::before {
  filter: brightness(1.25);
}

.stage-button__icon {
  position: relative;
  width: 30px;
  height: 30px;
  color: var(--text-primary);
  filter: drop-shadow(0 0 6px var(--accent));
}

.stage-button__text {
  position: relative;
  font-size: 24px;
  font-weight: 700;
  font-style: italic;
  letter-spacing: 2px;
}
</style>
