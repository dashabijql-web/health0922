import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/ScreenLayout.vue'),
    children: [
      { path: '', redirect: '/portal' },
      {
        path: 'portal',
        name: 'portal',
        component: () => import('@/views/portal/PortalView.vue'),
        meta: { title: '入口' }
      },
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '动态数据', nav: true }
      },
      {
        path: 'archive',
        name: 'archive',
        component: () => import('@/views/archive/ArchiveListView.vue'),
        meta: { title: '健康档案', nav: true }
      },
      {
        // 卡编码 17 位字母数字；不合格的地址落到最后的兜底，回入口页
        path: 'archive/:cardCode([0-9A-Za-z]{17})',
        name: 'person-archive',
        component: () => import('@/views/archive/PersonArchiveView.vue'),
        meta: { title: '个人档案', nav: true }
      }
    ]
  }
]

// 公共组件预览页，只在开发模式下存在，生产构建里没有
if (import.meta.env.DEV) {
  routes.push({
    path: '/dev/components',
    component: () => import('@/layouts/ScreenLayout.vue'),
    children: [
      {
        path: '',
        name: 'dev-components',
        component: () => import('@/views/dev/ComponentsPreview.vue'),
        meta: { title: '组件预览' }
      }
    ]
  })
}

routes.push({ path: '/:pathMatch(.*)*', redirect: '/portal' })

export const router = createRouter({
  history: createWebHashHistory(),
  routes
})

/** 登录后跳回的地址只接受站内路径，防止被带到别的网站。 */
export function safeRedirect(value: unknown): string {
  const path = Array.isArray(value) ? value[0] : value
  if (typeof path === 'string' && path.startsWith('/') && !path.startsWith('//') && !path.startsWith('/login')) {
    return path
  }
  return '/portal'
}

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    return to.name === 'login' && auth.isLoggedIn ? safeRedirect(to.query.redirect) : true
  }
  if (!auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (!auth.user) {
    try {
      await auth.loadUser()
    } catch {
      // 令牌失效（401）时 request.ts 已清掉令牌；其他错误也回登录页
      auth.reset()
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }
  return true
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : ''
  document.title = title ? `${title} · 职工健康管理系统` : '职工健康管理系统'
})

router.onError((error) => {
  console.error('[router] 页面加载失败', error)
})
