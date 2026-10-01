<template>
  <section class="info" aria-label="人员信息">
    <span v-for="c in CORNERS" :key="c" :class="['info__corner', `info__corner--${c}`]" aria-hidden="true"></span>
    <AvatarRing class="info__avatar" :size="106" />
    <dl class="info__rows">
      <div class="info__row info__row--name">
        <dt>姓名：</dt>
        <dd :title="detail.name ?? ''">{{ detail.name ?? '未录入' }}</dd>
      </div>
      <div class="info__row">
        <dt>年龄：</dt>
        <dd>
          <span v-if="detail.age !== null" class="num" data-testid="age"
                :title="detail.ageUpdatedBy ? `${detail.ageUpdatedBy} 于 ${detail.ageUpdatedAt} 录入` : ''">{{ detail.age }}</span>
          <span v-else class="info__missing" data-testid="age">未录入</span>
          <button type="button" class="info__edit" @click="emit('editAge')">{{ detail.age === null ? '录入' : '修改' }}</button>
        </dd>
      </div>
      <div class="info__row">
        <dt>工种：</dt>
        <dd :title="detail.jobKind ?? ''">{{ detail.jobKind ?? '未录入' }}</dd>
      </div>
      <div class="info__row">
        <dt>部门：</dt>
        <dd :title="detail.dept ?? ''">{{ detail.dept ?? '未录入' }}</dd>
      </div>
      <div class="info__row">
        <dt>卡号：</dt>
        <dd class="num" :title="detail.cardCode">{{ cardNo(detail.cardCode) }}</dd>
      </div>
    </dl>

    <!-- 截图里没有：手表状态和当前位置（docs/09 第九节） -->
    <div class="info__status">
      <p :class="['info__watch', `info__watch--${detail.watch.state.toLowerCase()}`]">{{ watchText }}</p>
      <p :title="positionTitle || positionText">{{ positionText }}</p>
    </div>
  </section>
</template>

<script setup lang="ts">
// 个人档案右侧的人员信息卡（截图 4 (1068,323)-(1418,510)，docs/09 第七节）：四角亮青色折角，左边圆形头像，
// 右边姓名（大一点，青色）、年龄、工种、部门、卡号。年龄是唯一能人工改的字段，旁边有"录入/修改"。
import { computed } from 'vue'
import type { PersonDetail } from '@/api/archive'
import AvatarRing from '@/components/AvatarRing.vue'
import { cardNo, shortTime } from '@/utils/format'

const props = defineProps<{ detail: PersonDetail }>()
const emit = defineEmits<{ editAge: [] }>()

const CORNERS = ['tl', 'tr', 'bl', 'br'] as const

const watchText = computed(() => {
  const w = props.detail.watch
  if (w.state === 'UNBOUND') return '未绑定手表'
  if (w.state === 'ONLINE') return '手表在线'
  return w.lastSeenAt ? `手表离线（最后在线 ${shortTime(w.lastSeenAt)}）` : '手表离线'
})

const positionText = computed(() => {
  const p = props.detail.position
  if (!p) return '不在井下'
  const where = [p.areaName, p.stationName ? `${p.stationName}附近` : null].filter(Boolean).join(' · ')
  return `${where || '井下'}（${shortTime(p.posTime)} 定位）`
})

const positionTitle = computed(() => {
  const p = props.detail.position
  if (!p) return ''
  const notes = [p.placed ? null : '基站未摆放', p.stationAbnormal ? '所在基站通讯异常，位置可能不准' : null]
  return notes.filter(Boolean).join('；')
})
</script>

<style scoped>
.info {
  position: relative;
  width: 336px;
  height: 209px;
  background: var(--profile-card-bg);
  box-shadow: inset 0 0 30px var(--accent-bg);
}

.info__corner {
  position: absolute;
  width: 18px;
  height: 18px;
  border: 3px solid var(--corner-glow);
  border-radius: 3px;
  pointer-events: none;
}

.info__corner--tl { top: 0; left: 0; border-right: 0; border-bottom: 0; }
.info__corner--tr { top: 0; right: 0; border-left: 0; border-bottom: 0; }
.info__corner--bl { bottom: 0; left: 0; border-right: 0; border-top: 0; }
.info__corner--br { bottom: 0; right: 0; border-left: 0; border-top: 0; }

/* 截图里头像中心在卡片 (80,93)，直径约 110 */
.info__avatar {
  position: absolute;
  left: 24px;
  top: 51px;
}

.info__rows {
  position: absolute;
  left: 149px;
  right: 12px;
  top: 22px;
  margin: 0;
}

.info__row {
  display: flex;
  align-items: center;
  height: 31px;
  font-size: 15px;
  color: var(--text-bright);
}

.info__row dt {
  width: 52px;
  flex-shrink: 0;
}

.info__row dd {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.info__row--name {
  height: 40px;
  font-size: 21px;
  color: var(--accent);
}

.info__row--name dt {
  width: 70px;
}

.info__missing {
  color: var(--text-secondary);
}

.info__edit {
  height: 22px;
  padding: 0 8px;
  border: 1px solid var(--accent-soft);
  border-radius: 2px;
  background: transparent;
  color: var(--accent);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}

.info__edit:hover {
  background: var(--accent-bg);
}

.info__status {
  position: absolute;
  left: 0;
  right: -10px;
  top: calc(100% + 10px);
  font-size: 13px;
  line-height: 20px;
  color: var(--text-secondary);
}

.info__status p {
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.info__watch--online {
  color: var(--accent);
}
</style>
