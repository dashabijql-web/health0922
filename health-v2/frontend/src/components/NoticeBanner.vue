<template>
  <div :class="['notice', `notice--${type}`]" :role="type === 'danger' ? 'alert' : 'status'">
    <svg class="notice__icon" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 3L2 21h20z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" />
      <path d="M12 10v5M12 17.5v.5" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
    </svg>
    <span class="notice__text"><slot>{{ message }}</slot></span>
  </div>
</template>

<script setup lang="ts">
// 顶部提示条：定位数据已过期、刷新失败等（docs/05 第二节）。除颜色外带图标，色弱用户也能分辨（docs/07）。
withDefaults(
  defineProps<{
    type?: 'warn' | 'danger' | 'info'
    message?: string
  }>(),
  { type: 'warn', message: '' }
)
</script>

<style scoped>
.notice {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  font-size: 16px;
  border: 1px solid currentColor;
}

.notice--warn {
  color: var(--warn);
  background: var(--warn-bg);
}

.notice--danger {
  color: var(--danger);
  background: var(--danger-bg);
}

.notice--info {
  color: var(--info);
  background: var(--bg-panel-strong);
}

.notice__icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
}

.notice__text {
  color: var(--text-primary);
}
</style>
