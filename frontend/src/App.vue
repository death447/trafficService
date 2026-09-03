<template>
  <div v-if="isLoginPage" class="auth-shell">
    <router-view />
  </div>

  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">救</div>
        <div class="brand-text">
          <strong>救援派单系统</strong>
          <span>运营管理平台</span>
        </div>
      </div>

      <nav class="side-nav">
        <p class="nav-section">工作台</p>
        <router-link
          to="/"
          class="nav-item"
          :class="{ active: route.path === '/' }"
        >
          <span class="nav-ico">⌂</span>
          概览
        </router-link>

        <p class="nav-section">业务调度</p>
        <router-link
          v-auth="'dispatch:manage'"
          to="/dispatches"
          class="nav-item"
          active-class="active"
        >
          <span class="nav-ico">任</span>
          任务管理
        </router-link>
        <router-link
          v-auth="'vehicle:manage'"
          to="/vehicles"
          class="nav-item"
          active-class="active"
        >
          <span class="nav-ico">车</span>
          施救车辆
        </router-link>

        <p class="nav-section">系统管理</p>
        <router-link
          v-auth="'user:manage'"
          to="/users"
          class="nav-item"
          active-class="active"
        >
          <span class="nav-ico">用</span>
          用户管理
        </router-link>
        <router-link
          v-auth="'role:manage'"
          to="/roles"
          class="nav-item"
          active-class="active"
        >
          <span class="nav-ico">角</span>
          角色管理
        </router-link>
        <router-link
          v-auth="'permission:manage'"
          to="/permissions"
          class="nav-item"
          active-class="active"
        >
          <span class="nav-ico">权</span>
          权限管理
        </router-link>
      </nav>

      <div class="sidebar-foot">
        <span>内部系统 · 机密</span>
      </div>
    </aside>

    <div class="main-area">
      <header class="topbar">
        <div class="topbar-left">
          <h1 class="topbar-title">{{ pageTitle }}</h1>
        </div>
        <div class="topbar-right">
          <div class="user-chip">
            <span class="avatar">{{ avatarText }}</span>
            <div class="user-meta">
              <strong>{{ displayName }}</strong>
              <span>{{ roleLabel }}</span>
            </div>
          </div>
          <button type="button" class="secondary" @click="onLogout">退出登录</button>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from './stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const isLoginPage = computed(() => route.path === '/login')

const pageTitle = computed(() => {
  const path = route.path
  if (path === '/dispatches/new') return '新建工单'
  if (/^\/dispatches\/[^/]+$/.test(path)) return '工单详情'
  const map = {
    '/': '工作台概览',
    '/users': '用户管理',
    '/roles': '角色管理',
    '/permissions': '权限管理',
    '/vehicles': '施救车辆',
    '/dispatches': '任务管理',
    '/403': '访问受限'
  }
  return map[path] || '救援派单系统'
})

const displayName = computed(() => store.username || '未登录用户')

const avatarText = computed(() => {
  const name = displayName.value
  return name ? name.slice(0, 1).toUpperCase() : '?'
})

const roleLabel = computed(() => {
  if (!store.roles?.length) return '已登录'
  const labels = {
    ADMIN: '系统管理员',
    TRAFFIC_POLICE: '交警',
    DISPATCHER: '调度员',
    TOW_DRIVER: '施救员',
    PARKING_ADMIN: '停车场管理员'
  }
  return store.roles.map((r) => labels[r] || r).join(' / ')
})

function onLogout() {
  store.logout()
  router.push('/login')
}
</script>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  width: var(--sidebar-width);
  flex-shrink: 0;
  background: linear-gradient(180deg, #18263c 0%, #121c2d 100%);
  color: var(--text-inverse);
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
}

.brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1.15rem 1.1rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-mark {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: linear-gradient(145deg, #3aa0df, #1f6fa8);
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 0.95rem;
  color: #fff;
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
}

.brand-text strong {
  font-size: 0.92rem;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.brand-text span {
  font-size: 0.72rem;
  color: var(--text-inverse-muted);
}

.side-nav {
  flex: 1;
  padding: 0.85rem 0.7rem;
  overflow: auto;
}

.nav-section {
  margin: 0.85rem 0.65rem 0.4rem;
  font-size: 0.7rem;
  color: var(--text-inverse-muted);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding: 0.62rem 0.75rem;
  margin-bottom: 0.2rem;
  border-radius: 6px;
  color: var(--text-inverse-muted);
  font-size: 0.875rem;
  transition: background 0.15s ease, color 0.15s ease;
}

.nav-item:hover {
  background: var(--bg-sidebar-hover);
  color: var(--text-inverse);
}

.nav-item.active {
  background: var(--bg-sidebar-active);
  color: #fff;
  font-weight: 600;
}

.nav-ico {
  width: 22px;
  height: 22px;
  border-radius: 5px;
  background: rgba(255, 255, 255, 0.08);
  display: grid;
  place-items: center;
  font-size: 0.7rem;
  flex-shrink: 0;
}

.sidebar-foot {
  padding: 0.9rem 1.1rem 1.1rem;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 0.7rem;
  color: var(--text-inverse-muted);
}

.main-area {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: var(--topbar-height);
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 1.5rem;
  position: sticky;
  top: 0;
  z-index: 20;
}

.topbar-title {
  font-size: 1rem;
  font-weight: 600;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 0.85rem;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--accent-hover);
  display: grid;
  place-items: center;
  font-size: 0.85rem;
  font-weight: 600;
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.user-meta strong {
  font-size: 0.85rem;
  font-weight: 600;
}

.user-meta span {
  font-size: 0.72rem;
  color: var(--text-secondary);
}

.content {
  flex: 1;
}

.auth-shell {
  min-height: 100vh;
}

@media (max-width: 860px) {
  .app-shell {
    flex-direction: column;
  }

  .sidebar {
    width: 100%;
    height: auto;
    position: relative;
  }

  .side-nav {
    display: flex;
    flex-wrap: wrap;
    gap: 0.25rem;
    padding: 0.6rem;
  }

  .nav-section {
    width: 100%;
    margin: 0.4rem 0.4rem 0.15rem;
  }

  .sidebar-foot {
    display: none;
  }

  .user-meta {
    display: none;
  }
}
</style>
