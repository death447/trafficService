import { defineStore } from 'pinia'
import { loginApi } from '../api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userId: localStorage.getItem('userId') ? Number(localStorage.getItem('userId')) : null,
    username: localStorage.getItem('username') || '',
    permissions: JSON.parse(localStorage.getItem('permissions') || '[]'),
    roles: JSON.parse(localStorage.getItem('roles') || '[]')
  }),
  actions: {
    async login(username, password) {
      const res = await loginApi({ username, password })
      this.token = res.data.token
      this.userId = res.data.userId
      this.username = res.data.username
      this.permissions = res.data.permissions || []
      this.roles = res.data.roles || []
      localStorage.setItem('token', this.token)
      localStorage.setItem('userId', String(this.userId ?? ''))
      localStorage.setItem('username', this.username || '')
      localStorage.setItem('permissions', JSON.stringify(this.permissions))
      localStorage.setItem('roles', JSON.stringify(this.roles))
    },
    logout() {
      this.token = ''
      this.userId = null
      this.username = ''
      this.permissions = []
      this.roles = []
      localStorage.removeItem('token')
      localStorage.removeItem('userId')
      localStorage.removeItem('username')
      localStorage.removeItem('permissions')
      localStorage.removeItem('roles')
    },
    hasPermission(code) {
      return this.permissions.includes(code)
    }
  }
})
