<template>
  <view class="page">
    <view class="card profile">
      <view class="name">{{ profile.realName || profile.username || '-' }}</view>
      <view class="muted">@{{ profile.username || '-' }}</view>
      <view class="line">手机：{{ profile.phone || '未填写' }}</view>
      <view class="line">邮箱：{{ profile.email || '未填写' }}</view>
    </view>

    <view class="card">
      <view class="section-title">绑定车辆</view>
      <view v-if="vehicle">
        <view class="line">车牌：{{ vehicle.plateNo || '-' }}</view>
        <view class="line muted">车辆 ID：{{ vehicle.id }}</view>
      </view>
      <view v-else class="muted">尚未绑定施救车辆</view>
    </view>

    <view class="btn-ghost" @click="goEdit">编辑资料</view>
    <view class="btn-ghost" style="margin-top: 20rpx" @click="goBind">扫码/手输绑车</view>
    <view class="btn-danger" style="margin-top: 20rpx" @click="onLogout">退出登录</view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getProfile, getBoundVehicle } from '../../api/rescuer'
import { getUserState, logout } from '../../stores/user'
import { startLocationReporter } from '../../utils/locationReporter'

const profile = reactive({
  username: '',
  realName: '',
  phone: '',
  email: ''
})
const vehicle = ref(null)

onShow(() => {
  if (!getUserState().token) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  startLocationReporter()
  load()
})

async function load() {
  try {
    const [p, v] = await Promise.all([getProfile(), getBoundVehicle()])
    Object.assign(profile, p.data || {})
    vehicle.value = v.data || null
  } catch (_) {}
}

function goEdit() {
  uni.navigateTo({ url: '/pages/mine/edit' })
}

function goBind() {
  uni.navigateTo({ url: '/pages/mine/bind' })
}

function onLogout() {
  uni.showModal({
    title: '确认退出？',
    success: (r) => {
      if (r.confirm) logout()
    }
  })
}
</script>

<style scoped>
.profile .name {
  font-size: 36rpx;
  font-weight: 700;
}
.line {
  margin-top: 12rpx;
}
.section-title {
  font-weight: 600;
  margin-bottom: 12rpx;
}
</style>
