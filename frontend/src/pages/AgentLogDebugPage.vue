<template>
  <div class="log-page">
    <n-card class="log-card" :bordered="true">
      <template #header>
        <div class="header">
          <h1 class="title">多 Agent 调用日志（调试视图）</h1>
          <p class="subtitle">
            这里展示最近一段时间内
            <code class="mono-inline">
              GoalAgent / PlanAgent / RagAgent / DetailPlanAgent / TutorAgent
            </code>
            的调用记录，便于在论文或答辩中说明“多 Agent 调用链”的实际运行情况。
          </p>
        </div>
      </template>

      <n-space class="controls" size="small" align="end" wrap>
        <n-form
          :model="form"
          label-placement="top"
          size="small"
          class="controls-form"
          @submit.prevent="loadLogs"
        >
          <n-space size="small" align="end" wrap>
            <n-form-item label="traceId（可选）">
              <n-input
                v-model:value="form.traceId"
                placeholder="可输入某次调用的 traceId 进行过滤，不填则查看全局最近日志"
              />
            </n-form-item>
            <n-form-item label="日志范围">
              <n-select
                v-model:value="form.mode"
                :options="modeOptions"
                style="width: 160px"
              />
            </n-form-item>
            <n-form-item label="条数">
              <n-input-number
                v-model:value="form.limit"
                :min="1"
                :max="200"
              />
            </n-form-item>
            <n-button
              type="primary"
              attr-type="submit"
              :loading="loading"
            >
              {{ loading ? '正在加载…' : '刷新日志' }}
            </n-button>
          </n-space>
        </n-form>
      </n-space>

      <n-alert
        v-if="error"
        type="error"
        closable
        class="alert"
        @close="error = ''"
      >
        {{ error }}
      </n-alert>

      <n-empty
        v-else-if="!loading && !logs.length"
        description="暂无日志记录。可以先在前端发起一次“生成学习计划 / 加载推荐资源 / 细化任务 / 生成练习题”等操作，再回来刷新。"
      />

      <n-data-table
        v-else
        :columns="columns"
        :data="logs"
        :bordered="true"
        size="small"
        :single-line="false"
        :pagination="{
          pageSize: 10
        }"
        class="log-table"
      />
    </n-card>
  </div>
</template>

<script setup>
import { h, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { NTag } from 'naive-ui';
import { getAgentLogs } from '../api/plan';

const route = useRoute();

const logs = ref([]);
const loading = ref(false);
const error = ref('');

const form = reactive({
  traceId: '',
  mode: 'all',
  limit: 50
});

const modeOptions = [
  { label: '全部日志', value: 'all' },
  { label: '异常 / 慢调用', value: 'suspicious' }
];

function syncFormFromRoute(query = route.query) {
  form.traceId = typeof query.traceId === 'string' ? query.traceId : '';
  form.mode = query.mode === 'suspicious' ? 'suspicious' : 'all';
  const parsedLimit = Number.parseInt(String(query.limit ?? ''), 10);
  form.limit = Number.isFinite(parsedLimit) ? Math.min(Math.max(parsedLimit, 1), 200) : 50;
}

async function loadLogs() {
  loading.value = true;
  error.value = '';
  try {
    const data = await getAgentLogs({
      traceId: form.traceId || undefined,
      limit: form.limit || 50
    });
    const rows = Array.isArray(data) ? data : [];
    logs.value = form.mode === 'suspicious' ? rows.filter(isSuspiciousLog) : rows;
  } catch (e) {
    // eslint-disable-next-line no-console
    console.error(e);
    error.value = '加载 Agent 调用日志失败，请确认后端和 Agent 平台已启动。';
  } finally {
    loading.value = false;
  }
}

function formatTime(iso) {
  if (!iso) return '';
  return iso.replace('T', ' ').replace('Z', '');
}

function truncate(str, max = 80) {
  if (!str) return '';
  if (str.length <= max) return str;
  return `${str.slice(0, max)}...`;
}

function isSuspiciousLog(row) {
  const haystack = `${row?.requestPayload || ''} ${row?.responsePayload || ''}`.toLowerCase();
  return (
    Number(row?.durationMs || 0) >= 3000 ||
    haystack.includes('error') ||
    haystack.includes('exception') ||
    haystack.includes('fail')
  );
}

function agentTagType(name) {
  if (!name) return 'default';
  if (name.includes('Goal')) return 'info';
  if (name.includes('Plan')) return 'success';
  if (name.includes('Rag')) return 'warning';
  if (name.includes('Tutor')) return 'default';
  if (name.includes('Detail')) return 'primary';
  return 'default';
}

const columns = [
  {
    title: 'ID',
    key: 'id',
    width: 70
  },
  {
    title: '时间',
    key: 'createdAt',
    width: 170,
    render(row) {
      return formatTime(row.createdAt);
    }
  },
  {
    title: 'traceId',
    key: 'traceId',
    width: 160,
    ellipsis: {
      tooltip: true
    }
  },
  {
    title: 'Agent',
    key: 'agentName',
    width: 140,
    render(row) {
      return h(
        NTag,
        {
          size: 'small',
          type: agentTagType(row.agentName)
        },
        { default: () => row.agentName }
      );
    }
  },
  {
    title: '耗时(ms)',
    key: 'durationMs',
    width: 90
  },
  {
    title: '请求摘要',
    key: 'requestPayload',
    render(row) {
      return h(
        'div',
        { class: 'payload mono small' },
        truncate(row.requestPayload)
      );
    }
  },
  {
    title: '响应摘要',
    key: 'responsePayload',
    render(row) {
      return h(
        'div',
        { class: 'payload mono small' },
        truncate(row.responsePayload)
      );
    }
  }
];

watch(
  () => route.query,
  () => {
    syncFormFromRoute();
    loadLogs();
  },
  { immediate: true }
);
</script>

<style scoped>
.log-page {
  padding-top: 4px;
}

.log-card {
  width: 100%;
}

.header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.controls-form {
  width: 100%;
}

.controls {
  margin-bottom: 8px;
}

.alert {
  margin-bottom: 8px;
}

.mono-inline {
  font-family:
    ui-monospace,
    SFMono-Regular,
    Menlo,
    Monaco,
    Consolas,
    'Liberation Mono',
    'Courier New',
    monospace;
  font-size: 12px;
  background-color: #e5e7eb;
  padding: 2px 4px;
  border-radius: 4px;
}

.mono {
  font-family:
    ui-monospace,
    SFMono-Regular,
    Menlo,
    Monaco,
    Consolas,
    'Liberation Mono',
    'Courier New',
    monospace;
}

.small {
  font-size: 11px;
}

.payload {
  max-width: 360px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
