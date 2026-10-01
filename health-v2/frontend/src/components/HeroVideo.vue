<template>
  <div class="hero-video decor" aria-hidden="true">
    <img v-if="still" class="hero-video__media" :src="poster" alt="" />
    <video
      v-else
      ref="video"
      class="hero-video__media"
      :src="src"
      :poster="poster"
      autoplay
      muted
      loop
      playsinline
      preload="auto"
      disablepictureinpicture
    ></video>
  </div>
</template>

<script setup lang="ts">
// 预渲染的循环动画（docs/07 第一部分"六、动画""七、首页中央动画"）：背景纯黑，用 mix-blend-mode: screen 融掉黑色。
// 页面切到后台时暂停；系统开启"减少动态效果"时只显示静态海报图。同一页面只放一个。
import { onBeforeUnmount, onMounted, ref } from 'vue'

defineProps<{ src: string; poster: string }>()

const video = ref<HTMLVideoElement | null>(null)
const still = ref(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false)

function onVisibility() {
  const v = video.value
  if (!v) return
  if (document.hidden) v.pause()
  else void v.play().catch(() => undefined)
}

onMounted(() => document.addEventListener('visibilitychange', onVisibility))
onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', onVisibility)
  video.value?.pause()
})
</script>

<style scoped>
.hero-video {
  position: absolute;
  inset: 0;
  pointer-events: none;
  mix-blend-mode: screen;
}

.hero-video__media {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: fill;
}
</style>
