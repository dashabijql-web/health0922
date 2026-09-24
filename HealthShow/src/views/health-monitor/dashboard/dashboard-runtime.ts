import dayjs from 'dayjs'

export const dashboardRuntimeMethods: LegacyVueOptions = {
  initTime() {
    this.updateTime()
    this._timeTask?.start()
  },

  updateTime() {
    this.currentTime = dayjs().format('YYYY年MM月DD日 HH:mm:ss')
  },

  toggleFullscreen() {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch(() => {})
    } else {
      document.exitFullscreen().catch(() => {})
    }
  },

  startAutoRefresh() {
    this._refreshTask?.start()
  },

  onVisibilityChange() {
    if (document.hidden) {
      this._timeTask?.stop()
      this._refreshTask?.stop()
      this._kpiRefreshTask?.stop()
      this._refreshTextTask?.stop()
    } else {
      this.fetchData(true)
      this._timeTask?.start()
      this.startAutoRefresh()
      this._kpiRefreshTask?.start()
      this._refreshTextTask?.start()
    }
  },

  handleResize() {
    this._resizeTask?.start()
  }
}
