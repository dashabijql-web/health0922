<template>
  <Panel title="体征信息展示" :loading="loading" :failed-at="failedAt" class="alert-tiles">
    <template #extra>
      <button
        type="button"
        :class="['device-badge', { 'device-badge--hot': (data?.deviceEventCount ?? 0) > 0 }]"
        :disabled="!data"
        @click="emit('openDeviceEvents')"
      >
        设备事件 <span class="num">{{ data ? data.deviceEventCount : '–' }}</span>
      </button>
    </template>
    <EmptyState v-if="error" kind="error" />
    <div v-else class="alert-tiles__grid">
      <StatTile
        v-for="t in TILES"
        :key="t.category"
        :name="t.name"
        :icon="t.icon"
        :value="data ? data.alerts[t.category] : null"
        @select="emit('select', t.category)"
      />
    </div>
  </Panel>
</template>

<script setup lang="ts">
// 右上：六类告警今天的人数 + 标题栏"设备事件 N"（docs/05 第四节、docs/09 第三节）。
import type { AlertCategory, Overview } from '@/api/dashboard'
import EmptyState from '@/components/EmptyState.vue'
import Panel from '@/components/Panel.vue'
import StatTile from '@/components/StatTile.vue'
import type { IconName } from '@/components/icon-names'

defineProps<{ data: Overview | null; loading: boolean; failedAt: string | null; error: boolean }>()
const emit = defineEmits<{ select: [category: AlertCategory]; openDeviceEvents: [] }>()

const TILES: { category: AlertCategory; name: string; icon: IconName }[] = [
  { category: 'SOS', name: 'SOS告警', icon: 'sos' },
  { category: 'FALL', name: '跌倒告警', icon: 'fall' },
  { category: 'HEART_RATE', name: '心率', icon: 'heart' },
  { category: 'BLOOD_PRESSURE', name: '血压', icon: 'pressure' },
  { category: 'SPO2', name: '血氧', icon: 'drop' },
  { category: 'TEMPERATURE', name: '体温', icon: 'thermometer' }
]
</script>

<style scoped>
.alert-tiles :deep(.panel__body) {
  padding: 9px 21px 0 19px;
}

.alert-tiles__grid {
  display: grid;
  grid-template-columns: repeat(3, 127px);
  grid-template-rows: repeat(2, 61px);
  gap: 11px 9px;
}

.device-badge {
  height: 26px;
  padding: 0 10px;
  border: 1px solid var(--accent);
  border-radius: 2px;
  background: transparent;
  color: var(--accent);
  font-size: 13px;
  cursor: pointer;
}

.device-badge--hot .num {
  color: var(--warn);
}

.device-badge:disabled {
  opacity: 0.6;
  cursor: default;
}
</style>
