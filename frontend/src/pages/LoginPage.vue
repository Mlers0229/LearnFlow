<template>
  <div class="auth-page">
    <n-card class="auth-card" :bordered="true" hoverable>
      <h1 class="title">登录 LearnFlow</h1>
      <p class="subtitle">仅用于 Demo，不涉及真实数据。</p>

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
            placeholder="请输入用户名"
          />
        </n-form-item>

        <n-form-item label="密码" path="password">
          <n-input
            v-model:value="form.password"
            type="password"
            show-password-on="click"
            placeholder="请输入密码"
          />
        </n-form-item>

        <n-button
          type="primary"
          block
          attr-type="submit"
          :loading="loading"
        >
          {{ loading ? '正在登录…' : '登录' }}
        </n-button>
      </n-form>

      <p v-if="error" class="error-text">
        {{ error }}
      </p>

      <p class="helper-text">
        还没有账号？
        <RouterLink to="/register">去注册</RouterLink>
      </p>
    </n-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter, RouterLink } from 'vue-router';
import { login } from '../api/auth';
import { useAuthStore } from '../store/auth';

const router = useRouter();
const { setUser } = useAuthStore();

const form = reactive({
  username: '',
  password: ''
});

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
  }
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
    const user = await login({
      username: form.username,
      password: form.password
    });
    setUser(user);
    // 如果是管理员账号，登录后直接进入管理端资源管理页面；否则进入用户端首页
    if (user && user.role === 'admin') {
      router.push('/admin/resources');
    } else {
      router.push('/');
    }
  } catch (e) {
    // eslint-disable-next-line no-console
    console.error(e);
    error.value = '登录失败，请检查用户名或密码。';
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

