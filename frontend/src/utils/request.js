import axios from 'axios'
import { useUserStore } from '../stores/user'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const store = useUserStore()
  if (store.token) {
    config.headers.Authorization = `Bearer ${store.token}`
  }
  return config
})

request.interceptors.response.use(
  (res) => {
    const data = res.data
    if (data.code !== 200) {
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return data
  },
  (err) => {
    if (err.response?.status === 401) {
      const store = useUserStore()
      store.logout()
      router.push('/login')
    }
    return Promise.reject(err)
  }
)

export default request
