<template>
  <ReportPage :no="no" title="数据汇总统计" :section="section" :status="status" :empty="!data" :month="month"
              @retry="emit('retry')">
    <template v-if="data">
      <p class="report-text"><slot name="summary" /></p>
      <EChart v-if="option" class="report-chart share__pie" :option="option" :label="`${section}饼图`" />
      <table class="report-table">
        <colgroup><col style="width: 52%" /><col /><col /></colgroup>
        <thead><tr><th>名称</th><th class="report-num">数量（人）</th><th class="report-num">百分比（%）</th></tr></thead>
        <tbody>
          <tr v-for="s in data.shares" :key="`${s.kind}-${s.name}`">
            <td :class="{ missing: s.kind === 'UNRECORDED' }" :title="shareName(s)">{{ shareName(s) }}</td>
            <td class="report-num">{{ s.count }}</td>
            <td class="report-num">{{ s.percent.toFixed(2) }}</td>
          </tr>
          <tr class="report-table__total">
            <td>合计</td><td class="report-num">{{ data.total }}</td><td class="report-num">100.00</td>
          </tr>
        </tbody>
      </table>
      <p v-if="data.shares.some((s) => s.kind === 'OTHER')" class="report-note">
        注：人数最多的前 8 {{ unit }}单独列出，其余合并为"其他"；{{ unit === '种' ? '工种' : '部门' }}未录入的单独一类。
      </p>
    </template>
  </ReportPage>
</template>

<script setup lang="ts">
// 第 3 页（概述：按工种）、第 4 页（智能手表使用汇总：按部门）：一句概述、饼图、表格（截图 7）。
// 表格和饼图是同样的几行（前 8 + 其他 + 未录入），最后一行合计。
import { computed } from 'vue'
import EChart from '@/components/EChart.vue'
import type { Share } from '@/api/report'
import { sharePie, type ReportColors } from '../report-charts'
import { shareName } from '../report-format'
import ReportPage from './ReportPage.vue'

const props = defineProps<{
  no: number
  section: string
  /** 量词："种"（工种）或"个"（部门） */
  unit: string
  status: 'loading' | 'failed' | 'ready'
  month: string | null
  /** 没有数据为 null */
  data: { shares: Share[]; total: number } | null
  colors: ReportColors
}>()
const emit = defineEmits<{ retry: [] }>()

const option = computed(() => (props.data ? sharePie(props.data.shares, props.colors) : null))
</script>

<style scoped>
.share__pie {
  height: 270px;
  margin-bottom: 8px;
}
</style>
