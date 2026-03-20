<script setup>
import { message } from '../components/toast';

import { ref, onMounted } from 'vue'
import { 
  getAdminEnvelopeAll, 
  postAdminEnvelopeUpdate, 
  postAdminEnvelopeDelete 
} from '../api/admin'

const list = ref([])
const loading = ref(false)
const errorMsg = ref('')

async function load() {
  loading.value = true
  try {
    list.value = await getAdminEnvelopeAll()
  } catch (e) {
    errorMsg.value = e.message
  } finally {
    loading.value = false
  }
}

async function doDelete(id) {
  if (!confirm('确定要永久销毁此红包记录吗？这将导致关联的领取审计失效。')) return
  try {
    await postAdminEnvelopeDelete(id)
    load()
  } catch (e) {
    message.error(e.message)
  }
}

async function setStatus(env, s) {
  try {
    await postAdminEnvelopeUpdate({ id: env.id, status: s })
    load()
  } catch (e) {
    message.error(e.message)
  }
}

function formatStatus(s) {
  const map = { 0: '领取中', 1: '已领完', 2: '已过期' }
  return map[s] || '未知'
}

function getStatusClass(s) {
  const map = { 0: 'tag-active', 1: 'tag-done', 2: 'tag-expired' }
  return map[s] || ''
}

onMounted(load)
</script>

<template>
  <div class="admin-page envelope-page">
    <div class="admin-card">
      <div class="card-head">
        <span class="card-title">营销审计 - 全站红包发放记录</span>
        <button type="button" class="btn btn-small btn-primary" @click="load">刷新审计数据</button>
      </div>
      <div class="card-body">
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>发放 UID</th>
                <th>币种</th>
                <th>总金额/剩余</th>
                <th>总个数/剩余</th>
                <th>红包类型</th>
                <th>展现状态</th>
                <th>创建时间</th>
                <th style="text-align: right">多维操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="8" class="loading-cell">正在穿透红包资产账本...</td>
              </tr>
              <tr v-else-if="!list.length">
                <td colspan="8" class="no-data-cell">未监测到红包发放资产记录</td>
              </tr>
              <tr v-for="env in list" :key="env.id">
                <td>#{{ env.memberId || '系统' }}</td>
                <td><span class="symbol-tag">{{ env.symbol }}</span></td>
                <td>
                  <div class="amount-val">Σ {{ env.totalAmount }}</div>
                  <div class="remain-val">剩 {{ env.remainingAmount }}</div>
                </td>
                <td>
                  <div class="count-val">{{ env.totalCount }} 个</div>
                  <div class="remain-count">剩 {{ env.remainingCount }} 个</div>
                </td>
                <td>
                  <span :class="['type-tag', env.type === 0 ? 'luck' : 'equal']">
                    {{ env.type === 0 ? '拼手气随机' : '普通等额' }}
                  </span>
                </td>
                <td>
                  <span :class="['status-tag', getStatusClass(env.status)]">{{ formatStatus(env.status) }}</span>
                </td>
                <td class="time-cell">{{ new Date(env.createTime).toLocaleString() }}</td>
                <td style="text-align: right">
                  <button v-if="env.status === 0" type="button" class="btn-sm btn-expire" @click="setStatus(env, 2)">强制倒时</button>
                  <button type="button" class="btn-sm btn-danger-lite" @click="doDelete(env.id)">销毁记录</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.envelope-page { color: #333; }
.admin-card { border: 1px solid #eef0f2; border-radius: 8px; overflow: hidden; background: #fff; box-shadow: 0 4px 12px rgba(0,0,0,0.03); }
.card-head { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; background: #f8f9fa; border-bottom: 1px solid #eef0f2; }
.card-title { font-size: 15px; font-weight: 600; color: #1a202c; }
.card-body { padding: 20px; }

.table-wrap { border: 1px solid #edf2f7; border-radius: 6px; overflow: hidden; }
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th { text-align: left; padding: 14px 16px; background: #f7fafc; color: #4a5568; font-weight: 600; }
.data-table td { padding: 14px 16px; border-top: 1px solid #edf2f7; vertical-align: middle; }

.symbol-tag { background: #f1f5f9; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 700; color: #475569; }
.amount-val { font-weight: 700; color: #1e293b; }
.remain-val { font-size: 11px; color: #10b981; }
.count-val { color: #475569; font-weight: 500; }
.remain-count { font-size: 11px; color: #64748b; }

.type-tag { font-size: 11px; padding: 2px 6px; border-radius: 4px; font-weight: 600; }
.type-tag.luck { color: #f59e0b; background: rgba(245, 158, 11, 0.1); }
.type-tag.equal { color: #3b82f6; background: rgba(59, 130, 246, 0.1); }

.status-tag { padding: 3px 10px; border-radius: 20px; font-size: 10px; font-weight: 700; color: #fff; }
.tag-active { background: #ef4444; } /* 红包用红色 */
.tag-done { background: #94a3b8; }
.tag-expired { background: #cbd5e1; }

.time-cell { font-size: 11px; color: #94a3b8; white-space: nowrap; }

.btn-sm { padding: 5px 12px; border-radius: 4px; font-size: 11px; cursor: pointer; border: none; font-weight: 500; margin-left: 6px; }
.btn-expire { background: #f8fafc; color: #475569; border: 1px solid #e2e8f0; }
.btn-danger-lite { background: rgba(229, 62, 62, 0.1); color: #e53e3e; }

.loading-cell, .no-data-cell { text-align: center; color: #94a3b8; padding: 60px; font-size: 14px; }
</style>
