<template>
  <div :class="['map-stage', { 'map-stage--placing': s.placing.value }]">
    <!-- 地图铺满主区域；背景是深色带纹理的底（docs/09 第四节） -->
    <div ref="mapEl" class="map-stage__map" data-testid="mine-map"></div>
    <EmptyState v-if="s.configError.value" class="map-stage__config-error" kind="error" text="地图配置读取失败，点右上角刷新重试" />

    <!-- 左侧：重点监护、今日关注（只显示名单里的人，再点一次取消） -->
    <template v-if="!s.placing.value">
      <StageButton :class="['map-stage__btn', 'map-stage__btn--1', { 'map-stage__btn--on': s.filter.value === 'key' }]"
                   icon="person" :aria-pressed="s.filter.value === 'key'" @click="s.toggleFilter('key')">重点监护</StageButton>
      <StageButton :class="['map-stage__btn', 'map-stage__btn--2', { 'map-stage__btn--on': s.filter.value === 'today' }]"
                   icon="star" :aria-pressed="s.filter.value === 'today'" @click="s.toggleFilter('today')">今日关注</StageButton>
      <p v-if="s.filter.value !== 'all'" class="map-stage__filter-note" role="status">
        只显示{{ s.filter.value === 'key' ? '重点监护' : '今日关注' }}名单里的人 ·
        <template v-if="s.shownFilter.value === s.filter.value">井下 <span class="num">{{ s.persons.data.value?.length ?? 0 }}</span> 人</template>
        <template v-else>读取中……</template>
      </p>
    </template>

    <!-- 顶部搜索框：姓名或卡号，回车定位；旁边刷新 -->
    <form class="map-stage__search" role="search" @submit.prevent="s.search">
      <input v-model="s.keyword.value" class="map-stage__search-input" type="search" name="keyword"
             placeholder="请输入职工姓名/卡号进行查询" aria-label="职工姓名或卡号" maxlength="50" />
      <button type="button" :class="['map-stage__refresh', { 'map-stage__refresh--busy': s.refreshing.value }]"
              title="刷新" aria-label="刷新" @click="s.refresh">
        <ScreenIcon name="refresh" />
      </button>
      <ul v-if="s.searchResults.value" class="map-stage__results" aria-label="搜索结果">
        <li v-for="p in s.searchResults.value" :key="p.cardCode">
          <button type="button" @click="s.pickSearchResult(p)">
            <span>{{ p.name ?? '未录入' }}</span>
            <span class="num" :title="p.cardCode">{{ cardNo(p.cardCode) }}</span>
            <span class="map-stage__muted">{{ p.areaName ?? '暂无数据' }}</span>
            <span v-if="!p.placed" class="map-stage__unplaced">基站未摆放</span>
          </button>
        </li>
      </ul>
    </form>

    <ModeButton class="map-stage__mode" @click="emit('toggleMode')">地图模式</ModeButton>
    <button v-if="!s.placing.value" type="button" class="map-stage__place-btn" @click="s.enterPlacement()">
      <ScreenIcon name="pin" />摆放基站
    </button>

    <!-- 左下角：显示/隐藏基站 -->
    <button v-if="!s.placing.value" type="button"
            :class="['map-stage__tower', { 'map-stage__tower--off': !s.stationsShown.value }]"
            :title="s.stationsShown.value ? '隐藏基站' : '显示基站'" :aria-pressed="s.stationsShown.value"
            aria-label="显示或隐藏基站" @click="s.stationsShown.value = !s.stationsShown.value">
      <ScreenIcon name="tower" />
    </button>

    <!-- 所在基站没摆放的人：地图上不画，这里列出来（docs/05 第五节） -->
    <div v-if="s.unplacedPersons.value.length > 0 && !s.placing.value" class="map-stage__unplaced-box">
      <button type="button" class="map-stage__unplaced-toggle" :aria-expanded="unplacedOpen"
              @click="unplacedOpen = !unplacedOpen">
        <span class="num">{{ s.unplacedPersons.value.length }}</span> 人所在基站未摆放，地图上没有画 {{ unplacedOpen ? '▾' : '▸' }}
      </button>
      <ul v-if="unplacedOpen" class="map-stage__unplaced-list">
        <li v-for="p in s.unplacedPersons.value" :key="p.cardCode">
          <button type="button" @click="s.openPerson(p)">
            <span>{{ p.name ?? '未录入' }}</span>
            <span class="num" :title="p.cardCode">{{ cardNo(p.cardCode) }}</span>
            <span class="map-stage__muted">{{ p.areaName ?? '暂无数据' }}</span>
            <span class="map-stage__unplaced">基站未摆放</span>
          </button>
        </li>
      </ul>
    </div>

    <p v-if="s.baseMapError.value" class="map-stage__notice" role="alert">底图加载失败（GeoServer 没有响应），人员和基站照常显示</p>
    <p v-if="s.awaitingClick.value && !s.pending.value" class="map-stage__notice map-stage__notice--place" role="status">
      在地图上点一下「{{ s.selectedStation.value?.name }}」的位置
    </p>
    <p v-if="s.failedAt.value" class="map-stage__failed" role="status"
       :title="`刷新失败，显示的是 ${s.failedAt.value} 的数据`">刷新失败，显示的是 {{ s.failedAt.value }} 的数据</p>

    <!-- 挂在地图上的弹窗：体征卡，或同一基站几个人的名单 -->
    <div ref="popupEl" class="map-stage__popup">
      <template v-if="s.popup.value?.coord">
        <MapPersonCard v-if="s.popup.value.kind === 'card'" :person="s.popup.value.group[0]" :data="s.card.data.value"
                       :loading="s.card.loading.value" :docked="false" @close="s.closePopup" @archive="openArchive"
                       @place="s.goPlace" />
        <div v-else class="map-stage__group" role="dialog" aria-label="同一基站的人">
          <p class="map-stage__group-title">
            {{ s.popup.value.group[0].stationName }} · <span class="num">{{ s.popup.value.group.length }}</span> 人
            <button type="button" aria-label="关闭" @click="s.closePopup"><ScreenIcon name="close" /></button>
          </p>
          <ul>
            <li v-for="p in s.popup.value.group" :key="p.cardCode">
              <button type="button" @click="s.openPerson(p)">
                <span :class="{ 'map-stage__alerting': p.alerting }">{{ p.name ?? '未录入' }}</span>
                <span class="num" :title="p.cardCode">{{ cardNo(p.cardCode) }}</span>
                <span class="map-stage__muted">{{ WATCH_TEXT[p.watchState] }}</span>
              </button>
            </li>
          </ul>
          <span class="map-stage__group-arrow" aria-hidden="true"></span>
        </div>
      </template>
    </div>
    <!-- 人所在基站没摆放：地图不动，卡片停在上方并提示 -->
    <MapPersonCard v-if="s.popup.value && !s.popup.value.coord" class="map-stage__docked" :person="s.popup.value.group[0]"
                   :data="s.card.data.value" :loading="s.card.loading.value" docked @close="s.closePopup"
                   @archive="openArchive" @place="s.goPlace" />

    <PlacementPanel
      v-if="s.placing.value"
      v-model:filter="s.placeFilter.value"
      v-model:keyword="s.placeKeyword.value"
      v-model:name="s.nameDraft.value"
      :total="s.stations.data.value?.total ?? null"
      :unplaced="s.stations.data.value?.unplaced ?? 0"
      :list="s.placeList.value"
      :selected="s.selectedStation.value"
      :pending="s.pending.value !== null"
      :dirty="s.dirty.value"
      :saving="s.saving.value"
      @select="s.selectStation"
      @save="s.saveSelected"
      @cancel="s.cancelEdit"
      @delete="s.deleteOpen.value = true"
      @close="s.exitPlacement"
      @logs="s.logsOpen.value = true"
    />

    <StationLogsDrawer :open="s.logsOpen.value" :station-code="s.selectedStation.value?.stationCode ?? null"
                       :station-name="s.selectedStation.value?.name ?? null" @close="s.logsOpen.value = false" />

    <ScreenModal :open="s.deleteOpen.value" title="删除基站位置" @close="s.deleteOpen.value = false">
      <p>确定删除「{{ s.selectedStation.value?.name }}」在地图上的位置吗？删除后它回到"未摆放"，在它附近的人地图上不再显示。</p>
      <template #footer>
        <ScreenButton kind="plain" @click="s.deleteOpen.value = false">取消</ScreenButton>
        <ScreenButton @click="s.deleteSelected">删除</ScreenButton>
      </template>
    </ScreenModal>
  </div>
</template>

<script setup lang="ts">
// 动态数据页中间主区域·地图模式（docs/05 第五节、docs/06 第四～六节、docs/09 第四节）。坐标是相对主区域左上角的像素。
// 状态和动作在 map/use-map-stage.ts，地图图层和样式在 map/mine-map.ts。
import 'ol/ol.css'
import { ref, toRef } from 'vue'
import { useRouter } from 'vue-router'
import type { WatchState } from '@/api/dashboard'
import EmptyState from '@/components/EmptyState.vue'
import ScreenButton from '@/components/ScreenButton.vue'
import ScreenIcon from '@/components/ScreenIcon.vue'
import ScreenModal from '@/components/ScreenModal.vue'
import { cardNo } from '@/utils/format'
import MapPersonCard from '../map/MapPersonCard.vue'
import PlacementPanel from '../map/PlacementPanel.vue'
import StationLogsDrawer from '../map/StationLogsDrawer.vue'
import { useMapStage } from '../map/use-map-stage'
import ModeButton from './ModeButton.vue'
import StageButton from './StageButton.vue'

const props = defineProps<{
  /** 要定位的人（人员表点某一行，地址里的 ?card=）；seq 每点一次加 1，同一个人再点也重新定位 */
  locate: { card: string | null; seq: number }
}>()
const emit = defineEmits<{ toggleMode: [] }>()

const mapEl = ref<HTMLElement | null>(null)
const popupEl = ref<HTMLElement | null>(null)
const s = useMapStage({ mapEl, popupEl }, toRef(props, 'locate'))

const unplacedOpen = ref(false)
const WATCH_TEXT: Record<WatchState, string> = { ONLINE: '手表在线', OFFLINE: '手表离线', UNBOUND: '未绑定手表' }

const router = useRouter()

/** 体征卡的"档案"：跳到这个人的个人档案（docs/05 第五节） */
function openArchive() {
  const card = s.popup.value?.group[0]?.cardCode
  if (card) void router.push(`/archive/${card}`)
}
</script>

<style scoped>
.map-stage {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

/* 深色带纹理的底，像暗色的岩石（docs/09 第四节）：几层渐变 + 细噪点 */
.map-stage__map {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 30% 35%, var(--map-rock-1), transparent 55%),
    radial-gradient(ellipse at 72% 68%, var(--map-rock-2), transparent 60%),
    radial-gradient(ellipse at 55% 15%, var(--map-rock-3), transparent 50%),
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='240' height='240'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='3' stitchTiles='stitch'/%3E%3CfeColorMatrix values='0 0 0 0 0.6 0 0 0 0 0.7 0 0 0 0 0.68 0 0 0 0.09 0'/%3E%3C/filter%3E%3Crect width='240' height='240' filter='url(%23n)'/%3E%3C/svg%3E"),
    linear-gradient(160deg, var(--map-bg-top), var(--map-bg-mid) 55%, var(--map-bg-bottom));
}

.map-stage__config-error {
  position: absolute;
  inset: 0;
}

.map-stage__btn {
  position: absolute;
  left: 19px;
  width: 195px;
  height: 51px;
  z-index: 2;
}

.map-stage__btn--1 {
  top: 19px;
}

.map-stage__btn--2 {
  top: 100px;
}

.map-stage__btn--on :deep(.stage-button__text),
.map-stage__btn--on :deep(.stage-button__icon) {
  color: var(--min-yellow);
}

.map-stage__btn--on::before {
  filter: brightness(1.5);
}

.map-stage__filter-note {
  position: absolute;
  left: 22px;
  top: 165px;
  z-index: 2;
  margin: 0;
  padding: 2px 10px;
  font-size: 13px;
  color: var(--min-yellow);
  background: var(--map-chip-bg);
  border-radius: 2px;
}

/* 搜索框 (885,165)-(1290,210) */
.map-stage__search {
  position: absolute;
  left: 800px;
  top: 22px;
  z-index: 4;
  width: 389px;
  height: 50px;
}

.map-stage__search-input {
  width: 100%;
  height: 100%;
  padding: 0 52px 0 22px;
  border: 1px solid var(--border-glow);
  border-radius: 25px;
  background: var(--map-control-bg);
  box-shadow: inset 0 0 12px var(--accent-bg);
  color: var(--text-primary);
  font-size: 15px;
  outline: none;
}

.map-stage__search-input::placeholder {
  color: var(--text-dim-white);
}

.map-stage__search-input:focus {
  border-color: var(--accent);
}

.map-stage__refresh {
  position: absolute;
  right: 14px;
  top: 12px;
  width: 26px;
  height: 26px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--text-bright);
  cursor: pointer;
}

.map-stage__refresh--busy {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.map-stage__results,
.map-stage__unplaced-list,
.map-stage__group ul {
  margin: 0;
  padding: 4px 0;
  list-style: none;
}

.map-stage__results {
  position: absolute;
  left: 12px;
  right: 12px;
  top: 54px;
  max-height: 300px;
  overflow-y: auto;
  background: var(--bg-panel-strong);
  border: 1px solid var(--border-glow);
}

.map-stage__results button,
.map-stage__unplaced-list button,
.map-stage__group li button {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 6px 12px;
  border: 0;
  background: none;
  color: var(--text-primary);
  font-size: 14px;
  text-align: left;
  cursor: pointer;
}

.map-stage__results button:hover,
.map-stage__unplaced-list button:hover,
.map-stage__group li button:hover {
  background: var(--accent-bg);
}

.map-stage__muted {
  color: var(--text-secondary);
  font-size: 13px;
}

.map-stage__unplaced {
  margin-left: auto;
  padding: 0 6px;
  font-size: 12px;
  color: var(--warn);
  border: 1px solid var(--warn-border);
  border-radius: 2px;
}

.map-stage__mode {
  position: absolute;
  left: 1204px;
  top: 21px;
  z-index: 4;
  width: 128px;
  height: 54px;
}

.map-stage__place-btn {
  position: absolute;
  left: 1204px;
  top: 86px;
  z-index: 4;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 128px;
  height: 34px;
  border: 1px solid var(--accent);
  border-radius: 17px;
  background: var(--map-control-bg);
  color: var(--accent);
  font-size: 15px;
  cursor: pointer;
}

.map-stage__place-btn :deep(svg) {
  width: 18px;
  height: 18px;
}

.map-stage__place-btn:hover {
  background: var(--accent-bg);
}

/* 左下角小圆形按钮 (68,600)-(102,635) */
.map-stage__tower {
  position: absolute;
  left: 15px;
  top: 508px;
  z-index: 2;
  width: 36px;
  height: 36px;
  padding: 6px;
  border: 1px solid var(--map-control-border);
  border-radius: 50%;
  background: var(--map-tower-bg);
  color: var(--text-bright);
  cursor: pointer;
}

.map-stage__tower--off {
  background: var(--map-tower-off-bg);
  color: var(--text-secondary);
}

.map-stage__unplaced-box {
  position: absolute;
  right: 14px;
  bottom: 12px;
  z-index: 2;
  width: 330px;
  background: var(--map-float-bg);
  border: 1px solid var(--warn-border);
}

.map-stage__unplaced-toggle {
  width: 100%;
  padding: 6px 12px;
  border: 0;
  background: none;
  color: var(--warn);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.map-stage__unplaced-list {
  max-height: 220px;
  overflow-y: auto;
  border-top: 1px solid var(--border-faint);
}

.map-stage__notice {
  position: absolute;
  left: 50%;
  top: 84px;
  z-index: 3;
  margin: 0;
  padding: 4px 14px;
  transform: translateX(-50%);
  font-size: 14px;
  color: var(--map-notice-text);
  background: var(--map-notice-bg);
  border-radius: 2px;
  white-space: nowrap;
}

.map-stage__notice--place {
  top: 84px;
  left: calc(50% + 190px);
  color: var(--map-place-notice-text);
  background: var(--map-place-notice-bg);
}

.map-stage__failed {
  position: absolute;
  left: 60px;
  bottom: 8px;
  z-index: 2;
  margin: 0;
  font-size: 12px;
  color: var(--warn);
}

.map-stage__docked {
  position: absolute;
  left: 50%;
  top: 86px;
  z-index: 5;
  transform: translateX(-50%);
}

.map-stage__group {
  position: relative;
  width: 260px;
  background: var(--map-float-bg);
  border: 1px solid var(--accent-soft);
  font-size: 14px;
}

.map-stage__group-title {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: 0;
  padding: 6px 8px 6px 12px;
  border-bottom: 1px solid var(--border-faint);
  color: var(--accent);
}

.map-stage__group-title button {
  width: 18px;
  height: 18px;
  margin-left: auto;
  padding: 0;
  border: 0;
  background: none;
  color: var(--text-primary);
  cursor: pointer;
}

.map-stage__group ul {
  max-height: 240px;
  overflow-y: auto;
}

.map-stage__alerting {
  color: var(--danger);
  font-weight: 700;
}

.map-stage__group-arrow {
  position: absolute;
  left: 50%;
  bottom: -8px;
  margin-left: -8px;
  border-left: 8px solid transparent;
  border-right: 8px solid transparent;
  border-top: 8px solid var(--accent-soft);
}

/* 摆放模式：地图上的按钮让开左边的列表 */
.map-stage--placing .map-stage__search {
  left: 400px;
  width: 360px;
}
</style>
