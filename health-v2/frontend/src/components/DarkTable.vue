<template>
  <div class="dark-table" role="table">
    <div class="dark-table__head" role="row" :style="{ gridTemplateColumns: template }">
      <span v-for="c in columns" :key="c.key" role="columnheader" class="dark-table__th">{{ c.title }}</span>
    </div>
    <div class="dark-table__body" role="rowgroup">
      <div
        v-for="(row, i) in rows"
        :key="rowKey(row, i)"
        role="row"
        :class="['dark-table__row', { 'dark-table__row--clickable': clickable }]"
        :style="{ gridTemplateColumns: template, height: `${rowHeight}px` }"
        :tabindex="clickable ? 0 : undefined"
        @click="clickable && emit('rowClick', row)"
        @keydown.enter="clickable && emit('rowClick', row)"
      >
        <span v-for="c in columns" :key="c.key" role="cell" class="dark-table__td">
          <slot :name="`cell-${c.key}`" :row="row">{{ (row as Record<string, unknown>)[c.key] }}</slot>
        </span>
      </div>
      <div v-if="rows.length === 0" class="dark-table__empty"><slot name="empty">暂无数据</slot></div>
    </div>
  </div>
</template>

<script setup lang="ts" generic="T">
// 深色表格（docs/07 公共组件 DarkTable）：表头是实心青色横条，行之间细线分隔，表体超出时在表格内部滚动。
import { computed } from 'vue'

export interface Column {
  key: string
  title: string
  /** CSS grid 列宽，如 "1fr"、"80px"，默认 1fr */
  width?: string
}

const props = withDefaults(
  defineProps<{
    columns: Column[]
    rows: T[]
    rowKey: (row: T, index: number) => string | number
    rowHeight?: number
    clickable?: boolean
  }>(),
  { rowHeight: 40, clickable: false }
)
const emit = defineEmits<{ rowClick: [row: T] }>()

const template = computed(() => props.columns.map((c) => c.width ?? '1fr').join(' '))
</script>

<style scoped>
.dark-table {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  font-size: 15px;
}

.dark-table__head,
.dark-table__row {
  display: grid;
  align-items: center;
  text-align: center;
}

.dark-table__head {
  height: 34px;
  flex-shrink: 0;
  background: var(--table-head-bg);
  color: var(--text-primary);
  font-size: 16px;
}

.dark-table__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--border-glow) transparent;
}

.dark-table__row {
  border-bottom: 1px solid var(--border-faint);
}

.dark-table__row--clickable {
  cursor: pointer;
}

.dark-table__row--clickable:hover,
.dark-table__row--clickable:focus-visible {
  background: var(--accent-bg);
  outline: none;
}

.dark-table__td {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 0 4px;
}

.dark-table__empty {
  display: grid;
  place-items: center;
  height: 100%;
  min-height: 60px;
  color: var(--text-secondary);
}
</style>
