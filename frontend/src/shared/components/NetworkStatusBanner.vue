<template>
  <Transition name="network-banner">
    <div v-if="!isOnline" class="lf-network-banner" role="status" aria-live="polite">
      <WifiOff :size="17" aria-hidden="true" />
      <span>网络连接已中断。你仍可查看当前内容，恢复连接后再提交操作。</span>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { WifiOff } from 'lucide-vue-next';

const isOnline = ref(typeof navigator === 'undefined' ? true : navigator.onLine);

const updateNetworkStatus = () => {
  isOnline.value = navigator.onLine;
};

onMounted(() => {
  window.addEventListener('online', updateNetworkStatus);
  window.addEventListener('offline', updateNetworkStatus);
});

onBeforeUnmount(() => {
  window.removeEventListener('online', updateNetworkStatus);
  window.removeEventListener('offline', updateNetworkStatus);
});
</script>

<style scoped>
.lf-network-banner {
  position: fixed;
  z-index: var(--lf-z-toast);
  top: var(--lf-space-3);
  left: 50%;
  display: inline-flex;
  max-width: min(92vw, 680px);
  align-items: center;
  gap: var(--lf-space-2);
  padding: 10px 14px;
  border: 1px solid color-mix(in srgb, var(--lf-warning-600) 34%, transparent);
  border-radius: var(--lf-radius-pill);
  background: color-mix(in srgb, var(--lf-warning-50) 94%, white);
  box-shadow: var(--lf-shadow-lg);
  color: var(--lf-warning-900);
  font-size: var(--lf-text-sm);
  transform: translateX(-50%);
}

.network-banner-enter-active,
.network-banner-leave-active {
  transition: opacity var(--lf-motion-normal), transform var(--lf-motion-normal);
}

.network-banner-enter-from,
.network-banner-leave-to {
  opacity: 0;
  transform: translate(-50%, -12px);
}
</style>
