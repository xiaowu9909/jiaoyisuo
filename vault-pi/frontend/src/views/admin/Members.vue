<script setup>
import { ref, onMounted } from 'vue'

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const members = ref([])
const page = ref(1)
const totalPages = ref(0)
const totalElements = ref(0)
const searchKey = ref('')

async function loadMembers() {
  try {
    const q = new URLSearchParams({ pageNo: page.value, pageSize: 20 })
    if (searchKey.value) q.set('searchKey', searchKey.value)
    const res = await fetch(`${API_BASE}/admin/member/page?${q}`, { credentials: 'include' })
    const json = await res.json()
    if (json.code === 0) {
      members.value = json.data.content || []
      totalPages.value = json.data.totalPages
      totalElements.value = json.data.totalElements
    }
  } catch (_) {}
}

function search() { page.value = 1; loadMembers() }
function gotoPage(p) { page.value = p; loadMembers() }

async function toggleStatus(m) {
  const newStatus = m.status === 0 ? 1 : 0
  try {
    await fetch(`${API_BASE}/admin/member/${m.id}/status?status=${newStatus}`, {
      method: 'PUT', credentials: 'include'
    })
    loadMembers()
  } catch (_) {}
}

onMounted(loadMembers)
</script>

<template>
  <div class="admin-page">
    <h1 class="page-title">会员管理</h1>
    <div class="toolbar">
      <input v-model="searchKey" placeholder="搜索用户名/邮箱/手机" class="search-input" @keyup.enter="search" />
      <button class="btn-primary" @click="search">搜索</button>
      <span class="total">共 {{ totalElements }} 条</span>
    </div>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>ID</th><th>用户名</th><th>邮箱</th><th>手机</th><th>昵称</th><th>邀请码</th><th>状态</th><th>注册时间</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in members" :key="m.id">
            <td>{{ m.id }}</td>
            <td>{{ m.username }}</td>
            <td>{{ m.email || '-' }}</td>
            <td>{{ m.phone || '-' }}</td>
            <td>{{ m.nickname || '-' }}</td>
            <td>{{ m.inviteCode || '-' }}</td>
            <td><span :class="m.status === 0 ? 'badge-ok' : 'badge-ban'">{{ m.status === 0 ? '正常' : '禁用' }}</span></td>
            <td>{{ m.registrationTime || '-' }}</td>
            <td>
              <button class="btn-sm" @click="toggleStatus(m)">
                {{ m.status === 0 ? '禁用' : '启用' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="pagination" v-if="totalPages > 1">
      <button v-for="p in totalPages" :key="p" :class="{ active: p === page }" @click="gotoPage(p)">{{ p }}</button>
    </div>
  </div>
</template>

<style scoped>
.admin-page { max-width: 1200px; }
.page-title { font-size: 22px; font-weight: 700; margin: 0 0 20px; color: #fff; }
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.search-input {
  padding: 6px 12px; background: #172636; border: 1px solid #27313e; border-radius: 4px;
  color: #fff; font-size: 13px; width: 260px;
}
.btn-primary {
  padding: 6px 16px; background: #f0a70a; border: none; border-radius: 4px;
  color: #000; font-size: 13px; cursor: pointer; font-weight: 600;
}
.total { color: #828ea1; font-size: 12px; margin-left: auto; }
.table-wrap { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.data-table th { padding: 8px 10px; text-align: left; color: #828ea1; border-bottom: 1px solid #1e2d3d; font-weight: 600; white-space: nowrap; }
.data-table td { padding: 8px 10px; border-bottom: 1px solid #1e2d3d; color: #e4e4e7; white-space: nowrap; }
.badge-ok { color: #0ecb81; }
.badge-ban { color: #f6465d; }
.btn-sm { padding: 3px 10px; background: transparent; border: 1px solid #f0a70a; color: #f0a70a; border-radius: 4px; cursor: pointer; font-size: 11px; }
.btn-sm:hover { background: rgba(240,167,10,0.1); }
.pagination { display: flex; gap: 4px; margin-top: 16px; }
.pagination button { padding: 4px 10px; background: #172636; border: 1px solid #27313e; color: #828ea1; border-radius: 4px; cursor: pointer; font-size: 12px; }
.pagination button.active { border-color: #f0a70a; color: #f0a70a; }
</style>
