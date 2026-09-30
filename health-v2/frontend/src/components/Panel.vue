<template>
  <section class="panel">
    <span class="corner corner--tl" aria-hidden="true"></span>
    <span class="corner corner--tr" aria-hidden="true"></span>
    <span class="corner corner--bl" aria-hidden="true"></span>
    <span class="corner corner--br" aria-hidden="true"></span>

    <header v-if="title" class="panel__header">
      <span class="panel__slashes" aria-hidden="true">///</span>
      <h2 class="panel__title">{{ title }}</h2>
      <div class="panel__extra"><slot name="extra" /></div>
    </header>

    <div class="panel__body">
      <div v-if="loading" class="panel__skeleton" aria-busy="true" aria-label="加载中">
        <span v-for="i in 3" :key="i" class="panel__skeleton-line"></span>
      </div>
      <slot v-else />
    </div>

    <p v-if="failedAt" class="panel__failed" role="status">刷新失败，显示的是 {{ failedAt }} 的数据</p>
  </section>
</template>

<script setup lang="ts">
// 带标题栏和四角装饰的面板（docs/07 第一部分"五、公共组件"，样式见 docs/09 第二节）。
// 面板四种状态（docs/05 第二节）：加载中用 loading 显示骨架屏；没有数据/未录入在插槽里放 EmptyState；
// 刷新失败时保留上次数据，并用 failedAt（上次成功的 hh:mm）在角落提示。
defineProps<{
  title?: string
  loading?: boolean
  failedAt?: string | null
}>()
</script>

<style scoped>
.panel {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--bg-panel);
  border: 1px solid var(--border-faint);
  backdrop-filter: blur(6px);
}

.corner {
  position: absolute;
  width: 14px;
  height: 14px;
  border: 2px solid var(--corner-glow);
  pointer-events: none;
}

.corner--tl { top: -1px; left: -1px; border-right: 0; border-bottom: 0; }
.corner--tr { top: -1px; right: -1px; border-left: 0; border-bottom: 0; }
.corner--bl { bottom: -1px; left: -1px; border-right: 0; border-top: 0; }
.corner--br { bottom: -1px; right: -1px; border-left: 0; border-top: 0; }

.panel__header {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 44px;
  padding: 0 16px;
  flex-shrink: 0;
  background: linear-gradient(90deg, var(--accent-bg), transparent 70%);
  border-bottom: 1px solid var(--border-faint);
}

.panel__slashes {
  color: var(--accent);
  font-weight: 700;
  font-style: italic;
  letter-spacing: -2px;
}

.panel__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
}

.panel__extra {
  margin-left: auto;
}

.panel__body {
  position: relative;
  flex: 1;
  min-height: 0;
  padding: 16px;
}

.panel__skeleton {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.panel__skeleton-line {
  height: 16px;
  border-radius: 3px;
  background: linear-gradient(90deg, var(--accent-bg), var(--border-faint), var(--accent-bg));
  background-size: 200% 100%;
  animation: skeleton 1.4s ease-in-out infinite;
}

.panel__skeleton-line:nth-child(2) { width: 80%; }
.panel__skeleton-line:nth-child(3) { width: 60%; }

@keyframes skeleton {
  from { background-position: 200% 0; }
  to { background-position: -200% 0; }
}

.panel__failed {
  position: absolute;
  right: 12px;
  bottom: 6px;
  margin: 0;
  font-size: 12px;
  color: var(--warn);
}

@media (prefers-reduced-motion: reduce) {
  .panel__skeleton-line { animation: none; }
}
</style>
