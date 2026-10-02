<template>
  <ReportPage :no="7" title="职工健康评估" section="体征稳定状态评估" :status="status" @retry="emit('retry')">
    <div class="rules__box">
      体征稳定状态指一个人的生理指标（如心率、体温、血氧）在活动状况大致相同的情况下，处于一个相对稳定的状态。
      体征稳定状态是了解个人健康状况的参考之一：某项指标经常越出正常范围，说明身体的稳态可能有所下降，
      需要留意相关的健康风险，建议由医生进一步判断。
    </div>

    <h4 class="report-sub">评估规则</h4>
    <ol class="rules__list">
      <li><b>正常率</b> = 本月落在正常范围内的测量次数 ÷ 本月测量总次数。正常范围按本人所属岗位类别的预警阈值，和实时告警用同一套。</li>
      <li>正常率达到"稳定"线为<b>稳定</b>，低于"不稳定"线为<b>不稳定</b>，介于两者之间为<b>波动</b>。</li>
      <li>本月某项指标有数据的天数少于所属岗位类别的最少天数时，这一项不参与评估，页面注明人数。</li>
      <li>按生成报告时所属的岗位类别评估；月中换了工种的，整月按新类别算。</li>
    </ol>

    <table v-if="rules.length" class="report-table rules__table">
      <colgroup><col style="width: 22%" /><col style="width: 12%" /><col /><col style="width: 17%" /><col style="width: 17%" /></colgroup>
      <thead><tr><th>岗位类别</th><th>指标</th><th>正常范围</th><th class="report-num">稳定</th><th class="report-num">不稳定</th></tr></thead>
      <tbody>
        <template v-for="g in shown" :key="g.code">
          <tr v-for="(m, i) in METRICS" :key="`${g.code}-${m}`" :class="{ rules__first: i === 0 }">
            <td v-if="i === 0" :rowspan="METRICS.length" class="rules__group">
              <span :title="g.name">{{ g.name }}</span>
              <small>至少 {{ g.minEvalDays }} 天</small>
            </td>
            <td>{{ METRIC_NAME[m].name }}</td>
            <td>{{ rangeText(m, g.metrics[m]) }} <span class="rules__unit">{{ METRIC_NAME[m].unit }}</span></td>
            <td class="report-num">≥ {{ g.metrics[m].stablePct }}%</td>
            <td class="report-num">&lt; {{ g.metrics[m].unstablePct }}%</td>
          </tr>
        </template>
      </tbody>
    </table>
    <p v-if="rules.length > MAX_GROUPS" class="report-note">共 {{ rules.length }} 个岗位类别，这里列出前 {{ MAX_GROUPS }} 个。</p>
    <p class="report-note">注：参数由矿上医务人员确定，可以按岗位类别分别配置；没有配置的岗位类别用"默认"的参数。</p>
  </ReportPage>
</template>

<script setup lang="ts">
// 第 7 页：体征稳定状态评估的概念说明和评估规则（截图 9 左页）。规则表是后端返回的各岗位类别实际参数
// （已按"没配就用默认类别的"补齐），任何月份都显示。
import { computed } from 'vue'
import type { GroupRule } from '@/api/report'
import { METRICS, METRIC_NAME, rangeText } from '../report-format'
import ReportPage from './ReportPage.vue'

const props = defineProps<{ status: 'loading' | 'failed' | 'ready'; rules: GroupRule[] }>()
const emit = defineEmits<{ retry: [] }>()

/** 一页放得下 4 个岗位类别（每个 3 行） */
const MAX_GROUPS = 4
const shown = computed(() => props.rules.slice(0, MAX_GROUPS))
</script>

<style scoped>
/* 概念说明：浅蓝底、青色描边的框 */
.rules__box {
  margin-top: 8px;
  padding: 16px 20px;
  border: 2px solid var(--report-box-border);
  background: var(--report-box-bg);
  font-size: 14px;
  line-height: 1.9;
  text-indent: 2em;
}

.rules__list {
  margin: 0;
  padding-left: 20px;
  font-size: 13.5px;
  line-height: 1.75;
}

.rules__list b {
  color: var(--report-blue-deep);
}

.rules__table {
  margin-top: 10px;
}

.rules__group {
  vertical-align: middle;
  border-right: 1px solid var(--report-table-line);
  white-space: normal;
}

.rules__group span {
  display: block;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rules__group small {
  color: var(--report-text-dim);
  font-size: 12px;
}

.rules__unit {
  color: var(--report-text-dim);
  font-size: 12px;
}
</style>
