<template>
  <button type="button" class="mode-button"><slot /></button>
</template>

<script setup lang="ts">
// "展示模式" / "地图模式" 胶囊按钮（docs/09 第三、四节），点击切换模式。
</script>

<style scoped>
.mode-button {
  width: 100%;
  height: 100%;
  border: 1px solid var(--accent);
  border-radius: 27px;
  background: linear-gradient(180deg, var(--mode-btn-top), var(--mode-btn-bottom));
  box-shadow: 0 0 14px var(--accent-glow), inset 0 1px 0 var(--shine);
  color: var(--text-bright);
  font-size: 25px;
  letter-spacing: 2px;
  cursor: pointer;
}

.mode-button:hover {
  filter: brightness(1.1);
}
</style>
