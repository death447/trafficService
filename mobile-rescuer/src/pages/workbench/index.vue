<template>
  <view class="page">
    <TaskList v-if="workspace === 'rescuer'" ref="taskRef" />
    <DetainList v-if="workspace === 'parking'" ref="detainRef" />
  </view>
</template>

<script setup>
import { nextTick, ref } from 'vue'
import { onReachBottom, onShow } from '@dcloudio/uni-app'
import { getUserState } from '../../stores/user'
import { applyWorkbenchTabText, requirePageAccess } from '../../utils/guard.js'
import { startLocationReporter, stopLocationReporter } from '../../utils/locationReporter'
import TaskList from '../../components/TaskList.vue'
import DetainList from '../../components/DetainList.vue'

const workspace = ref('')
const taskRef = ref(null)
const detainRef = ref(null)

onShow(() => {
  if (!requirePageAccess('shared')) return
  const state = getUserState()
  workspace.value = state.workspace
  applyWorkbenchTabText(state.workspace)
  if (state.workspace === 'rescuer') startLocationReporter()
  if (state.workspace === 'parking') stopLocationReporter()
  nextTick(() => {
    taskRef.value?.reload()
    detainRef.value?.reload()
  })
})

onReachBottom(() => {
  if (workspace.value !== 'parking') return
  detainRef.value?.loadMore?.()
})
</script>
