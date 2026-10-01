<template>
  <div class="archive">
    <!-- 筛选条 (80,150)-(1900,210) -->
    <form class="archive__bar" role="search" @submit.prevent="p.search">
      <ScreenSelect v-model="p.draft.dept" class="archive__dept" :options="p.filters.value.depts"
                    placeholder="部门" label="部门" all-text="全部部门" />
      <ScreenSelect v-model="p.draft.jobKind" class="archive__job" :options="p.filters.value.jobKinds"
                    placeholder="请选择或者输入工种" label="工种" all-text="全部工种" filterable />
      <input v-model="p.draft.keyword" class="archive__keyword" type="search" maxlength="50"
             placeholder="请输入职工姓名/卡号进行查询" aria-label="姓名或卡号" />
      <button type="submit" class="pill pill--green archive__query">查询</button>
      <button type="button" class="pill pill--green archive__reset" @click="p.reset">重置</button>
      <button type="button" class="pill pill--blue archive__monthly" @click="p.openMonthly">健康数据汇总</button>
    </form>

    <!-- 卡片 4 × 3 -->
    <section class="archive__grid" aria-label="人员卡片" :aria-busy="p.loading.value">
      <template v-if="p.data.value === null && p.loading.value">
        <div v-for="i in PAGE_SIZE" :key="i" class="archive__skeleton" aria-hidden="true"></div>
      </template>
      <template v-else-if="p.data.value && p.data.value.list.length > 0">
        <PersonCard v-for="person in p.data.value.list" :key="person.cardCode" :person="person"
                    @open="p.openPerson" />
      </template>
    </section>
    <div v-if="p.data.value && p.data.value.list.length === 0" class="archive__empty">
      <EmptyState :text="p.applied.value.dept || p.applied.value.jobKind || p.applied.value.keyword
        ? '没有符合条件的人员' : '暂无数据'" />
    </div>
    <div v-else-if="p.data.value === null && p.failed.value" class="archive__empty">
      <EmptyState kind="error" />
    </div>

    <!-- 左右翻页 -->
    <button type="button" class="archive__arrow archive__arrow--prev" aria-label="上一页"
            :disabled="p.page.value <= 1" @click="p.go(-1)">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 5l-7 7 7 7" /></svg>
    </button>
    <button type="button" class="archive__arrow archive__arrow--next" aria-label="下一页"
            :disabled="p.page.value >= p.pages.value" @click="p.go(1)">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 5l7 7-7 7" /></svg>
    </button>

    <!-- 截图里没有：总人数和页码，方便核对（docs/09 第九节） -->
    <p v-if="p.data.value" class="archive__count" role="status">
      共 <span class="num">{{ p.data.value.total }}</span> 人 · 第 <span class="num">{{ p.page.value }}</span>
      / <span class="num">{{ p.pages.value }}</span> 页<template v-if="p.failed.value"> · 查询失败，显示的是上次的结果</template>
    </p>
  </div>
</template>

<script setup lang="ts">
// 健康档案（截图 3，docs/05 第六节、docs/09 第六节）：筛选 + 4×3 人员卡片 + 左右翻页。
// 只列绑定了手表的人（docs/00 第 8 项）。布局按截图坐标换算到 1920×1080 画布，坐标写在下面的样式里。
import EmptyState from '@/components/EmptyState.vue'
import PersonCard from '@/components/PersonCard.vue'
import ScreenSelect from '@/components/ScreenSelect.vue'
import { PAGE_SIZE, useArchiveListPage } from './use-archive-list-page'

const p = useArchiveListPage()
</script>

<style scoped>
/* 参考坐标 (x1,y1)-(x2,y2) 换算：横向 ×0.96，纵向 ×1.116（docs/09 第一节） */
.archive {
  position: absolute;
  inset: 0;
}

/* 筛选条 (80,150)-(1900,210)；里面的控件 y 158–202 */
.archive__bar {
  position: absolute;
  left: 77px;
  top: 167px;
  width: 1747px;
  height: 67px;
  margin: 0;
  border: 1px solid var(--filter-bar-border);
  border-radius: 34px;
  background: var(--filter-bar-bg);
}

.archive__bar > * {
  position: absolute;
  top: 8px;
  height: 49px;
}

/* (160,158)-(558,202) */
.archive__dept { left: 77px; width: 382px; }
/* (588,158)-(986,202) */
.archive__job { left: 487px; width: 382px; }
/* (1016,158)-(1414,202) */
.archive__keyword {
  left: 898px;
  width: 382px;
  padding: 0 24px 0 40px;
  border: 1px solid var(--filter-input-border);
  border-radius: 999px;
  outline: none;
  background: var(--filter-input-bg);
  box-shadow: 0 0 10px var(--filter-input-glow), inset 0 0 8px var(--filter-input-glow);
  color: var(--text-primary);
  font: inherit;
  font-size: 17px;
}

.archive__keyword::placeholder {
  color: var(--filter-placeholder);
}

.archive__keyword:focus-visible {
  border-color: var(--accent-soft);
}

/* (1441,158)-(1534,202)、(1560,158)-(1653,202)、(1680,158)-(1873,202) */
.archive__query { left: 1306px; width: 89px; }
.archive__reset { left: 1421px; width: 89px; }
.archive__monthly { left: 1536px; width: 185px; }

.pill {
  padding: 0;
  border: 0;
  border-radius: 999px;
  color: var(--text-bright);
  font: inherit;
  font-size: 24px;
  cursor: pointer;
}

.pill--green {
  background: linear-gradient(180deg, var(--pill-green-top), var(--pill-green-bottom));
}

.pill--blue {
  background: linear-gradient(180deg, var(--pill-blue-top), var(--pill-blue-bottom));
}

.pill:hover {
  filter: brightness(1.1);
}

.pill:focus-visible {
  outline: 2px solid var(--text-bright);
  outline-offset: 2px;
}

/* 卡片：列 x 208/616/1024/1432，行 y 245/486/727，每张 344×195 */
.archive__grid {
  position: absolute;
  left: 200px;
  top: 273px;
  display: grid;
  grid-template-columns: repeat(4, 330px);
  grid-auto-rows: 218px;
  column-gap: 61px;
  row-gap: 51px;
}

.archive__skeleton {
  width: 330px;
  height: 218px;
  border: 1px solid var(--border-faint);
  background: linear-gradient(90deg, var(--accent-bg), var(--border-faint), var(--accent-bg));
  background-size: 200% 100%;
  animation: skeleton 1.4s ease-in-out infinite;
}

@keyframes skeleton {
  from { background-position: 200% 0; }
  to { background-position: -200% 0; }
}

.archive__empty {
  position: absolute;
  left: 200px;
  top: 273px;
  width: 1505px;
  height: 756px;
}

/* 左右翻页 (113,548)-(168,615)、(1818,548)-(1873,615)：绿色半圆形按钮，白色箭头 */
.archive__arrow {
  position: absolute;
  top: 612px;
  width: 53px;
  height: 75px;
  padding: 0;
  border: 0;
  background: linear-gradient(90deg, var(--page-arrow-top), var(--page-arrow-bottom));
  color: var(--text-bright);
  cursor: pointer;
}

.archive__arrow--prev {
  left: 108px;
  border-radius: 38px 4px 4px 38px / 38px 4px 4px 38px;
}

.archive__arrow--next {
  left: 1745px;
  border-radius: 4px 38px 38px 4px / 4px 38px 38px 4px;
  background: linear-gradient(270deg, var(--page-arrow-top), var(--page-arrow-bottom));
}

.archive__arrow svg {
  width: 30px;
  height: 30px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2.4;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.archive__arrow:disabled {
  opacity: 0.35;
  cursor: default;
}

.archive__arrow:not(:disabled):hover {
  filter: brightness(1.15);
}

.archive__count {
  position: absolute;
  left: 0;
  right: 0;
  top: 1036px;
  margin: 0;
  text-align: center;
  font-size: 14px;
  color: var(--text-secondary);
}

.archive__count .num {
  color: var(--text-primary);
}

@media (prefers-reduced-motion: reduce) {
  .archive__skeleton { animation: none; }
}
</style>
