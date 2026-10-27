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
  },
  {
    path: '/dispatches',
    name: 'DispatchList',
    component: () => import('../views/dispatch/DispatchList.vue'),
    meta: { permissions: ['dispatch:manage'] }
  },
  {
    path: '/dispatches/new',
    name: 'DispatchCreate',
    component: () => import('../views/dispatch/DispatchCreate.vue'),
    meta: { permissions: ['dispatch:add'] }
  },
  {
    path: '/dispatches/:id',
    name: 'DispatchDetail',
    component: () => import('../views/dispatch/DispatchDetail.vue'),
    meta: { permissions: ['dispatch:query'] }
  },
  {
    path: '/districts',
    name: 'DistrictList',
    component: () => import('../views/district/DistrictList.vue'),
    meta: { permissions: ['district:manage'] }
  },
  {
    path: '/parkings',
    name: 'ParkingList',
    component: () => import('../views/parking/ParkingList.vue'),
    meta: { permissions: ['parking:manage'] }
  },
  {
    path: '/detained-vehicles',
    name: 'DetainedVehicleList',
    component: () => import('../views/detain/DetainedVehicleList.vue'),
    meta: { permissions: ['detain:manage'] }
  },
  {
    path: '/detained-vehicles/:id/hangtag',
    name: 'HangtagPrint',
    component: () => import('../views/detain/HangtagPrint.vue'),
    meta: { permissions: ['detain:print'] }
  },
  {
    path: '/schedules',
    name: 'ScheduleList',
    component: () => import('../views/schedule/ScheduleList.vue'),
    meta: { permissions: ['schedule:manage'] }
  },
  {
    path: '/reports',
    name: 'Report',
    component: () => import('../views/report/Report.vue'),
    meta: { permissions: ['report:manage'] }
  },
  {
    path: '/vehicle-types',
    name: 'VehicleTypeList',
    component: () => import('../views/vehicleType/VehicleTypeList.vue'),
    meta: { permissions: ['vehicle-type:manage'] }
  },
  {
    path: '/dicts',
    name: 'DictList',
    component: () => import('../views/dict/DictList.vue'),
    meta: { permissions: ['dict:manage'] }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

let permissionsSynced = false

router.beforeEach(async (to, from, next) => {
  const store = useUserStore()
  if (to.path !== '/login' && !store.token) {
    next('/login')
    return
  }
  // 权限可能被管理员在登录后调整过，进入应用时以服务端为准同步一次
  if (store.token && !permissionsSynced) {
    permissionsSynced = true
    try {
      await store.refreshPermissions()
    } catch (e) {
      // 同步失败时沿用本地缓存，避免阻塞导航
    }
  }
  if (to.meta.permissions) {
    const ok = to.meta.permissions.every((p) => store.hasPermission(p))
    ok ? next() : next('/403')
  } else {
    next()
  }
})

export default router
