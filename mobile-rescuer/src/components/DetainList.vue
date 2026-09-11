<template>
  <view>
    <view class="header">
      <view class="btn-ghost" @click="goScan">扫码</view>
      <view v-if="canAdd" class="btn-primary" @click="goCheckin">入库</view>
    </view>

    <view class="card filters">
      <view class="field">
        <view class="field-label">车牌</view>
        <input class="field-input" v-model="plateNo" placeholder="车牌号" confirm-type="search" @confirm="onConfirmPlate" />
      </view>
      <view class="field">
        <view class="field-label">扣押编号</view>
        <input class="field-input" v-model="detainNo" placeholder="请输入扣押编号" confirm-type="search" @confirm="onConfirmDetain" />
      </view>
      <view class="btn-ghost" @click="onQuery">查询</view>
    </view>

    <view v-if="loading && !list.length" class="muted center">加载中…</view>
    <view v-else-if="loadFailed && !list.length" class="muted center">加载失败</view>
    <view v-else-if="!list.length" class="muted center">暂无在库车辆，可点右上角入库</view>
    <view v-else>
      <view class="card item" v-for="item in list" :key="item.id" @click="goDetail(item.id)">
        <view class="row-between">
          <text class="detain-no">{{ item.detainNo || '-' }}</text>
          <text class="plate">{{ item.plateNo || '-' }}</text>
        </view>
        <view class="line">停车场：{{ item.parkingLotName || '-' }}</view>
        <view class="muted">入库时间：{{ formatTime(item.inTime) }}</view>
      </view>
      <view class="muted load-tip">
        {{ loading ? '加载中…' : (list.length >= total ? '没有更多了' : '上滑加载更多') }}
      </view>
    </view>
  </view>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { listDetains } from '../api/detain'
import { getUserState } from '../stores/user'
import { confirmInputValue, normalizeHangtagText } from '../utils/workspace.js'

const PAGE_SIZE = 10

const plateNo = ref('')
const detainNo = ref('')
const page = ref(1)
const total = ref(0)
const list = ref([])
const loading = ref(false)
const loadFailed = ref(false)
const canAdd = ref(false)

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function refreshPerms() {
  const perms = getUserState().permissions || []
  canAdd.value = perms.includes('detain:add')
}

refreshPerms()

async function load(append = false) {
  refreshPerms()
  loading.value = true
  try {
    const params = { page: page.value, size: PAGE_SIZE, status: 'IN_YARD' }
    const plate = String(plateNo.value || '').trim()
    const no = normalizeHangtagText(detainNo.value)
    if (plate) params.plateNo = plate
    if (no) params.detainNo = no
    const res = await listDetains(params)
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

function onConfirmPlate(e) {
  plateNo.value = confirmInputValue(e, plateNo.value)
  onQuery()
}

function onConfirmDetain(e) {
  detainNo.value = confirmInputValue(e, detainNo.value)
  onQuery()
}

function onQuery() {
  reload()
}

function goScan() {
  uni.navigateTo({ url: '/pages/detain/scan' })
}

function goCheckin() {
  uni.navigateTo({ url: '/pages/detain/checkin' })
}

function goDetail(id) {
  uni.navigateTo({ url: `/pages/detain/detail?id=${id}` })
}

defineExpose({ reload, loadMore })

function onWindowScroll() {
  if (typeof document === 'undefined') return
  const doc = document.documentElement
  const remain = doc.scrollHeight - window.innerHeight - window.scrollY
  if (remain < 80) loadMore()
}

onMounted(() => {
  if (typeof window === 'undefined') return
  window.addEventListener('scroll', onWindowScroll, { passive: true })
})

onUnmounted(() => {
  if (typeof window === 'undefined') return
  window.removeEventListener('scroll', onWindowScroll)
})
</script>

<style scoped>
.header {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;
}
.header .btn-ghost,
.header .btn-primary {
  flex: 1;
}
.filters .field {
  margin-bottom: 16rpx;
}
.item .detain-no {
  font-weight: 600;
  font-size: 30rpx;
}
.plate {
  color: #2979ff;
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
.load-tip {
  text-align: center;
  padding: 24rpx 0 80rpx;
}
</style>
