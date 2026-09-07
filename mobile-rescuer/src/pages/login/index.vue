<template>
  <view class="login-page">
    <view class="brand">施救员移动端</view>
    <view class="sub">道路交通事故救援派单系统</view>
    <view class="card form">
      <view class="field">
        <view class="field-label">用户名</view>
        <input class="field-input" v-model="username" placeholder="请输入用户名" />
      </view>
      <view class="field">
        <view class="field-label">密码</view>
        <input class="field-input" v-model="password" password placeholder="请输入密码" />
      </view>
      <view class="btn-primary" :class="{ 'btn-disabled': loading }" @click="onLogin">
        {{ loading ? '登录中…' : '登录' }}
      </view>
      <view class="hint muted">演示账号：towdriver / admin123</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { loginApi } from '../../api/auth'
import { setSession, hasRescuerAccess, getUserState } from '../../stores/user'
import { onShow } from '@dcloudio/uni-app'

const username = ref('towdriver')
const password = ref('admin123')
const loading = ref(false)

onShow(() => {
  if (getUserState().token && hasRescuerAccess()) {
    uni.switchTab({ url: '/pages/task/list' })
  }
})

async function onLogin() {
  if (loading.value) return
  if (!username.value || !password.value) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const res = await loginApi({ username: username.value, password: password.value })
    const data = res.data || {}
    const permissions = data.permissions || []
    if (!hasRescuerAccess(permissions)) {
      uni.showToast({ title: '无施救员移动端权限', icon: 'none' })
      return
    }
    setSession(data)
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => {
      uni.switchTab({ url: '/pages/task/list' })
    }, 300)
  } catch (e) {
    uni.showToast({ title: e.message || '登录失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  padding: 120rpx 40rpx 40rpx;
  background: linear-gradient(180deg, #e8f0ff 0%, #f5f6f8 45%);
  box-sizing: border-box;
}
.brand {
  font-size: 48rpx;
  font-weight: 700;
  color: #1a1a1a;
}
.sub {
  margin-top: 12rpx;
  margin-bottom: 48rpx;
  color: #666;
  font-size: 26rpx;
}
.form {
  padding: 36rpx 28rpx;
}
.hint {
  margin-top: 28rpx;
  text-align: center;
}
</style>
