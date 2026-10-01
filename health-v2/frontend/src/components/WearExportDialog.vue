<template>
  <ScreenModal :open="open" title="是否确认导出?" @close="emit('close')">
    <p class="wear-export__text">
      <span class="wear-export__icon"><ScreenIcon name="alert" /></span>
      此操作将导出下井职工前一天手表佩戴情况
    </p>
    <template #footer>
      <ScreenButton :disabled="busy" @click="emit('close')">取消</ScreenButton>
      <ScreenButton :disabled="busy" @click="confirm">{{ busy ? '导出中…' : '确定' }}</ScreenButton>
    </template>
  </ScreenModal>
</template>

<script setup lang="ts">
// 佩戴情况确认框（截图 13，docs/09 第八节）→ 下载 Excel（docs/05 第四节"佩戴情况导出"，默认导出昨天）。
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { downloadWearExcel } from '@/api/dashboard'
import ScreenButton from './ScreenButton.vue'
import ScreenIcon from './ScreenIcon.vue'
import ScreenModal from './ScreenModal.vue'

defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

const busy = ref(false)

async function confirm() {
  busy.value = true
  try {
    const { blob, fileName } = await downloadWearExcel()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName ?? '佩戴情况.xlsx'
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 10_000)
    ElMessage.success('已导出')
    emit('close')
  } catch {
    // 失败提示已由 download() 弹出
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.wear-export__text {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 4px 0 8px;
  font-size: 15px;
}

.wear-export__icon {
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  color: var(--warn);
}
</style>
