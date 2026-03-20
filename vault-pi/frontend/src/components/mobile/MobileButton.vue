<script setup>
defineProps({
  variant: { type: String, default: 'primary' }, // primary, success, danger, gray, tab
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  block: { type: Boolean, default: false }
})
</script>

<template>
  <button
    class="m-btn"
    :class="[variant, { disabled, loading, block }]"
    :disabled="disabled || loading"
    @click="$emit('click', $event)"
  >
    <span v-if="loading" class="m-btn-loader"></span>
    <slot v-else></slot>
  </button>
</template>

<style scoped>
.m-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
  outline: none;
  gap: 8px;
  user-select: none;
}

.m-btn.block { width: 100%; }

/* Variants */
.m-btn.primary { background: #3b82f6; color: #fff; }
.m-btn.primary:active:not(.disabled) { background: #2563eb; transform: scale(0.98); }

.m-btn.success { background: #0ecb81; color: #fff; }
.m-btn.success:active:not(.disabled) { background: #0ca66b; transform: scale(0.98); }

.m-btn.danger { background: #f6465d; color: #fff; }
.m-btn.danger:active:not(.disabled) { background: #d93e52; transform: scale(0.98); }

.m-btn.gray { background: #334155; color: #cbd5e1; }
.m-btn.gray:active:not(.disabled) { background: #1e293b; transform: scale(0.98); }

.m-btn.tab {
  background: none;
  color: #94a3b8;
  padding: 14px 0;
  border-radius: 0;
  border-bottom: 2px solid transparent;
}
.m-btn.tab.active {
  color: #3b82f6;
  border-bottom-color: #3b82f6;
}

/* States */
.m-btn.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.m-btn-loader {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
