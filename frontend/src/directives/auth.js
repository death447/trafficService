import { useUserStore } from '../stores/user'

export default {
  mounted(el, binding) {
    const store = useUserStore()
    const code = binding.value
    if (!store.hasPermission(code)) {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}
