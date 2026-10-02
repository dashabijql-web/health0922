<template>
  <ReportPage :no="no" title="职工健康评估" :section="low ? '运动量较小' : '运动量较大'" :status="status"
              :empty="!steps" :month="month" @retry="emit('retry')">
    <template v-if="steps">
      <p class="report-text">
        {{ monthLabel(month) }}，<template v-if="!low">通过对职工健康监测数据的统计，</template>运动量较{{ low ? '小' : '大' }}职工排名如下：
      </p>
      <ol v-if="list.length" class="steps__list">
        <li v-for="r in list" :key="r.cardCode" class="steps__card" :data-card="r.cardCode">
          <DefaultAvatar class="steps__avatar" />
          <span class="steps__rank">{{ rankText(r.rank) }}</span>
          <span class="steps__name" :title="r.name ?? ''">{{ r.name ?? '未录入' }}</span>
          <span class="steps__no" :title="r.cardCode">{{ cardNo(r.cardCode) }}</span>
          <span :class="['steps__dept', { 'steps__dept--missing': !r.dept }]" :title="r.dept ?? ''">{{ r.dept ?? '未录入' }}</span>
          <span class="steps__value" :title="`本月 ${r.days} 天有步数记录，日均 ${r.avgSteps} 步`">{{ r.avgSteps }}</span>
          <span class="steps__track" aria-hidden="true">
            <i :class="`steps__bar steps__bar--${r.rank}`" :style="{ width: `${barWidth(r.avgSteps)}%` }"></i>
          </span>
        </li>
      </ol>
      <ReportEmpty v-else :text="`没有人有 ${steps.minDays} 天以上的步数记录`" />
      <p class="report-note">
        注：按日均步数（本月总步数 ÷ 有步数记录的天数）从{{ low ? '小到大' : '大到小' }}排名前 10 名；
        只统计有 {{ steps.minDays }} 天以上记录的 {{ steps.ranked }} 人<template v-if="steps.excluded > 0">
        （另有 {{ steps.excluded }} 人不足 {{ steps.minDays }} 天，未参与排名）</template>。两页的进度条用同一把尺，以运动量最大的第 1 名为满格。
      </p>
    </template>
  </ReportPage>
</template>

<script setup lang="ts">
// 第 11、12 页：运动量较小、较大前 10 名（截图 11）。每行一个浅色圆角卡片：头像、橙色名次、姓名、卡号、部门、
// 青绿色的日均步数，卡片下缘一条彩色进度条（第 1 名橙红，之后依次橙、黄、蓝、青、绿、深绿……）。
import { computed } from 'vue'
import DefaultAvatar from '@/components/DefaultAvatar.vue'
import type { MonthlyReport } from '@/api/report'
import { cardNo } from '@/utils/format'
import ReportEmpty from '../art/ReportEmpty.vue'
import { monthLabel, rankText } from '../report-format'
import ReportPage from './ReportPage.vue'

const props = defineProps<{
  no: number
  /** true 是"运动量较小"，false 是"运动量较大" */
  low: boolean
  status: 'loading' | 'failed' | 'ready'
  month: string | null
  steps: MonthlyReport['steps']
}>()
const emit = defineEmits<{ retry: [] }>()

const list = computed(() => (props.steps ? (props.low ? props.steps.low : props.steps.high) : []))
/** 满格 = 运动量较大的第 1 名 */
const scale = computed(() => props.steps?.high[0]?.avgSteps ?? 0)

function barWidth(v: number): number {
  return scale.value > 0 ? Math.max(1, (v / scale.value) * 100) : 0
}
</script>

<style scoped>
.steps__list {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
}

.steps__card {
  position: relative;
  display: grid;
  grid-template-columns: 50px 46px 94px 76px 1fr 104px;
  align-items: center;
  height: 47px;
  margin-bottom: 10px;
  padding: 0 14px 0 10px;
  border: 1px solid var(--report-card-border);
  border-radius: 4px;
  background: var(--report-paper);
  box-shadow: 0 2px 6px var(--report-card-shadow);
  font-size: 14px;
}

/* 头像沿用大屏的默认头像，底色换成截图里的浅蓝 */
.steps__avatar {
  width: 36px;
  height: 36px;
  --avatar-disk: var(--report-avatar-disk);
  --avatar-disk-edge: var(--report-avatar-disk-edge);
}

.steps__rank {
  color: var(--report-rank);
  font-size: 17px;
  font-weight: 700;
}

.steps__name,
.steps__dept {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.steps__no {
  color: var(--report-text-dim);
  font-variant-numeric: tabular-nums;
}

.steps__dept--missing {
  color: var(--report-text-dim);
}

.steps__value {
  text-align: right;
  color: var(--report-value);
  font-size: 18px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.steps__track {
  position: absolute;
  left: 60px;
  right: 14px;
  bottom: 6px;
  height: 6px;
  border-radius: 3px;
  background: var(--report-track);
}

.steps__bar {
  display: block;
  height: 100%;
  border-radius: 3px;
}

.steps__bar--1 { background: var(--report-bar-1); }
.steps__bar--2 { background: var(--report-bar-2); }
.steps__bar--3 { background: var(--report-bar-3); }
.steps__bar--4 { background: var(--report-bar-4); }
.steps__bar--5 { background: var(--report-bar-5); }
.steps__bar--6 { background: var(--report-bar-6); }
.steps__bar--7 { background: var(--report-bar-7); }
.steps__bar--8 { background: var(--report-bar-8); }
.steps__bar--9 { background: var(--report-bar-9); }
.steps__bar--10 { background: var(--report-bar-10); }
</style>
