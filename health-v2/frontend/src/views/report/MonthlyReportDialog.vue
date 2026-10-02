<template>
  <div ref="root" class="report" role="dialog" aria-modal="true" aria-label="职工健康数据汇总报告" tabindex="-1"
       @mousedown.self="emit('close')">
    <div class="report__bar">
      <label class="report__month">
        <span>月份</span>
        <select :value="p.month.value ?? ''" :disabled="p.months.value.length === 0" aria-label="选择月份"
                @change="p.selectMonth(($event.target as HTMLSelectElement).value)">
          <option v-if="p.months.value.length === 0" value="">暂无数据</option>
          <option v-for="m in p.months.value" :key="m" :value="m">{{ monthLabel(m) }}</option>
        </select>
      </label>
      <span class="report__pageno" aria-live="polite">{{ p.pageText.value }}</span>
      <span class="report__hint">点击或拖动页角翻页 · ← → 翻页 · Esc 关闭</span>
    </div>
    <button type="button" class="report__close" aria-label="关闭" @click="emit('close')">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 7l10 10M17 7L7 17" /></svg>
    </button>

    <div class="report__stage" @mousedown.self="emit('close')">
      <MonthlyReportBook ref="book" :width="PAGE_W" :height="PAGE_H" @flip="(n) => (p.page.value = n)">
        <div class="book-page" data-density="hard">
          <CoverPage :month="p.month.value" :generated-at="r?.generatedAt ?? null" />
        </div>
        <div class="book-page"><IntroPage /></div>
        <div class="book-page"><CatalogPage @go="p.goTo" /></div>
        <div class="book-page">
          <SharePage :no="3" section="概述" unit="种" :status="s" :month="m" :colors="colors" @retry="p.retry"
                     :data="r?.overview ? { shares: r.overview.byJobKind, total: r.overview.workerCount } : null">
            <template #summary>
              本报告数据汇总时间范围为{{ monthLabel(m) }}，涉及职工 <b>{{ r?.overview?.workerCount }}</b> 人，
              涵盖工种 <b>{{ r?.overview?.jobKindCount }}</b> 个。
            </template>
          </SharePage>
        </div>
        <div class="book-page">
          <SharePage :no="4" section="智能手表使用汇总" unit="个" :status="s" :month="m" :colors="colors"
                     @retry="p.retry"
                     :data="r?.watchUsage ? { shares: r.watchUsage.byDept, total: r.watchUsage.userCount } : null">
            <template #summary>
              {{ monthLabel(m) }}，共有 <b>{{ r?.watchUsage?.userCount }}</b> 人使用智能手表进行了动态体征监测，
              涉及 <b>{{ r?.watchUsage?.deptCount }}</b> 个部门。
            </template>
          </SharePage>
        </div>
        <div class="book-page">
          <VitalsPage :status="s" :month="m" :vitals="r?.vitals ?? null" :colors="colors" @retry="p.retry" />
        </div>
        <div class="book-page">
          <AlertsPage :status="s" :month="m" :alerts="r?.alerts ?? null" :colors="colors" @retry="p.retry" />
        </div>
        <div class="book-page"><RulesPage :status="s" :rules="r?.rules ?? []" @retry="p.retry" /></div>
        <div v-for="(metric, i) in METRICS" :key="metric" class="book-page">
          <StabilityPage :no="8 + i" :metric="metric" :status="s" :month="m" :data="r?.stability?.[metric] ?? null"
                         :colors="colors" @retry="p.retry" />
        </div>
        <div class="book-page">
          <StepsPage :no="11" low :status="s" :month="m" :steps="r?.steps ?? null" @retry="p.retry" />
        </div>
        <div class="book-page">
          <StepsPage :no="12" :low="false" :status="s" :month="m" :steps="r?.steps ?? null" @retry="p.retry" />
        </div>
        <div class="book-page">
          <RiskPage :status="s" :month="m" :risk="r?.risk ?? null" :rules="r?.rules ?? []" @retry="p.retry" />
        </div>
        <div class="book-page" data-density="hard"><div class="report__back" aria-label="封底"></div></div>
      </MonthlyReportBook>
    </div>
  </div>
</template>

<script setup lang="ts">
// 月度汇总（docs/07 第二部分，截图 2、5–12）：全屏弹出一本书，封面 + 13 页 + 封底。
// 画在 1920×1080 画布里（随画布缩放），页面变暗、书居中，单页 720×920（宽高比约 0.78）；右上角黄色圆形关闭按钮，
// 顶部月份选择（默认上个月，只列有数据的月份）。页面是 HTML，图表用 ECharts。
// 这个组件和 page-flip、饼图都在单独的分包里，打开月报时才下载（DashboardView、ArchiveListView 用 defineAsyncComponent）。
import { computed } from 'vue'
import MonthlyReportBook from '@/components/MonthlyReportBook.vue'
import { readReportColors } from './report-charts'
import { METRICS, monthLabel } from './report-format'
import { useMonthlyReportPage } from './use-monthly-report-page'
import AlertsPage from './pages/AlertsPage.vue'
import CatalogPage from './pages/CatalogPage.vue'
import CoverPage from './pages/CoverPage.vue'
import IntroPage from './pages/IntroPage.vue'
import RiskPage from './pages/RiskPage.vue'
import RulesPage from './pages/RulesPage.vue'
import SharePage from './pages/SharePage.vue'
import StabilityPage from './pages/StabilityPage.vue'
import StepsPage from './pages/StepsPage.vue'
import VitalsPage from './pages/VitalsPage.vue'
import './report.css'

const emit = defineEmits<{ close: [] }>()

const PAGE_W = 720
const PAGE_H = 920

const p = useMonthlyReportPage(() => emit('close'))
const colors = readReportColors()

// 模板里用得多，取短名
const r = computed(() => p.report.value)
const m = computed(() => p.report.value?.month ?? p.month.value)
const s = computed(() => p.status.value)
</script>

<style scoped>
.report {
  position: absolute;
  inset: 0;
  z-index: 60;
  background: var(--report-mask);
  outline: none;
}

/* 顶部一行：月份选择、当前页、操作提示（书的上方，和书左边对齐）。
   书的那一层铺满整个弹层（点空白处关闭），这一行和关闭按钮要压在它上面才点得到 */
.report__bar,
.report__close {
  z-index: 1;
}

.report__bar {
  position: absolute;
  left: 240px;
  top: 22px;
  display: flex;
  align-items: center;
  gap: 22px;
  height: 36px;
  color: var(--text-primary);
  font-size: 16px;
}

.report__month {
  display: flex;
  align-items: center;
  gap: 10px;
}

.report__month select {
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--modal-border);
  border-radius: 4px;
  background: var(--modal-bg);
  color: var(--modal-text);
  font-size: 16px;
  cursor: pointer;
}

.report__pageno {
  min-width: 90px;
  color: var(--accent);
  font-family: var(--font-number);
  font-size: 15px;
}

.report__hint {
  color: var(--text-secondary);
  font-size: 14px;
}

/* 黄色圆形关闭按钮，在书的右上方（截图 2） */
.report__close {
  position: absolute;
  left: 1662px;
  top: 18px;
  width: 42px;
  height: 42px;
  padding: 0;
  border: 3px solid var(--report-close-text);
  border-radius: 50%;
  background: var(--report-close);
  color: var(--report-close-text);
  box-shadow: 0 0 0 2px var(--report-close);
  cursor: pointer;
}

.report__close svg {
  width: 22px;
  height: 22px;
  fill: none;
  stroke: currentColor;
  stroke-width: 3;
  stroke-linecap: round;
}

.report__close:hover {
  filter: brightness(1.08);
}

/* 书：画布宽 1920，两页 1440，左右各留 240；上下 (1080 − 920) / 2 */
.report__stage {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  padding-top: 60px;
}

.book-page {
  overflow: hidden;
  background: var(--report-paper);
}

.report__back {
  width: 100%;
  height: 100%;
  background:
    radial-gradient(circle at -20% 105%, var(--report-cover-arc) 0 48%, transparent 48.3%),
    radial-gradient(circle at 120% -10%, var(--report-cover-arc-2) 0 36%, transparent 36.3%),
    linear-gradient(165deg, var(--report-back-top), var(--report-back-bottom));
}
</style>
