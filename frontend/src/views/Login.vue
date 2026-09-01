<template>
  <div class="login">
    <form class="login-form" @submit.prevent="onSubmit">
      <h1>登录</h1>
      <label>
        用户名
        <input v-model.trim="username" type="text" autocomplete="username" required />
      </label>
      <label>
        密码
        <input v-model="password" type="password" autocomplete="current-password" required />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <button type="submit" :disabled="loading">
        {{ loading ? '登录中…' : '登录' }}
      </button>
    </form>
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
.login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f6f8;
}

.login-form {
  width: 320px;
  padding: 2rem;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

h1 {
  font-size: 1.25rem;
  text-align: center;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.9rem;
}

input {
  padding: 0.5rem 0.75rem;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
}

button {
  padding: 0.6rem;
  border: none;
  border-radius: 4px;
  background: #1677ff;
  color: #fff;
  cursor: pointer;
}

button:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.error {
  color: #d4380d;
  font-size: 0.85rem;
}
</style>
