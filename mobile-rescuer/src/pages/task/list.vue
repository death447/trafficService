<template>
  <view></view>
</template>

<script setup>
import { onShow } from '@dcloudio/uni-app'

onShow(() => {
  uni.switchTab({ url: '/pages/workbench/index' })
})
</script>
