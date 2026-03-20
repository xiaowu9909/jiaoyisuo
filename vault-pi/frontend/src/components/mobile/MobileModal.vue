<script setup>
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: String,
  type: { type: String, default: 'modal' } // modal, drawer
})

const emit = defineEmits(['update:modelValue'])

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <Transition name="fade">
    <div v-if="modelValue" class="m-modal-overlay" @click.self="close">
      <div 
        class="m-modal-content" 
        :class="type"
      >
        <div class="m-modal-header">
          <h3>{{ title }}</h3>
          <span class="m-close" @click="close">&times;</span>
        </div>
        <div class="m-modal-body">
          <slot></slot>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.m-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(4px);
  z-index: 1000;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
}

.m-modal-content {
  background: #1e293b;
  border-radius: 16px;
  width: 100%;
  max-width: 400px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4);
  border: 1px solid #334155;
}

/* Modal Type */
.m-modal-content.modal {
  animation: modalIn 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

/* Drawer Type */
.m-modal-overlay:has(.drawer) {
  align-items: flex-end;
  padding: 0;
}
.m-modal-content.drawer {
  max-width: none;
  border-radius: 24px 24px 0 0;
  animation: drawerIn 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.m-modal-header {
  padding: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #334155;
}
.m-modal-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #fff;
}
.m-close {
  font-size: 28px;
  color: #94a3b8;
  cursor: pointer;
  line-height: 1;
}

.m-modal-body {
  padding: 20px;
  overflow-y: auto;
  max-height: 80vh;
}
/* 抽屉式：键盘弹出时底部留白，便于滚动使确认按钮可见 */
.m-modal-content.drawer .m-modal-body {
  padding-bottom: max(20px, env(safe-area-inset-bottom, 0px) + 80px);
}

/* Animations */
@keyframes modalIn {
  from { opacity: 0; transform: scale(0.95); }
  to { opacity: 1; transform: scale(1); }
}
@keyframes drawerIn {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
