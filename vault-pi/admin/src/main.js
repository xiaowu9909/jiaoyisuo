import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import SvgIcon from './components/SvgIcon.vue'
import UiPage from './ui/UiPage.vue'
import UiCard from './ui/UiCard.vue'
import './index.css'

// 初始化主题（默认 dark，可从 localStorage 恢复）
const root = document.documentElement
const savedTheme = localStorage.getItem('vaultpi-admin-theme')
const initialTheme = savedTheme === 'light' ? 'light' : 'dark'
root.setAttribute('data-theme', initialTheme)

const app = createApp(App)
app.component('SvgIcon', SvgIcon)
app.component('UiPage', UiPage)
app.component('UiCard', UiCard)
app.use(router).mount('#app')
