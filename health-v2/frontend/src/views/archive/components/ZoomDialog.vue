<template>
  <ScreenModal :open="open" :title="`查看详情 · ${title}`" :width="1100" @close="emit('close')">
    <div class="zoom">
      <div class="zoom__bar">
        <div class="zoom__tabs" role="tablist" aria-label="指标">
          <button v-for="m in METRICS" :key="m.key" type="button" role="tab" :aria-selected="m.key === metric"
                  :class="['zoom__tab', { 'zoom__tab--on': m.key === metric }]"
                  @click="emit('metric', m.key)">{{ m.name }}</button>
        </div>
        <div v-if="metric !== 'STEPS'" class="zoom__date">
          <ScreenButton kind="plain" @click="emit('shift', -1)">前一天</ScreenButton>
          <input class="zoom__date-input" type="date" :value="date" :max="today" aria-label="日期"
                 @change="(e) => emit('date', (e.target as HTMLInputElement).value)" />
          <ScreenButton kind="plain" :disabled="isToday" @click="emit('shift', 1)">后一天</ScreenButton>
        </div>
        <p v-else class="zoom__hint">近 {{ stepDays }} 天，每天的步数</p>
      </div>
      <div class="zoom__chart">
        <p v-if="loading" class="zoom__msg" aria-busy="true">加载中……</p>
        <EmptyState v-else-if="failed" kind="error" />
        <EChart v-else-if="option" :option="option" :label="title" />
        <EmptyState v-else />
      </div>
      <p class="zoom__foot">
        <template v-if="metric === 'STEPS'">没有记录的日子不画柱子</template>
        <template v-else>
          {{ date }} 0 点起的全部原始读数<template v-if="count !== null">，共 <span class="num">{{ count }}</span> 个</template>；
          两次读数隔得太久（超过 {{ gapMinutes ?? '—' }} 分钟）时折线断开
        </template>
      </p>
    </div>
  </ScreenModal>
</template>

<script setup lang="ts">
// 个人档案"查看详情"（docs/05 第七节）：放大的图表，可换指标（含血压、步数）和日期。
import { computed } from 'vue'
import type { EChartsCoreOption } from 'echarts/core'
import EChart from '@/components/EChart.vue'
import EmptyState from '@/components/EmptyState.vue'
import ScreenButton from '@/components/ScreenButton.vue'
import ScreenModal from '@/components/ScreenModal.vue'
import { localDate } from '@/utils/format'
import type { DetailMetric } from '../use-person-archive-page'

const props = defineProps<{
  open: boolean
  metric: DetailMetric
  date: string
  isToday: boolean
  option: EChartsCoreOption | null
  loading: boolean
  failed: boolean
  count: number | null
  gapMinutes: number | null
  stepDays: number
}>()
const emit = defineEmits<{ close: []; metric: [m: DetailMetric]; date: [d: string]; shift: [days: number] }>()

const METRICS: { key: DetailMetric; name: string }[] = [
  { key: 'HEART_RATE', name: '心率' },
  { key: 'SPO2', name: '血氧' },
  { key: 'TEMPERATURE', name: '体温' },
  { key: 'BLOOD_PRESSURE', name: '血压' },
  { key: 'STEPS', name: '步数' }
]

const title = computed(() => METRICS.find((m) => m.key === props.metric)?.name ?? '')
const today = computed(() => (props.open ? localDate() : ''))
</script>

<style scoped>
.zoom {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 560px;
}

.zoom__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.zoom__tabs {
  display: flex;
  gap: 8px;
}

.zoom__tab {
  height: 32px;
  padding: 0 18px;
  border: 1px solid var(--border-glow);
  border-radius: 16px;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  font-size: 15px;
  cursor: pointer;
}

.zoom__tab--on {
  border-color: transparent;
  background: linear-gradient(180deg, var(--mode-btn-top), var(--mode-btn-bottom));
  color: var(--text-bright);
}

.zoom__date {
  display: flex;
  align-items: center;
  gap: 10px;
}

.zoom__date-input {
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--border-glow);
  border-radius: 3px;
  background: var(--input-bg);
  color: var(--text-primary);
  color-scheme: dark;
  font: inherit;
  font-size: 15px;
}

.zoom__hint,
.zoom__foot {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.zoom__chart {
  position: relative;
  flex: 1;
  min-height: 0;
  border: 1px solid var(--border-faint);
  background: var(--inset-bg);
}

.zoom__msg {
  margin: 0;
  padding-top: 200px;
  text-align: center;
  color: var(--text-secondary);
}
</style>
