<template>
  <div class="reset-page">
    <n-card class="reset-card" :bordered="true">
      <div class="reset-kicker">Account Recovery</div>
      <h1>{{ token ? '设置新密码' : '找回密码' }}</h1>
      <p class="reset-description">
        {{ token ? '请输入至少 12 位的新密码。完成后，其他刷新会话将被注销。' : '填写注册用户名和邮箱。若信息匹配，系统会发送一次性重置链接。' }}
      </p>

      <n-form v-if="!token" :model="requestForm" label-placement="top" @submit.prevent="submitRequest">
        <n-form-item label="用户名">
          <n-input v-model:value="requestForm.username" maxlength="50" />
        </n-form-item>
        <n-form-item label="邮箱">
          <n-input v-model:value="requestForm.email" type="email" maxlength="100" />
        </n-form-item>
        <n-button type="primary" block attr-type="submit" :loading="loading">发送重置邮件</n-button>
      </n-form>

      <n-form v-else :model="confirmForm" label-placement="top" @submit.prevent="submitConfirm">
        <n-form-item label="新密码">
          <n-input v-model:value="confirmForm.password" type="password" show-password-on="click" />
        </n-form-item>
        <n-form-item label="确认新密码">
          <n-input v-model:value="confirmForm.confirmPassword" type="password" show-password-on="click" />
        </n-form-item>
        <n-button type="primary" block attr-type="submit" :loading="loading">确认重置</n-button>
      </n-form>

      <p v-if="message" class="success-text">{{ message }}</p>
      <p v-if="error" class="error-text">{{ error }}</p>
      <RouterLink class="back-link" to="/login">返回登录</RouterLink>
    </n-card>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { confirmPasswordReset, requestPasswordReset } from '../api/auth';

const route = useRoute();
const router = useRouter();
const token = computed(() => String(route.query.token || ''));
const loading = ref(false);
const message = ref('');
const error = ref('');
const requestForm = reactive({ username: '', email: '' });
const confirmForm = reactive({ password: '', confirmPassword: '' });

async function submitRequest() {
  if (!requestForm.username || !requestForm.email) {
    error.value = '请填写用户名和邮箱。';
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    await requestPasswordReset(requestForm);
    message.value = '如果账号信息匹配，重置邮件将很快送达。';
  } catch {
    error.value = '暂时无法发送重置邮件，请稍后再试。';
  } finally {
    loading.value = false;
  }
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
  } catch {
    error.value = '重置链接无效、已过期或已经使用。';
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.reset-page { min-height: calc(100vh - 84px); display: grid; place-items: center; padding: 32px 16px; }
.reset-card { width: min(480px, 100%); border-radius: 24px; box-shadow: 0 18px 40px rgba(15, 35, 50, 0.1); }
.reset-kicker { color: #708393; font-size: 11px; letter-spacing: 0.16em; text-transform: uppercase; }
h1 { margin: 8px 0; color: #102235; }
.reset-description { color: #64748b; line-height: 1.7; }
.success-text { color: #18794e; margin-top: 16px; }
.error-text { color: #c2413b; margin-top: 16px; }
.back-link { display: inline-block; margin-top: 16px; color: #1f5f68; font-weight: 700; text-decoration: none; }
</style>
