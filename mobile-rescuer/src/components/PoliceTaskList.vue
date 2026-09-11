<template>
  <view>
    <view v-if="loading && !list.length" class="muted center">加载中…</view>
    <view v-else-if="loadFailed && !list.length" class="muted center">加载失败</view>
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
        <view v-if="canRatePoliceTask(item.status, item.rated)" class="btn-ghost rate-btn" @click.stop="goRate(item.id)">评价</view>
        <view v-else-if="item.status === 'COMPLETED' && item.rated" class="muted">已评价</view>
      </view>
      <view class="muted load-tip">
        {{ loading ? '加载中…' : (list.length >= total ? '没有更多了' : '上滑加载更多') }}
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { listPoliceTasks } from '../api/police'
import { canRatePoliceTask } from '../utils/workspace.js'

const PAGE_SIZE = 10
const page = ref(1)
const total = ref(0)
const list = ref([])
const loading = ref(false)
const loadFailed = ref(false)

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

async function load(append = false) {
  loading.value = true
  try {
    const res = await listPoliceTasks({ page: page.value, size: PAGE_SIZE })
    const rows = res.data?.list || []
    total.value = res.data?.total ?? 0
    list.value = append ? list.value.concat(rows) : rows
    loadFailed.value = false
  } catch (_) {
    if (append) {
      page.value = Math.max(1, page.value - 1)
      uni.showToast({ title: '加载失败', icon: 'none' })
    } else {
      list.value = []
      total.value = 0
      loadFailed.value = true
    }
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  return load(false)
}

async function loadMore() {
  if (loading.value || list.value.length >= total.value) return
  page.value += 1
  await load(true)
}

function goDetail(id) {
  uni.navigateTo({ url: `/pages/police/detail?id=${id}` })
}

function goRate(id) {
  uni.navigateTo({ url: `/pages/police/rate?id=${id}` })
}

defineExpose({ reload, loadMore })
</script>

<style scoped>
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
  font-size: 26rpx;
  color: #444;
  margin-top: 8rpx;
}
.rate-btn {
  margin-top: 16rpx;
}
.load-tip {
  text-align: center;
  padding: 16rpx 0 8rpx;
}
</style>
