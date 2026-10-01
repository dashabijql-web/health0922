<template>
  <div class="portal">
    <div v-if="page.staleBanner.value || page.failedBanner.value" class="portal__banners">
      <NoticeBanner v-if="page.failedBanner.value" type="danger" :message="page.failedBanner.value" />
      <NoticeBanner v-if="page.staleBanner.value" type="warn" :message="page.staleBanner.value" />
    </div>

    <!-- 中央大圆环 + 预渲染动画（tools/hero-render，scene=portal） -->
    <div class="ring" aria-hidden="true">
      <HeroVideo class="ring__video" :src="heroVideo" :poster="heroPoster" />
      <svg class="ring__deco decor" viewBox="0 0 614 614">
        <circle cx="307" cy="307" r="300" class="ring__outer" />
        <circle cx="307" cy="307" r="286" class="ring__inner" />
        <g class="ring__spin">
          <circle cx="307" cy="307" r="274" class="ring__dash" />
        </g>
        <g class="ring__spin ring__spin--rev">
          <path d="M 307 21 A 286 286 0 0 1 517 113" class="ring__arc" />
          <path d="M 97 501 A 286 286 0 0 0 307 593" class="ring__arc" />
          <path d="M 593 307 A 286 286 0 0 1 560 440" class="ring__arc ring__arc--thin" />
        </g>
      </svg>
    </div>

    <MetricPanel class="pos pos--hr" title="心率监测" icon="heart" side="left" :corners="[0, 100, 83, 0]"
                 :stat="vitals?.HEART_RATE ?? null" />
    <MetricPanel class="pos pos--temp" title="体温监测" icon="thermometer" side="left" :corners="[0, 86, 100, 0]"
                 :stat="vitals?.TEMPERATURE ?? null" />
    <MetricPanel class="pos pos--spo2" title="血氧监测" icon="drop" side="right" :corners="[0, 100, 100, 16]"
                 :stat="vitals?.SPO2 ?? null" />
    <MetricPanel class="pos pos--steps" title="运动步数" icon="runner" side="right" :corners="[14, 100, 100, 0]"
                 :stat="vitals?.STEPS ?? null" />

    <PortalButton class="pos pos--admin" side="left" @click="page.openAdmin">数据管理</PortalButton>
    <PortalButton class="pos pos--show" side="right" @click="page.openDashboard">数据展示</PortalButton>

    <RingCounter class="pos counter counter--1" label="重点监护人员" :value="s ? s.keyPersonCount : null" />
    <RingCounter class="pos counter counter--2" label="当日关注人员" :value="s ? s.todayWatchCount : null" />
    <RingCounter class="pos counter counter--3" label="职工数量" :value="s ? s.inWellCount : null" />
    <RingCounter class="pos counter counter--4" label="监护设备数量" :value="s ? s.deviceCount : null" />

    <p v-if="page.summary.error.value" class="portal__error">加载失败</p>
    <p class="portal__times">
      体征统计 <span class="num">{{ shortTime(s?.dataTime) || '暂无数据' }}</span>
      <span class="portal__sep">·</span>
      定位数据 <span class="num">{{ shortTime(s?.positioning.dataTime) || '暂无数据' }}</span>
    </p>
  </div>
</template>

<script setup lang="ts">
// 入口页（截图 16，docs/05 第三节、docs/09 第五节）：登录后默认进入；"数据展示"进入动态数据，"数据管理"建设中。
// 布局坐标按截图换算到 1920×1080 画布（docs/09 第一节），写在下面的样式里。
import { computed } from 'vue'
import heroPoster from '@/assets/hero/portal-poster.jpg'
import heroVideo from '@/assets/hero/portal.mp4'
import HeroVideo from '@/components/HeroVideo.vue'
import NoticeBanner from '@/components/NoticeBanner.vue'
import { shortTime } from '@/utils/format'
import MetricPanel from './components/MetricPanel.vue'
import PortalButton from './components/PortalButton.vue'
import RingCounter from './components/RingCounter.vue'
import { usePortalPage } from './use-portal-page'

const page = usePortalPage()
const s = computed(() => page.summary.data.value)
const vitals = computed(() => s.value?.vitals)
</script>

<style scoped>
.portal {
  position: absolute;
  inset: 0;
}

.pos {
  position: absolute;
}

/* 心率监测 (85,187)-(730,357) */
.pos--hr {
  left: 82px;
  top: 209px;
  width: 619px;
  height: 189px;
  --title-x: 259px;
  --title-top: 34px;
  --items-top: 112px;
  --items-left: 44px;
}

/* 体温监测 (85,578)-(700,748) */
.pos--temp {
  left: 82px;
  top: 645px;
  width: 590px;
  height: 190px;
  --title-x: 259px;
  --title-top: 18px;
  --items-top: 98px;
  --items-left: 44px;
}

/* 血氧监测 (1262,187)-(1910,357)，左右镜像 */
.pos--spo2 {
  left: 1212px;
  top: 209px;
  width: 622px;
  height: 189px;
  --title-x: 353px;
  --title-top: 34px;
  --items-top: 112px;
  --items-left: 142px;
}

/* 运动步数 (1290,578)-(1910,748) */
.pos--steps {
  left: 1238px;
  top: 645px;
  width: 596px;
  height: 190px;
  --title-x: 327px;
  --title-top: 18px;
  --items-top: 98px;
  --items-left: 116px;
}

/* 数据管理 (85,402)-(625,548)；数据展示 (1372,402)-(1912,548) */
.pos--admin {
  left: 82px;
  top: 449px;
  width: 518px;
  height: 163px;
}

.pos--show {
  left: 1317px;
  top: 449px;
  width: 519px;
  height: 163px;
}

/* 中央大圆环：中心 (995,480)、半径约 320 → 中心 (955,536)、半径 307，圆形按 0.96 等比缩放 */
.ring {
  position: absolute;
  left: 648px;
  top: 229px;
  width: 614px;
  height: 614px;
}

.ring__video {
  inset: 47px;
  border-radius: 50%;
  overflow: hidden;
}

.ring__deco {
  position: absolute;
  inset: 0;
  overflow: visible;
}

.ring__outer {
  fill: none;
  stroke: var(--accent);
  stroke-width: 10;
  filter: drop-shadow(0 0 12px var(--accent));
  opacity: 0.85;
}

.ring__inner {
  fill: none;
  stroke: var(--accent-soft);
  stroke-width: 2;
}

.ring__dash {
  fill: none;
  stroke: var(--accent);
  stroke-width: 3;
  stroke-dasharray: 2 7;
  opacity: 0.8;
}

.ring__arc {
  fill: none;
  stroke: var(--ring-blue);
  stroke-width: 6;
  stroke-linecap: round;
  filter: drop-shadow(0 0 6px var(--ring-blue));
}

.ring__arc--thin {
  stroke-width: 3;
}

.ring__spin {
  transform-origin: 307px 307px;
  animation: ring-spin 60s linear infinite;
}

.ring__spin--rev {
  animation-duration: 90s;
  animation-direction: reverse;
}

@keyframes ring-spin {
  to {
    transform: rotate(360deg);
  }
}

/* 底部四个圆环：圆心 x=300、762、1225、1687，y=840 → x 288、732、1176、1620，y 937 */
.counter {
  top: 889px;
}

.counter--1 { left: 288px; }
.counter--2 { left: 732px; }
.counter--3 { left: 1176px; }
.counter--4 { left: 1620px; }

.portal__banners {
  position: absolute;
  left: 812px;
  top: 52px;
  z-index: 20;
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 560px;
}

.portal__error {
  position: absolute;
  left: 0;
  right: 0;
  top: 860px;
  margin: 0;
  text-align: center;
  color: var(--danger);
}

.portal__times {
  position: absolute;
  right: 196px;
  top: 1031px;
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.portal__times .num {
  color: var(--text-primary);
}

.portal__sep {
  margin: 0 8px;
}
</style>
