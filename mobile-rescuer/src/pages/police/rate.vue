<template>
  <view class="page" v-if="ready">
    <view class="card">
      <view class="dim" v-for="d in dimensions" :key="d.key">
        <view class="label">{{ d.label }}</view>
        <view class="stars">
          <text
            v-for="n in 5"
            :key="n"
            class="star"
            :class="{ on: scores[d.key] >= n }"
            @click="scores[d.key] = n"
          >★</text>
        </view>
      </view>
      <view class="field">
        <view class="field-label">反馈意见</view>
        <textarea class="field-input area" v-model="comment" placeholder="选填" maxlength="500" />
      </view>
    </view>
    <view class="btn-primary" :class="{ 'btn-disabled': submitting }" @click="onSubmit">提交评价</view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getPoliceTask, ratePoliceTask } from '../../api/police'
import { requirePageAccess } from '../../utils/guard.js'
import { canRatePoliceTask } from '../../utils/workspace.js'

const dimensions = [
  { key: 'punctual', label: '到达现场是否及时' },
  { key: 'standard', label: '处置现场是否规范合理' },
  { key: 'safety', label: '操作过程是否确保安全' },
  { key: 'attitude', label: '服务态度是否热情耐心' }
]

const id = ref('')
const ready = ref(false)
const submitting = ref(false)
const comment = ref('')
const scores = reactive({
  punctual: 5,
  standard: 5,
  safety: 5,
  attitude: 5
})

function bounce(title) {
  uni.showToast({ title, icon: 'none' })
  setTimeout(() => uni.navigateBack(), 400)
}

async function load() {
  if (!requirePageAccess('police')) return
  if (!id.value) return
  try {
    const res = await getPoliceTask(id.value)
    const data = res.data || {}
    const order = data.order || {}
    if (!canRatePoliceTask(order.status, !!data.evaluation)) {
      bounce(data.evaluation ? '该工单已评价' : '仅已完成工单可评价')
      return
    }
    scores.punctual = 5
    scores.standard = 5
    scores.safety = 5
    scores.attitude = 5
    comment.value = ''
    ready.value = true
  } catch (e) {
    bounce(e.message || '加载失败')
  }
}

function onSubmit() {
  if (submitting.value) return
  uni.showModal({
    title: '提交评价？',
    success: async (r) => {
      if (!r.confirm) return
      submitting.value = true
      try {
        await ratePoliceTask(id.value, {
          scorePunctual: scores.punctual,
          scoreStandard: scores.standard,
          scoreSafety: scores.safety,
          scoreAttitude: scores.attitude,
          comment: comment.value
        })
        uni.showToast({ title: '评价成功', icon: 'none' })
        uni.redirectTo({ url: `/pages/police/detail?id=${id.value}` })
      } catch (e) {
        uni.showToast({ title: e.message || '提交失败', icon: 'none' })
      } finally {
        submitting.value = false
      }
    }
  })
}

onLoad((q) => {
  id.value = q.id
})

onShow(() => {
  load()
})
</script>

<style scoped>
.dim { margin-bottom: 28rpx; }
.label { font-size: 28rpx; margin-bottom: 8rpx; }
.stars { display: flex; gap: 16rpx; font-size: 44rpx; color: #ccc; }
.star.on { color: #f5a623; }
.area { min-height: 160rpx; }
.btn-primary { margin-top: 24rpx; }
</style>
