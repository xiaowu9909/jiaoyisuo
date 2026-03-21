<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getAiPhrasesByType } from '../../api'

const phrases = ref([])
const currentPhrase = ref('')
let timer = null

function pickRandom() {
  if (phrases.value.length === 0) return
  const pool = phrases.value
  const idx = Math.floor(Math.random() * pool.length)
  const item = pool[idx]
  currentPhrase.value = typeof item === 'string' ? item : (item?.content || item?.text || String(item))
}

onMounted(async () => {
  try {
    const data = await getAiPhrasesByType(1)
    phrases.value = Array.isArray(data) ? data : []
  } catch (_) {
    phrases.value = []
  }
  pickRandom()
  timer = setInterval(pickRandom, 8000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="ai-stream-card">
    <div class="ai-status-header">
      <span class="breathe-dot"></span>
      <span class="ai-label">AI云端量化引擎运行中</span>
    </div>
    <div class="ai-phrase-text">{{ currentPhrase }}</div>
  </div>
</template>

<style scoped>
.ai-stream-card {
  background: var(--ui-surface, #172636);
  border: 1px solid #1e3448;
  border-radius: 8px;
  padding: 16px 20px;
}

.ai-status-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

@keyframes breathe {
  0%, 100% { opacity: 1; box-shadow: 0 0 6px #22c55e; }
  50% { opacity: 0.3; box-shadow: none; }
}

.breathe-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  flex-shrink: 0;
  animation: breathe 2s ease-in-out infinite;
}

.ai-label {
  font-size: 13px;
  color: #a0b4c8;
  letter-spacing: 0.02em;
}

.ai-phrase-text {
  font-size: 13px;
  color: #6b8299;
  line-height: 1.6;
  min-height: 20px;
  transition: opacity 0.3s;
}
</style>
