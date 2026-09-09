<script>
import { startLocationReporter, stopLocationReporter } from './utils/locationReporter'

export default {
  onLaunch() {
    const token = uni.getStorageSync('token')
    const pages = getCurrentPages()
    const route = pages.length ? pages[pages.length - 1].route : ''
    if (!token && route !== 'pages/login/index') {
      uni.reLaunch({ url: '/pages/login/index' })
    }
  },
  onShow() {
    startLocationReporter()
  },
  onHide() {
    // H5（有 window）: 切标签也会触发 onHide，停表会导致几乎不上报；仅原生 App 在后台停表。
    const isH5 = typeof window !== 'undefined' && typeof document !== 'undefined'
    if (!isH5) {
      stopLocationReporter()
    }
  }
}
</script>

<style>
page {
  background-color: #f5f6f8;
  color: #222;
  font-size: 28rpx;
  box-sizing: border-box;
}

.page {
  min-height: 100vh;
  padding: 24rpx;
  box-sizing: border-box;
}

.card {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
}

.btn-primary {
  background: #2979ff;
  color: #fff;
  border-radius: 12rpx;
  text-align: center;
  padding: 22rpx;
  font-size: 30rpx;
}

.btn-ghost {
  background: #fff;
  color: #2979ff;
  border: 1px solid #2979ff;
  border-radius: 12rpx;
  text-align: center;
  padding: 20rpx;
  font-size: 28rpx;
}

.btn-danger {
  background: #fff;
  color: #e54d42;
  border: 1px solid #e54d42;
  border-radius: 12rpx;
  text-align: center;
  padding: 20rpx;
  font-size: 28rpx;
}

.btn-disabled {
  opacity: 0.45;
}

.field {
  margin-bottom: 24rpx;
}

.field-label {
  color: #666;
  margin-bottom: 12rpx;
  font-size: 26rpx;
}

/* Override uni-input 1.4em height + border-box padding (crushes tap area) and force readable text color. */
.field-input {
  display: block;
  width: 100%;
  box-sizing: border-box;
  background: #f5f6f8;
  border-radius: 12rpx;
  padding: 22rpx 24rpx;
  height: auto;
  min-height: 88rpx;
  line-height: 1.4;
  font-size: 16px;
  color: #222;
  -webkit-text-fill-color: #222;
  color-scheme: light;
  -webkit-user-select: text;
  user-select: text;
  overflow: visible;
}

uni-textarea.field-input,
.field-input.area {
  min-height: 140rpx;
  height: auto;
  width: 100%;
}

/* H5 内层原生控件：继承外层可读色与可选中 */
.field-input .uni-input-wrapper,
.field-input .uni-textarea-wrapper {
  min-height: inherit;
  height: 100%;
}

.field-input .uni-input-input,
.field-input .uni-textarea-textarea {
  color: #222 !important;
  -webkit-text-fill-color: #222 !important;
  -webkit-user-select: text;
  user-select: text;
  caret-color: #222;
}

.field-input .uni-input-placeholder,
.field-input .uni-textarea-placeholder {
  color: #999;
  -webkit-text-fill-color: #999;
}

.muted {
  color: #999;
  font-size: 24rpx;
}

.row-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
