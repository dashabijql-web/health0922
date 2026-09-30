<template>
  <!-- 入口页的内容在阶段 3 做（docs/08）；阶段 0 只是登录后能进入的空白首页 -->
  <div class="portal">
    <Panel title="入口页" class="portal__panel">
      <EmptyState text="建设中" />
    </Panel>
  </div>
</template>

<script setup lang="ts">
import EmptyState from '@/components/EmptyState.vue'
import Panel from '@/components/Panel.vue'
</script>

<style scoped>
.portal {
  display: grid;
  place-items: center;
  height: 100%;
}

.portal__panel {
  width: 640px;
  height: 320px;
}
</style>
