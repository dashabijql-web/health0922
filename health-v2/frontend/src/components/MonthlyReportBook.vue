<template>
  <div
    :class="['book', { 'book--cover': centered }]"
    :style="{ width: `${width * 2}px`, height: `${height}px` }"
    :data-state="state"
    :data-page="page"
    :data-flip-state="flipState"
  >
    <!-- 书页由调用方放进来：每一页是一个 class="book-page" 的元素，第一页是封面。
         StPageFlip 会把这些元素搬进它自己的容器、改它们的 style，所以页面根元素上不要绑动态的 class 和 style -->
    <div ref="block" class="book__block"><slot /></div>
  </div>
</template>

<script setup lang="ts">
// 翻书（docs/07 第二部分"一、形态"）：StPageFlip 的封装，封面单页、后面双页展开。
// 鼠标移到页角页角卷起，点击或拖动翻页；键盘由调用方处理（next / prev / flipTo）。
// 停在封面时整本书左移半页，让封面居中（截图 2、5）；开始翻页时移回来。
// 卸载时 destroy()：移除事件监听和书的元素，并停掉绘制循环（原版不停，本项目打了补丁，见 types/page-flip.d.ts）。
// 根元素的 data-state（loading / ready）、data-page（当前页序号，封面是 0）、data-flip-state 供冒烟脚本判断。
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { PageFlip, type FlipState } from 'page-flip'

const props = withDefaults(defineProps<{ width: number; height: number; flippingTime?: number }>(), {
  flippingTime: 800
})
const emit = defineEmits<{ flip: [page: number] }>()

const block = ref<HTMLElement | null>(null)
const state = ref<'loading' | 'ready'>('loading')
const page = ref(0)
const flipState = ref<FlipState>('read')
const centered = computed(() => page.value === 0 && (flipState.value === 'read' || flipState.value === 'fold_corner'))

let book: PageFlip | null = null

onMounted(() => {
  const el = block.value
  if (!el) return
  const pages = Array.from(el.querySelectorAll<HTMLElement>(':scope > .book-page'))
  // "减少动态效果"时翻页动画缩短（翻页本身是操作反馈，不去掉）
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  book = new PageFlip(el, {
    width: props.width,
    height: props.height,
    size: 'fixed',
    autoSize: false,
    usePortrait: false,
    showCover: true,
    drawShadow: true,
    maxShadowOpacity: 0.45,
    flippingTime: reduced ? 250 : props.flippingTime,
    mobileScrollSupport: false,
    showPageCorners: true
  })
  book.on('init', () => (state.value = 'ready'))
  book.on('flip', (e) => {
    page.value = e.data
    emit('flip', e.data)
  })
  book.on('changeState', (e) => (flipState.value = e.data))
  book.loadFromHTML(pages)
})

onBeforeUnmount(() => {
  book?.destroy()
  book = null
})

/** 正在翻的时候不接新的翻页，免得动画叠在一起 */
function idle() {
  return book !== null && state.value === 'ready' && (flipState.value === 'read' || flipState.value === 'fold_corner')
}

function next() {
  if (idle()) book?.flipNext('bottom')
}

function prev() {
  if (idle()) book?.flipPrev('bottom')
}

/** 翻到第 index 页（封面是 0） */
function flipTo(index: number) {
  if (idle() && index !== page.value) book?.flip(index, 'bottom')
}

defineExpose({ next, prev, flipTo })
</script>

<style scoped>
.book {
  position: relative;
  transition: transform 0.45s ease;
}

/* 封面在右半边，左移半页（书宽的 1/4）让它居中 */
.book--cover {
  transform: translateX(-25%);
}

.book__block {
  width: 100%;
  height: 100%;
}

/* StPageFlip 自带的样式把 .stf__wrapper 写错成了 .sft__wrapper，这里补上：不用 autoSize 时要撑满 */
.book__block :deep(.stf__wrapper) {
  position: relative;
  width: 100%;
  height: 100%;
}

@media (prefers-reduced-motion: reduce) {
  .book {
    transition: none;
  }
}
</style>
