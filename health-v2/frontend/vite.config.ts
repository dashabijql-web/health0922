import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { version } from './package.json'

// 开发期把 /api 和 /actuator 转给本项目后端（8081），不碰老项目的 8080
const backend = process.env.VITE_BACKEND ?? 'http://127.0.0.1:8081'
// 矿图底图：只转 GeoServer 的 WMS（docs/06），管理页面和 REST 接口不对浏览器开放。上线时 nginx 做同样的转发
const geoserver = process.env.VITE_GEOSERVER ?? 'http://127.0.0.1:8082'

export default defineConfig({
  plugins: [vue()],
  // 月报封面写"数据来源：职工健康管理系统 <版本>"（docs/07 第二部分）
  define: { __APP_VERSION__: JSON.stringify(version) },
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    host: '127.0.0.1',
    port: 9529,
    strictPort: true,
    proxy: {
      '/api': { target: backend, changeOrigin: true },
      '/actuator': { target: backend, changeOrigin: true },
      '/geoserver/hv2/wms': { target: geoserver, changeOrigin: true }
    }
  },
  preview: { port: 9529, strictPort: true }
})
