/**
 * Cross-platform QR scan. H5: camera page (html5-qrcode). Native: uni.scanCode.
 * @returns {Promise<string>} decoded text
 */
export function scanQrCode() {
  return new Promise((resolve, reject) => {
    // #ifdef H5
    uni.navigateTo({
      url: '/pages/mine/scan',
      events: {
        success: (data) => {
          const text = (data && data.result ? data.result : '').trim()
          if (text) resolve(text)
          else reject(new Error('empty'))
        },
        cancel: () => reject(new Error('cancel'))
      },
      fail: (err) => reject(err || new Error('navigate fail'))
    })
    // #endif

    // #ifndef H5
    uni.scanCode({
      onlyFromCamera: false,
      scanType: ['qrCode'],
      success: (res) => {
        const text = (res.result || '').trim()
        if (text) resolve(text)
        else reject(new Error('empty'))
      },
      fail: (err) => reject(err || new Error('scan fail'))
    })
    // #endif
  })
}
