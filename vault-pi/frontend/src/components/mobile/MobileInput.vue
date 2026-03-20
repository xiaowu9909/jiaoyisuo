<script setup>
const props = defineProps({
  modelValue: [String, Number],
  label: String,
  unit: String,
  type: { type: String, default: 'text' },
  placeholder: String,
  search: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

function onInput(e) {
  emit('update:modelValue', e.target.value)
}
</script>

<template>
  <div class="m-input" :class="{ 'search-mode': search, 'field-mode': label || unit }">
    <i v-if="search" class="icon-search"></i>
    <label v-if="label">{{ label }}</label>
    <input
      :type="type"
      :value="modelValue"
      :placeholder="placeholder"
      @input="onInput"
    />
    <span v-if="unit" class="unit">{{ unit }}</span>
  </div>
</template>

<style scoped>
.m-input {
  display: flex;
  align-items: center;
  background: #1e2936;
  border-radius: 8px;
  padding: 0 12px;
  height: 44px;
  border: 1px solid #334155;
  transition: border-color 0.2s;
  width: 100%;
  box-sizing: border-box;
}

.m-input:focus-within {
  border-color: #3b82f6;
}

input {
  flex: 1;
  background: none;
  border: none;
  color: #fff;
  font-size: 15px;
  height: 100%;
  width: 100%;
  outline: none;
}

input::placeholder {
  color: #64748b;
}

/* Search Mode */
.m-input.search-mode {
  border-radius: 22px;
  height: 36px;
  padding: 0 16px;
}
.icon-search {
  width: 14px;
  height: 14px;
  margin-right: 8px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%2364748b'%3E%3Cpath d='M15.5 14h-.79l-.28-.27A6.471 6.471 0 0016 9.5 6.5 6.5 0 109.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z'/%3E%3C/svg%3E");
  background-size: contain;
  background-repeat: no-repeat;
}

/* Field Mode (Label + Unit) */
.m-input.field-mode {
  height: 56px;
  background: #0f172a;
  border-radius: 12px;
}
.m-input label {
  width: 70px;
  font-size: 14px;
  color: #64748b;
  font-weight: 600;
}
.m-input.field-mode input {
  text-align: right;
  font-weight: 700;
  font-family: 'Roboto Mono', monospace;
  padding-right: 8px;
}
.m-input .unit {
  font-size: 13px;
  color: #475569;
  font-weight: 800;
}
</style>
