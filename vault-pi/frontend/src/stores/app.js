import { defineStore } from 'pinia'
import { request, setCsrfToken } from '@/api'

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const MEMBER_KEY = 'MEMBER'

export const useAppStore = defineStore('app', {
  state: () => ({
    member: JSON.parse(localStorage.getItem(MEMBER_KEY) || 'null'),
    lang: localStorage.getItem('LANG') || 'en',
    loadingCount: 0,
  }),

  getters: {
    isLogin: (state) => !!state.member,
    isLoading: (state) => state.loadingCount > 0,
    languageValue: (state) => {
      const map = {
        en: 'English',
        es: 'Español',
        fr: 'Français',
        de: 'Deutsch',
        it: 'Italiano'
      };
      return map[state.lang] || 'English';
    },
  },

  actions: {
    setMember(data) {
      this.member = data
      localStorage.setItem(MEMBER_KEY, JSON.stringify(data || null))
    },

    setLang(locale) {
      this.lang = locale
      localStorage.setItem('LANG', locale)
    },

    addLoading() {
      this.loadingCount += 1
    },
    removeLoading() {
      if (this.loadingCount > 0) this.loadingCount -= 1
    },

    async checkLogin() {
      try {
        const json = await request(API_BASE + '/check/login', {
          method: 'POST',
          body: '{}',
        })
        if (json.code !== 0) return
        if (json.data === false || json.data == null) {
          this.setMember(null)
          setCsrfToken(null)
        } else if (typeof json.data === 'object' && json.data.username) {
          this.setMember(json.data)
        }
      } catch (_) { }
    },

    async logout() {
      try {
        await request(API_BASE + '/logout', { method: 'POST', body: '{}' })
      } catch (_) { }
      setCsrfToken(null)
      this.setMember(null)
    },
  },
})
