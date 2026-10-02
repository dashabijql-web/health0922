<template>
  <ReportPage :no="5" title="监测数据汇总" section="体征指标" :status="status" :empty="!vitals" :month="month"
              @retry="emit('retry')">
    <template v-if="vitals">
      <p class="report-text">{{ monthLabel(month) }}，职工各项数据统计如下：</p>
      <EChart v-if="option" class="report-chart vitals__chart" :option="option" label="体征指标柱状图" />
      <div class="vitals__blocks">
        <section v-for="m in METRICS" :key="m" class="vitals__block" :data-metric="m">
          <h4 class="vitals__name">{{ METRIC_NAME[m].label }}</h4>
          <dl v-if="vitals[m]" class="vitals__list">
            <div><dt>最大值</dt><dd class="v1">{{ vitalText(m, vitals[m]!.max) }}</dd></div>
            <div><dt>最小值</dt><dd class="v2">{{ vitalText(m, vitals[m]!.min) }}</dd></div>
            <div><dt>平均值</dt><dd class="v3">{{ vitalText(m, vitals[m]!.avg) }}</dd></div>
          </dl>
          <p v-else class="vitals__none">暂无数据</p>
        </section>
      </div>
      <p class="report-note">注：最大、最小值取各日的最大、最小值；平均值按每天的测量次数加权。</p>
    </template>
  </ReportPage>
</template>

<script setup lang="ts">
// 第 5 页：心率、体温、血氧的最大/最小/平均，柱状图 + 三组大数字（截图 8 左页）。没有数据的那项写"暂无数据"。
import { computed } from 'vue'
import EChart from '@/components/EChart.vue'
import type { MonthlyReport } from '@/api/report'
import { vitalsBar, type ReportColors } from '../report-charts'
import { METRICS, METRIC_NAME, monthLabel, vitalText } from '../report-format'
import ReportPage from './ReportPage.vue'

const props = defineProps<{
  status: 'loading' | 'failed' | 'ready'
  month: string | null
  vitals: MonthlyReport['vitals']
  colors: ReportColors
}>()
const emit = defineEmits<{ retry: [] }>()

const option = computed(() => {
  const v = props.vitals
  return v ? vitalsBar(v, METRICS.filter((m) => v[m]), props.colors) : null
})
</script>

<style scoped>
.vitals__chart {
  height: 380px;
}

.vitals__blocks {
  display: flex;
  justify-content: space-between;
  margin-top: 18px;
}

.vitals__block {
  width: 186px;
}

.vitals__name {
  margin: 0;
  padding-bottom: 12px;
  border-bottom: 2px solid var(--report-chart-1);
  font-size: 14px;
  font-weight: 400;
}

.vitals__list {
  margin: 10px 0 0;
}

.vitals__list div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.vitals__list dt {
  font-size: 14px;
}

.vitals__list dd {
  margin: 0;
  font-size: 28px;
  line-height: 1.3;
}

.v1 { color: var(--report-chart-1); }
.v2 { color: var(--report-chart-2); }
.v3 { color: var(--report-chart-3); }

.vitals__none {
  margin: 30px 0 0;
  color: var(--report-text-dim);
}
</style>
