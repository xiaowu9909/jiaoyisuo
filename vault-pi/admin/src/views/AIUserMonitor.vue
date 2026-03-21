<script setup>
import { ref, onMounted } from 'vue'
import { message } from '../components/toast'
import {
  getAdminAiUsers,
  postAdminAiUserCancel,
} from '../api/admin'

const list = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await getAdminAiUsers()
    list.value = Array.isArray(data) ? data : (data?.list || data?.content || [])
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

async function cancelAi(item) {
  if (!confirm(`确定强制取消用户 [${item.username || item.userId}] 的AI订阅？`)) return
  try {
    await postAdminAiUserCancel(item.userId)
    message.success('已强制取消AI订阅')
    await load()
  } catch (e) {
    message.error(e.message)
  }
}

function formatTime(str) {
  if (!str) return '—'
  return new Date(str).toLocaleString('zh-CN')
}

function isExpired(dateStr) {
  if (!dateStr) return false
  return new Date(dateStr) < new Date()
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="page-header">
      <div class="title-group">
        <h2 class="page-title">AI用户监控</h2>
        <span v-if="!loading" class="count-badge">共 {{ list.length }} 位用户</span>
      </div>
      <button type="button" class="btn btn-ghost" @click="load">刷新</button>
    </div>

    <div class="admin-card">
      <div class="table-container">
        <table>
          <thead>
            <tr>
              <th>用户ID</th>
              <th>用户名</th>
              <th>当前余额</th>
              <th>AI订阅到期时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="5" class="text-center py-8">正在加载...</td>
            </tr>
            <tr v-else-if="!list.length">
              <td colspan="5" class="text-center py-8">暂无AI用户数据</td>
            </tr>
            <tr v-for="item in list" :key="item.userId">
              <td class="col-id">{{ item.userId }}</td>
              <td class="col-username">{{ item.username || '—' }}</td>
              <td class="col-balance">
                <span class="balance-val">{{ item.balance != null ? Number(item.balance).toFixed(2) : '—' }}</span>
                <span v-if="item.balance != null" class="unit">USDT</span>
              </td>
              <td class="col-expire">
                <div class="expire-wrap">
                  <span>{{ formatTime(item.aiExpireTime) }}</span>
                  <span v-if="isExpired(item.aiExpireTime)" class="badge badge-expired">已到期</span>
                </div>
              </td>
              <td class="action-cell">
                <button class="btn btn-danger-sm" @click="cancelAi(item)">强制取消AI</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-page { padding: 24px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.title-group { display: flex; align-items: center; gap: 12px; }
.page-title { font-size: 20px; font-weight: 700; color: var(--text-main); margin: 0; }
.count-badge { background: var(--table-th-bg); border: 1px solid var(--border-color); color: var(--text-secondary); padding: 3px 10px; border-radius: 999px; font-size: 13px; font-weight: 500; }

.admin-card { background: var(--ui-surface-2); border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); padding: 20px; }
.table-container { width: 100%; overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 14px; }
th { text-align: left; padding: 12px 16px; background: var(--table-th-bg); color: var(--text-secondary); font-weight: 600; border-bottom: 1px solid var(--border-color); }
td { padding: 14px 16px; border-bottom: 1px solid var(--border-color); vertical-align: middle; }
.text-center { text-align: center; }
.py-8 { padding-top: 32px; padding-bottom: 32px; color: var(--text-muted); }

.col-id { color: var(--text-muted); font-size: 12px; width: 80px; }
.col-username { font-weight: 500; color: var(--text-main); }
.col-balance { }
.balance-val { font-weight: 600; color: var(--text-main); }
.unit { font-size: 11px; color: var(--text-muted); margin-left: 4px; }

.col-expire { }
.expire-wrap { display: flex; align-items: center; gap: 8px; }

.badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
.badge-expired { background: #fff5f5; color: var(--color-danger-alt, #c53030); border: 1px solid #fed7d7; }

.action-cell { white-space: nowrap; }

.btn { padding: 8px 16px; border-radius: 6px; font-weight: 600; cursor: pointer; border: 1px solid transparent; transition: all 0.2s; font-size: 13px; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-ghost { background: var(--table-th-bg); color: var(--text-secondary); border-color: var(--border-color); }
.btn-danger-sm { background: #fff5f5; color: var(--color-danger-alt, #c53030); border: 1px solid #fed7d7; padding: 6px 14px; font-size: 12px; font-weight: 600; border-radius: 6px; cursor: pointer; transition: all 0.2s; }
.btn-danger-sm:hover { background: #fed7d7; }
</style>
