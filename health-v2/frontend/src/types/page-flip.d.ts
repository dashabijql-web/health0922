// page-flip（StPageFlip 2.0.7）没有自带类型声明，这里只声明用到的部分（来自它的 src/PageFlip.ts、Settings.ts）。
// 本项目给它打了补丁（patches/page-flip@2.0.7.patch，docs/07 第二部分"一、形态"）：destroy() 会停掉绘制循环；
// 鼠标位置按布局像素换算，书可以放在 CSS 缩放过的 1920×1080 画布里。
declare module 'page-flip' {
  /** read 静止 / fold_corner 鼠标在页角、页角卷起 / user_fold 正在拖 / flipping 正在翻 */
  export type FlipState = 'read' | 'fold_corner' | 'user_fold' | 'flipping'
  export type FlipCorner = 'top' | 'bottom'

  export interface FlipSetting {
    startPage: number
    size: 'fixed' | 'stretch'
    width: number
    height: number
    drawShadow: boolean
    flippingTime: number
    usePortrait: boolean
    startZIndex: number
    autoSize: boolean
    maxShadowOpacity: number
    showCover: boolean
    mobileScrollSupport: boolean
    clickEventForward: boolean
    useMouseEvents: boolean
    swipeDistance: number
    showPageCorners: boolean
    disableFlipByClick: boolean
  }

  export interface FlipEvent<T> {
    data: T
    object: PageFlip
  }

  export class PageFlip {
    constructor(block: HTMLElement, setting: Partial<FlipSetting>)
    loadFromHTML(items: NodeListOf<HTMLElement> | HTMLElement[]): void
    on(event: 'init', callback: (e: FlipEvent<{ page: number; mode: string }>) => void): PageFlip
    on(event: 'flip', callback: (e: FlipEvent<number>) => void): PageFlip
    on(event: 'changeState', callback: (e: FlipEvent<FlipState>) => void): PageFlip
    flipNext(corner?: FlipCorner): void
    flipPrev(corner?: FlipCorner): void
    /** 翻到第 page 页（不相邻时先跳到它旁边再翻） */
    flip(page: number, corner?: FlipCorner): void
    turnToPage(page: number): void
    getCurrentPageIndex(): number
    getPageCount(): number
    /** 移除事件监听和书的元素（打过补丁：同时停掉绘制循环） */
    destroy(): void
  }
}
