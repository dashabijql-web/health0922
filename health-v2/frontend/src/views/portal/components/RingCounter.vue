<template>
  <div class="ring-counter">
    <span class="ring-counter__ring">
      <CounterRoll class="ring-counter__value" :value="value" />
    </span>
    <span class="ring-counter__label">{{ label }}</span>
  </div>
</template>

<script setup lang="ts">
// 入口页底部的发光圆环 + 白色大数字 + 下方深色圆角标签（docs/09 第五节）。
import CounterRoll from '@/components/CounterRoll.vue'

defineProps<{ label: string; value: number | null }>()
</script>

<style scoped>
.ring-counter {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 180px;
  margin-left: -90px;
}

.ring-counter__ring {
  display: grid;
  place-items: center;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  border: 4px solid var(--accent-soft);
  background: radial-gradient(circle, var(--portal-ring-inner), var(--bg-page) 70%);
  box-shadow: 0 0 16px var(--accent-glow), inset 0 0 14px var(--accent-glow);
}

.ring-counter__value {
  font-size: 32px;
  color: var(--text-bright);
}

.ring-counter__label {
  margin-top: 12px;
  padding: 4px 16px;
  border-radius: 6px;
  background: var(--portal-label-bg);
  font-size: 16px;
  font-weight: 700;
  color: var(--text-bright);
  white-space: nowrap;
}
</style>
