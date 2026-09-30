<template>
  <div :class="['empty-state', `empty-state--${kind}`, { 'empty-state--inline': inline }]" role="status">
    <svg v-if="!inline" class="empty-state__icon" viewBox="0 0 48 48" aria-hidden="true">
      <template v-if="kind === 'error'">
        <circle cx="24" cy="24" r="18" fill="none" stroke="currentColor" stroke-width="2" />
        <path d="M24 14v12M24 31v3" stroke="currentColor" stroke-width="3" stroke-linecap="round" />
      </template>
      <template v-else-if="kind === 'not-recorded'">
        <rect x="11" y="8" width="26" height="32" rx="3" fill="none" stroke="currentColor" stroke-width="2" />
        <path d="M17 18h14M17 25h14M17 32h8" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
      </template>
      <template v-else>
        <path d="M8 30l6-16h20l6 16v8H8z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" />
        <path d="M8 30h10l2 4h8l2-4h10" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" />
      </template>
    </svg>
    <span class="empty-state__text">{{ text ?? DEFAULT_TEXT[kind] }}</span>
  </div>
</template>

<script setup lang="ts">
// 空状态：暂无数据 / 未录入 / 加载失败（docs/07 第一部分"五、公共组件"）。
// 后端返回 null 时用它，不显示 0（docs/05 第二节）。inline 用于表格单元格等窄处，只显示文字。
withDefaults(
  defineProps<{
    kind?: 'empty' | 'not-recorded' | 'error'
    text?: string
    inline?: boolean
  }>(),
  { kind: 'empty', text: undefined, inline: false }
)

const DEFAULT_TEXT = {
  empty: '暂无数据',
  'not-recorded': '未录入',
  error: '加载失败'
} as const
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 100%;
  min-height: 80px;
  color: var(--text-secondary);
  font-size: 16px;
}

.empty-state--error {
  color: var(--danger);
}

.empty-state__icon {
  width: 48px;
  height: 48px;
  opacity: 0.8;
}

.empty-state--inline {
  display: inline;
  min-height: 0;
  font-size: inherit;
}
</style>
