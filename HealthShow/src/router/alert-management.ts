import Layout from './layout-component.ts'

export default {
  path: '/alert-management',
  component: Layout,
  name: 'AlertManagement',
  meta: { title: '风险事件中心', icon: 'Bell' },
  children: [
    {
      path: 'sos',
      name: 'SosPage',
      component: () => import('@/views/alert-management/sos/index.vue'),
      meta: {
        title: '设备紧急事件',
        icon: 'Warning',
        permCode: 'alert:sos',
        activeMenu: '/health-monitor/risk-warning',
        navGroup: 'warning',
        navOrder: 15
      }
    },
    {
      path: 'config',
      name: 'AlertConfig',
      component: () => import('@/views/alert-management/config/index.vue'),
      meta: {
        title: '规则配置',
        icon: 'Setting',
        permCode: 'alert:config',
        activeMenu: '/health-monitor/risk-warning',
        navGroup: 'warning',
        navOrder: 14
      }
    },
    {
      path: 'records',
      name: 'AlertRecords',
      component: () => import('@/views/alert-management/records/index.vue'),
      meta: {
        title: '处置记录',
        icon: 'List',
        permCode: 'alert:records',
        activeMenu: '/health-monitor/risk-warning',
        navGroup: 'warning',
        navOrder: 13
      }
    }
  ]
}
