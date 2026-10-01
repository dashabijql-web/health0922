<template>
  <ScreenModal :open="open" title="今天的设备事件" :width="760" @close="emit('close')">
    <div class="list-dialog__table">
      <EmptyState v-if="list.failed.value && !list.data.value" kind="error" />
      <DarkTable v-else :columns="COLUMNS" :rows="list.data.value?.list ?? []" :row-key="(r) => r.id">
        <template #cell-name="{ row }">{{ row.cardCode ? (row.name ?? '未录入') : '未绑定设备' }}</template>
        <template #cell-card="{ row }">
          <span v-if="row.cardCode" class="num" :title="row.cardCode">{{ cardNo(row.cardCode) }}</span>
          <span v-else class="num" title="设备号 IMEI">{{ row.deviceImei }}</span>
        </template>
        <template #cell-time="{ row }">
          <span class="num">{{ hhmmss(row.lastOccurredAt) }}</span>
          <template v-if="row.occurCount > 1">（{{ row.occurCount }} 次）</template>
        </template>
        <template #empty>{{ list.loading.value ? '加载中…' : '今天没有设备事件' }}</template>
      </DarkTable>
    </div>
    <template #footer>
      <ListPager :total="list.data.value?.total ?? 0" :page="list.page.value" :pages="list.pages()" unit="条"
                 :busy="list.loading.value" @go="list.load" />
    </template>
  </ScreenModal>
</template>

<script setup lang="ts">
// 点"设备事件 N"：今天的低电、脱落、佩戴提醒、拆卸、红外、房颤，最新的在前（docs/05 第四节"设备事件"）。
// 一段持续的事件只算一条；重复发生的写次数。未绑定的表写"未绑定设备"和 IMEI。
import { toRef } from 'vue'
import { fetchDeviceEvents } from '@/api/dashboard'
import DarkTable, { type Column } from '@/components/DarkTable.vue'
import EmptyState from '@/components/EmptyState.vue'
import ScreenModal from '@/components/ScreenModal.vue'
import { cardNo, hhmmss } from '@/utils/format'
import ListPager from '@/components/ListPager.vue'
import { usePagedList } from './use-paged-list'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

const COLUMNS: Column[] = [
  { key: 'name', title: '姓名' },
  { key: 'card', title: '卡号', width: '1.9fr' },
  { key: 'event', title: '事件' },
  { key: 'time', title: '时间', width: '1.3fr' }
]

const list = usePagedList(toRef(props, 'open'), fetchDeviceEvents)
</script>

<style scoped>
.list-dialog__table {
  height: 446px;
}
</style>
