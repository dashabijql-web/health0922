<template>
  <Transition name="screen-modal">
    <div v-if="open" class="screen-modal" @mousedown.self="emit('close')">
      <section
        ref="box"
        class="screen-modal__box"
        :style="{ width: `${width}px` }"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
        tabindex="-1"
        @keydown.esc="emit('close')"
      >
        <header class="screen-modal__head">
          <h3 class="screen-modal__title">{{ title }}</h3>
          <slot name="head-extra" />
          <button type="button" class="screen-modal__close" aria-label="关闭" @click="emit('close')">
            <ScreenIcon name="close" />
          </button>
        </header>
        <div class="screen-modal__body"><slot /></div>
        <footer v-if="$slots.footer" class="screen-modal__foot"><slot name="footer" /></footer>
      </section>
    </div>
  </Transition>
</template>

<script setup lang="ts">
// 大屏里的弹层：画在 1920×1080 画布里（随画布一起缩放），盖住整页并居中。
// 样式对照截图 13 的确认框：深青绿底、亮青色描边、左上标题、右上 ×（docs/09 第八节）。Esc、点空白处关闭。
import { nextTick, ref, watch } from 'vue'
import ScreenIcon from './ScreenIcon.vue'

const props = withDefaults(defineProps<{ open: boolean; title: string; width?: number }>(), { width: 422 })
const emit = defineEmits<{ close: [] }>()

const box = ref<HTMLElement | null>(null)
watch(
  () => props.open,
  async (open) => {
    if (open) {
      await nextTick()
      box.value?.focus()
    }
  }
)
</script>

<style scoped>
.screen-modal {
  position: absolute;
  inset: 0;
  z-index: 50;
  display: grid;
  place-items: center;
  background: var(--modal-mask);
}

.screen-modal__box {
  display: flex;
  flex-direction: column;
  max-height: 80%;
  background: var(--modal-bg);
  border: 1px solid var(--modal-border);
  border-radius: 4px;
  box-shadow: 0 0 24px var(--accent-bg);
  outline: none;
}

.screen-modal__head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 16px 10px 18px;
}

.screen-modal__title {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
  color: var(--modal-text);
}

.screen-modal__close {
  width: 22px;
  height: 22px;
  margin-left: auto;
  padding: 0;
  border: 0;
  background: none;
  color: var(--modal-text);
  cursor: pointer;
}

.screen-modal__close:hover {
  color: var(--accent);
}

.screen-modal__body {
  flex: 1;
  min-height: 0;
  padding: 6px 18px 12px;
  color: var(--modal-text);
  font-size: 15px;
}

.screen-modal__foot {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 4px 16px 16px;
}

.screen-modal-enter-active,
.screen-modal-leave-active {
  transition: opacity 0.15s;
}

.screen-modal-enter-from,
.screen-modal-leave-to {
  opacity: 0;
}
</style>
