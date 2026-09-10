import { getUserState, logout } from '../stores/user'
import { resolveLoginTarget } from './workspace.js'

const WORKBENCH = '/pages/workbench/index'
const SELECT = '/pages/workspace/select'
const LOGIN = '/pages/login/index'

export function requirePageAccess(kind) {
  const state = getUserState()
  if (kind === 'public') {
    if (state.token && state.workspace) {
      uni.switchTab({ url: WORKBENCH })
      return false
    }
    return true
  }
  if (!state.token) {
    uni.reLaunch({ url: LOGIN })
    return false
  }
  const target = resolveLoginTarget(state.permissions)
  if (target === 'none') {
    logout()
    return false
  }
  if (!state.workspace) {
    if (kind !== 'select') {
      uni.reLaunch({ url: SELECT })
      return false
    }
    return true
  }
  if (kind === 'select') {
    uni.switchTab({ url: WORKBENCH })
    return false
  }
  if (kind === 'rescuer' && state.workspace !== 'rescuer') {
    uni.switchTab({ url: WORKBENCH })
    return false
  }
  if (kind === 'parking' && state.workspace !== 'parking') {
    uni.switchTab({ url: WORKBENCH })
    return false
  }
  return true
}

export function applyWorkbenchTabText(workspace) {
  uni.setTabBarItem({
    index: 0,
    text: workspace === 'parking' ? '扣车' : '任务'
  })
}
