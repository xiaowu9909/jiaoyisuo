import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import './style.css'
import './mobile-common.css'
import App from './App.vue'
import router from './router'
import { setRequestLoadingStore } from './api'
import { useAppStore } from './stores/app'
import en from './locales/en'
import es from './locales/es'
import fr from './locales/fr'
import de from './locales/de'
import it from './locales/it'

const savedLang = localStorage.getItem('LANG') || 'en'

const i18n = createI18n({
  legacy: false,
  locale: savedLang,
  fallbackLocale: 'en',
  messages: { en, es, fr, de, it },
})

const app = createApp(App)
app.use(createPinia())
setRequestLoadingStore(useAppStore())
app.use(i18n)
app.use(router)
app.mount('#app')
