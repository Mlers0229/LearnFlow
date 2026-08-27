<template>
  <nav class="lf-navigation" :aria-label="label">
    <RouterLink
      v-for="item in items"
      :key="item.to"
      :to="item.to"
      :class="['lf-navigation-link', isActive(item) && 'lf-navigation-link--active']"
      :aria-current="isActive(item) ? 'page' : undefined"
      @click="$emit('navigate')"
    >
      <component :is="item.icon" :size="18" :stroke-width="1.9" aria-hidden="true" />
      <span>{{ item.label }}</span>
      <span v-if="item.badge" class="lf-navigation-badge">{{ item.badge }}</span>
    </RouterLink>
  </nav>
</template>

<script setup lang="ts">
import type { Component } from 'vue';
import { useRoute } from 'vue-router';

export type NavigationItem = {
  to: string;
  label: string;
  icon: Component;
  badge?: string;
  exact?: boolean;
};

withDefaults(
  defineProps<{
    items: NavigationItem[];
    label?: string;
  }>(),
  {
    label: '主导航'
  }
);

defineEmits<{
  navigate: [];
}>();

const route = useRoute();
const isActive = (item: NavigationItem) =>
  item.exact ? route.path === item.to : route.path === item.to || route.path.startsWith(`${item.to}/`);
</script>

<style scoped>
.lf-navigation {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.lf-navigation-link {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  padding: 10px 12px;
  border-radius: var(--lf-radius-md);
  color: inherit;
  font-size: 14px;
  font-weight: 610;
  text-decoration: none;
  transition: background var(--lf-motion-fast), color var(--lf-motion-fast), transform var(--lf-motion-fast);
}

.lf-navigation-link:hover {
  background: rgba(33, 129, 125, 0.09);
  color: var(--lf-brand-800);
  transform: translateX(2px);
}

.lf-navigation-link--active {
  background: linear-gradient(135deg, var(--lf-brand-700), var(--lf-brand-500));
  box-shadow: 0 10px 22px rgba(17, 86, 83, 0.2);
  color: #ffffff;
}

.lf-navigation-link--active:hover {
  color: #ffffff;
}

.lf-navigation-badge {
  min-width: 22px;
  padding: 2px 7px;
  border-radius: var(--lf-radius-pill);
  background: rgba(255, 255, 255, 0.18);
  font-size: 10px;
  text-align: center;
}
</style>
