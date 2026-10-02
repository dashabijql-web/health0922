<template>
  <ReportPage :no="1" dark>
    <div class="intro">
      <p class="intro__line1"><span class="intro__arrow" aria-hidden="true"></span>赋能职工全流程的</p>
      <p class="intro__line2">健康监测及数据管理</p>
      <div class="intro__art"><IsoIllustration /></div>
    </div>
  </ReportPage>
</template>

<script setup lang="ts">
// 第 1 页：插画页 + 一句标语（截图 6 左页）。插画是我们自己画的等距插画。
import IsoIllustration from '../art/IsoIllustration.vue'
import ReportPage from './ReportPage.vue'
</script>

<style scoped>
.intro {
  height: 100%;
  margin: 0 -40px;
  padding-top: 150px;
}

.intro__line1,
.intro__line2 {
  margin: 0;
  font-weight: 700;
}

.intro__line1 {
  display: flex;
  align-items: center;
  gap: 22px;
  padding-left: 40px;
  font-size: 25px;
}

.intro__arrow {
  width: 22px;
  height: 20px;
  background: var(--report-tagline-arrow);
  clip-path: polygon(0 0, 100% 50%, 0 100%, 30% 50%);
}

.intro__line2 {
  margin-top: 16px;
  padding-left: 132px;
  font-size: 32px;
  letter-spacing: 1px;
}

.intro__art {
  width: 620px;
  height: 480px;
  margin: 70px auto 0;
}
</style>
