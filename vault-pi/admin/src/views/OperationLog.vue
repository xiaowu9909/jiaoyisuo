<script setup>
import { ref, onMounted } from 'vue'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const pageNo = ref(1)
const pageSize = 20

async function load() {
  loading.value = true
  try {
    // 预留：接入 GET /api/admin/system/operation-log?pageNo=&pageSize= 等
    list.value = []
    total.value = 0
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function formatTime(str) {
  if (!str) return '—'
  try {
    return new Date(str).toLocaleString('zh-CN')
  } catch (_) {
    return str
  }
}

onMounted(load)
</script>

<template>
  <div class="log-page">
    <h1 class="page-title">操作日志</h1>
    <p class="page-desc">查看管理员在后台的操作记录。</p>

    <div class="toolbar">
      <button type="button" class="btn btn-primary" @click="load">刷新</button>
    </div>

    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>操作人</th>
            <th>模块</th>
            <th>操作</th>
            <th>详情</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="5" class="loading-cell">加载中…</td>
          </tr>
          <tr v-else-if="!list.length">
            <td colspan="5" class="no-data-cell">暂无数据。需后端提供操作日志接口后可展示。</td>
          </tr>
          <tr v-for="item in list" :key="item.id">
            <td>{{ formatTime(item.createTime) }}</td>
            <td>{{ item.operatorName ?? '—' }}</td>
            <td>{{ item.module ?? '—' }}</td>
            <td>{{ item.action ?? '—' }}</td>
            <td class="detail-cell">{{ item.detail ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.log-page { padding: 24px; }
.page-title { font-size: 20px; margin: 0 0 8px; font-weight: 700; color: #1f2937; }
.page-desc { color: #6b7280; font-size: 14px; margin: 0 0 20px; }
.toolbar { margin-bottom: 16px; }
.btn { padding: 8px 16px; border-radius: 8px; font-size: 13px; cursor: pointer; border: 1px solid transparent; }
.btn-primary { background: var(--primary-color, #6366f1); color: #fff; }
.table-wrap { overflow-x: auto; background: #fff; border-radius: 10px; box-shadow: 0 1px 3px rgba(0,0,0,.06); }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { padding: 12px 14px; text-align: left; border-bottom: 1px solid #f1f5f9; }
.data-table th { background: #f8fafc; font-size: 12px; font-weight: 600; color: #475569; }
.detail-cell { max-width: 320px; word-break: break-all; color: #64748b; font-size: 13px; }
.loading-cell, .no-data-cell { text-align: center; color: #94a3b8; padding: 32px !important; }
</style>
