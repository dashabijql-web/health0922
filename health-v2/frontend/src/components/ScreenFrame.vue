<template>
  <!-- 整页外围的科幻细线边框（docs/09 第二节"背景和边框"），坐标已按 1920×1080 换算 -->
  <svg class="screen-frame" viewBox="0 0 1920 1080" preserveAspectRatio="none" aria-hidden="true">
    <g class="line">
      <!-- 顶部：断开的短线和小圆点 -->
      <path d="M560 33H1200M1260 33H1520M1560 33H1700" />
      <path d="M1760 28H1868L1886 46" />
      <!-- 右侧：竖向装饰线 -->
      <path d="M1886 46V1010M1872 300V420M1872 640V760" />
      <!-- 底部：断开的横线 -->
      <path d="M40 1054H640M700 1054H1200M1260 1054H1760" />
      <path d="M22 1040V1054H40" />
    </g>
    <g class="accent">
      <circle cx="1216" cy="33" r="3" />
      <circle cx="1540" cy="33" r="3" />
      <circle cx="1730" cy="33" r="3" />
      <path d="M160 1054H240M760 1054H800" />
      <!-- 右下角一组竖条 -->
      <path d="M1836 1040V1062M1843 1040V1062M1850 1040V1062M1857 1040V1062M1864 1040V1062" />
    </g>
  </svg>
</template>

<style scoped>
.screen-frame {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.line {
  fill: none;
  stroke: var(--border-glow);
  stroke-width: 1.5;
}

.accent {
  fill: var(--accent);
  stroke: var(--accent);
  stroke-width: 3;
}
</style>
