<template>
  <Panel title="实时运动量" :loading="loading" :failed-at="failedAt" class="steps-panel">
    <EmptyState v-if="error" kind="error" />
    <EmptyState v-else-if="data && data.length === 0" />
    <ol v-else class="steps-panel__list">
      <li v-for="r in data ?? []" :key="r.cardCode" class="steps-panel__row">
        <RankBar
          :rank="r.rank"
          :name="r.name ?? '未录入'"
          :title="`卡号 ${cardNo(r.cardCode)}（${r.cardCode}）`"
          :pct="r.barPct"
          :value="r.steps"
          unit="步"
        />
      </li>
    </ol>
  </Panel>
</template>

<script setup lang="ts">
// 底右：今天步数排行（docs/05 第四节"实时运动量"）。一页显示 5 行，其余滚动。
import type { StepRank } from '@/api/dashboard'
import EmptyState from '@/components/EmptyState.vue'
import Panel from '@/components/Panel.vue'
import RankBar from '@/components/RankBar.vue'
import { cardNo } from '@/utils/format'

defineProps<{ data: StepRank[] | null; loading: boolean; failedAt: string | null; error: boolean }>()
</script>

<style scoped>
.steps-panel :deep(.panel__body) {
  padding: 4px 14px 0 16px;
}

.steps-panel__list {
  height: 100%;
  margin: 0;
  padding: 0;
  list-style: none;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--border-glow) transparent;
}

.steps-panel__row {
  height: 46px;
  padding: 5px 0;
}
</style>
