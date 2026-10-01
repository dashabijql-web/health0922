<template>
  <button type="button" :class="['stat-tile', { 'stat-tile--hot': (value ?? 0) > 0 }]" @click="emit('select')">
    <span class="stat-tile__icon"><ScreenIcon :name="icon" /></span>
    <span class="stat-tile__text">
      <span class="stat-tile__name">{{ name }}</span>
      <CounterRoll class="stat-tile__value" :value="value" />
    </span>
  </button>
</template>

<script setup lang="ts">
// 图标 + 名称 + 数字的小方块（六类告警，docs/09 第三节"右上：体征信息展示"）。数字大于 0 时用告警色，并保留图标和文字，
// 色弱用户也能分辨（docs/07）。点开看是哪些人。
import CounterRoll from './CounterRoll.vue'
import ScreenIcon from './ScreenIcon.vue'
import type { IconName } from './icon-names'

defineProps<{ name: string; icon: IconName; value: number | null }>()
const emit = defineEmits<{ select: [] }>()
</script>

<style scoped>
.stat-tile {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  height: 100%;
  padding: 0 10px;
  border: 1px solid var(--border-glow);
  background: linear-gradient(180deg, var(--tile-bg-top), var(--tile-bg-bottom));
  box-shadow: inset 0 0 12px var(--accent-bg);
  color: var(--text-primary);
  cursor: pointer;
  text-align: left;
}

.stat-tile:hover,
.stat-tile:focus-visible {
  border-color: var(--accent);
  outline: none;
}

.stat-tile__icon {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  color: var(--accent);
  filter: drop-shadow(0 0 6px var(--accent-glow));
}

.stat-tile__text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.stat-tile__name {
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
}

.stat-tile__value {
  font-size: 20px;
  color: var(--accent);
}

.stat-tile--hot .stat-tile__value,
.stat-tile--hot .stat-tile__icon {
  color: var(--danger);
}
</style>
