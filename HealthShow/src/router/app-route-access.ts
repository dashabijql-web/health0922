import appRoutes from './app-routes.ts'

interface AccessRoute {
  path?: string
  hidden?: boolean
  children?: AccessRoute[]
  meta?: { permCode?: string; [key: string]: unknown }
  [key: string]: unknown
}

function cloneRouteRecord(route: AccessRoute): AccessRoute {
  return {
    ...route,
    meta: route.meta ? { ...route.meta } : route.meta,
    children: route.children ? route.children.map(cloneRouteRecord) : route.children
  }
}

export function getAppRouteDefinitions(): AccessRoute[] {
  return appRoutes.map(cloneRouteRecord)
}

export function filterRoutesByPermissions(routes: AccessRoute[], permCodes: string[] | null | undefined): AccessRoute[] {
  if (permCodes === null || permCodes === undefined) return routes.map(cloneRouteRecord)
  if (permCodes.length === 0) return []

  const visibleRoutes: AccessRoute[] = []

  for (const route of routes) {
    const record = cloneRouteRecord(route)

    if (record.hidden) {
      visibleRoutes.push(record)
      continue
    }

    if (record.children && record.children.length > 0) {
      record.children = filterRoutesByPermissions(record.children, permCodes)
      if (record.children.length > 0) {
        visibleRoutes.push(record)
      }
      continue
    }

    if (!record.meta?.permCode || permCodes.includes(record.meta.permCode)) {
      visibleRoutes.push(record)
    }
  }

  return visibleRoutes
}

export function getPermittedAppRoutes(permCodes: string[] | null | undefined): AccessRoute[] {
  return filterRoutesByPermissions(getAppRouteDefinitions(), permCodes)
}
