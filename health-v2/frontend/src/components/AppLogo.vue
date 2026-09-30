<template>
  <div class="app-logo" role="img" aria-label="系统标志">
    <svg class="app-logo__mark" viewBox="0 0 64 64" aria-hidden="true">
      <defs>
        <linearGradient id="hv2-logo-grad" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="var(--text-primary)" />
          <stop offset="1" stop-color="var(--accent)" />
        </linearGradient>
      </defs>
      <!-- 六边形外框 + 矿灯 + 心跳线：占位标志，不使用任何第三方标志 -->
      <path d="M32 3l25 14.5v29L32 61 7 46.5v-29z" fill="none" stroke="url(#hv2-logo-grad)" stroke-width="3" />
      <circle cx="32" cy="20" r="5" fill="var(--accent)" />
      <path d="M12 38h10l4-9 6 17 5-12 3 4h12" fill="none" stroke="var(--accent)" stroke-width="3"
            stroke-linecap="round" stroke-linejoin="round" />
    </svg>
    <div class="app-logo__text">
      <span class="app-logo__cn">井下健康</span>
      <span class="app-logo__en">MINE HEALTH</span>
    </div>
  </div>
</template>

<style scoped>
.app-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 100%;
}

.app-logo__mark {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  filter: drop-shadow(0 0 6px var(--accent-glow));
}

.app-logo__text {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}

.app-logo__cn {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 4px;
  color: var(--text-primary);
}

.app-logo__en {
  font-family: var(--font-number);
  font-size: 12px;
  letter-spacing: 3px;
  color: var(--accent);
}
</style>
