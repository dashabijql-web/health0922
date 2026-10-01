<template>
  <section :class="['pcard', { 'pcard--docked': docked }]" role="dialog" :aria-label="`${title}的体征卡`">
    <button type="button" class="pcard__close" aria-label="关闭" @click="emit('close')"><ScreenIcon name="close" /></button>

    <div class="pcard__body">
      <p class="pcard__title">
        <strong>{{ title }}</strong>
        <span class="num" :title="person.cardCode">卡号 {{ cardNo(person.cardCode) }}</span>
        <span>{{ dept }}</span>
      </p>

      <!-- 位置：区域 + 基站 + 定位时间（docs/05 第五节） -->
      <p class="pcard__row">
        <span class="pcard__label">位置：</span>
        <span v-if="pos">
          {{ pos.areaName ?? '暂无数据' }} · {{ pos.stationName ?? '暂无数据' }}附近
          <span class="pcard__time">{{ shortTime(pos.posTime) }} 定位</span>
        </span>
        <span v-else-if="data">不在井下</span>
        <span v-else>{{ person.areaName ?? '暂无数据' }} · {{ person.stationName ?? '暂无数据' }}附近</span>
      </p>
      <p v-if="unplacedHint" class="pcard__warn" role="status">
        这个人所在的基站（{{ unplacedHint }}）还没有摆放位置
      </p>
      <p v-if="pos?.stationAbnormal" class="pcard__warn" role="status">所在基站通讯异常，位置可能不准</p>

      <template v-if="data">
        <p v-for="v in VITALS" :key="v.key" :class="['pcard__row', { 'pcard__row--stale': data.vitals[v.key]?.stale }]">
          <span class="pcard__label">{{ v.label }}：</span>
          <template v-if="data.vitals[v.key]">
            <span class="num">{{ v.format(data.vitals[v.key]!) }}</span>
            <span class="pcard__unit">{{ v.unit }}</span>
            <span class="pcard__time">{{ shortTime(data.vitals[v.key]!.collectedAt) }}</span>
            <span v-if="data.vitals[v.key]!.stale" class="pcard__stale">数据较旧</span>
          </template>
          <span v-else class="pcard__none">暂无数据</span>
        </p>
        <p class="pcard__row">
          <span class="pcard__label">电量：</span>
          <template v-if="data.battery">
            <span class="num">{{ data.battery.pct }}</span><span class="pcard__unit">%</span>
            <span v-if="data.battery.time" class="pcard__time">{{ shortTime(data.battery.time) }}</span>
          </template>
          <span v-else class="pcard__none">暂无数据</span>
        </p>
        <p class="pcard__row">
          <span class="pcard__label">手表：</span>
          <span v-if="data.watch.state === 'ONLINE'">在线</span>
          <span v-else-if="data.watch.state === 'OFFLINE'">
            离线<span class="pcard__time">{{ data.watch.lastSeenAt ? `最后在线 ${shortTime(data.watch.lastSeenAt)}` : '从没上线' }}</span>
          </span>
          <span v-else>未绑定</span>
        </p>
      </template>
      <div v-else-if="loading" class="pcard__loading" aria-busy="true">读取体征……</div>
      <p v-else class="pcard__warn">体征读取失败，请稍后刷新</p>

      <!-- 告警记录：最近 3 条 -->
      <ul v-if="alertsOpen && data" class="pcard__alerts">
        <li v-for="(a, i) in data.alerts" :key="i">
          <span class="pcard__time">{{ shortTime(a.lastOccurredAt) }}</span>
          <span>{{ a.event }}</span>
          <span v-if="a.value" class="num">{{ a.value }}</span>
        </li>
        <li v-if="data.alerts.length === 0" class="pcard__none">暂无告警记录</li>
      </ul>
    </div>

    <footer class="pcard__actions">
      <button type="button" :aria-pressed="alertsOpen" @click="alertsOpen = !alertsOpen">告警记录</button>
      <button type="button" @click="emit('archive')">档案</button>
      <button v-if="unplacedHint" type="button" class="pcard__place" @click="emit('place', placeStation)">去摆放</button>
    </footer>
    <span v-if="!docked" class="pcard__arrow" aria-hidden="true"></span>
  </section>
</template>

<script setup lang="ts">
// 地图模式的体征卡（docs/05 第五节，样式对照截图 15）：青绿色卡片、白色小字，下面一排文字按钮，竖线分隔。
// 每项体征带采集时间，超过"数据较旧"阈值的标灰；人所在基站没摆放时卡片停在地图上方，并给"去摆放"入口。
import { computed, ref, watch } from 'vue'
import type { MapPerson, PersonCard, Vital, VitalKey } from '@/api/map'
import ScreenIcon from '@/components/ScreenIcon.vue'
import { cardNo, shortTime } from '@/utils/format'

const props = defineProps<{
  /** 人员列表里的这个人（卡片数据回来之前先显示姓名、位置） */
  person: MapPerson
  data: PersonCard | null
  loading: boolean
  /** 没挂在地图上（所在基站没摆放） */
  docked: boolean
}>()
const emit = defineEmits<{ close: []; archive: []; place: [stationCode: string | null] }>()

const VITALS: { key: VitalKey; label: string; unit: string; format: (v: Vital) => string }[] = [
  { key: 'HEART_RATE', label: '心率', unit: 'bpm', format: (v) => String(Math.round(v.value)) },
  { key: 'TEMPERATURE', label: '体温', unit: '℃', format: (v) => v.value.toFixed(1) },
  { key: 'SPO2', label: '血氧', unit: '%', format: (v) => String(Math.round(v.value)) },
  { key: 'BLOOD_PRESSURE', label: '血压', unit: 'mmHg', format: (v) => `${Math.round(v.value)}/${v.value2 == null ? '—' : Math.round(v.value2)}` }
]

const alertsOpen = ref(false)
watch(() => props.person.cardCode, () => (alertsOpen.value = false))

const title = computed(() => props.data?.name ?? props.person.name ?? '未录入')
const dept = computed(() => props.data?.dept ?? props.person.dept ?? '部门未录入')
const pos = computed(() => props.data?.position ?? null)
const placeStation = computed(() => pos.value?.stationCode ?? props.person.stationCode)
/** 所在基站没摆放时提示里写的基站名称 */
const unplacedHint = computed(() => {
  if (pos.value) return pos.value.placed ? null : pos.value.stationName ?? '未知基站'
  if (props.data) return null
  return props.person.placed ? null : props.person.stationName ?? '未知基站'
})
</script>

<style scoped>
.pcard {
  position: relative;
  width: 318px;
  color: var(--text-bright);
  font-size: 13px;
  line-height: 1.75;
  background: var(--card-bg);
  border: 1px solid var(--card-border);
  box-shadow: 0 6px 22px var(--overlay-shadow);
}

.pcard__close {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--card-muted);
  cursor: pointer;
}

.pcard__body {
  padding: 10px 14px 8px;
}

.pcard__title {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 10px;
  margin: 0 22px 4px 0;
}

.pcard__title strong {
  font-size: 15px;
}

.pcard__row {
  margin: 0;
}

.pcard__row--stale {
  color: var(--card-stale);
}

.pcard__label {
  font-weight: 700;
}

.pcard__unit {
  margin-left: 2px;
}

.pcard__time {
  margin-left: 8px;
  font-size: 12px;
  color: var(--card-muted);
}

.pcard__stale {
  margin-left: 6px;
  padding: 0 4px;
  font-size: 11px;
  border: 1px solid var(--card-stale);
  border-radius: 2px;
}

.pcard__none {
  color: var(--card-muted);
}

.pcard__warn {
  margin: 2px 0;
  padding: 1px 6px;
  font-size: 12px;
  color: var(--card-warn-text);
  background: var(--card-warn-bg);
  border-radius: 2px;
}

.pcard__loading {
  padding: 8px 0;
  color: var(--card-muted);
}

.pcard__alerts {
  margin: 6px 0 0;
  padding: 6px 8px;
  list-style: none;
  background: var(--card-inset);
}

.pcard__alerts li {
  display: flex;
  gap: 8px;
}

.pcard__actions {
  display: flex;
  border-top: 1px solid var(--card-divider);
  background: var(--card-footer);
}

.pcard__actions button {
  flex: 1;
  height: 34px;
  border: 0;
  border-left: 1px solid var(--card-divider);
  background: none;
  color: var(--text-bright);
  font-size: 13px;
  cursor: pointer;
}

.pcard__actions button:first-child {
  border-left: 0;
}

.pcard__actions button:hover,
.pcard__actions button[aria-pressed='true'] {
  background: var(--card-hover);
}

.pcard__actions .pcard__place {
  color: var(--card-action-hi);
  font-weight: 700;
}

.pcard__arrow {
  position: absolute;
  left: 50%;
  bottom: -9px;
  width: 0;
  height: 0;
  margin-left: -9px;
  border-left: 9px solid transparent;
  border-right: 9px solid transparent;
  border-top: 9px solid var(--card-footer);
}
</style>
