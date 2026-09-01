<template>
  <div class="login-page">
    <div class="login-visual">
      <div class="visual-inner">
        <p class="eyebrow">道路交通事故救援</p>
        <h1>派单运营管理平台</h1>
        <p class="desc">
          统一管理交警、调度、施救与停车场人员账号，按角色动态分配业务权限。
        </p>
        <ul class="highlights">
          <li>多角色权限控制</li>
          <li>组织内账号统一管理</li>
          <li>操作留痕与安全认证</li>
        </ul>
      </div>
      <div class="visual-grid" aria-hidden="true"></div>
    </div>

    <div class="login-panel">
      <form class="login-form" @submit.prevent="onSubmit">
        <div class="form-head">
          <h2>账号登录</h2>
          <p>请使用内部账号进入系统</p>
        </div>

        <label>
          用户名
          <input
            v-model.trim="username"
            type="text"
            autocomplete="username"
            placeholder="请输入用户名"
            required
          />
        </label>
        <label>
          密码
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            required
          />
        </label>

        <p v-if="error" class="error">{{ error }}</p>

        <button type="submit" class="submit" :disabled="loading">
          {{ loading ? '正在登录…' : '登录系统' }}
        </button>

        <p class="hint">默认管理员账号请联系系统管理员获取</p>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const router = useRouter()
const store = useUserStore()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function onSubmit() {
  error.value = ''
  loading.value = true
  try {
    await store.login(username.value, password.value)
    router.push('/')
  } catch (e) {
    error.value = e.response?.data?.message || e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
}

.login-visual {
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(ellipse 80% 60% at 20% 20%, rgba(58, 160, 223, 0.28), transparent 55%),
    radial-gradient(ellipse 70% 50% at 80% 80%, rgba(20, 90, 140, 0.35), transparent 50%),
    linear-gradient(155deg, #132033 0%, #1a334f 48%, #0f1a2a 100%);
  color: #eef5fb;
  display: flex;
  align-items: center;
  padding: 3rem;
}

.visual-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.55), transparent 85%);
  pointer-events: none;
}

.visual-inner {
  position: relative;
  z-index: 1;
  max-width: 440px;
}

.eyebrow {
  font-size: 0.8rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #8eb8d8;
  margin-bottom: 0.85rem;
}

.visual-inner h1 {
  font-size: clamp(1.8rem, 3vw, 2.4rem);
  font-weight: 600;
  line-height: 1.25;
  margin-bottom: 1rem;
}

.desc {
  font-size: 0.95rem;
  line-height: 1.7;
  color: #b7c9db;
  margin-bottom: 1.75rem;
}

.highlights {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.highlights li {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  font-size: 0.9rem;
  color: #d5e4f2;
}

.highlights li::before {
  content: "";
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #3aa0df;
  flex-shrink: 0;
}

.login-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  background:
    linear-gradient(180deg, #f7f9fc 0%, #eef1f6 100%);
}

.login-form {
  width: 100%;
  max-width: 380px;
  padding: 2rem 2rem 1.75rem;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.06);
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-head h2 {
  font-size: 1.35rem;
  font-weight: 600;
  color: var(--text);
}

.form-head p {
  margin-top: 0.35rem;
  font-size: 0.875rem;
  color: var(--text-secondary);
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  font-size: 0.85rem;
  color: var(--text-secondary);
}

input {
  padding: 0.7rem 0.8rem;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  font-family: inherit;
  font-size: 0.925rem;
  color: var(--text);
  background: #fff;
}

input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(47, 143, 206, 0.16);
}

.submit {
  margin-top: 0.35rem;
  padding: 0.75rem;
  font-size: 0.95rem;
  font-weight: 600;
}

.hint {
  text-align: center;
  font-size: 0.75rem;
  color: var(--text-secondary);
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-visual {
    min-height: 240px;
    padding: 2rem 1.5rem;
  }

  .highlights {
    display: none;
  }
}
</style>
