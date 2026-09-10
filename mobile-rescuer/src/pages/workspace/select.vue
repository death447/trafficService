<template>
  <view class="page">
    <view v-if="showRescuer" class="card choice" @click="choose('rescuer')">施救任务</view>
    <view v-if="showParking" class="card choice" @click="choose('parking')">停车场扣车</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { enterWorkspace, getUserState } from '../../stores/user'
import { requirePageAccess } from '../../utils/guard.js'
import { hasRescuerAccess, hasParkingAccess } from '../../utils/workspace.js'

const showRescuer = ref(false)
const showParking = ref(false)

onShow(() => {
  if (!requirePageAccess('select')) return
  const permissions = getUserState().permissions
  showRescuer.value = hasRescuerAccess(permissions)
  showParking.value = hasParkingAccess(permissions)
})

function choose(name) {
  enterWorkspace(name)
  uni.switchTab({ url: '/pages/workbench/index' })
}
</script>

<style scoped>
.choice {
  font-size: 32rpx;
  font-weight: 600;
  text-align: center;
  padding: 48rpx 28rpx;
}
</style>
