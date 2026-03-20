<script setup>
import { message } from '../components/toast';

import { ref, onMounted } from 'vue'
import { 
  getAdminExchangeCoinList, 
  postAdminExchangeCoinAdd, 
  postAdminExchangeCoinUpdate, 
  postAdminExchangeCoinDelete 
} from '../api/admin'

const list = ref([])
const loading = ref(false)
const errorMsg = ref('')
const searchSymbol = ref('')
const searchBase = ref('')

// Modal state
const modalVisible = ref(false)
const modalSubmitting = ref(false)
const modalError = ref('')
const isEdit = ref(false)
const formData = ref({
  id: null,
  symbol: '',
  baseSymbol: '',
  coinSymbol: '',
  baseCoinPrecision: null,
  coinPrecision: null,
  enable: true,
  virtual: false,
  customPrice: null,
  customPriceLow: null,
  customPriceHigh: null,
  virtualActivity: 'NORMAL',
  virtualDriftDaily: null,
  virtualVolatility: null,
  virtualTickSize: null
})

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await getAdminExchangeCoinList()
    list.value = data || []
    if (searchSymbol.value || searchBase.value) {
      list.value = list.value.filter((c) => {
        const matchSymbol = !searchSymbol.value || (c.symbol && c.symbol.toLowerCase().includes(searchSymbol.value.toLowerCase()))
        const matchBase = !searchBase.value || (c.baseSymbol && c.baseSymbol.toLowerCase().includes(searchBase.value.toLowerCase()))
        return matchSymbol && matchBase
      })
    }
  } catch (e) {
    errorMsg.value = e.message
  } finally {
    loading.value = false
  }
}

function onSearch() {
  load()
}

function openAdd() {
  isEdit.value = false
  formData.value = { id: null, symbol: '', baseSymbol: '', coinSymbol: '', baseCoinPrecision: null, coinPrecision: null, enable: true, virtual: false, customPrice: null, customPriceLow: null, customPriceHigh: null, virtualActivity: 'NORMAL', virtualDriftDaily: null, virtualVolatility: null, virtualTickSize: null }
  modalError.value = ''
  modalVisible.value = true
}

function openEdit(coin) {
  isEdit.value = true
  formData.value = { ...coin, virtualDriftDaily: coin.virtualDriftDaily ?? null, virtualVolatility: coin.virtualVolatility ?? null, virtualTickSize: coin.virtualTickSize ?? null }
  modalError.value = ''
  modalVisible.value = true
}

async function submitForm() {
  if (!formData.value.symbol || !formData.value.baseSymbol || !formData.value.coinSymbol) {
    modalError.value = '请填写完整必填项'
    return
  }
  if (formData.value.virtual) {
    const low = formData.value.customPriceLow != null && formData.value.customPriceLow !== '' ? Number(formData.value.customPriceLow) : NaN
    const high = formData.value.customPriceHigh != null && formData.value.customPriceHigh !== '' ? Number(formData.value.customPriceHigh) : NaN
    const single = formData.value.customPrice != null && formData.value.customPrice !== '' ? Number(formData.value.customPrice) : NaN
    const hasRange = !isNaN(low) && !isNaN(high) && low > 0 && high > 0 && low <= high
    const hasSingle = !isNaN(single) && single > 0
    if (!hasRange && !hasSingle) {
      modalError.value = '虚拟盘请填写有效的价格区间（下限 ≤ 上限且均大于 0），或填写单一自设价格'
      return
    }
  }
  modalSubmitting.value = true
  try {
    if (isEdit.value) {
      await postAdminExchangeCoinUpdate(formData.value)
    } else {
      await postAdminExchangeCoinAdd(formData.value)
    }
    modalVisible.value = false
    await load()
  } catch (e) {
    modalError.value = e.message
  } finally {
    modalSubmitting.value = false
  }
}

function priceRangeText(c) {
  if (!c.virtual) return '—'
  if (c.customPriceLow != null && c.customPriceHigh != null) return `${c.customPriceLow} - ${c.customPriceHigh}`
  if (c.customPrice != null) return String(c.customPrice)
  return '—'
}

function activityText(virtualActivity) {
  if (!virtualActivity) return '—'
  const map = { NORMAL: '一般', ACTIVE: '活跃', HOT: '热门' }
  return map[virtualActivity] || virtualActivity
}

async function doDelete(id) {
  if (!confirm('确定要删除该交易对吗？此操作不可逆。')) return
  try {
    await postAdminExchangeCoinDelete(id)
    await load()
  } catch (e) {
    message.error(e.message)
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page exchange-coin-page">
    <div class="admin-card">
      <div class="card-head">
        <span class="card-title">币币交易对配置管理</span>
        <button type="button" class="btn btn-small btn-primary btn-with-icon" @click="load">
          <SvgIcon name="refresh" :size="16" class="btn-icon" /> 刷新列表
        </button>
      </div>
      <div class="card-body">
        <div class="function-wrapper">
          <div class="search-wrapper">
            <input v-model="searchSymbol" type="text" class="search-input" placeholder="搜索交易对(BTC/USDT)" />
            <input v-model="searchBase" type="text" class="search-input" placeholder="按结算币(USDT)" />
            <button type="button" class="btn btn-info" @click="onSearch">筛选</button>
          </div>
          <div class="btns-wrapper">
            <button type="button" class="btn btn-primary btn-with-icon" @click="openAdd"><SvgIcon name="plus" :size="16" class="btn-icon" /> 新增交易对</button>
          </div>
        </div>
        
        <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
        
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>交易对名 (Symbol)</th>
                <th>结算币种</th>
                <th>交易币种</th>
                <th>类型</th>
                <th>价格区间</th>
                <th>活跃度</th>
                <th>GBM 趋势/波动</th>
                <th>精度 (价格/数量)</th>
                <th>运行状态</th>
                <th style="text-align: right">操作项</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="12" class="loading-cell">正在拉取交易快照...</td>
              </tr>
              <tr v-else-if="!list.length">
                <td colspan="12" class="no-data-cell">未匹配到符合条件的交易对</td>
              </tr>
              <tr v-for="c in list" :key="c.id">
                <td>{{ c.id }}</td>
                <td><b style="color: #111827">{{ c.symbol }}</b></td>
                <td>{{ c.baseSymbol }}</td>
                <td>{{ c.coinSymbol }}</td>
                <td>
                  <span :class="['status-tag', c.virtual ? 'virtual' : 'real']">
                    {{ c.virtual ? '虚拟盘' : '实盘' }}
                  </span>
                </td>
                <td>{{ priceRangeText(c) }}</td>
                <td>{{ activityText(c.virtualActivity) }}</td>
                <td>{{ (c.virtualDriftDaily != null || c.virtualVolatility != null) ? ((c.virtualDriftDaily ?? 0) + ' / ' + (c.virtualVolatility ?? 0.015)) : '—' }}</td>
                <td>{{ c.baseCoinPrecision != null ? c.baseCoinPrecision : '—' }} / {{ c.coinPrecision != null ? c.coinPrecision : '—' }}</td>
                <td>
                  <span :class="['status-tag', c.enable ? 'ok' : 'err']">
                    {{ c.enable ? '启用中' : '已禁用' }}
                  </span>
                </td>
                <td style="text-align: right">
                  <button type="button" class="btn-sm btn-info" @click="openEdit(c)">编辑</button>
                  <button type="button" class="btn-sm btn-danger-lite" @click="doDelete(c.id)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Add/Edit Modal -->
    <div v-if="modalVisible" class="modal-mask" @click.self="modalVisible = false">
      <div class="modal-wrap">
        <div class="modal-header">
          <div class="modal-title">{{ isEdit ? '编辑交易对配置' : '新增币币交易对' }}</div>
          <span class="modal-close" @click="modalVisible = false" aria-label="关闭"><SvgIcon name="close" :size="18" /></span>
        </div>
        <div class="modal-body">
          <div class="modal-form-item">
            <label>交易对标识 (Symbol)</label>
            <input v-model="formData.symbol" type="text" class="input" placeholder="例如: BTC/USDT" />
          </div>
          <div class="modal-form-item">
            <label>结算币种 (Base)</label>
            <input v-model="formData.baseSymbol" type="text" class="input" placeholder="例如: USDT" />
          </div>
          <div class="modal-form-item">
            <label>交易币种 (Coin)</label>
            <input v-model="formData.coinSymbol" type="text" class="input" placeholder="例如: BTC" />
          </div>
          <div class="modal-form-item row-half">
            <label>价格精度 (Base)</label>
            <input v-model.number="formData.baseCoinPrecision" type="number" min="0" class="input" placeholder="小数位，如 2" />
          </div>
          <div class="modal-form-item row-half">
            <label>数量精度 (Coin)</label>
            <input v-model.number="formData.coinPrecision" type="number" min="0" class="input" placeholder="小数位，如 6" />
          </div>
          <div class="modal-form-item">
            <label class="checkbox-label">
              <input type="checkbox" v-model="formData.virtual" /> 虚拟盘（价格区间或自设价格，行情由系统自动生成）
            </label>
          </div>
          <div v-if="formData.virtual" class="modal-form-item row-half-wrap">
            <div class="modal-form-item row-half">
              <label>价格下限</label>
              <input v-model="formData.customPriceLow" type="number" min="0" step="any" class="input" placeholder="例如 1.0" />
            </div>
            <div class="modal-form-item row-half">
              <label>价格上限</label>
              <input v-model="formData.customPriceHigh" type="number" min="0" step="any" class="input" placeholder="例如 2.0" />
            </div>
          </div>
          <div v-if="formData.virtual" class="modal-form-item">
            <label>自设价格（可选，无区间时使用）</label>
            <input v-model.number="formData.customPrice" type="number" min="0" step="any" class="input" placeholder="例如 1.5，与区间二选一" />
          </div>
          <div v-if="formData.virtual" class="modal-form-item">
            <label>行情活跃度</label>
            <select v-model="formData.virtualActivity" class="input">
              <option value="NORMAL">一般</option>
              <option value="ACTIVE">活跃</option>
              <option value="HOT">热门</option>
            </select>
          </div>
          <div v-if="formData.virtual" class="modal-form-item">
            <label>GBM 日趋势（如 0.01=每日约涨1%，-0.005=每日约跌0.5%）</label>
            <input v-model.number="formData.virtualDriftDaily" type="number" step="0.0001" class="input" placeholder="可选，默认 0" />
          </div>
          <div v-if="formData.virtual" class="modal-form-item">
            <label>GBM 波动率（如 0.02 约 2%，影响 K 线影线/实体）</label>
            <input v-model.number="formData.virtualVolatility" type="number" step="0.001" min="0" class="input" placeholder="可选，默认 0.015" />
          </div>
          <div v-if="formData.virtual" class="modal-form-item">
            <label>深度 Tick 间距（中心辐射挂单间距）</label>
            <input v-model.number="formData.virtualTickSize" type="number" step="0.00000001" min="0" class="input" placeholder="可选，如 0.1 或 0.0001" />
          </div>
          <div class="modal-form-item">
            <label>市场启用状态</label>
            <div class="toggle-wrap">
              <label><input type="radio" :value="true" v-model="formData.enable" /> 启用交易</label>
              <label><input type="radio" :value="false" v-model="formData.enable" /> 暂停交易</label>
            </div>
          </div>
          <p v-if="modalError" class="error-tip">{{ modalError }}</p>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn-cancel" @click="modalVisible = false">取消</button>
          <button type="button" class="btn-primary" @click="submitForm" :disabled="modalSubmitting">
            {{ modalSubmitting ? '正在应用变更...' : '确认保存' }}
          </button>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
.exchange-coin-page { color: #333; }
.admin-card { border: 1px solid #eef0f2; border-radius: 8px; overflow: hidden; background: #fff; box-shadow: 0 4px 12px rgba(0,0,0,0.03); }
.card-head { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; background: #f8f9fa; border-bottom: 1px solid #eef0f2; }
.card-title { font-size: 15px; font-weight: 600; color: #1a202c; }
.card-body { padding: 20px; }

.function-wrapper { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.search-wrapper { display: flex; align-items: center; gap: 10px; }
.search-input { width: 170px; padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 6px; font-size: 13px; }

.btn { padding: 8px 18px; border-radius: 6px; font-size: 14px; cursor: pointer; border: none; font-weight: 500; }
.btn-primary { background: #2d8cf0; color: #fff; }
.btn-info { background: #f7fafc; color: #4a5568; border: 1px solid #e2e8f0; }

.table-wrap { border: 1px solid #edf2f7; border-radius: 6px; overflow: hidden; }
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th { text-align: left; padding: 12px 16px; background: #f7fafc; color: #4a5568; font-weight: 600; }
.data-table td { padding: 14px 16px; border-top: 1px solid #edf2f7; }

.status-tag { padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 600; }
.status-tag.ok { background: #c6f6d5; color: #22543d; }
.status-tag.err { background: #fed7d7; color: #822727; }
.status-tag.virtual { background: #e9d8fd; color: #553c9a; }
.status-tag.real { background: #bee3f8; color: #2c5282; }
.checkbox-label { display: flex; align-items: center; gap: 8px; cursor: pointer; }

.btn-sm { padding: 4px 10px; font-size: 12px; border-radius: 4px; cursor: pointer; border: none; margin-left: 6px; }
.btn-danger-lite { background: rgba(229, 62, 62, 0.1); color: #e53e3e; }

.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.6); backdrop-filter: blur(2px); display: flex; align-items: center; justify-content: center; z-index: 2000; padding: 20px; box-sizing: border-box; }
.modal-wrap { width: 440px; max-width: 100%; max-height: calc(100vh - 40px); background: #fff; border-radius: 8px; box-shadow: 0 10px 25px rgba(0,0,0,0.2); display: flex; flex-direction: column; }
.modal-header { flex-shrink: 0; padding: 16px 20px; border-bottom: 1px solid #edf2f7; display: flex; justify-content: space-between; align-items: center; }
.modal-title { font-size: 14px; font-weight: 600; }
.modal-body { flex: 1; min-height: 0; overflow-y: auto; padding: 20px; }
.modal-form-item { margin-bottom: 14px; }
.modal-form-item:last-of-type { margin-bottom: 0; }
.modal-footer { flex-shrink: 0; padding: 16px 20px; background: #f8fafc; text-align: right; border-top: 1px solid #edf2f7; }
.modal-form-item.row-half { display: inline-block; width: calc(50% - 8px); margin-right: 8px; vertical-align: top; }
.modal-form-item.row-half-wrap { display: flex; gap: 12px; }
.modal-form-item.row-half-wrap .row-half { flex: 1; margin-right: 0; }
select.input { cursor: pointer; }
.modal-form-item label { display: block; margin-bottom: 6px; color: #4a5568; font-size: 13px; }
.input { width: 100%; padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 6px; outline: none; }
.toggle-wrap { display: flex; gap: 20px; font-size: 13px; padding-top: 4px; }
.btn-cancel { padding: 7px 18px; border: 1px solid #e2e8f0; border-radius: 6px; background: #fff; margin-right: 10px; cursor: pointer; }
.error-tip { color: #e53e3e; font-size: 12px; margin-top: 10px; }
.form-hint { color: #718096; font-size: 12px; margin: 6px 0 0; line-height: 1.4; }

.error { color: #e53e3e; padding: 10px; font-size: 13px; }
.loading-cell, .no-data-cell { text-align: center; color: #a0aec0; padding: 30px; }
</style>
