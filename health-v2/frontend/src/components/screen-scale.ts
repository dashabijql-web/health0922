import type { ComputedRef, InjectionKey } from 'vue'

/** 当前缩放比例（设计画布 1920×1080 → 窗口），地图等需要换算鼠标坐标的组件用得到。 */
export const SCREEN_SCALE_KEY: InjectionKey<ComputedRef<number>> = Symbol('screen-scale')
