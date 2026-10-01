<template>
  <span :class="['counter', { 'counter--boxed': boxed, 'counter--ready': ready }]"
        :aria-label="value === null ? undefined : String(value)">
    <template v-if="value !== null">
      <span v-for="ch in chars" :key="ch.key" :class="ch.digit ? 'counter__cell' : 'counter__sep'" aria-hidden="true">
        <span v-if="ch.digit" class="counter__strip" :style="{ transform: `translateY(${-ch.n * 10}%)` }">
          <span v-for="d in 10" :key="d" class="counter__digit">{{ d - 1 }}</span>
        </span>
        <template v-else>{{ ch.text }}</template>
      </span>
    </template>
    <slot v-else name="empty"><span class="counter__empty">暂无数据</span></slot>
  </span>
</template>

<script setup lang="ts">
// 数字滚动（docs/07 第一部分"六、动画"）：每一位是一列 0–9，数值变化时 600 ms 滚到新的数字；
// 第一次显示不滚动，系统开启"减少动态效果"时直接跳变。boxed 是"累计采集数据"那种每一位一个深色小方块的数字牌。
// value 为 null 表示后端没有这个数据，显示"暂无数据"，不显示 0。
import { computed, nextTick, onMounted, ref } from 'vue'

const props = withDefaults(defineProps<{ value: number | string | null; boxed?: boolean }>(), { boxed: false })

const ready = ref(false)
onMounted(() => void nextTick(() => requestAnimationFrame(() => (ready.value = true))))

// 按"从右数第几位"给每一位定 key，位数变化时已有的列不重建，滚动才连贯
const chars = computed(() => {
  const text = props.value === null ? '' : String(props.value)
  return [...text].map((c, i) => {
    const fromRight = text.length - i
    const digit = c >= '0' && c <= '9'
    return { key: `${digit ? 'd' : 's'}${fromRight}`, digit, n: digit ? Number(c) : 0, text: c }
  })
})
</script>

<style scoped>
.counter {
  display: inline-flex;
  align-items: stretch;
  font-family: var(--font-number);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.counter__cell {
  position: relative;
  display: inline-block;
  height: 1em;
  overflow: hidden;
}

.counter__strip {
  display: flex;
  flex-direction: column;
}

.counter--ready .counter__strip {
  transition: transform 600ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.counter__digit {
  display: block;
  height: 1em;
  text-align: center;
}

.counter__sep {
  display: inline-block;
}

.counter__empty {
  font-family: var(--font-body);
  font-size: 0.5em;
  color: var(--text-secondary);
  align-self: center;
}

/* 数字牌：每一位一个深色小方块 */
.counter--boxed {
  gap: 4px;
}

.counter--boxed .counter__cell {
  width: 0.8em;
  height: 1.2em;
  padding-top: 0.1em;
  background: linear-gradient(180deg, var(--digit-bg-top), var(--digit-bg-bottom));
  border: 1px solid var(--border-faint);
  box-shadow: inset 0 0 6px var(--accent-bg);
}

@media (prefers-reduced-motion: reduce) {
  .counter--ready .counter__strip {
    transition: none;
  }
}
</style>
