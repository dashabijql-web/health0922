<template>
  <div class="list-pager">
    <span>共 <span class="num">{{ total }}</span> {{ unit }}</span>
    <ScreenButton kind="plain" :disabled="page <= 1 || busy" @click="emit('go', page - 1)">上一页</ScreenButton>
    <span class="num">{{ page }} / {{ pages }}</span>
    <ScreenButton kind="plain" :disabled="page >= pages || busy" @click="emit('go', page + 1)">下一页</ScreenButton>
  </div>
</template>

<script setup lang="ts">
import ScreenButton from '@/components/ScreenButton.vue'

defineProps<{ total: number; page: number; pages: number; unit: string; busy: boolean }>()
const emit = defineEmits<{ go: [page: number] }>()
</script>

<style scoped>
.list-pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  width: 100%;
  font-size: 14px;
  color: var(--text-secondary);
}
</style>
