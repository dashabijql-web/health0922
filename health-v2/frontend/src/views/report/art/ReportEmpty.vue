<template>
  <div class="report-empty" role="status">
    <!-- 浅蓝色线条插画：两张叠着的文档、柱状图、问号气泡、星点（截图 8–12 的"暂无数据"，我们自己画的） -->
    <svg class="report-empty__art" viewBox="0 0 320 150" aria-hidden="true">
      <path class="deco" d="M18 136q142-46 284 0" stroke-dasharray="10 7" />
      <rect class="line" x="112" y="12" width="96" height="114" rx="12" />
      <rect class="line fill" x="128" y="24" width="96" height="114" rx="12" />
      <path class="line" d="M150 48v28M168 40v36M186 52v24M145 76h58M145 92h62M145 104h62M145 116h62" />
      <path class="deco" d="M76 32h22a6 6 0 0 1 6 6v10a6 6 0 0 1-6 6h-12l-7 6v-6h-3a6 6 0 0 1-6-6v-10a6 6 0 0 1 6-6z" />
      <path class="deco" d="M85 38q4-3 7 0t-4 6v2M88 50v1" />
      <path class="deco" d="M232 14l6-8M238 22l11-5M232 34l-2 12M240 66h8M244 62v8M252 84h8M256 80v8M98 72h8M102 68v8M86 92h9M90.5 88v9" />
      <circle class="deco" cx="263" cy="70" r="5" />
      <circle class="deco" cx="102" cy="104" r="5" />
      <circle class="deco" cx="236" cy="96" r="2.5" />
      <path class="deco" d="M60 112l5 5M65 112l-5 5M78 128l5 5M83 128l-5 5M246 116l5 5M251 116l-5 5M268 128l5 5M273 128l-5 5" />
    </svg>
    <p :class="['report-empty__text', { 'report-empty__text--error': error }]">{{ text }}</p>
    <slot />
  </div>
</template>

<script setup lang="ts">
// 月报里"暂无数据"的统一插画（docs/07 第二部分：某页没有数据时显示"yyyy年MM月，暂无数据"）。
// error 时文字用红色（加载失败）。
withDefaults(defineProps<{ text: string; error?: boolean }>(), { error: false })
</script>

<style scoped>
.report-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 110px;
  gap: 10px;
}

.report-empty__art {
  width: 320px;
  height: 150px;
  fill: none;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.line {
  stroke: var(--report-empty-line);
  stroke-width: 3;
}

.fill {
  fill: var(--report-paper);
}

.deco {
  stroke: var(--report-empty-deco);
  stroke-width: 2;
}

.report-empty__text {
  margin: 0;
  color: var(--report-empty-line);
  font-size: 15px;
  font-weight: 700;
}

.report-empty__text--error {
  color: var(--report-danger);
}
</style>
