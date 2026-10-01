<template>
  <Transition name="logs">
    <aside v-if="open" class="logs" role="dialog" aria-label="摆放操作记录" tabindex="-1" @keydown.esc="emit('close')">
      <header class="logs__head">
        <h3 class="logs__title">摆放操作记录</h3>
        <label v-if="stationCode" class="logs__only">
          <input v-model="onlyCurrent" type="checkbox" />只看「{{ stationName }}」
        </label>
        <button type="button" class="logs__close" aria-label="关闭" @click="emit('close')"><ScreenIcon name="close" /></button>
      </header>

      <div class="logs__body">
        <EmptyState v-if="error" kind="error" />
        <ul v-else class="logs__list">
          <li v-for="log in data?.list ?? []" :key="log.id" class="logs__item">
            <p class="logs__line">
              <span class="num logs__time">{{ log.createdAt }}</span>
              <span class="logs__user">{{ log.username }}</span>
              <span :class="['logs__action', `logs__action--${log.action.toLowerCase()}`]">{{ ACTION_TEXT[log.action] }}</span>
              <span class="logs__station" :title="log.stationCode">{{ log.stationName }}</span>
            </p>
            <p class="logs__detail">
              <template v-if="log.action === 'STATION_PLACE' && log.after">放在 {{ xy(log.after) }}</template>
              <template v-else-if="log.action === 'STATION_MOVE' && log.before && log.after">
                {{ xy(log.before) }} → {{ xy(log.after) }}（挪了 {{ distance(log.before, log.after) }} 米）
              </template>
              <template v-else-if="log.action === 'STATION_RENAME' && log.before && log.after">
                名字 {{ label(log.before.displayName) }} → {{ label(log.after.displayName) }}
              </template>
              <template v-else-if="log.action === 'STATION_DELETE' && log.before">删除了 {{ xy(log.before) }} 的位置，回到未摆放</template>
            </p>
          </li>
          <li v-if="!loading && (data?.list.length ?? 0) === 0" class="logs__empty">暂无操作记录</li>
        </ul>
      </div>

      <footer class="logs__foot">
        <ListPager :total="data?.total ?? 0" :page="page" :pages="pages" unit="条" :busy="loading" @go="go" />
      </footer>
    </aside>
  </Transition>
</template>

<script setup lang="ts">
// "查看操作记录"抽屉（docs/06 第五节）：倒序、可按基站筛选、服务端分页，只读。
import { computed, ref, shallowRef, watch } from 'vue'
import { fetchStationLogs, type MarkSnapshot, type StationAction, type StationLog } from '@/api/map'
import type { Page } from '@/api/dashboard'
import EmptyState from '@/components/EmptyState.vue'
import ScreenIcon from '@/components/ScreenIcon.vue'
import ListPager from '../components/ListPager.vue'

const props = defineProps<{ open: boolean; stationCode: string | null; stationName: string | null }>()
const emit = defineEmits<{ close: [] }>()

const PAGE_SIZE = 20
const ACTION_TEXT: Record<StationAction, string> = {
  STATION_PLACE: '摆放',
  STATION_MOVE: '移动',
  STATION_RENAME: '改名',
  STATION_DELETE: '删除'
}

const onlyCurrent = ref(true)
const page = ref(1)
const data = shallowRef<Page<StationLog> | null>(null)
const loading = ref(false)
const error = ref(false)
const pages = computed(() => Math.max(1, Math.ceil((data.value?.total ?? 0) / PAGE_SIZE)))

async function load() {
  loading.value = true
  try {
    data.value = await fetchStationLogs(onlyCurrent.value ? props.stationCode : null, page.value, PAGE_SIZE)
    error.value = false
  } catch {
    error.value = data.value === null
  } finally {
    loading.value = false
  }
}

function go(p: number) {
  page.value = p
  void load()
}

watch(
  () => [props.open, props.stationCode, onlyCurrent.value] as const,
  ([open]) => {
    if (!open) return
    page.value = 1
    void load()
  },
  { immediate: true }
)

function xy(m: MarkSnapshot): string {
  return `(${Number(m.x).toFixed(1)}, ${Number(m.y).toFixed(1)})`
}

function distance(a: MarkSnapshot, b: MarkSnapshot): string {
  return Math.hypot(Number(b.x) - Number(a.x), Number(b.y) - Number(a.y)).toFixed(1)
}

function label(name: string | null): string {
  return name ? `"${name}"` : '（厂家名称）'
}
</script>

<style scoped>
.logs {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 6;
  display: flex;
  flex-direction: column;
  width: 560px;
  background: var(--modal-bg);
  border-left: 1px solid var(--modal-border);
  box-shadow: -6px 0 24px var(--overlay-shadow);
  outline: none;
}

.logs__head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px 10px 18px;
  border-bottom: 1px solid var(--border-faint);
}

.logs__title {
  margin: 0;
  font-size: 18px;
}

.logs__only {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
}

.logs__close {
  width: 22px;
  height: 22px;
  margin-left: auto;
  padding: 0;
  border: 0;
  background: none;
  color: var(--modal-text);
  cursor: pointer;
}

.logs__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 18px;
}

.logs__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.logs__item {
  padding: 8px 0;
  border-bottom: 1px solid var(--border-faint);
  font-size: 13px;
}

.logs__line,
.logs__detail {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 0;
}

.logs__detail {
  margin-top: 2px;
  color: var(--text-secondary);
}

.logs__time {
  color: var(--text-secondary);
}

.logs__action {
  padding: 0 6px;
  border-radius: 2px;
  background: var(--accent-bg);
  color: var(--accent);
}

.logs__action--station_delete {
  background: var(--danger-bg);
  color: var(--danger);
}

.logs__action--station_rename {
  background: var(--warn-bg);
  color: var(--warn);
}

.logs__empty {
  padding: 24px 0;
  text-align: center;
  color: var(--text-secondary);
}

.logs__foot {
  padding: 8px 18px 12px;
}

.logs-enter-active,
.logs-leave-active {
  transition: transform 0.18s ease, opacity 0.18s ease;
}

.logs-enter-from,
.logs-leave-to {
  transform: translateX(30px);
  opacity: 0;
}
</style>
