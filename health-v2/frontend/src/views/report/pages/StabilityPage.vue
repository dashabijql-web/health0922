<template>
  <ReportPage :no="no" title="职工健康评估" :section="`${METRIC_NAME[metric].name}稳定状态评估`" :status="status"
              :empty="!data" :month="month" @retry="emit('retry')">
    <template v-if="data">
      <p class="report-text">
        {{ monthLabel(month) }}，共 <b>{{ data.persons }}</b> 人有{{ METRIC_NAME[metric].name }}数据，
        其中 <b>{{ evaluated }}</b> 人参与评估：稳定 {{ data.stable }} 人、波动 {{ data.fluctuating }} 人、
        不稳定 {{ data.unstable }} 人。<template v-if="data.notEvaluated > 0">
          有 <b>{{ data.notEvaluated }}</b> 人因数据不足（有数据的天数少于所属岗位类别的最少天数）未参与评估。</template>
      </p>
      <template v-if="evaluated > 0">
        <EChart v-if="option" class="report-chart stability__pie" :option="option"
                :label="`${METRIC_NAME[metric].name}稳定状态饼图`" />
        <h4 class="report-sub">不稳定人员</h4>
        <table v-if="data.unstableList.length" class="report-table">
          <colgroup>
            <col style="width: 15%" /><col style="width: 21%" /><col style="width: 16%" />
            <col style="width: 16%" /><col /><col />
          </colgroup>
          <thead>
            <tr><th>姓名</th><th>部门</th><th>岗位类别</th><th class="report-num">正常率</th>
              <th class="report-num">最小值</th><th class="report-num">最大值</th></tr>
          </thead>
          <tbody>
            <tr v-for="u in data.unstableList" :key="u.cardCode">
              <td :title="`卡号 ${cardNo(u.cardCode)}（${u.cardCode}）`">{{ u.name ?? '未录入' }}</td>
              <td :class="{ missing: !u.dept }" :title="u.dept ?? ''">{{ u.dept ?? '未录入' }}</td>
              <td :title="u.groupName">{{ u.groupName }}</td>
              <td class="report-num danger">{{ u.normalPct.toFixed(1) }}%</td>
              <td class="report-num">{{ vitalText(metric, u.min) }}</td>
              <td class="report-num">{{ vitalText(metric, u.max) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="report-text stability__none">本月没有被评为"不稳定"的职工。</p>
        <p v-if="data.unstable > data.unstableList.length" class="report-note">
          共 {{ data.unstable }} 人不稳定，这里列出正常率最低的 {{ data.unstableList.length }} 人。
        </p>
      </template>
      <ReportEmpty v-else text="没有人达到评估所需的天数" />
    </template>
  </ReportPage>
</template>

<script setup lang="ts">
// 第 8–10 页：心率、体温、血氧的稳定状态评估（docs/07 第二部分"三、计算口径"）：三种状态的人数（饼图）、
// "不稳定"人员表（前 10 人）。样本不足的人不参与评估，正文注明人数。
import { computed } from 'vue'
import EChart from '@/components/EChart.vue'
import type { Stability, StabilityMetric } from '@/api/report'
import { cardNo } from '@/utils/format'
import ReportEmpty from '../art/ReportEmpty.vue'
import { stabilityPie, type ReportColors } from '../report-charts'
import { METRIC_NAME, monthLabel, vitalText } from '../report-format'
import ReportPage from './ReportPage.vue'

const props = defineProps<{
  no: number
  metric: StabilityMetric
  status: 'loading' | 'failed' | 'ready'
  month: string | null
  data: Stability | null
  colors: ReportColors
}>()
const emit = defineEmits<{ retry: [] }>()

const evaluated = computed(() => (props.data ? props.data.persons - props.data.notEvaluated : 0))
const option = computed(() => (props.data ? stabilityPie(props.data, props.colors) : null))
</script>

<style scoped>
.stability__pie {
  height: 280px;
}

.stability__none {
  margin-top: 8px;
  color: var(--report-text-dim);
}
</style>
