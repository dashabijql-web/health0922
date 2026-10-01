<template>
  <Panel title="上线职工曲线" :loading="loading" :failed-at="failedAt" class="curve-panel">
    <EmptyState v-if="error" kind="error" />
    <EmptyState v-else-if="data && data.points.length === 0" />
    <HeadcountChart v-else-if="data" :series="data" :now-ms="nowMs" />
  </Panel>
</template>

<script setup lang="ts">
// 底左：当天井下人数曲线，每份 RYSS 一个点（docs/05 第四节，docs/00 第 19 项按默认画井下人数）。
import type { HeadcountSeries } from '@/api/dashboard'
import EmptyState from '@/components/EmptyState.vue'
import HeadcountChart from '@/components/HeadcountChart.vue'
import Panel from '@/components/Panel.vue'

defineProps<{
  data: HeadcountSeries | null
  loading: boolean
  failedAt: string | null
  error: boolean
  nowMs: number
}>()
</script>

<style scoped>
.curve-panel :deep(.panel__body) {
  padding: 4px 8px 0 0;
}
</style>
