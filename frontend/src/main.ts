import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './App.vue'
import router from './router'
import { registerDirectives } from './directives'
import { registerIcons } from './utils/icon-config'
import './style.css'

const app = createApp(App)

// 注册 Pinia
const pinia = createPinia()
app.use(pinia)

// 注册 Element Plus
app.use(ElementPlus, {
  locale: zhCn,
})

// 注册路由
app.use(router)

// 注册自定义指令
registerDirectives(app)

// 注册自定义图标（使用按需注册，避免重复注册警告）
registerIcons(app)

// 挂载应用
app.mount('#app')
