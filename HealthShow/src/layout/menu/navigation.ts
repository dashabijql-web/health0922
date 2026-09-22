export const NAV_GROUPS = [
  { key: 'command', title: '指挥中心', icon: 'HomeFilled', order: 1, path: '/__nav__/command' },
  { key: 'monitor', title: '监测中心', icon: 'DataAnalysis', order: 2, path: '/__nav__/monitor' },
  {
    // 风险事件中心的具体页面已经通过 WarningCenterNav 提供统一切换入口，
    // 不再作为独立一级菜单展示，而是并入指挥中心子菜单，默认打开“总览”。
    key: 'warning',
    title: '风险事件中心',
    icon: 'Bell',
    order: 3,
    path: '/__nav__/warning',
    singleEntry: true,
    parentGroup: 'command',
    preferredPaths: ['/health-monitor/risk-warning', '/alert-management/notifications']
  },
  { key: 'people', title: '人员中心', icon: 'UserFilled', order: 4, path: '/__nav__/people' },
  { key: 'report', title: '报告与AI', icon: 'Document', order: 5, path: '/__nav__/report' },
  { key: 'admin', title: '系统管理', icon: 'Setting', order: 6, path: '/__nav__/admin' }
]

export const MOBILE_NAV_SLOTS = [
  {
    key: 'command',
    icon: 'DataAnalysis',
    label: '指挥',
    groups: ['command'],
    preferredPaths: ['/health-monitor/dashboard']
  },
  {
    key: 'monitor',
    icon: 'Monitor',
    label: '监测',
    groups: ['monitor'],
    preferredPaths: ['/health-monitor/real-time']
  },
  {
    key: 'entry',
    icon: 'Stamp',
    label: '准入',
    includePaths: ['/health-monitor/mine-entry'],
    preferredPaths: ['/health-monitor/mine-entry']
  },
  {
    key: 'warning',
    icon: 'Bell',
    label: '预警',
    groups: ['warning'],
    preferredPaths: ['/health-monitor/risk-warning', '/alert-management/notifications']
  },
  {
    key: 'people',
    icon: 'UserFilled',
    label: '人员',
    groups: ['people'],
    excludePaths: ['/health-monitor/mine-entry'],
    preferredPaths: ['/health-monitor/employee-archive', '/health-monitor/workbench']
  }
]

export interface NavigationRoute {
  path: string
  hidden?: boolean
  meta?: {
    navGroup?: string
    navOrder?: number
    [key: string]: unknown
  }
  children?: NavigationRoute[]
  [key: string]: unknown
}

export function resolveMenuPath(basePath: string, routePath = '') {
  if (!routePath) return basePath || '/'
  if (routePath.startsWith('/')) return routePath
  if (!basePath) return routePath.startsWith('/') ? routePath : `/${routePath}`
  return `${basePath.replace(/\/$/, '')}/${routePath}`
}

function collectLeafRoutes(routes: NavigationRoute[] = [], basePath = ''): NavigationRoute[] {
  const leaves: NavigationRoute[] = []

  for (const route of routes || []) {
    const fullPath = resolveMenuPath(basePath, route.path)

    if (route.children && route.children.length > 0) {
      leaves.push(...collectLeafRoutes(route.children, fullPath))
      continue
    }

    if (route.hidden || !route.meta?.navGroup) continue

    leaves.push({
      ...route,
      path: fullPath,
      children: undefined
    })
  }

  return leaves
}

function leavesOfGroup(leaves: NavigationRoute[], key: string) {
  return leaves
    .filter((route) => route.meta?.navGroup === key)
    .sort((a, b) => (a.meta?.navOrder || 999) - (b.meta?.navOrder || 999))
    .map((route) => ({
      ...route,
      path: route.path.startsWith('/') ? route.path : `/${route.path}`
    }))
}

// 把一个 singleEntry 分组折叠成一个可直接点击的子菜单项，
// 默认跳转到该分组的首选页面（例如风险事件中心的“总览”）。
function buildSingleEntryChild(group: (typeof NAV_GROUPS)[number], children: NavigationRoute[]) {
  if (!children.length) return null

  const defaultPath = group.preferredPaths?.find((path) =>
    children.some((child) => child.path === path)
  ) || children[0].path

  return {
    path: defaultPath,
    name: `NavGroup${group.key}`,
    meta: {
      title: group.title,
      icon: group.icon
    }
  }
}

export function buildGroupedMenuRoutes(routes: NavigationRoute[]) {
  const leaves = collectLeafRoutes(routes)
  const nestedGroups = NAV_GROUPS.filter((group) => group.parentGroup)

  return NAV_GROUPS
    .filter((group) => !group.parentGroup)
    .flatMap((group) => {
      const children = leavesOfGroup(leaves, group.key)

      const nestedChildren = nestedGroups
        .filter((nested) => nested.parentGroup === group.key)
        .map((nested) => buildSingleEntryChild(nested, leavesOfGroup(leaves, nested.key)))
        .filter((child): child is NonNullable<typeof child> => Boolean(child))

      const allChildren = [...children, ...nestedChildren]

      if (!allChildren.length) return []

      if (group.singleEntry) {
        const entry = buildSingleEntryChild(group, allChildren)
        return entry ? [entry] : []
      }

      return [{
        path: group.path,
        name: `NavGroup${group.key}`,
        alwaysShow: true,
        meta: {
          title: group.title,
          icon: group.icon
        },
        children: allChildren
      }]
    })
}

export function buildMobileNavItems(routes: NavigationRoute[]) {
  const leaves = collectLeafRoutes(routes)
  const pathSet = new Set(leaves.map((route) => route.path))

  return MOBILE_NAV_SLOTS
    .map((slot) => {
      const slotLeaves = leaves.filter((route) => {
        const path = route.path
        if (slot.includePaths?.includes(path)) return true
        if (slot.excludePaths?.includes(path)) return false
        return typeof route.meta?.navGroup === 'string' && slot.groups?.includes(route.meta.navGroup)
      })

      if (!slotLeaves.length) return null

      const matches = [...new Set(slotLeaves.map((route) => route.path))]
      const preferredPath = (slot.preferredPaths || []).find((path) => pathSet.has(path))
      const path = preferredPath || slotLeaves[0].path

      return {
        key: slot.key,
        path,
        icon: slot.icon,
        label: slot.label,
        matches
      }
    })
    .filter(Boolean)
}

export function isActiveNavigationTarget(currentPath, item) {
  const matchers = item.matches || [item.path]
  return matchers.some((prefix) => currentPath.startsWith(prefix))
}
