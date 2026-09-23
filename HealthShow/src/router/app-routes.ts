import healthMonitorRouter from './health-monitor.ts'
import alertManagementRouter from './alert-management.ts'
import Layout from './layout-component.ts'

export const appRoutes = [
  healthMonitorRouter,

  alertManagementRouter,

  {
    path: '/admin',
    component: Layout,
    name: 'Admin',
    redirect: 'noRedirect',
    meta: { title: '系统管理', icon: 'Setting' },
    children: [
      {
        path: 'device-list',
        name: 'DeviceList',
        component: () => import('@/views/device-management/index.vue'),
        meta: {
          title: '设备管理',
          icon: 'Monitor',
          permCode: 'device:list',
          navGroup: 'admin',
          navOrder: 51
        }
      },
      {
        path: 'user-list',
        name: 'UserList',
        component: () => import('@/views/user-list/index.vue'),
        meta: {
          title: '用户管理',
          icon: 'UserFilled',
          permCode: 'user:list',
          navGroup: 'admin',
          navOrder: 52
        }
      },
      {
        path: 'role',
        name: 'RoleManagement',
        component: () => import('@/views/role-management/index.vue'),
        meta: {
          title: '角色管理',
          icon: 'Key',
          permCode: 'role:list',
          navGroup: 'admin',
          navOrder: 53
        }
      },
      {
        path: 'department',
        name: 'Department',
        component: () => import('@/views/org-management/department/index.vue'),
        meta: {
          title: '部门管理',
          icon: 'Grid',
          permCode: 'org:department',
          navGroup: 'admin',
          navOrder: 54
        }
      },
      {
        path: 'job-type',
        name: 'JobType',
        component: () => import('@/views/org-management/job-type/index.vue'),
        meta: {
          title: '工种管理',
          icon: 'SetUp',
          permCode: 'org:job-type',
          navGroup: 'admin',
          navOrder: 55
        }
      }
    ]
  },

  {
    path: '/ai-chat',
    component: Layout,
    meta: { title: 'AI健康助手', breadcrumb: false },
    children: [
      {
        path: 'index',
        name: 'AiChat',
        component: () => import('@/views/ai-chat/index.vue'),
        meta: {
          title: 'AI健康助手',
          icon: 'ChatDotRound',
          navGroup: 'report',
          navOrder: 42
        }
      }
    ]
  },

  {
    path: '/:pathMatch(.*)*',
    redirect: '/404',
    hidden: true
  }
]

export default appRoutes
