<template>
  <main class="lf-system-page">
    <section class="lf-system-card" aria-labelledby="system-state-title">
      <div class="lf-system-code" aria-hidden="true">{{ code }}</div>
      <div class="lf-eyebrow">{{ eyebrow }}</div>
      <h1 id="system-state-title">{{ title }}</h1>
      <p>{{ description }}</p>
      <div class="lf-system-actions">
        <n-button v-if="showRetry" type="primary" size="large" @click="retry">
          <template #icon><RefreshCw /></template>
          重新加载
        </n-button>
        <n-button v-else type="primary" size="large" @click="goHome">
          <template #icon><Home /></template>
          返回学习工作台
        </n-button>
        <n-button size="large" secondary @click="goBack">
          <template #icon><ArrowLeft /></template>
          返回上一页
        </n-button>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import { ArrowLeft, Home, RefreshCw } from 'lucide-vue-next';

withDefaults(
  defineProps<{
    code: string;
    eyebrow: string;
    title: string;
    description: string;
    showRetry?: boolean;
  }>(),
  {
    showRetry: false
  }
);

const router = useRouter();

const goHome = () => router.push({ name: 'plan-generator' });
const goBack = () => router.back();
const retry = () => window.location.reload();
</script>

<style scoped>
.lf-system-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: var(--lf-space-6);
  background:
    radial-gradient(circle at 14% 8%, rgba(33, 129, 125, 0.14), transparent 34%),
    var(--lf-bg-canvas);
}

.lf-system-card {
  width: min(100%, 680px);
  padding: clamp(28px, 6vw, 56px);
  border: 1px solid var(--lf-border-subtle);
  border-radius: var(--lf-radius-2xl);
  background: var(--lf-surface-raised);
  box-shadow: var(--lf-shadow-xl);
}

.lf-system-code {
  margin-bottom: var(--lf-space-3);
  color: var(--lf-brand-700);
  font-size: clamp(56px, 14vw, 112px);
  font-weight: 780;
  letter-spacing: -0.08em;
  line-height: 0.86;
  opacity: 0.16;
}

.lf-system-card h1 {
  margin: var(--lf-space-2) 0 var(--lf-space-3);
  color: var(--lf-text-strong);
  font-size: clamp(28px, 5vw, 42px);
  letter-spacing: -0.035em;
  line-height: 1.12;
}

.lf-system-card p {
  max-width: 56ch;
  margin: 0;
  color: var(--lf-text-muted);
  font-size: var(--lf-text-lg);
  line-height: 1.75;
}

.lf-system-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lf-space-3);
  margin-top: var(--lf-space-7);
}

@media (max-width: 520px) {
  .lf-system-actions > * {
    flex: 1 1 100%;
  }
}
</style>
