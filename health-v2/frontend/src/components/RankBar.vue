<template>
  <div :class="['rank-bar', { 'rank-bar--first': rank === 1 }]">
    <span class="rank-bar__no">
      <span class="rank-bar__diamond" aria-hidden="true"></span>
      <span class="num">No. {{ String(rank).padStart(2, '0') }}</span>
    </span>
    <span class="rank-bar__name" :title="title">{{ name }}</span>
    <span class="rank-bar__track" aria-hidden="true">
      <span class="rank-bar__fill" :style="{ width: `${pct}%` }"></span>
    </span>
    <span class="rank-bar__value"><span class="num">{{ value }}</span> {{ unit }}</span>
  </div>
</template>

<script setup lang="ts">
// 排行条（docs/09 第三节"底右：实时运动量"）：菱形 + "No. 01"，姓名，进度条，末尾"35 步"。
// 第一名金黄色菱形、亮青绿进度条；其余青蓝色菱形。进度条长度 pct 由后端算好。
defineProps<{
  rank: number
  name: string
  title?: string
  pct: number
  value: number
  unit: string
}>()
</script>

<style scoped>
.rank-bar {
  display: grid;
  grid-template-columns: 112px 82px 1fr auto;
  align-items: center;
  gap: 10px;
  height: 100%;
  font-size: 17px;
}

.rank-bar__no {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 36px;
  padding-left: 10px;
  background: var(--accent-bg);
}

.rank-bar__diamond {
  width: 12px;
  height: 12px;
  border: 2px solid var(--info);
  transform: rotate(45deg);
}

.rank-bar--first .rank-bar__diamond {
  border-color: var(--gold-bright);
  background: var(--gold-bright);
}

.rank-bar__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-bar__track {
  height: 10px;
  border-radius: 5px;
  background: var(--track-bg);
  overflow: hidden;
}

.rank-bar__fill {
  display: block;
  height: 100%;
  border-radius: 5px;
  background: var(--info);
}

.rank-bar--first .rank-bar__fill {
  background: linear-gradient(90deg, var(--accent-soft), var(--accent));
  box-shadow: 0 0 8px var(--accent-glow);
}

.rank-bar__value {
  min-width: 70px;
  text-align: right;
  color: var(--text-secondary);
}

.rank-bar__value .num {
  color: var(--text-primary);
}
</style>
