<template>
  <button type="button" :class="['screen-button', `screen-button--${kind}`]" :disabled="disabled"><slot /></button>
</template>

<script setup lang="ts">
// 弹层里的按钮。截图 13 的"取消""确定"颜色一样：亮青绿底、深色字（docs/09 第八节）。
withDefaults(defineProps<{ kind?: 'bright' | 'plain'; disabled?: boolean }>(), { kind: 'bright', disabled: false })
</script>

<style scoped>
.screen-button {
  min-width: 58px;
  height: 32px;
  padding: 0 14px;
  border: 0;
  border-radius: 3px;
  font-size: 14px;
  cursor: pointer;
}

.screen-button--bright {
  background: var(--btn-bright);
  color: var(--btn-bright-text);
}

.screen-button--bright:hover:not(:disabled) {
  filter: brightness(1.08);
}

.screen-button--plain {
  background: transparent;
  border: 1px solid var(--border-glow);
  color: var(--text-primary);
}

.screen-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
