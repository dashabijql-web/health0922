import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'

// 字体随项目打包，不走 CDN（docs/07 第一部分"三、字体"）
import '@fontsource/noto-sans-sc/400.css'
import '@fontsource/noto-sans-sc/700.css'
import '@fontsource/orbitron/500.css'
import '@fontsource/orbitron/700.css'

import './styles/tokens.css'
import './styles/element.css'
import './styles/base.css'

import App from './App.vue'
import { router } from './router'
import { onUnauthorized } from './api/request'
import { useAuthStore } from './stores/auth'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

onUnauthorized(() => {
  useAuthStore(pinia).reset()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    void router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
