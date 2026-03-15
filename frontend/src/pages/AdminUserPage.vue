<template>
  <div class="user-page">
    <n-card title="用户管理" size="small" :segmented="{ content: true }">
      <p class="helper">仅管理员可见，可调整角色与禁用/启用账号。</p>
      <n-space justify="space-between" align="center" wrap>
        <n-space align="center" wrap>
          <n-input
            v-model:value="keyword"
            size="small"
            placeholder="搜索用户名 / 邮箱"
            clearable
            style="max-width: 260px"
          />
          <n-select
            v-model:value="statusFilter"
            size="small"
            :options="statusOptions"
            style="width: 140px"
          />
        </n-space>
        <n-space>
          <n-button size="small" secondary @click="loadUsers">刷新</n-button>
          <n-button size="small" type="primary" @click="showCreate = true">创建用户</n-button>
        </n-space>
      </n-space>
      <n-data-table
        class="table"
        size="small"
        :columns="columns"
        :data="filtered"
        :loading="loading"
        :bordered="false"
        :pagination="{ pageSize: 10 }"
      />
      <n-alert v-if="error" type="error" class="mt8" closable @close="error = ''">
        {{ error }}
      </n-alert>
      <n-alert v-if="success" type="success" class="mt8" closable @close="success = ''">
        {{ success }}
      </n-alert>
      <n-modal v-model:show="showCreate" preset="dialog" title="创建用户">
        <n-form label-placement="left" label-width="80">
          <n-form-item label="用户名">
            <n-input v-model:value="createForm.username" />
          </n-form-item>
          <n-form-item label="邮箱">
            <n-input v-model:value="createForm.email" />
          </n-form-item>
          <n-form-item label="角色">
            <n-select v-model:value="createForm.role" :options="roleOptions" />
          </n-form-item>
          <n-form-item label="水平">
            <n-select
              v-model:value="createForm.level"
              :options="[
                { label: 'beginner', value: 'beginner' },
                { label: 'intermediate', value: 'intermediate' },
                { label: 'advanced', value: 'advanced' }
              ]"
            />
          </n-form-item>
        </n-form>
        <template #action>
          <n-button @click="showCreate = false">取消</n-button>
          <n-button type="primary" @click="handleCreate">创建</n-button>
        </template>
      </n-modal>
    </n-card>
  </div>
</template>

<script setup>
import { computed, h, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { NButton, NTag, NSwitch, NSelect, NInput, NForm, NFormItem, NModal } from 'naive-ui';
import { listUsers, updateUser, createUser, resetPassword } from '../api/adminUser';

const route = useRoute();

const users = ref([]);
const loading = ref(false);
const error = ref('');
const success = ref('');
const keyword = ref('');
const statusFilter = ref('');
const showCreate = ref(false);
const createForm = reactive({
  username: '',
  email: '',
  role: 'student',
  level: 'beginner'
});
const tempPassword = ref('');

const roleOptions = [
  { label: 'student', value: 'student' },
  { label: 'admin', value: 'admin' }
];

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '正常', value: 'ACTIVE' },
  { label: '禁用', value: 'DISABLED' }
];

const statusText = (s) => (s === 'DISABLED' ? '禁用' : '正常');
const statusType = (s) => (s === 'DISABLED' ? 'error' : 'success');

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  return users.value.filter((u) => {
    if (statusFilter.value && u.status !== statusFilter.value) {
      return false;
    }
    if (!kw) {
      return true;
    }
    return (
      (u.username && u.username.toLowerCase().includes(kw)) ||
      (u.email && u.email.toLowerCase().includes(kw))
    );
  });
});

const columns = [
  { title: '用户名', key: 'username' },
  { title: '邮箱', key: 'email' },
  {
    title: '角色',
    key: 'role',
    render(row) {
      return h(NSelect, {
        size: 'small',
        value: row.role,
        options: roleOptions,
        style: 'width:120px',
        onUpdateValue: async (val) => {
          await handleUpdate(row.id, { role: val });
        }
      });
    }
  },
  {
    title: '状态',
    key: 'status',
    render(row) {
      return h(NSwitch, {
        size: 'small',
        value: row.status === 'ACTIVE',
        'onUpdate:value': async (val) => {
          await handleUpdate(row.id, { status: val ? 'ACTIVE' : 'DISABLED' });
        }
      });
    }
  },
  {
    title: '标签',
    key: 'tags',
    render(row) {
      return h(
        NTag,
        { type: statusType(row.status), size: 'small' },
        { default: () => statusText(row.status) }
      );
    }
  }
];

async function handleUpdate(id, payload) {
  error.value = '';
  success.value = '';
  loading.value = true;
  try {
    await updateUser(id, payload);
    success.value = '已更新';
    await loadUsers();
  } catch (e) {
    // eslint-disable-next-line no-console
    console.error(e);
    error.value = '更新失败，请重试';
  } finally {
    loading.value = false;
  }
}

async function handleCreate() {
  error.value = '';
  success.value = '';
  try {
    await createUser(createForm);
    showCreate.value = false;
    success.value = '用户已创建（临时密码为固定值，记得通知用户）';
    createForm.username = '';
    createForm.email = '';
    createForm.role = 'student';
    createForm.level = 'beginner';
    await loadUsers();
  } catch (e) {
    // eslint-disable-next-line no-console
    console.error(e);
    error.value = '创建用户失败';
  }
}

async function handleResetPwd(id) {
  error.value = '';
  tempPassword.value = '';
  try {
    tempPassword.value = await resetPassword(id);
    success.value = `临时密码：${tempPassword.value}`;
  } catch (e) {
    // eslint-disable-next-line no-console
    console.error(e);
    error.value = '重置密码失败';
  }
}

function syncFiltersFromRoute(query = route.query) {
  keyword.value = typeof query.keyword === 'string' ? query.keyword : '';
  statusFilter.value =
    typeof query.status === 'string' && ['ACTIVE', 'DISABLED'].includes(query.status)
      ? query.status
      : '';
}

async function loadUsers() {
  error.value = '';
  loading.value = true;
  try {
    users.value = await listUsers();
  } catch (e) {
    // eslint-disable-next-line no-console
    console.error(e);
    error.value = '加载用户失败，请确认后端已启动';
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  syncFiltersFromRoute();
  await loadUsers();
});

watch(
  () => route.query,
  (query) => {
    syncFiltersFromRoute(query);
  }
);
</script>

<style scoped>
.user-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.helper {
  color: #6b7280;
  margin: 0 0 8px;
}

.mt8 {
  margin-top: 8px;
}
</style>
