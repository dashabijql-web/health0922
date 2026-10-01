<template>
  <!-- 仅开发模式：公共组件和颜色变量的预览，方便对照 docs/07。这里的文字都是示例，不是业务数据。 -->
  <div class="preview">
    <div class="preview__banners">
      <NoticeBanner type="warn" message="示例：定位数据已过期，最后更新 hh:mm" />
      <NoticeBanner type="danger" message="示例：刷新失败" />
      <NoticeBanner type="info" message="示例：普通提示" />
    </div>

    <div class="preview__grid">
      <Panel title="暂无数据">
        <EmptyState />
      </Panel>
      <Panel title="未录入">
        <EmptyState kind="not-recorded" />
      </Panel>
      <Panel title="加载失败">
        <EmptyState kind="error" />
      </Panel>
      <Panel title="加载中" loading />
      <Panel title="刷新失败" failed-at="hh:mm">
        <p class="preview__text">示例：保留上次数据，角落提示刷新失败</p>
      </Panel>
      <Panel title="标题栏右侧">
        <template #extra><span class="preview__tag">示例标记</span></template>
        <p class="preview__text">单元格内的 <EmptyState kind="not-recorded" inline /> 和 <EmptyState inline /></p>
        <p class="preview__num num">0123456789</p>
      </Panel>
    </div>

    <div class="preview__swatches">
      <div v-for="name in TOKENS" :key="name" class="swatch">
        <span class="swatch__color" :style="{ background: `var(${name})` }"></span>
        <code>{{ name }}</code>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import EmptyState from '@/components/EmptyState.vue'
import NoticeBanner from '@/components/NoticeBanner.vue'
import Panel from '@/components/Panel.vue'

const TOKENS = [
  '--bg-page', '--bg-panel', '--bg-panel-strong', '--border-glow', '--accent', '--accent-soft',
  '--text-primary', '--text-secondary', '--warn', '--danger', '--info', '--gold'
]
</script>

<style scoped>
.preview {
  position: absolute;
  left: 50px;
  right: 50px;
  top: 160px;
  bottom: 50px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.preview__banners {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preview__grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  grid-template-rows: repeat(2, 1fr);
  gap: 16px;
  min-height: 0;
}

.preview__text {
  margin: 0;
  color: var(--text-secondary);
  font-size: 16px;
}

.preview__num {
  margin: 12px 0 0;
  font-size: 40px;
  color: var(--accent);
}

.preview__tag {
  padding: 2px 10px;
  border: 1px solid var(--border-glow);
  color: var(--accent);
}

.preview__swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
}

.swatch {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
}

.swatch__color {
  width: 28px;
  height: 18px;
  border: 1px solid var(--border-glow);
}
</style>
