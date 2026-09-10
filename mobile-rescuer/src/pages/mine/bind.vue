<template>
  <view class="page">
    <view class="card">
      <view class="muted tip">扫码内容格式：RV:{车辆ID}。可用摄像头/相册扫码，也可手动输入。</view>
      <view class="btn-primary" :class="{ 'btn-disabled': scanning }" @click="onScan">
        {{ scanning ? '扫码中…' : '扫码绑定' }}
      </view>
      <view class="field" style="margin-top: 32rpx">
        <view class="field-label">手动输入载荷</view>
        <input class="field-input" v-model="payload" placeholder="例如 RV:1" />
      </view>
      <view class="btn-ghost" @click="onBind">确认绑定</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { bindVehicle } from '../../api/rescuer'
import { requirePageAccess } from '../../utils/guard.js'
import { startLocationReporter } from '../../utils/locationReporter'
import { scanQrCode } from '../../utils/scanCode'

const payload = ref('')
const scanning = ref(false)

onShow(() => {
  if (!requirePageAccess('rescuer')) return
})

async function onScan() {
  if (scanning.value) return
  scanning.value = true
  try {
    const text = await scanQrCode()
    payload.value = text
    if (payload.value) await onBind()
  } catch (e) {
    const msg = (e && e.message) || ''
    if (msg !== 'cancel') {
      uni.showToast({ title: '扫码失败，请手输 RV:id', icon: 'none' })
    }
  } finally {
    scanning.value = false
  }
}

async function onBind() {
  const qrPayload = (payload.value || '').trim()
  if (!qrPayload) {
    uni.showToast({ title: '请输入或扫码 RV:id', icon: 'none' })
    return
  }
  try {
    await bindVehicle(qrPayload)
    startLocationReporter()
    uni.showToast({ title: '绑定成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 400)
  } catch (_) {}
}
</script>

<style scoped>
.tip {
  margin-bottom: 28rpx;
  line-height: 1.5;
}
</style>
