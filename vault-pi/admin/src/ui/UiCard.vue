<script setup>
const props = defineProps({
  kind: { type: String, default: 'default' }, // primary | success | warning | default
  title: { type: String, default: '' },
  description: { type: String, default: '' },
})
</script>

<template>
  <article class="ui-card" :data-kind="kind">
    <header v-if="props.title || $slots.header" class="ui-card-header">
      <div class="ui-card-header-main">
        <h2 v-if="props.title" class="ui-card-title">{{ props.title }}</h2>
        <p v-if="props.description" class="ui-card-desc">{{ props.description }}</p>
        <slot name="header" />
      </div>
      <div class="ui-card-header-extra">
        <slot name="extra" />
      </div>
    </header>
    <div class="ui-card-body">
      <slot />
    </div>
    <footer v-if="$slots.footer" class="ui-card-footer">
      <slot name="footer" />
    </footer>
  </article>
</template>

<style scoped>
.ui-card {
  position: relative;
  padding: 20px 20px 22px;
  border-radius: 26px;
  background:
    radial-gradient(circle at top left, rgba(96, 165, 250, 0.16), transparent 55%),
    radial-gradient(circle at bottom right, rgba(45, 212, 191, 0.16), transparent 60%),
    rgba(15, 23, 42, 0.82);
  border: 0;
  box-shadow: 0 26px 80px rgba(15, 23, 42, 0.9);
  backdrop-filter: blur(28px);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ui-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.ui-card-title {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  margin: 0 0 4px;
  color: #e5e7eb;
}
.ui-card-desc {
  margin: 0;
  font-size: 12px;
  color: #9ca3af;
}
.ui-card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ui-card-footer {
  margin-top: 8px;
  padding-top: 10px;
  border-top: 1px solid rgba(30, 64, 175, 0.5);
  font-size: 12px;
  color: #9ca3af;
}
/* kind 目前只保留语义，不改变视觉 */
.ui-card[data-kind='primary'],
.ui-card[data-kind='success'],
.ui-card[data-kind='warning'] {}
</style>
