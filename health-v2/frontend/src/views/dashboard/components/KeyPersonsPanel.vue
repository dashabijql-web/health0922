<template>
  <Panel title="重点人员列表" :loading="loading" :failed-at="failedAt" class="key-persons">
    <EmptyState v-if="error" kind="error" />
    <DarkTable v-else :columns="COLUMNS" :rows="data ?? []" :row-key="(r) => r.cardCode" :row-height="40">
      <template #cell-name="{ row }">{{ row.name ?? '未录入' }}</template>
      <template #cell-card="{ row }"><span class="num" :title="row.cardCode">{{ cardNo(row.cardCode) }}</span></template>
      <template #cell-dept="{ row }"><span :title="row.dept ?? ''">{{ row.dept ?? '未录入' }}</span></template>
      <template #cell-detail><button type="button" class="key-persons__link" @click="openArchive">详情</button></template>
      <template #empty>暂无重点人员</template>
    </DarkTable>
  </Panel>
</template>

<script setup lang="ts">
// 右中：重点监护名单（docs/05 第四节"重点人员"）。"详情"跳个人档案，个人档案在阶段 5 做。
import { ElMessage } from 'element-plus'
import type { KeyPerson } from '@/api/dashboard'
import DarkTable, { type Column } from '@/components/DarkTable.vue'
import EmptyState from '@/components/EmptyState.vue'
import Panel from '@/components/Panel.vue'
import { cardNo } from '@/utils/format'

defineProps<{ data: KeyPerson[] | null; loading: boolean; failedAt: string | null; error: boolean }>()

const COLUMNS: Column[] = [
  { key: 'name', title: '姓名' },
  { key: 'card', title: '卡号' },
  { key: 'dept', title: '部门', width: '1.4fr' },
  { key: 'detail', title: '详情', width: '0.8fr' }
]

function openArchive() {
  ElMessage.info('建设中')
}
</script>

<style scoped>
.key-persons :deep(.panel__body) {
  padding: 6px 0 0;
}

.key-persons__link {
  border: 0;
  background: none;
  color: var(--accent);
  cursor: pointer;
  font-size: 15px;
}
</style>
