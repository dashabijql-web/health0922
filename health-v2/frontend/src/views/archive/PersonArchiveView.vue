<template>
  <!-- 换了一个人（地址里的卡编码变了）就整页重建，状态不串到下一个人 -->
  <PersonArchive :key="cardCode" :card-code="cardCode" />
</template>

<script setup lang="ts">
// 个人档案路由页 /archive/:cardCode（docs/05 第七节）。内容在 components/PersonArchive.vue。
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import PersonArchive from './components/PersonArchive.vue'

const route = useRoute()
const cardCode = computed(() => String(route.params.cardCode))
</script>
