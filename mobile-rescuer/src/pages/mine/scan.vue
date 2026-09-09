<template>
  <view class="page scan-page">
    <view class="tip muted">将车辆二维码置于框内；需 HTTPS 或 localhost 才能调起摄像头。</view>
    <div id="qr-reader" class="reader"></div>
    <view v-if="status" class="status muted">{{ status }}</view>
    <view class="actions">
      <view class="btn-ghost" @click="onPickImage">从相册选择</view>
      <view class="btn-danger" style="margin-top: 20rpx" @click="onCancel">取消</view>
    </view>
  </view>
</template>

<script setup>
import { ref, getCurrentInstance } from 'vue'
import { onLoad, onReady, onUnload, onHide } from '@dcloudio/uni-app'
import { Html5Qrcode } from 'html5-qrcode'

const status = ref('正在打开摄像头…')
let scanner = null
let finished = false
let eventChannel = null

const instance = getCurrentInstance()

onLoad(() => {
  const proxy = instance?.proxy
  eventChannel = proxy && typeof proxy.getOpenerEventChannel === 'function'
    ? proxy.getOpenerEventChannel()
    : null
})

onReady(() => {
  startCamera()
})

onHide(() => {
  stopCamera()
})

onUnload(() => {
  stopCamera()
})

async function startCamera() {
  try {
    await stopCamera()
    scanner = new Html5Qrcode('qr-reader')
    await scanner.start(
      { facingMode: 'environment' },
      { fps: 8, qrbox: { width: 240, height: 240 } },
      (decodedText) => {
        emitSuccess(decodedText)
      },
      () => {}
    )
    status.value = '请对准二维码'
  } catch (e) {
    status.value = '摄像头不可用，请改用相册或返回手输'
    uni.showToast({ title: '摄像头打开失败', icon: 'none' })
  }
}

async function stopCamera() {
  if (!scanner) return
  try {
    if (scanner.isScanning) {
      await scanner.stop()
    }
    scanner.clear()
  } catch (_) {}
  scanner = null
}

async function emitSuccess(text) {
  if (finished) return
  const result = (text || '').trim()
  if (!result) return
  finished = true
  await stopCamera()
  if (eventChannel) {
    eventChannel.emit('success', { result })
  }
  uni.navigateBack()
}

function onCancel() {
  if (finished) return
  finished = true
  stopCamera().finally(() => {
    if (eventChannel) eventChannel.emit('cancel')
    uni.navigateBack()
  })
}

function onPickImage() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album'],
    success: async (res) => {
      const path = res.tempFilePaths?.[0]
      if (!path) return
      status.value = '正在识别…'
      try {
        await stopCamera()
        const file = await pathToFile(path)
        const reader = new Html5Qrcode('qr-reader')
        scanner = reader
        const decoded = await reader.scanFile(file, true)
        await emitSuccess(decoded)
      } catch (_) {
        status.value = '未识别到二维码，请重试或手输'
        uni.showToast({ title: '未识别到二维码', icon: 'none' })
        finished = false
        startCamera()
      }
    }
  })
}

async function pathToFile(path) {
  const response = await fetch(path)
  const blob = await response.blob()
  const name = 'qr-scan.jpg'
  if (typeof File !== 'undefined') {
    return new File([blob], name, { type: blob.type || 'image/jpeg' })
  }
  return blob
}
</script>

<style scoped>
.scan-page {
  padding-bottom: 40rpx;
}
.tip {
  margin-bottom: 20rpx;
  line-height: 1.5;
}
.reader {
  width: 100%;
  min-height: 420px;
  overflow: hidden;
  border-radius: 16rpx;
  background: #111;
}
.status {
  margin-top: 16rpx;
  text-align: center;
}
.actions {
  margin-top: 28rpx;
}
</style>
