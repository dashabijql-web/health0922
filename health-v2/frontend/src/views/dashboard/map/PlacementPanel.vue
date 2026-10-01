<template>
  <aside class="place" aria-label="摆放基站">
    <header class="place__head">
      <span class="place__title"><ScreenIcon name="pin" class="place__title-icon" />摆放基站</span>
      <button type="button" class="place__link" @click="emit('logs')"><ScreenIcon name="log" />操作记录</button>
      <button type="button" class="place__exit" @click="emit('close')">退出摆放</button>
    </header>

    <p class="place__hint" role="status">
      <template v-if="total === null">读取基站……</template>
      <template v-else-if="unplaced > 0">还有 <b class="num">{{ unplaced }}</b> 个基站未摆放（共 <span class="num">{{ total }}</span> 个）</template>
      <template v-else>全部 <span class="num">{{ total }}</span> 个基站都已摆放</template>
    </p>

    <div class="place__tools">
      <input v-model="keyword" class="place__search" type="search" placeholder="搜索基站名称、区域、编码" aria-label="搜索基站" />
      <div class="place__filters" role="radiogroup" aria-label="筛选">
        <button v-for="f in FILTERS" :key="f.key" type="button" role="radio" :aria-checked="filter === f.key"
                :class="['place__filter', { 'place__filter--on': filter === f.key }]" @click="filter = f.key">
          {{ f.label }}
        </button>
      </div>
    </div>

    <ul ref="listEl" class="place__list" aria-label="基站列表">
      <li v-for="s in list" :key="s.stationCode">
        <button type="button" :class="['place__item', { 'place__item--on': s.stationCode === selected?.stationCode }]"
                :data-code="s.stationCode" @click="emit('select', s.stationCode)">
          <span class="place__name" :title="s.vendorName && s.displayName ? `厂家名称：${s.vendorName}` : s.stationCode">
            {{ s.name }}
          </span>
          <span :class="['place__tag', s.placed ? 'place__tag--ok' : 'place__tag--todo']">{{ s.placed ? '已摆放' : '未摆放' }}</span>
          <span class="place__meta">{{ s.areaName ?? '区域暂无数据' }}</span>
          <span :class="['place__run', `place__run--${runKind(s.runStatus)}`]">{{ runText(s.runStatus) }}</span>
        </button>
      </li>
      <li v-if="list.length === 0" class="place__empty">{{ total === null ? '' : '没有符合条件的基站' }}</li>
    </ul>

    <!-- 选中的基站：改名、保存、取消、删除 -->
    <section v-if="selected" class="place__edit" aria-label="当前基站">
      <p class="place__edit-name">{{ selected.name }}</p>
      <p class="place__edit-meta">
        {{ selected.areaName ?? '区域暂无数据' }} · 厂家名称 {{ selected.vendorName ?? '无' }}
        <span v-if="selected.placed && selected.updatedBy"> · {{ selected.updatedBy }} {{ shortTime(selected.updatedAt) }}</span>
      </p>
      <p :class="['place__step', { 'place__step--ready': pending }]" role="status">
        <template v-if="!selected.placed && !pending">在地图上点一下它应该在的位置</template>
        <template v-else-if="pending">已放到新位置，可以拖动图标微调，点"保存"生效</template>
        <template v-else>拖动图标可以移动位置</template>
      </p>
      <label class="place__rename">
        <span>自己起的名字</span>
        <input v-model="name" maxlength="100" placeholder="不填就用厂家名称" />
      </label>
      <div class="place__buttons">
        <ScreenButton :disabled="saving || !canSave" @click="emit('save')">保存</ScreenButton>
        <ScreenButton kind="plain" :disabled="saving || !dirty" @click="emit('cancel')">取消</ScreenButton>
        <ScreenButton v-if="selected.placed" kind="plain" class="place__delete" :disabled="saving"
                      @click="emit('delete')">删除位置</ScreenButton>
      </div>
    </section>
  </aside>
</template>

<script setup lang="ts">
// 摆放模式左边的基站列表（docs/06 第五节）：搜索、筛选（全部/未摆放/已摆放），每行名称、区域、运行状态、是否已摆放；
// 下面是选中基站的编辑区：起名字、保存、取消、删除。列表顺序由后端按区域和名称排好。
import { computed, nextTick, ref, watch } from 'vue'
import type { Station } from '@/api/map'
import ScreenButton from '@/components/ScreenButton.vue'
import ScreenIcon from '@/components/ScreenIcon.vue'
import { shortTime } from '@/utils/format'
import type { PlaceFilter } from './use-map-stage'

const props = defineProps<{
  total: number | null
  unplaced: number
  list: Station[]
  selected: Station | null
  pending: boolean
  dirty: boolean
  saving: boolean
}>()
const emit = defineEmits<{ select: [code: string]; save: []; cancel: []; delete: []; close: []; logs: [] }>()
const filter = defineModel<PlaceFilter>('filter', { required: true })
const keyword = defineModel<string>('keyword', { required: true })
const name = defineModel<string>('name', { required: true })

const FILTERS: { key: PlaceFilter; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'unplaced', label: '未摆放' },
  { key: 'placed', label: '已摆放' }
]

const canSave = computed(() => props.dirty && (props.pending || !!props.selected?.placed))

function runKind(s: number | null): string {
  return s === 0 ? 'ok' : s === 2 ? 'fault' : 'off'
}

function runText(s: number | null): string {
  return s === 0 ? '通讯正常' : s === 1 ? '通讯中断' : s === 2 ? '故障' : s === 9 ? '状态未知' : '暂无状态'
}

// 选中的基站滚到看得见的地方（保存后自动跳到下一个时尤其需要）
const listEl = ref<HTMLElement | null>(null)
watch(
  () => props.selected?.stationCode,
  async (code) => {
    if (!code) return
    await nextTick()
    listEl.value?.querySelector(`[data-code="${code}"]`)?.scrollIntoView({ block: 'nearest' })
  }
)
</script>

<style scoped>
.place {
  position: absolute;
  left: 12px;
  top: 12px;
  bottom: 12px;
  z-index: 3;
  display: flex;
  flex-direction: column;
  width: 372px;
  background: var(--map-float-bg);
  border: 1px solid var(--border-glow);
  box-shadow: 0 0 18px var(--overlay-shadow);
  font-size: 14px;
}

.place__head {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 42px;
  padding: 0 10px 0 14px;
  background: linear-gradient(90deg, var(--accent-bg), transparent 80%);
  border-bottom: 1px solid var(--border-faint);
}

.place__title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-right: auto;
  font-size: 18px;
  font-weight: 700;
}

.place__title-icon {
  width: 20px;
  height: 20px;
  color: var(--accent);
}

.place__link,
.place__exit {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 8px;
  border: 1px solid var(--border-glow);
  border-radius: 3px;
  background: none;
  color: var(--text-primary);
  font-size: 13px;
  cursor: pointer;
}

.place__link :deep(svg) {
  width: 15px;
  height: 15px;
}

.place__exit {
  border-color: var(--accent);
  color: var(--accent);
}

.place__hint {
  margin: 8px 14px 0;
  color: var(--text-secondary);
}

.place__hint b {
  color: var(--warn);
}

.place__tools {
  display: flex;
  gap: 8px;
  padding: 8px 14px;
}

.place__search,
.place__rename input {
  flex: 1;
  min-width: 0;
  height: 30px;
  padding: 0 8px;
  border: 1px solid var(--border-glow);
  border-radius: 3px;
  background: var(--input-bg);
  color: var(--text-primary);
  font-size: 13px;
  outline: none;
}

.place__search:focus,
.place__rename input:focus {
  border-color: var(--accent);
}

.place__filters {
  display: flex;
}

.place__filter {
  height: 30px;
  padding: 0 8px;
  border: 1px solid var(--border-glow);
  background: none;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.place__filter + .place__filter {
  border-left: 0;
}

.place__filter--on {
  background: var(--accent-bg);
  color: var(--accent);
}

.place__list {
  flex: 1;
  min-height: 0;
  margin: 0;
  padding: 0 6px 0 14px;
  overflow-y: auto;
  list-style: none;
}

.place__item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0 8px;
  width: 100%;
  padding: 6px 8px;
  border: 0;
  border-bottom: 1px solid var(--border-faint);
  background: none;
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
}

.place__item:hover {
  background: var(--accent-hover);
}

.place__item--on {
  background: var(--select-bg);
  box-shadow: inset 3px 0 0 var(--min-yellow);
}

.place__name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.place__tag {
  font-size: 12px;
}

.place__tag--ok {
  color: var(--accent);
}

.place__tag--todo {
  color: var(--warn);
}

.place__meta,
.place__run {
  font-size: 12px;
  color: var(--text-secondary);
}

.place__run--ok {
  color: var(--accent-soft);
}

.place__run--fault {
  color: var(--danger);
}

.place__empty {
  padding: 16px 0;
  color: var(--text-secondary);
  text-align: center;
}

.place__edit {
  padding: 10px 14px 12px;
  border-top: 1px solid var(--border-glow);
  background: var(--inset-bg);
}

.place__edit p {
  margin: 0;
}

.place__edit-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--min-yellow);
}

.place__edit-meta {
  font-size: 12px;
  color: var(--text-secondary);
}

.place__step {
  margin: 6px 0 !important;
  font-size: 13px;
  color: var(--text-primary);
}

.place__step--ready {
  color: var(--min-yellow);
}

.place__rename {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.place__buttons {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.place__delete {
  margin-left: auto;
  border-color: var(--danger-border) !important;
  color: var(--danger) !important;
}
</style>
