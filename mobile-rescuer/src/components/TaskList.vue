<template>
  <view>
    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.value"
        class="tab"
        :class="{ active: tab === t.value }"
        @click="switchTab(t.value)"
      >
        {{ t.label }}
      </view>
    </view>

    <view v-if="loading" class="muted center">加载中…</view>
    <view v-else-if="!list.length" class="muted center">暂无任务</view>
    <view v-else>
      <view class="card item" v-for="item in list" :key="item.id" @click="goDetail(item.id)">
        <view class="row-between">
          <text class="order-no">{{ item.orderNo || ('#' + item.id) }}</text>
          <text class="status">{{ statusText(item.status) }}</text>
        </view>
        <view class="addr">{{ item.accidentAddress || '未填写地址' }}</view>
        <view class="line">事故车牌：{{ item.plateNo || '-' }}</view>
        <view class="line">事故联系人：{{ item.partyName || '-' }}</view>
        <view class="line">联系方式：{{ item.partyPhone || '-' }}</view>
        <view class="muted">{{ item.rescueReason || '' }}</view>
        <view class="line">派单时间：{{ formatTime(item.dispatchedAt) }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { listTasks } from '../api/rescuer'

const tabs = [
  { label: '待办', value: 'todo' },
  { label: '已办', value: 'done' },
  { label: '中止', value: 'aborted' }
]

const tab = ref('todo')
const list = ref([])
const loading = ref(false)

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

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

async function load() {
  loading.value = true
  try {
    const res = await listTasks(tab.value)
    list.value = res.data || []
  } catch (_) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function switchTab(v) {
  if (tab.value === v) return
  tab.value = v
  load()
}

function goDetail(id) {
  uni.navigateTo({ url: `/pages/task/detail?id=${id}` })
}

defineExpose({ reload: load })
</script>

<style scoped>
.tabs {
  display: flex;
  background: #fff;
  border-radius: 16rpx;
  margin-bottom: 24rpx;
  overflow: hidden;
}
.tab {
  flex: 1;
  text-align: center;
  padding: 24rpx 0;
  color: #666;
}
.tab.active {
  color: #2979ff;
  font-weight: 600;
  background: #eef4ff;
}
.item .order-no {
  font-weight: 600;
  font-size: 30rpx;
}
.status {
  color: #2979ff;
  font-size: 24rpx;
}
.addr {
  margin: 16rpx 0 8rpx;
  font-size: 28rpx;
}
.line {
  margin-top: 8rpx;
  font-size: 26rpx;
  color: #333;
  line-height: 1.4;
}
.center {
  text-align: center;
  padding: 80rpx 0;
}
</style>
