<template>
  <ScreenModal :open="category !== null" :title="title" :width="760" @close="emit('close')">
    <div class="list-dialog__table">
      <EmptyState v-if="list.failed.value && !list.data.value" kind="error" />
      <DarkTable v-else :columns="COLUMNS" :rows="list.data.value?.list ?? []" :row-key="(r) => r.cardCode ?? r.deviceImei ?? ''">
        <template #cell-name="{ row }">{{ row.cardCode ? (row.name ?? '未录入') : '未绑定设备' }}</template>
        <template #cell-card="{ row }">
          <span v-if="row.cardCode" class="num" :title="row.cardCode">{{ cardNo(row.cardCode) }}</span>
          <span v-else class="num" title="设备号 IMEI">{{ row.deviceImei }}</span>
        </template>
        <template #cell-dept="{ row }">{{ row.cardCode ? (row.dept ?? '未录入') : '—' }}</template>
        <template #cell-event="{ row }">{{ row.lastEvent }}<template v-if="row.lastValue">（{{ row.lastValue }}）</template></template>
        <template #cell-time="{ row }"><span class="num">{{ hhmmss(row.lastAt) }}</span></template>
        <template #cell-count="{ row }">{{ row.eventCount }} 条 / {{ row.occurTotal }} 次</template>
        <template #empty>{{ list.loading.value ? '加载中…' : '今天没有' }}</template>
      </DarkTable>
    </div>
    <template #footer>
      <ListPager :total="list.data.value?.total ?? 0" :page="list.page.value" :pages="list.pages()" unit="人"
                 :busy="list.loading.value" @go="list.load" />
    </template>
  </ScreenModal>
</template>

<script setup lang="ts">
// 点开某个告警格子：今天出过这一类告警的人，一人一行，人数和格子一致（docs/05 第四节"六类告警"）。
// 没绑定的表写"未绑定设备 + IMEI"。
import { computed } from 'vue'
import { fetchAlertPersons, type AlertCategory } from '@/api/dashboard'
import DarkTable, { type Column } from '@/components/DarkTable.vue'
import EmptyState from '@/components/EmptyState.vue'
import ScreenModal from '@/components/ScreenModal.vue'
import { cardNo, hhmmss } from '@/utils/format'
import ListPager from './ListPager.vue'
import { usePagedList } from './use-paged-list'

const props = defineProps<{ category: AlertCategory | null }>()
const emit = defineEmits<{ close: [] }>()

const NAMES: Record<AlertCategory, string> = {
  SOS: 'SOS告警', FALL: '跌倒告警', HEART_RATE: '心率告警', BLOOD_PRESSURE: '血压告警', SPO2: '血氧告警', TEMPERATURE: '体温告警'
}
const COLUMNS: Column[] = [
  { key: 'name', title: '姓名', width: '1fr' },
  { key: 'card', title: '卡号', width: '1.9fr' },
  { key: 'dept', title: '部门', width: '1.1fr' },
  { key: 'event', title: '最近一次', width: '1.3fr' },
  { key: 'time', title: '时间', width: '0.9fr' },
  { key: 'count', title: '今天', width: '1fr' }
]

const title = computed(() => (props.category ? `今天的${NAMES[props.category]}` : ''))
const open = computed(() => props.category !== null)
const list = usePagedList(open, (page, size) => fetchAlertPersons(props.category!, page, size))
</script>

<style scoped>
.list-dialog__table {
  height: 446px;
}
</style>
