<template>
  <AuthShell
    eyebrow="Account recovery"
    :title="token ? '设置一个新的安全密码' : '安全地找回你的学习空间'"
    :description="token ? '重置成功后，已有刷新会话会失效，你可以使用新密码重新进入 LearnFlow。' : '提交注册用户名和邮箱后，如果信息匹配，系统会发送一次性重置链接。'"
    :highlights="['一次性安全链接', '不会公开账号是否存在', '重置后旧会话失效']"
  >
    <template #top-action>
      <RouterLink class="lf-back-login" to="/login">
        <ArrowLeft :size="16" />返回登录
      </RouterLink>
    </template>

    <div class="lf-form-heading">
      <div class="lf-eyebrow">{{ token ? '设置新密码' : '找回密码' }}</div>
      <h2>{{ token ? '重新保护你的账号' : '接收重置链接' }}</h2>
      <p>{{ token ? '新密码至少需要 12 位，建议同时包含字母、数字和符号。' : '为了保护隐私，无论账号是否匹配，页面都会返回相同结果。' }}</p>
    </div>

    <n-alert v-if="message" type="success" :bordered="false" class="lf-form-alert" role="status">
      {{ message }}
    </n-alert>
    <n-alert v-if="error" type="error" :bordered="false" class="lf-form-alert" role="alert">
      {{ error }}
    </n-alert>

    <n-form v-if="!token" label-placement="top" size="large" @submit.prevent="submitRequest">
      <n-form-item label="用户名">
        <n-input
          v-model:value="requestForm.username"
          maxlength="50"
          placeholder="请输入注册用户名"
          :disabled="loading || requestSent"
          :input-props="{ autocomplete: 'username', autocapitalize: 'none' }"
        >
          <template #prefix><UserRound :size="18" /></template>
        </n-input>
      </n-form-item>
      <n-form-item label="注册邮箱">
        <n-input
          v-model:value="requestForm.email"
          type="email"
          maxlength="100"
          placeholder="name@example.com"
          :disabled="loading || requestSent"
          :input-props="{ autocomplete: 'email' }"
        >
          <template #prefix><Mail :size="18" /></template>
        </n-input>
      </n-form-item>
      <n-button v-if="!requestSent" type="primary" size="large" block attr-type="submit" :loading="loading">
        <template #icon><Send /></template>
        {{ loading ? '正在提交…' : '发送重置邮件' }}
      </n-button>
      <n-button v-else secondary size="large" block @click="resetRequest">使用其他账号信息</n-button>
    </n-form>

    <n-form v-else label-placement="top" size="large" @submit.prevent="submitConfirm">
      <n-form-item label="新密码">
        <n-input
          v-model:value="confirmForm.password"
          type="password"
          show-password-on="click"
          placeholder="至少 12 位"
          :disabled="loading"
          :input-props="{ autocomplete: 'new-password' }"
        >
          <template #prefix><LockKeyhole :size="18" /></template>
        </n-input>
      </n-form-item>
      <n-form-item label="确认新密码">
        <n-input
          v-model:value="confirmForm.confirmPassword"
          type="password"
          show-password-on="click"
          placeholder="再次输入新密码"
          :disabled="loading"
          :input-props="{ autocomplete: 'new-password' }"
        />
      </n-form-item>
      <n-button type="primary" size="large" block attr-type="submit" :loading="loading">
        <template #icon><ShieldCheck /></template>
        {{ loading ? '正在重置…' : '确认重置密码' }}
      </n-button>
    </n-form>

    <div class="lf-security-note">
      <ShieldCheck :size="18" aria-hidden="true" />
      <span>LearnFlow 不会通过邮件索要你的密码或 API Key。</span>
    </div>
  </AuthShell>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { ArrowLeft, LockKeyhole, Mail, Send, ShieldCheck, UserRound } from 'lucide-vue-next';
import AuthShell from '../features/auth/components/AuthShell.vue';
import { confirmPasswordReset, requestPasswordReset } from '../api/auth';
import { getUserFacingError } from '../shared/api/errors';

const route = useRoute();
const router = useRouter();
const token = computed(() => String(route.query.token || ''));
const loading = ref(false);
const requestSent = ref(false);
const message = ref('');
const error = ref('');
const requestForm = reactive({ username: '', email: '' });
const confirmForm = reactive({ password: '', confirmPassword: '' });

async function submitRequest() {
  if (!requestForm.username.trim() || !requestForm.email.trim()) {
    error.value = '请填写用户名和注册邮箱。';
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    await requestPasswordReset({ username: requestForm.username.trim(), email: requestForm.email.trim() });
    requestSent.value = true;
    message.value = '如果账号信息匹配，重置邮件将很快送达。请同时检查垃圾邮件目录。';
  } catch (cause) {
    error.value = getUserFacingError(cause, '暂时无法发送重置邮件，请稍后再试。');
  } finally {
    loading.value = false;
  }
}

function resetRequest() {
  requestSent.value = false;
  message.value = '';
  error.value = '';
}

async function submitConfirm() {
  if (confirmForm.password.length < 12) {
    error.value = '新密码至少需要 12 位。';
    return;
  }
  if (confirmForm.password !== confirmForm.confirmPassword) {
    error.value = '两次输入的密码不一致。';
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    await confirmPasswordReset({ token: token.value, newPassword: confirmForm.password });
    await router.push({ name: 'login', query: { reset: 'success' } });
  } catch (cause) {
    error.value = getUserFacingError(cause, '重置链接无效、已过期或已经使用。');
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.lf-back-login { display: inline-flex; align-items: center; gap: 7px; color: var(--lf-brand-700); font-size: 13px; font-weight: 700; text-decoration: none; }
.lf-form-heading { margin-bottom: 26px; }
.lf-form-heading h2 { margin: 8px 0 7px; color: var(--lf-text-strong); font-size: 30px; letter-spacing: -0.035em; }
.lf-form-heading p { margin: 0; color: var(--lf-text-muted); font-size: 14px; line-height: 1.7; }
.lf-form-alert { margin-bottom: 18px; }
.lf-security-note { display: flex; align-items: flex-start; gap: 9px; margin-top: 22px; padding-top: 18px; border-top: 1px solid var(--lf-border-subtle); color: var(--lf-text-subtle); font-size: 11px; line-height: 1.6; }
.lf-security-note svg { flex: 0 0 auto; color: var(--lf-brand-600); }
</style>
