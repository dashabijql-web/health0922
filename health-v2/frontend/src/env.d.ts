/// <reference types="vite/client" />

import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    title?: string
    /** 页头是否显示"动态数据""健康档案"导航（入口页没有） */
    nav?: boolean
  }
}
