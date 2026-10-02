<template>
  <ReportPage :no="2">
    <div class="catalog">
      <div class="catalog__badge" aria-hidden="true">
        <span class="catalog__num">01</span>
        <span class="catalog__en">CATALOGUE</span>
        <span class="catalog__cn">目录</span>
      </div>
      <h3 class="catalog__sr">目录</h3>
      <ol class="catalog__list">
        <li v-for="item in ITEMS" :key="item.page">
          <button type="button" class="catalog__item" @click="emit('go', item.page)">
            <span class="catalog__index">{{ item.index }}</span>{{ item.text }}
            <span class="catalog__page">第 {{ item.page }} 页</span>
          </button>
        </li>
      </ol>
      <svg class="catalog__wave" viewBox="0 0 720 200" preserveAspectRatio="none" aria-hidden="true">
        <path v-for="i in 14" :key="i" :d="wave(i)" />
      </svg>
    </div>
  </ReportPage>
</template>

<script setup lang="ts">
// 第 2 页：目录（截图 6 右页）。右上角深蓝色大圆角写"01 CATALOGUE 目录"，下面三行蓝色文字；
// 点一行翻到那一章（按钮上点击不会触发翻页）。底部浅色波浪线装饰。
import ReportPage from './ReportPage.vue'

const emit = defineEmits<{ go: [page: number] }>()

const ITEMS = [
  { index: '一、', text: '数据汇总统计', page: 3 },
  { index: '二、', text: '职工健康评估', page: 7 },
  { index: '三、', text: '结论', page: 13 }
]

/** 一组错开的正弦曲线 */
function wave(i: number): string {
  const amp = 36 + i * 2
  const y0 = 120 + i * 3
  const shift = i * 14
  return `M0 ${y0} C ${180 + shift} ${y0 - amp}, ${360 + shift} ${y0 + amp}, 720 ${y0 - 20 - i * 2}`
}
</script>

<style scoped>
.catalog {
  position: absolute;
  inset: -12px 0 0;
}

.catalog__badge {
  position: absolute;
  top: -40px;
  right: -40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  width: 270px;
  height: 250px;
  padding-bottom: 34px;
  border-bottom-left-radius: 135px;
  background: var(--report-blue-deep);
  color: var(--report-white);
}

.catalog__num {
  font-size: 34px;
  font-weight: 700;
  line-height: 1;
}

.catalog__en {
  margin-top: 6px;
  font-size: 14px;
  letter-spacing: 1px;
}

.catalog__cn {
  margin-top: 4px;
  font-size: 38px;
  font-weight: 700;
  letter-spacing: 4px;
  line-height: 1.2;
}

.catalog__sr {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}

.catalog__list {
  position: absolute;
  top: 230px;
  left: 110px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.catalog__item {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 30px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--report-link);
  font-size: 22px;
  font-weight: 700;
  cursor: pointer;
}

.catalog__item:hover {
  color: var(--report-blue-deep);
}

.catalog__index {
  min-width: 52px;
}

.catalog__page {
  margin-left: 18px;
  color: var(--report-text-dim);
  font-size: 13px;
  font-weight: 400;
}

.catalog__wave {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  height: 220px;
  fill: none;
  stroke: var(--report-box-border);
  stroke-width: 1;
  opacity: 0.45;
}
</style>
