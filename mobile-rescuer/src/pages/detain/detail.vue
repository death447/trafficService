<template>
  <view class="page" v-if="record">
    <view class="card">
      <view class="line">扣押编号：{{ record.detainNo || '-' }}</view>
      <view class="line">车牌：{{ record.plateNo || '-' }}</view>
      <view class="line">车型：{{ record.vehicleType || '-' }}</view>
      <view class="line">停车场：{{ record.parkingLotName || '-' }}</view>
      <view class="line">扣留部门：{{ record.detainDept || '-' }}</view>
      <view class="line">关联工单号：{{ record.orderNo || '-' }}</view>
      <view class="line">入库时间：{{ formatTime(record.inTime) }}</view>
      <view class="line">备注：{{ record.remark || '-' }}</view>
      <view class="line">状态：{{ statusText(record.status) }}</view>
    </view>
    <view v-if="canOut" class="btn-danger" :class="{ 'btn-disabled': submitting }" @click="onOut">
      {{ submitting ? '出库中…' : '出库' }}
    </view>
  </view>
  <view v-else class="page muted center">加载中…</view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { checkOutDetain, getDetain } from '../../api/detain'
import { getUserState } from '../../stores/user'
import { requirePageAccess } from '../../utils/guard.js'

const id = ref(null)
const record = ref(null)
const submitting = ref(false)

const canOut = computed(() => {
  const perms = getUserState().permissions || []
  return record.value?.status === 'IN_YARD' && perms.includes('detain:out')
})

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function statusText(status) {
  const map = {
    IN_YARD: '在库',
    OUT: '已出库',
    CLEARED: '已清理'
  }
  return map[status] || status || '-'
}

onLoad((q) => {
  id.value = q.id
})

onShow(() => {
  if (!requirePageAccess('parking')) return
  if (id.value) load()
})

async function load() {
  try {
    const res = await getDetain(id.value)
    record.value = res.data || null
  } catch (_) {
    record.value = null
  }
}

function onOut() {
  if (!canOut.value || submitting.value) return
  uni.showModal({
    title: '确认出库？',
    content: record.value?.plateNo ? `将「${record.value.plateNo}」出库` : '',
    success: async (r) => {
      if (!r.confirm) return
      submitting.value = true
      try {
        await checkOutDetain(id.value)
        uni.navigateBack()
      } catch (_) {
      } finally {
        submitting.value = false
      }
    }
  })
}
</script>

<style scoped>
.line {
  margin-top: 12rpx;
  font-size: 28rpx;
  line-height: 1.5;
}
.center {
  text-align: center;
  padding: 80rpx 0;
}
</style>
