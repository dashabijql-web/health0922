<template>
  <Panel :title="title" :loading="loading" :failed-at="failedAt" class="chart-panel">
    <template #extra>
      <button type="button" class="zoom-btn" @click="emit('zoom')">查看详情</button>
    </template>
    <EmptyState v-if="error" kind="error" />
    <template v-else>
      <p class="chart-panel__unit">单位：{{ unit }}</p>
      <div class="chart-panel__chart">
        <EChart v-if="option" :option="option" :label="title" />
        <EmptyState v-else />
      </div>
    </template>
  </Panel>
</template>

<script setup lang="ts">
// 个人档案的一个图表面板（截图 4 右中、底部三块）：标题栏右边"查看详情"，里面是图表；没有数据时显示"暂无数据"，
// 不画一张空图（docs/05 第二节）。图表配置由 archive-charts.ts 的纯函数算好传进来。
import type { EChartsCoreOption } from 'echarts/core'
import EChart from '@/components/EChart.vue'
import EmptyState from '@/components/EmptyState.vue'
import Panel from '@/components/Panel.vue'

defineProps<{
  title: string
  unit: string
  option: EChartsCoreOption | null
  loading: boolean
  failedAt: string | null
  error: boolean
}>()
const emit = defineEmits<{ zoom: [] }>()
</script>

<style scoped>
.chart-panel :deep(.panel__body) {
  display: flex;
  flex-direction: column;
  padding: 6px 12px 8px;
}

.chart-panel__unit {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.chart-panel__chart {
  flex: 1;
  min-height: 0;
}
</style>

<style>
/* "查看详情"：青色描边的小按钮（截图 4 每个面板标题栏右边），几个面板共用 */
.zoom-btn {
  height: 26px;
  padding: 0 10px;
  border: 1px solid var(--accent-soft);
  border-radius: 2px;
  background: var(--accent-bg);
  color: var(--accent);
  font: inherit;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.zoom-btn:hover {
  background: var(--accent-hover);
  filter: brightness(1.2);
}
</style>
