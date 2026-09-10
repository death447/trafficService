const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api'

export function getApiBase() {
  return API_BASE.replace(/\/$/, '')
}

/** Origin for static uploads: strip trailing /api */
export function getUploadOrigin() {
  return getApiBase().replace(/\/api\/?$/, '')
}

export function mediaUrl(filePath) {
  if (!filePath) return ''
  if (/^https?:\/\//i.test(filePath)) return filePath
  const path = String(filePath).replace(/^\/+/, '')
  return `${getUploadOrigin()}/uploads/${path}`
}

function clearAuthAndGoLogin() {
  uni.removeStorageSync('token')
  uni.removeStorageSync('userId')
  uni.removeStorageSync('username')
  uni.removeStorageSync('permissions')
  uni.removeStorageSync('roles')
  uni.removeStorageSync('workspace')
  uni.reLaunch({ url: '/pages/login/index' })
}

/**
 * uni.request wrapper
 * @param {{ url: string, method?: string, data?: any, header?: object, showError?: boolean }} options
 */
export function request(options = {}) {
  const { url, method = 'GET', data, header = {}, showError = true } = options
  const token = uni.getStorageSync('token')
  const fullUrl = url.startsWith('http') ? url : `${getApiBase()}${url.startsWith('/') ? '' : '/'}${url}`

  return new Promise((resolve, reject) => {
    uni.request({
      url: fullUrl,
      method,
      data,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...header
      },
      success: (res) => {
        if (res.statusCode === 401) {
          clearAuthAndGoLogin()
          reject(new Error('未登录或登录已过期'))
          return
        }
        const body = res.data
        if (res.statusCode >= 200 && res.statusCode < 300) {
          if (body && typeof body === 'object' && 'code' in body) {
            if (body.code === 200) {
              resolve(body)
              return
            }
            if (body.code === 401) {
              clearAuthAndGoLogin()
              reject(new Error(body.message || '未登录或登录已过期'))
              return
            }
            if (showError) {
              uni.showToast({ title: body.message || '请求失败', icon: 'none' })
            }
            reject(new Error(body.message || '请求失败'))
            return
          }
          resolve(body)
          return
        }
        if (showError) {
          uni.showToast({ title: `HTTP ${res.statusCode}`, icon: 'none' })
        }
        reject(new Error(`HTTP ${res.statusCode}`))
      },
      fail: (err) => {
        if (showError) {
          uni.showToast({ title: '网络异常', icon: 'none' })
        }
        reject(err)
      }
    })
  })
}

export function uploadFile({ url, filePath, name = 'file', formData = {} }) {
  const token = uni.getStorageSync('token')
  const fullUrl = url.startsWith('http') ? url : `${getApiBase()}${url.startsWith('/') ? '' : '/'}${url}`

  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: fullUrl,
      filePath,
      name,
      formData,
      header: {
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      success: (res) => {
        if (res.statusCode === 401) {
          clearAuthAndGoLogin()
          reject(new Error('未登录或登录已过期'))
          return
        }
        let body = res.data
        try {
          body = typeof body === 'string' ? JSON.parse(body) : body
        } catch (_) {
          /* keep raw */
        }
        if (body && body.code === 200) {
          resolve(body)
          return
        }
        const msg = (body && body.message) || '上传失败'
        uni.showToast({ title: msg, icon: 'none' })
        reject(new Error(msg))
      },
      fail: (err) => {
        uni.showToast({ title: '上传失败', icon: 'none' })
        reject(err)
      }
    })
  })
}

export default request
