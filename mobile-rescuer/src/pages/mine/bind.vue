<template>
  <view class="page">
    <view class="card">
      <view class="muted tip">扫码内容格式：RV:{车辆ID}。H5 扫码可能受限，可手动输入。</view>
      <view class="btn-primary" @click="onScan">扫码绑定</view>
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
import { bindVehicle } from '../../api/rescuer'

const payload = ref('')

function onScan() {
  uni.scanCode({
    onlyFromCamera: false,
    success: (res) => {
      payload.value = (res.result || '').trim()
      if (payload.value) onBind()
    },
    fail: () => {
      uni.showToast({ title: '扫码不可用，请手输 RV:id', icon: 'none' })
    }
  })
}

async function onBind() {
  const qrPayload = (payload.value || '').trim()
  if (!qrPayload) {
    uni.showToast({ title: '请输入或扫码 RV:id', icon: 'none' })
    return
  }
  try {
    await bindVehicle(qrPayload)
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
