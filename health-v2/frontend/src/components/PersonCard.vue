<template>
  <article class="person-card" :aria-label="person.name ?? '未录入姓名'">
    <span v-for="c in CORNERS" :key="c" :class="['person-card__corner', `person-card__corner--${c}`]" aria-hidden="true"></span>
    <AvatarRing class="person-card__avatar" :size="106" />
    <h3 class="person-card__name" :title="person.name ?? ''">{{ person.name ?? '未录入' }}</h3>
    <dl class="person-card__rows">
      <div class="person-card__row">
        <dt><svg viewBox="0 0 16 16" aria-hidden="true"><rect x="1.5" y="3" width="13" height="10" rx="1.5" /><circle cx="5.5" cy="7.5" r="1.6" /><path d="M3.2 11c.6-1.4 3.9-1.4 4.6 0M9.5 6.5h3M9.5 9h3" /></svg>卡号</dt>
        <dd class="num" :title="person.cardCode">{{ cardNo(person.cardCode) }}</dd>
      </div>
      <div class="person-card__row">
        <dt><svg viewBox="0 0 16 16" aria-hidden="true"><path d="M6.5 2h3l-.8 2.2L10.5 12 8 14.5 5.5 12l1.8-7.8z" /></svg>工种</dt>
        <dd :title="person.jobKind ?? ''">{{ person.jobKind ?? '未录入' }}</dd>
      </div>
      <div class="person-card__row">
        <dt><svg viewBox="0 0 16 16" aria-hidden="true"><circle cx="8" cy="4.5" r="2" /><circle cx="3.5" cy="8" r="1.6" /><circle cx="12.5" cy="8" r="1.6" /><path d="M4.5 14c.4-2.6 6.6-2.6 7 0M1 13c.2-1.6 3-1.9 4-.8M15 13c-.2-1.6-3-1.9-4-.8" /></svg>部门</dt>
        <dd :title="person.dept ?? ''">{{ person.dept ?? '未录入' }}</dd>
      </div>
      <div class="person-card__row">
        <dt><svg viewBox="0 0 16 16" aria-hidden="true"><path d="M8 14V7M8 9c-3 0-5-1.6-5-4.5 3 0 5 1.6 5 4.5zM8 8c0-2.6 2-4 4.8-4 0 2.6-2 4-4.8 4z" /></svg>年龄</dt>
        <dd :class="{ 'person-card__missing': person.age === null }">{{ person.age ?? '未录入' }}</dd>
      </div>
    </dl>
    <button type="button" class="person-card__open" @click="emit('open', person.cardCode)">
      <span v-for="c in CORNERS" :key="c" :class="['gold-corner', `gold-corner--${c}`]" aria-hidden="true"></span>
      健康档案
    </button>
  </article>
</template>

<script setup lang="ts">
// 健康档案卡片（docs/07 公共组件 PersonCard，样式见 docs/09 第六节"卡片内部"）：
// 左边圆形头像，右边姓名和卡号、工种、部门、年龄，底部居中金色的"健康档案"按钮。
// 卡片 344×195 按画布换算成 330×218；里面的位置按截图量出来的相对位置换算，头像是圆，只按 0.96 缩放。
import type { ArchivePerson } from '@/api/archive'
import { cardNo } from '@/utils/format'
import AvatarRing from './AvatarRing.vue'

defineProps<{ person: ArchivePerson }>()
const emit = defineEmits<{ open: [cardCode: string] }>()

const CORNERS = ['tl', 'tr', 'bl', 'br'] as const
</script>

<style scoped>
.person-card {
  position: relative;
  width: 330px;
  height: 218px;
  border: 1px solid var(--archive-card-border);
  background: radial-gradient(ellipse at 45% 30%, var(--archive-card-center), var(--archive-card-edge) 85%);
  box-shadow: inset 0 0 24px var(--overlay-shadow);
}

.person-card__corner {
  position: absolute;
  width: 12px;
  height: 12px;
  border: 2px solid var(--corner-glow);
  pointer-events: none;
}

.person-card__corner--tl { top: -1px; left: -1px; border-right: 0; border-bottom: 0; }
.person-card__corner--tr { top: -1px; right: -1px; border-left: 0; border-bottom: 0; }
.person-card__corner--bl { bottom: -1px; left: -1px; border-right: 0; border-top: 0; }
.person-card__corner--br { bottom: -1px; right: -1px; border-left: 0; border-top: 0; }

/* 截图里头像中心在卡片 (69,75)，直径约 110 */
.person-card__avatar {
  position: absolute;
  left: 13px;
  top: 31px;
}

.person-card__name {
  position: absolute;
  left: 143px;
  right: 10px;
  top: 16px;
  margin: 0;
  font-size: 29px;
  font-weight: 400;
  line-height: 40px;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.person-card__rows {
  position: absolute;
  left: 143px;
  right: 10px;
  top: 56px;
  margin: 0;
}

.person-card__row {
  display: flex;
  align-items: center;
  height: 24px;
  font-size: 17px;
}

.person-card__row dt {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 72px;
  flex-shrink: 0;
  color: var(--archive-label);
}

.person-card__row dt svg {
  width: 15px;
  height: 15px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.4;
  stroke-linejoin: round;
  stroke-linecap: round;
}

.person-card__row dd {
  margin: 0;
  min-width: 0;
  color: var(--archive-value);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.person-card__row dd.person-card__missing {
  color: var(--text-secondary);
}

/* 金色边框、金色文字的按钮，宽约 190（docs/09 第六节） */
.person-card__open {
  position: absolute;
  left: 75px;
  top: 166px;
  width: 180px;
  height: 38px;
  padding: 0;
  border: 1px solid var(--gold-btn-border);
  background: var(--gold-btn-bg);
  color: var(--gold-btn-text);
  font: inherit;
  font-size: 21px;
  letter-spacing: 1px;
  cursor: pointer;
}

.person-card__open:hover {
  filter: brightness(1.15);
}

.person-card__open:focus-visible {
  outline: 2px solid var(--gold-btn-corner);
  outline-offset: 2px;
}

.gold-corner {
  position: absolute;
  width: 7px;
  height: 7px;
  border: 2px solid var(--gold-btn-corner);
  pointer-events: none;
}

.gold-corner--tl { top: -2px; left: -2px; border-right: 0; border-bottom: 0; }
.gold-corner--tr { top: -2px; right: -2px; border-left: 0; border-bottom: 0; }
.gold-corner--bl { bottom: -2px; left: -2px; border-right: 0; border-top: 0; }
.gold-corner--br { bottom: -2px; right: -2px; border-left: 0; border-top: 0; }
</style>
