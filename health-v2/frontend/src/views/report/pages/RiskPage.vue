<template>
  <ReportPage :no="13" title="结论" section="本月风险职工评估" :status="status" :empty="!risk" :month="month"
              @retry="emit('retry')">
    <template v-if="risk">
      <template v-if="risk.total > 0">
        <p class="report-text">
          {{ monthLabel(month) }}，经系统汇总分析，本月需要重点关注的职工共 <b>{{ risk.total }}</b> 人：
        </p>
        <table class="report-table risk__table">
          <colgroup><col style="width: 12%" /><col style="width: 10%" /><col style="width: 15%" /><col style="width: 13%" /><col /></colgroup>
          <thead>
            <tr><th>姓名</th><th>卡号</th><th>部门</th><th class="report-num">告警次数</th><th>主要问题</th></tr>
          </thead>
          <tbody>
            <tr v-for="p in risk.list" :key="p.cardCode">
              <td :title="p.name ?? ''">{{ p.name ?? '未录入' }}</td>
              <td :title="p.cardCode">{{ cardNo(p.cardCode) }}</td>
              <td :class="{ missing: !p.dept }" :title="p.dept ?? ''">{{ p.dept ?? '未录入' }}</td>
              <td class="report-num">{{ p.alertCount }}</td>
              <td class="risk__problems" :title="riskProblems(p)"><span>{{ riskProblems(p) }}</span></td>
            </tr>
          </tbody>
        </table>
        <p v-if="risk.total > risk.list.length" class="report-note">
          共 {{ risk.total }} 人，这里列出告警次数最多的 {{ risk.list.length }} 人（同样多时"不稳定"项多的在前），主要问题悬停可看全。
        </p>
      </template>
      <template v-else>
        <p class="report-text">{{ monthLabel(month) }}，经系统汇总分析，本月暂无需要重点关注的职工。</p>
        <ReportEmpty text="本月暂无需要重点关注的职工" />
      </template>
      <p class="report-note risk__rule">
        列入条件（满足任意一条）：① 本月六类告警事件达到所属岗位类别的条数（{{ riskCountText }}；低电、脱落等设备事件不算）；
        ② 心率、体温、血氧任一项被评为"不稳定"。这只是提示需要关注，不是诊断结论。
      </p>
    </template>
  </ReportPage>
</template>

<script setup lang="ts">
// 第 13 页：结论——本月风险职工评估（docs/07 第二部分"三、计算口径"）。表格：姓名、卡号、部门、告警次数、主要问题。
// 没有符合条件的显示"本月暂无需要重点关注的职工"；这个月完全没有数据时显示"暂无数据"。
import { computed } from 'vue'
import type { GroupRule, MonthlyReport } from '@/api/report'
import { cardNo } from '@/utils/format'
import ReportEmpty from '../art/ReportEmpty.vue'
import { monthLabel, riskProblems } from '../report-format'
import ReportPage from './ReportPage.vue'

const props = defineProps<{
  status: 'loading' | 'failed' | 'ready'
  month: string | null
  risk: MonthlyReport['risk']
  rules: GroupRule[]
}>()
const emit = defineEmits<{ retry: [] }>()

/** "默认 3 条" 或 "默认 3 条、重体力 5 条" */
const riskCountText = computed(() => props.rules.map((g) => `${g.name} ${g.riskEventCount} 条`).join('、'))
</script>

<style scoped>
.risk__table {
  margin-top: 6px;
}

/* 主要问题可能很长：最多两行，完整内容悬停看 */
.risk__problems {
  padding-top: 4px;
  padding-bottom: 4px;
  white-space: normal;
  line-height: 1.4;
  font-size: 12.5px;
}

.risk__problems span {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
}

.risk__rule {
  position: absolute;
  left: 40px;
  right: 40px;
  bottom: 8px;
}
</style>
