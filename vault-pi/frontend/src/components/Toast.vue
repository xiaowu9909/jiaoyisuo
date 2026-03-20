<template>
  <transition name="toast-fade">
    <div v-show="visible" class="custom-toast" :class="[`toast-${type}`]">
      <span class="toast-message">{{ message }}</span>
    </div>
  </transition>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const props = defineProps({
  message: { type: String, required: true },
  type: { type: String, default: 'info' },
  duration: { type: Number, default: 3000 }
})

const emit = defineEmits(['closed'])

const visible = ref(false)

onMounted(() => {
  visible.value = true
  if (props.duration > 0) {
    setTimeout(() => {
      visible.value = false
      setTimeout(() => {
        emit('closed')
      }, 300) // wait for transition
    }, props.duration)
  }
})
</script>

<style scoped>
.custom-toast {
  position: fixed;
  top: 30px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
  padding: 10px 20px;
  border-radius: 4px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  display: flex;
  align-items: center;
  font-size: 14px;
  min-width: 200px;
  justify-content: center;
}

.toast-message {
  color: #fff;
}

.toast-info { background-color: #909399; }
.toast-success { background-color: #67c23a; }
.toast-warning { background-color: #e6a23c; }
.toast-error { background-color: #f56c6c; }

.toast-fade-enter-active,
.toast-fade-leave-active {
  transition: opacity 0.3s, transform 0.3s;
}

.toast-fade-enter-from,
.toast-fade-leave-to {
  opacity: 0;
  transform: translate(-50%, -20px);
}
</style>
