import 'axios'

declare module 'axios' {
  interface AxiosRequestConfig {
    silentError?: boolean
    surviveNavigation?: boolean
  }

  interface InternalAxiosRequestConfig {
    silentError?: boolean
    surviveNavigation?: boolean
  }
}
