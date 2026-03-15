<template>
  <n-config-provider>
    <n-message-provider>
      <RouterView />
    </n-message-provider>
  </n-config-provider>
</template>

<script setup>
import { RouterView } from 'vue-router';
import { NConfigProvider, NMessageProvider } from 'naive-ui';
</script>
