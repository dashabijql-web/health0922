<template>
  <ReportPage :no="6" title="监测数据汇总" section="告警数据汇总" :status="status" :empty="!alerts" :month="month"
              :empty-text="month ? `${monthLabel(month)}，本月暂无告警数据` : '暂无数据'" @retry="emit('retry')">
    <template v-if="alerts">
      <p class="report-text">
        {{ monthLabel(month) }}，六类告警共发生 <b>{{ alerts.events }}</b> 起，涉及 <b>{{ alerts.persons }}</b> 人：
      </p>
      <EChart v-if="option" class="report-chart alerts__chart" :option="option" label="六类告警柱状图" />
      <table class="report-table">
        <thead><tr><th>告警类别</th><th class="report-num">事件数（条）</th><th class="report-num">涉及人数（人）</th></tr></thead>
        <tbody>
          <tr v-for="c in alerts.byCategory" :key="c.category" :data-category="c.category">
            <td>{{ ALERT_NAME[c.category] }}告警</td>
            <td :class="['report-num', { danger: c.events > 0 }]">{{ c.events }}</td>
            <td class="report-num">{{ c.persons }}</td>
          </tr>
          <tr class="report-table__total">
            <td>合计</td><td class="report-num">{{ alerts.events }}</td><td class="report-num">{{ alerts.persons }}</td>
          </tr>
        </tbody>
      </table>
      <p class="report-note">
        注：一条事件是一段持续的异常（同一种异常在去重时间内重复出现只算一条，不跨天）。合计人数按人去重，一个人可能有几类告警；
        没绑定职工的手表按设备算一人。低电、脱落等设备事件不在这里统计。
      </p>
    </template>
  </ReportPage>
</template>

<script setup lang="ts">
// 第 6 页：六类告警各自的事件数和涉及人数（截图 8 右页的位置）。一共没有告警时写"本月暂无告警数据"。
import { computed } from 'vue'
import EChart from '@/components/EChart.vue'
import type { MonthlyReport } from '@/api/report'
import { alertsBar, type ReportColors } from '../report-charts'
import { ALERT_NAME, monthLabel } from '../report-format'
import ReportPage from './ReportPage.vue'

const props = defineProps<{
  status: 'loading' | 'failed' | 'ready'
  month: string | null
  alerts: MonthlyReport['alerts']
  colors: ReportColors
}>()
const emit = defineEmits<{ retry: [] }>()

const option = computed(() => (props.alerts ? alertsBar(props.alerts.byCategory, props.colors) : null))
</script>

<style scoped>
.alerts__chart {
  height: 300px;
  margin-bottom: 12px;
}
</style>
