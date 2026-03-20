<script setup>
const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
})
</script>

<template>
  <section class="ui-page">
    <header class="ui-page-header">
      <div>
        <h1 class="ui-page-title">{{ props.title }}</h1>
        <p v-if="props.subtitle" class="ui-page-subtitle">{{ props.subtitle }}</p>
      </div>
      <div class="ui-page-actions">
        <slot name="actions" />
      </div>
    </header>
    <div class="ui-page-body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.ui-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 18px 22px 22px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top left, rgba(148, 163, 184, 0.16), transparent 55%),
    radial-gradient(circle at bottom right, rgba(56, 189, 248, 0.08), transparent 60%),
    rgba(15, 23, 42, 0.72);
  border: 1px solid rgba(148, 163, 184, 0.35);
  box-shadow:
    0 24px 70px rgba(15, 23, 42, 0.85),
    0 0 0 1px rgba(15, 23, 42, 0.8) inset;
  backdrop-filter: blur(26px);
}
.ui-page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.ui-page-title {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  margin: 0 0 4px;
  color: #e5e7eb;
}
.ui-page-subtitle {
  margin: 0;
  font-size: 13px;
  color: #9ca3af;
}
.ui-page-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ui-page-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
</style>
