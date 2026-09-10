<template>
  <view>
    <view class="header">
      <view class="btn-ghost" @click="goScan">扫码</view>
      <view v-if="canAdd" class="btn-primary" @click="goCheckin">入库</view>
    </view>

    <view class="card filters">
      <view class="field">
        <view class="field-label">车牌</view>
        <input class="field-input" v-model="plateNo" placeholder="车牌号" confirm-type="search" @confirm="onQuery" />
      </view>
      <view class="field">
        <view class="field-label">扣押编号</view>
        <input class="field-input" v-model="detainNo" placeholder="DV…" confirm-type="search" @confirm="onQuery" />
      </view>
      <view class="btn-ghost" @click="onQuery">查询</view>
    </view>

    <view v-if="loading" class="muted center">加载中…</view>
    <view v-else-if="!list.length" class="muted center">暂无数据</view>
    <view v-else>
      <view class="card item" v-for="item in list" :key="item.id" @click="goDetail(item.id)">
        <view class="row-between">
          <text class="detain-no">{{ item.detainNo || '-' }}</text>
          <text class="plate">{{ item.plateNo || '-' }}</text>
        </view>
        <view class="line">停车场：{{ item.parkingLotName || '-' }}</view>
        <view class="muted">入库时间：{{ formatTime(item.inTime) }}</view>
      </view>
    </view>

    <view v-if="!loading" class="pager">
      <view class="sizes">
        <text
          v-for="s in pageSizes"
          :key="s"
          class="size"
          :class="{ active: size === s }"
          @click="changeSize(s)"
        >{{ s }}</text>
      </view>
      <view class="row-between">
        <text class="muted">共 {{ total }} 条</text>
        <view class="pager-btns">
          <text class="link" :class="{ disabled: page <= 1 }" @click="prevPage">上一页</text>
          <text class="muted page-num">{{ page }}</text>
          <text class="link" :class="{ disabled: page >= totalPages }" @click="nextPage">下一页</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { listDetains } from '../api/detain'
import { getUserState } from '../stores/user'

const pageSizes = [10, 20, 50, 100]

const plateNo = ref('')
const detainNo = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const list = ref([])
const loading = ref(false)
const canAdd = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil((total.value || 0) / size.value)))

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function refreshPerms() {
  const perms = getUserState().permissions || []
  canAdd.value = perms.includes('detain:add')
}

refreshPerms()

async function load() {
  refreshPerms()
  loading.value = true
  try {
    const params = { page: page.value, size: size.value, status: 'IN_YARD' }
    const plate = String(plateNo.value || '').trim()
    const no = String(detainNo.value || '').trim()
    if (plate) params.plateNo = plate
    if (no) params.detainNo = no
    const res = await listDetains(params)
    list.value = res.data?.list || []
    total.value = res.data?.total ?? 0
    if (res.data?.page) page.value = res.data.page
    if (res.data?.size) size.value = res.data.size
  } catch (_) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onQuery() {
  page.value = 1
  load()
}

function changeSize(s) {
  if (size.value === s) return
  size.value = s
  page.value = 1
  load()
}

function prevPage() {
  if (page.value <= 1) return
  page.value -= 1
  load()
}

function nextPage() {
  if (page.value >= totalPages.value) return
  page.value += 1
  load()
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

defineExpose({ reload: load })
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
.pager {
  margin-top: 8rpx;
  padding: 16rpx 8rpx 32rpx;
}
.sizes {
  display: flex;
  gap: 16rpx;
  margin-bottom: 16rpx;
}
.size {
  flex: 1;
  text-align: center;
  padding: 12rpx 0;
  border-radius: 12rpx;
  background: #fff;
  color: #666;
  font-size: 24rpx;
}
.size.active {
  color: #2979ff;
  font-weight: 600;
  background: #eef4ff;
}
.pager-btns {
  display: flex;
  align-items: center;
  gap: 20rpx;
}
.link {
  color: #2979ff;
  font-size: 26rpx;
}
.link.disabled {
  color: #ccc;
}
.page-num {
  min-width: 40rpx;
  text-align: center;
}
</style>
