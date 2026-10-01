<template>
  <div class="stage">
    <HeroVideo class="stage__video" :src="heroVideo" :poster="heroPoster" />

    <StageButton class="stage__btn stage__btn--1" icon="watch" @click="emit('export')">佩戴情况</StageButton>
    <StageButton class="stage__btn stage__btn--2" icon="report" @click="emit('monthly')">月度汇总</StageButton>

    <span class="stage__total-label">累计采集数据</span>
    <span class="stage__total">
      <CounterRoll boxed :value="overview ? overview.totalCollected : null" />
      <span class="stage__unit">条</span>
    </span>

    <ModeButton class="stage__mode" @click="emit('toggleMode')">展示模式</ModeButton>

    <!-- 四个平台上的名称和数字是页面文字，叠在视频上（docs/09 第三节） -->
    <span v-for="p in PLATFORMS" :key="p.key" class="stage__platform" :style="{ left: `${p.x}px`, top: `${p.y}px` }">
      <span class="stage__platform-name">{{ p.name }}</span>
      <CounterRoll class="stage__platform-value" :value="overview ? overview.collected[p.key] : null" />
    </span>
  </div>
</template>

<script setup lang="ts">
// 动态数据页中间主区域·展示模式（docs/05 第四节、docs/09 第三节）。坐标是相对主区域左上角的像素（1920×1080 画布）。
// 平台、全息人体在预渲染视频里（tools/hero-render，scene=dashboard），平台位置和这里的文字位置一一对应。
import type { CollectedKey, Overview } from '@/api/dashboard'
import CounterRoll from '@/components/CounterRoll.vue'
import HeroVideo from '@/components/HeroVideo.vue'
import heroPoster from '@/assets/hero/dashboard-poster.jpg'
import heroVideo from '@/assets/hero/dashboard.mp4'
import ModeButton from './ModeButton.vue'
import StageButton from './StageButton.vue'

defineProps<{ overview: Overview | null }>()
const emit = defineEmits<{ export: []; monthly: []; toggleMode: [] }>()

/** 名称左端、垂直中线的位置（截图 1 的坐标换算后减去主区域原点）；第四个平台截图是"体检"，我们是血压 */
const PLATFORMS: { key: CollectedKey; name: string; x: number; y: number }[] = [
  { key: 'HEART_RATE', name: '心率', x: 257, y: 121 },
  { key: 'SPO2', name: '血氧', x: 955, y: 121 },
  { key: 'TEMPERATURE', name: '体温', x: 411, y: 349 },
  { key: 'BLOOD_PRESSURE', name: '血压', x: 769, y: 354 }
]
</script>

<style scoped>
.stage {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.stage__btn {
  position: absolute;
  left: 19px;
  width: 195px;
}

.stage__btn--1 {
  top: 19px;
  height: 51px;
}

.stage__btn--2 {
  top: 100px;
  height: 50px;
}

.stage__total-label {
  position: absolute;
  left: 425px;
  top: 26px;
  font-size: 30px;
  line-height: 34px;
  letter-spacing: 2px;
  color: var(--text-dim-white);
}

.stage__total {
  position: absolute;
  left: 632px;
  top: 18px;
  display: flex;
  align-items: center;
  gap: 14px;
  height: 50px;
  font-size: 36px;
  color: var(--accent);
}

.stage__unit {
  font-size: 30px;
  color: var(--text-dim-white);
}

.stage__mode {
  position: absolute;
  left: 1204px;
  top: 21px;
  width: 128px;
  height: 54px;
}

.stage__platform {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 12px;
  transform: translateY(-50%);
  white-space: nowrap;
  text-shadow: 0 0 6px var(--bg-page);
}

.stage__platform-name {
  font-size: 20px;
  color: var(--text-bright);
}

.stage__platform-value {
  font-size: 24px;
  color: var(--accent);
}
</style>
