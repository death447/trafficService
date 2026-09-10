<template>
  <view class="page">
    <view class="muted center">扫码查找即将上线</view>
  </view>
</template>

<script setup>
import { onShow } from '@dcloudio/uni-app'
import { requirePageAccess } from '../../utils/guard.js'

onShow(() => {
  if (!requirePageAccess('parking')) return
})
</script>

<style scoped>
.center {
  text-align: center;
  padding: 80rpx 0;
}
</style>
