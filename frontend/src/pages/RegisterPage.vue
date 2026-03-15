<template>
  <div class="auth-page">
    <n-card class="auth-card" :bordered="true" hoverable>
      <h1 class="title">注册 LearnFlow 账号</h1>
      <p class="subtitle">
        注册后可以生成学习计划、上传资源，后续版本会支持保存个人学习进度。
      </p>

      <n-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-placement="top"
        size="small"
        @submit.prevent="onSubmit"
      >
        <n-form-item label="用户名" path="username">
          <n-input
            v-model:value="form.username"
            placeholder="至少 3 个字符"
          />
        </n-form-item>

        <n-form-item label="密码" path="password">
          <n-input
            v-model:value="form.password"
            type="password"
            show-password-on="click"
            placeholder="至少 6 位"
          />
        </n-form-item>

        <n-form-item label="确认密码" path="confirmPassword">
          <n-input
            v-model:value="form.confirmPassword"
            type="password"
            show-password-on="click"
            placeholder="请再次输入密码"
          />
        </n-form-item>

        <n-form-item label="邮箱（可选）" path="email">
          <n-input
            v-model:value="form.email"
            type="email"
            placeholder="用于找回密码或联系（可选）"
          />
        </n-form-item>

        <n-form-item label="当前基础水平" path="level">
          <n-select
            v-model:value="form.level"
            :options="levelOptions"
            placeholder="未指定"
          />
        </n-form-item>

        <n-button
          type="primary"
          block
          attr-type="submit"
          :loading="loading"
        >
          {{ loading ? '正在注册…' : '注册并登录' }}
        </n-button>
      </n-form>

      <p v-if="error" class="error-text">
        {{ error }}
      </p>

      <p class="helper-text">
        已有账号？
        <RouterLink to="/login">去登录</RouterLink>
      </p>
    </n-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter, RouterLink } from 'vue-router';
import { register } from '../api/auth';
import { useAuthStore } from '../store/auth';

const router = useRouter();
const { setUser } = useAuthStore();

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
  level: ''
});

const levelOptions = [
  { label: '未指定', value: '' },
  { label: '零基础', value: 'beginner' },
  { label: '有一点基础', value: 'intermediate' },
  { label: '进阶', value: 'advanced' }
];

const rules = {
  username: {
    required: true,
    message: '请输入用户名',
    trigger: ['input', 'blur']
  },
  password: {
    required: true,
    message: '请输入密码',
    trigger: ['input', 'blur']
  },
  confirmPassword: [
    {
      required: true,
      message: '请再次输入密码',
      trigger: ['input', 'blur']
    },
    {
      validator: () => form.password === form.confirmPassword,
      message: '两次输入的密码不一致',
      trigger: ['input', 'blur']
    }
  ]
};

const formRef = ref(null);
const loading = ref(false);
const error = ref('');

async function onSubmit() {
  if (formRef.value) {
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) return;
  }

  loading.value = true;
  error.value = '';

  try {
    const user = await register({
      username: form.username,
      password: form.password,
      email: form.email || null,
      level: form.level || null
    });
    setUser(user);
    router.push('/');
  } catch (e) {
    // eslint-disable-next-line no-console
    console.error(e);
    error.value = '注册失败，请更换用户名或稍后再试。';
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.auth-page {
  min-height: calc(100vh - 64px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px 32px;
}

.auth-card {
  width: 100%;
  max-width: 420px;
}
</style>

