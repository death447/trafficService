<template>
  <view class="page" v-if="order">
    <view class="card">
      <view class="row-between">
        <text class="title">{{ order.orderNo || ('#' + order.id) }}</text>
        <text class="status">{{ statusText(order.status) }}</text>
      </view>
      <view class="line">事故地址：{{ order.accidentAddress || '-' }}</view>
      <view class="line">救援事由：{{ order.rescueReason || '-' }}</view>
      <view class="line muted" v-if="order.checkedInAt">
        已签到（{{ order.checkinMode }}）{{ order.checkedInAt }}
      </view>
      <view class="line muted" v-if="order.rejectReason">退单原因：{{ order.rejectReason }}</view>
    </view>

    <view class="card" v-if="fieldRecord">
      <view class="section-title">现场摘要</view>
      <view class="line">车牌：{{ fieldRecord.plateNo || '-' }}</view>
      <view class="line">车型：{{ fieldRecord.vehicleType || '-' }}</view>
      <view class="line">受损：{{ fieldRecord.damageDesc || '-' }}</view>
      <view class="line">停放：{{ fieldRecord.parkAddress || '-' }}</view>
    </view>

    <view class="actions" v-if="order.status === 'DISPATCHED'">
      <view class="btn-primary" @click="onAccept">接单</view>
      <view class="btn-danger" @click="promptReject">退单</view>
    </view>

    <view class="actions" v-if="order.status === 'ACCEPTED'">
      <view v-if="!order.checkedInAt" class="btn-primary" @click="onCheckin">签到</view>
      <view class="btn-ghost" @click="goScene">现场采集</view>
      <view class="btn-ghost" @click="goPark">入库登记</view>
      <view
        class="btn-primary"
        :class="{ 'btn-disabled': !order.checkedInAt }"
        @click="onComplete"
      >
        完成工单
      </view>
    </view>

    <view class="mask" v-if="reasonPanel" @click.self="reasonPanel = null">
      <view class="card panel">
        <view class="section-title">{{ reasonPanel === 'reject' ? '退单原因' : '手动签到原因' }}</view>
        <textarea class="field-input area" v-model="reasonText" placeholder="请填写原因（必填）" />
        <view class="btn-primary" @click="submitReason">确认</view>
        <view class="btn-ghost" style="margin-top: 16rpx" @click="reasonPanel = null">取消</view>
      </view>
    </view>
  </view>
  <view v-else class="page muted center">加载中…</view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import {
  getTask,
  acceptTask,
  rejectTask,
  checkinTask,
  completeTask
} from '../../api/rescuer'

const id = ref(null)
const order = ref(null)
const fieldRecord = ref(null)
const reasonPanel = ref(null)
const reasonText = ref('')

onLoad((q) => {
  id.value = q.id
})

onShow(() => {
  if (id.value) load()
})

function statusText(s) {
  const map = {
    PENDING: '待派单',
    DISPATCHED: '已派单',
    ACCEPTED: '已接单',
    COMPLETED: '已完成',
    ABORTED: '已中止'
  }
  return map[s] || s || '-'
}

async function load() {
  try {
    const res = await getTask(id.value)
    order.value = res.data?.order || null
    fieldRecord.value = res.data?.fieldRecord || null
  } catch (_) {
    order.value = null
  }
}

async function onAccept() {
  try {
    await acceptTask(id.value)
    uni.showToast({ title: '已接单', icon: 'success' })
    load()
  } catch (_) {}
}

function promptReject() {
  reasonText.value = ''
  reasonPanel.value = 'reject'
}

function promptManual() {
  reasonText.value = ''
  reasonPanel.value = 'manual'
}

async function submitReason() {
  const text = (reasonText.value || '').trim()
  if (!text) {
    uni.showToast({ title: '请填写原因', icon: 'none' })
    return
  }
  const mode = reasonPanel.value
  reasonPanel.value = null
  if (mode === 'reject') {
    try {
      await rejectTask(id.value, text)
      uni.showToast({ title: '已退单', icon: 'success' })
      setTimeout(() => uni.navigateBack(), 400)
    } catch (_) {}
    return
  }
  if (mode === 'manual') {
    try {
      await checkinTask(id.value, { mode: 'MANUAL', remark: text })
      uni.showToast({ title: '手动签到成功', icon: 'success' })
      load()
    } catch (_) {}
  }
}

function onCheckin() {
  uni.getLocation({
    type: 'gcj02',
    success: async (loc) => {
      try {
        await checkinTask(id.value, {
          lng: loc.longitude,
          lat: loc.latitude,
          mode: 'AUTO'
        })
        uni.showToast({ title: '签到成功', icon: 'success' })
        load()
      } catch (_) {
        promptManual()
      }
    },
    fail: () => promptManual()
  })
}

function goScene() {
  uni.navigateTo({ url: `/pages/task/scene?id=${id.value}` })
}

function goPark() {
  uni.navigateTo({ url: `/pages/task/park?id=${id.value}` })
}

async function onComplete() {
  if (!order.value?.checkedInAt) {
    uni.showToast({ title: '请先签到', icon: 'none' })
    return
  }
  try {
    await completeTask(id.value)
    uni.showToast({ title: '已完成', icon: 'success' })
    load()
  } catch (_) {}
}
</script>

<style scoped>
.title {
  font-size: 32rpx;
  font-weight: 700;
}
.status {
  color: #2979ff;
}
.line {
  margin-top: 16rpx;
  line-height: 1.5;
}
.section-title {
  font-weight: 600;
  margin-bottom: 8rpx;
}
.actions {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}
.center {
  text-align: center;
  padding-top: 80rpx;
}
.mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx;
  z-index: 100;
}
.panel {
  width: 100%;
  margin: 0;
}
.area {
  min-height: 140rpx;
  margin: 20rpx 0 28rpx;
}
</style>
