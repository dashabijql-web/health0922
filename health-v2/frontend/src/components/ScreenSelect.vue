<template>
  <div ref="root" :class="['screen-select', { 'screen-select--open': open }]" @keydown="onKeydown">
    <input
      ref="input"
      class="screen-select__input"
      :value="open && filterable ? typed : (modelValue ?? '')"
      :placeholder="modelValue ?? placeholder"
      :readonly="!filterable"
      role="combobox"
      :aria-label="label"
      :aria-expanded="open"
      aria-autocomplete="list"
      @mousedown="toggle"
      @focus="onFocus"
      @input="onInput"
    />
    <span class="screen-select__chevron" aria-hidden="true">
      <svg viewBox="0 0 16 16"><path d="M4 6l4 4 4-4" /></svg>
    </span>
    <ul v-if="open" class="screen-select__list" role="listbox" :aria-label="label">
      <li
        v-for="(o, i) in shown"
        :key="o.key"
        role="option"
        :aria-selected="o.value === modelValue"
        :class="['screen-select__option', {
          'screen-select__option--active': i === active,
          'screen-select__option--selected': o.value === modelValue,
          'screen-select__option--all': o.value === null
        }]"
        @mousedown.prevent="choose(o.value)"
        @mousemove="active = i"
      >{{ o.text }}</li>
      <li v-if="shown.length === 0" class="screen-select__none">没有匹配的选项</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
// 大屏里的下拉框（健康档案的部门、工种）：列表画在 1920×1080 画布里，随画布一起缩放，
// 不用 Element Plus 那种挂到 body 上的弹层（画布缩放后会对不齐）。第一项"全部"表示不限。
// filterable 时可以输入文字过滤（"请选择或者输入工种"）；上下键移动、回车选中、Esc 关闭。
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: string | null
    options: string[]
    placeholder: string
    /** 无障碍名称 */
    label: string
    allText?: string
    filterable?: boolean
  }>(),
  { allText: '全部', filterable: false }
)
const emit = defineEmits<{ 'update:modelValue': [value: string | null] }>()

const root = ref<HTMLElement | null>(null)
const input = ref<HTMLInputElement | null>(null)
const open = ref(false)
const typed = ref('')
const active = ref(0)

const shown = computed(() => {
  const kw = props.filterable ? typed.value.trim() : ''
  const list = props.options.filter((o) => !kw || o.includes(kw)).map((o) => ({ key: o, value: o, text: o }))
  return kw ? list : [{ key: '\u0000all', value: null as string | null, text: props.allText }, ...list]
})

function onOutside(e: MouseEvent) {
  if (root.value && !root.value.contains(e.target as Node)) close()
}

function show() {
  if (open.value) return
  open.value = true
  typed.value = ''
  const i = shown.value.findIndex((o) => o.value === props.modelValue)
  active.value = Math.max(0, i)
  document.addEventListener('mousedown', onOutside, true)
  void nextTick(scrollToActive)
}

function close() {
  if (!open.value) return
  open.value = false
  typed.value = ''
  document.removeEventListener('mousedown', onOutside, true)
}

function toggle() {
  if (open.value && !props.filterable) close()
  else show()
}

function onFocus() {
  if (props.filterable) show()
}

function onInput(e: Event) {
  typed.value = (e.target as HTMLInputElement).value
  active.value = 0
  if (!open.value) show()
}

function choose(value: string | null) {
  emit('update:modelValue', value)
  close()
  input.value?.blur()
}

function scrollToActive() {
  root.value?.querySelector('.screen-select__option--active')?.scrollIntoView({ block: 'nearest' })
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    if (open.value) e.stopPropagation()
    close()
    return
  }
  if (e.key === 'Tab') {
    close()
    return
  }
  if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
    e.preventDefault()
    if (!open.value) return show()
    const n = shown.value.length
    if (n === 0) return
    active.value = (active.value + (e.key === 'ArrowDown' ? 1 : n - 1)) % n
    void nextTick(scrollToActive)
    return
  }
  if (e.key === 'Enter') {
    e.preventDefault()
    if (!open.value) return show()
    const o = shown.value[active.value]
    if (o) choose(o.value)
  }
}

onBeforeUnmount(() => document.removeEventListener('mousedown', onOutside, true))
</script>

<style scoped>
.screen-select {
  position: relative;
  height: 100%;
}

.screen-select__input {
  width: 100%;
  height: 100%;
  padding: 0 44px 0 40px;
  border: 1px solid var(--filter-input-border);
  border-radius: 999px;
  outline: none;
  background: var(--filter-input-bg);
  box-shadow: 0 0 10px var(--filter-input-glow), inset 0 0 8px var(--filter-input-glow);
  color: var(--text-primary);
  font: inherit;
  font-size: 17px;
  cursor: pointer;
}

.screen-select__input::placeholder {
  color: var(--filter-placeholder);
}

/* 选中了值时，占位文字显示的就是这个值（输入过滤时） */
.screen-select--open .screen-select__input,
.screen-select__input:focus-visible {
  border-color: var(--accent-soft);
}

.screen-select__chevron {
  position: absolute;
  right: 20px;
  top: 50%;
  width: 18px;
  height: 18px;
  transform: translateY(-50%);
  pointer-events: none;
  transition: transform 0.15s;
}

.screen-select--open .screen-select__chevron {
  transform: translateY(-50%) rotate(180deg);
}

.screen-select__chevron svg {
  width: 100%;
  height: 100%;
  fill: none;
  stroke: var(--text-primary);
  stroke-width: 1.6;
}

.screen-select__list {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  z-index: 30;
  max-height: 340px;
  margin: 0;
  padding: 6px 0;
  overflow-y: auto;
  list-style: none;
  background: var(--map-float-bg);
  border: 1px solid var(--filter-input-border);
  border-radius: 8px;
  box-shadow: 0 8px 24px var(--overlay-shadow);
  scrollbar-width: thin;
}

.screen-select__option,
.screen-select__none {
  padding: 8px 24px;
  font-size: 16px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.screen-select__option {
  cursor: pointer;
}

.screen-select__option--all {
  color: var(--text-secondary);
}

.screen-select__option--active {
  background: var(--accent-bg);
}

.screen-select__option--selected {
  color: var(--accent);
}

.screen-select__none {
  color: var(--text-secondary);
}
</style>
