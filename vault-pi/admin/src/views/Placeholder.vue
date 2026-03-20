<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
const title = route.meta?.title || route.name || '页面'
</script>

<template>
  <div class="admin-page">
    <div class="admin-card">
      <div class="card-head">
        <span class="card-title">{{ title }}</span>
      </div>
      <div class="card-body">
        <p class="placeholder-hint">功能完善中。</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-card { border: 1px solid #e8eaec; border-radius: 4px; overflow: hidden; background: #fff; }
.card-head { padding: 12px 16px; background: #f8f8f9; border-bottom: 1px solid #e8eaec; }
.card-title { font-size: 14px; font-weight: 600; }
.card-body { padding: 24px; }
.placeholder-hint { color: #666; font-size: 14px; margin: 0; }
</style>
