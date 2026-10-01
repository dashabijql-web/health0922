<template>
  <div class="avatar-ring" :style="{ width: `${size}px`, height: `${size}px` }">
    <span class="avatar-ring__ring" aria-hidden="true"></span>
    <span class="avatar-ring__dot" aria-hidden="true"></span>
    <div class="avatar-ring__face"><DefaultAvatar /></div>
  </div>
</template>

<script setup lang="ts">
// 圆形头像 + 青绿色发光圆环 + 环顶上的亮点（截图 3 卡片、截图 4 人员信息卡）。
// 圆形按 0.96 等比缩放，不跟着画布纵向拉伸（docs/09 第一节），所以尺寸用固定像素。
import DefaultAvatar from './DefaultAvatar.vue'

withDefaults(defineProps<{ size?: number }>(), { size: 106 })
</script>

<style scoped>
.avatar-ring {
  position: relative;
  flex-shrink: 0;
}

.avatar-ring__ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 3px solid var(--avatar-ring);
  box-shadow: 0 0 12px var(--avatar-ring-glow), inset 0 0 10px var(--avatar-ring-glow);
}

/* 环顶偏左的亮点 */
.avatar-ring__dot {
  position: absolute;
  left: 43%;
  top: -3px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--avatar-dot);
  box-shadow: 0 0 10px 3px var(--avatar-ring-glow);
}

.avatar-ring__face {
  position: absolute;
  inset: 13%;
}
</style>
