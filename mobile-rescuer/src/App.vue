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
    stopLocationReporter()
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

.field-input {
  background: #f5f6f8;
  border-radius: 12rpx;
  padding: 20rpx 24rpx;
  width: 100%;
  box-sizing: border-box;
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
