<template>
  <div class="cover">
    <div class="cover__logo"><AppLogo /></div>
    <h1 class="cover__title">职工健康数据汇总报告</h1>
    <p class="cover__month">{{ month ? monthLabel(month) : '暂无数据' }}</p>
    <img class="cover__art" :src="poster" alt="" draggable="false" />
    <p class="cover__note">声明：{{ DISCLAIMER }}</p>
    <p class="cover__source">
      数据来源：职工健康管理系统 {{ VERSION }}<template v-if="generatedAt"> · 统计于 {{ generatedAt.slice(0, 16) }}</template>
    </p>
  </div>
</template>

<script setup lang="ts">
// 封面（截图 5）：整页深蓝渐变，左上 logo，中间白色大标题、月份；下半部分是发光的人站在多层光环上——
// 用的是我们自己渲染的入口页海报（tools/hero-render），用 screen 混合去掉黑底、色相转成蓝色；
// 底部橙色声明、白色"数据来源"。截图里标题下的"企业版"标签去掉（docs/09 第八节）。
import AppLogo from '@/components/AppLogo.vue'
import poster from '@/assets/hero/portal-poster.jpg'
import { DISCLAIMER, monthLabel } from '../report-format'

defineProps<{ month: string | null; generatedAt: string | null }>()

const VERSION = __APP_VERSION__
</script>

<style scoped>
.cover {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background:
    radial-gradient(circle at 112% 2%, var(--report-cover-arc) 0 44%, transparent 44.3%),
    radial-gradient(circle at 128% 46%, var(--report-cover-arc-2) 0 40%, transparent 40.3%),
    radial-gradient(ellipse at 50% 72%, var(--report-cover-arc) 0 30%, transparent 60%),
    linear-gradient(165deg, var(--report-cover-top), var(--report-cover-bottom));
  color: var(--report-white);
  text-align: center;
}

/* logo 原本是大屏的青绿色，封面上换成白色和亮青色 */
.cover__logo {
  position: absolute;
  left: 34px;
  top: 30px;
  height: 52px;
  --accent: var(--report-tagline-arrow);
  --text-primary: var(--report-white);
  --text-secondary: var(--report-cover-tag);
}

.cover__title {
  margin: 158px 0 0;
  font-size: 40px;
  font-weight: 700;
  letter-spacing: 10px;
  text-indent: 10px;
}

.cover__month {
  margin: 30px 0 0;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 1px;
}

.cover__art {
  position: absolute;
  left: 50%;
  top: 300px;
  width: 560px;
  height: 560px;
  transform: translateX(-50%);
  mix-blend-mode: screen;
  filter: hue-rotate(38deg) saturate(1.1);
  pointer-events: none;
  user-select: none;
}

.cover__note {
  position: absolute;
  left: 40px;
  right: 40px;
  bottom: 62px;
  margin: 0;
  color: var(--report-cover-note);
  font-size: 12px;
}

.cover__source {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 30px;
  margin: 0;
  font-size: 13px;
  font-weight: 700;
}
</style>
