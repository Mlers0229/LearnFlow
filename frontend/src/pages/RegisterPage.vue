<template>
  <div class="auth-page">
    <div class="auth-shell">
      <section class="auth-showcase">
        <div class="auth-kicker">Create Your Workspace</div>
        <h1 class="auth-title">创建 LearnFlow 账号</h1>
        <p class="auth-subtitle">
          注册后可以生成个性化学习计划、沉淀练习记录、上传学习资源，并在后续回顾中持续追踪自己的学习节奏。
        </p>

        <div class="auth-chip-row">
          <span v-for="chip in highlights" :key="chip" class="auth-chip">{{ chip }}</span>
        </div>

        <div class="auth-feature-list">
          <div v-for="feature in features" :key="feature.title" class="auth-feature-item">
            <div class="auth-feature-title">{{ feature.title }}</div>
            <div class="auth-feature-desc">{{ feature.desc }}</div>
          </div>
        </div>

        <div class="auth-note-panel">
          <div class="auth-note-label">注册后即可使用</div>
          <div class="auth-note-title">生成计划、复盘执行、沉淀练习、上传资源</div>
          <div class="auth-note-desc">
            账号体系会记录你的基础水平与学习轨迹，后续体验会继续围绕长期成长做增强。
          </div>
        </div>
      </section>

      <n-card class="auth-card" :bordered="true" hoverable>
        <div class="auth-card-head">
          <div class="auth-card-kicker">账号注册</div>
          <h2 class="auth-card-title">开始建立你的学习空间</h2>
          <p class="auth-card-subtitle">
            填写基础信息后将自动登录，并进入个人学习工作台。
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
              placeholder="至少 3 个字符"
            />
          </n-form-item>

          <n-form-item label="密码" path="password">
            <n-input
              v-model:value="form.password"
              type="password"
              show-password-on="click"
              placeholder="至少 12 位"
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
              placeholder="用于找回密码或接收通知"
            />
          </n-form-item>

          <n-form-item label="当前基础水平" path="level">
            <n-select
              v-model:value="form.level"
              :options="levelOptions"
              placeholder="未指定"
            />
          </n-form-item>

          <div class="auth-submit-row">
            <n-button
              type="primary"
              block
              attr-type="submit"
              :loading="loading"
            >
              {{ loading ? '正在注册…' : '注册并登录' }}
            </n-button>
          </div>
        </n-form>

        <p v-if="error" class="error-text">
          {{ error }}
        </p>

        <div class="auth-footnote">
          <span>已有账号？</span>
          <RouterLink to="/login">去登录</RouterLink>
        </div>
      </n-card>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter, RouterLink } from 'vue-router';
import { register } from '../api/auth';
import { useAuthStore } from '../store/auth';

const router = useRouter();
const { setUser } = useAuthStore();

const highlights = ['学习画像初始化', '历史计划沉淀', '练习记录回顾', '资源上传治理'];

const features = [
  {
    title: '从第一份计划开始沉淀轨迹',
    desc: '注册后就可以保存你的基础水平、计划记录与后续执行状态。'
  },
  {
    title: '兼顾学习执行与资源建设',
    desc: '除了使用系统生成计划，你也可以上传优质资源参与内容共建。'
  }
];

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
  password: [
    {
      required: true,
      message: '请输入密码',
      trigger: ['input', 'blur']
    },
    {
      validator: (_, value) => String(value || '').length >= 12,
      message: '密码至少 12 位',
      trigger: ['input', 'blur']
    }
  ],
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
    console.error(e);
    error.value = '注册失败，请更换用户名或稍后再试。';
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
  grid-template-columns: minmax(0, 1.2fr) minmax(400px, 0.8fr);
  gap: 18px;
  align-items: stretch;
}

.auth-showcase {
  padding: 30px 32px;
  border-radius: 30px;
  background:
    radial-gradient(circle at top left, rgba(255, 255, 255, 0.84), transparent 34%),
    linear-gradient(135deg, #163650, #20586a 50%, #d9efe5 180%);
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

.auth-feature-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 22px;
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

.auth-note-panel {
  margin-top: 22px;
  padding: 18px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.auth-note-label {
  font-size: 12px;
  color: rgba(236, 245, 248, 0.72);
}

.auth-note-title {
  margin-top: 8px;
  font-size: 22px;
  line-height: 1.35;
  font-weight: 700;
}

.auth-note-desc {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.8;
  color: rgba(236, 245, 248, 0.84);
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
  line-height: 1.2;
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
}
</style>
