<template>
  <svg class="screen-icon" viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor"
       stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
    <!-- 心形（心率） -->
    <template v-if="name === 'heart'">
      <path d="M12 20s-7.5-4.6-7.5-10.1A4.2 4.2 0 0 1 12 7.4a4.2 4.2 0 0 1 7.5 2.5C19.5 15.4 12 20 12 20z" />
      <path d="M7 12h2.5l1.2-2.2 1.8 4.4 1.3-2.2H17" />
    </template>
    <!-- 水滴（血氧） -->
    <template v-else-if="name === 'drop'">
      <path d="M12 3.5s6 6.4 6 10.6a6 6 0 0 1-12 0C6 9.9 12 3.5 12 3.5z" />
      <path d="M9.5 14.5a2.6 2.6 0 0 0 2.5 2.4" />
    </template>
    <!-- 温度计（体温） -->
    <template v-else-if="name === 'thermometer'">
      <path d="M10 13.5V5a2 2 0 0 1 4 0v8.5a4 4 0 1 1-4 0z" />
      <path d="M12 9v7" />
      <circle cx="12" cy="16.8" r="1.2" fill="currentColor" />
    </template>
    <!-- 跑步的人（步数） -->
    <template v-else-if="name === 'runner'">
      <circle cx="14.5" cy="4.5" r="1.8" />
      <path d="M8 21l3-6 3 2v4M7 11l3-3 4 1.5 2 3h3M11 8l-1.5 6.5" />
    </template>
    <!-- 警报灯（SOS） -->
    <template v-else-if="name === 'sos'">
      <path d="M6 18v-5a6 6 0 0 1 12 0v5" />
      <path d="M4 18h16v2.5H4zM12 3v2M4.2 6.2l1.4 1.4M19.8 6.2l-1.4 1.4" />
      <path d="M10 13a2 2 0 0 1 2-2" />
    </template>
    <!-- 跌倒 -->
    <template v-else-if="name === 'fall'">
      <circle cx="6" cy="6" r="1.8" />
      <path d="M7.5 9l4 2.5 4.5-1M10 11l-1.5 5 4 2.5M3 21h18M15 14l3 3" />
    </template>
    <!-- 血压（压力表） -->
    <template v-else-if="name === 'pressure'">
      <circle cx="12" cy="13" r="7.5" />
      <path d="M12 13l3.5-3.5M8 13h.01M12 9h.01M16 13h.01" />
      <path d="M10 3.5h4" />
    </template>
    <!-- 手表 -->
    <template v-else-if="name === 'watch'">
      <rect x="6.5" y="6.5" width="11" height="11" rx="2.5" />
      <path d="M9 6.5L9.6 3h4.8l.6 3.5M9 17.5l.6 3.5h4.8l.6-3.5M9 12.5l1.5 1.5 3-3.5" />
    </template>
    <!-- 报告（月度汇总） -->
    <template v-else-if="name === 'report'">
      <path d="M6 3h9l3 3v15H6z" />
      <path d="M9 9h6M9 13h6M9 17h3" />
      <circle cx="17.5" cy="17.5" r="3" fill="var(--bg-panel-strong)" />
      <path d="M17.5 16v1.6l1 .8" />
    </template>
    <!-- 下载 -->
    <template v-else-if="name === 'download'">
      <path d="M12 4v11M7.5 10.5L12 15l4.5-4.5M5 19h14" />
    </template>
    <!-- 人像 -->
    <template v-else-if="name === 'person'">
      <circle cx="12" cy="8.5" r="3.6" />
      <path d="M4.8 20c.9-4 3.8-6 7.2-6s6.3 2 7.2 6" />
    </template>
    <!-- 数据管理 / 数据展示（屏幕 + 饼图） -->
    <template v-else-if="name === 'screen'">
      <rect x="3.5" y="4.5" width="17" height="12" rx="1.5" />
      <path d="M12 7.5v3h3a3 3 0 1 1-3-3zM9 20h6M12 16.5V20" />
    </template>
    <!-- 叉 -->
    <template v-else-if="name === 'close'">
      <path d="M6 6l12 12M18 6L6 18" />
    </template>
    <!-- 感叹号圆圈 -->
    <template v-else-if="name === 'alert'">
      <circle cx="12" cy="12" r="9" fill="currentColor" stroke="none" />
      <path d="M12 7.5v6M12 16.5v.3" stroke="var(--bg-page)" stroke-width="2.4" />
    </template>
    <!-- 设备事件（电池） -->
    <template v-else-if="name === 'battery'">
      <rect x="3" y="7.5" width="16" height="9" rx="1.5" />
      <path d="M21 10.5v3M6 10.5v3" />
    </template>
  </svg>
</template>

<script setup lang="ts">
// 大屏用的线条图标，颜色跟随文字颜色（currentColor），大小由外层 CSS 决定。图标是我们自己画的，不用第三方素材。
import type { IconName } from './icon-names'

defineProps<{ name: IconName }>()
</script>

<style scoped>
.screen-icon {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
