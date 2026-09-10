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
  <view v-else-if="loading" class="page muted center">加载中…</view>
  <view v-else class="page muted center">
    <view>{{ notFound ? '未找到扣留记录' : (loadError || '加载失败') }}</view>
    <view class="btn-ghost retry" @click="load">重试</view>
  </view>
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
const loading = ref(true)
const loadError = ref('')
const notFound = ref(false)

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
  load()
})

async function load() {
  loading.value = true
  loadError.value = ''
  notFound.value = false
  record.value = null
  if (!id.value) {
    notFound.value = true
    loading.value = false
    return
  }
  try {
    const res = await getDetain(id.value)
    record.value = res.data || null
    if (!record.value) notFound.value = true
  } catch (e) {
    record.value = null
    const msg = (e && e.message) || ''
    if (/404|不存在|未找到/.test(msg)) {
      notFound.value = true
    } else {
      loadError.value = msg || '加载失败'
    }
  } finally {
    loading.value = false
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
        await load()
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
.retry {
  margin-top: 24rpx;
}
</style>
