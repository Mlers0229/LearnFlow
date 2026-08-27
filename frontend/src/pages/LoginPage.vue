<template>
  <AuthShell
    eyebrow="LearnFlow workspace"
    title="把学习目标变成每天都能完成的行动"
    description="登录后继续你的学习计划、任务进度和练习复盘。LearnFlow 会保留上下文，让每一次学习都从上一次积累开始。"
    :highlights="highlights"
  >
    <template #top-action>
      <div class="lf-auth-top-action">
        <span>还没有账号？</span>
        <RouterLink to="/register">免费注册</RouterLink>
      </div>
    </template>

    <div class="lf-form-heading">
      <div class="lf-eyebrow">账号登录</div>
      <h2>欢迎回来</h2>
      <p>使用 LearnFlow 账号进入你的个人学习空间。</p>
    </div>

    <n-alert v-if="resetSuccess" type="success" :bordered="false" class="lf-form-alert">
      密码已重置，请使用新密码登录。
    </n-alert>

    <n-alert v-if="error" type="error" :bordered="false" class="lf-form-alert" role="alert">
      {{ error }}
    </n-alert>

    <n-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-placement="top"
      size="large"
      @submit.prevent="onSubmit"
    >
      <n-form-item label="用户名" path="username">
        <n-input
          v-model:value="form.username"
          placeholder="请输入用户名"
          :disabled="loading"
          :input-props="{ autocomplete: 'username', autocapitalize: 'none' }"
        >
          <template #prefix><UserRound :size="18" aria-hidden="true" /></template>
        </n-input>
      </n-form-item>

      <n-form-item label="密码" path="password">
        <n-input
          v-model:value="form.password"
          type="password"
          show-password-on="click"
          placeholder="请输入密码"
          :disabled="loading"
          :input-props="{ autocomplete: 'current-password' }"
        >
          <template #prefix><LockKeyhole :size="18" aria-hidden="true" /></template>
        </n-input>
      </n-form-item>

      <div class="lf-form-between">
        <span>登录状态将通过安全会话保持</span>
        <RouterLink to="/reset-password">忘记密码？</RouterLink>
      </div>

      <n-button type="primary" size="large" block attr-type="submit" :loading="loading">
        <template #icon><LogIn /></template>
        {{ loading ? '正在验证账号…' : '登录并继续学习' }}
      </n-button>
    </n-form>

    <div class="lf-form-divider"><span>第一次使用 LearnFlow</span></div>
    <RouterLink class="lf-secondary-action" to="/register">创建新的学习空间</RouterLink>
  </AuthShell>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import type { FormInst, FormRules } from 'naive-ui';
import { LockKeyhole, LogIn, UserRound } from 'lucide-vue-next';
import AuthShell from '../features/auth/components/AuthShell.vue';
import { login } from '../api/auth';
import { getUserFacingError } from '../shared/api/errors';
import { useAuthStore } from '../store/auth';

const highlights = ['个性化学习计划', '任务执行追踪', '练习与掌握度', 'AI 对话辅学'];
const route = useRoute();
const router = useRouter();
const { setUser } = useAuthStore();
const resetSuccess = computed(() => route.query.reset === 'success');

const form = reactive({ username: '', password: '' });
const formRef = ref<FormInst | null>(null);
const loading = ref(false);
const error = ref('');

const rules: FormRules = {
  username: { required: true, message: '请输入用户名', trigger: ['input', 'blur'] },
  password: { required: true, message: '请输入密码', trigger: ['input', 'blur'] }
};

function safeRedirect(value: unknown) {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') ? value : '';
}

async function onSubmit() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false);
  if (!valid) return;

  loading.value = true;
  error.value = '';
  try {
    const user = await login({ username: form.username.trim(), password: form.password });
    setUser(user);
    const redirect = safeRedirect(route.query.redirect);

    if (user?.role === 'admin') {
      await router.push(redirect && (redirect.startsWith('/admin') || redirect.startsWith('/debug')) ? redirect : '/admin');
    } else {
      await router.push(redirect && !redirect.startsWith('/admin') && !redirect.startsWith('/debug') ? redirect : '/');
    }
  } catch (cause) {
    error.value = getUserFacingError(cause, '登录失败，请检查用户名或密码。');
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.lf-auth-top-action { display: flex; align-items: center; gap: 8px; color: var(--lf-text-subtle); font-size: 13px; }
.lf-auth-top-action a, .lf-form-between a { color: var(--lf-brand-700); font-weight: 700; text-decoration: none; }
.lf-form-heading { margin-bottom: 26px; }
.lf-form-heading h2 { margin: 8px 0 7px; color: var(--lf-text-strong); font-size: 30px; letter-spacing: -0.035em; }
.lf-form-heading p { margin: 0; color: var(--lf-text-muted); font-size: 14px; line-height: 1.7; }
.lf-form-alert { margin-bottom: 18px; }
.lf-form-between { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin: -2px 0 20px; color: var(--lf-text-subtle); font-size: 12px; }
.lf-form-divider { display: flex; align-items: center; gap: 12px; margin: 24px 0 14px; color: var(--lf-text-subtle); font-size: 11px; }
.lf-form-divider::before, .lf-form-divider::after { height: 1px; flex: 1; background: var(--lf-border-subtle); content: ''; }
.lf-secondary-action { display: flex; min-height: 44px; align-items: center; justify-content: center; border: 1px solid var(--lf-border-subtle); border-radius: var(--lf-radius-md); color: var(--lf-text-default); font-size: 13px; font-weight: 700; text-decoration: none; transition: border-color var(--lf-motion-fast), background var(--lf-motion-fast); }
.lf-secondary-action:hover { border-color: var(--lf-brand-300); background: var(--lf-brand-50); }
@media (max-width: 520px) { .lf-auth-top-action span, .lf-form-between span { display: none; } }
</style>
