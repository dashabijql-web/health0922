<template>
  <Panel title="人员信息展示" :loading="headcount.loading.value" :failed-at="failedAt" class="people">
    <template #extra>
      <div class="people__extra">
        <span v-if="pages > 1" class="people__pager">
          <button type="button" aria-label="上一页" :disabled="page <= 1" @click="emit('page', -1)">‹</button>
          <span class="num">{{ page }}/{{ pages }}</span>
          <button type="button" aria-label="下一页" :disabled="page >= pages" @click="emit('page', 1)">›</button>
        </span>
        <button type="button" class="people__download" title="佩戴情况导出" aria-label="佩戴情况导出"
                @click="emit('export')">
          <ScreenIcon name="download" />
        </button>
      </div>
    </template>

    <div class="people__body">
      <!-- 左半：5 个人数。没收到过定位数据时都是 null，显示"暂无数据" -->
      <EmptyState v-if="headcount.error.value" kind="error" class="people__counts" />
      <div v-else class="people__counts">
        <div class="count count--main">
          <span class="count__avatar count__avatar--main"><ScreenIcon name="person" /></span>
          <span class="count__text">
            <span class="count__label">井下职工</span>
            <CounterRoll class="count__value" :value="h?.inWell ?? null" />
          </span>
        </div>
        <div v-for="c in COUNTS" :key="c.key" class="count">
          <span class="count__avatar"><ScreenIcon name="person" /></span>
          <span class="count__text">
            <span class="count__label">{{ c.label }}</span>
            <CounterRoll class="count__value" :value="h ? h[c.key] : null" />
          </span>
        </div>
      </div>

      <!-- 右半：井下人员表 -->
      <div class="people__table">
        <EmptyState v-if="persons.error.value" kind="error" />
        <DarkTable
          v-else
          :columns="COLUMNS"
          :rows="persons.data.value?.list ?? []"
          :row-key="(r) => r.cardCode"
          :row-height="44"
          clickable
          @row-click="(r) => emit('locate', r)"
        >
          <template #cell-name="{ row }">
            <span class="people__name">
              <span class="people__mini-avatar"><ScreenIcon name="person" /></span>{{ row.name ?? '未录入' }}
            </span>
          </template>
          <template #cell-card="{ row }"><span class="num" :title="row.cardCode">{{ cardNo(row.cardCode) }}</span></template>
          <template #cell-dept="{ row }"><span :title="row.dept ?? ''">{{ row.dept ?? '未录入' }}</span></template>
          <template #cell-area="{ row }">
            <span :title="row.areaName ?? ''">{{ row.areaName ?? '暂无数据' }}</span>
            <!-- 地图模式：所在基站没摆放的人地图上不画，这里标出来（docs/05 第五节） -->
            <span v-if="mapMode && !row.stationPlaced" class="people__unplaced" title="所在基站还没有摆放位置，地图上不画">基站未摆放</span>
          </template>
          <template #cell-watch="{ row }">
            <span v-if="row.watchState === 'UNBOUND'" class="watch-unbound">未绑定</span>
            <span v-else :class="['watch', `watch--${row.watchState.toLowerCase()}`]" :title="WATCH_TEXT[row.watchState]"
                  role="img" :aria-label="WATCH_TEXT[row.watchState]">
              <ScreenIcon name="watch" />
            </span>
          </template>
          <template #empty>{{ persons.loading.value ? '' : '暂无井下人员' }}</template>
        </DarkTable>
      </div>
    </div>
  </Panel>
</template>

<script setup lang="ts">
// 底中：井下职工 / 应上线 / 未上线 / 已上线无告警 / 已上线-告警 + 井下人员表（docs/05 第四节"人员信息展示"）。
// 点表里的一行切到地图模式并定位到这个人。标题栏的下载图标就是"佩戴情况导出"。
import { computed } from 'vue'
import type { Headcount, InWellPerson, Page, WatchState } from '@/api/dashboard'
import CounterRoll from '@/components/CounterRoll.vue'
import DarkTable, { type Column } from '@/components/DarkTable.vue'
import EmptyState from '@/components/EmptyState.vue'
import Panel from '@/components/Panel.vue'
import ScreenIcon from '@/components/ScreenIcon.vue'
import type { Resource } from '@/composables/use-polling'
import { cardNo } from '@/utils/format'

const props = defineProps<{
  headcount: Resource<Headcount>
  persons: Resource<Page<InWellPerson>>
  page: number
  pageSize: number
  /** 地图模式下标出"基站未摆放" */
  mapMode?: boolean
}>()
const emit = defineEmits<{ page: [delta: number]; export: []; locate: [person: InWellPerson] }>()

const COUNTS: { key: 'expected' | 'offline' | 'onlineNormal' | 'onlineAlert'; label: string }[] = [
  { key: 'expected', label: '应上线' },
  { key: 'offline', label: '未上线' },
  { key: 'onlineNormal', label: '已上线无告警' },
  { key: 'onlineAlert', label: '已上线-告警' }
]

const COLUMNS: Column[] = [
  { key: 'name', title: '姓名', width: '1.3fr' },
  { key: 'card', title: '卡号', width: '0.8fr' },
  { key: 'dept', title: '机构', width: '1.2fr' },
  { key: 'area', title: '区域', width: '1.2fr' },
  { key: 'watch', title: '监测', width: '0.8fr' }
]

const WATCH_TEXT: Record<WatchState, string> = { ONLINE: '手表在线', OFFLINE: '手表离线', UNBOUND: '未绑定手表' }

const h = computed(() => props.headcount.data.value)
const pages = computed(() => Math.max(1, Math.ceil((props.persons.data.value?.total ?? 0) / props.pageSize)))
const failedAt = computed(() => props.headcount.failedAt.value ?? props.persons.failedAt.value)
</script>

<style scoped>
.people :deep(.panel__body) {
  padding: 8px 0 6px 2px;
}

.people__extra {
  display: flex;
  align-items: center;
  gap: 14px;
}

.people__unplaced {
  display: block;
  font-size: 11px;
  line-height: 1.2;
  color: var(--warn);
}

.people__pager {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}

.people__pager button {
  width: 22px;
  height: 22px;
  border: 1px solid var(--border-glow);
  background: transparent;
  color: var(--accent);
  cursor: pointer;
}

.people__pager button:disabled {
  opacity: 0.4;
  cursor: default;
}

.people__download {
  width: 26px;
  height: 26px;
  padding: 2px;
  border: 1px solid var(--accent);
  border-radius: 3px;
  background: var(--accent-bg);
  color: var(--accent);
  cursor: pointer;
}

.people__body {
  display: grid;
  grid-template-columns: 308px 1fr;
  grid-template-rows: minmax(0, 1fr);
  gap: 6px;
  height: 100%;
}

.people__counts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 70px 74px 74px;
  align-items: center;
}

.count {
  display: flex;
  align-items: center;
  gap: 10px;
}

.count--main {
  grid-column: 1 / -1;
}

.count__avatar {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  padding: 9px;
  flex-shrink: 0;
  border-radius: 50%;
  border: 2px solid var(--accent-soft);
  background: radial-gradient(circle, var(--accent-bg), transparent 70%);
  color: var(--text-primary);
  box-shadow: 0 0 10px var(--accent-glow);
}

.count__avatar--main {
  width: 52px;
  height: 52px;
  border-color: var(--orange-glow);
  box-shadow: 0 0 14px var(--orange-glow);
}

.count__text {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.count__label {
  font-size: 15px;
  color: var(--text-secondary);
}

.count__value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}

.people__table {
  min-width: 0;
  min-height: 0;
  height: 100%;
  padding-right: 4px;
  overflow: hidden;
}

.people__name {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.people__mini-avatar {
  width: 24px;
  height: 24px;
  padding: 3px;
  flex-shrink: 0;
  border-radius: 50%;
  border: 1px solid var(--accent-soft);
  color: var(--text-primary);
}

.watch {
  position: relative;
  display: inline-flex;
  align-items: center;
  width: 24px;
  height: 24px;
  vertical-align: middle;
}

.watch--online {
  color: var(--info);
  filter: drop-shadow(0 0 4px var(--info));
}

/* 离线：灰色图标加一道斜杠，不只靠颜色区分 */
.watch--offline {
  color: var(--text-secondary);
}

.watch--offline::after {
  content: '';
  position: absolute;
  left: 50%;
  top: -2px;
  width: 2px;
  height: 28px;
  background: var(--warn);
  transform: rotate(45deg);
}

.watch-unbound {
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
