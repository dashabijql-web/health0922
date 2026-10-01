<template>
  <div class="profile">
    <div v-if="p.failedBanner.value" class="profile__banners">
      <NoticeBanner type="danger" :message="p.failedBanner.value" />
    </div>

    <div v-if="p.notFound.value" class="profile__missing">
      <EmptyState text="没有这个人" />
      <RouterLink to="/archive" class="zoom-btn profile__back">返回健康档案</RouterLink>
    </div>

    <template v-else>
      <!-- 左侧主区域 (52,138)-(1460,652)：3D 人体 + 人员信息卡；"预警记录"标签时换成列表 -->
      <section class="area area--stage" aria-label="个人档案">
        <div class="stage__body" :class="{ 'stage__body--hidden': p.tab.value !== 'summary' }">
          <BodyModel3D :paused="p.tab.value !== 'summary'" />
        </div>

        <div class="stage__tabs" role="tablist" aria-label="个人档案标签">
          <button type="button" role="tab" :aria-selected="p.tab.value === 'summary'"
                  :class="['stage__pill', { 'stage__pill--on': p.tab.value === 'summary' }]"
                  @click="p.tab.value = 'summary'">数据汇总</button>
          <button type="button" role="tab" :aria-selected="p.tab.value === 'alerts'" aria-label="预警记录"
                  title="预警记录" :class="['stage__round', { 'stage__round--on': p.tab.value === 'alerts' }]"
                  @click="p.tab.value = 'alerts'">
            <ScreenIcon name="alert" />
          </button>
          <span v-if="p.tab.value === 'alerts'" class="stage__tab-name">预警记录</span>
        </div>

        <div v-if="p.detail.data.value" class="stage__lists">
          <button v-for="t in LISTS" :key="t.type" type="button"
                  :class="['stage__list', { 'stage__list--on': p.detail.data.value.lists[t.type] }]"
                  :aria-pressed="!!p.detail.data.value.lists[t.type]" :disabled="p.listBusy.value !== null"
                  :title="p.detail.data.value.lists[t.type] ? listTitle(t.type) : t.hint"
                  @click="p.toggleList(t.type)">
            <ScreenIcon :name="t.icon" />
            {{ p.detail.data.value.lists[t.type] ? `移出${t.name}` : `加入${t.name}` }}
          </button>
        </div>

        <template v-if="p.tab.value === 'summary'">
          <div class="stage__info">
            <ProfileInfoCard v-if="p.detail.data.value" :detail="p.detail.data.value" @edit-age="p.openAge" />
            <div v-else-if="p.detail.loading.value" class="stage__info-skeleton" aria-busy="true"></div>
            <EmptyState v-else kind="error" />
          </div>
        </template>
        <div v-else class="stage__records">
          <AlertRecords :data="p.alerts.data.value" :loading="p.alerts.loading.value" :error="p.alerts.error.value"
                        :failed-at="p.alerts.failedAt.value" :page="p.alertPage.value" :pages="p.alertPages.value"
                        :busy="p.alertsBusy.value" @go="p.goAlertPage" />
        </div>
      </section>

      <!-- 右上 (1480,133)-(1938,410)：当前体征 -->
      <Panel class="area area--vitals vitals-panel" title="当前体征" :loading="p.detail.loading.value"
             :failed-at="p.detail.failedAt.value">
        <template #extra>
          <button type="button" class="zoom-btn" title="血压趋势" @click="p.openZoom('BLOOD_PRESSURE')">查看详情</button>
        </template>
        <VitalsGrid v-if="p.detail.data.value" :detail="p.detail.data.value" />
        <EmptyState v-else kind="error" />
      </Panel>

      <!-- 右中 (1480,428)-(1938,660)：今日血氧 -->
      <ChartPanel class="area area--spo2" title="今日血氧折线图" unit="%" :option="p.spo2Option.value"
                  :loading="p.spo2.loading.value" :failed-at="p.spo2.failedAt.value" :error="p.spo2.error.value"
                  @zoom="p.openZoom('SPO2')" />
      <!-- 底左 (48,668)-(745,920)：今日心率 -->
      <ChartPanel class="area area--heart" title="今日心率折线图" unit="次/分" :option="p.heartOption.value"
                  :loading="p.heart.loading.value" :failed-at="p.heart.failedAt.value" :error="p.heart.error.value"
                  @zoom="p.openZoom('HEART_RATE')" />
      <!-- 底中 (762,668)-(1460,920)：近 7 天步数 -->
      <ChartPanel class="area area--steps" title="运动量数据分析" unit="步数" :option="p.stepsOption.value"
                  :loading="p.steps.loading.value" :failed-at="p.steps.failedAt.value" :error="p.steps.error.value"
                  @zoom="p.openZoom('STEPS')" />
      <!-- 底右 (1480,668)-(1938,920)：今日体温 -->
      <ChartPanel class="area area--temp" title="今日体温折线图" unit="℃" :option="p.tempOption.value"
                  :loading="p.temp.loading.value" :failed-at="p.temp.failedAt.value" :error="p.temp.error.value"
                  @zoom="p.openZoom('TEMPERATURE')" />

      <ScreenModal :open="p.ageOpen.value" title="录入年龄" :width="380" @close="p.ageOpen.value = false">
        <form class="age" @submit.prevent="p.submitAge">
          <label class="age__label" for="age-input">{{ p.detail.data.value?.name ?? '' }} 的年龄（{{ MIN_AGE }}–{{ MAX_AGE }}）</label>
          <input id="age-input" v-model="p.ageInput.value" class="age__input" inputmode="numeric" maxlength="2"
                 autocomplete="off" />
          <p class="age__hint" :class="{ 'age__hint--error': p.ageInput.value !== '' && p.ageError.value }">
            {{ p.ageInput.value !== '' && p.ageError.value ? p.ageError.value : '年龄是唯一能人工修改的人员信息，保存后记操作日志' }}
          </p>
        </form>
        <template #footer>
          <ScreenButton kind="plain" @click="p.ageOpen.value = false">取消</ScreenButton>
          <ScreenButton :disabled="!!p.ageError.value || p.ageSaving.value" @click="p.submitAge">保存</ScreenButton>
        </template>
      </ScreenModal>

      <ZoomDialog :open="p.zoomOpen.value" :metric="p.zoomMetric.value" :date="p.zoomDate.value"
                  :is-today="p.zoomIsToday.value" :option="p.zoomOption.value" :loading="p.zoomLoading.value"
                  :failed="p.zoomFailed.value" :count="p.zoomTrend.value?.points.length ?? null"
                  :gap-minutes="p.zoomTrend.value?.gapMinutes ?? null" :step-days="DETAIL_STEP_DAYS"
                  @close="p.zoomOpen.value = false" @metric="p.setZoomMetric" @date="p.setZoomDate"
                  @shift="p.shiftZoomDate" />
    </template>
  </div>
</template>

<script setup lang="ts">
// 个人档案（截图 4，docs/05 第七节、docs/09 第七节）。布局按截图坐标换算到 1920×1080 画布，坐标写在下面的样式里。
// 3D 人体用到 three.js，体积大，切到本页时才加载（defineAsyncComponent）。
import { defineAsyncComponent } from 'vue'
import type { ListType } from '@/api/archive'
import EmptyState from '@/components/EmptyState.vue'
import NoticeBanner from '@/components/NoticeBanner.vue'
import Panel from '@/components/Panel.vue'
import ScreenButton from '@/components/ScreenButton.vue'
import ScreenIcon from '@/components/ScreenIcon.vue'
import ScreenModal from '@/components/ScreenModal.vue'
import type { IconName } from '@/components/icon-names'
import { DETAIL_STEP_DAYS, MAX_AGE, MIN_AGE, usePersonArchivePage } from '../use-person-archive-page'
import AlertRecords from './AlertRecords.vue'
import ChartPanel from './ChartPanel.vue'
import ProfileInfoCard from './ProfileInfoCard.vue'
import VitalsGrid from './VitalsGrid.vue'
import ZoomDialog from './ZoomDialog.vue'

const BodyModel3D = defineAsyncComponent(() => import('@/components/BodyModel3D.vue'))

const props = defineProps<{ cardCode: string }>()
const p = usePersonArchivePage(props.cardCode)

const LISTS: { type: ListType; name: string; icon: IconName; hint: string }[] = [
  { type: 'KEY', name: '重点监护', icon: 'star', hint: '加入后一直有效，直到有人移出' },
  { type: 'TODAY', name: '今日关注', icon: 'pin', hint: '今天 24 点自动失效' }
]

function listTitle(type: ListType): string {
  const e = p.detail.data.value?.lists[type]
  return e ? `${e.addedBy} 于 ${e.addedAt} 加入${e.expireDate ? `，${e.expireDate} 24 点失效` : ''}` : ''
}
</script>

<style scoped>
/* 参考坐标 (x1,y1)-(x2,y2) 换算：横向 ×0.96，纵向 ×1.116（docs/09 第一节） */
.profile {
  position: absolute;
  inset: 0;
}

.area {
  position: absolute;
}

/* 左侧主区域 (52,138)-(1460,652)：比其他面板亮一点的深蓝绿渐变 */
.area--stage {
  left: 50px;
  top: 154px;
  width: 1352px;
  height: 574px;
  overflow: hidden;
  border: 1px solid var(--border-faint);
  background:
    radial-gradient(ellipse at 50% 70%, var(--profile-stage-glow), transparent 60%),
    linear-gradient(180deg, var(--profile-stage-top), var(--profile-stage-bottom));
}

/* 右上 (1480,133)-(1938,410) */
.area--vitals { left: 1421px; top: 148px; width: 440px; height: 309px; }
/* 右中 (1480,428)-(1938,660) */
.area--spo2 { left: 1421px; top: 478px; width: 440px; height: 259px; }
/* 底左 (48,668)-(745,920) */
.area--heart { left: 46px; top: 745px; width: 669px; height: 281px; }
/* 底中 (762,668)-(1460,920) */
.area--steps { left: 732px; top: 745px; width: 670px; height: 281px; }
/* 底右 (1480,668)-(1938,920) */
.area--temp { left: 1421px; top: 745px; width: 440px; height: 281px; }

.vitals-panel :deep(.panel__body) {
  padding: 10px 12px 12px;
}

.stage__body {
  position: absolute;
  inset: 0;
}

/* 切到"预警记录"时 3D 人体留在页面上但停止绘制，切回来不用重新加载模型 */
.stage__body--hidden {
  visibility: hidden;
}

/* 左上标签 y 185–232：绿色胶囊"数据汇总" + 圆形图标按钮 */
.stage__tabs {
  position: absolute;
  left: 36px;
  top: 52px;
  display: flex;
  align-items: center;
  gap: 14px;
  z-index: 2;
}

.stage__pill {
  width: 136px;
  height: 50px;
  padding: 0;
  border: 1px solid var(--border-glow);
  border-radius: 25px;
  background: var(--profile-icon-btn);
  color: var(--text-primary);
  font: inherit;
  font-size: 24px;
  letter-spacing: 2px;
  cursor: pointer;
}

.stage__pill--on {
  border-color: transparent;
  background: linear-gradient(180deg, var(--mode-btn-top), var(--mode-btn-bottom));
  color: var(--text-bright);
  box-shadow: 0 0 12px var(--accent-glow);
}

.stage__round {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  padding: 10px;
  border: 2px solid var(--profile-icon-ring);
  border-radius: 50%;
  background: var(--profile-icon-btn);
  color: var(--text-bright);
  cursor: pointer;
}

.stage__round--on {
  border-color: var(--accent);
  background: linear-gradient(180deg, var(--mode-btn-top), var(--mode-btn-bottom));
  box-shadow: 0 0 12px var(--accent-glow);
}

.stage__tab-name {
  font-size: 20px;
  color: var(--text-primary);
}

/* 右上角：加入/移出重点监护、今日关注（docs/05 第八节） */
.stage__lists {
  position: absolute;
  right: 36px;
  top: 57px;
  display: flex;
  gap: 12px;
  z-index: 2;
}

.stage__list {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 16px;
  border: 1px solid var(--border-glow);
  border-radius: 20px;
  background: var(--profile-icon-btn);
  color: var(--text-primary);
  font: inherit;
  font-size: 16px;
  cursor: pointer;
}

.stage__list :deep(.screen-icon) {
  width: 18px;
  height: 18px;
}

.stage__list--on {
  border-color: var(--min-yellow);
  color: var(--min-yellow);
}

.stage__list:disabled {
  opacity: 0.6;
  cursor: wait;
}

/* 人员信息卡 (1068,323)-(1418,510) */
.stage__info {
  position: absolute;
  left: 975px;
  top: 206px;
  width: 336px;
  height: 209px;
  z-index: 1;
}

.stage__info-skeleton {
  width: 100%;
  height: 100%;
  background: var(--profile-card-bg);
}

.stage__records {
  position: absolute;
  left: 36px;
  right: 36px;
  top: 124px;
  bottom: 20px;
  z-index: 1;
}

.profile__banners {
  position: absolute;
  left: 812px;
  top: 52px;
  z-index: 20;
  width: 560px;
}

.profile__missing {
  position: absolute;
  left: 50px;
  top: 154px;
  width: 1820px;
  height: 600px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.profile__back {
  display: inline-grid;
  place-items: center;
  text-decoration: none;
}

.age {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.age__label {
  font-size: 15px;
}

.age__input {
  height: 38px;
  padding: 0 12px;
  border: 1px solid var(--border-glow);
  border-radius: 3px;
  outline: none;
  background: var(--input-bg);
  color: var(--text-primary);
  font: inherit;
  font-size: 20px;
}

.age__input:focus-visible {
  border-color: var(--accent);
}

.age__hint {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.age__hint--error {
  color: var(--warn);
}
</style>
