<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAiPlans, postAiSubscribePurchase } from '../../api'

const router = useRouter()

const plans = ref([])
const loading = ref(true)

// confirm modal
const showConfirm = ref(false)
const selectedPlan = ref(null)

// result states
const submitting = ref(false)
const showSuccess = ref(false)
const showErrorModal = ref(false)
const errorMsg = ref('')
const isInsufficientBalance = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await getAiPlans()
    plans.value = Array.isArray(data) ? data : []
  } catch (_) {
    plans.value = []
  } finally {
    loading.value = false
  }
}

function openConfirm(plan) {
  selectedPlan.value = plan
  showConfirm.value = true
}

function cancelConfirm() {
  showConfirm.value = false
  selectedPlan.value = null
}

async function confirmPurchase() {
  if (!selectedPlan.value || submitting.value) return
  submitting.value = true
  try {
    await postAiSubscribePurchase(selectedPlan.value.id)
    showConfirm.value = false
    showSuccess.value = true
    setTimeout(() => {
      router.push('/uc/ai/orders')
    }, 1500)
  } catch (e) {
    showConfirm.value = false
    const msg = e?.message || '订阅失败'
    if (msg.includes('余额不足') || msg.includes('balance')) {
      isInsufficientBalance.value = true
      errorMsg.value = '账户可用余额不足以支付订阅费'
    } else {
      isInsufficientBalance.value = false
      errorMsg.value = msg
    }
    showErrorModal.value = true
  } finally {
    submitting.value = false
  }
}

function closeErrorModal() {
  showErrorModal.value = false
  errorMsg.value = ''
  isInsufficientBalance.value = false
}

function goRecharge() {
  closeErrorModal()
  router.push('/uc/recharge')
}

onMounted(load)
</script>

<template>
  <div class="uc-sub">
    <h2 class="uc-page-title">AI订阅中心</h2>

    <div v-if="loading" class="uc-hint">加载中...</div>

    <div v-else-if="showSuccess" class="success-state">
      <div class="success-icon">✓</div>
      <div class="success-text">引擎激活成功！</div>
      <div class="success-sub">正在跳转至订单记录...</div>
    </div>

    <div v-else>
      <div v-if="plans.length === 0" class="empty-hint">暂无可用套餐</div>

      <div v-else class="plans-grid">
        <div
          v-for="plan in plans"
          :key="plan.id"
          class="plan-card"
        >
          <div class="plan-name">{{ plan.name }}</div>
          <div class="plan-price-wrap">
            <span class="plan-price mono">{{ Number(plan.price).toFixed(2) }}</span>
            <span class="plan-currency">USDT</span>
          </div>
          <div class="plan-duration">{{ plan.duration || plan.days }}天有效</div>
          <div v-if="plan.description" class="plan-desc">{{ plan.description }}</div>
          <button class="plan-btn" @click="openConfirm(plan)">立即激活</button>
        </div>
      </div>
    </div>

    <!-- Confirm Modal -->
    <div v-if="showConfirm" class="modal-overlay" @click.self="cancelConfirm">
      <div class="modal-box">
        <div class="modal-title">确认激活</div>
        <div class="modal-body">
          是否支付 <span class="modal-price mono">{{ selectedPlan ? Number(selectedPlan.price).toFixed(2) : '' }} USDT</span> 激活AI幻影引擎？
        </div>
        <div class="modal-actions">
          <button class="modal-btn modal-btn-cancel" :disabled="submitting" @click="cancelConfirm">取消</button>
          <button class="modal-btn modal-btn-confirm" :disabled="submitting" @click="confirmPurchase">
            {{ submitting ? '处理中...' : '确认支付' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Error Modal -->
    <div v-if="showErrorModal" class="modal-overlay" @click.self="closeErrorModal">
      <div class="modal-box">
        <div class="modal-title error-title">激活失败</div>
        <div class="modal-body">{{ errorMsg }}</div>
        <div v-if="isInsufficientBalance" class="recharge-wrap">
          <button class="recharge-btn" @click="goRecharge">前往钱包充值</button>
        </div>
        <div class="modal-actions">
          <button class="modal-btn modal-btn-cancel" @click="closeErrorModal">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.uc-page-title {
  font-size: 18px;
  font-weight: 600;
  color: #e0ecf8;
  margin-bottom: 24px;
}

.uc-hint,
.empty-hint {
  color: #6b8299;
  font-size: 14px;
  padding: 24px 0;
  text-align: center;
}

/* Plans grid */
.plans-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

@media (max-width: 768px) {
  .plans-grid {
    grid-template-columns: 1fr;
  }
}

@media (min-width: 769px) and (max-width: 1024px) {
  .plans-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.plan-card {
  background: var(--ui-surface, #172636);
  border: 1px solid #1e3448;
  border-radius: 10px;
  padding: 20px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: border-color 0.2s;
}

.plan-card:hover {
  border-color: #f0a70a55;
}

.plan-name {
  font-size: 15px;
  font-weight: 600;
  color: #e0ecf8;
}

.plan-price-wrap {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.plan-price {
  font-size: 28px;
  font-weight: 700;
  color: #f0a70a;
}

.plan-currency {
  font-size: 14px;
  color: #a0b4c8;
}

.plan-duration {
  font-size: 13px;
  color: #6b8299;
}

.plan-desc {
  font-size: 12px;
  color: #4a6275;
  line-height: 1.5;
  flex: 1;
}

.plan-btn {
  margin-top: 8px;
  width: 100%;
  padding: 10px 0;
  background: #f0a70a;
  color: #0b1520;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}

.plan-btn:hover {
  background: #d4920a;
}

/* Success state */
.success-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 0;
  gap: 12px;
}

.success-icon {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: #22c55e22;
  color: #22c55e;
  font-size: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.success-text {
  font-size: 18px;
  font-weight: 600;
  color: #22c55e;
}

.success-sub {
  font-size: 13px;
  color: #6b8299;
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}

.modal-box {
  background: #172636;
  border: 1px solid #1e3448;
  border-radius: 12px;
  padding: 24px 24px 20px;
  width: 100%;
  max-width: 380px;
}

.modal-title {
  font-size: 16px;
  font-weight: 600;
  color: #e0ecf8;
  margin-bottom: 14px;
}

.error-title {
  color: #f87171;
}

.modal-body {
  font-size: 14px;
  color: #a0b4c8;
  line-height: 1.6;
  margin-bottom: 16px;
}

.modal-price {
  color: #f0a70a;
  font-weight: 600;
}

.recharge-wrap {
  margin-bottom: 16px;
}

.recharge-btn {
  width: 100%;
  padding: 12px 0;
  background: #f0a70a;
  color: #0b1520;
  border: none;
  border-radius: 6px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.2s;
}

.recharge-btn:hover {
  background: #d4920a;
}

.modal-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.modal-btn {
  padding: 8px 20px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: background 0.2s;
}

.modal-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.modal-btn-cancel {
  background: #1e3448;
  color: #a0b4c8;
}

.modal-btn-cancel:hover:not(:disabled) {
  background: #243f58;
}

.modal-btn-confirm {
  background: #f0a70a;
  color: #0b1520;
  font-weight: 600;
}

.modal-btn-confirm:hover:not(:disabled) {
  background: #d4920a;
}

.mono {
  font-family: monospace;
}
</style>
