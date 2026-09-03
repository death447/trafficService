<template>
  <div class="page home">
    <section class="hero-panel">
      <div>
        <p class="eyebrow">内部工作台</p>
        <h2>欢迎回来{{ greetingName }}</h2>
        <p class="lead">
          从工作台进入任务调度、施救车辆与系统管理模块，按组织职责处理派单与权限配置。
        </p>
      </div>
      <div class="hero-stat">
        <span class="stat-label">当前角色</span>
        <strong>{{ roleText }}</strong>
      </div>
    </section>

    <section class="module-grid">
      <router-link
        v-auth="'dispatch:manage'"
        to="/dispatches"
        class="module-item"
      >
        <span class="module-tag">调度</span>
        <h3>任务管理</h3>
        <p>新建与处理救援派单工单，完成派单、结案与中止流转。</p>
      </router-link>

      <router-link
        v-auth="'vehicle:manage'"
        to="/vehicles"
        class="module-item"
      >
        <span class="module-tag">运力</span>
        <h3>施救车辆</h3>
        <p>维护拖车、清障车等救援车辆信息与空闲/忙碌状态。</p>
      </router-link>

      <router-link
        v-auth="'user:manage'"
        to="/users"
        class="module-item"
      >
        <span class="module-tag">账号</span>
        <h3>用户管理</h3>
        <p>维护内部账号、联系方式与启用状态，并分配业务角色。</p>
      </router-link>

      <router-link
        v-auth="'role:manage'"
        to="/roles"
        class="module-item"
      >
        <span class="module-tag">组织</span>
        <h3>角色管理</h3>
        <p>配置交警、调度、施救、停车场等角色及其权限集合。</p>
      </router-link>

      <router-link
        v-auth="'permission:manage'"
        to="/permissions"
        class="module-item"
      >
        <span class="module-tag">安全</span>
        <h3>权限管理</h3>
        <p>维护功能模块与操作权限，支持动态授权调整。</p>
      </router-link>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '../stores/user'

const store = useUserStore()

const greetingName = computed(() => (store.username ? `，${store.username}` : ''))

const roleText = computed(() => {
  if (!store.roles?.length) return '未分配角色'
  const labels = {
    ADMIN: '系统管理员',
    TRAFFIC_POLICE: '交警',
    DISPATCHER: '调度员',
    TOW_DRIVER: '施救员',
    PARKING_ADMIN: '停车场管理员'
  }
  return store.roles.map((r) => labels[r] || r).join('、')
})
</script>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.hero-panel {
  display: flex;
  justify-content: space-between;
  gap: 1.5rem;
  padding: 1.5rem 1.6rem;
  background:
    linear-gradient(120deg, #ffffff 0%, #f3f8fc 55%, #eaf3fa 100%);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
}

.eyebrow {
  font-size: 0.75rem;
  color: var(--accent-hover);
  letter-spacing: 0.08em;
  margin-bottom: 0.45rem;
}

.hero-panel h2 {
  font-size: 1.35rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.lead {
  max-width: 520px;
  font-size: 0.9rem;
  line-height: 1.65;
  color: var(--text-secondary);
}

.hero-stat {
  align-self: center;
  min-width: 160px;
  padding: 0.9rem 1rem;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.stat-label {
  display: block;
  font-size: 0.75rem;
  color: var(--text-secondary);
  margin-bottom: 0.35rem;
}

.hero-stat strong {
  font-size: 0.95rem;
  font-weight: 600;
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 1rem;
}

.module-item {
  display: block;
  padding: 1.2rem 1.25rem;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  color: inherit;
  transition: border-color 0.15s ease, transform 0.15s ease;
}

.module-item:hover {
  border-color: #9cc7e6;
  transform: translateY(-1px);
  color: inherit;
}

.module-tag {
  display: inline-block;
  margin-bottom: 0.7rem;
  padding: 0.15rem 0.45rem;
  font-size: 0.72rem;
  color: var(--accent-hover);
  background: var(--accent-soft);
  border-radius: 4px;
}

.module-item h3 {
  font-size: 1rem;
  font-weight: 600;
  margin-bottom: 0.45rem;
}

.module-item p {
  font-size: 0.85rem;
  line-height: 1.6;
  color: var(--text-secondary);
}

@media (max-width: 960px) {
  .hero-panel {
    flex-direction: column;
  }

  .module-grid {
    grid-template-columns: 1fr;
  }
}
</style>
