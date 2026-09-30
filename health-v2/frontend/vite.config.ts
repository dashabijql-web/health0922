import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发期把 /api 和 /actuator 转给本项目后端（8081），不碰老项目的 8080
const backend = process.env.VITE_BACKEND ?? 'http://127.0.0.1:8081'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    host: '127.0.0.1',
    port: 9529,
    strictPort: true,
    proxy: {
      '/api': { target: backend, changeOrigin: true },
      '/actuator': { target: backend, changeOrigin: true }
    }
  },
  preview: { port: 9529, strictPort: true }
})
