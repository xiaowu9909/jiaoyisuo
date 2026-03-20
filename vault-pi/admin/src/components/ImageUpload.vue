<script setup>
import { ref } from 'vue'
import { postAdminUploadImage } from '../api/admin'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '上传图片' },
  accept: { type: String, default: 'image/png,image/jpeg,image/gif,image/webp,image/svg+xml' },
})

const emit = defineEmits(['update:modelValue'])

const uploading = ref(false)
const inputEl = ref(null)

function triggerSelect() {
  if (uploading.value) return
  inputEl.value?.click()
}

function onFileChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  uploading.value = true
  postAdminUploadImage(file)
    .then((url) => {
      emit('update:modelValue', url || '')
    })
    .catch((err) => {
      alert(err?.message || '上传失败')
    })
    .finally(() => {
      uploading.value = false
      e.target.value = ''
    })
}

function clearImage() {
  emit('update:modelValue', '')
}
</script>

<template>
  <div class="image-upload">
    <div v-if="modelValue" class="preview-wrap">
      <img :src="modelValue" alt="预览" class="preview-img" @error="clearImage" />
      <div class="preview-actions">
        <button type="button" class="btn btn-sm btn-ghost" :disabled="uploading" @click="triggerSelect">
          {{ uploading ? '上传中…' : '更换' }}
        </button>
        <button type="button" class="btn btn-sm btn-ghost" :disabled="uploading" @click="clearImage">清除</button>
      </div>
    </div>
    <div v-else class="upload-trigger" @click="triggerSelect">
      <span v-if="uploading">上传中…</span>
      <span v-else>{{ placeholder }}</span>
    </div>
    <input ref="inputEl" type="file" class="hidden-input" :accept="accept" @change="onFileChange" />
  </div>
</template>

<style scoped>
.image-upload { display: inline-block; }
.preview-wrap { display: flex; flex-direction: column; gap: 8px; }
.preview-img { max-width: 220px; max-height: 80px; object-fit: contain; border: 1px solid #e2e8f0; border-radius: 8px; }
.preview-actions { display: flex; gap: 8px; }
.upload-trigger {
  width: 160px; height: 80px; border: 1px dashed #cbd5e1; border-radius: 8px;
  display: flex; align-items: center; justify-content: center; cursor: pointer; color: #64748b; font-size: 14px;
  background: #f8fafc;
}
.upload-trigger:hover { border-color: #2d8cf0; color: #2d8cf0; }
.hidden-input { position: absolute; width: 0; height: 0; opacity: 0; pointer-events: none; }
.btn { padding: 6px 12px; border-radius: 6px; font-size: 13px; cursor: pointer; border: 1px solid transparent; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-sm { padding: 4px 10px; font-size: 12px; }
.btn-ghost { background: #f1f5f9; color: #475569; }
.btn-ghost:hover:not(:disabled) { background: #e2e8f0; }
</style>
