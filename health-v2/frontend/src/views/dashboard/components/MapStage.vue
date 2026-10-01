<template>
  <div class="map-stage">
    <StageButton class="map-stage__btn map-stage__btn--1" icon="person" @click="soon">重点监护</StageButton>
    <StageButton class="map-stage__btn map-stage__btn--2" icon="heart" @click="soon">今日关注</StageButton>
    <ModeButton class="map-stage__mode" @click="emit('toggleMode')">地图模式</ModeButton>
    <EmptyState class="map-stage__empty" text="地图模式建设中" />
  </div>
</template>

<script setup lang="ts">
// 地图模式（?mode=map）在阶段 4 做；本阶段只做到切换模式（docs/08 阶段 3）。
import { ElMessage } from 'element-plus'
import EmptyState from '@/components/EmptyState.vue'
import ModeButton from './ModeButton.vue'
import StageButton from './StageButton.vue'

const emit = defineEmits<{ toggleMode: [] }>()

function soon() {
  ElMessage.info('建设中')
}
</script>

<style scoped>
.map-stage {
  position: relative;
  width: 100%;
  height: 100%;
}

.map-stage__btn {
  position: absolute;
  left: 19px;
  width: 195px;
  height: 51px;
  z-index: 1;
}

.map-stage__btn--1 {
  top: 19px;
}

.map-stage__btn--2 {
  top: 100px;
}

.map-stage__mode {
  position: absolute;
  left: 1204px;
  top: 21px;
  width: 128px;
  height: 54px;
  z-index: 1;
}

.map-stage__empty {
  position: absolute;
  inset: 0;
}
</style>
