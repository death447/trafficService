<template>
  <view class="page">
    <TaskList v-if="workspace === 'rescuer'" ref="taskRef" />
    <view v-if="workspace === 'parking'" class="muted center">扣车模块安装中</view>
  </view>
</template>

<script setup>
import { nextTick, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getUserState } from '../../stores/user'
import { applyWorkbenchTabText, requirePageAccess } from '../../utils/guard.js'
import { startLocationReporter, stopLocationReporter } from '../../utils/locationReporter'
import TaskList from '../../components/TaskList.vue'

const workspace = ref('')
const taskRef = ref(null)

onShow(() => {
  if (!requirePageAccess('shared')) return
  const state = getUserState()
  workspace.value = state.workspace
  applyWorkbenchTabText(state.workspace)
  if (state.workspace === 'rescuer') startLocationReporter()
  if (state.workspace === 'parking') stopLocationReporter()
  nextTick(() => {
    taskRef.value?.reload()
  })
})
</script>

<style scoped>
.center {
  text-align: center;
  padding: 80rpx 0;
}
</style>
