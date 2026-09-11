<template>
  <view class="page">
    <view class="card">
      <view class="field">
        <view class="field-label">扣押编号</view>
        <input
          class="field-input"
          v-model="payload"
          placeholder="请输入或扫描扣押编号"
          confirm-type="search"
          @confirm="onFind"
        />
      </view>
      <view class="btn-primary" @click="onFind">查找</view>
      <view class="btn-ghost scan-btn" :class="{ 'btn-disabled': scanning }" @click="onScan">
        {{ scanning ? '扫码中…' : '扫码' }}
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listDetains } from '../../api/detain'
import { requirePageAccess } from '../../utils/guard.js'
import { confirmInputValue, matchHangtag, normalizeHangtagText } from '../../utils/workspace.js'
import { scanQrCode } from '../../utils/scanCode'

const payload = ref('')
const scanning = ref(false)

onShow(() => {
  if (!requirePageAccess('parking')) return
})

async function lookup(raw) {
  const text = normalizeHangtagText(raw)
  const pre = matchHangtag([], text)
  if (pre.kind === 'empty') {
    uni.showToast({ title: '请输入或扫描扣押编号', icon: 'none' })
    return
  }
  if (pre.kind === 'bind-qr') {
    uni.showToast({ title: '请扫描吊牌二维码', icon: 'none' })
    return
  }
  try {
    const res = await listDetains({ detainNo: text, page: 1, size: 10 })
    const list = res.data?.list || []
    const hit = matchHangtag(list, text)
    if (hit.kind === 'in-yard') {
      uni.navigateTo({ url: `/pages/detain/detail?id=${hit.record.id}` })
      return
    }
    if (hit.kind === 'not-in-yard') {
      uni.showToast({ title: '该车辆已出库或已清理', icon: 'none' })
      return
    }
    uni.showToast({ title: '未找到扣留记录', icon: 'none' })
  } catch (_) {}
}

function onFind(e) {
  const text = confirmInputValue(e, payload.value)
  payload.value = text
  lookup(text)
}

async function onScan() {
  if (scanning.value) return
  scanning.value = true
  try {
    const text = normalizeHangtagText(await scanQrCode())
    payload.value = text
    await lookup(text)
  } catch (e) {
    const msg = (e && e.message) || ''
    if (msg !== 'cancel') {
      uni.showToast({ title: msg === 'empty' ? '未识别到二维码' : '扫码失败', icon: 'none' })
    }
  } finally {
    scanning.value = false
  }
}
</script>

<style scoped>
.scan-btn {
  margin-top: 20rpx;
}
</style>
