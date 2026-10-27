import { watchEffect } from 'vue'
import { useUserStore } from '../stores/user'

/**
 * v-auth="'perm:code'"
 * 无权限时隐藏元素。权限可能在应用启动时异步刷新，因此用 watchEffect 响应式控制，
 * 而不是在 mounted 时直接移除节点（否则刷新后新获得的权限菜单不会出现）。
 */
export default {
  mounted(el, binding) {
    const store = useUserStore()
    const code = binding.value
    el.__authStop = watchEffect(() => {
      el.style.display = store.hasPermission(code) ? '' : 'none'
    })
  },
  unmounted(el) {
    if (el.__authStop) {
      el.__authStop()
      el.__authStop = null
    }
  }
}
