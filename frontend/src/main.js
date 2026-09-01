import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import authDirective from './directives/auth'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.directive('auth', authDirective)

app.mount('#app')