<template>
  <section class="records" aria-label="预警记录">
    <div class="records__table">
      <div v-if="loading" class="records__loading" aria-busy="true">加载中……</div>
      <EmptyState v-else-if="error" kind="error" />
      <DarkTable v-else :columns="COLUMNS" :rows="data?.list ?? []" :row-key="(r) => r.id" :row-height="40">
        <template #cell-time="{ row }"><span class="num" :title="row.occurredAt">{{ dayTime(row.occurredAt) }}</span></template>
        <template #cell-event="{ row }">
          <span :class="{ 'records__alert': row.category !== 'OTHER' }">{{ row.event }}</span>
          <span v-if="row.category === 'OTHER'" class="records__tag">设备事件</span>
        </template>
        <template #cell-value="{ row }"><span class="num">{{ row.value ?? '—' }}</span></template>
        <template #cell-count="{ row }"><span class="num">{{ row.count }}</span> 次</template>
        <template #cell-last="{ row }"><span class="num" :title="row.lastOccurredAt">{{ dayTime(row.lastOccurredAt) }}</span></template>
        <template #empty>暂无预警记录</template>
      </DarkTable>
    </div>
    <ListPager v-if="data && data.total > 0" class="records__pager" :total="data.total" :page="page" :pages="pages"
               unit="条" :busy="busy" @go="(p) => emit('go', p)" />
    <p v-if="failedAt" class="records__failed" role="status">刷新失败，显示的是 {{ failedAt }} 的数据</p>
  </section>
</template>

<script setup lang="ts">
// 个人档案"预警记录"标签（docs/05 第七节）：这个人的全部事件，最新的在前，服务端分页。
// 六类告警（体征越界、SOS、跌倒）和设备事件（低电、脱落……）都列出来，设备事件另外标出；告警没有状态（AGENTS.md"告警"）。
import type { AlertRecord } from '@/api/archive'
import type { Page } from '@/api/dashboard'
import DarkTable, { type Column } from '@/components/DarkTable.vue'
import EmptyState from '@/components/EmptyState.vue'
import ListPager from '@/components/ListPager.vue'

defineProps<{
  data: Page<AlertRecord> | null
  loading: boolean
  error: boolean
  failedAt: string | null
  page: number
  pages: number
  busy: boolean
}>()
const emit = defineEmits<{ go: [page: number] }>()

const COLUMNS: Column[] = [
  { key: 'time', title: '发生时间', width: '1.3fr' },
  { key: 'event', title: '事件', width: '1.6fr' },
  { key: 'value', title: '触发时的值', width: '1fr' },
  { key: 'count', title: '次数', width: '0.8fr' },
  { key: 'last', title: '最近一次', width: '1.3fr' }
]

/** "2026-09-24 10:13:19" → "2026-09-24 10:13" */
function dayTime(t: string): string {
  return t.slice(0, 16)
}
</script>

<style scoped>
.records {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: 100%;
}

.records__table {
  flex: 1;
  min-height: 0;
}

.records__loading {
  padding: 40px;
  text-align: center;
  color: var(--text-secondary);
}

.records__alert {
  color: var(--danger);
}

.records__tag {
  margin-left: 6px;
  padding: 0 6px;
  border: 1px solid var(--border-glow);
  border-radius: 2px;
  font-size: 12px;
  color: var(--text-secondary);
}

.records__failed {
  margin: 0;
  text-align: right;
  font-size: 12px;
  color: var(--warn);
}
</style>
