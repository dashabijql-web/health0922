/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_BASE_API?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

/** Methods exported from legacy Options API helpers run on the host component. */
type LegacyVueOptions = Record<string, any> & ThisType<Record<string, any>>

declare module '*.vue' {
  import type { DefineComponent } from 'vue'

  const component: DefineComponent<{}, {}, any>
  export default component
}
