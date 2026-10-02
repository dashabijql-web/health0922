<template>
  <div class="dashboard">
    <!-- 顶部横幅：定位数据过期、刷新失败（docs/05 第二节）。放在页头标题和导航之间的空处，不挡数字 -->
    <div v-if="page.staleBanner.value || page.failedBanner.value" class="dashboard__banners">
      <NoticeBanner v-if="page.failedBanner.value" type="danger" :message="page.failedBanner.value" />
      <NoticeBanner v-if="page.staleBanner.value" type="warn" :message="page.staleBanner.value" />
    </div>

    <section class="area area--main">
      <DisplayStage
        v-if="page.mode.value === 'display'"
        :overview="page.overview.data.value"
        @export="page.exportOpen.value = true"
        @monthly="page.monthlyOpen.value = true"
        @toggle-mode="page.toggleMode"
      />
      <MapStage v-else :locate="page.locate.value" @toggle-mode="page.toggleMode" />
    </section>

    <AlertTilesPanel
      class="area area--vitals"
      :data="page.overview.data.value"
      :loading="page.overview.loading.value"
      :failed-at="page.overview.failedAt.value"
      :error="page.overview.error.value"
      @select="(c) => (page.alertCategory.value = c)"
      @open-device-events="page.deviceEventsOpen.value = true"
    />
    <KeyPersonsPanel
      class="area area--key"
      :data="page.keyPersons.data.value"
      :loading="page.keyPersons.loading.value"
      :failed-at="page.keyPersons.failedAt.value"
      :error="page.keyPersons.error.value"
    />
    <HeadcountCurvePanel
      class="area area--curve"
      :data="page.series.data.value"
      :loading="page.series.loading.value"
      :failed-at="page.series.failedAt.value"
      :error="page.series.error.value"
      :now-ms="page.seriesNow.value"
    />
    <PeopleInfoPanel
      class="area area--people"
      :headcount="page.headcount"
      :persons="page.persons"
      :page="page.personPage.value"
      :page-size="PERSON_PAGE_SIZE"
      :map-mode="page.mode.value === 'map'"
      @page="page.goPersonPage"
      @export="page.exportOpen.value = true"
      @locate="page.locatePerson"
    />
    <StepsRankPanel
      class="area area--steps"
      :data="page.steps.data.value"
      :loading="page.steps.loading.value"
      :failed-at="page.steps.failedAt.value"
      :error="page.steps.error.value"
    />

    <!-- 角落常驻：定位数据时间、手表数据时间 -->
    <p class="dashboard__times">
      定位数据 <span class="num">{{ page.positioningTime.value || '暂无数据' }}</span>
      <span class="dashboard__sep">·</span>
      手表数据 <span class="num">{{ page.watchTime.value || '暂无数据' }}</span>
    </p>

    <WearExportDialog :open="page.exportOpen.value" @close="page.exportOpen.value = false" />
    <DeviceEventsDialog :open="page.deviceEventsOpen.value" @close="page.deviceEventsOpen.value = false" />
    <AlertPersonsDialog :category="page.alertCategory.value" @close="page.alertCategory.value = null" />
    <MonthlyReportDialog v-if="page.monthlyOpen.value" @close="page.monthlyOpen.value = false" />
  </div>
</template>

<script setup lang="ts">
// 动态数据页（docs/05 第四节）。布局按截图 1 换算到 1920×1080 画布（docs/09 第三节），坐标写在下面的样式里。
import { defineAsyncComponent } from 'vue'
import NoticeBanner from '@/components/NoticeBanner.vue'
import WearExportDialog from '@/components/WearExportDialog.vue'
import AlertPersonsDialog from './components/AlertPersonsDialog.vue'
import AlertTilesPanel from './components/AlertTilesPanel.vue'
import DeviceEventsDialog from './components/DeviceEventsDialog.vue'
import DisplayStage from './components/DisplayStage.vue'
import HeadcountCurvePanel from './components/HeadcountCurvePanel.vue'
import KeyPersonsPanel from './components/KeyPersonsPanel.vue'
import PeopleInfoPanel from './components/PeopleInfoPanel.vue'
import StepsRankPanel from './components/StepsRankPanel.vue'
import { PERSON_PAGE_SIZE, useDashboardPage } from './use-dashboard-page'

// 地图模式用到 OpenLayers、proj4，体积大，切到地图模式时才加载
const MapStage = defineAsyncComponent(() => import('./components/MapStage.vue'))
// 月度汇总用到 page-flip 和饼图，打开时才加载
const MonthlyReportDialog = defineAsyncComponent(() => import('@/views/report/MonthlyReportDialog.vue'))

const page = useDashboardPage()
</script>

<style scoped>
.dashboard {
  position: absolute;
  inset: 0;
}

/* 参考坐标 (x1,y1)-(x2,y2) 换算：横向 ×0.96，纵向 ×1.116（docs/09 第一节） */
.area {
  position: absolute;
}

/* 中间主区域 (52,145)-(1460,652) */
.area--main {
  left: 50px;
  top: 162px;
  width: 1352px;
  height: 566px;
  border: 1px solid var(--border-faint);
  background: radial-gradient(ellipse at 52% 45%, var(--stage-glow), transparent 65%), var(--stage-bg);
}

/* 右上：体征信息展示 (1480,133)-(1938,310) */
.area--vitals {
  left: 1421px;
  top: 148px;
  width: 439px;
  height: 198px;
}

/* 右中：重点人员列表 (1480,327)-(1938,660) */
.area--key {
  left: 1421px;
  top: 365px;
  width: 439px;
  height: 372px;
}

/* 底左：上线职工曲线 (48,668)-(600,920) */
.area--curve {
  left: 46px;
  top: 745px;
  width: 530px;
  height: 282px;
}

/* 底中：人员信息展示 (608,668)-(1460,920) */
.area--people {
  left: 584px;
  top: 745px;
  width: 818px;
  height: 282px;
}

/* 底右：实时运动量 (1480,668)-(1938,920) */
.area--steps {
  left: 1421px;
  top: 745px;
  width: 439px;
  height: 282px;
}

.dashboard__banners {
  position: absolute;
  left: 812px;
  top: 52px;
  z-index: 20;
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 560px;
}

.dashboard__times {
  position: absolute;
  left: 50px;
  top: 1031px;
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.dashboard__times .num {
  color: var(--text-primary);
}

.dashboard__sep {
  margin: 0 8px;
}
</style>
