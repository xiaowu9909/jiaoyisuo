<script setup>
import { message } from '../components/toast';

import { ref, onMounted, computed } from 'vue'
import { getAdminSystemConfigList, postAdminSystemConfigUpdate } from '../api/admin'

const list = ref([])
const loading = ref(false)
const submitting = ref(false)

async function load() {
  loading.value = true
  try {
    list.value = await getAdminSystemConfigList()
  } catch (e) {
    message.error(e.message)
  } finally {
    loading.value = false
  }
}

// Group configs for better UI
const groups = computed(() => {
  const map = {}
  list.value.forEach(item => {
    const gn = item.groupName || '基本配置'
    if (!map[gn]) map[gn] = []
    map[gn].push(item)
  })
  return map
})

async function saveItem(item) {
  submitting.value = true
  try {
    await postAdminSystemConfigUpdate({ id: item.id, value: item.value })
    message.success('保存成功')
  } catch (e) {
    message.error(e.message)
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="page-header">
      <h2 class="page-title">系统参数配置</h2>
      <button class="btn btn-ghost" @click="load">刷新数据</button>
    </div>

    <div v-if="loading" class="text-center py-20">正在读取系统配置...</div>
    
    <div v-else class="groups-container">
      <div v-for="(configs, groupName) in groups" :key="groupName" class="admin-card group-card">
        <h3 class="group-title">{{ groupName }}</h3>
        
        <div class="config-list">
          <div v-for="conf in configs" :key="conf.id" class="config-item">
            <div class="item-info">
              <label class="item-key">{{ conf.id }}</label>
              <div class="item-remark" v-if="conf.remark">{{ conf.remark }}</div>
            </div>
            <div class="item-control">
              <input v-model="conf.value" class="item-input" @keyup.enter="saveItem(conf)" />
              <button class="btn btn-primary btn-sm" :disabled="submitting" @click="saveItem(conf)">保存</button>
            </div>
          </div>
        </div>
      </div>
      
      <div v-if="list.length === 0" class="admin-card text-center py-10 empty-state">
        <p>暂无系统配置项。请在数据库中初始化 <code>system_config</code> 表。</p>
        <p class="text-xs text-muted mt-2">提示：常用 Key 如 REGISTER_OPEN, WITHDRAW_FEE_RATE 等</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-page { padding: 24px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-title { font-size: 20px; font-weight: 700; color: #1a202c; }

.groups-container { display: flex; flex-direction: column; gap: 24px; }
.group-card { background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); padding: 24px; }
.group-title { font-size: 16px; font-weight: 700; color: #2d3748; margin-bottom: 20px; padding-left: 12px; border-left: 4px solid var(--primary-color); }

.config-list { display: flex; flex-direction: column; gap: 20px; }
.config-item { display: flex; justify-content: space-between; align-items: flex-start; gap: 40px; border-bottom: 1px solid #f7fafc; padding-bottom: 16px; }
.config-item:last-child { border-bottom: none; padding-bottom: 0; }

.item-info { flex: 1; display: flex; flex-direction: column; gap: 4px; }
.item-key { font-family: monospace; font-weight: 600; color: #4a5568; font-size: 13px; }
.item-remark { font-size: 12px; color: #a0aec0; }

.item-control { display: flex; align-items: center; gap: 12px; width: 400px; }
.item-input { flex: 1; padding: 10px 12px; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 14px; outline: none; transition: border-color 0.2s; }
.item-input:focus { border-color: var(--primary-color); }

.btn-sm { padding: 8px 16px; font-size: 13px; }
.empty-state { color: #a0aec0; font-size: 14px; }

.btn { padding: 10px 20px; border-radius: 6px; font-weight: 600; cursor: pointer; border: 1px solid transparent; }
.btn-primary { background: #2d8cf0; color: #fff; }
.btn-ghost { background: #f7fafc; color: #4a5568; border-color: #e2e8f0; }

@media (max-width: 900px) {
  .config-item { flex-direction: column; gap: 12px; }
  .item-control { width: 100%; }
}
</style>
