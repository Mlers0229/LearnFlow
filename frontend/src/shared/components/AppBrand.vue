<template>
  <RouterLink :to="to" :class="['lf-brand', compact && 'lf-brand--compact', inverse && 'lf-brand--inverse']">
    <span class="lf-brand-mark" aria-hidden="true">
      <span />
      <span />
      <span />
    </span>
    <span class="lf-brand-copy">
      <strong>LearnFlow</strong>
      <small>{{ subtitle }}</small>
    </span>
  </RouterLink>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router';

withDefaults(
  defineProps<{
    to?: string;
    subtitle?: string;
    compact?: boolean;
    inverse?: boolean;
  }>(),
  {
    to: '/',
    subtitle: '智能学习工作台',
    compact: false,
    inverse: false
  }
);
</script>

<style scoped>
.lf-brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  color: var(--lf-text-strong);
  text-decoration: none;
}

.lf-brand--inverse {
  color: #f8fbfb;
}

.lf-brand-mark {
  position: relative;
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  grid-template-columns: repeat(3, 1fr);
  align-items: end;
  gap: 3px;
  padding: 8px;
  border-radius: 12px;
  background: linear-gradient(145deg, var(--lf-brand-500), var(--lf-brand-800));
  box-shadow: 0 10px 24px rgba(17, 86, 83, 0.22);
}

.lf-brand-mark span {
  display: block;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.94);
}

.lf-brand-mark span:nth-child(1) { height: 42%; }
.lf-brand-mark span:nth-child(2) { height: 72%; }
.lf-brand-mark span:nth-child(3) { height: 100%; background: #f4ba77; }

.lf-brand-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.lf-brand-copy strong {
  font-size: 18px;
  letter-spacing: -0.02em;
  line-height: 1.1;
}

.lf-brand-copy small {
  overflow: hidden;
  color: var(--lf-text-subtle);
  font-size: 11px;
  letter-spacing: 0.04em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lf-brand--inverse .lf-brand-copy small {
  color: rgba(226, 238, 240, 0.7);
}

.lf-brand--compact .lf-brand-copy small {
  display: none;
}
</style>
