<template>
  <div class="auth-page">
    <div class="auth-shell">
      <section class="auth-showcase">
        <div class="auth-kicker">LearnFlow Workspace</div>
        <h1 class="auth-title">进入你的学习工作台</h1>
        <p class="auth-subtitle">
          登录后即可继续生成学习计划、回看历史执行轨迹、查看练习评测结果，并在同一套界面中完成资源沉淀与 AI
          辅学。
        </p>

        <div class="auth-chip-row">
          <span v-for="chip in highlights" :key="chip" class="auth-chip">{{ chip }}</span>
        </div>

        <div class="auth-metrics">
          <div v-for="item in metrics" :key="item.label" class="auth-metric-card">
            <div class="auth-metric-label">{{ item.label }}</div>
            <div class="auth-metric-value">{{ item.value }}</div>
            <div class="auth-metric-desc">{{ item.desc }}</div>
          </div>
        </div>

        <div class="auth-feature-list">
          <div v-for="feature in features" :key="feature.title" class="auth-feature-item">
            <div class="auth-feature-title">{{ feature.title }}</div>
            <div class="auth-feature-desc">{{ feature.desc }}</div>
          </div>
        </div>
      </section>

      <n-card class="auth-card" :bordered="true" hoverable>
        <div class="auth-card-head">
          <div class="auth-card-kicker">账号登录</div>
          <h2 class="auth-card-title">欢迎回来</h2>
          <p class="auth-card-subtitle">
            使用你的 LearnFlow 账号继续进入学习工作台。
          </p>
        </div>

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

          <div class="auth-submit-row">
            <n-button
              type="primary"
              block
              attr-type="submit"
              :loading="loading"
            >
              {{ loading ? '正在登录…' : '登录' }}
            </n-button>
          </div>
        </n-form>

        <p v-if="error" class="error-text">
          {{ error }}
        </p>

        <div class="auth-footnote">
          <span>还没有账号？</span>
          <RouterLink to="/register">立即注册</RouterLink>
        </div>
        <div class="auth-footnote">
          <span>忘记密码？</span>
          <RouterLink to="/reset-password">找回密码</RouterLink>
        </div>
      </n-card>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter, RouterLink } from 'vue-router';
import { login } from '../api/auth';
import { useAuthStore } from '../store/auth';

const router = useRouter();
const { setUser } = useAuthStore();

const highlights = ['学习计划生成', '历史计划复盘', '练习评测沉淀', 'AI 对话辅学'];

const metrics = [
  {
    label: '学习执行',
    value: '一体化',
    desc: '计划、资源、练习与对话收敛到同一工作台。'
  },
  {
    label: '计划视图',
    value: '工作台式',
    desc: '支持历史计划切换、日程索引与执行联动。'
  }
];

const features = [
  {
    title: '继续上次的学习节奏',
    desc: '登录后可以继续查看你最近生成的计划、日程进度与练习回顾。'
  },
  {
    title: '用户端与管理端共用账号体系',
    desc: '管理员登录后会自动进入控制台，普通用户保持在个人学习工作台。'
  }
];

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
    if (user && user.role === 'admin') {
      router.push('/admin');
    } else {
      router.push('/');
    }
  } catch (e) {
    console.error(e);
    error.value = '登录失败，请检查用户名或密码。';
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.auth-page {
  min-height: calc(100vh - 84px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28px 18px 40px;
}

.auth-shell {
  width: min(1160px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(380px, 0.8fr);
  gap: 18px;
  align-items: stretch;
}

.auth-showcase {
  padding: 30px 32px;
  border-radius: 30px;
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.84), transparent 34%),
    linear-gradient(135deg, #12324a, #204f63 52%, #d7eee7 180%);
  color: #f8fafc;
  box-shadow: 0 24px 48px rgba(12, 37, 53, 0.16);
}

.auth-kicker {
  font-size: 11px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: rgba(236, 245, 248, 0.72);
}

.auth-title {
  margin: 10px 0 12px;
  font-size: clamp(30px, 4vw, 42px);
  line-height: 1.08;
}

.auth-subtitle {
  margin: 0;
  max-width: 700px;
  font-size: 14px;
  line-height: 1.85;
  color: rgba(236, 245, 248, 0.9);
}

.auth-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 20px;
}

.auth-chip {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.14);
  font-size: 12px;
}

.auth-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 22px;
}

.auth-metric-card {
  padding: 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.auth-metric-label {
  font-size: 12px;
  color: rgba(236, 245, 248, 0.74);
}

.auth-metric-value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 700;
}

.auth-metric-desc {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.75;
  color: rgba(236, 245, 248, 0.82);
}

.auth-feature-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 20px;
}

.auth-feature-item {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(9, 24, 33, 0.24);
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.auth-feature-title {
  font-size: 14px;
  font-weight: 700;
}

.auth-feature-desc {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.75;
  color: rgba(236, 245, 248, 0.82);
}

.auth-card {
  height: 100%;
  border-radius: 28px;
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.08);
}

.auth-card-head {
  margin-bottom: 8px;
}

.auth-card-kicker {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #708393;
}

.auth-card-title {
  margin: 8px 0 6px;
  font-size: 28px;
  line-height: 1.15;
  color: #102235;
}

.auth-card-subtitle {
  margin: 0 0 14px;
  font-size: 13px;
  line-height: 1.8;
  color: #64748b;
}

.auth-submit-row {
  margin-top: 6px;
}

.auth-footnote {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #64748b;
}

.auth-footnote a {
  color: #1f5f68;
  font-weight: 700;
  text-decoration: none;
}

.auth-footnote a:hover {
  text-decoration: underline;
}

@media (max-width: 960px) {
  .auth-shell {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .auth-page {
    padding: 22px 12px 30px;
  }

  .auth-showcase,
  .auth-card {
    border-radius: 24px;
  }

  .auth-showcase {
    padding: 24px 20px;
  }

  .auth-metrics {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
