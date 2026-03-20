<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
const title = route.meta?.title || '页面'
</script>

<template>
  <div class="uc-sub">
    <h2 class="uc-page-title">{{ title }}</h2>
    <p class="uc-hint">功能完善中。</p>
  </div>
</template>

<style scoped>
.uc-sub { color: #fff; }
.uc-page-title { font-size: 18px; margin: 0 0 20px; }
.uc-hint { color: #828ea1; font-size: 14px; }
</style>
