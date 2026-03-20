<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
const title = route.meta?.title || '页面'
</script>

<template>
  <div class="placeholder-page">
    <h1>{{ title }}</h1>
    <p>功能完善中。</p>
  </div>
</template>

<style scoped>
.placeholder-page {
  padding: 70px 8% 40px;
  min-height: 60vh;
  background: #0b1520;
  color: #fff;
}
.placeholder-page h1 {
  font-size: 20px;
  margin-bottom: 12px;
}
.placeholder-page p {
  color: #828ea1;
  font-size: 14px;
}
</style>
