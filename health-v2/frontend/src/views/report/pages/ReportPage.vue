<template>
  <article :class="['rp', `rp--${side}`, { 'rp--dark': dark }]" :aria-label="`第 ${no} 页 ${section ?? title ?? ''}`">
    <header v-if="title" class="rp__banner">
      <BannerNetwork :seed="no" />
      <h2 class="rp__title">{{ title }}</h2>
    </header>
    <h3 v-if="section" class="rp__section"><span>{{ section }}</span></h3>

    <div class="rp__body">
      <div v-if="status === 'loading'" class="rp__state" role="status">加载中…</div>
      <ReportEmpty v-else-if="status === 'failed'" text="加载失败" error>
        <button type="button" class="report-btn" @click="emit('retry')">重新加载</button>
      </ReportEmpty>
      <ReportEmpty v-else-if="empty" :text="emptyText ?? (month ? `${monthLabel(month)}，暂无数据` : '暂无数据')" />
      <slot v-else />
    </div>

    <footer class="rp__foot">
      <p class="rp__disclaimer">{{ DISCLAIMER }}</p>
      <span class="rp__bars" aria-hidden="true"><i></i><i></i></span>
      <span class="rp__no">{{ no }} / {{ PAGE_TOTAL }}</span>
    </footer>
  </article>
</template>

<script setup lang="ts">
// 月报内页的框（截图 7–12，docs/09 第八节）：蓝色渐变页眉 + 网络连线纹理、深蓝斜切的小节标题、
// 正文、页脚（免责声明、两条斜切蓝条、页码 n / 13：左页在左下，右页在右下）。
// status 是整份报告的加载状态；empty 表示这一页没有数据，显示统一的"yyyy年MM月，暂无数据"插画。
import { computed } from 'vue'
import BannerNetwork from '../art/BannerNetwork.vue'
import ReportEmpty from '../art/ReportEmpty.vue'
import { DISCLAIMER, PAGE_TOTAL, monthLabel } from '../report-format'

const props = withDefaults(
  defineProps<{
    no: number
    title?: string
    section?: string
    status?: 'loading' | 'failed' | 'ready'
    empty?: boolean
    emptyText?: string
    month?: string | null
    dark?: boolean
  }>(),
  { title: undefined, section: undefined, status: 'ready', empty: false, emptyText: undefined, month: null, dark: false }
)
const emit = defineEmits<{ retry: [] }>()

const side = computed(() => (props.no % 2 === 1 ? 'left' : 'right'))
</script>

<style scoped>
.rp {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: var(--report-paper);
  color: var(--report-text);
  font-size: 14px;
  line-height: 1.6;
}

.rp--dark {
  background:
    radial-gradient(circle at 105% -5%, var(--report-cover-arc) 0 38%, transparent 38.2%),
    radial-gradient(circle at 120% 30%, var(--report-cover-arc-2) 0 30%, transparent 30.2%),
    linear-gradient(160deg, var(--report-cover-top), var(--report-cover-bottom));
  color: var(--report-white);
}

/* 页眉：高约页高的 8%；左右两页的渐变接起来是一整条（左页蓝 → 中，右页中 → 靛） */
.rp__banner {
  position: relative;
  flex: none;
  height: 74px;
  background: linear-gradient(90deg, var(--report-banner-left), var(--report-banner-mid));
}

.rp--right .rp__banner {
  background: linear-gradient(90deg, var(--report-banner-mid), var(--report-banner-right));
}

.rp__title {
  position: relative;
  margin: 0;
  padding-left: 40px;
  line-height: 74px;
  color: var(--report-white);
  font-size: 27px;
  font-weight: 700;
  letter-spacing: 1px;
}

/* 小节标题：深蓝斜切条，右端浅灰紫的斜角 */
.rp__section {
  position: relative;
  flex: none;
  align-self: flex-start;
  margin: 20px 0 0 40px;
  padding: 0 64px 0 10px;
  min-width: 270px;
  height: 36px;
  line-height: 36px;
  color: var(--report-white);
  font-size: 19px;
  font-weight: 700;
  background: var(--report-blue-deep);
  clip-path: polygon(0 0, 100% 0, calc(100% - 24px) 100%, 0 100%);
}

.rp__section::after {
  content: '';
  position: absolute;
  top: 0;
  right: -2px;
  width: 30px;
  height: 100%;
  background: var(--report-tag-deco);
  clip-path: polygon(0 0, 50% 0, 100% 100%, 50% 100%);
}

.rp__section span {
  position: relative;
}

.rp__body {
  position: relative;
  flex: 1;
  min-height: 0;
  padding: 12px 40px 0;
}

.rp__state {
  padding-top: 200px;
  text-align: center;
  color: var(--report-text-dim);
  font-size: 15px;
}

/* 页脚：免责声明一行，下面两条斜切蓝条和页码 */
.rp__foot {
  position: relative;
  flex: none;
  height: 64px;
}

.rp__disclaimer {
  margin: 0;
  padding: 0 64px;
  text-align: center;
  color: var(--report-text-dim);
  font-size: 11px;
  line-height: 1.4;
}

.rp--dark .rp__disclaimer {
  color: var(--report-cover-tag);
}

.rp__bars {
  position: absolute;
  bottom: 24px;
  left: 0;
  display: flex;
  gap: 6px;
}

.rp__bars i {
  display: block;
  height: 12px;
  background: var(--report-footer-bar);
  transform: skewX(-40deg);
}

.rp__bars i:first-child {
  width: 66px;
  margin-left: -10px;
}

.rp__bars i:last-child {
  width: 16px;
}

.rp--right .rp__bars {
  left: auto;
  right: 0;
  flex-direction: row-reverse;
}

.rp--right .rp__bars i:first-child {
  margin: 0 -10px 0 0;
}

.rp__no {
  position: absolute;
  bottom: 4px;
  left: 14px;
  color: var(--report-blue);
  font-size: 13px;
  font-weight: 700;
}

.rp--right .rp__no {
  left: auto;
  right: 14px;
}

.rp--dark .rp__no {
  color: var(--report-white);
}
</style>
