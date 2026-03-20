<script setup>
import { message } from '../components/toast';

import { ref, onMounted } from 'vue'
import { getAdminDepositAddressList, postAdminDepositAddressUpdate, postAdminDepositAddressAdd } from '../api/admin'

const list = ref([])
const loading = ref(false)
const savingId = ref(null)
const showAdd = ref(false)
const addForm = ref({ unit: '', name: '', address: '' })
const addSaving = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await getAdminDepositAddressList()
  } catch (e) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function save(item) {
  savingId.value = item.id
  try {
    await postAdminDepositAddressUpdate({ coinId: item.id, address: item.depositAddress || '' })
    message.success('保存成功')
  } catch (e) {
    message.error(e.message || '保存失败')
  } finally {
    savingId.value = null
  }
}

function openAdd() {
  addForm.value = { unit: '', name: '', address: '' }
  showAdd.value = true
}

async function submitAdd() {
  const { unit, name, address } = addForm.value
  if (!unit || !unit.trim()) {
    message.error('请填写币种')
    return
  }
  addSaving.value = true
  try {
    await postAdminDepositAddressAdd({ unit: unit.trim(), name: (name || '').trim(), address: (address || '').trim() })
    message.success('添加成功')
    showAdd.value = false
    await load()
  } catch (e) {
    message.error(e.message || '添加失败')
  } finally {
    addSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="page-header">
      <h2 class="page-title">充币地址</h2>
      <p class="page-desc">可在此添加、修改各币种的充币地址，C 端充值页将按币种展示对应地址。</p>
      <div class="header-actions">
        <button class="btn btn-primary" @click="openAdd">+ 添加充币地址</button>
        <button class="btn btn-ghost" @click="load">刷新</button>
      </div>
    </div>

    <!-- 新增一行 -->
    <div v-if="showAdd" class="admin-card add-form-card">
      <h3 class="add-form-title">新增充币地址</h3>
      <div class="add-form-row">
        <input v-model="addForm.unit" class="add-input" placeholder="币种（必填，如 USDT）" />
        <input v-model="addForm.name" class="add-input" placeholder="名称（选填）" />
        <input v-model="addForm.address" class="add-input address-input" placeholder="充币地址" />
        <button class="btn btn-primary btn-sm" :disabled="addSaving" @click="submitAdd">{{ addSaving ? '提交中…' : '添加' }}</button>
        <button class="btn btn-ghost btn-sm" :disabled="addSaving" @click="showAdd = false">取消</button>
      </div>
    </div>

    <div v-if="loading" class="text-center py-20">加载中...</div>
    <div v-else class="admin-card table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>币种</th>
            <th>名称</th>
            <th>充币地址</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in list" :key="item.id">
            <td><strong>{{ item.unit }}</strong></td>
            <td>{{ item.name }}</td>
            <td>
              <input
                v-model="item.depositAddress"
                class="address-input"
                type="text"
                placeholder="请输入该币种充币地址"
              />
            </td>
            <td>
              <button
                class="btn btn-primary btn-sm"
                :disabled="savingId === item.id"
                @click="save(item)"
              >
                {{ savingId === item.id ? '保存中…' : '保存' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!list.length" class="empty-hint">暂无数据，点击「添加充币地址」创建。</p>
    </div>
  </div>
</template>

<style scoped>
.admin-page { padding: 24px; }
.page-header { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-bottom: 24px; }
.page-title { font-size: 20px; font-weight: 700; color: #1a202c; width: 100%; }
.page-desc { font-size: 14px; color: #64748b; margin: 0; flex: 1; }
.header-actions { display: flex; gap: 10px; margin-left: auto; }
.btn-ghost { background: #f1f5f9; color: #475569; border: 1px solid #e2e8f0; }

.add-form-card { margin-bottom: 24px; }
.add-form-title { font-size: 15px; font-weight: 600; color: #334155; margin: 0 0 16px 0; }
.add-form-row { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.add-input { padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 14px; width: 140px; }
.add-input.address-input { min-width: 280px; flex: 1; font-family: monospace; }
.add-input:focus { outline: none; border-color: var(--primary-color, #2d8cf0); }

.admin-card { background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); padding: 24px; }
.table-wrap { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th { text-align: left; padding: 12px 16px; background: #f8fafc; font-size: 13px; color: #475569; border-bottom: 1px solid #e2e8f0; }
.data-table td { padding: 12px 16px; border-bottom: 1px solid #f1f5f9; font-size: 14px; }
.data-table tbody tr:hover { background: #fafafa; }

.address-input { width: 100%; min-width: 280px; padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 13px; font-family: monospace; }
.address-input:focus { outline: none; border-color: var(--primary-color, #2d8cf0); }

.btn { padding: 10px 20px; border-radius: 6px; font-weight: 600; cursor: pointer; border: 1px solid transparent; }
.btn-primary { background: #2d8cf0; color: #fff; }
.btn-sm { padding: 8px 16px; font-size: 13px; }
.btn:disabled { opacity: 0.7; cursor: not-allowed; }

.empty-hint { text-align: center; color: #94a3b8; padding: 24px; margin: 0; }
</style>
