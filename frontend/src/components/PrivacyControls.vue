<template>
  <n-card title="数据与账户" :segmented="{ content: true }" class="privacy-card">
    <n-space vertical :size="16">
      <section>
        <div class="section-title">导出个人数据</div>
        <p class="hint">生成版本化 JSON 文件。文件仅保留 24 小时，下载时会校验完整性。</p>
        <n-button :loading="exporting" @click="handleExport">生成并下载</n-button>
        <n-progress v-if="exporting" type="line" :percentage="exportProgress" :show-indicator="false" />
      </section>

      <n-divider />

      <section v-if="!isAdmin" class="danger-zone">
        <div class="section-title danger-title">永久注销账户</div>
        <n-alert type="warning" :show-icon="true">
          注销会立即停用登录，并异步删除计划、练习、反馈、个性化记录及你上传的资源原件。该操作不可撤销。
        </n-alert>
        <n-form-item label="当前密码" class="danger-field">
          <n-input v-model:value="erasure.password" type="password" show-password-on="click" />
        </n-form-item>
        <n-form-item :label="`输入 DELETE ${username}`">
          <n-input v-model:value="erasure.confirmation" :placeholder="`DELETE ${username}`" />
        </n-form-item>
        <n-button type="error" :disabled="!canErase" :loading="erasing" @click="handleErasure">
          永久注销账户
        </n-button>
      </section>
      <n-alert v-else type="info">
        管理员账户必须由数据保护负责人通过受控流程处理，不能在此自助注销。
      </n-alert>
    </n-space>
  </n-card>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { useDialog, useMessage } from 'naive-ui';
import { useRouter } from 'vue-router';
import { createPrivacyExport, downloadPrivacyExport, getPrivacyRequest, requestAccountErasure } from '../api/privacy';
import { useAuthStore } from '../store/auth';

const { currentUser, isAdmin, logout } = useAuthStore();
const message = useMessage();
const dialog = useDialog();
const router = useRouter();
const username = computed(() => currentUser.value?.username || '');
const exporting = ref(false);
const exportProgress = ref(10);
const erasing = ref(false);
const erasure = reactive({ password: '', confirmation: '' });
const canErase = computed(() => erasure.password.length > 0 && erasure.confirmation === `DELETE ${username.value}`);

async function handleExport() {
  exporting.value = true;
  exportProgress.value = 15;
  try {
    const request = await createPrivacyExport();
    const completed = await waitForCompletion(request.id);
    if (completed.status !== 'SUCCEEDED' || !completed.downloadReady) {
      throw new Error(`导出失败：${completed.errorCode || completed.status}`);
    }
    exportProgress.value = 95;
    const artifact = await downloadPrivacyExport(request.id);
    const url = URL.createObjectURL(artifact.blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = artifact.filename;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
    exportProgress.value = 100;
    message.success('个人数据导出已下载');
  } catch (error) {
    message.error(error.message || '数据导出失败');
  } finally {
    exporting.value = false;
  }
}

async function waitForCompletion(requestId) {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    const current = await getPrivacyRequest(requestId);
    exportProgress.value = Math.min(90, 20 + attempt);
    if (current.status === 'SUCCEEDED' || current.status === 'FAILED') return current;
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  throw new Error('导出仍在处理中，请稍后重试');
}

function handleErasure() {
  if (!canErase.value) return;
  dialog.error({
    title: '确认永久注销',
    content: '账户会立即停用，关联数据和上传原件随后被不可逆删除。确定继续吗？',
    positiveText: '永久注销',
    negativeText: '取消',
    async onPositiveClick() {
      erasing.value = true;
      try {
        await requestAccountErasure({ ...erasure });
        logout();
        message.success('账户已停用，擦除请求正在处理');
        await router.replace('/login');
      } catch (error) {
        message.error(error.message || '账户注销请求失败');
      } finally {
        erasing.value = false;
      }
    }
  });
}
</script>

<style scoped>
.privacy-card { margin-top: 16px; }
.section-title { font-weight: 600; margin-bottom: 6px; }
.hint { color: #9ca3af; font-size: 13px; margin: 0 0 12px; }
.danger-zone { border: 1px solid rgba(239, 68, 68, 0.35); border-radius: 8px; padding: 16px; }
.danger-title { color: #dc2626; }
.danger-field { margin-top: 16px; }
</style>
