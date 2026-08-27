<template>
  <AuthShell
    eyebrow="Create your workspace"
    title="从第一份计划开始积累学习进步"
    description="创建账号后，学习目标、执行进度、练习反馈和资源记录会沉淀在同一个空间，帮助你持续调整下一步。"
    :highlights="highlights"
  >
    <template #top-action>
      <div class="lf-auth-top-action">
        <span>已经有账号？</span>
        <RouterLink to="/login">返回登录</RouterLink>
      </div>
    </template>

    <div class="lf-form-heading">
      <div class="lf-eyebrow">账号注册</div>
      <h2>创建学习空间</h2>
      <p>两步完成基础账号和学习偏好设置。</p>
    </div>

    <n-steps :current="step" size="small" class="lf-register-steps">
      <n-step title="账号信息" />
      <n-step title="学习偏好" />
    </n-steps>

    <n-alert v-if="error" type="error" :bordered="false" class="lf-form-alert" role="alert">
      {{ error }}
    </n-alert>

    <n-form v-show="step === 1" label-placement="top" size="large" @submit.prevent="goToPreferences">
      <n-form-item label="用户名">
        <n-input
          v-model:value="form.username"
          placeholder="至少 3 个字符"
          maxlength="50"
          :disabled="loading"
          :input-props="{ autocomplete: 'username', autocapitalize: 'none' }"
        >
          <template #prefix><UserRound :size="18" /></template>
        </n-input>
      </n-form-item>

      <n-form-item label="邮箱（可选）">
        <n-input
          v-model:value="form.email"
          type="email"
          placeholder="用于找回密码"
          maxlength="100"
          :disabled="loading"
          :input-props="{ autocomplete: 'email' }"
        >
          <template #prefix><Mail :size="18" /></template>
        </n-input>
      </n-form-item>

      <n-form-item label="密码">
        <n-input
          v-model:value="form.password"
          type="password"
          show-password-on="click"
          placeholder="至少 12 位"
          :disabled="loading"
          :input-props="{ autocomplete: 'new-password' }"
        >
          <template #prefix><LockKeyhole :size="18" /></template>
        </n-input>
        <template #feedback>
          <div class="lf-password-strength">
            <span v-for="index in 4" :key="index" :class="index <= passwordStrength && 'is-active'" />
            <small>{{ passwordStrengthText }}</small>
          </div>
        </template>
      </n-form-item>

      <n-form-item label="确认密码">
        <n-input
          v-model:value="form.confirmPassword"
          type="password"
          show-password-on="click"
          placeholder="请再次输入密码"
          :disabled="loading"
          :input-props="{ autocomplete: 'new-password' }"
        />
      </n-form-item>

      <n-button type="primary" size="large" block attr-type="submit">
        下一步：学习偏好
        <template #icon><ArrowRight /></template>
      </n-button>
    </n-form>

    <n-form v-show="step === 2" label-placement="top" size="large" @submit.prevent="onSubmit">
      <n-form-item label="当前基础水平">
        <n-radio-group v-model:value="form.level" class="lf-level-options">
          <n-radio-button v-for="option in levelOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </n-radio-button>
        </n-radio-group>
      </n-form-item>

      <div class="lf-preference-note">
        <Target :size="20" aria-hidden="true" />
        <div>
          <strong>偏好可以随时调整</strong>
          <p>系统会将基础水平用于计划难度和资源筛选，不会限制你之后选择其他内容。</p>
        </div>
      </div>

      <n-checkbox v-model:checked="agreed" class="lf-agreement">
        我了解 LearnFlow 会保存账号资料和学习记录，用于提供学习计划与复盘服务。
      </n-checkbox>

      <div class="lf-register-actions">
        <n-button size="large" secondary :disabled="loading" @click="step = 1">返回修改</n-button>
        <n-button type="primary" size="large" attr-type="submit" :loading="loading">
          <template #icon><UserPlus /></template>
          {{ loading ? '正在创建…' : '创建并进入 LearnFlow' }}
        </n-button>
      </div>
    </n-form>
  </AuthShell>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import { z } from 'zod';
import { ArrowRight, LockKeyhole, Mail, Target, UserPlus, UserRound } from 'lucide-vue-next';
import AuthShell from '../features/auth/components/AuthShell.vue';
import { register } from '../api/auth';
import { getUserFacingError } from '../shared/api/errors';
import { useAuthStore } from '../store/auth';

const highlights = ['学习画像初始化', '计划与进度保存', '练习反馈沉淀', '隐私控制'];
const levelOptions = [
  { label: '暂不指定', value: '' },
  { label: '零基础', value: 'beginner' },
  { label: '有一定基础', value: 'intermediate' },
  { label: '进阶学习', value: 'advanced' }
];

const accountSchema = z.object({
  username: z.string().trim().min(3, '用户名至少需要 3 个字符。').max(50, '用户名不能超过 50 个字符。'),
  email: z.union([z.literal(''), z.string().email('请输入有效的邮箱地址。')]),
  password: z.string().min(12, '密码至少需要 12 位。'),
  confirmPassword: z.string()
}).refine((value) => value.password === value.confirmPassword, {
  message: '两次输入的密码不一致。',
  path: ['confirmPassword']
});

const router = useRouter();
const { setUser } = useAuthStore();
const step = ref(1);
const agreed = ref(false);
const loading = ref(false);
const error = ref('');
const form = reactive({ username: '', email: '', password: '', confirmPassword: '', level: '' });

const passwordStrength = computed(() => [
  form.password.length >= 12,
  /[a-zA-Z]/.test(form.password),
  /\d/.test(form.password),
  /[^a-zA-Z0-9]/.test(form.password)
].filter(Boolean).length);

const passwordStrengthText = computed(() => ['尚未输入', '较弱', '一般', '良好', '较强'][passwordStrength.value]);

function goToPreferences() {
  error.value = '';
  const result = accountSchema.safeParse(form);
  if (!result.success) {
    error.value = result.error.issues[0]?.message || '请检查账号信息。';
    return;
  }
  step.value = 2;
}

async function onSubmit() {
  if (!agreed.value) {
    error.value = '请先确认账号资料和学习记录的使用说明。';
    return;
  }

  loading.value = true;
  error.value = '';
  try {
    const user = await register({
      username: form.username.trim(),
      password: form.password,
      email: form.email.trim() || undefined,
      level: form.level || undefined
    });
    setUser(user);
    await router.push('/');
  } catch (cause) {
    error.value = getUserFacingError(cause, '注册失败，请更换用户名或稍后再试。');
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.lf-auth-top-action { display: flex; align-items: center; gap: 8px; color: var(--lf-text-subtle); font-size: 13px; }
.lf-auth-top-action a { color: var(--lf-brand-700); font-weight: 700; text-decoration: none; }
.lf-form-heading { margin-bottom: 22px; }
.lf-form-heading h2 { margin: 8px 0 7px; color: var(--lf-text-strong); font-size: 30px; letter-spacing: -0.035em; }
.lf-form-heading p { margin: 0; color: var(--lf-text-muted); font-size: 14px; line-height: 1.7; }
.lf-register-steps { margin-bottom: 24px; }
.lf-form-alert { margin-bottom: 18px; }
.lf-password-strength { display: grid; grid-template-columns: repeat(4, 1fr) auto; align-items: center; gap: 5px; width: 100%; }
.lf-password-strength > span { height: 4px; border-radius: 999px; background: var(--lf-neutral-200); }
.lf-password-strength > span.is-active { background: var(--lf-brand-500); }
.lf-password-strength small { min-width: 34px; color: var(--lf-text-subtle); font-size: 10px; text-align: right; }
.lf-level-options { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; width: 100%; }
.lf-level-options :deep(.n-radio-button) { width: 100%; }
.lf-level-options :deep(.n-radio-button__state-border), .lf-level-options :deep(.n-radio-button__state-border + div) { border-radius: 10px; }
.lf-preference-note { display: flex; gap: 12px; margin: 4px 0 18px; padding: 14px; border: 1px solid var(--lf-border-subtle); border-radius: var(--lf-radius-lg); background: var(--lf-brand-50); color: var(--lf-brand-800); }
.lf-preference-note svg { flex: 0 0 auto; margin-top: 2px; }
.lf-preference-note strong { font-size: 13px; }
.lf-preference-note p { margin: 5px 0 0; color: var(--lf-text-muted); font-size: 11px; line-height: 1.6; }
.lf-agreement { align-items: flex-start; margin-bottom: 22px; color: var(--lf-text-muted); line-height: 1.6; }
.lf-register-actions { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 10px; }
@media (max-width: 520px) { .lf-auth-top-action span { display: none; } .lf-level-options, .lf-register-actions { grid-template-columns: 1fr; } }
</style>
