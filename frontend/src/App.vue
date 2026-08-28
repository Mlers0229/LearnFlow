<template>
  <n-config-provider :theme-overrides="learnflowThemeOverrides">
    <n-dialog-provider>
      <n-notification-provider>
        <n-message-provider>
          <NetworkStatusBanner />
          <RouterView />
          <ResourceViewerDialog />
          <SessionExpiredDialog />
        </n-message-provider>
      </n-notification-provider>
    </n-dialog-provider>
  </n-config-provider>
</template>

<script setup>
import { RouterView } from 'vue-router';
import {
  NConfigProvider,
  NDialogProvider,
  NMessageProvider,
  NNotificationProvider
} from 'naive-ui';
import { learnflowThemeOverrides } from './app/theme';
import NetworkStatusBanner from './shared/components/NetworkStatusBanner.vue';
import ResourceViewerDialog from './features/resources/viewer/ResourceViewerDialog.vue';
import SessionExpiredDialog from './components/SessionExpiredDialog.vue';
</script>
