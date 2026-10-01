<template>
  <ul class="vitals">
    <li v-for="c in cells" :key="c.key" :class="['vitals__cell', { 'vitals__cell--stale': c.stale }]"
        :data-key="c.key">
      <span class="vitals__icon" aria-hidden="true"><ScreenIcon :name="c.icon" /></span>
      <div class="vitals__body">
        <p class="vitals__label">{{ c.label }}</p>
        <p v-if="c.value !== null" class="vitals__value"><span class="num">{{ c.value }}</span><small>{{ c.unit }}</small></p>
        <p v-else class="vitals__value vitals__value--none">暂无数据</p>
        <p v-if="c.time" class="vitals__time" :title="c.fullTime ?? ''">
          {{ c.time }}<template v-if="c.stale"> · 数据较旧</template>
        </p>
      </div>
    </li>
  </ul>
</template>

<script setup lang="ts">
// 当前体征 2×3 格子（docs/09 第七节"右上"）：心率、血氧、体温、收缩压/舒张压、电量、今日步数。
// 每格写采集时间（不是请求时间）；超过"数据较旧"阈值的标灰并写"数据较旧"；没有的写"暂无数据"（docs/05 第二节）。
import { computed } from 'vue'
import type { PersonDetail, Vital } from '@/api/archive'
import ScreenIcon from '@/components/ScreenIcon.vue'
import type { IconName } from '@/components/icon-names'
import { shortTime } from '@/utils/format'

const props = defineProps<{ detail: PersonDetail }>()

interface Cell {
  key: string
  icon: IconName
  label: string
  unit: string
  value: string | null
  /** "10:13 采集"；不是今天的带月日 */
  time: string | null
  fullTime: string | null
  stale: boolean
}

function num(v: number, digits = 0): string {
  return digits ? v.toFixed(digits) : String(Math.round(v))
}

function vitalCell(key: string, icon: IconName, label: string, unit: string, v: Vital | null,
                   fmt: (v: Vital) => string): Cell {
  return {
    key, icon, label, unit,
    value: v ? fmt(v) : null,
    time: v ? `${shortTime(v.collectedAt)} 采集` : null,
    fullTime: v?.collectedAt ?? null,
    stale: v?.stale ?? false
  }
}

const cells = computed<Cell[]>(() => {
  const d = props.detail
  const vit = d.vitals
  return [
    vitalCell('HEART_RATE', 'heart', '心率', '次/分', vit.HEART_RATE, (v) => num(v.value)),
    vitalCell('SPO2', 'drop', '血氧', '%', vit.SPO2, (v) => num(v.value)),
    vitalCell('TEMPERATURE', 'thermometer', '体温', '℃', vit.TEMPERATURE, (v) => num(v.value, 1)),
    vitalCell('BLOOD_PRESSURE', 'pressure', '收缩压/舒张压', 'mmHg', vit.BLOOD_PRESSURE,
      (v) => `${num(v.value)}/${v.value2 === null ? '—' : num(v.value2)}`),
    {
      key: 'BATTERY', icon: 'battery', label: '电量', unit: '%',
      value: d.battery ? String(d.battery.pct) : null,
      time: d.battery ? `${shortTime(d.battery.time)} 上报` : null,
      fullTime: d.battery?.time ?? null,
      stale: false
    },
    {
      key: 'STEPS', icon: 'runner', label: '今日步数', unit: '步',
      value: d.steps ? String(d.steps.steps) : null,
      time: d.steps ? `${shortTime(d.steps.updatedAt)} 更新` : null,
      fullTime: d.steps?.updatedAt ?? null,
      stale: false
    }
  ]
})
</script>

<style scoped>
.vitals {
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-auto-rows: 1fr;
  gap: 8px;
  height: 100%;
  margin: 0;
  padding: 0;
  list-style: none;
}

.vitals__cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 0 10px;
  border: 1px solid var(--vital-cell-border);
  background: var(--vital-cell-bg);
}

.vitals__icon {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  color: var(--vital-icon);
  filter: drop-shadow(0 0 4px var(--accent-glow));
}

.vitals__body {
  min-width: 0;
}

.vitals__body p {
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.vitals__label {
  font-size: 14px;
  color: var(--text-dim-white);
}

.vitals__value small {
  margin-left: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.vitals__value {
  font-size: 22px;
  line-height: 28px;
  color: var(--accent);
}

.vitals__value--none {
  font-size: 15px;
  color: var(--text-secondary);
}

.vitals__time {
  font-size: 12px;
  color: var(--text-secondary);
}

.vitals__cell--stale .vitals__value,
.vitals__cell--stale .vitals__icon {
  color: var(--card-stale);
  filter: none;
}

.vitals__cell--stale .vitals__time {
  color: var(--warn);
}
</style>
