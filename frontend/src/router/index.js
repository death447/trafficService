import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/Forbidden.vue')
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue')
  },
  {
    path: '/users',
    name: 'UserList',
    component: () => import('../views/user/UserList.vue'),
    meta: { permissions: ['user:manage'] }
  },
  {
    path: '/roles',
    name: 'RoleList',
    component: () => import('../views/role/RoleList.vue'),
    meta: { permissions: ['role:manage'] }
  },
  {
    path: '/permissions',
    name: 'PermissionList',
    component: () => import('../views/permission/PermissionList.vue'),
    meta: { permissions: ['permission:manage'] }
  },
  {
    path: '/vehicles',
    name: 'VehicleList',
    component: () => import('../views/vehicle/VehicleList.vue'),
    meta: { permissions: ['vehicle:manage'] }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const store = useUserStore()
  if (to.path !== '/login' && !store.token) {
    next('/login')
  } else if (to.meta.permissions) {
    const ok = to.meta.permissions.every((p) => store.hasPermission(p))
    ok ? next() : next('/403')
  } else {
    next()
  }
})

export default router
