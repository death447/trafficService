<template>
  <div id="app">
    <header v-if="showNav" class="nav">
      <nav>
        <router-link to="/">首页</router-link>
        <router-link v-auth="'user:manage'" to="/users">用户管理</router-link>
        <router-link v-auth="'role:manage'" to="/roles">角色管理</router-link>
        <router-link v-auth="'permission:manage'" to="/permissions">权限管理</router-link>
      </nav>
      <div class="nav-right">
        <span v-if="store.username">{{ store.username }}</span>
        <button type="button" class="secondary" @click="onLogout">退出</button>
      </div>
    </header>
    <router-view />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from './stores/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const showNav = computed(() => route.path !== '/login' && !!store.token)

function onLogout() {
  store.logout()
  router.push('/login')
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

#app {
  width: 100%;
  min-height: 100vh;
  background: #f5f6f8;
}

.nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1.5rem;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
}

.nav a {
  margin-right: 1rem;
  color: #1677ff;
  text-decoration: none;
}

.nav a.router-link-active {
  font-weight: 600;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.page {
  padding: 1.5rem;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1rem;
}

.page h1,
.page h2 {
  font-size: 1.25rem;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
}

.data-table th,
.data-table td {
  padding: 0.6rem 0.75rem;
  border-bottom: 1px solid #f0f0f0;
  text-align: left;
  font-size: 0.9rem;
}

.data-table th {
  background: #fafafa;
}

.actions {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}

button {
  padding: 0.4rem 0.75rem;
  border: none;
  border-radius: 4px;
  background: #1677ff;
  color: #fff;
  cursor: pointer;
}

button.secondary {
  background: #fff;
  color: #333;
  border: 1px solid #d9d9d9;
}

button.danger {
  background: #ff4d4f;
}

button:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.error {
  color: #d4380d;
  font-size: 0.85rem;
  margin-bottom: 0.75rem;
}

.muted {
  color: #8c8c8c;
  font-size: 0.8rem;
  margin-left: 0.35rem;
}

.modal {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 4rem 1rem;
  overflow: auto;
}

.modal-card {
  width: 420px;
  max-width: 100%;
  padding: 1.5rem;
  background: #fff;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.modal-card label,
.modal-card fieldset {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.9rem;
}

.modal-card fieldset {
  border: 1px solid #f0f0f0;
  padding: 0.5rem;
  max-height: 180px;
  overflow: auto;
}

.modal-card input,
.modal-card select {
  padding: 0.45rem 0.6rem;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

.check {
  flex-direction: row;
  align-items: center;
  gap: 0.4rem;
}

.check.nested,
.tree-row.nested {
  margin-left: 1.5rem;
}

.tree {
  background: #fff;
  padding: 0.5rem 0;
}

.tree-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  border-bottom: 1px solid #f5f5f5;
}

.tree-row .actions {
  margin-left: auto;
}

.perm-groups {
  max-height: 360px;
  overflow: auto;
}

.perm-group {
  margin-bottom: 0.75rem;
}
</style>
