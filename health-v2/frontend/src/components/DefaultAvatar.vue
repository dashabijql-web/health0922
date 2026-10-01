<template>
  <svg class="default-avatar" viewBox="0 0 100 100" role="img" aria-label="默认头像">
    <defs>
      <clipPath :id="clipId"><circle cx="50" cy="50" r="46" /></clipPath>
      <radialGradient :id="diskId" cx="50%" cy="38%" r="65%">
        <stop offset="0%" stop-color="var(--avatar-disk)" />
        <stop offset="100%" stop-color="var(--avatar-disk-edge)" />
      </radialGradient>
    </defs>
    <circle cx="50" cy="50" r="46" :fill="`url(#${diskId})`" />
    <g :clip-path="`url(#${clipId})`">
      <!-- 西装、衬衫、领带 -->
      <path d="M14 100c2-17 14-25 36-25s34 8 36 25z" fill="var(--avatar-suit)" />
      <path d="M40 75h20l-10 24z" fill="var(--avatar-shirt)" />
      <path d="M47.5 77h5l1.5 4-2.5 14h-3l-2.5-14z" fill="var(--avatar-tie)" />
      <!-- 脖子、脸、耳朵 -->
      <rect x="43" y="62" width="14" height="14" rx="3" fill="var(--avatar-skin)" />
      <ellipse cx="50" cy="48" rx="15" ry="18" fill="var(--avatar-skin)" />
      <ellipse cx="35" cy="50" rx="3" ry="4.5" fill="var(--avatar-skin)" />
      <ellipse cx="65" cy="50" rx="3" ry="4.5" fill="var(--avatar-skin)" />
      <!-- 头发 -->
      <path d="M34.5 47c-2-15 6-24 16-24s17.5 7 15.5 24c-1.5-6-4.5-10-9-11.5-5 3-14 3.5-19 1-2 2.5-3 6-3.5 10.5z"
            fill="var(--avatar-hair)" />
    </g>
  </svg>
</template>

<script setup lang="ts">
// 统一的默认头像（docs/09 第六节"头像用统一的默认头像"）：我们自己画的半身像，颜色来自 tokens.css。
// 同一页面有十几个头像，渐变和裁剪的 id 每个实例各用一份，避免互相引用。
import { useId } from 'vue'

const uid = useId()
const clipId = `avatar-clip-${uid}`
const diskId = `avatar-disk-${uid}`
</script>

<style scoped>
.default-avatar {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
