<template>
  <div class="profile-page">
    <n-card title="个人设置" :segmented="{ content: true }">
      <n-form
        label-placement="left"
        label-width="100"
        :model="form"
        :rules="rules"
        ref="formRef"
      >
        <n-form-item label="用户名">
          <n-input v-model:value="form.username" disabled />
        </n-form-item>
        <n-form-item label="邮箱" path="email">
          <n-input v-model:value="form.email" placeholder="请输入邮箱" />
        </n-form-item>
        <n-form-item label="学习水平">
          <n-select v-model:value="form.level" :options="levelOptions" />
        </n-form-item>
        <n-form-item label="主题">
          <n-space>
            <n-button size="small" @click="toggleTheme">
              切换为 {{ isDark ? '亮色' : '暗色' }} 主题
            </n-button>
            <n-button size="small" @click="decreaseFont">A-</n-button>
            <n-button size="small" @click="increaseFont">A+</n-button>
            <span class="hint">主题/字号会在本机保存</span>
          </n-space>
        </n-form-item>
        <n-form-item label="原密码" path="oldPassword">
          <n-input type="password" v-model:value="form.oldPassword" placeholder="若修改密码请先填写原密码" />
        </n-form-item>
        <n-form-item label="新密码" path="newPassword">
          <n-input type="password" v-model:value="form.newPassword" placeholder="不修改可留空" />
        </n-form-item>
        <n-space>
          <n-button type="primary" :loading="saving" @click="handleSave">保存</n-button>
          <span class="hint">若不修改密码，可只改邮箱/学习水平。</span>
        </n-space>
      </n-form>
      <n-alert v-if="error" type="error" class="mt8" closable @close="error = ''">
        {{ error }}
      </n-alert>
      <n-alert v-if="success" type="success" class="mt8" closable @close="success = ''">
        {{ success }}
      </n-alert>
    </n-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useMessage } from 'naive-ui';
import { useAuthStore } from '../store/auth';
import { updateProfile } from '../api/auth';
import { useUiStore } from '../store/ui';

const { currentUser, setUser } = useAuthStore();
const { toggleTheme, increaseFont, decreaseFont, isDark } = useUiStore();
const message = useMessage();

const form = reactive({
  username: currentUser.value?.username || '',
  email: currentUser.value?.email || '',
  level: currentUser.value?.level || '',
  oldPassword: '',
  newPassword: ''
});

const levelOptions = [
  { label: '零基础', value: 'beginner' },
  { label: '有一点基础', value: 'intermediate' },
  { label: '进阶', value: 'advanced' }
];

const rules = {
  email: [
    {
      validator: (_, value) => {
        if (!value) return true;
        return /\S+@\S+\.\S+/.test(value);
      },
      message: '请输入合法的邮箱',
      trigger: ['input', 'blur']
    }
  ],
  newPassword: [
    {
      validator: (_, value) => {
        if (!value) return true;
        return value.length >= 6;
      },
      message: '新密码至少 6 位',
      trigger: ['input', 'blur']
    }
  ]
};

const formRef = ref(null);
const saving = ref(false);
const error = ref('');
const success = ref('');

async function handleSave() {
  error.value = '';
  success.value = '';
  if (formRef.value) {
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) return;
  }
  if (!currentUser.value?.id) {
    error.value = '当前未登录';
    return;
  }
  saving.value = true;
  try {
    const payload = {
      userId: currentUser.value.id,
      email: form.email,
      level: form.level,
      oldPassword: form.oldPassword || undefined,
      newPassword: form.newPassword || undefined
    };
    const resp = await updateProfile(payload);
    setUser({
      ...currentUser.value,
      email: resp.email,
      level: resp.level
    });
    success.value = '保存成功';
    message.success('已更新个人信息');
    form.oldPassword = '';
    form.newPassword = '';
  } catch (e) {
    // eslint-disable-next-line no-console
    console.error(e);
    error.value = e.message || '更新失败';
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.profile-page {
  max-width: 680px;
}
.hint {
  color: #9ca3af;
  font-size: 12px;
}
.mt8 {
  margin-top: 8px;
}
</style>


